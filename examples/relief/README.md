# V-101 coupled relief, blowdown and disposal benchmark

This synthetic 20 s benchmark exercises the native two-vessel V-101 reference flowsheet.
The pressure at the beginning of each interval feeds back to both physical valve outlet
flanges. The disposal calculation advances an 8 m³ rigid, adiabatic equilibrium inventory
using the components and enthalpy actually discharged. It withdraws vapor to the tip and
drains each equilibrium liquid phase with a 30 s residence time. The assumed drum area is 4 m².
The synthetic dry-gas tip is characterized from 1.2 bara at zero flow to 3.2 bara at
10 kg/s. The simulation fails if flow exceeds that range.

| Physical connection or tag | Benchmark behavior |
| --- | --- |
| 10-V-100 → 20-V-101 | Native source inventories, level controls and process interconnection |
| 20-V-101 → 20-TEE-PSV-001 → 20-PCV-001 | Primary pressure controller fails closed at 1 s |
| 20-TEE-PSV-001 → 20-PSV-001 → common header | Native pressure-actuated lift and reseat |
| 20-TEE-PSV-001 → 20-BDV-001 → common header | Gas blowdown at 1 s, or 5 s in the delayed case |
| 10-V-100 → 10-TEE-LIQ-001 → 10-BDV-001 → common header | Wet case uses a separate liquid branch in parallel with 10-LCV-001, feeding the same disposal volume as the V-101 PSV |
| Common header/KO volume → vapor tip and liquid drain | Accumulation, phase equilibrium, drainage and separate tip-load history |

BDV-only immobilizes the PSV. Wet relief transfers 0.20 of the feed mole fraction from
methane to n-hexane and opens the V-100 liquid BDV. Both physical valves see the same
pressure because the model assumes negligible lateral and common-pipe loss.
The source model contains the isolation valves. These five disposal cases give no credit
for instrumented trips. Distributed header travel time, distinct local line losses, droplet
entrainment, metal temperatures and measured equipment characteristics remain outside this case.

## Reproduce

From the repository root with Java 17+, Maven, Python and JPype1 installed:

```sh
./mvnw -B -ntp test-compile dependency:build-classpath -Dmdep.outputFile=target/relief-classpath.txt
export NEQSIM_CLASSPATH="$PWD/target/test-classes:$PWD/target/classes:$(cat target/relief-classpath.txt)"
rev=$(git rev-parse HEAD)
python examples/relief/verify_v101_disposal.py v101-results --revision "$rev" --run
python examples/relief/steady_disposal_limit.py
```

The matrix uses the native conservative energy integration policy on both vessels,
with its relative VU energy tolerance set to the existing study limit of 1e-5. Each inlet
and outlet enthalpy is integrated once. A locally acceptable VU state outside the
epoch energy limit receives one warm refinement at the same volume and energy. A
persistent defect rejects the native step with rollback. Standalone separators retain
the previous single-flash behavior unless the user enables the additional tolerance.

Two fixed-boundary controls hold the actual PSV outlet at 1.2 bara. The original control
uses `--vessel-energy reference`. At 0.1 s it recovers the reference PSV peak of 5.495518 kg/s
at 3.2 s and the V-101 peak of 42.363686 bara at 2.4 s. Each JSON row is an end-of-interval
snapshot. The terminal rating and final EOS pressure are solved together. Vapor enthalpy and
composition and the liquid drainage use opening-phase inventories. Rows include vessel temperatures and inventories, valve flows and lift,
flange/header pressures, drum level, drain and vapor flow, and header balance residuals.

## Qualification evidence

Reference-policy Java revision `0eacf4bac9646a0c2839aa95392e38d67ddb51b2`. Run
with `--vessel-energy reference` to reproduce the table below. It gives independent maxima over 20 s at a 0.1 s timestep. The two status columns apply
the same numerical criteria at both resolutions.

| Scenario | V-101 peak (bara) | PSV peak (kg/s) | BDV peak (kg/s) | Tip peak (kg/s) | Header peak (bara) | 0.1 s | 0.05 s |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| PSV only | 42.451 | 5.499 | 0 | 5.201 | 1.741 | Pass | Pass |
| BDV only | 45.129 | 0 | 0.609 | 0.594 | 1.207 | Pass | Hold |
| Simultaneous | 42.368 | 5.470 | 0.572 | 5.453 | 1.795 | Pass | Pass |
| Delayed BDV | 42.440 | 5.498 | 0.571 | 5.450 | 1.794 | Pass | Pass |
| Wet | 42.413 | 5.281 | 2.178 | 5.416 | 1.787 | Hold | Hold |

The verifier independently integrates each flow, checks every valve flange against the
previous header pressure, evaluates the steady terminal pressure at each measured tip
flow and compares it with the closing inventory pressure, verifies each peak and time, and checks actual closure before BDV opening.
Header closure thresholds are 1e-8 kg total, 1e-8 mol per component, 1e-5 relative
volume and the reference vessel's 1e-5 relative energy limit. Each upstream vessel
must independently report a complete energy balance with maximum relative cumulative
residual at or below 1e-5. A disposal balance alone cannot clear a source energy hold.

At 0.1 s the wet drum retained 18.823 kg at 20 s and drained 6.135 kg.
Halving the step changed the peak tip flow from 5.416 to 5.449 kg/s and moved its time
from 14.90 to 7.95 s. The V-100 energy residual fell from 3.4274e-5 to 1.5821e-5.
The BDV-only fine run reached 1.0351e-5. Peak-time sensitivity, source balances,
thermal response and missing project equipment data keep the engineering qualification open.
The receipt includes both upstream vessel energy ledgers in joules. It records the
integrated boundary energy, expected and published internal energy, signed local VU
defects, and maximum historical debt. The verifier recomputes each normalized maximum
from the physical ledger and requires a complete passing epoch for both conservative vessels.

With conservative integration and the same 1e-5 vessel policy, the fine BDV-only
maximum fell from 1.0351e-5 to 4.6428e-7. In the wet case at 0.05 s, accumulating
the exact source-energy target reduced the V-100 maximum from 1.5821e-5 to
1.1931e-5. Its 218.125 J peak debt equalled the largest accepted VU flash defect.
Refining only locally accepted states outside the epoch's 182.821 J budget reduced
the maximum to 119.660 J (6.5452e-6). The fine wet tip peaked at 5.4493 kg/s at 7.95 s.
At 0.025 and 0.0125 s the wet-case source maxima were 7.4565e-6 and 4.8489e-6.
These are numerical energy checks on the synthetic vessels. The thermal comparison
and project equipment limitations still apply.

The BDV-only case also exposes the stiffness of the terminal rating near zero pressure
rise. Evaluating the tip flow from the opening pressure produced peaks of 0.685 and
0.592 kg/s at successive timesteps. The implicit terminal calculation gave 0.5941 and
0.5918 kg/s, both at 20 s. Its rate stays inside both the characterized gas range and
the available vapor inventory. The preferred pressure residual is 1e-9 bar. When
the flow-rate bracket has collapsed below 1e-11 kg/s the documented EOS resolution
permits up to 1e-6 bar (0.1 Pa).

The steady-limit check uses frozen, independently calculated choked gas loads in zero-loss
laterals. Each source admits the input fixed by its area, pressure and the ideal choked
mass-flux equation. At 40 s the finite header reached 1.40672 bara with one source
(3.21495 kg/s) and 1.66512 bara with two (4.82243 kg/s). The independently implemented
native steady network and the settled finite header differed by less than 3e-9 bar
in both cases. The regression limits are 1e-4 bar and 1e-4 kg/s. The wet run is
outside the dry-gas network comparison.

## Independent vessel blowdown comparison

```sh
python examples/relief/spadeadam_s12.py --dt 2 > s12-2s.json
python examples/relief/spadeadam_s12.py --dt 1 > s12-1s.json
```

The separate S12 experiment exercises the native vapor-withdrawal vessel blowdown solver.
The basis is 66.5/3.5/30 mol% methane/ethane/propane, 118.5 bara, 293.15 K, a 10 mm
top orifice with discharge coefficient 0.84, 1.13 m bore, and 59 mm steel wall. The model
assumes a 2.497 m³ equivalent cylinder, 7850 kg/m³ steel, 470 J/(kg K) wall heat capacity
and 50 W/(m² K) internal heat transfer. There is one wall and one equilibrium fluid
temperature. These assumed thermal parameters were held fixed under timestep refinement.

| Quantity | NeqSim, 2 s step | NeqSim, 1 s step | Published S12 Figure 1, approximate visual range |
| --- | ---: | ---: | --- |
| Pressure at 1500 s (bara) | 2.154 | 2.146 | Approximately 3–5 bar |
| Minimum fluid temperature (K) and time | 237.06 at 712 s | 237.09 at 716 s | Vapor near 250 K at roughly 500–600 s |
| Fluid temperature at 1500 s (K) | 243.05 | 242.89 | Vapor roughly 260–266 K |

Both native runs reached 1500 s with zero VU-flash fallbacks. The equilibrium fluid
cooled more deeply and reached its minimum later than the reported vapor measurements.
The published figure provides a screening comparison across different thermocouple
locations and phase representations. It does not establish thermal or disposal-system
qualification. Experimental initial temperature and trace heavy species are uncertain.
See [Ricci et al., J. Phys. Conf. Ser. 655 (2015) 012031, section 4.1](https://doi.org/10.1088/1742-6596/655/1/012031).
