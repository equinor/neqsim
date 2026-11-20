---
name: neqsim-reservoir-mercury-estimation
calculation_basis: "neqsim-java"
version: "0.1.0"
description: "Screening estimate of elemental mercury in the gas of a reservoir, prospect or discovery: shrinkage model calibrated on tested wells (reservoir age class, province, temperature), analogue tests, NeqSim mineral limits (cinnabar and liquid mercury) and a report with P10/P50/P90. USE WHEN: asked what mercury to expect from a new reservoir, how certain it is, whether a measured value is plausible, or which mineral reaction limits mercury."
last_verified: "2026-11-20"
requires:
  python_packages: ["numpy", "neqsim (for the mineral limits)", "matplotlib and python-docx (for the report)"]
  java_packages: ["neqsim.thermo.util.mineral (MineralMercuryBuffers, ReservoirMercuryEstimator)", "neqsim.thermo.util.mercury (MercuryFluidConverter, MercuryPhaseEquilibrium)"]
  env: []
  network: []
---

# Mercury in reservoir gas: estimate, limits and uncertainty

Elemental mercury (Hg0) in produced gas decides whether a mercury removal unit is needed and where. Unlike H2S, mercury in a reservoir is not held at a mineral equilibrium: measured values lie a factor 100 to a million below saturation with cinnabar (HgS), neighbouring wells differ by a factor ten, and no single reservoir property predicts it. What can be said in advance is an order of magnitude with an honest uncertainty, bounded by mineral limits. This skill does that and writes the report.

## When to Use

- A prospect or discovery needs a mercury figure for design, material selection or the choice of gas route before a sample exists.
- A measured value must be judged: is it plausible, is it likely too low, how far can production differ from it.
- A mercury data set from tested wells must be turned into a calibrated model with cross-validated error.
- The upper limit of mercury at reservoir conditions is needed (worst case).

Not for: mercury distribution through a process (use `neqsim-mercury-distribution-study`), mercury removal bed design, or speciation (organic, ionic, particulate mercury).

## Inputs

- Prospect: name, position, reservoir formation or epoch, reservoir temperature and pressure, fluid type, CO2 content. Missing items are allowed and become data gaps.
- Gas readings from the discovery itself, if any (microgram per Sm3).
- A calibration registry and the table of tested intervals it was fitted on. The data are not part of this skill: bring a registry built from your own measurements with `ShrinkageModel` and `cross_validate`, or one supplied by a data owner.

## Outputs

- `estimate_prospect(...)`: best estimate, P10/P50/P90, probability above 1, 10, 100 and 1000 µg/Sm3, a rounded range for design, the contribution of each predictor, analogue tests, limits, reasoning, accuracy statements and data gaps (schema `mercury_prospect_estimate.v1`).
- `mercury_limits(...)`: cinnabar, trace-sulfide and liquid-mercury limits in separator gas, buffered H2S, fugacity coefficient of mercury.
- `write_report(...)`: JSON, Markdown and Word report with two figures.
- `ShrinkageModel`, `cross_validate`, `group_statistics`: calibration tools.

## Engineering Method

Mineral limits (Java, `ReservoirMercuryEstimator`). Two condensed phases can hold mercury back:

```
4 HgS + CH4 + 2 H2O = 4 Hg + 4 H2S + CO2        4 ln f(Hg) = ln K + ln f(CH4) + 2 ln a(H2O) + 4 ln a(HgS) - 4 ln f(H2S) - ln f(CO2)
8 HgS + 4 FeCO3 + CH4 = 8 Hg + 4 FeS2 + 5 CO2 + 2 H2O     (H2S fixed by the pyrite + Fe-carbonate buffer)
Hg(l) = Hg(g)                                    f(Hg) = vapour pressure x Poynting factor
```

- ln K from USGS Bulletin 2131 (cinnabar, metacinnabar), CODATA (mercury) and NIST-JANAF; the liquid and gas data reproduce the vapour pressure of Huber et al. (2006) within 1 % from 0 to 250 °C.
- CH4, CO2 and H2S fugacities from the equation of state of the fluid; mercury as a dilute solute (fugacity coefficient at trace content, summed over the phases). Use UMR-PRU-MC (`MercuryFluidConverter.toUmrPruMc`), which has mercury parameters.
- Pure cinnabar allows about 300 µg/Sm3 at 50 °C and 40 000 at 110 °C in a gas reservoir: a factor 20 per 30 °C. Above about 130 °C the liquid-mercury limit is the lower one.
- An HgS activity of 0.003 (mercury as a trace in pyrite or other sulfides; Bryndzia et al. 2022 found the same value on a global data set) follows the highest measured values and is the practical worst case. It is exceeded by a few percent of tests, so it is not a hard cap.
- Measured mercury does not follow the temperature slope of the limit (data about 0.008 log10 per °C, limit 0.022): mercury is supply limited, not buffered.

Statistical estimate (Python).

- Target: log10 of the highest gas reading per test. Gas readings can be too low (adsorption, short flow, methanol or glycol), not too high.
- `ShrinkageModel`: ridge regression with indicator columns for categories (reservoir age class, province) and standardised numerics (temperature). The ridge penalty shrinks a group with few tests towards the overall mean; an unseen group predicts the mean.
- Error: leave-one-well-out root mean square error (all tests of a well left out together). This, not the fit residual, is the uncertainty of a new well.
- Production gas follows the highest reading found in a discovery. Without a measurement the estimate is shifted from "typical test" to "highest reading in the discovery" by the mean offset observed in the calibration set, with its own standard deviation.
- With a measurement in the discovery the highest gas reading is the best estimate and the within-well scatter gives the range.
- Range for design: from the P50 (rounded down) to the P90 (rounded to the nearest) on the series 1, 2, 3, 5 times a power of ten, capped at the liquid-mercury limit. With a measurement: a third of the highest reading (rounded down) to three times it (rounded to the nearest).
- `interval_coverage(errors, sd)` checks the stated range: the share of left-out tests inside, above and below P10 to P90 (0.8, 0.1 and 0.1 when the range is honest). Store it in the registry as `interval_coverage`; the report then states it.

## Python Usage Pattern

```python
from neqsim import jneqsim
from reservoir_mercury import estimate_prospect, load_registry, mercury_limits, write_report

# 1. Mineral limits with the Java estimator directly
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 110.0, 350.0)
for name, z in [("CO2", 2.0), ("methane", 80.0), ("ethane", 8.0), ("propane", 4.0), ("n-hexane", 5.0)]:
    fluid.addComponent(name, z)
fluid.setMixingRule("classic")
fluid = jneqsim.thermo.util.mercury.MercuryFluidConverter.toUmrPruMc(fluid, 1.0, "ug/Sm3")
est = jneqsim.thermo.util.mineral.ReservoirMercuryEstimator()
est.setMercurySulfideActivity(0.003)            # trace mercury in sulfide; 1.0 for pure cinnabar
e = est.estimate(fluid)
print(e.getSulfideLimit("ug/Sm3"), e.getLiquidLimit("ug/Sm3"), e.getControllingPhase(), e.getH2SPpm())
print(e.getSulfideSaturationIndex())            # log10(content / limit) of the 1 ug/Sm3 put in the fluid

# 2. Limits in separator gas and the prospect estimate
limits = mercury_limits(150.0, 500.0, fluid_type="gas", co2_molpct=3.0)
registry = load_registry("mercury_registry.json")          # your calibration
prospect = {"name": "Alpha", "lat": 64.9, "lon": 6.9, "formation": "Tilje", "res_T_C": 150.0, "res_P_bara": 500.0}
result = estimate_prospect(prospect, registry, tests=test_rows, limits=limits, measurements=None)
print(result["best_estimate"], result["p10"], result["p90"], result["recommended_range"])
write_report(result, "report_dir", registry=registry, tests=test_rows)
```

Calibration from your own tests: `cross_validate(rows, "y", "well", ("source_class", "province"), ("res_T_C",))`, then `ShrinkageModel(...).fit(rows, "y")`, and store `model.to_dict()` with `cv_sd_log10`, `discovery_level`, `within_well_sd_log10`, `within_field_sd_log10`, `distribution`, `n_tests`, `n_wells` and `cross_validation` in the registry. `classify_formation("Tilje Fm.")` gives group, epoch and the reservoir age class for Norwegian shelf formations.

Command line: `python -m reservoir_mercury estimate --prospect p.json --registry r.json --tests t.csv --out DIR`.

## Validation Checklist

- [ ] The estimate is reported as a range (P10 to P90 or the range for design), never as one number alone.
- [ ] Highest gas reading per test used; liquid and water samples not mixed into the gas basis.
- [ ] Cross-validation leaves out whole wells; the no-predictor score is quoted beside the model score.
- [ ] Limits calculated at reservoir temperature and pressure with UMR-PRU-MC and expressed in separator gas.
- [ ] Trace-sulfide limit described as worst case, pure cinnabar and liquid mercury as hard upper bounds.
- [ ] Data gaps listed (formation, temperature, pressure, analogues within 100 km).
- [ ] A measurement below the P10 of the statistical estimate is questioned before it is used.

## Common Mistakes

| Mistake | Correction |
| --- | --- |
| Predicting mercury from temperature alone | Temperature alone is a weak predictor; use it with the reservoir age class and quote the cross-validated error. |
| Treating the cinnabar limit as the expected value | Pure cinnabar overpredicts measured mercury by up to three orders of magnitude (Bryndzia et al., 2022); the limit is a ceiling. |
| Using neighbouring wells as the estimate | Proximity is a poor indicator (Sandengen, 2024). Check it on your own data with the scatter inside a field from `group_statistics`; neighbours give the level only. |
| Using the median of the readings of a test | Readings are biased low; use the highest gas reading. |
| Reading the fit residual as the uncertainty | Use the leave-one-well-out error. |
| Comparing reservoir-fluid mole fraction with separator-gas µg/Sm3 | Flash to separator conditions with `MercuryPhaseEquilibrium` (done in `mercury_limits`). |
| Expecting cinnabar and liquid mercury to coexist with the fluid | Above about 130 °C liquid mercury is the lower limit and the sulfide is not stable. |

## Limitations

- Screening. Expect an error of about one order of magnitude for a new well: mercury is reported as not predictable from reservoir data (Sandengen, 2024), and a model of this kind moves the median by reservoir age and temperature while the range stays wide. On a governed shelf-scale calibration (about 100 tests, 2026-11) the gain over no predictor was small and the stated P10 to P90 range held about four in five left-out tests; the figures are with the owner of that data. Quote the leave-one-well-out error and the interval coverage of your own calibration, with its date and its number of tests and wells.
- No reservoir transport, source-rock kinetics or sorption model; the reservoir age class stands in for the source.
- Thermochemistry of HgS carries about 0.5 log10 (one standard deviation) in the limit; the heat capacity of metacinnabar is taken equal to cinnabar.
- Elemental mercury only; particulate HgS and organic mercury in liquids are outside the method.
- The stratigraphy table covers Norwegian shelf formations; give the epoch directly elsewhere.

## References

- Sandengen, K. (2024). Mercury on the Norwegian Continental Shelf (NCS). What value to use for design? Geoenergy Science and Engineering 242, 213263. https://doi.org/10.1016/j.geoen.2024.213263
- Bryndzia, L.T., Burgess, J.M., Bourdet, J. (2022). Predicting the solubility of mercury in hydrocarbons. SPE Journal, SPE-212271-PA. https://doi.org/10.2118/212271-PA
- Robie, R.A., Hemingway, B.S. (1995). USGS Bulletin 2131. Cox, J.D., Wagman, D.D., Medvedev, V.A. (1989). CODATA key values for thermodynamics.
- Huber, M.L., Laesecke, A., Friend, D.G. (2006). Correlation for the vapor pressure of mercury. Ind. Eng. Chem. Res. 45, 7351-7361. https://doi.org/10.1021/ie060560s
- Koulocheris, V., Louli, V., Panteli, E., Skouras, S., Voutsas, E. (2018). Modelling of elemental mercury solubility in natural gas components. Fuel 233, 558-564.
- Companion skills: `neqsim-reservoir-h2s-mineral-buffer` (H2S buffer used in the sulfide limit), `neqsim-mercury-distribution-study` (mercury through the process).
