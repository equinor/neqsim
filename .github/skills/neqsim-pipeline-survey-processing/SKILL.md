---
name: neqsim-pipeline-survey-processing
version: "0.1.0"
description: "Educational as-built pipeline survey processing that turns raw survey rows (KP, depth to top of pipe, seabed depth, easting/northing or latitude/longitude) into a cleaned, sign-normalised, resolution-filtered pipeline profile with flagged erroneous points, free-span and cover/burial candidates, a repeat-survey change comparison, a traceable processing log, and an elevation profile a NeqSim pipe model can consume. USE WHEN: a task needs a public, screening-level pipeline profile built from survey or inspection data before detailed DNV-RP-F105 free-span, DNV-RP-F109 on-bottom-stability, DNV-RP-F114 pipe-soil, or NeqSim thermal-hydraulic analysis."
last_verified: "2026-08-10"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Pipeline Survey Processing

A pipeline hydraulic or integrity model needs an elevation profile. The profile
almost always starts life as a survey file: thousands of rows of KP, depth to top
of pipe, seabed depth and coordinates, produced by a different discipline, in a
different datum, with null markers, duplicated stations, sections that belong to a
neighbouring line, and a handful of points that are simply wrong.

The step from that file to a usable profile is where the numbers are decided, and
it is normally done by hand in a spreadsheet with no record of what was done. This
skill makes that step explicit and repeatable: every filter, sign flip, projection
and rejection is written into a processing log that travels with the result, so a
reviewer can see how the profile in front of them was produced.

It stops at the profile. Free spans and burial intervals are reported as geometric
*candidates* for a qualified assessment - it does not perform modal, VIV, fatigue
or stability analysis.

## When to Use

- A survey or inspection export must become a pipeline elevation profile for a
  NeqSim pipe model.
- Erroneous points, null markers or duplicated stations need to be identified
  before a profile is trusted.
- Only part of a surveyed line is in scope, and the section between two
  structures must be extracted with the trim recorded.
- Free-span or cover candidates need to be located along KP as input to a
  DNV-RP-F105 or DNV-RP-F114 assessment.
- Two surveys of the same line, taken years apart, need a like-for-like change
  comparison.
- The survey lacks outer diameter and a value has to be supplied manually with
  the override recorded as a data gap.

## When *Not* to Use

- As a free-span assessment. Span candidates here are geometry only; modal
  response, VIV screening and fatigue belong to DNV-RP-F105 and to a qualified
  pipeline engineer.
- As a positioning or datum authority. This skill normalises a sign convention
  and projects coordinates for screening; it does not perform geodetic datum
  transformation and must not be used to reconcile survey datums.
- To conclude that a pipeline has moved. A change between surveys can be datum,
  tide, or survey uncertainty as easily as real movement.
- On confidential survey files in a public workspace. Inputs must be public,
  synthetic, or approved for open-source use.

## Inputs

| Input | Meaning |
|---|---|
| `records` | Survey rows, each a mapping with `depth_to_top_m` and either `kp_m` or a coordinate pair |
| `depth_to_top_m` | Depth to top of pipe, positive-down or negative-up - the convention is detected and normalised |
| `seabed_depth_m` | Optional seabed depth at the same station; required for span and cover screening |
| `easting_m` / `northing_m` | Optional projected coordinates; used to derive KP when it is absent |
| `latitude_deg` / `longitude_deg` | Optional geographic coordinates; swapped column order is detected and corrected, then projected |
| `outer_diameter_m` (row or argument) | Pipe outer diameter, from the survey or supplied as a user override |
| `pipeline_id`, `survey_id` | Identifiers carried into the result for traceability |
| `start_kp_m`, `end_kp_m` | Optional section trim, for example between a flowline end and a hot-tap |
| `minimum_kp_spacing_m` | Resolution filter; closer stations are dropped |
| `outlier_threshold` | Robust sigma multiple above which a point is flagged as erroneous |
| `minimum_residual_m` | Physical floor on the flagging residual, so survey noise is never flagged |
| `smoothing_window` | Odd rolling-median window used as the smoothing reference |
| `span_gap_threshold_m` | Gap above seabed at which a station counts towards a span candidate |
| `change_threshold_m` (compare) | Depth change above which a repeat-survey interval is reported |

## Outputs

| Output | Contents |
|---|---|
| `SurveyProfileResult.points` | Retained, ordered, sign-normalised survey points |
| `smoothed_depth_m` | Rolling-median reference used for outlier detection |
| `flagged_points` | Erroneous points with KP, depth, residual and reason |
| `kp_profile_m` / `elevation_profile_m` | The profile itself, elevation being negated depth |
| `route_length_m`, `max_slope_deg`, `slope_warning` | Section length and slope screening (`ok`, `watch`, `high`) |
| `span_candidates` | Free-span candidates with start/end KP, length, max and mean gap |
| `exposed_length_m`, `longest_span_m` | Roll-up of the span candidates |
| `buried_intervals`, `minimum_cover_m` | Cover/burial intervals and the controlling minimum cover |
| `outer_diameter_m`, `outer_diameter_source` | Diameter and whether it came from `survey`, `user_override` or is `missing` |
| `depth_convention` | `positive-down` or `negative-up` as detected in the input |
| `raw_point_count`, `retained_point_count`, `rejected_point_count` | Filtering audit counts |
| `processing_log` | Every operation performed, in order |
| `data_gaps` | Missing evidence that a reviewer must close |
| `SurveyComparisonResult` | Common KP grid, per-station depth change, max lowering and lifting, changed intervals |
| `to_neqsim_elevation_profile` | Evenly spaced `leg_positions_m` and `elevation_profile_m` for a NeqSim pipe model |

## Engineering Method

**Sign convention before anything else.** Survey depths appear both as positive
metres below sea level and as negative elevations. The convention is inferred from
the sign of every depth in the file and normalised to positive-down; mixed signs
are refused rather than guessed, because a silent sign error inverts every slope
in the profile.

**Validity, then order, then resolution.** Non-finite values and the common survey
null markers (`-999`, `-999.25`, `-9999`, `9999`) are removed first. Rows are then
sorted on KP and non-increasing stations dropped, so the profile is strictly
monotonic. Only then is the resolution filter applied, keeping the first station
of every `minimum_kp_spacing_m` window plus both endpoints - decimating before
sorting would delete real stations and keep duplicates.

**Erroneous points by robust residual.** The depth series is compared against a
rolling median, and a point is flagged when its residual exceeds
`outlier_threshold` times a robust sigma derived from the median absolute residual
(`1.4826 x MAD`). A median reference and a MAD scale are used rather than a mean
and a standard deviation because a cluster of bad points would inflate both and
hide itself. Points within half a smoothing window of each end sit on a truncated
window, so their residual carries the local trend rather than an error; they are
excluded from assessment instead of being flagged. Flagged points are reported,
never silently deleted - a run of flags in one area is usually a survey-quality
signal, not noise - but they are excluded from the span and cover geometry,
because a depth that has been called wrong must not then be used to measure a gap.

**Coordinates.** Latitude/longitude columns are checked for swapped order, which
is detectable because a latitude cannot exceed 90 degrees, then projected onto a
local equirectangular frame about the first point. That is adequate for deriving
KP and screening geometry over a flowline and explicitly not adequate for
positioning work.

**Spans and cover from one signed quantity.** With both depths present,
`cover = depth_to_top - seabed_depth` in the positive-down frame. Positive cover
means the pipe top sits below the seabed and the station belongs to a burial
interval; negative cover is a gap, and consecutive stations whose gap exceeds
`span_gap_threshold_m` are grouped into a span candidate with its length, maximum
and mean gap. Grouping consecutive stations, rather than counting points, is what
makes the output comparable to a span list.

**Repeat surveys on a common grid.** Two processed surveys rarely share stations,
so both are linearly interpolated onto a common KP grid over their overlap and
differenced. Positive change means the pipe top sits deeper in the repeat survey.
Intervals exceeding `change_threshold_m` are grouped and labelled `lowering` or
`lifting`; the skill deliberately does not call this movement, because datum and
tide differences produce the same signature.

## Python Usage Pattern

```python
from pipeline_survey_processing import PipelineSurveyProcessor

processor = PipelineSurveyProcessor(
    minimum_kp_spacing_m=5.0,
    outlier_threshold=4.0,
    minimum_residual_m=0.1,
    smoothing_window=11,
    span_gap_threshold_m=0.05,
)

records = [
    {"kp_m": 0.0, "depth_to_top_m": -298.4, "seabed_depth_m": -298.2},
    {"kp_m": 5.0, "depth_to_top_m": -298.2, "seabed_depth_m": -298.0},
    {"kp_m": 10.0, "depth_to_top_m": -999.25},          # null marker, removed
    {"kp_m": 15.0, "depth_to_top_m": -240.0, "seabed_depth_m": -297.7},  # flagged
    {"kp_m": 20.0, "depth_to_top_m": -297.5, "seabed_depth_m": -297.3},
]

result = processor.process(
    records=records,
    pipeline_id="1192-Y-101",
    survey_id="2010-survey",
    start_kp_m=0.0,
    end_kp_m=20.0,
    outer_diameter_m=0.3239,          # survey had no OD; recorded as a data gap
)

print(result.depth_convention)        # negative-up
print(result.rejected_point_count)
print([point.kp_m for point in result.flagged_points])
print(result.span_candidates)
for entry in result.processing_log:
    print(entry)

# Handoff into a NeqSim pipe model
handoff = PipelineSurveyProcessor.to_neqsim_elevation_profile(result, section_count=20)
# from neqsim import jneqsim
# pipe = jneqsim.process.equipment.pipeline.TwoFluidPipe("flowline", stream)
# pipe.setElevationProfile(handoff["elevation_profile_m"])

# Repeat-survey comparison
older = processor.process(records=records, pipeline_id="1192-Y-101", survey_id="2006-survey")
change = processor.compare(baseline=older, repeat=result, change_threshold_m=0.2)
print(change.max_lowering_m, change.max_lifting_m, change.changed_intervals)
```

## Validation Checklist

- The depth convention reported by the result matches the survey report.
- `rejected_point_count` is explainable: nulls, duplicates and resolution
  filtering account for it, and no unexpected bulk rejection occurred.
- Flagged points have been inspected rather than deleted, and clusters of flags
  are explained by a survey-quality note.
- The section trim matches the two structures the study is scoped to, and no
  neighbouring line is included.
- `outer_diameter_source` is `survey`, or the override is traced to a line list
  or pipe class entry.
- Every entry in `data_gaps` is closed or carried into the report as an open item.
- Span candidates are handed to a DNV-RP-F105 assessment before being quoted as
  free spans.
- A repeat-survey change is reconciled against datum, tide and positioning basis
  before it is described as pipeline movement.
- The processing log is stored with the profile in the task evidence.

## Common Mistakes

- Decimating or smoothing before sorting on KP, which keeps duplicates and drops
  real stations.
- Detecting outliers with a mean and standard deviation, so a cluster of bad
  points raises the threshold and hides itself.
- Flagging on a robust sigma with no physical floor, so a smooth section reports
  centimetre-level noise as erroneous.
- Treating flagged points as noise to delete; they are frequently the finding.
- Flagging on a robust sigma with no physical floor, so a very smooth section
  reports centimetre-level survey noise as erroneous.
- Mixing positive-down depth and negative-up elevation in one file and letting
  the model average across the sign change.
- Reporting the count of stations above the gap threshold as the number of spans
  instead of grouping consecutive stations into intervals.
- Letting a point that has been flagged as erroneous set the maximum gap or the
  minimum cover.
- Using the projected coordinates from this skill for positioning or datum work.
- Comparing two surveys station by station without interpolating onto a common
  grid, so a KP offset appears as a depth change.
- Quoting a geometric span candidate as an acceptable or unacceptable span.

## Limitations

- Screening only; it is not an as-built integrity assessment and it does not
  establish adequacy or compliance.
- No geodetic datum transformation, tide correction or positioning-uncertainty
  treatment.
- The smoothing reference is a rolling median, not the vendor spline that a
  survey processing package applies; residuals will differ in detail.
- Points within half a smoothing window of each end are not assessed for
  outliers, so an error at the very start or end of a section will not be flagged.
- A gap that spans a single station reports a length of zero, because span length
  is measured between the first and last gap station.
- Span candidates carry no modal, VIV, fatigue or stability content.
- Embedment is inferred from cover only; no soil model, lay-effect or remoulding
  is applied.
- The skill performs no file or network access; records are supplied by the caller.

## References

- NeqSim: https://github.com/equinor/neqsim
- NeqSim Community Skills: https://github.com/equinor/neqsim-community-skills
- Companion skills: `neqsim-pipe-route-profile`, `neqsim-bathymetry-profile-screening`,
  `neqsim-subsea-layout-geometry`, `neqsim-pressure-drop-screening`
- NeqSim Java handoff: `neqsim.process.equipment.pipeline.PipeBeggsAndBrills`,
  `neqsim.process.equipment.pipeline.TwoFluidPipe.setElevationProfile`,
  `neqsim.process.engineering.calculation.DnvRpF105FreeSpanScreeningKernel`,
  `neqsim.process.engineering.calculation.DnvRpF109OnBottomStabilityKernel`,
  `neqsim.process.engineering.calculation.DnvRpF114PipeSoilInteractionScreeningKernel`
- Public standards for the downstream assessment: DNV-RP-F105 (free spanning
  pipelines), DNV-RP-F109 (on-bottom stability), DNV-RP-F114 (pipe-soil
  interaction).
