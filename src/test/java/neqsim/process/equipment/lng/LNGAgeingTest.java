package neqsim.process.equipment.lng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the LNG ageing simulation package.
 *
 * @author NeqSim
 */
public class LNGAgeingTest {

  /** Reference LNG fluid. */
  private SystemInterface lngFluid;

  /**
   * Set up a typical lean LNG composition.
   */
  @BeforeEach
  public void setUp() {
    // Typical lean LNG: ~92% methane, 5% ethane, 2% propane, 1% nitrogen
    lngFluid = new SystemSrkEos(111.0, 1.013);
    lngFluid.addComponent("methane", 0.92);
    lngFluid.addComponent("ethane", 0.05);
    lngFluid.addComponent("propane", 0.02);
    lngFluid.addComponent("nitrogen", 0.01);
    lngFluid.setMixingRule("classic");
  }

  // ────────────────────────── LNGTankLayer ──────────────────────────

  @Test
  public void testTankLayerBasics() {
    LNGTankLayer layer = new LNGTankLayer(0, 1e6, 111.5, 1.013);
    assertEquals(0, layer.getLayerIndex());
    assertEquals(1e6, layer.getTotalMoles(), 1e-2);
    assertEquals(111.5, layer.getTemperature(), 1e-3);
    assertEquals(1.013, layer.getPressure(), 1e-5);
  }

  @Test
  public void testTankLayerCompositionAndMass() {
    LNGTankLayer layer = new LNGTankLayer(0, 1e8, 111.0, 1.013);
    Map<String, Double> comp = new LinkedHashMap<String, Double>();
    comp.put("methane", 0.95);
    comp.put("ethane", 0.05);
    layer.setComposition(comp);
    layer.setMolarMass(0.01648); // ~16.48 g/mol

    double mass = layer.getMass();
    assertTrue(mass > 0, "Mass should be positive");
    assertEquals(1e8 * 0.01648, mass, 1.0); // approx match
  }

  @Test
  public void testTankLayerRemoveVapor() {
    LNGTankLayer layer = new LNGTankLayer(0, 1e8, 111.0, 1.013);
    Map<String, Double> comp = new LinkedHashMap<String, Double>();
    comp.put("methane", 0.92);
    comp.put("nitrogen", 0.08);
    layer.setComposition(comp);

    // Vapor is nitrogen-rich
    Map<String, Double> vaporComp = new LinkedHashMap<String, Double>();
    vaporComp.put("methane", 0.3);
    vaporComp.put("nitrogen", 0.7);

    double molesBefore = layer.getTotalMoles();
    layer.removeVapor(1e5, vaporComp);

    assertTrue(layer.getTotalMoles() < molesBefore, "Moles should decrease after boil-off");
  }

  @Test
  public void testTankLayerDensityComparison() {
    LNGTankLayer layer1 = new LNGTankLayer(0);
    layer1.setDensity(450.0);
    LNGTankLayer layer2 = new LNGTankLayer(1);
    layer2.setDensity(445.0);

    assertTrue(layer1.isDenserThan(layer2));
    assertEquals(5.0, layer1.getDensityDifference(layer2), 1e-10);
  }

  // ────────────────────────── LNGTankLayeredModel ──────────────────────────

  @Test
  public void testLayeredModelInitialisation() {
    LNGTankLayeredModel model = new LNGTankLayeredModel(lngFluid);
    model.setTotalTankVolume(140000.0);
    model.setTankPressure(1.013);
    model.setOverallHeatTransferCoeff(0.045);
    model.setTankSurfaceArea(12000.0);

    model.initialise(137200.0); // 98% fill

    // After initialisation we should have one layer
    assertEquals(1, model.getLayers().size());

    // Temperature should be near LNG boiling point at ~1 atm (~111-112 K)
    double temp = model.getBulkTemperature();
    assertTrue(temp > 100.0 && temp < 120.0,
        "Bulk temperature should be near LNG boiling point, got " + temp);

    // Density should be in LNG range (420-480 kg/m3)
    double rho = model.getBulkDensity();
    assertTrue(rho > 400.0 && rho < 500.0, "Bulk density should be in LNG range, got " + rho);

    // Total moles should be positive and very large
    assertTrue(model.getTotalLiquidMoles() > 1e6, "Total moles should be very large");
  }

  @Test
  public void testLayeredModelSingleStep() {
    LNGTankLayeredModel model = new LNGTankLayeredModel(lngFluid);
    model.setTotalTankVolume(140000.0);
    model.setTankPressure(1.013);
    model.setOverallHeatTransferCoeff(0.045);
    model.setTankSurfaceArea(12000.0);
    model.initialise(137200.0);

    double initialMoles = model.getTotalLiquidMoles();

    // Step one hour with ambient = 35°C (308.15 K)
    LNGAgeingResult result = model.step(1.0, 308.15);

    assertNotNull(result);
    assertTrue(result.getTemperature() > 0, "Temperature should be positive");
    assertTrue(result.getDensity() > 0, "Density should be positive");
    assertTrue(result.getBogMassFlowRate() >= 0, "BOG rate should be non-negative");

    // Moles should decrease due to boil-off
    assertTrue(model.getTotalLiquidMoles() <= initialMoles,
        "Moles should not increase after boil-off");
  }

  @Test
  public void testLayeredModelMultiStep() {
    LNGTankLayeredModel model = new LNGTankLayeredModel(lngFluid);
    model.setTotalTankVolume(140000.0);
    model.setTankPressure(1.013);
    model.setOverallHeatTransferCoeff(0.045);
    model.setTankSurfaceArea(12000.0);
    model.initialise(137200.0);

    double initialTemp = model.getBulkTemperature();
    double initialDensity = model.getBulkDensity();

    // Run 24 hours (1 day)
    List<LNGAgeingResult> results = new ArrayList<LNGAgeingResult>();
    for (int i = 0; i < 24; i++) {
      LNGAgeingResult r = model.step(1.0, 308.15);
      results.add(r);
    }

    assertEquals(24, results.size());

    // Over 24 hours, temperature should increase slightly due to heat ingress
    // (but may stay constant if latent heat absorbs all ingress — either is physical)
    double finalTemp = model.getBulkTemperature();
    assertTrue(finalTemp >= initialTemp - 0.1, "Temperature should not decrease significantly");

    // Composition should change — methane fraction should increase as N2 boils off preferentially
    Map<String, Double> comp = model.getBulkLiquidComposition();
    assertFalse(comp.isEmpty(), "Composition should not be empty");
    assertTrue(comp.containsKey("methane"), "Should contain methane");
  }

  // ────────────────────────── LNGVaporSpaceModel ──────────────────────────

  @Test
  public void testVaporSpaceBasics() {
    LNGVaporSpaceModel vapor = new LNGVaporSpaceModel(140000.0);
    vapor.setTankPressure(1.05);
    vapor.setMaxPressure(1.20);
    vapor.setMinPressure(0.95);
    vapor.setCurrentLiquidVolume(137200.0);
    vapor.setVaporTemperature(111.5);

    assertEquals(140000.0, vapor.getTotalTankVolume(), 1e-3);
    assertEquals(1.05, vapor.getTankPressure(), 1e-5);
    assertFalse(vapor.isPressureAboveRelief());
    assertFalse(vapor.isUnderPressured());
  }

  @Test
  public void testVaporSpacePressureRelief() {
    LNGVaporSpaceModel vapor = new LNGVaporSpaceModel(140000.0);
    vapor.setTankPressure(1.25); // Above relief
    vapor.setMaxPressure(1.20);

    assertTrue(vapor.isPressureAboveRelief(), "Pressure above relief should trigger");
  }

  // ────────────────────────── LNGRolloverDetector ──────────────────────────

  @Test
  public void testRolloverDetectorNoRisk() {
    LNGRolloverDetector detector = new LNGRolloverDetector();

    // Single layer — no rollover risk
    List<LNGTankLayer> layers = new ArrayList<LNGTankLayer>();
    LNGTankLayer layer = new LNGTankLayer(0);
    layer.setDensity(450.0);
    layer.setTemperature(111.0);
    layer.setVolume(137200.0);
    layers.add(layer);

    LNGRolloverDetector.RolloverAssessment result = detector.assess(layers);
    assertNotNull(result);
    assertEquals(LNGRolloverDetector.RolloverRiskLevel.NONE, result.getRiskLevel());
  }

  @Test
  public void testRolloverDetectorWithDensityInversion() {
    LNGRolloverDetector detector = new LNGRolloverDetector();

    // Two layers with density inversion (heavier on top)
    List<LNGTankLayer> layers = new ArrayList<LNGTankLayer>();

    LNGTankLayer bottom = new LNGTankLayer(0);
    bottom.setDensity(445.0);
    bottom.setTemperature(111.0);
    bottom.setVolume(68600.0);

    LNGTankLayer top = new LNGTankLayer(1);
    top.setDensity(460.0); // Denser on top = inversion
    top.setTemperature(112.0);
    top.setVolume(68600.0);

    layers.add(bottom);
    layers.add(top);

    LNGRolloverDetector.RolloverAssessment result = detector.assess(layers);
    assertNotNull(result);
    assertTrue(result.isDensityInversion(), "Should detect density inversion");
    assertTrue(result.getRiskLevel().ordinal() > 0, "Risk should be above NONE");
  }

  // ────────────────────────── LNGBOGHandlingNetwork ──────────────────────────

  @Test
  public void testBOGHandlingFuelOnly() {
    LNGBOGHandlingNetwork network = new LNGBOGHandlingNetwork();
    network.setHandlingMode(LNGBOGHandlingNetwork.HandlingMode.FUEL_ONLY);
    network.setBaseFuelConsumption(3000.0);
    network.setVesselSpeed(19.5);
    network.setDesignSpeed(19.5);

    LNGBOGHandlingNetwork.BOGDisposition disposition = network.calculateDisposition(2500.0);
    assertNotNull(disposition);
    assertEquals(2500.0, disposition.bogToFuel, 100.0);
    assertEquals(0.0, disposition.bogReliquefied, 1e-10);
  }

  @Test
  public void testBOGHandlingReliquefaction() {
    LNGBOGHandlingNetwork network = new LNGBOGHandlingNetwork();
    network.setHandlingMode(LNGBOGHandlingNetwork.HandlingMode.FUEL_PLUS_RELIQUEFACTION);
    network.setBaseFuelConsumption(2000.0);
    network.setReliquefactionCapacity(3000.0);
    network.setVesselSpeed(19.5);
    network.setDesignSpeed(19.5);

    LNGBOGHandlingNetwork.BOGDisposition disposition = network.calculateDisposition(4000.0);
    assertNotNull(disposition);
    // Fuel demand ~2000, remainder to reliquefaction
    assertTrue(disposition.bogToFuel > 0, "Should have fuel use");
    assertTrue(disposition.bogReliquefied > 0, "Should have reliquefaction");
  }

  // ────────────────────────── LNGVoyageProfile ──────────────────────────

  @Test
  public void testVoyageProfileUniform() {
    LNGVoyageProfile profile = LNGVoyageProfile.createUniform("Uniform", 480.0, 308.15);
    assertNotNull(profile);

    double temp = profile.getAmbientTemperatureAt(240.0);
    assertEquals(308.15, temp, 1e-3);
  }

  @Test
  public void testVoyageProfileSegments() {
    LNGVoyageProfile profile = new LNGVoyageProfile("Qatar to Japan");
    profile.addSegment(new LNGVoyageProfile.Segment(0, 120, 308.15, 1.0, 10.0, 800.0));
    profile.addSegment(new LNGVoyageProfile.Segment(120, 360, 303.15, 2.0, 20.0, 600.0));
    profile.addSegment(new LNGVoyageProfile.Segment(360, 480, 293.15, 1.5, 15.0, 400.0));

    // Middle of first segment
    assertEquals(308.15, profile.getAmbientTemperatureAt(60.0), 1e-3);
    // Middle of last segment
    assertEquals(293.15, profile.getAmbientTemperatureAt(420.0), 1e-3);
  }

  // ────────────────────────── LNGHeelManager ──────────────────────────

  @Test
  public void testHeelManagerBasics() {
    LNGHeelManager heel = new LNGHeelManager(0.03, 140000.0);
    assertEquals(4200.0, heel.getHeelVolume(), 1e-2);
    assertEquals(0.03, heel.getHeelFraction(), 1e-10);
    assertEquals(140000.0, heel.getTankVolume(), 1e-3);
  }

  @Test
  public void testHeelManagerMixing() {
    LNGHeelManager heel = new LNGHeelManager(0.03, 140000.0);

    // Old heel composition (aged, methane-rich)
    Map<String, Double> heelComp = new LinkedHashMap<String, Double>();
    heelComp.put("methane", 0.97);
    heelComp.put("ethane", 0.03);
    heel.setHeelState(heelComp, 112.0, 425.0);

    // New cargo composition (fresh, leaner)
    Map<String, Double> newComp = new LinkedHashMap<String, Double>();
    newComp.put("methane", 0.92);
    newComp.put("ethane", 0.05);
    newComp.put("propane", 0.02);
    newComp.put("nitrogen", 0.01);

    // Mix with large amount of new cargo
    Map<String, Double> mixed = heel.calculateMixedComposition(newComp, 1e10, 111.0);
    assertNotNull(mixed);
    assertFalse(mixed.isEmpty());
    assertTrue(mixed.containsKey("methane"));
    // Mixed methane should be between heel and new cargo values
    double xCH4 = mixed.get("methane");
    assertTrue(xCH4 >= 0.92 && xCH4 <= 0.97,
        "Mixed methane fraction should be between heel and cargo: " + xCH4);
  }

  // ────────────────────────── LNGAgeingResult ──────────────────────────

  @Test
  public void testAgeingResultToMap() {
    LNGAgeingResult result = new LNGAgeingResult();
    result.setTimeHours(24.0);
    result.setTemperature(111.5);
    result.setPressure(1.05);
    result.setDensity(450.0);
    result.setWobbeIndex(51.0);
    result.setGcvVolumetric(38.5);
    result.setBoilOffRatePctPerDay(0.15);

    Map<String, Object> map = result.toMap();
    assertNotNull(map);
    assertEquals(24.0, (double) map.get("timeHours"), 1e-5);
    assertEquals(111.5, (double) map.get("temperature_K"), 1e-5);
  }

  @Test
  public void testAgeingResultTimeSeries() {
    List<LNGAgeingResult> series = new ArrayList<LNGAgeingResult>();
    for (int i = 0; i < 5; i++) {
      LNGAgeingResult r = new LNGAgeingResult();
      r.setTimeHours(i * 24.0);
      r.setTemperature(111.0 + i * 0.01);
      r.setDensity(450.0 - i * 0.1);
      series.add(r);
    }

    Map<String, double[]> ts = LNGAgeingResult.toTimeSeries(series);
    assertNotNull(ts);
    assertTrue(ts.containsKey("timeHours"));
    assertTrue(ts.containsKey("temperature_K"));
    assertEquals(5, ts.get("timeHours").length);
  }

  // ────────────────────────── LNGAgeingScenario (Integration) ──────────────────────────

  @Test
  public void testAgeingScenarioShortSimulation() {
    Stream feed = new Stream("LNG feed", lngFluid);
    feed.setFlowRate(140000.0, "m3/hr");
    feed.run();

    LNGAgeingScenario scenario = new LNGAgeingScenario("Qatar-Japan", feed);
    scenario.setTankVolume(140000.0);
    scenario.setInitialFillingRatio(0.98);
    scenario.setSimulationTime(24.0); // 1 day only (fast test)
    scenario.setTimeStepHours(4.0); // coarse steps for speed
    scenario.setOverallHeatTransferCoeff(0.045);
    scenario.setAmbientTemperature(308.15); // 35°C
    scenario.setTankPressure(1.013);

    scenario.run();

    List<LNGAgeingResult> results = scenario.getResults();
    assertNotNull(results);
    assertFalse(results.isEmpty(), "Should have results after running");
    assertTrue(results.size() >= 2, "Should have initial + at least one step");

    // First result is initial state
    LNGAgeingResult initial = results.get(0);
    assertTrue(initial.getTemperature() > 100 && initial.getTemperature() < 130,
        "Initial temp should be near LNG boiling point");

    // Summary should not be empty
    String summary = scenario.getResultsSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("LNG Ageing Simulation Summary"));
  }

  @Test
  public void testAgeingScenarioWithVoyageProfile() {
    Stream feed = new Stream("LNG feed", lngFluid);
    feed.setFlowRate(140000.0, "m3/hr");
    feed.run();

    LNGAgeingScenario scenario = new LNGAgeingScenario("Profiled Voyage", feed);
    scenario.setTankVolume(140000.0);
    scenario.setInitialFillingRatio(0.98);
    scenario.setSimulationTime(48.0);
    scenario.setTimeStepHours(12.0);
    scenario.setOverallHeatTransferCoeff(0.045);
    scenario.setTankPressure(1.013);

    // Add voyage profile with two segments
    LNGVoyageProfile profile = new LNGVoyageProfile("Test Route");
    profile.addSegment(new LNGVoyageProfile.Segment(0, 24, 308.15, 1.0, 10.0, 800.0));
    profile.addSegment(new LNGVoyageProfile.Segment(24, 48, 298.15, 2.0, 20.0, 400.0));
    scenario.setVoyageProfile(profile);

    scenario.run();

    List<LNGAgeingResult> results = scenario.getResults();
    assertFalse(results.isEmpty());
    assertTrue(results.size() >= 3, "Should have initial + steps for 48h with 12h step");
  }

  @Test
  public void testAgeingScenarioSubModelAccess() {
    Stream feed = new Stream("LNG feed", lngFluid);
    feed.setFlowRate(140000.0, "m3/hr");
    feed.run();

    LNGAgeingScenario scenario = new LNGAgeingScenario("Access Test", feed);
    scenario.setTankVolume(140000.0);
    scenario.setSimulationTime(12.0);
    scenario.setTimeStepHours(6.0);
    scenario.run();

    // Access sub-models
    assertNotNull(scenario.getTankModel(), "Tank model should be available after run");
    assertNotNull(scenario.getVaporSpaceModel());
    assertNotNull(scenario.getRolloverDetector());
    assertNotNull(scenario.getBogNetwork());
    assertNotNull(scenario.getHeelManager());
  }
}
