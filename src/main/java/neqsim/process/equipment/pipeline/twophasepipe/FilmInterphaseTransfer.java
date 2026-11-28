package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.Arrays;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.AnnularFlow;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.BubbleFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.DropletFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.SlugFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Non-equilibrium mass transfer between gas, oil and an aqueous phase from the multicomponent film model of the
 * fluid-mechanics package.
 *
 * <p>
 * Each wet cell is handed to a two-phase flow node with the transported gas and oil compositions, the cell pressure and
 * temperature, the phase volume fractions and the phase speeds. Its Krishna-Standart fluid boundary solves the
 * Maxwell-Stefan film equations on both sides of the interface together with interface equilibrium and the interface
 * heat balance. Nothing is fitted here: transfer coefficients come from the flow state through the correlations of the
 * film model.
 * </p>
 *
 * <p>
 * Three transfer laws turn the film solution into component sources:
 * </p>
 * <ul>
 * <li>{@link TransferLaw#FILM_FLUX_RELAXATION} (default). A component whose Maxwell-Stefan film flux points toward its
 * inventory at local equilibrium approaches that inventory exponentially, {@code dm = dm_eq (1 - exp(-kappa dt))}, with
 * the rate constant {@code kappa = N w / dm_eq} taken at the film solve and held until the next one. For a short step
 * after a film solve this is the film flux, with its cross-diffusion and finite-flux effects; for a long step it is
 * local equilibrium, so it is stable on pipeline time steps. The film is solved again after the update interval, or
 * sooner when the distance to equilibrium has doubled. A component whose film flux points away from its equilibrium
 * inventory is held until the two agree: carrying it would move the cell away from equilibrium for as long as the film
 * solution is held, and near the dew point that fills the pipe with liquid.</li>
 * <li>{@link TransferLaw#TWO_RESISTANCE_RELAXATION}. Each component relaxes toward its inventory at local equilibrium
 * with the rate constant of two finite compartments exchanging through two film resistances,
 * {@code kappa = K_ov (w / A_G) (1 + K n_G / n_L)}, {@code 1 / K_ov = 1 / k_G + K c_G / (c_L k_L)}. It uses effective
 * coefficients only. Against the film flux its initial rates were 2 to 8 times higher for butane to heptane in a rich
 * gas at 80 bara and wrong in sign for ethane and propane.</li>
 * <li>{@link TransferLaw#FILM_FLUX}. The Maxwell-Stefan fluxes times the interface width are applied directly. It is
 * explicit: use it only with steps well below the liquid-layer time scale {@code depth / k_L} (seconds).</li>
 * </ul>
 *
 * <p>
 * Scope and assumptions:
 * </p>
 * <ul>
 * <li>Gas with oil, with an aqueous phase (water, glycols, alcohols; no ions), or with both. Each liquid has its own
 * film with the gas, solved at its own holdup and speed. Two liquids share the interface in proportion to their volume.
 * Where a component must leave one liquid and enter the other, the smaller of the two amounts passes directly between
 * the liquids with the liquid-liquid relaxation time (default 30 s); no liquid-liquid coefficient is calculated. A
 * fluid with water needs an equation of state that describes it, for example CPA with the multiphase check on.</li>
 * <li>One temperature per cell. The interface temperature is solved, but gas and oil share the cell temperature, so the
 * conservative total-energy equation owns the latent heat.</li>
 * <li>Interface width: the stratified chord, or the gas-core perimeter in annular and mist flow. With wall condensation
 * enabled (default) a cell whose net transfer is condensation uses the gas-core perimeter, on the assumption that
 * condensing vapour wets the whole wall before the liquid drains to the bottom.</li>
 * <li>Phase appearance, and cells whose gas or oil volume fraction is below the minimum holdup, cannot form a film.
 * They relax toward local equilibrium with the fallback relaxation time. So does a cell whose film solve fails.</li>
 * <li>Where the cell composition is single phase at equilibrium (the gas can hold all of the liquid, or the reverse),
 * the phase holding less mass can only lose components: film fluxes toward it are set to zero. An interface still
 * exists there as long as some mixture of the two phases splits, and the film is solved.</li>
 * <li>Miscible contact. Before a film solve, mixtures of the gas and the oil of the cell (10 to 90 mol% oil) are
 * flashed. If none splits, the two are miscible at first contact and there is no interface: the cell is in
 * {@link CellMode#MISCIBLE} and the phase it should lose mixes into the other with the radial turbulent mixing time of
 * the pipe, {@code R / (0.07 u*)}, not shorter than the fallback relaxation time. Stable stratification is not
 * accounted for.</li>
 * <li>Bulk phase split (on by default). Before the film is solved, the gas inventory of the cell is flashed on its own
 * at the cell pressure and temperature, and so is each liquid inventory. Liquid that the gas forms by itself is fog,
 * hydrocarbon or aqueous: it nucleates in the bulk and needs no interface. Vapour that a liquid forms by itself is
 * flashing. All are moved with the bulk split time (default 30 s, the default equilibrium relaxation time of the pipe);
 * the flashes are repeated every third of that time. The film then acts between phases that are each stable, and
 * carries only what is left of the distance to local equilibrium. Without this split, condensation during a pressure
 * fall would be limited by diffusion of the heavy components through the gas to the wall, which is not how a retrograde
 * gas condenses.</li>
 * <li>Entrained droplets can be added as a second interface (off by default): entrained fraction and Sauter diameter
 * from {@link EntrainmentDeposition}, Ranz-Marshall outside the droplet and the rigid-sphere limit inside it. Droplets
 * are not transported as a separate field; the entrained fraction is the local equilibrium value. The entrainment
 * correlation puts almost all liquid in droplets in large high-pressure lines, so treat the result with droplets as the
 * upper bound of the interfacial area and the result without them as the lower bound.</li>
 * <li>Every flow regime has its own film node and interface (switchable): a liquid layer in stratified flow and in
 * cells classified as single-phase gas that hold a trace of liquid; the slug-flow node on the layer in slug and churn
 * flow; a wall film on the gas-core perimeter in annular flow; droplets in mist flow (Sauter diameter from
 * {@link EntrainmentDeposition}, settling speed as slip, Ranz-Marshall outside and Kronig-Brink inside); bubbles in
 * bubble and dispersed-bubble flow and in cells classified as single-phase liquid (Hinze diameter, Harmathy rise
 * speed). The regime of a cell is the one the pipe assigns; transitions are not blended. In layer and film regimes the
 * gas side follows the Chilton-Colburn analogy on the interfacial friction factor and includes natural convection where
 * the gas is unstably stratified.</li>
 * </ul>
 */
public final class FilmInterphaseTransfer implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double MAX_SOURCE_FRACTION = 0.5;
  private static final double MINIMUM_NODE_SPEED = 1.0e-6;
  /** A cell whose film solve failed is not tried again before this many update intervals have passed. */
  private static final double FAILURE_RETRY_FACTOR = 4.0;
  /** Largest |ln K| of an interface whose two sides count as one phase. */
  private static final double MISCIBLE_LOG_EQUILIBRIUM_RATIO = 0.01;
  /** Eddy diffusivity of turbulent pipe flow over friction velocity times radius. */
  private static final double EDDY_DIFFUSIVITY_FACTOR = 0.07;
  /** Oil mole shares of the gas/oil mixtures flashed to test for miscibility. */
  private static final double[] MISCIBILITY_OIL_SHARES = {0.5, 0.25, 0.75, 0.1, 0.9};
  /** Liquid to gas density ratio below which two hydrocarbon phases count as one for the miscibility test. */
  private static final double NEAR_CRITICAL_DENSITY_RATIO = 1.25;
  /** Index of the oil among the liquids of a cell. */
  private static final int OIL = 0;
  /** Index of the aqueous phase among the liquids of a cell. */
  private static final int AQUEOUS = 1;
  private static final int LIQUIDS = 2;
  /** Distance to equilibrium, relative to the liquid a cell holds, below which a grown distance does not matter. */
  private static final double MINIMUM_RELATIVE_DISTANCE = 1.0e-6;

  /** How a film solution is turned into component sources. */
  public enum TransferLaw {
    /**
     * Exponential approach to local equilibrium whose initial rate is the Maxwell-Stefan film flux of the component.
     */
    FILM_FLUX_RELAXATION,
    /** Exponential relaxation toward local equilibrium with two-resistance rate constants from the film solve. */
    TWO_RESISTANCE_RELAXATION,
    /** Maxwell-Stefan film fluxes applied directly (explicit). */
    FILM_FLUX
  }

  /** How the source of a cell was obtained. */
  public enum CellMode {
    /** No transfer: the cell holds a single phase at equilibrium. */
    NONE,
    /** Multicomponent film fluxes. */
    FILM,
    /** Relaxation toward local equilibrium (appearance, trace phase or failed film solve). */
    RELAXATION,
    /**
     * The gas and the oil of the cell are miscible at its pressure and temperature: the film solution has identical
     * phases on the two sides of the interface. The oil mixes into the gas with the turbulent mixing time.
     */
    MISCIBLE
  }

  private double updateIntervalSeconds = 0.0;
  private double minimumHoldup = 1.0e-7;
  private double fallbackRelaxationTimeSeconds = 30.0;
  private boolean wallCondensation = true;
  private boolean finiteFluxCorrection = true;
  private boolean thermodynamicCorrection = true;
  private double interfaceAreaFactor = 1.0;
  private TransferLaw transferLaw = TransferLaw.FILM_FLUX_RELAXATION;
  private boolean regimeSpecificFilm = true;
  private boolean dropletField = false;
  private double miscibleMixingTimeSeconds = Double.NaN;
  private boolean bulkPhaseSplit = true;
  private double bulkPhaseSplitTimeSeconds = 30.0;
  private double liquidLiquidRelaxationTimeSeconds = 30.0;

  /** Mass fluxes of the last film solve, by cell, liquid and component. */
  private double[][][] cachedMassFluxKgPerSquareMetreSecond;
  private double[][] cachedTimeSeconds;
  private double[][] cachedInterfaceTemperatureK;
  private double[][][] cachedOverallCoefficientMetresPerSecond;
  private double[][][] cachedEquilibriumRatio;
  private double[][] cachedMolarMassKgPerMol;
  private double[][][] cachedDropletOverallCoefficientMetresPerSecond;
  private double[][] cachedEntrainedFraction;
  private double[][] cachedDropletDiameterMetres;
  private boolean[][] cachedFilm;
  private double[] cachedMixingTimeSeconds;
  private double[][] cachedRetryTimeSeconds;
  /** What each phase of a cell shed in its last bulk split, by cell, shedding phase, receiving phase and component. */
  private double[][][][] cachedShedKg;
  private double[][] cachedSplitTimeSeconds;
  /** Distance to equilibrium of every component at the last film solve, by cell, liquid and component. */
  private double[][][] cachedTargetKgPerMetre;
  private double[][] cachedDistanceKgPerMetre;
  /** True while the cached fluxes of a cell and liquid come from a converged film, not from the miscibility test. */
  private boolean[][] cachedSolution;
  /** Surface of the droplets or bubbles of a cell in m2 per m of pipe; zero where the liquid forms a layer or film. */
  private double[][] cachedDispersedAreaSquareMetresPerMetre;
  /** True where the liquid of a cell travels as droplets (mist flow). */
  private boolean[][] cachedDispersedLiquid;
  private long filmSolveCount;
  private long filmFailureCount;
  private long bulkSplitFailureCount;
  private String lastFailure = "";

  /** Sources and diagnostics of one evaluation. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double[][][] sources;
    private final CellMode[][] modes;
    private final double[][] interfaceWidths;
    private final double[] netMassFluxes;
    private final double[] interfaceTemperatures;
    private final double[] slowestTimeConstants;
    private final double[][] characteristicTimes;
    private final double[] entrainedFractions;
    private final double[] dropletAreas;
    private final boolean[] donorLimited;
    private final boolean[] equilibriumLimited;
    private final boolean[] counterEquilibrium;
    private final double[][] fogRates;
    private final double[][] flashingRates;
    private final double[] liquidLiquidRates;
    private final double[] mixingTimes;
    private final long filmSolveCount;
    private final long filmFailureCount;
    private final String lastFailure;

    private Result(double[][][] sources, CellMode[][] modes, double[][] interfaceWidths, double[] netMassFluxes,
        double[] interfaceTemperatures, double[] slowestTimeConstants, double[][] characteristicTimes,
        double[] entrainedFractions, double[] dropletAreas, boolean[] donorLimited, boolean[] equilibriumLimited,
        boolean[] counterEquilibrium, double[][] fogRates, double[][] flashingRates, double[] liquidLiquidRates,
        double[] mixingTimes, long filmSolveCount, long filmFailureCount, String lastFailure) {
      this.sources = sources;
      this.modes = modes;
      this.interfaceWidths = interfaceWidths;
      this.netMassFluxes = netMassFluxes;
      this.interfaceTemperatures = interfaceTemperatures;
      this.slowestTimeConstants = slowestTimeConstants;
      this.characteristicTimes = characteristicTimes;
      this.entrainedFractions = entrainedFractions;
      this.dropletAreas = dropletAreas;
      this.donorLimited = donorLimited;
      this.equilibriumLimited = equilibriumLimited;
      this.counterEquilibrium = counterEquilibrium;
      this.fogRates = fogRates;
      this.flashingRates = flashingRates;
      this.liquidLiquidRates = liquidLiquidRates;
      this.mixingTimes = mixingTimes;
      this.filmSolveCount = filmSolveCount;
      this.filmFailureCount = filmFailureCount;
      this.lastFailure = lastFailure;
    }

    /**
     * Component sources of the gas, the oil and the aqueous phase.
     *
     * @return cell/phase/component sources in kg/(m s); the gas source is the exact negative of oil plus aqueous
     */
    public double[][][] getSourcesKgPerMetreSecond() {
      double[][][] copy = new double[sources.length][][];
      for (int cell = 0; cell < sources.length; cell++) {
        copy[cell] = new double[sources[cell].length][];
        for (int phase = 0; phase < sources[cell].length; phase++) {
          copy[cell][phase] = sources[cell][phase].clone();
        }
      }
      return copy;
    }

    /** @return how the gas/oil source of each cell was obtained */
    public CellMode[] getModes() {
      return modes[OIL].clone();
    }

    /** @return how the gas/aqueous source of each cell was obtained */
    public CellMode[] getAqueousModes() {
      return modes[AQUEOUS].clone();
    }

    /** @return gas/oil interface width used in each film cell, in m of interface per m of pipe */
    public double[] getInterfaceWidthsMetres() {
      return interfaceWidths[OIL].clone();
    }

    /** @return gas/aqueous interface width used in each film cell, in m of interface per m of pipe */
    public double[] getAqueousInterfaceWidthsMetres() {
      return interfaceWidths[AQUEOUS].clone();
    }

    /** @return net film mass flux of each film cell in kg/(m2 s), positive from gas to oil */
    public double[] getNetMassFluxesKgPerSquareMetreSecond() {
      return netMassFluxes.clone();
    }

    /** @return solved interface temperature of each film cell in K, NaN elsewhere */
    public double[] getInterfaceTemperaturesK() {
      return interfaceTemperatures.clone();
    }

    /**
     * Time each film cell needs to cover its distance to local equilibrium at its present rates: the sum over the
     * components moving toward equilibrium of their equilibrium inventory change, divided by the sum of their initial
     * transfer rates.
     *
     * @return characteristic time of each film cell in s; NaN where no film is solved or nothing moves
     */
    public double[] getCharacteristicTimesSeconds() {
      return characteristicTimes[OIL].clone();
    }

    /**
     * Time the gas/aqueous film of each cell needs to cover its distance to local equilibrium at its present rates.
     *
     * @return characteristic time in s; NaN where no gas/aqueous film is solved or nothing moves
     */
    public double[] getAqueousCharacteristicTimesSeconds() {
      return characteristicTimes[AQUEOUS].clone();
    }

    /**
     * Longest time constant among the major movers of a film cell: the components that move toward equilibrium and
     * whose equilibrium inventory change is at least a tenth of the largest one. In a gas-condensate cell these are the
     * heavy components, which decide how fast the liquid amount follows equilibrium.
     *
     * @return longest component time constant of each film cell in s; NaN elsewhere
     */
    public double[] getSlowestTimeConstantsSeconds() {
      return slowestTimeConstants.clone();
    }

    /** @return fraction of the liquid carried as droplets in each film cell, zero elsewhere */
    public double[] getEntrainedFractions() {
      return entrainedFractions.clone();
    }

    /** @return droplet surface area of each film cell in m2 per m of pipe, zero elsewhere */
    public double[] getDropletAreasSquareMetresPerMetre() {
      return dropletAreas.clone();
    }

    /** @return cells where a component source was cut to half of its donor inventory per step */
    public boolean[] getDonorLimited() {
      return donorLimited.clone();
    }

    /** @return cells where a held film flux was cut so that it does not pass local equilibrium within the step */
    public boolean[] getEquilibriumLimited() {
      return equilibriumLimited.clone();
    }

    /**
     * Cells where the film flux of at least one component pointed away from its inventory at local equilibrium. Such a
     * component is held, not carried.
     *
     * @return flag per cell
     */
    public boolean[] getCounterEquilibrium() {
      return counterEquilibrium.clone();
    }

    /**
     * Hydrocarbon liquid formed in the bulk of the gas, without an interface.
     *
     * @return gas-to-oil rate per cell in kg/(m s) from the bulk split of the gas inventory
     */
    public double[] getFogRatesKgPerMetreSecond() {
      return fogRates[OIL].clone();
    }

    /**
     * Aqueous liquid formed in the bulk of the gas, without an interface.
     *
     * @return gas-to-aqueous rate per cell in kg/(m s) from the bulk split of the gas inventory
     */
    public double[] getAqueousFogRatesKgPerMetreSecond() {
      return fogRates[AQUEOUS].clone();
    }

    /**
     * Vapour formed in the bulk of the oil, without an interface.
     *
     * @return oil-to-gas rate per cell in kg/(m s) from the bulk split of the oil inventory
     */
    public double[] getFlashingRatesKgPerMetreSecond() {
      return flashingRates[OIL].clone();
    }

    /**
     * Vapour formed in the bulk of the aqueous phase, without an interface.
     *
     * @return aqueous-to-gas rate per cell in kg/(m s) from the bulk split of the aqueous inventory
     */
    public double[] getAqueousFlashingRatesKgPerMetreSecond() {
      return flashingRates[AQUEOUS].clone();
    }

    /**
     * Net transfer that passes directly between the two liquids: what one liquid sheds as the other in its bulk, and
     * the exchange of components that oil and aqueous phase must move in opposite directions.
     *
     * @return oil-to-aqueous rate per cell in kg/(m s), negative from aqueous to oil
     */
    public double[] getLiquidLiquidRatesKgPerMetreSecond() {
      return liquidLiquidRates.clone();
    }

    /**
     * Mixing time of the cells whose gas and oil are miscible.
     *
     * @return time per cell in s, {@code NaN} where the cell is not in {@link CellMode#MISCIBLE}
     */
    public double[] getMiscibleMixingTimesSeconds() {
      return mixingTimes.clone();
    }

    /** @return film solves made by the closure so far */
    public long getFilmSolveCount() {
      return filmSolveCount;
    }

    /** @return film solves that failed and fell back to relaxation so far */
    public long getFilmFailureCount() {
      return filmFailureCount;
    }

    /** @return message of the most recent failed film solve, or an empty string */
    public String getLastFailure() {
      return lastFailure;
    }
  }

  /**
   * Hold the film fluxes of a cell for this long before solving the film again.
   *
   * @param seconds nonnegative interval in s; zero solves at every evaluation
   */
  public void setUpdateInterval(double seconds) {
    if (!Double.isFinite(seconds) || seconds < 0.0) {
      throw new IllegalArgumentException("Film update interval must be finite and nonnegative");
    }
    updateIntervalSeconds = seconds;
  }

  /** @return interval in s over which film fluxes are held */
  public double getUpdateInterval() {
    return updateIntervalSeconds;
  }

  /**
   * Set the smallest gas and oil volume fraction for which a film is solved.
   *
   * @param holdup volume fraction in (0, 0.5)
   */
  public void setMinimumHoldup(double holdup) {
    if (!(holdup > 0.0) || !(holdup < 0.5)) {
      throw new IllegalArgumentException("Minimum film holdup must lie in (0, 0.5)");
    }
    minimumHoldup = holdup;
  }

  /** @return smallest gas and oil volume fraction for which a film is solved */
  public double getMinimumHoldup() {
    return minimumHoldup;
  }

  /**
   * Set the relaxation time used where no film can be solved.
   *
   * @param seconds positive finite time in s
   */
  public void setFallbackRelaxationTime(double seconds) {
    if (!(seconds > 0.0) || !Double.isFinite(seconds)) {
      throw new IllegalArgumentException("Fallback relaxation time must be positive and finite");
    }
    fallbackRelaxationTimeSeconds = seconds;
  }

  /** @return relaxation time in s used where no film can be solved */
  public double getFallbackRelaxationTime() {
    return fallbackRelaxationTimeSeconds;
  }

  /**
   * Choose the interface of a condensing cell.
   *
   * @param enabled true to let net condensation act on the gas-core perimeter, false to use the regime interface
   */
  public void setWallCondensation(boolean enabled) {
    wallCondensation = enabled;
  }

  /** @return true when net condensation acts on the gas-core perimeter */
  public boolean isWallCondensation() {
    return wallCondensation;
  }

  /**
   * Scale the interface width, for sensitivity studies of the interfacial area.
   *
   * @param factor positive finite multiplier; one leaves the geometric width unchanged
   */
  public void setInterfaceAreaFactor(double factor) {
    if (!(factor > 0.0) || !Double.isFinite(factor)) {
      throw new IllegalArgumentException("Interface area factor must be positive and finite");
    }
    interfaceAreaFactor = factor;
  }

  /** @return multiplier on the geometric interface width */
  public double getInterfaceAreaFactor() {
    return interfaceAreaFactor;
  }

  /**
   * Choose the film node and interface of each flow regime.
   *
   * @param enabled true to solve annular cells with the annular-flow node (Wallis interfacial friction), slug and churn
   * cells with the slug-flow node, mist cells as droplets and bubble cells as bubbles; false to use the stratified-flow
   * node in every regime, on the gas-core perimeter in annular and mist cells and on the chord of the liquid layer
   * elsewhere
   */
  public void setRegimeSpecificFilm(boolean enabled) {
    regimeSpecificFilm = enabled;
  }

  /** @return true when annular, mist and slug cells use their own film nodes */
  public boolean isRegimeSpecificFilm() {
    return regimeSpecificFilm;
  }

  /**
   * Include entrained droplets as a second interface.
   *
   * <p>
   * The entrained fraction (Ishii and Mishima) and the Sauter diameter (critical Weber number) come from
   * {@link EntrainmentDeposition}. The droplets add the area {@code 6 E alpha_L A / d32} per metre of pipe, with a
   * gas-side coefficient from Ranz and Marshall at the droplet settling speed and a liquid-side coefficient for a rigid
   * sphere, {@code Sh = 6.58}. The droplet conductance is added to the film conductance; the film flux of a component
   * is raised in the same proportion.
   * </p>
   *
   * @param enabled true to add the droplet interface where the gas entrains liquid; default false
   */
  public void setDropletField(boolean enabled) {
    dropletField = enabled;
  }

  /** @return true when entrained droplets contribute to the transfer */
  public boolean isDropletField() {
    return dropletField;
  }

  /**
   * Switch the bulk phase split.
   *
   * <p>
   * With the split, the gas inventory of a cell is flashed on its own at the cell pressure and temperature, and so is
   * the oil inventory. Liquid formed by the gas alone (fog) and vapour formed by the oil alone (flashing) are moved
   * with the bulk split time, and the film is solved between the two stable phases that remain. Without it every
   * transfer has to cross the interface.
   * </p>
   *
   * @param enabled true to move fog and flashing vapour without an interface; default true
   */
  public void setBulkPhaseSplit(boolean enabled) {
    bulkPhaseSplit = enabled;
  }

  /** @return true when fog and flashing vapour are moved without an interface */
  public boolean isBulkPhaseSplit() {
    return bulkPhaseSplit;
  }

  /**
   * Set the time constant of fog formation and of flashing.
   *
   * @param seconds positive finite time in s; default 30 s
   */
  public void setBulkPhaseSplitTime(double seconds) {
    if (!(seconds > 0.0) || !Double.isFinite(seconds)) {
      throw new IllegalArgumentException("Bulk phase split time must be positive and finite");
    }
    bulkPhaseSplitTimeSeconds = seconds;
  }

  /** @return time constant in s of fog formation and of flashing */
  public double getBulkPhaseSplitTime() {
    return bulkPhaseSplitTimeSeconds;
  }

  /**
   * Set the time constant of the exchange that passes directly between oil and aqueous phase.
   *
   * <p>
   * Where a component must leave one liquid and enter the other (water dissolved in oil dropping out, a glycol washing
   * out of condensate), the smaller of the two amounts is moved from liquid to liquid with this time. No liquid-liquid
   * mass transfer coefficient is calculated.
   * </p>
   *
   * @param seconds positive finite time in s; default 30 s
   */
  public void setLiquidLiquidRelaxationTime(double seconds) {
    if (!(seconds > 0.0) || !Double.isFinite(seconds)) {
      throw new IllegalArgumentException("Liquid-liquid relaxation time must be positive and finite");
    }
    liquidLiquidRelaxationTimeSeconds = seconds;
  }

  /** @return time constant in s of the direct exchange between oil and aqueous phase */
  public double getLiquidLiquidRelaxationTime() {
    return liquidLiquidRelaxationTimeSeconds;
  }

  /**
   * Fix the mixing time of cells whose gas and oil are miscible.
   *
   * <p>
   * Above the critical pressure of the gas/oil mixing line there is no interface. By default such a cell mixes with the
   * radial turbulent mixing time of the pipe, {@code R^2 / D_t} with the eddy diffusivity {@code D_t = 0.07 u* R} and
   * the friction velocity {@code u*} of the gas on a smooth wall, but not faster than the fallback relaxation time.
   * Stable stratification of the denser layer is not accounted for, so the default is a lower estimate of the mixing
   * time at low speed.
   * </p>
   *
   * @param seconds positive finite time in s, or {@code NaN} for the turbulent estimate (default)
   */
  public void setMiscibleMixingTime(double seconds) {
    if (!Double.isNaN(seconds) && (!(seconds > 0.0) || !Double.isFinite(seconds))) {
      throw new IllegalArgumentException("Miscible mixing time must be positive and finite, or NaN");
    }
    miscibleMixingTimeSeconds = seconds;
  }

  /** @return fixed mixing time in s of miscible cells, or {@code NaN} when the turbulent estimate is used */
  public double getMiscibleMixingTime() {
    return miscibleMixingTimeSeconds;
  }

  /**
   * Choose how the film solution becomes component sources.
   *
   * @param law transfer law
   */
  public void setTransferLaw(TransferLaw law) {
    if (law == null) {
      throw new IllegalArgumentException("Transfer law cannot be null");
    }
    transferLaw = law;
  }

  /** @return transfer law in use */
  public TransferLaw getTransferLaw() {
    return transferLaw;
  }

  /**
   * Switch the finite-flux and thermodynamic-factor corrections of the film model.
   *
   * @param finiteFlux true to apply the Krishna-Standart finite-flux correction
   * @param thermodynamicFactor true to apply the thermodynamic factor matrix
   */
  public void setFilmCorrections(boolean finiteFlux, boolean thermodynamicFactor) {
    finiteFluxCorrection = finiteFlux;
    thermodynamicCorrection = thermodynamicFactor;
  }

  /**
   * Evaluate the component sources of every cell.
   *
   * @param transport component inventories of the cells
   * @param sections accepted hydrodynamic cells
   * @param template thermodynamic template holding the component slate
   * @param equilibriumStates flashes of the conserved cell compositions at the cell pressure and temperature
   * @param timeSeconds simulation time of this evaluation in s
   * @param timeStepSeconds step over which the sources will be applied, in s
   * @return sources and diagnostics
   */
  public Result calculate(TwoFluidComponentTransport transport, TwoFluidSection[] sections, SystemInterface template,
      SystemInterface[] equilibriumStates, double timeSeconds, double timeStepSeconds) {
    if (transport == null || sections == null || template == null || equilibriumStates == null
        || !Double.isFinite(timeSeconds) || !Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0) {
      throw new IllegalArgumentException("Film transfer requires inventories, cells, flashes and finite positive time");
    }
    double[][][] targetChanges = transport.createHydrocarbonEquilibriumInventoryChanges(equilibriumStates, sections);
    if (targetChanges == null) {
      // An aqueous phase is present or wanted: every component is partitioned between gas, oil and aqueous phase.
      // With a unit time the relaxation rates are the changes in kg/m.
      targetChanges = transport.createEquilibriumRelaxationSourceRates(equilibriumStates, sections, 1.0);
    }
    String[] names = transport.getComponentNames();
    double[][][] inventory = transport.getInventoryKg();
    int cells = sections.length;
    if (cachedTimeSeconds == null || cachedTimeSeconds.length != cells) {
      cachedMassFluxKgPerSquareMetreSecond = new double[cells][LIQUIDS][];
      cachedTimeSeconds = new double[cells][LIQUIDS];
      cachedInterfaceTemperatureK = new double[cells][LIQUIDS];
      cachedOverallCoefficientMetresPerSecond = new double[cells][LIQUIDS][];
      cachedEquilibriumRatio = new double[cells][LIQUIDS][];
      cachedMolarMassKgPerMol = new double[cells][];
      cachedDropletOverallCoefficientMetresPerSecond = new double[cells][LIQUIDS][];
      cachedEntrainedFraction = new double[cells][LIQUIDS];
      cachedDropletDiameterMetres = new double[cells][LIQUIDS];
      cachedFilm = new boolean[cells][LIQUIDS];
      cachedMixingTimeSeconds = new double[cells];
      cachedRetryTimeSeconds = new double[cells][LIQUIDS];
      cachedShedKg = new double[cells][3][][];
      cachedSplitTimeSeconds = new double[cells][3];
      cachedTargetKgPerMetre = new double[cells][LIQUIDS][];
      cachedDistanceKgPerMetre = new double[cells][LIQUIDS];
      cachedSolution = new boolean[cells][LIQUIDS];
      cachedDispersedAreaSquareMetresPerMetre = new double[cells][LIQUIDS];
      cachedDispersedLiquid = new boolean[cells][LIQUIDS];
      for (int cell = 0; cell < cells; cell++) {
        Arrays.fill(cachedTimeSeconds[cell], Double.NEGATIVE_INFINITY);
        Arrays.fill(cachedRetryTimeSeconds[cell], Double.NEGATIVE_INFINITY);
        Arrays.fill(cachedSplitTimeSeconds[cell], Double.NEGATIVE_INFINITY);
      }
    }
    int count = names.length;
    Work work = new Work(cells, count);
    // Fractions of a distance covered within this step, per second, for the fixed time constants.
    double splitFractionRate = -Math.expm1(-timeStepSeconds / bulkPhaseSplitTimeSeconds) / timeStepSeconds;
    double fallbackFractionRate = -Math.expm1(-timeStepSeconds / fallbackRelaxationTimeSeconds) / timeStepSeconds;
    double liquidLiquidFractionRate = -Math.expm1(-timeStepSeconds / liquidLiquidRelaxationTimeSeconds)
        / timeStepSeconds;

    for (int cell = 0; cell < cells; cell++) {
      TwoFluidSection section = sections[cell];
      double length = section.getLength();
      double[] mass = {sum(inventory[cell][0]), sum(inventory[cell][1]), sum(inventory[cell][2])};
      boolean aqueousCell = mass[2] > 0.0;
      // Where the hydrocarbons of the cell are single phase at equilibrium there is no gas/oil interface for the
      // mixture as a whole and the gas or oil label of the flash is arbitrary. The hydrocarbon phase holding less mass
      // must then disappear into the other: that is the target, and no component may be carried into it.
      boolean singlePhaseEquilibrium = !(hasPhase(equilibriumStates[cell], 0) && hasPhase(equilibriumStates[cell], 1));
      boolean gasMajority = mass[0] >= mass[1];
      if (singlePhaseEquilibrium) {
        moveHydrocarbonTargetToMajority(targetChanges[cell], inventory[cell], length, gasMajority);
      }
      double[] holdup = {aqueousCell ? section.getOilHoldup() : section.getLiquidHoldup(), section.getWaterHoldup()};
      double[] liquidVelocity = {aqueousCell ? section.getOilVelocity() : section.getLiquidVelocity(),
          section.getWaterVelocity()};
      boolean[] filmPossible = new boolean[LIQUIDS];
      double wetHoldup = 0.0;
      for (int liquid = 0; liquid < LIQUIDS; liquid++) {
        filmPossible[liquid] = mass[0] > 0.0 && mass[liquid + 1] > 0.0 && section.getGasHoldup() >= minimumHoldup
            && holdup[liquid] >= minimumHoldup;
        if (filmPossible[liquid]) {
          wetHoldup += holdup[liquid];
        }
      }

      // Bulk split: what each phase sheds when flashed on its own, as the net gain of every phase in kg.
      double[][] split = new double[3][count];
      if ((filmPossible[OIL] || filmPossible[AQUEOUS]) && bulkPhaseSplit) {
        boolean[] closed = {singlePhaseEquilibrium && !gasMajority, singlePhaseEquilibrium && gasMajority, false};
        splitPhases(cell, template, names, inventory[cell], section, mass, filmPossible, closed, timeSeconds,
            splitFractionRate / length, split, work);
      }

      // What is left once fog and flashing vapour have moved. Where oil and aqueous phase must change in opposite
      // directions, the smaller of the two amounts passes directly between the liquids; the rest involves the gas.
      double[][] residual = new double[LIQUIDS][count];
      double[] liquidLiquid = new double[count];
      for (int component = 0; component < count; component++) {
        residual[OIL][component] = targetChanges[cell][1][component] - split[1][component] / length;
        residual[AQUEOUS][component] = targetChanges[cell][2][component] - split[2][component] / length;
        if (residual[OIL][component] * residual[AQUEOUS][component] < 0.0) {
          double amount = Math.min(Math.abs(residual[OIL][component]), Math.abs(residual[AQUEOUS][component]));
          liquidLiquid[component] = residual[AQUEOUS][component] > 0.0 ? amount : -amount;
          residual[OIL][component] += liquidLiquid[component];
          residual[AQUEOUS][component] -= liquidLiquid[component];
          work.liquidLiquidRates[cell] += liquidLiquid[component] * liquidLiquidFractionRate;
        }
      }

      double[][] pairRates = new double[LIQUIDS][count];
      for (int liquid = 0; liquid < LIQUIDS; liquid++) {
        if (filmPossible[liquid]) {
          filmPossible[liquid] = refreshFilm(cell, liquid, section, template, names, inventory[cell], split,
              residual[liquid], mass[liquid + 1] / length, holdup[liquid], liquidVelocity[liquid], timeSeconds,
              timeStepSeconds);
        } else {
          cachedFilm[cell][liquid] = false;
          cachedSolution[cell][liquid] = false;
          cachedDistanceKgPerMetre[cell][liquid] = 0.0;
          if (liquid == OIL) {
            cachedMixingTimeSeconds[cell] = 0.0;
          }
          cachedRetryTimeSeconds[cell][liquid] = Double.NEGATIVE_INFINITY;
        }
        double[] target = residual[liquid];
        if (liquid == OIL && filmPossible[OIL] && cachedMixingTimeSeconds[cell] > 0.0) {
          // No interface: the phase the cell should lose mixes into the other.
          work.modes[OIL][cell] = CellMode.MISCIBLE;
          work.mixingTimes[cell] = Double.isNaN(miscibleMixingTimeSeconds) ? cachedMixingTimeSeconds[cell]
              : miscibleMixingTimeSeconds;
          work.interfaceTemperatures[cell] = cachedInterfaceTemperatureK[cell][OIL];
          double mixingFractionRate = -Math.expm1(-timeStepSeconds / work.mixingTimes[cell]) / timeStepSeconds;
          for (int component = 0; component < count; component++) {
            pairRates[OIL][component] = target[component] * mixingFractionRate;
          }
        } else if (filmPossible[liquid]) {
          work.modes[liquid][cell] = CellMode.FILM;
          double share = wetHoldup > holdup[liquid] ? holdup[liquid] / wetHoldup : 1.0;
          filmRates(cell, liquid, section, inventory[cell], target, holdup[liquid], share,
              singlePhaseEquilibrium && liquid == OIL, timeStepSeconds, pairRates[liquid], work);
        } else {
          // Appearance, a trace phase or a failed film solve.
          boolean any = false;
          for (int component = 0; component < count; component++) {
            pairRates[liquid][component] = target[component] * fallbackFractionRate;
            any |= pairRates[liquid][component] != 0.0;
          }
          work.modes[liquid][cell] = any ? CellMode.RELAXATION : CellMode.NONE;
        }
      }

      for (int component = 0; component < count; component++) {
        double[] rate = new double[3];
        rate[1] = split[1][component] / length * splitFractionRate + pairRates[OIL][component]
            - liquidLiquid[component] * liquidLiquidFractionRate;
        rate[2] = split[2][component] / length * splitFractionRate + pairRates[AQUEOUS][component]
            + liquidLiquid[component] * liquidLiquidFractionRate;
        rate[0] = -(rate[1] + rate[2]);
        // No phase gives up more than half of what it holds of a component within one step.
        double factor = 1.0;
        for (int phase = 0; phase < 3; phase++) {
          if (rate[phase] < 0.0) {
            double limit = MAX_SOURCE_FRACTION * inventory[cell][phase][component] / length / timeStepSeconds;
            factor = Math.min(factor, limit / -rate[phase]);
          }
        }
        work.donorLimited[cell] |= factor < 1.0;
        work.sources[cell][1][component] = rate[1] * factor;
        work.sources[cell][2][component] = rate[2] * factor;
        work.sources[cell][0][component] = -(work.sources[cell][1][component] + work.sources[cell][2][component]);
      }
    }
    return new Result(work.sources, work.modes, work.widths, work.netFluxes, work.interfaceTemperatures,
        work.slowestTimeConstants, work.characteristicTimes, work.entrainedFractions, work.dropletAreas,
        work.donorLimited, work.equilibriumLimited, work.counterEquilibrium, work.fogRates, work.flashingRates,
        work.liquidLiquidRates, work.mixingTimes, filmSolveCount, filmFailureCount, lastFailure);
  }

  /** Arrays filled during one evaluation. */
  private static final class Work {
    private final double[][][] sources;
    private final CellMode[][] modes;
    private final double[][] widths;
    private final double[] netFluxes;
    private final double[] interfaceTemperatures;
    private final double[] slowestTimeConstants;
    private final double[][] characteristicTimes;
    private final double[] entrainedFractions;
    private final double[] dropletAreas;
    private final boolean[] donorLimited;
    private final boolean[] equilibriumLimited;
    private final boolean[] counterEquilibrium;
    private final double[][] fogRates;
    private final double[][] flashingRates;
    private final double[] liquidLiquidRates;
    private final double[] mixingTimes;

    private Work(int cells, int components) {
      sources = new double[cells][3][components];
      modes = new CellMode[LIQUIDS][cells];
      widths = new double[LIQUIDS][cells];
      netFluxes = new double[cells];
      interfaceTemperatures = new double[cells];
      Arrays.fill(interfaceTemperatures, Double.NaN);
      slowestTimeConstants = new double[cells];
      Arrays.fill(slowestTimeConstants, Double.NaN);
      characteristicTimes = new double[LIQUIDS][cells];
      Arrays.fill(characteristicTimes[OIL], Double.NaN);
      Arrays.fill(characteristicTimes[AQUEOUS], Double.NaN);
      entrainedFractions = new double[cells];
      dropletAreas = new double[cells];
      donorLimited = new boolean[cells];
      equilibriumLimited = new boolean[cells];
      counterEquilibrium = new boolean[cells];
      fogRates = new double[LIQUIDS][cells];
      flashingRates = new double[LIQUIDS][cells];
      liquidLiquidRates = new double[cells];
      mixingTimes = new double[cells];
      Arrays.fill(mixingTimes, Double.NaN);
    }
  }

  /**
   * Give the hydrocarbon content of a cell to the hydrocarbon phase that holds more mass.
   *
   * @param target phase/component equilibrium changes of the cell in kg/m, changed in place
   * @param inventory phase/component masses of the cell in kg
   * @param length cell length in m
   * @param gasMajority true when the gas holds at least as much mass as the oil
   */
  private static void moveHydrocarbonTargetToMajority(double[][] target, double[][] inventory, double length,
      boolean gasMajority) {
    for (int component = 0; component < target[0].length; component++) {
      double gasNow = inventory[0][component] / length;
      double oilNow = inventory[1][component] / length;
      double hydrocarbonTarget = gasNow + oilNow + target[0][component] + target[1][component];
      if (gasMajority) {
        target[1][component] = -oilNow;
        target[0][component] = hydrocarbonTarget - gasNow;
      } else {
        target[0][component] = -gasNow;
        target[1][component] = hydrocarbonTarget - oilNow;
      }
    }
  }

  /**
   * Flash every phase of a cell on its own and add what it sheds to the net gain of the other phases.
   *
   * @param cell cell index
   * @param template thermodynamic template
   * @param names transported component names
   * @param inventory phase/component masses of the cell in kg
   * @param section hydrodynamic cell
   * @param mass phase masses of the cell in kg
   * @param filmPossible liquids that are present in an amount that can hold an interface
   * @param closed phases that must not receive anything (the hydrocarbon phase the cell should lose)
   * @param timeSeconds simulation time in s
   * @param rateFactor fraction of a split that moves per second, divided by the cell length in m
   * @param split net gain of every phase and component in kg, added to
   * @param work diagnostics of the evaluation
   */
  private void splitPhases(int cell, SystemInterface template, String[] names, double[][] inventory,
      TwoFluidSection section, double[] mass, boolean[] filmPossible, boolean[] closed, double timeSeconds,
      double rateFactor, double[][] split, Work work) {
    for (int from = 0; from < 3; from++) {
      if (!(mass[from] > 0.0) || from > 0 && !filmPossible[from - 1]) {
        continue;
      }
      // The flashes are held for a third of the split time: far shorter than the time over which the split acts, far
      // longer than a solver substep.
      boolean stale = timeSeconds < cachedSplitTimeSeconds[cell][from]
          || timeSeconds - cachedSplitTimeSeconds[cell][from] >= bulkPhaseSplitTimeSeconds / 3.0;
      if (stale) {
        cachedShedKg[cell][from] = bulkSplit(template, names, inventory[from], section, from);
        cachedSplitTimeSeconds[cell][from] = timeSeconds;
      }
      double[][] shed = cachedShedKg[cell][from];
      if (shed == null) {
        continue;
      }
      for (int to = 0; to < 3; to++) {
        if (to == from || closed[to]) {
          continue;
        }
        double moved = 0.0;
        for (int component = 0; component < names.length; component++) {
          // A held split cannot take more than the phase holds now.
          double amount = Math.min(shed[to][component],
              Math.max(0.0, inventory[from][component] + split[from][component]));
          split[to][component] += amount;
          split[from][component] -= amount;
          moved += amount;
        }
        if (from == 0) {
          work.fogRates[to - 1][cell] += moved * rateFactor;
        } else if (to == 0) {
          work.flashingRates[from - 1][cell] += moved * rateFactor;
        } else {
          work.liquidLiquidRates[cell] += (to == 2 ? moved : -moved) * rateFactor;
        }
      }
    }
  }

  /**
   * Solve the film between the gas and one liquid of a cell again when its held solution is stale.
   *
   * @param cell cell index
   * @param liquid {@link #OIL} or {@link #AQUEOUS}
   * @param section hydrodynamic cell
   * @param template thermodynamic template
   * @param names transported component names
   * @param inventory phase/component masses of the cell in kg
   * @param split net gain of every phase and component from the bulk split in kg
   * @param residual distance of the liquid to local equilibrium per component in kg/m
   * @param liquidMassPerMetre mass of the liquid in kg/m
   * @param holdup volume fraction of the liquid
   * @param liquidSpeed velocity of the liquid in m/s
   * @param timeSeconds simulation time in s
   * @param timeStepSeconds step in s
   * @return true when the cell holds a film solution or a miscibility result for this liquid
   */
  private boolean refreshFilm(int cell, int liquid, TwoFluidSection section, SystemInterface template, String[] names,
      double[][] inventory, double[][] split, double[] residual, double liquidMassPerMetre, double holdup,
      double liquidSpeed, double timeSeconds, double timeStepSeconds) {
    // A time before the cached one means a rejected step was rolled back: solve again.
    boolean rolledBack = timeSeconds < cachedTimeSeconds[cell][liquid];
    // A film solved near equilibrium says little about the rates far from it: solve again once the distance to
    // equilibrium has doubled.
    double distance = 0.0;
    for (double value : residual) {
      distance += Math.abs(value);
    }
    boolean grown = distance > 2.0 * cachedDistanceKgPerMetre[cell][liquid]
        && distance > MINIMUM_RELATIVE_DISTANCE * liquidMassPerMetre;
    boolean stale = rolledBack
        || cachedFilm[cell][liquid] && (timeSeconds - cachedTimeSeconds[cell][liquid] >= updateIntervalSeconds || grown)
        || !cachedFilm[cell][liquid] && timeSeconds >= cachedRetryTimeSeconds[cell][liquid];
    if (!stale) {
      return cachedFilm[cell][liquid];
    }
    try {
      filmSolveCount++;
      double[] interfaceTemperature = new double[1];
      // The film acts between the phases as they are once fog and flashing vapour have moved.
      double[][] stable = new double[2][names.length];
      for (int component = 0; component < names.length; component++) {
        stable[0][component] = Math.max(0.0, inventory[0][component] + split[0][component]);
        stable[1][component] = Math.max(0.0, inventory[liquid + 1][component] + split[liquid + 1][component]);
      }
      if (liquid == OIL && isMiscible(template, names, stable, section)) {
        // No mixture of the two phases splits: there is no interface to solve.
        cachedMixingTimeSeconds[cell] = mixingTime(section);
        cachedSolution[cell][liquid] = false;
        interfaceTemperature[0] = section.getTemperature();
      } else {
        double[] flux = solveFilm(cell, liquid, section, template, names, stable, holdup, liquidSpeed,
            interfaceTemperature);
        cachedMassFluxKgPerSquareMetreSecond[cell][liquid] = flux;
        cachedSolution[cell][liquid] = true;
      }
      cachedInterfaceTemperatureK[cell][liquid] = interfaceTemperature[0];
      cachedTargetKgPerMetre[cell][liquid] = residual.clone();
      cachedDistanceKgPerMetre[cell][liquid] = distance;
      cachedFilm[cell][liquid] = true;
      cachedTimeSeconds[cell][liquid] = timeSeconds;
    } catch (RuntimeException exception) {
      filmFailureCount++;
      lastFailure = "cell " + cell + (liquid == OIL ? "" : " (aqueous)") + " at " + timeSeconds + " s: "
          + exception.getMessage();
      cachedTimeSeconds[cell][liquid] = timeSeconds;
      if (cachedFilm[cell][liquid] && cachedSolution[cell][liquid]) {
        // The rate constants of the last converged film are a property of the flow: they stay in use until the next
        // attempt, one update interval from now.
        cachedDistanceKgPerMetre[cell][liquid] = Math.max(cachedDistanceKgPerMetre[cell][liquid], distance);
      } else {
        cachedFilm[cell][liquid] = false;
        cachedSolution[cell][liquid] = false;
        if (liquid == OIL) {
          cachedMixingTimeSeconds[cell] = 0.0;
        }
        cachedRetryTimeSeconds[cell][liquid] = timeSeconds
            + FAILURE_RETRY_FACTOR * Math.max(updateIntervalSeconds, timeStepSeconds);
      }
    }
    return cachedFilm[cell][liquid];
  }

  /**
   * Turn the held film solution of one liquid of a cell into component rates.
   *
   * @param cell cell index
   * @param liquid {@link #OIL} or {@link #AQUEOUS}
   * @param section hydrodynamic cell
   * @param inventory phase/component masses of the cell in kg
   * @param target distance of the liquid to local equilibrium per component in kg/m
   * @param holdup volume fraction of the liquid
   * @param share share of the gas-liquid interface that this liquid holds
   * @param minorityOnly true when the liquid can only lose components (single-phase hydrocarbon equilibrium)
   * @param timeStepSeconds step in s
   * @param rates component rates from gas to liquid in kg/(m s), filled
   * @param work diagnostics of the evaluation
   */
  private void filmRates(int cell, int liquid, TwoFluidSection section, double[][] inventory, double[] target,
      double holdup, double share, boolean minorityOnly, double timeStepSeconds, double[] rates, Work work) {
    int count = target.length;
    double pipeArea = Math.PI * section.getDiameter() * section.getDiameter() / 4.0;
    double gasArea = section.getGasHoldup() * pipeArea;
    double[] flux = cachedMassFluxKgPerSquareMetreSecond[cell][liquid];
    double netFlux = sum(flux);
    boolean condensing = transferLaw == TransferLaw.FILM_FLUX ? netFlux > 0.0 : sum(target) > 0.0;
    // Two liquids share the interface in proportion to their volume; droplets carry their own surface.
    double dispersedArea = cachedDispersedAreaSquareMetresPerMetre[cell][liquid];
    double width = interfaceAreaFactor
        * (dispersedArea > 0.0 ? dispersedArea * (cachedDispersedLiquid[cell][liquid] ? 1.0 : share)
            : interfaceWidth(section, condensing) * share);
    work.widths[liquid][cell] = width;
    double gasMoles = 0.0;
    double liquidMoles = 0.0;
    for (int component = 0; component < count; component++) {
      gasMoles += inventory[0][component] / cachedMolarMassKgPerMol[cell][component];
      liquidMoles += inventory[liquid + 1][component] / cachedMolarMassKgPerMol[cell][component];
    }
    // Only the major movers set the reported time constant: components whose equilibrium change is at least a tenth
    // of the largest one.
    double significantTarget = 0.0;
    for (double value : target) {
      significantTarget = Math.max(significantTarget, 0.1 * Math.abs(value));
    }
    double dropletArea = 0.0;
    if (dropletField && cachedEntrainedFraction[cell][liquid] > 0.0
        && cachedDropletDiameterMetres[cell][liquid] > 0.0) {
      dropletArea = 6.0 * cachedEntrainedFraction[cell][liquid] * holdup * pipeArea
          / cachedDropletDiameterMetres[cell][liquid];
    }
    double slowest = Double.NaN;
    double distance = 0.0;
    double initialRate = 0.0;
    for (int component = 0; component < count; component++) {
      double filmConductance = cachedOverallCoefficientMetresPerSecond[cell][liquid][component] * width;
      double dropletConductance = cachedDropletOverallCoefficientMetresPerSecond[cell][liquid][component] * dropletArea;
      double rate;
      if (transferLaw == TransferLaw.FILM_FLUX) {
        rate = flux[component] * width;
        if (minorityOnly && rate * target[component] <= 0.0) {
          rate = 0.0;
        }
        // A film flux must not carry a component past its local equilibrium inventory within one step.
        if (rate * target[component] > 0.0 && Math.abs(rate) * timeStepSeconds > Math.abs(target[component])) {
          rate = target[component] / timeStepSeconds;
          work.equilibriumLimited[cell] = true;
        }
      } else if (transferLaw == TransferLaw.TWO_RESISTANCE_RELAXATION) {
        // Two finite compartments exchanging through two film resistances approach equilibrium at this rate.
        double rateConstant = (filmConductance + dropletConductance) / gasArea
            * (1.0 + cachedEquilibriumRatio[cell][liquid][component] * gasMoles / liquidMoles);
        rate = target[component] * -Math.expm1(-rateConstant * timeStepSeconds) / timeStepSeconds;
        slowest = Double.isNaN(slowest) ? 1.0 / rateConstant : Math.max(slowest, 1.0 / rateConstant);
        distance += Math.abs(target[component]);
        initialRate += rateConstant * Math.abs(target[component]);
      } else {
        // The film flux of the component, raised by the droplet interface in proportion to its conductance, over the
        // distance to equilibrium at the time of the film solve: a rate constant, which is held.
        double filmRate = flux[component] * width
            * (filmConductance > 0.0 ? 1.0 + dropletConductance / filmConductance : 1.0);
        double solvedTarget = cachedTargetKgPerMetre[cell][liquid][component];
        if (filmRate * solvedTarget > 0.0) {
          // Toward local equilibrium: exponential approach, starting at the film flux when the film is fresh.
          double rateConstant = filmRate / solvedTarget;
          rate = target[component] * -Math.expm1(-rateConstant * timeStepSeconds) / timeStepSeconds;
          distance += Math.abs(target[component]);
          initialRate += rateConstant * Math.abs(target[component]);
          if (Math.abs(target[component]) > significantTarget) {
            slowest = Double.isNaN(slowest) ? 1.0 / rateConstant : Math.max(slowest, 1.0 / rateConstant);
          }
        } else {
          // Away from local equilibrium: held until film flux and equilibrium agree.
          rate = 0.0;
          work.counterEquilibrium[cell] |= filmRate != 0.0 && solvedTarget != 0.0;
        }
      }
      rates[component] = rate;
    }
    if (initialRate > 0.0) {
      work.characteristicTimes[liquid][cell] = distance / initialRate;
    }
    if (liquid == OIL) {
      work.netFluxes[cell] = netFlux;
      work.interfaceTemperatures[cell] = cachedInterfaceTemperatureK[cell][OIL];
      work.slowestTimeConstants[cell] = slowest;
      work.entrainedFractions[cell] = dropletArea > 0.0 ? cachedEntrainedFraction[cell][OIL] : 0.0;
      work.dropletAreas[cell] = dropletArea;
    }
  }

  /**
   * Test whether an equilibrium state holds a phase of one kind.
   *
   * @param state flashed state
   * @param kind 0 for gas, 1 for oil, 2 for aqueous
   * @return true when a phase of that kind holds mass
   */
  private static boolean hasPhase(SystemInterface state, int kind) {
    for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
      if (phaseKind(state.getPhase(phase).getType()) == kind && state.getPhase(phase).getNumberOfMolesInPhase() > 0.0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Map a thermodynamic phase type to a transported phase.
   *
   * @param type phase type
   * @return 0 for gas, 1 for oil or another hydrocarbon liquid, 2 for aqueous, -1 otherwise
   */
  private static int phaseKind(PhaseType type) {
    if (type == PhaseType.GAS) {
      return 0;
    }
    if (type == PhaseType.OIL || type == PhaseType.LIQUID || type == PhaseType.LIQUID_ASPHALTENE) {
      return 1;
    }
    return type == PhaseType.AQUEOUS ? 2 : -1;
  }

  /**
   * Solve the film between the gas and one liquid of a cell.
   *
   * @param cell cell index, for the cached coefficients
   * @param liquid {@link #OIL} or {@link #AQUEOUS}
   * @param section hydrodynamic cell
   * @param template thermodynamic template
   * @param names transported component names
   * @param inventory gas and liquid component masses in kg
   * @param liquidHoldup volume fraction of the liquid
   * @param liquidSpeed velocity of the liquid in m/s
   * @param interfaceTemperature one-element output for the solved interface temperature in K
   * @return component mass fluxes in kg/(m2 s) in transport order, positive from gas to liquid
   */
  private double[] solveFilm(int cell, int liquid, TwoFluidSection section, SystemInterface template, String[] names,
      double[][] inventory, double liquidHoldup, double liquidSpeed, double[] interfaceTemperature) {
    SystemInterface bulk = template.clone();
    bulk.setEmptyFluid();
    int count = bulk.getPhase(0).getNumberOfComponents();
    int[] transportIndex = new int[count];
    for (int component = 0; component < count; component++) {
      String name = bulk.getPhase(0).getComponent(component).getComponentName();
      transportIndex[component] = Arrays.asList(names).indexOf(name);
      if (transportIndex[component] < 0) {
        throw new IllegalStateException("Film template component '" + name + "' is not transported");
      }
      double molarMass = bulk.getPhase(0).getComponent(component).getMolarMass();
      bulk.addComponent(component, inventory[0][transportIndex[component]] / molarMass, 0);
      bulk.addComponent(component, inventory[1][transportIndex[component]] / molarMass, 1);
    }
    bulk.setTemperature(section.getTemperature());
    bulk.setPressure(section.getPressure() / 1.0e5);
    bulk.setNumberOfPhases(2);
    bulk.setMaxNumberOfPhases(2);
    bulk.setPhaseType(0, PhaseType.GAS);
    bulk.setPhaseType(1, liquid == OIL ? PhaseType.OIL : PhaseType.AQUEOUS);
    bulk.setForcePhaseTypes(true);
    bulk.init_x_y();
    bulk.initBeta();
    bulk.init(1);
    bulk.initProperties();

    PipeData pipe = new PipeData(section.getDiameter(), section.getRoughness());
    pipe.setNodeLength(1.0);
    FlowRegime regime = section.getFlowRegime();
    FlowNodeInterface node;
    cachedDispersedAreaSquareMetresPerMetre[cell][liquid] = 0.0;
    cachedDispersedLiquid[cell][liquid] = false;
    double pipeArea = Math.PI * section.getDiameter() * section.getDiameter() / 4.0;
    if (regimeSpecificFilm && regime == FlowRegime.MIST) {
      // The liquid travels as droplets in the gas.
      double[] droplet = dropletSizeAndSlip(bulk, section, liquidHoldup, liquidSpeed);
      DropletFlowNode dropletNode = new DropletFlowNode(bulk, pipe);
      dropletNode.setAverageDropletDiameter(droplet[0]);
      dropletNode.setSpecifiedDispersedPhaseRelativeVelocity(droplet[1]);
      node = dropletNode;
      cachedDispersedAreaSquareMetresPerMetre[cell][liquid] = 6.0 * liquidHoldup * pipeArea / droplet[0];
      cachedDispersedLiquid[cell][liquid] = true;
    } else if (regimeSpecificFilm && (regime == FlowRegime.BUBBLE || regime == FlowRegime.DISPERSED_BUBBLE
        || regime == FlowRegime.SINGLE_PHASE_LIQUID)) {
      // The gas travels as bubbles in the liquid.
      double[] bubble = bubbleSizeAndSlip(bulk, section, liquidHoldup, liquidSpeed);
      BubbleFlowNode bubbleNode = new BubbleFlowNode(bulk, pipe);
      bubbleNode.setAverageBubbleDiameter(bubble[0]);
      bubbleNode.setSpecifiedDispersedPhaseRelativeVelocity(bubble[1]);
      node = bubbleNode;
      cachedDispersedAreaSquareMetresPerMetre[cell][liquid] = 6.0 * section.getGasHoldup() * pipeArea / bubble[0];
    } else if (regimeSpecificFilm && regime == FlowRegime.ANNULAR) {
      node = new AnnularFlow(bulk, pipe);
    } else if (regimeSpecificFilm && (regime == FlowRegime.SLUG || regime == FlowRegime.CHURN)) {
      node = new SlugFlowNode(bulk, pipe);
    } else {
      node = new StratifiedFlowNode(bulk, pipe);
    }
    node.setPhaseFraction(0, 1.0 - liquidHoldup);
    node.setPhaseFraction(1, liquidHoldup);
    // The gas-side coefficient follows the gas speed relative to the liquid; the liquid side its own speed.
    node.setVelocity(0, Math.max(Math.abs(section.getGasVelocity() - liquidSpeed), MINIMUM_NODE_SPEED));
    node.setVelocity(1, Math.max(Math.abs(liquidSpeed), MINIMUM_NODE_SPEED));
    node.init();
    FluidBoundaryInterface boundary = node.getFluidBoundary();
    boundary.setMassTransferCalc(true);
    boundary.setHeatTransferCalc(true);
    boundary.useFiniteFluxCorrection(finiteFluxCorrection);
    boundary.useThermodynamicCorrections(thermodynamicCorrection);
    boundary.setCoupledMassHeatTransferEnabled(true);
    boundary.solve();

    double[] flux = new double[names.length];
    double[] overall = new double[names.length];
    double[] ratio = new double[names.length];
    double[] molarMasses = new double[names.length];
    SystemInterface nodeBulk = node.getBulkSystem();
    SystemInterface interfaceState = boundary.getInterphaseSystem();
    double gasMolarDensity = nodeBulk.getPhase(0).getDensity("kg/m3") / nodeBulk.getPhase(0).getMolarMass();
    double liquidMolarDensity = nodeBulk.getPhase(1).getDensity("kg/m3") / nodeBulk.getPhase(1).getMolarMass();
    for (int component = 0; component < count; component++) {
      int index = transportIndex[component];
      molarMasses[index] = nodeBulk.getPhase(0).getComponent(component).getMolarMass();
      double value = boundary.getInterphaseMolarFlux(component) * molarMasses[index];
      double gasCoefficient = boundary.getEffectiveMassTransferCoefficient(0, component);
      double liquidCoefficient = boundary.getEffectiveMassTransferCoefficient(1, component);
      ratio[index] = interfaceState.getPhase(0).getComponent(component).getx()
          / interfaceState.getPhase(1).getComponent(component).getx();
      overall[index] = 1.0
          / (1.0 / gasCoefficient + ratio[index] * gasMolarDensity / (liquidMolarDensity * liquidCoefficient));
      if (!Double.isFinite(value) || !(gasCoefficient > 0.0) || !(liquidCoefficient > 0.0) || !(ratio[index] > 0.0)
          || !Double.isFinite(ratio[index]) || !(overall[index] > 0.0) || !Double.isFinite(overall[index])) {
        throw new IllegalStateException("Film returned a nonfinite flux, coefficient or equilibrium ratio");
      }
      flux[index] = value;
    }
    if (liquid == OIL) {
      // Identical phases on the two sides of the interface: the bulk gas and oil are miscible here.
      double largestLogRatio = 0.0;
      for (int component = 0; component < count; component++) {
        largestLogRatio = Math.max(largestLogRatio, Math.abs(Math.log(ratio[transportIndex[component]])));
      }
      cachedMixingTimeSeconds[cell] = largestLogRatio < MISCIBLE_LOG_EQUILIBRIUM_RATIO ? mixingTime(section) : 0.0;
    }
    cachedOverallCoefficientMetresPerSecond[cell][liquid] = overall;
    cachedEquilibriumRatio[cell][liquid] = ratio;
    cachedMolarMassKgPerMol[cell] = molarMasses;

    // Entrained droplets: fraction and size from the entrainment closure, coefficients for a sphere.
    double[] dropletOverall = new double[names.length];
    cachedEntrainedFraction[cell][liquid] = 0.0;
    cachedDropletDiameterMetres[cell][liquid] = 0.0;
    if (dropletField && !cachedDispersedLiquid[cell][liquid]) {
      double gasDensity = nodeBulk.getPhase(0).getDensity("kg/m3");
      double liquidDensity = nodeBulk.getPhase(1).getDensity("kg/m3");
      double gasViscosity = nodeBulk.getPhase(0).getPhysicalProperties().getViscosity();
      double liquidViscosity = nodeBulk.getPhase(1).getPhysicalProperties().getViscosity();
      double surfaceTension = nodeBulk.getInterphaseProperties().getSurfaceTension(0, 1);
      if (!(surfaceTension > 0.0) || !Double.isFinite(surfaceTension)) {
        surfaceTension = liquid == OIL ? 5.0e-3 : 5.0e-2;
      }
      double gasSpeed = Math.abs(section.getGasVelocity());
      EntrainmentDeposition.EntrainmentResult entrainment = new EntrainmentDeposition().calculate(FlowRegime.ANNULAR,
          gasSpeed * (1.0 - liquidHoldup), Math.max(Math.abs(liquidSpeed) * liquidHoldup, 1.0e-9), gasDensity,
          liquidDensity, gasViscosity, liquidViscosity, surfaceTension, section.getDiameter(), liquidHoldup);
      double entrained = entrainment.entrainmentFraction;
      double dropletDiameter = entrainment.dropletDiameter;
      if (entrained > 0.0 && entrained <= 1.0 && dropletDiameter > 0.0 && Double.isFinite(dropletDiameter)) {
        cachedEntrainedFraction[cell][liquid] = entrained;
        cachedDropletDiameterMetres[cell][liquid] = dropletDiameter;
        // Settling speed of the droplet from the Archimedes number, Re = Ar / (18 + 0.61 sqrt(Ar)).
        double archimedes = 9.81 * Math.pow(dropletDiameter, 3.0) * gasDensity * Math.abs(liquidDensity - gasDensity)
            / (gasViscosity * gasViscosity);
        double dropletReynolds = archimedes / (18.0 + 0.61 * Math.sqrt(archimedes));
        double gasKinematicViscosity = gasViscosity / gasDensity;
        nodeBulk.getPhase(0).getPhysicalProperties().calcEffectiveDiffusionCoefficients();
        nodeBulk.getPhase(1).getPhysicalProperties().calcEffectiveDiffusionCoefficients();
        for (int component = 0; component < count; component++) {
          int index = transportIndex[component];
          double gasDiffusivity = nodeBulk.getPhase(0).getPhysicalProperties()
              .getEffectiveDiffusionCoefficient(component);
          double liquidDiffusivity = nodeBulk.getPhase(1).getPhysicalProperties()
              .getEffectiveDiffusionCoefficient(component);
          if (!(gasDiffusivity > 0.0) || !(liquidDiffusivity > 0.0) || !Double.isFinite(gasDiffusivity)
              || !Double.isFinite(liquidDiffusivity)) {
            continue;
          }
          // Ranz and Marshall outside the droplet, rigid-sphere diffusion limit inside it.
          double gasSherwood = 2.0
              + 0.6 * Math.sqrt(dropletReynolds) * Math.cbrt(gasKinematicViscosity / gasDiffusivity);
          double gasDropletCoefficient = gasSherwood * gasDiffusivity / dropletDiameter;
          double liquidDropletCoefficient = 6.58 * liquidDiffusivity / dropletDiameter;
          dropletOverall[index] = 1.0 / (1.0 / gasDropletCoefficient
              + ratio[index] * gasMolarDensity / (liquidMolarDensity * liquidDropletCoefficient));
        }
      }
    }
    cachedDropletOverallCoefficientMetresPerSecond[cell][liquid] = dropletOverall;
    interfaceTemperature[0] = boundary.getInterphaseSystem().getTemperature();
    if (!Double.isFinite(interfaceTemperature[0])) {
      throw new IllegalStateException("Film returned a nonfinite interface temperature");
    }
    return flux;
  }

  /**
   * Test whether the gas and the oil of a cell are miscible at first contact.
   *
   * @param template thermodynamic template
   * @param names transported component names
   * @param inventory gas and oil component masses in kg
   * @param section hydrodynamic cell, for pressure and temperature
   * @return true when no mixture of the two phases, from one tenth to nine tenths oil by moles, splits into two
   * hydrocarbon phases whose densities differ by more than a quarter
   */
  private boolean isMiscible(SystemInterface template, String[] names, double[][] inventory, TwoFluidSection section) {
    try {
      SystemInterface mixture = template.clone();
      int count = mixture.getPhase(0).getNumberOfComponents();
      double[] gasMoles = new double[count];
      double[] oilMoles = new double[count];
      double gasTotal = 0.0;
      double oilTotal = 0.0;
      for (int component = 0; component < count; component++) {
        int index = Arrays.asList(names).indexOf(mixture.getPhase(0).getComponent(component).getComponentName());
        if (index < 0) {
          return false;
        }
        double molarMass = mixture.getPhase(0).getComponent(component).getMolarMass();
        gasMoles[component] = Math.max(0.0, inventory[0][index]) / molarMass;
        oilMoles[component] = Math.max(0.0, inventory[1][index]) / molarMass;
        gasTotal += gasMoles[component];
        oilTotal += oilMoles[component];
      }
      if (!(gasTotal > 0.0) || !(oilTotal > 0.0)) {
        return false;
      }
      for (double oilShare : MISCIBILITY_OIL_SHARES) {
        SystemInterface trial = template.clone();
        trial.setEmptyFluid();
        for (int component = 0; component < count; component++) {
          trial.addComponent(component,
              (1.0 - oilShare) * gasMoles[component] / gasTotal + oilShare * oilMoles[component] / oilTotal);
        }
        trial.setTemperature(section.getTemperature());
        trial.setPressure(section.getPressure() / 1.0e5);
        new ThermodynamicOperations(trial).TPflash();
        int hydrocarbonPhases = 0;
        double lightest = Double.POSITIVE_INFINITY;
        double densest = 0.0;
        for (int phase = 0; phase < trial.getNumberOfPhases(); phase++) {
          int kind = phaseKind(trial.getPhase(phase).getType());
          if (kind == 0 || kind == 1) {
            hydrocarbonPhases++;
            lightest = Math.min(lightest, trial.getPhase(phase).getDensity());
            densest = Math.max(densest, trial.getPhase(phase).getDensity());
          }
        }
        // Two hydrocarbon phases of nearly the same density are close to their critical point: the interface between
        // them has almost no tension and does not hold against the turbulence of the flow.
        if (hydrocarbonPhases > 2 || hydrocarbonPhases == 2 && densest >= NEAR_CRITICAL_DENSITY_RATIO * lightest) {
          return false;
        }
      }
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  /**
   * Radial turbulent mixing time of a cell, {@code R^2 / (0.07 u* R)} with the friction velocity of the gas on a smooth
   * wall (Blasius), not shorter than the fallback relaxation time.
   *
   * @param section hydrodynamic cell
   * @return mixing time in s
   */
  private double mixingTime(TwoFluidSection section) {
    double speed = Math.abs(section.getGasVelocity());
    double reynolds = section.getGasDensity() * speed * section.getDiameter() / section.getGasViscosity();
    double frictionVelocity = reynolds > 0.0 && Double.isFinite(reynolds)
        ? speed * Math.sqrt(0.316 * Math.pow(reynolds, -0.25) / 8.0)
        : 0.0;
    double time = frictionVelocity > 0.0 ? 0.5 * section.getDiameter() / (EDDY_DIFFUSIVITY_FACTOR * frictionVelocity)
        : Double.MAX_VALUE;
    return Math.max(fallbackRelaxationTimeSeconds, Double.isFinite(time) ? time : Double.MAX_VALUE);
  }

  /**
   * Flash one phase inventory of a cell on its own and return what it sheds to the other phases.
   *
   * <p>
   * Of the hydrocarbon phases the flash forms, the lighter counts as gas and the denser as oil whatever their labels; a
   * single hydrocarbon phase stays with a gas or oil inventory. An aqueous phase is identified by its type.
   * </p>
   *
   * @param template thermodynamic template
   * @param names transported component names
   * @param phaseInventory component masses of the phase in kg
   * @param section hydrodynamic cell, for pressure and temperature
   * @param from phase that is flashed: 0 gas, 1 oil, 2 aqueous
   * @return shed component masses in kg by receiving phase (0 gas, 1 oil, 2 aqueous) in transport order, or null when
   * the phase is stable on its own
   */
  private double[][] bulkSplit(SystemInterface template, String[] names, double[] phaseInventory,
      TwoFluidSection section, int from) {
    try {
      SystemInterface bulk = template.clone();
      bulk.setEmptyFluid();
      int count = bulk.getPhase(0).getNumberOfComponents();
      int[] transportIndex = new int[count];
      for (int component = 0; component < count; component++) {
        String name = bulk.getPhase(0).getComponent(component).getComponentName();
        transportIndex[component] = Arrays.asList(names).indexOf(name);
        if (transportIndex[component] < 0) {
          return null;
        }
        bulk.addComponent(component,
            phaseInventory[transportIndex[component]] / bulk.getPhase(0).getComponent(component).getMolarMass());
      }
      bulk.setTemperature(section.getTemperature());
      bulk.setPressure(section.getPressure() / 1.0e5);
      new ThermodynamicOperations(bulk).TPflash();
      int phases = bulk.getNumberOfPhases();
      if (phases < 2) {
        return null;
      }
      // Receiving phase of every product phase.
      int[] receiver = new int[phases];
      int lighter = -1;
      int denser = -1;
      for (int phase = 0; phase < phases; phase++) {
        int kind = phaseKind(bulk.getPhase(phase).getType());
        if (kind == 2) {
          receiver[phase] = 2;
        } else if (lighter < 0) {
          lighter = phase;
        } else if (denser < 0) {
          denser = phase;
          if (bulk.getPhase(lighter).getDensity() > bulk.getPhase(denser).getDensity()) {
            denser = lighter;
            lighter = phase;
          }
        } else {
          return null;
        }
      }
      if (lighter >= 0 && denser >= 0) {
        receiver[lighter] = 0;
        receiver[denser] = 1;
      } else if (lighter >= 0) {
        receiver[lighter] = from < 2 ? from : phaseKind(bulk.getPhase(lighter).getType());
      }
      double[][] shed = new double[3][names.length];
      boolean any = false;
      for (int phase = 0; phase < phases; phase++) {
        if (receiver[phase] == from || receiver[phase] < 0) {
          continue;
        }
        for (int component = 0; component < count; component++) {
          double mass = bulk.getPhase(phase).getComponent(component).getNumberOfMolesInPhase()
              * bulk.getPhase(phase).getComponent(component).getMolarMass();
          if (!Double.isFinite(mass) || mass < 0.0) {
            return null;
          }
          shed[receiver[phase]][transportIndex[component]] += mass;
          any |= mass > 0.0;
        }
      }
      if (!any) {
        return null;
      }
      // A phase cannot shed more of a component than it holds.
      for (int component = 0; component < names.length; component++) {
        double total = shed[0][component] + shed[1][component] + shed[2][component];
        if (total > phaseInventory[component] && total > 0.0) {
          double scale = phaseInventory[component] / total;
          for (int to = 0; to < 3; to++) {
            shed[to][component] *= scale;
          }
        }
      }
      return shed;
    } catch (RuntimeException exception) {
      bulkSplitFailureCount++;
      return null;
    }
  }

  /**
   * Sauter diameter and slip speed of the droplets of a mist cell.
   *
   * <p>
   * The diameter comes from {@link EntrainmentDeposition} (critical Weber number), bounded to 10 micrometres and a
   * tenth of the bore; the slip is the settling speed from the Archimedes number,
   * {@code Re = Ar / (18 + 0.61 sqrt(Ar))}.
   * </p>
   *
   * @param bulk gas and liquid of the cell with physical properties
   * @param section hydrodynamic cell
   * @param liquidHoldup volume fraction of the liquid
   * @param liquidSpeed velocity of the liquid in m/s
   * @return droplet diameter in m and slip speed in m/s
   */
  private double[] dropletSizeAndSlip(SystemInterface bulk, TwoFluidSection section, double liquidHoldup,
      double liquidSpeed) {
    double gasDensity = bulk.getPhase(0).getDensity("kg/m3");
    double liquidDensity = bulk.getPhase(1).getDensity("kg/m3");
    double gasViscosity = bulk.getPhase(0).getPhysicalProperties().getViscosity();
    double liquidViscosity = bulk.getPhase(1).getPhysicalProperties().getViscosity();
    double surfaceTension = surfaceTension(bulk);
    double gasSpeed = Math.abs(section.getGasVelocity());
    double diameter = new EntrainmentDeposition().calculate(FlowRegime.ANNULAR, gasSpeed * (1.0 - liquidHoldup),
        Math.max(Math.abs(liquidSpeed) * liquidHoldup, 1.0e-9), gasDensity, liquidDensity, gasViscosity,
        liquidViscosity, surfaceTension, section.getDiameter(), liquidHoldup).dropletDiameter;
    if (!(diameter > 0.0) || !Double.isFinite(diameter)) {
      diameter = 1.0e-4;
    }
    diameter = Math.min(Math.max(diameter, 1.0e-5), 0.1 * section.getDiameter());
    double archimedes = 9.81 * Math.pow(diameter, 3.0) * gasDensity * Math.abs(liquidDensity - gasDensity)
        / (gasViscosity * gasViscosity);
    double slip = archimedes / (18.0 + 0.61 * Math.sqrt(archimedes)) * gasViscosity / (gasDensity * diameter);
    return new double[] {diameter, Math.max(slip, MINIMUM_NODE_SPEED)};
  }

  /**
   * Sauter diameter and rise speed of the bubbles of a bubble-flow cell.
   *
   * <p>
   * The largest stable bubble in the turbulent liquid follows Hinze, {@code d_max = 0.725 (sigma / rho_L)^0.6
   * eps^-0.4}, with the dissipation {@code eps = f u_m^3 / (2 D)} of the mixture on a smooth wall; the Sauter diameter
   * is 0.62 of it, bounded by four capillary lengths and 0.1 mm. The rise speed is that of a distorted bubble
   * (Harmathy), {@code 1.53 (g sigma (rho_L - rho_G) / rho_L^2)^0.25}.
   * </p>
   *
   * @param bulk gas and liquid of the cell with physical properties
   * @param section hydrodynamic cell
   * @param liquidHoldup volume fraction of the liquid
   * @param liquidSpeed velocity of the liquid in m/s
   * @return bubble diameter in m and rise speed in m/s
   */
  private double[] bubbleSizeAndSlip(SystemInterface bulk, TwoFluidSection section, double liquidHoldup,
      double liquidSpeed) {
    double gasDensity = bulk.getPhase(0).getDensity("kg/m3");
    double liquidDensity = bulk.getPhase(1).getDensity("kg/m3");
    double liquidViscosity = bulk.getPhase(1).getPhysicalProperties().getViscosity();
    double surfaceTension = surfaceTension(bulk);
    double densityDifference = Math.max(Math.abs(liquidDensity - gasDensity), 1.0);
    double capillaryLength = Math.sqrt(surfaceTension / (9.81 * densityDifference));
    double mixtureSpeed = Math.abs(section.getGasHoldup() * section.getGasVelocity() + liquidHoldup * liquidSpeed);
    double reynolds = liquidDensity * mixtureSpeed * section.getDiameter() / liquidViscosity;
    double diameter = 4.0 * capillaryLength;
    if (reynolds > 0.0 && Double.isFinite(reynolds)) {
      double dissipation = 0.316 * Math.pow(reynolds, -0.25) * Math.pow(mixtureSpeed, 3.0)
          / (2.0 * section.getDiameter());
      if (dissipation > 0.0) {
        diameter = Math.min(diameter,
            0.62 * 0.725 * Math.pow(surfaceTension / liquidDensity, 0.6) * Math.pow(dissipation, -0.4));
      }
    }
    diameter = Math.max(diameter, 1.0e-4);
    double slip = 1.53 * Math.pow(9.81 * surfaceTension * densityDifference / (liquidDensity * liquidDensity), 0.25);
    return new double[] {diameter, Math.max(slip, MINIMUM_NODE_SPEED)};
  }

  /**
   * Interfacial tension between the two phases of a film bulk.
   *
   * @param bulk gas and liquid with physical properties
   * @return interfacial tension in N/m; 5 mN/m against a hydrocarbon liquid and 50 mN/m against an aqueous liquid where
   * the property model returns none
   */
  private static double surfaceTension(SystemInterface bulk) {
    double value = Double.NaN;
    try {
      value = bulk.getInterphaseProperties().getSurfaceTension(0, 1);
    } catch (RuntimeException exception) {
      value = Double.NaN;
    }
    if (!(value > 0.0) || !Double.isFinite(value)) {
      value = bulk.getPhase(1).getType() == PhaseType.AQUEOUS ? 5.0e-2 : 5.0e-3;
    }
    return value;
  }

  /**
   * Interface width of a cell.
   *
   * @param section hydrodynamic cell
   * @param condensing true when the net film transfer is from gas to oil
   * @return interface width in m of interface per m of pipe
   */
  private double interfaceWidth(TwoFluidSection section, boolean condensing) {
    FlowRegime regime = section.getFlowRegime();
    double diameter = section.getDiameter();
    if (condensing && wallCondensation || regime == FlowRegime.ANNULAR || regime == FlowRegime.MIST) {
      return Math.PI * diameter * Math.sqrt(Math.max(0.0, section.getGasHoldup()));
    }
    return new GeometryCalculator().calculateFromHoldup(section.getLiquidHoldup(), diameter).interfacialWidth;
  }

  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }
}
