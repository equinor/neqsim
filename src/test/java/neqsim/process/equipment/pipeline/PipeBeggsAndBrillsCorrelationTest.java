package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests locking the Beggs and Brill (1973) correlation in {@link PipeBeggsAndBrills} against values
 * recomputed independently from the published equations.
 *
 * <p>
 * These tests cover three defects that were present in earlier revisions:
 * </p>
 *
 * <table>
 * <caption>Defects covered by these tests</caption>
 * <tr>
 * <th>Defect</th>
 * <th>Symptom</th>
 * </tr>
 * <tr>
 * <td>Pipe angle converted from degrees to radians twice before the inclination correction</td>
 * <td>The correction was suppressed by a factor of about 57, so liquid holdup barely responded to pipe inclination</td>
 * </tr>
 * <tr>
 * <td>Distributed-regime boundary tested L4 instead of L1 for no-slip liquid fractions below 0.4</td>
 * <td>Distributed flow was unreachable at low liquid fraction and was reported as intermittent</td>
 * </tr>
 * <tr>
 * <td>Liquid velocity number divided by an extra 32.2</td>
 * <td>Gravity was counted twice because the 1.938 prefactor already absorbs it</td>
 * </tr>
 * <tr>
 * <td>Baker-Swerdloff surface tension left unbounded</td>
 * <td>The pressure correction crosses zero at 3971 psi, so above 274 bara the liquid velocity number became NaN and the
 * inclination correction was silently dropped</td>
 * </tr>
 * <tr>
 * <td>Transition regime had no inclination correction</td>
 * <td>Only the segregated, intermittent and distributed branches set the inclination coefficient, so a leg falling in
 * the transition band reported the horizontal holdup</td>
 * </tr>
 * </table>
 *
 * @author NeqSim
 * @version 1.0
 */
class PipeBeggsAndBrillsCorrelationTest {
  /** Absolute tolerance on liquid holdup when comparing against the reference values. */
  private static final double HOLDUP_TOLERANCE = 1.0e-3;

  /**
   * Builds a two-phase methane/n-decane stream at the requested state.
   *
   * @param pressureBara pressure in bara, must be positive
   * @param temperatureC temperature in degrees Celsius
   * @param moleFractionC10 n-decane mole fraction, between 0 and 1
   * @param massFlowKgPerHour total mass flow in kg/hr, must be positive
   * @return a stream that has been flashed and had its properties initialised
   */
  private Stream buildStream(double pressureBara, double temperatureC, double moleFractionC10,
      double massFlowKgPerHour) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    fluid.addComponent("methane", 1.0 - moleFractionC10);
    fluid.addComponent("nC10", moleFractionC10);
    fluid.setMixingRule(2);
    fluid.setPressure(pressureBara, "bara");
    fluid.setTemperature(temperatureC, "C");
    fluid.setTotalFlowRate(massFlowKgPerHour, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();

    Stream stream = new Stream("feed", fluid);
    stream.setFlowRate(massFlowKgPerHour, "kg/hr");
    stream.run();
    return stream;
  }

  /**
   * Runs a single-increment pipe so the result is one evaluation of the correlation.
   *
   * @param stream the inlet stream, must not be null
   * @param diameter inside diameter in m, must be positive
   * @param angleDegrees pipe inclination in degrees, positive upwards
   * @return the pipe after it has been run
   */
  private PipeBeggsAndBrills runSingleSegment(Stream stream, double diameter, double angleDegrees) {
    // A short segment keeps the pressure change across the increment negligible, so
    // the correlation is evaluated essentially at the stated inlet condition.
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("segment", stream);
    pipe.setLength(1.0);
    pipe.setAngle(angleDegrees);
    pipe.setElevation(Math.sin(Math.toRadians(angleDegrees)));
    pipe.setDiameter(diameter);
    pipe.setPipeWallRoughness(1.0e-5);
    pipe.setNumberOfIncrements(1);
    pipe.setRunIsothermal(true);
    pipe.run();
    return pipe;
  }

  /**
   * Runs the lean segregated reference case at the requested inclination.
   *
   * <p>
   * Methane/n-decane with 0.5 mol% n-decane at 100 bara and 40 C, 50000 kg/hr through a 0.25 m line. The state gives a
   * no-slip liquid fraction of 0.00395, a mixture Froude number of 6.281 and a horizontal segregated holdup of 0.05721.
   * </p>
   *
   * @param angleDegrees pipe inclination in degrees, positive upwards
   * @return the liquid holdup reported for the single segment
   */
  private double leanCaseHoldup(double angleDegrees) {
    return runSingleSegment(buildStream(100.0, 40.0, 0.005, 50000.0), 0.25, angleDegrees).getSegmentLiquidHoldup(1);
  }

  @Test
  @DisplayName("Inclination correction matches the published correlation uphill and downhill")
  void testInclinationCorrectionMatchesReference() {
    // Reference psi and holdup recomputed from Beggs and Brill (1973) for this
    // state: N_LV = 0.1583, horizontal holdup 0.05721.
    // -5 deg: psi = 0.62415, H_L = 0.03571
    // 0 deg: psi = 1, H_L = 0.05721
    // +5 deg: psi = 2.05847, H_L = 0.11777
    // +30 deg: psi = 5.31494, H_L = 0.30407
    Assertions.assertEquals(0.03571, leanCaseHoldup(-5.0), HOLDUP_TOLERANCE);
    Assertions.assertEquals(0.05721, leanCaseHoldup(0.0), HOLDUP_TOLERANCE);
    Assertions.assertEquals(0.11777, leanCaseHoldup(5.0), HOLDUP_TOLERANCE);
    Assertions.assertEquals(0.30407, leanCaseHoldup(30.0), HOLDUP_TOLERANCE);
  }

  @Test
  @DisplayName("Liquid holdup increases uphill and decreases downhill")
  void testHoldupOrderingWithInclination() {
    // When the pipe angle is converted to radians twice the correction is
    // suppressed by a factor of about 57 and these three values collapse together.
    double downhill = leanCaseHoldup(-5.0);
    double horizontal = leanCaseHoldup(0.0);
    double uphill = leanCaseHoldup(5.0);

    Assertions.assertTrue(uphill > horizontal * 1.5,
        "uphill holdup " + uphill + " should clearly exceed horizontal holdup " + horizontal);
    Assertions.assertTrue(downhill < horizontal * 0.8,
        "downhill holdup " + downhill + " should be clearly below horizontal holdup " + horizontal);
  }

  @Test
  @DisplayName("Distributed regime is reached below a no-slip liquid fraction of 0.4")
  void testDistributedRegimeBoundaryUsesL1() {
    // Methane/n-decane with 30 mol% n-decane at 60 bara, 200000 kg/hr through a
    // 0.125 m line: lambda_L = 0.20509 and Fr = 542.85, above L1 = 195.84. The
    // published map gives distributed flow; testing L4 instead of L1 made the
    // branch unreachable at this liquid fraction.
    PipeBeggsAndBrills pipe = runSingleSegment(buildStream(60.0, 40.0, 0.3, 200000.0), 0.125, 0.0);
    Assertions.assertEquals(PipeBeggsAndBrills.FlowRegime.DISTRIBUTED, pipe.getSegmentFlowRegime(1));
    Assertions.assertEquals(0.28846, pipe.getSegmentLiquidHoldup(1), HOLDUP_TOLERANCE);
  }

  @Test
  @DisplayName("Horizontal segregated holdup equals the published correlation")
  void testHorizontalSegregatedMatchesCorrelation() {
    PipeBeggsAndBrills pipe = runSingleSegment(buildStream(100.0, 40.0, 0.005, 50000.0), 0.25, 0.0);
    Assertions.assertEquals(PipeBeggsAndBrills.FlowRegime.SEGREGATED, pipe.getSegmentFlowRegime(1));

    // A horizontal pipe applies no inclination correction, so the reported holdup
    // must equal the horizontal correlation evaluated at the same lambda and Fr.
    double lambda = pipe.getSegmentLiquidSuperficialVelocity(1) / pipe.getSegmentMixtureSuperficialVelocity(1);
    double diameterFeet = 0.25 * 3.2808399;
    double froude = Math.pow(pipe.getSegmentMixtureSuperficialVelocity(1) * 3.2808399, 2) / (32.174 * diameterFeet);
    double expected = 0.98 * Math.pow(lambda, 0.4846) / Math.pow(froude, 0.0868);
    Assertions.assertEquals(expected, pipe.getSegmentLiquidHoldup(1), HOLDUP_TOLERANCE);
  }

  /**
   * Runs a high-pressure reference segment: 50000 kg/hr of methane/n-decane at 40 C through a 0.25 m line.
   *
   * @param pressureBara pressure in bara, must be positive
   * @param moleFractionC10 n-decane mole fraction, between 0 and 1
   * @param angleDegrees pipe inclination in degrees, positive upwards
   * @return the liquid holdup reported for the single segment
   */
  private double highPressureHoldup(double pressureBara, double moleFractionC10, double angleDegrees) {
    return runSingleSegment(buildStream(pressureBara, 40.0, moleFractionC10, 50000.0), 0.25, angleDegrees)
        .getSegmentLiquidHoldup(1);
  }

  @Test
  @DisplayName("Inclination correction survives above the Baker-Swerdloff pressure limit")
  void testSurfaceTensionFloorKeepsInclinationCorrection() {
    // The Baker-Swerdloff pressure correction 1 - 0.024 * P^0.45 crosses zero at
    // 3971 psi = 274 bara. With no floor the surface tension turns negative, the
    // Duns and Ros liquid velocity number becomes NaN, every "logArg > 0" test then
    // fails and the inclination coefficient stays zero. The uphill-to-horizontal
    // holdup ratio below used to fall from 2.95 at 274 bara to exactly 1.0 at
    // 276 bara, i.e. an uphill leg reported the horizontal holdup.
    double ratioBelow = highPressureHoldup(274.0, 0.02, 5.0) / highPressureHoldup(274.0, 0.02, 0.0);
    double ratioAbove = highPressureHoldup(300.0, 0.02, 5.0) / highPressureHoldup(300.0, 0.02, 0.0);

    Assertions.assertTrue(ratioAbove > 2.0,
        "inclination correction lost above 274 bara, uphill/horizontal holdup ratio was " + ratioAbove);
    Assertions.assertEquals(ratioBelow, ratioAbove, 0.1,
        "the inclination correction must not step across the surface tension zero crossing");
  }

  @Test
  @DisplayName("Liquid holdup is continuous across the surface tension zero crossing")
  void testHoldupContinuousAcrossSurfaceTensionZeroCrossing() {
    // Two bar apart, on either side of the 274 bara crossing. Before the floor was
    // applied these were 0.524 and 0.175, a factor of three for a 2 bar change.
    double below = highPressureHoldup(274.0, 0.02, 5.0);
    double above = highPressureHoldup(276.0, 0.02, 5.0);
    Assertions.assertEquals(below, above, 0.02,
        "holdup stepped from " + below + " to " + above + " over a 2 bar pressure change");
  }

  @Test
  @DisplayName("Transition regime applies the inclination correction")
  void testTransitionRegimeAppliesInclinationCorrection() {
    // 5 mol% n-decane at 300 bara puts the segment in the transition band, which
    // interpolates between the segregated and intermittent correlations. The
    // correction used to be applied only to the three named regimes, so the
    // transition band reported the horizontal holdup and the correction jumped
    // between 1 and about 1.8 at both transition boundaries.
    PipeBeggsAndBrills uphill = runSingleSegment(buildStream(300.0, 40.0, 0.05, 50000.0), 0.25, 5.0);
    Assertions.assertEquals(PipeBeggsAndBrills.FlowRegime.TRANSITION, uphill.getSegmentFlowRegime(1));

    double ratio = uphill.getSegmentLiquidHoldup(1) / highPressureHoldup(300.0, 0.05, 0.0);
    Assertions.assertEquals(1.784, ratio, 0.05,
        "transition regime inclination correction, uphill/horizontal holdup ratio was " + ratio);
  }
}
