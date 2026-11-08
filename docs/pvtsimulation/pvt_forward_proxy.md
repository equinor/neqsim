---
title: "PVT forward-proxy surface process"
description: "Calibrate one common set of separator and scrubber pressures and temperatures for a strictly forward surface process that reproduces the stabilized oil and gas rates of a detailed NeqSim process with recycle, over a representative fluid ensemble, for PVT-table generation in Eclipse 300, PVTsim, FluidMagic, OLGA and black-oil workflows."
keywords: "PVT proxy, forward separator train, surface process, scrubber, recycle, PVT tables, Eclipse 300, SEPCOND, PVTsim, FluidMagic, OLGA, GOR, dry gas, calibration, pvt-proxy"
---

Reservoir and PVT tools such as Eclipse 300, PVTsim, FluidMagic and OLGA PVT
workflows describe the surface process as a forward sequence of separator
stages. They cannot represent recycle or reflux. A real facility, and its
detailed NeqSim model, has compressor scrubbers, condensate return to earlier
stages and other feedback.

`neqsim pvt-proxy` closes that gap. It runs a representative set of reservoir
fluids through the detailed NeqSim process (the reference). It then fits
**one** set of stage and scrubber pressures and temperatures for a strictly
forward proxy, so that the proxy gives approximately the same standard oil and
gas rates for every fluid in the set.

The proxy defines the surface separation used when PVT tables are generated. It
is not a method for allocating molar production from measured rates, and it
does not replace the production workflow (Eclipse profiles, FluidMagic, OLGA,
NeqSim process).

Use [@pvt-forward-proxy](../../.github/agents/pvt-forward-proxy.agent.md) with
the [workflow skill](../../.github/skills/neqsim-pvt-forward-proxy/SKILL.md).

## Quick start

```bash
neqsim pvt-proxy example recompression --out proxy.yaml
neqsim pvt-proxy run proxy.yaml --out results --strict
```

Without the `neqsim` command, use `python -m pvt_proxy` with the same arguments
from the `devtools` folder. Other commands:

| Command | Purpose |
|---|---|
| `fluids CONFIG --out DIR` | Generate the fluid ensemble only (`fluids.json`). |
| `reference CONFIG --out DIR` | Run the reference process only (`reference_results.csv`). |
| `run CONFIG --out DIR [--resume] [--strict]` | Complete workflow; `--strict` exits 2 when the fit needs review. |
| `evaluate PROXY CONFIG --out CSV [--fluids F]` | Run a fitted proxy on new fluids with extrapolation warnings. |
| `export PROXY --target T --out DIR` | Write target files: `neqsim`, `e300`, `pvtsim`, `fluidmagic`, `blackoil`, `olga`. |

## Workflow

1. **Fluid ensemble.** All fluids share one component template (EOS, component
   list, plus-fraction properties). Only the mole fractions change between
   fluids, so the component mapping is stable everywhere. Methods:
   `explicit` (characterized fluids), `mixing` (oil zone and gas zone, gas
   coning, allocation), `depletion` (constant-volume depletion: produced gas
   and optional recombination with reservoir liquid), and `variation`
   (seeded Latin-hypercube perturbation). Use at least 30 fluids, and up to
   about 100, covering the field-life range from the heaviest oil to the
   lightest gas.
2. **Reference.** `type: recompression` builds separator stages plus gas-handling steps
   (an optional compressor, a cooler and a scrubber), with scrubber liquid returned
   upstream. A step without compression models a suction cooler and scrubber, and
   `gas_return` returns a fixed gas rate such as gas lift. The loops are closed by
   `ProcessSystem.makeRecycles` with a tight tear tolerance (`recycle_tolerance`,
   default 1e-6). A reference result whose mass balance misses
   `max_mass_balance_error` (default 1e-4) is recorded as failed. `type: python` plugs
   in any existing NeqSim model through a `module:function` callable. `type: forward`
   is a known forward train, used to prove parameter recovery. A tuned project EOS is
   used unchanged through `fluids.template.factory: "module:function"`.
3. **Proxy.** The proxy is a forward chain of separator stages and an optional
   scrubber, ending with a stock-tank flash at standard conditions. The
   scrubber takes gas from chosen stages, and its liquid may only go to a
   later stage or the stock tank. A backward route is rejected because it
   would be a recycle. Each pressure and temperature is either fixed or free
   within bounds.
4. **Calibration.** One parameter vector is fitted to all fluids. Pressure
   ordering ($P_1 > P_2 > \dots > P_N > P_{stock}$, with a minimum step) is
   enforced by rejecting invalid candidates without simulating them. The
   default optimizer is differential evolution followed by a bounded
   trust-region least-squares refinement (`de+lsq`). Other methods are
   `lsq`, `de`, `powell`, `nelder-mead`, `de+powell` and the multistart
   variants. Fitted flowsheets are reused between candidates. Results are
   cached, and checkpoints allow `--resume`.
5. **Validation and outputs.** The workflow writes the per-fluid table,
   aggregate metrics, results by fluid class, worst fits, dominance
   diagnostics, identifiability, the calibration range, a Markdown report with
   figures, `proxy_definition.yaml`/`.json` and the target exports.

## Product definitions

| Quantity | Definition |
|---|---|
| Standard oil rate | Stock-tank liquid after the final flash at standard conditions; volume = mass / EOS density at standard conditions (Sm3/h). |
| Standard gas rate | Every gas stream leaving the process, including stock-tank gas, as ideal-gas standard volume (Sm3/h). |
| GOR | Gas Sm3 / oil Sm3, a diagnostic by default. |
| Basis | The same feed molar rate, pressure and temperature for reference and proxy. |
| Water | Excluded from the fluid ensemble. |

Standard conditions default to 288.15 K and 1.01325 bara (23.645 Sm3/kmol)
and are stored with the proxy.

## Objective

Oil and gas rates are fitted directly. GOR alone is not a good target: a
super-dry gas can have a GOR of tens of thousands without that error mattering
operationally, while an oil-volume error at moderate GOR matters much more.

$$
J(\theta) = \sum_j w_j \left[ w_o L\!\left(\frac{Q^{p}_{oil,j}-Q^{r}_{oil,j}}{Q^{r}_{oil,j}+\epsilon_o}\right)
 + w_g L\!\left(\frac{Q^{p}_{gas,j}-Q^{r}_{gas,j}}{Q^{r}_{gas,j}+\epsilon_g}\right)
 + w_{GOR} L(r_{GOR,j}) \right]
$$

The defaults give oil twice the gas weight and set $w_{GOR} = 0$. They also
protect the fit against dry-gas dominance:

- $\epsilon_o$ is 5 % of the median reference oil rate.
- Fluids with GOR above 15 000 Sm3/Sm3 get 0.25 of their weight.
- $L$ is the robust soft-L1 loss.

Each fluid's share of $J$ is reported. A fluid, or the dry-gas group, that
carries a disproportionate share is flagged.

## Identifiability

Oil and gas rates often constrain only a combination of stage conditions.
For example, a lower LP pressure can be offset by a higher LP temperature with
practically the same rates. The report therefore gives, for every free
variable, the change that raises the RMS rate residual by 0.1 %, both with the
other variables re-fitted and with them held fixed. A variable whose re-fitted
resolution exceeds a quarter of its bound range is marked as poorly
determined.

Report poorly determined values with their resolution, fix them from the
design basis, or set a small `objective.prior_weight` to stay near the design
values. Variables that end on a bound are flagged as well.

## Choosing the proxy topology

Applied to a producing installation, the topology mattered more than the optimizer. That process
returns the condensate of three compressor scrubbers to one production separator and
has gas lift. Over 46 fluids (GOR 125-6 960 Sm3/Sm3):

- Proxies of one or two separators reached 1.4-1.6 % mean oil error, and up to 6 % for
  gas condensates.
- One separator whose gas passes a scrubber, with the scrubber liquid sent forward to the
  stock tank, reached 0.06 %. All four conditions were determined by the data.

When the plant returns condensate, start with a scrubber topology. Gas lift, fuel gas and
anti-surge recycles do not change the products when every vessel runs at fixed pressure
and temperature. Take the separator temperature from the solved plant instead, because that
is where gas-lift heating acts. The proxy conditions are equivalent conditions, not set
points. Refit them when the scrubber or cooler temperatures change materially.

## Acceptance tests

`devtools/test_pvt_proxy.py` (hermetic) and
`devtools/test_pvt_proxy_integration.py` (NeqSim) cover:

1. recovery of known conditions when reference and proxy are the same forward process;
2. a reference with liquid recycle and a proxy without;
3. black oil, volatile oil, condensate and gas fluids with one parameter set;
4. rejection of an invalid pressure sequence;
5. a failed case kept with its reason and penalized;
6. reproducibility;
7. an extrapolation warning outside the calibration range;
8. a super-dry gas that does not dominate the fit;
9. selection of the better oil match over the better GOR match.

## Limitations

- Equilibrium stages only, in both reference and proxy.
- One common set of conditions cannot reproduce recycle effects exactly for
  every composition; the residuals are reported.
- The E300 `SEPCOND` export lists the stage table with liquid and vapour
  destinations. Check its item order and destination conventions against the
  manual of the simulator version in use; the stage table is the
  authoritative definition.
- The proxy is valid inside the stored calibration range.
