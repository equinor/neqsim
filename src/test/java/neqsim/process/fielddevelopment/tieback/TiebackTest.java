package neqsim.process.fielddevelopment.tieback;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.concept.ReservoirInput;
import neqsim.process.fielddevelopment.concept.WellsInput;

/**
 * Unit tests for the tieback analysis package.
 *
 * @author ESOL
 * @version 1.0
 */
class TiebackTest {

  private FieldConcept gasTieback;
  private FieldConcept oilTieback;
  private List<HostFacility> hosts;
  private TiebackAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    // Create gas tieback concept using builder pattern
    gasTieback = FieldConcept.builder("Marginal Gas Field")
        .reservoir(ReservoirInput.leanGas().co2Percent(1.5).h2sPercent(0.0).build())
        .wells(WellsInput.builder().producerCount(2).ratePerWell(1.5e6, "Sm3/d").build())
        .infrastructure(InfrastructureInput.builder().tiebackLength(25.0).build()).build();

    // Create oil tieback concept using builder pattern
    oilTieback = FieldConcept.builder("Small Oil Field")
        .reservoir(ReservoirInput.blackOil().waterCut(0.1).build())
        .wells(WellsInput.builder().producerCount(3).ratePerWell(5000.0, "bbl/d").build())
        .infrastructure(InfrastructureInput.builder().tiebackLength(30.0).build()).build();

    // Create host facilities
    hosts = new ArrayList<HostFacility>();

    hosts.add(
        HostFacility.builder("Platform A").location(61.5, 2.3).waterDepth(120).spareGasCapacity(5.0) // MSm3/d
            .spareOilCapacity(20000) // bbl/d
            .minTieInPressure(80).build());

    hosts.add(HostFacility.builder("FPSO B").location(61.8, 2.1).waterDepth(350)
        .spareGasCapacity(8.0).spareOilCapacity(50000).build());

    hosts.add(
        HostFacility.builder("Platform C").location(62.0, 2.5).waterDepth(90).spareGasCapacity(2.0) // Limited
                                                                                                    // capacity
            .build());

    // Create analyzer
    analyzer = new TiebackAnalyzer();
  }

  // ============================================================================
  // HOST FACILITY TESTS
  // ============================================================================

  @Test
  void testHostFacilityBuilder() {
    HostFacility host = HostFacility.builder("Test Platform").location(60.0, 3.0).waterDepth(150)
        .spareGasCapacity(10.0).spareOilCapacity(30000).waterCapacity(50000).minTieInPressure(100)
        .build();

    assertEquals("Test Platform", host.getName());
    assertEquals(60.0, host.getLatitude(), 0.001);
    assertEquals(3.0, host.getLongitude(), 0.001);
    assertEquals(150, host.getWaterDepthM());
    assertEquals(10.0, host.getSpareGasCapacity(), 0.001);
    assertEquals(30000, host.getSpareOilCapacity(), 0.001);
    assertEquals(100, host.getMinTieInPressureBara());
  }

  @Test
  void testHostFacilityCapacityCheck() {
    HostFacility host =
        HostFacility.builder("Test").spareGasCapacity(5.0).spareOilCapacity(20000).build();

    assertTrue(host.canAcceptGasRate(4.0));
    assertFalse(host.canAcceptGasRate(6.0));
    assertTrue(host.canAcceptOilRate(15000));
    assertFalse(host.canAcceptOilRate(25000));
  }

  @Test
  void testDistanceCalculation() {
    HostFacility host = HostFacility.builder("North Sea Platform").location(61.5, 2.3).build();

    // Distance to a point about 25 km away
    double distance = host.distanceToKm(61.7, 2.5);
    assertTrue(distance > 20 && distance < 35, "Distance should be ~25-30 km");

    // Distance to same location
    double zeroDistance = host.distanceToKm(61.5, 2.3);
    assertEquals(0.0, zeroDistance, 0.1);
  }

  // ============================================================================
  // TIEBACK OPTION TESTS
  // ============================================================================

  @Test
  void testTiebackOptionCreation() {
    TiebackOption option = new TiebackOption("Discovery X", "Platform A");

    assertEquals("Discovery X", option.getDiscoveryName());
    assertEquals("Platform A", option.getHostName());
    assertTrue(option.isFeasible()); // Default is feasible
  }

  @Test
  void testTiebackOptionCapexCalculation() {
    TiebackOption option = new TiebackOption("Test", "Host");
    option.setSubseaCapexMusd(60.0);
    option.setPipelineCapexMusd(75.0);
    option.setUmbilicalCapexMusd(25.0);
    option.setDrillingCapexMusd(160.0);
    option.setHostModificationCapexMusd(40.0);

    option.calculateTotalCapex();

    assertEquals(360.0, option.getTotalCapexMusd(), 0.001);
  }

  @Test
  void testTiebackOptionComparable() {
    TiebackOption opt1 = new TiebackOption("Test", "Host A");
    opt1.setNpvMusd(100.0);

    TiebackOption opt2 = new TiebackOption("Test", "Host B");
    opt2.setNpvMusd(200.0);

    TiebackOption opt3 = new TiebackOption("Test", "Host C");
    opt3.setNpvMusd(150.0);

    List<TiebackOption> options = new ArrayList<TiebackOption>();
    options.add(opt1);
    options.add(opt2);
    options.add(opt3);

    java.util.Collections.sort(options);

    // Should be sorted by NPV, highest first
    assertEquals("Host B", options.get(0).getHostName());
    assertEquals("Host C", options.get(1).getHostName());
    assertEquals("Host A", options.get(2).getHostName());
  }

  // ============================================================================
  // TIEBACK ANALYZER TESTS
  // ============================================================================

  @Test
  void testGasTiebackAnalysis() {
    TiebackReport report = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);

    assertNotNull(report);
    assertEquals("Marginal Gas Field", report.getDiscoveryName());
    assertEquals(3, report.getOptionCount());
    assertTrue(report.hasFeasibleOption());
  }

  @Test
  void testSingleTiebackEvaluation() {
    TiebackOption option = analyzer.evaluateSingleTieback(gasTieback, hosts.get(0), 61.6, 2.4);

    assertNotNull(option);
    assertEquals("Marginal Gas Field", option.getDiscoveryName());
    assertEquals("Platform A", option.getHostName());
    assertTrue(option.getDistanceKm() > 0);
    assertTrue(option.getTotalCapexMusd() > 0);
  }

  @Test
  void testCapacityConstraintEnforcement() {
    // Platform C has only 2 MSm3/d capacity, but we need 3 MSm3/d
    HostFacility limitedHost = hosts.get(2);

    TiebackOption option = analyzer.evaluateSingleTieback(gasTieback, limitedHost, 61.6, 2.4);

    // Should be marked infeasible due to capacity
    assertFalse(option.isFeasible());
    assertTrue(option.getInfeasibilityReason().contains("capacity"));
  }

  @Test
  void testDistanceLimitEnforcement() {
    analyzer.setMaxTiebackDistanceKm(10.0); // Very short max distance

    TiebackReport report = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);

    // All hosts should be too far
    assertEquals(0, report.getFeasibleOptionCount());
  }

  @Test
  void testNpvCalculation() {
    TiebackReport report = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);

    TiebackOption best = report.getBestFeasibleOption();
    assertNotNull(best);
    // NPV should be calculated (can be positive or negative)
    assertTrue(Double.isFinite(best.getNpvMusd()));
    assertTrue(best.getTotalCapexMusd() > 0);
  }

  @Test
  void testPriceAssumptionImpact() {
    // Low gas price
    analyzer.setGasPriceUsdPerSm3(0.10);
    TiebackReport lowPriceReport = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);
    double lowPriceNpv = lowPriceReport.getBestFeasibleOption().getNpvMusd();

    // High gas price
    analyzer.setGasPriceUsdPerSm3(0.40);
    TiebackReport highPriceReport = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);
    double highPriceNpv = highPriceReport.getBestFeasibleOption().getNpvMusd();

    // Higher price should give higher NPV
    assertTrue(highPriceNpv > lowPriceNpv);
  }

  // ============================================================================
  // TIEBACK REPORT TESTS
  // ============================================================================

  @Test
  void testReportRanking() {
    TiebackReport report = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);

    List<TiebackOption> feasible = report.getFeasibleOptions();
    if (feasible.size() > 1) {
      // Should be sorted by NPV, highest first
      for (int i = 0; i < feasible.size() - 1; i++) {
        assertTrue(feasible.get(i).getNpvMusd() >= feasible.get(i + 1).getNpvMusd());
      }
    }
  }

  @Test
  void testReportSummary() {
    TiebackReport report = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);

    String summary = report.getSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("TIEBACK ANALYSIS REPORT"));
    assertTrue(summary.contains("Marginal Gas Field"));
  }

  @Test
  void testReportRecommendation() {
    TiebackReport report = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);

    String recommendation = report.getRecommendation();
    assertNotNull(recommendation);
    assertTrue(recommendation.length() > 10);
  }

  @Test
  void testCsvExport() {
    TiebackReport report = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);

    String csv = report.toCsv();
    assertNotNull(csv);
    assertTrue(csv.contains("Host"));
    assertTrue(csv.contains("NPV_MUSD"));
  }

  @Test
  void testMarkdownTableExport() {
    TiebackReport report = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);

    String md = report.toMarkdownTable();
    assertNotNull(md);
    assertTrue(md.contains("|"));
    assertTrue(md.contains("Host"));
  }

  @Test
  void testOptionComparison() {
    TiebackReport report = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);

    String comparison = report.compareOptions("Platform A", "FPSO B");
    assertNotNull(comparison);
    assertTrue(comparison.contains("Distance"));
    assertTrue(comparison.contains("CAPEX"));
    assertTrue(comparison.contains("NPV"));
  }

  @Test
  void testNpvRange() {
    TiebackReport report = analyzer.analyze(gasTieback, hosts, 61.6, 2.4);

    double[] range = report.getNpvRange();
    assertNotNull(range);
    assertEquals(2, range.length);
    assertTrue(range[0] <= range[1]);
  }

  // ============================================================================
  // OIL FIELD TESTS
  // ============================================================================

  @Test
  void testOilTiebackAnalysis() {
    TiebackReport report = analyzer.analyze(oilTieback, hosts, 61.6, 2.4);

    assertNotNull(report);
    assertEquals("Small Oil Field", report.getDiscoveryName());
    assertTrue(report.getOptionCount() > 0);
  }

  // ============================================================================
  // EDGE CASES
  // ============================================================================

  @Test
  void testEmptyHostList() {
    List<HostFacility> emptyHosts = new ArrayList<HostFacility>();
    TiebackReport report = analyzer.analyze(gasTieback, emptyHosts, 61.6, 2.4);

    assertNotNull(report);
    assertEquals(0, report.getOptionCount());
    assertNull(report.getBestOption());
    assertFalse(report.hasFeasibleOption());
  }

  @Test
  void testAnalyzerParameterSetters() {
    analyzer.setDiscountRate(0.10);
    assertEquals(0.10, analyzer.getDiscountRate(), 0.001);

    analyzer.setGasPriceUsdPerSm3(0.30);
    assertEquals(0.30, analyzer.getGasPriceUsdPerSm3(), 0.001);

    analyzer.setOilPriceUsdPerBbl(80.0);
    assertEquals(80.0, analyzer.getOilPriceUsdPerBbl(), 0.001);

    analyzer.setSubseaTreeCostMusd(30.0);
    assertEquals(30.0, analyzer.getSubseaTreeCostMusd(), 0.001);

    analyzer.setPipelineCostPerKmMusd(3.0);
    assertEquals(3.0, analyzer.getPipelineCostPerKmMusd(), 0.001);
  }
}
