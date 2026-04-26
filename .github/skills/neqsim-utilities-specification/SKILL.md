---
name: neqsim-utilities-specification
version: "1.0.0"
description: "Utility system specification — steam levels (HP/MP/LP), cooling water (CW supply/return ΔT), instrument air dryness, fuel gas composition, nitrogen purity, demineralized water, refrigeration. USE WHEN: a task requires utility duty consolidation, utility-system sizing, ESG/energy reporting, or selecting between utility levels for a process service. Pairs with neqsim-heat-integration (grand composite curve drives utility level selection) and neqsim-power-generation (HRSG/steam network)."
last_verified: "2026-04-26"
---

# NeqSim Utilities Specification Skill

Specify and size the seven core utilities every process plant needs:
**steam, cooling water, instrument air, fuel gas, nitrogen, demin water, refrigeration**.
Choices are usually decided by the grand-composite curve from [`neqsim-heat-integration`](../neqsim-heat-integration/SKILL.md)
and constrained by site standards (NORSOK U-001, IOGP S-714).

## When to Use

- Consolidating heating/cooling duties into utility headers
- Choosing **between** utility levels (LP vs MP steam, CW vs chilled water)
- Sizing utility headers and consumers
- Drafting utility design basis for FEED
- ESG / energy intensity reporting (kg CO₂ per t product)

Standards: **NORSOK U-001** (utility systems), **NORSOK P-002** (utility design),
**IOGP S-714**, **ASME PTC 12** (steam quality), **ISA-7.0.01** (instrument air).

## Utility 1 — Steam (HP / MP / LP)

Typical levels — pick from the grand composite curve:

| Level | Pressure  | Saturation T | Service                                                    |
| ----- | --------- | ------------ | ---------------------------------------------------------- |
| HP    | 40–100 barg | 250–310 °C  | Driving steam turbines, high-T reboilers                   |
| MP    | 10–25 barg  | 180–225 °C  | Reboilers, glycol regen, stripper                          |
| LP    | 3–6 barg    | 130–160 °C  | Tracing, deaerator, low-T reboilers                        |
| Atmospheric | 0 barg | 100 °C      | Heating coils, tank heating                                |

**Selection rule:** pick the **lowest** level that beats the cold stream by
ΔTmin + 10 °C margin. Lower steam = cheaper and lets HP go to power generation.

**Sizing inputs:**
- Total reboiler / heater duty per level
- Letdown allowance (10–20% spare)
- Network losses (3–5% per km of header)

**Boiler / HRSG feed water (BFW):**
- Conductivity < 0.3 µS/cm (per ASME PTC 12)
- O2 < 7 ppb (deaerator + scavenger like hydrazine or carbohydrazide)
- Hardness < 0.01 ppm (softener / RO)

## Utility 2 — Cooling Water

```
Q_cool = ṁ_CW × cp × (T_return − T_supply)
```

| Parameter         | Tropical     | Temperate    | Arctic        |
| ----------------- | ------------ | ------------ | ------------- |
| T_supply (°C)     | 32           | 25           | 10            |
| T_return (°C)     | 42           | 35           | 25            |
| ΔT design         | 10           | 10           | 15            |
| Cycles of conc.   | 4–6          | 5–7          | 5–7           |
| Velocity (tube)   | 1.5–2.5 m/s  | 1.5–2.5 m/s  | 1.5–2.5 m/s   |
| Fouling factor    | 0.0002 m²K/W | 0.0001 m²K/W | 0.0001 m²K/W  |

**Pinch on CW:** cooler outlet T_process must be ≥ T_supply + ΔTmin (typically 8–10 °C);
otherwise switch to chilled water or air cooler.

**Air coolers** preferred when:
- Site lacks abundant water
- Service T > 60 °C (CW would scale)
- Environmental discharge restricted

## Utility 3 — Instrument Air (ISA-7.0.01)

| Specification        | Value                            |
| -------------------- | -------------------------------- |
| Pressure             | 7–9 barg                         |
| Dew point            | ≤ −40 °C (atmospheric) — keep dry |
| Particulates         | ≤ 40 µm                          |
| Oil content          | ≤ 1 mg/m³                        |
| Header sizing        | Σ users × 1.5 + 25% spare        |
| Receiver volume      | 5 min @ peak demand              |
| Backup               | N₂ tie-in for emergency          |

## Utility 4 — Fuel Gas

For gas turbines, fired heaters, flare pilots:

| Spec                             | Typical                       |
| -------------------------------- | ----------------------------- |
| LHV (Wobbe Index variation)      | < ±5% of design               |
| Pressure (turbine)               | 25–35 barg                    |
| H2S content                      | < 4 ppmv (NORSOK), < 250 mg/Nm³ (general) |
| Liquid carryover                 | None — KO drum upstream       |
| C5+ dewpoint margin              | ≥ 20 °C above lowest pipeline T |
| Heating value                    | 30–50 MJ/Nm³ for natural gas  |

Use NeqSim to build the Wobbe Index calc:
```java
double LHV  = fluid.getCombustionEnergy("LHV/MJ/Sm3");
double SG   = fluid.getMolarMass() / 28.96;
double WI   = LHV / Math.sqrt(SG);          // Wobbe index
```

## Utility 5 — Nitrogen

| Service                | Purity       | Pressure       | Notes                     |
| ---------------------- | ------------ | -------------- | ------------------------- |
| Purging / inerting     | 99.5%        | 7–10 barg      | Bulk N₂ ok                |
| Blanketing             | 99.9%        | 0.05 barg      | Tank low-P                |
| Catalyst regen / sour service | 99.99% | 7 barg     | Cryogenic / PSA           |
| Instrument backup      | 99.5%        | 7 barg         | Tie-in to IA header       |

Volume rule for vessel inerting: **5× geometric volume** at atmospheric to reach ≤ 1% O₂.

## Utility 6 — Demineralized Water

| Service                  | Conductivity   | Notes                |
| ------------------------ | -------------- | -------------------- |
| BFW / HRSG               | < 0.3 µS/cm    | Mixed-bed polished   |
| Chemical injection make-up | < 1 µS/cm    | Two-bed sufficient   |
| Wash water (GBS, sour)   | Drinking water | Cl < 250 ppm         |

## Utility 7 — Refrigeration (Chilled Water / Propane / Mixed Refrigerant)

When CW can't reach the temperature target:

| Service          | Temp range | Refrigerant                    |
| ---------------- | ---------- | ------------------------------ |
| Chilled water    | 5–15 °C    | NH₃ or absorption chiller      |
| Propane          | −40 to 0 °C | C3 cycle (LNG, NGL recovery)  |
| Cascade / MR     | < −40 °C   | C3-MR, DMR (LNG)               |
| LIN / LHE        | < −150 °C  | Liquid N₂ / He (specialty)     |

For LNG see also [`neqsim-platform-modeling`](../neqsim-platform-modeling/SKILL.md).

## Pattern 1 — Utility Duty Consolidation

```java
double Q_HP_steam = 0, Q_MP = 0, Q_LP = 0, Q_CW = 0, Q_air = 0;
for (ProcessEquipmentInterface eq : process.getUnitOperations()) {
    if (eq instanceof Heater) {
        double T = ((Heater) eq).getOutletStream().getTemperature("C");
        double Q = ((Heater) eq).getDuty();
        if      (T > 200) Q_HP_steam += Q;
        else if (T > 130) Q_MP += Q;
        else              Q_LP += Q;
    } else if (eq instanceof Cooler) {
        double T = ((Cooler) eq).getOutletStream().getTemperature("C");
        double Q = ((Cooler) eq).getDuty();
        if (T > 60) Q_air += Q;        // air-cooled
        else        Q_CW += Q;         // CW
    }
}
```

## Pattern 2 — Utility Cost Allocation

```
$/yr = duty × hours × $/kWh
```

Typical Norwegian indicative numbers (escalate / replace per project):

| Utility                | $/MWh     |
| ---------------------- | --------- |
| HP steam               | 25        |
| MP steam               | 22        |
| LP steam               | 18        |
| Cooling water          | 0.5       |
| Air cooling (fans)     | 1.5 (electricity only) |
| Refrigeration (propane) | 50–80    |
| Electricity            | 70–100    |

## Common Mistakes

| Mistake                                                  | Fix                                                                  |
| -------------------------------------------------------- | -------------------------------------------------------------------- |
| Single steam level for all heating                       | At least HP + LP — saves 10–25% energy via pinch                     |
| CW return T close to wet-bulb                            | Cooling tower can't approach wet-bulb closer than 3–5 °C             |
| Sizing IA at peak demand only                            | Add 25% spare + receiver for valve-stroke transients                 |
| Fuel gas spec without Wobbe                              | Burners care about Wobbe, not just LHV — vary composition tests it   |
| N₂ purge based on volume × 1                             | Need 4–5 displacements to reach 99% inerting (well-mixed assumption) |
| Demin water spec'd as drinking water                     | BFW conductivity < 0.3 µS/cm — drinking water is 100–500             |

## Validation Checklist

- [ ] Each consumer linked to a utility level / supply
- [ ] Steam levels chosen against grand composite curve
- [ ] CW ΔT fits site climate (10 °C temperate, 15 °C arctic)
- [ ] IA dryness ≤ −40 °C dew point in design basis
- [ ] Fuel-gas Wobbe variation within burner tolerance
- [ ] Utility load summary in `results.json` under `utilities` with duty + cost
- [ ] CO₂ intensity calculated for project ESG metric

## Related Skills

- [`neqsim-heat-integration`](../neqsim-heat-integration/SKILL.md) — pinch dictates utility levels
- [`neqsim-power-generation`](../neqsim-power-generation/SKILL.md) — HRSG / steam network
- [`neqsim-emissions-environmental`](../neqsim-ccs-hydrogen/SKILL.md) — CO₂ from utility fuel
