package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * HydrateFormationTemperatureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class HydrateFormationTemperatureFlash extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(HydrateFormationTemperatureFlash.class);

  /**
   * Dissociation factor for ions in hydrate calculations (NaCl -> Na+ + Cl-). CPA EOS doesn't fully
   * account for the colligative effect of ion dissociation on water activity, so we apply this
   * correction. Note: Glycols and alcohols are handled correctly by CPA association terms, so no
   * additional correction is needed for them.
   */
  private static final double ION_DISSOCIATION_FACTOR = 2.0;

  /**
   * <p>
   * Constructor for HydrateFormationTemperatureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public HydrateFormationTemperatureFlash(SystemInterface system) {
    super(system);
  }

  /**
   * <p>
   * stop.
   * </p>
   */
  public void stop() {
    system = null;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    double olfFug = 0.0;
    double temp = 0.0;
    double oldTemp = 0.0;
    double oldDiff = 0.0;
    // system.setHydrateCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    system.getPhase(4).getComponent("water").setx(1.0);
    int iter = 0;
    double diff = 0;
    system.setTemperature(system.getTemperature() + 0.0001);
    do {
      iter++;
      olfFug = system.getPhase(4).getFugacity("water");
      ops.TPflash();
      setFug();
      system.getPhase(4).getComponent("water").fugcoef(system.getPhase(4));
      system.getPhase(4).getComponent("water").setx(1.0);
      oldDiff = diff;

      // Get effective water fugacity accounting for ion dissociation
      double effectiveWaterFugacity = getEffectiveWaterFugacityForHydrate();
      double hydrateFugacity = system.getPhase(4).getFugacity("water");

      diff = 1.0 - (hydrateFugacity / effectiveWaterFugacity);

      // Debug output for first few iterations
      if (iter <= 5) {
        int aqIndex = findAqueousPhaseIndex();
        double rawWaterFug = system.getPhase(aqIndex).getFugacity("water");
        logger.debug("Iter {}: T={}K, hydrateFug={}, rawWaterFug={}, " + "effectiveFug={}, diff={}",
            iter, system.getTemperature(), hydrateFugacity, rawWaterFug, effectiveWaterFugacity,
            diff);
      }

      oldTemp = temp;
      temp = system.getTemperature();
      double dDiffdT = (diff - oldDiff) / (temp - oldTemp);

      if (iter < 2) {
        system.setTemperature((system.getTemperature() + 0.1));
      } else {
        double dT =
            (Math.abs(diff / dDiffdT)) > 10 ? Math.signum(diff / dDiffdT) * 10 : diff / dDiffdT;
        if (Double.isNaN(dT)) {
          dT = 0.1;
        }
        system.setTemperature(system.getTemperature() - dT);
      }
      if (iter > 2 && Math.abs(diff) > Math.abs(oldDiff)) {
        system.setTemperature((oldTemp + system.getTemperature()) / 2.0);
      }
      // logger.info("diff " + (system.getPhase(4).getFugacity("water") /
      // system.getPhase(0).getFugacity("water")));
      // logger.info("temperature " + system.getTemperature() + " iter " + iter);
      // logger.info("x water " + system.getPhase(4).getComponent("water").getx());
      try {
        Thread.sleep(100);
      } catch (InterruptedException iex) {
      }
    } while (Math.abs((olfFug - system.getPhase(4).getFugacity("water")) / olfFug) > 1e-6
        && iter < 100 || iter < 3);
  }

  /**
   * Calculate the effective water fugacity for hydrate equilibrium, accounting for the effect of
   * ionic species (salts) that the CPA EOS doesn't fully capture.
   * 
   * <p>
   * Ions like NaCl dissociate into multiple species (Na+ + Cl-), which has a colligative effect on
   * water activity that CPA doesn't model. This method applies a correction factor for ionic
   * components only.
   * </p>
   * 
   * <p>
   * Note: Glycols and alcohols are handled correctly by the CPA association terms, so no additional
   * correction is needed for them.
   * </p>
   *
   * @return the effective water fugacity accounting for ion dissociation effects
   */
  private double getEffectiveWaterFugacityForHydrate() {
    int aqueousPhaseIndex = findAqueousPhaseIndex();

    PhaseInterface aqueousPhase = system.getPhase(aqueousPhaseIndex);
    double waterFugacity = aqueousPhase.getFugacity("water");
    double waterMoleFraction = aqueousPhase.getComponent("water").getx();

    // Calculate effective additional moles from ionic species only
    // CPA handles glycols and alcohols correctly through association terms
    double effectiveAdditionalMoles = 0.0;

    for (int i = 0; i < aqueousPhase.getNumberOfComponents(); i++) {
      String compName = aqueousPhase.getComponent(i).getName();
      double moleFraction = aqueousPhase.getComponent(i).getx();

      // Skip water itself
      if (compName.equalsIgnoreCase("water")) {
        continue;
      }

      // Only apply correction for ions and salts
      // Check both isIsIon() and ionicCharge != 0 to catch salts like NaCl
      boolean isIonicComponent = aqueousPhase.getComponent(i).isIsIon()
          || aqueousPhase.getComponent(i).getIonicCharge() != 0;
      if (isIonicComponent) {
        // For salts, the dissociation doubles the effective particle count
        // (e.g., NaCl -> Na+ + Cl-)
        effectiveAdditionalMoles += moleFraction * (ION_DISSOCIATION_FACTOR - 1.0);
      }
      // Note: Glycols and alcohols are NOT corrected here - CPA handles them
    }

    // If no ionic species present, return original fugacity
    if (effectiveAdditionalMoles < 1e-10) {
      return waterFugacity;
    }

    // Calculate effective water mole fraction
    // Total effective moles = 1 (original normalized) + additional effective moles
    double effectiveTotalMoles = 1.0 + effectiveAdditionalMoles;
    double effectiveWaterMoleFraction = waterMoleFraction / effectiveTotalMoles;

    // Adjust fugacity based on the reduced effective water mole fraction
    // f = x * gamma * P, so f_effective = f * (x_effective / x_original)
    double fugacityCorrection = effectiveWaterMoleFraction / waterMoleFraction;

    // Debug logging
    logger.debug(
        "getEffectiveWaterFugacityForHydrate: waterFug={}, waterX={}, "
            + "effectiveAdditionalMoles={}, correction={}, effectiveFug={}",
        waterFugacity, waterMoleFraction, effectiveAdditionalMoles, fugacityCorrection,
        waterFugacity * fugacityCorrection);

    return waterFugacity * fugacityCorrection;
  }

  /**
   * <p>
   * run2.
   * </p>
   */
  public void run2() {
    double olfFug = 0.0;
    double oldTemp = 0.0;
    double oldOldTemp = 0.0;
    double oldDiff = 0.0;
    double oldOldDiff = 0.0;
    // system.setHydrateCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    system.getPhase(4).getComponent("water").setx(1.0);
    int iter = 0;
    do {
      iter++;
      olfFug = system.getPhase(4).getFugacity("water");
      ops.TPflash();
      setFug();
      system.getPhase(4).getComponent("water").fugcoef(system.getPhase(4));
      system.getPhase(4).getComponent("water").setx(1.0);

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
            - system.getPhase(4).getFugacity("water") / system.getPhase(0).getFugacity("water"));
        if (Math.abs(change) > 5.0) {
          change = Math.abs(change) / change * 5.0;
        }
        system.setTemperature(system.getTemperature() + change);
      }

      double diff =
          1.0 - (system.getPhase(4).getFugacity("water") / system.getPhase(0).getFugacity("water"));
      // logger.info("iter " + iter + " diff " +
      // (system.getPhase(4).getFugacity("water") /
      // system.getPhase(0).getFugacity("water")));
      oldOldTemp = oldTemp;
      oldTemp = system.getTemperature();

      oldOldDiff = oldDiff;
      oldDiff = diff;

      // logger.info("temperature " + system.getTemperature());
      // logger.info("x water " + system.getPhase(4).getComponent("water").getx());
    } while (Math.abs((olfFug - system.getPhase(4).getFugacity("water")) / olfFug) > 1e-6
        && iter < 100 || iter < 3);
  }

  /**
   * <p>
   * setFug.
   * </p>
   */
  public void setFug() {
    // Find the aqueous phase (phase with highest water content) for reference fugacity
    int aqueousPhaseIndex = findAqueousPhaseIndex();

    system.getPhase(4).getComponent("water").setx(1.0);
    for (int i = 0; i < system.getPhase(aqueousPhaseIndex).getNumberOfComponents(); i++) {
      for (int j = 0; j < system.getPhase(aqueousPhaseIndex).getNumberOfComponents(); j++) {
        if (system.getPhase(4).getComponent(j).isHydrateFormer()
            || system.getPhase(4).getComponent(j).getName().equals("water")) {
          ((ComponentHydrate) system.getPhase(4).getComponent(i)).setRefFug(j,
              system.getPhase(aqueousPhaseIndex).getFugacity(j));
        } else {
          ((ComponentHydrate) system.getPhase(4).getComponent(i)).setRefFug(j, 0);
        }
      }
    }
    system.getPhase(4).getComponent("water").setx(1.0);
    system.getPhase(4).init();
    system.getPhase(4).getComponent("water").fugcoef(system.getPhase(4));
  }

  /**
   * Find the index of the aqueous phase (phase with highest water mole fraction).
   *
   * @return the index of the aqueous phase
   */
  private int findAqueousPhaseIndex() {
    int aqueousPhaseIndex = 0;
    double maxWaterFraction = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).hasComponent("water")) {
        double waterFrac = system.getPhase(i).getComponent("water").getx();
        if (waterFrac > maxWaterFraction) {
          maxWaterFraction = waterFrac;
          aqueousPhaseIndex = i;
        }
      }
    }
    return aqueousPhaseIndex;
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
