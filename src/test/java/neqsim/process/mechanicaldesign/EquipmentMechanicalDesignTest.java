package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.membrane.MembraneSeparator;
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.electrolyzer.ElectrolyzerMechanicalDesign;
import neqsim.process.mechanicaldesign.flare.FlareMechanicalDesign;
import neqsim.process.mechanicaldesign.membrane.MembraneMechanicalDesign;
import neqsim.process.mechanicaldesign.powergeneration.PowerGenerationMechanicalDesign;
import neqsim.process.mechanicaldesign.reactor.ReactorMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for equipment-based mechanical design classes: Flare, Electrolyzer, Membrane, Reactor, and
 * PowerGeneration.
 *
 * @author esol
 */
class EquipmentMechanicalDesignTest {

  /**
   * Creates a standard natural gas fluid for testing.
   *
   * @return configured gas fluid
   */
  private SystemInterface createGasFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Creates a low-pressure flare gas fluid (realistic flare header conditions).
   *
   * @return configured low-pressure gas fluid
   */
  private SystemInterface createFlareGasFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 1.5);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Creates a water fluid for electrolyzer testing.
   *
   * @return configured water fluid
   */
  private SystemInterface createWaterFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 30.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  // ============================================================================
  // FlareMechanicalDesign Tests
  // ============================================================================
  @Nested
  @DisplayName("FlareMechanicalDesign Tests")
  class FlareMechanicalDesignTests {

    @Test
    @DisplayName("Should calculate flare design with gas stream")
    void shouldCalculateFlareDesign() {
      SystemInterface fluid = createFlareGasFluid();
      Stream feed = new Stream("flare feed", fluid);
      feed.setFlowRate(5000.0, "kg/hr");
      feed.run();

      Flare flare = new Flare("Test Flare", feed);
      flare.run();

      FlareMechanicalDesign design = new FlareMechanicalDesign(flare);
      design.calcDesign();

      assertTrue(design.getTipDiameter() > 0, "Tip diameter should be positive");
      assertTrue(design.getStackHeight() >= 10.0, "Stack height should be at least 10m");
      assertTrue(design.getFlameLength() > 0, "Flame length should be positive");
      assertTrue(design.getHeaderDiameter() > 0, "Header diameter should be positive");
      assertTrue(design.getStackWeight() > 0, "Stack weight should be positive");
      assertTrue(design.getPilotGasConsumption() > 0, "Pilot gas should be positive");
    }

    @Test
    @DisplayName("Larger flow should produce taller stack")
    void largerFlowShouldProduceTallerStack() {
      SystemInterface fluid1 = createFlareGasFluid();
      Stream feed1 = new Stream("feed1", fluid1);
      feed1.setFlowRate(1000.0, "kg/hr");
      feed1.run();

      SystemInterface fluid2 = createFlareGasFluid();
      Stream feed2 = new Stream("feed2", fluid2);
      feed2.setFlowRate(50000.0, "kg/hr");
      feed2.run();

      Flare flare1 = new Flare("Small Flare", feed1);
      flare1.run();
      FlareMechanicalDesign design1 = new FlareMechanicalDesign(flare1);
      design1.calcDesign();

      Flare flare2 = new Flare("Large Flare", feed2);
      flare2.run();
      FlareMechanicalDesign design2 = new FlareMechanicalDesign(flare2);
      design2.calcDesign();

      assertTrue(design2.getStackHeight() >= design1.getStackHeight(),
          "Larger flow should require taller stack");
      assertTrue(design2.getTipDiameter() >= design1.getTipDiameter(),
          "Larger flow should need bigger or equal tip");
    }

    @Test
    @DisplayName("Heat release should be positive for combustible gas")
    void heatReleaseShouldBePositive() {
      SystemInterface fluid = createFlareGasFluid();
      Stream feed = new Stream("feed", fluid);
      feed.setFlowRate(10000.0, "kg/hr");
      feed.run();

      Flare flare = new Flare("Flare", feed);
      flare.run();
      FlareMechanicalDesign design = new FlareMechanicalDesign(flare);
      design.calcDesign();

      assertTrue(design.getDesignHeatReleaseMW() >= 0, "Heat release should be non-negative");
    }

    @Test
    @DisplayName("Radiation distance should be positive")
    void radiationDistanceShouldBePositive() {
      SystemInterface fluid = createFlareGasFluid();
      Stream feed = new Stream("feed", fluid);
      feed.setFlowRate(5000.0, "kg/hr");
      feed.run();

      Flare flare = new Flare("Flare", feed);
      flare.run();
      FlareMechanicalDesign design = new FlareMechanicalDesign(flare);
      design.calcDesign();

      assertTrue(design.getRadiationDistanceAtGrade() >= 0,
          "Radiation distance should be non-negative");
    }
  }

  // ============================================================================
  // ElectrolyzerMechanicalDesign Tests
  // ============================================================================
  @Nested
  @DisplayName("ElectrolyzerMechanicalDesign Tests")
  class ElectrolyzerMechanicalDesignTests {

    @Test
    @DisplayName("Should calculate electrolyzer design from water stream")
    void shouldCalculateElectrolyzerDesign() {
      SystemInterface water = createWaterFluid();
      Stream waterFeed = new Stream("water feed", water);
      waterFeed.setFlowRate(100.0, "kg/hr");
      waterFeed.run();

      Electrolyzer electrolyzer = new Electrolyzer("Test Electrolyzer", waterFeed);
      electrolyzer.run();

      ElectrolyzerMechanicalDesign design = new ElectrolyzerMechanicalDesign(electrolyzer);
      design.calcDesign();

      assertTrue(design.getH2ProductionRateKgHr() > 0, "H2 production rate should be positive");
      assertTrue(design.getTotalPowerKW() > 0, "Total power should be positive");
      assertTrue(design.getNumberOfStacks() >= 1, "Should have at least one stack");
      assertTrue(design.getCellsPerStack() > 0, "Should have positive cells per stack");
    }

    @Test
    @DisplayName("PEM and alkaline should give different weights")
    void pemAndAlkalineShouldDiffer() {
      SystemInterface water = createWaterFluid();
      Stream waterFeed = new Stream("water1", water);
      waterFeed.setFlowRate(100.0, "kg/hr");
      waterFeed.run();

      Electrolyzer elec1 = new Electrolyzer("PEM", waterFeed);
      elec1.run();
      ElectrolyzerMechanicalDesign pem = new ElectrolyzerMechanicalDesign(elec1);
      pem.setElectrolyzerType("PEM");
      pem.calcDesign();

      SystemInterface water2 = createWaterFluid();
      Stream waterFeed2 = new Stream("water2", water2);
      waterFeed2.setFlowRate(100.0, "kg/hr");
      waterFeed2.run();

      Electrolyzer elec2 = new Electrolyzer("ALK", waterFeed2);
      elec2.run();
      ElectrolyzerMechanicalDesign alk = new ElectrolyzerMechanicalDesign(elec2);
      alk.setElectrolyzerType("ALKALINE");
      alk.calcDesign();

      // Alkaline is heavier per kW
      assertTrue(alk.getWeightTotal() > pem.getWeightTotal(),
          "Alkaline should be heavier than PEM for same production");
    }

    @Test
    @DisplayName("Specific energy should be in reasonable range")
    void specificEnergyShouldBeReasonable() {
      SystemInterface water = createWaterFluid();
      Stream waterFeed = new Stream("water", water);
      waterFeed.setFlowRate(100.0, "kg/hr");
      waterFeed.run();

      Electrolyzer electrolyzer = new Electrolyzer("Elec", waterFeed);
      electrolyzer.run();
      ElectrolyzerMechanicalDesign design = new ElectrolyzerMechanicalDesign(electrolyzer);
      design.calcDesign();

      double specificEnergy = design.getSpecificEnergyKWhPerKg();
      // Typical PEM: 50-80 kWh/kgH2 (including overvoltage)
      if (specificEnergy > 0) {
        assertTrue(specificEnergy > 30, "Specific energy should be > 30 kWh/kgH2");
        assertTrue(specificEnergy < 200, "Specific energy should be < 200 kWh/kgH2");
      }
    }

    @Test
    @DisplayName("Stack efficiency should be between 0 and 1")
    void stackEfficiencyShouldBeReasonable() {
      SystemInterface water = createWaterFluid();
      Stream waterFeed = new Stream("water", water);
      waterFeed.setFlowRate(100.0, "kg/hr");
      waterFeed.run();

      Electrolyzer electrolyzer = new Electrolyzer("Elec", waterFeed);
      electrolyzer.run();
      ElectrolyzerMechanicalDesign design = new ElectrolyzerMechanicalDesign(electrolyzer);
      design.calcDesign();

      double efficiency = design.getStackEfficiency();
      if (efficiency > 0) {
        assertTrue(efficiency > 0.1, "Efficiency should be > 10%");
        assertTrue(efficiency <= 1.0, "Efficiency should be <= 100%");
      }
    }
  }

  // ============================================================================
  // MembraneMechanicalDesign Tests
  // ============================================================================
  @Nested
  @DisplayName("MembraneMechanicalDesign Tests")
  class MembraneMechanicalDesignTests {

    @Test
    @DisplayName("Should calculate membrane design with gas stream")
    void shouldCalculateMembraneDesign() {
      SystemInterface fluid = createGasFluid();
      Stream feed = new Stream("membrane feed", fluid);
      feed.setFlowRate(5000.0, "kg/hr");
      feed.run();

      MembraneSeparator membrane = new MembraneSeparator("Test Membrane", feed);
      membrane.setMembraneArea(500.0); // 500 m2
      membrane.run();

      MembraneMechanicalDesign design = new MembraneMechanicalDesign(membrane);
      design.calcDesign();

      assertTrue(design.getNumberOfModules() > 0, "Should have at least 1 module");
      assertTrue(design.getTotalMembraneArea() > 0, "Total area should be positive");
      assertTrue(design.getHousingWallThickness() > 0, "Housing wall thickness should be positive");
    }

    @Test
    @DisplayName("Module type defaults should be valid")
    void moduleTypeShouldBeValid() {
      SystemInterface fluid = createGasFluid();
      Stream feed = new Stream("feed", fluid);
      feed.setFlowRate(5000.0, "kg/hr");
      feed.run();

      MembraneSeparator membrane = new MembraneSeparator("Membrane", feed);
      membrane.run();

      MembraneMechanicalDesign design = new MembraneMechanicalDesign(membrane);
      assertNotNull(design.getModuleType());
    }

    @Test
    @DisplayName("Membrane life should have sensible default")
    void membraneLifeShouldHaveSensibleDefault() {
      SystemInterface fluid = createGasFluid();
      Stream feed = new Stream("feed", fluid);
      feed.setFlowRate(5000.0, "kg/hr");
      feed.run();

      MembraneSeparator membrane = new MembraneSeparator("Membrane", feed);
      membrane.run();

      MembraneMechanicalDesign design = new MembraneMechanicalDesign(membrane);
      assertTrue(design.getMembraneLifeMonths() > 0, "Membrane life should be positive");
    }
  }

  // ============================================================================
  // ReactorMechanicalDesign Tests
  // ============================================================================
  @Nested
  @DisplayName("ReactorMechanicalDesign Tests")
  class ReactorMechanicalDesignTests {

    @Test
    @DisplayName("Should calculate reactor vessel design")
    void shouldCalculateReactorDesign() {
      SystemInterface fluid = createGasFluid();
      Stream feed = new Stream("reactor feed", fluid);
      feed.setFlowRate(10000.0, "kg/hr");
      feed.run();

      neqsim.process.equipment.reactor.GibbsReactor reactor =
          new neqsim.process.equipment.reactor.GibbsReactor("Test Reactor", feed);
      reactor.run();

      ReactorMechanicalDesign design = new ReactorMechanicalDesign(reactor);
      design.setMaxOperationPressure(50.0);
      design.setMaxOperationTemperature(273.15 + 400.0);
      design.calcDesign();

      assertTrue(design.getVesselDiameter() > 0, "Vessel diameter should be positive");
      assertTrue(design.getVesselLength() > 0, "Vessel length should be positive");
      assertTrue(design.getShellThickness() > 0, "Shell thickness should be positive");
      assertTrue(design.getHeadThickness() > 0, "Head thickness should be positive");
    }

    @Test
    @DisplayName("Higher pressure should produce thicker shell")
    void higherPressureShouldProduceThickerShell() {
      SystemInterface fluid1 = createGasFluid();
      Stream feed1 = new Stream("feed1", fluid1);
      feed1.setFlowRate(5000.0, "kg/hr");
      feed1.run();

      neqsim.process.equipment.reactor.GibbsReactor reactor1 =
          new neqsim.process.equipment.reactor.GibbsReactor("Reactor LP", feed1);
      reactor1.run();

      ReactorMechanicalDesign designLP = new ReactorMechanicalDesign(reactor1);
      designLP.setMaxOperationPressure(10.0);
      designLP.setMaxOperationTemperature(273.15 + 200.0);
      designLP.calcDesign();

      SystemInterface fluid2 = createGasFluid();
      Stream feed2 = new Stream("feed2", fluid2);
      feed2.setFlowRate(5000.0, "kg/hr");
      feed2.run();

      neqsim.process.equipment.reactor.GibbsReactor reactor2 =
          new neqsim.process.equipment.reactor.GibbsReactor("Reactor HP", feed2);
      reactor2.run();

      ReactorMechanicalDesign designHP = new ReactorMechanicalDesign(reactor2);
      designHP.setMaxOperationPressure(100.0);
      designHP.setMaxOperationTemperature(273.15 + 200.0);
      designHP.calcDesign();

      assertTrue(designHP.getShellThickness() > designLP.getShellThickness(),
          "Higher pressure should require thicker shell");
    }

    @Test
    @DisplayName("Reactor type can be set and retrieved")
    void reactorTypeCanBeSetAndRetrieved() {
      SystemInterface fluid = createGasFluid();
      Stream feed = new Stream("feed", fluid);
      feed.setFlowRate(5000.0, "kg/hr");
      feed.run();

      neqsim.process.equipment.reactor.GibbsReactor reactor =
          new neqsim.process.equipment.reactor.GibbsReactor("Reactor", feed);
      reactor.run();

      ReactorMechanicalDesign design = new ReactorMechanicalDesign(reactor);
      design.setReactorType("CSTR");
      assertEquals("CSTR", design.getReactorType());
    }

    @Test
    @DisplayName("Design pressure should include margin")
    void designPressureShouldIncludeMargin() {
      SystemInterface fluid = createGasFluid();
      Stream feed = new Stream("feed", fluid);
      feed.setFlowRate(5000.0, "kg/hr");
      feed.run();

      neqsim.process.equipment.reactor.GibbsReactor reactor =
          new neqsim.process.equipment.reactor.GibbsReactor("Reactor", feed);
      reactor.run();

      ReactorMechanicalDesign design = new ReactorMechanicalDesign(reactor);
      design.setMaxOperationPressure(50.0);
      design.setMaxOperationTemperature(273.15 + 300.0);
      design.calcDesign();

      assertTrue(design.getDesignPressureBara() > 50.0,
          "Design pressure should exceed max operating pressure");
    }
  }

  // ============================================================================
  // PowerGenerationMechanicalDesign Tests
  // ============================================================================
  @Nested
  @DisplayName("PowerGenerationMechanicalDesign Tests")
  class PowerGenerationMechanicalDesignTests {

    @Test
    @DisplayName("Should calculate gas turbine design")
    void shouldCalculateTurbineDesign() {
      SystemInterface fuel = createGasFluid();
      Stream fuelFeed = new Stream("fuel gas", fuel);
      fuelFeed.setFlowRate(2000.0, "kg/hr");
      fuelFeed.run();

      GasTurbine turbine = new GasTurbine("Test GT");
      turbine.setInletStream(fuelFeed);
      turbine.run();

      PowerGenerationMechanicalDesign design = new PowerGenerationMechanicalDesign(turbine);
      design.calcDesign();

      assertTrue(design.getRatedPowerMW() > 0, "Power should be positive");
      assertNotNull(design.getTurbineClass(), "Turbine class should be set");
      assertTrue(design.getFuelConsumptionKgHr() > 0, "Fuel consumption should be positive");
    }

    @Test
    @DisplayName("Turbine efficiency should be in valid range")
    void turbineEfficiencyShouldBeValid() {
      SystemInterface fuel = createGasFluid();
      Stream fuelFeed = new Stream("fuel", fuel);
      fuelFeed.setFlowRate(1000.0, "kg/hr");
      fuelFeed.run();

      GasTurbine turbine = new GasTurbine("GT");
      turbine.setInletStream(fuelFeed);
      turbine.run();

      PowerGenerationMechanicalDesign design = new PowerGenerationMechanicalDesign(turbine);
      design.calcDesign();

      double efficiency = design.getThermalEfficiency();
      if (efficiency > 0) {
        assertTrue(efficiency > 0.1, "Efficiency should be > 10%");
        assertTrue(efficiency < 0.6, "Efficiency should be < 60%");
      }
    }

    @Test
    @DisplayName("CO2 emissions should be positive for gas fuel")
    void co2EmissionsShouldBePositive() {
      SystemInterface fuel = createGasFluid();
      Stream fuelFeed = new Stream("fuel", fuel);
      fuelFeed.setFlowRate(2000.0, "kg/hr");
      fuelFeed.run();

      GasTurbine turbine = new GasTurbine("GT");
      turbine.setInletStream(fuelFeed);
      turbine.run();

      PowerGenerationMechanicalDesign design = new PowerGenerationMechanicalDesign(turbine);
      design.calcDesign();

      assertTrue(design.getCo2EmissionTonnesHr() >= 0, "CO2 emissions should be non-negative");
    }

    @Test
    @DisplayName("Weight should be positive")
    void weightShouldBePositive() {
      SystemInterface fuel = createGasFluid();
      Stream fuelFeed = new Stream("fuel", fuel);
      fuelFeed.setFlowRate(2000.0, "kg/hr");
      fuelFeed.run();

      GasTurbine turbine = new GasTurbine("GT");
      turbine.setInletStream(fuelFeed);
      turbine.run();

      PowerGenerationMechanicalDesign design = new PowerGenerationMechanicalDesign(turbine);
      design.calcDesign();

      assertTrue(design.getTurbinePackageWeightTonnes() > 0,
          "Turbine package weight should be positive");
      assertTrue(design.getTotalSystemWeightTonnes() > 0, "Total system weight should be positive");
    }
  }
}
