package neqsim.process.equipment.reactor.digestion;

/**
 * Declared fidelity of a biological conversion model.
 *
 * @author NeqSim team
 * @version 1.0
 */
public enum ModelFidelity {
  /** Prescribed yields and removal fractions for early screening. */
  SCREENING,
  /** Kinetic or transport-aware model suitable for concept studies after calibration. */
  ENGINEERING,
  /** Model calibrated to a named experimental, pilot, or operating data set. */
  CALIBRATED
}
