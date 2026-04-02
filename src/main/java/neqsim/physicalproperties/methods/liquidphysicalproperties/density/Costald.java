/*
 * Costald.java
 *
 * Created on 13. July 2022
 */

package neqsim.physicalproperties.methods.liquidphysicalproperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * COSTALD (Corresponding States Liquid Density) calculation for liquids.
 * </p>
 *
 * <p>
 * Implements the Hankinson-Thomson (1979) method for saturated liquid molar volume and the Aalto et
 * al. (1996) Tait-type correction for compressed (sub-cooled) liquids. The pseudocritical vapor
 * pressure uses the Lee-Kesler correlation.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Hankinson, R.W. and Thomson, G.H., AIChE J. 25, 653-663 (1979)</li>
 * <li>Thomson, G.H., Brobst, K.R. and Hankinson, R.W., AIChE J. 28, 671-676 (1982)</li>
 * <li>Aalto, M. et al., Fluid Phase Equil. 114, 1-19 (1996)</li>
 * <li>Poling, B.E. et al., The Properties of Gases and Liquids, 5th ed., McGraw-Hill (2001)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Costald extends LiquidPhysicalPropertyMethod implements DensityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Costald.class);

  /** Gas constant in cm3 bar / (mol K). */
  private static final double R_CM3_BAR = 83.14;

  /**
   * <p>
   * Constructor for Costald.
   * </p>
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Costald(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public Costald clone() {
    Costald properties = null;

    try {
      properties = (Costald) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /**
   * Calculates COSTALD dimensionless saturated volume function V_R^(0).
   *
   * <p>
   * V_R^(0) = 1 - 1.52816 tau^(1/3) + 1.43907 tau^(2/3) - 0.81446 tau + 0.190454 tau^(4/3) where
   * tau = 1 - Tr.
   * </p>
   *
   * @param reducedTemperature reduced temperature T/Tc
   * @return V_R^(0) dimensionless
   */
  private double calcVR0(double reducedTemperature) {
    double tau = 1.0 - reducedTemperature;
    if (tau <= 0.0) {
      return 1.0;
    }
    double tau13 = Math.pow(tau, 1.0 / 3.0);
    return 1.0 - 1.52816 * tau13 + 1.43907 * tau13 * tau13 - 0.81446 * tau + 0.190454 * tau13 * tau;
  }

  /**
   * Calculates COSTALD dimensionless departure volume function V_R^(delta).
   *
   * <p>
   * V_R^(delta) = (-0.296123 + 0.386914 Tr - 0.0427258 Tr^2 - 0.0480645 Tr^3) / (Tr - 1.00001)
   * </p>
   *
   * @param reducedTemperature reduced temperature T/Tc
   * @return V_R^(delta) dimensionless
   */
  private double calcVRdelta(double reducedTemperature) {
    double tr = reducedTemperature;
    double numer = -0.296123 + 0.386914 * tr - 0.0427258 * tr * tr - 0.0480645 * tr * tr * tr;
    double denom = tr - 1.00001;
    return numer / denom;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates liquid density using the COSTALD method. Returns density in kg/m3.
   * </p>
   */
  @Override
  public double calcDensity() {
    int nc = liquidPhase.getPhase().getNumberOfComponents();

    // --- Mixing rules (Hankinson and Thomson, 1979) ---
    // omega_m = sum(xi * omega_i) -- linear mixing
    double omegaMix = 0.0;

    // V*_m = 1/4 * [sum(xi*V*i) + 3 * sum(xi*V*i^(2/3)) * sum(xi*V*i^(1/3))]
    double sumVstar = 0.0;
    double sumVstar23 = 0.0;
    double sumVstar13 = 0.0;

    // Tc_m = [sum(xi * sqrt(Tci * V*i))]^2 / V*_m (Poling, Table 4-12)
    double sumTcV = 0.0;

    for (int i = 0; i < nc; i++) {
      double xi = liquidPhase.getPhase().getComponent(i).getx();
      double vstarI = getCharacteristicVolume(i);
      double tci = liquidPhase.getPhase().getComponent(i).getTC();
      double omegaI = liquidPhase.getPhase().getComponent(i).getAcentricFactor();

      omegaMix += xi * omegaI;
      sumVstar += xi * vstarI;
      sumVstar23 += xi * Math.pow(vstarI, 2.0 / 3.0);
      sumVstar13 += xi * Math.pow(vstarI, 1.0 / 3.0);
      sumTcV += xi * Math.sqrt(tci * vstarI);
    }

    double vstarMix = 0.25 * (sumVstar + 3.0 * sumVstar23 * sumVstar13);
    double tcMix = sumTcV * sumTcV / vstarMix;

    double temperature = liquidPhase.getPhase().getTemperature();
    double pressure = liquidPhase.getPhase().getPressure();
    double reducedTemperature = temperature / tcMix;

    if (reducedTemperature >= 1.0) {
      logger.warn("COSTALD: reduced temperature {} >= 1.0, method not valid above Tc",
          reducedTemperature);
      // Fallback to EOS-based density
      return 1.0 / liquidPhase.getPhase().getMolarVolume() * liquidPhase.getPhase().getMolarMass()
          * 1.0e5;
    }

    if (reducedTemperature < 0.25) {
      logger.warn(
          "COSTALD: reduced temperature {} < 0.25, extrapolating below validated range (0.25-0.95)",
          reducedTemperature);
    }

    // --- Saturated liquid molar volume (Hankinson-Thomson) ---
    double vr0 = calcVR0(reducedTemperature);
    double vrdelta = calcVRdelta(reducedTemperature);
    double vsSat = vstarMix * vr0 * (1.0 - omegaMix * vrdelta);

    if (vsSat <= 0.0) {
      logger.warn("COSTALD: saturated volume <= 0 at Tr={}, falling back to EOS density",
          reducedTemperature);
      return 1.0 / liquidPhase.getPhase().getMolarVolume() * liquidPhase.getPhase().getMolarMass()
          * 1.0e5;
    }

    // --- Compressed liquid correction (Aalto et al. 1996) ---
    double vLiquid = applyCompressedLiquidCorrection(vsSat, reducedTemperature, omegaMix, vstarMix,
        tcMix, pressure);

    // Convert cm3/mol to density kg/m3: rho = M [kg/mol] / (V [cm3/mol] * 1e-6 [m3/cm3])
    double molarMass = liquidPhase.getPhase().getMolarMass();
    return molarMass / (vLiquid * 1.0e-6);
  }

  /**
   * Applies the Aalto et al. (1996) compressed liquid correction.
   *
   * @param vsSat saturated liquid molar volume in cm3/mol
   * @param reducedTemperature reduced temperature T/Tc_m
   * @param omega acentric factor of mixture
   * @param vstarMix characteristic volume of mixture in cm3/mol
   * @param tcMix pseudocritical temperature of mixture in K
   * @param pressure system pressure in bar
   * @return corrected liquid molar volume in cm3/mol
   */
  private double applyCompressedLiquidCorrection(double vsSat, double reducedTemperature,
      double omega, double vstarMix, double tcMix, double pressure) {
    // Pseudocritical pressure: Pc = (0.291 - 0.080*omega) * R * Tc / V*
    double pcPseudo = (0.291 - 0.080 * omega) * R_CM3_BAR * tcMix / vstarMix;
    double prReduced = pressure / pcPseudo;

    // Saturated vapor pressure via Lee-Kesler correlation
    double tr = reducedTemperature;
    double f0 = 5.92714 - 6.09648 / tr - 1.28862 * Math.log(tr) + 0.169347 * Math.pow(tr, 6.0);
    double f1 = 15.2518 - 15.6875 / tr - 13.4721 * Math.log(tr) + 0.43577 * Math.pow(tr, 6.0);
    double prSat = Math.exp(f0 + omega * f1);

    // If pressure is below or at the estimated saturation pressure, return saturated volume
    if (prReduced <= prSat) {
      return vsSat;
    }

    // Aalto et al. (1996) constants
    double a0 = -170.335;
    double a1 = -28.578;
    double a2 = 124.809;
    double a3 = -55.5393;
    double a4 = 130.01;
    double b0 = 0.164813;
    double b1 = -0.0914427;
    double cConst = Math.E;
    double dConst = 1.00588;

    double bigA = a0 + a1 * tr + a2 * tr * tr * tr + a3 * Math.pow(tr, 6.0) + a4 / tr;
    double bigB = b0 + b1 * omega;

    double numerator = bigA + Math.pow(cConst, Math.pow(dConst - tr, bigB)) * (prReduced - prSat);
    double denominator = bigA + cConst * (prReduced - prSat);

    if (Math.abs(denominator) < 1.0e-30) {
      return vsSat;
    }

    double ratio = numerator / denominator;
    // Compressed liquid should always be denser than saturated liquid (ratio <= 1.0)
    if (ratio > 1.0) {
      ratio = 1.0;
    }
    // Safety clamp: ratio should not produce unreasonably dense liquid
    if (ratio < 0.5) {
      ratio = 0.5;
    }
    return vsSat * ratio;
  }

  /**
   * Gets the COSTALD characteristic volume V* for a component.
   *
   * <p>
   * Priority order:
   * </p>
   * <ol>
   * <li>Explicitly set V* via {@code setCostaldCharacteristicVolume()}</li>
   * <li>Back-calculated from normal liquid density at 60 deg F (288.71 K) using the
   * Hankinson-Thomson saturated volume equation solved inversely. This is used for TBP/plus
   * fractions and polar/associating compounds (water, glycols, alcohols) where the critical volume
   * is not a good estimate of V*. This is the standard approach in commercial simulators
   * (UniSim/HYSYS, PRO/II).</li>
   * <li>Fallback: critical volume Vc (for components without known liquid density, or when the
   * back-calculation gives a supercritical reduced temperature at standard conditions)</li>
   * </ol>
   *
   * @param compIdx component index in the phase
   * @return characteristic volume V* in cm3/mol
   */
  private double getCharacteristicVolume(int compIdx) {
    double vstar = liquidPhase.getPhase().getComponent(compIdx).getCostaldCharacteristicVolume();
    if (vstar > 0.0) {
      return vstar;
    }

    // Try to estimate V* from the known normal liquid density. This is essential for:
    // - TBP/plus fractions (pseudo-components)
    // - Polar/associating compounds (water, MEG, TEG, methanol, ethanol, etc.)
    // where Vc significantly overestimates V* due to strong intermolecular forces
    double estimatedVstar = estimateVstarFromDensity(compIdx);
    if (estimatedVstar > 0.0) {
      return estimatedVstar;
    }

    return liquidPhase.getPhase().getComponent(compIdx).getCriticalVolume();
  }

  /**
   * Estimates the COSTALD characteristic volume V* from the known normal liquid density at standard
   * conditions (60 deg F = 288.71 K = 15.56 deg C). This is the Hankinson-Thomson (1979) approach
   * for compounds where V* has not been explicitly set.
   *
   * <p>
   * This method is essential for three categories of components:
   * </p>
   * <ul>
   * <li>TBP/plus fraction pseudo-components (always have normalLiquidDensity from user input)</li>
   * <li>Polar/associating compounds (water, methanol, ethanol, MEG, TEG, DEG, etc.) where the
   * critical volume Vc significantly overestimates V* due to strong hydrogen bonding and
   * intermolecular association. Using the back-calculated V* from density inherently captures these
   * polar effects.</li>
   * <li>Any database component with a known standard liquid density (normalLiquidDensity &gt;
   * 0)</li>
   * </ul>
   *
   * <p>
   * The method solves: rho_std = M / (V* * VR0 * (1 - omega * VRdelta)) for V*, where VR0 and
   * VRdelta are evaluated at Tr = 288.71/Tc.
   * </p>
   *
   * @param compIdx component index in the phase
   * @return estimated V* in cm3/mol, or 0 if estimation fails (e.g., near-critical or supercritical
   *         at standard conditions with Tr &gt; 0.9, missing data)
   */
  private double estimateVstarFromDensity(int compIdx) {
    double normalDensity = liquidPhase.getPhase().getComponent(compIdx).getNormalLiquidDensity();
    if (normalDensity <= 0.0) {
      return 0.0;
    }

    double molarMass = liquidPhase.getPhase().getComponent(compIdx).getMolarMass(); // kg/mol
    double tc = liquidPhase.getPhase().getComponent(compIdx).getTC(); // K
    double omega = liquidPhase.getPhase().getComponent(compIdx).getAcentricFactor();

    if (tc <= 0.0 || molarMass <= 0.0) {
      return 0.0;
    }

    // Standard temperature: 60°F = 288.71 K
    double tStd = 288.71;
    double trStd = tStd / tc;

    if (trStd >= 0.9) {
      // Component is near-critical or supercritical at standard conditions.
      // For near-critical components (0.9 <= Tr < 1.0), the database LIQDENS may be at the
      // normal boiling point rather than at 288.71 K, making V* back-calculation unreliable.
      // Fall back to critical volume Vc.
      return 0.0;
    }

    double vr0 = calcVR0(trStd);
    double vrdelta = calcVRdelta(trStd);
    double dimensionlessFactor = vr0 * (1.0 - omega * vrdelta);

    if (Math.abs(dimensionlessFactor) < 1.0e-15) {
      return 0.0;
    }

    // normalDensity is in g/cm3, molarMass in kg/mol
    // V_molar_std [cm3/mol] = (molarMass [kg/mol] * 1000 [g/kg]) / normalDensity [g/cm3]
    double vMolarStd = molarMass * 1000.0 / normalDensity; // cm3/mol

    // V_molar_std = V* * dimensionlessFactor => V* = V_molar_std / dimensionlessFactor
    double vstarEstimated = vMolarStd / dimensionlessFactor;

    if (vstarEstimated <= 0.0) {
      return 0.0;
    }

    return vstarEstimated;
  }
}
