package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>
 * PFCTConductivityMethodMod86 class extending conductivity for commonphase.
 * </p>
 *
 * @author esol
 * @version Method was revised by Even Solbraa 21.01.2019
 */
public class PFCTConductivityMethodMod86 extends Conductivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Constant <code>referenceSystem</code>. */
  public static SystemInterface referenceSystem =
      new SystemSrkEos(273.0, ThermodynamicConstantsInterface.referencePressure);
  double[] GVcoef = {-2.147621e5, 2.190461e5, -8.618097e4, 1.496099e4, -4.730660e2, -2.331178e2,
      3.778439e1, -2.320481, 5.311764e-2};
  double condRefA = -0.25276292;
  double condRefB = 0.33432859;
  double condRefC = 1.12;
  double condRefF = 168.0;
  double condRefE = 1.0;
  double condRefG = 0.0;

  double[] condRefJ = {-7.04036339907, 12.319512908, -8.8525979933e2, 72.835897919, 0.74421462902,
      -2.9706914540, 2.2209758501e3};
  double[] condRefK = {-8.55109, 12.5539, -1020.85, 238.394, 1.31563, -72.5759, 1411.6};
  double PCmix = 0.0;
  double TCmix = 0.0;
  double Mmix = 0.0;

  /**
   * <p>
   * Constructor for PFCTConductivityMethodMod86.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public PFCTConductivityMethodMod86(PhysicalProperties phase) {
    super(phase);
    if (referenceSystem.getNumberOfMoles() < 1e-10) {
      referenceSystem.addComponent("methane", 10.0);
      referenceSystem.init(0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    double Pc0 = referenceSystem.getPhase(0).getComponent(0).getPC();

    double Tc0 = referenceSystem.getPhase(0).getComponent(0).getTC();
    double M0 = referenceSystem.getPhase(0).getComponent(0).getMolarMass() * 1e3;
    double alfa0 = 1.0;
    double alfaMix = 1.0;
    double tempTC1 = 0.0;
    double tempTC2 = 0.0;
    double tempPC1 = 0.0;
    double tempPC2 = 0.0;
    double Mwtemp = 0.0;

    double Mmtemp = 0.0;
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
        double tempVar =
            phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(j).getx()
                * Math.pow(Math
                    .pow(phase.getPhase().getComponent(i).getTC()
                        / phase.getPhase().getComponent(i).getPC(), 1.0 / 3.0)
                    + Math.pow(phase.getPhase().getComponent(j).getTC()
                        / phase.getPhase().getComponent(j).getPC(), 1.0 / 3.0),
                    3.0);
        tempTC1 += tempVar * Math.sqrt(
            phase.getPhase().getComponent(i).getTC() * phase.getPhase().getComponent(j).getTC());
        tempTC2 += tempVar;
        tempPC1 += tempVar * Math.sqrt(
            phase.getPhase().getComponent(i).getTC() * phase.getPhase().getComponent(j).getTC());
        tempPC2 += tempVar;
      }
      Mwtemp += phase.getPhase().getComponent(i).getx()
          * Math.pow(phase.getPhase().getComponent(i).getMolarMass(), 2.0);
      Mmtemp +=
          phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(i).getMolarMass();
    }

    PCmix = 8.0 * tempPC1 / (tempPC2 * tempPC2);
    TCmix = tempTC1 / tempTC2;
    Mmix = (Mmtemp + 1.304e-4 * (Math.pow(Mwtemp / Mmtemp, 2.303) - Math.pow(Mmtemp, 2.303))) * 1e3; // phase.getPhase().getMolarMass();
    /*
     * double tempMMix1 = 0.0; double tempMMix2 = 0.0; double tempMMix3 = 0.0; double tempMMix4 =
     * 0.0; for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) { for (int j = 0; j <
     * phase.getPhase().getNumberOfComponents(); j++) { tempMMix1 =
     * phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(j).getx() *
     * Math.sqrt(1.0 / phase.getPhase().getComponent(i).getMolarMass() + 1.0 /
     * phase.getPhase().getComponent(j).getMolarMass()) *
     * Math.pow(phase.getPhase().getComponent(i).getTC() / phase.getPhase().getComponent(j).getTC(),
     * 1.0 / 4.0); tempMMix2 = Math.pow(Math.pow(phase.getPhase().getComponent(i).getTC() /
     * phase.getPhase().getComponent(i).getPC(), 1.0 / 3.0) +
     * Math.pow(phase.getPhase().getComponent(j).getTC() / phase.getPhase().getComponent(j).getPC(),
     * 1.0 / 3.0), 2.0); tempMMix3 += tempMMix1 / tempMMix2; } } tempMMix4 = 1.0 / 16.0 *
     * Math.pow(tempMMix3, -2.0) * Math.pow(TCmix, -1.0 / 3.0) * Math.pow(PCmix, 4.0 / 3.0);
     */
    if (Double.isNaN(PCmix) || Double.isNaN(TCmix)) {
      PCmix = 1.0;
      TCmix = 273.15;
    }
    double TOref = phase.getPhase().getTemperature()
        * referenceSystem.getPhase(0).getComponent(0).getTC() / TCmix;
    referenceSystem.setTemperature(TOref);
    referenceSystem.setPressure(phase.getPhase().getPressure()
        * referenceSystem.getPhase(0).getComponent(0).getPC() / PCmix);
    try {
      referenceSystem.init(1);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    double molDens = 1.0 / referenceSystem.getLowestGibbsEnergyPhase().getMolarVolume() * 100.0;
    double critMolDens = 10.1521197;
    double redDens = molDens / critMolDens;

    double[] alphai = new double[phase.getPhase().getNumberOfComponents()];
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      alphai[i] = 1.0 + 6.004e-4 * Math.pow(redDens, 2.043)
          * Math.pow(phase.getPhase().getComponent(i).getMolarMass() * 1e3, 1.086);
    }
    alfaMix = 0.0;
    alfa0 = 1.0 + 6.004e-4 * Math.pow(redDens, 2.043)
        * Math.pow(referenceSystem.getMolarMass() * 1e3, 1.086);
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
        alfaMix += phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(j).getx()
            * Math.sqrt(alphai[i] * alphai[j]);
      }
    }
    if (alfaMix < 1e-10) {
      return 0.0; // this can happen befor init is doen on system
      // alfaMix= alfaMix*2;
    }

    double T0 = phase.getPhase().getTemperature()
        * referenceSystem.getPhase(0).getComponent(0).getTC() / TCmix * alfa0 / alfaMix;
    double P0 = phase.getPhase().getPressure() * referenceSystem.getPhase(0).getComponent(0).getPC()
        / PCmix * alfa0 / alfaMix;

    if (Double.isNaN(T0) || Double.isNaN(P0)) {
      P0 = 1.0;
      T0 = 273.15;
    }

    double nstarRef =
        getRefComponentViscosity(T0, ThermodynamicConstantsInterface.referencePressure);
    double CpID = referenceSystem.getLowestGibbsEnergyPhase().getComponent(0).getCp0(T0);
    double Ffunc = 1.0 + 0.053432 * redDens - 0.030182 * redDens * redDens
        - 0.029725 * redDens * redDens * redDens;
    double condIntRef =
        (1.18653 * nstarRef * (CpID - 2.5 * neqsim.thermo.ThermodynamicConstantsInterface.R)
            * Ffunc) / (referenceSystem.getMolarMass()); // *1e3;

    double nstarMix = calcMixLPViscosity();
    double FfuncMix = 1.0 + 0.053432 * redDens - 0.030182 * redDens * redDens
        - 0.029725 * redDens * redDens * redDens;
    double CpIDmix = phase.getPhase().getCp0();
    double condIntMix =
        (1.18653 * nstarMix * (CpIDmix - 2.5 * neqsim.thermo.ThermodynamicConstantsInterface.R)
            * FfuncMix) / (Mmix / 1.0e3); // *1e3;

    double refConductivity = getRefComponentConductivity(T0, P0);

    conductivity = Math.pow(TCmix / Tc0, -1.0 / 6.0) * Math.pow(PCmix / Pc0, 2.0 / 3.0)
        * Math.pow(Mmix / M0, -0.5) * alfaMix / alfa0 * (refConductivity - condIntRef) + condIntMix;
    return conductivity;
  }

  /**
   * <p>
   * getRefComponentConductivity.
   * </p>
   *
   * @param temp a double
   * @param pres a double
   * @return a double
   */
  public double getRefComponentConductivity(double temp, double pres) {
    referenceSystem.setTemperature(temp);
    referenceSystem.setPressure(pres);
    referenceSystem.init(1);
    double molDens = 1.0 / referenceSystem.getLowestGibbsEnergyPhase().getMolarVolume() * 100.0; // mol/dm^3
    double critMolDens = 10.15;
    double redMolDens = (molDens - critMolDens) / critMolDens;
    // density in gr/cm^3
    molDens = referenceSystem.getLowestGibbsEnergyPhase().getDensity() * 1e-3;

    double viscRefO = GVcoef[0] * Math.pow(temp, -1.0) + GVcoef[1] * Math.pow(temp, -2.0 / 3.0)
        + GVcoef[2] * Math.pow(temp, -1.0 / 3.0) + GVcoef[3] + GVcoef[4] * Math.pow(temp, 1.0 / 3.0)
        + GVcoef[5] * Math.pow(temp, 2.0 / 3.0) + GVcoef[6] * temp
        + GVcoef[7] * Math.pow(temp, 4.0 / 3.0) + GVcoef[8] * Math.pow(temp, 5.0 / 3.0);

    double viscRef1 =
        (condRefA + condRefB * Math.pow(condRefC - Math.log(temp / condRefF), 2.0)) * molDens;

    double temp1 = Math.pow(molDens, 0.1) * (condRefJ[1] + condRefJ[2] / Math.pow(temp, 3.0 / 2.0));
    double temp2 = redMolDens * Math.pow(molDens, 0.5)
        * (condRefJ[4] + condRefJ[5] / temp + condRefJ[6] / Math.pow(temp, 2.0));
    double temp3 = Math.exp(temp1 + temp2);

    double dTfreeze = temp - 90.69;
    double HTAN =
        (Math.exp(dTfreeze) - Math.exp(-dTfreeze)) / (Math.exp(dTfreeze) + Math.exp(-dTfreeze));
    condRefE = (HTAN + 1.0) / 2.0;

    double viscRef2 = condRefE * Math.exp(condRefJ[0] + condRefJ[3] / temp) * (temp3 - 1.0);
    if (Double.isNaN(viscRef2)) {
      viscRef2 = 0.0;
    }
    double temp4 = Math.pow(molDens, 0.1) * (condRefK[1] + condRefK[2] / Math.pow(temp, 3.0 / 2.0));
    double temp5 = redMolDens * Math.pow(molDens, 0.5)
        * (condRefK[4] + condRefK[5] / temp + condRefK[6] / Math.pow(temp, 2.0));
    double temp6 = Math.exp(temp4 + temp5);
    condRefG = (1.0 - HTAN) / 2.0;
    double viscRef3 = condRefG * Math.exp(condRefK[0] + condRefK[3] / temp) * (temp6 - 1.0);
    if (Double.isNaN(viscRef3)) {
      viscRef3 = 0.0;
    }
    double refCond = (viscRefO + viscRef1 + viscRef2 + viscRef3) * 1e-3;
    return refCond;
  }

  /**
   * <p>
   * getRefComponentViscosity.
   * </p>
   *
   * @param temp a double
   * @param pres a double
   * @return a double
   */
  public double getRefComponentViscosity(double temp, double pres) {
    double[] GVcoef = {-2.090975e5, 2.647269e5, -1.472818e5, 4.716740e4, -9.491872e3, 1.219979e3,
        -9.627993e1, 4.274152, -8.141531e-2};
    double visRefE = 1.0;
    double[] viscRefJ = {-1.035060586e1, 1.7571599671e1, -3.0193918656e3, 1.8873011594e2,
        4.2903609488e-2, 1.4529023444e2, 6.1276818706e3};
    // double viscRefK[] = {-9.74602, 18.0834, -4126.66, 44.6055, 0.9676544, 81.8134, 15649.9};

    double molDens = ThermodynamicConstantsInterface.atm / ThermodynamicConstantsInterface.R
        / phase.getPhase().getTemperature() / 1.0e3;
    double critMolDens = 10.15;
    double redMolDens = (molDens - critMolDens) / critMolDens;
    double viscRefO = GVcoef[0] * Math.pow(temp, -1.0) + GVcoef[1] * Math.pow(temp, -2.0 / 3.0)
        + GVcoef[2] * Math.pow(temp, -1.0 / 3.0) + GVcoef[3] + GVcoef[4] * Math.pow(temp, 1.0 / 3.0)
        + GVcoef[5] * Math.pow(temp, 2.0 / 3.0) + GVcoef[6] * temp
        + GVcoef[7] * Math.pow(temp, 4.0 / 3.0) + GVcoef[8] * Math.pow(temp, 5.0 / 3.0);

    double temp1 = Math.pow(molDens, 0.1) * (viscRefJ[1] + viscRefJ[2] / Math.pow(temp, 3.0 / 2.0));
    double temp2 = redMolDens * Math.pow(molDens, 0.5)
        * (viscRefJ[4] + viscRefJ[5] / temp + viscRefJ[6] / Math.pow(temp, 2.0));
    double temp3 = Math.exp(temp1 + temp2);

    double dTfreeze = temp - 90.69;
    double HTAN =
        (Math.exp(dTfreeze) - Math.exp(-dTfreeze)) / (Math.exp(dTfreeze) + Math.exp(-dTfreeze));
    visRefE = (HTAN + 1.0) / 2.0;

    double viscRef2 = visRefE * Math.exp(viscRefJ[0] + viscRefJ[3] / temp) * (temp3 - 1.0);
    if (Double.isNaN(viscRef2)) {
      viscRef2 = 0.0;
    }
    double refVisc = (viscRefO + viscRef2) / 1.0e7;
    return refVisc;
  }

  /**
   * <p>
   * calcMixLPViscosity.
   * </p>
   *
   * @return a double
   */
  public double calcMixLPViscosity() {
    double Pc0 = referenceSystem.getPhase(0).getComponent(0).getPC();

    double Tc0 = referenceSystem.getPhase(0).getComponent(0).getTC();
    double M0 = referenceSystem.getPhase(0).getComponent(0).getMolarMass() * 1e3;
    // double PCmix = 0.0, TCmix = 0.0, Mmix = 0.0;
    /*
     * for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) { for (int j = 0; j <
     * phase.getPhase().getNumberOfComponents(); j++) { double tempVar =
     * phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(j).getx() *
     * Math.pow(Math.pow(phase.getPhase().getComponent(i).getTC() /
     * phase.getPhase().getComponent(i).getPC(), 1.0 / 3.0) +
     * Math.pow(phase.getPhase().getComponent(j).getTC() / phase.getPhase().getComponent(j).getPC(),
     * 1.0 / 3.0), 3.0); tempTC1 += tempVar * Math.sqrt(phase.getPhase().getComponent(i).getTC() *
     * phase.getPhase().getComponent(j).getTC()); tempTC2 += tempVar; tempPC1 += tempVar *
     * Math.sqrt(phase.getPhase().getComponent(i).getTC() *
     * phase.getPhase().getComponent(j).getTC()); tempPC2 += tempVar; } Mwtemp +=
     * phase.getPhase().getComponent(i).getx() *
     * Math.pow(phase.getPhase().getComponent(i).getMolarMass(), 2.0); Mmtemp +=
     * phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(i).getMolarMass(); }
     *
     * PCmix = 8.0 * tempPC1 / (tempPC2 * tempPC2); TCmix = tempTC1 / tempTC2; Mmix = (Mmtemp +
     * 1.304e-4 * (Math.pow(Mwtemp / Mmtemp, 2.303) - Math.pow(Mmtemp, 2.303))) * 1e3;
     * //phase.getPhase().getMolarMass();
     */
    double redDens = 101325 / ThermodynamicConstantsInterface.R / phase.getPhase().getTemperature()
        / 1.0e3 / 10.15;
    double alfa0 = 0.0;
    double alfaMix = 0.0;
    double[] alphai = new double[phase.getPhase().getNumberOfComponents()];
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      alphai[i] = 1.0 + 6.004e-4 * Math.pow(redDens, 2.043)
          * Math.pow(phase.getPhase().getComponent(i).getMolarMass() * 1e3, 1.086);
    }
    alfaMix = 0.0;
    alfa0 = 1.0 + 6.004e-4 * Math.pow(redDens, 2.043)
        * Math.pow(referenceSystem.getMolarMass() * 1e3, 1.086);
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
        alfaMix += phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(j).getx()
            * Math.sqrt(alphai[i] * alphai[j]);
      }
    }

    double T0 = phase.getPhase().getTemperature()
        * referenceSystem.getPhase(0).getComponent(0).getTC() / TCmix * alfaMix / alfa0;
    double P0 = ThermodynamicConstantsInterface.referencePressure
        * referenceSystem.getPhase(0).getComponent(0).getPC() / PCmix * alfaMix / alfa0;

    double refVisosity = getRefComponentViscosity(T0, P0);
    double viscosity = refVisosity * Math.pow(TCmix / Tc0, -1.0 / 6.0)
        * Math.pow(PCmix / Pc0, 2.0 / 3.0) * Math.pow(Mmix / M0, 0.5) * alfaMix / alfa0;
    return viscosity;
  }
}
