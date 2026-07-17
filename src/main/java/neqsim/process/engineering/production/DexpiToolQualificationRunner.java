package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compares stable DEXPI semantic snapshots before and after a named external-tool round trip. */
public final class DexpiToolQualificationRunner {
  private DexpiToolQualificationRunner() {
  }

  /** Tool-neutral semantic inventory produced by a DEXPI importer adapter. */
  public static final class Snapshot implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String profile;
    private final Map<String, Map<String, String>> objects = new LinkedHashMap<String, Map<String, String>>();
    private final Set<String> connections = new LinkedHashSet<String>();

    public Snapshot(String profile) {
      this.profile = text(profile, "profile");
    }

    public Snapshot addObject(String persistentId, Map<String, String> properties) {
      String id = text(persistentId, "persistentId");
      if (objects.containsKey(id)) {
        throw new IllegalArgumentException("Duplicate DEXPI persistent id " + id);
      }
      Map<String, String> normalized = new LinkedHashMap<String, String>();
      if (properties != null) {
        for (Map.Entry<String, String> property : properties.entrySet()) {
          normalized.put(text(property.getKey(), "property name"), text(property.getValue(), "property value"));
        }
      }
      objects.put(id, Collections.unmodifiableMap(normalized));
      return this;
    }

    public Snapshot addConnection(String sourceId, String relationship, String targetId) {
      connections.add(
          text(sourceId, "sourceId") + "|" + text(relationship, "relationship") + "|" + text(targetId, "targetId"));
      return this;
    }
  }

  /** Complete named-tool comparison plus the readiness evidence derived from it. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final DexpiToolQualificationEvidence evidence;
    private final List<String> semanticDifferences;

    Result(DexpiToolQualificationEvidence evidence, List<String> semanticDifferences) {
      this.evidence = evidence;
      this.semanticDifferences = Collections.unmodifiableList(new ArrayList<String>(semanticDifferences));
    }

    public DexpiToolQualificationEvidence getEvidence() {
      return evidence;
    }

    public boolean isQualified() {
      return evidence.isQualified();
    }

    public List<String> getSemanticDifferences() {
      return semanticDifferences;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("evidence", evidence.toMap());
      result.put("semanticDifferences", new ArrayList<String>(semanticDifferences));
      result.put("qualified", Boolean.valueOf(isQualified()));
      return result;
    }
  }

  public static Result compare(String product, String version, Snapshot reference, Snapshot afterImport,
      Snapshot afterExportAndReimport, String evidenceReference, String accountableReviewer) {
    List<String> differences = new ArrayList<String>();
    boolean imported = afterImport != null;
    boolean exported = afterExportAndReimport != null;
    if (reference == null) {
      throw new IllegalArgumentException("reference snapshot must not be null");
    }
    if (afterImport == null) {
      differences.add("Named tool did not produce an import snapshot");
    } else {
      compareSnapshots("IMPORT", reference, afterImport, differences);
    }
    if (afterExportAndReimport == null) {
      differences.add("Named tool did not produce an export/reimport snapshot");
    } else {
      compareSnapshots("EXPORT_REIMPORT", reference, afterExportAndReimport, differences);
    }
    DexpiToolQualificationEvidence evidence = new DexpiToolQualificationEvidence(product, version, reference.profile,
        imported, exported, differences.size(), evidenceReference, accountableReviewer);
    return new Result(evidence, differences);
  }

  private static void compareSnapshots(String stage, Snapshot expected, Snapshot actual, List<String> differences) {
    if (!expected.profile.equals(actual.profile)) {
      differences.add(stage + " profile expected " + expected.profile + " but found " + actual.profile);
    }
    for (Map.Entry<String, Map<String, String>> expectedObject : expected.objects.entrySet()) {
      Map<String, String> actualProperties = actual.objects.get(expectedObject.getKey());
      if (actualProperties == null) {
        differences.add(stage + " missing object " + expectedObject.getKey());
        continue;
      }
      for (Map.Entry<String, String> property : expectedObject.getValue().entrySet()) {
        String actualValue = actualProperties.get(property.getKey());
        if (!property.getValue().equals(actualValue)) {
          differences.add(stage + " object " + expectedObject.getKey() + " property " + property.getKey() + " expected "
              + property.getValue() + " but found " + String.valueOf(actualValue));
        }
      }
      for (String property : actualProperties.keySet()) {
        if (!expectedObject.getValue().containsKey(property)) {
          differences.add(stage + " object " + expectedObject.getKey() + " unexpected property " + property);
        }
      }
    }
    for (String id : actual.objects.keySet()) {
      if (!expected.objects.containsKey(id)) {
        differences.add(stage + " unexpected object " + id);
      }
    }
    for (String connection : expected.connections) {
      if (!actual.connections.contains(connection)) {
        differences.add(stage + " missing connection " + connection);
      }
    }
    for (String connection : actual.connections) {
      if (!expected.connections.contains(connection)) {
        differences.add(stage + " unexpected connection " + connection);
      }
    }
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
