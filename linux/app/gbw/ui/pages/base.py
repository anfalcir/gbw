from __future__ import annotations
from gbw.ui.ctk_compat import ctk

class BasePage(ctk.CTkScrollableFrame):
    page_key="base"
    def __init__(self,master,app):
        super().__init__(master,fg_color="transparent",corner_radius=0)
        self.app=app
        self.grid_columnconfigure(0,weight=1)
    def on_show(self):
        pass
