package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Chung et al. (1988) dilute and dense-fluid thermal conductivity with conformal mixture rules.
 *
 * <p>
 * Uses the original conductivity coefficients, also reproduced in Bouraba et al., International Journal of
 * Refrigeration 80 (2017), Table A.2. Some handbook tables change the sign of the association coefficient in B6 and use
 * a different B7 row. No critical enhancement is included. Association uses the transport correction factor, not the
 * CPA association volume. Binary combining-rule factors are unity.
 *
 * @author Even Solbraa
 * @version 1.1
 */
public class ChungDenseConductivityMethod extends Conductivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Original Chung conductivity coefficients: constant, acentric, dipole and association terms. */
  private static final double[][] COEFFICIENTS = {{2.41657, 0.74824, -0.91858, 121.721},
      {-0.50924, -1.50936, -49.9912, 69.9834}, {6.61069, 5.62073, 64.7599, 27.0389},
      {14.5425, -8.91387, -5.63794, 74.3435}, {0.79274, 0.82019, -0.69369, 6.31734},
      {-5.8634, 12.8005, 9.58926, -65.5292}, {81.171, 114.158, -60.841, 466.775}};

  /**
   * Creates a conductivity method bound to an existing physical-property package.
   *
   * @param phase physical-property package supplying composition, temperature and density
   */
  public ChungDenseConductivityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    PhaseInterface fluid = phase.getPhase();
    int count = fluid.getNumberOfComponents();
    double temperature = fluid.getTemperature();
    if (count == 1) {
      ComponentInterface component = fluid.getComponent(0);
      if (!Double.isFinite(component.getx()) || Math.abs(component.getx() - 1.0) > 1e-8) {
        throw new IllegalArgumentException("Chung requires normalized nonempty composition");
      }
      conductivity = calculateConductivity(temperature, fluid.getDensity() / component.getMolarMass(),
          component.getMolarMass(), component.getTC(), component.getCriticalVolume(), component.getAcentricFactor(),
          component.getCv0(temperature), component.getDebyeDipoleMoment(), component.getViscosityCorrectionFactor());
      return conductivity;
    }
    double[] volumeRoots = new double[count];
    double[] criticalTemperatures = new double[count];
    double[] masses = new double[count];
    double[] dipolesSquared = new double[count];
    double[] associationRoots = new double[count];
    double cv = 0.0;
    double fractionSum = 0.0;
    double actualMolarMass = 0.0;
    for (int i = 0; i < count; i++) {
      ComponentInterface component = fluid.getComponent(i);
      double fraction = component.getx();
      if (!Double.isFinite(fraction) || fraction < 0.0) {
        throw new IllegalArgumentException("Chung requires finite nonnegative mole fractions");
      }
      if (fraction == 0.0) {
        continue;
      }
      requirePositive(component.getCriticalVolume(), "critical volume");
      requirePositive(component.getTC(), "critical temperature");
      requirePositive(component.getMolarMass(), "molar mass");
      double association = component.getViscosityCorrectionFactor();
      if (!Double.isFinite(association) || association < 0.0 || !Double.isFinite(component.getDebyeDipoleMoment())
          || !Double.isFinite(component.getAcentricFactor())) {
        throw new IllegalArgumentException("Chung requires finite transport parameters and nonnegative association");
      }
      volumeRoots[i] = Math.cbrt(component.getCriticalVolume());
      criticalTemperatures[i] = component.getTC();
      masses[i] = component.getMolarMass() * 1000.0;
      double dipole = component.getDebyeDipoleMoment();
      dipolesSquared[i] = dipole * dipole;
      associationRoots[i] = Math.sqrt(association);
      cv += fraction * component.getCv0(temperature);
      actualMolarMass += fraction * component.getMolarMass();
      fractionSum += fraction;
    }
    if (Math.abs(fractionSum - 1.0) > 1e-8) {
      throw new IllegalArgumentException("Chung requires normalized nonempty composition");
    }

    // Powers of 0.809 cancel in the conformal rules when cbrt(Vc) replaces sigma.
    double volume = 0.0;
    double temperatureNumerator = 0.0;
    double massNumerator = 0.0;
    double omegaNumerator = 0.0;
    double dipoleNumerator = 0.0;
    double association = 0.0;
    for (int i = 0; i < count; i++) {
      double xi = fluid.getComponent(i).getx();
      if (xi == 0.0) {
        continue;
      }
      for (int j = i; j < count; j++) {
        double xj = fluid.getComponent(j).getx();
        if (xj == 0.0) {
          continue;
        }
        double weight = xi * xj * (i == j ? 1.0 : 2.0);
        double diameterSquared = volumeRoots[i] * volumeRoots[j];
        double pairVolume = diameterSquared * Math.sqrt(diameterSquared);
        double pairTemperature = Math.sqrt(criticalTemperatures[i] * criticalTemperatures[j]);
        double pairMass = 2.0 * masses[i] * masses[j] / (masses[i] + masses[j]);
        volume += weight * pairVolume;
        temperatureNumerator += weight * pairVolume * pairTemperature;
        massNumerator += weight * diameterSquared * pairTemperature * Math.sqrt(pairMass);
        omegaNumerator += weight * pairVolume * 0.5
            * (fluid.getComponent(i).getAcentricFactor() + fluid.getComponent(j).getAcentricFactor());
        dipoleNumerator += weight * dipolesSquared[i] * dipolesSquared[j] / pairVolume;
        association += weight * associationRoots[i] * associationRoots[j];
      }
    }
    double tc = temperatureNumerator / volume;
    double effectiveMassRoot = massNumerator / (tc * Math.pow(volume, 2.0 / 3.0));
    double effectiveMolarMass = effectiveMassRoot * effectiveMassRoot / 1000.0;
    double dipole = Math.sqrt(Math.sqrt(volume * dipoleNumerator));
    // Actual molar mass converts EOS mass density; conformal mass belongs only in the transport kernel.
    double molarDensity = fluid.getDensity() / actualMolarMass;
    conductivity = calculateConductivity(temperature, molarDensity, effectiveMolarMass, tc, volume,
        omegaNumerator / volume, cv, dipole, association);
    return conductivity;
  }

  /**
   * Evaluates the pure-fluid Chung equation, also used with conformal mixture parameters.
   *
   * @param temperature temperature in K
   * @param molarDensity molar density in mol/m3; zero gives the analytic dilute limit
   * @param molarMass molar mass in kg/mol
   * @param criticalTemperature critical temperature in K
   * @param criticalVolume critical molar volume in cm3/mol
   * @param acentricFactor dimensionless acentric factor
   * @param idealGasCv ideal-gas molar Cv in J/(mol K)
   * @param dipoleMoment dipole moment in Debye
   * @param associationFactor dimensionless Chung transport association correction
   * @return conductivity in W/(m K)
   * @throws IllegalArgumentException for invalid inputs or the hard-sphere singular domain
   */
  public static double calculateConductivity(double temperature, double molarDensity, double molarMass,
      double criticalTemperature, double criticalVolume, double acentricFactor, double idealGasCv, double dipoleMoment,
      double associationFactor) {
    requirePositive(temperature, "temperature");
    requirePositive(molarMass, "molar mass");
    requirePositive(criticalTemperature, "critical temperature");
    requirePositive(criticalVolume, "critical volume");
    requirePositive(idealGasCv, "ideal-gas Cv");
    if (!Double.isFinite(molarDensity) || molarDensity < 0.0 || !Double.isFinite(acentricFactor)
        || !Double.isFinite(dipoleMoment) || !Double.isFinite(associationFactor) || associationFactor < 0.0) {
      throw new IllegalArgumentException("Invalid Chung density or transport parameter");
    }
    double tr = temperature / criticalTemperature;
    double tStar = 1.2593 * tr;
    if (tStar < 0.3 || tStar > 100.0) {
      throw new IllegalArgumentException("Chung collision integral requires 0.3 <= T* <= 100");
    }
    double y = molarDensity * 1e-6 * criticalVolume / 6.0;
    if (y >= 1.0) {
      throw new IllegalArgumentException("Chung packing fraction must be below one");
    }
    double reducedDipole = 131.3 * dipoleMoment / Math.sqrt(criticalVolume * criticalTemperature);
    double dipole4 = reducedDipole * reducedDipole * reducedDipole * reducedDipole;
    double fc = 1.0 - 0.2756 * acentricFactor + 0.059035 * dipole4 + associationFactor;
    double collisionIntegral = 1.16145 * Math.pow(tStar, -0.14874) + 0.52487 * Math.exp(-0.77320 * tStar)
        + 2.16178 * Math.exp(-2.43787 * tStar);
    double volumePower = Math.pow(criticalVolume, 2.0 / 3.0);
    double diluteViscosity = 40.785e-7 * fc * Math.sqrt(1000.0 * molarMass * temperature)
        / (volumePower * collisionIntegral);
    double alpha = idealGasCv / R - 1.5;
    double beta = 0.7862 - 0.7109 * acentricFactor + 1.3168 * acentricFactor * acentricFactor;
    double z = 2.0 + 10.5 * tr * tr;
    double psi = 1.0
        + alpha * (0.215 + 0.28288 * alpha - 1.061 * beta + 0.26665 * z) / (0.6366 + beta * z + 1.061 * alpha * beta);
    double[] b = new double[7];
    for (int i = 0; i < b.length; i++) {
      b[i] = COEFFICIENTS[i][0] + COEFFICIENTS[i][1] * acentricFactor + COEFFICIENTS[i][2] * dipole4
          + COEFFICIENTS[i][3] * associationFactor;
    }
    double g1 = (1.0 - 0.5 * y) / Math.pow(1.0 - y, 3.0);
    // expm1 avoids cancellation at low density, with the analytic value at zero density.
    double exponentialRatio = y == 0.0 ? b[3] : -Math.expm1(-b[3] * y) / y;
    double h2 = (b[0] * exponentialRatio + b[1] * g1 * Math.exp(b[4] * y) + b[2] * g1) / (b[0] * b[3] + b[1] + b[2]);
    requirePositive(h2, "Chung H2");
    double q = 3.586e-3 * Math.sqrt(criticalTemperature / molarMass) / volumePower;
    double result = 31.2 * diluteViscosity * psi / molarMass * (1.0 / h2 + b[5] * y)
        + q * b[6] * y * y * Math.sqrt(tr) * h2;
    requirePositive(result, "conductivity");
    return result;
  }

  /**
   * Rejects nonphysical scalar inputs instead of clipping a correlation result.
   *
   * @param value input value
   * @param name parameter name for diagnostics
   * @throws IllegalArgumentException when the input is not finite and positive
   */
  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException("Chung requires finite positive " + name);
    }
  }
}
