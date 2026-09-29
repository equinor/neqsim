package neqsim.process.equipment.distillation;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import neqsim.process.equipment.distillation.internals.ColumnInternalsDesigner;
import neqsim.process.equipment.distillation.internals.PackingHydraulicsCalculator;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Packed column (contactor/absorber/stripper) using packing internals with HETP-based staging.
 *
 * <p>
 * Models a packed column for gas absorption (amine, TEG, water wash) or stripping applications. Internally wraps a
 * {@link DistillationColumn} and adds packing-specific functionality: HETP calculation, packing hydraulics (flooding,
 * pressure drop, mass transfer), and packing selection.
 * </p>
 *
 * <p>
 * The number of theoretical stages is determined from packed bed height and HETP. The underlying
 * {@link DistillationColumn} provides the rigorous VLE calculations, while the {@link PackingHydraulicsCalculator}
 * provides the hydraulic design evaluation.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * PackedColumn contactor = new PackedColumn("TEG Contactor", gasStream);
 * contactor.setPackedHeight(6.0);
 * contactor.setPackingType("Mellapak-250Y");
 * contactor.setStructuredPacking(true);
 * contactor.setDesignFloodFraction(0.70);
 * contactor.addSolventStream(leanTEGStream);
 * contactor.run();
 *
 * StreamInterface dryGas = contactor.getGasOutStream();
 * StreamInterface richSolvent = contactor.getLiquidOutStream();
 * double hetp = contactor.getHETP();
 * double percentFlood = contactor.getPercentFlood();
 * String report = contactor.toJson();
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PackedColumn extends DistillationColumn {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger. */
  private static final Logger logger = LogManager.getLogger(PackedColumn.class);

  /** Initial HETP used only to choose the rigorous stage topology before hydraulics are available [m]. */
  private static final double INITIAL_HETP_GUESS_M = 0.5;

  // ======================== Packing configuration ========================

  /** Packed bed height [m]. */
  private double packedHeight = 5.0;

  /** Packing type name. */
  private String packingType = "Pall-Ring-50";

  /** Whether packing is structured (true) or random (false). */
  private boolean structuredPacking = false;

  /** Design flood fraction (0-1). Typical 0.65-0.75 for packed columns. */
  private double designFloodFraction = 0.70;

  /** Relative hydraulic capacity factor for the selected packing. */
  private double packingHydraulicCapacityFactor = 1.0;

  /** Column internal diameter [m]. If &lt;= 0, auto-sized from hydraulics. */
  private double columnDiameter = -1.0;

  // ======================== Hydraulic results ========================

  /** Packing hydraulics calculator (populated after run). */
  private transient PackingHydraulicsCalculator hydraulics;

  /** HETP from hydraulics calculation [m]. */
  private double hetp = 0.0;

  /** Number of theoretical stages from HETP (packed_height / HETP). */
  private double theoreticalStages = 0.0;

  /** Percent flooding at operating conditions. */
  private double percentFlood = 0.0;

  /** Total packing pressure drop [Pa]. */
  private double packingPressureDrop = 0.0;

  /** Flooding velocity [m/s]. */
  private double floodingVelocity = 0.0;

  /** Whether hydraulics design is feasible. */
  private boolean hydraulicsOk = false;

  /** Whether the most recent packing-hydraulics calculation produced a result. */
  private boolean hydraulicsCalculated = false;

  /** Diagnostic message when the most recent packing-hydraulics calculation failed. */
  private String lastHydraulicsError = "";

  /**
   * Create a packed column with a given number of stages (from HETP).
   *
   * <p>
   * The default 0.5 m HETP is used only to choose the rigorous stage topology at construction. After running, packing
   * correlations calculate a separate hydraulic HETP and equivalent theoretical-stage count; those reported hydraulic
   * values do not retroactively change the stage topology that was solved.
   * </p>
   *
   * @param name equipment name
   * @param hasCondenser true to include a condenser
   * @param hasReboiler true to include a reboiler
   */
  public PackedColumn(String name, boolean hasCondenser, boolean hasReboiler) {
    super(name, estimateStages(5.0, INITIAL_HETP_GUESS_M), hasReboiler, hasCondenser);
  }

  /**
   * Create a packed column for absorber/contactor use (no condenser, no reboiler).
   *
   * @param name equipment name
   * @param gasInStream the gas inlet stream (bottom)
   */
  public PackedColumn(String name, StreamInterface gasInStream) {
    super(name, 10, false, false);
    addFeedStream(gasInStream, 0);
  }

  /**
   * Create a packed column with specified packed height and packing type.
   *
   * @param name equipment name
   * @param packedHeight packed bed height [m]
   * @param packingType packing name (e.g., "Pall-Ring-50", "Mellapak-250Y")
   * @param hasCondenser true for condenser
   * @param hasReboiler true for reboiler
   */
  public PackedColumn(String name, double packedHeight, String packingType, boolean hasCondenser, boolean hasReboiler) {
    super(name, estimateStages(packedHeight, INITIAL_HETP_GUESS_M), hasReboiler, hasCondenser);
    this.packedHeight = packedHeight;
    this.packingType = packingType;
  }

  /**
   * Estimate the number of theoretical stages from packed height and an initial HETP guess.
   *
   * @param height packed bed height [m]
   * @param hetpGuess initial HETP guess [m]
   * @return estimated stage count (at least 2)
   */
  private static int estimateStages(double height, double hetpGuess) {
    int stages = (int) Math.ceil(height / hetpGuess);
    return Math.max(stages, 2);
  }

  // ======================== Setters ========================

  /**
   * Set packed bed height [m].
   *
   * @param height packed bed height
   */
  public void setPackedHeight(double height) {
    this.packedHeight = height;
  }

  /**
   * Get packed bed height [m].
   *
   * @return packed bed height
   */
  public double getPackedHeight() {
    return packedHeight;
  }

  /**
   * Set packing type by name.
   *
   * @param packingType packing name (e.g., "Pall-Ring-50", "Mellapak-250Y")
   */
  public void setPackingType(String packingType) {
    this.packingType = packingType;
  }

  /**
   * Get packing type name.
   *
   * @return packing name
   */
  public String getPackingType() {
    return packingType;
  }

  /**
   * Set whether packing is structured or random.
   *
   * @param structured true for structured (e.g., Mellapak), false for random (e.g., Pall Ring)
   */
  public void setStructuredPacking(boolean structured) {
    this.structuredPacking = structured;
  }

  /**
   * Check if packing is structured.
   *
   * @return true for structured packing
   */
  public boolean isStructuredPacking() {
    return structuredPacking;
  }

  /**
   * Set design flood fraction (typical 0.65-0.75).
   *
   * @param fraction flood fraction
   */
  public void setDesignFloodFraction(double fraction) {
    this.designFloodFraction = fraction;
  }

  /**
   * Get design flood fraction.
   *
   * @return flood fraction
   */
  public double getDesignFloodFraction() {
    return designFloodFraction;
  }

  /**
   * Set the relative hydraulic capacity factor for the selected packing.
   *
   * <p>
   * The default of 1.0 uses the base packing correlation. A higher value may be used to represent a high-capacity
   * structured packing only when supported by vendor hydraulic data for the intended service.
   * </p>
   *
   * @param factor positive relative capacity factor
   * @throws IllegalArgumentException if the factor is not positive and finite
   */
  public void setPackingHydraulicCapacityFactor(double factor) {
    if (!Double.isFinite(factor) || factor <= 0.0) {
      throw new IllegalArgumentException("packing hydraulic capacity factor must be positive and finite");
    }
    this.packingHydraulicCapacityFactor = factor;
  }

  /**
   * Get the relative hydraulic capacity factor for the selected packing.
   *
   * @return relative hydraulic capacity factor
   */
  public double getPackingHydraulicCapacityFactor() {
    return packingHydraulicCapacityFactor;
  }

  /**
   * Override column diameter [m].
   *
   * <p>
   * Set to &lt;= 0 to request post-solve hydraulic auto-sizing. In auto-sizing mode the internally reported diameter is
   * replaced by the hydraulics designer result after the rigorous column solve; the diameter therefore describes the
   * design evaluation, not an input used by the VLE solve.
   * </p>
   *
   * @param diameter column diameter
   */
  public void setColumnDiameter(double diameter) {
    this.columnDiameter = diameter;
  }

  /**
   * Add the solvent (lean amine/TEG) stream to the top of the column.
   *
   * @param solventStream the lean solvent stream
   */
  public void addSolventStream(StreamInterface solventStream) {
    int topTrayIndex = getTrays().size() - 1;
    if (topTrayIndex < 0) {
      topTrayIndex = 0;
    }
    addFeedStream(solventStream, topTrayIndex);
  }

  // ======================== Result getters ========================

  /**
   * Get the calculated HETP [m].
   *
   * @return HETP
   */
  public double getHETP() {
    return hetp;
  }

  /**
   * Get the packing-correlation equivalent number of theoretical stages.
   *
   * <p>
   * This value is calculated after the rigorous column solve as packed height divided by hydraulic HETP. It is a
   * hydraulic/design result and is not the number of stage objects used by the preceding VLE calculation. Use
   * {@link #getNumberOfTrays()} for the stage topology that was actually solved.
   * </p>
   *
   * @return packing-correlation equivalent theoretical stages
   */
  public double getTheoreticalStages() {
    return theoreticalStages;
  }

  /**
   * Check whether the most recent packing-hydraulics calculation produced a usable result.
   *
   * @return true when hydraulic result fields belong to the most recent run
   */
  public boolean isHydraulicsCalculated() {
    return hydraulicsCalculated;
  }

  /**
   * Return the diagnostic message from the most recent failed packing-hydraulics calculation.
   *
   * @return empty string after successful hydraulics, otherwise the failure message
   */
  public String getLastHydraulicsError() {
    return lastHydraulicsError;
  }

  /**
   * Get percent flooding at operating conditions.
   *
   * @return percent flood
   */
  public double getPercentFlood() {
    return percentFlood;
  }

  /**
   * Get packing pressure drop [Pa].
   *
   * @return pressure drop
   */
  public double getPackingPressureDrop() {
    return packingPressureDrop;
  }

  /**
   * Get packing pressure drop with unit.
   *
   * @param unit "Pa", "mbar", "bar"
   * @return pressure drop in specified unit
   */
  public double getPackingPressureDrop(String unit) {
    if ("mbar".equalsIgnoreCase(unit)) {
      return packingPressureDrop / 100.0;
    } else if ("bar".equalsIgnoreCase(unit)) {
      return packingPressureDrop / 1e5;
    }
    return packingPressureDrop;
  }

  /**
   * Get flooding velocity [m/s].
   *
   * @return flooding velocity
   */
  public double getFloodingVelocity() {
    return floodingVelocity;
  }

  /**
   * Check if the hydraulic design is feasible.
   *
   * @return true if all hydraulic checks pass
   */
  public boolean isHydraulicsOk() {
    return hydraulicsOk;
  }

  /**
   * Get the packing hydraulics calculator (null before run).
   *
   * @return hydraulics calculator
   */
  public PackingHydraulicsCalculator getHydraulics() {
    return hydraulics;
  }

  // ======================== Run ========================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Run the underlying distillation column for VLE.
    super.run(id);

    // Hydraulic results must never silently describe an earlier run.
    hydraulics = null;
    hetp = Double.NaN;
    theoreticalStages = Double.NaN;
    percentFlood = Double.NaN;
    packingPressureDrop = Double.NaN;
    floodingVelocity = Double.NaN;
    hydraulicsOk = false;
    hydraulicsCalculated = false;
    lastHydraulicsError = "";

    // Now evaluate packing hydraulics.
    try {
      calcPackingHydraulics();
      hydraulicsCalculated = hydraulics != null;
      if (!hydraulicsCalculated) {
        lastHydraulicsError = "Packing hydraulics returned no result";
        logger.warn("{}: {}", getName(), lastHydraulicsError);
      }
    } catch (Exception ex) {
      lastHydraulicsError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      logger.warn("{}: packing hydraulics calculation failed: {}", getName(), lastHydraulicsError);
    }
  }

  /**
   * Calculate packing hydraulics after the column has converged.
   */
  private void calcPackingHydraulics() {
    ColumnInternalsDesigner designer = new ColumnInternalsDesigner(this);
    designer.setInternalsType("packed");
    designer.setStructuredPacking(structuredPacking);
    designer.setPackingPreset(packingType);
    designer.setPackedHeight(packedHeight);
    designer.setDesignFloodFraction(designFloodFraction);
    designer.setPackingHydraulicCapacityFactor(packingHydraulicCapacityFactor);
    if (columnDiameter > 0) {
      designer.setColumnDiameterOverride(columnDiameter);
    }
    designer.calculate();

    PackingHydraulicsCalculator packResult = designer.getPackingResult();
    if (packResult != null) {
      this.hydraulics = packResult;
      this.hetp = packResult.getHETP();
      this.theoreticalStages = (hetp > 0) ? packedHeight / hetp : 0;
      this.percentFlood = packResult.getPercentFlood();
      this.packingPressureDrop = packResult.getTotalPressureDrop();
      this.floodingVelocity = packResult.getFloodingVelocity();
      this.hydraulicsOk = packResult.isDesignOk();

      // Update internal diameter from sizing
      if (columnDiameter <= 0) {
        setInternalDiameter(designer.getRequiredDiameter());
      } else {
        setInternalDiameter(columnDiameter);
      }
    }
  }

  /**
   * Generate a JSON report including both column results and packing hydraulics.
   *
   * @return comprehensive JSON report
   */
  @Override
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("equipmentName", getName());
    root.addProperty("equipmentType", "PackedColumn");

    // Packing configuration
    JsonObject packing = new JsonObject();
    packing.addProperty("packingType", packingType);
    packing.addProperty("packingCategory", structuredPacking ? "structured" : "random");
    packing.addProperty("packedHeight_m", packedHeight);
    packing.addProperty("columnDiameter_m", getInternalDiameter());
    packing.addProperty("designFloodFraction", designFloodFraction);
    packing.addProperty("hydraulicCapacityFactor", packingHydraulicCapacityFactor);
    root.add("packingConfiguration", packing);

    // Hydraulic results
    JsonObject hydResults = new JsonObject();
    hydResults.addProperty("HETP_m", hetp);
    hydResults.addProperty("packingEquivalentTheoreticalStages", theoreticalStages);
    hydResults.addProperty("solvedStageCount", getNumberOfTrays());
    hydResults.addProperty("hydraulicsCalculated", hydraulicsCalculated);
    hydResults.addProperty("hydraulicsError", lastHydraulicsError);
    hydResults.addProperty("percentFlood", percentFlood);
    hydResults.addProperty("floodingVelocity_ms", floodingVelocity);
    hydResults.addProperty("packingPressureDrop_Pa", packingPressureDrop);
    hydResults.addProperty("packingPressureDrop_mbar", packingPressureDrop / 100.0);
    hydResults.addProperty("hydraulicsOk", hydraulicsOk);
    if (hydraulics != null) {
      hydResults.addProperty("fsFactor", hydraulics.getFsFactor());
      hydResults.addProperty("kGa", hydraulics.getKGa());
      hydResults.addProperty("kLa", hydraulics.getKLa());
      hydResults.addProperty("wettedArea_m2m3", hydraulics.getWettedArea());
      hydResults.addProperty("HTU_G_m", hydraulics.getHtuG());
      hydResults.addProperty("HTU_L_m", hydraulics.getHtuL());
      hydResults.addProperty("HTU_OG_m", hydraulics.getHtuOG());
    }
    root.add("hydraulicResults", hydResults);

    // Column performance
    JsonObject perf = new JsonObject();
    if (getGasOutStream() != null) {
      perf.addProperty("gasOutTemperature_C", getGasOutStream().getTemperature() - 273.15);
      perf.addProperty("gasOutPressure_bara", getGasOutStream().getPressure());
    }
    if (getLiquidOutStream() != null) {
      perf.addProperty("liquidOutTemperature_C", getLiquidOutStream().getTemperature() - 273.15);
      perf.addProperty("liquidOutPressure_bara", getLiquidOutStream().getPressure());
    }
    perf.addProperty("converged", solved());
    root.add("columnPerformance", perf);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
  }
}
