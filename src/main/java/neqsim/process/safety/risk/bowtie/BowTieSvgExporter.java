package neqsim.process.safety.risk.bowtie;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports BowTie models to SVG format for visualization.
 *
 * <p>
 * This class generates SVG (Scalable Vector Graphics) representations of bow-tie diagrams for use
 * in reports, presentations, and web applications. The SVG output follows standard bow-tie
 * visualization conventions with threats on the left, the hazard in the center, and consequences on
 * the right.
 * </p>
 *
 * <h2>SVG Structure</h2>
 * <ul>
 * <li>Left side: Threats with prevention barriers</li>
 * <li>Center: Hazard (top event)</li>
 * <li>Right side: Consequences with mitigation barriers</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * BowTieModel model = new BowTieModel("HAZARD-001", "Vessel Rupture");
 * // ... configure model ...
 * 
 * BowTieSvgExporter exporter = new BowTieSvgExporter(model);
 * String svg = exporter.export();
 * Files.writeString(Path.of("bowtie.svg"), svg);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @since 3.3.0
 */
public class BowTieSvgExporter implements Serializable {
  private static final long serialVersionUID = 1000L;

  // Layout constants
  private static final int CANVAS_WIDTH = 1200;
  private static final int CANVAS_HEIGHT = 800;
  private static final int THREAT_X = 50;
  private static final int HAZARD_X = 550;
  private static final int CONSEQUENCE_X = 850;
  private static final int BOX_WIDTH = 120;
  private static final int BOX_HEIGHT = 50;
  private static final int BARRIER_WIDTH = 80;
  private static final int BARRIER_HEIGHT = 30;
  private static final int VERTICAL_SPACING = 80;

  // Colors
  private static final String COLOR_THREAT = "#ff6b6b";
  private static final String COLOR_HAZARD = "#ffd93d";
  private static final String COLOR_CONSEQUENCE = "#6bcb77";
  private static final String COLOR_PREVENTION = "#4d96ff";
  private static final String COLOR_MITIGATION = "#9d4edd";
  private static final String COLOR_LINE = "#333333";
  private static final String COLOR_TEXT = "#000000";

  private BowTieModel model;
  private int width;
  private int height;

  /**
   * Creates an SVG exporter for the specified bow-tie model.
   *
   * @param model bow-tie model to export
   */
  public BowTieSvgExporter(BowTieModel model) {
    this.model = model;
    this.width = CANVAS_WIDTH;
    this.height = CANVAS_HEIGHT;
  }

  /**
   * Creates an SVG exporter with custom dimensions.
   *
   * @param model bow-tie model to export
   * @param width canvas width in pixels
   * @param height canvas height in pixels
   */
  public BowTieSvgExporter(BowTieModel model, int width, int height) {
    this.model = model;
    this.width = width;
    this.height = height;
  }

  /**
   * Exports the bow-tie model to SVG format.
   *
   * @return SVG string
   */
  public String export() {
    // Ensure risk calculation is done
    model.calculateRisk();

    StringBuilder svg = new StringBuilder();
    svg.append(getSvgHeader());
    svg.append(getStyles());
    svg.append(getTitle());
    svg.append(getThreats());
    svg.append(getHazard());
    svg.append(getConsequences());
    svg.append(getConnectors());
    svg.append(getLegend());
    svg.append(getStatistics());
    svg.append(getSvgFooter());

    return svg.toString();
  }

  /**
   * Gets SVG header.
   *
   * @return SVG header string
   */
  private String getSvgHeader() {
    return String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>%n"
        + "<svg xmlns=\"http://www.w3.org/2000/svg\" "
        + "width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">%n", width, height, width, height);
  }

  /**
   * Gets SVG footer.
   *
   * @return SVG footer string
   */
  private String getSvgFooter() {
    return "</svg>\n";
  }

  /**
   * Gets CSS styles for the SVG.
   *
   * @return style definitions
   */
  private String getStyles() {
    return "<defs>\n" + "  <style>\n" + "    .title { font: bold 18px sans-serif; fill: "
        + COLOR_TEXT + "; }\n" + "    .subtitle { font: 12px sans-serif; fill: #666666; }\n"
        + "    .threat-box { fill: " + COLOR_THREAT + "; stroke: #cc5555; stroke-width: 2; }\n"
        + "    .hazard-box { fill: " + COLOR_HAZARD + "; stroke: #ccaa00; stroke-width: 3; }\n"
        + "    .consequence-box { fill: " + COLOR_CONSEQUENCE
        + "; stroke: #4a9f5a; stroke-width: 2; }\n" + "    .prevention-barrier { fill: "
        + COLOR_PREVENTION + "; stroke: #3a7acc; stroke-width: 1; }\n"
        + "    .mitigation-barrier { fill: " + COLOR_MITIGATION
        + "; stroke: #7a3ab0; stroke-width: 1; }\n"
        + "    .box-text { font: 11px sans-serif; fill: white; text-anchor: middle; }\n"
        + "    .barrier-text { font: 9px sans-serif; fill: white; text-anchor: middle; }\n"
        + "    .connector { stroke: " + COLOR_LINE + "; stroke-width: 2; fill: none; }\n"
        + "    .stats { font: 10px monospace; fill: #333333; }\n"
        + "    .legend-text { font: 10px sans-serif; fill: " + COLOR_TEXT + "; }\n" + "  </style>\n"
        + "</defs>\n";
  }

  /**
   * Gets the title section.
   *
   * @return title SVG elements
   */
  private String getTitle() {
    return String.format(
        "<text x=\"%d\" y=\"30\" class=\"title\">Bow-Tie: %s</text>%n"
            + "<text x=\"%d\" y=\"50\" class=\"subtitle\">%s</text>%n",
        width / 2, escapeXml(model.getHazardId()), width / 2,
        escapeXml(model.getHazardDescription()));
  }

  /**
   * Gets threat elements.
   *
   * @return threat SVG elements
   */
  private String getThreats() {
    StringBuilder sb = new StringBuilder();
    sb.append("<!-- Threats -->\n");

    List<BowTieModel.Threat> threats = model.getThreats();
    int startY = 100;
    int spacing = Math.min(VERTICAL_SPACING, (height - 200) / Math.max(threats.size(), 1));

    for (int i = 0; i < threats.size(); i++) {
      BowTieModel.Threat threat = threats.get(i);
      int y = startY + i * spacing;
      double freq = threat.getFrequency();

      // Threat box
      sb.append(String.format(
          "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"threat-box\" rx=\"5\"/>%n",
          THREAT_X, y, BOX_WIDTH, BOX_HEIGHT));
      sb.append(String.format("<text x=\"%d\" y=\"%d\" class=\"box-text\">%s</text>%n",
          THREAT_X + BOX_WIDTH / 2, y + 20, truncate(threat.getDescription(), 15)));
      sb.append(String.format("<text x=\"%d\" y=\"%d\" class=\"box-text\">%.2e /yr</text>%n",
          THREAT_X + BOX_WIDTH / 2, y + 35, freq));

      // Prevention barriers linked to this threat
      List<BowTieModel.Barrier> barriers = getBarriersForThreat(threat);
      int barrierX = THREAT_X + BOX_WIDTH + 30;
      for (int j = 0; j < barriers.size() && j < 3; j++) {
        BowTieModel.Barrier barrier = barriers.get(j);
        double eff = barrier.getEffectiveness();
        int bx = barrierX + j * (BARRIER_WIDTH + 10);
        int by = y + (BOX_HEIGHT - BARRIER_HEIGHT) / 2;

        sb.append(String.format(
            "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"prevention-barrier\" rx=\"3\"/>%n",
            bx, by, BARRIER_WIDTH, BARRIER_HEIGHT));
        sb.append(String.format("<text x=\"%d\" y=\"%d\" class=\"barrier-text\">%s</text>%n",
            bx + BARRIER_WIDTH / 2, by + 12, truncate(barrier.getDescription(), 12)));
        sb.append(String.format("<text x=\"%d\" y=\"%d\" class=\"barrier-text\">%.0f%%</text>%n",
            bx + BARRIER_WIDTH / 2, by + 24, eff * 100));
      }
    }

    return sb.toString();
  }

  /**
   * Gets barriers linked to a specific threat.
   *
   * @param threat the threat
   * @return list of barriers linked to the threat
   */
  private List<BowTieModel.Barrier> getBarriersForThreat(BowTieModel.Threat threat) {
    List<BowTieModel.Barrier> result = new ArrayList<>();
    List<String> linkedIds = threat.getLinkedBarrierIds();
    for (BowTieModel.Barrier barrier : model.getBarriers()) {
      if (linkedIds.contains(barrier.getId())
          && (barrier.getBarrierType() == BowTieModel.BarrierType.PREVENTION
              || barrier.getBarrierType() == BowTieModel.BarrierType.BOTH)) {
        result.add(barrier);
      }
    }
    return result;
  }

  /**
   * Gets barriers linked to a specific consequence.
   *
   * @param consequence the consequence
   * @return list of barriers linked to the consequence
   */
  private List<BowTieModel.Barrier> getBarriersForConsequence(BowTieModel.Consequence consequence) {
    List<BowTieModel.Barrier> result = new ArrayList<>();
    List<String> linkedIds = consequence.getLinkedBarrierIds();
    for (BowTieModel.Barrier barrier : model.getBarriers()) {
      if (linkedIds.contains(barrier.getId())
          && (barrier.getBarrierType() == BowTieModel.BarrierType.MITIGATION
              || barrier.getBarrierType() == BowTieModel.BarrierType.BOTH)) {
        result.add(barrier);
      }
    }
    return result;
  }

  /**
   * Gets hazard element.
   *
   * @return hazard SVG element
   */
  private String getHazard() {
    int hazardY = height / 2 - BOX_HEIGHT;
    int hazardWidth = 150;
    int hazardHeight = 80;

    return String.format("<!-- Hazard -->%n"
        + "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"hazard-box\" rx=\"10\"/>%n"
        + "<text x=\"%d\" y=\"%d\" class=\"title\" style=\"fill: #333;\">HAZARD</text>%n"
        + "<text x=\"%d\" y=\"%d\" class=\"box-text\" style=\"fill: #333;\">%s</text>%n", HAZARD_X,
        hazardY, hazardWidth, hazardHeight, HAZARD_X + hazardWidth / 2, hazardY + 30,
        HAZARD_X + hazardWidth / 2, hazardY + 55, truncate(model.getHazardDescription(), 20));
  }

  /**
   * Gets consequence elements.
   *
   * @return consequence SVG elements
   */
  private String getConsequences() {
    StringBuilder sb = new StringBuilder();
    sb.append("<!-- Consequences -->\n");

    List<BowTieModel.Consequence> consequences = model.getConsequences();
    int startY = 100;
    int spacing = Math.min(VERTICAL_SPACING, (height - 200) / Math.max(consequences.size(), 1));

    for (int i = 0; i < consequences.size(); i++) {
      BowTieModel.Consequence consequence = consequences.get(i);
      int y = startY + i * spacing;
      int severity = consequence.getSeverity();

      // Mitigation barriers (before consequence)
      List<BowTieModel.Barrier> barriers = getBarriersForConsequence(consequence);
      int barrierX =
          CONSEQUENCE_X - (barriers.size() > 0 ? barriers.size() * (BARRIER_WIDTH + 10) : 0);
      for (int j = 0; j < barriers.size() && j < 3; j++) {
        BowTieModel.Barrier barrier = barriers.get(j);
        double eff = barrier.getEffectiveness();
        int bx = barrierX + j * (BARRIER_WIDTH + 10);
        int by = y + (BOX_HEIGHT - BARRIER_HEIGHT) / 2;

        sb.append(String.format(
            "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"mitigation-barrier\" rx=\"3\"/>%n",
            bx, by, BARRIER_WIDTH, BARRIER_HEIGHT));
        sb.append(String.format("<text x=\"%d\" y=\"%d\" class=\"barrier-text\">%s</text>%n",
            bx + BARRIER_WIDTH / 2, by + 12, truncate(barrier.getDescription(), 12)));
        sb.append(String.format("<text x=\"%d\" y=\"%d\" class=\"barrier-text\">%.0f%%</text>%n",
            bx + BARRIER_WIDTH / 2, by + 24, eff * 100));
      }

      // Consequence box
      int consX = CONSEQUENCE_X + 50;
      sb.append(String.format(
          "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" class=\"consequence-box\" rx=\"5\"/>%n",
          consX, y, BOX_WIDTH, BOX_HEIGHT));
      sb.append(String.format("<text x=\"%d\" y=\"%d\" class=\"box-text\">%s</text>%n",
          consX + BOX_WIDTH / 2, y + 20, truncate(consequence.getDescription(), 15)));
      sb.append(String.format("<text x=\"%d\" y=\"%d\" class=\"box-text\">Sev: %d</text>%n",
          consX + BOX_WIDTH / 2, y + 35, severity));
    }

    return sb.toString();
  }

  /**
   * Gets connector lines.
   *
   * @return connector SVG elements
   */
  private String getConnectors() {
    StringBuilder sb = new StringBuilder();
    sb.append("<!-- Connectors -->\n");

    int hazardY = height / 2 - BOX_HEIGHT / 2;
    int hazardLeft = HAZARD_X;
    int hazardRight = HAZARD_X + 150;

    // Connect threats to hazard
    List<BowTieModel.Threat> threats = model.getThreats();
    int startY = 100 + BOX_HEIGHT / 2;
    int spacing = Math.min(VERTICAL_SPACING, (height - 200) / Math.max(threats.size(), 1));
    for (int i = 0; i < threats.size(); i++) {
      int y = startY + i * spacing;
      int startX = THREAT_X + BOX_WIDTH;
      List<BowTieModel.Barrier> barriers = getBarriersForThreat(threats.get(i));
      if (!barriers.isEmpty()) {
        startX = THREAT_X + BOX_WIDTH + 30 + Math.min(barriers.size(), 3) * (BARRIER_WIDTH + 10);
      }
      sb.append(String.format("<path d=\"M %d %d Q %d %d %d %d\" class=\"connector\"/>%n", startX,
          y, (startX + hazardLeft) / 2, (y + hazardY) / 2, hazardLeft, hazardY));
    }

    // Connect hazard to consequences
    List<BowTieModel.Consequence> consequences = model.getConsequences();
    for (int i = 0; i < consequences.size(); i++) {
      int y = startY + i * spacing;
      int endX = CONSEQUENCE_X + 50;
      List<BowTieModel.Barrier> barriers = getBarriersForConsequence(consequences.get(i));
      if (!barriers.isEmpty()) {
        endX = CONSEQUENCE_X - Math.min(barriers.size(), 3) * (BARRIER_WIDTH + 10);
      }
      sb.append(String.format("<path d=\"M %d %d Q %d %d %d %d\" class=\"connector\"/>%n",
          hazardRight, hazardY, (hazardRight + endX) / 2, (hazardY + y) / 2, endX, y));
    }

    return sb.toString();
  }

  /**
   * Gets legend.
   *
   * @return legend SVG elements
   */
  private String getLegend() {
    int legendY = height - 80;
    return String.format(
        "<!-- Legend -->%n"
            + "<rect x=\"20\" y=\"%d\" width=\"15\" height=\"15\" class=\"threat-box\"/>%n"
            + "<text x=\"40\" y=\"%d\" class=\"legend-text\">Threat</text>%n"
            + "<rect x=\"100\" y=\"%d\" width=\"15\" height=\"15\" class=\"prevention-barrier\"/>%n"
            + "<text x=\"120\" y=\"%d\" class=\"legend-text\">Prevention Barrier</text>%n"
            + "<rect x=\"240\" y=\"%d\" width=\"15\" height=\"15\" class=\"hazard-box\"/>%n"
            + "<text x=\"260\" y=\"%d\" class=\"legend-text\">Hazard</text>%n"
            + "<rect x=\"320\" y=\"%d\" width=\"15\" height=\"15\" class=\"mitigation-barrier\"/>%n"
            + "<text x=\"340\" y=\"%d\" class=\"legend-text\">Mitigation Barrier</text>%n"
            + "<rect x=\"470\" y=\"%d\" width=\"15\" height=\"15\" class=\"consequence-box\"/>%n"
            + "<text x=\"490\" y=\"%d\" class=\"legend-text\">Consequence</text>%n",
        legendY, legendY + 12, legendY, legendY + 12, legendY, legendY + 12, legendY, legendY + 12,
        legendY, legendY + 12);
  }

  /**
   * Gets statistics section.
   *
   * @return statistics SVG elements
   */
  private String getStatistics() {
    int statsY = height - 50;
    double unmitigated = model.getUnmitigatedFrequency();
    double mitigated = model.getMitigatedFrequency();
    double riskReduction = mitigated > 0 ? unmitigated / mitigated : 0;
    return String.format(
        "<!-- Statistics -->%n"
            + "<text x=\"20\" y=\"%d\" class=\"stats\">Unmitigated Freq: %.4f /yr | "
            + "Mitigated Freq: %.6f /yr | " + "Risk Reduction: %.0fx</text>%n",
        statsY, unmitigated, mitigated, riskReduction);
  }

  /**
   * Escapes XML special characters.
   *
   * @param text text to escape
   * @return escaped text
   */
  private String escapeXml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;");
  }

  /**
   * Truncates text to specified length.
   *
   * @param text text to truncate
   * @param maxLen maximum length
   * @return truncated text
   */
  private String truncate(String text, int maxLen) {
    if (text == null) {
      return "";
    }
    text = escapeXml(text);
    if (text.length() <= maxLen) {
      return text;
    }
    return text.substring(0, maxLen - 2) + "..";
  }

  /**
   * Exports the bow-tie model to HTML with embedded SVG.
   *
   * @return HTML string
   */
  public String exportToHtml() {
    String svg = export();
    return "<!DOCTYPE html>\n" + "<html>\n" + "<head>\n" + "  <title>Bow-Tie: "
        + escapeXml(model.getHazardId()) + "</title>\n" + "  <style>\n"
        + "    body { font-family: sans-serif; margin: 20px; }\n"
        + "    .container { max-width: 1200px; margin: 0 auto; }\n" + "    h1 { color: #333; }\n"
        + "    .svg-container { border: 1px solid #ccc; border-radius: 8px; padding: 10px; }\n"
        + "  </style>\n" + "</head>\n" + "<body>\n" + "  <div class=\"container\">\n"
        + "    <h1>Bow-Tie Analysis: " + escapeXml(model.getHazardId()) + "</h1>\n"
        + "    <div class=\"svg-container\">\n" + svg + "    </div>\n" + "  </div>\n" + "</body>\n"
        + "</html>\n";
  }

  /**
   * Gets the canvas width.
   *
   * @return width in pixels
   */
  public int getWidth() {
    return width;
  }

  /**
   * Sets the canvas width.
   *
   * @param width width in pixels
   */
  public void setWidth(int width) {
    this.width = width;
  }

  /**
   * Gets the canvas height.
   *
   * @return height in pixels
   */
  public int getHeight() {
    return height;
  }

  /**
   * Sets the canvas height.
   *
   * @param height height in pixels
   */
  public void setHeight(int height) {
    this.height = height;
  }
}
