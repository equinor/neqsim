package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.function.DoublePredicate;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Deterministic search-topology tests, independent of EOS convergence and wall-clock timing. */
class SaturationTemperatureSearchTest extends neqsim.NeqSimTest {
  @Test
  void defaultSearchFindsDisconnectedUpperRegionRegardlessOfInitialTemperature() {
    SaturationTemperature simulation = simulationAt(285.0,
        temperature -> (temperature >= 270.0 && temperature < 291.0) || (temperature >= 530.0 && temperature < 551.0));
    assertEquals(551.0, simulation.calcSaturationTemperature(), 1.0e-5);
  }

  @Test
  void boundedSearchFindsUppermostOfMultipleCrossingsInInterval() {
    SaturationTemperature simulation = simulationAt(273.0,
        temperature -> (temperature >= 270.0 && temperature < 281.0) || (temperature >= 300.0 && temperature < 311.0));
    simulation.setTemperatureSearchBounds(260.0, 320.0);
    assertEquals(311.0, simulation.calcSaturationTemperature(), 1.0e-5);
    assertTrue(((SyntheticOperations) simulation.thermoOps).flashes <= 25);
  }

  @Test
  void boundsAreExplicitAndCanBeClearedToRecoverGlobalUppermostCrossing() {
    SaturationTemperature simulation = simulationAt(285.0,
        temperature -> (temperature >= 270.0 && temperature < 291.0) || (temperature >= 530.0 && temperature < 551.0));
    simulation.setTemperatureSearchBounds(260.0, 320.0);
    assertEquals(291.0, simulation.calcSaturationTemperature(), 1.0e-5);
    simulation.clearTemperatureSearchBounds();
    assertEquals(551.0, simulation.calcSaturationTemperature(), 1.0e-5);
  }

  @Test
  void multiphaseUpperEndpointFallsBackRatherThanReturningLowerCrossing() {
    SaturationTemperature simulation = simulationAt(275.0,
        temperature -> (temperature >= 260.0 && temperature < 281.0) || (temperature >= 310.0 && temperature < 341.0));
    simulation.setTemperatureSearchBounds(250.0, 320.0);
    assertEquals(341.0, simulation.calcSaturationTemperature(), 1.0e-5);
    assertEquals(1200.0, ((SyntheticOperations) simulation.thermoOps).maximumTemperature);
  }

  @Test
  void missingLocalBoundaryFallsBackToGlobalSearch() {
    SaturationTemperature simulation = simulationAt(290.0, temperature -> temperature < 241.0);
    simulation.setTemperatureSearchBounds(260.0, 320.0);
    assertEquals(241.0, simulation.calcSaturationTemperature(), 1.0e-5);
    assertFalse(simulation.getThermoSystem().doMultiPhaseCheck());
  }

  @Test
  void noBoundaryPreservesGlobalFallbackAndMultiphaseSetting() {
    SaturationTemperature simulation = simulationAt(290.0, temperature -> false);
    simulation.setTemperatureSearchBounds(260.0, 320.0);
    simulation.getThermoSystem().setMultiPhaseCheck(true);
    assertEquals(1200.0, simulation.calcSaturationTemperature());
    assertEquals(1, simulation.getThermoSystem().getNumberOfPhases());
    assertTrue(simulation.getThermoSystem().doMultiPhaseCheck());
  }

  @Test
  void offGridBoundsNarrowerThanOneStepAreBothTested() {
    SaturationTemperature simulation = simulationAt(290.0, temperature -> temperature < 292.5);
    simulation.setTemperatureSearchBounds(291.0, 294.0);
    assertEquals(292.5, simulation.calcSaturationTemperature(), 1.0e-5);
    assertEquals(294.0, ((SyntheticOperations) simulation.thermoOps).maximumTemperature);
  }

  @Test
  void invalidBoundsAreRejectedWithoutMutatingTheFluid() {
    SaturationTemperature simulation = simulationAt(290.0, temperature -> temperature < 291.0);
    double[][] invalidBounds = { { Double.NaN, 320.0 }, { 260.0, Double.POSITIVE_INFINITY }, { 29.0, 320.0 },
        { 260.0, 1201.0 }, { 300.0, 300.0 }, { 320.0, 260.0 } };
    for (double[] bounds : invalidBounds) {
      assertThrows(IllegalArgumentException.class, () -> simulation.setTemperatureSearchBounds(bounds[0], bounds[1]));
    }
    assertEquals(290.0, simulation.getThermoSystem().getTemperature());
    assertFalse(simulation.getThermoSystem().doMultiPhaseCheck());
  }

  @Test
  void flashFailureRestoresMultiphaseSettingAndPropagates() {
    SaturationTemperature simulation = simulationAt(290.0, temperature -> {
      throw new IllegalStateException("Synthetic flash failure");
    });
    simulation.setTemperatureSearchBounds(260.0, 320.0);
    assertThrows(IllegalStateException.class, simulation::calcSaturationTemperature);
    assertFalse(simulation.getThermoSystem().doMultiPhaseCheck());
  }

  private static SaturationTemperature simulationAt(double temperature, DoublePredicate isMultiphase) {
    SystemInterface fluid = new SystemSrkEos(temperature, 52.1);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("n-decane", 0.05);
    fluid.setMixingRule("classic");
    SaturationTemperature simulation = new SaturationTemperature(fluid);
    simulation.thermoOps = new SyntheticOperations(fluid, isMultiphase);
    return simulation;
  }

  private static final class SyntheticOperations extends ThermodynamicOperations {
    private final DoublePredicate isMultiphase;
    private int flashes;
    private double maximumTemperature = Double.NEGATIVE_INFINITY;

    private SyntheticOperations(SystemInterface fluid, DoublePredicate isMultiphase) {
      super(fluid);
      this.isMultiphase = isMultiphase;
    }

    @Override
    public void TPflash() {
      assertTrue(getSystem().doMultiPhaseCheck());
      flashes++;
      double temperature = getSystem().getTemperature();
      maximumTemperature = Math.max(maximumTemperature, temperature);
      getSystem().setNumberOfPhases(isMultiphase.test(temperature) ? 2 : 1);
    }
  }
}
