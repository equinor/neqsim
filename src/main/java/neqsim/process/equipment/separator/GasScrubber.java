/*
 * GasScrubber.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.process.equipment.separator;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;

/**
 * <p>
 * GasScrubber class.
 * </p>
 *
 * <p>
 * A gas scrubber is a vertical separator designed primarily for removing liquid droplets from gas
 * streams. Unlike standard separators, the key performance metric is the K-value (Souders-Brown
 * factor) rather than liquid retention time.
 * </p>
 *
 * <h2>Capacity Utilization Setup</h2>
 *
 * <p>
 * To get meaningful capacity utilization from {@link #getCapacityUtilization()}, set:
 * </p>
 * <ol>
 * <li>{@link #setInternalDiameter(double)} — scrubber inner diameter [m]</li>
 * <li>{@link #setDesignGasLoadFactor(double)} — design K-factor [m/s], typically 0.04–0.10 for
 * vertical scrubbers</li>
 * </ol>
 *
 * <p>
 * The orientation is automatically set to "vertical" and the design liquid level fraction defaults
 * to 0.1 (10%), reflecting that scrubbers hold very little liquid. For dry gas (no liquid phase), a
 * default liquid density of 1000 kg/m³ is used.
 * </p>
 *
 * <p>
 * <b>Example:</b>
 * </p>
 *
 * <pre>
 * GasScrubber scrubber = new GasScrubber("inlet scrubber", feedStream);
 * scrubber.setInternalDiameter(1.2); // 1.2 m ID
 * scrubber.setDesignGasLoadFactor(0.08); // K = 0.08 m/s
 * scrubber.run();
 * double util = scrubber.getCapacityUtilization(); // e.g. 0.65 = 65%
 * </pre>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GasScrubber extends Separator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for GasScrubber.
   *
   * @param name name of gas scrubber
   */
  public GasScrubber(String name) {
    super(name);
    this.setOrientation("vertical");
    this.setDesignLiquidLevelFraction(0.1);
    // Use only K-value constraint for gas scrubbers
    useGasScrubberConstraints();
  }

  /**
   * <p>
   * Constructor for GasScrubber.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.Stream} object
   */
  public GasScrubber(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.setOrientation("vertical");
    this.setDesignLiquidLevelFraction(0.1);
    // Use only K-value constraint for gas scrubbers
    useGasScrubberConstraints();
  }

  /** {@inheritDoc} */
  @Override
  public GasScrubberMechanicalDesign getMechanicalDesign() {
    return new GasScrubberMechanicalDesign(this);
  }
}
