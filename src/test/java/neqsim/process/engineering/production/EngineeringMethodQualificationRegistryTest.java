package neqsim.process.engineering.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class EngineeringMethodQualificationRegistryTest {

  @Test
  void qualifiesExactVersionInsideControlledServiceEnvelope() {
    EngineeringMethodQualification qualification = completeQualification();
    EngineeringBenchmarkSuite.Report benchmarks = qualifyingBenchmark();
    EngineeringMethodQualificationRegistry registry = new EngineeringMethodQualificationRegistry("project-methods",
        "A").register(qualification);

    EngineeringMethodQualificationRegistry.Result result = registry.assess("gas-method@2.1", feedService(80.0),
        benchmarks);

    assertEquals(EngineeringMethodQualificationRegistry.Status.QUALIFIED_FOR_SERVICE, result.getStatus());
    assertTrue(result.isQualifiedForService());
    assertTrue(result.isExecutionPermitted());
    assertTrue(qualification.isIndustrialQualificationComplete());
    assertTrue(registry.toMap().toString().contains("engineering_method_qualification_registry.v1"));
  }

  @Test
  void blocksProhibitedExtrapolationAndRetainsExplicitVerdict() {
    EngineeringMethodQualificationRegistry registry = new EngineeringMethodQualificationRegistry("project-methods",
        "A").register(completeQualification());

    EngineeringMethodQualificationRegistry.Result result = registry.assess("gas-method@2.1", feedService(180.0),
        qualifyingBenchmark());

    assertEquals(EngineeringMethodQualificationRegistry.Status.OUTSIDE_QUALIFIED_ENVELOPE, result.getStatus());
    assertFalse(result.isQualifiedForService());
    assertFalse(result.isExecutionPermitted());
    assertTrue(result.getFindings().toString().contains("outside the inclusive range"));
  }

  @Test
  void distinguishesMissingContextUseAndBenchmarkEvidence() {
    EngineeringMethodQualificationRegistry registry = new EngineeringMethodQualificationRegistry("project-methods",
        "A").register(completeQualification());
    EngineeringMethodServiceContext missing = new EngineeringMethodServiceContext(
        EngineeringMethodQualification.IntendedUse.FEED_SUPPORT).numericValue("pressure", 80.0, "bara");
    EngineeringMethodServiceContext operations = new EngineeringMethodServiceContext(
        EngineeringMethodQualification.IntendedUse.OPERATIONS_ADVISORY).numericValue("pressure", 80.0, "bara")
            .numericValue("temperature", 300.0, "K").categoricalValue("phase", "GAS")
            .suppliedInput("compositionBasis");

    assertEquals(EngineeringMethodQualificationRegistry.Status.INSUFFICIENT_SERVICE_CONTEXT,
        registry.assess("gas-method@2.1", missing, qualifyingBenchmark()).getStatus());
    assertEquals(EngineeringMethodQualificationRegistry.Status.USE_NOT_QUALIFIED,
        registry.assess("gas-method@2.1", operations, qualifyingBenchmark()).getStatus());
    assertEquals(EngineeringMethodQualificationRegistry.Status.MISSING_INDEPENDENT_BENCHMARK,
        registry.assess("gas-method@2.1", feedService(80.0), null).getStatus());
    assertEquals(EngineeringMethodQualificationRegistry.Status.UNREGISTERED_METHOD,
        registry.assess("gas-method@9.9", feedService(80.0), qualifyingBenchmark()).getStatus());
  }

  private EngineeringMethodQualification completeQualification() {
    EngineeringMethodApplicabilityEnvelope envelope = new EngineeringMethodApplicabilityEnvelope(
        "gas-method-envelope", "A").requireInput("compositionBasis").numericRange("pressure", "bara", 1.0, 150.0)
            .numericRange("temperature", "K", 250.0, 400.0).allowedValues("phase", "GAS")
            .knownLimitation("Not qualified for two-phase or reacting service")
            .uncertaintyBasis("INDEPENDENT-CALC-21 uncertainty budget, revision A").prohibitExtrapolation(true);
    return new EngineeringMethodQualification("gas-method", "2.1",
        EngineeringMethodQualification.Level.PROJECT_QUALIFIED).addStandardReference("PROJECT-METHOD-BASIS-A")
            .addApplicabilityLimit("Use the structured envelope gas-method-envelope revision A")
            .addEvidenceReference("INDEPENDENT-CALC-21").approve("Technical authority / APPROVAL-21")
            .applicabilityEnvelope(envelope).qualifyFor(EngineeringMethodQualification.IntendedUse.FEED_SUPPORT)
            .addAcceptanceCriterion("pressureDrop", "bar", 0.05, 0.01);
  }

  private EngineeringMethodServiceContext feedService(double pressure) {
    return new EngineeringMethodServiceContext(EngineeringMethodQualification.IntendedUse.FEED_SUPPORT)
        .numericValue("pressure", pressure, "bara").numericValue("temperature", 300.0, "K")
        .categoricalValue("phase", "GAS").suppliedInput("compositionBasis");
  }

  private EngineeringBenchmarkSuite.Report qualifyingBenchmark() {
    return new EngineeringBenchmarkSuite("gas-method-suite", "A").requireMethod("gas-method@2.1")
        .add(EngineeringValidationBenchmark.builder("gas-case-1", "gas-method", "2.1")
            .source(EngineeringValidationBenchmark.SourceClass.INDEPENDENT_CALCULATION, "INDEPENDENT-CALC-21", "A")
            .independentReview("Independent checker / REVIEW-21")
            .check("pressureDrop", 1.0, 1.005, "bar", 0.05, 0.01).build())
        .evaluate();
  }
}
