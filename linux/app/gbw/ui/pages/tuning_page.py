from __future__ import annotations

from gbw.ui.ctk_compat import ctk
from gbw.ui.widgets import PageTitle, Card, InlineMessage, TuningCandidateCard, ResponsiveLabel
from gbw.ui.theme import COLORS
from gbw.models import TUNING_NAMES, tuning_delta, transpose_key_label


class TuningPage(ctk.CTkScrollableFrame):
    page_key = "tuning"

    def __init__(self, master, app):
        super().__init__(master, fg_color="transparent", corner_radius=0)
        self.app = app
        self.grid_columnconfigure(0, weight=1)

        PageTitle(self, "Afinação & Pitch", "Confirme a afinação e escolha o ajuste desejado.").grid(
            row=0, column=0, sticky="ew", pady=(0, 14)
        )

        detect = Card(self, "Detecção")
        detect.grid(row=1, column=0, sticky="ew", pady=6)
        detect_actions = ctk.CTkFrame(detect.body, fg_color="transparent")
        detect_actions.grid(row=0, column=0, sticky="w")
        self.detect_btn = ctk.CTkButton(detect_actions, text="Detectar afinação", command=self.detect)
        self.detect_btn.pack(side="left")
        self.cancel_detect_btn = ctk.CTkButton(
            detect_actions, text="Cancelar análise", state="disabled",
            fg_color=COLORS["danger"], hover_color=COLORS["danger"], command=self.cancel_detect
        )
        self.cancel_detect_btn.pack(side="left", padx=(8, 0))
        self.summary = ResponsiveLabel(
            detect.body, text="Ainda não analisado.", text_color=COLORS["muted"], max_wrap=None
        )
        self.summary.grid(row=1, column=0, sticky="ew", pady=(10, 0))
        self.candidates = ctk.CTkFrame(detect.body, fg_color="transparent")
        self.candidates.grid(row=2, column=0, sticky="ew", pady=(10, 0))
        self.candidates.grid_columnconfigure(0, weight=1)

        choose = Card(self, "Afinação original → destino")
        choose.grid(row=2, column=0, sticky="ew", pady=6)
        choose.body.grid_columnconfigure(1, weight=1)
        ctk.CTkLabel(choose.body, text="Original").grid(row=0, column=0, sticky="w", pady=5)
        self.original = ctk.StringVar(value=self.app.doc.config.original_tuning or "")
        self.orig_combo = ctk.CTkComboBox(
            choose.body,
            values=list(TUNING_NAMES),
            variable=self.original,
            command=lambda _v: self._on_tuning_change(),
        )
        self.orig_combo.grid(row=0, column=1, sticky="w", padx=10)

        ctk.CTkLabel(choose.body, text="Destino").grid(row=1, column=0, sticky="w", pady=5)
        self.target = ctk.StringVar(value=self.app.doc.config.target_tuning or "Drop D")
        self.target_combo = ctk.CTkComboBox(
            choose.body,
            values=list(TUNING_NAMES),
            variable=self.target,
            command=lambda _v: self._on_tuning_change(),
        )
        self.target_combo.grid(row=1, column=1, sticky="w", padx=10)
        self.math = ResponsiveLabel(choose.body, text="", max_wrap=None)
        self.math.grid(row=2, column=0, columnspan=2, sticky="ew", pady=(12, 0))

        pitch = Card(self, "Pitch")
        pitch.grid(row=3, column=0, sticky="ew", pady=6)
        self.mode = ctk.StringVar(value=self.app.doc.config.pitch_mode)
        ctk.CTkRadioButton(
            pitch.body, text="Automático pela afinação", variable=self.mode, value="tuning",
            command=self._on_mode_change,
        ).grid(row=0, column=0, sticky="w", pady=3)
        ctk.CTkRadioButton(
            pitch.body, text="Manual", variable=self.mode, value="manual",
            command=self._on_mode_change,
        ).grid(row=1, column=0, sticky="w", pady=3)
        ctk.CTkRadioButton(
            pitch.body, text="Sem pitch", variable=self.mode, value="none",
            command=self._on_mode_change,
        ).grid(row=2, column=0, sticky="w", pady=3)

        self.manual_row = ctk.CTkFrame(pitch.body, fg_color="transparent")
        self.manual_row.grid(row=3, column=0, sticky="w", pady=(10, 0))
        ctk.CTkLabel(self.manual_row, text="Semitons:").pack(side="left")
        self.manual = ctk.StringVar(value=str(self.app.doc.config.semitones))
        ctk.CTkEntry(self.manual_row, textvariable=self.manual, width=90).pack(side="left", padx=8)
        ctk.CTkButton(
            self.manual_row, text="Atualizar cálculo", width=120, command=self._update_manual
        ).pack(side="left")

        action = Card(self, "Continuar")
        action.grid(row=4, column=0, sticky="ew", pady=6)
        self.confirm_btn = ctk.CTkButton(
            action.body, text="Confirmar e continuar", height=42, command=self.confirm_and_continue
        )
        self.confirm_btn.grid(row=0, column=0, sticky="w")
        ResponsiveLabel(
            action.body,
            text="O pitch será aplicado ao gerar os arquivos finais.",
            text_color=COLORS["muted"],
            max_wrap=None,
        ).grid(row=1, column=0, sticky="ew", pady=(8, 0))

        self.msg = InlineMessage(self)
        self.msg.grid(row=5, column=0, sticky="ew", pady=8)

        self.show_analysis(self.app.doc.state.tuning_analysis)
        self._sync_mode_ui()
        self.recompute(invalidate=False)

    def _invalidate_confirmation(self):
        if getattr(self.app.doc.state, "tuning_confirmed", False):
            self.app.doc.state.tuning_confirmed = False
            self.app.save_project()

    def _on_tuning_change(self):
        self._invalidate_confirmation()
        self.recompute(invalidate=False)

    def _on_mode_change(self):
        self._invalidate_confirmation()
        self._sync_mode_ui()
        self.recompute(invalidate=False)

    def _sync_mode_ui(self):
        if self.mode.get() == "manual":
            self.manual_row.grid()
        else:
            self.manual_row.grid_remove()

    def _update_manual(self):
        self._invalidate_confirmation()
        if self.recompute(invalidate=False):
            self.msg.set("Cálculo atualizado.", "success")

    def cancel_detect(self):
        if self.app.tasks.busy:
            self.cancel_detect_btn.configure(state="disabled", text="Cancelando…")
            self.app.cancel_task()
            self.msg.set("Cancelamento solicitado.", "warning")

    def detect(self):
        mode = self.app.doc.config.final_separator
        if mode not in self.app.doc.state.stem_maps:
            self.msg.set("Conclua a separação primeiro.", "danger")
            return
        self.detect_btn.configure(state="disabled")
        self.cancel_detect_btn.configure(state="normal", text="Cancelar análise")
        self.app.set_busy("Analisando afinação…")

        def work():
            return self.app.tuning_service.analyze(self.app.doc, mode)

        def ok(result):
            self.detect_btn.configure(state="normal")
            self.cancel_detect_btn.configure(state="disabled", text="Cancelar análise")
            self.show_analysis(result)
            self._invalidate_confirmation()
            self.app.save_project()
            self.app.set_ready("Afinação analisada")

        def err(exc):
            self.detect_btn.configure(state="normal")
            self.cancel_detect_btn.configure(state="disabled", text="Cancelar análise")
            if "cancelad" in str(exc).lower():
                self.msg.set("Análise cancelada.", "warning")
                self.app.set_ready("Análise cancelada")
            else:
                self.msg.set(str(exc), "danger")
                self.app.set_error(str(exc))

        if not self.app.tasks.submit("detecção de afinação", work, ok, err):
            self.detect_btn.configure(state="normal")
            self.cancel_detect_btn.configure(state="disabled")
            self.msg.set("Há outra tarefa em andamento.", "warning")

    def show_analysis(self, result):
        for w in self.candidates.winfo_children():
            w.destroy()
        if not result:
            return
        conf = int(result.get("confidence", 0))
        key = result.get("key", {})
        best = result.get("best_tuning", "")
        self.summary.configure(
            text=(
                f"Tom provável: {key.get('label', '?')} ({key.get('confidence', 0)}%). "
                f"Afinação provável: {best or '?'} ({conf}%)."
            )
        )
        # A análise sugere a melhor afinação, mas nunca deve apagar uma escolha
        # que já está salva no projeto. O primeiro resultado só vira seleção
        # automática quando ainda não existe afinação escolhida.
        cfg = self.app.doc.config
        if not cfg.original_tuning and best:
            cfg.original_tuning = best
        selected_tuning = cfg.original_tuning or best
        self.original.set(selected_tuning or "")

        for i, candidate in enumerate((result.get("fused_candidates", []) or [])[:5]):
            tuning = str(candidate.get("tuning", ""))
            TuningCandidateCard(
                self.candidates, candidate, conf, self.use_tuning,
                selected=(tuning == selected_tuning),
                recommended=(i == 0),
            ).grid(row=i, column=0, sticky="ew", pady=5)
        self.recompute(invalidate=False)

    def use_tuning(self, name):
        self.original.set(name)
        self.app.doc.config.original_tuning = name
        self._invalidate_confirmation()
        self.recompute(invalidate=False)
        # Recria os cards para mostrar visualmente a afinação escolhida sem
        # confundi-la com a recomendação automática do analisador.
        self.show_analysis(self.app.doc.state.tuning_analysis)
        self.app.save_project()
        self.app.logger.info("Afinação selecionada: %s", name)
        self.msg.set(f"Afinação selecionada: {name}", "success")

    def recompute(self, invalidate: bool = True) -> bool:
        if invalidate:
            self._invalidate_confirmation()
        cfg = self.app.doc.config
        cfg.pitch_mode = self.mode.get()
        cfg.original_tuning = self.original.get()
        cfg.target_tuning = self.target.get()

        if cfg.pitch_mode == "none":
            cfg.semitones = 0
            self.math.configure(text="Sem pitch.", text_color=COLORS["muted"])
        elif cfg.pitch_mode == "manual":
            try:
                value = int(self.manual.get())
            except ValueError:
                self.math.configure(text="Informe um número inteiro entre -12 e +12.", text_color=COLORS["danger"])
                return False
            if not -12 <= value <= 12:
                self.math.configure(text="Use um valor entre -12 e +12.", text_color=COLORS["danger"])
                return False
            cfg.semitones = value
            self.math.configure(text=f"Pitch: {value:+d} semitons.", text_color=COLORS["warning"])
        else:
            if not cfg.original_tuning or not cfg.target_tuning:
                self.math.configure(text="Escolha a afinação original e o destino.", text_color=COLORS["danger"])
                return False
            delta = tuning_delta(cfg.original_tuning, cfg.target_tuning)
            if delta is None:
                self.math.configure(
                    text=f"{cfg.original_tuning} → {cfg.target_tuning} não pode ser feito com pitch global.",
                    text_color=COLORS["danger"],
                )
                return False
            cfg.semitones = delta
            key = (self.app.doc.state.tuning_analysis or {}).get("key", {})
            transposed = (
                transpose_key_label(str(key.get("key", "")), str(key.get("mode", "minor")), delta)
                if key else "—"
            )
            suffix = f" • Tom estimado: {transposed}" if transposed != "—" else ""
            self.math.configure(
                text=f"{cfg.original_tuning} → {cfg.target_tuning}: {delta:+d} semitons{suffix}.",
                text_color=COLORS["success"],
            )

        self.app.save_project()
        self.app.refresh_header()
        return True

    def confirm_and_continue(self):
        if not self.recompute(invalidate=False):
            self.msg.set("Revise as opções antes de continuar.", "danger")
            return
        self.app.doc.state.tuning_confirmed = True
        self.app.save_project()
        cfg = self.app.doc.config
        self.app.logger.info("Afinação e pitch confirmados.")
        self.msg.set("Afinação e pitch confirmados.", "success")
        self.app.set_ready("Afinação pronta")
        self.app.navigate("export")

    def sync_from_doc(self):
        cfg = self.app.doc.config
        try:
            self.original.set(cfg.original_tuning or "")
            self.target.set(cfg.target_tuning or "Drop D")
            self.mode.set(cfg.pitch_mode or "tuning")
            self.manual.set(str(cfg.semitones))
        except Exception:
            pass
        self._sync_mode_ui()
        self.show_analysis(self.app.doc.state.tuning_analysis)
        self.recompute(invalidate=False)
