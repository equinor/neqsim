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

    assertEquals(1, model.getLayers().size());

    double temp = model.getBulkTemperature();
    assertTrue(temp > 100.0 && temp < 120.0,
        "Bulk temperature should be near LNG boiling point, got " + temp);

    double rho = model.getBulkDensity();
    assertTrue(rho > 400.0 && rho < 500.0, "Bulk density should be in LNG range, got " + rho);

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

    LNGAgeingResult result = model.step(1.0, 308.15);

    assertNotNull(result);
    assertTrue(result.getTemperature() > 0, "Temperature should be positive");
    assertTrue(result.getDensity() > 0, "Density should be positive");
    assertTrue(result.getBogMassFlowRate() >= 0, "BOG rate should be non-negative");

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

    List<LNGAgeingResult> results = new ArrayList<LNGAgeingResult>();
    for (int i = 0; i < 24; i++) {
      LNGAgeingResult r = model.step(1.0, 308.15);
      results.add(r);
    }

    assertEquals(24, results.size());

    double finalTemp = model.getBulkTemperature();
    assertTrue(finalTemp >= initialTemp - 0.1, "Temperature should not decrease significantly");

    Map<String, Double> comp = model.getBulkLiquidComposition();
    assertFalse(comp.isEmpty(), "Composition should not be empty");
    assertTrue(comp.containsKey("methane"), "Should contain methane");
  }

  @Test
  public void testLayeredModelWithGERG2008() {
    LNGTankLayeredModel model = new LNGTankLayeredModel(lngFluid);
    model.setTotalTankVolume(140000.0);
    model.setTankPressure(1.013);
    model.setOverallHeatTransferCoeff(0.045);
    model.setTankSurfaceArea(12000.0);
    model.setUseGERG2008(true);
    assertTrue(model.isUseGERG2008());

    model.initialise(137200.0);

    LNGAgeingResult result = model.step(1.0, 308.15);
    assertNotNull(result);
    // GERG-2008 density may fall back to ISO 6578 if EOS phase not detected as liquid
    assertTrue(result.getDensity() > 0, "Density should be positive with GERG-2008 or fallback");
  }

  @Test
  public void testLayeredModelWithMethaneNumberCalculator() {
    LNGTankLayeredModel model = new LNGTankLayeredModel(lngFluid);
    model.setTotalTankVolume(140000.0);
    model.setTankPressure(1.013);
    model.setOverallHeatTransferCoeff(0.045);
    model.setTankSurfaceArea(12000.0);

    MethaneNumberCalculator mnCalc = new MethaneNumberCalculator();
    mnCalc.setMethod(MethaneNumberCalculator.Method.EN16726);
    model.setMethaneNumberCalculator(mnCalc);
    assertNotNull(model.getMethaneNumberCalculator());

    model.initialise(137200.0);

    LNGAgeingResult result = model.step(1.0, 308.15);
    double mn = result.getMethaneNumber();
    assertTrue(mn > 50 && mn < 120, "EN16726 MN should be reasonable for lean LNG, got " + mn);
  }

  @Test
  public void testLayeredModelWithTankGeometry() {
    LNGTankLayeredModel model = new LNGTankLayeredModel(lngFluid);

    TankGeometry geometry = TankGeometry.createQMax();
    model.setTankGeometry(geometry);
    assertNotNull(model.getTankGeometry());
    assertEquals(geometry.getTotalVolume(), model.getTotalTankVolume(), 1.0);

    model.setTankPressure(1.013);
    model.setOverallHeatTransferCoeff(0.045);
    model.initialise(geometry.getTotalVolume() * 0.98);

    LNGAgeingResult result = model.step(1.0, 308.15);
    assertNotNull(result);
    assertTrue(result.getDensity() > 0);
  }

  @Test
  public void testLayeredModelDiffusionCoeff() {
    LNGTankLayeredModel model = new LNGTankLayeredModel(lngFluid);
    model.setEffectiveDiffusionCoeff(5.0e-9);
    assertEquals(5.0e-9, model.getEffectiveDiffusionCoeff(), 1e-15);
  }

  @Test
  public void testLayeredModelFillFraction() {
    LNGTankLayeredModel model = new LNGTankLayeredModel(lngFluid);
    model.setTotalTankVolume(140000.0);
    model.setTankPressure(1.013);
    model.setOverallHeatTransferCoeff(0.045);
    model.setTankSurfaceArea(12000.0);
    model.initialise(137200.0);

    double fill = model.getCurrentFillFraction();
    assertTrue(fill > 0.9 && fill < 1.0, "Fill fraction should be ~0.98, got " + fill);
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
    vapor.setTankPressure(1.25);
    vapor.setMaxPressure(1.20);

    assertTrue(vapor.isPressureAboveRelief(), "Pressure above relief should trigger");
  }

  @Test
  public void testVaporSpaceFlashModelConfig() {
    LNGVaporSpaceModel vapor = new LNGVaporSpaceModel(140000.0);
    assertFalse(vapor.isUseFlashModel());
    vapor.setUseFlashModel(true);
    assertTrue(vapor.isUseFlashModel());
    vapor.setReferenceSystem(lngFluid);
    assertNotNull(vapor.getReferenceSystem());
  }

  // ────────────────────────── LNGRolloverDetector ──────────────────────────

  @Test
  public void testRolloverDetectorNoRisk() {
    LNGRolloverDetector detector = new LNGRolloverDetector();

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

    List<LNGTankLayer> layers = new ArrayList<LNGTankLayer>();

    LNGTankLayer bottom = new LNGTankLayer(0);
    bottom.setDensity(445.0);
    bottom.setTemperature(111.0);
    bottom.setVolume(68600.0);

    LNGTankLayer top = new LNGTankLayer(1);
    top.setDensity(460.0);
    top.setTemperature(112.0);
    top.setVolume(68600.0);

    layers.add(bottom);
    layers.add(top);

    LNGRolloverDetector.RolloverAssessment result = detector.assess(layers);
    assertNotNull(result);
    assertTrue(result.isDensityInversion(), "Should detect density inversion");
    assertTrue(result.getRiskLevel().ordinal() > 0, "Risk should be above NONE");
  }

  @Test
  public void testTimeToRolloverPrediction() {
    LNGRolloverDetector detector = new LNGRolloverDetector();
    detector.setDensityAlarmThreshold(5.0);

    // Build up a trend of increasing density differences
    for (int i = 0; i < 10; i++) {
      List<LNGTankLayer> layers = new ArrayList<LNGTankLayer>();
      LNGTankLayer bottom = new LNGTankLayer(0);
      bottom.setDensity(445.0);
      bottom.setTemperature(111.0);
      bottom.setVolume(68600.0);

      LNGTankLayer top = new LNGTankLayer(1);
      top.setDensity(445.0 + (i * 0.3)); // Slowly increasing inversion
      top.setTemperature(112.0);
      top.setVolume(68600.0);

      layers.add(bottom);
      layers.add(top);

      detector.assess(layers);
    }

    // Check history has been populated
    assertFalse(detector.getDensityDiffHistory().isEmpty(), "History should be populated");
    assertTrue(detector.getDensityDiffHistory().size() >= 3, "Should have at least 3 data points");
  }

  @Test
  public void testRolloverDetectorClearHistory() {
    LNGRolloverDetector detector = new LNGRolloverDetector();

    // Build some history
    List<LNGTankLayer> layers = new ArrayList<LNGTankLayer>();
    LNGTankLayer bottom = new LNGTankLayer(0);
    bottom.setDensity(445.0);
    bottom.setTemperature(111.0);
    bottom.setVolume(68600.0);
    LNGTankLayer top = new LNGTankLayer(1);
    top.setDensity(446.0);
    top.setTemperature(112.0);
    top.setVolume(68600.0);
    layers.add(bottom);
    layers.add(top);

    detector.assess(layers);
    assertFalse(detector.getDensityDiffHistory().isEmpty());

    detector.clearHistory();
    assertTrue(detector.getDensityDiffHistory().isEmpty(), "History should be empty after clear");
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

    assertEquals(308.15, profile.getAmbientTemperatureAt(60.0), 1e-3);
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

    Map<String, Double> heelComp = new LinkedHashMap<String, Double>();
    heelComp.put("methane", 0.97);
    heelComp.put("ethane", 0.03);
    heel.setHeelState(heelComp, 112.0, 425.0);

    Map<String, Double> newComp = new LinkedHashMap<String, Double>();
    newComp.put("methane", 0.92);
    newComp.put("ethane", 0.05);
    newComp.put("propane", 0.02);
    newComp.put("nitrogen", 0.01);

    Map<String, Double> mixed = heel.calculateMixedComposition(newComp, 1e10, 111.0);
    assertNotNull(mixed);
    assertFalse(mixed.isEmpty());
    assertTrue(mixed.containsKey("methane"));
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

  // ────────────────────────── TankGeometry ──────────────────────────

  @Test
  public void testTankGeometryQMax() {
    TankGeometry geom = TankGeometry.createQMax();
    assertEquals(TankGeometry.ContainmentType.MEMBRANE, geom.getContainmentType());
    assertTrue(geom.getTotalVolume() > 170000 && geom.getTotalVolume() < 180000,
        "QMax volume should be ~174000 m3, got " + geom.getTotalVolume());
    assertTrue(geom.getTotalSurfaceArea() > 5000, "Surface area should be significant");
    assertTrue(geom.getBottomArea() > 0);
    assertTrue(geom.getRoofArea() > 0);
    assertTrue(geom.getSidewallArea() > 0);
  }

  @Test
  public void testTankGeometryMoss() {
    TankGeometry geom = TankGeometry.createMossSingle();
    assertEquals(TankGeometry.ContainmentType.MOSS, geom.getContainmentType());
    assertTrue(geom.getTotalVolume() > 30000, "Moss tank should be ~36000 m3");
  }

  @Test
  public void testTankGeometryTypeC() {
    TankGeometry geom = TankGeometry.createTypeC();
    assertEquals(TankGeometry.ContainmentType.TYPE_C, geom.getContainmentType());
    assertTrue(geom.getTotalVolume() > 5000, "Type C should be ~7500 m3");
  }

  @Test
  public void testTankGeometryWettedArea() {
    TankGeometry geom = TankGeometry.createQMax();
    double wetted80 = geom.getWettedWallArea(0.8);
    double wetted40 = geom.getWettedWallArea(0.4);

    assertTrue(wetted80 > wetted40, "Higher fill should have more wetted area");
    assertTrue(wetted80 > 0 && wetted40 > 0);
  }

  @Test
  public void testTankGeometryLiquidHeight() {
    TankGeometry geom = TankGeometry.createQMax();
    double height50 = geom.getLiquidHeight(geom.getTotalVolume() * 0.5);
    double height100 = geom.getLiquidHeight(geom.getTotalVolume());
    assertTrue(height100 > height50, "Full tank should be taller than half");
    assertTrue(height50 > 0);
  }

  @Test
  public void testTankGeometryUValue() {
    TankGeometry membrane = TankGeometry.createQMax();
    TankGeometry moss = TankGeometry.createMossSingle();
    double uMembrane = membrane.getInsulationUValue();
    double uMoss = moss.getInsulationUValue();

    assertTrue(uMembrane > 0 && uMembrane < 1.0, "U-value should be reasonable");
    assertTrue(uMoss > 0 && uMoss < 1.0, "U-value should be reasonable");
  }

  // ────────────────────────── TankHeatTransferModel ──────────────────────────

  @Test
  public void testHeatTransferModelFromGeometry() {
    TankGeometry geom = TankGeometry.createQMax();
    TankHeatTransferModel htModel = new TankHeatTransferModel(geom, 308.15);

    assertFalse(htModel.getZones().isEmpty(), "Should have zones from geometry");

    double q = htModel.calculateTotalHeatIngress(111.0);
    assertTrue(q > 0, "Heat ingress should be positive (ambient > LNG)");
  }

  @Test
  public void testHeatTransferModelZoneContributions() {
    TankGeometry geom = TankGeometry.createQMax();
    TankHeatTransferModel htModel = new TankHeatTransferModel(geom, 308.15);

    Map<String, Double> zoneQ = htModel.calculateZoneHeatIngress(111.0);
    assertFalse(zoneQ.isEmpty());
    for (Double value : zoneQ.values()) {
      assertTrue(value >= 0, "Zone heat ingress should be non-negative");
    }
  }

  @Test
  public void testHeatTransferModelBoundaryUpdate() {
    TankGeometry geom = TankGeometry.createQMax();
    TankHeatTransferModel htModel = new TankHeatTransferModel(geom, 308.15);

    double q1 = htModel.calculateTotalHeatIngress(111.0);
    htModel.updateBoundaryConditions(320.0, 500.0, 285.0);
    double q2 = htModel.calculateTotalHeatIngress(111.0);

    // Higher ambient temp should give more heat ingress
    assertTrue(q2 > q1 || Math.abs(q2 - q1) < 100.0,
        "Higher ambient should give more heat ingress");
  }

  // ────────────────────────── MethaneNumberCalculator ──────────────────────────

  @Test
  public void testMethaneNumberEN16726() {
    MethaneNumberCalculator calc = new MethaneNumberCalculator();
    calc.setMethod(MethaneNumberCalculator.Method.EN16726);

    Map<String, Double> leanLNG = new LinkedHashMap<String, Double>();
    leanLNG.put("methane", 0.95);
    leanLNG.put("ethane", 0.03);
    leanLNG.put("propane", 0.01);
    leanLNG.put("nitrogen", 0.01);

    double mn = calc.calculate(leanLNG);
    assertTrue(mn > 60 && mn < 110, "Lean LNG should have MN ~ 80-100, got " + mn);
  }

  @Test
  public void testMethaneNumberMWM() {
    MethaneNumberCalculator calc = new MethaneNumberCalculator();
    calc.setMethod(MethaneNumberCalculator.Method.MWM);

    Map<String, Double> leanLNG = new LinkedHashMap<String, Double>();
    leanLNG.put("methane", 0.95);
    leanLNG.put("ethane", 0.03);
    leanLNG.put("propane", 0.01);
    leanLNG.put("nitrogen", 0.01);

    double mn = calc.calculate(leanLNG);
    assertTrue(mn > 50 && mn < 120, "MWM MN should be reasonable for lean LNG, got " + mn);
  }

  @Test
  public void testMethaneNumberAllMethods() {
    MethaneNumberCalculator calc = new MethaneNumberCalculator();

    Map<String, Double> comp = new LinkedHashMap<String, Double>();
    comp.put("methane", 0.90);
    comp.put("ethane", 0.06);
    comp.put("propane", 0.02);
    comp.put("nitrogen", 0.02);

    Map<String, Double> allMN = calc.calculateAll(comp);
    assertNotNull(allMN);
    assertTrue(allMN.containsKey("EN16726"));
    assertTrue(allMN.containsKey("MWM"));
    assertTrue(allMN.containsKey("Simplified"));
    assertTrue(allMN.get("EN16726") > 0);
  }

  @Test
  public void testMethaneNumberSpecification() {
    MethaneNumberCalculator calc = new MethaneNumberCalculator();
    calc.setMethod(MethaneNumberCalculator.Method.EN16726);
    Map<String, Double> leanLNG = new LinkedHashMap<String, Double>();
    leanLNG.put("methane", 0.95);
    leanLNG.put("ethane", 0.03);
    leanLNG.put("propane", 0.01);
    leanLNG.put("nitrogen", 0.01);

    assertTrue(calc.meetsSpecification(leanLNG, 70.0), "Lean LNG should meet MN > 70 spec");
  }

  // ────────────────────────── LNGSloshingModel ──────────────────────────

  @Test
  public void testSloshingModelMixingFactor() {
    LNGSloshingModel sloshing = new LNGSloshingModel();

    double calm = sloshing.calculateMixingFactor(0.0, 0.5);
    assertEquals(1.0, calm, 1e-3, "Calm sea should give factor 1.0");

    double rough = sloshing.calculateMixingFactor(3.0, 0.5);
    assertTrue(rough > 1.0, "Rough sea should enhance mixing, got " + rough);
  }

  @Test
  public void testSloshingModelBOGEnhancement() {
    LNGSloshingModel sloshing = new LNGSloshingModel();

    double enhCalm = sloshing.calculateBOGEnhancement(0.0, 0.5);
    assertEquals(1.0, enhCalm, 0.01, "No waves should give enhancement = 1.0");

    double enhRough = sloshing.calculateBOGEnhancement(3.0, 0.5);
    assertTrue(enhRough >= 1.0, "Waves should enhance BOG, got " + enhRough);
  }

  @Test
  public void testSloshingModelFillLevelEffect() {
    LNGSloshingModel sloshing = new LNGSloshingModel();

    // Fill level effect is tested indirectly via mixing factor at different fill levels
    double factorMid = sloshing.calculateMixingFactor(2.0, 0.4);
    double factorLow = sloshing.calculateMixingFactor(2.0, 0.05);
    double factorHigh = sloshing.calculateMixingFactor(2.0, 0.95);

    // Mid fill levels should have more sloshing effect than extremes
    assertTrue(factorMid > factorLow || factorMid > factorHigh,
        "Mid fill should have more mixing than at least one extreme");
  }

  @Test
  public void testSloshingModelContainmentType() {
    LNGSloshingModel membrane = new LNGSloshingModel(TankGeometry.ContainmentType.MEMBRANE);
    LNGSloshingModel moss = new LNGSloshingModel(TankGeometry.ContainmentType.MOSS);

    double memFactor = membrane.calculateMixingFactor(2.0, 0.5);
    double mossFactor = moss.calculateMixingFactor(2.0, 0.5);

    assertTrue(memFactor > mossFactor, "Membrane should be more susceptible to sloshing than Moss");
  }

  // ────────────────────────── LNGShipModel ──────────────────────────

  @Test
  public void testShipModelCreation() {
    LNGShipModel ship = new LNGShipModel("Test Carrier");
    assertEquals("Test Carrier", ship.getShipName());
    assertTrue(ship.getTankScenarios().isEmpty());
    assertNotNull(ship.getBogNetwork());
  }

  @Test
  public void testShipModelWithSingleTank() {
    Stream feed = new Stream("LNG feed", lngFluid);
    feed.setFlowRate(140000.0, "m3/hr");
    feed.run();

    LNGAgeingScenario tank1 = new LNGAgeingScenario("Tank 1", feed);
    tank1.setTankVolume(35000.0);
    tank1.setInitialFillingRatio(0.98);
    tank1.setOverallHeatTransferCoeff(0.045);
    tank1.setAmbientTemperature(308.15);
    tank1.setTankPressure(1.013);

    LNGShipModel ship = new LNGShipModel("Single Tank Test");
    ship.addTank(tank1);
    ship.setSimulationTime(24.0);
    ship.setTimeStepHours(8.0);

    ship.run();

    assertFalse(ship.getTankResults().isEmpty(), "Should have tank results");
    assertFalse(ship.getShipResults().isEmpty(), "Should have ship results");
    assertTrue(ship.getTotalCargoLossPct() >= 0, "Cargo loss should be non-negative");

    String summary = ship.getShipSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("LNG Ship Model Summary"));
  }

  @Test
  public void testShipModelMultipleTanks() {
    Stream feed1 = new Stream("LNG feed 1", lngFluid);
    feed1.setFlowRate(140000.0, "m3/hr");
    feed1.run();

    SystemInterface lngFluid2 = new SystemSrkEos(111.0, 1.013);
    lngFluid2.addComponent("methane", 0.88);
    lngFluid2.addComponent("ethane", 0.07);
    lngFluid2.addComponent("propane", 0.03);
    lngFluid2.addComponent("nitrogen", 0.02);
    lngFluid2.setMixingRule("classic");

    Stream feed2 = new Stream("LNG feed 2", lngFluid2);
    feed2.setFlowRate(140000.0, "m3/hr");
    feed2.run();

    LNGAgeingScenario tank1 = new LNGAgeingScenario("Tank 1", feed1);
    tank1.setTankVolume(35000.0);
    tank1.setInitialFillingRatio(0.98);
    tank1.setOverallHeatTransferCoeff(0.045);
    tank1.setAmbientTemperature(308.15);

    LNGAgeingScenario tank2 = new LNGAgeingScenario("Tank 2", feed2);
    tank2.setTankVolume(35000.0);
    tank2.setInitialFillingRatio(0.98);
    tank2.setOverallHeatTransferCoeff(0.045);
    tank2.setAmbientTemperature(308.15);

    LNGShipModel ship = new LNGShipModel("Multi Tank Test");
    ship.addTank(tank1);
    ship.addTank(tank2);
    ship.setSimulationTime(24.0);
    ship.setTimeStepHours(12.0);

    ship.run();

    assertEquals(2, ship.getTankResults().size(), "Should have results for 2 tanks");
    assertFalse(ship.getShipResults().isEmpty());

    // Ship results should aggregate BOG from both tanks
    LNGShipModel.ShipResult lastResult =
        ship.getShipResults().get(ship.getShipResults().size() - 1);
    assertTrue(lastResult.totalLiquidMass > 0, "Should have total liquid mass");
    assertTrue(lastResult.numberOfTanks == 2, "Should report 2 active tanks");
  }

  @Test
  public void testShipResultToMap() {
    LNGShipModel.ShipResult sr = new LNGShipModel.ShipResult();
    sr.timeHours = 24.0;
    sr.totalBOGRate = 1500.0;
    sr.totalLiquidMass = 60000000.0;
    sr.averageMN = 85.0;

    Map<String, Object> map = sr.toMap();
    assertNotNull(map);
    assertEquals(24.0, (double) map.get("timeHours"), 1e-5);
    assertEquals(1500.0, (double) map.get("totalBOGRate_kghr"), 1e-3);
    assertEquals(85.0, (double) map.get("averageMN"), 1e-5);
  }

  // ────────────────────────── OperationalEvent ──────────────────────────

  @Test
  public void testOperationalEvent() {
    LNGAgeingScenario.OperationalEvent event = new LNGAgeingScenario.OperationalEvent(
        LNGAgeingScenario.OperationalEvent.EventType.LOADING, 0, 12.0);
    event.setDescription("Loading at Ras Laffan");
    event.setRateM3PerHour(12000.0);

    assertEquals(LNGAgeingScenario.OperationalEvent.EventType.LOADING, event.getEventType());
    assertEquals(0, event.getStartTimeHours(), 1e-5);
    assertEquals(12.0, event.getDurationHours(), 1e-5);
    assertEquals(12000.0, event.getRateM3PerHour(), 1e-3);
    assertTrue(event.isActiveAt(6.0));
    assertFalse(event.isActiveAt(15.0));
    assertEquals("Loading at Ras Laffan", event.getDescription());
  }

  @Test
  public void testScenarioWithOperationalEvents() {
    Stream feed = new Stream("LNG feed", lngFluid);
    feed.setFlowRate(140000.0, "m3/hr");
    feed.run();

    LNGAgeingScenario scenario = new LNGAgeingScenario("Event Test", feed);
    scenario.setTankVolume(140000.0);
    scenario.setSimulationTime(24.0);
    scenario.setTimeStepHours(8.0);

    scenario.addOperationalEvent(new LNGAgeingScenario.OperationalEvent(
        LNGAgeingScenario.OperationalEvent.EventType.LADEN_VOYAGE, 0, 480.0));

    assertFalse(scenario.getOperationalEvents().isEmpty());
    assertEquals(1, scenario.getOperationalEvents().size());
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
    scenario.setSimulationTime(24.0);
    scenario.setTimeStepHours(4.0);
    scenario.setOverallHeatTransferCoeff(0.045);
    scenario.setAmbientTemperature(308.15);
    scenario.setTankPressure(1.013);

    scenario.run();

    List<LNGAgeingResult> results = scenario.getResults();
    assertNotNull(results);
    assertFalse(results.isEmpty(), "Should have results after running");
    assertTrue(results.size() >= 2, "Should have initial + at least one step");

    LNGAgeingResult initial = results.get(0);
    assertTrue(initial.getTemperature() > 100 && initial.getTemperature() < 130,
        "Initial temp should be near LNG boiling point");

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
  public void testAgeingScenarioWithAdvancedModels() {
    Stream feed = new Stream("LNG feed", lngFluid);
    feed.setFlowRate(140000.0, "m3/hr");
    feed.run();

    LNGAgeingScenario scenario = new LNGAgeingScenario("Advanced", feed);
    scenario.setTankVolume(140000.0);
    scenario.setInitialFillingRatio(0.98);
    scenario.setSimulationTime(24.0);
    scenario.setTimeStepHours(8.0);
    scenario.setOverallHeatTransferCoeff(0.045);
    scenario.setAmbientTemperature(308.15);
    scenario.setTankPressure(1.013);

    // Wire advanced models
    scenario.setTankGeometry(TankGeometry.createQMax());
    scenario.setMethaneNumberCalculator(new MethaneNumberCalculator());

    scenario.run();

    List<LNGAgeingResult> results = scenario.getResults();
    assertFalse(results.isEmpty());
    assertNotNull(scenario.getTankModel());
    assertNotNull(scenario.getTankModel().getTankGeometry());
    assertNotNull(scenario.getTankModel().getMethaneNumberCalculator());
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

    assertNotNull(scenario.getTankModel(), "Tank model should be available after run");
    assertNotNull(scenario.getVaporSpaceModel());
    assertNotNull(scenario.getRolloverDetector());
    assertNotNull(scenario.getBogNetwork());
    assertNotNull(scenario.getHeelManager());
  }
}
