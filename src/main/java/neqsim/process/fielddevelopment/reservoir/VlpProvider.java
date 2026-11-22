package neqsim.process.fielddevelopment.reservoir;

/**
 * Supplies the vertical (or full-system) lift performance used to fill a VFP table: the flowing pressure at the table
 * datum that is needed to deliver a surface rate against a given outlet pressure.
 *
 * <p>
 * Implementations wrap a calibrated model, for example a deviated tubing survey ({@link TubingVlpProvider}), a chain of
 * flowlines and risers, or a whole {@code ProcessSystem} solved for the inlet pressure ({@link ForwardVlpProvider}).
 * The interface has a single method so that a Java lambda or a Python callable can be used directly.
 * </p>
 *
 * <p>
 * All arguments use the surface-volume basis of the table being built: rates in Sm3/day of the table rate basis (oil,
 * liquid or gas), ratios in Sm3/Sm3 (WGR, OGR, WCT, GOR as selected in the builder) and gas lift in Sm3/day.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
@FunctionalInterface
public interface VlpProvider {
  /**
   * Calculates the flowing pressure at the table datum for one table coordinate.
   *
   * @param rate surface rate in Sm3/day of the table rate basis
   * @param outletPressure pressure at the outlet node (THP axis value) in bara
   * @param waterRatio water ratio in the unit of the selected water ratio type (0 when the table has no such axis)
   * @param gasRatio gas ratio in the unit of the selected gas ratio type (0 when the table has no such axis)
   * @param alq artificial lift quantity (gas lift rate in Sm3/day, 0 when not used)
   * @return flowing pressure at datum in bara; NaN or an exception marks the point as infeasible
   */
  double bottomHolePressure(double rate, double outletPressure, double waterRatio, double gasRatio, double alq);
}
