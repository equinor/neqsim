package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests for the conservative local implicit momentum solver. */
class StiffInterphaseMomentumSolverTest {

  @Test
  void twoPhaseSolveIsConservativeDissipativeAndStableAcrossStiffnessRange() {
    double[] masses = { 2.0, 3.0 };
    double[] momenta = { 8.0, -3.0 };
    double pairCoefficient = 7.5;
    double[][] coefficients = { { 0.0, pairCoefficient }, { pairCoefficient, 0.0 } };
    double initialSlip = momenta[0] / masses[0] - momenta[1] / masses[1];
    double relaxationTime = 1.0 / (pairCoefficient * (1.0 / masses[0] + 1.0 / masses[1]));
    double[] stiffnessRatios = { 1.0e-3, 0.1, 1.0, 10.0, 1.0e3 };

    for (double stiffnessRatio : stiffnessRatios) {
      double[] result = StiffInterphaseMomentumSolver.solve(masses, momenta, coefficients,
          stiffnessRatio * relaxationTime);
      double resultSlip = result[0] / masses[0] - result[1] / masses[1];

      assertEquals(initialSlip / (1.0 + stiffnessRatio), resultSlip, Math.abs(initialSlip) * 2.0e-12);
      assertEquals(sum(momenta), sum(result), 2.0e-14);
      assertTrue(kineticEnergy(masses, result) <= kineticEnergy(masses, momenta) + 1.0e-13);
      assertTrue(resultSlip > 0.0);
    }
  }

  @Test
  void threePhaseSolvePreservesMomentumAndCannotCreateKineticEnergy() {
    double[] masses = { 1.0, 2.0, 3.0 };
    double[] momenta = { 1.0, 2.8, 0.0 };
    double[][] coefficients = { { 0.0, 4.0, 2.0 }, { 4.0, 0.0, 1.5 }, { 2.0, 1.5, 0.0 } };

    double[] result = StiffInterphaseMomentumSolver.solve(masses, momenta, coefficients, 100.0);

    assertEquals(sum(momenta), sum(result), 2.0e-14);
    assertTrue(kineticEnergy(masses, result) <= kineticEnergy(masses, momenta) + 1.0e-13);
    double centreVelocity = sum(momenta) / sum(masses);
    for (int phase = 0; phase < masses.length; phase++) {
      assertEquals(centreVelocity, result[phase] / masses[phase], 5.0e-3);
    }
  }

  @Test
  void absentPhaseIsEliminatedWithoutMassFloor() {
    double[] masses = { 1.0, 2.0, 0.0 };
    double[] momenta = { 1.0, -2.0, 7.0e-9 };
    double[][] coefficients = { { 0.0, 3.0, 20.0 }, { 3.0, 0.0, 10.0 }, { 20.0, 10.0, 0.0 } };

    double[] result = StiffInterphaseMomentumSolver.solve(masses, momenta, coefficients, 2.0);

    assertEquals(0.0, result[2], 0.0);
    assertEquals(momenta[0] + momenta[1], sum(result), 2.0e-14);
    assertTrue(kineticEnergy(masses, result) <= kineticEnergy(masses, momenta) + 1.0e-13);
  }

  @Test
  void solveIsSignSymmetricAndBackwardEulerConvergesAtFirstOrder() {
    double[] masses = { 2.0, 3.0 };
    double[] momenta = { 8.0, -3.0 };
    double[][] coefficients = { { 0.0, 7.5 }, { 7.5, 0.0 } };
    double[] reverseMomenta = { -momenta[0], -momenta[1] };
    double[] forward = StiffInterphaseMomentumSolver.solve(masses, momenta, coefficients, 0.2);
    double[] reverse = StiffInterphaseMomentumSolver.solve(masses, reverseMomenta, coefficients, 0.2);

    assertEquals(-forward[0], reverse[0], 1.0e-14);
    assertEquals(-forward[1], reverse[1], 1.0e-14);

    double relaxationRate = coefficients[0][1] * (1.0 / masses[0] + 1.0 / masses[1]);
    double totalTime = 0.4 / relaxationRate;
    double initialSlip = momenta[0] / masses[0] - momenta[1] / masses[1];
    double exactSlip = initialSlip * Math.exp(-relaxationRate * totalTime);
    double coarseSlip = repeatedSlip(masses, momenta, coefficients, totalTime, 1);
    double fineSlip = repeatedSlip(masses, momenta, coefficients, totalTime, 2);
    double coarseError = Math.abs(coarseSlip - exactSlip);
    double fineError = Math.abs(fineSlip - exactSlip);

    assertTrue(fineError < coarseError);
    assertTrue(coarseError / fineError > 1.5);
  }

  private double repeatedSlip(double[] masses, double[] initialMomenta, double[][] coefficients, double totalTime,
      int steps) {
    double[] momenta = initialMomenta.clone();
    for (int step = 0; step < steps; step++) {
      momenta = StiffInterphaseMomentumSolver.solve(masses, momenta, coefficients, totalTime / steps);
    }
    return momenta[0] / masses[0] - momenta[1] / masses[1];
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
