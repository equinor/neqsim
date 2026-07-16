package neqsim.process.engineering.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/** Versioned schema definitions for canonical engineering compiler artifacts. */
public final class EngineeringSchemaCatalog {
  public static final String CATALOG_VERSION = "neqsim_engineering_schema_catalog.v1";
  public static final String GRAPH = "neqsim_engineering_graph.v1";
  public static final String CONNECTIVITY = "neqsim_engineering_connectivity.v1";
  public static final String CALCULATION_DAG = "neqsim_engineering_calculation_dag.v1";
  public static final String DESIGN_CASE_MATRIX = "neqsim_engineering_design_case_matrix.v1";
  public static final String DISCIPLINE_PACKAGE = "neqsim_engineering_discipline_package.v1";
  public static final String DESIGN_CASE_ENVELOPE = "neqsim_design_case_envelope.v1";
  public static final String EQUIPMENT_REGISTER = "neqsim_equipment_register.v1";
  public static final String LINE_REGISTER = "neqsim_line_register.v1";
  public static final String INSTRUMENT_REGISTER = "neqsim_instrument_register.v1";
  public static final String COMPILER_MANIFEST = "neqsim_engineering_compiler_manifest.v1";
  public static final String REVISION_DIFF = "neqsim_engineering_revision_diff.v1";
  public static final String VALIDATION_REPORT = "neqsim_engineering_validation_report.v1";

  /** One immutable schema registration. */
  public static final class Definition {
    private final String artifactName;
    private final String schemaVersion;
    private final String schemaUri;
    private final String schemaFile;

    Definition(String artifactName, String schemaVersion, String schemaUri, String schemaFile) {
      this.artifactName = artifactName;
      this.schemaVersion = schemaVersion;
      this.schemaUri = schemaUri;
      this.schemaFile = schemaFile;
    }

    public String getArtifactName() {
      return artifactName;
    }

    public String getSchemaVersion() {
      return schemaVersion;
    }

    public String getSchemaUri() {
      return schemaUri;
    }

    public String getSchemaFile() {
      return schemaFile;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("artifact", artifactName);
      value.put("schemaVersion", schemaVersion);
      value.put("schemaUri", schemaUri);
      value.put("schemaFile", "schemas/" + schemaFile);
      return value;
    }
  }

  private static final String RESOURCE_ROOT = "/neqsim/process/engineering/schema/";
  private static final List<Definition> DEFINITIONS;
  private static final Map<String, Definition> BY_ARTIFACT;
  private static final Map<String, Definition> BY_VERSION;

  static {
    List<Definition> values = new ArrayList<Definition>();
    values.add(definition("engineering-model.json", GRAPH, "engineering-model.schema.json"));
    values.add(definition("engineering-connectivity.json", CONNECTIVITY, "engineering-connectivity.schema.json"));
    values.add(
        definition("engineering-calculation-dag.json", CALCULATION_DAG, "engineering-calculation-dag.schema.json"));
    values.add(definition("engineering-design-case-matrix.json", DESIGN_CASE_MATRIX,
        "engineering-design-case-matrix.schema.json"));
    values.add(definition("engineering-discipline-package.json", DISCIPLINE_PACKAGE,
        "engineering-discipline-package.schema.json"));
    values.add(definition("design-case-envelope.json", DESIGN_CASE_ENVELOPE, "design-case-envelope.schema.json"));
    values.add(definition("equipment-register.json", EQUIPMENT_REGISTER, "equipment-register.schema.json"));
    values.add(definition("line-register.json", LINE_REGISTER, "line-register.schema.json"));
    values.add(definition("instrument-register.json", INSTRUMENT_REGISTER, "instrument-register.schema.json"));
    values.add(definition("engineering-compiler-manifest.json", COMPILER_MANIFEST, "compiler-manifest.schema.json"));
    values.add(definition("engineering-revision-diff.json", REVISION_DIFF, "revision-diff.schema.json"));
    values.add(definition("engineering-validation-report.json", VALIDATION_REPORT, "validation-report.schema.json"));
    DEFINITIONS = Collections.unmodifiableList(values);
    Map<String, Definition> artifacts = new LinkedHashMap<String, Definition>();
    Map<String, Definition> versions = new LinkedHashMap<String, Definition>();
    for (Definition value : values) {
      artifacts.put(value.getArtifactName(), value);
      versions.put(value.getSchemaVersion(), value);
    }
    BY_ARTIFACT = Collections.unmodifiableMap(artifacts);
    BY_VERSION = Collections.unmodifiableMap(versions);
  }

  private EngineeringSchemaCatalog() {
  }

  public static List<Definition> getDefinitions() {
    return DEFINITIONS;
  }

  public static Definition forArtifact(String artifactName) {
    return BY_ARTIFACT.get(artifactName);
  }

  public static Definition forVersion(String schemaVersion) {
    return BY_VERSION.get(schemaVersion);
  }

  public static String schemaUri(String schemaVersion) {
    Definition definition = forVersion(schemaVersion);
    if (definition == null) {
      throw new IllegalArgumentException("Unsupported engineering schema version " + schemaVersion);
    }
    return definition.getSchemaUri();
  }

  /** Writes the schema catalog and bundled schemas into an engineering package. */
  public static List<String> writeSchemas(Path outputDirectory) throws IOException {
    if (outputDirectory == null) {
      throw new IllegalArgumentException("outputDirectory must not be null");
    }
    Path schemaDirectory = outputDirectory.resolve("schemas");
    Files.createDirectories(schemaDirectory);
    List<String> written = new ArrayList<String>();
    for (Definition definition : DEFINITIONS) {
      Path target = schemaDirectory.resolve(definition.getSchemaFile());
      InputStream source = EngineeringSchemaCatalog.class
          .getResourceAsStream(RESOURCE_ROOT + definition.getSchemaFile());
      if (source == null) {
        throw new IOException("Bundled engineering schema is missing: " + definition.getSchemaFile());
      }
      try {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        source.close();
      }
      written.add("schemas/" + definition.getSchemaFile());
    }
    Map<String, Object> catalog = new LinkedHashMap<String, Object>();
    catalog.put("schemaVersion", CATALOG_VERSION);
    List<Map<String, Object>> schemas = new ArrayList<Map<String, Object>>();
    for (Definition definition : DEFINITIONS) {
      schemas.add(definition.toMap());
    }
    catalog.put("schemas", schemas);
    Files.write(outputDirectory.resolve("engineering-schema-catalog.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(catalog).getBytes(StandardCharsets.UTF_8));
    written.add("engineering-schema-catalog.json");
    return written;
  }

  public static List<Map<String, Object>> manifestEntries() {
    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
    for (Definition definition : DEFINITIONS) {
      values.add(definition.toMap());
    }
    return values;
  }

  private static Definition definition(String artifact, String version, String file) {
    String stem = file.substring(0, file.length() - ".schema.json".length());
    return new Definition(artifact, version, "urn:neqsim:schema:" + stem + ":v1", file);
  }
}
