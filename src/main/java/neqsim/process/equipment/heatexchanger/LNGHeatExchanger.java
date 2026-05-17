package neqsim.process.equipment.heatexchanger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * LNG cryogenic multi-stream heat exchanger model (plate-fin / brazed aluminium).
 *
 * <p>
 * Provides a comprehensive set of capabilities for MCHE design and analysis:
 * </p>
 * <ol>
 * <li><b>Rigorous H-T curves</b> &mdash; TP-flash at every zone boundary (P1).</li>
 * <li><b>Per-stream pressure drop</b> &mdash; linear or correlated P interpolation (P2).</li>
 * <li><b>Exergy analysis</b> &mdash; zone-by-zone entropy generation and eta-II (P3).</li>
 * <li><b>Adaptive zone refinement</b> &mdash; auto-refines near phase boundaries (P4).</li>
 * <li><b>Manglik-Bergles fin correlations</b> &mdash; offset-strip j/f factors (P5).</li>
 * <li><b>Two-phase pressure drop</b> &mdash; Lockhart-Martinelli separated flow (P6).</li>
 * <li><b>Dynamic cool-down transient</b> &mdash; lumped thermal mass model (P7).</li>
 * <li><b>Core sizing</b> &mdash; BAHX L x W x H from duty and fin geometry (P8).</li>
 * <li><b>Freeze-out detection</b> &mdash; CO2 and heavy HC solid risk per zone (P9).</li>
 * <li><b>Flow maldistribution</b> &mdash; MITA correction factor (P10).</li>
 * </ol>
 *
 * @author NeqSim
 * @version 4.0
 */
public class LNGHeatExchanger extends MultiStreamHeatExchanger2 {
  private static final long serialVersionUID = 1004;
  private static final Logger logger = LogManager.getLogger(LNGHeatExchanger.class);

  /** Default reference temperature for exergy calculations: 15 deg C (288.15 K). */
  private static final double DEFAULT_REFERENCE_TEMP_K = 288.15;

  /** CO2 triple point temperature in deg C (-56.6 deg C). */
  private static final double CO2_FREEZE_TEMP_C = -56.6;

  /** Mercury safe limit in ppb for aluminium BAHX. */
  private static final double MERCURY_SAFE_LIMIT_PPB = 10.0;

  // ═══════════════════════════════════════════════════════════════════
  // Inner data classes
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Plate-fin geometry for offset-strip, wavy, plain, or perforated fins.
   *
   * <p>
   * Used by the Manglik-Bergles correlations (P5) to compute heat-transfer (j) and friction (f)
   * factors and by core sizing (P8) to determine exchanger dimensions.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class FinGeometry implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Fin type. */
    private String type = "offset-strip";
    /** Fin height in metres. */
    private double finHeight = 0.006;
    /** Fin thickness in metres. */
    private double finThickness = 0.0003;
    /** Fin pitch (centre-to-centre spacing) in metres. */
    private double finPitch = 0.0016;
    /** Offset strip length in metres (for offset-strip fins). */
    private double stripLength = 0.003;
    /** Plate (parting sheet) thickness in metres. */
    private double plateThickness = 0.0016;
    /** Fin thermal conductivity in W/(m K) — aluminium default. */
    private double finConductivity = 170.0;

    /**
     * Default constructor with standard BAHX fin geometry.
     */
    public FinGeometry() {}

    /**
     * Construct with key dimensions.
     *
     * @param finHeight fin height in m
     * @param finPitch fin pitch in m
     * @param finThickness fin thickness in m
     * @param stripLength offset strip length in m
     */
    public FinGeometry(double finHeight, double finPitch, double finThickness, double stripLength) {
      this.finHeight = finHeight;
      this.finPitch = finPitch;
      this.finThickness = finThickness;
      this.stripLength = stripLength;
    }

    /**
     * Get the hydraulic diameter.
     *
     * @return hydraulic diameter in m
     */
    public double getHydraulicDiameter() {
      double s = finPitch - finThickness;
      double h = finHeight - finThickness;
      return 4.0 * s * h * stripLength
          / (2.0 * (s * stripLength + h * stripLength + finThickness * h * s / stripLength)
              + finThickness * s);
    }

    /**
     * Get the free-flow area per unit frontal area.
     *
     * @return sigma ratio (dimensionless)
     */
    public double getSigma() {
      double s = finPitch - finThickness;
      double h = finHeight - finThickness;
      return s * h / (finPitch * (finHeight + plateThickness));
    }

    /**
     * Get surface area density (m2/m3).
     *
     * @return beta in m2/m3
     */
    public double getBeta() {
      return 4.0 * getSigma() / getHydraulicDiameter();
    }

    // ── Getters and setters ──

    /** @return fin type string */
    public String getType() {
      return type;
    }

    /** @param type fin type */
    public void setType(String type) {
      this.type = type;
    }

    /** @return fin height in m */
    public double getFinHeight() {
      return finHeight;
    }

    /** @param finHeight fin height in m */
    public void setFinHeight(double finHeight) {
      this.finHeight = finHeight;
    }

    /** @return fin thickness in m */
    public double getFinThickness() {
      return finThickness;
    }

    /** @param finThickness fin thickness in m */
    public void setFinThickness(double finThickness) {
      this.finThickness = finThickness;
    }

    /** @return fin pitch in m */
    public double getFinPitch() {
      return finPitch;
    }

    /** @param finPitch fin pitch in m */
    public void setFinPitch(double finPitch) {
      this.finPitch = finPitch;
    }

    /** @return strip length in m */
    public double getStripLength() {
      return stripLength;
    }

    /** @param stripLength strip length in m */
    public void setStripLength(double stripLength) {
      this.stripLength = stripLength;
    }

    /** @return plate thickness in m */
    public double getPlateThickness() {
      return plateThickness;
    }

    /** @param plateThickness plate thickness in m */
    public void setPlateThickness(double plateThickness) {
      this.plateThickness = plateThickness;
    }

    /** @return fin thermal conductivity in W/(m K) */
    public double getFinConductivity() {
      return finConductivity;
    }

    /** @param finConductivity fin conductivity in W/(m K) */
    public void setFinConductivity(double finConductivity) {
      this.finConductivity = finConductivity;
    }
  }

  /**
   * BAHX core geometry for sizing results (P8).
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class CoreGeometry implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Core length in metres (flow direction). */
    private double length = 0.0;
    /** Core width in metres (perpendicular to flow, header direction). */
    private double width = 0.0;
    /** Core height (stack-up) in metres. */
    private double height = 0.0;
    /** Number of layers per stream. */
    private int numberOfLayers = 10;
    /** Core weight in kg. */
    private double weight = 0.0;

    /**
     * Default constructor.
     */
    public CoreGeometry() {}

    /** @return core length in m */
    public double getLength() {
      return length;
    }

    /** @param length core length in m */
    public void setLength(double length) {
      this.length = length;
    }

    /** @return core width in m */
    public double getWidth() {
      return width;
    }

    /** @param width core width in m */
    public void setWidth(double width) {
      this.width = width;
    }

    /** @return core height in m */
    public double getHeight() {
      return height;
    }

    /** @param height core height in m */
    public void setHeight(double height) {
      this.height = height;
    }

    /** @return number of layers */
    public int getNumberOfLayers() {
      return numberOfLayers;
    }

    /** @param numberOfLayers number of layers */
    public void setNumberOfLayers(int numberOfLayers) {
      this.numberOfLayers = numberOfLayers;
    }

    /** @return weight in kg */
    public double getWeight() {
      return weight;
    }

    /** @param weight core weight in kg */
    public void setWeight(double weight) {
      this.weight = weight;
    }

    /** @return core volume in m3 */
    public double getVolume() {
      return length * width * height;
    }
  }

  /**
   * Snapshot of a single time step in a cool-down transient (P7).
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class TransientPoint implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Time in hours. */
    public final double timeHours;
    /** Average metal temperature in deg C. */
    public final double metalTempC;
    /** Fluid outlet temperature in deg C. */
    public final double fluidOutTempC;
    /** Instantaneous duty in kW. */
    public final double dutyKW;

    /**
     * Construct a transient data point.
     *
     * @param timeHours time in hours
     * @param metalTempC average metal temperature in deg C
     * @param fluidOutTempC fluid outlet temperature in deg C
     * @param dutyKW instantaneous duty in kW
     */
    public TransientPoint(double timeHours, double metalTempC, double fluidOutTempC,
        double dutyKW) {
      this.timeHours = timeHours;
      this.metalTempC = metalTempC;
      this.fluidOutTempC = fluidOutTempC;
      this.dutyKW = dutyKW;
    }
  }

  // ── Configuration fields ─────────────────────────────────────────────

  /** Number of axial zones for discretisation. */
  private int numberOfZones = 20;

  /** Exchanger type description. */
  private String exchangerType = "BAHX";

  /** Reference temperature for exergy calculations (K). */
  private double referenceTemperatureK = DEFAULT_REFERENCE_TEMP_K;

  /** Whether to enable adaptive zone refinement (P4). */
  private boolean adaptiveRefinement = false;

  /** Maximum zones after adaptive refinement (P4). */
  private int maxAdaptiveZones = 100;

  /** Enthalpy gradient threshold for adaptive refinement (P4). */
  private double adaptiveThresholdFactor = 2.0;

  /** Flow maldistribution factor applied to MITA (P10). 1.0 = ideal. */
  private double flowMaldistributionFactor = 1.0;

  /** Maximum allowable thermal gradient in deg C per meter (thermal stress). */
  private double maxAllowableThermalGradient = 5.0;

  /** Fin geometry per stream (P5), null entries use no fin correlation. */
  private List<FinGeometry> streamFinGeometry = new ArrayList<>();

  /** Core geometry for sizing (P8). */
  private CoreGeometry coreGeometry = new CoreGeometry();

  /** Core thermal mass in kJ/K for transient model (P7). */
  private double coreThermalMassKJK = 0.0;

  // ── Stream tracking ──────────────────────────────────────────────────

  /** Whether each registered stream is hot (true) or cold (false). */
  private List<Boolean> registeredIsHot = new ArrayList<>();

  /** Pressure drop per registered stream (bar). Zero means isobaric. */
  private List<Double> streamPressureDrops = new ArrayList<>();

  /** Manual hot/cold hints for pending streams (before registration). */
  private List<Boolean> pendingIsHot = new ArrayList<>();

  /** Number of registered streams (tracked because parent has no accessor). */
  private int streamCount = 0;

  /** Pending streams from addInStream() or list constructor. */
  private transient List<StreamInterface> pendingStreams = new ArrayList<>();

  // ── Result fields ────────────────────────────────────────────────────

  /** Minimum internal temperature approach across all zones (deg C). */
  private double minimumInternalTemperatureApproach = Double.MAX_VALUE;

  /** Zone index (0-based from cold end) where MITA occurs. */
  private int mitaZoneIndex = -1;

  /** Per-zone UA values (W/K). */
  private double[] uaPerZone;

  /** Per-zone minimum temperature approach (deg C). */
  private double[] mitaPerZone;

  /** Hot composite curve [nPoints][2]: col 0 = cumulative duty (kW), col 1 = temp (deg C). */
  private double[][] hotCompositeCurve;

  /** Cold composite curve [nPoints][2]: col 0 = cumulative duty (kW), col 1 = temp (deg C). */
  private double[][] coldCompositeCurve;

  /** Per-zone exergy destruction (kW). */
  private double[] exergyDestructionPerZone;

  /** Total exergy destruction across all zones (kW). */
  private double totalExergyDestruction = 0.0;

  /** Second-law (exergetic) efficiency, dimensionless 0&ndash;1. */
  private double secondLawEfficiency = 0.0;

  /** Per-zone CO2 freeze-out risk flag (P9). */
  private boolean[] freezeOutRiskPerZone;

  /** Per-zone temperature in deg C for freeze-out assessment (P9). */
  private double[] zoneTempProfileHotC;

  /** Per-zone temperature in deg C for freeze-out assessment - cold side (P9). */
  private double[] zoneTempProfileColdC;

  /** Per-zone thermal gradient in deg C per metre (thermal stress). */
  private double[] thermalGradientPerZone;

  /** Whether any thermal-stress warning was triggered. */
  private boolean thermalStressWarning = false;

  /** Per-stream detailed pressure drop from correlations in bar (P5/P6). */
  private double[] computedStreamDP;

  /** Per-stream j-factor results (P5). */
  private double[] streamJFactor;

  /** Per-stream f-factor results (P5). */
  private double[] streamFFactor;

  /** Cool-down transient results (P7). */
  private List<TransientPoint> transientResults = new ArrayList<>();

  /** Mercury risk assessment result. */
  private boolean mercuryRiskPresent = false;

  /** Mercury risk detail message. */
  private String mercuryRiskMessage = "";

  // ════════════════════════════════════════════════════════════════════
  // Constructors
  // ════════════════════════════════════════════════════════════════════

  /**
   * Constructor for LNGHeatExchanger.
   *
   * @param name name of the heat exchanger
   */
  public LNGHeatExchanger(String name) {
    super(name);
  }

  /**
   * Constructor for LNGHeatExchanger with initial streams.
   *
   * <p>
   * Streams are auto-classified as hot or cold based on inlet temperature when {@link #run(UUID)}
   * is called. The hotter stream(s) become hot and the colder one(s) become cold.
   * </p>
   *
   * @param name name of the heat exchanger
   * @param inStreams list of inlet streams
   */
  public LNGHeatExchanger(String name, List<StreamInterface> inStreams) {
    super(name);
    if (pendingStreams == null) {
      pendingStreams = new ArrayList<>();
    }
    for (StreamInterface s : inStreams) {
      pendingStreams.add(s);
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Stream management
  // ════════════════════════════════════════════════════════════════════

  /**
   * {@inheritDoc}
   *
   * <p>
   * Stores the stream for deferred registration. The stream will be auto-classified as hot or cold
   * based on its inlet temperature when {@link #run(UUID)} is called.
   * </p>
   */
  @Override
  public void addInStream(StreamInterface inStream) {
    if (pendingStreams == null) {
      pendingStreams = new ArrayList<>();
    }
    pendingStreams.add(inStream);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Also tracks the stream type and initialises pressure drop to zero.
   * </p>
   */
  @Override
  public void addInStreamMSHE(StreamInterface inStream, String streamType, Double outletTemp) {
    super.addInStreamMSHE(inStream, streamType, outletTemp);
    registeredIsHot.add("hot".equals(streamType));
    streamPressureDrops.add(0.0);
    streamCount++;
  }

  /**
   * Classify a pending stream as hot (being cooled) or cold (being heated).
   *
   * <p>
   * Must be called after {@link #addInStream(StreamInterface)} and before {@link #run(UUID)}. If
   * not called, the exchanger will auto-classify based on inlet temperatures.
   * </p>
   *
   * @param streamIndex 0-based index in the pending stream list
   * @param isHot true if the stream is hot (will be cooled)
   */
  public void setStreamIsHot(int streamIndex, boolean isHot) {
    while (pendingIsHot.size() <= streamIndex) {
      pendingIsHot.add(null);
    }
    pendingIsHot.set(streamIndex, isHot);
  }

  // ════════════════════════════════════════════════════════════════════
  // Configuration setters / getters
  // ════════════════════════════════════════════════════════════════════

  /**
   * Set the number of axial zones for discretisation.
   *
   * @param zones number of zones (must be &gt; 0)
   */
  public void setNumberOfZones(int zones) {
    if (zones < 1) {
      throw new IllegalArgumentException("numberOfZones must be positive, got " + zones);
    }
    this.numberOfZones = zones;
  }

  /**
   * Get the number of axial zones.
   *
   * @return number of zones
   */
  public int getNumberOfZones() {
    return numberOfZones;
  }

  /**
   * Set the exchanger type description.
   *
   * @param type exchanger type (e.g. "BAHX", "PCHE", "CWHE")
   */
  public void setExchangerType(String type) {
    this.exchangerType = type;
  }

  /**
   * Get the exchanger type description.
   *
   * @return exchanger type string
   */
  public String getExchangerType() {
    return exchangerType;
  }

  /**
   * Set the pressure drop for a specific stream (Priority 2).
   *
   * <p>
   * Pressure is linearly interpolated from inlet to outlet during the rigorous zone analysis. This
   * shifts saturation temperatures and affects pinch location, typically by 1&ndash;3 deg C for LNG
   * exchangers.
   * </p>
   *
   * @param streamIndex 0-based index of the registered stream
   * @param deltaPBar pressure drop in bar (positive value)
   */
  public void setStreamPressureDrop(int streamIndex, double deltaPBar) {
    while (streamPressureDrops.size() <= streamIndex) {
      streamPressureDrops.add(0.0);
    }
    streamPressureDrops.set(streamIndex, deltaPBar);
  }

  /**
   * Get the pressure drop for a specific stream.
   *
   * @param streamIndex 0-based index
   * @return pressure drop in bar
   */
  public double getStreamPressureDrop(int streamIndex) {
    if (streamIndex < 0 || streamIndex >= streamPressureDrops.size()) {
      return 0.0;
    }
    return streamPressureDrops.get(streamIndex);
  }

  /**
   * Set the reference (dead-state) temperature for exergy calculations.
   *
   * @param tempC reference temperature in degrees Celsius (default 15 deg C)
   */
  public void setReferenceTemperature(double tempC) {
    this.referenceTemperatureK = tempC + 273.15;
  }

  /**
   * Get the reference temperature for exergy calculations.
   *
   * @return reference temperature in degrees Celsius
   */
  public double getReferenceTemperature() {
    return referenceTemperatureK - 273.15;
  }

  /**
   * Enable or disable adaptive zone refinement near phase boundaries (P4).
   *
   * <p>
   * When enabled, zones where the enthalpy gradient changes by more than
   * {@link #setAdaptiveThresholdFactor(double)} times the average are automatically subdivided.
   * </p>
   *
   * @param enabled true to enable adaptive refinement
   */
  public void setAdaptiveRefinement(boolean enabled) {
    this.adaptiveRefinement = enabled;
  }

  /**
   * Get whether adaptive zone refinement is enabled.
   *
   * @return true if adaptive refinement is enabled
   */
  public boolean getAdaptiveRefinement() {
    return adaptiveRefinement;
  }

  /**
   * Set the maximum number of zones after adaptive refinement (P4).
   *
   * @param max maximum zone count (must be greater than numberOfZones)
   */
  public void setMaxAdaptiveZones(int max) {
    this.maxAdaptiveZones = max;
  }

  /**
   * Get the maximum number of zones after adaptive refinement.
   *
   * @return maximum adaptive zone count
   */
  public int getMaxAdaptiveZones() {
    return maxAdaptiveZones;
  }

  /**
   * Set the threshold factor for adaptive zone refinement (P4).
   *
   * <p>
   * A zone is refined when its enthalpy gradient exceeds this factor times the average gradient.
   * Default is 2.0.
   * </p>
   *
   * @param factor threshold factor (must be &gt; 1.0)
   */
  public void setAdaptiveThresholdFactor(double factor) {
    this.adaptiveThresholdFactor = factor;
  }

  /**
   * Get the adaptive threshold factor.
   *
   * @return threshold factor
   */
  public double getAdaptiveThresholdFactor() {
    return adaptiveThresholdFactor;
  }

  /**
   * Set the flow maldistribution correction factor (P10).
   *
   * <p>
   * Applies a penalty factor to the effective UA per zone and adjusts the computed MITA. A value of
   * 1.0 means ideal (no maldistribution). Typical BAHX maldistribution factors range from 0.85 to
   * 0.95.
   * </p>
   *
   * @param factor correction factor, 0 &lt; factor &lt;= 1.0
   */
  public void setFlowMaldistributionFactor(double factor) {
    this.flowMaldistributionFactor = factor;
  }

  /**
   * Get the flow maldistribution correction factor.
   *
   * @return correction factor (1.0 = ideal)
   */
  public double getFlowMaldistributionFactor() {
    return flowMaldistributionFactor;
  }

  /**
   * Set the maximum allowable thermal gradient for stress assessment.
   *
   * <p>
   * Typical limit for aluminium BAHX is 5 deg C/m per API 662 Part II guidelines. The assessment
   * compares the actual zone-by-zone gradient against this limit and flags warnings.
   * </p>
   *
   * @param gradientCPerM maximum gradient in deg C per metre of core length
   */
  public void setMaxAllowableThermalGradient(double gradientCPerM) {
    this.maxAllowableThermalGradient = gradientCPerM;
  }

  /**
   * Get the maximum allowable thermal gradient.
   *
   * @return limit in deg C per metre
   */
  public double getMaxAllowableThermalGradient() {
    return maxAllowableThermalGradient;
  }

  /**
   * Set the fin geometry for a specific stream (P5).
   *
   * <p>
   * When fin geometry is set, the Manglik-Bergles correlations for offset-strip fins are used to
   * compute heat-transfer and friction factors. These are also used by core sizing (P8).
   * </p>
   *
   * @param streamIndex 0-based stream index
   * @param fin fin geometry object
   */
  public void setStreamFinGeometry(int streamIndex, FinGeometry fin) {
    while (streamFinGeometry.size() <= streamIndex) {
      streamFinGeometry.add(null);
    }
    streamFinGeometry.set(streamIndex, fin);
  }

  /**
   * Get the fin geometry for a specific stream.
   *
   * @param streamIndex 0-based stream index
   * @return fin geometry, or null if not set
   */
  public FinGeometry getStreamFinGeometry(int streamIndex) {
    if (streamIndex < 0 || streamIndex >= streamFinGeometry.size()) {
      return null;
    }
    return streamFinGeometry.get(streamIndex);
  }

  /**
   * Set the core geometry for sizing and transient analysis (P8, P7).
   *
   * @param geom core geometry object
   */
  public void setCoreGeometry(CoreGeometry geom) {
    this.coreGeometry = geom;
  }

  /**
   * Get the core geometry (may be populated by {@link #sizeCore()}).
   *
   * @return core geometry object
   */
  public CoreGeometry getCoreGeometry() {
    return coreGeometry;
  }

  /**
   * Set the core thermal mass for transient cool-down analysis (P7).
   *
   * <p>
   * The thermal mass is the product of core metal mass and specific heat capacity (m * Cp). Typical
   * aluminium BAHX: ~2700 kg/m3 * 0.9 kJ/(kg K) = 2430 kJ/(m3 K).
   * </p>
   *
   * @param thermalMassKJK core thermal mass in kJ/K
   */
  public void setCoreThermalMass(double thermalMassKJK) {
    this.coreThermalMassKJK = thermalMassKJK;
  }

  /**
   * Get the core thermal mass.
   *
   * @return thermal mass in kJ/K
   */
  public double getCoreThermalMass() {
    return coreThermalMassKJK;
  }

  // ════════════════════════════════════════════════════════════════════
  // Result getters
  // ════════════════════════════════════════════════════════════════════

  /**
   * Get the minimum internal temperature approach (MITA) across all zones.
   *
   * @return MITA in degrees (same value in K and C for a temperature difference)
   */
  public double getMITA() {
    return minimumInternalTemperatureApproach;
  }

  /**
   * Get the MITA in the specified unit.
   *
   * @param unit temperature unit ("K", "C") &mdash; delta-T is identical in both
   * @return MITA value
   */
  public double getMITA(String unit) {
    return minimumInternalTemperatureApproach;
  }

  /**
   * Get the zone index (0-based from cold end) where MITA occurs.
   *
   * @return zone index, or -1 if not yet computed
   */
  public int getMITAZoneIndex() {
    return mitaZoneIndex;
  }

  /**
   * Get the per-zone UA values.
   *
   * @return array of UA values (W/K), length = numberOfZones
   */
  public double[] getUAPerZone() {
    return uaPerZone != null ? uaPerZone.clone() : new double[0];
  }

  /**
   * Get the per-zone MITA values.
   *
   * @return array of temperature approaches (deg C), length = numberOfZones
   */
  public double[] getMITAPerZone() {
    return mitaPerZone != null ? mitaPerZone.clone() : new double[0];
  }

  /**
   * Get the hot composite curve data (from rigorous zone-by-zone flash).
   *
   * @return 2D array [numberOfZones+1][2] where col 0 = cumulative duty (kW) from cold end and col
   *         1 = temperature (deg C)
   */
  public double[][] getHotCompositeCurve() {
    return hotCompositeCurve;
  }

  /**
   * Get the cold composite curve data (from rigorous zone-by-zone flash).
   *
   * @return 2D array [numberOfZones+1][2] where col 0 = cumulative duty (kW) from cold end and col
   *         1 = temperature (deg C)
   */
  public double[][] getColdCompositeCurve() {
    return coldCompositeCurve;
  }

  /**
   * Get the per-zone exergy destruction (Priority 3).
   *
   * @return array of exergy destruction values (kW), length = numberOfZones
   */
  public double[] getExergyDestructionPerZone() {
    return exergyDestructionPerZone != null ? exergyDestructionPerZone.clone() : new double[0];
  }

  /**
   * Get the total exergy destruction across all zones.
   *
   * @return total exergy destruction in kW
   */
  public double getTotalExergyDestruction() {
    return totalExergyDestruction;
  }

  /**
   * Get the second-law (exergetic) efficiency.
   *
   * <p>
   * Defined as the ratio of exergy gained by cold streams to exergy released by hot streams:
   * </p>
   *
   * <p>
   * {@code eta_II = (exergy_gained_cold) / (exergy_released_hot)}
   * </p>
   *
   * @return second-law efficiency, dimensionless (0 to 1). Typical LNG MCHE: 0.85&ndash;0.95.
   */
  public double getSecondLawEfficiency() {
    return secondLawEfficiency;
  }

  /**
   * Get the per-zone CO2 freeze-out risk flags (P9).
   *
   * @return boolean array; true if freeze-out risk exists in that zone
   */
  public boolean[] getFreezeOutRiskPerZone() {
    return freezeOutRiskPerZone != null ? freezeOutRiskPerZone.clone() : new boolean[0];
  }

  /**
   * Check if any zone has a freeze-out risk.
   *
   * @return true if at least one zone has freeze-out risk
   */
  public boolean hasFreezeOutRisk() {
    if (freezeOutRiskPerZone == null) {
      return false;
    }
    for (boolean risk : freezeOutRiskPerZone) {
      if (risk) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get the per-zone thermal gradient in deg C per metre of core length.
   *
   * @return array of thermal gradients (deg C/m), length = numberOfZones
   */
  public double[] getThermalGradientPerZone() {
    return thermalGradientPerZone != null ? thermalGradientPerZone.clone() : new double[0];
  }

  /**
   * Check if any zone exceeds the allowable thermal gradient.
   *
   * @return true if a thermal stress warning was triggered
   */
  public boolean hasThermalStressWarning() {
    return thermalStressWarning;
  }

  /**
   * Get the computed detailed pressure drop per stream from correlations (P5/P6).
   *
   * <p>
   * Only populated when fin geometry is set for a stream. Returns the sum of single-phase
   * (Manglik-Bergles) and two-phase (Lockhart-Martinelli) contributions.
   * </p>
   *
   * @return array of computed pressure drops (bar), one per stream
   */
  public double[] getComputedStreamDP() {
    return computedStreamDP != null ? computedStreamDP.clone() : new double[0];
  }

  /**
   * Get the average Colburn j-factor per stream from Manglik-Bergles correlation (P5).
   *
   * @return array of j-factors, one per stream (0.0 if fin geometry not set)
   */
  public double[] getStreamJFactor() {
    return streamJFactor != null ? streamJFactor.clone() : new double[0];
  }

  /**
   * Get the average Fanning friction factor per stream from Manglik-Bergles (P5).
   *
   * @return array of f-factors, one per stream (0.0 if fin geometry not set)
   */
  public double[] getStreamFFactor() {
    return streamFFactor != null ? streamFFactor.clone() : new double[0];
  }

  /**
   * Get the cool-down transient results (P7).
   *
   * @return list of transient data points (time, metal temp, fluid temp, duty)
   */
  public List<TransientPoint> getTransientResults() {
    return new ArrayList<>(transientResults);
  }

  /**
   * Check if a mercury risk was flagged for aluminium BAHX.
   *
   * @return true if mercury concentration exceeds the safe limit
   */
  public boolean isMercuryRiskPresent() {
    return mercuryRiskPresent;
  }

  /**
   * Get the mercury risk assessment message.
   *
   * @return message string (empty if no risk)
   */
  public String getMercuryRiskMessage() {
    return mercuryRiskMessage;
  }

  /**
   * Generate a complete design feasibility report for this BAHX.
   *
   * <p>
   * The report includes mechanical design (ASME VIII Div.1, ALPEMA), cost estimation (CAPEX, OPEX),
   * supplier matching, and feasibility checks (temperature, pressure, mercury, freeze-out, thermal
   * stress, MITA, exergy efficiency).
   * </p>
   *
   * @return the feasibility report with verdict, issues, suppliers, cost, and mechanical design
   */
  public neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerDesignFeasibilityReport generateFeasibilityReport() {
    neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerDesignFeasibilityReport report =
        new neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerDesignFeasibilityReport(
            this);
    report.generateReport();
    return report;
  }

  /**
   * Get the hot-side zone temperature profile in deg C (P9).
   *
   * @return array of temperatures per zone boundary, from cold end to hot end
   */
  public double[] getZoneTempProfileHotC() {
    return zoneTempProfileHotC != null ? zoneTempProfileHotC.clone() : new double[0];
  }

  /**
   * Get the cold-side zone temperature profile in deg C (P9).
   *
   * @return array of temperatures per zone boundary, from cold end to hot end
   */
  public double[] getZoneTempProfileColdC() {
    return zoneTempProfileColdC != null ? zoneTempProfileColdC.clone() : new double[0];
  }

  // ════════════════════════════════════════════════════════════════════
  // Run
  // ════════════════════════════════════════════════════════════════════

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (pendingStreams != null && !pendingStreams.isEmpty()) {
      registerPendingStreams();
    }

    // Parent's NR solver determines outlet temperatures
    super.run(id);

    // Rigorous zone-by-zone flash for composite curves, MITA, UA, and exergy
    computeRigorousZoneData();
  }

  // ════════════════════════════════════════════════════════════════════
  // Internal methods
  // ════════════════════════════════════════════════════════════════════

  /**
   * Register pending streams with the parent, auto-classifying as hot or cold.
   */
  private void registerPendingStreams() {
    while (pendingIsHot.size() < pendingStreams.size()) {
      pendingIsHot.add(null);
    }

    List<Double> temps = new ArrayList<>();
    for (StreamInterface s : pendingStreams) {
      temps.add(s.getFluid().getTemperature("C"));
    }

    double avgTemp = 0.0;
    for (double t : temps) {
      avgTemp += t;
    }
    avgTemp /= temps.size();

    for (int i = 0; i < pendingStreams.size(); i++) {
      String type;
      if (pendingIsHot.get(i) != null) {
        type = pendingIsHot.get(i) ? "hot" : "cold";
      } else {
        type = temps.get(i) >= avgTemp ? "hot" : "cold";
        if (pendingStreams.size() == 2 && Math.abs(temps.get(0) - temps.get(1)) < 0.01) {
          type = (i == 0) ? "hot" : "cold";
        }
      }
      addInStreamMSHE(pendingStreams.get(i), type, null);
    }
    pendingStreams.clear();
    pendingIsHot.clear();
  }

  /**
   * Rigorous zone-by-zone analysis with TP-flash at every zone boundary.
   *
   * <p>
   * This is the core method that distinguishes this class from the parent. For each stream, the
   * temperature range is divided into {@code numberOfZones} intervals and a full TP-flash is
   * performed at each boundary. This captures the non-linear enthalpy-temperature relationship
   * across phase transitions (methane condensation, MR evaporation), which is where the MITA pinch
   * typically occurs in LNG exchangers.
   * </p>
   *
   * <p>
   * Includes adaptive zone refinement (P4), freeze-out detection (P9), flow maldistribution (P10),
   * and thermal stress assessment.
   * </p>
   */
  private void computeRigorousZoneData() {
    if (streamCount < 2) {
      return;
    }

    // ── Step 0: Determine effective zone count (P4 adaptive refinement) ──
    int effectiveZones = numberOfZones;
    List<Double> zoneFractions = buildUniformFractions(numberOfZones);

    // First pass with uniform zones to detect phase boundaries
    if (adaptiveRefinement) {
      List<Double> refinedFractions = computeAdaptiveFractions(zoneFractions);
      if (refinedFractions.size() - 1 <= maxAdaptiveZones) {
        zoneFractions = refinedFractions;
        effectiveZones = zoneFractions.size() - 1;
      }
    }

    int nPoints = effectiveZones + 1;

    // ── Step 1: Rigorous TP-flash at each zone boundary ──────────────
    double[][] streamH = new double[streamCount][nPoints]; // kJ/kg
    double[][] streamS = new double[streamCount][nPoints]; // kJ/(kg·K)
    double[][] streamTempC = new double[streamCount][nPoints];
    double[][] streamVapFrac = new double[streamCount][nPoints]; // for 2-phase DP
    double[][] streamDensity = new double[streamCount][nPoints]; // kg/m3
    double[][] streamViscosity = new double[streamCount][nPoints]; // Pa·s
    double[] massFlowKgS = new double[streamCount];
    double[] inletTempC = new double[streamCount];
    double[] outletTempC = new double[streamCount];

    for (int i = 0; i < streamCount; i++) {
      StreamInterface inStr = getInStream(i);
      inletTempC[i] = getInTemperature(i);
      outletTempC[i] = getOutTemperature(i);
      double pIn = inStr.getPressure("bara");
      double deltaP = (i < streamPressureDrops.size()) ? streamPressureDrops.get(i) : 0.0;
      massFlowKgS[i] = inStr.getFlowRate("kg/sec");

      SystemInterface baseFluid = inStr.getFluid().clone();

      for (int z = 0; z < nPoints; z++) {
        double frac = zoneFractions.get(z);
        double temp = inletTempC[i] + frac * (outletTempC[i] - inletTempC[i]);
        double pres = pIn - frac * deltaP;
        if (pres < 0.01) {
          pres = 0.01;
        }

        SystemInterface zoneFluid = baseFluid.clone();
        zoneFluid.setTemperature(temp, "C");
        zoneFluid.setPressure(pres, "bara");

        ThermodynamicOperations ops = new ThermodynamicOperations(zoneFluid);
        ops.TPflash();
        zoneFluid.initThermoProperties();

        streamH[i][z] = zoneFluid.getEnthalpy("kJ/kg");
        streamS[i][z] = zoneFluid.getEntropy("kJ/kgK");
        streamTempC[i][z] = temp;

        // Capture phase info for two-phase DP (P6) and freeze-out (P9)
        int nPhases = zoneFluid.getNumberOfPhases();
        if (nPhases > 1 && zoneFluid.hasPhaseType("gas")) {
          int gasIdx = zoneFluid.getPhaseNumberOfPhase("gas");
          streamVapFrac[i][z] = zoneFluid.getPhase(gasIdx).getBeta();
        } else if (nPhases == 1 && zoneFluid.hasPhaseType("gas")) {
          streamVapFrac[i][z] = 1.0;
        } else {
          streamVapFrac[i][z] = 0.0;
        }
        streamDensity[i][z] = zoneFluid.getDensity("kg/m3");
        if (nPhases > 0) {
          streamViscosity[i][z] = zoneFluid.getPhase(0).getViscosity("kg/msec");
        }
      }
    }

    // ── Step 2: Build composite curves (cold end to hot end) ─────────
    hotCompositeCurve = new double[nPoints][2];
    coldCompositeCurve = new double[nPoints][2];
    uaPerZone = new double[effectiveZones];
    mitaPerZone = new double[effectiveZones];
    exergyDestructionPerZone = new double[effectiveZones];
    freezeOutRiskPerZone = new boolean[effectiveZones];
    thermalGradientPerZone = new double[effectiveZones];
    zoneTempProfileHotC = new double[nPoints];
    zoneTempProfileColdC = new double[nPoints];
    minimumInternalTemperatureApproach = Double.MAX_VALUE;
    mitaZoneIndex = -1;
    totalExergyDestruction = 0.0;
    thermalStressWarning = false;

    double cumHotDuty = 0.0;
    double cumColdDuty = 0.0;

    // Cold end (p=0)
    hotCompositeCurve[0][0] = 0.0;
    hotCompositeCurve[0][1] =
        compositeTempAdaptive(streamTempC, massFlowKgS, true, effectiveZones, 0);
    coldCompositeCurve[0][0] = 0.0;
    coldCompositeCurve[0][1] =
        compositeTempAdaptive(streamTempC, massFlowKgS, false, effectiveZones, 0);
    zoneTempProfileHotC[0] = hotCompositeCurve[0][1];
    zoneTempProfileColdC[0] = coldCompositeCurve[0][1];

    for (int p = 1; p <= effectiveZones; p++) {
      int hotZPrev = effectiveZones - p + 1;
      int hotZCurr = effectiveZones - p;
      int coldZPrev = p - 1;
      int coldZCurr = p;

      double hotZoneDuty = 0.0;
      double coldZoneDuty = 0.0;
      for (int i = 0; i < streamCount; i++) {
        if (registeredIsHot.get(i)) {
          hotZoneDuty += massFlowKgS[i] * (streamH[i][hotZCurr] - streamH[i][hotZPrev]);
        } else {
          coldZoneDuty += massFlowKgS[i] * (streamH[i][coldZCurr] - streamH[i][coldZPrev]);
        }
      }

      cumHotDuty += hotZoneDuty;
      cumColdDuty += coldZoneDuty;

      double hotTempP = compositeTempAdaptive(streamTempC, massFlowKgS, true, effectiveZones, p);
      double coldTempP = compositeTempAdaptive(streamTempC, massFlowKgS, false, effectiveZones, p);

      hotCompositeCurve[p][0] = cumHotDuty;
      hotCompositeCurve[p][1] = hotTempP;
      coldCompositeCurve[p][0] = cumColdDuty;
      coldCompositeCurve[p][1] = coldTempP;
      zoneTempProfileHotC[p] = hotTempP;
      zoneTempProfileColdC[p] = coldTempP;
    }

    // ── Step 3: Per-zone MITA, UA, exergy, freeze-out, thermal stress ──
    for (int p = 0; p < effectiveZones; p++) {
      double hotT1 = hotCompositeCurve[p][1];
      double coldT1 = coldCompositeCurve[p][1];
      double hotT2 = hotCompositeCurve[p + 1][1];
      double coldT2 = coldCompositeCurve[p + 1][1];

      double dT1 = hotT1 - coldT1;
      double dT2 = hotT2 - coldT2;
      double minApproach = Math.min(dT1, dT2);

      // P10: Flow maldistribution correction on MITA
      if (flowMaldistributionFactor < 1.0) {
        minApproach = minApproach * flowMaldistributionFactor;
      }
      mitaPerZone[p] = minApproach;

      if (minApproach < minimumInternalTemperatureApproach) {
        minimumInternalTemperatureApproach = minApproach;
        mitaZoneIndex = p;
      }

      // UA per zone via LMTD
      double zoneDuty = hotCompositeCurve[p + 1][0] - hotCompositeCurve[p][0];
      if (dT1 > 0.01 && dT2 > 0.01) {
        double lmtd;
        if (Math.abs(dT1 - dT2) < 0.01) {
          lmtd = (dT1 + dT2) / 2.0;
        } else {
          lmtd = (dT1 - dT2) / Math.log(dT1 / dT2);
        }
        double uaRaw = Math.abs(zoneDuty) * 1000.0 / lmtd;
        // P10: Maldistribution penalty on UA
        uaPerZone[p] = uaRaw * flowMaldistributionFactor;
      }

      // Exergy destruction
      double entropyGen = 0.0;
      for (int i = 0; i < streamCount; i++) {
        int zIn;
        int zOut;
        if (registeredIsHot.get(i)) {
          zIn = effectiveZones - p - 1;
          zOut = effectiveZones - p;
        } else {
          zIn = p;
          zOut = p + 1;
        }
        entropyGen += massFlowKgS[i] * (streamS[i][zOut] - streamS[i][zIn]);
      }
      exergyDestructionPerZone[p] = referenceTemperatureK * Math.abs(entropyGen);
      totalExergyDestruction += exergyDestructionPerZone[p];

      // P9: Freeze-out detection
      double minZoneTemp = Math.min(Math.min(hotT1, hotT2), Math.min(coldT1, coldT2));
      freezeOutRiskPerZone[p] = minZoneTemp < CO2_FREEZE_TEMP_C;

      // Thermal stress: gradient = |dT| / zone_length
      if (coreGeometry != null && coreGeometry.getLength() > 0.0) {
        double zoneLength = coreGeometry.getLength() / effectiveZones;
        double maxDT = Math.max(Math.abs(hotT2 - hotT1), Math.abs(coldT2 - coldT1));
        thermalGradientPerZone[p] = maxDT / zoneLength;
        if (thermalGradientPerZone[p] > maxAllowableThermalGradient) {
          thermalStressWarning = true;
        }
      }
    }

    // ── Step 4: Second-law efficiency ────────────────────────────────
    double exergyInput = 0.0;
    double exergyOutput = 0.0;
    for (int i = 0; i < streamCount; i++) {
      double dH = streamH[i][nPoints - 1] - streamH[i][0];
      double dS = streamS[i][nPoints - 1] - streamS[i][0];
      double exergyChange = massFlowKgS[i] * (dH - referenceTemperatureK * dS);

      if (exergyChange < 0.0) {
        exergyInput += Math.abs(exergyChange);
      } else {
        exergyOutput += exergyChange;
      }
    }

    if (exergyInput > 1e-6) {
      secondLawEfficiency = exergyOutput / exergyInput;
    }

    // ── Step 5: Detailed DP correlations (P5/P6) ─────────────────────
    computeDetailedPressureDrop(streamDensity, streamViscosity, streamVapFrac, massFlowKgS,
        effectiveZones);

    logger.info(String.format(
        "LNG HX rigorous: zones=%d, MITA=%.2f C (zone %d), eta_II=%.1f%%, "
            + "exergy_dest=%.1f kW, freezeRisk=%b, thermalStress=%b",
        effectiveZones, minimumInternalTemperatureApproach, mitaZoneIndex,
        secondLawEfficiency * 100.0, totalExergyDestruction, hasFreezeOutRisk(),
        thermalStressWarning));
  }

  // ════════════════════════════════════════════════════════════════════
  // P4: Adaptive zone refinement
  // ════════════════════════════════════════════════════════════════════

  /**
   * Build uniform fractional positions from 0.0 to 1.0.
   *
   * @param zones number of zones
   * @return list of nPoints = zones+1 fraction values
   */
  private List<Double> buildUniformFractions(int zones) {
    List<Double> fracs = new ArrayList<>();
    for (int z = 0; z <= zones; z++) {
      fracs.add((double) z / zones);
    }
    return fracs;
  }

  /**
   * Refine zone fractions near phase boundaries where enthalpy gradient is steep (P4).
   *
   * <p>
   * Uses a first-pass enthalpy scan on the first stream to detect zones where |dH/dT| exceeds
   * {@link #adaptiveThresholdFactor} times the average gradient. Such zones are bisected to improve
   * resolution near phase transitions.
   * </p>
   *
   * @param uniformFracs initial uniform fraction list
   * @return refined fraction list with additional points near phase boundaries
   */
  private List<Double> computeAdaptiveFractions(List<Double> uniformFracs) {
    if (streamCount < 1) {
      return uniformFracs;
    }

    // Quick enthalpy scan on first stream
    StreamInterface refStream = getInStream(0);
    double tIn = getInTemperature(0);
    double tOut = getOutTemperature(0);
    double pIn = refStream.getPressure("bara");
    double dp = (streamPressureDrops.size() > 0) ? streamPressureDrops.get(0) : 0.0;
    SystemInterface baseFluid = refStream.getFluid().clone();

    int nUniform = uniformFracs.size();
    double[] hScan = new double[nUniform];
    for (int z = 0; z < nUniform; z++) {
      double frac = uniformFracs.get(z);
      double temp = tIn + frac * (tOut - tIn);
      double pres = pIn - frac * dp;
      if (pres < 0.01) {
        pres = 0.01;
      }
      SystemInterface zf = baseFluid.clone();
      zf.setTemperature(temp, "C");
      zf.setPressure(pres, "bara");
      ThermodynamicOperations ops = new ThermodynamicOperations(zf);
      ops.TPflash();
      zf.initThermoProperties();
      hScan[z] = zf.getEnthalpy("kJ/kg");
    }

    // Compute gradients
    double[] gradients = new double[nUniform - 1];
    double avgGradient = 0.0;
    for (int z = 0; z < nUniform - 1; z++) {
      gradients[z] = Math.abs(hScan[z + 1] - hScan[z]);
      avgGradient += gradients[z];
    }
    avgGradient /= (nUniform - 1);

    // Build refined fraction list by bisecting steep zones
    List<Double> refined = new ArrayList<>();
    refined.add(uniformFracs.get(0));
    for (int z = 0; z < nUniform - 1; z++) {
      if (gradients[z] > adaptiveThresholdFactor * avgGradient
          && refined.size() + (nUniform - z) < maxAdaptiveZones) {
        // Insert midpoint
        double mid = (uniformFracs.get(z) + uniformFracs.get(z + 1)) / 2.0;
        refined.add(mid);
      }
      refined.add(uniformFracs.get(z + 1));
    }
    return refined;
  }

  // ════════════════════════════════════════════════════════════════════
  // P5: Manglik-Bergles offset-strip fin correlations
  // ════════════════════════════════════════════════════════════════════

  /**
   * Compute Colburn j-factor and Fanning f-factor for offset-strip fins.
   *
   * <p>
   * Manglik and Bergles (1995) correlations valid for 120 &lt; Re &lt; 10,000. Parameters are
   * dimensionless ratios derived from fin geometry.
   * </p>
   *
   * @param re Reynolds number based on hydraulic diameter
   * @param fin fin geometry
   * @return double array [j, f] where j = Colburn factor, f = Fanning friction factor
   */
  private double[] manglikBerglesOSF(double re, FinGeometry fin) {
    if (re < 1.0) {
      re = 1.0;
    }
    double s = fin.getFinPitch() - fin.getFinThickness();
    double h = fin.getFinHeight() - fin.getFinThickness();
    double t = fin.getFinThickness();
    double ls = fin.getStripLength();
    double dh = fin.getHydraulicDiameter();

    // Dimensionless ratios
    double alpha = s / h;
    double delta = t / ls;
    double gamma = t / s;

    // j-factor correlation (Manglik & Bergles 1995, Eq. 1)
    double j = 0.6522 * Math.pow(re, -0.5403) * Math.pow(alpha, -0.1541) * Math.pow(delta, 0.1499)
        * Math.pow(gamma, -0.0678) * Math.pow(1.0 + 5.269e-5 * Math.pow(re, 1.340)
            * Math.pow(alpha, 0.504) * Math.pow(delta, 0.456) * Math.pow(gamma, -1.055), 0.1);

    // f-factor correlation (Manglik & Bergles 1995, Eq. 2)
    double f = 9.6243 * Math.pow(re, -0.7422) * Math.pow(alpha, -0.1856) * Math.pow(delta, 0.3053)
        * Math.pow(gamma, -0.2659) * Math.pow(1.0 + 7.669e-8 * Math.pow(re, 4.429)
            * Math.pow(alpha, 0.920) * Math.pow(delta, 3.767) * Math.pow(gamma, 0.236), 0.1);

    return new double[] {j, f};
  }

  // ════════════════════════════════════════════════════════════════════
  // P6: Two-phase pressure drop — Lockhart-Martinelli
  // ════════════════════════════════════════════════════════════════════

  /**
   * Compute two-phase pressure drop multiplier using the Lockhart-Martinelli separated flow model.
   *
   * <p>
   * The Martinelli parameter X_tt for turbulent-turbulent flow is computed from quality, density,
   * and viscosity ratios. The two-phase multiplier phi_L squared is then evaluated using the
   * Chisholm (1967) C-parameter correlation.
   * </p>
   *
   * @param x vapour quality (mass fraction), 0 to 1
   * @param rhoL liquid density in kg/m3
   * @param rhoG gas density in kg/m3
   * @param muL liquid viscosity in Pa s
   * @param muG gas viscosity in Pa s
   * @return two-phase multiplier phi_L squared (dimensionless, &gt;= 1.0)
   */
  private double lockhartMartinelliPhiL2(double x, double rhoL, double rhoG, double muL,
      double muG) {
    if (x <= 0.001 || x >= 0.999) {
      return 1.0; // effectively single-phase
    }
    if (rhoG < 0.01 || muG < 1e-10 || muL < 1e-10) {
      return 1.0;
    }

    // Martinelli parameter X_tt for turbulent-turbulent flow
    double xtt =
        Math.pow((1.0 - x) / x, 0.9) * Math.pow(rhoG / rhoL, 0.5) * Math.pow(muL / muG, 0.1);

    // Chisholm C-parameter: C = 20 for turbulent-turbulent
    double c = 20.0;
    double phiL2 = 1.0 + c / xtt + 1.0 / (xtt * xtt);
    return phiL2;
  }

  /**
   * Compute detailed pressure drop per stream using Manglik-Bergles (P5) and Lockhart-Martinelli
   * (P6) correlations.
   *
   * @param streamDensity density at each zone boundary [stream][point] in kg/m3
   * @param streamViscosity viscosity at each zone boundary [stream][point] in Pa s
   * @param streamVapFrac vapour fraction at each zone boundary [stream][point]
   * @param massFlowKgS mass flow rate per stream in kg/s
   * @param effectiveZones number of zones used
   */
  private void computeDetailedPressureDrop(double[][] streamDensity, double[][] streamViscosity,
      double[][] streamVapFrac, double[] massFlowKgS, int effectiveZones) {
    computedStreamDP = new double[streamCount];
    streamJFactor = new double[streamCount];
    streamFFactor = new double[streamCount];

    for (int i = 0; i < streamCount; i++) {
      FinGeometry fin = (i < streamFinGeometry.size()) ? streamFinGeometry.get(i) : null;
      if (fin == null) {
        continue; // No fin geometry set, skip correlation-based DP
      }

      double dh = fin.getHydraulicDiameter();
      double sigma = fin.getSigma();
      double totalDP = 0.0;
      double jSum = 0.0;
      double fSum = 0.0;
      int nPoints = effectiveZones + 1;

      // Assume core frontal area from core geometry if available, else 1 m2
      double aFrontal = 1.0;
      if (coreGeometry.getWidth() > 0 && coreGeometry.getHeight() > 0) {
        aFrontal = coreGeometry.getWidth() * coreGeometry.getHeight();
      }
      double aFreeFlow = sigma * aFrontal;
      double massVelocity = massFlowKgS[i] / aFreeFlow; // kg/(m2·s)

      for (int z = 0; z < effectiveZones; z++) {
        double rho = (streamDensity[i][z] + streamDensity[i][z + 1]) / 2.0;
        double mu = (streamViscosity[i][z] + streamViscosity[i][z + 1]) / 2.0;
        if (rho < 0.01 || mu < 1e-12) {
          continue;
        }

        double re = massVelocity * dh / mu;
        double[] jf = manglikBerglesOSF(re, fin);
        jSum += jf[0];
        fSum += jf[1];

        // Zone length
        double zoneLength =
            (coreGeometry.getLength() > 0) ? coreGeometry.getLength() / effectiveZones
                : 1.0 / effectiveZones;

        // Single-phase friction DP for this zone
        double dpZone = 2.0 * jf[1] * (zoneLength / dh) * massVelocity * massVelocity / rho;

        // Two-phase correction (P6)
        double x = (streamVapFrac[i][z] + streamVapFrac[i][z + 1]) / 2.0;
        if (x > 0.001 && x < 0.999) {
          // Need liquid and gas properties — use simplified split
          double rhoL = rho / (1.0 - x + 0.001);
          double rhoG = rho * 0.1; // approximation for LNG conditions
          double muL = mu;
          double muG = mu * 0.05;
          double phiL2 = lockhartMartinelliPhiL2(x, rhoL, rhoG, muL, muG);
          dpZone *= phiL2;
        }

        totalDP += dpZone;
      }

      computedStreamDP[i] = totalDP / 1e5; // Pa to bar
      streamJFactor[i] = jSum / effectiveZones;
      streamFFactor[i] = fSum / effectiveZones;
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // P7: Dynamic cool-down transient model
  // ════════════════════════════════════════════════════════════════════

  /**
   * Run a simplified cool-down transient analysis (P7).
   *
   * <p>
   * Uses a lumped thermal mass model: the core metal temperature decreases from ambient toward the
   * cold fluid temperature as the cooling utility flows through the exchanger. The energy balance
   * at each time step is:
   * </p>
   *
   * <pre>
   *   m_metal * Cp_metal * dT_metal/dt = -UA * (T_metal - T_fluid)
   * </pre>
   *
   * <p>
   * The core thermal mass and UA must be set before calling this method.
   * </p>
   *
   * @param targetTempC target metal temperature in deg C
   * @param ambientTempC initial metal temperature (ambient) in deg C
   * @param timeSteps number of time steps for the simulation
   * @param totalTimeHours total simulation duration in hours
   */
  public void runCooldownTransient(double targetTempC, double ambientTempC, int timeSteps,
      double totalTimeHours) {
    transientResults.clear();

    if (coreThermalMassKJK <= 0.0) {
      logger.warn(
          "Core thermal mass not set for transient analysis. " + "Use setCoreThermalMass(kJ/K).");
      return;
    }

    // Total UA from steady-state analysis
    double totalUA_WK = 0.0;
    if (uaPerZone != null) {
      for (double ua : uaPerZone) {
        totalUA_WK += ua;
      }
    }
    if (totalUA_WK <= 0.0) {
      logger.warn("UA not available for transient analysis. Run steady-state first.");
      return;
    }

    double totalUA_kWK = totalUA_WK / 1000.0;
    double dtHours = totalTimeHours / timeSteps;
    double dtSeconds = dtHours * 3600.0;
    double metalTemp = ambientTempC;

    // Cold fluid temperature (cold end composite)
    double fluidTempC = targetTempC;

    transientResults.add(new TransientPoint(0.0, metalTemp, fluidTempC, 0.0));

    for (int step = 1; step <= timeSteps; step++) {
      double duty = totalUA_kWK * (metalTemp - fluidTempC); // kW
      double dTMetal = -duty * dtSeconds / coreThermalMassKJK; // deg C
      metalTemp += dTMetal;

      // Fluid outlet warms up as metal cools (simplified approach ratio)
      double fluidOutTemp = fluidTempC + duty / (totalUA_kWK + 1e-10);

      transientResults.add(new TransientPoint(step * dtHours, metalTemp, fluidOutTemp, duty));

      if (metalTemp <= targetTempC + 1.0) {
        break; // Converged to target
      }
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // P8: Core sizing (BAHX L × W × H)
  // ════════════════════════════════════════════════════════════════════

  /**
   * Size the BAHX core from total UA and fin geometry (P8).
   *
   * <p>
   * Calculates the required core dimensions (Length x Width x Height) from the total UA requirement
   * and fin geometry. The method assumes the Manglik-Bergles heat-transfer coefficient for the
   * first stream with fin geometry set.
   * </p>
   *
   * <p>
   * The design procedure follows Chart Industries and Linde BAHX sizing guidelines:
   * </p>
   * <ol>
   * <li>Compute total UA from zone analysis</li>
   * <li>Estimate average h from j-factor and fluid properties</li>
   * <li>Required area A = UA / h</li>
   * <li>Core volume = A / beta (surface area density)</li>
   * <li>Distribute volume into L x W x H with aspect ratio constraints</li>
   * </ol>
   *
   * <p>
   * Also computes core weight assuming aluminium 3003 alloy density (2730 kg/m3) and typical void
   * fraction of 0.3 (70% metal fraction including headers and bars).
   * </p>
   */
  public void sizeCore() {
    // Total UA
    double totalUA_WK = 0.0;
    if (uaPerZone != null) {
      for (double ua : uaPerZone) {
        totalUA_WK += ua;
      }
    }
    if (totalUA_WK <= 0.0) {
      logger.warn("Cannot size core: run steady-state first to get UA.");
      return;
    }

    // Find first stream with fin geometry
    FinGeometry refFin = null;
    int refIdx = -1;
    for (int i = 0; i < streamFinGeometry.size(); i++) {
      if (streamFinGeometry.get(i) != null) {
        refFin = streamFinGeometry.get(i);
        refIdx = i;
        break;
      }
    }
    if (refFin == null) {
      refFin = new FinGeometry(); // Use default BAHX fin geometry
    }

    // Average heat-transfer coefficient from j-factor
    double jAvg = (streamJFactor != null && refIdx >= 0 && refIdx < streamJFactor.length)
        ? streamJFactor[refIdx]
        : 0.003; // default engineering estimate for BAHX
    if (jAvg < 1e-6) {
      jAvg = 0.003;
    }

    // Rough average fluid properties for h = j * G * Cp * Pr^(-2/3)
    // Use representative BAHX value: h ~ 200-800 W/(m2 K) for LNG applications
    double hEst = 400.0; // W/(m2·K) typical for mixed refrigerant in BAHX

    // Required area
    double requiredArea = totalUA_WK / hEst; // m2

    // Core volume from surface area density
    double beta = refFin.getBeta(); // m2/m3
    if (beta < 1.0) {
      beta = 1000.0; // typical BAHX value
    }
    double coreVolume = requiredArea / beta; // m3

    // Aspect ratio: typical BAHX is 6:1:1 (L:W:H) for large MCHE
    // L = flow direction, typically 6-8 m for large LNG MCHE
    double aspectRatio = 6.0;
    double wh = Math.pow(coreVolume / aspectRatio, 1.0 / 3.0);
    double coreLength = aspectRatio * wh;
    double coreWidth = wh;
    double coreHeight = wh;

    // Weight: aluminium 3003 density = 2730 kg/m3, metal fraction ~0.3
    double metalFraction = 0.30;
    double alDensity = 2730.0;
    double weight = coreVolume * metalFraction * alDensity;

    // Number of layers per stream (assuming equal distribution)
    int nLayers =
        Math.max(1, (int) (coreHeight / (refFin.getFinHeight() + refFin.getPlateThickness())));

    coreGeometry.setLength(coreLength);
    coreGeometry.setWidth(coreWidth);
    coreGeometry.setHeight(coreHeight);
    coreGeometry.setWeight(weight);
    coreGeometry.setNumberOfLayers(nLayers);

    // Compute core thermal mass for transient analysis
    double cpAluminium = 0.9; // kJ/(kg·K)
    coreThermalMassKJK = weight * cpAluminium;

    logger.info(String.format(
        "BAHX core sized: L=%.2f m, W=%.2f m, H=%.2f m, weight=%.0f kg, "
            + "area=%.0f m2, layers=%d",
        coreLength, coreWidth, coreHeight, weight, requiredArea, nLayers));
  }

  // ════════════════════════════════════════════════════════════════════
  // Mercury risk assessment
  // ════════════════════════════════════════════════════════════════════

  /**
   * Assess mercury attack risk for aluminium BAHX.
   *
   * <p>
   * Mercury attacks aluminium through liquid metal embrittlement (LME), forming amalgam that
   * destroys the brazed joints. The industry limit for mercury in feed gas to aluminium BAHX is
   * typically less than 0.01 microg/Nm3 (10 nanogram/Nm3 or ~10 ppt).
   * </p>
   *
   * <p>
   * This method checks the mercury concentration against the safe limit and flags a warning if the
   * BAHX material is aluminium.
   * </p>
   *
   * @param mercuryPPB mercury concentration in ppb (parts per billion by volume)
   */
  public void assessMercuryRisk(double mercuryPPB) {
    if ("BAHX".equalsIgnoreCase(exchangerType) && mercuryPPB > MERCURY_SAFE_LIMIT_PPB) {
      mercuryRiskPresent = true;
      mercuryRiskMessage = String.format(
          "CRITICAL: Mercury concentration %.1f ppb exceeds safe limit %.1f ppb for "
              + "aluminium BAHX. Risk of liquid metal embrittlement (LME). "
              + "Install mercury removal unit (activated carbon or metal sulfide bed) "
              + "upstream, or consider stainless steel PCHE alternative.",
          mercuryPPB, MERCURY_SAFE_LIMIT_PPB);
      logger.warn(mercuryRiskMessage);
    } else {
      mercuryRiskPresent = false;
      mercuryRiskMessage = "";
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Composite temperature helpers
  // ════════════════════════════════════════════════════════════════════

  /**
   * Compute mass-flow-weighted composite temperature at a given position from cold end.
   *
   * @param inletTempC inlet temperatures in deg C per stream
   * @param outletTempC outlet temperatures in deg C per stream
   * @param massFlowKgS mass flow rates in kg/s per stream
   * @param hotSide true for hot composite, false for cold composite
   * @param posFromColdEnd position index from cold end (0 to numberOfZones)
   * @return weighted average temperature in deg C
   */
  private double compositeTemp(double[] inletTempC, double[] outletTempC, double[] massFlowKgS,
      boolean hotSide, int posFromColdEnd) {
    double weightedSum = 0.0;
    double massSum = 0.0;
    for (int i = 0; i < streamCount; i++) {
      if (registeredIsHot.get(i) != hotSide) {
        continue;
      }
      double frac;
      if (hotSide) {
        frac = (double) (numberOfZones - posFromColdEnd) / numberOfZones;
      } else {
        frac = (double) posFromColdEnd / numberOfZones;
      }
      double temp = inletTempC[i] + frac * (outletTempC[i] - inletTempC[i]);
      weightedSum += massFlowKgS[i] * temp;
      massSum += massFlowKgS[i];
    }
    return massSum > 0.0 ? weightedSum / massSum : 0.0;
  }

  /**
   * Compute mass-flow-weighted composite temperature using pre-computed per-zone temperatures.
   *
   * <p>
   * Used with adaptive zone refinement where zone positions are non-uniform.
   * </p>
   *
   * @param streamTempC pre-computed temperatures per stream per zone point
   * @param massFlowKgS mass flow rates in kg/s per stream
   * @param hotSide true for hot composite, false for cold composite
   * @param effectiveZones total number of effective zones
   * @param posFromColdEnd position index from cold end (0 to effectiveZones)
   * @return weighted average temperature in deg C
   */
  private double compositeTempAdaptive(double[][] streamTempC, double[] massFlowKgS,
      boolean hotSide, int effectiveZones, int posFromColdEnd) {
    double weightedSum = 0.0;
    double massSum = 0.0;
    for (int i = 0; i < streamCount; i++) {
      if (registeredIsHot.get(i) != hotSide) {
        continue;
      }
      int zIdx;
      if (hotSide) {
        zIdx = effectiveZones - posFromColdEnd;
      } else {
        zIdx = posFromColdEnd;
      }
      weightedSum += massFlowKgS[i] * streamTempC[i][zIdx];
      massSum += massFlowKgS[i];
    }
    return massSum > 0.0 ? weightedSum / massSum : 0.0;
  }
}
