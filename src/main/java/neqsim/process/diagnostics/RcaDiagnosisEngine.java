package neqsim.process.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic hypothesis-table reasoner for evidence-grounded process root-cause analysis.
 *
 * <p>
 * The engine deliberately separates evidence calculation from reasoning. It can be used as a fully reproducible
 * baseline or as a guardrail around an optional language-model agent that consumes {@link RcaDiagnosis#toJson()}.
 * Ranking is a weighted average of rule support and does not require faulty training data.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RcaDiagnosisEngine {
  /**
   * Diagnoses a process window.
   *
   * @param normalModel model fitted only to normal operation
   * @param window test window
   * @param hypotheses physical fault hypotheses, including normal operation if desired
   * @return ranked diagnosis
   */
  public RcaDiagnosis diagnose(RcaNormalOperationModel normalModel, RcaProcessWindow window,
      List<RcaFaultHypothesis> hypotheses) {
    if (normalModel == null) {
      throw new IllegalArgumentException("normalModel must not be null");
    }
    if (hypotheses == null || hypotheses.isEmpty()) {
      throw new IllegalArgumentException("hypotheses must not be empty");
    }

    RcaEvidence evidence = normalModel.analyze(window);
    List<RcaDiagnosis.RankedHypothesis> ranked = new ArrayList<RcaDiagnosis.RankedHypothesis>();
    for (RcaFaultHypothesis hypothesis : hypotheses) {
      if (hypothesis == null) {
        throw new IllegalArgumentException("hypotheses must not contain null");
      }
      double weightedScore = 0.0;
      double totalWeight = 0.0;
      List<RcaDiagnosis.RuleTrace> traces = new ArrayList<RcaDiagnosis.RuleTrace>();
      for (RcaFaultHypothesis.EvidenceRule rule : hypothesis.getRules()) {
        RcaFaultHypothesis.RuleEvaluation evaluation = rule.evaluate(evidence);
        weightedScore += evaluation.support * rule.getWeight();
        totalWeight += rule.getWeight();
        traces.add(new RcaDiagnosis.RuleTrace(rule, evaluation.value, evaluation.support));
      }
      ranked.add(new RcaDiagnosis.RankedHypothesis(hypothesis.getName(), hypothesis.getDescription(),
          weightedScore / totalWeight, traces));
    }

    Collections.sort(ranked, new Comparator<RcaDiagnosis.RankedHypothesis>() {
      @Override
      public int compare(RcaDiagnosis.RankedHypothesis first, RcaDiagnosis.RankedHypothesis second) {
        int scoreComparison = Double.compare(second.getScore(), first.getScore());
        return scoreComparison != 0 ? scoreComparison : first.getName().compareTo(second.getName());
      }
    });
    return new RcaDiagnosis(evidence, ranked);
  }
}
