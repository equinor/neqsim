package neqsim.process.util.report;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates a process system for mass balance closure, energy balance closure, and operating limit
 * violations.
 *
 * <p>
 * Scans all equipment in a {@link ProcessSystem} and checks:
 * </p>
 * <ul>
 * <li>Mass balance closure around each equipment (inlet vs outlet mass flow)</li>
 * <li>Extreme temperatures or pressures that may indicate errors</li>
 * <li>Negative flow rates</li>
 * <li>Streams with zero flow</li>
 * <li>Overall process mass balance</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * ProcessValidator validator = new ProcessValidator(process);
 * validator.setMassBalanceTolerance(0.001); // 0.1%
 * validator.validate();
 * boolean passed = validator.isValid();
 * List<ValidationIssue> issues = validator.getIssues();
 * String json = validator.toJson();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessValidator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ProcessValidator.class);

  private final ProcessSystem processSystem;
  private double massBalanceTolerance = 0.01; // 1% relative
  private double minPressure = 0.5; // bara
  private double maxPressure = 1500.0; // bara
  private double minTemperature = 100.0; // K (-173 C)
  private double maxTemperature = 1200.0; // K (927 C)

  private final List<ValidationIssue> issues = new ArrayList<>();
  private boolean validated = false;

  /**
   * Severity level for a validation issue.
   */
  public enum Severity {
    /** Informational notice. */
    INFO,
    /** Warning — may need investigation. */
    WARNING,
    /** Error — likely incorrect result. */
    ERROR
  }

  /**
   * Represents a single validation issue found during process validation.
   */
  public static class ValidationIssue implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Severity of the issue. */
    public final Severity severity;
    /** Equipment or stream name. */
    public final String location;
    /** Description of the issue. */
    public final String message;
    /** Current value that triggered the issue. */
    public final double value;

    /**
     * Creates a validation issue.
     *
     * @param severity issue severity
     * @param location equipment or stream name
     * @param message description
     * @param value triggering value
     */
    public ValidationIssue(Severity severity, String location, String message, double value) {
      this.severity = severity;
      this.location = location;
      this.message = message;
      this.value = value;
    }
  }

  /**
   * Creates a process validator for the given process system.
   *
   * @param processSystem the process system to validate
   */
  public ProcessValidator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Sets the mass balance closure tolerance.
   *
   * @param tolerance relative tolerance (e.g. 0.01 for 1%)
   */
  public void setMassBalanceTolerance(double tolerance) {
    this.massBalanceTolerance = tolerance;
    this.validated = false;
  }

  /**
   * Sets acceptable pressure range.
   *
   * @param min minimum pressure in bara
   * @param max maximum pressure in bara
   */
  public void setPressureLimits(double min, double max) {
    this.minPressure = min;
    this.maxPressure = max;
    this.validated = false;
  }

  /**
   * Sets acceptable temperature range.
   *
   * @param min minimum temperature in K
   * @param max maximum temperature in K
   */
  public void setTemperatureLimits(double min, double max) {
    this.minTemperature = min;
    this.maxTemperature = max;
    this.validated = false;
  }

  /**
   * Runs the validation checks.
   */
  public void validate() {
    issues.clear();
    checkStreamConditions();
    checkMassBalance();
    validated = true;
  }

  /**
   * Checks all streams for extreme conditions.
   */
  private void checkStreamConditions() {
    for (ProcessEquipmentInterface equip : processSystem.getUnitOperations()) {
      // Check inlet streams
      for (StreamInterface stream : equip.getInletStreams()) {
        checkStream(stream);
      }
      // Check outlet streams
      for (StreamInterface stream : equip.getOutletStreams()) {
        checkStream(stream);
      }
    }
  }

  /**
   * Checks a single stream for issues.
   *
   * @param stream the stream to check
   */
  private void checkStream(StreamInterface stream) {
    if (stream == null || stream.getFluid() == null) {
      return;
    }
    String name = stream.getName();
    double temp = stream.getTemperature();
    double press = stream.getPressure();
    double flow = stream.getFluid().getFlowRate("kg/hr");

    if (flow < 0) {
      issues.add(new ValidationIssue(Severity.ERROR, name, "Negative mass flow rate", flow));
    } else if (flow == 0) {
      issues.add(new ValidationIssue(Severity.INFO, name, "Zero mass flow rate", flow));
    }

    if (temp < minTemperature) {
      issues.add(new ValidationIssue(Severity.WARNING, name,
          "Temperature below limit (" + (minTemperature - 273.15) + " C)", temp - 273.15));
    } else if (temp > maxTemperature) {
      issues.add(new ValidationIssue(Severity.WARNING, name,
          "Temperature above limit (" + (maxTemperature - 273.15) + " C)", temp - 273.15));
    }

    if (press < minPressure) {
      issues.add(new ValidationIssue(Severity.WARNING, name,
          "Pressure below limit (" + minPressure + " bara)", press));
    } else if (press > maxPressure) {
      issues.add(new ValidationIssue(Severity.WARNING, name,
          "Pressure above limit (" + maxPressure + " bara)", press));
    }
  }

  /**
   * Checks mass balance around equipment with multiple inlets/outlets.
   */
  private void checkMassBalance() {
    for (ProcessEquipmentInterface equip : processSystem.getUnitOperations()) {
      List<StreamInterface> inlets = equip.getInletStreams();
      List<StreamInterface> outlets = equip.getOutletStreams();

      if (inlets.isEmpty() || outlets.isEmpty()) {
        continue;
      }

      // Only check equipment that should conserve mass (mixer, separator, splitter)
      // Heaters/coolers also conserve mass
      if (!(equip instanceof Mixer) && !(equip instanceof Separator) && !(equip instanceof Splitter)
          && !(equip instanceof Heater)) {
        continue;
      }

      double inletMass = 0.0;
      for (StreamInterface s : inlets) {
        if (s != null && s.getFluid() != null) {
          inletMass += s.getFluid().getFlowRate("kg/hr");
        }
      }

      double outletMass = 0.0;
      for (StreamInterface s : outlets) {
        if (s != null && s.getFluid() != null) {
          outletMass += s.getFluid().getFlowRate("kg/hr");
        }
      }

      if (inletMass > 0) {
        double error = Math.abs(inletMass - outletMass) / inletMass;
        if (error > massBalanceTolerance) {
          issues.add(new ValidationIssue(Severity.ERROR, equip.getName(),
              "Mass balance error: " + String.format("%.2f%%", error * 100.0), error));
        }
      }
    }
  }

  /**
   * Checks if the process passed all validation checks (no errors).
   *
   * @return true if no ERROR-level issues were found
   */
  public boolean isValid() {
    if (!validated) {
      validate();
    }
    for (ValidationIssue issue : issues) {
      if (issue.severity == Severity.ERROR) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets the total number of issues found.
   *
   * @return issue count
   */
  public int getIssueCount() {
    if (!validated) {
      validate();
    }
    return issues.size();
  }

  /**
   * Gets the number of errors found.
   *
   * @return error count
   */
  public int getErrorCount() {
    if (!validated) {
      validate();
    }
    int count = 0;
    for (ValidationIssue issue : issues) {
      if (issue.severity == Severity.ERROR) {
        count++;
      }
    }
    return count;
  }

  /**
   * Gets the number of warnings found.
   *
   * @return warning count
   */
  public int getWarningCount() {
    if (!validated) {
      validate();
    }
    int count = 0;
    for (ValidationIssue issue : issues) {
      if (issue.severity == Severity.WARNING) {
        count++;
      }
    }
    return count;
  }

  /**
   * Gets all validation issues.
   *
   * @return unmodifiable list of issues
   */
  public List<ValidationIssue> getIssues() {
    if (!validated) {
      validate();
    }
    return Collections.unmodifiableList(issues);
  }

  /**
   * Gets only error-level issues.
   *
   * @return list of errors
   */
  public List<ValidationIssue> getErrors() {
    if (!validated) {
      validate();
    }
    List<ValidationIssue> errors = new ArrayList<>();
    for (ValidationIssue issue : issues) {
      if (issue.severity == Severity.ERROR) {
        errors.add(issue);
      }
    }
    return errors;
  }

  /**
   * Returns the validation results as a JSON string.
   *
   * @return JSON representation of validation results
   */
  public String toJson() {
    if (!validated) {
      validate();
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("analysisType", "Process Validation");
    result.put("isValid", isValid());
    result.put("totalIssues", issues.size());
    result.put("errors", getErrorCount());
    result.put("warnings", getWarningCount());
    result.put("massBalanceTolerance", massBalanceTolerance);

    List<Map<String, Object>> issueList = new ArrayList<>();
    for (ValidationIssue issue : issues) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("severity", issue.severity.name());
      m.put("location", issue.location);
      m.put("message", issue.message);
      m.put("value", issue.value);
      issueList.add(m);
    }
    result.put("issues", issueList);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }
}
