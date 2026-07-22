package neqsim.process.equipment.expander;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * MapTurboExpanderCompressor class.
 *
 * <p>
 * An alternative single-shaft turboexpander-compressor (companding machine) model that couples a real {@link Expander}
 * and a real {@link Compressor} on a common shaft. Unlike {@link TurboExpanderCompressor}, which uses bespoke U/C and
 * Q/N curve-fit correction factors, this model reuses NeqSim's existing compressor performance-map machinery
 * ({@code CompressorChart} or {@code CompressorChartKhader2015}) for the compressor side. The two machines are coupled
 * through a mechanical (shaft/bearing) efficiency and solved so that the shaft power produced by the expander equals
 * the power absorbed by the compressor at a common shaft speed.
 * </p>
 *
 * <p>
 * This is conceptually equivalent to the Atlas Copco Mafi-Trench / River City Engineering EC-OD off-design rating
 * utility for Aspen HYSYS, but built on the open NeqSim chart classes so that the compressor map can account for
 * changing gas composition (via the Mach-number based {@code CompressorChartKhader2015}).
 * </p>
 *
 * <p>
 * Two operating modes are supported:
 * </p>
 * <ul>
 * <li>{@link ShaftMode#SPECIFIED_PRESSURES} &ndash; both the expander outlet pressure and the compressor outlet
 * pressure are specified. The two machines are solved independently and the shaft power balance residual is reported.
 * Useful at the design point.</li>
 * <li>{@link ShaftMode#BALANCED_SPEED} &ndash; rigorous off-design rating. The expander outlet pressure is specified
 * and the compressor uses a performance map. The common shaft speed is solved so that the compressor power equals the
 * shaft power delivered by the expander; the compressor discharge pressure is then an output of the calculation.</li>
 * </ul>
 *
 * @author esol
 * @version $Id: $Id
 */
public class MapTurboExpanderCompressor extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Shaft solution mode for the turboexpander-compressor.
   */
  public enum ShaftMode {
    /** Both outlet pressures specified; power balance residual reported. */
    SPECIFIED_PRESSURES,
    /** Expander outlet pressure specified; common shaft speed solved from power balance. */
    BALANCED_SPEED
  }

  /**
   * Operating status returned after a {@link ShaftMode#BALANCED_SPEED} solution.
   */
  public enum OperatingStatus {
    /** Interior shaft-speed solution found; expander and compressor power balanced. */
    BALANCED,
    /**
     * Expander cannot supply the compressor power even at the minimum map speed. The shaft is pegged at the minimum
     * speed and the compressor operating point has crossed its surge line; in practice the anti-surge / recycle valve
     * must open. This is the low-pressure turndown limit of the machine.
     */
    UNDER_POWER_SURGE,
    /**
     * Expander supplies more power than the compressor can absorb even at the maximum map speed. The shaft is pegged at
     * the maximum speed (over-speed / over-power limit).
     */
    OVER_POWER_MAX_SPEED
  }

  /** Expander side of the machine. */
  private Expander expander;

  /** Brake compressor side of the machine. */
  private Compressor compressor;

  /** Mechanical (shaft transmission) efficiency, fraction (0-1). */
  private double mechanicalEfficiency = 0.98;

  /** Parasitic bearing/seal power loss in Watt. */
  private double bearingLossPower = 0.0;

  /** Selected shaft solution mode. */
  private ShaftMode shaftMode = ShaftMode.SPECIFIED_PRESSURES;

  /** Lower bound for the shaft speed search in RPM (0 = use compressor chart minimum). */
  private double minShaftSpeed = 0.0;

  /** Upper bound for the shaft speed search in RPM (0 = use compressor chart maximum). */
  private double maxShaftSpeed = 0.0;

  /** Solved common shaft speed in RPM. */
  private double shaftSpeed = 0.0;

  /** Shaft power delivered to the compressor in Watt (after mechanical losses). */
  private double availableShaftPower = 0.0;

  /** Compressor absorbed power in Watt. */
  private double consumedCompressorPower = 0.0;

  /** Power balance residual (available - consumed) in Watt. */
  private double powerBalanceResidual = 0.0;

  /** Flag indicating whether the shaft speed was pegged at a search bound. */
  private boolean speedLimited = false;

  /** Operating status after the latest solve. */
  private OperatingStatus operatingStatus = OperatingStatus.BALANCED;

  /**
   * Constructor for MapTurboExpanderCompressor.
   *
   * @param name name of the unit operation
   * @param expanderInletStream high-pressure feed to the expander
   * @param compressorInletStream feed to the brake compressor
   */
  public MapTurboExpanderCompressor(String name, StreamInterface expanderInletStream,
      StreamInterface compressorInletStream) {
    super(name);
    expander = new Expander(name + "_expander", expanderInletStream);
    compressor = new Compressor(name + "_compressor", compressorInletStream);
  }

  /**
   * Get the expander side of the machine.
   *
   * @return the {@link Expander} instance
   */
  public Expander getExpander() {
    return expander;
  }

  /**
   * Get the brake compressor side of the machine.
   *
   * @return the {@link Compressor} instance
   */
  public Compressor getCompressor() {
    return compressor;
  }

  /**
   * Set the expander discharge pressure.
   *
   * @param pressure expander outlet pressure in bara
   */
  public void setExpanderOutletPressure(double pressure) {
    expander.setOutletPressure(pressure);
  }

  /**
   * Set the expander isentropic efficiency.
   *
   * @param efficiency isentropic efficiency as a fraction (0-1)
   */
  public void setExpanderIsentropicEfficiency(double efficiency) {
    expander.setIsentropicEfficiency(efficiency);
  }

  /**
   * Set the compressor discharge pressure. Only used in {@link ShaftMode#SPECIFIED_PRESSURES} mode; in
   * {@link ShaftMode#BALANCED_SPEED} mode the compressor discharge pressure is an output.
   *
   * @param pressure compressor outlet pressure in bara
   */
  public void setCompressorOutletPressure(double pressure) {
    compressor.setOutletPressure(pressure);
  }

  /**
   * Set the mechanical (shaft transmission) efficiency.
   *
   * @param efficiency mechanical efficiency as a fraction (0-1)
   */
  public void setMechanicalEfficiency(double efficiency) {
    this.mechanicalEfficiency = efficiency;
  }

  /**
   * Get the mechanical (shaft transmission) efficiency.
   *
   * @return mechanical efficiency as a fraction (0-1)
   */
  public double getMechanicalEfficiency() {
    return mechanicalEfficiency;
  }

  /**
   * Set the parasitic bearing/seal power loss.
   *
   * @param powerWatt bearing loss in Watt (greater than or equal to 0)
   */
  public void setBearingLossPower(double powerWatt) {
    this.bearingLossPower = powerWatt;
  }

  /**
   * Get the parasitic bearing/seal power loss.
   *
   * @return bearing loss in Watt
   */
  public double getBearingLossPower() {
    return bearingLossPower;
  }

  /**
   * Set the shaft solution mode.
   *
   * @param mode the {@link ShaftMode} to use
   */
  public void setShaftMode(ShaftMode mode) {
    this.shaftMode = mode;
  }

  /**
   * Get the shaft solution mode.
   *
   * @return the active {@link ShaftMode}
   */
  public ShaftMode getShaftMode() {
    return shaftMode;
  }

  /**
   * Set the lower and upper bounds for the shaft speed search in {@link ShaftMode#BALANCED_SPEED} mode.
   *
   * @param minSpeed lower bound in RPM (0 = use compressor chart minimum)
   * @param maxSpeed upper bound in RPM (0 = use compressor chart maximum)
   */
  public void setShaftSpeedBounds(double minSpeed, double maxSpeed) {
    this.minShaftSpeed = minSpeed;
    this.maxShaftSpeed = maxSpeed;
  }

  /**
   * Get the solved common shaft speed.
   *
   * @return shaft speed in RPM
   */
  public double getShaftSpeed() {
    return shaftSpeed;
  }

  /**
   * Get the shaft power delivered to the compressor (after mechanical losses).
   *
   * @return available shaft power in Watt
   */
  public double getAvailableShaftPower() {
    return availableShaftPower;
  }

  /**
   * Get the compressor absorbed power.
   *
   * @return consumed compressor power in Watt
   */
  public double getConsumedCompressorPower() {
    return consumedCompressorPower;
  }

  /**
   * Get the shaft power balance residual (available minus consumed). A value close to zero in
   * {@link ShaftMode#BALANCED_SPEED} mode indicates a converged solution.
   *
   * @return power balance residual in Watt
   */
  public double getPowerBalanceResidual() {
    return powerBalanceResidual;
  }

  /**
   * Indicates whether the shaft speed was pegged at one of the search bounds (i.e. the power balance could not be
   * satisfied within the allowed speed range).
   *
   * @return {@code true} if the shaft speed was limited by a bound
   */
  public boolean isSpeedLimited() {
    return speedLimited;
  }

  /**
   * Get the operating status from the latest {@link ShaftMode#BALANCED_SPEED} solution.
   *
   * @return the {@link OperatingStatus}
   */
  public OperatingStatus getOperatingStatus() {
    return operatingStatus;
  }

  /**
   * Indicates whether the machine has a physically feasible balanced operating point. In
   * {@link ShaftMode#BALANCED_SPEED} mode the machine is feasible only when an interior shaft-speed solution exists
   * ({@link OperatingStatus#BALANCED}); an {@link OperatingStatus#UNDER_POWER_SURGE} status corresponds to the
   * low-pressure turndown / surge limit where the brake compressor would have to recycle. In
   * {@link ShaftMode#SPECIFIED_PRESSURES} mode the result is always considered feasible (the residual is reported
   * separately).
   *
   * @return {@code true} if a balanced operating point was found
   */
  public boolean isFeasible() {
    if (shaftMode == ShaftMode.SPECIFIED_PRESSURES) {
      return true;
    }
    return operatingStatus == OperatingStatus.BALANCED;
  }

  /**
   * Get the expander outlet stream.
   *
   * @return the cold expander discharge stream
   */
  public StreamInterface getExpanderOutStream() {
    return expander.getOutletStream();
  }

  /**
   * Get the compressor outlet stream.
   *
   * @return the compressor discharge stream
   */
  public StreamInterface getCompressorOutStream() {
    return compressor.getOutletStream();
  }

  /**
   * Build a map of the key machine results for reporting and serialization.
   *
   * @return an ordered map of result names to values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", getName());
    map.put("shaftMode", shaftMode.toString());
    map.put("operatingStatus", operatingStatus.toString());
    map.put("feasible", Boolean.valueOf(isFeasible()));
    map.put("speedLimited", Boolean.valueOf(speedLimited));
    map.put("shaftSpeed_rpm", Double.valueOf(shaftSpeed));
    map.put("mechanicalEfficiency", Double.valueOf(mechanicalEfficiency));
    map.put("bearingLossPower_W", Double.valueOf(bearingLossPower));
    map.put("availableShaftPower_MW", Double.valueOf(availableShaftPower / 1.0e6));
    map.put("consumedCompressorPower_MW", Double.valueOf(consumedCompressorPower / 1.0e6));
    map.put("powerBalanceResidual_MW", Double.valueOf(powerBalanceResidual / 1.0e6));
    map.put("expanderInletPressure_bara", Double.valueOf(expander.getInletStream().getPressure()));
    map.put("expanderOutletPressure_bara", Double.valueOf(expander.getOutletStream().getPressure()));
    map.put("expanderOutletTemperature_C", Double.valueOf(expander.getOutletStream().getTemperature("C")));
    map.put("compressorInletPressure_bara", Double.valueOf(compressor.getInletStream().getPressure()));
    map.put("compressorOutletPressure_bara", Double.valueOf(compressor.getOutletStream().getPressure()));
    map.put("compressorPolytropicEfficiency", Double.valueOf(compressor.getPolytropicEfficiency()));
    return map;
  }

  /**
   * Serialize the key machine results to a pretty-printed JSON string.
   *
   * @return JSON representation of the machine results
   */
  @Override
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }

  /**
   * Compute the compressor absorbed power at a given shaft speed using the compressor performance map.
   *
   * @param speed shaft speed in RPM
   * @param id calculation identifier
   * @return compressor absorbed power in Watt
   */
  private double compressorPowerAtSpeed(double speed, UUID id) {
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.setSolveSpeed(false);
    compressor.setCalcPressureOut(true);
    compressor.setSpeed(speed);
    compressor.run(id);
    return compressor.getPower();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // 1. Run the expander to obtain available shaft power.
    expander.run(id);
    double expanderPower = Math.abs(expander.getPower());
    availableShaftPower = expanderPower * mechanicalEfficiency - bearingLossPower;
    if (availableShaftPower < 0.0) {
      availableShaftPower = 0.0;
    }
    speedLimited = false;
    operatingStatus = OperatingStatus.BALANCED;

    if (shaftMode == ShaftMode.BALANCED_SPEED) {
      double sLow = minShaftSpeed > 0.0 ? minShaftSpeed : compressor.getCompressorChart().getMinSpeedCurve();
      double sHigh = maxShaftSpeed > 0.0 ? maxShaftSpeed : compressor.getCompressorChart().getMaxSpeedCurve();

      double fLow = compressorPowerAtSpeed(sLow, id) - availableShaftPower;
      double fHigh = compressorPowerAtSpeed(sHigh, id) - availableShaftPower;

      if (fLow > 0.0) {
        // Expander cannot supply enough power even at the lowest speed.
        shaftSpeed = sLow;
        speedLimited = true;
        operatingStatus = OperatingStatus.UNDER_POWER_SURGE;
      } else if (fHigh < 0.0) {
        // Surplus power available; speed pegged at the upper bound.
        shaftSpeed = sHigh;
        speedLimited = true;
        operatingStatus = OperatingStatus.OVER_POWER_MAX_SPEED;
      } else {
        // Bisection on the monotonically increasing power-speed relationship.
        double a = sLow;
        double b = sHigh;
        for (int iter = 0; iter < 80; iter++) {
          double mid = 0.5 * (a + b);
          double fMid = compressorPowerAtSpeed(mid, id) - availableShaftPower;
          if (fMid > 0.0) {
            b = mid;
          } else {
            a = mid;
          }
          if (Math.abs(b - a) < 1.0e-3) {
            break;
          }
        }
        shaftSpeed = 0.5 * (a + b);
      }

      // Final run at the solved (or pegged) shaft speed.
      consumedCompressorPower = compressorPowerAtSpeed(shaftSpeed, id);
      expander.setSpeed(shaftSpeed);
    } else {
      // Specified pressures: solve compressor independently and report the residual.
      compressor.run(id);
      consumedCompressorPower = compressor.getPower();
      shaftSpeed = compressor.getSpeed();
    }

    powerBalanceResidual = availableShaftPower - consumedCompressorPower;
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> inlets = new ArrayList<StreamInterface>();
    inlets.add(expander.getInletStream());
    inlets.add(compressor.getInletStream());
    return inlets;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outlets = new ArrayList<StreamInterface>();
    outlets.add(expander.getOutletStream());
    outlets.add(compressor.getOutletStream());
    return outlets;
  }
}
