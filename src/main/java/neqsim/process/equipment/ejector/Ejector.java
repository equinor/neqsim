package neqsim.process.equipment.ejector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.ejector.EjectorMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Ejector class represents an ejector in a process simulation. It mixes a motive stream with a suction stream and
 * calculates the resulting mixed stream using a quasi one-dimensional formulation. The implementation combines energy
 * and momentum balances commonly used in steam-jet ejector design as summarised by Keenan et al. (1950) and ESDU 86030.
 *
 * <p>
 * Supports both constant-pressure mixing (CPM) and constant-area mixing (CAM) models, and calculates critical back
 * pressure, Mach numbers, and area ratios needed for vendor-style ejector sizing (e.g. Transvac, Croll Reynolds,
 * Schutte &amp; Koerting). The CPM model is the standard method used by most ejector vendors including Transvac for
 * gas-gas and steam ejector design.
 * </p>
 *
 * <p>
 * The ejector implements {@link neqsim.process.design.AutoSizeable} for automatic sizing based on flow conditions and
 * {@link neqsim.process.equipment.capacity.CapacityConstrainedEquipment} for capacity analysis with constraints for
 * entrainment ratio, compression ratio, and critical back pressure.
 * </p>
 *
 * <p>
 * Key Transvac-style parameters accessible after run():
 * </p>
 * <ul>
 * <li>Entrainment ratio (ER) = suction mass flow / motive mass flow</li>
 * <li>Compression ratio (CR) = discharge pressure / suction pressure</li>
 * <li>Expansion ratio = motive pressure / discharge pressure</li>
 * <li>Critical back pressure = max discharge pressure before ejector breaks down</li>
 * <li>Area ratio = mixing section area / motive nozzle throat area</li>
 * <li>Mach numbers at nozzle exit, suction, and mixing section</li>
 * </ul>
 *
 * @author esol
 * @version 2.0
 */
public class Ejector extends ProcessEquipmentBaseClass
    implements neqsim.process.design.AutoSizeable, neqsim.process.equipment.capacity.CapacityConstrainedEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private static final Logger logger = LogManager.getLogger(Ejector.class);

  private StreamInterface motiveStream;
  private StreamInterface suctionStream;
  private StreamInterface mixedStream;

  private double dischargePressure;
  private double mixingPressure = Double.NaN;
  private double efficiencyIsentropic = 0.75; // default nozzle efficiency
  private double suctionNozzleEfficiency = 0.90; // suction nozzle efficiency
  private double mixingEfficiency = 0.85; // mixing section momentum transfer efficiency
  private double diffuserEfficiency = 0.8; // diffuser pressure recovery efficiency

  private double designSuctionVelocity = 0.0; // estimated from operating conditions unless set
  private double designDiffuserOutletVelocity = 0.0; // estimated from operating conditions
  private boolean designSuctionVelocityOverride = false;
  private boolean designDiffuserVelocityOverride = false;

  private double suctionConnectionLength = 0.0; // m, optional straight pipe upstream
  private double dischargeConnectionLength = 0.0; // m, optional straight pipe downstream
  private boolean suctionConnectionLengthOverride = false;
  private boolean dischargeConnectionLengthOverride = false;

  private transient EjectorMechanicalDesign mechanicalDesign;

  // ============ Fixed-geometry (off-design) mode ============
  /** True when the ejector is solved for a fixed geometry (flows are results, not inputs). */
  private boolean fixedGeometryMode = false;
  /** Nozzle throat area in m2 for the fixed-geometry mode. */
  private double fixedThroatArea = 0.0;
  /** Mixing section area in m2 for the fixed-geometry mode. */
  private double fixedMixingArea = 0.0;
  /** Limit the entrained flow to the vapour flow supplied by the suction stream. */
  private boolean limitEntrainmentToSupply = false;
  /** Last fixed-geometry operating point. */
  private EjectorOperatingPoint operatingPoint = null;
  /** Vapour flow supplied by the suction stream at the last run in kg/s. */
  private double suctionVaporSupply = Double.NaN;
  /** Maximum allowed discharge pressure for the operating window in bara (NaN if not set). */
  private double maxDischargePressureLimit = Double.NaN;

  // ============ Calculated performance results ============
  /** Compression ratio = discharge pressure / suction pressure. */
  private double compressionRatio = 0.0;
  /** Expansion ratio = motive pressure / discharge pressure. */
  private double expansionRatio = 0.0;
  /** Critical back pressure in bara (max discharge before breakdown). */
  private double criticalBackPressure = 0.0;
  /** Area ratio = mixing area / motive nozzle throat area. */
  private double areaRatio = 0.0;
  /** Mach number at motive nozzle exit. */
  private double motiveNozzleMach = 0.0;
  /** Mach number of suction flow at mixing section entrance. */
  private double suctionMach = 0.0;
  /** Mach number of mixed flow at mixing section. */
  private double mixingMach = 0.0;
  /** Whether suction flow is choked (Mach &gt;= 1). */
  private boolean suctionChoked = false;
  /** Whether motive flow is choked at nozzle throat. */
  private boolean motiveChoked = false;

  // ============ AutoSizeable and CapacityConstrainedEquipment Fields ============
  /** Flag indicating if ejector has been auto-sized. */
  private boolean autoSized = false;
  /** Design entrainment ratio (suction/motive mass ratio). */
  private double designEntrainmentRatio = 0.0;
  /** Design compression ratio (discharge/suction pressure). */
  private double designCompressionRatio = 0.0;
  /** Maximum critical back pressure in bara. */
  private double maxCriticalBackPressure = 0.0;
  /** Design motive flow rate in kg/s. */
  private double designMotiveFlowRate = 0.0;
  /** Capacity constraints map. */
  private java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> ejectorCapacityConstraints = new java.util.LinkedHashMap<String, neqsim.process.equipment.capacity.CapacityConstraint>();
  /** Flag for capacity analysis. */
  private boolean ejectorCapacityAnalysisEnabled = true;

  private static final double BAR_TO_PA = 1.0e5;
  private static final double DEFAULT_NOZZLE_LENGTH_FACTOR = 3.0; // ~3x throat diameter
  private static final double DEFAULT_SUCTION_LENGTH_FACTOR = 2.5; // bellmouth and settling
  private static final double DEFAULT_MIXING_LENGTH_FACTOR = 4.0; // per Keenan et al.
  private static final double DEFAULT_DIFFUSER_LENGTH_FACTOR = 6.0; // gradual pressure recovery

  /**
   * Constructs an Ejector with the specified name, motive stream, and suction stream.
   *
   * @param name the name of the ejector
   * @param motiveStream the motive stream
   * @param suctionStream the suction stream
   */
  public Ejector(String name, StreamInterface motiveStream, StreamInterface suctionStream) {
    super(name);
    this.motiveStream = motiveStream;
    this.suctionStream = suctionStream;
    this.mixedStream = motiveStream.clone();
  }

  /**
   * Setter for the field <code>dischargePressure</code>.
   *
   * @param dischargePressure a double
   */
  public void setDischargePressure(double dischargePressure) {
    this.dischargePressure = dischargePressure;
  }

  /**
   * Setter for the field <code>efficiencyIsentropic</code>.
   *
   * @param efficiencyIsentropic a double
   */
  public void setEfficiencyIsentropic(double efficiencyIsentropic) {
    this.efficiencyIsentropic = efficiencyIsentropic;
  }

  /**
   * Setter for the field <code>diffuserEfficiency</code>.
   *
   * @param diffuserEfficiency a double
   */
  public void setDiffuserEfficiency(double diffuserEfficiency) {
    this.diffuserEfficiency = diffuserEfficiency;
  }

  /**
   * Sets the suction nozzle isentropic efficiency. This accounts for losses in the suction flow path as the suction gas
   * accelerates into the mixing section. Typical values: 0.85-0.95.
   *
   * @param efficiency suction nozzle efficiency (0-1)
   */
  public void setSuctionNozzleEfficiency(double efficiency) {
    this.suctionNozzleEfficiency = efficiency;
  }

  /**
   * Gets the suction nozzle efficiency.
   *
   * @return suction nozzle efficiency
   */
  public double getSuctionNozzleEfficiency() {
    return suctionNozzleEfficiency;
  }

  /**
   * Sets the mixing section efficiency. This accounts for momentum losses during the mixing of motive and suction
   * streams due to turbulence and friction. Typical values: 0.80-0.95. Transvac-type ejectors with optimised mixing
   * sections typically achieve 0.85-0.92.
   *
   * @param efficiency mixing efficiency (0-1)
   */
  public void setMixingEfficiency(double efficiency) {
    this.mixingEfficiency = efficiency;
  }

  /**
   * Gets the mixing section efficiency.
   *
   * @return mixing efficiency
   */
  public double getMixingEfficiency() {
    return mixingEfficiency;
  }

  /**
   * Setter for the field <code>throatArea</code>.
   *
   * <p>
   * Retained for backwards compatibility and still a no-op in the default (flow-input) mode, where the areas are
   * calculated from the hydraulic design. To run a fixed geometry use {@link #setFixedGeometry(double, double)} or
   * {@link #setFixedGeometryAreaRatio(double, double)}.
   * </p>
   *
   * @param throatArea a double
   */
  public void setThroatArea(double throatArea) {
    // Retained for backwards compatibility. The area is now calculated from the
    // hydraulic design and therefore this setter is deprecated.
  }

  /**
   * Switches the ejector to the fixed-geometry (off-design) mode. The nozzle throat and the constant-area mixing
   * section are given; the motive flow is the choked flow of the nozzle at the motive stream state, the entrained flow
   * is calculated from the 1-D constant-pressure mixing model with the nozzle, suction, mixing and diffuser
   * efficiencies of this ejector, and the flow rates of the motive and suction streams are not used (only their
   * composition, pressure and temperature). The discharge pressure set with {@link #setDischargePressure(double)} is
   * the back pressure. The default flow-input mode is unchanged unless this method is called.
   *
   * @param nozzleThroatArea motive nozzle throat area in m2
   * @param mixingSectionArea constant-area mixing section area in m2
   * @throws IllegalArgumentException if an area is not positive or the mixing area is not larger than the throat area
   */
  public void setFixedGeometry(double nozzleThroatArea, double mixingSectionArea) {
    if (nozzleThroatArea <= 0.0 || mixingSectionArea <= nozzleThroatArea) {
      throw new IllegalArgumentException("fixed geometry needs 0 < throat area < mixing section area, got "
          + nozzleThroatArea + " and " + mixingSectionArea);
    }
    this.fixedThroatArea = nozzleThroatArea;
    this.fixedMixingArea = mixingSectionArea;
    this.fixedGeometryMode = true;
  }

  /**
   * Switches to the fixed-geometry mode with the mixing section area given as an area ratio.
   *
   * @param nozzleThroatArea motive nozzle throat area in m2
   * @param areaRatio mixing section area divided by throat area (larger than one)
   * @throws IllegalArgumentException if the throat area is not positive or the area ratio is not larger than one
   */
  public void setFixedGeometryAreaRatio(double nozzleThroatArea, double areaRatio) {
    setFixedGeometry(nozzleThroatArea, nozzleThroatArea * areaRatio);
  }

  /**
   * Returns to the default flow-input mode (both flows are inputs, areas are calculated).
   */
  public void clearFixedGeometry() {
    this.fixedGeometryMode = false;
    this.operatingPoint = null;
  }

  /**
   * Returns whether the fixed-geometry mode is active.
   *
   * @return true if the ejector is solved for a fixed geometry
   */
  public boolean isFixedGeometryMode() {
    return fixedGeometryMode;
  }

  /**
   * Gets the nozzle throat area of the fixed geometry.
   *
   * @return area in m2 (zero if no fixed geometry is set)
   */
  public double getFixedThroatArea() {
    return fixedThroatArea;
  }

  /**
   * Gets the mixing section area of the fixed geometry.
   *
   * @return area in m2 (zero if no fixed geometry is set)
   */
  public double getFixedMixingArea() {
    return fixedMixingArea;
  }

  /**
   * When enabled, the entrained flow in the fixed-geometry mode is limited to the vapour flow the suction stream
   * supplies; otherwise the suction stream flow rate is ignored and the entrainment capacity is used. The utilisation
   * of the supply is reported in both cases.
   *
   * @param limit true to limit the entrained flow to the suction supply
   */
  public void setLimitEntrainmentToSuctionSupply(boolean limit) {
    this.limitEntrainmentToSupply = limit;
  }

  /**
   * Sets the maximum allowed discharge pressure used for the margin in the operating-window report.
   *
   * @param pressureBara maximum discharge pressure in bara
   */
  public void setMaxDischargePressureLimit(double pressureBara) {
    this.maxDischargePressureLimit = pressureBara;
  }

  /**
   * Sizes a fixed geometry for a design entrained flow with the current motive and suction streams and discharge
   * pressure, and activates the fixed-geometry mode. The area ratio is the largest one for which the critical back
   * pressure is at least (1 + margin) times the discharge pressure; the absolute areas scale to the entrained flow.
   *
   * @param designSuctionFlowKgPerSec design entrained (vapour) flow in kg/s
   * @param backPressureMargin required relative margin of the critical back pressure over the discharge pressure (for
   * example 0.10)
   * @throws IllegalStateException if the discharge pressure is not set
   */
  public void autoSizeFixedGeometry(double designSuctionFlowKgPerSec, double backPressureMargin) {
    if (dischargePressure <= 0.0) {
      throw new IllegalStateException("set the discharge pressure before sizing the fixed geometry");
    }
    motiveStream.run();
    suctionStream.run();
    EjectorFixedGeometrySolver solver = newFixedSolver();
    EjectorFixedGeometrySolver.UnitFluid fp = EjectorFixedGeometrySolver.vaporUnitFluid(motiveStream.getThermoSystem(),
        Double.NaN);
    EjectorFixedGeometrySolver.UnitFluid fs = EjectorFixedGeometrySolver.vaporUnitFluid(suctionStream.getThermoSystem(),
        Double.NaN);
    double[] areas = solver.autoSize(fp.getFluid(), fs.getFluid(), dischargePressure, designSuctionFlowKgPerSec,
        backPressureMargin);
    setFixedGeometry(areas[0], areas[1]);
  }

  /**
   * Creates a fixed-geometry solver with the efficiencies of this ejector.
   *
   * @return new solver
   */
  private EjectorFixedGeometrySolver newFixedSolver() {
    return new EjectorFixedGeometrySolver(efficiencyIsentropic, suctionNozzleEfficiency, mixingEfficiency,
        diffuserEfficiency);
  }

  /**
   * Gets the last fixed-geometry operating point.
   *
   * @return operating point, or null if the fixed-geometry mode has not been run
   */
  public EjectorOperatingPoint getOperatingPoint() {
    return operatingPoint;
  }

  /**
   * Gets the motive flow demanded by the nozzle (choked flow) in the fixed-geometry mode.
   *
   * @return motive flow in kg/s, zero if not run in fixed-geometry mode
   */
  public double getMotiveFlowCapacity() {
    return operatingPoint == null ? 0.0 : operatingPoint.getMotiveFlow();
  }

  /**
   * Gets the entrainment capacity (suction vapour flow) of the fixed geometry at the last run.
   *
   * @return entrained flow capacity in kg/s, zero if not run in fixed-geometry mode
   */
  public double getEntrainmentCapacity() {
    return operatingPoint == null ? 0.0 : operatingPoint.getSuctionFlow();
  }

  /**
   * Gets the margin to breakdown, critical back pressure divided by discharge pressure minus one (fixed-geometry mode).
   * Negative when the back pressure is above the critical value.
   *
   * @return relative margin, NaN if not run in fixed-geometry mode
   */
  public double getBreakdownMargin() {
    return operatingPoint == null ? Double.NaN : operatingPoint.getBreakdownMargin();
  }

  /**
   * Returns the operating-window report of the last fixed-geometry run: motive demand, entrainment capacity and ratio,
   * compression ratio, critical back pressure and margins, supply utilisation and warnings.
   *
   * @return ordered map of report items, empty if the fixed-geometry mode has not been run
   */
  public java.util.Map<String, Object> getOperatingWindowReport() {
    java.util.Map<String, Object> r = new java.util.LinkedHashMap<String, Object>();
    if (operatingPoint == null) {
      return r;
    }
    r.putAll(operatingPoint.toMap());
    double motiveSupplied = motiveStream.getFlowRate("kg/sec");
    r.put("motiveStreamFlow_kg_s", motiveSupplied);
    r.put("motiveSupplyMargin",
        operatingPoint.getMotiveFlow() > 0.0 ? motiveSupplied / operatingPoint.getMotiveFlow() - 1.0 : Double.NaN);
    r.put("suctionVaporSupply_kg_s", suctionVaporSupply);
    r.put("suctionSupplyUtilisation",
        operatingPoint.getSuctionFlow() > 0.0 ? suctionVaporSupply / operatingPoint.getSuctionFlow() : Double.NaN);
    r.put("suctionStarved", isSuctionStarved());
    r.put("limitEntrainmentToSuctionSupply", limitEntrainmentToSupply);
    r.put("entrainedFlow_kg_s", mixedStream.getFlowRate("kg/sec") - operatingPoint.getMotiveFlow());
    r.put("maxDischargePressure_bara", maxDischargePressureLimit);
    r.put("dischargePressureMargin", Double.isNaN(maxDischargePressureLimit) ? Double.NaN
        : maxDischargePressureLimit / operatingPoint.getDischargePressure() - 1.0);
    return r;
  }

  /**
   * Returns the operating-window report as JSON.
   *
   * @return JSON text
   */
  public String getOperatingWindowReportJson() {
    return new com.google.gson.GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getOperatingWindowReport());
  }

  /**
   * Returns whether the suction stream supplies less vapour than the ejector can entrain (fixed-geometry mode). The
   * ejector then needs a recycle from the discharge or the suction pressure falls.
   *
   * @return true if the vapour supply is below the entrainment capacity
   */
  public boolean isSuctionStarved() {
    return operatingPoint != null && !Double.isNaN(suctionVaporSupply)
        && suctionVaporSupply < operatingPoint.getSuctionFlow() * (1.0 - 1.0e-9);
  }

  /**
   * Generates the fixed-geometry performance curve over the discharge (back) pressure at constant motive and suction
   * conditions. In contrast to {@link #generatePerformanceCurve(double, double, int)} the points are solved directly
   * (the streams and the ejector state are not modified) and carry the full operating point including regime, critical
   * back pressure and breakdown margin.
   *
   * @param minDischargePressure minimum discharge pressure in bara
   * @param maxDischargePressure maximum discharge pressure in bara
   * @param numPoints number of points (at least 2)
   * @return operating points in order of increasing discharge pressure
   * @throws IllegalStateException if the fixed-geometry mode is not active
   */
  public List<EjectorOperatingPoint> generateFixedGeometryCurve(double minDischargePressure,
      double maxDischargePressure, int numPoints) {
    requireFixedGeometry();
    motiveStream.run();
    suctionStream.run();
    int n = Math.max(numPoints, 2);
    EjectorFixedGeometrySolver solver = newFixedSolver();
    SystemInterface fp = EjectorFixedGeometrySolver.vaporUnitFluid(motiveStream.getThermoSystem(), Double.NaN)
        .getFluid();
    SystemInterface fs = EjectorFixedGeometrySolver.vaporUnitFluid(suctionStream.getThermoSystem(), Double.NaN)
        .getFluid();
    List<EjectorOperatingPoint> curve = new ArrayList<EjectorOperatingPoint>();
    for (int i = 0; i < n; i++) {
      double pd = minDischargePressure + i * (maxDischargePressure - minDischargePressure) / (n - 1);
      curve.add(solver.solvePoint(fp, fs, pd, fixedThroatArea, fixedMixingArea));
    }
    return curve;
  }

  /**
   * Generates the fixed-geometry capacity curve over the suction pressure (entrained flow versus suction pressure) at
   * constant motive conditions, suction temperature, composition and discharge pressure.
   *
   * @param minSuctionPressure minimum suction pressure in bara
   * @param maxSuctionPressure maximum suction pressure in bara
   * @param numPoints number of points (at least 2)
   * @return operating points in order of increasing suction pressure
   * @throws IllegalStateException if the fixed-geometry mode is not active
   */
  public List<EjectorOperatingPoint> generateSuctionPressureCurve(double minSuctionPressure, double maxSuctionPressure,
      int numPoints) {
    requireFixedGeometry();
    motiveStream.run();
    suctionStream.run();
    int n = Math.max(numPoints, 2);
    EjectorFixedGeometrySolver solver = newFixedSolver();
    SystemInterface fp = EjectorFixedGeometrySolver.vaporUnitFluid(motiveStream.getThermoSystem(), Double.NaN)
        .getFluid();
    List<EjectorOperatingPoint> curve = new ArrayList<EjectorOperatingPoint>();
    for (int i = 0; i < n; i++) {
      double ps = minSuctionPressure + i * (maxSuctionPressure - minSuctionPressure) / (n - 1);
      SystemInterface fs = EjectorFixedGeometrySolver.vaporUnitFluid(suctionStream.getThermoSystem(), ps).getFluid();
      curve.add(solver.solvePoint(fp, fs, dischargePressure, fixedThroatArea, fixedMixingArea));
    }
    return curve;
  }

  /**
   * Checks that the fixed-geometry mode is active.
   *
   * @throws IllegalStateException if the fixed-geometry mode is not active
   */
  private void requireFixedGeometry() {
    if (!fixedGeometryMode) {
      throw new IllegalStateException("fixed-geometry mode is not active; call setFixedGeometry first");
    }
  }

  /**
   * Runs the fixed-geometry calculation and updates the mixed stream and the mechanical design.
   *
   * @param id calculation identifier
   */
  private void runFixedGeometry(UUID id) {
    if (dischargePressure <= 0.0) {
      throw new IllegalStateException("discharge pressure must be set for the fixed-geometry ejector " + getName());
    }
    EjectorFixedGeometrySolver solver = newFixedSolver();
    EjectorFixedGeometrySolver.UnitFluid fp = EjectorFixedGeometrySolver.vaporUnitFluid(motiveStream.getThermoSystem(),
        Double.NaN);
    EjectorFixedGeometrySolver.UnitFluid fs = EjectorFixedGeometrySolver.vaporUnitFluid(suctionStream.getThermoSystem(),
        Double.NaN);
    EjectorOperatingPoint op = solver.solvePoint(fp.getFluid(), fs.getFluid(), dischargePressure, fixedThroatArea,
        fixedMixingArea);
    suctionVaporSupply = suctionStream.getFlowRate("kg/sec") * fs.getVaporMassFraction();
    double mEntrained = op.getSuctionFlow();
    if (limitEntrainmentToSupply && suctionVaporSupply < mEntrained) {
      mEntrained = suctionVaporSupply;
      op.setSuctionFlow(mEntrained);
      op.setEntrainmentRatio(op.getMotiveFlow() > 0.0 ? mEntrained / op.getMotiveFlow() : 0.0);
    }
    if (fp.getVaporMassFraction() < 0.999) {
      op.getWarnings().add("motive stream is two-phase: only the vapour phase is used as motive");
    }
    if (fs.getVaporMassFraction() < 0.999) {
      op.getWarnings().add("suction stream is two-phase: only the vapour phase is entrained");
    }
    if (suctionVaporSupply < op.getSuctionFlow() * (1.0 - 1.0e-9)) {
      op.getWarnings().add("suction supply below the entrainment capacity: recycle or lower suction pressure");
    }
    operatingPoint = op;

    SystemInterface mixedFluid = solver.buildMixedFluid(fp.getFluid(), op.getMotiveFlow(), fs.getFluid(), mEntrained,
        dischargePressure);
    if (mixedStream == null) {
      mixedStream = motiveStream.clone();
    }
    mixedStream.setThermoSystem(mixedFluid);
    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);

    compressionRatio = op.getCompressionRatio();
    expansionRatio = op.getExpansionRatio();
    criticalBackPressure = op.getCriticalBackPressure();
    areaRatio = op.getAreaRatio();
    motiveChoked = op.isMotiveChoked();
    mixingMach = Double.isNaN(op.getMixingMach()) ? 0.0 : op.getMixingMach();
    updateFixedGeometryDesign(op, mixedFluid, mEntrained);
  }

  /**
   * Updates the mechanical design container from a fixed-geometry operating point.
   *
   * @param op operating point
   * @param mixedFluid flashed mixed fluid at the discharge pressure
   * @param mEntrained entrained flow in kg/s
   */
  private void updateFixedGeometryDesign(EjectorOperatingPoint op, SystemInterface mixedFluid, double mEntrained) {
    double mTotal = op.getMotiveFlow() + mEntrained;
    mixedFluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    double rhoOut = Math.max(mixedFluid.getDensity("kg/m3"), 1.0e-6);
    double diffuserVelocity = designDiffuserVelocityOverride ? designDiffuserOutletVelocity
        : estimateDesignDiffuserOutletVelocity(op.getMixingPressure(), dischargePressure, rhoOut, mTotal);
    diffuserVelocity = Math.max(diffuserVelocity, 1.0e-6);
    double diffuserArea = mTotal / (rhoOut * diffuserVelocity);
    double suctionArea = Math.max(fixedMixingArea - fixedThroatArea, 1.0e-9);
    double nozzleLength = estimateLength(diameterFromArea(fixedThroatArea), DEFAULT_NOZZLE_LENGTH_FACTOR);
    double suctionLength = estimateLength(diameterFromArea(suctionArea), DEFAULT_SUCTION_LENGTH_FACTOR);
    double mixingLength = estimateLength(diameterFromArea(fixedMixingArea), DEFAULT_MIXING_LENGTH_FACTOR);
    double diffuserLength = estimateLength(diameterFromArea(diffuserArea), DEFAULT_DIFFUSER_LENGTH_FACTOR);
    double suctionConnection = suctionConnectionLengthOverride ? suctionConnectionLength
        : estimateSuctionConnectionLength(diameterFromArea(suctionArea), op.getSuctionPressure(), dischargePressure);
    double dischargeConnection = dischargeConnectionLengthOverride ? dischargeConnectionLength
        : estimateDischargeConnectionLength(diameterFromArea(diffuserArea), op.getMixingPressure(), dischargePressure);
    double bodyVolume = cylinderVolume(fixedThroatArea, nozzleLength) + cylinderVolume(suctionArea, suctionLength)
        + cylinderVolume(fixedMixingArea, mixingLength) + cylinderVolume(diffuserArea, diffuserLength);
    double piping = cylinderVolume(suctionArea, suctionConnection) + cylinderVolume(diffuserArea, dischargeConnection);
    getMechanicalDesign().updateDesign(op.getMixingPressure(), fixedThroatArea, op.getNozzleExitVelocity(), suctionArea,
        op.getSuctionVelocity(), fixedMixingArea, op.getMixingVelocity(), diffuserArea, diffuserVelocity,
        op.getEntrainmentRatio(), nozzleLength, suctionLength, mixingLength, diffuserLength, bodyVolume, piping,
        suctionConnection, dischargeConnection);
  }

  /**
   * Sets the target mixing pressure within the ejector. If not set the suction pressure is used.
   *
   * @param mixingPressure the mixing pressure in bara
   */
  public void setMixingPressure(double mixingPressure) {
    this.mixingPressure = mixingPressure;
  }

  /**
   * Sets the design suction velocity used when calculating mechanical dimensions.
   *
   * @param velocity velocity in m/s
   */
  public void setDesignSuctionVelocity(double velocity) {
    this.designSuctionVelocity = velocity;
    this.designSuctionVelocityOverride = true;
  }

  /**
   * Sets the design diffuser outlet velocity used when calculating mechanical dimensions.
   *
   * @param velocity velocity in m/s
   */
  public void setDesignDiffuserOutletVelocity(double velocity) {
    this.designDiffuserOutletVelocity = velocity;
    this.designDiffuserVelocityOverride = true;
  }

  /**
   * Sets the straight length of suction piping to include in total volume calculations.
   *
   * @param length length in metres
   */
  public void setSuctionConnectionLength(double length) {
    this.suctionConnectionLength = Math.max(length, 0.0);
    this.suctionConnectionLengthOverride = true;
  }

  /**
   * Sets the straight length of discharge piping to include in total volume calculations.
   *
   * @param length length in metres
   */
  public void setDischargeConnectionLength(double length) {
    this.dischargeConnectionLength = Math.max(length, 0.0);
    this.dischargeConnectionLengthOverride = true;
  }

  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new EjectorMechanicalDesign(this);
  }

  @Override
  public EjectorMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      initMechanicalDesign();
    }
    return mechanicalDesign;
  }

  /**
   * Backwards compatible accessor for mechanical design results.
   *
   * @return the ejector mechanical design container
   */
  public EjectorMechanicalDesign getDesignResult() {
    return getMechanicalDesign();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    motiveStream.run();
    suctionStream.run();
    if (fixedGeometryMode) {
      runFixedGeometry(id);
      return;
    }
    // Keep the same outlet stream object across runs so downstream equipment that was connected to
    // getOutStream()/getMixedStream() before the first run keeps receiving the updated results.
    if (mixedStream == null) {
      mixedStream = motiveStream.clone();
    }

    double suctionPressure = suctionStream.getPressure("bara");
    double mDotMotive = motiveStream.getFlowRate("kg/sec");
    double mDotSuction = suctionStream.getFlowRate("kg/sec");

    double localMixingPressure = Double.isNaN(mixingPressure)
        ? estimateDefaultMixingPressure(suctionPressure, dischargePressure, mDotMotive, mDotSuction)
        : mixingPressure;

    if (localMixingPressure > suctionPressure) {
      logger.warn("Mixing pressure {} bara exceeds suction pressure {} bara – using suction "
          + "pressure for mixing calculations.", localMixingPressure, suctionPressure);
      localMixingPressure = suctionPressure;
    }

    double mDotTotal = mDotMotive + mDotSuction;

    if (mDotTotal <= 0.0) {
      mixedStream.setThermoSystem(motiveStream.getThermoSystem().clone());
      mixedStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      getMechanicalDesign().resetDesign();
      return;
    }

    // Motive nozzle calculation
    SystemInterface motiveNozzle = motiveStream.getThermoSystem().clone();
    double entropyMotive = motiveNozzle.getEntropy();
    double hMotiveIn = motiveNozzle.getEnthalpy("J/kg");
    motiveNozzle.setPressure(localMixingPressure, "bara");
    ThermodynamicOperations nozzleOps = new ThermodynamicOperations(motiveNozzle);
    nozzleOps.PSflash(entropyMotive);
    double hMotiveIsentropic = motiveNozzle.getEnthalpy("J/kg");
    double hMotiveActual = hMotiveIn - efficiencyIsentropic * (hMotiveIn - hMotiveIsentropic);
    nozzleOps.PHflash(hMotiveActual, "J/kg");
    motiveNozzle.init(3);
    motiveNozzle.initPhysicalProperties();
    double deltaHNozzle = Math.max(hMotiveIn - hMotiveActual, 0.0);
    double velocityNozzle = Math.sqrt(2.0 * deltaHNozzle);
    double rhoNozzle = Math.max(motiveNozzle.getDensity("kg/m3"), 1.0e-9);
    double motiveNozzleArea = mDotMotive / (rhoNozzle * Math.max(velocityNozzle, 1.0e-6));

    // Mach number at motive nozzle exit (estimate speed of sound from fluid)
    double sosMotive = estimateSpeedOfSound(motiveNozzle);
    motiveNozzleMach = sosMotive > 0 ? velocityNozzle / sosMotive : 0.0;
    motiveChoked = motiveNozzleMach >= 0.99;

    // Suction flow accelerated to mixing section (with suction nozzle efficiency)
    SystemInterface suctionAtMixing = suctionStream.getThermoSystem().clone();
    double hSuctionIn = suctionAtMixing.getEnthalpy("J/kg");
    double entropySuction = suctionAtMixing.getEntropy();
    suctionAtMixing.setPressure(localMixingPressure, "bara");
    ThermodynamicOperations suctionOps = new ThermodynamicOperations(suctionAtMixing);
    suctionOps.PSflash(entropySuction);
    double hSuctionIsentropic = suctionAtMixing.getEnthalpy("J/kg");
    double hSuctionActual = hSuctionIn - suctionNozzleEfficiency * (hSuctionIn - hSuctionIsentropic);
    suctionOps.PHflash(hSuctionActual, "J/kg");
    suctionAtMixing.init(3);
    suctionAtMixing.initPhysicalProperties();
    double rhoSuction = Math.max(suctionAtMixing.getDensity("kg/m3"), 1.0e-9);
    double deltaHSuction = Math.max(hSuctionIn - hSuctionActual, 0.0);
    double velocitySuctionFromEnthalpy = Math.sqrt(2.0 * deltaHSuction);

    double localDesignSuctionVelocity;
    if (designSuctionVelocityOverride) {
      // User-specified override takes precedence – do not recompute
      localDesignSuctionVelocity = Math.max(designSuctionVelocity, 1.0e-6);
    } else {
      // Use the larger of thermodynamic velocity and the empirical estimate
      double empirical = estimateDesignSuctionVelocity(suctionPressure, dischargePressure, rhoSuction, mDotSuction);
      localDesignSuctionVelocity = Math.max(Math.max(velocitySuctionFromEnthalpy, empirical), 1.0e-6);
    }

    double suctionArea = mDotSuction / (rhoSuction * localDesignSuctionVelocity);
    double velocitySuction = mDotSuction / (rhoSuction * Math.max(suctionArea, 1.0e-9));

    // Mach number at suction entry to mixing
    double sosSuction = estimateSpeedOfSound(suctionAtMixing);
    suctionMach = sosSuction > 0 ? velocitySuction / sosSuction : 0.0;
    suctionChoked = suctionMach >= 0.99;

    double totalEnthalpyMotive = hMotiveActual + 0.5 * velocityNozzle * velocityNozzle;
    double totalEnthalpySuction = hSuctionActual + 0.5 * velocitySuction * velocitySuction;

    // Momentum mixing with mixing efficiency (accounts for friction and turbulent losses)
    double idealMixingVelocity = (mDotMotive * velocityNozzle + mDotSuction * velocitySuction) / mDotTotal;
    double mixingVelocity = mixingEfficiency * idealMixingVelocity;
    double mixedTotalEnthalpy = (mDotMotive * totalEnthalpyMotive + mDotSuction * totalEnthalpySuction) / mDotTotal;
    double mixedStaticEnthalpy = mixedTotalEnthalpy - 0.5 * mixingVelocity * mixingVelocity;

    SystemInterface mixedFluid = motiveStream.getThermoSystem().clone();
    mixedFluid.addFluid(suctionStream.getThermoSystem());
    mixedFluid.setPressure(localMixingPressure, "bara");
    ThermodynamicOperations mixingOps = new ThermodynamicOperations(mixedFluid);
    mixingOps.PHflash(mixedStaticEnthalpy, "J/kg");
    mixedFluid.init(3);
    mixedFluid.initPhysicalProperties();
    double rhoMixing = Math.max(mixedFluid.getDensity("kg/m3"), 1.0e-9);
    double mixingArea = mDotTotal / (rhoMixing * Math.max(mixingVelocity, 1.0e-6));

    // Mach number in mixing section
    double sosMixing = estimateSpeedOfSound(mixedFluid);
    mixingMach = sosMixing > 0 ? mixingVelocity / sosMixing : 0.0;

    // Area ratio (key Transvac sizing parameter)
    areaRatio = motiveNozzleArea > 0 ? mixingArea / motiveNozzleArea : 0.0;

    // Diffuser recovery
    double recoveredSpecificEnergy = diffuserEfficiency * 0.5 * mixingVelocity * mixingVelocity;
    double staticEnthalpyBeforeDiffuser = mixedStaticEnthalpy + recoveredSpecificEnergy;

    mixedFluid.setPressure(dischargePressure, "bara");
    ThermodynamicOperations diffuserOps = new ThermodynamicOperations(mixedFluid);
    diffuserOps.PHflash(staticEnthalpyBeforeDiffuser, "J/kg");
    mixedFluid.init(3);
    // Diffuser sizing consumes density at the flashed discharge state. Refresh only
    // that required property; no other physical property is consumed by this calculation.
    mixedFluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    double rhoDiffuser = Math.max(mixedFluid.getDensity("kg/m3"), 1.0e-9);

    double localDesignDiffuserOutletVelocity = designDiffuserVelocityOverride ? designDiffuserOutletVelocity
        : estimateDesignDiffuserOutletVelocity(localMixingPressure, dischargePressure, rhoDiffuser, mDotTotal);
    localDesignDiffuserOutletVelocity = Math.max(localDesignDiffuserOutletVelocity, 1.0e-6);

    double diffuserArea = mDotTotal / (rhoDiffuser * localDesignDiffuserOutletVelocity);
    double diffuserVelocity = mDotTotal / (rhoDiffuser * Math.max(diffuserArea, 1.0e-9));
    double finalStaticEnthalpy = staticEnthalpyBeforeDiffuser - 0.5 * diffuserVelocity * diffuserVelocity;

    diffuserOps.PHflash(finalStaticEnthalpy, "J/kg");
    mixedFluid.init(3);

    // Calculate key Transvac-style performance parameters
    double motivePressure = motiveStream.getPressure("bara");
    compressionRatio = suctionPressure > 0 ? dischargePressure / suctionPressure : 0.0;
    expansionRatio = dischargePressure > 0 ? motivePressure / dischargePressure : 0.0;

    // Estimate critical back pressure: the maximum discharge pressure at which the ejector
    // can still operate. Above this, the suction flow unchokes and entrainment ratio drops
    // sharply. Uses the total enthalpy (stagnation enthalpy) and a thermodynamic flash to find
    // the maximum achievable pressure through the diffuser.
    criticalBackPressure = estimateCriticalBackPressure(mixedFluid.clone(), mixedTotalEnthalpy, localMixingPressure,
        dischargePressure);

    double motiveNozzleDiameter = diameterFromArea(motiveNozzleArea);
    double suctionDiameter = diameterFromArea(suctionArea);
    double mixingDiameter = diameterFromArea(mixingArea);
    double diffuserDiameter = diameterFromArea(diffuserArea);

    double nozzleLength = estimateLength(motiveNozzleDiameter, DEFAULT_NOZZLE_LENGTH_FACTOR);
    double suctionLength = estimateLength(suctionDiameter, DEFAULT_SUCTION_LENGTH_FACTOR);
    double mixingLength = estimateLength(mixingDiameter, DEFAULT_MIXING_LENGTH_FACTOR);
    double diffuserLength = estimateLength(diffuserDiameter, DEFAULT_DIFFUSER_LENGTH_FACTOR);

    double localSuctionConnectionLength = suctionConnectionLengthOverride ? suctionConnectionLength
        : estimateSuctionConnectionLength(suctionDiameter, suctionPressure, dischargePressure);
    double localDischargeConnectionLength = dischargeConnectionLengthOverride ? dischargeConnectionLength
        : estimateDischargeConnectionLength(diffuserDiameter, localMixingPressure, dischargePressure);

    double bodyVolume = cylinderVolume(motiveNozzleArea, nozzleLength) + cylinderVolume(suctionArea, suctionLength)
        + cylinderVolume(mixingArea, mixingLength) + cylinderVolume(diffuserArea, diffuserLength);

    double connectedPipingVolume = cylinderVolume(suctionArea, localSuctionConnectionLength)
        + cylinderVolume(diffuserArea, localDischargeConnectionLength);

    mixedStream.setThermoSystem(mixedFluid);
    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);

    getMechanicalDesign().updateDesign(localMixingPressure, motiveNozzleArea, velocityNozzle, suctionArea,
        velocitySuction, mixingArea, mixingVelocity, diffuserArea, diffuserVelocity, getEntrainmentRatio(),
        nozzleLength, suctionLength, mixingLength, diffuserLength, bodyVolume, connectedPipingVolume,
        localSuctionConnectionLength, localDischargeConnectionLength);
  }

  /**
   * getOutStream.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOutStream() {
    return mixedStream;
  }

  /**
   * getEntrainmentRatio.
   *
   * @return a double
   */
  public double getEntrainmentRatio() {
    if (fixedGeometryMode && operatingPoint != null) {
      return operatingPoint.getEntrainmentRatio();
    }
    return suctionStream.getFlowRate("kg/sec") / motiveStream.getFlowRate("kg/sec");
  }

  /**
   * Returns the compression ratio (discharge pressure / suction pressure). This is a key parameter in Transvac ejector
   * specification.
   *
   * @return compression ratio (dimensionless)
   */
  public double getCompressionRatio() {
    return compressionRatio;
  }

  /**
   * Returns the expansion ratio (motive pressure / discharge pressure). This indicates how much energy is available
   * from the motive stream.
   *
   * @return expansion ratio (dimensionless)
   */
  public double getExpansionRatio() {
    return expansionRatio;
  }

  /**
   * Returns the critical back pressure in bara. This is the maximum discharge pressure at which the ejector can
   * maintain stable operation with the current motive conditions. Above this pressure, the ejector breaks down and
   * entrainment ratio drops sharply to zero. This is the most important parameter in Transvac-style ejector design.
   *
   * @return critical back pressure in bara
   */
  public double getCriticalBackPressure() {
    return criticalBackPressure;
  }

  /**
   * Returns the area ratio (mixing section area / motive nozzle throat area). This is the primary sizing parameter for
   * ejectors and determines the operating envelope.
   *
   * @return area ratio (dimensionless)
   */
  public double getAreaRatio() {
    return areaRatio;
  }

  /**
   * Returns the Mach number at the motive nozzle exit.
   *
   * @return motive nozzle exit Mach number
   */
  public double getMotiveNozzleMach() {
    return motiveNozzleMach;
  }

  /**
   * Returns the Mach number of the suction flow at the mixing section entrance.
   *
   * @return suction Mach number
   */
  public double getSuctionMach() {
    return suctionMach;
  }

  /**
   * Returns the Mach number of the mixed flow in the mixing section.
   *
   * @return mixing section Mach number
   */
  public double getMixingMach() {
    return mixingMach;
  }

  /**
   * Returns whether the suction flow is choked (Mach &gt;= 1). When choked, the ejector is operating in its design
   * regime and the entrainment ratio is stable.
   *
   * @return true if suction flow is choked
   */
  public boolean isSuctionChoked() {
    return suctionChoked;
  }

  /**
   * Returns whether the motive flow is choked at the nozzle. For proper ejector operation, the motive flow should
   * always be choked.
   *
   * @return true if motive flow is choked
   */
  public boolean isMotiveChoked() {
    return motiveChoked;
  }

  /**
   * Checks if the current discharge pressure exceeds the critical back pressure, meaning the ejector is operating in
   * breakdown mode and cannot maintain stable entrainment.
   *
   * @return true if the ejector is operating beyond its critical back pressure
   */
  public boolean isInBreakdown() {
    if (fixedGeometryMode && operatingPoint != null) {
      return operatingPoint.isInBreakdown();
    }
    return criticalBackPressure > 0 && dischargePressure > criticalBackPressure;
  }

  /**
   * Generates a performance map showing how entrainment ratio varies with discharge pressure at constant motive and
   * suction conditions. This produces data similar to Transvac performance curves. Each point is calculated by running
   * the ejector at a different discharge pressure.
   *
   * @param minDischargePressure minimum discharge pressure in bara
   * @param maxDischargePressure maximum discharge pressure in bara
   * @param numPoints number of points in the curve
   * @return list of double arrays [dischargePressure, entrainmentRatio, compressionRatio]
   */
  public List<double[]> generatePerformanceCurve(double minDischargePressure, double maxDischargePressure,
      int numPoints) {
    List<double[]> curve = new ArrayList<double[]>();
    if (numPoints < 2) {
      numPoints = 2;
    }
    double step = (maxDischargePressure - minDischargePressure) / (numPoints - 1);
    double originalDischargePressure = this.dischargePressure;

    for (int i = 0; i < numPoints; i++) {
      double pDischarge = minDischargePressure + i * step;
      this.dischargePressure = pDischarge;
      try {
        this.run();
        double suctionP = suctionStream.getPressure("bara");
        double cr = suctionP > 0 ? pDischarge / suctionP : 0.0;
        curve.add(new double[] {pDischarge, getEntrainmentRatio(), cr});
      } catch (Exception ex) {
        logger.warn("Performance curve point at {} bara failed: {}", pDischarge, ex.getMessage());
      }
    }

    // Restore original discharge pressure and re-run
    this.dischargePressure = originalDischargePressure;
    try {
      this.run();
    } catch (Exception ex) {
      logger.warn("Failed to restore ejector to original discharge pressure", ex);
    }
    return curve;
  }

  private static double diameterFromArea(double area) {
    if (area <= 0.0) {
      return 0.0;
    }
    return Math.sqrt(4.0 * area / Math.PI);
  }

  private static double estimateLength(double diameter, double factor) {
    if (diameter <= 0.0 || factor <= 0.0) {
      return 0.0;
    }
    return factor * diameter;
  }

  private static double cylinderVolume(double area, double length) {
    if (area <= 0.0 || length <= 0.0) {
      return 0.0;
    }
    return area * length;
  }

  private double estimateDefaultMixingPressure(double suctionPressure, double dischargePressure, double mDotMotive,
      double mDotSuction) {
    if (suctionPressure <= 0.0) {
      return Math.max(dischargePressure, 0.0);
    }

    double entrainmentRatio = mDotMotive > 1.0e-9 ? Math.max(mDotSuction, 0.0) / mDotMotive : 1.0;
    double clampedRatio = clamp(entrainmentRatio, 0.0, 5.0);
    double pressureLift = Math.max(dischargePressure - suctionPressure, 0.0);
    double pressureDrop = pressureLift * (0.1 + 0.03 * clampedRatio);
    double suctionMargin = suctionPressure * 0.03;
    double estimated = suctionPressure - Math.max(pressureDrop, suctionMargin);
    double minimumPressure = Math.max(0.01, suctionPressure * 0.4);
    return clamp(estimated, minimumPressure, suctionPressure);
  }

  private double estimateDesignSuctionVelocity(double suctionPressure, double dischargePressure, double rhoSuction,
      double mDotSuction) {
    double density = Math.max(rhoSuction, 1.0e-6);
    double availableLiftPa = Math.max((dischargePressure - suctionPressure) * BAR_TO_PA, 0.0);
    double targetDynamic = Math.max(availableLiftPa * 0.02, 500.0);
    double baseline = Math.sqrt(2.0 * targetDynamic / density);
    double volumetricFlow = mDotSuction > 0.0 ? mDotSuction / density : 0.0;
    double flowScaling = volumetricFlow > 0.0 ? 50.0 + 30.0 * Math.log10(1.0 + volumetricFlow * 5.0) : 50.0;
    double blended = 0.6 * baseline + 0.4 * flowScaling;
    return clamp(blended, 25.0, 120.0);
  }

  private double estimateDesignDiffuserOutletVelocity(double mixingPressure, double dischargePressure,
      double rhoDiffuser, double mDotTotal) {
    double density = Math.max(rhoDiffuser, 1.0e-6);
    double pressureRecoveryPa = Math.max((dischargePressure - mixingPressure) * BAR_TO_PA, 0.0);
    double targetDynamic = Math.max(pressureRecoveryPa * 0.01, 250.0);
    double baseline = Math.sqrt(2.0 * targetDynamic / density);
    double volumetricFlow = mDotTotal > 0.0 ? mDotTotal / density : 0.0;
    double flowScaling = volumetricFlow > 0.0 ? 20.0 + 15.0 * Math.log10(1.0 + volumetricFlow * 4.0) : 25.0;
    double blended = 0.5 * baseline + 0.5 * flowScaling;
    return clamp(blended, 10.0, 60.0);
  }

  private double estimateSuctionConnectionLength(double suctionDiameter, double suctionPressure,
      double dischargePressure) {
    if (suctionDiameter <= 0.0) {
      return 0.0;
    }
    double pressureRatio = dischargePressure > 0.0 && suctionPressure > 0.0 ? dischargePressure / suctionPressure : 1.0;
    double factor = 3.0 + 1.5 * clamp(pressureRatio - 1.0, 0.0, 3.0);
    return factor * suctionDiameter;
  }

  private double estimateDischargeConnectionLength(double diffuserDiameter, double mixingPressure,
      double dischargePressure) {
    if (diffuserDiameter <= 0.0) {
      return 0.0;
    }
    double pressureRatio = dischargePressure > 0.0 && mixingPressure > 0.0 ? dischargePressure / mixingPressure : 1.0;
    double factor = 5.0 + 2.0 * clamp(pressureRatio - 1.0, 0.0, 3.0);
    return factor * diffuserDiameter;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(value, max));
  }

  /**
   * Estimates the speed of sound in a fluid from thermodynamic properties. Uses the relationship c = sqrt(Cp/Cv * P /
   * rho) for an ideal-gas approximation when detailed derivatives are not available. For real fluids, this is a
   * reasonable estimate that errs slightly on the conservative side.
   *
   * @param fluid the fluid to estimate speed of sound for
   * @return estimated speed of sound in m/s, or 0.0 if cannot be estimated
   */
  private double estimateSpeedOfSound(SystemInterface fluid) {
    try {
      double rho = fluid.getDensity("kg/m3");
      if (rho <= 0) {
        return 0.0;
      }

      // Try using Cp/Cv ratio (kappa) for ideal-gas speed of sound approximation
      // c = sqrt(kappa * P / rho) where P is in Pa
      double pressurePa = fluid.getPressure("Pa");
      double kappa = fluid.getGamma2(); // Cp/Cv ratio
      if (kappa > 0 && pressurePa > 0) {
        return Math.sqrt(kappa * pressurePa / rho);
      }

      // Fallback: use molar mass and temperature for ideal gas estimate
      // c = sqrt(gamma * R * T / M)
      double temperature = fluid.getTemperature(); // Kelvin
      double molarMass = fluid.getMolarMass("kg/mol");
      if (molarMass > 0 && temperature > 0) {
        double gamma = 1.3; // reasonable default for hydrocarbon gases
        return Math.sqrt(gamma * 8.314 * temperature / molarMass);
      }
    } catch (Exception e) {
      logger.debug("Could not estimate speed of sound: {}", e.getMessage());
    }
    return 0.0;
  }

  /**
   * Estimates the critical back pressure for the ejector. This is the maximum discharge pressure at which stable
   * ejector operation can be maintained. Above this pressure, the normal shock in the mixing section becomes too strong
   * and the suction flow unchokes.
   *
   * <p>
   * The estimation uses a bisection search to find the maximum pressure at which the mixed fluid's static enthalpy
   * (from a PH flash at total/stagnation enthalpy) is consistent with the diffuser efficiency. This is more accurate
   * than analytic formulas, especially for compressible gas ejectors where density varies significantly with pressure.
   * </p>
   *
   * @param mixedFluidClone a clone of the mixed fluid for trial flashes
   * @param totalEnthalpy total (stagnation) enthalpy of mixed stream in J/kg
   * @param mixingPressureLocal mixing pressure in bara
   * @param currentDischargePressure current discharge pressure in bara (starting point)
   * @return estimated critical back pressure in bara
   */
  private double estimateCriticalBackPressure(SystemInterface mixedFluidClone, double totalEnthalpy,
      double mixingPressureLocal, double currentDischargePressure) {
    if (mixingPressureLocal <= 0) {
      return 0.0;
    }

    // The stagnation enthalpy includes kinetic energy. The maximum achievable pressure is found
    // where the entire enthalpy is converted to pressure (zero velocity). We search for the
    // highest pressure at which a PH-flash at the total enthalpy with diffuser losses is valid.

    // Effective total enthalpy available through diffuser (accounts for diffuser losses):
    // h_recoverable = h_static + eta_diff * (h_total - h_static) = h_static + eta_diff * KE
    // Since we already have h_total and the kinetic energy is embedded in it,
    // the diffuser-limited enthalpy is approximately the discharge enthalpy from run().
    // For CBP estimation, use the total enthalpy (ideal diffuser) as the upper limit
    // and apply diffuser efficiency as a pressure reduction.

    try {
      // Upper bound: pressure at stagnation enthalpy (perfect diffuser, all KE -> pressure)
      // Try increasing pressure from mixing pressure until flash fails or entropy reverses
      double pLow = mixingPressureLocal;
      double pHigh = Math.max(currentDischargePressure * 3.0, mixingPressureLocal * 10.0);
      double cbp = mixingPressureLocal;

      // Binary search for the highest pressure at which a PH-flash at the recoverable
      // enthalpy produces a well-defined thermodynamic state
      int maxIterations = 20;
      for (int i = 0; i < maxIterations; i++) {
        double pTrial = 0.5 * (pLow + pHigh);
        try {
          SystemInterface trial = mixedFluidClone.clone();
          trial.setPressure(pTrial, "bara");
          ThermodynamicOperations trialOps = new ThermodynamicOperations(trial);
          trialOps.PHflash(totalEnthalpy, "J/kg");
          trial.init(3);

          // Check if the flash produced a valid state (temperature reasonable)
          double trialTemp = trial.getTemperature();
          if (trialTemp > 0 && trialTemp < 2000.0) {
            // Valid state - this pressure is achievable, try higher
            cbp = pTrial;
            pLow = pTrial;
          } else {
            pHigh = pTrial;
          }
        } catch (Exception e) {
          // Flash failed - pressure too high
          pHigh = pTrial;
        }

        if (pHigh - pLow < 0.01) {
          break;
        }
      }

      // Apply diffuser efficiency: the practical CBP is between mixing pressure and ideal CBP
      // CBP_practical = P_mixing + eta_diff * (CBP_ideal - P_mixing)
      double idealCBP = cbp;
      return mixingPressureLocal + diffuserEfficiency * (idealCBP - mixingPressureLocal);
    } catch (Exception e) {
      logger.debug("CBP estimation failed, using default: {}", e.getMessage());
      return mixingPressureLocal;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    if (fixedGeometryMode && operatingPoint != null) {
      double inKgS = operatingPoint.getMotiveFlow() + operatingPoint.getSuctionFlow();
      double outKgS = mixedStream.getThermoSystem().getFlowRate("kg/sec");
      double scale = mixedStream.getThermoSystem().getFlowRate(unit) / Math.max(outKgS, 1.0e-300);
      return outKgS * scale - inKgS * scale;
    }
    double inletFlow = motiveStream.getThermoSystem().getFlowRate(unit)
        + suctionStream.getThermoSystem().getFlowRate(unit);
    return mixedStream.getThermoSystem().getFlowRate(unit) - inletFlow;
  }

  /**
   * Getter for the field <code>motiveStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getMotiveStream() {
    return motiveStream;
  }

  /**
   * Getter for the field <code>suctionStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getSuctionStream() {
    return suctionStream;
  }

  /**
   * Getter for the field <code>mixedStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getMixedStream() {
    return mixedStream;
  }

  /**
   * Getter for the field <code>efficiencyIsentropic</code>.
   *
   * @return a double
   */
  public double getEfficiencyIsentropic() {
    return efficiencyIsentropic;
  }

  /**
   * Getter for the field <code>diffuserEfficiency</code>.
   *
   * @return a double
   */
  public double getDiffuserEfficiency() {
    return diffuserEfficiency;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new neqsim.process.util.monitor.EjectorResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(neqsim.process.util.report.ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == neqsim.process.util.report.ReportConfig.DetailLevel.HIDE) {
      return null;
    }
    neqsim.process.util.monitor.EjectorResponse res = new neqsim.process.util.monitor.EjectorResponse(this);
    res.applyConfig(cfg);
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  // ============================================================================
  // AutoSizeable Implementation
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    if (motiveStream == null || suctionStream == null) {
      throw new IllegalStateException("Both motive and suction streams must be connected before auto-sizing ejector");
    }

    // Calculate design values from current operating point
    double currentEntrainmentRatio = getEntrainmentRatio();
    double suctionPressure = suctionStream.getPressure("bara");
    double currentCompressionRatio = dischargePressure / suctionPressure;
    double motiveFlowRate = motiveStream.getFlowRate("kg/sec");

    // Apply safety factor
    this.designEntrainmentRatio = currentEntrainmentRatio * safetyFactor;
    this.designCompressionRatio = currentCompressionRatio;
    this.designMotiveFlowRate = motiveFlowRate * safetyFactor;
    this.maxCriticalBackPressure = dischargePressure * 1.1; // 10% margin

    // Initialize capacity constraints
    initializeEjectorCapacityConstraints();

    autoSized = true;
    logger.info("Ejector '{}' auto-sized: design ER={}, CR={}", getName(), designEntrainmentRatio,
        designCompressionRatio);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize() {
    autoSize(1.2);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(String companyStandard, String trDocument) {
    // Load company-specific parameters from database if available
    if (mechanicalDesign != null) {
      mechanicalDesign.setCompanySpecificDesignStandards(companyStandard);
    }
    autoSize(1.2);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return autoSized;
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Ejector Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(isAutoSized()).append("\n");

    if (motiveStream != null && suctionStream != null) {
      sb.append("\n--- Operating Conditions ---\n");
      sb.append("Motive Pressure: ").append(String.format(Locale.US, "%.2f bara", motiveStream.getPressure("bara")))
          .append("\n");
      sb.append("Suction Pressure: ").append(String.format(Locale.US, "%.2f bara", suctionStream.getPressure("bara")))
          .append("\n");
      sb.append("Discharge Pressure: ").append(String.format(Locale.US, "%.2f bara", dischargePressure)).append("\n");
      sb.append("Motive Flow: ").append(String.format(Locale.US, "%.3f kg/s", motiveStream.getFlowRate("kg/sec")))
          .append("\n");
      sb.append("Suction Flow: ").append(String.format(Locale.US, "%.3f kg/s", suctionStream.getFlowRate("kg/sec")))
          .append("\n");

      sb.append("\n--- Performance ---\n");
      sb.append("Entrainment Ratio: ").append(String.format(Locale.US, "%.3f", getEntrainmentRatio())).append("\n");
      sb.append("Compression Ratio: ")
          .append(String.format(Locale.US, "%.3f", dischargePressure / suctionStream.getPressure("bara"))).append("\n");
      sb.append("Nozzle Efficiency: ").append(String.format(Locale.US, "%.1f%%", efficiencyIsentropic * 100))
          .append("\n");
      sb.append("Diffuser Efficiency: ").append(String.format(Locale.US, "%.1f%%", diffuserEfficiency * 100))
          .append("\n");

      if (isAutoSized()) {
        sb.append("\n--- Design Values ---\n");
        sb.append("Design Entrainment Ratio: ").append(String.format(Locale.US, "%.3f", designEntrainmentRatio))
            .append("\n");
        sb.append("Design Compression Ratio: ").append(String.format(Locale.US, "%.3f", designCompressionRatio))
            .append("\n");
        sb.append("Design Motive Flow: ").append(String.format(Locale.US, "%.3f kg/s", designMotiveFlowRate))
            .append("\n");
        sb.append("Max Critical Back Pressure: ").append(String.format(Locale.US, "%.2f bara", maxCriticalBackPressure))
            .append("\n");
      }
    }

    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReportJson() {
    java.util.Map<String, Object> report = new java.util.LinkedHashMap<String, Object>();
    report.put("equipmentName", getName());
    report.put("equipmentType", "Ejector");
    report.put("autoSized", autoSized);

    if (motiveStream != null && suctionStream != null) {
      java.util.Map<String, Object> operating = new java.util.LinkedHashMap<String, Object>();
      operating.put("motivePressure_bara", motiveStream.getPressure("bara"));
      operating.put("suctionPressure_bara", suctionStream.getPressure("bara"));
      operating.put("dischargePressure_bara", dischargePressure);
      operating.put("motiveFlow_kg_s", motiveStream.getFlowRate("kg/sec"));
      operating.put("suctionFlow_kg_s", suctionStream.getFlowRate("kg/sec"));
      operating.put("entrainmentRatio", getEntrainmentRatio());
      operating.put("compressionRatio", dischargePressure / suctionStream.getPressure("bara"));
      report.put("operatingConditions", operating);

      if (autoSized) {
        java.util.Map<String, Object> design = new java.util.LinkedHashMap<String, Object>();
        design.put("designEntrainmentRatio", designEntrainmentRatio);
        design.put("designCompressionRatio", designCompressionRatio);
        design.put("designMotiveFlow_kg_s", designMotiveFlowRate);
        design.put("maxCriticalBackPressure_bara", maxCriticalBackPressure);
        report.put("designValues", design);
      }
    }

    return new com.google.gson.GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(report);
  }

  // ============================================================================
  // CapacityConstrainedEquipment Implementation
  // ============================================================================

  /**
   * Initialize ejector capacity constraints.
   */
  private void initializeEjectorCapacityConstraints() {
    ejectorCapacityConstraints.clear();

    // Entrainment ratio constraint
    if (designEntrainmentRatio > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint erConstraint = new neqsim.process.equipment.capacity.CapacityConstraint(
          "entrainmentRatio", "-", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
      erConstraint.setDesignValue(designEntrainmentRatio);
      erConstraint.setDescription("Entrainment ratio (suction/motive mass ratio)");
      erConstraint.setValueSupplier(this::getEntrainmentRatio);
      ejectorCapacityConstraints.put("entrainmentRatio", erConstraint);
    }

    // Compression ratio constraint
    if (designCompressionRatio > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint crConstraint = new neqsim.process.equipment.capacity.CapacityConstraint(
          "compressionRatio", "-", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
      crConstraint.setDesignValue(designCompressionRatio);
      crConstraint.setDescription("Compression ratio (discharge/suction pressure)");
      crConstraint.setValueSupplier(() -> {
        if (suctionStream != null && suctionStream.getPressure("bara") > 0) {
          return dischargePressure / suctionStream.getPressure("bara");
        }
        return 0.0;
      });
      ejectorCapacityConstraints.put("compressionRatio", crConstraint);
    }

    // Critical back pressure constraint (HARD limit)
    if (maxCriticalBackPressure > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint bpConstraint = new neqsim.process.equipment.capacity.CapacityConstraint(
          "criticalBackPressure", "bara", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.HARD);
      bpConstraint.setDesignValue(maxCriticalBackPressure);
      bpConstraint.setMaxValue(maxCriticalBackPressure);
      bpConstraint.setDescription("Maximum allowable back pressure");
      bpConstraint.setValueSupplier(() -> dischargePressure);
      ejectorCapacityConstraints.put("criticalBackPressure", bpConstraint);
    }

    // Motive flow rate constraint
    if (designMotiveFlowRate > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint mfConstraint = new neqsim.process.equipment.capacity.CapacityConstraint(
          "motiveFlowRate", "kg/s", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
      mfConstraint.setDesignValue(designMotiveFlowRate);
      mfConstraint.setDescription("Motive stream flow rate");
      mfConstraint.setValueSupplier(() -> motiveStream != null ? motiveStream.getFlowRate("kg/sec") : 0.0);
      ejectorCapacityConstraints.put("motiveFlowRate", mfConstraint);
    }
  }

  /**
   * Sets the design entrainment ratio.
   *
   * @param ratio design entrainment ratio (suction/motive mass ratio)
   */
  public void setDesignEntrainmentRatio(double ratio) {
    this.designEntrainmentRatio = ratio;
    initializeEjectorCapacityConstraints();
  }

  /**
   * Gets the design entrainment ratio.
   *
   * @return design entrainment ratio
   */
  public double getDesignEntrainmentRatio() {
    return designEntrainmentRatio;
  }

  /**
   * Sets the design compression ratio.
   *
   * @param ratio design compression ratio (discharge/suction pressure)
   */
  public void setDesignCompressionRatio(double ratio) {
    this.designCompressionRatio = ratio;
    initializeEjectorCapacityConstraints();
  }

  /**
   * Gets the design compression ratio.
   *
   * @return design compression ratio
   */
  public double getDesignCompressionRatio() {
    return designCompressionRatio;
  }

  /**
   * Sets the maximum critical back pressure.
   *
   * @param pressure maximum critical back pressure in bara
   */
  public void setMaxCriticalBackPressure(double pressure) {
    this.maxCriticalBackPressure = pressure;
    initializeEjectorCapacityConstraints();
  }

  /**
   * Gets the maximum critical back pressure.
   *
   * @return maximum critical back pressure in bara
   */
  public double getMaxCriticalBackPressure() {
    return maxCriticalBackPressure;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityAnalysisEnabled() {
    return ejectorCapacityAnalysisEnabled;
  }

  /** {@inheritDoc} */
  @Override
  public void setCapacityAnalysisEnabled(boolean enabled) {
    this.ejectorCapacityAnalysisEnabled = enabled;
  }

  /** {@inheritDoc} */
  @Override
  public java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> getCapacityConstraints() {
    return java.util.Collections.unmodifiableMap(ejectorCapacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.equipment.capacity.CapacityConstraint getBottleneckConstraint() {
    neqsim.process.equipment.capacity.CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : ejectorCapacityConstraints.values()) {
      if (constraint.isEnabled()) {
        double util = constraint.getUtilization();
        if (util > maxUtil) {
          maxUtil = util;
          bottleneck = constraint;
        }
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : ejectorCapacityConstraints.values()) {
      if (constraint.isEnabled() && constraint.getUtilization() > 1.0) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : ejectorCapacityConstraints.values()) {
      if (constraint.isEnabled()
          && constraint.getType() == neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.HARD
          && constraint.getUtilization() > 1.0) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : ejectorCapacityConstraints.values()) {
      if (constraint.isEnabled()) {
        maxUtil = Math.max(maxUtil, constraint.getUtilization());
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(neqsim.process.equipment.capacity.CapacityConstraint constraint) {
    ejectorCapacityConstraints.put(constraint.getName(), constraint);
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return ejectorCapacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    ejectorCapacityConstraints.clear();
  }
}
