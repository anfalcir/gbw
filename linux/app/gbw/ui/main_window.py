from __future__ import annotations

import logging
import os
import queue
from collections import deque
import shutil
import subprocess
import sys
from pathlib import Path
import tkinter as tk
from tkinter import messagebox

from gbw.config import APP_NAME, APP_VERSION, APP_HOME, Settings, load_settings, save_settings
from gbw.core.runner import CommandRunner
from gbw.core.system import detect_linux_system, system_health
from gbw.logging_setup import configure_logging
from gbw.models import ProjectDocument, WorkflowConfig, ProjectState
from gbw.project import ProjectManager, normalize_project_text
from gbw.services.task_manager import TaskManager
from gbw.services.source_search import SourceSearchService
from gbw.services.audio import AudioService
from gbw.services.tuning import TuningService
from gbw.services.backup import BackupService
from gbw.services.file_pitch import FilePitchService
from gbw.ui.ctk_compat import ctk, USING_CUSTOMTKINTER
from gbw.ui.theme import COLORS
from gbw.ui.widgets import StatusPill, ResponsiveLabel, configure_scroll_speed
from gbw.workflow import WORKFLOW_STEPS, STEP_HINTS, workflow_states, active_step, completed_count, step_number, step_label, header_workflow_text
from gbw.ui.pages.system_page import SystemPage
from gbw.ui.pages.source_page import SourcePage
from gbw.ui.pages.separation_page import SeparationPage
from gbw.ui.pages.tuning_page import TuningPage
from gbw.ui.pages.export_page import ExportPage
from gbw.ui.pages.projects_page import ProjectsPage
from gbw.ui.pages.logs_page import LogsPage
from gbw.ui.pages.settings_page import SettingsPage
from gbw.ui.pages.file_pitch_page import FilePitchPage

class GuitarBackingWizard(ctk.CTk):
    PAGE_CLASSES = {
        "system": SystemPage,
        "source": SourcePage,
        "separation": SeparationPage,
        "tuning": TuningPage,
        "export": ExportPage,
        "projects": ProjectsPage,
        "logs": LogsPage,
        "file_pitch": FilePitchPage,
        "settings": SettingsPage,
    }

    def __init__(self):
        self.settings: Settings = load_settings()
        ctk.set_appearance_mode(self.settings.appearance)
        try:
            ctk.set_default_color_theme("blue")
            ctk.set_widget_scaling(float(self.settings.ui_scale))
            # Geometria da janela fica previsível; widgets/textos recebem a escala configurada.
            ctk.set_window_scaling(1.0)
        except Exception:
            pass
        super().__init__(className="GuitarBackingWizard")

        # Não exibe a janela enquanto o shell e a primeira página ainda estão sendo construídos.
        try:
            self.withdraw()
        except Exception:
            pass

        self.title(f"{APP_NAME} {APP_VERSION}")
        self._app_icon = None
        self._apply_app_icon()
        self._apply_initial_geometry()

        self.log_queue: queue.Queue[str] = queue.Queue()
        self._session_log_buffer = deque(maxlen=5000)
        self.logger = configure_logging(self.log_queue, APP_HOME / "logs" / "app.log")
        self.tasks = TaskManager(self, self.logger)
        self.runner = CommandRunner(self.logger, self.tasks.cancel_event)
        self.source_service = SourceSearchService(self.runner, self.logger)
        self.audio_service = AudioService(self.runner, self.logger)
        self.file_pitch_service = FilePitchService(self.runner, self.audio_service)
        helper = Path(__file__).resolve().parents[2] / "audio_intelligence.py"
        self.tuning_service = TuningService(self.runner, self.source_service, self.logger, helper)
        self.backup_service = BackupService(Path(__file__).resolve().parents[2], self.logger)
        self.project_manager = ProjectManager(Path(self.settings.project_root))
        self.doc = ProjectDocument(config=WorkflowConfig(
            separator_mode=self.settings.default_separator,
            final_separator="B" if self.settings.default_separator == "B" else "A",
            vocal_formants=self.settings.vocal_formants,
            output_format=self.settings.default_output_format,
        ))
        self._system_health = system_health(self.doc.config.separator_mode)

        self.pages = {}
        self.nav_buttons = {}
        self.nav_section_headers = {}
        self.current_page = ""
        self._build_shell()
        # Sistema só toma a frente quando há um bloqueio real. Em ambiente saudável,
        # o usuário entra diretamente no passo 1 do processo contínuo.
        self.navigate("system" if not self._system_health.get("ok", False) else "source")
        self._log_poll_after_id = self.after(100, self._poll_logs)
        self.protocol("WM_DELETE_WINDOW", self.on_close)
        self.bind("<Control-plus>", lambda _e: self.adjust_ui_scale(+1))
        self.bind("<Control-equal>", lambda _e: self.adjust_ui_scale(+1))
        self.bind("<Control-minus>", lambda _e: self.adjust_ui_scale(-1))
        self._startup_after_id = None
        self._startup_lift_after_id = None
        self._startup_maximize_after_id = None
        if os.environ.get("GBW_HEADLESS_TEST") != "1":
            # Timer real, não after_idle: em escalas altas a fila de eventos de
            # geometria pode ser intensa e atrasar callbacks idle indefinidamente.
            self._startup_after_id = self.after(180, self._finish_startup)
        self.logger.info("%s %s iniciado • escala %.0f%%", APP_NAME, APP_VERSION, self.settings.ui_scale * 100)

    def _apply_app_icon(self):
        """Aplica o ícone da aplicação à janela e à barra de tarefas."""
        try:
            icon_path = Path(__file__).resolve().parents[2] / "assets" / "guitar-backing-wizard.png"
            if icon_path.exists():
                self._app_icon = tk.PhotoImage(file=str(icon_path))
                self.iconphoto(True, self._app_icon)
        except Exception as exc:
            try:
                self.logger.debug("Não foi possível aplicar o ícone da janela: %s", exc)
            except Exception:
                pass

    def _apply_initial_geometry(self):
        try:
            sw, sh = self.winfo_screenwidth(), self.winfo_screenheight()
        except Exception:
            sw, sh = 1360, 880
        width = min(1540, max(1180, int(sw * 0.92)))
        height = min(980, max(760, int(sh * 0.90)))
        width = min(width, max(900, sw - 24))
        height = min(height, max(650, sh - 60))
        x = max(0, (sw - width) // 2)
        y = max(0, (sh - height) // 2)
        self.geometry(f"{width}x{height}+{x}+{y}")
        self.minsize(min(1120, max(900, sw - 80)), min(740, max(650, sh - 100)))

    def _finish_startup(self):
        callback_id = getattr(self, "_startup_after_id", None)
        self._startup_after_id = None
        if callback_id:
            try:
                self.after_cancel(callback_id)
            except Exception:
                pass
        try:
            # Primeiro mapeia a janela. No Linux Mint/Cinnamon o pedido de
            # maximização é mais confiável depois que o gerenciador de janelas
            # já conhece a janela.
            self.deiconify()
            self._maximize_window()
            self._startup_lift_after_id = self.after(20, self._lift_after_startup)
            # Alguns WMs ignoram a primeira solicitação durante o mapeamento; uma
            # segunda tentativa curta torna o startup consistente sem usar fullscreen.
            self._startup_maximize_after_id = self.after(90, self._maximize_after_startup)
        except Exception:
            pass

    def _maximize_window(self):
        """Solicita janela maximizada sem entrar em modo tela cheia."""
        try:
            if sys.platform.startswith("win"):
                self.state("zoomed")
                return True
            # X11/Tk (Linux/Cinnamon) normalmente expõe -zoomed.
            self.attributes("-zoomed", True)
            return True
        except Exception:
            try:
                self.state("zoomed")
                return True
            except Exception:
                # Fallback apenas para WMs/Tk que não suportam estado maximizado.
                try:
                    sw, sh = self.winfo_screenwidth(), self.winfo_screenheight()
                    self.geometry(f"{max(900, sw)}x{max(650, sh)}+0+0")
                except Exception:
                    pass
        return False

    def _maximize_after_startup(self):
        self._startup_maximize_after_id = None
        self._maximize_window()

    def _lift_after_startup(self):
        self._startup_lift_after_id = None
        try:
            self.lift()
        except Exception:
            pass

    def destroy(self):
        # Cancela callbacks periódicos antes de destruir o interpretador Tk. Isso
        # evita mensagens "invalid command name ... after script" em encerramentos
        # rápidos e durante a validação automatizada.
        callback_id = getattr(self, "_startup_after_id", None)
        if callback_id:
            try:
                self.after_cancel(callback_id)
            except Exception:
                pass
            self._startup_after_id = None
        lift_id = getattr(self, "_startup_lift_after_id", None)
        if lift_id:
            try:
                self.after_cancel(lift_id)
            except Exception:
                pass
            self._startup_lift_after_id = None
        maximize_id = getattr(self, "_startup_maximize_after_id", None)
        if maximize_id:
            try:
                self.after_cancel(maximize_id)
            except Exception:
                pass
            self._startup_maximize_after_id = None
        log_id = getattr(self, "_log_poll_after_id", None)
        if log_id:
            try:
                self.after_cancel(log_id)
            except Exception:
                pass
            self._log_poll_after_id = None
        try:
            self.tasks.shutdown()
        except Exception:
            pass
        return super().destroy()

    def _ensure_page(self, key: str):
        if key in self.pages:
            return self.pages[key]
        cls = self.PAGE_CLASSES.get(key)
        if cls is None:
            return None
        page = cls(self.content, self)
        configure_scroll_speed(page, self.settings.ui_scale)
        self.pages[key] = page
        try:
            page.sync_from_doc()
        except Exception:
            pass
        if key == "logs":
            for line in self._session_log_buffer:
                try:
                    page.append(line)
                except Exception:
                    break
        return page

    # ---------- shell ----------
    def _make_nav_section_header(self, parent, key: str, title: str):
        """Cabeçalho visual de grupo da sidebar; não é uma opção clicável."""
        frame = ctk.CTkFrame(
            parent, fg_color=COLORS["nav_section"], corner_radius=9, height=42
        )
        frame.grid_propagate(False)
        frame.grid_columnconfigure(1, weight=1)

        accent = ctk.CTkFrame(
            frame, width=5, height=24, corner_radius=2, fg_color=COLORS["accent"]
        )
        accent.grid(row=0, column=0, sticky="nsw", padx=(9, 10), pady=8)

        label = ctk.CTkLabel(
            frame, text=title, anchor="w", text_color=COLORS["nav_section_text"],
            font=ctk.CTkFont(size=12, weight="bold")
        )
        label.grid(row=0, column=1, sticky="ew", padx=(0, 10), pady=(5, 5))

        # Linha inferior curta reforça a leitura de "seção", sem parecer botão.
        divider = ctk.CTkFrame(
            frame, height=1, corner_radius=0, fg_color=COLORS["nav_section_line"]
        )
        divider.grid(row=1, column=0, columnspan=2, sticky="ew", padx=8, pady=(0, 0))

        self.nav_section_headers[key] = {
            "frame": frame, "label": label, "accent": accent, "divider": divider
        }
        return frame

    def _build_shell(self):
        self.grid_rowconfigure(0, weight=1)
        self.grid_columnconfigure(1, weight=1)

        # Sidebar em três zonas: branding fixo, navegação rolável e ação crítica fixa.
        # Em escalas de TV (200–250%), somente a lista de navegação rola; o botão
        # Cancelar permanece sempre acessível.
        self.sidebar = ctk.CTkFrame(self, width=250, corner_radius=0, fg_color=COLORS["nav"])
        self.sidebar.grid(row=0, column=0, sticky="nsew")
        self.sidebar.grid_propagate(False)
        self.sidebar.grid_columnconfigure(0, weight=1)
        self.sidebar.grid_rowconfigure(1, weight=1)

        self.sidebar_brand = ctk.CTkFrame(self.sidebar, fg_color="transparent", corner_radius=0)
        self.sidebar_brand.grid(row=0, column=0, sticky="ew")
        self.sidebar_brand.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(self.sidebar_brand, text="GBW", font=ctk.CTkFont(size=29, weight="bold"), text_color="#FFFFFF").grid(row=0,column=0,sticky="w",padx=20,pady=(22,0))
        ctk.CTkLabel(self.sidebar_brand, text="Guitar Backing Wizard", font=ctk.CTkFont(size=13), text_color="#B9C4D8").grid(row=1,column=0,sticky="w",padx=20,pady=(0,16))

        self.sidebar_nav = ctk.CTkScrollableFrame(self.sidebar, fg_color=COLORS["nav"], corner_radius=0)
        configure_scroll_speed(self.sidebar_nav, self.settings.ui_scale)
        self.sidebar_nav.grid(row=1, column=0, sticky="nsew", padx=0, pady=0)
        self.sidebar_nav.grid_columnconfigure(0, weight=1)

        # Sistema em alerta: bloco especial no topo, visível apenas quando há
        # dependência bloqueante. O botão normal de Sistema fica no final.
        self.system_alert_block = ctk.CTkFrame(self.sidebar_nav, fg_color="transparent", corner_radius=0)
        self.system_alert_block.grid(row=0, column=0, sticky="ew", padx=0, pady=(0, 8))
        self.system_alert_block.grid_columnconfigure(0, weight=1)
        self.system_alert_title = ctk.CTkLabel(
            self.system_alert_block, text="ATENÇÃO", anchor="w",
            text_color=COLORS["warning"], font=ctk.CTkFont(size=11, weight="bold")
        )
        self.system_alert_title.grid(row=0, column=0, sticky="ew", padx=16, pady=(4, 3))
        self.system_alert_btn = ctk.CTkButton(
            self.system_alert_block, text="⚠ Sistema — verificar", anchor="w", height=46,
            corner_radius=9, fg_color=COLORS["danger"], hover_color=COLORS["danger"],
            text_color="#FFFFFF", font=ctk.CTkFont(size=14, weight="bold"),
            command=lambda: self.navigate("system")
        )
        self.system_alert_btn.grid(row=1, column=0, sticky="ew", padx=(8, 10), pady=3)

        self.workflow_nav = ctk.CTkFrame(self.sidebar_nav, fg_color="transparent", corner_radius=0)
        self.workflow_nav.grid(row=1, column=0, sticky="ew", pady=(0, 10))
        self.workflow_nav.grid_columnconfigure(0, weight=1)
        process_header = self._make_nav_section_header(self.workflow_nav, "process", "PROCESSO")
        process_header.grid(row=0, column=0, sticky="ew", padx=(8, 10), pady=(4, 8))
        for i, (key, label) in enumerate(WORKFLOW_STEPS, start=1):
            btn = ctk.CTkButton(
                self.workflow_nav, text=f"○ {i}. {label}", anchor="w", height=46,
                corner_radius=9, fg_color="transparent", hover_color=COLORS["nav_hover"],
                text_color="#FFFFFF", font=ctk.CTkFont(size=14),
                command=lambda k=key: self.navigate(k)
            )
            btn.grid(row=i, column=0, sticky="ew", padx=(8, 10), pady=3)
            self.nav_buttons[key] = btn

        self.management_nav = ctk.CTkFrame(self.sidebar_nav, fg_color="transparent", corner_radius=0)
        self.management_nav.grid(row=2, column=0, sticky="ew", pady=(0, 10))
        self.management_nav.grid_columnconfigure(0, weight=1)
        management_header = self._make_nav_section_header(self.management_nav, "management", "GERENCIAMENTO")
        management_header.grid(row=0, column=0, sticky="ew", padx=(8, 10), pady=(4, 8))
        for i, (key, label) in enumerate((("projects", "Projetos"), ("logs", "Logs")), start=1):
            btn = ctk.CTkButton(
                self.management_nav, text=label, anchor="w", height=44, corner_radius=9,
                fg_color="transparent", hover_color=COLORS["nav_hover"], text_color="#FFFFFF",
                font=ctk.CTkFont(size=14), command=lambda k=key: self.navigate(k)
            )
            btn.grid(row=i, column=0, sticky="ew", padx=(8, 10), pady=3)
            self.nav_buttons[key] = btn

        self.tools_nav = ctk.CTkFrame(self.sidebar_nav, fg_color="transparent", corner_radius=0)
        self.tools_nav.grid(row=3, column=0, sticky="ew", pady=(0, 10))
        self.tools_nav.grid_columnconfigure(0, weight=1)
        tools_header = self._make_nav_section_header(self.tools_nav, "tools", "FERRAMENTAS")
        tools_header.grid(row=0, column=0, sticky="ew", padx=(8, 10), pady=(4, 8))
        self.file_pitch_nav_btn = ctk.CTkButton(
            self.tools_nav, text="Pitch de Arquivo", anchor="w", height=44, corner_radius=9,
            fg_color="transparent", hover_color=COLORS["nav_hover"], text_color="#FFFFFF",
            font=ctk.CTkFont(size=14), command=lambda: self.navigate("file_pitch")
        )
        self.file_pitch_nav_btn.grid(row=1, column=0, sticky="ew", padx=(8, 10), pady=3)
        self.nav_buttons["file_pitch"] = self.file_pitch_nav_btn

        self.application_nav = ctk.CTkFrame(self.sidebar_nav, fg_color="transparent", corner_radius=0)
        self.application_nav.grid(row=4, column=0, sticky="ew", pady=(0, 8))
        self.application_nav.grid_columnconfigure(0, weight=1)
        application_header = self._make_nav_section_header(self.application_nav, "application", "APLICATIVO")
        application_header.grid(row=0, column=0, sticky="ew", padx=(8, 10), pady=(4, 8))
        self.settings_nav_btn = ctk.CTkButton(
            self.application_nav, text="Configurações", anchor="w", height=44, corner_radius=9,
            fg_color="transparent", hover_color=COLORS["nav_hover"], text_color="#FFFFFF",
            font=ctk.CTkFont(size=14), command=lambda: self.navigate("settings")
        )
        self.settings_nav_btn.grid(row=1, column=0, sticky="ew", padx=(8, 10), pady=3)
        self.nav_buttons["settings"] = self.settings_nav_btn
        self.system_normal_btn = ctk.CTkButton(
            self.application_nav, text="Sistema", anchor="w", height=44, corner_radius=9,
            fg_color="transparent", hover_color=COLORS["nav_hover"], text_color=COLORS["success"],
            font=ctk.CTkFont(size=14), command=lambda: self.navigate("system")
        )
        self.system_normal_btn.grid(row=2, column=0, sticky="ew", padx=(8, 10), pady=3)
        self.nav_buttons["system"] = self.system_normal_btn

        self.sidebar_footer = ctk.CTkFrame(self.sidebar, fg_color=COLORS["nav"], corner_radius=0)
        self.sidebar_footer.grid(row=2, column=0, sticky="ew")
        self.sidebar_footer.grid_columnconfigure(0, weight=1)
        self.close_project_btn = ctk.CTkButton(
            self.sidebar_footer, text="Sair", height=42,
            font=ctk.CTkFont(size=13), fg_color="transparent",
            hover_color=COLORS["nav_hover"], border_width=1,
            border_color=COLORS["border"], state="normal", command=self.close_project
        )
        self.close_project_btn.grid(row=0,column=0,sticky="ew",padx=12,pady=(8,18))

        self.refresh_navigation()

        self.main = ctk.CTkFrame(self, fg_color="transparent", corner_radius=0)
        self.main.grid(row=0,column=1,sticky="nsew");self.main.grid_rowconfigure(1,weight=1);self.main.grid_columnconfigure(0,weight=1)
        self.header=ctk.CTkFrame(self.main,corner_radius=0,fg_color=COLORS["card"])
        self.header.grid(row=0,column=0,sticky="ew",padx=0,pady=0);self.header.grid_columnconfigure(0,weight=1)
        self.project_label=ResponsiveLabel(self.header,text="Nenhum projeto aberto",font=ctk.CTkFont(size=18,weight="bold"),max_wrap=None,horizontal_margin=44)
        self.project_label.grid(row=0,column=0,sticky="ew",padx=22,pady=(14,1))
        self.detail_label=ResponsiveLabel(self.header,text="Comece pela Fonte ou abra um projeto.",text_color=COLORS["muted"],font=ctk.CTkFont(size=14),max_wrap=None,horizontal_margin=44)
        self.detail_label.grid(row=1,column=0,sticky="ew",padx=22,pady=(0,12))
        self.status=StatusPill(self.header,"Pronto","success");self.status.grid(row=0,column=1,rowspan=2,padx=12)
        self.progress=ctk.CTkProgressBar(self.header,width=210,mode="indeterminate",progress_color=COLORS["accent"])
        self.progress.grid(row=0,column=2,rowspan=2,padx=(0,22));self.progress.set(0)

        self.content=ctk.CTkFrame(self.main,fg_color="transparent",corner_radius=0)
        self.content.grid(row=1,column=0,sticky="nsew",padx=22,pady=20);self.content.grid_rowconfigure(0,weight=1);self.content.grid_columnconfigure(0,weight=1)

    def refresh_system_health(self, force: bool = False):
        # O probe é rápido e somente consulta executáveis/capacidade R3. Mantemos o
        # resultado em cache para não repetir subprocessos a cada repaint.
        if force or not hasattr(self, "_system_health"):
            self._system_health = system_health(self.doc.config.separator_mode)
        self.refresh_navigation()
        return self._system_health

    def refresh_navigation(self):
        if not hasattr(self, "workflow_nav"):
            return
        health = getattr(self, "_system_health", {"ok": True, "blocking": []})
        unhealthy = not bool(health.get("ok", True))
        if unhealthy:
            self.system_alert_block.grid()
            self.system_normal_btn.grid_remove()
            issues = health.get("blocking", []) or []
            text = "⚠ Sistema — verificar"
            if issues:
                text = f"⚠ Sistema — {len(issues)} problema(s)"
            self.system_alert_btn.configure(text=text)
        else:
            self.system_alert_block.grid_remove()
            self.system_normal_btn.grid()
            self.system_normal_btn.configure(text="Sistema", text_color=COLORS["success"])

        states = workflow_states(self.doc)
        current_flow = active_step(self.doc)
        for idx, (key, label) in enumerate(WORKFLOW_STEPS, start=1):
            btn = self.nav_buttons.get(key)
            if not btn:
                continue
            if states[key]:
                prefix = "✓"
                color = COLORS["success"]
            elif key == current_flow:
                prefix = "●"
                color = "#FFFFFF"
            else:
                prefix = "○"
                color = "#FFFFFF"
            btn.configure(
                text=f"{prefix} {idx}. {label}",
                text_color=color,
                fg_color=COLORS["accent"] if self.current_page == key else "transparent",
                font=ctk.CTkFont(size=14, weight="bold" if key == current_flow else "normal"),
            )

        for key in ("projects", "logs", "file_pitch", "settings"):
            btn = self.nav_buttons.get(key)
            if btn:
                btn.configure(fg_color=COLORS["accent"] if self.current_page == key else "transparent")
        system_selected = self.current_page == "system"
        self.system_normal_btn.configure(fg_color=COLORS["accent"] if system_selected and not unhealthy else "transparent")
        self.system_alert_btn.configure(fg_color=COLORS["accent"] if system_selected and unhealthy else COLORS["danger"])

    def workflow_step_status(self):
        return workflow_states(self.doc), active_step(self.doc), completed_count(self.doc)

    def _build_pages(self):
        """Constrói todas as páginas somente quando solicitado (útil para smoke-tests).

        O startup normal usa criação preguiçosa para eliminar flicker e reduzir o tempo até a
        primeira tela utilizável.
        """
        for key in self.PAGE_CLASSES:
            self._ensure_page(key)

    def navigate(self,key:str):
        page = self._ensure_page(key)
        if page is None:
            return
        # Nunca deixa frames roláveis empilhados. Somente a página atual fica gerenciada
        # pelo grid, evitando o bug em que Configurações podia aparecer sob "Sistema".
        for k, other in self.pages.items():
            if other is not page:
                try:
                    other.grid_remove()
                except Exception:
                    pass
        page.grid(row=0,column=0,sticky="nsew")
        self.current_page=key
        self.refresh_navigation()
        try:
            page.sync_from_doc()
        except Exception:
            pass
        if key == "separation":
            try: page.refresh_previews()
            except Exception: pass
        elif key == "tuning":
            try: page.show_analysis(self.doc.state.tuning_analysis); page.recompute()
            except Exception: pass
        try:page.on_show()
        except AttributeError:pass
        self.refresh_header()

    def apply_ui_scale(self, scale: float, persist: bool = True):
        scale = min(2.50, max(1.00, round(float(scale), 2)))
        self.settings.ui_scale = scale
        try:
            ctk.set_widget_scaling(scale)
        except Exception:
            pass
        configure_scroll_speed(getattr(self, "sidebar_nav", None), scale)
        for page in getattr(self, "pages", {}).values():
            configure_scroll_speed(page, scale)
        if persist:
            try: save_settings(self.settings)
            except Exception: pass
        self.logger.info("Escala da interface alterada para %.0f%%", scale * 100)

    def adjust_ui_scale(self, direction: int):
        levels = [1.00, 1.15, 1.25, 1.40, 1.60, 1.75, 2.00, 2.25, 2.50]
        current = float(self.settings.ui_scale)
        idx = min(range(len(levels)), key=lambda i: abs(levels[i] - current))
        idx = max(0, min(len(levels)-1, idx + (1 if direction > 0 else -1)))
        self.apply_ui_scale(levels[idx])
        settings_page = self.pages.get("settings")
        if settings_page and hasattr(settings_page, "sync_scale_control"):
            try: settings_page.sync_scale_control()
            except Exception: pass

    # ---------- project/state ----------
    def ensure_project(self,artist:str="",song:str=""):
        artist = normalize_project_text(artist)
        song = normalize_project_text(song)
        if self.doc.state.project_dir:
            self.doc.config.artist = normalize_project_text(self.doc.config.artist or artist)
            self.doc.config.song = normalize_project_text(self.doc.config.song or song)
            return
        self.project_manager=ProjectManager(Path(self.settings.project_root))
        self.doc=self.project_manager.create(artist,song)
        self.doc.config.separator_mode=self.settings.default_separator;self.doc.config.final_separator="B" if self.settings.default_separator=="B" else "A";self.doc.config.vocal_formants=self.settings.vocal_formants;self.doc.config.output_format=self.settings.default_output_format
        self.save_project();self._attach_project_log();self.refresh_header();self._sync_close_project_button()

    def save_project(self):
        if self.doc.state.project_dir:
            self.project_manager.save(self.doc)
        self.refresh_navigation()

    def load_project(self,manifest:Path):
        if self.doc.state.project_dir:
            self._detach_project_log(self.doc.state.project_dir)
        self.doc=self.project_manager.load(manifest)
        self.project_manager=ProjectManager(Path(self.settings.project_root))
        self._system_health = system_health(self.doc.config.separator_mode)
        self._attach_project_log();self.refresh_header();self.refresh_navigation()
        for page in self.pages.values():
            try:page.sync_from_doc()
            except Exception:pass
        try:self.pages["separation"].refresh_previews()
        except Exception:pass
        try:self.pages["tuning"].show_analysis(self.doc.state.tuning_analysis);self.pages["tuning"].recompute()
        except Exception:pass
        self.logger.info("Projeto carregado.")
        self._sync_close_project_button()

    def _sync_close_project_button(self):
        btn = getattr(self, "close_project_btn", None)
        if not btn:
            return
        has_project = bool(getattr(self.doc.state, "project_dir", ""))
        try:
            if has_project:
                btn.configure(
                    text="Fechar projeto",
                    state="normal" if not self.tasks.busy else "disabled",
                )
            else:
                # No estado inicial o mesmo espaço vira a ação natural de saída.
                # Se houver uma tarefa sem projeto, on_close() ainda faz a
                # confirmação padrão antes de encerrar.
                btn.configure(text="Sair", state="normal")
        except Exception:
            pass

    def _detach_project_log(self, project_dir: str | Path | None = None):
        if not project_dir:
            return
        target = str(Path(project_dir) / "logs" / "workflow.log")
        for handler in list(self.logger.handlers):
            if isinstance(handler, logging.FileHandler) and getattr(handler, "baseFilename", "") == target:
                try:
                    self.logger.removeHandler(handler)
                    handler.close()
                except Exception:
                    pass

    def close_project(self):
        if not self.doc.state.project_dir:
            self.on_close()
            return
        if self.tasks.busy:
            messagebox.showwarning(APP_NAME, "Há uma tarefa em andamento. Cancele-a na página atual antes de fechar o projeto.")
            return
        old_dir = self.doc.state.project_dir
        try:
            self.save_project()
        except Exception:
            pass
        self._detach_project_log(old_dir)
        self.doc = ProjectDocument(config=WorkflowConfig(
            separator_mode=self.settings.default_separator,
            final_separator="B" if self.settings.default_separator == "B" else "A",
            vocal_formants=self.settings.vocal_formants,
            output_format=self.settings.default_output_format,
        ))
        self._system_health = system_health(self.doc.config.separator_mode)
        # Destrói somente páginas ligadas ao projeto para eliminar qualquer estado visual residual.
        for key in ("source", "separation", "tuning", "export"):
            page = self.pages.pop(key, None)
            if page is not None:
                try:
                    page.destroy()
                except Exception:
                    pass
        self.logger.info("Projeto fechado.")
        self.refresh_header()
        self.refresh_navigation()
        self._sync_close_project_button()
        self.navigate("source")

    def _attach_project_log(self):
        if not self.doc.state.project_dir:return
        # Adiciona um FileHandler de projeto sem remover console/GUI/app.log.
        p=Path(self.doc.state.project_dir)/"logs"/"workflow.log"
        for h in self.logger.handlers:
            if isinstance(h,logging.FileHandler) and getattr(h,"baseFilename","")==str(p):return
        p.parent.mkdir(parents=True,exist_ok=True);fh=logging.FileHandler(p,encoding="utf-8");fh.setLevel(logging.DEBUG);fh.setFormatter(logging.Formatter("%(asctime)s %(levelname)-8s %(message)s","%Y-%m-%d %H:%M:%S"));self.logger.addHandler(fh)

    def refresh_header(self):
        cfg, st = self.doc.config, self.doc.state
        if self.current_page == "file_pitch":
            self.project_label.configure(text="Pitch de Arquivo")
            self.detail_label.configure(text="Ferramenta independente • funciona com ou sem projeto aberto.")
        elif not st.project_dir:
            self.project_label.configure(text="Nenhum projeto aberto")
            self.detail_label.configure(text="Comece pela Fonte ou abra um projeto.")
        else:
            title = " — ".join(x for x in (cfg.artist, cfg.song) if x) or "Projeto"
            self.project_label.configure(text=title)
            flow_text = header_workflow_text(self.doc)
            tuning = (f"{cfg.original_tuning or '?'} → {cfg.target_tuning or '?'}"
                      if cfg.pitch_mode == "tuning" else f"Pitch {cfg.semitones:+d} st")
            self.detail_label.configure(text=f"{flow_text}  •  {tuning}")
        self._sync_close_project_button()
        self.refresh_navigation()
        if not self.tasks.busy:
            done = completed_count(self.doc)
            try:
                self.progress.stop()
                self.progress.configure(mode="determinate")
                self.progress.set(0 if self.current_page == "file_pitch" else done / 4.0)
            except Exception:
                pass

    def save_settings_and_refresh(self):
        save_settings(self.settings);self.project_manager=ProjectManager(Path(self.settings.project_root));self.logger.info("Configurações salvas")

    # ---------- status/progress ----------
    def set_busy(self,message:str):
        self.status.set("Processando","accent");self.detail_label.configure(text=message)
        try:self.close_project_btn.configure(state="disabled")
        except Exception:pass
        try:self.progress.configure(mode="indeterminate");self.progress.start()
        except Exception:pass

    def update_progress(self,fraction:float|None,message:str):
        self.detail_label.configure(text=message)
        if fraction is None:
            try:self.progress.configure(mode="indeterminate");self.progress.start()
            except Exception:pass
        else:
            try:self.progress.stop();self.progress.configure(mode="determinate");self.progress.set(fraction)
            except Exception:pass

    def set_ready(self,message="Pronto"):
        try:self.progress.stop();self.progress.configure(mode="determinate")
        except Exception:pass
        self.status.set(message,"success");self._sync_close_project_button();self.refresh_header()

    def set_error(self,message:str):
        try:self.progress.stop();self.progress.set(0)
        except Exception:pass
        self.status.set("Erro","danger");self.detail_label.configure(text=message);self._sync_close_project_button()

    def cancel_task(self):self.tasks.cancel();self.runner.cancel();self.status.set("Cancelando…","warning")

    # ---------- helpers ----------
    def open_path(self,path:Path):
        try:
            if sys.platform.startswith("linux"):subprocess.Popen(["xdg-open",str(path)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            elif sys.platform=="darwin":subprocess.Popen(["open",str(path)])
            elif os.name=="nt":os.startfile(str(path))
        except Exception as exc:self.logger.error("Não foi possível abrir %s: %s",path,exc)

    def launch_installer(self):
        script=Path(__file__).resolve().parents[2]/"install.sh"
        terminals=[("x-terminal-emulator",["x-terminal-emulator","-e","bash",str(script)]),("gnome-terminal",["gnome-terminal","--","bash",str(script)]),("konsole",["konsole","-e","bash",str(script)]),("xfce4-terminal",["xfce4-terminal","-e",f"bash {script}"])]
        for exe,args in terminals:
            if shutil.which(exe):subprocess.Popen(args);return
        messagebox.showinfo(APP_NAME,f"Abra um terminal nesta pasta e execute:\n\n./install.sh\n\n{script}")

    def _poll_logs(self):
        self._log_poll_after_id = None
        try:
            while True:
                line=self.log_queue.get_nowait()
                self._session_log_buffer.append(line)
                page=self.pages.get("logs")
                if page:page.append(line)
        except queue.Empty:
            pass
        try:
            if self.winfo_exists():
                self._log_poll_after_id = self.after(100, self._poll_logs)
        except Exception:
            self._log_poll_after_id = None

    def on_close(self):
        try:save_settings(self.settings);self.save_project()
        except Exception:pass
        if self.tasks.busy:
            if not messagebox.askyesno(APP_NAME,"Há uma tarefa em andamento. Cancelar e fechar?"):return
            self.cancel_task()
        self.destroy()
