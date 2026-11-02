# Independent native wet DLE reference

Synthetic one-mole methane/n-decane/water 0.6/0.2/0.2, classic SRK without
volume translation, at 350 K and 200/100/50 bara. Tc/Pc/omega/kij/R are explicit
shared model inputs in `independent_srk_dle.py`, reused from the separately
qualified wet CCE oracle. No NeqSim calculation or fitted parameter enters the
independent equilibrium calculation.

Six log-fugacity equalities determine three distinct phase compositions at each
pressure. Three named component balances determine positive phase amounts.
The whole gas inventory, including water and vaporized n-decane, is removed;
oil plus aqueous component inventories become the next pressure's sample.
This path is not a series of independent flashes of the original composition.
It does not prove global stability or measured wet-fluid accuracy.

`reference.json` retains complete predicted equilibrium/retained volumes,
component withdrawals, retained component inventories, phase amounts and
compositions. `WetDleBenchmarkProbe` exports the public handoff's complete cell,
withdrawal and separator products to `native-states.json`. The fixed separator
is 10 bara/303.15 K then 1.01325 bara/298.15 K, retaining gas/oil/aqueous products.
Original/tuned provenance is retained by the handoff; these reference values
use only the declared original unshifted SRK model.

Run the independent script with Python plus NumPy/SciPy. Normal and optimized
Python must reproduce `reference.json` byte-for-byte. `WetDleHandoffTest` checks
relative volume and absolute phase-composition/amount error <=1e-8, component
closure <=1e-10 mol and separator closure <=1e-8 mol. These bounds are frozen
before comparison. The Java probe is executable test-source code; its main
argument is the output JSON filename. Nearby states and measured experiments
require separate acceptance; no uncertainty or reservoir-reserves claim follows.
