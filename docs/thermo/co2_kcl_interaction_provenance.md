---
title: "CO2–KCl interaction provenance"
description: "Source basis, model semantics, holdout diagnostics, and limitations for the electrolyte-CPA carbon-dioxide–potassium interaction."
---

`SystemElectrolyteCPAstatoil` uses the source-defined Born ion diameter and a
potassium-specific CO2 short-range interaction to reproduce the sign and
magnitude of KCl salting-out near the single calibration state. The change
corrects the previous strong salting-in response. It is not an empirical
hydrate-temperature offset and does not alter the hydrate model,
phase-equilibrium tolerances, or experimental fixtures.

## Parameter and model semantics

Solbraa (2002), Appendix C, equation (C.10), defines the Born charge-size term
with the characteristic Stokes diameter for cations and Pauling diameter for
anions. These are distinct from the covolume-derived hard-sphere diameter used
by the MSA and packing terms. Both the phase energy and component chemical
potential now use that same source-defined diameter.

The symmetric CO2/K+ coefficients are
`W0 = -2.31476480642442e-4 m3/mol`, `W1 = 0 m3 K/mol`, and
`W2 = 0 m3/mol`. The mixing rule evaluates

$$
W_{ij}(T)=W_0+W_1(1/T-1/298.15)
 +W_2[(298.15-T)/T+\ln(T/298.15)].
$$

These are short-range electrolyte-CPA coefficients, not dimensionless cubic-EOS
binary interaction parameters. The CO2/Cl− pair retains its legacy
`W0 = 1.27e-4 m3/mol`. KCl data constrain the sum of the two gas–ion
contributions; retaining chloride is therefore a fixed allocation convention,
not an independent ion measurement.

The specialized default applies only to `PhaseElectrolyteCPAstatoil`, and only
while both shared CO2 gas–ion constants retain their library defaults. Explicit
pair setters and explicit changes to either shared constant take precedence.
ScRK and other electrolyte-CPA phase implementations retain their existing
defaults.

## Calibration source

The only calibration target is the Onda et al. (1970) Table 3 correlation at
298.15 K:

$$
k_s=-0.2277-0.0362+0.3416=0.0777\ \mathrm{L/mol}
$$

in the source convention
$\log_{10}(\alpha_{water}/\alpha_{salt})=k_s I$, with solution molarity
$I=c_{KCl}$. The executable check evaluates infinite-dilution CO2 at 1 mol KCl
per kilogram of water and converts to solution concentration using the modeled
physical density. Onda's value is a global least-squares ion contribution
derived from several gases and salts, including compiled literature data; it is
not a direct raw CO2–KCl series.

`W1 = W2 = 0` is a zero-order temperature extrapolation. Onda alone does not
qualify that extrapolation over the 269–363 K holdout range.

## Independent holdouts

No He–Morse (1993), Yasunishi–Yoshida (1979), Dholabhai et al. (1993), mixed-salt,
or Burgass point is used to choose the coefficient. Repository diagnostics keep
those sources separate:

- He–Morse CO2 molality states test the 273.15 K KCl concentration response at
  matched CO2 partial pressure.
- Yasunishi–Yoshida Ostwald coefficients test an independent 298.15 K
  concentration basis.
- Dholabhai K10 and K15 points test hydrate equilibrium at low and high pressure
  without a temperature shift.
- NaCl, CaCl2, mixed-salt, and Burgass datasets remain controls for neighboring
  chemistry and hydrate behavior.

Passing numerical checks establishes state construction, conservation, and the
correct salting-out direction. It does not show agreement within experimental
uncertainty. The generated CSV diagnostics report residuals without converting
them into a relaxed acceptance tolerance.

On the implementation head, the six 273.15 K He–Morse states decrease
monotonically with KCl and have 29.38% absolute relative deviation on the
reported molality basis. All seven Yasunishi–Yoshida concentrations are matched;
their Ostwald coefficients decrease monotonically and have 17.64% absolute
relative deviation. The eight Dholabhai hydrate holdouts give K10 RMSE 0.179 K
(maximum absolute error 0.298 K) and K15 RMSE 0.246 K (maximum absolute error
0.348 K). These figures describe this exact model head; they are not acceptance
tolerances and remain subject to independent review.

The salt-only KCl water-activity diagnostic remains distinct from the dissolved
CO2 correction. At 273.15 K it differs from the Archer reference by −0.00215 for
K10 and −0.00467 for K15 before CO2 is added. The saturated Burgass NaCl control
retains five points within 1 K and a sixth error of −1.131 K at 194.77 bara.

## Qualification boundary

The correction also affects CO2 with potassium and counterions other than
chloride; those systems are not qualified by the KCl calibration. Temperature
dependence, high ionic strength, mixed solvents, mixed salts, and hydrate-model
accuracy require independent evidence. Pure-water CO2 bias is not fitted or
compensated here.

## Primary sources

- Solbraa (2002), [*Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing*](https://ntnuopen.ntnu.no/ntnu-xmlui/handle/11250/231326),
  Norwegian University of Science and Technology, Appendix C, equation (C.10).
- Onda et al. (1970), [DOI 10.1252/jcej.3.18](https://doi.org/10.1252/jcej.3.18), Table 3.
- He and Morse (1993), [DOI 10.1016/0016-7037(93)90137-L](https://doi.org/10.1016/0016-7037(93)90137-L),
  [NIST data reproduction](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_229).
- Yasunishi and Yoshida (1979), *J. Chem. Eng. Data* 24, 11–14,
  [NIST data reproduction](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_227).
- Dholabhai et al. (1993), [DOI 10.1021/je00012a045](https://doi.org/10.1021/je00012a045).

