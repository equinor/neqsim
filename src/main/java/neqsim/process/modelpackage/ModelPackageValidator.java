package neqsim.process.modelpackage;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fail-closed structural and content-integrity validation for {@link NeqSimModelPackage}. */
public final class ModelPackageValidator {
  private ModelPackageValidator() {
  }

  public static Result validate(Path packageDirectory) throws IOException {
    if (packageDirectory == null || !Files.isDirectory(packageDirectory)) {
      throw new IllegalArgumentException("packageDirectory must be an existing directory");
    }
    Path manifest = packageDirectory.resolve(NeqSimModelPackage.FILE_NAME);
    if (!Files.isRegularFile(manifest)) {
      return new Result(Collections.singletonList("Missing " + NeqSimModelPackage.FILE_NAME));
    }
    NeqSimModelPackage value;
    try {
      value = NeqSimModelPackage.read(manifest);
    } catch (RuntimeException ex) {
      return new Result(Collections.singletonList("Invalid model-package manifest: " + ex.getMessage()));
    }
    List<String> findings = new ArrayList<String>();
    Set<String> paths = new LinkedHashSet<String>();
    for (ModelPackageArtifact artifact : value.getArtifacts()) {
      String path = artifact.getPath();
      if (!isSafeRelativePath(path)) {
        findings.add("Unsafe artifact path " + path);
        continue;
      }
      if (!paths.add(path)) {
        findings.add("Duplicate artifact path " + path);
        continue;
      }
      Path file = packageDirectory.resolve(path).normalize();
      if (!file.startsWith(packageDirectory.normalize())) {
        findings.add("Artifact escapes package directory " + path);
      } else if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
        findings.add("Missing or non-regular artifact " + path);
      } else {
        if (Files.size(file) != artifact.getSizeBytes()) {
          findings.add("Artifact size mismatch " + path);
        }
        if (!NeqSimModelPackage.sha256(file).equals(artifact.getSha256())) {
          findings.add("Artifact SHA-256 mismatch " + path);
        }
      }
    }
    NeqSimModelPackage current = NeqSimModelPackage.create(packageDirectory, value.getIdentity(),
        value.getGraphArtifact(), value.getQualificationStatus(), value.getDependencies(), value.getSoftwareVersions());
    for (ModelPackageArtifact artifact : current.getArtifacts()) {
      if (!paths.contains(artifact.getPath())) {
        findings.add("Uninventoried artifact " + artifact.getPath());
      }
    }
    if (!paths.contains(value.getGraphArtifact())) {
      findings.add("Canonical graph artifact is not inventoried: " + value.getGraphArtifact());
    }
    return new Result(findings);
  }

  private static boolean isSafeRelativePath(String value) {
    if (value == null || value.trim().isEmpty() || value.startsWith("/") || value.startsWith("\\")
        || value.indexOf('\0') >= 0 || value.matches("^[A-Za-z]:.*")) {
      return false;
    }
    String normalized = value.replace('\\', '/');
    return !normalized.equals("..") && !normalized.startsWith("../") && !normalized.contains("/../")
        && !normalized.endsWith("/..");
  }

  /** Immutable validation decision. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final List<String> findings;

    Result(List<String> findings) {
      this.findings = new ArrayList<String>(findings);
    }

    public boolean isValid() {
      return findings.isEmpty();
    }

    public List<String> getFindings() {
      return Collections.unmodifiableList(findings);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("valid", Boolean.valueOf(isValid()));
      result.put("findings", new ArrayList<String>(findings));
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("governance", "Model-package integrity validation is not engineering approval");
      return result;
    }
  }
}
