package neqsim.pvtsimulation.simulation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SeparatorTest class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SeparatorTest extends BasePVTsimulation {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SeparatorTest.class);

  double[] temperature = null;
  double[] pressure = null;
  double[] Sm3gas, m3oil;
  private double[] Bofactor;
  private double[] GOR = null;
  double oilVolumeStdCond = 0;

  /**
   * <p>
   * Constructor for SeparatorTest.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SeparatorTest(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * <p>
   * setSeparatorConditions.
   * </p>
   *
   * @param temperature an array of type double
   * @param pressure an array of type double
   */
  public void setSeparatorConditions(double[] temperature, double[] pressure) {
    this.pressure = pressure;
    this.temperature = temperature;
  }

  /**
   * <p>
   * runCalc.
   * </p>
   */
  public void runCalc() {
    Sm3gas = new double[pressure.length];
    m3oil = new double[pressure.length];
    GOR = new double[pressure.length];
    Bofactor = new double[pressure.length];
    for (int i = 0; i < pressure.length; i++) {
      thermoOps.setSystem(getThermoSystem());
      getThermoSystem().setPressure(pressure[i]);
      getThermoSystem().setTemperature(temperature[i]);
      thermoOps.TPflash();
      if (getThermoSystem().getNumberOfPhases() > 1) {
        m3oil[i] = getThermoSystem().getPhase(1).getVolume();
      } else {
        m3oil[i] = getThermoSystem().getVolume();
      }
      if (getThermoSystem().getNumberOfPhases() > 1
          && getThermoSystem().getPhase(0).getType() == PhaseType.GAS) {
        getThermoSystem().getPhase(0)
            .setPressure(ThermodynamicConstantsInterface.referencePressure);
        getThermoSystem().getPhase(0).setTemperature(288.15);
        getThermoSystem().init(1);
        Sm3gas[i] = getThermoSystem().getPhase(0).getVolume();
        setThermoSystem(getThermoSystem().phaseToSystem(1));
      }
    }
    getThermoSystem().setPressure(ThermodynamicConstantsInterface.referencePressure);
    getThermoSystem().setTemperature(288.15);
    thermoOps.TPflash();
    oilVolumeStdCond = getThermoSystem().getPhase("oil").getVolume();

    for (int i = 0; i < pressure.length; i++) {
      if (Sm3gas[i] > 1e-10) {
        GOR[i] = Sm3gas[i] / oilVolumeStdCond;
        Bofactor[i] = m3oil[i] / oilVolumeStdCond;
      }
      logger.info("GOR " + getGOR()[i] + " Bo " + Bofactor[i]);
    }
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
    SystemInterface tempSystem = new SystemSrkEos(298.0, 300.0);
    tempSystem.addComponent("nitrogen", 0.64);
    tempSystem.addComponent("CO2", 3.53);
    tempSystem.addComponent("methane", 70.78);
    tempSystem.addComponent("ethane", 8.94);
    tempSystem.addComponent("propane", 5.05);
    tempSystem.addComponent("i-butane", 0.85);
    tempSystem.addComponent("n-butane", 1.68);
    tempSystem.addComponent("i-pentane", 0.62);
    tempSystem.addComponent("n-pentane", 0.79);
    tempSystem.addComponent("n-hexane", 0.83);
    tempSystem.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324);
    tempSystem.addTBPfraction("C8", 1.06, 104.6 / 1000.0, 0.7602);
    tempSystem.addTBPfraction("C9", 0.79, 119.1 / 1000.0, 0.7677);
    tempSystem.addTBPfraction("C10", 0.57, 133.0 / 1000.0, 0.79);
    tempSystem.addTBPfraction("C11", 0.38, 155.0 / 1000.0, 0.795);
    tempSystem.addTBPfraction("C12", 0.37, 162.0 / 1000.0, 0.806);
    tempSystem.addTBPfraction("C13", 0.32, 177.0 / 1000.0, 0.824);
    tempSystem.addTBPfraction("C14", 0.27, 198.0 / 1000.0, 0.835);
    tempSystem.addTBPfraction("C15", 0.23, 202.0 / 1000.0, 0.84);
    tempSystem.addTBPfraction("C16", 0.19, 215.0 / 1000.0, 0.846);
    tempSystem.addTBPfraction("C17", 0.17, 234.0 / 1000.0, 0.84);
    tempSystem.addTBPfraction("C18", 0.13, 251.0 / 1000.0, 0.844);
    tempSystem.addTBPfraction("C19", 0.13, 270.0 / 1000.0, 0.854);
    tempSystem.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.88);
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);

    SeparatorTest sepSim = new SeparatorTest(tempSystem);
    double[] temps = {313.15, 313.15, 313.15, 313.15, 313.15, 313.15, 313.15};
    double[] pres =
        {500, 400, 200, 100, 50.0, 5.0, ThermodynamicConstantsInterface.referencePressure};
    sepSim.setSeparatorConditions(temps, pres);
    sepSim.runCalc();
  }

  /**
   * <p>
   * getGOR.
   * </p>
   *
   * @return the GOR
   */
  public double[] getGOR() {
    return GOR;
  }

  /**
   * <p>
   * getBofactor.
   * </p>
   *
   * @return the Bofactor
   */
  public double[] getBofactor() {
    return Bofactor;
  }
}
