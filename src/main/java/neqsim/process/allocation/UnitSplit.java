package neqsim.process.allocation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Frozen per-component split (recovery) factors for a single conservative process unit.
 *
 * <p>
 * For a non-reactive, mass-conserving unit, the fraction of a component entering the unit that leaves through a given
 * outlet depends only on the mixed inlet state (temperature, pressure and bulk composition), not on which source the
 * moles came from. A {@code UnitSplit} captures those fractions, extracted once from a rigorous base-case run, for
 * every (outlet, component) pair:
 * </p>
 *
 * <p>
 * $$f_u(s,k) = \frac{\dot{n}_{s,k}^{\,out}}{\sum_{i\in inlets} \dot{n}_{i,k}^{\,in}}$$
 * </p>
 *
 * <p>
 * where {@code s} is an outlet stream and {@code k} a component. By component conservation the factors satisfy
 * {@code sum_s f_u(s,k) = 1} for every component {@code k} with non-zero inlet flow. Single-outlet pass-through units
 * (pumps, compressors, coolers, heaters, valves) reduce to the identity factor {@code f = 1}.
 * </p>
 *
 * <p>
 * The factors are linear in the inlet flows, which is what makes the downstream allocation network (see
 * {@link AllocationNetwork}) a linear system that can be solved by superposition.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class UnitSplit implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Name of the process unit these factors belong to. */
  private final String unitName;

  /** Simple class name of the unit (e.g. {@code Separator}, {@code Mixer}). */
  private final String equipmentType;

  /** Inlet streams of the unit (edge carriers in the proxy network). */
  private final List<StreamInterface> inletStreams;

  /** Outlet streams of the unit (edge carriers in the proxy network). */
  private final List<StreamInterface> outletStreams;

  /** Master component names (common slate) the factor columns are indexed by. */
  private final String[] componentNames;

  /** Split factors indexed as {@code factors[outletIndex][componentIndex]}. */
  private final double[][] factors;

  /** Total inlet molar flow per component (mole/sec), for reference and diagnostics. */
  private final double[] inletComponentFlow;

  /**
   * Creates a frozen split-factor record for one unit.
   *
   * @param unitName the unit name; must be non-null
   * @param equipmentType simple class name of the unit; must be non-null
   * @param inletStreams the inlet streams of the unit; must be non-null
   * @param outletStreams the outlet streams of the unit; must be non-null
   * @param componentNames the master component slate the factor columns map to; must be non-null
   * @param factors split factors {@code [outletIndex][componentIndex]}; must be non-null and sized
   * {@code outletStreams.size() x componentNames.length}
   * @param inletComponentFlow total inlet molar flow per component in mole/sec; must be non-null and sized
   * {@code componentNames.length}
   */
  public UnitSplit(String unitName, String equipmentType, List<StreamInterface> inletStreams,
      List<StreamInterface> outletStreams, String[] componentNames, double[][] factors, double[] inletComponentFlow) {
    this.unitName = unitName;
    this.equipmentType = equipmentType;
    this.inletStreams = new ArrayList<>(inletStreams);
    this.outletStreams = new ArrayList<>(outletStreams);
    this.componentNames = componentNames.clone();
    this.factors = factors;
    this.inletComponentFlow = inletComponentFlow.clone();
  }

  /**
   * Gets the unit name.
   *
   * @return the unit name
   */
  public String getUnitName() {
    return unitName;
  }

  /**
   * Gets the simple class name of the unit.
   *
   * @return the equipment type
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Gets the inlet streams of this unit.
   *
   * @return an unmodifiable list of inlet streams
   */
  public List<StreamInterface> getInletStreams() {
    return Collections.unmodifiableList(inletStreams);
  }

  /**
   * Gets the outlet streams of this unit.
   *
   * @return an unmodifiable list of outlet streams
   */
  public List<StreamInterface> getOutletStreams() {
    return Collections.unmodifiableList(outletStreams);
  }

  /**
   * Gets the number of outlet streams.
   *
   * @return the outlet count
   */
  public int getOutletCount() {
    return outletStreams.size();
  }

  /**
   * Gets the master component slate the factor columns are indexed by.
   *
   * @return a copy of the component name array
   */
  public String[] getComponentNames() {
    return componentNames.clone();
  }

  /**
   * Gets the split factor for a given outlet index and component index.
   *
   * @param outletIndex zero-based outlet index in {@link #getOutletStreams()}
   * @param componentIndex zero-based component index in {@link #getComponentNames()}
   * @return the split factor (fraction of the component leaving through that outlet)
   */
  public double getFactor(int outletIndex, int componentIndex) {
    return factors[outletIndex][componentIndex];
  }

  /**
   * Gets the total inlet molar flow of a component (mole/sec) for this unit in the base case.
   *
   * @param componentIndex zero-based component index in {@link #getComponentNames()}
   * @return the total inlet molar flow in mole/sec
   */
  public double getInletComponentFlow(int componentIndex) {
    return inletComponentFlow[componentIndex];
  }
}
