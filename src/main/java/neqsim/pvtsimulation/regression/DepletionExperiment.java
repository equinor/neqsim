package neqsim.pvtsimulation.regression;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.thermo.system.SystemInterface;

/**
 * Immutable isothermal depletion protocol with a bounded, model-local result cache shared by observation rows.
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class DepletionExperiment {
  private static final int MAX_CACHED_TRIALS = 8;
  private final ExperimentType type;
  private final double[] pressures;
  private final double temperature;
  private final byte[] modelSnapshot;
  private final RegressionParameter[] parameters;
  private final Map<ParameterKey, double[][]> results = new LinkedHashMap<>();

  /**
   * Freeze the full model, parameter identities and protocol for one registered experiment.
   *
   * @param type CVD or DLE
   * @param pressures ordered pressure schedule in bara
   * @param temperature isothermal temperature in K
   * @param baseFluid model snapshot before fitting
   * @param configs ordered fitting-parameter definitions
   */
  DepletionExperiment(ExperimentType type, double[] pressures, double temperature, SystemInterface baseFluid,
      List<RegressionParameterConfig> configs) {
    validateSchedule(type, pressures, temperature);
    this.type = type;
    this.pressures = pressures.clone();
    this.temperature = temperature;
    // Ordinary fluid clones share interaction matrices; a frozen object graph is required for model-local caching.
    this.modelSnapshot = SerializationUtils.serialize(baseFluid);
    parameters = new RegressionParameter[configs.size()];
    for (int i = 0; i < configs.size(); i++) {
      parameters[i] = configs.get(i).getParameter();
    }
  }

  /**
   * Validate a nonempty, nonincreasing depletion schedule without sorting or merging repeated rows.
   *
   * @param type requested experiment type
   * @param pressures pressure schedule in bara
   * @param temperature isothermal temperature in K
   * @throws IllegalArgumentException if any protocol value is invalid
   */
  static void validateSchedule(ExperimentType type, double[] pressures, double temperature) {
    if ((type != ExperimentType.CVD && type != ExperimentType.DLE) || pressures == null || pressures.length == 0
        || !Double.isFinite(temperature) || temperature <= 0.0) {
      throw new IllegalArgumentException("Depletion requires CVD/DLE, a pressure schedule and positive temperature");
    }
    for (int row = 0; row < pressures.length; row++) {
      if (!Double.isFinite(pressures[row]) || pressures[row] <= 0.0
          || (row > 0 && pressures[row] > pressures[row - 1])) {
        throw new IllegalArgumentException("Depletion pressures must be positive finite bara in nonincreasing order");
      }
    }
  }

  /**
   * Return one observable from a complete trial experiment. Only successfully completed results enter the cache.
   *
   * @param requestedType type encoded in the observation
   * @param pressure observation pressure in bara
   * @param observationTemperature observation temperature in K
   * @param row original protocol row index
   * @param property observable index
   * @param trialParameters complete fitting-parameter vector
   * @return the protocol-indexed simulated value
   * @throws IllegalArgumentException if observation identity or parameter values are invalid
   * @throws IllegalStateException if simulation results are missing or nonfinite
   */
  synchronized double value(ExperimentType requestedType, double pressure, double observationTemperature, int row,
      int property, double[] trialParameters) {
    int propertyCount = type == ExperimentType.CVD ? 2 : 3;
    if (requestedType != type || row < 0 || row >= pressures.length || pressure != pressures[row]
        || observationTemperature != temperature || property < 0 || property >= propertyCount) {
      throw new IllegalArgumentException("Depletion observation does not match its registered experiment/row/property");
    }
    if (trialParameters.length != parameters.length) {
      throw new IllegalArgumentException("Depletion trial must contain the complete parameter vector");
    }
    ParameterKey key = new ParameterKey(trialParameters);
    double[][] values = results.get(key);
    if (values == null) {
      SystemInterface trial = SerializationUtils.deserialize(modelSnapshot);
      for (int i = 0; i < parameters.length; i++) {
        parameters[i].applyToFluid(trial, key.values[i]);
      }
      trial.init(0);
      trial.init(1);
      if (type == ExperimentType.CVD) {
        ConstantVolumeDepletion experiment = new ConstantVolumeDepletion(trial);
        experiment.setPressures(pressures.clone());
        experiment.setTemperature(temperature, "K");
        experiment.runCalc();
        values = new double[][] {experiment.getLiquidRelativeVolume(), experiment.getZgas()};
      } else {
        DifferentialLiberation experiment = new DifferentialLiberation(trial);
        experiment.setPressures(pressures.clone());
        experiment.setTemperature(temperature, "K");
        experiment.runCalc();
        values = new double[][] {experiment.getRs(), experiment.getBo(), experiment.getOilDensity()};
      }
      for (double[] observable : values) {
        if (observable == null || observable.length != pressures.length) {
          throw new IllegalStateException("Incomplete " + type + " depletion experiment results");
        }
        for (double calculated : observable) {
          if (!Double.isFinite(calculated)) {
            throw new IllegalStateException("Nonfinite " + type + " depletion experiment result");
          }
        }
      }
      if (results.size() >= MAX_CACHED_TRIALS) {
        results.remove(results.keySet().iterator().next());
      }
      results.put(key, values);
    }
    return values[property][row];
  }

  /**
   * Exact parameter identity; the enclosing experiment fixes model, protocol, units and reference conditions.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class ParameterKey {
    private final double[] values;

    /**
     * Copy a complete finite trial vector, including vectors mutated through the legacy public parameter array.
     *
     * @param parameters trial values
     * @throws IllegalArgumentException if a trial parameter is nonfinite
     */
    private ParameterKey(double[] parameters) {
      values = parameters.clone();
      for (double value : values) {
        if (!Double.isFinite(value)) {
          throw new IllegalArgumentException("Depletion fitting parameters must be finite");
        }
      }
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
      return other instanceof ParameterKey && Arrays.equals(values, ((ParameterKey) other).values);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
      return Arrays.hashCode(values);
    }
  }
}
