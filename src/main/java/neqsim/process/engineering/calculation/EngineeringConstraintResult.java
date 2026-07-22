package neqsim.process.engineering.calculation;

/** Common contract for typed engineering results that expose an explicit constraint verdict. */
public interface EngineeringConstraintResult {
  /**
   * Tests whether every declared engineering constraint is satisfied.
   *
   * @return true only when at least one constraint exists and all declared constraints pass
   */
  boolean allConstraintsSatisfied();
}
