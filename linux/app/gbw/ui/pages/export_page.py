from __future__ import annotations
from pathlib import Path
from tkinter import filedialog

from gbw.ui.ctk_compat import ctk
from gbw.ui.widgets import PageTitle, Card, InlineMessage, ResponsiveLabel
from gbw.ui.theme import COLORS
from gbw.models import tuning_delta


class ExportPage(ctk.CTkScrollableFrame):
    page_key = "export"

    def __init__(self, master, app):
        super().__init__(master, fg_color="transparent", corner_radius=0)
        self.app = app
        self.grid_columnconfigure(0, weight=1)

        PageTitle(self, "Exportação", "Exporte e preserve os arquivos finais da música.").grid(
            row=0, column=0, sticky="ew", pady=(0, 14)
        )

        opt = Card(self, "Pares de áudio", "Escolha o que deseja exportar.")
        opt.grid(row=1, column=0, sticky="ew", pady=6)
        cfg = app.doc.config
        self.v_source = ctk.BooleanVar(value=cfg.export_source_original)
        self.v_original_pair = ctk.BooleanVar(value=cfg.export_original_pair)
        self.v_pitched_pair = ctk.BooleanVar(value=cfg.export_pitched_pair)
        self.v_stems = ctk.BooleanVar(value=cfg.keep_stems)

        rows = [
            (self.v_original_pair, "Tom ORIGINAL — exportar backing + guitar", "Útil para estudar/tocar na afinação original."),
            (self.v_pitched_pair, "Pitch AJUSTADO — exportar backing + guitar", "Principal quando você adapta a música para outra afinação."),
            (self.v_source, "Guardar também a fonte original", "Salva uma cópia da fonte usada no projeto."),
            (self.v_stems, "Manter arquivos separados", "Mantém as partes separadas da música."),
        ]
        for i, (var, text, desc) in enumerate(rows):
            block = ctk.CTkFrame(opt.body, fg_color="transparent")
            block.grid(row=i, column=0, sticky="ew", pady=(3, 10))
            block.grid_columnconfigure(0, weight=1)
            ctk.CTkCheckBox(block, text=text, variable=var).grid(row=0, column=0, sticky="w")
            ResponsiveLabel(
                block, text=desc, text_color=COLORS["muted"], max_wrap=None, horizontal_margin=42
            ).grid(row=1, column=0, sticky="ew", padx=(34, 0), pady=(3, 0))

        relation = Card(self, "Compatibilidade com REAPER", "Os pares saem prontos para uso juntos.")
        relation.grid(row=2, column=0, sticky="ew", pady=6)
        ResponsiveLabel(
            relation.body, text="Os arquivos serão organizados automaticamente na pasta exports.",
            text_color=COLORS["muted"], max_wrap=None,
        ).grid(row=0, column=0, sticky="ew")

        fmt = Card(self, "Formato final")
        fmt.grid(row=3, column=0, sticky="ew", pady=6)
        self.format = ctk.StringVar(value=cfg.output_format or self.app.settings.default_output_format)
        ctk.CTkOptionMenu(
            fmt.body, values=["FLAC 24-bit", "WAV 24-bit", "WAV 32-bit float"],
            variable=self.format, width=200,
        ).grid(row=0, column=0, sticky="w")
        ResponsiveLabel(
            fmt.body, text="FLAC 24-bit é a opção recomendada.", text_color=COLORS["muted"], max_wrap=None,
        ).grid(row=1, column=0, sticky="ew", pady=(8, 0))

        summary = Card(self, "Resumo")
        summary.grid(row=4, column=0, sticky="ew", pady=6)
        self.summary = ResponsiveLabel(summary.body, text="", max_wrap=None)
        self.summary.grid(row=0, column=0, sticky="ew")

        actions = ctk.CTkFrame(self, fg_color="transparent")
        actions.grid(row=5, column=0, sticky="w", pady=12)
        self.run_btn = ctk.CTkButton(actions, text="Gerar arquivos finais", height=42, command=self.run)
        self.run_btn.pack(side="left")
        self.cancel_export_btn = ctk.CTkButton(
            actions, text="Cancelar exportação", height=42, state="disabled",
            fg_color=COLORS["danger"], hover_color=COLORS["danger"], command=self.cancel_export,
        )
        self.cancel_export_btn.pack(side="left", padx=(10, 0))
        self.open_exports_btn = ctk.CTkButton(
            actions, text="Abrir pasta de exports", height=42,
            fg_color="transparent", border_width=1, command=self.open_exports,
        )
        self.open_exports_btn.pack(side="left", padx=(10, 0))

        backup = Card(
            self, "Backup do projeto",
            "Salva um pacote compacto para guardar na nuvem e restaurar depois.",
        )
        backup.grid(row=6, column=0, sticky="ew", pady=6)
        ResponsiveLabel(
            backup.body,
            text="Inclui configurações, fonte, cópia preparada, análise de afinação e exports. Os stems pesados não são incluídos.",
            text_color=COLORS["muted"], max_wrap=None,
        ).grid(row=0, column=0, sticky="ew", pady=(0, 8))
        backup_actions = ctk.CTkFrame(backup.body, fg_color="transparent")
        backup_actions.grid(row=1, column=0, sticky="w")
        self.backup_btn = ctk.CTkButton(
            backup_actions, text="Salvar backup do projeto…", command=self.save_project_backup
        )
        self.backup_btn.pack(side="left")
        self.cancel_backup_btn = ctk.CTkButton(
            backup_actions, text="Cancelar backup", state="disabled",
            fg_color=COLORS["danger"], hover_color=COLORS["danger"], command=self.cancel_backup,
        )
        self.cancel_backup_btn.pack(side="left", padx=(10, 0))

        self.msg = InlineMessage(self)
        self.msg.grid(row=7, column=0, sticky="ew", pady=8)
        self.refresh_summary()
        self._sync_buttons()

    def refresh_summary(self):
        cfg = self.app.doc.config
        self.summary.configure(
            text=f"Afinação: {cfg.original_tuning or '?'} → {cfg.target_tuning or '?'} • Pitch: {cfg.semitones:+d} st"
        )

    def sync_from_doc(self):
        cfg = self.app.doc.config
        try:
            self.v_source.set(cfg.export_source_original)
            self.v_original_pair.set(cfg.export_original_pair)
            self.v_pitched_pair.set(cfg.export_pitched_pair)
            self.v_stems.set(cfg.keep_stems)
            self.format.set(cfg.output_format or self.app.settings.default_output_format)
        except Exception:
            pass
        self.refresh_summary()
        self._sync_buttons()

    def _exports_dir(self) -> Path | None:
        project_dir = str(self.app.doc.state.project_dir or "").strip()
        return Path(project_dir) / "exports" if project_dir else None

    def _sync_exports_button(self):
        """Compatibilidade com chamadas antigas; sincroniza todos os botões da página."""
        self._sync_buttons()

    def _sync_buttons(self):
        exports = self._exports_dir()
        has_project = bool(self.app.doc.state.project_dir)
        busy = self.app.tasks.busy
        try:
            self.open_exports_btn.configure(state="normal" if exports and exports.exists() else "disabled")
            self.backup_btn.configure(state="normal" if has_project and not busy else "disabled")
            if not busy:
                self.run_btn.configure(state="normal")
        except Exception:
            pass

    def open_exports(self):
        exports = self._exports_dir()
        if exports is None:
            self.msg.set("Abra ou crie um projeto primeiro.", "warning")
            return
        if not exports.exists():
            self.msg.set("A pasta de exports será criada ao gerar os arquivos finais.", "warning")
            self._sync_buttons()
            return
        self.app.open_path(exports)

    def on_show(self):
        self.sync_from_doc()

    def cancel_export(self):
        if self.app.tasks.busy:
            self.cancel_export_btn.configure(state="disabled", text="Cancelando…")
            self.app.cancel_task()
            self.msg.set("Cancelamento solicitado.", "warning")

    def cancel_backup(self):
        if self.app.tasks.busy:
            self.cancel_backup_btn.configure(state="disabled", text="Cancelando…")
            self.app.cancel_task()
            self.msg.set("Cancelamento solicitado.", "warning")

    def run(self):
        cfg = self.app.doc.config
        if cfg.pitch_mode == "tuning" and tuning_delta(cfg.original_tuning, cfg.target_tuning) is None:
            self.msg.set(
                "A conversão de afinação escolhida não é válida por pitch global. Corrija na página Afinação & Pitch.",
                "danger",
            )
            return
        cfg.export_source_original = self.v_source.get()
        cfg.export_original_pair = self.v_original_pair.get()
        cfg.export_pitched_pair = self.v_pitched_pair.get()
        cfg.keep_stems = self.v_stems.get()
        cfg.output_format = self.format.get()
        self.app.save_project()
        if not any((cfg.export_source_original, cfg.export_original_pair, cfg.export_pitched_pair)):
            self.msg.set("Selecione pelo menos um arquivo/par final.", "danger")
            return

        self.run_btn.configure(state="disabled")
        self.cancel_export_btn.configure(state="normal", text="Cancelar exportação")
        self.backup_btn.configure(state="disabled")
        self.app.set_busy("Gerando arquivos finais…")

        def progress(frac, msg):
            self.app.tasks.call_ui(self.app.update_progress, frac, msg)

        def work():
            return self.app.audio_service.export(self.app.doc, self.app.settings.master_peak_dbfs, progress)

        def ok(files):
            self.run_btn.configure(state="normal")
            self.cancel_export_btn.configure(state="disabled", text="Cancelar exportação")
            self.app.save_project()
            self.msg.set(f"Concluído: {len(files)} arquivo(s).", "success")
            self._sync_buttons()
            self.app.set_ready("Projeto concluído")
            self.app.refresh_header()

        def err(exc):
            self.run_btn.configure(state="normal")
            self.cancel_export_btn.configure(state="disabled", text="Cancelar exportação")
            if "cancelad" in str(exc).lower():
                self.msg.set("Exportação cancelada.", "warning")
                self.app.set_ready("Exportação cancelada")
            else:
                self.msg.set(str(exc), "danger")
                self.app.set_error(str(exc))
            self._sync_buttons()

        if not self.app.tasks.submit("exportação", work, ok, err):
            self.run_btn.configure(state="normal")
            self.cancel_export_btn.configure(state="disabled")
            self.msg.set("Há outra tarefa em andamento.", "warning")

    def save_project_backup(self):
        if not self.app.doc.state.project_dir:
            self.msg.set("Abra um projeto primeiro.", "warning")
            return
        destination = filedialog.askdirectory(title="Salvar backup do projeto em…")
        if not destination:
            return

        self.backup_btn.configure(state="disabled")
        self.run_btn.configure(state="disabled")
        self.cancel_backup_btn.configure(state="normal", text="Cancelar backup")
        self.app.set_busy("Criando backup do projeto…")

        def work():
            return self.app.backup_service.backup_project(
                self.app.doc, Path(destination), self.app.tasks.cancel_event
            )

        def ok(path):
            self.cancel_backup_btn.configure(state="disabled", text="Cancelar backup")
            self.msg.set(f"Backup salvo: {Path(path).name}", "success")
            self.app.set_ready("Backup salvo")
            self._sync_buttons()

        def err(exc):
            self.cancel_backup_btn.configure(state="disabled", text="Cancelar backup")
            if "cancelad" in str(exc).lower():
                self.msg.set("Backup cancelado.", "warning")
                self.app.set_ready("Backup cancelado")
            else:
                self.msg.set(f"Falha no backup: {exc}", "danger")
                self.app.set_error(str(exc))
            self._sync_buttons()

        if not self.app.tasks.submit("backup do projeto", work, ok, err):
            self.cancel_backup_btn.configure(state="disabled")
            self.msg.set("Há outra tarefa em andamento.", "warning")
            self._sync_buttons()
