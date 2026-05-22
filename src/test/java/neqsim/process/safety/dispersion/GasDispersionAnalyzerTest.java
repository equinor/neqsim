package neqsim.process.safety.dispersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.safety.BoundaryConditions;
import neqsim.process.safety.release.LeakModel;
import neqsim.process.safety.release.SourceTermResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class GasDispersionAnalyzerTest {

  @Test
  void methaneReleaseGivesGaussianFlammableDistances() {
    GasDispersionResult result =
        GasDispersionAnalyzer.builder().scenarioName("Methane leak").fluid(methaneFluid(20.0))
            .massReleaseRate(1.0).boundaryConditions(standardWeather()).build().analyze();

    assertEquals("GAUSSIAN_PLUME", result.getSelectedModel());
    assertTrue(result.getDistanceToLflM() > 0.0);
    assertTrue(result.getDistanceToHalfLflM() > result.getDistanceToLflM());
    assertTrue(result.getFlammableCloudVolumeM3() > 0.0);
    assertTrue(result.hasFlammableCloud());
    assertTrue(result.toJson().contains("Methane leak"));
  }

  @Test
  void sts0131IntegralEndpointUsesTwentyPercentLfl() {
    GasDispersionResult result =
        GasDispersionAnalyzer.builder().scenarioName("Methane integral endpoint")
            .fluid(methaneFluid(20.0)).massReleaseRate(1.0).boundaryConditions(standardWeather())
            .sts0131IntegralEndpoint().build().analyze();

    assertEquals(0.20, result.getFlammableEndpointFractionOfLfl(), 1.0e-12);
    assertTrue(result.getDistanceToFlammableEndpointM() > result.getDistanceToHalfLflM());
    assertTrue(result.toJson().contains("distanceToFlammableEndpoint_m"));
  }

  @Test
  void carbonDioxideReleaseSelectsDenseGasAndNoFlammableCloud() {
    GasDispersionResult result =
        GasDispersionAnalyzer.builder().scenarioName("CO2 leak").fluid(carbonDioxideFluid())
            .massReleaseRate(5.0).boundaryConditions(standardWeather()).build().analyze();

    assertEquals("HEAVY_GAS", result.getSelectedModel());
    assertFalse(result.hasFlammableCloud());
    assertTrue(result.getSourceDensityKgPerM3() > result.getAirDensityKgPerM3());
  }

  @Test
  void h2sEndpointGivesToxicDistance() {
    SystemInterface sourGas = new SystemSrkEos(293.15, 20.0);
    sourGas.addComponent("methane", 0.98);
    sourGas.addComponent("H2S", 0.02);
    sourGas.setMixingRule("classic");

    GasDispersionResult result = GasDispersionAnalyzer.builder().scenarioName("Sour gas leak")
        .fluid(sourGas).massReleaseRate(2.0).boundaryConditions(standardWeather())
        .toxicEndpoint("H2S", 100.0).build().analyze();

    assertTrue(result.hasToxicEndpoint());
    assertTrue(result.getToxicDistanceM() > 0.0);
    assertEquals("H2S", result.getToxicComponentName());
  }

  @Test
  void sourceTermPeakRateFeedsDispersionScreening() {
    SystemInterface highPressureMethane = methaneFluid(50.0);
    LeakModel leak = LeakModel.builder().fluid(highPressureMethane).holeDiameter(0.01)
        .vesselVolume(1.0).scenarioName("Dynamic leak").build();
    SourceTermResult sourceTerm = leak.calculateSourceTerm(5.0, 1.0);

    GasDispersionResult result =
        GasDispersionAnalyzer.builder().scenarioName("Dynamic screen").fluid(highPressureMethane)
            .sourceTerm(sourceTerm).boundaryConditions(standardWeather()).build().analyze();

    assertEquals(sourceTerm.getPeakMassFlowRate(), result.getMassReleaseRateKgPerS(), 1.0e-12);
    assertTrue(result.getDistanceToLflM() > 0.0);
  }

  @Test
  void processStreamConvenienceUsesStreamMassRate() {
    Stream stream = new Stream("release stream", methaneFluid(10.0));
    stream.setFlowRate(3600.0, "kg/hr");
    stream.run();

    GasDispersionResult result =
        GasDispersionAnalyzer.analyzeStream("Stream leak", stream, standardWeather());

    assertEquals(1.0, result.getMassReleaseRateKgPerS(), 1.0e-9);
    assertTrue(result.getDistanceToLflM() > 0.0);
  }

  @Test
  void methaneLowerFlammableLimitCanBeCalculatedFromFluid() {
    assertEquals(0.044, GasDispersionAnalyzer.lowerFlammableLimit(methaneFluid(20.0)), 1.0e-12);
  }

  private static BoundaryConditions standardWeather() {
    return BoundaryConditions.builder().ambientTemperature(15.0, "C").windSpeed(5.0)
        .pasquillStabilityClass('D').isOffshore(false).surfaceRoughness(0.1).build();
  }

  private static SystemInterface methaneFluid(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(293.15, pressureBara);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static SystemInterface carbonDioxideFluid() {
    SystemInterface fluid = new SystemSrkEos(293.15, 20.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }
}
