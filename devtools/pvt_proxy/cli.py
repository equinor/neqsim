"""Command line for the PVT forward-proxy generator.

    pvt-proxy run CONFIG --out DIR [--resume] [--strict]   complete workflow
    pvt-proxy fluids CONFIG --out DIR                      generate and save the fluid ensemble
    pvt-proxy reference CONFIG --out DIR                   reference results only
    pvt-proxy evaluate PROXY CONFIG --out CSV [--fluids F] fitted proxy on (new) fluids, extrapolation warnings
    pvt-proxy export PROXY --target T [--target T] --out DIR
    pvt-proxy example {synthetic,recompression} --out FILE write an example configuration
"""
from __future__ import annotations

import argparse
import csv
import json
import shutil
import sys
from pathlib import Path
from typing import List, Optional

EXAMPLES = {"synthetic": "synthetic_three_stage.yaml", "recompression": "recompression_reference.yaml"}


def _parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(prog="pvt-proxy", description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run", help="fluids, reference, calibration, validation, exports and report")
    r.add_argument("config")
    r.add_argument("--out", required=True)
    r.add_argument("--resume", action="store_true", help="restart from out/checkpoint.json if it matches the config")
    r.add_argument("--strict", action="store_true", help="exit 2 when the fit status is review_required")
    f = sub.add_parser("fluids", help="generate the fluid ensemble (fluids.json)")
    f.add_argument("config")
    f.add_argument("--out", required=True)
    rf = sub.add_parser("reference", help="run the reference process for every fluid")
    rf.add_argument("config")
    rf.add_argument("--out", required=True)
    e = sub.add_parser("evaluate", help="evaluate a fitted proxy on fluids, with extrapolation warnings")
    e.add_argument("proxy", help="proxy_definition.yaml or .json")
    e.add_argument("config", help="configuration giving the template, feed and standard conditions")
    e.add_argument("--fluids", help="fluids.json (default: the ensemble of the configuration)")
    e.add_argument("--out", required=True, help="output CSV")
    x = sub.add_parser("export", help="write target-specific process definitions")
    x.add_argument("proxy")
    x.add_argument("--target", action="append", required=True)
    x.add_argument("--out", required=True)
    ex = sub.add_parser("example", help="copy an example configuration")
    ex.add_argument("name", choices=sorted(EXAMPLES))
    ex.add_argument("--out", required=True)
    return ap


def main(argv: Optional[List[str]] = None) -> int:
    """CLI entry point; returns the exit code."""
    args = _parser().parse_args(argv)
    from .config import ConfigError, load_config
    try:
        if args.cmd == "example":
            src = Path(__file__).parent / "examples" / EXAMPLES[args.name]
            shutil.copyfile(src, args.out)
            print("wrote %s" % args.out)
            return 0
        if args.cmd == "export":
            from .export import load_definition, run_exports
            for p in run_exports(load_definition(args.proxy), args.target, Path(args.out)):
                print("wrote %s" % p)
            return 0
        cfg = load_config(args.config)
        from . import workflow
        if args.cmd == "run":
            summary = workflow.run(cfg, Path(args.out), resume=args.resume)
            st = summary["definition"]["proxy_process"]["calibration"]["status"]["status"]
            return 2 if (args.strict and st != "pass") else 0
        out = Path(args.out)
        if args.cmd == "fluids":
            out.mkdir(parents=True, exist_ok=True)
            from .fluids import save_ensemble
            _, ens = workflow.prepare(cfg)
            save_ensemble(out / "fluids.json", ens)
            print("wrote %s" % (out / "fluids.json"))
            return 0
        if args.cmd == "reference":
            out.mkdir(parents=True, exist_ok=True)
            from .calibrate import run_references
            engine, ens = workflow.prepare(cfg)
            refs = run_references(cfg, engine, ens, cache_path=out / "reference_cache.json")
            workflow._write_csv(out / "reference_results.csv",
                                [dict(fluid_id=k, **{kk: workflow._num(vv) for kk, vv in v.items()}) for k, v in refs.items()])
            print("wrote %s" % (out / "reference_results.csv"))
            return 0
        if args.cmd == "evaluate":
            from .export import load_definition
            from .fluids import load_ensemble
            engine, ens = workflow.prepare(cfg)
            if args.fluids:
                ens = load_ensemble(args.fluids)
            rows = workflow.evaluate_fluids(load_definition(args.proxy), cfg, ens, engine)
            out.parent.mkdir(parents=True, exist_ok=True)
            with open(out, "w", newline="", encoding="utf-8") as fh:
                w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
                w.writeheader()
                for r in rows:
                    w.writerow(dict(r, warnings=json.dumps(r["warnings"])))
            for r in rows:
                for wmsg in r["warnings"]:
                    print("WARNING %s: %s" % (r["fluid_id"], wmsg))
            print("wrote %s (%d fluids, %d extrapolating)" % (out, len(rows), sum(r["extrapolation"] for r in rows)))
            return 0
    except ConfigError as exc:
        print("configuration error: %s" % exc, file=sys.stderr)
        return 1
    return 1
