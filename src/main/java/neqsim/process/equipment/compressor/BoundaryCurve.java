package neqsim.process.equipment.compressor;

import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

/**
 * Abstract base implementation for compressor limit curves such as surge and stone wall curves.
 *
 * @author esol
 */
public abstract class BoundaryCurve implements BoundaryCurveInterface {
  private static final long serialVersionUID = 1000L;

  protected double[] flow;
  protected double[] head;
  protected double[] chartConditions;
  protected boolean isActive = false;

  protected WeightedObservedPoints flowFitter = new WeightedObservedPoints();
  protected PolynomialFunction flowFitterFunc = null;

  /**
   * <p>
   * Constructor for BoundaryCurve.
   * </p>
   */
  protected BoundaryCurve() {}

  /**
   * <p>
   * Constructor for BoundaryCurve.
   * </p>
   *
   * @param flow an array of double objects
   * @param head an array of double objects
   */
  protected BoundaryCurve(double[] flow, double[] head) {
    setCurve(null, flow, head);
  }

  /** {@inheritDoc} */
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

  /**
   * Get flow values defining the curve.
   *
   * @return an array of double objects
   */
  public double[] getFlow() {
    return flow;
  }

  /** {@inheritDoc} */
  @Override
  public double getFlow(double head) {
    return flowFitterFunc.value(head);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isActive() {
    return isActive;
  }

  /** {@inheritDoc} */
  @Override
  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }



  /**
   * Get head values defining the curve.
   *
   * @return an array of double objects
   */
  public double[] getHead() {
    return head;
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
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

