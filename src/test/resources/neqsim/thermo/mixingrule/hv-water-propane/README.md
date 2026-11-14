# Staged PR-HV water–propane assessment

The [Yushchenko and Brusilovsky PR-HV model][tuple] uses water as component 1
and propane as component 2. Table 1 gives directed 12/21 energies of
−1584/4750 K, direction-specific offsets of −0.44/−3.3 and
nonrandomness 0.07. The model uses infinite-pressure covolume-weighted
modified NRTL, with tau equal to D/T plus the offset. The executable
assessment uses ordinary PR alpha and declared NeqSim pure inputs.

| Component | Critical temperature (K) | Critical pressure (bara) | Acentric factor |
| --- | ---: | ---: | ---: |
| Water | 647.30 | 220.89 | 0.344 |
| Propane | 369.83 | 42.48 | 0.1523 |

The reported fit emphasizes vapor water at 323.15–423.15 K and
25–700 bara. The authors' exact pure inputs, parameter uncertainty and a
composition validity envelope remain unresolved.

## Experimental coordinates

Kobayashi and Katz measured the aqueous propane mole fractions.
The [NIST-hosted IUPAC compiler PDF][compilation], printed page 46
(file page 8), reproduces the primary measurements while explicitly
excluding the second propane-rich liquid. The 18 rows were verified
against the rendered compiler page. The ACS original table was
inaccessible during this assessment. The compiled PDF SHA-256 is
`729df3428c6ba98a661c07fc4a02cd05f83c7b34475e5e9cf7dcdc09a2665394`.

Use the primary Fahrenheit and total psia values, not the calculated
partial pressure column. Convert temperature by (°F − 32) × 5/9 + 273.15
and pressure by 0.06894757293168 bara per psia. Multiply the last
observation column by 10⁻⁵ to recover the mole fraction. Use 170 °F at
181 psia as shown in the rendered table. The text extraction had
associated that row with the preceding 133 °F isotherm.

The compiler records estimated errors of 0.3 K, 1% in pressure and
3% relative in aqueous mole fraction. These estimates do not define a
statistical confidence interval. No measured overall feed, vapor-water
values or covariance are provided in this compilation. The calculated
vapor mole fractions are retained as predictions.

## Reproduction and results

Run `python3 devtools/audit_huron_vidal_propane.py` from the repository
root to reproduce [assessment.csv](assessment.csv). Add
`--match-water-pressure` to reproduce
[water-pressure-control.csv](water-pressure-control.csv). The independent
cubic solver calculates both phase compositions from both log-fugacity
equations using mechanically stable roots. It verifies a second
extensive-attraction difference step (5 × 10⁻¹¹ mol versus 10⁻¹⁰ mol).
The [native flash receipt](native-assessment.csv) uses a synthetic feed
of 0.5 mol water and 0.5 mol propane on master
`4efb4fa3b5c9cd3baa20fb07464bc4a3cfceeede`. The temporary diagnostic
runs 18 fresh water-first and 18 retained propane-first TP flashes.
It asserts both phase endpoints against the independent predictions at
5 × 10⁻⁸ relative (10⁻¹³ floor), the five thermodynamic identities at
10⁻⁸ and the existing refined numerical check at 10⁻⁶. Native maximum
component balance, normalization and log-fugacity residuals are
3.78 × 10⁻¹⁵, 2.22 × 10⁻¹⁶ and 2.00 × 10⁻¹¹. Phase amounts are
calculated for the synthetic feed.

| Case | Observation count | Aqueous propane AARD |
| --- | ---: | ---: |
| Within nominal source T/P | 5 | 12.62% |
| Outside nominal source T/P | 13 | 6.73% |
| In-window local matching of IAPWS pure-water pressure | 5 | 11.66% |

The five in-window predictions are 10.31–14.48% low with the declared
inputs. Run the same command with `--uncertainty-envelope` to reproduce
the input sensitivity. Repeating each with temperature shifted by ±0.3 K and total
pressure by ±1% leaves even the highest calculated aqueous fraction
below the measured value minus the compiler's relative 3% estimate.
The local pure-water pressure control changes only water alpha at
that temperature and keeps the binary tuple fixed. It yields no
transferable alpha correlation. A separate pure-propane comparison
against the [NIST Antoine correlation][propane-psat] found the ordinary
PR saturation pressure 0.12–0.53% higher at 310.93, 329.26, 349.82
and 360.80 K. These temperatures fall within the correlation's range.

## Independent aqueous propane experiment

Azarnoosh and McKetta (*Petroleum Refiner* 37, 275–278, 1958) measured
aqueous propane using an equilibrated sample, reflux and volumetric gas
collection. The [IUPAC compiler PDF][compilation], printed page 42 (file
page 4), reproduces original measurements. The
[observation file](azarnoosh-mcketta-observations.csv) retains all nine
original rows within the nominal fit T/P window (377.594 and 410.928 K,
26.903–35.280 bara). These rows were transcribed from the rendered table
using the reported Fahrenheit temperature, total psia and mole fraction.
The 511.7 psia row has no compiled partial pressure and has both original
coordinates and measured mole fraction. Pages 43–44 contain smoothed
values. Page 39 classifies the experiment as tentative and recommends no
dataset because overlap between experiments is limited.

Add `--dataset azarnoosh-mcketta` to the commands above to reproduce the
[declared-input assessment](azarnoosh-mcketta-assessment.csv), the
[pure-water pressure control](azarnoosh-mcketta-water-pressure-control.csv)
and the input sensitivity. With declared inputs, all nine predictions are
10.06–17.89% low (14.18% AARD). Matching local pure-water saturation
pressure gives 13.30% AARD. Page 40 gives 1% pressure and 2% compiler
aqueous mole-fraction error estimates. At fixed reported temperature,
the prediction stays below the lower composition estimate at both pressure
offsets for all nine rows. The table gives no temperature error or point
confidence intervals. The [native receipt](azarnoosh-mcketta-native-assessment.csv)
uses the same feed, fresh/repeated/reordered checks and acceptance tolerances
as above. The nine pairs of flashes passed all six model assertions and
matched both independent endpoints. Maximum balance, normalization and
log-fugacity residuals were 1.85 × 10⁻¹⁵, 1.11 × 10⁻¹⁶ and 9.91 × 10⁻¹².

The comparison supports keeping the tuple staged. The publisher's
original propane vapor-water table and its point uncertainty remain
missing. [Alassi et al.][alassi] provides liquid-propane experiments,
with and without hydrates, for a different phase regime. Salt-free
vapor/aqueous results cannot establish liquid-propane partition, ice,
brine, hydrate dissociation or an extrapolation domain.

[tuple]: https://doi.org/10.1016/j.fluid.2015.09.029
[compilation]: https://srdata.nist.gov/solubility/IUPAC/SDS-24/SDS-24-pages_39.pdf
[propane-psat]: https://webbook.nist.gov/cgi/cbook.cgi?Name=propane&cTP=on
[alassi]: https://doi.org/10.1016/j.jngse.2022.104732
