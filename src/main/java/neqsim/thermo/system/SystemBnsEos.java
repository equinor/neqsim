package neqsim.thermo.system;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentBNS;
import neqsim.thermo.phase.PhaseBNS;

/**
 * Thermodynamic system implementing the Burgoyne–Nielsen–Stanko PR correlation.
 */
public class SystemBnsEos extends SystemEos {
  private static final long serialVersionUID = 1L;

  private static double degRToK(double degR) {
    return degR * 5.0 / 9.0;
  }

  private static double psiaToBar(double psia) {
    return psia * 0.06894757293168;
  }

  private static final double MW_AIR = 28.97;
  private static final double MW_CH4 = 16.0425;
  private static final double TcCH4 = degRToK(343.008);
  private static final double PcCH4 = psiaToBar(667.029);
  private static final double VcZcCH4 = ThermodynamicConstantsInterface.R * TcCH4 / (PcCH4 * 1.0e5);

  private final double[] tcs;
  private final double[] pcs;
  private final double[] mws;
  private final double[] acfs;
  private final double[] omegaA;
  private final double[] omegaB;
  private final double[] vshift;
  private final double[] vshiftField;
  private double[][] cpCoeffs;

  private final double[] zfractions = new double[5];
  private double relativeDensity = 0.65;
  private boolean associatedGas = false;
  private boolean mixingRuleDefined = false;
  private boolean componentsInitialized = false;

  private static double tcAg(double x) {
    return degRToK(2695.14765 * x / (274.341701 + x) + 343.008);
  }

  private static double tcGc(double x) {
    return degRToK(1098.10948 * x / (101.529237 + x) + 343.008);
  }

  private static double pcFn(double x, double vcSlope, double tc) {
    double vcOnZc = vcSlope * x + VcZcCH4;
    return ThermodynamicConstantsInterface.R * tc / vcOnZc / 1.0e5;
  }

  private static double calcVshift(double ciField, double omegaB, double tc, double pc) {
    double ciSI = ciField * 0.0283168466 / 453.59237;
    double bi = omegaB * ThermodynamicConstantsInterface.R * tc / (pc * 1.0e5);
    return ciSI / bi;
  }

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

  private static double hydrocarbonSg(double sg, double[] zf, double[] mws) {
    double fracHc = zf[4];
    double sumNon = 0.0;
    for (int i = 0; i < 4; i++) {
      sumNon += zf[i] * mws[i];
    }
    if (fracHc > 0) {
      return (sg - sumNon / MW_AIR) / fracHc;
    } else {
      return 0.75;
    }
  }

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
      default:
        return "HC";
    }
  }

  private void applyBnsBips() {
    for (int p = 0; p < getMaxNumberOfPhases(); p++) {
      if (phaseArray[p] instanceof PhaseBNS) {
        ((PhaseBNS) phaseArray[p]).setBnsBips(getTemperature());
      }
    }
  }

  public SystemBnsEos() {
    this(288.15, 1.0);
  }

  public SystemBnsEos(double T, double P) {
    super(T, P, false);
    modelName = "BNS-PR";
    attractiveTermNumber = 1;

    tcs = new double[] {degRToK(547.416), degRToK(672.120), degRToK(227.160), degRToK(47.430), 1.0};
    pcs = new double[] {psiaToBar(1069.51), psiaToBar(1299.97), psiaToBar(492.84),
        psiaToBar(187.53), 1.0};
    mws = new double[] {44.01 / 1000.0, 34.082 / 1000.0, 28.014 / 1000.0, 2.016 / 1000.0, 0.0};
    acfs = new double[] {0.12253, 0.04909, 0.037, -0.217, -0.03899};
    omegaA = new double[] {0.427671, 0.436725, 0.457236, 0.457236, 0.457236};
    omegaB = new double[] {0.0696397, 0.0724345, 0.0777961, 0.0777961, 0.0777961};
    vshiftField = new double[] {-0.27607, -0.22901, -0.21066, -0.36270, -0.19076};
    vshift = new double[5];
    for (int i = 0; i < vshift.length; i++) {
      vshift[i] = -calcVshift(vshiftField[i], omegaB[i], tcs[i], pcs[i]);
    }

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseBNS(tcs, pcs, mws, acfs, omegaA, omegaB, vshift);
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    this.useVolumeCorrection(true);
  }

  /**
   * Constructs a BNS-PR system with composition.
   */
  public SystemBnsEos(double T, double P, double sg, double yCO2, double yH2S, double yN2,
      double yH2, boolean associatedGas) {
    this(T, P);
    this.associatedGas = associatedGas;
    this.relativeDensity = sg;
    setComposition(yCO2, yH2S, yN2, yH2);
  }

  /**
   * Sets the composition and associated gas flag for the system.
   *
   * @param sg relative density
   * @param yCO2 mole fraction of CO2
   * @param yH2S mole fraction of H2S
   * @param yN2 mole fraction of N2
   * @param yH2 mole fraction of H2
   * @param ag true if associated gas, false otherwise
   */
  public void setComposition(double sg, double yCO2, double yH2S, double yN2, double yH2,
      boolean ag) {
    this.relativeDensity = sg;
    this.associatedGas = ag;
    setComposition(yCO2, yH2S, yN2, yH2);
  }

  /**
   * Sets the composition of the system using mole fractions of CO2, H2S, N2, and H2.
   *
   * @param yCO2 mole fraction of CO2
   * @param yH2S mole fraction of H2S
   * @param yN2 mole fraction of N2
   * @param yH2 mole fraction of H2
   */
  public void setComposition(double yCO2, double yH2S, double yN2, double yH2) {
    zfractions[0] = yCO2;
    zfractions[1] = yH2S;
    zfractions[2] = yN2;
    zfractions[3] = yH2;
    componentsInitialized = true;
    updateComposition();
  }

  public void setRelativeDensity(double sg) {
    this.relativeDensity = sg;
    if (componentsInitialized) {
      updateComposition();
    }
  }

  public void setAssociatedGas(boolean ag) {
    this.associatedGas = ag;
    if (componentsInitialized) {
      updateComposition();
    }
  }

  private void updateComposition() {
    double sum = zfractions[0] + zfractions[1] + zfractions[2] + zfractions[3];
    zfractions[4] = 1.0 - sum;

    double[] zf = zfractions;
    double sgHc =
        hydrocarbonSg(relativeDensity, zf, new double[] {44.01, 34.082, 28.014, 2.016, 0.0});
    double[] tcpc = pseudoCritical(sgHc, associatedGas);
    tcs[4] = tcpc[0];
    pcs[4] = tcpc[1];
    mws[4] = sgHc * MW_AIR / 1000.0;
    
    vshift[4] = -calcVshift(vshiftField[4], omegaB[4], tcs[4], pcs[4]);

    double[][] cp = {{2.725473196, 0.004103751, 1.5602e-5, -4.19321e-8, 3.10542e-11},
        {4.446031265, -0.005296052, 2.0533e-5, -2.58993e-8, 1.25555e-11},
        {3.423811591, 0.001007461, -4.58491e-6, 8.4252e-9, -4.38083e-12},
        {1.421468418, 0.018192108, -6.04285e-5, 9.08033e-8, -5.18972e-11},
        {5.369051342, -0.014851371, 4.86358e-5, -3.70187e-8, 1.80641e-12}};

    double hcMw = mws[4] * 1000.0;
    double x = hcMw - MW_CH4;
    double[] a0 = {7.8570e-4, 1.3123e-3, 9.8133e-4, 1.6463e-3, 1.7306e-2};
    double[] a1 = {-8.1649e-3, 5.5485e-3, 8.3258e-2, 2.0635e-1, 2.5551};
    for (int k = 0; k < 5; k++) {
      double scale = a0[k] * x * x + a1[k] * x + 1.0;
      cp[4][k] *= scale;
    }
    double factor = ThermodynamicConstantsInterface.R;
    for (int i = 0; i < cp.length; i++) {
      for (int j = 0; j < cp[i].length; j++) {
        cp[i][j] *= factor;
      }
    }
    cpCoeffs = cp;

    for (int j = 0; j < zf.length; j++) {
      String name = compName(j);
      if (((PhaseBNS) phaseArray[0]).componentArray[j] != null) {
        getComponent(name).setNumberOfmoles(zf[j]);
        for (int i = 0; i < getMaxNumberOfPhases(); i++) {
          if (phaseArray[i] instanceof PhaseBNS) {
            ComponentBNS comp = (ComponentBNS) ((PhaseBNS) phaseArray[i]).componentArray[j];
            if (comp != null) {
              comp.setNumberOfMolesInPhase(zf[j]);
              comp.setCpA(cpCoeffs[j][0]);
              comp.setCpB(cpCoeffs[j][1]);
              comp.setCpC(cpCoeffs[j][2]);
              comp.setCpD(cpCoeffs[j][3]);
              comp.setCpE(cpCoeffs[j][4]);
              comp.setTC(tcs[j]);
              comp.setPC(pcs[j]);
              comp.setMolarMass(mws[j]);
              comp.setVolumeCorrectionConst(vshift[j]);
            }
          }
        }
      } else {
        addBnsComponent(name, zf[j], j);
      }
    }
    setTotalNumberOfMoles(1.0);

    if (mixingRuleDefined) {
      applyBnsBips();
    }
  }

  private void addBnsComponent(String name, double moles, int compIndex) {
    String addName = name.equals("HC") ? "methane" : name;
    super.addComponent(addName, moles);
    if (!addName.equals(name)) {
      renameComponent(addName, name);
    }
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      if (phaseArray[i] != null) {
        ComponentBNS comp = new ComponentBNS(name, moles, moles, compIndex, tcs[compIndex],
            pcs[compIndex], mws[compIndex], acfs[compIndex], omegaA[compIndex], omegaB[compIndex],
            vshift[compIndex]);
        comp.setCpA(cpCoeffs[compIndex][0]);
        comp.setCpB(cpCoeffs[compIndex][1]);
        comp.setCpC(cpCoeffs[compIndex][2]);
        comp.setCpD(cpCoeffs[compIndex][3]);
        comp.setCpE(cpCoeffs[compIndex][4]);
        ((PhaseBNS) phaseArray[i]).componentArray[compIndex] = comp;
      }
    }
  }

  @Override
  public void setMixingRule(int type) {
    super.setMixingRule(type);
    mixingRuleDefined = true;
    applyBnsBips();
  }

  @Override
  public SystemBnsEos clone() {
    return (SystemBnsEos) super.clone();
  }
}
