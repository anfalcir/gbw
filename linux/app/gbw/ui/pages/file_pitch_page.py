from __future__ import annotations

import re
import tkinter as tk
from pathlib import Path
from tkinter import filedialog

from gbw.models import TUNING_NAMES, tuning_delta
from gbw.ui.ctk_compat import ctk
from gbw.ui.pages.base import BasePage
from gbw.ui.theme import COLORS
from gbw.ui.widgets import Card, InlineMessage, PageTitle, ResponsiveLabel, configure_scroll_speed


class FilePitchPage(BasePage):
    page_key = "file_pitch"

    def __init__(self, master, app):
        super().__init__(master, app)
        self.input_path: Path | None = None
        self.inspection = None
        self.output_dir: Path | None = None
        self.last_output: Path | None = None
        self._guide_open = False
        self._analysis_running = False
        self._processing = False

        PageTitle(
            self, "Pitch de Arquivo",
            "Altere o pitch de um arquivo independente usando o mesmo motor de alta qualidade do workflow.",
        ).grid(row=0, column=0, sticky="ew", pady=(0, 10))

        file_card = Card(self, "Arquivo de entrada", "WAV, FLAC e os formatos de áudio mais comuns.")
        file_card.grid(row=1, column=0, sticky="ew", pady=6)
        file_actions = ctk.CTkFrame(file_card.body, fg_color="transparent")
        file_actions.grid(row=0, column=0, sticky="ew")
        file_actions.grid_columnconfigure(1, weight=1)
        self.select_btn = ctk.CTkButton(file_actions, text="Selecionar arquivo…", command=self.select_file)
        self.select_btn.grid(row=0, column=0, sticky="w")
        self.cancel_analysis_btn = ctk.CTkButton(
            file_actions, text="Cancelar análise", state="disabled",
            fg_color=COLORS["danger"], hover_color=COLORS["danger"], command=self.cancel_analysis,
        )
        self.cancel_analysis_btn.grid(row=0, column=1, sticky="w", padx=(10, 0))
        self.file_name = ResponsiveLabel(
            file_card.body, text="Nenhum arquivo selecionado.", text_color=COLORS["muted"], max_wrap=None,
        )
        self.file_name.grid(row=1, column=0, sticky="ew", pady=(10, 4))

        self.quality_frame = ctk.CTkFrame(file_card.body, fg_color=COLORS["card_alt"], corner_radius=10)
        self.quality_frame.grid(row=2, column=0, sticky="ew", pady=(6, 2))
        self.quality_frame.grid_columnconfigure(0, weight=1)
        self.quality_title = ResponsiveLabel(
            self.quality_frame, text="Selecione um arquivo para o GBW avaliar a entrada.",
            text_color=COLORS["muted"], font=ctk.CTkFont(size=15, weight="bold"), max_wrap=None,
        )
        self.quality_title.grid(row=0, column=0, sticky="ew", padx=12, pady=(10, 2))
        self.quality_summary = ResponsiveLabel(
            self.quality_frame, text="", text_color=COLORS["muted"], max_wrap=None,
        )
        self.quality_summary.grid(row=1, column=0, sticky="ew", padx=12, pady=(0, 2))
        self.quality_detail = ResponsiveLabel(
            self.quality_frame, text="", text_color=COLORS["muted"], max_wrap=None,
        )
        self.quality_detail.grid(row=2, column=0, sticky="ew", padx=12, pady=(0, 10))

        self.guide_btn = ctk.CTkButton(
            file_card.body, text="▸ Como exportar do seu DAW para o GBW?", anchor="w",
            fg_color="transparent", border_width=1, command=self.toggle_guide,
        )
        self.guide_btn.grid(row=3, column=0, sticky="ew", pady=(10, 0))
        self.guide_body = ctk.CTkFrame(file_card.body, fg_color=COLORS["card_alt"], corner_radius=10)
        self.guide_body.grid_columnconfigure(0, weight=1)
        guide_text = (
            "Para melhor qualidade:\n\n"
            "• Formato recomendado: WAV\n"
            "• Profundidade: 32-bit float\n"
            "• Taxa de amostragem: a mesma da sessão do DAW\n"
            "• Se estiver criando a sessão do zero: 48 kHz é uma ótima escolha\n"
            "• Sessão em 44,1 kHz: mantenha 44,1 kHz; não faça upsampling só para usar o GBW\n"
            "• Mono para guitarra mono; preserve estéreo quando houver efeitos estéreo impressos\n"
            "• Não normalize apenas para deixar o arquivo mais alto\n"
            "• Evite MP3/AAC quando ainda puder exportar novamente em lossless\n"
            "• Para manter sincronismo, exporte desde o mesmo ponto inicial da backing/original\n\n"
            "Fluxo recomendado: DAW → WAV 32-bit float → GBW → pitch → DAW."
        )
        ResponsiveLabel(
            self.guide_body, text=guide_text, text_color=COLORS["muted"], max_wrap=None,
        ).grid(row=0, column=0, sticky="ew", padx=12, pady=12)
        self.guide_body.grid(row=4, column=0, sticky="ew", pady=(6, 0))
        self.guide_body.grid_remove()

        conv = Card(self, "Conversão")
        conv.grid(row=2, column=0, sticky="ew", pady=6)
        self.mode = ctk.StringVar(value="tuning")
        modes = ctk.CTkFrame(conv.body, fg_color="transparent")
        modes.grid(row=0, column=0, sticky="w")
        ctk.CTkRadioButton(
            modes, text="Por afinação", variable=self.mode, value="tuning", command=self._sync_mode,
        ).pack(side="left")
        ctk.CTkRadioButton(
            modes, text="Por semitons", variable=self.mode, value="manual", command=self._sync_mode,
        ).pack(side="left", padx=(18, 0))

        self.tuning_row = ctk.CTkFrame(conv.body, fg_color="transparent")
        self.tuning_row.grid(row=1, column=0, sticky="ew", pady=(12, 0))
        self.tuning_row.grid_columnconfigure(5, weight=1)
        ctk.CTkLabel(self.tuning_row, text="Atual:").grid(row=0, column=0, sticky="w")
        self.original = ctk.StringVar(value="Drop D")
        ctk.CTkOptionMenu(
            self.tuning_row, values=list(TUNING_NAMES), variable=self.original,
            command=lambda _v: self.recompute(), width=170,
        ).grid(row=0, column=1, sticky="w", padx=(8, 18))
        ctk.CTkLabel(self.tuning_row, text="Destino:").grid(row=0, column=2, sticky="w")
        self.target = ctk.StringVar(value="Drop B")
        ctk.CTkOptionMenu(
            self.tuning_row, values=list(TUNING_NAMES), variable=self.target,
            command=lambda _v: self.recompute(), width=170,
        ).grid(row=0, column=3, sticky="w", padx=(8, 10))
        ctk.CTkButton(
            self.tuning_row, text="↕ Inverter", width=100, fg_color="transparent", border_width=1,
            command=self.invert_tunings,
        ).grid(row=0, column=4, sticky="w")

        self.manual_row = ctk.CTkFrame(conv.body, fg_color="transparent")
        self.manual_row.grid(row=2, column=0, sticky="w", pady=(12, 0))
        ctk.CTkLabel(self.manual_row, text="Semitons:").pack(side="left")
        self.manual = ctk.StringVar(value="-3")
        self.manual_entry = ctk.CTkEntry(self.manual_row, textvariable=self.manual, width=90)
        self.manual_entry.pack(side="left", padx=(8, 8))
        ctk.CTkButton(
            self.manual_row, text="Atualizar cálculo", width=130,
            fg_color="transparent", border_width=1, command=self.recompute,
        ).pack(side="left")

        self.math = ResponsiveLabel(conv.body, text="", text_color=COLORS["muted"], max_wrap=None)
        self.math.grid(row=3, column=0, sticky="ew", pady=(10, 0))
        self.inverse_btn = ctk.CTkButton(
            conv.body, text="Usar conversão inversa do projeto", fg_color="transparent", border_width=1,
            command=self.use_inverse_project,
        )
        self.inverse_btn.grid(row=4, column=0, sticky="w", pady=(10, 0))

        audio_type = Card(self, "Tipo de áudio", "Instrumento / Mix é o padrão para guitarra e backing.")
        audio_type.grid(row=3, column=0, sticky="ew", pady=6)
        self.audio_type = ctk.StringVar(value="instrument")
        ctk.CTkRadioButton(
            audio_type.body, text="Instrumento / Mix", variable=self.audio_type, value="instrument",
        ).grid(row=0, column=0, sticky="w")
        ctk.CTkRadioButton(
            audio_type.body, text="Vocal", variable=self.audio_type, value="vocal",
        ).grid(row=1, column=0, sticky="w", pady=(8, 0))
        ResponsiveLabel(
            audio_type.body,
            text="No modo Vocal, o GBW preserva formantes para reduzir alterações artificiais no timbre da voz.",
            text_color=COLORS["muted"], max_wrap=None,
        ).grid(row=2, column=0, sticky="ew", pady=(8, 0))

        out = Card(self, "Saída")
        out.grid(row=4, column=0, sticky="ew", pady=6)
        out.grid_columnconfigure(0, weight=1)
        self.output_format = ctk.StringVar(value="WAV 32-bit float")
        format_row = ctk.CTkFrame(out.body, fg_color="transparent")
        format_row.grid(row=0, column=0, sticky="ew")
        ctk.CTkLabel(format_row, text="Formato:").pack(side="left")
        ctk.CTkOptionMenu(
            format_row, values=["WAV 32-bit float", "WAV 24-bit", "FLAC 24-bit"],
            variable=self.output_format, width=180,
        ).pack(side="left", padx=(8, 0))
        ResponsiveLabel(
            out.body,
            text="WAV 32-bit float é o padrão recomendado para voltar ao DAW. A taxa de amostragem e os canais do input são preservados.",
            text_color=COLORS["muted"], max_wrap=None,
        ).grid(row=1, column=0, sticky="ew", pady=(8, 10))
        dest_row = ctk.CTkFrame(out.body, fg_color="transparent")
        dest_row.grid(row=2, column=0, sticky="ew")
        dest_row.grid_columnconfigure(1, weight=1)
        ctk.CTkButton(dest_row, text="Escolher pasta…", command=self.choose_output_dir).grid(row=0, column=0, sticky="w")
        self.output_dir_label = ResponsiveLabel(
            dest_row, text="A pasta do arquivo de entrada será usada por padrão.",
            text_color=COLORS["muted"], max_wrap=None,
        )
        self.output_dir_label.grid(row=0, column=1, sticky="ew", padx=(10, 0))

        actions = ctk.CTkFrame(self, fg_color="transparent")
        actions.grid(row=5, column=0, sticky="w", pady=12)
        self.run_btn = ctk.CTkButton(actions, text="Aplicar pitch", height=42, command=self.run)
        self.run_btn.pack(side="left")
        self.cancel_btn = ctk.CTkButton(
            actions, text="Cancelar", height=42, state="disabled",
            fg_color=COLORS["danger"], hover_color=COLORS["danger"], command=self.cancel_processing,
        )
        self.cancel_btn.pack(side="left", padx=(10, 0))
        self.open_btn = ctk.CTkButton(
            actions, text="Abrir pasta", height=42, state="disabled",
            fg_color="transparent", border_width=1, command=self.open_output,
        )
        self.open_btn.pack(side="left", padx=(10, 0))

        self.msg = InlineMessage(self)
        self.msg.grid(row=6, column=0, sticky="ew", pady=(0, 10))
        self._sync_mode()
        self.recompute()
        self._sync_inverse_button()

    def toggle_guide(self):
        self._guide_open = not self._guide_open
        if self._guide_open:
            self.guide_body.grid()
            self.guide_btn.configure(text="▾ Como exportar do seu DAW para o GBW?")
        else:
            self.guide_body.grid_remove()
            self.guide_btn.configure(text="▸ Como exportar do seu DAW para o GBW?")

    def _sync_mode(self):
        if self.mode.get() == "manual":
            self.tuning_row.grid_remove()
            self.manual_row.grid()
        else:
            self.manual_row.grid_remove()
            self.tuning_row.grid()
        self.recompute()

    def recompute(self):
        if self.mode.get() == "manual":
            try:
                semitones = int(self.manual.get().strip())
                if not -12 <= semitones <= 12:
                    raise ValueError
                self.math.configure(text=f"Resultado: {semitones:+d} semitons", text_color=COLORS["text"])
                return semitones
            except Exception:
                self.math.configure(text="Informe um número inteiro entre -12 e +12 semitons.", text_color=COLORS["danger"])
                return None
        delta = tuning_delta(self.original.get(), self.target.get())
        if delta is None:
            self.math.configure(
                text=f"{self.original.get()} → {self.target.get()} não pode ser feito por pitch global.",
                text_color=COLORS["danger"],
            )
            return None
        self.math.configure(
            text=f"Resultado: {self.original.get()} → {self.target.get()} = {delta:+d} semitons",
            text_color=COLORS["text"],
        )
        return delta

    def invert_tunings(self):
        a, b = self.original.get(), self.target.get()
        self.original.set(b)
        self.target.set(a)
        self.recompute()

    def _sync_inverse_button(self):
        cfg = self.app.doc.config
        usable = False
        if self.app.doc.state.project_dir:
            if cfg.pitch_mode == "tuning":
                usable = bool(cfg.original_tuning and cfg.target_tuning and tuning_delta(cfg.target_tuning, cfg.original_tuning) is not None)
            elif cfg.pitch_mode == "manual":
                usable = -12 <= int(cfg.semitones) <= 12
        try:
            self.inverse_btn.configure(state="normal" if usable else "disabled")
        except Exception:
            pass

    def use_inverse_project(self):
        cfg = self.app.doc.config
        if not self.app.doc.state.project_dir:
            return
        if cfg.pitch_mode == "tuning" and tuning_delta(cfg.target_tuning, cfg.original_tuning) is not None:
            self.mode.set("tuning")
            self.original.set(cfg.target_tuning)
            self.target.set(cfg.original_tuning)
            self._sync_mode()
            self.msg.set("Conversão inversa do projeto aplicada.", "success")
        elif cfg.pitch_mode == "manual" and -12 <= int(cfg.semitones) <= 12:
            self.mode.set("manual")
            self.manual.set(str(-int(cfg.semitones)))
            self._sync_mode()
            self.msg.set("Pitch inverso do projeto aplicado.", "success")

    def select_file(self):
        if self.app.tasks.busy:
            self.msg.set("Há outra tarefa em andamento.", "warning")
            return
        path = filedialog.askopenfilename(
            title="Selecionar arquivo de áudio",
            filetypes=[
                ("Áudio lossless", "*.wav *.flac *.aiff *.aif"),
                ("Áudio comum", "*.wav *.flac *.aiff *.aif *.m4a *.mp3 *.ogg *.opus *.aac *.wma *.mka *.webm *.mp4 *.mov"),
                ("Todos os arquivos", "*.*"),
            ],
        )
        if path:
            self.load_file(Path(path))

    def load_file(self, path: Path):
        if self.app.tasks.busy:
            self.msg.set("Há outra tarefa em andamento.", "warning")
            return
        self.input_path = Path(path).expanduser().resolve()
        self.inspection = None
        self.last_output = None
        self.output_dir = self.input_path.parent
        self.file_name.configure(text=str(self.input_path))
        self.output_dir_label.configure(text=str(self.output_dir))
        self.quality_title.configure(text="Analisando entrada…", text_color=COLORS["accent"])
        self.quality_summary.configure(text="")
        self.quality_detail.configure(text="")
        self.open_btn.configure(state="disabled")
        self.select_btn.configure(state="disabled")
        self.cancel_analysis_btn.configure(state="normal", text="Cancelar análise")
        self._analysis_running = True
        self.app.set_busy("Analisando qualidade do arquivo…")

        def progress(frac, msg):
            self.app.tasks.call_ui(self.app.update_progress, frac, msg)

        def work():
            return self.app.file_pitch_service.inspect(self.input_path, progress)

        def ok(result):
            self._analysis_running = False
            self.select_btn.configure(state="normal")
            self.cancel_analysis_btn.configure(state="disabled", text="Cancelar análise")
            self.inspection = result
            self._render_inspection()
            self.app.set_ready("Arquivo analisado")

        def err(exc):
            self._analysis_running = False
            self.select_btn.configure(state="normal")
            self.cancel_analysis_btn.configure(state="disabled", text="Cancelar análise")
            self.inspection = None
            if "cancelad" in str(exc).lower():
                self.quality_title.configure(text="Análise cancelada.", text_color=COLORS["warning"])
                self.app.set_ready("Análise cancelada")
            else:
                self.quality_title.configure(text="Não foi possível analisar este arquivo.", text_color=COLORS["danger"])
                self.quality_detail.configure(text=str(exc))
                self.app.set_error(str(exc))

        if not self.app.tasks.submit("análise de arquivo", work, ok, err):
            self._analysis_running = False
            self.select_btn.configure(state="normal")
            self.cancel_analysis_btn.configure(state="disabled")
            self.msg.set("Há outra tarefa em andamento.", "warning")

    def cancel_analysis(self):
        if self._analysis_running and self.app.tasks.busy:
            self.cancel_analysis_btn.configure(state="disabled", text="Cancelando…")
            self.app.cancel_task()

    def _render_inspection(self):
        r = self.inspection
        if not r:
            return
        symbols = {"ideal": "✓", "adequate": "ℹ", "caution": "⚠"}
        self.quality_title.configure(
            text=f"{symbols.get(r.status, '•')} {r.status_label}", text_color=COLORS[r.kind],
        )
        self.quality_summary.configure(text=r.summary, text_color=COLORS["text"])
        details = []
        if r.status == "ideal":
            details.append("Qualidade ideal para processamento e retorno ao DAW.")
        elif r.status == "adequate":
            details.append("Arquivo lossless adequado. Não é necessário reexportar apenas para usar o GBW.")
        else:
            details.append("Pode ser processado, mas há pontos que merecem sua decisão antes do pitch.")
        details.extend(r.notes[:2])
        if r.issues:
            details.append("Ressalvas: " + "; ".join(i.title for i in r.issues))
        self.quality_detail.configure(text=" ".join(details))

    def choose_output_dir(self):
        start = str(self.output_dir or (self.input_path.parent if self.input_path else Path.home()))
        chosen = filedialog.askdirectory(title="Escolher pasta de saída", initialdir=start)
        if chosen:
            self.output_dir = Path(chosen).expanduser().resolve()
            self.output_dir_label.configure(text=str(self.output_dir))

    @staticmethod
    def _safe_piece(text: str) -> str:
        text = re.sub(r"\s+", "", text or "")
        return re.sub(r"[^A-Za-z0-9#]+", "", text)[:28] or "tuning"

    def _output_path(self, semitones: int) -> Path:
        assert self.input_path
        out_dir = self.output_dir or self.input_path.parent
        out_dir.mkdir(parents=True, exist_ok=True)
        ext = ".flac" if self.output_format.get() == "FLAC 24-bit" else ".wav"
        if self.mode.get() == "tuning":
            suffix = f"_{self._safe_piece(self.original.get())}_para_{self._safe_piece(self.target.get())}"
        else:
            suffix = f"_pitch_{semitones:+d}st"
        base = self.input_path.stem + suffix
        candidate = out_dir / f"{base}{ext}"
        n = 2
        while candidate.exists():
            candidate = out_dir / f"{base}_{n}{ext}"
            n += 1
        return candidate

    def _confirm_caution(self) -> str:
        """Retorna 'continue' ou 'replace'. Só é chamado para status caution."""
        r = self.inspection
        if not r:
            return "replace"
        Dialog = getattr(ctk, "CTkToplevel", tk.Toplevel)
        dlg = Dialog(self.app)
        dlg.title("Verifique a qualidade do arquivo")
        try:
            dlg.transient(self.app)
            dlg.grab_set()
            dlg.resizable(True, True)
            sw, sh = self.app.winfo_screenwidth(), self.app.winfo_screenheight()
            dlg.geometry(f"{min(900, max(700, sw-120))}x{min(760, max(560, sh-120))}")
        except Exception:
            pass
        result = {"value": "replace"}
        outer = ctk.CTkScrollableFrame(dlg, fg_color="transparent", corner_radius=0)
        configure_scroll_speed(outer, self.app.settings.ui_scale)
        outer.pack(fill="both", expand=True, padx=12, pady=12)
        outer.grid_columnconfigure(0, weight=1)
        ResponsiveLabel(
            outer, text="⚠ Verifique a qualidade do arquivo",
            font=ctk.CTkFont(size=22, weight="bold"), text_color=COLORS["warning"], max_wrap=None,
        ).grid(row=0, column=0, sticky="ew")
        ResponsiveLabel(
            outer,
            text="O arquivo pode ser processado, mas encontramos condições que podem afetar o resultado. O GBW ainda não iniciou o pitch e está aguardando sua decisão.",
            text_color=COLORS["muted"], max_wrap=None,
        ).grid(row=1, column=0, sticky="ew", pady=(8, 12))
        box = ctk.CTkFrame(outer, fg_color=COLORS["card_alt"], corner_radius=10)
        box.grid(row=2, column=0, sticky="nsew")
        box.grid_columnconfigure(0, weight=1)
        lines = []
        for issue in r.issues:
            lines.append(f"{issue.title}\n{issue.detail}\nIdeal: {issue.ideal}\nRisco: {issue.risk}")
        lines.append("Recomendação geral para nova exportação: WAV • 32-bit float • mesma taxa de amostragem da sessão do DAW.")
        ResponsiveLabel(box, text="\n\n".join(lines), max_wrap=None).grid(
            row=0, column=0, sticky="ew", padx=14, pady=14
        )
        ResponsiveLabel(
            outer,
            text="Você mantém total autonomia: pode trocar o input ou continuar assumindo essas limitações.",
            text_color=COLORS["muted"], max_wrap=None,
        ).grid(row=3, column=0, sticky="ew", pady=(12, 12))
        buttons = ctk.CTkFrame(outer, fg_color="transparent")
        buttons.grid(row=4, column=0, sticky="e")

        def finish(value):
            result["value"] = value
            try:
                dlg.grab_release()
            except Exception:
                pass
            dlg.destroy()

        ctk.CTkButton(
            buttons, text="Escolher outro arquivo", fg_color="transparent", border_width=1,
            command=lambda: finish("replace"),
        ).pack(side="left")
        ctk.CTkButton(
            buttons, text="Continuar mesmo assim", command=lambda: finish("continue"),
        ).pack(side="left", padx=(10, 0))
        try:
            dlg.protocol("WM_DELETE_WINDOW", lambda: finish("replace"))
            self.app.wait_window(dlg)
        except Exception:
            pass
        return result["value"]

    def run(self):
        if self.app.tasks.busy:
            self.msg.set("Há outra tarefa em andamento.", "warning")
            return
        if not self.input_path or not self.inspection:
            self.msg.set("Selecione e analise um arquivo primeiro.", "warning")
            return
        semitones = self.recompute()
        if semitones is None:
            self.msg.set("Corrija os parâmetros de conversão.", "danger")
            return
        if self.inspection.status == "caution":
            self.app.status.set("Aguardando", "warning")
            self.app.detail_label.configure(text="Aguardando sua decisão sobre a qualidade do arquivo…")
            decision = self._confirm_caution()
            if decision != "continue":
                self.app.set_ready("Aguardando novo arquivo")
                self.after(20, self.select_file)
                return
            self.app.logger.warning("Pitch de arquivo iniciado com ressalvas de qualidade aceitas pelo usuário.")

        output = self._output_path(int(semitones))
        self.run_btn.configure(state="disabled")
        self.select_btn.configure(state="disabled")
        self.cancel_btn.configure(state="normal", text="Cancelar")
        self._processing = True
        self.app.set_busy("Processando pitch de arquivo…")

        def progress(frac, msg):
            self.app.tasks.call_ui(self.app.update_progress, frac, msg)

        def work():
            return self.app.file_pitch_service.process(
                self.inspection, output, int(semitones), self.audio_type.get() == "vocal",
                self.output_format.get(), progress,
            )

        def ok(path):
            self._processing = False
            self.last_output = Path(path)
            self.run_btn.configure(state="normal")
            self.select_btn.configure(state="normal")
            self.cancel_btn.configure(state="disabled", text="Cancelar")
            self.open_btn.configure(state="normal")
            self.msg.set(f"Concluído: {self.last_output.name}", "success")
            self.app.set_ready("Pitch concluído")

        def err(exc):
            self._processing = False
            self.run_btn.configure(state="normal")
            self.select_btn.configure(state="normal")
            self.cancel_btn.configure(state="disabled", text="Cancelar")
            if "cancelad" in str(exc).lower():
                self.msg.set("Processamento cancelado. Nenhum arquivo parcial foi mantido.", "warning")
                self.app.set_ready("Pitch cancelado")
            else:
                self.msg.set(str(exc), "danger")
                self.app.set_error(str(exc))

        if not self.app.tasks.submit("pitch de arquivo", work, ok, err):
            self._processing = False
            self.run_btn.configure(state="normal")
            self.select_btn.configure(state="normal")
            self.cancel_btn.configure(state="disabled")
            self.msg.set("Há outra tarefa em andamento.", "warning")

    def cancel_processing(self):
        if self._processing and self.app.tasks.busy:
            self.cancel_btn.configure(state="disabled", text="Cancelando…")
            self.app.cancel_task()

    def open_output(self):
        if self.last_output and self.last_output.exists():
            self.app.open_path(self.last_output.parent)

    def sync_from_doc(self):
        self._sync_inverse_button()

    def on_show(self):
        self._sync_inverse_button()
