# Frozen binary two-phase numerical qualification

Base: 32ee38fd5dc070bfa765d996027cdb0fa30e1e12. Independent synthetic
methane/n-heptane PR/classic oracle, standard-library only; no NeqSim imports.
Use explicit Tc=(190.56,540.2) K, Pc=(45.99,27.4) bara,
omega=(0.0115,0.349), R=8.3144621 J/mol/K, OmegaA=0.45724333333,
OmegaB=0.077803333, symmetric kij=0.02 (original 0). No volume correction.

Freeze before native comparison: log fugacity residual <=2e-9;
normalized EOS, component and volume closure <=1e-9; native pressure
absolute error <=1e-4 bar, produced composition <=1e-6 mol/mol,
final-basis GOR relative error <=1e-4, fitted volume error <=0.01 m3.
Native/reference use the same explicit finite start-state extraction schedule.
No tolerance will be enlarged to accommodate comparison failures.

Initial reservoir 323.15 K/80 bara, volume100 m3, gas/oil volume fractions
0.7/0.3. Four dated86400s intervals prescribe gas8000mol/oil2000mol each,
methane200mol/heptane10mol injection, four equal substeps. Reference solves
two-phase fixed-temperature fixed-volume equilibrium after each substep.
First three pressures fit volume over[80,120]m3; final pressure, produced
composition and surface GOR withheld. Surface separation is one equilibrium
flash at288.15K/1.01325bara, retaining both phases. No water or efficiency.

This is a finite-protocol synthetic oracle, not measured field accuracy,
continuous-flow convergence, general stability/critical-domain qualification,
or recoverable reserves. Reject absent phases or failed roots, never extrapolate.
Laboratory calibration and whole-experiment holdout will be independently
specified before any laboratory-to-field acceptance claim.

Laboratory extension (before native comparison): algebraically identify kij
from a0.8/0.2 one-mol sample at600K/200bara. Keep an entire650K CCE
at200/150/100/50bara out of identification. Frozen tuned holdout molar-volume
relative error <=1e-7. Preserve original k=0 mismatch and tuned k checkpoint.

Provenance boundary: laboratory0.8/0.2 calibration identifies the binary
interaction for this synthetic composition family. Supplied initial reservoir
phase-volume fractions imply a different overall component mixture, computed
explicitly in reference.json. The independently held-out650K experiment uses
the laboratory0.8/0.2 sample. This is not a composition-identical original
sample-to-reservoir reconstruction; do not claim that whole-issue gate closed.
