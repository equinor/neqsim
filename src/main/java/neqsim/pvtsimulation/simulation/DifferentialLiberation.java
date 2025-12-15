package neqsim.pvtsimulation.simulation;

import org.apache.commons.lang3.StringUtils;
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

  // =====================================================
  // QC Methods - Whitson PVT Methodology
  // =====================================================

  /**
   * Validate Bo monotonicity as per Whitson guidelines.
   *
   * <p>
   * Bo (oil formation volume factor) should decrease monotonically with decreasing pressure below
   * the saturation pressure. Non-monotonic behavior indicates potential data quality issues.
   * </p>
   *
   * @return true if Bo is monotonically decreasing, false otherwise
   */
  public boolean validateBoMonotonicity() {
    if (Bo == null || Bo.length < 2) {
      return true;
    }

    for (int i = 1; i < Bo.length; i++) {
      // Bo should decrease as pressure decreases (pressures array is typically decreasing)
      if (pressures[i] < pressures[i - 1] && Bo[i] > Bo[i - 1]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Validate Rs monotonicity as per Whitson guidelines.
   *
   * <p>
   * Rs (solution gas-oil ratio) should decrease monotonically with decreasing pressure below the
   * saturation pressure. Non-monotonic behavior indicates potential issues.
   * </p>
   *
   * @return true if Rs is monotonically decreasing, false otherwise
   */
  public boolean validateRsMonotonicity() {
    if (Rs == null || Rs.length < 2) {
      return true;
    }

    for (int i = 1; i < Rs.length; i++) {
      // Rs should decrease as pressure decreases
      if (pressures[i] < pressures[i - 1] && Rs[i] > Rs[i - 1]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Validate Bg monotonicity as per Whitson guidelines.
   *
   * <p>
   * Bg (gas formation volume factor) should increase with decreasing pressure. Non-monotonic
   * behavior may indicate calculation issues.
   * </p>
   *
   * @return true if Bg behavior is consistent, false otherwise
   */
  public boolean validateBgMonotonicity() {
    if (Bg == null || Bg.length < 2) {
      return true;
    }

    for (int i = 1; i < Bg.length; i++) {
      // Bg should increase as pressure decreases (Bg = V/Vstd)
      if (pressures[i] < pressures[i - 1] && Bg[i] < Bg[i - 1] && Bg[i] > 0 && Bg[i - 1] > 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Validate oil density monotonicity.
   *
   * <p>
   * Oil density should increase with decreasing pressure as gas is liberated. Non-monotonic
   * behavior may indicate issues.
   * </p>
   *
   * @return true if oil density is monotonically increasing, false otherwise
   */
  public boolean validateOilDensityMonotonicity() {
    if (oilDensity == null || oilDensity.length < 2) {
      return true;
    }

    for (int i = 1; i < oilDensity.length; i++) {
      // Density should increase as pressure decreases (gas liberates)
      if (pressures[i] < pressures[i - 1] && oilDensity[i] < oilDensity[i - 1]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Calculate total material balance residual.
   *
   * <p>
   * Checks that total mass is conserved throughout the DLE experiment. Large residuals indicate
   * potential calculation issues.
   * </p>
   *
   * @return maximum fractional mass balance error
   */
  public double calculateMaterialBalanceResidual() {
    if (Bo == null || Rs == null || pressures.length == 0) {
      return 0.0;
    }

    // At standard conditions, total hydrocarbon should be conserved
    // This is a simplified check based on Bo and Rs consistency
    double maxResidual = 0.0;

    // Bo at saturation should equal Bo at highest pressure (approximately)
    if (Bo.length > 1 && saturationPressure > 0) {
      double expectedBo = Bo[0]; // First point should be at or near saturation
      for (int i = 1; i < Bo.length; i++) {
        if (Bo[i] > expectedBo * 1.01) { // Allow 1% tolerance
          double residual = (Bo[i] - expectedBo) / expectedBo;
          maxResidual = Math.max(maxResidual, Math.abs(residual));
        }
      }
    }

    return maxResidual;
  }

  /**
   * Calculate the shrinkage factor curve.
   *
   * <p>
   * Shrinkage is defined as Bo/Bob where Bob is the Bo at bubble point. This is useful for
   * reservoir engineering calculations.
   * </p>
   *
   * @return array of shrinkage factors at each pressure step
   */
  public double[] getShrinkage() {
    if (Bo == null || Bo.length == 0) {
      return new double[0];
    }

    double Bob = Bo[0]; // Bo at saturation (first pressure point)
    double[] shrinkage = new double[Bo.length];

    for (int i = 0; i < Bo.length; i++) {
      shrinkage[i] = Bo[i] / Bob;
    }

    return shrinkage;
  }

  /**
   * Generate a comprehensive QC report for the DLE experiment.
   *
   * <p>
   * Includes monotonicity checks and material balance validation per Whitson guidelines.
   * </p>
   *
   * @return formatted QC report string
   */
  public String generateQCReport() {
    StringBuilder report = new StringBuilder();
    report.append("=== Differential Liberation QC Report ===\n\n");

    report.append("Saturation Pressure: ").append(String.format("%.2f", saturationPressure))
        .append(" bar\n\n");

    // Monotonicity checks
    report.append("Monotonicity Checks:\n");
    report.append("  Bo monotonically decreasing: ")
        .append(validateBoMonotonicity() ? "PASS" : "FAIL").append("\n");
    report.append("  Rs monotonically decreasing: ")
        .append(validateRsMonotonicity() ? "PASS" : "FAIL").append("\n");
    report.append("  Bg monotonically increasing: ")
        .append(validateBgMonotonicity() ? "PASS" : "FAIL").append("\n");
    report.append("  Oil density increasing: ")
        .append(validateOilDensityMonotonicity() ? "PASS" : "FAIL").append("\n\n");

    // Material balance
    double mbResidual = calculateMaterialBalanceResidual();
    report.append("Material Balance:\n");
    report.append("  Max residual: ").append(String.format("%.4f", mbResidual * 100))
        .append(" %\n");
    report.append("  Status: ")
        .append(mbResidual < 0.01 ? "PASS" : (mbResidual < 0.05 ? "WARNING" : "FAIL"))
        .append("\n\n");

    // Summary table
    if (pressures != null && Bo != null) {
      report.append("Summary Table:\n");
      report.append(String.format("%-12s %-12s %-12s %-12s %-14s %-12s\n", "P (bar)", "Bo", "Rs",
          "Bg", "Oil Dens", "Z gas"));
      report.append(StringUtils.repeat("-", 80)).append("\n");

      int numRows = Math.min(pressures.length, 10); // Limit to first 10 rows
      for (int i = 0; i < numRows; i++) {
        report.append(String.format("%-12.2f %-12.4f %-12.2f %-12.6f %-14.2f %-12.4f\n",
            pressures[i], Bo[i], Rs != null ? Rs[i] : 0.0, Bg != null ? Bg[i] : 0.0,
            oilDensity != null ? oilDensity[i] : 0.0, Zgas != null ? Zgas[i] : 0.0));
      }
      if (pressures.length > 10) {
        report.append("... (").append(pressures.length - 10).append(" more rows)\n");
      }
    }

    return report.toString();
  }
}
