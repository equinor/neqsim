package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.Condenser;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.electrolyzer.ElectrolyzerIVCharacteristic;
import neqsim.process.equipment.electrolyzer.ElectrolyzerTechnology;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.powergeneration.SolarPanel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class EnergyNetworkIntegrationTest extends neqsim.NeqSimTest {

  @Test
  void testExpanderShaftDrivesCompressorAndRemainsStableOnRepeatRun() {
    Stream expanderFeed = methaneFeed("expander feed", 100.0, 1.0);
    Expander expander = new Expander("expander", expanderFeed);
    expander.setOutletPressure(50.0);
    expander.setIsentropicEfficiency(0.8);

    Stream compressorFeed = methaneFeed("compressor feed", 10.0, 1.0);
    Compressor compressor = new Compressor("compressor", compressorFeed);
    compressor.setOutletPressure(20.0);
    compressor.setIsentropicEfficiency(0.8);

    MechanicalShaft shaft = new MechanicalShaft("expander compressor shaft");
    expander.connectEnergyStream("shaftPower", shaft, EnergyPortMode.CALCULATED);
    compressor.connectEnergyStream("shaftPower", shaft, EnergyPortMode.SPECIFICATION);

    ProcessSystem process = graphOrderedProcess();
    process.add(compressor);
    process.add(compressorFeed);
    process.add(expander);
    process.add(expanderFeed);
    process.runSequential(UUID.randomUUID());

    double generatedPower = shaft.getContribution("expander.shaftPower");
    double consumedPower = shaft.getContribution("compressor.shaftPower");
    assertTrue(generatedPower > 0.0);
    assertEquals(-generatedPower, consumedPower, Math.max(1.0, generatedPower * 1.0e-10));
    assertEquals(0.0, shaft.getNetPower(), Math.max(1.0, generatedPower * 1.0e-10));

    process.runSequential(UUID.randomUUID());

    assertEquals(generatedPower, shaft.getContribution("expander.shaftPower"), Math.max(1.0, generatedPower * 1.0e-10));
    assertEquals(-generatedPower, shaft.getContribution("compressor.shaftPower"),
        Math.max(1.0, generatedPower * 1.0e-10));
  }

  @Test
  void testSolarBusDrivesElectrolyzerAndRemainsStableOnRepeatRun() {
    SolarPanel solar = new SolarPanel("solar", 1000.0, 5000.0, 0.20);
    Stream waterFeed = waterFeed();
    Electrolyzer electrolyzer = new Electrolyzer("electrolyzer", waterFeed);
    electrolyzer.setTechnology(ElectrolyzerTechnology.PEM);
    electrolyzer.setIVCharacteristic(new ElectrolyzerIVCharacteristic(ElectrolyzerTechnology.PEM));
    electrolyzer.sizeStack(1.0e6);

    EnergyBus bus = new EnergyBus("electrical bus", EnergyType.ELECTRICAL);
    solar.connectEnergyStream("electricalPower", bus, EnergyPortMode.CALCULATED);
    electrolyzer.connectEnergyStream("electricalPower", bus, EnergyPortMode.SPECIFICATION);

    ProcessSystem process = graphOrderedProcess();
    process.add(electrolyzer);
    process.add(waterFeed);
    process.add(solar);
    process.runSequential(UUID.randomUUID());

    double firstStackPower = electrolyzer.getStackPower();
    assertTrue(firstStackPower > 0.0);
    assertTrue(electrolyzer.getHydrogenOutStream().getFlowRate("mole/sec") > 0.0);
    assertEquals(1.0e6, bus.getContribution("solar.electricalPower"), 1.0e-6);
    assertEquals(-firstStackPower, bus.getContribution("electrolyzer.electricalPower"),
        Math.max(1.0, firstStackPower * 1.0e-10));

    process.runSequential(UUID.randomUUID());

    assertEquals(firstStackPower, electrolyzer.getStackPower(), Math.max(1.0, firstStackPower * 1.0e-10));
    assertEquals(-firstStackPower, bus.getContribution("electrolyzer.electricalPower"),
        Math.max(1.0, firstStackPower * 1.0e-10));
  }

  @Test
  void testTwoStreamHeatExchangerPublishesCalculatedRecoveryDuty() {
    SystemInterface hotFluid = new SystemSrkEos(373.15, 20.0);
    hotFluid.addComponent("methane", 1.0);
    hotFluid.setMixingRule("classic");
    Stream hot = new Stream("hot feed", hotFluid);
    hot.setFlowRate(0.2, "MSm3/day");
    hot.run();

    SystemInterface coldFluid = new SystemSrkEos(273.15, 20.0);
    coldFluid.addComponent("methane", 1.0);
    coldFluid.setMixingRule("classic");
    Stream cold = new Stream("cold feed", coldFluid);
    cold.setFlowRate(0.2, "MSm3/day");
    cold.run();

    EnergyBus recovery = new EnergyBus("exchanger recovery", EnergyType.HEAT);
    HeatExchanger exchanger = new HeatExchanger("feed exchanger", hot, cold);
    exchanger.setUAvalue(10000.0);
    exchanger.connectEnergyStream("heatDuty", recovery, EnergyPortMode.CALCULATED);

    exchanger.run();

    assertTrue(exchanger.getDuty() > 0.0);
    assertEquals(exchanger.getDuty(), recovery.getContribution("feed exchanger.heatDuty"),
        Math.max(1.0, exchanger.getDuty() * 1.0e-10));
  }

  @Test
  void testCondenserHeatRecoveryDrivesHeater() {
    EnergyBus heatRecovery = new EnergyBus("heat recovery", EnergyType.HEAT);
    Condenser condenser = new Condenser("condenser");
    condenser.connectEnergyStream("heatDuty", heatRecovery, EnergyPortMode.CALCULATED);
    condenser.getEnergyPort("heatDuty").setDuty(-200.0, "kW");

    Stream heaterFeed = methaneFeed("heater feed", 20.0, 0.1);
    double inletTemperature = heaterFeed.getTemperature("K");
    Heater heater = new Heater("heater", heaterFeed);
    heater.connectEnergyStream("heatDuty", heatRecovery, EnergyPortMode.SPECIFICATION);

    heater.run();
    double firstOutletTemperature = heater.getOutletStream().getTemperature("K");

    assertTrue(firstOutletTemperature > inletTemperature);
    assertEquals(200.0, heatRecovery.getContribution("condenser.heatDuty", "kW"), 1.0e-12);
    assertEquals(-200.0, heatRecovery.getContribution("heater.heatDuty", "kW"), 1.0e-3);
    assertEquals(0.0, heatRecovery.getNetPower("kW"), 1.0e-3);

    heater.run();

    assertEquals(firstOutletTemperature, heater.getOutletStream().getTemperature("K"), 1.0e-6);
    assertEquals(0.0, heatRecovery.getNetPower("kW"), 1.0e-3);
  }

  private static ProcessSystem graphOrderedProcess() {
    ProcessSystem process = new ProcessSystem();
    process.setUseOptimizedExecution(false);
    process.setUseGraphBasedExecution(true);
    return process;
  }

  private static Stream methaneFeed(String name, double pressureBara, double flowMSm3Day) {
    SystemInterface gas = new SystemSrkEos(298.15, pressureBara);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    Stream feed = new Stream(name, gas);
    feed.setPressure(pressureBara, "bara");
    feed.setFlowRate(flowMSm3Day, "MSm3/day");
    feed.run();
    return feed;
  }

  private static Stream waterFeed() {
    SystemInterface water = new Fluid().create("water");
    Stream feed = new Stream("water feed", water);
    feed.setPressure(1.0, "bara");
    feed.setTemperature(353.15, "K");
    feed.setFlowRate(2.0, "mole/sec");
    feed.run();
    return feed;
  }
}
