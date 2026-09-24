package neqsim.thermo.spec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict, versioned test-only contract. Expected outcomes are never inferred from calculated values. */
final class ModelSpec {
  static final String RESOURCE = "/neqsim/thermo/spec/cases.tsv";
  static final String VERSION = "# neqsim-model-spec-v1";
  static final String HEADER = "id\tfixture\tproperty\tcomponents\ttemperatureK\tpressureBar\tphase\tcomponentIndex"
      + "\tmixingRule\toperation\tunit\tbasis\toutcome\texpected\tabsTol\trelTol\tminK\tmaxK\tsource\tprovenance\treason";

  /** Curated drivers, not arbitrary class names or reflective method calls. */
  enum Fixture {
    SATURATION, ANTOINE_ANALYTIC, WILSON_ANALYTIC, WILSON_PHASE, NRTL_ANALYTIC, NRTL_PHASE, UNIFAC, PSRK, UMR, SRK, PR,
    SRK_PHASE, PR_PHASE, UNIFAC_PHASE, PSRK_PHASE, UMR_PHASE, GERG, GERG_PHASE, UNIQUAC
  }

  /** Properties with distinct dimensional and sign contracts. */
  enum Property {
    PSAT, DPSAT_DT, T_SAT, GAMMA, LN_GAMMA, GEX, GROUP_R, GROUP_Q, INTERACTION_A, Z, PHI, HID, MOLAR_MASS,
    MOLAR_DENSITY, DPD_DENSITY, D2PD_DENSITY2, DPD_T, INTERNAL_ENERGY, ENTHALPY, ENTROPY, CV, CP, SOUND_SPEED,
    GIBBS_ENERGY, JT, KAPPA
  }

  /** An unsupported implementation is not an unavailable correlation or a numerical success. */
  enum Outcome {
    VALUE, UNAVAILABLE, UNSUPPORTED
  }

  final String id;
  final Fixture fixture;
  final Property property;
  final Map<String, Double> components = new LinkedHashMap<String, Double>();
  final double temperature;
  final double pressure;
  final String phase;
  final int componentIndex;
  final String mixingRule;
  final String operation;
  final String unit;
  final Outcome outcome;
  final double expected;
  final double absTol;
  final double relTol;
  final String source;
  final String provenance;
  final String reason;

  private ModelSpec(String[] row) {
    require(row.length == 21, "expected 21 columns, got " + row.length);
    for (String cell : row) {
      require(!cell.trim().isEmpty(), "empty cell");
      require(cell.equals(cell.trim()), "surrounding whitespace: " + cell);
    }
    id = row[0];
    require(id.matches("[a-z0-9-]+"), "invalid case id: " + id);
    fixture = Fixture.valueOf(row[1]);
    property = Property.valueOf(row[2]);
    double sum = 0.0;
    for (String entry : row[3].split(";", -1)) {
      String[] pair = entry.split("=", -1);
      require(pair.length == 2 && !pair[0].isEmpty(), "invalid composition: " + entry);
      double amount = finite(pair[1]);
      require(amount > 0.0 && !components.containsKey(pair[0]), "invalid/duplicate component: " + entry);
      components.put(pair[0], amount);
      sum += amount;
    }
    require(Math.abs(sum - 1.0) <= 1e-12, "composition must sum to one mole");
    temperature = finite(row[4]);
    pressure = finite(row[5]);
    require(temperature > 0.0 && pressure > 0.0, "T and absolute P must be positive");
    phase = row[6];
    componentIndex = Integer.parseInt(row[7]);
    require(componentIndex >= 0 && componentIndex < components.size(), "component index outside fixture");
    mixingRule = row[8];
    operation = row[9];
    unit = row[10];
    require("molar".equals(row[11]), "only the declared molar basis is supported");
    outcome = Outcome.valueOf(row[12]);
    expected = outcome == Outcome.VALUE ? finite(row[13]) : 0.0;
    require(outcome == Outcome.VALUE || "-".equals(row[13]), "absence cannot have a numerical reference");
    absTol = finite(row[14]);
    relTol = finite(row[15]);
    require(absTol >= 0.0 && relTol >= 0.0, "negative tolerance");
    require(Double.isFinite(absTol + relTol * Math.abs(expected)), "overflowing tolerance");
    double min = finite(row[16]);
    double max = finite(row[17]);
    require(min > 0.0 && min <= temperature && temperature <= max, "outside declared test domain");
    source = row[18];
    provenance = row[19];
    reason = row[20];
    require(source.startsWith("https://") || source.startsWith("analytic:"), "missing source locator");
    require(!"-".equals(provenance), "missing provenance");
    require((outcome == Outcome.VALUE) == "-".equals(reason), "outcome/reason disagreement");
    ModelSpecFixtures.validate(this);
  }

  static List<ModelSpec> load() throws IOException {
    InputStream stream = ModelSpec.class.getResourceAsStream(RESOURCE);
    require(stream != null, "missing catalog " + RESOURCE);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return read(reader);
    }
  }

  static List<ModelSpec> parse(String catalog) throws IOException {
    return read(new BufferedReader(new StringReader(catalog)));
  }

  private static List<ModelSpec> read(BufferedReader reader) throws IOException {
    require(VERSION.equals(reader.readLine()), "unknown catalog version");
    require(HEADER.equals(reader.readLine()), "unknown/missing columns");
    List<ModelSpec> cases = new ArrayList<ModelSpec>();
    Set<String> ids = new HashSet<String>();
    String line;
    int number = 2;
    while ((line = reader.readLine()) != null) {
      number++;
      try {
        ModelSpec spec = new ModelSpec(line.split("\t", -1));
        require(ids.add(spec.id), "duplicate id " + spec.id);
        cases.add(spec);
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("catalog line " + number + ": " + ex.getMessage(), ex);
      }
    }
    require(!cases.isEmpty(), "empty catalog");
    return cases;
  }

  private static double finite(String value) {
    double parsed = Double.parseDouble(value);
    require(Double.isFinite(parsed), "nonfinite number: " + value);
    return parsed;
  }

  static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  @Override
  public String toString() {
    return id + " [" + fixture + "/" + property + ", T=" + temperature + " K, P=" + pressure + " bar(a)]";
  }
}
