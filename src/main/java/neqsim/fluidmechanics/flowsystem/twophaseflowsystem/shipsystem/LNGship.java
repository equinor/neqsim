package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.shipsystem;

import java.text.DecimalFormat;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.standards.StandardInterface;
import neqsim.standards.gasquality.Standard_ISO6578;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.standards.gasquality.Standard_ISO6976_2016;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * LNGship class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class LNGship
    extends neqsim.fluidmechanics.flowsystem.twophaseflowsystem.TwoPhaseFlowSystem {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(LNGship.class);

  double[] temperature = null;
  double dailyBoilOffRatio = 0.005;
  public double totalTankVolume = 140000;
  private double liquidDensity = 0.7;
  public int numberOffTimeSteps = 100;
  private double initialTemperature = 111.0;
  private boolean setInitialTemperature = false;
  private neqsim.thermo.system.SystemInterface thermoSystem = null;
  public double initialNumberOffMoles;
  double molarBoilOffRate = 0.0;
  public double dailyBoilOffVolume = 0.0;
  private double endTime = 960; // 24.0 * 10;
  private Standard_ISO6976 standardISO6976 = null;
  StandardInterface standardDensity = null;
  double[] WI = null;
  double[] density = null;
  public double[] volume = null;
  double[] xmethane;
  double[] xethane;
  double[] xpropane;
  double[] xiC4;
  double[] xnC4;
  double[] xiC5;
  double[] xnC5;
  double[] xnC6;
  double[] xnitrogen;
  double[] ymethane;
  double[] yethane;
  double[] ypropane;
  double[] yiC4;
  double[] ynC4;
  double[] yiC5;
  double[] ynC5;
  double[] ynC6;
  double[] ynitrogen;
  double[] GCV;
  double[] GCVmass;
  double[] totalEnergy;
  double[] time;
  public double[] tankTemperature = null;
  double timeStep = 0;
  private String[][] resultTable = null;
  private boolean backCalculate = false;
  public double endVolume = 0.0;

  /**
   * <p>
   * Constructor for LNGship.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param totalTankVolume a double
   * @param dailyBoilOffRatio a double
   */
  public LNGship(neqsim.thermo.system.SystemInterface thermoSystem, double totalTankVolume,
      double dailyBoilOffRatio) {
    this.thermoSystem = thermoSystem;
    this.totalTankVolume = totalTankVolume;
    this.dailyBoilOffRatio = dailyBoilOffRatio;

    setStandardISO6976(new Standard_ISO6976_2016(thermoSystem, 0, 25, "volume"));
  }

  /**
   * <p>
   * useStandardVersion.
   * </p>
   *
   * @param isoName a {@link java.lang.String} object
   * @param version a {@link java.lang.String} object
   */
  public void useStandardVersion(String isoName, String version) {
    if (version.equals("2016")) {
      setStandardISO6976(new Standard_ISO6976_2016(thermoSystem, getStandardISO6976().getVolRefT(),
          getStandardISO6976().getEnergyRefT(), "volume"));
      logger.info("using  ISO6976 version 2016");
    } else {
      setStandardISO6976(new Standard_ISO6976(thermoSystem, getStandardISO6976().getVolRefT(),
          getStandardISO6976().getEnergyRefT(), "volume"));
      logger.info("using  ISO6976 version 1995");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void createSystem() {
    getThermoSystem().init(0);
    thermoOperations = new ThermodynamicOperations(getThermoSystem());
    try {
      if (isSetInitialTemperature()) {
        getThermoSystem().setTemperature(getInitialTemperature());
      } else {
        thermoOperations.bubblePointTemperatureFlash();
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    getThermoSystem().init(0);

    standardDensity = new Standard_ISO6578(getThermoSystem());
    standardDensity.calculate();

    liquidDensity = standardDensity.getValue("density");
    logger.info("density start " + standardDensity.getValue("density"));
    timeStep = getEndTime() / (numberOffTimeSteps * 1.0 - 1.0);
    dailyBoilOffVolume = totalTankVolume * dailyBoilOffRatio;

    logger.info("daily boiloff volume " + dailyBoilOffVolume);
    initialNumberOffMoles =
        totalTankVolume * getLiquidDensity() / getThermoSystem().getPhase(1).getMolarMass();
    double oldMoles = getThermoSystem().getTotalNumberOfMoles();

    for (int i = 0; i < getThermoSystem().getPhase(0).getNumberOfComponents(); i++) {
      getThermoSystem().addComponent(getThermoSystem().getPhase(0).getComponent(i).getName(),
          (initialNumberOffMoles - oldMoles)
              * getThermoSystem().getPhase(0).getComponent(i).getz());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void init() {}

  /** {@inheritDoc} */
  @Override
  public void solveSteadyState(int type, UUID id) {
    // TODO: double[] times = {0.0}; ?
    try {
      if (!isSetInitialTemperature()) {
        thermoOperations.bubblePointTemperatureFlash();
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    logger.info("temperature start " + getThermoSystem().getTemperature());
    // TODO: getTimeSeries().init(this);
    calcIdentifier = id;
  }

  /** {@inheritDoc} */
  @Override
  public void solveTransient(int type, UUID id) {
    SystemInterface tempThermoSystem = getThermoSystem().clone();
    WI = new double[numberOffTimeSteps];
    GCV = new double[numberOffTimeSteps];
    GCVmass = new double[numberOffTimeSteps];
    totalEnergy = new double[numberOffTimeSteps];
    time = new double[numberOffTimeSteps];
    density = new double[numberOffTimeSteps];
    tankTemperature = new double[numberOffTimeSteps];
    volume = new double[numberOffTimeSteps];

    xnitrogen = new double[numberOffTimeSteps];
    xmethane = new double[numberOffTimeSteps];
    xethane = new double[numberOffTimeSteps];
    xpropane = new double[numberOffTimeSteps];
    xiC4 = new double[numberOffTimeSteps];
    xnC4 = new double[numberOffTimeSteps];
    xiC5 = new double[numberOffTimeSteps];
    xnC5 = new double[numberOffTimeSteps];
    xnC6 = new double[numberOffTimeSteps];

    ynitrogen = new double[numberOffTimeSteps];
    ymethane = new double[numberOffTimeSteps];
    yethane = new double[numberOffTimeSteps];
    ypropane = new double[numberOffTimeSteps];
    yiC4 = new double[numberOffTimeSteps];
    ynC4 = new double[numberOffTimeSteps];
    yiC5 = new double[numberOffTimeSteps];
    ynC5 = new double[numberOffTimeSteps];
    ynC6 = new double[numberOffTimeSteps];

    double error = 100.0;
    double mulitplicator = 1.0;
    if (backCalculate) {
      mulitplicator = -1.0;
    }
    endVolume = totalTankVolume - mulitplicator * dailyBoilOffVolume * getEndTime() / 24.0;
    molarBoilOffRate = dailyBoilOffVolume * liquidDensity
        / getThermoSystem().getPhase(1).getMolarMass() / 24.0 * timeStep;
    if (backCalculate) {
      molarBoilOffRate = -molarBoilOffRate;
    }
    logger.info("end Volume " + endVolume);
    int iterations = 0;
    double boilOffCorrection = 0.0;
    do {
      setThermoSystem(tempThermoSystem.clone());
      thermoOperations = new ThermodynamicOperations(getThermoSystem());
      standardDensity = new Standard_ISO6578(getThermoSystem());
      getStandardISO6976().setThermoSystem(getThermoSystem());
      iterations++;
      for (int j = 0; j < numberOffTimeSteps; j++) {
        time[j] = j * timeStep;
        try {
          if (!(j == 0 && isSetInitialTemperature())) {
            thermoOperations.bubblePointTemperatureFlash();
          }
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }
        double[] xgas = new double[getThermoSystem().getPhase(0).getNumberOfComponents()];

        for (int kk = 0; kk < getThermoSystem().getPhase(0).getNumberOfComponents(); kk++) {
          xgas[kk] = getThermoSystem().getPhase(0).getComponent(kk).getx();
        }

        if (getThermoSystem().getPhase(0).hasComponent("nitrogen")) {
          xnitrogen[j] = getThermoSystem().getPhase(1).getComponent("nitrogen").getx();
          ynitrogen[j] = getThermoSystem().getPhase(0).getComponent("nitrogen").getx();
        }
        if (getThermoSystem().getPhase(0).hasComponent("methane")) {
          xmethane[j] = getThermoSystem().getPhase(1).getComponent("methane").getx();
          ymethane[j] = getThermoSystem().getPhase(0).getComponent("methane").getx();
        }
        if (getThermoSystem().getPhase(0).hasComponent("ethane")) {
          xethane[j] = getThermoSystem().getPhase(1).getComponent("ethane").getx();
          yethane[j] = getThermoSystem().getPhase(0).getComponent("ethane").getx();
        }
        if (getThermoSystem().getPhase(0).hasComponent("propane")) {
          xpropane[j] = getThermoSystem().getPhase(1).getComponent("propane").getx();
          ypropane[j] = getThermoSystem().getPhase(0).getComponent("propane").getx();
        }
        if (getThermoSystem().getPhase(0).hasComponent("i-butane")) {
          xiC4[j] = getThermoSystem().getPhase(1).getComponent("i-butane").getx();
          yiC4[j] = getThermoSystem().getPhase(0).getComponent("i-butane").getx();
        }
        if (getThermoSystem().getPhase(0).hasComponent("n-butane")) {
          xnC4[j] = getThermoSystem().getPhase(1).getComponent("n-butane").getx();
          ynC4[j] = getThermoSystem().getPhase(0).getComponent("n-butane").getx();
        }
        if (getThermoSystem().getPhase(0).hasComponent("i-pentane")) {
          xiC5[j] = getThermoSystem().getPhase(1).getComponent("i-pentane").getx();
          yiC5[j] = getThermoSystem().getPhase(0).getComponent("i-pentane").getx();
        }
        if (getThermoSystem().getPhase(0).hasComponent("n-pentane")) {
          xnC5[j] = getThermoSystem().getPhase(1).getComponent("n-pentane").getx();
          ynC5[j] = getThermoSystem().getPhase(0).getComponent("n-pentane").getx();
        }
        if (getThermoSystem().getPhase(0).hasComponent("n-hexane")) {
          xnC6[j] = getThermoSystem().getPhase(1).getComponent("n-hexane").getx();
          ynC6[j] = getThermoSystem().getPhase(0).getComponent("n-hexane").getx();
        }
        // System.out.println("time " + time[j] + " Superior Wobbe " +
        // getStandardISO6976().getValue("SuperiorWobbeIndex") + " temperature " +
        // getThermoSystem().getTemperature() + " density " + density[j] + " volume " +
        // volume[j] + " total energy " + totalEnergy[j]);
        getThermoSystem().init(0);

        standardDensity.calculate();
        density[j] = standardDensity.getValue("density");
        getStandardISO6976().calculate();
        // getStandardISO6976().display("");
        WI[j] = getStandardISO6976().getValue("SuperiorWobbeIndex");
        GCV[j] = getStandardISO6976().getValue("SuperiorCalorificValue");

        tankTemperature[j] = getThermoSystem().getTemperature();
        volume[j] = getThermoSystem().getNumberOfMoles()
            * getThermoSystem().getPhase(1).getMolarMass() / density[j]; // density[0];

        this.standardISO6976.setReferenceType("mass");
        totalEnergy[j] =
            getStandardISO6976().getValue("SuperiorCalorificValue") * volume[j] * density[j];
        GCVmass[j] = getStandardISO6976().getValue("SuperiorCalorificValue");
        this.standardISO6976.setReferenceType("volume");

        for (int i = 0; i < getThermoSystem().getPhase(0).getNumberOfComponents(); i++) {
          getThermoSystem().addComponent(getThermoSystem().getPhase(0).getComponent(i).getName(),
              -xgas[i] * molarBoilOffRate);
        }
      }
      double oldVolume = 0;
      double oldoldVolume = oldVolume;
      oldVolume = volume[0] - volume[numberOffTimeSteps - 1];
      double oldmolarBoilOffRate = 0;
      double oldoldmolarBoilOffRate = oldmolarBoilOffRate;
      oldmolarBoilOffRate = molarBoilOffRate;
      error = volume[numberOffTimeSteps - 1] / endVolume - 1.0;
      double derrordn = (oldVolume - oldoldVolume) / (oldmolarBoilOffRate - oldoldmolarBoilOffRate);
      boilOffCorrection = (volume[numberOffTimeSteps - 1] - endVolume) / derrordn;
      if (iterations > 1) {
        molarBoilOffRate += boilOffCorrection;
        // (volume[numberOffTimeSteps - 1] - endVolume) / derrordn;
      } else {
        molarBoilOffRate = molarBoilOffRate * volume[numberOffTimeSteps - 1] / endVolume;
      }
      // logger.info("error " + error + " iteration " + iterations + " molarboiloff "
      // + molarBoilOffRate + " endVolume " + endVolume + " orginalMolarBoilOff " +
      // orginalMolarBoilOff);
    } while (Math.abs(error) > 1e-8 && iterations < 100);
    try {
      thermoOperations.bubblePointTemperatureFlash();
      calcIdentifier = id;
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * Getter for the field <code>resultTable</code>.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable() {
    return resultTable;
  }

  /**
   * <p>
   * getResults.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResults(String name) {
    String[][] table = new String[numberOffTimeSteps + 1][26];
    String[] names = {"Time", "temperature", "WI", "GCV", "density", "volume", "energy"};

    for (int i = 0; i < 13; i++) {
      for (int j = 0; j < numberOffTimeSteps + 1; j++) {
        table[j][i] = "";
      }
    }

    System.arraycopy(names, 0, table[0], 0, 7);

    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);

    for (int j = 0; j < numberOffTimeSteps; j++) {
      table[j + 1][0] = nf.format(time[j]);
      table[j + 1][1] = nf.format(tankTemperature[j] - 273.15);
      table[j + 1][2] = nf.format(WI[j] / 1000.0);
      table[j + 1][3] = nf.format(GCV[j] / 1000.0);
      table[j + 1][4] = nf.format(density[j]);
      table[j + 1][5] = nf.format(volume[j]);
      table[j + 1][15] = nf.format(totalEnergy[j] / 1000.0);

      table[j + 1][6] = nf.format(xmethane[j]);
      table[j + 1][7] = nf.format(xethane[j]);
      table[j + 1][8] = nf.format(xpropane[j]);
      table[j + 1][9] = nf.format(xiC4[j]);
      table[j + 1][10] = nf.format(xnC4[j]);
      table[j + 1][11] = nf.format(xiC5[j]);
      table[j + 1][12] = nf.format(xnC5[j]);
      table[j + 1][13] = nf.format(xnC6[j]);
      table[j + 1][14] = nf.format(xnitrogen[j]);

      table[j + 1][16] = nf.format(GCVmass[j] / 1000.0);

      table[j + 1][17] = nf.format(ymethane[j]);
      table[j + 1][18] = nf.format(yethane[j]);
      table[j + 1][19] = nf.format(ypropane[j]);
      table[j + 1][20] = nf.format(yiC4[j]);
      table[j + 1][21] = nf.format(ynC4[j]);
      table[j + 1][22] = nf.format(yiC5[j]);
      table[j + 1][23] = nf.format(ynC5[j]);
      table[j + 1][24] = nf.format(ynC6[j]);
      table[j + 1][25] = nf.format(ynitrogen[j]);
    }
    setResultTable(table);

    /*
     * // for (int i = 0; i < 4; i++) { for (int j = 0; j < numberOffTimeSteps + 1; j++) {
     * logger.info(resultTable[j][25]); logger.info(resultTable[j][14]); } //// }
     */
    return table;
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
    // thermo.system.SystemInterface testSystem = new
    // thermo.system.SystemGERG2004Eos(273.15 - 161.4, 1.0);
    neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos(
        273.15 - 161.4, ThermodynamicConstantsInterface.referencePressure);
    /*
     * testSystem.addComponent("nitrogen", 0.0136); testSystem.addComponent("methane", 0.9186);
     * testSystem.addComponent("ethane", 0.0526); testSystem.addComponent("propane", 0.0115);
     */
    testSystem.addComponent("nitrogen", 0.691); // 0.93041);
    testSystem.addComponent("methane", 91.93); // 92.637);
    testSystem.addComponent("ethane", 5.651); // 4.876);
    testSystem.addComponent("propane", 1.296); // 1.093);
    testSystem.addComponent("i-butane", 0.122);
    testSystem.addComponent("n-butane", 0.289);
    testSystem.addComponent("i-pentane", 0.018);
    testSystem.addComponent("n-pentane", 0.003);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    neqsim.fluidmechanics.flowsystem.twophaseflowsystem.shipsystem.LNGship ship =
        new neqsim.fluidmechanics.flowsystem.twophaseflowsystem.shipsystem.LNGship(testSystem,
            140000, 0.0015);
    // ship.setInitialTemperature(111.0);
    // ship.useStandardVersion("","2016");
    ship.useStandardVersion("", "2016");
    ship.getStandardISO6976().setEnergyRefT(15);
    ship.getStandardISO6976().setVolRefT(15);
    ship.createSystem();
    ship.init();
    // ship.setBackCalculate(true);
    ship.solveSteadyState(0);
    ship.solveTransient(0);
    ship.getResults("test");
    ship.getThermoSystem().display();
  }

  /**
   * <p>
   * Setter for the field <code>resultTable</code>.
   * </p>
   *
   * @param resultTable the resultTable to set
   */
  public void setResultTable(String[][] resultTable) {
    this.resultTable = resultTable;
  }

  /**
   * <p>
   * Getter for the field <code>liquidDensity</code>.
   * </p>
   *
   * @return the liquidDensity
   */
  public double getLiquidDensity() {
    return liquidDensity;
  }

  /**
   * <p>
   * Setter for the field <code>liquidDensity</code>.
   * </p>
   *
   * @param liquidDensity the liquidDensity to set
   */
  public void setLiquidDensity(double liquidDensity) {
    this.liquidDensity = liquidDensity;
  }

  /**
   * <p>
   * Getter for the field <code>endTime</code>.
   * </p>
   *
   * @return the endTime
   */
  public double getEndTime() {
    return endTime;
  }

  /**
   * <p>
   * Setter for the field <code>endTime</code>.
   * </p>
   *
   * @param endTime the endTime to set
   */
  public void setEndTime(double endTime) {
    this.endTime = endTime;
  }

  /**
   * <p>
   * Getter for the field <code>standardISO6976</code>.
   * </p>
   *
   * @return the standardISO6976
   */
  public Standard_ISO6976 getStandardISO6976() {
    return standardISO6976;
  }

  /**
   * <p>
   * Setter for the field <code>standardISO6976</code>.
   * </p>
   *
   * @param standardISO6976 the standardISO6976 to set
   */
  public void setStandardISO6976(Standard_ISO6976 standardISO6976) {
    this.standardISO6976 = standardISO6976;
  }

  /**
   * <p>
   * isBackCalculate.
   * </p>
   *
   * @return the backCalculate
   */
  public boolean isBackCalculate() {
    return backCalculate;
  }

  /**
   * <p>
   * Setter for the field <code>backCalculate</code>.
   * </p>
   *
   * @param backCalculate the backCalculate to set
   */
  public void setBackCalculate(boolean backCalculate) {
    this.backCalculate = backCalculate;
  }

  /**
   * <p>
   * Getter for the field <code>thermoSystem</code>.
   * </p>
   *
   * @return the thermoSystem
   */
  public neqsim.thermo.system.SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  /**
   * <p>
   * Setter for the field <code>thermoSystem</code>.
   * </p>
   *
   * @param thermoSystem the thermoSystem to set
   */
  public void setThermoSystem(neqsim.thermo.system.SystemInterface thermoSystem) {
    this.thermoSystem = thermoSystem;
  }

  /**
   * <p>
   * isSetInitialTemperature.
   * </p>
   *
   * @return the setInitialTemperature
   */
  public boolean isSetInitialTemperature() {
    return setInitialTemperature;
  }

  /**
   * <p>
   * Getter for the field <code>initialTemperature</code>.
   * </p>
   *
   * @return the initialTemperature
   */
  public double getInitialTemperature() {
    return initialTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>initialTemperature</code>.
   * </p>
   *
   * @param initialTemperature the initialTemperature to set
   */
  public void setInitialTemperature(double initialTemperature) {
    setInitialTemperature = true;
    this.initialTemperature = initialTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>initialTemperature</code>.
   * </p>
   *
   * @param setInitialTemperature the setInitialTemperature to set
   */
  public void setInitialTemperature(boolean setInitialTemperature) {
    this.setInitialTemperature = setInitialTemperature;
  }
}
