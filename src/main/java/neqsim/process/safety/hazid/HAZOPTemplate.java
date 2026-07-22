package neqsim.process.safety.hazid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Hazard and Operability (HAZOP) study template — applies guide-words to process variables for a single node and stores
 * deviations, causes, consequences, safeguards and recommendations.
 *
 * <p>
 * Standard HAZOP guide-words: NO, MORE, LESS, REVERSE, AS_WELL_AS, PART_OF, OTHER_THAN. Standard parameters: FLOW,
 * PRESSURE, TEMPERATURE, LEVEL, COMPOSITION, REACTION.
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>IEC 61882 — HAZOP studies application guide</li>
 * <li>CCPS — Guidelines for Hazard Evaluation Procedures, 3rd Ed.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class HAZOPTemplate implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Standard HAZOP guide-words. */
  public enum GuideWord {
    /** Negation of design intent. */
    NO,
    /** Quantitative increase. */
    MORE,
    /** Quantitative decrease. */
    LESS,
    /** Logical opposite. */
    REVERSE,
    /** Qualitative increase (extra contamination). */
    AS_WELL_AS,
    /** Qualitative decrease (only some component). */
    PART_OF,
    /** Complete substitution. */
    OTHER_THAN
  }

  /** Standard HAZOP process parameters. */
  public enum Parameter {
    /** Volumetric or mass flow. */
    FLOW,
    /** Pressure. */
    PRESSURE,
    /** Temperature. */
    TEMPERATURE,
    /** Level. */
    LEVEL,
    /** Composition. */
    COMPOSITION,
    /** Reaction (rate, completion). */
    REACTION
  }

  private final String nodeId;
  private final String designIntent;
  private final List<HAZOPDeviation> deviations = new ArrayList<>();

  /**
   * Construct a HAZOP node template.
   *
   * @param nodeId HAZOP node identifier (e.g. "Node-12: Inlet line to V-100")
   * @param designIntent narrative of the design intent for the node
   */
  public HAZOPTemplate(String nodeId, String designIntent) {
    this.nodeId = nodeId;
    this.designIntent = designIntent;
  }

  /**
   * Add a deviation row to the worksheet.
   *
   * @param guideWord guide-word
   * @param parameter parameter
   * @param cause hypothesised cause
   * @param consequence consequence if no protection acts
   * @param safeguard existing safeguard (instrumentation, procedural, mechanical)
   * @param recommendation new recommendation (or null)
   * @return this template for chaining
   */
  public HAZOPTemplate addDeviation(GuideWord guideWord, Parameter parameter, String cause, String consequence,
      String safeguard, String recommendation) {
    deviations.add(new HAZOPDeviation(guideWord, parameter, cause, consequence, safeguard, recommendation));
    return this;
  }

  /**
   * Generate the full default deviation grid (all guide-word × parameter combinations) with empty causes/consequences
   * for the user to fill in.
   *
   * @param parameters list of parameters to include
   * @return this template for chaining
   */
  public HAZOPTemplate generateGrid(Parameter... parameters) {
    for (Parameter p : parameters) {
      for (GuideWord g : GuideWord.values()) {
        deviations.add(new HAZOPDeviation(g, p, "TBD", "TBD", "TBD", null));
      }
    }
    return this;
  }

  /**
   * @return immutable view of stored deviations
   */
  public List<HAZOPDeviation> getDeviations() {
    return new ArrayList<>(deviations);
  }

  /**
   * @return the HAZOP node identifier
   */
  public String getNodeId() {
    return nodeId;
  }

  /**
   * @return the node design intent
   */
  public String getDesignIntent() {
    return designIntent;
  }

  /**
   * Build a multi-line text report of all deviations.
   *
   * @return human-readable HAZOP worksheet
   */
  public String report() {
    StringBuilder sb = new StringBuilder();
    sb.append("HAZOP node: ").append(nodeId).append('\n');
    sb.append("Design intent: ").append(designIntent).append('\n');
    sb.append("---------------------------------------------------\n");
    int row = 1;
    for (HAZOPDeviation d : deviations) {
      sb.append(String.format("[%02d] %s + %s%n", row, d.guideWord, d.parameter));
      sb.append("     Cause       : ").append(d.cause).append('\n');
      sb.append("     Consequence : ").append(d.consequence).append('\n');
      sb.append("     Safeguard   : ").append(d.safeguard).append('\n');
      if (d.recommendation != null) {
        sb.append("     Recommend.  : ").append(d.recommendation).append('\n');
      }
      row++;
    }
    return sb.toString();
  }

  /**
   * One row of a HAZOP worksheet.
   */
  public static class HAZOPDeviation implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Guide-word. */
    public final GuideWord guideWord;
    /** Parameter. */
    public final Parameter parameter;
    /** Cause. */
    public final String cause;
    /** Consequence. */
    public final String consequence;
    /** Existing safeguard. */
    public final String safeguard;
    /** Recommendation (may be null). */
    public final String recommendation;

    /**
     * @param guideWord guide-word
     * @param parameter process parameter
     * @param cause cause description
     * @param consequence consequence description
     * @param safeguard existing safeguard
     * @param recommendation new recommendation (or null)
     */
    public HAZOPDeviation(GuideWord guideWord, Parameter parameter, String cause, String consequence, String safeguard,
        String recommendation) {
      this.guideWord = guideWord;
      this.parameter = parameter;
      this.cause = cause;
      this.consequence = consequence;
      this.safeguard = safeguard;
      this.recommendation = recommendation;
    }
  }

  /**
   * Convenience getter for all standard parameters.
   *
   * @return list of all standard HAZOP parameters
   */
  public static List<Parameter> standardParameters() {
    return Arrays.asList(Parameter.values());
  }

  /**
   * Build a first-pass HAZOP node list directly from a process flowsheet topology.
   *
   * <p>
   * One HAZOP node is created per unit operation. The design intent narrative references the inlet and outlet stream
   * names, and an equipment-type-specific set of guide-word/parameter deviations is seeded with "TBD" placeholder
   * causes, consequences and safeguards. The result is intended as a <b>screening / preparation</b> artefact that a
   * HAZOP facilitator completes and a competent team reviews; it is not a finished HAZOP.
   * </p>
   *
   * @param processSystem the flowsheet to walk (must not be null)
   * @return one {@link HAZOPTemplate} node per unit operation, in flowsheet order
   * @throws IllegalArgumentException if {@code processSystem} is null
   */
  public static List<HAZOPTemplate> fromProcessSystem(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    List<HAZOPTemplate> nodes = new ArrayList<>();
    int index = 1;
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit == null) {
        continue;
      }
      String name = unit.getName();
      String type = unit.getClass().getSimpleName();
      String nodeId = String.format(Locale.ROOT, "Node-%02d: %s (%s)", index, name, type);
      HAZOPTemplate node = new HAZOPTemplate(nodeId, buildDesignIntent(unit, name));
      seedDeviations(node, type);
      nodes.add(node);
      index++;
    }
    return nodes;
  }

  /**
   * Build a short design-intent narrative referencing the unit inlet and outlet stream names.
   *
   * @param unit the unit operation
   * @param name the unit name
   * @return a one-line design intent string
   */
  private static String buildDesignIntent(ProcessEquipmentInterface unit, String name) {
    String inlets = streamNames(safeStreams(unit, true));
    String outlets = streamNames(safeStreams(unit, false));
    StringBuilder sb = new StringBuilder();
    sb.append("Normal operation of ").append(name);
    if (!inlets.isEmpty()) {
      sb.append("; inlet(s): ").append(inlets);
    }
    if (!outlets.isEmpty()) {
      sb.append("; outlet(s): ").append(outlets);
    }
    return sb.toString();
  }

  /**
   * Read the inlet or outlet streams of a unit, tolerating equipment that throws while resolving streams.
   *
   * @param unit the unit operation
   * @param inlet true for inlet streams, false for outlet streams
   * @return the resolved streams, or an empty list if none are available or resolution fails
   */
  private static List<StreamInterface> safeStreams(ProcessEquipmentInterface unit, boolean inlet) {
    try {
      List<StreamInterface> streams = inlet ? unit.getInletStreams() : unit.getOutletStreams();
      if (streams == null) {
        return new ArrayList<>();
      }
      return streams;
    } catch (RuntimeException ex) {
      return new ArrayList<>();
    }
  }

  /**
   * Join the non-empty names of a list of streams into a comma-separated string.
   *
   * @param streams the streams to name
   * @return a comma-separated list of stream names (possibly empty)
   */
  private static String streamNames(List<StreamInterface> streams) {
    StringBuilder sb = new StringBuilder();
    for (StreamInterface stream : streams) {
      if (stream == null) {
        continue;
      }
      String streamName = stream.getName();
      if (streamName == null || streamName.trim().isEmpty()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(streamName);
    }
    return sb.toString();
  }

  /**
   * Seed an equipment-type-specific set of guide-word/parameter deviations with placeholder content.
   *
   * @param node the HAZOP node to populate
   * @param type the simple class name of the unit operation
   */
  private static void seedDeviations(HAZOPTemplate node, String type) {
    String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
    if (t.contains("separator") || t.contains("scrubber")) {
      addTbd(node, GuideWord.MORE, Parameter.LEVEL);
      addTbd(node, GuideWord.LESS, Parameter.LEVEL);
      addTbd(node, GuideWord.MORE, Parameter.PRESSURE);
      addTbd(node, GuideWord.LESS, Parameter.PRESSURE);
    } else if (t.contains("compressor") || t.contains("expander")) {
      addTbd(node, GuideWord.NO, Parameter.FLOW);
      addTbd(node, GuideWord.REVERSE, Parameter.FLOW);
      addTbd(node, GuideWord.MORE, Parameter.PRESSURE);
      addTbd(node, GuideWord.MORE, Parameter.TEMPERATURE);
    } else if (t.contains("pump")) {
      addTbd(node, GuideWord.NO, Parameter.FLOW);
      addTbd(node, GuideWord.REVERSE, Parameter.FLOW);
      addTbd(node, GuideWord.MORE, Parameter.PRESSURE);
    } else if (t.contains("valve")) {
      addTbd(node, GuideWord.NO, Parameter.FLOW);
      addTbd(node, GuideWord.MORE, Parameter.FLOW);
      addTbd(node, GuideWord.LESS, Parameter.FLOW);
    } else if (t.contains("cooler") || t.contains("heater") || t.contains("heatexchanger") || t.contains("reboiler")
        || t.contains("condenser")) {
      addTbd(node, GuideWord.MORE, Parameter.TEMPERATURE);
      addTbd(node, GuideWord.LESS, Parameter.TEMPERATURE);
      addTbd(node, GuideWord.NO, Parameter.FLOW);
    } else if (t.contains("pipe") || t.contains("pipeline")) {
      addTbd(node, GuideWord.NO, Parameter.FLOW);
      addTbd(node, GuideWord.LESS, Parameter.FLOW);
      addTbd(node, GuideWord.LESS, Parameter.PRESSURE);
    } else {
      addTbd(node, GuideWord.NO, Parameter.FLOW);
      addTbd(node, GuideWord.MORE, Parameter.FLOW);
      addTbd(node, GuideWord.MORE, Parameter.PRESSURE);
      addTbd(node, GuideWord.LESS, Parameter.PRESSURE);
    }
  }

  /**
   * Add a single placeholder deviation row to a node.
   *
   * @param node the HAZOP node
   * @param guideWord the guide-word
   * @param parameter the process parameter
   */
  private static void addTbd(HAZOPTemplate node, GuideWord guideWord, Parameter parameter) {
    node.addDeviation(guideWord, parameter, "TBD", "TBD", "TBD", null);
  }
}
