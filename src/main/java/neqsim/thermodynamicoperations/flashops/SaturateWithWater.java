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

    // Snapshot the caller's starting composition. If saturation cannot be established (the flood
    // flash diverges, or flooding does not produce a free aqueous phase) the fluid is restored to
    // this state rather than being left flooded with a large water excess.
    double[] originalMoles = captureMoles(system);

    boolean changedMultiPhase = false;
    if (!system.doMultiPhaseCheck()) {
      system.setMultiPhaseCheck(true);
      changedMultiPhase = true;
    }

    // Flood the fluid with a large excess of water so that a free aqueous phase is guaranteed to
    // form. When an aqueous phase coexists, the remaining (gas/oil) phases are - by definition of
    // phase equilibrium - exactly water saturated, so the water already dissolved in them is the
    // saturation amount we are looking for.
    system.addComponent("water", system.getTotalNumberOfMoles());
    this.tpFlash = new TPflash(system);

    // The TPflash can diverge for a vanishingly small aqueous phase; the exact point of divergence
    // depends on the JVM's transcendental math implementation (JDK 8 vs newer), so a flash failure
    // is recovered instead of aborting the whole flowsheet.
    if (!runFlashSafely(captureMoles(system))) {
      // Flood flash diverged: do not leave the fluid flooded - restore the caller's composition.
      restoreMoles(system, originalMoles);
      runFlashSafely(originalMoles);
      if (changedMultiPhase) {
	system.setMultiPhaseCheck(false);
      }
      return;
    }

    int aqueousIndex = indexOfAqueousPhase(system);
    if (aqueousIndex < 0) {
      // Flooding did not create a free aqueous phase (e.g. a fully water-miscible dense or
      // supercritical region near the water critical point). Saturation is undefined here, so the
      // caller's composition is restored rather than left over-watered.
      logger.warn("water saturation: no free aqueous phase formed after flooding; " + "restoring original composition");
      restoreMoles(system, originalMoles);
      runFlashSafely(originalMoles);
      if (changedMultiPhase) {
	system.setMultiPhaseCheck(false);
      }
      return;
    }

    // Read the water dissolved in every non-aqueous phase. This is the exact saturated water
    // content, computed directly from the equilibrium flash rather than by iterative refinement.
    // Resetting the overall water to that amount removes the whole free aqueous phase in a single
    // step and never drives the aqueous phase through the divergence-prone near-zero regime.
    double saturatedWaterMoles = 0.0;
    for (int p = 0; p < system.getNumberOfPhases(); p++) {
      if (p == aqueousIndex) {
	continue;
      }
      saturatedWaterMoles += system.getPhase(p).getComponent("water").getNumberOfMolesInPhase();
    }
    double currentWaterMoles = system.getComponent("water").getNumberOfmoles();
    system.addComponent("water", saturatedWaterMoles - currentWaterMoles);
    runFlashSafely(captureMoles(system));

    // The fluid now sits at (or just below) the saturation boundary. Any residual incipient aqueous
    // phase is located explicitly (not assumed to be the last phase) and removed so the result is a
    // single water-saturated hydrocarbon stream.
    int residualAqueous = indexOfAqueousPhase(system);
    if (residualAqueous >= 0 && system.getNumberOfPhases() > 1) {
      system.removePhase(residualAqueous);
      runFlashSafely(captureMoles(system));
    }

    if (changedMultiPhase) {
      system.setMultiPhaseCheck(false);
    }
  }

  /**
   * Returns the index of the (first) aqueous phase in the system, or {@code -1} if no aqueous phase is present.
   *
   * @param sys the thermodynamic system to inspect
   * @return the phase index of the aqueous phase, or {@code -1} if none is present
   */
  private static int indexOfAqueousPhase(SystemInterface sys) {
    for (int p = 0; p < sys.getNumberOfPhases(); p++) {
      if (sys.getPhase(p).getType() == PhaseType.AQUEOUS) {
	return p;
      }
    }
    return -1;
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
