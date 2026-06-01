package neqsim.process.safety.barrier;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Traceable evidence extracted from technical documentation for a safety decision.
 *
 * <p>
 * The evidence object is intentionally small and serializable so agents can carry references from
 * P&amp;IDs, cause and effect charts, safety requirement specifications, technical requirements,
 * inspection reports, and vendor data sheets into barrier registers and audit reports.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class DocumentEvidence implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String evidenceId;
  private final String documentId;
  private final String documentTitle;
  private final String revision;
  private final String section;
  private final int page;
  private final String sourceReference;
  private final String excerpt;
  private final double confidence;

  /**
   * Creates a document evidence record.
   *
   * @param evidenceId stable evidence identifier
   * @param documentId source document identifier or number
   * @param documentTitle source document title
   * @param revision document revision identifier
   * @param section clause, drawing zone, table, or section reference
   * @param page one-based page number, or 0 when not applicable
   * @param sourceReference path, URI, tag reference, or repository-local source pointer
   * @param excerpt short supporting text extracted from the source
   * @param confidence extraction confidence in the range 0 to 1
   */
  public DocumentEvidence(String evidenceId, String documentId, String documentTitle,
      String revision, String section, int page, String sourceReference, String excerpt,
      double confidence) {
    this.evidenceId = normalize(evidenceId);
    this.documentId = normalize(documentId);
    this.documentTitle = normalize(documentTitle);
    this.revision = normalize(revision);
    this.section = normalize(section);
    this.page = Math.max(0, page);
    this.sourceReference = normalize(sourceReference);
    this.excerpt = normalize(excerpt);
    this.confidence = Math.max(0.0, Math.min(1.0, confidence));
  }

  /**
   * Normalizes nullable text values for safe JSON export.
   *
   * @param value text value to normalize
   * @return trimmed text or an empty string
   */
  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Gets the evidence identifier.
   *
   * @return evidence identifier
   */
  public String getEvidenceId() {
    return evidenceId;
  }

  /**
   * Gets the document identifier.
   *
   * @return document identifier
   */
  public String getDocumentId() {
    return documentId;
  }

  /**
   * Gets the document title.
   *
   * @return document title
   */
  public String getDocumentTitle() {
    return documentTitle;
  }

  /**
   * Gets the document revision.
   *
   * @return revision identifier
   */
  public String getRevision() {
    return revision;
  }

  /**
   * Gets the section reference.
   *
   * @return section, clause, table, or drawing zone
   */
  public String getSection() {
    return section;
  }

  /**
   * Gets the one-based page number.
   *
   * @return page number, or 0 when not applicable
   */
  public int getPage() {
    return page;
  }

  /**
   * Gets the source reference.
   *
   * @return path, URI, tag reference, or repository-local source pointer
   */
  public String getSourceReference() {
    return sourceReference;
  }

  /**
   * Gets the extracted supporting text.
   *
   * @return source excerpt
   */
  public String getExcerpt() {
    return excerpt;
  }

  /**
   * Gets the extraction confidence.
   *
   * @return confidence in the range 0 to 1
   */
  public double getConfidence() {
    return confidence;
  }

  /**
   * Checks whether the evidence has enough source detail for audit traceability.
   *
   * @return true when a document or source reference and excerpt are present
   */
  public boolean isTraceable() {
    boolean hasSource = !documentId.isEmpty() || !sourceReference.isEmpty();
    return hasSource && !excerpt.isEmpty();
  }

  /**
   * Converts the evidence to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("evidenceId", evidenceId);
    map.put("documentId", documentId);
    map.put("documentTitle", documentTitle);
    map.put("revision", revision);
    map.put("section", section);
    map.put("page", page);
    map.put("sourceReference", sourceReference);
    map.put("excerpt", excerpt);
    map.put("confidence", confidence);
    map.put("traceable", isTraceable());
    return map;
  }

  /**
   * Converts the evidence to pretty JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }
}
