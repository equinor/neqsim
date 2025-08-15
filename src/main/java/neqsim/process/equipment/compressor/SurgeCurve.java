package neqsim.process.equipment.compressor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * SurgeCurve defines the compressor surge limit.
 */
public class SurgeCurve extends BoundaryCurve {
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SurgeCurve.class);

  /** Default constructor. */
  public SurgeCurve() {
    super();
  }

  /**
   * Create a surge curve from flow and head arrays.
   *
   * @param flow array of flow values
   * @param head array of head values
   */
  public SurgeCurve(double[] flow, double[] head) {
    super(flow, head);
  }

  /**
   * Get the surge flow for a given head.
   *
   * @param head head value
   * @return surge flow
   */
  public double getSurgeFlow(double head) {
    return getFlow(head);
  }

  /**
   * Check if the given point is in surge.
   *
   * @param head head value
   * @param flow flow value
   * @return true if the point is in surge
   */
  public boolean isSurge(double head, double flow) {
    return isLimit(head, flow);
  }

  @Override
  public boolean isLimit(double head, double flow) {
    return getFlow(head) > flow;
  }
}

