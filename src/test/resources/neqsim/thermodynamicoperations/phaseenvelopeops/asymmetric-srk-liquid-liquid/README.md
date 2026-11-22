# Asymmetric SRK liquid-liquid onset qualification

This is an independent numerical reference for methane/n-hexane/nC10 at overall
mole fractions 0.8/0.1/0.1 and 162.03989088929555 K. It explains why its bounded
continuation cannot be assumed to form a closed vapor-liquid envelope.

Run `python3 reproduce.py` in this directory. Only Python's standard library is
required. The script solves the SRK pressure cubic and closed-form SRK chemical
potentials directly with 60- and 80-digit decimal arithmetic. It calls no NeqSim
volume, pressure, fugacity, flash, continuation, derivative or linear-algebra
code. The explicit inputs are pure-component a(T), b, gas constant and classic
binary interactions exported from PR #4577 head `02917efb`; using those inputs
qualifies their numerical application, not their experimental accuracy.

For each composition, the script uses the SRK equations
`P = RT/(v-b) - a/[v(v+b)]` and
`Z^3 - Z^2 + (A-B-B^2)Z - AB = 0`, with
`A=aP/(RT)^2`, `B=bP/(RT)`,
`a=sum(x_i*x_j*sqrt(a_i*a_j)*(1-k_ij))`, and `b=sum(x_i*b_i)`.
The logarithmic fugacity coefficient is evaluated as
`(b_i/b)(Z-1)-ln(Z-B)-(A/B)(2*sum(x_j*a_ij)/a-b_i/b)*ln(1+B/Z)`.
Three equality-of-chemical-potential equations determine pressure and the two
independent incipient-phase composition variables. Newton linear systems use
independent pivoted Gaussian elimination and central-difference Jacobians.

The onset pressure is **152.94443048981308665 bara**, with incipient liquid
composition approximately **0.94675571763856884 / 0.03644939098018227 /
0.01679489138124889**. The liquid Z factors are **0.76446888724776619** and
**0.55514294014715844**. The independent solution has negative isothermal dP/dV
for both roots, positive composition-Hessian eigenvalues for both homogeneous
liquids, EOS pressure residuals below 1e-75 bara and logarithmic chemical-potential
residuals below 1e-76 at 80 digits. Both arithmetic precisions agree in pressure
to more than 50 digits. Positive Hessians establish local minima; this reference
does not claim exhaustive global stability or model validation against data.

`AsymmetricSrkLiquidLiquidEnvelopeTest` compares NeqSim's two homogeneous liquid
roots and chemical potentials to the independent reference, then uses multiphase
TP flashes on either side of the pressure to qualify onset, phase types,
component conservation, fugacity equality and repeatability. At 0.999 times the
reference pressure there are two oil phases; at 1.001 times the pressure there
is one oil phase.

The default envelope trace crosses a critical region near 435 K/271 bara before
continuing onto the low-temperature liquid-liquid boundary. The bounded trace
reaches about 119 K/998 bara and then its 1000-bara pressure limit. Its converged
segments are retained for diagnostics, `isEnvelopeClosed()` is false and its
overall extrema are NaN. The robustness test preserves the high-temperature
segment, one critical crossing, the high-pressure retained branch and this
explicit incomplete-result contract. Ordinary closed SRK/PR mixtures remain
covered by the full phase-envelope test matrix.

This is a fluid-only cubic-EOS numerical qualification. Solid formation and the
physical validity of extrapolating these interaction parameters to cryogenic
conditions are outside its scope.
