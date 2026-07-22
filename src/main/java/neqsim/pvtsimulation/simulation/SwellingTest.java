package neqsim.pvtsimulation.simulation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * SwellingTest class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SwellingTest extends BasePVTsimulation {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SwellingTest.class);

  double[] gasInjected = null;
  private double[] pressures = null;
  private double[] relativeOilVolume = null;
  SystemInterface injectionGas;

  /**
   * Constructor for SwellingTest.
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SwellingTest(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * Setter for the field <code>injectionGas</code>.
   *
   * @param injectionGas a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setInjectionGas(SystemInterface injectionGas) {
    this.injectionGas = injectionGas;
  }

  /**
   * setCummulativeMolePercentGasInjected.
   *
   * @param gasInjected an array of type double
   */
  public void setCummulativeMolePercentGasInjected(double[] gasInjected) {
    this.gasInjected = gasInjected;
    setPressures(new double[gasInjected.length]);
    setRelativeOilVolume(new double[gasInjected.length]);
  }

  /**
   * runCalc.
   */
  public void runCalc() {
    double oldInjected = 0.0;
    // thermoOps.TPflash();
    try {
      thermoOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    double orginalOilVolume = getThermoSystem().getVolume();
    double oilMoles = getThermoSystem().getTotalNumberOfMoles();

    for (int i = 0; i < getPressures().length; i++) {
      if (gasInjected[i] > 1e-10) {
        injectionGas.setTotalFlowRate(oilMoles * (gasInjected[i] - oldInjected) / 100.0, "mol/sec");
        injectionGas.init(0);
        injectionGas.init(1);
        oldInjected = gasInjected[i];

        getThermoSystem().init(0);
        getThermoSystem().addFluid(injectionGas);
      }
      getThermoSystem().init(0);
      if (i == 0) {
        getThermoSystem().setPressure(10.0);
      }
      getThermoSystem().setTemperature(temperature);
      try {
        thermoOps.bubblePointPressureFlash(false);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      pressures[i] = getThermoSystem().getPressure();
      getRelativeOilVolume()[i] = getThermoSystem().getVolume() / orginalOilVolume;
    }

    for (int i = 0; i < getPressures().length; i++) {
      logger.info("pressure " + getPressures()[i] + " relativeOil volume " + getRelativeOilVolume()[i]);
    }
  }

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface oilSystem = new SystemSrkEos(298.0, 50);
    oilSystem.addComponent("methane", 5.01);
    oilSystem.addComponent("propane", 0.01);
    oilSystem.addTBPfraction("C10", 100.0, 145.0 / 1000.0, 0.82);
    oilSystem.addTBPfraction("C12", 120.0, 175.0 / 1000.0, 0.85);
    oilSystem.createDatabase(true);
    oilSystem.setMixingRule(2);

    SystemInterface gasSystem = new SystemSrkEos(298.0, 50);
    gasSystem.addComponent("methane", 110.0);
    gasSystem.addComponent("ethane", 10.0);
    gasSystem.createDatabase(true);
    gasSystem.setMixingRule(2);

    SwellingTest test = new SwellingTest(oilSystem);
    test.setInjectionGas(gasSystem);
    test.setTemperature(298.15);
    test.setCummulativeMolePercentGasInjected(
        new double[] { 0.0, 0.01, 0.02, 0.03, 0.05, 0.1, 0.2, 0.4, 0.5, 1.0, 10.3, 22.0 });
    test.runCalc();

    test.getThermoSystem().display();
  }

  /**
   * Getter for the field <code>relativeOilVolume</code>.
   *
   * @return the relativeOilVolume
   */
  public double[] getRelativeOilVolume() {
    return relativeOilVolume;
  }

  /**
   * Setter for the field <code>relativeOilVolume</code>.
   *
   * @param relativeOilVolume the relativeOilVolume to set
   */
  public void setRelativeOilVolume(double[] relativeOilVolume) {
    this.relativeOilVolume = relativeOilVolume;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getPressures() {
    return pressures;
  }

  /** {@inheritDoc} */
  @Override
  public void setPressures(double[] pressures) {
    this.pressures = pressures;
  }
}
