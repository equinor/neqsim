package neqsim.process.processmodel.diagram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidSimulationBindings;
import neqsim.process.engineering.scd.ScdDesignModel;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Command;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Diagnostic;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Page;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Point;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Severity;

/**
 * A3 System Control Diagram function sheets using the native PFD/P&amp;ID SVG/PDF scene writers.
 *
 * <p>
 * Named signal continuations preserve arbitrary feedback and cross-equipment graphs without ambiguous wire crossings.
 * Each continuation appears at both ends with the same signal identity. Equipment/line context, project system
 * assignment, template code and terminal designations come from the shared source model.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class ScdDiagramRenderer {
  private final NativeEngineeringDiagramRenderer serializer;
  private final ScdDesignModel model;
  private final Map<String, String> layoutSpecifications = new LinkedHashMap<String, String>();

  /**
   * Creates a renderer with the same source identities used by DEXPI and the P&amp;ID.
   *
   * @param project source project
   * @param pid source P&amp;ID
   * @param bindings actual source bindings
   * @param model retained SCD projection
   */
  public ScdDiagramRenderer(EngineeringProject project, PidDesignModel pid, PidSimulationBindings bindings,
      ScdDesignModel model) {
    if (project == null || pid == null || bindings == null || model == null) {
      throw new IllegalArgumentException("project, P&ID, bindings and SCD are required");
    }
    model.assertCurrent(project, pid, bindings);
    this.model = model;
    this.serializer = new NativeEngineeringDiagramRenderer(
        ProcessDiagramDocumentSetAdapter.fromProcessSystem(project.getEngineeringProcessSystem(),
            project.getProjectId(), project.getRevision(), model.getDrawingNumber(), "System Control Diagram",
            ContentProfile.PID),
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL);
  }

  /**
   * Renders the detached constructor-time snapshot of every block and both ends of every functional connection.
   *
   * @return deterministic native SVG/PDF and actual text/coverage diagnostics
   */
  public NativeEngineeringDiagramRenderer.Result render() {
    layoutSpecifications.clear();
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    List<Page> pages = new ArrayList<Page>();
    List<Map<String, String>> signals = model.getSignals();
    List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
    List<Map<String, Object>> edges = new ArrayList<Map<String, Object>>();
    int totalSlots = 0;
    for (PidElement block : model.getBlocks()) {
      totalSlots += Math.max(1, (Math.max(connections(signals, "targetTag", block.getTag()).size(),
          connections(signals, "sourceTag", block.getTag()).size()) + 2) / 3);
    }
    int slot = 0;
    int pageRows = 0;
    Page page = null;
    for (PidElement block : model.getBlocks()) {
      List<Map<String, String>> incoming = connections(signals, "targetTag", block.getTag());
      List<Map<String, String>> outgoing = connections(signals, "sourceTag", block.getTag());
      int chunks = Math.max(1, (Math.max(incoming.size(), outgoing.size()) + 2) / 3);
      for (int chunk = 0; chunk < chunks; chunk++) {
        if (slot % 4 == 0) {
          if (page != null) {
            retainLayout(page, nodes, edges);
          }
          nodes = new ArrayList<Map<String, Object>>();
          edges = new ArrayList<Map<String, Object>>();
          page = new Page(model.getDrawingNumber() + ":" + (pages.size() + 1), 420.0, 297.0);
          pages.add(page);
          frame(page, pages.size(), diagnostics);
          pageRows = Math.min(4, totalSlots - slot);
        }
        double y = 43.0 + (4 - pageRows) * 25.5 + (slot % 4) * 51.0;
        String id = block.getId() + ":" + chunk;
        String context = "Equipment: " + block.getEquipmentTag() + "  Line: " + block.getLineTag() + "  System: "
            + textOrHold(ScdDesignModel.attribute(block, "system"));
        text(page, 20.0, y, 2.3, context, id, "start", 380.0, diagnostics);
        node(page, nodes, id, block.getTag() + (chunk == 0 ? "" : " / " + (chunk + 1)), 160.0, y + 8.0, 96.0, 35.0);
        text(page, 208.0, y + 15.0, 2.8, block.getTag(), id, "middle", 90.0, diagnostics);
        text(page, 208.0, y + 25.0, 4.0, ScdDesignModel.template(block), id, "middle", 90.0, diagnostics);
        text(page, 208.0, y + 35.0, 2.0, block.getType().name(), id, "middle", 90.0, diagnostics);
        ports(page, nodes, edges, incoming, chunk, id, y, true, diagnostics);
        ports(page, nodes, edges, outgoing, chunk, id, y, false, diagnostics);
        slot++;
      }
    }
    if (page == null) {
      throw new IllegalArgumentException("SCD has no supported functional blocks");
    }
    retainLayout(page, nodes, edges);
    return serializer.renderScenes(pages, diagnostics, Arrays.asList("CAPTURED_PID_AND_BINDING_IDENTITY",
        "SHARED_FUNCTIONAL_LINK_ENDPOINTS", "ALL_BLOCKS_AND_BOTH_SIGNAL_ENDS", "CONSERVATIVE_TEXT_WIDTH"));
  }

  /** @return detached sheet-to-layout JSON for the existing PFD/P&amp;ID layout checker */
  public Map<String, String> getLayoutSpecifications() {
    return new LinkedHashMap<String, String>(layoutSpecifications);
  }

  /**
   * Selects directed connections without replacing them with equipment proximity or inferred logic.
   *
   * @param signals shared source signals
   * @param key endpoint field
   * @param tag endpoint tag
   * @return ordered matches
   */
  private static List<Map<String, String>> connections(List<Map<String, String>> signals, String key, String tag) {
    List<Map<String, String>> result = new ArrayList<Map<String, String>>();
    for (Map<String, String> signal : signals) {
      if (tag.equals(signal.get(key))) {
        result.add(signal);
      }
    }
    return result;
  }

  /**
   * Draws one chunk of explicit signal terminals and paired reference connectors.
   *
   * @param page native scene
   * @param nodes layout nodes
   * @param edges layout edges
   * @param signals connections for this block side
   * @param chunk continuation chunk
   * @param blockId represented block ID
   * @param y row origin
   * @param incoming true for destination-side connectors
   * @param diagnostics text-size findings
   */
  private static void ports(Page page, List<Map<String, Object>> nodes, List<Map<String, Object>> edges,
      List<Map<String, String>> signals, int chunk, String blockId, double y, boolean incoming,
      List<Diagnostic> diagnostics) {
    if (chunk * 3 >= signals.size()) {
      String id = blockId + (incoming ? ":unused-in" : ":unused-out");
      double x = incoming ? 20.0 : 295.0;
      node(page, nodes, id, id, x, y + 10.0, 105.0, 8.0);
      nodes.get(nodes.size() - 1).put("kind", "note");
      text(page, x + 3.0, y + 14.0, 2.3, incoming ? "No further declared inputs" : "No further declared outputs", id,
          "start", 100.0, diagnostics);
    }
    for (int index = chunk * 3; index < Math.min(signals.size(), chunk * 3 + 3); index++) {
      Map<String, String> signal = signals.get(index);
      double portY = y + 14.0 + (index % 3) * 11.0;
      String id = signal.get("id") + (incoming ? ":in" : ":out");
      double connectorX = incoming ? 20.0 : 295.0;
      node(page, nodes, id, id, connectorX, portY - 4.0, 105.0, 8.0);
      String peer = signal.get(incoming ? "sourceTag" : "targetTag");
      text(page, connectorX + 3.0, portY, 2.3, signal.get("id") + (incoming ? " from " : " to ") + peer, id, "start",
          100.0, diagnostics);
      double start = incoming ? 125.0 : 256.0;
      double end = incoming ? 160.0 : 295.0;
      String dash = "BINARY".equals(signal.get("signalType")) ? "1,1" : "";
      page.commands.add(Command.line(start, portY, end, portY, "#111111", .3, id, dash));
      page.commands.add(Command.polygon(
          Arrays.asList(new Point(end - 2.0, portY - .9), new Point(end, portY), new Point(end - 2.0, portY + .9)),
          "#111111", "#111111", .2, id));
      text(page, (start + end) / 2.0, portY - 3.0, 2.0,
          textOrHold(signal.get(incoming ? "targetTerminal" : "sourceTerminal"))
              + ("HOLD".equals(signal.get("signalType")) ? "/HOLD" : ""),
          id + ":terminal", "middle", end - start - 2.0, diagnostics);
      Map<String, Object> edge = new LinkedHashMap<String, Object>();
      edge.put("id", id + ":wire");
      edge.put("from", incoming ? id : blockId);
      edge.put("to", incoming ? blockId : id);
      edge.put("points", Arrays.asList(Arrays.asList(start, portY), Arrays.asList(end, portY)));
      edge.put("arrow_end", Boolean.TRUE);
      edge.put("role", "signal");
      edge.put("stream", signal.get("id"));
      Map<String, Object> style = new LinkedHashMap<String, Object>();
      style.put("linestyle", dash.isEmpty() ? "solid" : "dashed");
      edge.put("style", style);
      edges.add(edge);
    }
  }

  /**
   * Adds a symbol envelope to both the vector scene and the exact layout sidecar.
   *
   * @param page native page
   * @param nodes layout nodes
   * @param id semantic representation ID
   * @param tag display reference
   * @param x left position
   * @param y top position
   * @param width envelope width
   * @param height envelope height
   */
  private static void node(Page page, List<Map<String, Object>> nodes, String id, String tag, double x, double y,
      double width, double height) {
    page.commands.add(Command.rect(x, y, width, height, "#111111", "#ffffff", .3, id, ""));
    Map<String, Object> node = new LinkedHashMap<String, Object>();
    node.put("id", id);
    node.put("kind", "block");
    node.put("tag", tag);
    node.put("bbox", Arrays.asList(x, y, width, height));
    nodes.add(node);
  }

  /**
   * Creates an A3 frame, controlled title and an explicit conventions/review legend.
   *
   * @param page native page
   * @param sheet sheet number
   * @param diagnostics text-size findings
   */
  private void frame(Page page, int sheet, List<Diagnostic> diagnostics) {
    page.commands.add(Command.rect(10.0, 8.0, 400.0, 281.0, "#111111", "none", .4, "", ""));
    text(page, 20.0, 17.0, 4.0, "SYSTEM CONTROL DIAGRAM", "title", "start", 380.0, diagnostics);
    text(page, 20.0, 26.0, 2.7, model.getDrawingNumber() + " / " + sheet + "   Revision " + model.getRevision(),
        "drawing", "start", 380.0, diagnostics);
    text(page, 20.0, 35.0, 2.2, "SIGNALS IN", "", "start", 100.0, diagnostics);
    text(page, 160.0, 35.0, 2.2, "APPLICATION FUNCTION", "", "start", 96.0, diagnostics);
    text(page, 295.0, 35.0, 2.2, "SIGNALS OUT", "", "start", 105.0, diagnostics);
    text(page, 20.0, 258.0, 2.5, "Drawing basis: " + model.getStandard().getReference() + " (legacy selected edition)",
        "basis", "start", 380.0, diagnostics);
    text(page, 20.0, 266.0, 2.3,
        "Same S-number at both ends = signal continuation. Dashed = project-declared binary signal.", "legend", "start",
        380.0, diagnostics);
    text(page, 20.0, 274.0, 2.3, "HOLD = missing project input. PROJECT = project function, not a standard template.",
        "legend", "start", 380.0, diagnostics);
    text(page, 20.0, 282.0, 2.3,
        "REVIEW REQUIRED: controlled standard, templates, terminal semantics and safety lifecycle. No construction approval.",
        "review", "start", 380.0, diagnostics);
  }

  /**
   * Retains the exact geometry consumed by the repository layout-review tool.
   *
   * @param page completed native page
   * @param nodes symbol envelopes
   * @param edges directed wire segments
   */
  private void retainLayout(Page page, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
    Map<String, Object> layout = new LinkedHashMap<String, Object>();
    layout.put("schema", "neqsim.pfd_layout.v1");
    Map<String, Object> canvas = new LinkedHashMap<String, Object>();
    canvas.put("width", page.width);
    canvas.put("height", page.height);
    canvas.put("unit", "mm");
    canvas.put("intended_width_mm", page.width);
    layout.put("canvas", canvas);
    layout.put("drawing_area", Arrays.asList(15.0, 40.0, 390.0, 207.0));
    layout.put("nodes", nodes);
    layout.put("edges", edges);
    List<Map<String, Object>> labels = new ArrayList<Map<String, Object>>();
    for (Command command : page.commands) {
      if (!"text".equals(command.type)) {
        continue;
      }
      double width = command.text.length() * command.size * .6;
      double x = command.x
          - ("middle".equals(command.anchor) ? width / 2.0 : "end".equals(command.anchor) ? width : 0.0);
      Map<String, Object> label = new LinkedHashMap<String, Object>();
      label.put("text", command.text);
      label.put("bbox", Arrays.asList(x, command.y - command.size / 2.0, width, command.size));
      label.put("font_size", command.size);
      boolean attached = false;
      for (Map<String, Object> node : nodes) {
        if (command.id.equals(node.get("id")) && !command.text.startsWith("Equipment: ")) {
          @SuppressWarnings("unchecked")
          List<Map<String, Object>> texts = (List<Map<String, Object>>) node.get("texts");
          if (texts == null) {
            texts = new ArrayList<Map<String, Object>>();
            node.put("texts", texts);
          }
          texts.add(label);
          attached = true;
          break;
        }
      }
      if (!attached) {
        if (!command.id.endsWith(":terminal") && !command.text.startsWith("Equipment: ")) {
          label.put("kind", "annotation");
        }
        labels.add(label);
      }
    }
    layout.put("labels", labels);
    layout.put("legend", "Same S-number identifies paired continuations; dashed = project-declared binary");
    layout.put("footer", "Review required; standard conformance not assessed");
    layout.put("title", "System Control Diagram");
    layout.put("flow_direction", "LR");
    layoutSpecifications.put(page.sheetId, new GsonBuilder().setPrettyPrinting().create().toJson(layout));
  }

  /**
   * Adds native escaped text and rejects a conservatively estimated overflow.
   *
   * @param page native page
   * @param x anchor x
   * @param y baseline y
   * @param size font size in millimetres
   * @param value visible text
   * @param id source identity
   * @param anchor text alignment
   * @param width available width
   * @param diagnostics findings
   */
  private static void text(Page page, double x, double y, double size, String value, String id, String anchor,
      double width, List<Diagnostic> diagnostics) {
    if (value.length() * size * .6 > width) {
      diagnostics.add(NativeEngineeringDiagramRenderer.sceneDiagnostic(Severity.ERROR, "SCD_TEXT_OVERFLOW",
          "Text exceeds its allocated width: " + value, id));
    }
    page.commands.add(Command.text(x, y, size, value, "#111111", id, anchor));
  }

  /**
   * Makes unresolved input visible without supplying a value.
   *
   * @param value optional text
   * @return value or HOLD marker
   */
  private static String textOrHold(String value) {
    return value == null || value.isEmpty() ? "HOLD" : value;
  }
}
