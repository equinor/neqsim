---
title: CO₂ Electrolyzer usage example
description: The snippet below illustrates how to couple the new `CO2Electrolyzer` with a CO₂-rich feed, a
---

# CO₂ Electrolyzer usage example

The snippet below illustrates how to couple the new `CO2Electrolyzer` with a CO₂-rich feed, a
power supply, and a downstream separator in a `ProcessSystem`.

```java
import neqsim.process.equipment.electrolyzer.CO2Electrolyzer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;

SystemInterface feedFluid = new Fluid().create2(
    new String[] {"CO2", "water"},
    new double[] {0.95, 0.05},
    "mole/sec");
feedFluid.setTemperature(298.15);
feedFluid.setPressure(20.0);
Stream feedStream = new Stream("CO2 feed", feedFluid);

CO2Electrolyzer electrolyzer = new CO2Electrolyzer("CO2 electrolyzer", feedStream);
electrolyzer.setCO2Conversion(0.55);
electrolyzer.setGasProductSelectivity("CO", 0.7);
electrolyzer.setGasProductSelectivity("H2", 0.3);
electrolyzer.setProductFaradaicEfficiency("CO", 0.9);
electrolyzer.setElectronsPerMoleProduct("H2", 2.0);

// Downstream separation polishing the gas product
Separator syngasPolisher = new Separator("syngas polisher");
syngasPolisher.setInletStream((StreamInterface) electrolyzer.getGasProductStream());

ProcessSystem process = new ProcessSystem("CO2 to fuels");
process.add(feedStream);
process.add(electrolyzer);
process.add(syngasPolisher);
process.run();

double powerDraw = electrolyzer.getEnergyStream().getDuty();
BatteryStorage battery = new BatteryStorage("renewable battery", 5.0e8);
battery.discharge(powerDraw, 1.0 / 3600.0);
System.out.println("Electrolyzer power demand: " + powerDraw + " W");
```

The gas product stream carries the vapor-phase synthesis gas, the liquid product stream (accessible
through `getLiquidProductStream()`) contains soluble products such as formate or methanol, and the
`EnergyStream` keeps track of the instantaneous electrical duty demanded from the battery.
