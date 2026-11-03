package neqsim.process.processmodel.diagram;

import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidPhysicalProjection;
import neqsim.process.engineering.pid.PidPhysicalProjection.Attachment;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Command;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Diagnostic;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Page;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Point;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Severity;
import neqsim.process.processmodel.diagram.ProfessionalPidSymbolLibrary.Family;

/**
 * Source-connected professional P&amp;ID scenes, ISO-basis content checks and deterministic pagination.
 *
 * <p>
 * This renderer consumes the same physical projection as the native DEXPI materializer. Material, sensing and control
 * connections are distinct. Named signal continuations avoid confusing crossed wires with physical junctions. Missing
 * controlled design information is retained as HOLD; it is never supplied from operating values. Drawing-content checks
 * do not constitute independent ISO qualification, safety approval or fitness for construction.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class ProfessionalPidDiagramRenderer {
  /** Paper width, ISO A1 landscape, in millimetres. */
  private static final double WIDTH = 841.0;
  /** Paper height in millimetres. */
  private static final double HEIGHT = 594.0;
  /** Actual source project. */
  private final EngineeringProject project;
  /** Source proposal model. */
  private final PidDesignModel model;
  /** Resolved shared engineering attachment model. */
  private final PidPhysicalProjection projection;
  /** Source document identities, retained independently of representation pagination. */
  private final EngineeringDiagramDocumentSet documents;
  /** Native serializer. */
  private final NativeEngineeringDiagramRenderer nativeRenderer;
  /** Actual process. */
  private final ProcessSystem process;
  /** Caller-controlled issue date/time; never a fabricated approval date. */
  private final String issuedAt;
  /** Process stream producers resolved by object identity. */
  private final Map<StreamInterface, ProcessEquipmentInterface> producers = new IdentityHashMap<StreamInterface, ProcessEquipmentInterface>();
  /** Source equipment by tag. */
  private final Map<String, ProcessEquipmentInterface> equipment = new LinkedHashMap<String, ProcessEquipmentInterface>();
  /** Proposal by tag. */
  private final Map<String, PidElement> proposals = new LinkedHashMap<String, PidElement>();
  /** Native pages. */
  private final List<Page> pages = new ArrayList<Page>();
  /** Exact layout specifications. */
  private final Map<String, Map<String, Object>> layouts = new LinkedHashMap<String, Map<String, Object>>();
  /** Source and proposal coverage. */
  private final Set<String> represented = new LinkedHashSet<String>();
  /** Actual equipment represented independently of proposal-tag coverage. */
  private final Set<String> representedEquipment = new LinkedHashSet<String>();
  /** Actual geometry findings. */
  private final List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
  /** ISO-basis content findings, separate from geometry. */
  private final List<Map<String, Object>> holds = new ArrayList<Map<String, Object>>();
  /** Sheet index. */
  private final List<Map<String, Object>> sheets = new ArrayList<Map<String, Object>>();
  /** Stable directed signal identities. */
  private final Map<String, String> signalIds = new LinkedHashMap<String, String>();
  /** Signal sources represented. */
  private final Set<String> signalSources = new LinkedHashSet<String>();
  /** Signal destinations represented. */
  private final Set<String> signalTargets = new LinkedHashSet<String>();
  /** Actual lines whose controlled design data have been checked. */
  private final Set<String> assessedLines = new LinkedHashSet<String>();
  /** Planned controlled sheet number for each main equipment representation. */
  private final Map<String, String> mainSheetNumbers = new LinkedHashMap<String, String>();
  /** Controlled auxiliary sheet per explicit route. */
  private final Map<String, String> auxiliarySheetNumbers = new LinkedHashMap<String, String>();

  /**
   * Creates the source-backed professional renderer.
   *
   * @param project controlled source project
   * @param model P&amp;ID engineering model
   * @param bindings explicit executable device bindings
   * @param documents source document identities
   * @param conventions controlled symbol-review records
   */
  public ProfessionalPidDiagramRenderer(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, EngineeringDiagramDocumentSet documents,
      EngineeringDiagramConventionRegister conventions) {
    this(project, model, bindings, documents, conventions, "HOLD");
  }

  /**
   * Creates the source-backed renderer with a caller-controlled issue date.
   *
   * @param project controlled project
   * @param model P&amp;ID design model
   * @param bindings executable bindings
   * @param documents source document identities
   * @param conventions symbol-review evidence
   * @param issuedAt controlled issue timestamp or HOLD
   */
  public ProfessionalPidDiagramRenderer(EngineeringProject project, PidDesignModel model,
      PidSimulationBindings bindings, EngineeringDiagramDocumentSet documents,
      EngineeringDiagramConventionRegister conventions, String issuedAt) {
    if (project == null || model == null || bindings == null || documents == null || conventions == null) {
      throw new IllegalArgumentException("project, model, bindings, documents and conventions are required");
    }
    bindings.assertCurrent(project, model);
    this.project = project;
    this.model = model;
    this.documents = documents;
    this.process = project.getEngineeringProcessSystem();
    this.issuedAt = issuedAt == null || issuedAt.trim().isEmpty() ? "HOLD" : issuedAt;
    this.projection = PidPhysicalProjection.resolve(process, model, bindings);
    this.nativeRenderer = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.SheetFormat.A1_LANDSCAPE, conventions,
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL, null, true);
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      equipment.put(unit.getName(), unit);
      if (unit instanceof StreamInterface) {
        producers.put((StreamInterface) unit, unit);
      } else {
        for (StreamInterface stream : unit.getOutletStreams()) {
          producers.put(stream, unit);
        }
      }
    }
    for (PidElement element : model.getElements()) {
      proposals.put(element.getTag(), element);
    }
    int index = 1;
    for (Map<String, String> signal : projection.getSignals()) {
      signalIds.put(signalKey(signal), String.format(Locale.ROOT, "SG-%03d", Integer.valueOf(index++)));
    }
  }

  /**
   * Renders every supported unit and proposal, paginating rather than clipping long lists.
   *
   * @return native vector result
   */
  public NativeEngineeringDiagramRenderer.Result render() {
    if (!pages.isEmpty()) {
      throw new IllegalStateException("Create a renderer for each controlled generation");
    }
    List<ProcessEquipmentInterface> primary = new ArrayList<ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && !(unit instanceof ThrottlingValve)) {
        primary.add(unit);
      }
    }
    if (primary.isEmpty()) {
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        if (!(unit instanceof StreamInterface)) {
          primary.add(unit);
        }
      }
    }
    int nextSheet = 1;
    for (ProcessEquipmentInterface unit : primary) {
      mainSheetNumbers.put(unit.getName(), String.format(Locale.ROOT, "%03d", Integer.valueOf(nextSheet++)));
      Set<String> auxiliaryGroups = new LinkedHashSet<String>();
      for (PidElement element : model.getElementsForEquipment(unit.getName())) {
        Attachment a = projection.getAttachments().get(element.getTag());
        if (a.getKind() == PidPhysicalProjection.Kind.AUXILIARY_ROUTE
            || a.getKind() == PidPhysicalProjection.Kind.SENSING && "DESIGNED_NOZZLE".equals(a.getDirection())) {
          String route = textValue(a.toMap().get("physicalRouteId"));
          auxiliaryGroups.add(route.isEmpty() ? element.getTag() : route);
        }
      }
      int auxiliaryIndex = 0;
      for (String group : auxiliaryGroups) {
        auxiliarySheetNumbers.put(unit.getName() + ":" + group,
            String.format(Locale.ROOT, "%03d", Integer.valueOf(nextSheet + auxiliaryIndex++ / 4)));
      }
      nextSheet += (auxiliaryGroups.size() + 3) / 4;
    }
    for (ProcessEquipmentInterface unit : primary) {
      if (ProfessionalPidSymbolLibrary.family(unit) == Family.UNMAPPED) {
        hold("SYMBOL_FAMILY_UNMAPPED", unit.getName(), "ISO 10628-2:2012",
            "Provide an explicit supported equipment family; generic substitution is prohibited");
        diagnostics.add(NativeEngineeringDiagramRenderer.sceneDiagnostic(Severity.ERROR, "PID_SYMBOL_UNMAPPED",
            "No supported equipment family", unit.getName()));
        continue;
      }
      renderUnit(unit);
      renderAuxiliary(unit);
    }
    List<PidElement> remaining = new ArrayList<PidElement>();
    for (PidElement element : model.getElements()) {
      if (!represented.contains(element.getTag())) {
        remaining.add(element);
      }
    }
    if (!remaining.isEmpty()) {
      renderHolds(remaining);
    }
    for (Map<String, String> signal : projection.getSignals()) {
      String id = signalIds.get(signalKey(signal));
      if (!signalSources.contains(id) || !signalTargets.contains(id)) {
        hold("SIGNAL_CONTINUATION_INCOMPLETE", id, "IEC 62424:2016",
            "Both controlled signal endpoints must be represented");
      }
    }
    for (Map<String, Object> layout : layouts.values()) {
      assessLayout(layout);
    }
    verifySourceCoverage();
    hold("SYMBOL_STANDARD_REVIEW", project.getProjectId(), ProfessionalPidSymbolLibrary.EQUIPMENT_STANDARD,
        "Authored vector catalogue requires independent comparison with the project-controlled standard edition");
    hold("ACCOUNTABLE_DRAWING_REVIEW", project.getProjectId(), "ISO 10628-1:2014",
        "Process, piping, control and technical-safety review and document sign-off remain required");
    return nativeRenderer.renderScenes(pages, diagnostics,
        Arrays.asList("DRAWING_SHEET_BOUNDS", "SYMBOL_AND_LABEL_BOUNDS", "SOURCE_AND_PROPOSAL_COVERAGE",
            "MATERIAL_AND_SIGNAL_ENDPOINTS", "TEXT_TEXT_AND_TEXT_ROUTE_INTERSECTIONS",
            "ORTHOGONAL_ROUTE_INTERSECTIONS"));
  }

  /** @return JSON geometry specifications, one per actual rendered sheet */
  public Map<String, Map<String, Object>> getLayoutSpecifications() {
    return Collections.unmodifiableMap(layouts);
  }

  /**
   * Renders a source-only A1 PFD overview for a small, forward-flow train.
   *
   * <p>
   * Up to four primary units are supported. Actual valves, streams and normal operating data are retained; proposed
   * instrumentation is excluded. Branched or reverse-flow arrangements are checked using the same geometry diagnostics
   * as the P&amp;ID, rather than being declared complete merely because a PDF exists.
   * </p>
   *
   * @return native PFD SVG/PDF and geometry diagnostics
   * @throws IllegalStateException if this instance was already used
   * @throws IllegalArgumentException if the source exceeds the supported overview size
   */
  public NativeEngineeringDiagramRenderer.Result renderProcessFlowOverview() {
    if (!pages.isEmpty()) {
      throw new IllegalStateException("Create a renderer for each controlled generation");
    }
    List<ProcessEquipmentInterface> primary = new ArrayList<ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && !(unit instanceof ThrottlingValve)) {
        primary.add(unit);
      }
    }
    if (primary.isEmpty() || primary.size() > 4) {
      throw new IllegalArgumentException("Native A1 PFD overview supports one to four primary units");
    }
    Scene scene = new Scene(project.getProjectId() + "-PFD-001", "Process flow diagram", "001", "pfd");
    Map<String, Node> actualNodes = new LinkedHashMap<String, Node>();
    for (int index = 0; index < primary.size(); index++) {
      ProcessEquipmentInterface unit = primary.get(index);
      Family family = ProfessionalPidSymbolLibrary.family(unit);
      Node node = scene.node(unit.getName(), family, 145 + index * 175, 250,
          family == Family.HORIZONTAL_VESSEL ? 70 : 42,
          family == Family.VERTICAL_VESSEL ? 80 : family == Family.HORIZONTAL_VESSEL ? 50 : 42, unit.getName());
      actualNodes.put(unit.getName(), node);
      scene.centered(node.x, 195, unit.getClass().getSimpleName(), 3, null);
      if (family == Family.UNMAPPED) {
        geometryError("PFD_SYMBOL_UNMAPPED", "Unsupported source equipment family", unit.getName());
      }
      for (ProcessEquipmentInterface valve : downstreamValves(unit)) {
        boolean liquid = unit instanceof Separator
            && unit.getOutletStreams().indexOf(valve.getInletStreams().get(0)) > 0;
        Node valveNode = scene.node(valve.getName(), ProfessionalPidSymbolLibrary.family(valve),
            liquid ? node.x + 55 : node.x + 85, liquid ? 400 : 250, 16, 10, valve.getName());
        actualNodes.put(valve.getName(), valveNode);
      }
    }
    Set<StreamInterface> streams = Collections.newSetFromMap(new IdentityHashMap<StreamInterface, Boolean>());
    List<StreamInterface> streamTable = new ArrayList<StreamInterface>();
    for (ProcessEquipmentInterface unit : primary) {
      for (StreamInterface stream : unit.getInletStreams()) {
        if (streams.add(stream)) {
          streamTable.add(stream);
        }
      }
      for (StreamInterface stream : unit.getOutletStreams()) {
        if (streams.add(stream)) {
          streamTable.add(stream);
        }
      }
      for (ProcessEquipmentInterface valve : downstreamValves(unit)) {
        for (StreamInterface stream : valve.getOutletStreams()) {
          if (streams.add(stream)) {
            streamTable.add(stream);
          }
        }
      }
    }
    for (StreamInterface stream : streamTable) {
      ProcessEquipmentInterface producer = producers.get(stream);
      ProcessEquipmentInterface consumer = null;
      for (ProcessEquipmentInterface candidate : process.getUnitOperations()) {
        if (!(candidate instanceof StreamInterface) && candidate.getInletStreams().contains(stream)) {
          if (consumer != null) {
            geometryError("PFD_AMBIGUOUS_STREAM", "Shared stream requires an explicit source splitter",
                stream.getName());
          }
          consumer = candidate;
        }
      }
      Node from = producer == null ? null : actualNodes.get(producer.getName());
      Node to = consumer == null ? null : actualNodes.get(consumer.getName());
      if (producer != null && !(producer instanceof StreamInterface) && from == null
          || consumer != null && to == null) {
        geometryError("PFD_UNREPRESENTED_EQUIPMENT", "Known endpoint exceeds supported overview topology",
            stream.getName());
        continue;
      }
      if (producer instanceof StreamInterface && !producer.getInletStreams().isEmpty()) {
        geometryError("PFD_INTERNAL_STREAM_WRAPPER", "Resolve internal stream aliases before overview rendering",
            stream.getName());
        continue;
      }
      if (from == null && to != null) {
        from = scene.node("pfd-in:" + stream.getName(), Family.TERMINAL, to.x - 85, to.y, 50, 15, "FEED");
      } else if (to == null && from != null) {
        boolean liquidOutlet = producer instanceof Separator && producer.getOutletStreams().indexOf(stream) > 0;
        to = scene.node("pfd-out:" + stream.getName(), Family.TERMINAL, Math.min(805, from.x + 85),
            liquidOutlet ? 400 : from.y, 50, 15, "PRODUCT");
      }
      if (from == null || to == null) {
        geometryError("PFD_STREAM_UNRESOLVED", "Source stream has no represented physical endpoints", stream.getName());
        continue;
      }
      boolean downward = to.y > from.y;
      boolean vertical = downward && Math.abs(from.x - to.x) < .01;
      scene.route(from, downward ? "b" : "r", to, vertical ? "t" : "l", stream.getName(), false,
          downward && !vertical ? points(from.bottom(), new Point(from.x, to.y), to.left()) : null);
      scene.centered(downward ? from.x + 38 : (from.right().x + to.left().x) / 2,
          downward ? (from.bottom().y + to.top().y) / 2 : from.y - (to.left().x - from.right().x < 40 ? 40 : 14),
          stream.getName(), 3, null);
    }
    scene.text(25, 92,
        "NORMAL OPERATING DATA - source simulation; design conditions and safeguards require separate review", 3.5,
        null);
    int energyRow = 0;
    for (ProcessEquipmentInterface unit : primary) {
      if (unit instanceof neqsim.process.equipment.compressor.Compressor) {
        scene.text(25, 107 + energyRow++ * 10, unit.getName() + " shaft power: " + String.format(Locale.ROOT, "%.2f kW",
            ((neqsim.process.equipment.compressor.Compressor) unit).getPower("kW")), 3, null);
      } else if (unit instanceof neqsim.process.equipment.heatexchanger.Heater) {
        scene.text(25, 107 + energyRow++ * 10, unit.getName() + " heat duty (to fluid): " + String.format(Locale.ROOT,
            "%.2f kW", ((neqsim.process.equipment.heatexchanger.Heater) unit).getDuty() / 1000.0), 3, null);
      }
    }
    scene.text(25, 450, "STREAM DATA  |  pressure bara  |  temperature C  |  mass flow kg/h", 3.5, null);
    for (int index = 0; index < streamTable.size(); index++) {
      StreamInterface stream = streamTable.get(index);
      if (index >= 18) {
        geometryError("PFD_STREAM_TABLE_OVERFLOW", "Stream data require an additional controlled sheet",
            stream.getName());
        break;
      }
      scene.text(
          25 + (index / 6) * 265, 465 + (index % 6) * 10, String.format(Locale.ROOT, "%s  |  %.2f  |  %.2f  |  %.2f",
              stream.getName(), stream.getPressure("bara"), stream.getTemperature("C"), stream.getFlowRate("kg/hr")),
          3, null);
    }
    scene.finish();
    assessLayout(scene.spec);
    verifySourceCoverage();
    return nativeRenderer.renderScenes(pages, diagnostics, Arrays.asList("DRAWING_SHEET_BOUNDS", "MATERIAL_ENDPOINTS",
        "TEXT_TEXT_AND_TEXT_ROUTE_INTERSECTIONS", "ORTHOGONAL_ROUTE_INTERSECTIONS"));
  }

  /** Fails closed when a real physical source item has no drawing representation. */
  private void verifySourceCoverage() {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && !representedEquipment.contains(unit.getName())) {
        geometryError("DRAWING_SOURCE_EQUIPMENT_UNREPRESENTED",
            "Actual source equipment requires a supported representation", unit.getName());
      }
    }
  }

  /** @return shared source-backed physical and functional attachment evidence */
  public PidPhysicalProjection getPhysicalProjection() {
    return projection;
  }

  /**
   * Returns explicit ISO-basis content coverage without promoting HOLDs into compliance.
   *
   * @return serialized content/standards assessment
   */
  public String getContentReportJson() {
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("schemaVersion", "neqsim_professional_pid_drawing_content.v1");
    report.put("projectId", project.getProjectId());
    report.put("revision", project.getRevision());
    report.put("issuedAt", issuedAt);
    report.put("sourceGraphFingerprint", documents.getSourceGraphFingerprint());
    report.put("standardsBasis", Arrays.asList("ISO 10628-1:2014", "ISO 10628-2:2012", "ISO 5457", "ISO 7200",
        "IEC 62424:2016", "ANSI/ISA-5.1:2024"));
    report.put("paper", "A1_LANDSCAPE_841_X_594_MM");
    report.put("processLineWidthMm", Double.valueOf(.5));
    report.put("instrumentLineWidthMm", Double.valueOf(.25));
    report.put("minimumTextHeightMm", Double.valueOf(2.5));
    report.put("proposalCount", Integer.valueOf(model.getElements().size()));
    report.put("representedProposalTags", new ArrayList<String>(represented));
    report.put("drawingIndex", sheets);
    report.put("signalContinuations", projection.getSignals());
    report.put("holds", holds);
    report.put("isoConformanceStatus", "REQUIRES_CONTROLLED_STANDARD_AND_DISCIPLINE_REVIEW");
    report.put("fitnessForConstruction", Boolean.FALSE);
    report.put("engineeringApproval", "NOT_ASSIGNED");
    return new GsonBuilder().setPrettyPrinting().create().toJson(report);
  }

  /**
   * Builds an equipment sheet using real source ports and declared inline devices.
   *
   * @param unit primary physical unit
   */
  private void renderUnit(ProcessEquipmentInterface unit) {
    Scene scene = scene(unit.getName(), "Process and instrumentation");
    Family family = ProfessionalPidSymbolLibrary.family(unit);
    double width = family == Family.HORIZONTAL_VESSEL ? 100 : 46;
    double height = family == Family.VERTICAL_VESSEL ? 90 : family == Family.HORIZONTAL_VESSEL ? 60 : 46;
    Node body = scene.node(unit.getName(), family, 320, 280, width, height, unit.getName());
    scene.text(225, 280 + height / 2 + 20, unit.getClass().getSimpleName(), 3.5, null);
    List<ProcessEquipmentInterface> valves = downstreamValves(unit);
    Map<String, Node> valveNodes = new LinkedHashMap<String, Node>();
    int inlet = 0;
    for (StreamInterface stream : unit.getInletStreams()) {
      double y = 280 + inlet * 45;
      Node source = scene.terminal("in:" + stream.getName(), 60, y, upstreamLabel(stream), false);
      List<PidElement> inline = inline(unit.getName(), stream.getName(), "INLET");
      Node last = source;
      int index = 0;
      for (PidElement proposal : inline) {
        Node node = proposalValve(scene, proposal, 130 + index++ * 65, y);
        valveNodes.put(proposal.getTag(), node);
        scene.route(last, "r", node, "l", stream.getName(), false, null);
        last = node;
      }
      scene.route(last, "r", body, "l", stream.getName(), false,
          inlet == 0 ? null : points(last.right(), new Point(260, y), new Point(260, 280), body.left()));
      scene.lineLabel(stream.getName(), 190, y + 12, unit.getName());
      inlet++;
    }
    int outlet = 0;
    for (StreamInterface stream : unit.getOutletStreams()) {
      boolean liquid = unit instanceof Separator && outlet > 0;
      double y = liquid ? 455 + (outlet - 1) * 45 : 140;
      if (y > 510) {
        hold("ADDITIONAL_OUTLET_SHEET_REQUIRED", stream.getName(), "ISO 10628-1:2014",
            "Additional product outlets exceed the supported per-unit sheet template");
        continue;
      }
      Node last = body;
      String startSide = liquid ? "b" : unit instanceof Separator ? "t" : "r";
      List<PidElement> inline = inline(unit.getName(), stream.getName(), "OUTLET");
      Set<String> inlineTags = new LinkedHashSet<String>();
      for (PidElement element : inline) {
        inlineTags.add(element.getTag());
      }
      List<String> physicalValves = new ArrayList<String>();
      for (ProcessEquipmentInterface valve : valves) {
        if (valve.getInletStreams().contains(stream)) {
          physicalValves.add(valve.getName());
        }
      }
      int index = 0;
      String physicalStream = stream.getName();
      for (PidElement element : inline) {
        Node node = proposalValve(scene, element, 500 + index++ * 100, y);
        valveNodes.put(element.getTag(), node);
        scene.route(last, startSide, node, "l", stream.getName(), false,
            last == body ? sourceRoute(body, startSide, node.left(), y) : null);
        last = node;
        startSide = "r";
      }
      for (String actual : physicalValves) {
        if (inlineTags.contains(actual)) {
          continue;
        }
        Node node = proposals.containsKey(actual) ? proposalValve(scene, proposals.get(actual), 500 + index++ * 100, y)
            : scene.node(actual, ProfessionalPidSymbolLibrary.family(equipment.get(actual)), 500 + index++ * 100, y, 16,
                10, actual);
        valveNodes.put(actual, node);
        scene.route(last, startSide, node, "l", physicalStream, false,
            last == body ? sourceRoute(body, startSide, node.left(), y) : null);
        last = node;
        startSide = "r";
        ProcessEquipmentInterface actualValve = equipment.get(actual);
        if (actualValve != null && !actualValve.getOutletStreams().isEmpty()) {
          physicalStream = actualValve.getOutletStreams().get(0).getName();
        }
      }
      Node target = scene.terminal("out:" + stream.getName(), 790, y, downstreamLabel(stream), false);
      scene.route(last, startSide, target, "l", physicalStream, false,
          last == body ? sourceRoute(body, startSide, target.left(), y) : null);
      scene.lineLabel(physicalStream, 640, y + 30, unit.getName());
      if (!physicalStream.equals(stream.getName())) {
        scene.lineLabel(stream.getName(), 420, liquid ? y + 30 : y - 24, unit.getName());
      }
      outlet++;
    }
    List<PidElement> sensors = new ArrayList<PidElement>();
    List<PidElement> vesselSensors = new ArrayList<PidElement>();
    List<PidElement> suctionSensors = new ArrayList<PidElement>();
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment attachment = projection.getAttachments().get(element.getTag());
      if (attachment.getKind() == PidPhysicalProjection.Kind.SENSING
          && !"DESIGNED_NOZZLE".equals(attachment.getDirection())) {
        if (unit instanceof Separator && "OUTLET".equals(attachment.getDirection())
            && attachment.getStreamIndex() > 0) {
          hold("LIQUID_OUTLET_SENSING_LAYOUT", element.getTag(), "ISO 10628-1:2014 / IEC 62424:2016",
              "Provide a controlled liquid-outlet sensing arrangement; the gas-outlet template must not be used");
          continue;
        }
        if ("VESSEL".equals(attachment.getDirection())) {
          vesselSensors.add(element);
        } else if ("INLET".equals(attachment.getDirection())) {
          suctionSensors.add(element);
        } else {
          sensors.add(element);
        }
      }
    }
    sensors.addAll(vesselSensors);
    Set<String> placed = new LinkedHashSet<String>();
    int row = 0;
    int vesselIndex = 0;
    for (PidElement sensor : sensors) {
      Attachment attachment = projection.getAttachments().get(sensor.getTag());
      double y = 200 + row * 36;
      if (y > 425) {
        continue;
      }
      Node measurement = scene.instrument(sensor, 520, y);
      boolean vessel = "VESSEL".equals(attachment.getDirection());
      if (vessel) {
        double py = body.y - body.height / 2 + body.height * (vesselIndex + 1) / (vesselSensors.size() + 1);
        double channel = 415 - vesselIndex * 11;
        Point tap = ProfessionalPidSymbolLibrary.vesselSensingTap(body.x, body.y, body.width, body.height, py);
        scene.sensing(body, tap, measurement, "l",
            points(tap, new Point(channel, py), new Point(channel, y), measurement.left()), sensor.getTag());
        vesselIndex++;
      } else {
        double tap = 425 - row * 9;
        Point start = new Point(tap, 140);
        scene.sensing(null, start, measurement, "l", points(start, new Point(tap, y), measurement.left()),
            sensor.getTag());
      }
      placed.add(sensor.getTag());
      placeChildFunction(scene, sensor, measurement, 640, y, placed);
      row++;
    }
    int suctionRow = 0;
    for (PidElement sensor : suctionSensors) {
      double y = 155 + suctionRow * 35;
      Node measurement = scene.instrument(sensor, 175, y);
      double tap = 145 + suctionRow * 8;
      Point start = new Point(tap, 280);
      scene.sensing(null, start, measurement, "b",
          points(start, new Point(tap, y + 24), new Point(175, y + 24), measurement.bottom()), sensor.getTag());
      placed.add(sensor.getTag());
      placeChildFunction(scene, sensor, measurement, 250, y, placed);
      suctionRow++;
    }
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment attachment = projection.getAttachments().get(element.getTag());
      if (attachment.getKind() == PidPhysicalProjection.Kind.FUNCTION && !placed.contains(element.getTag())) {
        double y = 200 + row++ * 36;
        if (y <= 425) {
          scene.instrument(element, 640, y);
          placed.add(element.getTag());
        }
      }
    }
    for (Map.Entry<String, Node> entry : new LinkedHashMap<String, Node>(scene.proposalNodes).entrySet()) {
      signalEnds(scene, entry.getKey(), entry.getValue());
    }
    drawNozzles(scene, unit, body, valveNodes);
    drawAuxiliaryContinuations(scene, unit);
    if (unit instanceof neqsim.process.equipment.compressor.Compressor) {
      scene.text(25, 480,
          "Normal shaft power: "
              + String.format(Locale.ROOT, "%.2f kW",
                  ((neqsim.process.equipment.compressor.Compressor) unit).getPower("kW"))
              + " | driver design data: HOLD",
          3, null);
      hold("DRIVER_CHARACTERISTIC_DATA", unit.getName(), "ISO 10628-1:2014",
          "Provide controlled driver type, rated power and governing characteristics");
    }
    scene.text(25, 490,
        "P&ID design status: proposed devices remain REVIEW REQUIRED; FA:HOLD means failure action is not assigned.", 3,
        null);
    scene.text(25, 499,
        "Auxiliary lines, signal IDs and source tags are coordinated with the physical projection and the detailed registers.",
        3, null);
    scene.finish();
  }

  /**
   * Marks actual source/return tie-ins on the main sheet with reciprocal auxiliary sheet numbers.
   *
   * @param scene main process sheet
   * @param unit actual source unit
   */
  private void drawAuxiliaryContinuations(Scene scene, ProcessEquipmentInterface unit) {
    int sourceIndex = 0;
    int targetIndex = 0;
    Set<String> seen = new LinkedHashSet<String>();
    Set<String> designSheets = new LinkedHashSet<String>();
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment a = projection.getAttachments().get(element.getTag());
      if (a.getKind() != PidPhysicalProjection.Kind.AUXILIARY_ROUTE
          && !(a.getKind() == PidPhysicalProjection.Kind.SENSING && "DESIGNED_NOZZLE".equals(a.getDirection()))) {
        continue;
      }
      String route = textValue(a.toMap().get("physicalRouteId"));
      String key = route.isEmpty() ? element.getTag() : route;
      if (!seen.add(key)) {
        continue;
      }
      String sheet = auxiliarySheetNumbers.get(unit.getName() + ":" + key);
      if (!textValue(a.toMap().get("physicalSourceNozzleTag")).isEmpty()) {
        designSheets.add(sheet);
      }
      if (!unit.getOutletStreams().isEmpty() && a.getSourceEquipmentTag().equals(unit.getName())
          && a.getSourceStreamTag().equals(unit.getOutletStreams().get(0).getName())) {
        if (sourceIndex >= 2) {
          hold("ADDITIONAL_AUXILIARY_TIE_IN_LAYOUT", element.getTag(), "ISO 10628-1:2014",
              "Additional same-line auxiliary take-offs require a controlled routing detail");
        } else {
          double x = 375 + sourceIndex++ * 70;
          Point tap = new Point(x, 140);
          Node terminal = scene.verticalTerminal("aux-main-source:" + element.getTag(), x, 80,
              element.getTag() + " / PID-" + sheet, true);
          scene.materialContinuation(tap, terminal, true, "aux-source:" + element.getTag());
        }
      }
      if (!unit.getInletStreams().isEmpty() && a.getTargetEquipmentTag().equals(unit.getName())
          && a.getTargetStreamTag().equals(unit.getInletStreams().get(0).getName())) {
        int inletDevices = inline(unit.getName(), unit.getInletStreams().get(0).getName(), "INLET").size();
        double x = Math.max(250, 130 + Math.max(0, inletDevices - 1) * 65 + 18) + targetIndex++ * 25;
        double inletBoundary = 320 - (ProfessionalPidSymbolLibrary.family(unit) == Family.HORIZONTAL_VESSEL ? 50 : 23);
        if (x >= inletBoundary - 8) {
          hold("ADDITIONAL_AUXILIARY_RETURN_LAYOUT", element.getTag(), "ISO 10628-1:2014",
              "Additional returns require a controlled detail downstream of all inlet devices");
          continue;
        }
        Point tap = new Point(x, 280);
        Node terminal = scene.verticalTerminal("aux-main-target:" + element.getTag(), x, 370,
            element.getTag() + " / PID-" + sheet, true);
        scene.materialContinuation(tap, terminal, false, "aux-return:" + element.getTag());
      }
    }
    if (!designSheets.isEmpty()) {
      scene.text(25, 515,
          "Owned design-nozzle / utility detail: auxiliary PID sheets " + join(new ArrayList<String>(designSheets)), 3,
          null);
    }
  }

  /**
   * Renders auxiliary paths on spacious dedicated sheets, preserving named tie-ins.
   *
   * @param unit source unit
   */
  private void renderAuxiliary(ProcessEquipmentInterface unit) {
    Map<String, List<PidElement>> groups = new LinkedHashMap<String, List<PidElement>>();
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment a = projection.getAttachments().get(element.getTag());
      if (a.getKind() == PidPhysicalProjection.Kind.AUXILIARY_ROUTE
          || a.getKind() == PidPhysicalProjection.Kind.SENSING && "DESIGNED_NOZZLE".equals(a.getDirection())) {
        String route = textValue(a.toMap().get("physicalRouteId"));
        String key = route.isEmpty() ? element.getTag() : route;
        if (!groups.containsKey(key)) {
          groups.put(key, new ArrayList<PidElement>());
        }
        groups.get(key).add(element);
      }
    }
    List<List<PidElement>> auxiliary = new ArrayList<List<PidElement>>(groups.values());
    for (List<PidElement> group : auxiliary) {
      Collections.sort(group, new Comparator<PidElement>() {
        @Override
        public int compare(PidElement first, PidElement second) {
          return Integer.compare(routeOrder(projection.getAttachments().get(first.getTag())),
              routeOrder(projection.getAttachments().get(second.getTag())));
        }
      });
    }
    for (int offset = 0; offset < auxiliary.size(); offset += 4) {
      Scene scene = scene(unit.getName(), "Auxiliary piping and safeguarding interfaces");
      for (int index = offset; index < Math.min(offset + 4, auxiliary.size()); index++) {
        List<PidElement> group = auxiliary.get(index);
        PidElement element = group.get(0);
        Attachment a = projection.getAttachments().get(element.getTag());
        double y = 125 + (index - offset) * 95;
        String sourceNozzle = textValue(a.toMap().get("physicalSourceNozzleTag"));
        String source = !sourceNozzle.isEmpty() ? sourceNozzle
            : !a.getSourceStreamTag().isEmpty() ? a.getSourceStreamTag() : a.getSourceEquipmentTag();
        String targetNozzle = textValue(a.toMap().get("physicalTargetNozzleTag"));
        String target = !targetNozzle.isEmpty() ? targetNozzle
            : !a.getTargetStreamTag().isEmpty() ? a.getTargetStreamTag()
                : !a.getTargetEquipmentTag().isEmpty() ? a.getTargetEquipmentTag() : a.getDestinationTag();
        Node from = scene.terminal("aux-in:" + element.getTag(), 135, y, source, true);
        if (a.getKind() == PidPhysicalProjection.Kind.SENSING) {
          Node measurement = scene.instrument(element, 410, y);
          scene.sensing(from, from.right(), measurement, "l", points(from.right(), measurement.left()),
              element.getTag());
          signalEnds(scene, element.getTag(), measurement);
          scene.text(75, y + 22, "UTILITY SENSING: " + a.getSourceEquipmentTag() + " / " + sourceNozzle, 2.5, null);
          continue;
        }
        if (group.size() > 3) {
          hold("AUXILIARY_SERIES_PAGINATION_REQUIRED", element.getTag(), "ISO 10628-1:2014",
              "More than three series devices require a controlled detail sheet");
          continue;
        }
        Node last = from;
        int position = 0;
        for (PidElement member : group) {
          Node valve = proposalValve(scene, member, group.size() == 1 ? 410 : 350 + position++ * 140, y);
          scene.route(last, "r", valve, "l", a.getMaterialLineTag(), false, null);
          signalEnds(scene, member.getTag(), valve);
          last = valve;
        }
        Node to = scene.terminal("aux-out:" + element.getTag(), 710, y, target, true);
        scene.route(last, "r", to, "l", a.getMaterialLineTag(), false, null);
        scene.text(90, y + 22, "SOURCE / " + sheetLabel(a.getSourceEquipmentTag()), 2.5, null);
        scene.text(660, y + 22, a.getTargetEquipmentTag().isEmpty() ? "DESTINATION / BATTERY LIMIT"
            : "DESTINATION / " + sheetLabel(a.getTargetEquipmentTag()), 2.5, null);
        scene.lineLabel(a.getMaterialLineTag(), 410, y + 31, unit.getName());
        for (PidElement member : group) {
          if (member.getType() == PidElementType.SAFETY_RELIEF_VALVE) {
            for (String id : member.getConnectedElementIds()) {
              PidElement destination = byId(id);
              if (destination != null && destination.getType() == PidElementType.OFF_PAGE_CONNECTOR) {
                scene.text(680, y - 20, destination.getTag(), 3, null);
                represented.add(destination.getTag());
              }
            }
          }
        }
      }
      scene.text(25, 490,
          "Each endpoint is an explicit project tie-in, not a destination inferred from a device tag or equipment order.",
          3, null);
      scene.text(25, 499,
          "Auxiliary sizes/classes, nozzle details, actuator data and relief sizing require the controlled project registers.",
          3, null);
      scene.finish();
    }
  }

  /**
   * Returns an explicit auxiliary-series order without assigning a missing engineering order.
   *
   * @param attachment shared physical evidence
   * @return supplied order or -1 for a single independent route
   */
  private static int routeOrder(Attachment attachment) {
    Object order = attachment.toMap().get("physicalRouteOrder");
    return order instanceof Number ? ((Number) order).intValue() : -1;
  }

  /**
   * Retrieves optional display text from immutable evidence.
   *
   * @param value evidence value
   * @return text or empty
   */
  private static String textValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /**
   * Paginates unresolved or unsupported content without hiding it or inventing piping.
   *
   * @param elements remaining proposal scope
   */
  private void renderHolds(List<PidElement> elements) {
    for (int offset = 0; offset < elements.size(); offset += 12) {
      Scene scene = scene(project.getProjectId(), "Unresolved engineering inputs - HOLD register");
      scene.spec.put("kind", "register");
      for (int index = offset; index < Math.min(offset + 12, elements.size()); index++) {
        PidElement element = elements.get(index);
        Attachment a = projection.getAttachments().get(element.getTag());
        double y = 78 + (index - offset) * 34;
        scene.text(30, y, element.getTag(), 3.5, null);
        scene.text(135, y, element.getType().name(), 3, null);
        scene.text(245, y, element.getEquipmentTag(), 3, null);
        scene.text(350, y,
            shorten(a.getReason().isEmpty() ? "Drawing attachment requires controlled project detail" : a.getReason(),
                85),
            3, null);
        represented.add(element.getTag());
        hold("PID_ATTACHMENT_HOLD", element.getTag(), "ISO 10628-1:2014",
            a.getReason().isEmpty() ? "Proposal is registered but has no qualified drawing attachment" : a.getReason());
      }
      scene.finish();
    }
  }

  /**
   * Places a sensor's directly declared control/trip function on the same row.
   *
   * @param scene page scene
   * @param sensor source proposal
   * @param measurement sensor symbol
   * @param x function position
   * @param y function position
   * @param placed already represented functions
   */
  private void placeChildFunction(Scene scene, PidElement sensor, Node measurement, double x, double y,
      Set<String> placed) {
    for (String id : sensor.getConnectedElementIds()) {
      PidElement child = byId(id);
      if (child != null
          && projection.getAttachments().get(child.getTag()).getKind() == PidPhysicalProjection.Kind.FUNCTION
          && !placed.contains(child.getTag())) {
        Node function = scene.instrument(child, x, y);
        scene.route(measurement, "r", function, "l", "signal:" + sensor.getTag() + ":" + child.getTag(), true, null);
        placed.add(child.getTag());
        String signal = signalId(sensor.getTag(), child.getTag());
        signalSources.add(signal);
        signalTargets.add(signal);
      }
    }
  }

  /**
   * Adds named control/safety continuations and their actual actuator endpoints.
   *
   * @param scene current page
   * @param tag P&amp;ID source/destination tag
   * @param node actual symbol
   */
  private void signalEnds(Scene scene, String tag, Node node) {
    List<String> incoming = new ArrayList<String>();
    List<String> outgoing = new ArrayList<String>();
    for (Map<String, String> signal : projection.getSignals()) {
      String id = signalIds.get(signalKey(signal));
      if (tag.equals(signal.get("sourceTag")) && !signalSources.contains(id)) {
        outgoing.add(id);
        signalSources.add(id);
      }
      if (tag.equals(signal.get("targetTag")) && !signalTargets.contains(id)) {
        incoming.add(id);
        signalTargets.add(id);
      }
    }
    if (!outgoing.isEmpty()) {
      String label = join(outgoing);
      double x = node.x + 55;
      double y = node.y;
      Node terminal = scene.terminal("signal-from:" + tag, x, y, label, true);
      scene.route(node, "r", terminal, "l", "signal-from:" + tag, true, null);
    }
    if (!incoming.isEmpty()) {
      boolean valve = node.family == Family.CONTROL_VALVE || node.family == Family.PNEUMATIC_OPERATED_VALVE
          || node.family == Family.ISOLATION_VALVE || node.family == Family.RELIEF_VALVE;
      Node terminal = scene.terminal("signal-to:" + tag, valve ? node.x : node.x - 75, valve ? node.y - 51 : node.y,
          join(incoming), true);
      scene.route(terminal, valve ? "b" : "r", node, valve ? "t" : "l", "signal-to:" + tag, true, null);
    }
  }

  /**
   * Draws physical source-nozzle identities at the represented equipment or valve ports.
   *
   * @param scene page scene
   * @param unit primary unit
   * @param body primary symbol
   * @param valves represented real/proposed inline valves
   */
  private void drawNozzles(Scene scene, ProcessEquipmentInterface unit, Node body, Map<String, Node> valves) {
    for (PidElement element : model.getElements()) {
      Attachment a = projection.getAttachments().get(element.getTag());
      if (a.getKind() != PidPhysicalProjection.Kind.PROCESS_NOZZLE) {
        continue;
      }
      Node owner = element.getEquipmentTag().equals(unit.getName()) ? body : valves.get(element.getEquipmentTag());
      if (owner == null) {
        continue;
      }
      Point point;
      boolean inlet = "INLET".equals(a.getDirection());
      if (owner == body && !inlet) {
        point = a.getStreamIndex() == 0 && unit instanceof Separator ? body.top()
            : a.getStreamIndex() > 0 && unit instanceof Separator ? body.bottom() : body.right();
      } else {
        point = inlet ? owner.left() : owner.right();
      }
      ProfessionalPidSymbolLibrary.draw(scene.page, Family.NOZZLE, point.x, point.y, 3, 4, element.getTag());
      scene.text(inlet ? point.x - 48 : point.x + 14, inlet ? point.y - 13 : point.y + (point.y < body.y ? -12 : 13),
          element.getTag(), 2.5, null);
      represented.add(element.getTag());
    }
  }

  /**
   * Draws the proposal's actual valve family and unassigned fail action explicitly.
   *
   * @param scene scene
   * @param element proposal
   * @param x position
   * @param y position
   * @return valve symbol
   */
  private Node proposalValve(Scene scene, PidElement element, double x, double y) {
    Family family = element.getType() == PidElementType.SAFETY_RELIEF_VALVE ? Family.RELIEF_VALVE
        : element.getType() == PidElementType.CHECK_VALVE ? Family.CHECK_VALVE
            : element.getType() == PidElementType.CONTROL_VALVE ? Family.CONTROL_VALVE : Family.ISOLATION_VALVE;
    if ((family == Family.CONTROL_VALVE || family == Family.ISOLATION_VALVE)
        && "PNEUMATIC_DIAPHRAGM_PROPOSAL".equals(element.getAttributes().get("actuatorType"))) {
      family = Family.PNEUMATIC_OPERATED_VALVE;
    }
    Node node = scene.node(element.getTag(), family, x, y, 16, 10, element.getTag());
    scene.proposalNodes.put(element.getTag(), node);
    scene.text(x, y + 21,
        family == Family.CHECK_VALVE ? "FLOW >" : family == Family.RELIEF_VALVE ? "SET:HOLD" : "FA:HOLD", 2.5, null);
    if (family != Family.CHECK_VALVE && family != Family.RELIEF_VALVE) {
      hold("VALVE_FAILURE_ACTION", element.getTag(), "ISO 10628-1:2014 / IEC 62424:2016",
          "Fail action and actuator detail require controlled project evidence");
    }
    return node;
  }

  /**
   * Selects source-backed inline proposals without duplicating existing physical valve objects.
   *
   * @param equipmentTag actual owner
   * @param streamTag declared actual line
   * @param direction flow boundary
   * @return deterministic inline sequence
   */
  private List<PidElement> inline(String equipmentTag, String streamTag, String direction) {
    List<PidElement> result = new ArrayList<PidElement>();
    for (PidElement element : model.getElementsForEquipment(equipmentTag)) {
      Attachment a = projection.getAttachments().get(element.getTag());
      if ((a.getKind() == PidPhysicalProjection.Kind.INLINE_VALVE
          || a.getKind() == PidPhysicalProjection.Kind.EXISTING_EQUIPMENT) && streamTag.equals(element.getLineTag())
          && direction.equals(a.getDirection())) {
        result.add(element);
      }
    }
    Collections.sort(result, new Comparator<PidElement>() {
      @Override
      public int compare(PidElement left, PidElement right) {
        return Integer.compare(projection.getAttachments().get(left.getTag()).getInlineOrder(),
            projection.getAttachments().get(right.getTag()).getInlineOrder());
      }
    });
    return result;
  }

  /**
   * Finds real valve descendants by actual stream identity.
   *
   * @param unit source unit
   * @return connected actual valves
   */
  private List<ProcessEquipmentInterface> downstreamValves(ProcessEquipmentInterface unit) {
    List<ProcessEquipmentInterface> result = new ArrayList<ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface candidate : process.getUnitOperations()) {
      if (candidate instanceof ThrottlingValve && !candidate.getInletStreams().isEmpty()
          && unit.getOutletStreams().contains(candidate.getInletStreams().get(0))) {
        result.add(candidate);
      }
    }
    return result;
  }

  /**
   * Resolves the actual upstream boundary for a stream.
   *
   * @param stream actual inlet
   * @return physical source identity
   */
  private String upstreamLabel(StreamInterface stream) {
    ProcessEquipmentInterface source = producers.get(stream);
    return source == null ? stream.getName() : sheetLabel(source.getName());
  }

  /**
   * Resolves the actual downstream boundary through a real outlet valve when present.
   *
   * @param stream actual outlet
   * @return physical target or explicit battery limit
   */
  private String downstreamLabel(StreamInterface stream) {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit != producers.get(stream) && unit.getInletStreams().contains(stream)) {
        if (unit instanceof ThrottlingValve) {
          for (StreamInterface outlet : unit.getOutletStreams()) {
            for (ProcessEquipmentInterface target : process.getUnitOperations()) {
              if (target != unit && target.getInletStreams().contains(outlet)) {
                return sheetLabel(target.getName());
              }
            }
          }
          return "BL " + unit.getOutletStreams().get(0).getName();
        }
        return sheetLabel(unit.getName());
      }
    }
    return "BL " + stream.getName();
  }

  /**
   * Returns a reciprocal equipment/sheet continuation label within this controlled drawing set.
   *
   * @param tag referenced equipment
   * @return equipment and main sheet number, or battery-limit/source identity
   */
  private String sheetLabel(String tag) {
    String sheet = mainSheetNumbers.get(tag);
    return sheet == null ? tag : tag + " / PID-" + sheet;
  }

  /**
   * Builds a source route clear of the primary equipment envelope.
   *
   * @param body source symbol
   * @param side source port side
   * @param end actual first downstream point
   * @param y route channel
   * @return orthogonal path
   */
  private static List<Point> sourceRoute(Node body, String side, Point end, double y) {
    Point start = body.port(side);
    if ("r".equals(side)) {
      return points(start, new Point(body.x + body.width / 2 + 20, start.y), new Point(body.x + body.width / 2 + 20, y),
          end);
    }
    return points(start, new Point(start.x, y), end);
  }

  /**
   * Returns a proposal by its controlled ID.
   *
   * @param id proposal ID
   * @return proposal or null
   */
  private PidElement byId(String id) {
    for (PidElement element : model.getElements()) {
      if (element.getId().equals(id)) {
        return element;
      }
    }
    return null;
  }

  /**
   * Returns one stable signal identity.
   *
   * @param from source tag
   * @param to target tag
   * @return signal ID
   */
  private String signalId(String from, String to) {
    for (Map<String, String> signal : projection.getSignals()) {
      if (from.equals(signal.get("sourceTag")) && to.equals(signal.get("targetTag"))) {
        return signalIds.get(signalKey(signal));
      }
    }
    return "";
  }

  /**
   * Returns a stable key for a directed functional connection.
   *
   * @param signal source and destination evidence
   * @return key
   */
  private static String signalKey(Map<String, String> signal) {
    return signal.get("sourceTag") + " -> " + signal.get("targetTag");
  }

  /**
   * Creates a native scene and source-linked sheet identity.
   *
   * @param subject subject tag
   * @param title sheet title
   * @return scene
   */
  private Scene scene(String subject, String title) {
    String number = String.format(Locale.ROOT, "%03d", Integer.valueOf(pages.size() + 1));
    String id = project.getProjectId() + "-PID-" + number;
    Scene scene = new Scene(id, subject + " - " + title, number);
    Map<String, Object> index = new LinkedHashMap<String, Object>();
    index.put("sheetId", id);
    index.put("sheetNumber", number);
    index.put("subjectTag", subject);
    index.put("sourceGraphFingerprint", documents.getSourceGraphFingerprint());
    sheets.add(index);
    return scene;
  }

  /**
   * Records a controlled content gap.
   *
   * @param code stable code
   * @param subject subject identity
   * @param standard relevant standard basis
   * @param message required input or review
   */
  private void hold(String code, String subject, String standard, String message) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("code", code);
    row.put("subject", subject);
    row.put("standard", standard);
    row.put("status", "HOLD");
    row.put("message", message);
    holds.add(row);
  }

  /**
   * Performs source-scene geometry checks without changing the generated drawing.
   *
   * @param layout actual geometry specification
   */
  @SuppressWarnings("unchecked")
  private void assessLayout(Map<String, Object> layout) {
    List<Map<String, Object>> text = (List<Map<String, Object>>) layout.get("labels");
    List<Map<String, Object>> symbols = (List<Map<String, Object>>) layout.get("nodes");
    List<Map<String, Object>> routes = (List<Map<String, Object>>) layout.get("edges");
    List<double[]> dots = (List<double[]>) layout.get("junctions");
    for (int index = 0; index < text.size(); index++) {
      Map<String, Object> label = text.get(index);
      double[] b = (double[]) label.get("bbox");
      String subject = String.valueOf(label.get("text"));
      if (b[0] < 10 || b[1] < 10 || b[0] + b[2] > WIDTH - 10 || b[1] + b[3] > HEIGHT - 10) {
        geometryError("PID_TEXT_OUTSIDE_SHEET", "Text crosses the controlled sheet border", subject);
      }
      for (int other = index + 1; other < text.size(); other++) {
        Map<String, Object> second = text.get(other);
        if (overlap(b, (double[]) second.get("bbox"), .15)) {
          geometryError("PID_TEXT_OVERLAP", "Text envelopes overlap: " + second.get("text"), subject);
        }
      }
      for (Map<String, Object> node : symbols) {
        if (!node.get("id").equals(label.get("owner")) && overlap(b, (double[]) node.get("bbox"), .15)) {
          geometryError("PID_TEXT_SYMBOL_OVERLAP", "Text overlaps symbol " + node.get("id"), subject);
        }
      }
      for (Map<String, Object> route : routes) {
        List<double[]> points = (List<double[]>) route.get("points");
        for (int segment = 1; segment < points.size(); segment++) {
          if (segmentInBox(points.get(segment - 1), points.get(segment), b, .25)) {
            geometryError("PID_TEXT_ROUTE_OVERLAP", "Text lies on a routed connection " + route.get("id"), subject);
            break;
          }
        }
      }
    }
    for (int index = 0; index < symbols.size(); index++) {
      Map<String, Object> node = symbols.get(index);
      for (int other = index + 1; other < symbols.size(); other++) {
        if (overlap((double[]) node.get("bbox"), (double[]) symbols.get(other).get("bbox"), .15)) {
          geometryError("PID_SYMBOL_OVERLAP", "Equipment/function envelopes overlap", String.valueOf(node.get("id")));
        }
      }
    }
    for (int index = 0; index < routes.size(); index++) {
      Map<String, Object> first = routes.get(index);
      List<double[]> a = (List<double[]>) first.get("points");
      for (int other = index + 1; other < routes.size(); other++) {
        List<double[]> b = (List<double[]>) routes.get(other).get("points");
        for (int ai = 1; ai < a.size(); ai++) {
          for (int bi = 1; bi < b.size(); bi++) {
            double[] intersection = crossing(a.get(ai - 1), a.get(ai), b.get(bi - 1), b.get(bi));
            if (intersection != null && !nearDot(intersection, dots)) {
              geometryError("PID_ROUTE_CROSSING", "Connections cross without a declared junction",
                  first.get("id") + " / " + routes.get(other).get("id"));
            }
          }
        }
      }
    }
  }

  /**
   * Adds one actual geometry error; engineering HOLDs use a separate report.
   *
   * @param code stable code
   * @param message description
   * @param subject controlled object
   */
  private void geometryError(String code, String message, String subject) {
    diagnostics.add(NativeEngineeringDiagramRenderer.sceneDiagnostic(Severity.ERROR, code, message, subject));
  }

  /**
   * Tests real envelope overlap after a small numerical clearance.
   *
   * @param a first box
   * @param b second box
   * @param inset numerical clearance
   * @return true for overlapping interiors
   */
  private static boolean overlap(double[] a, double[] b, double inset) {
    return Math.min(a[0] + a[2], b[0] + b[2]) - Math.max(a[0], b[0]) > inset
        && Math.min(a[1] + a[3], b[1] + b[3]) - Math.max(a[1], b[1]) > inset;
  }

  /**
   * Tests an orthogonal segment against a real text envelope.
   *
   * @param a start
   * @param b end
   * @param box text envelope
   * @param inset clearance
   * @return true if the segment enters the text
   */
  private static boolean segmentInBox(double[] a, double[] b, double[] box, double inset) {
    double x0 = box[0] + inset;
    double x1 = box[0] + box[2] - inset;
    double y0 = box[1] + inset;
    double y1 = box[1] + box[3] - inset;
    if (Math.abs(a[0] - b[0]) < 1e-6) {
      return a[0] > x0 && a[0] < x1 && Math.min(a[1], b[1]) < y1 && Math.max(a[1], b[1]) > y0;
    }
    return a[1] > y0 && a[1] < y1 && Math.min(a[0], b[0]) < x1 && Math.max(a[0], b[0]) > x0;
  }

  /**
   * Finds an interior orthogonal crossing; shared route endpoints are handled by semantic ports.
   *
   * @param a first start
   * @param b first end
   * @param c second start
   * @param d second end
   * @return intersection or null
   */
  private static double[] crossing(double[] a, double[] b, double[] c, double[] d) {
    boolean ah = Math.abs(a[1] - b[1]) < 1e-6;
    boolean ch = Math.abs(c[1] - d[1]) < 1e-6;
    if (ah == ch) {
      return null;
    }
    double x = ah ? c[0] : a[0];
    double y = ah ? a[1] : c[1];
    double[] h0 = ah ? a : c;
    double[] h1 = ah ? b : d;
    double[] v0 = ah ? c : a;
    double[] v1 = ah ? d : b;
    if (x > Math.min(h0[0], h1[0]) + .2 && x < Math.max(h0[0], h1[0]) - .2 && y > Math.min(v0[1], v1[1]) + .2
        && y < Math.max(v0[1], v1[1]) - .2) {
      return new double[] {x, y};
    }
    return null;
  }

  /**
   * Tests a crossing against the explicit physical junction register.
   *
   * @param point crossing
   * @param dots declared junctions
   * @return true for a deliberate connection
   */
  private static boolean nearDot(double[] point, List<double[]> dots) {
    for (double[] dot : dots) {
      if (Math.hypot(point[0] - dot[0], point[1] - dot[1]) < 1) {
        return true;
      }
    }
    return false;
  }

  /**
   * Makes a route-point list.
   *
   * @param points ordered source-to-target points
   * @return mutable independent list
   */
  private static List<Point> points(Point... points) {
    return new ArrayList<Point>(Arrays.asList(points));
  }

  /**
   * Joins named signal continuations.
   *
   * @param values IDs
   * @return compact IDs
   */
  private static String join(List<String> values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      if (result.length() > 0) {
        result.append(", ");
      }
      result.append(value);
    }
    return result.toString();
  }

  /**
   * Bounds a description to the controlled register cell width.
   *
   * @param value source text
   * @param maximum maximum characters
   * @return source text or an explicit shortened display, with full source retained in JSON
   */
  private static String shorten(String value, int maximum) {
    return value.length() <= maximum ? value : value.substring(0, maximum - 3) + "...";
  }

  /** One source-labelled drawing object. */
  private static final class Node {
    final String tag;
    final Family family;
    final double x;
    final double y;
    final double width;
    final double height;

    /**
     * Creates a symbol envelope.
     *
     * @param tag tag
     * @param family symbol family
     * @param x centre x
     * @param y centre y
     * @param width width
     * @param height height
     */
    Node(String tag, Family family, double x, double y, double width, double height) {
      this.tag = tag;
      this.family = family;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }

    /** @return left process port */
    Point left() {
      return new Point(x - width / 2 - (family == Family.SAFETY_FUNCTION ? 2 : 0), y);
    }

    /** @return right process port */
    Point right() {
      return new Point(x + width / 2 + (family == Family.SAFETY_FUNCTION ? 2 : 0), y);
    }

    /** @return upper sensing or actuator port */
    Point top() {
      return new Point(x, y - (family == Family.PNEUMATIC_OPERATED_VALVE ? 16
          : family == Family.CONTROL_VALVE ? 13 : family == Family.RELIEF_VALVE ? 18 : height / 2));
    }

    /** @return lower port */
    Point bottom() {
      return new Point(x, y + height / 2);
    }

    /**
     * Resolves a declared side.
     *
     * @param side l, r, t or b
     * @return port point
     */
    Point port(String side) {
      return "l".equals(side) ? left() : "r".equals(side) ? right() : "t".equals(side) ? top() : bottom();
    }
  }

  /** One native scene with a geometry specification used by pfd-layout-review. */
  private final class Scene {
    final Page page;
    final Map<String, Object> spec = new LinkedHashMap<String, Object>();
    final List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
    final List<Map<String, Object>> edges = new ArrayList<Map<String, Object>>();
    final List<Map<String, Object>> labels = new ArrayList<Map<String, Object>>();
    final List<double[]> junctions = new ArrayList<double[]>();
    final Map<String, Node> proposalNodes = new LinkedHashMap<String, Node>();

    /**
     * Creates the border, ISO-size paper and controlled document title fields.
     *
     * @param id controlled sheet ID
     * @param title title
     * @param number sheet number
     */
    Scene(String id, String title, String number) {
      this(id, title, number, "pid");
    }

    /**
     * Creates controlled PFD or P&amp;ID paper using the same native scene primitives.
     *
     * @param id sheet identity
     * @param title drawing title
     * @param number sheet number
     * @param kind pfd or pid
     */
    Scene(String id, String title, String number, String kind) {
      page = new Page(id, WIDTH, HEIGHT);
      page.commands.add(Command.rect(20, 10, WIDTH - 30, HEIGHT - 20, "#111111", "none", .5, "border", ""));
      text(25, 25, project.getName(), 5, null);
      text(25, 38, title, 4, null);
      text(25, 55,
          "ISO 10628 drawing basis | ISO A1 | solid: material / sensing | dashed: control signal | NOT TO SCALE", 3,
          null);
      page.commands.add(Command.rect(20, 535, WIDTH - 30, 49, "#111111", "none", .5, "title-block", ""));
      text(25, 546, "DRAWING: " + project.getProjectId() + "-" + kind.toUpperCase(Locale.ROOT) + "  /  SHEET: " + number
          + "  /  REV: " + project.getRevision(), 3.5, null);
      text(25, 558, "TITLE: " + title, 3, null);
      text(25, 570,
          "PREPARED: NeqSim native generator  |  CHECKED: HOLD  |  APPROVED: HOLD  |  ISSUE: ENGINEERING REVIEW", 3,
          null);
      text(490, 546, "PROJECT: " + project.getProjectId(), 3, null);
      text(490, 558, "SOURCE: " + documents.getSourceGraphFingerprint().substring(0, 16), 3, null);
      text(625, 558, "DATE: " + issuedAt, 3, null);
      text(490, 570, "SYMBOL REVIEW / ISO CONFORMANCE: HOLD", 3, null);
      Map<String, Object> canvas = new LinkedHashMap<String, Object>();
      canvas.put("width", Double.valueOf(WIDTH));
      canvas.put("height", Double.valueOf(HEIGHT));
      canvas.put("unit", "mm");
      canvas.put("intended_width_mm", Double.valueOf(WIDTH));
      spec.put("schema", "neqsim.pfd_layout.v1");
      spec.put("kind", kind);
      spec.put("name", id);
      spec.put("canvas", canvas);
      spec.put("nodes", nodes);
      spec.put("edges", edges);
      spec.put("labels", labels);
      spec.put("junctions", junctions);
      spec.put("title", Boolean.TRUE);
      spec.put("legend", Boolean.TRUE);
      spec.put("footer", Boolean.TRUE);
      spec.put("flow_direction", "");
      spec.put("drawing_area", new double[] {20, 75, 800, 400});
    }

    /**
     * Adds a symbol and its actual functional family.
     *
     * @param tag tag
     * @param family symbol family
     * @param x centre x
     * @param y centre y
     * @param width width
     * @param height height
     * @param label tag text
     * @return symbol node
     */
    Node node(String tag, Family family, double x, double y, double width, double height, String label) {
      ProfessionalPidSymbolLibrary.draw(page, family, x, y, width, height, tag);
      if (equipment.containsKey(tag)) {
        representedEquipment.add(tag);
      }
      Node node = new Node(tag, family, x, y, width, height);
      Map<String, Object> record = new LinkedHashMap<String, Object>();
      record.put("id", tag);
      record.put("tag", tag);
      record.put("kind", family == Family.HORIZONTAL_VESSEL || family == Family.VERTICAL_VESSEL ? "vessel"
          : family == Family.TERMINAL || family == Family.TERMINAL_NORTH ? "boundary"
              : family == Family.FIELD_INSTRUMENT ? "instrument"
                  : family == Family.CONTROL_FUNCTION || family == Family.SAFETY_FUNCTION ? "controller" : "equipment");
      double top = y - height / 2;
      double boundHeight = height;
      if (family == Family.CONTROL_VALVE || family == Family.PNEUMATIC_OPERATED_VALVE
          || family == Family.RELIEF_VALVE) {
        top = y - 19;
        boundHeight = 19 + height / 2;
      }
      record.put("bbox",
          family == Family.SAFETY_FUNCTION ? new double[] {x - width / 2 - 2, top - 2, width + 4, boundHeight + 4}
              : new double[] {x - width / 2, top, width, boundHeight});
      record.put("symbolFamily", family.name());
      record.put("standardReference",
          family == Family.FIELD_INSTRUMENT || family == Family.CONTROL_FUNCTION || family == Family.SAFETY_FUNCTION
              ? ProfessionalPidSymbolLibrary.INSTRUMENT_STANDARD
              : ProfessionalPidSymbolLibrary.EQUIPMENT_STANDARD);
      record.put("texts", new ArrayList<Object>());
      record.put("allow_position", Boolean.TRUE);
      nodes.add(record);
      if (label != null && !label.isEmpty()) {
        if (family == Family.FIELD_INSTRUMENT || family == Family.CONTROL_FUNCTION
            || family == Family.SAFETY_FUNCTION) {
          String[] parts = label.split("-");
          centered(x, y - 3, parts.length > 1 ? parts[parts.length - 2] : label, 3, tag);
          centered(x, y + 3.5, parts.length > 1 ? parts[parts.length - 1] : "", 3, tag);
        } else if (family == Family.TERMINAL || family == Family.TERMINAL_NORTH) {
          centered(x, y, shorten(label, 36), 2.7, tag);
        } else {
          centered(x, family == Family.VERTICAL_VESSEL || family == Family.HORIZONTAL_VESSEL ? y : y + height / 2 + 8,
              label, 3.5, tag);
        }
      }
      if (proposals.containsKey(tag)) {
        represented.add(tag);
      }
      return node;
    }

    /**
     * Adds a PCE function with its field/shared-control location.
     *
     * @param element source proposal
     * @param x x
     * @param y y
     * @return node
     */
    Node instrument(PidElement element, double x, double y) {
      Family family = element.getType() == PidElementType.MEASUREMENT || element.getType() == PidElementType.INDICATOR
          ? Family.FIELD_INSTRUMENT
          : element.getType() == PidElementType.CONTROLLER ? Family.CONTROL_FUNCTION : Family.SAFETY_FUNCTION;
      Node node = node(element.getTag(), family, x, y, 18, 18, element.getTag());
      proposalNodes.put(element.getTag(), node);
      return node;
    }

    /**
     * Adds an explicit material or signal continuation.
     *
     * @param tag identity
     * @param x x
     * @param y y
     * @param label source or target identity
     * @param compact true for a signal/tie-in reference
     * @return node
     */
    Node terminal(String tag, double x, double y, String label, boolean compact) {
      double width = compact ? Math.max(30, label.length() * 1.6 + 10) : 70;
      return node(tag, Family.TERMINAL, x, y, Math.min(115, width), 15, label);
    }

    /**
     * Adds a continuation whose symbol agrees with upward flow direction.
     *
     * @param tag identity
     * @param x x
     * @param y y
     * @param label controlled sheet reference
     * @param compact compact reference convention
     * @return north-facing continuation
     */
    Node verticalTerminal(String tag, double x, double y, String label, boolean compact) {
      return node(tag, Family.TERMINAL_NORTH, x, y, Math.max(30, label.length() * 1.6 + 10), 18, label);
    }

    /**
     * Adds one source-backed route and its geometry.
     *
     * @param from source
     * @param side source port
     * @param to destination
     * @param targetSide destination port
     * @param stream line identity
     * @param signal true for control signal
     * @param route explicit waypoints or null for straight route
     */
    void route(Node from, String side, Node to, String targetSide, String stream, boolean signal, List<Point> route) {
      List<Point> path = route == null ? points(from.port(side), to.port(targetSide)) : route;
      stroke(path, from.tag, to.tag, stream, signal, false);
    }

    /**
     * Adds a source-backed process take-off/return and its declared physical junction.
     *
     * @param tap actual line take-off point
     * @param terminal controlled continuation
     * @param outgoing true for a branch leaving the main line
     * @param id semantic connection
     */
    void materialContinuation(Point tap, Node terminal, boolean outgoing, String id) {
      junctions.add(new double[] {tap.x, tap.y});
      List<Point> dot = new ArrayList<Point>();
      for (int index = 0; index <= 16; index++) {
        dot.add(new Point(tap.x + .8 * Math.cos(index * Math.PI / 8), tap.y + .8 * Math.sin(index * Math.PI / 8)));
      }
      page.commands.add(Command.polygon(dot, "#111111", "#111111", .2, id + ":junction"));
      stroke(outgoing ? points(tap, terminal.bottom()) : points(terminal.top(), tap), outgoing ? "" : terminal.tag,
          outgoing ? terminal.tag : "", id, false, false);
    }

    /**
     * Adds an actual sensing tap with a junction dot, preserving field measurement location.
     *
     * @param owner equipment, or null for actual line sensing
     * @param tap source tap
     * @param measurement field instrument
     * @param side instrument attachment side
     * @param route orthogonal path
     * @param tag source sensor tag
     */
    void sensing(Node owner, Point tap, Node measurement, String side, List<Point> route, String tag) {
      junctions.add(new double[] {tap.x, tap.y});
      if (owner == null) {
        List<Point> dot = new ArrayList<Point>();
        for (int i = 0; i <= 16; i++) {
          dot.add(new Point(tap.x + .8 * Math.cos(i * Math.PI / 8), tap.y + .8 * Math.sin(i * Math.PI / 8)));
        }
        page.commands.add(Command.polygon(dot, "#111111", "#111111", .2, tag + ":tap"));
      }
      stroke(route, owner == null ? "" : owner.tag, measurement.tag, "sense:" + tag, false, true);
    }

    /**
     * Adds one native polyline, arrow and exact geometry sidecar.
     *
     * @param path source-to-target points
     * @param from source identity
     * @param to target identity
     * @param id controlled route ID
     * @param signal true for control signal
     * @param sensing true for sensing line
     */
    void stroke(List<Point> path, String from, String to, String id, boolean signal, boolean sensing) {
      List<Point> distinct = new ArrayList<Point>();
      for (Point point : path) {
        if (distinct.isEmpty() || Math.abs(point.x - distinct.get(distinct.size() - 1).x)
            + Math.abs(point.y - distinct.get(distinct.size() - 1).y) > 1e-7) {
          distinct.add(point);
        }
      }
      if (distinct.size() < 2) {
        return;
      }
      String dash = signal ? "3 2" : "";
      Command command = Command.polyline(distinct, "#111111", signal || sensing ? .25 : .5, dash, id, false);
      command.svgGroupAttribute = "data-stream";
      command.svgGroupValue = id;
      page.commands.add(command);
      Point end = distinct.get(distinct.size() - 1);
      Point before = distinct.get(distinct.size() - 2);
      double dx = end.x - before.x;
      double dy = end.y - before.y;
      double length = Math.hypot(dx, dy);
      dx /= length;
      dy /= length;
      if (!sensing) {
        page.commands.add(Command.polygon(
            points(end, new Point(end.x - 2.8 * dx - 1.1 * dy, end.y - 2.8 * dy + 1.1 * dx),
                new Point(end.x - 2.8 * dx + 1.1 * dy, end.y - 2.8 * dy - 1.1 * dx)),
            "#111111", "#111111", .2, id + ":arrow"));
      }
      List<double[]> coordinates = new ArrayList<double[]>();
      for (Point point : distinct) {
        coordinates.add(new double[] {point.x, point.y});
      }
      Map<String, Object> edge = new LinkedHashMap<String, Object>();
      edge.put("id", id + ":" + edges.size());
      edge.put("from", from);
      edge.put("to", to);
      edge.put("points", coordinates);
      edge.put("stream", id);
      edge.put("arrow_end", Boolean.valueOf(!sensing));
      edge.put("role", sensing ? "sensing" : "");
      edge.put("fluid", signal ? "signal" : sensing ? "sensing" : "process");
      Map<String, Object> style = new LinkedHashMap<String, Object>();
      style.put("color", "#111111");
      style.put("dash", dash);
      style.put("width", signal || sensing ? "0.25" : "0.5");
      edge.put("style", style);
      edges.add(edge);
    }

    /**
     * Adds an actual line designation including nominal size and piping class, or a visible HOLD.
     *
     * @param stream actual stream tag
     * @param x label centre x
     * @param y label y
     * @param owner source unit
     */
    void lineLabel(String stream, double x, double y, String owner) {
      LineDesignInput found = null;
      for (LineDesignInput line : project.getLineDesignInputs()) {
        if (stream.equals(line.getLineTag())) {
          found = line;
          break;
        }
      }
      String value = stream + " / DN:HOLD / CLASS:HOLD";
      if (found != null && !found.getNominalPipeSize().isEmpty() && !found.getPipingClass().isEmpty()) {
        value = stream + " / " + found.getNominalPipeSize() + " / " + found.getPipingClass();
      }
      if (assessedLines.add(stream) && (found == null || !found.getMissingFields().isEmpty())) {
        hold("LINE_DESIGN_INPUT", stream, "ISO 10628-1:2014 4.4.2",
            "Nominal size, piping class, material, design conditions and line-list evidence are required");
      }
      centered(x, y, value, 3, null);
    }

    /**
     * Adds start-anchored paper text and its full bounds.
     *
     * @param x x
     * @param y y
     * @param value text
     * @param size paper text height
     * @param owner owning symbol, or null
     */
    void text(double x, double y, String value, double size, String owner) {
      addText(x, y, value, size, owner, false);
    }

    /**
     * Adds centred paper text and its full bounds.
     *
     * @param x x
     * @param y y
     * @param value text
     * @param size paper text height
     * @param owner owner
     */
    void centered(double x, double y, String value, double size, String owner) {
      addText(x, y, value, size, owner, true);
    }

    /**
     * Adds native text and its estimated glyph envelope for layout checking.
     *
     * @param x anchor x
     * @param y anchor y
     * @param value text
     * @param size millimetre text height
     * @param owner owner
     * @param middle centre anchoring
     */
    void addText(double x, double y, String value, double size, String owner, boolean middle) {
      Command command = Command.text(x, y, size, value, "#111111", "text:" + labels.size(), middle ? "middle" : "");
      command.bold = size >= 3.5;
      page.commands.add(command);
      double width = value.length() * size * .52;
      Map<String, Object> label = new LinkedHashMap<String, Object>();
      label.put("text", value);
      label.put("font_size", Double.valueOf(size));
      label.put("bbox", new double[] {middle ? x - width / 2 : x, y - size * .6, width, size * 1.2});
      if (owner != null) {
        label.put("owner", owner);
        label.put("kind", "outside");
      }
      labels.add(label);
    }

    /** Finishes one controlled page without discarding unsupported scope. */
    void finish() {
      if (!nodes.isEmpty()) {
        double bottom = 0.0;
        double top = HEIGHT;
        for (Map<String, Object> node : nodes) {
          double[] bounds = (double[]) node.get("bbox");
          bottom = Math.max(bottom, bounds[1] + bounds[3]);
          top = Math.min(top, bounds[1]);
        }
        // The flow viewport excludes document notes and title blocks; paper stays ISO A1.
        spec.put("drawing_area", new double[] {20, top - 15, 800, bottom - top + 30});
        Map<String, Object> whitespace = new LinkedHashMap<String, Object>();
        whitespace.put("code", "WHITESPACE_POCKET");
        whitespace.put("reason",
            "Intentional clear schematic areas separate independent process branches, sensing columns and auxiliary circuits; all symbol, text and route intersections remain checked");
        spec.put("waivers", Collections.singletonList(whitespace));
      }
      pages.add(page);
      layouts.put(page.sheetId, spec);
    }
  }
}
