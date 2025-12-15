package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.Locale;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Catalog of reusable {@link Calculator} presets. These presets provide declarative building blocks
 * that can be paired with {@link Calculator#setCalculationMethod(BiConsumer)} to encourage
 * consistent AI- or rule-generated logic across simulations.
 */
public final class CalculatorLibrary {
  private static final Logger logger = LogManager.getLogger(CalculatorLibrary.class);

  private CalculatorLibrary() {}

  /**
   * Preset identifiers that can be resolved through {@link #byName(String)}.
   */
  public enum Preset {
    /**
     * Performs an enthalpy-based energy balance using the input streams and flashes the output
     * stream at its current pressure to match the summed enthalpy.
     */
    ENERGY_BALANCE,

    /**
     * Sets the output stream temperature to the hydrocarbon dew point of the first input stream at
     * the output stream's pressure.
     */
    DEW_POINT_TARGETING
  }

  /**
   * Resolve a preset calculation by name (case-insensitive). This is useful when wiring calculators
   * declaratively from metadata or AI-generated instructions.
   *
   * @param presetName the preset identifier (e.g., "energyBalance" or "dewPointTargeting")
   * @return a {@link BiConsumer} ready for {@link Calculator#setCalculationMethod(BiConsumer)}
   */
  public static BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> byName(
      String presetName) {
    if (presetName == null) {
      throw new IllegalArgumentException("Preset name cannot be null");
    }

    String normalized = presetName.trim().toUpperCase(Locale.ROOT).replace(" ", "_")
        .replace("-", "_");
    if ("DEWPOINTTARGETING".equals(normalized)) {
      normalized = "DEW_POINT_TARGETING";
    } else if ("ENERGYBALANCE".equals(normalized)) {
      normalized = "ENERGY_BALANCE";
    }
    try {
      return preset(Preset.valueOf(normalized));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown calculator preset: " + presetName, ex);
    }
  }

  /**
   * Resolve a preset calculation.
   *
   * @param preset the preset identifier
   * @return a {@link BiConsumer} ready for {@link Calculator#setCalculationMethod(BiConsumer)}
   */
  public static BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> preset(
      Preset preset) {
    switch (preset) {
      case ENERGY_BALANCE:
        return energyBalance();
      case DEW_POINT_TARGETING:
        return dewPointTargeting();
      default:
        throw new IllegalArgumentException("Unsupported preset: " + preset);
    }
  }

  /**
   * Create an energy-balance calculator. The output stream is flashed at its current pressure so
   * that its enthalpy equals the sum of the input stream enthalpies.
   */
  public static BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface>
      energyBalance() {
    return (inputs, output) -> {
      Stream targetStream = requireStream(output, "energy balance output");

      if (inputs == null || inputs.isEmpty()) {
        throw new IllegalArgumentException("Energy balance requires at least one input stream");
      }

      double targetPressure = targetStream.getThermoSystem().getPressure();
      double totalEnthalpy = 0.0;
      for (ProcessEquipmentInterface equipment : inputs) {
        Stream stream = requireStream(equipment, "energy balance input");
        totalEnthalpy += stream.getThermoSystem().getEnthalpy();
      }

      ThermodynamicOperations ops = new ThermodynamicOperations(targetStream.getThermoSystem());
      ops.getSystem().setPressure(targetPressure);
      try {
        ops.PHflash(totalEnthalpy);
      } catch (Exception ex) {
        logger.error("Energy balance flash failed", ex);
        throw new IllegalStateException("Energy balance flash failed", ex);
      }

      targetStream.setThermoSystem(ops.getSystem());
      targetStream.run();
    };
  }

  /**
   * Create a dew-point targeting calculator. It sets the output stream temperature to the
   * hydrocarbon dew point of the first input stream at the output stream's current pressure. A
   * small positive margin can be applied to stay above dew point.
   *
   * @param marginKelvin temperature margin to add to the dew point (K)
   */
  public static BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface>
      dewPointTargeting(double marginKelvin) {
    return (inputs, output) -> {
      Stream sourceStream = requireFirstStream(inputs, "dew point source");
      Stream targetStream = requireStream(output, "dew point target");

      double referencePressure = targetStream.getThermoSystem().getPressure("bara");
      double dewPointK =
          sourceStream.getHydrocarbonDewPoint("K", referencePressure, "bara");

      targetStream.setPressure(referencePressure, "bara");
      targetStream.setTemperature(dewPointK + marginKelvin, "K");
      targetStream.run();
    };
  }

  /**
   * Create a dew-point targeting calculator with zero temperature margin.
   */
  public static BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface>
      dewPointTargeting() {
    return dewPointTargeting(0.0);
  }

  private static Stream requireStream(ProcessEquipmentInterface equipment, String role) {
    if (equipment instanceof Stream) {
      return (Stream) equipment;
    }
    throw new IllegalArgumentException("Expected a Stream for " + role);
  }

  private static Stream requireFirstStream(ArrayList<ProcessEquipmentInterface> inputs, String role) {
    if (inputs == null || inputs.isEmpty()) {
      throw new IllegalArgumentException("Expected at least one input stream for " + role);
    }
    return requireStream(inputs.get(0), role);
  }
}
