package neqsim.process.synthesis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Result of {@link FlowsheetSynthesisEngine#proposeAndBuild(SeparationDuty)}.
 *
 * <p>
 * A proposal carries:
 * </p>
 * <ul>
 * <li>the recommended {@link ProcessSystem} (already built and ready to {@link ProcessSystem#run()
 * run}),</li>
 * <li>a human-readable rationale string explaining why this structure was chosen,</li>
 * <li>a discrete {@link Strategy} tag (single flash, two-stage flash, distillation, …),</li>
 * <li>per-product purity predictions keyed by component name,</li>
 * <li>an ordered list of alternatives that were considered but not selected, with the reason for
 * rejection.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class FlowsheetProposal implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** High-level structural choice made by the synthesis engine. */
  public enum Strategy {
    /** A single equilibrium flash drum. */
    SINGLE_FLASH,
    /** Two flash drums in series at different pressures (e.g. HP/LP). */
    TWO_STAGE_FLASH,
    /** A distillation column (with condenser and reboiler). */
    DISTILLATION,
    /** A stripping column (reboiler only, no condenser). */
    STRIPPER,
    /** No feasible separator found by the heuristics; the proposal is the trivial flash. */
    INFEASIBLE
  }

  private final Strategy strategy;
  private final String rationale;
  private final ProcessSystem processSystem;
  private final Map<String, Double> topProductPredicted;
  private final Map<String, Double> bottomProductPredicted;
  private final List<String> alternatives;
  private final boolean specsMet;

  /**
   * Creates a proposal.
   *
   * @param strategy the structural choice
   * @param rationale human-readable explanation
   * @param processSystem the assembled flowsheet
   * @param topProductPredicted predicted top-product mole fractions keyed by component
   * @param bottomProductPredicted predicted bottom-product mole fractions keyed by component
   * @param alternatives alternative proposals considered, with their rejection reason
   * @param specsMet true when all duty specs are predicted to be satisfied
   */
  public FlowsheetProposal(Strategy strategy, String rationale, ProcessSystem processSystem,
      Map<String, Double> topProductPredicted, Map<String, Double> bottomProductPredicted,
      List<String> alternatives, boolean specsMet) {
    this.strategy = strategy;
    this.rationale = rationale;
    this.processSystem = processSystem;
    this.topProductPredicted = topProductPredicted == null ? Collections.<String, Double>emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<String, Double>(topProductPredicted));
    this.bottomProductPredicted = bottomProductPredicted == null
        ? Collections.<String, Double>emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<String, Double>(bottomProductPredicted));
    this.alternatives = alternatives == null ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<String>(alternatives));
    this.specsMet = specsMet;
  }

  /**
   * Returns the strategy tag.
   *
   * @return non-null Strategy
   */
  public Strategy getStrategy() {
    return strategy;
  }

  /**
   * Returns the rationale.
   *
   * @return non-null human-readable string
   */
  public String getRationale() {
    return rationale;
  }

  /**
   * Returns the built flowsheet.
   *
   * @return the ProcessSystem (already populated; not yet run)
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Returns the predicted top-product mole fractions.
   *
   * @return unmodifiable map; never null
   */
  public Map<String, Double> getTopProductPredicted() {
    return topProductPredicted;
  }

  /**
   * Returns the predicted bottom-product mole fractions.
   *
   * @return unmodifiable map; never null
   */
  public Map<String, Double> getBottomProductPredicted() {
    return bottomProductPredicted;
  }

  /**
   * Returns the alternatives considered but rejected.
   *
   * @return unmodifiable list; never null
   */
  public List<String> getAlternatives() {
    return alternatives;
  }

  /**
   * Returns whether the predicted compositions satisfy every duty spec.
   *
   * @return true when all specs are met
   */
  public boolean isSpecsMet() {
    return specsMet;
  }

  /**
   * Returns a JSON description of this proposal — useful for agent handoff.
   *
   * @return a JsonObject with the proposal fields (no nested ProcessSystem)
   */
  public JsonObject toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", "1.0");
    root.addProperty("strategy", strategy.name());
    root.addProperty("rationale", rationale);
    root.addProperty("specsMet", specsMet);

    JsonObject top = new JsonObject();
    for (Map.Entry<String, Double> e : topProductPredicted.entrySet()) {
      top.addProperty(e.getKey(), e.getValue());
    }
    root.add("topProductPredicted", top);

    JsonObject bot = new JsonObject();
    for (Map.Entry<String, Double> e : bottomProductPredicted.entrySet()) {
      bot.addProperty(e.getKey(), e.getValue());
    }
    root.add("bottomProductPredicted", bot);

    JsonArray alts = new JsonArray();
    for (String s : alternatives) {
      alts.add(s);
    }
    root.add("alternatives", alts);
    return root;
  }
}
