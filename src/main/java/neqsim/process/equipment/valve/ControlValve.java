package neqsim.process.equipment.valve;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Control valve for process flow control.
 * 
 * <p>
 * ControlValve is a specialized throttling valve used for automatic or manual flow control in
 * process systems. It provides the same functionality as ThrottlingValve but with clearer semantic
 * meaning in process control applications.
 * </p>
 * 
 * <p>
 * Typical applications include:
 * <ul>
 * <li>Flow control valves (FCV)</li>
 * <li>Pressure control valves (PCV)</li>
 * <li>Temperature control valves (TCV)</li>
 * <li>Level control valves (LCV)</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * ControlValve flowControlValve = new ControlValve("FCV-101", inletStream);
 * flowControlValve.setPercentValveOpening(50.0); // 50% open
 * flowControlValve.setCv(300.0); // Flow coefficient
 * flowControlValve.run();
 * </pre>
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ControlValve extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for ControlValve.
   *
   * @param name name of the control valve
   */
  public ControlValve(String name) {
    super(name);
  }

  /**
   * Constructor for ControlValve.
   *
   * @param name name of the control valve
   * @param inStream inlet stream
   */
  public ControlValve(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns a string representation specific to control valves.
   * </p>
   */
  @Override
  public String toString() {
    return String.format("%s [Control Valve] - Opening: %.1f%%, Cv: %.1f", getName(),
        getPercentValveOpening(), getCv());
  }
}
