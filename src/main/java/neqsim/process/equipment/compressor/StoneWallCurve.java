package neqsim.process.equipment.compressor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * StoneWallCurve defines the compressor stone wall (choke) limit.
 */
public class StoneWallCurve extends BoundaryCurve {
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(StoneWallCurve.class);

  /** Default constructor. */
  public StoneWallCurve() {
    super();
  }

  /**
   * Create a stone wall curve from flow and head arrays.
   *
   * @param flow array of flow values
   * @param head array of head values
   */
  public StoneWallCurve(double[] flow, double[] head) {
    super(flow, head);
  }

  /**
   * Get the stone wall flow for a given head.
   *
   * @param head head value
   * @return stone wall flow
   */
  public double getStoneWallFlow(double head) {
    return getFlow(head);
  }

  /**
   * Check if the given point is beyond the stone wall limit.
   *
   * @param head head value
   * @param flow flow value
   * @return true if the point is beyond the stone wall limit
   */
  public boolean isStoneWall(double head, double flow) {
    return isLimit(head, flow);
  }

  @Override
  public boolean isLimit(double head, double flow) {
    return getFlow(head) < flow;
  }
}

