package neqsim.process.materials;

/**
 * Data source abstraction for materials-review input.
 *
 * <p>
 * Implementations can wrap STID extracts, project technical database exports, line lists,
 * inspection files, or hand-authored JSON. The engine consumes the normalized
 * {@link MaterialsReviewInput} returned by this interface.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public interface MaterialsReviewDataSource {

  /**
   * Reads and normalizes review input.
   *
   * @return normalized materials review input
   */
  MaterialsReviewInput read();
}
