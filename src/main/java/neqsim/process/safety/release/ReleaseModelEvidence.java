package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable applicability and validation-evidence manifest for a release-flow model.
 *
 * <p>
 * A manifest records what has been checked without converting software tests into engineering qualification. Current
 * built-in models remain {@code UNQUALIFIED}; an independent evidence record identifies evidence produced outside the
 * model implementation, but does not by itself authorize a qualified status.
 */
public final class ReleaseModelEvidence implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Evidence categories retained by the neutral source-term contract. */
  public enum Type {
    /** Closed-form or independently reimplemented analytical comparison. */
    ANALYTICAL,
    /** Component, total-mass, energy, momentum or volume closure. */
    CONSERVATION,
    /** Numerical regression, nearby-case or refinement evidence. */
    NUMERICAL,
    /** Experimental measurements or independently published validation data. */
    EXPERIMENTAL
  }

  /** One immutable evidence reference. */
  public static final class Record implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final Type type;
    private final String reference;
    private final String description;
    private final boolean independent;

    /**
     * Creates an evidence reference.
     *
     * @param id stable machine-readable evidence identifier
     * @param type evidence category
     * @param reference repository path, publication identifier or dataset identifier
     * @param description concise scope of the comparison
     * @param independent true only when the evidence was produced independently of this model
     */
    public Record(String id, Type type, String reference, String description, boolean independent) {
      this.id = requireText(id, "evidence id");
      if (type == null) {
        throw new IllegalArgumentException("Evidence type required");
      }
      this.type = type;
      this.reference = requireText(reference, "evidence reference");
      this.description = requireText(description, "evidence description");
      this.independent = independent;
    }

    /** @return stable evidence identifier */
    public String getId() {
      return id;
    }

    /** @return evidence category */
    public Type getType() {
      return type;
    }

    /** @return repository path, publication identifier or dataset identifier */
    public String getReference() {
      return reference;
    }

    /** @return concise evidence scope */
    public String getDescription() {
      return description;
    }

    /** @return whether the evidence is independent of the model implementation */
    public boolean isIndependent() {
      return independent;
    }
  }

  private final String manifestId;
  private final List<String> applicability;
  private final List<String> limitations;
  private final List<Record> records;

  /**
   * Creates an unqualified evidence manifest.
   *
   * @param manifestId stable manifest identifier including model/version context
   * @param applicability stable codes for represented regimes
   * @param limitations stable codes for excluded or unqualified regimes
   * @param records analytical, conservation, numerical or experimental evidence references
   */
  public ReleaseModelEvidence(String manifestId, List<String> applicability, List<String> limitations,
      List<Record> records) {
    this.manifestId = requireText(manifestId, "manifest id");
    this.applicability = immutableText(applicability, "applicability");
    this.limitations = immutableText(limitations, "limitations");
    if (records == null || records.contains(null)) {
      throw new IllegalArgumentException("Evidence records required");
    }
    this.records = Collections.unmodifiableList(new ArrayList<Record>(records));
  }

  /**
   * Creates the fail-closed manifest used by custom models that declare no model-specific evidence.
   *
   * @param modelId stable model identifier
   * @param modelVersion semantic model version
   * @return immutable unqualified manifest
   */
  public static ReleaseModelEvidence undeclared(String modelId, String modelVersion) {
    return new ReleaseModelEvidence(
        requireText(modelId, "model id") + ":" + requireText(modelVersion, "model version") + ":unqualified",
        Collections.singletonList("CALLER_DEFINED_MODEL"), Collections.singletonList("NO_DECLARED_VALIDATION_EVIDENCE"),
        Collections.<Record>emptyList());
  }

  /** @return stable manifest identifier */
  public String getManifestId() {
    return manifestId;
  }

  /** @return immutable stable applicability codes */
  public List<String> getApplicability() {
    return applicability;
  }

  /** @return immutable stable limitation codes */
  public List<String> getLimitations() {
    return limitations;
  }

  /** @return immutable evidence references */
  public List<Record> getRecords() {
    return records;
  }

  /** @return true when at least one retained record is independently produced */
  public boolean hasIndependentEvidence() {
    for (Record record : records) {
      if (record.isIndependent()) {
        return true;
      }
    }
    return false;
  }

  private static List<String> immutableText(List<String> values, String name) {
    if (values == null || values.isEmpty() || values.contains(null)) {
      throw new IllegalArgumentException(name + " codes required");
    }
    List<String> copy = new ArrayList<String>();
    for (String value : values) {
      copy.add(requireCode(value, name));
    }
    return Collections.unmodifiableList(copy);
  }

  private static String requireCode(String value, String name) {
    String text = requireText(value, name);
    if (!text.matches("[A-Z][A-Z0-9_]*")) {
      throw new IllegalArgumentException(name + " codes must be stable upper-snake-case identifiers");
    }
    return text;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " required");
    }
    return value;
  }
}
