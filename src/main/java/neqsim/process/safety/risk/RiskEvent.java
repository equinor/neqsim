package neqsim.process.safety.risk;

import neqsim.process.safety.InitiatingEvent;
import neqsim.process.safety.ProcessSafetyScenario;

/**
 * Represents a risk event for probabilistic safety analysis.
 *
 * <p>
 * A risk event combines an initiating event with its frequency (events per year) and conditional
 * probability. Events can be linked in event trees to model escalation sequences.
 * </p>
 *
 * <p>
 * Frequencies are typically sourced from industry databases such as:
 * </p>
 * <ul>
 * <li>OREDA (Offshore Reliability Data)</li>
 * <li>HSE Hydrocarbon Release Database</li>
 * <li>CCPS Guidelines</li>
 * <li>OGP Risk Assessment Data Directory</li>
 * </ul>
 *
 * @author NeqSim team
 */
public class RiskEvent {

  private final String name;
  private final String description;
  private final InitiatingEvent initiatingEvent;
  private double frequency; // events per year
  private double conditionalProbability; // 0-1, given parent event occurred
  private RiskEvent parentEvent; // for event tree logic
  private ProcessSafetyScenario scenario;
  private ConsequenceCategory consequenceCategory;

  /**
   * Consequence severity categories per industry standards.
   */
  public enum ConsequenceCategory {
    /** No significant impact. */
    NEGLIGIBLE(1),
    /** Minor impact, no injuries. */
    MINOR(2),
    /** Localized impact, potential minor injuries. */
    MODERATE(3),
    /** Significant impact, potential serious injuries. */
    MAJOR(4),
    /** Catastrophic impact, potential fatalities. */
    CATASTROPHIC(5);

    private final int severity;

    ConsequenceCategory(int severity) {
      this.severity = severity;
    }

    public int getSeverity() {
      return severity;
    }
  }

  /**
   * Creates a new risk event.
   *
   * @param name unique identifier for the event
   * @param initiatingEvent the type of initiating event
   */
  public RiskEvent(String name, InitiatingEvent initiatingEvent) {
    this.name = name;
    this.description = "";
    this.initiatingEvent = initiatingEvent;
    this.frequency = 0.0;
    this.conditionalProbability = 1.0;
    this.consequenceCategory = ConsequenceCategory.MODERATE;
  }

  /**
   * Creates a new risk event with description.
   *
   * @param name unique identifier for the event
   * @param description detailed description of the event
   * @param initiatingEvent the type of initiating event
   */
  public RiskEvent(String name, String description, InitiatingEvent initiatingEvent) {
    this.name = name;
    this.description = description;
    this.initiatingEvent = initiatingEvent;
    this.frequency = 0.0;
    this.conditionalProbability = 1.0;
    this.consequenceCategory = ConsequenceCategory.MODERATE;
  }

  /**
   * Builder pattern for constructing risk events.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder class for RiskEvent.
   */
  public static class Builder {
    private String name = "Unnamed Event";
    private String description = "";
    private InitiatingEvent initiatingEvent = InitiatingEvent.LEAK_SMALL;
    private double frequency = 0.0;
    private double conditionalProbability = 1.0;
    private RiskEvent parentEvent = null;
    private ProcessSafetyScenario scenario = null;
    private ConsequenceCategory consequenceCategory = ConsequenceCategory.MODERATE;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder initiatingEvent(InitiatingEvent event) {
      this.initiatingEvent = event;
      return this;
    }

    /**
     * Sets the event frequency (events per year).
     *
     * @param frequency annual frequency
     * @return this builder
     */
    public Builder frequency(double frequency) {
      this.frequency = frequency;
      return this;
    }

    /**
     * Sets conditional probability given parent event.
     *
     * @param probability conditional probability (0-1)
     * @return this builder
     */
    public Builder conditionalProbability(double probability) {
      this.conditionalProbability = Math.max(0.0, Math.min(1.0, probability));
      return this;
    }

    public Builder parentEvent(RiskEvent parent) {
      this.parentEvent = parent;
      return this;
    }

    public Builder scenario(ProcessSafetyScenario scenario) {
      this.scenario = scenario;
      return this;
    }

    public Builder consequenceCategory(ConsequenceCategory category) {
      this.consequenceCategory = category;
      return this;
    }

    /**
     * Builds the RiskEvent instance.
     *
     * @return configured RiskEvent
     */
    public RiskEvent build() {
      RiskEvent event = new RiskEvent(name, description, initiatingEvent);
      event.frequency = this.frequency;
      event.conditionalProbability = this.conditionalProbability;
      event.parentEvent = this.parentEvent;
      event.scenario = this.scenario;
      event.consequenceCategory = this.consequenceCategory;
      return event;
    }
  }

  // Getters and setters

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public InitiatingEvent getInitiatingEvent() {
    return initiatingEvent;
  }

  public double getFrequency() {
    return frequency;
  }

  public void setFrequency(double frequency) {
    this.frequency = frequency;
  }

  public double getConditionalProbability() {
    return conditionalProbability;
  }

  public void setConditionalProbability(double probability) {
    this.conditionalProbability = Math.max(0.0, Math.min(1.0, probability));
  }

  public RiskEvent getParentEvent() {
    return parentEvent;
  }

  public void setParentEvent(RiskEvent parent) {
    this.parentEvent = parent;
  }

  public ProcessSafetyScenario getScenario() {
    return scenario;
  }

  public void setScenario(ProcessSafetyScenario scenario) {
    this.scenario = scenario;
  }

  public ConsequenceCategory getConsequenceCategory() {
    return consequenceCategory;
  }

  public void setConsequenceCategory(ConsequenceCategory category) {
    this.consequenceCategory = category;
  }

  /**
   * Calculates the absolute frequency considering parent event chain.
   *
   * <p>
   * For events in an event tree, the absolute frequency is the product of the initiating event
   * frequency and all conditional probabilities along the branch.
   * </p>
   *
   * @return absolute frequency (events per year)
   */
  public double getAbsoluteFrequency() {
    if (parentEvent == null) {
      return frequency;
    }
    return parentEvent.getAbsoluteFrequency() * conditionalProbability;
  }

  /**
   * Calculates the risk index (frequency × severity).
   *
   * @return risk index value
   */
  public double getRiskIndex() {
    return getAbsoluteFrequency() * consequenceCategory.getSeverity();
  }

  /**
   * Checks if this is a root/initiating event (no parent).
   *
   * @return true if this is an initiating event
   */
  public boolean isInitiatingEvent() {
    return parentEvent == null;
  }

  @Override
  public String toString() {
    return String.format("RiskEvent[%s, %s, freq=%.2e/yr, P=%.3f, %s]", name,
        initiatingEvent.name(), getAbsoluteFrequency(), conditionalProbability,
        consequenceCategory);
  }
}
