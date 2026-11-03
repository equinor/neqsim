# MEG/water/natural-gas primary measurement receipts

Kruger et al. (2018), DOI [10.1021/acs.jced.8b00495](https://doi.org/10.1021/acs.jced.8b00495),
report 11 VLE states for a 20-component mixture. This fixture retains factual
numeric data from the public institutional accepted manuscript and its supplement.
It supplies measurement evidence for future light-gas/polar qualification, not
HV parameters, an EOS calibration or a physical accuracy threshold. The source
uses CPA; none of its coefficients is imported. Existing SRK/PR-HV profiles,
selected pairs, alpha models, defaults and CLASSICAL kij remain unchanged.

## Sources and contents

The [DTU record](https://orbit.dtu.dk/en/publications/multicomponent-vaporliquid-equilibrium-measurement-and-modeling-o/)
links both documents. `provenance.json` records their public URLs, downloaded
hashes, sizes, versions, visual inspection and exact table/page locators. Only
numeric transcriptions and source metadata are redistributed; no manuscript,
supplement document or table artwork is included. The manuscript cover is generated
at download time, so its full-file hash identifies this retrieval, not every future
download. Final-publication/version reconciliation remains open.

| File | Source | Retained meaning |
| --- | --- | --- |
| `gas-feed.csv` | Table 4, printed 10/PDF 11 | All 18 loading-gas components; molar percent |
| `states.csv` | Table 5, printed 11/PDF 12 | T, P in MPa, mass-fraction purity and loading volumes; separate nominal Tables 9/10 T/P |
| `observations.csv` | Tables 9/10, printed 21/22/PDF 22/23; S1/S2 | 220 original slots: 218 measured values and two missing vapor-MEG entries |

The 218 observations comprise nine vapor-MEG, 11 vapor-water, 99 dissolved-gas
mole fractions and 99 dimensionless gas partition coefficients **y/x**. Table 9
values are molar ppm; conversion to mole fraction divides by one million.
The author reports the mean of two experiments at each condition; individual
replicates are not supplied. Concentrations and partition coefficients from the
same experiments are correlated quantities, not independent validation samples.
The tables retain nine gas species through pentanes because those measurements
belong to the same experiments. This does not select any new heavy or inhibitor
HV pair. Loading T spans 288.18–323.17 K and P 5.99–12.51 MPa; these are discrete
states, not a continuously qualified operating range.

## Limits that must survive reuse

* Tables S1/S2 contain relative standard deviations in percent, **one sigma**.
  They are repeatability values, not a complete combined uncertainty or an EOS
  acceptance tolerance. Table 2 separately reports apparatus uncertainties of 3%
  vapor water, 12% vapor MEG and 2% liquid water without explicit coverage factors.
  Their relationship and calibration covariance need reconciliation. Its density
  uncertainty is printed with dimensionally inconsistent units `g mol^-1`;
  that value is retained in metadata and is not used for a density conversion.
* Vapor MEG is missing at experiments 6/9. Three RSD cells round to zero; this
  does not mean exact measurements. Footnote b excludes water experiments 10/11
  only from the author's average error. Both measurements remain in this fixture.
* Table 5 specifies MEG feed `>0.998` **g/g** for experiments 10/11. Table 3 instead
  gives MEG purity 99.8 **mol%**, with measured water `<0.003%` by KF, basis
  unspecified. The modeling paragraph on printed 23/PDF 24 refers to **H2O** 99.8 mol%.
  These conflicting source statements are retained explicitly, not repaired by
  assuming exact pure MEG, exchanging species or assigning all impurity to water.
* The loading volumes are not independently measured overall molar amounts. A
  molar-feed reconstruction requires a declared gas compressibility, liquid
  density, purity interpretation and propagated uncertainty. No overall feed is
  created here; rounded x and K do not complete a normalized phase composition.
* Loading T/P differ from nominal result labels. They are joined by experiment
  number, never by nearest-state matching. Table 4 totals 100.00022 mol% as printed;
  no silent normalization is performed. Component 15 has the duplicated printed
  alias 22DMC4, while its name/CAS 79-29-8 identify 2,3-dimethylbutane. Heptanes and
  octanes are source totals without pure CAS identities. No mapping is invented.

## Executable audit

From the repository root:

```
python3 devtools/audit_huron_vidal_natural_gas.py
python3 -m unittest discover -s devtools -p test_audit_huron_vidal_natural_gas.py -v
```

The read-only, standard-library audit checks pinned bytes and source identities,
complete ordering, directions, units, missing cells, inequalities and conflicts.
The CLI example is executed by the test suite, including with Python `-O`.
Mutation tests prevent reciprocal K, mass-ppm substitution, self-rehashed data,
invented missing values, lost footnotes and uncertainty/qualification promotion.
Complete uncertainty, feed-limit, prerequisite and apparatus statements are checked,
including their interpretation text. Preserving list lengths and numeric files
cannot authorize contradictory claims about exact measurements, discarded water
results, measured molar feeds or cleared source prerequisites.

A subsequent physical batch needs complete EOS-specific directed HV tuples and
paired alpha/critical inputs, source/version and full uncertainty reconciliation,
and a justified feed protocol. It must assert all applicable native
ThermodynamicModelTest identities/checkNumerically, independent finite differences,
fresh/repeated/changed states, both-direction inhibitor partition and strict phase
and component closure. Matched CLASSICAL/HV initialization and full-flash timing
must distinguish thermodynamic/solver work from overhead. This source-only change
has no runtime speedup or absolute-optimality claim. It does not qualify methanol,
oil partition, hydrate dissociation, ice, brine or extrapolation, and does not
complete campaign4576 or its outstanding physical criteria.
