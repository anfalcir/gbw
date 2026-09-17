from __future__ import annotations
import webbrowser
from pathlib import Path
from tkinter import filedialog
from gbw.ui.ctk_compat import ctk
from gbw.ui.widgets import PageTitle,Card,InlineMessage,SourceCandidateCard
from gbw.ui.theme import COLORS
from gbw.project import normalize_project_text

class SourcePage(ctk.CTkScrollableFrame):
    page_key="source"
    def __init__(self,master,app):
        super().__init__(master,fg_color="transparent",corner_radius=0)
        self.app=app;self.grid_columnconfigure(0,weight=1)
        self.candidates=[]
        self.results_page=0
        self.results_page_size=3
        self.recommended_url=""
        PageTitle(self,"Fonte","Escolha a fonte da música.").grid(row=0,column=0,sticky="ew",pady=(0,14))
        form=Card(self,"Música","Informe artista e música.")
        form.grid(row=1,column=0,sticky="ew",pady=6);form.body.grid_columnconfigure(1,weight=1)
        ctk.CTkLabel(form.body,text="Artista",anchor="w").grid(row=0,column=0,sticky="w",pady=5)
        self.artist=ctk.CTkEntry(form.body,placeholder_text="Ex.: Wolves At The Gate");self.artist.grid(row=0,column=1,sticky="ew",padx=(12,0),pady=5)
        ctk.CTkLabel(form.body,text="Música",anchor="w").grid(row=1,column=0,sticky="w",pady=5)
        self.song=ctk.CTkEntry(form.body,placeholder_text="Ex.: Enemy");self.song.grid(row=1,column=1,sticky="ew",padx=(12,0),pady=5)
        action=ctk.CTkFrame(form.body,fg_color="transparent");action.grid(row=2,column=0,columnspan=2,sticky="ew",pady=(10,0))
        self.depth=ctk.StringVar(value=self.app.settings.search_depth)
        ctk.CTkOptionMenu(action,values=["Robusta","Máxima"],variable=self.depth,width=110).pack(side="left")
        self.search_btn=ctk.CTkButton(action,text="Encontrar melhor fonte",command=self.search);self.search_btn.pack(side="left",padx=8)
        self.cancel_search_btn=ctk.CTkButton(action,text="Cancelar busca",state="disabled",fg_color=COLORS["danger"],hover_color=COLORS["danger"],command=self.cancel_search);self.cancel_search_btn.pack(side="left")
        ctk.CTkButton(action,text="Arquivo local…",fg_color="transparent",border_width=1,command=self.pick_local).pack(side="left",padx=(8,0))

        self.results=Card(self,"Fontes encontradas","3 resultados por página.")
        self.results.grid(row=2,column=0,sticky="ew",pady=6)
        self.selection_status=InlineMessage(self.results.body);self.selection_status.grid(row=0,column=0,sticky="ew",pady=(0,6))
        self.result_frame=ctk.CTkFrame(self.results.body,fg_color="transparent");self.result_frame.grid(row=1,column=0,sticky="ew");self.result_frame.grid_columnconfigure(0,weight=1)
        self.pager=ctk.CTkFrame(self.results.body,fg_color="transparent");self.pager.grid(row=2,column=0,sticky="ew",pady=(10,0));self.pager.grid_columnconfigure(1,weight=1)
        self.prev_btn=ctk.CTkButton(self.pager,text="← Anterior",width=120,fg_color="transparent",border_width=1,command=lambda:self.change_results_page(-1));self.prev_btn.grid(row=0,column=0,sticky="w")
        self.page_label=ctk.CTkLabel(self.pager,text="Nenhuma pesquisa",text_color=COLORS["muted"]);self.page_label.grid(row=0,column=1)
        self.next_btn=ctk.CTkButton(self.pager,text="Próxima →",width=120,fg_color="transparent",border_width=1,command=lambda:self.change_results_page(1));self.next_btn.grid(row=0,column=2,sticky="e")
        self.msg=InlineMessage(self);self.msg.grid(row=3,column=0,sticky="ew",pady=8)

        manual=Card(self,"URL manual","Cole um link, se preferir.")
        manual.grid(row=4,column=0,sticky="ew",pady=6);manual.body.grid_columnconfigure(0,weight=1)
        self.url=ctk.CTkEntry(manual.body,placeholder_text="https://…");self.url.grid(row=0,column=0,sticky="ew")
        ctk.CTkButton(manual.body,text="Usar URL",width=100,command=self.use_url).grid(row=0,column=1,padx=(8,0))

        prep=Card(self,"Preparar fonte","Cria a cópia de trabalho da música.")
        prep.grid(row=5,column=0,sticky="ew",pady=6)
        prep_actions=ctk.CTkFrame(prep.body,fg_color="transparent");prep_actions.grid(row=0,column=0,sticky="w")
        self.prepare_btn=ctk.CTkButton(prep_actions,text="Preparar fonte selecionada",command=self.prepare);self.prepare_btn.pack(side="left")
        self.cancel_prepare_btn=ctk.CTkButton(prep_actions,text="Cancelar preparação",state="disabled",fg_color=COLORS["danger"],hover_color=COLORS["danger"],command=self.cancel_prepare);self.cancel_prepare_btn.pack(side="left",padx=(8,0))
        self.sync_from_doc()

    def sync_from_doc(self):
        cfg=self.app.doc.config
        try:self.artist.delete(0,"end");self.artist.insert(0,cfg.artist)
        except Exception:pass
        try:self.song.delete(0,"end");self.song.insert(0,cfg.song)
        except Exception:pass

    def _normalized_identity(self):
        artist = normalize_project_text(self.artist.get())
        song = normalize_project_text(self.song.get())
        # Reflete imediatamente na tela o mesmo padrão usado no manifesto.
        try:
            if self.artist.get() != artist:
                self.artist.delete(0, "end"); self.artist.insert(0, artist)
            if self.song.get() != song:
                self.song.delete(0, "end"); self.song.insert(0, song)
        except Exception:
            pass
        return artist, song

    def cancel_search(self):
        if self.app.tasks.busy:
            self.cancel_search_btn.configure(state="disabled", text="Cancelando…")
            self.app.cancel_task()
            self.msg.set("Cancelamento solicitado.", "warning")

    def cancel_prepare(self):
        if self.app.tasks.busy:
            self.cancel_prepare_btn.configure(state="disabled", text="Cancelando…")
            self.app.cancel_task()
            self.msg.set("Cancelamento solicitado.", "warning")

    def search(self):
        artist, song = self._normalized_identity()
        if not song:self.msg.set("Informe o nome da música.","danger");return
        self.app.ensure_project(artist,song);self.app.doc.config.artist=artist;self.app.doc.config.song=song
        self.search_btn.configure(state="disabled");self.cancel_search_btn.configure(state="normal",text="Cancelar busca");self.msg.set("Pesquisando fontes…","accent");self.app.set_busy("Pesquisando fontes…")
        def work():return self.app.source_service.search(artist,song,self.depth.get())
        def ok(items):
            self.search_btn.configure(state="normal");self.cancel_search_btn.configure(state="disabled",text="Cancelar busca");self.show_candidates(items);self.app.set_ready()
        def err(exc):
            self.search_btn.configure(state="normal");self.cancel_search_btn.configure(state="disabled",text="Cancelar busca")
            if "cancelad" in str(exc).lower():
                self.msg.set("Busca cancelada.","warning");self.app.set_ready("Busca cancelada")
            else:
                self.msg.set(f"Falha na pesquisa: {exc}","danger");self.app.set_error(str(exc))
        if not self.app.tasks.submit("pesquisa de fontes",work,ok,err):
            self.search_btn.configure(state="normal");self.cancel_search_btn.configure(state="disabled");self.msg.set("Há outra tarefa em andamento.","warning")

    def show_candidates(self,items):
        self.candidates=list(items or [])
        self.results_page=0
        self.recommended_url=""
        if not self.candidates:
            self._render_results_page()
            self.selection_status.clear()
            self.msg.set("Nenhum candidato automático confiável. Use arquivo local ou URL manual.","warning")
            return
        safe=[c for c in self.candidates if not getattr(c,"preview_only",False)]
        recommended=safe[0] if safe else None
        if recommended is None:
            cfg=self.app.doc.config;cfg.selected_candidate=None;cfg.source_url=""
            self.app.save_project()
            self._render_results_page()
            self.selection_status.set("Nenhuma fonte completa selecionada.","danger")
            self.msg.set("Foram encontrados apenas trechos curtos. Escolha outra fonte.","danger")
            return
        self.recommended_url=recommended.url
        # Pré-seleciona o recomendado somente quando não há uma seleção segura atual entre os resultados.
        cfg=self.app.doc.config
        selected_url=str((cfg.selected_candidate or {}).get("url") or "")
        safe_urls={c.url for c in safe}
        if selected_url not in safe_urls:
            self.use_candidate(recommended,silent=True,rerender=False)
        selected_url=str((cfg.selected_candidate or {}).get("url") or "")
        selected_index=next((i for i,c in enumerate(self.candidates) if c.url==selected_url),0)
        self.results_page=selected_index//self.results_page_size
        self._render_results_page()
        rejected=sum(1 for c in self.candidates if getattr(c,"preview_only",False))
        suffix=f"; {rejected} resultado(s) descartado(s)" if rejected else ""
        self.msg.set(f"{len(self.candidates)} resultado(s) encontrado(s){suffix}.","success")

    def _selected_url(self):
        return str((self.app.doc.config.selected_candidate or {}).get("url") or "")

    def _render_results_page(self):
        for w in self.result_frame.winfo_children():w.destroy()
        total=len(self.candidates)
        pages=max(1,(total+self.results_page_size-1)//self.results_page_size)
        self.results_page=max(0,min(self.results_page,pages-1))
        start=self.results_page*self.results_page_size
        shown=self.candidates[start:start+self.results_page_size]
        selected_url=self._selected_url()
        for i,c in enumerate(shown):
            SourceCandidateCard(
                self.result_frame,c,self.use_candidate,lambda x:webbrowser.open(x.url),
                selected=(c.url==selected_url),recommended=(c.url==self.recommended_url)
            ).grid(row=i,column=0,sticky="ew",pady=5)
        if not shown:
            ctk.CTkLabel(self.result_frame,text="Nenhum resultado para exibir.",text_color=COLORS["muted"]).grid(row=0,column=0,sticky="w",pady=10)
        if total:
            first=start+1;last=min(start+self.results_page_size,total)
            self.page_label.configure(text=f"Página {self.results_page+1} de {pages}  •  resultados {first}–{last} de {total}")
        else:
            self.page_label.configure(text="Nenhuma pesquisa")
        self.prev_btn.configure(state="normal" if self.results_page>0 else "disabled")
        self.next_btn.configure(state="normal" if self.results_page<pages-1 and total else "disabled")
        if selected_url:
            selected=next((c for c in self.candidates if c.url==selected_url),None)
            if selected:
                self.selection_status.set(f"✓ Fonte ativa: {selected.source} — {selected.title}","success")
            else:
                self.selection_status.set("✓ Uma fonte já está selecionada para este projeto.","success")
        else:
            self.selection_status.set("Nenhuma fonte selecionada.","warning")

    def change_results_page(self,delta:int):
        if not self.candidates:return
        pages=max(1,(len(self.candidates)+self.results_page_size-1)//self.results_page_size)
        new_page=max(0,min(pages-1,self.results_page+delta))
        if new_page!=self.results_page:
            self.results_page=new_page
            self._render_results_page()

    def use_candidate(self,candidate,silent=False,rerender=True):
        if getattr(candidate,"preview_only",False):
            self.msg.set("Esse resultado é apenas um trecho curto e não pode ser usado.","danger")
            return
        cfg=self.app.doc.config;cfg.source_mode="auto";cfg.selected_candidate=candidate.__dict__.copy();cfg.source_url=candidate.url
        self.app.save_project()
        self.selection_status.set(f"✓ Fonte ativa: {candidate.source} — {candidate.title}","success")
        if rerender:self._render_results_page()
        if not silent:self.msg.set(f"Fonte selecionada: {candidate.source}.","success")

    def pick_local(self):
        f=filedialog.askopenfilename(title="Selecione o arquivo fonte",filetypes=[("Áudio/vídeo","*.wav *.flac *.m4a *.mp3 *.ogg *.opus *.webm *.aac *.aiff *.aif *.mka *.mp4 *.mov"),("Todos","*.*")])
        if f:
            artist, song = self._normalized_identity(); self.app.ensure_project(artist, song); cfg=self.app.doc.config; cfg.artist=artist; cfg.song=song; cfg.source_mode="local"; cfg.local_file=f; cfg.selected_candidate=None; self.app.save_project(); self.msg.set(f"Arquivo local selecionado: {Path(f).name}","success")

    def use_url(self):
        url=self.url.get().strip()
        if not (url.startswith("http://") or url.startswith("https://")):self.msg.set("Informe uma URL válida iniciando com http:// ou https://","danger");return
        artist, song = self._normalized_identity(); self.app.ensure_project(artist, song); cfg=self.app.doc.config; cfg.artist=artist; cfg.song=song; cfg.source_mode="url"; cfg.source_url=url; cfg.selected_candidate=None; self.app.save_project(); self.msg.set("URL manual selecionada.","success")

    def prepare(self):
        cfg=self.app.doc.config
        if not self.app.doc.state.project_dir:self.msg.set("Crie/selecione a música primeiro.","danger");return
        if cfg.source_mode=="local" and not cfg.local_file:self.msg.set("Selecione um arquivo local.","danger");return
        if cfg.source_mode in ("auto","url") and not cfg.source_url:self.msg.set("Selecione uma fonte automática ou informe uma URL.","danger");return
        self.prepare_btn.configure(state="disabled");self.cancel_prepare_btn.configure(state="normal",text="Cancelar preparação");self.app.set_busy("Preparando fonte…")
        def progress(frac,msg):self.app.tasks.call_ui(self.app.update_progress,frac,msg)
        def work():return self.app.audio_service.prepare_source(self.app.doc,progress)
        def ok(doc):
            self.app.doc=doc;self.app.save_project();self.prepare_btn.configure(state="normal");self.cancel_prepare_btn.configure(state="disabled",text="Cancelar preparação");self.msg.set("Fonte preparada com sucesso.","success");self.app.set_ready("Fonte pronta");self.app.navigate("separation")
        def err(exc):
            self.prepare_btn.configure(state="normal");self.cancel_prepare_btn.configure(state="disabled",text="Cancelar preparação")
            if "cancelad" in str(exc).lower():
                self.msg.set("Preparação cancelada.","warning");self.app.set_ready("Preparação cancelada")
            else:
                self.msg.set(str(exc),"danger");self.app.set_error(str(exc))
        if not self.app.tasks.submit("preparar fonte",work,ok,err):
            self.prepare_btn.configure(state="normal");self.cancel_prepare_btn.configure(state="disabled");self.msg.set("Há outra tarefa em andamento.","warning")

