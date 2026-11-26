package neqsim.process.equipment.refrigeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.EquipmentFactory;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.CoolingWaterSystem;
import neqsim.process.equipment.refrigeration.RefrigerationSystem.EconomizerType;
import neqsim.process.equipment.refrigeration.RefrigerationSystem.InterstageCooling;
import neqsim.process.equipment.stream.EnergyStream;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.utilitydesign.RefrigerationCycle;
import neqsim.process.util.utilitydesign.UtilitySystemDesigner;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the rigorous multi-level refrigeration system.
 */
class RefrigerationSystemTest {
  /**
   * Builds a single-level system with a fixed 1 MW load.
   *
   * @param refrigerant refrigerant name
   * @param evaporatingC evaporating temperature in C
   * @param condensingC condensing temperature in C
   * @param efficiency isentropic efficiency
   * @return configured system, not yet run
   */
  private static RefrigerationSystem singleLevel(String refrigerant, double evaporatingC, double condensingC,
      double efficiency) {
    RefrigerationSystem system = new RefrigerationSystem(refrigerant + " refrigeration");
    system.setRefrigerant(refrigerant);
    system.setCondensingTemperature(condensingC, "C");
    system.setIsentropicEfficiency(efficiency);
    system.addLevel("LP", evaporatingC, "C").addFixedDuty("load", 1000.0, "kW");
    return system;
  }

  @Test
  void idealR134aCycleMatchesTextbook() {
    // Cengel and Boles, Thermodynamics, ideal vapour-compression cycle with R-134a between 0.14 and 0.8 MPa:
    // h1 = 239.16, h2 = 275.39, h3 = h4 = 95.47 kJ/kg, COP = 3.97. Saturation temperatures are -18.77 and 31.31 C.
    RefrigerationSystem system = singleLevel("R134a", -18.77, 31.31, 1.0);
    system.run();
    assertEquals(1.40, system.getStages().get(0).getSuction().getPressure(), 0.03);
    assertEquals(8.00, system.getCondensingPressure(), 0.10);
    assertEquals(3.97, system.getCOP(), 0.03 * 3.97);
    double specificEffect = 1.0e6 / (system.getRefrigerantCirculation("kg/sec") * 1000.0);
    assertEquals(239.16 - 95.47, specificEffect, 0.03 * (239.16 - 95.47));
  }

  @Test
  void idealAmmoniaCycleMatchesReferenceProperties() {
    // Ammonia -30 C / 35 C with the reference equation of state: refrigerating effect h_g(-30 C) - h_f(35 C)
    // = 1422.5 - 366.1 = 1056.4 kJ/kg (IIR reference state).
    RefrigerationSystem system = singleLevel("ammonia", -30.0, 35.0, 1.0);
    system.run();
    double specificEffect = 1.0e6 / (system.getRefrigerantCirculation("kg/sec") * 1000.0);
    assertEquals(1056.4, specificEffect, 3.0);
    assertEquals(1.194, system.getStages().get(0).getSuction().getPressure(), 0.005);
    assertEquals(13.50, system.getCondensingPressure(), 0.03);
    assertTrue(system.getCOP() > 2.7 && system.getCOP() < 2.95, "ideal ammonia COP " + system.getCOP());
  }

  @Test
  void energyBalanceClosesForEveryConfiguration() {
    for (EconomizerType economizer : EconomizerType.values()) {
      for (InterstageCooling cooling : InterstageCooling.values()) {
        if (cooling == InterstageCooling.FLASH_INTERCOOLER && economizer == EconomizerType.NONE) {
          continue;
        }
        RefrigerationSystem system = singleLevel("propane", -35.0, 40.0, 0.75);
        system.addLevel("MP", -5.0, "C").addFixedDuty("mp", 500.0, "kW");
        system.addLevel("HP", 15.0, "C").addFixedDuty("hp", 300.0, "kW");
        system.setSubcooling(3.0);
        system.setCondenserPressureDrop(0.3);
        system.getLevel("LP").setSuperheat(5.0).setSuctionPressureDrop(0.05);
        system.setEconomizerType(economizer);
        system.setInterstageCooling(cooling);
        system.run();
        assertEquals(1800.0, system.getRefrigerationDuty("kW"), 1.0e-9);
        assertEquals(0.0, system.getEnergyBalanceResidual(), 1.0, economizer + " " + cooling);
        assertEquals(3, system.getStages().size());
        // Mass balance: the top stage carries the whole condenser flow.
        assertEquals(system.getRefrigerantCirculation("kg/sec"), system.getStages().get(0).getMassFlow(), 1.0e-9);
        assertTrue(system.getCarnotFraction() > 0.3 && system.getCarnotFraction() < 1.0);
      }
    }
  }

  @Test
  void flashEconomizerSavesPowerOverSingleStage() {
    RefrigerationSystem single = singleLevel("propane", -40.0, 30.0, 0.75);
    single.run();
    RefrigerationSystem economized = singleLevel("propane", -40.0, 30.0, 0.75);
    RefrigerationLevel economizer = economized.addGeometricMeanEconomizer("economizer");
    economized.run();

    assertEquals(Math.sqrt(single.getCondensingPressure() * single.getStages().get(0).getSuction().getPressure()),
        economizer.getPressure(), 1.0e-3);
    double saving = 1.0 - economized.getCompressorPower() / single.getCompressorPower();
    assertTrue(saving > 0.10 && saving < 0.20, "economizer power saving " + saving);
    assertEquals(0.0, economizer.getDuty(), 0.0);
    assertTrue(economizer.getFlashGasFlow() > 0.0);
    // The low stage only handles the liquid that reaches the cold level.
    assertTrue(economized.getStages().get(1).getMassFlow() < single.getStages().get(0).getMassFlow());
  }

  @Test
  void flashIntercoolerSaturatesHighStageSuctionAndSavesPower() {
    RefrigerationSystem economized = singleLevel("ammonia", -30.0, 35.0, 0.75);
    economized.addGeometricMeanEconomizer("intermediate");
    economized.run();
    RefrigerationSystem intercooled = singleLevel("ammonia", -30.0, 35.0, 0.75);
    RefrigerationLevel intermediate = intercooled.addGeometricMeanEconomizer("intermediate");
    intercooled.setInterstageCooling(InterstageCooling.FLASH_INTERCOOLER);
    intercooled.run();

    RefrigerationStage highStage = intercooled.getStages().get(0);
    assertEquals(intermediate.getTemperature(), highStage.getSuction().getTemperature(), 1.0e-3);
    assertTrue(intercooled.getCompressorPower() < economized.getCompressorPower());
    assertTrue(highStage.getDischarge().getTemperature() < economized.getStages().get(0).getDischarge().getTemperature()
        - 30.0, "ammonia discharge temperature must drop markedly with flash inter-cooling");
    assertEquals(0.0, intercooled.getEnergyBalanceResidual(), 1.0);
  }

  @Test
  void integratesWithProcessCoolersAndFollowsTheFlowsheet() {
    SystemInterface gas = new SystemSrkEos(298.15, 60.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.08);
    gas.addComponent("propane", 0.05);
    gas.addComponent("n-butane", 0.02);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed gas", gas);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(60.0, "bara");
    Cooler warmChiller = new Cooler("warm chiller", feed);
    warmChiller.setOutTemperature(273.15 + 5.0);
    Cooler coldChiller = new Cooler("cold chiller", warmChiller.getOutletStream());
    coldChiller.setOutTemperature(273.15 - 25.0);

    RefrigerationSystem refrigeration = new RefrigerationSystem("propane refrigeration");
    refrigeration.setRefrigerant("propane");
    refrigeration.setCondenserCoolingMedium(20.0, 30.0, 8.0, "C");
    refrigeration.addLevel("MP", 0.0, "C").setTemperatureFromLoads(5.0).addCooler(warmChiller);
    refrigeration.addLevel("LP", -30.0, "C").setTemperatureFromLoads(5.0).addCooler(coldChiller);
    EnergyStream shaft = new EnergyStream("refrigeration shaft");
    refrigeration.connectEnergyStream(RefrigerationSystem.SHAFT_POWER_PORT, shaft);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(warmChiller);
    process.add(coldChiller);
    process.add(refrigeration);
    process.run();

    double chillerDuty = -(warmChiller.getDuty() + coldChiller.getDuty());
    assertTrue(chillerDuty > 0.0);
    assertEquals(chillerDuty, refrigeration.getRefrigerationDuty(), 1.0e-6 * chillerDuty);
    assertEquals(273.15, refrigeration.getLevel("MP").getTemperature(), 1.0e-9);
    assertEquals(273.15 - 30.0, refrigeration.getLevel("LP").getTemperature(), 1.0e-9);
    assertEquals(273.15 + 38.0, refrigeration.getCondensingTemperature(), 1.0e-9);
    assertTrue(refrigeration.getWarnings().isEmpty(), refrigeration.getWarnings().toString());
    assertEquals(refrigeration.getCompressorPower(), shaft.getDuty(), 1.0e-6);
    assertEquals(refrigeration.getCondenserDuty("kW") / (4.186 * 10.0), refrigeration.getCoolingMediumFlow("kg/sec"),
        1.0e-6);
    double firstPower = refrigeration.getCompressorPower();

    // A colder product specification raises the duty and lowers the LP level on the next run.
    coldChiller.setOutTemperature(273.15 - 35.0);
    process.run();
    assertEquals(273.15 - 40.0, refrigeration.getLevel("LP").getTemperature(), 1.0e-9);
    assertTrue(refrigeration.getCompressorPower() > firstPower);

    // The utility designer sees the condenser and the compressor, not the refrigerated chillers.
    UtilitySystemDesigner designer = UtilitySystemDesigner.fromProcessSystem(process);
    designer.design();
    assertEquals(refrigeration.getCondenserDuty("kW"), designer.getTotalCoolingDutyKW(), 1.0e-6);
    assertEquals(refrigeration.getCompressorPower("kW"), designer.getTotalShaftPowerKW(), 1.0e-6);

    // The suction stream feeds a detailed compressor model.
    Stream suction = refrigeration.createSuctionStream(1);
    Compressor compressor = new Compressor("LP refrigerant compressor", suction);
    compressor.setOutletPressure(refrigeration.getLevel("MP").getPressure());
    compressor.setIsentropicEfficiency(0.75);
    compressor.run();
    RefrigerationStage lowStage = refrigeration.getStages().get(1);
    assertEquals(lowStage.getPower(), compressor.getPower(), 0.02 * lowStage.getPower());

    CoolingWaterSystem coolingWater = new CoolingWaterSystem();
    refrigeration.addCondenserTo(coolingWater, 8.0);
    coolingWater.calculate();
    assertEquals(refrigeration.getCondenserDuty("kW"), coolingWater.getTotalDuty(), 1.0e-6);
  }

  @Test
  void warnsWhenALevelIsTooWarmForItsLoad() {
    RefrigerationSystem system = new RefrigerationSystem("propane refrigeration");
    system.setRefrigerant("propane");
    system.setCondensingTemperature(40.0, "C");
    system.addLevel("LP", -20.0, "C").addLoad(RefrigerationLoad.fixed("chiller", 1.0e6, 273.15 - 21.0));
    system.run();
    assertEquals(1, system.getWarnings().size());
    assertTrue(system.getWarnings().get(0).contains("chiller"));
  }

  @Test
  void cascadeLoadsTheWarmSystemWithTheColdCondenser() {
    RefrigerationSystem cold = singleLevel("CO2", -45.0, -10.0, 0.75);
    RefrigerationSystem warm = new RefrigerationSystem("ammonia high stage");
    warm.setRefrigerant("ammonia");
    warm.setCondensingTemperature(35.0, "C");
    warm.setIsentropicEfficiency(0.75);
    warm.addLevel("cascade condenser", -15.0, "C").addLoad(RefrigerationLoad.cascadeCondenser(cold));

    ProcessSystem process = new ProcessSystem();
    process.add(cold);
    process.add(warm);
    process.run();

    assertEquals(cold.getCondenserDuty(), warm.getRefrigerationDuty(), 1.0e-6);
    assertTrue(warm.getWarnings().isEmpty(), warm.getWarnings().toString());
    double overallCop = cold.getRefrigerationDuty() / (cold.getCompressorPower() + warm.getCompressorPower());
    assertTrue(overallCop > 1.0 && overallCop < cold.getCOP());
    // The cold-system energy balance carries through the cascade.
    assertEquals(cold.getRefrigerationDuty() + cold.getCompressorPower() + warm.getCompressorPower(),
        warm.getCondenserDuty(), 1.0);
  }

  @Test
  void carnotFractionCalibratesTheScreeningCycle() {
    RefrigerationSystem rigorous = singleLevel("propane", -35.0, 35.0, 0.75);
    rigorous.run();
    RefrigerationCycle screening = new RefrigerationCycle("screening");
    screening.setEvaporatorTempC(-35.0);
    screening.setCondenserTempC(35.0);
    screening.setCycleEfficiency(rigorous.getCarnotFraction());
    screening.addRefrigerationDuty("load", 1000.0);
    screening.calculate();
    assertEquals(rigorous.getCompressorPower("kW"), screening.getCompressorPowerKW(), 1.0e-6);
    // The screening default of 0.55 is within the range a real single-stage propane cycle reaches.
    assertTrue(rigorous.getCarnotFraction() > 0.40 && rigorous.getCarnotFraction() < 0.65);
  }

  @Test
  void invalidSetupsAreRejectedWithAReason() {
    RefrigerationSystem noRefrigerant = new RefrigerationSystem("empty");
    assertFalse(noRefrigerant.validateSetup().isValid());
    assertThrows(IllegalStateException.class, () -> noRefrigerant.run());

    RefrigerationSystem transcritical = singleLevel("CO2", -30.0, 35.0, 0.75);
    assertFalse(transcritical.validateSetup().isValid());

    RefrigerationSystem inverted = singleLevel("propane", 45.0, 40.0, 0.75);
    assertFalse(inverted.validateSetup().isValid());

    RefrigerationSystem intercooledWithoutTanks = singleLevel("propane", -30.0, 40.0, 0.75);
    intercooledWithoutTanks.setEconomizerType(EconomizerType.NONE);
    intercooledWithoutTanks.setInterstageCooling(InterstageCooling.FLASH_INTERCOOLER);
    assertFalse(intercooledWithoutTanks.validateSetup().isValid());

    RefrigerationSystem duplicate = singleLevel("propane", -30.0, 40.0, 0.75);
    assertThrows(IllegalArgumentException.class, () -> duplicate.addLevel("LP", -10.0, "C"));
    assertThrows(IllegalStateException.class, () -> duplicate.getCOP());
  }

  @Test
  void reportsJsonAndIsConstructibleFromTheFactory() {
    RefrigerationSystem system = singleLevel("propane", -35.0, 40.0, 0.75);
    assertEquals(null, system.toJson());
    system.run();
    String json = system.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"COP\""));
    assertTrue(json.contains("\"compressorStages\""));
    assertTrue(EquipmentFactory.createEquipment("ref", "RefrigerationSystem") instanceof RefrigerationSystem);

    RefrigerationSystem idle = new RefrigerationSystem("idle");
    idle.setRefrigerant("propane");
    idle.addLevel("LP", -35.0, "C");
    idle.run();
    assertEquals(0.0, idle.getCompressorPower(), 0.0);
    assertTrue(Double.isNaN(idle.getCOP()));
    assertNotNull(idle.toJson());
  }
}
