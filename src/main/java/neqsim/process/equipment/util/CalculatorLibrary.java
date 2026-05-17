package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.Locale;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.splitter.Splitter;
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
    DEW_POINT_TARGETING,

    /**
     * Anti-surge recycle calculation. Adjusts a splitter's recycle flow so that the compressor
     * inlet actual volumetric flow stays at or above the surge flow. Works in actual flow (m3/hr)
     * space to match the compressor chart's surge curve coordinates.
     */
    ANTI_SURGE
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

    String normalized =
        presetName.trim().toUpperCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
    if ("DEWPOINTTARGETING".equals(normalized)) {
      normalized = "DEW_POINT_TARGETING";
    } else if ("ENERGYBALANCE".equals(normalized)) {
      normalized = "ENERGY_BALANCE";
    } else if ("ANTISURGE".equals(normalized)) {
      normalized = "ANTI_SURGE";
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
      case ANTI_SURGE:
        return antiSurge();
      default:
        throw new IllegalArgumentException("Unsupported preset: " + preset);
    }
  }

  /**
   * Create an energy-balance calculator. The output stream is flashed at its current pressure so
   * that its enthalpy equals the sum of the input stream enthalpies.
   *
   * @return a calculator function that performs energy balance
   */
  public static BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> energyBalance() {
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
   * @return a calculator function that performs dew point targeting
   */
  public static BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> dewPointTargeting(
      double marginKelvin) {
    return (inputs, output) -> {
      Stream sourceStream = requireFirstStream(inputs, "dew point source");
      Stream targetStream = requireStream(output, "dew point target");

      double referencePressure = targetStream.getThermoSystem().getPressure("bara");
      double dewPointK = sourceStream.getHydrocarbonDewPoint("K", referencePressure, "bara");

      targetStream.setPressure(referencePressure, "bara");
      targetStream.setTemperature(dewPointK + marginKelvin, "K");
      targetStream.run();
    };
  }

  /**
   * Create a dew-point targeting calculator with zero temperature margin.
   *
   * @return a calculator function that performs dew point targeting
   */
  public static BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> dewPointTargeting() {
    return dewPointTargeting(0.0);
  }

  /**
   * Create an anti-surge recycle calculator with no surge margin. The recycle flow through the
   * splitter's second outlet is adjusted so the compressor inlet actual volumetric flow equals the
   * surge flow from the compressor chart.
   *
   * <p>
   * Input variable 0 must be a {@link Compressor} with a loaded compressor chart. The output
   * variable must be a {@link Splitter} whose split stream index 1 is the recycle line.
   * </p>
   *
   * @return a calculator function that performs anti-surge recycle adjustment
   */
  public static BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> antiSurge() {
    return antiSurge(1.0);
  }

  /**
   * Create an anti-surge recycle calculator with a configurable surge margin factor. The target
   * flow is {@code surgeFlow * surgeMarginFactor}. A factor of 1.1 targets 10 % above surge.
   *
   * <p>
   * All flow comparisons use actual volumetric flow (m3/hr) to match the coordinate system of the
   * compressor chart's surge curve.
   * </p>
   *
   * @param surgeMarginFactor multiplicative margin applied to the surge flow (1.0 = no margin)
   * @return a calculator function that performs anti-surge recycle adjustment
   */
  public static BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> antiSurge(
      double surgeMarginFactor) {
    return (inputs, output) -> {
      if (inputs == null || inputs.isEmpty() || !(inputs.get(0) instanceof Compressor)) {
        throw new IllegalArgumentException(
            "Anti-surge calculation requires a Compressor as first input");
      }
      if (!(output instanceof Splitter)) {
        throw new IllegalArgumentException("Anti-surge calculation requires a Splitter as output");
      }

      Compressor compressor = (Compressor) inputs.get(0);
      Splitter splitter = (Splitter) output;

      double inletFlow = compressor.getInletStream().getFlowRate("m3/hr");
      double currentRecycleFlow = splitter.getSplitStream(1).getFlowRate("m3/hr");

      if (!Double.isFinite(inletFlow) || !Double.isFinite(currentRecycleFlow)) {
        logger.warn("Invalid flow rate detected during anti-surge calculation");
        return;
      }

      double surgeFlow = compressor.getSurgeFlowRate() * surgeMarginFactor;

      // Proportional adjustment toward surge target in actual flow space
      double flowAntiSurge = currentRecycleFlow + 0.5 * (surgeFlow - inletFlow);
      flowAntiSurge = Math.max(flowAntiSurge, inletFlow / 1e6);

      splitter.setFlowRates(new double[] {-1, flowAntiSurge}, "m3/hr");
      splitter.getSplitStream(1).setFlowRate(flowAntiSurge, "m3/hr");
      splitter.getSplitStream(1).run();
    };
  }

  private static Stream requireStream(ProcessEquipmentInterface equipment, String role) {
    if (equipment instanceof Stream) {
      return (Stream) equipment;
    }
    throw new IllegalArgumentException("Expected a Stream for " + role);
  }

  private static Stream requireFirstStream(ArrayList<ProcessEquipmentInterface> inputs,
      String role) {
    if (inputs == null || inputs.isEmpty()) {
      throw new IllegalArgumentException("Expected at least one input stream for " + role);
    }
    return requireStream(inputs.get(0), role);
  }
}
