from __future__ import annotations

"""CustomTkinter compatibility layer.

Production uses real CustomTkinter. A small ttk fallback exists so diagnostics and
core tests can still run on systems where installation was interrupted.
"""

try:
    import customtkinter as ctk  # type: ignore
    USING_CUSTOMTKINTER = True
except Exception:
    USING_CUSTOMTKINTER = False
    import tkinter as tk
    from tkinter import ttk

    def _clean(kwargs):
        ignored = {"fg_color","bg_color","hover_color","text_color","border_color","border_width","corner_radius","font","placeholder_text","progress_color","button_color","button_hover_color","dropdown_fg_color","dropdown_hover_color","dropdown_text_color","label_text","label_fg_color","orientation","dynamic_resizing","anchor"}
        out={k:v for k,v in kwargs.items() if k not in ignored}
        if "width" in out and isinstance(out["width"], int) and out["width"]>100: out.pop("width")
        if "height" in out and isinstance(out["height"], int): out.pop("height")
        return out

    class _CTk(tk.Tk): pass
    class _Frame(ttk.Frame):
        def __init__(self, master=None, **kwargs): super().__init__(master, **_clean(kwargs))
        def configure(self, cnf=None, **kwargs): return super().configure(cnf, **_clean(kwargs)) if cnf is not None else super().configure(**_clean(kwargs))
        config=configure
    class _Label(ttk.Label):
        def __init__(self, master=None, **kwargs):
            font=kwargs.pop("font",None); super().__init__(master, **_clean(kwargs));
            if font:
                try:super().configure(font=font)
                except Exception:pass
        def configure(self, cnf=None, **kwargs):
            kwargs=_clean(kwargs); return super().configure(cnf, **kwargs) if cnf is not None else super().configure(**kwargs)
        config=configure
    class _Button(ttk.Button):
        def __init__(self, master=None, **kwargs): super().__init__(master, **_clean(kwargs))
        def configure(self, cnf=None, **kwargs): return super().configure(cnf, **_clean(kwargs)) if cnf is not None else super().configure(**_clean(kwargs))
        config=configure
    class _Entry(ttk.Entry):
        def __init__(self, master=None, **kwargs): super().__init__(master, **_clean(kwargs))
    class _Textbox(tk.Text):
        def __init__(self, master=None, **kwargs):
            kwargs=_clean(kwargs); kwargs.setdefault("wrap","word"); super().__init__(master, **kwargs)
    class _Progress(ttk.Progressbar):
        def __init__(self, master=None, mode="determinate", **kwargs): super().__init__(master, mode=mode, maximum=1.0, **_clean(kwargs))
        def set(self, value): self["value"]=max(0,min(1,float(value)))
        def start(self): super().start(12)
    class _Combo(ttk.Combobox):
        def __init__(self, master=None, values=(), variable=None, command=None, **kwargs):
            self._command=command
            if variable is not None: kwargs["textvariable"]=variable
            kwargs["values"]=values
            super().__init__(master, **_clean(kwargs)); self.bind("<<ComboboxSelected>>", self._selected)
        def _selected(self,_e=None):
            if self._command:self._command(self.get())
        def set(self,value): super().set(value)
    class _Option(_Combo):
        def __init__(self, master=None, values=(), variable=None, command=None, **kwargs): super().__init__(master,values,variable,command,state="readonly",**kwargs)
    class _Radio(ttk.Radiobutton):
        def __init__(self, master=None, **kwargs): super().__init__(master, **_clean(kwargs))
    class _Check(ttk.Checkbutton):
        def __init__(self, master=None, **kwargs): super().__init__(master, **_clean(kwargs))
    class _Switch(_Check): pass
    class _Scrollable(_Frame):
        # Fallback keeps API and uses natural page scrolling at root level tests.
        pass
    class _Font:
        def __new__(cls, family=None, size=12, weight="normal", **kwargs): return (family or "TkDefaultFont", size, weight)
    class _NS:
        CTk=_CTk; CTkFrame=_Frame; CTkScrollableFrame=_Scrollable; CTkLabel=_Label; CTkButton=_Button
        CTkEntry=_Entry; CTkTextbox=_Textbox; CTkProgressBar=_Progress; CTkComboBox=_Combo; CTkOptionMenu=_Option
        CTkRadioButton=_Radio; CTkCheckBox=_Check; CTkSwitch=_Switch; CTkFont=_Font
        StringVar=tk.StringVar; BooleanVar=tk.BooleanVar; IntVar=tk.IntVar; DoubleVar=tk.DoubleVar
        END=tk.END
        @staticmethod
        def set_appearance_mode(_mode): pass
        @staticmethod
        def get_appearance_mode(): return "System"
        @staticmethod
        def set_default_color_theme(_theme): pass
        @staticmethod
        def set_widget_scaling(_scale): pass
        @staticmethod
        def set_window_scaling(_scale): pass
    ctk=_NS()
