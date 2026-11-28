package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.Arrays;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.AnnularFlow;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.SlugFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Non-equilibrium gas/oil mass transfer from the multicomponent film model of the fluid-mechanics package.
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
 * inventory at local equilibrium approaches that inventory exponentially, with the film flux as initial rate:
 * {@code dm = dm_eq (1 - exp(-N w dt / dm_eq))}. For a short step this is the film flux, with its cross-diffusion and
 * finite-flux effects; for a long step it is local equilibrium, so it is stable on pipeline time steps. A component
 * whose film flux points away from its equilibrium inventory is held until the two agree: carrying it would move the
 * cell away from equilibrium for as long as the film solution is held, and near the dew point that fills the pipe with
 * liquid.</li>
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
 * <li>Dry gas/oil cells of hydrocarbons and inert gases. Aqueous inventory is rejected.</li>
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
 * at the cell pressure and temperature, and so is the oil inventory. Liquid that the gas forms by itself is fog: it
 * nucleates in the bulk and needs no interface. Vapour that the oil forms by itself is flashing. Both are moved with
 * the bulk split time (default 30 s, the default equilibrium relaxation time of the pipe). The film then acts between
 * two phases that are each stable, and carries only what is left of the distance to local equilibrium. Without this
 * split, condensation during a pressure fall would be limited by diffusion of the heavy components through the gas to
 * the wall, which is not how a retrograde gas condenses.</li>
 * <li>Entrained droplets can be added as a second interface (off by default): entrained fraction and Sauter diameter
 * from {@link EntrainmentDeposition}, Ranz-Marshall outside the droplet and the rigid-sphere limit inside it. Droplets
 * are not transported as a separate field; the entrained fraction is the local equilibrium value. The entrainment
 * correlation puts almost all liquid in droplets in large high-pressure lines, so treat the result with droplets as the
 * upper bound of the interfacial area and the result without them as the lower bound.</li>
 * <li>Stratified, bubble and dispersed cells use the stratified-flow film node, annular and mist cells the annular-flow
 * node and slug cells the slug-flow node (switchable). The gas side follows the Chilton-Colburn analogy on the
 * interfacial friction factor and includes natural convection where the gas is unstably stratified.</li>
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

  private double[][] cachedMassFluxKgPerSquareMetreSecond;
  private double[] cachedTimeSeconds;
  private double[] cachedInterfaceTemperatureK;
  private double[][] cachedOverallCoefficientMetresPerSecond;
  private double[][] cachedEquilibriumRatio;
  private double[][] cachedInterfaceGasFraction;
  private double[][] cachedInterfaceOilFraction;
  private double[][] cachedMolarMassKgPerMol;
  private double[][] cachedDropletOverallCoefficientMetresPerSecond;
  private double[] cachedEntrainedFraction;
  private double[] cachedDropletDiameterMetres;
  private boolean[] cachedFilm;
  private double[] cachedMixingTimeSeconds;
  private double[] cachedRetryTimeSeconds;
  private long filmSolveCount;
  private long filmFailureCount;
  private long bulkSplitFailureCount;
  private String lastFailure = "";

  /** Sources and diagnostics of one evaluation. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double[][][] sources;
    private final CellMode[] modes;
    private final double[] interfaceWidths;
    private final double[] netMassFluxes;
    private final double[] interfaceTemperatures;
    private final double[] slowestTimeConstants;
    private final double[] characteristicTimes;
    private final double[] entrainedFractions;
    private final double[] dropletAreas;
    private final boolean[] donorLimited;
    private final boolean[] equilibriumLimited;
    private final boolean[] counterEquilibrium;
    private final double[] fogRates;
    private final double[] flashingRates;
    private final double[] mixingTimes;
    private final long filmSolveCount;
    private final long filmFailureCount;
    private final String lastFailure;

    private Result(double[][][] sources, CellMode[] modes, double[] interfaceWidths, double[] netMassFluxes,
        double[] interfaceTemperatures, double[] slowestTimeConstants, double[] characteristicTimes,
        double[] entrainedFractions, double[] dropletAreas, boolean[] donorLimited, boolean[] equilibriumLimited,
        boolean[] counterEquilibrium, double[] fogRates, double[] flashingRates, double[] mixingTimes,
        long filmSolveCount, long filmFailureCount, String lastFailure) {
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
      this.mixingTimes = mixingTimes;
      this.filmSolveCount = filmSolveCount;
      this.filmFailureCount = filmFailureCount;
      this.lastFailure = lastFailure;
    }

    /** @return cell/phase/component sources in kg/(m s); gas is the exact negative of oil */
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

    /** @return how each cell source was obtained */
    public CellMode[] getModes() {
      return modes.clone();
    }

    /** @return interface width used in each film cell, in m of interface per m of pipe */
    public double[] getInterfaceWidthsMetres() {
      return interfaceWidths.clone();
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
      return characteristicTimes.clone();
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
     * Liquid formed in the bulk of the gas, without an interface.
     *
     * @return gas-to-oil rate per cell in kg/(m s) from the bulk split of the gas inventory
     */
    public double[] getFogRatesKgPerMetreSecond() {
      return fogRates.clone();
    }

    /**
     * Vapour formed in the bulk of the oil, without an interface.
     *
     * @return oil-to-gas rate per cell in kg/(m s) from the bulk split of the oil inventory
     */
    public double[] getFlashingRatesKgPerMetreSecond() {
      return flashingRates.clone();
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
   * Choose the film node of annular, mist and slug cells.
   *
   * @param enabled true to solve annular and mist cells with the annular-flow node (Wallis interfacial friction) and
   * slug cells with the slug-flow node; false to use the stratified-flow node in every regime
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
   * @throws IllegalStateException if a cell holds aqueous inventory or equilibrium
   */
  public Result calculate(TwoFluidComponentTransport transport, TwoFluidSection[] sections, SystemInterface template,
      SystemInterface[] equilibriumStates, double timeSeconds, double timeStepSeconds) {
    if (transport == null || sections == null || template == null || equilibriumStates == null
        || !Double.isFinite(timeSeconds) || !Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0) {
      throw new IllegalArgumentException("Film transfer requires inventories, cells, flashes and finite positive time");
    }
    double[][][] targetChanges = transport.createHydrocarbonEquilibriumInventoryChanges(equilibriumStates, sections);
    if (targetChanges == null) {
      throw new IllegalStateException("Film interphase transfer requires dry gas/oil inventories and equilibrium");
    }
    String[] names = transport.getComponentNames();
    double[][][] inventory = transport.getInventoryKg();
    int cells = sections.length;
    if (cachedTimeSeconds == null || cachedTimeSeconds.length != cells) {
      cachedMassFluxKgPerSquareMetreSecond = new double[cells][];
      cachedTimeSeconds = new double[cells];
      cachedInterfaceTemperatureK = new double[cells];
      cachedOverallCoefficientMetresPerSecond = new double[cells][];
      cachedEquilibriumRatio = new double[cells][];
      cachedInterfaceGasFraction = new double[cells][];
      cachedInterfaceOilFraction = new double[cells][];
      cachedMolarMassKgPerMol = new double[cells][];
      cachedDropletOverallCoefficientMetresPerSecond = new double[cells][];
      cachedEntrainedFraction = new double[cells];
      cachedDropletDiameterMetres = new double[cells];
      cachedFilm = new boolean[cells];
      cachedMixingTimeSeconds = new double[cells];
      cachedRetryTimeSeconds = new double[cells];
      Arrays.fill(cachedTimeSeconds, Double.NEGATIVE_INFINITY);
      Arrays.fill(cachedRetryTimeSeconds, Double.NEGATIVE_INFINITY);
    }
    double[][][] sources = new double[cells][3][names.length];
    CellMode[] modes = new CellMode[cells];
    double[] widths = new double[cells];
    double[] netFluxes = new double[cells];
    double[] interfaceTemperatures = new double[cells];
    Arrays.fill(interfaceTemperatures, Double.NaN);
    double[] slowestTimeConstants = new double[cells];
    Arrays.fill(slowestTimeConstants, Double.NaN);
    double[] characteristicTimes = new double[cells];
    Arrays.fill(characteristicTimes, Double.NaN);
    double[] entrainedFractions = new double[cells];
    double[] dropletAreas = new double[cells];
    boolean[] donorLimited = new boolean[cells];
    boolean[] equilibriumLimited = new boolean[cells];
    boolean[] counterEquilibrium = new boolean[cells];
    double[] fogRates = new double[cells];
    double[] flashingRates = new double[cells];
    double[] mixingTimes = new double[cells];
    Arrays.fill(mixingTimes, Double.NaN);

    for (int cell = 0; cell < cells; cell++) {
      TwoFluidSection section = sections[cell];
      double length = section.getLength();
      double gasMass = sum(inventory[cell][0]);
      double oilMass = sum(inventory[cell][1]);
      // Where the conserved cell composition is single phase at equilibrium there is no interface for the mixture
      // as a whole and the gas or oil label of the flash is arbitrary. The phase holding less mass must then
      // disappear into the other: that is the target, and no component may be carried into the minority phase.
      boolean singlePhaseEquilibrium = equilibriumStates[cell].getNumberOfPhases() == 1;
      if (singlePhaseEquilibrium) {
        boolean gasMajority = gasMass >= oilMass;
        for (int component = 0; component < names.length; component++) {
          targetChanges[cell][1][component] = gasMajority ? -inventory[cell][1][component] / length
              : inventory[cell][0][component] / length;
        }
      }
      boolean filmPossible = gasMass > 0.0 && oilMass > 0.0 && section.getGasHoldup() >= minimumHoldup
          && section.getLiquidHoldup() >= minimumHoldup;
      // Bulk split: what each phase sheds when flashed on its own, in kg per component, gas to oil positive.
      double[] split = new double[names.length];
      if (filmPossible && bulkPhaseSplit) {
        double[] fog = singlePhaseEquilibrium && gasMass >= oilMass ? null
            : bulkSplit(template, names, inventory[cell][0], section, true);
        double[] vapour = singlePhaseEquilibrium && gasMass < oilMass ? null
            : bulkSplit(template, names, inventory[cell][1], section, false);
        for (int component = 0; component < names.length; component++) {
          double toOil = fog == null ? 0.0 : fog[component];
          double toGas = vapour == null ? 0.0 : vapour[component];
          split[component] = toOil - toGas;
          fogRates[cell] += toOil;
          flashingRates[cell] += toGas;
        }
      }
      // Fraction of the bulk split that moves within this step, per second.
      double splitFractionRate = -Math.expm1(-timeStepSeconds / bulkPhaseSplitTimeSeconds) / timeStepSeconds;
      fogRates[cell] *= splitFractionRate / length;
      flashingRates[cell] *= splitFractionRate / length;
      if (filmPossible) {
        // A time before the cached one means a rejected step was rolled back: solve again.
        boolean rolledBack = timeSeconds < cachedTimeSeconds[cell];
        boolean stale = rolledBack || cachedFilm[cell] && timeSeconds - cachedTimeSeconds[cell] >= updateIntervalSeconds
            || !cachedFilm[cell] && timeSeconds >= cachedRetryTimeSeconds[cell];
        if (stale) {
          try {
            filmSolveCount++;
            double[] interfaceTemperature = new double[1];
            // The film acts between the phases as they are once fog and flashing vapour have moved.
            double[][] stable = new double[2][names.length];
            for (int component = 0; component < names.length; component++) {
              stable[0][component] = inventory[cell][0][component] - split[component];
              stable[1][component] = inventory[cell][1][component] + split[component];
            }
            if (isMiscible(template, names, stable, section)) {
              // No mixture of the two phases splits: there is no interface to solve.
              cachedMixingTimeSeconds[cell] = mixingTime(section);
              interfaceTemperature[0] = section.getTemperature();
            } else {
              cachedMassFluxKgPerSquareMetreSecond[cell] = solveFilm(cell, section, template, names, stable,
                  interfaceTemperature);
            }
            cachedInterfaceTemperatureK[cell] = interfaceTemperature[0];
            cachedFilm[cell] = true;
            cachedTimeSeconds[cell] = timeSeconds;
          } catch (RuntimeException exception) {
            filmFailureCount++;
            lastFailure = "cell " + cell + " at " + timeSeconds + " s: " + exception.getMessage();
            cachedFilm[cell] = false;
            cachedMixingTimeSeconds[cell] = 0.0;
            cachedTimeSeconds[cell] = timeSeconds;
            cachedRetryTimeSeconds[cell] = timeSeconds
                + FAILURE_RETRY_FACTOR * Math.max(updateIntervalSeconds, timeStepSeconds);
            filmPossible = false;
          }
        }
        filmPossible = filmPossible && cachedFilm[cell];
      } else {
        cachedFilm[cell] = false;
        cachedMixingTimeSeconds[cell] = 0.0;
        cachedRetryTimeSeconds[cell] = Double.NEGATIVE_INFINITY;
      }

      double[] oilRates = new double[names.length];
      if (filmPossible && cachedMixingTimeSeconds[cell] > 0.0) {
        // No interface: the phase the cell should lose mixes into the other.
        modes[cell] = CellMode.MISCIBLE;
        mixingTimes[cell] = Double.isNaN(miscibleMixingTimeSeconds) ? cachedMixingTimeSeconds[cell]
            : miscibleMixingTimeSeconds;
        interfaceTemperatures[cell] = cachedInterfaceTemperatureK[cell];
        fogRates[cell] = 0.0;
        flashingRates[cell] = 0.0;
        for (int component = 0; component < names.length; component++) {
          oilRates[component] = targetChanges[cell][1][component] * -Math.expm1(-timeStepSeconds / mixingTimes[cell])
              / timeStepSeconds;
        }
      } else if (filmPossible) {
        modes[cell] = CellMode.FILM;
        double[] flux = cachedMassFluxKgPerSquareMetreSecond[cell];
        netFluxes[cell] = sum(flux);
        // What is left for the interface once fog and flashing vapour have moved.
        double[] residualTargets = new double[names.length];
        for (int component = 0; component < names.length; component++) {
          residualTargets[component] = targetChanges[cell][1][component] - split[component] / length;
        }
        double netTarget = sum(residualTargets);
        boolean condensing = transferLaw == TransferLaw.FILM_FLUX ? netFluxes[cell] > 0.0 : netTarget > 0.0;
        widths[cell] = interfaceAreaFactor * interfaceWidth(section, condensing);
        interfaceTemperatures[cell] = cachedInterfaceTemperatureK[cell];
        double gasMoles = 0.0;
        double oilMoles = 0.0;
        for (int component = 0; component < names.length; component++) {
          gasMoles += inventory[cell][0][component] / cachedMolarMassKgPerMol[cell][component];
          oilMoles += inventory[cell][1][component] / cachedMolarMassKgPerMol[cell][component];
        }
        // Only the major movers set the reported time constant: components whose equilibrium change is at least a
        // tenth of the largest one.
        double significantTarget = 0.0;
        for (double target : residualTargets) {
          significantTarget = Math.max(significantTarget, 0.1 * Math.abs(target));
        }
        double pipeArea = Math.PI * section.getDiameter() * section.getDiameter() / 4.0;
        double gasArea = section.getGasHoldup() * pipeArea;
        if (dropletField && cachedEntrainedFraction[cell] > 0.0 && cachedDropletDiameterMetres[cell] > 0.0) {
          entrainedFractions[cell] = cachedEntrainedFraction[cell];
          dropletAreas[cell] = 6.0 * cachedEntrainedFraction[cell] * section.getLiquidHoldup() * pipeArea
              / cachedDropletDiameterMetres[cell];
        }
        double distance = 0.0;
        double initialRate = 0.0;
        for (int component = 0; component < names.length; component++) {
          double target = residualTargets[component];
          double filmConductance = cachedOverallCoefficientMetresPerSecond[cell][component] * widths[cell];
          double dropletConductance = cachedDropletOverallCoefficientMetresPerSecond[cell][component]
              * dropletAreas[cell];
          double rate;
          if (transferLaw == TransferLaw.FILM_FLUX) {
            rate = flux[component] * widths[cell];
            if (singlePhaseEquilibrium && rate * target <= 0.0) {
              rate = 0.0;
            }
            // A film flux must not carry a component past its local equilibrium inventory within one step.
            if (rate * target > 0.0 && Math.abs(rate) * timeStepSeconds > Math.abs(target)) {
              rate = target / timeStepSeconds;
              equilibriumLimited[cell] = true;
            }
          } else if (transferLaw == TransferLaw.TWO_RESISTANCE_RELAXATION) {
            // Two finite compartments exchanging through two film resistances approach equilibrium at this rate.
            double rateConstant = (filmConductance + dropletConductance) / gasArea
                * (1.0 + cachedEquilibriumRatio[cell][component] * gasMoles / oilMoles);
            rate = target * -Math.expm1(-rateConstant * timeStepSeconds) / timeStepSeconds;
            slowestTimeConstants[cell] = Double.isNaN(slowestTimeConstants[cell]) ? 1.0 / rateConstant
                : Math.max(slowestTimeConstants[cell], 1.0 / rateConstant);
            distance += Math.abs(target);
            initialRate += rateConstant * Math.abs(target);
          } else {
            // The film flux of the component, raised by the droplet interface in proportion to its conductance.
            double filmRate = flux[component] * widths[cell]
                * (filmConductance > 0.0 ? 1.0 + dropletConductance / filmConductance : 1.0);
            if (filmRate * target > 0.0) {
              // Same direction as local equilibrium: exponential approach with the film flux as initial rate.
              double rateConstant = filmRate / target;
              rate = target * -Math.expm1(-rateConstant * timeStepSeconds) / timeStepSeconds;
              distance += Math.abs(target);
              initialRate += Math.abs(filmRate);
              if (Math.abs(target) > significantTarget) {
                slowestTimeConstants[cell] = Double.isNaN(slowestTimeConstants[cell]) ? 1.0 / rateConstant
                    : Math.max(slowestTimeConstants[cell], 1.0 / rateConstant);
              }
            } else {
              // Away from local equilibrium: held until film flux and equilibrium agree.
              rate = 0.0;
              counterEquilibrium[cell] |= filmRate != 0.0 && target != 0.0;
            }
          }
          oilRates[component] = rate + split[component] / length * splitFractionRate;
        }
        if (initialRate > 0.0) {
          characteristicTimes[cell] = distance / initialRate;
        }
      } else {
        boolean any = false;
        for (int component = 0; component < names.length; component++) {
          oilRates[component] = targetChanges[cell][1][component]
              * -Math.expm1(-timeStepSeconds / fallbackRelaxationTimeSeconds) / timeStepSeconds;
          any |= oilRates[component] != 0.0;
        }
        modes[cell] = any ? CellMode.RELAXATION : CellMode.NONE;
        fogRates[cell] = 0.0;
        flashingRates[cell] = 0.0;
      }
      for (int component = 0; component < names.length; component++) {
        double rate = oilRates[component];
        int donor = rate > 0.0 ? 0 : 1;
        double donorLimit = MAX_SOURCE_FRACTION * inventory[cell][donor][component] / length / timeStepSeconds;
        double limited = Math.copySign(Math.min(Math.abs(rate), donorLimit), rate);
        donorLimited[cell] |= Math.abs(limited) < Math.abs(rate);
        sources[cell][1][component] = limited;
        sources[cell][0][component] = -limited;
      }
    }
    return new Result(sources, modes, widths, netFluxes, interfaceTemperatures, slowestTimeConstants,
        characteristicTimes, entrainedFractions, dropletAreas, donorLimited, equilibriumLimited, counterEquilibrium,
        fogRates, flashingRates, mixingTimes, filmSolveCount, filmFailureCount, lastFailure);
  }

  /**
   * Solve the film of one cell.
   *
   * @param cell cell index, for the cached coefficients
   * @param section hydrodynamic cell
   * @param template thermodynamic template
   * @param names transported component names
   * @param inventory gas, oil and aqueous component masses of the cell in kg
   * @param interfaceTemperature one-element output for the solved interface temperature in K
   * @return component mass fluxes in kg/(m2 s) in transport order, positive from gas to oil
   */
  private double[] solveFilm(int cell, TwoFluidSection section, SystemInterface template, String[] names,
      double[][] inventory, double[] interfaceTemperature) {
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
    bulk.setPhaseType(1, PhaseType.OIL);
    bulk.setForcePhaseTypes(true);
    bulk.init_x_y();
    bulk.initBeta();
    bulk.init(1);
    bulk.initProperties();

    PipeData pipe = new PipeData(section.getDiameter(), section.getRoughness());
    pipe.setNodeLength(1.0);
    FlowRegime regime = section.getFlowRegime();
    FlowNodeInterface node;
    if (regimeSpecificFilm && (regime == FlowRegime.ANNULAR || regime == FlowRegime.MIST)) {
      node = new AnnularFlow(bulk, pipe);
    } else if (regimeSpecificFilm && regime == FlowRegime.SLUG) {
      node = new SlugFlowNode(bulk, pipe);
    } else {
      node = new StratifiedFlowNode(bulk, pipe);
    }
    double liquidHoldup = section.getLiquidHoldup();
    node.setPhaseFraction(0, 1.0 - liquidHoldup);
    node.setPhaseFraction(1, liquidHoldup);
    // The gas-side coefficient follows the gas speed relative to the liquid; the liquid side its own speed.
    node.setVelocity(0, Math.max(Math.abs(section.getGasVelocity() - section.getLiquidVelocity()), MINIMUM_NODE_SPEED));
    node.setVelocity(1, Math.max(Math.abs(section.getLiquidVelocity()), MINIMUM_NODE_SPEED));
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
    double[] interfaceGasFraction = new double[names.length];
    double[] interfaceOilFraction = new double[names.length];
    double[] molarMasses = new double[names.length];
    SystemInterface nodeBulk = node.getBulkSystem();
    SystemInterface interfaceState = boundary.getInterphaseSystem();
    double gasMolarDensity = nodeBulk.getPhase(0).getDensity("kg/m3") / nodeBulk.getPhase(0).getMolarMass();
    double oilMolarDensity = nodeBulk.getPhase(1).getDensity("kg/m3") / nodeBulk.getPhase(1).getMolarMass();
    for (int component = 0; component < count; component++) {
      int index = transportIndex[component];
      molarMasses[index] = nodeBulk.getPhase(0).getComponent(component).getMolarMass();
      double value = boundary.getInterphaseMolarFlux(component) * molarMasses[index];
      double gasCoefficient = boundary.getEffectiveMassTransferCoefficient(0, component);
      double oilCoefficient = boundary.getEffectiveMassTransferCoefficient(1, component);
      double interfaceGas = interfaceState.getPhase(0).getComponent(component).getx();
      double interfaceOil = interfaceState.getPhase(1).getComponent(component).getx();
      ratio[index] = interfaceGas / interfaceOil;
      interfaceGasFraction[index] = interfaceGas;
      interfaceOilFraction[index] = interfaceOil;
      overall[index] = 1.0
          / (1.0 / gasCoefficient + ratio[index] * gasMolarDensity / (oilMolarDensity * oilCoefficient));
      if (!Double.isFinite(value) || !(gasCoefficient > 0.0) || !(oilCoefficient > 0.0) || !(ratio[index] > 0.0)
          || !Double.isFinite(ratio[index]) || !(overall[index] > 0.0) || !Double.isFinite(overall[index])) {
        throw new IllegalStateException("Film returned a nonfinite flux, coefficient or equilibrium ratio");
      }
      flux[index] = value;
    }
    // Identical phases on the two sides of the interface: the bulk gas and oil are miscible here.
    double largestLogRatio = 0.0;
    for (int component = 0; component < count; component++) {
      largestLogRatio = Math.max(largestLogRatio, Math.abs(Math.log(ratio[transportIndex[component]])));
    }
    cachedMixingTimeSeconds[cell] = largestLogRatio < MISCIBLE_LOG_EQUILIBRIUM_RATIO ? mixingTime(section) : 0.0;
    cachedOverallCoefficientMetresPerSecond[cell] = overall;
    cachedEquilibriumRatio[cell] = ratio;
    cachedInterfaceGasFraction[cell] = interfaceGasFraction;
    cachedInterfaceOilFraction[cell] = interfaceOilFraction;
    cachedMolarMassKgPerMol[cell] = molarMasses;

    // Entrained droplets: fraction and size from the entrainment closure, coefficients for a sphere.
    double[] dropletOverall = new double[names.length];
    cachedEntrainedFraction[cell] = 0.0;
    cachedDropletDiameterMetres[cell] = 0.0;
    if (dropletField) {
      double gasDensity = nodeBulk.getPhase(0).getDensity("kg/m3");
      double oilDensity = nodeBulk.getPhase(1).getDensity("kg/m3");
      double gasViscosity = nodeBulk.getPhase(0).getPhysicalProperties().getViscosity();
      double oilViscosity = nodeBulk.getPhase(1).getPhysicalProperties().getViscosity();
      double surfaceTension = nodeBulk.getInterphaseProperties().getSurfaceTension(0, 1);
      if (!(surfaceTension > 0.0) || !Double.isFinite(surfaceTension)) {
        surfaceTension = 5.0e-3;
      }
      double gasSpeed = Math.abs(section.getGasVelocity());
      EntrainmentDeposition.EntrainmentResult entrainment = new EntrainmentDeposition().calculate(FlowRegime.ANNULAR,
          gasSpeed * (1.0 - liquidHoldup), Math.max(Math.abs(section.getLiquidVelocity()) * liquidHoldup, 1.0e-9),
          gasDensity, oilDensity, gasViscosity, oilViscosity, surfaceTension, section.getDiameter(), liquidHoldup);
      double entrained = entrainment.entrainmentFraction;
      double dropletDiameter = entrainment.dropletDiameter;
      if (entrained > 0.0 && entrained <= 1.0 && dropletDiameter > 0.0 && Double.isFinite(dropletDiameter)) {
        cachedEntrainedFraction[cell] = entrained;
        cachedDropletDiameterMetres[cell] = dropletDiameter;
        // Settling speed of the droplet from the Archimedes number, Re = Ar / (18 + 0.61 sqrt(Ar)).
        double archimedes = 9.81 * Math.pow(dropletDiameter, 3.0) * gasDensity * Math.abs(oilDensity - gasDensity)
            / (gasViscosity * gasViscosity);
        double dropletReynolds = archimedes / (18.0 + 0.61 * Math.sqrt(archimedes));
        double gasKinematicViscosity = gasViscosity / gasDensity;
        nodeBulk.getPhase(0).getPhysicalProperties().calcEffectiveDiffusionCoefficients();
        nodeBulk.getPhase(1).getPhysicalProperties().calcEffectiveDiffusionCoefficients();
        for (int component = 0; component < count; component++) {
          int index = transportIndex[component];
          double gasDiffusivity = nodeBulk.getPhase(0).getPhysicalProperties()
              .getEffectiveDiffusionCoefficient(component);
          double oilDiffusivity = nodeBulk.getPhase(1).getPhysicalProperties()
              .getEffectiveDiffusionCoefficient(component);
          if (!(gasDiffusivity > 0.0) || !(oilDiffusivity > 0.0) || !Double.isFinite(gasDiffusivity)
              || !Double.isFinite(oilDiffusivity)) {
            continue;
          }
          // Ranz and Marshall outside the droplet, rigid-sphere diffusion limit inside it.
          double gasSherwood = 2.0
              + 0.6 * Math.sqrt(dropletReynolds) * Math.cbrt(gasKinematicViscosity / gasDiffusivity);
          double gasDropletCoefficient = gasSherwood * gasDiffusivity / dropletDiameter;
          double oilDropletCoefficient = 6.58 * oilDiffusivity / dropletDiameter;
          dropletOverall[index] = 1.0 / (1.0 / gasDropletCoefficient
              + ratio[index] * gasMolarDensity / (oilMolarDensity * oilDropletCoefficient));
        }
      }
    }
    cachedDropletOverallCoefficientMetresPerSecond[cell] = dropletOverall;
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
   * @return true when no mixture of the two phases, from one tenth to nine tenths oil by moles, splits in two
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
        if (trial.getNumberOfPhases() != 1) {
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
   * Flash one phase inventory of a cell on its own and return what it sheds.
   *
   * @param template thermodynamic template
   * @param names transported component names
   * @param phaseInventory component masses of the phase in kg
   * @param section hydrodynamic cell, for pressure and temperature
   * @param gas true to flash the gas inventory and return the liquid it forms, false to flash the oil inventory and
   * return the vapour it forms
   * @return shed component masses in kg in transport order, or null when the phase is stable on its own
   */
  private double[] bulkSplit(SystemInterface template, String[] names, double[] phaseInventory, TwoFluidSection section,
      boolean gas) {
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
      if (bulk.getNumberOfPhases() != 2) {
        return null;
      }
      // Fog is the denser of the two phases the gas forms; flashing vapour the lighter of the two the oil forms.
      boolean firstDenser = bulk.getPhase(0).getDensity() > bulk.getPhase(1).getDensity();
      int shedPhase = gas == firstDenser ? 0 : 1;
      double[] shed = new double[names.length];
      for (int component = 0; component < count; component++) {
        double mass = bulk.getPhase(shedPhase).getComponent(component).getNumberOfMolesInPhase()
            * bulk.getPhase(shedPhase).getComponent(component).getMolarMass();
        if (!Double.isFinite(mass) || mass < 0.0) {
          return null;
        }
        shed[transportIndex[component]] = Math.min(mass, phaseInventory[transportIndex[component]]);
      }
      return shed;
    } catch (RuntimeException exception) {
      bulkSplitFailureCount++;
      return null;
    }
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
