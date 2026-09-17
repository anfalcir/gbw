from __future__ import annotations
from pathlib import Path
from tkinter import filedialog

from gbw.ui.ctk_compat import ctk
from gbw.ui.widgets import PageTitle, Card, InlineMessage, ResponsiveLabel
from gbw.ui.theme import COLORS
from gbw.core.system import detect_linux_system, system_install_command, diagnostic_report, tool_path, rubberband_r3_status


class SystemPage(ctk.CTkScrollableFrame):
    page_key = "system"

    def __init__(self, master, app):
        super().__init__(master, fg_color="transparent", corner_radius=0)
        self.app = app
        self.grid_columnconfigure(0, weight=1)
        PageTitle(self, "Sistema", "Verifique e preserve o aplicativo.").grid(
            row=0, column=0, sticky="ew", pady=(0, 14)
        )

        self.sys_card = Card(self, "Linux detectado")
        self.sys_card.grid(row=1, column=0, sticky="ew", pady=6)
        self.sys_label = ResponsiveLabel(
            self.sys_card.body, text="", font=ctk.CTkFont(size=14, weight="bold"), max_wrap=None
        )
        self.sys_label.grid(row=0, column=0, sticky="ew")
        self.cmd_label = ResponsiveLabel(
            self.sys_card.body, text="", text_color=COLORS["muted"], max_wrap=None
        )
        self.cmd_label.grid(row=1, column=0, sticky="ew", pady=(6, 10))
        actions = ctk.CTkFrame(self.sys_card.body, fg_color="transparent")
        actions.grid(row=2, column=0, sticky="w")
        ctk.CTkButton(actions, text="Copiar relatório", command=self.copy_diag).pack(side="left")
        ctk.CTkButton(
            actions, text="Copiar comando", fg_color="transparent", border_width=1, command=self.copy_install
        ).pack(side="left", padx=8)
        ctk.CTkButton(
            actions, text="Instalar", fg_color="transparent", border_width=1, command=self.app.launch_installer
        ).pack(side="left")

        self.dep_card = Card(self, "Componentes")
        self.dep_card.grid(row=2, column=0, sticky="ew", pady=6)
        self.dep_rows = ctk.CTkFrame(self.dep_card.body, fg_color="transparent")
        self.dep_rows.grid(row=0, column=0, sticky="ew")
        self.dep_rows.grid_columnconfigure(1, weight=1)
        self.dep_rows.grid_columnconfigure(2, weight=3)
        ctk.CTkButton(
            self.dep_card.body, text="Verificar novamente", width=150, command=self.refresh
        ).grid(row=1, column=0, sticky="w", pady=(12, 0))

        backup = Card(
            self, "Backup do GBW",
            "Cria um pacote instalável do programa para guardar na nuvem ou levar para outra máquina.",
        )
        backup.grid(row=3, column=0, sticky="ew", pady=6)
        ResponsiveLabel(
            backup.body,
            text="Não inclui projetos, músicas, logs, cache, ambiente virtual ou configurações pessoais.",
            text_color=COLORS["muted"], max_wrap=None,
        ).grid(row=0, column=0, sticky="ew", pady=(0, 8))
        backup_actions = ctk.CTkFrame(backup.body, fg_color="transparent")
        backup_actions.grid(row=1, column=0, sticky="w")
        self.backup_app_btn = ctk.CTkButton(
            backup_actions, text="Salvar backup do GBW…", command=self.save_app_backup
        )
        self.backup_app_btn.pack(side="left")
        self.cancel_backup_btn = ctk.CTkButton(
            backup_actions, text="Cancelar backup", state="disabled",
            fg_color=COLORS["danger"], hover_color=COLORS["danger"], command=self.cancel_backup,
        )
        self.cancel_backup_btn.pack(side="left", padx=(8, 0))

        self.msg = InlineMessage(self)
        self.msg.grid(row=4, column=0, sticky="ew", pady=8)
        self.refresh()

    def refresh(self):
        info = detect_linux_system()
        self.sys_label.configure(text=f"Sistema detectado: {info['pretty']}")
        self.cmd_label.configure(text="")
        self.cmd_label.grid_remove()
        for w in self.dep_rows.winfo_children():
            w.destroy()
        r3ok, _r3detail = rubberband_r3_status()
        items = [
            ("Áudio", bool(tool_path("ffmpeg")), "Pronto" if tool_path("ffmpeg") else "Ausente"),
            ("Pitch", r3ok, "Pronto" if r3ok else "Indisponível"),
            ("Separador A", bool(tool_path("bs-roformer-infer")), "Pronto" if tool_path("bs-roformer-infer") else "Ausente"),
            ("Separador B", bool(tool_path("demucs")), "Pronto" if tool_path("demucs") else "Ausente"),
            ("Download online", bool(tool_path("yt-dlp")), "Pronto" if tool_path("yt-dlp") else "Ausente"),
            ("Suporte web", bool(tool_path("deno")), "Pronto" if tool_path("deno") else "Opcional"),
        ]
        for i, (name, ok, detail) in enumerate(items):
            ctk.CTkLabel(
                self.dep_rows, text="✓" if ok else "!",
                text_color=COLORS["success"] if ok else COLORS["danger"],
                font=ctk.CTkFont(size=15, weight="bold"), width=30,
            ).grid(row=i, column=0, sticky="w", pady=4)
            ctk.CTkLabel(
                self.dep_rows, text=name, font=ctk.CTkFont(weight="bold"), width=160, anchor="w"
            ).grid(row=i, column=1, sticky="w", pady=4)
            ResponsiveLabel(
                self.dep_rows, text=detail, text_color=COLORS["muted"], max_wrap=None,
                min_wrap=120, horizontal_margin=12,
            ).grid(row=i, column=2, sticky="ew", pady=4)

        health = self.app.refresh_system_health(force=True)
        if health.get("ok"):
            self.msg.set("Sistema pronto.", "success")
        else:
            self.msg.set("Há itens pendentes: " + "; ".join(health.get("blocking") or []), "warning")
        self.app.refresh_header()

    def cancel_backup(self):
        if self.app.tasks.busy:
            self.cancel_backup_btn.configure(state="disabled", text="Cancelando…")
            self.app.cancel_task()
            self.msg.set("Cancelamento solicitado.", "warning")

    def save_app_backup(self):
        destination = filedialog.askdirectory(title="Salvar backup do GBW em…")
        if not destination:
            return
        self.backup_app_btn.configure(state="disabled")
        self.cancel_backup_btn.configure(state="normal", text="Cancelar backup")
        self.app.set_busy("Criando backup do GBW…")

        def work():
            return self.app.backup_service.backup_application(
                Path(destination), self.app.tasks.cancel_event
            )

        def ok(path):
            self.backup_app_btn.configure(state="normal")
            self.cancel_backup_btn.configure(state="disabled", text="Cancelar backup")
            self.msg.set(f"Backup salvo: {Path(path).name}", "success")
            self.app.set_ready("Backup salvo")

        def err(exc):
            self.backup_app_btn.configure(state="normal")
            self.cancel_backup_btn.configure(state="disabled", text="Cancelar backup")
            if "cancelad" in str(exc).lower():
                self.msg.set("Backup cancelado.", "warning")
                self.app.set_ready("Backup cancelado")
            else:
                self.msg.set(f"Falha no backup: {exc}", "danger")
                self.app.set_error(str(exc))

        if not self.app.tasks.submit("backup do GBW", work, ok, err):
            self.backup_app_btn.configure(state="normal")
            self.cancel_backup_btn.configure(state="disabled")
            self.msg.set("Há outra tarefa em andamento.", "warning")

    def copy_diag(self):
        text = diagnostic_report()
        self.app.clipboard_clear()
        self.app.clipboard_append(text)
        self.msg.set("Relatório copiado.", "success")

    def copy_install(self):
        self.app.clipboard_clear()
        self.app.clipboard_append(system_install_command())
        self.msg.set("Comando copiado.", "success")
