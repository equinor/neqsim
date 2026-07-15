package neqsim.process.engineering.dexpi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.EngineeringStandard;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.processmodel.dexpi.DexpiXmlWriter;

/**
 * Exports a governed engineering project as DEXPI XML plus lossless engineering sidecars.
 *
 * <p>
 * DEXPI carries the plant topology, equipment, instrumentation and graphical model. Large compressor maps and the full
 * review/approval manifest are written as JSON sidecars and referenced from the DEXPI equipment attributes. This avoids
 * flattening multidimensional vendor data into P&amp;ID attributes while retaining a machine-readable package.
 * </p>
 */
public final class DexpiEngineeringExporter {
  private DexpiEngineeringExporter() {
  }

  /** Result of an engineering package export. */
  public static final class ExportResult {
    private final Path dexpiFile;
    private final Path manifestFile;
    private final Path causeAndEffectFile;
    private final Map<String, Path> compressorMapFiles;

    ExportResult(Path dexpiFile, Path manifestFile, Path causeAndEffectFile, Map<String, Path> compressorMapFiles) {
      this.dexpiFile = dexpiFile;
      this.manifestFile = manifestFile;
      this.causeAndEffectFile = causeAndEffectFile;
      this.compressorMapFiles = new LinkedHashMap<String, Path>(compressorMapFiles);
    }

    /** @return generated DEXPI XML file */
    public Path getDexpiFile() {
      return dexpiFile;
    }

    /** @return generated engineering manifest */
    public Path getManifestFile() {
      return manifestFile;
    }

    /** @return proposed cause-and-effect matrix requiring HAZOP/LOPA and discipline review */
    public Path getCauseAndEffectFile() {
      return causeAndEffectFile;
    }

    /** @return immutable equipment-tag to compressor-map path mapping */
    public Map<String, Path> getCompressorMapFiles() {
      return Collections.unmodifiableMap(compressorMapFiles);
    }
  }

  /**
   * Writes a complete exchange package.
   *
   * @param project engineering project
   * @param outputDirectory target directory
   * @return paths to generated files
   * @throws IOException if a package file cannot be written
   */
  public static ExportResult export(EngineeringProject project, Path outputDirectory) throws IOException {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    if (outputDirectory == null) {
      throw new IllegalArgumentException("outputDirectory must not be null");
    }
    if (project.validate().hasErrors()) {
      throw new IllegalStateException("Engineering project has blocking validation errors");
    }
    Files.createDirectories(outputDirectory);
    Path datasets = outputDirectory.resolve("datasets");
    Files.createDirectories(datasets);

    Path dexpiFile = outputDirectory.resolve("plant.dexpi.xml");
    DexpiXmlWriter.writeDexpi20(project.getProcessSystem(), dexpiFile.toFile());

    Map<String, Path> maps = writeCompressorMaps(project, datasets);
    DexpiEngineeringMaterializer.MaterializationResult materialization = enrichDexpi(project, dexpiFile, maps);

    Path manifest = outputDirectory.resolve("engineering-manifest.json");
    Files.write(manifest, project.toJson().getBytes(StandardCharsets.UTF_8));
    Path causeAndEffect = outputDirectory.resolve("cause-and-effect.json");
    Files.write(causeAndEffect, materialization.toCauseAndEffectJson(project).getBytes(StandardCharsets.UTF_8));
    return new ExportResult(dexpiFile, manifest, causeAndEffect, maps);
  }

  private static Map<String, Path> writeCompressorMaps(EngineeringProject project, Path datasets) throws IOException {
    Map<String, Path> result = new LinkedHashMap<String, Path>();
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (!(unit instanceof Compressor)) {
        continue;
      }
      Compressor compressor = (Compressor) unit;
      if (compressor.getCompressorChart() == null || !compressor.getCompressorChart().isUseCompressorChart()) {
        continue;
      }
      String fileName = safeFileName(compressor.getName()) + "-compressor-map.json";
      Path mapFile = datasets.resolve(fileName);
      Files.write(mapFile, compressor.getCompressorChartAsJson().getBytes(StandardCharsets.UTF_8));
      result.put(compressor.getName(), mapFile);
    }
    return result;
  }

  private static DexpiEngineeringMaterializer.MaterializationResult enrichDexpi(EngineeringProject project,
      Path dexpiFile, Map<String, Path> maps) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      Document document = factory.newDocumentBuilder().parse(dexpiFile.toFile());

      Element plantInformation = firstElement(document, "PlantInformation");
      if (plantInformation != null) {
        Element attributes = document.createElement("GenericAttributes");
        attributes.setAttribute("Set", "NeqSimEngineeringProject");
        appendAttribute(document, attributes, "EngineeringProjectId", project.getProjectId());
        appendAttribute(document, attributes, "EngineeringProjectName", project.getName());
        appendAttribute(document, attributes, "Jurisdiction", project.getDesignBasis().getJurisdiction());
        appendAttribute(document, attributes, "FacilityType", project.getDesignBasis().getFacilityType());
        appendAttribute(document, attributes, "ProjectPhase", project.getDesignBasis().getProjectPhase());
        appendAttribute(document, attributes, "EngineeringManifestDocument", "engineering-manifest.json");
        appendAttribute(document, attributes, "CauseAndEffectDocument", "cause-and-effect.json");
        appendAttribute(document, attributes, "EngineeringApprovalState", "REVIEW_REQUIRED");
        appendAttribute(document, attributes, "Standards", joinStandards(project.getDesignBasis().getStandards()));
        plantInformation.appendChild(attributes);
      }

      NodeList equipmentNodes = document.getElementsByTagName("Equipment");
      for (int i = 0; i < equipmentNodes.getLength(); i++) {
        Element equipment = (Element) equipmentNodes.item(i);
        String tag = findTag(equipment);
        if (tag == null) {
          continue;
        }
        List<EngineeringRequirement> requirements = project.getRequirementsForEquipment(tag);
        Path map = maps.get(tag);
        if (requirements.isEmpty() && map == null) {
          continue;
        }
        Element attributes = document.createElement("GenericAttributes");
        attributes.setAttribute("Set", "EngineeringReferences");
        if (!requirements.isEmpty()) {
          appendAttribute(document, attributes, "EngineeringRequirementIds", joinRequirementIds(requirements));
          appendAttribute(document, attributes, "EngineeringRequirementApproval", "REVIEW_REQUIRED");
        }
        if (map != null) {
          appendAttribute(document, attributes, "CompressorPerformanceMapDocument",
              "datasets/" + map.getFileName().toString());
          appendAttribute(document, attributes, "CompressorMapDataOrigin", "SIMULATION_OR_VENDOR_INPUT");
        }
        equipment.appendChild(attributes);
      }

      DexpiEngineeringMaterializer.MaterializationResult materialization = DexpiEngineeringMaterializer
          .materialize(project, document);

      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.transform(new DOMSource(document), new StreamResult(dexpiFile.toFile()));
      return materialization;
    } catch (Exception ex) {
      throw new IOException("Could not enrich DEXPI engineering package", ex);
    }
  }

  private static Element firstElement(Document document, String name) {
    NodeList nodes = document.getElementsByTagName(name);
    return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
  }

  private static String findTag(Element equipment) {
    NodeList attributes = equipment.getElementsByTagName("GenericAttribute");
    for (int i = 0; i < attributes.getLength(); i++) {
      Node node = attributes.item(i);
      if (node instanceof Element) {
        Element attribute = (Element) node;
        if ("TagNameAssignmentClass".equals(attribute.getAttribute("Name"))) {
          return attribute.getAttribute("Value");
        }
      }
    }
    return null;
  }

  private static void appendAttribute(Document document, Element parent, String name, String value) {
    if (value == null || value.trim().isEmpty()) {
      return;
    }
    Element attribute = document.createElement("GenericAttribute");
    attribute.setAttribute("Name", name);
    attribute.setAttribute("Value", value);
    parent.appendChild(attribute);
  }

  private static String joinStandards(List<EngineeringStandard> standards) {
    List<String> references = new ArrayList<String>();
    for (EngineeringStandard standard : standards) {
      references.add(standard.getReference());
    }
    return join(references);
  }

  private static String joinRequirementIds(List<EngineeringRequirement> requirements) {
    List<String> ids = new ArrayList<String>();
    for (EngineeringRequirement requirement : requirements) {
      ids.add(requirement.getId());
    }
    return join(ids);
  }

  private static String join(List<String> values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      if (result.length() > 0) {
        result.append(';');
      }
      result.append(value);
    }
    return result.toString();
  }

  private static String safeFileName(String name) {
    String result = name == null ? "compressor" : name.replaceAll("[^A-Za-z0-9_-]", "-");
    return result.isEmpty() ? "compressor" : result;
  }
}
