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
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.physicalproperties.methods.methodinterface.DiffusivityInterface;
import neqsim.thermo.Fluid;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
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

  /** Adding zero-amount species must allow cloning before physical-property reinitialization. */
  @Test
  void cloneAirAfterAddingZeroAmountComponents() {
    SystemInterface air = new Fluid().create("air");
    air.addComponent("CO2", 0.0);
    air.addComponent("water", 0.0);
    SystemInterface copy = air.clone();
    assertEquals(4, copy.getNumberOfComponents());
    copy.init(0);
    copy.init(2);
    copy.initPhysicalProperties();
    double diffusion = copy.getPhase(0).getPhysicalProperties().getDiffusionCoefficient("CO2", "water");
    assertTrue(Double.isFinite(diffusion) && diffusion > 0.0);
    assertEquals(0.0, air.getComponent("water").getNumberOfmoles(), 0.0);
  }

  /**
   * Check independently mutable diffusion rows, including subclass scratch arrays used by scalar evaluations.
   *
   * @param model diffusion model to clone
   * @throws IllegalAccessException if an instance array cannot be inspected
   */
  @ParameterizedTest
  @ValueSource(strings = {"CSP", "Wilke Lee", "Siddiqi Lucas", "solid"})
  void diffusionCloneOwnsEveryPairRow(String model) throws IllegalAccessException {
    PhysicalProperties properties = createPhase().getPhysicalProperties();
    if ("solid".equals(model)) {
      properties.diffusivityCalc = new neqsim.physicalproperties.methods.solidphysicalproperties.diffusivity.Diffusivity(
          properties);
    } else {
      properties.setDiffusionCoefficientModel(model);
    }
    DiffusivityInterface original = properties.diffusivityCalc;
    double[][] originalPairs = original.calcDiffusionCoefficients(0, 0);
    double before = originalPairs[0][1];
    DiffusivityInterface copied = properties.clone().diffusivityCalc;
    double[][] copiedPairs = copied.calcDiffusionCoefficients(0, 0);
    assertEquals(before, copiedPairs[0][1], 0.0);
    copiedPairs[0][1] = before + 1.0;
    assertEquals(before, original.getMaxwellStefanBinaryDiffusionCoefficient(0, 1), 0.0);

    for (Class<?> type = original.getClass(); type != null; type = type.getSuperclass()) {
      for (Field field : type.getDeclaredFields()) {
        if (field.getType() == double[][].class) {
          field.setAccessible(true);
          double[][] source = (double[][]) field.get(original);
          if (source != null) {
            double[][] clone = (double[][]) field.get(copied);
            for (int row = 0; row < source.length; row++) {
              assertNotSame(source[row], clone[row], field.getName());
            }
          }
        }
      }
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
