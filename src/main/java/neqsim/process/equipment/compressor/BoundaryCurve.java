package neqsim.process.equipment.compressor;

import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

/**
 * Abstract base implementation for compressor limit curves such as surge and stone wall curves.
 */
public abstract class BoundaryCurve implements BoundaryCurveInterface {
  private static final long serialVersionUID = 1000L;

  protected double[] flow;
  protected double[] head;
  protected double[] chartConditions;
  protected boolean isActive = false;

  protected WeightedObservedPoints flowFitter = new WeightedObservedPoints();
  protected PolynomialFunction flowFitterFunc = null;

  protected BoundaryCurve() {}

  protected BoundaryCurve(double[] flow, double[] head) {
    setCurve(null, flow, head);
  }

  @Override
  public void setCurve(double[] chartConditions, double[] flow, double[] head) {
    this.flow = flow;
    this.head = head;
    this.chartConditions = chartConditions;
    flowFitter = new WeightedObservedPoints();
    for (int i = 0; i < flow.length; i++) {
      flowFitter.add(head[i], flow[i]);
    }
    PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
    flowFitterFunc = new PolynomialFunction(fitter.fit(flowFitter.toList()));
    isActive = true;
  }

  @Override
  public double getFlow(double head) {
    return flowFitterFunc.value(head);
  }

  @Override
  public boolean isActive() {
    return isActive;
  }

  @Override
  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }

  /** Get flow values defining the curve. */
  public double[] getFlow() {
    return flow;
  }

  /** Get head values defining the curve. */
  public double[] getHead() {
    return head;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(chartConditions);
    result = prime * result + Arrays.hashCode(flow);
    result = prime * result + Arrays.hashCode(head);
    result = prime * result + Objects.hash(flowFitterFunc, isActive);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    BoundaryCurve other = (BoundaryCurve) obj;
    return Arrays.equals(chartConditions, other.chartConditions) && Arrays.equals(flow, other.flow)
        && Arrays.equals(head, other.head) && Objects.equals(flowFitterFunc, other.flowFitterFunc)
        && isActive == other.isActive;
  }
}

