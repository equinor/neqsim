package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

/**
 * Guards the UNIFAC data tables against silent corruption.
 *
 * <p>
 * A component that is missing from a UNIFAC table, or that carries a wrong group assignment, does not fail loudly. It
 * ends up with R = Q = 0, which turns the combinatorial term into NaN and quietly destroys any phase envelope built on
 * it. These checks make such defects visible.
 * </p>
 *
 * <p>
 * The reference values for R, Q and main group come from the DDBST published original UNIFAC parameter set, which is
 * the same source UNIFACGroupParam.csv already cites through its "Hansen1991" reference column.
 * </p>
 *
 * <p>
 * Findings are compared against a baseline of accepted, pre-existing issues. A new finding fails the build, and so does
 * a baseline entry that has been fixed, which forces the baseline to shrink over time.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class UnifacDatabaseIntegrityTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(UnifacDatabaseIntegrityTest.class);

  /** Subgroup parameter table on the classpath. */
  private static final String GROUP_PARAM_RESOURCE = "data/UNIFACGroupParam.csv";

  /** Component database, used to cross-check molar mass and reachability. */
  private static final String COMPONENT_RESOURCE = "data/COMP.csv";

  /** UNIFAC component-to-group tables that are screened. */
  private static final List<String> COMPONENT_TABLES = Collections
      .unmodifiableList(Arrays.asList("data/UNIFACcomp.csv", "data/UNIFACcompUMRPRU.csv"));

  /** Baseline of accepted, pre-existing findings. */
  private static final String BASELINE_RESOURCE = "data/unifac_known_issues.tsv";

  /** Tolerance on published group volume and surface area parameters. */
  private static final double RQ_TOLERANCE = 5.0e-4;

  /** Tolerance on the molar mass implied by a group assignment, in g/mol. */
  private static final double MASS_TOLERANCE = 0.05;

  /** Aliphatic subgroups CH3, CH2, CH and C. */
  private static final List<String> ALIPHATIC_SUBGROUPS = Collections
      .unmodifiableList(Arrays.asList("1", "2", "3", "4"));

  /** UMR-PRU cyclic subgroups cCH2, cCH and cC. */
  private static final List<String> CYCLIC_SUBGROUPS = Collections.unmodifiableList(Arrays.asList("136", "137", "138"));

  /** Main group 4 subgroups ACCH3, ACCH2 and ACCH, the aromatic carbon-alkane groups. */
  private static final List<String> AROMATIC_ALKANE_SUBGROUPS = Collections
      .unmodifiableList(Arrays.asList("11", "12", "13"));

  /** Bare aromatic carbon AC, reserved for non-alkane ring substituents. */
  private static final String BARE_AROMATIC_CARBON = "10";

  /** Table in which the UMR-PRU cyclic groups are expected to be used. */
  private static final String UMRPRU_TABLE = "UNIFACcompUMRPRU";

  /**
   * DDBST published original UNIFAC subgroups, encoded as "secondary;name;maingroup;volumeR;surfaceQ".
   */
  private static final String[] DDBST_GROUPS = { "1;CH3;1;0.9011;0.8480", "2;CH2;1;0.6744;0.5400",
      "3;CH;1;0.4469;0.2280", "4;C;1;0.2195;0.0000", "5;CH2=CH;2;1.3454;1.1760", "6;CH=CH;2;1.1167;0.8670",
      "7;CH2=C;2;1.1173;0.9880", "8;CH=C;2;0.8886;0.6760", "9;ACH;3;0.5313;0.4000", "10;AC;3;0.3652;0.1200",
      "11;ACCH3;4;1.2663;0.9680", "12;ACCH2;4;1.0396;0.6600", "13;ACCH;4;0.8121;0.3480", "14;OH;5;1.0000;1.2000",
      "15;CH3OH;6;1.4311;1.4320", "16;H2O;7;0.9200;1.4000", "17;ACOH;8;0.8952;0.6800", "18;CH3CO;9;1.6724;1.4880",
      "19;CH2CO;9;1.4457;1.1800", "20;CHO;10;0.9980;0.9480", "21;CH3COO;11;1.9031;1.7280", "22;CH2COO;11;1.6764;1.4200",
      "23;HCOO;12;1.2420;1.1880", "24;CH3O;13;1.1450;1.0880", "25;CH2O;13;0.9183;0.7800", "26;CHO;13;0.6908;0.4680",
      "27;THF;13;0.9183;1.1000", "28;CH3NH2;14;1.5959;1.5440", "29;CH2NH2;14;1.3692;1.2360",
      "30;CHNH2;14;1.1417;0.9240", "31;CH3NH;15;1.4337;1.2440", "32;CH2NH;15;1.2070;0.9360", "33;CHNH;15;0.9795;0.6240",
      "34;CH3N;16;1.1865;0.9400", "35;CH2N;16;0.9597;0.6320", "36;ACNH2;17;1.0600;0.8160", "37;C5H5N;18;2.9993;2.1130",
      "38;C5H4N;18;2.8332;1.8330", "39;C5H3N;18;2.6670;1.5530", "40;CH3CN;19;1.8701;1.7240",
      "41;CH2CN;19;1.6434;1.4160", "42;COOH;20;1.3013;1.2240", "43;HCOOH;20;1.5280;1.5320", "44;CH2CL;21;1.4654;1.2640",
      "45;CHCL;21;1.2380;0.9520", "46;CCL;21;1.0106;0.7240", "47;CH2CL2;22;2.2564;1.9880", "48;CHCL2;22;2.0606;1.6840",
      "49;CCL2;22;1.8016;1.4480", "50;CHCL3;23;2.8700;2.4100", "51;CCL3;23;2.6401;2.1840", "52;CCL4;24;3.3900;2.9100",
      "53;ACCL;25;1.1562;0.8440", "54;CH3NO2;26;2.0086;1.8680", "55;CH2NO2;26;1.7818;1.5600",
      "56;CHNO2;26;1.5544;1.2480", "57;ACNO2;27;1.4199;1.1040", "58;CS2;28;2.0570;1.6500", "59;CH3SH;29;1.8770;1.6760",
      "60;CH2SH;29;1.6510;1.3680", "61;FURFURAL;30;3.1680;2.4840", "62;DOH;31;2.4088;2.2480", "63;I;32;1.2640;0.9920",
      "64;BR;33;0.9492;0.8320", "70;C=C;2;0.6605;0.4850" };

  /**
   * Molar mass implied by a subgroup, encoded as "secondary;grams per mole". Only subgroups with an unambiguous
   * composition are listed; a component using anything else is not mass-checked.
   */
  private static final String[] SUBGROUP_MASSES = { "1;15.0345", "2;14.0266", "3;13.0186", "4;12.0110", "5;27.0453",
      "6;26.0373", "7;26.0373", "8;25.0294", "70;24.0220", "9;13.0186", "10;12.0110", "11;27.0453", "12;26.0373",
      "13;25.0294", "14;17.0073", "15;32.0419", "16;18.0153", "24;31.0339", "25;30.0260", "26;29.0180", "62;62.0678",
      "120;17.0305", "121;44.0095", "122;16.0425", "123;31.9988", "124;39.9480", "125;28.0134", "126;34.0809",
      "127;2.0159", "128;28.0101", "134;30.0690", "136;14.0266", "137;13.0186", "138;12.0110", "139;62.0678",
      "140;150.1730" };

  /**
   * Fails when the UNIFAC tables contain an inconsistency that is not already recorded in the baseline.
   */
  @Test
  public void unifacTablesHaveNoUnrecordedInconsistencies() {
    Set<String> findings = screen();
    Set<String> baseline = readBaseline();
    Set<String> unrecorded = new TreeSet<String>(findings);
    unrecorded.removeAll(baseline);
    if (!unrecorded.isEmpty()) {
      for (String finding : unrecorded) {
        logger.error("unrecorded UNIFAC inconsistency: {}", finding.replace('\t', ' '));
      }
    }
    assertTrue(unrecorded.isEmpty(), "New UNIFAC table inconsistencies found: " + describe(unrecorded));
  }

  /**
   * Fails when the baseline still lists an issue that has since been fixed, so the baseline can only shrink.
   */
  @Test
  public void unifacBaselineListsNothingAlreadyFixed() {
    Set<String> findings = screen();
    Set<String> stale = new TreeSet<String>(readBaseline());
    stale.removeAll(findings);
    if (!stale.isEmpty()) {
      for (String entry : stale) {
        logger.error("stale UNIFAC baseline entry: {}", entry.replace('\t', ' '));
      }
    }
    assertTrue(stale.isEmpty(), "Baseline lists issues that no longer exist, remove them: " + describe(stale));
  }

  /**
   * Screens every UNIFAC table and returns the findings.
   *
   * @return findings as "category TAB subject", never null
   */
  private Set<String> screen() {
    Set<String> findings = new TreeSet<String>();

    Map<String, Map<String, String>> groupParams = new LinkedHashMap<String, Map<String, String>>();
    List<Map<String, String>> paramRows = readTable(GROUP_PARAM_RESOURCE);
    for (Map<String, String> row : paramRows) {
      String secondary = row.get("Secondary").trim();
      groupParams.put(secondary, row);
    }
    Map<String, String[]> reference = buildReference();
    for (Map.Entry<String, String[]> entry : reference.entrySet()) {
      Map<String, String> row = groupParams.get(entry.getKey());
      if (row == null) {
        continue;
      }
      String[] expected = entry.getValue();
      boolean bad = Math.abs(Double.parseDouble(row.get("VolumeR")) - Double.parseDouble(expected[2])) > RQ_TOLERANCE;
      bad = bad || Math.abs(Double.parseDouble(row.get("SurfAreaQ")) - Double.parseDouble(expected[3])) > RQ_TOLERANCE;
      bad = bad || !row.get("Main").trim().equals(expected[1]);
      if (bad) {
        findings.add("group_param_mismatch\tsub" + entry.getKey() + " " + row.get("Name").trim());
      }
    }

    Map<String, Double> masses = buildMasses();
    Map<String, Double> componentMass = new HashMap<String, Double>();
    for (Map<String, String> row : readTable(COMPONENT_RESOURCE)) {
      componentMass.put(row.get("NAME").trim(), Double.parseDouble(row.get("MOLARMASS")));
    }

    for (String resource : COMPONENT_TABLES) {
      String table = resource.substring(resource.lastIndexOf('/') + 1).replace(".csv", "");
      List<Map<String, String>> rows = readTable(resource);

      Map<String, Integer> seen = new TreeMap<String, Integer>();
      for (Map<String, String> row : rows) {
        String name = row.get("Name").trim();
        Integer count = seen.get(name);
        seen.put(name, count == null ? 1 : count.intValue() + 1);
      }
      for (Map.Entry<String, Integer> entry : seen.entrySet()) {
        if (entry.getValue().intValue() > 1) {
          findings.add("duplicate_component\t" + table + "/" + entry.getKey());
        }
      }

      for (Map<String, String> row : rows) {
        String name = row.get("Name").trim();
        Map<String, Integer> groups = assignmentOf(row);
        if (groups.isEmpty()) {
          findings.add("component_no_groups\t" + table + "/" + name);
          continue;
        }
        boolean massable = true;
        for (String subgroup : groups.keySet()) {
          if (!groupParams.containsKey(subgroup)) {
            findings.add("unknown_subgroup\t" + table + "/" + name + " sub" + subgroup);
          }
          if (!masses.containsKey(subgroup)) {
            massable = false;
          }
        }
        Double declared = componentMass.get(name);
        if (declared == null) {
          findings.add("not_in_comp\t" + table + "/" + name);
          continue;
        }
        if (!massable) {
          continue;
        }
        double implied = 0.0;
        for (Map.Entry<String, Integer> entry : groups.entrySet()) {
          implied += masses.get(entry.getKey()).doubleValue() * entry.getValue().intValue();
        }
        if (Math.abs(implied - declared.doubleValue()) > MASS_TOLERANCE) {
          findings.add("molar_mass_mismatch\t" + table + "/" + name);
        }
      }

      for (Map<String, String> row : rows) {
        String name = row.get("Name").trim();
        Map<String, Integer> groups = assignmentOf(row);
        boolean bareAromatic = count(groups, BARE_AROMATIC_CARBON) > 0;
        boolean aromaticAlkane = false;
        for (String subgroup : AROMATIC_ALKANE_SUBGROUPS) {
          aromaticAlkane = aromaticAlkane || count(groups, subgroup) > 0;
        }
        boolean aliphatic = false;
        for (String subgroup : ALIPHATIC_SUBGROUPS) {
          aliphatic = aliphatic || count(groups, subgroup) > 0;
        }
        boolean cyclic = false;
        for (String subgroup : CYCLIC_SUBGROUPS) {
          cyclic = cyclic || count(groups, subgroup) > 0;
        }
        if (bareAromatic && aliphatic && !aromaticAlkane) {
          findings.add("aromatic_group_convention\t" + table + "/" + name);
        }
        if (UMRPRU_TABLE.equals(table) && looksLikeRing(name) && aliphatic && !cyclic) {
          findings.add("ring_group_convention\t" + table + "/" + name);
        }
      }
    }
    return findings;
  }

  /**
   * Returns how many times a subgroup occurs in an assignment.
   *
   * @param groups assignment to inspect
   * @param subgroup subgroup number to count
   * @return the occurrence count, zero when absent
   */
  private int count(Map<String, Integer> groups, String subgroup) {
    Integer value = groups.get(subgroup);
    return value == null ? 0 : value.intValue();
  }

  /**
   * Returns whether a component name marks it as a naphthene in this database.
   *
   * @param name component name from a UNIFAC table
   * @return true when the name carries a ring marker
   */
  private boolean looksLikeRing(String name) {
    String lowered = name.toLowerCase(Locale.ROOT);
    return lowered.startsWith("c-c") || lowered.startsWith("cy-c") || lowered.contains("cy-c")
        || lowered.contains("cyc") || lowered.contains("chexane");
  }

  /**
   * Extracts the populated subgroup columns of a component row.
   *
   * @param row one row of a UNIFAC component table
   * @return map of subgroup number to occurrence count, never null
   */
  private Map<String, Integer> assignmentOf(Map<String, String> row) {
    Map<String, Integer> groups = new TreeMap<String, Integer>();
    for (Map.Entry<String, String> entry : row.entrySet()) {
      String column = entry.getKey();
      String value = entry.getValue();
      if (column == null || !column.startsWith("sub") || value == null) {
        continue;
      }
      String trimmed = value.trim();
      if (trimmed.isEmpty() || "0".equals(trimmed)) {
        continue;
      }
      groups.put(column.substring(3), Integer.valueOf(Integer.parseInt(trimmed)));
    }
    return groups;
  }

  /**
   * Builds the DDBST reference lookup.
   *
   * @return map of subgroup number to {name, main group, R, Q}, never null
   */
  private Map<String, String[]> buildReference() {
    Map<String, String[]> reference = new LinkedHashMap<String, String[]>();
    for (String entry : DDBST_GROUPS) {
      String[] parts = entry.split(";");
      reference.put(parts[0], new String[] { parts[1], parts[2], parts[3], parts[4] });
    }
    return reference;
  }

  /**
   * Builds the subgroup molar mass lookup.
   *
   * @return map of subgroup number to molar mass in g/mol, never null
   */
  private Map<String, Double> buildMasses() {
    Map<String, Double> masses = new LinkedHashMap<String, Double>();
    for (String entry : SUBGROUP_MASSES) {
      String[] parts = entry.split(";");
      masses.put(parts[0], Double.valueOf(Double.parseDouble(parts[1])));
    }
    return masses;
  }

  /**
   * Reads a classpath CSV table into a list of column-to-value maps.
   *
   * @param resource classpath location of the table
   * @return rows of the table, never null
   */
  private List<Map<String, String>> readTable(String resource) {
    List<String> lines = readLines(resource);
    List<String> header = splitCsvLine(lines.get(0));
    List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
    for (int i = 1; i < lines.size(); i++) {
      if (lines.get(i).trim().isEmpty()) {
        continue;
      }
      List<String> fields = splitCsvLine(lines.get(i));
      Map<String, String> row = new LinkedHashMap<String, String>();
      for (int j = 0; j < header.size() && j < fields.size(); j++) {
        row.put(header.get(j), fields.get(j));
      }
      rows.add(row);
    }
    return rows;
  }

  /**
   * Reads the baseline of accepted findings.
   *
   * @return baseline entries as "category TAB subject", never null
   */
  private Set<String> readBaseline() {
    Set<String> baseline = new TreeSet<String>();
    for (String line : readLines(BASELINE_RESOURCE)) {
      if (!line.trim().isEmpty()) {
        baseline.add(line);
      }
    }
    return baseline;
  }

  /**
   * Reads a UTF-8 classpath resource as lines, stripping a leading byte order mark.
   *
   * @param resource classpath location to read
   * @return the lines of the resource, never null
   * @throws IllegalStateException if the resource is missing or unreadable
   */
  private List<String> readLines(String resource) {
    List<String> lines = new ArrayList<String>();
    InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
    if (stream == null) {
      throw new IllegalStateException("resource not found on classpath: " + resource);
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
    try {
      String line = reader.readLine();
      boolean first = true;
      while (line != null) {
        if (first && line.startsWith("\uFEFF")) {
          line = line.substring(1);
        }
        first = false;
        lines.add(line);
        line = reader.readLine();
      }
    } catch (IOException ex) {
      throw new IllegalStateException("could not read " + resource, ex);
    } finally {
      try {
        reader.close();
      } catch (IOException ex) {
        logger.warn("could not close {}", resource, ex);
      }
    }
    return lines;
  }

  /**
   * Splits a CSV line, honouring double quotes so that names containing commas stay intact.
   *
   * @param line raw CSV line
   * @return the unquoted fields of the line, never null
   */
  private List<String> splitCsvLine(String line) {
    List<String> fields = new ArrayList<String>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char character = line.charAt(i);
      if (character == '"') {
        quoted = !quoted;
      } else if (character == ',' && !quoted) {
        fields.add(current.toString());
        current.setLength(0);
      } else {
        current.append(character);
      }
    }
    fields.add(current.toString());
    return fields;
  }

  /**
   * Renders findings for an assertion message.
   *
   * @param entries findings to render
   * @return a readable, newline separated listing, never null
   */
  private String describe(Set<String> entries) {
    StringBuilder text = new StringBuilder();
    for (String entry : entries) {
      text.append("\n  ").append(entry.replace('\t', ' '));
    }
    return text.toString();
  }
}
