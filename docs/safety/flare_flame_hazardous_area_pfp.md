---
title: "Flare Flame, Hazardous Area Classification and PFP Demand"
description: "API 537 flare flame geometry, radiation and noise; IEC 60079-10-1 hazardous-area (zone) classification from a release source; and API 521 passive-fire-protection demand from a fire heat flux — Api537FlareFlameModel, HazardousAreaCalculator and PfpDemandCalculator."
keywords: "API 537, flare flame, flame length, flame tilt, sterile zone, radiation, flare noise, IEC 60079-10-1, hazardous area, zone classification, Zone 0, Zone 1, Zone 2, LFL, PFP, passive fire protection, API 521, H60, J120"
---

# Flare Flame, Hazardous Area Classification and PFP Demand

| Class | Purpose | Standard |
|-------|---------|----------|
| `Api537FlareFlameModel` | Flare flame length, tilt, radiation contours and noise | API 537 |
| `HazardousAreaCalculator` | Hazardous-area distance and zone from a release source | IEC 60079-10-1 |
| `PfpDemandCalculator` | Passive-fire-protection demand and rating from a fire flux | API 521 |

Classes live under `neqsim.process.safety.fire` and
`neqsim.process.safety.dispersion`.

## API 537 flare flame model

The flare flame model takes the relief mass flow, heat of combustion, radiant
fraction and tip exit velocity. It returns flame geometry, sterile-zone radii
for the standard radiation thresholds, ground-level heat flux, and noise:

```java
import neqsim.process.safety.fire.Api537FlareFlameModel;

// mDot[kg/s], heat of combustion[J/kg], radiant fraction, exit velocity[m/s]
Api537FlareFlameModel model =
    new Api537FlareFlameModel(50.0, 50.0e6, 0.20, 200.0)
        .setStackHeightM(40.0)
        .setWindSpeedMPerS(10.0);

double length = model.flameLengthM();
double tilt   = model.flameTiltRad();

// Sterile-zone radii (nested: lower flux reaches further)
double r158 = model.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_1_58_KW);
double r473 = model.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_4_73_KW);
double r946 = model.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_9_46_KW);

double flux = model.heatFluxAtGroundDistance(60.0);   // W/m² at 60 m
double pwl  = model.soundPowerLevelDb();
double spl  = model.soundPressureLevelDb(100.0);       // dB at 100 m
```

With zero wind the flame is vertical (`flameTiltRad() == 0`); increasing wind
tilts it downwind. The flux thresholds `FLUX_1_58_KW`, `FLUX_4_73_KW`, and
`FLUX_9_46_KW` correspond to the API 521 / API 537 personnel-exposure limits.

## IEC 60079-10-1 hazardous-area classification

`HazardousAreaCalculator` estimates the hazardous distance from a gas release
and assigns an area zone from the release grade:

```java
import neqsim.process.safety.dispersion.HazardousAreaCalculator;
import neqsim.process.safety.dispersion.HazardousAreaCalculator.ReleaseGrade;

// mDot[kg/s], P[bara], T[K], LFL[vol frac], molar mass[kg/mol]
HazardousAreaCalculator calc =
    new HazardousAreaCalculator(0.1, 6.0, 340.0, 0.044, 0.01604)
        .setReleaseGrade(ReleaseGrade.SECONDARY)
        .setSafetyFactor(0.5);

double distance = calc.hazardousDistanceM();
String zone = calc.zoneClassification();   // "Zone 2"
```

The release grade maps to the zone: `CONTINUOUS` → Zone 0, `PRIMARY` → Zone 1,
`SECONDARY` → Zone 2. A larger release rate increases the hazardous distance, and
a stricter (smaller) safety factor also increases it.

## API 521 passive-fire-protection demand

`PfpDemandCalculator` compares the bare-steel time-to-critical-temperature under
a fire heat flux against the required survival time and selects a PFP rating:

```java
import neqsim.process.safety.fire.PfpDemandCalculator;
import neqsim.process.safety.fire.PfpDemandCalculator.FireType;
import neqsim.process.safety.fire.PfpDemandCalculator.PfpDemandResult;
import neqsim.process.safety.fire.PfpDemandCalculator.PfpRating;

// heat flux[W/m²], wall thickness[m]; required survival time[s]
PfpDemandResult res = new PfpDemandCalculator(100.0e3, 0.012)
    .setFireType(FireType.POOL)
    .evaluate(3600.0);

boolean required = res.isPfpRequired();              // true
double thickness = res.getRequiredPfpThicknessMm();
PfpRating rating = res.getRating();                  // H60
```

`FireType` selects `POOL` or `JET` fire heat-up kinetics. A thick wall with a
short required survival time may not need PFP at all
(`isPfpRequired() == false`, rating `NONE`). Longer jet-fire durations escalate
the rating (e.g. a 120 min jet-fire case yields `J120`).

## Verification

```bash
./mvnw test -Dtest=Api537FlareFlameModelTest,HazardousAreaCalculatorTest,PfpDemandCalculatorTest
```

## Related Documentation

- [Dispersion and Consequence Analysis](dispersion_and_consequence.md)
- [Relief Valve Sizing (API 520/521)](relief_valve_sizing_api.md)
- [Trapped Liquid Fire Rupture](trapped_liquid_fire_rupture.md)
- [Fire Blowdown Capabilities](fire_blowdown_capabilities.md)
