package neqsim.process.engineering.handover;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Project-controlled mapping from NeqSim engineering identities to CFIHOS 2.0 reference-data identifiers.
 *
 * <p>
 * The class deliberately does not ship or invent CFIHOS RDL identifiers. A project information manager must load exact
 * identifiers from the controlled Core or Extended RDL and approve the mapping revision used for a handover.
 */
public final class Cfihos20ReferenceDataMapping implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** CFIHOS 2.0 Reference Data Library delivery used by the project. */
  public enum Edition {
    CORE, EXTENDED
  }

  /** Tag and optional physical-equipment classification for one canonical graph node. */
  public static final class NodeClassification implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String tagClassId;
    private final String equipmentClassId;

    private NodeClassification(String tagClassId, String equipmentClassId) {
      this.tagClassId = requireText(tagClassId, "tagClassId");
      this.equipmentClassId = optionalText(equipmentClassId);
    }

    public String getTagClassId() {
      return tagClassId;
    }

    public String getEquipmentClassId() {
      return equipmentClassId;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("tagClassId", tagClassId);
      result.put("equipmentClassId", equipmentClassId);
      return result;
    }
  }

  /** CFIHOS property and unit-of-measure identifiers for one canonical property name. */
  public static final class PropertyDefinition implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String propertyId;
    private final String unitOfMeasureId;

    private PropertyDefinition(String propertyId, String unitOfMeasureId) {
      this.propertyId = requireText(propertyId, "propertyId");
      this.unitOfMeasureId = optionalText(unitOfMeasureId);
    }

    public String getPropertyId() {
      return propertyId;
    }

    public String getUnitOfMeasureId() {
      return unitOfMeasureId;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("propertyId", propertyId);
      result.put("unitOfMeasureId", unitOfMeasureId);
      return result;
    }
  }

  private final Edition edition;
  private final String sourceUri;
  private final String sourceSha256;
  private final boolean sourceDigestVerified;
  private final String mappingAuthority;
  private final String mappingRevision;
  private final boolean approvedForProject;
  private final Map<String, NodeClassification> nodeClassifications;
  private final Map<String, PropertyDefinition> propertyDefinitions;
  private final Map<String, String> documentTypeIds;

  private Cfihos20ReferenceDataMapping(Builder builder) {
    edition = builder.edition;
    sourceUri = builder.sourceUri;
    sourceSha256 = builder.sourceSha256;
    sourceDigestVerified = builder.sourceDigestVerified;
    mappingAuthority = builder.mappingAuthority;
    mappingRevision = builder.mappingRevision;
    approvedForProject = builder.approvedForProject;
    nodeClassifications = Collections
        .unmodifiableMap(new LinkedHashMap<String, NodeClassification>(builder.nodeClassifications));
    propertyDefinitions = Collections
        .unmodifiableMap(new LinkedHashMap<String, PropertyDefinition>(builder.propertyDefinitions));
    documentTypeIds = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.documentTypeIds));
  }

  public static Builder builder(Edition edition) {
    return new Builder(edition);
  }

  public Edition getEdition() {
    return edition;
  }

  public String getSourceUri() {
    return sourceUri;
  }

  public String getSourceSha256() {
    return sourceSha256;
  }

  public boolean isSourceDigestVerified() {
    return sourceDigestVerified;
  }

  public String getMappingAuthority() {
    return mappingAuthority;
  }

  public String getMappingRevision() {
    return mappingRevision;
  }

  public boolean isApprovedForProject() {
    return approvedForProject;
  }

  public NodeClassification getNodeClassification(String nodeId) {
    return nodeClassifications.get(nodeId);
  }

  public PropertyDefinition getPropertyDefinition(String propertyName) {
    return propertyDefinitions.get(propertyName);
  }

  public String getDocumentTypeId(String externalKey) {
    return documentTypeIds.get(externalKey);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("cfihosVersion", "2.0");
    result.put("rdlEdition", edition.name());
    result.put("rdlSourceUri", sourceUri);
    result.put("rdlSourceSha256", sourceSha256);
    result.put("rdlSourceDigestVerified", Boolean.valueOf(sourceDigestVerified));
    result.put("mappingAuthority", mappingAuthority);
    result.put("mappingRevision", mappingRevision);
    result.put("approvedForProject", Boolean.valueOf(approvedForProject));
    Map<String, Object> classifications = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, NodeClassification> item : new TreeMap<String, NodeClassification>(nodeClassifications)
        .entrySet()) {
      classifications.put(item.getKey(), item.getValue().toMap());
    }
    result.put("nodeClassifications", classifications);
    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, PropertyDefinition> item : new TreeMap<String, PropertyDefinition>(propertyDefinitions)
        .entrySet()) {
      properties.put(item.getKey(), item.getValue().toMap());
    }
    result.put("propertyDefinitions", properties);
    result.put("documentTypeIds", new TreeMap<String, String>(documentTypeIds));
    return result;
  }

  /** Builder for an auditable, project-specific RDL mapping. */
  public static final class Builder {
    private final Edition edition;
    private String sourceUri = "";
    private String sourceSha256 = "";
    private boolean sourceDigestVerified;
    private String mappingAuthority = "";
    private String mappingRevision = "";
    private boolean approvedForProject;
    private final Map<String, NodeClassification> nodeClassifications = new LinkedHashMap<String, NodeClassification>();
    private final Map<String, PropertyDefinition> propertyDefinitions = new LinkedHashMap<String, PropertyDefinition>();
    private final Map<String, String> documentTypeIds = new LinkedHashMap<String, String>();

    private Builder(Edition edition) {
      if (edition == null) {
        throw new IllegalArgumentException("edition must not be null");
      }
      this.edition = edition;
    }

    /** Records the controlled RDL source and its content digest. */
    public Builder source(String uri, String sha256) {
      sourceUri = requireText(uri, "sourceUri");
      sourceSha256 = requireSha256(sha256);
      sourceDigestVerified = false;
      return this;
    }

    /** Reads a controlled local RDL delivery and records its verified SHA-256 digest. */
    public Builder verifiedSource(String uri, Path file) throws IOException {
      if (file == null || !Files.isRegularFile(file)) {
        throw new IllegalArgumentException("file must identify a regular RDL delivery");
      }
      sourceUri = requireText(uri, "sourceUri");
      sourceSha256 = digest(Files.readAllBytes(file));
      sourceDigestVerified = true;
      return this;
    }

    /** Records approval of this exact mapping revision by the accountable project authority. */
    public Builder approvedBy(String authority, String revision) {
      mappingAuthority = requireText(authority, "mappingAuthority");
      mappingRevision = requireText(revision, "mappingRevision");
      approvedForProject = true;
      return this;
    }

    /** Maps one canonical node id to exact CFIHOS tag and optional equipment class ids. */
    public Builder mapNode(String nodeId, String tagClassId, String equipmentClassId) {
      String key = requireText(nodeId, "nodeId");
      nodeClassifications.put(key, new NodeClassification(tagClassId, equipmentClassId));
      return this;
    }

    /** Maps one canonical node property to exact CFIHOS property and, when applicable, UOM ids. */
    public Builder mapProperty(String propertyName, String propertyId, String unitOfMeasureId) {
      propertyDefinitions.put(requireText(propertyName, "propertyName"),
          new PropertyDefinition(propertyId, unitOfMeasureId));
      return this;
    }

    /** Maps one canonical document external key to an exact CFIHOS document-type id. */
    public Builder mapDocument(String externalKey, String documentTypeId) {
      documentTypeIds.put(requireText(externalKey, "externalKey"), requireText(documentTypeId, "documentTypeId"));
      return this;
    }

    public Cfihos20ReferenceDataMapping build() {
      if (approvedForProject && (sourceUri.isEmpty() || sourceSha256.isEmpty())) {
        throw new IllegalStateException("Approved mappings require a controlled RDL source and SHA-256");
      }
      return new Cfihos20ReferenceDataMapping(this);
    }
  }

  private static String optionalText(String value) {
    return value == null ? "" : value.trim();
  }

  private static String requireText(String value, String name) {
    String result = optionalText(value);
    if (result.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return result;
  }

  private static String requireSha256(String value) {
    String result = requireText(value, "sha256").toLowerCase();
    if (!result.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("sha256 must contain exactly 64 hexadecimal characters");
    }
    return result;
  }

  private static String digest(byte[] content) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
      StringBuilder result = new StringBuilder();
      for (byte value : hash) {
        result.append(String.format("%02x", value & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
