package neqsim.process.engineering.numerics;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Machine-readable convergence, closure, residual, and sensitivity evidence for one process state. */
public final class EngineeringNumericalHealthReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Overall fail-closed report decision. */
  public enum Status {
    HEALTHY, INCOMPLETE, FAILED
  }

  /** Availability and tolerance decision for one closure observation. */
  public enum ObservationStatus {
    PASS, FAIL, NOT_APPLICABLE, NOT_AVAILABLE
  }

  /** One mass- or energy-closure observation. */
  public static final class ClosureObservation implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String type;
    private final String scope;
    private final Double absoluteError;
    private final Double relativeErrorPercent;
    private final String unit;
    private final double tolerancePercent;
    private final ObservationStatus status;
    private final String message;

    ClosureObservation(String type, String scope, Double absoluteError, Double relativeErrorPercent, String unit,
        double tolerancePercent, ObservationStatus status, String message) {
      this.type = type;
      this.scope = scope;
      this.absoluteError = absoluteError;
      this.relativeErrorPercent = relativeErrorPercent;
      this.unit = unit;
      this.tolerancePercent = tolerancePercent;
      this.status = status;
      this.message = message;
    }

    public String getType() {
      return type;
    }

    public String getScope() {
      return scope;
    }

    public ObservationStatus getStatus() {
      return status;
    }

    public String getMessage() {
      return message;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("type", type);
      result.put("scope", scope);
      result.put("absoluteError", absoluteError);
      result.put("relativeErrorPercent", relativeErrorPercent);
      result.put("unit", unit);
      result.put("tolerancePercent", Double.valueOf(tolerancePercent));
      result.put("status", status.name());
      result.put("message", message);
      return result;
    }
  }

  /** One solver or equation residual normalized against its declared tolerance. */
  public static final class ResidualObservation implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final double value;
    private final double tolerance;
    private final double normalizedResidual;
    private final String unit;
    private final ObservationStatus status;

    ResidualObservation(String name, double value, double tolerance, String unit, double maximumNormalizedResidual) {
      this.name = name;
      this.value = value;
      this.tolerance = tolerance;
      this.unit = unit;
      normalizedResidual = Double.isFinite(value) ? Math.abs(value) / tolerance : Double.NaN;
      status = !Double.isFinite(normalizedResidual) ? ObservationStatus.NOT_AVAILABLE
          : normalizedResidual <= maximumNormalizedResidual ? ObservationStatus.PASS : ObservationStatus.FAIL;
    }

    public ObservationStatus getStatus() {
      return status;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("name", name);
      result.put("value", finite(value));
      result.put("tolerance", Double.valueOf(tolerance));
      result.put("normalizedResidual", finite(normalizedResidual));
      result.put("unit", unit);
      result.put("status", status.name());
      return result;
    }
  }

  /** Numerical rank and scale diagnostics for a supplied sensitivity Jacobian. */
  public static final class JacobianHealth implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final int rows;
    private final int columns;
    private final int numericalRank;
    private final int expectedRank;
    private final Double coefficientScaleRatio;
    private final boolean finite;
    private final ObservationStatus status;

    JacobianHealth(double[][] matrix, double maximumScaleRatio) {
      rows = matrix.length;
      columns = rows == 0 ? 0 : matrix[0].length;
      expectedRank = Math.min(rows, columns);
      finite = isFiniteRectangular(matrix, columns);
      numericalRank = finite ? rank(matrix) : 0;
      coefficientScaleRatio = finite(matrixScaleRatio(matrix));
      status = !finite || coefficientScaleRatio == null ? ObservationStatus.NOT_AVAILABLE
          : numericalRank < expectedRank || coefficientScaleRatio.doubleValue() > maximumScaleRatio
              ? ObservationStatus.FAIL
              : ObservationStatus.PASS;
    }

    public ObservationStatus getStatus() {
      return status;
    }

    public int getNumericalRank() {
      return numericalRank;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("rows", Integer.valueOf(rows));
      result.put("columns", Integer.valueOf(columns));
      result.put("numericalRank", Integer.valueOf(numericalRank));
      result.put("expectedRank", Integer.valueOf(expectedRank));
      result.put("finite", Boolean.valueOf(finite));
      result.put("coefficientScaleRatio", coefficientScaleRatio);
      result.put("status", status.name());
      result.put("interpretation",
          "Coefficient scale ratio is a screening diagnostic and is not a matrix condition number");
      return result;
    }

    private static boolean isFiniteRectangular(double[][] matrix, int columns) {
      if (matrix.length == 0 || columns == 0) {
        return false;
      }
      for (double[] row : matrix) {
        if (row == null || row.length != columns) {
          return false;
        }
        for (double value : row) {
          if (!Double.isFinite(value)) {
            return false;
          }
        }
      }
      return true;
    }

    private static double matrixScaleRatio(double[][] matrix) {
      double maximum = 0.0;
      double minimum = Double.POSITIVE_INFINITY;
      for (double[] row : matrix) {
        for (double value : row) {
          double absolute = Math.abs(value);
          maximum = Math.max(maximum, absolute);
          if (absolute > 0.0) {
            minimum = Math.min(minimum, absolute);
          }
        }
      }
      return maximum == 0.0 || !Double.isFinite(minimum) ? Double.NaN : maximum / minimum;
    }

    private static int rank(double[][] source) {
      int rowCount = source.length;
      int columnCount = source[0].length;
      double[][] values = new double[rowCount][columnCount];
      double maximum = 0.0;
      for (int row = 0; row < rowCount; row++) {
        System.arraycopy(source[row], 0, values[row], 0, columnCount);
        for (int column = 0; column < columnCount; column++) {
          maximum = Math.max(maximum, Math.abs(values[row][column]));
        }
      }
      double tolerance = maximum * Math.max(rowCount, columnCount) * 1.0e-12;
      int pivotRow = 0;
      for (int column = 0; column < columnCount && pivotRow < rowCount; column++) {
        int candidate = pivotRow;
        for (int row = pivotRow + 1; row < rowCount; row++) {
          if (Math.abs(values[row][column]) > Math.abs(values[candidate][column])) {
            candidate = row;
          }
        }
        if (Math.abs(values[candidate][column]) <= tolerance) {
          continue;
        }
        double[] swap = values[pivotRow];
        values[pivotRow] = values[candidate];
        values[candidate] = swap;
        for (int row = pivotRow + 1; row < rowCount; row++) {
          double factor = values[row][column] / values[pivotRow][column];
          for (int col = column; col < columnCount; col++) {
            values[row][col] -= factor * values[pivotRow][col];
          }
        }
        pivotRow++;
      }
      return pivotRow;
    }
  }

  private final String processName;
  private final boolean converged;
  private final String convergenceDiagnostics;
  private final EngineeringNumericalHealthCriteria criteria;
  private final List<ClosureObservation> massClosure;
  private final List<ClosureObservation> energyClosure;
  private final List<ResidualObservation> equationResiduals;
  private final JacobianHealth jacobianHealth;
  private final List<String> findings;
  private final Status status;

  EngineeringNumericalHealthReport(String processName, boolean converged, String convergenceDiagnostics,
      EngineeringNumericalHealthCriteria criteria, List<ClosureObservation> massClosure,
      List<ClosureObservation> energyClosure, List<ResidualObservation> equationResiduals,
      JacobianHealth jacobianHealth) {
    this.processName = processName;
    this.converged = converged;
    this.convergenceDiagnostics = convergenceDiagnostics;
    this.criteria = criteria;
    this.massClosure = new ArrayList<ClosureObservation>(massClosure);
    this.energyClosure = new ArrayList<ClosureObservation>(energyClosure);
    this.equationResiduals = new ArrayList<ResidualObservation>(equationResiduals);
    this.jacobianHealth = jacobianHealth;
    findings = findings();
    status = decideStatus();
  }

  public Status getStatus() {
    return status;
  }

  public boolean isAcceptableForEngineering() {
    return status == Status.HEALTHY;
  }

  public List<ClosureObservation> getMassClosure() {
    return Collections.unmodifiableList(massClosure);
  }

  public List<ClosureObservation> getEnergyClosure() {
    return Collections.unmodifiableList(energyClosure);
  }

  public List<ResidualObservation> getEquationResiduals() {
    return Collections.unmodifiableList(equationResiduals);
  }

  public JacobianHealth getJacobianHealth() {
    return jacobianHealth;
  }

  public List<String> getFindings() {
    return Collections.unmodifiableList(findings);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", EngineeringSchemaCatalog.NUMERICAL_HEALTH);
    result.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.NUMERICAL_HEALTH));
    result.put("processName", processName);
    result.put("status", status.name());
    result.put("acceptableForEngineering", Boolean.valueOf(isAcceptableForEngineering()));
    result.put("converged", Boolean.valueOf(converged));
    result.put("convergenceDiagnostics", convergenceDiagnostics);
    result.put("criteria", criteria.toMap());
    result.put("massClosure", closureMaps(massClosure));
    result.put("energyClosure", closureMaps(energyClosure));
    List<Map<String, Object>> residualMaps = new ArrayList<Map<String, Object>>();
    for (ResidualObservation observation : equationResiduals) {
      residualMaps.add(observation.toMap());
    }
    result.put("equationResiduals", residualMaps);
    result.put("jacobianHealth", jacobianHealth == null ? null : jacobianHealth.toMap());
    result.put("findings", new ArrayList<String>(findings));
    result.put("engineeringReviewRequired", Boolean.TRUE);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private Status decideStatus() {
    if (!converged || contains(massClosure, ObservationStatus.FAIL) || contains(energyClosure, ObservationStatus.FAIL)
        || containsResidual(ObservationStatus.FAIL)
        || jacobianHealth != null && jacobianHealth.getStatus() == ObservationStatus.FAIL) {
      return Status.FAILED;
    }
    if (!containsApplicableMassClosure() || contains(massClosure, ObservationStatus.NOT_AVAILABLE)
        || criteria.isEnergyClosureRequired() && energyClosure.isEmpty()
        || criteria.isEnergyClosureRequired() && contains(energyClosure, ObservationStatus.NOT_AVAILABLE)
        || criteria.areEquationResidualsRequired() && equationResiduals.isEmpty()
        || criteria.areEquationResidualsRequired() && containsResidual(ObservationStatus.NOT_AVAILABLE)
        || criteria.isSensitivityEvidenceRequired() && jacobianHealth == null
        || jacobianHealth != null && jacobianHealth.getStatus() == ObservationStatus.NOT_AVAILABLE) {
      return Status.INCOMPLETE;
    }
    return Status.HEALTHY;
  }

  private List<String> findings() {
    List<String> result = new ArrayList<String>();
    if (!converged) {
      result.add("Process simulation did not report convergence");
    }
    if (!containsApplicableMassClosure()) {
      result.add("No applicable unit-operation mass closure was available");
    }
    addObservationFindings(result, massClosure);
    addObservationFindings(result, energyClosure);
    if (criteria.isEnergyClosureRequired() && energyClosure.isEmpty()) {
      result.add("Required energy closure evidence was not supplied");
    }
    if (criteria.areEquationResidualsRequired() && equationResiduals.isEmpty()) {
      result.add("Required equation residual evidence was not supplied");
    }
    if (criteria.isSensitivityEvidenceRequired() && jacobianHealth == null) {
      result.add("Required sensitivity Jacobian evidence was not supplied");
    }
    return result;
  }

  private boolean containsApplicableMassClosure() {
    return contains(massClosure, ObservationStatus.PASS) || contains(massClosure, ObservationStatus.FAIL);
  }

  private boolean containsResidual(ObservationStatus expected) {
    for (ResidualObservation observation : equationResiduals) {
      if (observation.getStatus() == expected) {
        return true;
      }
    }
    return false;
  }

  private static boolean contains(List<ClosureObservation> observations, ObservationStatus expected) {
    for (ClosureObservation observation : observations) {
      if (observation.getStatus() == expected) {
        return true;
      }
    }
    return false;
  }

  private static void addObservationFindings(List<String> findings, List<ClosureObservation> observations) {
    for (ClosureObservation observation : observations) {
      if (observation.getStatus() == ObservationStatus.FAIL
          || observation.getStatus() == ObservationStatus.NOT_AVAILABLE) {
        findings
            .add(observation.getType() + " closure for " + observation.getScope() + ": " + observation.getMessage());
      }
    }
  }

  private static List<Map<String, Object>> closureMaps(List<ClosureObservation> observations) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (ClosureObservation observation : observations) {
      result.add(observation.toMap());
    }
    return result;
  }

  private static Double finite(double value) {
    return Double.isFinite(value) ? Double.valueOf(value) : null;
  }
}
