---
title: "Adsorption Recipes"
description: "Executable Java recipes for equilibrium adsorption, fixed-bed screening, and cycle scheduling, with explicit model boundaries."
---

These recipes demonstrate the current NeqSim adsorption APIs with complete Java 8
programs. They are screening examples, not adsorber or regeneration-system designs.
Before using a result, confirm that the selected adsorbent/component pair has measured
parameters over the relevant temperature, pressure, and composition range.

See [Adsorption Isotherm Models](../thermo/adsorption_isotherms.md) for the equations
and [Adsorption Bed](../process/equipment/adsorption_bed.md) for the equipment model.

## Choose a recipe

| Task | Recipe | Main limitation |
| --- | --- | --- |
| Compare equilibrium loading | [Competitive equilibrium screen](#competitive-equilibrium-screen) | Database parameters are screening data, not a vendor guarantee |
| Estimate fixed-bed response | [Steady and transient bed screen](#steady-and-transient-bed-screen) | One-dimensional LDF/Ergun screening model |
| Define PSA or TSA timing | [Cycle schedule](#cycle-schedule) | One controller/bed; not a cyclic-steady-state or multi-bed solver |

## Parameter and model checks

`LangmuirAdsorption` loads parameters by exact component and solid-material names.
When no row matches, the current implementation substitutes generic default
parameters. That fallback keeps a calculation running, but it is not evidence for the
named adsorbent. Audit the packaged parameter inventory or set independently validated
parameters before ranking materials.

For the CO2/methane example below, these material names have parameter rows for both
components:

- `AC Calgon F400`
- `Zeolite 13X`
- `Zeolite 5A`
- `Silica Gel`
- `MOF HKUST-1`

Use `calcExtendedLangmuir(...)` for competitive mixture screening. Calling
`calcAdsorption(...)` evaluates each component independently and does not apply the
shared extended-Langmuir denominator.

Total pressures passed to thermodynamic-system constructors are absolute bar (bara).
Equilibrium loadings are reported in mol/kg of adsorbent and Langmuir constants in
1/bar.

## Competitive equilibrium screen

This program flashes one gas state, then compares only material/component pairs that
exist in the packaged parameter inventory.

```java
import neqsim.physicalproperties.interfaceproperties.solidadsorption.LangmuirAdsorption;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class CompetitiveAdsorptionScreen {
    public static void main(String[] args) throws Exception {
        SystemSrkEos gas = new SystemSrkEos(298.15, 10.0);
        gas.addComponent("methane", 0.90);
        gas.addComponent("CO2", 0.10);
        gas.setMixingRule("classic");
        new ThermodynamicOperations(gas).TPflash();

        String[] materials = {
            "AC Calgon F400",
            "Zeolite 13X",
            "Zeolite 5A",
            "Silica Gel",
            "MOF HKUST-1"
        };

        System.out.printf(
            "%-18s %14s %14s %12s%n",
            "Material",
            "CO2 [mol/kg]",
            "CH4 [mol/kg]",
            "Selectivity");

        for (String material : materials) {
            LangmuirAdsorption model = new LangmuirAdsorption(gas);
            model.setSolidMaterial(material);
            model.calcExtendedLangmuir(0);

            double co2Loading = model.getSurfaceExcess("CO2");
            double methaneLoading = model.getSurfaceExcess("methane");
            double selectivity = model.getSelectivity(1, 0, 0);

            System.out.printf(
                "%-18s %14.4f %14.4f %12.2f%n",
                material,
                co2Loading,
                methaneLoading,
                selectivity);
        }
    }
}
```

The component indexes in `getSelectivity(1, 0, 0)` follow the creation order:
methane is index 0 and CO2 is index 1. Prefer loading queries by component name where
an index is not required.

For a temperature or pressure study, rebuild and flash the state at every condition.
Working capacity is a difference, not a ratio:

$$
\Delta q_i = q_{i,ads}(T_{ads}, P_{ads}, y_{ads})
             - q_{i,regen}(T_{regen}, P_{regen}, y_{regen})
$$

Both states and their gas compositions must be specified. A two-temperature curve at
one pressure does not by itself define TSA working capacity.

## Steady and transient bed screen

The next program evaluates the same characterized bed first with the steady screening
method and then from a clean transient grid. A new calculation identifier is used for
every physical time step.

```java
import java.util.UUID;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.IsothermType;
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class AdsorptionBedScreen {
    private static Stream createFeed() {
        SystemSrkEos gas = new SystemSrkEos(298.15, 10.0);
        gas.addComponent("methane", 0.85);
        gas.addComponent("CO2", 0.10);
        gas.addComponent("nitrogen", 0.05);
        gas.setMixingRule("classic");

        Stream feed = new Stream("feed", gas);
        feed.setFlowRate(1000.0, "kg/hr");
        feed.run();
        return feed;
    }

    private static AdsorptionBed createBed(String name, Stream feed) {
        AdsorptionBed bed = new AdsorptionBed(name, feed);
        bed.setBedDiameter(1.0);
        bed.setBedLength(3.0);
        bed.setAdsorbentMaterial("AC Calgon F400");
        bed.setIsothermType(IsothermType.LANGMUIR);
        bed.setKLDF(0.05); // 1/s; illustrative value only
        return bed;
    }

    public static void main(String[] args) {
        AdsorptionBed steadyBed = createBed("steady screen", createFeed());
        steadyBed.run();
        double outletCO2 = steadyBed.getOutletStream()
            .getFluid()
            .getPhase(0)
            .getComponent("CO2")
            .getx();
        System.out.printf(
            "Steady screen: adsorbent %.1f kg, pressure drop %.1f Pa, outlet CO2 %.6f%n",
            steadyBed.getAdsorbentMass(),
            steadyBed.getPressureDrop(),
            outletCO2);

        AdsorptionBed transientBed = createBed("transient screen", createFeed());
        transientBed.setNumberOfCells(20);
        transientBed.setCalculateSteadyState(false);
        transientBed.setBreakthroughThreshold(0.05);

        double dt = 0.25; // s
        for (int step = 0; step < 20; step++) {
            transientBed.runTransient(dt, UUID.randomUUID());
        }

        double co2Loading = transientBed.getAverageLoading(1);
        System.out.printf(
            "Transient screen: time %.2f s, average CO2 loading %.6f mol/kg, breakthrough %s%n",
            transientBed.getElapsedTime(),
            co2Loading,
            transientBed.isBreakthroughOccurred());
    }
}
```

The bed implementation combines equilibrium isotherms, a linear-driving-force (LDF)
rate, one-dimensional cells, and the Ergun pressure-drop equation. Its current
`LANGMUIR` and `EXTENDED_LANGMUIR` bed selections both create a
`LangmuirAdsorption` model, while the bed calls `calcAdsorption(...)` for its local
equilibrium. Use the direct competitive calculation above when the shared
extended-Langmuir denominator is required.

The `0.05 1/s` LDF value and 20-cell grid are executable demonstration inputs, not
defaults for design. Calibrate component-specific mass-transfer coefficients against
representative breakthrough data. Repeat the calculation with successively smaller
time steps and finer grids until decision-relevant outputs change within a declared
tolerance.

Do not use the result alone to select vessel dimensions, cycle time, adsorbent mass,
guard-bed life, or product specification. Those decisions also require adsorbent
vendor data, laboratory breakthrough/regeneration evidence, heat effects, distributor
and support-grid design, pressure-drop limits, attrition, ageing/poisoning, control
logic, and relief/mechanical review.

## Cycle schedule

`AdsorptionCycleController` schedules operating phases for one bed. The program below
inspects PSA and TSA schedules without claiming a cyclic-steady-state solution.

```java
import neqsim.physicalproperties.interfaceproperties.solidadsorption.IsothermType;
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.adsorber.AdsorptionCycleController;
import neqsim.process.equipment.adsorber.AdsorptionCycleController.PhaseStep;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class AdsorptionCycleSchedule {
    private static AdsorptionBed createBed() {
        SystemSrkEos gas = new SystemSrkEos(298.15, 10.0);
        gas.addComponent("methane", 0.90);
        gas.addComponent("CO2", 0.10);
        gas.setMixingRule("classic");

        Stream feed = new Stream("cycle feed", gas);
        feed.setFlowRate(1000.0, "kg/hr");
        feed.run();

        AdsorptionBed bed = new AdsorptionBed("cycle bed", feed);
        bed.setAdsorbentMaterial("Zeolite 13X");
        bed.setIsothermType(IsothermType.LANGMUIR);
        bed.setCalculateSteadyState(false);
        return bed;
    }

    private static void printSchedule(
            String name,
            AdsorptionCycleController controller) {
        System.out.println(name);
        for (PhaseStep step : controller.getSchedule()) {
            System.out.printf(
                "  %-18s duration %.0f s, target %.2f bara, %.2f K%n",
                step.getPhase(),
                step.getDuration(),
                step.getTargetPressure(),
                step.getTargetTemperature());
        }
    }

    public static void main(String[] args) {
        AdsorptionCycleController controller =
            new AdsorptionCycleController(createBed());

        controller.configurePSA(300.0, 30.0, 60.0, 30.0, 1.0);
        printSchedule("PSA schedule", controller);

        controller.configureTSA(1800.0, 600.0, 300.0, 523.15);
        printSchedule("TSA schedule", controller);
    }
}
```

When the configured schedule loops, the current controller resets and reinitializes
the bed. Repeating that loop therefore does not establish cyclic steady state. A
working PSA/TSA system also needs coordinated beds, valves, equalization/purge paths,
thermal and pressure transients, product/recovery balances, convergence criteria, and
control sequencing.

## Capillary-condensation boundary

This cookbook does not currently provide a capillary-condensation program. The
current property estimator documents critical volume in cm3/mol but applies a
conversion inconsistent with that basis before evaluating the Kelvin equation. In the
repository's nitrogen-at-77-K example, the resulting Kelvin radius is nonphysical for
a mesopore calculation. Treat this API as unvalidated until the unit conversion and
representative reference cases are corrected in production code.

## Common checks before interpretation

1. Confirm every component/material pair has a traceable parameter source. Do not
   interpret generic fallback values as material data.
2. Keep pressure basis (`bara`) and adsorption units (`mol/kg`, `1/bar`, `1/s`)
   explicit.
3. Flash each thermodynamic state before an equilibrium comparison.
4. Define adsorption and regeneration temperature, pressure, and gas composition when
   calculating working capacity.
5. Perform grid, time-step, and parameter sensitivity studies for transient results.
6. Close mass, component, and energy balances for a cycle; one printed concentration
   or loading is not a separation-performance certificate.
7. Treat chemical, mechanical, control, operability, and safety approval as separate
   accountable workflows.

## Related documentation

- [Adsorption Isotherm Models](../thermo/adsorption_isotherms.md)
- [Adsorption Bed Process Equipment](../process/equipment/adsorption_bed.md)
- [Process Recipes](process-recipes.md)
- [Thermodynamics Recipes](thermodynamics-recipes.md)
