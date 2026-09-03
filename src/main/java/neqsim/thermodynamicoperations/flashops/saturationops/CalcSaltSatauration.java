package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.flashops.TPflash;

/**
 * calcSaltSatauration class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CalcSaltSatauration extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private static final int MAX_SATURATION_ITERATIONS = 80;
  private static final double SATURATION_RATIO_TOLERANCE = 1.0e-6;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CalcSaltSatauration.class);

  String saltName;
  private transient SaltData saltDataCache;
  private transient SaltSaturationResult result;
  private transient int thermodynamicInitializationCount;

  /**
   * Constructor for calcSaltSatauration.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param name a {@link java.lang.String} object
   */
  public CalcSaltSatauration(SystemInterface system, String name) {
    super(system);
    this.saltName = name;
    logger.info("ok ");
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    result = null;
    thermodynamicInitializationCount = 0;

    SaltData saltData = readSaltData();
    ensureSaltIonsPresent(saltData);

    initialiseSystem();
    int aqueousPhaseNumber = getAqueousPhaseNumber();

    double saturationRatio = calculateSaturationRatio(saltData, aqueousPhaseNumber);
    double initialSaturationRatio = saturationRatio;
    if (saturationRatio >= 1.0) {
      boolean converged = Math.abs(saturationRatio - 1.0) < SATURATION_RATIO_TOLERANCE;
      result = new SaltSaturationResult(saltName, initialSaturationRatio, saturationRatio, 0.0, 0, 0,
          thermodynamicInitializationCount, true, converged, false);
      logger.info("{} is already saturated, SR={}", saltName, saturationRatio);
      return;
    }

    double lowerAddition = 0.0;
    double upperAddition = 1.0e-6;
    double upperSaturationRatio = saturationRatio;
    double acceptedAddition = 0.0;
    int bracketIterations = 0;
    int solveIterations = 0;

    SystemInterface baseSystem = system.clone();

    if (system instanceof neqsim.thermo.system.SystemPitzer || "FeCO3".equals(saltName)) {
      while (upperSaturationRatio < 1.0 && bracketIterations < MAX_SATURATION_ITERATIONS) {
        upperSaturationRatio = calculateSaturationRatioForAddition(baseSystem, saltData, upperAddition);
        if (upperSaturationRatio < 1.0) {
          lowerAddition = upperAddition;
          upperAddition *= 2.0;
        }
        bracketIterations++;
      }

      if (upperSaturationRatio < 1.0) {
        throw new IllegalStateException("Could not bracket salt saturation for " + saltName);
      }

      for (int i = 0; i < MAX_SATURATION_ITERATIONS; i++) {
        double trialAddition = 0.5 * (lowerAddition + upperAddition);
        saturationRatio = calculateSaturationRatioForAddition(baseSystem, saltData, trialAddition);
        solveIterations++;

        if (Math.abs(saturationRatio - 1.0) < SATURATION_RATIO_TOLERANCE) {
          lowerAddition = trialAddition;
          upperAddition = trialAddition;
          break;
        }
        if (saturationRatio < 1.0) {
          lowerAddition = trialAddition;
        } else {
          upperAddition = trialAddition;
        }
      }

      acceptedAddition = 0.5 * (lowerAddition + upperAddition);
      addSaltAmount(saltData, acceptedAddition);
      initialiseSystem();
    } else {
      double currentAddition = 0.0;

      while (upperSaturationRatio < 1.0 && bracketIterations < MAX_SATURATION_ITERATIONS) {
        lowerAddition = currentAddition;
        addSaltAmount(saltData, upperAddition - currentAddition);
        currentAddition = upperAddition;
        initialiseSystem();
        aqueousPhaseNumber = getAqueousPhaseNumber();
        upperSaturationRatio = calculateSaturationRatio(saltData, aqueousPhaseNumber);
        if (upperSaturationRatio < 1.0) {
          upperAddition *= 2.0;
        }
        bracketIterations++;
      }

      if (upperSaturationRatio < 1.0) {
        throw new IllegalStateException("Could not bracket salt saturation for " + saltName);
      }

      for (int i = 0; i < MAX_SATURATION_ITERATIONS; i++) {
        double trialAddition = 0.5 * (lowerAddition + upperAddition);
        addSaltAmount(saltData, trialAddition - currentAddition);
        currentAddition = trialAddition;
        initialiseSystem();
        aqueousPhaseNumber = getAqueousPhaseNumber();
        saturationRatio = calculateSaturationRatio(saltData, aqueousPhaseNumber);
        solveIterations++;

        if (Math.abs(saturationRatio - 1.0) < SATURATION_RATIO_TOLERANCE) {
          break;
        }
        if (saturationRatio < 1.0) {
          lowerAddition = trialAddition;
        } else {
          upperAddition = trialAddition;
        }
      }
      acceptedAddition = currentAddition;
    }

    aqueousPhaseNumber = getAqueousPhaseNumber();
    saturationRatio = calculateSaturationRatio(saltData, aqueousPhaseNumber);
    boolean converged = Math.abs(saturationRatio - 1.0) < SATURATION_RATIO_TOLERANCE;
    boolean iterationLimitReached = !converged && solveIterations >= MAX_SATURATION_ITERATIONS;
    result = new SaltSaturationResult(saltName, initialSaturationRatio, saturationRatio, acceptedAddition,
        bracketIterations, solveIterations, thermodynamicInitializationCount, false, converged, iterationLimitReached);

    logger.info("solution found for {} in calcSaltSatauration(), SR={}", saltName, saturationRatio);
  }

  /**
   * Returns diagnostics for the completed dissolved-salt saturation calculation.
   *
   * @return immutable convergence and work diagnostics
   * @throws java.lang.IllegalStateException if {@link #run()} has not completed successfully
   */
  public SaltSaturationResult getResult() {
    if (result == null) {
      throw new IllegalStateException("Salt saturation has not completed for " + saltName);
    }
    return result;
  }

  /**
   * Precipitates a supersaturated pure salt and leaves the residual fluid at activity equilibrium.
   *
   * <p>
   * Trial extents are evaluated on fresh clones. The accepted extent is then removed stoichiometrically from the real
   * system, which is reflashed so gas, oil and aqueous phase properties remain process-composable. The returned result
   * is the solid ledger; the solid is not added as a thermodynamic phase.
   * </p>
   *
   * @return immutable precipitation amount, saturation and material-balance diagnostics
   */
  public SaltPrecipitationResult precipitate() {
    SaltData saltData = readSaltData();
    ensureSaltIonsPresent(saltData);
    initialisePrecipitationSystem();

    int aqueousPhaseNumber = getAqueousPhaseNumber();
    double initialSaturationRatio = calculateSaturationRatio(saltData, aqueousPhaseNumber);
    if (!Double.isFinite(initialSaturationRatio) || initialSaturationRatio < 0.0) {
      throw new IllegalStateException(
          "Invalid initial saturation ratio for " + saltName + ": " + initialSaturationRatio);
    }

    double initialIon1Moles = system.getComponent(saltData.name1).getNumberOfmoles();
    double initialIon2Moles = system.getComponent(saltData.name2).getNumberOfmoles();
    double initialWaterMoles = saltData.waterstoc > 0.0 ? system.getComponent("water").getNumberOfmoles() : 0.0;
    if (initialSaturationRatio <= 1.0) {
      return new SaltPrecipitationResult(saltName, 0.0, 0.0, initialSaturationRatio, initialSaturationRatio, 0.0);
    }

    double maximumExtent = Math.min(initialIon1Moles / saltData.stoc1, initialIon2Moles / saltData.stoc2);
    if (saltData.waterstoc > 0.0) {
      maximumExtent = Math.min(maximumExtent, initialWaterMoles / saltData.waterstoc);
    }
    if (!(maximumExtent > 0.0) || !Double.isFinite(maximumExtent)) {
      throw new IllegalStateException("No finite positive precipitation extent is available for " + saltName);
    }

    SystemInterface baseSystem = system.clone();
    double lowerExtent = 0.0;
    double upperExtent = maximumExtent * (1.0 - 1.0e-12);
    double upperSaturationRatio = calculateSaturationRatioForRemoval(baseSystem, saltData, upperExtent);
    if (!(upperSaturationRatio < 1.0)) {
      throw new IllegalStateException(
          "Could not bracket precipitation equilibrium for " + saltName + ", SR=" + upperSaturationRatio);
    }

    double acceptedExtent = Double.NaN;
    for (int iteration = 0; iteration < 100; iteration++) {
      double trialExtent = 0.5 * (lowerExtent + upperExtent);
      double trialSaturationRatio = calculateSaturationRatioForRemoval(baseSystem, saltData, trialExtent);
      acceptedExtent = trialExtent;
      if (Math.abs(Math.log10(trialSaturationRatio)) <= 1.0e-8) {
        break;
      }
      if (trialSaturationRatio > 1.0) {
        lowerExtent = trialExtent;
      } else {
        upperExtent = trialExtent;
      }
    }

    removeSaltAmount(saltData, acceptedExtent);
    initialisePrecipitationSystem();
    double finalSaturationRatio = calculateSaturationRatio(saltData, getAqueousPhaseNumber());

    double ion1BalanceResidual = initialIon1Moles - system.getComponent(saltData.name1).getNumberOfmoles()
        - saltData.stoc1 * acceptedExtent;
    double ion2BalanceResidual = initialIon2Moles - system.getComponent(saltData.name2).getNumberOfmoles()
        - saltData.stoc2 * acceptedExtent;
    double waterBalanceResidual = saltData.waterstoc > 0.0
        ? initialWaterMoles - system.getComponent("water").getNumberOfmoles() - saltData.waterstoc * acceptedExtent
        : 0.0;
    double maximumBalanceResidual = Math.max(Math.max(Math.abs(ion1BalanceResidual), Math.abs(ion2BalanceResidual)),
        Math.abs(waterBalanceResidual));
    double solidMolarMassGrams = 1000.0 * (saltData.stoc1 * system.getComponent(saltData.name1).getMolarMass()
        + saltData.stoc2 * system.getComponent(saltData.name2).getMolarMass()
        + saltData.waterstoc * system.getComponent("water").getMolarMass());
    return new SaltPrecipitationResult(saltName, acceptedExtent, acceptedExtent * solidMolarMassGrams,
        initialSaturationRatio, finalSaturationRatio, maximumBalanceResidual);
  }

  /**
   * Returns the current activity-based saturation ratio after a complete TP flash.
   *
   * @return current ion-activity product divided by the COMPSALT solubility product
   */
  double getCurrentSaturationRatio() {
    SaltData saltData = readSaltData();
    ensureSaltIonsPresent(saltData);
    initialisePrecipitationSystem();
    return calculateSaturationRatio(saltData, getAqueousPhaseNumber());
  }

  /**
   * Dissolves at most the supplied amount of an existing pure-solid ledger.
   *
   * <p>
   * If all available solid can dissolve while the aqueous phase remains undersaturated, the full amount is returned.
   * Otherwise a clone-isolated bisection adds just enough stoichiometric ions to restore saturation equilibrium.
   * </p>
   *
   * @param availableSolidMoles available pure-solid formula units in mol
   * @return formula units dissolved in mol
   */
  double dissolve(double availableSolidMoles) {
    if (!(availableSolidMoles > 0.0) || !Double.isFinite(availableSolidMoles)) {
      throw new IllegalArgumentException("Available solid amount must be finite and positive for " + saltName);
    }
    SaltData saltData = readSaltData();
    ensureSaltIonsPresent(saltData);
    initialisePrecipitationSystem();
    double initialSaturationRatio = calculateSaturationRatio(saltData, getAqueousPhaseNumber());
    if (initialSaturationRatio >= 1.0) {
      return 0.0;
    }

    SystemInterface baseSystem = system.clone();
    double upperSaturationRatio = calculateSaturationRatioForDissolution(baseSystem, saltData, availableSolidMoles);
    double acceptedDissolution;
    if (upperSaturationRatio <= 1.0) {
      acceptedDissolution = availableSolidMoles;
    } else {
      double lowerDissolution = 0.0;
      double upperDissolution = availableSolidMoles;
      acceptedDissolution = Double.NaN;
      for (int iteration = 0; iteration < 100; iteration++) {
        double trialDissolution = 0.5 * (lowerDissolution + upperDissolution);
        double trialSaturationRatio = calculateSaturationRatioForDissolution(baseSystem, saltData, trialDissolution);
        acceptedDissolution = trialDissolution;
        if (Math.abs(Math.log10(trialSaturationRatio)) <= 1.0e-8) {
          break;
        }
        if (trialSaturationRatio < 1.0) {
          lowerDissolution = trialDissolution;
        } else {
          upperDissolution = trialDissolution;
        }
      }
    }

    addSaltAmount(saltData, acceptedDissolution);
    initialisePrecipitationSystem();
    return acceptedDissolution;
  }

  /** @return first COMPSALT ion name */
  String getIon1Name() {
    return readSaltData().name1;
  }

  /** @return second COMPSALT ion name */
  String getIon2Name() {
    return readSaltData().name2;
  }

  /** @return first-ion stoichiometric coefficient per formula unit */
  double getIon1Stoichiometry() {
    return readSaltData().stoc1;
  }

  /** @return second-ion stoichiometric coefficient per formula unit */
  double getIon2Stoichiometry() {
    return readSaltData().stoc2;
  }

  /** @return crystallization-water stoichiometry per formula unit */
  double getWaterStoichiometry() {
    return readSaltData().waterstoc;
  }

  /** @return pure-solid molar mass in grams per mole of formula units */
  double getSolidMolarMassGrams() {
    SaltData saltData = readSaltData();
    ensureSaltIonsPresent(saltData);
    return 1000.0 * (saltData.stoc1 * system.getComponent(saltData.name1).getMolarMass()
        + saltData.stoc2 * system.getComponent(saltData.name2).getMolarMass()
        + saltData.waterstoc * system.getComponent("water").getMolarMass());
  }

  private double calculateSaturationRatioForRemoval(SystemInterface baseSystem, SaltData saltData, double saltAmount) {
    SystemInterface originalSystem = system;
    try {
      system = createTrialSystem(baseSystem);
      removeSaltAmount(saltData, saltAmount);
      initialisePrecipitationSystem();
      return calculateSaturationRatio(saltData, getAqueousPhaseNumber());
    } finally {
      system = originalSystem;
    }
  }

  /** Evaluates dissolution on a fresh clone using the same full-flash path as precipitation. */
  private double calculateSaturationRatioForDissolution(SystemInterface baseSystem, SaltData saltData,
      double saltAmount) {
    SystemInterface originalSystem = system;
    try {
      system = createTrialSystem(baseSystem);
      addSaltAmount(saltData, saltAmount);
      initialisePrecipitationSystem();
      return calculateSaturationRatio(saltData, getAqueousPhaseNumber());
    } finally {
      system = originalSystem;
    }
  }

  /**
   * Calculates the saturation ratio after adding a trial salt amount to a fresh clone of a base system.
   *
   * @param baseSystem initialized system before the trial salt addition
   * @param saltData salt data from COMPSALT
   * @param saltAmount amount of salt formula units to add in moles
   * @return saturation ratio for the trial state
   */
  private double calculateSaturationRatioForAddition(SystemInterface baseSystem, SaltData saltData, double saltAmount) {
    SystemInterface originalSystem = system;
    try {
      system = createTrialSystem(baseSystem);
      addSaltAmount(saltData, saltAmount);
      initialiseSystem();
      return calculateSaturationRatio(saltData, getAqueousPhaseNumber());
    } finally {
      system = originalSystem;
    }
  }

  /**
   * Reads salt data from the NeqSim COMPSALT database.
   *
   * @return salt data for the requested salt
   */
  private SaltData readSaltData() {
    if (saltDataCache != null) {
      return saltDataCache;
    }
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = database
            .getResultSet("SELECT * FROM compsalt WHERE SaltName='" + saltName + "'")) {
      if (!dataSet.next()) {
        throw new IllegalArgumentException("Salt not found in COMPSALT database: " + saltName);
      }
      SaltData data = new SaltData();
      data.name1 = dataSet.getString("ion1").trim();
      data.name2 = dataSet.getString("ion2").trim();
      data.stoc1 = Double.parseDouble(dataSet.getString("stoc1"));
      data.stoc2 = Double.parseDouble(dataSet.getString("stoc2"));
      data.kspwater = Double.parseDouble(dataSet.getString("Kspwater"));
      data.kspwater2 = Double.parseDouble(dataSet.getString("Kspwater2"));
      data.kspwater3 = Double.parseDouble(dataSet.getString("Kspwater3"));
      data.kspwater4 = Double.parseDouble(dataSet.getString("Kspwater4"));
      data.kspwater5 = Double.parseDouble(dataSet.getString("Kspwater5"));
      data.vdelta = Double.parseDouble(dataSet.getString("Vdelta"));
      data.waterstoc = Double.parseDouble(dataSet.getString("waterstoc"));
      if (data.waterstoc < 0.0 || !Double.isFinite(data.waterstoc)) {
        throw new IllegalStateException("Invalid crystallization-water stoichiometry for " + saltName);
      }
      saltDataCache = data;
      return data;
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed reading COMPSALT data for " + saltName, ex);
    }
  }

  /**
   * Ensures the salt ions are components in the thermodynamic system before the saturation solve.
   *
   * @param saltData salt data from COMPSALT
   */
  private void ensureSaltIonsPresent(SaltData saltData) {
    boolean hadChemicalReactionOperations = system.getChemicalReactionOperations() != null;
    boolean addedComponent = false;
    if (!hasComponent(saltData.name1)) {
      system.addComponent(saltData.name1, 1.0e-20);
      addedComponent = true;
    }
    if (!hasComponent(saltData.name2)) {
      system.addComponent(saltData.name2, 1.0e-20);
      addedComponent = true;
    }
    if (addedComponent) {
      boolean preserveNonreactivePitzerTopology = system instanceof neqsim.thermo.system.SystemPitzer
          && !hadChemicalReactionOperations;
      if (!preserveNonreactivePitzerTopology) {
        try {
          system.chemicalReactionInit();
        } catch (Exception ex) {
          throw new IllegalStateException("Failed initializing chemical reactions for " + saltName, ex);
        }
      }
      system.createDatabase(true);
      if (system instanceof neqsim.thermo.system.SystemPitzer) {
        system.setMixingRule("classic");
      } else {
        system.setMixingRule(10);
      }
    }
  }

  /**
   * Returns true if the system already contains a named component.
   *
   * @param componentName component name to search for
   * @return true if any phase contains the component
   */
  private boolean hasComponent(String componentName) {
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      if (system.getPhase(phaseNumber).hasComponent(componentName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Creates a trial clone with chemical reactions bound to the clone itself.
   *
   * @param baseSystem initialized system before the trial salt addition
   * @return cloned trial system ready for flash calculations
   */
  private SystemInterface createTrialSystem(SystemInterface baseSystem) {
    SystemInterface trialSystem = baseSystem.clone();
    if (trialSystem.isChemicalSystem() || trialSystem.getChemicalReactionOperations() != null) {
      try {
        trialSystem.chemicalReactionInit();
      } catch (Exception ex) {
        throw new IllegalStateException("Failed initializing chemical reactions for trial saturation of " + saltName,
            ex);
      }
      trialSystem.createDatabase(true);
      if (trialSystem instanceof neqsim.thermo.system.SystemPitzer) {
        trialSystem.setMixingRule("classic");
      } else {
        trialSystem.setMixingRule(10);
      }
    }
    return trialSystem;
  }

  /**
   * Initialises thermodynamic and physical properties for the current system state.
   */
  private void initialiseSystem() {
    thermodynamicInitializationCount++;
    if (system instanceof neqsim.thermo.system.SystemPitzer) {
      neqsim.thermo.system.SystemPitzer pitzerSystem = (neqsim.thermo.system.SystemPitzer) system;
      system.init(0);
      pitzerSystem.refreshDefaultPitzerParameterSelection();
    }
    try {
      new TPflash(system).run();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed running TPflash for salt saturation of " + saltName, ex);
    }
    system.initPhysicalProperties();
  }

  /** Reflashes a precipitation trial so accepted phase amounts and properties are self-consistent. */
  private void initialisePrecipitationSystem() {
    try {
      new TPflash(system).run();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed running precipitation TPflash for " + saltName, ex);
    }
    system.init(1);
    system.initPhysicalProperties();
  }

  /**
   * Finds the aqueous phase, falling back to the water-containing phase for single-phase brines.
   *
   * @return phase number of the aqueous/water phase
   */
  private int getAqueousPhaseNumber() {
    int phaseNumber = system.getPhaseNumberOfPhase("aqueous");
    if (phaseNumber >= 0) {
      return phaseNumber;
    }
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).hasComponent("water")) {
        return i;
      }
    }
    throw new IllegalStateException("No aqueous or water-containing phase available for salt saturation");
  }

  /**
   * Adds a stoichiometric salt amount to the system as dissociated ions.
   *
   * @param saltData salt data from COMPSALT
   * @param saltAmount amount of salt formula units to add in moles
   */
  private void addSaltAmount(SaltData saltData, double saltAmount) {
    system.addComponent(saltData.name1, saltData.stoc1 * saltAmount);
    system.addComponent(saltData.name2, saltData.stoc2 * saltAmount);
    if (saltData.waterstoc > 0.0) {
      system.addComponent("water", saltData.waterstoc * saltAmount);
    }
  }

  /**
   * Removes a stoichiometric salt amount from the system as dissociated ions.
   *
   * @param saltData salt data from COMPSALT
   * @param saltAmount amount of salt formula units to remove in moles
   */
  private void removeSaltAmount(SaltData saltData, double saltAmount) {
    addSaltAmount(saltData, -saltAmount);
  }

  /**
   * Calculates saturation ratio for the selected salt using aqueous ion activities.
   *
   * @param saltData salt data from COMPSALT
   * @param phaseNumber aqueous phase number
   * @return saturation ratio, IAP/Ksp
   */
  private double calculateSaturationRatio(SaltData saltData, int phaseNumber) {
    PhaseInterface phase = system.getPhase(phaseNumber);
    if (!phase.hasComponent(saltData.name1) || !phase.hasComponent(saltData.name2)) {
      return 0.0;
    }
    int waterComponentNumber = phase.getComponent("water").getComponentNumber();
    double waterDenominator = phase.getComponent("water").getx() * phase.getComponent("water").getMolarMass();
    if (waterDenominator <= 0.0) {
      return 0.0;
    }

    ComponentInterface component1 = phase.getComponent(saltData.name1);
    ComponentInterface component2 = phase.getComponent(saltData.name2);
    double molality1 = component1.getx() / waterDenominator;
    double molality2 = component2.getx() / waterDenominator;
    if (molality1 <= 0.0 || molality2 <= 0.0) {
      return 0.0;
    }

    double gamma1 = phase.getActivityCoefficient(component1.getComponentNumber(), waterComponentNumber);
    double gamma2 = phase.getActivityCoefficient(component2.getComponentNumber(), waterComponentNumber);
    if (gamma1 <= 0.0 || gamma2 <= 0.0 || Double.isNaN(gamma1) || Double.isNaN(gamma2) || Double.isInfinite(gamma1)
        || Double.isInfinite(gamma2)) {
      return 0.0;
    }

    double logIonActivityProduct = saltData.stoc1 * Math.log(gamma1 * molality1)
        + saltData.stoc2 * Math.log(gamma2 * molality2);
    if (saltData.waterstoc > 0.0) {
      ComponentInterface water = phase.getComponent(waterComponentNumber);
      double waterActivity = water.getx();
      if (water.calcActivity()) {
        waterActivity *= phase.getActivityCoefficient(waterComponentNumber, waterComponentNumber);
      }
      if (!(waterActivity > 0.0) || !Double.isFinite(waterActivity)) {
        return 0.0;
      }
      logIonActivityProduct += saltData.waterstoc * Math.log(waterActivity);
    }
    double logSaturationRatio = logIonActivityProduct
        - Math.log(calculateKsp(saltData, phase.getTemperature(), phase.getPressure()));
    if (logSaturationRatio > 700.0) {
      return Math.exp(700.0);
    }
    if (logSaturationRatio < -745.0) {
      return 0.0;
    }
    return Math.exp(logSaturationRatio);
  }

  /**
   * Returns the authoritative COMPSALT solubility product at a specified state.
   *
   * <p>
   * This package-level view lets scientific qualification code reuse the exact mineral correlation and pressure
   * correction used by precipitation calculations. It does not initialise or mutate the thermodynamic system.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara absolute pressure in bara
   * @return solubility product on the COMPSALT standard state
   */
  double getSolubilityProduct(double temperatureK, double pressureBara) {
    if (!(temperatureK > 0.0) || !Double.isFinite(temperatureK)) {
      throw new IllegalArgumentException("Temperature must be finite and positive");
    }
    if (!(pressureBara > 0.0) || !Double.isFinite(pressureBara)) {
      throw new IllegalArgumentException("Pressure must be finite and positive");
    }
    return calculateKsp(readSaltData(), temperatureK, pressureBara);
  }

  /**
   * Calculates Ksp from the same COMPSALT correlations used by scale-potential calculations.
   *
   * @param saltData salt data from COMPSALT
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return solubility product
   */
  private double calculateKsp(SaltData saltData, double temperatureK, double pressureBara) {
    double ksp;
    if (saltName.equals("NaCl")) {
      ksp = 92.78 - 0.407 * temperatureK + 0.000747 * temperatureK * temperatureK;
    } else if (saltName.equals("CaCO3")) {
      double log10Ksp = -171.9065 - 0.077993 * temperatureK + 2839.319 / temperatureK
          + 71.595 * Math.log10(temperatureK);
      ksp = Math.pow(10.0, log10Ksp);
    } else if (saltName.equals("FeCO3")) {
      double log10Ksp = -59.3498 - 0.041377 * temperatureK - 2.1963 / temperatureK + 24.5724 * Math.log10(temperatureK);
      ksp = Math.pow(10.0, log10Ksp);
    } else {
      double lnKsp = saltData.kspwater / temperatureK + saltData.kspwater2 + Math.log(temperatureK) * saltData.kspwater3
          + temperatureK * saltData.kspwater4 + saltData.kspwater5 / (temperatureK * temperatureK);
      ksp = Math.exp(lnKsp);
    }
    if (Math.abs(saltData.vdelta) > 1.0e-10 && pressureBara > 1.013) {
      double gasConstantCm3Bar = 83.1446;
      double deltaPbar = pressureBara - 1.01325;
      double lnCorrection = -saltData.vdelta * deltaPbar / (gasConstantCm3Bar * temperatureK);
      if (lnCorrection > 50.0) {
        lnCorrection = 50.0;
      } else if (lnCorrection < -50.0) {
        lnCorrection = -50.0;
      }
      ksp *= Math.exp(lnCorrection);
    }
    return ksp;
  }

  /**
   * Data holder for one COMPSALT row.
   */
  private static class SaltData {
    private String name1;
    private String name2;
    private double stoc1;
    private double stoc2;
    private double kspwater;
    private double kspwater2;
    private double kspwater3;
    private double kspwater4;
    private double kspwater5;
    private double vdelta;
    private double waterstoc;
  }
}
