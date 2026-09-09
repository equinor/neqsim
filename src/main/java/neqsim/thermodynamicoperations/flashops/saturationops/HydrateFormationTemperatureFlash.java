package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * HydrateFormationTemperatureFlash class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class HydrateFormationTemperatureFlash extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(HydrateFormationTemperatureFlash.class);

  /** True when the last run reached the hydrate equilibrium condition. */
  private boolean converged = false;
  /** Water fugacity residual of the last run. */
  private double lastResidual = Double.NaN;
  /** Residual accepted when the converged root is re-checked from a clean phase split. */
  private static final double VERIFICATION_TOLERANCE = 1.0e-3;

  /**
   * Constructor for HydrateFormationTemperatureFlash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public HydrateFormationTemperatureFlash(SystemInterface system) {
    super(system);
  }

  /**
   * stop.
   */
  public void stop() {
    system = null;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // Enable multi-phase check to properly handle systems with water+MEG+hydrocarbons+electrolytes
    // This ensures proper phase separation (gas, aqueous, hydrocarbon liquid)
    boolean originalMultiPhaseCheck = system.doMultiPhaseCheck();
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    SystemInterface verificationSystem = system.clone();
    system.getPhase(4).getComponent("water").setx(1.0);

    int iter = 0;
    int maxIterations = 50;
    double tolerance = 1e-6;

    double temp = system.getTemperature();
    double oldTemp = temp;
    double oldOldTemp = temp;
    double diff = 0.0;
    double oldDiff = 0.0;
    double oldOldDiff = 0.0;

    // Initial flash to get starting fugacities
    ops.TPflash();
    setFug();
    system.getPhase(4).getComponent("water").fugcoef(system.getPhase(4));
    system.getPhase(4).getComponent("water").setx(1.0);

    int waterPhaseIndex = findWaterPhaseIndex();
    diff = 1.0 - (system.getPhase(4).getFugacity("water") / system.getPhase(waterPhaseIndex).getFugacity("water"));

    do {
      iter++;
      oldOldTemp = oldTemp;
      oldTemp = temp;
      oldOldDiff = oldDiff;
      oldDiff = diff;

      // Calculate temperature step using secant method when possible
      double dT;
      if (iter < 3) {
        // Initial steps: use simple proportional step
        dT = diff * 5.0; // Scale factor for initial convergence
        if (Math.abs(dT) > 10.0) {
          dT = Math.signum(dT) * 10.0;
        }
      } else {
        // Secant method for faster convergence
        double dDiffdT = (oldDiff - oldOldDiff) / (oldTemp - oldOldTemp);
        if (Math.abs(dDiffdT) > 1e-10) {
          dT = oldDiff / dDiffdT;
          // Limit step size
          if (Math.abs(dT) > 10.0) {
            dT = Math.signum(dT) * 10.0;
          }
        } else {
          dT = diff * 3.0;
          if (Math.abs(dT) > 5.0) {
            dT = Math.signum(dT) * 5.0;
          }
        }
      }

      // Handle NaN
      if (Double.isNaN(dT)) {
        dT = 1.0;
      }

      // Update temperature
      temp = system.getTemperature() - dT;

      // Ensure temperature stays in reasonable range (150K to 350K)
      if (temp < 150.0) {
        temp = 150.0;
      }
      if (temp > 350.0) {
        temp = 350.0;
      }

      system.setTemperature(temp);

      // Perform flash and update fugacities
      ops.TPflash();
      setFug();
      system.getPhase(4).getComponent("water").fugcoef(system.getPhase(4));
      system.getPhase(4).getComponent("water").setx(1.0);

      // Calculate new difference
      waterPhaseIndex = findWaterPhaseIndex();
      diff = 1.0 - (system.getPhase(4).getFugacity("water") / system.getPhase(waterPhaseIndex).getFugacity("water"));

      // Check for oscillation and dampen if needed
      if (iter > 3 && Math.abs(diff) > Math.abs(oldDiff) * 1.1) {
        // Oscillating - take smaller step
        temp = (oldTemp + temp) / 2.0;
        system.setTemperature(temp);
        ops.TPflash();
        setFug();
        system.getPhase(4).getComponent("water").fugcoef(system.getPhase(4));
        system.getPhase(4).getComponent("water").setx(1.0);
        waterPhaseIndex = findWaterPhaseIndex();
        diff = 1.0 - (system.getPhase(4).getFugacity("water") / system.getPhase(waterPhaseIndex).getFugacity("water"));
      }

      if (logger.isDebugEnabled()) {
        logger.debug("Hydrate T iter {}: T={} K, diff={}", iter, temp, diff);
      }

    } while (Math.abs(diff) > tolerance && iter < maxIterations);

    // Restore original multi-phase check setting
    system.setMultiPhaseCheck(originalMultiPhaseCheck);

    lastResidual = diff;
    converged = Double.isFinite(diff) && Math.abs(diff) <= tolerance;

    if (converged && !reproducesEquilibrium(verificationSystem, system.getTemperature(), system.getPressure())) {
      logger.error(
          "Hydrate equilibrium at {} bara could not be reproduced from a clean phase split at {} K. "
              + "The residual was satisfied by a degenerate phase split, not by a hydrate equilibrium.",
          system.getPressure(), system.getTemperature());
      converged = false;
    }

    if (!converged) {
      // Leaving the system at whatever temperature the last step reached is worse than failing:
      // it is a plausible-looking number that can land on either side of the true boundary. NaN
      // makes the failure impossible to mistake for a result, and matches the check that
      // ThermodynamicOperations.hydrateFormationTemperature already performs.
      logger.error(
          "Hydrate formation temperature did not converge at {} bara after {} iterations "
              + "(residual {}). Reporting NaN instead of the last iterate {} K.",
          system.getPressure(), iter, diff, temp);
      system.setTemperature(Double.NaN);
    }
  }

  /**
   * Check whether the last {@link #run()} reached the hydrate equilibrium condition.
   *
   * @return true when the water fugacity residual converged within tolerance
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Get the water fugacity residual of the last {@link #run()}.
   *
   * @return the final value of {@code 1 - f_hydrate(water) / f_aqueous(water)}
   */
  public double getLastResidual() {
    return lastResidual;
  }

  /**
   * Find the gas phase index in the system.
   *
   * @return the index of the gas phase, or 0 if no gas phase found
   */
  private int findGasPhaseIndex() {
    return findGasPhaseIndex(system);
  }

  /**
   * Find the gas phase index of a system.
   *
   * @param target the system to inspect
   * @return the index of the gas phase, or 0 if no gas phase found
   */
  private static int findGasPhaseIndex(SystemInterface target) {
    for (int i = 0; i < target.getNumberOfPhases(); i++) {
      if (target.getPhase(i).getType() == neqsim.thermo.phase.PhaseType.GAS) {
        return i;
      }
    }
    // Fallback to phase 0 if no gas phase found
    return 0;
  }

  /**
   * Find the aqueous phase index in the system (phase with highest water content).
   *
   * @return the index of the aqueous phase, or -1 if no aqueous phase found
   */
  private int findAqueousPhaseIndex() {
    return findAqueousPhaseIndex(system);
  }

  /**
   * Find the aqueous phase index of a system (phase with highest water content).
   *
   * @param target the system to inspect
   * @return the index of the aqueous phase, or -1 if no aqueous phase found
   */
  private static int findAqueousPhaseIndex(SystemInterface target) {
    int aqueousIndex = -1;
    double maxWaterFraction = 0.0;

    for (int i = 0; i < target.getNumberOfPhases(); i++) {
      if (target.getPhase(i).hasComponent("water")) {
        double waterFraction = target.getPhase(i).getComponent("water").getx();
        if (waterFraction > maxWaterFraction && waterFraction > 0.3) {
          maxWaterFraction = waterFraction;
          aqueousIndex = i;
        }
      }
    }
    return aqueousIndex;
  }

  /**
   * Find the best phase index for water fugacity comparison in hydrate equilibrium. Prefers aqueous phase if available,
   * otherwise uses gas phase.
   *
   * @return the phase index to use for water fugacity
   */
  private int findWaterPhaseIndex() {
    return findWaterPhaseIndex(system);
  }

  /**
   * Find the best phase index for water fugacity comparison in hydrate equilibrium of a system.
   *
   * @param target the system to inspect
   * @return the phase index to use for water fugacity
   */
  private static int findWaterPhaseIndex(SystemInterface target) {
    int aqueousIndex = findAqueousPhaseIndex(target);
    if (aqueousIndex >= 0) {
      return aqueousIndex;
    }
    // Fall back to gas phase if no aqueous phase
    return findGasPhaseIndex(target);
  }

  /**
   * run2.
   */
  public void run2() {
    double olfFug = 0.0;
    double oldTemp = 0.0;
    double oldOldTemp = 0.0;
    double oldDiff = 0.0;
    double oldOldDiff = 0.0;
    // system.setHydrateCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    // Enable multi-phase check to properly handle systems with water+MEG+hydrocarbons+electrolytes
    boolean originalMultiPhaseCheck = system.doMultiPhaseCheck();
    system.setMultiPhaseCheck(true);

    system.getPhase(4).getComponent("water").setx(1.0);
    int iter = 0;
    do {
      iter++;
      olfFug = system.getPhase(4).getFugacity("water");
      ops.TPflash();
      setFug();
      system.getPhase(4).getComponent("water").fugcoef(system.getPhase(4));
      system.getPhase(4).getComponent("water").setx(1.0);

      int waterPhaseIndex = findWaterPhaseIndex();
      if (iter % 4 == 0) {
        // logger.info("ny temp " +(system.getTemperature() -
        // oldDiff/((oldDiff-oldOldDiff)/(oldTemp-oldOldTemp))));
        double change = -oldDiff / ((oldDiff - oldOldDiff) / (oldTemp - oldOldTemp));
        if (Math.abs(change) > 5.0) {
          change = Math.abs(change) / change * 5.0;
        }
        system.setTemperature((system.getTemperature() + change));
      } else {
        double change = (1.0
            - system.getPhase(4).getFugacity("water") / system.getPhase(waterPhaseIndex).getFugacity("water"));
        if (Math.abs(change) > 5.0) {
          change = Math.abs(change) / change * 5.0;
        }
        system.setTemperature(system.getTemperature() + change);
      }

      double diff = 1.0
          - (system.getPhase(4).getFugacity("water") / system.getPhase(waterPhaseIndex).getFugacity("water"));
      // logger.info("iter " + iter + " diff " +
      // (system.getPhase(4).getFugacity("water") /
      // system.getPhase(gasPhaseIndex).getFugacity("water")));
      oldOldTemp = oldTemp;
      oldTemp = system.getTemperature();

      oldOldDiff = oldDiff;
      oldDiff = diff;

      // logger.info("temperature " + system.getTemperature());
      // logger.info("x water " + system.getPhase(4).getComponent("water").getx());
    } while (Math.abs((olfFug - system.getPhase(4).getFugacity("water")) / olfFug) > 1e-6 && iter < 100 || iter < 3);

    // Restore original multi-phase check setting
    system.setMultiPhaseCheck(originalMultiPhaseCheck);
  }

  /**
   * setFug.
   */
  public void setFug() {
    setFug(system);
  }

  /**
   * Set the hydrate phase reference fugacities from the gas phase of a system.
   *
   * @param target the system to operate on
   */
  private static void setFug(SystemInterface target) {
    target.getPhase(4).getComponent("water").setx(1.0);
    int gasPhaseIndex = findGasPhaseIndex(target);
    for (int i = 0; i < target.getPhase(0).getNumberOfComponents(); i++) {
      for (int j = 0; j < target.getPhase(0).getNumberOfComponents(); j++) {
        if (target.getPhase(4).getComponent(j).isHydrateFormer()
            || target.getPhase(4).getComponent(j).getName().equals("water")) {
          ((ComponentHydrate) target.getPhase(4).getComponent(i)).setRefFug(j,
              target.getPhase(gasPhaseIndex).getFugacity(j));
        } else {
          ((ComponentHydrate) target.getPhase(4).getComponent(i)).setRefFug(j, 0);
        }
      }
    }
    target.getPhase(4).getComponent("water").setx(1.0);
    target.getPhase(4).init();
    target.getPhase(4).getComponent("water").fugcoef(target.getPhase(4));
  }

  /**
   * Re-solve the hydrate equilibrium condition from a clean phase split at the converged temperature.
   *
   * <p>
   * The iteration re-uses the phase split of the previous step, so a flash that degenerates part way through the search
   * can leave the system in a state where the water fugacity equality is satisfied by an artefact rather than by a real
   * three phase equilibrium. Repeating the check on an untouched copy of the feed rejects such roots: a real
   * equilibrium temperature reproduces the residual, an artefact does not.
   * </p>
   *
   * @param reference an untouched copy of the feed
   * @param temperature the converged temperature in Kelvin
   * @param pressure the pressure in bara
   * @return true when the equilibrium condition is reproduced from a clean phase split
   */
  private static boolean reproducesEquilibrium(SystemInterface reference, double temperature, double pressure) {
    try {
      reference.setTemperature(temperature);
      reference.setPressure(pressure);
      reference.setHydrateCheck(true);
      reference.setMultiPhaseCheck(true);
      reference.getPhase(4).getComponent("water").setx(1.0);
      new ThermodynamicOperations(reference).TPflash();
      if (hasIonsInAGasPhase(reference)) {
        return false;
      }
      setFug(reference);
      reference.getPhase(4).getComponent("water").fugcoef(reference.getPhase(4));
      reference.getPhase(4).getComponent("water").setx(1.0);
      int waterPhase = findWaterPhaseIndex(reference);
      double residual = 1.0
          - (reference.getPhase(4).getFugacity("water") / reference.getPhase(waterPhase).getFugacity("water"));
      return Double.isFinite(residual) && Math.abs(residual) < VERIFICATION_TOLERANCE;
    } catch (Exception ex) {
      logger.debug("Hydrate equilibrium verification flash failed", ex);
      return false;
    }
  }

  /**
   * Check whether a phase split has placed ions in a phase identified as gas.
   *
   * <p>
   * Ions are not volatile, so a gas phase carrying a material ion fraction means the phase identification has failed.
   * Densities, activities and fugacities of that phase are then evaluated on the wrong volume root, and any hydrate
   * temperature built on it is an artefact rather than an equilibrium.
   * </p>
   *
   * @param target the system to inspect
   * @return true when a gas phase carries a material amount of ions
   */
  private static boolean hasIonsInAGasPhase(SystemInterface target) {
    for (int phase = 0; phase < target.getNumberOfPhases(); phase++) {
      if (target.getPhase(phase).getType() != neqsim.thermo.phase.PhaseType.GAS) {
        continue;
      }
      for (int component = 0; component < target.getPhase(phase).getNumberOfComponents(); component++) {
        if (target.getPhase(phase).getComponent(component).getIonicCharge() != 0
            && target.getPhase(phase).getComponent(component).getx() > 1.0e-10) {
          logger.debug("Phase {} is identified as gas but carries ions, so the phase split is not trustworthy", phase);
          return true;
        }
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {
  }
}
