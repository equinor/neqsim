package neqsim.pvtsimulation.simulation;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.util.parameterfitting.CMEFunction;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ConstantMassExpansion class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ConstantMassExpansion extends BasePVTsimulation {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ConstantMassExpansion.class);

  double[] relativeVolume = null;
  double[] totalVolume = null;
  private double[] liquidRelativeVolume = null;
  private double[] viscosity = null;
  private double[] viscosityOil = null;
  private double[] Zgas = null;
  private double[] density = null;
  private double[] Yfactor = null;
  private double[] isoThermalCompressibility = null;
  private double[] gasExpensionFactor = null;
  private double[] gasFormationVolumeFactor = null;
  private double[] Bg = null;
  private double[] gasStandardVolume = null;
  private double[] gasVolume = null;
  boolean saturationConditionFound = false;
  private double saturationIsoThermalCompressibility = 0.0;
  double[] temperatures = null;

  /**
   * <p>
   * Constructor for ConstantMassExpansion.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ConstantMassExpansion(SystemInterface tempSystem) {
    super(tempSystem);
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
      thermoOps.TPflash();
      // System.out.println("pressure "+ getThermoSystem().getPressure() );
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

    getThermoSystem().initPhysicalProperties();
    saturationVolume = getThermoSystem().getPhase(0).getMass()
        / getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    saturationPressure = getThermoSystem().getPressure();
    Zsaturation = getThermoSystem().getZ();
    saturationConditionFound = true;
  }

  /** {@inheritDoc} */
  @Override
  public double getSaturationPressure() {
    return saturationPressure;
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
    liquidRelativeVolume = new double[pressures.length];
    Zgas = new double[pressures.length];
    Bg = new double[pressures.length];
    gasStandardVolume = new double[pressures.length];
    gasVolume = new double[pressures.length];
    density = new double[pressures.length];
    Yfactor = new double[pressures.length];
    isoThermalCompressibility = new double[pressures.length];
    gasFormationVolumeFactor = new double[pressures.length];
    viscosity = new double[pressures.length];
    viscosityOil = new double[pressures.length];
    gasExpensionFactor = new double[pressures.length];
    if (!Double.isNaN(temperature)) {
      getThermoSystem().setTemperature(temperature, temperatureUnit);
    }
    if (!saturationConditionFound) {
      calcSaturationConditions();
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    try {
      // getThermoSystem().setPressure(400);
      // thermoOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    saturationVolume = getThermoSystem().getVolume();
    saturationPressure = getThermoSystem().getPressure();
    Zsaturation = getThermoSystem().getZ();
    saturationIsoThermalCompressibility = -1.0 / getThermoSystem().getPhase(0).getVolume()
        / getThermoSystem().getPhase(0).getdPdVTn();

    for (int i = 0; i < pressures.length; i++) {
      // getThermoSystem().init(0);
      getThermoSystem().setPressure(pressures[i]);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      getThermoSystem().initPhysicalProperties();
      // getThermoSystem().display();
      totalVolume[i] = getThermoSystem().getVolume();
      relativeVolume[i] = totalVolume[i] / saturationVolume;
      density[i] = getThermoSystem().getPhase(0).getDensity("kg/m3");
      gasVolume[i] = getThermoSystem().getPhase(0).getNumberOfMolesInPhase()
          * getThermoSystem().getPhase(0).getMolarMass() / density[i]; // getThermoSystem().getPhase(0).getVolume();
      gasStandardVolume[i] =
          getThermoSystem().getPhase(0).getVolume() * getThermoSystem().getPhase(0).getPressure()
              / ThermodynamicConstantsInterface.referencePressure
              / getThermoSystem().getPhase(0).getZ() * 288.15 / getThermoSystem().getTemperature();
      Bg[i] = gasVolume[i] * 1e5 / gasStandardVolume[i];
      Zgas[i] = getThermoSystem().getPhase(0).getZ();
      if (getThermoSystem().getNumberOfPhases() == 1) {
        viscosity[i] = getThermoSystem().getPhase(0).getViscosity();
        isoThermalCompressibility[i] = -1.0 / getThermoSystem().getPhase(0).getVolume()
            / getThermoSystem().getPhase(0).getdPdVTn();
        Yfactor[i] = Double.NaN;
        liquidRelativeVolume[i] = Double.NaN;
      }
      if (getThermoSystem().getNumberOfPhases() > 1) {
        liquidRelativeVolume[i] =
            getThermoSystem().getPhase("oil").getVolume() / saturationVolume * 100;
        Yfactor[i] = ((saturationPressure - pressures[i]) / pressures[i])
            / ((totalVolume[i] - saturationVolume) / saturationVolume);
        viscosity[i] = Double.NaN;
        isoThermalCompressibility[i] = Double.NaN;
      }
      // System.out.println("pressure " + getThermoSystem().getPressure() + " relative volume "
      // + relativeVolume[i] + " liquid rel vol " + liquidRelativeVolume[i] + " Zgas " + Zgas[i]
      // + " Yfactor " + getYfactor()[i] + " isoCompfactor " + getIsoThermalCompressibility()[i]);
    }

    // System.out.println("test finished");
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
        CMEFunction function = new CMEFunction();
        double[] guess = new double[] {
            getThermoSystem().getCharacterization().getPlusFractionModel().getMPlus() / 1000.0};
        function.setInitialGuess(guess);

        SystemInterface tempSystem = getThermoSystem(); // getThermoSystem().clone();

        tempSystem.setTemperature(temperature);
        tempSystem.setPressure(pressures[i]);
        // thermoOps.TPflash();
        // tempSystem.display();
        double[] sample1 = {temperature, pressures[i]};
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
    optimizer.setMaxNumberOfIterations(5);

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
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 73.0, 10.0);
    tempSystem.addComponent("nitrogen", 0.972);
    tempSystem.addComponent("CO2", 0.632);
    tempSystem.addComponent("methane", 95.111);
    tempSystem.addComponent("ethane", 2.553);
    tempSystem.addComponent("propane", 0.104);
    tempSystem.addComponent("i-butane", 0.121);
    tempSystem.addComponent("n-butane", 0.021);
    tempSystem.addComponent("i-pentane", 0.066);
    tempSystem.addComponent("n-pentane", 0.02);

    tempSystem.addTBPfraction("C6", 0.058, 86.18 / 1000.0, 664.0e-3);
    tempSystem.addTBPfraction("C7", 0.107, 96.0 / 1000.0, 738.0e-3);
    tempSystem.addTBPfraction("C8", 0.073, 107.0 / 1000.0, 765.0e-3);
    tempSystem.addTBPfraction("C9", 0.044, 121.0 / 1000.0, 781.0e-3);
    tempSystem.addPlusFraction("C10", 0.118, 190.0 / 1000.0, 813.30e-3);
    tempSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    tempSystem.getCharacterization().setLumpingModel("PVTlumpingModel");
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);
    tempSystem.init(0);
    tempSystem.init(1);
    /*
     * tempSystem.addComponent("nitrogen", 0.6); tempSystem.addComponent("CO2", 3.34);
     * tempSystem.addComponent("methane", 74.16); tempSystem.addComponent("ethane", 7.9);
     * tempSystem.addComponent("propane", 4.15); tempSystem.addComponent("i-butane", 0.71);
     * tempSystem.addComponent("n-butane", 0.71); tempSystem.addComponent("i-pentane", 0.66);
     * tempSystem.addComponent("n-pentane", 0.66); tempSystem.addComponent("n-hexane", 0.81);
     * tempSystem.addTBPfraction("C7", 1.2, 91.0 / 1000.0, 0.746); tempSystem.addTBPfraction("C8",
     * 1.15, 104.0 / 1000.0, 0.770); tempSystem.addTBPfraction("C9", 5.15, 125.0 / 1000.0, 0.8);
     */

    ConstantMassExpansion CMEsim = new ConstantMassExpansion(tempSystem);
    // CMEsim.runCalc();
    // double a = CMEsim.getSaturationPressure();

    CMEsim.setTemperaturesAndPressures(
        new double[] {273.15 + 73.9, 273.15 + 73.9, 273.15 + 73.9, 273.15 + 73.9, 273.15 + 73.9},
        new double[] {400, 300.0, 250.0, 200.0, 100.0});
    double[][] expData = {{0.95, 0.99, 1.12, 1.9}};
    CMEsim.setExperimentalData(expData);
    // CMEsim.runTuning();
    CMEsim.runCalc();
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
   * getYfactor.
   * </p>
   *
   * @return the Yfactor
   */
  public double[] getYfactor() {
    return Yfactor;
  }

  /**
   * <p>
   * Getter for the field <code>density</code>.
   * </p>
   *
   * @return the density
   */
  public double[] getDensity() {
    return density;
  }

  /**
   * <p>
   * Getter for the field <code>viscosity</code>.
   * </p>
   *
   * @return the gas viscosity
   */
  public double[] getViscosity() {
    return viscosity;
  }

  /**
   * <p>
   * getBg.
   * </p>
   *
   * @return the gas volume formation factor
   */
  public double[] getBg() {
    return Bg;
  }

  /**
   * <p>
   * Getter for the field <code>isoThermalCompressibility</code>.
   * </p>
   *
   * @return the isoThermalCompressibility
   */
  public double[] getIsoThermalCompressibility() {
    return isoThermalCompressibility;
  }

  /**
   * <p>
   * Getter for the field <code>saturationIsoThermalCompressibility</code>.
   * </p>
   *
   * @return the saturationIsoThermalCompressibility
   */
  public double getSaturationIsoThermalCompressibility() {
    return saturationIsoThermalCompressibility;
  }

  /**
   * <p>
   * setTemperaturesAndPressures.
   * </p>
   *
   * @param temperature an array of type double
   * @param pressure an array of type double
   */
  public void setTemperaturesAndPressures(double[] temperature, double[] pressure) {
    this.pressures = pressure;
    this.temperatures = temperature;
    experimentalData = new double[temperature.length][1];
  }
}
