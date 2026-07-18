package neqsim.process.engineering.safety;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

/** Contract tests for coordinated SIL and dynamic response verification. */
public class NcsSafetyFunctionStudyTest extends NeqSimTest {
  @Test
  void hippsStudyCombinesSilAndDynamicEvidence() {
    SafetyInstrumentedFunction hipps = SafetyInstrumentedFunction.builder().id("SIF-HIPPS-101")
        .name("Inlet HIPPS").description("Prevent downstream overpressure")
        .sil(3).pfd(5.0e-4).testIntervalHours(8760.0).mttr(24.0)
        .protectedEquipment(Arrays.asList("20-VG-001")).initiatingEvent("Blocked outlet")
        .safeState("HIPPS isolation valves closed").category(SafetyInstrumentedFunction.SIFCategory.HIPPS)
        .architecture("2oo3").build();
    Map<String, Object> dynamic = new SafetyFunctionTransientVerification("SIF-HIPPS-101", 63.0, 70.0, 3.0)
        .verify(new double[] {0, 1, 2, 3, 4}, new double[] {60, 63, 66, 68, 67},
            new double[] {100, 100, 60, 10, 0});
    assertTrue(((Boolean) dynamic.get("passed")).booleanValue());

    NcsSafetyFunctionStudy study = new NcsSafetyFunctionStudy("NCS-STUDY").add(hipps, dynamic);
    assertTrue(study.toJson().contains("SIF-HIPPS-101"));
    assertTrue(study.toJson().contains("silVerification"));
    assertTrue(study.toJson().contains("NORSOK S-001"));
    assertFalse(((Boolean) study.toMap().get("fitnessForConstruction")).booleanValue());
  }
}
