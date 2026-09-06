# Measured horizontal air-water slug data

## Attribution and license

Abdalellah O. Mohmmed, Mohammad S. Nasif, Hussain H. Al-Kayiem,
*Experimental data for the slug two-phase flow characteristics in horizontal pipeline*,
Data in Brief 16 (2018), 527–530; online 27 November 2017.
DOI: [10.1016/j.dib.2017.11.026](https://doi.org/10.1016/j.dib.2017.11.026).
PMCID: [PMC5734701](https://pmc.ncbi.nlm.nih.gov/articles/PMC5734701/).

Copyright © 2017 The Authors. The source article and supplementary tables are
licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
The CSV is an attributed transcription/format conversion of those tables under
the same license; this data attribution is separate from NeqSim's code license.
No numerical outcome was generated from a correlation or a NeqSim simulation.

Retrieved 2026-09-05 from the official Europe PMC service:

- [Article XML](https://www.ebi.ac.uk/europepmc/webservices/rest/PMC5734701/fullTextXML)
- [Supplementary archive](https://www.ebi.ac.uk/europepmc/webservices/rest/PMC5734701/supplementaryFiles)

## Contents and extraction

All numeric experimental rows are included, with exact OOXML worksheet paths
and cell addresses. No plot digitization, averaging, outlier deletion,
interpolation, relabeling of gas velocities, or model fitting was performed.
Numeric cell text is retained, including the spreadsheet's binary-decimal
representations. The supplementary workbook contents, not the contradictory
file ordering in the article prose, determine the quantity labels:

| Source workbook | Actual table content | Rows | SHA-256 |
|---|---|---:|---|
| mmc2.xlsx | Translation velocity | 15 | 671182fc190c289ccb0289e814ebfd654ed80cb4933937c5efed2031d3c85058 |
| mmc3.xlsx | Frequency at two stations | 30 | 8db0033f5585dd99762df0d9364a5a7a6dfbc1aa433a9a43b3468d90696595e6 |
| mmc4.xlsx | Slug length/diameter at two stations | 30 | 151b4d323e4cb85036edcc73a1e0169dab2831d26d77c4f0632e22abaece0fc0 |

The CSV contains 75 observations, not 75 distinct operating points. Each
quantity and station has its own independently preserved input grid. Matching
must use the explicit superficial velocities, never row offsets between files.
CSV SHA-256: `7da56f1510b6bf81399f53735add8bcba8feecac6491fda212c139d57161e133`.

## Conditions and limitations

- Horizontal 8 m air-water rig, room temperature 24 °C, atmospheric pressure
  1.013 bar; the fixture converts the latter to 297.15 K and 101300 Pa.
  These are the article conditions, not measured time-varying boundaries.
- Use inner diameter **0.074 m**, explicitly stated in the length workbook
  legend and in the cited method paper, rather than converting the article's
  nominal "3 in." to 0.0762 m. The method paper is Mohmmed et al.,
  [10.1016/j.flowmeasinst.2016.06.016](https://doi.org/10.1016/j.flowmeasinst.2016.06.016).
- Upstream frequency/length sheets say **54D**, whereas the data article says
  **58D**. Both are preserved as station bounds 54–58D with
  `UPSTREAM_LOCATION_CONFLICT`. This interval represents a documentation
  conflict, not a probabilistic positional uncertainty. Downstream 81D is
  consistent. Do not claim a unique experimentally specified growth distance.
- The velocity workbook does not specify a station. Its station bounds remain
  blank/NaN and carry `VELOCITY_STATION_UNSPECIFIED`. It describes a tracked
  liquid slug's translation, not phase velocity. The freely accessible primary
  method description does not unambiguously specify front, tail, or centroid;
  report any simulation choice explicitly and retain front/tail outputs.
- Some tabulated gas velocities exceed the article prose upper bound 2.8 m/s;
  those rows remain present with `EXCEEDS_ARTICLE_PROSE_GAS_RANGE`.
- The first velocity group has a tabulated mixture-velocity column that differs
  from `jG + jL` by 0.002 m/s. The fixture uses the separately tabulated measured
  superficial velocities; it does not copy that derived mixture column.
- No row-specific standard deviation, sample count, confidence interval,
  uncertainty coverage factor, or acquisition duration is in these workbooks.
  `NO_POINT_UNCERTAINTY` applies to every row. The cited measurement-method
  paper describes a maximum 6.7% discrepancy between two image-processing
  methods; this is **not** a 6.7% experimental confidence interval for these
  points. The data article's flow-meter accuracy text is malformed ("70.5%");
  it must not be silently reinterpreted as ±0.5%.
- There are no measured film holdups/velocities, gas fractions in slugs, inlet
  slug distributions, or pressure time series in these spreadsheets. Do not
  generate these quantities from correlations and label them experimental.

## Predeclared comparison contract

Before model comparisons, the engineering screening targets are fixed at
20% relative error per translation-speed observation, 30% per length
observation, and 30% per frequency observation. These targets are development
acceptance criteria, **not published experimental uncertainties**, and must not
be widened after observing errors. Report every applicable row and missing or
invalid predictions; report scoped subsets by name and count. The complete
dataset remains available even when some quantities cannot yet be predicted.

Infer modeled translation from displacement/elapsed time or interpolated
station arrival times across multiple accepted steps. A controlled tracker
with prescribed homogeneous flow tests its kinematic closure only. It does
not validate the coupled pressure/phase-transport solver. An initially seeded
slug length is an input and cannot count as a predicted length observation.
For full-pipe tests, freeze inlet perturbations, section spacing, time step,
sample interval, event-detection definitions, and warm-up independently of the
measured outputs. Compare grid/time refinement and multiple seeds before
claiming statistical prediction. The source ambiguities above remain limits
even if all engineering targets are met.
