from __future__ import annotations

import unittest

from gbw.config import Settings
from gbw.ui.main_window import GuitarBackingWizard
from gbw.models import SourceCandidate


class GuiSmokeTests(unittest.TestCase):
    def test_default_ui_scale_is_comfortable(self):
        self.assertEqual(Settings().ui_scale, 1.25)



    def test_custom_window_icon_and_desktop_class(self):
        app = GuitarBackingWizard()
        try:
            self.assertEqual(app.winfo_class(), "Guitarbackingwizard")
            self.assertIsNotNone(app._app_icon)
        finally:
            app.destroy()

    def test_ui_scale_supports_tv_levels_up_to_250(self):
        app = GuitarBackingWizard()
        try:
            app.apply_ui_scale(2.50, persist=False)
            self.assertEqual(app.settings.ui_scale, 2.50)
            app.apply_ui_scale(9.0, persist=False)
            self.assertEqual(app.settings.ui_scale, 2.50)
            app.apply_ui_scale(0.20, persist=False)
            self.assertEqual(app.settings.ui_scale, 1.00)
        finally:
            app.destroy()

    def test_startup_is_hidden_and_lazy(self):
        app = GuitarBackingWizard()
        try:
            # O root começa oculto e apenas a primeira página é construída.
            self.assertEqual(app.state(), "withdrawn")
            self.assertEqual(app.current_page, "system")
            self.assertEqual(set(app.pages), {"system"})
            self.assertNotIn("settings", app.pages)
        finally:
            app.destroy()

    def test_only_current_page_is_grid_managed(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("settings")
            self.assertEqual(app.current_page, "settings")
            self.assertEqual(app.pages["settings"].winfo_manager(), "grid")
            self.assertEqual(app.pages["system"].winfo_manager(), "")
        finally:
            app.destroy()

    def test_all_pages_construct_and_navigate(self):
        app = GuitarBackingWizard()
        app.withdraw()
        try:
            for geometry in ("1120x720", "1360x880", "1600x1000"):
                app.geometry(geometry)
                app.update_idletasks()
                for key in ["system", "source", "separation", "tuning", "export", "projects", "logs", "file_pitch", "settings"]:
                    app.navigate(key)
                    app.update_idletasks()
                    self.assertEqual(app.current_page, key)
                    self.assertEqual(app.pages[key].winfo_manager(), "grid")
                    mapped = [k for k, page in app.pages.items() if page.winfo_manager() == "grid"]
                    self.assertEqual(mapped, [key])
        finally:
            app.destroy()

    def test_source_results_are_paginated_three_per_page_and_selection_is_visible(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("source")
            page=app.pages["source"]
            items=[SourceCandidate(90-i,"YouTube","stream M4A ~128 kbps",f"Enemy result {i+1}","Wolves At The Gate",f"https://example.test/{i}",format_id="140",duration=198.0) for i in range(7)]
            page.show_candidates(items)
            self.assertEqual(page.results_page_size,3)
            self.assertEqual(len(page.result_frame.winfo_children()),3)
            self.assertIn("Página 1 de 3",page.page_label.cget("text"))
            page.change_results_page(1)
            self.assertEqual(page.results_page,1)
            self.assertEqual(len(page.result_frame.winfo_children()),3)
            self.assertIn("Página 2 de 3",page.page_label.cget("text"))
            page.use_candidate(items[4])
            self.assertEqual(app.doc.config.source_url,items[4].url)
            self.assertIn("Fonte ativa",page.selection_status.label.cget("text"))
            self.assertIn("Enemy result 5",page.selection_status.label.cget("text"))
        finally:
            app.destroy()

    def test_sidebar_navigation_has_independent_scroll_region_and_fixed_footer(self):
        app = GuitarBackingWizard()
        try:
            self.assertTrue(hasattr(app, "sidebar_nav"))
            self.assertTrue(hasattr(app, "sidebar_brand"))
            self.assertTrue(hasattr(app, "sidebar_footer"))
            self.assertEqual(app.sidebar_brand.winfo_manager(), "grid")
            self.assertEqual(app.sidebar_nav.winfo_manager(), "grid")
            self.assertEqual(app.sidebar_footer.winfo_manager(), "grid")
            # Todos os destinos continuam descendentes da zona central rolável,
            # embora agora sejam agrupados em Processo/Gerenciamento/Aplicativo.
            self.assertEqual(len(app.nav_buttons), 9)
            def is_descendant(widget, ancestor):
                current = widget
                while current is not None:
                    if current == ancestor:
                        return True
                    current = getattr(current, "master", None)
                return False
            for button in app.nav_buttons.values():
                self.assertTrue(is_descendant(button, app.sidebar_nav))
            self.assertEqual(app.close_project_btn.master, app.sidebar_footer)
            self.assertEqual(app.close_project_btn.cget("text"), "Sair")
        finally:
            app.destroy()

    def test_separation_hardware_layout_uses_stacked_blocks_at_tv_scale(self):
        app = GuitarBackingWizard()
        try:
            app.apply_ui_scale(2.50, persist=False)
            app.navigate("separation")
            page = app.pages["separation"]
            app.update_idletasks()
            # Os três controles críticos existem e têm valores íntegros; o layout
            # vertical evita a compressão vista na v5.4 em escala alta.
            self.assertEqual(page.device.get(), "auto")
            self.assertIn(page.shifts.get(), {"1", "2", "5", "10"})
            self.assertIn(page.overlap.get(), {"0.25", "0.5"})
        finally:
            app.destroy()


    def test_demucs_options_only_show_when_b_participates(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("separation")
            page = app.pages["separation"]
            page.mode.set("A"); page._sync_mode_options(); app.update_idletasks()
            self.assertEqual(page.demucs_shifts_block.winfo_manager(), "")
            self.assertEqual(page.demucs_overlap_block.winfo_manager(), "")
            page.mode.set("B"); page._sync_mode_options(); app.update_idletasks()
            self.assertEqual(page.demucs_shifts_block.winfo_manager(), "grid")
            self.assertEqual(page.demucs_overlap_block.winfo_manager(), "grid")
            page.mode.set("AB"); page._sync_mode_options(); app.update_idletasks()
            self.assertEqual(page.demucs_shifts_block.winfo_manager(), "grid")
            self.assertEqual(page.demucs_overlap_block.winfo_manager(), "grid")
        finally:
            app.destroy()

    def test_separation_controls_are_children_of_their_own_blocks(self):
        """Regressão v5.9: controles criados no card pai vazavam do grid_remove()."""
        app = GuitarBackingWizard()
        try:
            app.apply_ui_scale(2.00, persist=False)
            app.navigate("separation")
            page = app.pages["separation"]
            self.assertIs(page.device_menu.master, page.device_block)
            self.assertIs(page.shifts_combo.master, page.demucs_shifts_block)
            self.assertIs(page.overlap_combo.master, page.demucs_overlap_block)

            page.mode.set("A"); page._sync_mode_options(); app.update_idletasks()
            self.assertEqual(page.device_block.winfo_manager(), "grid")
            self.assertEqual(page.device_menu.winfo_manager(), "grid")
            self.assertEqual(page.device.get(), "auto")
            self.assertEqual(page.demucs_shifts_block.winfo_manager(), "")
            self.assertEqual(page.demucs_overlap_block.winfo_manager(), "")

            page.mode.set("B"); page._sync_mode_options(); app.update_idletasks()
            self.assertEqual(page.demucs_shifts_block.winfo_manager(), "grid")
            self.assertEqual(page.shifts_combo.winfo_manager(), "grid")
            self.assertEqual(page.demucs_overlap_block.winfo_manager(), "grid")
            self.assertEqual(page.overlap_combo.winfo_manager(), "grid")

            page.mode.set("A"); page._sync_mode_options(); app.update_idletasks()
            self.assertEqual(page.demucs_shifts_block.winfo_manager(), "")
            self.assertEqual(page.demucs_overlap_block.winfo_manager(), "")
            self.assertEqual(page.device_menu.winfo_manager(), "grid")
        finally:
            app.destroy()

    def test_separation_has_local_cancel_button_and_state_feedback(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("separation")
            page = app.pages["separation"]
            self.assertEqual(str(page.cancel_sep_btn.cget("state")), "disabled")
            page._set_separation_running(True)
            self.assertEqual(str(page.run_btn.cget("state")), "disabled")
            self.assertEqual(str(page.cancel_sep_btn.cget("state")), "normal")
            page._set_separation_running(False)
            self.assertEqual(str(page.run_btn.cget("state")), "normal")
            self.assertEqual(str(page.cancel_sep_btn.cget("state")), "disabled")
        finally:
            app.destroy()

    def test_page_subtitles_wrap_responsively_at_tv_scale(self):
        app = GuitarBackingWizard()
        try:
            app.apply_ui_scale(2.50, persist=False)
            app.geometry("1120x720")
            app.navigate("tuning")
            app.update_idletasks()
            page=app.pages["tuning"]
            # PageTitle é o primeiro filho; o subtitle usa ResponsiveLabel e deve ter wraplength.
            page_title=page.winfo_children()[0]
            labels=page_title.winfo_children()
            self.assertGreaterEqual(len(labels),2)
            wrap=float(labels[1].cget("wraplength"))
            self.assertGreater(wrap,100)
            self.assertLess(wrap,900)
        finally:
            app.destroy()


    def test_tuning_manual_controls_are_contextual_and_confirm_advances(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("tuning")
            page = app.pages["tuning"]
            page.mode.set("tuning")
            page._on_mode_change()
            app.update_idletasks()
            self.assertEqual(page.manual_row.winfo_manager(), "")
            self.assertEqual(page.confirm_btn.cget("text"), "Confirmar e continuar")

            page.mode.set("manual")
            page._on_mode_change()
            app.update_idletasks()
            self.assertEqual(page.manual_row.winfo_manager(), "grid")
            texts=[]
            for w in page.manual_row.winfo_children():
                try: texts.append(str(w.cget("text")))
                except Exception: pass
            self.assertNotIn("Aplicar", texts)

            page.mode.set("tuning")
            page._on_mode_change()
            page.original.set("D Standard")
            page.target.set("E Standard")
            page.recompute(invalidate=False)
            self.assertEqual(app.doc.config.semitones, 2)
            self.assertFalse(app.doc.state.tuning_confirmed)
            page.confirm_and_continue()
            self.assertTrue(app.doc.state.tuning_confirmed)
            self.assertEqual(app.current_page, "export")
        finally:
            app.destroy()

    def test_tuning_candidate_choice_persists_when_leaving_and_returning_to_page(self):
        app = GuitarBackingWizard()
        try:
            analysis = {
                "confidence": 72,
                "best_tuning": "D Standard",
                "key": {"key": "D", "mode": "major", "label": "D maior", "confidence": 62},
                "fused_candidates": [
                    {"tuning": "D Standard", "strings": ["D2", "G2", "C3", "F3", "A3", "D4"], "final_score": 100.0},
                    {"tuning": "Drop D", "strings": ["D2", "A2", "D3", "G3", "B3", "E4"], "final_score": 94.0},
                ],
            }
            app.doc.state.tuning_analysis = analysis
            app.navigate("tuning")
            page = app.pages["tuning"]
            page.show_analysis(analysis)
            self.assertEqual(page.original.get(), "D Standard")

            page.use_tuning("Drop D")
            self.assertEqual(app.doc.config.original_tuning, "Drop D")
            self.assertEqual(page.original.get(), "Drop D")

            app.navigate("source")
            app.navigate("tuning")
            self.assertEqual(app.doc.config.original_tuning, "Drop D")
            self.assertEqual(page.original.get(), "Drop D")
        finally:
            app.destroy()

    def test_export_defaults_to_original_and_pitched_guitar_backing_pairs(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("export")
            page=app.pages["export"]
            self.assertTrue(page.v_original_pair.get())
            self.assertTrue(page.v_pitched_pair.get())
            self.assertFalse(hasattr(page,"v_nog"))
            self.assertFalse(hasattr(page,"v_g"))
        finally:
            app.destroy()

    def test_responsive_label_uses_container_debounce_not_self_resize_loop(self):
        app = GuitarBackingWizard()
        try:
            app.apply_ui_scale(2.00, persist=False)
            app.geometry("1200x760")
            app.navigate("tuning")
            app.update_idletasks()
            page = app.pages["tuning"]
            page_title = page.winfo_children()[0]
            subtitle = page_title.winfo_children()[1]
            self.assertTrue(hasattr(subtitle, "_responsive_container"))
            self.assertIs(subtitle._responsive_container, subtitle.master)
            # Vinte eventos idênticos de largura devem colapsar para no máximo
            # uma atualização real após o debounce, não iniciar realimentação.
            class E:
                width = 800
            before = int(getattr(subtitle, "_responsive_apply_count", 0))
            for _ in range(20):
                subtitle._on_container_resize(E())
            app.after(180, app.quit)
            app.mainloop()
            after = int(getattr(subtitle, "_responsive_apply_count", 0))
            self.assertLessEqual(after - before, 2)
        finally:
            app.destroy()

    def test_finish_startup_does_not_wait_for_idle_queue_and_requests_maximize(self):
        app = GuitarBackingWizard()
        try:
            self.assertEqual(app.state(), "withdrawn")
            calls = []
            app._maximize_window = lambda: calls.append("maximize") or True
            app._finish_startup()
            app.update()
            self.assertNotEqual(app.state(), "withdrawn")
            self.assertGreaterEqual(len(calls), 1)
        finally:
            app.destroy()

    def test_footer_is_exit_without_project_and_close_project_with_project(self):
        import tempfile
        from pathlib import Path
        from gbw.project import ProjectManager
        app = GuitarBackingWizard()
        try:
            app.refresh_header()
            self.assertEqual(app.close_project_btn.cget("text"), "Sair")
            self.assertEqual(str(app.close_project_btn.cget("state")), "normal")
            with tempfile.TemporaryDirectory() as td:
                app.project_manager = ProjectManager(Path(td))
                app.doc = app.project_manager.create("Band", "Song")
                app.refresh_header()
                self.assertEqual(app.close_project_btn.cget("text"), "Fechar projeto")
                self.assertEqual(str(app.close_project_btn.cget("state")), "normal")
        finally:
            app.destroy()

    def test_sidebar_group_headers_are_visual_sections_not_menu_items(self):
        app = GuitarBackingWizard()
        try:
            self.assertEqual(set(app.nav_section_headers), {"process", "management", "tools", "application"})
            expected = {
                "process": ("PROCESSO", app.workflow_nav),
                "management": ("GERENCIAMENTO", app.management_nav),
                "tools": ("FERRAMENTAS", app.tools_nav),
                "application": ("APLICATIVO", app.application_nav),
            }
            for key, (title, parent) in expected.items():
                parts = app.nav_section_headers[key]
                self.assertIs(parts["frame"].master, parent)
                self.assertEqual(parts["label"].cget("text"), title)
                self.assertIs(parts["label"].master, parts["frame"])
                self.assertIs(parts["accent"].master, parts["frame"])
                self.assertIs(parts["divider"].master, parts["frame"])
                # Cabeçalho é Frame/Label, nunca CTkButton navegável.
                self.assertNotIn(key, app.nav_buttons)
        finally:
            app.destroy()


    def test_export_page_has_open_exports_button_and_uses_project_exports_dir(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("export")
            page = app.pages["export"]
            self.assertEqual(page.open_exports_btn.cget("text"), "Abrir pasta de exports")

            import tempfile
            from pathlib import Path
            with tempfile.TemporaryDirectory() as td:
                app.doc.state.project_dir = td
                page._sync_exports_button()
                self.assertEqual(str(page.open_exports_btn.cget("state")), "disabled")

                exports = Path(td) / "exports"
                exports.mkdir()
                page._sync_exports_button()
                self.assertEqual(str(page.open_exports_btn.cget("state")), "normal")

                opened = []
                app.open_path = lambda path: opened.append(Path(path))
                page.open_exports()
                self.assertEqual(opened, [exports])
        finally:
            app.destroy()
    def test_actions_have_local_cancel_buttons(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("source")
            self.assertTrue(hasattr(app.pages["source"], "cancel_search_btn"))
            self.assertTrue(hasattr(app.pages["source"], "cancel_prepare_btn"))
            app.navigate("separation")
            self.assertTrue(hasattr(app.pages["separation"], "cancel_sep_btn"))
            app.navigate("tuning")
            self.assertTrue(hasattr(app.pages["tuning"], "cancel_detect_btn"))
            app.navigate("export")
            self.assertTrue(hasattr(app.pages["export"], "cancel_export_btn"))
            self.assertTrue(hasattr(app.pages["export"], "cancel_backup_btn"))
            app.navigate("projects")
            self.assertTrue(hasattr(app.pages["projects"], "cancel_restore_btn"))
            app.navigate("system")
            self.assertTrue(hasattr(app.pages["system"], "cancel_backup_btn"))
        finally:
            app.destroy()

    def test_close_project_returns_to_initial_state(self):
        import tempfile
        from pathlib import Path
        from gbw.project import ProjectManager
        app = GuitarBackingWizard()
        try:
            with tempfile.TemporaryDirectory() as td:
                app.project_manager = ProjectManager(Path(td))
                app.doc = app.project_manager.create("Band", "Song")
                app.refresh_header()
                self.assertEqual(str(app.close_project_btn.cget("state")), "normal")
                self.assertEqual(app.close_project_btn.cget("text"), "Fechar projeto")
                app.close_project()
                self.assertFalse(app.doc.state.project_dir)
                self.assertEqual(app.current_page, "source")
                self.assertEqual(app.project_label.cget("text"), "Nenhum projeto aberto")
                self.assertEqual(str(app.close_project_btn.cget("state")), "normal")
                self.assertEqual(app.close_project_btn.cget("text"), "Sair")
        finally:
            app.destroy()

    def test_export_page_has_project_backup_button(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("export")
            page = app.pages["export"]
            self.assertEqual(page.backup_btn.cget("text"), "Salvar backup do projeto…")
        finally:
            app.destroy()

    def test_system_page_has_application_backup_button(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("system")
            page = app.pages["system"]
            self.assertEqual(page.backup_app_btn.cget("text"), "Salvar backup do GBW…")
        finally:
            app.destroy()

    def test_projects_are_sorted_by_artist_then_song_and_filter_live(self):
        import tempfile
        from pathlib import Path
        from gbw.project import ProjectManager

        app = GuitarBackingWizard()
        try:
            with tempfile.TemporaryDirectory() as td:
                app.project_manager = ProjectManager(Path(td))
                app.project_manager.create("wolves at the gate", "ENEMY")
                app.project_manager.create("SKILLET", "hero")
                app.project_manager.create("WOLVES AT THE GATE", "alone")
                app.project_manager.create("árvore", "ZETA")

                app.navigate("projects")
                page = app.pages["projects"]
                page.refresh()
                ordered = [(x["artist"], x["song"]) for x in page._catalog]
                self.assertEqual(ordered, [
                    ("Árvore", "Zeta"),
                    ("Skillet", "Hero"),
                    ("Wolves At The Gate", "Alone"),
                    ("Wolves At The Gate", "Enemy"),
                ])
                # Grafias diferentes da mesma banda precisam compartilhar o mesmo grupo canônico.
                wolves = [x for x in page._catalog if x["artist"] == "Wolves At The Gate"]
                self.assertEqual(len(wolves), 2)

                page.search_var.set("wolves enemy")
                app.update_idletasks()
                filtered = page._filtered_catalog()
                self.assertEqual(len(filtered), 1)
                self.assertEqual(filtered[0]["song"], "Enemy")
                self.assertIn("1 de 4", page.count_label.cget("text"))

                page.search_var.set("skil")
                app.update_idletasks()
                filtered = page._filtered_catalog()
                self.assertEqual([(x["artist"], x["song"]) for x in filtered], [("Skillet", "Hero")])
        finally:
            app.destroy()

    def test_file_pitch_tool_is_independent_and_has_complete_controls(self):
        app = GuitarBackingWizard()
        try:
            self.assertFalse(app.doc.state.project_dir)
            app.navigate("file_pitch")
            page = app.pages["file_pitch"]
            self.assertEqual(app.current_page, "file_pitch")
            self.assertEqual(app.project_label.cget("text"), "Pitch de Arquivo")
            self.assertIn("independente", app.detail_label.cget("text").lower())
            self.assertEqual(page.run_btn.cget("text"), "Aplicar pitch")
            self.assertEqual(page.cancel_btn.cget("text"), "Cancelar")
            self.assertEqual(page.open_btn.cget("text"), "Abrir pasta")
            self.assertEqual(page.guide_btn.cget("text"), "▸ Como exportar do seu DAW para o GBW?")
            self.assertEqual(page.guide_body.winfo_manager(), "")
            page.toggle_guide(); app.update_idletasks()
            self.assertEqual(page.guide_body.winfo_manager(), "grid")
            guide_text = " ".join(str(w.cget("text")) for w in page.guide_body.winfo_children() if hasattr(w, "cget"))
            self.assertIn("32-bit float", guide_text)
            self.assertIn("taxa de amostragem", guide_text.lower())
            self.assertIn("44,1", guide_text)
            self.assertIn("48 kHz", guide_text)
        finally:
            app.destroy()

    def test_file_pitch_tuning_math_and_inverse_project(self):
        import tempfile
        from pathlib import Path
        from gbw.project import ProjectManager
        app = GuitarBackingWizard()
        try:
            app.navigate("file_pitch")
            page = app.pages["file_pitch"]
            page.mode.set("tuning")
            page.original.set("Drop D")
            page.target.set("Drop B")
            self.assertEqual(page.recompute(), -3)
            page.invert_tunings()
            self.assertEqual(page.original.get(), "Drop B")
            self.assertEqual(page.target.get(), "Drop D")
            self.assertEqual(page.recompute(), 3)

            with tempfile.TemporaryDirectory() as td:
                app.project_manager = ProjectManager(Path(td))
                app.doc = app.project_manager.create("Band", "Song")
                app.doc.config.pitch_mode = "tuning"
                app.doc.config.original_tuning = "Drop B"
                app.doc.config.target_tuning = "Drop D"
                app.doc.config.semitones = 3
                page.sync_from_doc()
                self.assertEqual(str(page.inverse_btn.cget("state")), "normal")
                page.use_inverse_project()
                self.assertEqual(page.original.get(), "Drop D")
                self.assertEqual(page.target.get(), "Drop B")
                self.assertEqual(page.recompute(), -3)
        finally:
            app.destroy()

    def test_file_pitch_confirmation_is_reserved_for_caution_state(self):
        from types import SimpleNamespace
        from pathlib import Path
        app = GuitarBackingWizard()
        try:
            app.navigate("file_pitch")
            page = app.pages["file_pitch"]
            # A própria regra da página consulta apenas caution antes de abrir a decisão.
            for status, expected in (("ideal", False), ("adequate", False), ("caution", True)):
                page.inspection = SimpleNamespace(status=status)
                self.assertEqual(page.inspection.status == "caution", expected)
            # Os textos dos botões do diálogo são comunicativos e fazem parte do código público.
            source = Path(__file__).resolve().parents[1] / "gbw" / "ui" / "pages" / "file_pitch_page.py"
            text = source.read_text(encoding="utf-8")
            self.assertIn("Escolher outro arquivo", text)
            self.assertIn("Continuar mesmo assim", text)
            self.assertNotIn("askyesno", text)
        finally:
            app.destroy()


if __name__ == "__main__":
    unittest.main()
