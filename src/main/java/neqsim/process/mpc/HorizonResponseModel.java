package neqsim.process.mpc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sampled unit-step responses and applied-input history for a measurement-corrected prediction.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
final class HorizonResponseModel implements Serializable {
  private static final long serialVersionUID = 1L;
  private final double sampleTime;
  private final int mvCount;
  private final int dvCount;
  private final double[][][] gains;
  private final double[][][] mvGains;
  private final List<double[]> pastChanges = new ArrayList<>();
  private int memory;
  private double[] previousInputs;

  /**
   * Bind one uniformly sampled response to every named input-output pair.
   *
   * @param mvs manipulated inputs in controller order
   * @param dvs measured inputs in controller order
   * @param cvs outputs in controller order
   * @param interval sample interval in seconds
   * @param responses absolute CV step records
   */
  HorizonResponseModel(List<ManipulatedVariable> mvs, List<DisturbanceVariable> dvs, List<ControlledVariable> cvs,
      double interval, StepResponse[] responses) {
    if (mvs.isEmpty() || cvs.isEmpty() || !(interval > 0.0) || !Double.isFinite(interval) || responses == null) {
      throw new IllegalArgumentException("Configure inputs, outputs, a positive sample interval and step responses");
    }
    sampleTime = interval;
    mvCount = mvs.size();
    dvCount = dvs.size();
    List<String> inputs = new ArrayList<>();
    List<String> outputs = new ArrayList<>();
    addNames(inputs, mvs);
    addNames(inputs, dvs);
    addNames(outputs, cvs);
    if (responses.length != inputs.size() * outputs.size()) {
      throw new IllegalArgumentException("Supply one step response per input and CV, including zero-gain paths");
    }
    gains = new double[cvs.size()][inputs.size()][];
    for (StepResponse response : responses) {
      int i = response == null ? -1 : outputs.indexOf(response.getCvName());
      int j = response == null ? -1 : inputs.indexOf(response.getMvName());
      if (i < 0 || j < 0 || gains[i][j] != null) {
        throw new IllegalArgumentException("Null, unknown or duplicate step-response path");
      }
      gains[i][j] = coefficients(response);
      memory = Math.max(memory, gains[i][j].length - 1);
    }
    mvGains = new double[cvs.size()][][];
    for (int i = 0; i < gains.length; i++) {
      mvGains[i] = Arrays.copyOf(gains[i], mvCount);
    }
  }

  /**
   * Collect the distinct names used to bind response paths.
   *
   * @param names destination names
   * @param variables process variables
   */
  private static void addNames(List<String> names, List<? extends MPCVariable> variables) {
    for (MPCVariable variable : variables) {
      if (names.contains(variable.getName())) {
        throw new IllegalArgumentException("Duplicate horizon variable: " + variable.getName());
      }
      names.add(variable.getName());
    }
  }

  /**
   * Extract coefficients for the ends of successive sampling intervals.
   *
   * @param response observed step record
   * @return gain at each interval end
   */
  private double[] coefficients(StepResponse response) {
    double[] time = response.getTime();
    double[] values = response.getNormalizedResponse();
    if (time.length != values.length || time.length == 0 || !Double.isFinite(response.getStepSize())
        || Math.abs(response.getStepSize()) <= 1e-12 || !Double.isFinite(response.getSampleTime())
        || Math.abs(response.getSampleTime() - sampleTime) > 1e-9 * sampleTime) {
      throw new IllegalArgumentException("Step record has invalid amplitude, sample interval or dimensions");
    }
    int start = time[0] == 0.0 ? 1 : 0;
    if (time.length <= start) {
      throw new IllegalArgumentException("Step response needs at least one positive-time sample");
    }
    for (int i = 0; i < time.length; i++) {
      double required = (i + 1.0 - start) * sampleTime;
      if (!Double.isFinite(time[i]) || Math.abs(time[i] - required) > sampleTime * 1e-8
          || !Double.isFinite(values[i])) {
        throw new IllegalArgumentException("Step records must be finite and sampled on the controller time grid");
      }
    }
    return Arrays.copyOfRange(values, start, values.length);
  }

  /**
   * Optimize future inputs with past input changes and preview disturbances accounted for.
   *
   * @param mvs manipulated variables
   * @param cvs controlled variables with current measurements
   * @param inputs current absolute MV values followed by DV values
   * @param preview future absolute DV values with a held tail
   * @param prediction number of predicted samples
   * @param control number of independent moves
   * @param interval configured sample interval
   * @return solver result
   */
  HorizonPlan plan(List<ManipulatedVariable> mvs, List<ControlledVariable> cvs, double[] inputs, double[][] preview,
      int prediction, int control, double interval) {
    if (interval != sampleTime || mvCount != mvs.size() || gains.length != cvs.size()
        || inputs.length != mvCount + dvCount) {
      throw new IllegalStateException("Reconfigure horizon responses after changing variables or sample time");
    }
    if (prediction < 1 || control < 1 || control > prediction) {
      throw new IllegalArgumentException("Require 1 <= control horizon <= prediction horizon");
    }
    if (preview != null) {
      if (preview.length > prediction) {
        throw new IllegalArgumentException("Disturbance preview exceeds prediction horizon");
      }
      for (double[] row : preview) {
        if (row == null || row.length != dvCount) {
          throw new IllegalArgumentException("Supply one value per configured disturbance in each preview row");
        }
        requireFinite(row);
      }
    }
    requireFinite(inputs);
    double[] change = lastChange(inputs);
    double[][] free = new double[prediction][cvs.size()];
    for (int t = 0; t < prediction; t++) {
      for (int i = 0; i < cvs.size(); i++) {
        free[t][i] = cvs.get(i).getCurrentValue();
        for (int j = 0; j < inputs.length; j++) {
          double[] path = gains[i][j];
          free[t][i] += (gain(path, t + 1) - path[0]) * change[j];
          for (int h = 0; h < pastChanges.size() && h + 1 < path.length - 1; h++) {
            free[t][i] += (gain(path, t + h + 2) - path[h + 1]) * pastChanges.get(h)[j];
          }
          if (j >= mvCount && preview != null) {
            double prior = inputs[j];
            for (int k = 0; k <= t && k < preview.length; k++) {
              double next = preview[k][j - mvCount];
              free[t][i] += gain(path, t - k) * (next - prior);
              prior = next;
            }
          }
        }
      }
      requireFinite(free[t]);
    }
    return HorizonSolver.solve(mvs, cvs, Arrays.copyOf(inputs, mvCount), free, mvGains, control);
  }

  /**
   * Check finite measurements or predictions before optimizing a scan.
   *
   * @param values input values
   */
  private static void requireFinite(double[] values) {
    for (double value : values) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Horizon measurements and predictions must be finite");
      }
    }
  }

  /**
   * Hold the final response coefficient after the measured settling window.
   *
   * @param path positive-time gain sequence
   * @param lag zero-based interval index
   * @return gain
   */
  private static double gain(double[] path, int lag) {
    return path[Math.min(lag, path.length - 1)];
  }

  /**
   * Recover actual input changes over the preceding applied interval.
   *
   * @param inputs current measured inputs
   * @return changes, or zero at initial steady history
   */
  private double[] lastChange(double[] inputs) {
    double[] change = new double[inputs.length];
    if (previousInputs != null) {
      for (int j = 0; j < inputs.length; j++) {
        change[j] = inputs[j] - previousInputs[j];
      }
    }
    return change;
  }

  /**
   * Advance the sampled history once when a plan is applied.
   *
   * @param inputs measured inputs at the beginning of this scan
   */
  void advance(double[] inputs) {
    if (memory > 1) {
      pastChanges.add(0, lastChange(inputs));
      if (pastChanges.size() >= memory) {
        pastChanges.remove(memory - 1);
      }
    }
    previousInputs = inputs.clone();
  }
}
