package neqsim.process.engineering.safety;

import neqsim.process.engineering.SafetyFunctionDesign;
import neqsim.process.engineering.production.EngineeringBenchmarkSuite;
import neqsim.process.engineering.production.EngineeringValidationBenchmark;
import neqsim.process.engineering.production.EngineeringValidationBenchmark.SourceClass;
import neqsim.process.safety.risk.sis.LOPAResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;

/**
 * Versioned safety verification benchmarks backed by NeqSim's production-readiness benchmark model.
 *
 * <p>
 * Expected values and their provenance are supplied externally. A suite qualifies only when every required method
 * passes and the caller declares a non-regression source plus an independent review record. The class does not assert
 * that those declarations are true; document control and accountable reviewers remain responsible for them.
 * </p>
 */
public final class SafetyVerificationBenchmarkSuite {
  public static final String SIF_PFD_METHOD = "neqsim.safety.sif-pfd-screen";
  public static final String LOPA_FREQUENCY_METHOD = "neqsim.safety.lopa-frequency";
  public static final String DYNAMIC_RESPONSE_METHOD = "neqsim.safety.dynamic-response";
  public static final String METHOD_VERSION = "1.0";

  private final EngineeringBenchmarkSuite suite;
  private final SourceClass sourceClass;
  private final String sourceReference;
  private final String datasetRevision;
  private final String independentReviewRecord;

  /**
   * Creates a three-method safety benchmark suite.
   *
   * @param suiteId controlled benchmark-suite identifier
   * @param suiteRevision suite revision
   * @param sourceClass provenance class for the externally supplied expected values
   * @param sourceReference controlled reference to the independent calculation or published case
   * @param datasetRevision source dataset revision
   * @param independentReviewRecord controlled independent-review record, or blank for non-qualifying evidence
   */
  public SafetyVerificationBenchmarkSuite(String suiteId, String suiteRevision, SourceClass sourceClass,
      String sourceReference, String datasetRevision, String independentReviewRecord) {
    if (sourceClass == null) {
      throw new IllegalArgumentException("sourceClass is required");
    }
    this.sourceClass = sourceClass;
    this.sourceReference = requireText(sourceReference, "sourceReference");
    this.datasetRevision = requireText(datasetRevision, "datasetRevision");
    this.independentReviewRecord = independentReviewRecord == null ? "" : independentReviewRecord.trim();
    suite = new EngineeringBenchmarkSuite(suiteId, suiteRevision).requireMethod(methodKey(SIF_PFD_METHOD))
        .requireMethod(methodKey(LOPA_FREQUENCY_METHOD)).requireMethod(methodKey(DYNAMIC_RESPONSE_METHOD));
  }

  /** Adds a benchmark comparing a SIF screening result to an externally calculated PFDavg. */
  public SafetyVerificationBenchmarkSuite addSifPfd(String caseId, SafetyFunctionDesign design, double expectedPfd,
      double absoluteTolerance, double relativeTolerance) {
    if (design == null) {
      throw new IllegalArgumentException("design is required");
    }
    suite.add(benchmark(caseId, SIF_PFD_METHOD)
        .check("pfdAverage", expectedPfd, design.calculatePfdAverage(), "1", absoluteTolerance, relativeTolerance)
        .build());
    return this;
  }

  /** Adds a benchmark comparing LOPA arithmetic to an externally calculated residual frequency. */
  public SafetyVerificationBenchmarkSuite addLopaFrequency(String caseId, LOPAResult lopa, double expectedPerYear,
      double absoluteTolerance, double relativeTolerance) {
    if (lopa == null) {
      throw new IllegalArgumentException("lopa is required");
    }
    suite.add(benchmark(caseId, LOPA_FREQUENCY_METHOD).check("mitigatedFrequency", expectedPerYear,
        lopa.getMitigatedFrequency(), "1/year", absoluteTolerance, relativeTolerance).build());
    return this;
  }

  /** Adds a benchmark comparing an executed scenario criterion response time to an external reference. */
  public SafetyVerificationBenchmarkSuite addDynamicResponse(String caseId, DynamicSafetyScenarioResult result,
      String criterionId, double expectedSeconds, double absoluteTolerance, double relativeTolerance) {
    if (result == null) {
      throw new IllegalArgumentException("dynamic result is required");
    }
    DynamicSafetyScenarioResult.CriterionResult criterion = result.getCriterionResults().get(criterionId);
    if (criterion == null || criterion.getFirstSatisfiedSeconds() == null) {
      throw new IllegalArgumentException("criterion must exist and have a satisfied response time");
    }
    suite.add(benchmark(caseId, DYNAMIC_RESPONSE_METHOD).check("firstSatisfiedSeconds", expectedSeconds,
        criterion.getFirstSatisfiedSeconds().doubleValue(), "s", absoluteTolerance, relativeTolerance).build());
    return this;
  }

  /** @return standard production-readiness benchmark report */
  public EngineeringBenchmarkSuite.Report evaluate() {
    return suite.evaluate();
  }

  private EngineeringValidationBenchmark.Builder benchmark(String caseId, String methodId) {
    return EngineeringValidationBenchmark.builder(requireText(caseId, "caseId"), methodId, METHOD_VERSION)
        .source(sourceClass, sourceReference, datasetRevision).independentReview(independentReviewRecord);
  }

  private static String methodKey(String methodId) {
    return methodId + "@" + METHOD_VERSION;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
