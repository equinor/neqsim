# Water cooler

The `WaterCooler` equipment cools water streams using the dedicated water physical
property package. It also estimates the required cooling water flow rate using
the **IAPWS IF97** steam tables. Both the inlet and outlet process streams are
forced to use `PhysicalPropertyModel.WATER`.

```java
SystemInterface water = new SystemSrkEos(298.15, 1.0);
water.addComponent("water", 1.0);
water.setPhysicalPropertyModel(PhysicalPropertyModel.WATER);

Stream feed = new Stream("water feed", water);
feed.setTemperature(40.0, "C");

WaterCooler cooler = new WaterCooler("cooler", feed);
cooler.setOutTemperature(20.0, "C");
cooler.setWaterInletTemperature(25.0, "C");
cooler.setWaterOutletTemperature(35.0, "C");
cooler.setWaterPressure(1.0, "bara");

// After running the process system the calculated cooling water flow can be obtained
double cwFlow = cooler.getCoolingWaterFlowRate("kg/hr");
```

