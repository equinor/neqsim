package neqsim.util.validation.contracts;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.validation.ValidationResult;

/**
 * Contract for separator equipment.
 * 
 * <p>
 * Defines requirements and guarantees for {@link Separator} implementations. AI agents can use this
 * contract to validate separator setup before running.
 * </p>
 * 
 * <h2>Preconditions (what the separator needs):</h2>
 * <ul>
 * <li>At least one inlet stream connected</li>
 * <li>Inlet stream(s) have been run</li>
 * <li>Multi-phase fluid (gas + liquid) for meaningful separation</li>
 * </ul>
 * 
 * <h2>Postconditions (what run() provides):</h2>
 * <ul>
 * <li>Gas outlet stream with vapor phase</li>
 * <li>Liquid outlet stream with liquid phase(s)</li>
 * <li>Mass/energy balance maintained</li>
 * </ul>
 * 
 * @author NeqSim
 * @version 1.0
 */
public class SeparatorContract implements ModuleContract<Separator> {
  /** Singleton instance. */
  private static final SeparatorContract INSTANCE = new SeparatorContract();

  private SeparatorContract() {}

  /**
   * Get the singleton instance.
   * 
   * @return contract instance
   */
  public static SeparatorContract getInstance() {
    return INSTANCE;
  }

  @Override
  public String getContractName() {
    return "SeparatorContract";
  }

  @Override
  public ValidationResult checkPreconditions(Separator separator) {
    ValidationResult result = new ValidationResult("Separator:" + separator.getName());

    // Check: Has a name
    if (separator.getName() == null || separator.getName().isEmpty()) {
      result.addError("separator.name", "Separator has no name",
          "Set separator name in constructor: new Separator(\"sep1\", stream)");
    }

    // Check: Has inlet stream
    StreamInterface feedStream = null;
    try {
      feedStream = separator.getFeedStream();
    } catch (Exception e) {
      // getFeedStream may throw if not set
    }

    if (feedStream == null) {
      result.addError("separator.inlet", "No inlet stream connected",
          "Create separator with inlet: new Separator(\"name\", feedStream)");
      return result;
    }

    // Check feed stream preconditions
    StreamContract streamContract = StreamContract.getInstance();
    ValidationResult inletResult = streamContract.checkPreconditions(feedStream);
    for (ValidationResult.ValidationIssue issue : inletResult.getIssues()) {
      if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
        result.addError("separator.inlet." + issue.getCategory(), issue.getMessage(),
            issue.getRemediation());
      }
    }

    // Check: Multi-phase expected (warning only)
    if (feedStream.getFluid() != null) {
      int numPhases = feedStream.getFluid().getNumberOfPhases();
      if (numPhases < 2) {
        result.addWarning("separator.phases",
            "Inlet has only " + numPhases + " phase(s) - separator may not be useful",
            "Check if flash conditions produce gas + liquid");
      }
    }

    return result;
  }

  @Override
  public ValidationResult checkPostconditions(Separator separator) {
    ValidationResult result =
        new ValidationResult("Separator:" + separator.getName() + " (post-run)");

    // Check: Gas outlet exists and has vapor
    try {
      StreamInterface gasOut = separator.getGasOutStream();
      if (gasOut == null) {
        result.addWarning("separator.gasOutlet", "No gas outlet stream",
            "Access via separator.getGasOutStream()");
      } else if (gasOut.getFluid() != null) {
        double gasFraction = 0;
        if (gasOut.getFluid().hasPhaseType("gas")) {
          gasFraction = gasOut.getFluid().getPhase("gas").getBeta();
        }
        if (gasFraction < 0.01) {
          result.addWarning("separator.gasOutlet",
              "Gas outlet has very low vapor fraction: " + gasFraction,
              "Check inlet conditions - may be mostly liquid");
        }
      }
    } catch (Exception e) {
      result.addWarning("separator.gasOutlet", "Could not check gas outlet: " + e.getMessage(), "");
    }

    // Check: Liquid outlet exists
    try {
      StreamInterface liqOut = separator.getLiquidOutStream();
      if (liqOut == null) {
        result.addWarning("separator.liquidOutlet", "No liquid outlet stream",
            "Access via separator.getLiquidOutStream()");
      }
    } catch (Exception e) {
      result.addWarning("separator.liquidOutlet",
          "Could not check liquid outlet: " + e.getMessage(), "");
    }

    // Check: Mass balance (simplified)
    try {
      double inletMass = 0;
      StreamInterface feedStream = separator.getFeedStream();
      if (feedStream != null && feedStream.getFluid() != null) {
        inletMass = feedStream.getFluid().getTotalNumberOfMoles();
      }

      double outletMass = 0;
      StreamInterface gasOut = separator.getGasOutStream();
      StreamInterface liqOut = separator.getLiquidOutStream();
      if (gasOut != null && gasOut.getFluid() != null) {
        outletMass += gasOut.getFluid().getTotalNumberOfMoles();
      }
      if (liqOut != null && liqOut.getFluid() != null) {
        outletMass += liqOut.getFluid().getTotalNumberOfMoles();
      }

      if (inletMass > 0 && Math.abs(inletMass - outletMass) / inletMass > 0.01) {
        result.addWarning("separator.massBalance",
            "Mass balance error: inlet=" + inletMass + " mol, outlet=" + outletMass + " mol",
            "Check for leaks or accumulation in separator model");
      }
    } catch (Exception e) {
      // Mass balance check is optional
    }

    return result;
  }

  @Override
  public String getRequirementsDescription() {
    return "Separator Requirements:\n" + "- Valid name (non-empty string)\n"
        + "- At least one inlet stream connected\n" + "- Inlet stream has valid fluid\n"
        + "- Inlet stream has been run (TPflash performed)\n"
        + "- Multi-phase fluid for meaningful separation";
  }

  @Override
  public String getProvidesDescription() {
    return "Separator Provides (after run()):\n" + "- Gas outlet stream (getGasOutStream())\n"
        + "- Liquid outlet stream (getLiquidOutStream())\n"
        + "- Oil outlet stream if 3-phase (getOilOutStream())\n"
        + "- Separated phases with correct compositions\n" + "- Mass/energy balance maintained";
  }
}
