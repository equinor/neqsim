package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.Arrays;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.thermo.system.SystemInterface;

/**
 * Conductance- and heat-limited gas/oil transfer for a resolved hydrocarbon pool.
 *
 * <p>
 * The local equilibrium flash supplies only the component inventory target. User-supplied overall transfer coefficients
 * and an explicitly selected interface-area model supply the kinetics. For component {@code i}, the unconstrained oil
 * source is
 * </p>
 *
 * <pre>
 *   Gamma_i = (k_i A_i / V) (m_i,oil,eq - m_i,oil)
 * </pre>
 *
 * <p>
 * where {@code A_i/L} is a prescribed width, a local flowing stratified chord or a static hydrostatic-cell interface,
 * and {@code V/L = pi diameter^2 / 4}. Sources are donor limited for the accepted Euler interval and gas receives the
 * exact opposite of every oil source. An optional externally supplied heat rate limits net endothermic transfer. The
 * existing component and total-energy transports remain the inventory and energy owners.
 * </p>
 *
 * <p>
 * Coefficients are explicit model inputs. This closure provides analytical and conservation qualification but is not a
 * predictive evaporation correlation until its coefficients are independently validated.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class HydrocarbonPoolTransfer implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double MAX_SOURCE_FRACTION = 0.5;
  private final String[] components;
  private final double[] coefficients;
  private final double[] evaporationEnthalpiesJPerKg;
  private final double interfaceWidthFraction;
  private final double maximumHeatSupplyWPerMetre;
  private final boolean hydrostaticInterfaceArea;
  /** Use a local flowing chord rather than a stationary puddle in each cell. */
  private final boolean stratifiedInterfaceArea;

  /**
   * Immutable diagnostics and component sources for one stage.
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double[][][] sources;
    private final double[][] rateConstants;
    private final double[] interfaceAreasSquareMetres;
    private final boolean[] equilibriumAppearanceArea;
    private final boolean[] unresolvedTraceAreaFallback;
    private final double[] unconstrainedLatentHeatWPerMetre;
    private final double[] heatScale;
    private final boolean[] donorLimited;

    private Result(double[][][] sources, double[][] rateConstants, double[] interfaceAreasSquareMetres,
        boolean[] equilibriumAppearanceArea, boolean[] unresolvedTraceAreaFallback,
        double[] unconstrainedLatentHeatWPerMetre, double[] heatScale, boolean[] donorLimited) {
      this.sources = sources;
      this.rateConstants = rateConstants;
      this.interfaceAreasSquareMetres = interfaceAreasSquareMetres;
      this.equilibriumAppearanceArea = equilibriumAppearanceArea;
      this.unresolvedTraceAreaFallback = unresolvedTraceAreaFallback;
      this.unconstrainedLatentHeatWPerMetre = unconstrainedLatentHeatWPerMetre;
      this.heatScale = heatScale;
      this.donorLimited = donorLimited;
    }

    /** @return defensive cell/phase/component sources in kg/(m s) */
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

    /** @return configured component rate constants by cell in 1/s */
    public double[][] getRateConstantsPerSecond() {
      double[][] copy = new double[rateConstants.length][];
      for (int cell = 0; cell < rateConstants.length; cell++) {
        copy[cell] = rateConstants[cell].clone();
      }
      return copy;
    }

    /** @return gas/oil interface area used by each cell in square metres */
    public double[] getInterfaceAreasSquareMetres() {
      return interfaceAreasSquareMetres.clone();
    }

    /**
     * @return whether each zero-area accepted cell used the equilibrium target geometry to admit physical phase
     * appearance
     */
    public boolean[] getEquilibriumAppearanceArea() {
      return equilibriumAppearanceArea.clone();
    }

    /**
     * @return whether a sub-resolution retained pool used the local stratified chord area instead of an unresolved
     * inclined hydrostatic reconstruction
     */
    public boolean[] getUnresolvedTraceAreaFallback() {
      return unresolvedTraceAreaFallback.clone();
    }

    /** @return latent heat before the heat-supply limit by cell in W/m; evaporation is negative */
    public double[] getUnconstrainedLatentHeatWPerMetre() {
      return unconstrainedLatentHeatWPerMetre.clone();
    }

    /** @return multiplicative heat-limit scale by cell in [0, 1] */
    public double[] getHeatScale() {
      return heatScale.clone();
    }

    /** @return whether a component donor limit was active in each cell */
    public boolean[] getDonorLimited() {
      return donorLimited.clone();
    }
  }

  /**
   * Construct an immutable dry-hydrocarbon pool-transfer closure.
   *
   * @param components unique NeqSim component names
   * @param coefficients overall transfer coefficients in m/s, one per component
   * @param evaporationEnthalpiesJPerKg positive evaporation heat requirements in J/kg, one per component
   * @param interfaceWidthFraction prescribed interface width divided by pipe diameter, in [0, 1]
   * @param maximumHeatSupplyWPerMetre maximum external heat available to net evaporation in W/m; positive infinity
   * disables this limit
   */
  public HydrocarbonPoolTransfer(String[] components, double[] coefficients, double[] evaporationEnthalpiesJPerKg,
      double interfaceWidthFraction, double maximumHeatSupplyWPerMetre) {
    this(components, coefficients, evaporationEnthalpiesJPerKg, interfaceWidthFraction, maximumHeatSupplyWPerMetre,
        false, false);
  }

  /**
   * Construct a transfer closure using the local circular-stratified interface of the flowing state.
   *
   * <p>
   * The cell-average holdup determines one cross-sectional chord, extended over the cell arclength. Subdividing a
   * uniform state therefore preserves total interface area and integrated component sources. This does not impose a
   * separate stationary horizontal puddle in every numerical cell. A gas-only cell can use its equilibrium oil target
   * for first appearance without adding seed inventory. Empty and full target cross-sections have zero interface. This
   * assumption excludes annular, dispersed, slug-front and moving-pig interfaces; coefficients still require
   * independent physical validation.
   * </p>
   *
   * @param components unique NeqSim component names
   * @param coefficients overall transfer coefficients in m/s, one per component
   * @param evaporationEnthalpiesJPerKg positive evaporation heat requirements in J/kg, one per component
   * @param maximumHeatSupplyWPerMetre maximum external heat available to net evaporation in W/m; positive infinity
   * disables this limit
   * @return immutable local stratified-flow transfer closure
   */
  public static HydrocarbonPoolTransfer forStratifiedFlow(String[] components, double[] coefficients,
      double[] evaporationEnthalpiesJPerKg, double maximumHeatSupplyWPerMetre) {
    return new HydrocarbonPoolTransfer(components, coefficients, evaporationEnthalpiesJPerKg, 0.0,
        maximumHeatSupplyWPerMetre, false, true);
  }

  /**
   * Construct a transfer closure using the volume-preserving static hydrostatic interface diagnostic in every cell.
   *
   * <p>
   * This is appropriate only for a resolved stationary or slowly draining stratified pool. It is not an annular,
   * dispersed, slug-front or moving-pig area closure. A zero-area accepted gas cell with a positive equilibrium oil
   * target uses that target's volume-preserving hydrostatic area for the appearance step; accepted pool geometry owns
   * subsequent steps. When an inclined cell's retained volume is below the hydrostatic diagnostic's representable
   * relative-volume tolerance, the closure reports and uses the local stratified chord area rather than deleting or
   * clipping the trace inventory. A full liquid cell without a gas interface remains blocked.
   * </p>
   *
   * <p>
   * Independent per-cell stationary reconstructions are not invariant when a flowing uniform state is subdivided. Use
   * {@link #forStratifiedFlow(String[], double[], double[], double)} for that local flowing-interface assumption.
   * Static subdivision comparisons require cell volumes consistent with the same physical free surface.
   * </p>
   *
   * @param components unique NeqSim component names
   * @param coefficients overall transfer coefficients in m/s, one per component
   * @param evaporationEnthalpiesJPerKg positive evaporation heat requirements in J/kg, one per component
   * @param maximumHeatSupplyWPerMetre maximum external heat available to net evaporation in W/m; positive infinity
   * disables this limit
   * @return immutable hydrostatic-pool transfer closure
   */
  public static HydrocarbonPoolTransfer forHydrostaticPool(String[] components, double[] coefficients,
      double[] evaporationEnthalpiesJPerKg, double maximumHeatSupplyWPerMetre) {
    return new HydrocarbonPoolTransfer(components, coefficients, evaporationEnthalpiesJPerKg, 0.0,
        maximumHeatSupplyWPerMetre, true, false);
  }

  /**
   * Validate and store immutable transfer inputs.
   *
   * @param components unique component names
   * @param coefficients overall transfer coefficients in m/s
   * @param evaporationEnthalpiesJPerKg positive evaporation enthalpies in J/kg
   * @param interfaceWidthFraction fixed interface width divided by diameter
   * @param maximumHeatSupplyWPerMetre maximum evaporation heat supply in W/m
   * @param hydrostaticInterfaceArea whether to reconstruct the stationary pool area
   * @param stratifiedInterfaceArea whether to use the flowing local cross-sectional interface
   */
  private HydrocarbonPoolTransfer(String[] components, double[] coefficients, double[] evaporationEnthalpiesJPerKg,
      double interfaceWidthFraction, double maximumHeatSupplyWPerMetre, boolean hydrostaticInterfaceArea,
      boolean stratifiedInterfaceArea) {
    if (components == null || coefficients == null || evaporationEnthalpiesJPerKg == null || components.length == 0
        || components.length != coefficients.length || components.length != evaporationEnthalpiesJPerKg.length
        || !Double.isFinite(interfaceWidthFraction) || interfaceWidthFraction < 0.0 || interfaceWidthFraction > 1.0
        || Double.isNaN(maximumHeatSupplyWPerMetre) || maximumHeatSupplyWPerMetre < 0.0) {
      throw new IllegalArgumentException(
          "Pool-transfer species, coefficients, interface and heat supply must be valid");
    }
    for (int i = 0; i < components.length; i++) {
      if (components[i] == null || components[i].trim().isEmpty() || !Double.isFinite(coefficients[i])
          || coefficients[i] < 0.0 || !Double.isFinite(evaporationEnthalpiesJPerKg[i])
          || evaporationEnthalpiesJPerKg[i] < 0.0) {
        throw new IllegalArgumentException(
            "Pool-transfer coefficients and evaporation enthalpies must be finite and nonnegative");
      }
      for (int previous = 0; previous < i; previous++) {
        if (components[i].equals(components[previous])) {
          throw new IllegalArgumentException("Duplicate pool-transfer component " + components[i]);
        }
      }
    }
    this.components = components.clone();
    this.coefficients = coefficients.clone();
    this.evaporationEnthalpiesJPerKg = evaporationEnthalpiesJPerKg.clone();
    this.interfaceWidthFraction = interfaceWidthFraction;
    this.maximumHeatSupplyWPerMetre = maximumHeatSupplyWPerMetre;
    this.hydrostaticInterfaceArea = hydrostaticInterfaceArea;
    this.stratifiedInterfaceArea = stratifiedInterfaceArea;
  }

  /**
   * Evaluate paired component sources for one Euler stage.
   *
   * @param transport accepted component inventories
   * @param sections hydrodynamic cells
   * @param equilibriumStates local source-stage equilibrium flashes
   * @param timeStepSeconds accepted Euler substep in seconds
   * @return sources and rate-limit diagnostics
   */
  public Result calculate(TwoFluidComponentTransport transport, TwoFluidSection[] sections,
      SystemInterface[] equilibriumStates, double timeStepSeconds) {
    if (transport == null || sections == null || equilibriumStates == null || !Double.isFinite(timeStepSeconds)
        || timeStepSeconds <= 0.0) {
      throw new IllegalArgumentException("Pool transfer requires inventories, cells, flashes and finite positive time");
    }
    String[] names = transport.getComponentNames();
    int[] indices = new int[components.length];
    for (int configured = 0; configured < components.length; configured++) {
      indices[configured] = Arrays.asList(names).indexOf(components[configured]);
      if (indices[configured] < 0) {
        throw new IllegalArgumentException("Missing pool-transfer component " + components[configured]);
      }
    }
    double[][][] targetChanges = transport.createHydrocarbonEquilibriumInventoryChanges(equilibriumStates, sections);
    if (targetChanges == null) {
      throw new IllegalStateException("Hydrocarbon pool transfer requires dry gas/oil inventories and equilibrium");
    }
    double[][][] inventory = transport.getInventoryKg();
    double[][][] sources = new double[sections.length][3][names.length];
    double[][] rateConstants = new double[sections.length][components.length];
    double[] interfaceAreas = new double[sections.length];
    boolean[] equilibriumAppearanceArea = new boolean[sections.length];
    boolean[] unresolvedTraceAreaFallback = new boolean[sections.length];
    boolean[] donorLimited = new boolean[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      TwoFluidSection section = sections[cell];
      double diameter = section.getDiameter();
      double volumePerMetre = Math.PI * diameter * diameter / 4.0;
      if (!(diameter > 0.0) || !Double.isFinite(diameter) || !(section.getLength() > 0.0)
          || !Double.isFinite(section.getLength())) {
        throw new IllegalStateException("Pool-transfer cells require finite positive diameter and length");
      }
      double interfaceArea;
      if (stratifiedInterfaceArea) {
        interfaceArea = new GeometryCalculator()
            .calculateHydrostaticCell(section.getLiquidHoldup(), diameter, section.getLength(), 0.0).getInterfaceArea();
      } else if (hydrostaticInterfaceArea) {
        try {
          interfaceArea = section.getHydrostaticCellGeometry().getInterfaceArea();
        } catch (IllegalArgumentException exception) {
          if (!"Static liquid volume is not resolvable at this cell scale".equals(exception.getMessage())) {
            throw exception;
          }
          interfaceArea = new GeometryCalculator().calculateFromHoldup(section.getLiquidHoldup(),
              diameter).interfacialWidth * section.getLength();
          unresolvedTraceAreaFallback[cell] = true;
        }
      } else {
        interfaceArea = interfaceWidthFraction * diameter * section.getLength();
      }
      if ((hydrostaticInterfaceArea || stratifiedInterfaceArea) && interfaceArea == 0.0
          && section.getLiquidHoldup() == 0.0) {
        double targetOilMassPerMetre = 0.0;
        for (double oilChange : targetChanges[cell][1]) {
          targetOilMassPerMetre += oilChange;
        }
        if (targetOilMassPerMetre > 0.0 && equilibriumStates[cell].hasPhaseType("oil")) {
          for (double oilInventory : inventory[cell][1]) {
            targetOilMassPerMetre += oilInventory / section.getLength();
          }
          double oilDensity = equilibriumStates[cell].getPhaseOfType("oil").getDensity("kg/m3");
          double targetHoldup = targetOilMassPerMetre / oilDensity / volumePerMetre;
          if (!Double.isFinite(targetHoldup) || targetHoldup <= 0.0) {
            throw new IllegalStateException("Equilibrium oil target must have finite positive volume");
          }
          targetHoldup = Math.min(1.0, targetHoldup);
          interfaceArea = new GeometryCalculator().calculateHydrostaticCell(targetHoldup, diameter, section.getLength(),
              stratifiedInterfaceArea ? 0.0 : section.getInclination()).getInterfaceArea();
          equilibriumAppearanceArea[cell] = interfaceArea > 0.0;
        }
      }
      if (!Double.isFinite(interfaceArea) || interfaceArea < 0.0) {
        throw new IllegalStateException("Pool-transfer interface area must be finite and nonnegative");
      }
      interfaceAreas[cell] = interfaceArea;
      for (int configured = 0; configured < components.length; configured++) {
        int component = indices[configured];
        double rateConstant = coefficients[configured] * interfaceArea / (volumePerMetre * section.getLength());
        rateConstants[cell][configured] = rateConstant;
        double oilRate = rateConstant * targetChanges[cell][1][component];
        int donor = oilRate > 0.0 ? 0 : 1;
        double donorLimit = MAX_SOURCE_FRACTION * inventory[cell][donor][component] / section.getLength()
            / timeStepSeconds;
        double limitedRate = Math.copySign(Math.min(Math.abs(oilRate), donorLimit), oilRate);
        donorLimited[cell] |= Math.abs(limitedRate) < Math.abs(oilRate);
        sources[cell][1][component] = limitedRate;
        sources[cell][0][component] = -limitedRate;
      }
    }

    double[] unconstrainedLatentHeat = new double[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      for (int configured = 0; configured < components.length; configured++) {
        unconstrainedLatentHeat[cell] += sources[cell][1][indices[configured]]
            * evaporationEnthalpiesJPerKg[configured];
      }
    }
    double[] heatScale = new double[sections.length];
    Arrays.fill(heatScale, 1.0);
    if (Double.isFinite(maximumHeatSupplyWPerMetre)) {
      for (int cell = 0; cell < sections.length; cell++) {
        double evaporationDemand = Math.max(0.0, -unconstrainedLatentHeat[cell]);
        if (evaporationDemand > maximumHeatSupplyWPerMetre) {
          heatScale[cell] = maximumHeatSupplyWPerMetre / evaporationDemand;
          for (int phase = 0; phase < 2; phase++) {
            for (int component = 0; component < names.length; component++) {
              sources[cell][phase][component] *= heatScale[cell];
            }
          }
        }
      }
    }
    return new Result(sources, rateConstants, interfaceAreas, equilibriumAppearanceArea, unresolvedTraceAreaFallback,
        unconstrainedLatentHeat, heatScale, donorLimited);
  }
}
