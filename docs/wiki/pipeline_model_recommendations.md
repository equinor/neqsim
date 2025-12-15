# Pipeline Model Recommendations

## Quick Reference Guide

### Which Model Should I Use?

```
                         START
                           │
                           ▼
                 ┌───────────────────┐
                 │ Is flow single    │
                 │ phase (gas OR     │──── YES ───┐
                 │ liquid only)?     │            │
                 └────────┬──────────┘            │
                          │                       ▼
                         NO               ┌──────────────────┐
                          │               │ Is it primarily  │
                          ▼               │ gas?             │
                 ┌───────────────────┐    └────────┬─────────┘
                 │ PipeBeggsAndBrills│             │
                 │ (Multiphase)      │      YES ◄──┴──► NO
                 └───────────────────┘       │          │
                                             ▼          ▼
                                      AdiabaticPipe   PipeBeggsAndBrills
                                      (fast, accurate) (handles viscosity)
```

## Detailed Recommendations

### Scenario 1: High-Pressure Gas Transmission

**Use: `AdiabaticPipe`**

- Long-distance pipelines (>10 km)
- Pressure >20 bara
- Single-phase gas
- Compressibility effects important

```java
AdiabaticPipe pipe = new AdiabaticPipe("transmission", feed);
pipe.setLength(100000);    // 100 km
pipe.setDiameter(0.8);     // 32 inch
pipe.setInletPressure(80); // bara
pipe.run();
```

**Why?** Fastest computation, accounts for gas compressibility, well-validated.

---

### Scenario 2: Oil Pipeline

**Use: `PipeBeggsAndBrills`**

- Crude oil or condensate
- May have dissolved gas
- Viscosity effects important
- Any inclination

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("oil", feed);
pipe.setLength(5000);
pipe.setDiameter(0.3);
pipe.setPipeWallRoughness(4.6e-5);
pipe.setNumberOfIncrements(20);
pipe.run();
```

**Why?** Handles liquid accurately, proper friction for viscous fluids.

---

### Scenario 3: Well Tubing / Flowline

**Use: `PipeBeggsAndBrills`**

- Two-phase or three-phase flow
- Significant elevation change
- Need liquid holdup
- Need flow regime

```java
PipeBeggsAndBrills tubing = new PipeBeggsAndBrills("tubing", well);
tubing.setLength(3000);       // 3 km vertical
tubing.setElevation(2900);    // Almost vertical
tubing.setDiameter(0.0762);   // 3 inch
tubing.setNumberOfIncrements(30);
tubing.run();

System.out.println("Flow regime: " + tubing.getFlowRegime());
System.out.println("Liquid holdup: " + tubing.getSegmentLiquidHoldup(30));
```

**Why?** Only model that handles multiphase + elevation properly.

---

### Scenario 4: Processing Plant Piping

**Use: `AdiabaticTwoPhasePipe`** (for quick estimates) or **`PipeBeggsAndBrills`** (for accuracy)

- Short pipes (<100 m)
- Moderate pressure drops
- Quick calculations needed

```java
AdiabaticTwoPhasePipe pipe = new AdiabaticTwoPhasePipe("P-101", feed);
pipe.setLength(50);
pipe.setDiameter(0.1);
pipe.run();
```

**Why?** Fast, adequate accuracy for short pipes.

---

### Scenario 5: Transient/Dynamic Simulation

**Use: `PipeBeggsAndBrills`**

- Startup/shutdown
- Valve operations
- Flow rate changes

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("dynamic", feed);
pipe.setLength(1000);
pipe.setDiameter(0.2);
pipe.setNumberOfIncrements(20);
pipe.run();

pipe.setCalculateSteadyState(false);
for (int t = 0; t < 300; t++) {
    pipe.runTransient(1.0, uuid);
}
```

**Why?** Only model with transient capability.

---

### Scenario 6: Subsea Pipeline with Cooling

**Use: `PipeBeggsAndBrills`** with heat transfer

- Long subsea lines
- Temperature drop matters
- Hydrate/wax risk

```java
PipeBeggsAndBrills subsea = new PipeBeggsAndBrills("subsea", feed);
subsea.setLength(30000);
subsea.setDiameter(0.254);
subsea.setElevation(-500);
subsea.setRunAdiabatic(false);
subsea.setConstantSurfaceTemperature(277.15);
subsea.setHeatTransferCoefficient(5.0);
subsea.run();
```

**Why?** Handles elevation, multiphase, and heat transfer.

---

## Performance Comparison

| Model | Relative Speed | Memory | Accuracy |
|-------|---------------|--------|----------|
| AdiabaticPipe | ★★★★★ | Low | Good for gas |
| AdiabaticTwoPhasePipe | ★★★★☆ | Low | Moderate |
| PipeBeggsAndBrills | ★★★☆☆ | Medium | Best |

## Common Mistakes

### ❌ Wrong: Using AdiabaticPipe for liquid
```java
// Will give poor results for liquid
AdiabaticPipe pipe = new AdiabaticPipe("oil", liquidFeed);
```

### ✅ Correct: Use PipeBeggsAndBrills for liquid
```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("oil", liquidFeed);
```

---

### ❌ Wrong: Ignoring elevation for wells
```java
PipeBeggsAndBrills tubing = new PipeBeggsAndBrills("tubing", well);
tubing.setLength(3000);
// Missing: tubing.setElevation(3000);  // Vertical well!
```

### ✅ Correct: Set elevation
```java
PipeBeggsAndBrills tubing = new PipeBeggsAndBrills("tubing", well);
tubing.setLength(3000);
tubing.setElevation(3000);  // Important!
```

---

### ❌ Wrong: Too few segments for long pipes
```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("long", feed);
pipe.setLength(50000);  // 50 km
pipe.setNumberOfIncrements(5);  // Only 5 segments for 50 km!
```

### ✅ Correct: Use adequate segments
```java
pipe.setNumberOfIncrements(50);  // 1 km per segment
```

---

### ❌ Wrong: Roughness in wrong units
```java
pipe.setPipeWallRoughness(0.046);  // This is 46 mm! Way too rough!
```

### ✅ Correct: Use meters
```java
pipe.setPipeWallRoughness(4.6e-5);  // 0.046 mm = 4.6×10⁻⁵ m
```

## Validation Checklist

Before trusting results, verify:

- [ ] Pressure drop is positive (unless downhill liquid)
- [ ] Temperature change is reasonable
- [ ] Reynolds number is in expected range (1000-10⁷)
- [ ] Flow regime makes physical sense
- [ ] Results change appropriately with diameter/length

## See Also

- [Pipeline Pressure Drop Overview](pipeline_pressure_drop.md)
- [Beggs & Brill Correlation](beggs_and_brill_correlation.md)
- [Friction Factor Models](friction_factor_models.md)
- [Heat Transfer in Pipelines](pipeline_heat_transfer.md)
- [Transient Simulation](pipeline_transient_simulation.md)
