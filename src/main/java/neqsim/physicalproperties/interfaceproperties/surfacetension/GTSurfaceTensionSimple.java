package neqsim.physicalproperties.interfaceproperties.surfacetension;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * GTSurfaceTensionSimple class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class GTSurfaceTensionSimple extends SurfaceTension {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(GTSurfaceTensionSimple.class);

  int ite_step = 200;
  SystemInterface localSystem = null;
  double[][] den_interface = null;
  double[] z_step = null;
  double[] pressure_interface = null;
  boolean calcInfluenceParameter = true;
  double[] influenceParam;
  private double[][][] dmudn2 = null;

  /**
   * <p>
   * Constructor for GTSurfaceTensionSimple.
   * </p>
   */
  public GTSurfaceTensionSimple() {}

  /**
   * <p>
   * Constructor for GTSurfaceTensionSimple.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public GTSurfaceTensionSimple(SystemInterface system) {
    super(system);
  }

  /**
   * <p>
   * calcInfluenceParameters.
   * </p>
   */
  public void calcInfluenceParameters() {
    influenceParam = new double[localSystem.getPhase(0).getNumberOfComponents()];
    for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
      influenceParam[i] = localSystem.getPhase(0).getComponent(i)
          .getSurfaceTenisionInfluenceParameter(localSystem.getPhase(0).getTemperature());
    }
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
    localSystem = system.clone();

    double surdenstemp = 0.0;
    int referenceComponentNumber = getComponentWithHighestBoilingpoint();
    // localSystem.getPhase(0).getNumberOfComponents() // - 1;
    double[] del_den_interface = new double[localSystem.getPhase(0).getNumberOfComponents()];
    double[] del_den_interface_old = new double[localSystem.getPhase(0).getNumberOfComponents()];
    double[] mu_equi = new double[localSystem.getPhase(0).getNumberOfComponents()];
    double[][][] dmudn = new double[ite_step][localSystem.getPhase(0)
        .getNumberOfComponents()][localSystem.getPhase(0).getNumberOfComponents()];
    dmudn2 = new double[ite_step][localSystem.getPhase(0).getNumberOfComponents()][localSystem
        .getPhase(0).getNumberOfComponents()];
    double[][] mu_inter = new double[ite_step][localSystem.getPhase(0).getNumberOfComponents()];
    double[] mu_times_den = new double[ite_step];
    double[][] fmatrix = new double[localSystem.getPhase(0).getNumberOfComponents()
        - 1][localSystem.getPhase(0).getNumberOfComponents() - 1];
    double[] bmatrix = new double[localSystem.getPhase(0).getNumberOfComponents() - 1];
    Matrix ans = null;
    z_step = new double[ite_step];
    den_interface = new double[ite_step][localSystem.getPhase(0).getNumberOfComponents()];
    pressure_interface = new double[ite_step];
    pressure_interface[0] = 1e5 * system.getPressure();

    if (calcInfluenceParameter) {
      calcInfluenceParameters();
    }

    for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
      mu_equi[i] = system.getPhase(interface1).getComponent(i)
          .getChemicalPotential(system.getPhase(interface1));
      den_interface[0][i] = 1e5 * system.getPhase(interface1).getComponent(i).getx()
          / system.getPhase(interface1).getMolarVolume();
      localSystem.addComponent(localSystem.getPhase(0).getComponentName(i),
          -system.getPhase(0).getComponent(i).getNumberOfmoles());
      localSystem.addComponent(localSystem.getPhase(0).getComponentName(i),
          system.getPhase(interface1).getComponent(i).getx()
              / system.getPhase(interface1).getMolarVolume());
    }

    del_den_interface[referenceComponentNumber] =
        (1e5 * system.getPhase(interface2).getComponent(referenceComponentNumber).getx()
            / system.getPhase(interface2).getMolarVolume()
            - 1e5 * system.getPhase(interface1).getComponent(referenceComponentNumber).getx()
                / system.getPhase(interface1).getMolarVolume())
            / (ite_step * 1.0);
    /*
     * System.out.println("del den ref " + system.getPhase(interface1).getComponent(0).getx() /
     * system.getPhase(interface1).getMolarVolume()); System.out.println("del den ref2 " +
     * system.getPhase(interface2).getComponent(0).getx() /
     * system.getPhase(interface2).getMolarVolume());
     *
     * System.out.println("del den ref " + system.getPhase(interface1).getComponent(1).getx() /
     * system.getPhase(interface1).getMolarVolume()); System.out.println("del den ref2 " +
     * system.getPhase(interface2).getComponent(1).getx() /
     * system.getPhase(interface2).getMolarVolume());
     */

    localSystem.init(0);
    localSystem.setUseTVasIndependentVariables(true);
    localSystem.setNumberOfPhases(1);
    localSystem.getPhase(0).setTotalVolume(1.0);
    localSystem.useVolumeCorrection(false);
    localSystem.init_x_y();
    localSystem.init(3);
    for (int j = 1; j < ite_step; j++) {
      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        mu_inter[j][i] =
            localSystem.getPhase(0).getComponent(i).getChemicalPotential(localSystem.getPhase(0));
        /*
         * if (java.lang.Double.isNaN(mu_inter[j][i])) { double chemicalPotential =
         * localSystem.getPhase(0).getComponent(i) .getChemicalPotential(localSystem.getPhase(0)); }
         */

        for (int k = 0; k < localSystem.getPhase(0).getNumberOfComponents(); k++) {
          dmudn[j][i][k] = localSystem.getPhase(0).getComponent(i).getChemicalPotentialdNTV(k,
              localSystem.getPhase(0));
        }
      }

      int ii = 0;
      int kk = 0;
      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        if (i == referenceComponentNumber) {
          continue;
        }
        bmatrix[ii] = Math.sqrt(influenceParam[referenceComponentNumber])
            * dmudn[j][i][referenceComponentNumber]
            - Math.sqrt(influenceParam[i])
                * dmudn[j][referenceComponentNumber][referenceComponentNumber];
        kk = 0;
        for (int k = 0; k < localSystem.getPhase(0).getNumberOfComponents(); k++) {
          if (k == referenceComponentNumber) {
            continue;
          }
          fmatrix[ii][kk] = Math.sqrt(influenceParam[i]) * dmudn[j][referenceComponentNumber][k]
              - Math.sqrt(influenceParam[referenceComponentNumber]) * dmudn[j][i][k];
          kk++;
        }
        ii++;
      }

      if (localSystem.getPhase(0).getNumberOfComponents() > 1) {
        Matrix fmatrixJama = new Matrix(fmatrix);
        Matrix bmatrixJama =
            new Matrix(bmatrix, localSystem.getPhase(0).getNumberOfComponents() - 1);
        try {
          ans = fmatrixJama.solveTranspose(bmatrixJama.transpose());
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }
      }

      int pp = 0;
      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        if (i != referenceComponentNumber) {
          del_den_interface[i] = ans.get(pp, 0) * del_den_interface[referenceComponentNumber];
          if (Math.abs(ans.get(pp, 0)) * del_den_interface[referenceComponentNumber]
              / den_interface[j - 1][i] > 0.1) {
            del_den_interface[i] = Math.signum(ans.get(pp, 0)) * den_interface[j - 1][i];
          }
          pp++;
        }
        del_den_interface_old[i] = 0;
      }
      double err = 1.0;
      int iterations = 0;
      while (err > 1e-15 && iterations < 1200) {
        iterations++;
        double totalDens = 0.0;
        for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
          den_interface[j][i] = den_interface[j - 1][i] + del_den_interface[i];
          totalDens += den_interface[j][i];
          localSystem.addComponent(localSystem.getPhase(0).getComponentName(i),
              (del_den_interface[i] - del_den_interface_old[i]) / 1.0e5);
          del_den_interface_old[i] = del_den_interface[i];
        }

        localSystem.init_x_y();
        localSystem.init(3);

        for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
          mu_inter[j][i] =
              localSystem.getPhase(0).getComponent(i).getChemicalPotential(localSystem.getPhase(0));
          for (int k = 0; k < localSystem.getPhase(0).getNumberOfComponents(); k++) {
            dmudn[j][i][k] = localSystem.getPhase(0).getComponent(i).getChemicalPotentialdNTV(k,
                localSystem.getPhase(0));
            dmudn2[j][i][k] = dmudn[j][i][k];
          }
        }

        ii = 0;
        for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
          if (i == referenceComponentNumber) {
            continue;
          }
          bmatrix[ii] = -Math.sqrt(influenceParam[i])
              * (mu_equi[referenceComponentNumber] - mu_inter[j][referenceComponentNumber])
              + Math.sqrt(influenceParam[referenceComponentNumber]) * (mu_equi[i] - mu_inter[j][i]);
          kk = 0;
          for (int k = 0; k < localSystem.getPhase(0).getNumberOfComponents(); k++) {
            if (k == referenceComponentNumber) {
              continue;
            }
            fmatrix[ii][kk] = -Math.sqrt(influenceParam[i]) * dmudn[j][referenceComponentNumber][k]
                + Math.sqrt(influenceParam[referenceComponentNumber]) * dmudn[j][i][k];
            kk++;
          }
          ii++;
        }
        RealMatrix ans2 = null;
        RealMatrix bRealMatrix = new Array2DRowRealMatrix(bmatrix);
        if (localSystem.getPhase(0).getNumberOfComponents() > 1) {
          // BigMatrixImpl fmatrixJama = new BigMatrixImpl(fmatrix);
          RealMatrix fmatrixJama = new Array2DRowRealMatrix(fmatrix);
          // Matrix fmatrixJama = new Matrix(fmatrix);
          // BigMatrixImpl bmatrixJama = new BigMatrixImpl(bmatrix);
          // Matrix bmatrixJama = new Matrix(bmatrix,
          // localSystem.getPhase(0).getNumberOfComponents() - 1);
          try {
            // ans = fmatrixJama.solveTranspose(bmatrixJama.transpose());
            // ans2 = new BigMatrixImpl(fmatrixJama.solve(bmatrix));
            DecompositionSolver solver1 =
                new org.apache.commons.math3.linear.LUDecomposition(fmatrixJama).getSolver();
            ans2 = solver1.solve(bRealMatrix);
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
          }
        }

        pp = 0;
        err = 0.0;
        for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
          if (i != referenceComponentNumber) {
            err += Math.abs(ans2.getEntry(pp, 0) * 1e5) / totalDens;
            del_den_interface[i] += 1e5 * ans2.getEntry(pp, 0);
            // * (iterations) / (10.0 + iterations);
            pp++;
          }
        }
      }
      // System.out.println("err " + err);

      pressure_interface[j] = 1e5 * localSystem.getPhase(0).getPressure();
      mu_times_den[j] = 0.0;
      double kappa = 0.0;
      double kappai = 0.0;
      double kappak = 0.0;
      double interact = 1.0;
      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        double infli = influenceParam[i];
        // localSystem.getPhase(0).getComponent(i).getSurfaceTenisionInfluenceParameter(localSystem.getPhase(0).getTemperature());
        kappai = del_den_interface[i] / del_den_interface[referenceComponentNumber];
        mu_times_den[j] += den_interface[j][i] * (mu_inter[j][i] - mu_equi[i]);
        for (int k = 0; k < localSystem.getPhase(0).getNumberOfComponents(); k++) {
          if ((localSystem.getPhase(0).getComponentName(i).equals("water")
              || localSystem.getPhase(0).getComponentName(k).equals("water")) && i != k) {
            interact = 0.0;
          } else {
            interact = 0.0;
          }
          double inflk = influenceParam[k];
          // localSystem.getPhase(0).getComponent(k).getSurfaceTenisionInfluenceParameter(localSystem.getPhase(0).getTemperature());
          kappak = del_den_interface[k] / del_den_interface[referenceComponentNumber];
          kappa += Math.sqrt(infli * inflk) * kappai * kappak * (1.0 - interact);
        }
      }
      mu_times_den[j] += -(pressure_interface[j] - pressure_interface[0]);
      z_step[j] = z_step[j - 1] + Math.sqrt(kappa / (2.0 * mu_times_den[j]))
          * del_den_interface[referenceComponentNumber];
      if (Double.isNaN(z_step[j])) {
        break;
      }
      surdenstemp +=
          Math.sqrt(2.0 * kappa * mu_times_den[j]) * del_den_interface[referenceComponentNumber];
      // * thermo.ThermodynamicConstantsInterface.avagadroNumber;
    }

    // System.out.println("del den ref " +
    // localSystem.getPhase(interface1).getComponent(0).getx() /
    // localSystem.getPhase(interface1).getMolarVolume() );

    for (int j = 0; j < ite_step; j++) {
      // System.out.println("z " + z_step[j] + " density " + j + " " +
      // den_interface[j][0] + " mu_times_den[j] " + mu_times_den[j]+ "
      // pressure_interface[j] " + pressure_interface[j] + " "+
      // pressure_interface[0]); // + " " + den_interface[j][1] + " " +
      // den_interface[j][0] / den_interface[j][1]);
    }

    // System.out.println("end ");
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

  /**
   * <p>
   * getInfluenceParameter.
   * </p>
   *
   * @param interfaceTension a double
   * @param componentNumber a int
   * @return a double
   */
  public double getInfluenceParameter(double interfaceTension, int componentNumber) {
    // double startGuess = calcSurfaceTension(0, 1);
    double oldInfluenceParameter = influenceParam[componentNumber];
    double calcVal = 0.0;
    double oldCalcVal = 0.0;
    double dSurfTensdinfluence = 0.0;
    int iter = 0;
    calcInfluenceParameter = true;
    calcVal = calcSurfaceTension(0, 1) - interfaceTension;
    do {
      iter++;
      oldCalcVal = calcVal;
      // System.out.println("surface tenison " + calcSurfaceTension(0, 1) + " error "
      // + calcVal + " influenceParam " + influenceParam[componentNumber]);
      calcInfluenceParameter = false;
      if (iter > 1) {
        influenceParam[componentNumber] -= (calcVal) / dSurfTensdinfluence;
      } else {
        influenceParam[componentNumber] *= 1.01;
      }
      calcVal = calcSurfaceTension(0, 1) - interfaceTension;

      dSurfTensdinfluence =
          (calcVal - oldCalcVal) / (influenceParam[componentNumber] - oldInfluenceParameter);
      oldInfluenceParameter = influenceParam[componentNumber];
    } while (Math.abs(calcVal / interfaceTension) > 1e-8 && iter < 100);
    calcInfluenceParameter = true;
    return influenceParam[componentNumber];
  }

  /**
   * <p>
   * Getter for the field <code>dmudn2</code>.
   * </p>
   *
   * @return the dmudn2
   */
  public double[][][] getDmudn2() {
    return dmudn2;
  }

  /**
   * <p>
   * Setter for the field <code>dmudn2</code>.
   * </p>
   *
   * @param dmudn2 the dmudn2 to set
   */
  public void setDmudn2(double[][][] dmudn2) {
    this.dmudn2 = dmudn2;
  }
}
