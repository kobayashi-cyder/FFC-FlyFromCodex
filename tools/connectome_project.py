#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fly_agent.connectome_projection import compile_projection, load_role_map, read_edge_csv


def main() -> None:
    ap = argparse.ArgumentParser(description="Project a verified connectome edge list into Fly Proxy Agent JSON")
    ap.add_argument("--edges", required=True, help="CSV with src,dst,weight")
    ap.add_argument("--roles", required=True, help="JSON mapping verified node IDs to sensory/motor/interneuron roles")
    ap.add_argument("--out", required=True)
    ap.add_argument("--max-hops", type=int, default=3)
    ap.add_argument("--weight-scale", type=float, default=100.0)
    args = ap.parse_args()
    projection = compile_projection(
        read_edge_csv(args.edges),
        load_role_map(args.roles),
        max_hops=args.max_hops,
        weight_scale=args.weight_scale,
    )
    Path(args.out).write_text(json.dumps(projection, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(projection["metadata"], ensure_ascii=False))


if __name__ == "__main__":
    main()
