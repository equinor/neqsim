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

public class SafeSplineSurgeCurve implements java.io.Serializable {

  private static final long serialVersionUID = 1001;
  static Logger logger = LogManager.getLogger(SafeSplineSurgeCurve.class);

  private double[] flow;
  private double[] head;
  private double[] chartConditions;

  private double[] sortedHead;
  private double[] sortedFlow;

  private transient UnivariateFunction headFromFlow; // head = f(flow)
  private transient UnivariateFunction flowFromHead; // flow = f(head)

  private boolean isActive = false;

  public SafeSplineSurgeCurve() {}

  public SafeSplineSurgeCurve(double[] flow, double[] head) {
    setCurve(null, flow, head);
  }

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
    this.isActive = true;
  }

  public double getSurgeFlow(double headValue) {
    if (!isActive)
      return 0.0;

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
      logger.error("Error evaluating surge flow from head = " + headValue, e);
      return 0.0;
    }
  }

  public double getSurgeHead(double flowValue) {
    if (!isActive)
      return 0.0;

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
      logger.error("Error evaluating surge head from flow = " + flowValue, e);
      return 0.0;
    }
  }

  public boolean isSurge(double headValue, double flowValue) {
    return getSurgeFlow(headValue) > flowValue;
  }

  public boolean isActive() {
    return isActive;
  }

  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(flow), Arrays.hashCode(head),
        Arrays.hashCode(chartConditions), isActive);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SafeSplineSurgeCurve))
      return false;
    SafeSplineSurgeCurve other = (SafeSplineSurgeCurve) obj;
    return Arrays.equals(flow, other.flow) && Arrays.equals(head, other.head)
        && Arrays.equals(chartConditions, other.chartConditions) && isActive == other.isActive;
  }
}
