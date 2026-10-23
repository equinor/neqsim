package neqsim.process.fielddevelopment.reservoir;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a single VFPPROD or VFPINJ keyword back into numbers, in deck units and deck index order.
 *
 * <p>
 * The reader accepts what {@link neqsim.process.util.optimizer.EclipseVFPExporter} and
 * {@link VfpTableBuilder.Result#toDeckString()} write and the common hand-written layout of Eclipse and OPM Flow decks:
 * comments start with {@code --}, records end with a slash, the header carries table number, datum depth and the
 * quantity definitions, followed by the axes and one record per THP, water, gas and ALQ index combination. It is meant
 * for round-trip checks and for validating an existing table before it is replaced, not as a full deck parser.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class VfpDeckReader {

  /**
   * One parsed table in deck units.
   */
  public static final class ParsedTable {
    private boolean injection;
    private int tableNumber;
    private double datumDepth;
    private final List<String> definitions = new ArrayList<String>();
    private double[][] axes;
    private double[][][][][] bhp;

    /**
     * Tells whether the keyword was VFPINJ.
     *
     * @return true for VFPINJ
     */
    public boolean isInjection() {
      return injection;
    }

    /**
     * Gets the table number.
     *
     * @return table number
     */
    public int getTableNumber() {
      return tableNumber;
    }

    /**
     * Gets the datum depth in deck units.
     *
     * @return datum depth
     */
    public double getDatumDepth() {
      return datumDepth;
    }

    /**
     * Gets the quoted header definitions in order, for example GAS, WGR, OGR, THP, GRAT, METRIC, BHP.
     *
     * @return upper-case definition strings
     */
    public List<String> getDefinitions() {
      return definitions;
    }

    /**
     * Gets the axes in deck units: rate, THP, water ratio, gas ratio, ALQ (the last three have one zero for VFPINJ).
     *
     * @return five axes
     */
    public double[][] getAxes() {
      return axes;
    }

    /**
     * Gets the pressures in deck units in order [rate][THP][water][gas][ALQ].
     *
     * @return flowing pressure table
     */
    public double[][][][][] getBhp() {
      return bhp;
    }
  }

  /** Utility class; not instantiable. */
  private VfpDeckReader() {
  }

  /**
   * Parses the first VFPPROD or VFPINJ keyword of a text.
   *
   * @param text deck text
   * @return parsed table
   * @throws IllegalArgumentException if no keyword is found or the records are inconsistent
   */
  public static ParsedTable parse(String text) {
    StringBuilder clean = new StringBuilder();
    for (String line : text.split("\\r?\\n")) {
      int c = line.indexOf("--");
      clean.append(c >= 0 ? line.substring(0, c) : line).append('\n');
    }
    String body = clean.toString();
    int pi = body.indexOf("VFPPROD");
    int ii = body.indexOf("VFPINJ");
    boolean inj = pi < 0 || (ii >= 0 && ii < pi);
    int start = inj ? ii : pi;
    if (start < 0) {
      throw new IllegalArgumentException("No VFPPROD or VFPINJ keyword found");
    }
    String[] records = body.substring(start + (inj ? 6 : 7)).split("/");
    ParsedTable table = new ParsedTable();
    table.injection = inj;
    String[] header = records[0].trim().split("\\s+");
    table.tableNumber = Integer.parseInt(header[0]);
    table.datumDepth = Double.parseDouble(header[1]);
    for (int i = 2; i < header.length; i++) {
      table.definitions.add(header[i].replace("'", "").toUpperCase(java.util.Locale.ROOT));
    }
    int nAxes = inj ? 2 : 5;
    double[][] axes = new double[5][];
    for (int i = 0; i < nAxes; i++) {
      axes[i] = numbers(records[1 + i]);
    }
    for (int i = nAxes; i < 5; i++) {
      axes[i] = new double[] {0.0};
    }
    table.axes = axes;
    int nf = axes[0].length;
    int nt = axes[1].length;
    int nw = axes[2].length;
    int ng = axes[3].length;
    int na = axes[4].length;
    double[][][][][] bhp = new double[nf][nt][nw][ng][na];
    int expected = nt * nw * ng * na;
    int first = 1 + nAxes;
    for (int r = 0; r < expected; r++) {
      if (first + r >= records.length) {
        throw new IllegalArgumentException("Missing pressure records: expected " + expected);
      }
      double[] v = numbers(records[first + r]);
      int skip = inj ? 1 : 4;
      if (v.length != skip + nf) {
        throw new IllegalArgumentException(
            "Record " + (r + 1) + " has " + v.length + " numbers, expected " + (skip + nf));
      }
      int t = (int) v[0] - 1;
      int w = inj ? 0 : (int) v[1] - 1;
      int g = inj ? 0 : (int) v[2] - 1;
      int a = inj ? 0 : (int) v[3] - 1;
      for (int f = 0; f < nf; f++) {
        bhp[f][t][w][g][a] = v[skip + f];
      }
    }
    table.bhp = bhp;
    return table;
  }

  /**
   * Parses whitespace separated numbers of one slash-terminated record.
   *
   * @param record record text without the terminating slash
   * @return parsed numbers, empty for a blank record
   */
  private static double[] numbers(String record) {
    String trimmed = record.trim();
    if (trimmed.isEmpty()) {
      return new double[0];
    }
    String[] parts = trimmed.split("\\s+");
    double[] values = new double[parts.length];
    for (int i = 0; i < parts.length; i++) {
      values[i] = Double.parseDouble(parts[i]);
    }
    return values;
  }
}
