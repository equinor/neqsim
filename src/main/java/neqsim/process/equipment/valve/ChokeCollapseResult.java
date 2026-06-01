package neqsim.process.equipment.valve;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Result of a {@link ChokeCollapseAnalyzer} analysis.
 *
 * <p>
 * Holds the diagnosed flow regime, collapse mode, and the numerical margins used to reach the
 * verdict. The class is serialisable so it can be embedded in {@code ProcessAutomation} snapshots
 * and equipment reports.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ChokeCollapseResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Flow regime through the choke.
   */
  public enum FlowRegime {
    /** Critical (choked / sonic) flow — mass flow independent of downstream pressure. */
    CRITICAL,
    /** Subcritical flow — mass flow depends on downstream pressure. */
    SUBCRITICAL,
    /** Within the configured margin of the critical pressure ratio. */
    TRANSITION,
    /** Reverse flow (downstream pressure exceeds upstream pressure). */
    REVERSE
  }

  /**
   * Collapse-mode classification.
   */
  public enum CollapseMode {
    /** No collapse risk detected. */
    NONE,
    /** Operating point is within the configured margin of the critical pressure ratio. */
    NEAR_COLLAPSE,
    /** Choked flow has collapsed (regime now subcritical). */
    COLLAPSED,
    /** Liquid flashing across the trim (p2 below vapour pressure). */
    FLASHING,
    /** Cavitation index below threshold — incipient/active cavitation. */
    CAVITATION
  }

  private FlowRegime flowRegime = FlowRegime.SUBCRITICAL;
  private CollapseMode collapseMode = CollapseMode.NONE;
  private double pressureRatio = Double.NaN;
  private double criticalPressureRatio = Double.NaN;
  private double marginToCollapse = Double.NaN;
  private double gamma = Double.NaN;
  private double inletPressureBara = Double.NaN;
  private double outletPressureBara = Double.NaN;
  private double inletTemperatureK = Double.NaN;
  private double massFlowKgPerSec = Double.NaN;
  private double machNumber = Double.NaN;
  private double cavitationIndex = Double.NaN;
  private boolean flashing = false;
  private String fluidPhase = "unknown";
  private final List<String> recommendations = new ArrayList<>();

  /** Default constructor. */
  public ChokeCollapseResult() {}

  /**
   * @return diagnosed flow regime
   */
  public FlowRegime getFlowRegime() {
    return flowRegime;
  }

  /**
   * @param regime flow regime
   */
  public void setFlowRegime(FlowRegime regime) {
    this.flowRegime = regime;
  }

  /**
   * @return collapse mode
   */
  public CollapseMode getCollapseMode() {
    return collapseMode;
  }

  /**
   * @param mode collapse mode
   */
  public void setCollapseMode(CollapseMode mode) {
    this.collapseMode = mode;
  }

  /**
   * @return actual pressure ratio p2/p1
   */
  public double getPressureRatio() {
    return pressureRatio;
  }

  /**
   * @param r pressure ratio p2/p1
   */
  public void setPressureRatio(double r) {
    this.pressureRatio = r;
  }

  /**
   * @return critical pressure ratio r_c
   */
  public double getCriticalPressureRatio() {
    return criticalPressureRatio;
  }

  /**
   * @param rc critical pressure ratio
   */
  public void setCriticalPressureRatio(double rc) {
    this.criticalPressureRatio = rc;
  }

  /**
   * @return signed margin (r_c - r); positive means choked, negative means subcritical
   */
  public double getMarginToCollapse() {
    return marginToCollapse;
  }

  /**
   * @param m margin
   */
  public void setMarginToCollapse(double m) {
    this.marginToCollapse = m;
  }

  /**
   * @return heat capacity ratio used
   */
  public double getGamma() {
    return gamma;
  }

  /**
   * @param g heat capacity ratio
   */
  public void setGamma(double g) {
    this.gamma = g;
  }

  /**
   * @return inlet pressure in bara
   */
  public double getInletPressureBara() {
    return inletPressureBara;
  }

  /**
   * @param p inlet pressure in bara
   */
  public void setInletPressureBara(double p) {
    this.inletPressureBara = p;
  }

  /**
   * @return outlet pressure in bara
   */
  public double getOutletPressureBara() {
    return outletPressureBara;
  }

  /**
   * @param p outlet pressure in bara
   */
  public void setOutletPressureBara(double p) {
    this.outletPressureBara = p;
  }

  /**
   * @return inlet temperature in K
   */
  public double getInletTemperatureK() {
    return inletTemperatureK;
  }

  /**
   * @param t inlet temperature in K
   */
  public void setInletTemperatureK(double t) {
    this.inletTemperatureK = t;
  }

  /**
   * @return mass flow in kg/s
   */
  public double getMassFlowKgPerSec() {
    return massFlowKgPerSec;
  }

  /**
   * @param m mass flow in kg/s
   */
  public void setMassFlowKgPerSec(double m) {
    this.massFlowKgPerSec = m;
  }

  /**
   * @return throat Mach number estimate (1.0 at choke)
   */
  public double getMachNumber() {
    return machNumber;
  }

  /**
   * @param m Mach number
   */
  public void setMachNumber(double m) {
    this.machNumber = m;
  }

  /**
   * @return cavitation index sigma = (p2 - pv) / (p1 - p2); NaN for gas service
   */
  public double getCavitationIndex() {
    return cavitationIndex;
  }

  /**
   * @param s cavitation index
   */
  public void setCavitationIndex(double s) {
    this.cavitationIndex = s;
  }

  /**
   * @return true if liquid is flashing across the trim
   */
  public boolean isFlashing() {
    return flashing;
  }

  /**
   * @param f flashing flag
   */
  public void setFlashing(boolean f) {
    this.flashing = f;
  }

  /**
   * @return fluid phase label ("gas", "liquid", "two-phase", "unknown")
   */
  public String getFluidPhase() {
    return fluidPhase;
  }

  /**
   * @param phase fluid phase label
   */
  public void setFluidPhase(String phase) {
    this.fluidPhase = phase;
  }

  /**
   * @return mutable list of remediation recommendations
   */
  public List<String> getRecommendations() {
    return recommendations;
  }

  /**
   * Convert the result to a JSON document.
   *
   * @return pretty-printed JSON string
   */
  public String toJson() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("flowRegime", flowRegime.name());
    map.put("collapseMode", collapseMode.name());
    map.put("fluidPhase", fluidPhase);
    map.put("inletPressureBara", inletPressureBara);
    map.put("outletPressureBara", outletPressureBara);
    map.put("inletTemperatureK", inletTemperatureK);
    map.put("pressureRatio", pressureRatio);
    map.put("criticalPressureRatio", criticalPressureRatio);
    map.put("marginToCollapse", marginToCollapse);
    map.put("gamma", gamma);
    map.put("massFlowKgPerSec", massFlowKgPerSec);
    map.put("machNumber", machNumber);
    map.put("cavitationIndex", cavitationIndex);
    map.put("flashing", flashing);
    map.put("recommendations", recommendations);
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(map);
  }
}
