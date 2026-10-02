from __future__ import annotations

from typing import Callable, Protocol

from .models import OutputMode


class SpeechInput(Protocol):
    def listen(self) -> str: ...


class HumanOutput(Protocol):
    def write(self, text: str) -> None: ...


class SpeechOutput(Protocol):
    def speak(self, text: str) -> None: ...


class CallableScreenOutput:
    def __init__(self, fn: Callable[[str], None]):
        self.fn = fn

    def write(self, text: str) -> None:
        self.fn(text)


class CallableSpeechOutput:
    def __init__(self, fn: Callable[[str], None]):
        self.fn = fn

    def speak(self, text: str) -> None:
        self.fn(text)


class HumanChannel:
    def __init__(self, screen: HumanOutput | None = None, speech: SpeechOutput | None = None):
        self.screen = screen
        self.speech = speech

    def emit(self, text: str, mode: OutputMode = OutputMode.BOTH) -> None:
        if mode == OutputMode.SILENT:
            return
        if mode in (OutputMode.SCREEN, OutputMode.BOTH) and self.screen:
            self.screen.write(text)
        if mode in (OutputMode.SPEECH, OutputMode.BOTH) and self.speech:
            self.speech.speak(text)

    def __call__(self, text: str) -> None:
        self.emit(text, OutputMode.BOTH)


class MultiOutput:
    """Backward-compatible fan-out sink."""

    def __init__(self, *outputs: HumanOutput | SpeechOutput):
        self.outputs = outputs

    def __call__(self, text: str) -> None:
        for out in self.outputs:
            if hasattr(out, "write"):
                out.write(text)  # type: ignore[attr-defined]
            elif hasattr(out, "speak"):
                out.speak(text)  # type: ignore[attr-defined]
