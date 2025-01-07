/*
 * ChungViscosityMethod.java
 *
 * Created on 1. august 2001, 12:44
 */

package neqsim.physicalproperties.methods.gasphysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * ChungViscosityMethod class.
 * </p>
 *
 * @author esol
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class ChungViscosityMethod extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  public double[] pureComponentViscosity;

  public double[] relativeViscosity;

  public double[] Fc;

  public double[] omegaVisc;

  protected double[] chungE = new double[10];
  protected double[][] chungHPcoefs =
      {{6.324, 50.412, -51.680, 1189.0}, {1.210e-3, -1.154e-3, -6.257e-3, 0.03728},
          {5.283, 254.209, -168.48, 3898.0}, {6.623, 38.096, -8.464, 31.42},
          {19.745, 7.630, -14.354, 31.53}, {-1.9, -12.537, 4.985, -18.15},
          {24.275, 3.450, -11.291, 69.35}, {0.7972, 1.117, 0.01235, -4.117},
          {-0.2382, 0.06770, -0.8163, 4.025}, {0.06863, 0.3479, 0.5926, -0.727}};

  /**
   * <p>
   * Constructor for ChungViscosityMethod.
   * </p>
   *
   * @param gasPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public ChungViscosityMethod(PhysicalProperties gasPhase) {
    super(gasPhase);
    pureComponentViscosity = new double[gasPhase.getPhase().getNumberOfComponents()];
    relativeViscosity = new double[gasPhase.getPhase().getNumberOfComponents()];
    Fc = new double[gasPhase.getPhase().getNumberOfComponents()];
    omegaVisc = new double[gasPhase.getPhase().getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    // Wilkes method p. 407 TPoLG
    initChungPureComponentViscosity();
    double tempVar = 0;
    double tempVar2 = 0;
    double viscosity = 0;

    for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
      tempVar = 0;
      for (int j = 0; j < gasPhase.getPhase().getNumberOfComponents(); j++) {
        tempVar2 = Math
            .pow(1.0 + Math.sqrt(pureComponentViscosity[i] / pureComponentViscosity[j])
                * Math.pow(gasPhase.getPhase().getComponent(j).getMolarMass()
                    / gasPhase.getPhase().getComponent(i).getMolarMass(), 0.25),
                2.0)
            / Math.pow(8.0 * (1.0 + gasPhase.getPhase().getComponent(i).getMolarMass()
                / gasPhase.getPhase().getComponent(j).getMolarMass()), 0.5);
        tempVar += gasPhase.getPhase().getComponent(j).getx() * tempVar2;
      }

      viscosity += gasPhase.getPhase().getComponent(i).getx() * pureComponentViscosity[i] / tempVar;
    }
    viscosity *= 1.0e-7; // N-sek/m2
    return viscosity;
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentViscosity(int i) {
    return pureComponentViscosity[i];
  }

  /**
   * <p>
   * initChungPureComponentViscosity.
   * </p>
   */
  public void initChungPureComponentViscosity() {
    double tempVar = 0;
    double A = 1.16145;

    double B = 0.14874;
    double C = 0.52487;
    double D = 0.77320;
    double E = 2.16178;
    double F = 2.43787;
    double chungy = 0;

    double chungG1 = 0;
    double chungG2 = 0;
    double chungviskstartstar = 0;
    double chungviskstar = 0;
    for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
      // eq. 9-4.11 TPoLG
      relativeViscosity[i] = 131.3 * gasPhase.getPhase().getComponent(i).getDebyeDipoleMoment()
          / Math.sqrt(gasPhase.getPhase().getComponent(i).getCriticalVolume()
              * gasPhase.getPhase().getComponent(i).getTC());
      // eq. 9-4.10 TPoLG
      Fc[i] = 1.0 - 0.2756 * gasPhase.getPhase().getComponent(i).getAcentricFactor()
          + 0.059035 * Math.pow(relativeViscosity[i], 4)
          + gasPhase.getPhase().getComponent(i).getViscosityCorrectionFactor();

      for (int j = 0; j < 10; j++) {
        // Table 9-5 TPoLG
        chungE[j] = chungHPcoefs[j][0]
            + chungHPcoefs[j][1] * gasPhase.getPhase().getComponent(i).getAcentricFactor()
            + chungHPcoefs[j][2] * Math.pow(relativeViscosity[i], 4) + chungHPcoefs[j][3]
                * gasPhase.getPhase().getComponent(i).getViscosityCorrectionFactor();
      }

      // eq. 9-4.8 TPoLG (The properties of Liquids and Gases)
      tempVar = 1.2593 * gasPhase.getPhase().getTemperature()
          / gasPhase.getPhase().getComponent(i).getTC();
      // eq. 9.4.3 TPoLG
      omegaVisc[i] =
          A * Math.pow(tempVar, -B) + C * Math.exp(-D * tempVar) + E * Math.exp(-F * tempVar);
      // eq. 9-6.18 TPoLG
      chungy = 0.1 / (gasPhase.getPhase().getMolarVolume())
          * gasPhase.getPhase().getComponent(i).getCriticalVolume() / 6.0;
      // eq. 9-6.19 TPoLG
      chungG1 = (1.0 - 0.5 * chungy) / Math.pow((1.0 - chungy), 3.0);
      // eq. 9-6.20 TPoLG
      chungG2 = (chungE[0] * (((1.0 - Math.exp(-chungE[3] * chungy)) / chungy))
          + chungE[1] * chungG1 * Math.exp(chungE[4] * chungy) + chungE[2] * chungG1)
          / (chungE[0] * chungE[3] + chungE[1] + chungE[2]);
      // eq. 9-6.21 TPoLG
      chungviskstartstar = chungE[6] * chungy * chungy * chungG2
          * Math.exp(chungE[7] + chungE[8] / tempVar + chungE[9] * Math.pow(tempVar, -2.0));
      // eq. 9-6.17 TPoLG
      chungviskstar =
          Math.sqrt(tempVar) / omegaVisc[i] * (Fc[i] * (1.0 / chungG2 + chungE[5] * chungy))
              + chungviskstartstar;
      pureComponentViscosity[i] = chungviskstar * 36.344
          * Math.pow(gasPhase.getPhase().getComponent(i).getMolarMass() * 1000.0
              * gasPhase.getPhase().getComponent(i).getTC(), 0.5)
          / (Math.pow(gasPhase.getPhase().getPhase().getComponent(i).getCriticalVolume(),
              2.0 / 3.0));
    }
  }
}
