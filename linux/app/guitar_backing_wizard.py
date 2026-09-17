#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

from gbw.core.system import diagnostic_report
from gbw.models import tuning_delta
from gbw.ui.main_window import GuitarBackingWizard


def self_test() -> int:
    print("=== Guitar Backing Wizard 5.23 — self-test ===")
    print(diagnostic_report())
    assert tuning_delta("Drop C", "Drop D") == 2
    assert tuning_delta("Drop B", "Drop D") == 3
    assert tuning_delta("E Standard", "Drop D") is None
    print("Matemática de afinações: OK")
    if os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"):
        os.environ["GBW_HEADLESS_TEST"] = "1"
        app = GuitarBackingWizard()
        try:
            app.withdraw()
            for key in ("system", "source", "separation", "tuning", "export", "projects", "logs", "file_pitch", "settings"):
                app.navigate(key)
                app.update_idletasks()
            print("GUI: OK — todas as páginas construídas/navegadas")

            # Regressão do travamento observado no Mint com CustomTkinter em 200%:
            # processa eventos reais por alguns ciclos em 200% e 250%. Se houver
            # loop de <Configure>/wraplength, este self-test deixa de progredir e
            # o validate_local.sh o encerra por timeout com erro visível.
            for scale in (2.00, 2.50):
                app.apply_ui_scale(scale, persist=False)
                app.geometry("1200x760")
                app.navigate("file_pitch")
                deadline = time.monotonic() + 0.45
                cycles = 0
                while time.monotonic() < deadline:
                    app.update()
                    cycles += 1
                    time.sleep(0.01)
                if cycles < 5:
                    raise RuntimeError(f"Poucos ciclos de GUI em escala {scale:.2f}: {cycles}")
                print(f"GUI escala {int(scale*100)}%: OK — eventos processados sem loop")
        finally:
            app.destroy()
    else:
        print("GUI: SKIP — sessão gráfica não detectada")
    print("SELF-TEST: OK")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(add_help=True)
    ap.add_argument("--self-test", action="store_true", help="executa validações locais e sai")
    args = ap.parse_args(argv)
    if args.self_test:
        return self_test()
    app = GuitarBackingWizard()
    app.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
