package neqsim.process.research;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Curated operation templates for early-stage process synthesis.
 *
 * <p>
 * The template library adds conservative, reusable material-operation options such as phase split,
 * compression with aftercooling, conditioning heaters/coolers, and pressure letdown. The generated
 * options are still screened by {@link ProcessSynthesisGraph},
 * {@link ProcessSynthesisFeasibilityPruner}, and rigorous NeqSim simulation before ranking.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessSynthesisTemplateLibrary {

  /**
   * Creates a process synthesis template library.
   */
  public ProcessSynthesisTemplateLibrary() {}

  /**
   * Creates curated operation options for a process research specification.
   *
   * @param spec process research specification
   * @return operation options suggested by the template library
   */
  public List<OperationOption> createOptions(ProcessResearchSpec spec) {
    List<OperationOption> options = new ArrayList<OperationOption>();
    for (String targetMaterial : getTargetMaterials(spec)) {
      addCompressionCoolingSeparationTrain(spec, targetMaterial, options);
      addPhaseSplitOption(spec, targetMaterial, options);
      addThermalConditioningOptions(spec, targetMaterial, options);
      addPressureLetdownOption(spec, targetMaterial, options);
    }
    return options;
  }

  /**
   * Adds a direct phase-split option when separators are allowed.
   *
   * @param spec process research specification
   * @param targetMaterial target material name
   * @param options operation options to update
   */
  private void addPhaseSplitOption(ProcessResearchSpec spec, String targetMaterial,
      List<OperationOption> options) {
    if (!spec.allowsUnitType("Separator")) {
      return;
    }
    addUnique(options,
        new OperationOption("library phase split to " + targetMaterial, "Separator")
            .addInputMaterial(spec.getFeedMaterialName()).addOutputMaterial(targetMaterial)
            .setDescription("Template-library phase split toward target material."));
  }

  /**
   * Adds a compression, cooling, and polishing-separator train when the needed units are allowed.
   *
   * @param spec process research specification
   * @param targetMaterial target material name
   * @param options operation options to update
   */
  private void addCompressionCoolingSeparationTrain(ProcessResearchSpec spec, String targetMaterial,
      List<OperationOption> options) {
    if (!spec.allowsUnitType("Compressor") || !spec.allowsUnitType("Cooler")
        || !spec.allowsUnitType("Separator")) {
      return;
    }
    String compressed = targetMaterial + " compressed intermediate";
    String cooled = targetMaterial + " cooled intermediate";
    double outletPressure =
        Math.max(spec.getFeedPressureBara() * 1.5, spec.getFeedPressureBara() + 5.0);
    double outletTemperature = Math.max(233.15, spec.getFeedTemperatureK() - 15.0);

    addUnique(options,
        new OperationOption("library compression to " + targetMaterial, "Compressor")
            .addInputMaterial(spec.getFeedMaterialName()).addOutputMaterial(compressed)
            .setProperty("outletPressure", outletPressure, "bara")
            .setProperty("isentropicEfficiency", 0.75)
            .setDescription("Template-library pressure boost for gas-conditioning trains."));
    addUnique(options,
        new OperationOption("library aftercooler to " + targetMaterial, "Cooler")
            .addInputMaterial(compressed).addOutputMaterial(cooled)
            .setProperty("outletTemperature", outletTemperature, "K")
            .setDescription("Template-library aftercooling before phase polishing."));
    addUnique(options,
        new OperationOption("library polishing separator to " + targetMaterial, "Separator")
            .addInputMaterial(cooled).addOutputMaterial(targetMaterial).setDescription(
                "Template-library condensate knock-out after compression and cooling."));
  }

  /**
   * Adds direct heater and cooler conditioning options.
   *
   * @param spec process research specification
   * @param targetMaterial target material name
   * @param options operation options to update
   */
  private void addThermalConditioningOptions(ProcessResearchSpec spec, String targetMaterial,
      List<OperationOption> options) {
    if (spec.allowsUnitType("Heater")) {
      addUnique(options,
          new OperationOption("library heater to " + targetMaterial, "Heater")
              .addInputMaterial(spec.getFeedMaterialName()).addOutputMaterial(targetMaterial)
              .setProperty("outletTemperature", spec.getFeedTemperatureK() + 30.0, "K")
              .setDescription("Template-library heating step for thermal conditioning."));
    }
    if (spec.allowsUnitType("Cooler")) {
      addUnique(options,
          new OperationOption("library cooler to " + targetMaterial, "Cooler")
              .addInputMaterial(spec.getFeedMaterialName()).addOutputMaterial(targetMaterial)
              .setProperty("outletTemperature", Math.max(233.15, spec.getFeedTemperatureK() - 30.0),
                  "K")
              .setDescription("Template-library cooling step for thermal conditioning."));
    }
  }

  /**
   * Adds pressure-letdown options for expansion/JT screening.
   *
   * @param spec process research specification
   * @param targetMaterial target material name
   * @param options operation options to update
   */
  private void addPressureLetdownOption(ProcessResearchSpec spec, String targetMaterial,
      List<OperationOption> options) {
    if (!spec.allowsUnitType("ThrottlingValve") || spec.getFeedPressureBara() <= 1.5) {
      return;
    }
    addUnique(options,
        new OperationOption("library pressure letdown to " + targetMaterial, "ThrottlingValve")
            .addInputMaterial(spec.getFeedMaterialName()).addOutputMaterial(targetMaterial)
            .setProperty("outletPressure", Math.max(1.0, spec.getFeedPressureBara() * 0.7), "bara")
            .setDescription("Template-library pressure letdown for JT/expansion screening."));
  }

  /**
   * Gets usable target material names from product targets.
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
   * Adds an operation only if an equivalent operation is not already present.
   *
   * @param options existing options
   * @param candidate candidate option to add
   */
  private void addUnique(List<OperationOption> options, OperationOption candidate) {
    Set<String> signatures = new LinkedHashSet<String>();
    for (OperationOption option : options) {
      signatures.add(signature(option));
    }
    if (!signatures.contains(signature(candidate))) {
      options.add(candidate);
    }
  }

  /**
   * Creates a stable operation signature for duplicate suppression.
   *
   * @param option operation option
   * @return signature string
   */
  private String signature(OperationOption option) {
    return option.getName().toLowerCase() + "|" + option.getEquipmentType().toLowerCase() + "|"
        + option.getInputMaterials().toString().toLowerCase() + "|"
        + option.getOutputMaterials().toString().toLowerCase();
  }
}
