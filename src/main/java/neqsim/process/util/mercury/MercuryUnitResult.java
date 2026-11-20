package neqsim.process.util.mercury;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.thermo.util.mercury.MercuryPhaseEquilibrium;

/**
 * Mercury balance of one unit operation: mercury entering, leaving and staying in the unit.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class MercuryUnitResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Name of the unit operation. */
  private final String name;

  /** Class name of the unit operation. */
  private final String type;

  /** Molar mass of mercury in kg/mol. */
  private final double mercuryMolarMass;

  /** Mercury entering with the inlet streams in mole/sec. */
  private final double inletMercuryMoles;

  /** Mercury leaving with the outlet streams in mole/sec. */
  private final double outletMercuryMoles;

  /** Free mercury held back in a separator in mole/sec. */
  private final double freeMercuryMoles;

  /** State of the free mercury held back in a separator: none, liquid or solid. */
  private final String freeMercuryState;

  /**
   * Create the mercury balance of a unit operation.
   *
   * @param name name of the unit operation
   * @param type class name of the unit operation
   * @param mercuryMolarMass molar mass of mercury in kg/mol
   * @param flows mercury entering, leaving and held back as free mercury, in mole/sec
   * @param freeMercuryState state of the free mercury held back: none, liquid or solid
   */
  MercuryUnitResult(String name, String type, double mercuryMolarMass, double[] flows, String freeMercuryState) {
    this.name = name;
    this.type = type;
    this.mercuryMolarMass = mercuryMolarMass;
    this.inletMercuryMoles = flows[0];
    this.outletMercuryMoles = flows[1];
    this.freeMercuryMoles = flows[2];
    this.freeMercuryState = freeMercuryState;
  }

  /**
   * Get the name of the unit operation.
   *
   * @return unit operation name
   */
  public String getName() {
    return name;
  }

  /**
   * Get the type of the unit operation.
   *
   * @return simple class name, for example "Separator"
   */
  public String getType() {
    return type;
  }

  /**
   * Get the mercury entering the unit with its inlet streams.
   *
   * @param unit "mole/sec", "mole/hr", "kg/sec", "kg/min", "kg/hr", "kg/day", "kg/yr", "g/sec", "g/hr", "g/day" or
   * "g/yr"
   * @return flow of mercury
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getInletMercuryFlowRate(String unit) {
    return MercuryPhaseEquilibrium.convertMercuryFlow(inletMercuryMoles, mercuryMolarMass, unit);
  }

  /**
   * Get the mercury leaving the unit with its outlet streams.
   *
   * @param unit flow unit, see {@link #getInletMercuryFlowRate(String)}
   * @return flow of mercury
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getOutletMercuryFlowRate(String unit) {
    return MercuryPhaseEquilibrium.convertMercuryFlow(outletMercuryMoles, mercuryMolarMass, unit);
  }

  /**
   * Get the mercury that stays in the unit.
   *
   * @param unit flow unit, see {@link #getInletMercuryFlowRate(String)}
   * @return mercury entering minus mercury leaving: free mercury held back in a separator, mercury taken up by a
   * removal bed, or the remaining difference over a recycle that is not converged
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getRetainedMercuryFlowRate(String unit) {
    return MercuryPhaseEquilibrium.convertMercuryFlow(inletMercuryMoles - outletMercuryMoles, mercuryMolarMass, unit);
  }

  /**
   * Get the free mercury held back in a separator.
   *
   * @param unit flow unit, see {@link #getInletMercuryFlowRate(String)}
   * @return accumulation rate of free mercury, zero for other units and for separators without free mercury
   * accumulation
   * @throws IllegalArgumentException if the unit is not supported
   */
  public double getFreeMercuryAccumulationRate(String unit) {
    return MercuryPhaseEquilibrium.convertMercuryFlow(freeMercuryMoles, mercuryMolarMass, unit);
  }

  /**
   * Get the state of the free mercury held back in a separator.
   *
   * @return "none", "liquid" or "solid"
   */
  public String getFreeMercuryState() {
    return freeMercuryState;
  }

  /**
   * Get the result as a map for reporting.
   *
   * @return ordered map with flows in g/day
   */
  Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("unit", name);
    map.put("type", type);
    map.put("mercuryIn_g_day", getInletMercuryFlowRate("g/day"));
    map.put("mercuryOut_g_day", getOutletMercuryFlowRate("g/day"));
    map.put("mercuryRetained_g_day", getRetainedMercuryFlowRate("g/day"));
    map.put("freeMercuryAccumulation_g_day", getFreeMercuryAccumulationRate("g/day"));
    map.put("freeMercuryState", freeMercuryState);
    return map;
  }
}
