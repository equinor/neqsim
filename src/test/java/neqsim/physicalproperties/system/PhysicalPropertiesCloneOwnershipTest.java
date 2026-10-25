package neqsim.physicalproperties.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Guards transport-model ownership when phase roles and cloned column candidates change.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PhysicalPropertiesCloneOwnershipTest extends neqsim.NeqSimTest {
  /**
   * Creates an initialized EOS phase with all physical-property families configured.
   *
   * @return phase used as the clone source
   */
  private PhaseInterface createPhase() {
    SystemSrkEos fluid = new SystemSrkEos(330.0, 40.0);
    fluid.addComponent("methane", 0.3);
    fluid.addComponent("n-decane", 0.7);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(2);
    fluid.initPhysicalProperties();
    return fluid.getPhase(0);
  }

  /**
   * Checks every model's owning physical-property family without initializing away stale links.
   *
   * @param properties expected owner of its calculation methods
   * @throws IllegalAccessException if a model field cannot be inspected
   */
  private void assertMethodOwners(PhysicalProperties properties) throws IllegalAccessException {
    Object[] methods = {properties.densityCalc, properties.viscosityCalc, properties.conductivityCalc,
        properties.diffusivityCalc};
    for (Object method : methods) {
      for (Class<?> type = method.getClass(); type != null; type = type.getSuperclass()) {
        for (Field field : type.getDeclaredFields()) {
          if (field.getType() == PhysicalProperties.class) {
            field.setAccessible(true);
            assertSame(properties, field.get(method), type.getSimpleName() + "." + field.getName());
          }
        }
      }
    }
  }

  /**
   * Inactive families must belong to the new phase before any later property initialization.
   *
   * @param model configured physical-property package
   * @throws IllegalAccessException if a model field cannot be inspected
   */
  @ParameterizedTest
  @EnumSource(PhysicalPropertyModel.class)
  void everyFamilyBelongsToClonedPhase(PhysicalPropertyModel model) throws IllegalAccessException {
    PhaseInterface source = createPhase();
    source.setPhysicalPropertyModel(model);
    PhaseInterface cloned = source.clone();
    for (PhaseType role : new PhaseType[] {PhaseType.GAS, PhaseType.OIL, PhaseType.AQUEOUS, PhaseType.SOLID}) {
      source.setType(role);
      cloned.setType(role);
      PhysicalProperties original = source.getPhysicalProperties();
      PhysicalProperties copied = cloned.getPhysicalProperties();
      assertNotSame(original, copied);
      assertSame(cloned, copied.getPhase());
      assertSame(source, original.getPhase());
      assertEquals(original.viscosityCalc.getClass(), copied.viscosityCalc.getClass());
      assertNotSame(original.viscosityCalc, copied.viscosityCalc);
      assertMethodOwners(copied);
      assertMethodOwners(original);
      assertMethodOwners(original.clone());
    }
  }

  /**
   * Repeated cloned phase-role changes must not retain a growing chain of prior phase states.
   *
   * @throws Exception if serialization or model-owner inspection fails
   */
  @Test
  void repeatedRoleChangesHaveBoundedSerializedState() throws Exception {
    PhaseInterface phase = createPhase();
    // Populate lazy model state and complete both roles before measuring retained history.
    for (int iteration = 0; iteration < 4; iteration++) {
      phase = phase.clone();
      phase.setType(iteration % 2 == 0 ? PhaseType.OIL : PhaseType.GAS);
      phase.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    }
    int initialSize = serialized(phase).length;
    for (int iteration = 0; iteration < 40; iteration++) {
      phase = phase.clone();
      phase.setType(iteration % 2 == 0 ? PhaseType.OIL : PhaseType.GAS);
      phase.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    }
    byte[] bytes = serialized(phase);
    assertTrue(bytes.length <= initialSize + 1024,
        "Serialized phase grew from " + initialSize + " to " + bytes.length + " bytes");
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      PhaseInterface restored = (PhaseInterface) input.readObject();
      assertSame(restored, restored.getPhysicalProperties().getPhase());
      assertMethodOwners(restored.getPhysicalProperties());
    }
  }

  /**
   * Serializes one phase with the same object-graph traversal used for column candidate copies.
   *
   * @param phase phase to serialize
   * @return serialized bytes
   * @throws Exception if serialization fails
   */
  private byte[] serialized(PhaseInterface phase) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(phase);
    }
    return bytes.toByteArray();
  }
}
