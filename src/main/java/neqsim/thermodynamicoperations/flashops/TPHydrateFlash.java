/*
 * TPHydrateFlash.java
 *
 * Created for hydrate fraction calculation at given T and P
 */

package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * TPHydrateFlash performs a TP flash that includes hydrate phase equilibrium calculation.
 *
 * <p>
 * This class extends TPflash to calculate the fraction of hydrate at given temperature and pressure
 * conditions. It uses the CPA EOS approach (Statoil/Equinor model) for hydrate fugacity calculation
 * with proper cavity occupancy.
 * </p>
 *
 * <p>
 * The hydrate model supports both Structure I and Structure II hydrates, automatically selecting
 * the most stable structure based on fugacity minimization.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public class TPHydrateFlash extends TPflash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPHydrateFlash.class);

  /** Maximum iterations for hydrate fraction calculation. */
  private static final int MAX_HYDRATE_ITERATIONS = 100;

  /** Convergence tolerance for hydrate fugacity matching. */
  private static final double HYDRATE_TOLERANCE = 1e-8;

  /** Minimum hydrate fraction to consider hydrate formation. */
  private static final double MIN_HYDRATE_FRACTION = 1e-12;

  /** Flag to indicate if hydrate has formed. */
  private boolean hydrateFormed = false;

  /** The calculated hydrate fraction. */
  private double hydrateFraction = 0.0;

  /** The stable hydrate structure (1 or 2). */
  private int stableHydrateStructure = 1;

  /**
   * Flag to enable gas-hydrate only mode. When true, the algorithm will try to achieve gas-hydrate
   * equilibrium without an aqueous phase if all water can be consumed by hydrate.
   */
  private boolean gasHydrateOnlyMode = false;

  /**
   * Constructor for TPHydrateFlash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public TPHydrateFlash(SystemInterface system) {
    super(system);
  }

  /**
   * Constructor for TPHydrateFlash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public TPHydrateFlash(SystemInterface system, boolean checkForSolids) {
    super(system, checkForSolids);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // First ensure hydrate check is enabled
    if (!system.getHydrateCheck()) {
      system.setHydrateCheck(true);
    }

    // Do the regular TP flash first (gas/liquid/aqueous equilibrium)
    super.run();

    // Now perform hydrate equilibrium calculation
    calculateHydrateEquilibrium();
  }

  /**
   * Calculate hydrate phase equilibrium after the regular TP flash.
   *
   * <p>
   * This method checks if hydrate would form at the current T,P conditions and calculates the
   * hydrate fraction if formation occurs. When gasHydrateOnlyMode is enabled and water content is
   * low enough, it will calculate gas-hydrate equilibrium without an aqueous phase.
   * </p>
   */
  private void calculateHydrateEquilibrium() {
    // Check if water is present in the system
    int waterIndex = system.getPhase(0).getComponent("water") != null
        ? system.getPhase(0).getComponent("water").getComponentNumber()
        : -1;

    if (waterIndex < 0) {
      logger.debug("No water component found - hydrate cannot form");
      hydrateFormed = false;
      hydrateFraction = 0.0;
      return;
    }

    // Check if any hydrate formers are present
    boolean hasHydrateFormers = false;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).isHydrateFormer()) {
        hasHydrateFormers = true;
        break;
      }
    }

    if (!hasHydrateFormers) {
      logger.debug("No hydrate formers found - hydrate cannot form");
      hydrateFormed = false;
      hydrateFraction = 0.0;
      return;
    }

    // Initialize the hydrate phase (phase index 4 when setHydrateCheck is true)
    PhaseInterface hydratePhase = system.getPhase(4);
    if (!(hydratePhase instanceof PhaseHydrate)) {
      logger.warn("Hydrate phase not properly initialized");
      return;
    }

    // Set up reference fugacities for hydrate calculation
    setHydrateFugacities();

    // Calculate hydrate fugacity and compare with fluid phases
    double hydrateWaterFugacity = calculateHydrateWaterFugacity();

    // Find the phase with water (aqueous or gas phase)
    int waterPhaseIndex = findWaterPhase();

    // Check if we should attempt gas-hydrate-only equilibrium
    double waterZFraction = system.getPhase(0).getComponent(waterIndex).getz();
    boolean attemptGasHydrateOnly = shouldAttemptGasHydrateOnly(waterZFraction);

    if (waterPhaseIndex < 0 && !attemptGasHydrateOnly) {
      logger.debug("No water-bearing phase found");
      hydrateFormed = false;
      hydrateFraction = 0.0;
      return;
    }

    // If no aqueous phase but we have gas phase with water, use gas phase
    if (waterPhaseIndex < 0 && attemptGasHydrateOnly) {
      waterPhaseIndex = findGasPhaseWithWater();
      if (waterPhaseIndex < 0) {
        logger.debug("No water-bearing phase found for gas-hydrate equilibrium");
        hydrateFormed = false;
        hydrateFraction = 0.0;
        return;
      }
    }

    double fluidWaterFugacity = system.getPhase(waterPhaseIndex).getFugacity("water");

    // Check if hydrate would form (hydrate fugacity < fluid fugacity)
    double fugacityRatio = hydrateWaterFugacity / fluidWaterFugacity;

    if (fugacityRatio < 1.0) {
      // Hydrate is stable - calculate the hydrate fraction
      hydrateFormed = true;
      calculateHydrateFraction(waterPhaseIndex, waterIndex);

      // For gas-hydrate only mode with low water, try to remove aqueous phase
      if (attemptGasHydrateOnly) {
        attemptRemoveAqueousPhase(waterIndex);
      }
    } else {
      hydrateFormed = false;
      hydrateFraction = 0.0;
      logger.debug("Hydrate not stable at current conditions. Fugacity ratio: {}", fugacityRatio);
    }
  }

  /**
   * Set up the reference fugacities for all components in the hydrate phase.
   */
  private void setHydrateFugacities() {
    PhaseInterface hydratePhase = system.getPhase(4);

    // Use gas phase or highest pressure phase as reference
    int refPhaseIndex = 0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == PhaseType.GAS) {
        refPhaseIndex = i;
        break;
      }
    }

    // Set reference fugacities for each component
    for (int i = 0; i < hydratePhase.getNumberOfComponents(); i++) {
      for (int j = 0; j < hydratePhase.getNumberOfComponents(); j++) {
        if (hydratePhase.getComponent(j).isHydrateFormer()
            || hydratePhase.getComponent(j).getName().equals("water")) {
          double refFugacity = system.getPhase(refPhaseIndex).getFugacity(j);
          ((ComponentHydrate) hydratePhase.getComponent(i)).setRefFug(j, refFugacity);
        } else {
          ((ComponentHydrate) hydratePhase.getComponent(i)).setRefFug(j, 0);
        }
      }
    }

    // Set water mole fraction to 1 in hydrate phase (structural water)
    hydratePhase.getComponent("water").setx(1.0);

    // Initialize the hydrate phase with updated fugacities
    hydratePhase.init(hydratePhase.getNumberOfMolesInPhase(), hydratePhase.getNumberOfComponents(),
        1, PhaseType.HYDRATE, 1.0);
  }

  /**
   * Calculate the hydrate water fugacity using the CPA model.
   *
   * @return the water fugacity in the hydrate phase
   */
  private double calculateHydrateWaterFugacity() {
    PhaseInterface hydratePhase = system.getPhase(4);

    // Calculate fugacity coefficient for water in hydrate
    hydratePhase.getComponent("water").fugcoef(hydratePhase);

    return hydratePhase.getFugacity("water");
  }

  /**
   * Find the phase index that contains the most water.
   *
   * @return the phase index with the highest water content, or -1 if no water phase found
   */
  private int findWaterPhase() {
    int waterPhaseIndex = -1;
    double maxWaterFraction = 0.0;

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).hasComponent("water")) {
        double waterFraction = system.getPhase(i).getComponent("water").getx();
        if (waterFraction > maxWaterFraction) {
          maxWaterFraction = waterFraction;
          waterPhaseIndex = i;
        }
      }
    }

    return waterPhaseIndex;
  }

  /**
   * Find the gas phase index that contains water.
   *
   * @return the gas phase index with water, or -1 if not found
   */
  private int findGasPhaseWithWater() {
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == PhaseType.GAS
          && system.getPhase(i).hasComponent("water")) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Check if gas-hydrate-only equilibrium should be attempted.
   *
   * <p>
   * This returns true when water content is low enough that all water can potentially be consumed
   * by hydrate formation, allowing for gas-hydrate equilibrium without an aqueous phase.
   * </p>
   *
   * @param waterZFraction the total water mole fraction in the system
   * @return true if gas-hydrate-only mode should be attempted
   */
  private boolean shouldAttemptGasHydrateOnly(double waterZFraction) {
    // If gas-hydrate only mode is explicitly enabled, use it
    if (gasHydrateOnlyMode) {
      return true;
    }

    // For very low water content (< 1%), automatically attempt gas-hydrate only
    // This is the regime where water can be entirely consumed by hydrate
    return waterZFraction < 0.01;
  }

  /**
   * Attempt to remove the aqueous phase when all water is consumed by hydrate.
   *
   * <p>
   * When water content is very low and hydrate has formed, this method checks if the aqueous phase
   * fraction is negligible (smaller than the hydrate fraction) and removes it to achieve true
   * gas-hydrate equilibrium.
   * </p>
   *
   * @param waterIndex the component index of water
   */
  private void attemptRemoveAqueousPhase(int waterIndex) {
    // Find aqueous phase index
    int aqueousPhaseIndex = -1;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == PhaseType.AQUEOUS) {
        aqueousPhaseIndex = i;
        break;
      }
    }

    if (aqueousPhaseIndex < 0) {
      // No aqueous phase - already gas-hydrate only
      return;
    }

    double aqueousBeta = system.getBeta(aqueousPhaseIndex);
    double waterZFraction = system.getPhase(0).getComponent(waterIndex).getz();

    // Calculate the maximum water that can be in hydrate
    double waterFractionInHydrate = (stableHydrateStructure == 1) ? 46.0 / 54.0 : 136.0 / 160.0;
    double waterInHydrate = hydrateFraction * waterFractionInHydrate;

    // Check if hydrate can consume all water (with some tolerance)
    // If water in hydrate >= total water, remove aqueous phase
    double waterBalance = waterInHydrate - waterZFraction;

    if (waterBalance >= -1e-8 || aqueousBeta < 1e-10) {
      // All water is in hydrate - remove aqueous phase
      removeAqueousPhase(aqueousPhaseIndex);
      logger.debug("Removed aqueous phase - gas-hydrate equilibrium achieved");
    } else if (aqueousBeta < waterZFraction * 0.01) {
      // Aqueous phase is tiny compared to total water - remove it
      // Redistribute water to hydrate
      removeAqueousPhase(aqueousPhaseIndex);
      logger.debug("Removed trace aqueous phase - gas-hydrate equilibrium achieved");
    }
  }

  /**
   * Remove the aqueous phase from the system and redistribute its content to hydrate.
   *
   * @param aqueousPhaseIndex the index of the aqueous phase to remove
   */
  private void removeAqueousPhase(int aqueousPhaseIndex) {
    double aqueousBeta = system.getBeta(aqueousPhaseIndex);

    // Store phase information
    int currentNumPhases = system.getNumberOfPhases();
    double[] newBetas = new double[currentNumPhases - 1];
    int[] newPhaseIndices = new int[currentNumPhases - 1];

    // Copy phases except aqueous
    int newPhaseCount = 0;
    int hydratePhaseNewIndex = -1;
    for (int i = 0; i < currentNumPhases; i++) {
      if (i != aqueousPhaseIndex) {
        newBetas[newPhaseCount] = system.getBeta(i);
        newPhaseIndices[newPhaseCount] = system.getPhaseIndex(i);
        if (system.getPhase(i).getType() == PhaseType.HYDRATE) {
          hydratePhaseNewIndex = newPhaseCount;
        }
        newPhaseCount++;
      }
    }

    // Add aqueous beta to hydrate
    if (hydratePhaseNewIndex >= 0) {
      newBetas[hydratePhaseNewIndex] += aqueousBeta;
      hydrateFraction = newBetas[hydratePhaseNewIndex];
    }

    // Normalize betas to sum to 1.0
    double sum = 0.0;
    for (int i = 0; i < newPhaseCount; i++) {
      sum += newBetas[i];
    }
    for (int i = 0; i < newPhaseCount; i++) {
      newBetas[i] /= sum;
    }

    // Update system with new phase configuration
    system.setNumberOfPhases(newPhaseCount);
    for (int i = 0; i < newPhaseCount; i++) {
      system.setPhaseIndex(i, newPhaseIndices[i]);
      system.setBeta(i, newBetas[i]);
    }

    // Reinitialize the system
    system.init(1);
    system.orderByDensity();
    system.init(1);

    // Update hydrate fraction
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == PhaseType.HYDRATE) {
        hydrateFraction = system.getBeta(i);
        break;
      }
    }
  }

  /**
   * Calculate the hydrate fraction using iterative fugacity matching.
   *
   * @param waterPhaseIndex the index of the water-bearing phase
   * @param waterIndex the component index of water
   */
  private void calculateHydrateFraction(int waterPhaseIndex, int waterIndex) {
    // Get total water mole fraction in the system
    double waterZFraction = system.getPhase(0).getComponent(waterIndex).getz();

    // Calculate maximum hydrate fraction based on available water
    // Hydrate is ~85% water by mole, so max hydrate = water / 0.85
    double waterFractionInHydrate = (stableHydrateStructure == 1) ? 46.0 / 54.0 : 136.0 / 160.0;
    double maxHydrateFraction = waterZFraction / waterFractionInHydrate;

    // Cap at 99% to leave some gas phase
    if (maxHydrateFraction > 0.99) {
      maxHydrateFraction = 0.99;
    }

    // Initial guess for hydrate fraction based on fugacity difference
    double hydrateWaterFug = system.getPhase(4).getFugacity("water");
    double fluidWaterFug = system.getPhase(waterPhaseIndex).getFugacity("water");

    // Use secant method to find hydrate fraction
    double beta1 = 0.01; // Initial guess
    double beta2 = Math.min(0.5, maxHydrateFraction);

    double f1 = calculateHydrateObjective(beta1, waterPhaseIndex, waterIndex);
    double f2 = calculateHydrateObjective(beta2, waterPhaseIndex, waterIndex);

    for (int iter = 0; iter < MAX_HYDRATE_ITERATIONS; iter++) {
      if (Math.abs(f2 - f1) < 1e-20) {
        break;
      }

      double betaNew = beta2 - f2 * (beta2 - beta1) / (f2 - f1);

      // Bound the solution
      if (betaNew < MIN_HYDRATE_FRACTION) {
        betaNew = MIN_HYDRATE_FRACTION;
      }
      if (betaNew > maxHydrateFraction) {
        betaNew = maxHydrateFraction;
      }

      beta1 = beta2;
      f1 = f2;
      beta2 = betaNew;
      f2 = calculateHydrateObjective(beta2, waterPhaseIndex, waterIndex);

      if (Math.abs(f2) < HYDRATE_TOLERANCE) {
        break;
      }
    }

    // For very low water content, use the maximum possible hydrate fraction
    // This ensures all water goes to hydrate when water content is limiting
    if (waterZFraction < 0.01) {
      // Low water - use max hydrate that consumes all water
      hydrateFraction = maxHydrateFraction;
    } else {
      // Normal case - use calculated fraction
      hydrateFraction = beta2;
    }

    // Update the system with hydrate phase
    if (hydrateFraction > MIN_HYDRATE_FRACTION) {
      updateSystemWithHydrate(waterPhaseIndex, waterIndex);
    }
  }

  /**
   * Calculate the objective function for hydrate fraction iteration.
   *
   * <p>
   * The objective is to match the water fugacity between fluid and hydrate phases.
   * </p>
   *
   * @param beta the current hydrate fraction guess
   * @param waterPhaseIndex the index of the water phase
   * @param waterIndex the component index of water
   * @return the fugacity difference (should be zero at equilibrium)
   */
  private double calculateHydrateObjective(double beta, int waterPhaseIndex, int waterIndex) {
    // Update reference fugacities based on current beta
    setHydrateFugacities();

    // Recalculate hydrate fugacity
    double hydrateWaterFug = calculateHydrateWaterFugacity();
    double fluidWaterFug = system.getPhase(waterPhaseIndex).getFugacity("water");

    // Objective: ln(f_hydrate/f_fluid) = 0 at equilibrium
    return Math.log(hydrateWaterFug / fluidWaterFug);
  }

  /**
   * Update the system to include the hydrate phase with calculated fraction.
   *
   * @param waterPhaseIndex the index of the water-bearing phase
   * @param waterIndex the component index of water
   */
  private void updateSystemWithHydrate(int waterPhaseIndex, int waterIndex) {
    // Get water content in the system
    double waterZFraction = system.getPhase(0).getComponent(waterIndex).getz();

    // Check if initial state has aqueous phase
    boolean hasInitialAqueous = false;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == PhaseType.AQUEOUS) {
        hasInitialAqueous = true;
        break;
      }
    }

    // Store current phase information
    int currentNumPhases = system.getNumberOfPhases();
    double[] originalBetas = new double[currentNumPhases];
    double sumOriginalBetas = 0.0;
    for (int i = 0; i < currentNumPhases; i++) {
      originalBetas[i] = system.getBeta(i);
      sumOriginalBetas += originalBetas[i];
    }

    // Add hydrate phase to the active phases
    system.setNumberOfPhases(currentNumPhases + 1);
    system.setPhaseIndex(currentNumPhases, 4);

    // Scale down existing betas to make room for hydrate fraction
    // Total should remain 1.0: sum(scaledBetas) + hydrateFraction = 1.0
    double scaleFactor = (1.0 - hydrateFraction) / sumOriginalBetas;
    for (int i = 0; i < currentNumPhases; i++) {
      system.setBeta(i, originalBetas[i] * scaleFactor);
    }

    // Set the hydrate phase fraction
    system.setBeta(currentNumPhases, hydrateFraction);

    // Set hydrate composition based on cavity occupancy
    updateHydrateComposition();

    // Initialize the system with updated phase fractions
    system.init(1);

    // Order phases by density (hydrate will be at bottom as heaviest)
    system.orderByDensity();
    system.init(1);
  }

  /**
   * Update the hydrate phase composition based on cavity occupancy calculations.
   */
  private void updateHydrateComposition() {
    PhaseInterface hydratePhase = system.getPhase(4);

    // Calculate mole fractions based on cavity occupancy
    // Water is the host, hydrate formers occupy cavities
    double[] moleFractions = new double[hydratePhase.getNumberOfComponents()];

    // Water makes up about 85-87% of hydrate (depending on structure)
    // Structure I: 46 water molecules per unit cell, 8 guest sites
    // Structure II: 136 water molecules per unit cell, 24 guest sites
    double waterFraction = (stableHydrateStructure == 1) ? 46.0 / 54.0 : 136.0 / 160.0;

    int waterCompNum = hydratePhase.getComponent("water").getComponentNumber();
    moleFractions[waterCompNum] = waterFraction;

    // Distribute remaining fraction among hydrate formers based on cavity occupancy
    double guestFraction = 1.0 - waterFraction;
    double totalOccupancy = 0.0;

    for (int i = 0; i < hydratePhase.getNumberOfComponents(); i++) {
      if (hydratePhase.getComponent(i).isHydrateFormer()) {
        // Get cavity occupancy (YKI) for this component
        double yki = ((ComponentHydrate) hydratePhase.getComponent(i))
            .calcYKI(stableHydrateStructure - 1, 0, hydratePhase);
        totalOccupancy += yki;
      }
    }

    if (totalOccupancy > 0) {
      for (int i = 0; i < hydratePhase.getNumberOfComponents(); i++) {
        if (hydratePhase.getComponent(i).isHydrateFormer()) {
          double yki = ((ComponentHydrate) hydratePhase.getComponent(i))
              .calcYKI(stableHydrateStructure - 1, 0, hydratePhase);
          moleFractions[i] = guestFraction * (yki / totalOccupancy);
        }
      }
    }

    // Normalize to ensure sum = 1.0
    double sum = 0.0;
    for (int i = 0; i < hydratePhase.getNumberOfComponents(); i++) {
      sum += moleFractions[i];
    }

    // Set normalized mole fractions
    for (int i = 0; i < hydratePhase.getNumberOfComponents(); i++) {
      if (sum > 0) {
        hydratePhase.getComponent(i).setx(moleFractions[i] / sum);
      } else {
        hydratePhase.getComponent(i).setx(0.0);
      }
    }
  }

  /**
   * Check if hydrate has formed at the current conditions.
   *
   * @return true if hydrate has formed, false otherwise
   */
  public boolean isHydrateFormed() {
    return hydrateFormed;
  }

  /**
   * Get the calculated hydrate phase fraction.
   *
   * @return the hydrate fraction (mole basis)
   */
  public double getHydrateFraction() {
    return hydrateFraction;
  }

  /**
   * Get the stable hydrate structure type.
   *
   * @return 1 for Structure I, 2 for Structure II
   */
  public int getStableHydrateStructure() {
    return stableHydrateStructure;
  }

  /**
   * Get the cavity occupancy for a specific component.
   *
   * @param componentName the name of the component
   * @param structure the hydrate structure (1 or 2)
   * @param cavityType the cavity type (0=small, 1=large)
   * @return the cavity occupancy fraction
   */
  public double getCavityOccupancy(String componentName, int structure, int cavityType) {
    PhaseInterface hydratePhase = system.getPhase(4);
    if (hydratePhase.hasComponent(componentName)) {
      ComponentHydrate comp = (ComponentHydrate) hydratePhase.getComponent(componentName);
      return comp.calcYKI(structure - 1, cavityType, hydratePhase);
    }
    return 0.0;
  }

  /**
   * Check if gas-hydrate only mode is enabled.
   *
   * <p>
   * When enabled, the algorithm will try to achieve gas-hydrate equilibrium without an aqueous
   * phase when water content is low enough.
   * </p>
   *
   * @return true if gas-hydrate only mode is enabled
   */
  public boolean isGasHydrateOnlyMode() {
    return gasHydrateOnlyMode;
  }

  /**
   * Enable or disable gas-hydrate only mode.
   *
   * <p>
   * When enabled, the algorithm will try to achieve gas-hydrate equilibrium without an aqueous
   * phase when water content is low enough for all water to be consumed by hydrate formation.
   * </p>
   *
   * @param gasHydrateOnlyMode true to enable gas-hydrate only mode
   */
  public void setGasHydrateOnlyMode(boolean gasHydrateOnlyMode) {
    this.gasHydrateOnlyMode = gasHydrateOnlyMode;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    TPHydrateFlash other = (TPHydrateFlash) obj;
    if (hydrateFormed != other.hydrateFormed) {
      return false;
    }
    if (Double.compare(hydrateFraction, other.hydrateFraction) != 0) {
      return false;
    }
    if (gasHydrateOnlyMode != other.gasHydrateOnlyMode) {
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + (hydrateFormed ? 1231 : 1237);
    result = prime * result + Double.hashCode(hydrateFraction);
    result = prime * result + (gasHydrateOnlyMode ? 1231 : 1237);
    return result;
  }
}
