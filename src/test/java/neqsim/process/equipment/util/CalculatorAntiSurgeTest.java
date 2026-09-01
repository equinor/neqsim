package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for {@link Calculator#runAntiSurgeCalc(UUID)}.
 *
 * <p>
 * Verifies the bounded-step + explicit recycle-leg run() behaviour added to fix unbounded anti-surge setpoint
 * overshoot. Pre-fix bugs:
 * </p>
 * <ul>
 * <li>The calculator could request a recycle flow larger than the splitter inlet, pegging the forward leg to zero and
 * crashing the downstream EOS.</li>
 * <li>{@link Splitter#setFlowRates(double[], String)} rebuilds the split-stream array as full-flow clones of the inlet;
 * without an explicit {@code splitStream(1).setFlowRate(...).run()} the recycle leg never reflected the calculator's
 * setpoint.</li>
 * </ul>
 */
public class CalculatorAntiSurgeTest {

  private Compressor buildCompressorWithSurgeCurve(double inletFlowKgHr) {
    SystemInterface testFluid = new SystemSrkEos(298.15, 50.0);
    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 87.974);
    testFluid.addComponent("ethane", 5.258);
    testFluid.addComponent("propane", 3.283);
    testFluid.setMixingRule(2);
    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    testFluid.setTotalFlowRate(inletFlowKgHr, "kg/hr");

    Stream stream = new Stream("inlet", testFluid);
    stream.run();

    Compressor comp = new Compressor("cmp", stream);
    comp.setUsePolytropicCalc(true);
    comp.setSpeed(11918);

    double[] chartConditions = new double[] { 0.3, 1.0, 1.0, 1.0 };
    double[] speed = new double[] { 12913, 12298, 11683, 11098, 10453, 9224, 8609, 8200 };
    double[][] flow = new double[][] {
        { 2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329, 5661.0331 },
        { 2571.1753, 2943.7254, 3440.2675, 3837.4448, 4253.0898, 4668.6643, 4997.1926, 5387.4952 },
        { 2415.3793, 2763.0706, 3141.7095, 3594.7436, 4047.6467, 4494.1889, 4853.7353, 5138.7858 },
        { 2247.2043, 2799.7342, 3178.3428, 3656.1551, 4102.778, 4394.1591, 4648.3224, 4840.4998 },
        { 2072.8397, 2463.9483, 2836.4078, 3202.5266, 3599.6333, 3978.0203, 4257.0022, 4517.345 },
        { 1835.9552, 2208.455, 2618.1322, 2940.8034, 3244.7852, 3530.1279, 3753.3738, 3895.9746 },
        { 1711.3386, 1965.8848, 2356.9431, 2685.9247, 3008.5154, 3337.2855, 3591.5092 },
        { 1636.5807, 2002.8708, 2338.0319, 2642.1245, 2896.4894, 3113.6264, 3274.8764, 3411.2977 } };
    double[][] head = new double[][] { { 80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728 },
        { 72.2122, 71.8369, 68.9009, 65.8341, 60.7167, 54.702, 47.2749, 35.7471 },
        { 65.1576, 64.5253, 62.6118, 59.1619, 54.0455, 47.0059, 39.195, 31.6387 },
        { 58.6154, 56.9627, 54.6647, 50.4462, 44.4322, 38.4144, 32.9084, 28.8109 },
        { 52.3295, 51.0573, 49.5283, 46.3326, 42.3685, 37.2502, 31.4884, 25.598 },
        { 40.6578, 39.6416, 37.6008, 34.6603, 30.9503, 27.1116, 23.2713, 20.4546 },
        { 35.2705, 34.6359, 32.7228, 31.0645, 27.0985, 22.7482, 18.0113 },
        { 32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403 } };
    double[][] polyEff = new double[][] { { 77.2, 79.4, 80.7, 80.5, 79.2, 75.5, 69.6, 58.7 },
        { 77.0, 79.3, 80.9, 80.7, 79.5, 75.6, 69.7, 60.0 }, { 77.0, 79.2, 80.8, 80.7, 78.9, 73.7, 66.3, 57.7 },
        { 77.1, 80.5, 81.1, 79.6, 75.4, 69.5, 64.0, 58.8 }, { 77.0, 79.8, 80.9, 80.6, 78.0, 73.0, 66.6, 59.9 },
        { 77.5, 80.2, 81.0, 79.6, 76.4, 70.8, 64.6, 60.5 }, { 77.8, 80.1, 81.1, 79.9, 76.2, 69.3, 60.9 },
        { 78.1, 80.9, 80.8, 78.9, 75.2, 70.3, 65.6, 61.0 } };
    comp.getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);
    comp.getCompressorChart().setHeadUnit("kJ/kg");

    double[] surgeflow = new double[] { 2789.0, 2550.0, 2500.0, 2200.0 };
    double[] surgehead = new double[] { 80.0, 72.0, 70.0, 65.0 };
    comp.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeflow, surgehead);
    comp.run();
    return comp;
  }

  /**
   * After {@code runAntiSurgeCalc()} the recycle leg ({@code splitStream(1)}) must reflect the calculator's setpoint,
   * not the splitter inlet's full flow. Pre-fix this returned the full inlet flow because
   * {@link Splitter#setFlowRates(double[], String)} rebuilds the split-stream array as full-flow clones and
   * {@code calcSplitFactors()} only runs from inside {@code Splitter.run(UUID)}.
   */
  @Test
  public void testRecycleLegReflectsSetpoint() {
    Compressor comp = buildCompressorWithSurgeCurve(20000.0);
    Splitter splitter = new Splitter("anti surge splitter", comp.getOutletStream(), 2);
    splitter.setFlowRates(new double[] { -1.0, 1.0 }, "kg/hr");
    splitter.run();

    Calculator calc = new Calculator("anti surge calc");
    calc.addInputVariable(comp);
    calc.setOutputVariable(splitter);
    calc.runAntiSurgeCalc(UUID.randomUUID());

    double splitterInletFlow = splitter.getInletStream().getFlowRate("m3/hr");
    double recycleFlow = splitter.getSplitStream(1).getFlowRate("m3/hr");

    // Setpoint must be strictly below the splitter inlet (forward leg reserved).
    assertTrue(recycleFlow < splitterInletFlow,
        "recycle " + recycleFlow + " m3/hr must be < splitter inlet " + splitterInletFlow);
    // Setpoint must be finite and non-negative.
    assertTrue(Double.isFinite(recycleFlow) && recycleFlow >= 0.0,
        "recycle flow must be finite and non-negative, got " + recycleFlow);
  }

  /**
   * When inlet flow comfortably exceeds the surge flow rate the calculator short-circuits and closes the recycle valve
   * to (effectively) zero.
   */
  @Test
  public void testRecycleClosesWhenWellAboveSurge() {
    // 200000 kg/hr at the chart conditions sits well above the surge curve.
    Compressor comp = buildCompressorWithSurgeCurve(200000.0);
    Splitter splitter = new Splitter("anti surge splitter", comp.getOutletStream(), 2);
    splitter.setFlowRates(new double[] { -1.0, 5000.0 }, "kg/hr");
    splitter.run();

    Calculator calc = new Calculator("anti surge calc");
    calc.addInputVariable(comp);
    calc.setOutputVariable(splitter);
    calc.runAntiSurgeCalc(UUID.randomUUID());

    double inletFlow = comp.getInletStream().getFlowRate("m3/hr");
    double surgeFlow = comp.getSurgeFlowRate();
    double recycleFlow = splitter.getSplitStream(1).getFlowRate("m3/hr");

    assertTrue(inletFlow > 1.2 * surgeFlow,
        "test precondition: inlet " + inletFlow + " must exceed 1.2 * surge " + surgeFlow);
    // Closed to a negligible bleed (< 1% of inlet).
    assertTrue(recycleFlow < 0.01 * inletFlow, "recycle " + recycleFlow
        + " m3/hr must be ~closed when well above surge (inlet=" + inletFlow + ", surge=" + surgeFlow + ")");
  }

  /**
   * Non-finite or zero surge flow must not propagate NaN into the splitter setpoint; the calculator should skip without
   * throwing.
   */
  @Test
  public void testNonFiniteSurgeSkipsSafely() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 100.0);
    fluid.setMixingRule(2);
    fluid.setTemperature(24.0, "C");
    fluid.setPressure(48.0, "bara");
    fluid.setTotalFlowRate(1000.0, "kg/hr");
    Stream s = new Stream("s", fluid);
    s.run();

    // Compressor without a surge curve -> getSurgeFlowRate() returns NaN.
    Compressor comp = new Compressor("cmp no curve", s);
    comp.setOutletPressure(60.0, "bara");
    comp.run();

    Splitter splitter = new Splitter("split", comp.getOutletStream(), 2);
    splitter.setFlowRates(new double[] { -1.0, 1.0 }, "kg/hr");
    splitter.run();

    double recycleBefore = splitter.getSplitStream(1).getFlowRate("m3/hr");

    Calculator calc = new Calculator("calc");
    calc.addInputVariable(comp);
    calc.setOutputVariable(splitter);
    // Must not throw, must not produce NaN.
    calc.runAntiSurgeCalc(UUID.randomUUID());

    double recycleAfter = splitter.getSplitStream(1).getFlowRate("m3/hr");
    assertTrue(Double.isFinite(recycleAfter),
        "recycle must remain finite when surge data is missing, got " + recycleAfter);
    assertEquals(recycleBefore, recycleAfter, 1e-9, "recycle should be unchanged when calculator skips");
  }

  /**
   * The recycle must not be slammed shut when it is the recycle itself that holds the compressor above the surge line.
   *
   * <p>
   * The recycle is fed back into the compressor suction, so {@code getInletStream()} already contains it. Testing the
   * raw inlet flow against {@code 1.2 * surgeFlow} therefore asked "is the machine safe because of the recycle?" and
   * then removed exactly the flow that made it safe, producing a limit cycle in which the recycle tear stream swung
   * between ~0 and its full value on every pass and the owning process never reported solved.
   * </p>
   */
  @Test
  public void testRecycleNotSlammedShutWhenItIsHoldingTheMachineAboveSurge() {
    Compressor comp = buildCompressorWithSurgeCurve(200000.0);
    double inletFlow = comp.getInletStream().getFlowRate("m3/hr");
    double inletMassFlow = comp.getInletStream().getFlowRate("kg/hr");
    double surgeFlow = comp.getSurgeFlowRate();
    assertTrue(inletFlow > 1.2 * surgeFlow,
        "test precondition: raw inlet " + inletFlow + " must exceed 1.2 * surge " + surgeFlow);

    // Configure a recycle large enough that the fresh feed alone (inlet - recycle)
    // sits below the 1.2 * surge shortcut threshold.
    double suctionDensity = inletMassFlow / inletFlow;
    double targetRecycleMass = (inletFlow - 1.1 * surgeFlow) * suctionDensity;
    Splitter splitter = new Splitter("anti surge splitter", comp.getOutletStream(), 2);
    splitter.setFlowRates(new double[] { -1.0, targetRecycleMass }, "kg/hr");
    splitter.run();

    double recycleMassBefore = splitter.getSplitStream(1).getFlowRate("kg/hr");
    assertTrue(inletFlow - recycleMassBefore / suctionDensity < 1.2 * surgeFlow,
        "test precondition: fresh feed must be below the shortcut threshold");

    Calculator calc = new Calculator("anti surge calc");
    calc.addInputVariable(comp);
    calc.setOutputVariable(splitter);
    calc.runAntiSurgeCalc(UUID.randomUUID());

    double recycleMassAfter = splitter.getSplitStream(1).getFlowRate("kg/hr");
    assertTrue(recycleMassAfter > 0.01 * recycleMassBefore, "recycle must be closed gradually, not slammed to zero: "
        + recycleMassBefore + " -> " + recycleMassAfter + " kg/hr");
  }

  /**
   * The recycle setpoint must be expressed at compressor-suction conditions.
   *
   * <p>
   * The surge curve, and therefore the whole control law, is referenced to suction volumetric flow, but the anti-surge
   * splitter sits on the discharge side where the same mass occupies a much smaller volume. Writing a suction-sized
   * volumetric number straight onto the discharge-side splitter overshot the intended recycle by roughly the volume
   * ratio across the machine. The setpoint is therefore converted through the suction density and written as a mass
   * flow.
   * </p>
   */
  @Test
  public void testRecycleSetpointIsReferencedToSuctionConditions() {
    Compressor comp = buildCompressorWithSurgeCurve(20000.0);
    double inletVolFlow = comp.getInletStream().getFlowRate("m3/hr");
    double inletMassFlow = comp.getInletStream().getFlowRate("kg/hr");
    double suctionDensity = inletMassFlow / inletVolFlow;

    Splitter splitter = new Splitter("anti surge splitter", comp.getOutletStream(), 2);
    splitter.setFlowRates(new double[] { -1.0, 1.0 }, "kg/hr");
    splitter.run();

    Calculator calc = new Calculator("anti surge calc");
    calc.addInputVariable(comp);
    calc.setOutputVariable(splitter);
    calc.runAntiSurgeCalc(UUID.randomUUID());

    // Convert the resulting recycle mass back to a suction volume and check it is a
    // physically sensible fraction of the surge flow rather than a discharge volume
    // mistaken for a suction volume (which inflates it by the compression ratio).
    double recycleAtSuction = splitter.getSplitStream(1).getFlowRate("kg/hr") / suctionDensity;
    double surgeFlow = comp.getSurgeFlowRate();
    // Calculator caps the recycle setpoint at 2 x the surge flow.
    assertTrue(recycleAtSuction <= 2.0 * surgeFlow + 1.0e-6, "recycle referenced to suction (" + recycleAtSuction
        + " m3/hr) must respect the surge-flow cap (" + surgeFlow + " m3/hr)");
    assertTrue(recycleAtSuction >= 0.0, "recycle must be non-negative, got " + recycleAtSuction);
  }

  /**
   * A positive surge-control margin set on the COMPRESSOR must move the recycle target to the right of the surge line.
   *
   * <p>
   * The legacy control law drives the operating point to {@code inletFlow == surgeFlow}, i.e. exactly onto the surge
   * line with zero margin, which is not how any machine is operated. The margin is a property of the machine and its
   * chart ({@link Compressor#setSurgeControlMargin(double)} / {@link Compressor#getControlLineFlow()}), so the
   * calculator reads it from the compressor rather than owning a duplicate setting. The target becomes
   * {@code surgeFlow * (1 + margin)}, so for the same operating point the calculator must ask for strictly more
   * recycle.
   * </p>
   */
  @Test
  public void testSurgeControlMarginIncreasesTheRecycleTarget() {
    assertEquals(0.0, buildCompressorWithSurgeCurve(20000.0).getSurgeControlMargin(), 1.0e-12,
        "default compressor margin must stay 0.0 so legacy models are unchanged");

    double recycleNoMargin = runAntiSurgeOnceAndReadRecycle(0.0);
    double recycleWithMargin = runAntiSurgeOnceAndReadRecycle(0.10);

    assertTrue(recycleWithMargin > recycleNoMargin,
        "a 10 % control margin must ask for more recycle than control on the surge line itself: " + recycleNoMargin
            + " -> " + recycleWithMargin + " kg/hr");
  }

  /**
   * Builds a turned-down compressor with an anti-surge splitter, runs one anti-surge step with the given control margin
   * set on the compressor, and returns the resulting recycle mass flow.
   *
   * @param margin the surge-control margin as a fraction of the surge flow
   * @return the recycle leg mass flow in kg/hr after one calculator step
   */
  private double runAntiSurgeOnceAndReadRecycle(double margin) {
    Compressor comp = buildCompressorWithSurgeCurve(20000.0);
    comp.setSurgeControlMargin(margin);
    Splitter splitter = new Splitter("anti surge splitter", comp.getOutletStream(), 2);
    splitter.setFlowRates(new double[] { -1.0, 1.0 }, "kg/hr");
    splitter.run();

    Calculator calc = new Calculator("anti surge calc");
    calc.addInputVariable(comp);
    calc.setOutputVariable(splitter);
    calc.runAntiSurgeCalc(UUID.randomUUID());

    return splitter.getSplitStream(1).getFlowRate("kg/hr");
  }
}
