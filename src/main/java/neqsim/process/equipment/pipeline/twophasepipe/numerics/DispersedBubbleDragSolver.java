package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.util.Arrays;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;

/**
 * Pure local implicit solver for Schiller-Naumann dispersed-bubble momentum exchange.
 *
 * <p>
 * Oil and water are treated as one constrained continuous-liquid momentum during gas-liquid drag. The liquid impulse is
 * distributed by active phase mass, so the oil-water velocity difference is unchanged. This preserves total
 * gas-oil-water momentum and makes kinetic energy non-increasing without inventing an absent phase. Oil-water
 * interfacial shear remains a separate closure.
 * </p>
 */
public final class DispersedBubbleDragSolver {
  private static final int BISECTION_ITERATIONS = 100;

  private DispersedBubbleDragSolver() {
  }

  /**
   * Advance gas, oil, and water momenta through one backward-Euler bubble-drag step.
   *
   * @param flowRegime current gas-liquid flow regime
   * @param masses gas, oil, and water masses per pipe length in kg/m
   * @param momenta gas, oil, and water momenta per pipe length in kg/s
   * @param gasDensity gas density in kg/m3
   * @param liquidDensity continuous-liquid density in kg/m3
   * @param gasViscosity gas viscosity in Pa s
   * @param liquidViscosity continuous-liquid viscosity in Pa s
   * @param liquidHoldup total liquid holdup
   * @param diameter pipe internal diameter in m
   * @param surfaceTension gas-liquid surface tension in N/m
   * @param timeStep time step in s
   * @param dragLaw interfacial drag closure
   * @return gas, oil, and water momenta after the local drag step in kg/s
   * @throws IllegalArgumentException if an input is invalid
   * @throws IllegalStateException if the closure is non-finite or the step is not dissipative
   */
  public static double[] relax(FlowRegime flowRegime, double[] masses, double[] momenta, double gasDensity,
      double liquidDensity, double gasViscosity, double liquidViscosity, double liquidHoldup, double diameter,
      double surfaceTension, double timeStep, InterfacialFriction dragLaw) {
    validateInputs(masses, momenta, gasDensity, liquidDensity, gasViscosity, liquidViscosity, liquidHoldup, diameter,
        surfaceTension, timeStep, dragLaw);
    double[] result = momenta.clone();
    if (!isDispersedBubbleRegime(flowRegime) || timeStep == 0.0) {
      return result;
    }
    for (int phase = 0; phase < masses.length; phase++) {
      if (masses[phase] == 0.0) {
        result[phase] = 0.0;
      }
    }

    double gasMass = masses[0];
    double oilMass = masses[1];
    double waterMass = masses[2];
    double liquidMass = oilMass + waterMass;
    if (gasMass == 0.0 || liquidMass == 0.0) {
      return result;
    }

    double gasMomentum = momenta[0];
    double liquidMomentum = (oilMass > 0.0 ? momenta[1] : 0.0) + (waterMass > 0.0 ? momenta[2] : 0.0);
    double gasVelocity = gasMomentum / gasMass;
    double liquidVelocity = liquidMomentum / liquidMass;
    double initialSlip = gasVelocity - liquidVelocity;
    double initialSlipMagnitude = Math.abs(initialSlip);
    if (initialSlipMagnitude < 1.0e-10) {
      return result;
    }

    double inverseMassSum = 1.0 / gasMass + 1.0 / liquidMass;
    double slipSign = Math.signum(initialSlip);
    double lowerSlip = 0.0;
    double upperSlip = initialSlipMagnitude;
    for (int iteration = 0; iteration < BISECTION_ITERATIONS; iteration++) {
      double trialSlip = 0.5 * (lowerSlip + upperSlip);
      double force = bubbleForceMagnitude(dragLaw, flowRegime, slipSign * trialSlip, gasDensity, liquidDensity,
          gasViscosity, liquidViscosity, liquidHoldup, diameter, surfaceTension);
      double residual = trialSlip + timeStep * inverseMassSum * force - initialSlipMagnitude;
      if (residual > 0.0) {
        upperSlip = trialSlip;
      } else {
        lowerSlip = trialSlip;
      }
    }
    double relaxedSlip = 0.5 * (lowerSlip + upperSlip);
    double relaxedForce = bubbleForceMagnitude(dragLaw, flowRegime, slipSign * relaxedSlip, gasDensity, liquidDensity,
        gasViscosity, liquidViscosity, liquidHoldup, diameter, surfaceTension);
    double pairCoefficient = relaxedSlip > 0.0 ? relaxedForce / relaxedSlip : 0.0;

    double[] pseudoMasses = { gasMass, liquidMass };
    double[] pseudoMomenta = { gasMomentum, liquidMomentum };
    double[][] pairCoefficients = { { 0.0, pairCoefficient }, { pairCoefficient, 0.0 } };
    double[] relaxedPseudoMomenta = StiffInterphaseMomentumSolver.solve(pseudoMasses, pseudoMomenta, pairCoefficients,
        timeStep);

    result[0] = relaxedPseudoMomenta[0];
    double liquidVelocityChange = relaxedPseudoMomenta[1] / liquidMass - liquidVelocity;
    if (oilMass > 0.0) {
      result[1] = momenta[1] + oilMass * liquidVelocityChange;
    } else {
      result[1] = 0.0;
    }
    if (waterMass > 0.0) {
      result[2] = momenta[2] + waterMass * liquidVelocityChange;
    } else {
      result[2] = 0.0;
    }
    double liquidMomentumResidual = relaxedPseudoMomenta[1] - result[1] - result[2];
    if (oilMass >= waterMass && oilMass > 0.0) {
      result[1] += liquidMomentumResidual;
    } else if (waterMass > 0.0) {
      result[2] += liquidMomentumResidual;
    }

    double initialEnergy = kineticEnergy(masses, momenta);
    double finalEnergy = kineticEnergy(masses, result);
    double tolerance = 1.0e-10 * Math.max(1.0, initialEnergy);
    if (finalEnergy > initialEnergy + tolerance) {
      throw new IllegalStateException("Dispersed-bubble drag increased kinetic energy from " + initialEnergy + " to "
          + finalEnergy + "; masses=" + Arrays.toString(masses) + "; momenta=" + Arrays.toString(momenta) + "; result="
          + Arrays.toString(result));
    }
    return result;
  }

  private static boolean isDispersedBubbleRegime(FlowRegime flowRegime) {
    return flowRegime == FlowRegime.BUBBLE || flowRegime == FlowRegime.DISPERSED_BUBBLE;
  }

  private static double bubbleForceMagnitude(InterfacialFriction dragLaw, FlowRegime flowRegime, double slipVelocity,
      double gasDensity, double liquidDensity, double gasViscosity, double liquidViscosity, double liquidHoldup,
      double diameter, double surfaceTension) {
    double force = dragLaw.calcCorrectedBubbleDragForce(flowRegime, slipVelocity, 0.0, gasDensity, liquidDensity,
        gasViscosity, liquidViscosity, liquidHoldup, diameter, surfaceTension);
    if (!Double.isFinite(force)) {
      throw new IllegalStateException("Dispersed-bubble drag force must be finite");
    }
    return Math.abs(force);
  }

  private static void validateInputs(double[] masses, double[] momenta, double gasDensity, double liquidDensity,
      double gasViscosity, double liquidViscosity, double liquidHoldup, double diameter, double surfaceTension,
      double timeStep, InterfacialFriction dragLaw) {
    if (masses == null || momenta == null || masses.length != 3 || momenta.length != 3) {
      throw new IllegalArgumentException("Gas, oil, and water mass and momentum arrays are required");
    }
    for (int phase = 0; phase < masses.length; phase++) {
      if (!Double.isFinite(masses[phase]) || masses[phase] < 0.0 || !Double.isFinite(momenta[phase])) {
        throw new IllegalArgumentException("Phase masses and momenta must be finite and masses non-negative");
      }
    }
    if (!Double.isFinite(gasDensity) || gasDensity <= 0.0 || !Double.isFinite(liquidDensity)
        || liquidDensity <= gasDensity || !Double.isFinite(gasViscosity) || gasViscosity <= 0.0
        || !Double.isFinite(liquidViscosity) || liquidViscosity <= 0.0 || !Double.isFinite(liquidHoldup)
        || liquidHoldup < 0.0 || liquidHoldup > 1.0 || !Double.isFinite(diameter) || diameter <= 0.0
        || !Double.isFinite(surfaceTension) || surfaceTension < 0.0 || !Double.isFinite(timeStep) || timeStep < 0.0
        || dragLaw == null) {
      throw new IllegalArgumentException("Dispersed-bubble drag inputs are outside their valid range");
    }
  }

  private static double kineticEnergy(double[] masses, double[] momenta) {
    double energy = 0.0;
    for (int phase = 0; phase < masses.length; phase++) {
      if (masses[phase] > 0.0) {
        energy += 0.5 * momenta[phase] * momenta[phase] / masses[phase];
      }
    }
    return energy;
  }
}
