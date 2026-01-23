package neqsim.util.validation.contracts;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.validation.ValidationResult;

/**
 * Contract for process streams.
 * 
 * <p>
 * Defines requirements and guarantees for {@link StreamInterface} implementations. AI agents can
 * use this contract to validate stream setup before connecting to equipment.
 * </p>
 * 
 * <h2>Preconditions (what the stream needs):</h2>
 * <ul>
 * <li>Valid thermodynamic system attached</li>
 * <li>Flow rate &gt; 0</li>
 * <li>Valid name for identification</li>
 * </ul>
 * 
 * <h2>Postconditions (what run() provides):</h2>
 * <ul>
 * <li>Calculated outlet conditions</li>
 * <li>Phase equilibrium (if flash performed)</li>
 * <li>Stream properties accessible via getFluid()</li>
 * </ul>
 * 
 * @author NeqSim
 * @version 1.0
 */
public class StreamContract implements ModuleContract<StreamInterface> {
  /** Singleton instance. */
  private static final StreamContract INSTANCE = new StreamContract();

  /** Minimum valid flow rate. */
  private static final double MIN_FLOW_RATE = 1e-12;

  private StreamContract() {}

  /**
   * Get the singleton instance.
   * 
   * @return contract instance
   */
  public static StreamContract getInstance() {
    return INSTANCE;
  }

  @Override
  public String getContractName() {
    return "StreamContract";
  }

  @Override
  public ValidationResult checkPreconditions(StreamInterface stream) {
    ValidationResult result = new ValidationResult("Stream:" + stream.getName());

    // Check: Has a name
    if (stream.getName() == null || stream.getName().isEmpty()) {
      result.addError("stream.name", "Stream has no name",
          "Set stream name in constructor: new Stream(\"feed\", system)");
    }

    // Check: Has fluid attached
    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      result.addError("stream.fluid", "No fluid/thermodynamic system attached",
          "Create stream with fluid: new Stream(\"name\", system)");
      return result; // Can't check more without fluid
    }

    // Delegate thermo checks
    ThermodynamicSystemContract thermoContract = ThermodynamicSystemContract.getInstance();
    ValidationResult thermoResult = thermoContract.checkPreconditions(fluid);
    for (ValidationResult.ValidationIssue issue : thermoResult.getIssues()) {
      if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
        result.addError("stream.fluid." + issue.getCategory(), issue.getMessage(),
            issue.getRemediation());
      } else if (issue.getSeverity() == ValidationResult.Severity.MAJOR) {
        result.addWarning("stream.fluid." + issue.getCategory(), issue.getMessage(),
            issue.getRemediation());
      }
    }

    // Check: Flow rate
    double flowRate = fluid.getTotalNumberOfMoles();
    if (flowRate <= MIN_FLOW_RATE) {
      result.addWarning("stream.flowRate", "Flow rate is very low: " + flowRate + " mol/s",
          "Set flow rate: stream.setFlowRate(100.0, \"kg/hr\")");
    }

    return result;
  }

  @Override
  public ValidationResult checkPostconditions(StreamInterface stream) {
    ValidationResult result = new ValidationResult("Stream:" + stream.getName() + " (post-run)");

    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      result.addError("stream.fluid", "No fluid after run()", "Check stream initialization");
      return result;
    }

    // Delegate to thermo postconditions
    ThermodynamicSystemContract thermoContract = ThermodynamicSystemContract.getInstance();
    ValidationResult thermoResult = thermoContract.checkPostconditions(fluid);
    for (ValidationResult.ValidationIssue issue : thermoResult.getIssues()) {
      if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
        result.addError("stream.fluid." + issue.getCategory(), issue.getMessage(),
            issue.getRemediation());
      } else if (issue.getSeverity() == ValidationResult.Severity.MAJOR) {
        result.addWarning("stream.fluid." + issue.getCategory(), issue.getMessage(),
            issue.getRemediation());
      }
    }

    return result;
  }

  @Override
  public String getRequirementsDescription() {
    return "Stream Requirements:\n" + "- Valid name (non-empty string)\n"
        + "- Thermodynamic system attached\n"
        + "- Flow rate > 0 (via setFlowRate or component moles)\n"
        + "- Valid temperature and pressure in fluid";
  }

  @Override
  public String getProvidesDescription() {
    return "Stream Provides (after run()):\n" + "- Outlet temperature and pressure\n"
        + "- Phase distribution\n" + "- Component mass/mole fractions\n"
        + "- Enthalpy, entropy, density\n" + "- Can connect to downstream equipment";
  }
}
