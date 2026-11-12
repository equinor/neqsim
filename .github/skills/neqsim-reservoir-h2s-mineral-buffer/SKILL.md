---
name: neqsim-reservoir-h2s-mineral-buffer
calculation_basis: "neqsim-java"
version: "0.1.0"
description: "Original (pre-souring) H2S in reservoir fluids from mineral buffering: pyrite + Fe-carbonate with the CH4/CO2 of the fluid fixes the H2S fugacity, which explains the exponential rise of H2S with reservoir temperature (Haaland 1999 correlation, about 54 kJ/mol). USE WHEN: a task asks what H2S to expect in a new reservoir or discovery, why H2S rises with temperature, whether a measured H2S is natural or souring, or which mineral reaction controls H2S."
last_verified: "2026-11-12"
requires:
  python_packages: ["neqsim"]
  java_packages: ["neqsim.thermo.util.mineral (SolidGasReactionEquilibrium, MineralH2SBuffers, ReservoirH2SEstimator; equinor/neqsim#4945)"]
  env: []
  network: []
---

# Original reservoir H2S from mineral buffering

In clastic reservoirs with excess iron minerals, the H2S content of the reservoir fluid is set by equilibrium with the stable Fe minerals, not by how much sulfur was generated. Pyrite and an Fe-bearing carbonate (siderite or ankerite) buffer the H2S fugacity with the CH4/CO2 of the fluid as redox pair. The reaction enthalpy (about 55 kJ per mol H2S) is the same as the apparent enthalpy of the Håland et al. (1999) correlation of H2S versus reservoir temperature on the Norwegian shelf, which is why H2S doubles about every 18 °C.

## When to Use

- Expected original H2S in a new well, discovery or field before production (design basis, materials screening, scavenger sizing).
- Explaining why H2S in sandstone reservoirs rises from about 0.1 ppm at 50 °C to about 30 ppm at 190 °C.
- Deciding whether a measured H2S is the natural level or souring from injection water: compare with the buffer at the same temperature, pressure and CO2.
- Testing a proposed mineral reaction (for example FeS + H2O + CO2 = FeCO3 + H2S) against the phase rule.

Not for: production souring from seawater injection (use `neqsim-reservoir-souring-correlation`), separator-gas to wellstream conversion (use `neqsim-h2s-phase-partitioning`), or sour carbonate reservoirs with active thermochemical sulfate reduction (percent-level H2S, kinetically controlled).

## Inputs

- Reservoir-fluid composition (mol %, with C7+ as a TBP fraction: mole fraction, molar mass, density); CO2 and methane are required.
- Reservoir temperature (°C) and pressure (bara).
- Optional: formation-water activity (about 0.93-0.99 for brines), Fe-carbonate activity (1 for pure siderite; default 0.38 calibrated on the Norwegian shelf), whether sulfate minerals are present.

## Outputs

- H2S in the reservoir fluid (ppm mol) and its fugacity (bar); the equilibrated fluid for flashing to separator conditions.
- Phase-rule margins: log10 CO2-fugacity margin over the siderite-magnetite boundary and log10 H2S margin over the pyrite-pyrrhotite boundary, plus `isBufferConsistent()`.
- Warnings: extrapolated heat capacity, metastable 1-bar water reference, low-temperature kinetics, possible thermochemical sulfate reduction.

## Engineering Method

```
4 FeS2 + CH4 + 3 CO2 + 6 H2O = 4 FeCO3 + 8 H2S
8 ln f(H2S) = ln K(T,P) + ln f(CH4) + 3 ln f(CO2) + 6 ln a(H2O) - 4 ln a(FeCO3)
y(H2S) = f(H2S) / (phi(H2S) P)
```

- ln K(T) from phase-specific thermochemistry (USGS Bulletin 2131 minerals, NIST-JANAF gases) with condensed-phase pressure correction (constant solid volumes, IAPWS-IF97 water). Gas non-ideality enters only through the EOS fugacities of the actual fluid.
- Slope: at fixed CH4 and CO2 fugacities, d ln f(H2S)/d(1/T) = -dH/(8R), dH/8 about 55 kJ/mol. The digitized Håland curve is ln ppm = 17.58 - 6478/T (70-190 °C).
- CO2 is a second control: at given temperature y(H2S) is proportional to f(CO2)^(3/8). On the Norwegian shelf CO2 rises from about 0.3 % at 80 °C to about 4 % at 160 °C (Smith and Ehrenberg 1989). Split of the rise 80 to 160 °C (x55): K(T) +3.5, CO2 +1.6, pressure -0.9, phi(H2S) -0.3 (ln units).
- Phase rule: at shelf CO2 levels siderite is stable versus magnetite (median 1.6 log units margin) and pyrite versus pyrrhotite, so pyrite + Fe-carbonate is the buffering pair. Reactions with pyrrhotite or magnetite are metastable; FeS + H2O + CO2 = FeCO3 + H2S gives about 10x too much H2S and too flat a trend.
- Sulfate reduction supplies sulfur but its activation energies (120-210 kJ/mol) are far too steep to set the ppm baseline; with iron in excess the minerals take up the surplus.

## Python Usage Pattern

```python
from neqsim import jneqsim
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Estimator = jneqsim.thermo.util.mineral.ReservoirH2SEstimator

fluid = SystemSrkEos(273.15 + 140.0, 550.0)   # reservoir T [K] and P [bara]
for name, z in [("CO2", 0.03), ("methane", 0.80), ("ethane", 0.08), ("propane", 0.04), ("n-hexane", 0.05)]:
    fluid.addComponent(name, z)
fluid.setMixingRule("classic")

est = Estimator()                 # Fe-carbonate activity 0.38 (calibrated to the Haaland curve)
est.setWaterActivity(0.97)        # formation brine
e = est.estimate(fluid)
print(e.getH2SPpm(), e.isBufferConsistent(), list(e.getWarnings()))
```

Other buffers: `MineralH2SBuffers.all()` with `SolidGasReactionEquilibrium.solveForGas(fluid, "H2S")`; `setFeCarbonateActivity(1.0)` gives the pure-siderite value (0.61x the correlation on shelf fluids).

Palaeothermometer: `est.equilibrationTemperature(fluid, measured_ppm)` returns the temperature (K) at which the buffer gives the measured H2S at the fluid's pressure and fixed composition (`NaN` outside 20-250 °C; an overload takes bounds). Compare it with the present temperature to test burial memory. At fixed composition, doubling H2S is about +20 K at 140 °C, twice the step of the shelf-wide apparent enthalpy, because shelf CO2 also rises with depth.

Core mineralogy: the buffer needs pyrite and an Fe-carbonate (siderite or ankerite) to be present in the flow unit; their amount and the siderite/ankerite ratio do not change the value (phase rule). Use core XRD and thin sections as a presence screen. Count dolomite separately, because XRD does not resolve ankerite from Fe-poor red-bed dolocrete.

## Depletion and pressure dependence

At fixed composition the stoichiometry sets the ideal-gas pressure exponent beta in y(H2S) ~ P^beta: pyrite-siderite -0.5, pyrite-pyrrhotite, pyrite-magnetite and pyrrhotite-magnetite -1, pyrrhotite-siderite 0, anhydrite-TSR 0. With the real-gas fugacities of a reservoir gas at 400-500 bar the pyrite-siderite value is only about -0.15 (H2S rises x1.28 from 480 to 85 bar at 145 C, not x2.4), the other three H2-type buffers give about -0.55 and pyrrhotite-siderite +0.24. The fugacity coefficient of H2S, the CO2 and CH4 fugacities and the solid-volume term cancel about 85 % of the ideal-gas dilution effect. Judge a depletion trend with these effective exponents, never the ideal-gas ones.

Workflow used on the Asgard H and J wells (task 2026-11-12):

1. Build the gas-condensate fluid (plus fraction split, molar mass tuned to the measured dew pressure) and run a constant-volume depletion with `TPflash` stages; at each stage clone the template fluid, `setMolarComposition(gasMoles)`, set T and P and call `SolidGasReactionEquilibrium.solveForGas(fluid, "H2S")` or `ReservoirH2SEstimator.estimate`.
2. Compare ratios, not levels: the level carries about a factor 3 from thermochemical data, the ratio between two pressures does not.
3. Use CO2 and C1 of later gas samples as tracers against the depletion path: CO2 falling where depletion raises it means lean or CO2-poor gas (injection, recycle) or a CO2 sink. The equilibrium buffer then lowers H2S by about (CO2 ratio)^(3/8); frozen H2S is diluted in proportion to the lean-gas share. The two limits bracket the data and tell whether the buffer re-equilibrates.
4. Test hypotheses jointly: pressure exponent, level, 2003-2024 ratio with a cluster bootstrap by test campaign, and a join of CO2 to every H2S reading (slope of ln H2S on ln CO2 near 0.375 in equilibrium).

Code pitfalls: `getz()` of plus-fraction pseudo-components is zero until the fluid is initialised (use `getNumberOfmoles()`); pseudo-components are not in the database, so clone the template fluid instead of re-adding components; `dewPointPressureFlash` started at 300 bar can return a wrong root, so bisect the dew pressure on the phase count of `TPflash`.

## Validation Checklist

- [ ] Fluid composition is a reservoir fluid at reservoir T and P (not separator gas); CO2 and methane present.
- [ ] `isBufferConsistent()` is true (siderite and pyrite stable); otherwise the buffer does not apply.
- [ ] Temperature above about 70 °C, or the result is treated as an upper bound.
- [ ] Compared with measured wellsite/DST H2S converted to the same basis (flash the equilibrated fluid to separator conditions).
- [ ] Uncertainty reported: siderite dfH (±5.5 kJ/mol) gives about x2.3 per standard deviation; P10/P90 rather than a single value.

## Common Mistakes

| Mistake | Correction |
| --- | --- |
| Using laboratory PVT H2S as data | ppm H2S is lost to cylinder steel; lab compositions usually report 0. Use wellsite measurements. |
| Comparing reservoir-fluid ppm with separator-gas ppm | Flash the equilibrated fluid to separator conditions, or convert with `neqsim-h2s-phase-partitioning`. |
| Writing the buffer with a fixed H2 fugacity | In a reservoir H2 is set by CH4/CO2/H2O; write the buffer with CH4 and CO2 (pyrite-pyrrhotite then has about 112 kJ per mol H2S, not 49). |
| Ignoring CO2 | At a given temperature H2S scales with f(CO2)^(3/8); CO2-poor reservoirs sit below the temperature trend. |
| Fitting souring correlations to total H2S | Subtract the buffer baseline first. |
| Expecting more siderite in core to mean less H2S | Presence, not amount, sets the value; siderite-bearing wells give the calibrated activity 0.38 directly. |
| Applying the buffer to continental red beds | Triassic red beds poor in pyrite and siderite measured 9-27x below the buffer; use it as a ceiling there. |
| Expecting H2S to double on depletion from 500 to 100 bar | Real-gas fugacities cut the effective exponent to about -0.15 (x1.3); a flat series is the expected result |
| Predicting uplifted reservoirs at maximum burial temperature | The buffer re-equilibrates on cooling: uplifted wells kept only about +6 °C (about x1.4); use present temperature. |

## Limitations

- Calibrated for clastic reservoirs with pyrite and Fe-carbonate; carbonates with anhydrite and active TSR can exceed it by orders of magnitude.
- Fe-carbonate activity is calibrated (0.38), not measured; petrography per field narrows it.
- Troilite stands in for pyrrhotite; no mackinawite, Fe-chlorite or carbonate solid-solution model yet.
- Constant solid molar volumes; below about 70 °C equilibrium is slow.
- Validated on Norwegian shelf data (Håland curve, DST and well-report H2S for about 80 wellbores): median predicted/measured about 1.0, log10 RMSE 0.2-0.3; measured CO2 exponent at given temperature 0.48 (95 % CI 0.26-0.70) against 0.375 predicted. Marine and paralic sandstones alone: bias 0.00, RMSE 0.17 log10 (factor 1.5).
- Siderite persists to above 150 °C in shelf cores although ankerite grows with burial; the full siderite-to-ankerite shift would change H2S by at most about 1.2x.

## References

- Håland, K., Barrufet, M.A., Rønningsen, H.P., Meisingset, K.K. (1999). An empirical correlation between reservoir temperature and the concentration of hydrogen sulfide. SPE-50763-MS. https://doi.org/10.2118/50763-MS
- Smith, J.T., Ehrenberg, S.N. (1989). Correlation of carbon dioxide abundance with temperature in clastic hydrocarbon reservoirs: relationship to inorganic chemical equilibrium. Marine and Petroleum Geology 6, 129-135.
- Robie, R.A., Hemingway, B.S. (1995). USGS Bulletin 2131. Chase, M.W. (1998). NIST-JANAF Thermochemical Tables.
- Machel, H.G. (2001). Bacterial and thermochemical sulfate reduction in diagenetic settings. Sedimentary Geology 140, 143-175.