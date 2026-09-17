from __future__ import annotations

import logging
import queue
from pathlib import Path

class GuiQueueHandler(logging.Handler):
    def __init__(self, q: queue.Queue[str]):
        super().__init__()
        self.q = q
    def emit(self, record: logging.LogRecord) -> None:
        try:
            self.q.put_nowait(self.format(record))
        except Exception:
            pass


def configure_logging(gui_queue: queue.Queue[str] | None = None, project_log: Path | None = None) -> logging.Logger:
    logger = logging.getLogger("gbw")
    logger.setLevel(logging.DEBUG)
    # Fecha handlers antigos antes de reconfigurar (importante em testes/reaberturas).
    for handler in list(logger.handlers):
        try:
            handler.flush()
            handler.close()
        except Exception:
            pass
        logger.removeHandler(handler)
    fmt = logging.Formatter("%(asctime)s %(levelname)-8s %(message)s", "%H:%M:%S")

    console = logging.StreamHandler()
    console.setLevel(logging.INFO)
    console.setFormatter(fmt)
    logger.addHandler(console)

    if gui_queue is not None:
        gh = GuiQueueHandler(gui_queue)
        gh.setLevel(logging.INFO)
        gh.setFormatter(fmt)
        logger.addHandler(gh)

    if project_log is not None:
        project_log.parent.mkdir(parents=True, exist_ok=True)
        fh = logging.FileHandler(project_log, encoding="utf-8")
        fh.setLevel(logging.DEBUG)
        fh.setFormatter(fmt)
        logger.addHandler(fh)
    return logger
