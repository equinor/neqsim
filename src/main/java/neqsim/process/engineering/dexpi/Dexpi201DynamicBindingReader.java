package neqsim.process.engineering.dexpi;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.processmodel.dexpi.Dexpi201XmlWriter;
import neqsim.process.processmodel.dexpi.DexpiActuatingFunctionInfo;
import neqsim.process.processmodel.dexpi.DexpiInformationFlowInfo;
import neqsim.process.processmodel.dexpi.DexpiInstrumentInfo;
import neqsim.process.processmodel.dexpi.DexpiXmlReader;
import neqsim.process.processmodel.dexpi.DexpiXmlReaderException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads a controlled native DEXPI 2.0.1 P&amp;ID into revision-bound executable simulation bindings.
 *
 * <p>
 * The DEXPI document supplies exact drawing identities and relationships. The companion binding artifact supplies the
 * explicit mapping to a governed executable model; this reader never infers tuning, set points, fail action, SIL,
 * voting, fluid state, or missing process physics from drawing metadata. Any duplicate, missing, unresolved,
 * type-incompatible, or stale identity fails before a caller can execute a scenario.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class Dexpi201DynamicBindingReader {
  /** Prevents instantiation. */
  private Dexpi201DynamicBindingReader() {
  }

  /** Verified DEXPI source evidence and the executable binding register it authorizes. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final PidSimulationBindings bindings;
    private final String sourceSha256;
    private final List<String> verifiedTags;
    private final List<Map<String, String>> verifiedSignals;
    private final List<Map<String, String>> verifiedActuations;

    /**
     * Captures verified source evidence and its restored executable binding register.
     *
     * @param verifiedBindings exact restored runtime bindings
     * @param sha256 exact DEXPI byte fingerprint
     * @param tags exact verified P&amp;ID tags
     * @param signals exact verified source-target signal pairs
     * @param actuations exact verified function-final-element pairs
     */
    private Result(PidSimulationBindings verifiedBindings, String sha256, List<String> tags,
        List<Map<String, String>> signals, List<Map<String, String>> actuations) {
      bindings = verifiedBindings;
      sourceSha256 = sha256;
      verifiedTags = Collections.unmodifiableList(new ArrayList<String>(tags));
      verifiedSignals = Collections.unmodifiableList(copyEvidenceRows(signals));
      verifiedActuations = Collections.unmodifiableList(copyEvidenceRows(actuations));
    }

    /** @return revision-bound bindings ready for the existing strict scenario preflight */
    public PidSimulationBindings getBindings() {
      return bindings;
    }

    /** @return SHA-256 of the exact accepted DEXPI bytes */
    public String getSourceSha256() {
      return sourceSha256;
    }

    /** @return exact DEXPI/P&amp;ID tags checked against runtime roles */
    public List<String> getVerifiedTags() {
      return verifiedTags;
    }

    /** @return exact DEXPI signal pairs checked against the P&amp;ID graph */
    public List<Map<String, String>> getVerifiedSignals() {
      return verifiedSignals;
    }

    /** @return exact DEXPI actuation pairs checked against final elements */
    public List<Map<String, String>> getVerifiedActuations() {
      return verifiedActuations;
    }

    /** @return deterministic verification evidence without live simulation objects */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_dexpi_201_dynamic_binding_intake.v1");
      result.put("dexpiSpecificationVersion", "2.0.1");
      result.put("sourceSha256", sourceSha256);
      result.put("verifiedTags", verifiedTags);
      result.put("verifiedSignals", verifiedSignals);
      result.put("verifiedActuations", verifiedActuations);
      result.put("bindingEvidence", bindings.toMap());
      result.put("scope", "CONTROLLED_NATIVE_DEXPI_201_AND_EXPLICIT_BINDING_ARTIFACT");
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("fitnessForConstruction", Boolean.FALSE);
      return result;
    }

    /** @return deterministic JSON verification evidence */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }

    /** Copies evidence rows into a new list with immutable entries. */
    private static List<Map<String, String>> copyEvidenceRows(List<Map<String, String>> rows) {
      List<Map<String, String>> result = new ArrayList<Map<String, String>>();
      for (Map<String, String> row : rows) {
        result.add(Collections.unmodifiableMap(new LinkedHashMap<String, String>(row)));
      }
      return result;
    }
  }

  /**
   * Verifies one native DEXPI 2.0.1 document and restores only its explicit current runtime bindings.
   *
   * @param file native DEXPI 2.0.1 Plant P&amp;ID
   * @param project current governed executable project
   * @param model current governed P&amp;ID proposal model
   * @param bindingJson explicit revision-bound binding artifact retained with the DEXPI file
   * @return verified source evidence and executable bindings
   * @throws IOException when the source cannot be read or assessed
   */
  public static Result read(Path file, EngineeringProject project, PidDesignModel model, String bindingJson)
      throws IOException {
    if (file == null || project == null || model == null || bindingJson == null) {
      throw new IllegalArgumentException("file, project, model and bindingJson are required");
    }
    Document document = parse(file);
    if (!Dexpi201XmlWriter.isSpecification201(document)) {
      throw new IllegalArgumentException("Dynamic binding intake requires the exact DEXPI 2.0.1 Plant profile");
    }
    assertUniqueNativeIds(document);
    if (!Dexpi201XmlWriter.assess(file).isSchemaAndProfileConformant()) {
      throw new IllegalArgumentException("DEXPI 2.0.1 source is not schema/profile conformant");
    }
    PidSimulationBindings bindings = PidSimulationBindings.fromJson(project, model, bindingJson);
    bindings.assertCurrent(project, model);
    DexpiXmlReader.ImportResult imported;
    try {
      imported = DexpiXmlReader.readWithDiagnostics(file.toFile());
    } catch (DexpiXmlReaderException exception) {
      throw new IOException("Could not read supported DEXPI instrumentation evidence", exception);
    }
    Map<String, PidElement> elements = elementsByTag(model);
    Map<String, List<Element>> nativeByTag = nativeObjectsByTag(document);
    Map<String, DexpiInstrumentInfo> instruments = instrumentsByTag(imported.getInstruments());
    List<String> verifiedTags = verifyBindings(bindings, elements, nativeByTag, instruments,
        imported.getActuatingFunctions(), imported.getInformationFlows());
    List<Map<String, String>> signals = verifySignals(model, imported.getInformationFlows());
    List<Map<String, String>> actuations = verifyActuations(bindings, elements, imported.getActuatingFunctions());
    return new Result(bindings, sha256(Files.readAllBytes(file)), verifiedTags, signals, actuations);
  }

  /** Verifies every runtime binding against one exact native object/function role. */
  @SuppressWarnings("unchecked")
  private static List<String> verifyBindings(PidSimulationBindings bindings, Map<String, PidElement> elements,
      Map<String, List<Element>> nativeByTag, Map<String, DexpiInstrumentInfo> instruments,
      List<DexpiActuatingFunctionInfo> actuatingFunctions, List<DexpiInformationFlowInfo> informationFlows) {
    List<String> result = new ArrayList<String>();
    for (Map<String, Object> row : (List<Map<String, Object>>) bindings.toMap().get("bindings")) {
      String tag = String.valueOf(row.get("pidTag"));
      String role = String.valueOf(row.get("role"));
      String simulationName = String.valueOf(row.get("simulationName"));
      PidElement element = elements.get(tag);
      if (element == null) {
        throw new IllegalArgumentException("DEXPI binding references unknown P&ID tag: " + tag);
      }
      if ("MEASUREMENT".equals(role) || "CONTROLLER".equals(role)) {
        if (!instruments.containsKey(tag)) {
          throw new IllegalArgumentException("DEXPI 2.0.1 function is missing for bound tag: " + tag);
        }
        requireOneNativeType(nativeByTag, tag, "Plant/Instrumentation.ProcessInstrumentationFunction",
            "Plant/Instrumentation.ProcessSafetyFunction");
        if ("MEASUREMENT".equals(role) && !hasResolvedSensing(tag, informationFlows)) {
          throw new IllegalArgumentException("DEXPI sensing location is missing or unresolved for tag: " + tag);
        }
      } else if ("EQUIPMENT".equals(role)) {
        requirePhysicalNativeObject(nativeByTag, simulationName);
        if (isActuated(element.getType()) && !hasActuation(tag, simulationName, actuatingFunctions)) {
          throw new IllegalArgumentException("DEXPI actuation does not resolve bound final element for tag: " + tag);
        }
      } else {
        throw new IllegalArgumentException("Unsupported runtime binding role: " + role);
      }
      result.add(tag);
    }
    return Collections.unmodifiableList(result);
  }

  /** Verifies every declared functional connection that the controlled native profile can represent. */
  private static List<Map<String, String>> verifySignals(PidDesignModel model,
      List<DexpiInformationFlowInfo> informationFlows) {
    Map<String, PidElement> byId = new LinkedHashMap<String, PidElement>();
    Set<String> nativeSignals = new LinkedHashSet<String>();
    for (PidElement element : model.getElements()) {
      byId.put(element.getId(), element);
    }
    for (DexpiInformationFlowInfo flow : informationFlows) {
      if (flow.getKind() == DexpiInformationFlowInfo.Kind.SIGNAL_LINE && flow.isSourceResolved()
          && flow.isTargetResolved()) {
        nativeSignals.add(flow.getSourceTagName() + "\u0000" + flow.getTargetTagName());
      }
    }
    List<Map<String, String>> result = new ArrayList<Map<String, String>>();
    for (PidElement source : model.getElements()) {
      for (String targetId : source.getConnectedElementIds()) {
        PidElement target = byId.get(targetId);
        if (target == null || !isFunctional(source.getType()) || !isFunctional(target.getType())) {
          continue;
        }
        String key = source.getTag() + "\u0000" + target.getTag();
        if (!nativeSignals.contains(key)) {
          throw new IllegalArgumentException(
              "DEXPI signal is missing or unresolved: " + source.getTag() + " -> " + target.getTag());
        }
        Map<String, String> row = new LinkedHashMap<String, String>();
        row.put("sourceTag", source.getTag());
        row.put("targetTag", target.getTag());
        result.add(row);
      }
    }
    return result;
  }

  /** Verifies every bound actuated final element and records its exact relationship. */
  @SuppressWarnings("unchecked")
  private static List<Map<String, String>> verifyActuations(PidSimulationBindings bindings,
      Map<String, PidElement> elements, List<DexpiActuatingFunctionInfo> functions) {
    List<Map<String, String>> result = new ArrayList<Map<String, String>>();
    for (Map<String, Object> row : (List<Map<String, Object>>) bindings.toMap().get("bindings")) {
      String tag = String.valueOf(row.get("pidTag"));
      String simulationName = String.valueOf(row.get("simulationName"));
      PidElement element = elements.get(tag);
      if (!"EQUIPMENT".equals(row.get("role")) || element == null || !isActuated(element.getType())) {
        continue;
      }
      for (DexpiActuatingFunctionInfo function : functions) {
        if (tag.equals(function.getFunctionNumber()) && simulationName.equals(function.getFinalControlElementTagName())
            && function.isInstrumentationFunctionResolved() && function.isFinalControlElementResolved()) {
          Map<String, String> evidence = new LinkedHashMap<String, String>();
          evidence.put("functionTag", tag);
          evidence.put("simulationFinalElement", simulationName);
          evidence.put("nativeActuatingFunctionId", function.getId());
          evidence.put("nativeFinalElementId", function.getFinalControlElementId());
          result.add(evidence);
          break;
        }
      }
    }
    return result;
  }

  /** Returns whether one exact actuation relationship exists. */
  private static boolean hasActuation(String functionTag, String finalElementTag,
      List<DexpiActuatingFunctionInfo> functions) {
    for (DexpiActuatingFunctionInfo function : functions) {
      if (functionTag.equals(function.getFunctionNumber())
          && finalElementTag.equals(function.getFinalControlElementTagName())
          && function.isInstrumentationFunctionResolved() && function.isFinalControlElementResolved()) {
        return true;
      }
    }
    return false;
  }

  /** Returns whether a native measurement has one explicit resolved process attachment. */
  private static boolean hasResolvedSensing(String functionTag, List<DexpiInformationFlowInfo> flows) {
    for (DexpiInformationFlowInfo flow : flows) {
      if (flow.getKind() == DexpiInformationFlowInfo.Kind.MEASURING_LINE && functionTag.equals(flow.getTargetTagName())
          && flow.isSourceResolved() && flow.isTargetResolved() && flow.hasAttachment()
          && flow.isAttachmentResolved()) {
        return true;
      }
    }
    return false;
  }

  /** Indexes duplicate-checked governed P&amp;ID tags. */
  private static Map<String, PidElement> elementsByTag(PidDesignModel model) {
    Map<String, PidElement> result = new LinkedHashMap<String, PidElement>();
    for (PidElement element : model.getElements()) {
      if (result.put(element.getTag(), element) != null) {
        throw new IllegalArgumentException("Duplicate governed P&ID tag: " + element.getTag());
      }
    }
    return result;
  }

  /** Indexes duplicate-checked native instrumentation function tags. */
  private static Map<String, DexpiInstrumentInfo> instrumentsByTag(List<DexpiInstrumentInfo> instruments) {
    Map<String, DexpiInstrumentInfo> result = new LinkedHashMap<String, DexpiInstrumentInfo>();
    for (DexpiInstrumentInfo instrument : instruments) {
      if (instrument.getTagName() == null || instrument.getTagName().trim().isEmpty()) {
        continue;
      }
      if (result.put(instrument.getTagName(), instrument) != null) {
        throw new IllegalArgumentException("Duplicate DEXPI instrumentation tag: " + instrument.getTagName());
      }
    }
    return result;
  }

  /** Indexes all exact native tag properties without collapsing intentional valve/function pairs. */
  private static Map<String, List<Element>> nativeObjectsByTag(Document document) {
    Map<String, List<Element>> result = new LinkedHashMap<String, List<Element>>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      for (String property : new String[] {"TagName", "PipingComponentNumber", "PositionNumber", "PipeConnectorNumber",
          "SubTagName", "ProcessInstrumentationFunctionNumber"}) {
        String tag = stringData(object, property);
        if (tag.isEmpty()) {
          continue;
        }
        if (!result.containsKey(tag)) {
          result.put(tag, new ArrayList<Element>());
        }
        if (!result.get(tag).contains(object)) {
          result.get(tag).add(object);
        }
      }
    }
    return result;
  }

  /** Requires exactly one matching native function type. */
  private static void requireOneNativeType(Map<String, List<Element>> nativeByTag, String tag, String firstType,
      String secondType) {
    int matches = 0;
    for (Element object : nativeByTag.containsKey(tag) ? nativeByTag.get(tag) : Collections.<Element>emptyList()) {
      if (firstType.equals(object.getAttribute("type")) || secondType.equals(object.getAttribute("type"))) {
        matches++;
      }
    }
    if (matches != 1) {
      throw new IllegalArgumentException("Expected one native DEXPI function for tag " + tag + ", found " + matches);
    }
  }

  /** Requires exactly one native physical object with the bound runtime name. */
  private static void requirePhysicalNativeObject(Map<String, List<Element>> nativeByTag, String simulationName) {
    int matches = 0;
    for (Element object : nativeByTag.containsKey(simulationName) ? nativeByTag.get(simulationName)
        : Collections.<Element>emptyList()) {
      String type = object.getAttribute("type");
      if (type.startsWith("Plant/Piping.") || type.startsWith("Plant/ProcessEquipment.")) {
        matches++;
      }
    }
    if (matches != 1) {
      throw new IllegalArgumentException(
          "Expected one native physical object for runtime element " + simulationName + ", found " + matches);
    }
  }

  /** Returns whether the proposal requires native actuation evidence. */
  private static boolean isActuated(PidElementType type) {
    return type == PidElementType.CONTROL_VALVE || type == PidElementType.SHUTDOWN_VALVE
        || type == PidElementType.ISOLATION_VALVE || type == PidElementType.BLOWDOWN_VALVE;
  }

  /** Returns whether a proposal can be a native signal endpoint. */
  private static boolean isFunctional(PidElementType type) {
    return type == PidElementType.MEASUREMENT || type == PidElementType.INDICATOR || type == PidElementType.CONTROLLER
        || type == PidElementType.TRIP || type == PidElementType.SAFETY_FUNCTION || isActuated(type);
  }

  /** Reads one direct native string property. */
  private static String stringData(Element object, String property) {
    for (Node node = object.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element && "Data".equals(((Element) node).getTagName())
          && property.equals(((Element) node).getAttribute("property"))) {
        return node.getTextContent().trim();
      }
    }
    return "";
  }

  /** Securely parses one native document. */
  private static Document parse(Path file) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().parse(file.toFile());
    } catch (Exception exception) {
      throw new IOException("Could not parse native DEXPI 2.0.1 dynamic binding source", exception);
    }
  }

  /** Rejects duplicate non-empty native object identities before any relationship is resolved. */
  private static void assertUniqueNativeIds(Document document) {
    Set<String> identities = new LinkedHashSet<String>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      String id = ((Element) objects.item(index)).getAttribute("id").trim();
      if (!id.isEmpty() && !identities.add(id)) {
        throw new IllegalArgumentException("Duplicate native DEXPI object identity: " + id);
      }
    }
  }

  /** Returns a lowercase SHA-256 fingerprint. */
  private static String sha256(byte[] content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
      StringBuilder result = new StringBuilder();
      for (byte value : digest) {
        result.append(String.format("%02x", Integer.valueOf(value & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
    }
  }
}
