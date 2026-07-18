package neqsim.process.engineering.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import neqsim.process.engineering.SafetyFunctionDesign;
import org.junit.jupiter.api.Test;

/** Tests deterministic and seeded uncertainty propagation for SIF reliability evidence. */
class SafetyFunctionReliabilityStudyTest {

  @Test
  void constantInputsReproduceAnalyticalPfd() {
    SafetyFunctionDesign design = design();

    SafetyFunctionReliabilityStudy.Result result = SafetyFunctionReliabilityStudy.run(design,
        Collections.<SafetyFunctionReliabilityStudy.SubsystemUncertainty>emptyList(), 100, 42L);

    assertEquals(design.calculatePfdAverage(), result.getP10PfdAverage(), 1.0e-15);
    assertEquals(design.calculatePfdAverage(), result.getP50PfdAverage(), 1.0e-15);
    assertEquals(design.calculatePfdAverage(), result.getP90PfdAverage(), 1.0e-15);
    assertEquals(1.0, result.getTargetMetProbability(), 1.0e-15);
  }

  @Test
  void seededStudyIsRepeatableAndOrdersPfdPercentiles() {
    SafetyFunctionReliabilityStudy.SubsystemUncertainty uncertainty = new SafetyFunctionReliabilityStudy.SubsystemUncertainty(
        "pressure transmitters")
            .setFailureRateFactor(SafetyFunctionReliabilityStudy.TriangularDistribution.of(0.5, 1.0, 2.0))
            .setProofTestIntervalFactor(SafetyFunctionReliabilityStudy.TriangularDistribution.of(0.5, 1.0, 2.0))
            .setDiagnosticCoverage(SafetyFunctionReliabilityStudy.TriangularDistribution.of(0.0, 0.1, 0.2));

    SafetyFunctionReliabilityStudy.Result first = SafetyFunctionReliabilityStudy.run(design(),
        Collections.singletonList(uncertainty), 2000, 2486L);
    SafetyFunctionReliabilityStudy.Result second = SafetyFunctionReliabilityStudy.run(design(),
        Collections.singletonList(uncertainty), 2000, 2486L);

    assertEquals(first.getP10PfdAverage(), second.getP10PfdAverage(), 0.0);
    assertEquals(first.getP50PfdAverage(), second.getP50PfdAverage(), 0.0);
    assertEquals(first.getP90PfdAverage(), second.getP90PfdAverage(), 0.0);
    assertTrue(first.getP10PfdAverage() <= first.getP50PfdAverage());
    assertTrue(first.getP50PfdAverage() <= first.getP90PfdAverage());
    assertTrue(first.getTargetMetProbability() > 0.0 && first.getTargetMetProbability() < 1.0);
    assertEquals(Boolean.TRUE, first.toMap().get("engineeringApprovalRequired"));
  }

  private static SafetyFunctionDesign design() {
    return new SafetyFunctionDesign("SIF-PT-101", "REQ-PT-101", 2)
        .addSubsystem(new SafetyFunctionDesign.Subsystem("pressure transmitters",
            SafetyFunctionDesign.SubsystemType.SENSOR, 1, 1, 1.0e-6, 0.0, 8760.0, 8.0, 0.0));
  }
}
