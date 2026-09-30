from __future__ import annotations

import argparse
import json

from .models import Stimulus
from .runtime import FlyMachineAgent


def main() -> None:
    ap = argparse.ArgumentParser(description="Fly-led proxy agent with machine/tool prostheses")
    ap.add_argument("goal", nargs="*", help="goal text; omitted starts the console")
    ap.add_argument("--state-dir", default=".fly_agent_state")
    args = ap.parse_args()
    agent = FlyMachineAgent(state_dir=args.state_dir)

    if args.goal:
        text = " ".join(args.goal)
        agent.perceive([Stimulus("human_command", 1.0, 1.0, {"text": text})])
        agent.submit_goal(text)
        agent.run()
        return

    print("Fly Proxy Console. /help for commands")
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
        if line == "/help":
            print("/new [title] /listen LABEL on|off /pause /resume /stop /status /danger /novelty /retry GOAL /cancel GOAL /quit")
            continue
        if line.startswith("/new"):
            title = line[4:].strip() or None
            print(agent.new_thread(title))
            continue
        if line.startswith("/listen "):
            parts = line.split()
            if len(parts) != 3 or parts[2].lower() not in {"on", "off"}:
                print("usage: /listen LABEL on|off")
            else:
                agent.set_thread_listener(parts[1], parts[2].lower() == "on")
            continue
        if line == "/pause":
            agent.pause(); continue
        if line == "/resume":
            agent.resume(); continue
        if line == "/stop":
            agent.stop(); continue
        if line == "/status":
            print(json.dumps(agent.status(), ensure_ascii=False, indent=2)); continue
        if line == "/danger":
            agent.perceive([Stimulus("danger", 1.0, 1.0)]); continue
        if line == "/novelty":
            agent.perceive([Stimulus("novelty", 1.0, 1.0)]); continue
        if line.startswith("/retry "):
            print(agent.retry_goal(line.split(maxsplit=1)[1])); continue
        if line.startswith("/cancel "):
            print(agent.cancel_goal(line.split(maxsplit=1)[1])); continue
        agent.voice_input(line)
        agent.run()


if __name__ == "__main__":
    main()
