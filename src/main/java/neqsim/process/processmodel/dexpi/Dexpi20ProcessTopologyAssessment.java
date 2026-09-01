package neqsim.process.processmodel.dexpi;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.ProcessDiagramGraphAdapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Compares native DEXPI 2.0 Process material topology with the canonical diagram graph. */
public final class Dexpi20ProcessTopologyAssessment {
  private Dexpi20ProcessTopologyAssessment() {
  }

  /** Severity of one structured topology or export-scope diagnostic. */
  public enum Severity {
    INFO, WARNING, ERROR
  }

  /** Immutable structured topology or scope diagnostic. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subject;

    Diagnostic(Severity severity, String code, String message, String subject) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.subject = subject == null ? "" : subject;
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getCode() {
      return code;
    }

    public String getMessage() {
      return message;
    }

    public String getSubject() {
      return subject;
    }

    @Override
    public String toString() {
      return severity + ":" + code + (subject.isEmpty() ? "" : ":" + subject);
    }
  }

  /** One exported DEXPI material connection with explicit port identities. */
  public static final class ExportedConnection implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String streamId;
    private final String streamLabel;
    private final String sourceStep;
    private final String targetStep;
    private final String sourcePortId;
    private final String targetPortId;
    private final boolean boundaryTarget;

    ExportedConnection(String streamId, String streamLabel, String sourceStep, String targetStep, String sourcePortId,
        String targetPortId, boolean boundaryTarget) {
      this.streamId = streamId;
      this.streamLabel = streamLabel;
      this.sourceStep = sourceStep;
      this.targetStep = targetStep;
      this.sourcePortId = sourcePortId;
      this.targetPortId = targetPortId;
      this.boundaryTarget = boundaryTarget;
    }

    public String getStreamId() {
      return streamId;
    }

    public String getStreamLabel() {
      return streamLabel;
    }

    public String getSourceStep() {
      return sourceStep;
    }

    public String getTargetStep() {
      return targetStep;
    }

    public String getSourcePortId() {
      return sourcePortId;
    }

    public String getTargetPortId() {
      return targetPortId;
    }

    public boolean isBoundaryTarget() {
      return boundaryTarget;
    }

    String materialKey() {
      return sourceStep + "->" + targetStep;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("streamId", streamId);
      result.put("streamLabel", streamLabel);
      result.put("sourceStep", sourceStep);
      result.put("targetStep", targetStep);
      result.put("sourcePortId", sourcePortId);
      result.put("targetPortId", targetPortId);
      result.put("boundaryTarget", Boolean.valueOf(boundaryTarget));
      return result;
    }
  }

  /** Immutable conformance and canonical-topology evidence for one Process exchange. */
  public static final class Report implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Dexpi20ConformanceAssessment.Report conformanceReport;
    private final String exportTopologySource;
    private final String exportOperatingValueSource;
    private final String canonicalFingerprint;
    private final List<String> canonicalConnectionIds;
    private final List<String> canonicalMaterialConnections;
    private final List<String> exportedMaterialConnections;
    private final List<ExportedConnection> exportedConnections;
    private final List<Diagnostic> diagnostics;

    Report(Dexpi20ConformanceAssessment.Report conformanceReport, String exportTopologySource,
        String exportOperatingValueSource, String canonicalFingerprint, List<String> canonicalConnectionIds,
        List<String> canonicalMaterialConnections, List<String> exportedMaterialConnections,
        List<ExportedConnection> exportedConnections, List<Diagnostic> diagnostics) {
      this.conformanceReport = conformanceReport;
      this.exportTopologySource = exportTopologySource;
      this.exportOperatingValueSource = exportOperatingValueSource;
      this.canonicalFingerprint = canonicalFingerprint;
      this.canonicalConnectionIds = immutableCopy(canonicalConnectionIds);
      this.canonicalMaterialConnections = immutableCopy(canonicalMaterialConnections);
      this.exportedMaterialConnections = immutableCopy(exportedMaterialConnections);
      this.exportedConnections = Collections.unmodifiableList(new ArrayList<ExportedConnection>(exportedConnections));
      this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
    }

    public Dexpi20ConformanceAssessment.Report getConformanceReport() {
      return conformanceReport;
    }

    /** @return topology source that drove the assessed export */
    public String getExportTopologySource() {
      return exportTopologySource;
    }

    /**
     * @return operating-value source used by an opt-in assessed export, or {@code null} for the compatibility path
     */
    public String getExportOperatingValueSource() {
      return exportOperatingValueSource;
    }

    public String getCanonicalFingerprint() {
      return canonicalFingerprint;
    }

    public List<String> getCanonicalConnectionIds() {
      return canonicalConnectionIds;
    }

    public List<String> getCanonicalMaterialConnections() {
      return canonicalMaterialConnections;
    }

    public List<String> getExportedMaterialConnections() {
      return exportedMaterialConnections;
    }

    public List<ExportedConnection> getExportedConnections() {
      return exportedConnections;
    }

    public List<Diagnostic> getDiagnostics() {
      return diagnostics;
    }

    public boolean isSupportedMaterialTopologyEquivalent() {
      return canonicalMaterialConnections.equals(exportedMaterialConnections) && !hasErrors(diagnostics);
    }

    public boolean isSchemaProfileAndSupportedTopologyValid() {
      return conformanceReport.isSchemaAndProfileConformant() && isSupportedMaterialTopologyEquivalent();
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", "neqsim_dexpi_2_0_process_topology_assessment.v1");
      result.put("exportTopologySource", exportTopologySource);
      if (exportOperatingValueSource != null) {
        result.put("exportOperatingValueSource", exportOperatingValueSource);
      }
      result.put("canonicalFingerprint", canonicalFingerprint);
      result.put("canonicalConnectionIds", new ArrayList<String>(canonicalConnectionIds));
      result.put("sourceProvenance", "SIMULATION_MODEL");
      result.put("engineeringState", "CALCULATED");
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("canonicalMaterialConnections", new ArrayList<String>(canonicalMaterialConnections));
      result.put("exportedMaterialConnections", new ArrayList<String>(exportedMaterialConnections));
      List<Map<String, Object>> connectionMaps = new ArrayList<Map<String, Object>>();
      for (ExportedConnection connection : exportedConnections) {
        connectionMaps.add(connection.toMap());
      }
      result.put("exportedConnections", connectionMaps);
      List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
      for (Diagnostic diagnostic : diagnostics) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("severity", diagnostic.getSeverity().name());
        item.put("code", diagnostic.getCode());
        item.put("message", diagnostic.getMessage());
        item.put("subject", diagnostic.getSubject());
        diagnosticMaps.add(item);
      }
      result.put("diagnostics", diagnosticMaps);
      result.put("supportedMaterialTopologyEquivalent", Boolean.valueOf(isSupportedMaterialTopologyEquivalent()));
      result.put("conformance", conformanceReport.toMap());
      return result;
    }

    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }

  static Report assess(ProcessSystem processSystem, Path file, Dexpi20ConformanceAssessment.Report conformanceReport,
      ProcessDiagramGraphAdapter.Result canonical, String exportTopologySource) throws IOException {
    return assess(processSystem, file, conformanceReport, canonical, exportTopologySource, null,
        Collections.<Diagnostic>emptyList());
  }

  static Report assess(ProcessSystem processSystem, Path file, Dexpi20ConformanceAssessment.Report conformanceReport,
      ProcessDiagramGraphAdapter.Result canonical, String exportTopologySource, String exportOperatingValueSource,
      List<Diagnostic> exportDiagnostics) throws IOException {
    if (processSystem == null || file == null || conformanceReport == null) {
      throw new IllegalArgumentException("processSystem, file, and conformanceReport must not be null");
    }
    if (canonical == null || exportTopologySource == null || exportTopologySource.trim().isEmpty()) {
      throw new IllegalArgumentException("canonical and exportTopologySource must not be null or blank");
    }
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    copyAdapterDiagnostics(canonical, diagnostics);
    if (exportDiagnostics != null) {
      diagnostics.addAll(exportDiagnostics);
    }
    EngineeringGraph graph = canonical.getGraph();
    List<String> connectionIds = canonicalConnectionIds(graph);
    List<String> expected = canonicalMaterialConnections(graph, diagnostics);
    List<ExportedConnection> exported = exportedConnections(file, expected, diagnostics);
    List<String> actual = materialConnections(exported);
    compareMaterialConnections(expected, actual, diagnostics);
    diagnostics.add(new Diagnostic(Severity.WARNING, "DEXPI_PROCESS_MULTI_AREA_UNSUPPORTED",
        "Native DEXPI 2.0 Process export currently accepts ProcessSystem only; ProcessModel area hierarchy is not exchanged",
        processSystem.getName()));
    diagnostics.add(new Diagnostic(Severity.WARNING, "DEXPI_PROCESS_DOCUMENT_SEMANTICS_UNSUPPORTED",
        "Controlled document, sheet, revision-block, drawing-status, and off-page-reference semantics are not exchanged",
        processSystem.getName()));
    diagnostics.add(new Diagnostic(Severity.WARNING, "DEXPI_PROCESS_GRAPHICS_UNSUPPORTED",
        "Native DEXPI 2.0 Process export contains semantic steps and streams but no governed drawing layout",
        processSystem.getName()));
    return new Report(conformanceReport, exportTopologySource, exportOperatingValueSource, canonical.getFingerprint(),
        connectionIds, expected, actual, exported, diagnostics);
  }

  private static void copyAdapterDiagnostics(ProcessDiagramGraphAdapter.Result canonical,
      List<Diagnostic> diagnostics) {
    for (ProcessDiagramGraphAdapter.Diagnostic source : canonical.getDiagnostics()) {
      Severity severity = source.getSeverity() == ProcessDiagramGraphAdapter.Severity.ERROR ? Severity.ERROR
          : source.getSeverity() == ProcessDiagramGraphAdapter.Severity.WARNING ? Severity.WARNING : Severity.INFO;
      diagnostics.add(new Diagnostic(severity, source.getCode(), source.getMessage(), source.getSubject()));
    }
  }

  private static List<String> canonicalMaterialConnections(EngineeringGraph graph, List<Diagnostic> diagnostics) {
    List<String> result = new ArrayList<String>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() == EngineeringNode.Kind.PIPE_SEGMENT) {
        result.add(connectionKey(node));
      } else if (node.getKind() == EngineeringNode.Kind.ENERGY_CONNECTION) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "DEXPI_PROCESS_ENERGY_CONNECTION_UNSUPPORTED",
            "Energy connections are present in the canonical graph but are not emitted by the current Process writer",
            node.getId()));
      } else if (node.getKind() == EngineeringNode.Kind.SIGNAL_CONNECTION) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "DEXPI_PROCESS_SIGNAL_CONNECTION_UNSUPPORTED",
            "Signal connections are present in the canonical graph but are not emitted by the current Process writer",
            node.getId()));
      }
    }
    Collections.sort(result);
    return result;
  }

  private static List<String> canonicalConnectionIds(EngineeringGraph graph) {
    List<String> result = new ArrayList<String>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() == EngineeringNode.Kind.PIPE_SEGMENT) {
        result.add(node.getId());
      }
    }
    Collections.sort(result);
    return result;
  }

  private static String connectionKey(EngineeringNode node) {
    return String.valueOf(node.getProperties().get("sourceEquipment")) + "->"
        + String.valueOf(node.getProperties().get("targetEquipment"));
  }

  private static List<ExportedConnection> exportedConnections(Path file, List<String> canonicalMaterialConnections,
      List<Diagnostic> diagnostics) throws IOException {
    Document document = parse(file);
    Map<String, StepReference> stepByPort = new LinkedHashMap<String, StepReference>();
    NodeList objects = document.getElementsByTagName("Object");
    for (int index = 0; index < objects.getLength(); index++) {
      Element object = (Element) objects.item(index);
      if (!"Process/Process.MaterialPort".equals(object.getAttribute("type"))) {
        continue;
      }
      Element step = ancestorProcessStep(object);
      if (step != null) {
        stepByPort.put(object.getAttribute("id"),
            new StepReference(directString(step, "Identifier"), step.getAttribute("type")));
      }
    }
    List<ExportedConnection> result = new ArrayList<ExportedConnection>();
    Set<String> usedPorts = new LinkedHashSet<String>();
    for (int index = 0; index < objects.getLength(); index++) {
      Element stream = (Element) objects.item(index);
      if (!"Process/Process.Stream".equals(stream.getAttribute("type"))) {
        continue;
      }
      String sourcePort = reference(stream, "Source");
      String targetPort = reference(stream, "Target");
      StepReference source = stepByPort.get(sourcePort);
      StepReference target = stepByPort.get(targetPort);
      if (source == null || target == null) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "DEXPI_PROCESS_UNRESOLVED_EXPORTED_PORT",
            "A DEXPI Process stream does not resolve to source and target material ports", stream.getAttribute("id")));
        continue;
      }
      if (!usedPorts.add(sourcePort) || !usedPorts.add(targetPort)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "DEXPI_PROCESS_EXPORTED_PORT_REUSED",
            "Each exported material connection must own distinct source and target ports", stream.getAttribute("id")));
      }
      String materialConnection = source.name + "->" + target.name;
      boolean boundary = "Process/Process.Sink".equals(target.type)
          && !canonicalMaterialConnections.contains(materialConnection);
      if (boundary) {
        diagnostics.add(new Diagnostic(Severity.INFO, "DEXPI_PROCESS_BOUNDARY_SINK_SYNTHESIZED",
            "An unconsumed material outlet was projected to a DEXPI Process sink", stream.getAttribute("id")));
      }
      result.add(new ExportedConnection(stream.getAttribute("id"), directString(stream, "Label"), source.name,
          target.name, sourcePort, targetPort, boundary));
    }
    Collections.sort(result, new Comparator<ExportedConnection>() {
      @Override
      public int compare(ExportedConnection first, ExportedConnection second) {
        int byConnection = first.materialKey().compareTo(second.materialKey());
        return byConnection == 0 ? first.getStreamId().compareTo(second.getStreamId()) : byConnection;
      }
    });
    return result;
  }

  private static List<String> materialConnections(List<ExportedConnection> connections) {
    List<String> result = new ArrayList<String>();
    for (ExportedConnection connection : connections) {
      if (!connection.isBoundaryTarget()) {
        result.add(connection.materialKey());
      }
    }
    Collections.sort(result);
    return result;
  }

  private static void compareMaterialConnections(List<String> expected, List<String> actual,
      List<Diagnostic> diagnostics) {
    Map<String, Integer> expectedCounts = counts(expected);
    Map<String, Integer> actualCounts = counts(actual);
    Set<String> keys = new LinkedHashSet<String>();
    keys.addAll(expectedCounts.keySet());
    keys.addAll(actualCounts.keySet());
    for (String key : keys) {
      int required = value(expectedCounts, key);
      int exported = value(actualCounts, key);
      if (required > exported) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "DEXPI_PROCESS_MATERIAL_CONNECTION_MISSING",
            "Canonical material connection multiplicity " + required + " exceeds exported multiplicity " + exported,
            key));
      } else if (exported > required) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "DEXPI_PROCESS_MATERIAL_CONNECTION_UNEXPECTED",
            "Exported material connection multiplicity " + exported + " exceeds canonical multiplicity " + required,
            key));
      }
    }
  }

  private static Map<String, Integer> counts(List<String> values) {
    Map<String, Integer> result = new LinkedHashMap<String, Integer>();
    for (String value : values) {
      result.put(value, Integer.valueOf(value(result, value) + 1));
    }
    return result;
  }

  private static int value(Map<String, Integer> values, String key) {
    Integer value = values.get(key);
    return value == null ? 0 : value.intValue();
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
      throw new IOException("Could not configure DEXPI Process topology parser", ex);
    } catch (SAXException ex) {
      throw new IOException("Could not parse DEXPI Process topology", ex);
    }
  }

  private static Element ancestorProcessStep(Element port) {
    for (Node node = port.getParentNode(); node != null; node = node.getParentNode()) {
      if (node instanceof Element && "Object".equals(((Element) node).getTagName())) {
        Element object = (Element) node;
        String type = object.getAttribute("type");
        if (type.startsWith("Process/Process.") && !"Process/Process.MaterialPort".equals(type)) {
          return object;
        }
      }
    }
    return null;
  }

  private static String directString(Element parent, String property) {
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "Data".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        NodeList strings = ((Element) child).getElementsByTagName("String");
        return strings.getLength() == 0 ? "" : strings.item(0).getTextContent();
      }
    }
    return "";
  }

  private static String reference(Element parent, String property) {
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element && "References".equals(((Element) child).getTagName())
          && property.equals(((Element) child).getAttribute("property"))) {
        return ((Element) child).getAttribute("objects").replaceFirst("^#", "");
      }
    }
    return "";
  }

  private static boolean hasErrors(List<Diagnostic> diagnostics) {
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic.getSeverity() == Severity.ERROR) {
        return true;
      }
    }
    return false;
  }

  private static List<String> immutableCopy(List<String> values) {
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  private static final class StepReference {
    private final String name;
    private final String type;

    StepReference(String name, String type) {
      this.name = name;
      this.type = type;
    }
  }
}
