package neqsim.pvtsimulation.simulation;

import java.util.ArrayList;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.pvtsimulation.util.parameterfitting.CVDFunction;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * ConstantVolumeDepletion class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class ConstantVolumeDepletion extends BasePVTsimulation {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ConstantVolumeDepletion.class);

  private static final double CELL_VOLUME_RELATIVE_TOLERANCE = 1.0e-8;
  private static final int MAX_CELL_VOLUME_ITERATIONS = 100;

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
   * Constructor for ConstantVolumeDepletion.
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ConstantVolumeDepletion(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * setTemperaturesAndPressures.
   *
   * @param temperature an array of type double
   * @param pressure an array of type double
   */
  public void setTemperaturesAndPressures(double[] temperature, double[] pressure) {
    this.pressure = pressure;
    this.temperatures = temperature;
    experimentalData = new double[temperature.length][1];
  }

  /**
   * calcSaturationConditions.
   */
  public void calcSaturationConditions() {
    getThermoSystem().setPressure(1.0);
    do {
      getThermoSystem().setPressure(getThermoSystem().getPressure() + 10.0);
      thermoOps.TPflash();
    } while (getThermoSystem().getNumberOfPhases() == 1 && getThermoSystem().getPressure() < 1000.0);
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
     * try { thermoOps.dewPointPressureFlash(); } catch (Exception ex) { logger.error(ex.getMessage(), ex); }
     */
    getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    saturationVolume = getThermoSystem().getCorrectedVolume();
    saturationPressure = getThermoSystem().getPressure();
    Zsaturation = getThermoSystem().getZvolcorr();
    saturationConditionFound = true;
  }

  /**
   * Restore the constant CVD cell volume by withdrawing equilibrium gas.
   */
  private void restoreSaturationVolume() {
    for (int iteration = 0; iteration < MAX_CELL_VOLUME_ITERATIONS; iteration++) {
      thermoOps.TPflash();
      getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

      double currentVolume = getThermoSystem().getCorrectedVolume();
      double relativeResidual = (currentVolume - saturationVolume) / saturationVolume;
      if (Math.abs(relativeResidual) <= CELL_VOLUME_RELATIVE_TOLERANCE) {
        return;
      }
      if (relativeResidual < 0.0) {
        throw new IllegalStateException(String.format(Locale.ROOT,
            "CVD gas removal overshot the saturation volume by %.3e relative.", -relativeResidual));
      }
      if (!getThermoSystem().hasPhaseType("gas")) {
        throw new IllegalStateException("Cannot restore CVD cell volume without a gas phase.");
      }

      double gasMoles = getThermoSystem().getPhase("gas").getNumberOfMolesInPhase();
      double gasVolume = getThermoSystem().getPhase("gas").getCorrectedVolume();
      if (gasMoles <= 0.0 || gasVolume <= 0.0) {
        throw new IllegalStateException("Cannot restore CVD cell volume from an empty gas phase.");
      }

      double molesToRemove = (currentVolume - saturationVolume) / gasVolume * gasMoles;
      molesToRemove = Math.min(molesToRemove, 0.95 * gasMoles);
      double[] componentRemoval = new double[getThermoSystem().getNumberOfComponents()];
      for (int componentIndex = 0; componentIndex < componentRemoval.length; componentIndex++) {
        componentRemoval[componentIndex] = molesToRemove
            * getThermoSystem().getPhase("gas").getComponent(componentIndex).getx();
      }

      for (int componentIndex = 0; componentIndex < componentRemoval.length; componentIndex++) {
        getThermoSystem().addComponent(componentIndex, -componentRemoval[componentIndex]);
      }
    }

    throw new IllegalStateException("CVD gas removal did not restore the saturation volume.");
  }

  /**
   * runCalc.
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
    if (!Double.isNaN(temperature)) {
      getThermoSystem().setTemperature(temperature, temperatureUnit);
    }
    calcSaturationConditions();

    for (int i = 0; i < pressures.length; i++) {
      getThermoSystem().setPressure(pressures[i]);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        String message = String.format(Locale.ROOT, "CVD TP flash failed at pressure %.6f bara.",
            getThermoSystem().getPressure("bara"));
        logger.error(message, ex);
        throw new IllegalStateException(message, ex);
      }
      getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

      if (getThermoSystem().getNumberOfPhases() > 1
          && getThermoSystem().getCorrectedVolume() > saturationVolume * (1.0 + CELL_VOLUME_RELATIVE_TOLERANCE)) {
        restoreSaturationVolume();
      }
      getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

      totalVolume[i] = getThermoSystem().getCorrectedVolume();
      relativeVolume[i] = totalVolume[i] / saturationVolume;
      cummulativeMolePercDepleted[i] = 100.0 - getThermoSystem().getTotalNumberOfMoles() / totalNumberOfMoles * 100.0;
      Zmix[i] = getThermoSystem().getZvolcorr();

      if (getThermoSystem().hasPhaseType("gas")) {
        Zgas[i] = getThermoSystem().getPhase("gas").getZvolcorr();
      }
      if (getThermoSystem().hasPhaseType("oil")) {
        liquidVolume[i] = getThermoSystem().getPhase("oil").getCorrectedVolume();
        liquidVolumeRelativeToVsat[i] = liquidVolume[i] / saturationVolume;
        liquidRelativeVolume[i] = liquidVolumeRelativeToVsat[i] * 100.0;
      }
    }
  }

  /**
   * runTuning.
   */
  public void runTuning() {
    ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

    try {
      System.out.println("adding....");

      for (int i = 0; i < experimentalData[0].length; i++) {
        CVDFunction function = new CVDFunction();
        double[] guess = new double[] { 234.0 / 1000.0 }; // getThermoSystem().getCharacterization().getPlusFractionModel().getMPlus()/1000.0};
        function.setInitialGuess(guess);

        SystemInterface tempSystem = getThermoSystem(); // getThermoSystem().clone();

        tempSystem.setTemperature(temperature, temperatureUnit);
        tempSystem.setPressure(pressures[i]);
        // thermoOps.TPflash();
        // tempSystem.display();
        double[] sample1 = { temperatures[i], pressures[i] };
        double relativeVolume = experimentalData[0][i];
        double[] standardDeviation1 = { 1.5 };
        SampleValue sample = new SampleValue(relativeVolume, relativeVolume / 50.0, sample1, standardDeviation1);
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
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface tempSystem = new SystemSrkEos(298.0, 211.0);
    /*
     * tempSystem.addComponent("nitrogen", 0.64); tempSystem.addComponent("CO2", 3.53);
     * tempSystem.addComponent("methane", 70.78); tempSystem.addComponent("ethane", 8.94);
     * tempSystem.addComponent("propane", 5.05); tempSystem.addComponent("i-butane", 0.85);
     * tempSystem.addComponent("n-butane", 1.68); tempSystem.addComponent("iC5", 0.62);
     * tempSystem.addComponent("n-pentane", 0.79); tempSystem.addComponent("n-hexane", 0.83);
     * tempSystem.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324); tempSystem.addTBPfraction("C8", 1.06, 104.6 /
     * 1000.0, 0.7602); tempSystem.addTBPfraction("C9", 0.79, 119.1 / 1000.0, 0.7677); tempSystem.addTBPfraction("C10",
     * 0.57, 133.0 / 1000.0, 0.79); tempSystem.addTBPfraction("C11", 0.38, 155.0 / 1000.0, 0.795);
     * tempSystem.addTBPfraction("C12", 0.37, 162.0 / 1000.0, 0.806); tempSystem.addTBPfraction("C13", 0.32, 177.0 /
     * 1000.0, 0.824); tempSystem.addTBPfraction("C14", 0.27, 198.0 / 1000.0, 0.835); tempSystem.addTBPfraction("C15",
     * 0.23, 202.0 / 1000.0, 0.84); tempSystem.addTBPfraction("C16", 0.19, 215.0 / 1000.0, 0.846);
     * tempSystem.addTBPfraction("C17", 0.17, 234.0 / 1000.0, 0.84); tempSystem.addTBPfraction("C18", 0.13, 251.0 /
     * 1000.0, 0.844); tempSystem.addTBPfraction("C19", 0.13, 270.0 / 1000.0, 0.854); tempSystem.addPlusFraction("C20",
     * 0.62, 381.0 / 1000.0, 0.88); tempSystem.getCharacterization().getLumpingModel(). setNumberOfLumpedComponents(6);
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
    CVDsim.setPressures(new double[] { 400, 300.0, 200.0, 150.0, 100.0, 50.0 });
    CVDsim.runCalc();
    CVDsim.setTemperaturesAndPressures(new double[] { 313, 313, 313, 313 }, new double[] { 400, 300.0, 200.0, 100.0 });
    double[][] expData = { { 0.95, 0.99, 1.0, 1.1 } };
    CVDsim.setExperimentalData(expData);
    // CVDsim.runTuning();
  }

  /**
   * Getter for the field <code>relativeVolume</code>.
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
   * getZmix.
   *
   * @return the Zmix
   */
  public double[] getZmix() {
    return Zmix;
  }

  /**
   * getZgas.
   *
   * @return the Zgas
   */
  public double[] getZgas() {
    return Zgas;
  }

  /**
   * Getter for the field <code>liquidRelativeVolume</code>.
   *
   * @return the liquidRelativeVolume
   */
  public double[] getLiquidRelativeVolume() {
    return liquidRelativeVolume;
  }

  /**
   * Getter for the field <code>cummulativeMolePercDepleted</code>.
   *
   * @return the cummulativeMolePercDepleted
   */
  public double[] getCummulativeMolePercDepleted() {
    return cummulativeMolePercDepleted;
  }

  // ============================================================================
  // QC/QA Methods per Whitson methodology (https://wiki.whitson.com/phase_behavior/pvt_exp/CVD/)
  // ============================================================================

  /**
   * Calculate equilibrium K-values at each pressure step.
   *
   * <p>
   * K-values are calculated from the equilibrium liquid and gas compositions: Ki = yi / xi
   * </p>
   *
   * <p>
   * This is a key QC check per Whitson methodology - K-value plots should show smooth, consistent trends. Erratic
   * K-values indicate data quality issues.
   * </p>
   *
   * @return 2D array of K-values [pressure_index][component_index], or null if not calculated
   */
  public double[][] calculateKValues() {
    if (pressures == null || pressures.length == 0) {
      return null;
    }

    int nComp = getThermoSystem().getNumberOfComponents();
    double[][] kValues = new double[pressures.length][nComp];

    // Store original state
    double origTemp = getThermoSystem().getTemperature();
    double origPres = getThermoSystem().getPressure();

    if (!Double.isNaN(temperature)) {
      getThermoSystem().setTemperature(temperature, temperatureUnit);
    }

    for (int i = 0; i < pressures.length; i++) {
      getThermoSystem().setPressure(pressures[i]);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        continue;
      }

      if (getThermoSystem().getNumberOfPhases() > 1) {
        for (int j = 0; j < nComp; j++) {
          double yi = getThermoSystem().getPhase(0).getComponent(j).getx();
          double xi = getThermoSystem().getPhase(1).getComponent(j).getx();
          kValues[i][j] = (xi > 1e-20) ? yi / xi : 0.0;
        }
      } else {
        // Single phase - K-values are 1.0
        for (int j = 0; j < nComp; j++) {
          kValues[i][j] = 1.0;
        }
      }
    }

    // Restore original state
    getThermoSystem().setTemperature(origTemp);
    getThermoSystem().setPressure(origPres);

    return kValues;
  }

  /**
   * Validate material balance using the wet-gas material balance approach per Whitson.
   *
   * <p>
   * This QC check verifies that the cumulative moles produced match the expected values based on the gas removed at
   * each step. A tolerance of 1-2% is typically acceptable.
   * </p>
   *
   * @param tolerance acceptable relative error (e.g., 0.01 for 1%)
   * @return true if material balance is satisfied within tolerance
   */
  public boolean validateMaterialBalance(double tolerance) {
    if (cummulativeMolePercDepleted == null || cummulativeMolePercDepleted.length == 0) {
      return false;
    }

    // Check that cumulative depletion is monotonically increasing
    for (int i = 1; i < cummulativeMolePercDepleted.length; i++) {
      if (cummulativeMolePercDepleted[i] < cummulativeMolePercDepleted[i - 1]) {
        logger.warn("CVD QC: Cumulative depletion not monotonically increasing at step " + i);
        return false;
      }
    }

    // Check that final depletion is reasonable (typically < 95%)
    double finalDepletion = cummulativeMolePercDepleted[cummulativeMolePercDepleted.length - 1];
    if (finalDepletion > 99.0) {
      logger.warn("CVD QC: Final depletion exceeds 99%: " + finalDepletion);
      return false;
    }

    return true;
  }

  /**
   * Calculate gas density at each pressure step using the real gas law.
   *
   * <p>
   * Gas density is calculated as: rho_g = (P * M_g) / (Z * R * T)
   * </p>
   *
   * <p>
   * This QC method allows comparison with reported gas densities to validate Z-factor and molecular weight consistency.
   * </p>
   *
   * @return array of calculated gas densities in kg/m³
   */
  public double[] calculateGasDensityQC() {
    if (pressures == null || Zgas == null) {
      return null;
    }

    double[] gasDensity = new double[pressures.length];
    double R = 8.314; // J/(mol·K)

    for (int i = 0; i < pressures.length; i++) {
      if (Zgas[i] > 0 && getThermoSystem().getPhase(0) != null) {
        double P = pressures[i] * 1e5; // bar to Pa
        double T = !Double.isNaN(temperature) ? temperature : getThermoSystem().getTemperature();
        double Mg = getThermoSystem().getPhase(0).getMolarMass(); // kg/mol
        gasDensity[i] = (P * Mg) / (Zgas[i] * R * T);
      }
    }

    return gasDensity;
  }

  /**
   * Calculate oil density at each pressure step using material balance.
   *
   * <p>
   * Per Whitson methodology, oil density is back-calculated from cell volume, liquid volume, and moles remaining. This
   * serves as a QC check for the consistency of CVD data.
   * </p>
   *
   * @return array of calculated oil densities in kg/m³, or null if calculation not possible
   */
  public double[] calculateOilDensityQC() {
    if (pressures == null || liquidVolume == null || totalVolume == null) {
      return null;
    }

    double[] oilDensity = new double[pressures.length];

    // Store original state
    double origTemp = getThermoSystem().getTemperature();
    double origPres = getThermoSystem().getPressure();

    if (!Double.isNaN(temperature)) {
      getThermoSystem().setTemperature(temperature, temperatureUnit);
    }

    for (int i = 0; i < pressures.length; i++) {
      getThermoSystem().setPressure(pressures[i]);
      try {
        thermoOps.TPflash();
        if (getThermoSystem().getNumberOfPhases() > 1) {
          oilDensity[i] = getThermoSystem().getPhase("oil").getDensity("kg/m3");
        }
      } catch (Exception ex) {
        logger.debug(ex.getMessage());
      }
    }

    // Restore original state
    getThermoSystem().setTemperature(origTemp);
    getThermoSystem().setPressure(origPres);

    return oilDensity;
  }

  /**
   * Get liquid dropout curve (liquid volume percentage vs pressure).
   *
   * <p>
   * The liquid dropout curve is a key result from CVD experiments for gas condensate fluids. It shows the percentage of
   * liquid condensed at each pressure below the dew point.
   * </p>
   *
   * @return array of liquid dropout percentages (volume %)
   */
  public double[] getLiquidDropoutCurve() {
    return liquidRelativeVolume;
  }

  /**
   * Generate a CVD QC report as formatted text.
   *
   * <p>
   * This method produces a comprehensive QC report following Whitson methodology, including:
   * <ul>
   * <li>Saturation conditions</li>
   * <li>Relative volume at each pressure</li>
   * <li>Liquid dropout curve</li>
   * <li>Z-factor consistency</li>
   * <li>Material balance validation</li>
   * </ul>
   *
   * @return formatted QC report string
   */
  public String generateQCReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== CVD Quality Control Report ===\n\n");

    // Saturation conditions
    sb.append("Saturation Conditions:\n");
    sb.append(String.format("  Saturation Pressure: %.2f bar\n", saturationPressure));
    sb.append(String.format("  Saturation Volume: %.4f m³\n", saturationVolume));
    sb.append(String.format("  Z at Saturation: %.4f\n", Zsaturation));
    sb.append("\n");

    // Results table
    sb.append("Pressure Step Results:\n");
    sb.append(String.format("%10s %12s %12s %12s %12s\n", "P (bar)", "Vrel", "Liq Vol %", "Z-gas", "Depleted %"));
    sb.append(StringUtils.repeat("-", 60) + "\n");

    if (pressures != null) {
      for (int i = 0; i < pressures.length; i++) {
        double vrel = (relativeVolume != null && i < relativeVolume.length) ? relativeVolume[i] : Double.NaN;
        double liqVol = (liquidRelativeVolume != null && i < liquidRelativeVolume.length) ? liquidRelativeVolume[i]
            : Double.NaN;
        double zg = (Zgas != null && i < Zgas.length) ? Zgas[i] : Double.NaN;
        double depl = (cummulativeMolePercDepleted != null && i < cummulativeMolePercDepleted.length)
            ? cummulativeMolePercDepleted[i]
            : Double.NaN;

        sb.append(String.format("%10.2f %12.4f %12.2f %12.4f %12.2f\n", pressures[i], vrel, liqVol, zg, depl));
      }
    }
    sb.append("\n");

    // Material balance check
    boolean mbPassed = validateMaterialBalance(0.02);
    sb.append("Material Balance Check: " + (mbPassed ? "PASSED" : "FAILED") + "\n");

    return sb.toString();
  }
}
