package neqsim.pvtsimulation.simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.pvtsimulation.util.parameterfitting.CVDFunction;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.flashops.saturationops.ConstantDutyPressureFlash;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * ConstantVolumeDepletion class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class ConstantVolumeDepletion extends BasePVTsimulation {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ConstantVolumeDepletion.class);

  private static final double CELL_VOLUME_RELATIVE_TOLERANCE = 1.0e-8;
  private static final int MAX_CELL_VOLUME_ITERATIONS = 100;
  /** Internal precision targets for explicit-reference replay, separate from benchmark acceptance bounds. */
  private static final double FIXED_CELL_PRECISION_TOLERANCE = 1e-12;
  /** Maximum calls to the existing beta solver at one already accepted endpoint. */
  private static final int MAX_FIXED_CELL_REFINEMENTS = 12;

  private boolean recordDepletionSnapshots;
  private boolean refineSaturationConditions;
  private DepletionSnapshot[] depletionSnapshots = new DepletionSnapshot[0];

  private double[] relativeVolume = null;
  double[] totalVolume = null;
  double[] liquidVolumeRelativeToVsat = null;
  double[] liquidVolume = null;
  boolean saturationConditionFound = false;
  private double[] liquidRelativeVolume = null;
  private double[] Zmix;
  private double[] Zgas = null;
  private double[] cummulativeMolePercDepleted = null;
  private double[] materialBalanceRelativeError = null;
  private double initialMoles = Double.NaN;
  private double cumulativeMolesRemoved = 0.0;
  double[] temperatures = null;
  double[] pressure = null;

  /**
   * Constructor for ConstantVolumeDepletion.
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ConstantVolumeDepletion(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * Set paired tuning temperatures and the depletion pressure schedule used by runCalc and runTuning. runCalc uses the
   * common temperature configured with setTemperature, or the fluid's temperature if unset.
   *
   * @param temperature tuning temperatures in K
   * @param pressure depletion pressures in bara
   */
  public void setTemperaturesAndPressures(double[] temperature, double[] pressure) {
    this.pressure = pressure;
    this.pressures = pressure;
    this.temperatures = temperature;
    experimentalData = new double[temperature.length][1];
  }

  /**
   * Computes the cell reference with the legacy scan or opt-in checked upper-dew refinement.
   *
   * @throws IllegalArgumentException if refinement is requested for a wet sample
   * @throws IllegalStateException if a scan flash fails or refinement cannot establish an accepted boundary
   */
  public void calcSaturationConditions() {
    if (refineSaturationConditions && Arrays.asList(getThermoSystem().getComponentNames()).contains("water")) {
      throw new IllegalArgumentException("CVD upper dew refinement is not qualified for wet samples");
    }
    getThermoSystem().setPressure(1.0);
    do {
      getThermoSystem().setPressure(getThermoSystem().getPressure() + 10.0);
      runSaturationTPflash();
    } while (getThermoSystem().getNumberOfPhases() == 1 && getThermoSystem().getPressure() < 1000.0);
    SystemInterface twoPhase = refineSaturationConditions && getThermoSystem().getNumberOfPhases() == 2
        ? getThermoSystem().clone()
        : null;
    do {
      if (refineSaturationConditions && getThermoSystem().getNumberOfPhases() == 2) {
        twoPhase = getThermoSystem().clone();
      }
      getThermoSystem().setPressure(getThermoSystem().getPressure() + 10.0);
      runSaturationTPflash();
    } while (getThermoSystem().getNumberOfPhases() > 1 && getThermoSystem().getPressure() < 1000.0);
    double minPres = getThermoSystem().getPressure() - 10.0;
    double maxPres = getThermoSystem().getPressure();
    double branchMinimum = minPres;
    double branchMaximum = maxPres;
    if (refineSaturationConditions
        && (getThermoSystem().getNumberOfPhases() != 1 || !getThermoSystem().hasPhaseType("gas"))) {
      throw new IllegalStateException("CVD upper dew refinement requires gas above the upper scan bracket");
    }
    do {
      getThermoSystem().setPressure((minPres + maxPres) / 2.0);
      runSaturationTPflash();
      if (getThermoSystem().getNumberOfPhases() > 1) {
        minPres = getThermoSystem().getPressure();
      } else {
        maxPres = getThermoSystem().getPressure();
      }
    } while (Math.abs(maxPres - minPres) > 1e-5);
    if (refineSaturationConditions) {
      refineUpperDew(twoPhase, branchMinimum, branchMaximum);
      runSaturationTPflash();
    }
    /*
     * try { thermoOps.dewPointPressureFlash(); } catch (Exception ex) { logger.error(ex.getMessage(), ex); }
     */
    getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    saturationVolume = getThermoSystem().getCorrectedVolume();
    saturationPressure = getThermoSystem().getPressure();
    Zsaturation = getThermoSystem().getZvolcorr();
    saturationConditionFound = true;
  }

  /**
   * Enables checked upper-dew refinement of the phase-count scan for the next run.
   *
   * <p>
   * The default remains the legacy scan. Refinement requires a dry, nondegenerate gas/oil bracket below 1000 bara. It
   * preserves the scan's upper branch, uses the existing constant-duty pressure solver, and rejects a wrong branch,
   * trivial split or unclosed equilibrium instead of falling back to the scan. Pure fluids, oil bubble boundaries, wet
   * samples and unbracketed domains are not supported by this mode.
   * </p>
   *
   * @param refine true to refine and validate the upper dew boundary
   */
  public void setRefineSaturationConditions(boolean refine) {
    refineSaturationConditions = refine;
  }

  /**
   * Refines a warm gas/oil state while retaining the coarse scan's branch identity.
   *
   * @param twoPhase last gas/oil state within the coarse upper boundary bracket
   * @param minimum lower coarse bracket pressure in bara
   * @param maximum upper coarse bracket pressure in bara
   * @throws IllegalStateException if the branch or phase-equilibrium checks fail
   */
  private void refineUpperDew(SystemInterface twoPhase, double minimum, double maximum) {
    if (twoPhase == null || !twoPhase.hasPhaseType("gas") || !twoPhase.hasPhaseType("oil")
        || twoPhase.getNumberOfPhases() != 2 || maximum >= 1000.0
        || !"gas".equals(twoPhase.getPhase(0).getType().getDesc())
        || !"oil".equals(twoPhase.getPhase(1).getType().getDesc())) {
      throw new IllegalStateException("CVD upper dew refinement requires a dry gas/oil-to-gas bracket below 1000 bara");
    }
    twoPhase.setBeta(0, 1.0 - 1e-10);
    twoPhase.setBeta(1, 1e-10);
    new ConstantDutyPressureFlash(twoPhase).run();
    twoPhase.init(1);
    double pressure = twoPhase.getPressure();
    if (!Double.isFinite(pressure) || pressure < minimum - 1e-5 || pressure > maximum + 1e-5) {
      throw new IllegalStateException("CVD refined dew pressure left the upper scan bracket");
    }
    double distance = 0.0;
    double residual = 0.0;
    double gasSum = 0.0;
    double oilSum = 0.0;
    for (int i = 0; i < twoPhase.getNumberOfComponents(); i++) {
      double gas = twoPhase.getPhase("gas").getComponent(i).getx();
      double oil = twoPhase.getPhase("oil").getComponent(i).getx();
      double gasFugacity = gas * twoPhase.getPhase("gas").getComponent(i).getFugacityCoefficient();
      double oilFugacity = oil * twoPhase.getPhase("oil").getComponent(i).getFugacityCoefficient();
      double z = twoPhase.getComponent(i).getz();
      if (!Double.isFinite(gas) || !Double.isFinite(oil) || !Double.isFinite(z) || gas < 0.0 || oil < 0.0 || z < 0.0
          || Math.abs((1.0 - 1e-10) * gas + 1e-10 * oil - z) > 1e-8) {
        throw new IllegalStateException("CVD refined dew composition is invalid");
      }
      if (z > 0.0) {
        double error = Math.abs(Math.log(gasFugacity) - Math.log(oilFugacity));
        if (!Double.isFinite(error)) {
          throw new IllegalStateException("CVD refined dew fugacity is invalid");
        }
        residual = Math.max(residual, error);
      }
      distance += Math.abs(gas - oil);
      gasSum += gas;
      oilSum += oil;
    }
    if (distance <= 1e-8 || residual > 1e-8 || Math.abs(gasSum - 1.0) > 1e-8 || Math.abs(oilSum - 1.0) > 1e-8) {
      throw new IllegalStateException("CVD refined dew equilibrium is degenerate or not converged");
    }
    getThermoSystem().setPressure(pressure);
  }

  /**
   * Runs a TP flash during the saturation-pressure scan with pressure context.
   */
  private void runSaturationTPflash() {
    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      String message = String.format(Locale.ROOT, "CVD saturation scan TP flash failed at pressure %.6f bara.",
          getThermoSystem().getPressure("bara"));
      logger.error(message, ex);
      throw new IllegalStateException(message, ex);
    }
  }

  /**
   * Restore the constant CVD cell volume by withdrawing equilibrium gas.
   */
  private void restoreSaturationVolume() {
    restoreSaturationVolume(false);
  }

  /**
   * Restores the occupied cell with optional precision for the explicit-reference experiment only.
   *
   * @param precise true to refine equilibrium and use a tighter volume stopping criterion
   */
  private void restoreSaturationVolume(boolean precise) {
    for (int iteration = 0; iteration < MAX_CELL_VOLUME_ITERATIONS; iteration++) {
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        String message = String.format(Locale.ROOT,
            "CVD cell-volume restoration TP flash failed at pressure %.6f bara, iteration %d.",
            getThermoSystem().getPressure("bara"), iteration);
        logger.error(message, ex);
        throw new IllegalStateException(message, ex);
      }
      getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

      if (precise) {
        refineFixedCellEquilibrium(getThermoSystem());
      }
      double currentVolume = getThermoSystem().getCorrectedVolume();
      double relativeResidual = (currentVolume - saturationVolume) / saturationVolume;
      if (Math.abs(relativeResidual) <= (precise ? FIXED_CELL_PRECISION_TOLERANCE : CELL_VOLUME_RELATIVE_TOLERANCE)) {
        return;
      }
      if (relativeResidual < 0.0) {
        throw new IllegalStateException(String.format(Locale.ROOT,
            "CVD gas removal overshot the saturation volume by %.3e relative.", -relativeResidual));
      }
      if (!getThermoSystem().hasPhaseType("gas")) {
        throw new IllegalStateException("Cannot restore CVD cell volume without a gas phase.");
      }

      double gasMoles = getThermoSystem().getPhase("gas").getNumberOfMolesInPhase();
      double gasVolume = getThermoSystem().getPhase("gas").getCorrectedVolume();
      if (gasMoles <= 0.0 || gasVolume <= 0.0) {
        throw new IllegalStateException("Cannot restore CVD cell volume from an empty gas phase.");
      }

      double molesToRemove = (currentVolume - saturationVolume) / gasVolume * gasMoles;
      molesToRemove = Math.min(molesToRemove, 0.95 * gasMoles);
      double[] componentRemoval = new double[getThermoSystem().getNumberOfComponents()];
      for (int componentIndex = 0; componentIndex < componentRemoval.length; componentIndex++) {
        componentRemoval[componentIndex] = molesToRemove
            * getThermoSystem().getPhase("gas").getComponent(componentIndex).getx();
      }

      double removedMoles = 0.0;
      for (int componentIndex = 0; componentIndex < componentRemoval.length; componentIndex++) {
        getThermoSystem().addComponent(componentIndex, -componentRemoval[componentIndex]);
        removedMoles += componentRemoval[componentIndex];
      }
      cumulativeMolesRemoved += removedMoles;
    }

    throw new IllegalStateException("CVD gas removal did not restore the saturation volume.");
  }

  /**
   * Immutable explicit occupied-volume reference and completed native fixed-cell states.
   *
   * <p>
   * The reference is the accepted EOS sample at the first pressure, not an inferred dew point or a measured saturation
   * volume. No normalized PVT properties or absent-phase values are supplied.
   * </p>
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class FixedCellResult implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final double temperatureK;
    private final double referencePressureBara;
    private final double referenceVolumeM3;
    private final DepletionSnapshot[] snapshots;

    /**
     * Freezes a successfully completed experiment.
     *
     * @param temperatureK common experiment temperature, K
     * @param referencePressureBara first experiment pressure, bara
     * @param referenceVolumeM3 initial occupied EOS volume, reservoir m3
     * @param snapshots complete native states and component withdrawals
     */
    private FixedCellResult(double temperatureK, double referencePressureBara, double referenceVolumeM3,
        DepletionSnapshot[] snapshots) {
      this.temperatureK = temperatureK;
      this.referencePressureBara = referencePressureBara;
      this.referenceVolumeM3 = referenceVolumeM3;
      this.snapshots = snapshots.clone();
    }

    /**
     * Returns the common experiment temperature.
     *
     * @return temperature in K
     */
    public double getTemperatureK() {
      return temperatureK;
    }

    /**
     * Returns the initial occupied-volume reference pressure, without a dew-point interpretation.
     *
     * @return absolute reference pressure in bara
     */
    public double getReferencePressureBara() {
      return referencePressureBara;
    }

    /**
     * Returns the sample's initial occupied EOS volume on the model's fixed volume-correction basis.
     *
     * @return actual reference cell volume in m3
     */
    public double getReferenceVolumeM3() {
      return referenceVolumeM3;
    }

    /**
     * Returns immutable completed states in a defensive array.
     *
     * @return native volume-restored cells and interval component withdrawals
     */
    public DepletionSnapshot[] getSnapshots() {
      return snapshots.clone();
    }
  }

  /**
   * Replays an explicit constant-cell experiment transactionally on an independent copy of the current sample.
   *
   * <p>
   * The first pressure defines the occupied-volume reference with zero withdrawal. At each subsequent pressure,
   * equilibrium gas alone is withdrawn to restore that volume; all liquids remain. Gas-only, gas/oil, gas/aqueous and
   * gas/oil/aqueous cells are permitted when the named phase family remains unchanged throughout the sampled path. The
   * opt-in route refines accepted endpoints to 1e-12 log-fugacity residual and relative cell-volume closure using the
   * existing multiphase beta solver. Refinement must preserve phase family, Gibbs energy and component balance.
   * Phase-family changes, absent gas, other phase types, unclosed equilibrium and critical-degenerate splits fail. This
   * is not the saturation-referenced laboratory protocol used by {@link #runCalc()} and does not infer a wet upper dew
   * point. The caller must independently accept its EOS, phase options and experiment provenance.
   * </p>
   *
   * <p>
   * Neither the supplied sample nor this simulation's legacy results are changed, including after a late failure.
   * Repeated calls therefore restart from identical current input. No EOS parameter is fitted.
   * </p>
   *
   * @param temperatureK finite positive common temperature, K
   * @param pressuresBara complete strictly decreasing positive absolute pressure schedule, at least two points
   * @return immutable reference and complete native results only after every pressure succeeds
   * @throws IllegalArgumentException if temperature, pressure schedule or initial inventory is invalid
   * @throws IllegalStateException if the declared fixed-cell domain or a numerical closure check fails
   */
  public FixedCellResult runFixedCellDepletion(double temperatureK, double[] pressuresBara) {
    if (!Double.isFinite(temperatureK) || temperatureK <= 0 || pressuresBara == null || pressuresBara.length < 2) {
      throw new IllegalArgumentException("Positive temperature and complete fixed-cell pressure schedule required");
    }
    double[] schedule = pressuresBara.clone();
    for (int row = 0; row < schedule.length; row++) {
      if (!Double.isFinite(schedule[row]) || schedule[row] <= 0 || row > 0 && schedule[row] >= schedule[row - 1]) {
        throw new IllegalArgumentException("Fixed-cell pressures must be positive and strictly decreasing");
      }
    }
    SystemInterface cell = org.apache.commons.lang3.SerializationUtils.clone(getThermoSystem());
    if (!Double.isFinite(cell.getTotalNumberOfMoles()) || cell.getTotalNumberOfMoles() <= 0) {
      throw new IllegalArgumentException("Finite positive sample inventory required");
    }
    double[] initial = DepletionSnapshot.inventory(cell);
    for (double amount : initial) {
      if (!Double.isFinite(amount) || amount < 0) {
        throw new IllegalArgumentException("Finite nonnegative named component inventories required");
      }
    }
    ConstantVolumeDepletion trial = new ConstantVolumeDepletion(cell);
    neqsim.thermodynamicoperations.ThermodynamicOperations operations = new neqsim.thermodynamicoperations.ThermodynamicOperations(
        cell);
    cell.setTemperature(temperatureK);
    String[] family = null;
    double[] previous = initial.clone();
    double[] cumulative = new double[initial.length];
    DepletionSnapshot[] completed = new DepletionSnapshot[schedule.length];
    for (int row = 0; row < schedule.length; row++) {
      cell.setPressure(schedule[row]);
      try {
        operations.TPflash();
        cell.init(1);
        cell.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      } catch (Exception ex) {
        throw new IllegalStateException("Fixed-cell flash failed at row " + row, ex);
      }
      refineFixedCellEquilibrium(cell);
      String[] present = fixedCellFamily(cell);
      if (row == 0) {
        family = present;
        trial.saturationVolume = cell.getCorrectedVolume();
      } else if (!Arrays.equals(family, present)) {
        throw new IllegalStateException("Fixed-cell phase-family change is not qualified at row " + row);
      }
      trial.restoreSaturationVolume(true);
      if (!Arrays.equals(family, fixedCellFamily(cell))) {
        throw new IllegalStateException("Fixed-cell withdrawal changed the phase family at row " + row);
      }
      if (Math.abs(cell.getCorrectedVolume() / trial.saturationVolume - 1) > CELL_VOLUME_RELATIVE_TOLERANCE) {
        throw new IllegalStateException("Fixed-cell volume closure failed at row " + row);
      }
      completed[row] = new DepletionSnapshot(cell, cell, previous);
      double[] removed = completed[row].getProducedComponentMoles();
      double[] retained = DepletionSnapshot.inventory(cell);
      for (int component = 0; component < initial.length; component++) {
        cumulative[component] += removed[component];
        if (Math.abs(initial[component] - retained[component] - cumulative[component]) > 1e-10
            * Math.max(1.0, initial[component])) {
          throw new IllegalStateException("Fixed-cell component closure failed at row " + row);
        }
      }
      previous = retained;
    }
    return new FixedCellResult(temperatureK, schedule[0], trial.saturationVolume, completed);
  }

  /**
   * Refines an already accepted fixed-topology endpoint using the existing multiphase beta solver.
   *
   * <p>
   * Only the opt-in explicit-reference experiment pays this cost. A tighter log-fugacity residual prevents small
   * phase-fraction errors from scaling into large absolute inventory differences. Any failed precision, phase-family,
   * energy or balance gate rejects the whole transactional path rather than returning the less precise endpoint.
   * </p>
   *
   * @param cell local trial state, never the caller's fluid
   * @throws IllegalStateException if refinement cannot preserve a balanced supported endpoint
   */
  private static void refineFixedCellEquilibrium(SystemInterface cell) {
    String[] family = fixedCellFamily(cell);
    double residual = fixedCellFugacityResidual(cell);
    if (residual <= FIXED_CELL_PRECISION_TOLERANCE) {
      return;
    }
    double initialGibbs = cell.getGibbsEnergy();
    if (!Double.isFinite(initialGibbs)) {
      throw new IllegalStateException("Invalid fixed-cell Gibbs energy before refinement");
    }
    double[] inventory = DepletionSnapshot.inventory(cell);
    neqsim.thermodynamicoperations.flashops.TPmultiflash solver = new neqsim.thermodynamicoperations.flashops.TPmultiflash(
        cell, false);
    solver.setDoubleArrays();
    for (int refinement = 0; refinement < MAX_FIXED_CELL_REFINEMENTS
        && residual > FIXED_CELL_PRECISION_TOLERANCE; refinement++) {
      solver.solveBeta();
      cell.init(1);
      residual = fixedCellFugacityResidual(cell);
    }
    cell.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    if (!Double.isFinite(residual) || residual > FIXED_CELL_PRECISION_TOLERANCE
        || !Arrays.equals(family, fixedCellFamily(cell)) || !Double.isFinite(cell.getGibbsEnergy())
        || cell.getGibbsEnergy() > initialGibbs + 1e-10 * Math.max(1.0, Math.abs(initialGibbs))) {
      throw new IllegalStateException("Fixed-cell precision refinement failed");
    }
    for (int component = 0; component < inventory.length; component++) {
      double total = 0;
      for (int phase = 0; phase < cell.getNumberOfPhases(); phase++) {
        total += cell.getPhase(phase).getNumberOfMolesInPhase() * cell.getPhase(phase).getComponent(component).getx();
      }
      if (Math.abs(total - inventory[component]) > 1e-12 * Math.max(1.0, inventory[component])) {
        throw new IllegalStateException("Fixed-cell precision refinement lost component balance");
      }
    }
  }

  /**
   * Measures the largest log-fugacity residual against phase zero over present components.
   *
   * @param cell initialized local endpoint
   * @return maximum dimensionless residual, zero for a single phase
   */
  private static double fixedCellFugacityResidual(SystemInterface cell) {
    double maximum = 0;
    for (int phase = 1; phase < cell.getNumberOfPhases(); phase++) {
      for (int component = 0; component < cell.getNumberOfComponents(); component++) {
        if (cell.getComponent(component).getNumberOfmoles() <= 0) {
          continue;
        }
        double first = cell.getPhase(0).getComponent(component).getx()
            * cell.getPhase(0).getComponent(component).getFugacityCoefficient();
        double other = cell.getPhase(phase).getComponent(component).getx()
            * cell.getPhase(phase).getComponent(component).getFugacityCoefficient();
        maximum = Math.max(maximum, Math.abs(Math.log(first) - Math.log(other)));
      }
    }
    return maximum;
  }

  /**
   * Checks the bounded named-phase domain and returns its order-independent identity.
   *
   * @param cell already flashed and density-initialized native state
   * @return sorted unique gas/oil/aqueous phase names
   * @throws IllegalStateException if a phase, volume, composition or equilibrium is unsupported
   */
  private static String[] fixedCellFamily(SystemInterface cell) {
    if (!cell.hasPhaseType("gas") || !Double.isFinite(cell.getCorrectedVolume()) || cell.getCorrectedVolume() <= 0) {
      throw new IllegalStateException("Fixed-cell replay requires retained gas and positive finite occupied volume");
    }
    String[] family = new String[cell.getNumberOfPhases()];
    for (int phase = 0; phase < family.length; phase++) {
      family[phase] = cell.getPhase(phase).getType().getDesc();
      if (!"gas".equals(family[phase]) && !"oil".equals(family[phase]) && !"aqueous".equals(family[phase])) {
        throw new IllegalStateException("Unsupported fixed-cell phase " + family[phase]);
      }
      double amount = cell.getPhase(phase).getNumberOfMolesInPhase();
      double volume = cell.getPhase(phase).getCorrectedVolume();
      if (!Double.isFinite(amount) || amount <= 0 || !Double.isFinite(volume) || volume <= 0) {
        throw new IllegalStateException("Empty or invalid fixed-cell phase");
      }
      double sum = 0;
      for (int component = 0; component < cell.getNumberOfComponents(); component++) {
        double x = cell.getPhase(phase).getComponent(component).getx();
        if (!Double.isFinite(x) || x < 0) {
          throw new IllegalStateException("Invalid fixed-cell composition");
        }
        sum += x;
      }
      if (Math.abs(sum - 1) > 1e-8) {
        throw new IllegalStateException("Unnormalized fixed-cell phase");
      }
      for (int other = 0; other < phase; other++) {
        if (family[phase].equals(family[other])) {
          throw new IllegalStateException("Duplicate fixed-cell phase type is not qualified");
        }
        double distance = 0;
        for (int component = 0; component < cell.getNumberOfComponents(); component++) {
          double x = cell.getPhase(phase).getComponent(component).getx();
          double y = cell.getPhase(other).getComponent(component).getx();
          distance += Math.abs(x - y);
          if (cell.getComponent(component).getNumberOfmoles() > 0) {
            double f = x * cell.getPhase(phase).getComponent(component).getFugacityCoefficient();
            double g = y * cell.getPhase(other).getComponent(component).getFugacityCoefficient();
            if (!(f > 0) || !(g > 0) || !Double.isFinite(f) || !Double.isFinite(g)
                || Math.abs(Math.log(f) - Math.log(g)) > 1e-8) {
              throw new IllegalStateException("Unclosed fixed-cell phase equilibrium");
            }
          }
        }
        if (distance <= 1e-8) {
          throw new IllegalStateException("Critical-degenerate fixed-cell split is not qualified");
        }
      }
    }
    Arrays.sort(family);
    return family;
  }

  /**
   * Runs the pressure-ordered depletion schedule using the selected cell reference.
   *
   * <p>
   * Gas-only volume restoration continues below the reference pressure only after the schedule has sampled gas/oil
   * coexistence. An entirely single-phase schedule retains its inventory; the legacy scan reference alone does not
   * qualify an unbracketed or pure-fluid CVD experiment.
   * </p>
   *
   * @throws IllegalStateException if a flash, boundary refinement or volume restoration fails
   */
  public void runCalc() {
    saturationConditionFound = false;
    depletionSnapshots = recordDepletionSnapshots ? new DepletionSnapshot[pressures.length] : new DepletionSnapshot[0];
    double[] previousInventory = recordDepletionSnapshots ? DepletionSnapshot.inventory(getThermoSystem()) : null;
    relativeVolume = new double[pressures.length];
    totalVolume = new double[pressures.length];
    liquidVolumeRelativeToVsat = new double[pressures.length];
    liquidVolume = new double[pressures.length];
    Zgas = new double[pressures.length];
    Zmix = new double[pressures.length];
    liquidRelativeVolume = new double[pressures.length];
    cummulativeMolePercDepleted = new double[pressures.length];
    materialBalanceRelativeError = new double[pressures.length];

    initialMoles = getThermoSystem().getTotalNumberOfMoles();
    cumulativeMolesRemoved = 0.0;
    if (!Double.isNaN(temperature)) {
      getThermoSystem().setTemperature(temperature, temperatureUnit);
    }
    calcSaturationConditions();
    boolean sampledHydrocarbonSplit = false;

    for (int i = 0; i < pressures.length; i++) {
      getThermoSystem().setPressure(pressures[i]);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        String message = String.format(Locale.ROOT, "CVD TP flash failed at pressure %.6f bara.",
            getThermoSystem().getPressure("bara"));
        logger.error(message, ex);
        throw new IllegalStateException(message, ex);
      }
      getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

      sampledHydrocarbonSplit |= getThermoSystem().hasPhaseType("gas") && getThermoSystem().hasPhaseType("oil");
      // Continue gas withdrawal after a sampled gas/oil stage, even when the last liquid evaporates.
      // An unbracketed legacy scan reference alone must not start single-phase withdrawal.
      if ((getThermoSystem().getNumberOfPhases() > 1 || sampledHydrocarbonSplit && pressures[i] < saturationPressure)
          && getThermoSystem().getCorrectedVolume() > saturationVolume * (1.0 + CELL_VOLUME_RELATIVE_TOLERANCE)) {
        restoreSaturationVolume();
      }
      getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

      totalVolume[i] = getThermoSystem().getCorrectedVolume();
      relativeVolume[i] = totalVolume[i] / saturationVolume;
      double remainingMoles = getThermoSystem().getTotalNumberOfMoles();
      cummulativeMolePercDepleted[i] = 100.0 - remainingMoles / initialMoles * 100.0;
      materialBalanceRelativeError[i] = Math.abs(initialMoles - remainingMoles - cumulativeMolesRemoved) / initialMoles;
      Zmix[i] = getThermoSystem().getZvolcorr();

      if (getThermoSystem().hasPhaseType("gas")) {
        Zgas[i] = getThermoSystem().getPhase("gas").getZvolcorr();
      }
      if (getThermoSystem().hasPhaseType("oil")) {
        liquidVolume[i] = getThermoSystem().getPhase("oil").getCorrectedVolume();
        liquidVolumeRelativeToVsat[i] = liquidVolume[i] / saturationVolume;
        liquidRelativeVolume[i] = liquidVolumeRelativeToVsat[i] * 100.0;
      }
      if (recordDepletionSnapshots) {
        depletionSnapshots[i] = new DepletionSnapshot(getThermoSystem(), getThermoSystem(), previousInventory);
        previousInventory = DepletionSnapshot.inventory(getThermoSystem());
      }
    }
  }

  /**
   * Enables immutable per-pressure native phase and component-withdrawal evidence for the next run.
   *
   * @param record true to record; false preserves the default calculation and memory cost
   */
  public void setRecordDepletionSnapshots(boolean record) {
    recordDepletionSnapshots = record;
  }

  /**
   * Returns a copy of the recorded snapshot array, empty when recording was disabled.
   *
   * @return immutable completed-step evidence in supplied pressure order
   */
  public DepletionSnapshot[] getDepletionSnapshots() {
    return depletionSnapshots.clone();
  }

  /**
   * runTuning.
   */
  public void runTuning() {
    ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

    try {
      System.out.println("adding....");

      for (int i = 0; i < experimentalData[0].length; i++) {
        CVDFunction function = new CVDFunction();
        double[] guess = new double[] {234.0 / 1000.0}; // getThermoSystem().getCharacterization().getPlusFractionModel().getMPlus()/1000.0};
        function.setInitialGuess(guess);

        SystemInterface tempSystem = getThermoSystem(); // getThermoSystem().clone();

        tempSystem.setTemperature(temperature, temperatureUnit);
        tempSystem.setPressure(pressures[i]);
        // thermoOps.TPflash();
        // tempSystem.display();
        double[] sample1 = {temperatures[i], pressures[i]};
        double relativeVolume = experimentalData[0][i];
        double[] standardDeviation1 = {1.5};
        SampleValue sample = new SampleValue(relativeVolume, relativeVolume / 50.0, sample1, standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(tempSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error", ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);

    optimizer = new LevenbergMarquardt();
    optimizer.setMaxNumberOfIterations(20);

    optimizer.setSampleSet(sampleSet);
    optimizer.solve();
    runCalc();
    // optim.displayCurveFit();
  }

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface tempSystem = new SystemSrkEos(298.0, 211.0);
    /*
     * tempSystem.addComponent("nitrogen", 0.64); tempSystem.addComponent("CO2", 3.53);
     * tempSystem.addComponent("methane", 70.78); tempSystem.addComponent("ethane", 8.94);
     * tempSystem.addComponent("propane", 5.05); tempSystem.addComponent("i-butane", 0.85);
     * tempSystem.addComponent("n-butane", 1.68); tempSystem.addComponent("iC5", 0.62);
     * tempSystem.addComponent("n-pentane", 0.79); tempSystem.addComponent("n-hexane", 0.83);
     * tempSystem.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324); tempSystem.addTBPfraction("C8", 1.06, 104.6 /
     * 1000.0, 0.7602); tempSystem.addTBPfraction("C9", 0.79, 119.1 / 1000.0, 0.7677); tempSystem.addTBPfraction("C10",
     * 0.57, 133.0 / 1000.0, 0.79); tempSystem.addTBPfraction("C11", 0.38, 155.0 / 1000.0, 0.795);
     * tempSystem.addTBPfraction("C12", 0.37, 162.0 / 1000.0, 0.806); tempSystem.addTBPfraction("C13", 0.32, 177.0 /
     * 1000.0, 0.824); tempSystem.addTBPfraction("C14", 0.27, 198.0 / 1000.0, 0.835); tempSystem.addTBPfraction("C15",
     * 0.23, 202.0 / 1000.0, 0.84); tempSystem.addTBPfraction("C16", 0.19, 215.0 / 1000.0, 0.846);
     * tempSystem.addTBPfraction("C17", 0.17, 234.0 / 1000.0, 0.84); tempSystem.addTBPfraction("C18", 0.13, 251.0 /
     * 1000.0, 0.844); tempSystem.addTBPfraction("C19", 0.13, 270.0 / 1000.0, 0.854); tempSystem.addPlusFraction("C20",
     * 0.62, 381.0 / 1000.0, 0.88); tempSystem.getCharacterization().getLumpingModel(). setNumberOfLumpedComponents(6);
     * tempSystem.getCharacterization().characterisePlusFraction();
     */
    // tempSystem.addComponent("methane", 70.78);
    // tempSystem.addComponent("nC10", 5.83);
    // tempSystem.createDatabase(true);
    // tempSystem.setMixingRule(2);

    // SystemInterface tempSystem = new SystemSrkEos(273.15 + 120, 100.0);
    tempSystem.addComponent("nitrogen", 0.34);
    tempSystem.addComponent("CO2", 3.59);
    tempSystem.addComponent("methane", 67.42);
    tempSystem.addComponent("ethane", 9.02);
    tempSystem.addComponent("propane", 4.31);
    tempSystem.addComponent("i-butane", 0.93);
    tempSystem.addComponent("n-butane", 1.71);
    tempSystem.addComponent("i-pentane", 0.74);
    tempSystem.addComponent("n-pentane", 0.85);
    tempSystem.addComponent("n-hexane", 1.38);
    tempSystem.addTBPfraction("C7", 1.5, 109.00 / 1000.0, 0.6912);
    tempSystem.addTBPfraction("C8", 1.69, 120.20 / 1000.0, 0.7255);
    tempSystem.addTBPfraction("C9", 1.14, 129.5 / 1000.0, 0.7454);
    tempSystem.addTBPfraction("C10", 0.8, 135.3 / 1000.0, 0.7864);
    tempSystem.addPlusFraction("C11", 4.58, 256.2 / 1000.0, 0.8398);
    // tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);
    tempSystem.init(0);
    tempSystem.init(1);

    ConstantVolumeDepletion CVDsim = new ConstantVolumeDepletion(tempSystem);
    CVDsim.setTemperature(315.0, "K");
    CVDsim.setPressures(new double[] {400, 300.0, 200.0, 150.0, 100.0, 50.0});
    CVDsim.runCalc();
    CVDsim.setTemperaturesAndPressures(new double[] {313, 313, 313, 313}, new double[] {400, 300.0, 200.0, 100.0});
    double[][] expData = {{0.95, 0.99, 1.0, 1.1}};
    CVDsim.setExperimentalData(expData);
    // CVDsim.runTuning();
  }

  /**
   * Getter for the field <code>relativeVolume</code>.
   *
   * @return the relativeVolume
   */
  public double[] getRelativeVolume() {
    return relativeVolume;
  }

  /** {@inheritDoc} */
  @Override
  public double getSaturationPressure() {
    return saturationPressure;
  }

  /**
   * getZmix.
   *
   * @return the Zmix
   */
  public double[] getZmix() {
    return Zmix;
  }

  /**
   * getZgas.
   *
   * @return the Zgas
   */
  public double[] getZgas() {
    return Zgas;
  }

  /**
   * Getter for the field <code>liquidRelativeVolume</code>.
   *
   * @return the liquidRelativeVolume
   */
  public double[] getLiquidRelativeVolume() {
    return liquidRelativeVolume;
  }

  /**
   * Getter for the field <code>cummulativeMolePercDepleted</code>.
   *
   * @return the cummulativeMolePercDepleted
   */
  public double[] getCummulativeMolePercDepleted() {
    return cummulativeMolePercDepleted;
  }

  // ============================================================================
  // QC/QA Methods per Whitson methodology (https://wiki.whitson.com/phase_behavior/pvt_exp/CVD/)
  // ============================================================================

  /**
   * Calculate equilibrium K-values at each pressure step.
   *
   * <p>
   * K-values are calculated from the equilibrium liquid and gas compositions: Ki = yi / xi
   * </p>
   *
   * <p>
   * This is a key QC check per Whitson methodology - K-value plots should show smooth, consistent trends. Erratic
   * K-values indicate data quality issues.
   * </p>
   *
   * @return 2D array of K-values [pressure_index][component_index], or null if not calculated
   */
  public double[][] calculateKValues() {
    if (pressures == null || pressures.length == 0) {
      return null;
    }

    int nComp = getThermoSystem().getNumberOfComponents();
    double[][] kValues = new double[pressures.length][nComp];

    // Store original state
    double origTemp = getThermoSystem().getTemperature();
    double origPres = getThermoSystem().getPressure();

    if (!Double.isNaN(temperature)) {
      getThermoSystem().setTemperature(temperature, temperatureUnit);
    }

    for (int i = 0; i < pressures.length; i++) {
      getThermoSystem().setPressure(pressures[i]);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        continue;
      }

      if (getThermoSystem().getNumberOfPhases() > 1) {
        for (int j = 0; j < nComp; j++) {
          double yi = getThermoSystem().getPhase(0).getComponent(j).getx();
          double xi = getThermoSystem().getPhase(1).getComponent(j).getx();
          kValues[i][j] = (xi > 1e-20) ? yi / xi : 0.0;
        }
      } else {
        // Single phase - K-values are 1.0
        for (int j = 0; j < nComp; j++) {
          kValues[i][j] = 1.0;
        }
      }
    }

    // Restore original state
    getThermoSystem().setTemperature(origTemp);
    getThermoSystem().setPressure(origPres);

    return kValues;
  }

  /**
   * Validate material balance using the wet-gas material balance approach per Whitson.
   *
   * <p>
   * This QC check verifies that the cumulative moles produced match the expected values based on the gas removed at
   * each step. A tolerance of 1-2% is typically acceptable.
   * </p>
   *
   * @param tolerance acceptable relative error (e.g., 0.01 for 1%)
   * @return true if material balance is satisfied within tolerance
   */
  public boolean validateMaterialBalance(double tolerance) {
    if (!Double.isFinite(tolerance) || tolerance < 0.0 || cummulativeMolePercDepleted == null
        || cummulativeMolePercDepleted.length == 0 || materialBalanceRelativeError == null) {
      return false;
    }

    for (int i = 0; i < materialBalanceRelativeError.length; i++) {
      if (!Double.isFinite(materialBalanceRelativeError[i]) || materialBalanceRelativeError[i] > tolerance) {
        logger.warn("CVD QC: Material-balance error {} exceeds tolerance {} at step {}",
            materialBalanceRelativeError[i], tolerance, i);
        return false;
      }
    }

    // Check that cumulative depletion is monotonically increasing
    for (int i = 1; i < cummulativeMolePercDepleted.length; i++) {
      if (cummulativeMolePercDepleted[i] < cummulativeMolePercDepleted[i - 1]) {
        logger.warn("CVD QC: Cumulative depletion not monotonically increasing at step " + i);
        return false;
      }
    }

    // Check that final depletion is reasonable (typically < 95%)
    double finalDepletion = cummulativeMolePercDepleted[cummulativeMolePercDepleted.length - 1];
    if (finalDepletion > 99.0) {
      logger.warn("CVD QC: Final depletion exceeds 99%: " + finalDepletion);
      return false;
    }

    return true;
  }

  /**
   * Calculate gas density at each pressure step using the real gas law.
   *
   * <p>
   * Gas density is calculated as: rho_g = (P * M_g) / (Z * R * T)
   * </p>
   *
   * <p>
   * This QC method allows comparison with reported gas densities to validate Z-factor and molecular weight consistency.
   * </p>
   *
   * @return array of calculated gas densities in kg/m³
   */
  public double[] calculateGasDensityQC() {
    if (pressures == null || Zgas == null) {
      return null;
    }

    double[] gasDensity = new double[pressures.length];
    double R = 8.314; // J/(mol·K)

    for (int i = 0; i < pressures.length; i++) {
      if (Zgas[i] > 0 && getThermoSystem().getPhase(0) != null) {
        double P = pressures[i] * 1e5; // bar to Pa
        double T = !Double.isNaN(temperature) ? temperature : getThermoSystem().getTemperature();
        double Mg = getThermoSystem().getPhase(0).getMolarMass(); // kg/mol
        gasDensity[i] = (P * Mg) / (Zgas[i] * R * T);
      }
    }

    return gasDensity;
  }

  /**
   * Calculate oil density at each pressure step using material balance.
   *
   * <p>
   * Per Whitson methodology, oil density is back-calculated from cell volume, liquid volume, and moles remaining. This
   * serves as a QC check for the consistency of CVD data.
   * </p>
   *
   * @return array of calculated oil densities in kg/m³, or null if calculation not possible
   */
  public double[] calculateOilDensityQC() {
    if (pressures == null || liquidVolume == null || totalVolume == null) {
      return null;
    }

    double[] oilDensity = new double[pressures.length];

    // Store original state
    double origTemp = getThermoSystem().getTemperature();
    double origPres = getThermoSystem().getPressure();

    if (!Double.isNaN(temperature)) {
      getThermoSystem().setTemperature(temperature, temperatureUnit);
    }

    for (int i = 0; i < pressures.length; i++) {
      getThermoSystem().setPressure(pressures[i]);
      try {
        thermoOps.TPflash();
        if (getThermoSystem().getNumberOfPhases() > 1) {
          oilDensity[i] = getThermoSystem().getPhase("oil").getDensity("kg/m3");
        }
      } catch (Exception ex) {
        logger.debug(ex.getMessage());
      }
    }

    // Restore original state
    getThermoSystem().setTemperature(origTemp);
    getThermoSystem().setPressure(origPres);

    return oilDensity;
  }

  /**
   * Get liquid dropout curve (liquid volume percentage vs pressure).
   *
   * <p>
   * The liquid dropout curve is a key result from CVD experiments for gas condensate fluids. It shows the percentage of
   * liquid condensed at each pressure below the dew point.
   * </p>
   *
   * @return array of liquid dropout percentages (volume %)
   */
  public double[] getLiquidDropoutCurve() {
    return liquidRelativeVolume;
  }

  /**
   * Generate a CVD QC report as formatted text.
   *
   * <p>
   * This method produces a comprehensive QC report following Whitson methodology, including:
   * <ul>
   * <li>Saturation conditions</li>
   * <li>Relative volume at each pressure</li>
   * <li>Liquid dropout curve</li>
   * <li>Z-factor consistency</li>
   * <li>Material balance validation</li>
   * </ul>
   *
   * @return formatted QC report string
   */
  public String generateQCReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== CVD Quality Control Report ===\n\n");

    // Saturation conditions
    sb.append("Saturation Conditions:\n");
    sb.append(String.format("  Saturation Pressure: %.2f bar\n", saturationPressure));
    sb.append(String.format("  Saturation Volume: %.4f m³\n", saturationVolume));
    sb.append(String.format("  Z at Saturation: %.4f\n", Zsaturation));
    sb.append("\n");

    // Results table
    sb.append("Pressure Step Results:\n");
    sb.append(String.format("%10s %12s %12s %12s %12s\n", "P (bar)", "Vrel", "Liq Vol %", "Z-gas", "Depleted %"));
    sb.append(StringUtils.repeat("-", 60) + "\n");

    if (pressures != null) {
      for (int i = 0; i < pressures.length; i++) {
        double vrel = (relativeVolume != null && i < relativeVolume.length) ? relativeVolume[i] : Double.NaN;
        double liqVol = (liquidRelativeVolume != null && i < liquidRelativeVolume.length) ? liquidRelativeVolume[i]
            : Double.NaN;
        double zg = (Zgas != null && i < Zgas.length) ? Zgas[i] : Double.NaN;
        double depl = (cummulativeMolePercDepleted != null && i < cummulativeMolePercDepleted.length)
            ? cummulativeMolePercDepleted[i]
            : Double.NaN;

        sb.append(String.format("%10.2f %12.4f %12.2f %12.4f %12.2f\n", pressures[i], vrel, liqVol, zg, depl));
      }
    }
    sb.append("\n");

    // Material balance check
    boolean mbPassed = validateMaterialBalance(0.02);
    sb.append("Material Balance Check: " + (mbPassed ? "PASSED" : "FAILED") + "\n");

    return sb.toString();
  }
}
