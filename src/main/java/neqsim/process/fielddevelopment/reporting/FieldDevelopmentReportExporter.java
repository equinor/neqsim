package neqsim.process.fielddevelopment.reporting;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.fielddevelopment.concept.DevelopmentCaseTemplate;
import neqsim.process.fielddevelopment.economics.SensitivityAnalyzer;
import neqsim.process.fielddevelopment.evaluation.ConceptKPIs;
import neqsim.process.fielddevelopment.tieback.TiebackOption;
import neqsim.process.fielddevelopment.tieback.TiebackReport;

/**
 * Exports standard field-development comparison tables for books and reports.
 *
 * @author ESOL
 * @version 1.0
 */
public class FieldDevelopmentReportExporter {

  /**
   * Creates a report exporter.
   */
  public FieldDevelopmentReportExporter() {
    // Default constructor
  }

  /**
   * Builds a tieback-option comparison table.
   *
   * @param report tieback report to export
   * @return markdown comparison table
   */
  public String exportTiebackOptionsMarkdown(TiebackReport report) {
    StringBuilder table = new StringBuilder();
    table.append(
        "| Host | Route | Distance km | Installed km | CAPEX MUSD | NPV MUSD | Feasible |\n");
    table.append(
        "|------|-------|-------------|--------------|------------|----------|----------|\n");
    if (report == null) {
      return table.toString();
    }
    for (TiebackOption option : report.getOptions()) {
      table.append(String.format("| %s | %s | %.1f | %.1f | %.0f | %.0f | %s |%n",
          option.getHostName(), emptyAsDash(option.getRouteNetworkName()), option.getDistanceKm(),
          option.getRouteInstalledLengthKm() > 0.0 ? option.getRouteInstalledLengthKm()
              : option.getDistanceKm(),
          option.getTotalCapexMusd(), option.getNpvMusd(), option.isFeasible() ? "Yes" : "No"));
    }
    return table.toString();
  }

  /**
   * Builds a template-comparison table.
   *
   * @param templates development templates to compare
   * @return markdown comparison table
   */
  public String exportTemplateComparisonMarkdown(List<DevelopmentCaseTemplate> templates) {
    StringBuilder table = new StringBuilder();
    table.append(
        "| Case | Type | CAPEX MUSD | NPV MUSD | Power MW | Lifecycle CO2 kt | P50 resource |\n");
    table.append(
        "|------|------|------------|----------|----------|------------------|--------------|\n");
    if (templates == null) {
      return table.toString();
    }
    for (DevelopmentCaseTemplate template : templates) {
      double lifecycleKt =
          template.getLifecycleEmissionsProfile().getTotalLifecycleEmissionsTonnes() / 1000.0;
      table.append(String.format("| %s | %s | %.0f | %.0f | %.1f | %.1f | %.2f %s |%n",
          template.getCaseName(), template.getCaseType(), template.getTotalCapexMusd(),
          template.getEconomics().getNpv(), template.getPowerMw(), lifecycleKt,
          template.getUncertainty().getResource().getP50(),
          template.getUncertainty().getResource().getUnit()));
    }
    return table.toString();
  }

  /**
   * Builds a tornado sensitivity table.
   *
   * @param tornado tornado result to export
   * @return markdown table
   */
  public String exportTornadoMarkdown(SensitivityAnalyzer.TornadoResult tornado) {
    StringBuilder table = new StringBuilder();
    table.append("| Parameter | Low NPV MUSD | High NPV MUSD | Swing MUSD | Impact |\n");
    table.append("|-----------|--------------|---------------|------------|--------|\n");
    if (tornado == null) {
      return table.toString();
    }
    for (SensitivityAnalyzer.TornadoItem item : tornado.getItems()) {
      table.append(String.format("| %s | %.1f | %.1f | %.1f | %s |%n", item.getParameterName(),
          item.getLowNpv(), item.getHighNpv(), item.getSwing(), item.getImpactLevel()));
    }
    return table.toString();
  }

  /**
   * Builds a KPI comparison table from concept evaluations.
   *
   * @param kpis concept KPI results
   * @return markdown table
   */
  public String exportConceptKpisMarkdown(List<ConceptKPIs> kpis) {
    StringBuilder table = new StringBuilder();
    table.append("| Concept | CAPEX MUSD | NPV10 MUSD | CO2 kg/boe | Flow assurance | Score |\n");
    table.append("|---------|------------|------------|------------|----------------|-------|\n");
    if (kpis == null) {
      return table.toString();
    }
    for (ConceptKPIs item : kpis) {
      table.append(String.format("| %s | %.0f | %.0f | %.1f | %s | %.2f |%n", item.getConceptName(),
          item.getTotalCapexMUSD(), item.getNpv10MUSD(), item.getCo2IntensityKgPerBoe(),
          item.getFlowAssuranceOverall().getDisplayName(), item.getOverallScore()));
    }
    return table.toString();
  }

  /**
   * Builds figure-ready NPV data for a concept comparison bar chart.
   *
   * @param templates development templates to convert
   * @return list of rows with case name and NPV value
   */
  public List<String[]> exportTemplateNpvFigureData(List<DevelopmentCaseTemplate> templates) {
    List<String[]> rows = new ArrayList<String[]>();
    if (templates == null) {
      return rows;
    }
    for (DevelopmentCaseTemplate template : templates) {
      rows.add(new String[] {template.getCaseName(),
          String.format("%.3f", template.getEconomics().getNpv())});
    }
    return rows;
  }

  /**
   * Converts blank text to a dash for compact tables.
   *
   * @param text source text
   * @return text or dash when blank
   */
  private String emptyAsDash(String text) {
    return text == null || text.trim().isEmpty() ? "-" : text;
  }
}
