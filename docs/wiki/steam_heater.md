# Steam heater

The `SteamHeater` heats process streams while forcing the water property package. It estimates the required steam flow rate using the **IAPWS IF97** steam tables.

```java
SystemInterface water = new SystemSrkEos(298.15, 1.0);
water.addComponent("water", 1.0);
water.setPhysicalPropertyModel(PhysicalPropertyModel.WATER);

Stream feed = new Stream("water feed", water);
feed.setTemperature(25.0, "C");

SteamHeater heater = new SteamHeater("heater", feed);
heater.setOutTemperature(80.0, "C");
heater.setSteamInletTemperature(180.0, "C");
heater.setSteamOutletTemperature(100.0, "C");
heater.setSteamPressure(2.0, "bara");

// After running the process system the calculated steam flow can be obtained
double steamFlow = heater.getSteamFlowRate("kg/hr");
```
