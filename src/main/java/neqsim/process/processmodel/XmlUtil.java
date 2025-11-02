package neqsim.process.processmodel;

import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Utility class for XML parsing and writing.
 */
public final class XmlUtil {

  private XmlUtil() {}

  /**
   * Creates a new DocumentBuilder with secure processing features enabled.
   *
   * @return a new DocumentBuilder
   * @throws IOException if a parser configuration exception occurs
   */
  public static DocumentBuilder createDocumentBuilder() throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setNamespaceAware(false);
      factory.setExpandEntityReferences(false);
      factory.setXIncludeAware(false);
      return factory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IOException("Unable to create XML document builder", e);
    }
  }
}
