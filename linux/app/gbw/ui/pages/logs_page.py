from __future__ import annotations
from gbw.ui.ctk_compat import ctk
from gbw.ui.widgets import PageTitle,Card

class LogsPage(ctk.CTkFrame):
    page_key="logs"
    def __init__(self,master,app):
        super().__init__(master,fg_color="transparent",corner_radius=0);self.app=app;self.grid_columnconfigure(0,weight=1);self.grid_rowconfigure(1,weight=1)
        PageTitle(self,"Logs","Histórico da sessão.").grid(row=0,column=0,sticky="ew",pady=(0,14))
        card=Card(self,"Log da sessão");card.grid(row=1,column=0,sticky="nsew");card.body.grid_rowconfigure(0,weight=1);card.body.grid_columnconfigure(0,weight=1)
        self.text=ctk.CTkTextbox(card.body,wrap="word");self.text.grid(row=0,column=0,sticky="nsew")
        actions=ctk.CTkFrame(card.body,fg_color="transparent");actions.grid(row=1,column=0,sticky="w",pady=(8,0))
        ctk.CTkButton(actions,text="Copiar logs",command=self.copy).pack(side="left")
        ctk.CTkButton(actions,text="Limpar",fg_color="transparent",border_width=1,command=self.clear).pack(side="left",padx=8)
    def append(self,line):
        try:self.text.insert("end",line+"\n");self.text.see("end")
        except Exception:pass
    def copy(self):self.app.clipboard_clear();self.app.clipboard_append(self.text.get("1.0","end"))
    def clear(self):self.text.delete("1.0","end")
