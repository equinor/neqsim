package neqsim.process.equipment.pipeline.twophasepipe.closure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/** Tests for gas-liquid interfacial-friction closures. */
class InterfacialFrictionTest {
  private static final double GRAVITY = 9.81;
  private static final double DEFAULT_BUBBLE_SURFACE_TENSION = 0.02;

  @Test
  void dispersedBubbleForceMatchesSchillerNaumannDragLaw() {
    InterfacialFriction friction = new InterfacialFriction();
    double[] diameters = { 0.05, 0.10, 0.30 };
    double[] liquidHoldups = { 0.80, 0.95, 1.0 - 1.0e-8 };
    double[] slipVelocities = { -1.0, -0.10, 0.10, 1.0 };

    for (double diameter : diameters) {
      for (double liquidHoldup : liquidHoldups) {
        for (double slipVelocity : slipVelocities) {
          double gasVelocity = 0.5 + slipVelocity;
          double liquidVelocity = 0.5;
          double expected = expectedBubbleDragForcePerLength(gasVelocity, liquidVelocity, 5.0, 1000.0, 1.0e-3,
              liquidHoldup, diameter);

          double actual = friction.calcCorrectedBubbleDragForce(FlowRegime.DISPERSED_BUBBLE, gasVelocity,
              liquidVelocity, 5.0, 1000.0, 1.5e-5, 1.0e-3, liquidHoldup, diameter, 0.072);

          assertEquals(expected, actual, Math.max(1.0e-12, Math.abs(expected) * 1.0e-12),
              "Schiller-Naumann force mismatch at D=" + diameter + " m, alphaL=" + liquidHoldup + ", slip="
                  + slipVelocity + " m/s");
        }
      }
    }
  }

  @Test
  void bubbleDragIsOddInSlipAndVanishesAtPhaseLimits() {
    InterfacialFriction friction = new InterfacialFriction();
    double forward = friction.calcCorrectedBubbleDragForce(FlowRegime.BUBBLE, 0.7, 0.5, 5.0, 1000.0, 1.5e-5, 1.0e-3,
        0.9, 0.1, 0.072);
    double reverse = friction.calcCorrectedBubbleDragForce(FlowRegime.BUBBLE, 0.3, 0.5, 5.0, 1000.0, 1.5e-5, 1.0e-3,
        0.9, 0.1, 0.072);
    double zeroSlip = friction.calcCorrectedBubbleDragForce(FlowRegime.BUBBLE, 0.5, 0.5, 5.0, 1000.0, 1.5e-5, 1.0e-3,
        0.9, 0.1, 0.072);
    double noGas = friction.calcCorrectedBubbleDragForce(FlowRegime.BUBBLE, 0.7, 0.5, 5.0, 1000.0, 1.5e-5, 1.0e-3, 1.0,
        0.1, 0.072);

    assertTrue(forward > 0.0);
    assertEquals(-forward, reverse, Math.abs(forward) * 1.0e-12);
    assertEquals(0.0, zeroSlip, 0.0);
    assertEquals(0.0, noGas, 0.0);
  }

  @Test
  void correctedForceQueryDoesNotMutateCompatibilityMode() {
    InterfacialFriction friction = new InterfacialFriction();
    double corrected = friction.calcCorrectedBubbleDragForce(FlowRegime.BUBBLE, 0.7, 0.5, 5.0, 1000.0, 1.5e-5, 1.0e-3,
        0.9, 0.1, 0.072);

    assertFalse(friction.isUseCorrectedBubbleDrag());
    friction.setUseCorrectedBubbleDrag(true);
    double selected = friction.calcInterfacialForce(FlowRegime.BUBBLE, 0.7, 0.5, 5.0, 1000.0, 1.5e-5, 1.0e-3, 0.9, 0.1,
        0.072);

    assertEquals(corrected, selected, Math.abs(corrected) * 1.0e-14);
  }

  @Test
  void bubbleDiameterConfigurationControlsInterfacialAreaWithoutChangingDefault() {
    InterfacialFriction friction = new InterfacialFriction();
    double defaultDiameter = inferredBubbleDiameter(friction, 0.072);
    double expectedDefault = 2.0 * Math.sqrt(0.725 * DEFAULT_BUBBLE_SURFACE_TENSION / ((1000.0 - 5.0) * GRAVITY));

    assertEquals(expectedDefault, defaultDiameter, 1.0e-15);

    friction.getBubbleSizeClosure().setSurfaceTension(0.08);
    double configuredDiameter = inferredBubbleDiameter(friction, 0.072);
    assertEquals(2.0, configuredDiameter / defaultDiameter, 1.0e-12);

    friction.getBubbleSizeClosure().setUseLocalSurfaceTension(true);
    double localDiameter = inferredBubbleDiameter(friction, 0.045);
    assertEquals(Math.sqrt(0.045 / 0.08), localDiameter / configuredDiameter, 1.0e-12);
  }

  @Test
  void compatibilityScalingRemainsTheDefaultAndFreezesLegacyUnderprediction() {
    InterfacialFriction friction = new InterfacialFriction();
    double diameter = 0.1;
    double liquidHoldup = 0.9;
    double legacy = friction.calcInterfacialForce(FlowRegime.BUBBLE, 0.7, 0.5, 5.0, 1000.0, 1.5e-5, 1.0e-3,
        liquidHoldup, diameter, 0.072);
    double corrected = friction.calcCorrectedBubbleDragForce(FlowRegime.BUBBLE, 0.7, 0.5, 5.0, 1000.0, 1.5e-5, 1.0e-3,
        liquidHoldup, diameter, 0.072);
    double bubbleDiameter = 2.0 * Math.sqrt(0.725 * DEFAULT_BUBBLE_SURFACE_TENSION / ((1000.0 - 5.0) * GRAVITY));
    bubbleDiameter = Math.min(bubbleDiameter, diameter / 5.0);

    assertFalse(friction.isUseCorrectedBubbleDrag());
    assertTrue(legacy < corrected);
    assertEquals(bubbleDiameter / diameter, legacy / corrected, 1.0e-14);
  }

  private static double inferredBubbleDiameter(InterfacialFriction friction, double surfaceTension) {
    double diameter = 1.0;
    double liquidHoldup = 0.9;
    InterfacialFriction.InterfacialFrictionResult result = friction.calculate(FlowRegime.BUBBLE, 0.7, 0.5, 5.0, 1000.0,
        1.5e-5, 1.0e-3, liquidHoldup, diameter, surfaceTension);
    double gasHoldup = 1.0 - liquidHoldup;
    double area = Math.PI * diameter * diameter / 4.0;
    return 6.0 * gasHoldup * area / result.interfacialAreaPerLength;
  }

  private double expectedBubbleDragForcePerLength(double gasVelocity, double liquidVelocity, double gasDensity,
      double liquidDensity, double liquidViscosity, double liquidHoldup, double diameter) {
    double bubbleDiameter = 2.0
        * Math.sqrt(0.725 * DEFAULT_BUBBLE_SURFACE_TENSION / ((liquidDensity - gasDensity) * GRAVITY));
    bubbleDiameter = Math.min(bubbleDiameter, diameter / 5.0);
    double slipVelocity = gasVelocity - liquidVelocity;
    double bubbleReynoldsNumber = liquidDensity * Math.abs(slipVelocity) * bubbleDiameter / liquidViscosity;
    double dragCoefficient;
    if (bubbleReynoldsNumber < 0.1) {
      dragCoefficient = 240.0;
    } else if (bubbleReynoldsNumber < 1000.0) {
      dragCoefficient = 24.0 / bubbleReynoldsNumber * (1.0 + 0.15 * Math.pow(bubbleReynoldsNumber, 0.687));
    } else {
      dragCoefficient = 0.44;
    }
    double gasHoldup = 1.0 - liquidHoldup;
    double area = Math.PI * diameter * diameter / 4.0;
    return 0.75 * dragCoefficient * liquidDensity * gasHoldup * area / bubbleDiameter * slipVelocity
        * Math.abs(slipVelocity);
  }
}
