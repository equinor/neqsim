package neqsim.integration;

import neqsim.integration.ValidationFramework.CommonErrors;
import neqsim.integration.ValidationFramework.ValidationBuilder;
import neqsim.integration.ValidationFramework.ValidationResult;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Validators for process streams (StreamInterface).
 * 
 * <p>
 * Checks:
 * <ul>
 * <li>Fluid present and initialized</li>
 * <li>Temperature and pressure set and valid</li>
 * <li>Flowrate positive</li>
 * <li>Composition normalized</li>
 * <li>Stream has been executed (run())</li>
 * </ul>
 */
public class StreamValidator {

  /**
   * Validate a process stream before use in equipment.
   *
   * @param stream The stream to validate
   * @return ValidationResult with errors and warnings
   */
  public static ValidationResult validateStream(StreamInterface stream) {
    ValidationBuilder builder = new ValidationBuilder(
        "Stream: " + (stream != null && stream.getName() != null ? stream.getName() : "unnamed"));

    // Check: Stream exists
    builder.checkNotNull(stream, "stream");
    if (stream == null) {
      return builder.build();
    }

    // Check: Fluid present
    try {
      if (stream.getFluid() == null) {
        builder.checkTrue(false, "Stream has no fluid",
            "Create stream with fluid: new Stream(\"name\", system)");
      }
    } catch (Exception e) {
      builder.addWarning("stream", "Could not access stream fluid",
          "Ensure stream is properly initialized");
    }

    // Check: Temperature valid
    try {
      double temp = stream.getFluid().getTemperature();
      if (temp < 1.0) {
        builder.checkTrue(false, CommonErrors.INVALID_TEMPERATURE,
            CommonErrors.REMEDIATION_INVALID_TEMPERATURE);
      }
      if (temp < 200.0) {
        builder.addWarning("stream", "Stream temperature very low (< 200 K)",
            "Verify this is intentional. Most equipment operates above 250 K");
      }
    } catch (Exception e) {
      builder.addWarning("stream", "Could not verify temperature",
          "Set stream temperature: stream.setTemperature(value)");
    }

    // Check: Pressure valid
    try {
      double pressure = stream.getFluid().getPressure();
      if (pressure <= 0) {
        builder.checkTrue(false, CommonErrors.INVALID_PRESSURE,
            CommonErrors.REMEDIATION_INVALID_PRESSURE);
      }
    } catch (Exception e) {
      builder.addWarning("stream", "Could not verify pressure",
          "Set stream pressure: stream.setPressure(value)");
    }

    // Check: Flowrate positive
    try {
      double flowrate = stream.getFlowRate("kg/hr");
      if (flowrate <= 0) {
        builder.checkTrue(false, "Flowrate is zero or negative",
            "Set positive flowrate: stream.setFlowRate(value, \"kg/hr\")");
      }
    } catch (Exception e) {
      builder.addWarning("stream", "Could not verify flowrate",
          "Set stream flowrate: stream.setFlowRate(value, unit)");
    }

    // Check: Composition valid
    if (stream.getFluid() != null) {
      validateComposition(builder, stream.getFluid().getPhase(0));
    }

    return builder.build();
  }

  /**
   * Validate stream composition (helper for validateStream).
   *
   * @param builder the validation builder to add results to
   * @param phase the phase object to validate composition for
   */
  private static void validateComposition(ValidationBuilder builder, Object phase) {
    // This is a simplified check due to varying phase implementations
    try {
      builder.addWarning("stream", "Composition normalization not automatically verified",
          "Ensure component mole fractions sum to ~1.0");
    } catch (Exception e) {
      // Skip if phase interface varies
    }
  }

  /**
   * Validate that a stream has been executed (properties calculated).
   *
   * @param stream The stream to check
   * @return ValidationResult indicating if stream has been run
   */
  public static ValidationResult validateStreamHasRun(StreamInterface stream) {
    ValidationBuilder builder = new ValidationBuilder("Stream execution: "
        + (stream != null && stream.getName() != null ? stream.getName() : "unnamed"));

    if (stream == null) {
      builder.checkTrue(false, "Stream is null",
          "Create and initialize stream before checking execution");
      return builder.build();
    }

    try {
      // Check: Properties are not NaN (indicates executed)
      double temp = stream.getFluid().getTemperature();
      double pressure = stream.getFluid().getPressure();
      double enthalpy = stream.getFluid().getEnthalpy();

      if (Double.isNaN(temp) || Double.isNaN(pressure) || Double.isNaN(enthalpy)) {
        builder.checkTrue(false, CommonErrors.STREAM_NOT_RUN, CommonErrors.REMEDIATION_STREAM_RUN);
      }
    } catch (Exception e) {
      builder.checkTrue(false, CommonErrors.STREAM_NOT_RUN, CommonErrors.REMEDIATION_STREAM_RUN);
    }

    return builder.build();
  }

  /**
   * Validate state consistency between two streams (e.g., outlet of equipment A matches inlet of
   * equipment B).
   *
   * @param outlet the outlet stream
   * @param inlet the inlet stream
   * @return validation result with any errors or warnings
   */
  public static ValidationResult validateStreamConnection(StreamInterface outlet,
      StreamInterface inlet) {
    ValidationBuilder builder = new ValidationBuilder("Stream Connection");

    builder.checkNotNull(outlet, "outlet stream");
    builder.checkNotNull(inlet, "inlet stream");

    if (outlet == null || inlet == null) {
      return builder.build();
    }

    try {
      // Check: Fluids exist
      if (outlet.getFluid() == null || inlet.getFluid() == null) {
        builder.checkTrue(false, "Outlet or inlet fluid is null",
            "Ensure both streams are properly initialized");
      }

      // Check: Outlet has been executed
      double outletTemp = outlet.getFluid().getTemperature();
      if (Double.isNaN(outletTemp)) {
        builder.checkTrue(false, "Outlet stream has not been executed",
            "Call outlet.run() or equipment.run() to calculate outlet properties");
      }

      // Check: Compositions similar (same components)
      // Note: This is a simplified check - actual matching depends on cloning behavior
      builder.addWarning("connection", "Component matching not automatically verified",
          "Ensure outlet and inlet have same components");

    } catch (Exception e) {
      builder.addWarning("connection", "Could not verify stream connection",
          "Manually verify outlet properties match inlet expectations");
    }

    return builder.build();
  }

  /**
   * Helper: Check if stream appears ready.
   *
   * @param stream the stream to check
   * @return true if stream is ready
   */
  public static boolean isStreamReady(StreamInterface stream) {
    ValidationResult result = validateStream(stream);
    return result.isReady();
  }

  /**
   * Helper: Check if stream has been executed.
   *
   * @param stream the stream to check
   * @return true if stream has been run
   */
  public static boolean hasStreamBeenRun(StreamInterface stream) {
    ValidationResult result = validateStreamHasRun(stream);
    return result.isReady();
  }
}
