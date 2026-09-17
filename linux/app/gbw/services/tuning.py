from __future__ import annotations

import html
import json
import logging
import re
import urllib.parse
import urllib.request
from pathlib import Path

from gbw.config import VENV_DIR
from gbw.core.runner import CommandRunner, CommandError
from gbw.models import ProjectDocument, TUNINGS, tuning_notes
from gbw.services.source_search import SourceSearchService

class TuningService:
    def __init__(self, runner: CommandRunner, source_search: SourceSearchService, logger: logging.Logger, helper_path: Path):
        self.runner=runner; self.source_search=source_search; self.logger=logger; self.helper_path=helper_path

    @staticmethod
    def tuning_mentions(text:str)->dict[str,int]:
        t=(text or "").lower().replace("♯","#").replace("♭","b")
        patterns={
            "E Standard":[r"\be\s+standard\b",r"\bstandard\s+e\b"],
            "Eb Standard":[r"\be\s*flat\s+standard\b",r"\beb\s+standard\b",r"\be[- ]?flat\s+tuning\b"],
            "D Standard":[r"\bd\s+standard\b",r"\bstandard\s+d\b"],
            "C# Standard":[r"\bc\s*(?:#|sharp)\s+standard\b",r"\bdb\s+standard\b"],
            "C Standard":[r"\bc\s+standard\b",r"\bstandard\s+c\b"],
            "B Standard":[r"\bb\s+standard\b",r"\bstandard\s+b\b"],
            "Bb Standard":[r"\bb\s*flat\s+standard\b",r"\bbb\s+standard\b",r"\ba#\s+standard\b"],
            "A Standard":[r"\ba\s+standard\b",r"\bstandard\s+a\b"],
            "Drop D":[r"\bdrop\s+d\b"], "Drop C#":[r"\bdrop\s+c\s*(?:#|sharp)(?=\s|/|,|$)",r"\bdrop\s+db\b"],
            "Drop C":[r"\bdrop\s+c\b(?!\s*(?:#|sharp))"], "Drop B":[r"\bdrop\s+b\b(?!b)"],
            "Drop Bb":[r"\bdrop\s+(?:bb|b\s*flat|a#)\b"], "Drop A":[r"\bdrop\s+a\b(?!b|#)"],
            "Drop Ab":[r"\bdrop\s+(?:ab|a\s*flat)\b",r"\bdrop\s+g#(?=\s|/|,|$)"],
            "Drop G":[r"\bdrop\s+g\b(?!b|#)"], "Drop F#":[r"\bdrop\s+(?:f\s*sharp|gb)\b",r"\bdrop\s+f#(?=\s|/|,|$)"],
            "Drop F":[r"\bdrop\s+f\b(?!#)"],
        }
        out={}
        for name,regs in patterns.items():
            n=sum(len(re.findall(rx,t,re.I)) for rx in regs)
            if n: out[name]=n
        return out

    def _online(self, artist:str, song:str)->dict:
        query=" ".join(x for x in (artist,song) if x); counts={}; evidence=[]
        def merge(text,source,url=""):
            for name,n in self.tuning_mentions(text).items():
                counts[name]=counts.get(name,0)+n
                if len(evidence)<20:evidence.append({"tuning":name,"source":source,"url":url,"text":re.sub(r"\s+"," ",text)[:320]})
        try:
            seen=set(); entries=[]
            for q in (f"{query} guitar tuning",f"{query} guitar tab tuning",f"{query} guitar cover tuning"):
                entries.extend(self.source_search._flat_search("ytsearch",q,6))
            inspected=0
            for e in entries:
                if inspected>=7:break
                url=self.source_search._entry_url(e,"YouTube")
                if not url or url in seen:continue
                seen.add(url)
                try:
                    info=self.source_search.inspect_url(url)
                    merge("\n".join([str(info.get("title") or ""),str(info.get("description") or "")," ".join(str(x) for x in (info.get("tags") or []) if x)]),"YouTube",url)
                    inspected+=1
                except Exception as exc:self.logger.debug("Tuning YouTube ignorado: %s",exc)
        except Exception as exc:self.logger.debug("Busca tuning YouTube indisponível: %s",exc)
        try:
            q=urllib.parse.quote_plus(f'"{artist}" "{song}" guitar tuning')
            req=urllib.request.Request("https://html.duckduckgo.com/html/?q="+q,headers={"User-Agent":"Mozilla/5.0 GuitarBackingWizard/5.23"})
            with urllib.request.urlopen(req,timeout=12) as resp:page=resp.read(700_000).decode("utf-8",errors="replace")
            frags=re.findall(r'class="result__snippet"[^>]*>(.*?)</(?:a|div)>',page,re.I|re.S)+re.findall(r'class="result__a"[^>]*>(.*?)</a>',page,re.I|re.S)
            for frag in frags[:20]:merge(html.unescape(re.sub(r"<[^>]+>"," ",frag)),"Busca web")
        except Exception as exc:self.logger.debug("Snippets tuning indisponíveis: %s",exc)
        return {"counts":counts,"evidence":evidence}

    @staticmethod
    def fuse(local:dict,online:dict)->dict:
        guitar=local.get("guitar",{}) if isinstance(local,dict) else {}; locals_=guitar.get("candidates",[]) or []
        counts=online.get("counts",{}) if isinstance(online,dict) else {}; max_count=max([int(v) for v in counts.values()] or [0]); fused=[]
        for c in locals_:
            name=str(c.get("tuning","")); local_score=float(c.get("local_score",0) or 0); n=int(counts.get(name,0) or 0); online_score=100*n/max_count if max_count else 0
            final=.74*local_score+.26*online_score if max_count else local_score
            fused.append({**c,"online_mentions":n,"online_score":round(online_score,1),"final_score":round(final,1)})
        fused.sort(key=lambda x:(float(x.get("final_score",0)),int(x.get("online_mentions",0))),reverse=True)
        local_best=str(guitar.get("best","")); online_best=max(counts,key=lambda k:counts[k]) if counts else ""; conf=int(guitar.get("confidence_local",0) or 0)
        agreement="sem evidência online explícita"
        if online_best:
            if online_best==local_best:conf=min(96,conf+min(16,6+int(counts[online_best])*3));agreement=f"áudio e online concordam em {online_best}"
            else:conf=max(30,conf-18);agreement=f"conflito: áudio favorece {local_best}; online favorece {online_best}"
        if len(fused)>=2:
            margin=float(fused[0]["final_score"])-float(fused[1]["final_score"])
            if margin<6:conf=max(30,conf-12)
            elif margin>20:conf=min(96,conf+4)
        return {**local,"online":online,"fused_candidates":fused,"best_tuning":str(fused[0].get("tuning",local_best)) if fused else local_best,"confidence":conf,"agreement":agreement}

    def analyze(self,doc:ProjectDocument,separator:str)->dict:
        smap=doc.state.stem_maps.get(separator,{})
        guitar=Path(smap.get("guitar", "")); mix=Path(doc.state.prepared_wav)
        if not guitar.exists():raise CommandError("A guitarra separada não está disponível.")
        if not mix.exists():raise CommandError("Fonte preparada não está disponível.")
        py=VENV_DIR/"bin"/"python"
        if not py.exists():
            # Durante desenvolvimento/teste, o próprio Python pode ter librosa.
            import sys; py=Path(sys.executable)
        if not self.helper_path.exists():raise CommandError("A análise de afinação não está disponível.")
        output=self.runner.run([str(py),str(self.helper_path),"--mix",str(mix),"--guitar",str(guitar)],"Análise local de tom e afinação")
        local=None
        for line in reversed(output.splitlines()):
            if line.strip().startswith("{") and line.strip().endswith("}"):
                local=json.loads(line);break
        if not local:raise CommandError("Não foi possível analisar a afinação.")
        result=self.fuse(local,self._online(doc.config.artist,doc.config.song));result["separator"]=separator
        doc.state.tuning_analysis=result;doc.state.tuning_analysis_separator=separator
        Path(doc.state.project_dir,"tuning_analysis.json").write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
        return result
