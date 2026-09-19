#!/usr/bin/env python3
import argparse
import struct
import zipfile

def u16(buf, off):
    return struct.unpack_from("<H", buf, off)[0]

def u32(buf, off):
    return struct.unpack_from("<I", buf, off)[0]

def len8(buf, off):
    value = buf[off]
    off += 1
    if value & 0x80:
        value = ((value & 0x7F) << 7) | buf[off]
        off += 1
    return value, off

def len16(buf, off):
    value = u16(buf, off)
    off += 2
    if value & 0x8000:
        value = ((value & 0x7FFF) << 16) | u16(buf, off)
        off += 2
    return value, off

def string_pool(chunk):
    header_size = u16(chunk, 2)
    count = u32(chunk, 8)
    flags = u32(chunk, 16)
    start = u32(chunk, 20)
    utf8 = bool(flags & 0x100)
    offsets = [u32(chunk, header_size + i * 4) for i in range(count)]
    out = []
    for offset in offsets:
        pos = start + offset
        if utf8:
            _, pos = len8(chunk, pos)
            byte_len, pos = len8(chunk, pos)
            out.append(chunk[pos:pos + byte_len].decode("utf-8", "replace"))
        else:
            char_len, pos = len16(chunk, pos)
            out.append(chunk[pos:pos + char_len * 2].decode("utf-16le", "replace"))
    return out

def typed_value(raw, data_type, data, strings):
    if raw != 0xFFFFFFFF:
        return strings[raw]
    if data_type == 0x03:
        return strings[data]
    if data_type in (0x10, 0x11):
        return data
    return data

def manifest_identity(apk):
    with zipfile.ZipFile(apk) as archive:
        xml = archive.read("AndroidManifest.xml")
    offset = u16(xml, 2)
    strings = None
    while offset + 8 <= len(xml):
        chunk_type = u16(xml, offset)
        chunk_size = u32(xml, offset + 4)
        if chunk_size < 8 or offset + chunk_size > len(xml):
            break
        if chunk_type == 0x0001:
            strings = string_pool(xml[offset:offset + chunk_size])
        elif chunk_type == 0x0102 and strings is not None:
            ext = offset + 16
            name = strings[u32(xml, ext + 4)]
            attr_start = u16(xml, ext + 8)
            attr_size = u16(xml, ext + 10)
            attr_count = u16(xml, ext + 12)
            attrs = {}
            base = ext + attr_start
            for index in range(attr_count):
                pos = base + index * attr_size
                attr_name = strings[u32(xml, pos + 4)]
                raw = u32(xml, pos + 8)
                data_type = xml[pos + 15]
                data = u32(xml, pos + 16)
                attrs[attr_name] = typed_value(raw, data_type, data, strings)
            if name == "manifest":
                return (
                    str(attrs.get("package", "")),
                    int(attrs.get("versionCode", 0)),
                    str(attrs.get("versionName", "")),
                )
        offset += chunk_size
    raise SystemExit("Could not parse APK manifest identity")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument("--expected-package", required=True)
    parser.add_argument("--previous-version-code", type=int)
    parser.add_argument("--expected-version-code", type=int)
    args = parser.parse_args()
    package_name, version_code, version_name = manifest_identity(args.apk)
    if package_name != args.expected_package:
        raise SystemExit(f"package changed: {package_name!r} != {args.expected_package!r}")
    if args.previous_version_code is not None and version_code <= args.previous_version_code:
        raise SystemExit(f"versionCode is not monotonic: {version_code} <= {args.previous_version_code}")
    if args.expected_version_code is not None and version_code != args.expected_version_code:
        raise SystemExit(f"unexpected versionCode: {version_code} != {args.expected_version_code}")
    if not version_name:
        raise SystemExit("versionName is empty")
    print(
        "APK_UPDATE_IDENTITY_OK "
        f"package={package_name} versionCode={version_code} versionName={version_name}"
    )

if __name__ == "__main__":
    main()
