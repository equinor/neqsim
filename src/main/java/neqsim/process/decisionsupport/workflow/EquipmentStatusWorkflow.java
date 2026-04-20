package neqsim.process.decisionsupport.workflow;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.decisionsupport.EngineeringRecommendation;
import neqsim.process.decisionsupport.EngineeringRecommendation.ConstraintCheckResult;
import neqsim.process.decisionsupport.EngineeringRecommendation.ConstraintStatus;
import neqsim.process.decisionsupport.EngineeringRecommendation.Finding;
import neqsim.process.decisionsupport.EngineeringRecommendation.Severity;
import neqsim.process.decisionsupport.EngineeringRecommendation.Verdict;
import neqsim.process.decisionsupport.OperatingSpecification;
import neqsim.process.decisionsupport.OperatorQuery;
import neqsim.process.decisionsupport.QueryWorkflow;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Workflow for reporting current equipment operating status relative to constraints.
 *
 * <p>
 * Runs the current model (no changes), reads capacity utilizations and bottleneck information, and
 * produces a summary of all equipment margins.
 * </p>
 *
 * <p>
 * Optional query parameters:
 * </p>
 * <ul>
 * <li>{@code equipmentName} — specific equipment to check (optional; if omitted, checks all)</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EquipmentStatusWorkflow implements QueryWorkflow {
  private static final Logger logger = LogManager.getLogger(EquipmentStatusWorkflow.class);

  /**
   * Creates a new equipment status workflow.
   */
  public EquipmentStatusWorkflow() {}

  /** {@inheritDoc} */
  @Override
  public boolean canHandle(OperatorQuery query) {
    return query.getQueryType() == OperatorQuery.QueryType.EQUIPMENT_STATUS;
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringRecommendation execute(ProcessSystem process, OperatorQuery query,
      OperatingSpecification spec) {
    String targetEquipment = query.getParameterAsString("equipmentName", "");

    EngineeringRecommendation.Builder builder =
        EngineeringRecommendation.builder().queryId(query.getQueryId());

    // Run the model at current conditions
    try {
      process.run();
    } catch (Exception e) {
      logger.warn("Simulation failed: {}", e.getMessage());
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("Simulation failed: " + e.getMessage())
          .addFinding(new Finding("Simulation non-convergence", Severity.ERROR)).confidence(0.3)
          .build();
    }

    // Get utilizations
    Map<String, Double> utilizations = process.getCapacityUtilizationSummary();
    BottleneckResult bottleneck = process.findBottleneck();
    boolean overloaded = process.isAnyEquipmentOverloaded();

    boolean anyFail = false;
    boolean anyWarn = false;
    int totalChecked = 0;

    for (Map.Entry<String, Double> entry : utilizations.entrySet()) {
      String equipName = entry.getKey();
      double utilPct = entry.getValue();

      // Filter to specific equipment if requested
      if (!targetEquipment.isEmpty() && !equipName.equals(targetEquipment)) {
        continue;
      }

      totalChecked++;
      double marginPct = 100.0 - utilPct;

      ConstraintStatus status;
      if (utilPct > 100.0) {
        status = ConstraintStatus.FAIL;
        anyFail = true;
        builder.addFinding(new Finding(equipName + " is overloaded", Severity.ERROR,
            equipName + ".utilization", utilPct, 100.0, "%"));
      } else if (utilPct > 90.0) {
        status = ConstraintStatus.WARN;
        anyWarn = true;
        builder.addFinding(new Finding(equipName + " approaching capacity", Severity.WARNING,
            equipName + ".utilization", utilPct, 100.0, "%"));
      } else {
        status = ConstraintStatus.PASS;
      }

      builder.addConstraintCheck(
          new ConstraintCheckResult(equipName, status, marginPct, utilPct, 100.0, "%"));

      // Operating envelope status text
      String envelopeStatus;
      if (utilPct > 100) {
        envelopeStatus = "OVERLOADED (" + String.format("%.1f%%", utilPct) + ")";
      } else if (utilPct > 90) {
        envelopeStatus = "NEAR LIMIT (" + String.format("%.1f%%", utilPct) + ")";
      } else {
        envelopeStatus = "OK (" + String.format("%.1f%%", utilPct) + ")";
      }
      builder.addOperatingEnvelopeStatus(equipName, envelopeStatus);
    }

    // Check if specific equipment was found
    if (!targetEquipment.isEmpty() && totalChecked == 0) {
      builder.addFinding(new Finding(
          "Equipment '" + targetEquipment + "' not found or has no capacity constraints",
          Severity.WARNING));
    }

    // Build verdict
    Verdict verdict;
    if (anyFail || overloaded) {
      verdict = Verdict.NOT_FEASIBLE;
    } else if (anyWarn) {
      verdict = Verdict.FEASIBLE_WITH_WARNINGS;
    } else {
      verdict = Verdict.FEASIBLE;
    }

    // Build summary
    StringBuilder summary = new StringBuilder();
    summary.append("Equipment status report: ").append(totalChecked).append(" items checked. ");
    if (bottleneck.hasBottleneck()) {
      summary.append("Current bottleneck: ").append(bottleneck.getEquipmentName()).append(" at ")
          .append(String.format("%.1f%%", bottleneck.getUtilizationPercent()))
          .append(" utilization (margin: ")
          .append(String.format("%.1f%%", bottleneck.getMarginPercent())).append("). ");
    }
    if (verdict == Verdict.FEASIBLE) {
      summary.append("All equipment within normal operating limits.");
    } else if (verdict == Verdict.FEASIBLE_WITH_WARNINGS) {
      summary.append("Some equipment approaching limits.");
    } else {
      summary.append("Equipment overload detected — action required.");
    }

    builder.verdict(verdict).summary(summary.toString()).confidence(0.90)
        .addAssumption("Current model state reflects actual operating conditions")
        .addLimitation("Equipment degradation and fouling not modeled");

    return builder.build();
  }

  /** {@inheritDoc} */
  @Override
  public String getWorkflowId() {
    return "equipment-status";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Reports current equipment operating status, utilization margins, and bottlenecks.";
  }
}
