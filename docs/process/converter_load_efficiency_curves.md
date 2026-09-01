---
title: Converter load-efficiency curves
description: Add part-load performance to generators, transformers, and prime movers.
---

# Converter load-efficiency curves

`Generator`, `Transformer`, and `PrimeMover` support optional piecewise-linear efficiency versus useful-output load. Their existing constant-efficiency behavior remains the default.

```java
LoadEfficiencyCurve curve = new LoadEfficiencyCurve(
    new double[] {0.25, 0.50, 0.75, 1.00},
    new double[] {0.80, 0.90, 0.94, 0.96});

Generator generator = new Generator("main generator");
generator.setRatedOutputPower(20.0e6);
generator.setLoadEfficiencyCurve(curve);
```

The curve is evaluated from useful output divided by rated useful output. Endpoints are clamped, intermediate points are linearly interpolated, and output above the rating is rejected.

```java
double inputRequired = generator.getRequiredInputPowerForOutput(10.0e6);
double efficiency = generator.getEfficiencyAtOutputPower(10.0e6);
```

Forward conversion uses bounded iteration so the input, useful output, and heat loss remain consistent even when efficiency changes with load.

The same API is available for transformers and fuel-fired prime movers:

```java
Transformer transformer = new Transformer("export transformer");
transformer.setRatedOutputPower(25.0e6);
transformer.setLoadEfficiencyCurve(transformerCurve);

PrimeMover turbine = new PrimeMover("gas turbine driver");
turbine.setRatedOutputPower(15.0e6);
turbine.setLoadEfficiencyCurve(turbineCurve);
```

Call `clearLoadEfficiencyCurve()` to restore nominal constant efficiency.

## Scope

The curve represents steady load-dependent conversion efficiency. Startup fuel, minimum uptime, ambient derating, degradation, maintenance state, and dynamic thermal limits should be represented by higher-level equipment or commitment models.
