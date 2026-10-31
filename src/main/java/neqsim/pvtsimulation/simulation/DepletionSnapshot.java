package neqsim.pvtsimulation.simulation;

import java.io.Serializable;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.thermo.system.SystemInterface;

/**
 * Immutable native EOS evidence for one CVD or DLE pressure step.
 *
 * <p>
 * Equilibrium is the state used to interpret phase PVT; retained is the state after the step's gas withdrawal. CVD
 * records its equilibrated, volume-restored cell as both states. DLE records equilibrium before removing gas, and the
 * remaining liquid afterwards. Component withdrawals are differences of consecutive retained inventories, including the
 * original sample for the first step. Recording is opt-in in the existing solvers.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DepletionSnapshot implements Serializable {
  private static final long serialVersionUID = 1L;
  private final byte[] equilibrium;
  private final byte[] retained;
  private final String[] components;
  private final double[] produced;

  /**
   * Captures a completed step without reflashing either supplied state.
   *
   * @param equilibrium equilibrium state used for oil/gas PVT
   * @param retained retained fluid after gas withdrawal
   * @param previous previous retained component inventories, in the same named order
   */
  DepletionSnapshot(SystemInterface equilibrium, SystemInterface retained, double[] previous) {
    if (equilibrium == null || retained == null || previous == null
        || previous.length != retained.getNumberOfComponents()) {
      throw new IllegalArgumentException("Complete equilibrium, retained and previous component evidence required");
    }
    this.equilibrium = SerializationUtils.serialize(equilibrium);
    this.retained = SerializationUtils.serialize(retained);
    this.components = retained.getComponentNames().clone();
    this.produced = new double[previous.length];
    for (int i = 0; i < produced.length; i++) {
      double current = retained.getComponent(i).getNumberOfmoles();
      double amount = previous[i] - current;
      if (!Double.isFinite(current) || current < 0.0 || !Double.isFinite(amount)
          || amount < -1.0e-8 * Math.max(1.0, previous[i])) {
        throw new IllegalStateException("Depletion created a component inventory at index " + i);
      }
      produced[i] = Math.max(0.0, amount);
    }
  }

  /**
   * Returns an independent native equilibrium EOS state at this pressure.
   *
   * @return restored equilibrium state, before gas removal for DLE
   */
  public SystemInterface getEquilibriumFluid() {
    return SerializationUtils.deserialize(equilibrium);
  }

  /**
   * Returns an independent native EOS cell after the step's gas removal.
   *
   * @return restored retained cell
   */
  public SystemInterface getRetainedFluid() {
    return SerializationUtils.deserialize(retained);
  }

  /**
   * Returns the common named order for produced component amounts.
   *
   * @return component names in original solver order
   */
  public String[] getComponents() {
    return components.clone();
  }

  /**
   * Returns measured-by-difference withdrawal for this interval, not a surface volume.
   *
   * @return nonnegative withdrawn component amounts in mol
   */
  public double[] getProducedComponentMoles() {
    return produced.clone();
  }

  /**
   * Captures named component amounts for the next step's balance.
   *
   * @param fluid current retained cell
   * @return complete component amounts in solver order, mol
   */
  static double[] inventory(SystemInterface fluid) {
    double[] amounts = new double[fluid.getNumberOfComponents()];
    for (int i = 0; i < amounts.length; i++) {
      amounts[i] = fluid.getComponent(i).getNumberOfmoles();
    }
    return amounts;
  }
}
