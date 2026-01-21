package neqsim.process.mechanicaldesign.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.UtilityStreamSpecification;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for heater/cooler mechanical design sizing using utility specifications. */
public class HeaterCoolerMechanicalDesignTest {
  SystemInterface system = new SystemSrkEos(273.15 + temperatureC,
      50.0);system.addComponent("methane",120.0);system.addComponent("ethane",20.0);system.addComponent("n-heptane",2.0);system.createDatabase(true);system.setMixingRule(2);new ThermodynamicOperations(system).TPflash();return system;
  }

  @Test
  void testHeaterMechanicalDesignWithUtilitySpecification() {
    SystemInterface system = createBaseSystem(40.0);
    Stream feed = new Stream("heater feed", system);
    feed.setTemperature(40.0, "C");
    feed.setFlowRate(1200.0, "kg/hr");

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(80.0, "C");

    UtilityStreamSpecification spec = new UtilityStreamSpecification();
    spec.setSupplyTemperature(180.0, "C");
    spec.setReturnTemperature(160.0, "C");
    spec.setOverallHeatTransferCoefficient(650.0);
    heater.setUtilitySpecification(spec);

    ProcessSystem systemModel = new ProcessSystem();
    systemModel.add(feed);
    systemModel.add(heater);
    systemModel.run();

    heater.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = heater.getMechanicalDesign();
    design.calcDesign();

    double duty = Math.abs(heater.getDuty());
    double coldIn = heater.getInletStream().getTemperature();
    double coldOut = heater.getOutletStream().getTemperature();
    double hotIn = spec.getSupplyTemperature();
    double hotOut = spec.getReturnTemperature();

    double deltaT1 = Math.abs(hotIn - coldOut);
    double deltaT2 = Math.abs(hotOut - coldIn);
    double expectedLmtd = Math.abs((deltaT1 - deltaT2) / Math.log(deltaT1 / deltaT2));
    double expectedApproach = Math.min(deltaT1, deltaT2);
    double expectedUa = duty / expectedLmtd;

    assertEquals(expectedLmtd, design.getLogMeanTemperatureDifference(), expectedLmtd * 0.05);
    assertEquals(expectedApproach, design.getApproachTemperature(), expectedApproach * 0.05);
    assertEquals(expectedUa, design.getCalculatedUA(), expectedUa * 0.05);
    assertEquals(spec.getOverallHeatTransferCoefficient(),
        design.getUsedOverallHeatTransferCoefficient(), 1e-9);
  }

  @Test
  void testCoolerMechanicalDesignWithHeatCapacitySpecification() {
    SystemInterface system = createBaseSystem(60.0);
    Stream feed = new Stream("cooler feed", system);
    feed.setTemperature(60.0, "C");
    feed.setFlowRate(900.0, "kg/hr");

    Cooler cooler = new Cooler("cooler", feed);
    cooler.setOutTemperature(30.0, "C");

    cooler.setUtilitySupplyTemperature(10.0, "C");
    cooler.setUtilityHeatCapacityRate(4200.0);

    ProcessSystem systemModel = new ProcessSystem();
    systemModel.add(feed);
    systemModel.add(cooler);
    systemModel.run();

    cooler.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = cooler.getMechanicalDesign();
    design.calcDesign();

    assertTrue(design.getCalculatedUA() > 0.0);
    assertTrue(design.getLogMeanTemperatureDifference() > 0.0);

    double duty = Math.abs(cooler.getDuty());
    double utilitySupply = cooler.getUtilitySpecification().getSupplyTemperature();
    double utilityReturn =
        utilitySupply + duty / cooler.getUtilitySpecification().getHeatCapacityRate();
    double deltaT1 = Math.abs(cooler.getInletStream().getTemperature() - utilityReturn);
    double deltaT2 = Math.abs(cooler.getOutletStream().getTemperature() - utilitySupply);
    double expectedApproach = Math.min(deltaT1, deltaT2);

    assertEquals(expectedApproach, design.getApproachTemperature(), expectedApproach * 0.1 + 1e-6);
  }
}
