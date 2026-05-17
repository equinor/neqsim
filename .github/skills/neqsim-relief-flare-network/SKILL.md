---
name: neqsim-relief-flare-network
version: "1.0.0"
description: "Relief and flare system design — PSV sizing per API 520 (gas/liquid/two-phase, fire case), API 521 fire heat input, flare load summation, flare-tip sizing, radiation contour (API 521 §6), header back-pressure & Mach. USE WHEN: a task involves PSV sizing, relief contingency analysis, thermal relief for trapped liquid, flare network hydraulics, flare radiation/dispersion, or PSV→flare integration. Anchors on neqsim.process.util.fire.ReliefValveSizing, neqsim.process.equipment.flare.{Flare, FlareStack}, neqsim.process.equipment.valve.SafetyValve."
last_verified: "2026-04-26"
requires:
  java_packages: [neqsim.process.util.fire, neqsim.process.equipment.flare, neqsim.process.equipment.valve]
---

# NeqSim Relief & Flare Network Skill

End-to-end relief design — from individual PSV sizing through plant-wide load
summation, flare-tip selection, and radiation/dispersion checks per **API 520**,
**API 521**, **API 537**.

## When to Use

- Sizing a single PSV (gas / liquid / two-phase / fire case)
- Checking whether a blocked-in liquid segment needs thermal relief or source-term
    handoff after rupture screening
- API 521 fire heat input on wetted area
- Aggregating simultaneous relief loads to a flare header
- Flare tip diameter and stack height (radiation)
- Header hydraulics: back-pressure on PSVs, Mach < 0.7
- Sour-gas dispersion check (toxic clouds)

Standards: **API 520 Part I/II**, **API 521** (relief contingencies + radiation),
**API 537** (flare equipment), **NFPA 30**, **EN ISO 23251**.

## Pattern 1 — Gas-phase PSV (API 520)

```java
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.process.util.fire.ReliefValveSizing.PSVSizingResult;

PSVSizingResult psv = ReliefValveSizing.calculateRequiredArea(
    massFlowRate_kgs,
    setPressure_barg,
    backPressure_barg,
    T_K,
    MW,
    k_cpcv,            // ratio of specific heats
    Z_compressibility,
    overpressure_frac, // 0.10 process, 0.21 fire
    Kd                 // discharge coeff (0.975 typical)
);

double area_m2  = psv.getRequiredArea();
String orifice  = psv.getRecommendedOrifice();        // API 526 letter
String issues   = ReliefValveSizing.validateSizing(psv, /*fire*/ false);
```

## Pattern 2 — Fire Case (API 521)

```java
double Q_fire_W = ReliefValveSizing.calculateAPI521FireHeatInput(
    wettedAreaM2,
    /*hasDrainage*/ true,
    /*hasFireProofInsulation*/ false
);

// Mass flow = Q_fire / latent heat at relieving conditions
double mdot = Q_fire_W / latentHeat_J_per_kg;

PSVSizingResult psv = ReliefValveSizing.calculateRequiredArea(
    mdot, setP, backP, T_relief, MW, k, Z,
    /*overpressure_frac*/ 0.21,   // fire allows 21%
    Kd
);
```

API 521 fire equation: **Q = C × F × A_wet^0.82** with credit factors for drainage / insulation.

## Pattern 3 — Liquid PSV (API 520 Part I §5.8)

```java
import neqsim.process.util.fire.ReliefValveSizing.LiquidPSVSizingResult;

LiquidPSVSizingResult lpsv = ReliefValveSizing.calculateLiquidReliefArea(
    volumeFlowRate_m3s,
    setPressure_barg,
    backPressure_barg,
    rho_kg_m3,
    viscosity_cP,
    Kd
);
```

## Pattern 4 — Two-Phase (Omega Method)

```java
double area = ReliefValveSizing.calculateTwoPhaseReliefArea(
    massFlow_kgs, setP_barg, backP_barg, omega, rho_relief, Kd
);
```

## Pattern 5 — Flare Tip & Stack (API 537 + 521 §6)

```java
import neqsim.process.equipment.flare.Flare;

Flare flare = new Flare("MainFlare");
flare.setInletStream(reliefStream);
flare.setRadiantFraction(0.30);                // typical 0.20–0.40
flare.setTipDiameter(0.5);                     // m
flare.setDesignHeatDutyCapacity(150.0, "MW");
flare.run(UUID.randomUUID());

// Radiation at ground distance
double q_Wm2 = flare.estimateRadiationHeatFlux(75.0);   // 75 m
double dSafe = flare.radiationDistanceForFlux(4730.0);  // K = 1.5 kW/m² × 4 hr exposure
```

API 521 §6.4 radiation criteria:

| Receiver                           | Allowable flux (kW/m²)      |
| ---------------------------------- | --------------------------- |
| Personnel — emergency only         | 9.46                        |
| Personnel — escape (≤1 min)        | 6.31                        |
| Property line / 2-min escape       | 4.73                        |
| Solar background                   | ~1.0 (subtract from above)  |

## Pattern 6 — Plant Load Summation

For each contingency (general power failure, total reflux failure, fire zone):
1. List PSVs that lift simultaneously
2. Sum mass flows at each PSV at its **relieving** conditions
3. Pick the **governing** contingency (highest header load)
4. Size the flare for that load

```java
double totalReliefLoad = psvs.stream()
    .filter(p -> isActiveDuring(p, contingency))
    .mapToDouble(p -> p.getMassFlowCapacity())
    .sum();
```

## Pattern 7 — Header Back-Pressure & Mach

For balanced-bellows / pilot-operated PSVs, verify:
- Built-up back-pressure ≤ 50% set (conventional 10%)
- Header Mach < 0.7 at any location (avoid critical flow choking design)

```java
import neqsim.process.equipment.valve.SafetyValve;
SafetyValve sv = new SafetyValve("PSV-101", inletStream);
sv.setSetPressure(120.0, "barg");
sv.setBackPressure(15.0, "barg");
sv.run();
double KbCorrection = sv.getBackPressureCorrectionFactor();  // > 0.6 for balanced PSV
```

## Pattern 8 — Dispersion of Unignited Release

```java
FlareDispersionSurrogateDTO disp = flare.getDispersionSurrogate();
// Use to bound H2S / SO2 ground concentration vs. IDLH/ERPG-2
```

## Common Mistakes

| Mistake                                                   | Fix                                                                  |
| --------------------------------------------------------- | -------------------------------------------------------------------- |
| Sizing fire PSV at 10% overpressure                       | Fire case uses 21%; non-fire is 10% (API 520)                        |
| Wetted area = total surface                               | API 521 wetted area is liquid-touching surface up to 7.6 m elevation |
| Ignoring drainage credit                                  | F factor reduces Q_fire by 0.5 with adequate drainage (slope ≥ 1°)  |
| Adding all PSV capacities for header                      | Use the governing **contingency**, not sum of nameplate capacities   |
| K_d = 1.0                                                 | Typical: gas/vapor 0.975, liquid 0.65, certified two-phase ≤ 0.85    |
| Ignoring Mach in header                                   | Mach > 0.7 → choking, can dramatically raise back-pressure on PSVs   |
| Using inlet line ΔP > 3% set                              | API 520 §7.3: > 3% inlet ΔP causes valve chatter, fix the piping     |
| Picking smallest API 526 letter that meets area           | Always pick the **next** letter for spare margin & spare-parts pool  |

## Validation Checklist

- [ ] Each contingency has a documented worst-case mass flow
- [ ] Wetted-area calculation shows boundary up to 7.6 m
- [ ] Drainage / insulation credits supported by P&ID review
- [ ] Header sized so Mach < 0.7 at peak load
- [ ] Built-up back-pressure ≤ 10% (conventional) or ≤ 50% (balanced) of set
- [ ] Inlet line ΔP < 3% of set pressure
- [ ] Flare radiation ≤ 4.73 kW/m² at property line (incl. solar)
- [ ] Dispersion check at flame-out (toxic species)
- [ ] Results saved to `results.json` under `relief_system` with PSV table + flare load summary

## Related Skills

- [`neqsim-process-safety`](../neqsim-process-safety/SKILL.md) — when PSV is the IPL of last resort in LOPA
- [`neqsim-trapped-liquid-fire-rupture`](../neqsim-trapped-liquid-fire-rupture/SKILL.md) — blocked-in liquid fire rupture screening before thermal relief/PFP decisions
- [`neqsim-dynamic-simulation`](../neqsim-dynamic-simulation/SKILL.md) — depressurization (blowdown) is separate from PSV
- [`neqsim-mechanical-design`](../neqsim-api-patterns/SKILL.md) — PSV mechanical via `SafetyValveMechanicalDesign`
- [`neqsim-standards-lookup`](../neqsim-standards-lookup/SKILL.md) — API 520 / 521 / 526 / 537
