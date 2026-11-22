package neqsim.thermo.component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Guards the pure-component database against new internal inconsistencies.
 *
 * <p>
 * The checks compare columns of {@code data/COMP.csv} against one another and against physical plausibility. A finding
 * means two fields disagree, not which one is wrong: the {@code FORMULA} column is unreliable for some rows, so a
 * formula/mass mismatch may indicate a bad formula rather than a bad mass.
 * </p>
 *
 * <p>
 * The database carries a substantial number of pre-existing defects, dominated by rows created by copying a template
 * row and editing only some fields, which leaves the untouched columns holding the template's values. Those are listed
 * in {@code data/comp_known_issues.tsv} so that the gate can be strict about anything new. The test fails both when a
 * finding appears that is not in the baseline and when a baselined finding no longer occurs, so that repairing data
 * forces the baseline to shrink rather than drift.
 * </p>
 *
 * <p>
 * {@code devtools/screen_component_database.py} applies the same checks and prints the detail behind each finding; use
 * it to triage a failure here.
 * </p>
 *
 * @author Pablo Matias Dupuy
 * @version 1.0
 */
public class ComponentDatabaseIntegrityTest {
  /** Component database on the classpath. */
  private static final String DATABASE_RESOURCE = "data/COMP.csv";

  /** Accepted pre-existing findings, one {@code category<TAB>subject} per line. */
  private static final String BASELINE_RESOURCE = "data/comp_known_issues.tsv";

  /** A CAS number identifies a substance, so reuse across unrelated components is suspect. */
  private static final int MAX_COMPONENTS_PER_CAS = 1;

  /** Trailing markers turning a parent component into a modelled ion or carbamate. */
  private static final Pattern CHARGE_SUFFIX = Pattern.compile("(?:COO|[+-])+$");

  /** A CAS registry number is 2-7 digits, 2 digits, then a check digit. */
  private static final Pattern CAS_PATTERN = Pattern.compile("^(\\d{2,7})-(\\d{2})-(\\d)$");

  /** Explicit unknown marker for rows with no real registry number, exempt by design. */
  private static final String UNKNOWN_CAS = "0-0-0-0";

  /** Leading markers for spin or positional isomers sharing the parent's CAS number. */
  private static final Pattern ISOMER_PREFIX = Pattern.compile("^(?:ortho|para|meta)-", Pattern.CASE_INSENSITIVE);

  /** Above this many components sharing a property value, it is a template's, not a measurement. */
  private static final int MAX_COMPONENTS_PER_PROPERTY_VALUE = 3;

  /** Columns screened for values shared by implausibly many components. */
  private static final List<String> PROPERTY_COLUMNS = Collections
      .unmodifiableList(Arrays.asList("MOLARMASS", "LIQDENS", "NORMBOIL", "FORMULA"));

  /** Relative deviation between the formula-derived mass and MOLARMASS that counts as a finding. */
  private static final double FORMULA_MASS_TOLERANCE = 0.02;

  /** Lower bound of the plausible Watson characterisation factor band. */
  private static final double WATSON_K_MIN = 9.0;

  /** Upper bound of the plausible Watson characterisation factor band. */
  private static final double WATSON_K_MAX = 14.0;

  /** Boiling point in degC below which the Watson correlation is not meaningful. */
  private static final double WATSON_MIN_NORMBOIL_C = 0.0;

  /**
   * Abbreviated substituted-alkane name, for example {@code 223-TM-C4}. Groups are 1 = locants, 2 = abbreviation, 3 =
   * optional ring marker, 4 = parent carbon count.
   */
  private static final Pattern SUBSTITUENT_NAME = Pattern.compile("^([\\d.]+)-([A-Za-z]+)-(cy-)?C(\\d+)$",
      Pattern.CASE_INSENSITIVE);

  /** Methyl-substituent abbreviations recognised in component names. */
  private static final List<String> METHYL_ABBREVIATIONS = Collections
      .unmodifiableList(Arrays.asList("M", "DM", "TM", "TRM", "TEM", "TETM"));

  /** Molar mass of a CH2 group in g/mol. */
  private static final double CH2_MASS = 14.02658;

  /** Molar mass of the two chain-terminating hydrogens in g/mol. */
  private static final double H2_MASS = 2.01588;

  /** Mass agreement required in g/mol; well below one CH2 group. */
  private static final double SUBSTITUENT_MASS_TOLERANCE = 0.6;

  /** Largest methyl-substituent count considered when matching a molar mass. */
  private static final int MAX_METHYL_GROUPS = 6;

  /** Element symbol followed by an optional count. */
  private static final Pattern FORMULA_TOKEN = Pattern.compile("([A-Z][a-z]?)(\\d*)");

  /** Atomic masses in g/mol for the elements appearing in the database. */
  private static final Map<String, Double> ATOMIC_MASS = buildAtomicMasses();

  /**
   * Build the atomic mass table.
   *
   * @return unmodifiable map from element symbol to atomic mass in g/mol
   */
  private static Map<String, Double> buildAtomicMasses() {
    Map<String, Double> masses = new HashMap<String, Double>();
    masses.put("C", 12.011);
    masses.put("H", 1.008);
    masses.put("N", 14.007);
    masses.put("O", 15.999);
    masses.put("S", 32.06);
    masses.put("He", 4.0026);
    masses.put("Ne", 20.180);
    masses.put("Ar", 39.948);
    masses.put("Kr", 83.798);
    masses.put("Xe", 131.29);
    masses.put("F", 18.998);
    masses.put("Cl", 35.45);
    masses.put("Br", 79.904);
    masses.put("I", 126.90);
    masses.put("Na", 22.990);
    masses.put("K", 39.098);
    masses.put("Mg", 24.305);
    masses.put("Ca", 40.078);
    masses.put("Ba", 137.33);
    masses.put("Sr", 87.62);
    masses.put("Li", 6.94);
    masses.put("Fe", 55.845);
    return Collections.unmodifiableMap(masses);
  }

  /**
   * Split one CSV line, honouring double quotes so that names containing commas survive.
   *
   * @param line raw CSV line, never null
   * @return the fields in order, with surrounding quotes removed
   */
  private static List<String> splitCsvLine(String line) {
    List<String> fields = new ArrayList<String>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        quoted = !quoted;
      } else if (c == ',' && !quoted) {
        fields.add(field.toString().trim());
        field.setLength(0);
      } else {
        field.append(c);
      }
    }
    fields.add(field.toString().trim());
    return fields;
  }

  /**
   * Read a classpath resource into lines.
   *
   * @param resource classpath location of the resource
   * @return the lines in file order, excluding a trailing empty line
   * @throws IOException if the resource is missing or cannot be read
   */
  private static List<String> readLines(String resource) throws IOException {
    InputStream stream = ComponentDatabaseIntegrityTest.class.getClassLoader().getResourceAsStream(resource);
    if (stream == null) {
      throw new IOException("resource not found on the classpath: " + resource);
    }
    List<String> lines = new ArrayList<String>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    try {
      String line = reader.readLine();
      // Strip a byte order mark so the first column name matches.
      if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
        line = line.substring(1);
      }
      while (line != null) {
        lines.add(line);
        line = reader.readLine();
      }
    } finally {
      reader.close();
    }
    return lines;
  }

  /**
   * Read the component database into one map per row.
   *
   * @return the rows in file order, each keyed by column name
   * @throws IOException if the database cannot be read
   */
  private static List<Map<String, String>> readDatabase() throws IOException {
    List<String> lines = readLines(DATABASE_RESOURCE);
    Assertions.assertFalse(lines.isEmpty(), DATABASE_RESOURCE + " is empty");
    List<String> header = splitCsvLine(lines.get(0));
    List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
    for (int i = 1; i < lines.size(); i++) {
      if (lines.get(i).trim().isEmpty()) {
        continue;
      }
      List<String> values = splitCsvLine(lines.get(i));
      Map<String, String> row = new LinkedHashMap<String, String>();
      for (int c = 0; c < header.size() && c < values.size(); c++) {
        row.put(header.get(c), values.get(c));
      }
      rows.add(row);
    }
    return rows;
  }

  /**
   * Read a field as a double.
   *
   * @param row the database row
   * @param column column name
   * @return the value, or {@link Double#NaN} when absent or not numeric
   */
  private static double numeric(Map<String, String> row, String column) {
    String text = text(row, column);
    if (text.isEmpty()) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  /**
   * Read a field as trimmed text.
   *
   * @param row the database row
   * @param column column name
   * @return the value, or an empty string when absent
   */
  private static String text(Map<String, String> row, String column) {
    String value = row.get(column);
    return value == null ? "" : value.trim();
  }

  /**
   * Compute molar mass from a chemical formula.
   *
   * @param formula formula such as {@code C7H16}
   * @return molar mass in g/mol, or {@link Double#NaN} when the formula cannot be parsed
   */
  private static double formulaMass(String formula) {
    if (formula.isEmpty() || !Character.isUpperCase(formula.charAt(0))) {
      return Double.NaN;
    }
    double total = 0.0;
    int consumed = 0;
    Matcher matcher = FORMULA_TOKEN.matcher(formula);
    while (matcher.find()) {
      String element = matcher.group(1);
      String count = matcher.group(2);
      Double mass = ATOMIC_MASS.get(element);
      if (mass == null) {
        return Double.NaN;
      }
      total += mass.doubleValue() * (count.isEmpty() ? 1 : Integer.parseInt(count));
      consumed += element.length() + count.length();
    }
    if (consumed != formula.length() || total <= 0.0) {
      return Double.NaN;
    }
    return total;
  }

  /**
   * Compute the Watson characterisation factor.
   *
   * @param normboilC normal boiling point in degC
   * @param liqdens relative density (specific gravity), water = 1
   * @return Watson K, or {@link Double#NaN} when the inputs are unusable
   */
  private static double watsonK(double normboilC, double liqdens) {
    if (Double.isNaN(normboilC) || Double.isNaN(liqdens) || liqdens <= 0.0) {
      return Double.NaN;
    }
    double rankine = (normboilC + 273.15) * 1.8;
    if (rankine <= 0.0) {
      return Double.NaN;
    }
    return Math.cbrt(rankine) / liqdens;
  }

  /**
   * Report whether every name is a charge or isomer variant of one parent component.
   *
   * <p>
   * Such a group shares the parent's CAS number deliberately, for example {@code MEA}, {@code MEA+} and
   * {@code MEACOO-}, and is therefore not a data defect.
   * </p>
   *
   * @param names component names sharing one CAS number
   * @return true when all names reduce to the same base name
   */
  /**
   * Counts the substituent positions written in an abbreviated component name. Both {@code 2.2.3} and {@code 223}
   * denote three positions.
   *
   * @param locants locant fragment taken from the component name, never null
   * @return the number of substituent positions, zero or more
   */
  private static int countLocants(String locants) {
    if (locants.indexOf('.') >= 0) {
      int count = 0;
      for (String part : locants.split("\\.")) {
        if (!part.isEmpty()) {
          count++;
        }
      }
      return count;
    }
    return locants.length();
  }

  /**
   * Infers how many methyl groups a molar mass implies for a given parent skeleton.
   *
   * @param molarMass molar mass in g/mol
   * @param baseCarbons carbons in the parent chain or ring, one or more
   * @param isRing true when the parent is a cycloalkane
   * @return the implied methyl count, or -1 when no whole number fits the mass
   */
  private static int methylsFromMass(double molarMass, int baseCarbons, boolean isRing) {
    for (int methyls = 0; methyls <= MAX_METHYL_GROUPS; methyls++) {
      double expected = CH2_MASS * (baseCarbons + methyls) + (isRing ? 0.0 : H2_MASS);
      if (Math.abs(molarMass - expected) <= SUBSTITUENT_MASS_TOLERANCE) {
        return methyls;
      }
    }
    return -1;
  }

  private static boolean oneParent(Set<String> names) {
    Set<String> bases = new TreeSet<String>();
    for (String name : names) {
      String base = ISOMER_PREFIX.matcher(name).replaceAll("");
      bases.add(CHARGE_SUFFIX.matcher(base).replaceAll("").trim().toLowerCase(Locale.ROOT));
    }
    return bases.size() == 1;
  }

  /**
   * Verify a CAS registry number against its trailing check digit.
   *
   * <p>
   * The check digit is the sum of the preceding digits weighted by their position counted from the right, modulo ten.
   * This detects transcription errors and fabricated numbers without consulting any external source.
   * </p>
   *
   * @param cas registry number in XXXXXXX-YY-Z form
   * @return true when well formed and the check digit agrees
   */
  private static boolean casIsValid(String cas) {
    Matcher matcher = CAS_PATTERN.matcher(cas);
    if (!matcher.matches()) {
      return false;
    }
    String body = matcher.group(1) + matcher.group(2);
    int total = 0;
    for (int i = 0; i < body.length(); i++) {
      int digit = Character.digit(body.charAt(body.length() - 1 - i), 10);
      total += digit * (i + 1);
    }
    return total % 10 == Integer.parseInt(matcher.group(3));
  }

  /**
   * Record one component under a shared value so that over-sharing can be detected.
   *
   * @param index map from value to the components carrying it
   * @param value the shared value, ignored when empty
   * @param name component name
   */
  private static void index(Map<String, Set<String>> index, String value, String name) {
    if (value.isEmpty()) {
      return;
    }
    Set<String> names = index.get(value);
    if (names == null) {
      names = new TreeSet<String>();
      index.put(value, names);
    }
    names.add(name);
  }

  /**
   * Apply every consistency check to the database.
   *
   * @param rows the database rows
   * @return findings as sorted {@code category<TAB>subject} entries
   */
  private static Set<String> screen(List<Map<String, String>> rows) {
    Set<String> findings = new TreeSet<String>();
    Map<String, Set<String>> byCas = new TreeMap<String, Set<String>>();
    Map<String, Map<String, Set<String>>> byProperty = new LinkedHashMap<String, Map<String, Set<String>>>();
    for (String column : PROPERTY_COLUMNS) {
      byProperty.put(column, new TreeMap<String, Set<String>>());
    }
    Map<String, Set<String>> byTriplet = new TreeMap<String, Set<String>>();

    for (Map<String, String> row : rows) {
      String name = text(row, "NAME");
      if (name.isEmpty()) {
        continue;
      }
      String comptype = text(row, "COMPTYPE").toLowerCase();
      double molarMass = numeric(row, "MOLARMASS");
      double liqdens = numeric(row, "LIQDENS");
      double normboil = numeric(row, "NORMBOIL");
      double tc = numeric(row, "TC");

      for (int i = 0; i < name.length(); i++) {
        if (name.charAt(i) > 127) {
          findings.add("non_ascii_name\t" + name);
          break;
        }
      }

      double derived = formulaMass(text(row, "FORMULA"));
      if (!Double.isNaN(derived) && !Double.isNaN(molarMass) && molarMass > 0.0
          && Math.abs(derived - molarMass) / molarMass > FORMULA_MASS_TOLERANCE) {
        findings.add("formula_mass_mismatch\t" + name);
      }

      if (!Double.isNaN(tc) && !Double.isNaN(normboil) && tc <= normboil) {
        findings.add("critical_below_boiling\t" + name);
      }

      Matcher substituted = SUBSTITUENT_NAME.matcher(name);
      if (substituted.matches() && !Double.isNaN(molarMass)
          && METHYL_ABBREVIATIONS.contains(substituted.group(2).toUpperCase(Locale.ROOT))
          && methylsFromMass(molarMass, Integer.parseInt(substituted.group(4)),
              substituted.group(3) != null) != countLocants(substituted.group(1))) {
        findings.add("substituent_count_mismatch\t" + name);
      }

      if ("hc".equals(comptype) && !Double.isNaN(normboil) && normboil > WATSON_MIN_NORMBOIL_C) {
        double kw = watsonK(normboil, liqdens);
        if (!Double.isNaN(kw) && (kw < WATSON_K_MIN || kw > WATSON_K_MAX)) {
          findings.add("implausible_watson_k\t" + name);
        }
      }

      String cas = text(row, "CASnumber");
      index(byCas, cas, name);
      if (cas.length() > 0 && !UNKNOWN_CAS.equals(cas) && !casIsValid(cas)) {
        findings.add("cas_checksum_invalid\t" + name);
      }
      for (String column : PROPERTY_COLUMNS) {
        index(byProperty.get(column), text(row, column), name);
      }

      if (!"ion".equals(comptype) && !Double.isNaN(molarMass) && molarMass != 0.0 && !Double.isNaN(normboil)
          && !Double.isNaN(liqdens)) {
        index(byTriplet, text(row, "MOLARMASS") + "|" + text(row, "NORMBOIL") + "|" + text(row, "LIQDENS"), name);
      }
    }

    for (Map.Entry<String, Set<String>> entry : byCas.entrySet()) {
      if (entry.getValue().size() > MAX_COMPONENTS_PER_CAS && !oneParent(entry.getValue())) {
        findings.add("shared_cas_number\t" + entry.getKey());
      }
    }
    for (String column : PROPERTY_COLUMNS) {
      for (Map.Entry<String, Set<String>> entry : byProperty.get(column).entrySet()) {
        if (entry.getValue().size() > MAX_COMPONENTS_PER_PROPERTY_VALUE) {
          findings.add("over_shared_" + column.toLowerCase() + "\t" + entry.getKey());
        }
      }
    }
    for (Map.Entry<String, Set<String>> entry : byTriplet.entrySet()) {
      if (entry.getValue().size() > 1) {
        findings.add("duplicate_property_triplet\t" + entry.getKey());
      }
    }
    return findings;
  }

  /**
   * Render findings for an assertion message.
   *
   * @param heading text introducing the block
   * @param entries the findings to list
   * @return a multi-line description
   */
  private static String describe(String heading, Set<String> entries) {
    StringBuilder message = new StringBuilder();
    message.append(heading).append(" (").append(entries.size()).append("):");
    for (String entry : entries) {
      message.append("\n    ").append(entry.replace('\t', ' '));
    }
    return message.toString();
  }

  /**
   * Fail when the database gains a defect that the baseline does not already accept.
   *
   * @throws IOException if the database or the baseline cannot be read
   */
  @Test
  public void databaseHasNoUnrecordedInconsistencies() throws IOException {
    Set<String> findings = screen(readDatabase());
    Set<String> baseline = new TreeSet<String>(readLines(BASELINE_RESOURCE));
    baseline.remove("");

    Set<String> introduced = new TreeSet<String>(findings);
    introduced.removeAll(baseline);
    if (!introduced.isEmpty()) {
      Assertions.fail(describe("COMP.csv gained inconsistencies that are not in " + BASELINE_RESOURCE, introduced)
          + "\n\nRun devtools/screen_component_database.py for the detail behind each finding."
          + "\nFix the data, or add the entry to the baseline with a reason if it is intentional.");
    }
  }

  /**
   * Fail when the baseline lists a defect the database no longer has, so it shrinks as data is repaired instead of
   * drifting out of date.
   *
   * @throws IOException if the database or the baseline cannot be read
   */
  @Test
  public void baselineListsNothingAlreadyFixed() throws IOException {
    Set<String> findings = screen(readDatabase());
    Set<String> baseline = new TreeSet<String>(readLines(BASELINE_RESOURCE));
    baseline.remove("");

    Set<String> stale = new TreeSet<String>(baseline);
    stale.removeAll(findings);
    if (!stale.isEmpty()) {
      Assertions.fail(describe(BASELINE_RESOURCE + " accepts inconsistencies that no longer exist", stale)
          + "\n\nRemove these lines from the baseline.");
    }
  }
}
