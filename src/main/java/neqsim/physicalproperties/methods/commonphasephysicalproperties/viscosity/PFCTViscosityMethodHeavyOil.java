package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>
 * PFCTViscosityMethodHeavyOil class.
 * </p>
 *
 * @author esol
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class PFCTViscosityMethodHeavyOil extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // SystemInterface referenceSystem = new SystemBWRSEos(273.15,
  // ThermodynamicConstantsInterface.referencePressure);
  SystemInterface referenceSystem =
      new SystemSrkEos(273.0, ThermodynamicConstantsInterface.referencePressure);

  // todo: is this parameter required?
  int phaseTypeNumb = 1;
  double[] GVcoef = {-2.090975e5, 2.647269e5, -1.472818e5, 4.716740e4, -9.491872e3, 1.219979e3,
      -9.627993e1, 4.274152, -8.141531e-2};
  double visRefA = 1.696985927;

  double visRefB = -0.133372346;

  double visRefC = 1.4;

  double visRefF = 168.0;

  double visRefE = 1.0;

  double visRefG = 0.0;

  double[] viscRefJ = {-1.035060586e1, 1.7571599671e1, -3.0193918656e3, 1.8873011594e2,
      4.2903609488e-2, 1.4529023444e2, 6.1276818706e3};
  double[] viscRefK = {-9.74602, 18.0834, -4126.66, 44.6055, 0.976544, 81.8134, 15649.9};

  /**
   * <p>
   * Constructor for PFCTViscosityMethodHeavyOil.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public PFCTViscosityMethodHeavyOil(PhysicalProperties phase) {
    super(phase);
    if (referenceSystem.getNumberOfMoles() < 1e-10) {
      referenceSystem.addComponent("methane", 10.0);
      referenceSystem.init(0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    this.calcPureComponentViscosity();

    double Pc0 = referenceSystem.getPhase(0).getComponent(0).getPC();

    double Tc0 = referenceSystem.getPhase(0).getComponent(0).getTC();
    double M0 = referenceSystem.getPhase(0).getComponent(0).getMolarMass() * 1e3;
    double PCmix = 0.0;
    double TCmix = 0.0;
    double Mmix = 0.0;
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
    if (tempTC2 < 1e-10) {
      return 0.0;
    }
    PCmix = 8.0 * tempPC1 / (tempPC2 * tempPC2);
    TCmix = tempTC1 / tempTC2;
    Mmix = (Mmtemp + 1.304e-4 * (Math.pow(Mwtemp / Mmtemp, 2.303) - Math.pow(Mmtemp, 2.303))) * 1e3; // phase.getPhase().getMolarMass();

    referenceSystem.setTemperature(phase.getPhase().getTemperature()
        * referenceSystem.getPhase(0).getComponent(0).getTC() / TCmix);
    referenceSystem.setPressure(phase.getPhase().getPressure()
        * referenceSystem.getPhase(0).getComponent(0).getPC() / PCmix);
    referenceSystem.init(1);

    // todo: mixing phasetype and phase index?
    // double molDens = 1.0 /
    // referenceSystem.getPhase(phaseTypeNumb).getMolarVolume() * 100.0;
    double molDens = 1.0 / referenceSystem.getLowestGibbsEnergyPhase().getMolarVolume() * 100.0;
    double critMolDens = 10.15; // 1.0/referenceSystem.getPhase(0).getComponent(0).getCriticalVolume();
    double redDens = molDens / critMolDens;

    alfaMix = 1.0 + 7.378e-3 * Math.pow(redDens, 1.847) * Math.pow(Mmix, 0.5173);
    alfa0 = 1.0 + 7.378e-3 * Math.pow(redDens, 1.847)
        * Math.pow(referenceSystem.getMolarMass() * 1.0e3, 0.5173);
    // alfa0 = 1.0 + 8.374e-4 * Math.pow(redDens, 4.265);
    // System.out.println("func " + 7.475e-5*Math.pow(16.043, 0.8579));
    double T0 = phase.getPhase().getTemperature()
        * referenceSystem.getPhase(0).getComponent(0).getTC() / TCmix * alfa0 / alfaMix;
    double P0 = phase.getPhase().getPressure() * referenceSystem.getPhase(0).getComponent(0).getPC()
        / PCmix * alfa0 / alfaMix;
    double refVisosity = 0.0;
    if (T0 < 75.0) {
      double molM = 0.0;
      if (Mwtemp / Mmtemp / Mmtemp <= 1.5) {
        molM = Mmtemp * 1e3;
      } else {
        molM = Mmtemp * Math.pow(Mwtemp / Mmtemp / (1.5 * Mmtemp), 0.5) * 1e3;
      }
      double sign = 1.0;
      if (phase.getPhase().getTemperature() > 564.49) {
        sign = -1.0;
      }
      double termm = -0.07955 - sign * 0.01101 * molM - 371.8 / phase.getPhase().getTemperature()
          + 6.215 * molM / phase.getPhase().getTemperature();
      double HOviscosity = Math.pow(10.0, termm) * 1.0e-3;
      HOviscosity += HOviscosity * 0.008 * (phase.getPhase().getPressure() - 1.0);
      // refVisosity = refVisosity * Math.exp(0.00384 *
      // (Math.pow(phase.getPhase().getPressure(),0.8226) - 1.0) / 0.8226);

      if (T0 < 65) {
        return HOviscosity;
      }

      refVisosity = getRefComponentViscosity(T0, P0);
      double LOviscosity = refVisosity * Math.pow(TCmix / Tc0, -1.0 / 6.0)
          * Math.pow(PCmix / Pc0, 2.0 / 3.0) * Math.pow(Mmix / M0, 0.5) * alfaMix / alfa0;
      return LOviscosity * (1.0 - (75 - T0) / 10.0) + HOviscosity * (75.0 - T0) / 10.0;
    }
    // System.out.println("m/mix " + Mmix/M0);
    // System.out.println("a/amix " + alfaMix/alfa0);
    refVisosity = getRefComponentViscosity(T0, P0);
    double viscosity = refVisosity * Math.pow(TCmix / Tc0, -1.0 / 6.0)
        * Math.pow(PCmix / Pc0, 2.0 / 3.0) * Math.pow(Mmix / M0, 0.5) * alfaMix / alfa0;
    // System.out.println("viscosityLO " + viscosity);

    return viscosity;
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
    referenceSystem.setTemperature(temp);
    // System.out.println("ref temp " + temp);
    referenceSystem.setPressure(pres);
    // System.out.println("ref pres " + pres);
    referenceSystem.init(1);
    // referenceSystem.display();

    // todo: mixing phasetype and phase index?
    // double molDens = 1.0 /
    // referenceSystem.getPhase(phaseTypeNumb).getMolarVolume() * 100.0;
    double molDens = 1.0 / referenceSystem.getLowestGibbsEnergyPhase().getMolarVolume() * 100.0; // mol/dm^3
    // System.out.println("mol dens " + molDens);
    double critMolDens = 10.15; // 1.0/referenceSystem.getPhase(0).getComponent(0).getCriticalVolume();
    double redMolDens = (molDens - critMolDens) / critMolDens;
    // System.out.println("gv1 " +GVcoef[0]);

    molDens = referenceSystem.getLowestGibbsEnergyPhase().getDensity() * 1e-3;

    double viscRefO = GVcoef[0] * Math.pow(temp, -1.0) + GVcoef[1] * Math.pow(temp, -2.0 / 3.0)
        + GVcoef[2] * Math.pow(temp, -1.0 / 3.0) + GVcoef[3] + GVcoef[4] * Math.pow(temp, 1.0 / 3.0)
        + GVcoef[5] * Math.pow(temp, 2.0 / 3.0) + GVcoef[6] * temp
        + GVcoef[7] * Math.pow(temp, 4.0 / 3.0) + GVcoef[8] * Math.pow(temp, 5.0 / 3.0);

    // System.out.println("ref visc0 " + viscRefO);
    double viscRef1 =
        (visRefA + visRefB * Math.pow(visRefC - Math.log(temp / visRefF), 2.0)) * molDens;
    // System.out.println("ref visc1 " + viscRef1);

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

    double temp4 = Math.pow(molDens, 0.1) * (viscRefK[1] + viscRefK[2] / Math.pow(temp, 3.0 / 2.0));
    double temp5 = redMolDens * Math.pow(molDens, 0.5)
        * (viscRefK[4] + viscRefK[5] / temp + viscRefK[6] / Math.pow(temp, 2.0));
    double temp6 = Math.exp(temp4 + temp5);
    visRefG = (1.0 - HTAN) / 2.0;
    double viscRef3 = visRefG * Math.exp(viscRefK[0] + viscRefK[3] / temp) * (temp6 - 1.0);
    if (Double.isNaN(viscRef3)) {
      viscRef3 = 0.0;
    }
    double refVisc = (viscRefO + viscRef1 + viscRef2 + viscRef3) / 1.0e7;
    return refVisc;
  }
}
