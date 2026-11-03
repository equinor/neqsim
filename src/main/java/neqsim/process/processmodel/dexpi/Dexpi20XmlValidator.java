package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.SAXException;

/** Validates native DEXPI XML documents against the published DEXPI XML schema. */
public final class Dexpi20XmlValidator {
  private static final String SCHEMA_RESOURCE = "/dexpi/2.0/DEXPI_XML_Schema.xsd";

  private Dexpi20XmlValidator() {
  }

  /**
   * Validates a DEXPI XML document.
   *
   * @param file document to validate
   * @throws IOException when the document or bundled schema cannot be read
   * @throws SAXException when the document is not schema-valid
   */
  public static void validate(Path file) throws IOException, SAXException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    validate(file, false);
  }

  /**
   * Validates against the exact bundled specification schema.
   *
   * @param file document to validate
   * @param specification201 whether to select the schema published with 2.0.1
   * @throws IOException if the document or schema is unavailable
   * @throws SAXException if the document is not schema-valid
   */
  public static void validate(Path file, boolean specification201) throws IOException, SAXException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    String resource = specification201 ? "/dexpi/2.0.1/DEXPI_XML_Schema.xsd" : SCHEMA_RESOURCE;
    InputStream schemaStream = Dexpi20XmlValidator.class.getResourceAsStream(resource);
    if (schemaStream == null) {
      throw new IOException("Bundled DEXPI XML schema is unavailable: " + SCHEMA_RESOURCE);
    }
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Schema schema = factory.newSchema(new StreamSource(schemaStream));
      Validator validator = schema.newValidator();
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      validator.validate(new StreamSource(file.toFile()));
    } finally {
      schemaStream.close();
    }
  }

  /** Returns whether a document is schema-valid without hiding I/O failures. */
  public static boolean isValid(File file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    try {
      validate(file.toPath());
      return true;
    } catch (SAXException ex) {
      return false;
    }
  }
}
