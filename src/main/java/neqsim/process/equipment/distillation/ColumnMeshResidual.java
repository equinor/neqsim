package neqsim.process.equipment.distillation;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable residual vector for distillation column MESH diagnostics.
 *
 * <p>
 * Residuals are scaled to dimensionless values where possible. The object stores equation type,
 * tray index, and optional component labels so tests and future solvers can inspect the structure
 * without reparsing the column state.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
final class ColumnMeshResidual implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Residual values. */
  private final double[] values;
  /** Equation type per residual. */
  private final ColumnMeshEquationType[] equationTypes;
  /** Tray index per residual; -1 for column-level equations. */
  private final int[] trayIndices;
  /** Component label per residual; null for non-component equations. */
  private final String[] componentNames;

  /**
   * Create a residual vector.
   *
   * @param values residual values
   * @param equationTypes equation type per residual
   * @param trayIndices tray index per residual
   * @param componentNames component label per residual
   */
  ColumnMeshResidual(double[] values, ColumnMeshEquationType[] equationTypes, int[] trayIndices,
      String[] componentNames) {
    if (values.length != equationTypes.length || values.length != trayIndices.length
        || values.length != componentNames.length) {
      throw new IllegalArgumentException("Residual metadata arrays must have equal length");
    }
    this.values = values.clone();
    this.equationTypes = equationTypes.clone();
    this.trayIndices = trayIndices.clone();
    this.componentNames = componentNames.clone();
  }

  /**
   * Get a copy of the residual vector.
   *
   * @return residual values
   */
  double[] getValues() {
    return values.clone();
  }

  /**
   * Get equation types for the residual entries.
   *
   * @return equation type metadata
   */
  ColumnMeshEquationType[] getEquationTypes() {
    return equationTypes.clone();
  }

  /**
   * Get tray indices for the residual entries.
   *
   * @return tray index metadata
   */
  int[] getTrayIndices() {
    return trayIndices.clone();
  }

  /**
   * Get component names for the residual entries.
   *
   * @return component label metadata
   */
  String[] getComponentNames() {
    return componentNames.clone();
  }

  /**
   * Get the number of residual equations.
   *
   * @return residual count
   */
  int size() {
    return values.length;
  }

  /**
   * Check whether all residuals are finite numbers.
   *
   * @return {@code true} if every residual is finite
   */
  boolean isFinite() {
    for (int i = 0; i < values.length; i++) {
      if (!Double.isFinite(values[i])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Get the infinity norm of all residuals.
   *
   * @return maximum absolute residual
   */
  double getInfinityNorm() {
    double max = 0.0;
    for (int i = 0; i < values.length; i++) {
      max = Math.max(max, Math.abs(values[i]));
    }
    return max;
  }

  /**
   * Get the infinity norm for a subset of residuals.
   *
   * @param type equation type to include
   * @return maximum absolute residual for the requested type
   */
  double getInfinityNorm(ColumnMeshEquationType type) {
    double max = 0.0;
    for (int i = 0; i < values.length; i++) {
      if (equationTypes[i] == type) {
        max = Math.max(max, Math.abs(values[i]));
      }
    }
    return max;
  }

  /**
   * Get the Euclidean norm of all residuals.
   *
   * @return L2 norm
   */
  double getL2Norm() {
    double sumSquares = 0.0;
    for (int i = 0; i < values.length; i++) {
      sumSquares += values[i] * values[i];
    }
    return Math.sqrt(sumSquares);
  }

  /**
   * Count residuals of a given equation type.
   *
   * @param type equation type to count
   * @return number of residuals with the requested type
   */
  int count(ColumnMeshEquationType type) {
    int count = 0;
    for (int i = 0; i < equationTypes.length; i++) {
      if (equationTypes[i] == type) {
        count++;
      }
    }
    return count;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "ColumnMeshResidual[size=" + values.length + ", infinityNorm=" + getInfinityNorm()
        + ", values=" + Arrays.toString(values) + "]";
  }
}
