from __future__ import annotations

import webbrowser
from typing import Callable

from gbw.ui.ctk_compat import ctk
from gbw.ui.theme import COLORS


def _widget_scale(widget) -> float:
    """Retorna a escala lógica do CustomTkinter sem depender dela no fallback ttk."""
    try:
        return max(0.1, float(widget._get_widget_scaling()))
    except Exception:
        return 1.0


def configure_scroll_speed(scrollable, scale: float = 1.0) -> int:
    """Ajusta a roda do mouse em CTkScrollableFrame no Linux.

    O CustomTkinter usa 30 px por notch no Linux. Em escalas altas isso fica
    muito lento porque os controles crescem, mas o incremento permanece fixo.
    Mantemos cerca de uma linha/bloco por notch, proporcional à escala.
    Retorna o incremento aplicado para facilitar testes.
    """
    try:
        scale = max(1.0, float(scale))
    except Exception:
        scale = 1.0
    increment = max(55, int(round(55 * scale)))
    canvas = getattr(scrollable, "_parent_canvas", None)
    if canvas is not None:
        try:
            canvas.configure(yscrollincrement=increment, xscrollincrement=increment)
        except Exception:
            pass
    return increment


class ResponsiveLabel(ctk.CTkLabel):
    """Label com wrap responsivo sem realimentação de geometria.

    Regra importante: o widget NUNCA observa o próprio ``<Configure>`` para
    recalcular ``wraplength``. Em CustomTkinter, alterar ``wraplength`` muda a
    geometria interna do CTkLabel e pode emitir outro Configure, criando um loop
    em escalas altas (200–250%).

    Em vez disso, observamos somente a largura do CONTÊINER. A atualização é
    debounced, usa cache de largura e só chama ``configure`` quando o valor de
    wrap realmente mudou. Assim a mudança de altura causada pela quebra de
    linhas não consegue disparar a si mesma.
    """
    _DEBOUNCE_MS = 70
    _WIDTH_EPSILON_PX = 3
    _WRAP_EPSILON_LOGICAL = 3

    def __init__(self, master, *, max_wrap: int | None = None, min_wrap: int = 140,
                 horizontal_margin: int = 12, **kwargs):
        kwargs.setdefault("anchor", "w")
        kwargs.setdefault("justify", "left")
        # Um valor inicial moderado evita que, ainda com a janela withdrawn, um
        # texto longo solicite milhares de pixels físicos em escalas de TV.
        initial_wrap = max(min_wrap, min(max_wrap or 520, 520))
        kwargs.setdefault("wraplength", initial_wrap)
        super().__init__(master, **kwargs)

        self._responsive_max_wrap = max_wrap
        self._responsive_min_wrap = min_wrap
        self._responsive_margin = horizontal_margin
        self._responsive_container = master
        self._responsive_bind_id = None
        self._responsive_after_id = None
        self._responsive_pending_width = None
        self._responsive_last_container_width = None
        self._responsive_last_wrap = initial_wrap
        self._responsive_destroyed = False
        self._responsive_apply_count = 0  # útil para diagnóstico/testes de regressão

        try:
            self._responsive_bind_id = master.bind(
                "<Configure>", self._on_container_resize, add="+"
            )
        except Exception:
            self._responsive_bind_id = None

        try:
            self.bind("<Destroy>", self._on_destroy, add="+")
        except Exception:
            pass

        # Não usa after_idle: durante startup/escala alta pode existir uma fila
        # contínua de eventos de geometria. Um timer real garante que a tarefa
        # tenha oportunidade de executar quando a janela for mapeada.
        try:
            self._responsive_after_id = self.after(120, self._apply_pending_wrap)
        except Exception:
            self._responsive_after_id = None

    def _on_container_resize(self, event=None):
        if self._responsive_destroyed:
            return
        try:
            width = int(getattr(event, "width", 0) or self._responsive_container.winfo_width())
        except Exception:
            return
        if width <= 5:
            return
        last = self._responsive_last_container_width
        if last is not None and abs(width - last) <= self._WIDTH_EPSILON_PX:
            return
        self._responsive_last_container_width = width
        self._responsive_pending_width = width
        self._schedule_wrap_update()

    def _schedule_wrap_update(self):
        if self._responsive_destroyed:
            return
        if self._responsive_after_id:
            try:
                self.after_cancel(self._responsive_after_id)
            except Exception:
                pass
        try:
            self._responsive_after_id = self.after(self._DEBOUNCE_MS, self._apply_pending_wrap)
        except Exception:
            self._responsive_after_id = None

    def _apply_pending_wrap(self):
        self._responsive_after_id = None
        if self._responsive_destroyed:
            return
        try:
            if not self.winfo_exists():
                return
            width = int(self._responsive_pending_width or self._responsive_container.winfo_width())
            if width <= 5:
                return
            self._responsive_pending_width = None
            scale = _widget_scale(self)
            logical = max(
                self._responsive_min_wrap,
                int(width / scale) - self._responsive_margin,
            )
            if self._responsive_max_wrap:
                logical = min(logical, self._responsive_max_wrap)

            if abs(logical - self._responsive_last_wrap) <= self._WRAP_EPSILON_LOGICAL:
                return

            self._responsive_last_wrap = logical
            self._responsive_apply_count += 1
            self.configure(wraplength=logical)
        except Exception:
            # Layout responsivo nunca deve impedir a aplicação de abrir.
            return

    def _on_destroy(self, event=None):
        # <Destroy> também pode propagar de filhos; só finaliza quando é este widget.
        try:
            if event is not None and getattr(event, "widget", self) is not self:
                return
        except Exception:
            pass
        self._responsive_destroyed = True
        if self._responsive_after_id:
            try:
                self.after_cancel(self._responsive_after_id)
            except Exception:
                pass
            self._responsive_after_id = None
        if self._responsive_bind_id:
            try:
                self._responsive_container.unbind("<Configure>", self._responsive_bind_id)
            except Exception:
                pass
            self._responsive_bind_id = None


class PageTitle(ctk.CTkFrame):
    def __init__(self, master, title: str, subtitle: str = ""):
        super().__init__(master, fg_color="transparent")
        self.grid_columnconfigure(0, weight=1)
        ResponsiveLabel(
            self, text=title, font=ctk.CTkFont(size=28, weight="bold"),
            max_wrap=None, min_wrap=180, horizontal_margin=8,
        ).grid(row=0, column=0, sticky="ew")
        if subtitle:
            ResponsiveLabel(
                self, text=subtitle, text_color=COLORS["muted"],
                font=ctk.CTkFont(size=14), max_wrap=None, min_wrap=180,
                horizontal_margin=8,
            ).grid(row=1, column=0, sticky="ew", pady=(4, 0))


class Card(ctk.CTkFrame):
    def __init__(self, master, title: str = "", subtitle: str = "", **kwargs):
        super().__init__(master, fg_color=COLORS["card"], corner_radius=12,
                         border_width=1, border_color=COLORS["border"], **kwargs)
        self.grid_columnconfigure(0, weight=1)
        row = 0
        if title:
            ResponsiveLabel(
                self, text=title, font=ctk.CTkFont(size=17, weight="bold"),
                max_wrap=None, horizontal_margin=28,
            ).grid(row=row, column=0, sticky="ew", padx=16, pady=(14, 2))
            row += 1
        if subtitle:
            ResponsiveLabel(
                self, text=subtitle, text_color=COLORS["muted"],
                max_wrap=None, horizontal_margin=28,
            ).grid(row=row, column=0, sticky="ew", padx=16, pady=(0, 10))
            row += 1
        self.body = ctk.CTkFrame(self, fg_color="transparent")
        self.body.grid(row=row, column=0, sticky="nsew", padx=16, pady=(4, 14))
        self.body.grid_columnconfigure(0, weight=1)


class StatusPill(ctk.CTkFrame):
    def __init__(self, master, text="Pronto", kind="success"):
        super().__init__(master, corner_radius=14, fg_color=COLORS.get(kind, COLORS["accent"]))
        self.label = ctk.CTkLabel(self, text=text, text_color="#FFFFFF",
                                  font=ctk.CTkFont(size=13, weight="bold"))
        self.label.pack(padx=12, pady=5)

    def set(self, text, kind="success"):
        self.configure(fg_color=COLORS.get(kind, COLORS["accent"]))
        self.label.configure(text=text)


class InlineMessage(ctk.CTkFrame):
    def __init__(self, master):
        super().__init__(master, fg_color="transparent")
        self.label = ResponsiveLabel(self, text="", max_wrap=None, horizontal_margin=4)
        self.label.pack(fill="x")

    def set(self, text: str, kind="muted"):
        color = COLORS.get(kind, COLORS["muted"])
        self.label.configure(text=text, text_color=color)

    def clear(self):
        self.label.configure(text="")


class SourceCandidateCard(Card):
    def __init__(self, master, candidate, on_use: Callable, on_open: Callable,
                 selected=False, recommended=False):
        rejected = bool(getattr(candidate, "preview_only", False))
        if rejected:
            badge, badge_color = "TRECHO CURTO", COLORS["danger"]
        elif selected and recommended:
            badge, badge_color = "✓ RECOMENDADA", COLORS["success"]
        elif selected:
            badge, badge_color = "✓ SELECIONADA", COLORS["success"]
        elif recommended:
            badge, badge_color = "RECOMENDADA", COLORS["accent"]
        else:
            badge, badge_color = candidate.source, COLORS["accent"]
        super().__init__(master, title=f"{candidate.title}",
                         subtitle=f"{candidate.uploader or 'Artista/canal não informado'}")
        if selected:
            try:
                self.configure(border_color=COLORS["success"], border_width=2)
            except Exception:
                pass
        elif recommended:
            try:
                self.configure(border_color=COLORS["accent"], border_width=2)
            except Exception:
                pass

        top = ctk.CTkFrame(self.body, fg_color="transparent")
        top.grid(row=0, column=0, sticky="ew")
        top.grid_columnconfigure(3, weight=1)
        ResponsiveLabel(top, text=badge, font=ctk.CTkFont(size=12, weight="bold"),
                        text_color=badge_color, max_wrap=260).grid(row=0, column=0, sticky="w")
        ctk.CTkLabel(top, text=f"{candidate.score}/100",
                     font=ctk.CTkFont(size=17, weight="bold")).grid(row=0, column=1, padx=10)
        ResponsiveLabel(top, text=candidate.quality,
                        text_color=COLORS["danger"] if rejected else COLORS["muted"],
                        max_wrap=260).grid(row=0, column=2, sticky="w")
        duration = float(getattr(candidate, "duration", 0) or 0)
        if duration > 0:
            d = f"{int(duration)//60}:{int(duration)%60:02d}"
            ctk.CTkLabel(top, text=d,
                         text_color=COLORS["warning"] if getattr(candidate, "duration_warning", False)
                         else COLORS["muted"]).grid(row=0, column=3, sticky="e")
        ResponsiveLabel(
            self.body, text=candidate.reason,
            text_color=COLORS["danger"] if rejected else COLORS["muted"],
            max_wrap=None, horizontal_margin=8,
        ).grid(row=1, column=0, sticky="ew", pady=(7, 9))
        actions = ctk.CTkFrame(self.body, fg_color="transparent")
        actions.grid(row=2, column=0, sticky="w")
        button_text = "Selecionada ✓" if selected else "Usar fonte"
        use_btn = ctk.CTkButton(actions, text=button_text, width=150, command=lambda: on_use(candidate))
        use_btn.pack(side="left")
        if rejected or selected:
            try:
                use_btn.configure(state="disabled")
            except Exception:
                pass
        ctk.CTkButton(actions, text="Abrir", width=80, fg_color="transparent", border_width=1,
                      command=lambda: on_open(candidate)).pack(side="left", padx=8)


class TuningCandidateCard(Card):
    def __init__(self, master, candidate, confidence: int, on_use: Callable,
                 selected=False, recommended=False):
        tuning = str(candidate.get("tuning", "?"))
        final = float(candidate.get("final_score", 0))
        super().__init__(master, title=tuning, subtitle=" ".join(candidate.get("strings", []) or []))

        if selected and recommended:
            label = f"Selecionada ✓ • mais provável • {confidence}%"
            label_color = COLORS["success"]
        elif selected:
            label = f"Selecionada ✓ • compatibilidade {final:.0f}%"
            label_color = COLORS["success"]
        elif recommended:
            label = f"Mais provável • {confidence}%"
            label_color = COLORS["accent"]
        else:
            label = f"Compatibilidade {final:.0f}%"
            label_color = COLORS["accent"]

        if selected:
            try:
                self.configure(border_color=COLORS["success"], border_width=2)
            except Exception:
                pass

        ResponsiveLabel(
            self.body, text=label, font=ctk.CTkFont(weight="bold"),
            text_color=label_color, max_wrap=360,
        ).grid(row=0, column=0, sticky="w")
        button = ctk.CTkButton(
            self.body, text="Selecionada ✓" if selected else "Usar afinação",
            width=145, command=lambda: on_use(tuning)
        )
        button.grid(row=1, column=0, sticky="w", pady=(10, 0))
        if selected:
            button.configure(state="disabled")
