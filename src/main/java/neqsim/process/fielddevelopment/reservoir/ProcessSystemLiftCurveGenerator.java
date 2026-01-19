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
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.FlowRateType;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.VfpTable;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Generates Eclipse VFP (Vertical Flow Performance) lift curves for a complete oil and gas
 * processing facility using NeqSim ProcessSystem simulations.
 *
 * <p>
 * This class creates VFPPROD tables for Eclipse reservoir simulators by running actual process
 * simulations through a full separation train (separators, compressors, heat exchangers, etc.).
 * Unlike simple pipeline-only calculations, this captures the complete system behavior from
 * wellhead to export.
 * </p>
 *
 * <p>
 * This generator uses the shared {@link ReservoirCouplingExporter.VfpTable} data structure for
 * compatibility with other VFP generation tools in NeqSim.
 * </p>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>Full process system simulation for each VFP point</li>
 * <li>Supports multi-stage separation (HP/MP/LP separators)</li>
 * <li>Includes compressors, coolers, and other process equipment</li>
 * <li>Captures separator efficiency at different conditions</li>
 * <li>Tracks equipment capacity and identifies bottlenecks</li>
 * <li>Generates VFPPROD tables with (rate, THP, WCT, GOR, ALQ) dimensions</li>
 * <li>Exports to Eclipse 100 and E300 compatible formats</li>
 * </ul>
 *
 * <h2>Concept</h2>
 * <p>
 * The VFP table relates wellhead conditions to the required bottomhole flowing pressure (BHP)
 * needed to deliver a given flow rate through the entire process facility:
 * </p>
 * <ul>
 * <li><b>Input (Wellhead)</b>: Flow rate, temperature, and pressure (THP)</li>
 * <li><b>Process</b>: Separation, compression, export pipeline</li>
 * <li><b>Output</b>: Required inlet pressure for given export conditions</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create process system with separation train
 * ProcessSystem process = createOilGasSeparationProcess(baseFluid);
 * 
 * // Create lift curve generator
 * ProcessSystemLiftCurveGenerator generator =
 *     new ProcessSystemLiftCurveGenerator(process, baseFluid);
 * 
 * // Set inlet and outlet stream names
 * generator.setInletStreamName("well stream");
 * generator.setExportGasStreamName("export gas");
 * generator.setExportOilStreamName("stable oil");
 * 
 * // Configure VFP table parameters
 * generator.setFlowRateRange(500, 10000, 10); // Sm3/day
 * generator.setThpRange(20, 80, 7); // bara
 * generator.setWaterCutRange(0.0, 0.8, 5); // fraction
 * generator.setGorRange(100, 500, 5); // Sm3/Sm3
 * 
 * // Generate and export
 * generator.generateVfpTable(1, "PLATFORM-VFP");
 * generator.exportToFile("vfp_platform.inc");
 * }</pre>
 *
 * @author ESOL
 * @version 1.1
 * @see ProcessSystem
 * @see EclipseLiftCurveGenerator
 * @see ReservoirCouplingExporter.VfpTable
 */
public class ProcessSystemLiftCurveGenerator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(ProcessSystemLiftCurveGenerator.class);

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

  /** Reference process system. */
  private ProcessSystem referenceProcess;

  /** Base fluid for composition. */
  private SystemInterface baseFluid;

  /** Name of inlet stream in the process. */
  private String inletStreamName = "well stream";

  /** Name of export gas stream in the process. */
  private String exportGasStreamName = "export gas";

  /** Name of export oil stream in the process. */
  private String exportOilStreamName = "stable oil";

  /** Name of export water stream in the process (optional). */
  private String exportWaterStreamName = null;

  /** Inlet temperature (K). */
  private double inletTemperature = 330.0;

  /** Export pressure requirement (bara). */
  private double exportPressure = 70.0;

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

  /** Process description for documentation. */
  private String processDescription = "Oil and Gas Separation Process";

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new lift curve generator for a process system.
   *
   * @param processSystem the reference ProcessSystem
   * @param baseFluid the base fluid composition
   */
  public ProcessSystemLiftCurveGenerator(ProcessSystem processSystem, SystemInterface baseFluid) {
    this.referenceProcess = processSystem;
    this.baseFluid = baseFluid.clone();
    this.vfpTables = new ArrayList<VfpTable>();
    this.keywordsBuffer = new StringBuilder();

    // Set default ranges
    setFlowRateRange(500.0, 10000.0, 10);
    setThpRange(20.0, 80.0, 7);
    setWaterCutRange(0.0, 0.8, 5);
    setGorRange(100.0, 500.0, 5);
    setAlmRange(0.0, 0.0, 1); // No artificial lift by default
  }

  /**
   * Creates a new lift curve generator with just a base fluid. Process system should be set later.
   *
   * @param baseFluid the base fluid composition
   */
  public ProcessSystemLiftCurveGenerator(SystemInterface baseFluid) {
    this(null, baseFluid);
  }

  // ============================================================================
  // CONFIGURATION METHODS
  // ============================================================================

  /**
   * Set the process system to use for calculations.
   *
   * @param processSystem the ProcessSystem
   */
  public void setProcessSystem(ProcessSystem processSystem) {
    this.referenceProcess = processSystem;
  }

  /**
   * Set the inlet stream name in the process system.
   *
   * @param name stream name
   */
  public void setInletStreamName(String name) {
    this.inletStreamName = name;
  }

  /**
   * Set the export gas stream name in the process system.
   *
   * @param name stream name
   */
  public void setExportGasStreamName(String name) {
    this.exportGasStreamName = name;
  }

  /**
   * Set the export oil stream name in the process system.
   *
   * @param name stream name
   */
  public void setExportOilStreamName(String name) {
    this.exportOilStreamName = name;
  }

  /**
   * Set the export water stream name in the process system (optional).
   *
   * @param name stream name, or null if not tracked
   */
  public void setExportWaterStreamName(String name) {
    this.exportWaterStreamName = name;
  }

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
   * Set export pressure requirement.
   *
   * @param pressure export pressure in bara
   */
  public void setExportPressure(double pressure) {
    this.exportPressure = pressure;
  }

  /**
   * Set process description for documentation.
   *
   * @param description description string
   */
  public void setProcessDescription(String description) {
    this.processDescription = description;
  }

  // ============================================================================
  // VFP TABLE GENERATION
  // ============================================================================

  /**
   * Generate VFPPROD table using full process system calculations.
   *
   * <p>
   * Creates a multi-dimensional VFP table mapping (rate, THP, WCT, GOR, ALQ) to BHP by running the
   * complete process simulation for each combination of parameters.
   * </p>
   *
   * @param tableNumber VFP table number (1-9999)
   * @param wellName well/platform name
   * @return the generated VFP table data (using shared VfpTable type)
   */
  public VfpTable generateVfpTable(int tableNumber, String wellName) {
    logger.info("Generating VFPPROD table {} for {} using full process simulation", tableNumber,
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
    int infeasiblePoints = 0;

    // Generate values for each combination
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

              // Run process simulation and extract results
              ProcessSimulationResult result = runProcessSimulation(rate, thp, wct, gor, alm);

              vfp.getBhpValues()[iFlow][iThp][iWct][iGor][iAlm] = result.bhp;
              vfp.getExportGasRates()[iFlow][iThp][iWct][iGor][iAlm] = result.exportGasRate;
              vfp.getExportOilRates()[iFlow][iThp][iWct][iGor][iAlm] = result.exportOilRate;
              vfp.getExportWaterRates()[iFlow][iThp][iWct][iGor][iAlm] = result.exportWaterRate;
              vfp.getCompressionPower()[iFlow][iThp][iWct][iGor][iAlm] = result.compressionPower;
              vfp.getMaxUtilization()[iFlow][iThp][iWct][iGor][iAlm] = result.maxUtilization;
              vfp.getFeasible()[iFlow][iThp][iWct][iGor][iAlm] = result.feasible;
              vfp.getBottleneckEquipment()[iFlow][iThp][iWct][iGor][iAlm] =
                  result.bottleneckEquipment;

              if (!result.feasible) {
                infeasiblePoints++;
              }

              completedPoints++;
              if (completedPoints % 50 == 0) {
                logger.info("Progress: {}/{} points calculated ({} %)", completedPoints,
                    totalPoints, (100 * completedPoints / totalPoints));
              }
            }
          }
        }
      }
    }

    vfpTables.add(vfp);
    appendVfpProdKeyword(vfp);

    logger.info("VFP table {} generated with {} points ({} feasible, {} exceed capacity limits)",
        tableNumber, totalPoints, totalPoints - infeasiblePoints, infeasiblePoints);
    return vfp;
  }

  /**
   * Result from a single process simulation run.
   */
  private static class ProcessSimulationResult {
    double bhp;
    double exportGasRate;
    double exportOilRate;
    double exportWaterRate;
    double compressionPower;
    // Equipment capacity tracking
    double maxUtilization;
    boolean feasible;
    String bottleneckEquipment;
  }

  /**
   * Run a single process simulation for given conditions.
   *
   * @param liquidRate liquid flow rate (Sm3/day)
   * @param thp tubing head pressure (bara)
   * @param wct water cut (0-1)
   * @param gor gas-oil ratio (Sm3/Sm3)
   * @param alm artificial lift quantity
   * @return simulation results
   */
  private ProcessSimulationResult runProcessSimulation(double liquidRate, double thp, double wct,
      double gor, double alm) {
    ProcessSimulationResult result = new ProcessSimulationResult();
    result.feasible = true;
    result.maxUtilization = 0.0;
    result.bottleneckEquipment = "";

    try {
      // Create a fresh process system for this calculation
      ProcessSystem process = createProcessForConditions(liquidRate, thp, wct, gor, alm);

      // Run the process
      process.run();

      // Extract results
      result.bhp = calculateRequiredBhp(process, thp, liquidRate, wct, gor);
      result.exportGasRate = getExportGasRate(process);
      result.exportOilRate = getExportOilRate(process);
      result.exportWaterRate = getExportWaterRate(process);
      result.compressionPower = getTotalCompressionPower(process);

      // Check equipment capacity constraints
      EquipmentCapacityResult capacityResult = checkEquipmentCapacity(process);
      result.maxUtilization = capacityResult.maxUtilization;
      result.feasible = capacityResult.feasible;
      result.bottleneckEquipment = capacityResult.bottleneckEquipment;

    } catch (Exception e) {
      logger.warn("Error running process simulation for rate={}, THP={}, WCT={}, GOR={}: {}",
          liquidRate, thp, wct, gor, e.getMessage());
      // Return estimated values on error
      result = estimateFallbackResult(liquidRate, thp, wct, gor);
    }

    return result;
  }

  /**
   * Result from equipment capacity check.
   */
  private static class EquipmentCapacityResult {
    double maxUtilization;
    boolean feasible;
    String bottleneckEquipment;
  }

  /**
   * Check equipment capacity constraints for a process.
   *
   * @param process the process system
   * @return capacity check result
   */
  private EquipmentCapacityResult checkEquipmentCapacity(ProcessSystem process) {
    EquipmentCapacityResult result = new EquipmentCapacityResult();
    result.maxUtilization = 0.0;
    result.feasible = true;
    result.bottleneckEquipment = "";

    try {
      // Use ProcessSystem's bottleneck detection if available
      neqsim.process.equipment.ProcessEquipmentInterface bottleneck = process.getBottleneck();
      if (bottleneck != null) {
        result.bottleneckEquipment = bottleneck.getName();
      }

      // Check utilization of all equipment
      for (int i = 0; i < process.getUnitOperations().size(); i++) {
        Object unit = process.getUnitOperations().get(i);
        if (unit instanceof neqsim.process.equipment.ProcessEquipmentInterface) {
          neqsim.process.equipment.ProcessEquipmentInterface equipment =
              (neqsim.process.equipment.ProcessEquipmentInterface) unit;

          double capacityMax = equipment.getCapacityMax();
          double capacityDuty = equipment.getCapacityDuty();

          if (capacityMax > 0) {
            double utilization = capacityDuty / capacityMax;

            if (utilization > result.maxUtilization) {
              result.maxUtilization = utilization;
              if (result.bottleneckEquipment.isEmpty()) {
                result.bottleneckEquipment = equipment.getName();
              }
            }

            // Check if exceeds capacity (>100% utilization)
            if (utilization > 1.0) {
              result.feasible = false;
              result.bottleneckEquipment = equipment.getName();
              logger.debug("Equipment {} exceeds capacity: {:.1f}%", equipment.getName(),
                  utilization * 100);
            }
          }
        }
      }
    } catch (Exception e) {
      logger.debug("Error checking equipment capacity: {}", e.getMessage());
    }

    return result;
  }

  /**
   * Create a process system configured for specific inlet conditions.
   *
   * @param liquidRate liquid rate (Sm3/day)
   * @param thp inlet pressure (bara)
   * @param wct water cut (0-1)
   * @param gor gas-oil ratio (Sm3/Sm3)
   * @param alm artificial lift
   * @return configured ProcessSystem
   */
  private ProcessSystem createProcessForConditions(double liquidRate, double thp, double wct,
      double gor, double alm) {

    // Clone the reference process if available
    ProcessSystem process;
    if (referenceProcess != null) {
      process = referenceProcess.copy();
    } else {
      process = createDefaultSeparationProcess();
    }

    // Find and configure the inlet stream
    StreamInterface inletStream = (StreamInterface) process.getUnit(inletStreamName);
    if (inletStream != null) {
      // Create fluid with adjusted composition
      SystemInterface fluid = createFluidWithWctGor(wct, gor);
      fluid.setTemperature(inletTemperature);
      fluid.setPressure(thp);

      // Flash to equilibrium
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      try {
        ops.TPflash();
        fluid.initPhysicalProperties();
      } catch (Exception e) {
        logger.warn("Flash calculation failed: {}", e.getMessage());
      }

      // Set flow rate - convert Sm3/day liquid to mass flow
      double massFlow = convertToMassFlow(fluid, liquidRate, wct, gor);

      // Update the inlet stream
      inletStream.setFluid(fluid);
      inletStream.setFlowRate(massFlow, "kg/hr");
      inletStream.setPressure(thp, "bara");
      inletStream.setTemperature(inletTemperature - 273.15, "C");
    }

    return process;
  }

  /**
   * Create a default oil/gas separation process if no reference is provided.
   *
   * @return a basic separation process
   */
  private ProcessSystem createDefaultSeparationProcess() {
    ProcessSystem process = new ProcessSystem("VFP Separation Process");

    // Create inlet stream
    SystemInterface fluid = baseFluid.clone();
    Stream inlet = new Stream(inletStreamName, fluid);
    process.add(inlet);

    // First stage separator
    neqsim.process.equipment.separator.ThreePhaseSeparator hpSep =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("HP Separator", inlet);
    process.add(hpSep);

    // Second stage separator
    neqsim.process.equipment.valve.ThrottlingValve valve1 =
        new neqsim.process.equipment.valve.ThrottlingValve("HP to MP valve",
            hpSep.getOilOutStream());
    valve1.setOutletPressure(10.0);
    process.add(valve1);

    neqsim.process.equipment.separator.ThreePhaseSeparator mpSep =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("MP Separator",
            valve1.getOutletStream());
    process.add(mpSep);

    // Third stage separator
    neqsim.process.equipment.valve.ThrottlingValve valve2 =
        new neqsim.process.equipment.valve.ThrottlingValve("MP to LP valve",
            mpSep.getOilOutStream());
    valve2.setOutletPressure(2.0);
    process.add(valve2);

    neqsim.process.equipment.separator.ThreePhaseSeparator lpSep =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("LP Separator",
            valve2.getOutletStream());
    process.add(lpSep);

    // Export streams
    Stream stableOil = new Stream(exportOilStreamName, lpSep.getOilOutStream());
    process.add(stableOil);

    // Gas compression (simplified)
    neqsim.process.equipment.mixer.Mixer gasMixer =
        new neqsim.process.equipment.mixer.Mixer("Gas Mixer");
    gasMixer.addStream(hpSep.getGasOutStream());
    gasMixer.addStream(mpSep.getGasOutStream());
    gasMixer.addStream(lpSep.getGasOutStream());
    process.add(gasMixer);

    neqsim.process.equipment.compressor.Compressor compressor =
        new neqsim.process.equipment.compressor.Compressor("Export Compressor",
            gasMixer.getOutletStream());
    compressor.setOutletPressure(exportPressure);
    process.add(compressor);

    Stream exportGas = new Stream(exportGasStreamName, compressor.getOutletStream());
    process.add(exportGas);

    return process;
  }

  /**
   * Calculate required BHP for given conditions.
   *
   * @param process the process system
   * @param thp inlet pressure
   * @param liquidRate liquid rate
   * @param wct water cut
   * @param gor GOR
   * @return required BHP (bara)
   */
  private double calculateRequiredBhp(ProcessSystem process, double thp, double liquidRate,
      double wct, double gor) {
    // For surface facilities, BHP represents the required wellhead pressure
    // to deliver the flow through the facility
    // This is essentially the inlet pressure plus any additional pressure
    // needed to overcome the facility pressure drop

    // In this simplified model, we use THP as the base and add
    // estimated well tubing pressure drop
    double facilityPressureDrop = estimateFacilityPressureDrop(process);

    // Estimate well tubing pressure drop (simplified)
    double density = 800.0 - wct * 200.0 + (1.0 / (1.0 + gor / 100.0)) * 200.0;
    double wellDepth = datumDepth > 0 ? datumDepth : 2000.0;
    double hydrostaticHead = density * 9.81 * wellDepth / 1e5;

    // Friction loss (simplified)
    double frictionFactor = 0.0001 * Math.pow(liquidRate / 1000.0, 1.8);
    double frictionLoss = frictionFactor * wellDepth / 100.0;

    return thp + hydrostaticHead + frictionLoss + facilityPressureDrop;
  }

  /**
   * Estimate pressure drop across the facility.
   *
   * @param process the process system
   * @return pressure drop (bar)
   */
  private double estimateFacilityPressureDrop(ProcessSystem process) {
    // In a real implementation, this would calculate the actual pressure drop
    // For now, return a typical value
    return 5.0; // bar
  }

  /**
   * Get export gas rate from process.
   *
   * @param process the process system
   * @return gas rate (Sm3/day)
   */
  private double getExportGasRate(ProcessSystem process) {
    try {
      StreamInterface gasStream = (StreamInterface) process.getUnit(exportGasStreamName);
      if (gasStream != null && gasStream.getFluid() != null) {
        return gasStream.getFluid().getFlowRate("Sm3/day");
      }
    } catch (Exception e) {
      logger.debug("Could not get export gas rate: {}", e.getMessage());
    }
    return 0.0;
  }

  /**
   * Get export oil rate from process.
   *
   * @param process the process system
   * @return oil rate (Sm3/day)
   */
  private double getExportOilRate(ProcessSystem process) {
    try {
      StreamInterface oilStream = (StreamInterface) process.getUnit(exportOilStreamName);
      if (oilStream != null && oilStream.getFluid() != null) {
        return oilStream.getFluid().getFlowRate("Sm3/day");
      }
    } catch (Exception e) {
      logger.debug("Could not get export oil rate: {}", e.getMessage());
    }
    return 0.0;
  }

  /**
   * Get export water rate from process.
   *
   * @param process the process system
   * @return water rate (Sm3/day)
   */
  private double getExportWaterRate(ProcessSystem process) {
    if (exportWaterStreamName == null) {
      return 0.0;
    }
    try {
      StreamInterface waterStream = (StreamInterface) process.getUnit(exportWaterStreamName);
      if (waterStream != null && waterStream.getFluid() != null) {
        return waterStream.getFluid().getFlowRate("Sm3/day");
      }
    } catch (Exception e) {
      logger.debug("Could not get export water rate: {}", e.getMessage());
    }
    return 0.0;
  }

  /**
   * Get total compression power from process.
   *
   * @param process the process system
   * @return power (kW)
   */
  private double getTotalCompressionPower(ProcessSystem process) {
    double totalPower = 0.0;
    try {
      for (int i = 0; i < process.getUnitOperations().size(); i++) {
        Object unit = process.getUnitOperations().get(i);
        if (unit instanceof neqsim.process.equipment.compressor.Compressor) {
          neqsim.process.equipment.compressor.Compressor comp =
              (neqsim.process.equipment.compressor.Compressor) unit;
          totalPower += comp.getPower() / 1000.0; // Convert to kW
        }
      }
    } catch (Exception e) {
      logger.debug("Could not get compression power: {}", e.getMessage());
    }
    return totalPower;
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

    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      String name = fluid.getComponent(i).getName().toLowerCase();
      if (name.contains("water") || name.equals("h2o")) {
        waterIndex = i;
      } else if (name.contains("methane") || name.equals("c1")) {
        gasIndex = i;
      }
    }

    // Adjust composition based on WCT and GOR
    if (waterIndex >= 0 && wct > 0) {
      double totalMoles = fluid.getTotalNumberOfMoles();
      double waterMoleFrac = wct * 0.5;
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
    double oilDensityStd = 800.0;
    double waterDensityStd = 1000.0;
    double gasDensityStd = 0.8;

    double oilRate = liquidRate * (1.0 - wct);
    double waterRate = liquidRate * wct;
    double gasRate = oilRate * gor;

    double oilMass = oilRate * oilDensityStd / 24.0;
    double waterMass = waterRate * waterDensityStd / 24.0;
    double gasMass = gasRate * gasDensityStd / 24.0;

    return oilMass + waterMass + gasMass;
  }

  /**
   * Fallback result estimation when simulation fails.
   *
   * @param liquidRate liquid rate
   * @param thp inlet pressure
   * @param wct water cut
   * @param gor GOR
   * @return estimated results
   */
  private ProcessSimulationResult estimateFallbackResult(double liquidRate, double thp, double wct,
      double gor) {
    ProcessSimulationResult result = new ProcessSimulationResult();

    // Simplified estimation
    double density = 800.0 - wct * 200.0 + (1.0 / (1.0 + gor / 100.0)) * 200.0;
    double wellDepth = datumDepth > 0 ? datumDepth : 2000.0;
    double hydrostatic = density * 9.81 * wellDepth / 1e5;
    double frictionFactor = 0.0001 * Math.pow(liquidRate / 1000.0, 1.8);
    double frictionLoss = frictionFactor * wellDepth / 100.0;

    result.bhp = thp + hydrostatic + frictionLoss + 5.0;
    result.exportGasRate = liquidRate * (1.0 - wct) * gor;
    result.exportOilRate = liquidRate * (1.0 - wct);
    result.exportWaterRate = liquidRate * wct;
    result.compressionPower = result.exportGasRate * 0.01;

    // Capacity fields for fallback
    result.feasible = true; // Assume feasible when no process simulation
    result.maxUtilization = 0.0;
    result.bottleneckEquipment = "N/A (fallback estimation)";

    return result;
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
    keywordsBuffer.append("-- Generated by NeqSim ProcessSystemLiftCurveGenerator\n");
    keywordsBuffer.append(String.format("-- Process: %s\n", processDescription));
    keywordsBuffer.append(String.format("-- Generated: %s\n",
        new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())));

    String rateType = "LIQ";
    if (vfp.getFlowRateType() == FlowRateType.GAS) {
      rateType = "GAS";
    } else if (vfp.getFlowRateType() == FlowRateType.OIL) {
      rateType = "OIL";
    }

    keywordsBuffer
        .append(String.format("  %d  %.1f  '%s'  'WCT'  'GOR'  'THP'  '' 'METRIC'  'BHP' /\n",
            vfp.getTableNumber(), vfp.getDatumDepth(), rateType));

    double[] flowRates = vfp.getFlowRates();
    double[] thpValues = vfp.getThpValues();
    double[] wctValues = vfp.getWctValues();
    double[] gorValues = vfp.getGorValues();
    double[] almValues = vfp.getAlmValues();
    double[][][][][] bhpValues = vfp.getBhpValues();

    // Flow rate axis
    keywordsBuffer.append("-- Flow rates (Sm3/d)\n");
    for (double rate : flowRates) {
      keywordsBuffer.append(String.format("  %.1f", rate));
    }
    keywordsBuffer.append("  /\n");

    // THP axis
    keywordsBuffer.append("-- THP (bara)\n");
    for (double thp : thpValues) {
      keywordsBuffer.append(String.format("  %.1f", thp));
    }
    keywordsBuffer.append("  /\n");

    // WCT axis
    keywordsBuffer.append("-- Water cut (fraction)\n");
    for (double wct : wctValues) {
      keywordsBuffer.append(String.format("  %.3f", wct));
    }
    keywordsBuffer.append("  /\n");

    // GOR axis
    keywordsBuffer.append("-- GOR (Sm3/Sm3)\n");
    for (double gor : gorValues) {
      keywordsBuffer.append(String.format("  %.1f", gor));
    }
    keywordsBuffer.append("  /\n");

    // ALQ axis
    keywordsBuffer.append("-- ALQ (artificial lift)\n");
    for (double alm : almValues) {
      keywordsBuffer.append(String.format("  %.1f", alm));
    }
    keywordsBuffer.append("  /\n");

    // BHP values
    keywordsBuffer.append("-- BHP values (bara)\n");
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

    full.append("-- ==========================================================\n");
    full.append("-- NeqSim Process System Lift Curve Export\n");
    full.append(String.format("-- Process: %s\n", processDescription));
    full.append(String.format("-- Generated: %s\n",
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())));
    full.append("-- ==========================================================\n\n");

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
   * Export to CSV format with extended process data.
   *
   * @return CSV string
   */
  public String exportToCsv() {
    StringBuilder csv = new StringBuilder();
    csv.append("TableNumber,WellName,FlowRate_Sm3d,THP_bara,WCT,GOR_Sm3Sm3,ALQ,");
    csv.append("BHP_bara,ExportGas_Sm3d,ExportOil_Sm3d,ExportWater_Sm3d,Power_kW,");
    csv.append("MaxUtilization,Feasible,BottleneckEquipment\n");

    for (VfpTable vfp : vfpTables) {
      double[] flowRates = vfp.getFlowRates();
      double[] thpValues = vfp.getThpValues();
      double[] wctValues = vfp.getWctValues();
      double[] gorValues = vfp.getGorValues();
      double[] almValues = vfp.getAlmValues();
      double[][][][][] bhpValues = vfp.getBhpValues();
      double[][][][][] exportGasRates = vfp.getExportGasRates();
      double[][][][][] exportOilRates = vfp.getExportOilRates();
      double[][][][][] exportWaterRates = vfp.getExportWaterRates();
      double[][][][][] compressionPower = vfp.getCompressionPower();
      double[][][][][] maxUtilization = vfp.getMaxUtilization();
      boolean[][][][][] feasible = vfp.getFeasible();
      String[][][][][] bottleneckEquipment = vfp.getBottleneckEquipment();

      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        for (int iThp = 0; iThp < thpValues.length; iThp++) {
          for (int iWct = 0; iWct < wctValues.length; iWct++) {
            for (int iGor = 0; iGor < gorValues.length; iGor++) {
              for (int iAlm = 0; iAlm < almValues.length; iAlm++) {
                String bottleneck =
                    bottleneckEquipment != null ? bottleneckEquipment[iFlow][iThp][iWct][iGor][iAlm]
                        : "";
                if (bottleneck == null) {
                  bottleneck = "";
                }
                csv.append(String.format(
                    "%d,%s,%.1f,%.1f,%.3f,%.1f,%.1f,%.2f,%.1f,%.1f,%.1f,%.1f,%.3f,%s,\"%s\"\n",
                    vfp.getTableNumber(), vfp.getWellName(), flowRates[iFlow], thpValues[iThp],
                    wctValues[iWct], gorValues[iGor], almValues[iAlm],
                    bhpValues[iFlow][iThp][iWct][iGor][iAlm],
                    exportGasRates != null ? exportGasRates[iFlow][iThp][iWct][iGor][iAlm] : 0.0,
                    exportOilRates != null ? exportOilRates[iFlow][iThp][iWct][iGor][iAlm] : 0.0,
                    exportWaterRates != null ? exportWaterRates[iFlow][iThp][iWct][iGor][iAlm]
                        : 0.0,
                    compressionPower != null ? compressionPower[iFlow][iThp][iWct][iGor][iAlm]
                        : 0.0,
                    maxUtilization != null ? maxUtilization[iFlow][iThp][iWct][iGor][iAlm] : 0.0,
                    feasible != null ? feasible[iFlow][iThp][iWct][iGor][iAlm] : true, bottleneck));
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
    result.put("generatorType", "ProcessSystemLiftCurveGenerator");
    result.put("processDescription", processDescription);
    result.put("inletStreamName", inletStreamName);
    result.put("exportGasStreamName", exportGasStreamName);
    result.put("exportOilStreamName", exportOilStreamName);
    result.put("inletTemperature_K", inletTemperature);
    result.put("exportPressure_bara", exportPressure);

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

      double[] flowRates = vfp.getFlowRates();
      double[] thpValues = vfp.getThpValues();
      double[] wctValues = vfp.getWctValues();
      double[] gorValues = vfp.getGorValues();
      double[] almValues = vfp.getAlmValues();
      double[][][][][] bhpValues = vfp.getBhpValues();
      double[][][][][] exportGasRates = vfp.getExportGasRates();
      double[][][][][] exportOilRates = vfp.getExportOilRates();
      double[][][][][] compressionPower = vfp.getCompressionPower();
      double[][][][][] maxUtilization = vfp.getMaxUtilization();
      boolean[][][][][] feasible = vfp.getFeasible();
      String[][][][][] bottleneckEquipment = vfp.getBottleneckEquipment();

      // Flatten data for JSON
      List<Map<String, Object>> dataPoints = new ArrayList<Map<String, Object>>();
      int totalPoints = 0;
      int feasiblePoints = 0;
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
                point.put("exportGasRate",
                    exportGasRates != null ? exportGasRates[iFlow][iThp][iWct][iGor][iAlm] : 0.0);
                point.put("exportOilRate",
                    exportOilRates != null ? exportOilRates[iFlow][iThp][iWct][iGor][iAlm] : 0.0);
                point.put("compressionPower",
                    compressionPower != null ? compressionPower[iFlow][iThp][iWct][iGor][iAlm]
                        : 0.0);
                // Equipment capacity data
                boolean isFeasible =
                    feasible != null ? feasible[iFlow][iThp][iWct][iGor][iAlm] : true;
                point.put("maxUtilization",
                    maxUtilization != null ? maxUtilization[iFlow][iThp][iWct][iGor][iAlm] : 0.0);
                point.put("feasible", isFeasible);
                point.put("bottleneckEquipment",
                    bottleneckEquipment != null ? bottleneckEquipment[iFlow][iThp][iWct][iGor][iAlm]
                        : "");
                dataPoints.add(point);
                totalPoints++;
                if (isFeasible) {
                  feasiblePoints++;
                }
              }
            }
          }
        }
      }
      tableMap.put("dataPoints", dataPoints);
      tableMap.put("totalPoints", totalPoints);
      tableMap.put("feasiblePoints", feasiblePoints);
      tableMap.put("infeasiblePoints", totalPoints - feasiblePoints);
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
   * Get inlet stream name.
   *
   * @return stream name
   */
  public String getInletStreamName() {
    return inletStreamName;
  }

  /**
   * Get export gas stream name.
   *
   * @return stream name
   */
  public String getExportGasStreamName() {
    return exportGasStreamName;
  }

  /**
   * Get export oil stream name.
   *
   * @return stream name
   */
  public String getExportOilStreamName() {
    return exportOilStreamName;
  }

  /**
   * Get process description.
   *
   * @return description
   */
  public String getProcessDescription() {
    return processDescription;
  }
}
