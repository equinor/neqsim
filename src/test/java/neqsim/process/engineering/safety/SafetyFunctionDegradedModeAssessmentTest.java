package neqsim.process.engineering.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.SafetyFunctionDesign;
import neqsim.process.engineering.safety.SafetyFunctionOperatingMode.ChannelState;
import neqsim.process.engineering.safety.SafetyFunctionOperatingMode.ModeType;
import org.junit.jupiter.api.Test;

/** Tests effective voting and governance findings for bypass and proof-test states. */
class SafetyFunctionDegradedModeAssessmentTest {

  @Test
  void oneBypassedChannelDegradesTwoOutOfThreeToTwoOutOfTwo() {
    SafetyFunctionOperatingMode mode = SafetyFunctionOperatingMode.builder("PT-C bypass", ModeType.DEGRADED)
        .authorizationReference("OPS-BYPASS-2026-014").compensatingMeasure("Continuous pressure watch")
        .duration(2.0, 8.0).channelState("pressure transmitters", 3, ChannelState.BYPASSED)
        .hoursSinceProofTest("pressure transmitters", 100.0).build();

    SafetyFunctionDegradedModeAssessment.Result result = SafetyFunctionDegradedModeAssessment.assess(design(), mode);

    assertTrue(result.isDemandCapable());
    assertFalse(result.isTargetSilClaimPreserved());
    assertEquals("2oo2", result.getSubsystemResults().get(0).getEffectiveArchitecture());
    assertEquals("RESTRICTED_ENGINEERING_REVIEW", result.getVerdict());
  }

  @Test
  void twoUnavailableChannelsCannotMeetTwoOutOfThreeDemand() {
    SafetyFunctionOperatingMode mode = SafetyFunctionOperatingMode.builder("Two channels in repair", ModeType.MAINTENANCE)
        .authorizationReference("MOC-2026-88").compensatingMeasure("Process shutdown")
        .duration(1.0, 2.0).channelState("pressure transmitters", 2, ChannelState.UNDER_REPAIR)
        .channelState("pressure transmitters", 3, ChannelState.BYPASSED)
        .hoursSinceProofTest("pressure transmitters", 100.0).build();

    SafetyFunctionDegradedModeAssessment.Result result = SafetyFunctionDegradedModeAssessment.assess(design(), mode);

    assertFalse(result.isDemandCapable());
    assertEquals("2oo1", result.getSubsystemResults().get(0).getEffectiveArchitecture());
    assertEquals("NOT_DEMAND_CAPABLE", result.getVerdict());
  }

  @Test
  void overdueProofTestInvalidatesSilClaim() {
    SafetyFunctionOperatingMode mode = SafetyFunctionOperatingMode.builder("Normal operation", ModeType.NORMAL)
        .hoursSinceProofTest("pressure transmitters", 9000.0).build();

    SafetyFunctionDegradedModeAssessment.Result result = SafetyFunctionDegradedModeAssessment.assess(design(), mode);

    assertTrue(result.isDemandCapable());
    assertTrue(result.getSubsystemResults().get(0).isProofTestOverdue());
    assertFalse(result.isTargetSilClaimPreserved());
    assertTrue(result.getFindings().get(0).contains("overdue"));
  }

  private static SafetyFunctionDesign design() {
    return new SafetyFunctionDesign("SIF-HP-101", "REQ-HP-101", 2)
        .addSubsystem(new SafetyFunctionDesign.Subsystem("pressure transmitters",
            SafetyFunctionDesign.SubsystemType.SENSOR, 2, 3, 1.0e-6, 0.6, 8760.0, 8.0, 0.05));
  }
}
