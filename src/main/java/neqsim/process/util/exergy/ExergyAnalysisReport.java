package neqsim.process.util.exergy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured result of an exergy analysis over a {@code ProcessSystem} or {@code ProcessModel}.
 * Contains one row per unit operation with its exergy change across material streams and its exergy
 * destruction rate, plus convenience aggregation / ranking / reporting helpers suitable for large
 * flowsheets.
 *
 * <p>
 * All energies are stored internally in Joules (J). For steady-state process simulations in NeqSim,
 * stream enthalpy/entropy are per-second quantities so "J" is equivalent to "W" and may be reported
 * as such via {@link #toString(String)}.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ExergyAnalysisReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * One row of an exergy analysis: the exergy balance for a single unit operation or process area.
   */
  public static class Entry implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final String type;
    private final String area;
    private final double exergyChangeJ;
    private final double exergyDestructionJ;

    /**
     * Create a new entry.
     *
     * @param name unit name
     * @param type simple class name of the unit
     * @param area process area name (may be {@code null} for flat ProcessSystem)
     * @param exergyChangeJ net change in stream exergy across the unit (outlet − inlet) in Joules
     * @param exergyDestructionJ exergy destruction rate in Joules (always non-negative)
     */
    public Entry(String name, String type, String area, double exergyChangeJ,
        double exergyDestructionJ) {
      this.name = name;
      this.type = type;
      this.area = area;
      this.exergyChangeJ = exergyChangeJ;
      this.exergyDestructionJ = Math.max(0.0, exergyDestructionJ);
    }

    /**
     * @return unit operation name
     */
    public String getName() {
      return name;
    }

    /**
     * @return simple class name of the unit
     */
    public String getType() {
      return type;
    }

    /**
     * @return area name (may be {@code null})
     */
    public String getArea() {
      return area;
    }

    /**
     * @return exergy change across the unit in Joules
     */
    public double getExergyChangeJ() {
      return exergyChangeJ;
    }

    /**
     * @return exergy destruction in Joules
     */
    public double getExergyDestructionJ() {
      return exergyDestructionJ;
    }

    /**
     * @param unit unit of the returned value ("J", "kJ", "MJ", "W", "kW", "MW")
     * @return exergy change in requested unit
     */
    public double getExergyChange(String unit) {
      return convert(exergyChangeJ, unit);
    }

    /**
     * @param unit unit of the returned value
     * @return exergy destruction in requested unit
     */
    public double getExergyDestruction(String unit) {
      return convert(exergyDestructionJ, unit);
    }
  }

  private final double surroundingTemperatureK;
  private final List<Entry> entries = new ArrayList<Entry>();

  /**
   * Create an empty report.
   *
   * @param surroundingTemperatureK surrounding ("dead-state") temperature in K used when computing
   *        exergy
   */
  public ExergyAnalysisReport(double surroundingTemperatureK) {
    this.surroundingTemperatureK = surroundingTemperatureK;
  }

  /**
   * Append an entry to the report.
   *
   * @param entry row to add
   */
  public void addEntry(Entry entry) {
    if (entry != null) {
      entries.add(entry);
    }
  }

  /**
   * @return surrounding temperature used for exergy calculations (K)
   */
  public double getSurroundingTemperatureK() {
    return surroundingTemperatureK;
  }

  /**
   * @return an unmodifiable view of all entries in insertion order
   */
  public List<Entry> getEntries() {
    return Collections.unmodifiableList(entries);
  }

  /**
   * @return number of rows in the report
   */
  public int size() {
    return entries.size();
  }

  /**
   * Returns the top-N entries with the largest exergy destruction. Useful for hot-spot
   * identification on large processes.
   *
   * @param n number of entries to return; values &lt;= 0 return the full list sorted
   * @return a new list of entries sorted by descending destruction
   */
  public List<Entry> getTopDestructionHotspots(int n) {
    List<Entry> sorted = new ArrayList<Entry>(entries);
    Collections.sort(sorted, new Comparator<Entry>() {
      @Override
      public int compare(Entry a, Entry b) {
        return Double.compare(b.exergyDestructionJ, a.exergyDestructionJ);
      }
    });
    if (n <= 0 || n >= sorted.size()) {
      return sorted;
    }
    return new ArrayList<Entry>(sorted.subList(0, n));
  }

  /**
   * @param unit energy unit ("J", "kJ", "MJ", "W", "kW", "MW")
   * @return total exergy destruction summed over all entries
   */
  public double getTotalExergyDestruction(String unit) {
    double sum = 0.0;
    for (Entry e : entries) {
      sum += e.exergyDestructionJ;
    }
    return convert(sum, unit);
  }

  /**
   * @param unit energy unit
   * @return total exergy change summed over all entries
   */
  public double getTotalExergyChange(String unit) {
    double sum = 0.0;
    for (Entry e : entries) {
      sum += e.exergyChangeJ;
    }
    return convert(sum, unit);
  }

  /**
   * Groups the total exergy destruction by process area. Returns an empty map when no entries carry
   * an area.
   *
   * @param unit energy unit
   * @return map from area name to total destruction in the requested unit
   */
  public Map<String, Double> getDestructionByArea(String unit) {
    Map<String, Double> out = new LinkedHashMap<String, Double>();
    for (Entry e : entries) {
      if (e.area == null) {
        continue;
      }
      Double prev = out.get(e.area);
      double cur = prev == null ? 0.0 : prev.doubleValue();
      out.put(e.area, cur + convert(e.exergyDestructionJ, unit));
    }
    return out;
  }

  /**
   * Groups the total exergy destruction by unit type (simple class name).
   *
   * @param unit energy unit
   * @return map from class name to total destruction
   */
  public Map<String, Double> getDestructionByType(String unit) {
    Map<String, Double> out = new LinkedHashMap<String, Double>();
    for (Entry e : entries) {
      String key = e.type == null ? "Unknown" : e.type;
      Double prev = out.get(key);
      double cur = prev == null ? 0.0 : prev.doubleValue();
      out.put(key, cur + convert(e.exergyDestructionJ, unit));
    }
    return out;
  }

  /**
   * Pretty-print the report as a plain-text table ranked by exergy destruction.
   *
   * @param unit energy unit to display
   * @return a human-readable table
   */
  public String toString(String unit) {
    StringBuilder sb = new StringBuilder();
    sb.append("Exergy analysis (T0 = ").append(String.format("%.2f", surroundingTemperatureK))
        .append(" K, unit = ").append(unit).append(")\n");
    sb.append(String.format("%-30s %-20s %-15s %15s %15s%n", "Unit", "Type", "Area",
        "dE (" + unit + ")", "Destr (" + unit + ")"));
    sb.append(repeat('-', 100)).append('\n');
    List<Entry> sorted = getTopDestructionHotspots(0);
    for (Entry e : sorted) {
      sb.append(String.format("%-30s %-20s %-15s %15.3f %15.3f%n", clip(e.name, 30),
          clip(e.type == null ? "" : e.type, 20), clip(e.area == null ? "" : e.area, 15),
          e.getExergyChange(unit), e.getExergyDestruction(unit)));
    }
    sb.append(repeat('-', 100)).append('\n');
    sb.append(String.format("%-67s %15.3f %15.3f%n", "TOTAL", getTotalExergyChange(unit),
        getTotalExergyDestruction(unit)));
    return sb.toString();
  }

  @Override
  public String toString() {
    return toString("kW");
  }

  /**
   * Serialize the report to JSON. Output contains surrounding temperature, totals and an array of
   * per-unit rows.
   *
   * @return a JSON string
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    sb.append("\"surroundingTemperatureK\":").append(surroundingTemperatureK).append(',');
    sb.append("\"totalExergyDestructionJ\":").append(getTotalExergyDestruction("J")).append(',');
    sb.append("\"totalExergyChangeJ\":").append(getTotalExergyChange("J")).append(',');
    sb.append("\"entries\":[");
    for (int i = 0; i < entries.size(); i++) {
      Entry e = entries.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append('{').append("\"name\":\"").append(escape(e.name)).append('\"').append(',')
          .append("\"type\":\"").append(escape(e.type == null ? "" : e.type)).append('\"')
          .append(',').append("\"area\":")
          .append(e.area == null ? "null" : ("\"" + escape(e.area) + "\"")).append(',')
          .append("\"exergyChangeJ\":").append(e.exergyChangeJ).append(',')
          .append("\"exergyDestructionJ\":").append(e.exergyDestructionJ).append('}');
    }
    sb.append("]}");
    return sb.toString();
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  /**
   * Convert Joules to the requested unit.
   *
   * @param valueJ value in Joules
   * @param unit target unit ("J", "kJ", "MJ", "W", "kW", "MW")
   * @return converted value
   */
  private static double convert(double valueJ, String unit) {
    if (unit == null) {
      return valueJ;
    }
    if (unit.equals("J") || unit.equals("W")) {
      return valueJ;
    }
    if (unit.equals("kJ") || unit.equals("kW")) {
      return valueJ / 1.0e3;
    }
    if (unit.equals("MJ") || unit.equals("MW")) {
      return valueJ / 1.0e6;
    }
    return valueJ;
  }

  /**
   * Java-8 compatible string repeat.
   *
   * @param c character to repeat
   * @param n number of repetitions
   * @return the resulting string
   */
  private static String repeat(char c, int n) {
    char[] buf = new char[Math.max(0, n)];
    for (int i = 0; i < buf.length; i++) {
      buf[i] = c;
    }
    return new String(buf);
  }

  /**
   * Truncate {@code s} to at most {@code max} characters.
   *
   * @param s input string (may be {@code null})
   * @param max maximum length
   * @return truncated string, never {@code null}
   */
  private static String clip(String s, int max) {
    if (s == null) {
      return "";
    }
    if (s.length() <= max) {
      return s;
    }
    return s.substring(0, Math.max(0, max - 1)) + "…";
  }

  /**
   * Escape a string for inclusion in a JSON literal.
   *
   * @param s input
   * @return escaped string
   */
  private static String escape(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          sb.append(c);
      }
    }
    return sb.toString();
  }
}
