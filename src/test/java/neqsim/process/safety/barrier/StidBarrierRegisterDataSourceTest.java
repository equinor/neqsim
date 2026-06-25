package neqsim.process.safety.barrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StidBarrierRegisterDataSource}, which builds a barrier register from normalized STID JSON.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class StidBarrierRegisterDataSourceTest {

  private static final String SAMPLE_JSON = "{" + "\"registerId\": \"REG-AAA\","
      + "\"name\": \"Example installation barrier register\"," + "\"installationCode\": \"AAA\","
      + "\"safetyCriticalElements\": [" + "  {" + "    \"id\": \"SCE-PSD\"," + "    \"tag\": \"PSD\","
      + "    \"name\": \"Process shutdown\"," + "    \"type\": \"INSTRUMENTED_FUNCTION\","
      + "    \"equipmentTags\": [\"VA-2001\"]," + "    \"barriers\": ["
      + "      {\"id\": \"B-PSD-2001\", \"name\": \"PSD on separator\", \"type\": \"PREVENTION\","
      + "       \"status\": \"AVAILABLE\", \"pfd\": 0.01, \"equipmentTags\": [\"VA-2001\"]}" + "    ]" + "  }" + "],"
      + "\"barriers\": [" + "  {" + "    \"id\": \"B-PSV-2001\"," + "    \"name\": \"PSV on inlet separator\","
      + "    \"type\": \"mitigation\"," + "    \"status\": \"out of service\","
      + "    \"equipmentTags\": [\"VA-2001\"]," + "    \"hazardIds\": [\"H-OVP-1\"]," + "    \"evidence\": ["
      + "      {\"documentId\": \"PSV-LIST\", \"revision\": \"03\", \"excerpt\": \"PSV-2001 set 75 barg\"}" + "    ]"
      + "  }" + "]" + "}";

  @Test
  void readsInstallationCode() {
    assertEquals("AAA", new StidBarrierRegisterDataSource(SAMPLE_JSON).getInstallationCode());
  }

  @Test
  void buildsRegisterWithIdAndName() {
    BarrierRegister register = new StidBarrierRegisterDataSource(SAMPLE_JSON).read();
    assertNotNull(register);
    assertEquals("REG-AAA", register.getRegisterId());
    assertEquals("Example installation barrier register", register.getName());
  }

  @Test
  void buildsSafetyCriticalElementWithBarrier() {
    BarrierRegister register = new StidBarrierRegisterDataSource(SAMPLE_JSON).read();
    SafetyCriticalElement sce = register.getSafetyCriticalElement("SCE-PSD");
    assertNotNull(sce);
    assertEquals(SafetyCriticalElement.ElementType.INSTRUMENTED_FUNCTION, sce.getType());
    assertTrue(sce.getEquipmentTags().contains("VA-2001"));
    SafetyBarrier sceBarrier = sce.getBarrier("B-PSD-2001");
    assertNotNull(sceBarrier);
    assertEquals(SafetyBarrier.BarrierStatus.AVAILABLE, sceBarrier.getStatus());
    assertEquals(0.01, sceBarrier.getPfd(), 1.0e-9);
  }

  @Test
  void parsesTolerantTypeAndStatusTokens() {
    BarrierRegister register = new StidBarrierRegisterDataSource(SAMPLE_JSON).read();
    SafetyBarrier psv = register.getBarrier("B-PSV-2001");
    assertNotNull(psv);
    assertEquals(SafetyBarrier.BarrierType.MITIGATION, psv.getType());
    assertEquals(SafetyBarrier.BarrierStatus.OUT_OF_SERVICE, psv.getStatus());
  }

  @Test
  void evidenceInheritsRegisterInstallationCode() {
    BarrierRegister register = new StidBarrierRegisterDataSource(SAMPLE_JSON).read();
    SafetyBarrier psv = register.getBarrier("B-PSV-2001");
    List<DocumentEvidence> evidence = psv.getEvidence();
    assertFalse(evidence.isEmpty());
    DocumentEvidence first = evidence.get(0);
    assertEquals("AAA", first.getInstallationCode());
    assertEquals("PSV-LIST", first.getDocumentId());
    assertTrue(first.isTraceable());
  }

  @Test
  void emptySourceProducesEmptyRegister() {
    BarrierRegister register = new StidBarrierRegisterDataSource("{}").read();
    assertNotNull(register);
    assertTrue(register.getSafetyCriticalElements().isEmpty());
    assertTrue(register.getBarriers().isEmpty());
  }
}
