package neqsim.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for EOSComparison cross-model comparison utility.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class EOSComparisonTest extends neqsim.NeqSimTest {

  @Test
  public void testBasicComparison() {
    EOSComparison comp = new EOSComparison();
    comp.addComponent("methane", 0.85);
    comp.addComponent("ethane", 0.10);
    comp.addComponent("propane", 0.05);
    comp.setConditions(273.15 + 25.0, 60.0);
    comp.setEOSTypes(EOSComparison.EOSType.SRK, EOSComparison.EOSType.PR);

    EOSComparison.ComparisonResult result = comp.compare();

    assertNotNull(result);
    assertNotNull(result.getResults());
    assertTrue(result.getResults().size() == 2, "Should have 2 EOS results");

    for (EOSComparison.EOSResult r : result.getResults()) {
      assertTrue(r.isSuccessful(), r.eosType + " should succeed");
      assertTrue(r.density_kgm3 > 0, r.eosType + " density should be positive");
      assertTrue(r.compressibilityFactor > 0, r.eosType + " Z should be positive");
    }
  }

  @Test
  public void testToJson() {
    EOSComparison comp = new EOSComparison();
    comp.addComponent("methane", 0.90);
    comp.addComponent("CO2", 0.10);
    comp.setConditions(273.15 + 40.0, 80.0);
    comp.setEOSTypes(EOSComparison.EOSType.SRK, EOSComparison.EOSType.PR);

    EOSComparison.ComparisonResult result = comp.compare();
    String json = result.toJson();

    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("SRK"), "JSON should reference SRK");
    assertTrue(json.contains("PR"), "JSON should reference PR");
    assertTrue(json.contains("maxDeviations_pct"), "JSON should contain deviations");
  }

  @Test
  public void testMaxDeviation() {
    EOSComparison comp = new EOSComparison();
    comp.addComponent("methane", 0.85);
    comp.addComponent("ethane", 0.10);
    comp.addComponent("propane", 0.05);
    comp.setConditions(273.15 + 25.0, 60.0);
    comp.setEOSTypes(EOSComparison.EOSType.SRK, EOSComparison.EOSType.PR);

    EOSComparison.ComparisonResult result = comp.compare();

    double densityDev = result.getMaxDeviation("density");
    // SRK and PR should give similar but not identical densities
    assertTrue(!Double.isNaN(densityDev), "Density deviation should be computed");
    assertTrue(densityDev >= 0, "Deviation should be non-negative");
    assertTrue(densityDev < 20, "SRK vs PR density deviation should be < 20%");
  }

  @Test
  public void testGetResultByType() {
    EOSComparison comp = new EOSComparison();
    comp.addComponent("methane", 1.0);
    comp.setConditions(273.15 + 25.0, 50.0);
    comp.setEOSTypes(EOSComparison.EOSType.SRK, EOSComparison.EOSType.PR);

    EOSComparison.ComparisonResult result = comp.compare();

    EOSComparison.EOSResult srkResult = result.getResult(EOSComparison.EOSType.SRK);
    EOSComparison.EOSResult prResult = result.getResult(EOSComparison.EOSType.PR);

    assertNotNull(srkResult, "Should find SRK result");
    assertNotNull(prResult, "Should find PR result");

    // Pure methane Z-factor should be similar between models
    assertTrue(Math.abs(srkResult.compressibilityFactor - prResult.compressibilityFactor) < 0.05,
        "Z-factor deviation for pure CH4 should be small");
  }

  @Test
  public void testThreeModels() {
    EOSComparison comp = new EOSComparison();
    comp.addComponent("methane", 0.85);
    comp.addComponent("ethane", 0.10);
    comp.addComponent("propane", 0.05);
    comp.setConditions(273.15 + 30.0, 50.0);
    comp.setEOSTypes(EOSComparison.EOSType.SRK, EOSComparison.EOSType.PR,
        EOSComparison.EOSType.GERG2008);

    EOSComparison.ComparisonResult result = comp.compare();

    // GERG2008 should succeed for these components
    int successCount = 0;
    for (EOSComparison.EOSResult r : result.getResults()) {
      if (r.isSuccessful()) {
        successCount++;
      }
    }
    assertTrue(successCount >= 2, "At least 2 models should succeed");
  }

  @Test
  public void testMultiPhaseCheck() {
    EOSComparison comp = new EOSComparison();
    comp.addComponent("methane", 0.50);
    comp.addComponent("n-hexane", 0.50);
    comp.setConditions(273.15 + 25.0, 50.0);
    comp.setMultiPhaseCheck(true);
    comp.setEOSTypes(EOSComparison.EOSType.SRK, EOSComparison.EOSType.PR);

    EOSComparison.ComparisonResult result = comp.compare();

    assertNotNull(result);
    for (EOSComparison.EOSResult r : result.getResults()) {
      assertTrue(r.isSuccessful(), r.eosType + " should succeed with multi-phase check");
    }
  }
}
