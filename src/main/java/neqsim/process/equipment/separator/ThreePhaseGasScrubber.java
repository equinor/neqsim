/*
 * ThreePhaseGasScrubber.java
 */

package neqsim.process.equipment.separator;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * ThreePhaseGasScrubber class.
 *
 * <p>
 * A three-phase gas scrubber is functionally identical to a {@link ThreePhaseSeparator} but is oriented
 * <b>vertically</b>. It separates an inlet stream into gas, oil and aqueous (water) products, with the two liquid
 * phases stacked in the bottom of the vessel (water at the bottom, oil floating on top). It is intended for inlet/fuel
 * gas scrubbing duties where a small amount of hydrocarbon liquid and free water must be knocked out and drained
 * independently.
 * </p>
 *
 * <p>
 * The class inherits the full three-phase behaviour of {@link ThreePhaseSeparator} — including the steady-state
 * three-phase flash, the transient VU-flash inventory model, the six entrainment (carry-over) paths and independent
 * water/oil level tracking — so it can be used in both steady-state and dynamic simulations.
 * </p>
 *
 * <h2>Dual liquid-level control</h2>
 *
 * <p>
 * Because the vessel holds two liquid layers, both liquid levels can be controlled independently by valves on the oil
 * and aqueous (water) outlets. During dynamic simulation the valve positions are modelled with the outlet flow
 * fractions:
 * </p>
 * <ul>
 * <li>{@link #setOilOutletFlowFraction(double)} — oil outlet valve (0 = closed, 1 = fully open)</li>
 * <li>{@link #setWaterOutletFlowFraction(double)} — aqueous outlet valve (0 = closed, 1 = fully open)</li>
 * <li>{@link #setGasOutletFlowFraction(double)} — gas outlet valve (pressure control)</li>
 * </ul>
 *
 * <p>
 * The current levels are read back with {@link #getWaterLevel()} and {@link #getOilLevel()} (heights in metres from the
 * bottom of the vessel) and can be measured with {@link neqsim.process.measurementdevice.WaterLevelTransmitter} and
 * {@link neqsim.process.measurementdevice.OilLevelTransmitter} to close level-control loops.
 * </p>
 *
 * <p>
 * <b>Example:</b>
 * </p>
 *
 * <pre>
 * ThreePhaseGasScrubber scrubber = new ThreePhaseGasScrubber("inlet scrubber", feedStream);
 * scrubber.setInternalDiameter(1.5); // 1.5 m ID
 * scrubber.setSeparatorLength(4.0); // 4.0 m tan-tan height
 * scrubber.run();
 * double waterLevel = scrubber.getWaterLevel(); // m
 * double oilLevel = scrubber.getOilLevel(); // m (water + oil)
 * </pre>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ThreePhaseGasScrubber extends ThreePhaseSeparator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for ThreePhaseGasScrubber.
   *
   * @param name name of the three-phase gas scrubber
   */
  public ThreePhaseGasScrubber(String name) {
    super(name);
    this.setOrientation("vertical");
  }

  /**
   * Constructor for ThreePhaseGasScrubber.
   *
   * @param name name of the three-phase gas scrubber
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public ThreePhaseGasScrubber(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.setOrientation("vertical");
  }
}
