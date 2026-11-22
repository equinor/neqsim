package neqsim.process.mechanicaldesign.compressor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Screening assessment of the axial "null thrust" condition for balance-piston centrifugal compressors.
 *
 * <p>
 * A multistage centrifugal compressor with a balance piston is normally sized so that the net rotor axial thrust
 * (impeller thrust minus balance-piston thrust) stays small and of one sign across the intended operating envelope;
 * vendor rotor axial thrust calculation reports (for example per API 617) tabulate the minimum, average and maximum
 * predicted thrust load for each operating case and typically also give the average thrust load as a function of inlet
 * flow at a fixed (certified) speed. Because the net thrust normally falls with increasing flow (higher flow raises the
 * discharge-to-suction pressure differential across the balance piston faster than the summed impeller thrust), there
 * is usually one flow at which the net thrust crosses zero. Operating close to that flow gives the thrust bearing very
 * little net load to react, so the rotor can float axially with reduced restoring force; this "null thrust" condition
 * is reported by rotating-equipment specialists as a contributor to increased axial vibration and, over time, to
 * accelerated wear of components that must accommodate relative axial sliding, such as a dry-gas-seal dynamic O-ring.
 * </p>
 *
 * <p>
 * This class fits a straight line through a vendor thrust-versus-flow data set (least squares), finds the zero-crossing
 * ("null thrust") flow, and expresses an operating flow's distance from that crossing as a signed margin fraction of
 * the crossing flow. It is a screening indicator only: it does not perform a rotordynamic or thrust-bearing design
 * calculation and does not replace the OEM's own thrust calculation report.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class AxialThrustScreening implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Private constructor; use {@link #evaluate(double[], double[], double, double)}. */
  private AxialThrustScreening() {
  }

  /**
   * Least-squares straight-line fit {@code thrust = slope * flow + intercept}.
   *
   * @param flow flow values (any consistent flow unit, e.g. Sm3/h), at least two distinct values
   * @param thrust thrust load values at the same points (any consistent unit, e.g. MPa), same length as {@code flow}
   * @return array {@code {slope, intercept}}
   * @throws IllegalArgumentException if the arrays are null, of different length, have fewer than two points, or all
   * flow values are equal, or the data or fitted coefficients are not finite
   */
  public static double[] fitLinear(double[] flow, double[] thrust) {
    if (flow == null || thrust == null || flow.length != thrust.length || flow.length < 2) {
      throw new IllegalArgumentException(
          "flow and thrust must be non-null, of equal length, and contain at least two points");
    }
    int n = flow.length;
    double sumX = 0.0;
    double sumY = 0.0;
    for (int i = 0; i < n; i++) {
      if (!Double.isFinite(flow[i]) || !Double.isFinite(thrust[i])) {
        throw new IllegalArgumentException("flow and thrust values must be finite");
      }
      sumX += flow[i];
      sumY += thrust[i];
    }
    double meanX = sumX / n;
    double meanY = sumY / n;
    double sxy = 0.0;
    double sxx = 0.0;
    for (int i = 0; i < n; i++) {
      double dx = flow[i] - meanX;
      sxy += dx * (thrust[i] - meanY);
      sxx += dx * dx;
    }
    if (sxx == 0.0) {
      throw new IllegalArgumentException("flow values must not all be equal");
    }
    double slope = sxy / sxx;
    double intercept = meanY - slope * meanX;
    if (!Double.isFinite(slope) || !Double.isFinite(intercept)) {
      throw new IllegalArgumentException("fitted coefficients must be finite; rescale the input data");
    }
    return new double[] {slope, intercept};
  }

  /**
   * Flow at which the fitted thrust trend crosses zero (the "null thrust" flow).
   *
   * @param slope slope of the fitted trend (thrust per unit flow)
   * @param intercept intercept of the fitted trend
   * @return the flow at which {@code slope * flow + intercept == 0}
   * @throws IllegalArgumentException if slope is zero or the inputs or crossing are not finite
   */
  public static double zeroCrossingFlow(double slope, double intercept) {
    if (slope == 0.0 || !Double.isFinite(slope) || !Double.isFinite(intercept)) {
      throw new IllegalArgumentException("slope must be non-zero for a zero-crossing to exist");
    }
    double crossing = -intercept / slope;
    if (!Double.isFinite(crossing)) {
      throw new IllegalArgumentException("zero-crossing flow must be finite");
    }
    return crossing;
  }

  /**
   * Evaluate an operating flow against a vendor thrust-versus-flow trend.
   *
   * @param flow vendor flow values used to establish the trend, at least two points
   * @param thrust vendor thrust load values at the same points, same length as {@code flow}
   * @param operatingFlow the flow to screen (same unit as {@code flow})
   * @param marginThresholdFraction absolute margin fraction below which the operating flow is flagged as near the
   * null-thrust crossing (for example 0.10 for +/-10 %), must be positive
   * @return the screening result
   * @throws IllegalArgumentException if inputs or results are non-finite, operating flow is negative, the threshold is
   * not positive, or there is no positive crossing to normalize the margin
   */
  public static Result evaluate(double[] flow, double[] thrust, double operatingFlow, double marginThresholdFraction) {
    if (!Double.isFinite(operatingFlow) || operatingFlow < 0.0) {
      throw new IllegalArgumentException("operatingFlow must be non-negative and finite");
    }
    if (!(marginThresholdFraction > 0.0) || Double.isInfinite(marginThresholdFraction)) {
      throw new IllegalArgumentException("marginThresholdFraction must be positive and finite");
    }
    double[] fit = fitLinear(flow, thrust);
    double slope = fit[0];
    double intercept = fit[1];
    double crossingFlow = zeroCrossingFlow(slope, intercept);
    if (crossingFlow <= 0.0) {
      throw new IllegalArgumentException("a positive zero-crossing flow is required for a relative margin");
    }
    double predictedThrust = slope * operatingFlow + intercept;
    double marginFraction = (operatingFlow - crossingFlow) / crossingFlow;
    if (!Double.isFinite(predictedThrust) || !Double.isFinite(marginFraction)) {
      throw new IllegalArgumentException("predicted thrust and margin must be finite; rescale the input data");
    }
    boolean nearNullThrust = Math.abs(marginFraction) < marginThresholdFraction;
    String direction = predictedThrust > 0.0 ? "OUTBOARD" : predictedThrust < 0.0 ? "INBOARD" : "NULL";
    return new Result(slope, intercept, crossingFlow, operatingFlow, predictedThrust, marginFraction, nearNullThrust,
        marginThresholdFraction, direction);
  }

  /**
   * Result of an axial null-thrust screening evaluation.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double slope;
    private final double intercept;
    private final double zeroCrossingFlow;
    private final double operatingFlow;
    private final double predictedThrust;
    private final double marginFraction;
    private final boolean nearNullThrust;
    private final double marginThresholdFraction;
    private final String thrustDirection;

    /**
     * Create a result.
     *
     * @param slope slope of the fitted thrust-vs-flow trend
     * @param intercept intercept of the fitted trend
     * @param zeroCrossingFlow flow at which the trend crosses zero thrust
     * @param operatingFlow the flow that was screened
     * @param predictedThrust trend-predicted thrust load at {@code operatingFlow}
     * @param marginFraction signed distance of {@code operatingFlow} from the crossing flow, as a fraction of the
     * crossing flow
     * @param nearNullThrust true if {@code |marginFraction|} is below the configured threshold
     * @param marginThresholdFraction the threshold fraction used for the flag
     * @param thrustDirection {@code "INBOARD"}, {@code "OUTBOARD"} or {@code "NULL"} for the predicted thrust sign at
     * {@code operatingFlow}
     */
    Result(double slope, double intercept, double zeroCrossingFlow, double operatingFlow, double predictedThrust,
        double marginFraction, boolean nearNullThrust, double marginThresholdFraction, String thrustDirection) {
      this.slope = slope;
      this.intercept = intercept;
      this.zeroCrossingFlow = zeroCrossingFlow;
      this.operatingFlow = operatingFlow;
      this.predictedThrust = predictedThrust;
      this.marginFraction = marginFraction;
      this.nearNullThrust = nearNullThrust;
      this.marginThresholdFraction = marginThresholdFraction;
      this.thrustDirection = thrustDirection;
    }

    /**
     * Get the slope of the fitted thrust-versus-flow trend.
     *
     * @return slope (thrust per unit flow)
     */
    public double getSlope() {
      return slope;
    }

    /**
     * Get the intercept of the fitted thrust-versus-flow trend.
     *
     * @return intercept
     */
    public double getIntercept() {
      return intercept;
    }

    /**
     * Get the flow at which the fitted trend crosses zero thrust.
     *
     * @return the null-thrust flow
     */
    public double getZeroCrossingFlow() {
      return zeroCrossingFlow;
    }

    /**
     * Get the operating flow that was screened.
     *
     * @return operating flow
     */
    public double getOperatingFlow() {
      return operatingFlow;
    }

    /**
     * Get the trend-predicted thrust load at the operating flow.
     *
     * @return predicted thrust load
     */
    public double getPredictedThrust() {
      return predictedThrust;
    }

    /**
     * Get the signed margin from the null-thrust crossing, as a fraction of the crossing flow.
     *
     * @return margin fraction; negative values are below the crossing flow, positive above it
     */
    public double getMarginFraction() {
      return marginFraction;
    }

    /**
     * Check whether the operating flow is within the configured margin of the null-thrust crossing.
     *
     * @return true if {@code |marginFraction|} is below the configured threshold
     */
    public boolean isNearNullThrust() {
      return nearNullThrust;
    }

    /**
     * Get the margin threshold fraction used to flag proximity to the crossing.
     *
     * @return margin threshold fraction
     */
    public double getMarginThresholdFraction() {
      return marginThresholdFraction;
    }

    /**
     * Get the predicted thrust direction at the operating flow.
     *
     * @return {@code "INBOARD"}, {@code "OUTBOARD"} or {@code "NULL"}
     */
    public String getThrustDirection() {
      return thrustDirection;
    }

    /**
     * Serialise the result to JSON.
     *
     * @return pretty-printed JSON string
     */
    public String toJson() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("slope", slope);
      map.put("intercept", intercept);
      map.put("zeroCrossingFlow", zeroCrossingFlow);
      map.put("operatingFlow", operatingFlow);
      map.put("predictedThrust", predictedThrust);
      map.put("marginFraction", marginFraction);
      map.put("nearNullThrust", nearNullThrust);
      map.put("marginThresholdFraction", marginThresholdFraction);
      map.put("thrustDirection", thrustDirection);
      return new GsonBuilder().setPrettyPrinting().create().toJson(map);
    }
  }
}
