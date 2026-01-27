package neqsim.process.equipment.manifold;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesign;
import neqsim.process.util.monitor.ManifoldResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;

/**
 * <p>
 * Manifold class.
 * </p>
 * A manifold is a process unit that can take in any number of streams and distribute them into a
 * number of output streams. In NeqSim it is created as a combination of a mixer and a splitter.
 *
 * <p>
 * The manifold supports mechanical design calculations including:
 * </p>
 * <ul>
 * <li>Header and branch inner/outer diameters</li>
 * <li>Wall thickness calculations per ASME B31.3 or DNV-ST-F101</li>
 * <li>Velocity calculations (header, branch, erosional)</li>
 * <li>Flow-induced vibration analysis (LOF and FRMS methods)</li>
 * </ul>
 *
 * <p>
 * The manifold implements CapacityConstrainedEquipment with constraints for:
 * </p>
 * <ul>
 * <li>Header velocity - SOFT limit based on erosional velocity</li>
 * <li>Branch velocity - SOFT limit based on erosional velocity</li>
 * <li>Header LOF (Likelihood of Failure) - SOFT limit for FIV</li>
 * <li>Header FRMS - SOFT limit for flow-induced vibration</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Manifold extends ProcessEquipmentBaseClass
    implements neqsim.process.design.AutoSizeable, CapacityConstrainedEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Manifold.class);

  /** Mechanical design for the manifold. */
  private ManifoldMechanicalDesign mechanicalDesign;

  protected Mixer localmixer = new Mixer("tmpName");
  protected Splitter localsplitter = new Splitter("tmpName");

  double[] splitFactors = new double[1];

  /** Header inner diameter in meters. */
  private double headerInnerDiameter = 0.2794; // Default 12" ID

  /** Header wall thickness in meters. */
  private double headerWallThickness = 0.0127; // Default 12.7mm

  /** Branch inner diameter in meters. */
  private double branchInnerDiameter = 0.1397; // Default 6" ID

  /** Branch wall thickness in meters. */
  private double branchWallThickness = 0.00711; // Default 7.11mm

  /** Support arrangement for FIV calculation. */
  private String supportArrangement = "Stiff";

  /**
   * <p>
   * Constructor for Manifold with name as input.
   * </p>
   *
   * @param name name of manifold
   */
  public Manifold(String name) {
    super(name);
    setName(name);
  }

  /**
   * <p>
   * addStream.
   * </p>
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addStream(StreamInterface newStream) {
    localmixer.addStream(newStream);
  }

  /**
   * <p>
   * setSplitFactors.
   * </p>
   *
   * @param splitFact an array of type double
   */
  public void setSplitFactors(double[] splitFact) {
    splitFactors = splitFact;
    localsplitter.setInletStream(localmixer.getOutletStream());
    localsplitter.setSplitFactors(splitFactors);
  }

  /**
   * <p>
   * getSplitStream.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getSplitStream(int i) {
    return localsplitter.getSplitStream(i);
  }

  /**
   * <p>
   * getMixedStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface}
   */
  public StreamInterface getMixedStream() {
    return localmixer.getOutletStream();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    localmixer.run(id);
    localsplitter.setInletStream(localmixer.getOutletStream());
    localsplitter.run();
  }

  /** {@inheritDoc} */
  @Override
  public void setName(String name) {
    super.setName(name);
    localmixer.setName(name + "local mixer");
    localsplitter.setName(name + "local splitter");
  }

  /**
   * Get the number of output streams from the manifold.
   *
   * @return number of split streams
   */
  public int getNumberOfOutputStreams() {
    return localsplitter.getSplitFactors().length;
  }

  // ============================================================================
  // DIAMETER AND GEOMETRY METHODS
  // ============================================================================

  /**
   * Get header inner diameter.
   *
   * @return inner diameter in meters
   */
  public double getHeaderInnerDiameter() {
    return headerInnerDiameter;
  }

  /**
   * Set header inner diameter.
   *
   * @param diameter inner diameter in meters
   */
  public void setHeaderInnerDiameter(double diameter) {
    this.headerInnerDiameter = diameter;
  }

  /**
   * Set header inner diameter with unit.
   *
   * @param diameter inner diameter value
   * @param unit unit (m, mm, inch)
   */
  public void setHeaderInnerDiameter(double diameter, String unit) {
    if (unit.equalsIgnoreCase("mm")) {
      this.headerInnerDiameter = diameter / 1000.0;
    } else if (unit.equalsIgnoreCase("inch") || unit.equalsIgnoreCase("in")) {
      this.headerInnerDiameter = diameter * 0.0254;
    } else {
      this.headerInnerDiameter = diameter;
    }
  }

  /**
   * Get header outer diameter (ID + 2 * wall thickness).
   *
   * @return outer diameter in meters
   */
  public double getHeaderOuterDiameter() {
    return headerInnerDiameter + 2 * headerWallThickness;
  }

  /**
   * Get header wall thickness.
   *
   * @return wall thickness in meters
   */
  public double getHeaderWallThickness() {
    return headerWallThickness;
  }

  /**
   * Set header wall thickness.
   *
   * @param thickness wall thickness in meters
   */
  public void setHeaderWallThickness(double thickness) {
    this.headerWallThickness = thickness;
  }

  /**
   * Set header wall thickness with unit.
   *
   * @param thickness wall thickness value
   * @param unit unit (m, mm, inch)
   */
  public void setHeaderWallThickness(double thickness, String unit) {
    if (unit.equalsIgnoreCase("mm")) {
      this.headerWallThickness = thickness / 1000.0;
    } else if (unit.equalsIgnoreCase("inch") || unit.equalsIgnoreCase("in")) {
      this.headerWallThickness = thickness * 0.0254;
    } else {
      this.headerWallThickness = thickness;
    }
  }

  /**
   * Get branch inner diameter.
   *
   * @return inner diameter in meters
   */
  public double getBranchInnerDiameter() {
    return branchInnerDiameter;
  }

  /**
   * Set branch inner diameter.
   *
   * @param diameter inner diameter in meters
   */
  public void setBranchInnerDiameter(double diameter) {
    this.branchInnerDiameter = diameter;
  }

  /**
   * Set branch inner diameter with unit.
   *
   * @param diameter inner diameter value
   * @param unit unit (m, mm, inch)
   */
  public void setBranchInnerDiameter(double diameter, String unit) {
    if (unit.equalsIgnoreCase("mm")) {
      this.branchInnerDiameter = diameter / 1000.0;
    } else if (unit.equalsIgnoreCase("inch") || unit.equalsIgnoreCase("in")) {
      this.branchInnerDiameter = diameter * 0.0254;
    } else {
      this.branchInnerDiameter = diameter;
    }
  }

  /**
   * Get branch outer diameter (ID + 2 * wall thickness).
   *
   * @return outer diameter in meters
   */
  public double getBranchOuterDiameter() {
    return branchInnerDiameter + 2 * branchWallThickness;
  }

  /**
   * Get branch wall thickness.
   *
   * @return wall thickness in meters
   */
  public double getBranchWallThickness() {
    return branchWallThickness;
  }

  /**
   * Set branch wall thickness.
   *
   * @param thickness wall thickness in meters
   */
  public void setBranchWallThickness(double thickness) {
    this.branchWallThickness = thickness;
  }

  /**
   * Set branch wall thickness with unit.
   *
   * @param thickness wall thickness value
   * @param unit unit (m, mm, inch)
   */
  public void setBranchWallThickness(double thickness, String unit) {
    if (unit.equalsIgnoreCase("mm")) {
      this.branchWallThickness = thickness / 1000.0;
    } else if (unit.equalsIgnoreCase("inch") || unit.equalsIgnoreCase("in")) {
      this.branchWallThickness = thickness * 0.0254;
    } else {
      this.branchWallThickness = thickness;
    }
  }

  // ============================================================================
  // VELOCITY CALCULATIONS
  // ============================================================================

  /**
   * Calculate header velocity based on current flow and geometry.
   *
   * @return header velocity in m/s
   */
  public double getHeaderVelocity() {
    StreamInterface mixedStream = getMixedStream();
    if (mixedStream == null || mixedStream.getThermoSystem() == null) {
      return 0.0;
    }

    double volumeFlow = mixedStream.getFlowRate("m3/sec");
    double area = Math.PI * headerInnerDiameter * headerInnerDiameter / 4.0;
    return area > 0 ? volumeFlow / area : 0.0;
  }

  /**
   * Calculate branch velocity based on current flow and geometry.
   *
   * @return average branch velocity in m/s
   */
  public double getBranchVelocity() {
    StreamInterface mixedStream = getMixedStream();
    if (mixedStream == null || mixedStream.getThermoSystem() == null) {
      return 0.0;
    }

    double volumeFlow = mixedStream.getFlowRate("m3/sec");
    double flowPerBranch = volumeFlow / Math.max(1, getNumberOfOutputStreams());
    double area = Math.PI * branchInnerDiameter * branchInnerDiameter / 4.0;
    return area > 0 ? flowPerBranch / area : 0.0;
  }

  /**
   * Calculate erosional velocity per API RP 14E.
   *
   * @param cFactor erosional C-factor (typically 100-150)
   * @return erosional velocity in m/s
   */
  public double getErosionalVelocity(double cFactor) {
    StreamInterface mixedStream = getMixedStream();
    if (mixedStream == null || mixedStream.getThermoSystem() == null) {
      return 0.0;
    }

    double density = mixedStream.getThermoSystem().getDensity("kg/m3");
    return density > 0 ? cFactor / Math.sqrt(density) : 0.0;
  }

  /**
   * Calculate erosional velocity with default C-factor of 100.
   *
   * @return erosional velocity in m/s
   */
  public double getErosionalVelocity() {
    return getErosionalVelocity(100.0);
  }

  // ============================================================================
  // FLOW-INDUCED VIBRATION (FIV) CALCULATIONS
  // ============================================================================

  /**
   * Get support arrangement for FIV calculations.
   *
   * @return support arrangement (Stiff, Medium stiff, Medium, Flexible)
   */
  public String getSupportArrangement() {
    return supportArrangement;
  }

  /**
   * Set support arrangement for FIV calculations.
   *
   * @param arrangement support arrangement (Stiff, Medium stiff, Medium, Flexible)
   */
  public void setSupportArrangement(String arrangement) {
    this.supportArrangement = arrangement;
  }

  /**
   * Calculate Likelihood of Failure (LOF) for header pipe based on flow-induced vibration.
   * <p>
   * LOF interpretation:
   * </p>
   * <ul>
   * <li>&lt; 0.5: Low risk - acceptable</li>
   * <li>0.5 - 1.0: Medium risk - monitoring recommended</li>
   * <li>&gt; 1.0: High risk - design review required</li>
   * </ul>
   *
   * @return LOF value (dimensionless)
   */
  public double calculateHeaderLOF() {
    StreamInterface mixedStream = getMixedStream();
    if (mixedStream == null || mixedStream.getThermoSystem() == null) {
      return Double.NaN;
    }

    double mixDensity = mixedStream.getThermoSystem().getDensity("kg/m3");
    double mixVelocity = getHeaderVelocity();

    // Calculate gas volume fraction
    double gasVelocity = 0.0;
    if (mixedStream.getThermoSystem().hasPhaseType("gas")) {
      double gasFrac = mixedStream.getThermoSystem().getPhase("gas").getVolume("m3")
          / mixedStream.getThermoSystem().getVolume("m3");
      gasVelocity = mixVelocity * gasFrac;
    }
    double GVF = mixVelocity > 0 ? gasVelocity / mixVelocity : 0.0;

    // Calculate flow velocity factor (FVF)
    double FVF = 1.0;
    if (GVF > 0.88) {
      if (GVF > 0.99) {
        double viscosity = mixedStream.getThermoSystem().getViscosity("kg/msec");
        FVF = Math.sqrt(viscosity / Math.sqrt(0.001));
      } else {
        FVF = -27.882 * GVF * GVF + 45.545 * GVF - 17.495;
      }
    } else if (GVF < 0.2) {
      FVF = 0.2 + 4 * GVF;
    }

    // External diameter in mm
    double externalDiameter = getHeaderOuterDiameter() * 1000.0;

    // Support arrangement coefficients
    double alpha;
    double beta;
    if (supportArrangement.equals("Stiff")) {
      alpha = 446187 + 646 * externalDiameter
          + 9.17E-4 * externalDiameter * externalDiameter * externalDiameter;
      beta = 0.1 * Math.log(externalDiameter) - 1.3739;
    } else if (supportArrangement.equals("Medium stiff")) {
      alpha = 283921 + 370 * externalDiameter;
      beta = 0.1106 * Math.log(externalDiameter) - 1.501;
    } else if (supportArrangement.equals("Medium")) {
      alpha = 150412 + 209 * externalDiameter;
      beta = 0.0815 * Math.log(externalDiameter) - 1.3269;
    } else {
      // Flexible
      alpha = 41.21 * Math.log(externalDiameter) + 49397;
      beta = 0.0815 * Math.log(externalDiameter) - 1.3842;
    }

    double diameterOverThickness = externalDiameter / (headerWallThickness * 1000.0);
    double Fv = alpha * Math.pow(diameterOverThickness, beta);

    return mixDensity * mixVelocity * mixVelocity * FVF / Fv;
  }

  /**
   * Calculate Flow-induced vibration RMS (FRMS) for header pipe.
   * <p>
   * FRMS provides an alternative measure of vibration intensity based on mixture properties.
   * </p>
   *
   * @return FRMS value
   */
  public double calculateHeaderFRMS() {
    return calculateHeaderFRMS(6.7);
  }

  /**
   * Calculate Flow-induced vibration RMS (FRMS) for header pipe with specified constant.
   *
   * @param frmsConstant FRMS constant (typically 6.7)
   * @return FRMS value
   */
  public double calculateHeaderFRMS(double frmsConstant) {
    StreamInterface mixedStream = getMixedStream();
    if (mixedStream == null || mixedStream.getThermoSystem() == null) {
      return Double.NaN;
    }

    double mixVelocity = getHeaderVelocity();

    // Calculate gas volume fraction
    double GVF = 0.0;
    if (mixedStream.getThermoSystem().hasPhaseType("gas")) {
      GVF = mixedStream.getThermoSystem().getPhase("gas").getVolume("m3")
          / mixedStream.getThermoSystem().getVolume("m3");
    }

    // Get liquid density (use mixture if no liquid)
    double liquidDensity = mixedStream.getThermoSystem().getDensity("kg/m3");
    if (mixedStream.getThermoSystem().hasPhaseType("oil")) {
      liquidDensity = mixedStream.getThermoSystem().getPhase("oil").getDensity("kg/m3");
    } else if (mixedStream.getThermoSystem().hasPhaseType("aqueous")) {
      liquidDensity = mixedStream.getThermoSystem().getPhase("aqueous").getDensity("kg/m3");
    }

    double C = Math.min(Math.min(1, 5 * (1 - GVF)), 5 * GVF) * frmsConstant;
    return C * Math.pow(headerInnerDiameter, 1.6) * Math.pow(liquidDensity, 0.6)
        * Math.pow(mixVelocity, 1.2);
  }

  /**
   * Calculate LOF for branch pipes.
   *
   * @return branch LOF value
   */
  public double calculateBranchLOF() {
    StreamInterface mixedStream = getMixedStream();
    if (mixedStream == null || mixedStream.getThermoSystem() == null) {
      return Double.NaN;
    }

    double mixDensity = mixedStream.getThermoSystem().getDensity("kg/m3");
    double mixVelocity = getBranchVelocity();

    // Calculate gas volume fraction
    double gasVelocity = 0.0;
    if (mixedStream.getThermoSystem().hasPhaseType("gas")) {
      double gasFrac = mixedStream.getThermoSystem().getPhase("gas").getVolume("m3")
          / mixedStream.getThermoSystem().getVolume("m3");
      gasVelocity = mixVelocity * gasFrac;
    }
    double GVF = mixVelocity > 0 ? gasVelocity / mixVelocity : 0.0;

    // Calculate flow velocity factor (FVF)
    double FVF = 1.0;
    if (GVF > 0.88) {
      if (GVF > 0.99) {
        double viscosity = mixedStream.getThermoSystem().getViscosity("kg/msec");
        FVF = Math.sqrt(viscosity / Math.sqrt(0.001));
      } else {
        FVF = -27.882 * GVF * GVF + 45.545 * GVF - 17.495;
      }
    } else if (GVF < 0.2) {
      FVF = 0.2 + 4 * GVF;
    }

    // External diameter in mm
    double externalDiameter = getBranchOuterDiameter() * 1000.0;

    // Support arrangement coefficients
    double alpha;
    double beta;
    if (supportArrangement.equals("Stiff")) {
      alpha = 446187 + 646 * externalDiameter
          + 9.17E-4 * externalDiameter * externalDiameter * externalDiameter;
      beta = 0.1 * Math.log(externalDiameter) - 1.3739;
    } else if (supportArrangement.equals("Medium stiff")) {
      alpha = 283921 + 370 * externalDiameter;
      beta = 0.1106 * Math.log(externalDiameter) - 1.501;
    } else if (supportArrangement.equals("Medium")) {
      alpha = 150412 + 209 * externalDiameter;
      beta = 0.0815 * Math.log(externalDiameter) - 1.3269;
    } else {
      // Flexible
      alpha = 41.21 * Math.log(externalDiameter) + 49397;
      beta = 0.0815 * Math.log(externalDiameter) - 1.3842;
    }

    double diameterOverThickness = externalDiameter / (branchWallThickness * 1000.0);
    double Fv = alpha * Math.pow(diameterOverThickness, beta);

    return mixDensity * mixVelocity * mixVelocity * FVF / Fv;
  }

  /**
   * Calculate Flow-induced vibration RMS (FRMS) for branch pipes.
   * <p>
   * FRMS provides an alternative measure of vibration intensity based on mixture properties.
   * </p>
   *
   * @return FRMS value
   */
  public double calculateBranchFRMS() {
    return calculateBranchFRMS(6.7);
  }

  /**
   * Calculate Flow-induced vibration RMS (FRMS) for branch pipes with specified constant.
   *
   * @param frmsConstant FRMS constant (typically 6.7)
   * @return FRMS value
   */
  public double calculateBranchFRMS(double frmsConstant) {
    StreamInterface mixedStream = getMixedStream();
    if (mixedStream == null || mixedStream.getThermoSystem() == null) {
      return Double.NaN;
    }

    double mixVelocity = getBranchVelocity();

    // Calculate gas volume fraction
    double GVF = 0.0;
    if (mixedStream.getThermoSystem().hasPhaseType("gas")) {
      GVF = mixedStream.getThermoSystem().getPhase("gas").getVolume("m3")
          / mixedStream.getThermoSystem().getVolume("m3");
    }

    // Get liquid density (use mixture if no liquid)
    double liquidDensity = mixedStream.getThermoSystem().getDensity("kg/m3");
    if (mixedStream.getThermoSystem().hasPhaseType("oil")) {
      liquidDensity = mixedStream.getThermoSystem().getPhase("oil").getDensity("kg/m3");
    } else if (mixedStream.getThermoSystem().hasPhaseType("aqueous")) {
      liquidDensity = mixedStream.getThermoSystem().getPhase("aqueous").getDensity("kg/m3");
    }

    double C = Math.min(Math.min(1, 5 * (1 - GVF)), 5 * GVF) * frmsConstant;
    return C * Math.pow(branchInnerDiameter, 1.6) * Math.pow(liquidDensity, 0.6)
        * Math.pow(mixVelocity, 1.2);
  }

  /**
   * Get comprehensive FIV analysis results as a map.
   *
   * @return map containing all FIV analysis results
   */
  public java.util.Map<String, Object> getFIVAnalysis() {
    java.util.Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();

    result.put("supportArrangement", supportArrangement);

    // Header analysis
    java.util.Map<String, Object> header = new java.util.LinkedHashMap<String, Object>();
    header.put("innerDiameter_m", headerInnerDiameter);
    header.put("outerDiameter_m", getHeaderOuterDiameter());
    header.put("wallThickness_m", headerWallThickness);
    header.put("velocity_m_s", getHeaderVelocity());
    header.put("LOF", calculateHeaderLOF());
    header.put("FRMS", calculateHeaderFRMS());

    double headerLOF = calculateHeaderLOF();
    if (headerLOF < 0.5) {
      header.put("LOF_risk", "LOW");
    } else if (headerLOF < 1.0) {
      header.put("LOF_risk", "MEDIUM");
    } else {
      header.put("LOF_risk", "HIGH");
    }
    result.put("header", header);

    // Branch analysis
    java.util.Map<String, Object> branch = new java.util.LinkedHashMap<String, Object>();
    branch.put("innerDiameter_m", branchInnerDiameter);
    branch.put("outerDiameter_m", getBranchOuterDiameter());
    branch.put("wallThickness_m", branchWallThickness);
    branch.put("velocity_m_s", getBranchVelocity());
    branch.put("LOF", calculateBranchLOF());
    branch.put("FRMS", calculateBranchFRMS());

    double branchLOF = calculateBranchLOF();
    if (branchLOF < 0.5) {
      branch.put("LOF_risk", "LOW");
    } else if (branchLOF < 1.0) {
      branch.put("LOF_risk", "MEDIUM");
    } else {
      branch.put("LOF_risk", "HIGH");
    }
    result.put("branch", branch);

    // Erosional velocity
    result.put("erosionalVelocity_m_s", getErosionalVelocity());
    result.put("velocityMargin_header",
        getErosionalVelocity() > 0 ? getHeaderVelocity() / getErosionalVelocity() : 0.0);
    result.put("velocityMargin_branch",
        getErosionalVelocity() > 0 ? getBranchVelocity() / getErosionalVelocity() : 0.0);

    return result;
  }

  /**
   * Get FIV analysis as JSON string.
   *
   * @return JSON string with FIV analysis
   */
  public String getFIVAnalysisJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getFIVAnalysis());
  }

  // ============================================================================
  // MASS BALANCE AND JSON
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = localmixer.getMassBalance(unit)
        + localmixer.getOutletStream().getThermoSystem().getFlowRate(unit);
    double outletFlow = 0.0;
    for (int i = 0; i < getNumberOfOutputStreams(); i++) {
      outletFlow += getSplitStream(i).getThermoSystem().getFlowRate(unit);
    }
    return outletFlow - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new ManifoldResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    ManifoldResponse res = new ManifoldResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new ManifoldMechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public ManifoldMechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  // ============================================================================
  // AutoSizeable Implementation
  // ============================================================================

  /** Flag indicating if manifold has been auto-sized. */
  private boolean autoSized = false;

  /**
   * Target header velocity for sizing (m/s) - typical gas manifold design velocity.
   */
  private static final double TARGET_HEADER_VELOCITY = 15.0;

  /** Target branch velocity for sizing (m/s) - slightly higher than header. */
  private static final double TARGET_BRANCH_VELOCITY = 18.0;

  /** Standard pipe inner diameters in meters (Schedule 40 approximate). */
  private static final double[] STANDARD_PIPE_IDS = {0.0269, // 1"
      0.0409, // 1.5"
      0.0525, // 2"
      0.0779, // 3"
      0.1023, // 4"
      0.1541, // 6"
      0.2027, // 8"
      0.2540, // 10"
      0.3048, // 12"
      0.3556, // 14"
      0.4064, // 16"
      0.4572, // 18"
      0.5080, // 20"
      0.6096, // 24"
      0.7620, // 30"
      0.9144 // 36"
  };

  /** Standard pipe wall thicknesses in meters (Schedule 40 approximate). */
  private static final double[] STANDARD_PIPE_WALLS = {0.00338, // 1"
      0.00368, // 1.5"
      0.00391, // 2"
      0.00549, // 3"
      0.00602, // 4"
      0.00711, // 6"
      0.00823, // 8"
      0.00927, // 10"
      0.01067, // 12"
      0.01118, // 14"
      0.01270, // 16"
      0.01422, // 18"
      0.01575, // 20"
      0.01778, // 24"
      0.01905, // 30"
      0.02223 // 36"
  };

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    // Run to get current conditions
    run();

    // Get total inlet flow
    StreamInterface mixedStream = getMixedStream();
    if (mixedStream == null || mixedStream.getThermoSystem() == null) {
      throw new IllegalStateException("Manifold must have inlet streams before auto-sizing");
    }

    double totalVolumeFlow = mixedStream.getFlowRate("m3/hr");
    double totalMassFlow = mixedStream.getFlowRate("kg/hr");
    double volumeFlowM3sec = mixedStream.getFlowRate("m3/sec");

    // Check for zero or negligible flow
    boolean hasFlow = totalMassFlow > 1e-6;

    if (hasFlow) {
      // Calculate required header diameter for target velocity
      // Area = Q / v, Diameter = sqrt(4 * Area / pi)
      double requiredHeaderArea = volumeFlowM3sec / TARGET_HEADER_VELOCITY;
      double requiredHeaderID = Math.sqrt(4.0 * requiredHeaderArea / Math.PI);

      // Select next larger standard pipe size
      double selectedHeaderID = selectStandardPipeID(requiredHeaderID);
      double selectedHeaderWall = getWallThicknessForID(selectedHeaderID);

      // Set header dimensions
      setHeaderInnerDiameter(selectedHeaderID);
      setHeaderWallThickness(selectedHeaderWall);

      // Calculate required branch diameter
      int numBranches = Math.max(1, getNumberOfOutputStreams());
      double flowPerBranch = volumeFlowM3sec / numBranches;
      double requiredBranchArea = flowPerBranch / TARGET_BRANCH_VELOCITY;
      double requiredBranchID = Math.sqrt(4.0 * requiredBranchArea / Math.PI);

      // Select next larger standard pipe size for branches
      double selectedBranchID = selectStandardPipeID(requiredBranchID);
      double selectedBranchWall = getWallThicknessForID(selectedBranchID);

      // Set branch dimensions
      setBranchInnerDiameter(selectedBranchID);
      setBranchWallThickness(selectedBranchWall);

      logger.info("Manifold '{}' sized: Header ID={} mm, Branch ID={} mm for flow={} m3/hr",
          getName(), String.format("%.1f", selectedHeaderID * 1000),
          String.format("%.1f", selectedBranchID * 1000), String.format("%.1f", totalVolumeFlow));
    }

    // Apply safety factor to design capacity
    double designVolumeFlow = hasFlow ? totalVolumeFlow * safetyFactor : 100.0;

    // Set mechanical design parameters
    if (mechanicalDesign != null) {
      mechanicalDesign.setMaxDesignVolumeFlow(designVolumeFlow);
      // Sync diameters with mechanical design
      mechanicalDesign.setHeaderDiameter(getHeaderOuterDiameter());
      mechanicalDesign.setBranchDiameter(getBranchOuterDiameter());
      mechanicalDesign.setNumberOfInlets(localmixer.getNumberOfInputStreams());
      mechanicalDesign.setNumberOfOutlets(getNumberOfOutputStreams());
    }

    // Update velocity design limits based on sized pipe velocities with safety
    // factor
    double currentHeaderVelocity = getHeaderVelocity();
    double currentBranchVelocity = getBranchVelocity();
    double erosionalVelocity = getErosionalVelocity();

    // For header velocity design: use current velocity * safety factor, but cap at
    // erosional limit
    if (hasFlow && currentHeaderVelocity > 0 && !Double.isNaN(currentHeaderVelocity)) {
      double proposedHeaderDesign = currentHeaderVelocity * safetyFactor;
      if (erosionalVelocity > 0 && !Double.isNaN(erosionalVelocity)) {
        maxHeaderVelocityDesign = Math.min(proposedHeaderDesign, erosionalVelocity * 0.9);
      } else {
        maxHeaderVelocityDesign = proposedHeaderDesign;
      }
      maxHeaderVelocityDesign = Math.max(maxHeaderVelocityDesign, 5.0);
    }

    // Same for branch velocity
    if (hasFlow && currentBranchVelocity > 0 && !Double.isNaN(currentBranchVelocity)) {
      double proposedBranchDesign = currentBranchVelocity * safetyFactor;
      if (erosionalVelocity > 0 && !Double.isNaN(erosionalVelocity)) {
        maxBranchVelocityDesign = Math.min(proposedBranchDesign, erosionalVelocity * 0.9);
      } else {
        maxBranchVelocityDesign = proposedBranchDesign;
      }
      maxBranchVelocityDesign = Math.max(maxBranchVelocityDesign, 5.0);
    }

    // Clear and reinitialize capacity constraints with new design values
    capacityConstraints.clear();
    initializeCapacityConstraints();

    autoSized = true;
    logger.info(
        "Manifold '{}' auto-sized: headerVel={} m/s (design={}), branchVel={} m/s (design={})",
        getName(), String.format("%.1f", currentHeaderVelocity),
        String.format("%.1f", maxHeaderVelocityDesign),
        String.format("%.1f", currentBranchVelocity),
        String.format("%.1f", maxBranchVelocityDesign));
  }

  /**
   * Select the next larger standard pipe ID for the required diameter.
   *
   * @param requiredID required inner diameter in meters
   * @return selected standard pipe ID in meters
   */
  private double selectStandardPipeID(double requiredID) {
    for (double standardID : STANDARD_PIPE_IDS) {
      if (standardID >= requiredID) {
        return standardID;
      }
    }
    // If larger than all standard sizes, return the largest
    return STANDARD_PIPE_IDS[STANDARD_PIPE_IDS.length - 1];
  }

  /**
   * Get standard wall thickness for a given pipe ID.
   *
   * @param pipeID pipe inner diameter in meters
   * @return wall thickness in meters
   */
  private double getWallThicknessForID(double pipeID) {
    for (int i = 0; i < STANDARD_PIPE_IDS.length; i++) {
      if (Math.abs(STANDARD_PIPE_IDS[i] - pipeID) < 0.001) {
        return STANDARD_PIPE_WALLS[i];
      }
    }
    // Default wall thickness based on approximate scaling
    return pipeID * 0.04; // ~4% of ID as rough estimate
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize() {
    autoSize(1.2); // Default 20% margin
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(String companyStandard, String trDocument) {
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
    sb.append("=== Manifold Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(autoSized).append("\n");

    StreamInterface mixedStream = getMixedStream();
    if (mixedStream != null && mixedStream.getThermoSystem() != null) {
      sb.append("\n--- Operating Conditions ---\n");
      sb.append("Total Flow Rate: ")
          .append(String.format("%.2f m3/hr", mixedStream.getFlowRate("m3/hr"))).append("\n");
      sb.append("Mass Flow Rate: ")
          .append(String.format("%.2f kg/hr", mixedStream.getFlowRate("kg/hr"))).append("\n");
      sb.append("Pressure: ").append(String.format("%.2f bara", mixedStream.getPressure("bara")))
          .append("\n");
      sb.append("Temperature: ").append(String.format("%.2f C", mixedStream.getTemperature("C")))
          .append("\n");

      sb.append("\n--- Geometry ---\n");
      sb.append("Header ID: ").append(String.format("%.1f mm", headerInnerDiameter * 1000))
          .append("\n");
      sb.append("Header OD: ").append(String.format("%.1f mm", getHeaderOuterDiameter() * 1000))
          .append("\n");
      sb.append("Header Wall: ").append(String.format("%.2f mm", headerWallThickness * 1000))
          .append("\n");
      sb.append("Branch ID: ").append(String.format("%.1f mm", branchInnerDiameter * 1000))
          .append("\n");
      sb.append("Branch OD: ").append(String.format("%.1f mm", getBranchOuterDiameter() * 1000))
          .append("\n");

      sb.append("\n--- Velocities ---\n");
      sb.append("Header Velocity: ").append(String.format("%.2f m/s", getHeaderVelocity()))
          .append("\n");
      sb.append("Branch Velocity: ").append(String.format("%.2f m/s", getBranchVelocity()))
          .append("\n");
      sb.append("Erosional Velocity: ").append(String.format("%.2f m/s", getErosionalVelocity()))
          .append("\n");

      sb.append("\n--- FIV Analysis ---\n");
      sb.append("Support Arrangement: ").append(supportArrangement).append("\n");
      double headerLOF = calculateHeaderLOF();
      sb.append("Header LOF: ").append(String.format("%.4f", headerLOF));
      if (headerLOF < 0.5) {
        sb.append(" (LOW RISK)\n");
      } else if (headerLOF < 1.0) {
        sb.append(" (MEDIUM RISK)\n");
      } else {
        sb.append(" (HIGH RISK)\n");
      }
      sb.append("Header FRMS: ").append(String.format("%.2f", calculateHeaderFRMS())).append("\n");
      double branchLOF = calculateBranchLOF();
      sb.append("Branch LOF: ").append(String.format("%.4f", branchLOF));
      if (branchLOF < 0.5) {
        sb.append(" (LOW RISK)\n");
      } else if (branchLOF < 1.0) {
        sb.append(" (MEDIUM RISK)\n");
      } else {
        sb.append(" (HIGH RISK)\n");
      }
    }

    sb.append("\n--- Configuration ---\n");
    sb.append("Number of Inputs: ").append(localmixer.getNumberOfInputStreams()).append("\n");
    sb.append("Number of Outputs: ").append(getNumberOfOutputStreams()).append("\n");

    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReportJson() {
    java.util.Map<String, Object> report = new java.util.LinkedHashMap<String, Object>();
    report.put("equipmentName", getName());
    report.put("equipmentType", "Manifold");
    report.put("autoSized", autoSized);

    StreamInterface mixedStream = getMixedStream();
    if (mixedStream != null && mixedStream.getThermoSystem() != null) {
      java.util.Map<String, Object> operating = new java.util.LinkedHashMap<String, Object>();
      operating.put("volumeFlow_m3hr", mixedStream.getFlowRate("m3/hr"));
      operating.put("massFlow_kghr", mixedStream.getFlowRate("kg/hr"));
      operating.put("pressure_bara", mixedStream.getPressure("bara"));
      operating.put("temperature_C", mixedStream.getTemperature("C"));
      report.put("operatingConditions", operating);

      java.util.Map<String, Object> geometry = new java.util.LinkedHashMap<String, Object>();
      geometry.put("headerInnerDiameter_mm", headerInnerDiameter * 1000);
      geometry.put("headerOuterDiameter_mm", getHeaderOuterDiameter() * 1000);
      geometry.put("headerWallThickness_mm", headerWallThickness * 1000);
      geometry.put("branchInnerDiameter_mm", branchInnerDiameter * 1000);
      geometry.put("branchOuterDiameter_mm", getBranchOuterDiameter() * 1000);
      geometry.put("branchWallThickness_mm", branchWallThickness * 1000);
      report.put("geometry", geometry);

      java.util.Map<String, Object> velocities = new java.util.LinkedHashMap<String, Object>();
      velocities.put("headerVelocity_m_s", getHeaderVelocity());
      velocities.put("branchVelocity_m_s", getBranchVelocity());
      velocities.put("erosionalVelocity_m_s", getErosionalVelocity());
      report.put("velocities", velocities);

      // Add FIV analysis
      report.put("fivAnalysis", getFIVAnalysis());
    }

    report.put("numberOfInputs", localmixer.getNumberOfInputStreams());
    report.put("numberOfOutputs", getNumberOfOutputStreams());

    return new GsonBuilder().setPrettyPrinting().create().toJson(report);
  }

  // ============================================================================
  // CapacityConstrainedEquipment Implementation
  // ============================================================================

  /** Storage for capacity constraints. */
  private final Map<String, CapacityConstraint> capacityConstraints = new LinkedHashMap<>();

  /** Maximum header velocity design limit in m/s. */
  private double maxHeaderVelocityDesign = 20.0;

  /** Maximum branch velocity design limit in m/s. */
  private double maxBranchVelocityDesign = 25.0;

  /** Maximum LOF design limit (1.0 = high risk threshold). */
  private double maxLOFDesign = 1.0;

  /** Maximum FRMS design limit. */
  private double maxFRMSDesign = 500.0;

  /**
   * Initialize capacity constraints based on design limits and FIV analysis.
   *
   * <p>
   * NOTE: All constraints are disabled by default for backwards compatibility. Enable specific
   * constraints when manifold capacity analysis is needed (e.g., after sizing).
   * </p>
   */
  protected void initializeCapacityConstraints() {
    // Header velocity constraint
    addCapacityConstraint(
        new CapacityConstraint("headerVelocity", "m/s", CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxHeaderVelocityDesign).setMaxValue(getErosionalVelocity())
            .setWarningThreshold(0.9).setDescription("Header pipe velocity vs erosional limit")
            .setValueSupplier(() -> getHeaderVelocity()));

    // Branch velocity constraint
    addCapacityConstraint(
        new CapacityConstraint("branchVelocity", "m/s", CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxBranchVelocityDesign).setMaxValue(getErosionalVelocity())
            .setWarningThreshold(0.9).setDescription("Branch pipe velocity vs erosional limit")
            .setValueSupplier(() -> getBranchVelocity()));

    // Header LOF (Likelihood of Failure) - FIV constraint
    addCapacityConstraint(
        new CapacityConstraint("headerLOF", "-", CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxLOFDesign).setMaxValue(1.5).setWarningThreshold(0.5)
            .setDescription("Header LOF for flow-induced vibration (>1.0 = high risk)")
            .setValueSupplier(() -> calculateHeaderLOF()));

    // Header FRMS (Flow-induced vibration RMS)
    addCapacityConstraint(
        new CapacityConstraint("headerFRMS", "-", CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxFRMSDesign).setMaxValue(750.0).setWarningThreshold(0.8)
            .setDescription("Header FRMS vibration intensity")
            .setValueSupplier(() -> calculateHeaderFRMS()));

    // Branch LOF - FIV constraint
    addCapacityConstraint(
        new CapacityConstraint("branchLOF", "-", CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxLOFDesign).setMaxValue(1.5).setWarningThreshold(0.5)
            .setDescription("Branch LOF for flow-induced vibration (>1.0 = high risk)")
            .setValueSupplier(() -> calculateBranchLOF()));

    // Branch FRMS (Flow-induced vibration RMS)
    addCapacityConstraint(
        new CapacityConstraint("branchFRMS", "-", CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxFRMSDesign).setMaxValue(750.0).setWarningThreshold(0.8)
            .setDescription("Branch FRMS vibration intensity")
            .setValueSupplier(() -> calculateBranchFRMS()));
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getCapacityConstraints() {
    if (capacityConstraints.isEmpty()) {
      initializeCapacityConstraints();
    }
    return Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint() {
    CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (CapacityConstraint c : getCapacityConstraints().values()) {
      if (!c.isEnabled()) {
        continue;
      }
      double util = c.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = c;
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (CapacityConstraint c : getCapacityConstraints().values()) {
      if (!c.isEnabled()) {
        continue;
      }
      if (c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (CapacityConstraint c : getCapacityConstraints().values()) {
      if (!c.isEnabled()) {
        continue;
      }
      if (c.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (CapacityConstraint c : getCapacityConstraints().values()) {
      if (!c.isEnabled()) {
        continue;
      }
      double util = c.getUtilization();
      if (!Double.isNaN(util)) {
        maxUtil = Math.max(maxUtil, util);
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(CapacityConstraint constraint) {
    if (constraint != null) {
      capacityConstraints.put(constraint.getName(), constraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return capacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    capacityConstraints.clear();
  }

  /**
   * Set maximum header velocity design limit.
   *
   * @param velocity maximum velocity in m/s
   */
  public void setMaxHeaderVelocityDesign(double velocity) {
    this.maxHeaderVelocityDesign = velocity;
    capacityConstraints.clear(); // Force re-initialization
  }

  /**
   * Set maximum branch velocity design limit.
   *
   * @param velocity maximum velocity in m/s
   */
  public void setMaxBranchVelocityDesign(double velocity) {
    this.maxBranchVelocityDesign = velocity;
    capacityConstraints.clear(); // Force re-initialization
  }

  /**
   * Set maximum LOF design limit.
   *
   * @param lof maximum LOF value
   */
  public void setMaxLOFDesign(double lof) {
    this.maxLOFDesign = lof;
    capacityConstraints.clear(); // Force re-initialization
  }

  /**
   * Set maximum FRMS design limit.
   *
   * @param frms maximum FRMS value
   */
  public void setMaxFRMSDesign(double frms) {
    this.maxFRMSDesign = frms;
    capacityConstraints.clear(); // Force re-initialization
  }
}
