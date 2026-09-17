from __future__ import annotations

import logging
import queue
import threading
from dataclasses import dataclass
from typing import Any, Callable

@dataclass
class ProgressEvent:
    mode: str = "indeterminate"  # indeterminate/determinate
    fraction: float | None = None
    message: str = ""

class TaskManager:
    def __init__(self, root, logger: logging.Logger):
        self.root = root
        self.logger = logger
        self.cancel_event = threading.Event()
        self.thread: threading.Thread | None = None
        self._ui_queue: queue.Queue[tuple[Callable, tuple, dict]] = queue.Queue()
        self._stopped = False
        self._poll_after_id = self.root.after(80, self._poll)

    @property
    def busy(self) -> bool:
        return bool(self.thread and self.thread.is_alive())

    def call_ui(self, fn: Callable, *args, **kwargs) -> None:
        self._ui_queue.put((fn, args, kwargs))

    def _poll(self):
        self._poll_after_id = None
        if self._stopped:
            return
        try:
            while True:
                fn, args, kwargs = self._ui_queue.get_nowait()
                try:
                    fn(*args, **kwargs)
                except Exception:
                    self.logger.exception("Falha em callback de UI")
        except queue.Empty:
            pass
        if not self._stopped:
            try:
                self._poll_after_id = self.root.after(80, self._poll)
            except Exception:
                self._poll_after_id = None

    def shutdown(self):
        self._stopped = True
        self.cancel_event.set()
        if self._poll_after_id:
            try:
                self.root.after_cancel(self._poll_after_id)
            except Exception:
                pass
            self._poll_after_id = None

    def submit(self, label: str, fn: Callable[[], Any], on_success: Callable[[Any], None] | None = None,
               on_error: Callable[[Exception], None] | None = None, on_finally: Callable[[], None] | None = None) -> bool:
        if self.busy:
            return False
        self.cancel_event.clear()
        def worker():
            self.logger.info("Tarefa iniciada: %s", label)
            try:
                result = fn()
                self.logger.info("Tarefa concluída: %s", label)
                if on_success:
                    self.call_ui(on_success, result)
            except Exception as exc:
                if self.cancel_event.is_set() or "cancelad" in str(exc).lower():
                    self.logger.warning("Tarefa cancelada: %s", label)
                else:
                    self.logger.exception("Tarefa falhou: %s", label)
                if on_error:
                    self.call_ui(on_error, exc)
            finally:
                if on_finally:
                    self.call_ui(on_finally)
        self.thread = threading.Thread(target=worker, daemon=True, name=f"GBW-{label}")
        self.thread.start()
        return True

    def cancel(self):
        self.cancel_event.set()
        self.logger.warning("Cancelamento solicitado pelo usuário")
