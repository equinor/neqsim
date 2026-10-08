package neqsim.process.fielddevelopment.tieback.capacity;

import java.io.Serializable;

/**
 * Supplies the molar feed of a host process stream for a trial production load.
 *
 * <p>
 * Set on a {@link HostTieInPoint} to make the {@link TieInCapacityPlanner} change the composition of the tie-in stream
 * together with its rate. This captures the effect of a changing GOR, water cut or field mix on equipment capacity,
 * which a fixed composition scaled in rate cannot.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
@FunctionalInterface
public interface HostFeedProvider extends Serializable {

  /**
   * Returns the molar feed implied by a production load.
   *
   * @param load base-plus-satellite production load of the trial operating point
   * @return composition and molar rate on the component order of the host stream fluid
   */
  HostFeed getFeed(ProductionLoad load);
}
