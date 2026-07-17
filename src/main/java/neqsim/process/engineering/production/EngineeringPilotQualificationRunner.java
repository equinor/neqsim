package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Quantified comparison of one NeqSim engineering pilot against an independently reviewed reference package. */
public final class EngineeringPilotQualificationRunner {
  private EngineeringPilotQualificationRunner() {
  }

  /** One scalar comparison; material comparisons block pilot acceptance when outside tolerance. */
  public static final class Comparison implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final double referenceValue;
    private final double simulatedValue;
    private final String unit;
    private final double absoluteTolerance;
    private final double relativeTolerance;
    private final boolean material;

    public Comparison(String name, double referenceValue, double simulatedValue, String unit, double absoluteTolerance,
        double relativeTolerance, boolean material) {
      this.name = text(name, "name");
      this.unit = text(unit, "unit");
      if (!Double.isFinite(referenceValue) || !Double.isFinite(simulatedValue) || !Double.isFinite(absoluteTolerance)
          || !Double.isFinite(relativeTolerance) || absoluteTolerance < 0.0 || relativeTolerance < 0.0) {
        throw new IllegalArgumentException("Pilot values and tolerances must be finite and non-negative");
      }
      this.referenceValue = referenceValue;
      this.simulatedValue = simulatedValue;
      this.absoluteTolerance = absoluteTolerance;
      this.relativeTolerance = relativeTolerance;
      this.material = material;
    }

    public boolean isPassed() {
      double error = Math.abs(simulatedValue - referenceValue);
      return error <= absoluteTolerance || error / Math.max(Math.abs(referenceValue), 1.0e-12) <= relativeTolerance;
    }

    Map<String, Object> toMap() {
      double absoluteError = Math.abs(simulatedValue - referenceValue);
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("name", name);
      result.put("referenceValue", Double.valueOf(referenceValue));
      result.put("simulatedValue", Double.valueOf(simulatedValue));
      result.put("unit", unit);
      result.put("absoluteTolerance", Double.valueOf(absoluteTolerance));
      result.put("relativeTolerance", Double.valueOf(relativeTolerance));
      result.put("absoluteError", Double.valueOf(absoluteError));
      result.put("relativeError", Double.valueOf(absoluteError / Math.max(Math.abs(referenceValue), 1.0e-12)));
      result.put("material", Boolean.valueOf(material));
      result.put("passed", Boolean.valueOf(isPassed()));
      return result;
    }
  }

  /** Pilot run result and readiness evidence derived from actual comparisons. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final EngineeringPilotProjectEvidence evidence;
    private final List<Comparison> comparisons;

    Result(EngineeringPilotProjectEvidence evidence, List<Comparison> comparisons) {
      this.evidence = evidence;
      this.comparisons = Collections.unmodifiableList(new ArrayList<Comparison>(comparisons));
    }

    public EngineeringPilotProjectEvidence getEvidence() {
      return evidence;
    }

    public List<Comparison> getComparisons() {
      return comparisons;
    }

    public boolean isAccepted() {
      return evidence.isAccepted();
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
      for (Comparison comparison : comparisons) {
        rows.add(comparison.toMap());
      }
      result.put("evidence", evidence.toMap());
      result.put("comparisons", rows);
      return result;
    }
  }

  public static Result run(String projectId, EngineeringPilotProjectEvidence.Scope scope, String referencePackage,
      List<Comparison> comparisons, String independentReviewer, String acceptanceRecord) {
    if (comparisons == null || comparisons.isEmpty()) {
      throw new IllegalArgumentException("At least one controlled pilot comparison is required");
    }
    int materialDiscrepancies = 0;
    for (Comparison comparison : comparisons) {
      if (comparison == null) {
        throw new IllegalArgumentException("Pilot comparisons must not contain null");
      }
      if (comparison.material && !comparison.isPassed()) {
        materialDiscrepancies++;
      }
    }
    EngineeringPilotProjectEvidence evidence = new EngineeringPilotProjectEvidence(projectId, scope, referencePackage,
        comparisons.size(), materialDiscrepancies, independentReviewer, acceptanceRecord);
    return new Result(evidence, comparisons);
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
