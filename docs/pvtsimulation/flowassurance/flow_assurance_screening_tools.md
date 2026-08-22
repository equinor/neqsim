---
title: "Flow Assurance Screening Tools"
description: "Current API, unit, result, and engineering boundaries for NeqSim cooldown, corrosion, scale, and wax screening utilities."
---

NeqSim provides focused utilities for early flow-assurance screening. They are useful for
comparing cases and exposing margins, but they do not replace a qualified transient model,
water-analysis workflow, corrosion/materials assessment, laboratory programme, or operating
procedure.

## Select the correct tool

| Question | Class | Primary result | Required interpretation |
| --- | --- | --- | --- |
| How quickly can a stagnant line cool? | `PipelineCooldownCalculator` | Temperature profile, threshold time, and lumped time constant | A one-dimensional lumped screen; no axial gradients, multiphase redistribution, or restart hydraulics |
| What is the simplified internal CO2-corrosion rate? | `DeWaardMilliamsCorrosion` | Baseline/corrected rate and screening diagnostics | Not a complete NORSOK M-506 calculation, material qualification, inhibitor-dose design, or remaining-life assessment |
| Is a specified water analysis supersaturated? | `ScalePredictionCalculator` | Mineral saturation indices and positive-SI flags | Supersaturation is not precipitation rate, deposited mass, adhesion, or inhibitor dose |
| What wax curve does a characterized fluid produce? | `WaxCurveCalculator` | WAT, raw/enforced wax fractions, and flash diagnostics | Requires a suitable wax-enabled fluid model and calibration; curve correction is not a deposition or restart model |

All four classes are in `neqsim.pvtsimulation.flowassurance`.

## Units and state contract

| Input or result | Unit/meaning |
| --- | --- |
| Cooldown temperatures | Kelvin |
| Cooldown time step / horizon | minutes / hours |
| Pipeline dimensions | metres |
| Overall heat-transfer coefficient | W/(m2 K), referenced to outside diameter |
| Fluid density / heat capacity | kg/m3 / J/(kg K) |
| Corrosion temperature | degrees Celsius |
| CO2 and H2S partial pressure | bar |
| Corrosion rate / linear allowance screen | mm/year / mm |
| Water-ion and total-dissolved-solids inputs | mg/L |
| Scale pressure | bara |
| Wax pressure / temperature range | bara / degrees Celsius |
| Wax fraction | mass fraction of the total flashed system |

Set all inputs before calling `calculate()`. These mutable calculators retain their configured
state and results. Create separate instances for independent cases, or reset every case-defining
input explicitly.

## Executable Java 8 screening example

The following program executes cooldown, simplified corrosion, and mineral-scale screens. The
focused regression in
`src/test/java/neqsim/pvtsimulation/flowassurance/FlowAssuranceDocumentationTest.java`
exercises the same APIs and result bounds.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.flowassurance.DeWaardMilliamsCorrosion;
import neqsim.pvtsimulation.flowassurance.PipelineCooldownCalculator;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;

public final class FlowAssuranceScreeningQuickStart {
  private static final Logger logger =
      LogManager.getLogger(FlowAssuranceScreeningQuickStart.class);

  private FlowAssuranceScreeningQuickStart() {}

  public static void main(String[] args) {
    PipelineCooldownCalculator cooldown = new PipelineCooldownCalculator();
    cooldown.setInternalDiameter(0.254);
    cooldown.setWallThickness(0.0127);
    cooldown.setInsulationThickness(0.050);
    cooldown.setInitialFluidTemperature(273.15 + 80.0);
    cooldown.setAmbientTemperature(273.15 + 4.0);
    cooldown.setFluidDensity(750.0);
    cooldown.setFluidSpecificHeat(2200.0);
    cooldown.setOverallUValue(3.0);
    cooldown.setTimeStepMinutes(5.0);
    cooldown.setTotalTimeHours(48.0);
    cooldown.calculate();

    double timeConstantHours = cooldown.getTimeConstantHours();
    double temperatureAt12HoursK = cooldown.getTemperatureAtTime(12.0);
    double timeTo20CHours = cooldown.getTimeToReachTemperature(273.15 + 20.0);

    DeWaardMilliamsCorrosion corrosion = new DeWaardMilliamsCorrosion();
    corrosion.setTemperatureCelsius(60.0);
    corrosion.setCO2PartialPressure(2.0);
    corrosion.setPH(4.5);
    corrosion.setFlowVelocity(2.0);
    corrosion.setInhibitorEfficiency(0.80);
    double corrosionRateMmPerYear = corrosion.calculateCorrosionRate();

    ScalePredictionCalculator scale = new ScalePredictionCalculator();
    scale.setTemperatureCelsius(80.0);
    scale.setPressureBara(100.0);
    scale.setCalciumConcentration(1000.0);
    scale.setBicarbonateConcentration(500.0);
    scale.setBariumConcentration(50.0);
    scale.setSulphateConcentration(200.0);
    scale.setTotalDissolvedSolids(50000.0);
    scale.setCO2PartialPressure(2.0);
    scale.enableAutoPH();
    scale.calculate();

    double calciteSI = scale.getCaCO3SaturationIndex();
    double bariteSI = scale.getBaSO4SaturationIndex();
    if (!Double.isFinite(timeConstantHours)
        || temperatureAt12HoursK >= 273.15 + 80.0
        || temperatureAt12HoursK <= 273.15 + 4.0
        || !Double.isFinite(corrosionRateMmPerYear)
        || corrosionRateMmPerYear < 0.0
        || !Double.isFinite(calciteSI)
        || !Double.isFinite(bariteSI)) {
      throw new IllegalStateException("Flow-assurance screening result is invalid");
    }

    logger.info(
        "Cooldown tau {} h, time to 20 C {} h, corrosion {} mm/year, calcite SI {}, barite SI {}",
        timeConstantHours,
        timeTo20CHours,
        corrosionRateMmPerYear,
        calciteSI,
        bariteSI);
    logger.debug("Cooldown result: {}", cooldown.toJson());
    logger.debug("Scale result: {}", scale.toJson());
  }
}
```

`getTimeToReachTemperature(...)` returns `-1.0` if the threshold is not reached within the
configured horizon. Treat that as “not reached in this simulation,” not as infinite no-touch
time. The time-step result comes from explicit Euler integration; repeat important cases with a
smaller step and check that the decision-relevant threshold time is stable.

## Pipeline cooldown

The model treats the fluid, steel wall, and insulation as a combined thermal mass per unit
length. With an outside-diameter-referenced overall coefficient, its governing screen is:

$$\frac{dT}{dt}=-\frac{U A_o(T-T_a)}{\sum_i m_i C_{p,i}}$$

and the corresponding lumped time constant is:

$$\tau=\frac{\sum_i m_i C_{p,i}}{U A_o}$$

Use either `setOverallUValue(...)` or `useLayerCalculation()`. The layer route uses the configured
steel, insulation, coating, and external-convection properties. It does not resolve axial thermal
gradients, phase redistribution, soil/seabed transients, natural convection, or changing fluid
properties during cooldown.

Preserve the time and temperature arrays from `getTimeHours()` and `getFluidTemperature()` when a
downstream study needs the profile. `toJson()` is a serializable result handoff, but the calculator
itself does not own asset identity, input provenance, or approval state; store those alongside the
JSON in the study record.

## Simplified CO2-corrosion screen

The implemented baseline is:

$$\log_{10}(V_{cor})=5.8-\frac{1710}{T+273.15}+0.67\log_{10}(p_{CO_2})$$

where `T` is in degrees Celsius, `pCO2` is in bar, and `Vcor` is returned in mm/year. The corrected
screen multiplies empirical pH, scale, glycol, flow, and inhibitor-efficiency factors and can add
an elemental-sulfur contribution.

Important ownership boundaries:

- `setTotalPressure(...)` and `setPipeDiameter(...)` are retained in the object and JSON report,
  but the current corrosion-rate calculation does not use them. Supply CO2 partial pressure
  directly and use `setFlowVelocity(...)` for the implemented flow factor.
- `setInhibitorEfficiency(...)` supplies an assumed fractional efficiency. The class does not
  calculate inhibitor dose, availability, partitioning, compatibility, or persistence.
- `estimateCorrosionAllowance(years)` is only rate times duration. It is not a remaining-life or
  corrosion-allowance design procedure.
- `isSourService()` is a simple H2S-partial-pressure flag. Apply the controlled project edition of
  the applicable materials standard and its complete environmental limits separately.

Use the fuller [NORSOK M-506 calculation guide](../../process/corrosion/norsok_m506_corrosion_rate.md)
and [NORSOK M-001 material-selection guide](../../process/corrosion/norsok_m001_material_selection.md)
when those workflows apply. Neither guide removes the need for accountable materials and
integrity review.

## Mineral-scale saturation screen

The calculator reports:

$$SI=\log_{10}\left(\frac{IAP}{K_{sp}}\right)$$

A positive SI is a supersaturation flag in this calculator. `getScaleRisks()` and
`hasScalingRisk()` use the same `SI > 0` boundary; the JSON labels values above `0.5` as high and
positive values up to `0.5` as moderate. These labels are screen presentation, not a kinetic or
operability acceptance criterion.

Record the original water-analysis units and sampling conditions. `enableAutoPH()` estimates pH
from configured conditions; use measured or independently calculated aqueous-phase pH when the
decision requires it. For high salinity, mixed waters, coupled precipitation, or chemical
treatment, continue with [mineral-scale formation](../mineral_scale_formation.md),
[scale-prediction API details](../scale_prediction_api.md), and
[chemical-treatment validation](../mineral_scale_chemical_treatment_validation.md).

## Wax curve API

`WaxCurveCalculator` accepts one `SystemInterface`; pressure is configured separately. Temperature
range inputs and WAT results are in degrees Celsius, not Kelvin:

```java
WaxCurveCalculator waxCurve = new WaxCurveCalculator(waxFluid);
waxCurve.setPressure(50.0);
waxCurve.setTemperatureRange(-10.0, 60.0, 1.0);
waxCurve.calculate();

double watC = waxCurve.getWaxAppearanceTemperatureC();
double[] temperaturesC = waxCurve.getTemperaturesC();
double[] rawWaxFractions = waxCurve.getRawWaxFractions();
double[] enforcedWaxFractions = waxCurve.getWaxWeightFractions();
int corrections = waxCurve.getMonotonicityCorrections();
int failedFlashes = waxCurve.getFailCount();
```

This fragment assumes that `waxFluid` is already characterized with a suitable wax model. The
calculator scans from high to low temperature. When monotonicity enforcement is enabled, it
replaces decreases in wax mass fraction with the preceding maximum and reports the number of
corrections. A failed flash is retained as a diagnostic and the curve substitutes the previous
fraction; therefore inspect `getSuccessCount()`, `getFailCount()`, raw values, and corrected values
before accepting a curve. `calculateWAT()` runs the separate built-in WAT operation and returns
degrees Celsius.

See [wax characterization](../../thermo/characterization/wax_characterization.md) for fluid setup,
calibration, and interpretation. Neither the WAT nor wax-fraction curve predicts deposition rate,
gel strength, restart pressure, pigging interval, or chemical performance.

## Related documentation

- [Flow-assurance landing page](README.md)
- [Integrated flow-assurance overview](../flow_assurance_overview.md)
- [De Boer asphaltene screening](asphaltene_deboer_screening.md)
- [Erosion prediction](erosion_prediction.md)
- [Emulsion viscosity](emulsion_viscosity_calculator.md)
- [Hydrate models](../../thermo/hydrate_models.md)

