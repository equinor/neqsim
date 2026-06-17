package neqsim.util.agentic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Automated quality gate for simulation results validation.
 *
 * <p>
 * Validates a completed ProcessSystem against physical and engineering constraints:
 * </p>
 * <ul>
 * <li>Mass balance closure across the entire process</li>
 * <li>Energy balance closure (if enthalpy data available)</li>
 * <li>Physical bounds (T &gt; 0 K, P &gt; 0, 0 &lt;= compositions &lt;= 1)</li>
 * <li>Stream consistency (no NaN or infinite values)</li>
 * </ul>
 *
 * <p>
 * Designed to be called between Step 2 (analysis) and Step 3 (reporting) of the task-solving
 * workflow. When called from Python via jpype, enables automated QA gates in notebooks.
 * </p>
 *
 * <h2>Usage in Java:</h2>
 *
 * <pre>
 * ProcessSystem process = new ProcessSystem();
 * // ... build and run process ...
 * process.run();
 *
 * SimulationQualityGate gate = new SimulationQualityGate(process);
 * gate.validate();
 * boolean passed = gate.isPassed();
 * String report = gate.toJson();
 * </pre>
 *
 * <h2>Usage in Python (jpype):</h2>
 *
 * <pre>
 * import jpype
 * SimulationQualityGate = jpype.JClass(
 *     "neqsim.util.agentic.SimulationQualityGate")
 * gate = SimulationQualityGate(process)
 * gate.validate()
 * assert gate.isPassed(), gate.toJson()
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SimulationQualityGate implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Default tolerance for mass balance closure (fraction). */
  public static final double DEFAULT_MASS_BALANCE_TOLERANCE = 0.01;

  /** Default tolerance for energy balance closure (fraction). */
  public static final double DEFAULT_ENERGY_BALANCE_TOLERANCE = 0.05;

  private final transient ProcessSystem process;
  private double massBalanceTolerance = DEFAULT_MASS_BALANCE_TOLERANCE;
  private double energyBalanceTolerance = DEFAULT_ENERGY_BALANCE_TOLERANCE;

  private boolean validated = false;
  private boolean passed = false;
  private List<QualityIssue> issues;

  /**
   * Constructor for SimulationQualityGate.
   *
   * @param process the ProcessSystem to validate
   */
  public SimulationQualityGate(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("ProcessSystem cannot be null");
    }
    this.process = process;
    this.issues = new ArrayList<>();
  }

  /**
   * Run all validation checks.
   */
  public void validate() {
    issues.clear();
    passed = true;

    checkMassBalance();
    checkEnergyBalance();
    checkPhysicalBounds();
    checkStreamConsistency();
    checkCompositionNormalization();

    validated = true;
    for (QualityIssue issue : issues) {
      if (issue.severity == Severity.ERROR) {
        passed = false;
        break;
      }
    }
  }

  /**
   * Check overall mass balance closure across the process.
   *
   * <p>
   * Compares total inlet mass flow to total outlet mass flow. Uses the first and last equipment in
   * the process to identify boundary streams. Tolerance is configurable via
   * {@link #setMassBalanceTolerance(double)}.
   * </p>
   */
  private void checkMassBalance() {
    List<ProcessEquipmentInterface> units = process.getUnitOperations();
    if (units == null || units.size() < 2) {
      return;
    }

    double totalInletMass = 0.0;
    double totalOutletMass = 0.0;

    // Sum inlet mass flow from the first unit (feed streams)
    ProcessEquipmentInterface firstUnit = units.get(0);
    if (firstUnit != null) {
      List<StreamInterface> inlets = firstUnit.getInletStreams();
      if (inlets != null) {
        for (StreamInterface stream : inlets) {
          if (stream != null && stream.getThermoSystem() != null) {
            double flow = stream.getThermoSystem().getFlowRate("kg/sec");
            if (!Double.isNaN(flow) && !Double.isInfinite(flow) && flow > 0) {
              totalInletMass += flow;
            }
          }
        }
      }
    }

    // Sum outlet mass flow from the last unit (product streams)
    ProcessEquipmentInterface lastUnit = units.get(units.size() - 1);
    if (lastUnit != null) {
      List<StreamInterface> outlets = lastUnit.getOutletStreams();
      if (outlets != null) {
        for (StreamInterface stream : outlets) {
          if (stream != null && stream.getThermoSystem() != null) {
            double flow = stream.getThermoSystem().getFlowRate("kg/sec");
            if (!Double.isNaN(flow) && !Double.isInfinite(flow) && flow > 0) {
              totalOutletMass += flow;
            }
          }
        }
      }
    }

    // Also check intermediate streams that exit the process boundary (e.g., separator liquid out)
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface unit = units.get(i);
      if (unit == null || unit == lastUnit) {
        continue;
      }
      List<StreamInterface> outlets = unit.getOutletStreams();
      if (outlets == null) {
        continue;
      }
      for (StreamInterface stream : outlets) {
        if (stream == null || stream.getThermoSystem() == null) {
          continue;
        }
        // Check if this outlet stream is consumed by a downstream unit
        boolean isConsumed = false;
        for (int j = i + 1; j < units.size(); j++) {
          ProcessEquipmentInterface downstream = units.get(j);
          if (downstream == null) {
            continue;
          }
          List<StreamInterface> downInlets = downstream.getInletStreams();
          if (downInlets != null && downInlets.contains(stream)) {
            isConsumed = true;
            break;
          }
        }
        if (!isConsumed) {
          double flow = stream.getThermoSystem().getFlowRate("kg/sec");
          if (!Double.isNaN(flow) && !Double.isInfinite(flow) && flow > 0) {
            totalOutletMass += flow;
          }
        }
      }
    }

    if (totalInletMass > 0.0 && totalOutletMass > 0.0) {
      double relativeError = Math.abs(totalInletMass - totalOutletMass) / totalInletMass;
      if (relativeError > massBalanceTolerance) {
        addIssue(Severity.ERROR, "mass_balance",
            String.format(
                "Mass balance not closed: inlet=%.4f kg/s, outlet=%.4f kg/s, error=%.4f%%",
                totalInletMass, totalOutletMass, relativeError * 100.0),
            "Check for leaks in the flowsheet — missing outlet streams or disconnected equipment");
      } else {
        addIssue(Severity.INFO, "mass_balance",
            String.format("Mass balance closed: error=%.6f%% (tolerance=%.2f%%)",
                relativeError * 100.0, massBalanceTolerance * 100.0),
            "No action needed");
      }
    }
  }

  /**
   * Check overall energy balance closure across the process.
   *
   * <p>
   * Compares total inlet enthalpy flow to total outlet enthalpy flow plus external duties.
   * Tolerance is configurable via {@link #setEnergyBalanceTolerance(double)}.
   * </p>
   */
  private void checkEnergyBalance() {
    List<ProcessEquipmentInterface> units = process.getUnitOperations();
    if (units == null || units.size() < 2) {
      return;
    }

    double totalInletEnthalpy = 0.0;
    double totalOutletEnthalpy = 0.0;
    boolean hasValidEnthalpy = false;

    // Sum inlet enthalpy from feed streams
    ProcessEquipmentInterface firstUnit = units.get(0);
    if (firstUnit != null) {
      List<StreamInterface> inlets = firstUnit.getInletStreams();
      if (inlets != null) {
        for (StreamInterface stream : inlets) {
          if (stream != null && stream.getThermoSystem() != null) {
            double h = stream.getThermoSystem().getEnthalpy();
            if (!Double.isNaN(h) && !Double.isInfinite(h)) {
              totalInletEnthalpy += h;
              hasValidEnthalpy = true;
            }
          }
        }
      }
    }

    // Sum outlet enthalpy from all boundary streams
    for (ProcessEquipmentInterface unit : units) {
      if (unit == null) {
        continue;
      }
      List<StreamInterface> outlets = unit.getOutletStreams();
      if (outlets == null) {
        continue;
      }
      for (StreamInterface stream : outlets) {
        if (stream == null || stream.getThermoSystem() == null) {
          continue;
        }
        double h = stream.getThermoSystem().getEnthalpy();
        if (!Double.isNaN(h) && !Double.isInfinite(h)) {
          totalOutletEnthalpy += h;
        }
      }
    }

    if (hasValidEnthalpy && totalInletEnthalpy != 0.0) {
      double relativeError =
          Math.abs(totalInletEnthalpy - totalOutletEnthalpy) / Math.abs(totalInletEnthalpy);
      if (relativeError > energyBalanceTolerance) {
        addIssue(Severity.WARNING, "energy_balance",
            String.format("Energy balance deviation: inlet=%.2f W, outlet=%.2f W, error=%.2f%%",
                totalInletEnthalpy, totalOutletEnthalpy, relativeError * 100.0),
            "Check heat exchanger duties, compressor work, and external energy sources/sinks");
      } else {
        addIssue(Severity.INFO, "energy_balance",
            String.format("Energy balance acceptable: error=%.4f%% (tolerance=%.2f%%)",
                relativeError * 100.0, energyBalanceTolerance * 100.0),
            "No action needed");
      }
    }
  }

  /**
   * Check physical bounds on all streams in the process.
   */
  private void checkPhysicalBounds() {
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      if (equipment == null) {
        continue;
      }

      List<StreamInterface> outlets = equipment.getOutletStreams();
      if (outlets == null) {
        continue;
      }
      for (StreamInterface stream : outlets) {
        if (stream == null || stream.getThermoSystem() == null) {
          continue;
        }
        checkStreamPhysicalBounds(stream);
      }
    }
  }

  /**
   * Check physical bounds for a single stream.
   *
   * @param stream the stream to check
   */
  private void checkStreamPhysicalBounds(StreamInterface stream) {
    SystemInterface fluid = stream.getThermoSystem();
    String streamName = stream.getName();

    // Temperature check: must be > 0 K
    double temp = fluid.getTemperature();
    if (temp <= 0.0 || Double.isNaN(temp) || Double.isInfinite(temp)) {
      addIssue(Severity.ERROR, "physical_bounds",
          "Stream '" + streamName + "' has invalid temperature: " + temp + " K",
          "Check upstream equipment calculations and inlet conditions");
    } else if (temp < 100.0) {
      addIssue(Severity.WARNING, "physical_bounds",
          "Stream '" + streamName + "' has very low temperature: " + (temp - 273.15) + " C",
          "Verify this is physically reasonable for the process");
    } else if (temp > 2500.0) {
      addIssue(Severity.WARNING, "physical_bounds",
          "Stream '" + streamName + "' has very high temperature: " + (temp - 273.15) + " C",
          "Temperatures above 2200 C are unusual for process equipment");
    }

    // Pressure check: must be > 0
    double press = fluid.getPressure();
    if (press <= 0.0 || Double.isNaN(press) || Double.isInfinite(press)) {
      addIssue(Severity.ERROR, "physical_bounds",
          "Stream '" + streamName + "' has invalid pressure: " + press + " bara",
          "Check equipment pressure settings and valve configurations");
    } else if (press > 1500.0) {
      addIssue(Severity.WARNING, "physical_bounds",
          "Stream '" + streamName + "' has very high pressure: " + press + " bara",
          "Verify this is within equipment design limits");
    }
  }

  /**
   * Check for NaN or infinite values in stream properties.
   */
  private void checkStreamConsistency() {
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      if (equipment == null) {
        continue;
      }

      List<StreamInterface> outlets = equipment.getOutletStreams();
      if (outlets == null) {
        continue;
      }
      for (StreamInterface stream : outlets) {
        if (stream == null || stream.getThermoSystem() == null) {
          continue;
        }
        SystemInterface fluid = stream.getThermoSystem();
        String streamName = stream.getName();

        // Check flow rate
        double flowRate = fluid.getFlowRate("kg/sec");
        if (Double.isNaN(flowRate) || Double.isInfinite(flowRate)) {
          addIssue(Severity.ERROR, "stream_consistency",
              "Stream '" + streamName + "' has NaN/Inf flow rate",
              "Check feed stream specifications and fluid initialization");
        } else if (flowRate < 0.0) {
          addIssue(Severity.ERROR, "stream_consistency",
              "Stream '" + streamName + "' has negative flow rate: " + flowRate + " kg/s",
              "Negative flow indicates reversed stream — check equipment connections");
        } else if (flowRate == 0.0) {
          addIssue(Severity.WARNING, "stream_consistency",
              "Stream '" + streamName + "' has zero flow rate",
              "Zero flow may indicate disconnected or bypassed equipment");
        }

        // Check enthalpy
        double enthalpy = fluid.getEnthalpy();
        if (Double.isNaN(enthalpy) || Double.isInfinite(enthalpy)) {
          addIssue(Severity.WARNING, "stream_consistency",
              "Stream '" + streamName + "' has NaN/Inf enthalpy",
              "May affect energy balance. Check flash calculation convergence");
        }
      }
    }
  }

  /**
   * Check that component mole fractions sum to approximately 1.0 in all streams.
   */
  private void checkCompositionNormalization() {
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      if (equipment == null) {
        continue;
      }

      List<StreamInterface> outlets = equipment.getOutletStreams();
      if (outlets == null) {
        continue;
      }
      for (StreamInterface stream : outlets) {
        if (stream == null || stream.getThermoSystem() == null) {
          continue;
        }
        SystemInterface fluid = stream.getThermoSystem();
        String streamName = stream.getName();

        for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
          double sumZ = 0.0;
          for (int c = 0; c < fluid.getPhase(p).getNumberOfComponents(); c++) {
            double z = fluid.getPhase(p).getComponent(c).getx();
            if (Double.isNaN(z) || Double.isInfinite(z)) {
              addIssue(Severity.ERROR, "composition",
                  "Stream '" + streamName + "' phase " + p + " component " + c + " has NaN/Inf "
                      + "mole fraction",
                  "Flash calculation likely failed — check convergence and mixing rules");
              break;
            }
            if (z < -1e-10) {
              addIssue(Severity.ERROR, "composition",
                  "Stream '" + streamName + "' phase " + p + " has negative mole fraction for "
                      + fluid.getPhase(p).getComponent(c).getComponentName(),
                  "Indicates flash calculation error");
            }
            sumZ += z;
          }
          if (Math.abs(sumZ - 1.0) > 0.01) {
            addIssue(Severity.WARNING, "composition",
                "Stream '" + streamName + "' phase " + p + " mole fractions sum to " + sumZ
                    + " (expected 1.0)",
                "May indicate flash convergence issues or improper normalization");
          }
        }
      }
    }
  }

  /**
   * Add a quality issue.
   *
   * @param severity issue severity
   * @param category check category
   * @param message description of the issue
   * @param remediation suggested fix
   */
  private void addIssue(Severity severity, String category, String message, String remediation) {
    issues.add(new QualityIssue(severity, category, message, remediation));
  }

  /**
   * Check if validation passed (no errors).
   *
   * @return true if all checks passed without errors
   */
  public boolean isPassed() {
    if (!validated) {
      throw new IllegalStateException("Call validate() before checking results");
    }
    return passed;
  }

  /**
   * Get the number of errors found.
   *
   * @return error count
   */
  public int getErrorCount() {
    int count = 0;
    for (QualityIssue issue : issues) {
      if (issue.severity == Severity.ERROR) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get the number of warnings found.
   *
   * @return warning count
   */
  public int getWarningCount() {
    int count = 0;
    for (QualityIssue issue : issues) {
      if (issue.severity == Severity.WARNING) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get all issues.
   *
   * @return list of quality issues
   */
  public List<QualityIssue> getIssues() {
    return new ArrayList<>(issues);
  }

  /**
   * Get results as JSON string.
   *
   * @return JSON report of validation results
   */
  public String toJson() {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("passed", passed);
    report.put("errorCount", getErrorCount());
    report.put("warningCount", getWarningCount());
    report.put("massBalanceTolerance", massBalanceTolerance);
    report.put("energyBalanceTolerance", energyBalanceTolerance);

    List<Map<String, String>> issueList = new ArrayList<>();
    for (QualityIssue issue : issues) {
      Map<String, String> issueMap = new LinkedHashMap<>();
      issueMap.put("severity", issue.severity.name());
      issueMap.put("category", issue.category);
      issueMap.put("message", issue.message);
      issueMap.put("remediation", issue.remediation);
      issueList.add(issueMap);
    }
    report.put("issues", issueList);

    return new GsonBuilder().setPrettyPrinting().create().toJson(report);
  }

  /**
   * Set mass balance tolerance.
   *
   * @param tolerance tolerance as a fraction (e.g., 0.01 for 1%)
   */
  public void setMassBalanceTolerance(double tolerance) {
    this.massBalanceTolerance = tolerance;
  }

  /**
   * Set energy balance tolerance.
   *
   * @param tolerance tolerance as a fraction (e.g., 0.05 for 5%)
   */
  public void setEnergyBalanceTolerance(double tolerance) {
    this.energyBalanceTolerance = tolerance;
  }

  /**
   * Issue severity levels.
   */
  public enum Severity {
    /** Critical error that must be fixed. */
    ERROR,
    /** Warning that should be reviewed. */
    WARNING,
    /** Informational note. */
    INFO
  }

  /**
   * Represents a single quality gate issue.
   */
  public static class QualityIssue implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Issue severity. */
    public final Severity severity;
    /** Check category (e.g., "mass_balance", "physical_bounds"). */
    public final String category;
    /** Description of the issue. */
    public final String message;
    /** Suggested remediation. */
    public final String remediation;

    /**
     * Constructor for QualityIssue.
     *
     * @param severity issue severity
     * @param category check category
     * @param message description
     * @param remediation suggested fix
     */
    public QualityIssue(Severity severity, String category, String message, String remediation) {
      this.severity = severity;
      this.category = category;
      this.message = message;
      this.remediation = remediation;
    }
  }
}
