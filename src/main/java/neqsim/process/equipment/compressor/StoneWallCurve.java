package neqsim.process.equipment.compressor;

import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * StoneWallCurve class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class StoneWallCurve implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(StoneWallCurve.class);

  double[] flow;
  double[] head;
  double[] chartConditions = null;
  private boolean isActive = false;
  final WeightedObservedPoints flowFitter = new WeightedObservedPoints();
  PolynomialFunction flowFitterFunc = null;

  /**
   * <p>
   * Constructor for StoneWallCurve.
   * </p>
   */
  public StoneWallCurve() {
    // flow = new double[] {453.2, 600.0, 750.0};
    // head = new double[] {1000.0, 900.0, 800.0};
  }

  /**
   * <p>
   * Constructor for StoneWallCurve.
   * </p>
   *
   * @param flow an array of type double
   * @param head an array of type double
   */
  public StoneWallCurve(double[] flow, double[] head) {
    this.flow = flow;
    this.head = head;
  }

  /**
   * <p>
   * setCurve.
   * </p>
   *
   * @param chartConditions an array of type double
   * @param flow an array of type double
   * @param head an array of type double
   */
  public void setCurve(double[] chartConditions, double[] flow, double[] head) {
    this.chartConditions = chartConditions;
    for (int i = 0; i < flow.length; i++) {
      flowFitter.add(head[i], flow[i]);
    }
    PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
    flowFitterFunc = new PolynomialFunction(fitter.fit(flowFitter.toList()));
    isActive = true;
  }

  /**
   * <p>
   * getStoneWallFlow.
   * </p>
   *
   * @param head a double
   * @return a double
   */
  public double getStoneWallFlow(double head) {
    return flowFitterFunc.value(head);
  }

  /**
   * <p>
   * isStoneWall.
   * </p>
   *
   * @param head a double
   * @param flow a double
   * @return a boolean
   */
  public boolean isStoneWall(double head, double flow) {
    if (getStoneWallFlow(head) < flow) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Getter for property isActive.
   *
   * @return boolean
   */
  boolean isActive() {
    return isActive;
  }

  /**
   * Setter for property isActive.
   *
   * @param isActive true if stone wall curve should be used for compressor calculations
   */
  void setActive(boolean isActive) {
    this.isActive = isActive;
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
    // result = prime * result + Objects.hash(flowFitter);
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
    StoneWallCurve other = (StoneWallCurve) obj;
    return Arrays.equals(chartConditions, other.chartConditions) && Arrays.equals(flow, other.flow)
        && Objects.equals(flowFitterFunc, other.flowFitterFunc) && Arrays.equals(head, other.head)
        && isActive == other.isActive;
    // && Objects.equals(flowFitter, other.flowFitter)
  }
}
