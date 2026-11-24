package neqsim.process.fielddevelopment.hybrid;

/**
 * Cumulative surface volumes (and the elapsed time) that can parameterise a reservoir-state proxy.
 *
 * <p>
 * A proxy table uses one quantity as its abscissa (for example cumulative oil produced) and a scenario blend uses a
 * second one (for example cumulative water injected) to interpolate between simulator scenarios.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum CumulativeQuantity {
  /** Cumulative stock-tank oil produced in Sm3. */
  OIL_PRODUCED,
  /** Cumulative water produced in Sm3. */
  WATER_PRODUCED,
  /** Cumulative gas produced in Sm3. */
  GAS_PRODUCED,
  /** Cumulative liquid (oil plus water) produced in Sm3. */
  LIQUID_PRODUCED,
  /** Cumulative water injected in Sm3. */
  WATER_INJECTED,
  /** Cumulative gas injected in Sm3. */
  GAS_INJECTED,
  /**
   * Elapsed production time in days. As the second coordinate of a scenario blend it tells how fast the cumulative
   * volume was produced, which separates rate-dependent behaviour such as water coning and aquifer influx.
   */
  ELAPSED_TIME
}
