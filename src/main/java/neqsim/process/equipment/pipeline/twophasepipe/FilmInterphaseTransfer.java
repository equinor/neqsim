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
 * whose flux points away from equilibrium (a transient, for example ethane dissolving while the liquid as a whole
 * evaporates) saturates when the bulk of the limiting phase reaches the interface composition of the film
 * solution.</li>
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
 * <li>Where the cell composition is single phase at equilibrium (above the local phase envelope, including dense phase
 * where gas and oil are miscible), the phase holding less mass can only lose components: film fluxes toward it are set
 * to zero, so a miscible "oil" cannot grow by absorbing gas.</li>
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
    RELAXATION
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
  private double[] cachedRetryTimeSeconds;
  private long filmSolveCount;
  private long filmFailureCount;
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
    private final long filmSolveCount;
    private final long filmFailureCount;
    private final String lastFailure;

    private Result(double[][][] sources, CellMode[] modes, double[] interfaceWidths, double[] netMassFluxes,
        double[] interfaceTemperatures, double[] slowestTimeConstants, double[] characteristicTimes,
        double[] entrainedFractions, double[] dropletAreas, boolean[] donorLimited, boolean[] equilibriumLimited,
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
      if (filmPossible) {
        // A time before the cached one means a rejected step was rolled back: solve again.
        boolean rolledBack = timeSeconds < cachedTimeSeconds[cell];
        boolean stale = rolledBack || cachedFilm[cell] && timeSeconds - cachedTimeSeconds[cell] >= updateIntervalSeconds
            || !cachedFilm[cell] && timeSeconds >= cachedRetryTimeSeconds[cell];
        if (stale) {
          try {
            filmSolveCount++;
            double[] interfaceTemperature = new double[1];
            cachedMassFluxKgPerSquareMetreSecond[cell] = solveFilm(cell, section, template, names, inventory[cell],
                interfaceTemperature);
            cachedInterfaceTemperatureK[cell] = interfaceTemperature[0];
            cachedFilm[cell] = true;
            cachedTimeSeconds[cell] = timeSeconds;
          } catch (RuntimeException exception) {
            filmFailureCount++;
            lastFailure = "cell " + cell + " at " + timeSeconds + " s: " + exception.getMessage();
            cachedFilm[cell] = false;
            cachedTimeSeconds[cell] = timeSeconds;
            cachedRetryTimeSeconds[cell] = timeSeconds
                + FAILURE_RETRY_FACTOR * Math.max(updateIntervalSeconds, timeStepSeconds);
            filmPossible = false;
          }
        }
        filmPossible = filmPossible && cachedFilm[cell];
      } else {
        cachedFilm[cell] = false;
        cachedRetryTimeSeconds[cell] = Double.NEGATIVE_INFINITY;
      }

      double[] oilRates = new double[names.length];
      if (filmPossible) {
        modes[cell] = CellMode.FILM;
        double[] flux = cachedMassFluxKgPerSquareMetreSecond[cell];
        netFluxes[cell] = sum(flux);
        double netTarget = sum(targetChanges[cell][1]);
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
        for (double target : targetChanges[cell][1]) {
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
          double target = targetChanges[cell][1][component];
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
            } else if (singlePhaseEquilibrium || filmRate == 0.0) {
              rate = 0.0;
            } else {
              // Against the direction of local equilibrium: a transient that saturates when the bulk of the
              // limiting phase reaches the interface composition of the film solution.
              double molarMass = cachedMolarMassKgPerMol[cell][component];
              double oilFraction = inventory[cell][1][component] / molarMass / oilMoles;
              double gasFraction = inventory[cell][0][component] / molarMass / gasMoles;
              double interfaceOil = cachedInterfaceOilFraction[cell][component];
              double interfaceGas = cachedInterfaceGasFraction[cell][component];
              double direction = Math.signum(filmRate);
              double oilCapacity = oilMoles * Math.max(0.0, direction * (interfaceOil - oilFraction))
                  / (1.0 - interfaceOil);
              double gasCapacity = gasMoles * Math.max(0.0, direction * (gasFraction - interfaceGas))
                  / (1.0 - interfaceGas);
              double capacityMoles = oilCapacity > 0.0 && gasCapacity > 0.0 ? Math.min(oilCapacity, gasCapacity)
                  : Math.max(oilCapacity, gasCapacity);
              double capacity = capacityMoles * molarMass / length;
              rate = capacity > 0.0 && Double.isFinite(capacity) ? direction * capacity
                  * -Math.expm1(-Math.abs(filmRate) * timeStepSeconds / capacity) / timeStepSeconds : 0.0;
            }
          }
          oilRates[component] = rate;
        }
        if (initialRate > 0.0) {
          characteristicTimes[cell] = distance / initialRate;
        }
      } else {
        boolean any = false;
        for (int component = 0; component < names.length; component++) {
          oilRates[component] = targetChanges[cell][1][component] / fallbackRelaxationTimeSeconds;
          any |= oilRates[component] != 0.0;
        }
        modes[cell] = any ? CellMode.RELAXATION : CellMode.NONE;
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
        characteristicTimes, entrainedFractions, dropletAreas, donorLimited, equilibriumLimited, filmSolveCount,
        filmFailureCount, lastFailure);
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
