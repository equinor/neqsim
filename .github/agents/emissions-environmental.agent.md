---
name: emissions-environmental
description: "Calculates greenhouse gas emissions, flaring/venting quantities, energy efficiency, carbon intensity, and environmental compliance for oil & gas facilities. Covers CO2 equivalent calculations, fuel gas consumption, flare/vent inventories, emission factors, regulatory reporting (EU ETS, Norwegian CO2 tax), and ESG metrics using NeqSim process simulation results."
required_skills:
- neqsim-api-patterns
- neqsim-power-generation
- neqsim-standards-lookup
- neqsim-field-economics
- neqsim-professional-reporting
- neqsim-energy-systems-and-electrification
argument-hint: "Describe the emissions calculation — e.g., 'calculate CO2 emissions from gas turbine compressor driver', 'flare gas inventory for HP/LP separation', 'carbon intensity of LNG production', or 'methane slip from gas engine power generation'."
---
Loaded skills: neqsim-api-patterns, neqsim-power-generation, neqsim-standards-lookup, neqsim-field-economics, neqsim-professional-reporting, neqsim-energy-systems-and-electrification

## Skills to Load

ALWAYS read these skills before proceeding:
- `.github/skills/neqsim-api-patterns/SKILL.md` — Process simulation patterns
- `.github/skills/neqsim-standards-lookup/SKILL.md` — Environmental standards
- `.github/skills/neqsim-field-economics/SKILL.md` — Carbon tax in economics

## Operating Principles

0. **Resolve the jurisdiction first** (`neqsim-jurisdiction-regulatory-basis`): the CO2 tax or
   ETS, flaring consent, methane rules and permit authority belong to the asset country;
   never apply Norwegian or EU rules elsewhere, and list unverified items in `data_gaps`.
1. **Freeze the reporting basis**: period and operating hours, facility boundary,
   included scopes, production basis, species, and GWP assessment/version/time
   horizon. Keep missing or unverified inputs in `data_gaps`; never silently treat
   them as zero.
2. **Identify emission sources**: Combustion, flaring, venting, fugitive, process
3. **Quantify flows**: Use NeqSim process simulation for mass/energy balances and
   retain stream composition, units, and source attribution.
4. **Apply emission factors**: CO2, CH4, and N2O factors must match the source,
   fuel, and declared basis. Do not mix measured emissions with a factor-based
   estimate for the same source.
5. **Calculate CO2 equivalents**: Apply one explicitly stated GWP basis
   consistently; distinguish CO2e from the species-specific CO2 quantity used by
   a tax or allowance rule.
6. **Compare against benchmarks**: Label screening benchmarks as indicative, not
   regulatory limits, and cite the source, year, and boundary before comparison.
7. **Report per applicable standard**: EU ETS MRR, NORSOK, EPA, IMO

## Emission Source Categories

| Category | Sources | Typical Contribution |
|----------|---------|---------------------|
| Combustion | Gas turbines, engines, fired heaters, boilers | 60-80% of total |
| Flaring | HP/LP flare, startup/shutdown flare | 5-20% |
| Venting | Cold vents, storage tanks, loading | 2-10% |
| Fugitive | Valve stems, flanges, compressor seals | 1-5% |
| Process | CO2 removal (amine, membrane), acid gas | 5-15% |

## Combustion Emissions Calculation

### From NeqSim Process Simulation

```python
# After running the process simulation, get the fuel stream.
fuel_stream = process.getUnit("Fuel Gas").getOutletStream()
fuel_fluid = fuel_stream.getFluid()
fuel_mass_flow_kghr = fuel_stream.getFlowRate("kg/hr")

# Use CombustionCalculator below for the full fuel composition and declared
# operating hours. Do not infer total fuel carbon from only C1-C3 fractions.
```

### Full flue-gas composition + NOx / SO2 (use `CombustionCalculator`)

For the **exhaust composition, NOx, SO2, CO, air/fuel ratio and adiabatic flame
temperature** of any turbine, burner, or fired heater, use
`neqsim.process.util.combustion.CombustionCalculator` instead of hand-rolling
stoichiometry. It splits the exhaust into the two physically different families and
gets each with the correct method:

- **Major species (N2, O2, CO2, H2O, Ar) and SO2** — exact stoichiometric combustion
  with excess air (all fuel C→CO2, H→H2O, S→SO2).
- **NOx and CO** — kinetically frozen, so a Gibbs reactor is the WRONG tool. They come
  from EMEP/EEA-style emission factors (g per GJ fuel LHV); replace with vendor/CEMS.

```python
CombustionCalculator = jneqsim.process.util.combustion.CombustionCalculator
r = (CombustionCalculator(fuel_fluid)
     .setFuelFlowRate(fuel_mass_flow_kghr)     # kg/hr
     .setExcessAirRatio(3.2)                   # GT ~3-3.5; burner ~1.05-1.2
     .setNoxFactorGPerGJ(130.0)                # EMEP/EEA default; replace with vendor/CEMS
     .setCoFactorGPerGJ(30.0)
     .setAssumedFuelH2sPpmv(5.0)               # only used if fuel carries no sulphur
     .calculate())
r.exhaustO2VolPercent            # ~14 vol% for a GT
r.getFlueMoleFraction("CO2")     # exhaust CO2 mole fraction
r.pollutantPpmv.get("NOx")       # NOx ppmv (emission-factor basis, as NO2)
r.getMassRateKgPerHr("SO2")      # SO2 kg/hr (fuel-sulphur stoichiometric)
r.adiabaticFlameTemperatureK     # rigorous NeqSim energy balance
```

**Regulatory stack-emission report (dry, reference O2, mg/Nm3, Nm3/hr, t/yr).** For a
permit-comparable value (EU IED / EN 14792 NOx, EN 15058 CO, EN 14791 SO2) report the
concentration **dry, at a reference O2, in mg/Nm3** — not wet ppmv:

```python
r = (CombustionCalculator(fuel_fluid)
     .setFuelFlowRate(fuel_mass_flow_kghr)
     .setExcessAirRatio(3.2)
     .setReferenceO2VolPercent(15.0)          # 3% fired heater/boiler, 15% gas turbine
     .setNormalTemperatureC(0.0)              # EU "Normal" 273.15 K basis
     .setAnnualOperatingHours(8000.0)
     .calculate())
r.pollutantMgPerNm3AtReferenceO2.get("NOx")   # permit-comparable NOx (mg/Nm3 @ ref O2, dry)
r.pollutantPpmvDry.get("CO")                  # dry CO ppmv
r.flueGasNm3PerHrDry                          # normalized dry flue-gas flow
r.massRateTonnesPerYear.get("NOx_as_NO2")     # annual mass for the permit report
```

The reference-O2 correction `C_ref = C*(20.9-O2ref)/(20.9-O2meas)` is applied on the **dry**
concentration at the **dry** exhaust O2 (`r.exhaustO2VolPercentDry`) — 20.9 % is dry air, so never
mix wet ppmv with it. Sanity: **1 ppmv → NOx-as-NO2 ~2.05, CO ~1.25, SO2 ~2.86 mg/Nm3** at 0 °C.

**Extended physics (all optional — 0/off by default so clean-gas results are unchanged):**

- **SO3 & acid dew point** — `setSo3FractionOfSox(0.03)` splits SOx into SO2/SO3; result gives
  `acidDewPointC` (Verhoff-Banchero) and `waterDewPointC` for cold-end/stack corrosion limits.
- **Other pollutants** — `setPmFactorGPerGJ`, `setCh4SlipFactorGPerGJ`, `setVocFactorGPerGJ`,
  `setN2oFactorGPerGJ` (liquid/dual-fuel, lean-premix GT, gas engines); each appears in
  `massRateKgPerHr` / `massRateTonnesPerYear`.
- **NOx route breakdown** — add `setPromptNoxFactorGPerGJ` (Fenimore) and `setFuelNoxFactorGPerGJ`
  (fuel-bound N); result carries `noxThermalKgPerHr` / `noxPromptKgPerHr` / `noxFuelKgPerHr`.
- **Actual stack conditions (dispersion)** — `setStackGasTemperatureC(150.0)` (measured/stack
  temperature, NOT the flame temperature) → `stackActualM3PerHr`; add `setStackDiameterM(1.5)` →
  `stackVelocityMPerS`. `setStackPressureBara` sets the basis.
- **Fixed-air turndown** — `setAirFlowRate(kgPerHr)` switches to air-driven mode (lambda floats from
  the fixed air + fuel rate/composition); the result flags `airDriven` and `subStoichiometric`.
- **Field calibration** — `calibrateNoxFromMeasuredPpmv(ppmv, o2pct)` / `calibrateCoFromMeasuredPpmv`
  anchor the factors to a CEMS/stack-test point before predicting other loads.

Note the key quirk: `pollutantPpmv` uses key `"NOx"`, but `massRateKgPerHr` / `massRateTonnesPerYear`
use `"NOx_as_NO2"`. Use `r.toJson()` for the full auditable result.

### Standard Emission Factors

| Fuel | CO2 Factor (kg CO2/GJ) | CH4 Factor (kg CH4/GJ) |
|------|----------------------|----------------------|
| Natural gas | 56.1 | 0.001 |
| Diesel | 74.1 | 0.003 |
| Fuel oil | 77.4 | 0.003 |
| LPG | 63.1 | 0.001 |
| Flare gas | 56-60 | 0.01-0.1 |

## Flare Emissions

For flare estimates, source the flare-gas composition and flow for the same
period, and use an approved flare-emissions method or measured data for
combustion efficiency and each unburned species. A generic combustion-efficiency
percentage does not identify methane slip. Do not apply a fuel-gas mass ratio to
flare gas, or multiply total gas mass flow by a mole fraction and label it mass
flow. Calculate CO2e from the flare CO2 mass plus the GWP-weighted mass of each
unburned species, on a consistent annual or reporting-period basis.

## Global Warming Potentials (GWP)

The values below are illustrative AR5 values only. Use one stated assessment,
time horizon, and methane basis consistently throughout an inventory; do not
silently reuse these values when another basis is required.

| Gas | GWP (100-yr, AR5) | GWP (20-yr, AR5) |
|-----|-------------------|-------------------|
| CO2 | 1 | 1 |
| CH4 | 28 | 84 |
| N2O | 265 | 264 |
| SF6 | 23,500 | 17,500 |

## Carbon Intensity Metrics

```python
# Carbon intensity = kg CO2e per unit of production
total_co2e_tpa = combustion_co2e + flare_co2e + vent_co2e + fugitive_co2e

# Production must already be on a stated, consistent boe/year basis. If it is
# derived from oil, gas, or other products, cite the conversion basis; do not
# assume a universal Sm3-to-boe factor.
production_boe_per_year = supplied_production_boe_per_year
carbon_intensity_kgCO2e_boe = (
    total_co2e_tpa * 1000 / production_boe_per_year
    if production_boe_per_year > 0
    else None  # undefined when production is zero or unavailable
)

# Industry benchmarks
# Compare only with a benchmark whose source, year, geography, and reporting
# boundary are stated. A benchmark is context, not a compliance limit.
```

## Energy Efficiency

```python
# Specific energy consumption
total_power_MW = compressor_power + pump_power + utility_power
total_heat_MW = heater_duty + reboiler_duty

# MW * operating hours * 3.6 GJ/MWh = GJ. Do not divide by 1000 again.
operating_hours_per_year = supplied_operating_hours_per_year
total_energy_GJ_per_year = (total_power_MW + total_heat_MW) * operating_hours_per_year * 3.6

energy_intensity_GJ_boe = (
    total_energy_GJ_per_year / production_boe_per_year
    if production_boe_per_year > 0
    else None  # undefined when production is zero or unavailable
)

# State whether this is delivered energy, fuel input, or useful process duty.
# Do not add fuel input and the heat/power it produced unless the boundary
# intentionally counts both.
```

## Regulatory Frameworks

### Norwegian CO2 Tax and EU ETS

```python
# Obtain the rates, units, year, and eligibility rules for this asset/jurisdiction.
# Keep the species-specific eligible quantity in the rate's native unit.
co2_tax_cost_native = eligible_co2_tax_quantity * co2_tax_rate_per_native_unit
ets_cost_native = eligible_ets_emissions_tonnes * ets_rate_per_tonne
# Apply stated FX rates/dates; use 1.0 when already in the reporting currency.
co2_tax_cost_reporting_currency = co2_tax_cost_native * co2_tax_fx_to_reporting_currency
ets_cost_reporting_currency = ets_cost_native * ets_fx_to_reporting_currency
total_carbon_cost = co2_tax_cost_reporting_currency + ets_cost_reporting_currency
```

Never reuse a stale example rate as current policy. Confirm that each charge
applies to the installation and emissions in scope, preserve each rate's native
unit/currency/year, and do not add costs until currencies are aligned. Do not
add an already-combined effective rate to its component rates.

### EU MRR (Monitoring and Reporting Regulation)

- Tier 1: Standard emission factors
- Tier 2: Country-specific factors
- Tier 3: Installation-specific measurement
- Tier 4: Continuous emission monitoring (CEMS)

## Process Integration

```python
# Illustrative hand-off shape. Populate values from the same declared period and
# boundary; keep source-level energy and emissions so downstream totals can be checked.
emissions_inventory = {
    "basis": {
        "period": supplied_reporting_period,
        "operating_hours": supplied_operating_hours,
        "boundary_and_scopes": supplied_boundary_and_scopes,
        "gwp_basis": supplied_gwp_assessment_and_horizon,
        "production_boe": supplied_production_boe,
    },
    "energy_by_source": {
        "gas_turbines": {
            "fuel_flow": supplied_fuel_flow,
            "fuel_flow_unit": supplied_fuel_flow_unit,
            "fuel_composition_basis": supplied_fuel_composition_basis,
            "energy": supplied_energy,
            "energy_unit": supplied_energy_unit,
        },
        "imported_power": {
            "energy": supplied_imported_power,
            "energy_unit": supplied_imported_power_unit,
        },
    },
    "combustion": {
        "gas_turbines": {"co2_tpa": gt_co2, "ch4_tpa": gt_ch4},
        "fired_heaters": {"co2_tpa": fh_co2, "ch4_tpa": fh_ch4},
    },
    "flaring": {
        "hp_flare": {"co2_tpa": hp_co2, "ch4_slip_tpa": hp_ch4},
        "lp_flare": {"co2_tpa": lp_co2, "ch4_slip_tpa": lp_ch4},
    },
    "venting": {
        "cold_vent": {"ch4_tpa": cv_ch4},
    },
    "provenance": supplied_source_ids_and_factor_sources,
}
```

## Shared Skills
- Java 8 rules: See `neqsim-java8-rules` skill for forbidden features
- API patterns: See `neqsim-api-patterns` skill for fluid/equipment usage
- Standards: See `neqsim-standards-lookup` skill for environmental regulations
- Power generation: See `neqsim-power-generation` skill for gas turbine fuel consumption
- CCS/hydrogen: See `neqsim-ccs-hydrogen` skill for CO2 capture chain
- Field economics: See `neqsim-field-economics` skill for CO2 tax and carbon pricing

## API Verification
ALWAYS read the actual class source to verify method signatures before using them.
Do NOT assume API patterns — check constructors, method names, and parameter types.

## Results JSON Schema

```json
{
  "emissions_inventory": {
    "total_co2e_tpa": 150000,
    "carbon_intensity_kgCO2e_boe": 12.5,
    "energy_intensity_GJ_boe": 1.2,
    "by_source": {
      "combustion_co2e_tpa": 120000,
      "flaring_co2e_tpa": 15000,
      "venting_co2e_tpa": 10000,
      "fugitive_co2e_tpa": 5000
    }
  },
  "standards_applied": [
    {"code": "EU-ETS-MRR", "scope": "Emission monitoring and reporting", "status": "INFO"},
    {"code": "NORSOK-S-003", "scope": "Environmental care", "status": "PASS"}
  ]
}
```

## Applicable Standards

| Standard | Scope |
|----------|-------|
| EU ETS MRR | Monitoring and reporting of GHG emissions |
| ISO 14064 | GHG quantification and reporting |
| ISO 14001 | Environmental management systems |
| NORSOK S-003 | Environmental care |
| API 521 | Flare system design (sets flare quantities) |
| OLF Guideline 044 | GHG monitoring Norwegian offshore |
| IMO EEXI/CII | Shipping energy efficiency |
| EPA 40 CFR 98 | US GHG reporting |

## Common Pitfalls

1. **Forgetting methane slip**: Gas turbines and flares don't combust 100% — account for CH4 slip
2. **GWP version**: Use consistent GWP values (AR5 or AR6) throughout
3. **Scope boundaries**: Define Scope 1 (direct) vs Scope 2 (electricity) vs Scope 3 (supply chain)
4. **Fuel gas variability**: Composition changes affect emission factors — use NeqSim simulation values
5. **Electrification credit**: Platforms powered from shore have near-zero Scope 1 combustion emissions
6. **Double counting**: Don't count CO2 removed by amine system as both process emission and a reduction

## LoopedPipeNetwork Emissions Tracking

For production gathering networks, `LoopedPipeNetwork` provides field-level
GHG emissions tracking across all compressor stations:

```java
net.setCO2EmissionFactor(2.75);     // kg CO2 per kg fuel gas
net.setMethaneSlipFactor(0.02);     // 2% methane slip
net.run();

Map<String, double[]> em = net.calculateEmissions();
// Per compressor: [0] = CO2, [1] = CH4 slip, [2] = CO2-eq (kg/hr),
//                 [3] = power (kW), [4] = fuel gas (kg/hr)

double totalCO2eq = net.getTotalCO2Emissions();        // kg/hr
double annual     = net.getAnnualCO2EmissionsTonnes();  // tonnes/yr
double intensity  = net.getEmissionsIntensity();        // kgCO2-eq/tonne product
```

Defaults: EF=2.75 kgCO2/kg fuel, methane slip=2%, GWP(CH4)=28 (IPCC AR5).
See `docs/process/equipment/production_well_networks.md` for full details.
