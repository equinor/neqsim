package neqsim.process.fielddevelopment.economics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the country-independent tax model framework.
 *
 * <p>
 * This test class verifies the functionality of the TaxModelRegistry, GenericTaxModel, and
 * FiscalParameters classes that provide country-independent fiscal calculations.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class TaxModelTest {

  private static final double TOLERANCE = 0.0001;

  @BeforeEach
  void setUp() {
    // Reset any state if needed
  }

  // ============================================================================
  // REGISTRY TESTS
  // ============================================================================

  @Test
  @DisplayName("Registry contains predefined countries")
  void testRegistryContainsCountries() {
    List<String> countries = TaxModelRegistry.getAvailableCountries();
    assertNotNull(countries);
    assertTrue(countries.size() >= 10, "Should have at least 10 predefined countries");

    // Check some key countries
    assertTrue(TaxModelRegistry.isRegistered("NO"), "Norway should be registered");
    assertTrue(TaxModelRegistry.isRegistered("UK"), "UK should be registered");
    assertTrue(TaxModelRegistry.isRegistered("US-GOM"), "US-GOM should be registered");
    assertTrue(TaxModelRegistry.isRegistered("BR"), "Brazil should be registered");
  }

  @Test
  @DisplayName("Get Norway fiscal parameters")
  void testGetNorwayParameters() {
    FiscalParameters params = TaxModelRegistry.getParameters("NO");
    assertNotNull(params);
    assertEquals("NO", params.getCountryCode());
    assertEquals("Norway", params.getCountryName());
    assertEquals(0.22, params.getCorporateTaxRate(), TOLERANCE);
    assertEquals(0.56, params.getResourceTaxRate(), TOLERANCE);
    assertEquals(0.78, params.getTotalMarginalTaxRate(), TOLERANCE);
    assertEquals(0.0, params.getRoyaltyRate(), TOLERANCE);
    assertEquals(FiscalParameters.FiscalSystemType.CONCESSIONARY, params.getFiscalSystemType());
  }

  @Test
  @DisplayName("Get UK fiscal parameters")
  void testGetUKParameters() {
    FiscalParameters params = TaxModelRegistry.getParameters("UK");
    assertNotNull(params);
    assertEquals("UK", params.getCountryCode());
    assertEquals(0.30, params.getCorporateTaxRate(), TOLERANCE);
    assertEquals(0.10, params.getResourceTaxRate(), TOLERANCE);
    assertEquals(FiscalParameters.RingFenceLevel.LICENSE, params.getRingFenceLevel());
  }

  @Test
  @DisplayName("Get Brazil concession parameters")
  void testGetBrazilConcessionParameters() {
    FiscalParameters params = TaxModelRegistry.getParameters("BR");
    assertNotNull(params);
    assertEquals("BR", params.getCountryCode());
    assertEquals("Brazil", params.getCountryName());
    assertEquals(FiscalParameters.FiscalSystemType.CONCESSIONARY, params.getFiscalSystemType());
    assertEquals(0.34, params.getCorporateTaxRate(), TOLERANCE); // IRPJ + CSLL
    assertEquals(0.10, params.getRoyaltyRate(), TOLERANCE);
    assertEquals(FiscalParameters.RingFenceLevel.FIELD, params.getRingFenceLevel());
  }

  @Test
  @DisplayName("Get Brazil pre-salt PSA parameters")
  void testGetBrazilPreSaltPSAParameters() {
    FiscalParameters params = TaxModelRegistry.getParameters("BR-PSA");
    assertNotNull(params);
    assertEquals("BR-PSA", params.getCountryCode());
    assertEquals("Brazil - Pre-Salt PSA", params.getCountryName());
    assertEquals(FiscalParameters.FiscalSystemType.PSC, params.getFiscalSystemType());
    assertEquals(0.34, params.getCorporateTaxRate(), TOLERANCE);
    assertEquals(0.15, params.getRoyaltyRate(), TOLERANCE); // Higher royalty for pre-salt
    assertEquals(0.50, params.getCostRecoveryLimit(), TOLERANCE);
    assertEquals(0.60, params.getProfitShareGovernment(), TOLERANCE);
    assertEquals(0.40, params.getProfitShareContractor(), TOLERANCE);
    assertEquals(0.30, params.getStateParticipation(), TOLERANCE); // Petrobras minimum
  }

  @Test
  @DisplayName("Get Brazil deep water parameters")
  void testGetBrazilDeepWaterParameters() {
    FiscalParameters params = TaxModelRegistry.getParameters("BR-DW");
    assertNotNull(params);
    assertEquals("BR-DW", params.getCountryCode());
    assertEquals("Brazil - Deep Water Concession", params.getCountryName());
    assertEquals(FiscalParameters.FiscalSystemType.CONCESSIONARY, params.getFiscalSystemType());
    assertEquals(0.34, params.getCorporateTaxRate(), TOLERANCE);
    assertEquals(0.25, params.getResourceTaxRate(), TOLERANCE); // Special participation
    assertEquals(0.15, params.getRoyaltyRate(), TOLERANCE);
  }

  @Test
  @DisplayName("Get Angola PSC parameters")
  void testGetAngolaPSCParameters() {
    FiscalParameters params = TaxModelRegistry.getParameters("AO");
    assertNotNull(params);
    assertEquals(FiscalParameters.FiscalSystemType.PSC, params.getFiscalSystemType());
    assertEquals(0.50, params.getCostRecoveryLimit(), TOLERANCE);
    assertEquals(0.60, params.getProfitShareGovernment(), TOLERANCE);
    assertEquals(0.40, params.getProfitShareContractor(), TOLERANCE);
  }

  @Test
  @DisplayName("Registry is case-insensitive")
  void testRegistryCaseInsensitive() {
    FiscalParameters lower = TaxModelRegistry.getParameters("no");
    FiscalParameters upper = TaxModelRegistry.getParameters("NO");
    FiscalParameters mixed = TaxModelRegistry.getParameters("No");

    assertNotNull(lower);
    assertNotNull(upper);
    assertNotNull(mixed);
    assertEquals(lower.getCountryCode(), upper.getCountryCode());
    assertEquals(lower.getCountryCode(), mixed.getCountryCode());
  }

  @Test
  @DisplayName("Unknown country returns null")
  void testUnknownCountryReturnsNull() {
    FiscalParameters params = TaxModelRegistry.getParameters("UNKNOWN");
    assertEquals(null, params);
  }

  // ============================================================================
  // TAX MODEL CREATION TESTS
  // ============================================================================

  @Test
  @DisplayName("Create tax model for Norway")
  void testCreateNorwayModel() {
    TaxModel model = TaxModelRegistry.createModel("NO");
    assertNotNull(model);
    assertEquals("Norway", model.getCountryName());
    assertEquals("NO", model.getCountryCode());
    assertEquals(0.78, model.getTotalMarginalTaxRate(), TOLERANCE);
  }

  @Test
  @DisplayName("Create tax model for PSC country")
  void testCreateAngolaModel() {
    TaxModel model = TaxModelRegistry.createModel("AO");
    assertNotNull(model);
    assertEquals("Angola", model.getCountryName());
  }

  @Test
  @DisplayName("Create model for unknown country throws exception")
  void testCreateModelUnknownCountryThrows() {
    assertThrows(IllegalArgumentException.class, () -> TaxModelRegistry.createModel("UNKNOWN"));
  }

  @Test
  @DisplayName("GenericTaxModel forCountry factory method")
  void testGenericTaxModelForCountry() {
    TaxModel model = GenericTaxModel.forCountry("NO");
    assertNotNull(model);
    assertEquals("Norway", model.getCountryName());
  }

  // ============================================================================
  // TAX CALCULATION TESTS - NORWAY
  // ============================================================================

  @Test
  @DisplayName("Norway basic tax calculation")
  void testNorwayBasicTaxCalculation() {
    TaxModel model = TaxModelRegistry.createModel("NO");

    double revenue = 1000000.0; // 1 MUSD
    double opex = 200000.0;
    double depreciation = 100000.0;
    double uplift = 0.0;

    TaxModel.TaxResult result = model.calculateTax(revenue, opex, depreciation, uplift);

    assertNotNull(result);
    assertTrue(result.getTotalTax() > 0);

    // Total tax at 78% marginal rate on (revenue - opex - depreciation)
    // = 1,000,000 - 200,000 - 100,000 = 700,000
    double expectedTax = 700000.0 * 0.78;
    assertEquals(expectedTax, result.getTotalTax(), 1.0);
  }

  @Test
  @DisplayName("Norway with loss carry-forward")
  void testNorwayLossCarryForward() {
    TaxModel model = TaxModelRegistry.createModel("NO");

    // Year 1: Loss (opex > revenue)
    TaxModel.TaxResult result1 = model.calculateTax(100000.0, 200000.0, 0.0, 0.0);
    assertEquals(0.0, result1.getTotalTax(), TOLERANCE);
    assertTrue(model.getLossCarryForward() > 0, "Loss should be carried forward");

    // Loss is carried forward for BOTH corporate (22%) and resource (56%) tax bases
    // Each has a loss of 100000, so total = 200000
    double lossCarried = model.getLossCarryForward();
    assertEquals(200000.0, lossCarried, TOLERANCE);

    // Year 2: Profit - should use loss carry-forward
    TaxModel.TaxResult result2 = model.calculateTax(500000.0, 100000.0, 0.0, 0.0);
    // Profit = 400,000 for each tax base, minus loss cf of 100,000 each = 300,000 taxable each

    // Loss carry-forward should be reduced
    assertEquals(0.0, model.getLossCarryForward(), TOLERANCE);
  }

  // ============================================================================
  // TAX CALCULATION TESTS - UK
  // ============================================================================

  @Test
  @DisplayName("UK basic tax calculation")
  void testUKBasicTaxCalculation() {
    TaxModel model = TaxModelRegistry.createModel("UK");

    TaxModel.TaxResult result = model.calculateTax(1000000.0, 200000.0, 100000.0, 0.0);

    assertNotNull(result);
    // UK: 30% + 10% = 40% marginal
    assertEquals(0.40, model.getTotalMarginalTaxRate(), TOLERANCE);

    // Taxable = 1,000,000 - 200,000 - 100,000 = 700,000
    assertEquals(280000.0, result.getTotalTax(), 1.0);
  }

  // ============================================================================
  // TAX CALCULATION TESTS - BRAZIL
  // ============================================================================

  @Test
  @DisplayName("Brazil concession tax calculation")
  void testBrazilConcessionTaxCalculation() {
    TaxModel model = TaxModelRegistry.createModel("BR");

    double revenue = 1000000.0;
    double opex = 200000.0;
    double depreciation = 100000.0;

    TaxModel.TaxResult result = model.calculateTax(revenue, opex, depreciation, 0.0);

    assertNotNull(result);
    // Brazil concession: 34% corporate tax, 10% royalty
    assertEquals(0.34, model.getTotalMarginalTaxRate(), TOLERANCE);

    // Royalty = 10% of 1,000,000 = 100,000
    double expectedRoyalty = model.calculateRoyalty(revenue);
    assertEquals(100000.0, expectedRoyalty, TOLERANCE);

    // Revenue after royalty = 900,000
    // Taxable = 900,000 - 200,000 - 100,000 = 600,000
    // Corporate tax = 600,000 * 34% = 204,000
    assertEquals(204000.0, result.getTotalTax(), 1.0);
  }

  @Test
  @DisplayName("Brazil pre-salt PSA tax calculation")
  void testBrazilPreSaltPSATaxCalculation() {
    TaxModel model = TaxModelRegistry.createModel("BR-PSA");

    double revenue = 1000000.0;
    double opex = 200000.0;

    TaxModel.TaxResult result = model.calculateTax(revenue, opex, 0.0, 0.0);

    assertNotNull(result);
    // BR-PSA is a PSC with 15% royalty, 50% cost recovery, 60/40 profit split
    assertTrue(result.getTotalTax() > 0);

    // Royalty = 15% of 1,000,000 = 150,000
    double expectedRoyalty = model.calculateRoyalty(revenue);
    assertEquals(150000.0, expectedRoyalty, TOLERANCE);
  }

  @Test
  @DisplayName("Brazil deep water with special participation")
  void testBrazilDeepWaterTaxCalculation() {
    TaxModel model = TaxModelRegistry.createModel("BR-DW");

    double revenue = 1000000.0;
    double opex = 200000.0;
    double depreciation = 100000.0;

    TaxModel.TaxResult result = model.calculateTax(revenue, opex, depreciation, 0.0);

    assertNotNull(result);
    // BR-DW: 34% corporate + 25% special participation = 59% marginal
    assertEquals(0.59, model.getTotalMarginalTaxRate(), TOLERANCE);

    // Royalty = 15% of 1,000,000 = 150,000
    double expectedRoyalty = model.calculateRoyalty(revenue);
    assertEquals(150000.0, expectedRoyalty, TOLERANCE);

    // Higher total tax due to special participation
    assertTrue(result.getTotalTax() > 300000.0,
        "Should have significant tax with special participation");
  }

  // ============================================================================
  // TAX CALCULATION TESTS - US GOM
  // ============================================================================

  @Test
  @DisplayName("US GOM with royalty calculation")
  void testUSGOMRoyaltyCalculation() {
    TaxModel model = TaxModelRegistry.createModel("US-GOM");

    double revenue = 1000000.0;
    double royalty = model.calculateRoyalty(revenue);

    // US-GOM: 18.75% royalty
    assertEquals(187500.0, royalty, TOLERANCE);
  }

  // ============================================================================
  // TAX CALCULATION TESTS - ANGOLA PSC
  // ============================================================================

  @Test
  @DisplayName("Angola PSC profit sharing calculation")
  void testAngolaPSCProfitSharing() {
    TaxModel model = TaxModelRegistry.createModel("AO");

    double revenue = 1000000.0;
    double opex = 200000.0;

    TaxModel.TaxResult result = model.calculateTax(revenue, opex, 0.0, 0.0);
    assertNotNull(result);

    // For PSC, total tax includes government profit share
    assertTrue(result.getTotalTax() > 0);
  }

  // ============================================================================
  // DEPRECIATION TESTS
  // ============================================================================

  @Test
  @DisplayName("Norway straight-line depreciation over 6 years")
  void testNorwayDepreciation() {
    TaxModel model = TaxModelRegistry.createModel("NO");

    double capex = 600000.0; // 600k investment

    // Year 1 depreciation = 600,000 / 6 = 100,000
    double dep = model.calculateDepreciation(capex, 1);
    assertEquals(100000.0, dep, TOLERANCE);

    // Year 6
    dep = model.calculateDepreciation(capex, 6);
    assertEquals(100000.0, dep, TOLERANCE);

    // Year 7 - fully depreciated
    dep = model.calculateDepreciation(capex, 7);
    assertEquals(0.0, dep, TOLERANCE);
  }

  @Test
  @DisplayName("Canada declining balance depreciation")
  void testCanadaDepreciation() {
    TaxModel model = TaxModelRegistry.createModel("CA-AB");

    double capex = 1000000.0;

    // Year 1: 25% of 1,000,000 = 250,000
    double dep1 = model.calculateDepreciation(capex, 1);
    assertEquals(250000.0, dep1, TOLERANCE);

    // Year 2: 25% of (1,000,000 - 250,000) = 187,500
    double dep2 = model.calculateDepreciation(capex, 2);
    assertEquals(187500.0, dep2, TOLERANCE);

    // Year 3: 25% of 562,500 = 140,625
    double dep3 = model.calculateDepreciation(capex, 3);
    assertEquals(140625.0, dep3, TOLERANCE);
  }

  // ============================================================================
  // UPLIFT TESTS
  // ============================================================================

  @Test
  @DisplayName("Norway uplift calculation")
  void testNorwayUplift() {
    TaxModel model = TaxModelRegistry.createModel("NO");

    double capex = 1000000.0;

    // Norway: 5.5% for 4 years
    double uplift1 = model.calculateUplift(capex, 1);
    assertEquals(55000.0, uplift1, TOLERANCE);

    double uplift4 = model.calculateUplift(capex, 4);
    assertEquals(55000.0, uplift4, TOLERANCE);

    // Year 5: no more uplift
    double uplift5 = model.calculateUplift(capex, 5);
    assertEquals(0.0, uplift5, TOLERANCE);
  }

  // ============================================================================
  // CUSTOM PARAMETERS TESTS
  // ============================================================================

  @Test
  @DisplayName("Register and use custom parameters")
  void testCustomParameters() {
    // Create custom parameters
    FiscalParameters custom = FiscalParameters.builder("TEST-CUSTOM").countryName("Test Country")
        .description("Custom test parameters").corporateTaxRate(0.25).resourceTaxRate(0.15)
        .royaltyRate(0.12).depreciation(FiscalParameters.DepreciationMethod.STRAIGHT_LINE, 5)
        .build();

    // Register
    TaxModelRegistry.register(custom);

    // Verify registration
    assertTrue(TaxModelRegistry.isRegistered("TEST-CUSTOM"));

    // Create model and use
    TaxModel model = TaxModelRegistry.createModel("TEST-CUSTOM");
    assertEquals("Test Country", model.getCountryName());
    assertEquals(0.40, model.getTotalMarginalTaxRate(), TOLERANCE);

    // Test royalty
    double royalty = model.calculateRoyalty(1000000.0);
    assertEquals(120000.0, royalty, TOLERANCE);
  }

  // ============================================================================
  // WINDFALL TAX TESTS
  // ============================================================================

  @Test
  @DisplayName("Kazakhstan windfall tax parameters")
  void testKazakhstanWindfallTax() {
    FiscalParameters params = TaxModelRegistry.getParameters("KZ");
    assertNotNull(params);
    assertEquals(40.0, params.getWindfallTaxThreshold(), TOLERANCE);
    assertEquals(0.60, params.getWindfallTaxRate(), TOLERANCE);
  }

  // ============================================================================
  // BUILDER PATTERN TESTS
  // ============================================================================

  @Test
  @DisplayName("FiscalParameters builder creates valid object")
  void testFiscalParametersBuilder() {
    FiscalParameters params = FiscalParameters.builder("TEST-BUILD")
        .countryName("Test Builder Country").description("Testing the builder pattern")
        .validFromYear(2024).fiscalSystemType(FiscalParameters.FiscalSystemType.CONCESSIONARY)
        .corporateTaxRate(0.30).resourceTaxRate(0.20).royaltyRate(0.05)
        .depreciation(FiscalParameters.DepreciationMethod.STRAIGHT_LINE, 8).uplift(0.10, 3)
        .lossCarryForward(5, 0.02).ringFenced(FiscalParameters.RingFenceLevel.LICENSE)
        .investmentTaxCredit(0.10).build();

    assertEquals("TEST-BUILD", params.getCountryCode());
    assertEquals("Test Builder Country", params.getCountryName());
    assertEquals(2024, params.getValidFromYear());
    assertEquals(FiscalParameters.FiscalSystemType.CONCESSIONARY, params.getFiscalSystemType());
    assertEquals(0.30, params.getCorporateTaxRate(), TOLERANCE);
    assertEquals(0.20, params.getResourceTaxRate(), TOLERANCE);
    assertEquals(0.05, params.getRoyaltyRate(), TOLERANCE);
    assertEquals(FiscalParameters.DepreciationMethod.STRAIGHT_LINE, params.getDepreciationMethod());
    assertEquals(8, params.getDepreciationYears());
    assertEquals(0.10, params.getUpliftRate(), TOLERANCE);
    assertEquals(3, params.getUpliftYears());
    assertEquals(5, params.getLossCarryForwardYears());
    assertEquals(0.02, params.getLossCarryForwardInterest(), TOLERANCE);
    assertEquals(FiscalParameters.RingFenceLevel.LICENSE, params.getRingFenceLevel());
    assertEquals(0.10, params.getInvestmentTaxCredit(), TOLERANCE);
  }

  @Test
  @DisplayName("PSC parameters builder")
  void testPSCParametersBuilder() {
    FiscalParameters params = FiscalParameters.builder("TEST-PSC").countryName("Test PSC Country")
        .fiscalSystemType(FiscalParameters.FiscalSystemType.PSC).corporateTaxRate(0.25)
        .costRecoveryLimit(0.60).profitSharing(0.70, 0.30).build();

    assertEquals(FiscalParameters.FiscalSystemType.PSC, params.getFiscalSystemType());
    assertEquals(0.60, params.getCostRecoveryLimit(), TOLERANCE);
    assertEquals(0.70, params.getProfitShareGovernment(), TOLERANCE);
    assertEquals(0.30, params.getProfitShareContractor(), TOLERANCE);
  }

  // ============================================================================
  // MODEL RESET AND STATE TESTS
  // ============================================================================

  @Test
  @DisplayName("Model reset clears loss carry-forward")
  void testModelReset() {
    TaxModel model = TaxModelRegistry.createModel("NO");

    // Create a loss
    model.calculateTax(100000.0, 200000.0, 0.0, 0.0);
    assertTrue(model.getLossCarryForward() > 0);

    // Reset
    model.reset();

    // Loss should be cleared
    assertEquals(0.0, model.getLossCarryForward(), TOLERANCE);
  }

  // ============================================================================
  // SUMMARY TABLE TEST
  // ============================================================================

  @Test
  @DisplayName("Summary table is generated correctly")
  void testSummaryTable() {
    String table = TaxModelRegistry.getSummaryTable();
    assertNotNull(table);
    assertTrue(table.length() > 100);
    assertTrue(table.contains("Norway"));
    assertTrue(table.contains("NO"));
  }

  // ============================================================================
  // INTEGRATION WITH EXISTING NORWEGIAN TAX MODEL
  // ============================================================================

  @Test
  @DisplayName("NorwegianTaxModel integration methods")
  void testNorwegianTaxModelIntegration() {
    // Get parameters via the factory
    FiscalParameters params = NorwegianTaxModel.getFiscalParameters();
    assertNotNull(params);
    assertEquals("NO", params.getCountryCode());
    assertEquals(0.22, params.getCorporateTaxRate(), TOLERANCE);
    assertEquals(0.56, params.getResourceTaxRate(), TOLERANCE);

    // Create tax model via factory
    TaxModel model = NorwegianTaxModel.createTaxModel();
    assertNotNull(model);
    assertEquals(0.78, model.getTotalMarginalTaxRate(), TOLERANCE);
  }
}
