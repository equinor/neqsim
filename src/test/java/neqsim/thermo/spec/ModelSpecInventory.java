package neqsim.thermo.spec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import neqsim.thermo.component.ComponentSrk;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/** Inventory reconciliation discovers types only; calculations always use explicit typed adapters. */
final class ModelSpecInventory {
  static final String RESOURCE = "/neqsim/thermo/spec/inventory.tsv";
  static final String HEADER = "type\tkind\tstatus\tfixtures\tproperties\tdomain\tissue\treview";

  enum Kind {
    SYSTEM, PHASE, CORRELATION
  }

  enum Status {
    PARTIAL, DEBT, UNSUPPORTED
  }

  static final class Entry {
    final String type;
    final Kind kind;
    final Status status;
    final Set<ModelSpec.Fixture> fixtures = EnumSet.noneOf(ModelSpec.Fixture.class);
    final Set<ModelSpec.Property> properties = EnumSet.noneOf(ModelSpec.Property.class);
    final String domain;
    final String issue;
    final String review;

    Entry(String[] row) {
      ModelSpec.require(row.length == 8, "inventory requires eight columns");
      for (String cell : row) {
        ModelSpec.require(!cell.trim().isEmpty() && cell.equals(cell.trim()), "invalid inventory cell");
      }
      type = row[0];
      kind = Kind.valueOf(row[1]);
      status = Status.valueOf(row[2]);
      domain = row[5];
      issue = row[6];
      review = row[7];
      ModelSpec.require(issue.matches("https://github.com/equinor/neqsim/issues/[1-9][0-9]*"), "missing debt issue");
      ModelSpec.require(review.length() > 10 && !"-".equals(review), "missing review condition");
      if (status == Status.DEBT) {
        ModelSpec.require("-".equals(row[3]) && "-".equals(row[4]) && "not-qualified".equals(domain),
            "debt is not a supported or unsupported capability");
      } else {
        for (String fixture : row[3].split(";", -1)) {
          ModelSpec.require(fixtures.add(ModelSpec.Fixture.valueOf(fixture)), "duplicate fixture");
        }
        for (String property : row[4].split(";", -1)) {
          ModelSpec.require(properties.add(ModelSpec.Property.valueOf(property)), "duplicate property");
        }
        ModelSpec.require("catalog-only".equals(domain), "qualified domains must come from referenced cases");
      }
    }
  }

  private ModelSpecInventory() {
  }

  static List<Entry> load() throws IOException {
    InputStream stream = ModelSpecInventory.class.getResourceAsStream(RESOURCE);
    ModelSpec.require(stream != null, "missing inventory");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return read(reader);
    }
  }

  static List<Entry> parse(String text) throws IOException {
    return read(new BufferedReader(new StringReader(text)));
  }

  private static List<Entry> read(BufferedReader reader) throws IOException {
    ModelSpec.require("# neqsim-model-inventory-v1".equals(reader.readLine()), "unknown inventory version");
    ModelSpec.require(HEADER.equals(reader.readLine()), "invalid inventory header");
    List<Entry> entries = new ArrayList<Entry>();
    Set<String> names = new TreeSet<String>();
    String line;
    int number = 2;
    while ((line = reader.readLine()) != null) {
      number++;
      try {
        Entry entry = new Entry(line.split("\t", -1));
        ModelSpec.require(names.add(entry.type), "duplicate inventory type " + entry.type);
        entries.add(entry);
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("inventory line " + number + ": " + ex.getMessage(), ex);
      }
    }
    ModelSpec.require(!entries.isEmpty(), "empty inventory");
    return entries;
  }

  static Map<String, Kind> discover() throws IOException, URISyntaxException, ClassNotFoundException {
    Path classes = Paths.get(SystemInterface.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    ModelSpec.require(Files.isDirectory(classes), "inventory gate requires compiled project classes, not a jar");
    Map<String, Kind> result = new TreeMap<String, Kind>();
    discoverPackage(classes, "neqsim/thermo/system", SystemInterface.class, Kind.SYSTEM, result);
    discoverPackage(classes, "neqsim/thermo/phase", PhaseInterface.class, Kind.PHASE, result);
    // Explicit standalone correlation entry point; this is not an inventory of all component APIs.
    result.put(ComponentSrk.class.getName(), Kind.CORRELATION);
    return result;
  }

  private static void discoverPackage(Path root, String packagePath, Class<?> contract, Kind kind,
      Map<String, Kind> result) throws IOException, ClassNotFoundException {
    try (Stream<Path> paths = Files.walk(root.resolve(packagePath))) {
      for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".class"))::iterator) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        String name = relative.substring(0, relative.length() - 6).replace('/', '.');
        // No constructor, static initializer, thermodynamic operation or arbitrary method invocation.
        Class<?> type = Class.forName(name, false, ModelSpecInventory.class.getClassLoader());
        if (contract.isAssignableFrom(type) && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
          result.put(name, kind);
        }
      }
    }
  }

  static void validate(List<Entry> entries, Map<String, Kind> discovered, List<ModelSpec> cases) {
    Set<String> declared = new TreeSet<String>();
    Set<ModelSpec.Fixture> bound = EnumSet.noneOf(ModelSpec.Fixture.class);
    Set<String> baselineDebt = initialDebt();
    for (Entry entry : entries) {
      ModelSpec.require(declared.add(entry.type), "duplicate inventory type " + entry.type);
      ModelSpec.require(entry.kind == discovered.get(entry.type), "unknown or wrongly classified type " + entry.type);
      if (entry.status == Status.DEBT) {
        ModelSpec.require(baselineDebt.contains(entry.type),
            "new type cannot expand initial coverage debt: " + entry.type);
        continue;
      }
      Set<ModelSpec.Property> actualProperties = EnumSet.noneOf(ModelSpec.Property.class);
      int count = 0;
      boolean value = false;
      for (ModelSpec.Fixture fixture : entry.fixtures) {
        int fixtureCases = 0;
        ModelSpec.require(bound.add(fixture), "fixture bound twice: " + fixture);
        ModelSpec.require(ModelSpecFixtures.type(fixture).getName().equals(entry.type),
            "wrong fixture type: " + fixture);
        for (ModelSpec spec : cases) {
          if (spec.fixture == fixture) {
            fixtureCases++;
            count++;
            actualProperties.add(spec.property);
            value |= spec.outcome == ModelSpec.Outcome.VALUE;
            ModelSpec.require(entry.status != Status.UNSUPPORTED || spec.outcome == ModelSpec.Outcome.UNSUPPORTED,
                "unsupported entry has non-unsupported case " + spec.id);
          }
        }
        ModelSpec.require(fixtureCases > 0, "fixture has no catalog cases: " + fixture);
      }
      ModelSpec.require(count > 0 && actualProperties.equals(entry.properties),
          "missing/property-mismatched cases: " + entry.type);
      ModelSpec.require(entry.status != Status.PARTIAL || value, "partial support requires numerical evidence");
    }
    Set<String> missing = new TreeSet<String>(discovered.keySet());
    missing.removeAll(declared);
    ModelSpec.require(missing.isEmpty(), "unclassified concrete types: " + missing);
    ModelSpec.require(bound.equals(EnumSet.allOf(ModelSpec.Fixture.class)), "unbound curated fixtures");
  }

  private static Set<String> initialDebt() {
    // Frozen at c7cde46a78f18922534c18ec1241d61abac0a08c. Never auto-refresh from discovery.
    try (InputStream stream = ModelSpecInventory.class.getResourceAsStream("/neqsim/thermo/spec/initial-debt.txt")) {
      ModelSpec.require(stream != null, "missing initial debt snapshot");
      Set<String> result = new TreeSet<String>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          ModelSpec.require(result.add(line), "duplicate initial debt type");
        }
      }
      ModelSpec.require(!result.isEmpty(), "empty initial debt snapshot");
      return result;
    } catch (IOException ex) {
      throw new IllegalArgumentException("cannot read initial debt snapshot", ex);
    }
  }

  static void report(List<Entry> entries, List<ModelSpec> cases) throws IOException {
    List<String> lines = new ArrayList<String>();
    lines.add(HEADER + "\tcaseCount\tcaseIds");
    int debt = 0;
    int unsupported = 0;
    for (Entry entry : entries) {
      List<String> ids = new ArrayList<String>();
      for (ModelSpec spec : cases) {
        if (entry.fixtures.contains(spec.fixture)) {
          ids.add(spec.id);
        }
      }
      debt += entry.status == Status.DEBT ? 1 : 0;
      unsupported += entry.status == Status.UNSUPPORTED ? 1 : 0;
      lines.add(entry.type + "\t" + entry.kind + "\t" + entry.status + "\t" + entry.fixtures + "\t" + entry.properties
          + "\t" + entry.domain + "\t" + entry.issue + "\t" + entry.review + "\t" + ids.size() + "\t"
          + String.join(";", ids));
    }
    Path directory = Paths.get("target", "model-spec");
    Files.createDirectories(directory);
    Files.write(directory.resolve("coverage.tsv"), lines, StandardCharsets.UTF_8);
    Files.write(directory.resolve("coverage.md"),
        Arrays.asList("### Model specification coverage", "",
            "Classified concrete types: " + entries.size() + ". Catalog cases: " + cases.size() + ".", "",
            "Partially covered types: " + (entries.size() - debt - unsupported) + "; unsupported contracts: "
                + unsupported + "; types with unqualified catalog coverage: " + debt + ".",
            "", "PARTIAL qualifies only the listed cases/domains, not every model property. DEBT is not unsupported.",
            "See coverage.tsv for every type/property/case mapping and #3792 for family qualification."),
        StandardCharsets.UTF_8);
  }
}
