package neqsim.process.equipment.valve;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Check valve (non-return valve) that prevents reverse flow.
 * 
 * <p>
 * CheckValve is a self-actuating valve that opens when forward differential pressure exceeds the
 * cracking pressure, and closes when flow reverses or stops. It is essential for protecting
 * equipment like pumps and compressors from reverse flow damage.
 * </p>
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Automatic operation based on differential pressure</li>
 * <li>Configurable cracking pressure (minimum ΔP to open)</li>
 * <li>Prevents backflow in piping systems</li>
 * <li>Zero leakage when closed (ideal model)</li>
 * <li>Minimal pressure drop when fully open</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Common applications:
 * <ul>
 * <li>Pump discharge protection</li>
 * <li>Compressor discharge protection</li>
 * <li>Parallel equipment isolation</li>
 * <li>Gravity drainage systems</li>
 * <li>Preventing siphoning</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * CheckValve checkValve = new CheckValve("CV-101", pumpDischargeStream);
 * checkValve.setCrackingPressure(0.2); // Opens at 0.2 bar differential
 * checkValve.setCv(250.0); // Flow coefficient when fully open
 * checkValve.run();
 * 
 * if (checkValve.isOpen()) {
 *   System.out.println("Check valve is open, flow is forward");
 * } else {
 *   System.out.println("Check valve is closed, no flow or reverse ΔP");
 * }
 * </pre>
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class CheckValve extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Minimum differential pressure required to open valve (bara). */
  private double crackingPressure = 0.1; // Default 0.1 bar

  /** Indicates if valve is currently open. */
  private boolean isOpen = false;

  /** Pressure drop coefficient when closed (very high resistance). */
  private static final double CLOSED_RESISTANCE = 1.0e-6;

  /**
   * Constructor for CheckValve.
   *
   * @param name name of the check valve
   */
  public CheckValve(String name) {
    super(name);
    // Check valves are typically fully open or fully closed
    setPercentValveOpening(100.0);
  }

  /**
   * Constructor for CheckValve.
   *
   * @param name name of the check valve
   * @param inStream inlet stream
   */
  public CheckValve(String name, StreamInterface inStream) {
    super(name, inStream);
    setPercentValveOpening(100.0);
  }

  /**
   * Set the cracking pressure - minimum differential pressure to open valve.
   *
   * @param crackingPressure cracking pressure in bara
   */
  public void setCrackingPressure(double crackingPressure) {
    this.crackingPressure = crackingPressure;
  }

  /**
   * Get the cracking pressure.
   *
   * @return cracking pressure in bara
   */
  public double getCrackingPressure() {
    return crackingPressure;
  }

  /**
   * Check if valve is currently open.
   *
   * @return true if valve is open, false if closed
   */
  public boolean isOpen() {
    return isOpen;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Override run method to implement check valve logic. Valve opens when inlet pressure exceeds
   * outlet pressure by cracking pressure, and closes otherwise.
   * </p>
   */
  @Override
  public void run() {
    // Get inlet pressure
    double inletPressure = getInletStream().getPressure("bara");

    // For initial run, assume outlet pressure from setOutletPressure or calculate
    double outletPressure;
    if (getOutletStream() != null && getOutletStream().getFlowRate("kg/hr") > 0) {
      outletPressure = getOutletStream().getPressure("bara");
    } else {
      // Use set pressure or estimate
      outletPressure = inletPressure - 0.05; // Small default drop
    }

    // Calculate differential pressure
    double differentialPressure = inletPressure - outletPressure;

    // Determine valve state
    if (differentialPressure >= crackingPressure) {
      // Forward flow - valve opens
      isOpen = true;
      setPercentValveOpening(100.0);
    } else {
      // Reverse flow or insufficient pressure - valve closes
      isOpen = false;
      setPercentValveOpening(0.0);
      // Set very high resistance when closed
      if (getCv() > 0) {
        double tempCv = getCv();
        setCv(CLOSED_RESISTANCE);
        super.run();
        setCv(tempCv); // Restore original Cv
        return;
      }
    }

    // Run parent throttling valve calculation
    super.run();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns a string representation specific to check valves.
   * </p>
   */
  @Override
  public String toString() {
    return String.format("%s [Check Valve] - State: %s, Cracking P: %.3f bara, Cv: %.1f", getName(),
        isOpen ? "OPEN" : "CLOSED", crackingPressure, getCv());
  }
}
