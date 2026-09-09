package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Recombination must preserve standard liquid rates, reference phase volumes, and cached composition.
 */
class RecombinationFlashGeneratorRateConsistencyTest {
  private FluidMagicInput input() {
    SystemInterface reference = new SystemSrkEos(288.15, 1.01325);
    reference.addComponent("methane", 0.5);
    reference.addComponent("n-heptane", 0.5);
    reference.setMixingRule("classic");
    reference.setMultiPhaseCheck(true);
    FluidMagicInput input = FluidMagicInput.fromFluid(reference);
    input.separateToStandardConditions();
    return input;
  }

  @Test
  void extractedReferencePhasesRemainSinglePhase() {
    FluidMagicInput input = input();
    assertEquals(1, input.getGasPhase().getNumberOfPhases());
    assertEquals(1, input.getOilPhase().getNumberOfPhases());
    assertEquals(1, input.getWaterPhase().getNumberOfPhases());
    assertTrue(input.getGasPhase().hasPhaseType("gas"));
    assertTrue(input.getOilPhase().hasPhaseType("oil"));
    assertTrue(input.getWaterPhase().hasPhaseType("aqueous"));
  }

  @Test
  void cacheMissAndRescaledHitsMeetStandardLiquidRateAndMoleBalances() {
    RecombinationFlashGenerator generator = new RecombinationFlashGenerator(input());
    for (double waterCut : new double[] { 0.0, 0.3, 0.7 }) {
      for (double gor : new double[] { 80.0, 200.0 }) {
        double initialMassRate = 0.0;
        for (double rate : new double[] { 1000.0, 2000.0, 1000.0 }) {
          SystemInterface fluid = generator.generateFluid(gor, waterCut, rate, 353.15, 50.0);
          assertEquals(353.15, fluid.getTemperature(), 1.0e-9);
          assertEquals(50.0, fluid.getPressure(), 1.0e-9);
          double massRate = fluid.getFlowRate("kg/hr");
          if (initialMassRate == 0.0) {
            initialMassRate = massRate;
          }
          assertEquals(initialMassRate * rate / 1000.0, massRate, initialMassRate * 1.0e-8);
          fluid.setTemperature(288.15);
          fluid.setPressure(1.01325);
          new ThermodynamicOperations(fluid).TPflash();
          fluid.initPhysicalProperties();
          double oil = fluid.getPhase("oil").getVolume("m3");
          double gas = fluid.getPhase("gas").getVolume("m3");
          double water = fluid.hasPhaseType("aqueous") ? fluid.getPhase("aqueous").getVolume("m3") : 0.0;
          assertEquals(rate, (oil + water) * 3600.0, rate * 1.0e-8);
          // Added water changes the vapour composition slightly at standard conditions.
          assertEquals(gor, gas / oil, gor * 0.05);
          assertEquals(waterCut, water / (oil + water), 0.005);
          double componentMoles = 0.0;
          double phaseMoles = 0.0;
          for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
            componentMoles += fluid.getComponent(i).getNumberOfmoles();
          }
          for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
            phaseMoles += fluid.getPhase(i).getNumberOfMolesInPhase();
          }
          assertEquals(fluid.getTotalNumberOfMoles(), componentMoles, componentMoles * 1.0e-9);
          assertEquals(fluid.getTotalNumberOfMoles(), phaseMoles, componentMoles * 1.0e-9);
        }
      }
    }
  }

  @Test
  void neighboringScenarioValuesDoNotCollideInCache() {
    RecombinationFlashGenerator generator = new RecombinationFlashGenerator(input());
    generator.generateFluid(200.0, 0.3, 1000.0, 353.15, 50.0);
    SystemInterface cached = generator.generateFluid(200.004, 0.30002, 1500.0, 363.15, 80.0);
    generator.setEnableCaching(false);
    SystemInterface fresh = generator.generateFluid(200.004, 0.30002, 1500.0, 363.15, 80.0);
    assertArrayEquals(fresh.getMolarComposition(), cached.getMolarComposition(), 1.0e-12);
    assertEquals(fresh.getFlowRate("kg/hr"), cached.getFlowRate("kg/hr"), 1.0e-6);
  }

  @Test
  void characterizedFractionsAndReferenceEquationOfStateSurviveRecombination() {
    SystemInterface reference = new SystemPrEos(288.15, 1.01325);
    reference.addComponent("methane", 0.5);
    reference.addTBPfraction("C7_test", 0.5, 0.15, 0.78);
    reference.setMixingRule("classic");
    reference.setMultiPhaseCheck(true);
    FluidMagicInput input = FluidMagicInput.fromFluid(reference);
    input.separateToStandardConditions();
    RecombinationFlashGenerator generator = new RecombinationFlashGenerator(input);
    for (double rate : new double[] { 1000.0, 2000.0 }) {
      SystemInterface recombined = generator.generateFluid(80.0, 0.1, rate, 353.15, 50.0);
      assertEquals(reference.getClass(), recombined.getClass());
      assertTrue(recombined.getComponent("C7_test_PC").isIsTBPfraction());
      assertEquals(reference.getComponent("C7_test_PC").getMolarMass(),
          recombined.getComponent("C7_test_PC").getMolarMass(), 1.0e-12);
      assertEquals(reference.getComponent("C7_test_PC").getTC(), recombined.getComponent("C7_test_PC").getTC(),
          1.0e-10);
      assertEquals(reference.getComponent("C7_test_PC").getPC(), recombined.getComponent("C7_test_PC").getPC(),
          1.0e-10);
      recombined.setTemperature(288.15);
      recombined.setPressure(1.01325);
      new ThermodynamicOperations(recombined).TPflash();
      recombined.initPhysicalProperties();
      double liquid = recombined.getPhase("oil").getVolume("m3") + recombined.getPhase("aqueous").getVolume("m3");
      assertEquals(rate, 3600.0 * liquid, rate * 1.0e-8);
    }
  }
}
