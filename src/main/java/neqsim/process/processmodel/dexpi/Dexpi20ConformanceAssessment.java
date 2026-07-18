package neqsim.process.processmodel.dexpi;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Produces an auditable DEXPI 2.0 schema, model-import, and semantic-profile conformance report. */
public final class Dexpi20ConformanceAssessment {
  public static final String SPECIFICATION_VERSION = "2.0.0";
  public static final String SPECIFICATION_RELEASE_DATE = "2025-10-10";
  public static final String OFFICIAL_SCHEMA_SHA256 = "0bd6568b7c52fd59ac4ef6d8547ab2b2aa890c009a3a1bfbc5d7cc18c53b36e6";
  private static final String SCHEMA_RESOURCE = "/dexpi/2.0/DEXPI_XML_Schema.xsd";

  private Dexpi20ConformanceAssessment() {
  }

  /** Supported official DEXPI 2.0 information-model exchange profiles. */
  public enum Profile {
    PLANT_P_ID("Plant", "https://data.dexpi.org/models/2.0.0/Plant.xml"),
    PROCESS_PFD_BFD("Process", "https://data.dexpi.org/models/2.0.0/Process.xml");

    private final String prefix;
    private final String modelUri;

    Profile(String prefix, String modelUri) {
      this.prefix = prefix;
      this.modelUri = modelUri;
    }
  }

  /** Immutable conformance evidence. */
  public static final class Report implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Profile profile;
    private final String fileSha256;
    private final String bundledSchemaSha256;
    private final boolean officialSchemaVerified;
    private final boolean schemaValid;
    private final boolean officialImportsValid;
    private final boolean semanticProfileValid;
    private final int objectCount;
    private final int referenceCount;
    private final List<String> errors;
    private final List<String> warnings;

    Report(Profile profile, String fileSha256, String bundledSchemaSha256, boolean officialSchemaVerified,
        boolean schemaValid, boolean officialImportsValid, boolean semanticProfileValid, int objectCount,
        int referenceCount, List<String> errors, List<String> warnings) {
      this.profile = profile;
      this.fileSha256 = fileSha256;
      this.bundledSchemaSha256 = bundledSchemaSha256;
      this.officialSchemaVerified = officialSchemaVerified;
      this.schemaValid = schemaValid;
      this.officialImportsValid = officialImportsValid;
      this.semanticProfileValid = semanticProfileValid;
      this.objectCount = objectCount;
      this.referenceCount = referenceCount;
      this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
      this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public boolean isSchemaAndProfileConformant() {
      return officialSchemaVerified && schemaValid && officialImportsValid && semanticProfileValid;
    }

    public List<String> getErrors() {
      return errors;
    }

    public List<String> getWarnings() {
      return warnings;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_dexpi_2_0_conformance.v1");
      result.put("dexpiSpecificationVersion", SPECIFICATION_VERSION);
      result.put("dexpiSpecificationReleaseDate", SPECIFICATION_RELEASE_DATE);
      result.put("profile", profile.name());
      result.put("fileSha256", fileSha256);
      result.put("bundledOfficialSchemaSha256", bundledSchemaSha256);
      result.put("officialSchemaVerified", Boolean.valueOf(officialSchemaVerified));
      result.put("schemaValid", Boolean.valueOf(schemaValid));
      result.put("officialModelImportsValid", Boolean.valueOf(officialImportsValid));
      result.put("neqsimSemanticProfileValid", Boolean.valueOf(semanticProfileValid));
      result.put("objectCount", Integer.valueOf(objectCount));
      result.put("referenceCount", Integer.valueOf(referenceCount));
      result.put("status", isSchemaAndProfileConformant() ? "CONFORMANT" : "NONCONFORMANT");
      result.put("errors", new ArrayList<String>(errors));
      result.put("warnings", new ArrayList<String>(warnings));
      result.put("assessmentScope",
          "Official DEXPI XML 2.0 schema, official model imports, reference integrity, and NeqSim supported semantic profile");
      result.put("dexpiEvCertificationStatus", "NOT_A_DEXPI_EV_CERTIFICATE");
      result.put("namedCaeRoundTripStatus", "QUALIFICATION_REQUIRED");
      return result;
    }

    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }

  /** Assesses one DEXPI XML file without network access. */
  public static Report assess(Path file, Profile profile) throws IOException {
    if (file == null || profile == null) {
      throw new IllegalArgumentException("file and profile must not be null");
    }
    List<String> errors = new ArrayList<String>();
    List<String> warnings = new ArrayList<String>();
    String schemaSha = resourceSha256();
    boolean officialSchema = OFFICIAL_SCHEMA_SHA256.equals(schemaSha);
    if (!officialSchema) {
      errors.add("Bundled DEXPI XML schema fingerprint does not match the reviewed V2.0.0 resource");
    }
    boolean schemaValid = true;
    try {
      Dexpi20XmlValidator.validate(file);
    } catch (SAXException ex) {
      schemaValid = false;
      errors.add("Official DEXPI XML schema validation failed: " + ex.getMessage());
    }

    Document document = parse(file);
    Map<String, String> imports = imports(document);
    boolean importsValid = Dexpi20ProcessModelWriter.CORE_MODEL.equals(imports.get("Core"))
        && profile.modelUri.equals(imports.get(profile.prefix));
    if (!importsValid) {
      errors.add("Required official Core and " + profile.prefix + " model imports are missing or version-mismatched");
    }

    Dexpi20SemanticValidator.ValidationReport semantic = Dexpi20SemanticValidator.validate(document);
    errors.addAll(semantic.getErrors());
    warnings.addAll(semantic.getWarnings());
    NodeList objects = document.getElementsByTagName("Object");
    NodeList references = document.getElementsByTagName("References");
    return new Report(profile, sha256(Files.newInputStream(file)), schemaSha, officialSchema, schemaValid, importsValid,
        semantic.isValid(), objects.getLength(), references.getLength(), errors, warnings);
  }

  private static Document parse(Path file) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().parse(file.toFile());
    } catch (ParserConfigurationException ex) {
      throw new IOException("Could not configure DEXPI 2.0 conformance parser", ex);
    } catch (SAXException ex) {
      throw new IOException("Could not parse DEXPI 2.0 XML", ex);
    }
  }

  private static Map<String, String> imports(Document document) {
    Map<String, String> result = new LinkedHashMap<String, String>();
    NodeList imports = document.getDocumentElement().getElementsByTagName("Import");
    for (int index = 0; index < imports.getLength(); index++) {
      Element item = (Element) imports.item(index);
      result.put(item.getAttribute("prefix"), item.getAttribute("source"));
    }
    return result;
  }

  private static String resourceSha256() throws IOException {
    InputStream stream = Dexpi20ConformanceAssessment.class.getResourceAsStream(SCHEMA_RESOURCE);
    if (stream == null) {
      throw new IOException("Bundled DEXPI XML schema is unavailable: " + SCHEMA_RESOURCE);
    }
    return sha256(stream);
  }

  private static String sha256(InputStream stream) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      try {
        int count;
        while ((count = stream.read(buffer)) >= 0) {
          digest.update(buffer, 0, count);
        }
      } finally {
        stream.close();
      }
      StringBuilder result = new StringBuilder();
      for (byte value : digest.digest()) {
        result.append(String.format("%02x", Integer.valueOf(value & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
