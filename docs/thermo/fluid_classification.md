---
title: "Reservoir Fluid Classification"
description: "Screen reservoir fluids with NeqSim's C7+, GOR, and phase-envelope classification routes."
---

`FluidClassifier` provides screening classifications for reservoir-fluid studies. Choose one
route deliberately: the methods do not reconcile composition, measured GOR, API gravity, and
laboratory phase behavior into a single validated fluid description.

## Choose the classification route

| Route | Input | What it does |
| --- | --- | --- |
| `classify(fluid)` | NeqSim fluid | Calculates C7+ mol% and applies the C7+ thresholds. |
| `classifyByC7Plus(c7PlusMolPercent)` | C7+ in mol% | Applies the composition thresholds directly. |
| `classifyByGOR(gorScfStb)` | GOR in scf/STB | Applies the GOR thresholds directly. |
| `classifyWithPhaseEnvelope(fluid, reservoirTemperatureK)` | Fluid and reservoir temperature in K | Starts from the C7+ result and heuristically refines it using a calculated critical temperature. If phase-envelope calculation fails, it returns the C7+ result. |

`classify(fluid)` is therefore a **C7+-screening route**, not a phase-envelope calculation and
not a measured-GOR correlation. Use the explicit GOR route when a representative producing GOR
is the controlling input.

## Implemented thresholds

The comparisons below describe the current public API exactly.

| Result | C7+ route (mol%) | GOR route (scf/STB) |
| --- | ---: | ---: |
| Dry gas | `< 0.7` | `> 100,000` |
| Wet gas | `0.7` to `< 4.0` | `> 15,000` to `<= 100,000` |
| Gas condensate | `4.0` to `< 12.5` | `> 3,300` to `<= 15,000` |
| Volatile oil | `12.5` to `< 20.0` | `> 1,000` to `<= 3,300` |
| Black oil | `20.0` to `< 30.0` | `> 200` to `<= 1,000` |
| Heavy oil | `>= 30.0` | `<= 200` |

`classifyByC7Plus` returns `UNKNOWN` for a negative or `NaN` value. The current GOR route does
not reject invalid values, so callers must require a finite, non-negative GOR before calling it.

## Executable classification example

The program uses K, bara, mol%, and scf/STB explicitly. Run it with assertions enabled
(`java -ea ...`); the documentation test compiles it for Java 8 and invokes it with assertions
enabled.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.FluidClassifier;
import neqsim.thermo.util.ReservoirFluidType;

public final class FluidClassificationExample {
  private static final Logger logger =
      LogManager.getLogger(FluidClassificationExample.class);

  private FluidClassificationExample() {}

  public static void main(String[] args) {
    double temperatureK = 373.15;
    double pressureBara = 100.0;
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.75);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.10);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.init(0);

    double c7PlusMolPercent = FluidClassifier.calculateC7PlusContent(fluid);
    ReservoirFluidType compositionScreen = FluidClassifier.classify(fluid);

    double producingGorScfStb = 5000.0;
    if (!Double.isFinite(producingGorScfStb) || producingGorScfStb < 0.0) {
      throw new IllegalArgumentException("GOR must be finite and non-negative");
    }
    ReservoirFluidType gorScreen = FluidClassifier.classifyByGOR(producingGorScfStb);

    assert Math.abs(c7PlusMolPercent - 10.0) < 1.0e-8;
    assert compositionScreen == ReservoirFluidType.GAS_CONDENSATE;
    assert gorScreen == ReservoirFluidType.GAS_CONDENSATE;

    String report = FluidClassifier.generateClassificationReport(fluid);
    assert report.contains("C7+ Content: 10.00 mol%");
    assert report.contains("Fluid Type: Gas Condensate");

    logger.info(
        "C7+ screen: {} mol%, composition={}, GOR={} scf/STB, GOR screen={}",
        c7PlusMolPercent,
        compositionScreen,
        producingGorScfStb,
        gorScreen);
  }
}
```

## Engineering boundaries

- Treat the result as a screening label. Confirm the fluid description against representative
  PVT samples and laboratory CCE, CVD, DLE, separator, and viscosity data as applicable.
- `calculateC7PlusContent` identifies heavy components using molar mass, component names, and
  TBP/plus-fraction flags. Review the characterized pseudo-components before relying on the result.
- GOR classification depends on the measurement basis and producing conditions. Convert to scf/STB
  on the intended standard-condition basis before using `classifyByGOR`.
- `estimateAPIGravity` flashes a cloned fluid at 288.71 K and 1.01325 bara and returns `NaN` when
  no oil phase can be evaluated. It is an estimate, not a substitute for measured stock-tank density.
- `classifyWithPhaseEnvelope` is a heuristic around calculated critical temperature. Inspect the
  calculated envelope and fluid model rather than treating the label as design evidence.

## Result metadata and reports

`ReservoirFluidType` exposes `getDisplayName()`, `getTypicalGORRange()`, and
`getTypicalC7PlusRange()`. `generateClassificationReport(fluid)` reports the calculated C7+ screen,
the typical ranges stored on the enum, an API-gravity estimate when available, and modeling
recommendations. Parse neither the human-readable report nor the range strings as a stable data
interface; retain the enum and numeric inputs in engineering records.

## References

- Whitson, C. H. and Brulé, M. R., *Phase Behavior*, SPE Monograph Series.
- McCain, W. D., *Properties of Petroleum Fluids*, 2nd ed.
- [Whitson Wiki: reservoir-fluid classification](https://wiki.whitson.com/phase_behavior/classification/reservoir_fluid_type/)

## See also

- [Fluid creation guide](fluid_creation_guide)
- [PVT characterization](pvt_fluid_characterization)
- [Fluid characterization](../wiki/fluid_characterization)
- [Black-oil models](../blackoil/)
