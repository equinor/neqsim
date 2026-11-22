---
title: "Solar Panel Unit Operation"
description: "Screen idealized solar-panel electrical generation with explicit units, input checks, and an executable NeqSim example."
---

# Solar Panel Unit Operation

Use `SolarPanel` for a bounded, steady-state screening estimate of electrical power from a
specified irradiance, active panel area, and conversion efficiency. The model evaluates

$P_{\mathrm{el}} = G A \eta$

where $G$ is irradiance in `W/m²`, $A$ is panel area in `m²`, $\eta$ is a dimensionless
fraction, and $P_{\mathrm{el}}$ is generated electrical power in `W`.

## Inputs and sign convention

- Irradiance must be finite and non-negative in `W/m²`.
- Panel area must be finite and non-negative in `m²`.
- Efficiency must be finite and between `0.0` and `1.0`; use `0.20` for 20%.
- `getPower()` returns positive generated power in `W`.
- The `electricalPower` energy port reports generation as a negative duty in `W`. A
  320 W generation result therefore has a -320 W duty.

The setters do not enforce these engineering bounds, so validate inputs before configuring
the unit.

## Executable solar-panel screening example

The complete program uses Java 8, Log4j2, and assertions. Run it with assertions enabled
(for example, `java -ea SolarPanelScreeningExample`).

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.powergeneration.SolarPanel;

public final class SolarPanelScreeningExample {
  private static final Logger logger =
      LogManager.getLogger(SolarPanelScreeningExample.class);

  private SolarPanelScreeningExample() {}

  public static void main(String[] args) {
    double irradianceWPerSquareMetre = 800.0;
    double panelAreaSquareMetres = 2.0;
    double efficiencyFraction = 0.20;

    requireFiniteNonNegative("irradiance", irradianceWPerSquareMetre);
    requireFiniteNonNegative("panel area", panelAreaSquareMetres);
    requireEfficiency(efficiencyFraction);

    SolarPanel panel = new SolarPanel("screening panel");
    panel.setIrradiance(irradianceWPerSquareMetre);
    panel.setPanelArea(panelAreaSquareMetres);
    panel.setEfficiency(efficiencyFraction);
    panel.run();

    double expectedPowerW =
        irradianceWPerSquareMetre * panelAreaSquareMetres * efficiencyFraction;
    double generatedPowerW = panel.getPower();
    double electricalDutyW = panel.getEnergyStream().getDuty();

    assert Double.isFinite(generatedPowerW);
    assert Math.abs(generatedPowerW - expectedPowerW) < 1.0e-9;
    assert Math.abs(electricalDutyW + generatedPowerW) < 1.0e-9;
    assert Math.abs(generatedPowerW - 320.0) < 1.0e-9;

    logger.info(
        "Generated power: {} W; electrical-port duty: {} W",
        generatedPowerW,
        electricalDutyW);
  }

  private static void requireFiniteNonNegative(String name, double value) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  private static void requireEfficiency(double value) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException("efficiency must be a fraction from 0 to 1");
    }
  }
}
```

For the specified inputs, the analytical check is
$800\ \mathrm{W/m^2} \times 2\ \mathrm{m^2} \times 0.20 = 320\ \mathrm{W}$.
The example also verifies the energy-port duty is `-320 W`.

## Engineering boundary

This model is an algebraic conversion of user-supplied irradiance, area, and efficiency. It
does not calculate sun angle, shading, spectral response, cell temperature, weather
variability, soiling, degradation, wiring or inverter losses, curtailment, storage, or
electrical-network behavior. Supply appropriately derated inputs and use a qualified
time-series or electrical model for design, guarantees, controls, or safety decisions.
