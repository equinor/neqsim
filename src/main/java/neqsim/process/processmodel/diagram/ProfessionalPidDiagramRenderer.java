package neqsim.process.processmodel.diagram;

import neqsim.process.engineering.pid.PidSizing;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.HashMap;
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
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.controllerdevice.AntiSurgeController;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.valve.SafetyReliefValve;
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
  /** Placeholder replaced by the total sheet count once every sheet is rendered. */
  private static final String SHEET_TOTAL_TOKEN = "@@SHEETS@@";
  /** Start of a peer-sheet placeholder resolved from the sheet where the peer is actually drawn. */
  private static final String SHEET_REF_START = "@@AT:";
  private static final java.util.regex.Pattern SHEET_REF = java.util.regex.Pattern
      .compile("/PID-@@AT:([^|@]*)\\|([^@]*)@@");
  /** First sheet number on which each proposal symbol is drawn. */
  private final Map<String, String> drawnSheets = new LinkedHashMap<String, String>();
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
  private PidSizing sizing;
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
  /** Source stream (name @ producer) by drawing line number; generated numbers stay traceable to the stream. */
  private final Map<String, String> lineNumbers = new LinkedHashMap<String, String>();
  private final Map<StreamInterface, String> streamLineNumbers = new java.util.IdentityHashMap<StreamInterface, String>();
  private final Map<String, String> nameOnlyLineNumbers = new HashMap<String, String>();
  /** Drawing nozzle label (N1, N2, ...) per equipment, mapped to the controlled nozzle identity. */
  private final Map<String, Map<String, String>> nozzleRegister = new LinkedHashMap<String, Map<String, String>>();
  private final Map<String, Integer> lineSequence = new HashMap<String, Integer>();
  private static final java.util.regex.Pattern LINE_TAG = java.util.regex.Pattern
      .compile("^[0-9A-Z]{1,4}-[A-Z]{1,5}-[0-9A-Z]{2,}([-/ ].*)?$");
  /** Signal sources represented. */
  private final Set<String> signalSources = new LinkedHashSet<String>();
  /** Signal destinations represented. */
  private final Set<String> signalTargets = new LinkedHashSet<String>();
  /** Actual lines whose controlled design data have been checked. */
  private final Set<String> assessedLines = new LinkedHashSet<String>();
  /** Planned controlled sheet number for each main equipment representation. */
  private final Map<String, String> mainSheetNumbers = new LinkedHashMap<String, String>();
  /** Source valves and tees drawn on dedicated material-path sheets. */
  private final Set<String> materialPathTags = new LinkedHashSet<String>();
  /** Controlled auxiliary sheet per explicit route. */
  private final Map<String, String> auxiliarySheetNumbers = new LinkedHashMap<String, String>();
  /** Auxiliary route keys (unit:group) in sheet order, pooled across units. */
  private final Set<String> pooledAuxiliaryKeys = new LinkedHashSet<String>();
  /** Auxiliary route groups awaiting the pooled sheets: {owning unit, ordered group members}. */
  private final List<Object[]> pooledAuxiliary = new ArrayList<Object[]>();
  private static final int AUXILIARY_ROUTES_PER_SHEET = 4;
  /** Heaters and coolers drawn as bands on shared heat-exchanger sheets. */
  private final List<ProcessEquipmentInterface> compactExchangers = new ArrayList<ProcessEquipmentInterface>();
  /** Heat-exchanger sheets in drawing order, each with its exchangers top to bottom. */
  private final List<List<ProcessEquipmentInterface>> exchangerSheets =
      new ArrayList<List<ProcessEquipmentInterface>>();
  private static final double EXCHANGER_SHEET_TOP = 88.0;
  /** ISO 5457 A1 frame: 20 mm filing margin on the left, 10 mm elsewhere. */
  private static final double FRAME_LEFT = 20.0;
  private static final double FRAME_TOP = 10.0;
  private static final double FRAME_RIGHT = 831.0;
  private static final double FRAME_BOTTOM = 584.0;
  private static final double EXCHANGER_SHEET_BOTTOM = 478.0;
  private static final double EXCHANGER_ROW = 25.0;
  /** Actual valve-port instrument details assigned to their existing primary sheet. */
  private final Map<String, List<PidElement>> instrumentDetails = new LinkedHashMap<String, List<PidElement>>();
  /** Planned reciprocal detail sheet for each actual instrument. */
  private final Map<String, String> instrumentDetailSheetNumbers = new LinkedHashMap<String, String>();
  /** Actual instruments deliberately placed in reciprocal details rather than crowded primary rows. */
  private final Set<String> instrumentDetailTags = new LinkedHashSet<String>();

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
    // Numerical tear utilities are not plant items: the physical producer of a recycle's outlet is the producer of
    // its inlet, so recycle lines connect their real physical ends on the drawing.
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof neqsim.process.equipment.util.Recycle && !unit.getInletStreams().isEmpty()
          && ((neqsim.process.equipment.util.Recycle) unit).getOutletStream() != null) {
        ProcessEquipmentInterface physical = producers.get(unit.getInletStreams().get(0));
        if (physical != null) {
          producers.put(((neqsim.process.equipment.util.Recycle) unit).getOutletStream(), physical);
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
    return render(new PidSizing(project));
  }

  /**
   * Renders a drawing set with the sizes used for its native exchange.
   * 
   * @param caseSizing case sizing snapshot
   * @return native vector result
   */
  public NativeEngineeringDiagramRenderer.Result render(PidSizing caseSizing) {
    sizing = caseSizing;
    if (!pages.isEmpty()) {
      throw new IllegalStateException("Create a renderer for each controlled generation");
    }
    for (Map.Entry<String, String> entry : sizing.getEquipmentHolds().entrySet()) {
      hold("EQUIPMENT_SIZING", entry.getKey(), "Mechanical design", entry.getValue());
    }
    List<List<ProcessEquipmentInterface>> materialPages = planReliefPaths();
    for (List<ProcessEquipmentInterface> path : materialPages) {
      for (ProcessEquipmentInterface member : path) {
        materialPathTags.add(member.getName());
      }
    }
    List<ProcessEquipmentInterface> primary = new ArrayList<ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && !(unit instanceof ThrottlingValve) && !supportedReliefTee(unit)
          && !supportedSuctionMixer(unit) && !supportedHeaderMixer(unit) && !numericalHelper(unit)
          && !materialPathTags.contains(unit.getName())) {
        primary.add(unit);
      }
    }
    if (primary.isEmpty() && materialPages.isEmpty()) {
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        if (!(unit instanceof StreamInterface)) {
          primary.add(unit);
        }
      }
    }
    planInstrumentDetails(primary);
    for (ProcessEquipmentInterface unit : primary) {
      if (compactExchanger(unit)) {
        compactExchangers.add(unit);
      }
    }
    int nextSheet = 1;
    for (ProcessEquipmentInterface unit : primary) {
      Family family = ProfessionalPidSymbolLibrary.family(unit);
      if (family == Family.UNMAPPED || family == Family.PIPE_TEE) {
        continue;
      }
      if (compactExchangers.contains(unit)) {
        for (String group : auxiliaryGroupKeys(unit)) {
          pooledAuxiliaryKeys.add(unit.getName() + ":" + group);
        }
        continue;
      }
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
      for (String group : auxiliaryGroups) {
        pooledAuxiliaryKeys.add(unit.getName() + ":" + group);
      }
      nextSheet = numberDetails(unit, nextSheet);
    }
    // Small heaters and coolers share compact "Heat exchangers" sheets, several bands per A1 sheet.
    double used = Double.MAX_VALUE;
    for (ProcessEquipmentInterface exchanger : compactExchangers) {
      double band = exchangerBandHeight(exchanger);
      if (used + band > EXCHANGER_SHEET_BOTTOM - EXCHANGER_SHEET_TOP) {
        exchangerSheets.add(new ArrayList<ProcessEquipmentInterface>());
        used = 0.0;
        nextSheet++;
      }
      exchangerSheets.get(exchangerSheets.size() - 1).add(exchanger);
      mainSheetNumbers.put(exchanger.getName(), String.format(Locale.ROOT, "%03d", Integer.valueOf(nextSheet - 1)));
      used += band;
    }
    for (List<ProcessEquipmentInterface> path : materialPages) {
      String number = String.format(Locale.ROOT, "%03d", Integer.valueOf(nextSheet++));
      for (ProcessEquipmentInterface member : path) {
        mainSheetNumbers.put(member.getName(), number);
      }
    }
    for (List<ProcessEquipmentInterface> path : materialPages) {
      for (ProcessEquipmentInterface member : path) {
        nextSheet = numberDetails(member, nextSheet);
      }
    }
    // Auxiliary connections of all units share pooled sheets (relief, blowdown, vent and drain system sheets), four
    // routes per sheet, instead of one sparsely filled auxiliary sheet per unit.
    int pooledIndex = 0;
    for (String key : pooledAuxiliaryKeys) {
      auxiliarySheetNumbers.put(key,
          String.format(Locale.ROOT, "%03d", Integer.valueOf(nextSheet + pooledIndex++ / AUXILIARY_ROUTES_PER_SHEET)));
    }
    nextSheet += (pooledAuxiliaryKeys.size() + AUXILIARY_ROUTES_PER_SHEET - 1) / AUXILIARY_ROUTES_PER_SHEET;
    for (ProcessEquipmentInterface junction : process.getUnitOperations()) {
      if (supportedSuctionMixer(junction) || supportedHeaderMixer(junction)) {
        ProcessEquipmentInterface receiver = soleConsumer(junction.getOutletStreams().get(0));
        mainSheetNumbers.put(junction.getName(), mainSheetNumbers.get(receiver.getName()));
      } else if (supportedReliefTee(junction)) {
        ProcessEquipmentInterface source = producers.get(junction.getInletStreams().get(0));
        mainSheetNumbers.put(junction.getName(), mainSheetNumbers.get(source.getName()));
      }
    }
    for (ProcessEquipmentInterface unit : primary) {
      if (ProfessionalPidSymbolLibrary.family(unit) == Family.UNMAPPED
          || ProfessionalPidSymbolLibrary.family(unit) == Family.PIPE_TEE) {
        hold("SYMBOL_FAMILY_UNMAPPED", unit.getName(), "ISO 10628-2:2012",
            "Provide an explicit supported equipment family; generic substitution is prohibited");
        diagnostics.add(NativeEngineeringDiagramRenderer.sceneDiagnostic(Severity.ERROR, "PID_SYMBOL_UNMAPPED",
            "No supported equipment family", unit.getName()));
        continue;
      }
      if (compactExchangers.contains(unit)) {
        collectAuxiliary(unit);
        continue;
      }
      renderUnit(unit);
      collectAuxiliary(unit);
      renderInstrumentDetails(unit);
    }
    for (List<ProcessEquipmentInterface> sheet : exchangerSheets) {
      renderExchangerSheet(sheet);
    }
    for (List<ProcessEquipmentInterface> path : materialPages) {
      renderReliefPath(path);
    }
    for (List<ProcessEquipmentInterface> path : materialPages) {
      for (ProcessEquipmentInterface member : path) {
        renderInstrumentDetails(member);
      }
    }
    renderPooledAuxiliary();
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
    fillSheetTotals();
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
   * Counts actual primary units, excluding only explicitly supported source-backed piping junctions.
   *
   * @return number of primary equipment symbols required by the overview
   */
  public int getPrimaryEquipmentCount() {
    int count = 0;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && !(unit instanceof ThrottlingValve) && !supportedReliefTee(unit)
          && !supportedSuctionMixer(unit) && !supportedHeaderMixer(unit) && !numericalHelper(unit)) {
        count++;
      }
    }
    return count;
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
      if (!(unit instanceof StreamInterface) && !(unit instanceof ThrottlingValve) && !supportedReliefTee(unit)
          && !supportedSuctionMixer(unit) && !supportedHeaderMixer(unit) && !numericalHelper(unit)) {
        primary.add(unit);
      }
    }
    if (primary.isEmpty() || primary.size() > 4) {
      throw new IllegalArgumentException("Native A1 PFD overview supports one to four primary units");
    }
    if (primary.size() == 1 && primary.get(0) instanceof HeatExchanger) {
      renderTwoCircuitExchanger((HeatExchanger) primary.get(0), true);
      for (Map<String, Object> layout : layouts.values()) {
        assessLayout(layout);
      }
      verifySourceCoverage();
      fillSheetTotals();
    return nativeRenderer.renderScenes(pages, diagnostics, Arrays.asList("DRAWING_SHEET_BOUNDS", "MATERIAL_ENDPOINTS",
          "TEXT_TEXT_AND_TEXT_ROUTE_INTERSECTIONS", "ORTHOGONAL_ROUTE_INTERSECTIONS"));
    }
    Scene scene = new Scene(project.getProjectId() + "-PFD-001", "Process flow diagram", "001", "pfd");
    Map<String, Node> actualNodes = new LinkedHashMap<String, Node>();
    boolean recycleTrain = false;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      recycleTrain |= supportedSuctionMixer(unit);
    }
    for (int index = 0; index < primary.size(); index++) {
      ProcessEquipmentInterface unit = primary.get(index);
      Family family = ProfessionalPidSymbolLibrary.family(unit);
      Node node = scene.node(unit.getName(), family,
          recycleTrain && primary.size() == 4 ? new double[] {175, 350, 500, 650}[index]
              : 145 + (4 - primary.size()) * 87.5 + index * 175,
          250, family == Family.HORIZONTAL_VESSEL ? 70 : 42,
          family == Family.VERTICAL_VESSEL ? 80 : family == Family.HORIZONTAL_VESSEL ? 50 : 42, unit.getName());
      actualNodes.put(unit.getName(), node);
      scene.centered(node.x, 195, unit.getClass().getSimpleName(), 3, null);
      if (family == Family.UNMAPPED) {
        geometryError("PFD_SYMBOL_UNMAPPED", "Unsupported source equipment family", unit.getName());
      }
      ProcessEquipmentInterface reliefTee = reliefTee(
          unit instanceof Separator ? ((Separator) unit).getGasOutStream() : null);
      if (reliefTee != null) {
        Node teeNode = scene.node(reliefTee.getName(), Family.PIPE_TEE, recycleTrain ? node.x + 60 : node.x + 53, 250,
            6, 6, "");
        actualNodes.put(reliefTee.getName(), teeNode);
        scene.centered(teeNode.x, 280, reliefTee.getName(), 3, null);
      }
      for (ProcessEquipmentInterface valve : downstreamValves(unit)) {
        if (reliefTee != null && valve.getClass() == SafetyReliefValve.class
            && reliefTee.getOutletStreams().contains(valve.getInletStreams().get(0))) {
          actualNodes.put(valve.getName(),
              scene.node(valve.getName(), Family.RELIEF_VALVE, node.x + 155, 175, 16, 10, valve.getName()));
          continue;
        }
        if (reliefTee != null && isActualAntiSurgeValve(valve)) {
          actualNodes.put(valve.getName(), scene.node(valve.getName(), ProfessionalPidSymbolLibrary.family(valve),
              node.x, 150, 16, 10, valve.getName()));
          continue;
        }
        boolean liquid = unit instanceof Separator
            && unit.getOutletStreams().indexOf(valve.getInletStreams().get(0)) > 0;
        Node valveNode = scene.node(valve.getName(), ProfessionalPidSymbolLibrary.family(valve),
            liquid ? node.x + 55 : node.x + 85, liquid ? 400 : 250, 16, 10, valve.getName());
        actualNodes.put(valve.getName(), valveNode);
      }
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (supportedSuctionMixer(unit)) {
        Node receiver = actualNodes.get(soleConsumer(unit.getOutletStreams().get(0)).getName());
        Node merge = scene.node(unit.getName(), Family.PIPE_TEE, receiver.x - 60, receiver.y, 6, 6, "");
        actualNodes.put(unit.getName(), merge);
        scene.centered(merge.x, merge.y + 30, unit.getName(), 3, null);
        for (StreamInterface inlet : unit.getInletStreams()) {
          ProcessEquipmentInterface upstream = producers.get(inlet);
          if (upstream instanceof ThrottlingValve && !isActualAntiSurgeValve(upstream)) {
            actualNodes.put(upstream.getName(), scene.node(upstream.getName(),
                ProfessionalPidSymbolLibrary.family(upstream), merge.x - 35, merge.y, 16, 10, upstream.getName()));
          }
        }
      }
    }
    // Direct upstream valves are real source equipment too. A valve already
    // represented as the previous primary unit's outlet must not be duplicated.
    for (ProcessEquipmentInterface unit : primary) {
      for (StreamInterface stream : unit.getInletStreams()) {
        ProcessEquipmentInterface producer = producers.get(stream);
        if (producer instanceof ThrottlingValve && !actualNodes.containsKey(producer.getName())) {
          Node owner = actualNodes.get(unit.getName());
          actualNodes.put(producer.getName(), scene.node(producer.getName(),
              ProfessionalPidSymbolLibrary.family(producer), owner.x - 85, owner.y, 16, 10, producer.getName()));
        }
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
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (supportedReliefTee(unit) && actualNodes.containsKey(unit.getName())) {
        for (StreamInterface stream : unit.getOutletStreams()) {
          if (streams.add(stream)) {
            streamTable.add(stream);
          }
        }
      }
      if (unit instanceof ThrottlingValve && actualNodes.containsKey(unit.getName())) {
        for (StreamInterface stream : unit.getInletStreams()) {
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
        from = scene.node("pfd-in:" + stream.getName(), Family.TERMINAL, Math.max(45, to.x - 85), to.y, 36, 15, "FEED");
      } else if (to == null && from != null) {
        boolean liquidOutlet = producer instanceof Separator && producer.getOutletStreams().indexOf(stream) > 0;
        to = scene.node("pfd-out:" + stream.getName(), Family.TERMINAL, Math.min(790, from.x + 85),
            liquidOutlet ? 400 : from.y, 50, 15, "PRODUCT");
      }
      if (from == null || to == null) {
        geometryError("PFD_STREAM_UNRESOLVED", "Source stream has no represented physical endpoints", stream.getName());
        continue;
      }
      boolean upwardRelief = producer != null && supportedReliefTee(producer) && consumer != null
          && (consumer.getClass() == SafetyReliefValve.class || isActualAntiSurgeValve(consumer));
      boolean returningRecycle = isActualAntiSurgeValve(producer) && supportedSuctionMixer(consumer);
      boolean downward = to.y > from.y;
      boolean vertical = downward && Math.abs(from.x - to.x) < .01;
      if (returningRecycle) {
        scene.route(from, "l", to, "t", stream.getName(), false,
            points(from.left(), new Point(to.x, from.y), to.top()));
        scene.centered((from.x + to.x) / 2, from.y - 14, stream.getName(), 3, null);
      } else if (upwardRelief && to.x < from.x) {
        scene.route(from, "t", to, "r", stream.getName(), false,
            points(from.top(), new Point(from.x, to.y), to.right()));
        scene.centered(from.x + 25, (from.y + to.y) / 2, stream.getName(), 3, null);
      } else {
        scene.route(from, upwardRelief ? "t" : downward ? "b" : "r", to, vertical ? "t" : "l", stream.getName(), false,
            upwardRelief ? points(from.top(), new Point(from.x, to.y), to.left())
                : downward && !vertical ? points(from.bottom(), new Point(from.x, to.y), to.left()) : null);
        scene.centered(
            producer != null && supportedReliefTee(producer) && !upwardRelief ? (from.x + to.x) / 2 + 15
                : upwardRelief ? (from.x + to.x) / 2 : downward ? from.x + 38 : (from.right().x + to.left().x) / 2,
            producer != null && supportedReliefTee(producer) && !upwardRelief ? from.y + 42
                : upwardRelief ? to.y - 28
                    : downward ? (from.bottom().y + to.top().y) / 2
                        : from.y - (to.left().x - from.right().x < 40 ? 40 : 14),
            stream.getName(), 3, null);
      }
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
        break;
      }
      scene.text(
          25 + (index / 6) * 265, 465 + (index % 6) * 10, String.format(Locale.ROOT, "%s  |  %.2f  |  %.2f  |  %.2f",
              stream.getName(), stream.getPressure("bara"), stream.getTemperature("C"), stream.getFlowRate("kg/hr")),
          3, null);
    }
    scene.finish();
    for (int offset = 18; offset < streamTable.size(); offset += 30) {
      String number = String.format(Locale.ROOT, "%03d", Integer.valueOf(2 + (offset - 18) / 30));
      Scene data = new Scene(project.getProjectId() + "-PFD-" + number, "Process flow stream data", number, "pfd");
      data.text(25, 90, "CONTINUED FROM PFD-001 | normal operating values; independent design basis: HOLD", 3.5, null);
      data.text(25, 110, "STREAM | pressure bara | temperature C | mass flow kg/h", 3.5, null);
      for (int row = offset; row < Math.min(offset + 30, streamTable.size()); row++) {
        StreamInterface stream = streamTable.get(row);
        data.text(25, 130 + (row - offset) * 12, String.format(Locale.ROOT, "%s  |  %.4f  |  %.3f  |  %.4f",
            stream.getName(), stream.getPressure("bara"), stream.getTemperature("C"), stream.getFlowRate("kg/hr")), 3,
            null);
      }
      data.finish();
    }
    for (Map<String, Object> layout : layouts.values()) {
      assessLayout(layout);
    }
    verifySourceCoverage();
    fillSheetTotals();
    return nativeRenderer.renderScenes(pages, diagnostics, Arrays.asList("DRAWING_SHEET_BOUNDS", "MATERIAL_ENDPOINTS",
        "TEXT_TEXT_AND_TEXT_ROUTE_INTERSECTIONS", "ORTHOGONAL_ROUTE_INTERSECTIONS"));
  }

  /** Fails closed when a real physical source item has no drawing representation. */
  private void verifySourceCoverage() {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && !numericalHelper(unit)
          && !representedEquipment.contains(unit.getName())) {
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
    report.put("standardsBasis", Arrays.asList("ISO 15519-1:2010", "ISO 15519-2:2015", "ISO 10628-1:2014",
        "ISO 10628-2:2012", "ISO 5457", "ISO 7200", "IEC 62424:2016", "ANSI/ISA-5.1:2024"));
    Map<String, String> standardsAssessment = new LinkedHashMap<String, String>();
    standardsAssessment.put("ISO 15519-1:2010", "CONTROLLED_REQUIREMENTS_REVIEW_REQUIRED");
    standardsAssessment.put("ISO 15519-2:2015", "CONTROLLED_REQUIREMENTS_REVIEW_REQUIRED");
    report.put("standardsAssessment", standardsAssessment);
    report.put("paper", "A1_LANDSCAPE_841_X_594_MM");
    report.put("processLineWidthMm", Double.valueOf(.5));
    report.put("instrumentLineWidthMm", Double.valueOf(.25));
    report.put("minimumTextHeightMm", Double.valueOf(2.5));
    report.put("proposalCount", Integer.valueOf(model.getElements().size()));
    report.put("representedProposalTags", new ArrayList<String>(represented));
    report.put("drawingIndex", sheets);
    report.put("signalContinuations", projection.getSignals());
    report.put("lineNumberRegister", lineNumbers);
    report.put("nozzleRegister", nozzleRegister);
    Map<String, String> conventions = new LinkedHashMap<String, String>();
    conventions.put("FIELD_INSTRUMENT", "ISA-5.1 discrete field-mounted instrument: circle");
    conventions.put("CONTROL_FUNCTION",
        "ISA-5.1 shared display / shared control (PCS/DCS), operator accessible: circle in square with line");
    conventions.put("SAFETY_FUNCTION",
        "ISA-5.1 programmable logic function used for SIS/ESD/PSD logic: diamond in square with line");
    conventions.put("CONTROL_VALVE", "ISO 10628-2 globe-pattern valve body with ISA-5.1 generic actuator");
    conventions.put("SHUTDOWN_VALVE",
        "On/off shutdown or blowdown valve: ball closure element with ISA-5.1 generic actuator");
    conventions.put("ISOLATION_VALVE", "Manual isolation valve body without actuator");
    conventions.put("ACTUATOR_TYPE", "Generic actuator shown until the project selects the actuator type (HOLD)");
    conventions.put("SIGNAL_LINE", "Dashed 0.25 mm; drawn directly on the sheet, continuation flags only across sheets");
    conventions.put("PROCESS_LINE", "Solid 0.5 mm with <area>-<fluid code>-<sequence> / DN / class designation");
    report.put("symbolConventions", conventions);
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
    if (unit instanceof HeatExchanger && unit.getInletStreams().size() == 2 && unit.getOutletStreams().size() == 2) {
      renderTwoCircuitExchanger((HeatExchanger) unit, false);
      return;
    }
    Scene scene = scene(unit.getName(), "Process and instrumentation");
    Family family = ProfessionalPidSymbolLibrary.family(unit);
    double width = family == Family.HORIZONTAL_VESSEL ? 100 : 46;
    double height = family == Family.VERTICAL_VESSEL ? 90 : family == Family.HORIZONTAL_VESSEL ? 60 : 46;
    Node body = scene.node(unit.getName(), family, 320, 280, width, height, unit.getName());
    equipmentCallout(scene, unit, body.x);
    List<ProcessEquipmentInterface> valves = downstreamValves(unit);
    Map<String, Node> valveNodes = new LinkedHashMap<String, Node>();
    int inlet = 0;
    for (StreamInterface stream : unit.getInletStreams()) {
      double y = 280 + inlet * 45;
      ProcessEquipmentInterface inletProducer = producers.get(stream);
      if (supportedSuctionMixer(inletProducer)) {
        renderSuctionMixer(scene, body, inletProducer, stream, valveNodes);
        inlet++;
        continue;
      }
      if (supportedHeaderMixer(inletProducer) && inline(unit.getName(), stream.getName(), "INLET").isEmpty()) {
        renderHeaderMixer(scene, body, inletProducer, stream, y);
        inlet++;
        continue;
      }
      List<PidElement> inline = inline(unit.getName(), stream.getName(), "INLET");
      StreamInterface sourceStream = stream;
      if (!inline.isEmpty()) {
        Attachment first = projection.getAttachments().get(inline.get(0).getTag());
        ProcessEquipmentInterface actual = equipment.get(first.getEquipmentTag());
        if (first.getKind() == PidPhysicalProjection.Kind.EXISTING_EQUIPMENT && actual != null
            && !actual.getInletStreams().isEmpty()) {
          sourceStream = actual.getInletStreams().get(0);
        }
      }
      Node source = supportedHeaderMixer(inletProducer) ? headerTee(scene, inletProducer, y)
          : scene.terminal("in:" + stream.getName(), 60, y, upstreamLabel(sourceStream), false);
      Node last = source;
      int index = 0;
      String physicalStream = sourceStream.getName();
      for (PidElement proposal : inline) {
        Node node = proposalValve(scene, proposal, 130 + index++ * 65, y);
        valveNodes.put(proposal.getTag(), node);
        scene.route(last, "r", node, "l", physicalStream, false, null);
        last = node;
        Attachment attachment = projection.getAttachments().get(proposal.getTag());
        ProcessEquipmentInterface actual = equipment.get(attachment.getEquipmentTag());
        if (attachment.getKind() == PidPhysicalProjection.Kind.EXISTING_EQUIPMENT && actual != null
            && !actual.getOutletStreams().isEmpty()) {
          physicalStream = actual.getOutletStreams().get(0).getName();
        }
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
      ProcessEquipmentInterface tee = reliefTee(stream);
      if (tee != null) {
        renderPassiveReliefBranch(scene, unit, body, stream, tee, valveNodes);
        outlet++;
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
        scene.route(last, startSide, node, "l", physicalStream, false,
            last == body ? outletRoute(unit, body, startSide, node.left(), y, outlet) : null);
        last = node;
        startSide = "r";
        if (physicalValves.contains(element.getTag())
            && !equipment.get(element.getTag()).getOutletStreams().isEmpty()) {
          physicalStream = equipment.get(element.getTag()).getOutletStreams().get(0).getName();
        }
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
            last == body ? outletRoute(unit, body, startSide, node.left(), y, outlet) : null);
        last = node;
        startSide = "r";
        ProcessEquipmentInterface actualValve = equipment.get(actual);
        if (actualValve != null && !actualValve.getOutletStreams().isEmpty()) {
          physicalStream = actualValve.getOutletStreams().get(0).getName();
        }
      }
      Node target = scene.terminal("out:" + stream.getName(), 790, y, downstreamLabel(stream), false);
      scene.route(last, startSide, target, "l", physicalStream, false,
          last == body ? outletRoute(unit, body, startSide, target.left(), y, outlet) : null);
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
      if (instrumentDetailTags.contains(element.getTag())) {
        continue;
      }
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
    int outletSensorCount = sensors.size();
    sensors.addAll(vesselSensors);
    Set<String> placed = new LinkedHashSet<String>();
    int row = 0;
    int vesselIndex = 0;
    for (PidElement sensor : sensors) {
      Attachment attachment = projection.getAttachments().get(sensor.getTag());
      boolean vessel = "VESSEL".equals(attachment.getDirection());
      double y = 200 + row * 36 + (vessel && vesselSensors.size() > 1 ? 4 : 0);
      if (y > 425) {
        continue;
      }
      Node measurement = scene.instrument(sensor, 520, y);
      if (vessel) {
        double py = body.y - body.height / 2 + body.height * (vesselIndex + 1) / (vesselSensors.size() + 1);
        double channel = Math.min(415, 425 - Math.max(0, outletSensorCount - 1) * 9 - 12) - vesselIndex * 11;
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
    int manualRow = 0;
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment attachment = projection.getAttachments().get(element.getTag());
      if (attachment.getKind() == PidPhysicalProjection.Kind.MANUAL_INPUT) {
        double y = 95 + manualRow++ * 36;
        Node input = scene.instrument(element, 175, y);
        scene.text(155, y + 17, "MANUAL INPUT", 2.7, null);
        placed.add(element.getTag());
        placeChildFunction(scene, element, input, 250, y, placed);
      }
    }
    placeSharedFunctions(scene, placed);
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment attachment = projection.getAttachments().get(element.getTag());
      if (attachment.getKind() == PidPhysicalProjection.Kind.FUNCTION && !placed.contains(element.getTag())
          && !localCommandOutput(element)) {
        double y = 200 + row++ * 36;
        if (y <= 425) {
          Node function = scene.instrument(element, 640, y);
          placed.add(element.getTag());
          placeLocalCommand(scene, element, function, placed);
        }
      }
    }
    for (Map.Entry<String, Node> entry : new LinkedHashMap<String, Node>(scene.proposalNodes).entrySet()) {
      signalEnds(scene, entry.getKey(), entry.getValue());
    }
    drawNozzles(scene, unit, body, valveNodes);
    drawAuxiliaryContinuations(scene, unit);
    equipmentSize(scene, unit);
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
    List<PidElement> details = instrumentDetails.get(unit.getName());
    if (details != null && !details.isEmpty()) {
      List<String> references = new ArrayList<String>();
      for (PidElement detail : details) {
        references.add(detail.getTag() + " / PID-" + instrumentDetailSheetNumbers.get(detail.getTag()));
      }
      scene.text(25, 518, "Actual valve-port instrumentation details: " + join(references), 3, null);
    }
    scene.finish();
  }

  /** Numbers the detail pages in the same order as their four-row rendering. */
  private int numberDetails(ProcessEquipmentInterface owner, int first) {
    List<PidElement> details = instrumentDetails.get(owner.getName());
    if (details == null) {
      return first;
    }
    for (int i = 0; i < details.size(); i++) {
      instrumentDetailSheetNumbers.put(details.get(i).getTag(),
          String.format(Locale.ROOT, "%03d", Integer.valueOf(first + i / 4)));
    }
    return first + (details.size() + 3) / 4;
  }

  /**
   * Assigns actual valve-port measurements to the primary sheet that represents that exact valve.
   *
   * @param primary actual primary equipment in drawing order
   */
  private void planInstrumentDetails(List<ProcessEquipmentInterface> primary) {
    for (ProcessEquipmentInterface unit : primary) {
      if (unit instanceof HeatExchanger) {
        continue; // The four-port template owns each exact exchanger and adjacent valve sensing segment.
      }
      List<PidElement> rows = new ArrayList<PidElement>();
      for (PidElement element : model.getElementsForEquipment(unit.getName())) {
        Attachment attachment = projection.getAttachments().get(element.getTag());
        if (attachment.getKind() == PidPhysicalProjection.Kind.SENSING
            && ("VESSEL".equals(attachment.getDirection()) || "OUTLET".equals(attachment.getDirection()))) {
          rows.add(element);
        }
      }
      int overflow = rows.size() - 6;
      for (int index = rows.size() - 1; index >= 0 && overflow > 0; index--) {
        PidElement element = rows.get(index);
        Attachment attachment = projection.getAttachments().get(element.getTag());
        if (element.getConnectedElementIds().isEmpty() && "OUTLET".equals(attachment.getDirection())
            && !attachment.getBoundSimulationName().isEmpty()) {
          addInstrumentDetail(unit.getName(), element);
          overflow--;
        }
      }
    }
    for (PidElement element : model.getElements()) {
      Attachment attachment = projection.getAttachments().get(element.getTag());
      ProcessEquipmentInterface actual = equipment.get(attachment.getEquipmentTag());
      if (materialPathTags.contains(element.getEquipmentTag())
          && attachment.getKind() == PidPhysicalProjection.Kind.FUNCTION
          && element.getType() == PidElementType.SAFETY_FUNCTION
          && projection.getSignals().stream().anyMatch(link -> element.getTag().equals(link.get("sourceTag"))
              || element.getTag().equals(link.get("targetTag")))) {
        addInstrumentDetail(element.getEquipmentTag(), element);
        continue;
      }
      if (attachment.getKind() != PidPhysicalProjection.Kind.SENSING || !(actual instanceof ThrottlingValve)
          || attachment.getBoundSimulationName().isEmpty()
          || !("INLET".equals(attachment.getDirection()) || "OUTLET".equals(attachment.getDirection()))) {
        continue;
      }
      if (materialPathTags.contains(actual.getName())) {
        addInstrumentDetail(actual.getName(), element);
        continue;
      }
      ProcessEquipmentInterface found = null;
      for (ProcessEquipmentInterface unit : primary) {
        boolean owns = downstreamValves(unit).contains(actual);
        PidElement canonicalValve = proposals.get(actual.getName());
        owns |= canonicalValve != null && unit.getName().equals(canonicalValve.getEquipmentTag());
        for (PidElement inlet : model.getElementsForEquipment(unit.getName())) {
          Attachment location = projection.getAttachments().get(inlet.getTag());
          owns |= location.getKind() == PidPhysicalProjection.Kind.EXISTING_EQUIPMENT
              && actual.getName().equals(location.getBoundSimulationName()) && "INLET".equals(location.getDirection());
        }
        if (owns) {
          if (found != null) {
            found = null;
            break;
          }
          found = unit;
        }
      }
      if (found != null && !(found instanceof HeatExchanger)) {
        addInstrumentDetail(found.getName(), element);
      }
    }
  }

  /**
   * Renders four distinct material ports and source-bound instrumentation for an actual two-stream exchanger.
   *
   * @param unit actual exchanger, independent of tag naming or fluid property packages
   * @param overview true for the source-only PFD profile
   */
  private void renderTwoCircuitExchanger(HeatExchanger unit, boolean overview) {
    Scene scene = overview
        ? new Scene(project.getProjectId() + "-PFD-001", "Two-circuit heat exchanger process flow", "001", "pfd")
        : scene(unit.getName(), "Two-circuit process and instrumentation");
    Node body = scene.node(unit.getName(), Family.HEAT_EXCHANGER, 400, 285, 90, 90, null);
    scene.centered(400, 320, unit.getName(), 3.5, unit.getName());
    Map<String, Node> local = new LinkedHashMap<String, Node>();
    Map<String, List<Point>> material = new LinkedHashMap<String, List<Point>>();
    for (int side = 0; side < 2; side++) {
      StreamInterface inlet = unit.getInStream(side);
      ProcessEquipmentInterface producer = producers.get(inlet);
      ThrottlingValve upstream = producer instanceof ThrottlingValve ? (ThrottlingValve) producer : null;
      StreamInterface sourceStream = upstream == null ? inlet : upstream.getInletStream();
      double y = side == 0 ? 285 : 440;
      Node source = side == 0 ? scene.terminal("in:" + inlet.getName(), 70, y, upstreamLabel(sourceStream), false)
          : scene.node("in:" + inlet.getName(), Family.TERMINAL_WEST, 760, y, 70, 15, upstreamLabel(sourceStream));
      Node last = source;
      if (upstream != null) {
        PidElement proposal = proposals.get(upstream.getName());
        Node valve = proposal == null
            ? scene.node(upstream.getName(), Family.CONTROL_VALVE, side == 0 ? 180 : 600, y, 16, 10, upstream.getName())
            : proposalValve(scene, proposal, side == 0 ? 180 : 600, y);
        local.put(upstream.getName(), valve);
        List<Point> supply = side == 0 ? points(source.right(), valve.left()) : points(source.left(), valve.right());
        scene.stroke(supply, source.tag, valve.tag, sourceStream.getName(), false, false);
        material.put(sourceStream.getName(), supply);
        last = valve;
      }
      List<Point> feed = side == 0 ? points(last.right(), body.left())
          : points(last.left(), new Point(400, y), body.bottom());
      scene.stroke(feed, last.tag, body.tag, inlet.getName(), false, false);
      material.put(inlet.getName(), feed);
      exchangerLineLabel(scene, sourceStream, side == 0 ? 200 : 690, y + 32, overview, unit.getName());
      if (upstream != null) {
        exchangerLineLabel(scene, inlet, side == 0 ? 275 : 470, side == 0 ? y + 54 : y - 20, overview, unit.getName());
      }
    }
    for (int side = 0; side < 2; side++) {
      StreamInterface outlet = unit.getOutStream(side);
      Node target = side == 0 ? scene.terminal("out:" + outlet.getName(), 760, 285, downstreamLabel(outlet), false)
          : scene.node("out:" + outlet.getName(), Family.TERMINAL_WEST, 70, 140, 70, 15, downstreamLabel(outlet));
      List<Point> route = side == 0 ? points(body.right(), target.left())
          : points(body.top(), new Point(400, 140), target.right());
      scene.stroke(route, body.tag, target.tag, outlet.getName(), false, false);
      material.put(outlet.getName(), route);
      exchangerLineLabel(scene, outlet, side == 0 ? 600 : 225, side == 0 ? 325 : 177, overview, unit.getName());
    }
    if (!overview) {
      for (int side = 0; side < 2; side++) {
        double inletTap = side == 0 ? (producers.get(unit.getInStream(0)) instanceof ThrottlingValve ? 215 : 140) : 430;
        exchangerMeasurements(scene, unit.getInStream(side), inletTap, side == 0 ? 220 : 490, side == 0 ? 285 : 440,
            local);
        exchangerMeasurements(scene, unit.getOutStream(side), side == 0 ? 520 : 160, side == 0 ? 220 : 95,
            side == 0 ? 285 : 140, local);
      }
      int controllerIndex = 0;
      for (PidElement element : model.getElements()) {
        if (element.getType() == PidElementType.CONTROLLER && unit.getName().equals(element.getEquipmentTag())
            && controllerIndex < 2) {
          local.put(element.getTag(), scene.instrument(element, 620 + controllerIndex++ * 100, 160));
        }
      }
      for (Map<String, String> signal : projection.getSignals()) {
        Node source = local.get(signal.get("sourceTag"));
        Node target = local.get(signal.get("targetTag"));
        if (source == null || target == null) {
          continue;
        }
        List<Point> route = target.family == Family.CONTROL_VALVE || target.family == Family.PNEUMATIC_OPERATED_VALVE
            || target.family == Family.SHUTDOWN_VALVE ? points(source.right(), new Point(815, source.y), new Point(815, 385), new Point(target.x, 385),
                target.top())
            : points(source.right(), new Point(source.right().x + 22, source.y),
                new Point(source.right().x + 22, target.y), target.left());
        String id = signalIds.get(signalKey(signal));
        scene.route(source, "r", target, target.family == Family.CONTROL_FUNCTION ? "l" : "t", id, true, route);
        signalSources.add(id);
        signalTargets.add(id);
      }
      for (Map.Entry<String, Node> entry : local.entrySet()) {
        signalEnds(scene, entry.getKey(), entry.getValue());
      }
      for (PidElement element : model.getElementsForEquipment(unit.getName())) {
        Attachment attachment = projection.getAttachments().get(element.getTag());
        if (attachment.getKind() == PidPhysicalProjection.Kind.PROCESS_NOZZLE) {
          boolean inlet = "INLET".equals(attachment.getDirection());
          Point port = attachment.getStreamIndex() == 0 ? (inlet ? body.left() : body.right())
              : (inlet ? body.bottom() : body.top());
          scene.node(element.getTag(), Family.NOZZLE, port.x, port.y, 4, 4, null);
        }
      }
    }
    if (!overview) {
      equipmentSize(scene, unit);
    }
    scene.spec.put("exchangerMaterialSegments", material);
    scene.text(130, 385, "Two independent material circuits; separate property models and actual source ports.", 3,
        null);
    scene.text(130, 398, "Normal operating values remain distinct from line design inputs and construction approval.",
        3, null);
    scene.finish();
  }

  /**
   * Adds a size callout in the equipment data band of the sheet.
   * 
   * @param scene drawing sheet
   * @param unit subject equipment
   */
  private void equipmentSize(Scene scene, ProcessEquipmentInterface unit) {
    String label = sizing.equipmentLabel(unit.getName());
    if (!label.isEmpty()) {
      scene.page.commands.add(Command.rect(486, 16, 337, 28, "#111111", "none", .25, "equipment-data", ""));
      scene.text(490, 24, "EQUIPMENT DATA   /   " + unit.getName(), 3, null);
      scene.text(490, 38, label, 4, null);
    }
  }

  /**
   * Places up to three actual measurements on their exact physical material segment.
   *
   * @param scene controlled native scene
   * @param stream actual measured stream
   * @param firstX first tapping position
   * @param instrumentY instrument row
   * @param pipeY physical horizontal segment ordinate
   * @param local local source-bound function nodes
   */
  private void exchangerMeasurements(Scene scene, StreamInterface stream, double firstX, double instrumentY,
      double pipeY, Map<String, Node> local) {
    List<PidElement> sensors = new ArrayList<PidElement>();
    for (PidElement element : model.getElements()) {
      Attachment attachment = projection.getAttachments().get(element.getTag());
      if (attachment.getKind() == PidPhysicalProjection.Kind.SENSING
          && stream.getName().equals(attachment.getStreamTag())) {
        sensors.add(element);
      }
    }
    Collections.sort(sensors, new Comparator<PidElement>() {
      @Override
      public int compare(PidElement left, PidElement right) {
        int control = Boolean.compare(left.getConnectedElementIds().isEmpty(),
            right.getConnectedElementIds().isEmpty());
        return control == 0 ? left.getTag().compareTo(right.getTag()) : control;
      }
    });
    for (int index = 0; index < sensors.size(); index++) {
      if (index >= 3) {
        hold("EXCHANGER_INSTRUMENT_DENSITY", sensors.get(index).getTag(), "ISO 10628-1:2014",
            "Provide a controlled detail sheet for more than three measurements on one material segment");
        continue;
      }
      PidElement sensor = sensors.get(index);
      double x = firstX + index * 65;
      Node node = scene.instrument(sensor, x, instrumentY);
      local.put(sensor.getTag(), node);
      Point tap = new Point(x, pipeY);
      scene.sensing(null, tap, node, instrumentY < pipeY ? "b" : "t",
          points(tap, instrumentY < pipeY ? node.bottom() : node.top()), sensor.getTag());
    }
  }

  /**
   * Keeps PFD operating values separate from P&amp;ID line-design declarations.
   *
   * @param scene native scene
   * @param stream actual material stream
   * @param x label centre
   * @param y label row
   * @param overview true for the PFD operating-data profile
   * @param owner actual exchanger tag
   */
  private void exchangerLineLabel(Scene scene, StreamInterface stream, double x, double y, boolean overview,
      String owner) {
    if (overview) {
      scene.centered(x, y, stream.getName(), 3, null);
      scene.centered(x, y + 10, String.format(Locale.ROOT, "%.2f C / %.2f bara / %.3f kg/s", stream.getTemperature("C"),
          stream.getPressure("bara"), stream.getFlowRate("kg/sec")), 3, null);
    } else {
      scene.lineLabel(stream.getName(), x, y, owner);
    }
  }

  /**
   * Assigns one actual instrument to a reciprocal detail sheet exactly once.
   *
   * @param owner primary drawing owner
   * @param element actual bound instrument proposal
   */
  private void addInstrumentDetail(String owner, PidElement element) {
    if (instrumentDetailTags.add(element.getTag())) {
      if (!instrumentDetails.containsKey(owner)) {
        instrumentDetails.put(owner, new ArrayList<PidElement>());
      }
      instrumentDetails.get(owner).add(element);
    }
  }

  /**
   * Draws actual existing stream-segment instrument contexts with reciprocal main-sheet references.
   *
   * @param primary actual primary equipment whose valve ports supply the measured segments
   */
  private void renderInstrumentDetails(ProcessEquipmentInterface primary) {
    List<PidElement> details = instrumentDetails.get(primary.getName());
    if (details == null) {
      return;
    }
    for (int offset = 0; offset < details.size(); offset += 4) {
      Scene scene = scene(primary.getName() + "-INSTRUMENT-DETAILS", "Actual instrumentation details");
      for (int index = offset; index < Math.min(details.size(), offset + 4); index++) {
        PidElement element = details.get(index);
        Attachment attachment = projection.getAttachments().get(element.getTag());
        double y = 145 + (index - offset) * 90;
        if (attachment.getKind() == PidPhysicalProjection.Kind.FUNCTION) {
          scene.text(25, y - 53, element.getTag() + " | " + shorten(textValue(element.toMap().get("description")), 80)
              + " | main " + sheetLabel(primary.getName()), 3, null);
          Node function = scene.instrument(element, 400, y - 10);
          signalEnds(scene, element.getTag(), function);
          continue;
        }
        ProcessEquipmentInterface valve = equipment.get(attachment.getEquipmentTag());
        List<StreamInterface> ports = "INLET".equals(attachment.getDirection()) ? valve.getInletStreams()
            : valve.getOutletStreams();
        StreamInterface stream = ports.get(attachment.getStreamIndex());
        scene.text(25, y - 60, "DETAIL " + element.getTag() + " | actual " + valve.getName() + " "
            + attachment.getDirection() + " port | main " + sheetLabel(primary.getName()), 3, null);
        String source = valve.getName() + " " + attachment.getDirection() + " / PID-"
            + mainSheetNumbers.get(primary.getName());
        String destination = "B/L " + lineNumber(stream);
        if (materialPathTags.contains(valve.getName())) {
          source = upstreamLabel(stream);
          ProcessEquipmentInterface target = soleConsumer(stream);
          if (target != null) {
            destination = sheetLabel(target.getName());
          }
        } else {
          for (ProcessEquipmentInterface consumer : process.getUnitOperations()) {
            if (consumer != valve && !(consumer instanceof StreamInterface)
                && consumer.getInletStreams().contains(stream)) {
              destination = consumer.getName() + " INLET / PID-" + mainSheetNumbers.get(primary.getName());
              break;
            }
          }
        }
        Node from = scene.terminal("detail-source:" + element.getTag(), 110, y, source, false);
        Node to = scene.terminal("detail-target:" + element.getTag(), 700, y, destination, false);
        scene.route(from, "r", to, "l", stream.getName(), false, null);
        Node measurement = scene.instrument(element, 400, y - 35);
        Point tap = new Point(300, y);
        scene.sensing(null, tap, measurement, "b",
            points(tap, new Point(300, y - 18), new Point(400, y - 18), measurement.bottom()), element.getTag());
        scene.lineLabel(stream.getName(), 420, y + 15, valve.getName());
        signalEnds(scene, element.getTag(), measurement);
      }
      scene.text(25, 505,
          "Detail views repeat the identified existing segment only; no additional valve or pipeline is implied.", 3,
          null);
      scene.finish();
    }
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
   * Collects the auxiliary route groups of one unit for the pooled auxiliary sheets.
   *
   * @param unit source unit
   */
  private void collectAuxiliary(ProcessEquipmentInterface unit) {
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
    for (List<PidElement> group : auxiliary) {
      pooledAuxiliary.add(new Object[] {unit, group});
    }
  }

  /**
   * Draws the pooled auxiliary sheets: relief, blowdown, vent, drain and utility connections of all units, four routes
   * per sheet, after every unit and detail sheet so the sheet numbers agree with the controlled index.
   */
  @SuppressWarnings("unchecked")
  private void renderPooledAuxiliary() {
    for (int offset = 0; offset < pooledAuxiliary.size(); offset += AUXILIARY_ROUTES_PER_SHEET) {
      Scene scene = scene(project.getProjectId(), "Relief, blowdown, vent, drain and utility connections");
      for (int index = offset; index < Math.min(offset + AUXILIARY_ROUTES_PER_SHEET, pooledAuxiliary.size());
          index++) {
        ProcessEquipmentInterface unit = (ProcessEquipmentInterface) pooledAuxiliary.get(index)[0];
        List<PidElement> group = (List<PidElement>) pooledAuxiliary.get(index)[1];
        PidElement element = group.get(0);
        Attachment a = projection.getAttachments().get(element.getTag());
        double y = 125 + (index - offset) * 95;
        String sourceNozzle = textValue(a.toMap().get("physicalSourceNozzleTag"));
        String source = !sourceNozzle.isEmpty()
            ? a.getSourceEquipmentTag() + " " + nozzleLabel(a.getSourceEquipmentTag(), sourceNozzle)
            : !a.getSourceStreamTag().isEmpty() ? a.getSourceStreamTag() : a.getSourceEquipmentTag();
        String targetNozzle = textValue(a.toMap().get("physicalTargetNozzleTag"));
        String target = !targetNozzle.isEmpty()
            ? a.getTargetEquipmentTag() + " " + nozzleLabel(a.getTargetEquipmentTag(), targetNozzle)
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
          && child.getEquipmentTag().equals(scene.spec.get("subjectTag")) && incomingInstruments(child).size() <= 1
          && !placed.contains(child.getTag())) {
        Node function = scene.instrument(child, x, y);
        scene.route(measurement, "r", function, "l", "signal:" + sensor.getTag() + ":" + child.getTag(), true, null);
        placed.add(child.getTag());
        placeLocalCommand(scene, child, function, placed);
        String signal = signalId(sensor.getTag(), child.getTag());
        signalSources.add(signal);
        signalTargets.add(signal);
      }
    }
  }

  /**
   * Tests an actual native actuator-command function that is drawn beside its owning local controller.
   *
   * @param function canonical command
   * @return true only for the bounded source-backed controller-to-command relation
   */
  private boolean localCommandOutput(PidElement function) {
    if (!function.getAttributes().containsKey("nativeActuatorRole")) {
      return false;
    }
    for (PidElement candidate : model.getElementsForEquipment(function.getEquipmentTag())) {
      if (candidate.getType() == PidElementType.CONTROLLER
          && candidate.getConnectedElementIds().contains(function.getId())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Draws a real local PIC/TIC-to-native-command link directly, preserving remote signal continuations elsewhere.
   *
   * @param scene current equipment sheet
   * @param controller canonical actual controller
   * @param node controller function symbol
   * @param placed functions already drawn on this sheet
   */
  private void placeLocalCommand(Scene scene, PidElement controller, Node node, Set<String> placed) {
    if (controller.getType() != PidElementType.CONTROLLER) {
      return;
    }
    for (String id : controller.getConnectedElementIds()) {
      PidElement command = byId(id);
      if (command != null && localCommandOutput(command)
          && command.getEquipmentTag().equals(controller.getEquipmentTag()) && !placed.contains(command.getTag())) {
        Node target = scene.instrument(command, node.x + 105, node.y);
        scene.route(node, "r", target, "l", "signal:" + controller.getTag() + ":" + command.getTag(), true, null);
        String signal = signalId(controller.getTag(), command.getTag());
        signalSources.add(signal);
        signalTargets.add(signal);
        placed.add(command.getTag());
      }
    }
  }

  /**
   * Finds separately identified instrument inputs to a shared function.
   *
   * @param function proposed function
   * @return incoming instrument proposals in canonical order
   */
  private List<PidElement> incomingInstruments(PidElement function) {
    List<PidElement> inputs = new ArrayList<PidElement>();
    for (PidElement candidate : model.getElements()) {
      if ((candidate.getType() == PidElementType.MEASUREMENT || candidate.getType() == PidElementType.INDICATOR)
          && candidate.getConnectedElementIds().contains(function.getId())) {
        inputs.add(candidate);
      }
    }
    return inputs;
  }

  /**
   * Draws all locally represented inputs to one voting function without overlapping continuation symbols.
   *
   * <p>
   * Each input ends at a distinct point on the existing safety-function envelope. A function spanning sheets keeps
   * explicit signal continuations instead; no missing source is silently implied by the local fan-in.
   * </p>
   *
   * @param scene current source-backed sheet
   * @param placed already represented proposals
   */
  private void placeSharedFunctions(Scene scene, Set<String> placed) {
    for (PidElement function : model.getElements()) {
      if (placed.contains(function.getTag()) || !function.getEquipmentTag().equals(scene.spec.get("subjectTag"))
          || projection.getAttachments().get(function.getTag()).getKind() != PidPhysicalProjection.Kind.FUNCTION) {
        continue;
      }
      List<PidElement> inputs = incomingInstruments(function);
      if (inputs.size() < 2 || inputs.size() > 3) {
        continue;
      }
      double y = 0.0;
      boolean local = true;
      for (PidElement input : inputs) {
        Node source = scene.proposalNodes.get(input.getTag());
        local &= source != null;
        if (source != null) {
          y += source.y;
        }
      }
      if (!local) {
        continue;
      }
      y /= inputs.size();
      Node target = scene.instrument(function, 700, y, 24);
      String voting = textValue(function.getAttributes().get("simulationVoting"));
      String role = textValue(function.getAttributes().get("safetyFunctionRole"));
      if (!voting.isEmpty() || !role.isEmpty()) {
        scene.text(676, y - 18, (role + " " + voting).trim(), 3, null);
      }
      for (int index = 0; index < inputs.size(); index++) {
        PidElement input = inputs.get(index);
        Node source = scene.proposalNodes.get(input.getTag());
        double offset = inputs.size() == 2 ? (index == 0 ? -6 : 6) : -10.5 + 21.0 * index / (inputs.size() - 1);
        double lane = 590 + 12 * index;
        Point endpoint = new Point(target.left().x, target.y + offset);
        scene.stroke(points(source.right(), new Point(lane, source.y), new Point(lane, endpoint.y), endpoint),
            source.tag, target.tag, "signal:" + input.getTag() + ":" + function.getTag(), true, false);
        String signal = signalId(input.getTag(), function.getTag());
        signalSources.add(signal);
        signalTargets.add(signal);
      }
      placed.add(function.getTag());
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
    for (Map<String, String> signal : projection.getSignals()) {
      String id = signalIds.get(signalKey(signal));
      if (tag.equals(signal.get("sourceTag")) && !signalSources.contains(id)) {
        scene.pendingSignals.add(new PendingSignal(id, tag, node, signal.get("targetTag"), true));
      }
      if (tag.equals(signal.get("targetTag")) && !signalTargets.contains(id)) {
        scene.pendingSignals.add(new PendingSignal(id, tag, node, signal.get("sourceTag"), false));
      }
    }
  }

  /** A signal end awaiting a direct same-sheet connection or a named off-page continuation. */
  private static final class PendingSignal {
    final String id;
    final String tag;
    final Node node;
    final String peerTag;
    final boolean outgoing;

    /**
     * Creates a pending signal end.
     *
     * @param id stable signal identity
     * @param tag tag of the drawn end
     * @param node drawn symbol
     * @param peerTag tag of the other end
     * @param outgoing true when the drawn end is the signal source
     */
    PendingSignal(String id, String tag, Node node, String peerTag, boolean outgoing) {
      this.id = id;
      this.tag = tag;
      this.node = node;
      this.peerTag = peerTag;
      this.outgoing = outgoing;
    }
  }

  /**
   * Resolves the signal ends recorded on one sheet.
   *
   * <p>
   * IEC 62424 and ISA-5.1 practice draws a signal line directly when both ends are on the same sheet; a named
   * continuation is used only when the peer is on another sheet. Signals whose peer is drawn on this sheet are therefore
   * routed directly as dashed signal lines, and every remaining end receives the reciprocal continuation reference.
   * </p>
   *
   * @param scene finished sheet
   */
  private void resolveSignals(Scene scene) {
    Map<Node, List<String>> outgoing = new LinkedHashMap<Node, List<String>>();
    Map<Node, List<String>> incoming = new LinkedHashMap<Node, List<String>>();
    Map<Node, String> tags = new LinkedHashMap<Node, String>();
    for (PendingSignal pending : scene.pendingSignals) {
      boolean sourceDone = signalSources.contains(pending.id);
      boolean targetDone = signalTargets.contains(pending.id);
      if (sourceDone && targetDone) {
        continue;
      }
      Node peer = scene.proposalNodes.get(pending.peerTag);
      if (peer != null && peer != pending.node && !sourceDone && !targetDone) {
        Node source = pending.outgoing ? pending.node : peer;
        Node target = pending.outgoing ? peer : pending.node;
        if (scene.directSignal(source, target, pending.id)) {
          signalSources.add(pending.id);
          signalTargets.add(pending.id);
          continue;
        }
      }
      if (pending.outgoing && !sourceDone) {
        outgoing.computeIfAbsent(pending.node, key -> new ArrayList<String>())
            .add(signalReference(pending.id, pending.peerTag));
        signalSources.add(pending.id);
        tags.put(pending.node, pending.tag);
      } else if (!pending.outgoing && !targetDone) {
        incoming.computeIfAbsent(pending.node, key -> new ArrayList<String>())
            .add(signalReference(pending.id, pending.peerTag));
        signalTargets.add(pending.id);
        tags.put(pending.node, pending.tag);
      }
    }
    scene.pendingSignals.clear();
    for (Map.Entry<Node, List<String>> entry : outgoing.entrySet()) {
      Node node = entry.getKey();
      String tag = tags.get(node);
      boolean functionDetail = node.family == Family.SAFETY_FUNCTION && instrumentDetailSheetNumbers.containsKey(tag);
      String label = join(entry.getValue());
      double continuationWidth = Math.min(115, Math.max(30, displayLength(label) * 1.6 + 10));
      double x = functionDetail ? 700
          : node.x + node.width / 2 + 12 + 3 * scene.directSignalLanes + continuationWidth / 2;
      for (int shift = 0; shift < 20 && !functionDetail
          && scene.routeInBox(new double[] {x - continuationWidth / 2 - 5, node.y - 8.5, continuationWidth + 6, 17});
          shift++) {
        x += 6;
      }
      Node terminal = scene.terminal("signal-from:" + tag, x, node.y, label, true);
      scene.strokeBroken(points(node.right(), terminal.left()), node.tag, terminal.tag, "signal-from:" + tag);
    }
    for (Map.Entry<Node, List<String>> entry : incoming.entrySet()) {
      Node node = entry.getKey();
      String tag = tags.get(node);
      boolean functionDetail = node.family == Family.SAFETY_FUNCTION && instrumentDetailSheetNumbers.containsKey(tag);
      boolean valve = isValveFamily(node.family);
      double x = functionDetail ? 110 : valve ? node.x : node.x - 75;
      String label = join(entry.getValue());
      double width = Math.min(115, Math.max(30, displayLength(label) * 1.6 + 10));
      double y = valve ? node.y - 51 : node.y;
      for (int shift = 0; shift < 20 && !functionDetail
          && scene.routeInBox(new double[] {x - width / 2 - 1, y - 8.5, width + 2, valve ? 22 : 17}); shift++) {
        if (valve) {
          y -= 6;
        } else {
          x -= 6;
        }
      }
      Node terminal = scene.terminal("signal-to:" + tag, x, y, label, true);
      scene.strokeBroken(valve ? points(terminal.bottom(), node.top()) : points(terminal.right(), node.left()),
          terminal.tag, node.tag, "signal-to:" + tag);
    }
  }

  /**
   * Adds the exact peer sheet to a named signal continuation without replacing its stable signal identity.
   *
   * @param id stable native signal identity
   * @param peerTag exact canonical peer function/device
   * @return readable reciprocal sheet reference
   */
  private String signalReference(String id, String peerTag) {
    String sheet = instrumentDetailSheetNumbers.get(peerTag);
    PidElement peer = proposals.get(peerTag);
    if (sheet == null && materialPathTags.contains(peerTag)) {
      sheet = mainSheetNumbers.get(peerTag);
    }
    if (sheet == null && peer != null && !materialPathTags.contains(peer.getEquipmentTag())) {
      sheet = mainSheetNumbers.get(peer.getEquipmentTag());
    }
    return id + "/PID-" + SHEET_REF_START + peerTag + "|" + (sheet == null ? "" : sheet) + "@@";
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
            : a.getStreamIndex() > 0 && unit instanceof Separator ? liquidOutletPort(unit, body, a.getStreamIndex())
                : body.right();
      } else {
        point = inlet ? owner.left() : owner.right();
      }
      ProfessionalPidSymbolLibrary.draw(scene.page, Family.NOZZLE, point.x, point.y, 3, 4, element.getTag());
      scene.text(inlet ? point.x - 20 : point.x + 6, inlet ? point.y - 13 : point.y + (point.y < body.y ? -6 : 8),
          nozzleLabel(unit.getName(), element.getTag()), 2.5, null);
      represented.add(element.getTag());
    }
  }

  /**
   * Draws the actual valve family and source-backed simulation fail position or an explicit unresolved action.
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
            : element.getType() == PidElementType.CONTROL_VALVE ? Family.CONTROL_VALVE
                : element.getType() == PidElementType.SHUTDOWN_VALVE
                    || element.getType() == PidElementType.BLOWDOWN_VALVE ? Family.SHUTDOWN_VALVE
                        : Family.ISOLATION_VALVE;
    String letters = isaLetters(element.getTag());
    if ((family == Family.SHUTDOWN_VALVE || family == Family.ISOLATION_VALVE)
        && ProfessionalPidSymbolLibrary.controlFunctionTag(element.getTag())) {
      family = Family.CONTROL_VALVE; // the declared ISA function governs the symbol
    }
    if (family == Family.ISOLATION_VALVE && letters != null && (letters.equals("XV") || letters.equals("SDV")
        || letters.equals("ESDV") || letters.equals("PSDV") || letters.equals("BDV") || letters.equals("HIPPS"))) {
      family = Family.SHUTDOWN_VALVE; // ISA-5.1 automated on/off valve: drawn with its actuator
    }
    if (family == Family.CONTROL_VALVE
        && "PNEUMATIC_DIAPHRAGM_PROPOSAL".equals(element.getAttributes().get("actuatorType"))) {
      family = Family.PNEUMATIC_OPERATED_VALVE;
    }
    Node node = scene.node(element.getTag(), family, x, y, 16, 10, element.getTag());
    scene.proposalNodes.put(element.getTag(), node);
    drawnSheets.putIfAbsent(element.getTag(), scene.number);
    // Fail action applies to actuated valves only; a manual valve carries its normal position (NO/NC).
    String valveNote = family == Family.CHECK_VALVE ? "FLOW >" : family == Family.RELIEF_VALVE ? "SET:HOLD"
        : family == Family.ISOLATION_VALVE
            ? (element.getType() == PidElementType.DRAIN || element.getType() == PidElementType.VENT ? "NC"
                : "NO/NC:HOLD")
            : "FA:HOLD";
    Attachment actualAttachment = projection.getAttachments().get(element.getTag());
    ProcessEquipmentInterface boundValve = actualAttachment == null ? null
        : equipment.get(actualAttachment.getBoundSimulationName());
    if (actualAttachment != null && actualAttachment.getKind() == PidPhysicalProjection.Kind.EXISTING_EQUIPMENT
        && boundValve != null && boundValve.getClass() == neqsim.process.equipment.valve.ESDValve.class) {
      double actualFail = ((neqsim.process.equipment.valve.ESDValve) boundValve).getFailSafePosition();
      Object canonicalFail = element.getAttributes().get("simulationFailSafePosition");
      Object controlFail = element.getAttributes().get("nativeControlConfiguration") instanceof Map
          ? ((Map<?, ?>) element.getAttributes().get("nativeControlConfiguration")).get("failSafePositionPercent")
          : null;
      if (canonicalFail instanceof Number && Double.compare(((Number) canonicalFail).doubleValue(), actualFail) != 0
          || controlFail instanceof Number && Double.compare(((Number) controlFail).doubleValue(), actualFail) != 0) {
        geometryError("PID_ACTUATOR_FAIL_ACTION_STALE", "Canonical fail action differs from the actual bound ESD valve",
            element.getTag());
      } else {
        valveNote = actualFail == 0.0 ? "SIM FC"
            : actualFail == 100.0 ? "SIM FO" : String.format(Locale.ROOT, "SIM FAIL:%.1f%%", actualFail);
      }
    }
    Object reliefSettings = element.getAttributes().get("simulationPressureReliefConfiguration");
    if (family == Family.RELIEF_VALVE && reliefSettings instanceof Map) {
      Map<?, ?> settings = (Map<?, ?>) reliefSettings;
      if (settings.get("setPressureBar") instanceof Number && "bara".equals(settings.get("pressureUnit"))) {
        valveNote = String.format(Locale.ROOT, "SIM SET:%.2f bara",
            ((Number) settings.get("setPressureBar")).doubleValue());
      }
    }
    Object study = element.getAttributes().get(neqsim.process.engineering.pid.PidSafetyStudies.ANNOTATION);
    if (family == Family.RELIEF_VALVE && study != null) {
      valveNote = String.valueOf(study);
    }
    scene.text(family == Family.RELIEF_VALVE ? x + 4 : x, family == Family.RELIEF_VALVE ? y + 16 : y + 21, valveNote,
        2.5, null);
    if (family != Family.RELIEF_VALVE && study != null) {
      scene.text(x, y + 25, String.valueOf(study), 2.5, null);
    }
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
          || ("INLET".equals(direction) && a.getKind() == PidPhysicalProjection.Kind.EXISTING_EQUIPMENT))
          && streamTag.equals(element.getLineTag()) && direction.equals(a.getDirection())) {
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
   * Plans source-backed material sheets for feed valve trains and gas outlets with a passive relief branch. The
   * established compressor recycle and separator relief templates retain their own junctions.
   *
   * @return ordered pages of in-series valves or a junction and its relief valve
   */
  private List<List<ProcessEquipmentInterface>> planReliefPaths() {
    List<List<ProcessEquipmentInterface>> pages = new ArrayList<List<ProcessEquipmentInterface>>();
    Map<StreamInterface, List<ProcessEquipmentInterface>> consumers = new IdentityHashMap<StreamInterface, List<ProcessEquipmentInterface>>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface)) {
        for (StreamInterface inlet : unit.getInletStreams()) {
          consumers.computeIfAbsent(inlet, key -> new ArrayList<ProcessEquipmentInterface>()).add(unit);
        }
      }
    }
    for (ProcessEquipmentInterface tee : process.getUnitOperations()) {
      if (tee.getClass() != Splitter.class || tee.getInletStreams().size() != 1 || tee.getOutletStreams().size() != 2
          || supportedReliefTee(tee)) {
        continue;
      }
      StreamInterface inlet = tee.getInletStreams().get(0);
      List<ProcessEquipmentInterface> before = new ArrayList<ProcessEquipmentInterface>();
      ProcessEquipmentInterface source = producers.get(inlet);
      while (isPathValve(source) && !before.contains(source) && consumers.get(inlet).size() == 1) {
        before.add(source);
        inlet = source.getInletStreams().get(0);
        source = producers.get(inlet);
      }
      if (consumers.get(inlet).size() != 1 || !(source instanceof StreamInterface
          || before.isEmpty() && source instanceof Separator && ((Separator) source).getGasOutStream() == inlet)) {
        continue;
      }
      StreamInterface first = tee.getOutletStreams().get(0);
      StreamInterface second = tee.getOutletStreams().get(1);
      ProcessEquipmentInterface firstValve = soleValveConsumer(first);
      ProcessEquipmentInterface secondValve = soleValveConsumer(second);
      boolean firstRelief = firstValve != null
          && ProfessionalPidSymbolLibrary.family(firstValve) == Family.RELIEF_VALVE;
      boolean secondRelief = secondValve != null
          && ProfessionalPidSymbolLibrary.family(secondValve) == Family.RELIEF_VALVE;
      if (firstRelief == secondRelief) {
        continue;
      }
      ProcessEquipmentInterface relief = firstRelief ? firstValve : secondValve;
      if (relief.getOutletStreams().size() != 1 || consumers.containsKey(relief.getOutletStreams().get(0))) {
        continue;
      }
      StreamInterface normal = firstRelief ? second : first;
      List<ProcessEquipmentInterface> after = new ArrayList<ProcessEquipmentInterface>();
      ProcessEquipmentInterface receiver = soleConsumer(normal);
      while (isPathValve(receiver) && !after.contains(receiver)) {
        after.add(receiver);
        normal = receiver.getOutletStreams().get(0);
        receiver = soleConsumer(normal);
      }
      // A missing unique consumer can also be an ambiguous fan-out. Only a real boundary or vessel is supported.
      if (consumers.containsKey(normal) && !(receiver instanceof Separator)
          || receiver != null && !inline(receiver.getName(), normal.getName(), "INLET").isEmpty()) {
        continue;
      }
      Collections.reverse(before);
      addValvePages(pages, before);
      pages.add(Arrays.asList(tee, relief));
      addValvePages(pages, after);
    }
    return pages;
  }

  /** Returns whether a source unit is a supported single-circuit series valve. */
  private boolean isPathValve(ProcessEquipmentInterface unit) {
    if (!(unit instanceof ThrottlingValve) || unit.getInletStreams().size() != 1
        || unit.getOutletStreams().size() != 1) {
      return false;
    }
    Family family = ProfessionalPidSymbolLibrary.family(unit);
    return family != Family.RELIEF_VALVE && family != Family.UNMAPPED && !isActualAntiSurgeValve(unit);
  }

  /** Places up to three consecutive physical valves on each A1 sheet. */
  private static void addValvePages(List<List<ProcessEquipmentInterface>> pages,
      List<ProcessEquipmentInterface> valves) {
    for (int start = 0; start < valves.size(); start += 3) {
      pages.add(valves.subList(start, Math.min(start + 3, valves.size())));
    }
  }

  /** Renders actual port identities on a valve train sheet or on the relief takeoff sheet. */
  private void renderReliefPath(List<ProcessEquipmentInterface> path) {
    ProcessEquipmentInterface first = path.get(0);
    boolean branch = first instanceof Splitter;
    Scene scene = scene(first.getName(), branch ? "Relief takeoff" : "Series valves");
    StreamInterface stream = first.getInletStreams().get(0);
    Node last = scene.terminal("in:" + stream.getName(), 75, 280, upstreamLabel(stream), false);
    if (branch) {
      Node tee = scene.node(first.getName(), Family.PIPE_TEE, 365, 280, 6, 6, "");
      scene.centered(365, 297, first.getName(), 3.5, null);
      scene.route(last, "r", tee, "l", stream.getName(), false, null);
      scene.lineLabel(stream.getName(), 230, 267, first.getName());
      ProcessEquipmentInterface relief = path.get(1);
      StreamInterface reliefIn = relief.getInletStreams().get(0);
      Node symbol = materialValve(scene, relief, 520, 165);
      scene.route(tee, "t", symbol, "l", reliefIn.getName(), false,
          points(tee.top(), new Point(tee.x, symbol.y), symbol.left()));
      scene.lineLabel(reliefIn.getName(), 435, 150, first.getName());
      finishMaterialLine(scene, symbol, relief.getOutletStreams().get(0), first.getName());
      stream = first.getOutletStreams().get(0) == reliefIn ? first.getOutletStreams().get(1)
          : first.getOutletStreams().get(0);
      last = tee;
    } else {
      for (int i = 0; i < path.size(); i++) {
        ProcessEquipmentInterface member = path.get(i);
        double x = 75 + (i + 1) * 690.0 / (path.size() + 1);
        Node symbol = materialValve(scene, member, x, 280);
        scene.route(last, "r", symbol, "l", stream.getName(), false, null);
        scene.lineLabel(stream.getName(), (last.right().x + symbol.left().x) / 2, 258, first.getName());
        stream = member.getOutletStreams().get(0);
        last = symbol;
      }
    }
    finishMaterialLine(scene, last, stream, first.getName());
    for (int i = 0; i < path.size(); i++) {
      List<PidElement> details = instrumentDetails.get(path.get(i).getName());
      if (details != null) {
        Set<String> refs = new LinkedHashSet<String>();
        for (PidElement detail : details) {
          refs.add("PID-" + instrumentDetailSheetNumbers.get(detail.getTag()));
        }
        scene.text(25, 474 + 12 * i, path.get(i).getName() + " instrumentation details / " + String.join(", ", refs), 3,
            null);
      }
    }
    scene.finish();
  }

  /** Draws only the actual source family unless the physical projection confirms the binding. */
  private Node materialValve(Scene scene, ProcessEquipmentInterface valve, double x, double y) {
    PidElement element = proposals.get(valve.getName());
    Attachment attachment = projection.getAttachments().get(valve.getName());
    if (element != null && attachment.getKind() == PidPhysicalProjection.Kind.EXISTING_EQUIPMENT
        && valve.getName().equals(attachment.getBoundSimulationName())) {
      Node symbol = proposalValve(scene, element, x, y);
      signalEnds(scene, element.getTag(), symbol);
      return symbol;
    }
    boolean previouslyRepresented = represented.contains(valve.getName());
    Node symbol = scene.node(valve.getName(), ProfessionalPidSymbolLibrary.family(valve), x, y, 16, 10,
        valve.getName());
    if (!previouslyRepresented) {
      represented.remove(valve.getName());
    }
    return symbol;
  }

  /** Labels the line leaving the last source device with its immediate consumer or battery limit. */
  private void finishMaterialLine(Scene scene, Node last, StreamInterface stream, String owner) {
    ProcessEquipmentInterface consumer = soleConsumer(stream);
    String destination = consumer == null ? "B/L " + lineNumber(stream) : sheetLabel(consumer.getName());
    Node target = scene.terminal("out:" + stream.getName(), 765, last.y, destination, false);
    scene.route(last, "r", target, "l", stream.getName(), false, null);
    scene.lineLabel(stream.getName(), (last.right().x + target.left().x) / 2, last.y - 22, owner);
  }

  /**
   * Draws one actual passive relief tee and its normal/relief branches without adding hardware or signals.
   * 
   * @param scene native controlled scene
   * @param owner actual protected unit
   * @param body actual protected symbol
   * @param stream actual gas stream to tee
   * @param tee actual supported two-outlet tee
   * @param valveNodes actual valve symbols used by later nozzle and signal placement
   */
  private void renderPassiveReliefBranch(Scene scene, ProcessEquipmentInterface owner, Node body,
      StreamInterface stream, ProcessEquipmentInterface tee, Map<String, Node> valveNodes) {
    Node junction = scene.node(tee.getName(), Family.PIPE_TEE, 470, 140, 6, 6, "");
    scene.centered(450, 157, tee.getName(), 3, null);
    scene.route(body, "t", junction, "l", stream.getName(), false, sourceRoute(body, "t", junction.left(), 140));
    scene.lineLabel(stream.getName(), 420, 116, owner.getName());
    for (StreamInterface branch : tee.getOutletStreams()) {
      ProcessEquipmentInterface valve = soleValveConsumer(branch);
      boolean relief = valve.getClass() == SafetyReliefValve.class || isActualAntiSurgeValve(valve);
      // Reserve the full signal continuation width above the inline valve between the two branch risers.
      double valveX = relief ? 620 : 530;
      Node symbol = proposals.containsKey(valve.getName())
          ? proposalValve(scene, proposals.get(valve.getName()), valveX, relief ? 100 : 140)
          : scene.node(valve.getName(), ProfessionalPidSymbolLibrary.family(valve), valveX, relief ? 100 : 140, 16, 10,
              valve.getName());
      valveNodes.put(valve.getName(), symbol);
      scene.route(junction, relief ? "t" : "r", symbol, "l", branch.getName(), false,
          relief
              ? points(junction.top(), new Point(junction.x, 75), new Point(symbol.left().x - 14, 75),
                  new Point(symbol.left().x - 14, symbol.y), symbol.left())
              : null);
      scene.lineLabel(branch.getName(), relief ? 565 : 465, relief ? 112 : 172, owner.getName());
      StreamInterface discharge = valve.getOutletStreams().get(0);
      Node terminal = scene.terminal("out:" + discharge.getName(), relief ? 750 : 790, relief ? 100 : 140,
          downstreamLabel(discharge), false);
      scene.route(symbol, "r", terminal, "l", discharge.getName(), false, null);
      scene.lineLabel(discharge.getName(), relief ? 680 : 660, relief ? 90 : 170, owner.getName());
      if (relief && proposals.containsKey(valve.getName())) {
        Object values = proposals.get(valve.getName()).getAttributes().get("simulationPressureReliefConfiguration");
        if (values instanceof Map) {
          Map<?, ?> settings = (Map<?, ?>) values;
          scene.text(25, 508,
              "Passive PSV simulation basis | set " + settings.get("setPressureBar") + " bara | law "
                  + settings.get("openingLaw") + " | MODULATING ramp ref (unused by SNAP) "
                  + settings.get("fullLiftPressureBar") + " bara | reseat " + settings.get("reseatPressureBar")
                  + " bara | rated US Cv " + settings.get("ratedCv") + " (synthetic); certified capacity: HOLD",
              2.7, null);
        }
      }
    }
  }

  /**
   * Tests a real anti-surge target by its executing controller and actual return junction.
   *
   * @param valve actual candidate
   * @return whether this is an actual controller-bound recycle final element
   */
  private boolean isActualAntiSurgeValve(ProcessEquipmentInterface valve) {
    if (!(valve instanceof ThrottlingValve)
        || !(((ThrottlingValve) valve).getController() instanceof AntiSurgeController)) {
      return false;
    }
    AntiSurgeController controller = (AntiSurgeController) ((ThrottlingValve) valve).getController();
    return controller.getRecycleValve() == valve && controller.getCompressor() != null
        && equipment.get(controller.getCompressor().getName()) == controller.getCompressor()
        && valve.getOutletStreams().size() == 1 && soleConsumer(valve.getOutletStreams().get(0)) instanceof Mixer;
  }

  /**
   * Resolves one unique non-stream consumer by actual stream identity.
   *
   * @param stream actual line
   * @return unique actual consumer, or null
   */
  private ProcessEquipmentInterface soleConsumer(StreamInterface stream) {
    ProcessEquipmentInterface result = null;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && unit.getInletStreams().contains(stream)) {
        if (result != null) {
          return null;
        }
        result = unit;
      }
    }
    if (result instanceof neqsim.process.equipment.util.Recycle
        && ((neqsim.process.equipment.util.Recycle) result).getOutletStream() != null
        && ((neqsim.process.equipment.util.Recycle) result).getOutletStream() != stream) {
      return soleConsumer(((neqsim.process.equipment.util.Recycle) result).getOutletStream());
    }
    return result;
  }

  /**
   * Returns true for numerical solver utilities that are not plant items and are never drawn.
   *
   * @param unit candidate
   * @return true for recycle, calculator and adjuster utilities
   */
  static boolean numericalHelper(ProcessEquipmentInterface unit) {
    return unit instanceof neqsim.process.equipment.util.Recycle
        || unit instanceof neqsim.process.equipment.util.Calculator
        || unit instanceof neqsim.process.equipment.util.Adjuster;
  }

  /**
   * Recognizes a header merge: a mixer with two or three inlets whose sole consumer is drawn on its own sheet. The
   * merge is drawn as a tee on the consumer's inlet line, as on a process P&amp;ID, rather than as equipment.
   *
   * @param unit candidate
   * @return true for a supported header merge
   */
  private boolean supportedHeaderMixer(ProcessEquipmentInterface unit) {
    if (unit == null || unit.getClass() != Mixer.class || unit.getInletStreams().size() < 2
        || unit.getInletStreams().size() > 3 || unit.getOutletStreams().size() != 1 || supportedSuctionMixer(unit)) {
      return false;
    }
    ProcessEquipmentInterface consumer = soleConsumer(unit.getOutletStreams().get(0));
    if (consumer == null || consumer instanceof ThrottlingValve || consumer.getClass() == Mixer.class
        || numericalHelper(consumer)) {
      return false;
    }
    Family family = ProfessionalPidSymbolLibrary.family(consumer);
    return family != Family.UNMAPPED && family != Family.PIPE_TEE;
  }


  /**
   * Draws a header merge tee ahead of the receiving unit's inlet devices and returns the tee as the line source.
   *
   * @param scene receiving unit sheet
   * @param mixer actual merge
   * @param y inlet line ordinate
   * @return tee node from which the inlet line continues
   */
  private Node headerTee(Scene scene, ProcessEquipmentInterface mixer, double y) {
    Node junction = scene.node(mixer.getName(), Family.PIPE_TEE, 115, y, 6, 6, "");
    scene.centered(115, y - 8, mixer.getName(), 3, null);
    List<StreamInterface> inlets = mixer.getInletStreams();
    for (int index = 0; index < inlets.size(); index++) {
      StreamInterface inlet = inlets.get(index);
      double ty = index == 0 ? y : index == 1 ? y + 45 : y - 45;
      Node continuation = scene.terminal("in:" + inlet.getName(), 60, ty, upstreamLabel(inlet), false);
      if (index == 0) {
        scene.route(continuation, "r", junction, "l", inlet.getName(), false, null);
      } else {
        scene.route(continuation, "r", junction, index == 1 ? "b" : "t", inlet.getName(), false,
            points(continuation.right(), new Point(junction.x, ty), index == 1 ? junction.bottom() : junction.top()));
      }
      scene.lineLabel(inlet.getName(), 60, ty + 13, scene.spec.get("subjectTag").toString());
    }
    representedEquipment.add(mixer.getName());
    return junction;
  }

  /**
   * Draws a header merge as a tee on the receiving unit's inlet line, with a continuation for each actual feed.
   *
   * @param scene receiving unit sheet
   * @param body receiving unit symbol
   * @param mixer actual merge
   * @param outlet merge outlet entering the unit
   * @param y inlet line ordinate
   */
  private void renderHeaderMixer(Scene scene, Node body, ProcessEquipmentInterface mixer, StreamInterface outlet,
      double y) {
    Node junction = scene.node(mixer.getName(), Family.PIPE_TEE, 245, y, 6, 6, "");
    scene.centered(245, y - 8, mixer.getName(), 3, null);
    scene.route(junction, "r", body, "l", outlet.getName(), false, null);
    List<StreamInterface> inlets = mixer.getInletStreams();
    for (int index = 0; index < inlets.size(); index++) {
      StreamInterface inlet = inlets.get(index);
      double ty = index == 0 ? y : index == 1 ? y + 45 : y - 45;
      Node continuation = scene.terminal("in:" + inlet.getName(), 60, ty, upstreamLabel(inlet), false);
      if (index == 0) {
        scene.route(continuation, "r", junction, "l", inlet.getName(), false, null);
        scene.lineLabel(inlet.getName(), 160, y + 8, body.tag);
      } else {
        scene.route(continuation, "r", junction, index == 1 ? "b" : "t", inlet.getName(), false,
            points(continuation.right(), new Point(junction.x, ty), index == 1 ? junction.bottom() : junction.top()));
        scene.lineLabel(inlet.getName(), 160, ty + 8, body.tag);
      }
    }
    representedEquipment.add(mixer.getName());
  }

  /**
   * Recognizes a native two-inlet suction merge with one actual anti-surge recycle input.
   *
   * @param unit actual candidate
   * @return true only for the bounded supported source topology
   */
  private boolean supportedSuctionMixer(ProcessEquipmentInterface unit) {
    if (unit == null || unit.getClass() != Mixer.class || unit.getInletStreams().size() != 2
        || unit.getOutletStreams().size() != 1
        || !(soleConsumer(unit.getOutletStreams().get(0)) instanceof Separator)) {
      return false;
    }
    int recycle = 0;
    for (StreamInterface inlet : unit.getInletStreams()) {
      if (isActualAntiSurgeValve(producers.get(inlet))) {
        recycle++;
      }
    }
    return recycle == 1;
  }

  /**
   * Draws the actual suction tee, inlet shutdown device and reciprocal recycle continuation.
   *
   * @param scene native scene
   * @param body receiving vessel
   * @param mixer actual merge junction
   * @param outlet actual junction outlet
   * @param valveNodes physical valve symbol registry
   */
  private void renderSuctionMixer(Scene scene, Node body, ProcessEquipmentInterface mixer, StreamInterface outlet,
      Map<String, Node> valveNodes) {
    Node junction = scene.node(mixer.getName(), Family.PIPE_TEE, 245, 280, 6, 6, "");
    scene.centered(190, 315, mixer.getName(), 3, null);
    scene.route(junction, "r", body, "l", outlet.getName(), false, null);
    for (StreamInterface inlet : mixer.getInletStreams()) {
      ProcessEquipmentInterface producer = producers.get(inlet);
      if (isActualAntiSurgeValve(producer)) {
        Node continuation = scene.terminal("in:" + inlet.getName(), 60, 350,
            sheetLabel(proposals.get(producer.getName()).getEquipmentTag()) + " / " + producer.getName(), false);
        scene.route(continuation, "r", junction, "b", inlet.getName(), false,
            points(continuation.right(), new Point(junction.x, 350), junction.bottom()));
        scene.lineLabel(inlet.getName(), 160, 366, body.tag);
      } else if (producer instanceof ThrottlingValve && producer.getInletStreams().size() == 1) {
        Node source = scene.terminal("in:" + producer.getInletStreams().get(0).getName(), 60, 280,
            upstreamLabel(producer.getInletStreams().get(0)), false);
        Node valve = proposals.containsKey(producer.getName())
            ? proposalValve(scene, proposals.get(producer.getName()), 160, 280)
            : scene.node(producer.getName(), ProfessionalPidSymbolLibrary.family(producer), 160, 280, 16, 10,
                producer.getName());
        valveNodes.put(producer.getName(), valve);
        scene.route(source, "r", valve, "l", producer.getInletStreams().get(0).getName(), false, null);
        scene.route(valve, "r", junction, "l", inlet.getName(), false, null);
      } else {
        hold("SUCTION_MERGE_UPSTREAM_DEVICE", mixer.getName(), "ISO 10628-1:2014",
            "Actual suction merge feed requires a supported explicit inlet device");
      }
    }
  }

  /**
   * Resolves one exact actual two-way tee on a separator gas outlet.
   * 
   * @param stream actual source stream, or null
   * @return supported tee, or null
   */
  private ProcessEquipmentInterface reliefTee(StreamInterface stream) {
    if (stream == null) {
      return null;
    }
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (supportedReliefTee(unit) && unit.getInletStreams().get(0) == stream) {
        return unit;
      }
    }
    return null;
  }

  /**
   * Recognizes a bounded actual tee with one passive relief valve and one other native valve.
   * 
   * @param unit actual candidate
   * @return true only for source-backed supported topology
   */
  private boolean supportedReliefTee(ProcessEquipmentInterface unit) {
    if (unit.getClass() != Splitter.class || unit.getInletStreams().size() != 1
        || unit.getOutletStreams().size() != 2) {
      return false;
    }
    ProcessEquipmentInterface producer = producers.get(unit.getInletStreams().get(0));
    if (!(producer instanceof Separator) || ((Separator) producer).getGasOutStream() != unit.getInletStreams().get(0)) {
      return false;
    }
    int relief = 0;
    for (StreamInterface outlet : unit.getOutletStreams()) {
      ProcessEquipmentInterface valve = soleValveConsumer(outlet);
      if (!(valve instanceof ThrottlingValve)) {
        return false;
      }
      if (valve.getClass() == SafetyReliefValve.class) {
        relief++;
      }
    }
    if (relief == 1) {
      return true;
    }
    int recycle = 0;
    for (StreamInterface outlet : unit.getOutletStreams()) {
      if (isActualAntiSurgeValve(soleValveConsumer(outlet))) {
        recycle++;
      }
    }
    return relief == 0 && recycle == 1;
  }

  /**
   * Resolves unique actual downstream valve ownership by stream object identity.
   * 
   * @param stream actual tee outlet
   * @return exact valve or null for missing/ambiguous consumers
   */
  private ProcessEquipmentInterface soleValveConsumer(StreamInterface stream) {
    ProcessEquipmentInterface found = null;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof StreamInterface) && unit.getInletStreams().contains(stream)) {
        if (found != null || !(unit instanceof ThrottlingValve)) {
          return null;
        }
        found = unit;
      }
    }
    return found;
  }

  /**
   * Resolves a real branch valve through a supported tee, without inferring tag-based hardware.
   * 
   * @param owner actual protected unit
   * @param candidate actual valve
   * @return true for an exact outlet branch
   */
  private boolean isReliefTeeChild(ProcessEquipmentInterface owner, ProcessEquipmentInterface candidate) {
    for (StreamInterface stream : owner.getOutletStreams()) {
      ProcessEquipmentInterface tee = reliefTee(stream);
      if (tee != null && tee.getOutletStreams().contains(candidate.getInletStreams().get(0))) {
        return true;
      }
    }
    return false;
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
          && (unit.getOutletStreams().contains(candidate.getInletStreams().get(0)) || isReliefTeeChild(unit, candidate))
          && !assignedToOtherInletSheet(candidate.getName(), unit.getName())) {
        result.add(candidate);
      }
    }
    return result;
  }

  /**
   * Keeps a bound inter-unit valve on its explicitly declared receiving-equipment sheet.
   *
   * @param actualName actual valve identity
   * @param sourceOwner current upstream source unit
   * @return whether another inlet sheet owns this exact bound valve symbol
   */
  private boolean assignedToOtherInletSheet(String actualName, String sourceOwner) {
    for (PidElement element : model.getElements()) {
      Attachment attachment = projection.getAttachments().get(element.getTag());
      if (attachment.getKind() == PidPhysicalProjection.Kind.EXISTING_EQUIPMENT
          && actualName.equals(attachment.getBoundSimulationName()) && "INLET".equals(attachment.getDirection())
          && !sourceOwner.equals(element.getEquipmentTag())) {
        return true;
      }
    }
    return false;
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
        if (numericalHelper(unit) && unit instanceof neqsim.process.equipment.util.Recycle
            && ((neqsim.process.equipment.util.Recycle) unit).getOutletStream() != null) {
          ProcessEquipmentInterface physical = soleConsumer(((neqsim.process.equipment.util.Recycle) unit)
              .getOutletStream());
          return physical == null ? "B/L " + lineNumber(stream) : sheetLabel(physical.getName());
        }
        if (unit instanceof ThrottlingValve && !materialPathTags.contains(unit.getName())) {
          for (StreamInterface outlet : unit.getOutletStreams()) {
            for (ProcessEquipmentInterface target : process.getUnitOperations()) {
              if (target != unit && target.getInletStreams().contains(outlet)) {
                return sheetLabel(target.getName());
              }
            }
          }
          return "B/L " + lineNumber(unit.getOutletStreams().get(0));
        }
        return sheetLabel(unit.getName());
      }
    }
    return "B/L " + lineNumber(stream);
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

  /** Separates the oil and water outlets on the straight shell of a horizontal three-phase vessel. */
  private static Point liquidOutletPort(ProcessEquipmentInterface unit, Node body, int outlet) {
    Point bottom = body.bottom();
    if (body.family == Family.HORIZONTAL_VESSEL && unit.getOutletStreams().size() == 3) {
      return new Point(bottom.x + (outlet == 1 ? 1 : -1) * body.width * .16, bottom.y);
    }
    return bottom;
  }

  /** Routes each phase from its own physical drawing port. */
  private static List<Point> outletRoute(ProcessEquipmentInterface unit, Node body, String side, Point end, double y,
      int outlet) {
    if ("b".equals(side)) {
      Point port = liquidOutletPort(unit, body, outlet);
      return points(port, new Point(port.x, y), end);
    }
    return sourceRoute(body, side, end, y);
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
    scene.spec.put("subjectTag", subject);
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
      return new Point(x - width / 2 - (boxedFunction(family) ? 2 : 0), y);
    }

    /** @return right process port */
    Point right() {
      return new Point(x + width / 2 + (boxedFunction(family) ? 2 : 0), y);
    }

    /** @return upper sensing or actuator port */
    Point top() {
      return new Point(x, y - (actuatedValve(family) ? 16 : family == Family.RELIEF_VALVE ? 18 : height / 2));
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


  /**
   * Returns the drawing line number for a source stream name, resolved against the streams of its owner.
   *
   * <p>
   * A stream already named as a controlled line tag (for example {@code 20-PG-001} or {@code 20-FEED-001}) keeps its
   * name. Streams with internal simulation names ({@code gasOutStream}, {@code 23-KA-001 out stream}) receive a
   * NORSOK-style number {@code <area>-<fluid code>-<sequence>}: the area is the numeric prefix of the producing (or
   * receiving) equipment tag, the fluid code is {@code PG} for vapour-dominated and {@code PL} for liquid-dominated
   * process lines, and the sequence is unique per area and fluid code. Every generated number is recorded in the
   * drawing content report with its source stream and is review-required, so line-list ownership stays with the project.
   * </p>
   *
   * @param streamName source stream name; names need not be unique in a process
   * @param ownerTag equipment the label is drawn for, used to pick the right stream
   * @return drawing line number
   */
  String lineNumber(String streamName, String ownerTag) {
    StreamInterface stream = null;
    ProcessEquipmentInterface owner = ownerTag == null ? null : equipment.get(ownerTag);
    if (owner != null) {
      List<StreamInterface> candidates = new ArrayList<StreamInterface>(owner.getInletStreams());
      candidates.addAll(owner.getOutletStreams());
      for (StreamInterface candidate : candidates) {
        if (candidate != null && streamName.equals(candidate.getName())) {
          stream = candidate;
          break;
        }
      }
    }
    if (stream == null) {
      for (StreamInterface candidate : producers.keySet()) {
        if (streamName.equals(candidate.getName())) {
          stream = candidate;
          break;
        }
      }
    }
    if (stream == null) {
      String known = nameOnlyLineNumbers.get(streamName);
      if (known == null) {
        known = lineNumberFor(streamName, null);
        nameOnlyLineNumbers.put(streamName, known);
      }
      return known;
    }
    return lineNumber(stream);
  }

  /**
   * Returns the drawing line number of an actual stream.
   *
   * @param stream source stream
   * @return drawing line number
   */
  String lineNumber(StreamInterface stream) {
    String known = streamLineNumbers.get(stream);
    if (known == null) {
      known = lineNumberFor(stream.getName(), stream);
      streamLineNumbers.put(stream, known);
    }
    return known;
  }

  /**
   * Assigns the drawing line number of one stream and records it in the line-number register.
   *
   * @param streamName source stream name
   * @param stream source stream, or null when it cannot be resolved
   * @return drawing line number
   */
  private String lineNumberFor(String streamName, StreamInterface stream) {
    String upper = streamName.trim().toUpperCase(Locale.ROOT);
    String number;
    if (LINE_TAG.matcher(streamName.trim()).matches() && upper.equals(streamName.trim())) {
      number = streamName.trim();
    } else {
      ProcessEquipmentInterface owner = stream == null ? null : producers.get(stream);
      if (owner == null || owner instanceof StreamInterface) {
        ProcessEquipmentInterface consumer = stream == null ? null : soleConsumer(stream);
        owner = consumer != null ? consumer : owner;
      }
      String area = "00";
      if (owner != null) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^([0-9]{1,4})-").matcher(owner.getName());
        if (matcher.find()) {
          area = matcher.group(1);
        }
      }
      String fluid = "PG";
      try {
        if (stream != null && stream.getFluid() != null) {
          neqsim.thermo.system.SystemInterface system = stream.getFluid();
          fluid = system.hasPhaseType("gas") && system.getNumberOfPhases() == 1 ? "PG"
              : !system.hasPhaseType("gas") ? "PL" : system.getBeta(0) >= 0.5 ? "PG" : "PL";
        }
      } catch (RuntimeException ignored) {
        fluid = "PG";
      }
      String key = area + "-" + fluid;
      int next = lineSequence.containsKey(key) ? lineSequence.get(key).intValue() + 1 : 1;
      do {
        number = String.format(Locale.ROOT, "%s-%s-%03d", area, fluid, Integer.valueOf(next));
        next++;
      } while (lineNumbers.containsKey(number));
      lineSequence.put(key, Integer.valueOf(next - 1));
      hold("LINE_NUMBER_GENERATED", streamName, "ISO 10628-1:2014 / NORSOK Z-DP-002",
          "Generated drawing line number " + number + " must be confirmed against the project line list");
    }
    ProcessEquipmentInterface producer = stream == null ? null : producers.get(stream);
    lineNumbers.put(number, streamName + (producer == null || producer == stream ? "" : " @ " + producer.getName()));
    return number;
  }

  /** @return source stream (name @ producer) by drawing line number, including generated numbers */
  public Map<String, String> getLineNumbers() {
    return Collections.unmodifiableMap(lineNumbers);
  }


  /**
   * Adds the equipment data callout above the equipment, as is usual practice on process P&amp;IDs: tag, service,
   * operating conditions from the converged simulation, duty or power, and design conditions (HOLD until supplied).
   *
   * @param scene sheet
   * @param unit primary equipment
   * @param x callout centre x
   */
  private void equipmentCallout(Scene scene, ProcessEquipmentInterface unit, double x) {
    String service = unit.getClass().getSimpleName().replaceAll("([a-z])([A-Z])", "$1 $2").toUpperCase(Locale.ROOT)
        .replace("THREE PHASE", "3-PHASE");
    List<String[]> lines = new ArrayList<String[]>();
    lines.add(new String[] {unit.getName() + "  " + service, "3.5"});
    StringBuilder operating = new StringBuilder("OPERATING:");
    try {
      if (!unit.getOutletStreams().isEmpty()) {
        StreamInterface outlet = unit.getOutletStreams().get(0);
        operating.append(String.format(Locale.ROOT, " %.1f bara / %.1f C", outlet.getPressure("bara"),
            outlet.getTemperature("C")));
      }
      if (unit instanceof neqsim.process.equipment.compressor.Compressor) {
        operating.append(String.format(Locale.ROOT, " / %.0f kW",
            ((neqsim.process.equipment.compressor.Compressor) unit).getPower("kW")));
      } else if (unit instanceof neqsim.process.equipment.pump.Pump) {
        operating.append(
            String.format(Locale.ROOT, " / %.1f kW", ((neqsim.process.equipment.pump.Pump) unit).getPower("kW")));
      } else if (unit instanceof neqsim.process.equipment.heatexchanger.Heater) {
        operating.append(String.format(Locale.ROOT, " / DUTY %.0f kW",
            ((neqsim.process.equipment.heatexchanger.Heater) unit).getDuty() / 1000.0));
      }
    } catch (RuntimeException e) {
      operating.append(" HOLD");
    }
    lines.add(new String[] {operating.toString(), "3"});
    String designPressure = "HOLD barg";
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Object proposed = element.getAttributes().get("studyProtectedDesignPressureBarg");
      if (proposed instanceof Number) {
        designPressure = String.format(Locale.ROOT, "%.1f barg (PROP.)", ((Number) proposed).doubleValue());
      }
    }
    lines.add(new String[] {"DESIGN: " + designPressure + " / HOLD C   |   SIZE: see data sheet", "3"});
    scene.callout = lines;
    scene.calloutX = x;
  }



  /**
   * Replaces the sheet-total and peer-sheet placeholders in one text.
   *
   * @param text text possibly containing placeholders
   * @param total formatted total sheet count
   * @return resolved text
   */
  private String resolvePlaceholders(String text, String total) {
    String result = text.replace(SHEET_TOTAL_TOKEN, total);
    java.util.regex.Matcher matcher = SHEET_REF.matcher(result);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String drawn = drawnSheets.get(matcher.group(1));
      String sheet = drawn != null ? drawn : matcher.group(2);
      // A peer that is not drawn on any sheet is listed in the HOLD register; never point to a guessed sheet.
      matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(drawn != null ? "/PID-" + sheet
          : "/HOLD"));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }


  /**
   * Returns the displayed length of a label after peer-sheet placeholders are resolved (estimated with the fallback).
   *
   * @param label label possibly containing placeholders
   * @return displayed character count
   */
  static int displayLength(String label) {
    return label.replaceAll("@@AT:[^|@]*\\|([^@]*)@@", "$1").length();
  }

  /** Replaces the sheet-total placeholder in every title block once pagination is final, then checks standards. */
  private void fillSheetTotals() {
    resolveSheetText();
    for (Map<String, Object> layout : layouts.values()) {
      assessDrawingStandards(layout);
    }
  }

  private static final java.util.regex.Pattern SELF_REFERENCE = java.util.regex.Pattern
      .compile("SG-[0-9]+/PID-([0-9]{3})");
  private static final String[] TITLE_FIELDS = {"OWNER:", "DOC. TYPE:", "DOC. NO:", "REV:", "SHEET:", "ISSUED:",
      "STATUS:", "LANG:", "CREATED:", "APPROVED:", "REV", "DATE", "DESCRIPTION", "PREP", "CHK", "APPR"};

  /**
   * Checks one rendered sheet against the drawing conventions this renderer implements.
   *
   * <p>
   * Each violation is a drawing error, so a generated set cannot report a clean drawing while breaking them: ISO 7200
   * identification fields and revision table; ISO 5457 frame furniture; resolved sheet references with no
   * continuation pointing at its own sheet; line designations instead of internal simulation names; minimum text
   * height of 2.5 mm; and valve symbols consistent with the ISA-5.1 function letters of their tags.
   * </p>
   *
   * @param layout rendered sheet layout specification
   */
  @SuppressWarnings("unchecked")
  private void assessDrawingStandards(Map<String, Object> layout) {
    String sheet = String.valueOf(layout.get("name"));
    String number = String.valueOf(layout.get("sheetNumber"));
    if (!(layout.get("sheet_furniture") instanceof Map)) {
      geometryError("PID_STANDARD_SHEET_FRAME", "ISO 5457 frame, grid and centring marks are missing", sheet);
    }
    List<Map<String, Object>> nodes = (List<Map<String, Object>>) layout.get("nodes");
    if (nodes != null) {
      for (Map<String, Object> node : nodes) {
        if (node.get("bbox") instanceof List) {
          List<?> box = (List<?>) node.get("bbox");
          double x = ((Number) box.get(0)).doubleValue();
          double y = ((Number) box.get(1)).doubleValue();
          double w = ((Number) box.get(2)).doubleValue();
          double h = ((Number) box.get(3)).doubleValue();
          if (x < FRAME_LEFT - 0.01 || y < FRAME_TOP - 0.01 || x + w > FRAME_RIGHT + 0.01
              || y + h > FRAME_BOTTOM + 0.01) {
            geometryError("PID_STANDARD_DRAWING_AREA",
                "Symbol " + node.get("id") + " extends outside the ISO 5457 drawing frame", sheet);
          }
        }
      }
    }
    List<Map<String, Object>> labels = (List<Map<String, Object>>) layout.get("labels");
    java.util.Set<String> present = new java.util.HashSet<String>();
    for (Map<String, Object> label : labels) {
      String text = String.valueOf(label.get("text"));
      for (String field : TITLE_FIELDS) {
        if (text.equals(field) || text.startsWith(field + " ") || field.endsWith(":") && text.startsWith(field)) {
          present.add(field);
        }
      }
      if (text.contains("@@")) {
        geometryError("PID_STANDARD_UNRESOLVED_REFERENCE", "Unresolved drawing reference: " + text, sheet);
      }
      if (text.startsWith("SHEET:") && !text.matches("SHEET: [0-9]{3} OF [0-9]{3}")) {
        geometryError("PID_STANDARD_SHEET_NUMBERING", "Sheet number must read 'SHEET: n OF N': " + text, sheet);
      }
      java.util.regex.Matcher self = SELF_REFERENCE.matcher(text);
      while (self.find()) {
        if (self.group(1).equals(number)) {
          diagnostics.add(NativeEngineeringDiagramRenderer.sceneDiagnostic(Severity.WARNING,
              "PID_STANDARD_SIGNAL_SELF_REFERENCE",
              "Named same-sheet signal continuation used because no clear direct route exists: " + text, sheet));
        }
      }
      if (text.contains(" / DN") || text.startsWith("B/L ")) {
        String designation = text.startsWith("B/L ") ? text.substring(4).split(" ")[0] : text.split(" / ")[0];
        if (!designation.equals(designation.toUpperCase(Locale.ROOT))
            || designation.toUpperCase(Locale.ROOT).contains("STREAM")) {
          geometryError("PID_STANDARD_LINE_DESIGNATION",
              "Line designation uses an internal simulation name instead of a line number: " + text, sheet);
        }
      }
      Object size = label.get("font_size");
      if (size instanceof Number && ((Number) size).doubleValue() < 2.5 - 1e-9) {
        geometryError("PID_STANDARD_TEXT_HEIGHT", "Text below the 2.5 mm minimum height: " + text, sheet);
      }
    }
    if ("pid".equals(layout.get("kind")) || "pfd".equals(layout.get("kind"))) {
      for (String field : TITLE_FIELDS) {
        if (!present.contains(field)) {
          geometryError("PID_STANDARD_TITLE_BLOCK", "ISO 7200 title block or revision field missing: " + field,
              sheet);
        }
      }
    }
    for (Map<String, Object> node : (List<Map<String, Object>>) layout.get("nodes")) {
      String tag = String.valueOf(node.get("tag"));
      String family = String.valueOf(node.get("symbolFamily"));
      String letters = isaLetters(tag);
      if (letters == null) {
        continue;
      }
      boolean shutdown = letters.equals("ESDV") || letters.equals("SDV") || letters.equals("PSDV")
          || letters.equals("BDV") || letters.equals("XV") || letters.equals("HIPPS");
      boolean control = letters.length() >= 3 && letters.endsWith("CV") && !shutdown;
      if (shutdown && !Family.SHUTDOWN_VALVE.name().equals(family)) {
        geometryError("PID_STANDARD_VALVE_SYMBOL", tag + " is an actuated shutdown valve but is drawn as " + family,
            sheet);
      }
      if (control && !Family.CONTROL_VALVE.name().equals(family)
          && !Family.PNEUMATIC_OPERATED_VALVE.name().equals(family)) {
        geometryError("PID_STANDARD_VALVE_SYMBOL", tag + " is a control valve but is drawn as " + family, sheet);
      }
    }
  }

  /**
   * Extracts the ISA-5.1 function letters from a tag such as {@code 20-ESDV-001}.
   *
   * @param tag device tag
   * @return function letters, or null when the tag has no letter group
   */
  static String isaLetters(String tag) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^[0-9A-Z]{1,4}-([A-Z]{2,6})-[0-9]{2,}$")
        .matcher(tag == null ? "" : tag);
    return matcher.matches() ? matcher.group(1) : null;
  }

  /** Resolves sheet-total and peer-sheet placeholders in every rendered text. */
  private void resolveSheetText() {
    String total = String.format(Locale.ROOT, "%03d", Integer.valueOf(pages.size()));
    for (Page page : pages) {
      for (int index = 0; index < page.commands.size(); index++) {
        Command command = page.commands.get(index);
        if ("text".equals(command.type) && command.text != null
            && (command.text.contains(SHEET_TOTAL_TOKEN) || command.text.contains(SHEET_REF_START))) {
          Command replacement = Command.text(command.x, command.y, command.size,
              resolvePlaceholders(command.text, total), command.fill, command.id, command.anchor);
          replacement.bold = command.bold;
          page.commands.set(index, replacement);
        }
      }
    }
    for (Map<String, Object> layout : layouts.values()) {
      Object labels = layout.get("labels");
      if (labels instanceof List) {
        for (Object label : (List<?>) labels) {
          if (label instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) label;
            Object text = map.get("text");
            if (text instanceof String) {
              map.put("text", resolvePlaceholders((String) text, total));
            }
          }
        }
      }
    }
  }


  /**
   * Returns the short drawing label of a nozzle: N1, N2, ... numbered per equipment in drawing order, as on process
   * P&amp;IDs and equipment data sheets. The controlled nozzle identity is kept in the nozzle register of the content
   * report.
   *
   * @param equipmentTag owning equipment
   * @param nozzleIdentity controlled nozzle identity (proposal tag or design nozzle tag)
   * @return short drawing label
   */
  String nozzleLabel(String equipmentTag, String nozzleIdentity) {
    Map<String, String> labels = nozzleRegister.computeIfAbsent(equipmentTag == null ? "" : equipmentTag,
        key -> new LinkedHashMap<String, String>());
    for (Map.Entry<String, String> entry : labels.entrySet()) {
      if (entry.getValue().equals(nozzleIdentity)) {
        return entry.getKey();
      }
    }
    String label = "N" + (labels.size() + 1);
    labels.put(label, nozzleIdentity);
    return label;
  }


  /**
   * Returns the auxiliary route group keys of one unit, in model order.
   *
   * @param unit source unit
   * @return route group keys
   */
  private Set<String> auxiliaryGroupKeys(ProcessEquipmentInterface unit) {
    Set<String> groups = new LinkedHashSet<String>();
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment a = projection.getAttachments().get(element.getTag());
      if (a.getKind() == PidPhysicalProjection.Kind.AUXILIARY_ROUTE
          || a.getKind() == PidPhysicalProjection.Kind.SENSING && "DESIGNED_NOZZLE".equals(a.getDirection())) {
        String route = textValue(a.toMap().get("physicalRouteId"));
        groups.add(route.isEmpty() ? element.getTag() : route);
      }
    }
    return groups;
  }

  /**
   * Returns true for a simple heater or cooler that is drawn as a band on a shared heat-exchanger sheet.
   *
   * <p>
   * Eligible units have one inlet and one outlet, no instrument-detail sheets, no material-path role, and are fed either
   * directly or through a two-inlet header tee. Two-circuit exchangers, fired heaters with more complex topology and
   * units with too many instrument rows keep their own sheet.
   * </p>
   *
   * @param unit candidate unit
   * @return true when the unit is drawn on a shared heat-exchanger sheet
   */
  private boolean compactExchanger(ProcessEquipmentInterface unit) {
    if (unit.getClass() != neqsim.process.equipment.heatexchanger.Heater.class
        && unit.getClass() != neqsim.process.equipment.heatexchanger.Cooler.class) {
      return false;
    }
    if (unit.getInletStreams().size() != 1 || unit.getOutletStreams().size() != 1
        || materialPathTags.contains(unit.getName())) {
      return false;
    }
    List<PidElement> details = instrumentDetails.get(unit.getName());
    if (details != null && !details.isEmpty()) {
      return false;
    }
    ProcessEquipmentInterface producer = producers.get(unit.getInletStreams().get(0));
    if (supportedSuctionMixer(producer)
        || supportedHeaderMixer(producer) && producer.getInletStreams().size() != 2) {
      return false;
    }
    return exchangerRows(unit) <= 5;
  }

  /**
   * Counts the instrument rows of an exchanger band: one per line sensor and per stand-alone function.
   *
   * @param unit exchanger
   * @return instrument rows
   */
  private int exchangerRows(ProcessEquipmentInterface unit) {
    int rows = 0;
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment a = projection.getAttachments().get(element.getTag());
      if (a.getKind() == PidPhysicalProjection.Kind.SENSING && !"DESIGNED_NOZZLE".equals(a.getDirection())
          || a.getKind() == PidPhysicalProjection.Kind.MANUAL_INPUT
          || a.getKind() == PidPhysicalProjection.Kind.FUNCTION && incomingInstruments(element).isEmpty()
              && !localCommandOutput(element)) {
        rows++;
      }
    }
    return rows;
  }

  /**
   * Returns the height of an exchanger band on a shared sheet.
   *
   * @param unit exchanger
   * @return band height in millimetres
   */
  private double exchangerBandHeight(ProcessEquipmentInterface unit) {
    return 50.0 + Math.max(1, exchangerRows(unit)) * EXCHANGER_ROW + 12.0;
  }

  /**
   * Draws one shared heat-exchanger sheet: each heater or cooler as a band with its process line, inline devices,
   * nozzles, instruments, functions, signals and data callout.
   *
   * @param exchangers exchangers on this sheet, top to bottom
   */
  private void renderExchangerSheet(List<ProcessEquipmentInterface> exchangers) {
    List<String> tags = new ArrayList<String>();
    for (ProcessEquipmentInterface exchanger : exchangers) {
      tags.add(exchanger.getName());
    }
    Scene scene = scene(join(tags), "Heat exchangers");
    scene.compactAnnotations = true;
    double top = EXCHANGER_SHEET_TOP;
    for (ProcessEquipmentInterface exchanger : exchangers) {
      renderExchangerBand(scene, exchanger, top);
      top += exchangerBandHeight(exchanger);
    }
    scene.spec.put("subjectTag", join(tags));
    scene.text(25, 490,
        "P&ID design status: proposed devices remain REVIEW REQUIRED; FA:HOLD means failure action is not assigned.", 3,
        null);
    scene.text(25, 499,
        "Heaters and coolers are grouped on this sheet; their relief, vent and drain connections are on the pooled auxiliary sheets.",
        3, null);
    scene.finish();
  }

  /**
   * Draws one heater or cooler band.
   *
   * @param scene shared sheet
   * @param unit heater or cooler
   * @param top band top in millimetres
   */
  private void renderExchangerBand(Scene scene, ProcessEquipmentInterface unit, double top) {
    scene.spec.put("subjectTag", unit.getName());
    Set<String> before = new LinkedHashSet<String>(scene.proposalNodes.keySet());
    double y = top + 22.0;
    Node body = scene.node(unit.getName(), Family.HEAT_EXCHANGER, 300, y, 30, 30, unit.getName());
    exchangerCallout(scene, unit, top);
    Map<String, Node> valveNodes = new LinkedHashMap<String, Node>();
    // Inlet: header tee or upstream continuation, then inline devices.
    StreamInterface inletStream = unit.getInletStreams().get(0);
    ProcessEquipmentInterface producer = producers.get(inletStream);
    List<PidElement> inletInline = inline(unit.getName(), inletStream.getName(), "INLET");
    StreamInterface sourceStream = inletStream;
    if (!inletInline.isEmpty()) {
      Attachment first = projection.getAttachments().get(inletInline.get(0).getTag());
      ProcessEquipmentInterface actual = equipment.get(first.getEquipmentTag());
      if (first.getKind() == PidPhysicalProjection.Kind.EXISTING_EQUIPMENT && actual != null
          && !actual.getInletStreams().isEmpty()) {
        sourceStream = actual.getInletStreams().get(0);
      }
    }
    Node last = supportedHeaderMixer(producer) ? headerTee(scene, producer, y)
        : scene.terminal("in:" + inletStream.getName(), 60, y, upstreamLabel(sourceStream), false);
    int index = 0;
    for (PidElement proposal : inletInline) {
      Node node = proposalValve(scene, proposal, 140 + index++ * 50, y);
      valveNodes.put(proposal.getTag(), node);
      scene.route(last, "r", node, "l", sourceStream.getName(), false, null);
      last = node;
    }
    scene.route(last, "r", body, "l", inletStream.getName(), false, null);
    scene.lineLabel(inletStream.getName(), 160, y - 6, unit.getName());
    // Outlet: inline devices, then downstream continuation.
    StreamInterface outletStream = unit.getOutletStreams().get(0);
    last = body;
    index = 0;
    String physical = outletStream.getName();
    for (PidElement proposal : inline(unit.getName(), outletStream.getName(), "OUTLET")) {
      Node node = proposalValve(scene, proposal, 470 + index++ * 70, y);
      valveNodes.put(proposal.getTag(), node);
      scene.route(last, "r", node, "l", physical, false, null);
      last = node;
    }
    for (ProcessEquipmentInterface valve : downstreamValves(unit)) {
      if (valve.getInletStreams().contains(outletStream) && !valveNodes.containsKey(valve.getName())) {
        double vx = 470 + index++ * 70;
        Node node;
        if (proposals.containsKey(valve.getName())) {
          node = proposalValve(scene, proposals.get(valve.getName()), vx, y);
        } else {
          node = scene.node(valve.getName(), ProfessionalPidSymbolLibrary.family(valve), vx, y, 16, 10, valve.getName());
        }
        valveNodes.put(valve.getName(), node);
        scene.route(last, "r", node, "l", physical, false, null);
        last = node;
        if (!valve.getOutletStreams().isEmpty()) {
          physical = valve.getOutletStreams().get(0).getName();
        }
      }
    }
    Node target = scene.terminal("out:" + outletStream.getName(), 790, y, downstreamLabel(outletStream), false);
    scene.route(last, "r", target, "l", physical, false, null);
    scene.lineLabel(physical, 650, y + 12, unit.getName());
    // Instruments: outlet sensors first (taps right of the exchanger), then inlet sensors and stand-alone functions.
    List<PidElement> outletSensors = new ArrayList<PidElement>();
    List<PidElement> inletSensors = new ArrayList<PidElement>();
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment a = projection.getAttachments().get(element.getTag());
      if (a.getKind() == PidPhysicalProjection.Kind.SENSING && !"DESIGNED_NOZZLE".equals(a.getDirection())) {
        if ("INLET".equals(a.getDirection())) {
          inletSensors.add(element);
        } else {
          outletSensors.add(element);
        }
      }
    }
    Set<String> placed = new LinkedHashSet<String>();
    int row = 0;
    for (int i = 0; i < outletSensors.size(); i++) {
      PidElement sensor = outletSensors.get(i);
      double ry = top + 50 + row++ * EXCHANGER_ROW;
      Node measurement = scene.instrument(sensor, 430, ry);
      Point tap = new Point(366 - i * 8, y);
      scene.sensing(null, tap, measurement, "l", points(tap, new Point(tap.x, ry), measurement.left()),
          sensor.getTag());
      placed.add(sensor.getTag());
      placeChildFunction(scene, sensor, measurement, 550, ry, placed);
    }
    for (int i = 0; i < inletSensors.size(); i++) {
      PidElement sensor = inletSensors.get(i);
      double ry = top + 50 + row++ * EXCHANGER_ROW;
      Node measurement = scene.instrument(sensor, 430, ry);
      Point tap = new Point(262 - i * 8, y);
      scene.sensing(null, tap, measurement, "l", points(tap, new Point(tap.x, ry), measurement.left()),
          sensor.getTag());
      placed.add(sensor.getTag());
      placeChildFunction(scene, sensor, measurement, 550, ry, placed);
    }
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment a = projection.getAttachments().get(element.getTag());
      if (a.getKind() == PidPhysicalProjection.Kind.MANUAL_INPUT && !placed.contains(element.getTag())) {
        double ry = top + 50 + row++ * EXCHANGER_ROW;
        Node input = scene.instrument(element, 430, ry);
        placed.add(element.getTag());
        placeChildFunction(scene, element, input, 550, ry, placed);
      }
    }
    for (PidElement element : model.getElementsForEquipment(unit.getName())) {
      Attachment a = projection.getAttachments().get(element.getTag());
      if (a.getKind() == PidPhysicalProjection.Kind.FUNCTION && !placed.contains(element.getTag())
          && !localCommandOutput(element)) {
        double ry = top + 50 + row++ * EXCHANGER_ROW;
        Node function = scene.instrument(element, 550, ry);
        placed.add(element.getTag());
        placeLocalCommand(scene, element, function, placed);
      }
    }
    for (Map.Entry<String, Node> entry : new LinkedHashMap<String, Node>(scene.proposalNodes).entrySet()) {
      if (!before.contains(entry.getKey())) {
        signalEnds(scene, entry.getKey(), entry.getValue());
      }
    }
    drawNozzles(scene, unit, body, valveNodes);
    representedEquipment.add(unit.getName());
  }

  /**
   * Adds the equipment data callout of an exchanger band above its process line.
   *
   * @param scene shared sheet
   * @param unit heater or cooler
   * @param top band top in millimetres
   */
  private void exchangerCallout(Scene scene, ProcessEquipmentInterface unit, double top) {
    String service = unit.getClass().getSimpleName().toUpperCase(Locale.ROOT);
    String operating = "OPERATING: HOLD";
    try {
      StreamInterface outlet = unit.getOutletStreams().get(0);
      operating = String.format(Locale.ROOT, "OPERATING: %.1f bara / %.1f C / DUTY %.0f kW",
          outlet.getPressure("bara"), outlet.getTemperature("C"),
          ((neqsim.process.equipment.heatexchanger.Heater) unit).getDuty() / 1000.0);
    } catch (RuntimeException e) {
      operating = "OPERATING: HOLD";
    }
    scene.centered(680, top + 1, unit.getName() + "  " + service, 3.5, null);
    scene.centered(680, top + 6, operating, 3, null);
    String size = sizing.equipmentLabel(unit.getName());
    scene.centered(680, top + 11, "DESIGN: HOLD barg / HOLD C" + (size == null || size.trim().isEmpty() ? "" : " | " + size),
        3, null);
  }

  /**
   * Returns whether a family is an in-line valve symbol.
   *
   * @param family symbol family
   * @return true for control, actuated, shutdown, manual isolation and relief valves
   */
  static boolean isValveFamily(Family family) {
    return actuatedValve(family) || family == Family.ISOLATION_VALVE || family == Family.RELIEF_VALVE;
  }

  /**
   * Returns whether a valve family is drawn with an ISA-5.1 actuator above its body.
   *
   * @param family symbol family
   * @return true for control, pneumatic-operated and shutdown valves
   */
  static boolean actuatedValve(Family family) {
    return family == Family.CONTROL_VALVE || family == Family.PNEUMATIC_OPERATED_VALVE
        || family == Family.SHUTDOWN_VALVE;
  }

  /**
   * Returns whether a function symbol is drawn inside a square (ISA-5.1 shared or programmable function).
   *
   * @param family symbol family
   * @return true for DCS control and SIS logic functions
   */
  static boolean boxedFunction(Family family) {
    return family == Family.CONTROL_FUNCTION || family == Family.SAFETY_FUNCTION;
  }

  /** One native scene with a geometry specification used by pfd-layout-review. */
  private final class Scene {
    final Page page;
    final Map<String, Object> spec = new LinkedHashMap<String, Object>();
    final List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
    final List<Map<String, Object>> edges = new ArrayList<Map<String, Object>>();
    final List<Map<String, Object>> labels = new ArrayList<Map<String, Object>>();
    final List<double[]> junctions = new ArrayList<double[]>();
    /** Crossings without connection, drawn as interruptions of the signal line. */
    final List<double[]> breaks = new ArrayList<double[]>();
    final Map<String, Node> proposalNodes = new LinkedHashMap<String, Node>();
    final List<PendingSignal> pendingSignals = new ArrayList<PendingSignal>();
    /** Equipment data callout lines {text, size}, placed when the sheet is finished. */
    List<String[]> callout;
    double calloutX;
    /** Controlled sheet number. */
    final String number;
    /** Continuation zones reserved for cross-sheet signal flags: {owner tag, box}. */
    final List<Object[]> reserved = new ArrayList<Object[]>();
    int directSignalLanes = 0;

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
      this.number = number;
      page = new Page(id, WIDTH, HEIGHT);
      page.commands.add(Command.rect(20, 10, WIDTH - 30, HEIGHT - 20, "#111111", "none", .5, "border", ""));
      text(25, 25, project.getName(), 5, null);
      text(25, 38, title, 4, null);
      text(25, 55, "ISO 15519 / ISO 10628 basis | A1 | solid: material / sensing | dashed: control | NOT TO SCALE", 3,
          null);
      if ("pid".equals(kind) && project.getPidSizingSchedule() != null) {
        text(490, 55, "PREL. = preliminary size   |   HOLD = project input required", 3, null);
      }
      isoFrame();
      titleBlock(id, title, number, kind);
      Map<String, Object> furniture = new LinkedHashMap<String, Object>();
      furniture.put("standard", "ISO 5457 / ISO 7200");
      furniture.put("filingMarginMm", Double.valueOf(20));
      furniture.put("borderMm", Double.valueOf(10));
      furniture.put("gridColumns", Integer.valueOf(16));
      furniture.put("gridRows", "ABCDEFGHJKLM");
      furniture.put("centringMarks", Boolean.TRUE);
      furniture.put("titleBlockWidthMm", Double.valueOf(180));
      furniture.put("revisionTable", Boolean.TRUE);
      spec.put("sheet_furniture", furniture);
      spec.put("sheetNumber", number);
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
      spec.put("line_breaks", breaks);
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
          : family == Family.TERMINAL || family == Family.TERMINAL_NORTH || family == Family.TERMINAL_WEST ? "boundary"
              : family == Family.FIELD_INSTRUMENT ? "instrument"
                  : family == Family.CONTROL_FUNCTION || family == Family.SAFETY_FUNCTION ? "controller" : "equipment");
      double top = y - height / 2;
      double boundHeight = height;
      if (actuatedValve(family) || family == Family.RELIEF_VALVE) {
        top = y - 19;
        boundHeight = 19 + height / 2;
      }
      record.put("bbox",
          boxedFunction(family) ? new double[] {x - width / 2 - 2, top - 2, width + 4, boundHeight + 4}
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
          String upper = parts.length > 1 ? parts[parts.length - 2] : label;
          String lower = parts.length > 1 ? parts[parts.length - 1] : "";
          boolean compact = upper.length() > 8 || lower.length() > 8;
          centered(x, y - 3, upper.length() > 8 ? upper.substring(0, 7) + "." : upper, 3, tag);
          centered(x, y + 3.5, lower.length() > 8 ? lower.substring(0, 7) + "." : lower, 3, tag);
          if (compact) {
            centered(x, y + height / 2 + 6, label, 2.5, null);
          }
        } else if (family == Family.TERMINAL || family == Family.TERMINAL_NORTH || family == Family.TERMINAL_WEST) {
          if ((tag.startsWith("signal-from:") || tag.startsWith("signal-to:")) && label.length() > 36
              && label.contains(", ")) {
            int split = label.indexOf(", ", label.length() / 2 - 1);
            if (split < 0) {
              split = label.lastIndexOf(", ");
            }
            centered(x, y - 2.5, label.substring(0, split), 2.7, tag);
            centered(x, y + 2.5, label.substring(split + 2), 2.7, tag);
          } else {
            centered(x, y, shorten(label, 36), 2.7, tag);
          }
        } else if (family == Family.RELIEF_VALVE) {
          // Angle-body relief valve: the inlet rises from below, so the tag sits beside it under the outlet.
          text(x + 4, y + 10, label, 3.5, tag);
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
      return instrument(element, x, y, 18);
    }

    /** True on compact band sheets: study annotations go above the incoming signal instead of below the symbol. */
    boolean compactAnnotations;

    /**
     * Adds a function using the required source-backed input-port envelope size.
     *
     * @param element canonical instrument/function
     * @param x centre x
     * @param y centre y
     * @param diameter instrument circle diameter
     * @return actual drawing node
     */
    Node instrument(PidElement element, double x, double y, double diameter) {
      Family family = "CALCULATED_COMPRESSOR_MAP_MARGIN".equals(element.getAttributes().get("inputKind"))
          || "COMPRESSOR_SPEED".equals(element.getAttributes().get("nativeActuatorRole"))
          || "COOLER_UTILITY_OPENING".equals(element.getAttributes().get("nativeActuatorRole"))
              ? Family.CONTROL_FUNCTION
              : element.getType() == PidElementType.MEASUREMENT || element.getType() == PidElementType.INDICATOR
                  ? Family.FIELD_INSTRUMENT
                  : element.getType() == PidElementType.CONTROLLER ? Family.CONTROL_FUNCTION : Family.SAFETY_FUNCTION;
      Node node = node(element.getTag(), family, x, y, diameter, diameter, element.getTag());
      proposalNodes.put(element.getTag(), node);
      drawnSheets.putIfAbsent(element.getTag(), number);
      Object study = element.getAttributes().get(neqsim.process.engineering.pid.PidSafetyStudies.ANNOTATION);
      if (study != null) {
        if (compactAnnotations) {
          centered(x - 26.0, y - 2.5, String.valueOf(study), 2.5, null);
        } else {
          centered(x, y + diameter / 2.0 + 5.0, String.valueOf(study), 2.5, null);
        }
      }
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
      double width = compact ? Math.max(30, displayLength(label) * 1.6 + 10) : 70;
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
      if (!signal && to.family == Family.RELIEF_VALVE && "l".equals(targetSide)) {
        // ISO 10628-2 angle-body relief valve: the process inlet enters from below, the outlet leaves to the side.
        List<Point> angled = new ArrayList<Point>(path.subList(0, path.size() - 1));
        Point last = angled.get(angled.size() - 1);
        Point inlet = to.bottom();
        double dip = inlet.y + 7;
        double turnX = Math.min(last.x, inlet.x - 10);
        if (Math.abs(last.y - dip) > 1e-6) {
          angled.add(new Point(turnX, last.y));
          angled.add(new Point(turnX, dip));
        }
        angled.add(new Point(inlet.x, dip));
        angled.add(inlet);
        path = angled;
      }
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
     * Returns true when any routed connection passes through a box.
     *
     * @param box {x, y, width, height}
     * @return true when a route segment lies in the box
     */
    @SuppressWarnings("unchecked")
    boolean routeInBox(double[] box) {
      for (Map<String, Object> edge : edges) {
        List<double[]> points = (List<double[]>) edge.get("points");
        for (int segment = 1; segment < points.size(); segment++) {
          if (segmentInBox(points.get(segment - 1), points.get(segment), box, .25)) {
            return true;
          }
        }
      }
      return false;
    }

    /**
     * Strokes a signal line that is interrupted where it crosses another line.
     *
     * <p>
     * A crossing without connection is shown by a 1.5 mm gap on each side of the crossing in the signal line, so it
     * cannot be read as a junction. The crossing points are recorded in the layout specification as line breaks.
     * </p>
     *
     * @param path orthogonal signal path
     * @param from source identity
     * @param to target identity
     * @param id stable signal identity
     */
    @SuppressWarnings("unchecked")
    void strokeBroken(List<Point> path, String from, String to, String id) {
      strokeBroken(path, from, to, id, true);
    }

    /**
     * Strokes an interrupted signal line with an optional end arrow.
     *
     * @param path orthogonal signal path
     * @param from source identity
     * @param to target identity
     * @param id stable signal identity
     * @param arrow true for an arrow at the end
     */
    @SuppressWarnings("unchecked")
    void strokeBroken(List<Point> path, String from, String to, String id, boolean arrow) {
      List<List<Point>> pieces = new ArrayList<List<Point>>();
      List<Point> current = new ArrayList<Point>();
      current.add(path.get(0));
      for (int index = 1; index < path.size(); index++) {
        Point a = path.get(index - 1);
        Point b = path.get(index);
        double length = Math.hypot(b.x - a.x, b.y - a.y);
        if (length < 1e-9) {
          continue;
        }
        double ux = (b.x - a.x) / length;
        double uy = (b.y - a.y) / length;
        List<Double> cuts = new ArrayList<Double>();
        for (Map<String, Object> edge : edges) {
          List<double[]> points = (List<double[]>) edge.get("points");
          for (int segment = 1; segment < points.size(); segment++) {
            double[] hit = crossing(new double[] {a.x, a.y}, new double[] {b.x, b.y}, points.get(segment - 1),
                points.get(segment));
            if (hit != null) {
              double distance = Math.hypot(hit[0] - a.x, hit[1] - a.y);
              if (distance > 2.0 && distance < length - 2.0) {
                cuts.add(Double.valueOf(distance));
                breaks.add(hit);
              }
            }
          }
        }
        Collections.sort(cuts);
        for (Double cut : cuts) {
          current.add(new Point(a.x + ux * (cut - 1.5), a.y + uy * (cut - 1.5)));
          pieces.add(current);
          current = new ArrayList<Point>();
          current.add(new Point(a.x + ux * (cut + 1.5), a.y + uy * (cut + 1.5)));
        }
        current.add(b);
      }
      pieces.add(current);
      for (int index = 0; index < pieces.size(); index++) {
        stroke(pieces.get(index), from, to, id, true, false, arrow && index == pieces.size() - 1);
      }
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
      stroke(path, from, to, id, signal, sensing, !sensing);
    }

    /**
     * Adds one native polyline, an optional arrow and the exact geometry sidecar.
     *
     * @param path source-to-target points
     * @param from source identity
     * @param to target identity
     * @param id controlled route ID
     * @param signal true for control signal
     * @param sensing true for sensing line
     * @param arrow true to draw the direction arrow at the end
     */
    void stroke(List<Point> path, String from, String to, String id, boolean signal, boolean sensing,
        boolean arrow) {
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
      if (arrow) {
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
      edge.put("arrow_end", Boolean.valueOf(arrow));
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
      String nominal = sizing.nominal(stream);
      String clazz = sizing.pipingClass(stream);
      String value = lineNumber(stream, owner) + " / " + (nominal.isEmpty() ? "DN:HOLD" : nominal)
          + (sizing.calculatedSchedule(stream).isEmpty() ? "" : " PREL.") + " / "
          + (clazz.isEmpty() ? "CLASS:HOLD" : clazz);
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
      double width = displayLength(value) * size * .52;
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

    /**
     * Draws the ISO 5457 sheet furniture: 0.7 mm centring marks and the grid reference system (16 numbered columns
     * and 12 lettered rows, I and O omitted) in the border between the frame and the trimmed edge.
     */
    private void isoFrame() {
      double left = 20.0;
      double right = WIDTH - 10.0;
      double top = 10.0;
      double bottom = HEIGHT - 10.0;
      int columns = 16;
      String rows = "ABCDEFGHJKLM";
      for (int column = 0; column <= columns; column++) {
        double x = left + (right - left) * column / columns;
        if (column > 0 && column < columns) {
          page.commands.add(Command.line(x, top - 5, x, top, "#111111", .35, "grid", ""));
          page.commands.add(Command.line(x, bottom, x, bottom + 5, "#111111", .35, "grid", ""));
        }
        if (column < columns) {
          double centre = x + (right - left) / columns / 2.0;
          String label = Integer.toString(column + 1);
          gridText(centre, 7.2, label);
          gridText(centre, HEIGHT - 3.2, label);
        }
      }
      for (int row = 0; row <= rows.length(); row++) {
        double y = top + (bottom - top) * row / rows.length();
        if (row > 0 && row < rows.length()) {
          page.commands.add(Command.line(left - 5, y, left, y, "#111111", .35, "grid", ""));
          page.commands.add(Command.line(right, y, right + 5, y, "#111111", .35, "grid", ""));
        }
        if (row < rows.length()) {
          double centre = y + (bottom - top) / rows.length() / 2.0 + 1.2;
          String label = String.valueOf(rows.charAt(row));
          gridText(12.5, centre, label);
          gridText(WIDTH - 5.0, centre, label);
        }
      }
      double middleX = WIDTH / 2.0;
      double middleY = HEIGHT / 2.0;
      page.commands.add(Command.line(middleX, 0, middleX, top + 5, "#111111", .7, "centring", ""));
      page.commands.add(Command.line(middleX, bottom - 5, middleX, HEIGHT, "#111111", .7, "centring", ""));
      page.commands.add(Command.line(0, middleY, left + 5, middleY, "#111111", .7, "centring", ""));
      page.commands.add(Command.line(right - 5, middleY, WIDTH, middleY, "#111111", .7, "centring", ""));
    }

    /**
     * Adds one grid reference character in the sheet border, outside the layout label register.
     *
     * @param x centre x in millimetres
     * @param y baseline y in millimetres
     * @param value grid character
     */
    private void gridText(double x, double y, String value) {
      page.commands.add(Command.text(x, y, 3.5, value, "#111111", "grid:" + value, "middle"));
    }

    /**
     * Draws the title strip: revision table, drawing notes area and the ISO 7200 identification block (180 mm wide,
     * bottom right) with legal owner, document type, title, document number, revision, sheet number of total, date of
     * issue, document status, language, creator and approver.
     *
     * @param id controlled sheet identity
     * @param title sheet title
     * @param number sheet number
     * @param kind pid or pfd
     */
    private void titleBlock(String id, String title, String number, String kind) {
      double top = 535.0;
      double bottom = HEIGHT - 10.0;
      double right = WIDTH - 10.0;
      double block = right - 180.0;
      page.commands.add(Command.rect(20, top, right - 20, bottom - top, "#111111", "none", .5, "title-block", ""));
      page.commands.add(Command.line(block, top, block, bottom, "#111111", .5, "title-block", ""));
      page.commands.add(Command.line(250, top, 250, bottom, "#111111", .35, "title-block", ""));
      // Revision table (latest revision at the bottom, as read upward from the title block).
      double[] columns = {20, 34, 68, 192, 211, 230, 250};
      String[] heads = {"REV", "DATE", "DESCRIPTION", "PREP", "CHK", "APPR"};
      double headY = bottom - 8.0;
      page.commands.add(Command.line(20, headY, 250, headY, "#111111", .35, "title-block", ""));
      page.commands.add(Command.line(20, headY - 9, 250, headY - 9, "#111111", .25, "title-block", ""));
      for (int index = 0; index < columns.length; index++) {
        if (index > 0 && index < columns.length - 1) {
          page.commands.add(Command.line(columns[index], headY - 9, columns[index], bottom, "#111111", .25,
              "title-block", ""));
        }
        if (index < heads.length) {
          text(columns[index] + 1.5, bottom - 2.8, heads[index], 2.5, null);
        }
      }
      String date = issuedAt.length() >= 10 ? issuedAt.substring(0, 10) : issuedAt;
      text(21.5, headY - 3.2, project.getRevision(), 2.5, null);
      text(35.5, headY - 3.2, date, 2.5, null);
      text(69.5, headY - 3.2, "ISSUED FOR REVIEW - ENGINEERING PROPOSAL", 2.5, null);
      text(193.5, headY - 3.2, "NSIM", 2.5, null);
      text(212.5, headY - 3.2, "HOLD", 2.5, null);
      text(231.5, headY - 3.2, "HOLD", 2.5, null);
      text(21.5, top + 5.5, "REVISIONS", 3, null);
      // Drawing information and review status.
      text(253, top + 5.5, "NOTES", 3, null);
      text(253, top + 13, "1. ALL DIMENSIONS IN MM UNLESS NOTED. NOT TO SCALE.", 2.5, null);
      text(253, top + 19, "2. HOLD = PROJECT INPUT REQUIRED. PREL. = PRELIMINARY SIZE.", 2.5, null);
      text(253, top + 25, "3. SYMBOLS: ISO 10628-2 / ISA-5.1, SEE SYMBOL CONVENTIONS REPORT.", 2.5, null);
      text(253, top + 31, "4. SOURCE MODEL " + documents.getSourceGraphFingerprint().substring(0, 16), 2.5, null);
      text(253, top + 37, "5. SYMBOL REVIEW / ISO CONFORMANCE: HOLD", 2.5, null);
      // ISO 7200 identification block.
      double x = block + 2.0;
      double row = 8.2;
      for (int line = 1; line < 6; line++) {
        page.commands.add(
            Command.line(block, top + row * line, right, top + row * line, "#111111", .25, "title-block", ""));
      }
      text(x, top + 5.5, "OWNER: HOLD", 2.5, null);
      text(x + 90, top + 5.5, "DOC. TYPE: " + ("pfd".equals(kind) ? "PFD" : "P&ID"), 2.5, null);
      text(x, top + row + 5.8, title.length() > 52 ? title.substring(0, 51) + "." : title, 3.5, null);
      text(x, top + 2 * row + 5.5, "DOC. NO: " + id, 2.5, null);
      text(x + 95, top + 2 * row + 5.5, "PROJECT: " + project.getProjectId(), 2.5, null);
      text(x, top + 3 * row + 5.5, "REV: " + project.getRevision(), 2.5, null);
      text(x + 45, top + 3 * row + 5.5, "SHEET: " + number + " OF " + SHEET_TOTAL_TOKEN, 2.5, null);
      text(x + 110, top + 3 * row + 5.5, "LANG: EN", 2.5, null);
      text(x, top + 4 * row + 5.5, "ISSUED: " + date, 2.5, null);
      text(x + 60, top + 4 * row + 5.5, "STATUS: WORKING - NOT FOR CONSTRUCTION", 2.5, null);
      text(x, top + 5 * row + 5.5, "CREATED: NEQSIM GENERATOR", 2.5, null);
      text(x + 90, top + 5 * row + 5.5, "APPROVED: HOLD", 2.5, null);
    }

    /**
     * Draws a same-sheet signal directly as a dashed orthogonal line.
     *
     * <p>
     * A final control element is entered vertically at its actuator; a function is entered from the left. Each direct
     * signal uses its own lane so parallel signals never overlap.
     * </p>
     *
     * @param source signal source symbol
     * @param target signal target symbol
     * @param id stable signal identity
     * @return true when drawn; false when no clear orthogonal route exists and a continuation must be used
     */
    boolean directSignal(Node source, Node target, String id) {
      Point start = source.right();
      int lane = directSignalLanes;
      boolean topEntry = !isValveFamily(target.family) && target.left().x - start.x < 8 && target.y > source.y + 10;
      if (isValveFamily(target.family) || topEntry) {
        Point end = target.top();
        double joinY = Double.NaN;
        for (Map<String, Object> edge : edges) {
          @SuppressWarnings("unchecked")
          List<double[]> existing = (List<double[]>) edge.get("points");
          double[] last = existing.get(existing.size() - 1);
          if (target.tag.equals(edge.get("to")) && "signal".equals(edge.get("fluid")) && existing.size() >= 2
              && Math.abs(last[0] - end.x) < 1e-6 && Math.abs(last[1] - end.y) < 1e-6) {
            double[] before = existing.get(existing.size() - 2);
            if (Math.abs(before[0] - end.x) < 1e-6) {
              joinY = before[1];
            }
          }
        }
        for (int attempt = 0; attempt < 16; attempt++) {
          double laneX = start.x + 4 + 3 * lane + 5 * attempt;
          if (laneX > WIDTH - 30) {
            break;
          }
          double laneY = topEntry ? end.y - 6 - 2 * lane : Math.min(end.y - 8 - 3 * lane, start.y - 4);
          for (int guard = 0; guard < 60; guard++) {
            double[] hit = firstHit(new Point(Math.min(laneX, end.x), laneY), new Point(Math.max(laneX, end.x), laneY),
                source.tag, target.tag);
            if (hit == null) {
              break;
            }
            laneY = hit[1] - 4 - 3 * lane;
          }
          if (laneY < 68 || topEntry && laneY <= start.y + 2) {
            break;
          }
          boolean join = !Double.isNaN(joinY);
          Point last = join ? new Point(end.x, Math.max(laneY, joinY)) : end;
          List<Point> path = points(start, new Point(laneX, start.y), new Point(laneX, laneY), new Point(end.x, laneY),
              last);
          if (clear(path, source.tag, target.tag)) {
            directSignalLanes++;
            if (join) {
              // Signals to the same final element join the existing signal line at a junction dot.
              junctions.add(new double[] {last.x, last.y});
              List<Point> dot = new ArrayList<Point>();
              for (int index = 0; index <= 16; index++) {
                dot.add(new Point(last.x + .7 * Math.cos(index * Math.PI / 8),
                    last.y + .7 * Math.sin(index * Math.PI / 8)));
              }
              page.commands.add(Command.polygon(dot, "#111111", "#111111", .2, id + ":junction"));
            }
            strokeBroken(path, source.tag, target.tag, id, !join);
            return true;
          }
        }
        return false;
      }
      Point end = target.left();
      for (int attempt = 0; attempt < 8; attempt++) {
        double midX = start.x + 4 + 3 * lane + 4 * attempt;
        if (midX > end.x - 3) {
          break;
        }
        List<Point> path = points(start, new Point(midX, start.y), new Point(midX, end.y), end);
        if (clear(path, source.tag, target.tag)) {
          directSignalLanes++;
          strokeBroken(path, source.tag, target.tag, id);
          return true;
        }
      }
      return false;
    }

    /**
     * Returns true when no segment of an orthogonal path crosses a symbol or text envelope other than its endpoints.
     *
     * @param path orthogonal path
     * @param sourceTag source symbol tag to ignore
     * @param targetTag target symbol tag to ignore
     * @return true when the path is clear
     */
    @SuppressWarnings("unchecked")
    private boolean clear(List<Point> path, String sourceTag, String targetTag) {
      for (int index = 0; index + 1 < path.size(); index++) {
        Point a = path.get(index);
        Point b = path.get(index + 1);
        if (firstHit(a, b, sourceTag, targetTag) != null) {
          return false;
        }
        double[] p0 = {a.x, a.y};
        double[] p1 = {b.x, b.y};
        for (Map<String, Object> edge : edges) {
          List<double[]> points = (List<double[]>) edge.get("points");
          for (int segment = 1; segment < points.size(); segment++) {
            if (collinearOverlap(p0, p1, points.get(segment - 1), points.get(segment))
                && !sourceTag.equals(edge.get("from")) && !targetTag.equals(edge.get("to"))) {
              return false;
            }
            double[] hit = crossing(p0, p1, points.get(segment - 1), points.get(segment));
            if (hit != null) {
              double distance = Math.hypot(hit[0] - a.x, hit[1] - a.y);
              double length = Math.hypot(b.x - a.x, b.y - a.y);
              if (distance < 2.5 || distance > length - 2.5) {
                return false; // too close to a bend to show a clear line break
              }
            }
          }
        }
      }
      return true;
    }

    /**
     * Returns true when two axis-aligned segments run along the same line and share more than a point.
     *
     * @param a first segment start
     * @param b first segment end
     * @param c second segment start
     * @param d second segment end
     * @return true for a collinear overlap
     */
    private boolean collinearOverlap(double[] a, double[] b, double[] c, double[] d) {
      boolean horizontal = Math.abs(a[1] - b[1]) < 1e-6 && Math.abs(c[1] - d[1]) < 1e-6
          && Math.abs(a[1] - c[1]) < .8;
      boolean vertical = Math.abs(a[0] - b[0]) < 1e-6 && Math.abs(c[0] - d[0]) < 1e-6 && Math.abs(a[0] - c[0]) < .8;
      if (horizontal) {
        return Math.min(Math.max(a[0], b[0]), Math.max(c[0], d[0]))
            - Math.max(Math.min(a[0], b[0]), Math.min(c[0], d[0])) > .5;
      }
      if (vertical) {
        return Math.min(Math.max(a[1], b[1]), Math.max(c[1], d[1]))
            - Math.max(Math.min(a[1], b[1]), Math.min(c[1], d[1])) > .5;
      }
      return false;
    }

    /**
     * Finds the first symbol or text envelope crossed by an axis-aligned segment.
     *
     * @param a segment start
     * @param b segment end
     * @param sourceTag tag to ignore
     * @param targetTag tag to ignore
     * @return the crossed envelope {x, y, width, height}, or null
     */
    private double[] firstHit(Point a, Point b, String sourceTag, String targetTag) {
      double minX = Math.min(a.x, b.x);
      double maxX = Math.max(a.x, b.x);
      double minY = Math.min(a.y, b.y);
      double maxY = Math.max(a.y, b.y);
      List<double[]> boxes = new ArrayList<double[]>();
      for (Map<String, Object> node : nodes) {
        Object tag = node.get("tag");
        if (!sourceTag.equals(tag) && !targetTag.equals(tag)) {
          boxes.add((double[]) node.get("bbox"));
        }
      }
      for (Map<String, Object> label : labels) {
        Object owner = label.get("owner");
        if (!sourceTag.equals(owner) && !targetTag.equals(owner)) {
          boxes.add((double[]) label.get("bbox"));
        }
      }
      for (Object[] zone : reserved) {
        if (!sourceTag.equals(zone[0]) && !targetTag.equals(zone[0])) {
          boxes.add((double[]) zone[1]);
        }
      }
      for (double[] box : boxes) {
        if (maxX >= box[0] - 1 && minX <= box[0] + box[2] + 1 && maxY >= box[1] - 1
            && minY <= box[1] + box[3] + 1) {
          return box;
        }
      }
      return null;
    }

    /**
     * Places the equipment data callout in the first free position above the drawing content, so it never overlaps a
     * symbol, text or route placed by the sheet template.
     */
    private void placeCallout() {
      if (callout == null) {
        return;
      }
      double[] offsets = {0, -90, 90, -180, 180, -270, 270};
      double[] tops = {64, 82, 100};
      double chosenX = calloutX;
      double chosenY = tops[0];
      search: for (double top : tops) {
        for (double offset : offsets) {
          double x = calloutX + offset;
          boolean free = x > 120 && x < WIDTH - 130;
          for (int line = 0; free && line < callout.size(); line++) {
            double size = Double.parseDouble(callout.get(line)[1]);
            double width = callout.get(line)[0].length() * size * .52;
            double[] box = {x - width / 2 - 1, top + 5 * line - size * .6 - 1, width + 2, size * 1.2 + 2};
            free = firstHit(new Point(box[0], box[1]), new Point(box[0] + box[2], box[1]), "", "") == null
                && firstHit(new Point(box[0], box[1] + box[3]), new Point(box[0] + box[2], box[1] + box[3]), "", "")
                    == null
                && firstHit(new Point(box[0], box[1]), new Point(box[0], box[1] + box[3]), "", "") == null
                && !routeInBox(box) && !boxCoversNode(box);
          }
          if (free) {
            chosenX = x;
            chosenY = top;
            break search;
          }
        }
      }
      for (int line = 0; line < callout.size(); line++) {
        centered(chosenX, chosenY + 5 * line, callout.get(line)[0], Double.parseDouble(callout.get(line)[1]), null);
      }
      callout = null;
    }

    /**
     * Returns true when a box overlaps any symbol envelope.
     *
     * @param box {x, y, width, height}
     * @return true when a symbol is covered
     */
    private boolean boxCoversNode(double[] box) {
      for (Map<String, Object> node : nodes) {
        double[] b = (double[]) node.get("bbox");
        if (box[0] < b[0] + b[2] && box[0] + box[2] > b[0] && box[1] < b[1] + b[3] && box[1] + box[3] > b[1]) {
          return true;
        }
      }
      return false;
    }

    /** Finishes one controlled page without discarding unsupported scope. */
    void finish() {
      placeCallout();
      resolveSignals(this);
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
