package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemThermo;

/**
 * Tests for phase removal utilities in TP flash operations.
 */
public class TPflashPhaseRemovalTest {

  private SystemInterface createBaseSystem() {
    SystemInterface system = new SystemSrkEos(300.0, 10.0);
    system.addComponent("methane", 1.0);
    system.addComponent("ethane", 0.5);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.init(0);
    system.init(1);
    return system;
  }

  private void ensurePhaseExists(SystemInterface system, int phaseNumber) {
    try {
      SystemThermo thermoSystem = (SystemThermo) system;
      Field phaseArrayField = SystemThermo.class.getDeclaredField("phaseArray");
      phaseArrayField.setAccessible(true);
      PhaseInterface[] phaseArray = (PhaseInterface[]) phaseArrayField.get(thermoSystem);

      if (phaseArray[phaseNumber] == null) {
        PhaseInterface newPhase = (PhaseInterface) thermoSystem.getPhase(0).clone();
        newPhase.setTemperature(system.getTemperature());
        newPhase.setPressure(system.getPressure());
        phaseArray[phaseNumber] = newPhase;
      }

      Field phaseIndexField = SystemThermo.class.getDeclaredField("phaseIndex");
      phaseIndexField.setAccessible(true);
      int[] phaseIndex = (int[]) phaseIndexField.get(thermoSystem);
      phaseIndex[phaseNumber] = phaseNumber;
    } catch (Exception ex) {
      throw new RuntimeException("Unable to ensure phase " + phaseNumber + " exists", ex);
    }
  }

  @Test
  public void removeLowBetaPhasesRemovesAllSmallPhases() {
    SystemInterface system = createBaseSystem();
    system.setNumberOfPhases(3);
    ensurePhaseExists(system, 2);

    system.setBeta(0, phaseFractionMinimumLimit * 0.5);
    system.setBeta(1, phaseFractionMinimumLimit * 0.8);
    system.setBeta(2, 1.0 - system.getBeta(0) - system.getBeta(1));

    TPflash flash = new TPflash(system);
    flash.removeLowBetaPhases();

    assertEquals(1, system.getNumberOfPhases(), "All phases below the limit should be removed");
  }

  @Test
  public void removeLowBetaPhasesKeepCompositionRemovesMultiplePhases() {
    SystemInterface system = createBaseSystem();
    system.setNumberOfPhases(4);
    ensurePhaseExists(system, 2);
    ensurePhaseExists(system, 3);

    system.setBeta(0, phaseFractionMinimumLimit * 0.5);
    system.setBeta(1, phaseFractionMinimumLimit * 0.9);
    system.setBeta(2, 0.1);
    system.setBeta(3, 1.0 - system.getBeta(0) - system.getBeta(1) - system.getBeta(2));

    TPmultiflash flash = new TPmultiflash(system);
    boolean removed = flash.removeLowBetaPhasesKeepComposition();

    assertTrue(removed, "At least one phase should have been removed");
    assertEquals(2, system.getNumberOfPhases(),
        "All phases below the limit should be removed when keeping composition");
  }
}
