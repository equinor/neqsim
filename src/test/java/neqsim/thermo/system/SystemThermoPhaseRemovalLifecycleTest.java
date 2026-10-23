package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

class SystemThermoPhaseRemovalLifecycleTest {
  private static final double[] BETAS = {0.15, 0.35, 0.50};
  private static final PhaseType[] PHASE_TYPES = {PhaseType.GAS, PhaseType.OIL, PhaseType.AQUEOUS};

  @Test
  void phaseRemovalRecyclesOnlyTheReleasedPhysicalSlot() {
    for (boolean keepTotalComposition : new boolean[] {false, true}) {
      for (int removedPhase = 0; removedPhase < 3; removedPhase++) {
        verifyRemovalLifecycle(removedPhase, keepTotalComposition);
      }
    }
  }

  private void verifyRemovalLifecycle(int removedPhase, boolean keepTotalComposition) {
    LifecycleSystem system = new LifecycleSystem(298.15, 10.0);
    system.addComponent("methane", 0.7);
    system.addComponent("n-heptane", 0.3);
    system.addClonedPhase();
    for (int phase = 0; phase < 3; phase++) {
      system.setBeta(phase, BETAS[phase]);
      system.setPhaseType(phase, PHASE_TYPES[phase]);
      system.getPhase(phase).setType(PHASE_TYPES[phase]);
    }

    PhaseInterface[] originalPhases = {system.getPhase(0), system.getPhase(1), system.getPhase(2)};
    int releasedPhysicalSlot = system.getPhaseIndex(removedPhase);
    double expectedSeedBeta = BETAS[2];
    PhaseType expectedSeedType = PHASE_TYPES[2];

    if (keepTotalComposition) {
      system.removePhaseKeepTotalComposition(removedPhase);
    } else {
      system.removePhase(removedPhase);
    }

    assertEquals(2, system.getNumberOfPhases());
    int survivor = 0;
    for (int original = 0; original < 3; original++) {
      if (original != removedPhase) {
        assertSame(originalPhases[original], system.getPhase(survivor++));
      }
    }
    assertNotSame(system.getPhase(0), system.getPhase(1));
    assertEquals(releasedPhysicalSlot, system.getPhaseIndex(2));

    system.addPhase();

    assertEquals(3, system.getNumberOfPhases());
    assertEquals(releasedPhysicalSlot, system.getPhaseIndex(2));
    assertEquals(expectedSeedBeta, system.getBeta(2));
    assertEquals(expectedSeedType, system.getPhase(2).getType());
    assertNotSame(system.getPhase(0), system.getPhase(1));
    assertNotSame(system.getPhase(0), system.getPhase(2));
    assertNotSame(system.getPhase(1), system.getPhase(2));
  }

  private static final class LifecycleSystem extends SystemPrEos {
    private static final long serialVersionUID = 1L;

    LifecycleSystem(double temperature, double pressure) {
      super(temperature, pressure);
    }

    void addClonedPhase() {
      phaseArray[numberOfPhases] = phaseArray[numberOfPhases - 1].clone();
      addPhase();
    }
  }
}
