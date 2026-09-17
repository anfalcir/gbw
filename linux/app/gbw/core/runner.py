from __future__ import annotations

import json
import logging
import os
import queue
import shlex
import signal
import subprocess
import threading
import time
from typing import Callable

class CommandError(RuntimeError):
    pass

class CommandRunner:
    def __init__(self, logger: logging.Logger, cancel_event: threading.Event | None = None):
        self.logger = logger
        self.cancel_event = cancel_event or threading.Event()
        self.process: subprocess.Popen | None = None

    def cancel(self) -> None:
        """Cancela o comando ativo sem bloquear a thread da GUI.

        No Linux o processo é iniciado em uma nova sessão; o cancelamento envia
        SIGTERM ao grupo inteiro (útil para workers filhos de modelos de IA) e
        escala para SIGKILL após alguns segundos se necessário.
        """
        self.cancel_event.set()
        proc = self.process
        if not proc or proc.poll() is not None:
            return
        try:
            if os.name == "posix":
                os.killpg(proc.pid, signal.SIGTERM)
            else:
                proc.terminate()
        except Exception:
            try:
                proc.terminate()
            except Exception:
                return

        def force_kill():
            deadline = time.monotonic() + 3.0
            while time.monotonic() < deadline and proc.poll() is None:
                time.sleep(0.1)
            if proc.poll() is None:
                self.logger.warning("A tarefa demorou para encerrar e foi finalizada à força.")
                try:
                    if os.name == "posix":
                        os.killpg(proc.pid, signal.SIGKILL)
                    else:
                        proc.kill()
                except Exception:
                    try:
                        proc.kill()
                    except Exception:
                        pass
        threading.Thread(target=force_kill, daemon=True, name="GBW-cancel-watchdog").start()

    def reset_cancel(self) -> None:
        self.cancel_event.clear()

    def run(self, args: list[str], label: str = "", on_line: Callable[[str], None] | None = None) -> str:
        if self.cancel_event.is_set():
            raise CommandError("Operação cancelada.")
        if label:
            self.logger.debug("▶ %s", label)
        self.logger.debug("$ %s", shlex.join([str(a) for a in args]))
        try:
            self.process = subprocess.Popen(
                [str(a) for a in args], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, bufsize=1, universal_newlines=True,
                start_new_session=(os.name == "posix"),
            )
        except FileNotFoundError as exc:
            raise CommandError(f"Comando não encontrado: {args[0]}") from exc
        lines: list[str] = []
        assert self.process.stdout
        for raw in self.process.stdout:
            line = raw.rstrip()
            lines.append(line)
            self.logger.debug(line)
            if on_line:
                on_line(line)
            if self.cancel_event.is_set():
                self.process.terminate()
                break
        try:
            self.process.stdout.close()
        except Exception:
            pass
        rc = self.process.wait()
        self.process = None
        if self.cancel_event.is_set():
            raise CommandError("Operação cancelada.")
        if rc != 0:
            tail = "\n".join(lines[-10:])
            raise CommandError(f"Comando terminou com código {rc}.\n{tail}")
        return "\n".join(lines)

    def capture_json(self, args: list[str], timeout: int = 150) -> dict:
        """Executa comando JSON com suporte real a cancelamento e timeout."""
        if self.cancel_event.is_set():
            raise CommandError("Operação cancelada.")
        self.logger.debug("$ %s", shlex.join([str(a) for a in args]))
        try:
            self.process = subprocess.Popen(
                [str(a) for a in args], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                text=True, start_new_session=(os.name == "posix"),
            )
        except FileNotFoundError as exc:
            raise CommandError(f"Comando não encontrado: {args[0]}") from exc

        start = time.monotonic()
        stdout = stderr = ""
        try:
            while True:
                if self.cancel_event.is_set():
                    self.cancel()
                    try:
                        self.process.wait(timeout=4)
                    except Exception:
                        pass
                    raise CommandError("Operação cancelada.")
                remaining = max(0.05, timeout - (time.monotonic() - start))
                if remaining <= 0.05 and time.monotonic() - start >= timeout:
                    self.cancel()
                    raise CommandError(f"Tempo limite excedido ({timeout}s).")
                try:
                    stdout, stderr = self.process.communicate(timeout=min(0.25, remaining))
                    break
                except subprocess.TimeoutExpired:
                    continue
            rc = self.process.returncode
        finally:
            self.process = None

        if self.cancel_event.is_set():
            raise CommandError("Operação cancelada.")
        if rc != 0:
            tail = (stderr or stdout or "").strip().splitlines()[-6:]
            raise CommandError("; ".join(tail) or f"Comando falhou ({rc})")
        try:
            return json.loads(stdout)
        except json.JSONDecodeError as exc:
            raise CommandError("Resposta JSON inválida") from exc
