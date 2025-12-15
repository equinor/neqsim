package neqsim.process.mpc;

import java.io.Serializable;

/**
 * Result of a linearization operation containing the gain matrix and metadata.
 *
 * <p>
 * The linearization result captures the sensitivity of controlled variables (CVs) to manipulated
 * variables (MVs) and disturbance variables (DVs) around an operating point. These sensitivities
 * form the gain matrices used by linear MPC algorithms.
 * </p>
 *
 * <p>
 * The gain matrix G[i][j] represents ∂CV[i]/∂MV[j], the change in CV[i] per unit change in MV[j].
 * Similarly, the disturbance gain matrix D[i][k] represents ∂CV[i]/∂DV[k].
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class LinearizationResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Gain matrix: gainMatrix[cv][mv] = ∂CV/∂MV. */
  private final double[][] gainMatrix;

  /** Disturbance gain matrix: disturbanceGainMatrix[cv][dv] = ∂CV/∂DV. */
  private final double[][] disturbanceGainMatrix;

  /** Operating point values for MVs. */
  private final double[] mvOperatingPoint;

  /** Operating point values for CVs. */
  private final double[] cvOperatingPoint;

  /** Operating point values for DVs. */
  private final double[] dvOperatingPoint;

  /** Names of MVs. */
  private final String[] mvNames;

  /** Names of CVs. */
  private final String[] cvNames;

  /** Names of DVs. */
  private final String[] dvNames;

  /** Perturbation size used for finite differences. */
  private final double perturbationSize;

  /** Time taken for linearization (milliseconds). */
  private final long computationTimeMs;

  /** Whether linearization was successful. */
  private final boolean successful;

  /** Error message if linearization failed. */
  private final String errorMessage;

  /**
   * Construct a successful linearization result.
   *
   * @param gainMatrix the MV-to-CV gain matrix
   * @param disturbanceGainMatrix the DV-to-CV gain matrix
   * @param mvOperatingPoint MV values at operating point
   * @param cvOperatingPoint CV values at operating point
   * @param dvOperatingPoint DV values at operating point
   * @param mvNames names of MVs
   * @param cvNames names of CVs
   * @param dvNames names of DVs
   * @param perturbationSize perturbation used
   * @param computationTimeMs time taken
   */
  public LinearizationResult(double[][] gainMatrix, double[][] disturbanceGainMatrix,
      double[] mvOperatingPoint, double[] cvOperatingPoint, double[] dvOperatingPoint,
      String[] mvNames, String[] cvNames, String[] dvNames, double perturbationSize,
      long computationTimeMs) {
    this.gainMatrix = deepCopy(gainMatrix);
    this.disturbanceGainMatrix = deepCopy(disturbanceGainMatrix);
    this.mvOperatingPoint = mvOperatingPoint != null ? mvOperatingPoint.clone() : new double[0];
    this.cvOperatingPoint = cvOperatingPoint != null ? cvOperatingPoint.clone() : new double[0];
    this.dvOperatingPoint = dvOperatingPoint != null ? dvOperatingPoint.clone() : new double[0];
    this.mvNames = mvNames != null ? mvNames.clone() : new String[0];
    this.cvNames = cvNames != null ? cvNames.clone() : new String[0];
    this.dvNames = dvNames != null ? dvNames.clone() : new String[0];
    this.perturbationSize = perturbationSize;
    this.computationTimeMs = computationTimeMs;
    this.successful = true;
    this.errorMessage = null;
  }

  /**
   * Construct a failed linearization result.
   *
   * @param errorMessage the error description
   * @param computationTimeMs time taken before failure
   */
  public LinearizationResult(String errorMessage, long computationTimeMs) {
    this.gainMatrix = new double[0][0];
    this.disturbanceGainMatrix = new double[0][0];
    this.mvOperatingPoint = new double[0];
    this.cvOperatingPoint = new double[0];
    this.dvOperatingPoint = new double[0];
    this.mvNames = new String[0];
    this.cvNames = new String[0];
    this.dvNames = new String[0];
    this.perturbationSize = 0;
    this.computationTimeMs = computationTimeMs;
    this.successful = false;
    this.errorMessage = errorMessage;
  }

  private double[][] deepCopy(double[][] matrix) {
    if (matrix == null) {
      return new double[0][0];
    }
    double[][] copy = new double[matrix.length][];
    for (int i = 0; i < matrix.length; i++) {
      copy[i] = matrix[i] != null ? matrix[i].clone() : new double[0];
    }
    return copy;
  }

  /**
   * Check if linearization was successful.
   *
   * @return true if successful
   */
  public boolean isSuccessful() {
    return successful;
  }

  /**
   * Get the error message if linearization failed.
   *
   * @return the error message, or null if successful
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Get the full gain matrix.
   *
   * @return copy of the gain matrix [numCV][numMV]
   */
  public double[][] getGainMatrix() {
    return deepCopy(gainMatrix);
  }

  /**
   * Get a single gain value.
   *
   * @param cvIndex index of the controlled variable
   * @param mvIndex index of the manipulated variable
   * @return the gain ∂CV/∂MV
   */
  public double getGain(int cvIndex, int mvIndex) {
    if (cvIndex < 0 || cvIndex >= gainMatrix.length) {
      return 0.0;
    }
    if (mvIndex < 0 || mvIndex >= gainMatrix[cvIndex].length) {
      return 0.0;
    }
    return gainMatrix[cvIndex][mvIndex];
  }

  /**
   * Get a gain by variable names.
   *
   * @param cvName name of the controlled variable
   * @param mvName name of the manipulated variable
   * @return the gain ∂CV/∂MV
   */
  public double getGain(String cvName, String mvName) {
    int cvIndex = findIndex(cvNames, cvName);
    int mvIndex = findIndex(mvNames, mvName);
    return getGain(cvIndex, mvIndex);
  }

  /**
   * Get the disturbance gain matrix.
   *
   * @return copy of the disturbance gain matrix [numCV][numDV]
   */
  public double[][] getDisturbanceGainMatrix() {
    return deepCopy(disturbanceGainMatrix);
  }

  /**
   * Get a single disturbance gain value.
   *
   * @param cvIndex index of the controlled variable
   * @param dvIndex index of the disturbance variable
   * @return the gain ∂CV/∂DV
   */
  public double getDisturbanceGain(int cvIndex, int dvIndex) {
    if (cvIndex < 0 || cvIndex >= disturbanceGainMatrix.length) {
      return 0.0;
    }
    if (dvIndex < 0 || dvIndex >= disturbanceGainMatrix[cvIndex].length) {
      return 0.0;
    }
    return disturbanceGainMatrix[cvIndex][dvIndex];
  }

  /**
   * Get the MV operating point values.
   *
   * @return copy of MV operating point
   */
  public double[] getMvOperatingPoint() {
    return mvOperatingPoint.clone();
  }

  /**
   * Get the CV operating point values.
   *
   * @return copy of CV operating point
   */
  public double[] getCvOperatingPoint() {
    return cvOperatingPoint.clone();
  }

  /**
   * Get the DV operating point values.
   *
   * @return copy of DV operating point
   */
  public double[] getDvOperatingPoint() {
    return dvOperatingPoint.clone();
  }

  /**
   * Get the MV names.
   *
   * @return copy of MV names
   */
  public String[] getMvNames() {
    return mvNames.clone();
  }

  /**
   * Get the CV names.
   *
   * @return copy of CV names
   */
  public String[] getCvNames() {
    return cvNames.clone();
  }

  /**
   * Get the DV names.
   *
   * @return copy of DV names
   */
  public String[] getDvNames() {
    return dvNames.clone();
  }

  /**
   * Get the number of MVs.
   *
   * @return number of manipulated variables
   */
  public int getNumMV() {
    return mvNames.length;
  }

  /**
   * Get the number of CVs.
   *
   * @return number of controlled variables
   */
  public int getNumCV() {
    return cvNames.length;
  }

  /**
   * Get the number of DVs.
   *
   * @return number of disturbance variables
   */
  public int getNumDV() {
    return dvNames.length;
  }

  /**
   * Get the perturbation size used.
   *
   * @return the perturbation as a fraction
   */
  public double getPerturbationSize() {
    return perturbationSize;
  }

  /**
   * Get the computation time.
   *
   * @return time in milliseconds
   */
  public long getComputationTimeMs() {
    return computationTimeMs;
  }

  /**
   * Get the gain matrix row for a specific CV.
   *
   * @param cvIndex index of the controlled variable
   * @return array of gains for this CV with respect to all MVs
   */
  public double[] getGainsForCV(int cvIndex) {
    if (cvIndex < 0 || cvIndex >= gainMatrix.length) {
      return new double[0];
    }
    return gainMatrix[cvIndex].clone();
  }

  /**
   * Get the gain matrix column for a specific MV.
   *
   * @param mvIndex index of the manipulated variable
   * @return array of gains for all CVs with respect to this MV
   */
  public double[] getGainsForMV(int mvIndex) {
    double[] column = new double[gainMatrix.length];
    for (int i = 0; i < gainMatrix.length; i++) {
      if (mvIndex >= 0 && mvIndex < gainMatrix[i].length) {
        column[i] = gainMatrix[i][mvIndex];
      }
    }
    return column;
  }

  private int findIndex(String[] names, String name) {
    if (name == null || names == null) {
      return -1;
    }
    for (int i = 0; i < names.length; i++) {
      if (name.equals(names[i])) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Format the gain matrix as a string table.
   *
   * @return formatted string representation
   */
  public String formatGainMatrix() {
    if (!successful) {
      return "Linearization failed: " + errorMessage;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Gain Matrix (∂CV/∂MV):\n");
    sb.append(String.format("%20s", ""));
    for (String mvName : mvNames) {
      sb.append(String.format(" %12s", mvName));
    }
    sb.append("\n");
    for (int i = 0; i < cvNames.length; i++) {
      sb.append(String.format("%20s", cvNames[i]));
      for (int j = 0; j < mvNames.length; j++) {
        sb.append(String.format(" %12.4f", gainMatrix[i][j]));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("LinearizationResult {\n");
    sb.append("  successful: ").append(successful).append("\n");
    if (!successful) {
      sb.append("  error: ").append(errorMessage).append("\n");
    } else {
      sb.append("  numMV: ").append(getNumMV()).append("\n");
      sb.append("  numCV: ").append(getNumCV()).append("\n");
      sb.append("  numDV: ").append(getNumDV()).append("\n");
      sb.append("  perturbation: ").append(perturbationSize).append("\n");
      sb.append("  computationTime: ").append(computationTimeMs).append(" ms\n");
    }
    sb.append("}");
    return sb.toString();
  }
}
