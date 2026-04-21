package neqsim.process.diagnostics;

import java.io.Serializable;

/**
 * Abstract base class for trip root cause hypotheses.
 *
 * <p>
 * Each hypothesis represents a specific failure mode that could have caused a trip. The hypothesis
 * is evaluated against the evidence captured in the {@link ProcessStateSnapshot} and
 * {@link UnifiedEventTimeline} to produce a {@link HypothesisResult} with a confidence score.
 * </p>
 *
 * <p>
 * Subclasses implement the {@link #evaluate(ProcessStateSnapshot, UnifiedEventTimeline)} method to
 * check for specific patterns in the process data.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public abstract class TripHypothesis implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final String description;
  private final TripType applicableTripType;

  /**
   * Constructs a hypothesis with a name and description.
   *
   * @param name short hypothesis name (e.g. "Hydrate Formation")
   * @param description longer explanation of the failure mode
   * @param applicableTripType the trip type this hypothesis is most relevant to, or null for any
   */
  protected TripHypothesis(String name, String description, TripType applicableTripType) {
    this.name = name;
    this.description = description;
    this.applicableTripType = applicableTripType;
  }

  /**
   * Evaluates this hypothesis against the evidence.
   *
   * <p>
   * Implementations should examine the state differences in the snapshot and the event sequence in
   * the timeline to determine how likely this failure mode caused the trip.
   * </p>
   *
   * @param snapshot the process state snapshot comparing last good state to trip state
   * @param timeline the unified event timeline with alarm and state change history
   * @return evaluation result with confidence score and supporting evidence
   */
  public abstract HypothesisResult evaluate(ProcessStateSnapshot snapshot,
      UnifiedEventTimeline timeline);

  /**
   * Returns whether this hypothesis is applicable to the given trip type.
   *
   * <p>
   * A hypothesis with {@code applicableTripType == null} is applicable to all trip types.
   * </p>
   *
   * @param tripType the trip type to check
   * @return true if this hypothesis should be evaluated for the given trip type
   */
  public boolean isApplicableTo(TripType tripType) {
    return applicableTripType == null || applicableTripType == tripType;
  }

  /**
   * Returns the hypothesis name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the hypothesis description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the trip type this hypothesis is most relevant to.
   *
   * @return applicable trip type, or null if applicable to all
   */
  public TripType getApplicableTripType() {
    return applicableTripType;
  }

  @Override
  public String toString() {
    return name;
  }
}
