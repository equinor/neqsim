package neqsim.pvtsimulation.util;

import java.io.Serializable;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.blackoil.BlackOilPVTTable.Record;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.GeneralizedMaterialBalance;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.pvtsimulation.util.MaterialBalancePvtHistory.Path;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Evaluates modified black-oil PVT from the actual CVD or DLE depletion path and one fixed separator process.
 *
 * <p>
 * The Whitson-Torp phase-flash interpretation is an approximation to full component conservation. This API checks
 * native component conservation separately, evaluates its surface oil/gas closure error, and refuses a handoff above
 * the caller's justified acceptance tolerance. It never substitutes independent original-composition flashes for a
 * depletion experiment. The supported grid has two hydrocarbon phases at every interpreted point. Water-containing
 * depletion samples, absent-phase extrapolation, critical closure, injection paths and separator changes are rejected
 * or outside this bounded model. Bw comes from a separately accepted pure-water EOS, not an invented default.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DepletionPvtHistoryGenerator {

  /** Prevents construction of this stateless evaluator. */
  private DepletionPvtHistoryGenerator() {
  }

  /** Immutable protocol and approximation evidence accompanying an accepted PVT handoff. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final MaterialBalancePvtHistory history;
    private final DepletionSnapshot[] snapshots;
    private final double[][] surfaceResidual;
    private final double[][] cumulativeProduction;
    private final double maximumError;
    private final double tolerance;
    private final String toleranceReference;
    private final String waterReference;
    private final byte[] waterModel;

    /**
     * Freezes accepted conversion evidence.
     *
     * @param history accepted, pressure-aligned PVT handoff
     * @param snapshots native depletion evidence
     * @param surfaceResidual signed oil/gas pseudo-component closure errors relative to initial surface inventories
     * @param cumulativeProduction cumulative oil/gas production relative to the first retained state, Sm3
     * @param maximumError largest absolute relative pseudo-component closure error
     * @param tolerance caller-supplied error acceptance bound
     * @param toleranceReference engineering justification of this bound
     * @param waterReference independent water-model acceptance provenance
     * @param waterModel accepted independent water EOS to freeze
     */
    private Result(MaterialBalancePvtHistory history, DepletionSnapshot[] snapshots, double[][] surfaceResidual,
        double[][] cumulativeProduction, double maximumError, double tolerance, String toleranceReference,
        String waterReference, SystemInterface waterModel) {
      this.history = history;
      this.snapshots = snapshots.clone();
      this.surfaceResidual = copy(surfaceResidual);
      this.cumulativeProduction = copy(cumulativeProduction);
      this.maximumError = maximumError;
      this.tolerance = tolerance;
      this.toleranceReference = toleranceReference;
      this.waterReference = waterReference;
      this.waterModel = SerializationUtils.serialize(waterModel);
    }

    /**
     * Returns the accepted generalized-PVT handoff with immutable original/tuned model provenance.
     *
     * @return common-basis pressure-path handoff
     */
    public MaterialBalancePvtHistory getHistory() {
      return history;
    }

    /**
     * Returns native per-step phase states and component withdrawals, not a constant-composition surrogate.
     *
     * @return immutable snapshots in pressure order
     */
    public DepletionSnapshot[] getSnapshots() {
      return snapshots.clone();
    }

    /**
     * Returns signed relative pseudo-component closure errors; columns are surface oil and surface gas.
     *
     * @return (initial minus cumulative produced minus remaining) / initial surface inventory
     */
    public double[][] getRelativeSurfaceClosureResiduals() {
      return copy(surfaceResidual);
    }

    /**
     * Returns separator-evaluated laboratory withdrawals relative to the first retained cell.
     *
     * <p>
     * These are experiment outputs, not a measured reservoir production history. In particular, the DLE cell changes
     * volume and must not be mistaken for a fixed-volume reservoir.
     * </p>
     *
     * @return cumulative oil/gas experiment withdrawals in Sm3, two columns
     */
    public double[][] getCumulativeSurfaceProductionSm3() {
      return copy(cumulativeProduction);
    }

    /**
     * Returns the maximum absolute relative oil/gas pseudo-component closure error.
     *
     * @return finite approximation error, not a native component-conservation error
     */
    public double getMaximumRelativeSurfaceClosureError() {
      return maximumError;
    }

    /**
     * Returns the supplied maximum permitted relative surface-component error.
     *
     * @return engineering acceptance bound, not statistical uncertainty
     */
    public double getAcceptanceTolerance() {
      return tolerance;
    }

    /**
     * Returns the caller's reason for accepting the approximation error.
     *
     * @return tolerance justification reference
     */
    public String getToleranceReference() {
      return toleranceReference;
    }

    /**
     * Returns the separately accepted pure-water EOS provenance used to evaluate Bw.
     *
     * @return independent water-model reference
     */
    public String getWaterReference() {
      return waterReference;
    }

    /**
     * Restores an independent copy of the accepted pure-water EOS used for Bw.
     *
     * @return separately frozen pure-water model
     */
    public SystemInterface getWaterFluid() {
      return SerializationUtils.deserialize(waterModel);
    }
  }

  /**
   * Replays an accepted dry hydrocarbon sample through CVD or DLE and evaluates one unchanged separator process.
   *
   * <p>
   * All supplied pressures must strictly decrease and exceed the first separator pressure. Every grid point must
   * contain both oil and gas, including the first interpreted point. The first retained cell defines the zero origin of
   * the reported experiment withdrawals; any earlier sample-to-grid withdrawal remains visible in snapshot zero.
   * Equilibrium phase volumes use the EOS volume correction and surface gas is the dry gas phase after the complete
   * process, including any stock-tank condensate from each separator's offgas.
   * </p>
   *
   * @param model immutable original/tuned laboratory-calibration bundle
   * @param path CVD or DLE; other path types are unsupported
   * @param pathReference software/protocol evaluation reference
   * @param temperatureK fixed reservoir temperature in K
   * @param pressuresBara complete decreasing experiment grid, at least three two-phase points
   * @param separatorPressuresBara fixed decreasing separator pressures ending at 1.01325 bara
   * @param separatorTemperaturesK stage temperatures ending at 288.15 K
   * @param waterFluid separately accepted pure-water EOS, containing water only
   * @param waterReference provenance and acceptance of the water EOS at these conditions
   * @param maximumRelativeSurfaceError positive acceptance bound below one for both surface pseudo-components
   * @param toleranceReference engineering justification of the approximation bound
   * @return accepted PVT, native snapshots, experiment withdrawals and approximation diagnostics
   * @throws IllegalStateException if phase availability, conservation, flashes or approximation acceptance fails
   */
  public static Result generate(CalibratedPvtModel model, Path path, String pathReference, double temperatureK,
      double[] pressuresBara, double[] separatorPressuresBara, double[] separatorTemperaturesK,
      SystemInterface waterFluid, String waterReference, double maximumRelativeSurfaceError,
      String toleranceReference) {
    if (model == null || (path != Path.CVD && path != Path.DLE) || pressuresBara == null || pressuresBara.length < 3
        || !positive(temperatureK) || !positive(maximumRelativeSurfaceError) || maximumRelativeSurfaceError >= 1.0) {
      throw new IllegalArgumentException(
          "Model, CVD/DLE, positive temperature, three pressures and tolerance in (0,1) required");
    }
    MaterialBalancePvtHistory.requireSeparatorBasis(separatorPressuresBara, separatorTemperaturesK);
    String reference = CalibratedPvtModel.reference(pathReference);
    String waterSource = CalibratedPvtModel.reference(waterReference);
    String acceptanceSource = CalibratedPvtModel.reference(toleranceReference);
    double[] pressures = pressuresBara.clone();
    for (int i = 0; i < pressures.length; i++) {
      if (!positive(pressures[i]) || pressures[i] <= separatorPressuresBara[0]
          || (i > 0 && pressures[i] >= pressures[i - 1])) {
        throw new IllegalArgumentException("Depletion pressures must decrease strictly above the separator process");
      }
    }
    SystemInterface water = copy(waterFluid);
    if (water.getNumberOfComponents() != 1 || !"water".equals(water.getComponentNames()[0])) {
      throw new IllegalArgumentException("Bw reference must be a separately accepted pure-water EOS");
    }
    SystemInterface feed = model.getTunedFluid();
    for (String name : feed.getComponentNames()) {
      if ("water".equals(name)) {
        throw new IllegalArgumentException(
            "Water-containing depletion samples require a separately qualified protocol");
      }
    }
    double[] initialComponents = inventory(feed);
    DepletionSnapshot[] snapshots = replay(feed, path, temperatureK, pressures);
    checkComponentClosure(snapshots, initialComponents);
    Record[] records = new Record[pressures.length];
    double[][] closure = new double[pressures.length][2];
    double[][] produced = new double[pressures.length][2];
    double[] cumulative = new double[2];
    double[] originalSurface = null;
    double maximumError = 0.0;
    for (int row = 0; row < records.length; row++) {
      SystemInterface state = snapshots[row].getEquilibriumFluid();
      checkHydrocarbonPhases(state, row);
      state.initProperties();
      Surface oil = separate(extract(state, state.getPhaseNumberOfPhase("oil")), separatorPressuresBara,
          separatorTemperaturesK);
      Surface gas = separate(extract(state, state.getPhaseNumberOfPhase("gas")), separatorPressuresBara,
          separatorTemperaturesK);
      if (!positive(oil.oil) || !positive(gas.gas)) {
        throw new IllegalStateException("Separator oil/gas component basis is undefined at row " + row);
      }
      water.setTemperature(temperatureK);
      water.setPressure(pressures[row]);
      flash(water);
      requireWaterLiquid(water);
      double waterDensity = water.getDensity("kg/m3");
      double waterViscosity = water.getPhase(0).getPhysicalProperties().getViscosity();
      water.setTemperature(288.15);
      water.setPressure(1.01325);
      flash(water);
      requireWaterLiquid(water);
      double bw = water.getDensity("kg/m3") / waterDensity;
      records[row] = new Record(pressures[row], oil.gas / oil.oil, state.getPhase("oil").getCorrectedVolume() / oil.oil,
          state.getPhase("oil").getPhysicalProperties().getViscosity(),
          state.getPhase("gas").getCorrectedVolume() / gas.gas,
          state.getPhase("gas").getPhysicalProperties().getViscosity(), gas.oil / gas.gas, bw, waterViscosity);
      new GeneralizedMaterialBalance(records[row]);
      if (!positive(records[row].mu_o) || !positive(records[row].mu_g) || !positive(records[row].mu_w)) {
        throw new IllegalStateException("Nonphysical PVT transport property at row " + row);
      }
      SystemInterface retained = snapshots[row].getRetainedFluid();
      Surface remaining = separateExistingPhases(retained, separatorPressuresBara, separatorTemperaturesK);
      if (row == 0) {
        originalSurface = new double[] {remaining.oil, remaining.gas};
        if (!positive(originalSurface[0]) || !positive(originalSurface[1])) {
          throw new IllegalStateException("Initial retained cell must define both surface components");
        }
      } else {
        double[] amounts = snapshots[row].getProducedComponentMoles();
        double total = 0.0;
        for (double amount : amounts) {
          total += amount;
        }
        if (total > 0.0) {
          SystemInterface withdrawn = model.getTunedFluid();
          withdrawn.setTotalNumberOfMoles(total);
          double[] fractions = amounts.clone();
          for (int i = 0; i < fractions.length; i++) {
            fractions[i] /= total;
          }
          withdrawn.setMolarComposition(fractions);
          Surface output = separate(withdrawn, separatorPressuresBara, separatorTemperaturesK);
          cumulative[0] += output.oil;
          cumulative[1] += output.gas;
        }
      }
      double[] remainingVolumes = {remaining.oil, remaining.gas};
      for (int component = 0; component < 2; component++) {
        produced[row][component] = cumulative[component];
        closure[row][component] = (originalSurface[component] - cumulative[component] - remainingVolumes[component])
            / originalSurface[component];
        maximumError = Math.max(maximumError, Math.abs(closure[row][component]));
      }
    }
    if (!Double.isFinite(maximumError) || maximumError > maximumRelativeSurfaceError) {
      throw new IllegalStateException("Modified black-oil surface-component closure error " + maximumError
          + " exceeds justified tolerance " + maximumRelativeSurfaceError);
    }
    String evidence = reference + "; native " + path + " snapshots; fixed multistage separator; pure-water EOS: "
        + waterSource + "; surface-component tolerance " + maximumRelativeSurfaceError + " justified by "
        + acceptanceSource + "; observed maximum error " + maximumError;
    MaterialBalancePvtHistory history = new MaterialBalancePvtHistory(model, path, evidence, temperatureK,
        separatorPressuresBara, separatorTemperaturesK, records);
    return new Result(history, snapshots, closure, produced, maximumError, maximumRelativeSurfaceError,
        acceptanceSource, waterSource, waterFluid);
  }

  /**
   * Runs the existing depletion solver with opt-in native evidence.
   *
   * @param feed independent accepted hydrocarbon sample
   * @param path supported depletion protocol
   * @param temperature temperature in K
   * @param pressures decreasing pressure schedule in bara
   * @return native step snapshots
   */
  private static DepletionSnapshot[] replay(SystemInterface feed, Path path, double temperature, double[] pressures) {
    if (path == Path.CVD) {
      ConstantVolumeDepletion simulation = new ConstantVolumeDepletion(feed);
      simulation.setTemperature(temperature, "K");
      simulation.setPressures(pressures);
      simulation.setRecordDepletionSnapshots(true);
      simulation.runCalc();
      return simulation.getDepletionSnapshots();
    }
    DifferentialLiberation simulation = new DifferentialLiberation(feed);
    simulation.setTemperature(temperature, "K");
    simulation.setPressures(pressures);
    simulation.setRecordDepletionSnapshots(true);
    simulation.runCalc();
    return simulation.getDepletionSnapshots();
  }

  /**
   * Checks exact native-component bookkeeping before interpreting approximate surface components.
   *
   * @param snapshots solver evidence
   * @param initial original sample component amounts, mol
   */
  private static void checkComponentClosure(DepletionSnapshot[] snapshots, double[] initial) {
    double[] cumulative = new double[initial.length];
    for (int row = 0; row < snapshots.length; row++) {
      if (snapshots[row] == null) {
        throw new IllegalStateException("Incomplete depletion snapshot at row " + row);
      }
      double[] withdrawn = snapshots[row].getProducedComponentMoles();
      double[] retained = inventory(snapshots[row].getRetainedFluid());
      for (int i = 0; i < initial.length; i++) {
        cumulative[i] += withdrawn[i];
        if (Math.abs(initial[i] - cumulative[i] - retained[i]) > 1.0e-8 * Math.max(1.0, initial[i])) {
          throw new IllegalStateException(
              "Native component balance failed at depletion row " + row + ", component " + i);
        }
      }
    }
  }

  /**
   * Rejects unsupported phase states rather than extrapolating an absent phase's PVT.
   *
   * @param fluid solver equilibrium state
   * @param row pressure-grid index
   */
  private static void checkHydrocarbonPhases(SystemInterface fluid, int row) {
    if (fluid.getNumberOfPhases() != 2 || !fluid.hasPhaseType("gas") || !fluid.hasPhaseType("oil")) {
      throw new IllegalStateException("Two hydrocarbon phases required for PVT interpretation at row " + row);
    }
  }

  /** Surface oil/gas volumes and exact component accounting for one processed feed. */
  private static final class Surface {
    private double oil;
    private double gas;
    private final double[] components;

    /**
     * Allocates an empty separator balance.
     *
     * @param count original component count
     */
    private Surface(int count) {
      components = new double[count];
    }

    /**
     * Adds one independent completed separator stream.
     *
     * @param other processed surface output
     */
    private void add(Surface other) {
      oil += other.oil;
      gas += other.gas;
      for (int i = 0; i < components.length; i++) {
        components[i] += other.components[i];
      }
    }

  }

  /**
   * Processes each existing reservoir phase separately on the common surface-component basis.
   *
   * @param fluid native retained state without a new reservoir flash
   * @param pressures fixed separator pressures in bara
   * @param temperatures fixed stage temperatures in K
   * @return combined surface volumes of its retained phases
   */
  private static Surface separateExistingPhases(SystemInterface fluid, double[] pressures, double[] temperatures) {
    Surface total = new Surface(fluid.getNumberOfComponents());
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      total.add(separate(extract(fluid, p), pressures, temperatures));
    }
    return total;
  }

  /**
   * Separates a feed by forwarding liquid and sending each offgas to a final standard-condition flash.
   *
   * @param feed independent reservoir-phase or withdrawn mixture
   * @param pressures fixed separator pressures in bara
   * @param temperatures fixed separator temperatures in K
   * @return both surface product volumes and exact native-component accounting
   */
  private static Surface separate(SystemInterface feed, double[] pressures, double[] temperatures) {
    PvtSeparatorPath.Result separated = new PvtSeparatorPath(pressures, temperatures,
        "Depletion generator fixed 1.01325 bara / 288.15 K separator basis").separate(feed);
    Surface outputs = new Surface(feed.getNumberOfComponents());
    double[] volumes = separated.getVolumesM3();
    if (volumes[2] != 0.0) {
      throw new IllegalStateException("Only gas/oil surface phases are supported");
    }
    outputs.gas = volumes[0];
    outputs.oil = volumes[1];
    double[][] amounts = separated.getComponentMoles();
    for (int i = 0; i < outputs.components.length; i++) {
      outputs.components[i] = amounts[0][i] + amounts[1][i];
    }
    return outputs;
  }

  /**
   * Extracts a phase from an isolated native copy, protecting sibling model and transport state.
   *
   * @param fluid source EOS state
   * @param phase logical active phase index
   * @return independently owned phase fluid
   */
  private static SystemInterface extract(SystemInterface fluid, int phase) {
    if (phase < 0 || phase >= fluid.getNumberOfPhases()) {
      throw new IllegalStateException("Requested native phase is absent");
    }
    return copy(fluid).phaseToSystem(phase);
  }

  /**
   * Runs an EOS flash and prepares density/transport/volume-correction properties.
   *
   * @param fluid independent mutable working fluid
   */
  private static void flash(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    if (!positive(fluid.getDensity("kg/m3"))) {
      throw new IllegalStateException("Nonphysical flashed density");
    }
  }

  /**
   * Rejects vaporized water and unsupported phase transitions in the separate Bw reference.
   *
   * @param water accepted pure-water reference after flash
   */
  private static void requireWaterLiquid(SystemInterface water) {
    if (water.getNumberOfPhases() != 1 || water.hasPhaseType("gas") || water.getDensity("kg/m3") < 500.0) {
      throw new IllegalStateException("Bw reference must remain a single liquid-water phase");
    }
  }

  /**
   * Returns complete component-mole inventories in the native named order.
   *
   * @param fluid independent EOS state
   * @return component amounts in mol
   */
  private static double[] inventory(SystemInterface fluid) {
    double[] amounts = new double[fluid.getNumberOfComponents()];
    for (int i = 0; i < amounts.length; i++) {
      amounts[i] = fluid.getComponent(i).getNumberOfmoles();
      if (!Double.isFinite(amounts[i]) || amounts[i] < 0.0) {
        throw new IllegalStateException("Invalid native component amount");
      }
    }
    return amounts;
  }

  /**
   * Deep-copies a native EOS including nested mixing and physical-property objects.
   *
   * @param fluid supplied model
   * @return independent serialized copy
   */
  private static SystemInterface copy(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("Accepted water/reference fluid required");
    }
    return SerializationUtils.deserialize(SerializationUtils.serialize(fluid));
  }

  /**
   * Copies a rectangular evidence array.
   *
   * @param values owned numerical evidence
   * @return independent row arrays
   */
  private static double[][] copy(double[][] values) {
    double[][] result = new double[values.length][];
    for (int i = 0; i < result.length; i++) {
      result[i] = values[i].clone();
    }
    return result;
  }

  /**
   * Checks a strictly positive finite physical scale.
   *
   * @param value supplied scale
   * @return true for a finite positive value
   */
  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }
}
