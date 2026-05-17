package neqsim.process.measurementdevice;

/**
 * Defines the role of a measurement device when used in a digital-twin or field-data integration
 * context.
 *
 * <ul>
 * <li>{@link #INPUT} — field data drives the model input (boundary condition). The instrument reads
 * a value from the field historian and applies it to its connected stream or equipment.</li>
 * <li>{@link #BENCHMARK} — field data is compared against the model prediction for validation and
 * parameter optimisation. The deviation between model and field is tracked.</li>
 * <li>{@link #VIRTUAL} — the model provides a calculated value as a virtual (soft) sensor. No field
 * data is expected; the model output IS the measurement.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public enum InstrumentTagRole {
  /**
   * Field data sets the model input. The instrument pushes received field values into its connected
   * stream or equipment as boundary conditions.
   */
  INPUT,

  /**
   * Field data is compared against the model value for validation, calibration, and optimisation.
   */
  BENCHMARK,

  /**
   * Model-calculated value serves as a virtual measurement (soft sensor). No field data is needed.
   */
  VIRTUAL
}
