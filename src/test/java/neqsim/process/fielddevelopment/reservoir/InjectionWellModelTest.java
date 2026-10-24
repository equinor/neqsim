package neqsim.process.fielddevelopment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.reservoir.InjectionWellModel.InjectionType;
import neqsim.process.fielddevelopment.reservoir.InjectionWellModel.InjectionZone;
import neqsim.process.fielddevelopment.reservoir.InjectionWellModel.InjectionZoneResult;
import neqsim.process.fielddevelopment.reservoir.InjectionWellModel.InjectionWellResult;
import neqsim.process.fielddevelopment.reservoir.InjectionWellModel.MultiZoneInjectionResult;
import neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity.Viscosity;
import neqsim.thermo.util.steam.Iapws_if97;

/**
 * Unit tests for InjectionWellModel.
 *
 * @author ESOL
 */
class InjectionWellModelTest {
  private InjectionWellModel model;

  @BeforeEach
  void setUp() {
    model = new InjectionWellModel();
  }

  @Test
  @DisplayName("Test basic water injection calculation")
  void testBasicWaterInjection() {
    // Configure model
    model.setWellType(InjectionType.WATER_INJECTOR);
    model.setReservoirPressure(250.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");
    model.setSkinFactor(2.0);
    model.setWellDepth(3000.0, "m");
    model.setMaxBHP(350.0, "bara");

    // Calculate for target rate
    InjectionWellResult result = model.calculate(10000.0);

    // Verify result
    assertNotNull(result);
    assertTrue(result.injectivityIndex > 0, "II should be positive");
    assertTrue(result.achievableRate > 0, "Achievable rate should be positive");
    assertTrue(result.bottomholePressure > 250.0, "BHP should exceed reservoir pressure");
  }

  @Test
  @DisplayName("Test injectivity index calculation")
  void testInjectivityIndex() {
    model.setReservoirPressure(200.0, "bara");
    model.setFormationPermeability(200.0, "mD"); // Higher perm = higher II
    model.setFormationThickness(50.0, "m"); // Thicker = higher II
    model.setSkinFactor(0.0); // No skin damage

    InjectionWellResult result = model.calculate(5000.0);

    // Higher permeability and thickness should give higher II
    assertTrue(result.injectivityIndex > 10, "II should be significant");
  }

  @Test
  @DisplayName("Test rate limited by pressure constraint")
  void testPressureLimitedRate() {
    model.setReservoirPressure(300.0, "bara");
    model.setMaxBHP(320.0, "bara"); // Low margin above reservoir pressure
    model.setFormationPermeability(50.0, "mD");
    model.setFormationThickness(20.0, "m");

    // Request high rate
    InjectionWellResult result = model.calculate(50000.0);

    // Should be limited by pressure
    assertTrue(result.limitedByPressure, "Should be pressure limited");
    assertTrue(result.achievableRate < 50000.0, "Rate should be limited");
    // BHP should be near maximum (320 bara)
    assertTrue(result.bottomholePressure >= 318.0, "BHP should be near maximum");
  }

  @Test
  @DisplayName("Test pump requirement calculation")
  void testPumpRequirement() {
    model.setReservoirPressure(200.0, "bara");
    model.setMaxBHP(300.0, "bara");
    model.setSurfaceInjectionPressure(50.0, "bara"); // Low surface pressure

    InjectionWellResult result = model.calculate(10000.0);

    if (result.wellheadPressure > 50.0) {
      assertTrue(result.needsPump, "Should need pump when WHP > surface pressure");
      assertTrue(result.pumpPower > 0, "Pump power should be calculated");
      assertTrue(result.requiredPumpDeltaP > 0, "Pump ΔP should be positive");
    }
  }

  @Test
  @DisplayName("Test maximum rate calculation")
  void testMaximumRate() {
    model.setReservoirPressure(200.0, "bara");
    model.setMaxBHP(350.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");

    InjectionWellResult result = model.calculateMaximumRate();

    // Maximum rate should use full pressure margin
    assertTrue(result.limitedByPressure, "Max rate should be pressure limited");
    assertTrue(result.bottomholePressure >= 348.0, "Should use near-maximum BHP");
  }

  @Test
  @DisplayName("Test Hall plot parameters")
  void testHallPlotParameters() {
    model.setReservoirPressure(250.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");
    model.setSkinFactor(5.0); // Some skin damage

    InjectionWellResult result = model.calculate(8000.0);

    // Hall slope should be calculated
    assertTrue(result.hallSlope > 0, "Hall slope should be positive");
    assertTrue(result.skinContribution >= 0, "Skin contribution should be calculated");
  }

  @Test
  @DisplayName("Test pressure interference effects")
  void testPressureInterference() {
    model.setReservoirPressure(250.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");
    model.setMaxBHP(300.0, "bara");

    // Calculate with nearby producers
    double[] producerDistances = {300.0, 400.0}; // m
    double[] producerRates = {5000.0, 6000.0}; // Sm3/day

    InjectionWellResult result = model.calculateWithInterference(10000.0, producerDistances, producerRates);

    // Interference should reduce effective reservoir pressure
    assertTrue(result.interferencePressure >= 0, "Should have interference pressure");
    assertTrue(result.effectiveReservoirPressure <= 250.0, "Effective pressure should be lower or equal");
  }

  @Test
  @DisplayName("Test gas injection")
  void testGasInjection() {
    model.setWellType(InjectionType.GAS_INJECTOR);
    model.setReservoirPressure(200.0, "bara");
    model.setReservoirTemperature(90.0, "C");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");

    InjectionWellResult result = model.calculate(50000.0); // Higher rate for gas

    assertNotNull(result);
    assertEquals(InjectionType.GAS_INJECTOR, result.injectionType);
    assertTrue(result.injectivityIndex > 0, "Gas II should be positive");
  }

  @Test
  @DisplayName("Test skin factor effect on injectivity")
  void testSkinFactorEffect() {
    model.setReservoirPressure(200.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(30.0, "m");

    // No skin
    model.setSkinFactor(0.0);
    InjectionWellResult noSkinResult = model.calculate(5000.0);

    // High skin
    model.setSkinFactor(10.0);
    InjectionWellResult highSkinResult = model.calculate(5000.0);

    // Higher skin should give lower II
    assertTrue(noSkinResult.injectivityIndex > highSkinResult.injectivityIndex,
        "Higher skin should reduce injectivity");
  }

  @Test
  @DisplayName("Test unit conversions")
  void testUnitConversions() {
    // Set in field units
    model.setReservoirPressure(3625.0, "psia"); // ~250 bara
    model.setReservoirTemperature(194.0, "F"); // ~90°C
    model.setFormationThickness(98.4, "ft"); // ~30 m
    model.setWellDepth(9843.0, "ft"); // ~3000 m
    model.setTubingID(4.0, "in"); // ~0.1 m
    model.setMaxBHP(5075.0, "psia"); // ~350 bara

    InjectionWellResult result = model.calculate(10000.0);

    assertNotNull(result);
    assertTrue(result.injectivityIndex > 0, "Should calculate with field units");
  }

  @Test
  @DisplayName("Test result toString format")
  void testResultToString() {
    model.setReservoirPressure(250.0, "bara");
    model.setFormationPermeability(100.0, "mD");

    InjectionWellResult result = model.calculate(10000.0);

    String output = result.toString();
    assertNotNull(output);
    assertTrue(output.contains("Injection Well Result"));
    assertTrue(output.contains("Injectivity index"));
    assertTrue(output.contains("BHP"));
    assertTrue(output.contains("WHP"));
  }

  @Test
  @DisplayName("Test injection pattern sweep efficiency")
  void testInjectionPatternSweep() {
    InjectionWellModel.InjectionPattern fiveSpot = new InjectionWellModel.InjectionPattern(
        InjectionWellModel.InjectionPattern.PatternType.FIVE_SPOT);

    // Favorable mobility ratio
    double sweepFavorable = fiveSpot.getArealSweepEfficiency(0.5);

    // Unfavorable mobility ratio
    double sweepUnfavorable = fiveSpot.getArealSweepEfficiency(5.0);

    assertTrue(sweepFavorable > sweepUnfavorable, "Favorable mobility should give better sweep");
    assertTrue(sweepFavorable > 0.5, "Should have reasonable sweep");
    assertTrue(sweepFavorable < 1.0, "Sweep cannot exceed 100%");
  }

  @Test
  @DisplayName("Test injection pattern configuration")
  void testInjectionPatternConfiguration() {
    InjectionWellModel.InjectionPattern pattern = new InjectionWellModel.InjectionPattern(
        InjectionWellModel.InjectionPattern.PatternType.LINE_DRIVE);

    pattern.setWellSpacing(400.0);

    assertEquals(400.0, pattern.getWellSpacing(), 0.1);
  }

  @Test
  @DisplayName("Multi-zone injectivity uses the radial Darcy SI-to-field conversion")
  void testMultiZoneInjectivityDimensionalConversion() {
    InjectionZone zone = new InjectionZone("target", 2500.0, 250.0, 100.0, 20.0, 400.0);
    zone.skinFactor = 2.0;
    zone.isTargetZone = true;
    model.addZone(zone);
    model.setMaxBHP(390.0, "bara");

    double targetRate = 1000.0;
    MultiZoneInjectionResult result = model.calculateMultiZone(targetRate);

    double permeabilitySI = 100.0 * 9.869e-16;
    double viscositySI = 0.3e-3;
    double expectedII = 2.0 * Math.PI * permeabilitySI * 20.0 / (viscositySI * (Math.log(500.0 / 0.1) + 2.0)) * 86400.0
        * 1e5;
    InjectionZoneResult zoneResult = result.zoneResults.get(0);

    assertEquals(expectedII, zoneResult.injectivityIndex, expectedII * 1e-12);
    assertEquals(targetRate, result.totalRate, 1e-9);
    assertEquals(250.0 + targetRate / expectedII, result.commonBHP, 1e-12);
    assertEquals(1.0, result.injectionEfficiency, 1e-12);
  }

  @Test
  @DisplayName("Multi-zone allocation conserves the requested rate across unequal zones")
  void testMultiZoneAllocationConservesRate() {
    InjectionZone target = new InjectionZone("target", 2500.0, 200.0, 100.0, 20.0, 400.0);
    target.skinFactor = 1.0;
    target.isTargetZone = true;
    InjectionZone nonTarget = new InjectionZone("non-target", 2600.0, 220.0, 50.0, 10.0, 400.0);
    nonTarget.skinFactor = 3.0;
    model.addZone(target);
    model.addZone(nonTarget);
    model.setMaxBHP(390.0, "bara");

    MultiZoneInjectionResult result = model.calculateMultiZone(1000.0);
    double summedZoneRate = result.zoneResults.stream().mapToDouble(zone -> zone.rate).sum();
    double reconstructedRate = result.zoneResults.get(0).injectivityIndex
        * (result.commonBHP - target.reservoirPressure)
        + result.zoneResults.get(1).injectivityIndex * (result.commonBHP - nonTarget.reservoirPressure);

    assertEquals(1000.0, result.totalRate, 1e-9);
    assertEquals(result.totalRate, summedZoneRate, 1e-12);
    assertEquals(result.totalRate, reconstructedRate, 1e-9);
    assertEquals(result.zoneResults.get(0).rate / result.totalRate, result.injectionEfficiency, 1e-12);
    assertEquals(result.zoneResults.get(1).rate, result.outOfZoneRate, 1e-12);
  }

  @Test
  @DisplayName("Multi-zone allocation excludes zones above the solved common BHP")
  void testMultiZoneAllocationUsesOnlyAcceptingZones() {
    InjectionZone accepting = new InjectionZone("accepting", 2500.0, 200.0, 100.0, 20.0, 400.0);
    accepting.isTargetZone = true;
    InjectionZone shutIn = new InjectionZone("shut-in", 2600.0, 250.0, 100.0, 20.0, 400.0);
    model.addZone(accepting);
    model.addZone(shutIn);
    model.setMaxBHP(390.0, "bara");

    double targetRate = 1000.0;
    MultiZoneInjectionResult result = model.calculateMultiZone(targetRate);
    double acceptingII = result.zoneResults.get(0).injectivityIndex;

    assertEquals(200.0 + targetRate / acceptingII, result.commonBHP, 1e-12);
    assertEquals(targetRate, result.totalRate, 1e-9);
    assertEquals(targetRate, result.zoneResults.get(0).rate, 1e-9);
    assertEquals(0.0, result.zoneResults.get(1).rate, 0.0);
    assertEquals(targetRate, result.getRequestedRate(), 0.0);
    assertEquals(result.commonBHP, result.getRequiredBHP(), 0.0);
    assertEquals(390.0, result.getPressureLimit(), 0.0);
    assertFalse(result.isLimitedByPressure());
  }

  @Test
  @DisplayName("Zero target does not create crossflow between unequal-pressure zones")
  void testMultiZoneZeroTargetHasZeroAllocation() {
    model.addZone(new InjectionZone("low-pressure", 2500.0, 200.0, 100.0, 20.0, 400.0));
    model.addZone(new InjectionZone("high-pressure", 2600.0, 250.0, 100.0, 20.0, 400.0));

    MultiZoneInjectionResult result = model.calculateMultiZone(0.0);

    assertEquals(200.0, result.commonBHP, 0.0);
    assertEquals(0.0, result.totalRate, 0.0);
    assertEquals(0.0, result.zoneResults.get(0).rate, 0.0);
    assertEquals(0.0, result.zoneResults.get(1).rate, 0.0);
    assertFalse(result.isLimitedByPressure());
  }

  @Test
  @DisplayName("Multi-zone allocation reports rate shortfall at the pressure limit")
  void testMultiZoneAllocationReportsPressureLimitedRate() {
    InjectionZone accepting = new InjectionZone("accepting", 2500.0, 200.0, 100.0, 20.0, 400.0);
    InjectionZone shutIn = new InjectionZone("shut-in", 2600.0, 250.0, 100.0, 20.0, 400.0);
    model.addZone(accepting);
    model.addZone(shutIn);
    model.setMaxBHP(210.0, "bara");

    MultiZoneInjectionResult result = model.calculateMultiZone(1000.0);
    double expectedRate = result.zoneResults.get(0).injectivityIndex * 10.0;

    assertTrue(result.isLimitedByPressure());
    assertEquals(210.0, result.getPressureLimit(), 0.0);
    assertEquals(210.0, result.commonBHP, 0.0);
    assertTrue(result.getRequiredBHP() > result.getPressureLimit());
    assertEquals(expectedRate, result.totalRate, 1e-9);
    assertTrue(result.totalRate < result.requestedRate);
    assertEquals(0.0, result.zoneResults.get(1).rate, 0.0);
  }

  @Test
  @DisplayName("Multi-zone allocation rejects invalid target rates")
  void testMultiZoneAllocationRejectsInvalidTargetRates() {
    model.addZone(new InjectionZone("target", 2500.0, 200.0, 100.0, 20.0, 400.0));

    assertThrows(IllegalArgumentException.class, () -> model.calculateMultiZone(-1.0));
    assertThrows(IllegalArgumentException.class, () -> model.calculateMultiZone(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> model.calculateMultiZone(Double.POSITIVE_INFINITY));
  }

  @Test
  @DisplayName("Multi-zone allocation rejects a configuration without positive injectivity")
  void testMultiZoneAllocationRejectsNoInjectiveZone() {
    model.addZone(new InjectionZone("sealed", 2500.0, 200.0, 0.0, 20.0, 400.0));

    assertThrows(IllegalStateException.class, () -> model.calculateMultiZone(1000.0));
  }

  @Test
  @DisplayName("Multi-zone injectivity records and applies explicit water PVT inputs")
  void testMultiZoneInjectivityUsesConfiguredWaterPvtInputs() {
    InjectionZone zone = new InjectionZone("target", 2500.0, 250.0, 100.0, 20.0, 400.0);
    zone.skinFactor = 2.0;
    zone.isTargetZone = true;
    model.addZone(zone);
    model.setMaxBHP(390.0, "bara");
    model.setFormationWaterViscosity(0.45);
    model.setFormationWaterVolumeFactor(1.02);

    MultiZoneInjectionResult result = model.calculateMultiZone(1000.0);

    double permeabilitySI = 100.0 * 9.869e-16;
    double expectedII = 2.0 * Math.PI * permeabilitySI * 20.0 / (0.45e-3 * 1.02 * (Math.log(500.0 / 0.1) + 2.0))
        * 86400.0 * 1e5;
    assertEquals(expectedII, result.zoneResults.get(0).injectivityIndex, expectedII * 1e-12);
    assertEquals(0.45, result.formationWaterViscosity, 0.0);
    assertEquals(1.02, result.formationWaterVolumeFactor, 0.0);
    assertEquals(1000.0, result.totalRate, 1e-9);
  }

  @Test
  @DisplayName("Multi-zone water PVT inputs reject nonphysical values")
  void testMultiZoneWaterPvtInputsRejectNonphysicalValues() {
    assertThrows(IllegalArgumentException.class, () -> model.setFormationWaterViscosity(0.0));
    assertThrows(IllegalArgumentException.class, () -> model.setFormationWaterViscosity(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> model.setFormationWaterVolumeFactor(-1.0));
    assertThrows(IllegalArgumentException.class, () -> model.setFormationWaterVolumeFactor(Double.POSITIVE_INFINITY));
  }

  @Test
  @DisplayName("Pure-water PVT reproduces official IF97 volume and IAPWS viscosity verification evidence")
  void testIapwsPureWaterReferenceEvidence() {
    // IAPWS-IF97 revised release, Region 1 verification point: 300 K and 3 MPa.
    assertEquals(1.00215168e-3, Iapws_if97.v_pt(3.0, 300.0), 5.0e-12);

    // IAPWS R12-08 Table 4: 298.15 K, 998 kg/m3, 889.735100 microPa s.
    double lowerPressureMPa = 0.1;
    double upperPressureMPa = 10.0;
    for (int i = 0; i < 80; i++) {
      double pressureMPa = 0.5 * (lowerPressureMPa + upperPressureMPa);
      double density = 1.0 / Iapws_if97.v_pt(pressureMPa, 298.15);
      if (density < 998.0) {
        lowerPressureMPa = pressureMPa;
      } else {
        upperPressureMPa = pressureMPa;
      }
    }
    double matchedPressureBara = 5.0 * (lowerPressureMPa + upperPressureMPa);
    assertEquals(0.889735100, Viscosity.calcWaterViscosity(298.15, matchedPressureBara), 0.0045);
  }

  @Test
  @DisplayName("IAPWS pure-water mode applies pressure-dependent viscosity and volume factor")
  void testIapwsPureWaterPvtFeedsRadialInjectivity() {
    model.setFormationWaterPvtMode(InjectionWellModel.FormationWaterPvtMode.IAPWS_IF97_PURE_WATER);
    model.setReservoirTemperature(300.0, "K");
    model.setReservoirPressure(30.0, "bara");
    model.setFormationPermeability(100.0, "mD");
    model.setFormationThickness(20.0, "m");
    model.setSkinFactor(2.0);

    InjectionWellResult result = model.calculate(1000.0);
    double standardSpecificVolume = Iapws_if97.v_pt(0.101325, 288.15);
    double expectedBw = Iapws_if97.v_pt(3.0, 300.0) / standardSpecificVolume;
    double expectedViscosity = Viscosity.calcWaterViscosity(300.0, 30.0);
    double expectedII = 2.0 * Math.PI * 100.0 * 9.869e-16 * 20.0
        / (expectedViscosity * 1.0e-3 * expectedBw * (Math.log(500.0 / 0.1) + 2.0)) * 86400.0 * 1.0e5;

    assertEquals(InjectionWellModel.FormationWaterPvtMode.IAPWS_IF97_PURE_WATER, result.getFormationWaterPvtMode());
    assertEquals(30.0, result.formationWaterPvtPressure, 0.0);
    assertEquals(expectedViscosity, result.formationWaterViscosity, 0.0);
    assertEquals(expectedBw, result.formationWaterVolumeFactor, 1.0e-12);
    assertEquals(expectedII, result.injectivityIndex, expectedII * 1.0e-12);
  }

  @Test
  @DisplayName("Multi-zone pure-water PVT refreshes each pressure boundary without changing topology")
  void testIapwsPureWaterPvtRefreshesPerZone() {
    model.setFormationWaterPvtMode(InjectionWellModel.FormationWaterPvtMode.IAPWS_IF97_PURE_WATER);
    model.setReservoirTemperature(300.0, "K");
    InjectionZone lowPressure = new InjectionZone("low", 2500.0, 30.0, 100.0, 20.0, 500.0);
    InjectionZone highPressure = new InjectionZone("high", 2600.0, 300.0, 100.0, 20.0, 500.0);
    model.addZone(lowPressure);
    model.addZone(highPressure);
    model.setMaxBHP(490.0, "bara");

    MultiZoneInjectionResult initial = model.calculateMultiZone(1000.0);
    assertEquals(InjectionWellModel.FormationWaterPvtMode.IAPWS_IF97_PURE_WATER, initial.getFormationWaterPvtMode());
    assertEquals(30.0, initial.zoneResults.get(0).formationWaterPvtPressure, 0.0);
    assertEquals(300.0, initial.zoneResults.get(1).formationWaterPvtPressure, 0.0);
    assertTrue(
        initial.zoneResults.get(0).formationWaterVolumeFactor > initial.zoneResults.get(1).formationWaterVolumeFactor);

    double initialVolumeFactor = initial.zoneResults.get(0).formationWaterVolumeFactor;
    lowPressure.reservoirPressure = 40.0;
    MultiZoneInjectionResult changed = model.calculateMultiZone(1000.0);
    assertEquals(40.0, changed.zoneResults.get(0).formationWaterPvtPressure, 0.0);
    assertTrue(changed.zoneResults.get(0).formationWaterVolumeFactor < initialVolumeFactor);
    assertSame(lowPressure, model.getZones().get(0));
  }

  @Test
  @DisplayName("IAPWS pure-water mode fails outside its declared liquid range")
  void testIapwsPureWaterPvtFailsOutsideRange() {
    model.setFormationWaterPvtMode(InjectionWellModel.FormationWaterPvtMode.IAPWS_IF97_PURE_WATER);
    model.setReservoirTemperature(573.15, "K");
    model.setReservoirPressure(700.0, "bara");
    InjectionWellResult upperBoundary = model.calculate(1000.0);
    assertTrue(Double.isFinite(upperBoundary.formationWaterViscosity));
    assertTrue(Double.isFinite(upperBoundary.formationWaterVolumeFactor));

    model.setReservoirPressure(700.0001, "bara");
    assertThrows(IllegalArgumentException.class, () -> model.calculate(1000.0));

    model.setReservoirPressure(1.0, "bara");
    model.setReservoirTemperature(373.15, "K");
    assertThrows(IllegalArgumentException.class, () -> model.calculate(1000.0));
    assertThrows(IllegalArgumentException.class, () -> model.setFormationWaterPvtMode(null));
  }
}
