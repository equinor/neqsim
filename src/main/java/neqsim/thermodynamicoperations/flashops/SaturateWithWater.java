package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SaturateWithWater class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class SaturateWithWater extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SaturateWithWater.class);

  Flash tpFlash;

  /**
   * <p>
   * Constructor for SaturateWithWater.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SaturateWithWater(SystemInterface system) {
    this.system = system;
    this.tpFlash = new TPflash(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (!system.getPhase(0).hasComponent("water")) {
      system.addComponent("water", system.getTotalNumberOfMoles() / 100.0);
      system.setMixingRule(system.getMixingRule());
    }

    boolean changedMultiPhase = false;
    if (!system.doMultiPhaseCheck()) {
      system.setMultiPhaseCheck(true);
      changedMultiPhase = true;
    }

    if (system.getComponent("water").getNumberOfmoles() < system.getTotalNumberOfMoles() / 2.0) {
      system.addComponent("water", system.getTotalNumberOfMoles());
    }
    this.tpFlash = new TPflash(system);

    // Snapshot of the last successfully converged composition (absolute moles).
    // The bisection-style refinement below drives the aqueous phase towards zero,
    // and the underlying TPflash can diverge for a vanishingly small aqueous phase.
    // The exact point of divergence depends on the JVM's transcendental math
    // implementation (JDK 8 vs newer), so a flash failure at any step is recovered
    // by restoring the last converged state instead of aborting the whole flowsheet.
    double[] lastConvergedMoles = captureMoles(system);
    if (!runFlashSafely(lastConvergedMoles)) {
      if (changedMultiPhase) {
	system.setMultiPhaseCheck(false);
      }
      return;
    }
    lastConvergedMoles = captureMoles(system);

    boolean hasAq = system.hasPhaseType(PhaseType.AQUEOUS);
    double lastdn = 0.0;
    if (hasAq) {
      lastdn = system.getPhase(PhaseType.AQUEOUS).getNumberOfMolesInPhase();
    } else {
      lastdn = system.getPhase(0).getNumberOfMolesInPhase();
    }
    double dn = 1.0;
    int i = 0;
    do {
      i++;
      if (system.getNumberOfPhases() == 1 && hasAq) {
	lastdn = -system.getComponent("water").getNumberOfmoles() * 0.1;
      } else if (!hasAq) {
	lastdn = Math.abs(lastdn) * 1.05;
      } else {
	lastdn = -system.getPhase(PhaseType.AQUEOUS).getComponent("water").getNumberOfMolesInPhase() * 0.9;
      }
      dn = lastdn / system.getNumberOfMoles();
      system.addComponent("water", lastdn);
      if (!runFlashSafely(lastConvergedMoles)) {
	break;
      }
      lastConvergedMoles = captureMoles(system);
      hasAq = system.hasPhaseType(PhaseType.AQUEOUS);
    } while (Math.abs(dn) > 1e-7 && i <= 50);
    if (i == 50) {
      logger.error("could not find solution - in water saturate : dn  " + dn);
    }
    if (system.hasPhaseType(PhaseType.AQUEOUS)) {
      system.removePhase(system.getNumberOfPhases() - 1);
      runFlashSafely(captureMoles(system));
    }
    if (changedMultiPhase) {
      system.setMultiPhaseCheck(false);
    }
  }

  /**
   * Runs the TP flash and recovers from a divergence near the water saturation point.
   *
   * <p>
   * On a {@link RuntimeException} the system is restored to the supplied last converged composition and a single
   * recovery flash is attempted, so that water saturation never propagates a flash failure up to the calling unit
   * operation. The point of divergence is JVM-dependent (JDK 8 transcendental math differs from newer JDKs).
   * </p>
   *
   * @param lastConvergedMoles absolute component mole numbers of the last converged state, as produced by
   * {@link #captureMoles(SystemInterface)}
   * @return {@code true} if the flash converged, {@code false} if it diverged and the last converged state was restored
   */
  private boolean runFlashSafely(double[] lastConvergedMoles) {
    try {
      tpFlash.run();
      return true;
    } catch (RuntimeException ex) {
      logger.warn("water saturation flash diverged near the saturation point; " + "restoring last converged state: "
	  + ex.getMessage());
      restoreMoles(system, lastConvergedMoles);
      try {
	tpFlash.run();
      } catch (RuntimeException ex2) {
	logger.warn("recovery flash after water saturation divergence failed: " + ex2.getMessage());
      }
      return false;
    }
  }

  /**
   * Captures the current overall composition of a system as absolute component mole numbers.
   *
   * @param sys the thermodynamic system to snapshot
   * @return array of component mole numbers in component order
   */
  private static double[] captureMoles(SystemInterface sys) {
    double[] composition = sys.getMolarComposition();
    double totalMoles = sys.getNumberOfMoles();
    double[] moles = new double[composition.length];
    for (int k = 0; k < composition.length; k++) {
      moles[k] = composition[k] * totalMoles;
    }
    return moles;
  }

  /**
   * Restores a system to a previously captured set of absolute component mole numbers.
   *
   * @param sys the thermodynamic system to restore
   * @param moles array of component mole numbers in component order, as produced by
   * {@link #captureMoles(SystemInterface)}
   */
  private static void restoreMoles(SystemInterface sys, double[] moles) {
    sys.setMolarFlowRates(moles);
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 70.0, 150.0);

    testSystem.addComponent("methane", 75.0);
    testSystem.addComponent("ethane", 7.5);
    testSystem.addComponent("propane", 4.0);
    testSystem.addComponent("n-butane", 1.0);
    testSystem.addComponent("i-butane", 0.6);
    testSystem.addComponent("n-hexane", 0.3);
    testSystem.addPlusFraction("C6", 1.3, 100.3 / 1000.0, 0.8232);
    // testSystem.addComponent("water", 0.3);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.TPflash();
      // testSystem.display();
      testOps.saturateWithWater();
      // testSystem.display();
      // testSystem.addComponent("water", 1);
      // testOps.saturateWithWater();
      // testSystem.display();
      // testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // testSystem.display();
  }
}
