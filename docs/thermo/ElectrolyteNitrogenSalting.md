---
title: "Bounded gas–salt parameter qualification"
description: "Nitrogen–NaCl calibration, independent validation, coefficient conventions and limits of the predictive divalent-chloride default."
---

# Bounded gas–salt parameter qualification

The defaults described here apply to `SystemElectrolyteCPAstatoil`, with the
source-correct Born characteristic diameters and nitrogen relative permittivity
2.0. The solvent, ion-volume and ion–ion parameters are unchanged. Explicit pair
coefficients set through the electrolyte mixing-rule API remain authoritative.
Changing either relevant global gas–cation or gas–anion coefficient also disables
these specialized defaults for that gas. The default check is exact equality
against a private copy of the shipped `furstParamsGasIon` array. Both relevant
entries must remain unchanged. Restoring their original values re-enables the
default; setting any coefficient of an explicit pair marks the entire symmetric
pair fitted and preserves its other two coefficients. Set all three explicitly
when replacing a temperature-dependent pair.

## Nitrogen and sodium chloride

The original gas–ion defaults were described in the source as NaCl-oriented,
without a reproducible calibration dataset. With corrected dielectric inputs
they predict salting-in for nitrogen at approximately 1 mol/kg NaCl. The new
nitrogen–sodium coefficients fit the measured base-10 molal Setschenow relation
`log10(HmSalt/HmWater) = ksmm*mSalt`, where `Hm = fGas/mGas` in bar kg/mol,
`mSalt` is mol/kg water, and `ksmm` is kg/mol.

The training observations are Morrison and Billett (1952), reproduced in
IUPAC–NIST Solubility Data Series volume 10, p. 69. Their reported salt molality
is 1 mol/kg and nitrogen pressure is 1 atm. The compilation labels the dataset
tentative and reports uncertainty ±0.010 kg/mol.

| Temperature, K | Reported ksmm, kg/mol | Use |
|---|---:|---|
| 285.75 | 0.154 | Training |
| 303.15 | 0.121 | Withheld temperature; prediction 0.122340 |
| 322.55 | 0.106 | Training |
| 344.85 | 0.106 | Training |

The existing correlation is retained:
`W(T) = W0 + W1*(1/T - 1/298.15) + W2*((298.15-T)/T + ln(T/298.15))`.
`W0` and `W2` have units m³/mol; `W1` has units m³ K/mol. These are Helmholtz
interaction coefficients, not Setschenow coefficients. The electrolyte
short-range energy is `Fsr/(RT) = -sum(ni*nj*Wij)/(V*(1-eta))`.

At each of the three training temperatures, scalar bisection identifies the
equal-ion W producing the reported coefficient. Solving the resulting three
linear equations gives
`[W0,W1,W2] = [1.0718504029482e-5,-0.053834813215369,-0.0014224929630166]`.
Only the neutral NaCl combination `W(g,Na)+W(g,Cl)` is identified by these data.
The implementation preserves legacy `W(g,Cl)=[1.05e-4,0,0]` and assigns twice
the fitted vector minus that chloride vector to sodium. This preserves salts
without sodium. Sodium salts with other anions and mixed salts are not independently
qualified by the NaCl fit; this is not a globally invariant change of ion gauge.

Independent measurements reproduced in SDS 10 give molar coefficients 0.124 L/mol
at 303.15 K, 1 mol/L NaCl (Smith, Kelemen and Nagy, 1962), and 0.122 L/mol at
324.65 K, 1 mol/L (O'Sullivan and Smith, 1970). Predictions are 0.13350 at 50 bar
and 0.11696 at 100 bar, respectively. The regression envelope of ±0.012 L/mol
is an engineering acceptance bound, not their experimental uncertainty.

Conversion uses independently correlated NaCl solution density from Laliberte
and Cooper (2004), not an EOS molar volume:
`cSalt = mSalt*rhoSalt/(1+mSalt*Msalt)` and
`HcSalt/HcWater = (HmSalt/HmWater)*(1+mSalt*Msalt)*rhoWater/rhoSalt`.
Density is kg/L and salt molar mass is kg/mol. The published concentration
preparation basis at high pressure introduces an additional conversion uncertainty;
these checks do not establish pressure-dependent brine density accuracy.

The supported characterization is 285.75–344.85 K, approximately 1 mol/kg NaCl,
with separate 1 mol/L checks at 50 and 100 bar. Extrapolation is unqualified.
At 303.15 K and 50 bar, predicted molar coefficients at 2.7 and 5.47 mol/L are
0.1472 and 0.1618 versus measured 0.113 and 0.098: concentrated-brine curvature
remains wrong. At 375.65–398.15 K the coefficient errors grow substantially.
Pure-water nitrogen solubility is unchanged and is approximately 2–10% below
the Morrison–Billett water measurements over 285.65–345.65 K. The correction
qualifies the relative salt effect, not absolute gas–water solubility accuracy.

## Unfitted divalent chloride prediction

The legacy code applied the same NaCl-oriented gas–ion coefficient to every
positive ion, including Ca²⁺ and Mg²⁺. That transfer has no qualified source
dataset. Solbraa (2002), equations (8.14–8.15), defines the predictive ion–water
and ion–ion correlations; gas–salt residuals are fitted separately, as illustrated
by the CO₂ salt data in Table 8-14. Omission of an unfitted residual is a predictive
assumption, not evidence that the physical interaction vanishes.

The revised divalent default sets `W(g,cation)=-zCation*W(g,Cl)` and zero
temperature coefficients. For a charge-balanced binary divalent chloride this
makes the gas–salt short-range residual zero while retaining the existing
chloride gauge and all NaCl coefficients. All remaining EOS, electrostatic,
Born and ion–water contributions remain active. Binary salt properties without
gas are unchanged. Other anions and high-concentration mixtures are unqualified.

Independent nitrogen–CaCl₂ measurements at 303.15 K provide a useful but limited
check. At 50 bar, the old and revised base-10 molar coefficients are:

| CaCl₂, mol/L | Measured kscc, L/mol | Old default | Zero-residual prediction |
|---:|---:|---:|---:|
| 0.5 | 0.212 ± 0.020 | −0.3242 | 0.05984 |
| 1.75 | 0.177 ± 0.008 | −0.3481 | 0.04278 |
| 3.5 | 0.157 ± 0.008 | −0.3971 | 0.00130 |
| 5.6 | 0.149 ± 0.026 | −0.4740 | −0.09037 |

The low-concentration direction improves, but magnitude errors remain 72–76%
and the concentrated solution still has the wrong sign. For dilute CO₂–CaCl₂
at 298.15 K, Onda's coefficient 0.1776 L/mol compares with −0.1573 before and
0.3070 after: the direction improves while the prediction remains about 73% high.
This default is therefore a limited improvement over unsupported salting-in,
not a quantitative divalent-salt model. No propane–CaCl₂ solubility data were
used or independently verified. Correct propane salting direction in a mixed
brine is a regression requirement, not a quantitative literature validation.

Machine-readable training rows, coefficient conventions, independent validation results and
extrapolation failures are retained in
[the qualification data](data/electrolyte-gas-salt-qualification.json).

## Sources

- T. J. Morrison and F. Billett (1952), *J. Chem. Soc.*, 3819–3824,
  [doi:10.1039/JR9520003819](https://doi.org/10.1039/JR9520003819).
- [IUPAC–NIST SDS 10, Nitrogen and Air](https://iupac.github.io/SolubilityDataSeries/volumes/SDS-10.pdf),
  pp. 17, 50, 69, 346 and 350–351. Primary measurements and concentration
  definitions, including the independent Smith–Kelemen–Nagy and O'Sullivan–Smith datasets.
- M. Laliberte and W. E. Cooper (2004), *J. Chem. Eng. Data*, 49, 1141–1151,
  equations (6), (8), (9), Table 1,
  [doi:10.1021/je0498659](https://doi.org/10.1021/je0498659).
- K. Onda et al. (1970), *J. Chem. Eng. Japan*, 3, 18–24,
  [doi:10.1252/jcej.3.18](https://doi.org/10.1252/jcej.3.18).
- E. Solbraa (2002), *Equilibrium and Non-Equilibrium Thermodynamics of Natural
  Gas Processing*, [original thesis](http://hdl.handle.net/11250/231326).
