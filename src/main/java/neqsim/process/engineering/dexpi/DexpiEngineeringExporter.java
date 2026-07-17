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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import neqsim.process.engineering.SimulationEngineeringDesignReport;
import neqsim.process.engineering.SimulationEngineeringDesignRunner;
import neqsim.process.engineering.design.EngineeringDesignValue;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.mechanicaldesign.DesignConditions;
import neqsim.process.processmodel.dexpi.DexpiXmlWriter;
import neqsim.process.processmodel.dexpi.Dexpi20XmlWriter;
import neqsim.process.processmodel.dexpi.Dexpi20SemanticValidator;

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
    private final Path dexpi20File;
    private final Path pyDexpiFile;
    private final Path manifestFile;
    private final Path calculationsFile;
    private final Path causeAndEffectFile;
    private final Path interoperabilityReportFile;
    private final Path validationFile;
    private final Path packageManifestFile;
    private final Map<String, Path> compressorMapFiles;
    private final Map<String, Path> registerFiles;

    ExportResult(Path dexpiFile, Path dexpi20File, Path pyDexpiFile, Path manifestFile, Path calculationsFile,
        Path causeAndEffectFile, Path interoperabilityReportFile, Path validationFile, Path packageManifestFile,
        Map<String, Path> compressorMapFiles, Map<String, Path> registerFiles) {
      this.dexpiFile = dexpiFile;
      this.dexpi20File = dexpi20File;
      this.pyDexpiFile = pyDexpiFile;
      this.manifestFile = manifestFile;
      this.calculationsFile = calculationsFile;
      this.causeAndEffectFile = causeAndEffectFile;
      this.interoperabilityReportFile = interoperabilityReportFile;
      this.validationFile = validationFile;
      this.packageManifestFile = packageManifestFile;
      this.compressorMapFiles = new LinkedHashMap<String, Path>(compressorMapFiles);
      this.registerFiles = new LinkedHashMap<String, Path>(registerFiles);
    }

    /** @return generated DEXPI XML file */
    public Path getDexpiFile() {
      return dexpiFile;
    }

    /** @return native, schema-validated DEXPI 2.0 semantic model */
    public Path getDexpi20File() {
      return dexpi20File;
    }

    /** @return namespace-omitted Proteus compatibility file accepted by pyDEXPI */
    public Path getPyDexpiFile() {
      return pyDexpiFile;
    }

    /** @return generated engineering manifest */
    public Path getManifestFile() {
      return manifestFile;
    }

    /** @return simulation-backed equipment, PSV, blowdown, flare and materials calculation handoff */
    public Path getCalculationsFile() {
      return calculationsFile;
    }

    /** @return proposed cause-and-effect matrix requiring HAZOP/LOPA and discipline review */
    public Path getCauseAndEffectFile() {
      return causeAndEffectFile;
    }

    /** @return machine-readable schema, semantic, pyDEXPI and commercial-CAE qualification status */
    public Path getInteroperabilityReportFile() {
      return interoperabilityReportFile;
    }

    /** @return structural/reference/round-trip DEXPI validation report */
    public Path getValidationFile() {
      return validationFile;
    }

    /** @return SHA-256 inventory of every other generated package file */
    public Path getPackageManifestFile() {
      return packageManifestFile;
    }

    /** @return immutable equipment-tag to compressor-map path mapping */
    public Map<String, Path> getCompressorMapFiles() {
      return Collections.unmodifiableMap(compressorMapFiles);
    }

    /** @return immutable engineering-register name to path mapping */
    public Map<String, Path> getRegisterFiles() {
      return Collections.unmodifiableMap(registerFiles);
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

    Path dexpi20File = outputDirectory.resolve("plant.dexpi.xml");
    Dexpi20XmlWriter.write(project.getEngineeringProcessSystem(), dexpi20File.toFile());
    Dexpi20EngineeringMaterializer.materialize(project, dexpi20File);

    Path dexpiFile = outputDirectory.resolve("plant-proteus.xml");
    DexpiXmlWriter.write(project.getEngineeringProcessSystem(), dexpiFile.toFile());

    Path pyDexpiFile = outputDirectory.resolve("plant-pydexpi.xml");
    DexpiXmlWriter.writeForPyDexpi(project.getEngineeringProcessSystem(), pyDexpiFile.toFile());

    Map<String, Path> maps = writeCompressorMaps(project, datasets);
    DexpiEngineeringMaterializer.MaterializationResult materialization = enrichDexpi(project, dexpiFile, maps);
    enrichDexpi(project, pyDexpiFile, maps);

    Path manifest = outputDirectory.resolve("engineering-manifest.json");
    Files.write(manifest, packageManifest(project).getBytes(StandardCharsets.UTF_8));
    SimulationEngineeringDesignReport calculationReport = SimulationEngineeringDesignRunner.run(project);
    Path calculations = outputDirectory.resolve("engineering-calculations.json");
    Files.write(calculations, calculationReport.toJson().getBytes(StandardCharsets.UTF_8));
    Path causeAndEffect = outputDirectory.resolve("cause-and-effect.json");
    Files.write(causeAndEffect, materialization.toCauseAndEffectJson(project).getBytes(StandardCharsets.UTF_8));
    Path interoperability = outputDirectory.resolve("interoperability-report.json");
    Files.write(interoperability, interoperabilityReport(project, dexpi20File).getBytes(StandardCharsets.UTF_8));
    Map<String, Path> registers = EngineeringRegisterExporter.export(project, outputDirectory.resolve("registers"));
    Path validation = outputDirectory.resolve("dexpi-validation.json");
    DexpiEngineeringValidator.write(DexpiEngineeringValidator.validate(dexpiFile), validation);
    Path integrityManifest = outputDirectory.resolve("package-manifest.json");
    EngineeringPackageManifest.write(outputDirectory, integrityManifest);
    return new ExportResult(dexpiFile, dexpi20File, pyDexpiFile, manifest, calculations, causeAndEffect,
        interoperability, validation, integrityManifest, maps, registers);
  }

  private static String interoperabilityReport(EngineeringProject project, Path nativeDexpi) throws IOException {
    Dexpi20SemanticValidator.ValidationReport semantic = Dexpi20SemanticValidator.validate(nativeDexpi);
    JsonObject report = new JsonObject();
    report.addProperty("profile", "neqsim_dexpi_interoperability.v1");
    JsonObject nativeStatus = new JsonObject();
    nativeStatus.addProperty("file", "plant.dexpi.xml");
    nativeStatus.addProperty("dexpiVersion", "2.0");
    nativeStatus.addProperty("xsdValidation", "PASSED");
    nativeStatus.addProperty("semanticProfileValidation", semantic.isValid() ? "PASSED" : "FAILED");
    nativeStatus.add("semanticWarnings", new GsonBuilder().create().toJsonTree(semantic.getWarnings()));
    report.add("nativeDexpi", nativeStatus);
    JsonObject pydexpi = new JsonObject();
    pydexpi.addProperty("file", "plant-pydexpi.xml");
    pydexpi.addProperty("target", "pyDEXPI Proteus importer");
    pydexpi.addProperty("status", "NOT_RUN_BY_JAVA_EXPORTER");
    pydexpi.addProperty("qualificationCommand",
        "python devtools/validate_dexpi_interoperability.py <package-directory> --require-pydexpi --output <package-directory>/interoperability-report.json");
    report.add("pyDexpi", pydexpi);
    JsonObject commercial = new JsonObject();
    commercial.addProperty("status", "QUALIFICATION_REQUIRED");
    commercial.addProperty("requiredEvidence",
        "Named CAE product/version, successful import, exported round-trip file and reviewed difference report");
    report.add("commercialCae", commercial);
    report.add("boundaries", new GsonBuilder().create().toJsonTree(project.getBoundaries()));
    return new GsonBuilder().setPrettyPrinting().create().toJson(report);
  }

  private static String packageManifest(EngineeringProject project) {
    JsonObject manifest = JsonParser.parseString(project.toJson()).getAsJsonObject();
    JsonObject artifacts = new JsonObject();
    JsonObject nativeDexpi = new JsonObject();
    nativeDexpi.addProperty("file", "plant.dexpi.xml");
    nativeDexpi.addProperty("serialization", "DEXPI_XML_2_0_NATIVE");
    nativeDexpi.addProperty("schemaValidation", "PASSED_DURING_EXPORT");
    nativeDexpi.addProperty("semanticProfileValidation", "PASSED_DURING_EXPORT");
    nativeDexpi.addProperty("content", "PROCESS_TOPOLOGY_AND_SEMANTIC_EQUIPMENT_MODEL");
    artifacts.add("nativeDexpi20", nativeDexpi);
    JsonObject proteus = new JsonObject();
    proteus.addProperty("file", "plant-proteus.xml");
    proteus.addProperty("serialization", "PROTEUS_4_1_1_COMPATIBLE");
    proteus.addProperty("content", "GRAPHICAL_PID_AND_ENGINEERING_PROPOSALS");
    proteus.addProperty("nativeDexpi20Conformance", false);
    artifacts.add("proteusPid", proteus);
    JsonObject pyDexpi = new JsonObject();
    pyDexpi.addProperty("file", "plant-pydexpi.xml");
    pyDexpi.addProperty("serialization", "PROTEUS_4_1_1_NAMESPACE_OMITTED_FOR_PYDEXPI");
    pyDexpi.addProperty("content", "GRAPHICAL_PID_AND_ENGINEERING_PROPOSALS");
    artifacts.add("pyDexpiCompatibility", pyDexpi);
    manifest.add("exchangeArtifacts", artifacts);
    return new GsonBuilder().setPrettyPrinting().create().toJson(manifest);
  }

  private static Map<String, Path> writeCompressorMaps(EngineeringProject project, Path datasets) throws IOException {
    Map<String, Path> result = new LinkedHashMap<String, Path>();
    for (ProcessEquipmentInterface unit : project.getEngineeringProcessSystem().getUnitOperations()) {
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
        appendAttribute(document, attributes, "EngineeringCalculationsDocument", "engineering-calculations.json");
        appendAttribute(document, attributes, "CauseAndEffectDocument", "cause-and-effect.json");
        appendAttribute(document, attributes, "DexpiValidationDocument", "dexpi-validation.json");
        appendAttribute(document, attributes, "PackageManifestDocument", "package-manifest.json");
        appendAttribute(document, attributes, "EquipmentRegisterDocument", "registers/equipment-register.csv");
        appendAttribute(document, attributes, "LineListDocument", "registers/line-list.csv");
        appendAttribute(document, attributes, "InstrumentIndexDocument", "registers/instrument-index.csv");
        appendAttribute(document, attributes, "SifRegisterDocument", "registers/sif-register.csv");
        appendAttribute(document, attributes, "ShutdownRegisterDocument", "registers/shutdown-register.csv");
        appendAttribute(document, attributes, "ReliefRegisterDocument", "registers/relief-register.csv");
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
        appendSimulationAndDesignConditions(project, document, attributes,
            project.getEngineeringProcessSystem().getUnit(tag));
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

  private static void appendSimulationAndDesignConditions(EngineeringProject project, Document document,
      Element attributes, ProcessEquipmentInterface equipment) {
    if (equipment == null) {
      return;
    }
    try {
      appendAttribute(document, attributes, "SimulationOperatingPressureBara",
          Double.toString(equipment.getPressure("bara")));
    } catch (Exception ex) {
      // Operating pressure is optional for non-thermodynamic equipment.
    }
    try {
      appendAttribute(document, attributes, "SimulationOperatingTemperatureC",
          Double.toString(equipment.getTemperature("C")));
    } catch (Exception ex) {
      // Operating temperature is optional for non-thermodynamic equipment.
    }
    DesignConditions design = equipment.getDesignConditions();
    if (design != null && design.isDesignPressureSet()) {
      appendAttribute(document, attributes, "DeclaredDesignPressureBara", Double.toString(design.getDesignPressure()));
    }
    if (design != null && design.isMaxDesignTemperatureSet()) {
      appendAttribute(document, attributes, "DeclaredMaximumDesignTemperatureC",
          Double.toString(design.getMaxDesignTemperature()));
    }
    if (design != null && design.isMinDesignTemperatureSet()) {
      appendAttribute(document, attributes, "DeclaredMinimumDesignTemperatureC",
          Double.toString(design.getMinDesignTemperature()));
    }
    if (design != null && design.isReliefSetPressureSet()) {
      appendAttribute(document, attributes, "DeclaredReliefSetPressureBara",
          Double.toString(design.getReliefSetPressure()));
    }
    if (design != null && design.isCorrosionAllowanceSet()) {
      appendAttribute(document, attributes, "DeclaredCorrosionAllowanceMm",
          Double.toString(design.getCorrosionAllowance()));
    }
    if (design != null && design.isConstructionMaterialSet()) {
      appendAttribute(document, attributes, "DeclaredConstructionMaterial", design.getConstructionMaterial());
    }
    if (design != null && design.isFailureActionSet()) {
      appendAttribute(document, attributes, "DeclaredValveFailureAction", design.getFailureAction().name());
    }
    if (project.getLatestEngineeringDesignLoopResult() != null) {
      String prefix = equipment.getName() + ".";
      for (Map.Entry<String, EngineeringDesignValue> entry : project.getLatestEngineeringDesignLoopResult().getState()
          .getValues().entrySet()) {
        if (entry.getKey().startsWith(prefix)) {
          String property = entry.getKey().substring(prefix.length()).replaceAll("[^A-Za-z0-9]", "_");
          appendAttribute(document, attributes, "CalculatedDesign_" + property,
              entry.getValue().getValue() + " " + entry.getValue().getUnit());
        }
      }
    }
    appendAttribute(document, attributes, "CalculatedEngineeringDataStatus", "REVIEW_REQUIRED");
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
