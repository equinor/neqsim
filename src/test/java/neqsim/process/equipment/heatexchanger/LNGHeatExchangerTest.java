package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link LNGHeatExchanger}.
 *
 * <p>
 * Covers P1-P10 features: rigorous H-T curves, per-stream DP, exergy analysis, adaptive refinement,
 * Manglik-Bergles j/f, Lockhart-Martinelli two-phase DP, dynamic transient, core sizing, freeze-out
 * detection, flow maldistribution, thermal stress, and mercury risk.
 * </p>
 *
 * @author NeqSim
 */
class LNGHeatExchangerTest {

  /** Create a standard hot feed gas stream for reuse in tests. */
  private Stream createHotStream() {
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    hotFluid.addComponent("methane", 0.90);
    hotFluid.addComponent("ethane", 0.05);
    hotFluid.addComponent("propane", 0.03);
    hotFluid.addComponent("nitrogen", 0.02);
    hotFluid.setMixingRule("classic");

    Stream hotStream = new Stream("hot_feed", hotFluid);
    hotStream.setFlowRate(100000.0, "kg/hr");
    hotStream.setTemperature(30.0, "C");
    hotStream.setPressure(50.0, "bara");
    return hotStream;
  }

  /** Create a standard cold refrigerant stream for reuse in tests. */
  private Stream createColdStream() {
    SystemInterface coldFluid = new SystemSrkEos(273.15 - 160.0, 3.0);
    coldFluid.addComponent("methane", 0.40);
    coldFluid.addComponent("ethane", 0.30);
    coldFluid.addComponent("propane", 0.30);
    coldFluid.setMixingRule("classic");

    Stream coldStream = new Stream("cold_ref", coldFluid);
    coldStream.setFlowRate(150000.0, "kg/hr");
    coldStream.setTemperature(-33.0, "C");
    coldStream.setPressure(3.0, "bara");
    return coldStream;
  }

  /** Run a process with the given HX and its input streams. */
  private void runHX(LNGHeatExchanger hx, Stream... streams) {
    ProcessSystem process = new ProcessSystem();
    for (Stream s : streams) {
      process.add(s);
    }
    process.add(hx);
    process.run();
  }

  @Test
  void testBasicLNGHeatExchanger() {
    Stream hotStream = createHotStream();
    Stream coldStream = createColdStream();

    LNGHeatExchanger lngHX = new LNGHeatExchanger("MCHE",
        Arrays.asList((StreamInterface) hotStream, (StreamInterface) coldStream));

    lngHX.setNumberOfZones(10);
    assertEquals(10, lngHX.getNumberOfZones());
    lngHX.setExchangerType("BAHX");
    assertEquals("BAHX", lngHX.getExchangerType());

    runHX(lngHX, hotStream, coldStream);

    double mita = lngHX.getMITA();
    assertTrue(mita >= 0.0, "MITA should be non-negative, got: " + mita);
  }

  @Test
  void testStreamClassification() {
    SystemInterface fluid1 = new SystemSrkEos(273.15 + 20.0, 50.0);
    fluid1.addComponent("methane", 1.0);
    fluid1.setMixingRule("classic");

    Stream s1 = new Stream("warm", fluid1);
    s1.setFlowRate(50000.0, "kg/hr");
    s1.setTemperature(20.0, "C");
    s1.setPressure(50.0, "bara");

    SystemInterface fluid2 = new SystemSrkEos(273.15 - 100.0, 3.0);
    fluid2.addComponent("methane", 0.5);
    fluid2.addComponent("ethane", 0.5);
    fluid2.setMixingRule("classic");

    Stream s2 = new Stream("cold", fluid2);
    s2.setFlowRate(50000.0, "kg/hr");
    s2.setTemperature(-100.0, "C");
    s2.setPressure(3.0, "bara");

    LNGHeatExchanger hx =
        new LNGHeatExchanger("test_hx", Arrays.asList((StreamInterface) s1, (StreamInterface) s2));
    hx.setNumberOfZones(5);

    runHX(hx, s1, s2);

    double[][] hotCurve = hx.getHotCompositeCurve();
    double[][] coldCurve = hx.getColdCompositeCurve();
    double mita = hx.getMITA();
    assertTrue(mita >= 0.0 || hotCurve == null, "MITA should be valid or curves absent");
  }

  @Test
  void testPressureDropAndExergy() {
    Stream hotStream = createHotStream();
    Stream coldStream = createColdStream();

    LNGHeatExchanger lngHX = new LNGHeatExchanger("MCHE-PD");
    lngHX.addInStream(hotStream);
    lngHX.addInStream(coldStream);
    lngHX.setNumberOfZones(10);
    lngHX.setStreamPressureDrop(0, 2.0);
    lngHX.setStreamPressureDrop(1, 0.5);
    assertEquals(2.0, lngHX.getStreamPressureDrop(0), 1e-10);
    assertEquals(0.5, lngHX.getStreamPressureDrop(1), 1e-10);
    assertEquals(0.0, lngHX.getStreamPressureDrop(5), 1e-10);

    lngHX.setReferenceTemperature(15.0);
    assertEquals(15.0, lngHX.getReferenceTemperature(), 0.01);

    runHX(lngHX, hotStream, coldStream);

    double mita = lngHX.getMITA();
    assertTrue(mita > 0.0, "MITA should be positive, got: " + mita);

    double etaII = lngHX.getSecondLawEfficiency();
    assertTrue(etaII > 0.0 && etaII <= 1.0,
        "Second-law efficiency should be in (0,1], got: " + etaII);

    double totalEx = lngHX.getTotalExergyDestruction();
    assertTrue(totalEx > 0.0, "Total exergy destruction should be positive, got: " + totalEx);

    double[] exZones = lngHX.getExergyDestructionPerZone();
    assertEquals(10, exZones.length, "Exergy array should match zone count");
    for (int z = 0; z < exZones.length; z++) {
      assertTrue(exZones[z] >= 0.0,
          "Per-zone exergy destruction should be non-negative at zone " + z);
    }

    int mitaZ = lngHX.getMITAZoneIndex();
    assertTrue(mitaZ >= 0 && mitaZ < 10, "MITA zone index should be valid");
  }

  // ══════════════════════════════════════════════════════════════════
  // P4: Adaptive zone refinement
  // ══════════════════════════════════════════════════════════════════

  @Test
  void testAdaptiveZoneRefinement() {
    Stream hotStream = createHotStream();
    Stream coldStream = createColdStream();

    LNGHeatExchanger hx = new LNGHeatExchanger("MCHE-adaptive");
    hx.addInStream(hotStream);
    hx.addInStream(coldStream);
    hx.setNumberOfZones(10);
    hx.setAdaptiveRefinement(true);
    hx.setMaxAdaptiveZones(50);
    hx.setAdaptiveThresholdFactor(1.5);

    assertTrue(hx.getAdaptiveRefinement());
    assertEquals(50, hx.getMaxAdaptiveZones());
    assertEquals(1.5, hx.getAdaptiveThresholdFactor(), 1e-10);

    runHX(hx, hotStream, coldStream);

    // After adaptive refinement, the composite curves may have more points
    double[][] hotCurve = hx.getHotCompositeCurve();
    assertNotNull(hotCurve);
    // With adaptive refinement, at least the original 11 points
    assertTrue(hotCurve.length >= 11,
        "Adaptive refinement should produce >= 11 points, got: " + hotCurve.length);

    double mita = hx.getMITA();
    assertTrue(mita >= 0.0, "MITA should be non-negative after adaptive refinement");

    double etaII = hx.getSecondLawEfficiency();
    assertTrue(etaII > 0.0 && etaII <= 1.0, "eta_II should be valid after adaptive refinement");
  }

  // ══════════════════════════════════════════════════════════════════
  // P5: Manglik-Bergles fin correlations
  // ══════════════════════════════════════════════════════════════════

  @Test
  void testFinGeometryAndCorrelations() {
    // Test FinGeometry inner class
    LNGHeatExchanger.FinGeometry fin = new LNGHeatExchanger.FinGeometry();
    assertEquals("offset-strip", fin.getType());
    assertEquals(0.006, fin.getFinHeight(), 1e-6);
    assertEquals(0.0003, fin.getFinThickness(), 1e-6);
    assertEquals(0.0016, fin.getFinPitch(), 1e-6);
    assertEquals(0.003, fin.getStripLength(), 1e-6);

    // Test constructor with parameters
    LNGHeatExchanger.FinGeometry fin2 =
        new LNGHeatExchanger.FinGeometry(0.008, 0.002, 0.0004, 0.004);
    assertEquals(0.008, fin2.getFinHeight(), 1e-6);
    assertEquals(0.002, fin2.getFinPitch(), 1e-6);

    // Hydraulic diameter should be positive
    double dh = fin.getHydraulicDiameter();
    assertTrue(dh > 0.0, "Hydraulic diameter should be positive, got: " + dh);
    assertTrue(dh < 0.01, "Hydraulic diameter should be < 10mm for BAHX fins, got: " + dh);

    // Sigma and beta
    double sigma = fin.getSigma();
    assertTrue(sigma > 0.0 && sigma < 1.0, "Sigma should be between 0 and 1, got: " + sigma);
    double beta = fin.getBeta();
    assertTrue(beta > 100.0, "Beta should be > 100 m2/m3 for BAHX, got: " + beta);

    // Run with fin geometry configured
    Stream hotStream = createHotStream();
    Stream coldStream = createColdStream();

    LNGHeatExchanger hx = new LNGHeatExchanger("MCHE-fin");
    hx.addInStream(hotStream);
    hx.addInStream(coldStream);
    hx.setNumberOfZones(10);
    hx.setStreamFinGeometry(0, fin);
    hx.setStreamFinGeometry(1, fin);
    assertEquals(fin, hx.getStreamFinGeometry(0));
    assertNull(hx.getStreamFinGeometry(5));

    // Set core geometry for DP calculations
    LNGHeatExchanger.CoreGeometry core = new LNGHeatExchanger.CoreGeometry();
    core.setLength(6.0);
    core.setWidth(1.2);
    core.setHeight(1.2);
    hx.setCoreGeometry(core);

    runHX(hx, hotStream, coldStream);

    // Check j-factor and f-factor results
    double[] jFactors = hx.getStreamJFactor();
    double[] fFactors = hx.getStreamFFactor();
    assertEquals(2, jFactors.length);
    assertEquals(2, fFactors.length);

    // j and f should be positive when fin geometry is set
    assertTrue(jFactors[0] > 0.0, "j-factor should be > 0 for stream 0, got: " + jFactors[0]);
    assertTrue(fFactors[0] > 0.0, "f-factor should be > 0 for stream 0, got: " + fFactors[0]);

    // Typical BAHX j-factor range: 0.001 - 0.03
    assertTrue(jFactors[0] < 0.1, "j-factor should be < 0.1, got: " + jFactors[0]);
    assertTrue(fFactors[0] < 0.5, "f-factor should be < 0.5, got: " + fFactors[0]);

    // Computed DP should be available
    double[] dp = hx.getComputedStreamDP();
    assertEquals(2, dp.length);
    assertTrue(dp[0] >= 0.0, "Computed DP should be non-negative for stream 0");
  }

  // ══════════════════════════════════════════════════════════════════
  // P7: Dynamic cool-down transient
  // ══════════════════════════════════════════════════════════════════

  @Test
  void testCooldownTransient() {
    Stream hotStream = createHotStream();
    Stream coldStream = createColdStream();

    LNGHeatExchanger hx = new LNGHeatExchanger("MCHE-transient");
    hx.addInStream(hotStream);
    hx.addInStream(coldStream);
    hx.setNumberOfZones(10);

    // Set core thermal mass (typical: 5000 kg * 0.9 kJ/(kg·K) = 4500 kJ/K)
    hx.setCoreThermalMass(4500.0);
    assertEquals(4500.0, hx.getCoreThermalMass(), 1e-6);

    runHX(hx, hotStream, coldStream);

    // Run transient from 20°C ambient to -150°C target over 24 hours, 100 steps
    hx.runCooldownTransient(-150.0, 20.0, 100, 24.0);

    List<LNGHeatExchanger.TransientPoint> results = hx.getTransientResults();
    assertFalse(results.isEmpty(), "Transient results should not be empty");

    // First point should be at time=0, metal temp=ambient
    LNGHeatExchanger.TransientPoint t0 = results.get(0);
    assertEquals(0.0, t0.timeHours, 1e-6);
    assertEquals(20.0, t0.metalTempC, 1e-6);

    // Metal temperature should decrease over time
    LNGHeatExchanger.TransientPoint tLast = results.get(results.size() - 1);
    assertTrue(tLast.metalTempC < 20.0,
        "Metal temp should decrease during cool-down, got: " + tLast.metalTempC);
  }

  // ══════════════════════════════════════════════════════════════════
  // P8: Core sizing
  // ══════════════════════════════════════════════════════════════════

  @Test
  void testCoreSizing() {
    Stream hotStream = createHotStream();
    Stream coldStream = createColdStream();

    LNGHeatExchanger hx = new LNGHeatExchanger("MCHE-sizing");
    hx.addInStream(hotStream);
    hx.addInStream(coldStream);
    hx.setNumberOfZones(10);

    // Set fin geometry for sizing reference
    LNGHeatExchanger.FinGeometry fin = new LNGHeatExchanger.FinGeometry();
    hx.setStreamFinGeometry(0, fin);

    runHX(hx, hotStream, coldStream);

    // Size the core
    hx.sizeCore();

    LNGHeatExchanger.CoreGeometry core = hx.getCoreGeometry();
    assertNotNull(core);
    assertTrue(core.getLength() > 0.0, "Core length should be positive, got: " + core.getLength());
    assertTrue(core.getWidth() > 0.0, "Core width should be positive, got: " + core.getWidth());
    assertTrue(core.getHeight() > 0.0, "Core height should be positive, got: " + core.getHeight());
    assertTrue(core.getWeight() > 0.0, "Core weight should be positive, got: " + core.getWeight());
    assertTrue(core.getNumberOfLayers() >= 1,
        "Layer count should be >= 1, got: " + core.getNumberOfLayers());
    assertTrue(core.getVolume() > 0.0, "Core volume should be positive, got: " + core.getVolume());

    // Reasonable ranges for LNG MCHE (roughly)
    assertTrue(core.getLength() > 0.5, "Core length should be > 0.5 m for LNG service");
    assertTrue(core.getLength() < 20.0, "Core length should be < 20 m");

    // Core thermal mass should be set after sizing
    assertTrue(hx.getCoreThermalMass() > 0.0,
        "Thermal mass should be set after sizing, got: " + hx.getCoreThermalMass());
  }

  // ══════════════════════════════════════════════════════════════════
  // P9: Freeze-out detection
  // ══════════════════════════════════════════════════════════════════

  @Test
  void testFreezeOutDetection() {
    // Create a case with temperatures below CO2 freeze point (-56.6°C)
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    hotFluid.addComponent("methane", 0.85);
    hotFluid.addComponent("ethane", 0.05);
    hotFluid.addComponent("CO2", 0.08);
    hotFluid.addComponent("nitrogen", 0.02);
    hotFluid.setMixingRule("classic");

    Stream hotStream = new Stream("hot_with_co2", hotFluid);
    hotStream.setFlowRate(100000.0, "kg/hr");
    hotStream.setTemperature(30.0, "C");
    hotStream.setPressure(50.0, "bara");

    // Very cold refrigerant (below CO2 freeze point)
    SystemInterface coldFluid = new SystemSrkEos(273.15 - 160.0, 3.0);
    coldFluid.addComponent("methane", 0.40);
    coldFluid.addComponent("ethane", 0.30);
    coldFluid.addComponent("propane", 0.30);
    coldFluid.setMixingRule("classic");

    Stream coldStream = new Stream("cold_deep", coldFluid);
    coldStream.setFlowRate(200000.0, "kg/hr");
    coldStream.setTemperature(-80.0, "C");
    coldStream.setPressure(3.0, "bara");

    LNGHeatExchanger hx = new LNGHeatExchanger("MCHE-freeze");
    hx.addInStream(hotStream);
    hx.addInStream(coldStream);
    hx.setNumberOfZones(10);

    runHX(hx, hotStream, coldStream);

    // Freeze-out risk array should be populated
    boolean[] risks = hx.getFreezeOutRiskPerZone();
    assertEquals(10, risks.length, "Freeze-out risk array should match zone count");

    // Temperature profiles should be available
    double[] hotProfile = hx.getZoneTempProfileHotC();
    double[] coldProfile = hx.getZoneTempProfileColdC();
    assertTrue(hotProfile.length > 0, "Hot temperature profile should be populated");
    assertTrue(coldProfile.length > 0, "Cold temperature profile should be populated");
  }

  // ══════════════════════════════════════════════════════════════════
  // P10: Flow maldistribution
  // ══════════════════════════════════════════════════════════════════

  @Test
  void testFlowMaldistribution() {
    Stream hotStream = createHotStream();
    Stream coldStream = createColdStream();

    // Baseline without maldistribution
    LNGHeatExchanger hxIdeal = new LNGHeatExchanger("MCHE-ideal");
    hxIdeal.addInStream(hotStream);
    hxIdeal.addInStream(coldStream);
    hxIdeal.setNumberOfZones(10);
    hxIdeal.setFlowMaldistributionFactor(1.0);
    assertEquals(1.0, hxIdeal.getFlowMaldistributionFactor(), 1e-10);

    runHX(hxIdeal, hotStream, coldStream);
    double mitaIdeal = hxIdeal.getMITA();

    // Create fresh streams for the second run
    Stream hotStream2 = createHotStream();
    Stream coldStream2 = createColdStream();

    // With 10% maldistribution penalty
    LNGHeatExchanger hxMaldist = new LNGHeatExchanger("MCHE-maldist");
    hxMaldist.addInStream(hotStream2);
    hxMaldist.addInStream(coldStream2);
    hxMaldist.setNumberOfZones(10);
    hxMaldist.setFlowMaldistributionFactor(0.90);

    runHX(hxMaldist, hotStream2, coldStream2);
    double mitaMaldist = hxMaldist.getMITA();

    // MITA with maldistribution should be tighter (smaller or equal)
    assertTrue(mitaMaldist <= mitaIdeal + 0.01,
        "Maldistribution should not increase MITA: ideal=" + mitaIdeal + " maldist=" + mitaMaldist);
  }

  // ══════════════════════════════════════════════════════════════════
  // Thermal stress assessment
  // ══════════════════════════════════════════════════════════════════

  @Test
  void testThermalStressAssessment() {
    Stream hotStream = createHotStream();
    Stream coldStream = createColdStream();

    LNGHeatExchanger hx = new LNGHeatExchanger("MCHE-stress");
    hx.addInStream(hotStream);
    hx.addInStream(coldStream);
    hx.setNumberOfZones(10);

    // Set core geometry for gradient calculation
    LNGHeatExchanger.CoreGeometry core = new LNGHeatExchanger.CoreGeometry();
    core.setLength(6.0);
    hx.setCoreGeometry(core);

    // Very tight gradient limit to trigger warning
    hx.setMaxAllowableThermalGradient(0.1);
    assertEquals(0.1, hx.getMaxAllowableThermalGradient(), 1e-10);

    runHX(hx, hotStream, coldStream);

    double[] gradients = hx.getThermalGradientPerZone();
    assertEquals(10, gradients.length, "Gradient array should match zone count");

    // With such a tight limit, thermal stress should be flagged
    assertTrue(hx.hasThermalStressWarning(), "Thermal stress should be flagged with 0.1 C/m limit");
  }

  // ══════════════════════════════════════════════════════════════════
  // Mercury risk assessment
  // ══════════════════════════════════════════════════════════════════

  @Test
  void testMercuryRiskAssessment() {
    Stream hotStream = createHotStream();
    Stream coldStream = createColdStream();

    LNGHeatExchanger hx = new LNGHeatExchanger("MCHE-Hg");
    hx.addInStream(hotStream);
    hx.addInStream(coldStream);
    hx.setNumberOfZones(5);
    hx.setExchangerType("BAHX");

    runHX(hx, hotStream, coldStream);

    // Safe mercury level
    hx.assessMercuryRisk(5.0);
    assertFalse(hx.isMercuryRiskPresent(), "5 ppb should be safe for BAHX");
    assertTrue(hx.getMercuryRiskMessage().isEmpty());

    // Unsafe mercury level
    hx.assessMercuryRisk(15.0);
    assertTrue(hx.isMercuryRiskPresent(), "15 ppb should trigger risk for BAHX");
    assertFalse(hx.getMercuryRiskMessage().isEmpty());
    assertTrue(hx.getMercuryRiskMessage().contains("CRITICAL"),
        "Risk message should mention CRITICAL");
  }

  // ══════════════════════════════════════════════════════════════════
  // Integration: All features combined
  // ══════════════════════════════════════════════════════════════════

  @Test
  void testAllFeaturesCombined() {
    Stream hotStream = createHotStream();
    Stream coldStream = createColdStream();

    LNGHeatExchanger hx = new LNGHeatExchanger("MCHE-full");
    hx.addInStream(hotStream);
    hx.addInStream(coldStream);

    // P1-P3: zones, DP, exergy
    hx.setNumberOfZones(15);
    hx.setStreamPressureDrop(0, 1.5);
    hx.setStreamPressureDrop(1, 0.3);
    hx.setReferenceTemperature(15.0);

    // P4: Adaptive refinement
    hx.setAdaptiveRefinement(true);
    hx.setMaxAdaptiveZones(60);

    // P5: Fin geometry
    LNGHeatExchanger.FinGeometry fin = new LNGHeatExchanger.FinGeometry();
    hx.setStreamFinGeometry(0, fin);
    hx.setStreamFinGeometry(1, fin);

    // P8: Core geometry
    LNGHeatExchanger.CoreGeometry core = new LNGHeatExchanger.CoreGeometry();
    core.setLength(6.0);
    core.setWidth(1.2);
    core.setHeight(1.2);
    hx.setCoreGeometry(core);

    // P10: Maldistribution
    hx.setFlowMaldistributionFactor(0.95);

    // Thermal stress limit
    hx.setMaxAllowableThermalGradient(5.0);

    runHX(hx, hotStream, coldStream);

    // Verify all results are available
    assertTrue(hx.getMITA() >= 0.0, "MITA should be non-negative");
    assertTrue(hx.getSecondLawEfficiency() > 0.0, "eta_II should be positive");
    assertTrue(hx.getTotalExergyDestruction() > 0.0, "Exergy destruction should be positive");

    boolean[] freezeRisks = hx.getFreezeOutRiskPerZone();
    assertTrue(freezeRisks.length > 0, "Freeze-out risk array should be populated");

    double[] gradients = hx.getThermalGradientPerZone();
    assertTrue(gradients.length > 0, "Thermal gradient array should be populated");

    double[] jFactors = hx.getStreamJFactor();
    assertTrue(jFactors.length > 0, "j-factor array should be populated");

    // P8: Size the core (this overwrites the initial core geometry)
    hx.sizeCore();
    LNGHeatExchanger.CoreGeometry sized = hx.getCoreGeometry();
    assertTrue(sized.getLength() > 0.0, "Sized core length should be positive");
    assertTrue(sized.getWeight() > 0.0, "Sized core weight should be positive");

    // P7: Transient (needs core thermal mass from sizing)
    hx.runCooldownTransient(-160.0, 20.0, 50, 48.0);
    List<LNGHeatExchanger.TransientPoint> transientPts = hx.getTransientResults();
    assertFalse(transientPts.isEmpty(), "Transient results should not be empty");

    // Mercury
    hx.assessMercuryRisk(0.5);
    assertFalse(hx.isMercuryRiskPresent(), "0.5 ppb should be safe");

    System.out.println("Full integration test passed:");
    System.out.println("  MITA = " + String.format("%.2f", hx.getMITA()) + " C");
    System.out
        .println("  eta_II = " + String.format("%.1f", hx.getSecondLawEfficiency() * 100) + " %");
    System.out.println("  Core: " + String.format("%.1f x %.1f x %.1f m", sized.getLength(),
        sized.getWidth(), sized.getHeight()));
    System.out.println("  Weight: " + String.format("%.0f", sized.getWeight()) + " kg");
    System.out
        .println("  Thermal mass: " + String.format("%.0f", hx.getCoreThermalMass()) + " kJ/K");
    System.out.println("  Cool-down steps: " + transientPts.size());
  }
}
