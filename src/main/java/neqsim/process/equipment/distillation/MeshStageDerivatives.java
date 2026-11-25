package neqsim.process.equipment.distillation;

import org.ejml.simple.SimpleMatrix;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Analytic derivatives of the equilibrium-stage quantities used by the simultaneous-correction column solver.
 *
 * <p>
 * The stage variables are the liquid component flows {@code l_m}, the temperature {@code T} and the total vapor flow
 * {@code V}. The K-values are defined implicitly by {@code ln K_i = ln phiL_i(T, x) - ln phiV_i(T, y)} with
 * {@code x_i = l_i / L} and {@code y_i = K_i x_i / sum(K x)}. Differentiating this fixed point gives the linear system
 * {@code (I + AV diag(y)) dlnK = (BL - BV) dT + (AL - AV diag(K / s)) dl / L}, where {@code A} and {@code B} are the
 * composition and temperature derivatives of {@code ln phi} of the liquid (L) and vapor (V) phases and {@code s} is
 * {@code sum(K x)}. The terms that come from normalizing the compositions vanish because {@code ln phi} is homogeneous
 * of degree zero in the mole numbers.
 * </p>
 *
 * <p>
 * Enthalpy derivatives use the heat capacity and the partial molar enthalpies
 * {@code hbar_i = h_i^ig(T) - R T^2 dln(phi_i)/dT}. One evaluation of each phase with composition derivatives replaces
 * the {@code C + 2} perturbed stage evaluations of a finite-difference Jacobian, and the result is exact to rounding
 * instead of to the perturbation size.
 * </p>
 *
 * <p>
 * Derivative arrays are indexed by stage variable: {@code 0..C-1} are the liquid component flows, {@code C} is the
 * temperature and {@code C + 1} is the total vapor flow.
 * </p>
 *
 * @author NeqSim / AI-assisted
 * @version 1.0
 */
final class MeshStageDerivatives {
  /** Largest relative mismatch accepted in the internal consistency checks of the phase derivatives. */
  private static final double CONSISTENCY_TOLERANCE = 1.0e-6;

  /** Number of components. */
  private final int componentCount;

  /** Derivatives of the vapor component flows {@code v_i = K_i x_i V}, indexed [component][variable]. */
  private final double[][] vaporFlowDerivatives;

  /** Derivatives of the summation function {@code sum(K x) - 1}. */
  private final double[] summationDerivatives;

  /** Derivatives of the liquid enthalpy flow {@code L hL}. */
  private final double[] liquidEnthalpyFlowDerivatives;

  /** Derivatives of the vapor enthalpy flow {@code V hV}. */
  private final double[] vaporEnthalpyFlowDerivatives;

  /** Derivatives of the molar liquid enthalpy {@code hL}. */
  private final double[] liquidMolarEnthalpyDerivatives;

  /** Derivatives of the molar vapor enthalpy {@code hV}. */
  private final double[] vaporMolarEnthalpyDerivatives;

  /** Liquid-phase derivatives of ln(phi_i) with respect to mole number k on a one-mole basis. */
  private final double[][] liquidCompositionDerivatives;

  /** Vapor-phase derivatives of ln(phi_i) with respect to mole number k on a one-mole basis. */
  private final double[][] vaporCompositionDerivatives;

  /** Liquid-phase temperature derivatives of ln(phi_i). */
  private final double[] liquidTemperatureDerivatives;

  /** Vapor-phase temperature derivatives of ln(phi_i). */
  private final double[] vaporTemperatureDerivatives;

  /** Liquid partial molar enthalpies in J/mol. */
  private final double[] liquidPartialMolarEnthalpies;

  /** Vapor partial molar enthalpies in J/mol. */
  private final double[] vaporPartialMolarEnthalpies;

  /** Liquid molar heat capacity in J/(mol K). */
  private double liquidHeatCapacity;

  /** Vapor molar heat capacity in J/(mol K). */
  private double vaporHeatCapacity;

  /** Liquid molar enthalpy in J/mol reported by the phase. */
  private double liquidMolarEnthalpy;

  /** Vapor molar enthalpy in J/mol reported by the phase. */
  private double vaporMolarEnthalpy;

  /**
   * Create the derivative storage for one stage.
   *
   * @param componentCount number of components
   */
  MeshStageDerivatives(int componentCount) {
    this.componentCount = componentCount;
    int variables = componentCount + 2;
    vaporFlowDerivatives = new double[componentCount][variables];
    summationDerivatives = new double[variables];
    liquidEnthalpyFlowDerivatives = new double[variables];
    vaporEnthalpyFlowDerivatives = new double[variables];
    liquidMolarEnthalpyDerivatives = new double[variables];
    vaporMolarEnthalpyDerivatives = new double[variables];
    liquidCompositionDerivatives = new double[componentCount][componentCount];
    vaporCompositionDerivatives = new double[componentCount][componentCount];
    liquidTemperatureDerivatives = new double[componentCount];
    vaporTemperatureDerivatives = new double[componentCount];
    liquidPartialMolarEnthalpies = new double[componentCount];
    vaporPartialMolarEnthalpies = new double[componentCount];
  }

  /**
   * Read the fugacity and enthalpy derivatives of one phase.
   *
   * <p>
   * The phase must hold one mole at the stage temperature, pressure and composition and must have been initialized with
   * composition derivatives (initialization level 3). The method rejects the phase when the derivatives are not finite,
   * when the composition derivatives are not homogeneous of degree zero, or when the mole-fraction weighted partial
   * molar enthalpies do not reproduce the phase enthalpy. A rejected phase means the thermodynamic model does not
   * supply the derivatives this class needs, and the caller must use finite differences.
   * </p>
   *
   * @param phase one-mole phase initialized with composition derivatives
   * @param vapor true to store the values as the vapor phase, false for the liquid phase
   * @return true when the derivatives are finite and consistent
   */
  boolean loadPhase(PhaseInterface phase, boolean vapor) {
    double[][] compositionDerivatives = vapor ? vaporCompositionDerivatives : liquidCompositionDerivatives;
    double[] temperatureDerivatives = vapor ? vaporTemperatureDerivatives : liquidTemperatureDerivatives;
    double[] partialMolarEnthalpies = vapor ? vaporPartialMolarEnthalpies : liquidPartialMolarEnthalpies;
    double moles = phase.getNumberOfMolesInPhase();
    if (!(moles > 0.0)) {
      return false;
    }
    double temperature = phase.getTemperature();
    double molarEnthalpy = phase.getEnthalpy() / moles;
    double heatCapacity = phase.getCp() / moles;
    double enthalpySum = 0.0;
    double enthalpyMagnitude = 0.0;
    for (int i = 0; i < componentCount; i++) {
      ComponentInterface component = phase.getComponent(i);
      double temperatureDerivative = component.getdfugdt();
      temperatureDerivatives[i] = temperatureDerivative;
      partialMolarEnthalpies[i] = component.getHID(temperature)
          - ThermodynamicConstantsInterface.R * temperature * temperature * temperatureDerivative;
      double homogeneity = 0.0;
      double magnitude = 0.0;
      for (int k = 0; k < componentCount; k++) {
        double derivative = component.getdfugdn(k);
        compositionDerivatives[i][k] = derivative;
        double term = derivative * phase.getComponent(k).getx();
        homogeneity += term;
        magnitude += Math.abs(term);
      }
      if (!Double.isFinite(homogeneity) || !Double.isFinite(partialMolarEnthalpies[i])
          || Math.abs(homogeneity) > CONSISTENCY_TOLERANCE * Math.max(magnitude, 1.0)) {
        return false;
      }
      double weighted = component.getx() * partialMolarEnthalpies[i];
      enthalpySum += weighted;
      enthalpyMagnitude += Math.abs(weighted);
    }
    if (!Double.isFinite(molarEnthalpy) || !Double.isFinite(heatCapacity) || Math.abs(enthalpySum
        - molarEnthalpy) > CONSISTENCY_TOLERANCE * Math.max(enthalpyMagnitude, Math.abs(molarEnthalpy)) + 1.0e-6) {
      return false;
    }
    if (vapor) {
      vaporMolarEnthalpy = molarEnthalpy;
      vaporHeatCapacity = heatCapacity;
    } else {
      liquidMolarEnthalpy = molarEnthalpy;
      liquidHeatCapacity = heatCapacity;
    }
    return true;
  }

  /**
   * Compute the stage derivatives from the loaded phase derivatives.
   *
   * @param liquidFlows liquid component flows in the solver flow unit
   * @param vaporFlow total vapor flow in the solver flow unit
   * @param kValues equilibrium K-values of the stage at the loaded phase states
   * @return true when the implicit K-value system was solved and every derivative is finite
   */
  boolean compute(double[] liquidFlows, double vaporFlow, double[] kValues) {
    int c = componentCount;
    double liquidFlow = 0.0;
    for (int i = 0; i < c; i++) {
      liquidFlow += liquidFlows[i];
    }
    if (!(liquidFlow > 0.0)) {
      return false;
    }
    double[] x = new double[c];
    double[] y = new double[c];
    double sumKx = 0.0;
    for (int i = 0; i < c; i++) {
      x[i] = liquidFlows[i] / liquidFlow;
      sumKx += kValues[i] * x[i];
    }
    if (!(sumKx > 0.0) || !Double.isFinite(sumKx)) {
      return false;
    }
    for (int i = 0; i < c; i++) {
      y[i] = kValues[i] * x[i] / sumKx;
    }

    SimpleMatrix system = new SimpleMatrix(c, c);
    SimpleMatrix rightHandSide = new SimpleMatrix(c, c + 1);
    for (int i = 0; i < c; i++) {
      for (int k = 0; k < c; k++) {
        system.set(i, k, (i == k ? 1.0 : 0.0) + vaporCompositionDerivatives[i][k] * y[k]);
        rightHandSide.set(i, k,
            (liquidCompositionDerivatives[i][k] - vaporCompositionDerivatives[i][k] * kValues[k] / sumKx) / liquidFlow);
      }
      rightHandSide.set(i, c, liquidTemperatureDerivatives[i] - vaporTemperatureDerivatives[i]);
    }
    SimpleMatrix logKDerivatives;
    try {
      logKDerivatives = system.solve(rightHandSide);
    } catch (RuntimeException exception) {
      return false;
    }

    java.util.Arrays.fill(summationDerivatives, 0.0);
    java.util.Arrays.fill(vaporMolarEnthalpyDerivatives, 0.0);
    for (int i = 0; i < c; i++) {
      double kx = kValues[i] * x[i];
      double vaporComponentFlow = kx * vaporFlow;
      double enthalpyWeight = y[i] * (vaporPartialMolarEnthalpies[i] - vaporMolarEnthalpy);
      for (int m = 0; m <= c; m++) {
        double logKDerivative = logKDerivatives.get(i, m);
        if (!Double.isFinite(logKDerivative)) {
          return false;
        }
        vaporFlowDerivatives[i][m] = vaporComponentFlow * logKDerivative;
        summationDerivatives[m] += kx * logKDerivative;
        vaporMolarEnthalpyDerivatives[m] += enthalpyWeight * logKDerivative;
      }
      for (int m = 0; m < c; m++) {
        vaporFlowDerivatives[i][m] -= vaporComponentFlow / liquidFlow;
      }
      vaporFlowDerivatives[i][i] += kValues[i] * vaporFlow / liquidFlow;
      vaporFlowDerivatives[i][c + 1] = kx;
    }
    for (int m = 0; m < c; m++) {
      summationDerivatives[m] += (kValues[m] - sumKx) / liquidFlow;
      vaporMolarEnthalpyDerivatives[m] += (vaporPartialMolarEnthalpies[m] - vaporMolarEnthalpy) * kValues[m]
          / (sumKx * liquidFlow);
      liquidEnthalpyFlowDerivatives[m] = liquidPartialMolarEnthalpies[m];
      liquidMolarEnthalpyDerivatives[m] = (liquidPartialMolarEnthalpies[m] - liquidMolarEnthalpy) / liquidFlow;
      vaporEnthalpyFlowDerivatives[m] = vaporFlow * vaporMolarEnthalpyDerivatives[m];
    }
    vaporMolarEnthalpyDerivatives[c] += vaporHeatCapacity;
    liquidEnthalpyFlowDerivatives[c] = liquidFlow * liquidHeatCapacity;
    liquidMolarEnthalpyDerivatives[c] = liquidHeatCapacity;
    vaporEnthalpyFlowDerivatives[c] = vaporFlow * vaporMolarEnthalpyDerivatives[c];
    summationDerivatives[c + 1] = 0.0;
    liquidEnthalpyFlowDerivatives[c + 1] = 0.0;
    liquidMolarEnthalpyDerivatives[c + 1] = 0.0;
    vaporMolarEnthalpyDerivatives[c + 1] = 0.0;
    vaporEnthalpyFlowDerivatives[c + 1] = vaporMolarEnthalpy;
    return true;
  }

  /**
   * Get the derivative of a vapor component flow.
   *
   * @param component component index
   * @param variable stage variable index
   * @return derivative of {@code v_component} with respect to the stage variable
   */
  double getVaporFlowDerivative(int component, int variable) {
    return vaporFlowDerivatives[component][variable];
  }

  /**
   * Get the derivative of the summation function {@code sum(K x) - 1}.
   *
   * @param variable stage variable index
   * @return derivative with respect to the stage variable
   */
  double getSummationDerivative(int variable) {
    return summationDerivatives[variable];
  }

  /**
   * Get the derivative of the liquid enthalpy flow {@code L hL}.
   *
   * @param variable stage variable index
   * @return derivative with respect to the stage variable
   */
  double getLiquidEnthalpyFlowDerivative(int variable) {
    return liquidEnthalpyFlowDerivatives[variable];
  }

  /**
   * Get the derivative of the vapor enthalpy flow {@code V hV}.
   *
   * @param variable stage variable index
   * @return derivative with respect to the stage variable
   */
  double getVaporEnthalpyFlowDerivative(int variable) {
    return vaporEnthalpyFlowDerivatives[variable];
  }

  /**
   * Get the derivative of the molar liquid enthalpy.
   *
   * @param variable stage variable index
   * @return derivative with respect to the stage variable
   */
  double getLiquidMolarEnthalpyDerivative(int variable) {
    return liquidMolarEnthalpyDerivatives[variable];
  }

  /**
   * Get the derivative of the molar vapor enthalpy.
   *
   * @param variable stage variable index
   * @return derivative with respect to the stage variable
   */
  double getVaporMolarEnthalpyDerivative(int variable) {
    return vaporMolarEnthalpyDerivatives[variable];
  }
}
