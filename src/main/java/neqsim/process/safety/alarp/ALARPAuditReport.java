package neqsim.process.safety.alarp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ALARP (As Low As Reasonably Practicable) audit report builder.
 *
 * <p>
 * Captures the demonstration that residual risk has been reduced ALARP per UK HSE R2P2 and NORSOK
 * Z-013: each candidate risk-reduction measure is evaluated in terms of risk reduction
 * (averted-fatalities-per-year-equivalent), implementation cost and resulting cost-per-averted-
 * fatality (ICAF). A measure is rejected only if ICAF exceeds the disproportion-adjusted gross
 * threshold (typically £4–10 M / fatality, NOK 30–80 M).
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>UK HSE R2P2 — Reducing risks, protecting people (2001)</li>
 * <li>NORSOK Z-013 — Risk and emergency preparedness assessment</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ALARPAuditReport implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String studyName;
  private final List<MeasureRecord> measures = new ArrayList<>();
  private double valueOfStatisticalLifeNOK = 30.0e6;
  private double disproportionFactor = 3.0;

  /**
   * Construct an ALARP audit report.
   *
   * @param studyName study identifier
   */
  public ALARPAuditReport(String studyName) {
    this.studyName = studyName;
  }

  /**
   * Set the value of statistical life (VSL / VOSL) used to monetise averted fatalities.
   * Defaults to 30 MNOK.
   *
   * @param vslNOK VSL in NOK
   * @return this report for chaining
   */
  public ALARPAuditReport setValueOfStatisticalLife(double vslNOK) {
    this.valueOfStatisticalLifeNOK = vslNOK;
    return this;
  }

  /**
   * Set the disproportion factor (gross-disproportion multiplier, default 3).
   *
   * @param dispFactor disproportion factor
   * @return this report for chaining
   */
  public ALARPAuditReport setDisproportionFactor(double dispFactor) {
    this.disproportionFactor = dispFactor;
    return this;
  }

  /**
   * Add a candidate risk-reduction measure.
   *
   * @param description measure description
   * @param riskReductionPerYear averted fatality frequency per year
   * @param annualisedCostNOK annualised implementation cost in NOK/year
   * @return this report for chaining
   */
  public ALARPAuditReport addMeasure(String description, double riskReductionPerYear,
      double annualisedCostNOK) {
    if (riskReductionPerYear < 0.0 || annualisedCostNOK < 0.0) {
      throw new IllegalArgumentException("riskReduction and cost must be non-negative");
    }
    measures.add(new MeasureRecord(description, riskReductionPerYear, annualisedCostNOK));
    return this;
  }

  /**
   * Evaluate all measures and return their ICAF and ALARP verdict.
   *
   * @return list of evaluation records
   */
  public List<EvaluationResult> evaluate() {
    List<EvaluationResult> out = new ArrayList<>();
    double threshold = valueOfStatisticalLifeNOK * disproportionFactor;
    for (MeasureRecord m : measures) {
      double icaf = m.riskReductionPerYear == 0.0
          ? Double.POSITIVE_INFINITY
          : m.annualisedCostNOK / m.riskReductionPerYear;
      String verdict = icaf <= threshold ? "IMPLEMENT (cost-effective)" : "REJECT (exceeds GDF threshold)";
      out.add(new EvaluationResult(m.description, m.riskReductionPerYear, m.annualisedCostNOK,
          icaf, verdict));
    }
    return out;
  }

  /**
   * Build a multi-line text report.
   *
   * @return human-readable ALARP audit report
   */
  public String report() {
    StringBuilder sb = new StringBuilder();
    sb.append("ALARP audit: ").append(studyName).append('\n');
    sb.append(String.format("VSL = %.2e NOK   GDF = %.1f   threshold = %.2e NOK%n",
        valueOfStatisticalLifeNOK, disproportionFactor,
        valueOfStatisticalLifeNOK * disproportionFactor));
    sb.append("---------------------------------------------------\n");
    for (EvaluationResult r : evaluate()) {
      sb.append(String.format("ΔF=%.4e /yr  C=%.2e NOK/yr  ICAF=%.2e NOK/fatality%n",
          r.riskReductionPerYear, r.annualisedCostNOK, r.icaf));
      sb.append("    ").append(r.description).append('\n');
      sb.append("    Verdict: ").append(r.verdict).append('\n');
    }
    return sb.toString();
  }

  /** Internal record of a candidate measure. */
  private static class MeasureRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    final String description;
    final double riskReductionPerYear;
    final double annualisedCostNOK;

    MeasureRecord(String description, double riskReductionPerYear, double annualisedCostNOK) {
      this.description = description;
      this.riskReductionPerYear = riskReductionPerYear;
      this.annualisedCostNOK = annualisedCostNOK;
    }
  }

  /** Per-measure evaluation result. */
  public static class EvaluationResult implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Measure description. */
    public final String description;
    /** Risk reduction (averted fatalities per year). */
    public final double riskReductionPerYear;
    /** Annualised cost in NOK/year. */
    public final double annualisedCostNOK;
    /** Implied cost of averting a fatality. */
    public final double icaf;
    /** ALARP verdict text. */
    public final String verdict;

    EvaluationResult(String description, double riskReductionPerYear, double annualisedCostNOK,
        double icaf, String verdict) {
      this.description = description;
      this.riskReductionPerYear = riskReductionPerYear;
      this.annualisedCostNOK = annualisedCostNOK;
      this.icaf = icaf;
      this.verdict = verdict;
    }
  }
}
