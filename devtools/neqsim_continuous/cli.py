"""Command line for living tasks, dispatched as ``neqsim task-<command>``.

    neqsim task-living <task> [--brief FILE]
    neqsim task-cycle <task> [--mode monitor|solve] [--stages a,b] [--dry-run] [--no-agent] [--now ISO]
    neqsim task-solve <task> [--until goal|converged] [--max-rounds N] [--no-agent] [--allow-unconfirmed]
    neqsim task-backtest <task> --start ISO --end ISO [--step-hours 24] [--repeat]
    neqsim task-schedule <task> [--daily HH:MM] [--install | --remove | --show]
    neqsim task-promote <task> <cycle-id> --reviewer NAME [--note TEXT]
    neqsim task-ledger <task> [list | show ID | set ID STATUS --by NAME [--note TEXT] | merge OTHER]
    neqsim task-status [task-or-task-root]
    neqsim task-report <task> [--formal]
    neqsim task-reference-case [parent-folder]

A <task> that is not an existing folder is looked up in the task root used for new tasks
(``neqsim --show-task-root``), so ``neqsim task-cycle 2026-09-24_my_task`` works from any
folder. Without a folder, ``task-status`` and ``task-reference-case`` use that task root.
"""

import argparse
import json
import os
import sys

COMMANDS = ("living", "cycle", "solve", "backtest", "schedule", "promote", "ledger", "status",
            "report", "reference-case")


def _print(data):
    print(json.dumps(data, indent=2, sort_keys=True, default=str))


def task_root():
    """Return the task root for new tasks: --task-root, NEQSIM_TASK_ROOT, saved default, task_solve/."""
    try:
        from new_task import resolve_task_root
    except ImportError:  # runner used outside devtools
        return os.path.abspath(os.environ.get("NEQSIM_TASK_ROOT") or os.getcwd())
    return resolve_task_root()


def _task(path):
    candidate = os.path.abspath(path)
    if not os.path.isdir(candidate) and not os.path.isabs(os.path.expanduser(path)):
        in_root = os.path.join(task_root(), path)
        if os.path.isdir(in_root):
            candidate = in_root
    if not os.path.isdir(candidate):
        raise SystemExit("ERROR: task folder not found: {} (also looked in the task root {})".format(
            os.path.abspath(path), task_root()))
    return candidate


def _parse_time(text):
    if not text:
        return None
    from .watermarks import parse_time
    return parse_time(text)


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    parser = argparse.ArgumentParser(prog="neqsim task-<command>", description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("living", help="make an existing task living (never overwrites)")
    p.add_argument("task")
    p.add_argument("--brief", help="Word or Markdown task brief")

    p = sub.add_parser("cycle", help="run one cycle")
    p.add_argument("task")
    p.add_argument("--mode", default="monitor", choices=["monitor", "solve"])
    p.add_argument("--stages", help="comma-separated subset of stages")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--no-agent", action="store_true")
    p.add_argument("--now", help="override the cycle clock (ISO time)")

    p = sub.add_parser("solve", help="solve until the goal is met or improvement is marginal")
    p.add_argument("task")
    p.add_argument("--until", default="goal", choices=["goal", "converged"])
    p.add_argument("--max-rounds", type=int)
    p.add_argument("--no-agent", action="store_true")
    p.add_argument("--allow-unconfirmed", action="store_true")
    p.add_argument("--reset", action="store_true", help="start a fresh iteration history")

    p = sub.add_parser("backtest", help="replay archived data with a simulated clock")
    p.add_argument("task")
    p.add_argument("--start", required=True)
    p.add_argument("--end", required=True)
    p.add_argument("--step-hours", type=float, default=24.0)
    p.add_argument("--name", default="backtest")
    p.add_argument("--repeat", action="store_true", help="rerun and report reproducibility")

    p = sub.add_parser("schedule", help="schedule monitor cycles")
    p.add_argument("task")
    p.add_argument("--daily", default="05:00")
    group = p.add_mutually_exclusive_group()
    group.add_argument("--install", action="store_true")
    group.add_argument("--remove", action="store_true")
    group.add_argument("--show", action="store_true")

    p = sub.add_parser("promote", help="promote a complete cycle to the baseline")
    p.add_argument("task")
    p.add_argument("cycle")
    p.add_argument("--reviewer", required=True)
    p.add_argument("--note", default="")

    p = sub.add_parser("ledger", help="list or update the improvement ledger")
    p.add_argument("task")
    p.add_argument("action", nargs="?", default="list", choices=["list", "show", "set", "merge"])
    p.add_argument("args", nargs="*")
    p.add_argument("--by", default="")
    p.add_argument("--note", default="")

    p = sub.add_parser("status", help="status of one living task or every living task in a folder")
    p.add_argument("path", nargs="?", help="task or folder (default: the task root)")

    p = sub.add_parser("report", help="rebuild continuous/LIVING_REPORT.md now")
    p.add_argument("task")
    p.add_argument("--formal", action="store_true", help="also regenerate the Word/HTML report")

    p = sub.add_parser("reference-case", help="create the public reference task")
    p.add_argument("parent", nargs="?", help="parent folder (default: the task root)")

    args = parser.parse_args(argv)
    if getattr(args, "task", None):
        args.task = _task(args.task)

    if args.command == "living":
        from .living import make_living
        _print(make_living(_task(args.task), brief=args.brief))
    elif args.command == "cycle":
        from .cycle import run_cycle
        from .living import note_reopen
        stages = args.stages.split(",") if args.stages else None
        manifest = run_cycle(_task(args.task), mode=args.mode, now=_parse_time(args.now),
                             stages=stages, dry_run=args.dry_run, no_agent=args.no_agent)
        if not args.dry_run:
            note_reopen(args.task, manifest)
        print(open(os.path.join(args.task, "continuous", "cycles", manifest["cycle_id"], "digest.md"),
                   encoding="utf-8").read() if os.path.exists(os.path.join(
                       args.task, "continuous", "cycles", manifest["cycle_id"], "digest.md")) else "")
        print("cycle {}: {}{}".format(manifest["cycle_id"], manifest["status"],
                                      " (degraded)" if manifest.get("degraded") else ""))
    elif args.command == "solve":
        from .solve import solve
        state = solve(_task(args.task), until=args.until, max_rounds=args.max_rounds,
                      no_agent=args.no_agent, allow_unconfirmed=args.allow_unconfirmed, reset=args.reset)
        _print({k: state[k] for k in ("state", "reason", "rounds", "details", "paused_branches")})
    elif args.command == "backtest":
        from .backtest import run_backtest
        report = run_backtest(_task(args.task), _parse_time(args.start), _parse_time(args.end),
                              step_hours=args.step_hours, run_name=args.name,
                              check_reproducibility=args.repeat)
        summary = {k: report[k] for k in ("cycles", "detected", "missed", "false_alarms_per_month",
                                          "expected", "reproducibility") if k in report}
        _print(summary)
    elif args.command == "schedule":
        from . import schedule
        spec = schedule.build(_task(args.task), daily=args.daily)
        if args.install:
            _print(dict(schedule.install(spec), command=spec["command"]))
        elif args.remove:
            _print(schedule.remove(args.task))
        elif args.show:
            _print(schedule.show(args.task))
        else:
            _print(spec)
    elif args.command == "promote":
        from .living import promote
        _print(promote(_task(args.task), args.cycle, args.reviewer, args.note))
        print("Living report updated: {}".format(os.path.join(args.task, "continuous", "LIVING_REPORT.md")))
        print("Regenerate the formal report with: neqsim report \"{}\" "
              "(or set report.formal: on_promote in cycle_plan.yaml)".format(args.task))
    elif args.command == "ledger":
        from .ledger import Ledger
        from .living_report import update
        book = Ledger(os.path.join(_task(args.task), "continuous", "ledger", "events.jsonl"))
        if args.action == "list":
            for key, item in sorted(book.current().items()):
                print("{}  {:<13} {:<13} {}".format(key, item.get("status"), item.get("category"),
                                                   item.get("title")))
        elif args.action == "show":
            _print(book.current().get(args.args[0]) if args.args else {})
        elif args.action == "set":
            if len(args.args) != 2 or not args.by:
                raise SystemExit("usage: task-ledger <task> set ID STATUS --by NAME")
            _print(book.set_status(args.args[0], args.args[1], args.by, args.note))
            update(args.task, event="ledger")
        elif args.action == "merge":
            print("{} new events merged".format(book.merge(args.args[0])))
            update(args.task, event="ledger")
    elif args.command == "status":
        from .living import status
        from .plan import is_living
        path = _task(args.path) if args.path else task_root()
        if is_living(path):
            _print(status(path))
        else:
            names = sorted(os.listdir(path)) if os.path.isdir(path) else []
            rows = [status(os.path.join(path, n)) for n in names
                    if os.path.isdir(os.path.join(path, n)) and is_living(os.path.join(path, n))]
            _print(rows)
    elif args.command == "report":
        from .living_report import regenerate_formal, update
        path = update(_task(args.task), event="manual")
        print(path or "ERROR: living report not written (is the task living?)")
        if args.formal:
            regenerate_formal(args.task)
    elif args.command == "reference-case":
        from .reference_case import create_reference_task
        parent = os.path.abspath(args.parent) if args.parent else task_root()
        os.makedirs(parent, exist_ok=True)
        print(create_reference_task(parent))
    return 0


if __name__ == "__main__":
    sys.exit(main())
