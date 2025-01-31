package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>
 * PFCTViscosityMethodMod86 class.
 * </p>
 *
 * @author esol
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class PFCTViscosityMethodMod86 extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // SystemInterface referenceSystem = new SystemBWRSEos(273.15,
  // ThermodynamicConstantsInterface.referencePressure);
  SystemInterface referenceSystem =
      new SystemSrkEos(273.15, ThermodynamicConstantsInterface.referencePressure);

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
   * Constructor for PFCTViscosityMethodMod86.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public PFCTViscosityMethodMod86(PhysicalProperties phase) {
    super(phase);
    if (referenceSystem.getNumberOfMoles() < 1e-10) {
      referenceSystem.addComponent("methane", 10.0);
      referenceSystem.init(0);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    final double Pc0 = referenceSystem.getPhase(0).getComponent(0).getPC();
    final double Tc0 = referenceSystem.getPhase(0).getComponent(0).getTC();
    final double M0 = referenceSystem.getPhase(0).getComponent(0).getMolarMass() * 1e3;
    double PCmix = 0.0;
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
    double TCmix = tempTC1 / tempTC2;
    double Mmix = (Mmtemp + 1.304e-4 * (Math.pow(Mwtemp / Mmtemp, 2.303) - Math.pow(Mmtemp, 2.303))) * 1e3; // phase.getPhase().getMolarMass();

    referenceSystem.setTemperature(phase.getPhase().getTemperature());
    referenceSystem.setPressure(phase.getPhase().getPressure());
    referenceSystem.init(1);

    double molDens = 1.0 / referenceSystem.getLowestGibbsEnergyPhase().getMolarVolume() * 100.0;
    double critMolDens = 10.15; // 1.0/referenceSystem.getPhase(0).getComponent(0).getCriticalVolume();
    double redDens = molDens / critMolDens;

    double alfaMix = 1.0 + 7.378e-3 * Math.pow(redDens, 1.847) * Math.pow(Mmix, 0.5173);
    double alfa0 = 1.0 + 7.378e-3 * Math.pow(redDens, 1.847)
        * Math.pow(referenceSystem.getMolarMass() * 1.0e3, 0.5173);
    
    double T0 = phase.getPhase().getTemperature()
        * referenceSystem.getPhase(0).getComponent(0).getTC() / TCmix * alfa0 / alfaMix;
    double P0 = phase.getPhase().getPressure() * referenceSystem.getPhase(0).getComponent(0).getPC()
        / PCmix * alfa0 / alfaMix;

    double refVisosity = getRefComponentViscosity(T0, P0);
    double viscosity = refVisosity * Math.pow(TCmix / Tc0, -1.0 / 6.0)
        * Math.pow(PCmix / Pc0, 2.0 / 3.0) * Math.pow(Mmix / M0, 0.5) * alfaMix / alfa0;
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
    referenceSystem.setPressure(pres);
    referenceSystem.init(1);
    double molDens = referenceSystem.getLowestGibbsEnergyPhase().getDensity() * 1e-3;  //[kg/L]
    double critMolDens = 162.66e-3; //[kg/L] (Source: NIST)
    double redMolDens = (molDens - critMolDens) / critMolDens;
    
    double viscRefO = 0.0;
    for (int i = 0; i < GVcoef.length; i++) {
      viscRefO += GVcoef[i] * Math.pow(temp, ((i + 1) - 4) / 3.0);
    }

    
    //Calculating the reference viscosity contributions:
    double temp1 = Math.pow(molDens, 0.1) * (viscRefJ[1] + viscRefJ[2] / Math.pow(temp, 3.0 / 2.0));
    double temp2 = redMolDens * Math.pow(molDens, 0.5)
        * (viscRefJ[4] + viscRefJ[5] / temp + viscRefJ[6] / Math.pow(temp, 2.0));
    double temp3 = Math.exp(temp1 + temp2);

    double dTfreeze = temp - 90.69;
    double HTAN =
        (Math.exp(dTfreeze) - Math.exp(-dTfreeze)) / (Math.exp(dTfreeze) + Math.exp(-dTfreeze));
    
    //This compensates for that the HTAN function is not defined for dTfreeze > 709.0:
    if (dTfreeze > 709.0) { 
      double visRefE = 1.0;
      double visRefG = 0.0;
    } else {
      double visRefE = (HTAN + 1.0) / 2.0;
      double visRefG = (1.0 - HTAN) / 2.0;
    }

    double viscRef1 = (visRefA + visRefB * Math.pow(visRefC - Math.log(temp / visRefF), 2.0)) * molDens;
    double viscRef2 = visRefE * Math.exp(viscRefJ[0] + viscRefJ[3] / temp) * (temp3 - 1.0);

    double temp4 = Math.pow(molDens, 0.1) * (viscRefK[1] + viscRefK[2] / Math.pow(temp, 3.0 / 2.0));
    double temp5 = redMolDens * Math.pow(molDens, 0.5)
        * (viscRefK[4] + viscRefK[5] / temp + viscRefK[6] / Math.pow(temp, 2.0));
    double temp6 = Math.exp(temp4 + temp5);
    double viscRef3 = visRefG * Math.exp(viscRefK[0] + viscRefK[3] / temp) * (temp6 - 1.0);

    double refVisc = (viscRefO + viscRef1 + viscRef2 + viscRef3) / 1.0e7;
    return refVisc;
  }
}
