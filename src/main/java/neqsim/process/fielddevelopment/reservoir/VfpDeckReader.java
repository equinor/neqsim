package neqsim.process.fielddevelopment.reservoir;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    Matcher keyword = Pattern.compile("(?im)^\\s*(VFPPROD|VFPINJ)\\b").matcher(body);
    if (!keyword.find()) {
      throw new IllegalArgumentException("No VFPPROD or VFPINJ keyword found");
    }
    boolean inj = "VFPINJ".equalsIgnoreCase(keyword.group(1));
    String[] records = body.substring(keyword.end()).split("/", -1);
    ParsedTable table = new ParsedTable();
    table.injection = inj;
    Matcher header = Pattern.compile("'([^']*)'|([^\\s]+)").matcher(records[0].trim());
    List<String> fields = new ArrayList<String>();
    while (header.find()) {
      fields.add(header.group(1) == null ? header.group(2) : header.group(1));
    }
    if (fields.size() != (inj ? 6 : 9)) {
      throw new IllegalArgumentException("Invalid VFP header: expected " + (inj ? 6 : 9) + " fields");
    }
    table.tableNumber = Integer.parseInt(fields.get(0));
    table.datumDepth = Double.parseDouble(fields.get(1));
    for (int i = 2; i < fields.size(); i++) {
      table.definitions.add(fields.get(i).toUpperCase(java.util.Locale.ROOT));
    }
    int nAxes = inj ? 2 : 5;
    double[][] axes = new double[5][];
    if (records.length <= nAxes + 1) {
      throw new IllegalArgumentException("Missing VFP axis records");
    }
    for (int i = 0; i < nAxes; i++) {
      axes[i] = numbers(records[1 + i]);
      if (axes[i].length == 0) {
        throw new IllegalArgumentException("Empty VFP axis " + i);
      }
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
    long recordCount = 1;
    for (int d = 1; d < 5; d++) {
      recordCount *= axes[d].length;
      if (recordCount > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Too many VFP pressure records");
      }
    }
    int expected = (int) recordCount;
    int first = 1 + nAxes;
    if (records.length - first - 1 < expected) {
      throw new IllegalArgumentException("Missing pressure records: expected " + expected);
    }
    double[][][][][] bhp = new double[nf][nt][nw][ng][na];
    boolean[][][][] seen = new boolean[nt][nw][ng][na];
    for (int r = 0; r < expected; r++) {
      if (first + r >= records.length - 1 || records[first + r].trim().isEmpty()) {
        throw new IllegalArgumentException("Missing pressure records: expected " + expected);
      }
      double[] v = numbers(records[first + r]);
      int skip = inj ? 1 : 4;
      if (v.length != skip + nf) {
        throw new IllegalArgumentException(
            "Record " + (r + 1) + " has " + v.length + " numbers, expected " + (skip + nf));
      }
      int t = index(v[0], nt, "THP");
      int w = inj ? 0 : index(v[1], nw, "WFR");
      int g = inj ? 0 : index(v[2], ng, "GFR");
      int a = inj ? 0 : index(v[3], na, "ALQ");
      if (seen[t][w][g][a]) {
        throw new IllegalArgumentException("Duplicate VFP pressure record: THP/WFR/GFR/ALQ indices");
      }
      seen[t][w][g][a] = true;
      for (int f = 0; f < nf; f++) {
        bhp[f][t][w][g][a] = v[skip + f];
      }
    }
    String trailing = records[first + expected].trim();
    if (!trailing.isEmpty() && !Character.isLetter(trailing.charAt(0))) {
      throw new IllegalArgumentException("Extra pressure records: expected " + expected);
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
    List<Double> expanded = new ArrayList<Double>();
    for (String token : trimmed.split("\\s+")) {
      String[] repetition = token.split("\\*", -1);
      int count = repetition.length == 2 ? Integer.parseInt(repetition[0]) : 1;
      String value = repetition.length == 2 ? repetition[1] : token;
      if (repetition.length > 2 || value.isEmpty() || count <= 0 || count > 1000000) {
        throw new IllegalArgumentException("Unsupported VFP numeric/default token: " + token);
      }
      double number = Double.parseDouble(value.replace('D', 'E').replace('d', 'e'));
      if (!Double.isFinite(number)) {
        throw new IllegalArgumentException("Non-finite VFP number: " + token);
      }
      for (int i = 0; i < count; i++) {
        expanded.add(number);
      }
    }
    double[] values = new double[expanded.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = expanded.get(i);
    }
    return values;
  }

  /**
   * Validates a one-based deck index before converting to a Java index.
   *
   * @param value deck index, dimensionless
   * @param length axis length
   * @param axis axis name for diagnostics
   * @return zero-based index
   */
  private static int index(double value, int length, String axis) {
    if (value != Math.rint(value) || value < 1 || value > length) {
      throw new IllegalArgumentException("Invalid " + axis + " record index " + value + "; expected 1.." + length);
    }
    return (int) value - 1;
  }
}
