# Independent incipient SRK reference

Synthetic methane/ethane/propane/n-butane, classical SRK rule 2, at
253.46685189059752 K and 77.53775411226596 bara. Scalar inputs were captured
from PR #4577 head `9b2269837eee56d9adb4368460504d5b61354f21`.
`inputs.json` preserves decimal strings for component amounts, temperature-dependent
pure attractions, covolumes and binary interaction parameters. Attractions/covolumes
use NeqSim internal pressure-volume scaling; the script forms dimensionless A/B.

Run `python3 reproduce.py` from any directory. This requires only the Python
standard library and no NeqSim/JVM. The reproduction independently implements
classical SRK log fugacity, the cubic pressure equation and a pivoted Newton solve
of four log-fugacity equations plus Rachford-Rice. Both phase roots must be outside
covolume and mechanically stable. Normalization and material balance are asserted.
It does not reuse NeqSim roots, compositions or phase-fraction output as reference
values. Three rough initial estimates are run at each of 60 and 80 decimal digits.
`reference.json` is the saved output.

All six solutions give beta_v = 0.00003508682096277057190884200393792207...
The previous reference 0.0000350882832337307 differs by 1.46227e-9.
Current NeqSim 0.000035086806597917075 differs by -1.43649e-11.
The existing absolute 1e-10 regression tolerance is retained, alongside all
material, fugacity, repeated/changed/returned-state and cross-algorithm checks.
This is an equation-level numerical qualification, not an experimental benchmark
or a proof of global stability for all phase sets.
