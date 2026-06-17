package neqsim.statistics.parameterfitting;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight serializable report for a completed parameter fitting study.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ParameterFittingReport implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private final String studyName;
  private final String dataSetName;
  private final ExperimentType experimentType;
  private final ObjectiveFunctionType objectiveFunctionType;
  private final String[] parameterNames;
  private final double[] fittedParameters;
  private final double rootMeanSquareError;
  private final double meanAbsoluteError;
  private final double weightedRootMeanSquareError;
  private final double validationRootMeanSquareError;
  private final double reducedChiSquare;
  private final boolean converged;
  private final String convergenceReason;

  /**
   * Creates a fitting report from a completed study.
   *
   * @param study completed parameter fitting study
   * @throws IllegalArgumentException if the study has not been run
   */
  public ParameterFittingReport(ParameterFittingStudy study) {
    if (study == null || study.getResult() == null) {
      throw new IllegalArgumentException("study must have a completed result");
    }
    ParameterFittingStudy.Result result = study.getResult();
    ParameterFittingSpec spec = study.getSpec();
    this.studyName = spec == null ? study.getDataSet().getName() : spec.getName();
    this.dataSetName = study.getDataSet().getName();
    this.experimentType = spec == null ? ExperimentType.GENERIC : spec.getExperimentType();
    this.objectiveFunctionType = result.getObjectiveFunctionType();
    this.parameterNames = result.getParameterNames();
    this.fittedParameters = result.getFittedParameters();
    this.rootMeanSquareError = result.getRootMeanSquareError();
    this.meanAbsoluteError = result.getMeanAbsoluteError();
    this.weightedRootMeanSquareError = result.getWeightedRootMeanSquareError();
    this.validationRootMeanSquareError = result.getValidationRootMeanSquareError();
    this.reducedChiSquare = result.getReducedChiSquare();
    this.converged = result.isConverged();
    this.convergenceReason = result.getOptimizerResult().getConvergenceReason().name();
  }

  /**
   * Serializes this report as JSON.
   *
   * @return pretty-printed JSON report
   * @throws IOException if serialization fails
   */
  public String toJson() throws IOException {
    return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(toMap());
  }

  /**
   * Renders this report as a compact Markdown summary.
   *
   * @return Markdown report
   */
  public String toMarkdown() {
    StringBuilder builder = new StringBuilder();
    builder.append("# ").append(studyName).append('\n');
    builder.append("\n");
    builder.append("Data set: ").append(dataSetName).append('\n');
    builder.append("\n");
    builder.append("Objective: ").append(objectiveFunctionType.name()).append('\n');
    builder.append("\n");
    builder.append("Converged: ").append(converged).append(" (").append(convergenceReason)
        .append(")\n");
    builder.append("\n");
    builder.append("| Parameter | Value |\n");
    builder.append("|-----------|-------|\n");
    for (int i = 0; i < parameterNames.length; i++) {
      builder.append("| ").append(parameterNames[i]).append(" | ").append(fittedParameters[i])
          .append(" |\n");
    }
    builder.append("\n");
    builder.append("RMSE: ").append(rootMeanSquareError).append('\n');
    builder.append("\n");
    builder.append("MAE: ").append(meanAbsoluteError).append('\n');
    builder.append("\n");
    builder.append("Weighted RMSE: ").append(weightedRootMeanSquareError).append('\n');
    builder.append("\n");
    builder.append("Reduced chi-square: ").append(reducedChiSquare).append('\n');
    return builder.toString();
  }

  /**
   * Converts the report to a map suitable for JSON serialization.
   *
   * @return report map
   */
  public Map<String, Object> toMap() {
    LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("studyName", studyName);
    map.put("dataSetName", dataSetName);
    map.put("experimentType", experimentType.name());
    map.put("objectiveFunctionType", objectiveFunctionType.name());
    map.put("parameterNames", parameterNames);
    map.put("fittedParameters", fittedParameters);
    map.put("rootMeanSquareError", Double.valueOf(rootMeanSquareError));
    map.put("meanAbsoluteError", Double.valueOf(meanAbsoluteError));
    map.put("weightedRootMeanSquareError", Double.valueOf(weightedRootMeanSquareError));
    map.put("validationRootMeanSquareError", Double.valueOf(validationRootMeanSquareError));
    map.put("reducedChiSquare", Double.valueOf(reducedChiSquare));
    map.put("converged", Boolean.valueOf(converged));
    map.put("convergenceReason", convergenceReason);
    return map;
  }
}
