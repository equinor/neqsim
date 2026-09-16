package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import org.junit.jupiter.api.Test;

/** The upward bubble criterion must not interpret an inadmissible inferred void fraction as bubble flow. */
class FlowRegimeBubbleDomainTest {
  private static final double GAS_DENSITY = 1.7282775171241345;
  private static final double LIQUID_DENSITY = 851.261721181216;
  private static final double SURFACE_TENSION = 0.02;

  @Test
  void capturedRiserPressureProbeCannotSwitchToBubbleThroughANegativeVoidFraction() {
    // Cell 8 of the 16-cell backward-Euler/interpolation failure at t=0.6 s. These two states differ by the
    // solver's ordinary positive pressure probe, not by a physical bubble-coalescence transition.
    TwoFluidSection base = section(0.9832433233684708, -1.1716640515007488);
    setCapturedState(base, 152915.13351088561, GAS_DENSITY, 0.061942406331990385);
    TwoFluidSection probe = base.clone();
    setCapturedState(probe, 152915.28305193465, 1.7282792074736155, 0.06194234950938367);
    assertTrue(bubbleTransport(base) > 0.0);
    assertTrue(bubbleTransport(probe) < 0.0);
    assertTrue(base.getSuperficialGasVelocity() / bubbleTransport(base) > 1.0);
    assertTrue(probe.getSuperficialGasVelocity() / bubbleTransport(probe) < 0.0);

    FlowRegimeDetector detector = new FlowRegimeDetector();
    assertEquals(FlowRegime.SLUG, detector.classify(base));
    assertEquals(FlowRegime.SLUG, detector.classify(probe));
    double baseForce = drag(base);
    double probeForce = drag(probe);
    assertTrue(Math.abs(probeForce - baseForce) < 1.0e-5 * Math.abs(baseForce),
        "A pressure derivative must not include the hundreds-fold drag jump from a negative inferred void fraction");
  }

  @Test
  void countercurrentBubbleTransportPoleHasNoNegativeVoidBranchOnEitherSide() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    double gasFlow = 0.3;
    double rise = bubbleRiseVelocity(GAS_DENSITY, LIQUID_DENSITY);
    for (double displacement : new double[] { -1.0e-6, -1.0e-9, -1.0e-12, 0.0, 1.0e-12, 1.0e-9, 1.0e-6 }) {
      TwoFluidSection section = section(gasFlow, -gasFlow - rise + displacement);
      double[] before = section.getStateVector();
      assertEquals(FlowRegime.SLUG, detector.detectFlowRegime(section), "transport displacement=" + displacement);
      assertEquals(FlowRegime.SLUG, detector.classify(section));
      assertArrayEquals(before, section.getStateVector(), 0.0);
    }
  }

  @Test
  void positiveAdmissibleBubbleFractionsRemainBubbleForCocurrentAndCountercurrentFlow() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    for (double liquidFlow : new double[] { -0.05, 0.0, 0.2 }) {
      TwoFluidSection section = section(0.02, liquidFlow);
      double inferredVoid = section.getSuperficialGasVelocity() / bubbleTransport(section);
      assertTrue(inferredVoid > 0.0 && inferredVoid < 0.25);
      assertEquals(FlowRegime.BUBBLE, detector.detectFlowRegime(section));
      assertEquals(FlowRegime.BUBBLE, detector.classify(section));
    }
    assertEquals(FlowRegime.BUBBLE, detector.detectFlowRegime(section(0.0, 0.0)),
        "Exactly stagnant gas with positive bubble rise velocity retains the existing classification");
  }

  @Test
  void negativeGasTransportDoesNotPassTheNonnegativeVoidCriterion() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    TwoFluidSection section = section(-0.02, 0.2);
    assertTrue(bubbleTransport(section) > 0.0);
    assertTrue(section.getSuperficialGasVelocity() / bubbleTransport(section) < 0.0);
    assertEquals(FlowRegime.SLUG, detector.detectFlowRegime(section));
  }

  @Test
  void originalPositiveBubbleCoalescenceThresholdStillSeparatesTheRegimes() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    double liquidFlow = 0.2;
    double threshold = (liquidFlow + bubbleRiseVelocity(GAS_DENSITY, LIQUID_DENSITY)) / 3.0;
    assertEquals(FlowRegime.BUBBLE, detector.detectFlowRegime(section(threshold * (1.0 - 1.0e-8), liquidFlow)));
    assertEquals(FlowRegime.SLUG, detector.detectFlowRegime(section(threshold * (1.0 + 1.0e-8), liquidFlow)));
  }

  private static TwoFluidSection section(double gasSuperficialVelocity, double liquidSuperficialVelocity) {
    TwoFluidSection section = new TwoFluidSection(18.4609375, 2.171875, 0.0762, 0.46462201361810224);
    section.setPressure(152915.13351088561);
    section.setTemperature(298.15);
    section.setGasDensity(GAS_DENSITY);
    section.setLiquidDensity(LIQUID_DENSITY);
    section.setOilDensity(LIQUID_DENSITY);
    section.setWaterDensity(1000.0);
    section.setGasHoldup(0.2);
    section.setLiquidHoldup(0.8);
    section.setOilHoldup(0.8);
    section.setWaterHoldup(0.0);
    section.setWaterCut(0.0);
    section.setOilFractionInLiquid(1.0);
    section.setGasVelocity(gasSuperficialVelocity / 0.2);
    section.setLiquidVelocity(liquidSuperficialVelocity / 0.8);
    section.setOilVelocity(liquidSuperficialVelocity / 0.8);
    section.setWaterVelocity(0.0);
    section.setGasViscosity(1.8137846670633024e-5);
    section.setLiquidViscosity(0.006846502418612348);
    section.setOilViscosity(0.006846502418612348);
    section.setWaterViscosity(1.0e-3);
    section.setSurfaceTension(SURFACE_TENSION);
    section.setRoughness(1.5e-6);
    section.updateConservativeVariables();
    section.updateDerivedQuantities();
    return section;
  }

  private static void setCapturedState(TwoFluidSection section, double pressure, double gasDensity, double gasHoldup) {
    section.setPressure(pressure);
    section.setGasDensity(gasDensity);
    section.setGasHoldup(gasHoldup);
    section.setLiquidHoldup(1.0 - gasHoldup);
    section.setOilHoldup(1.0 - gasHoldup);
    section.setGasVelocity(15.87350866058736);
    section.setLiquidVelocity(-1.2490321057146259);
    section.setOilVelocity(-1.2490321057146259);
    section.updateConservativeVariables();
    section.updateDerivedQuantities();
  }

  private static double bubbleTransport(TwoFluidSection section) {
    return section.getSuperficialGasVelocity() + section.getSuperficialLiquidVelocity()
        + bubbleRiseVelocity(section.getGasDensity(), section.getLiquidDensity());
  }

  private static double bubbleRiseVelocity(double gasDensity, double liquidDensity) {
    return 1.53
        * Math.pow(9.81 * SURFACE_TENSION * (liquidDensity - gasDensity) / (liquidDensity * liquidDensity), 0.25);
  }

  private static double drag(TwoFluidSection section) {
    return new InterfacialFriction().calcInterfacialForce(section.getFlowRegime(), section.getGasVelocity(),
        section.getLiquidVelocity(), section.getGasDensity(), section.getLiquidDensity(), section.getGasViscosity(),
        section.getLiquidViscosity(), section.getLiquidHoldup(), section.getDiameter(), section.getSurfaceTension());
  }
}
