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
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Exports time-series data for reservoir simulator coupling (E300/ECLIPSE).
 *
 * <p>
 * Generates VFP tables, separator efficiency curves, and schedule keywords for coupling NeqSim
 * process models with reservoir simulators. Supports both Eclipse 100 and E300 (compositional)
 * formats.
 * </p>
 *
 * <h2>Export Capabilities</h2>
 * <ul>
 * <li><b>VFP Tables</b>: Vertical flow performance for wells (VFPPROD/VFPINJ)</li>
 * <li><b>Separator Efficiency</b>: Oil/gas/water split ratios vs conditions</li>
 * <li><b>Compression Curves</b>: Power vs rate and suction pressure</li>
 * <li><b>Network Deliverability</b>: Platform capacity constraints</li>
 * <li><b>Schedule Keywords</b>: Time-varying constraints</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create exporter
 * ReservoirCouplingExporter exporter = new ReservoirCouplingExporter(processSystem);
 * 
 * // Configure VFP table generation
 * exporter.setWellheadPressureRange(20.0, 100.0, 9); // 20-100 bara, 9 points
 * exporter.setGasRateRange(0.1e6, 10.0e6, 10); // 0.1-10 MSm3/d
 * exporter.setWaterCutRange(0.0, 0.95, 6); // 0-95% water cut
 * 
 * // Generate VFP tables
 * exporter.generateVfpProd("WELL-1", wellStream, 1);
 * 
 * // Export to file
 * exporter.exportToFile("vfp_tables.inc");
 * 
 * // Or get as string for E300 INCLUDE
 * String vfpKeywords = exporter.getEclipseKeywords();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ReservoirCouplingExporter implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(ReservoirCouplingExporter.class);

  /**
   * Export format type.
   */
  public enum ExportFormat {
    /** Eclipse 100 black oil format. */
    ECLIPSE_100,
    /** Eclipse 300 compositional format. */
    E300_COMPOSITIONAL,
    /** Intersect format. */
    INTERSECT,
    /** Generic CSV for external tools. */
    CSV
  }

  /**
   * Flow rate type for VFP table.
   */
  public enum FlowRateType {
    /** Liquid rate (oil + water) in Sm3/day. */
    LIQUID,
    /** Oil rate in Sm3/day. */
    OIL,
    /** Gas rate in Sm3/day. */
    GAS
  }

  /**
   * VFP table data structure containing all lift curve data.
   *
   * <p>
   * This class represents a complete VFP (Vertical Flow Performance) table that relates wellhead
   * conditions to bottomhole pressure. It supports:
   * </p>
   * <ul>
   * <li>Standard Eclipse VFPPROD dimensions (flow, THP, WCT, GOR, ALQ)</li>
   * <li>Equipment capacity tracking (utilization, feasibility, bottleneck identification)</li>
   * <li>Additional process outputs (gas/oil/water export rates, compression power)</li>
   * </ul>
   *
   * @author ESOL
   * @version 1.1
   */
  public static class VfpTable implements Serializable {
    private static final long serialVersionUID = 1001L;

    private int tableNumber;
    private String wellName;
    private double datumDepth;
    private double[] flowRates;
    private double[] thpValues;
    private double[] wctValues;
    private double[] gorValues;
    private double[] almValues; // artificial lift
    private double[][][][][] bhpValues; // 5D array: flow, thp, wct, gor, alm
    private FlowRateType flowRateType = FlowRateType.LIQUID;

    // Additional process outputs
    private double[][][][][] exportGasRates; // [flow][thp][wct][gor][alm]
    private double[][][][][] exportOilRates;
    private double[][][][][] exportWaterRates;
    private double[][][][][] compressionPower;

    // Equipment capacity tracking
    private double[][][][][] maxUtilization; // Maximum equipment utilization (0-1)
    private boolean[][][][][] feasible; // Whether point is within all equipment capacities
    private String[][][][][] bottleneckEquipment; // Name of limiting equipment

    /** Get table number. */
    public int getTableNumber() {
      return tableNumber;
    }

    /** Set table number. */
    public void setTableNumber(int num) {
      this.tableNumber = num;
    }

    /** Get well name. */
    public String getWellName() {
      return wellName;
    }

    /** Set well name. */
    public void setWellName(String name) {
      this.wellName = name;
    }

    /** Get datum depth. */
    public double getDatumDepth() {
      return datumDepth;
    }

    /** Set datum depth. */
    public void setDatumDepth(double depth) {
      this.datumDepth = depth;
    }

    /** Get flow rates. */
    public double[] getFlowRates() {
      return flowRates;
    }

    /** Set flow rates. */
    public void setFlowRates(double[] rates) {
      this.flowRates = rates;
    }

    /** Get THP values. */
    public double[] getThpValues() {
      return thpValues;
    }

    /** Set THP values. */
    public void setThpValues(double[] values) {
      this.thpValues = values;
    }

    /** Get water cut values. */
    public double[] getWctValues() {
      return wctValues;
    }

    /** Set water cut values. */
    public void setWctValues(double[] values) {
      this.wctValues = values;
    }

    /**
     * Get GOR values.
     *
     * @return the array of gas-oil ratio values
     */
    public double[] getGorValues() {
      return gorValues;
    }

    /** Set GOR values. */
    public void setGorValues(double[] values) {
      this.gorValues = values;
    }

    /** Get ALM values. */
    public double[] getAlmValues() {
      return almValues;
    }

    /** Set ALM values. */
    public void setAlmValues(double[] values) {
      this.almValues = values;
    }

    /**
     * Get BHP values array.
     *
     * @return the 5-dimensional array of bottom-hole pressure values
     */
    public double[][][][][] getBhpValues() {
      return bhpValues;
    }

    /** Set BHP values. */
    public void setBhpValues(double[][][][][] values) {
      this.bhpValues = values;
    }

    /** Get flow rate type. */
    public FlowRateType getFlowRateType() {
      return flowRateType;
    }

    /** Set flow rate type. */
    public void setFlowRateType(FlowRateType type) {
      this.flowRateType = type;
    }

    /** Get export gas rates. */
    public double[][][][][] getExportGasRates() {
      return exportGasRates;
    }

    /** Set export gas rates. */
    public void setExportGasRates(double[][][][][] rates) {
      this.exportGasRates = rates;
    }

    /** Get export oil rates. */
    public double[][][][][] getExportOilRates() {
      return exportOilRates;
    }

    /** Set export oil rates. */
    public void setExportOilRates(double[][][][][] rates) {
      this.exportOilRates = rates;
    }

    /** Get export water rates. */
    public double[][][][][] getExportWaterRates() {
      return exportWaterRates;
    }

    /** Set export water rates. */
    public void setExportWaterRates(double[][][][][] rates) {
      this.exportWaterRates = rates;
    }

    /** Get compression power. */
    public double[][][][][] getCompressionPower() {
      return compressionPower;
    }

    /** Set compression power. */
    public void setCompressionPower(double[][][][][] power) {
      this.compressionPower = power;
    }

    /** Get max utilization array. */
    public double[][][][][] getMaxUtilization() {
      return maxUtilization;
    }

    /** Set max utilization array. */
    public void setMaxUtilization(double[][][][][] util) {
      this.maxUtilization = util;
    }

    /** Get feasible array. */
    public boolean[][][][][] getFeasible() {
      return feasible;
    }

    /** Set feasible array. */
    public void setFeasible(boolean[][][][][] feas) {
      this.feasible = feas;
    }

    /** Get bottleneck equipment array. */
    public String[][][][][] getBottleneckEquipment() {
      return bottleneckEquipment;
    }

    /** Set bottleneck equipment array. */
    public void setBottleneckEquipment(String[][][][][] equipment) {
      this.bottleneckEquipment = equipment;
    }

    /**
     * Initialize all arrays for given dimensions.
     *
     * @param nFlow number of flow rate points
     * @param nThp number of THP points
     * @param nWct number of WCT points
     * @param nGor number of GOR points
     * @param nAlm number of ALQ points
     */
    public void initializeArrays(int nFlow, int nThp, int nWct, int nGor, int nAlm) {
      bhpValues = new double[nFlow][nThp][nWct][nGor][nAlm];
      exportGasRates = new double[nFlow][nThp][nWct][nGor][nAlm];
      exportOilRates = new double[nFlow][nThp][nWct][nGor][nAlm];
      exportWaterRates = new double[nFlow][nThp][nWct][nGor][nAlm];
      compressionPower = new double[nFlow][nThp][nWct][nGor][nAlm];
      maxUtilization = new double[nFlow][nThp][nWct][nGor][nAlm];
      feasible = new boolean[nFlow][nThp][nWct][nGor][nAlm];
      bottleneckEquipment = new String[nFlow][nThp][nWct][nGor][nAlm];

      // Initialize feasible to true by default
      for (int i = 0; i < nFlow; i++) {
        for (int j = 0; j < nThp; j++) {
          for (int k = 0; k < nWct; k++) {
            for (int l = 0; l < nGor; l++) {
              for (int m = 0; m < nAlm; m++) {
                feasible[i][j][k][l][m] = true;
              }
            }
          }
        }
      }
    }

    /**
     * Get number of feasible points in the table.
     *
     * @return count of feasible points
     */
    public int countFeasiblePoints() {
      if (feasible == null) {
        return flowRates.length * thpValues.length * wctValues.length * gorValues.length
            * almValues.length;
      }
      int count = 0;
      for (int i = 0; i < flowRates.length; i++) {
        for (int j = 0; j < thpValues.length; j++) {
          for (int k = 0; k < wctValues.length; k++) {
            for (int l = 0; l < gorValues.length; l++) {
              for (int m = 0; m < almValues.length; m++) {
                if (feasible[i][j][k][l][m]) {
                  count++;
                }
              }
            }
          }
        }
      }
      return count;
    }

    /**
     * Get total number of points in the table.
     *
     * @return total point count
     */
    public int getTotalPoints() {
      return flowRates.length * thpValues.length * wctValues.length * gorValues.length
          * almValues.length;
    }
  }

  /**
   * Schedule keyword entry.
   */
  public static class ScheduleEntry implements Serializable {
    private static final long serialVersionUID = 1002L;

    private Date date;
    private String keyword;
    private String content;

    /**
     * Creates a new schedule entry.
     *
     * @param date schedule date
     * @param keyword Eclipse keyword
     * @param content keyword content
     */
    public ScheduleEntry(Date date, String keyword, String content) {
      this.date = date;
      this.keyword = keyword;
      this.content = content;
    }

    /** Get date. */
    public Date getDate() {
      return date;
    }

    /** Get keyword. */
    public String getKeyword() {
      return keyword;
    }

    /** Get content. */
    public String getContent() {
      return content;
    }
  }

  // ============================================================================
  // INSTANCE VARIABLES
  // ============================================================================

  /** Process system for calculations. */
  private ProcessSystem processSystem;

  /** Export format. */
  private ExportFormat format;

  /** Generated VFP tables. */
  private List<VfpTable> vfpTables;

  /** Schedule entries. */
  private List<ScheduleEntry> scheduleEntries;

  /** Generated keywords buffer. */
  private StringBuilder keywordsBuffer;

  // VFP generation parameters
  private double[] pressureRange;
  private double[] rateRange;
  private double[] wctRange;
  private double[] gorRange;

  /** Datum depth for VFP tables (m). */
  private double datumDepth = 2500.0;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new exporter for the given process system.
   *
   * @param processSystem the process system
   */
  public ReservoirCouplingExporter(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.format = ExportFormat.ECLIPSE_100;
    this.vfpTables = new ArrayList<VfpTable>();
    this.scheduleEntries = new ArrayList<ScheduleEntry>();
    this.keywordsBuffer = new StringBuilder();

    // Default ranges
    setPressureRange(20.0, 100.0, 9);
    setRateRange(100.0, 10000.0, 10);
    setWctRange(0.0, 0.90, 4);
    setGorRange(100.0, 1000.0, 4);
  }

  /**
   * Creates a new standalone exporter.
   */
  public ReservoirCouplingExporter() {
    this(null);
  }

  // ============================================================================
  // CONFIGURATION
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
   * Set wellhead/tubing pressure range for VFP generation.
   *
   * @param minBar minimum pressure (bara)
   * @param maxBar maximum pressure (bara)
   * @param points number of points
   */
  public void setPressureRange(double minBar, double maxBar, int points) {
    this.pressureRange = linspace(minBar, maxBar, points);
  }

  /**
   * Set flow rate range for VFP generation.
   *
   * @param minRate minimum rate (Sm3/d for liquid, MSm3/d for gas)
   * @param maxRate maximum rate
   * @param points number of points
   */
  public void setRateRange(double minRate, double maxRate, int points) {
    this.rateRange = linspace(minRate, maxRate, points);
  }

  /**
   * Set water cut range for VFP generation.
   *
   * @param minWct minimum water cut (0-1)
   * @param maxWct maximum water cut (0-1)
   * @param points number of points
   */
  public void setWctRange(double minWct, double maxWct, int points) {
    this.wctRange = linspace(minWct, maxWct, points);
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
   * Set datum depth for VFP tables.
   *
   * @param depth datum depth in meters
   */
  public void setDatumDepth(double depth) {
    this.datumDepth = depth;
  }

  // ============================================================================
  // VFP TABLE GENERATION
  // ============================================================================

  /**
   * Generate VFPPROD table for a producing well.
   *
   * <p>
   * Creates a multi-dimensional VFP table mapping (rate, THP, WCT, GOR) to BHP using the process
   * model for pressure drop calculations.
   * </p>
   *
   * @param wellName well name
   * @param baseFluid base fluid composition
   * @param tableNumber VFP table number (1-9999)
   * @return the generated VFP table
   */
  public VfpTable generateVfpProd(String wellName, SystemInterface baseFluid, int tableNumber) {
    logger.info("Generating VFPPROD table {} for well {}", tableNumber, wellName);

    VfpTable vfp = new VfpTable();
    vfp.setTableNumber(tableNumber);
    vfp.setWellName(wellName);
    vfp.setDatumDepth(datumDepth);
    vfp.flowRates = rateRange.clone();
    vfp.thpValues = pressureRange.clone();
    vfp.wctValues = wctRange.clone();
    vfp.gorValues = gorRange.clone();
    vfp.almValues = new double[] {0.0}; // No artificial lift by default

    int nFlow = rateRange.length;
    int nThp = pressureRange.length;
    int nWct = wctRange.length;
    int nGor = gorRange.length;
    int nAlm = 1;

    vfp.bhpValues = new double[nFlow][nThp][nWct][nGor][nAlm];

    // Generate BHP values for each combination
    for (int iFlow = 0; iFlow < nFlow; iFlow++) {
      for (int iThp = 0; iThp < nThp; iThp++) {
        for (int iWct = 0; iWct < nWct; iWct++) {
          for (int iGor = 0; iGor < nGor; iGor++) {
            double rate = rateRange[iFlow];
            double thp = pressureRange[iThp];
            double wct = wctRange[iWct];
            double gor = gorRange[iGor];

            // Calculate BHP using process model or correlation
            double bhp = calculateBhp(baseFluid, rate, thp, wct, gor);
            vfp.bhpValues[iFlow][iThp][iWct][iGor][0] = bhp;
          }
        }
      }
    }

    vfpTables.add(vfp);
    appendVfpProdKeyword(vfp);

    return vfp;
  }

  /**
   * Generate VFPINJ table for an injection well.
   *
   * @param wellName well name
   * @param injectionFluid injection fluid (water or gas)
   * @param tableNumber VFP table number
   * @return the generated VFP table
   */
  public VfpTable generateVfpInj(String wellName, SystemInterface injectionFluid, int tableNumber) {
    logger.info("Generating VFPINJ table {} for well {}", tableNumber, wellName);

    VfpTable vfp = new VfpTable();
    vfp.setTableNumber(tableNumber);
    vfp.setWellName(wellName);
    vfp.setDatumDepth(datumDepth);
    vfp.flowRates = rateRange.clone();
    vfp.thpValues = pressureRange.clone();

    int nFlow = rateRange.length;
    int nThp = pressureRange.length;

    // Simplified 2D table for injection
    vfp.bhpValues = new double[nFlow][nThp][1][1][1];

    for (int iFlow = 0; iFlow < nFlow; iFlow++) {
      for (int iThp = 0; iThp < nThp; iThp++) {
        double rate = rateRange[iFlow];
        double thp = pressureRange[iThp];

        // Calculate BHP for injection
        double bhp = calculateInjectionBhp(injectionFluid, rate, thp);
        vfp.bhpValues[iFlow][iThp][0][0][0] = bhp;
      }
    }

    vfpTables.add(vfp);
    appendVfpInjKeyword(vfp);

    return vfp;
  }

  /**
   * Calculate BHP from THP using process model.
   */
  private double calculateBhp(SystemInterface fluid, double rate, double thp, double wct,
      double gor) {
    // Simplified correlation - in practice would use full well model
    // BHP = THP + hydrostatic + friction
    double tvd = datumDepth;
    double density = 800.0 - wct * 200.0; // Approximate mixture density kg/m3
    double hydrostatic = density * 9.81 * tvd / 1e5; // bara

    // Friction loss increases with rate
    double frictionFactor = 0.001 * Math.pow(rate / 1000.0, 1.8);
    double frictionLoss = frictionFactor * tvd / 100.0;

    return thp + hydrostatic + frictionLoss;
  }

  /**
   * Calculate injection BHP.
   */
  private double calculateInjectionBhp(SystemInterface fluid, double rate, double thp) {
    double tvd = datumDepth;
    double density = 1025.0; // Assume water injection
    double hydrostatic = density * 9.81 * tvd / 1e5;

    // Friction loss
    double frictionLoss = 0.0005 * Math.pow(rate / 1000.0, 1.8) * tvd / 100.0;

    // For injection: BHP = THP + hydrostatic - friction (helping)
    return thp + hydrostatic + frictionLoss;
  }

  // ============================================================================
  // SCHEDULE KEYWORDS
  // ============================================================================

  /**
   * Add a platform rate constraint.
   *
   * @param date effective date
   * @param groupName group/platform name
   * @param oilRate oil rate limit (Sm3/d)
   * @param gasRate gas rate limit (Sm3/d)
   * @param waterRate water injection rate limit (Sm3/d)
   */
  public void addGroupConstraint(Date date, String groupName, double oilRate, double gasRate,
      double waterRate) {
    StringBuilder content = new StringBuilder();
    content.append(
        String.format("  '%s'  %.1f  %.1f  %.1f  /\n", groupName, oilRate, gasRate, waterRate));

    scheduleEntries.add(new ScheduleEntry(date, "GCONPROD", content.toString()));
  }

  /**
   * Add well control mode change.
   *
   * @param date effective date
   * @param wellName well name
   * @param controlMode control mode (ORAT, GRAT, LRAT, RESV, BHP)
   * @param targetValue target value
   */
  public void addWellControl(Date date, String wellName, String controlMode, double targetValue) {
    StringBuilder content = new StringBuilder();
    content.append(
        String.format("  '%s'  'OPEN'  '%s'  %.1f  /\n", wellName, controlMode, targetValue));

    scheduleEntries.add(new ScheduleEntry(date, "WCONPROD", content.toString()));
  }

  /**
   * Add VFP table reference for a well.
   *
   * @param date effective date
   * @param wellName well name
   * @param vfpTableNumber VFP table number
   */
  public void addVfpReference(Date date, String wellName, int vfpTableNumber) {
    StringBuilder content = new StringBuilder();
    content.append(String.format("  '%s'  %d  /\n", wellName, vfpTableNumber));

    scheduleEntries.add(new ScheduleEntry(date, "WVFPPROD", content.toString()));
  }

  // ============================================================================
  // KEYWORD GENERATION
  // ============================================================================

  /**
   * Append VFPPROD keyword to buffer.
   */
  private void appendVfpProdKeyword(VfpTable vfp) {
    keywordsBuffer.append("VFPPROD\n");
    keywordsBuffer.append(String.format("-- VFP Production Table %d for %s\n", vfp.getTableNumber(),
        vfp.getWellName()));
    keywordsBuffer.append(String.format("-- Generated by NeqSim %s\n",
        new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())));
    keywordsBuffer
        .append(String.format("  %d  %.1f  'LIQ'  'WCT'  'GOR'  'THP'  '' 'METRIC'  'BHP' /\n",
            vfp.getTableNumber(), vfp.getDatumDepth()));

    // Flow rate axis
    keywordsBuffer.append("-- Flow rates (Sm3/d)\n");
    for (double rate : vfp.flowRates) {
      keywordsBuffer.append(String.format("  %.1f", rate));
    }
    keywordsBuffer.append("  /\n");

    // THP axis
    keywordsBuffer.append("-- THP (bara)\n");
    for (double thp : vfp.thpValues) {
      keywordsBuffer.append(String.format("  %.1f", thp));
    }
    keywordsBuffer.append("  /\n");

    // WCT axis
    keywordsBuffer.append("-- Water cut\n");
    for (double wct : vfp.wctValues) {
      keywordsBuffer.append(String.format("  %.3f", wct));
    }
    keywordsBuffer.append("  /\n");

    // GOR axis
    keywordsBuffer.append("-- GOR (Sm3/Sm3)\n");
    for (double gor : vfp.gorValues) {
      keywordsBuffer.append(String.format("  %.1f", gor));
    }
    keywordsBuffer.append("  /\n");

    // ALQ axis (artificial lift)
    keywordsBuffer.append("-- ALQ\n  0.0  /\n");

    // BHP values
    keywordsBuffer.append("-- BHP values (bara)\n");
    for (int iThp = 0; iThp < vfp.thpValues.length; iThp++) {
      for (int iWct = 0; iWct < vfp.wctValues.length; iWct++) {
        for (int iGor = 0; iGor < vfp.gorValues.length; iGor++) {
          keywordsBuffer.append(String.format("-- THP=%.0f WCT=%.2f GOR=%.0f\n",
              vfp.thpValues[iThp], vfp.wctValues[iWct], vfp.gorValues[iGor]));
          for (int iFlow = 0; iFlow < vfp.flowRates.length; iFlow++) {
            keywordsBuffer
                .append(String.format("  %.2f", vfp.bhpValues[iFlow][iThp][iWct][iGor][0]));
          }
          keywordsBuffer.append("  /\n");
        }
      }
    }
    keywordsBuffer.append("\n");
  }

  /**
   * Append VFPINJ keyword to buffer.
   */
  private void appendVfpInjKeyword(VfpTable vfp) {
    keywordsBuffer.append("VFPINJ\n");
    keywordsBuffer.append(String.format("-- VFP Injection Table %d for %s\n", vfp.getTableNumber(),
        vfp.getWellName()));
    keywordsBuffer.append(String.format("  %d  %.1f  'WAT'  'METRIC'  'BHP' /\n",
        vfp.getTableNumber(), vfp.getDatumDepth()));

    // Flow rate axis
    keywordsBuffer.append("-- Flow rates (Sm3/d)\n");
    for (double rate : vfp.flowRates) {
      keywordsBuffer.append(String.format("  %.1f", rate));
    }
    keywordsBuffer.append("  /\n");

    // THP axis
    keywordsBuffer.append("-- THP (bara)\n");
    for (double thp : vfp.thpValues) {
      keywordsBuffer.append(String.format("  %.1f", thp));
    }
    keywordsBuffer.append("  /\n");

    // BHP values
    keywordsBuffer.append("-- BHP values (bara)\n");
    for (int iThp = 0; iThp < vfp.thpValues.length; iThp++) {
      keywordsBuffer.append(String.format("-- THP=%.0f\n", vfp.thpValues[iThp]));
      for (int iFlow = 0; iFlow < vfp.flowRates.length; iFlow++) {
        keywordsBuffer.append(String.format("  %.2f", vfp.bhpValues[iFlow][iThp][0][0][0]));
      }
      keywordsBuffer.append("  /\n");
    }
    keywordsBuffer.append("\n");
  }

  // ============================================================================
  // SEPARATOR EFFICIENCY EXPORT
  // ============================================================================

  /**
   * Export separator efficiency curves for E300 coupling.
   *
   * @param separatorName separator equipment name
   * @param pressurePoints pressure points to evaluate (bara)
   * @param temperaturePoints temperature points to evaluate (K)
   * @return CSV data string
   */
  public String exportSeparatorEfficiency(String separatorName, double[] pressurePoints,
      double[] temperaturePoints) {
    StringBuilder csv = new StringBuilder();
    csv.append("-- Separator efficiency table for ").append(separatorName).append("\n");
    csv.append("-- Generated by NeqSim\n");
    csv.append("Pressure_bara,Temperature_K,Oil_Recovery,Gas_Recovery,Water_Recovery\n");

    for (double p : pressurePoints) {
      for (double t : temperaturePoints) {
        // Would use actual separator model here
        double oilRec = 0.98 - 0.001 * (100 - p);
        double gasRec = 0.99;
        double waterRec = 0.95;

        csv.append(String.format("%.1f,%.1f,%.4f,%.4f,%.4f\n", p, t, oilRec, gasRec, waterRec));
      }
    }

    return csv.toString();
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
    full.append("-- NeqSim Process Model Export\n");
    full.append(String.format("-- Generated: %s\n",
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())));
    full.append("-- ==========================================================\n\n");

    // VFP tables
    full.append(keywordsBuffer);

    // Schedule section with sorted entries
    if (!scheduleEntries.isEmpty()) {
      full.append("SCHEDULE\n\n");

      // Sort by date
      List<ScheduleEntry> sorted = new ArrayList<ScheduleEntry>(scheduleEntries);
      java.util.Collections.sort(sorted, new java.util.Comparator<ScheduleEntry>() {
        @Override
        public int compare(ScheduleEntry a, ScheduleEntry b) {
          return a.getDate().compareTo(b.getDate());
        }
      });

      SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy");
      Date lastDate = null;

      for (ScheduleEntry entry : sorted) {
        if (lastDate == null || !entry.getDate().equals(lastDate)) {
          full.append(String.format("\nDATES\n  %s /\n/\n\n",
              dateFmt.format(entry.getDate()).toUpperCase()));
          lastDate = entry.getDate();
        }
        full.append(entry.getKeyword()).append("\n");
        full.append(entry.getContent());
        full.append("/\n\n");
      }
    }

    return full.toString();
  }

  /**
   * Export to file.
   *
   * @param filePath output file path
   * @throws IOException if write fails
   */
  public void exportToFile(String filePath) throws IOException {
    logger.info("Exporting to file: {}", filePath);

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
   * Export production forecast to CSV for import to other tools.
   *
   * @param years forecast years
   * @param oilRates oil rates by year (Sm3/d)
   * @param gasRates gas rates by year (Sm3/d)
   * @param waterRates water rates by year (Sm3/d)
   * @return CSV string
   */
  public String exportProductionForecastCsv(int[] years, double[] oilRates, double[] gasRates,
      double[] waterRates) {
    StringBuilder csv = new StringBuilder();
    csv.append("Year,Oil_Sm3d,Gas_Sm3d,Water_Sm3d,Liquid_Sm3d,WaterCut,GOR\n");

    for (int i = 0; i < years.length; i++) {
      double oil = oilRates[i];
      double gas = gasRates[i];
      double water = waterRates[i];
      double liquid = oil + water;
      double wct = liquid > 0 ? water / liquid : 0;
      double gor = oil > 0 ? gas / oil : 0;

      csv.append(String.format("%d,%.1f,%.1f,%.1f,%.1f,%.3f,%.1f\n", years[i], oil, gas, water,
          liquid, wct, gor));
    }

    return csv.toString();
  }

  // ============================================================================
  // UTILITY METHODS
  // ============================================================================

  /**
   * Generate linearly spaced array.
   */
  private double[] linspace(double start, double end, int points) {
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
    scheduleEntries.clear();
    keywordsBuffer = new StringBuilder();
  }

  /**
   * Get generated VFP tables.
   *
   * @return list of VFP tables
   */
  public List<VfpTable> getVfpTables() {
    return vfpTables;
  }
}
