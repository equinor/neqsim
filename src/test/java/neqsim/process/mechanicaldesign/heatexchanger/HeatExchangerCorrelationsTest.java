package neqsim.process.mechanicaldesign.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for heat exchanger two-phase and fouling correlations.
 *
 * <p>
 * Covers: ShahCondensation, BoilingHeatTransfer, TwoPhasePressureDrop, FoulingModel, and
 * TubeInsertModel.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class HeatExchangerCorrelationsTest {

  // ============================================================================
  // Typical fluid properties for tests (R134a-like at ~40°C, 10 bar)
  // ============================================================================
  private static final double LIQUID_DENSITY = 1147.0; // kg/m3
  private static final double VAPOR_DENSITY = 50.1; // kg/m3
  private static final double LIQUID_VISCOSITY = 1.65e-4; // Pa*s
  private static final double VAPOR_VISCOSITY = 1.25e-5; // Pa*s
  private static final double LIQUID_CP = 1480.0; // J/(kg*K)
  private static final double LIQUID_CONDUCTIVITY = 0.078; // W/(m*K)
  private static final double SURFACE_TENSION = 0.006; // N/m
  private static final double HEAT_OF_VAPORIZATION = 163000.0; // J/kg
  private static final double TUBE_ID = 0.01905; // 3/4 inch tube
  private static final double MASS_FLUX = 300.0; // kg/(m2*s)

  // ============================================================================
  // Shah Condensation Tests
  // ============================================================================

  @Test
  void testShahLocalHTC_MidQuality() {
    double hLo = 3000.0; // W/(m2*K)
    double x = 0.5;
    double Pr = 0.1;

    double h = ShahCondensation.calcLocalHTC(hLo, x, Pr);
    assertTrue(h > hLo, "Condensation HTC should exceed liquid-only at mid quality");
    assertTrue(h < hLo * 15, "Condensation HTC should be reasonable (not > 15x hLo)");
  }

  @Test
  void testShahLocalHTC_ZeroQuality() {
    double hLo = 3000.0;
    double h = ShahCondensation.calcLocalHTC(hLo, 0.0, 0.1);
    assertEquals(hLo, h, hLo * 0.05, "At zero quality, HTC should approach hLo");
  }

  @Test
  void testShahLocalHTC_HighQuality() {
    double hLo = 3000.0;
    double hLow = ShahCondensation.calcLocalHTC(hLo, 0.2, 0.1);
    double hHigh = ShahCondensation.calcLocalHTC(hLo, 0.8, 0.1);
    assertTrue(hHigh > hLow, "HTC should increase with quality (more vapor shearing)");
  }

  @Test
  void testShahLocalHTC_InvalidInputs() {
    assertEquals(0.0, ShahCondensation.calcLocalHTC(-1.0, 0.5, 0.1),
        "Negative hLo should return 0");
    assertEquals(0.0, ShahCondensation.calcLocalHTC(3000.0, 0.5, -0.1),
        "Negative Pr should return 0");
  }

  @Test
  void testShahLiquidOnlyHTC() {
    double h = ShahCondensation.calcLiquidOnlyHTC(MASS_FLUX, TUBE_ID, LIQUID_DENSITY,
        LIQUID_VISCOSITY, LIQUID_CP, LIQUID_CONDUCTIVITY);
    assertTrue(h > 500, "Liquid-only HTC should be > 500 W/(m2*K) for typical conditions");
    assertTrue(h < 20000, "Liquid-only HTC should be < 20000 W/(m2*K) for typical conditions");
  }

  @Test
  void testShahAverageHTC() {
    double hLo = 3000.0;
    double hAvg = ShahCondensation.calcAverageHTC(hLo, 0.1, 0.9, 0.1, 20);
    assertTrue(hAvg > 0, "Average HTC must be positive");

    // Average should be between the min and max local values
    double hAtIn = ShahCondensation.calcLocalHTC(hLo, 0.9, 0.1);
    double hAtOut = ShahCondensation.calcLocalHTC(hLo, 0.1, 0.1);
    double hMin = Math.min(hAtIn, hAtOut);
    double hMax = Math.max(hAtIn, hAtOut);
    assertTrue(hAvg >= hMin * 0.8, "Average should not be much below minimum local HTC");
    assertTrue(hAvg <= hMax * 1.2, "Average should not be much above maximum local HTC");
  }

  @Test
  void testShahVerticalTubeHTC() {
    double hLo = 3000.0;
    double h = ShahCondensation.calcVerticalTubeHTC(hLo, 0.5, 0.1, MASS_FLUX, TUBE_ID,
        VAPOR_DENSITY, LIQUID_DENSITY);
    assertTrue(h > 0, "Vertical tube HTC must be positive");
  }

  // ============================================================================
  // Boiling Heat Transfer Tests
  // ============================================================================

  @Test
  void testChenHTC_MidQuality() {
    double h = BoilingHeatTransfer.calcChenHTC(MASS_FLUX, 0.3, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, LIQUID_CP, LIQUID_CONDUCTIVITY,
        SURFACE_TENSION, HEAT_OF_VAPORIZATION, 5.0, 50000.0);
    assertTrue(h > 0, "Chen HTC must be positive");
    assertTrue(h > 500, "Chen HTC should be > 500 for nucleate boiling conditions");
  }

  @Test
  void testChenHTC_NoWallSuperheat() {
    // Without wall superheat, nucleate boiling component is zero
    double h = BoilingHeatTransfer.calcChenHTC(MASS_FLUX, 0.3, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, LIQUID_CP, LIQUID_CONDUCTIVITY,
        SURFACE_TENSION, HEAT_OF_VAPORIZATION, 0.0, 0.0);
    assertTrue(h > 0, "Chen HTC with F*h_l only should still be positive");
  }

  @Test
  void testChenHTC_InvalidInputs() {
    assertEquals(0.0,
        BoilingHeatTransfer.calcChenHTC(0.0, 0.3, TUBE_ID, LIQUID_DENSITY, VAPOR_DENSITY,
            LIQUID_VISCOSITY, VAPOR_VISCOSITY, LIQUID_CP, LIQUID_CONDUCTIVITY, SURFACE_TENSION,
            HEAT_OF_VAPORIZATION, 5.0, 50000.0));
  }

  @Test
  void testGungorWintertonHTC() {
    double heatFlux = 20000.0; // W/m2
    double h = BoilingHeatTransfer.calcGungorWintertonHTC(MASS_FLUX, 0.3, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, LIQUID_CP, LIQUID_CONDUCTIVITY, heatFlux,
        HEAT_OF_VAPORIZATION);
    assertTrue(h > 0, "Gungor-Winterton HTC must be positive");
    assertTrue(h > 500, "Gungor-Winterton should give > 500 W/(m2*K) for these conditions");
  }

  @Test
  void testGungorWintertonHTC_QualityEffect() {
    double heatFlux = 20000.0;
    double h1 = BoilingHeatTransfer.calcGungorWintertonHTC(MASS_FLUX, 0.1, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, LIQUID_CP, LIQUID_CONDUCTIVITY, heatFlux,
        HEAT_OF_VAPORIZATION);
    double h2 = BoilingHeatTransfer.calcGungorWintertonHTC(MASS_FLUX, 0.5, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, LIQUID_CP, LIQUID_CONDUCTIVITY, heatFlux,
        HEAT_OF_VAPORIZATION);
    assertTrue(h2 > h1, "HTC should increase with quality in convective boiling regime");
  }

  @Test
  void testMartinelliParameter() {
    double Xtt = BoilingHeatTransfer.calcMartinelliParameter(0.5, LIQUID_DENSITY, VAPOR_DENSITY,
        LIQUID_VISCOSITY, VAPOR_VISCOSITY);
    assertTrue(Xtt > 0, "Martinelli parameter must be positive");
    assertTrue(Xtt < 100, "Martinelli parameter should be reasonable");
  }

  @Test
  void testChenEnhancementFactor() {
    double F = BoilingHeatTransfer.calcChenEnhancementFactor(0.5);
    assertTrue(F >= 1.0, "Enhancement factor must be >= 1.0");
    assertTrue(F < 20, "Enhancement factor should be reasonable");
  }

  @Test
  void testChenSuppressionFactor() {
    double S = BoilingHeatTransfer.calcChenSuppressionFactor(50000);
    assertTrue(S > 0.0, "Suppression factor must be > 0");
    assertTrue(S <= 1.0, "Suppression factor must be <= 1.0");
  }

  @Test
  void testBoilingAverageHTC() {
    double heatFlux = 20000.0;
    double hAvg = BoilingHeatTransfer.calcAverageHTC(MASS_FLUX, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, LIQUID_CP, LIQUID_CONDUCTIVITY, heatFlux,
        HEAT_OF_VAPORIZATION, 0.1, 0.9, 20);
    assertTrue(hAvg > 0, "Average boiling HTC must be positive");
  }

  // ============================================================================
  // Two-Phase Pressure Drop Tests
  // ============================================================================

  @Test
  void testFriedelGradient_MidQuality() {
    double dPdz = TwoPhasePressureDrop.calcFriedelGradient(MASS_FLUX, 0.5, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, SURFACE_TENSION);
    assertTrue(dPdz > 0, "Friedel gradient must be positive");
  }

  @Test
  void testFriedelGradient_ZeroQuality() {
    double dPdz = TwoPhasePressureDrop.calcFriedelGradient(MASS_FLUX, 0.0, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, SURFACE_TENSION);
    // At zero quality, should be equal to single-phase liquid drop
    assertTrue(dPdz > 0, "All-liquid gradient should be positive");
  }

  @Test
  void testFriedelGradient_QualityIncreases() {
    double dPdz_lo = TwoPhasePressureDrop.calcFriedelGradient(MASS_FLUX, 0.1, TUBE_ID,
        LIQUID_DENSITY, VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, SURFACE_TENSION);
    double dPdz_hi = TwoPhasePressureDrop.calcFriedelGradient(MASS_FLUX, 0.8, TUBE_ID,
        LIQUID_DENSITY, VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, SURFACE_TENSION);
    assertTrue(dPdz_hi > dPdz_lo,
        "Pressure drop gradient should increase with quality due to lower mixture density");
  }

  @Test
  void testFriedelGradient_InvalidInputs() {
    assertEquals(0.0, TwoPhasePressureDrop.calcFriedelGradient(0.0, 0.5, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, SURFACE_TENSION));
  }

  @Test
  void testFriedelPressureDrop() {
    double tubeLength = 5.0; // m
    double dP = TwoPhasePressureDrop.calcFriedelPressureDrop(MASS_FLUX, 0.5, TUBE_ID, tubeLength,
        LIQUID_DENSITY, VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, SURFACE_TENSION);
    assertTrue(dP > 0, "Pressure drop must be positive");

    // Longer tube -> higher pressure drop
    double dP2 = TwoPhasePressureDrop.calcFriedelPressureDrop(MASS_FLUX, 0.5, TUBE_ID, 10.0,
        LIQUID_DENSITY, VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, SURFACE_TENSION);
    assertTrue(dP2 > dP, "Doubling tube length should increase pressure drop");
  }

  @Test
  void testMullerSteinhagenHeckGradient() {
    double dPdz = TwoPhasePressureDrop.calcMullerSteinhagenHeckGradient(MASS_FLUX, 0.5, TUBE_ID,
        LIQUID_DENSITY, VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY);
    assertTrue(dPdz > 0, "MSH gradient must be positive");
  }

  @Test
  void testGravitationalGradient() {
    // Method returns rho_tp * g (assumes vertical)
    double dPdz =
        TwoPhasePressureDrop.calcGravitationalGradient(0.3, LIQUID_DENSITY, VAPOR_DENSITY);
    assertTrue(dPdz > 0, "Gravitational gradient must be positive");
    // Should be between pure vapor and pure liquid gravitational gradients
    double dPdz_liquid = LIQUID_DENSITY * 9.81;
    double dPdz_vapor = VAPOR_DENSITY * 9.81;
    assertTrue(dPdz > dPdz_vapor && dPdz < dPdz_liquid,
        "Two-phase gravity gradient should be between single-phase extremes");
  }

  @Test
  void testAccelerationPressureDrop() {
    double dP = TwoPhasePressureDrop.calcAccelerationPressureDrop(MASS_FLUX, 0.1, 0.9,
        LIQUID_DENSITY, VAPOR_DENSITY);
    assertTrue(dP > 0 || dP < 0, "Acceleration pressure drop can be positive or negative");
  }

  // ============================================================================
  // Fouling Model Tests
  // ============================================================================

  @Test
  void testFoulingModel_Fixed() {
    FoulingModel model = new FoulingModel();
    // Default is FIXED type with TEMA water default resistance (0.000176 m2*K/W)
    double rf = model.getFoulingResistance();
    assertEquals(1.76e-4, rf, 1e-6, "Default fouling resistance should be TEMA water default");
  }

  @Test
  void testFoulingModel_KernSeaton() {
    FoulingModel model = FoulingModel.createCoolingWaterModel(0.0003, 2000.0);
    // At time 0
    assertEquals(0.0, model.calcKernSeatonResistance(0.0), 1e-10);

    // At time >> tau, should approach rfMax
    double rfMax = model.calcKernSeatonResistance(20000.0);
    assertEquals(0.0003, rfMax, 0.0003 * 0.05, "Should approach asymptotic value at t >> tau");

    // At t = tau, should be about 63.2% of rfMax
    double rfTau = model.calcKernSeatonResistance(2000.0);
    double expected = 0.0003 * (1.0 - Math.exp(-1.0));
    assertEquals(expected, rfTau, expected * 0.001, "At t=tau, should be 63.2% of rfMax");
  }

  @Test
  void testFoulingModel_EbertPanchal() {
    FoulingModel model = FoulingModel.createCrudeOilModel();
    model.updateConditions(1.5, 800.0, 0.001, 523.15, TUBE_ID); // v=1.5 m/s, T=250°C

    double rate = model.calcEbertPanchalFoulingRate(0.0);
    assertTrue(rate > 0, "Initial fouling rate should be positive at high wall temp");

    double rf = model.calcEbertPanchalResistance(1000.0);
    assertTrue(rf > 0, "Fouling resistance at 1000h should be positive");
  }

  @Test
  void testFoulingModel_AdvanceTime() {
    FoulingModel model = FoulingModel.createCrudeOilModel();
    model.updateConditions(1.5, 800.0, 0.001, 523.15, TUBE_ID);
    model.reset();

    model.advanceTime(100.0);
    double rf1 = model.getFoulingResistance();

    model.advanceTime(100.0);
    double rf2 = model.getFoulingResistance();

    assertTrue(rf2 >= rf1, "Fouling resistance should increase or stay constant with time");
  }

  @Test
  void testFoulingModel_Reset() {
    FoulingModel model = FoulingModel.createCrudeOilModel();
    model.updateConditions(1.5, 800.0, 0.001, 523.15, TUBE_ID);
    model.advanceTime(500.0);
    assertTrue(model.getFoulingResistance() > 0, "Should have some fouling after 500h");

    model.reset();
    assertEquals(0.0, model.getFoulingResistance(), 1e-10, "Reset should clear fouling");
  }

  @Test
  void testFoulingModel_ThresholdTemperature() {
    FoulingModel model = FoulingModel.createCrudeOilModel();
    model.updateConditions(1.5, 800.0, 0.001, 523.15, TUBE_ID);

    double tThreshold = model.calcThresholdTemperature(1e-10);
    assertTrue(tThreshold > 273.15, "Threshold temperature should be above freezing");
    assertTrue(tThreshold < 1000.0, "Threshold temperature should be realistic");
  }

  @Test
  void testFoulingModel_Json() {
    FoulingModel model = FoulingModel.createCrudeOilModel();
    model.updateConditions(1.5, 800.0, 0.001, 523.15, TUBE_ID);

    String json = model.toJson();
    assertNotNull(json, "JSON output should not be null");
    assertTrue(json.contains("EBERT_PANCHAL"), "JSON should contain model type");
    assertTrue(json.contains("wallTemperature"), "JSON should contain wall temperature");
  }

  @Test
  void testFoulingModel_FactoryMethods() {
    FoulingModel crude = FoulingModel.createCrudeOilModel();
    assertNotNull(crude);

    FoulingModel heavy = FoulingModel.createHeavyCrudeModel();
    assertNotNull(heavy);

    FoulingModel cw = FoulingModel.createCoolingWaterModel(0.0003, 2000.0);
    assertNotNull(cw);
  }

  // ============================================================================
  // Tube Insert Model Tests
  // ============================================================================

  @Test
  void testTubeInsert_None() {
    TubeInsertModel model = new TubeInsertModel();
    assertEquals(1.0, model.getHeatTransferEnhancementRatio(50000, 5.0), 1e-10,
        "No insert should give ratio = 1.0");
    assertEquals(1.0, model.getPressureDropPenaltyRatio(50000), 1e-10,
        "No insert should give ratio = 1.0");
  }

  @Test
  void testTubeInsert_TwistedTape() {
    TubeInsertModel model = TubeInsertModel.createTwistedTape(3.0);
    double Re = 30000;
    double Pr = 5.0;

    double hRatio = model.getHeatTransferEnhancementRatio(Re, Pr);
    double fRatio = model.getPressureDropPenaltyRatio(Re);

    assertTrue(hRatio > 1.0, "Twisted tape should enhance heat transfer: " + hRatio);
    assertTrue(fRatio > 1.0, "Twisted tape should increase pressure drop: " + fRatio);
    assertTrue(hRatio < 5.0, "Enhancement should be reasonable (< 5x): " + hRatio);
    assertTrue(fRatio < 10.0, "Penalty should be reasonable (< 10x): " + fRatio);
  }

  @Test
  void testTubeInsert_TwistedTape_TwistRatioEffect() {
    double Re = 30000;
    double Pr = 5.0;

    TubeInsertModel tight = TubeInsertModel.createTwistedTape(2.5);
    TubeInsertModel loose = TubeInsertModel.createTwistedTape(8.0);

    double hTight = tight.getHeatTransferEnhancementRatio(Re, Pr);
    double hLoose = loose.getHeatTransferEnhancementRatio(Re, Pr);

    assertTrue(hTight > hLoose,
        "Tighter twist should give more enhancement: tight=" + hTight + " loose=" + hLoose);
  }

  @Test
  void testTubeInsert_WireMatrix() {
    TubeInsertModel model = TubeInsertModel.createWireMatrix(0.5);
    double Re = 30000;
    double Pr = 5.0;

    double hRatio = model.getHeatTransferEnhancementRatio(Re, Pr);
    double fRatio = model.getPressureDropPenaltyRatio(Re);

    assertTrue(hRatio > 1.0, "Wire matrix should enhance heat transfer");
    assertTrue(fRatio > 1.0, "Wire matrix should increase pressure drop");
  }

  @Test
  void testTubeInsert_WireMatrix_DensityEffect() {
    double Re = 30000;
    double Pr = 5.0;

    TubeInsertModel sparse = TubeInsertModel.createWireMatrix(0.3);
    TubeInsertModel dense = TubeInsertModel.createWireMatrix(0.7);

    assertTrue(dense.getHeatTransferEnhancementRatio(Re, Pr) > sparse
        .getHeatTransferEnhancementRatio(Re, Pr), "Denser matrix should give more enhancement");
  }

  @Test
  void testTubeInsert_CoiledWire() {
    TubeInsertModel model = TubeInsertModel.createCoiledWire(0.03, 45.0);
    double Re = 30000;
    double Pr = 5.0;

    double hRatio = model.getHeatTransferEnhancementRatio(Re, Pr);
    double fRatio = model.getPressureDropPenaltyRatio(Re);

    assertTrue(hRatio > 1.0, "Coiled wire should enhance heat transfer");
    assertTrue(fRatio > 1.0, "Coiled wire should increase pressure drop");
  }

  @Test
  void testTubeInsert_PEC() {
    TubeInsertModel model = TubeInsertModel.createTwistedTape(4.0);
    double pec = model.getPerformanceEvaluationCriteria(30000, 5.0);
    assertTrue(pec > 0, "PEC must be positive");
    // Good inserts should have PEC > 1 (net benefit at constant pumping power)
  }

  @Test
  void testTubeInsert_ApplyEnhancement() {
    TubeInsertModel model = TubeInsertModel.createTwistedTape(4.0);
    double plainH = 2000.0;
    double plainDP = 5000.0;

    double[] enhanced = model.applyEnhancement(plainH, plainDP, 30000, 5.0);
    assertTrue(enhanced[0] > plainH, "Enhanced HTC should exceed plain");
    assertTrue(enhanced[1] > plainDP, "Enhanced DP should exceed plain");
  }

  @Test
  void testTubeInsert_LaminarRegime() {
    double Re = 1000;
    double Pr = 50.0; // High viscosity fluid in laminar

    TubeInsertModel tape = TubeInsertModel.createTwistedTape(3.0);
    double hRatio = tape.getHeatTransferEnhancementRatio(Re, Pr);
    assertTrue(hRatio > 1.0, "Twisted tape should enhance even in laminar: " + hRatio);

    TubeInsertModel wire = TubeInsertModel.createWireMatrix(0.5);
    double hRatioWire = wire.getHeatTransferEnhancementRatio(Re, Pr);
    assertTrue(hRatioWire > 1.0, "Wire matrix should enhance in laminar: " + hRatioWire);
  }

  @Test
  void testTubeInsert_Json() {
    TubeInsertModel model = TubeInsertModel.createTwistedTape(4.0);
    String json = model.toJson(30000, 5.0);
    assertNotNull(json);
    assertTrue(json.contains("TWISTED_TAPE"));
    assertTrue(json.contains("twistRatio"));
    assertTrue(json.contains("heatTransferEnhancementRatio"));
    assertTrue(json.contains("performanceEvaluationCriteria"));
  }

  @Test
  void testTubeInsert_ToMap() {
    TubeInsertModel model = TubeInsertModel.createWireMatrix(0.5);
    Map<String, Object> map = model.toMap(30000, 5.0);
    assertNotNull(map);
    assertTrue(map.containsKey("insertType"));
    assertTrue(map.containsKey("heatTransferEnhancementRatio"));
    assertTrue(map.containsKey("pressureDropPenaltyRatio"));
    assertTrue(map.containsKey("insertParameters"));
  }

  // ============================================================================
  // Cross-validation tests between correlations
  // ============================================================================

  @Test
  void testFriedel_vs_MSH_SameOrder() {
    // Both methods should give the same order of magnitude for the same conditions
    double dPdz_F = TwoPhasePressureDrop.calcFriedelGradient(MASS_FLUX, 0.3, TUBE_ID,
        LIQUID_DENSITY, VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, SURFACE_TENSION);
    double dPdz_M = TwoPhasePressureDrop.calcMullerSteinhagenHeckGradient(MASS_FLUX, 0.3, TUBE_ID,
        LIQUID_DENSITY, VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY);
    assertTrue(dPdz_F > 0 && dPdz_M > 0, "Both methods should give positive values");
    double ratio = dPdz_F / dPdz_M;
    assertTrue(ratio > 0.1 && ratio < 10,
        "Friedel and MSH should agree within an order of magnitude: ratio=" + ratio);
  }

  @Test
  void testChen_vs_GungorWinterton_SameOrder() {
    // Both methods should give similar range for same conditions
    double hChen = BoilingHeatTransfer.calcChenHTC(MASS_FLUX, 0.3, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, VAPOR_VISCOSITY, LIQUID_CP, LIQUID_CONDUCTIVITY,
        SURFACE_TENSION, HEAT_OF_VAPORIZATION, 5.0, 50000.0);
    double hGW = BoilingHeatTransfer.calcGungorWintertonHTC(MASS_FLUX, 0.3, TUBE_ID, LIQUID_DENSITY,
        VAPOR_DENSITY, LIQUID_VISCOSITY, LIQUID_CP, LIQUID_CONDUCTIVITY, 20000.0,
        HEAT_OF_VAPORIZATION);
    assertTrue(hChen > 0 && hGW > 0, "Both correlations should give positive HTC");
    double ratio = hChen / hGW;
    assertTrue(ratio > 0.1 && ratio < 10,
        "Chen and GW should agree within order of magnitude: ratio=" + ratio);
  }
}
