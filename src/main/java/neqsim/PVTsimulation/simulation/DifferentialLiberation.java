package neqsim.PVTsimulation.simulation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>
 * DifferentialLiberation class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class DifferentialLiberation extends BasePVTsimulation {
  static Logger logger = LogManager.getLogger(DifferentialLiberation.class);

  double VoilStd = 0.0;
  double[] relativeVolume = null;
  double[] totalVolume = null;
  double[] liquidVolumeRelativeToVsat = null;
  double[] liquidVolume = null;
  private double[] oilDensity = null;
  private double[] gasStandardVolume = null;
  double saturationVolume = 0;

  double saturationPressure = 0;

  boolean saturationConditionFound = false;
  private double[] Bo;
  private double[] Bg;
  private double[] Rs;
  private double[] Zgas;
  private double[] relGasGravity;
  double[] gasVolume;

  /**
   * <p>
   * Constructor for DifferentialLiberation.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public DifferentialLiberation(SystemInterface tempSystem) {
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
     * logger.error(ex.getMessage()); }
     */
    saturationVolume = getThermoSystem().getVolume();
    saturationPressure = getThermoSystem().getPressure();
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
    gasVolume = new double[pressures.length];
    gasStandardVolume = new double[pressures.length];
    Bo = new double[pressures.length];
    Bg = new double[pressures.length];
    Rs = new double[pressures.length];
    Zgas = new double[pressures.length];
    relGasGravity = new double[pressures.length];
    oilDensity = new double[pressures.length];
    double totalGasStandardVolume = 0;

    getThermoSystem().setTemperature(temperature);

    for (int i = 0; i < pressures.length; i++) {
      getThermoSystem().setPressure(pressures[i]);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage());
      }
      totalVolume[i] = getThermoSystem().getVolume();
      liquidVolume[i] = getThermoSystem().getVolume();
      System.out.println("volume " + totalVolume[i]);

      if (getThermoSystem().getNumberOfPhases() > 1) {
        if (!saturationConditionFound) {
          calcSaturationConditions();
          getThermoSystem().setPressure(pressures[i]);
          try {
            thermoOps.TPflash();
          } catch (Exception ex) {
            logger.error(ex.getMessage());
          }
        }
        gasStandardVolume[i] = getThermoSystem().getPhase(0).getVolume()
            * getThermoSystem().getPhase(0).getPressure() / 1.01325
            / getThermoSystem().getPhase(0).getZ() * 288.15 / getThermoSystem().getTemperature();
        totalGasStandardVolume += getGasStandardVolume()[i];
        // if (totalVolume[i] > saturationVolume) {
        Zgas[i] = getThermoSystem().getPhase(0).getZ();
        relGasGravity[i] = getThermoSystem().getPhase(0).getMolarMass() / 0.028;
        getThermoSystem().initPhysicalProperties();
        if (getThermoSystem().hasPhaseType("gas") && getThermoSystem().hasPhaseType("oil")) {
          liquidVolume[i] = getThermoSystem().getPhase(1).getVolume();
          oilDensity[i] = getThermoSystem().getPhase(1).getPhysicalProperties().getDensity();
        } else if (getThermoSystem().hasPhaseType("oil")) {
          liquidVolume[i] = getThermoSystem().getPhase(0).getVolume();
          oilDensity[i] = getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
        } else {
          liquidVolume[i] = getThermoSystem().getPhase(0).getVolume();
          oilDensity[i] = getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
        }

        if (getThermoSystem().getNumberOfPhases() > 1) {
          gasVolume[i] = getThermoSystem().getPhase(0).getVolume();
        } else {
          gasVolume[i] = 0.0;
        }

        liquidVolumeRelativeToVsat[i] = liquidVolume[i] / saturationVolume;
        double volumeCorrection =
            getThermoSystem().getVolume() - getThermoSystem().getPhase(1).getVolume();
        double test = volumeCorrection / getThermoSystem().getPhase(0).getMolarVolume();

        for (int j = 0; j < getThermoSystem().getPhase(0).getNumberOfComponents(); j++) {
          getThermoSystem().addComponent(j,
              -test * getThermoSystem().getPhase(0).getComponent(j).getx());
        }
      }
    }
    getThermoSystem().setPressure(1.01325);
    getThermoSystem().setTemperature(288.15);
    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    VoilStd = getThermoSystem().getPhase(1).getVolume();
    totalGasStandardVolume += getThermoSystem().getPhase(0).getVolume();
    // getThermoSystem().display();
    double total = 0;
    for (int i = 0; i < pressures.length; i++) {
      relativeVolume[i] = totalVolume[i] / saturationVolume;
      Bo[i] = liquidVolume[i] / VoilStd;
      total += getGasStandardVolume()[i];
      if (Zgas[i] > 1e-10) {
        Bg[i] = gasVolume[i] / getGasStandardVolume()[i];
        Rs[i] = (totalGasStandardVolume - total) / VoilStd;
      }
      System.out.println("Bo " + getBo()[i] + " Bg " + getBg()[i] + " Rs " + getRs()[i]
          + " oil density " + getOilDensity()[i] + "  gas gracvity " + getRelGasGravity()[i]
          + " Zgas " + getZgas()[i] + " gasstdvol " + getGasStandardVolume()[i]);
    }
    System.out.println("test finished");
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 83.5, 450.0);
    tempSystem.addComponent("nitrogen", 0.586);
    tempSystem.addComponent("CO2", 0.087);
    tempSystem.addComponent("methane", 17.0209);
    tempSystem.addComponent("ethane", 5.176);
    tempSystem.addComponent("propane", 6.652);
    tempSystem.addComponent("i-butane", 1.533);
    tempSystem.addComponent("n-butane", 3.544);
    tempSystem.addComponent("i-pentane", 1.585);
    tempSystem.addComponent("n-pentane", 2.036);
    tempSystem.addTBPfraction("C6", 2.879, 84.9 / 1000.0, 0.6668);
    tempSystem.addTBPfraction("C7", 4.435, 93.2 / 1000.0, 0.7243);
    tempSystem.addTBPfraction("C8", 4.815, 105.7 / 1000.0, 0.7527);
    tempSystem.addTBPfraction("C9", 3.488, 119.8 / 1000.0, 0.7743);
    tempSystem.addPlusFraction("C10", 45.944, 320.0 / 1000.0, 0.924);
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.getCharacterization().characterisePlusFraction();

    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);

    DifferentialLiberation CVDsim = new DifferentialLiberation(tempSystem);
    CVDsim.runCalc();
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
   * getBo.
   * </p>
   *
   * @return the Bo
   */
  public double[] getBo() {
    return Bo;
  }

  /**
   * <p>
   * getBg.
   * </p>
   *
   * @return the Bg
   */
  public double[] getBg() {
    return Bg;
  }

  /**
   * <p>
   * getRs.
   * </p>
   *
   * @return the Rs
   */
  public double[] getRs() {
    return Rs;
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
   * Getter for the field <code>relGasGravity</code>.
   * </p>
   *
   * @return the relGasGravity
   */
  public double[] getRelGasGravity() {
    return relGasGravity;
  }

  /**
   * <p>
   * Getter for the field <code>gasStandardVolume</code>.
   * </p>
   *
   * @return the gasStandardVolume
   */
  public double[] getGasStandardVolume() {
    return gasStandardVolume;
  }

  /**
   * <p>
   * Getter for the field <code>oilDensity</code>.
   * </p>
   *
   * @return the oilDensity
   */
  public double[] getOilDensity() {
    return oilDensity;
  }
}
