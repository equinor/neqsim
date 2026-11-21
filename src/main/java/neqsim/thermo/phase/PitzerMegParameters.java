package neqsim.thermo.phase;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.component.ComponentInterface;

/**
 * Neutral-solute Pitzer parameters for monoethylene glycol (MEG) in water and in aqueous NaCl.
 *
 * <p>
 * MEG is treated as a neutral solute on the molality scale. For MEG alone, the osmotic coefficient of water is $\phi =
 * 1 + \lambda m + 2\mu m^2$ and $\ln a_w = -M_w m \phi$, with $\lambda(T)$ and $\mu(T)$ in the PHREEQC six-term form
 * referenced to 298.15 K. The binary parameters were regressed by NeqSim to the freezing points (233-269 K) and the
 * 298.15 K isopiestic water activities of aqueous ethylene glycol reported by Tsurko et al. (2026), J. Chem. Eng. Data
 * 71, 942-959 (Tables 2 and 44). The NaCl term was regressed to the ternary water/NaCl/ethylene-glycol melting-point
 * equation of Benson et al. (2010), Cryobiology 61, 352-356, for EG:NaCl mass ratios 5-45 and total solute up to 30 wt%
 * (rms 0.70 K in freezing temperature). Only the sum of the MEG-Na+ and MEG-Cl- lambda terms is identifiable from
 * water-activity data of a single salt; by convention the full value is assigned to MEG-Na+ and the MEG-Cl- term is
 * zero.
 * </p>
 *
 * <p>
 * Qualified scope: MEG molality up to {@value #MAX_MEG_MOLALITY} mol/kg water (about 52 wt% salt-free), 233-298 K for
 * the binary and 248-273 K for the NaCl term (NaCl up to 1.22 mol/kg in the regression data), with Na+ and Cl- as the
 * only ions. Other cations (K+, Ca++, Mg++) have no MEG rows; the neutral coverage audit then fails with a diagnostic
 * rather than assuming zero. MEG together with methanol or another liquid co-solvent likewise fails closed. Dilute
 * molecular gases and hydrocarbons receive explicit zero rows with MEG, listed by
 * {@link PhasePitzer#getMegAssumedZeroRows()}, which reproduces their ideal-dilute treatment.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class PitzerMegParameters {
  /** Dataset identity. */
  public static final String DATASET_ID = "neqsim-pitzer-meg-nacl-v1";
  /** Upper MEG molality covered by the regression data, mol/kg water. */
  public static final double MAX_MEG_MOLALITY = 17.6;
  /** Reference temperature of the coefficient functions, K. */
  public static final double REFERENCE_TEMPERATURE_K = 298.15;

  /** lambda(MEG,MEG) PHREEQC six-term coefficients, kg/mol. */
  static final double[] LAMBDA_MEG_MEG = {3.04911030e-03, 7.18602813e+01, 0.0, 0.0, 0.0, 0.0};
  /** mu(MEG,MEG,MEG) PHREEQC six-term coefficients, kg2/mol2. */
  static final double[] MU_MEG_MEG_MEG = {-2.96617652e-04, -1.52913867e+00, 0.0, 0.0, 0.0, 0.0};
  /** lambda(MEG,Na+) PHREEQC six-term coefficients, kg/mol (carries the full NaCl salting term). */
  static final double[] LAMBDA_MEG_NA = {1.478121e-01, 0.0, 0.0, 0.0, 0.0, 0.0};
  /** lambda(MEG,Cl-) by convention zero. */
  static final double[] LAMBDA_MEG_CL = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
  /** zeta(MEG,Na+,Cl-) by convention zero; a fitted value did not extrapolate beyond the NaCl data range. */
  static final double[] ZETA_MEG_NA_CL = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};

  /** Normal boiling point below which an unparameterised neutral is treated as a dilute gas, K. */
  private static final double DILUTE_GAS_BOILING_POINT_K = 273.15;

  /** Utility class; not instantiated. */
  private PitzerMegParameters() {
  }

  /**
   * Tests whether a component is MEG.
   *
   * @param component component
   * @return {@code true} for MEG or ethylene glycol
   */
  public static boolean isMeg(ComponentInterface component) {
    String name = component.getComponentName();
    if ("MEG".equalsIgnoreCase(name) || "ethylene glycol".equalsIgnoreCase(name)) {
      return true;
    }
    return "ethyleneglycol".equalsIgnoreCase(name);
  }

  /**
   * Finds the MEG component index.
   *
   * @param phase Pitzer phase
   * @return component index, or -1 when MEG is absent
   */
  public static int megIndex(PhasePitzer phase) {
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      if (isMeg(phase.getComponent(i))) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Applies the MEG rows to a Pitzer phase that contains MEG. Does nothing when MEG is absent.
   *
   * @param phase aqueous Pitzer phase
   * @throws IllegalStateException when MEG is combined with a liquid co-solvent that has no MEG data
   */
  public static void apply(PhasePitzer phase) {
    int meg = megIndex(phase);
    if (meg < 0) {
      return;
    }
    double tref = REFERENCE_TEMPERATURE_K;
    phase.setLambdaTemperatureCoefficients(meg, meg, tref, LAMBDA_MEG_MEG.clone());
    phase.setMuTemperatureCoefficients(meg, meg, meg, tref, MU_MEG_MEG_MEG.clone());

    List<Integer> cations = new ArrayList<Integer>();
    List<Integer> anions = new ArrayList<Integer>();
    List<Integer> others = new ArrayList<Integer>();
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      ComponentInterface component = phase.getComponent(i);
      double charge = component.getIonicCharge();
      if (charge > 0.5) {
        cations.add(i);
      } else if (charge < -0.5) {
        anions.add(i);
      } else if (i != meg && phase.isNeutralSoluteInTopology(i)) {
        others.add(i);
      }
    }
    for (int cation : cations) {
      if ("Na+".equals(phase.getComponent(cation).getComponentName())) {
        phase.setLambdaTemperatureCoefficients(meg, cation, tref, LAMBDA_MEG_NA.clone());
        for (int anion : anions) {
          if ("Cl-".equals(phase.getComponent(anion).getComponentName())) {
            phase.setZetaTemperatureCoefficients(meg, cation, anion, tref, ZETA_MEG_NA_CL.clone());
          }
        }
      }
    }
    for (int anion : anions) {
      if ("Cl-".equals(phase.getComponent(anion).getComponentName())) {
        phase.setLambdaTemperatureCoefficients(meg, anion, tref, LAMBDA_MEG_CL.clone());
      }
    }

    List<String> assumed = new ArrayList<String>();
    for (int other : others) {
      ComponentInterface component = phase.getComponent(other);
      if (!isDiluteGasOrHydrocarbon(component)) {
        String message = "Pitzer MEG dataset " + DATASET_ID + " has no MEG data for " + component.getComponentName();
        throw new IllegalStateException(message + "; MEG combines only with dilute gases, hydrocarbons and NaCl");
      }
      String name = component.getComponentName();
      assumeZeroPair(phase, other, meg, "LAMBDA:[" + name + ",MEG]", assumed);
      assumeZeroPair(phase, other, other, "LAMBDA:[" + name + "," + name + "]", assumed);
      List<Integer> ions = new ArrayList<Integer>(cations);
      ions.addAll(anions);
      for (int ion : ions) {
        String label = "LAMBDA:[" + name + "," + phase.getComponent(ion).getComponentName() + "]";
        assumeZeroPair(phase, other, ion, label, assumed);
      }
      for (int cation : cations) {
        for (int anion : anions) {
          if (!phase.hasNeutralInteraction(PhasePitzer.NEUTRAL_FAMILY_ZETA, other, cation, anion)) {
            phase.setZeta(other, cation, anion, 0.0);
            String cationName = phase.getComponent(cation).getComponentName();
            String anionName = phase.getComponent(anion).getComponentName();
            assumed.add("ZETA:[" + name + "," + cationName + "," + anionName + "]");
          }
        }
      }
      for (int second : others) {
        String label = "LAMBDA:[" + name + "," + phase.getComponent(second).getComponentName() + "]";
        assumeZeroPair(phase, other, second, label, assumed);
      }
    }
    // The mu family is now active, so every neutral triple is audited. Only MEG-MEG-MEG carries data.
    List<Integer> neutrals = new ArrayList<Integer>(others);
    neutrals.add(meg);
    for (int a = 0; a < neutrals.size(); a++) {
      for (int b = a; b < neutrals.size(); b++) {
        for (int c = b; c < neutrals.size(); c++) {
          int i = neutrals.get(a);
          int j = neutrals.get(b);
          int k = neutrals.get(c);
          if (!phase.hasNeutralInteraction(PhasePitzer.NEUTRAL_FAMILY_MU, i, j, k)) {
            phase.setMu(i, j, k, 0.0);
            String firstName = phase.getComponent(i).getComponentName();
            String secondName = phase.getComponent(j).getComponentName();
            String thirdName = phase.getComponent(k).getComponentName();
            assumed.add("MU:[" + firstName + "," + secondName + "," + thirdName + "]");
          }
        }
      }
    }
    phase.setMegAssumedZeroRows(assumed);
    String id = phase.getParameterDatasetId();
    if (id == null || !id.contains(DATASET_ID)) {
      phase.setParameterDatasetId(id == null || id.trim().isEmpty() ? DATASET_ID : id + "+" + DATASET_ID);
    }
  }

  /**
   * Defines a zero lambda row when no dataset has defined it and records the assumption.
   *
   * @param phase Pitzer phase
   * @param first first component index
   * @param second second component index
   * @param label row description for the assumption list
   * @param assumed list receiving the descriptions of rows set to zero
   */
  private static void assumeZeroPair(PhasePitzer phase, int first, int second, String label, List<String> assumed) {
    if (!phase.hasNeutralInteraction(PhasePitzer.NEUTRAL_FAMILY_LAMBDA, first, second)) {
      phase.setLambda(first, second, 0.0);
      assumed.add(label);
    }
  }

  /**
   * Identifies neutral species whose interactions with MEG may be set to zero: dilute gases and hydrocarbons.
   *
   * @param component neutral component
   * @return {@code true} when a zero row reproduces the previous ideal-dilute treatment
   */
  static boolean isDiluteGasOrHydrocarbon(ComponentInterface component) {
    if (PitzerParameterDatasets.isHydrocarbonForAutomaticCatalog(component)) {
      return true;
    }
    double boilingPoint = component.getNormalBoilingPoint();
    return Double.isFinite(boilingPoint) && boilingPoint > 0.0 && boilingPoint < DILUTE_GAS_BOILING_POINT_K;
  }

  /**
   * Molality-scale Henry reference of MEG, bar kg/mol, from its pure-liquid vapour pressure.
   *
   * <p>
   * Uses the Raoult limit $H_m = M_w p^{sat}_{MEG}(T)$, i.e. a symmetric infinite-dilution activity coefficient of one.
   * This sets only the small MEG volatility into gas and oil; it does not enter the water activity.
   * </p>
   *
   * @param component MEG component
   * @param temperature temperature in K
   * @return Henry reference in bar kg/mol
   */
  public static double henryReference(ComponentInterface component, double temperature) {
    return 0.01801528 * component.getAntoineVaporPressure(temperature);
  }
}
