package neqsim.process.equipment.pump;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Sucker-rod (beam) pump artificial-lift model.
 *
 * <p>
 * A sucker-rod pump is a positive-displacement plunger pump driven by a surface beam unit through a rod string. Its
 * delivered rate is set by the swept volume per stroke and the pumping speed, not by upstream pressure. This class
 * implements the standard volumetric-displacement sizing used in artificial-lift design.
 * </p>
 *
 * <p>
 * Theoretical displacement: q_th = A_plunger &middot; S &middot; N &middot; &eta;_v, where A_plunger is the plunger
 * cross-sectional area, S the effective stroke length, N the pumping speed (strokes per minute) and &eta;_v the
 * volumetric efficiency (slippage, gas interference and fillage). A simplified polished-rod load is also provided for
 * beam-unit sizing.
 * </p>
 *
 * <p>
 * In {@link #run(UUID)} the device acts as a pressure booster to the configured discharge pressure while exposing the
 * displacement and rod-load diagnostics.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see Pump
 */
public class SuckerRodPump extends Pump {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SuckerRodPump.class);

  private double plungerDiameter = 0.0381; // m (1.5 in)
  private double strokeLength = 1.5; // m
  private double strokesPerMinute = 8.0; // SPM
  private double volumetricEfficiency = 0.80; // fraction
  private double rodWeightPerLength = 16.0; // kg/m (rod string in fluid)
  private double pumpDepth = 1500.0; // m
  private double fluidDensity = 900.0; // kg/m3
  private double dischargePressureBara = 0.0;

  // Operating state
  private double theoreticalDisplacement = 0.0; // m3/s
  private double actualDisplacement = 0.0; // m3/s
  private double polishedRodLoad = 0.0; // N

  /**
   * Constructor for SuckerRodPump.
   *
   * @param name name of sucker-rod pump
   */
  public SuckerRodPump(String name) {
    super(name);
  }

  /**
   * Constructor for SuckerRodPump.
   *
   * @param name name of sucker-rod pump
   * @param inletStream inlet (pump intake) stream
   */
  public SuckerRodPump(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Sets the plunger diameter.
   *
   * @param plungerDiameterM plunger diameter in m
   */
  public void setPlungerDiameter(double plungerDiameterM) {
    this.plungerDiameter = plungerDiameterM;
  }

  /**
   * Sets the effective stroke length.
   *
   * @param strokeLengthM stroke length in m
   */
  public void setStrokeLength(double strokeLengthM) {
    this.strokeLength = strokeLengthM;
  }

  /**
   * Sets the pumping speed.
   *
   * @param spm strokes per minute
   */
  public void setStrokesPerMinute(double spm) {
    this.strokesPerMinute = spm;
  }

  /**
   * Sets the volumetric efficiency (slippage, gas interference, fillage).
   *
   * @param volumetricEfficiency efficiency fraction (0..1)
   */
  public void setVolumetricEfficiency(double volumetricEfficiency) {
    this.volumetricEfficiency = volumetricEfficiency;
  }

  /**
   * Sets the rod-string weight per unit length (buoyant) for rod-load estimation.
   *
   * @param rodWeightPerLengthKgPerM rod weight in kg/m
   */
  public void setRodWeightPerLength(double rodWeightPerLengthKgPerM) {
    this.rodWeightPerLength = rodWeightPerLengthKgPerM;
  }

  /**
   * Sets the pump setting depth.
   *
   * @param pumpDepthM pump depth in m
   */
  public void setPumpDepth(double pumpDepthM) {
    this.pumpDepth = pumpDepthM;
  }

  /**
   * Sets the produced-fluid density used for the rod-load estimate.
   *
   * @param fluidDensityKgPerM3 fluid density in kg/m3
   */
  public void setFluidDensity(double fluidDensityKgPerM3) {
    this.fluidDensity = fluidDensityKgPerM3;
  }

  /**
   * Sets the pump discharge pressure.
   *
   * @param dischargePressureBara discharge pressure in bara
   */
  public void setDischargePressure(double dischargePressureBara) {
    this.dischargePressureBara = dischargePressureBara;
  }

  /**
   * Returns the plunger cross-sectional area.
   *
   * @return plunger area in m2
   */
  public double getPlungerArea() {
    return 0.25 * Math.PI * plungerDiameter * plungerDiameter;
  }

  /**
   * Returns the theoretical (100% fillage) displacement.
   *
   * @param unit "m3/sec" or "m3/day"
   * @return theoretical displacement in the requested unit
   */
  public double getTheoreticalDisplacement(String unit) {
    double qps = getPlungerArea() * strokeLength * (strokesPerMinute / 60.0);
    if ("m3/day".equals(unit)) {
      return qps * 86400.0;
    }
    return qps;
  }

  /**
   * Returns the actual displacement including volumetric efficiency.
   *
   * @param unit "m3/sec" or "m3/day"
   * @return actual displacement in the requested unit
   */
  public double getActualDisplacement(String unit) {
    return getTheoreticalDisplacement(unit) * volumetricEfficiency;
  }

  /**
   * Returns the most recent estimated polished-rod load.
   *
   * @return polished-rod load in N
   */
  public double getPolishedRodLoad() {
    return polishedRodLoad;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Computes the displacement and rod load, then boosts the inlet stream to the configured discharge pressure.
   * </p>
   */
  @Override
  public void run(UUID id) {
    theoreticalDisplacement = getTheoreticalDisplacement("m3/sec");
    actualDisplacement = getActualDisplacement("m3/sec");
    double g = 9.80665;
    double rodWeight = rodWeightPerLength * pumpDepth * g;
    double fluidLoad = getPlungerArea() * pumpDepth * fluidDensity * g;
    polishedRodLoad = rodWeight + fluidLoad;
    double suctionPressure = getInletStream().getPressure("bara");
    double outP = dischargePressureBara > 0.0 ? dischargePressureBara : suctionPressure;
    if (outP < suctionPressure) {
      outP = suctionPressure;
    }
    setOutletPressure(outP, "bara");
    super.run(id);
  }
}
