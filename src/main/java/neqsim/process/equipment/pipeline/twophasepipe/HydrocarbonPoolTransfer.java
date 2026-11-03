package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import java.util.Arrays;
import neqsim.thermo.system.SystemInterface;

/**
 * Conductance- and heat-limited gas/oil transfer for a resolved hydrocarbon pool.
 *
 * <p>
 * The local equilibrium flash supplies only the component inventory target. User-supplied overall transfer coefficients
 * and an explicit interface width supply the kinetics. For component {@code i}, the unconstrained oil source is
 * </p>
 *
 * <pre>
 *   Gamma_i = (k_i A_i / V) (m_i,oil,eq - m_i,oil)
 * </pre>
 *
 * <p>
 * where {@code A_i/L = interfaceWidthFraction * diameter} and {@code V/L = pi diameter^2 / 4}. Sources are donor
 * limited for the accepted Euler interval and gas receives the exact opposite of every oil source. An optional
 * externally supplied heat rate limits net endothermic transfer. The existing component and total-energy transports
 * remain the inventory and energy owners.
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

  /**
   * Immutable diagnostics and component sources for one stage.
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double[][][] sources;
    private final double[][] rateConstants;
    private final double[] unconstrainedLatentHeatWPerMetre;
    private final double[] heatScale;
    private final boolean[] donorLimited;

    private Result(double[][][] sources, double[][] rateConstants, double[] unconstrainedLatentHeatWPerMetre,
        double[] heatScale, boolean[] donorLimited) {
      this.sources = sources;
      this.rateConstants = rateConstants;
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
    boolean[] donorLimited = new boolean[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      TwoFluidSection section = sections[cell];
      double diameter = section.getDiameter();
      double volumePerMetre = Math.PI * diameter * diameter / 4.0;
      double interfaceWidth = interfaceWidthFraction * diameter;
      if (!(diameter > 0.0) || !Double.isFinite(diameter) || !(section.getLength() > 0.0)
          || !Double.isFinite(section.getLength())) {
        throw new IllegalStateException("Pool-transfer cells require finite positive diameter and length");
      }
      for (int configured = 0; configured < components.length; configured++) {
        int component = indices[configured];
        double rateConstant = coefficients[configured] * interfaceWidth / volumePerMetre;
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
    return new Result(sources, rateConstants, unconstrainedLatentHeat, heatScale, donorLimited);
  }
}
