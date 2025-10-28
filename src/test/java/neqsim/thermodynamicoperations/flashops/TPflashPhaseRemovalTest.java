package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
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

  private void setPhaseFractions(SystemInterface system, double... betas) {
    system.setNumberOfPhases(betas.length);
    for (int i = 0; i < betas.length; i++) {
      ensurePhaseExists(system, i);
    }

    double totalMoles = system.getTotalNumberOfMoles();
    int components = system.getPhase(0).getNumberOfComponents();
    double[] componentTotals = new double[components];
    for (int comp = 0; comp < components; comp++) {
      componentTotals[comp] = system.getPhase(0).getComponent(comp).getNumberOfmoles();
    }

    for (int phaseNum = 0; phaseNum < betas.length; phaseNum++) {
      PhaseInterface phase = system.getPhase(phaseNum);
      double targetPhaseMoles = totalMoles * betas[phaseNum];
      setPhaseTotalMoles(phase, targetPhaseMoles);
      phase.setBeta(betas[phaseNum]);
      for (int comp = 0; comp < components; comp++) {
        double fraction = componentTotals[comp] / totalMoles;
        double compPhaseMoles = targetPhaseMoles * fraction;
        phase.getComponent(comp).setNumberOfMolesInPhase(compPhaseMoles);
      }
      system.setBeta(phaseNum, betas[phaseNum]);
    }
  }

  private void setPhaseTotalMoles(PhaseInterface phase, double targetPhaseMoles) {
    Class<?> current = phase.getClass();
    while (current != null) {
      try {
        Field molesField = current.getDeclaredField("numberOfMolesInPhase");
        molesField.setAccessible(true);
        molesField.setDouble(phase, targetPhaseMoles);
        return;
      } catch (NoSuchFieldException ex) {
        current = current.getSuperclass();
      } catch (IllegalAccessException ex) {
        throw new RuntimeException("Unable to set phase mole count", ex);
      }
    }
    throw new RuntimeException("Unable to locate numberOfMolesInPhase field");
  }

  @Test
  public void removeLowBetaPhasesRemovesAllSmallPhases() {
    SystemInterface system = createBaseSystem();
    setPhaseFractions(system, phaseFractionMinimumLimit * 0.5,
        phaseFractionMinimumLimit * 0.8,
        1.0 - phaseFractionMinimumLimit * (0.5 + 0.8));

    TPflash flash = new TPflash(system);
    flash.removeLowBetaPhases();

    assertEquals(1, system.getNumberOfPhases(), "All phases below the limit should be removed");
  }

  @Test
  public void removeLowBetaPhasesKeepCompositionRemovesMultiplePhases() {
    SystemInterface system = createBaseSystem();
    setPhaseFractions(system, phaseFractionMinimumLimit * 0.5,
        phaseFractionMinimumLimit * 0.9,
        0.1,
        1.0 - (phaseFractionMinimumLimit * (0.5 + 0.9) + 0.1));

    TPmultiflash flash = new TPmultiflash(system);
    boolean removed = flash.removeLowBetaPhasesKeepComposition();

    assertTrue(removed, "At least one phase should have been removed");
    assertEquals(2, system.getNumberOfPhases(),
        "All phases below the limit should be removed when keeping composition");
  }
}
