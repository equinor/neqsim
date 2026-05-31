package neqsim.process.equipment.reactor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Screening model for high-temperature and low-temperature water-gas shift service.
 *
 * <p>
 * The unit wraps {@link GibbsReactor} for the equilibrium reaction family dominated by
 * {@code CO + H2O = CO2 + H2}. Methane, nitrogen, and oxygen are treated as inert so the model
 * represents a shift converter rather than another reformer stage. It is intended for blue-H2 and
 * syngas process templates where a fast, repeatable equilibrium endpoint is more useful than a
 * vendor-calibrated rate model.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class WaterGasShiftReactor extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Shift reactor target temperature in Kelvin. */
  private double shiftTemperatureK = 623.15;

  /** Pressure drop across the shift bed in bar. */
  private double pressureDropBar = 0.5;

  /** Internal Gibbs equilibrium reactor from the most recent run. */
  private transient GibbsReactor equilibriumReactor;

  /** Carbon monoxide conversion from the most recent run. */
  private double carbonMonoxideConversion = 0.0;

  /** Hydrogen mole-flow gain from the most recent run. */
  private double hydrogenMoleFlowGain = 0.0;

  /** Carbon dioxide mole-flow formation from the most recent run. */
  private double carbonDioxideMoleFlowFormation = 0.0;

  /** Isothermal heat duty from the most recent run in kW. */
  private double heatDutyKW = 0.0;

  /** WGS reaction quotient from the most recent run. */
  private double wgsEquilibriumRatio = Double.NaN;

  /**
   * Constructs a water-gas shift reactor without an inlet stream.
   *
   * @param name unit operation name
   */
  public WaterGasShiftReactor(String name) {
    super(name);
  }

  /**
   * Constructs a water-gas shift reactor with an inlet stream.
   *
   * @param name unit operation name
   * @param inletStream syngas inlet stream
   */
  public WaterGasShiftReactor(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Sets the isothermal shift temperature in Kelvin.
   *
   * @param temperatureK shift temperature in Kelvin, greater than zero
   */
  public void setShiftTemperature(double temperatureK) {
    validatePositive(temperatureK, "temperatureK");
    this.shiftTemperatureK = temperatureK;
  }

  /**
   * Sets the isothermal shift temperature with unit conversion.
   *
   * @param temperature shift temperature value
   * @param unit temperature unit, accepted values are K, C, and F
   */
  public void setShiftTemperature(double temperature, String unit) {
    setShiftTemperature(toKelvin(temperature, unit));
  }

  /**
   * Gets the configured shift temperature.
   *
   * @return shift temperature in Kelvin
   */
  public double getShiftTemperature() {
    return shiftTemperatureK;
  }

  /**
   * Sets the pressure drop across the shift reactor.
   *
   * @param pressureDropBar pressure drop in bar, greater than or equal to zero
   */
  public void setPressureDrop(double pressureDropBar) {
    if (!Double.isFinite(pressureDropBar) || pressureDropBar < 0.0) {
      throw new IllegalArgumentException("pressureDropBar must be finite and non-negative");
    }
    this.pressureDropBar = pressureDropBar;
  }

  /**
   * Gets the configured pressure drop.
   *
   * @return pressure drop in bar
   */
  public double getPressureDrop() {
    return pressureDropBar;
  }

  /**
   * Gets carbon monoxide conversion.
   *
   * @return fraction of inlet CO converted, bounded between zero and one
   */
  public double getCarbonMonoxideConversion() {
    return carbonMonoxideConversion;
  }

  /**
   * Gets hydrogen mole-flow gain.
   *
   * @return additional hydrogen moles in the outlet stream basis
   */
  public double getHydrogenMoleFlowGain() {
    return hydrogenMoleFlowGain;
  }

  /**
   * Gets carbon dioxide mole-flow formation.
   *
   * @return additional carbon dioxide moles in the outlet stream basis
   */
  public double getCarbonDioxideMoleFlowFormation() {
    return carbonDioxideMoleFlowFormation;
  }

  /**
   * Gets isothermal heat duty.
   *
   * @return heat duty in kW, positive when heat is added to the reactor
   */
  public double getHeatDutyKW() {
    return heatDutyKW;
  }

  /**
   * Gets the water-gas shift reaction quotient.
   *
   * @return ratio {@code CO2 * H2 / (CO * H2O)}, or {@link Double#NaN} if unavailable
   */
  public double getWgsEquilibriumRatio() {
    return wgsEquilibriumRatio;
  }

  /**
   * Gets whether the internal equilibrium reactor converged.
   *
   * @return true if the most recent Gibbs reactor converged
   */
  public boolean hasConverged() {
    return equilibriumReactor != null && equilibriumReactor.hasConverged();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (getInletStream() == null) {
      throw new IllegalStateException("WaterGasShiftReactor requires an inlet stream");
    }

    SystemInterface inletSystem = getInletStream().getThermoSystem().clone();
    HydrogenProductionUtils.ensureSyngasComponents(inletSystem);
    double coIn = HydrogenProductionUtils.getComponentMoles(inletSystem, "CO");
    double h2In = HydrogenProductionUtils.getComponentMoles(inletSystem, "hydrogen");
    double co2In = HydrogenProductionUtils.getComponentMoles(inletSystem, "CO2");

    inletSystem.setTemperature(shiftTemperatureK);
    inletSystem.setPressure(Math.max(0.1, inletSystem.getPressure("bara") - pressureDropBar),
        "bara");
    inletSystem.init(0);
    inletSystem.init(3);

    Stream reactorFeed = new Stream(getName() + " equilibrium feed", inletSystem);
    reactorFeed.run(id);

    equilibriumReactor = HydrogenProductionUtils.createSyngasGibbsReactor(
        getName() + " equilibrium", reactorFeed, GibbsReactor.EnergyMode.ISOTHERMAL);
    equilibriumReactor.setComponentAsInert("methane");
    equilibriumReactor.setComponentAsInert("nitrogen");
    equilibriumReactor.setComponentAsInert("oxygen");
    equilibriumReactor.run(id);

    SystemInterface outletSystem = equilibriumReactor.getOutletStream().getThermoSystem().clone();
    getOutletStream().setThermoSystem(outletSystem);
    getOutletStream().run(id);
    updateMetrics(outletSystem, coIn, h2In, co2In);
    setCalculationIdentifier(id);
  }

  /**
   * Gets a compact result map for reporting and agent workflows.
   *
   * @return ordered map of key water-gas shift metrics
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("shiftTemperatureK", shiftTemperatureK);
    results.put("pressureDropBar", pressureDropBar);
    results.put("carbonMonoxideConversion", carbonMonoxideConversion);
    results.put("hydrogenMoleFlowGain", hydrogenMoleFlowGain);
    results.put("carbonDioxideMoleFlowFormation", carbonDioxideMoleFlowFormation);
    results.put("heatDutyKW", heatDutyKW);
    results.put("wgsEquilibriumRatio", wgsEquilibriumRatio);
    results.put("converged", hasConverged());
    results.put("outletComposition",
        HydrogenProductionUtils.extractSyngasComposition(getOutletStream().getThermoSystem()));
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(getResults());
  }

  /**
   * Updates reactor metrics after equilibrium calculation.
   *
   * @param outletSystem outlet equilibrium system
   * @param coIn inlet carbon monoxide amount
   * @param h2In inlet hydrogen amount
   * @param co2In inlet carbon dioxide amount
   */
  private void updateMetrics(SystemInterface outletSystem, double coIn, double h2In, double co2In) {
    double coOut = HydrogenProductionUtils.getComponentMoles(outletSystem, "CO");
    double h2Out = HydrogenProductionUtils.getComponentMoles(outletSystem, "hydrogen");
    double co2Out = HydrogenProductionUtils.getComponentMoles(outletSystem, "CO2");
    double waterOut = HydrogenProductionUtils.getComponentMoles(outletSystem, "water");

    carbonMonoxideConversion =
        coIn > 0.0 ? HydrogenProductionUtils.clamp((coIn - coOut) / coIn, 0.0, 1.0) : 0.0;
    hydrogenMoleFlowGain = h2Out - h2In;
    carbonDioxideMoleFlowFormation = co2Out - co2In;
    heatDutyKW = equilibriumReactor != null ? equilibriumReactor.getPower("kW") : 0.0;
    wgsEquilibriumRatio =
        calculateWgsEquilibriumRatio(outletSystem, coOut, waterOut, co2Out, h2Out);
  }

  /**
   * Calculates the WGS reaction quotient from outlet component amounts.
   *
   * @param outletSystem outlet system used only for finite-system validation
   * @param coOut outlet carbon monoxide amount
   * @param waterOut outlet water amount
   * @param co2Out outlet carbon dioxide amount
   * @param h2Out outlet hydrogen amount
   * @return WGS reaction quotient, or {@link Double#NaN} when undefined
   */
  private double calculateWgsEquilibriumRatio(SystemInterface outletSystem, double coOut,
      double waterOut, double co2Out, double h2Out) {
    if (outletSystem == null || coOut <= 0.0 || waterOut <= 0.0) {
      return Double.NaN;
    }
    return co2Out * h2Out / (coOut * waterOut);
  }

  /**
   * Converts temperature values to Kelvin.
   *
   * @param temperature temperature value
   * @param unit unit string, accepted values are K, C, and F
   * @return temperature in Kelvin
   */
  private double toKelvin(double temperature, String unit) {
    if (unit == null || "K".equalsIgnoreCase(unit)) {
      return temperature;
    }
    if ("C".equalsIgnoreCase(unit)) {
      return temperature + 273.15;
    }
    if ("F".equalsIgnoreCase(unit)) {
      return (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    }
    throw new IllegalArgumentException("Unsupported temperature unit: " + unit);
  }

  /**
   * Validates positive finite values.
   *
   * @param value value to validate
   * @param parameterName parameter name used in exception messages
   */
  private void validatePositive(double value, String parameterName) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(parameterName + " must be finite and greater than zero");
    }
  }
}
