package neqsim.process.fielddevelopment.facility;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.fielddevelopment.concept.FieldConcept;

/**
 * Immutable configuration for a complete facility.
 *
 * <p>
 * Created by {@link FacilityBuilder}, this class holds all the block configurations and parameters
 * needed to instantiate a facility.
 *
 * @author ESOL
 * @version 1.0
 */
public final class FacilityConfig implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final FieldConcept concept;
  private final List<BlockConfig> blocks;
  private final List<String> redundancyRequirements;
  private final double designMargin;

  FacilityConfig(String name, FieldConcept concept, List<BlockConfig> blocks,
      List<String> redundancyRequirements, double designMargin) {
    this.name = name;
    this.concept = concept;
    this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
    this.redundancyRequirements =
        Collections.unmodifiableList(new ArrayList<>(redundancyRequirements));
    this.designMargin = designMargin;
  }

  /**
   * Gets the facility name.
   *
   * @return facility name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the underlying field concept.
   *
   * @return field concept
   */
  public FieldConcept getConcept() {
    return concept;
  }

  /**
   * Gets the list of configured blocks.
   *
   * @return unmodifiable list of blocks
   */
  public List<BlockConfig> getBlocks() {
    return blocks;
  }

  /**
   * Gets blocks of a specific type.
   *
   * @param type block type to filter by
   * @return list of matching blocks
   */
  public List<BlockConfig> getBlocksOfType(BlockType type) {
    List<BlockConfig> result = new ArrayList<>();
    for (BlockConfig block : blocks) {
      if (block.getType() == type) {
        result.add(block);
      }
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Checks if the facility has a specific block type.
   *
   * @param type block type to check
   * @return true if facility has this block type
   */
  public boolean hasBlock(BlockType type) {
    for (BlockConfig block : blocks) {
      if (block.getType() == type) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the redundancy requirements.
   *
   * @return unmodifiable list of redundancy specs
   */
  public List<String> getRedundancyRequirements() {
    return redundancyRequirements;
  }

  /**
   * Gets the design margin factor.
   *
   * @return design margin (e.g., 1.15 for 15% margin)
   */
  public double getDesignMargin() {
    return designMargin;
  }

  /**
   * Gets the number of blocks in the facility.
   *
   * @return block count
   */
  public int getBlockCount() {
    return blocks.size();
  }

  /**
   * Checks if this facility has compression.
   *
   * @return true if compression block exists
   */
  public boolean hasCompression() {
    return hasBlock(BlockType.COMPRESSION);
  }

  /**
   * Checks if this facility has CO2 removal.
   *
   * @return true if any CO2 removal block exists
   */
  public boolean hasCo2Removal() {
    return hasBlock(BlockType.CO2_REMOVAL_MEMBRANE) || hasBlock(BlockType.CO2_REMOVAL_AMINE);
  }

  /**
   * Checks if this facility has dehydration.
   *
   * @return true if TEG dehydration block exists
   */
  public boolean hasDehydration() {
    return hasBlock(BlockType.TEG_DEHYDRATION);
  }

  /**
   * Gets the total number of compression stages.
   *
   * @return total compression stages
   */
  public int getTotalCompressionStages() {
    int total = 0;
    for (BlockConfig block : getBlocksOfType(BlockType.COMPRESSION)) {
      total += block.getIntParameter("stages", 1);
    }
    return total;
  }

  /**
   * Estimates if this is a "complex" facility based on block count and types.
   *
   * @return true if complex
   */
  public boolean isComplex() {
    return blocks.size() > 6 || hasCo2Removal() || hasBlock(BlockType.H2S_REMOVAL)
        || hasBlock(BlockType.NGL_RECOVERY);
  }

  /**
   * Gets a summary description of the facility.
   *
   * @return summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(": ");
    List<String> blockNames = new ArrayList<>();
    for (BlockConfig block : blocks) {
      blockNames.add(block.getType().getDisplayName());
    }
    sb.append(String.join(" → ", blockNames));
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("FacilityConfig[%s, blocks=%d, margin=%.0f%%]", name, blocks.size(),
        (designMargin - 1) * 100);
  }
}
