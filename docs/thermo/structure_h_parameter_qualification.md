---
title: "Structure-H hydrate data and parameter qualification"
description: "Reproduce preliminary fits to experimental structure-H dissociation data and distinguish system correlations from component Langmuir or Kihara parameters."
keywords: "structure H, sH, hydrate, experimental data, parameter fitting, Langmuir, Kihara, COMP, dissociation pressure, qualification"
---

Experimental data are available for a bounded first step in
[issue #4730](https://github.com/equinor/neqsim/issues/4730). The repository
contains 24 experimental markers extracted from a primary supporting-material
figure, provenance, an offline fitting tool, and numerical checks. The resulting
coefficients describe complete methane–water–promoter systems. They do not
qualify component parameters for COMP or enable sH in a hydrate flash.

NeqSim's executable hydrate models still support sI and sII. Their parameters,
structure selection, and numerical paths are unchanged by this dataset.

## Primary source and data basis

The source is *Enlargement of Structure-H Clathrate Hydrates by New Large
Molecular Guest Substances and Lattice Size Limitation*, Journal of Chemical &
Engineering Data (2023), [DOI 10.1021/acs.jced.3c00252](https://doi.org/10.1021/acs.jced.3c00252).
The measurements appear as colored markers in Figure S3, page 4 of the
[supporting material](https://acs.figshare.com/articles/journal_contribution/23557761).

The figure has vector markers. Their centers and axis coordinates were
extracted directly: the horizontal coordinate is linear in reciprocal kelvin,
and the vertical coordinate is logarithmic in reported MPa. Curve segments
and legend circles were excluded. These are reconstructed observations from
a published figure, rather than author-supplied numerical table entries.

The frozen files are
`src/test/resources/data/chemistry_benchmarks/structure_h_jin2023_digitized.csv`
and its adjacent `.json` provenance record. They retain source DOI, figure/page,
source-PDF and derived-CSV SHA-256 checksums, marker coordinates, calibration,
chemical names, counts, and unresolved qualification details. Decimal digits in
the extracted coordinates aid reproduction; they do not establish experimental
precision. The original PDF is not redistributed. Its Figshare record labels
the source artifact CC BY-NC 4.0; the extracted factual coordinates are attributed.

The retrieved supplement identifies four sH systems, but does not establish a
complete fluid-phase/composition basis, the absolute/gauge pressure convention,
or the measurement-uncertainty budget. Pressures therefore retain the label
**reported MPa**. No bara conversion, uncertainty weights, or inhibitor/multiphase
qualification is inferred.

## Reproducible preliminary fit

Run from the repository root with Python 3.11 or later:

```bash
python3 devtools/fit_structure_h_dissociation.py --output /tmp/structure-h-fit.json
```

This command is offline and uses the Python standard library. The output
reports `source_pdf_audit: "NOT_RUN"` because a default run verifies the frozen
dataset and its checksum, without claiming to re-read the PDF.

For a byte-identical copy of the source PDF, the optional audit re-extracts
the axes and markers and compares every observation with the frozen CSV:

```bash
python3 devtools/fit_structure_h_dissociation.py --source-pdf /path/to/je3c00252_si_001.pdf --output /tmp/structure-h-fit.json
```

The audit requires PyMuPDF, already included in the devtools environment. The
[source download](https://ndownloader.figshare.com/files/41329983) must match
the checksum in the provenance record. No download occurs during fitting or CI.

The fitted equation is

$$\ln(P_{\mathrm{reported}}/\mathrm{MPa})=A-B/T$$

Here $T$ is temperature in K, $A$ is dimensionless, and $B$ is in K. Least squares
uses log pressure and a centered reciprocal-temperature coordinate to reduce
cancellation. The coefficients apply only to each complete source system and
the extracted temperature interval; extrapolation is unqualified.

| Large guest | Points | Temperature range (K) | A | B (K) | All-point pressure AARD (%) | Leave-one-out pressure AARD (%) |
|---|---:|---:|---:|---:|---:|---:|
| Iodocyclopentane (ICP) | 7 | 275.93–286.26 | 35.24082 | 9515.04 | 0.933 | 1.448 |
| Chlorocycloheptane (ClCHP) | 6 | 275.97–288.98 | 37.12752 | 10097.06 | 1.634 | 2.756 |
| Bromocycloheptane (BrCHP) | 5 | 277.78–287.96 | 35.71212 | 9650.02 | 0.882 | 1.724 |
| trans-1,4-Dichlorocyclohexane (t14DClCH) | 6 | 276.28–287.48 | 34.25062 | 9264.66 | 1.212 | 1.982 |

AARD is average absolute relative pressure deviation against extracted markers.
The largest individual leave-one-out error is 5.19%. The fixed internal holdout
uses the second and penultimate temperature in each system; the remaining
observations are used for fitting. Leave-one-out fitting excludes and refits
for every prediction target. Both are internal checks from the same publication,
not independent-study validation or experimental-accuracy claims.

The standard-library fitter reproduces a separately computed NumPy
least-squares result. Tests also cover a prescribed analytical curve, narrowly
spaced temperatures, invalid and rank-deficient inputs, corrupted data,
coordinate calibration, a deliberately perturbed marker, and reversed
pressure-temperature trends. The documented CLI runs in the test suite.

## Why these coefficients cannot be inserted into COMP

COMP hydrate fields describe guest–cage interactions. Equilibrium pressure of
a methane–water–promoter system is not a component/cavity Langmuir constant.
The JSON always reports `coefficient_type: "METHANE_WATER_PROMOTER_SYSTEM_CORRELATION"`
and `accepted_for_COMP: false`. It reports the physical component fit as blocked,
even when the preliminary pressure curve has small residuals.

For a vdWP sH model, the hydrate-water reference involves all three cavity types:

$$\ln f_w^H=\ln f_w^\beta-\sum_c\nu_c\ln\left(1+\sum_j C_{jc}f_j\right)$$

Here $f_w^H$ is hydrate-water fugacity, $f_w^\beta$ is empty-host water fugacity,
$f_j$ is fluid guest fugacity, $C_{jc}$ is the guest/cavity Langmuir constant,
and the cavity counts per water molecule are $\nu_c=3/34,2/34,1/34$ for
small, medium, and large sH cavities. Fugacity and inverse-fugacity units must
match. This equation states a model requirement; an executable sH equilibrium
model is not supplied by this fitting tool.

An unknown host reference, helper-gas occupancy, and large-guest occupancy can
compensate for each other in a pressure-curve fit. In the high-occupancy limit,
the logarithmic terms mainly constrain combinations of host and guest
parameters. A low curve residual therefore cannot establish unique physical
component coefficients.

The remaining physical calibration must:

1. Fix a source-qualified sH host reference, cage geometry, and helper-gas
   parameterization, with consistent units and thermodynamic reference states.
2. Establish measured compositions, phase basis, pressure reference, and
   uncertainty; obtain independent holdouts. Prefer numerical tables when available.
3. Calculate water and guest fugacities using the intended NeqSim EOS/mixing
   rule and all relevant gas/oil/aqueous phases.
4. Fit identifiable guest Langmuir or Kihara parameters. Check parameter
   sensitivity and correlation, occupancy bounds, fugacity residuals,
   derivatives, and independent predictions.
5. Populate COMP only for supported components and integrate three-cavity
   structure selection and conserved amount calculations together. Preserve
   sI/sII numerical results and measured performance.

Missing data remain missing. Existing sI/sII values, copied extended-component
placeholders, and the system-correlation A/B values are not promoted to sH
component defaults. Issue #4730 remains open for this physical implementation
and qualification.

## Related documentation

- [Hydrate models](./hydrate_models.md)
- [COMP database and provenance](./component_database_guide.md)
- [Hydrate flash operations](../thermodynamicoperations/hydrate_flash_operations.md)
