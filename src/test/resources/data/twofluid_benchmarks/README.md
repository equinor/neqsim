# TwoFluidPipe External Benchmark Corpus

This directory contains CSV fixtures for the `TwoFluidBenchmarkHarness` schema:

```text
case,time_s,position_m,variable,value,abs_tolerance,rel_tolerance,source
```

The checked-in files are sample corpus entries that document the expected format for OLGA,
LedaFlow, and field-historian exports. Replace the numeric values with project-approved exported
data before using them as validation evidence.

Recommended sources:

- `olga_flow_step_export_sample.csv` - dynamic line-pack or flow-step export from OLGA.
- `ledaflow_slugging_export_sample.csv` - riser or terrain-slugging export from LedaFlow.
- `field_arrival_trend_sample.csv` - measured arrival pressure, temperature, and holdup-related
  trend points from field or laboratory data.

Use case acceptance metrics should be declared in the study report before comparing against these
files. Typical metrics are pressure drop, arrival pressure, holdup profile, slug frequency, liquid
inventory, and arrival time.