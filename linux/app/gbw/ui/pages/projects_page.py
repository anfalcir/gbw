from __future__ import annotations

import json
import unicodedata
from pathlib import Path
from tkinter import filedialog, messagebox

from gbw.ui.ctk_compat import ctk
from gbw.ui.widgets import PageTitle, Card, InlineMessage, ResponsiveLabel
from gbw.ui.theme import COLORS
from gbw.project import normalize_project_text


def _fold_text(value: str) -> str:
    """Texto amigável para ordenação/pesquisa, ignorando caixa e acentos."""
    text = unicodedata.normalize("NFKD", str(value or ""))
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return " ".join(text.casefold().split())


class ProjectsPage(ctk.CTkScrollableFrame):
    page_key = "projects"

    def __init__(self, master, app):
        super().__init__(master, fg_color="transparent", corner_radius=0)
        self.app = app
        self.grid_columnconfigure(0, weight=1)
        self._catalog = []
        PageTitle(self, "Projetos", "Abra, restaure ou exclua projetos.").grid(
            row=0, column=0, sticky="ew", pady=(0, 14)
        )

        actions = ctk.CTkFrame(self, fg_color="transparent")
        actions.grid(row=1, column=0, sticky="w", pady=(0, 8))
        self.restore_btn = ctk.CTkButton(actions, text="Restaurar backup…", command=self.restore_backup)
        self.restore_btn.pack(side="left")
        self.cancel_restore_btn = ctk.CTkButton(
            actions, text="Cancelar restauração", state="disabled",
            fg_color=COLORS["danger"], hover_color=COLORS["danger"], command=self.cancel_restore
        )
        self.cancel_restore_btn.pack(side="left", padx=(8, 0))
        ctk.CTkButton(
            actions, text="Abrir pasta de projetos", fg_color="transparent", border_width=1,
            command=lambda: self.app.open_path(self.app.project_manager.root)
        ).pack(side="left", padx=(8, 0))

        # Pesquisa local e instantânea. O catálogo é lido apenas em refresh();
        # cada tecla filtra a lista já carregada em memória.
        search = ctk.CTkFrame(self, fg_color=COLORS["card"], corner_radius=10,
                              border_width=1, border_color=COLORS["border"])
        search.grid(row=2, column=0, sticky="ew", pady=(2, 10))
        search.grid_columnconfigure(1, weight=1)
        ctk.CTkLabel(search, text="Pesquisar", font=ctk.CTkFont(weight="bold")).grid(
            row=0, column=0, sticky="w", padx=(14, 10), pady=12
        )
        self.search_var = ctk.StringVar(value="")
        self.search_entry = ctk.CTkEntry(
            search, textvariable=self.search_var, placeholder_text="Digite banda ou música…"
        )
        self.search_entry.grid(row=0, column=1, sticky="ew", pady=10)
        self.count_label = ctk.CTkLabel(search, text="", text_color=COLORS["muted"])
        self.count_label.grid(row=0, column=2, sticky="e", padx=(12, 14), pady=12)
        self._search_trace = self.search_var.trace_add("write", self._on_search_changed)

        self.container = ctk.CTkFrame(self, fg_color="transparent")
        self.container.grid(row=3, column=0, sticky="ew")
        self.container.grid_columnconfigure(0, weight=1)
        self.msg = InlineMessage(self)
        self.msg.grid(row=4, column=0, sticky="ew", pady=8)
        self.refresh()

    def on_show(self):
        self.refresh()

    def _load_catalog(self):
        items = []
        for p in self.app.project_manager.list_projects():
            artist = ""
            song = ""
            try:
                d = json.loads(p.read_text(encoding="utf-8"))
                cfg = d.get("config", {}) or {}
                st = d.get("state", {}) or {}
                artist = normalize_project_text(cfg.get("artist", ""))
                song = normalize_project_text(cfg.get("song", ""))
                stage_map = {
                    "new": "Novo", "source_prepared": "Fonte pronta",
                    "separated": "Separado", "finished": "Concluído"
                }
                sub = (
                    f"Status: {stage_map.get(st.get('stage', '?'), st.get('stage', '?'))}"
                    f" | Pitch: {int(cfg.get('semitones', 0)):+d} st"
                )
            except Exception:
                artist = ""
                song = p.parent.name
                sub = "Projeto inválido"

            display_artist = artist or "Sem banda/artista"
            display_song = song or p.parent.name
            items.append({
                "manifest": p,
                "artist": display_artist,
                "song": display_song,
                "sub": sub,
                "search": _fold_text(f"{artist} {song} {p.parent.name}"),
                "sort": (_fold_text(display_artist), _fold_text(display_song), _fold_text(p.parent.name)),
            })
        return sorted(items, key=lambda item: item["sort"])

    def _on_search_changed(self, *_args):
        self._render_catalog()

    def _filtered_catalog(self):
        query = _fold_text(self.search_var.get())
        if not query:
            return list(self._catalog)
        terms = [term for term in query.split() if term]
        return [item for item in self._catalog if all(term in item["search"] for term in terms)]

    def refresh(self):
        self._catalog = self._load_catalog()
        self._render_catalog()

    def _render_catalog(self):
        for w in self.container.winfo_children():
            w.destroy()

        items = self._filtered_catalog()
        total = len(self._catalog)
        shown = len(items)
        if self.search_var.get().strip():
            self.count_label.configure(text=f"{shown} de {total}")
        else:
            self.count_label.configure(text=f"{total} projeto{'s' if total != 1 else ''}")

        if not self._catalog:
            ctk.CTkLabel(
                self.container, text="Nenhum projeto salvo ainda.", text_color=COLORS["muted"]
            ).grid(row=0, column=0, sticky="w")
            return
        if not items:
            ctk.CTkLabel(
                self.container, text="Nenhum projeto corresponde à pesquisa.", text_color=COLORS["muted"]
            ).grid(row=0, column=0, sticky="w", pady=10)
            return

        row = 0
        current_artist_key = None
        for item in items:
            artist = item["artist"]
            song = item["song"]
            manifest = item["manifest"]
            artist_key = _fold_text(artist)
            if artist_key != current_artist_key:
                if row:
                    row += 1
                header = ctk.CTkFrame(self.container, fg_color="transparent")
                header.grid(row=row, column=0, sticky="ew", pady=(8, 3))
                header.grid_columnconfigure(0, weight=1)
                ResponsiveLabel(
                    header, text=artist, font=ctk.CTkFont(size=17, weight="bold"),
                    text_color=COLORS["accent"], max_wrap=None
                ).grid(row=0, column=0, sticky="ew")
                row += 1
                current_artist_key = artist_key

            card = Card(self.container, song, item["sub"])
            card.grid(row=row, column=0, sticky="ew", pady=5)
            ctk.CTkButton(
                card.body, text="Continuar projeto", command=lambda q=manifest: self.open_project(q)
            ).pack(side="left")
            ctk.CTkButton(
                card.body, text="Abrir pasta", fg_color="transparent", border_width=1,
                command=lambda q=manifest: self.app.open_path(q.parent)
            ).pack(side="left", padx=8)
            ctk.CTkButton(
                card.body, text="Excluir", fg_color=COLORS["danger"], hover_color=COLORS["danger"],
                command=lambda q=manifest, name=f"{artist} — {song}": self.delete_project(q, name)
            ).pack(side="left")
            row += 1

    def open_project(self, path):
        try:
            self.app.load_project(path)
            self.msg.set("Projeto carregado.", "success")
            self.app.navigate("source")
        except Exception as exc:
            self.msg.set(f"Falha ao carregar: {exc}", "danger")

    def delete_project(self, manifest: Path, title: str):
        if self.app.tasks.busy:
            self.msg.set("Cancele a tarefa em andamento antes de excluir um projeto.", "warning")
            return
        if not messagebox.askyesno(
            "Excluir projeto",
            f"Excluir permanentemente o projeto\n\n{title}\n\ne todos os arquivos dele?",
            parent=self,
        ):
            return
        try:
            is_current = bool(
                self.app.doc.state.project_dir
                and Path(self.app.doc.state.project_dir).resolve() == manifest.parent.resolve()
            )
            if is_current:
                self.app.close_project()
            deleted = self.app.project_manager.delete_project(manifest)
            self.app.logger.info("Projeto excluído: %s", deleted.name)
            self.msg.set("Projeto excluído.", "success")
            self.refresh()
        except Exception as exc:
            self.msg.set(f"Não foi possível excluir: {exc}", "danger")

    def cancel_restore(self):
        if self.app.tasks.busy:
            self.cancel_restore_btn.configure(state="disabled", text="Cancelando…")
            self.app.cancel_task()
            self.msg.set("Cancelamento solicitado.", "warning")

    def restore_backup(self):
        if self.app.tasks.busy:
            self.msg.set("Aguarde ou cancele a tarefa atual antes de restaurar um backup.", "warning")
            return
        backup = filedialog.askopenfilename(
            title="Restaurar backup de projeto",
            filetypes=[("Backup GBW", "*.zip"), ("Todos os arquivos", "*.*")],
        )
        if not backup:
            return

        self.restore_btn.configure(state="disabled")
        self.cancel_restore_btn.configure(state="normal", text="Cancelar restauração")
        self.app.set_busy("Restaurando backup…")

        def work():
            return self.app.backup_service.restore_project(
                Path(backup), self.app.project_manager, self.app.tasks.cancel_event
            )

        def ok(manifest):
            self.restore_btn.configure(state="normal")
            self.cancel_restore_btn.configure(state="disabled", text="Cancelar restauração")
            self.msg.set("Backup restaurado. O projeto está pronto para ser aberto.", "success")
            self.refresh()
            self.app.set_ready("Backup restaurado")
            if messagebox.askyesno("Backup restaurado", "Deseja abrir o projeto restaurado agora?", parent=self):
                self.open_project(manifest)

        def err(exc):
            self.restore_btn.configure(state="normal")
            self.cancel_restore_btn.configure(state="disabled", text="Cancelar restauração")
            if "cancelad" in str(exc).lower():
                self.msg.set("Restauração cancelada.", "warning")
                self.app.set_ready("Restauração cancelada")
            else:
                self.msg.set(f"Falha ao restaurar backup: {exc}", "danger")
                self.app.set_error(str(exc))

        if not self.app.tasks.submit("restauração de projeto", work, ok, err):
            self.restore_btn.configure(state="normal")
            self.cancel_restore_btn.configure(state="disabled")
            self.msg.set("Há outra tarefa em andamento.", "warning")
