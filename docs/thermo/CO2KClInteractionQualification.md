---
title: "CO2–KCl interaction qualification"
description: "Calibration, independent solubility and hydrate holdouts, compatibility and remaining model errors for the electrolyte CPA carbon dioxide–potassium interaction."
---

# CO2–KCl interaction qualification for electrolyte CPA

The `SystemElectrolyteCPAstatoil` default CO2–potassium short-range interaction is
calibrated to salt suppression of dissolved CO2. It fixes the previous prediction
of strong salting-in. It does not remove the separate pure-water CO2-solubility
bias or qualify every potassium salt and solvent mixture.

## Parameter, units and scope

The symmetric CO2/K+ coefficients are `W0 = -2.31476480642442e-4 m3/mol`,
`W1 = 0 m3 K/mol`, and `W2 = 0 m3/mol`. The implemented temperature convention is

$$W_{ij}(T)=W_0+W_1(1/T-1/298.15)+W_2[(298.15-T)/T+\ln(T/298.15)].$$

Here $F=A^{res}/(RT)$ has units mol. The code computes its short-range contribution as
$F^{SR2}=-\sum_{ij}n_i n_j W_{ij}/[V(1-\epsilon)]$, with $V$ in m3. Both
symmetric entries are present in the double sum. These coefficients must not be
interpreted as a dimensionless cubic-EOS binary interaction parameter.

The CO2/Cl− pair retains its existing `W0 = 1.27e-4 m3/mol`, `W1 = W2 = 0`.
Only the CO2/K+ pair is changed; its inherited cation–water temperature
coefficients are explicitly cleared. This allocation is a fixed chloride gauge
for KCl. The directly constrained 1:1 salt combination is the sum of the two gas–ion
coefficients, not either individual-ion value. Changing the shared chloride
coefficient would also require compatible changes to other ions. The correction
also affects mixtures containing CO2 and potassium with other counterions;
those mixtures have not been experimentally qualified by the KCl data.

The specialized default applies only to `PhaseElectrolyteCPAstatoil` and only while
the global CO2 gas–ion constants remain at their original defaults. Explicit pair
setters retain priority. ScRK, other CPA implementations, and fluids without
potassium retain their existing pair parameters. There is no temperature or
concentration clamp, imposed fluid phase count, or alteration of equilibrium
acceptance tolerances.

## Calibration and independent measurements

The only fitted target is the Onda et al. (1970) Table 3 correlation at 298.15 K:
$k_s=-0.2277-0.0362+0.3416=0.0777$ L/mol in
$\log_{10}(\alpha_{water}/\alpha_{salt})=k_s I$, where $I=c_{KCl}$ in mol/L.
The model target is evaluated at 1 mol KCl/kg water and infinite dilution of CO2.
The concentration conversion uses phase mass divided by physical density,
including the configured volume correction, rather than the raw EOS volume.
The reported digits make the calculation reproducible; they do not represent
experimental parameter precision. This is a published correlation target, not
an individual raw KCl measurement.

Independent validation uses He–Morse (1993), Yasunishi–Yoshida (1979), and
Dholabhai et al. (1993); none was used in the fit. The solubility states match final
aqueous salt concentration and gas partial pressure. All reported public flashes
pass the unchanged species-balance, charge, pressure-root, and phase-equilibrium
checks. Numerical convergence is distinct from experimental accuracy.

| Model baseline | He–Morse absolute CO2 molality AARD, 26 points | He–Morse salt-factor AARD | Yasunishi absolute Ostwald AARD, 7 points | Yasunishi salt-factor AARD, 6 salt points |
|---|---:|---:|---:|---:|
| Original Born formula and original gas–ion defaults | 1048.67% | 67.88% | 1443.05% | 79.36% |
| Source-correct Born formula and original gas–ion defaults | 1069.56% | 67.99% | 1471.48% | 79.46% |
| Source-correct Born formula and CO2/K+ correction | 19.86% | 4.33% | 18.53% | 1.57% |

The original-formula row is a controlled model comparison using the current
numerical solvers with the previous Born diameter restored; it is not a replay
of an old complete binary. He–Morse covers 273.15–363.15 K and approximately
0.1–5 mol/kg water; Yasunishi covers 298.15 K and 0–4.131 mol/L.

The eight unfitted K10/K15 hydrate points at 11.30–35.75 bara give K10 RMSE
0.179 K and maximum absolute error 0.298 K; K15 RMSE 0.246 K and maximum error
0.348 K. All evaluated solutions retain two fluid phases at the measured hydrate points. The source-correct
uncalibrated K15 model instead has errors of approximately +4.56 to +5.52 K and
an extra aqueous phase at the two higher pressures.

## Remaining errors and rejected compensation

The corrected He–Morse absolute CO2 errors remain −11.39% to −31.45%; its
salt-factor maximum error is 13.12%. The Yasunishi absolute errors remain
−15.38% to −20.01%, while its salt-factor maximum error is 3.51%. The model
therefore improves the salt response without meeting the much smaller reported
measurement uncertainties. The 35% absolute and 14% relative limits in regression
tests are model-error envelopes, not experimental uncertainty claims.

Six low-concentration absolute predictions become worse because the original
salting-in partly compensated the pure-water bias:

| T, K | KCl, mol/kg water | Original default error | Corrected error |
|---:|---:|---:|---:|
| 273.15 | 0.100 | −19.22% | −27.11% |
| 273.15 | 0.505 | +17.91% | −29.50% |
| 298.15 | 0.100 | −7.20% | −16.05% |
| 323.15 | 0.106 | −2.03% | −11.67% |
| 348.15 | 0.100 | −2.55% | −11.39% |
| 363.15 | 0.100 | −3.81% | −12.39% |

Above roughly 3 mol/kg, residual concentration curvature is also imperfect:
at 323.15 K the calculated salt factor decreases slightly between 4 and 5 mol/kg,
whereas the measurements increase. Do not infer unrestricted high-concentration,
mixed-salt, or mixed-solvent accuracy from the successful hydrate checks.

A separate two-coefficient CO2–water `k(T)` fit to the Plummer–Busenberg (1982)
Henry correlation was tested and rejected. That source defines
$K_H=a_{CO2(aq)}/f_{CO2(g)}$ on a molality/atm basis, with ionization already
removed and dissolved CO2 including H2CO3. Thus the dilute EOS comparison is
$H_m[\mathrm{bar\,kg/mol}]=1.01325/K_H$ at the source's 1 atm reference pressure.
The fit improved low-pressure absolute solubility but worsened 11 independent
Wiebe–Gaddy (1940) high-pressure water points from 6.09% to 19.04% AARD.
The existing water interaction and cross-association parameters are retained.

## Primary sources

- Onda et al. (1970), [DOI 10.1252/jcej.3.18](https://doi.org/10.1252/jcej.3.18), Table 3.
- He and Morse (1993), [DOI 10.1016/0016-7037(93)90137-L](https://doi.org/10.1016/0016-7037(93)90137-L), [original measurements reproduced by NIST](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_229).
- Yasunishi and Yoshida (1979), J. Chem. Eng. Data 24, 11–14, [original measurements reproduced by NIST](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_227).
- Dholabhai et al. (1993), [DOI 10.1021/je00012a045](https://doi.org/10.1021/je00012a045).
- Plummer and Busenberg (1982), [DOI 10.1016/0016-7037(82)90056-4](https://doi.org/10.1016/0016-7037(82)90056-4), Eq. 4 and Henry data reduction.
- Wiebe and Gaddy (1940), [DOI 10.1021/ja01861a033](https://doi.org/10.1021/ja01861a033), [original data reproduction](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_32).
