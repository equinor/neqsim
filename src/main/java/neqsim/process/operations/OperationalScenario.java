package neqsim.process.operations;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered set of plant-agnostic operational actions for a NeqSim process model.
 *
 * <p>
 * Use this class to represent P&amp;ID-driven changes such as closing a valve, changing a controller
 * output, applying live boundary data, and then running steady-state or dynamic calculations.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperationalScenario implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String name;
  private final List<OperationalAction> actions;

  /**
   * Creates a scenario from a builder.
   *
   * @param builder scenario builder
   */
  private OperationalScenario(Builder builder) {
    name = cleanRequired(builder.name, "name");
    actions = Collections.unmodifiableList(new ArrayList<OperationalAction>(builder.actions));
  }

  /**
   * Starts a scenario builder.
   *
   * @param name scenario name
   * @return builder instance
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Returns the scenario name.
   *
   * @return scenario name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns ordered actions.
   *
   * @return unmodifiable action list
   */
  public List<OperationalAction> getActions() {
    return actions;
  }

  /** Builder for {@link OperationalScenario}. */
  public static final class Builder {
    private final String name;
    private final List<OperationalAction> actions = new ArrayList<OperationalAction>();

    /**
     * Creates a builder.
     *
     * @param name scenario name
     */
    private Builder(String name) {
      this.name = name;
    }

    /**
     * Adds an action to the scenario.
     *
     * @param action action to add
     * @return this builder
     */
    public Builder addAction(OperationalAction action) {
      if (action == null) {
        throw new IllegalArgumentException("action must not be null");
      }
      actions.add(action);
      return this;
    }

    /**
     * Builds the immutable scenario.
     *
     * @return operational scenario
     */
    public OperationalScenario build() {
      return new OperationalScenario(this);
    }
  }

  /**
   * Requires a non-empty text value.
   *
   * @param text text to validate
   * @param fieldName field name used in error messages
   * @return trimmed text
   * @throws IllegalArgumentException if the text is null or empty
   */
  private static String cleanRequired(String text, String fieldName) {
    String value = text == null ? "" : text.trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be empty");
    }
    return value;
  }
}