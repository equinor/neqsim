package neqsim.process.engineering.calculation;

/** Typed, readiness-gated engineering calculation contract. */
public interface EngineeringCalculationModule<I, O> {
  /** Stable method identifier used in provenance and regression tests. */
  String getMethod();

  /** Version of the implemented method, independent of the NeqSim release version. */
  String getMethodVersion();

  /** Evaluates whether the supplied input is sufficient to run the calculation. */
  CalculationReadiness assess(I input, EngineeringCalculationContext context);

  /** Runs the calculation and returns a typed, review-gated result. */
  EngineeringCalculationResult<O> calculate(I input, EngineeringCalculationContext context);
}
