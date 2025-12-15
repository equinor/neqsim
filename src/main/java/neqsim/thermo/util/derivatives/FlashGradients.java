package neqsim.thermo.util.derivatives;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Container for gradients of flash calculation results with respect to input parameters.
 *
 * <p>
 * Stores sensitivities of flash outputs (K-values, phase fraction, phase compositions) with
 * respect to:
 * </p>
 * <ul>
 * <li>Temperature (T)</li>
 * <li>Pressure (P)</li>
 * <li>Feed composition (z)</li>
 * </ul>
 *
 * <p>
 * These gradients are computed using the implicit function theorem, which provides exact
 * derivatives without differentiating through the iterative flash solver.
 * </p>
 *
 * <h2>Mathematical Background</h2>
 * <p>
 * At phase equilibrium, the residual equations F(y; θ) = 0 are satisfied, where y represents the
 * solution variables (K-values, β) and θ represents parameters (T, P, z).
 * </p>
 * <p>
 * By the implicit function theorem:
 * </p>
 * 
 * <pre>
 * dy/dθ = -(∂F/∂y)^(-1) * (∂F/∂θ)
 * </pre>
 *
 * @author ESOL
 * @since 3.0
 */
public class FlashGradients implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Number of components. */
  private final int numberOfComponents;

  /** Number of phases. */
  private final int numberOfPhases;

  /** Current K-values (y_i/x_i). */
  private final double[] kValues;

  /** Current vapor phase fraction (β). */
  private final double beta;

  /** Derivatives of K-values w.r.t. temperature: dK_i/dT [1/K]. */
  private final double[] dKdT;

  /** Derivatives of K-values w.r.t. pressure: dK_i/dP [1/bar]. */
  private final double[] dKdP;

  /** Derivatives of K-values w.r.t. feed composition: dK_i/dz_j. */
  private final double[][] dKdz;

  /** Derivative of vapor fraction w.r.t. temperature: dβ/dT [1/K]. */
  private final double dBetadT;

  /** Derivative of vapor fraction w.r.t. pressure: dβ/dP [1/bar]. */
  private final double dBetadP;

  /** Derivatives of vapor fraction w.r.t. feed composition: dβ/dz_i. */
  private final double[] dBetadz;

  /** Component names. */
  private final String[] componentNames;

  /** Whether gradients were successfully computed. */
  private final boolean valid;

  /** Error message if gradient computation failed. */
  private final String errorMessage;

  /**
   * Constructor for successful gradient computation.
   *
   * @param kValues current K-values
   * @param beta current vapor fraction
   * @param dKdT K-value temperature derivatives
   * @param dKdP K-value pressure derivatives
   * @param dKdz K-value composition derivatives
   * @param dBetadT vapor fraction temperature derivative
   * @param dBetadP vapor fraction pressure derivative
   * @param dBetadz vapor fraction composition derivatives
   * @param componentNames component names
   */
  public FlashGradients(double[] kValues, double beta, double[] dKdT, double[] dKdP,
      double[][] dKdz, double dBetadT, double dBetadP, double[] dBetadz, String[] componentNames) {
    this.numberOfComponents = kValues.length;
    this.numberOfPhases = 2;
    this.kValues = Arrays.copyOf(kValues, kValues.length);
    this.beta = beta;
    this.dKdT = Arrays.copyOf(dKdT, dKdT.length);
    this.dKdP = Arrays.copyOf(dKdP, dKdP.length);
    this.dKdz = new double[numberOfComponents][numberOfComponents];
    for (int i = 0; i < numberOfComponents; i++) {
      this.dKdz[i] = Arrays.copyOf(dKdz[i], numberOfComponents);
    }
    this.dBetadT = dBetadT;
    this.dBetadP = dBetadP;
    this.dBetadz = Arrays.copyOf(dBetadz, dBetadz.length);
    this.componentNames = componentNames != null ? Arrays.copyOf(componentNames,
        componentNames.length) : new String[numberOfComponents];
    this.valid = true;
    this.errorMessage = null;
  }

  /**
   * Constructor for failed gradient computation.
   *
   * @param numberOfComponents number of components
   * @param errorMessage error description
   */
  public FlashGradients(int numberOfComponents, String errorMessage) {
    this.numberOfComponents = numberOfComponents;
    this.numberOfPhases = 2;
    this.kValues = new double[numberOfComponents];
    this.beta = Double.NaN;
    this.dKdT = new double[numberOfComponents];
    this.dKdP = new double[numberOfComponents];
    this.dKdz = new double[numberOfComponents][numberOfComponents];
    this.dBetadT = Double.NaN;
    this.dBetadP = Double.NaN;
    this.dBetadz = new double[numberOfComponents];
    this.componentNames = new String[numberOfComponents];
    this.valid = false;
    this.errorMessage = errorMessage;
  }

  /**
   * Check if gradients are valid.
   *
   * @return true if gradients were successfully computed
   */
  public boolean isValid() {
    return valid;
  }

  /**
   * Get error message if computation failed.
   *
   * @return error message or null if successful
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Get number of components.
   *
   * @return number of components
   */
  public int getNumberOfComponents() {
    return numberOfComponents;
  }

  /**
   * Get number of phases.
   *
   * @return number of phases
   */
  public int getNumberOfPhases() {
    return numberOfPhases;
  }

  /**
   * Get current K-values.
   *
   * @return array of K_i = y_i/x_i
   */
  public double[] getKValues() {
    return Arrays.copyOf(kValues, kValues.length);
  }

  /**
   * Get K-value for a component.
   *
   * @param componentIndex component index
   * @return K_i
   */
  public double getKValue(int componentIndex) {
    return kValues[componentIndex];
  }

  /**
   * Get current vapor phase fraction.
   *
   * @return β (vapor mole fraction)
   */
  public double getBeta() {
    return beta;
  }

  /**
   * Get derivative of K-value w.r.t. temperature.
   *
   * @param componentIndex component index
   * @return dK_i/dT [1/K]
   */
  public double getDKdT(int componentIndex) {
    return dKdT[componentIndex];
  }

  /**
   * Get all K-value temperature derivatives.
   *
   * @return array of dK_i/dT
   */
  public double[] getDKdT() {
    return Arrays.copyOf(dKdT, dKdT.length);
  }

  /**
   * Get derivative of K-value w.r.t. pressure.
   *
   * @param componentIndex component index
   * @return dK_i/dP [1/bar]
   */
  public double getDKdP(int componentIndex) {
    return dKdP[componentIndex];
  }

  /**
   * Get all K-value pressure derivatives.
   *
   * @return array of dK_i/dP
   */
  public double[] getDKdP() {
    return Arrays.copyOf(dKdP, dKdP.length);
  }

  /**
   * Get derivative of K-value w.r.t. feed composition.
   *
   * @param i K-value component index
   * @param j feed composition component index
   * @return dK_i/dz_j
   */
  public double getDKdz(int i, int j) {
    return dKdz[i][j];
  }

  /**
   * Get K-value composition derivatives for a component.
   *
   * @param componentIndex component index
   * @return array of dK_i/dz_j for all j
   */
  public double[] getDKdzRow(int componentIndex) {
    return Arrays.copyOf(dKdz[componentIndex], numberOfComponents);
  }

  /**
   * Get full K-value composition Jacobian.
   *
   * @return matrix [i][j] = dK_i/dz_j
   */
  public double[][] getDKdz() {
    double[][] result = new double[numberOfComponents][numberOfComponents];
    for (int i = 0; i < numberOfComponents; i++) {
      result[i] = Arrays.copyOf(dKdz[i], numberOfComponents);
    }
    return result;
  }

  /**
   * Get derivative of vapor fraction w.r.t. temperature.
   *
   * @return dβ/dT [1/K]
   */
  public double getDBetadT() {
    return dBetadT;
  }

  /**
   * Get derivative of vapor fraction w.r.t. pressure.
   *
   * @return dβ/dP [1/bar]
   */
  public double getDBetadP() {
    return dBetadP;
  }

  /**
   * Get derivatives of vapor fraction w.r.t. feed composition.
   *
   * @return array of dβ/dz_i
   */
  public double[] getDBetadz() {
    return Arrays.copyOf(dBetadz, dBetadz.length);
  }

  /**
   * Get derivative of vapor fraction w.r.t. specific component in feed.
   *
   * @param componentIndex component index
   * @return dβ/dz_i
   */
  public double getDBetadz(int componentIndex) {
    return dBetadz[componentIndex];
  }

  /**
   * Get component names.
   *
   * @return array of component names
   */
  public String[] getComponentNames() {
    return Arrays.copyOf(componentNames, componentNames.length);
  }

  /**
   * Compute derivative of liquid phase composition x_i w.r.t. temperature.
   *
   * <p>
   * Using: x_i = z_i / (1 + β*(K_i - 1))
   * </p>
   *
   * @param componentIndex component index
   * @param z feed composition
   * @return dx_i/dT
   */
  public double getDxdT(int componentIndex, double[] z) {
    double K = kValues[componentIndex];
    double denom = 1.0 + beta * (K - 1.0);
    double x = z[componentIndex] / denom;
    // dx/dT = -x/denom * (dβ/dT*(K-1) + β*dK/dT)
    return -x / denom * (dBetadT * (K - 1.0) + beta * dKdT[componentIndex]);
  }

  /**
   * Compute derivative of vapor phase composition y_i w.r.t. temperature.
   *
   * <p>
   * Using: y_i = K_i * x_i
   * </p>
   *
   * @param componentIndex component index
   * @param z feed composition
   * @return dy_i/dT
   */
  public double getDydT(int componentIndex, double[] z) {
    double K = kValues[componentIndex];
    double denom = 1.0 + beta * (K - 1.0);
    double x = z[componentIndex] / denom;
    double dxdT = getDxdT(componentIndex, z);
    // dy/dT = dK/dT * x + K * dx/dT
    return dKdT[componentIndex] * x + K * dxdT;
  }

  /**
   * Get all gradients as a flat array for ML frameworks.
   *
   * <p>
   * Layout: [dK_0/dT, ..., dK_n/dT, dK_0/dP, ..., dK_n/dP, dβ/dT, dβ/dP, dK_0/dz_0, ...]
   * </p>
   *
   * @return flattened gradient array
   */
  public double[] toFlatArray() {
    int size = 2 * numberOfComponents + 2 + numberOfComponents * numberOfComponents
        + numberOfComponents;
    double[] result = new double[size];
    int idx = 0;

    // dK/dT
    for (int i = 0; i < numberOfComponents; i++) {
      result[idx++] = dKdT[i];
    }
    // dK/dP
    for (int i = 0; i < numberOfComponents; i++) {
      result[idx++] = dKdP[i];
    }
    // dβ/dT, dβ/dP
    result[idx++] = dBetadT;
    result[idx++] = dBetadP;
    // dK/dz (flattened)
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        result[idx++] = dKdz[i][j];
      }
    }
    // dβ/dz
    for (int i = 0; i < numberOfComponents; i++) {
      result[idx++] = dBetadz[i];
    }

    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FlashGradients[nc=").append(numberOfComponents);
    sb.append(", valid=").append(valid).append("]\n");
    if (!valid) {
      sb.append("  Error: ").append(errorMessage).append("\n");
      return sb.toString();
    }
    sb.append("  K-values: ").append(Arrays.toString(kValues)).append("\n");
    sb.append("  β = ").append(beta).append("\n");
    sb.append("  dK/dT: ").append(Arrays.toString(dKdT)).append("\n");
    sb.append("  dK/dP: ").append(Arrays.toString(dKdP)).append("\n");
    sb.append("  dβ/dT = ").append(dBetadT).append("\n");
    sb.append("  dβ/dP = ").append(dBetadP).append("\n");
    sb.append("  dβ/dz: ").append(Arrays.toString(dBetadz)).append("\n");
    return sb.toString();
  }
}
