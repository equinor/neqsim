"""Chronological Volve well-history evaluation with the native linear liquid IPR.

Reads the public daily CSV locally. Zero gauges, shut-in/partial days and
injection volumes without a pressure observation never enter a pressure fit.
"""

import argparse
import csv
from datetime import datetime
import json
import math
import os
from pathlib import Path
import statistics

import jpype


def value(row, key):
    text = row[key].strip().replace(",", "")
    return float(text) if text else math.nan


def fit(x, y):
    """Least-squares intercept and slope with explicit identifiability checks."""
    if len(x) < 3 or max(x) - min(x) < 1.:
        raise ValueError("Insufficient pressure or flow variation for fitting")
    x_bar, y_bar = statistics.mean(x), statistics.mean(y)
    spread = sum((v - x_bar) ** 2 for v in x)
    slope = sum((v - x_bar) * (w - y_bar) for v, w in zip(x, y)) / spread
    return y_bar - slope * x_bar, slope


def errors(observed, predicted):
    return {"n": len(observed), "mae": statistics.mean(abs(o - p) for o, p in zip(observed, predicted)),
            "bias": statistics.mean(p - o for o, p in zip(observed, predicted))}


def injection_holdout(path, n):
    """Replay paired single-zone water injection observations on their original mass basis."""
    with path.open(encoding="utf-8", newline="") as source:
        rows = sorted(csv.DictReader(source), key=lambda row: row["date"])
    if not rows or len({r["date"] for r in rows}) != len(rows):
        raise ValueError("Require unique observation dates for one injector")
    train = [r for r in rows if r["role"] == "calibration"]
    hold = [r for r in rows if r["role"] == "holdout"]
    if len(train) < 3 or not hold or len(train) + len(hold) != len(rows) or train[-1]["date"] >= hold[0]["date"]:
        raise ValueError("Require an earlier calibration period and a later holdout period")
    deltas = []
    for r in rows:
        datetime.strptime(r["date"], "%Y-%m-%d")
        values = [float(r[k]) for k in ("bottomhole_bara", "reservoir_bara", "water_kg_s", "fracture_bara")]
        if not all(math.isfinite(v) and v > 0 for v in values) or values[0] <= values[1] or not r["source"].strip():
            raise ValueError("Require positive forward injectivity observations and measurement source")
        if r["role"] == "calibration":
            if values[0] >= values[3]:
                raise ValueError("Training observation is outside the unfractured linear model")
            deltas.append(values[0] - values[1])
    if max(deltas) - min(deltas) < 1.:
        raise ValueError("Insufficient injection pressure excitation")
    index = sum(d * float(r["water_kg_s"]) for d, r in zip(deltas, train)) / sum(d*d for d in deltas)
    errors_by_date = []
    for row in hold:
        p_res, p_bh = float(row["reservoir_bara"]), float(row["bottomhole_bara"])
        water = n.thermo.system.SystemSrkEos(298.15, p_res)
        water.addComponent("water", 1.)
        water.setMixingRule("classic")
        supply = n.process.equipment.stream.Stream("Water supply", water)
        supply.setFlowRate(1., "kg/sec")
        supply.run()
        well = n.process.equipment.reservoir.WellFlow("Single-zone injector")
        well.setInletStream(supply)
        well.addLiquidInjectionZone("zone", supply, p_res, index, float(row["fracture_bara"]))
        well.setOutletPressure(p_bh, "bara")
        well.run()
        errors_by_date.append({"date": row["date"], "source": row["source"], "observed_kg_s": float(row["water_kg_s"]),
                               "predicted_kg_s": well.getOutletStream().getFlowRate("kg/sec"),
                               "fracture_margin_bar": float(row["fracture_bara"]) - p_bh})
    return {"fitted_mass_injectivity_kg_s_bar": index, "holdout": errors_by_date,
            "errors_kg_s": errors([r["observed_kg_s"] for r in errors_by_date],
                                  [r["predicted_kg_s"] for r in errors_by_date])}


def evaluate(path, ipr):
    groups = {}
    seen = set()
    with path.open(encoding="utf-8-sig", newline="") as source:
        for r in csv.DictReader(source):
            well = r["NPD_WELL_BORE_NAME"]
            if well not in ("15/9-F-12", "15/9-F-14", "15/9-F-4"):
                continue
            date = datetime.strptime(r["DATEPRD"], "%d-%b-%y").date().isoformat()
            hours = value(r, "ON_STREAM_HRS")
            if not ("2008-06-01" <= date < "2008-09-01" and 23.5 <= hours <= 24):
                continue
            producer = well != "15/9-F-4"
            if r["FLOW_KIND"] != ("production" if producer else "injection"):
                continue
            vol = (value(r, "BORE_OIL_VOL") + value(r, "BORE_WAT_VOL")
                   if producer else value(r, "BORE_WI_VOL"))
            pressure = value(r, "AVG_DOWNHOLE_PRESSURE")
            if not vol > 0 or (producer and not pressure > 0):
                continue
            if (well, date) in seen:
                raise ValueError(f"Duplicate observation for {well} on {date}")
            seen.add((well, date))
            groups.setdefault(well, []).append((date, pressure, vol * 24 / hours))
    results, by_day = {}, {}
    for well in ("15/9-F-12", "15/9-F-14", "15/9-F-4"):
        records = sorted(groups[well])
        train = [x for x in records if x[0] < "2008-08-01"]
        holdout = [x for x in records if x[0] >= "2008-08-01"]
        if not train or not holdout:
            raise ValueError(f"Empty train or holdout partition for {well}")
        observed = [x[2] for x in holdout]
        baseline = statistics.mean(x[2] for x in train)
        result = {"training_days": len(train), "holdout_days": len(holdout),
                  "mean_rate_baseline_Sm3_day": errors(observed, [baseline] * len(holdout))}
        if well == "15/9-F-4":
            result["positive_training_downhole_pressure_count"] = sum(x[1] > 0 for x in train)
            result["injectivity_evidence"] = "Requires paired injection and reservoir pressures"
        else:
            intercept, slope = fit([x[1] for x in train], [x[2] for x in train])
            if not slope < 0 or not intercept > 0:
                result["rejected_fit"] = "Nonphysical effective productivity or pressure intercept"
            else:
                model = ipr.linear(-slope, -intercept / slope)
                predicted = [model.rate(x[1]) for x in holdout]
                slopes = []
                for block_start in range(0, len(train), 7):
                    sample = train[:block_start] + train[block_start + 7:]
                    if len(sample) >= 3 and max(x[1] for x in sample) - min(x[1] for x in sample) >= 1:
                        slopes.append(-fit([x[1] for x in sample], [x[2] for x in sample])[1])
                result["leave_seven_observations_out_PI_range"] = [min(slopes), max(slopes)] if slopes else None
                result.update({"effective_PI_Sm3_day_bar": -slope,
                               "effective_pressure_intercept_bar_on_source_basis": -intercept / slope,
                               "conditional_liquid_rate_Sm3_day": errors(observed, predicted),
                               "training_flowing_pressure_span_bar": [min(x[1] for x in train),
                                                                      max(x[1] for x in train)]})
                for row, pred in zip(holdout, predicted):
                    by_day.setdefault(row[0], []).append((row[2], pred))
        results[well] = result
    paired = [v for v in by_day.values() if len(v) == 2]
    if paired:
        results["sum_of_two_conditional_rates_Sm3_day"] = errors(
            [sum(o for o, _ in day) for day in paired], [sum(p for _, p in day) for day in paired])
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--paired-injection", type=Path, help="Single-zone pressure/rate history CSV")
    args = parser.parse_args()
    jpype.startJVM("-Xmx256m", classpath=os.environ["NEQSIM_CLASSPATH"].split(os.pathsep))
    ipr = jpype.JClass("neqsim.process.equipment.reservoir.InflowPerformance")
    results = evaluate(args.csv, ipr)
    if args.paired_injection:
        results["paired_injection"] = injection_holdout(args.paired_injection, jpype.JPackage("neqsim"))
    print(json.dumps(results, indent=2, allow_nan=False))
