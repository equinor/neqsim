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
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Workflow for evaluating whether a proposed rate change is feasible.
 *
 * <p>
 * Given a target flow rate and the name of the feed stream, this workflow: (1) sets the new rate on
 * the feed stream, (2) runs the simulation, (3) checks capacity constraints and bottlenecks, and
 * (4) produces a recommendation with constraint check results.
 * </p>
 *
 * <p>
 * Required query parameters:
 * </p>
 * <ul>
 * <li>{@code targetFlowRate} — the proposed flow rate (double)</li>
 * <li>{@code flowRateUnit} — the unit string, e.g. "kg/hr" (optional, default "kg/hr")</li>
 * <li>{@code feedStreamName} — the name of the feed stream to adjust (optional, default "feed")
 * </li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class RateChangeFeasibilityWorkflow implements QueryWorkflow {
  private static final Logger logger = LogManager.getLogger(RateChangeFeasibilityWorkflow.class);

  /**
   * Creates a new rate change feasibility workflow.
   */
  public RateChangeFeasibilityWorkflow() {}

  /** {@inheritDoc} */
  @Override
  public boolean canHandle(OperatorQuery query) {
    return query.getQueryType() == OperatorQuery.QueryType.RATE_CHANGE_FEASIBILITY;
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringRecommendation execute(ProcessSystem process, OperatorQuery query,
      OperatingSpecification spec) {
    double targetRate = query.getParameterAsDouble("targetFlowRate", -1.0);
    String unit = query.getParameterAsString("flowRateUnit", "kg/hr");
    String feedName = query.getParameterAsString("feedStreamName", "feed");

    if (targetRate <= 0) {
      return EngineeringRecommendation.builder().verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("Missing or invalid 'targetFlowRate' parameter in query.")
          .queryId(query.getQueryId()).confidence(0.0).build();
    }

    EngineeringRecommendation.Builder builder =
        EngineeringRecommendation.builder().queryId(query.getQueryId());

    // Set the new flow rate
    Object unit0 = process.getUnit(feedName);
    if (unit0 == null || !(unit0 instanceof StreamInterface)) {
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("Feed stream '" + feedName + "' not found in process model.").confidence(0.0)
          .build();
    }

    StreamInterface feedStream = (StreamInterface) unit0;
    feedStream.setFlowRate(targetRate, unit);

    // Run simulation
    try {
      process.run();
    } catch (Exception e) {
      logger.warn("Simulation failed at rate {} {}: {}", targetRate, unit, e.getMessage());
      return builder.verdict(Verdict.NOT_FEASIBLE)
          .summary("Simulation failed at rate " + targetRate + " " + unit + ": " + e.getMessage())
          .addFinding(new Finding("Simulation non-convergence", Severity.ERROR)).confidence(0.5)
          .build();
    }

    // Check bottleneck
    BottleneckResult bottleneck = process.findBottleneck();
    boolean overloaded = process.isAnyEquipmentOverloaded();
    Map<String, Double> utilizations = process.getCapacityUtilizationSummary();

    // Assess constraints
    boolean anyFail = false;
    boolean anyWarn = false;
    int passCount = 0;

    for (Map.Entry<String, Double> entry : utilizations.entrySet()) {
      String equipName = entry.getKey();
      double utilizationPct = entry.getValue();
      double marginPct = 100.0 - utilizationPct;

      ConstraintStatus status;
      if (utilizationPct > 100.0) {
        status = ConstraintStatus.FAIL;
        anyFail = true;
      } else if (utilizationPct > 90.0) {
        status = ConstraintStatus.WARN;
        anyWarn = true;
      } else {
        status = ConstraintStatus.PASS;
        passCount++;
      }

      builder.addConstraintCheck(new ConstraintCheckResult(equipName + " capacity", status,
          marginPct, utilizationPct, 100.0, "%"));
    }

    // Build findings from bottleneck
    if (bottleneck.hasBottleneck()) {
      Severity sev = bottleneck.isExceeded() ? Severity.ERROR : Severity.WARNING;
      builder.addFinding(new Finding(
          "Bottleneck at " + bottleneck.getEquipmentName() + " (" + bottleneck.getConstraintName()
              + "): " + String.format("%.1f%%", bottleneck.getUtilizationPercent())
              + " utilization",
          sev, bottleneck.getEquipmentName() + ".utilization", bottleneck.getUtilizationPercent(),
          100.0, "%"));
    }

    // Determine verdict
    Verdict verdict;
    double confidence = 0.85;
    if (anyFail) {
      verdict = Verdict.NOT_FEASIBLE;
    } else if (anyWarn) {
      verdict = Verdict.FEASIBLE_WITH_WARNINGS;
    } else {
      verdict = Verdict.FEASIBLE;
      confidence = 0.90;
    }

    // Build summary
    StringBuilder summary = new StringBuilder();
    summary.append("Rate change to ").append(String.format("%.1f", targetRate)).append(" ")
        .append(unit);
    if (verdict == Verdict.FEASIBLE) {
      summary.append(" is feasible. All ").append(passCount)
          .append(" equipment constraints satisfied.");
    } else if (verdict == Verdict.FEASIBLE_WITH_WARNINGS) {
      summary.append(" is feasible with warnings. Some equipment approaching limits.");
      if (bottleneck.hasBottleneck()) {
        summary.append(" Closest limit: ").append(bottleneck.getEquipmentName()).append(" at ")
            .append(String.format("%.1f%%", bottleneck.getUtilizationPercent())).append(".");
      }
    } else {
      summary.append(" is NOT feasible.");
      if (bottleneck.hasBottleneck()) {
        summary.append(" ").append(bottleneck.getEquipmentName()).append(" exceeds capacity at ")
            .append(String.format("%.1f%%", bottleneck.getUtilizationPercent())).append(".");
      }
    }

    builder.verdict(verdict).summary(summary.toString()).confidence(confidence)
        .addAssumption("Steady-state simulation using current model configuration")
        .addAssumption("Equipment performance curves reflect as-designed conditions")
        .addLimitation("Dynamic transients during rate change are not modeled")
        .addLimitation("Instrument accuracy and measurement uncertainty not included");

    return builder.build();
  }

  /** {@inheritDoc} */
  @Override
  public String getWorkflowId() {
    return "rate-change-feasibility";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Evaluates whether a proposed rate change is feasible by running the simulation "
        + "at the new rate and checking all capacity constraints.";
  }
}
