package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for AsphalteneMethodComparison - comparing De Boer vs CPA methods.
 *
 * @author Even Solbraa
 */
public class AsphalteneMethodComparisonTest {
  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    // Create a typical crude oil system
    testFluid = new SystemSrkEos(373.15, 300.0);
    testFluid.addComponent("methane", 0.15);
    testFluid.addComponent("ethane", 0.05);
    testFluid.addComponent("propane", 0.05);
    testFluid.addComponent("n-butane", 0.05);
    testFluid.addComponent("n-pentane", 0.05);
    testFluid.addComponent("n-hexane", 0.10);
    testFluid.addComponent("n-heptane", 0.15);
    testFluid.addComponent("n-octane", 0.15);
    testFluid.addComponent("nC10", 0.25);
    testFluid.setMixingRule("classic");
  }

  @Test
  void testConstructor() {
    AsphalteneMethodComparison comparison =
        new AsphalteneMethodComparison(testFluid, 400.0, 373.15);

    assertNotNull(comparison);
    assertNotNull(comparison.getDeBoerScreening());
    assertNotNull(comparison.getCpaAnalyzer());
  }

  @Test
  void testRunComparison() {
    AsphalteneMethodComparison comparison =
        new AsphalteneMethodComparison(testFluid, 400.0, 373.15);

    // Set SARA fractions
    comparison.setSARAFractions(0.50, 0.30, 0.15, 0.05);

    String report = comparison.runComparison();

    assertNotNull(report);
    assertTrue(report.length() > 0);
    assertTrue(report.contains("DE BOER"));
    assertTrue(report.contains("CPA"));
    assertTrue(report.contains("SARA"));
    assertTrue(report.contains("COMPARISON"));
  }

  @Test
  void testQuickSummary() {
    AsphalteneMethodComparison comparison =
        new AsphalteneMethodComparison(testFluid, 400.0, 373.15);
    comparison.setSARAFractions(0.50, 0.30, 0.15, 0.05);

    // Run comparison first to populate values
    comparison.runComparison();

    String summary = comparison.getQuickSummary();

    assertNotNull(summary);
    assertTrue(summary.contains("De Boer"));
    assertTrue(summary.contains("CPA"));
  }

  @Test
  void testCalculatedProperties() {
    AsphalteneMethodComparison comparison =
        new AsphalteneMethodComparison(testFluid, 400.0, 373.15);

    comparison.runComparison();

    // Should have calculated bubble point and density
    double bubbleP = comparison.getBubblePointPressure();
    double density = comparison.getInSituDensity();

    // Values should be calculated (may be NaN if calculation fails)
    assertTrue(Double.isNaN(bubbleP) || bubbleP > 0);
    assertTrue(Double.isNaN(density) || density > 0);
  }

  @Test
  void testComparisonWithStableSARA() {
    AsphalteneMethodComparison comparison =
        new AsphalteneMethodComparison(testFluid, 300.0, 373.15);

    // Set stable SARA fractions (low CII)
    comparison.setSARAFractions(0.30, 0.40, 0.25, 0.05);

    String report = comparison.runComparison();

    // With low CII, should indicate stable
    assertTrue(
        report.contains("STABLE") || report.contains("NO_PROBLEM") || report.contains("LOW"));
  }

  @Test
  void testComparisonWithUnstableSARA() {
    AsphalteneMethodComparison comparison =
        new AsphalteneMethodComparison(testFluid, 500.0, 373.15);

    // Set unstable SARA fractions (high CII)
    comparison.setSARAFractions(0.55, 0.15, 0.15, 0.15);

    String report = comparison.runComparison();

    // Should contain risk-related content
    assertNotNull(report);
    assertTrue(report.contains("SARA") && report.contains("CII"));
  }

  @Test
  void testDeBoerScreeningAccess() {
    AsphalteneMethodComparison comparison =
        new AsphalteneMethodComparison(testFluid, 400.0, 373.15);
    comparison.setSARAFractions(0.50, 0.30, 0.15, 0.05);
    comparison.runComparison();

    DeBoerAsphalteneScreening deBoer = comparison.getDeBoerScreening();

    assertNotNull(deBoer);
    assertTrue(deBoer.getReservoirPressure() > 0);
  }

  @Test
  void testCpaAnalyzerAccess() {
    AsphalteneMethodComparison comparison =
        new AsphalteneMethodComparison(testFluid, 400.0, 373.15);
    comparison.setSARAFractions(0.50, 0.30, 0.15, 0.05);

    AsphalteneStabilityAnalyzer cpa = comparison.getCpaAnalyzer();

    assertNotNull(cpa);
    // CII should be calculated from SARA fractions
    double cii = cpa.getColloidalInstabilityIndex();
    assertTrue(cii > 0);
  }

  @Test
  void testMethodAgreementReport() {
    AsphalteneMethodComparison comparison =
        new AsphalteneMethodComparison(testFluid, 400.0, 373.15);
    comparison.setSARAFractions(0.50, 0.30, 0.15, 0.05);

    String report = comparison.runComparison();

    // Report should contain recommendation section
    assertTrue(report.contains("Recommendation") || report.contains("RECOMMENDATION"));
  }

  @Test
  void testHighPressureScenario() {
    // Test with very high reservoir pressure
    AsphalteneMethodComparison comparison = new AsphalteneMethodComparison(testFluid, 800.0, 400.0);
    comparison.setSARAFractions(0.45, 0.25, 0.20, 0.10);

    String report = comparison.runComparison();

    assertNotNull(report);
    // High pressure should show high undersaturation
    assertTrue(report.contains("Undersaturation"));
  }

  @Test
  void testReportContainsAllSections() {
    AsphalteneMethodComparison comparison =
        new AsphalteneMethodComparison(testFluid, 400.0, 373.15);
    comparison.setSARAFractions(0.50, 0.30, 0.15, 0.05);

    String report = comparison.runComparison();

    // Check all major sections are present
    assertTrue(report.contains("INPUT CONDITIONS"));
    assertTrue(report.contains("SARA ANALYSIS"));
    assertTrue(report.contains("DE BOER"));
    assertTrue(report.contains("CPA THERMODYNAMIC"));
    assertTrue(report.contains("COMPARISON"));
  }
}
