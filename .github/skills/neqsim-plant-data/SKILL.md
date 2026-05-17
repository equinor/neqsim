---
name: neqsim-plant-data
description: "Connecting NeqSim process simulations to plant historian data via tagreader. USE WHEN: reading data from OSIsoft PI or Aspen IP.21 historians, building tag mappings for process equipment, comparing simulated vs measured values, running digital twin loops, integrating NeqSim models with operational data, or extracting event windows for water-hammer screening. Covers tagreader API, tag mapping patterns, data quality handling, mock data generation, model-vs-plant comparison workflows, and valve/pump transient snapshots."
last_verified: "2026-07-04"
---

# Plant Data Integration with Tagreader

Patterns for connecting NeqSim process simulations to real plant data from
OSIsoft PI and Aspen IP.21 historians using the tagreader package.

For P&ID-driven operational studies, also load `neqsim-pid-process-operations`
to map instrument bubbles and valve symbols to logical tag names, infer active
state, and drive steady-state or dynamic NeqSim scenarios.

For water-hammer or liquid-hammer screening, also load `neqsim-water-hammer` and
extract short event windows with inlet pressure, temperature, flow rate, valve
position, pump speed/status, and observed closure/trip timing for MCP
`runWaterHammer`.

## Installation

```bash
pip install tagreader neqsim
```

## Tagreader API Reference

### Listing Available Data Sources

```python
import tagreader

# Discover what historian servers are accessible
pi_sources = tagreader.list_sources("piwebapi")     # OSIsoft PI Web API
aspen_sources = tagreader.list_sources("aspenone")   # Aspen IP.21 REST API
```

For custom historian deployments, supply `url`, `auth`, and optionally
`verify_ssl`:

```python
from requests_ntlm import HttpNtlmAuth
auth = HttpNtlmAuth("domain\\user", "password")
sources = tagreader.list_sources("aspenone",
    url="https://api.mycompany.com/aspenone",
    auth=auth, verify_ssl=False)
```

### Creating and Connecting a Client

```python
# PI Web API
c = tagreader.IMSClient("MY_PI_SOURCE", "piwebapi")
c.connect()

# Aspen IP.21
c = tagreader.IMSClient("MY_ASPEN_SOURCE", "aspenone")
c.connect()

# Custom deployment with explicit auth
c = tagreader.IMSClient(
    datasource="myplant",
    imstype="aspenone",
    url="https://api.mycompany.com/aspenone",
    auth=auth,
    verify_ssl=False,
    tz="US/Central"          # default is "Europe/Oslo"
)
c.connect()
```

**Constructor parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `datasource` | Yes | Name of the data source (from `list_sources()`) |
| `imstype` | Yes | `"piwebapi"` or `"aspenone"` |
| `tz` | No | Timezone for timestamps. Default: `"Europe/Oslo"` |
| `url` | No | Server URL for custom deployments |
| `verify_ssl` | No | SSL verification. Default: `True` |
| `auth` | No | Auth object. Default is determined by the configured source |
| `cache` | No | Local disk cache for avoiding re-reads |

### Searching for Tags

```python
# Search by tag name (wildcards supported)
results = c.search("*COMP*232*")

# Search by description
results = c.search(desc="*temperature*")

# Both must match
results = c.search(tag="BA:*", desc="*Temperature*")

# With timeout (avoids long-running searches)
results = c.search("*COMP*", timeout=30)
```

Returns a list of tuples: `[(tag_name, description), ...]`

### Reading Data

```python
tags = ["TAG1.PV", "TAG2.PV"]
start = "01.06.2025 06:00:00"    # dd.mm.yyyy HH:MM:SS
end = "01.06.2025 18:00:00"
interval = 300                     # seconds (5 minutes)

# Interpolated (default) — evenly spaced data points
df = c.read(tags, start, end, interval)

# Time-weighted average per interval
df = c.read(tags, start, end, interval, read_type=tagreader.ReaderType.AVG)

# Minimum value per interval
df = c.read(tags, start, end, interval, read_type=tagreader.ReaderType.MIN)

# Maximum value per interval
df = c.read(tags, start, end, interval, read_type=tagreader.ReaderType.MAX)

# Variance per interval
df = c.read(tags, start, end, interval, read_type=tagreader.ReaderType.VAR)

# Standard deviation per interval
df = c.read(tags, start, end, interval, read_type=tagreader.ReaderType.STD)

# Range (max - min) per interval
df = c.read(tags, start, end, interval, read_type=tagreader.ReaderType.RNG)

# Raw stored values (no interpolation)
df = c.read(tags, start, end, read_type=tagreader.ReaderType.RAW)

# Snapshot (current/latest value, one tag at a time)
df = c.read(["TAG1.PV"], read_type=tagreader.ReaderType.SNAPSHOT)

# Snapshot at specific time
df = c.read(["TAG1.PV"], end_time="01.06.2025 12:00:00",
            read_type=tagreader.ReaderType.SNAPSHOT)
```

**Read parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `tags` | Yes | List of tag names (no wildcards) |
| `start_time` | Yes* | Start of period (*not needed for SNAPSHOT) |
| `end_time` | Yes* | End of period (*optional for SNAPSHOT) |
| `ts` | No | Interval in seconds. Default: 60 |
| `read_type` | No | `ReaderType.INT` (default), `AVG`, `MIN`, `MAX`, `VAR`, `STD`, `RNG`, `RAW`, `SNAPSHOT` |
| `get_status` | No | Include data quality column. Default: `False` |

**Returns:** `pandas.DataFrame` with `DatetimeIndex` and one column per tag.

### Saving Data to CSV (MANDATORY for Task Workflows)

**All plant data read via tagreader MUST be saved as CSV inside the task folder.**
This creates a reproducible data snapshot so analysis can be rerun without
live historian access, and keeps the task self-contained.

```python
import os, pathlib

# ── Resolve task directory (same pattern as notebooks) ──
NOTEBOOK_DIR = pathlib.Path(globals().get(
    "__vsc_ipynb_file__", os.path.abspath("step2_analysis/notebook.ipynb")
)).resolve().parent
TASK_DIR = NOTEBOOK_DIR.parent
REFS_DIR = TASK_DIR / "step1_scope_and_research" / "references"
REFS_DIR.mkdir(parents=True, exist_ok=True)

# ── Read from historian ──
tags = list(TAG_MAP.values())
start = "01.06.2025 06:00:00"
end   = "01.06.2025 18:00:00"
df = c.read(tags, start, end, 300)  # 5-min interpolated

# ── Save raw plant data as CSV ──
csv_path = REFS_DIR / "plant_data_raw.csv"
df.to_csv(str(csv_path))
print(f"Saved {len(df)} rows × {len(df.columns)} tags → {csv_path}")

# ── Save tag mapping alongside the data ──
import json
tag_map_path = REFS_DIR / "tag_mapping.json"
with open(str(tag_map_path), "w") as f:
    json.dump(TAG_MAP, f, indent=2)
print(f"Tag mapping saved → {tag_map_path}")
```

**Loading saved CSV for offline analysis** (no historian needed):

```python
import pandas as pd, json, pathlib

REFS_DIR = TASK_DIR / "step1_scope_and_research" / "references"

# Load data + tag map
df_plant = pd.read_csv(str(REFS_DIR / "plant_data_raw.csv"),
                        index_col=0, parse_dates=True)
with open(str(REFS_DIR / "tag_mapping.json")) as f:
    TAG_MAP = json.load(f)

print(f"Loaded {len(df_plant)} rows, tags: {list(TAG_MAP.keys())}")
```

**CSV naming conventions** — use descriptive filenames with date range:

| Filename | Content |
|----------|---------|
| `plant_data_raw.csv` | Raw historian data (all tags, full period) |
| `plant_data_20250601_20250615.csv` | Date-scoped raw data |
| `plant_data_hourly_avg.csv` | Aggregated hourly averages |
| `plant_data_cleaned.csv` | After quality filtering (NaN removed, outliers clipped) |
| `tag_mapping.json` | Logical name → historian tag mapping |
| `digital_twin_results.csv` | Simulated vs plant comparison output |

### Data Quality

```python
df = c.read(tags, start, end, 60, get_status=True)
# Adds extra columns: TAG1.PV_status, TAG2.PV_status
```

| Status Code | Meaning |
|-------------|---------|
| 0 | Good |
| 1 | Suspect |
| 2 | Bad |
| 4 | Good/Modified |
| 5 | Suspect/Modified |
| 6 | Bad/Modified |

### Aspen IP.21 Tag Maps

For IP.21 tags with maps (controller outputs, setpoints, etc.):

```python
# Tag with map: "tag;map"
df = c.read(["109-HIC005;CS A_AUTO"], start, end, 60)
```

### Time Format

Tagreader accepts multiple timestamp formats via `pandas.Timestamp`:

```
"02.09.2025 23:00:00"      # dd.mm.yyyy HH:MM:SS  (European)
"05-Jan-2025 08:00:00"     # dd-Mon-yyyy HH:MM:SS
"05/01/25 11:30am"          # US short format
"2025-06-01T08:00:00"      # ISO format
datetime(2025, 6, 1, 8)    # Python datetime objects
```

## Tag Mapping Pattern

Map logical process parameters to historian tag names:

```python
TAG_MAP = {
    # Inlet separator
    'inlet_pressure':       'PLANT-20PT0201.PV',
    'inlet_temperature':    'PLANT-20TT0201.PV',

    # Compressor A
    'compA_suction_P':      'PLANT-35PT0101A.PV',
    'compA_discharge_P':    'PLANT-35PT0101B.PV',
    'compA_suction_T':      'PLANT-35TT0101A.PV',
    'compA_discharge_T':    'PLANT-35TT0101B.PV',
    'compA_flow':           'PLANT-35FT0101.PV',
    'compA_power':          'PLANT-35JI0101.PV',
    'compA_speed':          'PLANT-35SI0101.PV',

    # Export
    'export_pressure':      'PLANT-27PT0301.PV',
    'export_flow':          'PLANT-27FT0301.PV',
}
```

### Helper to Extract Values by Parameter Name

```python
import math

def get_plant_value(df, tag_map, param_name, aggregation='mean'):
    """Get a plant value by logical parameter name.

    Args:
        df: DataFrame from tagreader
        tag_map: Dictionary mapping logical names to tag names
        param_name: Logical parameter name (key in tag_map)
        aggregation: 'mean', 'last', 'median', 'min', 'max'

    Returns:
        float value or float('nan') if no data
    """
    tag = tag_map[param_name]
    if tag not in df.columns:
        return float('nan')
    series = df[tag].dropna()
    if len(series) == 0:
        return float('nan')
    if aggregation == 'mean':
        return float(series.mean())
    elif aggregation == 'last':
        return float(series.iloc[-1])
    elif aggregation == 'median':
        return float(series.median())
    elif aggregation == 'min':
        return float(series.min())
    elif aggregation == 'max':
        return float(series.max())
    return float(series.mean())
```

## Naming Conventions for Plant Tags

Common ISA/ISO-style tag naming patterns:

| Prefix | Meaning | Example |
|--------|---------|--------|
| `PT` | Pressure Transmitter | `PLANT-35PT0101A.PV` |
| `TT` | Temperature Transmitter | `PLANT-35TT0101A.PV` |
| `FT` | Flow Transmitter | `PLANT-35FT0101.PV` |
| `LT` | Level Transmitter | `PLANT-20LT0201.PV` |
| `AT` | Analytical Transmitter | `PLANT-20AT0301.PV` |
| `SI` | Speed Indicator | `PLANT-35SI0101.PV` |
| `JI` | Power/Energy Indicator | `PLANT-35JI0101.PV` |
| `.PV` | Process Value (current reading) | — |
| `.SP` | Setpoint | — |
| `.OP` | Output (controller) | — |

Tag structure: `SOURCE-SYSTEMNUMBER_TAGTYPE_SEQUENCE.ATTRIBUTE`

## Active State from P&ID and Historian Tags

Do not infer active equipment or train state from one tag alone. Combine P&ID
topology with independent historian indicators:

| Indicator | Active evidence | Inactive evidence |
|-----------|-----------------|-------------------|
| Flow | above meter noise threshold | zero or near-zero flow |
| Valve position | inlet and outlet path open | inlet or outlet path closed |
| Pressure | follows connected process pressure | static, isolated, or decaying pressure |
| Temperature | process-like trend | ambient or stagnant trend |
| Level | credible level with movement | frozen, bad quality, or maintenance state |
| Rotating equipment | run status, speed, or power | stopped or zero power |

Store the logical P&ID tag map separately from private historian tag names. Save
the task-local binding in `step1_scope_and_research/references/tag_mapping.json`.

## Mock Data for Demo/Testing

When historian access is unavailable, generate realistic mock data:

```python
import numpy as np
import pandas as pd

def generate_mock_plant_data(tag_map, start="2025-06-01 06:00",
                              end="2025-06-01 18:00", freq='5min', seed=42):
    """Generate realistic mock plant data for testing without historian access.

    Returns DataFrame with same structure as tagreader output.
    """
    timestamps = pd.date_range(start, end, freq=freq)
    np.random.seed(seed)
    n = len(timestamps)

    # Default realistic operating ranges for common parameters
    defaults = {
        'inlet_pressure':     (30.0, 0.3),    # bara
        'inlet_temperature':  (48.0, 1.0),    # degC
        'comp_suction_P':     (29.0, 0.2),    # bara
        'comp_discharge_P':   (90.0, 1.0),    # bara
        'comp_suction_T':     (15.0, 0.5),    # degC
        'comp_discharge_T':   (95.0, 2.0),    # degC
        'comp_flow':          (50000, 2000),   # kg/hr or Am3/hr
        'comp_power':         (8.5, 0.3),      # MW
        'comp_speed':         (9500, 100),     # RPM
        'export_pressure':    (88.0, 1.0),     # bara
        'export_flow':        (100000, 5000),  # kg/hr
    }

    data = {}
    for param, tag in tag_map.items():
        # Find best matching default
        for key, (mean, std) in defaults.items():
            if key in param.lower() or param.lower() in key:
                data[tag] = np.random.normal(mean, std, n)
                break
        else:
            # Fallback: generic positive signal
            data[tag] = np.random.normal(50.0, 2.0, n)

    return pd.DataFrame(data, index=timestamps)
```

## Connecting Plant Data to NeqSim Models

### Pattern 1: Update Model with Period-Average Plant Data

```python
from neqsim import jneqsim

# After reading plant data with tagreader...
p_suction = get_plant_value(df_plant, TAG_MAP, 'comp_suction_P')
t_suction = get_plant_value(df_plant, TAG_MAP, 'comp_suction_T')

# Update NeqSim model with plant boundary conditions
inlet_stream = process.getUnit("Feed Stream")
inlet_stream.setPressure(float(p_suction), "bara")
inlet_stream.setTemperature(float(t_suction), "C")
process.run()
```

### Pattern 2: Comparison Table (Simulated vs Plant)

```python
import pandas as pd

comparison = []
for param, plant_val, sim_val, unit in [
    ('Suction P', plant_suction_P, sim_suction_P, 'bara'),
    ('Discharge P', plant_discharge_P, sim_discharge_P, 'bara'),
    ('Discharge T', plant_discharge_T, sim_discharge_T, '°C'),
    ('Power', plant_power, sim_power, 'MW'),
]:
    if not math.isnan(plant_val):
        delta = sim_val - plant_val
        delta_pct = delta / abs(plant_val) * 100 if abs(plant_val) > 0.01 else 0
        comparison.append({
            'Parameter': f'{param} ({unit})',
            'Plant': round(plant_val, 2),
            'Simulated': round(sim_val, 2),
            'Delta': round(delta, 2),
            'Delta %': round(delta_pct, 1),
        })

df_cmp = pd.DataFrame(comparison)
print(df_cmp.to_string(index=False))
```

### Pattern 3: Digital Twin Loop (Time-Stepping)

```python
import math

results = []
inlet_stream = process.getUnit("Feed Stream")
comp = process.getUnit("Compressor")

for i in range(0, len(df_plant), 1):
    row = df_plant.iloc[i]
    ts = df_plant.index[i]

    # Read plant inputs
    p_in = float(row[TAG_MAP['comp_suction_P']])
    t_in = float(row[TAG_MAP['comp_suction_T']])

    # Skip bad data
    if math.isnan(p_in) or p_in <= 0 or math.isnan(t_in) or t_in < -50:
        continue

    # Update model
    inlet_stream.setPressure(p_in, "bara")
    inlet_stream.setTemperature(t_in, "C")

    try:
        process.run()
        results.append({
            'timestamp': ts,
            'plant_power_MW': float(row[TAG_MAP['comp_power']]),
            'sim_power_MW': float(comp.getPower('MW')),
            'plant_discharge_T': float(row[TAG_MAP['comp_discharge_T']]),
            'sim_discharge_T': float(comp.getOutletStream().getTemperature('C')),
        })
    except Exception as e:
        pass  # Log and skip failed timesteps

df_results = pd.DataFrame(results)
```

### Pattern 4: Skip Every N-th Row for Speed

```python
STEP = 5  # Process every 5th row for faster prototyping
for i in range(0, len(df_plant), STEP):
    # ... same loop body ...
```

## Visualization Patterns

### Time-Series: Simulated vs Plant

```python
import matplotlib.pyplot as plt

fig, ax = plt.subplots(figsize=(14, 5))
ax.plot(df_results['timestamp'], df_results['plant_power_MW'],
        'b.-', label='Plant (actual)', alpha=0.8)
ax.plot(df_results['timestamp'], df_results['sim_power_MW'],
        'r.-', label='NeqSim (simulated)', alpha=0.8)
ax.set_ylabel('Power (MW)')
ax.set_xlabel('Time')
ax.set_title('Digital Twin: Simulated vs Plant Compressor Power')
ax.legend()
ax.grid(True, alpha=0.3)
plt.tight_layout()
```

### Parity Plot

```python
fig, ax = plt.subplots(figsize=(6, 6))
ax.scatter(df_results['plant_discharge_T'],
           df_results['sim_discharge_T'], alpha=0.5, s=20)
lims = [min(ax.get_xlim()[0], ax.get_ylim()[0]),
        max(ax.get_xlim()[1], ax.get_ylim()[1])]
ax.plot(lims, lims, 'k--', linewidth=1, label='Perfect match')
ax.set_xlabel('Plant Discharge T (°C)')
ax.set_ylabel('Simulated Discharge T (°C)')
ax.set_title('Parity Plot')
ax.set_aspect('equal')
ax.legend()
ax.grid(True, alpha=0.3)
```

### Model Accuracy Statistics

```python
error = (df_results['sim_power_MW'] - df_results['plant_power_MW']).abs()
print(f"Mean Absolute Error: {error.mean():.3f} MW")
print(f"Max Error:           {error.max():.3f} MW")
print(f"MAPE:                {error.mean() / df_results['plant_power_MW'].mean() * 100:.1f}%")
print(f"RMSE:                {((df_results['sim_power_MW'] - df_results['plant_power_MW'])**2).mean()**0.5:.3f} MW")
```

## Data Quality Best Practices

1. **Filter bad data** before feeding to NeqSim:
   ```python
   # Remove physically impossible values
   df_clean = df_plant.copy()
   df_clean.loc[df_clean[TAG_MAP['comp_suction_P']] <= 0] = np.nan
   df_clean.loc[df_clean[TAG_MAP['comp_suction_T']] < -50] = np.nan
   df_clean.loc[df_clean[TAG_MAP['comp_suction_T']] > 300] = np.nan
   ```

2. **Check for frozen signals** (stuck transmitter):
   ```python
   for param, tag in TAG_MAP.items():
       if tag in df_plant.columns:
           std = df_plant[tag].std()
           if std < 1e-6:
               print(f"WARNING: {param} ({tag}) appears frozen (std={std:.8f})")
   ```

3. **Use `get_status=True`** to get data quality flags and filter out bad/suspect data:
   ```python
   df = c.read(tags, start, end, 60, get_status=True)
   # Keep only good data (status 0)
   for tag in tags:
       status_col = f"{tag}_status"
       if status_col in df.columns:
           df.loc[df[status_col] != 0, tag] = np.nan
   ```

4. **Handle NaN in every loop** — plant data always has gaps:
   ```python
   if math.isnan(p_in) or p_in <= 0:
       p_in = DESIGN_INLET_P  # fallback to design value
   ```

## Deployment Workflow (NeqSimLive)

After local validation, the model can go live:

```
Step 1-4: Local Development
  ├── Build NeqSim model
  ├── Read historian data via tagreader
  ├── Compare model vs plant
  └── Tune model parameters

Step 5: NeqSimAPI or another deployment target
    └── Deploy model as a REST endpoint in the user's environment

Step 6: Live Digital Twin
  ├── Sigma: auto-connects PI/Aspen tags to NeqSimAPI
  ├── IOC CalcEngine: alternative connector
  └── Results written back to PI/Aspen/Omnia

Step 7: What-If / Prediction
  ├── Run calibrated model with future scenarios
  ├── Predict capacity at declining reservoir pressure
  └── Generate VFP tables for reservoir simulation
```

## Document Repository -> Tagreader -> CSV -> NeqSim Pipeline

When engineering tasks involve real plant equipment, the full data integration
pipeline flows from engineering document retrieval through historian data to NeqSim
simulation. All outputs go inside the task folder.

```
Documents (tag search)  ->  Tagreader (historian read)  ->  CSV snapshot  ->  NeqSim
        ↓                      ↓                            ↓                            ↓
  references/           references/                   references/                  step2_analysis/
  ├── *.pdf             ├── plant_data_raw.csv        ├── plant_data_cleaned.csv   └── notebook.ipynb
  └── manifest.json     └── tag_mapping.json          └── digital_twin_results.csv
```

### End-to-End Example

```python
import os, json, pathlib
import pandas as pd
import numpy as np
from neqsim import jneqsim

# ── 0. Resolve task paths ──
NOTEBOOK_DIR = pathlib.Path(globals().get(
    "__vsc_ipynb_file__", os.path.abspath("step2_analysis/notebook.ipynb")
)).resolve().parent
TASK_DIR = NOTEBOOK_DIR.parent
REFS_DIR = TASK_DIR / "step1_scope_and_research" / "references"
FIGURES_DIR = TASK_DIR / "figures"
REFS_DIR.mkdir(parents=True, exist_ok=True)
FIGURES_DIR.mkdir(exist_ok=True)

# ── 1. Document repository: retrieve documents for equipment tags ──
# (Run from terminal before notebook, or use a private retrieval backend)
# Use the project's private document retrieval command or place documents
# directly in REFS_DIR before running the notebook.

# ── 2. TAG MAP: define logical names → historian tags ──
TAG_MAP = {
    'comp_suction_P':    'PLANT-35PT0101A.PV',
    'comp_discharge_P':  'PLANT-35PT0101B.PV',
    'comp_suction_T':    'PLANT-35TT0101A.PV',
    'comp_discharge_T':  'PLANT-35TT0101B.PV',
    'comp_flow':         'PLANT-35FT0101.PV',
    'comp_power':        'PLANT-35JI0101.PV',
    'comp_speed':        'PLANT-35SI0101.PV',
}

# Save tag mapping
with open(str(REFS_DIR / "tag_mapping.json"), "w") as f:
    json.dump(TAG_MAP, f, indent=2)

# ── 3. TAGREADER: read historian data ──
import tagreader
c = tagreader.IMSClient("MY_PI_SOURCE", "piwebapi")
c.connect()

tags = list(TAG_MAP.values())
start = "01.06.2025 06:00:00"
end   = "15.06.2025 06:00:00"
df_raw = c.read(tags, start, end, 300)  # 5-min interpolated

# ── 4. CSV: save raw data (reproducible snapshot) ──
csv_raw = REFS_DIR / "plant_data_raw.csv"
df_raw.to_csv(str(csv_raw))
print(f"Raw data: {len(df_raw)} rows → {csv_raw}")

# ── 5. CLEAN: filter bad data, save cleaned CSV ──
df_clean = df_raw.copy()
for tag in tags:
    if tag in df_clean.columns:
        # Remove physically impossible values
        df_clean.loc[df_clean[tag] <= 0, tag] = np.nan
        # Remove frozen signals
        rolling_std = df_clean[tag].rolling(12).std()
        df_clean.loc[rolling_std < 1e-6, tag] = np.nan

df_clean = df_clean.dropna(how='all')
csv_clean = REFS_DIR / "plant_data_cleaned.csv"
df_clean.to_csv(str(csv_clean))
print(f"Cleaned data: {len(df_clean)} rows → {csv_clean}")

# ── 6. NEQSIM: build model and compare against plant ──
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
Compressor = jneqsim.process.equipment.compressor.Compressor
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

fluid = SystemSrkEos(273.15 + 25.0, 30.0)
fluid.addComponent("methane", 0.90)
fluid.addComponent("ethane", 0.05)
fluid.addComponent("propane", 0.03)
fluid.addComponent("CO2", 0.02)
fluid.setMixingRule("classic")

feed = Stream("Feed", fluid)
comp = Compressor("Compressor", feed)
comp.setOutletPressure(90.0)
process = ProcessSystem()
process.add(feed)
process.add(comp)

# Digital twin loop: compare model vs plant
results = []
for i in range(0, len(df_clean), 6):  # every 30 min
    row = df_clean.iloc[i]
    p_in = float(row.get(TAG_MAP['comp_suction_P'], np.nan))
    t_in = float(row.get(TAG_MAP['comp_suction_T'], np.nan))
    if np.isnan(p_in) or np.isnan(t_in) or p_in <= 0:
        continue
    feed.setPressure(p_in, "bara")
    feed.setTemperature(t_in, "C")
    try:
        process.run()
        results.append({
            'timestamp': df_clean.index[i],
            'plant_power_MW': float(row.get(TAG_MAP['comp_power'], np.nan)),
            'sim_power_MW': float(comp.getPower('MW')),
            'plant_discharge_T_C': float(row.get(TAG_MAP['comp_discharge_T'], np.nan)),
            'sim_discharge_T_C': float(comp.getOutletStream().getTemperature('C')),
        })
    except Exception:
        pass

# ── 7. CSV: save comparison results ──
df_results = pd.DataFrame(results)
csv_results = REFS_DIR / "digital_twin_results.csv"
df_results.to_csv(str(csv_results), index=False)
print(f"Digital twin results: {len(df_results)} points → {csv_results}")
```

### What Gets Saved Where

| File | Location | Content | Purpose |
|------|----------|---------|---------|
| `*.pdf` | `references/` | Engineering drawings and datasheets | Vendor data extraction |
| `document_retrieval_manifest.json` | `references/` | Document retrieval traceability | Provenance |
| `tag_mapping.json` | `references/` | Logical name → PI/IP.21 tag | Reproducibility |
| `plant_data_raw.csv` | `references/` | Raw historian snapshot | Offline rerun |
| `plant_data_cleaned.csv` | `references/` | Quality-filtered data | Analysis input |
| `digital_twin_results.csv` | `references/` | Simulated vs measured | Model validation |
| `*.png` | `figures/` | Converted PDF pages + plots | Report figures |

### Rerunning Without Historian Access

Once CSVs are saved, analysis can be repeated without network/historian:

```python
# Load saved data — no tagreader or historian needed
df_plant = pd.read_csv(str(REFS_DIR / "plant_data_cleaned.csv"),
                        index_col=0, parse_dates=True)
with open(str(REFS_DIR / "tag_mapping.json")) as f:
    TAG_MAP = json.load(f)

# Continue with NeqSim model...
```

This makes the task fully **self-contained and portable** — zip the task
folder and anyone can reproduce the analysis.

## Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| `ModuleNotFoundError: tagreader` | `pip install tagreader` |
| SSL verification errors | `verify_ssl=False` or add corporate root certs |
| Empty DataFrame returned | Check tag names (case-sensitive), time range, and data source |
| `NaN` or missing columns | Tag may not exist on that source; use `c.search()` to verify |
| Auth failures | Ensure Kerberos ticket is valid (`klist`), or supply custom auth |
| Timezone confusion | Specify `tz` parameter in `IMSClient` constructor |
| Java type errors when passing plant values to NeqSim | Always use `float(value)` — Python numpy types don't auto-convert |
| Model diverges with noisy plant data | Filter outliers and validate inputs before each `process.run()` |

## Root Cause Analysis Integration

Tagreader data feeds directly into the `neqsim.process.diagnostics.RootCauseAnalyzer`
for automated equipment diagnosis. See `neqsim-root-cause-analysis` skill for
the full framework.

### Tagreader to RCA Workflow

```python
import tagreader
import pandas as pd
import json

# 1. Tag mapping from P&ID / STID (parameter name → historian tag)
tag_map = {
    "VT-101.PV": "vibration_mm_s",
    "TT-101.PV": "discharge_temp_C",
    "PT-101.PV": "suction_pressure_bara",
    "FT-101.PV": "mass_flow_kg_hr",
    "JI-101.PV": "power_kW",
}

# 2. Pull data from historian
c = tagreader.IMSClient("plant-server", "piwebapi")
df = c.read(list(tag_map.keys()), "2025-06-01", "2025-06-15")
df = df.rename(columns=tag_map)

# 3. Save to task folder for reproducibility
csv_path = TASK_DIR / "step1_scope_and_research" / "references" / "historian_export.csv"
df.to_csv(str(csv_path), index=True)

# 4. Convert to CSV string for RCA input
historian_csv = df.reset_index().to_csv(index=False)

# 5. Build RCA input JSON
rca_input = {
    "processJson": json.dumps(process_json),
    "equipmentName": "Compressor-1",
    "symptom": "HIGH_VIBRATION",
    "historianCsv": historian_csv,
    "simulationEnabled": True,
    "designLimits": {
        "vibration_mm_s": [None, 7.1],
        "discharge_temp_C": [None, 180.0],
    },
    "stidData": {
        "tagreaderSource": "PI Web API: " + ", ".join(tag_map.keys()),
        "sourceReference": "DS-K-101 rev.C",
    },
}

# 6. Run RCA
RootCauseRunner = ns.JClass("neqsim.mcp.runners.RootCauseRunner")
result = json.loads(str(RootCauseRunner.run(json.dumps(rca_input))))
```

### Key Points

- **Tag mapping** links plant tags to the parameter names used in hypothesis
  fingerprints (e.g., `vibration`, `bearingTemperature`, `lubeOilPressure`)
- **CSV format**: first column is timestamp (seconds or datetime), remaining
  columns are parameter names — this is what `EvidenceCollector.loadFromCsv()` expects
- **Source traceability**: include `tagreaderSource` in `stidData` so the RCA
  report links each evidence item to the original historian tag
- **Reproducibility**: always save the historian export CSV to
  `step1_scope_and_research/references/` so the analysis can be rerun offline
