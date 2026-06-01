package neqsim.process.safety.dispersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.BoundaryConditions;
import neqsim.process.safety.dispersion.GasDispersionAnalyzer.ModelSelection;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Reference-envelope tests for gas dispersion screening calculations.
 */
class DispersionBenchmarkValidationTest {

  @Test
  void methaneJetDispersionFallsWithinReferenceEnvelope() {
    GasDispersionResult result = GasDispersionAnalyzer.builder()
        .scenarioName("methane jet reference envelope").fluid(singleComponentFluid("methane"))
        .massReleaseRate(1.0).boundaryConditions(neutralWeather())
        .modelSelection(ModelSelection.GAUSSIAN_PLUME).build().analyze();

    assertEquals("GAUSSIAN_PLUME", result.getSelectedModel());
    assertWithinReferenceEnvelope("methane LFL distance", result.getDistanceToLflM(), 1.0, 1000.0);
    assertWithinReferenceEnvelope("methane 50 percent LFL distance", result.getDistanceToHalfLflM(),
        result.getDistanceToLflM(), 3000.0);
  }

  @Test
  void co2DenseGasToxicEndpointFallsWithinReferenceEnvelope() {
    GasDispersionResult result = GasDispersionAnalyzer.builder()
        .scenarioName("CO2 dense gas reference envelope").fluid(singleComponentFluid("CO2"))
        .massReleaseRate(10.0).boundaryConditions(stableLowWindWeather())
        .toxicEndpoint("CO2", 50000.0).build().analyze();

    assertEquals("HEAVY_GAS", result.getSelectedModel());
    assertWithinReferenceEnvelope("CO2 5 percent endpoint", result.getToxicDistanceM(), 1.0,
        5000.0);
  }

  @Test
  void propaneHeavyGasFlammableEndpointFallsWithinReferenceEnvelope() {
    GasDispersionResult result = GasDispersionAnalyzer.builder()
        .scenarioName("propane heavy gas reference envelope").fluid(singleComponentFluid("propane"))
        .massReleaseRate(1.0).boundaryConditions(neutralWeather()).build().analyze();

    assertEquals("HEAVY_GAS", result.getSelectedModel());
    assertWithinReferenceEnvelope("propane LFL distance", result.getDistanceToLflM(), 1.0, 3000.0);
    assertTrue(result.getFlammableCloudVolumeM3() > 0.0);
  }

  @Test
  void h2sToxicPlumeFallsWithinReferenceEnvelope() {
    SystemInterface sourGas = new SystemSrkEos(288.15, 10.0);
    sourGas.addComponent("methane", 0.90);
    sourGas.addComponent("H2S", 0.10);
    sourGas.setMixingRule("classic");

    GasDispersionResult result = GasDispersionAnalyzer.builder()
        .scenarioName("H2S toxic plume reference envelope").fluid(sourGas).massReleaseRate(1.0)
        .boundaryConditions(neutralWeather()).toxicEndpoint("H2S", 100.0)
        .modelSelection(ModelSelection.GAUSSIAN_PLUME).build().analyze();

    assertEquals("GAUSSIAN_PLUME", result.getSelectedModel());
    assertWithinReferenceEnvelope("H2S 100 ppm endpoint", result.getToxicDistanceM(), 1.0, 20000.0);
  }

  private static SystemInterface singleComponentFluid(String componentName) {
    SystemInterface fluid = new SystemSrkEos(288.15, 10.0);
    fluid.addComponent(componentName, 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static BoundaryConditions neutralWeather() {
    return BoundaryConditions.builder().ambientTemperature(15.0, "C").windSpeed(5.0)
        .pasquillStabilityClass('D').isOffshore(false).surfaceRoughness(0.1).build();
  }

  private static BoundaryConditions stableLowWindWeather() {
    return BoundaryConditions.builder().ambientTemperature(10.0, "C").windSpeed(2.0)
        .pasquillStabilityClass('F').isOffshore(false).surfaceRoughness(0.1).build();
  }

  private static void assertWithinReferenceEnvelope(String benchmarkName, double calculated,
      double lowerBound, double upperBound) {
    assertTrue(Double.isFinite(calculated), benchmarkName + " should be finite");
    assertTrue(calculated >= lowerBound,
        benchmarkName + " below reference envelope: " + calculated + " < " + lowerBound);
    assertTrue(calculated <= upperBound,
        benchmarkName + " above reference envelope: " + calculated + " > " + upperBound);
  }
}
