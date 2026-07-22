---
title: "Air cooler unit operation"
description: "The `AirCooler` is a simple process unit for estimating the amount of cooling air needed when a process stream is cooled by ambient air. The calculation makes use of the humid air utility in NeqSim to..."
---

# Air cooler unit operation

The `AirCooler` is a simple process unit for estimating the amount of cooling air needed when a process stream is cooled by ambient air. The calculation makes use of the humid air utility in NeqSim to evaluate the enthalpy rise of the air between the inlet and outlet temperature.

For a given inlet temperature, outlet temperature and relative humidity of the air, the mass flow of dry air is obtained from

$
\dot m_{air} = \frac{Q}{h_{out}-h_{in}}
$

where `Q` is the heat removed from the process stream in watt and `h_{in}` and `h_{out}` are the specific humid–air enthalpies in kJ per kg dry air. The volumetric flow is calculated from the ideal–gas relation at the inlet conditions.

Basic usage:

```java
AirCooler cooler = new AirCooler("air cooler", stream);
cooler.setOutTemperature(40.0, "C");
cooler.setAirInletTemperature(20.0, "C");
cooler.setAirOutletTemperature(30.0, "C");
cooler.setRelativeHumidity(0.5);
```
