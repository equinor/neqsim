package neqsim.process.engineering.pid;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.dexpi.Dexpi201DynamicBindingReader;
import neqsim.process.processmodel.dexpi.Dexpi201XmlWriter;
import neqsim.process.processmodel.dexpi.DexpiXmlReader;
import neqsim.process.processmodel.dexpi.DexpiXmlReaderException;

/**
 * Reopens a governed professional P&amp;ID package through its canonical DEXPI 2.0.1 model and executable bindings.
 *
 * <p>
 * The reader verifies every package-manifest entry before restoring the canonical P&amp;ID and revision-bound
 * simulation bindings. It then applies the native DEXPI 2.0.1 relationship checks and exposes the supported-subset
 * import diagnostics without converting warnings into a successful lossless claim. The executable process itself is
 * supplied through the caller's governed {@link EngineeringProject}; the reader does not infer fluids, inventories,
 * tuning, fail action, safety integrity or missing process physics from drawing metadata.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class ProfessionalPidPackageReader {
  private static final String DEXPI_FILE = "plant.dexpi.xml";
  private static final String MODEL_FILE = "pid-design-model.json";
  private static final String BINDINGS_FILE = "pid-simulation-bindings.json";
  private static final String MANIFEST_FILE = "package-manifest.json";

  /** Prevents utility construction. */
  private ProfessionalPidPackageReader() {
  }

  /** Immutable controlled-package intake result. */
  public static final class Result {
    private final PidDesignModel model;
    private final PidSimulationBindings bindings;
    private final Dexpi201DynamicBindingReader.Result dexpiEvidence;
    private final DexpiXmlReader.ImportResult importEvidence;
    private final List<String> diagnostics;
    private final String packageManifestSha256;
    private final String semanticFingerprint;

    /**
     * Captures verified package content and semantic evidence.
     *
     * @param restoredModel restored canonical P&amp;ID model
     * @param restoredBindings verified executable bindings
     * @param nativeEvidence native DEXPI relationship evidence
     * @param supportedImport supported-subset import and diagnostics
     * @param intakeDiagnostics ordered package-level semantic diagnostics
     * @param manifestSha256 exact package-manifest fingerprint
     */
    private Result(PidDesignModel restoredModel, PidSimulationBindings restoredBindings,
        Dexpi201DynamicBindingReader.Result nativeEvidence, DexpiXmlReader.ImportResult supportedImport,
        List<String> intakeDiagnostics, String manifestSha256) {
      model = restoredModel;
      bindings = restoredBindings;
      dexpiEvidence = nativeEvidence;
      importEvidence = supportedImport;
      diagnostics = Collections.unmodifiableList(new ArrayList<String>(intakeDiagnostics));
      packageManifestSha256 = manifestSha256;
      semanticFingerprint = fingerprint(semanticMap(this));
    }

    /** @return restored canonical P&amp;ID model */
    public PidDesignModel getModel() {
      return model;
    }

    /** @return restored revision-bound executable bindings */
    public PidSimulationBindings getBindings() {
      return bindings;
    }

    /** @return verified native DEXPI tag, signal and actuation evidence, or {@code null} when diagnostics block it */
    public Dexpi201DynamicBindingReader.Result getDexpiEvidence() {
      return dexpiEvidence;
    }

    /** @return supported-subset import evidence with explicit diagnostics */
    public DexpiXmlReader.ImportResult getImportEvidence() {
      return importEvidence;
    }

    /** @return stable ordered package-level semantic diagnostics */
    public List<String> getDiagnostics() {
      return diagnostics;
    }

    /** @return SHA-256 of the exact verified package manifest */
    public String getPackageManifestSha256() {
      return packageManifestSha256;
    }

    /** @return deterministic fingerprint of the supported canonical semantics */
    public String getSemanticFingerprint() {
      return semanticFingerprint;
    }

    /** @return whether the supported-subset import reported any warning or error */
    public boolean hasImportLosses() {
      return importEvidence.hasLosses() || !diagnostics.isEmpty();
    }

    /** @return deterministic intake evidence without live process objects */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_professional_pid_package_intake.v1");
      result.put("dexpiSpecificationVersion", "2.0.1");
      result.put("packageManifestSha256", packageManifestSha256);
      result.put("semanticFingerprint", semanticFingerprint);
      result.put("importHasLosses", Boolean.valueOf(hasImportLosses()));
      result.put("canonicalModel", model.toMap());
      result.put("simulationBindings", bindings.toMap());
      result.put("nativeDexpiEvidence", dexpiEvidence == null ? Collections.emptyMap() : dexpiEvidence.toMap());
      result.put("supportedImportEvidence", importEvidence.toMap());
      result.put("diagnostics", diagnostics);
      result.put("scope", "CONTROLLED_NEQSIM_PROFESSIONAL_PID_PACKAGE");
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("fitnessForConstruction", Boolean.FALSE);
      return result;
    }

    /** @return deterministic pretty-printed intake JSON */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }

  /** Deterministic supported-semantic comparison between two controlled package reads. */
  public static final class SemanticComparison implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String leftFingerprint;
    private final String rightFingerprint;
    private final List<String> differences;

    /**
     * Captures a semantic comparison.
     *
     * @param left left semantic fingerprint
     * @param right right semantic fingerprint
     * @param foundDifferences ordered difference identifiers
     */
    private SemanticComparison(String left, String right, List<String> foundDifferences) {
      leftFingerprint = left;
      rightFingerprint = right;
      differences = Collections.unmodifiableList(new ArrayList<String>(foundDifferences));
    }

    /** @return whether both packages have equal supported semantics and no import losses */
    public boolean isEquivalent() {
      return differences.isEmpty();
    }

    /** @return stable ordered semantic difference identifiers */
    public List<String> getDifferences() {
      return differences;
    }

    /** @return deterministic automation-friendly comparison evidence */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_professional_pid_semantic_comparison.v1");
      result.put("status", isEquivalent() ? "EQUIVALENT" : "NOT_EQUIVALENT");
      result.put("leftSemanticFingerprint", leftFingerprint);
      result.put("rightSemanticFingerprint", rightFingerprint);
      result.put("differences", differences);
      result.put("comparisonScope", "SUPPORTED_CANONICAL_AND_NATIVE_DEXPI_201_SEMANTICS");
      result.put("approvalStatus", "REVIEW_REQUIRED");
      return result;
    }

    /** @return deterministic pretty-printed comparison JSON */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }

  /**
   * Verifies and reopens one controlled package against an independently restored executable project.
   *
   * @param project governed project with the exact executable source, project ID and revision
   * @param packageDirectory professional P&amp;ID package directory
   * @return verified canonical model, bindings and DEXPI evidence
   * @throws IOException if a required artifact cannot be read or parsed
   */
  public static Result read(EngineeringProject project, Path packageDirectory) throws IOException {
    if (project == null || packageDirectory == null) {
      throw new IllegalArgumentException("project and packageDirectory are required");
    }
    Path directory = packageDirectory.toAbsolutePath().normalize();
    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException("Professional P&ID package directory does not exist: " + directory);
    }
    Path manifest = requiredFile(directory, MANIFEST_FILE);
    verifyManifest(directory, manifest);
    PidDesignModel model = PidDesignModel.fromJson(readUtf8(requiredFile(directory, MODEL_FILE)));
    String bindingJson = readUtf8(requiredFile(directory, BINDINGS_FILE));
    Path dexpi = requiredFile(directory, DEXPI_FILE);
    if (!Dexpi201XmlWriter.assess(dexpi).isSchemaAndProfileConformant()) {
      throw new IllegalArgumentException("Professional P&ID package requires conformant native DEXPI 2.0.1");
    }
    PidSimulationBindings bindings = PidSimulationBindings.fromJson(project, model, bindingJson);
    bindings.assertCurrent(project, model);
    List<String> diagnostics = new ArrayList<String>();
    Dexpi201DynamicBindingReader.Result nativeEvidence = null;
    try {
      nativeEvidence = Dexpi201DynamicBindingReader.read(dexpi, project, model, bindingJson);
      bindings = nativeEvidence.getBindings();
    } catch (IllegalArgumentException exception) {
      if (exception.getMessage() != null && (exception.getMessage().contains("exact DEXPI 2.0.1")
          || exception.getMessage().contains("not schema/profile conformant"))) {
        throw exception;
      }
      diagnostics.add("NATIVE_BINDING_SEMANTICS_INCOMPLETE: " + exception.getMessage());
    }
    DexpiXmlReader.ImportResult importEvidence;
    try {
      importEvidence = DexpiXmlReader.readWithDiagnostics(dexpi.toFile());
    } catch (DexpiXmlReaderException exception) {
      throw new IOException("Could not produce supported DEXPI import diagnostics", exception);
    }
    return new Result(model, bindings, nativeEvidence, importEvidence, diagnostics, sha256(manifest));
  }

  /**
   * Verifies and reopens a package only when the supported import and native binding semantics are lossless.
   *
   * @param project governed project with the exact executable source, project ID and revision
   * @param packageDirectory professional P&amp;ID package directory
   * @return lossless verified package intake
   * @throws IOException if a required artifact cannot be read or parsed
   */
  public static Result readStrict(EngineeringProject project, Path packageDirectory) throws IOException {
    Result result = read(project, packageDirectory);
    if (result.hasImportLosses()) {
      throw new IllegalArgumentException("Professional P&ID package has unsupported or incomplete DEXPI semantics");
    }
    return result;
  }

  /**
   * Compares two verified package reads using the shared canonical and supported native semantics.
   *
   * @param left first verified package read
   * @param right second verified package read
   * @return deterministic comparison; import losses always prevent equivalence
   */
  public static SemanticComparison compare(Result left, Result right) {
    if (left == null || right == null) {
      throw new IllegalArgumentException("left and right package reads are required");
    }
    List<String> differences = new ArrayList<String>();
    compareValue("PID_MODEL", left.model.toMap(), right.model.toMap(), differences);
    compareValue("SIMULATION_BINDINGS", left.bindings.toMap(), right.bindings.toMap(), differences);
    compareValue("NATIVE_DEXPI_BINDING_EVIDENCE", nativeSemanticMap(left), nativeSemanticMap(right), differences);
    compareValue("SUPPORTED_DEXPI_IMPORT", left.importEvidence.toMap(), right.importEvidence.toMap(), differences);
    compareValue("INTAKE_DIAGNOSTICS", left.diagnostics, right.diagnostics, differences);
    if (left.hasImportLosses() || right.hasImportLosses()) {
      differences.add("UNSUPPORTED_DEXPI_CONTENT");
    }
    return new SemanticComparison(left.semanticFingerprint, right.semanticFingerprint, differences);
  }

  /**
   * Appends one stable difference identifier when two values differ.
   *
   * @param identifier stable difference identifier
   * @param left left value
   * @param right right value
   * @param differences mutable ordered difference list
   */
  private static void compareValue(String identifier, Object left, Object right, List<String> differences) {
    if (!left.equals(right)) {
      differences.add(identifier);
    }
  }

  /**
   * Builds the deterministic supported semantic projection used for fingerprinting.
   *
   * @param result verified intake result
   * @return deterministic semantic projection
   */
  private static Map<String, Object> semanticMap(Result result) {
    Map<String, Object> semantic = new LinkedHashMap<String, Object>();
    semantic.put("canonicalModel", result.model.toMap());
    semantic.put("simulationBindings", result.bindings.toMap());
    semantic.put("nativeDexpiBindingEvidence", nativeSemanticMap(result));
    semantic.put("supportedImportEvidence", result.importEvidence.toMap());
    semantic.put("diagnostics", result.diagnostics);
    return semantic;
  }

  /**
   * Projects native evidence without its byte-level source fingerprint.
   *
   * @param result verified intake result
   * @return deterministic supported native semantics
   */
  private static Map<String, Object> nativeSemanticMap(Result result) {
    Map<String, Object> semantic = new LinkedHashMap<String, Object>();
    if (result.dexpiEvidence != null) {
      semantic.put("verifiedTags", result.dexpiEvidence.getVerifiedTags());
      semantic.put("verifiedSignals", result.dexpiEvidence.getVerifiedSignals());
      semantic.put("verifiedActuations", result.dexpiEvidence.getVerifiedActuations());
    }
    return semantic;
  }

  /**
   * Verifies the deterministic file inventory and rejects missing, changed, duplicate or unlisted package files.
   *
   * @param directory controlled package root
   * @param manifest package manifest
   * @throws IOException if a package file cannot be read
   */
  private static void verifyManifest(Path directory, Path manifest) throws IOException {
    JsonObject root = JsonParser.parseString(readUtf8(manifest)).getAsJsonObject();
    if (!"neqsim_engineering_package_manifest.v1".equals(text(root, "schemaVersion"))
        || !"SHA-256".equals(text(root, "hashAlgorithm"))) {
      throw new IllegalArgumentException("Unsupported professional P&ID package manifest");
    }
    JsonArray files = root.getAsJsonArray("files");
    if (files == null || !root.has("fileCount") || root.get("fileCount").getAsInt() != files.size()) {
      throw new IllegalArgumentException("Package manifest file count is missing or inconsistent");
    }
    Set<String> expected = new LinkedHashSet<String>();
    for (JsonElement value : files) {
      JsonObject row = value.getAsJsonObject();
      String relative = text(row, "path");
      if (relative.isEmpty() || !expected.add(relative)) {
        throw new IllegalArgumentException("Package manifest contains an empty or duplicate path: " + relative);
      }
      Path file = directory.resolve(relative).normalize();
      if (!file.startsWith(directory) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
        throw new IllegalArgumentException("Package manifest path is missing or leaves the package: " + relative);
      }
      if (!row.has("sizeBytes") || text(row, "sha256").isEmpty() || Files.size(file) != row.get("sizeBytes").getAsLong()
          || !sha256(file).equals(text(row, "sha256"))) {
        throw new IllegalArgumentException("Package manifest integrity mismatch: " + relative);
      }
    }
    Set<String> actual = new LinkedHashSet<String>();
    try (Stream<Path> paths = Files.walk(directory)) {
      paths.filter(Files::isRegularFile).forEach(path -> {
        String relative = directory.relativize(path).toString().replace('\\', '/');
        if (!MANIFEST_FILE.equals(relative) && !"neqsim-model-package.json".equals(relative)) {
          actual.add(relative);
        }
      });
    }
    if (!expected.equals(actual)) {
      throw new IllegalArgumentException("Package manifest does not match the package file inventory");
    }
  }

  /**
   * Resolves one required regular file inside the package.
   *
   * @param directory package root
   * @param relative relative artifact path
   * @return normalized regular-file path
   */
  private static Path requiredFile(Path directory, String relative) {
    Path file = directory.resolve(relative).normalize();
    if (!file.startsWith(directory) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
      throw new IllegalArgumentException("Required professional P&ID package artifact is missing: " + relative);
    }
    return file;
  }

  /**
   * Reads a UTF-8 package artifact.
   *
   * @param file artifact path
   * @return decoded text
   * @throws IOException if the artifact cannot be read
   */
  private static String readUtf8(Path file) throws IOException {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  /**
   * Reads an optional JSON string.
   *
   * @param object source object
   * @param field field name
   * @return string value or an empty string
   */
  private static String text(JsonObject object, String field) {
    return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
  }

  /**
   * Fingerprints a deterministic JSON-compatible semantic projection.
   *
   * @param value JSON-compatible value
   * @return lowercase SHA-256
   */
  private static String fingerprint(Object value) {
    return sha256(new GsonBuilder().create().toJson(value).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Computes a file SHA-256.
   *
   * @param file input file
   * @return lowercase SHA-256
   * @throws IOException if the file cannot be read
   */
  private static String sha256(Path file) throws IOException {
    MessageDigest digest = newDigest();
    byte[] buffer = new byte[8192];
    try (InputStream input = Files.newInputStream(file)) {
      int count;
      while ((count = input.read(buffer)) >= 0) {
        if (count > 0) {
          digest.update(buffer, 0, count);
        }
      }
    }
    return hex(digest.digest());
  }

  /**
   * Computes a byte-array SHA-256.
   *
   * @param bytes input bytes
   * @return lowercase SHA-256
   */
  private static String sha256(byte[] bytes) {
    MessageDigest digest = newDigest();
    digest.update(bytes);
    return hex(digest.digest());
  }

  /** @return a SHA-256 digest available in every supported Java runtime */
  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  /**
   * Converts digest bytes to lowercase hexadecimal.
   *
   * @param bytes digest bytes
   * @return lowercase hexadecimal
   */
  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte value : bytes) {
      result.append(String.format("%02x", Integer.valueOf(value & 0xff)));
    }
    return result.toString();
  }
}
