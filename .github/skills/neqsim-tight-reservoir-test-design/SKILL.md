---
name: neqsim-tight-reservoir-test-design
description: "Screening workflow for opening a tight (chalk, tight carbonate or tight sandstone) reservoir through an existing host: stimulated-horizontal-well rate and EUR model with NeqSim PVT, Arps extrapolation bias, production-test duration and sampling, acid or fracture flowback chemistry and compatibility (mixing scale, hydrate, souring, CO2), value of information of the test and the stage-gate decision criteria. USE WHEN: a task asks for a production test design, a drainage strategy or a stage-gate pipeline for a tight reservoir tied to an existing facility (Oseberg Shetland Chalk, Gullfaks Shetland/Lista, Valhall, Ekofisk analogues)."
---

# Tight-reservoir test design and stage-gate screening

Reference implementation: `task_solve/2026-10-09_ofc_rc7_shetland_chalk_further_development/step2_analysis/scripts` (`chalkmodel.py`, `econ.py`, `04_fluid_compat.py`, `05_economics_mc.py`). Copy the pattern, not the numbers.

## 1. Order of work

1. **Public evidence first** (Sodir resource reports, field news, EAGE/SPE abstracts), saved to `references/literature`. Sodir FactMaps wellbore layers (204, 205) answer "which wells found hydrocarbons in formation X"; page with `resultOffset` and filter client side (the WAF rejects `LIKE '%..%'` filters).
2. **Fluid from NeqSim**: reservoir T, p, Psat (`bubblePointPressureFlash`), compressibility from density at p and p+10 bar, viscosity, Bo and GOR from a 3-stage flash, TVP of the stock-tank oil. These feed the well model.
3. **Well model**: 1-D slab from the fracture face to the drainage boundary (area = zones x 2 faces x 2 xf x h x effectiveness, length L). Use finite differences with `scipy.linalg.solve_banded`, geometric time steps (dt/t about 5 %), pressure-dependent mobility and piecewise compressibility (single phase above Psat, gas expansion below). Validate against the closed-form series `q = A (k/mu) dP (2/L) sum exp(-(2n+1)^2 pi^2 eta t / 4L^2)` and the identity RF = c_t dP / S_o.
4. **Arps**: fit the first-year window with free b, then extrapolate. A boundary-dominated well fitted in its transient window gives b about 1.4-1.7 and overstates EUR 2-2.4x (b = 1.0: 1.4-1.8x). Never book EUR from a test decline without a boundary or volume check.
5. **Test duration** from the radius of investigation `r = sqrt(4 eta t)`, `eta = k / (mu phi c_t)`; single phase above Psat, 4x smaller eta below. A 6-month flow plus two build-ups sees 200-340 m for k = 0.1-0.3 mD; k = 0.05 mD needs well over a year.
6. **Flowback chemistry**: acid (15 % HCl) reacts with CaCO3: 1 t HCl gives 1.37 t CaCO3 dissolved, 0.6 t CO2 and a spent brine of about 20 wt % CaCl2 (about 88 g/L Ca). Use `AcidTreatmentSimulator` for the cross-check and `BrineMixingScaleEvaluator` for mixing curves (formation water + seawater, + produced water, spent acid + produced water). BaSO4 is the first scale to watch when sulphate meets a barium-bearing water.
7. **Hydrate**: SRK-CPA with the associated gas, lumped C7+ and free water; Wax: the Pedersen model needs an n-paraffin distribution, P/A pseudo-components do not converge, so report wax as a lab-data gap.
8. **Souring**: bound H2S in gas by the fraction of injected sulphate reduced; SRB activity stops above about 80-90 C, so souring is a cooled-zone risk around injectors.
9. **Value**: annual cash flow with the 2022 NCS tax (78 % marginal, direct expensing), Monte Carlo with common random numbers, tornado, break-even EUR per well (scale the well curve until NPV = 0), break-even oil price.
10. **Value of the test**: bin the test-estimated EUR (lognormal error), take the best concept per bin, compare "test then decide" against "commit blind" and "do nothing"; sweep the measurement error. Make the test measure the candidate completion, not the old one.

## 2. Pitfalls seen

- Trapezoid integration of a decaying implicit-Euler rate adds about 12 % to cumulative oil; use end-of-step rate x dt so the scheme conserves mass.
- Water injection modelled as a pressure boundary plus a separate imbibition term double counts oil. Treat the flux as liquid, split by water cut, and cap cumulative oil at an ultimate recovery factor.
- Host cessation (Centuries) cuts the long tail of a tight reservoir; always truncate at the host oil cessation year and report the lost tail.
- A gate scan that picks the best concept per sample is clairvoyant; fix the concept or use bin means.
- Proppant-frac cases need a stage-success factor; without it the completion looks free.
- EUR of a stimulated tight well is volume-limited, not rate-limited. At fixed stimulated area, EUR moved only about 7 % between 0.1 and 0.4 mD but doubled between 200 and 400 m connected half-width, while the one-year rate moved by 10-25 %. A test that reports only rate cannot rank wells; design it to observe the boundary (build-up, end of linear flow) and give the gate as a connected half-width plus an EUR.
- The value-of-information signal must be the same quantity as the decision variable. In the Oseberg case the signal was the propped test-well EUR; a stale comment saying it was the acid-frac EUR nearly put the gate on the wrong basis. State the basis in the code and in the gate table.
- Check the host plan against the Shetland rates year by year before claiming "not binding": a gas plan of 6 MSm3/d swallows 0.2 MSm3/d, but an oil plan of 1 300 Sm3/d does not swallow 1 000 Sm3/d.

## 3. Hand-offs

`neqsim-flow-assurance` (hydrate, wax, scale), `neqsim-production-chemistry` (inhibitor dose, scavenger), `neqsim-subsea-and-wells` (well cost), `neqsim-field-economics` (tax), `neqsim-process-safety` (barriers during stimulation), `neqsim-stid-retriever` (OSB P&IDs: `neqsim fetch-docs <task> --inst OSB --doc-nos ...`).

