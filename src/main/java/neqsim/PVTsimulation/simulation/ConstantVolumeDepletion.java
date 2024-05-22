package neqsim.PVTsimulation.simulation;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.PVTsimulation.util.parameterfitting.CVDFunction;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>
 * ConstantVolumeDepletion class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ConstantVolumeDepletion extends BasePVTsimulation {
  static Logger logger = LogManager.getLogger(ConstantVolumeDepletion.class);

  private double[] relativeVolume = null;
  double[] totalVolume = null;
  double[] liquidVolumeRelativeToVsat = null;
  double[] liquidVolume = null;
  boolean saturationConditionFound = false;
  private double[] liquidRelativeVolume = null;
  private double[] Zmix;
  private double[] Zgas = null;
  private double[] cummulativeMolePercDepleted = null;
  double[] temperatures = null;
  double[] pressure = null;

  /**
   * <p>
   * Constructor for ConstantVolumeDepletion.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ConstantVolumeDepletion(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * <p>
   * setTemperaturesAndPressures.
   * </p>
   *
   * @param temperature an array of {@link double} objects
   * @param pressure an array of {@link double} objects
   */
  public void setTemperaturesAndPressures(double[] temperature, double[] pressure) {
    this.pressure = pressure;
    this.temperatures = temperature;
    experimentalData = new double[temperature.length][1];
  }

  /**
   * <p>
   * calcSaturationConditions.
   * </p>
   */
  public void calcSaturationConditions() {
    getThermoSystem().setPressure(1.0);
    do {
      getThermoSystem().setPressure(getThermoSystem().getPressure() + 10.0);
    } while (getThermoSystem().getNumberOfPhases() == 1
        && getThermoSystem().getPressure() < 1000.0);
    do {
      getThermoSystem().setPressure(getThermoSystem().getPressure() + 10.0);
      thermoOps.TPflash();
    } while (getThermoSystem().getNumberOfPhases() > 1 && getThermoSystem().getPressure() < 1000.0);
    double minPres = getThermoSystem().getPressure() - 10.0;
    double maxPres = getThermoSystem().getPressure();
    do {
      getThermoSystem().setPressure((minPres + maxPres) / 2.0);
      thermoOps.TPflash();
      if (getThermoSystem().getNumberOfPhases() > 1) {
        minPres = getThermoSystem().getPressure();
      } else {
        maxPres = getThermoSystem().getPressure();
      }
    } while (Math.abs(maxPres - minPres) > 1e-5);
    /*
     * try { thermoOps.dewPointPressureFlash(); } catch (Exception ex) {
     * logger.error(ex.getMessage(), ex); }
     */
    saturationVolume = getThermoSystem().getVolume();
    saturationPressure = getThermoSystem().getPressure();
    Zsaturation = getThermoSystem().getZ();
    saturationConditionFound = true;
  }

  /**
   * <p>
   * runCalc.
   * </p>
   */
  public void runCalc() {
    saturationConditionFound = false;
    relativeVolume = new double[pressures.length];
    totalVolume = new double[pressures.length];
    liquidVolumeRelativeToVsat = new double[pressures.length];
    liquidVolume = new double[pressures.length];
    Zgas = new double[pressures.length];
    Zmix = new double[pressures.length];
    liquidRelativeVolume = new double[pressures.length];
    cummulativeMolePercDepleted = new double[pressures.length];
    double totalNumberOfMoles = getThermoSystem().getTotalNumberOfMoles();
    getThermoSystem().setTemperature(temperature, temperatureUnit);

    for (int i = 0; i < pressures.length; i++) {
      getThermoSystem().setPressure(pressures[i]);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      // getThermoSystem().display();
      totalVolume[i] = getThermoSystem().getVolume();
      System.out.println("volume " + totalVolume[i]);
      cummulativeMolePercDepleted[i] =
          100.0 - getThermoSystem().getTotalNumberOfMoles() / totalNumberOfMoles * 100;
      if (getThermoSystem().getNumberOfPhases() > 1) {
        if (!saturationConditionFound) {
          calcSaturationConditions();
          getThermoSystem().setPressure(pressures[i]);
          try {
            thermoOps.TPflash();
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
          }
        }

        // if (totalVolume[i] > saturationVolume) {
        liquidVolume[i] = getThermoSystem().getPhase(1).getVolume();
        liquidVolumeRelativeToVsat[i] = liquidVolume[i] / saturationVolume;
        Zgas[i] = getThermoSystem().getPhase(0).getZ();
        Zmix[i] = getThermoSystem().getZ();
        if (getThermoSystem().getNumberOfPhases() > 1) {
          liquidRelativeVolume[i] =
              getThermoSystem().getPhase("oil").getVolume() / saturationVolume * 100;
        }

        double volumeCorrection = getThermoSystem().getVolume() - saturationVolume;
        double test = volumeCorrection / getThermoSystem().getPhase(0).getMolarVolume();

        for (int j = 0; j < getThermoSystem().getPhase(0).getNumberOfComponents(); j++) {
          try {
            double change =
                (test * getThermoSystem().getPhase(0).getComponent(j).getx() < getThermoSystem()
                    .getPhase(0).getComponent(j).getNumberOfMolesInPhase())
                        ? test * getThermoSystem().getPhase(0).getComponent(j).getx()
                        : test * getThermoSystem().getPhase(0).getComponent(j).getx();
            getThermoSystem().addComponent(j, -change);
          } catch (Exception e) {
            logger.debug(e.getMessage());
          }
        }
      }
    }

    for (int i = 0; i < pressures.length; i++) {
      relativeVolume[i] = totalVolume[i] / saturationVolume;
      // System.out.println("rel volume " + relativeVolume[i]);
    }
    // System.out.println("test finished");
  }

  /**
   * <p>
   * runTuning.
   * </p>
   */
  public void runTuning() {
    ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

    try {
      System.out.println("adding....");

      for (int i = 0; i < experimentalData[0].length; i++) {
        CVDFunction function = new CVDFunction();
        double[] guess = new double[] {234.0 / 1000.0}; // getThermoSystem().getCharacterization().getPlusFractionModel().getMPlus()/1000.0};
        function.setInitialGuess(guess);

        SystemInterface tempSystem = getThermoSystem(); // getThermoSystem().clone();

        tempSystem.setTemperature(temperature, temperatureUnit);
        tempSystem.setPressure(pressures[i]);
        // thermoOps.TPflash();
        // tempSystem.display();
        double[] sample1 = {temperatures[i], pressures[i]};
        double relativeVolume = experimentalData[0][i];
        double[] standardDeviation1 = {1.5};
        SampleValue sample =
            new SampleValue(relativeVolume, relativeVolume / 50.0, sample1, standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(tempSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error", ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);

    optimizer = new LevenbergMarquardt();
    optimizer.setMaxNumberOfIterations(20);

    optimizer.setSampleSet(sampleSet);
    optimizer.solve();
    runCalc();
    // optim.displayCurveFit();
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface tempSystem = new SystemSrkEos(298.0, 211.0);
    /*
     * tempSystem.addComponent("nitrogen", 0.64); tempSystem.addComponent("CO2", 3.53);
     * tempSystem.addComponent("methane", 70.78); tempSystem.addComponent("ethane", 8.94);
     * tempSystem.addComponent("propane", 5.05); tempSystem.addComponent("i-butane", 0.85);
     * tempSystem.addComponent("n-butane", 1.68); tempSystem.addComponent("iC5", 0.62);
     * tempSystem.addComponent("n-pentane", 0.79); tempSystem.addComponent("n-hexane", 0.83);
     * tempSystem.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324); tempSystem.addTBPfraction("C8",
     * 1.06, 104.6 / 1000.0, 0.7602); tempSystem.addTBPfraction("C9", 0.79, 119.1 / 1000.0, 0.7677);
     * tempSystem.addTBPfraction("C10", 0.57, 133.0 / 1000.0, 0.79);
     * tempSystem.addTBPfraction("C11", 0.38, 155.0 / 1000.0, 0.795);
     * tempSystem.addTBPfraction("C12", 0.37, 162.0 / 1000.0, 0.806);
     * tempSystem.addTBPfraction("C13", 0.32, 177.0 / 1000.0, 0.824);
     * tempSystem.addTBPfraction("C14", 0.27, 198.0 / 1000.0, 0.835);
     * tempSystem.addTBPfraction("C15", 0.23, 202.0 / 1000.0, 0.84);
     * tempSystem.addTBPfraction("C16", 0.19, 215.0 / 1000.0, 0.846);
     * tempSystem.addTBPfraction("C17", 0.17, 234.0 / 1000.0, 0.84);
     * tempSystem.addTBPfraction("C18", 0.13, 251.0 / 1000.0, 0.844);
     * tempSystem.addTBPfraction("C19", 0.13, 270.0 / 1000.0, 0.854);
     * tempSystem.addPlusFraction("C20", 0.62, 381.0 / 1000.0, 0.88);
     * tempSystem.getCharacterization().getLumpingModel(). setNumberOfLumpedComponents(6);
     * tempSystem.getCharacterization().characterisePlusFraction();
     */
    // tempSystem.addComponent("methane", 70.78);
    // tempSystem.addComponent("nC10", 5.83);
    // tempSystem.createDatabase(true);
    // tempSystem.setMixingRule(2);

    // SystemInterface tempSystem = new SystemSrkEos(273.15 + 120, 100.0);
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
    tempSystem.addTBPfraction("C7", 1.5, 109.00 / 1000.0, 0.6912);
    tempSystem.addTBPfraction("C8", 1.69, 120.20 / 1000.0, 0.7255);
    tempSystem.addTBPfraction("C9", 1.14, 129.5 / 1000.0, 0.7454);
    tempSystem.addTBPfraction("C10", 0.8, 135.3 / 1000.0, 0.7864);
    tempSystem.addPlusFraction("C11", 4.58, 256.2 / 1000.0, 0.8398);
    // tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);
    tempSystem.init(0);
    tempSystem.init(1);

    ConstantVolumeDepletion CVDsim = new ConstantVolumeDepletion(tempSystem);
    CVDsim.setTemperature(315.0, "K");
    CVDsim.setPressures(new double[] {400, 300.0, 200.0, 150.0, 100.0, 50.0});
    CVDsim.runCalc();
    CVDsim.setTemperaturesAndPressures(new double[] {313, 313, 313, 313},
        new double[] {400, 300.0, 200.0, 100.0});
    double[][] expData = {{0.95, 0.99, 1.0, 1.1}};
    CVDsim.setExperimentalData(expData);
    // CVDsim.runTuning();
  }

  /**
   * <p>
   * Getter for the field <code>relativeVolume</code>.
   * </p>
   *
   * @return the relativeVolume
   */
  public double[] getRelativeVolume() {
    return relativeVolume;
  }

  /** {@inheritDoc} */
  @Override
  public double getSaturationPressure() {
    return saturationPressure;
  }

  /**
   * <p>
   * getZmix.
   * </p>
   *
   * @return the Zmix
   */
  public double[] getZmix() {
    return Zmix;
  }

  /**
   * <p>
   * getZgas.
   * </p>
   *
   * @return the Zgas
   */
  public double[] getZgas() {
    return Zgas;
  }

  /**
   * <p>
   * Getter for the field <code>liquidRelativeVolume</code>.
   * </p>
   *
   * @return the liquidRelativeVolume
   */
  public double[] getLiquidRelativeVolume() {
    return liquidRelativeVolume;
  }

  /**
   * <p>
   * Getter for the field <code>cummulativeMolePercDepleted</code>.
   * </p>
   *
   * @return the cummulativeMolePercDepleted
   */
  public double[] getCummulativeMolePercDepleted() {
    return cummulativeMolePercDepleted;
  }
}
