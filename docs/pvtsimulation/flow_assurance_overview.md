---
title: Flow Assurance Overview
description: Evidence-based NeqSim workflow for hydrate, asphaltene, scale, wax, corrosion, and pipeline screening, with explicit engineering boundaries.
---

# Flow Assurance Overview

Flow assurance combines fluid characterization, thermodynamics, transport models,
operating scenarios, and laboratory or field evidence. NeqSim can calculate several
inputs to that assessment, but no single result establishes that a line is safe,
restartable, corrosion resistant, or free from deposition.

Use this page to select a screening calculation. Follow the model-specific guides
before using a result in design or operations.

## What NeqSim calculates

| Topic | Supported calculation | Result does not establish |
| --- | --- | --- |
| Hydrates | Equilibrium formation temperature or pressure for a specified fluid and thermodynamic inhibitor composition | Formation time, plugging probability, transportability, or inhibitor dosage |
| Wax | Wax appearance and wax-fraction calculations for a characterized fluid | Deposition rate, gel strength, restart pressure, pigging interval, or chemical performance |
| Asphaltenes | De Boer empirical screening and configured thermodynamic onset or stability models | A universal onset pressure or deposition rate without fluid-specific calibration |
| Mineral scale | Saturation indices from specified water chemistry and conditions | Precipitation kinetics, deposited mass, adhesion, or treatment dose |
| Corrosion | Screening correlations and process-coupled corrosion calculations | Materials qualification, sour-service compliance, or remaining life |
| Pipelines | Selected steady-state, cooldown, transient, erosion, and multiphase calculations | Qualification of every slugging, restart, erosion, or integrity scenario |

A capability in this table is a calculation method, not an engineering approval.
Validate the fluid model and inputs over the pressure, temperature, composition,
salinity, and phase range of interest.

## Executable first screen

The following complete Java 8 program runs three independent screens and reports results through
the repository's Log4j2 logging contract. It uses:

- a hydrate equilibrium calculation for a specified gas, water, MEG, and salt mixture;
- the repository's De Boer implementation, using absolute pressure in bar and in-situ
  oil density in kg/m³;
- mineral saturation indices from explicit produced-water chemistry in mg/L.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening;
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening.DeBoerRisk;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FlowAssuranceScreen {
    private static final Logger logger =
        LogManager.getLogger(FlowAssuranceScreen.class);

    public static void main(String[] args) throws Exception {
        SystemInterface hydrateFluid =
            new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);
        hydrateFluid.addComponent("water", 0.494505);
        hydrateFluid.addComponent("MEG", 0.164835);
        hydrateFluid.addComponent("methane", 0.247253);
        hydrateFluid.addComponent("ethane", 0.0164835);
        hydrateFluid.addComponent("propane", 0.010989);
        hydrateFluid.addComponent("i-butane", 0.00549451);
        hydrateFluid.addComponent("n-butane", 0.00549451);
        hydrateFluid.addComponent("Na+", 0.0274725);
        hydrateFluid.addComponent("Cl-", 0.0274725);
        hydrateFluid.setMixingRule(10);
        hydrateFluid.setMultiPhaseCheck(true);
        hydrateFluid.setHydrateCheck(true);

        ThermodynamicOperations hydrateOps =
            new ThermodynamicOperations(hydrateFluid);
        hydrateOps.hydrateFormationTemperature();
        double hydrateTemperatureC = hydrateFluid.getTemperature("C");

        DeBoerAsphalteneScreening asphalteneScreen =
            new DeBoerAsphalteneScreening(350.0, 150.0, 750.0);
        DeBoerRisk asphalteneRisk = asphalteneScreen.evaluateRisk();
        double asphalteneRiskIndex = asphalteneScreen.calculateRiskIndex();

        ScalePredictionCalculator scaleScreen =
            new ScalePredictionCalculator();
        scaleScreen.setTemperatureCelsius(80.0);
        scaleScreen.setPressureBara(100.0);
        scaleScreen.setCalciumConcentration(400.0);
        scaleScreen.setBariumConcentration(10.0);
        scaleScreen.setStrontiumConcentration(5.0);
        scaleScreen.setIronConcentration(2.0);
        scaleScreen.setMagnesiumConcentration(1300.0);
        scaleScreen.setSodiumConcentration(11000.0);
        scaleScreen.setBicarbonateConcentration(150.0);
        scaleScreen.setSulphateConcentration(10.0);
        scaleScreen.setTotalDissolvedSolids(35000.0);
        scaleScreen.setCO2PartialPressure(2.0);
        scaleScreen.enableAutoPH();
        scaleScreen.calculate();

        logger.info(
            "Hydrate equilibrium temperature: {} °C",
            hydrateTemperatureC);
        logger.info(
            "De Boer screen: {}; risk index {}",
            asphalteneRisk,
            asphalteneRiskIndex);
        logger.info(
            "Calcite SI: {}; barite SI: {}; any scale flag: {}",
            scaleScreen.getCaCO3SaturationIndex(),
            scaleScreen.getBaSO4SaturationIndex(),
            scaleScreen.hasScalingRisk());
    }
}
```

For the stated De Boer inputs, the current implementation returns
`MODERATE_PROBLEM` and a risk index of `1.6`. The hydrate and scale results are
case-specific. Do not transfer them to another fluid or water analysis.

The example intentionally lets calculation errors propagate. A failed equilibrium
calculation is not evidence that no hydrate or scale risk exists.

## Interpret the three results

### Hydrate equilibrium

For a pipeline point at operating temperature $T_{op}$, define thermodynamic
subcooling as:

$$\Delta T_{\mathrm{sub}}=T_{\mathrm{eq}}-T_{\mathrm{op}}$$

Here, $T_{\mathrm{eq}}$ is the calculated hydrate-equilibrium temperature and
$T_{\mathrm{op}}$ is the operating temperature, both expressed on the same K or °C scale.
The temperature difference has the same numerical increment in K and °C.

A positive value means the operating point is below the calculated hydrate
equilibrium temperature. It identifies thermodynamic stability, not nucleation time,
growth rate, plugging probability, or acceptable operating margin. Preserve mass-
versus-mole-fraction distinctions when preparing inhibitor and salt inputs.

See the [hydrate models guide](../thermo/hydrate_models.md) for model selection and
the [screening tools](flowassurance/flow_assurance_screening_tools.md) for profile
calculations.

### De Boer asphaltene screen

`DeBoerAsphalteneScreening` evaluates the repository's implemented boundary
curves using reservoir pressure, saturation pressure, and in-situ oil density.
Do not replace it with a pressure-difference times asphaltene-content heuristic.
Confirm a flagged case with measured onset or precipitation data and a calibrated
model.

Use the [De Boer guide](flowassurance/asphaltene_deboer_screening.md), then select
a thermodynamic or empirical method through the
[asphaltene overview](flowassurance/asphaltene_modeling.md). A phase count greater
than two does not by itself identify an asphaltene-rich phase; inspect the phase
type and the configured model.

### Mineral-scale saturation index

The screening calculator reports:

$$SI=\log_{10}\left(\frac{IAP}{K_{sp}}\right)$$

Here, $IAP$ is the dimensionless ion-activity product and $K_{sp}$ is the
dimensionless thermodynamic solubility product on the same standard-state basis.

Positive SI indicates supersaturation in the calculator. It does not predict how
quickly a mineral precipitates, how much adheres to equipment, or the required
chemical dose. Use representative water analyses, mixing ratios, dissolved-gas
conditions, and uncertainty ranges.

Continue with [mineral-scale formation](mineral_scale_formation.md),
[scale-prediction API details](scale_prediction_api.md), and
[mineral-scale treatment validation](mineral_scale_chemical_treatment_validation.md).

## Build a governed flow-assurance study

1. **Define cases.** Include normal operation, turndown, start-up, shutdown,
   cooldown, depressurization, restart, water breakthrough, composition uncertainty,
   and credible equipment/control states.
2. **Characterize fluids and waters.** Record sampling conditions, compositional
   basis, heavy-end characterization, salinity, ion analyses, inhibitor basis, and
   data quality.
3. **Calculate profiles.** Determine pressure, temperature, phase fractions, water
   availability, velocities, and residence times before applying local screening
   models.
4. **Screen each mechanism.** Use the hydrate, wax, asphaltene, scale, corrosion,
   erosion, emulsion, and transient tools only where their required inputs and
   applicability are satisfied.
5. **Validate and quantify margins.** Compare with laboratory measurements, field
   history, model uncertainty, and sensitivity cases.
6. **Assess mitigation.** Model the applicable thermodynamic or hydraulic effect;
   obtain chemical performance, materials, operability, and mechanical evidence
   from the accountable disciplines.
7. **Record decisions.** Preserve model versions, assumptions, units, input
   provenance, convergence status, limitations, and required expert review.

## Model and mitigation boundaries

| Decision | NeqSim contribution | Additional evidence required |
| --- | --- | --- |
| MEG or methanol strategy | Hydrate equilibrium with specified composition | Injection basis, partitioning, regeneration, losses, kinetics, operability, and vendor data |
| Insulation or active heating | Thermal and hydraulic scenarios | Detailed heat-transfer design, installation, degradation, controls, and transient qualification |
| Wax management | WAT and wax-fraction screening | Deposition/gel testing, restart hydraulics, pigging and chemical qualification |
| Asphaltene management | Empirical screen or calibrated onset/stability model | Fluid-specific laboratory data, deposition behavior, and chemical qualification |
| Scale management | Saturation tendency and water-mixing scenarios | Kinetics, precipitation/deposition tests, treatment compatibility, dose and monitoring |
| Corrosion/materials | Screening rate and process-condition inputs | Applicable standard assessment, wall condition, materials, inspection, and integrity review |
| Depressurization or restart | Process/pipeline transient scenarios | Safeguarding, flare/blowdown capacity, controls, procedures, and multidisciplinary approval |

Do not infer sour-service requirements from bulk H2S mole fraction alone. Do not
infer carbonate-scale or corrosion acceptability from bulk CO2 and water fractions.
Those decisions require phase-specific conditions and the applicable materials,
chemistry, integrity, and standards workflows.

## Related documentation

- [Flow-assurance landing page](flowassurance/README.md)
- [pH stabilization and corrosion control](ph_stabilization_corrosion.md)
- [NORSOK M-506 corrosion calculation](../process/corrosion/norsok_m506_corrosion_rate.md)
- [NORSOK M-001 material selection](../process/corrosion/norsok_m001_material_selection.md)
- [Pipeline corrosion integration](../process/corrosion/pipeline_corrosion_integration.md)
- [Wax characterization](../thermo/characterization/wax_characterization.md)
- [Pipeline modeling](../process/equipment/pipelines.md)
- [Pipeline recipes](../cookbook/pipeline-recipes.md)

