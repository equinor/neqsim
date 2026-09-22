---
title: Process Recipes
description: Quick recipes for process simulation in NeqSim - separators, compressors, heat exchangers, flowsheets, and more.
---

Use the complete Java reference process below when you need an independently executable screen.
The Python sections are ordered session fragments: run the first recipe to create the shared `feed`
stream and `process` model, then run later fragments in the same session. They are not independent
programs; fragments that need additional streams state those prerequisites explicitly.

## Executable Reference Process

This Java 8 example separates a rich gas, compresses the gas outlet, and cools the discharge. It
checks separator mass closure, the pressure and temperature specifications, compressor power, and
cooler duty. The example is a process-screening route, not vendor selection, mechanical design,
relief design, an operability proof, or final facility design. Inspect the phase and equipment state
and validate the thermodynamic model, binary interaction parameters, compressor chart, and thermal
and mechanical limits for the intended service.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class ProcessCookbookScreen {
  private static final Logger logger = LogManager.getLogger(ProcessCookbookScreen.class);

  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.10);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    Separator separator = new Separator("HP separator", feed);

    Compressor compressor = new Compressor("Gas compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setIsentropicEfficiency(0.75);

    Cooler cooler = new Cooler("Aftercooler", compressor.getOutletStream());
    cooler.setOutletTemperature(40.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);
    process.run();

    double feedMass = feed.getFlowRate("kg/hr");
    double gasMass = separator.getGasOutStream().getFlowRate("kg/hr");
    double liquidMass = separator.getLiquidOutStream().getFlowRate("kg/hr");
    double massBalanceError = Math.abs(feedMass - gasMass - liquidMass) / feedMass;
    double powerKW = compressor.getPower("kW");
    double dischargePressure = compressor.getOutletStream().getPressure("bara");
    double cooledTemperature = cooler.getOutletStream().getTemperature("C");
    double coolerDutyW = cooler.getDuty();

    assert feedMass > 0.0;
    assert gasMass > 0.0;
    assert liquidMass >= 0.0;
    assert massBalanceError < 1.0e-6;
    assert Double.isFinite(powerKW) && powerKW > 0.0;
    assert Math.abs(dischargePressure - 80.0) < 1.0e-6;
    assert Math.abs(cooledTemperature - 40.0) < 1.0e-6;
    assert Double.isFinite(coolerDutyW) && Math.abs(coolerDutyW) > 1.0e-6;

    logger.info(
        "feed={} kg/hr, gas={} kg/hr, liquid={} kg/hr, power={} kW, cooler duty={} W",
        feedMass, gasMass, liquidMass, powerKW, coolerDutyW);
  }
}
```

Run with assertions enabled (`-ea`). The repository documentation contract extracts this exact
fence, compiles it with the Java 8 language target, and executes it.

## Table of Contents

- [Executable Reference Process](#executable-reference-process)
- [Streams](#streams)
- [Separators](#separators)
- [Compressors and Expanders](#compressors-and-expanders)
- [Heat Exchangers](#heat-exchangers)
- [Valves](#valves)
- [Flowsheet Building](#flowsheet-building)
- [Recycles and Adjusters](#recycles-and-adjusters)

---

## Streams

### Create a Feed Stream and Process

```python
from neqsim import jneqsim

ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream

# Create fluid
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 30.0, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

# Create the shared feed and process model
feed = Stream("Feed", fluid)
feed.setFlowRate(10000.0, "kg/hr")
process = ProcessSystem()
process.add(feed)

process.run()
print(f"Molar flow: {feed.getFlowRate('mol/hr'):.0f} mol/hr")
```

### Set Stream Conditions

```python
feed.setTemperature(30.0, "C")
feed.setPressure(50.0, "bara")
feed.setFlowRate(5000.0, "kg/hr")

# Alternative units
feed.setTemperature(86.0, "F")
feed.setPressure(725.0, "psia")
feed.setFlowRate(10.0, "MSm3/day")
```

---

## Separators

### Two-Phase Separator

```python
Separator = jneqsim.process.equipment.separator.Separator

separator = Separator("HP Separator", feed)
separator.setInternalDiameter(2.0)  # m, optional
process.add(separator)
process.run()

gas_out = separator.getGasOutStream()
liquid_out = separator.getLiquidOutStream()
print(f"Gas: {gas_out.getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Liquid: {liquid_out.getFlowRate('kg/hr'):.0f} kg/hr")
```

### Three-Phase Separator

```python
ThreePhaseSeparator = jneqsim.process.equipment.separator.ThreePhaseSeparator

separator = ThreePhaseSeparator("3-Phase Sep", feed)
process.add(separator)
process.run()

gas_out = separator.getGasOutStream()
oil_out = separator.getOilOutStream()
water_out = separator.getWaterOutStream()
```

---

## Compressors and Expanders

### Compressor with Efficiency

```python
Compressor = jneqsim.process.equipment.compressor.Compressor

compressor = Compressor("K-100", feed)
compressor.setOutletPressure(100.0, "bara")
compressor.setIsentropicEfficiency(0.75)
process.add(compressor)
process.run()

print(f"Power: {compressor.getPower('kW'):.1f} kW")
print(f"Outlet T: {compressor.getOutletStream().getTemperature('C'):.1f} °C")
```

The preceding example uses an isentropic efficiency. To use a polytropic efficiency instead,
enable the polytropic calculation explicitly before running the process:

```python
compressor.setUsePolytropicCalc(True)
compressor.setPolytropicEfficiency(0.80)
process.run()

print(f"Head: {compressor.getPolytropicHead('kJ/kg'):.1f} kJ/kg")
```

### Multi-Stage Compression with Intercooling

```python
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler

comp1 = Compressor("K-100A", feed)
comp1.setOutletPressure(70.0, "bara")
comp1.setIsentropicEfficiency(0.75)
process.add(comp1)

cooler1 = Cooler("E-100", comp1.getOutletStream())
cooler1.setOutletTemperature(40.0, "C")
process.add(cooler1)

comp2 = Compressor("K-100B", cooler1.getOutletStream())
comp2.setOutletPressure(100.0, "bara")
comp2.setIsentropicEfficiency(0.75)
process.add(comp2)

process.run()
total_power = comp1.getPower("kW") + comp2.getPower("kW")
print(f"Total power: {total_power:.1f} kW")
```

### Expander

```python
Expander = jneqsim.process.equipment.expander.Expander

expander = Expander("Turbo-Expander", feed)
expander.setOutletPressure(20.0, "bara")
expander.setIsentropicEfficiency(0.85)
process.add(expander)
process.run()

print(f"Power generated: {-expander.getPower('kW'):.1f} kW")
print(f"Outlet T: {expander.getOutletStream().getTemperature('C'):.1f} °C")
```

---

## Heat Exchangers

### Heater (Specified Outlet Temperature)

```python
Heater = jneqsim.process.equipment.heatexchanger.Heater

heater = Heater("E-100", feed)
heater.setOutletTemperature(80.0, "C")
process.add(heater)
process.run()

print(f"Duty: {heater.getDuty() / 1000.0:.1f} kW")
```

### Cooler (Specified Outlet Temperature)

```python
Cooler = jneqsim.process.equipment.heatexchanger.Cooler

cooler = Cooler("E-101", feed)
cooler.setOutletTemperature(30.0, "C")
process.add(cooler)
process.run()

print(f"Cooling duty: {cooler.getDuty() / 1000.0:.1f} kW")
```

### Two-Stream Heat Exchanger

This example creates both required inlet streams from the shared feed.

```python
HeatExchanger = jneqsim.process.equipment.heatexchanger.HeatExchanger

hot_stream = feed.clone("Hot feed")
hot_stream.setTemperature(120.0, "C")
cold_stream = feed.clone("Cold feed")
cold_stream.setTemperature(20.0, "C")

hx = HeatExchanger("E-102", hot_stream, cold_stream)
hx.setUAvalue(5000.0)  # W/K
process.add(hot_stream)
process.add(cold_stream)
process.add(hx)
process.run()

print(f"Duty: {hx.getDuty() / 1000.0:.1f} kW")
print(f"Hot out T: {hx.getOutStream(0).getTemperature('C'):.1f} °C")
print(f"Cold out T: {hx.getOutStream(1).getTemperature('C'):.1f} °C")
```

---

## Valves

### Throttling Valve (JT Valve)

```python
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve

valve = ThrottlingValve("VLV-100", feed)
valve.setOutletPressure(20.0, "bara")
process.add(valve)
process.run()

delta_temperature = valve.getOutletStream().getTemperature("K") - feed.getTemperature("K")
print(f"Temperature change: {delta_temperature:.1f} K")
```

### Control Valve with Cv

```python
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve

valve = ThrottlingValve("CV-100", feed)
valve.setOutletPressure(30.0, "bara")
valve.setCv(100.0)
valve.setPercentValveOpening(50.0)
process.add(valve)
process.run()
```

### Valve Sizing (Cv/Kv Calculation)

Calculate the required Cv/Kv for a control valve:

```python
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve

valve = ThrottlingValve("PCV-100", feed)
valve.setOutletPressure(25.0, "bara")
valve.setPercentValveOpening(100.0)

# Supported standards/correlations: "default", "IEC 60534", "IEC 60534 full",
# "prod choke", "Sachdeva", "Gilbert", "Baxendell", "Ros", and "Achong".
mech_design = valve.getMechanicalDesign()
mech_design.setValveSizingStandard("IEC 60534")
mech_design.getValveSizingMethod().setxT(0.75)

process.add(valve)
process.run()
valve.calcKv()
print(f"Cv = {valve.getCv():.2f}")
print(f"Kv = {valve.getKv():.2f}")
```

See [Valve Mechanical Design](../process/ValveMechanicalDesign.md) for full details on the
available sizing standards, parameters, and formulas.

### Choke Collapse Diagnostic

Detect loss of critical (sonic) flow across a throttling valve or choke and flag flashing or
cavitation in liquid service. See [Choke Collapse Analysis](../process/choke-collapse.md) for the
full theory.

```python
ChokeCollapseAnalyzer = jneqsim.process.equipment.valve.ChokeCollapseAnalyzer

# Run this after the valve recipe above.
result = valve.analyseChokeCollapse()
print("Flow regime:", result.getFlowRegime())
print("Collapse:", result.getCollapseMode())
print("Pressure ratio:", result.getPressureRatio())
print("Critical ratio:", result.getCriticalPressureRatio())
print("Margin:", result.getMarginToCollapse())

analyzer = ChokeCollapseAnalyzer(valve)
analyzer.setCriticalMarginThreshold(0.05)
analyzer.setDownstreamPressure(80.0, "bara")
print(analyzer.analyze().toJson())
```

### Inadvertent Valve Operation (IVO) Screening

Screen credible inadvertent open, close, or stuck scenarios per API 521 section 4.4.13 and
NORSOK P-002 section 5.5. See
[Inadvertent Valve Operation](../process/inadvertent-valve-operation.md) for the full scenario
taxonomy and severity rules.

```python
IvoResult = jneqsim.process.equipment.valve.InadvertentValveOperationResult

# Run this after the valve recipe above.
result = valve.analyseInadvertentOperation(
    IvoResult.ValveRole.BLOCK,
    IvoResult.IvoMode.SPURIOUS_CLOSE,
    100.0,  # Downstream segment design pressure, bara
)
print("Severity:", result.getSeverity())
print("Overpressure factor:", result.getOverpressureFactor())
print("Blocked outlet:", result.isBlockedOutlet())
print("Reverse-flow risk:", result.isReverseFlowRisk())
```

---

## Flowsheet Building

### Complete Simple Process

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler

fluid = SystemSrkEos(273.15 + 50.0, 30.0)
fluid.addComponent("methane", 0.70)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.10)
fluid.addComponent("n-butane", 0.05)
fluid.addComponent("n-pentane", 0.05)
fluid.setMixingRule("classic")

process = ProcessSystem()
feed = Stream("Feed", fluid)
feed.setFlowRate(50000.0, "kg/hr")
process.add(feed)

hp_separator = Separator("HP Sep", feed)
process.add(hp_separator)

compressor = Compressor("Compressor", hp_separator.getGasOutStream())
compressor.setOutletPressure(80.0, "bara")
compressor.setIsentropicEfficiency(0.75)
process.add(compressor)

cooler = Cooler("Aftercooler", compressor.getOutletStream())
cooler.setOutletTemperature(40.0, "C")
process.add(cooler)

process.run()
print(f"Feed: {feed.getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Gas: {hp_separator.getGasOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Liquid: {hp_separator.getLiquidOutStream().getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Compressor power: {compressor.getPower('kW'):.1f} kW")
print(f"Cooler duty: {cooler.getDuty() / 1000.0:.1f} kW")
```

---

## Recycles and Adjusters

### Recycle Stream

`Recycle.setOutletStream(...)` defines the tear-stream result. Connect that outlet to the
downstream mixer or equipment that closes the loop; it is not itself the destination mixer.
The following fragment assumes that the two named streams already exist in a larger flowsheet.

```python
Recycle = jneqsim.process.equipment.util.Recycle

recycle = Recycle("Recycle")
recycle.addStream(recycle_source_stream)
recycle.setOutletStream(recycle_tear_stream)
recycle.setTolerance(1.0e-6)
process.add(recycle)

# A downstream mixer should consume recycle.getOutletStream() to close the loop.
process.run()
```

### Adjuster (Spec Controller)

The generic `Adjuster` changes a stream property. This example varies the shared feed mass flow
to meet an actual gas-volume-flow target at a cooler outlet. Use the functional getter/setter
overloads when the manipulated variable must call an equipment-specific setter.

```python
Adjuster = jneqsim.process.equipment.util.Adjuster
Cooler = jneqsim.process.equipment.heatexchanger.Cooler

cooler = Cooler("Adjuster target cooler", feed)
cooler.setOutletTemperature(30.0, "C")
process.add(cooler)

adjuster = Adjuster("Adjust feed flow")
adjuster.setAdjustedVariable(feed, "flow", "kg/hr")
adjuster.setTargetVariable(cooler.getOutletStream(), "gasVolumeFlow", 5000.0, "Am3/hr")
adjuster.setMinAdjustedValue(1.0)
adjuster.setMaxAdjustedValue(100000.0)
adjuster.setTolerance(1.0e-4)
process.add(adjuster)

process.run()
print(f"Adjusted feed flow: {feed.getFlowRate('kg/hr'):.2f} kg/hr")
```

---

## See Also

- **[Process Equipment Documentation](../process/equipment/README.md)** - All equipment types
- **[Optimization Guide](../process/optimization/README.md)** - Process optimization
- **[JavaDoc: ProcessSystem](https://equinor.github.io/neqsim/javadoc/neqsim/process/processmodel/ProcessSystem.html)** - Complete API
