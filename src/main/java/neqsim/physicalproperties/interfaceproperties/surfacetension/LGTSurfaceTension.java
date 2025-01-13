package neqsim.physicalproperties.interfaceproperties.surfacetension;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * LGTSurfaceTension class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class LGTSurfaceTension extends SurfaceTension {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int ite_step = 200;
  SystemInterface localSystem = null;
  double[][] den_interface = null;
  double[] z_step = null;
  double[] pressure_interface = null;

  /**
   * <p>
   * Constructor for LGTSurfaceTension.
   * </p>
   */
  public LGTSurfaceTension() {}

  /**
   * <p>
   * Constructor for LGTSurfaceTension.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public LGTSurfaceTension(SystemInterface system) {
    super(system);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Using the Gradient Theory for mixtures Units: N/m
   * </p>
   */
  @Override
  public double calcSurfaceTension(int interface1, int interface2) {
    double surdenstemp = 0.0;
    localSystem = system.clone();

    int referenceComponentNumber = getComponentWithHighestBoilingpoint();
    // double influenceParamReferenceComponent =
    // localSystem.getPhase(0).getComponent(referenceComponentNumber).getSurfaceTenisionInfluenceParameter(localSystem.getPhase(0).getTemperature());
    double pressure_equi = 1e5 * system.getPressure();

    den_interface = new double[ite_step][localSystem.getPhase(0).getNumberOfComponents()];
    pressure_interface = new double[ite_step];
    pressure_interface[0] = pressure_equi;
    double[] del_den_interface = new double[localSystem.getPhase(0).getNumberOfComponents()];
    double[] del_den_interface_old = new double[localSystem.getPhase(0).getNumberOfComponents()];

    double[] mu_equi = new double[localSystem.getPhase(0).getNumberOfComponents()];
    double[][][] dmudn = new double[ite_step][localSystem.getPhase(0)
        .getNumberOfComponents()][localSystem.getPhase(0).getNumberOfComponents()];

    double[][] mu_inter = new double[ite_step][localSystem.getPhase(0).getNumberOfComponents()];
    // double[][][] dmudn_equi = new
    // double[ite_step][localSystem.getPhase(0).getNumberOfComponents()][localSystem.getPhase(0).getNumberOfComponents()];

    double[] mu_times_den = new double[ite_step];
    z_step = new double[ite_step];

    for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
      mu_equi[i] = system.getPhase(interface1).getComponent(i)
          .getChemicalPotential(system.getPhase(interface1));
      den_interface[0][i] = 1e5 * system.getPhase(interface1).getComponent(i).getx()
          / system.getPhase(interface1).getMolarVolume();
      den_interface[ite_step - 1][i] = 1e5 * system.getPhase(interface2).getComponent(i).getx()
          / system.getPhase(interface2).getMolarVolume();
      del_den_interface[i] = (1e5 * system.getPhase(interface2).getComponent(i).getx()
          / system.getPhase(interface2).getMolarVolume()
          - 1e5 * system.getPhase(interface1).getComponent(i).getx()
              / system.getPhase(interface1).getMolarVolume())
          / (ite_step * 1.0);
      del_den_interface_old[i] = 0.0;
      localSystem.addComponent(localSystem.getPhase(0).getComponentName(i),
          -system.getPhase(0).getComponent(i).getNumberOfmoles());
      localSystem.addComponent(localSystem.getPhase(0).getComponentName(i),
          system.getPhase(interface1).getComponent(i).getx()
              / system.getPhase(interface1).getMolarVolume());
    }

    localSystem.init(0);
    localSystem.setUseTVasIndependentVariables(true);
    localSystem.setNumberOfPhases(1);
    localSystem.getPhase(0).setTotalVolume(1.0);
    localSystem.useVolumeCorrection(false);
    localSystem.initBeta();
    localSystem.init_x_y();
    localSystem.init(3);

    // localSystem.display();
    // System.out.println("inerface1 + " + interface1 + " pressure " +
    // localSystem.getPressure());

    for (int j = 1; j < ite_step; j++) {
      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        del_den_interface[i] = (1e5 * system.getPhase(interface2).getComponent(i).getx()
            / system.getPhase(interface2).getMolarVolume()
            - 1e5 * system.getPhase(interface1).getComponent(i).getx()
                / system.getPhase(interface1).getMolarVolume())
            / (ite_step * 1.0);
        del_den_interface_old[i] = 0.0;
      }

      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        den_interface[j][i] = den_interface[j - 1][i] + del_den_interface[i];
        localSystem.addComponent(localSystem.getPhase(0).getComponentName(i),
            (del_den_interface[i] - del_den_interface_old[i]) / 1.0e5);
        del_den_interface_old[i] = del_den_interface[i];
      }

      localSystem.init_x_y();
      localSystem.init(3);
      // localSystem.init(3); //need to be fixed
      // System.out.println("pressure " + localSystem.getPressure());
      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        mu_inter[j][i] =
            localSystem.getPhase(0).getComponent(i).getChemicalPotential(localSystem.getPhase(0));
        for (int k = 0; k < localSystem.getPhase(0).getNumberOfComponents(); k++) {
          dmudn[j][i][k] = localSystem.getPhase(0).getComponent(i).getChemicalPotentialdNTV(k,
              localSystem.getPhase(0));
        }
      }

      pressure_interface[j] = 1e5 * localSystem.getPhase(0).getPressure();

      mu_times_den[j] = 0.0;
      double kappa = 0.0;
      double kappai = 0.0;
      double kappak = 0.0;
      double interact = 1.0;
      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        double infli = localSystem.getPhase(0).getComponent(i)
            .getSurfaceTenisionInfluenceParameter(localSystem.getPhase(0).getTemperature());

        if (i == referenceComponentNumber) {
          kappai = 1.0;
        } else {
          kappai = del_den_interface[i] / del_den_interface[referenceComponentNumber];
        }

        mu_times_den[j] += den_interface[j][i] * (mu_inter[j][i] - mu_equi[i]);
        for (int k = 0; k < localSystem.getPhase(0).getNumberOfComponents(); k++) {
          if ((localSystem.getPhase(0).getComponentName(i).equals("water")
              || localSystem.getPhase(0).getComponentName(k).equals("water")) && i != k) {
            if ((localSystem.getPhase(0).getComponentName(i).equals("MEG")
                || localSystem.getPhase(0).getComponentName(k).equals("MEG")) && i != k) {
              interact = 0.2;
            } else {
              interact = 0.35;
            }
          } else {
            interact = 0.0;
          }

          double inflk = localSystem.getPhase(0).getComponent(k)
              .getSurfaceTenisionInfluenceParameter(localSystem.getPhase(0).getTemperature());

          if (k == referenceComponentNumber) {
            kappak = 1.0;
          } else {
            kappak = del_den_interface[k] / del_den_interface[referenceComponentNumber];
          }

          kappa += Math.sqrt(infli * inflk) * kappai * kappak * (1.0 - interact);
        }
      }
      mu_times_den[j] += -(pressure_interface[j] - pressure_equi);
      z_step[j] = z_step[j - 1] + Math.sqrt(kappa / (2.0 * mu_times_den[j]))
          * del_den_interface[referenceComponentNumber];
      if (Double.isNaN(z_step[j])) {
        break;
      }
      surdenstemp +=
          Math.sqrt(2.0 * kappa * mu_times_den[j]) * del_den_interface[referenceComponentNumber];
      // thermo.ThermodynamicConstantsInterface.avagadroNumber;
      // System.out.println("surdenstemp " + surdenstemp + " kappa " + kappa + "
      // mu_times_den[j] " + mu_times_den[j] + " z " + z_step[j]);
    }

    for (int j = 0; j < ite_step; j++) {
      // System.out.println("z " + z_step[j] + " density " + j + " " +
      // den_interface[j][0] + " " + den_interface[j][1] + " " + den_interface[j][0] /
      // den_interface[j][1]);
    }

    return Math.abs(surdenstemp);
  }

  /**
   * <p>
   * getMolarDensity.
   * </p>
   *
   * @param compnum a int
   * @return an array of type double
   */
  public double[] getMolarDensity(int compnum) {
    double[] temp = new double[ite_step];
    for (int i = 0; i < ite_step; i++) {
      temp[i] = den_interface[i][compnum];
    }
    return temp;
  }

  /**
   * <p>
   * getMolarDensityTotal.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getMolarDensityTotal() {
    double[] temp = new double[ite_step];
    for (int i = 0; i < ite_step; i++) {
      for (int j = 0; j < system.getPhase(0).getNumberOfComponents(); j++) {
        temp[i] += den_interface[i][j];
      }
    }
    return temp;
  }

  /**
   * <p>
   * getz.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getz() {
    return z_step;
  }

  /**
   * <p>
   * getPressure.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getPressure() {
    return pressure_interface;
  }
}
