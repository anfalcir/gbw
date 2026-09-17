from __future__ import annotations

from tkinter import filedialog

from gbw.ui.ctk_compat import ctk
from gbw.ui.widgets import PageTitle, Card, InlineMessage, ResponsiveLabel


SCALE_OPTIONS = {
    "100% — Compacta": 1.00,
    "115% — Média": 1.15,
    "125% — Confortável (recomendado)": 1.25,
    "140% — Grande": 1.40,
    "160% — Extra grande": 1.60,
    "175% — TV / distância curta": 1.75,
    "200% — TV / grande": 2.00,
    "225% — TV / muito grande": 2.25,
    "250% — TV / máximo": 2.50,
}

THEME_OPTIONS = {
    "Sistema": "System",
    "Escuro": "Dark",
    "Claro": "Light",
}


def theme_label(value: str) -> str:
    for label, code in THEME_OPTIONS.items():
        if code.lower() == str(value).lower():
            return label
    return "Sistema"

SEPARATOR_OPTIONS = {
    "A — Alta qualidade": "A",
    "B — Alternativa": "B",
    "A + B — Comparar": "AB",
}


def separator_label(value: str) -> str:
    for label, code in SEPARATOR_OPTIONS.items():
        if code == value:
            return label
    return "A — Alta qualidade"


def scale_label(value: float) -> str:
    return min(SCALE_OPTIONS, key=lambda label: abs(SCALE_OPTIONS[label] - float(value)))


class SettingsPage(ctk.CTkScrollableFrame):
    page_key = "settings"

    def __init__(self, master, app):
        super().__init__(master, fg_color="transparent", corner_radius=0)
        self.app = app
        self.grid_columnconfigure(0, weight=1)

        PageTitle(
            self,
            "Configurações",
            "Ajuste aparência e preferências.",
        ).grid(row=0, column=0, sticky="ew", pady=(0, 16))

        visual = Card(self, "Aparência e tamanho")
        visual.grid(row=1, column=0, sticky="ew", pady=7)
        visual.body.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(visual.body, text="Tema").grid(row=0, column=0, sticky="w", pady=7)
        self.appearance = ctk.StringVar(value=theme_label(app.settings.appearance))
        ctk.CTkOptionMenu(
            visual.body,
            values=list(THEME_OPTIONS),
            variable=self.appearance,
            command=self.change_appearance,
            width=180,
        ).grid(row=0, column=1, sticky="w", padx=12, pady=7)

        ctk.CTkLabel(visual.body, text="Escala da interface").grid(row=1, column=0, sticky="w", pady=7)
        self.scale = ctk.StringVar(value=scale_label(app.settings.ui_scale))
        self.scale_menu = ctk.CTkOptionMenu(
            visual.body,
            values=list(SCALE_OPTIONS),
            variable=self.scale,
            command=self.change_scale,
            width=285,
        )
        self.scale_menu.grid(row=1, column=1, sticky="w", padx=12, pady=7)
        ResponsiveLabel(
            visual.body,
            text="Para TV, use 175% a 250%.",
            max_wrap=None,
        ).grid(row=2, column=0, columnspan=2, sticky="ew", pady=(2, 4))

        defaults = Card(self, "Padrões")
        defaults.grid(row=2, column=0, sticky="ew", pady=7)
        defaults.body.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(defaults.body, text="Pasta de projetos").grid(row=0, column=0, sticky="w", pady=7)
        self.root = ctk.StringVar(value=app.settings.project_root)
        ctk.CTkEntry(defaults.body, textvariable=self.root).grid(row=0, column=1, sticky="ew", padx=12, pady=7)
        ctk.CTkButton(defaults.body, text="Escolher…", width=105, command=self.pick_root).grid(row=0, column=2, pady=7)

        ctk.CTkLabel(defaults.body, text="Busca").grid(row=1, column=0, sticky="w", pady=7)
        self.depth = ctk.StringVar(value=app.settings.search_depth)
        ctk.CTkOptionMenu(defaults.body, values=["Robusta", "Máxima"], variable=self.depth, width=160).grid(
            row=1, column=1, sticky="w", padx=12, pady=7
        )

        ctk.CTkLabel(defaults.body, text="Separador padrão").grid(row=2, column=0, sticky="w", pady=7)
        self.sep = ctk.StringVar(value=separator_label(app.settings.default_separator))
        ctk.CTkOptionMenu(defaults.body, values=list(SEPARATOR_OPTIONS), variable=self.sep, width=240).grid(
            row=2, column=1, sticky="w", padx=12, pady=7
        )

        ctk.CTkLabel(defaults.body, text="Formato final").grid(row=3, column=0, sticky="w", pady=7)
        self.fmt = ctk.StringVar(value=app.settings.default_output_format)
        ctk.CTkOptionMenu(
            defaults.body,
            values=["FLAC 24-bit", "WAV 24-bit", "WAV 32-bit float"],
            variable=self.fmt,
            width=220,
        ).grid(row=3, column=1, sticky="w", padx=12, pady=7)

        audio = Card(self, "Áudio")
        audio.grid(row=3, column=0, sticky="ew", pady=7)
        audio.body.grid_columnconfigure(1, weight=1)
        ctk.CTkLabel(audio.body, text="Pico máximo (dBFS)").grid(row=0, column=0, sticky="w", pady=7)
        self.peak = ctk.StringVar(value=str(app.settings.master_peak_dbfs))
        ctk.CTkEntry(audio.body, textvariable=self.peak, width=120).grid(row=0, column=1, sticky="w", padx=12, pady=7)
        self.formants = ctk.BooleanVar(value=app.settings.vocal_formants)
        ctk.CTkSwitch(audio.body, text="Preservar timbre dos vocais", variable=self.formants).grid(
            row=1, column=0, columnspan=2, sticky="w", pady=(10, 2)
        )

        ctk.CTkButton(self, text="Salvar configurações", height=42, command=self.save).grid(
            row=4, column=0, sticky="w", pady=14
        )
        self.msg = InlineMessage(self)
        self.msg.grid(row=5, column=0, sticky="ew")

    def change_appearance(self, value):
        ctk.set_appearance_mode(THEME_OPTIONS.get(value, "System"))

    def change_scale(self, value):
        self.app.apply_ui_scale(SCALE_OPTIONS.get(value, 1.25), persist=True)
        self.msg.set(f"Escala aplicada: {int(self.app.settings.ui_scale * 100)}%.", "success")

    def sync_scale_control(self):
        self.scale.set(scale_label(self.app.settings.ui_scale))

    def pick_root(self):
        d = filedialog.askdirectory(title="Pasta de projetos")
        if d:
            self.root.set(d)

    def save(self):
        s = self.app.settings
        s.appearance = THEME_OPTIONS.get(self.appearance.get(), "System")
        s.ui_scale = SCALE_OPTIONS.get(self.scale.get(), s.ui_scale)
        s.project_root = self.root.get().strip()
        s.search_depth = self.depth.get()
        s.default_separator = SEPARATOR_OPTIONS.get(self.sep.get(), "A")
        s.default_output_format = self.fmt.get()
        s.vocal_formants = self.formants.get()
        try:
            s.master_peak_dbfs = float(self.peak.get())
        except ValueError:
            self.msg.set("Valor de pico inválido.", "danger")
            return
        self.app.apply_ui_scale(s.ui_scale, persist=False)
        self.app.save_settings_and_refresh()
        self.msg.set("Configurações salvas.", "success")
