package neqsim.process.equipment.compressor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Spline based implementation of the surge curve with safe extrapolation.
 *
 * @author esol
 */
public class SafeSplineSurgeCurve extends SurgeCurve {
  private static final long serialVersionUID = 1001L;
  static Logger logger = LogManager.getLogger(SafeSplineSurgeCurve.class);

  private double[] sortedHead;
  private double[] sortedFlow;

  private transient UnivariateFunction headFromFlow; // head = f(flow)
  private transient UnivariateFunction flowFromHead; // flow = f(head)

  /**
   * Flag indicating single-point surge (for single-speed compressors). When true, surge flow is
   * constant regardless of head.
   */
  private boolean isSinglePoint = false;

  /** Single surge flow value used when isSinglePoint is true. */
  private double singleSurgeFlow = 0.0;

  /** Single surge head value used when isSinglePoint is true. */
  private double singleSurgeHead = 0.0;

  /**
   * Default constructor.
   */
  public SafeSplineSurgeCurve() {}

  /**
   * Create a spline based surge curve from flow and head arrays.
   *
   * @param flow array of flow values
   * @param head array of head values
   */
  public SafeSplineSurgeCurve(double[] flow, double[] head) {
    setCurve(null, flow, head);
  }

  /**
   * <p>
   * Getter for the field <code>sortedHead</code>.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] getSortedHead() {
    return sortedHead;
  }

  /**
   * <p>
   * Getter for the field <code>sortedFlow</code>.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] getSortedFlow() {
    return sortedFlow;
  }

  /** {@inheritDoc} */
  @Override
  public void setCurve(double[] chartConditions, double[] flow, double[] head) {
    if (flow.length != head.length) {
      throw new IllegalArgumentException("Flow and head arrays must have the same length.");
    }
    if (flow.length < 1) {
      throw new IllegalArgumentException("Flow and head arrays must have at least 1 point.");
    }

    // Handle single-point surge for single-speed compressors
    if (flow.length == 1) {
      this.isSinglePoint = true;
      this.singleSurgeFlow = flow[0];
      this.singleSurgeHead = head[0];
      this.flow = new double[] {flow[0]};
      this.head = new double[] {head[0]};
      this.sortedFlow = new double[] {flow[0]};
      this.sortedHead = new double[] {head[0]};
      this.chartConditions =
          chartConditions == null ? null : Arrays.copyOf(chartConditions, chartConditions.length);
      this.headFromFlow = null;
      this.flowFromHead = null;
      setActive(true);
      return;
    }

    this.isSinglePoint = false;
    int n = flow.length;

    Double[][] flowHeadPairs = new Double[n][2];
    for (int i = 0; i < n; i++) {
      flowHeadPairs[i][0] = flow[i];
      flowHeadPairs[i][1] = head[i];
    }
    Arrays.sort(flowHeadPairs, Comparator.comparingDouble(p -> p[0]));

    this.flow = new double[n];
    this.head = new double[n];
    for (int i = 0; i < n; i++) {
      this.flow[i] = flowHeadPairs[i][0];
      this.head[i] = flowHeadPairs[i][1];
    }

    this.chartConditions =
        chartConditions == null ? null : Arrays.copyOf(chartConditions, chartConditions.length);

    SplineInterpolator interpolator = new SplineInterpolator();
    this.headFromFlow = interpolator.interpolate(this.flow, this.head);

    TreeMap<Double, Double> uniqueHeadFlow = new TreeMap<>();
    for (int i = 0; i < n; i++) {
      uniqueHeadFlow.put(this.head[i], this.flow[i]);
    }

    int m = uniqueHeadFlow.size();
    this.sortedHead = new double[m];
    this.sortedFlow = new double[m];

    int i = 0;
    for (Entry<Double, Double> entry : uniqueHeadFlow.entrySet()) {
      this.sortedHead[i] = entry.getKey();
      this.sortedFlow[i] = entry.getValue();
      i++;
    }

    if (m < 2) {
      throw new IllegalArgumentException(
          "Not enough distinct head values for spline interpolation.");
    }

    this.flowFromHead = interpolator.interpolate(this.sortedHead, this.sortedFlow);
    setActive(true);
  }

  /** {@inheritDoc} */
  @Override
  public double getFlow(double headValue) {
    if (!isActive()) {
      return 0.0;
    }

    // For single-point surge (single-speed compressors), return constant surge flow
    if (isSinglePoint) {
      return singleSurgeFlow;
    }

    if (flowFromHead == null) {
      setCurve(chartConditions, flow, head);
    }

    try {
      double minHead = sortedHead[0];
      double maxHead = sortedHead[sortedHead.length - 1];

      if (headValue >= minHead && headValue <= maxHead) {
        return Math.max(0.0, flowFromHead.value(headValue));
      }

      double slope;
      double extrapolated;
      if (headValue < minHead) {
        slope = (sortedFlow[1] - sortedFlow[0]) / (sortedHead[1] - sortedHead[0]);
        extrapolated = sortedFlow[0] + slope * (headValue - sortedHead[0]);
      } else {
        slope = (sortedFlow[sortedFlow.length - 1] - sortedFlow[sortedFlow.length - 2])
            / (sortedHead[sortedHead.length - 1] - sortedHead[sortedHead.length - 2]);
        extrapolated = sortedFlow[sortedFlow.length - 1]
            + slope * (headValue - sortedHead[sortedHead.length - 1]);
      }

      return Math.max(0.0, extrapolated);
    } catch (Exception e) {
      logger.error("Error evaluating surge flow from head = " + headValue, e);
      return 0.0;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Wrapper retaining old API.
   * </p>
   */
  public double getSurgeFlow(double headValue) {
    return getFlow(headValue);
  }

  /**
   * Get head value corresponding to a given flow.
   *
   * @param flowValue flow value
   * @return corresponding head
   */
  public double getSurgeHead(double flowValue) {
    if (!isActive()) {
      return 0.0;
    }

    // For single-point surge (single-speed compressors), return constant surge head
    if (isSinglePoint) {
      return singleSurgeHead;
    }

    if (headFromFlow == null) {
      setCurve(chartConditions, flow, head);
    }

    try {
      double minFlow = flow[0];
      double maxFlow = flow[flow.length - 1];
      if (minFlow > maxFlow) {
        double temp = minFlow;
        minFlow = maxFlow;
        maxFlow = temp;
      }

      if (flowValue >= minFlow && flowValue <= maxFlow) {
        return headFromFlow.value(flowValue);
      }

      double slope;
      double extrapolated;
      if (flowValue < minFlow) {
        slope = (head[1] - head[0]) / (flow[1] - flow[0]);
        extrapolated = head[0] + slope * (flowValue - flow[0]);
      } else {
        slope = (head[head.length - 1] - head[head.length - 2])
            / (flow[flow.length - 1] - flow[flow.length - 2]);
        extrapolated = head[head.length - 1] + slope * (flowValue - flow[flow.length - 1]);
      }

      return Math.max(0.0, extrapolated);
    } catch (Exception e) {
      logger.error("Error evaluating surge head from flow = " + flowValue, e);
      return 0.0;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Wrapper retaining old API.
   * </p>
   */
  public boolean isSurge(double headValue, double flowValue) {
    return isLimit(headValue, flowValue);
  }

  /**
   * Check if this surge curve represents a single-point surge (for single-speed compressors).
   *
   * @return true if this is a single-point surge curve
   */
  public boolean isSinglePointSurge() {
    return isSinglePoint;
  }

  /**
   * Get the single surge flow value for single-speed compressors.
   *
   * @return the single surge flow value, or 0.0 if not a single-point surge
   */
  public double getSingleSurgeFlow() {
    return singleSurgeFlow;
  }

  /**
   * Get the single surge head value for single-speed compressors.
   *
   * @return the single surge head value, or 0.0 if not a single-point surge
   */
  public double getSingleSurgeHead() {
    return singleSurgeHead;
  }
}

