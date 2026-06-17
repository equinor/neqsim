package neqsim.process.mechanicaldesign.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for heat exchanger thermal-hydraulic design classes.
 *
 * <p>
 * Covers LMTDcorrectionFactor, BellDelawareMethod, VibrationAnalysis, ThermalDesignCalculator,
 * ShellAndTubeDesignCalculator thermal integration, and HeatExchanger rating mode.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ThermalDesignCalculatorTest {

  // ============================================================================
  // LMTD Correction Factor Tests
  // ============================================================================

  @Test
  void testFtPureCounterflow() {
    // Pure counterflow: if cold exit = hot exit, R=1, P close to limit
    // A case with small duty should give Ft close to 1.0
    double ft = LMTDcorrectionFactor.calcFt(100.0, 90.0, 20.0, 30.0, 1);
    assertTrue(ft > 0.9, "Ft should be close to 1.0 for modest duty: " + ft);
    assertTrue(ft <= 1.0, "Ft should not exceed 1.0: " + ft);
  }

  @Test
  void testFtOneShellPass() {
    // Hot: 150 -> 90, Cold: 30 -> 80
    // R = (150-90)/(80-30) = 1.2, P = (80-30)/(150-30) = 0.417
    double ft = LMTDcorrectionFactor.calcFt1ShellPass(150.0, 90.0, 30.0, 80.0);
    assertTrue(ft > 0.75, "Ft should be above 0.75 for typical case: " + ft);
    assertTrue(ft < 1.0, "Ft should be below 1.0 for multi-pass: " + ft);
  }

  @Test
  void testFtTwoShellPasses() {
    // Same temperatures, 2 shell passes should give higher Ft than 1 shell pass
    double ft1 = LMTDcorrectionFactor.calcFt(150.0, 90.0, 30.0, 80.0, 1);
    double ft2 = LMTDcorrectionFactor.calcFt(150.0, 90.0, 30.0, 80.0, 2);
    assertTrue(ft2 >= ft1,
        "2 shell passes should give >= Ft than 1 pass: ft1=" + ft1 + " ft2=" + ft2);
  }

  @Test
  void testFtREqualsOne() {
    // R = 1 special case: Hot: 100 -> 60, Cold: 20 -> 60
    // R = (100-60)/(60-20) = 1.0, P = (60-20)/(100-20) = 0.5
    double ft = LMTDcorrectionFactor.calcFt1ShellPass(100.0, 60.0, 20.0, 60.0);
    assertTrue(ft > 0.5, "Ft should be reasonable for R=1: " + ft);
    assertTrue(ft <= 1.0, "Ft should not exceed 1.0: " + ft);
  }

  @Test
  void testRequiredShellPasses() {
    // Case with low Ft for 1 shell pass should require more shell passes
    // Hot: 200 -> 50, Cold: 30 -> 170 (high P, temperature cross)
    // P = (170-30)/(200-30) = 0.824 -> very high P, needs multiple shells
    int passes = LMTDcorrectionFactor.requiredShellPasses(200.0, 50.0, 30.0, 170.0);
    assertTrue(passes >= 1, "Should need at least 1 shell pass: " + passes);
    assertTrue(passes <= 6 || passes == -1, "Should return valid passes or -1");
  }

  @Test
  void testCalcRandP() {
    double R = LMTDcorrectionFactor.calcR(150.0, 90.0, 30.0, 80.0);
    double P = LMTDcorrectionFactor.calcP(150.0, 90.0, 30.0, 80.0);
    assertEquals(1.2, R, 1e-6, "R = (150-90)/(80-30) = 1.2");
    assertEquals(50.0 / 120.0, P, 1e-6, "P = (80-30)/(150-30) = 50/120");
  }

  @Test
  void testFtNoHeatTransfer() {
    // P = 0 (no cold-side temperature rise) -> Ft = 1.0
    double ft = LMTDcorrectionFactor.calcFt(100.0, 100.0, 30.0, 30.0, 1);
    assertEquals(1.0, ft, 1e-6, "Ft should be 1.0 when no heat transfer");
  }

  // ============================================================================
  // Bell-Delaware Method Tests
  // ============================================================================

  @Test
  void testShellEquivDiameterTriangular() {
    double tubeOD = 0.01905; // 3/4 inch
    double tubePitch = 0.02381; // 15/16 inch (1.25 * OD)
    double de = BellDelawareMethod.calcShellEquivDiameter(tubeOD, tubePitch, true);
    assertTrue(de > 0, "Equivalent diameter should be positive: " + de);
    assertTrue(de > tubeOD * 0.3 && de < tubeOD * 3.0,
        "Equivalent diameter should be reasonable: " + de);
  }

  @Test
  void testShellEquivDiameterSquare() {
    double tubeOD = 0.01905;
    double tubePitch = 0.02381;
    double deTri = BellDelawareMethod.calcShellEquivDiameter(tubeOD, tubePitch, true);
    double deSq = BellDelawareMethod.calcShellEquivDiameter(tubeOD, tubePitch, false);
    assertTrue(deSq > 0, "Square layout equiv diameter should be positive");
    // Square and triangular layouts should give different but comparable results
    assertTrue(Math.abs(deTri - deSq) / deTri < 1.0,
        "Triangular and square equiv diameters should be in same order of magnitude");
  }

  @Test
  void testCrossflowArea() {
    double shellID = 0.5; // 500 mm
    double baffleSpacing = 0.2; // 200 mm
    double tubeOD = 0.01905;
    double tubePitch = 0.02381;
    double area = BellDelawareMethod.calcCrossflowArea(shellID, baffleSpacing, tubeOD, tubePitch);
    assertTrue(area > 0, "Crossflow area should be positive: " + area);
    assertTrue(area < shellID * baffleSpacing, "Crossflow area should be less than shell*baffle");
  }

  @Test
  void testKernShellSideHTC() {
    // Typical shell-side conditions for a gas cooler
    double massFlux = 50.0; // kg/(m2*s) - moderate
    double de = 0.014; // m
    double mu = 1.5e-5; // Pa*s (gas)
    double cp = 2200.0; // J/(kg*K)
    double k = 0.03; // W/(m*K) (gas)
    double muW = 1.5e-5; // same (no wall correction)

    double h = BellDelawareMethod.calcKernShellSideHTC(massFlux, de, mu, cp, k, muW);
    assertTrue(h > 0, "Kern HTC should be positive: " + h);
    assertTrue(h > 10 && h < 50000, "Kern HTC should be in realistic range: " + h);
  }

  @Test
  void testKernShellSidePressureDrop() {
    double massFlux = 50.0;
    double de = 0.014;
    double shellDiameter = 0.5;
    int baffleCount = 10;
    double rho = 50.0; // kg/m3
    double mu = 1.5e-5;

    double dp = BellDelawareMethod.calcKernShellSidePressureDrop(massFlux, de, shellDiameter,
        baffleCount, rho, mu, mu);
    assertTrue(dp > 0, "Kern shell DP should be positive: " + dp);
    assertTrue(dp < 1e6, "Kern shell DP should be less than 1 MPa: " + dp);
  }

  @Test
  void testBellDelawareJFactors() {
    // J_c: segmental baffle correction
    double Jc = BellDelawareMethod.calcJc(0.25); // 25% baffle cut
    assertTrue(Jc > 0.5 && Jc <= 1.2, "Jc should be in range 0.5-1.2: " + Jc);

    // J_l: baffle leakage correction
    double Jl = BellDelawareMethod.calcJl(0.0004, 0.003, 0.02, 100, 0.01905, 0.2);
    assertTrue(Jl > 0.0 && Jl <= 1.0, "Jl should be in range 0-1.0: " + Jl);

    // J_b: bundle bypass correction
    double Jb = BellDelawareMethod.calcJb(0.005, 0.02, true, 2, 15);
    assertTrue(Jb > 0.5 && Jb <= 1.0, "Jb should be in range 0.5-1.0: " + Jb);

    // J_s: unequal baffle spacing
    double Js = BellDelawareMethod.calcJs(0.2, 0.25, 0.25, 10);
    assertTrue(Js > 0.5 && Js <= 1.2, "Js should be in range 0.5-1.2: " + Js);

    // J_r: adverse temperature gradient (laminar)
    double JrTurb = BellDelawareMethod.calcJr(20000.0, 15); // turbulent -> 1.0
    assertEquals(1.0, JrTurb, 1e-6, "Jr should be 1.0 for turbulent flow");

    double JrLam = BellDelawareMethod.calcJr(50.0, 15); // laminar
    assertTrue(JrLam < 1.0, "Jr should be < 1.0 for laminar flow: " + JrLam);
  }

  @Test
  void testCorrectedHTC() {
    double hIdeal = 500.0; // W/(m2*K)
    double corrected = BellDelawareMethod.calcCorrectedHTC(hIdeal, 1.0, 0.8, 0.9, 0.95, 1.0);
    double expected = 500.0 * 1.0 * 0.8 * 0.9 * 0.95 * 1.0;
    assertEquals(expected, corrected, 1e-6, "Corrected HTC should be product of factors * ideal");
  }

  @Test
  void testIdealCrossflowHTC() {
    double massFlux = 50.0;
    double tubeOD = 0.01905;
    double mu = 1.5e-5;
    double cp = 2200.0;
    double k = 0.03;
    double tubePitch = 0.02381;

    double h = BellDelawareMethod.calcIdealCrossflowHTC(massFlux, tubeOD, mu, cp, k, mu, 1.0);
    assertTrue(h > 0, "Ideal crossflow HTC should be positive: " + h);
  }

  // ============================================================================
  // Vibration Analysis Tests
  // ============================================================================

  @Test
  void testNaturalFrequency() {
    // Standard 3/4" tube, 0.5m unsupported span, carbon steel (E=200 GPa, rho=7800)
    double fn = VibrationAnalysis.calcNaturalFrequency(0.01905, // OD
        0.01483, // ID
        0.5, // span
        200e9, // E
        7800.0, // tube density
        800.0, // tube fluid density
        50.0, // shell fluid density
        "pinned");
    assertTrue(fn > 0, "Natural frequency should be positive: " + fn);
    assertTrue(fn > 10 && fn < 5000,
        "Natural frequency should be in realistic range (10-5000 Hz): " + fn);
  }

  @Test
  void testNaturalFrequencyEndConditions() {
    double fnPinned = VibrationAnalysis.calcNaturalFrequency(0.01905, 0.01483, 0.5, 200e9, 7800.0,
        800.0, 50.0, "pinned");
    double fnFixed = VibrationAnalysis.calcNaturalFrequency(0.01905, 0.01483, 0.5, 200e9, 7800.0,
        800.0, 50.0, "fixed");
    double fnClamped = VibrationAnalysis.calcNaturalFrequency(0.01905, 0.01483, 0.5, 200e9, 7800.0,
        800.0, 50.0, "clamped-pinned");

    assertTrue(fnFixed > fnClamped, "Fixed > Clamped-pinned frequency");
    assertTrue(fnClamped > fnPinned, "Clamped-pinned > Pinned frequency");
  }

  @Test
  void testVortexSheddingFrequency() {
    double fvs = VibrationAnalysis.calcVortexSheddingFrequency(2.0, 0.01905, 0.02381);
    assertTrue(fvs > 0, "Vortex shedding frequency should be positive: " + fvs);
    // St ~ 0.22, f = 0.22 * 2.0 / 0.01905 ~ 23 Hz
    assertTrue(fvs > 15 && fvs < 40, "Vortex frequency should be ~23 Hz: " + fvs);
  }

  @Test
  void testCriticalVelocityConnors() {
    double fn = 100.0; // Hz
    double tubeOD = 0.01905;
    double dampingRatio = 0.03;
    double mEff = 1.5; // kg/m
    double rhoShell = 50.0;

    double vCrit = VibrationAnalysis.calcCriticalVelocityConnors(fn, tubeOD, dampingRatio, mEff,
        rhoShell, true);
    assertTrue(vCrit > 0, "Critical velocity should be positive: " + vCrit);
    assertTrue(vCrit < 100, "Critical velocity should be reasonable: " + vCrit);
  }

  @Test
  void testAcousticFrequency() {
    // Shell ID = 0.5m, sonic velocity = 340 m/s (gas)
    double fac = VibrationAnalysis.calcAcousticFrequency(0.5, 340.0, 1);
    // f = 1 * 340 / (2 * 0.5) = 340 Hz
    assertEquals(340.0, fac, 1e-6, "Acoustic frequency should be 340 Hz");
  }

  @Test
  void testPerformScreeningPass() {
    // Conditions that should pass: low velocity, distant from resonance
    VibrationAnalysis.VibrationResult result = VibrationAnalysis.performScreening(0.01905, // tubeOD
        0.01483, // tubeID
        0.4, // unsupported span (short = high fn)
        0.02381, // tube pitch
        200e9, // E (steel)
        7800.0, // tube density
        0.5, // crossflow velocity (low)
        50.0, // shell fluid density
        800.0, // tube fluid density
        0.5, // shell ID
        340.0, // sonic velocity
        0.03, // damping ratio
        true); // triangular

    assertTrue(result.naturalFrequencyHz > 0, "Natural frequency should be computed");
    assertFalse(result.fluidElasticCritical,
        "Should not be fluid-elastic critical at low velocity");
    assertTrue(result.passed, "Should pass overall at benign conditions");
    assertNotNull(result.getSummary(), "Summary should not be null");
  }

  // ============================================================================
  // ThermalDesignCalculator Tests
  // ============================================================================

  @Test
  void testThermalDesignCalculatorKern() {
    ThermalDesignCalculator calc = createSimpleThermalCalculator();
    calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.KERN);
    calc.calculate();

    assertTrue(calc.getTubeSideHTC() > 0,
        "Tube-side HTC should be positive: " + calc.getTubeSideHTC());
    assertTrue(calc.getShellSideHTC() > 0,
        "Shell-side HTC should be positive: " + calc.getShellSideHTC());
    assertTrue(calc.getOverallU() > 0, "Overall U should be positive: " + calc.getOverallU());
    assertTrue(calc.getOverallU() < calc.getTubeSideHTC(),
        "Overall U should be less than tube-side HTC (resistance in series)");
    assertTrue(calc.getOverallU() < calc.getShellSideHTC(),
        "Overall U should be less than shell-side HTC (resistance in series)");
  }

  @Test
  void testThermalDesignCalculatorBellDelaware() {
    ThermalDesignCalculator calc = createSimpleThermalCalculator();
    calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
    calc.calculate();

    assertTrue(calc.getTubeSideHTC() > 0, "Tube-side HTC should be positive");
    assertTrue(calc.getShellSideHTC() > 0, "Shell-side HTC should be positive");
    assertTrue(calc.getOverallU() > 0, "Overall U should be positive");
  }

  @Test
  void testPressureDrops() {
    ThermalDesignCalculator calc = createSimpleThermalCalculator();
    calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.KERN);
    calc.calculate();

    double dpTube = calc.getTubeSidePressureDrop();
    double dpShell = calc.getShellSidePressureDrop();
    assertTrue(dpTube > 0, "Tube-side DP should be positive: " + dpTube);
    assertTrue(dpShell > 0, "Shell-side DP should be positive: " + dpShell);

    double dpTubeBar = calc.getTubeSidePressureDropBar();
    double dpShellBar = calc.getShellSidePressureDropBar();
    assertEquals(dpTube / 1e5, dpTubeBar, 1e-6, "Tube DP bar conversion");
    assertEquals(dpShell / 1e5, dpShellBar, 1e-6, "Shell DP bar conversion");
  }

  @Test
  void testVelocities() {
    ThermalDesignCalculator calc = createSimpleThermalCalculator();
    calc.calculate();

    double vTube = calc.getTubeSideVelocity();
    double vShell = calc.getShellSideVelocity();
    assertTrue(vTube > 0, "Tube-side velocity should be positive: " + vTube);
    assertTrue(vShell > 0, "Shell-side velocity should be positive: " + vShell);
  }

  @Test
  void testReynoldsNumbers() {
    ThermalDesignCalculator calc = createSimpleThermalCalculator();
    calc.calculate();

    double reTube = calc.getTubeSideRe();
    double reShell = calc.getShellSideRe();
    assertTrue(reTube > 0, "Tube-side Re should be positive: " + reTube);
    assertTrue(reShell > 0, "Shell-side Re should be positive: " + reShell);
  }

  @Test
  void testToMap() {
    ThermalDesignCalculator calc = createSimpleThermalCalculator();
    calc.calculate();

    Map<String, Object> map = calc.toMap();
    assertNotNull(map, "toMap should not return null");
    assertFalse(map.isEmpty(), "toMap should have entries");
    assertTrue(map.containsKey("tubeSide"), "Should contain tubeSide section");
    assertTrue(map.containsKey("shellSide"), "Should contain shellSide section");
    assertTrue(map.containsKey("overallHeatTransfer"), "Should contain overallHeatTransfer");
    assertTrue(map.containsKey("shellSideMethod"), "Should contain method name");
  }

  @Test
  void testFoulingReducesU() {
    ThermalDesignCalculator calcClean = createSimpleThermalCalculator();
    calcClean.setFoulingTube(0.0);
    calcClean.setFoulingShell(0.0);
    calcClean.calculate();
    double uClean = calcClean.getOverallU();

    ThermalDesignCalculator calcFouled = createSimpleThermalCalculator();
    calcFouled.setFoulingTube(0.0003);
    calcFouled.setFoulingShell(0.0003);
    calcFouled.calculate();
    double uFouled = calcFouled.getOverallU();

    assertTrue(uClean > uFouled,
        "Fouled U should be lower than clean U: clean=" + uClean + " fouled=" + uFouled);
  }

  // ============================================================================
  // Integration: ShellAndTubeDesignCalculator with thermal data via MechanicalDesign
  // ============================================================================

  @Test
  void testShellAndTubeWithThermalViaDesign() {
    // Run an HX, then do full mechanical design which triggers thermal calc
    HeatExchanger hx = createSizingHeatExchanger();
    hx.initMechanicalDesign();
    HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
    design.calcDesign();

    // The mechanical design should have run the shell-and-tube calculator
    assertNotNull(design.getSelectedSizingResult(), "Should have sizing result");
    assertTrue(design.getWeightTotal() > 0.0, "Should have positive weight");
  }

  // ============================================================================
  // Integration: HeatExchanger Rating Mode
  // ============================================================================

  @Test
  void testHeatExchangerRatingMode() {
    // Create and run a heat exchanger in rating mode
    SystemInterface hotSys = new SystemSrkEos(273.15 + 100.0, 20.0);
    hotSys.addComponent("methane", 120.0);
    hotSys.addComponent("ethane", 120.0);
    hotSys.addComponent("n-heptane", 3.0);
    hotSys.createDatabase(true);
    hotSys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(hotSys);
    ops.TPflash();

    Stream hot = new Stream("hot", hotSys);
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(1000.0, "kg/hr");

    Stream cold = new Stream("cold", hotSys.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(310.0, "kg/hr");

    HeatExchanger hx = new HeatExchanger("ratingHX", hot, cold);

    // Configure rating mode with calculator
    ThermalDesignCalculator ratingCalc = new ThermalDesignCalculator();
    ratingCalc.setTubeCount(100);
    ratingCalc.setTubePasses(2);
    ratingCalc.setTubeODm(0.01905);
    ratingCalc.setTubeIDm(0.01483);
    ratingCalc.setTubeLengthm(6.096);
    ratingCalc.setTubePitchm(0.02381);
    ratingCalc.setTriangularPitch(true);
    ratingCalc.setShellIDm(0.5);
    ratingCalc.setBaffleSpacingm(0.2);
    ratingCalc.setBaffleCount(10);

    hx.setRatingCalculator(ratingCalc);
    hx.setRatingArea(50.0); // m2

    assertEquals(HeatExchanger.DesignMode.RATING, hx.getDesignMode(),
        "Setting rating calculator should switch to RATING mode");

    ProcessSystem ps = new ProcessSystem();
    ps.add(hot);
    ps.add(cold);
    ps.add(hx);
    ps.run();

    // After running, rating U should be computed
    double u = hx.getRatingU();
    assertTrue(u >= 0, "Rating U should be non-negative after run: " + u);
  }

  @Test
  void testHeatExchangerDefaultSizingMode() {
    HeatExchanger hx = createSizingHeatExchanger();
    assertEquals(HeatExchanger.DesignMode.SIZING, hx.getDesignMode(),
        "Default mode should be SIZING");
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  /**
   * Creates a ThermalDesignCalculator with typical geometry and fluid properties for a gas cooler.
   *
   * @return configured ThermalDesignCalculator
   */
  private ThermalDesignCalculator createSimpleThermalCalculator() {
    ThermalDesignCalculator calc = new ThermalDesignCalculator();

    // Geometry: standard 3/4" tubes, 500mm shell
    calc.setTubeODm(0.01905);
    calc.setTubeIDm(0.01483);
    calc.setTubeLengthm(6.096);
    calc.setTubeCount(100);
    calc.setTubePasses(2);
    calc.setTubePitchm(0.02381);
    calc.setTriangularPitch(true);
    calc.setShellIDm(0.5);
    calc.setBaffleSpacingm(0.2);
    calc.setBaffleCount(10);
    calc.setBaffleCut(0.25);
    calc.setTubeWallConductivity(52.0);

    // Tube side: water-like liquid
    calc.setTubeSideFluid(998.0, 0.001, 4180.0, 0.60, 5.0, true);

    // Shell side: light hydrocarbon gas
    calc.setShellSideFluid(50.0, 1.5e-5, 2200.0, 0.03, 3.0);

    // Fouling
    calc.setFoulingTube(0.00018);
    calc.setFoulingShell(0.00035);

    return calc;
  }

  /**
   * Creates a HeatExchanger in default sizing mode.
   *
   * @return configured HeatExchanger
   */
  private HeatExchanger createSizingHeatExchanger() {
    SystemInterface sys = new SystemSrkEos(273.15 + 60.0, 20.0);
    sys.addComponent("methane", 120.0);
    sys.addComponent("ethane", 120.0);
    sys.createDatabase(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();

    Stream hot = new Stream("hot", sys);
    hot.setTemperature(80.0, "C");
    hot.setFlowRate(500.0, "kg/hr");

    Stream cold = new Stream("cold", sys.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(300.0, "kg/hr");

    HeatExchanger hx = new HeatExchanger("testHX", hot, cold);
    hx.setUAvalue(800.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(hot);
    ps.add(cold);
    ps.add(hx);
    ps.run();

    return hx;
  }
}
