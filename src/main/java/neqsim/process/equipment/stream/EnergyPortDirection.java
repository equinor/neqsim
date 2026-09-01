package neqsim.process.equipment.stream;

/**
 * Physical direction of energy transfer relative to a process equipment boundary.
 *
 * @author NeqSim
 * @version 1.0
 */
public enum EnergyPortDirection {
  /** Energy enters the equipment through the port. */
  INPUT,
  /** Energy leaves the equipment through the port. */
  OUTPUT,
  /** The physical transfer direction may change during a simulation. */
  BIDIRECTIONAL
}
