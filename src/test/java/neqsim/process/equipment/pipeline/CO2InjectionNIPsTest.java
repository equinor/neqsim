package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.ImpurityMonitor;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for NeqSim Improvement Proposals (NIPs) 1-6 related to CO2 injection well analysis.
 *
 * <ul>
 * <li>NIP-1: Formation temperature gradient in PipeBeggsAndBrills</li>
 * <li>NIP-2: ImpurityMonitor measurement device</li>
 * <li>NIP-3: TransientWellbore shutdown model</li>
 * <li>NIP-4: CO2FlowCorrections utility</li>
 * <li>NIP-5: GERG-2008 vs SRK pipe flow comparison</li>
 * <li>NIP-6: CO2InjectionWellAnalyzer</li>
 * </ul>
 */
public class CO2InjectionNIPsTest {

  private static SystemInterface co2Fluid;

  /**
   * Creates a CO2-rich fluid with H2 and N2 impurities (Smeaheia-like composition).
   */
  @BeforeAll
  static void setup() {
    co2Fluid = new SystemSrkEos(273.15 + 25.0, 90.0);
    co2Fluid.addComponent("CO2", 0.97);
    co2Fluid.addComponent("hydrogen", 0.01);
    co2Fluid.addComponent("nitrogen", 0.015);
    co2Fluid.addComponent("methane", 0.005);
    co2Fluid.setMixingRule(2);
    co2Fluid.init(0);
  }

  // ===== NIP-1: Formation Temperature Gradient in PipeBeggsAndBrills =====

  @Test
  public void testFormationTemperatureGradient() {
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 25.0);
    fluid.setPressure(90.0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(150000.0, "kg/hr");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("well", feed);
    pipe.setPipeWallRoughness(4.5e-5);
    pipe.setLength(1300.0);
    pipe.setElevation(-1300.0);
    pipe.setDiameter(0.1571);
    pipe.setNumberOfIncrements(10);

    // Set formation temperature gradient (4C at top, negative gradient means
    // temperature increases with depth for downward injection)
    double gradient = -(43.0 - 4.0) / 1300.0; // K/m (negative for increasing T with depth)
    pipe.setFormationTemperatureGradient(4.0, gradient, "C");
    pipe.setHeatTransferMode(PipeBeggsAndBrills.HeatTransferMode.ESTIMATED_INNER_H);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pipe);
    process.run();

    // Verify gradient was stored
    assertEquals(gradient, pipe.getFormationTemperatureGradient(), 1e-10);
    assertEquals(273.15 + 4.0, pipe.getSurfaceTemperatureAtInlet(), 0.1);

    // Verify outlet has reasonable temperature (should warm up during injection)
    double outletT = pipe.getOutletStream().getTemperature() - 273.15;
    assertTrue(outletT > 20, "Outlet temperature should be warm: " + outletT);
    assertTrue(outletT < 50, "Outlet temperature should be reasonable: " + outletT);
  }

  @Test
  public void testFormationTemperatureGradientKelvin() {
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 25.0);
    fluid.setPressure(90.0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("well-K", feed);
    pipe.setPipeWallRoughness(4.5e-5);
    pipe.setLength(1300.0);
    pipe.setElevation(-1300.0);
    pipe.setDiameter(0.1571);
    pipe.setNumberOfIncrements(5);

    // Set formation temperature gradient in Kelvin
    pipe.setFormationTemperatureGradient(277.15, -0.03, "K");
    assertEquals(277.15, pipe.getSurfaceTemperatureAtInlet(), 0.01);

    pipe.setHeatTransferMode(PipeBeggsAndBrills.HeatTransferMode.ESTIMATED_INNER_H);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pipe);
    process.run();

    double outletP = pipe.getOutletStream().getPressure();
    assertTrue(outletP > 90, "Pressure should increase with depth");
  }

  // ===== NIP-2: ImpurityMonitor =====

  @Test
  public void testImpurityMonitorSinglePhase() {
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 25.0);
    fluid.setPressure(90.0);

    Stream stream = new Stream("monitor-test", fluid);
    stream.setFlowRate(100000.0, "kg/hr");
    stream.run();

    ImpurityMonitor monitor = new ImpurityMonitor("H2-Monitor", stream);
    monitor.addTrackedComponent("hydrogen", 0.04);
    monitor.addTrackedComponent("nitrogen", 0.10);

    // At 25C/90bar, CO2-rich fluid should be single-phase (dense/supercritical)
    double bulkH2 = monitor.getBulkMoleFraction("hydrogen");
    assertEquals(0.01, bulkH2, 0.001, "Bulk H2 should be ~1%");

    // Full report should contain entries for both tracked components
    Map<String, Map<String, Double>> report = monitor.getFullReport();
    assertNotNull(report.get("hydrogen"));
    assertNotNull(report.get("nitrogen"));
    assertNotNull(report.get("_phase_info"));
  }

  @Test
  public void testImpurityMonitorTwoPhase() {
    // Create conditions that produce two-phase (low-P CO2)
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 4.0);
    fluid.setPressure(50.0);

    Stream stream = new Stream("two-phase-test", fluid);
    stream.setFlowRate(50000.0, "kg/hr");
    stream.run();

    ImpurityMonitor monitor = new ImpurityMonitor("H2-Monitor-TP", stream);
    monitor.addTrackedComponent("hydrogen", 0.04);
    monitor.setPrimaryComponent("hydrogen");

    int nPhases = monitor.getNumberOfPhases();
    if (nPhases > 1) {
      // If two-phase, enrichment factor should be > 1 for hydrogen
      double enrichment = monitor.getEnrichmentFactor("hydrogen");
      assertTrue(enrichment > 1.0,
          "H2 enrichment in gas phase should be > 1 in two-phase: " + enrichment);

      // getMeasuredValue should return gas phase H2 in mol%
      double measuredMol = monitor.getMeasuredValue("mol%");
      assertTrue(measuredMol > 1.0, "Gas phase H2 should be > 1 mol%: " + measuredMol);
    }
  }

  @Test
  public void testImpurityMonitorAlarm() {
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 25.0);
    fluid.setPressure(90.0);

    Stream stream = new Stream("alarm-test", fluid);
    stream.setFlowRate(10000.0, "kg/hr");
    stream.run();

    ImpurityMonitor monitor = new ImpurityMonitor("alarm-monitor", stream);
    // Set threshold extremely low so it should exceed
    monitor.addTrackedComponent("hydrogen", 0.001); // 0.1% threshold
    monitor.addTrackedComponent("nitrogen", 0.50); // 50% threshold (should NOT exceed)

    // N2 alarm at 50% should not be exceeded since bulk is 1.5%
    assertFalse(monitor.isAlarmExceeded("nitrogen"));
  }

  // ===== NIP-3: TransientWellbore =====

  @Test
  public void testTransientWellboreShutdown() {
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 25.0);
    fluid.setPressure(90.0);

    Stream feed = new Stream("well-feed", fluid);
    feed.setFlowRate(150000.0, "kg/hr");
    feed.run();

    TransientWellbore well = new TransientWellbore("shutdown-well", feed);
    well.setWellDepth(1300.0);
    well.setTubingDiameter(0.1571);
    well.setFormationTemperature(277.15, 316.15); // 4C at top, 43C at bottom
    well.setShutdownCoolingRate(5.0); // 5-hour time constant
    well.setNumberOfSegments(10);

    well.runShutdownSimulation(12.0, 2.0); // 12 hours, 2-hour steps

    // Should have 7 snapshots (t=0, 2, 4, 6, 8, 10, 12)
    List<TransientWellbore.TransientSnapshot> snapshots = well.getSnapshots();
    assertEquals(7, snapshots.size(), "Should have 7 snapshots");

    // Time points should be 0, 2, 4, 6, 8, 10, 12
    double[] timePoints = well.getTimePoints();
    assertEquals(0.0, timePoints[0], 1e-6);
    assertEquals(12.0, timePoints[timePoints.length - 1], 1e-6);

    // Temperature at wellhead should cool toward formation temperature (4C)
    TransientWellbore.TransientSnapshot lastSnap = snapshots.get(snapshots.size() - 1);
    double finalWellheadT = lastSnap.temperatures[0] - 273.15;
    assertTrue(finalWellheadT < 25.0, "Wellhead T should cool from initial 25C: " + finalWellheadT);
    assertTrue(finalWellheadT > 2.0, "Wellhead T shouldn't be below formation: " + finalWellheadT);

    // Temperature profiles should exist
    List<double[]> profiles = well.getTemperatureProfiles();
    assertEquals(7, profiles.size());
  }

  @Test
  public void testTransientWellboreDepressurization() {
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 25.0);
    fluid.setPressure(90.0);

    Stream feed = new Stream("depress-feed", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.run();

    TransientWellbore well = new TransientWellbore("depress-well", feed);
    well.setWellDepth(1300.0);
    well.setTubingDiameter(0.1571);
    well.setFormationTemperature(277.15, 316.15);
    well.setShutdownCoolingRate(4.0);
    well.setDepressurizationRate(5.0); // 5 bar/hr
    well.setNumberOfSegments(10);

    well.runShutdownSimulation(10.0, 2.0);

    // At t=10h with 5 bar/hr depress, WHP should drop from 90 to 40 bara
    List<TransientWellbore.TransientSnapshot> snapshots = well.getSnapshots();
    TransientWellbore.TransientSnapshot lastSnap = snapshots.get(snapshots.size() - 1);
    double finalWHP = lastSnap.pressures[0];
    assertEquals(40.0, finalWHP, 5.0, "WHP should be ~40 bara after 10h depressurization");
  }

  @Test
  public void testTransientWellboreRunMethodDefault() {
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 25.0);
    fluid.setPressure(90.0);

    Stream feed = new Stream("run-feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");

    TransientWellbore well = new TransientWellbore("run-well", feed);
    well.setWellDepth(1300.0);
    well.setFormationTemperature(277.15, 316.15);
    well.setNumberOfSegments(5);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(well);
    process.run();

    // Default run should create 25 snapshots (0 to 24 hours, 1-hr steps)
    assertEquals(25, well.getSnapshots().size());
  }

  // ===== NIP-4: CO2FlowCorrections =====

  @Test
  public void testCO2DominatedFluidDetection() {
    // CO2-rich fluid (97%)
    SystemInterface co2Rich = co2Fluid.clone();
    assertTrue(CO2FlowCorrections.isCO2DominatedFluid(co2Rich));

    // Methane-dominated fluid
    SystemInterface methane = new SystemSrkEos(273.15 + 25.0, 50.0);
    methane.addComponent("methane", 0.95);
    methane.addComponent("CO2", 0.05);
    methane.setMixingRule(2);
    methane.init(0);
    assertFalse(CO2FlowCorrections.isCO2DominatedFluid(methane));
  }

  @Test
  public void testCO2HoldupCorrectionFactor() {
    // Subcritical CO2 conditions
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 10.0); // 10C, Tr ~ 0.93
    fluid.setPressure(50.0);
    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
    ops.TPflash();

    double factor = CO2FlowCorrections.getLiquidHoldupCorrectionFactor(fluid);
    assertTrue(factor >= 0.7 && factor <= 1.0,
        "Holdup correction should be between 0.7 and 1.0: " + factor);
  }

  @Test
  public void testCO2FrictionCorrectionFactor() {
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 25.0);
    fluid.setPressure(90.0);

    double factor = CO2FlowCorrections.getFrictionCorrectionFactor(fluid);
    assertTrue(factor > 0 && factor <= 1.0,
        "Friction correction should be between 0 and 1: " + factor);
  }

  @Test
  public void testCO2SurfaceTension() {
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(273.15 + 10.0); // Subcritical
    fluid.setPressure(50.0);

    double sigma = CO2FlowCorrections.estimateCO2SurfaceTension(fluid);
    assertTrue(sigma > 0, "Surface tension should be positive below Tc: " + sigma);
    assertTrue(sigma < 0.01, "Surface tension should be small for CO2: " + sigma);

    // Above Tc — surface tension should be 0
    fluid.setTemperature(320.0); // Above Tc=304.13
    double sigmaAbove = CO2FlowCorrections.estimateCO2SurfaceTension(fluid);
    assertEquals(0.0, sigmaAbove, 1e-10, "Surface tension should be 0 above Tc");
  }

  @Test
  public void testDensePhaseDetection() {
    // Dense phase (T > Tc, P > Pc)
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(310.0); // > 304.13 K
    fluid.setPressure(80.0); // > 73.77 bara
    assertTrue(CO2FlowCorrections.isDensePhase(fluid));

    // Gas phase (T > Tc, P < Pc)
    fluid.setPressure(50.0);
    assertFalse(CO2FlowCorrections.isDensePhase(fluid));
  }

  @Test
  public void testReducedProperties() {
    SystemInterface fluid = co2Fluid.clone();
    fluid.setTemperature(304.13); // Exactly Tc
    fluid.setPressure(73.77); // Exactly Pc

    assertEquals(1.0, CO2FlowCorrections.getReducedTemperature(fluid), 0.01);
    assertEquals(1.0, CO2FlowCorrections.getReducedPressure(fluid), 0.01);
  }

  // ===== NIP-5: GERG-2008 vs SRK Pipe Flow Comparison =====

  @Test
  public void testSRKPipeFlowBaseline() {
    // This tests that SRK-based pipe flow works correctly for CO2 injection
    // as a baseline for future GERG-2008 comparison
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 90.0);
    fluid.addComponent("CO2", 0.97);
    fluid.addComponent("hydrogen", 0.01);
    fluid.addComponent("nitrogen", 0.015);
    fluid.addComponent("methane", 0.005);
    fluid.setMixingRule(2);

    Stream feed = new Stream("srk-feed", fluid);
    feed.setFlowRate(150000.0, "kg/hr");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("srk-well", feed);
    pipe.setPipeWallRoughness(4.5e-5);
    pipe.setLength(1300.0);
    pipe.setElevation(-1300.0);
    pipe.setDiameter(0.1571);
    pipe.setNumberOfIncrements(10);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pipe);
    process.run();

    double outletP = pipe.getOutletStream().getPressure();
    double outletT = pipe.getOutletStream().getTemperature() - 273.15;

    // BHP should be higher than WHP due to hydrostatic head
    assertTrue(outletP > 90.0, "BHP should exceed WHP: " + outletP);
    assertTrue(outletP < 200.0, "BHP shouldn't be unreasonably high: " + outletP);

    // Temperature should be reasonable
    assertTrue(outletT > 10.0 && outletT < 60.0, "BHT should be reasonable: " + outletT);
  }

  // ===== NIP-6: CO2InjectionWellAnalyzer =====

  @Test
  public void testAnalyzerFullRun() {
    CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("Smeaheia");
    analyzer.setFluid(co2Fluid.clone());
    analyzer.setWellGeometry(1300.0, 0.1571, 4.5e-5);
    analyzer.setOperatingConditions(90.0, 25.0, 150000.0);
    analyzer.setFormationTemperature(4.0, 43.0);
    analyzer.addTrackedComponent("hydrogen", 0.04);
    analyzer.addTrackedComponent("nitrogen", 0.10);

    assertFalse(analyzer.isAnalysisComplete());

    analyzer.runFullAnalysis();

    assertTrue(analyzer.isAnalysisComplete());
    assertEquals("Smeaheia", analyzer.getName());

    Map<String, Object> results = analyzer.getResults();
    assertNotNull(results.get("design_case"));
    assertNotNull(results.get("phase_boundary_scan"));
    assertNotNull(results.get("enrichment_map"));
    assertNotNull(results.get("shutdown_assessment"));
    assertNotNull(results.get("safe_operating_envelope"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testAnalyzerDesignCase() {
    CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("DesignTest");
    analyzer.setFluid(co2Fluid.clone());
    analyzer.setWellGeometry(1300.0, 0.1571, 4.5e-5);
    analyzer.setOperatingConditions(90.0, 25.0, 150000.0);
    analyzer.setFormationTemperature(4.0, 43.0);

    analyzer.runFullAnalysis();

    Map<String, Object> designCase = (Map<String, Object>) analyzer.getResults().get("design_case");

    double bhp = (double) designCase.get("BHP_bara");
    double bht = (double) designCase.get("BHT_C");

    assertTrue(bhp > 90, "BHP should exceed WHP: " + bhp);
    assertTrue(bht > 20 && bht < 60, "BHT should be reasonable: " + bht);
  }

  @Test
  public void testAnalyzerSafeOperation() {
    CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("SafetyTest");
    analyzer.setFluid(co2Fluid.clone());
    analyzer.setWellGeometry(1300.0, 0.1571, 4.5e-5);
    // High pressure, warm temperature — should be safe (single-phase)
    analyzer.setOperatingConditions(90.0, 25.0, 150000.0);
    analyzer.setFormationTemperature(4.0, 43.0);

    analyzer.runFullAnalysis();

    // At 90 bar, 25C, 97% CO2 should be single-phase
    boolean safe = analyzer.isSafeToOperate();
    assertTrue(safe, "High-pressure injection should be safe");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testAnalyzerPhaseBoundaryScan() {
    CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("PhaseScan");
    analyzer.setFluid(co2Fluid.clone());
    analyzer.setWellGeometry(1300.0, 0.1571, 4.5e-5);
    analyzer.setOperatingConditions(90.0, 25.0, 150000.0);
    analyzer.setFormationTemperature(4.0, 43.0);

    analyzer.runFullAnalysis();

    Map<String, Object> phaseScan =
        (Map<String, Object>) analyzer.getResults().get("phase_boundary_scan");

    int twoPhasePoints = (int) phaseScan.get("two_phase_points_found");
    // CO2 with impurities should have a two-phase region
    assertTrue(twoPhasePoints > 0, "Should find two-phase region for impure CO2");
  }
}
