from __future__ import annotations

import argparse

from .models import Stimulus
from .runtime import FlyMachineAgent


def main() -> None:
    ap = argparse.ArgumentParser(description="Fly connectome + machine prosthetic agent")
    ap.add_argument("goal", nargs="*", help="goal text; omitted starts a tiny REPL")
    ap.add_argument("--state-dir", default=".fly_agent_state")
    args = ap.parse_args()
    agent = FlyMachineAgent(state_dir=args.state_dir)

    if args.goal:
        text = " ".join(args.goal)
        agent.perceive([Stimulus("human_command", 1.0, 1.0, {"text": text})])
        agent.submit_goal(text)
        agent.run()
        return

    print("FlyMachineAgent REPL. commands: /pause /resume /stop /status /danger /novelty /quit")
    while True:
        try:
            line = input("> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if not line:
            continue
        if line == "/quit":
            break
        if line == "/pause":
            agent.pause()
            continue
        if line == "/resume":
            agent.resume()
            continue
        if line == "/stop":
            agent.stop()
            continue
        if line == "/status":
            print(agent.status())
            continue
        if line == "/danger":
            agent.perceive([Stimulus("danger", 1.0, 1.0)])
            continue
        if line == "/novelty":
            agent.perceive([Stimulus("novelty", 1.0, 1.0)])
            continue
        agent.perceive([Stimulus("human_command", 1.0, 1.0, {"text": line})])
        agent.submit_goal(line)
        agent.run()


if __name__ == "__main__":
    main()
