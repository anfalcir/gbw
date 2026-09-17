from __future__ import annotations

import json
import logging
import tempfile
import threading
import time
import sys
import subprocess
import unittest
import zipfile
from pathlib import Path

from gbw.services.backup import BackupService, PROJECT_BACKUP_MARKER, APP_BACKUP_MARKER
from gbw.models import tuning_delta, tuning_notes, transpose_key_label, WorkflowConfig, ProjectState, ProjectDocument
from gbw.project import ProjectManager, normalize_project_text
from gbw.services.source_search import SourceSearchService, title_matches_song, artist_matches_request
from gbw.services.tuning import TuningService
from gbw.services.file_pitch import FilePitchService, AudioInspection
from gbw.core.system import tool_path
from gbw.core.runner import CommandRunner
from gbw.ui.widgets import configure_scroll_speed


class DummyRunner:
    pass

class CoreTests(unittest.TestCase):
    def test_tuning_math(self):
        self.assertEqual(tuning_delta("Drop C", "Drop D"), 2)
        self.assertEqual(tuning_delta("Drop B", "Drop D"), 3)
        self.assertEqual(tuning_delta("D Standard", "E Standard"), 2)
        self.assertIsNone(tuning_delta("E Standard", "Drop D"))
        self.assertIn("C2", tuning_notes("Drop C"))
        self.assertEqual(transpose_key_label("C", "minor", 2), "D menor")

    def test_project_name_normalization_is_global_and_persistent(self):
        self.assertEqual(normalize_project_text("  wolves   AT the GATE  "), "Wolves At The Gate")
        self.assertEqual(normalize_project_text("ENEMY"), "Enemy")
        self.assertEqual(normalize_project_text("árvore"), "Árvore")
        with tempfile.TemporaryDirectory() as td:
            pm = ProjectManager(Path(td))
            doc = pm.create("wolves AT THE gate", "ENEMY")
            self.assertEqual(doc.config.artist, "Wolves At The Gate")
            self.assertEqual(doc.config.song, "Enemy")
            # Mesmo se algum chamador alterar o objeto com caixa inconsistente, save corrige.
            doc.config.artist = "WOLVES AT THE GATE"
            doc.config.song = "enemy"
            pm.save(doc)
            raw = json.loads(pm.manifest_path(doc).read_text(encoding="utf-8"))
            self.assertEqual(raw["config"]["artist"], "Wolves At The Gate")
            self.assertEqual(raw["config"]["song"], "Enemy")
            loaded = pm.load(pm.manifest_path(doc))
            self.assertEqual(loaded.config.artist, "Wolves At The Gate")
            self.assertEqual(loaded.config.song, "Enemy")

    def test_project_roundtrip(self):
        with tempfile.TemporaryDirectory() as td:
            pm=ProjectManager(Path(td))
            doc=pm.create("Wolves At The Gate","Enemy")
            doc.config.original_tuning="Drop C";doc.config.target_tuning="Drop D";doc.config.semitones=2
            doc.state.stage="separated";pm.save(doc)
            loaded=pm.load(pm.manifest_path(doc))
            self.assertEqual(loaded.config.artist,"Wolves At The Gate")
            self.assertEqual(loaded.config.semitones,2)
            self.assertEqual(loaded.state.stage,"separated")


    def test_load_legacy_v4_manifest(self):
        with tempfile.TemporaryDirectory() as td:
            d=Path(td)
            legacy={
                "stage":"separated",
                "config":{"artist":"Skillet","song":"Monster","source_mode":"url","source_url":"https://example.test","source_format_id":"251","original_tuning":"Drop B","target_tuning":"Drop D","semitones":3},
                "source_native":str(d/"source.webm"),
                "prepared_wav":str(d/"prepared.wav"),
                "separator_dirs":{"A":str(d/"sep")},
                "stems":{"A":{"guitar":str(d/"guitar.wav")}},
            }
            mf=d/"workflow_manifest.json";mf.write_text(json.dumps(legacy),encoding="utf-8")
            loaded=ProjectManager.load(mf)
            self.assertEqual(loaded.config.artist,"Skillet")
            self.assertEqual(loaded.config.semitones,3)
            self.assertEqual(loaded.state.project_dir,str(d))
            self.assertEqual(loaded.state.stage,"separated")

    def test_source_scoring_prefers_lossless(self):
        svc=SourceSearchService(DummyRunner(),logging.getLogger("test"))
        lossless={"title":"Enemy","artist":"Wolves At The Gate","formats":[{"vcodec":"none","ext":"flac","acodec":"flac","format_id":"download"}]}
        stream={"title":"Enemy (Official Audio)","uploader":"Wolves At The Gate - Topic","formats":[{"vcodec":"none","ext":"webm","acodec":"opus","format_id":"251","abr":140}]}
        a=svc._candidate(lossless,"Bandcamp","https://example/a","Wolves At The Gate","Enemy")
        b=svc._candidate(stream,"YouTube","https://example/b","Wolves At The Gate","Enemy")
        self.assertGreater(a.quality_bonus,b.quality_bonus)
        self.assertGreaterEqual(a.score,b.score)



    def test_source_title_gate_rejects_different_song_names(self):
        self.assertTrue(title_matches_song("Enemy", "Enemy (Official Audio)"))
        self.assertTrue(title_matches_song("The Pretender", "Wolves At The Gate - Pretender (Official Audio)"))
        self.assertFalse(title_matches_song("Enemy", "Eclipse"))
        self.assertFalse(title_matches_song("Enemy", "Alone"))
        self.assertFalse(title_matches_song("Enemy", "Enemies"))

    def test_source_artist_gate_accepts_artist_in_title_or_uploader(self):
        self.assertTrue(artist_matches_request("Wolves At The Gate", "Wolves At The Gate - Enemy", "Solid State Records"))
        self.assertTrue(artist_matches_request("Wolves At The Gate", "Enemy", "Wolves At The Gate"))
        self.assertFalse(artist_matches_request("Wolves At The Gate", "Enemy", "Another Artist"))

    def test_candidate_relevance_requires_song_and_artist(self):
        svc=SourceSearchService(DummyRunner(),logging.getLogger("test"))
        good=svc._candidate({"title":"Enemy (Official Audio)","uploader":"Wolves At The Gate - Topic","duration":198,
            "formats":[{"vcodec":"none","ext":"m4a","acodec":"aac","format_id":"140","abr":129}]},
            "YouTube","https://example/good","Wolves At The Gate","Enemy")
        wrong_song=svc._candidate({"title":"Eclipse","uploader":"Wolves At The Gate","duration":240,
            "formats":[{"vcodec":"none","ext":"webm","acodec":"opus","format_id":"251","abr":130}]},
            "YouTube","https://example/wrong-song","Wolves At The Gate","Enemy")
        wrong_artist=svc._candidate({"title":"Enemy","uploader":"Another Artist","duration":198,
            "formats":[{"vcodec":"none","ext":"webm","acodec":"opus","format_id":"251","abr":130}]},
            "YouTube","https://example/wrong-artist","Wolves At The Gate","Enemy")
        self.assertTrue(svc._candidate_matches_request(good,"Wolves At The Gate","Enemy"))
        self.assertFalse(svc._candidate_matches_request(wrong_song,"Wolves At The Gate","Enemy"))
        self.assertFalse(svc._candidate_matches_request(wrong_artist,"Wolves At The Gate","Enemy"))


    def test_search_filters_unrelated_tracks_before_display(self):
        class FakeSearch(SourceSearchService):
            def _bandcamp_urls(self, query, limit): return []
            def _flat_search(self, prefix, query, count):
                return [
                    {"id":"good","title":"Enemy"},
                    {"id":"eclipse","title":"Eclipse"},
                    {"id":"alone","title":"Alone"},
                ]
            def _entry_url(self, entry, source): return "https://example.test/" + entry["id"]
            def inspect_url(self, url):
                title={"good":"Enemy","eclipse":"Eclipse","alone":"Alone"}[url.rsplit("/",1)[-1]]
                return {"title":title,"artist":"Wolves At The Gate","duration":198,
                    "formats":[{"vcodec":"none","ext":"m4a","acodec":"aac","format_id":"140","abr":129}]}
        svc=FakeSearch(DummyRunner(),logging.getLogger("test"))
        items=svc.search("Wolves At The Gate","Enemy","Robusta")
        self.assertTrue(items)
        self.assertTrue(all(title_matches_song("Enemy",c.title) for c in items))
        self.assertEqual({c.title for c in items},{"Enemy"})

    def test_source_preview_is_rejected(self):
        svc=SourceSearchService(DummyRunner(),logging.getLogger("test"))
        preview={
            "title":"Enemy","artist":"Wolves At The Gate","duration":29.8,
            "formats":[{"vcodec":"none","ext":"mp3","acodec":"mp3","format_id":"http_mp3_1_0_preview","abr":128}]
        }
        c=svc._candidate(preview,"SoundCloud","https://example/preview","Wolves At The Gate","Enemy")
        self.assertTrue(c.preview_only)
        self.assertLessEqual(c.score,5)
        self.assertTrue("trecho" in c.reason.lower() or "preview" in c.reason.lower())

    def test_duration_consensus_penalizes_truncated_candidate(self):
        svc=SourceSearchService(DummyRunner(),logging.getLogger("test"))
        def mk(seconds):
            return svc._candidate({"title":"Enemy","artist":"Wolves At The Gate","duration":seconds,
                "formats":[{"vcodec":"none","ext":"webm","acodec":"opus","format_id":"251","abr":140}]},
                "YouTube","https://example/"+str(seconds),"Wolves At The Gate","Enemy")
        a,b,short=mk(220),mk(218),mk(80)
        before=short.score
        svc._apply_duration_consensus([a,b,short],"Enemy")
        self.assertLess(short.score,before)
        self.assertTrue(short.duration_warning)


    def test_bs_roformer_install_spec_targets_published_baseline(self):
        root = Path(__file__).resolve().parents[1]
        req = (root / "requirements.txt").read_text(encoding="utf-8")
        installer = (root / "install.sh").read_text(encoding="utf-8")
        self.assertIn("bs-roformer-infer>=0.1.5", req)
        self.assertIn("bs-roformer-infer>=0.1.5", installer)
        self.assertNotIn("bs-roformer-infer>=0.1.6", req)
        self.assertNotIn("bs-roformer-infer>=0.1.6", installer)


    def test_runner_cancel_terminates_active_process(self):
        runner=CommandRunner(logging.getLogger("cancel-test"),threading.Event())
        errors=[]
        def work():
            try:
                runner.run([sys.executable,"-c","import time; time.sleep(30)"],"sleep test")
            except Exception as exc:
                errors.append(exc)
        th=threading.Thread(target=work,daemon=True);th.start()
        deadline=time.monotonic()+3
        while runner.process is None and time.monotonic()<deadline:
            time.sleep(0.02)
        self.assertIsNotNone(runner.process)
        runner.cancel()
        th.join(timeout=5)
        self.assertFalse(th.is_alive(),"cancelamento não encerrou o subprocesso")
        self.assertTrue(errors)
        self.assertIn("cancelad",str(errors[0]).lower())

    def test_tuning_fusion_agreement_raises_confidence(self):
        local={"guitar":{"best":"Drop C","confidence_local":65,"candidates":[
            {"tuning":"Drop C","local_score":100.0,"strings":["C2","G2","C3","F3","A3","D4"]},
            {"tuning":"C Standard","local_score":92.0,"strings":["C2","F2","Bb2","Eb3","G3","C4"]},
        ]},"key":{"key":"C","mode":"minor"}}
        online={"counts":{"Drop C":3},"evidence":[]}
        fused=TuningService.fuse(local,online)
        self.assertEqual(fused["best_tuning"],"Drop C")
        self.assertGreater(fused["confidence"],65)
    def test_scroll_speed_scales_with_ui(self):
        class Canvas:
            def __init__(self): self.kw = {}
            def configure(self, **kwargs): self.kw.update(kwargs)
        class Scrollable:
            def __init__(self): self._parent_canvas = Canvas()
        w = Scrollable()
        applied = configure_scroll_speed(w, 2.0)
        self.assertEqual(applied, 110)
        self.assertEqual(w._parent_canvas.kw.get("yscrollincrement"), 110)
        applied = configure_scroll_speed(w, 2.5)
        self.assertEqual(applied, 138)
        self.assertEqual(w._parent_canvas.kw.get("yscrollincrement"), 138)

    def test_project_backup_restore_compact_roundtrip(self):
        with tempfile.TemporaryDirectory() as td, tempfile.TemporaryDirectory() as rd, tempfile.TemporaryDirectory() as bd:
            pm = ProjectManager(Path(td))
            doc = pm.create("Wolves At The Gate", "Enemy")
            pdir = Path(doc.state.project_dir)
            source = pdir / "source" / "original.m4a"
            source.write_bytes(b"source")
            prepared = pdir / "prepared" / "original.wav"
            prepared.write_bytes(b"prepared")
            export = pdir / "exports" / "pitch_+2st" / "backing.flac"
            export.parent.mkdir(parents=True, exist_ok=True)
            export.write_bytes(b"export")
            stem = pdir / "separation" / "A_bs_roformer" / "song_guitar.wav"
            stem.parent.mkdir(parents=True, exist_ok=True)
            stem.write_bytes(b"large-stem")
            doc.state.source_native = str(source)
            doc.state.prepared_wav = str(prepared)
            doc.state.tuning_analysis = {"best_tuning":"D Standard"}
            doc.state.tuning_confirmed = True
            doc.config.original_tuning = "D Standard"
            doc.config.target_tuning = "E Standard"
            doc.config.semitones = 2
            pm.save(doc)

            svc = BackupService(Path(__file__).resolve().parents[1], logging.getLogger("backup-test"))
            backup = svc.backup_project(doc, Path(bd))
            self.assertTrue(backup.exists())
            with zipfile.ZipFile(backup) as zf:
                names = set(zf.namelist())
                self.assertIn(PROJECT_BACKUP_MARKER, names)
                self.assertTrue(any("files/source/" in n for n in names))
                self.assertTrue(any("files/prepared/" in n for n in names))
                self.assertTrue(any("files/exports/" in n for n in names))
                self.assertFalse(any("separation/" in n for n in names))

            restored_pm = ProjectManager(Path(rd))
            manifest = svc.restore_project(backup, restored_pm)
            restored = restored_pm.load(manifest)
            self.assertEqual(restored.config.original_tuning, "D Standard")
            self.assertEqual(restored.config.target_tuning, "E Standard")
            self.assertTrue(Path(restored.state.source_native).exists())
            self.assertTrue(Path(restored.state.prepared_wav).exists())
            self.assertEqual(restored.state.stage, "source_prepared")
            self.assertTrue((Path(restored.state.project_dir)/"exports"/"pitch_+2st"/"backing.flac").exists())

    def test_project_delete_is_restricted_to_project_root(self):
        with tempfile.TemporaryDirectory() as td:
            pm = ProjectManager(Path(td))
            doc = pm.create("Band", "Song")
            manifest = pm.manifest_path(doc)
            project_dir = manifest.parent
            deleted = pm.delete_project(manifest)
            self.assertEqual(deleted, project_dir)
            self.assertFalse(project_dir.exists())

    def test_application_backup_contains_installer_and_excludes_user_data(self):
        with tempfile.TemporaryDirectory() as bd:
            root = Path(__file__).resolve().parents[1]
            svc = BackupService(root, logging.getLogger("app-backup-test"))
            backup = svc.backup_application(Path(bd))
            self.assertTrue(backup.exists())
            with zipfile.ZipFile(backup) as zf:
                names = set(zf.namelist())
                self.assertIn(f"guitar_backing_wizard/{APP_BACKUP_MARKER}", names)
                self.assertIn("guitar_backing_wizard/install.sh", names)
                self.assertIn("guitar_backing_wizard/requirements.txt", names)
                self.assertTrue(any(n.startswith("guitar_backing_wizard/gbw/") for n in names))
                self.assertFalse(any("venv/" in n or "/logs/" in n for n in names))

    @unittest.skipUnless(tool_path("ffmpeg") and tool_path("ffprobe"), "ffmpeg/ffprobe não disponíveis")
    def test_file_pitch_quality_classifies_ideal_adequate_and_caution(self):
        from gbw.services.audio import AudioService
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            ffmpeg = tool_path("ffmpeg")
            # WAV float32 48 kHz, com headroom: ideal.
            ideal = d / "ideal.wav"
            subprocess.run([ffmpeg, "-y", "-v", "error", "-f", "lavfi", "-i",
                            "sine=frequency=440:duration=0.4", "-af", "volume=-12dB",
                            "-ar", "48000", "-ac", "1", "-c:a", "pcm_f32le", str(ideal)], check=True)
            # FLAC 24-bit lossless: adequado, sem confirmação agressiva.
            adequate = d / "adequate.flac"
            subprocess.run([ffmpeg, "-y", "-v", "error", "-f", "lavfi", "-i",
                            "sine=frequency=330:duration=0.4", "-af", "volume=-12dB",
                            "-ar", "44100", "-ac", "1", "-c:a", "flac", "-sample_fmt", "s32",
                            "-bits_per_raw_sample", "24", str(adequate)], check=True)
            # MP3: ressalva por compressão com perdas.
            caution = d / "caution.mp3"
            subprocess.run([ffmpeg, "-y", "-v", "error", "-f", "lavfi", "-i",
                            "sine=frequency=220:duration=0.4", "-af", "volume=-12dB",
                            "-ar", "44100", "-ac", "1", "-c:a", "libmp3lame", "-b:a", "192k", str(caution)], check=True)
            runner = CommandRunner(logging.getLogger("file-pitch-quality"), threading.Event())
            svc = FilePitchService(runner, AudioService(runner, logging.getLogger("file-pitch-quality")))
            a = svc.inspect(ideal)
            b = svc.inspect(adequate)
            c = svc.inspect(caution)
            self.assertEqual(a.status, "ideal")
            self.assertEqual(a.sample_rate, 48000)
            self.assertEqual(a.channels, 1)
            self.assertEqual(b.status, "adequate")
            self.assertTrue(b.is_lossless)
            self.assertEqual(c.status, "caution")
            self.assertFalse(c.is_lossless)
            self.assertTrue(any("perdas" in issue.title.lower() for issue in c.issues))

    def test_file_pitch_process_delegates_to_same_audio_motor(self):
        calls = []
        class FakeAudio:
            def pitch_file(self, *args):
                calls.append(args)
                return args[1]
        svc = FilePitchService(DummyRunner(), FakeAudio())
        inspection = AudioInspection(
            path=Path("/tmp/input.wav"), container="wav", codec="pcm_f32le",
            sample_rate=48000, channels=1, channel_layout="mono", sample_fmt="flt",
            bit_depth=32, is_float=True, is_lossless=True, duration=123.0,
        )
        out = Path("/tmp/output.wav")
        result = svc.process(inspection, out, -3, False, "WAV 32-bit float")
        self.assertEqual(result, out)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][0], inspection.path)
        self.assertEqual(calls[0][2], -3)
        self.assertEqual(calls[0][5], 48000)
        self.assertEqual(calls[0][6], 1)


if __name__ == "__main__":
    unittest.main()
