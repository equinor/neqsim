package neqsim.process.modelpackage;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Portable, integrity-protected outer envelope for a NeqSim model and its governed artifacts.
 *
 * <p>
 * The package records identity, revision, dependencies, qualification state, and SHA-256 content hashes. Integrity is
 * not engineering approval; accountable decisions remain external controlled evidence.
 * </p>
 */
public final class NeqSimModelPackage implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String FILE_NAME = "neqsim-model-package.json";
  public static final String SCHEMA_VERSION = "neqsim_model_package.v1";
  public static final String SCHEMA_URI = "urn:neqsim:schema:model-package-manifest:v1";
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private final ModelPackageIdentity identity;
  private final String graphArtifact;
  private final String qualificationStatus;
  private final List<ModelPackageArtifact> artifacts;
  private final List<ModelDependency> dependencies;
  private final Map<String, String> softwareVersions;

  private NeqSimModelPackage(ModelPackageIdentity identity, String graphArtifact, String qualificationStatus,
      List<ModelPackageArtifact> artifacts, List<ModelDependency> dependencies, Map<String, String> softwareVersions) {
    if (identity == null) {
      throw new IllegalArgumentException("identity must not be null");
    }
    this.identity = identity;
    this.graphArtifact = requireText(graphArtifact, "graphArtifact");
    this.qualificationStatus = requireText(qualificationStatus, "qualificationStatus");
    this.artifacts = new ArrayList<ModelPackageArtifact>(artifacts);
    this.dependencies = new ArrayList<ModelDependency>(dependencies);
    this.softwareVersions = new LinkedHashMap<String, String>(softwareVersions);
  }

  /** Creates an inventory of an existing model package directory. */
  public static NeqSimModelPackage create(Path packageDirectory, ModelPackageIdentity identity, String graphArtifact,
      String qualificationStatus, List<ModelDependency> dependencies, Map<String, String> softwareVersions)
      throws IOException {
    if (packageDirectory == null || !Files.isDirectory(packageDirectory)) {
      throw new IllegalArgumentException("packageDirectory must be an existing directory");
    }
    List<ModelPackageArtifact> artifacts = inventory(packageDirectory);
    List<ModelDependency> dependencyValues = dependencies == null ? Collections.<ModelDependency>emptyList()
        : dependencies;
    Map<String, String> software = softwareVersions == null ? Collections.<String, String>emptyMap() : softwareVersions;
    return new NeqSimModelPackage(identity, graphArtifact, qualificationStatus, artifacts, dependencyValues, software);
  }

  /** Creates and writes {@value #FILE_NAME} into an existing package directory. */
  public static Path write(Path packageDirectory, ModelPackageIdentity identity, String graphArtifact,
      String qualificationStatus, List<ModelDependency> dependencies, Map<String, String> softwareVersions)
      throws IOException {
    NeqSimModelPackage value = create(packageDirectory, identity, graphArtifact, qualificationStatus, dependencies,
        softwareVersions);
    Path output = packageDirectory.resolve(FILE_NAME);
    Files.write(output, value.toJson().getBytes(StandardCharsets.UTF_8));
    return output;
  }

  /** Reads a model-package manifest. Content integrity is checked separately by {@link ModelPackageValidator}. */
  public static NeqSimModelPackage read(Path manifestFile) throws IOException {
    if (manifestFile == null || !Files.isRegularFile(manifestFile)) {
      throw new IllegalArgumentException("manifestFile must be an existing regular file");
    }
    String json = new String(Files.readAllBytes(manifestFile), StandardCharsets.UTF_8);
    Map<String, Object> root = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {
    }.getType());
    if (root == null || !SCHEMA_VERSION.equals(String.valueOf(root.get("schemaVersion")))) {
      throw new IllegalArgumentException("Unsupported NeqSim model-package schema version");
    }
    if (!SCHEMA_URI.equals(String.valueOf(root.get("schemaUri")))) {
      throw new IllegalArgumentException("Unsupported NeqSim model-package schema URI");
    }
    ModelPackageIdentity identity = ModelPackageIdentity.fromMap(map(root.get("identity"), "identity"));
    List<ModelPackageArtifact> artifacts = new ArrayList<ModelPackageArtifact>();
    for (Map<String, Object> item : maps(root.get("artifacts"), "artifacts")) {
      artifacts.add(ModelPackageArtifact.fromMap(item));
    }
    List<ModelDependency> dependencies = new ArrayList<ModelDependency>();
    for (Map<String, Object> item : maps(root.get("dependencies"), "dependencies")) {
      dependencies.add(ModelDependency.fromMap(item));
    }
    Map<String, String> softwareVersions = new LinkedHashMap<String, String>();
    for (Map.Entry<String, Object> entry : map(root.get("softwareVersions"), "softwareVersions").entrySet()) {
      softwareVersions.put(entry.getKey(), String.valueOf(entry.getValue()));
    }
    return new NeqSimModelPackage(identity, String.valueOf(root.get("graphArtifact")),
        String.valueOf(root.get("qualificationStatus")), artifacts, dependencies, softwareVersions);
  }

  public ModelPackageIdentity getIdentity() {
    return identity;
  }

  public String getGraphArtifact() {
    return graphArtifact;
  }

  public String getQualificationStatus() {
    return qualificationStatus;
  }

  public List<ModelPackageArtifact> getArtifacts() {
    return Collections.unmodifiableList(artifacts);
  }

  public List<ModelDependency> getDependencies() {
    return Collections.unmodifiableList(dependencies);
  }

  public Map<String, String> getSoftwareVersions() {
    return Collections.unmodifiableMap(softwareVersions);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("schemaUri", SCHEMA_URI);
    result.put("identity", identity.toMap());
    result.put("graphArtifact", graphArtifact);
    result.put("qualificationStatus", qualificationStatus);
    List<Map<String, Object>> artifactMaps = new ArrayList<Map<String, Object>>();
    for (ModelPackageArtifact artifact : artifacts) {
      artifactMaps.add(artifact.toMap());
    }
    result.put("artifacts", artifactMaps);
    List<Map<String, Object>> dependencyMaps = new ArrayList<Map<String, Object>>();
    for (ModelDependency dependency : dependencies) {
      dependencyMaps.add(dependency.toMap());
    }
    result.put("dependencies", dependencyMaps);
    result.put("softwareVersions", new LinkedHashMap<String, String>(softwareVersions));
    result.put("governance",
        "Integrity and qualification metadata do not grant engineering approval or fitness for construction");
    return result;
  }

  public String toJson() {
    return GSON.toJson(toMap());
  }

  private static List<ModelPackageArtifact> inventory(final Path packageDirectory) throws IOException {
    List<Path> files = new ArrayList<Path>();
    collect(packageDirectory, packageDirectory, files);
    Collections.sort(files, new Comparator<Path>() {
      @Override
      public int compare(Path left, Path right) {
        return relative(packageDirectory, left).compareTo(relative(packageDirectory, right));
      }
    });
    List<ModelPackageArtifact> result = new ArrayList<ModelPackageArtifact>();
    Set<String> paths = new LinkedHashSet<String>();
    for (Path file : files) {
      String relativePath = relative(packageDirectory, file);
      if (!paths.add(relativePath)) {
        throw new IOException("Duplicate model-package artifact " + relativePath);
      }
      result.add(new ModelPackageArtifact(relativePath, role(relativePath), mediaType(relativePath), Files.size(file),
          sha256(file)));
    }
    return result;
  }

  private static void collect(Path root, Path directory, List<Path> result) throws IOException {
    try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
      for (Path entry : stream) {
        if (Files.isSymbolicLink(entry)) {
          throw new IOException("Symbolic links are not permitted in a model package: " + relative(root, entry));
        }
        if (Files.isDirectory(entry)) {
          collect(root, entry, result);
        } else if (Files.isRegularFile(entry) && !FILE_NAME.equals(entry.getFileName().toString())) {
          result.add(entry);
        }
      }
    }
  }

  static String sha256(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      try (InputStream input = Files.newInputStream(file)) {
        int count;
        while ((count = input.read(buffer)) >= 0) {
          if (count > 0) {
            digest.update(buffer, 0, count);
          }
        }
      }
      StringBuilder result = new StringBuilder();
      for (byte value : digest.digest()) {
        result.append(String.format("%02x", Integer.valueOf(value & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  static String relative(Path root, Path file) {
    return root.relativize(file).toString().replace('\\', '/');
  }

  private static String role(String path) {
    if ("engineering-model.json".equals(path)) {
      return "CANONICAL_GRAPH";
    }
    if (path.startsWith("schemas/")) {
      return "SCHEMA";
    }
    if (path.endsWith(".dexpi.xml") || path.endsWith("-proteus.xml") || path.endsWith("-pydexpi.xml")) {
      return "EXCHANGE_MODEL";
    }
    if (path.endsWith(".json") || path.endsWith(".csv")) {
      return "ENGINEERING_ARTIFACT";
    }
    return "SUPPORTING_ARTIFACT";
  }

  private static String mediaType(String path) {
    if (path.endsWith(".json")) {
      return "application/json";
    }
    if (path.endsWith(".xml")) {
      return "application/xml";
    }
    if (path.endsWith(".csv")) {
      return "text/csv";
    }
    if (path.endsWith(".md")) {
      return "text/markdown";
    }
    return "application/octet-stream";
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value, String field) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException(field + " must be an object");
    }
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> maps(Object value, String field) {
    if (!(value instanceof List)) {
      throw new IllegalArgumentException(field + " must be an array");
    }
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Object item : (List<Object>) value) {
      result.add(map(item, field + " item"));
    }
    return result;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
