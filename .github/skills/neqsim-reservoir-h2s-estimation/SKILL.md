---
name: neqsim-reservoir-h2s-estimation
calculation_basis: "neqsim-java"
version: "0.1.0"
description: "Screening estimate of H2S in the test gas of a reservoir, prospect or discovery: NeqSim pyrite and Fe-carbonate buffer flashed to separator gas, calibrated on measured wells per depositional setting, with an uncertainty budget (temperature, CO2, pressure), P10/P50/P90, sour-service and gas-specification screening and a report. USE WHEN: asked what H2S to expect from a new reservoir, how certain it is, or whether a reading is plausible."
last_verified: "2026-11-20"
requires:
  python_packages: ["neqsim 3.29 or later (for the mineral buffer)", "matplotlib and python-docx (for the report)"]
  java_packages: ["neqsim.thermo.util.mineral (ReservoirH2SEstimator, MineralH2SBuffers)"]
  env: []
  network: []
---

# H2S in reservoir gas: estimate, uncertainty and report

H2S decides material selection (sour service), gas treatment and whether a gas meets the sales specification. In sandstone reservoirs it is not an accident of the source rock: pyrite and an iron-bearing carbonate hold it at a mineral equilibrium that depends on temperature and CO2. That makes H2S predictable before a well is drilled, provided the reservoir belongs to the setting the buffer describes; how closely is what the calibration of this skill measures on your own wells. This skill turns that into a number with a stated range and a report.

For the chemistry of the buffer itself, the thermochemistry and the stability checks, see `neqsim-reservoir-h2s-mineral-buffer`. For H2S that forms later where seawater is injected, see `neqsim-reservoir-souring-correlation`. For converting a separator-gas reading to wellstream H2S, see `neqsim-h2s-phase-partitioning`. For what the resulting H2S changes in materials, detection, consequence, flare and export gas, see `neqsim-h2s-safety-impact`.

## When to Use

- A prospect or discovery needs an H2S figure for material selection, process design or the choice of gas route before a test exists.
- A reading from a drill stem test must be judged: is it plausible, is it likely too low.
- A set of wells with measured H2S must be turned into a calibration with an error estimate.
- The share of the uncertainty that comes from temperature, CO2 and the model is needed, to decide what to measure.

Not for: reservoir souring during seawater injection, H2S through the process, carbonate and evaporite reservoirs with thermochemical sulfate reduction (the estimate is then a lower bound), or scavenger and treatment design.

## Inputs

- Prospect: name and reservoir temperature (needed). Useful: reservoir pressure, CO2 content, formation or depositional setting, fluid type (gas or oil), position. Missing pressure and CO2 are taken from their trends with temperature in the calibration and listed as data gaps.
- How well the inputs are known: `measured_conditions` for a drilled and sampled reservoir, or `res_T_sd_C`, `co2_sd_log10`, `res_P_sd_log10`. Defaults: 10 °C for a predicted temperature, 3 °C for a measured one.
- H2S readings in test gas from the discovery itself, if any (ppm by mole).
- A calibration registry and the well table it was built from. The data are not part of this skill: build a registry from your own wells with `calibrate`, or use one supplied by a data owner.

## Outputs

- `buffered_h2s(...)`: H2S in the reservoir fluid and in separator gas, H2S fugacity, stability margins of siderite and pyrite.
- `calibrate(wells)`: offset and scatter per depositional setting, errors with each well left out, share of wells inside the P10 to P90 range, comparison with the temperature correlation, CO2 and pressure trends.
- `estimate_prospect(...)`: best estimate, P10/P50/P90, probability above 1, 3, 10 and 30 ppm, a rounded range for design, H2S partial pressure against the sour-service limit of ISO 15156-2, the comparison with a gas limit, the uncertainty budget, analogue wells, reasoning, accuracy statements, assumptions and data gaps (schema `h2s_prospect_estimate.v1`).
- `write_report(...)`: JSON, Markdown and Word report with two figures.

## Engineering Method

1. **Buffer.** `4 FeS2 + CH4 + 3 CO2 + 6 H2O = 4 FeCO3 + 8 H2S`. `ReservoirH2SEstimator` solves the H2S content at which the H2S fugacity of the reservoir fluid (SRK) meets the buffer at reservoir temperature and pressure. A reservoir 10 °C hotter holds about a factor 1.5 more H2S; twice the CO2 gives about a factor 1.3 more; pressure has a weak effect.
2. **Like for like.** H2S is measured in the gas of a test separator, so the reservoir fluid with the buffered H2S is flashed to separator conditions (default 40 °C, 40 bara) and the gas phase is read. For an oil the separator gas holds less H2S than the reservoir fluid.
3. **Fluid.** Without a composition, a declared generic gas or oil is used with the CO2 of the prospect. Calibrate with the same generic fluid, so that the calibration absorbs the difference to real compositions.
4. **Calibration.** For every measured well, the ratio of the measured value (median of the detected readings) to the buffer value at the temperature, pressure and CO2 of that well. Per depositional setting: mean (offset) and scatter of log10 of the ratio. The scatter used for a prediction includes the uncertainty of the class mean, so a setting with three wells gives a wider range than one with fifty. Each well is predicted from the others to check the stated range.
5. **Depositional setting.** Marine and paralic sandstones carry pyrite and siderite or ankerite: the buffer applies. Continental red beds are oxidised and lie below the buffer by a poorly known factor. Carbonates and evaporites are outside the calibration; with anhydrite above about 120 °C, thermochemical sulfate reduction can raise H2S far above the buffer.
6. **Uncertainty budget.** For a prospect the temperature, CO2 and pressure are predictions. Their scatter is carried to H2S with the sensitivity of the buffer (three extra buffer solutions) and added in quadrature to the scatter of the wells. The result is lognormal. The budget table shows which input to improve.
7. **Readings from the discovery.** The median of the readings replaces the buffer value; the range becomes the scatter between repeat readings of one well. A reading below the P10 of the buffer estimate keeps the buffer estimate as the upper design value, because H2S is lost to steel, to scavengers in the mud and to water during a short test: a low reading is weaker evidence than a high one.
8. **Screening.** Sour service: H2S mole fraction of the reservoir gas times reservoir pressure against 0.3 kPa (ISO 15156-2); for an oil the H2S fugacity is used. Gas limit: test-separator gas against a limit in mg/Nm³ (default 5), with the caveat that export gas depends on the process.

Wells without detectable H2S cannot be used as ratios. Keep them apart and check what the buffer gives for them: most should lie below the detection limit of detector tubes. Those that do not show how often H2S is far below the estimate.

## Python Usage Pattern

```python
from reservoir_h2s import buffered_h2s, calibrate, estimate_prospect, write_report

# 1 buffer value for every measured well (same generic fluid as for prospects)
for well in wells:                      # measured_ppm, res_T_C, res_P_bara, co2_molpct, formation, lat, lon
    well["model_ppm"] = buffered_h2s(
        well["res_T_C"], well["res_P_bara"], well["co2_molpct"], well.get("fluid_type", "gas")
    )["separator_gas_ppm"]

# 2 calibration: offset and scatter per depositional setting
registry = calibrate(wells, name="my wells", created="2026-01-01")
print(registry["classes"], registry["well_out"])

# 3 estimate for a prospect and the report
prospect = {"name": "Prospect X", "formation": "Garn Fm.", "res_T_C": 130.0,
            "res_P_bara": 500.0, "co2_molpct": 2.0, "lat": 64.6, "lon": 6.4}
result = estimate_prospect(prospect, registry, wells)
print(result["p10"], result["p50"], result["p90"], result["uncertainty_budget"])
files = write_report(result, "out", wells=wells)
```

`estimate_prospect(..., buffer=fn)` takes a replacement for the buffer, for example `correlation_buffer` (temperature correlation only) when NeqSim is not available, or a cached function. With a NeqSim source checkout, pass `jclass` to `buffered_h2s` through `functools.partial`.

## Validation Checklist

- [ ] The buffer value of each calibration well was calculated at the conditions of that well, on the same separator basis as the measurement.
- [ ] Laboratory H2S from steel sample bottles is not used as a measured value; readings at the rig are.
- [ ] Wells without detectable H2S are kept out of the ratios and reported separately.
- [ ] With each well left out, about 80 % of the wells fall inside the P10 to P90 range; if far fewer, the scatter is understated.
- [ ] The buffer agrees with the temperature correlation of Håland et al. (1999) within about a factor 1.3 between 100 and 180 °C at typical CO2.
- [ ] The uncertainty budget sums to 100 % and its largest term is named in the report.
- [ ] The report states that souring by injected seawater is not included.

## Common Mistakes

- **Comparing reservoir-fluid H2S with separator-gas readings.** For oils the two differ; flash first.
- **Using the temperature of a wireline formation tester.** It reads low, often by 10 °C or more, because the well is cooled by mud; 10 °C lowers the estimate by a factor 1.5. Use the reservoir temperature of a test or a PVT report, or a depth trend fitted on those.
- **Treating a non-detect as zero.** It is a value below the detection limit of the tube, often 0.2 to 1 ppm.
- **Trusting a low reading over the buffer.** H2S is consumed by steel, by zinc and iron compounds in the mud and by water. A reading above the buffer is more informative than one below.
- **Adjusting to the neighbours.** Test whether the deviations of nearby wells from the buffer are correlated before using analogues to shift the estimate. Where they are not, analogues inform only through their temperature and CO2.
- **Applying the buffer to carbonates with anhydrite** or to reservoirs under seawater injection.

## Limitations

- Screening level. The stated range covers the scatter of measured wells and the input uncertainty, not a reservoir that falls outside the buffer assemblage.
- The buffer is a model of pyrite with an Fe-carbonate of fixed activity (0.38 in `ReservoirH2SEstimator`). Mineralogy is not an input; depositional setting stands in for it.
- A generic fluid composition is used when none is given. The calibration absorbs the average difference; a very rich or very lean fluid adds scatter.
- Below about 70 °C the reactions are slow, H2S is small and often below detection; bacterial sulfate reduction can add H2S.
- Uplifted basins can hold more H2S than the buffer at present temperature, because the fluid records a hotter past.
- The gas-limit check is on test-separator gas, not export gas.

## References

- Håland, K., Barrufet, M.A., Rønningsen, H.P., Meisingset, K.K. (1999). An empirical correlation between reservoir temperature and the concentration of hydrogen sulfide. SPE-50763-MS. https://doi.org/10.2118/50763-MS
- Smith, J.T., Ehrenberg, S.N. (1989). Correlation of carbon dioxide abundance with temperature in clastic hydrocarbon reservoirs. Marine and Petroleum Geology 6, 129-135.
- Machel, H.G. (2001). Bacterial and thermochemical sulfate reduction in diagenetic settings. Sedimentary Geology 140, 143-175.
- ISO 15156-2:2020. Materials for use in H2S-containing environments in oil and gas production, Part 2.
