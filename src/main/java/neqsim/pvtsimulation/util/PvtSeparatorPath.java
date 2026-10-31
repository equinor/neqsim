package neqsim.pvtsimulation.util;

import java.io.Serializable;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Immutable equilibrium separator path with explicit final product-volume conditions and component accounting.
 *
 * <p>
 * At each stage gas leaves the train and is flashed separately to the final specified conditions. All its products,
 * including condensate or water, are retained. The oil and aqueous phases continue together to the next stage. This is
 * an equilibrium material-accounting protocol, not a separator sizing, efficiency or transient model. The caller
 * supplies a qualified EOS and the phase options needed for the fluid; no water or electrolyte model is substituted.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PvtSeparatorPath implements Serializable {
  private static final long serialVersionUID = 1L;
  private final double[] pressures;
  private final double[] temperatures;
  private final String reference;

  /**
   * Freezes a fixed separator/stock-tank process and its provenance.
   *
   * @param pressuresBara positive strictly decreasing pressures, last is the declared final volume basis
   * @param temperaturesK positive stage temperatures in K, last is the declared final volume basis
   * @param reference protocol, standard conditions, units and acceptance provenance
   */
  public PvtSeparatorPath(double[] pressuresBara, double[] temperaturesK, String reference) {
    if (pressuresBara == null || temperaturesK == null || pressuresBara.length == 0
        || pressuresBara.length != temperaturesK.length) {
      throw new IllegalArgumentException("Equal nonempty pressure and temperature arrays required");
    }
    this.pressures = pressuresBara.clone();
    this.temperatures = temperaturesK.clone();
    this.reference = CalibratedPvtModel.reference(reference);
    for (int i = 0; i < pressures.length; i++) {
      if (!Double.isFinite(pressures[i]) || pressures[i] <= 0.0 || !Double.isFinite(temperatures[i])
          || temperatures[i] <= 0.0 || i > 0 && pressures[i] >= pressures[i - 1]) {
        throw new IllegalArgumentException("Finite positive temperatures and decreasing positive pressures required");
      }
    }
  }

  /**
   * Returns path provenance and declared product conditions.
   *
   * @return protocol reference
   */
  public String getReference() {
    return reference;
  }

  /**
   * Returns pressures including the declared final volume-basis pressure.
   *
   * @return defensive pressure array in bara
   */
  public double[] getPressuresBara() {
    return pressures.clone();
  }

  /**
   * Returns temperatures including the declared final volume-basis temperature.
   *
   * @return defensive temperature array in K
   */
  public double[] getTemperaturesK() {
    return temperatures.clone();
  }

  /**
   * Immutable final-condition phase volumes, native component amounts and protocol provenance.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double[] volumes;
    private final double[][] components;
    private final String[] names;
    private final double pressure;
    private final double temperature;
    private final String reference;

    /**
     * Creates empty accounting on a fixed named EOS basis.
     *
     * @param names EOS component identities
     * @param pressure final volume-basis pressure in bara
     * @param temperature final volume-basis temperature in K
     * @param reference process/basis provenance
     */
    private Result(String[] names, double pressure, double temperature, String reference) {
      this.names = names.clone();
      this.pressure = pressure;
      this.temperature = temperature;
      this.reference = reference;
      volumes = new double[3];
      components = new double[3][names.length];
    }

    /**
     * Returns the final product-volume pressure, retained with serialized results.
     *
     * @return pressure in bara
     */
    public double getFinalPressureBara() {
      return pressure;
    }

    /**
     * Returns the final product-volume temperature, retained with serialized results.
     *
     * @return temperature in K
     */
    public double getFinalTemperatureK() {
      return temperature;
    }

    /**
     * Returns the process/basis reference, retained with serialized results.
     *
     * @return provenance reference
     */
    public String getReference() {
      return reference;
    }

    /**
     * Returns final-condition gas, oil and aqueous volumes, in that order.
     *
     * @return defensive array in m3 at the path's final pressure/temperature
     */
    public double[] getVolumesM3() {
      return volumes.clone();
    }

    /**
     * Returns native component identities in all product arrays.
     *
     * @return defensive component-name array
     */
    public String[] getComponentNames() {
      return names.clone();
    }

    /**
     * Returns final gas/oil/aqueous component amounts with no absent-phase invention.
     *
     * @return phase-by-component mol; absent phases have zero amounts
     */
    public double[][] getComponentMoles() {
      double[][] copy = new double[3][];
      for (int p = 0; p < 3; p++) {
        copy[p] = components[p].clone();
      }
      return copy;
    }

    /**
     * Returns the gas/oil volume ratio on the declared common final basis.
     *
     * @return m3/m3, or NaN when no stock-tank oil is present
     */
    public double getGasOilRatio() {
      return volumes[1] > 0.0 ? volumes[0] / volumes[1] : Double.NaN;
    }

    /**
     * Accounts every final-condition phase, including condensate from offgas.
     *
     * @param fluid independent flashed product
     */
    private void add(SystemInterface fluid) {
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        PhaseInterface phase = fluid.getPhase(p);
        String type = phase.getType().getDesc();
        int index = "gas".equals(type) ? 0 : "oil".equals(type) ? 1 : "aqueous".equals(type) ? 2 : -1;
        double volume = phase.getCorrectedVolume();
        if (index < 0 || !Double.isFinite(volume) || volume <= 0.0) {
          throw new IllegalStateException("Unsupported or nonphysical separator product phase: " + type);
        }
        volumes[index] += volume;
        for (int i = 0; i < names.length; i++) {
          double amount = phase.getNumberOfMolesInPhase() * phase.getComponent(i).getx();
          if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalStateException("Invalid separator product component amount");
          }
          components[index][i] += amount;
        }
      }
    }
  }

  /**
   * Processes a complete mixture without mutating the supplied fluid or its EOS parameters.
   *
   * @param feed qualified EOS mixture, positive total amount; its amount is preserved
   * @return complete gas/oil/aqueous final-condition accounting
   * @throws IllegalStateException for unsupported phases, invalid products or component imbalance
   */
  public Result separate(SystemInterface feed) {
    if (feed == null || !Double.isFinite(feed.getTotalNumberOfMoles()) || feed.getTotalNumberOfMoles() <= 0.0) {
      throw new IllegalArgumentException("Positive finite separator feed required");
    }
    SystemInterface current = SerializationUtils.clone(feed);
    double[] initial = new double[current.getNumberOfComponents()];
    for (int i = 0; i < initial.length; i++) {
      initial[i] = current.getComponent(i).getNumberOfmoles();
      if (!Double.isFinite(initial[i]) || initial[i] < 0.0) {
        throw new IllegalArgumentException("Invalid feed component amount");
      }
    }
    int last = pressures.length - 1;
    Result result = new Result(current.getComponentNames(), pressures[last], temperatures[last], reference);
    for (int stage = 0; stage <= last; stage++) {
      flash(current, pressures[stage], temperatures[stage]);
      for (int p = 0; p < current.getNumberOfPhases(); p++) {
        String type = current.getPhase(p).getType().getDesc();
        if (!"gas".equals(type) && !"oil".equals(type) && !"aqueous".equals(type)) {
          throw new IllegalStateException("Unsupported separator phase: " + type);
        }
      }
      if (stage == last) {
        result.add(current);
        break;
      }
      if (current.hasPhaseType("gas")) {
        SystemInterface gas = SerializationUtils.clone(current).phaseToSystem("gas");
        flash(gas, pressures[last], temperatures[last]);
        result.add(gas);
      }
      boolean oil = current.hasPhaseType("oil");
      boolean water = current.hasPhaseType("aqueous");
      if (!oil && !water) {
        break;
      }
      SystemInterface remaining = SerializationUtils.clone(current);
      current = oil && water
          ? remaining.phaseToSystem(remaining.getPhaseNumberOfPhase("oil"), remaining.getPhaseNumberOfPhase("aqueous"))
          : remaining.phaseToSystem(oil ? "oil" : "aqueous");
    }
    for (int i = 0; i < initial.length; i++) {
      double accounted = result.components[0][i] + result.components[1][i] + result.components[2][i];
      if (!Double.isFinite(accounted) || Math.abs(initial[i] - accounted) > 1.0e-8 * Math.max(1.0, initial[i])) {
        throw new IllegalStateException("Separator native component balance failed for " + result.names[i]);
      }
    }
    for (double volume : result.volumes) {
      if (!Double.isFinite(volume)) {
        throw new IllegalStateException("Non-finite accumulated separator product volume");
      }
    }
    return result;
  }

  /**
   * Equilibrates one independently owned stream with corrected volume/transport properties initialized.
   *
   * @param fluid mutable working stream
   * @param pressure pressure in bara
   * @param temperature temperature in K
   */
  private static void flash(SystemInterface fluid, double pressure, double temperature) {
    fluid.setPressure(pressure);
    fluid.setTemperature(temperature);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
  }
}
