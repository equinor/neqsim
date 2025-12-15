package neqsim.thermo.util.derivatives;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Container for Jacobian matrix of fugacity coefficients.
 *
 * <p>
 * Stores derivatives of log fugacity coefficients (ln φ) for all components with respect to:
 * </p>
 * <ul>
 * <li>Temperature: ∂(ln φ_i)/∂T</li>
 * <li>Pressure: ∂(ln φ_i)/∂P</li>
 * <li>Composition: ∂(ln φ_i)/∂n_j (moles) or ∂(ln φ_i)/∂x_j (mole fractions)</li>
 * </ul>
 *
 * <p>
 * These derivatives are fundamental for:
 * </p>
 * <ul>
 * <li>Flash calculation convergence (Newton methods)</li>
 * <li>Implicit differentiation through equilibrium</li>
 * <li>Stability analysis</li>
 * </ul>
 *
 * @author ESOL
 * @since 3.0
 */
public class FugacityJacobian implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Phase index (0 = liquid-like, 1 = vapor-like). */
  private final int phaseIndex;

  /** Phase type description. */
  private final String phaseType;

  /** Number of components. */
  private final int numberOfComponents;

  /** Log fugacity coefficients ln(φ_i). */
  private final double[] lnPhi;

  /** Derivatives w.r.t. temperature: ∂(ln φ_i)/∂T [1/K]. */
  private final double[] dlnPhidT;

  /** Derivatives w.r.t. pressure: ∂(ln φ_i)/∂P [1/bar]. */
  private final double[] dlnPhidP;

  /**
   * Derivatives w.r.t. moles: ∂(ln φ_i)/∂n_j [1/mol]. Matrix is [component][d/dn_component].
   */
  private final double[][] dlnPhidn;

  /** Component names. */
  private final String[] componentNames;

  /**
   * Constructor for FugacityJacobian.
   *
   * @param phaseIndex phase index
   * @param phaseType phase type description
   * @param lnPhi log fugacity coefficients
   * @param dlnPhidT derivatives w.r.t. temperature
   * @param dlnPhidP derivatives w.r.t. pressure
   * @param dlnPhidn derivatives w.r.t. moles (Jacobian matrix)
   * @param componentNames component names
   */
  public FugacityJacobian(int phaseIndex, String phaseType, double[] lnPhi, double[] dlnPhidT,
      double[] dlnPhidP, double[][] dlnPhidn, String[] componentNames) {
    this.phaseIndex = phaseIndex;
    this.phaseType = phaseType;
    this.numberOfComponents = lnPhi.length;
    this.lnPhi = Arrays.copyOf(lnPhi, lnPhi.length);
    this.dlnPhidT = Arrays.copyOf(dlnPhidT, dlnPhidT.length);
    this.dlnPhidP = Arrays.copyOf(dlnPhidP, dlnPhidP.length);
    this.dlnPhidn = new double[numberOfComponents][numberOfComponents];
    for (int i = 0; i < numberOfComponents; i++) {
      this.dlnPhidn[i] = Arrays.copyOf(dlnPhidn[i], numberOfComponents);
    }
    this.componentNames =
        componentNames != null ? Arrays.copyOf(componentNames, componentNames.length)
            : new String[numberOfComponents];
  }

  /**
   * Get phase index.
   *
   * @return phase index
   */
  public int getPhaseIndex() {
    return phaseIndex;
  }

  /**
   * Get phase type description.
   *
   * @return phase type
   */
  public String getPhaseType() {
    return phaseType;
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
   * Get log fugacity coefficient for a component.
   *
   * @param componentIndex component index
   * @return ln(φ_i)
   */
  public double getLnPhi(int componentIndex) {
    return lnPhi[componentIndex];
  }

  /**
   * Get all log fugacity coefficients.
   *
   * @return array of ln(φ_i)
   */
  public double[] getLnPhi() {
    return Arrays.copyOf(lnPhi, lnPhi.length);
  }

  /**
   * Get derivative of ln(φ_i) w.r.t. temperature.
   *
   * @param componentIndex component index
   * @return ∂(ln φ_i)/∂T [1/K]
   */
  public double getDlnPhidT(int componentIndex) {
    return dlnPhidT[componentIndex];
  }

  /**
   * Get all temperature derivatives.
   *
   * @return array of ∂(ln φ_i)/∂T
   */
  public double[] getDlnPhidT() {
    return Arrays.copyOf(dlnPhidT, dlnPhidT.length);
  }

  /**
   * Get derivative of ln(φ_i) w.r.t. pressure.
   *
   * @param componentIndex component index
   * @return ∂(ln φ_i)/∂P [1/bar]
   */
  public double getDlnPhidP(int componentIndex) {
    return dlnPhidP[componentIndex];
  }

  /**
   * Get all pressure derivatives.
   *
   * @return array of ∂(ln φ_i)/∂P
   */
  public double[] getDlnPhidP() {
    return Arrays.copyOf(dlnPhidP, dlnPhidP.length);
  }

  /**
   * Get derivative of ln(φ_i) w.r.t. moles of component j.
   *
   * @param i component index for fugacity
   * @param j component index for differentiation
   * @return ∂(ln φ_i)/∂n_j [1/mol]
   */
  public double getDlnPhidn(int i, int j) {
    return dlnPhidn[i][j];
  }

  /**
   * Get row of composition Jacobian (derivatives of ln(φ_i) w.r.t. all component moles).
   *
   * @param componentIndex component index
   * @return array of ∂(ln φ_i)/∂n_j for all j
   */
  public double[] getDlnPhidnRow(int componentIndex) {
    return Arrays.copyOf(dlnPhidn[componentIndex], numberOfComponents);
  }

  /**
   * Get full composition Jacobian matrix.
   *
   * @return matrix [i][j] = ∂(ln φ_i)/∂n_j
   */
  public double[][] getDlnPhidn() {
    double[][] result = new double[numberOfComponents][numberOfComponents];
    for (int i = 0; i < numberOfComponents; i++) {
      result[i] = Arrays.copyOf(dlnPhidn[i], numberOfComponents);
    }
    return result;
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
   * Check thermodynamic consistency (symmetry of composition Jacobian).
   *
   * <p>
   * For a thermodynamically consistent model: n_i * ∂(ln φ_i)/∂n_j = n_j * ∂(ln φ_j)/∂n_i
   * </p>
   *
   * @param moles mole numbers
   * @param tolerance relative tolerance for symmetry check
   * @return true if consistent within tolerance
   */
  public boolean checkSymmetry(double[] moles, double tolerance) {
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = i + 1; j < numberOfComponents; j++) {
        double lhs = moles[i] * dlnPhidn[i][j];
        double rhs = moles[j] * dlnPhidn[j][i];
        double diff = Math.abs(lhs - rhs);
        double scale = Math.max(Math.abs(lhs), Math.abs(rhs));
        if (scale > 1e-10 && diff / scale > tolerance) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Compute directional derivative of ln(φ_i) along a perturbation.
   *
   * @param componentIndex component index
   * @param deltaT temperature perturbation
   * @param deltaP pressure perturbation
   * @param deltaN mole perturbations
   * @return directional derivative of ln(φ_i)
   */
  public double directionalDerivative(int componentIndex, double deltaT, double deltaP,
      double[] deltaN) {
    double result = dlnPhidT[componentIndex] * deltaT + dlnPhidP[componentIndex] * deltaP;
    if (deltaN != null) {
      for (int j = 0; j < Math.min(numberOfComponents, deltaN.length); j++) {
        result += dlnPhidn[componentIndex][j] * deltaN[j];
      }
    }
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FugacityJacobian[phase=").append(phaseType);
    sb.append(", nc=").append(numberOfComponents).append("]\n");
    sb.append("  Components: ").append(Arrays.toString(componentNames)).append("\n");
    sb.append("  ln(φ): ").append(Arrays.toString(lnPhi)).append("\n");
    sb.append("  d(ln φ)/dT: ").append(Arrays.toString(dlnPhidT)).append("\n");
    sb.append("  d(ln φ)/dP: ").append(Arrays.toString(dlnPhidP)).append("\n");
    sb.append("  d(ln φ)/dn matrix:\n");
    for (int i = 0; i < numberOfComponents; i++) {
      sb.append("    [").append(i).append("]: ").append(Arrays.toString(dlnPhidn[i])).append("\n");
    }
    return sb.toString();
  }
}
