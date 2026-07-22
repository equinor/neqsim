---
title: "Java simulations inspired by NeqSim Colab notebooks"
description: "This guide shows how to translate the interactive workflows from the"
---

# Java simulations inspired by NeqSim Colab notebooks

This guide shows how to translate the interactive workflows from the
[NeqSim-Colab](https://github.com/EvenSol/NeqSim-Colab) notebooks into
pure Java simulations. The notebooks run NeqSim through a Python bridge,
but the thermodynamics, process models, and solver settings are the same
as in Java. Use this page to recreate those examples in IDEs or CI
pipelines where Java is preferred.

## Prerequisites

1. Add the NeqSim dependency to your Maven or Gradle build (for Maven,
   use `pom.xml` with groupId `com.github.equinor` and artifactId
   `neqsim` from Maven Central).
2. Ensure you have the same component names and units used in the
   notebooks (mole fractions, bar, and Kelvin unless noted).
3. Enable a database connection when you need accurate equation of state
   parameters, mimicking the `fluid = fluid('srk')` cell in Colab.

```java
SystemSrkEos fluid = new SystemSrkEos(288.15, 100.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("n-hexane", 0.02);
fluid.createDatabase();
fluid.setMixingRule(2); // classic SRK as in the PVT notebooks
```

## Mapping common notebooks to Java

### PVT and flash notebooks

The PVT notebooks (e.g., `notebooks/PVT`) typically run TP or PT flashes
followed by property extraction. In Java, use `ThermodynamicOperations`
for the flashes and retrieve phase data from the `SystemInterface`.

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

System.out.println("Z-factors: " + Arrays.toString(fluid.getZ()));
System.out.println("Phase fractions: " + Arrays.toString(fluid.getPhaseFraction()));
System.out.println("GOR (Sm3/Sm3): " + fluid.getGOR());
```

For constant-volume or differential liberation sequences demonstrated in
Colab, iterate flashes while updating pressure or removing produced
vapour, mirroring the loop constructs in the notebooks.

### LNG, dehydration, and membranes

Process notebooks under `notebooks/LNG` and `notebooks/AI` connect
streams to unit operations like heat exchangers, expanders, membranes,
and glycol dehydrators. Build the same flows in Java using
`ProcessSystem` and the corresponding unit classes.

```java
ProcessSystem process = new ProcessSystem();
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(1.0, "MSm3/day");

Heater chiller = new Heater("pre-cooler", feed);
chiller.setOutTemperature(248.15);

ThrottlingValve expander = new ThrottlingValve("expander", chiller.getOutletStream());
expander.setOutletPressure(5.0); // bar

Separator coldSeparator = new Separator("cold separator", expander.getOutletStream());

process.add(feed);
process.add(chiller);
process.add(expander);
process.add(coldSeparator);
process.run();
```

Use `getOutletStream()` from each unit to pass streams to downstream
operations. For dehydration, connect `Separator` gas outlets to
`GlycolDehydrationlModule` and set specifications just as the notebook
cells set target water content.

### Dynamic and digital-twin notebooks

The Industry 4.0 notebooks in `notebooks/AI` stream dynamic results to
plots. In Java, enable dynamics by switching the `ProcessSystem` to
transient mode and stepping the solver while logging sensor variables.

```java
process.setTimeStep(0.5); // hours
process.setMaxNumberOfTimeSteps(200);
process.runTransient();

double[] pressureTrace = expander.getOutletStream().getPressureProfile();
```

Attach PID controllers (`ControllerDevice`) to match the control loops in
those notebooks—for instance, controlling separator pressure via valve
opening or maintaining dew point via coolant temperature.

### Exporting results for notebooks

When you want to feed Java results back into a notebook (for validation
or training), export stream tables as CSV or JSON. The Colab notebooks
usually turn `pandas` DataFrames into charts; in Java, you can write the
same tables using standard libraries.

```java
try (PrintWriter writer = new PrintWriter("cold-separator-summary.csv")) {
    writer.println("step,pressure_bar,gas_rate_kgph,liquid_rate_kgph");
    for (int step = 0; step < pressureTrace.length; step++) {
        double p = coldSeparator.getGasOutStream().getPressureProfile()[step];
        double gas = coldSeparator.getGasOutStream().getFlowRateProfile("kg/hr")[step];
        double liq = coldSeparator.getLiquidOutStream().getFlowRateProfile("kg/hr")[step];
        writer.printf("%d,%.3f,%.3f,%.3f%n", step, p, gas, liq);
    }
}
```

You can then load these CSVs in Colab with `pandas.read_csv` to compare
Java transient trajectories with the notebook runs.

## Tips for staying aligned with the notebooks

* Use the same unit systems shown in the cells (usually SI). The
  `setTemperature`/`setPressure` methods accept unit strings identical to
  the notebook helpers.
* Keep the same mixing rules and volume shift settings to reproduce
  liquid yields, Wobbe indices, and dew-point calculations from the
  Colab examples.
* For LPG and LNG cases, ensure low-temperature property packages (CPA or
  SRK with volume correction) match the selections called out in the
  notebook markdown cells.
* Dynamic notebooks often ramp valve openings or compressor speeds—mirror
  these with `setOpening` or `setCompressorSpeed` in a timestep loop for
  close alignment.

For additional notebook context and datasets, browse the
[NeqSim-Colab repository](https://github.com/EvenSol/NeqSim-Colab) and
open the relevant `.ipynb` files next to this Java guide.
