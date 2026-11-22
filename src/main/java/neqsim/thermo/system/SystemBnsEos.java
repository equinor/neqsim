package neqsim.thermo.system;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentBNS;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.phase.PhaseBNS;

/**
 * Thermodynamic system implementing the Burgoyne–Nielsen–Stanko PR correlation.
 *
 * @author esol
 * @version 1.0
 */
public class SystemBnsEos extends SystemEos {
  private static final long serialVersionUID = 1L;

  /**
   * Converts a temperature from degrees Rankine to Kelvin.
   *
   * @param degR temperature in degrees Rankine
   * @return temperature in Kelvin
   */
  private static double degRToK(double degR) {
    return degR * 5.0 / 9.0;
  }

  /**
   * Converts an absolute pressure from psi to bar.
   *
   * @param psia absolute pressure in psi
   * @return absolute pressure in bar
   */
  private static double psiaToBar(double psia) {
    return psia * 0.06894757293168;
  }

  // Conversion constants
  private static final double MW_AIR = 28.97; // Molecular weight of air [g/mol]
  private static final double MW_CH4 = 16.0425; // Molecular weight of methane [g/mol]
  private static final double TcCH4 = degRToK(343.008); // Critical temperature of methane [K]
  private static final double PcCH4 = psiaToBar(667.029); // Critical pressure of methane [bar]
  private static final double VcZcCH4 = ThermodynamicConstantsInterface.R * TcCH4 / (PcCH4 * 1.0e5);

  // Helium is appended to preserve the original five component indices.
  private static final int HYDROCARBON_INDEX = 4;
  private static final int HELIUM_INDEX = 5;
  private static final double HELIUM_MOLAR_MASS = 4.003; // g/mol

  private double[] tcs;
  private double[] pcs;
  private double[] mws;
  private final double[] acfs;
  private final double[] omegaA;
  private final double[] omegaB;
  private final double[] vshift;

  private double[] zfractions = new double[HELIUM_INDEX];
  private double heliumMoleFraction;
  private double relativeDensity = 0.65;
  private boolean associatedGas = false;
  private boolean componentsInitialized = false;

  /**
   * Returns the hydrocarbon pseudo-critical temperature for associated gas.
   *
   * @param x hydrocarbon molar mass above that of methane in g/mol
   * @return pseudo-critical temperature in Kelvin
   */
  private static double tcAg(double x) {
    return degRToK(2695.14765 * x / (274.341701 + x) + 343.008);
  }

  /**
   * Returns the hydrocarbon pseudo-critical temperature for gas condensate.
   *
   * @param x hydrocarbon molar mass above that of methane in g/mol
   * @return pseudo-critical temperature in Kelvin
   */
  private static double tcGc(double x) {
    return degRToK(1098.10948 * x / (101.529237 + x) + 343.008);
  }

  /**
   * Returns the hydrocarbon pseudo-critical pressure.
   *
   * @param x hydrocarbon molar mass above that of methane in g/mol
   * @param vcSlope slope of the critical volume divided by critical compressibility with respect to molar mass
   * @param tc pseudo-critical temperature in Kelvin
   * @return pseudo-critical pressure in bar
   */
  private static double pcFn(double x, double vcSlope, double tc) {
    double vcOnZc = vcSlope * x + VcZcCH4;
    return ThermodynamicConstantsInterface.R * tc / vcOnZc / 1.0e5;
  }

  /**
   * Returns the pseudo-critical temperature and pressure of the hydrocarbon fraction.
   *
   * @param sgHc hydrocarbon specific gravity relative to air
   * @param ag whether the hydrocarbon is characterized as associated gas
   * @return temperature in Kelvin and pressure in bar
   */
  private static double[] pseudoCritical(double sgHc, boolean ag) {
    double x = Math.max(0.0, MW_AIR * sgHc - MW_CH4);
    double tpc;
    double slope;
    if (ag) {
      tpc = tcAg(x);
      slope = 0.177497835 * 0.0283168466 / 453.59237;
    } else {
      tpc = tcGc(x);
      slope = 0.170931432 * 0.0283168466 / 453.59237;
    }
    double ppc = pcFn(x, slope, tpc);
    return new double[] {tpc, ppc};
  }

  /**
   * Calculates the hydrocarbon specific gravity by removing the known non-hydrocarbon contributions.
   *
   * @return hydrocarbon specific gravity, floored at methane
   */
  private double hydrocarbonSg() {
    double fracHc = zfractions[HYDROCARBON_INDEX];
    if (fracHc <= 0.0) {
      return 0.75; // The unused hydrocarbon pseudo-component still needs valid parameters.
    }

    double nonHydrocarbonMass = heliumMoleFraction * HELIUM_MOLAR_MASS;
    for (int i = 0; i < HYDROCARBON_INDEX; i++) {
      nonHydrocarbonMass += zfractions[i] * mws[i] * 1000.0;
    }
    return Math.max(MW_CH4 / MW_AIR, (relativeDensity - nonHydrocarbonMass / MW_AIR) / fracHc);
  }

  /**
   * Returns the NeqSim name for a BNS component slot.
   *
   * @param i component index
   * @return component name
   */
  private static String compName(int i) {
    switch (i) {
    case 0:
      return "CO2";
    case 1:
      return "H2S";
    case 2:
      return "N2";
    case 3:
      return "H2";
    case HYDROCARBON_INDEX:
      return "HC";
    default:
      return "helium";
    }
  }

  /**
   * Constructor for SystemBnsEos.
   */
  public SystemBnsEos() {
    this(288.15, 1.0);
  }

  /**
   * Constructor for SystemBnsEos.
   *
   * @param T a double
   * @param P a double
   */
  public SystemBnsEos(double T, double P) {
    super(T, P, false);
    modelName = "BNS-PR";
    attractiveTermNumber = 1;

    tcs = new double[] {degRToK(547.416), degRToK(672.120), degRToK(227.160), degRToK(47.430), 1.0};
    pcs = new double[] {psiaToBar(1069.51), psiaToBar(1299.97), psiaToBar(492.84), psiaToBar(187.53), 1.0};
    mws = new double[] {44.01 / 1000.0, 34.082 / 1000.0, 28.014 / 1000.0, 2.016 / 1000.0, 0.0};
    acfs = new double[] {0.12253, 0.04909, 0.037, -0.217, -0.03899};
    omegaA = new double[] {0.427671, 0.436725, 0.457236, 0.457236, 0.457236};
    omegaB = new double[] {0.0696397, 0.0724345, 0.0777961, 0.0777961, 0.0777961};
    vshift = new double[] {-0.27607, -0.22901, -0.21066, -0.36270, -0.19076};

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseBNS(tcs, pcs, mws, acfs, omegaA, omegaB, vshift);
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    this.useVolumeCorrection(true);
  }

  /**
   * Constructs a BNS-PR system with composition.
   *
   * @param T a double
   * @param P a double
   * @param sg a double
   * @param yCO2 a double
   * @param yH2S a double
   * @param yN2 a double
   * @param yH2 a double
   * @param associatedGas a boolean
   */
  public SystemBnsEos(double T, double P, double sg, double yCO2, double yH2S, double yN2, double yH2,
      boolean associatedGas) {
    this(T, P);
    setComposition(sg, yCO2, yH2S, yN2, yH2, associatedGas);
  }

  /**
   * Sets the composition using BNS-specific parameters matching the Python interface. This method matches the Python
   * call: setComposition(sg, yCO2, yH2S, yN2, yH2, associatedGas)
   *
   * @param sg relative density (specific gravity)
   * @param yCO2 mole fraction of CO2 (must be &gt;= 0)
   * @param yH2S mole fraction of H2S (must be &gt;= 0)
   * @param yN2 mole fraction of N2 (must be &gt;= 0)
   * @param yH2 mole fraction of H2 (must be &gt;= 0)
   * @param associatedGas true for associated gas, false for gas condensate
   * @throws java.lang.IllegalArgumentException if any mole fraction is negative or sum exceeds 1.0
   */
  public void setComposition(double sg, double yCO2, double yH2S, double yN2, double yH2, boolean associatedGas) {
    setComposition(sg, yCO2, yH2S, yN2, yH2, 0.0, associatedGas);
  }

  /**
   * Sets the BNS composition, including helium. Hydrocarbon is the balance of the supplied mole fractions.
   *
   * @param sg relative density (specific gravity)
   * @param yCO2 mole fraction of CO2
   * @param yH2S mole fraction of H2S
   * @param yN2 mole fraction of N2
   * @param yH2 mole fraction of H2
   * @param yHe mole fraction of helium
   * @param associatedGas true for associated gas, false for gas condensate
   * @throws IllegalArgumentException if a fraction is non-finite or negative, or their sum exceeds 1
   */
  public void setComposition(double sg, double yCO2, double yH2S, double yN2, double yH2, double yHe,
      boolean associatedGas) {
    if (!(yCO2 >= 0.0 && yH2S >= 0.0 && yN2 >= 0.0 && yH2 >= 0.0 && yHe >= 0.0)) {
      throw new IllegalArgumentException("Mole fractions must be non-negative");
    }
    double sum = yCO2 + yH2S + yN2 + yH2 + yHe;
    if (sum > 1.0) {
      throw new IllegalArgumentException(String.format("Sum of mole fractions (%.4f) exceeds 1.0", sum));
    }
    this.relativeDensity = sg;
    this.associatedGas = associatedGas;
    zfractions[0] = yCO2;
    zfractions[1] = yH2S;
    zfractions[2] = yN2;
    zfractions[3] = yH2;
    zfractions[HYDROCARBON_INDEX] = 1.0 - sum;
    heliumMoleFraction = yHe;
    componentsInitialized = true;
    updateComposition();
  }

  /**
   * Sets the composition of the system using mole fractions of CO2, H2S, N2, and H2.
   *
   * @param yCO2 mole fraction of CO2 (must be &gt;= 0)
   * @param yH2S mole fraction of H2S (must be &gt;= 0)
   * @param yN2 mole fraction of N2 (must be &gt;= 0)
   * @param yH2 mole fraction of H2 (must be &gt;= 0)
   * @throws java.lang.IllegalArgumentException if any mole fraction is negative or sum exceeds 1.0
   */
  public void setComposition(double yCO2, double yH2S, double yN2, double yH2) {
    setComposition(relativeDensity, yCO2, yH2S, yN2, yH2, 0.0, associatedGas);
  }

  /**
   * Setter for the field <code>relativeDensity</code>.
   *
   * @param sg a double (relative density, must be positive)
   * @throws java.lang.IllegalArgumentException if relative density is not positive
   */
  public void setRelativeDensity(double sg) {
    if (sg <= 0) {
      throw new IllegalArgumentException("Relative density must be positive, got: " + sg);
    }
    this.relativeDensity = sg;
    if (componentsInitialized) {
      updateComposition();
    }
  }

  /**
   * Setter for the field <code>associatedGas</code>.
   *
   * @param ag a boolean
   */
  public void setAssociatedGas(boolean ag) {
    this.associatedGas = ag;
    if (componentsInitialized) {
      updateComposition();
    }
  }

  /**
   * Updates the hydrocarbon pseudo-component properties based on composition and relative density.
   */
  private void updateHydrocarbonProperties() {
    double sgHc = hydrocarbonSg();
    double[] tcpc = pseudoCritical(sgHc, associatedGas);
    tcs[HYDROCARBON_INDEX] = tcpc[0];
    pcs[HYDROCARBON_INDEX] = tcpc[1];
    mws[HYDROCARBON_INDEX] = sgHc * MW_AIR / 1000.0;
  }

  /**
   * Calculates ideal-gas heat-capacity coefficients from the current hydrocarbon molar mass.
   *
   * @return SI heat-capacity polynomial coefficients in component order
   */
  private double[][] calculateHeatCapacityCoefficients() {
    // Base heat capacity coefficients for CO2, H2S, N2, H2, HC and helium
    double[][] cp = {{2.725473196, 0.004103751, 1.5602e-5, -4.19321e-8, 3.10542e-11},
        {4.446031265, -0.005296052, 2.0533e-5, -2.58993e-8, 1.25555e-11},
        {3.423811591, 0.001007461, -4.58491e-6, 8.4252e-9, -4.38083e-12},
        {1.421468418, 0.018192108, -6.04285e-5, 9.08033e-8, -5.18972e-11},
        {5.369051342, -0.014851371, 4.86358e-5, -3.70187e-8, 1.80641e-12}, {2.5, 0, 0, 0, 0}};

    // Apply scaling for hydrocarbon component based on molecular weight
    double hcMw = mws[HYDROCARBON_INDEX] * 1000.0;
    double x = hcMw - MW_CH4;
    double[] a0 = {7.8570e-4, 1.3123e-3, 9.8133e-4, 1.6463e-3, 1.7306e-2};
    double[] a1 = {-8.1649e-3, 5.5485e-3, 8.3258e-2, 2.0635e-1, 2.5551};
    for (int k = 0; k < a0.length; k++) {
      double scale = a0[k] * x * x + a1[k] * x + 1.0;
      cp[HYDROCARBON_INDEX][k] *= scale;
    }

    // Convert to SI units (multiply by R)
    double factor = ThermodynamicConstantsInterface.R;
    for (int i = 0; i < cp.length; i++) {
      for (int j = 0; j < cp[i].length; j++) {
        cp[i][j] *= factor;
      }
    }
    return cp;
  }

  /**
   * Updates the phase components and refreshes BNS interactions after a composition change.
   */
  private void updateComposition() {
    updateHydrocarbonProperties();
    double[][] cpCoeffs = calculateHeatCapacityCoefficients();

    int previousCount = getNumberOfComponents();
    int componentCount = heliumMoleFraction > 0.0 || previousCount > HELIUM_INDEX ? HELIUM_INDEX + 1 : HELIUM_INDEX;
    for (int j = 0; j < componentCount; j++) {
      double moles = j == HELIUM_INDEX ? heliumMoleFraction : zfractions[j];
      if (j >= previousCount) {
        String name = compName(j);
        if (j == HYDROCARBON_INDEX) {
          super.addComponent("methane", moles);
          renameComponent("methane", name);
        } else {
          super.addComponent(name, moles);
        }
      }
      for (int i = 0; i < getMaxNumberOfPhases(); i++) {
        if (phaseArray[i] instanceof PhaseBNS) {
          ComponentBNS comp = (ComponentBNS) phaseArray[i].getComponent(j);
          comp.setNumberOfmoles(moles);
          comp.setNumberOfMolesInPhase(moles);
          comp.setCpA(cpCoeffs[j][0]);
          comp.setCpB(cpCoeffs[j][1]);
          comp.setCpC(cpCoeffs[j][2]);
          comp.setCpD(cpCoeffs[j][3]);
          comp.setCpE(cpCoeffs[j][4]);
          if (j == HYDROCARBON_INDEX) {
            comp.setTC(tcs[j]);
            comp.setPC(pcs[j]);
            comp.setMolarMass(mws[j]);
            comp.calca();
            comp.calcb();
          }
        }
      }
    }
    setTotalNumberOfMolesRaw(1.0);
    if (getMixingRule() == null) {
      setMixingRule(EosMixingRuleType.CLASSIC_T2);
      return;
    }
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      if (phaseArray[i] instanceof PhaseBNS && phaseArray[i].getMixingRuleType() != null) {
        PhaseBNS phase = (PhaseBNS) phaseArray[i];
        if (componentCount != previousCount) {
          phase.setMixingRule(phase.getMixingRuleType());
          phase.resetPhysicalProperties();
        } else {
          phase.setBnsBips(getTemperature());
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemBnsEos clone() {
    SystemBnsEos copy = (SystemBnsEos) super.clone();
    copy.zfractions = zfractions.clone();
    copy.tcs = tcs.clone();
    copy.pcs = pcs.clone();
    copy.mws = mws.clone();
    return copy;
  }
}
