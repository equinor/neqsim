package neqsim.process.equipment.valve;

import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Blowdown Valve (BDValve) for emergency depressurization systems (ESD).
 * 
 * <p>
 * A blowdown valve is a normally closed isolation valve that opens during emergency shutdown (ESD)
 * events to rapidly depressurize process equipment. The flow through the valve is controlled by a
 * downstream orifice to ensure safe and controlled depressurization rates.
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Normally closed valve for emergency depressurization</li>
 * <li>Opens on ESD activation signal</li>
 * <li>Configurable opening time</li>
 * <li>Manual activation and reset capability</li>
 * <li>Typically used with downstream orifice for flow control</li>
 * </ul>
 * 
 * <p>
 * Typical usage in ESD system:
 * 
 * <pre>
 * // Create separator with gas outlet
 * Separator separator = new Separator("HP Separator", feedStream);
 * separator.run();
 * 
 * // Split gas outlet - one to normal process, one to blowdown
 * Splitter splitter = new Splitter("Gas Splitter", separator.getGasOutStream());
 * splitter.setSplitFactors(new double[] {1.0, 0.0}); // Initially all to process
 * 
 * // Create blowdown valve (normally closed)
 * BlowdownValve bdValve = new BlowdownValve("BD-101", splitter.getSplitStream(1));
 * bdValve.setOpeningTime(5.0); // 5 seconds to fully open
 * 
 * // Create orifice to control blowdown flow rate
 * ThrottlingValve orifice = new ThrottlingValve("BD Orifice", bdValve.getOutletStream());
 * orifice.setCv(150.0); // Size for controlled depressurization
 * orifice.setOutletPressure(1.5); // Flare header pressure
 * 
 * // In emergency situation
 * bdValve.activate(); // Open blowdown valve
 * splitter.setSplitFactors(new double[] {0.0, 1.0}); // Redirect flow to blowdown
 * 
 * // In dynamic simulation loop
 * system.runTransient(dt, UUID.randomUUID());
 * // Valve gradually opens and gas flows to flare through orifice
 * </pre>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class BlowdownValve extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Indicates if blowdown valve has been activated (ESD triggered). */
  private boolean isActivated = false;

  /** Time required for valve to fully open (seconds). */
  private double openingTime = 5.0; // Default 5 seconds to open

  /** Track if valve opening is in progress. */
  private boolean isOpening = false;

  /** Time elapsed since activation started (seconds). */
  private double timeElapsed = 0.0;

  /**
   * Constructor for BlowdownValve.
   *
   * @param name name of blowdown valve
   */
  public BlowdownValve(String name) {
    super(name);
    // Blowdown valves start closed (fail-closed for safety)
    setPercentValveOpening(0.0);
  }

  /**
   * Constructor for BlowdownValve.
   *
   * @param name name of blowdown valve
   * @param inletStream inlet stream to valve
   */
  public BlowdownValve(String name, StreamInterface inletStream) {
    super(name, inletStream);
    // Blowdown valves start closed (fail-closed for safety)
    setPercentValveOpening(0.0);
  }

  /**
   * Sets the valve opening time.
   *
   * @param openingTime time in seconds for valve to open completely
   */
  public void setOpeningTime(double openingTime) {
    this.openingTime = Math.max(0.1, openingTime); // Minimum 0.1 seconds
    // Use the valve opening travel time mechanism
    setOpeningTravelTime(openingTime);
  }

  /**
   * Gets the configured opening time.
   *
   * @return opening time in seconds
   */
  public double getOpeningTime() {
    return openingTime;
  }

  /**
   * Checks if blowdown valve is activated.
   *
   * @return true if valve has been activated (ESD triggered)
   */
  public boolean isActivated() {
    return isActivated;
  }

  /**
   * Activates the blowdown valve (simulates ESD trigger).
   * 
   * <p>
   * When activated, the valve will begin opening according to the configured opening time. This
   * simulates the emergency shutdown system triggering the blowdown valve to depressurize the
   * equipment.
   * </p>
   */
  public void activate() {
    if (!isActivated) {
      isActivated = true;
      isOpening = true;
      timeElapsed = 0.0;
      // Start opening the valve
      setPercentValveOpening(0.0);
    }
  }

  /**
   * Resets the blowdown valve to its initial closed state.
   * 
   * <p>
   * This simulates the process of resetting the ESD system after an emergency event. In real
   * operations, this would require operator action and verification that the system is safe to
   * restart.
   * </p>
   */
  public void reset() {
    isActivated = false;
    isOpening = false;
    timeElapsed = 0.0;
    setPercentValveOpening(0.0);
  }

  /**
   * Manually closes the blowdown valve.
   * 
   * <p>
   * This allows manual closure of the valve after depressurization is complete.
   * </p>
   */
  public void close() {
    setPercentValveOpening(0.0);
    isOpening = false;
  }

  /**
   * Gets the current time elapsed since activation.
   *
   * @return time elapsed in seconds
   */
  public double getTimeElapsed() {
    return timeElapsed;
  }

  /**
   * Performs dynamic simulation step with automatic opening logic.
   * 
   * <p>
   * If the valve has been activated, it will gradually open according to the configured opening
   * time until fully open (100%).
   * </p>
   *
   * @param dt time step in seconds
   * @param id unique identifier for this calculation
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (isActivated && isOpening) {
      // Increment time elapsed
      timeElapsed += dt;

      // Calculate opening percentage based on time
      double openingFraction = Math.min(1.0, timeElapsed / openingTime);
      double targetOpening = openingFraction * 100.0;

      // Set valve opening
      setPercentValveOpening(targetOpening);

      // Check if fully open
      if (openingFraction >= 1.0) {
        isOpening = false;
      }
    }

    // Run base class transient calculation
    super.runTransient(dt, id);
  }

  /**
   * Checks if valve is currently in the process of opening.
   *
   * @return true if valve is opening
   */
  public boolean isOpening() {
    return isOpening;
  }

  /**
   * Gets a string representation of the blowdown valve state.
   *
   * @return string describing valve state including activation status
   */
  @Override
  public String toString() {
    return getName() + " [Blowdown Valve] - Opening: "
        + String.format("%.1f", getPercentValveOpening()) + "%, Activated: "
        + (isActivated ? "YES" : "NO") + ", Opening: " + (isOpening ? "YES" : "NO")
        + ", Time Elapsed: " + String.format("%.1f", timeElapsed) + "s";
  }
}
