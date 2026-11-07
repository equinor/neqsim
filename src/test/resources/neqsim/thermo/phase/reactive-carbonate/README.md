# Independent reactive-carbonate parameter evidence

`iphreeqc-states.csv` contains 54 new IPhreeqc 3.7.3 calculations, generated independently of
NeqSim. The reference input is the public-domain USGS PHREEQC database at commit
`b0b3be767158ccc3322d2c816625cf470045e67e`, Git blob
`324f852784be84650b77bd7f07f8316aafd8188b`:
https://github.com/phreeqc-dev/phreeqc3/blob/b0b3be767158ccc3322d2c816625cf470045e67e/database/pitzer.dat

The generator verifies the exact database Git blob, including its original Latin-1 encoding.
It then removes only `GAS_BINARY_PARAMETERS` and `MEAN_GAMMAS`, which the older reference
executable cannot parse and which do not enter these gas-free aqueous calculations. Aqueous
species, equilibrium constants and every Pitzer interaction coefficient remain unchanged.
`PITZER -macinnes false` fixes the unscaled single-ion convention in every reference state.
No PHREEQC binary, third-party package or database copy is redistributed here.

Grid: 298.15/313.15/323.15 K; 0.1/1/3 mol/kg total chloride; Na fractions 0/0.5/1;
0.001/0.1 mol/kg total dissolved inorganic carbon; 1 atm. pH is solved from electroneutrality.
The CSV retains final species molalities, ionic strength, unscaled pH, water activity and
individual log10 activity coefficients. An absent spectator ion has `nan` for its log gamma.
The generated numerical results can be redistributed under this repository's license; the
source thermodynamic database is public domain.

Regenerate with `generate_reference.py --database /path/to/pitzer.dat --library /path/to/libiphreeqc-3.7.3.so`
in an environment with `phreeqpy` installed. CI replays the frozen values without network access
or an external simulator. Reference agreement is implementation evidence, not experimental
qualification. The separate Millero et al. (2007) carbonate-dissociation comparison retains
out-of-envelope discrepancies, as described in `docs/thermo/pitzer_parameter_provenance.md`.

`millero2007-dissociation.csv` evaluates the published fitted NaCl correlation, not individual
experimental measurements: Millero, Huang, Graham and Pierrot (2007), GCA 71, 46–55,
DOI 10.1016/j.gca.2006.08.041. The pure-water baselines follow Harned–Davis (1943) and
Harned–Scholes (1941). The reported 0.013/0.020 pK values are fit standard deviations.
Both dissociation steps are compared on the molality scale, including solvent activity and
native Pitzer coefficients. Maximum model discrepancies are 0.174/0.218 pK across the
12 atmospheric-pressure states. They remain outside full qualification; no coefficient was
regressed to these data.
