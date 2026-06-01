package neqsim.process.research;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Generates process candidates from a {@link ProcessResearchSpec}.
 *
 * <p>
 * The first implementation combines template-like built-in candidates with process-network
 * operation options and reaction-route options. Generated candidates are represented as NeqSim JSON
 * definitions so they can be built deterministically by {@code ProcessSystem.fromJsonAndRun}.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessCandidateGenerator {
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private final ProcessSynthesisFeasibilityPruner pruner = new ProcessSynthesisFeasibilityPruner();
  private final ProcessSynthesisTemplateLibrary templateLibrary =
      new ProcessSynthesisTemplateLibrary();

  /**
   * Creates a process candidate generator.
   */
  public ProcessCandidateGenerator() {}

  /**
   * Generates process candidates.
   *
   * @param spec process research specification
   * @return generated candidates
   */
  public List<ProcessCandidate> generate(ProcessResearchSpec spec) {
    List<ProcessCandidate> candidates = new ArrayList<>();
    List<OperationOption> operationOptions = getOperationOptions(spec);
    addPhaseSeparationCandidate(spec, candidates);
    addCompressionCandidate(spec, candidates);
    addOperationOptionCandidates(spec, operationOptions, candidates);
    addProcessNetworkPathCandidates(spec, operationOptions, candidates);
    addReactionCandidates(spec, candidates);
    if (candidates.size() > spec.getMaxCandidates()) {
      return new ArrayList<ProcessCandidate>(candidates.subList(0, spec.getMaxCandidates()));
    }
    return candidates;
  }

  /**
   * Gets explicit and optional template-library operation options.
   *
   * @param spec process research specification
   * @return combined operation option list
   */
  private List<OperationOption> getOperationOptions(ProcessResearchSpec spec) {
    List<OperationOption> operationOptions = new ArrayList<OperationOption>();
    operationOptions.addAll(spec.getOperationOptions());
    if (spec.isIncludeSynthesisLibrary()) {
      operationOptions.addAll(templateLibrary.createOptions(spec));
    }
    return operationOptions;
  }

  /**
   * Adds the default phase-separation candidate when applicable.
   *
   * @param spec process research specification
   * @param candidates candidate list to append to
   */
  private void addPhaseSeparationCandidate(ProcessResearchSpec spec,
      List<ProcessCandidate> candidates) {
    if (!spec.allowsUnitType("Separator")) {
      return;
    }
    JsonObject root = createRoot(spec);
    JsonArray process = root.getAsJsonArray("process");
    process.add(createStreamUnit(spec));
    process.add(createUnit("Separator", "phase separator", "feed", null));
    root.addProperty("autoRun", true);

    ProcessCandidate candidate =
        new ProcessCandidate("candidate-separation", "Phase separation", "template-rule")
            .setDescription("Feed followed by a two-phase separator.")
            .setJsonDefinition(gson.toJson(root));
    candidate.addProductStreamReference("gas", "phase separator.gasOut");
    candidate.addProductStreamReference("liquid", "phase separator.liquidOut");
    candidate.addProductStreamReference("product", "phase separator.gasOut");
    candidate.addAssumption("Uses a simple separator as the first screening topology.");
    candidates.add(candidate);
  }

  /**
   * Adds a gas-compression candidate when applicable.
   *
   * @param spec process research specification
   * @param candidates candidate list to append to
   */
  private void addCompressionCandidate(ProcessResearchSpec spec,
      List<ProcessCandidate> candidates) {
    if (!spec.allowsUnitType("Compressor") || !spec.allowsUnitType("Separator")) {
      return;
    }
    JsonObject root = createRoot(spec);
    JsonArray process = root.getAsJsonArray("process");
    process.add(createStreamUnit(spec));
    process.add(createUnit("Separator", "inlet separator", "feed", null));

    JsonObject props = new JsonObject();
    props.add("outletPressure", createUnitArray(spec.getFeedPressureBara() * 1.5, "bara"));
    props.addProperty("isentropicEfficiency", 0.75);
    process.add(createUnit("Compressor", "product compressor", "inlet separator.gasOut", props));
    root.addProperty("autoRun", true);

    ProcessCandidate candidate =
        new ProcessCandidate("candidate-compression", "Gas compression", "template-rule")
            .setDescription("Gas product candidate with inlet separation and compression.")
            .setJsonDefinition(gson.toJson(root));
    candidate.addProductStreamReference("gas", "product compressor.outlet");
    candidate.addProductStreamReference("product", "product compressor.outlet");
    candidate.addAssumption("Compressor outlet pressure defaults to 1.5 times feed pressure.");
    candidates.add(candidate);
  }

  /**
   * Adds candidates from explicit process-network operation options.
   *
   * @param spec process research specification
   * @param operationOptions process-network operation options to convert to candidates
   * @param candidates candidate list to append to
   */
  private void addOperationOptionCandidates(ProcessResearchSpec spec,
      List<OperationOption> operationOptions, List<ProcessCandidate> candidates) {
    int index = 1;
    for (OperationOption option : operationOptions) {
      if (!spec.allowsUnitType(option.getEquipmentType())) {
        continue;
      }
      JsonObject root = createRoot(spec);
      JsonArray process = root.getAsJsonArray("process");
      process.add(createStreamUnit(spec));
      JsonObject props = option.propertiesToJson();
      process.add(createUnit(option.getEquipmentType(), option.getName(), "feed", props));
      root.addProperty("autoRun", true);

      ProcessCandidate candidate = new ProcessCandidate("candidate-operation-" + index,
          option.getName(), "process-network-option").setDescription(option.getDescription())
              .setJsonDefinition(gson.toJson(root));
      candidate.addProductStreamReference("product", option.getName() + ".outlet");
      candidate.addProductStreamReference("gas", option.getName() + ".gasOut");
      candidate.addProductStreamReference("liquid", option.getName() + ".liquidOut");
      candidate.addAssumption("Generated from an explicit operation option.");
      candidates.add(candidate);
      index++;
    }
  }

  /**
   * Adds graph-enumerated process-network path candidates.
   *
   * @param spec process research specification
   * @param operationOptions process-network operation options used to build the synthesis graph
   * @param candidates candidate list to append to
   */
  private void addProcessNetworkPathCandidates(ProcessResearchSpec spec,
      List<OperationOption> operationOptions, List<ProcessCandidate> candidates) {
    ProcessSynthesisGraph graph = createGraph(spec, operationOptions);
    List<String> targetMaterials = getTargetMaterials(spec);
    if (targetMaterials.isEmpty() || operationOptions.isEmpty()) {
      return;
    }
    List<List<OperationOption>> paths = graph.enumeratePaths(spec.getFeedMaterialName(),
        targetMaterials, spec.getMaxSynthesisDepth(), spec.getMaxCandidates());
    int index = 1;
    for (List<OperationOption> path : paths) {
      ProcessSynthesisFeasibilityPruner.FeasibilityResult feasibility =
          pruner.checkOperationPath(path, spec);
      if (spec.isFeasibilityPruningEnabled() && !feasibility.isFeasible()) {
        ProcessCandidate rejected = new ProcessCandidate("candidate-network-rejected-" + index,
            "Rejected process-network path " + index, "process-network-pruned");
        for (String issue : feasibility.getIssues()) {
          rejected.addError(issue);
        }
        rejected.setFeasible(false);
        rejected.setScore(Double.NEGATIVE_INFINITY);
        candidates.add(rejected);
        index++;
        continue;
      }
      JsonObject root = createRoot(spec);
      JsonArray process = root.getAsJsonArray("process");
      process.add(createStreamUnit(spec));
      String inletReference = "feed";
      String lastUnitName = "feed";
      for (int step = 0; step < path.size(); step++) {
        OperationOption option = path.get(step);
        lastUnitName = option.getName() + " path " + index + " step " + (step + 1);
        process.add(createUnit(option.getEquipmentType(), lastUnitName, inletReference,
            option.propertiesToJson()));
        inletReference = lastUnitName + ".outlet";
      }
      root.addProperty("autoRun", true);

      ProcessCandidate candidate =
          new ProcessCandidate("candidate-network-" + index, "Process-network path " + index,
              "process-network-graph").setDescription("Graph-enumerated material-operation path.")
                  .setJsonDefinition(gson.toJson(root));
      addTerminalProductReferences(candidate, lastUnitName, path.get(path.size() - 1));
      candidate.addAssumption("Generated by bounded P-graph-style material-operation search.");
      if (spec.isIncludeSynthesisLibrary()) {
        candidate
            .addAssumption("May include curated operation templates from the synthesis library.");
      }
      for (OperationOption option : path) {
        candidate.addSynthesisPathStep(option.getName() + " (" + option.getEquipmentType() + ")");
      }
      candidates.add(candidate);
      index++;
    }
  }

  /**
   * Creates a synthesis graph from explicit and template-library operations.
   *
   * @param spec process research specification
   * @param operationOptions operation options to include
   * @return populated synthesis graph
   */
  private ProcessSynthesisGraph createGraph(ProcessResearchSpec spec,
      List<OperationOption> operationOptions) {
    ProcessSynthesisGraph graph = new ProcessSynthesisGraph();
    graph.addMaterial(new MaterialNode(spec.getFeedMaterialName(), "feed material", null));
    for (MaterialNode node : spec.getMaterialNodes()) {
      graph.addMaterial(node);
    }
    for (OperationOption option : operationOptions) {
      graph.addOperation(option);
    }
    return graph;
  }

  /**
   * Adds product stream references based on the final equipment type in a generated path.
   *
   * @param candidate candidate to update
   * @param unitName terminal unit name
   * @param option terminal operation option
   */
  private void addTerminalProductReferences(ProcessCandidate candidate, String unitName,
      OperationOption option) {
    if ("Separator".equalsIgnoreCase(option.getEquipmentType())
        || "GasScrubber".equalsIgnoreCase(option.getEquipmentType())) {
      candidate.addProductStreamReference("product", unitName + ".gasOut");
      candidate.addProductStreamReference("gas", unitName + ".gasOut");
      candidate.addProductStreamReference("liquid", unitName + ".liquidOut");
    } else if ("ThreePhaseSeparator".equalsIgnoreCase(option.getEquipmentType())) {
      candidate.addProductStreamReference("product", unitName + ".gasOut");
      candidate.addProductStreamReference("gas", unitName + ".gasOut");
      candidate.addProductStreamReference("oil", unitName + ".oilOut");
      candidate.addProductStreamReference("water", unitName + ".waterOut");
    } else {
      candidate.addProductStreamReference("product", unitName + ".outlet");
      candidate.addProductStreamReference("gas", unitName + ".gasOut");
      candidate.addProductStreamReference("liquid", unitName + ".liquidOut");
    }
  }

  /**
   * Gets target material names from product targets.
   *
   * @param spec process research specification
   * @return target material names
   */
  private List<String> getTargetMaterials(ProcessResearchSpec spec) {
    List<String> targets = new ArrayList<String>();
    for (ProcessResearchSpec.ProductTarget target : spec.getProductTargets()) {
      if (target.getMaterialName() != null && !target.getMaterialName().trim().isEmpty()) {
        targets.add(target.getMaterialName());
      }
    }
    return targets;
  }

  /**
   * Adds reactor-containing candidates from reaction options.
   *
   * @param spec process research specification
   * @param candidates candidate list to append to
   */
  private void addReactionCandidates(ProcessResearchSpec spec, List<ProcessCandidate> candidates) {
    int index = 1;
    for (ReactionOption reaction : spec.getReactionOptions()) {
      ProcessSynthesisFeasibilityPruner.FeasibilityResult feasibility =
          pruner.checkReaction(reaction, spec);
      if (spec.isFeasibilityPruningEnabled() && !feasibility.isFeasible()) {
        ProcessCandidate rejected = new ProcessCandidate("candidate-reaction-rejected-" + index,
            reaction.getName(), "reaction-route-pruned");
        for (String issue : feasibility.getIssues()) {
          rejected.addError(issue);
        }
        rejected.setFeasible(false);
        rejected.setScore(Double.NEGATIVE_INFINITY);
        candidates.add(rejected);
        index++;
        continue;
      }
      if (!spec.allowsUnitType(reaction.getReactorType())) {
        continue;
      }
      JsonObject root = createRoot(spec);
      JsonArray process = root.getAsJsonArray("process");
      process.add(createStreamUnit(spec));
      String inletReference = "feed";

      if (!Double.isNaN(reaction.getReactorTemperatureK()) && spec.allowsUnitType("Heater")) {
        JsonObject heaterProps = new JsonObject();
        heaterProps.add("outletTemperature",
            createUnitArray(reaction.getReactorTemperatureK(), "K"));
        if (!Double.isNaN(reaction.getReactorPressureBara())) {
          heaterProps.add("outletPressure",
              createUnitArray(reaction.getReactorPressureBara(), "bara"));
        }
        process
            .add(createUnit("Heater", "reaction preheater " + index, inletReference, heaterProps));
        inletReference = "reaction preheater " + index + ".outlet";
      }

      JsonObject reactorProps = new JsonObject();
      reactorProps.addProperty("energyMode", reaction.getEnergyMode());
      if (!Double.isNaN(reaction.getReactorPressureBara())) {
        reactorProps.add("outletPressure",
            createUnitArray(reaction.getReactorPressureBara(), "bara"));
      }
      process.add(createUnit(reaction.getReactorType(), "reaction reactor " + index, inletReference,
          reactorProps));
      process.add(createUnit("Separator", "reaction separator " + index,
          "reaction reactor " + index + ".outlet", null));
      root.addProperty("autoRun", true);

      ProcessCandidate candidate =
          new ProcessCandidate("candidate-reaction-" + index, reaction.getName(), "reaction-route")
              .setDescription("Reactor plus product flash.").setJsonDefinition(gson.toJson(root));
      candidate.addProductStreamReference("gas", "reaction separator " + index + ".gasOut");
      candidate.addProductStreamReference("liquid", "reaction separator " + index + ".liquidOut");
      candidate.addProductStreamReference("product", "reaction separator " + index + ".gasOut");
      if (reaction.getExpectedProductComponent() != null) {
        candidate
            .addAssumption("Expected product component: " + reaction.getExpectedProductComponent());
      }
      candidates.add(candidate);
      index++;
    }
  }

  /**
   * Creates the root JSON object with fluid definition and empty process array.
   *
   * @param spec process research specification
   * @return root JSON object
   */
  private JsonObject createRoot(ProcessResearchSpec spec) {
    JsonObject root = new JsonObject();
    JsonObject fluid = new JsonObject();
    fluid.addProperty("model", spec.getFluidModel());
    fluid.addProperty("temperature", spec.getFeedTemperatureK());
    fluid.addProperty("pressure", spec.getFeedPressureBara());
    fluid.addProperty("mixingRule", spec.getMixingRule());
    JsonObject components = new JsonObject();
    for (java.util.Map.Entry<String, Double> entry : spec.getFeedComponents().entrySet()) {
      components.addProperty(entry.getKey(), entry.getValue());
    }
    fluid.add("components", components);
    root.add("fluid", fluid);
    root.add("process", new JsonArray());
    return root;
  }

  /**
   * Creates the feed stream unit JSON object.
   *
   * @param spec process research specification
   * @return stream unit JSON
   */
  private JsonObject createStreamUnit(ProcessResearchSpec spec) {
    JsonObject props = new JsonObject();
    props.add("flowRate", createUnitArray(spec.getFeedFlowRate(), spec.getFeedFlowUnit()));
    return createUnit("Stream", "feed", null, props);
  }

  /**
   * Creates a unit JSON object.
   *
   * @param type equipment type
   * @param name equipment name
   * @param inlet inlet reference, or null for feed stream
   * @param properties properties JSON object, or null
   * @return unit JSON object
   */
  private JsonObject createUnit(String type, String name, String inlet, JsonObject properties) {
    JsonObject unit = new JsonObject();
    unit.addProperty("type", type);
    unit.addProperty("name", name);
    if (inlet != null) {
      unit.addProperty("inlet", inlet);
    }
    if (properties != null && properties.entrySet().size() > 0) {
      unit.add("properties", properties);
    }
    return unit;
  }

  /**
   * Creates a JSON array in the common [value, unit] format.
   *
   * @param value numeric value
   * @param unit unit string
   * @return JSON array
   */
  private JsonArray createUnitArray(double value, String unit) {
    JsonArray array = new JsonArray();
    array.add(value);
    array.add(unit);
    return array;
  }
}
