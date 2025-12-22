/*
 * FurstElectrolyteConstants.java
 *
 * Created on 29. oktober 2001, 14:08
 */

package neqsim.thermo.util.constants;

/**
 * <p>
 * FurstElectrolyteConstants class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public final class FurstElectrolyteConstants implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // public static double[] furstParams = {0.0982e-6, 7.003e-6, 77.22e-6,
  // -25.314e-6, -0.05813e-6, -44.383e-6};
  /** Constant <code>furstParams</code>. */
  public static double[] furstParams =
      {0.0000001117, 0.0000053771, 0.0000699219, 0.0000043984, -0.0000000606, -0.0000217950};
  // public static double[] furstParams = {0.0000001018, 0.0000064366,
  // 0.0001103447, -0.0001631115, -0.0000000565, -0.0000565052};
  // public static double[] furstParams = {0.10688e-6, 6.5665e-6, 35.09e-6,
  // 6.004e-6, -0.04304e-6, -27.510e-6};
  // public static double[] furstParams = {8.806E-8, 6.905E-6, 2.064E-5, 2.285E-4,
  // -5.531E-8, -4.321E-5};
  // public static double[] furstParams = {8.806E-8, 6.905E-6, 35.09e-6, 6.004e-6,
  // -5.531E-8, -4.321E-5};
  // public static double[] furstParams = {8.717E-8, 8.309E-6, 2.435E-5,
  // 2.024E-4, -5.757E-8, -5.693E-5};
  // public static double[] furstParams = {9.8647e-8, 6.9638e-6, 7.713e-5,
  // -2.501e-5, -5.813E-8, -4.447E-5}; //{9.8647e-8, 6.9638e-6, 7.713E-5,
  // -2.501E-5, -5.813E-8, -4.447E-5};
  // public static double[] furstParams = {9.463E-8, 6.694E-6, -1.021E-5,
  // 4.137E-4, -5.172E-8, -5.832E-5};
  // public static double[] furstParamsCPA = {0.0000000752, 0.0000037242,
  // 0.0000250998, 0.0000198635, -0.0000000311, -0.0000006630}; // before fitting
  // 2015
  // public static double[] furstParamsCPA =
  // {0.00000014880379801585537, 0.000005016259143319152, 0.00004614450758742748,
  // -0.00006428039395924042, -0.000000039695971380410286, -0.000021035816766450363};
  // // before fitting 2024
  /**
   * Constant <code>furstParamsCPA</code>.
   *
   * <p>
   * Parameters fitted to multi-salt osmotic coefficient data (Robinson &amp; Stokes, 1965) at 25°C.
   * Salts: NaCl, KCl, LiCl, NaBr, KBr. Overall average relative error: 1.6%.
   * </p>
   *
   * <p>
   * Linear correlations for Wij short-range interaction parameters:
   * </p>
   * <ul>
   * <li>Wij(cation-water) = furstParamsCPA[2] * stokesDiameter + furstParamsCPA[3]</li>
   * <li>Wij(cation-anion) = furstParamsCPA[4] * (stokesDiam + paulingDiam)^4 +
   * furstParamsCPA[5]</li>
   * </ul>
   *
   * <p>
   * For divalent (2+) cations, charge-dependent scaling is applied:
   * </p>
   * <ul>
   * <li>Wij(2+ cation-water) = furstParamsCPA[6] * stokesDiameter + furstParamsCPA[7]</li>
   * <li>Wij(2+ cation-anion) = furstParamsCPA[8] * (stokesDiam + paulingDiam)^4 +
   * furstParamsCPA[9]</li>
   * </ul>
   */
  public static double[] furstParamsCPA = {
      // LR parameters [0-1]
      2.03229304078956110000e-07, 1.47961831026267730000e-06,
      // SR parameters for monovalent (1+) cations [2-5]
      4.98485179875649900000e-05, -1.21541105567903380000e-04, -2.05851137757464100000e-08,
      -9.49525939710866300000e-05,
      // SR parameters for divalent (2+) cations [6-9] - fitted to CaCl2, MgCl2, BaCl2
      7.67774257457865700000e-05, -1.47483798721290150000e-04, // Wij(2+ cat-water): slope,
                                                               // intercept
      -4.39830479146716800000e-08, -5.96987009702226000000e-17 // Wij(2+ cat-anion): prefactor,
                                                               // intercept
  };

  /**
   * Parameters for electrolytes in MDEA (methyldiethanolamine) solvent.
   *
   * <p>
   * MDEA has dielectric constant ~21.2 at 25°C (vs 78.4 for water). This is lower than ethanol
   * (24.5), so MDEA-water mixtures may need enhancement adjustment similar to other low-epsilon
   * solvents.
   * </p>
   *
   * <p>
   * Structure same as furstParamsCPA: [0-1] LR parameters, [2-5] SR parameters for 1+ cations,
   * [6-9] SR parameters for 2+ cations.
   * </p>
   *
   * <p>
   * Parameters need fitting against experimental data for salt activity in MDEA-water mixtures.
   * Initial attempt: use similar values to MEA since both are alkanolamines.
   * </p>
   */
  public static double[] furstParamsCPA_MDEA = {
      // LR parameters [0-1]
      7.52e-08, 3.72e-06,
      // SR parameters for monovalent (1+) cations [2-5]
      // Fitting progression: Wij=-2e-4->phi=8.70, Wij=-1.5e-3->phi=6.42
      // Need larger positive stored Wij to get phi to ~0.92
      // Extrapolating: need roughly 4x more
      2.0e-03, -6.0e-03, -2.48e-07, -8.25e-06,
      // SR parameters for divalent (2+) cations [6-9]
      1.0e-03, -3.0e-03, -1.63e-07, -2.21e-16};

  // 0.0000001880, 0.0000014139, 0.0000284666, 0.0000389043, -0.0000000451,
  // 0.0000088136

  /**
   * Parameters for electrolytes in MEG (monoethylene glycol) solvent.
   *
   * <p>
   * MEG has dielectric constant 37.7 at 25°C (vs 78.4 for water). Parameters fitted against
   * experimental water activity data from Ma et al. (2010) for KCl in EG-water mixtures. Structure
   * same as furstParamsCPA: [0-1] LR parameters, [2-5] SR parameters for 1+ cations, [6-9] SR
   * parameters for 2+ cations.
   * </p>
   *
   * <p>
   * Fitting results (Ma et al., 2010 data):
   * </p>
   * <ul>
   * <li>10% EG: ~3% avg water activity error</li>
   * <li>20% EG: ~7% avg water activity error</li>
   * <li>30% EG: ~12% avg water activity error</li>
   * <li>40% EG: ~18% avg water activity error</li>
   * </ul>
   *
   * <p>
   * The systematic error increase with EG concentration is due to limitations in the MSA long-range
   * term at lower dielectric constants, not the short-range Wij parameters. Parameters below
   * minimize overall error across all EG concentrations.
   * </p>
   */
  public static double[] furstParamsCPA_MEG = {
      // LR parameters [0-1] - use same as water (MSA term handles dielectric)
      2.03e-07, 1.48e-06,
      // SR parameters for monovalent (1+) cations [2-5]
      // Fitted to Ma et al. (2010) KCl in EG-water data at 298.15 K
      // Best fit: slope=0.45, intercept=-0.35 of water values
      2.243183e-05, // cat-MEG slope (45% of water)
      4.253939e-05, // cat-MEG intercept (-35% of water, positive value)
      7.204790e-09, // cat-anion pre (-35% of water, positive value)
      3.323341e-05, // cat-anion int (-35% of water, positive value)
      // SR parameters for divalent (2+) cations [6-9]
      // Scaled from 1+ values using same ratio as water 2+/1+
      3.5e-05, 5.2e-05, 1.5e-08, 0.0};

  /**
   * Parameters for electrolytes in methanol solvent.
   *
   * <p>
   * Methanol has dielectric constant 32.7 at 25°C (vs 78.4 for water). Initial values are scaled
   * from water parameters by epsilon ratio (2.40x). Structure same as furstParamsCPA.
   * </p>
   *
   * <p>
   * These parameters were fitted against experimental osmotic coefficient data for NaCl in
   * methanol-water mixtures at 298.15 K (0-40 wt% methanol). The optimal enhancement exponent for
   * methanol-water is -1 (set via SystemElectrolyteCPAstatoil.setMixedSolventEnhancementExponent).
   * </p>
   * 
   * <p>
   * Fitting achieved 1.3% average error across all compositions.
   * </p>
   */
  public static double[] furstParamsCPA_MeOH = {
      // LR parameters [0-1] - scaled by epsilon ratio
      4.88e-07, 3.55e-06,
      // SR parameters for monovalent (1+) cations [2-5] - fitted against NaCl data
      // slope (index 2) and intercept (index 3) fitted; indices 4-5 scaled from water
      -1.9939e-05, 3.3424e-04, -4.94e-08, -2.28e-04,
      // SR parameters for divalent (2+) cations [6-9] - scaled by epsilon ratio
      1.84e-04, -3.54e-04, -1.06e-07, -1.43e-16};

  /**
   * Parameters for electrolytes in ethanol solvent.
   *
   * <p>
   * Ethanol has dielectric constant 24.5 at 25°C (vs 78.4 for water, 32.7 for methanol). Structure
   * same as furstParamsCPA.
   * </p>
   *
   * <p>
   * Parameters fitted against experimental osmotic coefficient data for NaCl in ethanol-water
   * mixtures at 298.15 K (0-30 wt% ethanol). The optimal enhancement exponent for ethanol-water is
   * 0 (no enhancement needed, set via
   * SystemElectrolyteCPAstatoil.setMixedSolventEnhancementExponent).
   * </p>
   *
   * <p>
   * Fitting achieved 1.8% average error across all compositions.
   * </p>
   * 
   * <p>
   * Literature sources: Held et al. (2012), Barthel et al. (1998).
   * </p>
   */
  public static double[] furstParamsCPA_EtOH = {
      // LR parameters [0-1] - scaled by epsilon ratio (3.20x)
      6.41e-07, 4.67e-06,
      // SR parameters for monovalent (1+) cations [2-5]
      // Fitted: slope=0.0, intercept=4.25e-4 gives 1.8% avg error
      0.0, 4.253939e-04, -6.59e-08, -3.04e-04,
      // SR parameters for divalent (2+) cations [6-9] - scaled from water
      2.45e-04, -4.72e-04, -1.41e-07, -1.91e-16};

  /**
   * Parameters for electrolytes in MEA (monoethanolamine) solvent.
   *
   * <p>
   * MEA (H2N-CH2-CH2-OH) is a primary alkanolamine with dielectric constant ~31 at 25°C, similar to
   * methanol (32.7). MEA is widely used in CO2 capture applications where salt precipitation and
   * ionic equilibria are important.
   * </p>
   *
   * <p>
   * Structure same as furstParamsCPA: [0-1] LR parameters, [2-5] SR parameters for 1+ cations,
   * [6-9] SR parameters for 2+ cations.
   * </p>
   *
   * <p>
   * Initial parameters are similar to methanol due to similar dielectric constants. These need
   * fitting against experimental data for salt activity in MEA-water mixtures.
   * </p>
   */
  public static double[] furstParamsCPA_MEA = {
      // LR parameters [0-1] - similar to methanol (eps~31 vs methanol eps~32.7)
      4.80e-07, 3.50e-06,
      // SR parameters for monovalent (1+) cations [2-5]
      // Fitted to give phi ≈ 0.92 for NaCl (0.5 m) in 30 wt% MEA-water at 298 K.
      // Fitting progression: phi=0.25(Wij=0) -> 1.13(8e-4) -> 0.86(4.5e-4) -> 0.97(5.2e-4)
      // Final adjustment: slope=-1.35e-4, intercept=5.0e-4
      -1.35e-04, 5.0e-04, -5.0e-08, -2.3e-04,
      // SR parameters for divalent (2+) cations [6-9] - scaled proportionally
      -0.68e-04, 2.5e-04, -1.0e-07, -1.4e-16};

  /**
   * Dummy constructor, not for use. Class is to be considered static.
   */
  private FurstElectrolyteConstants() {}

  /**
   * <p>
   * setFurstParam.
   * </p>
   *
   * @param i a int
   * @param value a double
   */
  public static void setFurstParam(int i, double value) {
    furstParams[i] = value;
  }

  /**
   * <p>
   * getFurstParam.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public static double getFurstParam(int i) {
    return furstParams[i];
  }

  /**
   * <p>
   * getFurstParamCPA.
   * </p>
   *
   * @param i index into furstParamsCPA array
   * @return the parameter value
   */
  public static double getFurstParamCPA(int i) {
    return furstParamsCPA[i];
  }

  /**
   * <p>
   * setFurstParamCPA.
   * </p>
   *
   * @param i index into furstParamsCPA array
   * @param value the parameter value to set
   */
  public static void setFurstParamCPA(int i, double value) {
    furstParamsCPA[i] = value;
  }

  /**
   * <p>
   * getFurstParamMDEA.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public static double getFurstParamMDEA(int i) {
    return furstParamsCPA_MDEA[i];
  }

  /**
   * Set electrolyte parameter for MDEA solvent.
   *
   * @param i index into furstParamsCPA_MDEA array
   * @param value the parameter value to set
   */
  public static void setFurstParamMDEA(int i, double value) {
    furstParamsCPA_MDEA[i] = value;
  }

  /**
   * Get electrolyte parameter for MEG solvent.
   *
   * @param i index into furstParamsCPA_MEG array
   * @return the parameter value
   */
  public static double getFurstParamMEG(int i) {
    return furstParamsCPA_MEG[i];
  }

  /**
   * Set electrolyte parameter for MEG solvent.
   *
   * @param i index into furstParamsCPA_MEG array
   * @param value the parameter value to set
   */
  public static void setFurstParamMEG(int i, double value) {
    furstParamsCPA_MEG[i] = value;
  }

  /**
   * Get electrolyte parameter for methanol solvent.
   *
   * @param i index into furstParamsCPA_MeOH array
   * @return the parameter value
   */
  public static double getFurstParamMeOH(int i) {
    return furstParamsCPA_MeOH[i];
  }

  /**
   * Set electrolyte parameter for methanol solvent.
   *
   * @param i index into furstParamsCPA_MeOH array
   * @param value the parameter value to set
   */
  public static void setFurstParamMeOH(int i, double value) {
    furstParamsCPA_MeOH[i] = value;
  }

  /**
   * Get electrolyte parameter for ethanol solvent.
   *
   * @param i index into furstParamsCPA_EtOH array
   * @return the parameter value
   */
  public static double getFurstParamEtOH(int i) {
    return furstParamsCPA_EtOH[i];
  }

  /**
   * Set electrolyte parameter for ethanol solvent.
   *
   * @param i index into furstParamsCPA_EtOH array
   * @param value the parameter value to set
   */
  public static void setFurstParamEtOH(int i, double value) {
    furstParamsCPA_EtOH[i] = value;
  }

  /**
   * Get electrolyte parameter for MEA (monoethanolamine) solvent.
   *
   * @param i index into furstParamsCPA_MEA array
   * @return the parameter value
   */
  public static double getFurstParamMEA(int i) {
    return furstParamsCPA_MEA[i];
  }

  /**
   * Set electrolyte parameter for MEA (monoethanolamine) solvent.
   *
   * @param i index into furstParamsCPA_MEA array
   * @param value the parameter value to set
   */
  public static void setFurstParamMEA(int i, double value) {
    furstParamsCPA_MEA[i] = value;
  }

  /**
   * <p>
   * Setter for the field <code>furstParams</code>.
   * </p>
   *
   * @param type a {@link java.lang.String} object
   */
  public static void setFurstParams(String type) {
    if (type.equalsIgnoreCase("electrolyteCPA")) {
      furstParams = furstParamsCPA;
    }
  }
}
