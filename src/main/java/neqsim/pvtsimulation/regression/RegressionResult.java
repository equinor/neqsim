package neqsim.pvtsimulation.regression;

import java.util.List;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;

/**
 * Result of a PVT regression optimization.
 *
 * <p>
 * Contains the tuned fluid, objective function values, optimized parameters, and uncertainty
 * analysis.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class RegressionResult {
  private SystemInterface tunedFluid;
  private Map<ExperimentType, Double> objectiveValues;
  private List<RegressionParameterConfig> parameterConfigs;
  private UncertaintyAnalysis uncertainty;
  private double[] optimizedParameters;
  private double finalChiSquare;

  /**
   * Create a regression result.
   *
   * @param tunedFluid the tuned fluid
   * @param objectiveValues objective function values by experiment type
   * @param parameterConfigs parameter configurations
   * @param uncertainty uncertainty analysis
   * @param optimizedParameters optimized parameter values
   * @param finalChiSquare final chi-square value
   */
  public RegressionResult(SystemInterface tunedFluid, Map<ExperimentType, Double> objectiveValues,
      List<RegressionParameterConfig> parameterConfigs, UncertaintyAnalysis uncertainty,
      double[] optimizedParameters, double finalChiSquare) {
    this.tunedFluid = tunedFluid;
    this.objectiveValues = objectiveValues;
    this.parameterConfigs = parameterConfigs;
    this.uncertainty = uncertainty;
    this.optimizedParameters = optimizedParameters;
    this.finalChiSquare = finalChiSquare;

    // Update parameter configs with optimized values
    for (int i = 0; i < parameterConfigs.size() && i < optimizedParameters.length; i++) {
      parameterConfigs.get(i).setOptimizedValue(optimizedParameters[i]);
    }
  }

  /**
   * Get the tuned fluid.
   *
   * @return tuned fluid
   */
  public SystemInterface getTunedFluid() {
    return tunedFluid;
  }

  /**
   * Get objective function values by experiment type.
   *
   * @return map of experiment type to objective value
   */
  public Map<ExperimentType, Double> getObjectiveValues() {
    return objectiveValues;
  }

  /**
   * Get total objective function value.
   *
   * @return total objective value
   */
  public double getTotalObjective() {
    return objectiveValues.values().stream().mapToDouble(Double::doubleValue).sum();
  }

  /**
   * Get objective value for a specific experiment type.
   *
   * @param type experiment type
   * @return objective value or 0 if not available
   */
  public double getObjectiveValue(ExperimentType type) {
    return objectiveValues.getOrDefault(type, 0.0);
  }

  /**
   * Get parameter configurations with optimized values.
   *
   * @return list of parameter configurations
   */
  public List<RegressionParameterConfig> getParameterConfigs() {
    return parameterConfigs;
  }

  /**
   * Get optimized value for a specific parameter.
   *
   * @param parameter parameter type
   * @return optimized value or NaN if not found
   */
  public double getOptimizedValue(RegressionParameter parameter) {
    for (RegressionParameterConfig config : parameterConfigs) {
      if (config.getParameter() == parameter) {
        return config.getOptimizedValue();
      }
    }
    return Double.NaN;
  }

  /**
   * Get the uncertainty analysis.
   *
   * @return uncertainty analysis
   */
  public UncertaintyAnalysis getUncertainty() {
    return uncertainty;
  }

  /**
   * Get 95% confidence interval for a parameter.
   *
   * @param parameter parameter type
   * @return confidence interval [lower, upper] or null if not available
   */
  public double[] getConfidenceInterval(RegressionParameter parameter) {
    if (uncertainty == null) {
      return null;
    }

    for (int i = 0; i < parameterConfigs.size(); i++) {
      if (parameterConfigs.get(i).getParameter() == parameter) {
        double value = optimizedParameters[i];
        double ci = uncertainty.getConfidenceInterval95(i);
        return new double[] {value - ci, value + ci};
      }
    }
    return null;
  }

  /**
   * Get the final chi-square value.
   *
   * @return chi-square value
   */
  public double getFinalChiSquare() {
    return finalChiSquare;
  }

  /**
   * Get average absolute deviation in percent.
   *
   * @return AAD %
   */
  public double getAverageAbsoluteDeviation() {
    return Math.sqrt(finalChiSquare) * 100;
  }

  /**
   * Generate a summary report.
   *
   * @return summary report string
   */
  public String generateSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== PVT Regression Results ===\n\n");

    sb.append("Optimized Parameters:\n");
    for (int i = 0; i < parameterConfigs.size(); i++) {
      RegressionParameterConfig config = parameterConfigs.get(i);
      sb.append(String.format("  %s: %.6f", config.getParameter().name(), optimizedParameters[i]));
      if (uncertainty != null) {
        sb.append(String.format(" ± %.6f (95%% CI)", uncertainty.getConfidenceInterval95(i)));
      }
      sb.append("\n");
    }

    sb.append("\nObjective Function Values:\n");
    for (Map.Entry<ExperimentType, Double> entry : objectiveValues.entrySet()) {
      sb.append(String.format("  %s: %.6f\n", entry.getKey().name(), entry.getValue()));
    }
    sb.append(String.format("  Total: %.6f\n", getTotalObjective()));

    sb.append("\nFit Quality:\n");
    sb.append(String.format("  Chi-square: %.6f\n", finalChiSquare));
    sb.append(
        String.format("  Average Absolute Deviation: %.2f%%\n", getAverageAbsoluteDeviation()));

    return sb.toString();
  }

  @Override
  public String toString() {
    return generateSummary();
  }
}
