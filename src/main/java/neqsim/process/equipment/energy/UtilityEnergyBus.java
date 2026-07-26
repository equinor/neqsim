package neqsim.process.equipment.energy;

import java.util.Objects;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyNetworkReport;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.UtilityLevel;
import neqsim.util.unit.PowerUnit;

/**
 * Thermal {@link EnergyBus} carrying a standard utility grade and optional supply/return thermodynamic states.
 *
 * <p>
 * A solved energy duty can be converted to physical utility mass flow when supply and return specific enthalpies are
 * configured. Heating utilities must lose enthalpy from supply to return; cooling and refrigeration utilities must gain
 * enthalpy while absorbing process heat.
 * </p>
 *
 * @author NeqSim
 * @version 2.0
 */
public class UtilityEnergyBus extends EnergyBus {
  private static final long serialVersionUID = 1000L;

  private double returnTemperature = Double.NaN;
  private ThermalUtilityState supplyState;
  private ThermalUtilityState returnState;

  /**
   * Creates a utility bus.
   *
   * @param name bus name
   * @param utilityLevel utility grade
   */
  public UtilityEnergyBus(String name, UtilityLevel utilityLevel) {
    super(name, EnergyType.HEAT);
    if (utilityLevel == null || utilityLevel == UtilityLevel.UNSPECIFIED) {
      throw new IllegalArgumentException("A specified utility level is required");
    }
    getQuality().setUtilityLevel(utilityLevel);
  }

  /**
   * Creates a utility bus with explicit supply and return temperatures.
   *
   * @param name bus name
   * @param utilityLevel utility grade
   * @param supplyTemperature supply temperature in K
   * @param returnTemperature return temperature in K
   */
  public UtilityEnergyBus(String name, UtilityLevel utilityLevel, double supplyTemperature, double returnTemperature) {
    this(name, utilityLevel);
    setSupplyTemperature(supplyTemperature);
    setReturnTemperature(returnTemperature);
  }

  /**
   * Creates a utility bus with complete thermodynamic states.
   *
   * @param name bus name
   * @param utilityLevel utility grade
   * @param supplyState supply state
   * @param returnState return state
   */
  public UtilityEnergyBus(String name, UtilityLevel utilityLevel, ThermalUtilityState supplyState,
      ThermalUtilityState returnState) {
    this(name, utilityLevel);
    setThermodynamicStates(supplyState, returnState);
  }

  /**
   * Sets supply and return thermodynamic states used for mass-flow conversion.
   *
   * @param supplyState supply state
   * @param returnState return state
   */
  public void setThermodynamicStates(ThermalUtilityState supplyState, ThermalUtilityState returnState) {
    ThermalUtilityState supply = Objects.requireNonNull(supplyState, "supplyState cannot be null");
    ThermalUtilityState returns = Objects.requireNonNull(returnState, "returnState cannot be null");
    double enthalpyChange = supply.getSpecificEnthalpy() - returns.getSpecificEnthalpy();
    if (isHeatingUtility()) {
      if (enthalpyChange <= 0.0) {
        throw new IllegalArgumentException("Heating-utility supply enthalpy must exceed return enthalpy");
      }
    } else if (enthalpyChange >= 0.0) {
      throw new IllegalArgumentException("Cooling-utility return enthalpy must exceed supply enthalpy");
    }

    this.supplyState = supply;
    this.returnState = returns;
    getQuality().setTemperature(supply.getTemperature());
    getQuality().setPressure(supply.getPressure());
    returnTemperature = returns.getTemperature();
  }

  /** Removes thermodynamic states while retaining utility grade and temperature metadata. */
  public void clearThermodynamicStates() {
    supplyState = null;
    returnState = null;
  }

  /**
   * Checks whether specific-enthalpy states are available.
   *
   * @return {@code true} when both supply and return states are configured
   */
  public boolean hasThermodynamicStates() {
    return supplyState != null && returnState != null;
  }

  /**
   * Gets supply thermodynamic state.
   *
   * @return supply state, or {@code null} when not configured
   */
  public ThermalUtilityState getSupplyState() {
    return supplyState;
  }

  /**
   * Gets return thermodynamic state.
   *
   * @return return state, or {@code null} when not configured
   */
  public ThermalUtilityState getReturnState() {
    return returnState;
  }

  /**
   * Gets useful thermal duty per unit mass circulated.
   *
   * @return absolute supply-return enthalpy difference in J/kg
   */
  public double getSpecificDuty() {
    requireThermodynamicStates();
    return Math.abs(supplyState.getSpecificEnthalpy() - returnState.getSpecificEnthalpy());
  }

  /**
   * Converts thermal duty to required utility mass flow.
   *
   * @param duty absolute thermal duty in W
   * @return mass flow in kg/s
   */
  public double getMassFlowForDuty(double duty) {
    if (!Double.isFinite(duty) || duty < 0.0) {
      throw new IllegalArgumentException("Utility duty must be non-negative and finite");
    }
    return duty / getSpecificDuty();
  }

  /**
   * Converts thermal duty to required utility mass flow.
   *
   * @param duty thermal duty
   * @param powerUnit duty unit supported by {@link PowerUnit}
   * @param massFlowUnit requested mass-flow unit: kg/sec, kg/hr, or ton/hr
   * @return utility mass flow in the requested unit
   */
  public double getMassFlowForDuty(double duty, String powerUnit, String massFlowUnit) {
    double dutyInW = new PowerUnit(duty, powerUnit).getValue("W");
    return convertMassFlow(getMassFlowForDuty(dutyInW), massFlowUnit);
  }

  /**
   * Gets mass flow corresponding to requested network demand.
   *
   * @return requested mass flow in kg/s
   */
  public double getRequestedMassFlow() {
    return getMassFlowForDuty(requireLastReport().getRequestedDemand());
  }

  /**
   * Gets mass flow corresponding to served network demand.
   *
   * @return served mass flow in kg/s
   */
  public double getServedMassFlow() {
    return getMassFlowForDuty(requireLastReport().getServedDemand());
  }

  /**
   * Gets mass flow corresponding to offered utility supply.
   *
   * @return offered mass flow in kg/s
   */
  public double getOfferedMassFlow() {
    return getMassFlowForDuty(requireLastReport().getOfferedSupply());
  }

  /**
   * Gets mass flow corresponding to accepted utility supply.
   *
   * @return accepted mass flow in kg/s
   */
  public double getAcceptedMassFlow() {
    return getMassFlowForDuty(requireLastReport().getAcceptedSupply());
  }

  /**
   * Gets mass flow equivalent of unmet utility demand.
   *
   * @return unmet-demand mass flow in kg/s
   */
  public double getUnmetMassFlow() {
    return getMassFlowForDuty(requireLastReport().getUnmetDemand());
  }

  /**
   * Gets mass flow equivalent of curtailed utility supply.
   *
   * @return curtailed-supply mass flow in kg/s
   */
  public double getCurtailedMassFlow() {
    return getMassFlowForDuty(requireLastReport().getCurtailedSupply());
  }

  /**
   * Gets supply temperature.
   *
   * @return temperature in K, or {@link Double#NaN} when unspecified
   */
  public double getSupplyTemperature() {
    return getQuality().getTemperature();
  }

  /**
   * Sets supply temperature.
   *
   * @param supplyTemperature temperature in K
   */
  public void setSupplyTemperature(double supplyTemperature) {
    getQuality().setTemperature(supplyTemperature);
    if (supplyState != null) {
      supplyState = new ThermalUtilityState(supplyTemperature, supplyState.getPressure(),
          supplyState.getSpecificEnthalpy());
    }
  }

  /**
   * Gets supply pressure.
   *
   * @return pressure in Pa, or {@link Double#NaN} when unspecified
   */
  public double getSupplyPressure() {
    return getQuality().getPressure();
  }

  /**
   * Sets supply pressure.
   *
   * @param supplyPressure pressure in Pa
   */
  public void setSupplyPressure(double supplyPressure) {
    getQuality().setPressure(supplyPressure);
    if (supplyState != null) {
      supplyState = new ThermalUtilityState(supplyState.getTemperature(), supplyPressure,
          supplyState.getSpecificEnthalpy());
    }
  }

  /**
   * Gets return temperature.
   *
   * @return temperature in K, or {@link Double#NaN} when unspecified
   */
  public double getReturnTemperature() {
    return returnTemperature;
  }

  /**
   * Sets return temperature.
   *
   * @param returnTemperature temperature in K
   */
  public void setReturnTemperature(double returnTemperature) {
    if (!Double.isFinite(returnTemperature) || returnTemperature <= 0.0) {
      throw new IllegalArgumentException("Return temperature must be positive and finite");
    }
    this.returnTemperature = returnTemperature;
    if (returnState != null) {
      returnState = new ThermalUtilityState(returnTemperature, returnState.getPressure(),
          returnState.getSpecificEnthalpy());
    }
  }

  /**
   * Gets utility grade.
   *
   * @return utility grade
   */
  public UtilityLevel getUtilityLevel() {
    return getQuality().getUtilityLevel();
  }

  /** Returns whether this utility delivers heat rather than absorbing it. */
  private boolean isHeatingUtility() {
    UtilityLevel level = getUtilityLevel();
    return level == UtilityLevel.HIGH_PRESSURE_STEAM || level == UtilityLevel.MEDIUM_PRESSURE_STEAM
        || level == UtilityLevel.LOW_PRESSURE_STEAM || level == UtilityLevel.HOT_OIL;
  }

  /** Requires supply and return states for a mass-flow calculation. */
  private void requireThermodynamicStates() {
    if (!hasThermodynamicStates()) {
      throw new IllegalStateException("Configure supply and return thermodynamic states before calculating mass flow");
    }
  }

  /** Requires a completed energy-network solve. */
  private EnergyNetworkReport requireLastReport() {
    EnergyNetworkReport report = getLastReport();
    if (report == null) {
      throw new IllegalStateException("Solve the utility energy bus before requesting report mass flows");
    }
    return report;
  }

  /** Converts kg/s to one supported reporting unit. */
  private static double convertMassFlow(double massFlowKgPerSecond, String unit) {
    if ("kg/sec".equals(unit) || "kg/s".equals(unit)) {
      return massFlowKgPerSecond;
    }
    if ("kg/hr".equals(unit)) {
      return massFlowKgPerSecond * 3600.0;
    }
    if ("ton/hr".equals(unit) || "t/hr".equals(unit)) {
      return massFlowKgPerSecond * 3.6;
    }
    throw new IllegalArgumentException("Unsupported utility mass-flow unit: " + unit);
  }
}
