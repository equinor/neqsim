"""Start a native two-source blowdown screening run at confirmed simulated isolation."""
import csv
import json
import math

from steady import J, ROOT, build


def run():
    with (ROOT / "blocked_gas.csv").open() as handle:
        isolation = next(row for row in csv.DictReader(handle)
                         if int(row["trip"]) and all(float(row[tag]) == 0
                         for tag in ("inlet_sdv_pct", "transfer_sdv_pct", "export_sdv_pct")))
    start = float(isolation["t_s"])
    _, _, streams, _, _ = build()
    pkg = "neqsim.process.safety.depressurization."
    simulator = J(pkg + "DepressurizationSimulator")
    study = J(pkg + "MultiVesselBlowdownStudy")()
    # Fixed receiving-header state is common to both branches.
    study.setHeader(0.30, 1.5, 288.15, 0.020, 1.30).setGridStep(1.0)
    for tag, volume, bore in [(101, 45, 0.026), (102, 35, 0.020)]:
        gas = streams[f"gas{tag}"].getFluid().clone()
        gas.setPressure(float(isolation[f"P{tag}_bara"]), "bara")
        fraction_liquid = (float(isolation[f"oil{tag}_volpct"]) +
                           float(isolation[f"water{tag}_volpct"])) / 100
        headspace = volume * (1 - fraction_liquid)
        vessel = simulator(gas, headspace, bore, 0.72, 1.5e5)
        vessel.setTimeStep(1.0).setMaxTime(180).setStopPressure(1.6e5)
        study.addSource(f"V-{tag}", vessel)
    result = study.run()
    tags = ("V-101", "V-102")
    sources = result.getSourceMassFlowKgPerS()
    times = list(result.getTimeS())
    totals = list(result.getTotalMassFlowKgPerS())
    with (ROOT / "flare.csv").open("w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["plant_time_s", "HP_kg_s", "MP_kg_s", "total_kg_s"])
        for i, (t, total) in enumerate(zip(times, totals)):
            rates = [sources.get(tag).get(i) for tag in tags]
            if not all(math.isfinite(q) and q >= 0 for q in rates) or abs(sum(rates) - total) > 1e-9:
                raise ValueError("Inconsistent flare source handoff")
            writer.writerow([start + t, *rates, total])
    return {"isolated_at_s": start, "time_samples": len(times),
            "peak_total_kg_s": result.getPeakTotalMassFlowKgPerS(),
            "peak_plant_time_s": start + result.getPeakTimeS(),
            "peak_header_mach": result.getHeaderMach(), "assumed_header_bara": 1.5}


if __name__ == "__main__":
    summary = run()
    (ROOT / "flare.json").write_text(json.dumps(summary, indent=2, allow_nan=False))
    print(summary)
