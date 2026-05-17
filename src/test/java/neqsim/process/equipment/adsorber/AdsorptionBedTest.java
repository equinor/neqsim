package neqsim.process.equipment.adsorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.IsothermType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the AdsorptionBed unit operation.
 *
 * @author Even Solbraa
 */
public class AdsorptionBedTest {
  private SystemInterface testGas;
  private StreamInterface feedStream;
  private AdsorptionBed bed;

  /**
   * Set up test fixtures.
   */
  @BeforeEach
  public void setUp() {
    testGas = new SystemSrkEos(298.15, 10.0);
    testGas.addComponent("methane", 0.85);
    testGas.addComponent("CO2", 0.10);
    testGas.addComponent("nitrogen", 0.05);
    testGas.setMixingRule("classic");
    testGas.init(0);

    feedStream = new Stream("feed", testGas);
    feedStream.setFlowRate(1000.0, "kg/hr");
    feedStream.run();

    bed = new AdsorptionBed("TestBed", feedStream);
    bed.setBedDiameter(1.0);
    bed.setBedLength(3.0);
    bed.setVoidFraction(0.35);
    bed.setAdsorbentMaterial("AC");
    bed.setAdsorbentBulkDensity(500.0);
    bed.setParticleDiameter(0.003);
  }

  // =============================================
  // Construction and configuration tests
  // =============================================

  /**
   * Test default construction.
   */
  @Test
  public void testDefaultConstruction() {
    AdsorptionBed simpleBed = new AdsorptionBed("SimpleBed");
    assertEquals("SimpleBed", simpleBed.getName());
    assertNotNull(simpleBed.toJson());
  }

  /**
   * Test construction with inlet stream.
   */
  @Test
  public void testConstructionWithStream() {
    assertNotNull(bed.getInletStream());
    assertNotNull(bed.getOutletStream());
    assertEquals("TestBed", bed.getName());
  }

  /**
   * Test bed geometry setters/getters.
   */
  @Test
  public void testGeometryConfiguration() {
    bed.setBedDiameter(2.0);
    bed.setBedLength(5.0);
    bed.setVoidFraction(0.4);
    bed.setParticleDiameter(0.005);
    bed.setParticlePorosity(0.45);

    assertEquals(2.0, bed.getBedDiameter(), 1e-10);
    assertEquals(5.0, bed.getBedLength(), 1e-10);
    assertEquals(0.4, bed.getVoidFraction(), 1e-10);
    assertEquals(0.005, bed.getParticleDiameter(), 1e-10);
    assertEquals(0.45, bed.getParticlePorosity(), 1e-10);
  }

  /**
   * Test adsorbent mass calculation.
   */
  @Test
  public void testAdsorbentMass() {
    bed.setBedDiameter(1.0);
    bed.setBedLength(3.0);
    bed.setVoidFraction(0.35);
    bed.setAdsorbentBulkDensity(500.0);

    double expectedVolume = Math.PI / 4.0 * 1.0 * 1.0 * 3.0;
    double expectedMass = expectedVolume * (1.0 - 0.35) * 500.0;
    assertEquals(expectedMass, bed.getAdsorbentMass(), 0.1);
  }

  /**
   * Test bed volume calculation.
   */
  @Test
  public void testBedVolume() {
    bed.setBedDiameter(2.0);
    bed.setBedLength(4.0);
    double expectedVolume = Math.PI / 4.0 * 4.0 * 4.0;
    assertEquals(expectedVolume, bed.getBedVolume(), 1e-6);
  }

  /**
   * Test isotherm type configuration.
   */
  @Test
  public void testIsothermTypeConfiguration() {
    bed.setIsothermType(IsothermType.LANGMUIR);
    assertEquals(IsothermType.LANGMUIR, bed.getIsothermType());

    bed.setIsothermType(IsothermType.SIPS);
    assertEquals(IsothermType.SIPS, bed.getIsothermType());

    bed.setIsothermType(IsothermType.DRA);
    assertEquals(IsothermType.DRA, bed.getIsothermType());
  }

  // =============================================
  // Steady-state tests
  // =============================================

  /**
   * Test steady-state run produces valid outlet.
   */
  @Test
  public void testSteadyStateRun() {
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.run(UUID.randomUUID());

    StreamInterface outlet = bed.getOutletStream();
    assertNotNull(outlet);
    assertNotNull(outlet.getThermoSystem());

    // Outlet should have less total moles than inlet (some adsorbed)
    double inletMoles = feedStream.getThermoSystem().getTotalNumberOfMoles();
    double outletMoles = outlet.getThermoSystem().getTotalNumberOfMoles();
    assertTrue(outletMoles <= inletMoles,
        "Outlet moles should not exceed inlet moles (adsorption removes species)");
  }

  /**
   * Test that CO2 is preferentially adsorbed over methane on AC.
   */
  @Test
  public void testCO2PreferentialAdsorption() {
    bed.setAdsorbentMaterial("AC");
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.run(UUID.randomUUID());

    SystemInterface inletFluid = feedStream.getThermoSystem();
    SystemInterface outletFluid = bed.getOutletStream().getThermoSystem();

    double inletCO2Frac = inletFluid.getPhase(0).getComponent("CO2").getx();
    double outletCO2Frac = outletFluid.getPhase(0).getComponent("CO2").getx();

    // CO2 fraction should decrease more than methane fraction
    assertTrue(outletCO2Frac < inletCO2Frac, "CO2 should be preferentially removed: inlet="
        + inletCO2Frac + ", outlet=" + outletCO2Frac);
  }

  /**
   * Test pressure drop calculation (Ergun equation).
   */
  @Test
  public void testPressureDropCalculation() {
    bed.setCalculatePressureDrop(true);
    bed.run(UUID.randomUUID());

    double dp = bed.getPressureDrop();
    assertTrue(dp > 0, "Pressure drop should be positive");
    assertTrue(dp < 1e6, "Pressure drop should be reasonable (< 10 bar)");

    // Check unit conversion
    double dpBar = bed.getPressureDrop("bar");
    assertEquals(dp / 1e5, dpBar, 1e-10);
  }

  /**
   * Test that different isotherm models all produce valid results.
   */
  @Test
  public void testMultipleIsothermModels() {
    IsothermType[] types =
        new IsothermType[] {IsothermType.LANGMUIR, IsothermType.FREUNDLICH, IsothermType.SIPS};

    for (IsothermType type : types) {
      AdsorptionBed testBed = new AdsorptionBed("Bed_" + type.name(), feedStream);
      testBed.setBedDiameter(1.0);
      testBed.setBedLength(3.0);
      testBed.setAdsorbentMaterial("AC");
      testBed.setIsothermType(type);
      testBed.run(UUID.randomUUID());

      StreamInterface outlet = testBed.getOutletStream();
      assertNotNull(outlet.getThermoSystem(),
          "Outlet should exist for isotherm type " + type.name());
    }
  }

  // =============================================
  // Transient simulation tests
  // =============================================

  /**
   * Test transient grid initialisation.
   */
  @Test
  public void testTransientGridInitialisation() {
    bed.setNumberOfCells(20);
    bed.initialiseTransientGrid();

    assertEquals(20, bed.getNumberOfCells());
    assertFalse(bed.isBreakthroughOccurred());
    assertEquals(-1.0, bed.getBreakthroughTime(), 1e-10);
    assertEquals(0.0, bed.getElapsedTime(), 1e-10);
  }

  /**
   * Test transient simulation advances loading.
   */
  @Test
  public void testTransientSimulationAdvancesLoading() {
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.setNumberOfCells(10);
    bed.setKLDF(0.05); // moderate transfer rate
    bed.setCalculateSteadyState(false);

    // Run several transient steps
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 20; i++) {
      bed.runTransient(1.0, id);
    }

    // After some time, the first cells should have significant loading
    double[] loadingCO2 = bed.getLoadingProfile(1); // CO2 is component index 1
    assertTrue(loadingCO2[0] > 0, "First cell should have non-zero CO2 loading after 20 steps");
  }

  /**
   * Test that loading front moves through the bed over time.
   */
  @Test
  public void testLoadingFrontPropagation() {
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.setNumberOfCells(20);
    bed.setKLDF(0.5); // fast transfer so differential is visible
    bed.setCalculateSteadyState(false);

    // Run enough steps for front to partially penetrate the bed
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 50; i++) {
      bed.runTransient(0.2, id);
    }

    double[] loading = bed.getLoadingProfile(1);
    // Sum loading in the first half vs second half of the bed
    double firstHalf = 0.0;
    double secondHalf = 0.0;
    for (int cell = 0; cell < 10; cell++) {
      firstHalf += loading[cell];
    }
    for (int cell = 10; cell < 20; cell++) {
      secondHalf += loading[cell];
    }
    assertTrue(firstHalf >= secondHalf,
        "First half of bed should have more loading than second half: " + firstHalf + " vs "
            + secondHalf);
  }

  /**
   * Test elapsed time tracking.
   */
  @Test
  public void testElapsedTimeTracking() {
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.setNumberOfCells(10);
    bed.setCalculateSteadyState(false);

    UUID id = UUID.randomUUID();
    bed.runTransient(5.0, id);
    bed.runTransient(3.0, id);

    assertEquals(8.0, bed.getElapsedTime(), 1e-6);
  }

  /**
   * Test bed pre-loading.
   */
  @Test
  public void testBedPreLoading() {
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.setNumberOfCells(10);
    bed.preloadBed(0.5, 0); // 50% saturated

    double avgLoading = bed.getAverageLoading(1); // CO2
    assertTrue(avgLoading > 0, "Pre-loaded bed should have non-zero loading");
  }

  /**
   * Test bed reset.
   */
  @Test
  public void testBedReset() {
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.setNumberOfCells(10);
    bed.setCalculateSteadyState(false);

    UUID id = UUID.randomUUID();
    bed.runTransient(5.0, id);
    assertTrue(bed.getElapsedTime() > 0);

    bed.resetBed();
    assertEquals(0.0, bed.getElapsedTime(), 1e-10);
    assertFalse(bed.isBreakthroughOccurred());
    assertFalse(bed.isDesorptionMode());
  }

  // =============================================
  // Desorption mode tests
  // =============================================

  /**
   * Test desorption mode reduces loading in the first cell.
   *
   * <p>
   * During desorption the inlet concentration is zero, so the first cell receives clean gas and its
   * equilibrium loading drops, causing net desorption. This test tracks the first cell specifically
   * rather than the bed average, because downstream cells may still be absorbing gas released from
   * upstream.
   * </p>
   */
  @Test
  public void testDesorptionReducesLoading() {
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.setNumberOfCells(10);
    bed.setKLDF(0.1);
    bed.setCalculateSteadyState(false);

    // Phase 1: saturate the bed with adsorption
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 60; i++) {
      bed.runTransient(0.5, id);
    }
    double[] loadingAfterAds = bed.getLoadingProfile(1);
    double firstCellAfterAds = loadingAfterAds[0];
    assertTrue(firstCellAfterAds > 0, "First cell should have loading after adsorption");

    // Phase 2: switch to desorption and run for sufficient time
    bed.setDesorptionMode(true);
    assertTrue(bed.isDesorptionMode());
    assertEquals(AdsorptionCycleController.CyclePhase.DESORPTION, bed.getCurrentPhase());

    for (int i = 0; i < 200; i++) {
      bed.runTransient(0.5, id);
    }
    double[] loadingAfterDes = bed.getLoadingProfile(1);
    double firstCellAfterDes = loadingAfterDes[0];
    assertTrue(firstCellAfterDes < firstCellAfterAds,
        "First cell loading should decrease during desorption: before=" + firstCellAfterAds
            + ", after=" + firstCellAfterDes);
  }

  // =============================================
  // LDF / mass transfer tests
  // =============================================

  /**
   * Test LDF coefficient setters/getters.
   */
  @Test
  public void testKLDFConfiguration() {
    bed.setKLDF(0, 0.02);
    bed.setKLDF(1, 0.05);
    bed.setKLDF(2, 0.01);

    assertEquals(0.02, bed.getKLDF(0), 1e-10);
    assertEquals(0.05, bed.getKLDF(1), 1e-10);
    assertEquals(0.01, bed.getKLDF(2), 1e-10);
  }

  /**
   * Test that higher kLDF leads to faster adsorption.
   */
  @Test
  public void testHigherKLDFMeansMoreAdsorption() {
    // Slow transfer
    AdsorptionBed slowBed = new AdsorptionBed("Slow", feedStream);
    slowBed.setBedDiameter(1.0);
    slowBed.setBedLength(3.0);
    slowBed.setAdsorbentMaterial("AC");
    slowBed.setIsothermType(IsothermType.LANGMUIR);
    slowBed.setKLDF(0.001); // very slow
    slowBed.run(UUID.randomUUID());
    double slowOutletMoles = slowBed.getOutletStream().getThermoSystem().getTotalNumberOfMoles();

    // Fast transfer
    AdsorptionBed fastBed = new AdsorptionBed("Fast", feedStream);
    fastBed.setBedDiameter(1.0);
    fastBed.setBedLength(3.0);
    fastBed.setAdsorbentMaterial("AC");
    fastBed.setIsothermType(IsothermType.LANGMUIR);
    fastBed.setKLDF(1.0); // very fast
    fastBed.run(UUID.randomUUID());
    double fastOutletMoles = fastBed.getOutletStream().getThermoSystem().getTotalNumberOfMoles();

    assertTrue(fastOutletMoles < slowOutletMoles, "Higher kLDF should remove more moles: fast="
        + fastOutletMoles + ", slow=" + slowOutletMoles);
  }

  // =============================================
  // Validation and JSON tests
  // =============================================

  /**
   * Test validation result with valid configuration.
   */
  @Test
  public void testValidationPasses() {
    neqsim.util.validation.ValidationResult result = bed.validateSetup();
    assertTrue(result.getErrors().isEmpty(),
        "Valid configuration should pass validation: " + result.getErrors());
  }

  /**
   * Test validation catches invalid configuration.
   */
  @Test
  public void testValidationCatchesErrors() {
    AdsorptionBed badBed = new AdsorptionBed("BadBed", feedStream);
    badBed.setBedDiameter(-1.0);
    badBed.setBedLength(0.0);
    badBed.setVoidFraction(1.5);

    neqsim.util.validation.ValidationResult result = badBed.validateSetup();
    assertFalse(result.getErrors().isEmpty(), "Invalid config should have validation errors");
  }

  /**
   * Test JSON report generation.
   */
  @Test
  public void testJsonReport() {
    bed.run(UUID.randomUUID());
    String json = bed.toJson();

    assertNotNull(json);
    assertTrue(json.contains("AdsorptionBed"));
    assertTrue(json.contains("bedDiameter_m"));
    assertTrue(json.contains("material"));
    assertTrue(json.contains("pressureDrop_Pa"));
  }

  // =============================================
  // Different adsorbent materials test
  // =============================================

  /**
   * Test with Zeolite 13X (strong CO2 adsorbent).
   */
  @Test
  public void testZeolite13XAdsorbent() {
    bed.setAdsorbentMaterial("Zeolite 13X");
    bed.setAdsorbentBulkDensity(650.0);
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.run(UUID.randomUUID());

    SystemInterface outlet = bed.getOutletStream().getThermoSystem();
    assertNotNull(outlet);

    // Zeolite 13X should remove CO2 well
    double outletCO2 = outlet.getPhase(0).getComponent("CO2").getx();
    double inletCO2 = feedStream.getThermoSystem().getPhase(0).getComponent("CO2").getx();
    assertTrue(outletCO2 < inletCO2, "Zeolite 13X should reduce CO2 concentration");
  }
}
