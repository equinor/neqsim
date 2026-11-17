# Staged PR-HV water/n-butane endpoint assessment

The staged water-first tuple has (D_{12}=4968) K, (DT_{12}=-19.6),
(D_{21}=1567) K, (DT_{21}=5.7), and symmetric (alpha=0.06).
It uses (	au_{ij}=D_{ij}/T+DT_{ij}) in the infinite-pressure,
covolume-weighted modified-NRTL convention from [Yushchenko and
Brusilovsky (2016), Table 1](https://doi.org/10.1016/j.fluid.2015.09.029).
The paper's stated emphasis is 323.15–423.15 K and 25–700 bara. The fit's
parameter covariance, complete pure inputs and composition envelope are
unresolved. The test stages only this pair using ordinary PR alpha and the
following declared library inputs.

| Component | (T_c), K | (P_c), bara | Acentric factor |
| --- | ---: | ---: | ---: |
| Water | 647.30 | 220.89 | 0.344 |
| n-Butane | 425.12 | 37.96 | 0.2002 |

[Yushchenko's thesis](https://mai.ru/upload/iblock/fca/kandidatskaya-dissertatsiya_yushchenko-t.s..pdf)
lists n-butane at 425.20 K, 37.97 bara and 0.2010 in Table 1.1. The author’s
Table G8 supplies a forward data lead attributed to Namiot and Bondareva.
Its original measurement table, reported concentration basis and phase labels
need verification before using those values as a forward benchmark. Figure 6 of
the journal cites the same work and names propane while printing C₄H₁₀.

## Observations and calculation

[observations.csv](observations.csv) preserves selected reported
concentrations and total absolute pressures. Two experiments contribute.

- **Reamer, Sage and Lacey (1952).** [The ACS original](https://doi.org/10.1021/ie50507a049)
  requires purchase. These ten paired tabulations were checked against the page
  images in [Liège (2006), Appendix 5](https://orbit.dtu.dk/files/2494315/Xavier%20Liege%2C%20ph.d..pdf),
  printed pp. 150–151 (PDF pp. 164–165). The column (x[1]) gives aqueous
  n-butane in mole ppm. The original scale for (x'[2]), water in the
  n-butane-rich phase, is mole fraction times 1000. The adjacent rich n-butane
  ppm column was checked as its complement. The reproduction has a regular grid
  and the status of these values as individual measurements is unresolved.
  It supplies no point uncertainty for the selected rows. This assessment
  selects one vapor/liquid and nine liquid/liquid coordinates separated from
  the reported three-phase region. It uses the appendix phase diagram on
  p. 161 to distinguish branches near 280 °F. The IUPAC evaluation flags
  disagreements between Reamer's and Le Breton's measurements. Its uncertainty
  estimates on p. 53 apply to its separate three-phase-limit table.
- **Le Breton and McKetta (1964).** Four neighboring 280 °F vapor/liquid
  coordinates are from the original-data column reproduced in the
  [IUPAC Solubility Data Series 24, pp. 49–50](https://srdata.nist.gov/solubility/IUPAC/SDS-24/SDS-24-pages_48.pdf)
  and image-checked against Liège Appendix 5 p. 148 (PDF p. 162). Only the
  aqueous direction is available at these coordinates. IUPAC reports error
  estimates of 5% from the authors and 1% from the compiler for the solubility,
  with no point confidence intervals. The 360.7 psia row falls below the model
  paper's emphasis and is kept separately. The 490.7 psia three-phase-limit
  row is outside this comparison.

Temperatures are converted from °F to K and total pressure from psia to bara.
The test uses a synthetic equimolar overall feed. Composition endpoints are
compared at known T/P. No experimental overall feed or phase amounts are inferred.
No model coefficients are fitted. The independent Decimal solver differentiates
the extensive attraction with mole steps of (10^{-10}) and (5	imes10^{-11})
and solves both log-fugacity equations. The two calculated roots and compositions
must agree under refinement to (10^{-9}) absolute and their log-fugacity
residuals must be below (10^{-10}). It selects the large rich root for vapor
observations and the small rich root for liquid observations.

`HuronVidalWaterButaneAssessmentTest` checks fresh and retained reversed
component-order fluids, including a same-state repeat and changes in T/P and
phase branch. It checks balance (10^{-10}), normalization (10^{-12}),
log-fugacity equality (10^{-8}), and all six existing thermodynamic
identities/numerical checks. Native/independent parity uses (5	imes10^{-8})
relative or (10^{-13}) absolute, with the latter the floor for dilute
components. The native phase label is reported alongside the experimental
branch. At 280 °F and 800–1500 psia, NeqSim uses `GAS` for three dense
rich roots in the reported two-liquid region. These agree with the independent
rich root. Their calculated V/B ratios are 2.039, 1.927 and 1.768, above the
1.75 cutoff in `PhaseEos.init` that assigns `GAS`. The label is an unresolved
physical classification discrepancy.

## Results and limits

| Observations inside stated T/P emphasis | Rows | AARD with declared pure inputs | AARD with water-pressure control |
| --- | ---: | ---: | ---: |
| Reamer water in rich phase | 10 | 4.70% | 5.07% |
| Reamer n-butane in water | 10 | 88.17% | 88.02% |
| Le Breton n-butane in water | 3 | 73.98% | 73.75% |

[Pure-pressure results](pure-pressure.csv) compare ordinary PR separately with
the IAPWS water saturation correlation and [NIST's Antoine correlation from Das
et al.](https://webbook.nist.gov/cgi/cbook.cgi?ID=C106978&Mask=4#Thermo-Phase).
Across 344.26–410.93 K, the pressure deviation ranges from −8.29% to −2.29%
for water and from −0.38% to +0.07% for n-butane. Matching pure-water pressure
locally changes aqueous AARD by less than 0.3 percentage points in both
in-window sets. This sensitivity leaves the large reverse error unresolved.
The local alpha control is a diagnostic at each temperature. The comparisons
do not establish a temperature-dependent calibration, experimental acceptance,
source-author input reproduction, or new runtime parameters. Ice, brine,
inhibitor partition and hydrate dissociation are outside the assessed domain.

Run from the repository root:

```bash
python3 devtools/audit_huron_vidal_nbutane.py
python3 devtools/audit_huron_vidal_nbutane.py --mode match-water-pressure
python3 devtools/audit_huron_vidal_nbutane.py --mode pure-pressure
./mvnw -Dtest=HuronVidalWaterButaneAssessmentTest test
```

The first three commands reproduce [assessment.csv](assessment.csv),
[match-water-pressure.csv](match-water-pressure.csv) and [pure-pressure.csv](pure-pressure.csv).
The Java test writes the native endpoints, native label and closure residuals
to `target/hv-water-nbutane-native.csv`.
