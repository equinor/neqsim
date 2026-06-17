package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SustainabilityMetrics}.
 */
class SustainabilityMetricsTest {

  @Test
  void testBasicCarbonIntensity() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setBiogasProductionNm3PerYear(4_000_000.0);
    metrics.setMethaneContentFraction(0.60);
    metrics.setElectricityProductionMWhPerYear(8000.0);
    metrics.setHeatProductionMWhPerYear(10000.0);
    metrics.setParasiticElectricityMWhPerYear(1200.0);
    metrics.setMethaneSlipPercent(1.5);
    metrics.calculate();

    assertTrue(metrics.isCalculated());
    assertTrue(metrics.getCarbonIntensityKgCO2PerMWh() > 0, "Carbon intensity should be positive");
    assertTrue(metrics.getNetEnergyProductionMWhPerYear() > 0, "Net energy should be positive");
  }

  @Test
  void testFossilFuelDisplacement() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setElectricityProductionMWhPerYear(10000.0);
    metrics.setHeatProductionMWhPerYear(12000.0);
    metrics.setParasiticElectricityMWhPerYear(1000.0);
    metrics.setFossilReferenceEmissionFactor(0.450);
    metrics.setFossilHeatEmissionFactor(0.250);
    metrics.calculate();

    assertTrue(metrics.getFossilFuelDisplacementTCO2PerYear() > 0,
        "Fossil displacement should be positive");
    // Net electricity = 9000, net heat = 12000
    // Displaced: 9000 * 0.450 + 12000 * 0.250 = 4050 + 3000 = 7050 tCO2
    assertEquals(7050.0, metrics.getFossilFuelDisplacementTCO2PerYear(), 0.1);
  }

  @Test
  void testBiomethaneGridInjection() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setBiomethaneProductionNm3PerYear(2_000_000.0);
    metrics.setFossilGasEmissionFactor(2.0);
    metrics.calculate();

    // Displaced gas = 2e6 * 2.0 / 1000 = 4000 tCO2
    assertEquals(4000.0, metrics.getFossilFuelDisplacementTCO2PerYear(), 0.1);
  }

  @Test
  void testRenewableEnergyFraction() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setBiogasProductionNm3PerYear(1_000_000.0);
    metrics.setMethaneContentFraction(0.60);
    metrics.setImportedElectricityMWhPerYear(0.0); // 100% renewable
    metrics.calculate();

    assertEquals(1.0, metrics.getRenewableEnergyFraction(), 1e-6,
        "Should be 100% renewable when no grid import");
  }

  @Test
  void testRenewableEnergyFractionWithGridImport() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setBiogasProductionNm3PerYear(1_000_000.0);
    metrics.setMethaneContentFraction(0.60);
    // Methane energy = 600000 * 9.97/1000 = 5982 MWh
    metrics.setImportedElectricityMWhPerYear(5982.0); // equal to bio energy
    metrics.calculate();

    assertEquals(0.5, metrics.getRenewableEnergyFraction(), 0.01,
        "Should be ~50% renewable with equal grid import");
  }

  @Test
  void testEROI() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setElectricityProductionMWhPerYear(10000.0);
    metrics.setHeatProductionMWhPerYear(12000.0);
    metrics.setParasiticElectricityMWhPerYear(2000.0);
    metrics.setParasiticHeatMWhPerYear(1000.0);
    metrics.calculate();

    // EROI = (10000 + 12000) / (2000 + 1000) = 22000 / 3000 = 7.33
    assertEquals(7.33, metrics.getEnergyReturnOnInvestment(), 0.01);
  }

  @Test
  void testCustomEmissions() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setElectricityProductionMWhPerYear(5000.0);
    metrics.setParasiticElectricityMWhPerYear(500.0);
    metrics.addEmission(SustainabilityMetrics.EmissionSource.CUSTOM, "Chemical additive", 10.0);
    metrics.addEmission(SustainabilityMetrics.EmissionSource.FLARING, "Emergency flare", 5.0);
    metrics.calculate();

    assertTrue(metrics.getTotalEmissionsTCO2eqPerYear() >= 15.0,
        "Custom emissions should be included in total");
  }

  @Test
  void testNetCarbonBalance() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setElectricityProductionMWhPerYear(10000.0);
    metrics.setParasiticElectricityMWhPerYear(1000.0);
    metrics.setFossilReferenceEmissionFactor(0.450);
    metrics.setBiogasProductionNm3PerYear(1_000_000.0);
    metrics.setMethaneContentFraction(0.60);
    metrics.setMethaneSlipPercent(1.0);
    metrics.calculate();

    // Net carbon should be negative (savings) when fossil displacement exceeds emissions
    double net = metrics.getNetCarbonBalanceTCO2PerYear();
    // Emissions primarily from methane slip
    // Fossil displacement from 9000 MWh net electricity * 0.450
    assertTrue(net < 0, "Net carbon should be negative (net saving)");
  }

  @Test
  void testGetResults() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setBiogasProductionNm3PerYear(1_000_000.0);
    metrics.setElectricityProductionMWhPerYear(5000.0);
    metrics.calculate();

    Map<String, Object> results = metrics.getResults();
    assertTrue(results.containsKey("totalEmissions_tCO2eq_per_year"));
    assertTrue(results.containsKey("carbonIntensity_kgCO2eq_per_MWh"));
    assertTrue(results.containsKey("renewableEnergyFraction"));
    assertTrue(results.containsKey("inputs"));
  }

  @Test
  void testToJson() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setBiogasProductionNm3PerYear(1_000_000.0);
    metrics.calculate();

    String json = metrics.toJson();
    assertNotNull(json);
    assertTrue(json.contains("totalEmissions_tCO2eq_per_year"));
    assertTrue(json.contains("renewableEnergyFraction"));
  }

  @Test
  void testTransportEmissions() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setFeedstockTransport(50.0, 10000.0);
    metrics.setTransportEmissionFactor(0.062);
    metrics.setElectricityProductionMWhPerYear(5000.0);
    metrics.setParasiticElectricityMWhPerYear(500.0);
    metrics.calculate();

    // Transport = 50 * 10000 * 0.062 * 2 / 1000 = 62 tCO2
    assertTrue(metrics.getTotalEmissionsTCO2eqPerYear() >= 60.0,
        "Transport emissions should contribute to total");
  }

  @Test
  void testN2OEmissions() {
    SustainabilityMetrics metrics = new SustainabilityMetrics();
    metrics.setDigestateNitrogenKgPerYear(5000.0);
    metrics.setN2OEmissionFraction(0.01);
    metrics.setElectricityProductionMWhPerYear(5000.0);
    metrics.setParasiticElectricityMWhPerYear(500.0);
    metrics.calculate();

    // N2O = 5000 * 0.01 * (44/28) * 273 / 1000 = 21.45 tCO2eq
    assertTrue(metrics.getTotalEmissionsTCO2eqPerYear() > 20.0,
        "N2O emissions should be significant");
  }
}
