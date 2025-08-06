package neqsim.process.equipment.compressor;

/**
 * Common interface for compressor limit curves such as surge and stone wall curves.
 */
public interface BoundaryCurveInterface extends java.io.Serializable {
  /**
   * Define the curve from arrays of flow and head values with optional chart conditions.
   *
   * @param chartConditions conditions for the reference curve, or null
   * @param flow array of flow values
   * @param head array of head values
   */
  void setCurve(double[] chartConditions, double[] flow, double[] head);

  /**
   * Get the flow corresponding to a given head value on the curve.
   *
   * @param head the head value
   * @return the corresponding flow
   */
  double getFlow(double head);

  /**
   * Check if a flow/head point is on the limiting side of the curve.
   *
   * @param head the head value
   * @param flow the flow value
   * @return true if the point violates the limit represented by the curve
   */
  boolean isLimit(double head, double flow);

  /**
   * Determine if the curve is active in calculations.
   *
   * @return true if the curve is active
   */
  boolean isActive();

  /**
   * Activate or deactivate the curve.
   *
   * @param isActive true to activate the curve
   */
  void setActive(boolean isActive);
}

