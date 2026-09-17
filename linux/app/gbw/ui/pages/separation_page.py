from __future__ import annotations
from pathlib import Path
from gbw.ui.ctk_compat import ctk
from gbw.ui.widgets import PageTitle, Card, InlineMessage, ResponsiveLabel
from gbw.ui.theme import COLORS
from gbw.core.system import tool_path


class SeparationPage(ctk.CTkScrollableFrame):
    page_key = "separation"

    def __init__(self, master, app):
        super().__init__(master, fg_color="transparent", corner_radius=0)
        self.app = app
        self.grid_columnconfigure(0, weight=1)

        PageTitle(
            self,
            "Separação",
            "Escolha como separar a guitarra.",
        ).grid(row=0, column=0, sticky="ew", pady=(0, 14))

        card = Card(
            self,
            "Estratégia",
            "Escolha o modo de separação.",
        )
        card.grid(row=1, column=0, sticky="ew", pady=6)
        card.body.grid_columnconfigure(0, weight=1)
        self.mode = ctk.StringVar(value=self.app.settings.default_separator)
        for i, (val, title, desc) in enumerate([
            ("A", "A — Alta qualidade", "Recomendado para isolar a guitarra."),
            ("B", "B — Alternativa", "Outra opção de separação."),
            ("AB", "A + B — Comparar", "Gera as duas opções para comparação."),
        ]):
            block = ctk.CTkFrame(card.body, fg_color="transparent")
            block.grid(row=i, column=0, sticky="ew", pady=(3, 10))
            block.grid_columnconfigure(0, weight=1)
            ctk.CTkRadioButton(
                block, text=title, variable=self.mode, value=val, command=self._sync_mode_options
            ).grid(row=0, column=0, sticky="w")
            ResponsiveLabel(
                block, text=desc, text_color=COLORS["muted"], max_wrap=None,
                horizontal_margin=44,
            ).grid(row=1, column=0, sticky="ew", padx=(38, 0), pady=(3, 0))

        opt = Card(
            self,
            "Qualidade e hardware",
            "Ajuste apenas o que for necessário.",
        )
        opt.grid(row=2, column=0, sticky="ew", pady=6)
        opt.body.grid_columnconfigure(0, weight=1)

        def setting_block(row: int, title: str, control_factory, help_text: str):
            """Cria um bloco autocontido.

            IMPORTANTE: o controle precisa nascer com ``block`` como master.
            Nas versões anteriores os ComboBoxes eram criados com ``opt.body``
            como master e apenas posicionados visualmente pelo helper. Em Tk, o
            master do widget é imutável; por isso eles permaneciam fora do bloco,
            sobrepunham-se na mesma célula e continuavam visíveis mesmo após
            ``grid_remove()`` do bloco Demucs.
            """
            block = ctk.CTkFrame(opt.body, fg_color="transparent")
            block.grid(row=row, column=0, sticky="ew", pady=(2, 12))
            block.grid_columnconfigure(0, weight=1)
            ResponsiveLabel(block, text=title, font=ctk.CTkFont(weight="bold"), max_wrap=None).grid(
                row=0, column=0, sticky="ew"
            )
            control = control_factory(block)
            control.grid(row=1, column=0, sticky="w", pady=(5, 4))
            ResponsiveLabel(
                block, text=help_text, text_color=COLORS["muted"], max_wrap=None,
                horizontal_margin=8,
            ).grid(row=2, column=0, sticky="ew")
            return block, control

        self.device = ctk.StringVar(value="auto")
        self.device_block, self.device_menu = setting_block(
            0,
            "Dispositivo",
            lambda parent: ctk.CTkOptionMenu(
                parent, values=["auto", "cpu", "cuda"], variable=self.device, width=180
            ),
            "Auto usa GPU quando disponível; caso contrário, CPU.",
        )

        default_shifts = self.app.settings.demucs_shifts_gpu if tool_path("nvidia-smi") else self.app.settings.demucs_shifts_cpu
        self.shifts = ctk.StringVar(value=str(default_shifts))
        self.demucs_shifts_block, self.shifts_combo = setting_block(
            1,
            "Variações",
            lambda parent: ctk.CTkComboBox(
                parent, values=["1", "2", "5", "10"], variable=self.shifts, width=180
            ),
            "Valores maiores podem melhorar o resultado, mas aumentam o tempo.",
        )

        self.overlap = ctk.StringVar(value=str(self.app.settings.demucs_overlap))
        self.demucs_overlap_block, self.overlap_combo = setting_block(
            2,
            "Sobreposição",
            lambda parent: ctk.CTkComboBox(
                parent, values=["0.25", "0.5"], variable=self.overlap, width=180
            ),
            "0,5 prioriza qualidade; 0,25 tende a ser mais rápido.",
        )

        run = Card(
            self,
            "Executar",
            "O tempo exibido é aproximado.",
        )
        run.grid(row=3, column=0, sticky="ew", pady=6)
        self.run_actions = ctk.CTkFrame(run.body, fg_color="transparent")
        self.run_actions.grid(row=0, column=0, sticky="ew")
        self.run_btn = ctk.CTkButton(self.run_actions, text="Iniciar separação", command=self.run)
        self.run_btn.pack(side="left")
        self.cancel_sep_btn = ctk.CTkButton(
            self.run_actions, text="Cancelar separação", command=self.cancel_separation,
            fg_color=COLORS["danger"], hover_color=COLORS["danger"], state="disabled"
        )
        self.cancel_sep_btn.pack(side="left", padx=(10, 0))
        self.eta_status = ResponsiveLabel(
            run.body,
            text="Aguardando separação.",
            text_color=COLORS["muted"],
            max_wrap=None,
        )
        self.eta_status.grid(row=1, column=0, sticky="ew", pady=(10, 2))
        self.preview = ctk.CTkFrame(run.body, fg_color="transparent")
        self.preview.grid(row=2, column=0, sticky="ew", pady=(10, 0))
        self.msg = InlineMessage(self)
        self.msg.grid(row=4, column=0, sticky="ew", pady=8)
        self._sync_mode_options()
        self.refresh_previews()

    def _sync_mode_options(self):
        """Mostra parâmetros do Demucs somente quando B participa da estratégia."""
        show_demucs = self.mode.get() in ("B", "AB")
        for block in (self.demucs_shifts_block, self.demucs_overlap_block):
            if show_demucs:
                block.grid()
            else:
                block.grid_remove()

    def _set_separation_running(self, running: bool):
        self.run_btn.configure(state="disabled" if running else "normal")
        self.cancel_sep_btn.configure(state="normal" if running else "disabled")

    def cancel_separation(self):
        if not self.app.tasks.busy:
            self.msg.set("Nenhuma separação está em andamento.", "warning")
            return
        self.cancel_sep_btn.configure(state="disabled", text="Cancelando…")
        self.eta_status.configure(text="Cancelando separação…", text_color=COLORS["warning"])
        self.msg.set("Cancelamento solicitado.", "warning")
        self.app.cancel_task()

    def run(self):
        if not self.app.doc.state.prepared_wav or not Path(self.app.doc.state.prepared_wav).exists():
            self.msg.set("Prepare a fonte primeiro.", "danger")
            return
        cfg = self.app.doc.config
        cfg.separator_mode = self.mode.get()
        cfg.device = self.device.get()
        if cfg.separator_mode in ("B", "AB"):
            try:
                cfg.demucs_shifts = int(self.shifts.get())
                cfg.demucs_overlap = float(self.overlap.get())
            except ValueError:
                self.msg.set("Valores inválidos.", "danger")
                return
        cfg.final_separator = "B" if cfg.separator_mode == "B" else "A"
        self.app.save_project()
        self._set_separation_running(True)
        self.cancel_sep_btn.configure(text="Cancelar separação")
        self.eta_status.configure(text="Iniciando separação…", text_color=COLORS["accent"])
        self.app.set_busy("Iniciando separação…")

        def progress(frac, msg):
            self.app.tasks.call_ui(self.app.update_progress, frac, msg)
            self.app.tasks.call_ui(self.eta_status.configure, text=msg, text_color=COLORS["muted"])

        def work():
            return self.app.audio_service.separate(self.app.doc, progress)

        def ok(doc):
            self.app.doc = doc
            self.app.save_project()
            self._set_separation_running(False)
            self.cancel_sep_btn.configure(text="Cancelar separação")
            self.refresh_previews()
            self.eta_status.configure(text="Separação concluída.", text_color=COLORS["success"])
            self.msg.set("Separação concluída. Confira o resultado e avance.", "success")
            self.app.set_ready("Separação pronta")
            self.app.navigate("tuning")

        def err(exc):
            self._set_separation_running(False)
            self.cancel_sep_btn.configure(text="Cancelar separação")
            text = str(exc)
            if "cancelad" in text.lower():
                self.eta_status.configure(text="Separação cancelada.", text_color=COLORS["warning"])
                self.msg.set("Separação cancelada.", "warning")
                self.app.set_ready("Separação cancelada")
            else:
                self.eta_status.configure(text="Separação interrompida.", text_color=COLORS["danger"])
                self.msg.set(text, "danger")
                self.app.set_error(text)

        submitted = self.app.tasks.submit("separação", work, ok, err)
        if not submitted:
            self._set_separation_running(False)
            self.cancel_sep_btn.configure(text="Cancelar separação")
            self.eta_status.configure(text="Aguardando: outra tarefa ainda está em execução.", text_color=COLORS["warning"])
            self.msg.set("Há outra tarefa em andamento. Cancele ou aguarde antes de iniciar a separação.", "warning")

    def refresh_previews(self):
        for w in self.preview.winfo_children():
            w.destroy()
        previews = self.app.doc.state.previews
        if not previews:
            ResponsiveLabel(self.preview, text="Nenhum preview ainda.", text_color=COLORS["muted"], max_wrap=None).pack(anchor="w", fill="x")
            return
        ResponsiveLabel(self.preview, text="Separador para continuar:", font=ctk.CTkFont(weight="bold"), max_wrap=None).pack(anchor="w", fill="x", pady=(0, 6))
        final = ctk.StringVar(value=self.app.doc.config.final_separator)

        def set_final(value):
            self.app.doc.config.final_separator = value
            self.app.save_project()
            self.msg.set(f"Opção {value} selecionada.", "success")

        for mode in ("A", "B"):
            if mode not in previews:
                continue
            row = ctk.CTkFrame(self.preview, fg_color="transparent")
            row.pack(fill="x", pady=3)
            ctk.CTkRadioButton(row, text=f"Usar {mode}", variable=final, value=mode,
                               command=lambda m=mode: set_final(m)).pack(side="left")
            ctk.CTkButton(
                row, text="Ouvir backing sem guitarra", width=180,
                fg_color="transparent", border_width=1,
                command=lambda m=mode: self.app.open_path(Path(previews[m])),
            ).pack(side="left", padx=8)
            gp = self.app.doc.state.stem_maps.get(mode, {}).get("guitar")
            if gp:
                ctk.CTkButton(
                    row, text="Ouvir guitar isolada", width=145,
                    fg_color="transparent", border_width=1,
                    command=lambda p=gp: self.app.open_path(Path(p)),
                ).pack(side="left")
