package neqsim.process.equipment.stream;

/**
 * Calculation role of an {@link EnergyPort} in a process simulation.
 *
 * <p>
 * The mode is independent of the physical direction. For example, a pressure-specified pump has an
 * {@link EnergyPortDirection#INPUT} shaft port but calculates its required power, so that port is {@link #CALCULATED}.
 * A power-specified pump reads the same physical input port as a {@link #SPECIFICATION}.
 *
 * @author NeqSim
 * @version 1.0
 */
public enum EnergyPortMode {
  /** The equipment reads the connected stream as an input specification. */
  SPECIFICATION,
  /** The equipment calculates and publishes the connected stream duty. */
  CALCULATED,
  /** The duty is determined by a wider energy balance or simultaneous solver. */
  BALANCE
}
