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
import neqsim.process.processmodel.ProcessSystem;

/**
 * Workflow for checking current product specifications against defined limits.
 *
 * <p>
 * Runs the simulation, extracts product quality values, and checks them against the
 * {@link OperatingSpecification}. Useful for answering questions like "Are we meeting sales gas
 * spec right now?"
 * </p>
 *
 * <p>
 * Optional query parameters:
 * </p>
 * <ul>
 * <li>{@code productValues} — a Map of spec name to measured value (optional; if provided, uses
 * these instead of running the simulation)</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProductSpecCheckWorkflow implements QueryWorkflow {
  private static final Logger logger = LogManager.getLogger(ProductSpecCheckWorkflow.class);

  /**
   * Creates a new product spec check workflow.
   */
  public ProductSpecCheckWorkflow() {}

  /** {@inheritDoc} */
  @Override
  public boolean canHandle(OperatorQuery query) {
    return query.getQueryType() == OperatorQuery.QueryType.PRODUCT_SPEC_CHECK;
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public EngineeringRecommendation execute(ProcessSystem process, OperatorQuery query,
      OperatingSpecification spec) {
    EngineeringRecommendation.Builder builder =
        EngineeringRecommendation.builder().queryId(query.getQueryId());

    // Run simulation to get current state
    try {
      process.run();
    } catch (Exception e) {
      logger.warn("Simulation failed: {}", e.getMessage());
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("Simulation failed: " + e.getMessage()).confidence(0.3).build();
    }

    // Get product values — either from query parameters or from currentConditions
    Map<String, Double> productValues;
    Object valuesObj = query.getParameter("productValues");
    if (valuesObj instanceof Map) {
      // Convert Map<String, Object> to Map<String, Double>
      Map<String, Object> rawMap = (Map<String, Object>) valuesObj;
      productValues = new java.util.HashMap<>();
      for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
        if (entry.getValue() instanceof Number) {
          productValues.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
        }
      }
    } else {
      productValues = query.getCurrentConditions();
    }

    if (productValues.isEmpty()) {
      return builder.verdict(Verdict.REQUIRES_FURTHER_ANALYSIS)
          .summary("No product values available. Provide 'productValues' map or "
              + "'currentConditions' in the query.")
          .confidence(0.0).build();
    }

    // Check against specifications
    java.util.List<SpecCheckResult> results = spec.checkValues(productValues);

    int passCount = 0;
    int warnCount = 0;
    int failCount = 0;

    for (SpecCheckResult result : results) {
      ConstraintStatus cs = result.getStatus();
      double margin = 0.0;

      if (!Double.isNaN(result.getMaxLimit()) && !Double.isNaN(result.getMeasuredValue())) {
        if (result.getMaxLimit() != 0) {
          margin =
              ((result.getMaxLimit() - result.getMeasuredValue()) / Math.abs(result.getMaxLimit()))
                  * 100;
        }
      } else if (!Double.isNaN(result.getMinLimit()) && !Double.isNaN(result.getMeasuredValue())) {
        if (result.getMinLimit() != 0) {
          margin =
              ((result.getMeasuredValue() - result.getMinLimit()) / Math.abs(result.getMinLimit()))
                  * 100;
        }
      }

      builder.addConstraintCheck(
          new ConstraintCheckResult(result.getSpecName(), cs, margin, result.getMeasuredValue(),
              Double.isNaN(result.getMaxLimit()) ? result.getMinLimit() : result.getMaxLimit(),
              result.getUnit()));

      if (cs == ConstraintStatus.FAIL) {
        failCount++;
        builder.addFinding(
            new Finding("Spec '" + result.getSpecName() + "' failed: " + result.getMessage(),
                Severity.ERROR, result.getSpecName(), result.getMeasuredValue(),
                Double.isNaN(result.getMaxLimit()) ? result.getMinLimit() : result.getMaxLimit(),
                result.getUnit()));
      } else if (cs == ConstraintStatus.WARN) {
        warnCount++;
        builder.addFinding(
            new Finding("Spec '" + result.getSpecName() + "' warning: " + result.getMessage(),
                Severity.WARNING));
      } else {
        passCount++;
      }
    }

    // Determine verdict
    Verdict verdict;
    if (failCount > 0) {
      verdict = Verdict.NOT_FEASIBLE;
    } else if (warnCount > 0) {
      verdict = Verdict.FEASIBLE_WITH_WARNINGS;
    } else {
      verdict = Verdict.FEASIBLE;
    }

    StringBuilder summary = new StringBuilder();
    summary.append("Product specification check: ").append(passCount).append(" pass, ")
        .append(warnCount).append(" warn, ").append(failCount).append(" fail out of ")
        .append(results.size()).append(" specifications checked.");

    builder.verdict(verdict).summary(summary.toString()).confidence(0.90)
        .addAssumption("Product values represent current steady-state conditions")
        .addLimitation("Measurement instrument uncertainty not included in spec check");

    return builder.build();
  }

  /** {@inheritDoc} */
  @Override
  public String getWorkflowId() {
    return "product-spec-check";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Checks current product quality values against defined operating specifications.";
  }
}
