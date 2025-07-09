package neqsim.pvtsimulation.simulation;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SaturationPressure class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SaturationPressure extends BasePVTsimulation {
  /**
   * <p>
   * Constructor for SaturationPressure.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SaturationPressure(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * <p>
   * calcSaturationPressure.
   * </p>
   *
   * @return a double
   */
  public double calcSaturationPressure() {
    if (!Double.isNaN(temperature)) {
      getThermoSystem().setTemperature(temperature, temperatureUnit);
    }

    boolean isMultiPhaseCheckChanged = false;
    if (!getThermoSystem().doMultiPhaseCheck()) {
      isMultiPhaseCheckChanged = true;
      getThermoSystem().setMultiPhaseCheck(true);
    }
    // getThermoSystem().isImplementedCompositionDeriativesofFugacity(false);
    getThermoSystem().setPressure(1.0);
    do {
      getThermoSystem().setPressure(getThermoSystem().getPressure() + 10.0);
      thermoOps.TPflash();
    } while (getThermoSystem().getNumberOfPhases() == 1
        && getThermoSystem().getPressure() < 1000.0);
    do {
      getThermoSystem().setPressure(getThermoSystem().getPressure() + 10.0);
      thermoOps.TPflash();
    } while (getThermoSystem().getNumberOfPhases() > 1 && getThermoSystem().getPressure() < 1000.0);

    if (getThermoSystem().getPressure() > 1000.0) {
      return getThermoSystem().getPressure();
    }

    double minPres = getThermoSystem().getPressure() - 10.0;
    double maxPres = getThermoSystem().getPressure();
    int iteration = 0;
    do {
      iteration++;
      getThermoSystem().setPressure((minPres + maxPres) / 2.0);
      thermoOps.TPflash();
      if (getThermoSystem().getNumberOfPhases() > 1) {
        minPres = getThermoSystem().getPressure();
      } else {
        maxPres = getThermoSystem().getPressure();
      }
    } while (Math.abs(maxPres - minPres) > 1e-5 && iteration < 500);
    getThermoSystem().setPressure(maxPres);
    thermoOps.TPflash();
    if (isMultiPhaseCheckChanged) {
      getThermoSystem().setMultiPhaseCheck(false);
    }
    return getThermoSystem().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    super.run();
    saturationPressure = calcSaturationPressure();
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
    // SystemInterface tempSystem = new SystemSrkCPAstatoil(273.15 + 120, 100.0);
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 120, 100.0);
    tempSystem.addComponent("nitrogen", 0.34);
    tempSystem.addComponent("CO2", 3.59);
    tempSystem.addComponent("methane", 67.42);
    tempSystem.addComponent("ethane", 9.02);
    tempSystem.addComponent("propane", 4.31);
    tempSystem.addComponent("i-butane", 0.93);
    tempSystem.addComponent("n-butane", 1.71);
    tempSystem.addComponent("i-pentane", 0.74);
    tempSystem.addComponent("n-pentane", 0.85);
    tempSystem.addComponent("n-hexane", 1.38);
    
    // Using the new method with boiling point and molar mass
    // C7: TB ≈ 371.6 K, MW ≈ 0.109 kg/mol
    tempSystem.addTBPfractionFromBoilingPoint("C7_new", 1.5, 371.6, 109.00 / 1000.0);
    
    // Traditional method for comparison
    tempSystem.addTBPfraction("C7", 1.5, 109.00 / 1000.0, 0.6912);
    tempSystem.addTBPfraction("C8", 1.69, 120.20 / 1000.0, 0.7255);
    tempSystem.addTBPfraction("C9", 1.14, 129.5 / 1000.0, 0.7454);
    tempSystem.addTBPfraction("C10", 0.8, 135.3 / 1000.0, 0.7864);
    tempSystem.addPlusFraction("C11", 4.58, 256.2 / 1000.0, 0.8398);
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(9); // "HV", "UNIFAC_UMRPRU");
    tempSystem.init(0);
    tempSystem.init(1);
    // tempSystem.saveFluid(928);

    SimulationInterface satPresSim = new SaturationPressure(tempSystem);
    satPresSim.run();
    // satPresSim.getThermoSystem().display();
    /*
     * double saturationPressure = 350.0; double saturationTemperature = 273.15 + 80;
     *
     * TuningInterface tuning = new TuneToSaturation(satPresSim);
     * tuning.setSaturationConditions(saturationTemperature, saturationPressure); tuning.run();
     * tuning.getSimulation().getThermoSystem().display();
     */
  }

  /** {@inheritDoc} */
  @Override
  public double getSaturationPressure() {
    return saturationPressure;
  }
}
