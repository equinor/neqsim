package neqsim.process.fielddevelopment.facility;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.fielddevelopment.concept.FieldConcept;

/**
 * Fluent builder for assembling facility configurations from modular blocks.
 *
 * <p>
 * This builder enables rapid, concept-level facility configuration using pre-validated process
 * blocks. Blocks are assembled in sequence and can later be instantiated into actual process
 * equipment via a facility instantiator.
 *
 * <p>
 * Example usage:
 * 
 * <pre>
 * FacilityConfig config =
 *     FacilityBuilder.forConcept(myConcept).addBlock(BlockConfig.inletSeparation(80, 25))
 *         .addBlock(BlockConfig.compression(2, 180)).addBlock(BlockConfig.tegDehydration(50))
 *         .addBlock(BlockConfig.co2Membrane(2.5)).withRedundancy("compression", 1).build();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public final class FacilityBuilder implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final FieldConcept concept;
  private final List<BlockConfig> blocks;
  private final List<String> redundancyRequirements;
  private String name;
  private boolean includeFlare = true;
  private boolean includePowerGen = false;
  private double designMargin = 1.15; // 15% design margin

  private FacilityBuilder(FieldConcept concept) {
    this.concept = concept;
    this.blocks = new ArrayList<>();
    this.redundancyRequirements = new ArrayList<>();
    this.name = concept.getName() + " Facility";
  }

  /**
   * Creates a new facility builder for the given concept.
   *
   * @param concept field concept
   * @return new builder instance
   */
  public static FacilityBuilder forConcept(FieldConcept concept) {
    return new FacilityBuilder(concept);
  }

  /**
   * Creates a builder and auto-generates blocks based on concept requirements.
   *
   * <p>
   * Automatically adds:
   * <ul>
   * <li>Inlet separation (always)</li>
   * <li>Compression if inlet pressure &lt; export pressure</li>
   * <li>TEG dehydration if gas export</li>
   * <li>CO2 removal if CO2 &gt; 2%</li>
   * <li>H2S removal if H2S &gt; 50 ppm</li>
   * <li>Oil stabilization if oil export</li>
   * </ul>
   *
   * @param concept field concept
   * @return builder with auto-generated blocks
   */
  public static FacilityBuilder autoGenerate(FieldConcept concept) {
    FacilityBuilder builder = new FacilityBuilder(concept);
    builder.name = concept.getName() + " Auto-Generated Facility";

    // Always have inlet separation
    double inletPressure =
        concept.getWells() != null ? concept.getWells().getTubeheadPressure() : 80.0;
    builder.addBlock(BlockConfig.inletSeparation(inletPressure, 25.0));

    // Check if CO2 removal needed
    if (concept.needsCO2Removal()) {
      double co2Percent =
          concept.getReservoir() != null ? concept.getReservoir().getCo2Percent() : 0;
      if (co2Percent > 10) {
        // High CO2 - use amine
        builder.addBlock(BlockConfig.co2Amine(2.5));
      } else {
        // Moderate CO2 - use membrane
        builder.addBlock(BlockConfig.co2Membrane(2.5));
      }
    }

    // Check if H2S removal needed
    if (concept.needsH2SRemoval()) {
      builder.addBlock(BlockConfig.of(BlockType.H2S_REMOVAL));
    }

    // Check if compression needed
    double exportPressure =
        concept.getInfrastructure() != null ? concept.getInfrastructure().getExportPressure()
            : 180.0;
    if (inletPressure < exportPressure) {
      double ratio = exportPressure / inletPressure;
      int stages = (int) Math.ceil(Math.log(ratio) / Math.log(3.0)); // max 3:1 per stage
      stages = Math.max(1, Math.min(4, stages));
      builder.addBlock(BlockConfig.compression(stages, exportPressure));
    }

    // Check if dehydration needed
    if (concept.needsDehydration()) {
      builder.addBlock(BlockConfig.tegDehydration(50.0));
    }

    // Check if oil handling needed
    if (concept.getReservoir() != null && concept.getReservoir().hasLiquidProduction()) {
      builder.addBlock(BlockConfig.oilStabilization(3, 0.7));
      builder.addBlock(BlockConfig.of(BlockType.WATER_TREATMENT));
    }

    // Export conditioning
    builder.addBlock(BlockConfig.of(BlockType.EXPORT_CONDITIONING));

    return builder;
  }

  /**
   * Sets a custom name for the facility.
   *
   * @param name facility name
   * @return this builder
   */
  public FacilityBuilder name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Adds a block to the facility.
   *
   * @param block block configuration
   * @return this builder
   */
  public FacilityBuilder addBlock(BlockConfig block) {
    blocks.add(block);
    return this;
  }

  /**
   * Adds a block with default parameters.
   *
   * @param type block type
   * @return this builder
   */
  public FacilityBuilder addBlock(BlockType type) {
    blocks.add(BlockConfig.of(type));
    return this;
  }

  /**
   * Adds compression with specified number of stages.
   *
   * @param stages compression stages
   * @return this builder
   */
  public FacilityBuilder addCompression(int stages) {
    blocks.add(BlockConfig.compression(stages));
    return this;
  }

  /**
   * Adds compression with specified stages and outlet pressure.
   *
   * @param stages compression stages
   * @param outletPressure target outlet pressure in bara
   * @return this builder
   */
  public FacilityBuilder addCompression(int stages, double outletPressure) {
    blocks.add(BlockConfig.compression(stages, outletPressure));
    return this;
  }

  /**
   * Adds TEG dehydration with specified water spec.
   *
   * @param waterSpecPpm target water content in ppm
   * @return this builder
   */
  public FacilityBuilder addTegDehydration(double waterSpecPpm) {
    blocks.add(BlockConfig.tegDehydration(waterSpecPpm));
    return this;
  }

  /**
   * Adds CO2 removal via membrane.
   *
   * @param co2SpecPercent target CO2 percent
   * @return this builder
   */
  public FacilityBuilder addCo2Membrane(double co2SpecPercent) {
    blocks.add(BlockConfig.co2Membrane(co2SpecPercent));
    return this;
  }

  /**
   * Adds CO2 removal via amine.
   *
   * @param co2SpecPercent target CO2 percent
   * @return this builder
   */
  public FacilityBuilder addCo2Amine(double co2SpecPercent) {
    blocks.add(BlockConfig.co2Amine(co2SpecPercent));
    return this;
  }

  /**
   * Specifies that a block type should have redundancy (n+x).
   *
   * @param blockName name of block requiring redundancy
   * @param spareCount number of spare units (e.g., 1 for n+1)
   * @return this builder
   */
  public FacilityBuilder withRedundancy(String blockName, int spareCount) {
    redundancyRequirements.add(blockName + ":" + spareCount);
    return this;
  }

  /**
   * Sets whether to include a flare system (default: true).
   *
   * @param include true to include flare
   * @return this builder
   */
  public FacilityBuilder includeFlare(boolean include) {
    this.includeFlare = include;
    return this;
  }

  /**
   * Sets whether to include power generation on-site.
   *
   * @param include true to include power generation
   * @return this builder
   */
  public FacilityBuilder includePowerGeneration(boolean include) {
    this.includePowerGen = include;
    return this;
  }

  /**
   * Sets the design margin factor (default: 1.15 = 15% margin).
   *
   * @param margin design margin factor
   * @return this builder
   */
  public FacilityBuilder designMargin(double margin) {
    this.designMargin = margin;
    return this;
  }

  /**
   * Builds the facility configuration.
   *
   * @return immutable facility configuration
   */
  public FacilityConfig build() {
    List<BlockConfig> finalBlocks = new ArrayList<>(blocks);

    if (includeFlare) {
      finalBlocks.add(BlockConfig.of(BlockType.FLARE_SYSTEM));
    }

    if (includePowerGen) {
      finalBlocks.add(BlockConfig.of(BlockType.POWER_GENERATION));
    }

    return new FacilityConfig(name, concept, Collections.unmodifiableList(finalBlocks),
        Collections.unmodifiableList(new ArrayList<>(redundancyRequirements)), designMargin);
  }
}
