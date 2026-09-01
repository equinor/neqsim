package neqsim.process.engineering.production;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Binds external engineering evidence receipts to the actual supplied document bytes.
 *
 * <p>
 * The simulator verifies content integrity only. It does not create, sign, accept, or approve an external decision.
 */
public final class EngineeringExternalEvidenceDocumentIntegrity implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final Map<String, Document> documents = new LinkedHashMap<String, Document>();

  /** Adds one controlled document and computes its SHA-256 digest from the supplied bytes. */
  public EngineeringExternalEvidenceDocumentIntegrity addDocument(String reference, byte[] content) {
    String controlledReference = requireText(reference, "reference");
    if (content == null) {
      throw new IllegalArgumentException("content must not be null");
    }
    Document candidate = new Document(controlledReference, sha256(content), content.length);
    Document existing = documents.get(controlledReference);
    if (existing != null && !existing.sha256.equals(candidate.sha256)) {
      throw new IllegalArgumentException("Conflicting content supplied for " + controlledReference);
    }
    documents.put(controlledReference, candidate);
    return this;
  }

  /** Adds one UTF-8 controlled document. */
  public EngineeringExternalEvidenceDocumentIntegrity addDocument(String reference, String content) {
    if (content == null) {
      throw new IllegalArgumentException("content must not be null");
    }
    return addDocument(reference, content.getBytes(StandardCharsets.UTF_8));
  }

  public List<String> getDocumentReferences() {
    return Collections.unmodifiableList(new ArrayList<String>(documents.keySet()));
  }

  /** Verifies every complete accepted receipt against independently supplied document content. */
  public Result assess(EngineeringExternalEvidenceRegister register) {
    List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();
    List<String> verifiedRecordIds = new ArrayList<String>();
    int acceptedRecords = 0;
    if (register == null) {
      findings.add(finding("MISSING_EVIDENCE_REGISTER", "", "Attach the external evidence register"));
    } else {
      for (EngineeringExternalEvidenceRecord record : register.getRecords()) {
        if (!record.isAcceptedAndComplete()) {
          continue;
        }
        acceptedRecords++;
        Document document = documents.get(record.getDocumentReference());
        if (document == null) {
          findings.add(finding("DOCUMENT_NOT_SUPPLIED", record.getId(),
              "Supply the controlled document bytes for " + record.getDocumentReference()));
        } else if (!document.sha256.equals(record.getSha256().toLowerCase(Locale.ROOT))) {
          findings.add(finding("DOCUMENT_HASH_MISMATCH", record.getId(),
              "Replace the document or receipt so the computed and declared SHA-256 hashes agree"));
        } else {
          verifiedRecordIds.add(record.getId());
        }
      }
    }
    if (acceptedRecords == 0) {
      findings.add(finding("NO_ACCEPTED_EXTERNAL_DOCUMENTS", "",
          "Attach complete accepted receipts and their controlled document bytes"));
    }
    return new Result(documents, verifiedRecordIds, findings);
  }

  /** Fail-closed convenience entry point for an optional integrity manifest. */
  public static Result assess(EngineeringExternalEvidenceRegister register,
      EngineeringExternalEvidenceDocumentIntegrity integrity) {
    if (integrity == null) {
      return new EngineeringExternalEvidenceDocumentIntegrity().assess(register);
    }
    return integrity.assess(register);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
    for (Document document : documents.values()) {
      values.add(document.toMap());
    }
    result.put("documents", values);
    result.put("documentCount", Integer.valueOf(values.size()));
    return result;
  }

  private static String sha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] value = digest.digest(content);
      StringBuilder result = new StringBuilder();
      for (byte item : value) {
        result.append(String.format("%02x", Integer.valueOf(item & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private static Map<String, Object> finding(String code, String recordId, String action) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("code", code);
    result.put("recordId", recordId);
    result.put("severity", "BLOCKER");
    result.put("requiredAction", action);
    return result;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static final class Document implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String reference;
    private final String sha256;
    private final long byteLength;

    Document(String reference, String sha256, long byteLength) {
      this.reference = reference;
      this.sha256 = sha256;
      this.byteLength = byteLength;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("reference", reference);
      result.put("computedSha256", sha256);
      result.put("byteLength", Long.valueOf(byteLength));
      return result;
    }
  }

  /** Immutable integrity result for package and readiness gates. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Document> documents;
    private final List<String> verifiedRecordIds;
    private final List<Map<String, Object>> findings;

    Result(Map<String, Document> documents, List<String> verifiedRecordIds, List<Map<String, Object>> findings) {
      this.documents = new LinkedHashMap<String, Document>(documents);
      this.verifiedRecordIds = new ArrayList<String>(verifiedRecordIds);
      this.findings = new ArrayList<Map<String, Object>>(findings);
    }

    public boolean isPassed() {
      return !verifiedRecordIds.isEmpty() && findings.isEmpty();
    }

    public List<String> getVerifiedRecordIds() {
      return Collections.unmodifiableList(verifiedRecordIds);
    }

    public List<Map<String, Object>> getFindings() {
      return Collections.unmodifiableList(findings);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      List<Map<String, Object>> manifest = new ArrayList<Map<String, Object>>();
      for (Document document : documents.values()) {
        manifest.add(document.toMap());
      }
      result.put("passed", Boolean.valueOf(isPassed()));
      result.put("verifiedRecordIds", new ArrayList<String>(verifiedRecordIds));
      result.put("documents", manifest);
      result.put("findings", new ArrayList<Map<String, Object>>(findings));
      result.put("approvalGrantedBySimulator", Boolean.FALSE);
      return result;
    }
  }
}
