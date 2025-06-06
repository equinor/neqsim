package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;

public class MethaneViscosityMethod extends Viscosity {
  /**
   * <p>
   * Constructor for MethaneViscosityMethod.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public MethaneViscosityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {

    // Check if there are other components than methane
    if (phase.getPhase().getNumberOfComponents() > 1
        || !phase.getPhase().getComponent(0).getName().equalsIgnoreCase("methane")) {
      throw new Error("Methane viscosity model only supports PURE METHANE.");
    }

    // The following is exactly the same as LBCViscosityMethod
    double[] a = {0.10230, 0.023364, 0.058533, -0.040758, 0.0093324};

    double T = phase.getPhase().getTemperature();
    double P = phase.getPhase().getPressure() / 10.0; // [MPa]

    double lowPresVisc = 0.0;
    double temp = 0.0;
    double temp2 = 0.0;
    double temp3 = 0.0;
    double temp4 = 0.0;
    double critDens = 0.0;
    double par1 = 0.0;
    double par2 = 0.0;
    double par3 = 0.0;
    double par4 = 0.0;
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      par1 += phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(i).getTC();
      par2 += phase.getPhase().getComponent(i).getx()
          * phase.getPhase().getComponent(i).getMolarMass() * 1000.0;
      par3 += phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(i).getPC();
      par4 += phase.getPhase().getComponent(i).getx()
          * phase.getPhase().getComponent(i).getCriticalVolume();
      double TR = phase.getPhase().getTemperature() / phase.getPhase().getComponent(i).getTC();
      temp2 = Math.pow(phase.getPhase().getComponent(i).getTC(), 1.0 / 6.0)
          / (Math.pow(phase.getPhase().getComponent(i).getMolarMass() * 1000.0, 1.0 / 2.0)
              * Math.pow(phase.getPhase().getComponent(i).getPC(), 2.0 / 3.0));
      temp = TR < 1.5 ? 34.0e-5 * 1.0 / temp2 * Math.pow(TR, 0.94)
          : 17.78e-5 * 1.0 / temp2 * Math.pow(4.58 * TR - 1.67, 5.0 / 8.0);

      temp3 += phase.getPhase().getComponent(i).getx() * temp
          * Math.pow(phase.getPhase().getComponent(i).getMolarMass() * 1000.0, 1.0 / 2.0);
      temp4 += phase.getPhase().getComponent(i).getx()
          * Math.pow(phase.getPhase().getComponent(i).getMolarMass() * 1000.0, 1.0 / 2.0);
    }

    lowPresVisc = temp3 / temp4;
    // logger.info("LP visc " + lowPresVisc);
    critDens = 1.0 / par4; // mol/cm3
    double eps =
        Math.pow(par1, 1.0 / 6.0) * Math.pow(par2, -1.0 / 2.0) * Math.pow(par3, -2.0 / 3.0);
    double reducedDensity = phase.getPhase().getPhysicalProperties().getDensity()
        / phase.getPhase().getMolarMass() / critDens / 1000000.0;
    // System.out.println("reduced density " + reducedDensity);
    double numb = a[0] + a[1] * reducedDensity + a[2] * Math.pow(reducedDensity, 2.0)
        + a[3] * Math.pow(reducedDensity, 3.0) + a[4] * Math.pow(reducedDensity, 4.0);
    double viscosity_LBC = (-Math.pow(10.0, -4.0) + Math.pow(numb, 4.0)) / eps + lowPresVisc;
    viscosity_LBC /= 1.0e3;
    // System.out.println("visc " + viscosity);

    // HERE ARE THE CORRECTION TERMS:
    double term_A = 0.0; // Declaring the variable so that it can be changed
    if (T >= 345) {
      term_A = 1.1 * Math.pow(T / 345 - 1, 1.2);
    } else {
      term_A = 0.64 * (T / 345 - 1)
          * (1 - 0.4 * Math.exp(-Math.pow(T - 298.15, 2) / 100) / (1 + Math.exp(-(P - 21))));
    }

    double A = Math.pow(10, -6) * (term_A + 0.27 * Math.exp(-Math.pow(T - 430, 2) / 9000)
        * Math.exp(-Math.pow(P - 21, 2) / 35) / (1 + Math.exp(-(P - 15))));
    double B = Math.pow(10, -8)
        * (1.2 * Math.pow(300 / T, 3) * Math.pow(P, 1.2) / (1 + Math.exp(-0.6 * (P - 20)))
            + 30 * (1 - T / 400) * Math.exp(-Math.pow(P - 15, 2) / 20)
            + 13 * Math.exp(-Math.pow(P - 12.8, 2) / 7));
    double C = Math.pow(10, -8) * (1 / (1 + Math.exp((P - 15)))
        * (2 * P * Math.exp(-Math.pow(T - 375, 2) / 10000) + 8.0 * (P / 4 - 1))
        + 10.0 * Math.exp(-Math.pow(T - 260, 2) / 300) / (1 + Math.exp(0.9 * (P - 12))));
    double D = Math.pow(10, -6) * (0.62 * (1 - T / 430) * 1 / (1 + Math.exp(-0.5 * (P - 26)))
        + 0.306 * Math.exp(-Math.pow(T - 270, 2) / 50) / (1 + Math.exp(-(P - 25)))
        - (265.2 / T) * Math.exp(-Math.pow(T - 270, 2) / 160) / (1 + Math.exp(-0.5 * (P - 25))));

    double eta = viscosity_LBC + A - B + C - D;

    return eta;
  }
}
