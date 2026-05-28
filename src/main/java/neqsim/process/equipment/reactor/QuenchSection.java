package neqsim.process.equipment.reactor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Fast-quench section for POX and ATR syngas cooling studies.
 *
 * <p>
 * The section cools a hot syngas stream to a specified target temperature at nearly constant
 * pressure, estimates the required heat removal, and exposes a quench severity metric. It is a
 * screening substitute for detailed radiant/convective syngas coolers and water-quench hardware.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class QuenchSection extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Target outlet temperature in Kelvin. */
  private double targetTemperatureK = 1173.15;

  /** Pressure drop across the quench section in bar. */
  private double pressureDropBar = 0.2;

  /** Heat removed in W. */
  private double heatRemovedW = 0.0;

  /** Quench severity as inlet-outlet temperature drop in K. */
  private double quenchSeverityK = 0.0;

  /**
   * Creates a quench section.
   *
   * @param name equipment name
   */
  public QuenchSection(String name) {
    super(name);
  }

  /**
   * Creates a quench section with an inlet stream.
   *
   * @param name equipment name
   * @param inletStream hot syngas inlet stream
   */
  public QuenchSection(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Sets the target outlet temperature.
   *
   * @param temperatureK target temperature in Kelvin
   */
  public void setTargetTemperature(double temperatureK) {
    if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperatureK must be finite and greater than zero");
    }
    this.targetTemperatureK = temperatureK;
  }

  /**
   * Sets the target outlet temperature with unit conversion.
   *
   * @param temperature temperature value
   * @param unit temperature unit, either K or C
   */
  public void setTargetTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      setTargetTemperature(temperature + 273.15);
    } else {
      setTargetTemperature(temperature);
    }
  }

  /**
   * Gets the target temperature.
   *
   * @return target temperature in Kelvin
   */
  public double getTargetTemperature() {
    return targetTemperatureK;
  }

  /**
   * Sets pressure drop across the quench section.
   *
   * @param pressureDropBar pressure drop in bar, non-negative
   */
  public void setPressureDrop(double pressureDropBar) {
    if (!Double.isFinite(pressureDropBar) || pressureDropBar < 0.0) {
      throw new IllegalArgumentException("pressureDropBar must be finite and non-negative");
    }
    this.pressureDropBar = pressureDropBar;
  }

  /**
   * Gets the heat removed in W.
   *
   * @return heat removed in W
   */
  public double getHeatRemoved() {
    return heatRemovedW;
  }

  /**
   * Gets the heat removed in a selected unit.
   *
   * @param unit unit string, W, kW, or MW
   * @return heat removed in requested unit
   */
  public double getHeatRemoved(String unit) {
    if (unit == null) {
      return heatRemovedW;
    }
    String normalized = unit.trim().toLowerCase();
    if ("mw".equals(normalized)) {
      return heatRemovedW / 1.0e6;
    } else if ("kw".equals(normalized)) {
      return heatRemovedW / 1.0e3;
    }
    return heatRemovedW;
  }

  /**
   * Gets the quench severity.
   *
   * @return inlet-to-outlet temperature drop in K
   */
  public double getQuenchSeverity() {
    return quenchSeverityK;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface inletSystem = getInletStream().getThermoSystem().clone();
    inletSystem.init(3);
    double inletTemperature = inletSystem.getTemperature();
    double inletEnthalpy = inletSystem.getEnthalpy("J");

    SystemInterface outletSystem = inletSystem.clone();
    outletSystem.setTemperature(targetTemperatureK);
    outletSystem.setPressure(Math.max(1.0e-6, inletSystem.getPressure() - pressureDropBar));
    ThermodynamicOperations operations = new ThermodynamicOperations(outletSystem);
    try {
      operations.TPflash();
    } catch (Exception ex) {
      outletSystem.init(3);
    }
    outletSystem.initProperties();

    double outletEnthalpy = outletSystem.getEnthalpy("J");
    heatRemovedW = Math.max(0.0, inletEnthalpy - outletEnthalpy);
    quenchSeverityK = Math.max(0.0, inletTemperature - outletSystem.getTemperature());

    outStream.setThermoSystem(outletSystem);
    outStream.run(id);
    setCalculationIdentifier(id);
  }

  /**
   * Builds a map of quench results.
   *
   * @return ordered result map
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("targetTemperatureK", targetTemperatureK);
    results.put("pressureDropBar", pressureDropBar);
    results.put("heatRemovedKW", heatRemovedW / 1.0e3);
    results.put("quenchSeverityK", quenchSeverityK);
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }
}
