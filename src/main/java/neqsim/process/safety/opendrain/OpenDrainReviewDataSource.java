package neqsim.process.safety.opendrain;

/**
 * Source adapter for normalized open-drain review inputs.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public interface OpenDrainReviewDataSource {

  /**
   * Reads normalized review input.
   *
   * @return open-drain review input
   */
  OpenDrainReviewInput read();
}
