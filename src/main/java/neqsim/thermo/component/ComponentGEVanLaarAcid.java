/*
 * ComponentGEVanLaarAcid.java
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure;

/**
 * <p>
 * ComponentGEVanLaarAcid class.
 * </p>
 *
 * <p>
 * Excess-Gibbs-energy (activity-coefficient) component for the
 * water-nitric-acid-sulfuric-acid
 * system, using the Van Laar model of Taleb, Ponche and Mirabel (1996). The
 * activity coefficient and
 * the pure-component saturation vapour pressure are obtained from
 * {@link neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure}, so the
 * component reproduces the
 * partial pressures of that reference exactly.
 * </p>
 *
 * <p>
 * The fugacity coefficient is forced onto the symmetric (Raoult) reference
 * state
 * {@code phi_i = gamma_i * P0_i / P}, regardless of the
 * {@code referenceStateType} stored in the
 * component database. This guarantees the gamma-phi equilibrium identity
 * {@code fugacity_i = x_i * phi_i * P = gamma_i * x_i * P0_i} for water, nitric
 * acid and sulfuric
 * acid even though the two acids are tagged as "solute" (Henry's-law) species
 * in the NeqSim
 * component database.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class ComponentGEVanLaarAcid extends ComponentGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Conversion factor from pascal (Pa) to bar. */
  private static final double PA_TO_BAR = 1.0e5;

  /** Activity/fugacity penalty for species outside the Van Laar acid model. */
  private static final double NON_MODELED_COMPONENT_PENALTY = 1.0e12;

  /**
   * Acid identity: 1 = water, 2 = nitric acid, 3 = sulfuric acid, 0 = other/inert
   * species.
   */
  private final int acidIndex;

  /**
   * <p>
   * Constructor for ComponentGEVanLaarAcid.
   * </p>
   *
   * @param name         Name of component.
   * @param moles        Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex    Index number of component in phase object component
   *                     array.
   */
  public ComponentGEVanLaarAcid(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    this.acidIndex = acidIndexOf(name);
  }

  /**
   * <p>
   * Map a component name onto the Taleb et al. (1996) acid index (1 = water, 2 =
   * nitric acid, 3 =
   * sulfuric acid). Common synonyms and chemical formulae are recognised.
   * </p>
   *
   * @param name component name (case-insensitive); may be {@code null}
   * @return the acid index 1, 2 or 3, or 0 if the name is not one of the three
   *         modelled species
   */
  static int acidIndexOf(String name) {
    if (name == null) {
      return 0;
    }
    String n = name.trim().toLowerCase();
    if (n.equals("water") || n.equals("h2o")) {
      return 1;
    }
    if (n.equals("nitric acid") || n.equals("hno3")) {
      return 2;
    }
    if (n.equals("sulfuric acid") || n.equals("sulphuric acid") || n.equals("h2so4")) {
      return 3;
    }
    return 0;
  }

  /**
   * <p>
   * Extract the acid-basis mole fractions {x_H2O, x_HNO3, x_H2SO4} from a phase.
   * Any species that is
   * not one of the three modelled acids (for example a dissolved carrier gas) is
   * ignored and the
   * three acid mole fractions are renormalised to sum to unity, so the Van Laar
   * model is always
   * evaluated on its native composition basis.
   * </p>
   *
   * @param phase the phase to read mole fractions from
   * @return a three-element array {x1, x2, x3} that sums to one (defaults to pure
   *         water if no acid
   *         is present)
   */
  static double[] acidMoleFractions(PhaseInterface phase) {
    double x1 = 0.0;
    double x2 = 0.0;
    double x3 = 0.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      int idx = acidIndexOf(phase.getComponent(i).getName());
      double xi = phase.getComponent(i).getx();
      if (idx == 1) {
        x1 += xi;
      } else if (idx == 2) {
        x2 += xi;
      } else if (idx == 3) {
        x3 += xi;
      }
    }
    double sum = x1 + x2 + x3;
    if (sum <= 0.0) {
      return new double[] { 1.0, 0.0, 0.0 };
    }
    return new double[] { x1 / sum, x2 / sum, x3 / sum };
  }

  /**
   * <p>
   * Compute, store and return the Van Laar activity coefficient of this component
   * for the current
   * composition and temperature of the supplied phase. Species that are not one
   * of the three
   * modelled acids are assigned a very high activity so the liquid phase rejects
   * them.
   * </p>
   *
   * @param phase the phase supplying composition and temperature
   * @return the activity coefficient (dimensionless)
   */
  private double computeGamma(PhaseInterface phase) {
    if (dlngammadn == null || dlngammadn.length != phase.getNumberOfComponents()) {
      dlngammadn = new double[phase.getNumberOfComponents()];
    }
    if (acidIndex == 0) {
      gamma = NON_MODELED_COMPONENT_PENALTY;
      lngamma = Math.log(gamma);
      return gamma;
    }
    double[] x = acidMoleFractions(phase);
    double t = phase.getTemperature();
    if (acidIndex == 1) {
      gamma = NitricSulfuricAcidVaporPressure.activityCoefficientWater(x[0], x[1], x[2], t);
    } else if (acidIndex == 2) {
      gamma = NitricSulfuricAcidVaporPressure.activityCoefficientNitricAcid(x[0], x[1], x[2], t);
    } else {
      gamma = NitricSulfuricAcidVaporPressure.activityCoefficientSulfuricAcid(x[0], x[1], x[2], t);
    }
    lngamma = Math.log(gamma);
    return gamma;
  }

  /**
   * <p>
   * Pure-component saturation vapour pressure of this component, expressed in bar
   * (the internal
   * NeqSim pressure unit). For the three modelled acids the value comes from the
   * Taleb et al. (1996)
   * correlations; any other species falls back to its database Antoine
   * correlation.
   * </p>
   *
   * @param temperature temperature in kelvin
   * @return the pure-component vapour pressure in bar
   */
  private double pureVaporPressureBar(double temperature) {
    if (acidIndex == 1) {
      return NitricSulfuricAcidVaporPressure.pureVaporPressureWater(temperature) / PA_TO_BAR;
    } else if (acidIndex == 2) {
      return NitricSulfuricAcidVaporPressure.pureVaporPressureNitricAcid(temperature) / PA_TO_BAR;
    } else if (acidIndex == 3) {
      return NitricSulfuricAcidVaporPressure.pureVaporPressureSulfuricAcid(temperature) / PA_TO_BAR;
    }
    return getAntoineVaporPressure(temperature);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Overridden to enforce the symmetric (Raoult) reference state for every
   * component, giving
   * {@code phi_i = gamma_i * P0_i / P}. This bypasses the Henry's-law branch that
   * {@link ComponentGE#fugcoef(PhaseInterface)} would otherwise apply to the
   * acids and yields the
   * gamma-phi identity {@code fugacity_i = gamma_i * x_i * P0_i}.
   * </p>
   */
  @Override
  public double fugcoef(PhaseInterface phase) {
    computeGamma(phase);
    if (acidIndex == 0) {
      fugacityCoefficient = NON_MODELED_COMPONENT_PENALTY;
      gammaRefCor = gamma;
      return fugacityCoefficient;
    }
    double p0Bar = pureVaporPressureBar(phase.getTemperature());
    fugacityCoefficient = gamma * p0Bar / phase.getPressure();
    gammaRefCor = gamma;
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt, double[][] HValpha, double[][] HVgij, double[][] intparam,
      String[][] mixRule) {
    return computeGamma(phase);
  }
}
