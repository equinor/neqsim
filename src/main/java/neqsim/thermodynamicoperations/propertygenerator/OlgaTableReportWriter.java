package neqsim.thermodynamicoperations.propertygenerator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Markdown builder for the engineering report that accompanies a generated OLGA table.
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class OlgaTableReportWriter {
  private final StringBuilder text = new StringBuilder();

  /**
   * Formats a number with six significant digits, or "n/a" when it is not finite.
   *
   * @param value number
   * @return formatted text
   */
  static String number(double value) {
    return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6g", value) : "n/a";
  }

  /**
   * Adds the report title.
   *
   * @param title title text
   * @return this writer
   */
  OlgaTableReportWriter title(String title) {
    text.append("# ").append(title).append("\n\n");
    return this;
  }

  /**
   * Adds a section heading.
   *
   * @param heading heading text
   * @return this writer
   */
  OlgaTableReportWriter section(String heading) {
    text.append("## ").append(heading).append("\n\n");
    return this;
  }

  /**
   * Adds a paragraph.
   *
   * @param paragraph paragraph text
   * @return this writer
   */
  OlgaTableReportWriter paragraph(String paragraph) {
    text.append(paragraph).append("\n\n");
    return this;
  }

  /**
   * Adds a two-column item/value table.
   *
   * @param items ordered items and values
   * @return this writer
   */
  OlgaTableReportWriter keyValues(Map<String, String> items) {
    text.append("| Item | Value |\n| --- | --- |\n");
    for (Map.Entry<String, String> item : items.entrySet()) {
      text.append("| ").append(item.getKey()).append(" | ").append(item.getValue()).append(" |\n");
    }
    text.append('\n');
    return this;
  }

  /**
   * Adds a table.
   *
   * @param header column headings
   * @param rows table rows
   * @return this writer
   */
  OlgaTableReportWriter table(String[] header, List<String[]> rows) {
    text.append("| ").append(String.join(" | ", header)).append(" |\n|");
    for (int i = 0; i < header.length; i++) {
      text.append(" --- |");
    }
    text.append('\n');
    for (String[] row : rows) {
      text.append("| ").append(String.join(" | ", row)).append(" |\n");
    }
    text.append('\n');
    return this;
  }

  /**
   * Adds the feed composition table.
   *
   * @param components component names
   * @param moleFractions mole fractions
   * @param molarMasses molar masses [g/mol]
   * @return this writer
   */
  OlgaTableReportWriter composition(String[] components, double[] moleFractions, double[] molarMasses) {
    List<String[]> rows = new ArrayList<String[]>();
    for (int i = 0; i < components.length; i++) {
      rows.add(new String[] {components[i], number(moleFractions[i]), number(molarMasses[i])});
    }
    return table(new String[] {"Component", "Mole fraction", "Molar mass [g/mol]"}, rows);
  }

  /**
   * Adds the phase-envelope section.
   *
   * @param envelope envelope extents of the water-free feed
   * @param refinement true when envelope grid refinement was requested
   * @param gridFactor ratio of points inside to outside the envelope
   * @return this writer
   */
  OlgaTableReportWriter envelope(OlgaEnvelopeInfo envelope, boolean refinement, double gridFactor) {
    section("Phase envelope of the water-free feed");
    Map<String, String> items = new LinkedHashMap<String, String>();
    items.put("Critical temperature [K]", number(envelope.criticalTemperature));
    items.put("Critical pressure [bara]", number(envelope.criticalPressure));
    items.put("Cricondenbar pressure [bara]", number(envelope.cricondenbarPressure));
    items.put("Cricondentherm temperature [K]", number(envelope.cricondenthermTemperature));
    items.put("Envelope grid refinement",
        refinement
            ? "on, grid factor " + number(gridFactor) + " (share inside " + number(gridFactor / (gridFactor + 1.0))
                + ")"
            : "off");
    return keyValues(items);
  }

  /**
   * Adds the grid section.
   *
   * @param pressuresBara pressure axis [bara]
   * @param pressureMode how the pressure axis was built
   * @param temperaturesC temperature axis [C]
   * @param temperatureMode how the temperature axis was built
   * @return this writer
   */
  OlgaTableReportWriter grid(double[] pressuresBara, String pressureMode, double[] temperaturesC,
      String temperatureMode) {
    section("Grid");
    List<String[]> rows = new ArrayList<String[]>();
    rows.add(new String[] {"Pressure [bara]", Integer.toString(pressuresBara.length), number(pressuresBara[0]),
        number(pressuresBara[pressuresBara.length - 1]), pressureMode});
    rows.add(new String[] {"Temperature [C]", Integer.toString(temperaturesC.length), number(temperaturesC[0]),
        number(temperaturesC[temperaturesC.length - 1]), temperatureMode});
    table(new String[] {"Axis", "Points", "Min", "Max", "Construction"}, rows);
    paragraph("Pressures [bara]: " + join(pressuresBara));
    return paragraph("Temperatures [C]: " + join(temperaturesC));
  }

  /**
   * Adds the quality-check section.
   *
   * @param report quality report of the table
   * @return this writer
   */
  OlgaTableReportWriter quality(OlgaTableQualityReport report) {
    section("Quality checks");
    paragraph("Loadable by OLGA (all values finite, densities, compressibilities, viscosities, heat capacities and"
        + " conductivities positive): **" + (report.isLoadable() ? "yes" : "NO") + "**. Monotonicity violations"
        + " at existing nodes are physical where phase compositions change with pressure (retrograde liquid"
        + " density); violations at extrapolated nodes indicate a fill defect. A boundary jump far above the"
        + " interior jump indicates a discontinuous extrapolation.");
    List<String[]> rows = new ArrayList<String[]>();
    for (OlgaTableQualityReport.ColumnSummary column : report.getColumns()) {
      rows.add(new String[] {column.keyword, number(column.minimum), number(column.maximum),
          Integer.toString(column.nonFiniteCount), Integer.toString(column.nonPositiveCount),
          column.existingNodes < 0 ? "all" : Integer.toString(column.existingNodes),
          Integer.toString(column.monotonicityViolationsExisting),
          Integer.toString(column.monotonicityViolationsExtrapolated), number(column.maxInteriorJump),
          number(column.maxBoundaryJump)});
    }
    return table(new String[] {"Column", "Min", "Max", "Non-finite", "Non-positive", "Existing nodes",
        "Violations (existing)", "Violations (extrapolated)", "Max interior jump", "Max boundary jump"}, rows);
  }

  /**
   * Joins axis values.
   *
   * @param values axis values
   * @return comma-separated text
   */
  private static String join(double[] values) {
    StringBuilder joined = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        joined.append(", ");
      }
      joined.append(number(values[i]));
    }
    return joined.toString();
  }

  /**
   * Writes the report to a file, creating the parent directory.
   *
   * @param filename output file
   * @throws IOException if the file cannot be written
   */
  void write(String filename) throws IOException {
    File file = new File(filename);
    File parent = file.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new IOException("Could not create directory " + parent.getAbsolutePath());
    }
    try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"))) {
      writer.write(text.toString());
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return text.toString();
  }
}
