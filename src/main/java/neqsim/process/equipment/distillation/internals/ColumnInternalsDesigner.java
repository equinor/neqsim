package neqsim.process.equipment.distillation.internals;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.distillation.SimpleTray;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Column internals designer for distillation columns.
 *
 * <p>
 * High-level facade that evaluates hydraulic performance on every tray in a converged
 * {@link DistillationColumn}, identifies the controlling tray (highest vapor/liquid loading), sizes
 * the column diameter, and produces a comprehensive JSON report.
 * </p>
 *
 * <p>
 * Supports both tray columns (sieve, valve, bubble-cap via {@link TrayHydraulicsCalculator}) and
 * packed columns ({@link PackingHydraulicsCalculator}).
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * // After running a DistillationColumn:
 * column.run();
 *
 * ColumnInternalsDesigner designer = new ColumnInternalsDesigner(column);
 * designer.setInternalsType("sieve");
 * designer.setTraySpacing(0.6);
 * designer.setDesignFloodFraction(0.80);
 * designer.calculate();
 *
 * double diameter = designer.getRequiredDiameter();
 * boolean ok = designer.isDesignOk();
 * String report = designer.toJson();
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ColumnInternalsDesigner implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ColumnInternalsDesigner.class);

  // ======================== Configuration ========================

  /** The distillation column to evaluate. */
  private transient DistillationColumn column;

  /** Internals type: "sieve", "valve", "bubble-cap", or "packed". */
  private String internalsType = "sieve";

  /** Tray spacing [m] (for tray columns). */
  private double traySpacing = 0.6;

  /** Weir height [m] (for tray columns). */
  private double weirHeight = 0.05;

  /** Hole diameter [mm] (for sieve trays). */
  private double holeDiameter = 12.7;

  /** Hole area fraction (sieve trays). */
  private double holeAreaFraction = 0.10;

  /** Downcomer area fraction (tray columns). */
  private double downcommerAreaFraction = 0.10;

  /** Design flooding fraction (0-1). */
  private double designFloodFraction = 0.80;

  /** Packing preset name (for packed columns). */
  private String packingPreset = "Pall-Ring-50";

  /** Whether the packing is structured. */
  private boolean structuredPacking = false;

  /** Packed bed height [m] (for packed columns). */
  private double packedHeight = 5.0;

  /** Column diameter override [m]. If &gt; 0, use this instead of auto-sizing. */
  private double columnDiameterOverride = -1.0;

  // ======================== Results ========================

  /** Per-tray hydraulics results (tray columns only). */
  private List<TrayHydraulicsCalculator> trayResults = new ArrayList<TrayHydraulicsCalculator>();

  /** Packing hydraulics result (packed columns only). */
  private PackingHydraulicsCalculator packingResult;

  /** Index of the controlling tray (highest loading). */
  private int controllingTrayIndex = -1;

  /** Required column diameter [m]. */
  private double requiredDiameter = 0.0;

  /** Overall design verdict. */
  private boolean designOk = false;

  /** Overall tray efficiency (O'Connell average). */
  private double averageTrayEfficiency = 0.0;

  /** Total column pressure drop [Pa]. */
  private double totalPressureDrop = 0.0;

  /** Maximum percent flooding across all trays. */
  private double maxPercentFlood = 0.0;

  /** Minimum percent flooding across all trays (turndown check). */
  private double minPercentFlood = 100.0;

  /**
   * Constructor with column.
   *
   * @param column the converged distillation column
   */
  public ColumnInternalsDesigner(DistillationColumn column) {
    this.column = column;
  }

  /**
   * Default constructor. Column must be set via {@link #setColumn(DistillationColumn)}.
   */
  public ColumnInternalsDesigner() {}

  /**
   * Set the distillation column.
   *
   * @param column the distillation column
   */
  public void setColumn(DistillationColumn column) {
    this.column = column;
  }

  /**
   * Set internals type: "sieve", "valve", "bubble-cap", or "packed".
   *
   * @param type internals type
   */
  public void setInternalsType(String type) {
    this.internalsType = type;
  }

  /**
   * Set tray spacing [m].
   *
   * @param spacing tray spacing
   */
  public void setTraySpacing(double spacing) {
    this.traySpacing = spacing;
  }

  /**
   * Set weir height [m].
   *
   * @param height weir height
   */
  public void setWeirHeight(double height) {
    this.weirHeight = height;
  }

  /**
   * Set hole diameter [mm] (sieve trays).
   *
   * @param diameter hole diameter
   */
  public void setHoleDiameter(double diameter) {
    this.holeDiameter = diameter;
  }

  /**
   * Set hole area fraction (sieve trays, 0.05-0.16).
   *
   * @param fraction hole area fraction
   */
  public void setHoleAreaFraction(double fraction) {
    this.holeAreaFraction = fraction;
  }

  /**
   * Set downcomer area fraction (0.08-0.12).
   *
   * @param fraction downcomer area fraction
   */
  public void setDowncommerAreaFraction(double fraction) {
    this.downcommerAreaFraction = fraction;
  }

  /**
   * Set design flooding fraction (typical 0.70-0.85 for trays, 0.65-0.75 for packing).
   *
   * @param fraction design flooding fraction
   */
  public void setDesignFloodFraction(double fraction) {
    this.designFloodFraction = fraction;
  }

  /**
   * Set packing preset (for packed columns).
   *
   * @param preset packing name (e.g., "Pall-Ring-50", "Mellapak-250Y")
   */
  public void setPackingPreset(String preset) {
    this.packingPreset = preset;
  }

  /**
   * Set whether packing is structured (default false = random).
   *
   * @param structured true for structured packing
   */
  public void setStructuredPacking(boolean structured) {
    this.structuredPacking = structured;
  }

  /**
   * Set packed bed height [m].
   *
   * @param height packed bed height
   */
  public void setPackedHeight(double height) {
    this.packedHeight = height;
  }

  /**
   * Override column diameter [m]. Set to -1 for auto-sizing.
   *
   * @param diameter column diameter or -1 for auto
   */
  public void setColumnDiameterOverride(double diameter) {
    this.columnDiameterOverride = diameter;
  }

  /**
   * Run all internals sizing calculations.
   *
   * <p>
   * For tray columns, evaluates hydraulics on EVERY tray, finds the controlling tray, and sizes the
   * column diameter. For packed columns, runs the packing hydraulics calculator.
   * </p>
   */
  public void calculate() {
    if (column == null) {
      logger.error("No distillation column set for ColumnInternalsDesigner");
      return;
    }

    if ("packed".equalsIgnoreCase(internalsType)) {
      calculatePacked();
    } else {
      calculateTrayed();
    }
  }

  /**
   * Calculate internals for tray columns.
   */
  private void calculateTrayed() {
    List<SimpleTray> trays = column.getTrays();
    if (trays == null || trays.isEmpty()) {
      logger.error("Column has no trays");
      return;
    }

    trayResults.clear();

    // Pass 1: Find the controlling tray (highest vapor load) for diameter sizing
    double maxVaporMassFlow = 0.0;
    int maxVaporTrayIdx = 0;
    double maxLiquidMassFlow = 0.0;

    for (int i = 0; i < trays.size(); i++) {
      SimpleTray tray = trays.get(i);
      double[] flows = getTrayFlows(tray);
      if (flows[0] > maxVaporMassFlow) {
        maxVaporMassFlow = flows[0];
        maxVaporTrayIdx = i;
      }
      if (flows[1] > maxLiquidMassFlow) {
        maxLiquidMassFlow = flows[1];
      }
    }
    controllingTrayIndex = maxVaporTrayIdx;

    // Size the column diameter using the controlling tray
    SimpleTray controlTray = trays.get(controllingTrayIndex);
    double[] controlFlows = getTrayFlows(controlTray);
    double[] controlProps = getTrayProperties(controlTray);

    TrayHydraulicsCalculator sizer = new TrayHydraulicsCalculator();
    sizer.setTrayType(internalsType);
    sizer.setTraySpacing(traySpacing);
    sizer.setWeirHeight(weirHeight);
    sizer.setHoleDiameter(holeDiameter);
    sizer.setHoleAreaFraction(holeAreaFraction);
    sizer.setDowncommerAreaFraction(downcommerAreaFraction);
    sizer.setDesignFloodFraction(designFloodFraction);
    sizer.setVaporMassFlow(controlFlows[0]);
    sizer.setLiquidMassFlow(controlFlows[1]);
    sizer.setVaporDensity(controlProps[0]);
    sizer.setLiquidDensity(controlProps[1]);
    sizer.setLiquidViscosity(controlProps[2]);
    sizer.setSurfaceTension(controlProps[3]);

    if (columnDiameterOverride > 0) {
      requiredDiameter = columnDiameterOverride;
    } else {
      requiredDiameter = sizer.sizeColumnDiameter();
    }

    // Pass 2: Evaluate hydraulics on every tray at the sized diameter
    totalPressureDrop = 0.0;
    maxPercentFlood = 0.0;
    minPercentFlood = 100.0;
    double effSum = 0.0;
    int effCount = 0;
    designOk = true;

    for (int i = 0; i < trays.size(); i++) {
      SimpleTray tray = trays.get(i);
      double[] flows = getTrayFlows(tray);
      double[] props = getTrayProperties(tray);

      TrayHydraulicsCalculator calc = new TrayHydraulicsCalculator();
      calc.setTrayType(internalsType);
      calc.setColumnDiameter(requiredDiameter);
      calc.setTraySpacing(traySpacing);
      calc.setWeirHeight(weirHeight);
      calc.setHoleDiameter(holeDiameter);
      calc.setHoleAreaFraction(holeAreaFraction);
      calc.setDowncommerAreaFraction(downcommerAreaFraction);
      calc.setDesignFloodFraction(designFloodFraction);
      calc.setVaporMassFlow(flows[0]);
      calc.setLiquidMassFlow(flows[1]);
      calc.setVaporDensity(props[0]);
      calc.setLiquidDensity(props[1]);
      calc.setLiquidViscosity(props[2]);
      calc.setSurfaceTension(props[3]);
      calc.setRelativeVolatility(props[4]);

      calc.calculate();
      trayResults.add(calc);

      totalPressureDrop += calc.getTotalTrayPressureDrop();
      if (calc.getPercentFlood() > maxPercentFlood) {
        maxPercentFlood = calc.getPercentFlood();
      }
      if (calc.getPercentFlood() > 0 && calc.getPercentFlood() < minPercentFlood) {
        minPercentFlood = calc.getPercentFlood();
      }

      if (!calc.isDesignOk()) {
        designOk = false;
      }

      effSum += calc.getTrayEfficiency();
      effCount++;
    }

    averageTrayEfficiency = (effCount > 0) ? effSum / effCount : 0.65;
  }

  /**
   * Calculate internals for packed columns.
   */
  private void calculatePacked() {
    // Use average conditions from top and bottom of column
    List<SimpleTray> trays = column.getTrays();
    if (trays == null || trays.size() < 2) {
      logger.error("Column must have at least 2 trays for packed column evaluation");
      return;
    }

    // Get conditions from middle of column for sizing
    int midIdx = trays.size() / 2;
    SimpleTray midTray = trays.get(midIdx);
    double[] flows = getTrayFlows(midTray);
    double[] props = getTrayProperties(midTray);

    packingResult = new PackingHydraulicsCalculator();
    if (structuredPacking) {
      packingResult.setStructuredPackingPreset(packingPreset);
    } else {
      packingResult.setPackingPreset(packingPreset);
    }

    packingResult.setDesignFloodFraction(designFloodFraction);
    packingResult.setVaporMassFlow(flows[0]);
    packingResult.setLiquidMassFlow(flows[1]);
    packingResult.setVaporDensity(props[0]);
    packingResult.setLiquidDensity(props[1]);
    packingResult.setLiquidViscosity(props[2]);
    packingResult.setSurfaceTension(props[3]);

    // Size diameter
    if (columnDiameterOverride > 0) {
      requiredDiameter = columnDiameterOverride;
      packingResult.setColumnDiameter(columnDiameterOverride);
    } else {
      requiredDiameter = packingResult.sizeColumnDiameter();
    }

    packingResult.setPackedHeight(packedHeight);
    packingResult.calculate();

    totalPressureDrop = packingResult.getTotalPressureDrop();
    maxPercentFlood = packingResult.getPercentFlood();
    minPercentFlood = maxPercentFlood;
    averageTrayEfficiency = 0.0; // Not applicable for packing
    designOk = packingResult.isDesignOk();
  }

  /**
   * Extract vapor and liquid mass flow rates from a tray [kg/s].
   *
   * @param tray the tray to query
   * @return array {vaporMassFlow, liquidMassFlow} in kg/s
   */
  private double[] getTrayFlows(SimpleTray tray) {
    double vaporMass = 0.0;
    double liquidMass = 0.0;
    try {
      if (tray.getGasOutStream() != null) {
        vaporMass = tray.getGasOutStream().getFlowRate("kg/hr") / 3600.0;
      }
      if (tray.getLiquidOutStream() != null) {
        liquidMass = tray.getLiquidOutStream().getFlowRate("kg/hr") / 3600.0;
      }
    } catch (Exception ex) {
      logger.debug("Could not read tray flows: " + ex.getMessage());
    }
    return new double[] {Math.max(vaporMass, 1e-6), Math.max(liquidMass, 1e-6)};
  }

  /**
   * Extract physical properties from a tray's fluid.
   *
   * @param tray the tray to query
   * @return array {vaporDensity, liquidDensity, liquidViscosity, surfaceTension,
   *         relativeVolatility} in SI units
   */
  private double[] getTrayProperties(SimpleTray tray) {
    double vaporDensity = 1.0;
    double liquidDensity = 800.0;
    double liquidViscosity = 0.001;
    double surfaceTension = 0.02;
    double alpha = 2.0;

    try {
      SystemInterface fluid = tray.getFluid();
      if (fluid != null && fluid.getNumberOfPhases() >= 2) {
        fluid.initProperties();

        // Vapor properties
        if (fluid.hasPhaseType("gas")) {
          vaporDensity = fluid.getPhase("gas").getDensity("kg/m3");
        } else {
          vaporDensity = fluid.getPhase(0).getDensity("kg/m3");
        }

        // Liquid properties
        int liqPhase = fluid.hasPhaseType("oil") ? fluid.getPhaseNumberOfPhase("oil")
            : (fluid.hasPhaseType("aqueous") ? fluid.getPhaseNumberOfPhase("aqueous") : 1);
        liquidDensity = fluid.getPhase(liqPhase).getDensity("kg/m3");

        try {
          liquidViscosity = fluid.getPhase(liqPhase).getViscosity("kg/msec");
          if (liquidViscosity <= 0) {
            liquidViscosity = 0.001;
          }
        } catch (Exception ex) {
          liquidViscosity = 0.001;
        }

        // Surface tension — try to get from interface, fall back to estimate
        try {
          surfaceTension = fluid.getInterphaseProperties().getSurfaceTension(0, 1);
          if (surfaceTension <= 0 || Double.isNaN(surfaceTension)) {
            surfaceTension = 0.02;
          }
        } catch (Exception ex) {
          surfaceTension = 0.02;
        }

        // Relative volatility from K-values of lightest and heaviest components
        try {
          double maxK = 0.0;
          double minK = 1e10;
          for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
            double yi = fluid.getPhase(0).getComponent(i).getx();
            double xi = fluid.getPhase(liqPhase).getComponent(i).getx();
            if (xi > 1e-10 && yi > 1e-10) {
              double k = yi / xi;
              if (k > maxK) {
                maxK = k;
              }
              if (k < minK) {
                minK = k;
              }
            }
          }
          if (minK > 0) {
            alpha = maxK / minK;
          }
          alpha = Math.min(alpha, 20.0);
        } catch (Exception ex) {
          alpha = 2.0;
        }
      } else if (fluid != null) {
        // Single phase — use available data
        vaporDensity = fluid.getPhase(0).getDensity("kg/m3");
        if (fluid.getPhase(0).getType() == PhaseType.GAS) {
          liquidDensity = 600.0; // estimate
        }
      }
    } catch (Exception ex) {
      logger.debug("Could not read tray properties: " + ex.getMessage());
    }

    return new double[] {vaporDensity, liquidDensity, liquidViscosity, surfaceTension, alpha};
  }

  // ======================== Result Getters ========================

  /**
   * Get the required column diameter [m].
   *
   * @return required diameter
   */
  public double getRequiredDiameter() {
    return requiredDiameter;
  }

  /**
   * Check if the overall design is feasible.
   *
   * @return true if all tray/packing checks pass
   */
  public boolean isDesignOk() {
    return designOk;
  }

  /**
   * Get the controlling tray index.
   *
   * @return index of the tray with highest vapor loading
   */
  public int getControllingTrayIndex() {
    return controllingTrayIndex;
  }

  /**
   * Get the per-tray results list.
   *
   * @return list of TrayHydraulicsCalculator results (one per tray)
   */
  public List<TrayHydraulicsCalculator> getTrayResults() {
    return trayResults;
  }

  /**
   * Get the packing result calculator.
   *
   * @return PackingHydraulicsCalculator or null if tray column
   */
  public PackingHydraulicsCalculator getPackingResult() {
    return packingResult;
  }

  /**
   * Get maximum percent flooding across all trays.
   *
   * @return max percent flood
   */
  public double getMaxPercentFlood() {
    return maxPercentFlood;
  }

  /**
   * Get minimum percent flooding across all trays.
   *
   * @return min percent flood
   */
  public double getMinPercentFlood() {
    return minPercentFlood;
  }

  /**
   * Get the average tray efficiency (O'Connell).
   *
   * @return average efficiency (0-1)
   */
  public double getAverageTrayEfficiency() {
    return averageTrayEfficiency;
  }

  /**
   * Get total column pressure drop [Pa].
   *
   * @return total pressure drop
   */
  public double getTotalPressureDrop() {
    return totalPressureDrop;
  }

  /**
   * Get total column pressure drop [mbar].
   *
   * @return total pressure drop in mbar
   */
  public double getTotalPressureDropMbar() {
    return totalPressureDrop / 100.0;
  }

  /**
   * Get a comprehensive JSON report of column internals sizing.
   *
   * @return JSON string with full internals design results
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("columnName", column != null ? column.getName() : "unknown");
    root.addProperty("internalsType", internalsType);
    root.addProperty("designFloodFraction", designFloodFraction);

    // Overall results
    JsonObject overall = new JsonObject();
    overall.addProperty("requiredDiameter_m", requiredDiameter);
    overall.addProperty("maxPercentFlood", maxPercentFlood);
    overall.addProperty("minPercentFlood", minPercentFlood);
    overall.addProperty("totalPressureDrop_Pa", totalPressureDrop);
    overall.addProperty("totalPressureDrop_mbar", totalPressureDrop / 100.0);
    overall.addProperty("averageTrayEfficiency", averageTrayEfficiency);
    overall.addProperty("designOk", designOk);
    root.add("overallResults", overall);

    if ("packed".equalsIgnoreCase(internalsType) && packingResult != null) {
      // Packing report
      JsonObject packing = new JsonObject();
      packing.addProperty("packingName", packingPreset);
      packing.addProperty("packingCategory", structuredPacking ? "structured" : "random");
      packing.addProperty("packedHeight_m", packedHeight);
      packing.addProperty("columnDiameter_m", requiredDiameter);
      packing.addProperty("floodingVelocity_ms", packingResult.getFloodingVelocity());
      packing.addProperty("actualVelocity_ms", packingResult.getActualVelocity());
      packing.addProperty("percentFlood", packingResult.getPercentFlood());
      packing.addProperty("pressureDropPerMeter_Pa", packingResult.getPressureDropPerMeter());
      packing.addProperty("totalPressureDrop_Pa", packingResult.getTotalPressureDrop());
      packing.addProperty("HETP_m", packingResult.getHETP());
      packing.addProperty("numberOfTheoreticalStages",
          packingResult.getNumberOfTheoreticalStages());
      packing.addProperty("wettedArea_m2m3", packingResult.getWettedArea());
      packing.addProperty("kGa", packingResult.getKGa());
      packing.addProperty("kLa", packingResult.getKLa());
      packing.addProperty("HTU_G_m", packingResult.getHtuG());
      packing.addProperty("HTU_L_m", packingResult.getHtuL());
      packing.addProperty("HTU_OG_m", packingResult.getHtuOG());
      packing.addProperty("fsFactor", packingResult.getFsFactor());
      packing.addProperty("wettingOk", packingResult.isWettingOk());
      packing.addProperty("designOk", packingResult.isDesignOk());
      root.add("packingDesign", packing);
    } else {
      // Tray report
      JsonObject trayConfig = new JsonObject();
      trayConfig.addProperty("trayType", internalsType);
      trayConfig.addProperty("traySpacing_m", traySpacing);
      trayConfig.addProperty("weirHeight_m", weirHeight);
      trayConfig.addProperty("holeDiameter_mm", holeDiameter);
      trayConfig.addProperty("holeAreaFraction", holeAreaFraction);
      trayConfig.addProperty("downcommerAreaFraction", downcommerAreaFraction);
      trayConfig.addProperty("numberOfTrays", trayResults.size());
      trayConfig.addProperty("controllingTrayIndex", controllingTrayIndex);
      root.add("trayConfiguration", trayConfig);

      // Per-tray profile
      JsonArray trayArray = new JsonArray();
      for (int i = 0; i < trayResults.size(); i++) {
        TrayHydraulicsCalculator t = trayResults.get(i);
        JsonObject trayObj = new JsonObject();
        trayObj.addProperty("trayNumber", i + 1);
        trayObj.addProperty("percentFlood", t.getPercentFlood());
        trayObj.addProperty("floodingVelocity_ms", t.getFloodingVelocity());
        trayObj.addProperty("actualVelocity_ms", t.getActualVaporVelocity());
        trayObj.addProperty("fsFactor", t.getFsFactor());
        trayObj.addProperty("weepingOk", t.isWeepingOk());
        trayObj.addProperty("entrainment", t.getEntrainment());
        trayObj.addProperty("entrainmentOk", t.isEntrainmentOk());
        trayObj.addProperty("downcommerBackup_m", t.getDowncommerBackup());
        trayObj.addProperty("downcommerBackupFraction", t.getDowncommerBackupFraction());
        trayObj.addProperty("downcommerBackupOk", t.isDowncommerBackupOk());
        trayObj.addProperty("totalPressureDrop_Pa", t.getTotalTrayPressureDrop());
        trayObj.addProperty("totalPressureDrop_mbar", t.getTotalTrayPressureDropMbar());
        trayObj.addProperty("dryTrayDP_Pa", t.getDryTrayPressureDrop());
        trayObj.addProperty("liquidHeadDP_Pa", t.getLiquidHeadPressureDrop());
        trayObj.addProperty("residualHeadDP_Pa", t.getResidualHeadPressureDrop());
        trayObj.addProperty("trayEfficiency", t.getTrayEfficiency());
        trayObj.addProperty("designOk", t.isDesignOk());
        trayArray.add(trayObj);
      }
      root.add("trayProfile", trayArray);
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(root);
  }
}
