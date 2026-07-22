package neqsim.process.fielddevelopment.tieback.capacity;

/**
 * Policy for handling satellite production that cannot pass through the host in a period.
 *
 * @author ESOL
 * @version 1.0
 */
public enum HoldbackPolicy {
  /** Treat constrained production as curtailed or lost for the planning case. */
  CURTAIL,

  /** Carry constrained satellite production into the next planning period as deferred backlog. */
  DEFER_TO_LATER_YEARS
}
