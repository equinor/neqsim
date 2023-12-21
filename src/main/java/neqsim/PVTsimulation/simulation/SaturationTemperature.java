package neqsim.PVTsimulation.simulation;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>
 * SaturationPressure class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SaturationTemperature extends BasePVTsimulation {
  /**
   * <p>
   * Constructor for SaturationPressure.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SaturationTemperature(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * <p>
   * calcSaturationPressure.
   * </p>
   *
   * @return a double
   */
  public double calcSaturationTemperature() {
    boolean isMultiPhaseCheckChanged = false;
    if (!getThermoSystem().doMultiPhaseCheck()) {
      isMultiPhaseCheckChanged = true;
      getThermoSystem().setMultiPhaseCheck(true);
    }
    do {
      getThermoSystem().setTemperature(getThermoSystem().getTemperature() - 10.0);
      thermoOps.TPflash();
    } while (getThermoSystem().getNumberOfPhases() == 1
        && getThermoSystem().getTemperature() > 30.0);
    do {
      getThermoSystem().setTemperature(getThermoSystem().getTemperature() + 10.0);
      thermoOps.TPflash();
    } while (getThermoSystem().getNumberOfPhases() > 1
        && getThermoSystem().getTemperature() < 1200.0);
    double minTemp = getThermoSystem().getTemperature() - 10.0;
    double maxTemp = getThermoSystem().getTemperature();
    int iteration = 0;
    do {
      iteration++;
      getThermoSystem().setTemperature((minTemp + maxTemp) / 2.0);
      thermoOps.TPflash();
      if (getThermoSystem().getNumberOfPhases() > 1) {
        minTemp = getThermoSystem().getTemperature();
      } else {
        maxTemp = getThermoSystem().getTemperature();
      }
    } while (Math.abs(maxTemp - minTemp) > 1e-5 && iteration < 500);
    getThermoSystem().setTemperature(maxTemp);
    thermoOps.TPflash();
    if (isMultiPhaseCheckChanged) {
      getThermoSystem().setMultiPhaseCheck(false);
    }
    return getThermoSystem().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    super.run();
    saturationTemperature = calcSaturationTemperature();
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 20, 60.0);
    tempSystem.addComponent("nitrogen", 0.34);
    tempSystem.addComponent("CO2", 3.59);
    tempSystem.addComponent("methane", 67.42);
    tempSystem.addComponent("ethane", 9.02);
    tempSystem.addComponent("propane", 4.31);
    tempSystem.addComponent("i-butane", 0.93);
    tempSystem.addComponent("n-butane", 1.71);
    tempSystem.addComponent("i-pentane", 0.74);
    tempSystem.addComponent("n-pentane", 0.85);
    tempSystem.addComponent("n-hexane", 0.38);
    tempSystem.addTBPfraction("C7", 0.5, 109.00 / 1000.0, 0.6912);
    tempSystem.addTBPfraction("C8", 0.69, 120.20 / 1000.0, 0.7255);
    tempSystem.addTBPfraction("C9", 0.14, 129.5 / 1000.0, 0.7454);
    tempSystem.addTBPfraction("C10", 0.08, 135.3 / 1000.0, 0.7864);
    // tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2); // "HV", "UNIFAC_UMRPRU");
    tempSystem.init(0);
    tempSystem.init(1);
    // tempSystem.saveFluid(928);

    SimulationInterface satPresSim = new SaturationTemperature(tempSystem);
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
}
