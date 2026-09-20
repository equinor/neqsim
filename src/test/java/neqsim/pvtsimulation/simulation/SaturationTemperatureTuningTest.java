package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression coverage for the repeated UMR-PRU dew-point tuning workload in issue 3795. */
class SaturationTemperatureTuningTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(SaturationTemperatureTuningTest.class);

  @Test
  void nearBoundarySearchAvoidsGlobalScan() {
    for (double factor : new double[] {0.5, 1.0, 1.5}) {
      SystemInterface referenceFluid = createFluid(factor);
      SaturationTemperature reference = new SaturationTemperature(referenceFluid);
      CountingOperations referenceOperations = new CountingOperations(referenceFluid);
      reference.thermoOps = referenceOperations;
      double expected = reference.calcSaturationTemperature();

      SystemInterface fluid = createFluid(factor);
      SaturationTemperature simulation = new SaturationTemperature(fluid);
      simulation.setTemperatureSearchBounds(260.0, 320.0);
      CountingOperations operations = new CountingOperations(fluid);
      simulation.thermoOps = operations;
      for (int repetition = 0; repetition < 2; repetition++) {
        operations.flashes = 0;
        double result = simulation.calcSaturationTemperature();
        assertEquals(expected, result, 1.0e-5);
        assertTrue(operations.flashes <= 30,
            "Near-boundary tuning should need at most 30 flashes, got " + operations.flashes);
        assertTrue(operations.flashes * 3 < referenceOperations.flashes);
        assertFalse(fluid.doMultiPhaseCheck());
        logger.info("Issue 3795: factor={}, T={} K, global flashes={}, bounded flashes={}", factor, result,
            referenceOperations.flashes, operations.flashes);
      }
    }
  }

  @Test
  void repeatedHeavyFractionTuningUsesFreshCompositionAndPreviousTemperature() {
    double target = new SaturationTemperature(createFluid(1.0)).calcSaturationTemperature();
    double lowerFactor = 0.5;
    double upperFactor = 1.5;
    double previousTemperature = 280.0;
    int totalFlashes = 0;
    for (int iteration = 0; iteration < 22; iteration++) {
      double factor = (lowerFactor + upperFactor) / 2.0;
      SystemInterface fluid = createFluid(factor);
      fluid.setTemperature(previousTemperature);
      SaturationTemperature simulation = new SaturationTemperature(fluid);
      simulation.setTemperatureSearchBounds(260.0, 320.0);
      CountingOperations operations = new CountingOperations(fluid);
      simulation.thermoOps = operations;
      previousTemperature = simulation.calcSaturationTemperature();
      totalFlashes += operations.flashes;
      assertTrue(operations.flashes <= 30);
      if (previousTemperature < target) {
        lowerFactor = factor;
      } else {
        upperFactor = factor;
      }
    }
    assertEquals(1.0, (lowerFactor + upperFactor) / 2.0, 1.0e-5);
    assertEquals(target, previousTemperature, 2.0e-5);
    logger.info("Issue 3795 tuning: 22 calls, {} TP flashes, final T={} K", totalFlashes, previousTemperature);
  }

  @Test
  void runHonorsBoundsAfterCompositionAndPressureChanges() {
    SystemInterface fluid = createFluid(1.0);
    SaturationTemperature simulation = new SaturationTemperature(fluid);
    simulation.setTemperatureSearchBounds(260.0, 320.0);
    simulation.run();
    assertEquals(290.88693618774414, simulation.getSaturationTemperature(), 1.0e-4);

    fluid.addComponent("nC10", 1.0e-5);
    fluid.setPressure(55.0);
    SystemInterface referenceFluid = fluid.clone();
    double expected = new SaturationTemperature(referenceFluid).calcSaturationTemperature();
    simulation.run();
    assertEquals(expected, simulation.getSaturationTemperature(), 1.0e-5);
    assertFalse(fluid.doMultiPhaseCheck());
  }

  private static SystemInterface createFluid(double heavyFactor) {
    String[] names = {"CO2", "nitrogen", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "2-m-C5", "3-m-C5", "n-hexane", "n-heptane", "c-hexane", "benzene", "n-octane", "c-C7", "toluene",
        "n-nonane", "c-C8", "m-Xylene", "nC10", "nC11", "nC12"};
    double[] amounts = {0.00645, 0.00966, 0.949, 0.0258, 0.00352, 0.00152, 0.000881, 0.00079, 0.000395, 0.000401,
        0.000122, 0.000198, 0.000133, 0.000798, 1.65e-5, 3.39e-5, 0.000487, 3.62e-5, 2.57e-5, 5.81e-5, 1.7e-5, 1.16e-5,
        0.0, 0.0};
    SystemInterface fluid = new SystemUMRPRUMCEos(280.0, 52.1);
    for (int index = 0; index < names.length; index++) {
      fluid.addComponent(names[index], amounts[index] * (index >= 9 ? heavyFactor : 1.0));
    }
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    return fluid;
  }

  private static final class CountingOperations extends ThermodynamicOperations {
    private int flashes;

    private CountingOperations(SystemInterface fluid) {
      super(fluid);
    }

    @Override
    public void TPflash() {
      flashes++;
      super.TPflash();
    }
  }
}
