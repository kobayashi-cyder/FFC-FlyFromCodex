from __future__ import annotations

from typing import Protocol


class SpeechInput(Protocol):
    def listen(self) -> str: ...


class HumanOutput(Protocol):
    def write(self, text: str) -> None: ...


class SpeechOutput(Protocol):
    def speak(self, text: str) -> None: ...


class MultiOutput:
    """Fan-out sink for screen plus optional speech without coupling the agent to one UI."""

    def __init__(self, *outputs: HumanOutput | SpeechOutput):
        self.outputs = outputs

    def __call__(self, text: str) -> None:
        for out in self.outputs:
            if hasattr(out, "write"):
                out.write(text)  # type: ignore[attr-defined]
            elif hasattr(out, "speak"):
                out.speak(text)  # type: ignore[attr-defined]
