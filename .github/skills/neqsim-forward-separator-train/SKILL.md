---
name: neqsim-forward-separator-train
calculation_basis: "neqsim-java"
version: "0.1.0"
description: "Run one fluid (E300 or composition) through a forward-only, no-recycle separator train (HP, MP, LP, stock tank) like PVTsim/ECL300 and return GOR, Bo, STO API and gas per stage; also fit ONE shared stage P/T set to reservoir-simulation oil and gas rates over many fluids. USE WHEN: 'kjør dette for et gitt fluid', surface GOR/Bo/STO for an E300 fluid, or fitting the pseudo-process P/T per tank/scrubber of a reservoir simulation."
last_verified: "2026-11-08"
requires:
  python_packages: [neqsim]
  java_packages: []
  env: []
  network: []
---

# Forward Separator Train for One Fluid

A forward train has no recycle: liquid cascades from stage to stage and every gas
stream leaves the system. For a fixed composition and fixed stage pressures and
temperatures the result is therefore unique and needs no iteration. This is the
same separator test PVTsim and ECL300 run, so results can be compared directly.

The skill runs **one** fluid. To study variation, call it once per fluid
(see "Many fluids" below).

## When to Use

- A fluid is given as an E300 file (or a mole-fraction table) and surface results are needed.
- GOR, Bo, stock-tank oil density/API, or gas rate and gas MW per stage are requested.
- A PVTsim or ECL300 separator-test number must be reproduced or sanity-checked.

Do not use it for trains with recycles or with feedback from compressors or
coolers into earlier stages; build a `ProcessSystem` with recycles instead.

## Inputs

| Input | Default | Notes |
| --- | --- | --- |
| E300 file or `--composition` JSON | required | Exactly one. JSON: `{"eos":"srk","components":{"methane":0.7,...}}` (`srk` or `pr`, library component names). |
| `--feed-p` | 250 bara | Set to reservoir (or wellstream) pressure. |
| `--feed-t` | 100 degC | Set to reservoir (or wellstream) temperature. |
| `--stages` | `70:60,20:50,2:40` | `bara:degC` per stage, strictly falling pressure. Stock tank at 15 degC and 1.01325 bara is always appended. |
| `--flow` | 100000 kg/hr | Only scales absolute rates; GOR and Bo do not depend on it. |
| `--out` | none | Writes the result JSON. |

Ask the user for the stage P/T and feed conditions if they are not given. The
defaults are placeholders, not a design.

## Engineering Method

Per stage the script applies a `ThrottlingValve` to the stage pressure, a
`Heater` to the stage temperature and a `Separator`; the liquid outlet feeds the
next stage and each gas outlet leaves the system. A stock-tank stage at 15 degC
and 1.01325 bara is always appended. GOR and Bo follow from the stage gas
volumes and the stock-tank oil volume. For the regression of one shared P/T set
over many fluids see "Fit ONE P/T set to reservoir-simulation rates".

## Python Usage Pattern

```
<python> scripts/run_forward_separator.py fluid.e300 --feed-p 300 --feed-t 110 \
    --stages "70:60,20:50,2:40" --out result.json
```

`<python>` is the interpreter with the `neqsim` package. The script builds, per
stage: `ThrottlingValve` to the stage pressure, `Heater` to the stage
temperature, `Separator`; the liquid outlet feeds the next stage.

## Outputs (JSON)

- `GOR_Sm3_Sm3`: total gas of all stages including stock tank, per Sm3 of stock-tank oil (gas at 15 degC, 1.01325 bara, ideal-gas conversion).
- `Bo_m3_Sm3`: feed volume at feed P/T divided by stock-tank oil volume.
- `STO_density_kgm3`, `STO_API`, `STO_Sm3_h`: stock-tank oil.
- `stages[]`: `P_bara`, `T_C`, `gas_Sm3_h`, `gas_MW_gmol`, `liquid_kg_h` per stage.
- `feed_n_phases`, `mass_balance_rel_err`, `warnings[]`.

## Validation Checklist

Do these before reporting a result.

1. `mass_balance_rel_err` below 1e-6.
2. `feed_n_phases`: if 2 at feed P/T, the feed is not a single-phase reservoir
   fluid (feed below saturation, or feed P/T wrong). Bo then describes the mixed
   stream; say so, or correct the feed conditions.
3. GOR and API are physically plausible for the fluid type (black oil about
   50-300 Sm3/Sm3, 25-40 API; volatile oil/condensate much higher GOR, higher API).
4. For a PVTsim/ECL300 comparison, confirm the same stage P/T, the same standard
   conditions (15 degC, 1.01325 bara) and the same EOS parameters (volume shift,
   kij) before attributing differences to the model.

## Many fluids

The user normally runs variations themselves: loop the script over several E300
files or composition JSONs and collect the `GOR_Sm3_Sm3`, `Bo_m3_Sm3` and
`STO_API` fields into a table. `scripts/make_variations.py base.e300 --n 30
--outdir d` writes Latin-hypercube variations (+-20 % on each C1-C6 component, on
the C7+ amount and on a C7+ tilt, keeping the pseudo-component EOS parameters)
plus a `manifest.csv`. Keep stage P/T and feed P/T fixed across cases so the
cases are comparable.

## Fit ONE P/T set to reservoir-simulation rates

A reservoir simulator applies one pseudo process (the same stage P/T) to every
fluid it produces, from all formations. To reproduce its produced oil and gas
rate, regress a single set of stage pressures and temperatures over all fluids.
Only oil and gas rate matter (hence GOR); Bo and densities are not used.

Manifest CSV, one row per fluid: `fluid` (E300 path), `oil_Sm3_h`, `gas_Sm3_h`
(targets from the simulator), optional `feed_kmol_h`, `feed_P_bara`, `feed_T_C`,
`weight`, `formation` (label for the error report only; all formations share the
one P/T set).

```
<python> scripts/fit_separator_pt.py manifest.csv --x0 "80:70,25:55,3:45" \
    --feed-p 300 --feed-t 110 --p-max 120 --out fit.json
```

- Unknowns: P and T of every stage (stage count = number of `--x0` stages; stock
  tank fixed at 15 degC, 1.01325 bara). Pressure falls along the train by construction.
- Residuals: ln(model/target) for oil and for gas per fluid, so errors are relative.
- Output `fit.json` and `*_per_fluid.csv`: fitted stages, per-fluid target vs model
  (oil, gas, GOR, % error), `formation_report`, parameter standard errors,
  Jacobian condition number, warnings.

**Uniqueness is not guaranteed.** Oil and gas rate alone constrain only a
combination of the stage conditions. Tested on synthetic targets (known train
60:55, 15:45, 1.8:35, 1 % noise): fluids varied +-20 % around one base gave a
pure data fit (`--prior-weight 0`) with huge standard errors that drifted to a
physically odd set at the same noise-level match. A weak prior toward `--x0`
(`--prior-weight`, default 0.02) selects one reproducible solution along the flat
valley; with three different base fluids the match was at the noise level
(about 1 % GOR) for every formation, and the standard errors shrank (about 7-21 degC,
a factor 1.8 in pressure). So:

1. Always report the fitted set together with its `std_error`s and the match quality;
   do not present poorly determined stages as measured values.
2. Use fluids from as many formations with different volatility as possible; that
   is what makes P/T better determined.
3. Set `--x0` to the simulator's own pseudo-process (or a typical train); the prior
   keeps the answer near it. Fix a stage you know by narrowing `--t-min/--t-max`
   and `--p-max`.
4. If `formation_report` shows a formation biased by more than a few percent GOR,
   one P/T set does not fit all formations; say so instead of tuning further.
5. A run on 30 fluids and 3 stages takes about 1 minute.

## Common Mistakes

- Using the placeholder feed and stage defaults as if they were a design; ask for the real P/T.
- Running a feed that is two-phase at feed P/T and reading Bo as a reservoir-fluid value.
- Comparing with PVTsim or ECL300 using different stage P/T, standard conditions or EOS parameters.
- Presenting poorly determined fitted stage P/T values, without their standard errors, as measured data.

## Limitations

- Equilibrium stages only; no entrainment, carry-over or separation efficiency.
- Water is not treated specially; an aqueous phase in the liquid outlet is counted as liquid.
- Stage gas rates are ideal-gas standard volumes, which can differ by a fraction of a percent from PVTsim's real-gas standard volumes.

## References

- Whitson, C.H. and Brule, M.R., Phase Behavior, SPE Monograph 20 (2000): separator tests and Bo/GOR definitions.
- NeqSim: ThrottlingValve, Heater and Separator in neqsim.process.equipment; E300 reading via the e300-fluid tools.

