package neqsim.util.nucleation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PopulationBalanceModel}.
 *
 * @author esol
 * @version 1.0
 */
@Disabled("Long-running test - disabled for CI")
class PopulationBalanceModelTest {

  private ClassicalNucleationTheory cnt;

  @BeforeEach
  void setUp() {
    // Create a sulfur S8 CNT model with known nucleation conditions
    cnt = ClassicalNucleationTheory.sulfurS8();
    cnt.setTemperature(253.15); // -20 C
    cnt.setSupersaturationRatio(100.0); // Strong supersaturation
    cnt.setGasViscosity(1.0e-5);
    cnt.setCarrierGasMolarMass(0.018); // 18 g/mol
    cnt.setTotalPressure(30.0e5); // 30 bara
    cnt.setResidenceTime(1.0);
    cnt.calculate();
  }

  @Test
  void testConstructorAndDefaults() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    assertNotNull(pbm);
    assertEquals(30, pbm.getNumberOfBins());
  }

  @Test
  void testBinInitialization() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setNumberOfBins(20);
    pbm.setMinDiameter(1.0e-9);
    pbm.setMaxDiameter(10.0e-6);
    pbm.setTotalTime(0.1);
    pbm.setTimeSteps(10);
    pbm.solve();

    double[] diameters = pbm.getBinDiameters();
    assertNotNull(diameters);
    assertEquals(20, diameters.length);

    // Diameters should be geometrically spaced
    assertTrue(diameters[0] > 0, "First bin diameter should be positive");
    assertTrue(diameters[19] > diameters[0], "Last diameter should be larger than first");
    assertTrue(diameters[0] >= 1.0e-9, "First diameter should be >= minDiameter");
    assertTrue(diameters[19] <= 10.0e-6, "Last diameter should be <= maxDiameter");

    // Check geometric spacing
    double ratio1 = diameters[1] / diameters[0];
    double ratio2 = diameters[2] / diameters[1];
    assertEquals(ratio1, ratio2, 0.01, "Bins should be geometrically spaced");
  }

  @Test
  void testBinEdges() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setNumberOfBins(10);
    pbm.setMinDiameter(1.0e-9);
    pbm.setMaxDiameter(1.0e-5);
    pbm.setTotalTime(0.01);
    pbm.setTimeSteps(5);
    pbm.solve();

    double[] edges = pbm.getBinEdges();
    assertNotNull(edges);
    assertEquals(11, edges.length); // numberOfBins + 1
    for (int i = 0; i < edges.length - 1; i++) {
      assertTrue(edges[i + 1] > edges[i], "Bin edges should be monotonically increasing");
    }
  }

  @Test
  void testSolveProducesParticles() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setNumberOfBins(30);
    pbm.setMinDiameter(1.0e-9);
    pbm.setMaxDiameter(100.0e-6);
    pbm.setTotalTime(1.0);
    pbm.setTimeSteps(100);
    pbm.solve();

    // With S=100, there should be significant nucleation
    double totalN = pbm.getTotalNumberDensity();
    assertTrue(totalN > 0.0, "Total number density should be positive after nucleation");

    double totalV = pbm.getTotalVolumeConcentration();
    assertTrue(totalV >= 0.0, "Total volume concentration should be non-negative");

    double totalM = pbm.getTotalMassConcentration();
    assertTrue(totalM >= 0.0, "Total mass concentration should be non-negative");
  }

  @Test
  void testMedianDiameter() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setNumberOfBins(40);
    pbm.setMinDiameter(1.0e-9);
    pbm.setMaxDiameter(100.0e-6);
    pbm.setTotalTime(2.0);
    pbm.setTimeSteps(200);
    pbm.solve();

    double d50 = pbm.getMedianDiameter();
    double dMean = pbm.getMeanDiameter();

    if (pbm.getTotalNumberDensity() > 0.0) {
      assertTrue(d50 > 0.0, "Median diameter should be positive when particles exist");
      assertTrue(dMean > 0.0, "Mean diameter should be positive when particles exist");
      // Median should be somewhere between min and max bin diameters
      assertTrue(d50 >= 1.0e-9 && d50 <= 100.0e-6, "Median diameter should be within bin range");
    }
  }

  @Test
  void testGeometricStdDev() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setNumberOfBins(30);
    pbm.setMinDiameter(1.0e-9);
    pbm.setMaxDiameter(100.0e-6);
    pbm.setTotalTime(1.0);
    pbm.setTimeSteps(100);
    pbm.solve();

    double gsd = pbm.getGeometricStdDev();
    assertTrue(gsd >= 1.0, "Geometric std dev should be >= 1.0");
  }

  @Test
  void testCurrentTime() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setTotalTime(5.0);
    pbm.setTimeSteps(50);
    pbm.solve();

    double t = pbm.getCurrentTime();
    assertEquals(5.0, t, 0.1, "Current time should equal total time after solving");
  }

  @Test
  void testNumberDensityArray() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setNumberOfBins(20);
    pbm.setTotalTime(0.5);
    pbm.setTimeSteps(50);
    pbm.solve();

    double[] n = pbm.getBinNumberDensities();
    assertNotNull(n);
    assertEquals(20, n.length);

    // All densities should be non-negative
    for (double ni : n) {
      assertTrue(ni >= 0.0, "Bin number density should be non-negative");
    }
  }

  @Test
  void testVolumeDensityArray() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setNumberOfBins(20);
    pbm.setTotalTime(0.5);
    pbm.setTimeSteps(50);
    pbm.solve();

    double[] v = pbm.getBinVolumeDensities();
    assertNotNull(v);
    assertEquals(20, v.length);

    for (double vi : v) {
      assertTrue(vi >= 0.0, "Bin volume density should be non-negative");
    }
  }

  @Test
  void testGrowthIncreasesSize() {
    // Run for short time — nucleation only
    PopulationBalanceModel pbmShort = new PopulationBalanceModel(cnt);
    pbmShort.setNumberOfBins(30);
    pbmShort.setTotalTime(0.01);
    pbmShort.setTimeSteps(10);
    pbmShort.solve();
    double d50Short = pbmShort.getMedianDiameter();

    // Run for longer time — nucleation + growth
    PopulationBalanceModel pbmLong = new PopulationBalanceModel(cnt);
    pbmLong.setNumberOfBins(30);
    pbmLong.setTotalTime(5.0);
    pbmLong.setTimeSteps(500);
    pbmLong.solve();
    double d50Long = pbmLong.getMedianDiameter();

    // Longer time should produce particles that have grown larger
    if (pbmShort.getTotalNumberDensity() > 0 && pbmLong.getTotalNumberDensity() > 0) {
      assertTrue(d50Long >= d50Short,
          "Longer simulation should produce equal or larger median diameter");
    }
  }

  @Test
  void testToMap() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setTotalTime(0.5);
    pbm.setTimeSteps(50);
    pbm.solve();

    Map<String, Object> map = pbm.toMap();
    assertNotNull(map);
    assertTrue(map.containsKey("configuration"), "Should have configuration key");
    assertTrue(map.containsKey("statistics"), "Should have statistics key");
    @SuppressWarnings("unchecked")
    Map<String, Object> config = (Map<String, Object>) map.get("configuration");
    assertNotNull(config);
    assertTrue(config.containsKey("numberOfBins"), "Configuration should contain numberOfBins");
  }

  @Test
  void testToJson() {
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setTotalTime(0.5);
    pbm.setTimeSteps(50);
    pbm.solve();

    String json = pbm.toJson();
    assertNotNull(json);
    assertTrue(json.contains("numberOfBins"));
    assertTrue(json.contains("totalNumberDensity"));
    assertTrue(json.contains("medianDiameter_m"));
  }

  @Test
  void testZeroSupersaturationNoParticles() {
    // CNT with S=1 (no supersaturation) should give zero nucleation
    ClassicalNucleationTheory cntNoSuper = ClassicalNucleationTheory.sulfurS8();
    cntNoSuper.setTemperature(253.15);
    cntNoSuper.setSupersaturationRatio(1.0); // No supersaturation
    cntNoSuper.setGasViscosity(1.0e-5);
    cntNoSuper.calculate();

    PopulationBalanceModel pbm = new PopulationBalanceModel(cntNoSuper);
    pbm.setTotalTime(1.0);
    pbm.setTimeSteps(100);
    pbm.solve();

    assertEquals(0.0, pbm.getTotalNumberDensity(), 1e-10,
        "No supersaturation should give zero particles");
  }

  @Test
  void testConservation() {
    // Total particle volume should be consistent with total mass and density
    PopulationBalanceModel pbm = new PopulationBalanceModel(cnt);
    pbm.setNumberOfBins(30);
    pbm.setTotalTime(1.0);
    pbm.setTimeSteps(100);
    pbm.solve();

    double totalV = pbm.getTotalVolumeConcentration();
    double totalM = pbm.getTotalMassConcentration();

    if (totalV > 0.0 && totalM > 0.0) {
      // M = rho * V, so effective density should be reasonable
      double effectiveRho = totalM / totalV;
      assertTrue(effectiveRho > 100.0 && effectiveRho < 5000.0,
          "Effective density should be physically reasonable: " + effectiveRho);
    }
  }
}
