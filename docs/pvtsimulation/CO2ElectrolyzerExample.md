---
title: "CO₂ Electrolyzer Executable Example"
description: "Executable Java 8 example for CO2 conversion, selectivity, electrical duty, battery discharge, and downstream gas separation screening."
---

This example couples a `CO2Electrolyzer` to a pre-charged battery and a
downstream equilibrium separator. It is a transparent screening calculation:
the caller supplies conversion, product selectivities, Faradaic efficiencies,
cell voltage, and current efficiency.

## Engineering basis

- Feed rates are in mol/s, temperature is in K, and pressure is absolute bara.
- Electrical duty and delivered power are in W.
- `BatteryStorage` capacity and state of charge are in Wh; its discharge
  duration is in hours. The example requests one second of full-duty power.
- Product selectivities are moles of product per mole of converted CO₂.
- The gas separator performs an equilibrium phase split after the simplified
  selectivity calculation. It does not model electrolyzer product purification.

## Executable Java workflow

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.electrolyzer.CO2Electrolyzer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.Fluid;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

public final class CO2ElectrolyzerQuickStart {
  private static final Logger logger =
      LogManager.getLogger(CO2ElectrolyzerQuickStart.class);

  private CO2ElectrolyzerQuickStart() {}

  public static void main(String[] args) {
    double feedTemperatureK = 298.15;
    double feedPressureBara = 20.0;
    double co2FeedMolesPerSecond = 0.95;
    double waterFeedMolesPerSecond = 0.05;
    double conversion = 0.55;
    double coSelectivity = 0.70;
    double hydrogenSelectivity = 0.30;

    SystemInterface feedFluid =
        new Fluid()
            .create2(
                new String[] {"CO2", "water"},
                new double[] {co2FeedMolesPerSecond, waterFeedMolesPerSecond},
                "mole/sec");
    feedFluid.setTemperature(feedTemperatureK);
    feedFluid.setPressure(feedPressureBara);

    Stream feed = new Stream("CO2 feed", feedFluid);
    feed.setTemperature(feedTemperatureK, "K");
    feed.setPressure(feedPressureBara, "bara");

    CO2Electrolyzer electrolyzer = new CO2Electrolyzer("CO2 electrolyzer", feed);
    electrolyzer.setCO2Conversion(conversion);
    electrolyzer.setGasProductSelectivity("CO", coSelectivity);
    electrolyzer.setGasProductSelectivity("H2", hydrogenSelectivity);
    electrolyzer.setProductFaradaicEfficiency("CO", 0.90);
    electrolyzer.setProductFaradaicEfficiency("H2", 1.00);
    electrolyzer.setElectronsPerMoleProduct("H2", 2.0);
    electrolyzer.setCellVoltage(2.70);
    electrolyzer.setCurrentEfficiency(0.95);

    Separator gasPolisher =
        new Separator("syngas equilibrium polisher", electrolyzer.getGasProductStream());
    ProcessSystem process = new ProcessSystem("CO2 conversion screen");
    process.add(feed);
    process.add(electrolyzer);
    process.add(gasPolisher);
    process.run();

    SystemInterface gasProduct = electrolyzer.getGasProductStream().getThermoSystem();
    String co2Name = ComponentInterface.getComponentNameFromAlias("CO2");
    String coName = ComponentInterface.getComponentNameFromAlias("CO");
    String hydrogenName = ComponentInterface.getComponentNameFromAlias("H2");
    double unreactedCo2MolesPerSecond =
        gasProduct.getComponent(co2Name).getFlowRate("mole/sec");
    double coProductMolesPerSecond =
        gasProduct.getComponent(coName).getFlowRate("mole/sec");
    double hydrogenProductMolesPerSecond =
        gasProduct.getComponent(hydrogenName).getFlowRate("mole/sec");

    double expectedConvertedCo2 = co2FeedMolesPerSecond * conversion;
    assert Math.abs(
            unreactedCo2MolesPerSecond - (co2FeedMolesPerSecond - expectedConvertedCo2))
        < 1.0e-8;
    assert Math.abs(coProductMolesPerSecond - expectedConvertedCo2 * coSelectivity)
        < 1.0e-8;
    assert Math.abs(
            hydrogenProductMolesPerSecond - expectedConvertedCo2 * hydrogenSelectivity)
        < 1.0e-8;

    double electricalDutyW = electrolyzer.getEnergyStream().getDuty();
    assert Double.isFinite(electricalDutyW) && electricalDutyW > 0.0;

    double batteryCapacityWh = 5.0e8;
    BatteryStorage battery = new BatteryStorage("renewable battery", batteryCapacityWh);
    battery.setStateOfCharge(batteryCapacityWh);
    double initialStoredEnergyWh = battery.getStateOfCharge();
    double deliveredPowerW = battery.discharge(electricalDutyW, 1.0 / 3600.0);
    assert Math.abs(deliveredPowerW - electricalDutyW)
        < Math.max(1.0, electricalDutyW) * 1.0e-10;
    assert battery.getStateOfCharge() < initialStoredEnergyWh;

    assert gasPolisher.getGasOutStream().getFlowRate("mole/sec") > 0.0;
    assert Math.abs(gasPolisher.getGasOutStream().getPressure("bara") - feedPressureBara)
        < 1.0e-8;

    logger.info(
        "CO2 conversion={} mol/mol, CO={} mol/s, H2={} mol/s, duty={} W, "
            + "one-second battery delivery={} W",
        conversion,
        coProductMolesPerSecond,
        hydrogenProductMolesPerSecond,
        electricalDutyW,
        deliveredPowerW);
  }
}
```

Run the program with assertions enabled (`java -ea`). The exact published fence
is compiled for Java 8 and executed by `CO2ElectrolyzerTest`.

## Qualification boundary

The selectivity route is a caller-parameterized material and electrical-duty
screen. It does not close an electrochemical oxygen balance, predict kinetics,
polarization, membrane transport, degradation, heat rejection, gas purity, or
stack scale-up. The one-second battery call demonstrates the W/Wh/hour API; it
does not qualify dispatch, power electronics, ramping, reserve, or cycle life.
The equilibrium separator is not a product-purification design. Validate
selectivity, Faradaic efficiency, cell voltage, current efficiency, separation,
utility integration, and safety against project data and independent methods
before design or commercial use, with accountable engineering review.

