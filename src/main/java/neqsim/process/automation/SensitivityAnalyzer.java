package neqsim.process.automation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Gradient and Jacobian computation over the agent-facing {@link ProcessAutomation} API.
 *
 * <p>
 * Many downstream agentic workflows (sensitivity studies, gradient-based optimization, what-if
 * scoping, KPI sensitivity dashboards) need partial derivatives of simulation outputs with respect
 * to inputs without having to instrument every equipment class. This analyzer provides those
 * derivatives by finite differences driven entirely through the existing string-addressable
 * facade.
 * </p>
 *
 * <p>
 * Two perturbation modes are supported:
 * </p>
 * <ul>
 * <li><strong>Central difference</strong> (default): {@code dy/dx ≈ (y(x+h) - y(x-h)) / (2 h)} —
 * O(h²) accurate, requires two re-runs per input.</li>
 * <li><strong>Forward difference</strong>: {@code dy/dx ≈ (y(x+h) - y(x)) / h} — O(h) accurate,
 * requires one re-run per input (cheaper for large Jacobians).</li>
 * </ul>
 *
 * <p>
 * The step size for each input defaults to {@code max(absStep, relStep · |x|)} so it scales
 * gracefully across pressures (bara) and temperatures (K). The original input value is restored
 * after every probe so the underlying {@link ProcessAutomation} is left in the state it started
 * in.
 * </p>
 *
 * <p>
 * <strong>Example:</strong>
 * </p>
 *
 * <pre>
 * ProcessAutomation auto = process.getAutomation();
 * SensitivityAnalyzer sens = new SensitivityAnalyzer(auto);
 * double dPowerDPin = sens.partial(
 *     "Compressor.inletStream.pressure", "bara",
 *     "Compressor.power", "kW");
 * Map&lt;String, Double&gt; grad = sens.gradient("Compressor.power", "kW",
 *     Arrays.asList("Compressor.inletStream.pressure", "Compressor.outletPressure"), "bara");
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SensitivityAnalyzer {

  /** Stable schema version for JSON responses produced by this class. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Perturbation mode. */
  public enum Mode {
    /** Central difference: O(h^2), two re-runs per input. */
    CENTRAL,
    /** Forward difference: O(h), one re-run per input. */
    FORWARD
  }

  private final ProcessAutomation automation;
  private double relativeStep = 1.0e-4;
  private double absoluteStep = 1.0e-6;
  private Mode mode = Mode.CENTRAL;

  /**
   * Creates an analyzer bound to a {@link ProcessAutomation} facade.
   *
   * @param automation non-null automation facade; must already be wired to a runnable process
   */
  public SensitivityAnalyzer(ProcessAutomation automation) {
    if (automation == null) {
      throw new IllegalArgumentException("automation must not be null");
    }
    this.automation = automation;
  }

  /**
   * Sets the relative perturbation step. The actual step used per input is
   * {@code max(absoluteStep, relativeStep · |x|)}.
   *
   * @param relativeStep relative step (must be {@code > 0}); typical value 1e-4
   * @return this analyzer for chaining
   */
  public SensitivityAnalyzer setRelativeStep(double relativeStep) {
    if (!(relativeStep > 0.0)) {
      throw new IllegalArgumentException("relativeStep must be > 0");
    }
    this.relativeStep = relativeStep;
    return this;
  }

  /**
   * Sets the absolute floor on perturbation step.
   *
   * @param absoluteStep absolute step (must be {@code > 0}); typical value 1e-6
   * @return this analyzer for chaining
   */
  public SensitivityAnalyzer setAbsoluteStep(double absoluteStep) {
    if (!(absoluteStep > 0.0)) {
      throw new IllegalArgumentException("absoluteStep must be > 0");
    }
    this.absoluteStep = absoluteStep;
    return this;
  }

  /**
   * Sets the finite-difference mode.
   *
   * @param mode non-null mode
   * @return this analyzer for chaining
   */
  public SensitivityAnalyzer setMode(Mode mode) {
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    this.mode = mode;
    return this;
  }

  /**
   * Returns the configured relative step.
   *
   * @return relative step
   */
  public double getRelativeStep() {
    return relativeStep;
  }

  /**
   * Returns the configured absolute step.
   *
   * @return absolute step
   */
  public double getAbsoluteStep() {
    return absoluteStep;
  }

  /**
   * Returns the configured finite-difference mode.
   *
   * @return mode
   */
  public Mode getMode() {
    return mode;
  }

  /**
   * Computes the partial derivative of a single output with respect to a single input by finite
   * differences. The underlying process is re-run via {@link ProcessAutomation#run()} after each
   * perturbation and is restored to its original value afterwards.
   *
   * @param inputAddress dot-notation address of the input variable (must be an INPUT-type variable)
   * @param inputUnit unit string for the input value, e.g. "bara"; may be null or empty for default
   * @param outputAddress dot-notation address of the output variable
   * @param outputUnit unit string for the output value, e.g. "kW"; may be null or empty for default
   * @return ∂(output)/∂(input) in {@code outputUnit / inputUnit}; returns {@link Double#NaN} when
   *         either probe failed
   */
  public double partial(String inputAddress, String inputUnit, String outputAddress,
      String outputUnit) {
    if (inputAddress == null || inputAddress.trim().isEmpty()) {
      throw new IllegalArgumentException("inputAddress must not be null or empty");
    }
    if (outputAddress == null || outputAddress.trim().isEmpty()) {
      throw new IllegalArgumentException("outputAddress must not be null or empty");
    }
    // Ensure base state is consistent before reading anything.
    automation.runIfDirty();
    double x0 = automation.getVariableValue(inputAddress, inputUnit);
    double h = Math.max(absoluteStep, relativeStep * Math.abs(x0));
    if (h <= 0.0) {
      h = absoluteStep;
    }
    double y0 = automation.getVariableValue(outputAddress, outputUnit);
    double dydx;
    try {
      if (mode == Mode.CENTRAL) {
        automation.setVariableValue(inputAddress, x0 + h, inputUnit);
        automation.run();
        double yPlus = automation.getVariableValue(outputAddress, outputUnit);
        automation.setVariableValue(inputAddress, x0 - h, inputUnit);
        automation.run();
        double yMinus = automation.getVariableValue(outputAddress, outputUnit);
        dydx = (yPlus - yMinus) / (2.0 * h);
      } else {
        automation.setVariableValue(inputAddress, x0 + h, inputUnit);
        automation.run();
        double yPlus = automation.getVariableValue(outputAddress, outputUnit);
        dydx = (yPlus - y0) / h;
      }
    } finally {
      // Always restore.
      try {
        automation.setVariableValue(inputAddress, x0, inputUnit);
        automation.run();
      } catch (RuntimeException ignored) {
        // Suppress restore failures so the original exception (if any) is the one that surfaces.
      }
    }
    return dydx;
  }

  /**
   * Computes the gradient ∇y of a single output with respect to a list of inputs. Each input is
   * perturbed independently with the original values restored between probes.
   *
   * @param outputAddress dot-notation address of the output variable
   * @param outputUnit unit string for the output value; may be null or empty for default
   * @param inputAddresses non-null, non-empty list of input addresses
   * @param inputUnit unit string applied to all input values; may be null or empty for default
   * @return ordered map keyed by input address with ∂y/∂x in each entry
   */
  public Map<String, Double> gradient(String outputAddress, String outputUnit,
      List<String> inputAddresses, String inputUnit) {
    if (inputAddresses == null || inputAddresses.isEmpty()) {
      throw new IllegalArgumentException("inputAddresses must not be null or empty");
    }
    Map<String, Double> g = new LinkedHashMap<String, Double>();
    for (String in : inputAddresses) {
      g.put(in, partial(in, inputUnit, outputAddress, outputUnit));
    }
    return g;
  }

  /**
   * Computes the Jacobian matrix ∂y_i/∂x_j over a list of outputs and inputs. Rows correspond to
   * outputs and columns to inputs.
   *
   * @param outputAddresses non-null, non-empty list of output addresses
   * @param outputUnit unit string applied to all output values; may be null or empty for default
   * @param inputAddresses non-null, non-empty list of input addresses
   * @param inputUnit unit string applied to all input values; may be null or empty for default
   * @return Jacobian matrix of shape {@code [outputs.size()][inputs.size()]}
   */
  public double[][] jacobian(List<String> outputAddresses, String outputUnit,
      List<String> inputAddresses, String inputUnit) {
    if (outputAddresses == null || outputAddresses.isEmpty()) {
      throw new IllegalArgumentException("outputAddresses must not be null or empty");
    }
    if (inputAddresses == null || inputAddresses.isEmpty()) {
      throw new IllegalArgumentException("inputAddresses must not be null or empty");
    }
    int nOut = outputAddresses.size();
    int nIn = inputAddresses.size();
    double[][] j = new double[nOut][nIn];
    // Walk by input (one input perturbation = read all outputs) so we run only 2N times for
    // central differences instead of 2·N·M.
    automation.runIfDirty();
    for (int colIdx = 0; colIdx < nIn; colIdx++) {
      String in = inputAddresses.get(colIdx);
      double x0 = automation.getVariableValue(in, inputUnit);
      double h = Math.max(absoluteStep, relativeStep * Math.abs(x0));
      if (h <= 0.0) {
        h = absoluteStep;
      }
      try {
        if (mode == Mode.CENTRAL) {
          automation.setVariableValue(in, x0 + h, inputUnit);
          automation.run();
          double[] yPlus = readAll(outputAddresses, outputUnit);
          automation.setVariableValue(in, x0 - h, inputUnit);
          automation.run();
          double[] yMinus = readAll(outputAddresses, outputUnit);
          for (int rowIdx = 0; rowIdx < nOut; rowIdx++) {
            j[rowIdx][colIdx] = (yPlus[rowIdx] - yMinus[rowIdx]) / (2.0 * h);
          }
        } else {
          double[] y0 = readAll(outputAddresses, outputUnit);
          automation.setVariableValue(in, x0 + h, inputUnit);
          automation.run();
          double[] yPlus = readAll(outputAddresses, outputUnit);
          for (int rowIdx = 0; rowIdx < nOut; rowIdx++) {
            j[rowIdx][colIdx] = (yPlus[rowIdx] - y0[rowIdx]) / h;
          }
        }
      } finally {
        try {
          automation.setVariableValue(in, x0, inputUnit);
          automation.run();
        } catch (RuntimeException ignored) {
          // Suppress restore failures.
        }
      }
    }
    return j;
  }

  /**
   * Returns a JSON representation of a gradient including the schema version, output address,
   * input addresses and computed derivatives. Useful as a stable agent handoff format.
   *
   * @param outputAddress dot-notation address of the output variable
   * @param outputUnit unit string for the output value; may be null or empty for default
   * @param inputAddresses non-null, non-empty list of input addresses
   * @param inputUnit unit string applied to all input values; may be null or empty for default
   * @return JSON string with keys {@code schemaVersion}, {@code mode}, {@code output},
   *         {@code outputUnit}, {@code inputUnit}, and {@code gradient}
   */
  public String gradientAsJson(String outputAddress, String outputUnit,
      List<String> inputAddresses, String inputUnit) {
    Map<String, Double> g = gradient(outputAddress, outputUnit, inputAddresses, inputUnit);
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("mode", mode.name());
    root.addProperty("output", outputAddress);
    root.addProperty("outputUnit", outputUnit == null ? "" : outputUnit);
    root.addProperty("inputUnit", inputUnit == null ? "" : inputUnit);
    root.addProperty("relativeStep", relativeStep);
    root.addProperty("absoluteStep", absoluteStep);
    JsonObject grad = new JsonObject();
    for (Map.Entry<String, Double> e : g.entrySet()) {
      grad.addProperty(e.getKey(), e.getValue());
    }
    root.add("gradient", grad);
    return root.toString();
  }

  /**
   * Returns a JSON representation of a Jacobian.
   *
   * @param outputAddresses non-null, non-empty list of output addresses
   * @param outputUnit unit string applied to all output values; may be null or empty for default
   * @param inputAddresses non-null, non-empty list of input addresses
   * @param inputUnit unit string applied to all input values; may be null or empty for default
   * @return JSON string with keys {@code schemaVersion}, {@code mode}, {@code outputs},
   *         {@code inputs}, {@code outputUnit}, {@code inputUnit}, and {@code jacobian} (a 2D array
   *         indexed as {@code [outputIndex][inputIndex]})
   */
  public String jacobianAsJson(List<String> outputAddresses, String outputUnit,
      List<String> inputAddresses, String inputUnit) {
    double[][] j = jacobian(outputAddresses, outputUnit, inputAddresses, inputUnit);
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("mode", mode.name());
    root.addProperty("outputUnit", outputUnit == null ? "" : outputUnit);
    root.addProperty("inputUnit", inputUnit == null ? "" : inputUnit);
    root.addProperty("relativeStep", relativeStep);
    root.addProperty("absoluteStep", absoluteStep);
    JsonArray outs = new JsonArray();
    for (String o : outputAddresses) {
      outs.add(o);
    }
    root.add("outputs", outs);
    JsonArray ins = new JsonArray();
    for (String i : inputAddresses) {
      ins.add(i);
    }
    root.add("inputs", ins);
    JsonArray rows = new JsonArray();
    for (int rowIdx = 0; rowIdx < j.length; rowIdx++) {
      JsonArray row = new JsonArray();
      for (int colIdx = 0; colIdx < j[rowIdx].length; colIdx++) {
        row.add(j[rowIdx][colIdx]);
      }
      rows.add(row);
    }
    root.add("jacobian", rows);
    return root.toString();
  }

  private double[] readAll(List<String> addresses, String unit) {
    double[] vals = new double[addresses.size()];
    for (int rowIdx = 0; rowIdx < addresses.size(); rowIdx++) {
      vals[rowIdx] = automation.getVariableValue(addresses.get(rowIdx), unit);
    }
    return vals;
  }

  /**
   * Convenience: returns the input addresses currently held (none — kept for API symmetry).
   *
   * @return empty list (this analyzer is stateless w.r.t. addresses)
   */
  public List<String> getInputAddresses() {
    return new ArrayList<String>();
  }
}
