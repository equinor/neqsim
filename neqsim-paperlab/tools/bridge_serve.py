"""bridge_serve — list pending prompts produced by the copilot-bridge LLM
provider, so a VS Code Copilot Chat agent can answer them by hand or via
runSubagent and write the reply file.

Workflow
--------
1. paperflow runs with ``--provider copilot-bridge``.
2. For every section/notebook it writes
   ``.llm_bridge/pending/<id>.json`` and blocks polling
   ``.llm_bridge/done/<id>.json``.
3. An in-IDE agent runs ``python bridge_serve.py list`` to see pending
   prompts, ``python bridge_serve.py show <id>`` to read one, and
   ``python bridge_serve.py answer <id> <reply.txt>`` to release the
   waiting paperflow process.

This decouples the long-running paperflow loop from the LLM, letting the
existing VS Code Copilot Chat session BE the LLM — no API key, no SDK,
no per-call cost beyond the user's existing Copilot license.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path


def _root() -> Path:
    root = os.environ.get("PAPERLAB_BRIDGE_DIR") or ".llm_bridge"
    p = Path(root)
    (p / "pending").mkdir(parents=True, exist_ok=True)
    (p / "done").mkdir(parents=True, exist_ok=True)
    return p


def cmd_list(args: argparse.Namespace) -> int:
    pending = sorted((_root() / "pending").glob("*.json"))
    if not pending:
        print("No pending prompts.")
        return 0
    print(f"{len(pending)} pending:")
    for p in pending:
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            sys_msg = next((m["content"][:80] for m in data.get("messages", [])
                           if m.get("role") == "system"), "")
            user_msg = next((m["content"][:80] for m in data.get("messages", [])
                            if m.get("role") == "user"), "")
            print(f"  {p.stem}  model={data.get('model')}  "
                  f"sys={sys_msg!r}...  user={user_msg!r}...")
        except Exception:  # noqa: BLE001
            print(f"  {p.stem}  (unreadable)")
    return 0


def cmd_show(args: argparse.Namespace) -> int:
    p = _root() / "pending" / f"{args.id}.json"
    if not p.exists():
        print(f"No pending prompt {args.id}", file=sys.stderr)
        return 1
    print(p.read_text(encoding="utf-8"))
    return 0


def cmd_answer(args: argparse.Namespace) -> int:
    pending = _root() / "pending" / f"{args.id}.json"
    done = _root() / "done" / f"{args.id}.json"
    if not pending.exists():
        print(f"No pending prompt {args.id}", file=sys.stderr)
        return 1
    if args.reply_path == "-":
        content = sys.stdin.read()
    else:
        content = Path(args.reply_path).read_text(encoding="utf-8")
    done.write_text(json.dumps({"id": args.id, "content": content},
                               indent=2, ensure_ascii=False),
                    encoding="utf-8")
    pending.unlink()
    print(f"Wrote {done}; paperflow will resume.")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="copilot-bridge inbox tool")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list", help="List pending prompts")
    s_show = sub.add_parser("show", help="Print one pending prompt as JSON")
    s_show.add_argument("id")
    s_ans = sub.add_parser("answer", help="Write a reply for a pending prompt")
    s_ans.add_argument("id")
    s_ans.add_argument("reply_path", help="Path to text file with reply, or - for stdin")
    args = ap.parse_args()
    if args.cmd == "list":
        return cmd_list(args)
    if args.cmd == "show":
        return cmd_show(args)
    if args.cmd == "answer":
        return cmd_answer(args)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
