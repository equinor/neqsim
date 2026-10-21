package neqsim.process.equipment.compressor.seal;

/**
 * Leakage correlations available in {@link LabyrinthSealFlowCalculator}.
 *
 * <p>
 * All methods estimate the gas mass flow through a straight-through labyrinth seal with teeth on the stator, from the
 * upstream state, the downstream pressure and the seal geometry. Each method is capped at its critical (choked) flow.
 * </p>
 *
 * @author neqsim
 * @version 1.0
 */
public enum LabyrinthFlowMethod {
  /** Stodola equation with a constant carry-over coefficient of 0.71. */
  STODOLA,
  /** Egli method with the contraction, throttling and carry-over coefficients fitted by Aungier. */
  EGLI,
  /** Vermes equation with a residual-energy (carry-over) factor from tooth pitch, tip width and clearance. */
  VERMES,
  /** Kearton equation with the Kearton clearance correction and a Fanno-type pressure profile. */
  KEARTON,
  /**
   * Modified Neumann method: tooth-by-tooth pressure solution with a pressure-ratio dependent flow coefficient, kinetic
   * carry-over after the first tooth, isenthalpic expansion (PH flash) at each tooth and choking at any tooth.
   */
  NEUMANN
}
