package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.Condenser;
import neqsim.process.equipment.distillation.Reboiler;
import neqsim.process.equipment.electrolyzer.CO2Electrolyzer;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.powergeneration.CombinedCycleSystem;
import neqsim.process.equipment.powergeneration.FuelCell;
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.powergeneration.SolarPanel;
import neqsim.process.equipment.powergeneration.SteamTurbine;
import neqsim.process.equipment.powergeneration.WindFarm;
import neqsim.process.equipment.powergeneration.WindTurbine;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineUnit;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reactor.AmmoniaSynthesisReactor;
import neqsim.process.equipment.reactor.StirredTankReactor;
import neqsim.process.equipment.solidhandling.BioFeedstockPreparation;

class EnergyPortCoverageTest {

  @Test
  void testRotatingEquipmentPorts() {
    assertPort(new Pump("pump"), "shaftPower", EnergyType.SHAFT_WORK, EnergyPortDirection.INPUT,
        EnergyPortMode.CALCULATED);
    assertPort(new Compressor("compressor"), "shaftPower", EnergyType.SHAFT_WORK, EnergyPortDirection.INPUT,
        EnergyPortMode.CALCULATED);
    assertPort(new Expander("expander"), "shaftPower", EnergyType.SHAFT_WORK, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    assertPort(new SteamTurbine("steam turbine"), "shaftPower", EnergyType.SHAFT_WORK, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    assertPort(new GasTurbine("gas turbine"), "shaftPower", EnergyType.SHAFT_WORK, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    assertPort(new GasTurbineUnit("catalogue gas turbine"), "shaftPower", EnergyType.SHAFT_WORK,
        EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
  }

  @Test
  void testThermalEquipmentPorts() {
    assertPort(new Heater("heater"), "heatDuty", EnergyType.HEAT, EnergyPortDirection.BIDIRECTIONAL,
        EnergyPortMode.CALCULATED);
    assertPort(new Cooler("cooler"), "heatDuty", EnergyType.HEAT, EnergyPortDirection.BIDIRECTIONAL,
        EnergyPortMode.CALCULATED);
    assertPort(new Condenser("condenser"), "heatDuty", EnergyType.HEAT, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    assertPort(new Reboiler("reboiler"), "heatDuty", EnergyType.HEAT, EnergyPortDirection.INPUT,
        EnergyPortMode.CALCULATED);
    assertPort(new AmmoniaSynthesisReactor("ammonia reactor"), "reactionHeat", EnergyType.HEAT,
        EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
    StirredTankReactor reactor = new StirredTankReactor("CSTR");
    assertPort(reactor, "heatDuty", EnergyType.HEAT, EnergyPortDirection.BIDIRECTIONAL, EnergyPortMode.CALCULATED);
    assertPort(reactor, "agitatorPower", EnergyType.ELECTRICAL, EnergyPortDirection.INPUT, EnergyPortMode.CALCULATED);
    assertPort(new GasTurbine("gas turbine"), "exhaustHeat", EnergyType.HEAT, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
  }

  @Test
  void testElectricalEquipmentPorts() {
    ProcessEquipmentBaseClass[] generators = { new SolarPanel("solar"), new WindTurbine("wind turbine"),
        new WindFarm("wind farm"), new FuelCell("fuel cell"), new CombinedCycleSystem("combined cycle") };
    for (ProcessEquipmentBaseClass generator : generators) {
      assertPort(generator, "electricalPower", EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT,
          EnergyPortMode.CALCULATED);
    }

    assertPort(new BatteryStorage("battery"), "electricalPower", EnergyType.ELECTRICAL,
        EnergyPortDirection.BIDIRECTIONAL, EnergyPortMode.CALCULATED);
    assertPort(new Electrolyzer("electrolyzer"), "electricalPower", EnergyType.ELECTRICAL, EnergyPortDirection.INPUT,
        EnergyPortMode.CALCULATED);
    assertPort(new CO2Electrolyzer("CO2 electrolyzer"), "electricalPower", EnergyType.ELECTRICAL,
        EnergyPortDirection.INPUT, EnergyPortMode.CALCULATED);
    assertPort(new BioFeedstockPreparation("feed preparation"), "electricalPower", EnergyType.ELECTRICAL,
        EnergyPortDirection.INPUT, EnergyPortMode.CALCULATED);
  }

  private static void assertPort(ProcessEquipmentBaseClass equipment, String portName, EnergyType type,
      EnergyPortDirection direction, EnergyPortMode mode) {
    EnergyPort port = equipment.getEnergyPort(portName);
    assertNotNull(port);
    assertEquals(type, port.getEnergyType());
    assertEquals(direction, port.getDirection());
    assertEquals(mode, port.getMode());
    if (port.isConnected()) {
      assertEquals(type, port.getEnergyStream().getEnergyType());
    }
  }
}
