package neqsim.process.controllerdevice.structure;

/**
 * Generic interface for multi-loop control structures coordinating one or more regulators. Examples
 * include cascade, ratio and feed-forward control. The structure produces a single output signal
 * that can be connected to a manipulated variable in the process.
 *
 * @author esol
 */
public interface ControlStructureInterface extends java.io.Serializable {
  /**
   * Execute one transient calculation step.
   *
   * @param dt time step in seconds
   */
  void runTransient(double dt);

  /**
   * Get resulting output signal from the control structure.
   *
   * @return control signal
   */
  double getOutput();

  /**
   * Enable or disable the entire control structure.
   *
   * @param isActive set {@code true} to enable
   */
  void setActive(boolean isActive);

  /**
   * Check whether the control structure is currently active.
   *
   * @return {@code true} if active
   */
  boolean isActive();
}
