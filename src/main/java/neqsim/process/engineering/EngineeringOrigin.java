package neqsim.process.engineering;

/** Identifies where an engineering value or requirement originated. */
public enum EngineeringOrigin {
  /** Explicitly supplied by a user or project design basis. */
  USER_SPECIFIED,
  /** Calculated by a NeqSim simulation or design calculation. */
  SIMULATION_CALCULATED,
  /** Proposed by a deterministic engineering rule. */
  RULE_INFERRED,
  /** Selected from an equipment or engineering database. */
  DATABASE_SELECTED,
  /** Supplied by an equipment vendor. */
  VENDOR_SUPPLIED,
  /** Reviewed and accepted by an accountable engineer. */
  ENGINEER_APPROVED
}
