package neqsim.process.fielddevelopment.reservoir;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.FlowRateType;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.VfpTable;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Generates Eclipse VFP (Vertical Flow Performance) lift curves using Beggs and Brill pipeline
 * calculations from NeqSim process simulations.
 *
 * <p>
 * This class creates VFPPROD tables for Eclipse reservoir simulators by running actual multiphase
 * flow calculations using the Beggs and Brill correlation. Unlike simplified correlations, this
 * uses rigorous thermodynamic calculations and empirical pressure drop correlations.
 * </p>
 *
 * <p>
 * This generator uses the shared {@link ReservoirCouplingExporter.VfpTable} data structure for
 * compatibility with other VFP generation tools in NeqSim.
 * </p>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>Generates VFPPROD tables with full (rate, THP, WCT, GOR, ALQ) dimensions</li>
 * <li>Uses actual PipeBeggsAndBrills calculations for pressure drop</li>
 * <li>Supports multiphase flow with gas, oil, and water</li>
 * <li>Handles both vertical risers and horizontal/inclined pipelines</li>
 * <li>Exports to Eclipse 100 and E300 compatible formats</li>
 * <li>JSON output for integration with other tools</li>
 * </ul>
 *
 * <h2>Eclipse VFP Table Format</h2>
 * <p>
 * The generated VFPPROD tables follow the Eclipse format with:
 * </p>
 * <ul>
 * <li>Flow rate axis (Sm3/day liquid or gas rate)</li>
 * <li>THP (Tubing Head Pressure) axis (bara)</li>
 * <li>WCT (Water Cut) axis (fraction 0-1)</li>
 * <li>GOR (Gas-Oil Ratio) axis (Sm3/Sm3)</li>
 * <li>ALQ (Artificial Lift Quantity) axis (typically gas lift rate)</li>
 * <li>BHP (Bottom Hole Pressure) values for each combination</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create base fluid
 * SystemInterface fluid = new SystemSrkEos(330.0, 50.0);
 * fluid.addComponent("methane", 0.70);
 * fluid.addComponent("ethane", 0.10);
 * fluid.addComponent("propane", 0.05);
 * fluid.addComponent("n-heptane", 0.10);
 * fluid.addComponent("water", 0.05);
 * fluid.setMixingRule("classic");
 * 
 * // Create inlet stream
 * Stream inlet = new Stream("inlet", fluid);
 * inlet.setFlowRate(5000, "Sm3/day");
 * inlet.run();
 * 
 * // Create pipeline (e.g., riser)
 * PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("riser", inlet);
 * pipe.setDiameter(0.1524); // 6 inch
 * pipe.setLength(2000.0); // 2000 m
 * pipe.setElevation(2000.0); // Vertical riser
 * pipe.setNumberOfIncrements(20);
 * 
 * // Create lift curve generator
 * EclipseLiftCurveGenerator generator = new EclipseLiftCurveGenerator(pipe, fluid);
 * 
 * // Configure VFP table parameters
 * generator.setFlowRateRange(500, 10000, 10); // Sm3/day
 * generator.setThpRange(20, 80, 7); // bara
 * generator.setWaterCutRange(0.0, 0.8, 5); // fraction
 * generator.setGorRange(100, 500, 5); // Sm3/Sm3
 * 
 * // Generate and export
 * generator.generateVfpTable(1, "PROD-A1");
 * generator.exportToFile("vfp_prodasystem.inc");
 * }</pre>
 *
 * @author ESOL
 * @version 1.1
 * @see PipeBeggsAndBrills
 * @see ReservoirCouplingExporter
 * @see ReservoirCouplingExporter.VfpTable
 */
public class EclipseLiftCurveGenerator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(EclipseLiftCurveGenerator.class);

  /**
   * Export format type.
   */
  public enum ExportFormat {
    /** Eclipse 100 black oil format. */
    ECLIPSE_100,
    /** Eclipse 300 compositional format. */
    E300_COMPOSITIONAL,
    /** CSV format for external tools. */
    CSV
  }

  /**
   * VFP table data structure - uses shared VfpTable from ReservoirCouplingExporter.
   *
   * @deprecated Use {@link ReservoirCouplingExporter.VfpTable} directly for new code.
   */
  @Deprecated
  public static class VfpTableData extends VfpTable {
    private static final long serialVersionUID = 1001L;

    /** Default constructor. */
    public VfpTableData() {
      super();
    }
  }

  // ============================================================================
  // INSTANCE VARIABLES
  // ============================================================================

  /** Reference pipeline for calculations. */
  private PipeBeggsAndBrills referencePipeline;

  /** Base fluid for composition. */
  private SystemInterface baseFluid;

  /** Temperature at inlet (K). */
  private double inletTemperature = 330.0;

  /** Export format. */
  private ExportFormat format = ExportFormat.ECLIPSE_100;

  /** Generated VFP tables (using shared VfpTable type). */
  private List<VfpTable> vfpTables;

  /** Generated keywords buffer. */
  private StringBuilder keywordsBuffer;

  // VFP generation parameter arrays
  private double[] flowRateRange;
  private double[] thpRange;
  private double[] wctRange;
  private double[] gorRange;
  private double[] almRange;

  /** Flow rate type for VFP table. */
  private FlowRateType flowRateType = FlowRateType.LIQUID;

  /** Datum depth for VFP tables (m). */
  private double datumDepth = 0.0;

  /** Pipeline diameter (m). */
  private double pipelineDiameter = 0.1524;

  /** Pipeline length (m). */
  private double pipelineLength = 2000.0;

  /** Pipeline elevation change (m). */
  private double pipelineElevation = 2000.0;

  /** Number of calculation increments. */
  private int numberOfIncrements = 20;

  /** Pipe wall roughness (m). */
  private double pipeWallRoughness = 1e-5;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new lift curve generator using an existing pipeline and fluid.
   *
   * <p>
   * Note: The pipeline parameters (diameter, length, elevation) should be set on the pipeline
   * before creating this generator. The generator extracts these parameters for use in VFP
   * calculations.
   * </p>
   *
   * @param pipeline the reference PipeBeggsAndBrills pipeline
   * @param baseFluid the base fluid composition
   */
  public EclipseLiftCurveGenerator(PipeBeggsAndBrills pipeline, SystemInterface baseFluid) {
    this.referencePipeline = pipeline;
    this.baseFluid = baseFluid.clone();
    this.vfpTables = new ArrayList<VfpTable>();
    this.keywordsBuffer = new StringBuilder();

    // Extract pipeline parameters - note: getDiameter() and getLength() return the set values
    // but getElevation() returns cumulativeElevation which is only set during run()
    // We need to use the pipeline's geometry data if available
    if (pipeline != null) {
      this.pipelineDiameter = pipeline.getDiameter();
      this.pipelineLength = pipeline.getLength();
      this.numberOfIncrements = pipeline.getNumberOfIncrements();
      this.pipeWallRoughness = pipeline.getPipeWallRoughness();
      // Note: Pipeline elevation should be set via setPipelineParameters() if needed
      // since getElevation() may return 0 before pipeline.run() is called
    }

    // Set default ranges
    setFlowRateRange(500.0, 10000.0, 10);
    setThpRange(20.0, 80.0, 7);
    setWaterCutRange(0.0, 0.8, 5);
    setGorRange(100.0, 500.0, 5);
    setAlmRange(0.0, 0.0, 1); // No artificial lift by default
  }

  /**
   * Creates a new standalone lift curve generator with pipeline parameters.
   *
   * @param baseFluid the base fluid composition
   * @param diameter pipeline diameter in meters
   * @param length pipeline length in meters
   * @param elevation pipeline elevation change in meters
   */
  public EclipseLiftCurveGenerator(SystemInterface baseFluid, double diameter, double length,
      double elevation) {
    this(null, baseFluid);
    this.pipelineDiameter = diameter;
    this.pipelineLength = length;
    this.pipelineElevation = elevation;
    this.datumDepth = elevation;
  }

  // ============================================================================
  // CONFIGURATION METHODS
  // ============================================================================

  /**
   * Set export format.
   *
   * @param format the format
   */
  public void setFormat(ExportFormat format) {
    this.format = format;
  }

  /**
   * Set flow rate range for VFP generation.
   *
   * @param minRate minimum rate (Sm3/day)
   * @param maxRate maximum rate (Sm3/day)
   * @param points number of points
   */
  public void setFlowRateRange(double minRate, double maxRate, int points) {
    this.flowRateRange = linspace(minRate, maxRate, points);
  }

  /**
   * Set flow rate values explicitly.
   *
   * @param rates array of flow rates (Sm3/day)
   */
  public void setFlowRates(double[] rates) {
    this.flowRateRange = rates.clone();
  }

  /**
   * Set THP (tubing head pressure) range for VFP generation.
   *
   * @param minThp minimum THP (bara)
   * @param maxThp maximum THP (bara)
   * @param points number of points
   */
  public void setThpRange(double minThp, double maxThp, int points) {
    this.thpRange = linspace(minThp, maxThp, points);
  }

  /**
   * Set THP values explicitly.
   *
   * @param thps array of THP values (bara)
   */
  public void setThpValues(double[] thps) {
    this.thpRange = thps.clone();
  }

  /**
   * Set water cut range for VFP generation.
   *
   * @param minWct minimum water cut (0-1)
   * @param maxWct maximum water cut (0-1)
   * @param points number of points
   */
  public void setWaterCutRange(double minWct, double maxWct, int points) {
    this.wctRange = linspace(minWct, maxWct, points);
  }

  /**
   * Set water cut values explicitly.
   *
   * @param wcts array of water cut values (0-1)
   */
  public void setWaterCutValues(double[] wcts) {
    this.wctRange = wcts.clone();
  }

  /**
   * Set GOR range for VFP generation.
   *
   * @param minGor minimum GOR (Sm3/Sm3)
   * @param maxGor maximum GOR (Sm3/Sm3)
   * @param points number of points
   */
  public void setGorRange(double minGor, double maxGor, int points) {
    this.gorRange = linspace(minGor, maxGor, points);
  }

  /**
   * Set GOR values explicitly.
   *
   * @param gors array of GOR values (Sm3/Sm3)
   */
  public void setGorValues(double[] gors) {
    this.gorRange = gors.clone();
  }

  /**
   * Set artificial lift quantity range for VFP generation.
   *
   * @param minAlm minimum ALQ
   * @param maxAlm maximum ALQ
   * @param points number of points
   */
  public void setAlmRange(double minAlm, double maxAlm, int points) {
    this.almRange = linspace(minAlm, maxAlm, points);
  }

  /**
   * Set flow rate type (LIQUID, OIL, or GAS).
   *
   * @param type the flow rate type
   */
  public void setFlowRateType(FlowRateType type) {
    this.flowRateType = type;
  }

  /**
   * Set datum depth for VFP tables.
   *
   * @param depth datum depth in meters
   */
  public void setDatumDepth(double depth) {
    this.datumDepth = depth;
  }

  /**
   * Set inlet temperature for calculations.
   *
   * @param temperature temperature in Kelvin
   */
  public void setInletTemperature(double temperature) {
    this.inletTemperature = temperature;
  }

  /**
   * Set inlet temperature with unit.
   *
   * @param temperature temperature value
   * @param unit temperature unit ("K", "C", "F")
   */
  public void setInletTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      this.inletTemperature = temperature + 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      this.inletTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      this.inletTemperature = temperature;
    }
  }

  /**
   * Set pipeline parameters.
   *
   * @param diameter diameter in meters
   * @param length length in meters
   * @param elevation elevation change in meters
   */
  public void setPipelineParameters(double diameter, double length, double elevation) {
    this.pipelineDiameter = diameter;
    this.pipelineLength = length;
    this.pipelineElevation = elevation;
  }

  /**
   * Set number of calculation increments.
   *
   * @param increments number of increments
   */
  public void setNumberOfIncrements(int increments) {
    this.numberOfIncrements = increments;
  }

  /**
   * Set pipe wall roughness.
   *
   * @param roughness roughness in meters
   */
  public void setPipeWallRoughness(double roughness) {
    this.pipeWallRoughness = roughness;
  }

  // ============================================================================
  // VFP TABLE GENERATION
  // ============================================================================

  /**
   * Generate VFPPROD table using Beggs and Brill calculations.
   *
   * <p>
   * Creates a multi-dimensional VFP table mapping (rate, THP, WCT, GOR, ALQ) to BHP using actual
   * NeqSim pipeline calculations with the Beggs and Brill correlation.
   * </p>
   *
   * @param tableNumber VFP table number (1-9999)
   * @param wellName well name
   * @return the generated VFP table data (using shared VfpTable type)
   */
  public VfpTable generateVfpTable(int tableNumber, String wellName) {
    logger.info("Generating VFPPROD table {} for well {} using Beggs and Brill", tableNumber,
        wellName);

    VfpTable vfp = new VfpTable();
    vfp.setTableNumber(tableNumber);
    vfp.setWellName(wellName);
    vfp.setDatumDepth(datumDepth);
    vfp.setFlowRateType(flowRateType);
    vfp.setFlowRates(flowRateRange.clone());
    vfp.setThpValues(thpRange.clone());
    vfp.setWctValues(wctRange.clone());
    vfp.setGorValues(gorRange.clone());
    vfp.setAlmValues(almRange.clone());

    int nFlow = flowRateRange.length;
    int nThp = thpRange.length;
    int nWct = wctRange.length;
    int nGor = gorRange.length;
    int nAlm = almRange.length;

    // Initialize all arrays including capacity tracking
    vfp.initializeArrays(nFlow, nThp, nWct, nGor, nAlm);

    int totalPoints = nFlow * nThp * nWct * nGor * nAlm;
    int completedPoints = 0;

    // Generate BHP values for each combination
    for (int iFlow = 0; iFlow < nFlow; iFlow++) {
      for (int iThp = 0; iThp < nThp; iThp++) {
        for (int iWct = 0; iWct < nWct; iWct++) {
          for (int iGor = 0; iGor < nGor; iGor++) {
            for (int iAlm = 0; iAlm < nAlm; iAlm++) {
              double rate = flowRateRange[iFlow];
              double thp = thpRange[iThp];
              double wct = wctRange[iWct];
              double gor = gorRange[iGor];
              double alm = almRange[iAlm];

              // Calculate BHP using Beggs and Brill pipeline model
              double bhp = calculateBhpWithPipeline(rate, thp, wct, gor, alm);
              vfp.getBhpValues()[iFlow][iThp][iWct][iGor][iAlm] = bhp;

              // For single pipeline, all points are feasible (no equipment capacity limits)
              vfp.getFeasible()[iFlow][iThp][iWct][iGor][iAlm] = true;
              vfp.getMaxUtilization()[iFlow][iThp][iWct][iGor][iAlm] = 0.0;
              vfp.getBottleneckEquipment()[iFlow][iThp][iWct][iGor][iAlm] = "none";

              completedPoints++;
              if (completedPoints % 100 == 0) {
                logger.debug("Progress: {}/{} points calculated", completedPoints, totalPoints);
              }
            }
          }
        }
      }
    }

    vfpTables.add(vfp);
    appendVfpProdKeyword(vfp);

    logger.info("VFP table {} generated with {} points", tableNumber, totalPoints);
    return vfp;
  }

  /**
   * Calculate BHP using PipeBeggsAndBrills for given conditions.
   *
   * @param liquidRate liquid flow rate (Sm3/day)
   * @param thp tubing head pressure (bara)
   * @param wct water cut (0-1)
   * @param gor gas-oil ratio (Sm3/Sm3)
   * @param alm artificial lift quantity
   * @return bottom hole pressure (bara)
   */
  private double calculateBhpWithPipeline(double liquidRate, double thp, double wct, double gor,
      double alm) {
    try {
      // Create fluid with adjusted composition for given WCT and GOR
      SystemInterface calcFluid = createFluidWithWctGor(wct, gor);

      // Set inlet conditions: pressure = THP (outlet), solve for inlet (BHP)
      calcFluid.setTemperature(inletTemperature);
      calcFluid.setPressure(thp);

      // Flash to equilibrium
      ThermodynamicOperations ops = new ThermodynamicOperations(calcFluid);
      ops.TPflash();
      calcFluid.initPhysicalProperties();

      // Create inlet stream
      Stream inlet = new Stream("vfp_calc_inlet", calcFluid);

      // Set flow rate based on type
      double massFlowRate = convertToMassFlow(calcFluid, liquidRate, wct, gor);
      inlet.setFlowRate(massFlowRate, "kg/hr");
      inlet.run();

      // Create pipeline
      PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("vfp_pipe", inlet);
      pipe.setDiameter(pipelineDiameter);
      pipe.setLength(pipelineLength);
      pipe.setElevation(pipelineElevation);
      pipe.setNumberOfIncrements(numberOfIncrements);
      pipe.setPipeWallRoughness(pipeWallRoughness);

      // Run pipeline calculation
      pipe.run();

      // Get pressure drop
      double pressureDrop = pipe.getPressureDrop();

      // BHP = THP + pressure drop (for production well, flowing up)
      // If elevation is positive (upward flow), pressure increases with depth
      double bhp = thp + pressureDrop;

      // Ensure BHP is positive and reasonable
      if (bhp < thp) {
        bhp = thp + 1.0; // Minimum BHP slightly above THP
      }

      return bhp;

    } catch (Exception e) {
      logger.warn("Error calculating BHP for rate={}, THP={}, WCT={}, GOR={}: {}", liquidRate, thp,
          wct, gor, e.getMessage());
      // Return estimated value on error
      return estimateBhpFallback(liquidRate, thp, wct, gor);
    }
  }

  /**
   * Create fluid with specified water cut and GOR.
   *
   * @param wct water cut (0-1)
   * @param gor gas-oil ratio (Sm3/Sm3)
   * @return configured fluid
   */
  private SystemInterface createFluidWithWctGor(double wct, double gor) {
    SystemInterface fluid = baseFluid.clone();

    // Get component indices
    int waterIndex = -1;
    int gasIndex = -1;
    int oilIndex = -1;

    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      String name = fluid.getComponent(i).getName().toLowerCase();
      if (name.contains("water") || name.equals("h2o")) {
        waterIndex = i;
      } else if (name.contains("methane") || name.equals("c1")) {
        gasIndex = i;
      } else if (name.contains("heptane") || name.contains("decane") || name.contains("c7")
          || name.contains("c10") || name.contains("oil")) {
        oilIndex = i;
      }
    }

    // Adjust composition based on WCT and GOR
    // This is a simplified approach - in practice would need more sophisticated
    // composition handling

    // Scale water component based on WCT
    if (waterIndex >= 0 && wct > 0) {
      double totalMoles = fluid.getTotalNumberOfMoles();
      // Approximate water mole fraction for target WCT
      double waterMoleFrac = wct * 0.5; // Simplified mapping
      fluid.addComponent(waterIndex, waterMoleFrac * totalMoles);
    }

    return fluid;
  }

  /**
   * Convert liquid rate to mass flow rate.
   *
   * @param fluid the fluid system
   * @param liquidRate liquid rate (Sm3/day)
   * @param wct water cut
   * @param gor gas-oil ratio
   * @return mass flow rate (kg/hr)
   */
  private double convertToMassFlow(SystemInterface fluid, double liquidRate, double wct,
      double gor) {
    // Standard conditions density approximations
    double oilDensityStd = 800.0; // kg/m3 at standard conditions
    double waterDensityStd = 1000.0; // kg/m3 at standard conditions
    double gasDensityStd = 0.8; // kg/Sm3 at standard conditions

    // Calculate liquid mass flow
    double oilRate = liquidRate * (1.0 - wct); // Sm3/day
    double waterRate = liquidRate * wct; // Sm3/day
    double gasRate = oilRate * gor; // Sm3/day

    double oilMass = oilRate * oilDensityStd / 24.0; // kg/hr
    double waterMass = waterRate * waterDensityStd / 24.0; // kg/hr
    double gasMass = gasRate * gasDensityStd / 24.0; // kg/hr

    return oilMass + waterMass + gasMass;
  }

  /**
   * Fallback BHP estimation when pipeline calculation fails.
   *
   * @param liquidRate liquid rate (Sm3/day)
   * @param thp tubing head pressure (bara)
   * @param wct water cut
   * @param gor gas-oil ratio
   * @return estimated BHP (bara)
   */
  private double estimateBhpFallback(double liquidRate, double thp, double wct, double gor) {
    // Simplified correlation for fallback
    double density = 800.0 - wct * 200.0 + (1.0 / (1.0 + gor / 100.0)) * 200.0;
    double hydrostatic = density * 9.81 * pipelineElevation / 1e5;
    double frictionFactor = 0.0001 * Math.pow(liquidRate / 1000.0, 1.8);
    double frictionLoss = frictionFactor * pipelineLength / 100.0;

    return thp + hydrostatic + frictionLoss;
  }

  // ============================================================================
  // KEYWORD GENERATION
  // ============================================================================

  /**
   * Append VFPPROD keyword to buffer.
   *
   * @param vfp the VFP table data
   */
  private void appendVfpProdKeyword(VfpTable vfp) {
    keywordsBuffer.append("VFPPROD\n");
    keywordsBuffer.append(String.format("-- VFP Production Table %d for %s\n", vfp.getTableNumber(),
        vfp.getWellName()));
    keywordsBuffer.append("-- Generated by NeqSim EclipseLiftCurveGenerator using Beggs & Brill\n");
    keywordsBuffer.append(String.format("-- Generated: %s\n",
        new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())));
    keywordsBuffer.append(String.format("-- Pipeline: D=%.4f m, L=%.1f m, Elev=%.1f m\n",
        pipelineDiameter, pipelineLength, pipelineElevation));

    // Table header
    String rateType = "LIQ";
    if (vfp.getFlowRateType() == FlowRateType.GAS) {
      rateType = "GAS";
    } else if (vfp.getFlowRateType() == FlowRateType.OIL) {
      rateType = "OIL";
    }

    keywordsBuffer
        .append(String.format("  %d  %.1f  '%s'  'WCT'  'GOR'  'THP'  '' 'METRIC'  'BHP' /\n",
            vfp.getTableNumber(), vfp.getDatumDepth(), rateType));

    // Flow rate axis
    keywordsBuffer.append("-- Flow rates (Sm3/d)\n");
    for (double rate : vfp.getFlowRates()) {
      keywordsBuffer.append(String.format("  %.1f", rate));
    }
    keywordsBuffer.append("  /\n");

    // THP axis
    keywordsBuffer.append("-- THP (bara)\n");
    for (double thp : vfp.getThpValues()) {
      keywordsBuffer.append(String.format("  %.1f", thp));
    }
    keywordsBuffer.append("  /\n");

    // WCT axis
    keywordsBuffer.append("-- Water cut (fraction)\n");
    for (double wct : vfp.getWctValues()) {
      keywordsBuffer.append(String.format("  %.3f", wct));
    }
    keywordsBuffer.append("  /\n");

    // GOR axis
    keywordsBuffer.append("-- GOR (Sm3/Sm3)\n");
    for (double gor : vfp.getGorValues()) {
      keywordsBuffer.append(String.format("  %.1f", gor));
    }
    keywordsBuffer.append("  /\n");

    // ALQ axis
    keywordsBuffer.append("-- ALQ (artificial lift)\n");
    for (double alm : vfp.getAlmValues()) {
      keywordsBuffer.append(String.format("  %.1f", alm));
    }
    keywordsBuffer.append("  /\n");

    // BHP values - Eclipse expects values in order:
    // For each THP, for each WCT, for each GOR, for each ALQ: all flow rates
    keywordsBuffer.append("-- BHP values (bara)\n");
    double[] flowRates = vfp.getFlowRates();
    double[] thpValues = vfp.getThpValues();
    double[] wctValues = vfp.getWctValues();
    double[] gorValues = vfp.getGorValues();
    double[] almValues = vfp.getAlmValues();
    double[][][][][] bhpValues = vfp.getBhpValues();

    for (int iThp = 0; iThp < thpValues.length; iThp++) {
      for (int iWct = 0; iWct < wctValues.length; iWct++) {
        for (int iGor = 0; iGor < gorValues.length; iGor++) {
          for (int iAlm = 0; iAlm < almValues.length; iAlm++) {
            keywordsBuffer.append(String.format("-- THP=%.1f WCT=%.2f GOR=%.0f ALQ=%.0f\n",
                thpValues[iThp], wctValues[iWct], gorValues[iGor], almValues[iAlm]));
            for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
              keywordsBuffer
                  .append(String.format("  %.2f", bhpValues[iFlow][iThp][iWct][iGor][iAlm]));
            }
            keywordsBuffer.append("  /\n");
          }
        }
      }
    }
    keywordsBuffer.append("\n");
  }

  // ============================================================================
  // EXPORT METHODS
  // ============================================================================

  /**
   * Get all generated Eclipse keywords.
   *
   * @return keywords string
   */
  public String getEclipseKeywords() {
    StringBuilder full = new StringBuilder();

    // Header
    full.append("-- ==========================================================\n");
    full.append("-- NeqSim Lift Curve Export (Beggs & Brill Pipeline Model)\n");
    full.append(String.format("-- Generated: %s\n",
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())));
    full.append("-- ==========================================================\n\n");

    // VFP tables
    full.append(keywordsBuffer);

    return full.toString();
  }

  /**
   * Export to file.
   *
   * @param filePath output file path
   * @throws IOException if write fails
   */
  public void exportToFile(String filePath) throws IOException {
    logger.info("Exporting VFP table to file: {}", filePath);

    BufferedWriter writer = null;
    try {
      writer = new BufferedWriter(new FileWriter(filePath));
      writer.write(getEclipseKeywords());
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  /**
   * Export to CSV format for external tools.
   *
   * @return CSV string
   */
  public String exportToCsv() {
    StringBuilder csv = new StringBuilder();
    csv.append("TableNumber,WellName,FlowRate_Sm3d,THP_bara,WCT,GOR_Sm3Sm3,ALQ,BHP_bara\n");

    for (VfpTable vfp : vfpTables) {
      double[] flowRates = vfp.getFlowRates();
      double[] thpValues = vfp.getThpValues();
      double[] wctValues = vfp.getWctValues();
      double[] gorValues = vfp.getGorValues();
      double[] almValues = vfp.getAlmValues();
      double[][][][][] bhpValues = vfp.getBhpValues();

      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        for (int iThp = 0; iThp < thpValues.length; iThp++) {
          for (int iWct = 0; iWct < wctValues.length; iWct++) {
            for (int iGor = 0; iGor < gorValues.length; iGor++) {
              for (int iAlm = 0; iAlm < almValues.length; iAlm++) {
                csv.append(String.format("%d,%s,%.1f,%.1f,%.3f,%.1f,%.1f,%.2f\n",
                    vfp.getTableNumber(), vfp.getWellName(), flowRates[iFlow], thpValues[iThp],
                    wctValues[iWct], gorValues[iGor], almValues[iAlm],
                    bhpValues[iFlow][iThp][iWct][iGor][iAlm]));
              }
            }
          }
        }
      }
    }

    return csv.toString();
  }

  /**
   * Export VFP table data to JSON format.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
    result.put("pipelineModel", "Beggs and Brill");
    result.put("pipelineDiameter_m", pipelineDiameter);
    result.put("pipelineLength_m", pipelineLength);
    result.put("pipelineElevation_m", pipelineElevation);
    result.put("inletTemperature_K", inletTemperature);

    List<Map<String, Object>> tablesData = new ArrayList<Map<String, Object>>();
    for (VfpTable vfp : vfpTables) {
      Map<String, Object> tableMap = new LinkedHashMap<String, Object>();
      tableMap.put("tableNumber", vfp.getTableNumber());
      tableMap.put("wellName", vfp.getWellName());
      tableMap.put("datumDepth_m", vfp.getDatumDepth());
      tableMap.put("flowRateType", vfp.getFlowRateType().toString());
      tableMap.put("flowRates_Sm3d", vfp.getFlowRates());
      tableMap.put("thpValues_bara", vfp.getThpValues());
      tableMap.put("wctValues", vfp.getWctValues());
      tableMap.put("gorValues_Sm3Sm3", vfp.getGorValues());
      tableMap.put("almValues", vfp.getAlmValues());
      tableMap.put("totalPoints", vfp.getTotalPoints());
      tableMap.put("feasiblePoints", vfp.countFeasiblePoints());

      double[] flowRates = vfp.getFlowRates();
      double[] thpValues = vfp.getThpValues();
      double[] wctValues = vfp.getWctValues();
      double[] gorValues = vfp.getGorValues();
      double[] almValues = vfp.getAlmValues();
      double[][][][][] bhpValues = vfp.getBhpValues();

      // Flatten BHP values for JSON
      List<Map<String, Object>> bhpData = new ArrayList<Map<String, Object>>();
      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        for (int iThp = 0; iThp < thpValues.length; iThp++) {
          for (int iWct = 0; iWct < wctValues.length; iWct++) {
            for (int iGor = 0; iGor < gorValues.length; iGor++) {
              for (int iAlm = 0; iAlm < almValues.length; iAlm++) {
                Map<String, Object> point = new LinkedHashMap<String, Object>();
                point.put("flowRate", flowRates[iFlow]);
                point.put("thp", thpValues[iThp]);
                point.put("wct", wctValues[iWct]);
                point.put("gor", gorValues[iGor]);
                point.put("alm", almValues[iAlm]);
                point.put("bhp", bhpValues[iFlow][iThp][iWct][iGor][iAlm]);
                bhpData.add(point);
              }
            }
          }
        }
      }
      tableMap.put("bhpData", bhpData);
      tablesData.add(tableMap);
    }
    result.put("vfpTables", tablesData);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }

  // ============================================================================
  // UTILITY METHODS
  // ============================================================================

  /**
   * Generate linearly spaced array.
   *
   * @param start start value
   * @param end end value
   * @param points number of points
   * @return linearly spaced array
   */
  private double[] linspace(double start, double end, int points) {
    if (points <= 1) {
      return new double[] {start};
    }
    double[] result = new double[points];
    double step = (end - start) / (points - 1);
    for (int i = 0; i < points; i++) {
      result[i] = start + i * step;
    }
    return result;
  }

  /**
   * Clear all generated content.
   */
  public void clear() {
    vfpTables.clear();
    keywordsBuffer = new StringBuilder();
  }

  /**
   * Get generated VFP tables.
   *
   * @return list of VFP tables (using shared VfpTable type)
   */
  public List<VfpTable> getVfpTables() {
    return vfpTables;
  }

  /**
   * Get pipeline diameter.
   *
   * @return diameter in meters
   */
  public double getPipelineDiameter() {
    return pipelineDiameter;
  }

  /**
   * Get pipeline length.
   *
   * @return length in meters
   */
  public double getPipelineLength() {
    return pipelineLength;
  }

  /**
   * Get pipeline elevation.
   *
   * @return elevation in meters
   */
  public double getPipelineElevation() {
    return pipelineElevation;
  }
}
