package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;

/** Tests for the local implicit Schiller-Naumann source solve. */
class DispersedBubbleDragSolverTest {
  private static final double GAS_DENSITY = 5.0;
  private static final double LIQUID_DENSITY = 1000.0;
  private static final double GAS_VISCOSITY = 1.5e-5;
  private static final double LIQUID_VISCOSITY = 1.0e-3;
  private static final double LIQUID_HOLDUP = 0.8;
  private static final double DIAMETER = 0.05;
  private static final double SURFACE_TENSION = 0.072;

  @Test
  void nonlinearDragIsStableConservativeAndMonotoneThroughDtOverTauOneThousand() {
    InterfacialFriction dragLaw = new InterfacialFriction();
    double[] masses = { 1.0, 4.0, 0.0 };
    double[] momenta = { 1.0, 0.0, 0.0 };
    double initialSlip = 1.0;
    double force = Math.abs(dragLaw.calcCorrectedBubbleDragForce(FlowRegime.BUBBLE, initialSlip, 0.0, GAS_DENSITY,
        LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY, LIQUID_HOLDUP, DIAMETER, SURFACE_TENSION));
    double relaxationTime = initialSlip / ((1.0 / masses[0] + 1.0 / masses[1]) * force);
    double[] stiffnessRatios = { 1.0e-3, 0.1, 1.0, 10.0, 1.0e3 };

    for (double stiffnessRatio : stiffnessRatios) {
      double[] result = relax(FlowRegime.BUBBLE, masses, momenta, stiffnessRatio * relaxationTime, dragLaw);
      double resultSlip = result[0] / masses[0] - result[1] / masses[1];

      assertEquals(sum(momenta), sum(result), 2.0e-14);
      assertTrue(resultSlip > 0.0);
      assertTrue(resultSlip <= initialSlip);
      assertTrue(kineticEnergy(masses, result) <= kineticEnergy(masses, momenta) + 1.0e-13);
    }
  }

  @Test
  void reverseSlipIsSymmetricAndZeroSlipIsAnExactFixedPoint() {
    InterfacialFriction dragLaw = new InterfacialFriction();
    double[] masses = { 1.0, 4.0, 0.0 };
    double[] forwardMomenta = { 1.0, 0.0, 0.0 };
    double[] reverseMomenta = { -1.0, 0.0, 0.0 };
    double[] zeroSlipMomenta = { 1.0, 4.0, 0.0 };

    double[] forward = relax(FlowRegime.DISPERSED_BUBBLE, masses, forwardMomenta, 2.0, dragLaw);
    double[] reverse = relax(FlowRegime.DISPERSED_BUBBLE, masses, reverseMomenta, 2.0, dragLaw);
    double[] zeroSlip = relax(FlowRegime.BUBBLE, masses, zeroSlipMomenta, 2.0, dragLaw);

    for (int phase = 0; phase < masses.length; phase++) {
      assertEquals(-forward[phase], reverse[phase], 2.0e-14);
    }
    assertArrayEquals(zeroSlipMomenta, zeroSlip, 0.0);
  }

  @Test
  void threePhasePseudoLiquidPreservesOilWaterSlipAndDissipates() {
    InterfacialFriction dragLaw = new InterfacialFriction();
    double[] masses = { 1.0, 2.0, 3.0 };
    double[] momenta = { 1.0, 2.8, 0.0 };
    double initialOilWaterSlip = momenta[1] / masses[1] - momenta[2] / masses[2];

    double[] result = relax(FlowRegime.BUBBLE, masses, momenta, 5.0, dragLaw);

    double finalOilWaterSlip = result[1] / masses[1] - result[2] / masses[2];
    assertEquals(initialOilWaterSlip, finalOilWaterSlip, 2.0e-14);
    assertEquals(sum(momenta), sum(result), 2.0e-14);
    assertTrue(kineticEnergy(masses, result) <= kineticEnergy(masses, momenta) + 1.0e-13);
  }

  @Test
  void absentPhasesAreNotCreatedAndNonBubbleRegimeIsUntouched() {
    InterfacialFriction dragLaw = new InterfacialFriction();
    double[] oilOnlyLiquidMasses = { 1.0, 4.0, 0.0 };
    double[] momenta = { 1.0, 0.0, 0.2 };
    double[] noGasMasses = { 0.0, 4.0, 0.0 };
    double[] noGasMomenta = { 3.0e-9, 2.0, 0.0 };

    double[] oilOnly = relax(FlowRegime.BUBBLE, oilOnlyLiquidMasses, momenta, 1.0, dragLaw);
    double[] noGas = relax(FlowRegime.BUBBLE, noGasMasses, noGasMomenta, 1.0, dragLaw);
    double[] slug = relax(FlowRegime.SLUG, oilOnlyLiquidMasses, momenta, 1.0, dragLaw);

    assertEquals(0.0, oilOnly[2], 0.0);
    assertEquals(momenta[0] + momenta[1], oilOnly[0] + oilOnly[1], 2.0e-14);
    assertEquals(0.0, noGas[0], 0.0);
    assertEquals(noGasMomenta[1], noGas[1], 0.0);
    assertEquals(0.0, noGas[2], 0.0);
    assertArrayEquals(momenta, slug, 0.0);
  }

  @Test
  void repeatedCallsAreDeterministicAndDoNotMutateInputs() {
    InterfacialFriction dragLaw = new InterfacialFriction();
    double[] masses = { 1.0, 2.0, 3.0 };
    double[] momenta = { 1.0, 2.8, 0.0 };
    double[] originalMasses = masses.clone();
    double[] originalMomenta = momenta.clone();

    double[] first = relax(FlowRegime.BUBBLE, masses, momenta, 0.5, dragLaw);
    double[] second = relax(FlowRegime.BUBBLE, masses, momenta, 0.5, dragLaw);

    assertArrayEquals(first, second, 0.0);
    assertArrayEquals(originalMasses, masses, 0.0);
    assertArrayEquals(originalMomenta, momenta, 0.0);
  }

  @Test
  void bubbleClassificationsAgreeAndNonlinearRefinementConverges() {
    InterfacialFriction dragLaw = new InterfacialFriction();
    double[] masses = { 1.0, 4.0, 0.0 };
    double[] momenta = { 1.0, 0.0, 0.0 };
    double totalTime = 0.2;
    double[] bubble = relax(FlowRegime.BUBBLE, masses, momenta, totalTime, dragLaw);
    double[] dispersed = relax(FlowRegime.DISPERSED_BUBBLE, masses, momenta, totalTime, dragLaw);
    double[] reference = repeatedRelax(masses, momenta, totalTime, 256, dragLaw);
    double[] twoSteps = repeatedRelax(masses, momenta, totalTime, 2, dragLaw);

    assertArrayEquals(bubble, dispersed, 0.0);
    assertTrue(slipError(masses, twoSteps, reference) < slipError(masses, bubble, reference));
  }

  @Test
  void tracePositivePhaseRemainsFiniteWithoutAHiddenMassFloor() {
    InterfacialFriction dragLaw = new InterfacialFriction();
    double[] masses = { 1.0e-12, 4.0, 0.0 };
    double[] momenta = { 1.0e-12, 0.0, 0.0 };

    double[] result = relax(FlowRegime.BUBBLE, masses, momenta, 1.0, dragLaw);

    assertTrue(Double.isFinite(result[0]));
    assertTrue(Double.isFinite(result[1]));
    assertEquals(sum(momenta), sum(result), 1.0e-20);
  }

  private double[] repeatedRelax(double[] masses, double[] initialMomenta, double totalTime, int steps,
      InterfacialFriction dragLaw) {
    double[] result = initialMomenta.clone();
    for (int step = 0; step < steps; step++) {
      result = relax(FlowRegime.BUBBLE, masses, result, totalTime / steps, dragLaw);
    }
    return result;
  }

  private double slipError(double[] masses, double[] actual, double[] expected) {
    double actualSlip = actual[0] / masses[0] - actual[1] / masses[1];
    double expectedSlip = expected[0] / masses[0] - expected[1] / masses[1];
    return Math.abs(actualSlip - expectedSlip);
  }

  private double[] relax(FlowRegime regime, double[] masses, double[] momenta, double timeStep,
      InterfacialFriction dragLaw) {
    return DispersedBubbleDragSolver.relax(regime, masses, momenta, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY,
        LIQUID_VISCOSITY, LIQUID_HOLDUP, DIAMETER, SURFACE_TENSION, timeStep, dragLaw);
  }

  private double kineticEnergy(double[] masses, double[] momenta) {
    double energy = 0.0;
    for (int phase = 0; phase < masses.length; phase++) {
      if (masses[phase] > 0.0) {
        energy += 0.5 * momenta[phase] * momenta[phase] / masses[phase];
      }
    }
    return energy;
  }

  private double sum(double[] values) {
    double result = 0.0;
    for (double value : values) {
      result += value;
    }
    return result;
  }
}
