package neqsim.process.mechanicaldesign.designstandards;

/** Publisher lifecycle status for the default edition recorded in NeqSim. */
public enum StandardLifecycleStatus {
  /** The publisher identifies the recorded edition as current. */
  CURRENT,
  /** The recorded standard has been replaced by another publication. */
  SUPERSEDED,
  /** The publisher identifies the recorded standard as withdrawn. */
  WITHDRAWN,
  /** NeqSim has not verified the lifecycle against a current publisher source. */
  UNVERIFIED
}
