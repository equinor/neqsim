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
 * DifferentialLiberation class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class DifferentialLiberation extends BasePVTsimulation {
  /** Logger object for class. */
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
     * logger.error(ex.getMessage(), ex); }
     */
    getThermoSystem().initPhysicalProperties();
    saturationVolume = getThermoSystem().getPhase(0).getMass()
        / getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
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
    double[] mass = new double[pressures.length];
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

    if (!Double.isNaN(temperature)) {
      getThermoSystem().setTemperature(temperature, temperatureUnit);
    }

    for (int i = 0; i < pressures.length; i++) {
      getThermoSystem().setPressure(pressures[i]);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      getThermoSystem().initPhysicalProperties();
      oilDensity[i] = getThermoSystem().getDensity("kg/m3");
      mass[i] = getThermoSystem().getMass("kg");

      totalVolume[i] = mass[i] / oilDensity[i];
      liquidVolume[i] = totalVolume[i];
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
        gasStandardVolume[i] = getThermoSystem().getPhase(PhaseType.GAS).getMass()
            / getThermoSystem().getPhase(PhaseType.GAS).getPhysicalProperties().getDensity()
            * getThermoSystem().getPhase(0).getPressure()
            / ThermodynamicConstantsInterface.referencePressure
            / getThermoSystem().getPhase(0).getZ() * 288.15 / getThermoSystem().getTemperature();
        totalGasStandardVolume += getGasStandardVolume()[i];
        // if (totalVolume[i] > saturationVolume) {
        Zgas[i] = getThermoSystem().getPhase(0).getZ();
        relGasGravity[i] = getThermoSystem().getPhase(0).getMolarMass() / 0.028;
        if (getThermoSystem().hasPhaseType(PhaseType.GAS)
            && getThermoSystem().hasPhaseType(PhaseType.OIL)) {
          oilDensity[i] = getThermoSystem().getPhase(1).getPhysicalProperties().getDensity();
          liquidVolume[i] = getThermoSystem().getPhase(1).getMass() / oilDensity[i];
          getThermoSystem().getPhase(1).getMass();
        } else if (getThermoSystem().hasPhaseType("oil")) {
          oilDensity[i] = getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
          liquidVolume[i] = getThermoSystem().getPhase(0).getMass() / oilDensity[i];
        } else {
          oilDensity[i] = getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
          liquidVolume[i] = getThermoSystem().getPhase(0).getMass() / oilDensity[i];
        }

        if (getThermoSystem().getNumberOfPhases() > 1) {
          gasVolume[i] = getThermoSystem().getPhase(PhaseType.GAS).getMass()
              / getThermoSystem().getPhase(PhaseType.GAS).getPhysicalProperties().getDensity();
        }

        liquidVolumeRelativeToVsat[i] = liquidVolume[i] / saturationVolume;
        getThermoSystem().removePhase(0);
      }
    }
    getThermoSystem().setPressure(ThermodynamicConstantsInterface.referencePressure);
    getThermoSystem().setTemperature(288.15);
    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    getThermoSystem().initPhysicalProperties();
    VoilStd = getThermoSystem().getPhase(PhaseType.OIL).getMass()
        / getThermoSystem().getPhase(PhaseType.OIL).getPhysicalProperties().getDensity();
    if (getThermoSystem().hasPhaseType(PhaseType.GAS)) {
      totalGasStandardVolume += getThermoSystem().getPhase(PhaseType.GAS).getCorrectedVolume();
    }

    double total = 0;
    for (int i = 0; i < pressures.length; i++) {
      relativeVolume[i] = totalVolume[i] / saturationVolume;
      Bo[i] = liquidVolume[i] / VoilStd;
      total += getGasStandardVolume()[i];
      Rs[i] = (totalGasStandardVolume - total) / VoilStd;
      if (Zgas[i] > 1e-10) {
        Bg[i] = gasVolume[i] / getGasStandardVolume()[i];
      }
      /*
       * System.out.println("pressure " + pressures[i] + " Bo " + getBo()[i] + " Bg " + getBg()[i] +
       * " Rs " + getRs()[i] + " oil density " + getOilDensity()[i] + "  gas gracvity " +
       * getRelGasGravity()[i] + " Zgas " + getZgas()[i] + " gasstdvol " +
       * getGasStandardVolume()[i]);
       */
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
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 83.5, 450.0);
    tempSystem.addComponent("nitrogen", 0.586);
    tempSystem.addComponent("CO2", 0.087);
    tempSystem.addComponent("methane", 107.0209);
    tempSystem.addComponent("ethane", 15.176);
    tempSystem.addComponent("propane", 6.652);
    tempSystem.addComponent("i-butane", 3.533);
    tempSystem.addComponent("n-butane", 5.544);
    tempSystem.addComponent("i-pentane", 1.585);
    tempSystem.addComponent("n-pentane", 2.036);
    tempSystem.addTBPfraction("C6", 2.879, 84.9 / 1000.0, 0.6668);
    tempSystem.addTBPfraction("C7", 4.435, 93.2 / 1000.0, 0.7243);
    tempSystem.addTBPfraction("C8", 4.815, 105.7 / 1000.0, 0.7527);
    tempSystem.addTBPfraction("C9", 3.488, 119.8 / 1000.0, 0.7743);
    tempSystem.addPlusFraction("C10", 45.944, 320.0 / 1000.0, 0.924);
    tempSystem.getCharacterization().characterisePlusFraction();

    DifferentialLiberation differentialLiberation = new DifferentialLiberation(tempSystem);
    differentialLiberation.setPressures(
        new double[] {350.0, 250.0, 200.0, 150.0, 100.0, 70.0, 50.0, 40.0, 30.0, 20.0, 1.0});
    differentialLiberation.setTemperature(83.5, "C");
    differentialLiberation.runCalc();
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
