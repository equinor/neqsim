package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.CO2BrinePhaseEquilibrium;

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
  /** Maximum absolute mole-fraction error accepted for a non-reactive electrolyte fluid. */
  private static final double INVENTORY_TOLERANCE = 1.0e-9;
  /** Snapshot of the last calculation; null before the first run. */
  private HydrateEquilibriumDiagnostics diagnostics;
  /** Stability evidence from the latest CO2/brine fluid evaluation. */
  private double minimumCo2TrialDistance = Double.NaN;

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

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException if a non-reactive electrolyte fluid evaluation fails conservation or phase checks
   */
  @Override
  public void run() {
    diagnostics = null;
    minimumCo2TrialDistance = Double.NaN;
    if (system instanceof neqsim.thermo.system.SystemPitzer) {
      PitzerHydrateFlash flash = new PitzerHydrateFlash((neqsim.thermo.system.SystemPitzer) system, false);
      flash.run();
      diagnostics = new HydrateEquilibriumDiagnostics(system, flash.isConverged(), -Math.expm1(flash.getResidual()),
          Double.NaN);
      return;
    }
    // Enable multi-phase check to properly handle systems with water+MEG+hydrocarbons+electrolytes
    // This ensures proper phase separation (gas, aqueous, hydrocarbon liquid)
    boolean originalMultiPhaseCheck = system.doMultiPhaseCheck();
    system.setMultiPhaseCheck(true);
    try {
      runTemperatureIterations();
    } catch (RuntimeException ex) {
      if (diagnostics == null) {
        diagnostics = new HydrateEquilibriumDiagnostics(system, false, Double.NaN, minimumCo2TrialDistance);
      }
      throw ex;
    } finally {
      system.setMultiPhaseCheck(originalMultiPhaseCheck);
    }
  }

  /**
   * Returns immutable convergence, phase identity, stability and conservation evidence.
   *
   * @return the most recent diagnostic snapshot, or null before a run
   */
  public HydrateEquilibriumDiagnostics getDiagnostics() {
    return diagnostics;
  }

  /** Iterates hydrate-water fugacity equality while checking the conserved electrolyte feed. */
  private void runTemperatureIterations() {
    double[] conservedMoles = null;
    if (!system.isChemicalSystem() && system.hasIons()) {
      conservedMoles = new double[system.getPhase(0).getNumberOfComponents()];
      for (int component = 0; component < conservedMoles.length; component++) {
        conservedMoles[component] = system.getPhase(0).getComponent(component).getNumberOfmoles();
      }
    }

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
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
    updateFluidAndHydrate(ops, conservedMoles);

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
      updateFluidAndHydrate(ops, conservedMoles);

      // Calculate new difference
      waterPhaseIndex = findWaterPhaseIndex();
      diff = 1.0 - (system.getPhase(4).getFugacity("water") / system.getPhase(waterPhaseIndex).getFugacity("water"));

      // Check for oscillation and dampen if needed
      if (iter > 3 && Math.abs(diff) > Math.abs(oldDiff) * 1.1) {
        // Oscillating - take smaller step
        temp = (oldTemp + temp) / 2.0;
        system.setTemperature(temp);
        updateFluidAndHydrate(ops, conservedMoles);
        waterPhaseIndex = findWaterPhaseIndex();
        diff = 1.0 - (system.getPhase(4).getFugacity("water") / system.getPhase(waterPhaseIndex).getFugacity("water"));
      }

      if (logger.isDebugEnabled()) {
        logger.debug("Hydrate T iter {}: T={} K, diff={}", iter, temp, diff);
      }

    } while (Math.abs(diff) > tolerance && iter < maxIterations);

    if (iter >= maxIterations) {
      logger.warn("Hydrate formation temperature did not converge after {} iterations. " + "Final diff={}",
          maxIterations, diff);
    }

    boolean converged = Double.isFinite(diff) && Math.abs(diff) <= tolerance;
    diagnostics = new HydrateEquilibriumDiagnostics(system, converged, diff, minimumCo2TrialDistance);
    if (!converged && CO2BrinePhaseEquilibrium.isApplicable(system)) {
      throw new IllegalStateException("CO2/brine hydrate temperature did not converge: residual=" + diff);
    }

  }

  /**
   * Evaluates the fluid and hydrate reference and rejects an invalid electrolyte inventory.
   *
   * @param ops fluid flash operations
   * @param conservedMoles input component amounts, or null when this operation does not own species conservation
   */
  private void updateFluidAndHydrate(ThermodynamicOperations ops, double[] conservedMoles) {
    if (CO2BrinePhaseEquilibrium.isApplicable(system)) {
      CO2BrinePhaseEquilibrium fluidFlash = new CO2BrinePhaseEquilibrium(system);
      fluidFlash.run();
      minimumCo2TrialDistance = fluidFlash.getMinimumTrialDistance();
    } else {
      ops.TPflash();
    }
    setFug();
    system.getPhase(4).getComponent("water").fugcoef(system.getPhase(4));
    system.getPhase(4).getComponent("water").setx(1.0);
    if (conservedMoles == null) {
      return;
    }
    double totalMoles = 0.0;
    for (double moles : conservedMoles) {
      if (!Double.isFinite(moles) || moles < 0.0) {
        throw new IllegalStateException("Hydrate fluid inventory has an invalid input component amount");
      }
      totalMoles += moles;
    }
    if (!(totalMoles > 0.0) || !Double.isFinite(totalMoles) || !Double.isFinite(system.getTotalNumberOfMoles())
        || Math.abs(system.getTotalNumberOfMoles() / totalMoles - 1.0) > INVENTORY_TOLERANCE) {
      throw new IllegalStateException("Hydrate fluid inventory failed total-mole conservation");
    }
    double betaSum = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      if (!Double.isFinite(beta) || beta < 0.0 || beta > 1.0) {
        throw new IllegalStateException("Hydrate fluid inventory has an invalid phase fraction: " + beta);
      }
      betaSum += beta;
      double compositionSum = 0.0;
      for (int component = 0; component < conservedMoles.length; component++) {
        ComponentInterface species = system.getPhase(phase).getComponent(component);
        double x = species.getx();
        if (!Double.isFinite(x) || x < 0.0 || x > 1.0) {
          throw new IllegalStateException("Hydrate fluid inventory has an invalid composition for "
              + species.getComponentName() + " in phase " + phase);
        }
        compositionSum += x;
        if ((species.getIonicCharge() != 0 || species.isIsIon())
            && system.getPhase(phase).getType() != PhaseType.AQUEOUS && x > 1.0e-12) {
          throw new IllegalStateException(
              "Hydrate fluid inventory has an ion outside the aqueous phase: " + species.getComponentName());
        }
      }
      if (Math.abs(compositionSum - 1.0) > INVENTORY_TOLERANCE) {
        throw new IllegalStateException("Hydrate fluid inventory has an unnormalized composition in phase " + phase);
      }
    }
    if (Math.abs(betaSum - 1.0) > INVENTORY_TOLERANCE) {
      throw new IllegalStateException("Hydrate fluid inventory has unnormalized phase fractions: " + betaSum);
    }
    for (int component = 0; component < conservedMoles.length; component++) {
      double expected = conservedMoles[component] / totalMoles;
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      double overall = system.getPhase(0).getComponent(component).getz();
      if (!Double.isFinite(overall) || Math.abs(overall - expected) > INVENTORY_TOLERANCE
          || Math.abs(recovered - expected) > INVENTORY_TOLERANCE) {
        throw new IllegalStateException(
            "Hydrate fluid inventory failed for " + system.getPhase(0).getComponent(component).getComponentName()
                + ": expected=" + expected + ", overall=" + overall + ", recovered=" + recovered);
      }
    }
  }

  /**
   * Find the gas phase index in the system.
   *
   * @return the index of the gas phase, or 0 if no gas phase found
   */
  private int findGasPhaseIndex() {
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == neqsim.thermo.phase.PhaseType.GAS) {
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
    int aqueousIndex = -1;
    double maxWaterFraction = 0.0;

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).hasComponent("water")) {
        double waterFraction = system.getPhase(i).getComponent("water").getx();
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
    int aqueousIndex = findAqueousPhaseIndex();
    if (aqueousIndex >= 0) {
      return aqueousIndex;
    }
    // Fall back to gas phase if no aqueous phase
    return findGasPhaseIndex();
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
    system.getPhase(4).getComponent("water").setx(1.0);
    int gasPhaseIndex = findGasPhaseIndex();
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      for (int j = 0; j < system.getPhase(0).getNumberOfComponents(); j++) {
        if (system.getPhase(4).getComponent(j).isHydrateFormer()
            || system.getPhase(4).getComponent(j).getName().equals("water")) {
          ((ComponentHydrate) system.getPhase(4).getComponent(i)).setRefFug(j,
              system.getPhase(gasPhaseIndex).getFugacity(j));
        } else {
          ((ComponentHydrate) system.getPhase(4).getComponent(i)).setRefFug(j, 0);
        }
      }
    }
    system.getPhase(4).getComponent("water").setx(1.0);
    system.getPhase(4).init();
    system.getPhase(4).getComponent("water").fugcoef(system.getPhase(4));
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {
  }
}
