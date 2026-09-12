package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.process.equipment.pipeline.twophasepipe.FlowRegimeDetector.DetectionMethod;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;

/**
 * Co-current regime detection must describe the same physical flow after reversing the axial coordinate.
 *
 * <p>
 * The captured riser velocities are a numerical failure reproducer, not an experimental slug-flow reference.
 * Countercurrent criteria retain their existing behavior and are not qualified by coordinate reversal of co-current
 * states.
 * </p>
 */
class FlowRegimeOrientationTest {
  @ParameterizedTest
  @EnumSource(DetectionMethod.class)
  void capturedRiserFallbackHasTheSameRegimeAndOppositeDragInEitherCoordinate(DetectionMethod method) {
    TwoFluidSection backward = section(Math.PI / 2.0, 0.3742200011, 0.0, -2.06043, -3.99753, 0.0);
    TwoFluidSection reversed = reverse(backward);
    FlowRegimeDetector detector = detector(method);
    assertEquivalent(detector, backward, reversed);

    InterfacialFriction friction = new InterfacialFriction();
    double backwardForce = interfacialForce(friction, backward);
    double reversedForce = interfacialForce(friction, reversed);
    assertEquals(-backwardForce, reversedForce, 1.0e-12);
    assertTrue(backwardForce * (backward.getGasVelocity() - backward.getLiquidVelocity()) >= 0.0,
        "Interfacial drag must dissipate relative kinetic energy in either coordinate");
  }

  @ParameterizedTest
  @EnumSource(DetectionMethod.class)
  void nearHorizontalClosureWeightsAreIndependentOfCoordinateDirection(DetectionMethod method) {
    FlowRegimeDetector detector = detector(method);
    boolean foundBlendedState = false;
    for (double degrees : new double[] { -3.0, 0.0, 3.0 }) {
      for (int sample = 0; sample <= 40; sample++) {
        double gasSuperficialVelocity = 0.5 + 0.1 * sample;
        TwoFluidSection forward = section(Math.toRadians(degrees), 0.95, 0.0, gasSuperficialVelocity / 0.95,
            0.035 / 0.05, 0.0);
        forward.setDiameter(0.2);
        forward.setGasDensity(40.0);
        forward.setOilDensity(600.0);
        forward.setLiquidDensity(600.0);
        forward.setOilViscosity(5.0e-4);
        forward.setLiquidViscosity(5.0e-4);
        forward.updateConservativeVariables();
        assertEquivalent(detector, forward, reverse(forward));
        Map<FlowRegime, Double> weights = forward.getRegimeWeights();
        foundBlendedState |= weights != null && weights.size() > 1;
      }
    }
    if (method == DetectionMethod.MECHANISTIC) {
      assertTrue(foundBlendedState, "The sweep must exercise a closure blend, not only its dominant label");
    }
  }

  @ParameterizedTest
  @EnumSource(DetectionMethod.class)
  void aStagnantPresentPhaseUsesTheMovingPhaseDirectionWithoutChangingInventory(DetectionMethod method) {
    FlowRegimeDetector detector = detector(method);
    double[][] velocities = { { 0.0, -2.0 }, { -2.0, 0.0 }, { -1.0e-12, -2.0 }, { -2.0, -1.0e-12 } };
    for (double degrees : new double[] { -90.0, -3.0, 0.0, 3.0, 90.0 }) {
      for (double[] velocity : velocities) {
        TwoFluidSection backward = section(Math.toRadians(degrees), 0.4, 0.0, velocity[0], velocity[1], 0.0);
        assertTrue(backward.getGasMassPerLength() > 0.0 && backward.getOilMassPerLength() > 0.0);
        assertEquivalent(detector, backward, reverse(backward));
      }
    }
  }

  @ParameterizedTest
  @EnumSource(DetectionMethod.class)
  void independentOilAndWaterSlipSurvivesDetectionAndClassification(DetectionMethod method) {
    FlowRegimeDetector detector = detector(method);
    for (double degrees : new double[] { -90.0, -3.0, 0.0, 3.0, 90.0 }) {
      TwoFluidSection backward = section(Math.toRadians(degrees), 0.4, 0.25, -3.0, -2.0, -4.0);
      assertTrue(backward.getOilMassPerLength() > 0.0 && backward.getWaterMassPerLength() > 0.0);
      assertNotEquals(backward.getOilVelocity(), backward.getWaterVelocity());
      assertEquivalent(detector, backward, reverse(backward));
    }
  }

  @ParameterizedTest
  @EnumSource(DetectionMethod.class)
  void exactlyAbsentPhasesStaySinglePhaseInBackwardFlow(DetectionMethod method) {
    FlowRegimeDetector detector = detector(method);
    TwoFluidSection gas = section(Math.PI / 2.0, 1.0, 0.0, -2.0, 0.0, 0.0);
    assertEquivalent(detector, gas, reverse(gas));
    assertEquals(FlowRegime.SINGLE_PHASE_GAS, gas.getFlowRegime());

    TwoFluidSection water = section(Math.PI / 2.0, 0.0, 1.0, 0.0, 0.0, -2.0);
    assertEquivalent(detector, water, reverse(water));
    assertEquals(FlowRegime.SINGLE_PHASE_LIQUID, water.getFlowRegime());
  }

  @ParameterizedTest
  @EnumSource(DetectionMethod.class)
  void completeStagnationDoesNotSelectAnArtificialReverseDirection(DetectionMethod method) {
    FlowRegimeDetector detector = detector(method);
    for (double degrees : new double[] { -90.0, 0.0, 90.0 }) {
      TwoFluidSection positiveZero = section(Math.toRadians(degrees), 0.4, 0.0, 0.0, 0.0, 0.0);
      TwoFluidSection negativeZero = section(Math.toRadians(degrees), 0.4, 0.0, -0.0, -0.0, -0.0);
      assertEquivalent(detector, positiveZero, negativeZero);
      if (method == DetectionMethod.MECHANISTIC && degrees == 90.0) {
        assertEquals(FlowRegime.BUBBLE, positiveZero.getFlowRegime(),
            "The existing zero-flow classification must remain unchanged by orientation handling");
      }
    }
  }

  @Test
  void countercurrentInputsAreNotReplacedByAbsolutePhaseVelocities() {
    FlowRegimeDetector detector = detector(DetectionMethod.MECHANISTIC);
    for (double sign : new double[] { -1.0, 1.0 }) {
      TwoFluidSection countercurrent = section(Math.PI / 2.0, 0.4, 0.0, sign * 0.3 / 0.4, -sign * 0.5 / 0.6, 0.0);
      TwoFluidSection before = countercurrent.clone();
      // This pins compatibility outside the bounded correction; it is not a countercurrent validation anchor.
      assertEquals(FlowRegime.BUBBLE, detector.detectFlowRegime(countercurrent));
      detector.classify(countercurrent);
      assertEquals(FlowRegime.BUBBLE, countercurrent.getFlowRegime());
      assertPhysicalStateEquals(before, countercurrent);
    }
    TwoFluidSection forwardMagnitudes = section(Math.PI / 2.0, 0.4, 0.0, 0.3 / 0.4, 0.5 / 0.6, 0.0);
    assertEquals(FlowRegime.SLUG, detector.detectFlowRegime(forwardMagnitudes),
        "Taking absolute values would silently select a different closure for the countercurrent states");
  }

  private static FlowRegimeDetector detector(DetectionMethod method) {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    detector.setDetectionMethod(method);
    return detector;
  }

  private static TwoFluidSection section(double inclination, double gasHoldup, double waterHoldup, double gasVelocity,
      double oilVelocity, double waterVelocity) {
    TwoFluidSection section = new TwoFluidSection(22.8046875, 2.171875, 0.0762, inclination);
    double oilHoldup = 1.0 - gasHoldup - waterHoldup;
    double liquidHoldup = oilHoldup + waterHoldup;
    double oilDensity = 851.2603;
    double waterDensity = 1000.0;
    double liquidMass = oilHoldup * oilDensity + waterHoldup * waterDensity;
    double liquidVelocity = liquidMass > 0.0
        ? (oilHoldup * oilDensity * oilVelocity + waterHoldup * waterDensity * waterVelocity) / liquidMass
        : 0.0;
    section.setGasHoldup(gasHoldup);
    section.setLiquidHoldup(liquidHoldup);
    section.setOilHoldup(oilHoldup);
    section.setWaterHoldup(waterHoldup);
    section.setWaterCut(liquidHoldup > 0.0 ? waterHoldup / liquidHoldup : 0.0);
    section.setOilFractionInLiquid(liquidHoldup > 0.0 ? oilHoldup / liquidHoldup : 1.0);
    section.setGasDensity(1.5505756);
    section.setOilDensity(oilDensity);
    section.setWaterDensity(waterDensity);
    section.setLiquidDensity(liquidHoldup > 0.0 ? liquidMass / liquidHoldup : oilDensity);
    section.setGasViscosity(1.8e-5);
    section.setOilViscosity(2.0e-3);
    section.setWaterViscosity(1.0e-3);
    section.setLiquidViscosity(2.0e-3);
    section.setSurfaceTension(0.02);
    section.setPressure(137198.2167);
    section.setTemperature(298.15);
    section.setGasVelocity(gasVelocity);
    section.setLiquidVelocity(liquidVelocity);
    section.setOilVelocity(oilVelocity);
    section.setWaterVelocity(waterVelocity);
    section.setRoughness(1.5e-6);
    section.setFlowRegime(FlowRegime.CHURN);
    section.updateDerivedQuantities();
    section.updateConservativeVariables();
    return section;
  }

  private static TwoFluidSection reverse(TwoFluidSection original) {
    TwoFluidSection reversed = original.clone();
    reversed.setInclination(-original.getInclination());
    reversed.setGasVelocity(-original.getGasVelocity());
    reversed.setLiquidVelocity(-original.getLiquidVelocity());
    reversed.setOilVelocity(-original.getOilVelocity());
    reversed.setWaterVelocity(-original.getWaterVelocity());
    reversed.updateDerivedQuantities();
    reversed.updateConservativeVariables();
    return reversed;
  }

  private static void assertEquivalent(FlowRegimeDetector detector, TwoFluidSection first, TwoFluidSection second) {
    TwoFluidSection firstBefore = first.clone();
    TwoFluidSection secondBefore = second.clone();
    FlowRegime firstDetected = detector.detectFlowRegime(first);
    FlowRegime secondDetected = detector.detectFlowRegime(second);
    assertPhysicalStateEquals(firstBefore, first);
    assertPhysicalStateEquals(secondBefore, second);
    assertEquals(firstBefore.getFlowRegime(), first.getFlowRegime());
    assertEquals(secondBefore.getFlowRegime(), second.getFlowRegime());
    assertEquals(firstBefore.getRegimeWeights(), first.getRegimeWeights());
    assertEquals(secondBefore.getRegimeWeights(), second.getRegimeWeights());
    assertEquals(firstDetected, secondDetected, "Co-current regime must be independent of axial coordinate direction");
    assertEquals(detector.classify(first), detector.classify(second));
    assertEquals(first.getRegimeWeights(), second.getRegimeWeights(),
        "Closure weights must transform with the dominant regime");
    assertPhysicalStateEquals(firstBefore, first);
    assertPhysicalStateEquals(secondBefore, second);
  }

  private static void assertPhysicalStateEquals(TwoFluidSection expected, TwoFluidSection actual) {
    assertArrayEquals(expected.getStateVector(), actual.getStateVector(), 0.0);
    assertEquals(expected.getGasVelocity(), actual.getGasVelocity(), 0.0);
    assertEquals(expected.getLiquidVelocity(), actual.getLiquidVelocity(), 0.0);
    assertEquals(expected.getOilVelocity(), actual.getOilVelocity(), 0.0);
    assertEquals(expected.getWaterVelocity(), actual.getWaterVelocity(), 0.0);
    assertEquals(expected.getInclination(), actual.getInclination(), 0.0);
    assertEquals(expected.getPosition(), actual.getPosition(), 0.0);
    assertEquals(expected.getLength(), actual.getLength(), 0.0);
    assertEquals(expected.getArea(), actual.getArea(), 0.0);
    assertEquals(expected.getPressure(), actual.getPressure(), 0.0);
    assertEquals(expected.getGasHoldup(), actual.getGasHoldup(), 0.0);
    assertEquals(expected.getOilHoldup(), actual.getOilHoldup(), 0.0);
    assertEquals(expected.getWaterHoldup(), actual.getWaterHoldup(), 0.0);
  }

  private static double interfacialForce(InterfacialFriction friction, TwoFluidSection section) {
    return friction.calcInterfacialForce(section.getFlowRegime(), section.getGasVelocity(), section.getLiquidVelocity(),
        section.getGasDensity(), section.getLiquidDensity(), section.getGasViscosity(), section.getLiquidViscosity(),
        section.getLiquidHoldup(), section.getDiameter(), section.getSurfaceTension());
  }
}
