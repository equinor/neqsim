package neqsim.process.fielddevelopment.lifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Evaluates and ranks executable field concepts on consistent lifecycle assumptions. */
public class FieldLifecycleEvaluator {
  /** Evaluates one field concept. */
  public FieldLifecycleResult evaluate(FieldLifecycleConcept concept) {
    return new FieldLifecycleSimulator().run(concept);
  }

  /**
   * Evaluates concepts and returns them ranked by after-tax NPV, highest first.
   *
   * @param concepts executable concepts; each must own an independent mutable model
   * @return ranked lifecycle results
   */
  public List<FieldLifecycleResult> evaluateAll(List<FieldLifecycleConcept> concepts) {
    List<FieldLifecycleResult> results = new ArrayList<FieldLifecycleResult>();
    for (FieldLifecycleConcept concept : concepts) {
      results.add(evaluate(concept));
    }
    Collections.sort(results, new Comparator<FieldLifecycleResult>() {
      @Override
      public int compare(FieldLifecycleResult first, FieldLifecycleResult second) {
        return Double.compare(second.getNpvMusd(), first.getNpvMusd());
      }
    });
    return results;
  }

  /** Creates a Markdown comparison table from ranked results. */
  public String toMarkdownTable(List<FieldLifecycleResult> results) {
    StringBuilder table = new StringBuilder();
    table.append("| Concept | NPV (MUSD) | IRR (%) | Break-even oil (USD/bbl) | Oil (MSm3) ");
    table.append("| Deferred oil (MSm3) | Peak facility utilization (%) | Gas injected (GSm3) | CO2 (kt) |\n");
    table.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    for (FieldLifecycleResult result : results) {
      table.append(result.toMarkdownRow()).append("\n");
    }
    return table.toString();
  }
}
