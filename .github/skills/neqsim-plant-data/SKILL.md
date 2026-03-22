---
name: neqsim-plant-data
description: "Connecting NeqSim process simulations to plant historian data via tagreader. USE WHEN: reading data from OSIsoft PI or Aspen IP.21 historians, building tag mappings for process equipment, comparing simulated vs measured values, running digital twin loops, or integrating NeqSim models with operational data. Covers tagreader API, tag mapping patterns, data quality handling, mock data generation, and model-vs-plant comparison workflows."
---

# Plant Data Integration with Tagreader

Patterns for connecting NeqSim process simulations to real plant data from
OSIsoft PI and Aspen IP.21 historians using the
[tagreader-python](https://github.com/equinor/tagreader-python) package.

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

For non-Equinor servers, supply `url`, `auth`, and optionally `verify_ssl`:

```python
from requests_ntlm import HttpNtlmAuth
auth = HttpNtlmAuth("domain\\user", "password")
sources = tagreader.list_sources("aspenone",
    url="https://api.mycompany.com/aspenone",
    auth=auth, verify_ssl=False)
```

### Creating and Connecting a Client

```python
# Equinor PI Web API
c = tagreader.IMSClient("PINO", "piwebapi")
c.connect()

# Equinor Aspen IP.21
c = tagreader.IMSClient("TRA", "aspenone")
c.connect()

# Non-Equinor with custom auth
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
| `url` | No | Server URL (needed for non-Equinor servers) |
| `verify_ssl` | No | SSL verification. Default: `True` |
| `auth` | No | Auth object. Default: Kerberos (Equinor) |
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
    'inlet_pressure':       'PLANT-20PT0118.PV',
    'inlet_temperature':    'PLANT-20TT0124.PV',

    # Compressor A
    'compA_suction_P':      'PLANT-35PT3601A.PV',
    'compA_discharge_P':    'PLANT-35PT3601B.PV',
    'compA_suction_T':      'PLANT-35TT3601A.PV',
    'compA_discharge_T':    'PLANT-35TT3601B.PV',
    'compA_flow':           'PLANT-35FT3601.PV',
    'compA_power':          'PLANT-35JI3191F000.PV',
    'compA_speed':          'PLANT-35SI3152.PV',

    # Export
    'export_pressure':      'PLANT-27PT0004.PV',
    'export_flow':          'PLANT-27FT0006.PV',
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

Common Equinor/NCS tag naming patterns:

| Prefix | Meaning | Example |
|--------|---------|---------|
| `PT` | Pressure Transmitter | `TRA-35PT3601A.PV` |
| `TT` | Temperature Transmitter | `TRA-35TT3601A.PV` |
| `FT` | Flow Transmitter | `TRA-35FT3601.PV` |
| `LT` | Level Transmitter | `TRA-20LT0118.PV` |
| `AT` | Analytical Transmitter | `TRA-20AT0130.PV` |
| `SI` | Speed Indicator | `TRA-35SI3152.PV` |
| `JI` | Power/Energy Indicator | `TRA-35JI3191F000.PV` |
| `.PV` | Process Value (current reading) | — |
| `.SP` | Setpoint | — |
| `.OP` | Output (controller) | — |

Tag structure: `SOURCE-SYSTEMNUMBER_TAGTYPE_SEQUENCE.ATTRIBUTE`

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

Step 5: NeqSimAPI (Cloud)
  └── Deploy model as REST endpoint
      https://neqsimapi.app.radix.equinor.com/docs

Step 6: Live Digital Twin
  ├── Sigma: auto-connects PI/Aspen tags to NeqSimAPI
  ├── IOC CalcEngine: alternative connector
  └── Results written back to PI/Aspen/Omnia

Step 7: What-If / Prediction
  ├── Run calibrated model with future scenarios
  ├── Predict capacity at declining reservoir pressure
  └── Generate VFP tables for reservoir simulation
```

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
