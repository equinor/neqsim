package neqsim.process.decisionsupport.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.decisionsupport.EngineeringRecommendation;
import neqsim.process.decisionsupport.EngineeringRecommendation.ConstraintStatus;
import neqsim.process.decisionsupport.EngineeringRecommendation.DerateOption;
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
 * Workflow for finding safe derate options when the plant needs to reduce throughput.
 *
 * <p>
 * Sweeps flow rates from the current rate down to a minimum, checks constraints at each step, and
 * ranks options by safety margin. Produces a list of derate options sorted safest-first.
 * </p>
 *
 * <p>
 * Required query parameters:
 * </p>
 * <ul>
 * <li>{@code currentFlowRate} — current operating flow rate (double)</li>
 * <li>{@code minFlowRate} — minimum flow rate to consider (double)</li>
 * <li>{@code flowRateUnit} — the unit string (optional, default "kg/hr")</li>
 * <li>{@code feedStreamName} — the feed stream name (optional, default "feed")</li>
 * <li>{@code steps} — number of rate steps to evaluate (optional, default 5)</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DerateOptionsWorkflow implements QueryWorkflow {
  private static final Logger logger = LogManager.getLogger(DerateOptionsWorkflow.class);

  /**
   * Creates a new derate options workflow.
   */
  public DerateOptionsWorkflow() {}

  /** {@inheritDoc} */
  @Override
  public boolean canHandle(OperatorQuery query) {
    return query.getQueryType() == OperatorQuery.QueryType.DERATE_OPTIONS;
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringRecommendation execute(ProcessSystem process, OperatorQuery query,
      OperatingSpecification spec) {
    double currentRate = query.getParameterAsDouble("currentFlowRate", -1.0);
    double minRate = query.getParameterAsDouble("minFlowRate", -1.0);
    String unit = query.getParameterAsString("flowRateUnit", "kg/hr");
    String feedName = query.getParameterAsString("feedStreamName", "feed");
    int steps = (int) query.getParameterAsDouble("steps", 5.0);

    EngineeringRecommendation.Builder builder =
        EngineeringRecommendation.builder().queryId(query.getQueryId());

    if (currentRate <= 0 || minRate <= 0 || minRate >= currentRate) {
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("Invalid rate range. Provide 'currentFlowRate' > 'minFlowRate' > 0.")
          .confidence(0.0).build();
    }

    // Validate feed stream exists
    Object unit0 = process.getUnit(feedName);
    if (unit0 == null || !(unit0 instanceof StreamInterface)) {
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("Feed stream '" + feedName + "' not found in process model.").confidence(0.0)
          .build();
    }

    // Calculate rate steps
    double stepSize = (currentRate - minRate) / steps;
    List<DerateOption> options = new ArrayList<>();

    for (int i = 0; i <= steps; i++) {
      double testRate = currentRate - (i * stepSize);
      if (testRate < minRate) {
        testRate = minRate;
      }

      // Clone process for each test
      ProcessSystem testProcess;
      try {
        testProcess = process.copy();
      } catch (Exception e) {
        logger.warn("Failed to clone for rate {}: {}", testRate, e.getMessage());
        continue;
      }

      // Set rate and run
      Object testUnit = testProcess.getUnit(feedName);
      if (testUnit instanceof StreamInterface) {
        ((StreamInterface) testUnit).setFlowRate(testRate, unit);
      }

      try {
        testProcess.run();
      } catch (Exception e) {
        logger.warn("Simulation failed at rate {}: {}", testRate, e.getMessage());
        continue;
      }

      // Evaluate constraints
      Map<String, Double> utilizations = testProcess.getCapacityUtilizationSummary();
      BottleneckResult bottleneck = testProcess.findBottleneck();

      double worstMargin = 100.0;
      String limitingEquip = "none";
      Map<String, ConstraintStatus> statuses = new HashMap<>();

      for (Map.Entry<String, Double> entry : utilizations.entrySet()) {
        double util = entry.getValue();
        double margin = 100.0 - util;
        ConstraintStatus status = util > 100 ? ConstraintStatus.FAIL
            : util > 90 ? ConstraintStatus.WARN : ConstraintStatus.PASS;
        statuses.put(entry.getKey(), status);

        if (margin < worstMargin) {
          worstMargin = margin;
          limitingEquip = entry.getKey();
        }
      }

      if (bottleneck.hasBottleneck()) {
        limitingEquip = bottleneck.getEquipmentName();
      }

      String riskLevel;
      if (worstMargin > 20.0) {
        riskLevel = "Low";
      } else if (worstMargin > 10.0) {
        riskLevel = "Medium";
      } else if (worstMargin > 0) {
        riskLevel = "High";
      } else {
        riskLevel = "Exceeded";
      }

      options
          .add(new DerateOption(testRate, unit, worstMargin, riskLevel, limitingEquip, statuses));
    }

    // Sort by safety margin (safest first = highest margin)
    Collections.sort(options, new java.util.Comparator<DerateOption>() {
      @Override
      public int compare(DerateOption a, DerateOption b) {
        return Double.compare(b.getSafetyMarginPercent(), a.getSafetyMarginPercent());
      }
    });

    for (DerateOption opt : options) {
      builder.addDerateOption(opt);
    }

    // Find the recommended option (highest rate with adequate margin)
    DerateOption recommended = null;
    for (DerateOption opt : options) {
      if (opt.getSafetyMarginPercent() >= 10.0) {
        if (recommended == null || opt.getFlowRate() > recommended.getFlowRate()) {
          recommended = opt;
        }
      }
    }

    StringBuilder summary = new StringBuilder();
    summary.append("Evaluated ").append(options.size()).append(" derate options from ")
        .append(String.format("%.1f", currentRate)).append(" to ")
        .append(String.format("%.1f", minRate)).append(" ").append(unit).append(". ");

    if (recommended != null) {
      summary.append("Recommended: ").append(String.format("%.1f", recommended.getFlowRate()))
          .append(" ").append(unit).append(" (")
          .append(String.format("%.1f%%", recommended.getSafetyMarginPercent())).append(" margin, ")
          .append(recommended.getRiskLevel()).append(" risk).");
      builder.verdict(Verdict.FEASIBLE).addFinding(new Finding(
          "Recommended derate to " + String.format("%.1f", recommended.getFlowRate()) + " " + unit,
          Severity.INFO));
    } else {
      summary.append("No option found with >= 10% safety margin.");
      builder.verdict(Verdict.FEASIBLE_WITH_WARNINGS)
          .addFinding(new Finding("All derate options have tight margins", Severity.WARNING));
    }

    builder.summary(summary.toString()).confidence(0.85)
        .addAssumption("Steady-state conditions at each rate point")
        .addAssumption("Equipment turndown characteristics as modeled")
        .addLimitation("Transition dynamics between rates not evaluated")
        .addLimitation("Minimum stable throughput limits may not be captured");

    return builder.build();
  }

  /** {@inheritDoc} */
  @Override
  public String getWorkflowId() {
    return "derate-options";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Finds safe derate options by sweeping flow rates, checking constraints at each "
        + "step, and ranking by safety margin.";
  }
}
