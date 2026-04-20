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
import neqsim.process.decisionsupport.OperatingSpecification.SpecCheckResult;
import neqsim.process.decisionsupport.OperatorQuery;
import neqsim.process.decisionsupport.QueryWorkflow;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Workflow for evaluating the impact of a gas quality change on process performance and product
 * specifications.
 *
 * <p>
 * Given new composition data, this workflow: (1) updates the feed stream composition, (2) runs the
 * simulation, (3) checks product specifications, (4) checks equipment constraints, and (5) produces
 * a recommendation.
 * </p>
 *
 * <p>
 * Required query parameters:
 * </p>
 * <ul>
 * <li>{@code feedStreamName} — the name of the feed stream (optional, default "feed")</li>
 * <li>{@code composition} — a Map of component name to mole fraction (required)</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class GasQualityImpactWorkflow implements QueryWorkflow {
  private static final Logger logger = LogManager.getLogger(GasQualityImpactWorkflow.class);

  /**
   * Creates a new gas quality impact workflow.
   */
  public GasQualityImpactWorkflow() {}

  /** {@inheritDoc} */
  @Override
  public boolean canHandle(OperatorQuery query) {
    return query.getQueryType() == OperatorQuery.QueryType.GAS_QUALITY_IMPACT;
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public EngineeringRecommendation execute(ProcessSystem process, OperatorQuery query,
      OperatingSpecification spec) {
    String feedName = query.getParameterAsString("feedStreamName", "feed");

    EngineeringRecommendation.Builder builder =
        EngineeringRecommendation.builder().queryId(query.getQueryId());

    // Get composition from query parameters
    Object compositionObj = query.getParameter("composition");
    if (compositionObj == null || !(compositionObj instanceof Map)) {
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary(
              "Missing 'composition' parameter (expected Map of component name to mole fraction).")
          .confidence(0.0).build();
    }

    Map<String, Object> compositionMap = (Map<String, Object>) compositionObj;

    // Find feed stream
    Object unit0 = process.getUnit(feedName);
    if (unit0 == null || !(unit0 instanceof StreamInterface)) {
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("Feed stream '" + feedName + "' not found in process model.").confidence(0.0)
          .build();
    }

    StreamInterface feedStream = (StreamInterface) unit0;
    SystemInterface fluid = feedStream.getFluid();

    // Update composition
    try {
      int numComponents = fluid.getNumberOfComponents();
      double[] molefracs = new double[numComponents];

      for (int i = 0; i < numComponents; i++) {
        String compName = fluid.getComponent(i).getComponentName();
        Object fracObj = compositionMap.get(compName);
        if (fracObj instanceof Number) {
          molefracs[i] = ((Number) fracObj).doubleValue();
        } else {
          molefracs[i] = fluid.getComponent(i).getz();
        }
      }

      // Normalize
      double sum = 0.0;
      for (double f : molefracs) {
        sum += f;
      }
      if (sum > 0 && Math.abs(sum - 1.0) > 1e-6) {
        for (int i = 0; i < molefracs.length; i++) {
          molefracs[i] /= sum;
        }
        builder.addAssumption(
            "Composition normalized from sum=" + String.format("%.4f", sum) + " to 1.0");
      }

      fluid.setMolarComposition(molefracs);
    } catch (Exception e) {
      logger.warn("Failed to set composition: {}", e.getMessage());
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("Failed to apply new composition: " + e.getMessage()).confidence(0.0).build();
    }

    // Run simulation with new composition
    try {
      process.run();
    } catch (Exception e) {
      logger.warn("Simulation failed with new composition: {}", e.getMessage());
      return builder.verdict(Verdict.NOT_FEASIBLE)
          .summary("Simulation failed with new gas composition: " + e.getMessage())
          .addFinding(
              new Finding("Simulation non-convergence with new composition", Severity.ERROR))
          .confidence(0.5).build();
    }

    // Check product specifications
    Map<String, Double> currentConditions = query.getCurrentConditions();
    java.util.List<SpecCheckResult> specResults = spec.checkValues(currentConditions);

    boolean anySpecFail = false;
    boolean anySpecWarn = false;

    for (SpecCheckResult result : specResults) {
      ConstraintStatus cs;
      if (result.getStatus() == ConstraintStatus.FAIL) {
        cs = ConstraintStatus.FAIL;
        anySpecFail = true;
      } else if (result.getStatus() == ConstraintStatus.WARN) {
        cs = ConstraintStatus.WARN;
        anySpecWarn = true;
      } else {
        cs = ConstraintStatus.PASS;
      }

      double margin = 0.0;
      if (!Double.isNaN(result.getMaxLimit()) && !Double.isNaN(result.getMeasuredValue())) {
        margin = ((result.getMaxLimit() - result.getMeasuredValue()) / result.getMaxLimit()) * 100;
      }

      builder.addConstraintCheck(new ConstraintCheckResult(result.getSpecName(), cs, margin,
          result.getMeasuredValue(), result.getMaxLimit(), result.getUnit()));
    }

    // Check capacity constraints
    boolean overloaded = process.isAnyEquipmentOverloaded();
    Map<String, Double> utilizations = process.getCapacityUtilizationSummary();

    for (Map.Entry<String, Double> entry : utilizations.entrySet()) {
      double util = entry.getValue();
      ConstraintStatus status = util > 100 ? ConstraintStatus.FAIL
          : util > 90 ? ConstraintStatus.WARN : ConstraintStatus.PASS;
      if (util > 100) {
        anySpecFail = true;
      }
      builder.addConstraintCheck(new ConstraintCheckResult(entry.getKey() + " capacity", status,
          100.0 - util, util, 100.0, "%"));
    }

    // Determine verdict
    Verdict verdict;
    if (anySpecFail || overloaded) {
      verdict = Verdict.NOT_FEASIBLE;
    } else if (anySpecWarn) {
      verdict = Verdict.FEASIBLE_WITH_WARNINGS;
    } else {
      verdict = Verdict.FEASIBLE;
    }

    StringBuilder summary = new StringBuilder();
    summary.append("Gas quality change impact assessment: ");
    if (verdict == Verdict.FEASIBLE) {
      summary.append("All product specifications and equipment constraints are met.");
    } else if (verdict == Verdict.FEASIBLE_WITH_WARNINGS) {
      summary.append("Feasible but some specifications are close to limits.");
    } else {
      summary.append("One or more specifications or equipment constraints violated.");
    }

    builder.verdict(verdict).summary(summary.toString()).confidence(0.80)
        .addAssumption("Composition change is instantaneous (no transition period modeled)")
        .addAssumption("Equipment performance curves remain valid for new composition")
        .addLimitation("Hydrate formation conditions not checked")
        .addLimitation("Trace component effects (mercury, NORM) not modeled");

    return builder.build();
  }

  /** {@inheritDoc} */
  @Override
  public String getWorkflowId() {
    return "gas-quality-impact";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Evaluates the impact of a gas quality change on process performance "
        + "and product specifications.";
  }
}
