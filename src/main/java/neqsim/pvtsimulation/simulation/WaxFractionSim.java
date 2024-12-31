package neqsim.pvtsimulation.simulation;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.util.parameterfitting.WaxFunction;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * WaxFractionSim class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class WaxFractionSim extends BasePVTsimulation {
  static Logger logger = LogManager.getLogger(WaxFractionSim.class);

  double[] temperature = null;

  double[] pressure = null;
  private double[] waxFraction = null;
  double[] Sm3gas;
  double[] m3oil;

  private double[] Bofactor;
  private double[] GOR = null;
  double oilVolumeStdCond = 0;

  /**
   * <p>
   * Constructor for WaxFractionSim.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public WaxFractionSim(SystemInterface tempSystem) {
    super(tempSystem);
    temperature = new double[1];
    pressure = new double[1];
    temperature[0] = tempSystem.getTemperature();
    pressure[0] = tempSystem.getPressure();
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
    this.pressure = pressure;
    this.temperature = temperature;
    experimentalData = new double[temperature.length][1];
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
        ArrayList<Double> guessArray = new ArrayList<Double>();
        for (int p = 0; p < 3; p++) {
          guessArray.add(getThermoSystem().getWaxModel().getWaxParameters()[p]);
        }
        guessArray.add(getThermoSystem().getWaxModel().getParameterWaxHeatOfFusion()[0]);
        guessArray.add(getThermoSystem().getWaxModel().getParameterWaxTriplePointTemperature()[0]);

        // double[] guess = getThermoSystem().getWaxModel().getWaxParameters();
        double[] guess = new double[optimizer.getNumberOfTuningParameters()];
        for (int o = 0; o < guess.length; o++) {
          guess[o] = guessArray.get(o);
        }
        // guess = guessArray.subList(0, optimizer.getNumberOfTuningParameters()-1);

        WaxFunction function = new WaxFunction();
        function.setInitialGuess(guess);

        SystemInterface tempSystem = getThermoSystem(); // getThermoSystem().clone();

        tempSystem.setTemperature(temperature[i]);
        tempSystem.setPressure(pressure[i]);
        thermoOps.TPflash();
        // tempSystem.display();
        double[] sample1 = {temperature[i]};
        double waxContent = experimentalData[0][i];
        double[] standardDeviation1 = {1.5};
        SampleValue sample =
            new SampleValue(waxContent, waxContent / 10.0 + 0.1, sample1, standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(tempSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error", ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);

    optimizer.setSampleSet(sampleSet);
    optimizer.solve();
    // runCalc();
    // optim.displayCurveFit();
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
    waxFraction = new double[pressure.length];
    Bofactor = new double[pressure.length];
    for (int i = 0; i < pressure.length; i++) {
      // thermoOps.setSystem(getThermoSystem());
      getThermoSystem().setPressure(pressure[i]);
      getThermoSystem().setTemperature(temperature[i]);
      thermoOps.TPflash();
      waxFraction[i] = 0.0;
      if (getThermoSystem().hasPhaseType("wax")) {
        waxFraction[i] =
            getThermoSystem().getWtFraction(getThermoSystem().getPhaseNumberOfPhase("wax"));
      }
      // System.out.println("wax fraction " + waxFraction[i]);
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
    NeqSimDataBase.setConnectionString(
        "jdbc:derby:C:/Users/esol/OneDrive - Equinor/temp/neqsimthermodatabase");
    NeqSimDataBase.setCreateTemporaryTables(true);

    SystemInterface tempSystem = new SystemSrkEos(298.0, 10.0);
    tempSystem.addComponent("methane", 6.78);

    tempSystem.addTBPfraction("C19", 10.13, 170.0 / 1000.0, 0.7814);
    tempSystem.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.850871882888);

    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.getWaxModel().addTBPWax();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);
    tempSystem.addSolidComplexPhase("wax");
    tempSystem.setMultiphaseWaxCheck(true);
    tempSystem.setMultiPhaseCheck(true);
    tempSystem.init(0);
    tempSystem.init(1);
    NeqSimDataBase.setCreateTemporaryTables(false);
    WaxFractionSim sepSim = new WaxFractionSim(tempSystem);
    double[] temps = {293.15, 283.15, 273.15, 264.15, 263, 262, 261};
    double[] pres = {5, 5, 5.0, 5.0, 5.0, 5.0, 5.0};
    sepSim.setTemperaturesAndPressures(temps, pres);

    sepSim.runCalc();
    sepSim.getThermoSystem().display();
    double[][] expData = {{4, 7, 9, 10, 11, 12, 13}};
    sepSim.setExperimentalData(expData);
    // String[] params = {"Mplus", "waxParam1", "waxParam2"};
    // sepSim.getOptimizer().setTuningParameters("")
    sepSim.getOptimizer().setNumberOfTuningParameters(3);
    sepSim.getOptimizer().setMaxNumberOfIterations(20);
    sepSim.runTuning();
    sepSim.runCalc();
    // double a = sepSim.getWaxFraction()[0];

    // sepSim.tuneModel(exptemperatures, exppressures, expwaxFrations);
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

  /**
   * <p>
   * Getter for the field <code>waxFraction</code>.
   * </p>
   *
   * @return the waxFraction
   */
  public double[] getWaxFraction() {
    return waxFraction;
  }
}
