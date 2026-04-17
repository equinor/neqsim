package neqsim.physicalproperties.interfaceproperties.surfacetension;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentEos;
import neqsim.thermo.system.SystemInterface;

/**
 * Classical Density Functional Theory (cDFT) for interfacial tension calculation.
 *
 * <p>
 * Computes the interfacial tension of a pure component by minimising the excess grand potential
 * across a planar vapour-liquid interface using a non-local density functional derived directly
 * from the cubic equation of state (PR or SRK). Unlike gradient theory, the surface tension emerges
 * from the non-local treatment of attractive interactions without requiring an empirical influence
 * parameter.
 * </p>
 *
 * <p>
 * The approach splits the EOS Helmholtz free energy into:
 * </p>
 * <ul>
 * <li>A local repulsive part (ideal gas + excluded volume from the EOS)</li>
 * <li>A non-local attractive part: the van der Waals mean-field attraction is replaced by a
 * convolution with a molecular-range step-function kernel, while the EOS-specific correction
 * (beyond simple mean-field) is treated locally.</li>
 * </ul>
 *
 * <p>
 * The density profile is parameterised as a tanh function whose width is optimised variationally to
 * minimise the surface tension. This avoids unstable Picard iteration and guarantees a physically
 * bounded density profile.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Tarazona, P. (1985). "Free-energy density functional for hard spheres." Phys. Rev. A, 31,
 * 2672.</li>
 * <li>Kahl, H.; Winkelmann, J. (2008). Fluid Phase Equilibria, 270, 50-61.</li>
 * <li>Gross, J. (2009). J. Chem. Phys., 131, 204705.</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 3.0
 */
public class CDFTSurfaceTension extends SurfaceTension {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(CDFTSurfaceTension.class);

  /** Universal gas constant R in J/(mol K). */
  private static final double R = ThermodynamicConstantsInterface.R;

  /** Avogadro's number. */
  private static final double NA = ThermodynamicConstantsInterface.avagadroNumber;

  /** Number of grid points for the density profile. */
  private int nGrid = 2048;

  /** Half-width of the computational domain in units of molecular diameter. */
  private double domainHalfWidthInD = 15.0;

  /**
   * Constructor for CDFTSurfaceTension.
   */
  public CDFTSurfaceTension() {}

  /**
   * Constructor for CDFTSurfaceTension.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public CDFTSurfaceTension(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public double calcSurfaceTension(int interface1, int interface2) {
    if (system.getPhase(0).getNumberOfComponents() != 1) {
      logger.warn("cDFT currently supports only pure components. "
          + "Falling back to parachor for mixtures.");
      ParachorSurfaceTension fallback = new ParachorSurfaceTension(system);
      return fallback.calcSurfaceTension(interface1, interface2);
    }
    try {
      return solvePureComponent(interface1, interface2);
    } catch (Exception ex) {
      logger.error("cDFT failed: " + ex.getMessage() + ". Falling back to parachor.");
      ParachorSurfaceTension fallback = new ParachorSurfaceTension(system);
      return fallback.calcSurfaceTension(interface1, interface2);
    }
  }

  /**
   * Solves the cDFT equations for a pure-component planar interface using a variational approach
   * with a tanh density profile.
   *
   * <p>
   * The density profile is parameterised as rho(z) = (rhoL+rhoV)/2 + (rhoL-rhoV)/2 * tanh(-z/d)
   * where d is an interface half-width parameter. The surface tension is computed as the integral
   * of the excess grand potential and minimised over d using golden section search.
   * </p>
   *
   * @param ph1 index of phase 1
   * @param ph2 index of phase 2
   * @return interfacial tension in N/m
   */
  private double solvePureComponent(int ph1, int ph2) {
    SystemInterface localSys = system.clone();
    double temperature = localSys.getPhase(0).getTemperature();

    // NeqSim getMolarVolume() returns in internal units; multiply by 1e-5 for m3/mol
    double molarVolLiq = localSys.getPhase(ph2).getMolarVolume() * 1.0e-5;
    double molarVolVap = localSys.getPhase(ph1).getMolarVolume() * 1.0e-5;

    // Molar densities in mol/m3
    double rhoLiq = 1.0 / molarVolLiq;
    double rhoVap = 1.0 / molarVolVap;

    // Ensure rhoLiq > rhoVap
    if (rhoLiq < rhoVap) {
      double tmp = rhoLiq;
      rhoLiq = rhoVap;
      rhoVap = tmp;
    }

    // Extract EOS parameters a(T) and b
    ComponentEos comp = (ComponentEos) localSys.getPhase(ph2).getComponent(0);
    double aEos = comp.getaT();
    double bEos = comp.getb();

    // Convert to SI: a [Pa m6/mol2], b [m3/mol]
    // NeqSim uses hybrid units (R in Pa m3/(mol K), Pc in bar) => divide by 1e5
    double aSI = aEos * 1.0e-5;
    double bSI = bEos * 1.0e-5;

    // Determine EOS type: PR or SRK
    double delta1;
    double delta2;
    String className = localSys.getClass().getSimpleName();
    if (className.contains("Srk") || className.contains("SRK")) {
      delta1 = 1.0;
      delta2 = 0.0;
    } else {
      delta1 = 1.0 + Math.sqrt(2.0);
      delta2 = 1.0 - Math.sqrt(2.0);
    }

    // Molecular diameter from covolume (for kernel width)
    double dMol = Math.pow(6.0 * bSI / (Math.PI * NA), 1.0 / 3.0);

    // Equilibrium chemical potential from full EOS
    double muRepExLiq = muRepExcess(rhoLiq, temperature, bSI);
    double muAttLiq = muAtt(rhoLiq, aSI, bSI, delta1, delta2);
    double muEq = R * temperature * Math.log(rhoLiq) + muRepExLiq + muAttLiq;

    // Bulk pressure from full EOS
    double pBulk = pressure(rhoLiq, temperature, aSI, bSI, delta1, delta2);

    // Variational optimisation: find interface width delta that minimises sigma
    double deltaLo = 0.3 * dMol;
    double deltaHi = 30.0 * dMol;

    // Phase 1: Coarse scan to find approximate minimum
    int nScan = 80;
    double bestDelta = dMol;
    double bestSigma = Double.MAX_VALUE;
    for (int k = 0; k < nScan; k++) {
      double delta = deltaLo + (deltaHi - deltaLo) * k / (nScan - 1);
      double sig = computeSigmaForDelta(rhoLiq, rhoVap, temperature, aSI, bSI, delta1, delta2, dMol,
          delta, muEq, pBulk);
      if (sig < bestSigma) {
        bestSigma = sig;
        bestDelta = delta;
      }
    }

    // Phase 2: Golden section refinement around bestDelta
    double refLo = Math.max(deltaLo, bestDelta - 3.0 * dMol);
    double refHi = Math.min(deltaHi, bestDelta + 3.0 * dMol);
    double gr = (Math.sqrt(5.0) + 1.0) / 2.0;

    for (int gs = 0; gs < 60; gs++) {
      double c = refHi - (refHi - refLo) / gr;
      double d = refLo + (refHi - refLo) / gr;
      double fc = computeSigmaForDelta(rhoLiq, rhoVap, temperature, aSI, bSI, delta1, delta2, dMol,
          c, muEq, pBulk);
      double fd = computeSigmaForDelta(rhoLiq, rhoVap, temperature, aSI, bSI, delta1, delta2, dMol,
          d, muEq, pBulk);
      if (fc < fd) {
        refHi = d;
      } else {
        refLo = c;
      }
    }

    double optDelta = 0.5 * (refLo + refHi);
    double sigma = computeSigmaForDelta(rhoLiq, rhoVap, temperature, aSI, bSI, delta1, delta2, dMol,
        optDelta, muEq, pBulk);

    logger.debug("cDFT: T={} K, rhoLiq={}, rhoVap={}, optDelta/dMol={}, sigma={} mN/m", temperature,
        rhoLiq, rhoVap, optDelta / dMol, sigma * 1000.0);

    return Math.max(sigma, 0.0);
  }

  /**
   * Computes the interfacial tension for a given tanh profile width.
   *
   * <p>
   * Uses rho(z) = (rhoL+rhoV)/2 + (rhoL-rhoV)/2 * tanh(-z/delta) and integrates the excess grand
   * potential density across the interface.
   * </p>
   *
   * @param rhoLiq liquid molar density (mol/m3)
   * @param rhoVap vapor molar density (mol/m3)
   * @param temp temperature (K)
   * @param a EOS energy parameter (Pa m6/mol2)
   * @param b covolume (m3/mol)
   * @param d1 EOS constant delta1
   * @param d2 EOS constant delta2
   * @param dMol molecular diameter (m)
   * @param delta interface half-width parameter (m)
   * @param muEq equilibrium chemical potential (J/mol)
   * @param pBulk equilibrium pressure (Pa)
   * @return interfacial tension (N/m)
   */
  private double computeSigmaForDelta(double rhoLiq, double rhoVap, double temp, double a, double b,
      double d1, double d2, double dMol, double delta, double muEq, double pBulk) {

    double halfWidth = domainHalfWidthInD * dMol;
    double dz = 2.0 * halfWidth / (nGrid - 1);
    int nKernHalf = (int) Math.ceil(0.5 * dMol / dz);
    // Normalise so that the discrete kernel integrates to exactly -2*a
    int nKernPoints = 2 * nKernHalf + 1;
    double wVal = -2.0 * a / (nKernPoints * dz);

    double rhoAvg = 0.5 * (rhoLiq + rhoVap);
    double dRho = rhoLiq - rhoVap;

    // Build tanh density profile
    double[] rho = new double[nGrid];
    for (int i = 0; i < nGrid; i++) {
      double z = -halfWidth + i * dz;
      rho[i] = rhoAvg + 0.5 * dRho * Math.tanh(-z / delta);
    }

    // Compute convolution W(z)
    double[] wConv = stepConvolve(rho, nGrid, nKernHalf, wVal, dz);

    // Integrate sigma = integral of [f_total - muEq*rho + pBulk] dz
    double sigma = 0.0;
    for (int i = 0; i < nGrid; i++) {
      double ri = rho[i];
      if (ri < 1.0e-10) {
        ri = 1.0e-10;
      }
      if (b * ri >= 0.999) {
        ri = 0.999 / b;
      }

      // f_rep = rho * RT * [ln(rho) - 1 - ln(1 - b*rho)]
      double fRep = ri * R * temp * (Math.log(ri) - 1.0 - Math.log(1.0 - b * ri));

      // f_att_nonlocal = 0.5 * rho * W(z)
      double fAttNL = 0.5 * ri * wConv[i];

      // f_corr = f_att_EOS(rho) + a*rho^2 (PR/SRK correction beyond vdW mean-field)
      double fAttEos = fAttLocal(ri, a, b, d1, d2);
      double fCorr = fAttEos + a * ri * ri;

      double fTotal = fRep + fAttNL + fCorr;
      sigma += (fTotal - muEq * ri + pBulk) * dz;
    }

    return sigma;
  }

  /**
   * Step-function convolution: W(z_i) = sum_{k=-nKernHalf}^{nKernHalf} wVal * rho(i+k) * dz.
   *
   * @param rho density profile
   * @param n grid size
   * @param nk half-width of kernel in grid points
   * @param wv kernel constant value
   * @param dz grid spacing
   * @return convolution result
   */
  private double[] stepConvolve(double[] rho, int n, int nk, double wv, double dz) {
    double[] result = new double[n];
    for (int i = 0; i < n; i++) {
      double sum = 0.0;
      for (int k = -nk; k <= nk; k++) {
        int j = i + k;
        if (j < 0) {
          j = 0;
        }
        if (j >= n) {
          j = n - 1;
        }
        sum += rho[j];
      }
      result[i] = wv * sum * dz;
    }
    return result;
  }

  /**
   * Repulsive excess chemical potential per mole (ideal excluded volume). mu_rep_ex = RT * [-ln(1 -
   * b*rho) + b*rho/(1 - b*rho)]
   *
   * @param rhoM molar density (mol/m3)
   * @param t temperature (K)
   * @param b covolume (m3/mol)
   * @return excess chemical potential (J/mol)
   */
  private double muRepExcess(double rhoM, double t, double b) {
    double bRho = b * rhoM;
    if (bRho >= 0.999) {
      bRho = 0.999;
    }
    return R * t * (-Math.log(1.0 - bRho) + bRho / (1.0 - bRho));
  }

  /**
   * Attractive chemical potential per mole from the EOS. For general cubic EOS with P = RT/(V-b) -
   * a/((V+d1*b)(V+d2*b)): mu_att = a/((d1-d2)*b) * ln((1+d2*b*rho)/(1+d1*b*rho)) - a*rho /
   * ((1+d1*b*rho)(1+d2*b*rho))
   *
   * @param rhoM molar density (mol/m3)
   * @param a EOS energy parameter (Pa m6/mol2)
   * @param b covolume (m3/mol)
   * @param d1 EOS constant delta1
   * @param d2 EOS constant delta2
   * @return attractive chemical potential (J/mol)
   */
  private double muAtt(double rhoM, double a, double b, double d1, double d2) {
    double u = b * rhoM;
    double denom1 = 1.0 + d1 * u;
    double denom2 = 1.0 + d2 * u;
    double logTerm = a / ((d1 - d2) * b) * Math.log(denom2 / denom1);
    double fracTerm = -a * rhoM / (denom1 * denom2);
    return logTerm + fracTerm;
  }

  /**
   * Attractive Helmholtz free energy density from the EOS. f_att = rho * A_m_att where A_m_att =
   * a/((d1-d2)*b) * ln((1+d2*b*rho)/(1+d1*b*rho))
   *
   * @param rhoM molar density (mol/m3)
   * @param a EOS energy parameter (Pa m6/mol2)
   * @param b covolume (m3/mol)
   * @param d1 EOS constant delta1
   * @param d2 EOS constant delta2
   * @return attractive free energy density (J/m3)
   */
  private double fAttLocal(double rhoM, double a, double b, double d1, double d2) {
    double u = b * rhoM;
    double denom1 = 1.0 + d1 * u;
    double denom2 = 1.0 + d2 * u;
    return rhoM * a / ((d1 - d2) * b) * Math.log(denom2 / denom1);
  }

  /**
   * Equation of state pressure.
   *
   * @param rhoM molar density (mol/m3)
   * @param t temperature (K)
   * @param a EOS energy parameter (Pa m6/mol2)
   * @param b covolume (m3/mol)
   * @param d1 EOS constant delta1
   * @param d2 EOS constant delta2
   * @return pressure (Pa)
   */
  private double pressure(double rhoM, double t, double a, double b, double d1, double d2) {
    double bRho = b * rhoM;
    double pRep = R * t * rhoM / (1.0 - bRho);
    double denom = (1.0 + d1 * bRho) * (1.0 + d2 * bRho);
    double pAtt = -a * rhoM * rhoM / denom;
    return pRep + pAtt;
  }

  /**
   * Sets the number of grid points.
   *
   * @param nGrid number of grid points
   */
  public void setNGrid(int nGrid) {
    this.nGrid = nGrid;
  }

  /**
   * Gets the number of grid points.
   *
   * @return number of grid points
   */
  public int getNGrid() {
    return this.nGrid;
  }
}
