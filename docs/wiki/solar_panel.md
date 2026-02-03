---
title: "Solar Panel Unit Operation"
description: "The `SolarPanel` unit converts solar irradiance into electrical power using a"
---

# Solar Panel Unit Operation

The `SolarPanel` unit converts solar irradiance into electrical power using a
simple relation between the incoming radiation, panel area and efficiency:

```
Power = irradiance [W/m^2] × panel area [m^2] × efficiency
```

The produced power is available from the unit's energy stream as a negative duty
(indicating power generation).

## Example

```java
SolarPanel panel = new SolarPanel("panel");
panel.setIrradiance(800.0); // W/m^2
panel.setPanelArea(2.0);    // m^2
panel.setEfficiency(0.2);   // 20%
panel.run();
System.out.println(panel.getPower());
```
