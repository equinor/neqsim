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
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CalcSaltSatauration.class);

  String saltName;

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
    SaltData saltData = readSaltData();
    ensureSaltIonsPresent(saltData);

    initialiseSystem();
    int aqueousPhaseNumber = getAqueousPhaseNumber();

    double saturationRatio = calculateSaturationRatio(saltData, aqueousPhaseNumber);
    if (saturationRatio >= 1.0) {
      logger.info("{} is already saturated, SR={}", saltName, saturationRatio);
      return;
    }

    double currentAddition = 0.0;
    double lowerAddition = 0.0;
    double upperAddition = 1.0e-6;
    double upperSaturationRatio = saturationRatio;
    int bracketIterations = 0;

    while (upperSaturationRatio < 1.0 && bracketIterations < 80) {
      lowerAddition = currentAddition;
      addSaltAmount(saltData, upperAddition - currentAddition);
      currentAddition = upperAddition;
      initialiseSystem();
      aqueousPhaseNumber = getAqueousPhaseNumber();
      upperSaturationRatio = calculateSaturationRatio(saltData, aqueousPhaseNumber);
      upperAddition *= 2.0;
      bracketIterations++;
    }

    if (upperSaturationRatio < 1.0) {
      throw new IllegalStateException("Could not bracket salt saturation for " + saltName);
    }

    for (int i = 0; i < 80; i++) {
      double trialAddition = 0.5 * (lowerAddition + upperAddition);
      addSaltAmount(saltData, trialAddition - currentAddition);
      currentAddition = trialAddition;
      initialiseSystem();
      aqueousPhaseNumber = getAqueousPhaseNumber();
      saturationRatio = calculateSaturationRatio(saltData, aqueousPhaseNumber);

      if (Math.abs(saturationRatio - 1.0) < 1.0e-6) {
        break;
      }
      if (saturationRatio < 1.0) {
        lowerAddition = trialAddition;
      } else {
        upperAddition = trialAddition;
      }
    }

    logger.info("solution found for {} in calcSaltSatauration(), SR={}", saltName, saturationRatio);
  }

  /**
   * Reads salt data from the NeqSim COMPSALT database.
   *
   * @return salt data for the requested salt
   */
  private SaltData readSaltData() {
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
      try {
        system.chemicalReactionInit();
      } catch (Exception ex) {
        throw new IllegalStateException("Failed initializing chemical reactions for " + saltName, ex);
      }
      system.createDatabase(true);
      system.setMixingRule(10);
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
   * Initialises thermodynamic and physical properties for the current system state.
   */
  private void initialiseSystem() {
    try {
      new TPflash(system).run();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed running TPflash for salt saturation of " + saltName, ex);
    }
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

    double ionActivityProduct = Math.pow(gamma1 * molality1, saltData.stoc1)
        * Math.pow(gamma2 * molality2, saltData.stoc2);
    return ionActivityProduct / calculateKsp(saltData, phase.getTemperature(), phase.getPressure());
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
      double log10Ksp = -59.3498 - 0.041377 * temperatureK + 2.1963 / temperatureK + 24.5724 * Math.log10(temperatureK)
          + 2.518e-5 * temperatureK * temperatureK;
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
  }
}
