package neqsim.process.equipment.compressor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * SafeSplineStoneWallCurve class.
 * </p>
 *
 * <p>
 * This class provides a spline based representation of the stone wall curve similar to
 * {@link SafeSplineSurgeCurve}. It offers safe evaluation with linear extrapolation outside the
 * provided data range.
 * </p>
 *
 * @author esol
 */
public class SafeSplineStoneWallCurve extends StoneWallCurve {
  private static final long serialVersionUID = 1001L;
  static Logger logger = LogManager.getLogger(SafeSplineStoneWallCurve.class);

  private double[] sortedHead;
  private double[] sortedFlow;

  private transient UnivariateFunction headFromFlow; // head = f(flow)
  private transient UnivariateFunction flowFromHead; // flow = f(head)

  /**
   * <p>
   * Constructor for SafeSplineStoneWallCurve.
   * </p>
   */
  public SafeSplineStoneWallCurve() {}

  /**
   * <p>
   * Constructor for SafeSplineStoneWallCurve.
   * </p>
   *
   * @param flow an array of {@link double} objects
   * @param head an array of {@link double} objects
   */
  public SafeSplineStoneWallCurve(double[] flow, double[] head) {
    setCurve(null, flow, head);
  }

  public double[] getSortedHead() {
    return sortedHead;
  }

  public double[] getSortedFlow() {
    return sortedFlow;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Sets the curve values and prepares spline interpolators in both directions. Flow and head
   * arrays are sorted by flow to ensure monotonicity.
   * </p>
   */
  @Override
  public void setCurve(double[] chartConditions, double[] flow, double[] head) {
    if (flow.length != head.length || flow.length < 2) {
      throw new IllegalArgumentException(
          "Flow and head arrays must have the same length and at least 2 points.");
    }

    int n = flow.length;

    // Sort by flow (ascending)
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

    // Interpolation: head = f(flow)
    SplineInterpolator interpolator = new SplineInterpolator();
    this.headFromFlow = interpolator.interpolate(this.flow, this.head);

    // Interpolation: flow = f(head), use TreeMap to ensure strictly increasing head values
    TreeMap<Double, Double> uniqueHeadFlow = new TreeMap<>();
    for (int i = 0; i < n; i++) {
      uniqueHeadFlow.put(this.head[i], this.flow[i]); // if duplicate, last wins
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

  /**
   * <p>
   * getStoneWallFlow.
   * </p>
   *
   * @param headValue a double
   * @return a double representing the stone wall flow for the given head
   */
  public double getStoneWallFlow(double headValue) {
    if (!isActive()) {
      return 0.0;
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

      // Linear extrapolation
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
      logger.error("Error evaluating stone wall flow from head = " + headValue, e);
      return 0.0;
    }
  }

  /**
   * <p>
   * getStoneWallHead.
   * </p>
   *
   * @param flowValue a double
   * @return a double representing the stone wall head for the given flow
   */
  public double getStoneWallHead(double flowValue) {
    if (!isActive()) {
      return 0.0;
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

      // Linear extrapolation
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
      logger.error("Error evaluating stone wall head from flow = " + flowValue, e);
      return 0.0;
    }
  }

  /**
   * <p>
   * isStoneWall.
   * </p>
   *
   * @param headValue a double
   * @param flowValue a double
   * @return a boolean
   */
  public boolean isStoneWall(double headValue, double flowValue) {
    return getStoneWallFlow(headValue) < flowValue;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isActive() {
    return super.isActive();
  }

  /** {@inheritDoc} */
  @Override
  public void setActive(boolean isActive) {
    super.setActive(isActive);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(flow), Arrays.hashCode(head),
        Arrays.hashCode(chartConditions), isActive());
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SafeSplineStoneWallCurve)) {
      return false;
    }
    SafeSplineStoneWallCurve other = (SafeSplineStoneWallCurve) obj;
    return Arrays.equals(flow, other.flow) && Arrays.equals(head, other.head)
        && Arrays.equals(chartConditions, other.chartConditions) && isActive() == other.isActive();
  }
}

