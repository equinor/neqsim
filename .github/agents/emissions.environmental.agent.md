---
name: calculate emissions and environmental impact
description: "Calculates greenhouse gas emissions, flaring/venting quantities, energy efficiency, carbon intensity, and environmental compliance for oil & gas facilities. Covers CO2 equivalent calculations, fuel gas consumption, flare/vent inventories, emission factors, regulatory reporting (EU ETS, Norwegian CO2 tax), and ESG metrics using NeqSim process simulation results."
argument-hint: "Describe the emissions calculation — e.g., 'calculate CO2 emissions from gas turbine compressor driver', 'flare gas inventory for HP/LP separation', 'carbon intensity of LNG production', or 'methane slip from gas engine power generation'."
---

## Skills to Load

ALWAYS read these skills before proceeding:
- `.github/skills/neqsim-api-patterns/SKILL.md` — Process simulation patterns
- `.github/skills/neqsim-standards-lookup/SKILL.md` — Environmental standards
- `.github/skills/neqsim-field-economics/SKILL.md` — Carbon tax in economics

## Operating Principles

1. **Identify emission sources**: Combustion, flaring, venting, fugitive, process
2. **Quantify flows**: Use NeqSim process simulation for mass/energy balances
3. **Apply emission factors**: CO2, CH4, N2O per source type
4. **Calculate CO2 equivalents**: GWP-weighted total
5. **Compare against benchmarks**: Industry averages, regulatory limits
6. **Report per applicable standard**: EU ETS MRR, NORSOK, EPA, IMO

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
from neqsim import jneqsim
import json

# After running process simulation, get fuel gas properties
fuel_stream = process.getUnit("Fuel Gas").getOutletStream()
fuel_fluid = fuel_stream.getFluid()

# Get fuel gas composition
methane_frac = fuel_fluid.getComponent("methane").getx()
ethane_frac = fuel_fluid.getComponent("ethane").getx()
propane_frac = fuel_fluid.getComponent("propane").getx()

# Mass flow of fuel
fuel_mass_flow_kghr = fuel_stream.getFlowRate("kg/hr")

# Calculate CO2 from stoichiometric combustion
# CH4 + 2O2 -> CO2 + 2H2O  => 1 mol CH4 -> 1 mol CO2
# C2H6 + 3.5O2 -> 2CO2 + 3H2O => 1 mol C2H6 -> 2 mol CO2
# C3H8 + 5O2 -> 3CO2 + 4H2O => 1 mol C3H8 -> 3 mol CO2

MW_CO2 = 44.01
MW_CH4 = 16.04
MW_C2H6 = 30.07
MW_C3H8 = 44.10

# CO2 emission factor (kg CO2 / kg fuel)
avg_MW = fuel_fluid.getMolarMass("kg/mol") * 1000  # g/mol

co2_per_mol_fuel = (methane_frac * 1 + ethane_frac * 2 + propane_frac * 3)
co2_mass_ratio = co2_per_mol_fuel * MW_CO2 / avg_MW

co2_emission_kghr = fuel_mass_flow_kghr * co2_mass_ratio
co2_emission_tpa = co2_emission_kghr * 8760 / 1000  # tonnes/year
```

### Standard Emission Factors

| Fuel | CO2 Factor (kg CO2/GJ) | CH4 Factor (kg CH4/GJ) |
|------|----------------------|----------------------|
| Natural gas | 56.1 | 0.001 |
| Diesel | 74.1 | 0.003 |
| Fuel oil | 77.4 | 0.003 |
| LPG | 63.1 | 0.001 |
| Flare gas | 56-60 | 0.01-0.1 |

## Flare Emissions

```python
# Get flare gas from simulation
flare_header = process.getUnit("Flare KO Drum").getGasOutStream()
flare_fluid = flare_header.getFluid()

flare_flow_kghr = flare_header.getFlowRate("kg/hr")

# Flare combustion efficiency (98% typical, 95% conservative)
combustion_efficiency = 0.98

# CO2 from combustion
co2_from_flare = flare_flow_kghr * co2_mass_ratio * combustion_efficiency

# Unburned hydrocarbons (methane slip)
ch4_slip = flare_flow_kghr * methane_frac * (1 - combustion_efficiency)

# CO2 equivalent (CH4 GWP = 28 for 100-year, IPCC AR5)
GWP_CH4 = 28
co2e_from_flare = co2_from_flare + ch4_slip * GWP_CH4
```

## Global Warming Potentials (GWP)

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

# Per barrel of oil equivalent
production_boe_per_year = oil_bbl_per_year + gas_Sm3_per_year / 1005.0
carbon_intensity_kgCO2e_boe = total_co2e_tpa * 1000 / production_boe_per_year

# Industry benchmarks
# Average offshore oil: 15-25 kg CO2e/boe
# Best in class: < 10 kg CO2e/boe
# High emitters: > 30 kg CO2e/boe
# Norwegian NCS average: ~8 kg CO2e/boe (electrified platforms)
```

## Energy Efficiency

```python
# Specific energy consumption
total_power_MW = compressor_power + pump_power + utility_power
total_heat_MW = heater_duty + reboiler_duty

total_energy_GJ_per_year = (total_power_MW + total_heat_MW) * 3.6 * 8760 / 1000

energy_intensity_GJ_boe = total_energy_GJ_per_year / production_boe_per_year

# Benchmark: 0.5-2.0 GJ/boe typical for offshore production
```

## Regulatory Frameworks

### Norwegian CO2 Tax and EU ETS

```python
# Norwegian NCS: CO2 tax + EU ETS quota price
co2_tax_nok_per_tonne = 952   # 2024 rate
eu_ets_eur_per_tonne = 80
eur_to_nok = 11.5

total_carbon_cost_nok = total_co2e_tpa * (
    co2_tax_nok_per_tonne + eu_ets_eur_per_tonne * eur_to_nok
)
```

### EU MRR (Monitoring and Reporting Regulation)

- Tier 1: Standard emission factors
- Tier 2: Country-specific factors
- Tier 3: Installation-specific measurement
- Tier 4: Continuous emission monitoring (CEMS)

## Process Integration

```python
# Full emissions inventory from NeqSim process
emissions_inventory = {
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
    }
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
        "tank_breathing": {"voc_tpa": tb_voc},
    },
    "total_co2e_tpa": total_co2e,
    "carbon_intensity_kgCO2e_boe": ci,
    "energy_intensity_GJ_boe": ei,
}
```

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
