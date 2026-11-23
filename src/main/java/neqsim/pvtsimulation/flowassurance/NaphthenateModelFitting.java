package neqsim.pvtsimulation.flowassurance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.simple.SimpleMatrix;
import neqsim.pvtsimulation.flowassurance.NaphthenatePrecipitationModel.Parameter;

/**
 * Regression of {@link NaphthenatePrecipitationModel} parameters to laboratory or field measurements.
 *
 * <p>
 * Each observation is one experiment: a model holding the conditions of the experiment (oil, water, pH or CO2 pressure,
 * temperature), the kind of quantity that was measured, its value and its standard deviation. The selected parameters
 * are adjusted by weighted non-linear least squares (Levenberg-Marquardt with a finite-difference Jacobian).
 * </p>
 *
 * <p>
 * Which parameters the data can determine:
 * </p>
 * <ul>
 * <li>Acid content of the water versus pH without calcium determines the sum pKa + log P. Fit only one of the two; they
 * are separately identifiable only when the data reach pH values where the water acid is mostly dissociated and the oil
 * is depleted.</li>
 * <li>Calcium soap amounts or soap onset pH determine pKsp of the calcium soap, given pKa and log P.</li>
 * <li>ARN precipitated fraction or ARN onset pH determines pK of ARN.</li>
 * <li>The enthalpies need data at two or more temperatures.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class NaphthenateModelFitting {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(NaphthenateModelFitting.class);

  /**
   * Kind of measured quantity.
   */
  public enum ObservationType {
    /** Precipitated calcium soap of the monoprotic acids (kg/m3 oil). */
    PRECIPITATED_CALCIUM_SOAP,
    /** Fraction of the ARN tetraacid that precipitated (0 to 1). */
    PRECIPITATED_ARN_FRACTION,
    /** Organic acid in the water after equilibration, dissociated and undissociated (mg/L). */
    AQUEOUS_ORGANIC_ACID,
    /** Equilibrium water pH, for experiments run at a CO2 partial pressure. */
    WATER_PH,
    /** Water pH at which ARN calcium naphthenate first forms. */
    ARN_ONSET_PH,
    /** Water pH at which the calcium soap of the monoprotic acids first forms. */
    CALCIUM_SOAP_ONSET_PH
  }

  /**
   * One measurement with the conditions it was taken at.
   */
  private static final class Observation {
    /** Model holding the experimental conditions. */
    private final NaphthenatePrecipitationModel scenario;
    /** Kind of measured quantity. */
    private final ObservationType type;
    /** Measured value. */
    private final double measured;
    /** Standard deviation of the measurement. */
    private final double standardDeviation;

    /**
     * Creates an observation.
     *
     * @param scenario model holding the experimental conditions
     * @param type kind of measured quantity
     * @param measured measured value
     * @param standardDeviation standard deviation of the measurement
     */
    private Observation(NaphthenatePrecipitationModel scenario, ObservationType type, double measured,
        double standardDeviation) {
      this.scenario = scenario;
      this.type = type;
      this.measured = measured;
      this.standardDeviation = standardDeviation;
    }
  }

  /** Observations to fit. */
  private final List<Observation> observations = new ArrayList<Observation>();
  /** Parameters to adjust. */
  private final List<Parameter> fitParameters = new ArrayList<Parameter>();
  /** Fitted parameter values, in the order of {@link #fitParameters}. */
  private double[] fittedValues = new double[0];
  /** Standard errors of the fitted parameters. */
  private double[] standardErrors = new double[0];
  /** Model values of the observations after the fit. */
  private double[] calculatedValues = new double[0];
  /** Weighted sum of squared residuals after the fit. */
  private double chiSquare = Double.NaN;
  /** Whether a fit has completed. */
  private boolean solved = false;
  /** Maximum number of Levenberg-Marquardt iterations. */
  private int maxIterations = 100;

  /**
   * Creates an empty fitting problem.
   */
  public NaphthenateModelFitting() {
  }

  /**
   * Adds a measurement.
   *
   * @param scenario model with the conditions of the experiment; it is copied, and its parameter values are the
   * starting point for parameters that are not fitted
   * @param type kind of measured quantity
   * @param measured measured value in the unit of the observation type
   * @param standardDeviation standard deviation of the measurement in the same unit, positive
   * @throws IllegalArgumentException if the scenario or type is null or the standard deviation is not positive
   */
  public void addObservation(NaphthenatePrecipitationModel scenario, ObservationType type, double measured,
      double standardDeviation) {
    if (scenario == null || type == null) {
      throw new IllegalArgumentException("scenario and type must not be null");
    }
    if (!(standardDeviation > 0.0)) {
      throw new IllegalArgumentException("standardDeviation must be positive");
    }
    observations.add(new Observation(scenario.copy(), type, measured, standardDeviation));
    solved = false;
  }

  /**
   * Selects a parameter to adjust.
   *
   * @param parameter parameter to adjust
   */
  public void addFitParameter(Parameter parameter) {
    if (!fitParameters.contains(parameter)) {
      fitParameters.add(parameter);
      solved = false;
    }
  }

  /**
   * Sets the maximum number of iterations.
   *
   * @param maxIterations maximum number of Levenberg-Marquardt iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Calculates the model value of one observation.
   *
   * @param observation observation to evaluate
   * @param values trial values of the fitted parameters
   * @param extrapolateOnset true to return a parameter-dependent value above pH 14 when the calcium soap onset is not
   * reached, so that the search has a slope towards parameters that give an onset
   * @return model value in the unit of the observation type, NaN when the model has no value
   */
  private double calculate(Observation observation, double[] values, boolean extrapolateOnset) {
    NaphthenatePrecipitationModel model = observation.scenario;
    for (int i = 0; i < values.length; i++) {
      model.setParameter(fitParameters.get(i), values[i]);
    }
    switch (observation.type) {
    case ARN_ONSET_PH:
      return model.calculateArnOnsetPH();
    case CALCIUM_SOAP_ONSET_PH:
      double onset = model.calculateCalciumSoapOnsetPH();
      if (Double.isNaN(onset) && extrapolateOnset) {
        double saturation = model.calculateCalciumSoapSaturationRatio(14.0);
        // the saturation ratio rises by about two decades per pH unit
        return saturation > 0.0 ? 14.0 - 0.5 * Math.log10(saturation) : Double.NaN;
      }
      return onset;
    default:
      break;
    }
    model.run();
    switch (observation.type) {
    case PRECIPITATED_CALCIUM_SOAP:
      return model.getPrecipitatedCalciumSoap();
    case PRECIPITATED_ARN_FRACTION:
      return model.getPrecipitatedArnFraction();
    case AQUEOUS_ORGANIC_ACID:
      return model.getAqueousOrganicAcidConcentration();
    case WATER_PH:
      return model.getWaterPH();
    default:
      throw new IllegalStateException("Unsupported observation type " + observation.type);
    }
  }

  /**
   * Calculates the weighted residuals of all observations.
   *
   * @param values trial values of the fitted parameters
   * @return weighted residuals (calculated - measured) / standard deviation
   */
  private double[] residuals(double[] values) {
    double[] result = new double[observations.size()];
    for (int i = 0; i < result.length; i++) {
      Observation observation = observations.get(i);
      double calculated = calculate(observation, values, true);
      if (Double.isNaN(calculated)) {
        // no model value at all (for example an ARN onset without ARN): large penalty, rejected after the search
        calculated = observation.measured + 100.0 * observation.standardDeviation;
      }
      result[i] = (calculated - observation.measured) / observation.standardDeviation;
    }
    return result;
  }

  /**
   * Sums the squares of an array.
   *
   * @param values values to square and sum
   * @return sum of squares
   */
  private static double sumOfSquares(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value * value;
    }
    return sum;
  }

  /**
   * Calculates the finite-difference Jacobian of the weighted residuals.
   *
   * @param values parameter values
   * @param base residuals at the parameter values
   * @return Jacobian with one row per observation and one column per parameter
   */
  private SimpleMatrix jacobian(double[] values, double[] base) {
    SimpleMatrix matrix = new SimpleMatrix(base.length, values.length);
    for (int j = 0; j < values.length; j++) {
      double step = 1.0e-4 * Math.max(Math.abs(values[j]), 1.0);
      double[] shifted = values.clone();
      shifted[j] += step;
      double[] moved = residuals(shifted);
      for (int i = 0; i < base.length; i++) {
        matrix.set(i, j, (moved[i] - base[i]) / step);
      }
    }
    return matrix;
  }

  /**
   * Runs the regression.
   *
   * @return true when the fit completed and the model gives a value for every observation; false when an observed
   * quantity cannot be reproduced at all (for example an onset pH that the fitted model never reaches), in which case
   * the result is not available and nothing can be applied
   * @throws IllegalStateException if no parameter is selected or there are fewer observations than parameters
   */
  public boolean solve() {
    int parameterCount = fitParameters.size();
    if (parameterCount == 0) {
      throw new IllegalStateException("Select at least one parameter with addFitParameter");
    }
    if (observations.size() < parameterCount) {
      throw new IllegalStateException("Need at least as many observations as fitted parameters");
    }
    double[] values = new double[parameterCount];
    for (int j = 0; j < parameterCount; j++) {
      values[j] = observations.get(0).scenario.getParameter(fitParameters.get(j));
    }
    double[] residual = residuals(values);
    double cost = sumOfSquares(residual);
    double damping = 1.0e-3;
    solved = false;

    for (int iteration = 0; iteration < maxIterations; iteration++) {
      SimpleMatrix jac = jacobian(values, residual);
      SimpleMatrix normal = jac.transpose().mult(jac);
      SimpleMatrix gradient = jac.transpose().mult(new SimpleMatrix(residual.length, 1, true, residual));
      boolean improved = false;
      double relativeChange = 0.0;
      while (damping < 1.0e12) {
        SimpleMatrix damped = normal.copy();
        for (int j = 0; j < parameterCount; j++) {
          damped.set(j, j, normal.get(j, j) * (1.0 + damping) + 1.0e-12);
        }
        double[] trial = values.clone();
        boolean finite = true;
        try {
          SimpleMatrix step = damped.solve(gradient);
          for (int j = 0; j < parameterCount; j++) {
            trial[j] -= step.get(j, 0);
            finite = finite && !Double.isNaN(trial[j]) && !Double.isInfinite(trial[j]);
          }
        } catch (RuntimeException e) {
          finite = false;
        }
        if (finite) {
          double[] trialResidual = residuals(trial);
          double trialCost = sumOfSquares(trialResidual);
          if (trialCost < cost) {
            relativeChange = (cost - trialCost) / Math.max(cost, 1.0e-300);
            values = trial;
            residual = trialResidual;
            cost = trialCost;
            damping = Math.max(damping / 10.0, 1.0e-12);
            improved = true;
            break;
          }
        }
        damping *= 10.0;
      }
      if (!improved || relativeChange < 1.0e-12 || cost < 1.0e-20) {
        break;
      }
    }

    fittedValues = values;
    chiSquare = cost;
    calculatedValues = new double[observations.size()];
    for (int i = 0; i < calculatedValues.length; i++) {
      calculatedValues[i] = calculate(observations.get(i), values, false);
      if (Double.isNaN(calculatedValues[i])) {
        logger.warn("Naphthenate model fit rejected: observation {} ({}) has no model value", i,
            observations.get(i).type);
        return false;
      }
    }
    standardErrors = calculateStandardErrors(values, residual);
    solved = true;
    logger.info("Naphthenate model fit: {} parameters, {} observations, chi-square {}", parameterCount,
        observations.size(), chiSquare);
    return true;
  }

  /**
   * Calculates the standard errors of the fitted parameters from the covariance matrix.
   *
   * @param values fitted parameter values
   * @param residual weighted residuals at the fitted values
   * @return standard errors, NaN where the data do not determine a parameter
   */
  private double[] calculateStandardErrors(double[] values, double[] residual) {
    double[] errors = new double[values.length];
    Arrays.fill(errors, Double.NaN);
    try {
      SimpleMatrix jac = jacobian(values, residual);
      SimpleMatrix normal = jac.transpose().mult(jac);
      if (Math.abs(normal.determinant()) < 1.0e-300) {
        return errors;
      }
      SimpleMatrix covariance = normal.invert();
      int freedom = residual.length - values.length;
      // with no degrees of freedom the measurement standard deviations alone set the uncertainty
      double scale = freedom > 0 ? Math.max(1.0, sumOfSquares(residual) / freedom) : 1.0;
      for (int j = 0; j < values.length; j++) {
        double variance = covariance.get(j, j) * scale;
        errors[j] = variance >= 0.0 && !Double.isInfinite(variance) ? Math.sqrt(variance) : Double.NaN;
      }
    } catch (RuntimeException e) {
      logger.warn("Covariance of the naphthenate fit could not be calculated: {}", e.getMessage());
    }
    return errors;
  }

  /**
   * Checks that a fit exists.
   *
   * @throws IllegalStateException if {@link #solve()} has not completed
   */
  private void requireSolved() {
    if (!solved) {
      throw new IllegalStateException("Call solve() first");
    }
  }

  /**
   * Gets the fitted value of a parameter.
   *
   * @param parameter fitted parameter
   * @return fitted value
   * @throws IllegalStateException if {@link #solve()} has not completed
   * @throws IllegalArgumentException if the parameter was not fitted
   */
  public double getFittedValue(Parameter parameter) {
    requireSolved();
    return fittedValues[indexOf(parameter)];
  }

  /**
   * Gets the standard error of a fitted parameter.
   *
   * @param parameter fitted parameter
   * @return standard error, NaN when the data do not determine the parameter
   * @throws IllegalStateException if {@link #solve()} has not completed
   * @throws IllegalArgumentException if the parameter was not fitted
   */
  public double getStandardError(Parameter parameter) {
    requireSolved();
    return standardErrors[indexOf(parameter)];
  }

  /**
   * Finds the position of a fitted parameter.
   *
   * @param parameter parameter to find
   * @return index in the list of fitted parameters
   * @throws IllegalArgumentException if the parameter was not fitted
   */
  private int indexOf(Parameter parameter) {
    int index = fitParameters.indexOf(parameter);
    if (index < 0) {
      throw new IllegalArgumentException(parameter + " was not fitted");
    }
    return index;
  }

  /**
   * Gets the model values of the observations after the fit, in the order they were added.
   *
   * @return calculated values in the units of the observation types
   * @throws IllegalStateException if {@link #solve()} has not completed
   */
  public double[] getCalculatedValues() {
    requireSolved();
    return calculatedValues.clone();
  }

  /**
   * Gets the weighted sum of squared residuals after the fit.
   *
   * @return chi-square
   * @throws IllegalStateException if {@link #solve()} has not completed
   */
  public double getChiSquare() {
    requireSolved();
    return chiSquare;
  }

  /**
   * Gets the root mean square of the weighted residuals. A value near one means the model matches the data within the
   * stated measurement uncertainty.
   *
   * @return root mean square weighted residual
   * @throws IllegalStateException if {@link #solve()} has not completed
   */
  public double getRootMeanSquareWeightedResidual() {
    requireSolved();
    return Math.sqrt(chiSquare / observations.size());
  }

  /**
   * Gets the number of observations.
   *
   * @return number of observations
   */
  public int getNumberOfObservations() {
    return observations.size();
  }

  /**
   * Copies the fitted parameters to a model and marks it as calibrated.
   *
   * @param model model to update
   * @throws IllegalStateException if {@link #solve()} has not completed
   */
  public void applyTo(NaphthenatePrecipitationModel model) {
    requireSolved();
    for (int j = 0; j < fittedValues.length; j++) {
      model.setParameter(fitParameters.get(j), fittedValues[j]);
    }
    model.setCalibrated(true);
  }

  /**
   * Returns the fit as an ordered map.
   *
   * @return map with fitted parameters, standard errors and fit quality
   * @throws IllegalStateException if {@link #solve()} has not completed
   */
  public Map<String, Object> toMap() {
    requireSolved();
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    for (int j = 0; j < fittedValues.length; j++) {
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("value", fittedValues[j]);
      entry.put("standardError", Double.isNaN(standardErrors[j]) ? null : Double.valueOf(standardErrors[j]));
      parameters.put(fitParameters.get(j).name(), entry);
    }
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("fittedParameters", parameters);
    map.put("numberOfObservations", observations.size());
    map.put("chiSquare", chiSquare);
    map.put("rootMeanSquareWeightedResidual", getRootMeanSquareWeightedResidual());
    return map;
  }
}
