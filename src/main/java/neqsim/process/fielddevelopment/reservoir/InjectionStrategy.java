package neqsim.process.fielddevelopment.reservoir;

import java.io.Serializable;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Injection strategy for reservoir pressure maintenance.
 *
 * <p>
 * This class calculates required injection rates for voidage replacement and pressure maintenance
 * strategies. Supports water injection, gas injection, and WAG (water-alternating-gas).
 * </p>
 *
 * <p>
 * <b>Voidage Replacement:</b>
 * </p>
 * <p>
 * Voidage replacement ratio (VRR) is defined as: VRR = (Injection Volume) / (Production Voidage)
 * where production voidage is the reservoir volume of produced fluids. VRR = 1.0 maintains
 * reservoir pressure.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see SimpleReservoir
 */
public class InjectionStrategy implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Strategy types. */
  public enum StrategyType {
    /** No injection - natural depletion. */
    NATURAL_DEPLETION,
    /** Water injection only. */
    WATER_INJECTION,
    /** Gas injection only. */
    GAS_INJECTION,
    /** Water alternating gas. */
    WAG,
    /** Pressure maintenance at target. */
    PRESSURE_MAINTENANCE
  }

  private StrategyType strategyType = StrategyType.NATURAL_DEPLETION;

  /** Target voidage replacement ratio (1.0 = full replacement). */
  private double targetVRR = 1.0;

  /** WAG ratio (water cycles / gas cycles). */
  private double wagRatio = 1.0;

  /** WAG cycle duration in days. */
  private double wagCycleDays = 30.0;

  /** Target reservoir pressure for pressure maintenance (bara). */
  private double targetPressure = 0.0;

  /** Maximum water injection rate (Sm3/day). */
  private double maxWaterRate = Double.MAX_VALUE;

  /** Maximum gas injection rate (Sm3/day). */
  private double maxGasRate = Double.MAX_VALUE;

  /** Injection fluid temperature (K). */
  private double injectionTemperature = 288.15;

  /** Water injection efficiency (0-1). */
  private double waterInjectionEfficiency = 0.95;

  /** Gas injection efficiency (0-1). */
  private double gasInjectionEfficiency = 0.90;

  /**
   * Creates a new injection strategy.
   *
   * @param strategyType strategy type
   */
  public InjectionStrategy(StrategyType strategyType) {
    this.strategyType = strategyType;
  }

  /**
   * Creates a water injection strategy.
   *
   * @param targetVRR target voidage replacement ratio
   * @return injection strategy
   */
  public static InjectionStrategy waterInjection(double targetVRR) {
    InjectionStrategy strategy = new InjectionStrategy(StrategyType.WATER_INJECTION);
    strategy.targetVRR = targetVRR;
    return strategy;
  }

  /**
   * Creates a gas injection strategy.
   *
   * @param targetVRR target voidage replacement ratio
   * @return injection strategy
   */
  public static InjectionStrategy gasInjection(double targetVRR) {
    InjectionStrategy strategy = new InjectionStrategy(StrategyType.GAS_INJECTION);
    strategy.targetVRR = targetVRR;
    return strategy;
  }

  /**
   * Creates a WAG strategy.
   *
   * @param wagRatio water/gas ratio
   * @param cycleDays cycle duration in days
   * @return injection strategy
   */
  public static InjectionStrategy wag(double wagRatio, double cycleDays) {
    InjectionStrategy strategy = new InjectionStrategy(StrategyType.WAG);
    strategy.wagRatio = wagRatio;
    strategy.wagCycleDays = cycleDays;
    return strategy;
  }

  /**
   * Creates a pressure maintenance strategy.
   *
   * @param targetPressureBara target reservoir pressure in bara
   * @return injection strategy
   */
  public static InjectionStrategy pressureMaintenance(double targetPressureBara) {
    InjectionStrategy strategy = new InjectionStrategy(StrategyType.PRESSURE_MAINTENANCE);
    strategy.targetPressure = targetPressureBara;
    return strategy;
  }

  /**
   * Calculates required injection rates for voidage replacement.
   *
   * @param reservoir the reservoir
   * @param oilProductionRate oil production rate (Sm3/day)
   * @param gasProductionRate gas production rate (Sm3/day)
   * @param waterProductionRate water production rate (Sm3/day)
   * @return injection result with water and gas rates
   */
  public InjectionResult calculateInjection(SimpleReservoir reservoir, double oilProductionRate,
      double gasProductionRate, double waterProductionRate) {

    InjectionResult result = new InjectionResult();
    result.strategyType = this.strategyType;

    if (strategyType == StrategyType.NATURAL_DEPLETION) {
      result.waterInjectionRate = 0.0;
      result.gasInjectionRate = 0.0;
      return result;
    }

    // Calculate production voidage at reservoir conditions
    double productionVoidage = calculateProductionVoidage(reservoir, oilProductionRate,
        gasProductionRate, waterProductionRate);

    result.productionVoidage = productionVoidage;

    // Calculate required injection volume
    double requiredInjectionVolume = productionVoidage * targetVRR;

    switch (strategyType) {
      case WATER_INJECTION:
        result.waterInjectionRate = calculateWaterRate(reservoir, requiredInjectionVolume);
        result.gasInjectionRate = 0.0;
        break;

      case GAS_INJECTION:
        result.gasInjectionRate = calculateGasRate(reservoir, requiredInjectionVolume);
        result.waterInjectionRate = 0.0;
        break;

      case WAG:
        // Distribute injection between water and gas based on WAG ratio
        double waterFraction = wagRatio / (1.0 + wagRatio);
        result.waterInjectionRate =
            calculateWaterRate(reservoir, requiredInjectionVolume * waterFraction);
        result.gasInjectionRate =
            calculateGasRate(reservoir, requiredInjectionVolume * (1.0 - waterFraction));
        break;

      case PRESSURE_MAINTENANCE:
        // For pressure maintenance, calculate based on compressibility
        result.waterInjectionRate = calculatePressureMaintenanceRate(reservoir);
        break;

      default:
        break;
    }

    // Apply rate limits
    result.waterInjectionRate =
        Math.min(result.waterInjectionRate, maxWaterRate) * waterInjectionEfficiency;
    result.gasInjectionRate =
        Math.min(result.gasInjectionRate, maxGasRate) * gasInjectionEfficiency;

    // Calculate actual VRR achieved
    double actualInjectionVolume =
        calculateInjectionVolume(reservoir, result.waterInjectionRate, result.gasInjectionRate);
    result.achievedVRR = productionVoidage > 0 ? actualInjectionVolume / productionVoidage : 0.0;

    return result;
  }

  /**
   * Calculates production voidage at reservoir conditions.
   *
   * @param reservoir the reservoir
   * @param oilRate oil rate (Sm3/day)
   * @param gasRate gas rate (Sm3/day)
   * @param waterRate water rate (Sm3/day)
   * @return production voidage (m3/day at reservoir conditions)
   */
  private double calculateProductionVoidage(SimpleReservoir reservoir, double oilRate,
      double gasRate, double waterRate) {

    SystemInterface reservoirFluid = reservoir.getReservoirFluid();
    double reservoirPressure = reservoirFluid.getPressure();
    double reservoirTemperature = reservoirFluid.getTemperature();

    // Calculate Bo (oil formation volume factor)
    double bo = 1.2; // Default
    try {
      SystemInterface oil = reservoirFluid.phaseToSystem("oil");
      if (oil != null) {
        oil.setTemperature(reservoirTemperature);
        oil.setPressure(reservoirPressure);
        ThermodynamicOperations ops = new ThermodynamicOperations(oil);
        ops.TPflash();
        oil.initProperties();
        double oilDensityRes = oil.getDensity("kg/m3");

        oil.setTemperature(288.15);
        oil.setPressure(1.01325);
        ops.TPflash();
        oil.initProperties();
        double oilDensityStd = oil.getDensity("kg/m3");

        bo = oilDensityStd / oilDensityRes;
      }
    } catch (Exception e) {
      // Use default
    }

    // Calculate Bg (gas formation volume factor)
    double bg =
        reservoirPressure > 0 ? 1.01325 / reservoirPressure * reservoirTemperature / 288.15 : 0.01;

    // Calculate Bw (water formation volume factor) - approximately 1.0
    double bw = 1.02;

    // Total voidage
    return oilRate * bo + gasRate * bg + waterRate * bw;
  }

  /**
   * Calculates water injection rate for target reservoir volume.
   *
   * @param reservoir the reservoir
   * @param targetVolume target reservoir volume (m3/day)
   * @return water injection rate (Sm3/day)
   */
  private double calculateWaterRate(SimpleReservoir reservoir, double targetVolume) {
    // Bw is approximately 1.02 at reservoir conditions
    double bw = 1.02;
    return targetVolume / bw;
  }

  /**
   * Calculates gas injection rate for target reservoir volume.
   *
   * @param reservoir the reservoir
   * @param targetVolume target reservoir volume (m3/day)
   * @return gas injection rate (Sm3/day)
   */
  private double calculateGasRate(SimpleReservoir reservoir, double targetVolume) {
    SystemInterface reservoirFluid = reservoir.getReservoirFluid();
    double reservoirPressure = reservoirFluid.getPressure();
    double reservoirTemperature = reservoirFluid.getTemperature();

    // Bg = Pstd/P * T/Tstd * Z
    double z = 0.9; // Approximate
    double bg = 1.01325 / reservoirPressure * reservoirTemperature / 288.15 * z;

    return targetVolume / bg;
  }

  /**
   * Calculates injection rate for pressure maintenance.
   *
   * @param reservoir the reservoir
   * @return water injection rate (Sm3/day)
   */
  private double calculatePressureMaintenanceRate(SimpleReservoir reservoir) {
    // Simplified calculation based on reservoir compressibility
    // In practice, this would require reservoir simulation
    SystemInterface reservoirFluid = reservoir.getReservoirFluid();
    double currentPressure = reservoirFluid.getPressure();
    double pressureDifference = targetPressure - currentPressure;

    // Estimate based on typical compressibility
    double compressibility = 1e-5; // 1/bar
    double reservoirVolume = 1e8; // m3, would need to get from reservoir

    return pressureDifference * compressibility * reservoirVolume / 365.0;
  }

  /**
   * Calculates injection volume at reservoir conditions.
   *
   * @param reservoir the reservoir
   * @param waterRate water rate (Sm3/day)
   * @param gasRate gas rate (Sm3/day)
   * @return injection volume (m3/day at reservoir conditions)
   */
  private double calculateInjectionVolume(SimpleReservoir reservoir, double waterRate,
      double gasRate) {
    SystemInterface reservoirFluid = reservoir.getReservoirFluid();
    double reservoirPressure = reservoirFluid.getPressure();
    double reservoirTemperature = reservoirFluid.getTemperature();

    double bw = 1.02;
    double z = 0.9;
    double bg = 1.01325 / reservoirPressure * reservoirTemperature / 288.15 * z;

    return waterRate * bw + gasRate * bg;
  }

  // Getters and setters

  /**
   * Gets the strategy type.
   *
   * @return strategy type
   */
  public StrategyType getStrategyType() {
    return strategyType;
  }

  /**
   * Sets the target VRR.
   *
   * @param targetVRR target voidage replacement ratio
   * @return this for chaining
   */
  public InjectionStrategy setTargetVRR(double targetVRR) {
    this.targetVRR = targetVRR;
    return this;
  }

  /**
   * Gets the target VRR.
   *
   * @return target VRR
   */
  public double getTargetVRR() {
    return targetVRR;
  }

  /**
   * Sets maximum water injection rate.
   *
   * @param maxWaterRate max rate (Sm3/day)
   * @return this for chaining
   */
  public InjectionStrategy setMaxWaterRate(double maxWaterRate) {
    this.maxWaterRate = maxWaterRate;
    return this;
  }

  /**
   * Sets maximum gas injection rate.
   *
   * @param maxGasRate max rate (Sm3/day)
   * @return this for chaining
   */
  public InjectionStrategy setMaxGasRate(double maxGasRate) {
    this.maxGasRate = maxGasRate;
    return this;
  }

  /**
   * Sets injection temperature.
   *
   * @param temperatureK temperature in Kelvin
   * @return this for chaining
   */
  public InjectionStrategy setInjectionTemperature(double temperatureK) {
    this.injectionTemperature = temperatureK;
    return this;
  }

  /**
   * Result container for injection calculations.
   */
  public static class InjectionResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Strategy type used. */
    public StrategyType strategyType;

    /** Required water injection rate (Sm3/day). */
    public double waterInjectionRate;

    /** Required gas injection rate (Sm3/day). */
    public double gasInjectionRate;

    /** Production voidage (m3/day at reservoir conditions). */
    public double productionVoidage;

    /** Achieved VRR. */
    public double achievedVRR;

    @Override
    public String toString() {
      return String.format("InjectionResult[water=%.0f Sm3/d, gas=%.0f Sm3/d, VRR=%.2f]",
          waterInjectionRate, gasInjectionRate, achievedVRR);
    }
  }
}
