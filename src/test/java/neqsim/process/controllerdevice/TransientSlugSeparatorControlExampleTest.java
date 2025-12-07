package neqsim.process.controllerdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.TransientSlugSeparatorControlExample.SimulationResult;

public class TransientSlugSeparatorControlExampleTest {

  @Test
  void runExampleProducesResults() {
    SimulationResult results = TransientSlugSeparatorControlExample.runSimulation();

    assertNotNull(results.getSlugStatistics(), "Slug statistics should be available");
    assertFalse(results.getSlugStatistics().isEmpty(), "Slug statistics should not be empty");
    assertTrue(results.getLiquidLevel() >= 0.0, "Liquid level should be non-negative");
    assertTrue(results.getGasOutletPressure() > 0.0, "Gas outlet pressure should be positive");

    assertFalse(results.getTimes().isEmpty(), "Time history should be populated");
    assertEquals(
        results.getTimes().size(),
        results.getLiquidLevelHistory().size(),
        "Level history should match time steps");
    assertEquals(
        results.getTimes().size(),
        results.getGasOutletPressureHistory().size(),
        "Pressure history should match time steps");
    assertTrue(results.getTimes().get(results.getTimes().size() - 1) > 0.0, "Simulation should advance time");
  }
}
