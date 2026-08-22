package neqsim.process.processmodel.dexpi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Immutable structured result from the opt-in DEXPI Core graphical-projection adapter.
 *
 * <p>
 * A complete report means that every projection primitive was attached to one exported conceptual
 * object. It is not a DEXPI profile, standards-conformance, or accountable drawing-approval claim.
 * </p>
 */
public final class Dexpi20GraphicalProjectionReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Diagnostic severity for adapter mapping and conversion findings. */
  public enum Severity {
    /** Informational evidence that does not represent a loss. */
    INFO,
    /** A deterministic approximation or retained limitation. */
    WARNING,
    /** A primitive or represented identity could not be exported. */
    ERROR
  }

  /** Immutable adapter finding. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subjectId;

    Diagnostic(Severity severity, String code, String message, String subjectId) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.subjectId = subjectId;
    }

    /** @return finding severity */
    public Severity getSeverity() {
      return severity;
    }

    /** @return stable machine-readable finding code */
    public String getCode() {
      return code;
    }

    /** @return human-readable finding explanation */
    public String getMessage() {
      return message;
    }

    /** @return projection primitive or represented-object identity */
    public String getSubjectId() {
      return subjectId;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity.name());
      result.put("code", code);
      result.put("message", message);
      result.put("subjectId", subjectId);
      return result;
    }
  }

  private final String sourceGraphFingerprint;
  private final String sourceReference;
  private final String revision;
  private final int emittedRepresentationGroupCount;
  private final int emittedPrimitiveCount;
  private final int skippedPrimitiveCount;
  private final List<Diagnostic> diagnostics;

  Dexpi20GraphicalProjectionReport(String sourceGraphFingerprint, String sourceReference, String revision,
      int emittedRepresentationGroupCount, int emittedPrimitiveCount, int skippedPrimitiveCount,
      List<Diagnostic> diagnostics) {
    this.sourceGraphFingerprint = sourceGraphFingerprint;
    this.sourceReference = sourceReference;
    this.revision = revision;
    this.emittedRepresentationGroupCount = emittedRepresentationGroupCount;
    this.emittedPrimitiveCount = emittedPrimitiveCount;
    this.skippedPrimitiveCount = skippedPrimitiveCount;
    List<Diagnostic> sorted = new ArrayList<Diagnostic>(diagnostics);
    Collections.sort(sorted, new Comparator<Diagnostic>() {
      @Override
      public int compare(Diagnostic left, Diagnostic right) {
        int code = left.getCode().compareTo(right.getCode());
        return code == 0 ? left.getSubjectId().compareTo(right.getSubjectId()) : code;
      }
    });
    this.diagnostics = Collections.unmodifiableList(sorted);
  }

  /** @return canonical source-graph fingerprint carried by the projection */
  public String getSourceGraphFingerprint() {
    return sourceGraphFingerprint;
  }

  /** @return controlled source or drawing reference carried by the projection */
  public String getSourceReference() {
    return sourceReference;
  }

  /** @return projection revision */
  public String getRevision() {
    return revision;
  }

  /** @return number of emitted non-empty DEXPI RepresentationGroup objects */
  public int getEmittedRepresentationGroupCount() {
    return emittedRepresentationGroupCount;
  }

  /** @return number of emitted Core Polygon, PolyLine, and Text objects */
  public int getEmittedPrimitiveCount() {
    return emittedPrimitiveCount;
  }

  /** @return number of projection primitives deliberately omitted with diagnostics */
  public int getSkippedPrimitiveCount() {
    return skippedPrimitiveCount;
  }

  /** @return immutable deterministic diagnostics */
  public List<Diagnostic> getDiagnostics() {
    return diagnostics;
  }

  /** @return whether no primitive or represented identity was lost */
  public boolean isComplete() {
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic.getSeverity() == Severity.ERROR) {
        return false;
      }
    }
    return skippedPrimitiveCount == 0;
  }

  /**
   * Returns deterministic machine-readable adapter evidence.
   *
   * @return ordered JSON-compatible report map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_dexpi20_graphical_projection_report.v1");
    result.put("sourceGraphFingerprint", sourceGraphFingerprint);
    result.put("sourceReference", sourceReference);
    result.put("revision", revision);
    result.put("emittedRepresentationGroupCount", Integer.valueOf(emittedRepresentationGroupCount));
    result.put("emittedPrimitiveCount", Integer.valueOf(emittedPrimitiveCount));
    result.put("skippedPrimitiveCount", Integer.valueOf(skippedPrimitiveCount));
    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
    for (Diagnostic diagnostic : diagnostics) {
      values.add(diagnostic.toMap());
    }
    result.put("diagnostics", values);
    result.put("complete", Boolean.valueOf(isComplete()));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  /** @return deterministic pretty-printed JSON evidence */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }
}
