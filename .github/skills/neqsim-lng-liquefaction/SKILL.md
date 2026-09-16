---
name: neqsim-lng-liquefaction
description: LNG liquefaction modelling in NeqSim. USE WHEN: building or sizing a liquefaction plant (SMR, DMR, C3MR, cascade, nitrogen expander), calibrating a mixed refrigerant, computing liquefaction specific energy or power, scaling to trains, choosing gas-turbine against electric drive and the resulting carbon intensity, or sizing LNG storage and cargo loading. Covers LNGProcessBuilder, LNGProcessModel, the mandatory specific-energy benchmark gate, and the LNG bubble-point trap.
---

# LNG liquefaction with NeqSim

## When to use this

Any task that turns a treated gas stream into LNG and needs a defensible power, specific energy,
train count, storage volume or carbon intensity. Pair with `neqsim-process-modeling` for the
upstream treatment train and `neqsim-equipment-cost-estimation` for the cost side.

## The one thing that will bite you

**A shipped mixed-refrigerant inventory is not a design.** Liquefaction specific energy is a
strong function of refrigerant composition and circulation ratio — those *are* the cycle design.
A builder with a fixed default inventory will return whatever that inventory gives, which can be
two to three times outside the published band for the cycle, with no warning.

**Therefore: calibrating the refrigerant and passing the specific-energy benchmark is a mandatory
gate, not a refinement.** Never scale an uncalibrated train to plant size.

Reference bands (specific energy on the feed-to-LNG basis):

| Cycle | kWh/kg LNG | Notes |
|---|---|---|
| Single mixed refrigerant (SMR) | 0.19 – 0.43 | Typical reference ~0.26 |
| Dual mixed refrigerant (DMR) | 0.25 – 0.35 | |
| Propane-precooled MR (C3MR) | 0.27 – 0.35 | Baseload workhorse |
| Cascade | 0.30 – 0.38 | |
| Nitrogen expander | 0.45 – 0.70 | Small scale / FLNG, simplicity over efficiency |

Source: Pereira et al., *Energy Conversion and Management* 272 (2022) 116364, and the open
liquefaction literature. Always cite the band you validate against.

## Core API

```java
LNGProcessBuilder builder = new LNGProcessBuilder("SMR train");
builder.setFeedStream(treatedGas);
builder.setLngTemperature(-160.0);      // degC
builder.setLngPressure(1.08);           // bara

// Refrigerant is configurable — set it, do not accept the default
builder.setRefrigerantComposition(
    new String[] {"nitrogen", "methane", "ethane", "propane", "i-butane"},
    new double[] {0.08, 0.34, 0.34, 0.12, 0.12});
builder.setRefrigerantCirculationRatio(2.5);   // kmol refrigerant per kmol feed
builder.setRefrigerantSuctionPressure(4.0);    // bara
builder.setRefrigerantDischargePressure(45.0); // bara
builder.setRefrigerantCoolingTemperature(30.0);// degC after the aftercooler

ProcessSystem train = builder.buildSMR();
train.run();
```

Other cycles: `buildDMR()`, `buildC3MR()`, `buildCascade()`, `buildNitrogenExpander()`.

## The calibration workflow (do this every time)

```python
def specific_energy(comp, fractions, ratio, suction, discharge):
    b = LNGProcessBuilder("cal")
    b.setFeedStream(feed)
    b.setRefrigerantComposition(comp, fractions)
    b.setRefrigerantCirculationRatio(ratio)
    b.setRefrigerantSuctionPressure(suction)
    b.setRefrigerantDischargePressure(discharge)
    p = b.buildSMR()
    p.run()
    power_kW = sum(u.getPower("kW") for u in compressors_in(p))
    lng_kg_per_h = lng_stream.getFlowRate("kg/hr")
    return power_kW / lng_kg_per_h

# 1. Sweep circulation ratio and discharge pressure
# 2. Accept the first combination inside the published band
# 3. ASSERT it is inside the band before going further
assert 0.19 <= se <= 0.43, "SMR train outside the published specific-energy band"
```

Circulation ratio is the strongest single lever; discharge pressure is the second. Suction
pressure mainly sets the cold-end approach.

## Scaling to a plant

Scale on **specific energy**, not by multiplying a single train's absolute power, so the train
count and the plant rate stay consistent:

```python
train_mtpa   = lng_kg_per_h * 8766 * availability / 1e9
n_trains     = ceil(plant_mtpa / train_mtpa)
plant_power  = specific_energy * plant_mtpa * 1e9 / (8766 * availability)  # kW
```

Then add, as separate items:
- **End-flash and BOG compression**: typically 5–8 % of refrigeration power.
- **Utilities** (cooling pumps, air, N2, instrument): typically 8–10 %.

A common sanity number: total FLNG/plant power lands near **0.35–0.40 kWh/kg** once end-flash and
utilities are included, against 0.26–0.32 for the refrigeration cycle alone.

## The LNG bubble-point trap

Flashing an LNG composition at its storage temperature and pressure with `TPflash` frequently
returns **vapour**, giving a density near 2 kg/m³ instead of 430–460 kg/m³ — and the error then
propagates silently into cargo size, storage volume and cargo count.

```python
# WRONG — may land on the vapour side
fluid.setTemperature(-160.0, "C"); fluid.setPressure(1.08, "bara")
ops.TPflash(); rho = fluid.getDensity("kg/m3")     # 1.9 kg/m3 !

# RIGHT — put the fluid on the saturation line first
fluid.setPressure(1.08, "bara")
ops.bubblePointTemperatureFlash()
fluid.initProperties()
rho = fluid.getPhase("oil").getDensity("kg/m3")    # ~440 kg/m3
```

Always cross-check LNG density against a reference equation (CoolProp methane saturated liquid is
close enough for a screening study; expect SRK to sit within ~4 %).

## Driver selection and carbon intensity

This is usually the largest ESG lever in an LNG study and is cheap to compute:

```python
# Gas-turbine drive
fuel_kg_per_h = power_kW / (gt_efficiency * LHV_kWh_per_kg)
co2_kg_per_h  = fuel_kg_per_h * 2.72          # kg CO2 per kg of natural gas
intensity     = co2_kg_per_h / lng_kg_per_h * 1000.0   # kg CO2 per tonne LNG

# Electric drive
co2_kg_per_h  = power_kW * grid_intensity_kg_per_kWh
```

Typical outcomes: gas-turbine drive ~180–220 kg CO2 per tonne of LNG; electric drive from a
low-carbon grid ~5–15 kg CO2/t. Electric drive also **releases the fuel gas back into production**
— quantify that, because it extends plateau life and is often worth more than the emission saving.

## Storage and marine sizing

```python
cargo_m3        = carrier_size_m3 * 0.985          # allow for heel and trim
cargo_t         = cargo_m3 * lng_density / 1000.0
cargoes_per_y   = plant_mtpa * 1e6 / cargo_t
days_between    = 365.25 / cargoes_per_y
berth_occupancy = cargoes_per_y * turnaround_hours / (365.25 * 24)
loading_rate    = cargo_m3 / loading_hours          # m3/h -> number of arms
```

Rules of thumb: one berth serves up to ~50 % occupancy comfortably; storage of 1.5–2 cargo
volumes (roughly 5 days of production) is the usual minimum; 16-inch arms carry ~3000–4000 m³/h
each.

## Validation gate for any LNG deliverable

1. Specific energy inside the published band for the chosen cycle — **assert it**.
2. LNG density cross-checked against a reference equation.
3. Feed-to-LNG mass balance closes (account for NGL extraction, fuel and end-flash).
4. Feed rate cross-checked against a published plant of similar capacity
   (~160–170 MMscfd per MTPA for a lean feed; higher for a rich feed with NGL extraction).

## Related skills

- `neqsim-process-modeling` — building the treatment train that feeds the liquefier
- `neqsim-water-dewpoint-dehydration-screening` — molecular-sieve specification upstream
- `neqsim-equipment-cost-estimation` — costing the trains (watch the correlation validity range)
- `neqsim-ccs-hydrogen` — if the CO2 removed upstream is captured rather than vented
- `neqsim-benchmark-reference-data` — the reference-data comparison layer for the validation gate
