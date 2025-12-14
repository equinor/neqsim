package neqsim.pvtsimulation.regression;

import java.util.EnumMap;
import java.util.List;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;
import neqsim.thermo.system.SystemInterface;

/**
 * Objective function for PVT regression using Levenberg-Marquardt optimization.
 *
 * <p>
 * This function computes the calculated value for a given experimental data point using the current
 * parameter values. The Levenberg-Marquardt algorithm minimizes the sum of squared residuals
 * between calculated and experimental values.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PVTRegressionFunction extends LevenbergMarquardtFunction {
  private SystemInterface baseFluid;
  private List<RegressionParameterConfig> parameterConfigs;
  private EnumMap<ExperimentType, Double> experimentWeights;

  /**
   * Create a PVT regression function.
   *
   * @param baseFluid the base fluid to tune
   * @param parameterConfigs list of parameter configurations
   * @param experimentWeights weights for each experiment type
   */
  public PVTRegressionFunction(SystemInterface baseFluid,
      List<RegressionParameterConfig> parameterConfigs,
      EnumMap<ExperimentType, Double> experimentWeights) {
    this.baseFluid = baseFluid;
    this.parameterConfigs = parameterConfigs;
    this.experimentWeights = experimentWeights;
    this.params = new double[parameterConfigs.size()];
  }

  /**
   * Set parameter bounds.
   *
   * @param bounds 2D array [nParams][2] with [lower, upper] bounds
   */
  public void setBounds(double[][] bounds) {
    this.bounds = bounds;
  }

  /** {@inheritDoc} */
  @Override
  public PVTRegressionFunction clone() {
    PVTRegressionFunction cloned = new PVTRegressionFunction(baseFluid.clone(), parameterConfigs,
        new EnumMap<>(experimentWeights));
    if (this.params != null) {
      cloned.params = new double[this.params.length];
      System.arraycopy(this.params, 0, cloned.params, 0, this.params.length);
    }
    if (this.bounds != null) {
      cloned.bounds = new double[this.bounds.length][2];
      for (int i = 0; i < this.bounds.length; i++) {
        cloned.bounds[i][0] = this.bounds[i][0];
        cloned.bounds[i][1] = this.bounds[i][1];
      }
    }
    return cloned;
  }

  /**
   * Calculate the value for a given experimental data point.
   *
   * The dependentValues array contains:
   * <ul>
   * <li>[0] = pressure (bar)</li>
   * <li>[1] = temperature (K)</li>
   * <li>[2] = experiment type ordinal</li>
   * <li>[3] = property index (0=primary, 1=secondary, etc.)</li>
   * <li>[4] = additional info (e.g., reservoir temperature for separator tests)</li>
   * </ul>
   *
   * @param dependentValues array of dependent values for this data point
   * @return calculated value
   */
  @Override
  public double calcValue(double[] dependentValues) {
    // Apply current parameters to a cloned fluid
    SystemInterface tunedFluid = baseFluid.clone();
    for (int i = 0; i < parameterConfigs.size(); i++) {
      RegressionParameter param = parameterConfigs.get(i).getParameter();
      param.applyToFluid(tunedFluid, params[i]);
    }
    tunedFluid.init(0);
    tunedFluid.init(1);

    // Parse dependent values
    double pressure = dependentValues[0];
    double temperature = dependentValues[1];
    int experimentTypeOrdinal = (int) dependentValues[2];
    int propertyIndex = (int) dependentValues[3];

    ExperimentType experimentType = ExperimentType.values()[experimentTypeOrdinal];

    // Calculate appropriate property based on experiment type
    switch (experimentType) {
      case CCE:
        return calculateCCEValue(tunedFluid, pressure, temperature, propertyIndex);
      case CVD:
        return calculateCVDValue(tunedFluid, pressure, temperature, propertyIndex);
      case DLE:
        return calculateDLEValue(tunedFluid, pressure, temperature, propertyIndex);
      case SEPARATOR:
        double reservoirTemperature = dependentValues.length > 4 ? dependentValues[4] : temperature;
        return calculateSeparatorValue(tunedFluid, pressure, temperature, propertyIndex,
            reservoirTemperature);
      default:
        return 0.0;
    }
  }

  /**
   * Calculate CCE property value.
   */
  private double calculateCCEValue(SystemInterface fluid, double pressure, double temperature,
      int propertyIndex) {
    ConstantMassExpansion cme = new ConstantMassExpansion(fluid);
    cme.setPressures(new double[] {pressure});
    cme.setTemperature(temperature, "K");
    cme.runCalc();

    switch (propertyIndex) {
      case 0: // Relative volume
        double[] relVol = cme.getRelativeVolume();
        return relVol != null && relVol.length > 0 ? relVol[0] : 1.0;
      case 1: // Y-factor
        double[] yFactor = cme.getYfactor();
        return yFactor != null && yFactor.length > 0 ? yFactor[0] : 1.0;
      default:
        return 1.0;
    }
  }

  /**
   * Calculate CVD property value.
   */
  private double calculateCVDValue(SystemInterface fluid, double pressure, double temperature,
      int propertyIndex) {
    ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid);
    cvd.setPressures(new double[] {pressure});
    cvd.setTemperature(temperature, "K");
    cvd.runCalc();

    switch (propertyIndex) {
      case 0: // Liquid dropout
        double[] liquidVol = cvd.getLiquidRelativeVolume();
        return liquidVol != null && liquidVol.length > 0 ? liquidVol[0] : 0.0;
      case 1: // Z-factor
        double[] zGas = cvd.getZgas();
        return zGas != null && zGas.length > 0 ? zGas[0] : 1.0;
      default:
        return 0.0;
    }
  }

  /**
   * Calculate DLE property value.
   */
  private double calculateDLEValue(SystemInterface fluid, double pressure, double temperature,
      int propertyIndex) {
    DifferentialLiberation dle = new DifferentialLiberation(fluid);
    dle.setPressures(new double[] {pressure});
    dle.setTemperature(temperature, "K");
    dle.runCalc();

    switch (propertyIndex) {
      case 0: // Rs
        double[] rs = dle.getRs();
        return rs != null && rs.length > 0 ? rs[0] : 0.0;
      case 1: // Bo
        double[] bo = dle.getBo();
        return bo != null && bo.length > 0 ? bo[0] : 1.0;
      case 2: // Oil density
        double[] density = dle.getOilDensity();
        return density != null && density.length > 0 ? density[0] : 800.0;
      default:
        return 0.0;
    }
  }

  /**
   * Calculate separator test property value.
   */
  private double calculateSeparatorValue(SystemInterface fluid, double separatorPressure,
      double separatorTemperature, int propertyIndex, double reservoirTemperature) {
    // Clone fluid and set reservoir conditions first
    SystemInterface reservoirFluid = fluid.clone();
    reservoirFluid.setTemperature(reservoirTemperature);
    reservoirFluid.setPressure(500); // High pressure for reservoir
    reservoirFluid.init(0);
    reservoirFluid.init(1);

    // Flash to separator conditions
    SystemInterface sepFluid = reservoirFluid.clone();
    sepFluid.setTemperature(separatorTemperature);
    sepFluid.setPressure(separatorPressure);
    try {
      neqsim.thermodynamicoperations.ThermodynamicOperations thermoOps =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(sepFluid);
      thermoOps.TPflash();
    } catch (Exception e) {
      return 0.0;
    }

    sepFluid.initPhysicalProperties();

    switch (propertyIndex) {
      case 0: // GOR
        if (sepFluid.getNumberOfPhases() > 1) {
          double gasVol = sepFluid.getPhase(0).getVolume(); // Gas phase
          double oilVol = sepFluid.getPhase(1).getVolume(); // Oil phase
          return gasVol / oilVol;
        }
        return 0.0;
      case 1: // Bo
        if (sepFluid.getNumberOfPhases() > 1) {
          double oilVolRes = reservoirFluid.getPhase(1).getVolume();
          double oilVolStd = sepFluid.getPhase(1).getVolume();
          return oilVolRes / oilVolStd;
        }
        return 1.0;
      default:
        return 0.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    // Apply bounds
    if (bounds != null && i < bounds.length) {
      value = Math.max(bounds[i][0], Math.min(bounds[i][1], value));
    }
    params[i] = value;
  }
}
