package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;

/** Regression tests for Pitzer parameter coverage state and object independence. */
public class PitzerParameterCoverageTest extends neqsim.NeqSimTest {
  /** Verify that a newly active ion invalidates the cached topology audit. */
  @Test
  public void testChangedStateInvalidatesCoverageTopology() {
    SystemInterface system = createMixedSystem(1.0e-20);
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);
    phase.loadParametersFromDatabase();
    assertTrue(phase.getPitzerParameterCoverage().isComplete());

    phase.getComponent("K+").setx(0.01);
    phase.getComponent("K+").setNumberOfMolesInPhase(50.0);
    PitzerParameterCoverage changedCoverage = phase.getPitzerParameterCoverage();

    assertFalse(changedCoverage.isComplete());
    assertTrue(changedCoverage.getMissingThetaPairs().contains("K+|Na+"));
    assertTrue(changedCoverage.getMissingPsiTuples().contains("K+|Na+|Cl-"));
  }

  /** Verify that Pitzer arrays and definition state are independent after cloning. */
  @Test
  public void testCloneOwnsParameterDefinitions() {
    PhasePitzer original = createMixedPhase(0.5);
    PhasePitzer clone = original.clone();
    int sodium = clone.getComponent("Na+").getComponentNumber();
    int potassium = clone.getComponent("K+").getComponentNumber();
    int chloride = clone.getComponent("Cl-").getComponentNumber();

    clone.setTheta(sodium, potassium, -0.012);
    clone.setPsi(sodium, potassium, chloride, -0.0018);

    assertNotSame(original.getPitzerParameterCoverage(), clone.getPitzerParameterCoverage());
    assertFalse(original.getPitzerParameterCoverage().isComplete());
    assertTrue(clone.getPitzerParameterCoverage().isComplete());
    assertTrue(Math.abs(original.getThetaij(sodium, potassium)) < 1.0e-20);
    assertTrue(Math.abs(clone.getThetaij(sodium, potassium) + 0.012) < 1.0e-20);
  }

  /** Verify that serialized parameter definitions recompute a complete diagnostic on read. */
  @Test
  public void testSerializationPreservesDefinitionsAndDropsCache() throws Exception {
    PhasePitzer phase = createMixedPhase(0.5);
    int sodium = phase.getComponent("Na+").getComponentNumber();
    int potassium = phase.getComponent("K+").getComponentNumber();
    int chloride = phase.getComponent("Cl-").getComponentNumber();
    phase.setParameterDatasetId("test-explicit-mixed-v1");
    phase.setTheta(sodium, potassium, 0.0);
    phase.setPsi(sodium, potassium, chloride, 0.0);
    assertTrue(phase.getPitzerParameterCoverage().isComplete());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(phase);
    }
    PhasePitzer restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (PhasePitzer) input.readObject();
    }

    assertTrue(restored.getPitzerParameterCoverage().isComplete());
    assertTrue("test-explicit-mixed-v1".equals(restored.getParameterDatasetId()));
  }

  /** Verify the unequal-charge fast-path cache follows component topology and clone state. */
  @Test
  public void testUnequalChargeTopologyFastPath() {
    SystemInterface equalChargeSystem = createMixedSystem(0.5);
    PhasePitzer equalChargePhase = (PhasePitzer) equalChargeSystem.getPhase(1);
    assertFalse(equalChargePhase.hasUnequalChargeSameSignPair());

    SystemInterface unequalChargeSystem = new SystemPitzer(298.15, 1.01325);
    unequalChargeSystem.addComponent("water", 55.508);
    unequalChargeSystem.addComponent("Na+", 0.2);
    unequalChargeSystem.addComponent("Ca++", 0.4);
    unequalChargeSystem.addComponent("Cl-", 1.0);
    PhasePitzer unequalChargePhase = (PhasePitzer) unequalChargeSystem.getPhase(1);
    assertTrue(unequalChargePhase.hasUnequalChargeSameSignPair());
    assertTrue(unequalChargePhase.clone().hasUnequalChargeSameSignPair());
  }

  /**
   * Creates a water/Na/K/Cl phase and loads its binary database parameters.
   *
   * @param potassiumMoles potassium amount in mol
   * @return initialized Pitzer phase
   */
  private static PhasePitzer createMixedPhase(double potassiumMoles) {
    SystemInterface system = createMixedSystem(potassiumMoles);
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);
    phase.loadParametersFromDatabase();
    return phase;
  }

  /**
   * Creates an initialized water/Na/K/Cl system.
   *
   * @param potassiumMoles potassium amount in mol
   * @return initialized Pitzer system
   */
  private static SystemInterface createMixedSystem(double potassiumMoles) {
    SystemPitzer system = new SystemPitzer(298.15, 1.01325);
    system.useLegacyPitzerParameters();
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("K+", potassiumMoles);
    system.addComponent("Cl-", 1.0 + potassiumMoles);
    system.setMixingRule("classic");
    system.init(0);
    return system;
  }
}
