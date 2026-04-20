package neqsim.process.decisionsupport.workflow;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.decisionsupport.EngineeringRecommendation;
import neqsim.process.decisionsupport.EngineeringRecommendation.Finding;
import neqsim.process.decisionsupport.EngineeringRecommendation.Severity;
import neqsim.process.decisionsupport.EngineeringRecommendation.Verdict;
import neqsim.process.decisionsupport.OperatingSpecification;
import neqsim.process.decisionsupport.OperatorQuery;
import neqsim.process.decisionsupport.QueryWorkflow;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Workflow for evaluating arbitrary "what-if" parameter changes.
 *
 * <p>
 * Accepts a map of variable address/value pairs, applies them to a cloned model via
 * {@link ProcessAutomation}, runs the simulation, and reports key output changes.
 * </p>
 *
 * <p>
 * Required query parameters:
 * </p>
 * <ul>
 * <li>{@code changes} — a Map of variable address (String) to new value (Number). The address
 * format follows the {@link ProcessAutomation} convention: "UnitName.stream.property".</li>
 * <li>{@code outputVariables} — a Map of variable address (String) to unit (String) to report
 * (optional; if omitted, reports bottleneck and capacity utilization)</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class WhatIfWorkflow implements QueryWorkflow {
  private static final Logger logger = LogManager.getLogger(WhatIfWorkflow.class);

  /**
   * Creates a new what-if workflow.
   */
  public WhatIfWorkflow() {}

  /** {@inheritDoc} */
  @Override
  public boolean canHandle(OperatorQuery query) {
    return query.getQueryType() == OperatorQuery.QueryType.WHAT_IF;
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public EngineeringRecommendation execute(ProcessSystem process, OperatorQuery query,
      OperatingSpecification spec) {
    EngineeringRecommendation.Builder builder =
        EngineeringRecommendation.builder().queryId(query.getQueryId());

    // Get changes
    Object changesObj = query.getParameter("changes");
    if (changesObj == null || !(changesObj instanceof Map)) {
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("Missing 'changes' parameter (expected Map of variable address to value).")
          .confidence(0.0).build();
    }

    Map<String, Object> changes = (Map<String, Object>) changesObj;

    // Run baseline first
    try {
      process.run();
    } catch (Exception e) {
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("Baseline simulation failed: " + e.getMessage()).confidence(0.0).build();
    }

    // Capture baseline utilizations
    Map<String, Double> baselineUtils = process.getCapacityUtilizationSummary();
    boolean baselineOverloaded = process.isAnyEquipmentOverloaded();

    // Apply changes via automation API
    ProcessAutomation auto = process.getAutomation();
    int appliedCount = 0;
    int failedCount = 0;

    for (Map.Entry<String, Object> entry : changes.entrySet()) {
      String address = entry.getKey();
      Object valueObj = entry.getValue();

      if (!(valueObj instanceof Number)) {
        builder.addFinding(
            new Finding("Skipped non-numeric value for '" + address + "'", Severity.WARNING));
        failedCount++;
        continue;
      }

      double value = ((Number) valueObj).doubleValue();
      try {
        auto.setVariableValue(address, value, "");
        appliedCount++;
      } catch (Exception e) {
        builder.addFinding(
            new Finding("Failed to set '" + address + "': " + e.getMessage(), Severity.WARNING));
        failedCount++;
      }
    }

    if (appliedCount == 0) {
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("No changes could be applied. Check variable addresses.").confidence(0.0)
          .build();
    }

    // Re-run with changes
    try {
      process.run();
    } catch (Exception e) {
      return builder.verdict(Verdict.NOT_FEASIBLE)
          .summary("Simulation failed after applying changes: " + e.getMessage())
          .addFinding(new Finding("Simulation non-convergence after changes", Severity.ERROR))
          .confidence(0.5).build();
    }

    // Compare results
    Map<String, Double> newUtils = process.getCapacityUtilizationSummary();
    boolean newOverloaded = process.isAnyEquipmentOverloaded();

    // Report changes in utilization
    for (Map.Entry<String, Double> entry : newUtils.entrySet()) {
      String equipName = entry.getKey();
      double newUtil = entry.getValue();
      Double baseUtil = baselineUtils.get(equipName);
      double baseVal = (baseUtil != null) ? baseUtil : 0.0;
      double delta = newUtil - baseVal;

      if (Math.abs(delta) > 1.0) {
        Severity sev;
        if (newUtil > 100) {
          sev = Severity.ERROR;
        } else if (newUtil > 90) {
          sev = Severity.WARNING;
        } else {
          sev = Severity.INFO;
        }
        builder.addFinding(new Finding(
            equipName + " utilization " + (delta > 0 ? "increased" : "decreased") + " by "
                + String.format("%.1f%%", Math.abs(delta)) + " to "
                + String.format("%.1f%%", newUtil),
            sev, equipName + ".utilization", newUtil, 100.0, "%"));
      }
    }

    // Read requested output variables
    Object outputsObj = query.getParameter("outputVariables");
    if (outputsObj instanceof Map) {
      Map<String, Object> outputs = (Map<String, Object>) outputsObj;
      for (Map.Entry<String, Object> entry : outputs.entrySet()) {
        String address = entry.getKey();
        String outputUnit = (entry.getValue() instanceof String) ? (String) entry.getValue() : "";
        try {
          double val = auto.getVariableValue(address, outputUnit);
          builder.addFinding(
              new Finding(address + " = " + String.format("%.4f", val) + " " + outputUnit,
                  Severity.INFO, address, val, 0.0, outputUnit));
        } catch (Exception e) {
          builder.addFinding(
              new Finding("Could not read '" + address + "': " + e.getMessage(), Severity.WARNING));
        }
      }
    }

    // Determine verdict
    Verdict verdict;
    if (newOverloaded) {
      verdict = Verdict.NOT_FEASIBLE;
    } else if (!baselineOverloaded && process.isAnyEquipmentOverloaded()) {
      verdict = Verdict.NOT_FEASIBLE;
    } else {
      boolean anyNearLimit = false;
      for (Double util : newUtils.values()) {
        if (util > 90) {
          anyNearLimit = true;
          break;
        }
      }
      verdict = anyNearLimit ? Verdict.FEASIBLE_WITH_WARNINGS : Verdict.FEASIBLE;
    }

    StringBuilder summary = new StringBuilder();
    summary.append("What-if analysis: applied ").append(appliedCount).append(" change(s)");
    if (failedCount > 0) {
      summary.append(" (").append(failedCount).append(" failed)");
    }
    summary.append(". ");
    if (verdict == Verdict.FEASIBLE) {
      summary.append("All constraints satisfied after changes.");
    } else if (verdict == Verdict.FEASIBLE_WITH_WARNINGS) {
      summary.append("Changes feasible but some equipment approaching limits.");
    } else {
      summary.append("Changes result in equipment overload — not feasible.");
    }

    builder.verdict(verdict).summary(summary.toString()).confidence(0.80)
        .addAssumption("Steady-state analysis only")
        .addAssumption("All other operating parameters remain unchanged")
        .addLimitation("Transient behavior during change not modeled")
        .addLimitation("Coupled effects between parameters may not be fully captured");

    return builder.build();
  }

  /** {@inheritDoc} */
  @Override
  public String getWorkflowId() {
    return "what-if";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Evaluates arbitrary parameter changes by applying them to the model "
        + "and comparing before/after results.";
  }
}
