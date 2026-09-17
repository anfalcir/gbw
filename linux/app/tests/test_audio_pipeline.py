from __future__ import annotations

import logging
import os
import time
import subprocess
import tempfile
import shutil
import threading
import unittest
from pathlib import Path

from gbw.core.runner import CommandRunner
from gbw.core.system import tool_path
from gbw.models import ProjectDocument,WorkflowConfig,ProjectState
from gbw.services.audio import AudioService

@unittest.skipUnless(tool_path("ffmpeg"), "ffmpeg não disponível")
class AudioPipelineTests(unittest.TestCase):
    def setUp(self):
        self.runner=CommandRunner(logging.getLogger("audio-test"),threading.Event())
        self.svc=AudioService(self.runner,logging.getLogger("audio-test"))

    def make_tone(self,path:Path,freq:int):
        subprocess.run([tool_path("ffmpeg"),"-y","-hide_banner","-loglevel","error","-f","lavfi","-i",f"sine=frequency={freq}:duration=1.2","-ac","2","-ar","44100","-c:a","pcm_f32le",str(path)],check=True)

    def test_mix_and_flac_24(self):
        with tempfile.TemporaryDirectory() as td:
            d=Path(td);a=d/"a.wav";b=d/"b.wav";out=d/"mix.flac";self.make_tone(a,220);self.make_tone(b,330)
            self.svc.mix_stems([a,b],out,"FLAC 24-bit",-1.0)
            self.assertTrue(out.exists());self.assertGreater(out.stat().st_size,1000)
            cp=subprocess.run([tool_path("ffprobe"),"-v","error","-select_streams","a:0","-show_entries","stream=codec_name,sample_rate,bits_per_raw_sample","-of","json",str(out)],stdout=subprocess.PIPE,text=True,check=True)
            self.assertIn('"flac"',cp.stdout);self.assertIn('"44100"',cp.stdout);self.assertIn('"24"',cp.stdout)

    def test_prepare_local_float32(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);project=root/"project"
            for sub in ("source","prepared"): (project/sub).mkdir(parents=True,exist_ok=True)
            src=root/"input.wav";self.make_tone(src,440)
            doc=ProjectDocument(config=WorkflowConfig(source_mode="local",local_file=str(src)),state=ProjectState(project_dir=str(project)))
            self.svc.prepare_source(doc)
            p=Path(doc.state.prepared_wav);self.assertTrue(p.exists())
            cp=subprocess.run([tool_path("ffprobe"),"-v","error","-select_streams","a:0","-show_entries","stream=sample_fmt,sample_rate,channels","-of","json",str(p)],stdout=subprocess.PIPE,text=True,check=True)
            self.assertIn('"flt"',cp.stdout);self.assertIn('"44100"',cp.stdout);self.assertIn('"channels": 2',cp.stdout)

    def test_legacy_preview_metadata_is_blocked_before_separation(self):
        from gbw.core.runner import CommandError
        with tempfile.TemporaryDirectory() as td:
            d=Path(td); project=d/"project"
            for sub in ("source","prepared"): (project/sub).mkdir(parents=True,exist_ok=True)
            prepared=project/"prepared"/"original_44100_f32.wav"; self.make_tone(prepared,440)
            (project/"source"/"original.info.json").write_text('{"format_id":"http_mp3_1_0_preview"}',encoding="utf-8")
            doc=ProjectDocument(config=WorkflowConfig(separator_mode="A"),state=ProjectState(project_dir=str(project),prepared_wav=str(prepared)))
            with self.assertRaises(CommandError):
                self.svc.validate_existing_source(doc)

    def test_bs_roformer_cli_compatibility_without_output_format(self):
        from unittest.mock import patch
        class CaptureRunner:
            def __init__(self): self.args=None
            def run(self,args,label="",on_line=None): self.args=list(args); return ""
        with tempfile.TemporaryDirectory() as td:
            d=Path(td); project=d/"project"
            for sub in ("prepared","separation/A_bs_roformer"): (project/sub).mkdir(parents=True,exist_ok=True)
            prepared=project/"prepared"/"original_44100_f32.wav"; self.make_tone(prepared,440)
            cli=d/"bs-roformer-infer"
            cli.write_text('#!/bin/sh\nif [ "$1" = "--help" ]; then echo "usage: bs-roformer-infer --input_folder INPUT --store_dir STORE --device DEVICE"; fi\n',encoding="utf-8")
            cli.chmod(0o755)
            runner=CaptureRunner(); svc=AudioService(runner,logging.getLogger("audio-test"))
            doc=ProjectDocument(config=WorkflowConfig(device="auto"),state=ProjectState(project_dir=str(project),prepared_wav=str(prepared)))
            with patch("gbw.services.audio.tool_path",side_effect=lambda name: str(cli) if name=="bs-roformer-infer" else tool_path(name)), \
                 patch.object(svc,"resolve_torch_device",return_value="cpu"):
                svc._separate_a(doc)
            self.assertNotIn("--output_format",runner.args)
            self.assertIn("--device",runner.args)
            self.assertEqual(runner.args[runner.args.index("--device")+1],"cpu")
            self.assertNotIn("auto",runner.args)

    def test_bs_roformer_cli_uses_output_format_when_supported(self):
        from unittest.mock import patch
        class CaptureRunner:
            def __init__(self): self.args=None
            def run(self,args,label="",on_line=None): self.args=list(args); return ""
        with tempfile.TemporaryDirectory() as td:
            d=Path(td); project=d/"project"
            for sub in ("prepared","separation/A_bs_roformer"): (project/sub).mkdir(parents=True,exist_ok=True)
            prepared=project/"prepared"/"original_44100_f32.wav"; self.make_tone(prepared,440)
            cli=d/"bs-roformer-infer"
            cli.write_text('#!/bin/sh\nif [ "$1" = "--help" ]; then echo "usage: bs-roformer-infer --input_folder INPUT --output_format FORMAT"; fi\n',encoding="utf-8")
            cli.chmod(0o755)
            runner=CaptureRunner(); svc=AudioService(runner,logging.getLogger("audio-test"))
            doc=ProjectDocument(config=WorkflowConfig(device="auto"),state=ProjectState(project_dir=str(project),prepared_wav=str(prepared)))
            with patch("gbw.services.audio.tool_path",side_effect=lambda name: str(cli) if name=="bs-roformer-infer" else tool_path(name)), \
                 patch.object(svc,"resolve_torch_device",return_value="cpu"):
                svc._separate_a(doc)
            self.assertIn("--output_format",runner.args)
            self.assertIn("wav_float32",runner.args)
            self.assertEqual(runner.args[runner.args.index("--device")+1],"cpu")

    def test_auto_device_resolves_to_cpu_when_no_accelerator(self):
        from unittest.mock import patch
        with patch.object(self.svc,"torch_device_capabilities",return_value={"cuda":False,"mps":False,"xpu":False}):
            self.assertEqual(self.svc.resolve_torch_device("auto","/fake/cli"),"cpu")

    def test_auto_device_prefers_cuda_when_torch_confirms_it(self):
        from unittest.mock import patch
        with patch.object(self.svc,"torch_device_capabilities",return_value={"cuda":True,"mps":False,"xpu":False}):
            self.assertEqual(self.svc.resolve_torch_device("auto","/fake/cli"),"cuda")

    def test_explicit_cuda_is_rejected_when_unavailable(self):
        from unittest.mock import patch
        from gbw.core.runner import CommandError
        with patch.object(self.svc,"torch_device_capabilities",return_value={"cuda":False,"mps":False,"xpu":False}):
            with self.assertRaises(CommandError):
                self.svc.resolve_torch_device("cuda","/fake/cli")

    def test_export_default_creates_guitar_and_backing_pairs(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); project=root/"project"
            for sub in ("exports","pitched/A","separation/A"): (project/sub).mkdir(parents=True,exist_ok=True)
            stems={}
            freqs={"vocals":330,"drums":110,"bass":165,"guitar":440,"piano":550,"other":660}
            for stem,freq in freqs.items():
                path=project/"separation/A"/f"{stem}.wav"; self.make_tone(path,freq); stems[stem]=str(path)
            cfg=WorkflowConfig(export_source_original=False,export_original_pair=True,export_pitched_pair=True,keep_stems=True,semitones=2,output_format="FLAC 24-bit",final_separator="A")
            st=ProjectState(project_dir=str(project),stem_maps={"A":stems})
            doc=ProjectDocument(config=cfg,state=st)
            from unittest.mock import patch
            with patch.object(self.svc,"pitch_stem",side_effect=lambda src,dst,semitones,formants: shutil.copy2(src,dst)):
                created=self.svc.export(doc,-1.0)
            expected=[project/"exports/original/backing.flac",project/"exports/original/guitar.flac",project/"exports/pitch_+2st/backing.flac",project/"exports/pitch_+2st/guitar.flac"]
            self.assertEqual({p.resolve() for p in created},{p.resolve() for p in expected})
            for path in expected:self.assertTrue(path.exists(),path)

    def test_pair_render_uses_same_gain_for_guitar_and_backing(self):
        with tempfile.TemporaryDirectory() as td:
            d=Path(td); backing_src=d/"band.wav"; guitar=d/"guitar.wav"
            # sinais distintos permitem verificar que ambos sofreram o mesmo deslocamento de pico
            self.make_tone(backing_src,220); self.make_tone(guitar,440)
            before_b=self.svc.measure_peak(backing_src); before_g=self.svc.measure_peak(guitar)
            out_b=d/"backing.wav"; out_g=d/"guitar_out.wav"
            self.svc.render_pair([backing_src],guitar,out_b,out_g,"WAV 32-bit float",-12.0)
            after_b=self.svc.measure_peak(out_b); after_g=self.svc.measure_peak(out_g)
            self.assertIsNotNone(before_b);self.assertIsNotNone(before_g);self.assertIsNotNone(after_b);self.assertIsNotNone(after_g)
            self.assertAlmostEqual((after_b-before_b),(after_g-before_g),delta=0.25)


    def test_cancel_cleanup_discards_only_partial_separator_output(self):
        with tempfile.TemporaryDirectory() as td:
            d=Path(td); project=d/"project"
            a=project/"separation/A_bs_roformer"; b=project/"separation/B_demucs"
            a.mkdir(parents=True); b.mkdir(parents=True)
            (a/"guitar.wav").write_bytes(b"partial")
            (b/"guitar.wav").write_bytes(b"keep")
            preview=project/"previews/A_backing_original_sem_guitarra.wav"; preview.parent.mkdir(parents=True);preview.write_bytes(b"partial")
            st=ProjectState(project_dir=str(project),separator_dirs={"A":str(a),"B":str(b)},stem_maps={"A":{"guitar":str(a/"guitar.wav")},"B":{"guitar":str(b/"guitar.wav")}},previews={"A":str(preview)})
            doc=ProjectDocument(config=WorkflowConfig(),state=st)
            self.svc._clear_separator_output(doc,"A")
            self.assertFalse(a.exists())
            self.assertTrue(b.exists())
            self.assertNotIn("A",doc.state.separator_dirs)
            self.assertNotIn("A",doc.state.stem_maps)
            self.assertFalse(preview.exists())
            self.assertIn("B",doc.state.separator_dirs)

    def test_separator_eta_parser_ignores_model_download_and_accepts_inference_progress(self):
        self.assertIsNone(self.svc._parse_separator_fraction("Downloading checkpoint: 50%"))
        self.assertAlmostEqual(self.svc._parse_separator_fraction("chunks: 25%|##"),0.25)
        self.assertAlmostEqual(self.svc._parse_separator_fraction("processing audio chunks 3/12"),0.25)

    def test_roformer_native_eta_parser_reads_v015_output(self):
        kind, seconds = self.svc._parse_roformer_native_eta(
            "Estimated total processing time for this track: 1833.42 seconds"
        )
        self.assertEqual(kind, "total")
        self.assertAlmostEqual(seconds, 1833.42)
        kind, seconds = self.svc._parse_roformer_native_eta(
            "Estimated time remaining: 742.10 seconds"
        )
        self.assertEqual(kind, "remaining")
        self.assertAlmostEqual(seconds, 742.10)
        self.assertIsNone(self.svc._parse_roformer_native_eta("Downloading checkpoint: 100%"))

    def test_roformer_zero_native_eta_means_finalizing_until_process_exits(self):
        class FakeRunner:
            process = None
            def run(self, args, label, on_line=None):
                if on_line:
                    on_line("Estimated total processing time for this track: 10.00 seconds")
                    on_line("Estimated time remaining: 0.00 seconds")
                time.sleep(2.2)
                return ""

        svc = AudioService(FakeRunner(), logging.getLogger("eta-zero-test"))
        updates = []
        svc._run_separator_with_eta(
            ["fake"], "BS-RoFormer-SW 6 stems", "A", "cpu", 30.0,
            lambda frac, msg: updates.append((frac, msg)),
        )
        live = [msg for frac, msg in updates[:-1]]
        self.assertTrue(any("finalizando stems" in msg for msg in live), live)
        self.assertFalse(any("ETA ~0s" in msg or "ETA ~00s" in msg for msg in live), live)
        # A barra deve permanecer indeterminada enquanto o subprocesso vive.
        self.assertTrue(all(frac is None for frac, _ in updates[:-1]), updates)
        self.assertAlmostEqual(updates[-1][0], 1.0)


    def test_roformer_eta_display_marks_age_and_never_creates_percent(self):
        now = 1000.0
        eta, origin, zero = self.svc._roformer_eta_display(600.0, 970.0, now)
        self.assertFalse(zero)
        self.assertAlmostEqual(eta, 570.0)
        self.assertIn("atualizada há", origin)
        self.assertIn("atualizada há", origin)

        eta, origin, zero = self.svc._roformer_eta_display(600.0, 850.0, now)
        self.assertFalse(zero)
        self.assertAlmostEqual(eta, 450.0)
        self.assertIn("última atualização", origin)

        eta, origin, zero = self.svc._roformer_eta_display(60.0, 850.0, now)
        self.assertIsNone(eta)
        self.assertFalse(zero)
        self.assertIn("aguardando atualização", origin)

        eta, origin, zero = self.svc._roformer_eta_display(0.0, 999.0, now)
        self.assertIsNone(eta)
        self.assertTrue(zero)
        self.assertIn("finalizando", origin)

    def test_roformer_live_messages_do_not_show_global_percentage(self):
        class FakeRunner:
            process = None
            def run(self, args, label, on_line=None):
                if on_line:
                    on_line("Estimated total processing time for this track: 120.00 seconds")
                    on_line("Estimated time remaining: 90.00 seconds")
                time.sleep(2.2)
                return ""

        svc = AudioService(FakeRunner(), logging.getLogger("eta-no-percent-test"))
        updates = []
        svc._run_separator_with_eta(
            ["fake"], "BS-RoFormer-SW 6 stems", "A", "cpu", 60.0,
            lambda frac, msg: updates.append((frac, msg)),
        )
        live = updates[:-1]
        self.assertTrue(live)
        self.assertTrue(all(frac is None for frac, _ in live), live)
        self.assertFalse(any("progresso ~" in msg or "%" in msg for _, msg in live), live)
        self.assertTrue(any("atualizada há" in msg or "tempo restante" in msg.lower() for _, msg in live), live)

    def test_linux_cpu_activity_probe_is_safe(self):
        value = self.svc._linux_process_cpu_seconds(os.getpid())
        if os.name == "posix" and Path(f"/proc/{os.getpid()}/stat").exists():
            self.assertIsNotNone(value)
            self.assertGreaterEqual(value, 0.0)

    def test_roformer_generic_100_percent_never_freezes_global_bar(self):
        class FakeRunner:
            process = None
            def run(self, args, label, on_line=None):
                if on_line:
                    on_line("processing audio chunks 12/12")
                time.sleep(2.2)
                return ""

        svc = AudioService(FakeRunner(), logging.getLogger("eta-generic-100-test"))
        updates = []
        svc._run_separator_with_eta(
            ["fake"], "BS-RoFormer-SW 6 stems", "A", "cpu", 60.0,
            lambda frac, msg: updates.append((frac, msg)),
        )
        # Antes da conclusão real, A deve manter a barra indeterminada.
        self.assertTrue(all(frac is None for frac, _ in updates[:-1]), updates)
        self.assertAlmostEqual(updates[-1][0], 1.0)

if __name__=="__main__":unittest.main()
