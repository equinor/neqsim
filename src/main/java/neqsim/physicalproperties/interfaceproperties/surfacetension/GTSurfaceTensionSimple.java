package neqsim.physicalproperties.interfaceproperties.surfacetension;

import neqsim.mathlib.linearalgebra.ApacheCommonsMathLinearAlgebra;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.thermo.system.SystemInterface;

/**
 * GTSurfaceTensionSimple class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class GTSurfaceTensionSimple extends SurfaceTension {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private static final LinearAlgebraOperations ALGEBRA = new EjmlLinearAlgebra();
  private static final LinearAlgebraOperations COMMONS_MATH = new ApacheCommonsMathLinearAlgebra();

  /** True only after every integration step succeeds. */
  private boolean profileComplete;

  /** Relative density-update tolerance, above round-off in chemical-potential derivatives. */
  private static final double NEWTON_TOLERANCE = 1e-12;

  int ite_step = 200;
  SystemInterface localSystem = null;
  double[][] den_interface = null;
  double[] z_step = null;
  double[] pressure_interface = null;
  boolean calcInfluenceParameter = true;
  double[] influenceParam;
  private double[][][] dmudn2 = null;

  /**
   * Constructor for GTSurfaceTensionSimple.
   */
  public GTSurfaceTensionSimple() {
  }

  /**
   * Constructor for GTSurfaceTensionSimple.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public GTSurfaceTensionSimple(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public GTSurfaceTensionSimple copyForSystem(SystemInterface target) {
    GTSurfaceTensionSimple copy = (GTSurfaceTensionSimple) super.copyForSystem(target);
    copy.influenceParam = influenceParam == null ? null : influenceParam.clone();
    return copy;
  }

  /**
   * calcInfluenceParameters.
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
   * Computes a complete gradient-theory profile in N/m. If integration leaves the physical domain, a linear solve
   * fails, or convergence is not reached, discards the profile and logs a warning before using the default correlation
   * for the phase pair. Profile getters are unavailable after fallback; no partial integral is returned.
   * </p>
   */
  @Override
  public double calcSurfaceTension(int interface1, int interface2) {
    clearProfile();
    if (!validateInterface(interface1, interface2)) {
      return 0.0;
    }
    try {
      double tension = validateResult(integrateProfile(interface1, interface2));
      profileComplete = true;
      return tension;
    } catch (RuntimeException failure) {
      clearProfile();
      return correlationFallback(interface1, interface2, failure);
    }
  }

  /**
   * Integrates every density step, rejecting invalid intermediate states.
   *
   * @param interface1 first bulk phase index
   * @param interface2 second bulk phase index
   * @return complete surface-tension integral in N/m
   * @throws IllegalStateException if a profile step is invalid or does not converge
   */
  private double integrateProfile(int interface1, int interface2) {
    if (ite_step < 2) {
      throw new IllegalStateException("At least two density-profile grid points are required");
    }
    localSystem = system.clone();

    double surdenstemp = 0.0;
    int referenceComponentNumber = getReferenceComponent(interface1, interface2);
    // localSystem.getPhase(0).getNumberOfComponents() // - 1;
    double[] del_den_interface = new double[localSystem.getPhase(0).getNumberOfComponents()];
    double[] del_den_interface_old = new double[localSystem.getPhase(0).getNumberOfComponents()];
    double[] mu_equi = new double[localSystem.getPhase(0).getNumberOfComponents()];
    double[][][] dmudn = new double[ite_step][localSystem.getPhase(0).getNumberOfComponents()][localSystem.getPhase(0)
        .getNumberOfComponents()];
    dmudn2 = new double[ite_step][localSystem.getPhase(0).getNumberOfComponents()][localSystem.getPhase(0)
        .getNumberOfComponents()];
    double[][] mu_inter = new double[ite_step][localSystem.getPhase(0).getNumberOfComponents()];
    double[] mu_times_den = new double[ite_step];
    double[][] fmatrix = new double[localSystem.getPhase(0).getNumberOfComponents()
        - 1][localSystem.getPhase(0).getNumberOfComponents() - 1];
    double[] bmatrix = new double[localSystem.getPhase(0).getNumberOfComponents() - 1];
    double[] ans = null;
    z_step = new double[ite_step];
    den_interface = new double[ite_step][localSystem.getPhase(0).getNumberOfComponents()];
    pressure_interface = new double[ite_step];
    pressure_interface[0] = 1e5 * system.getPressure();

    if (calcInfluenceParameter) {
      calcInfluenceParameters();
    }

    for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
      mu_equi[i] = system.getPhase(interface1).getComponent(i).getChemicalPotential(system.getPhase(interface1));
      GTSurfaceTensionUtils.requireFinite(mu_equi[i], "bulk chemical potential");
      den_interface[0][i] = 1e5 * system.getPhase(interface1).getComponent(i).getx()
          / system.getPhase(interface1).getMolarVolume();
      localSystem.addComponent(localSystem.getPhase(0).getComponent(i).getName(),
          -system.getPhase(0).getComponent(i).getNumberOfmoles());
      localSystem.addComponent(localSystem.getPhase(0).getComponent(i).getName(),
          system.getPhase(interface1).getComponent(i).getx() / system.getPhase(interface1).getMolarVolume());
    }

    del_den_interface[referenceComponentNumber] = (1e5
        * system.getPhase(interface2).getComponent(referenceComponentNumber).getx()
        / system.getPhase(interface2).getMolarVolume()
        - 1e5 * system.getPhase(interface1).getComponent(referenceComponentNumber).getx()
            / system.getPhase(interface1).getMolarVolume())
        / (ite_step * 1.0);
    /*
     * System.out.println("del den ref " + system.getPhase(interface1).getComponent(0).getx() /
     * system.getPhase(interface1).getMolarVolume()); System.out.println("del den ref2 " +
     * system.getPhase(interface2).getComponent(0).getx() / system.getPhase(interface2).getMolarVolume());
     *
     * System.out.println("del den ref " + system.getPhase(interface1).getComponent(1).getx() /
     * system.getPhase(interface1).getMolarVolume()); System.out.println("del den ref2 " +
     * system.getPhase(interface2).getComponent(1).getx() / system.getPhase(interface2).getMolarVolume());
     */

    localSystem.init(0);
    localSystem.setUseTVasIndependentVariables(true);
    localSystem.setNumberOfPhases(1);
    localSystem.getPhase(0).setTotalVolume(1.0);
    localSystem.useVolumeCorrection(false);
    localSystem.init_x_y();
    localSystem.init(3);
    GTSurfaceTensionUtils.validateState(localSystem.getPhase(0));
    for (int j = 1; j < ite_step; j++) {
      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        mu_inter[j][i] = localSystem.getPhase(0).getComponent(i).getChemicalPotential(localSystem.getPhase(0));
        GTSurfaceTensionUtils.requireFinite(mu_inter[j][i], "chemical potential");
        for (int k = 0; k < localSystem.getPhase(0).getNumberOfComponents(); k++) {
          dmudn[j][i][k] = localSystem.getPhase(0).getComponent(i).getChemicalPotentialdNTV(k, localSystem.getPhase(0));
          GTSurfaceTensionUtils.requireFinite(dmudn[j][i][k], "chemical-potential derivative");
        }
      }

      int ii = 0;
      int kk = 0;
      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        if (i == referenceComponentNumber) {
          continue;
        }
        bmatrix[ii] = Math.sqrt(influenceParam[referenceComponentNumber]) * dmudn[j][i][referenceComponentNumber]
            - Math.sqrt(influenceParam[i]) * dmudn[j][referenceComponentNumber][referenceComponentNumber];
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
        ans = ALGEBRA.solve(ALGEBRA.transpose(fmatrix), bmatrix);
        for (double update : ans) {
          GTSurfaceTensionUtils.requireFinite(update, "density-gradient update");
        }
      }

      int pp = 0;
      for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
        if (i != referenceComponentNumber) {
          del_den_interface[i] = ans[pp] * del_den_interface[referenceComponentNumber];
          if (Math.abs(ans[pp]) * del_den_interface[referenceComponentNumber] / den_interface[j - 1][i] > 0.1) {
            del_den_interface[i] = Math.signum(ans[pp]) * den_interface[j - 1][i];
          }
          pp++;
        }
        del_den_interface_old[i] = 0;
      }
      double err = 1.0;
      int iterations = 0;
      while (err > NEWTON_TOLERANCE && iterations < 1200) {
        iterations++;
        double totalDens = 0.0;
        for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
          den_interface[j][i] = den_interface[j - 1][i] + del_den_interface[i];
          totalDens += den_interface[j][i];
        }
        GTSurfaceTensionUtils.validateDensities(den_interface[j]);
        for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
          localSystem.addComponent(localSystem.getPhase(0).getComponent(i).getName(),
              (del_den_interface[i] - del_den_interface_old[i]) / 1.0e5);
          del_den_interface_old[i] = del_den_interface[i];
        }

        localSystem.init_x_y();
        localSystem.init(3);
        GTSurfaceTensionUtils.validateState(localSystem.getPhase(0));

        for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
          mu_inter[j][i] = localSystem.getPhase(0).getComponent(i).getChemicalPotential(localSystem.getPhase(0));
          GTSurfaceTensionUtils.requireFinite(mu_inter[j][i], "chemical potential");
          for (int k = 0; k < localSystem.getPhase(0).getNumberOfComponents(); k++) {
            dmudn[j][i][k] = localSystem.getPhase(0).getComponent(i).getChemicalPotentialdNTV(k,
                localSystem.getPhase(0));
            GTSurfaceTensionUtils.requireFinite(dmudn[j][i][k], "chemical-potential derivative");
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
        double[] ans2 = null;
        if (localSystem.getPhase(0).getNumberOfComponents() > 1) {
          ans2 = COMMONS_MATH.solve(fmatrix, bmatrix);
          for (double update : ans2) {
            GTSurfaceTensionUtils.requireFinite(update, "Newton update");
          }
        }

        pp = 0;
        err = 0.0;
        for (int i = 0; i < localSystem.getPhase(0).getNumberOfComponents(); i++) {
          if (i != referenceComponentNumber) {
            err += Math.abs(ans2[pp] * 1e5) / totalDens;
            del_den_interface[i] += 1e5 * ans2[pp];
            // * (iterations) / (10.0 + iterations);
            pp++;
          }
        }
      }
      if (!Double.isFinite(err) || err > NEWTON_TOLERANCE) {
        throw new IllegalStateException("Gradient-theory Newton iteration did not converge at density step " + j);
      }

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
          if ((localSystem.getPhase(0).getComponent(i).getName().equals("water")
              || localSystem.getPhase(0).getComponent(k).getName().equals("water")) && i != k) {
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
      z_step[j] = z_step[j - 1]
          + Math.sqrt(kappa / (2.0 * mu_times_den[j])) * del_den_interface[referenceComponentNumber];
      if (!Double.isFinite(kappa) || kappa <= 0.0 || !Double.isFinite(mu_times_den[j]) || mu_times_den[j] <= 0.0
          || !Double.isFinite(z_step[j])) {
        throw new IllegalStateException("Invalid gradient-theory integrand at density step " + j);
      }
      surdenstemp += Math.sqrt(2.0 * kappa * mu_times_den[j]) * del_den_interface[referenceComponentNumber];
      GTSurfaceTensionUtils.requireFinite(surdenstemp, "surface-tension integral");
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
    GTSurfaceTensionUtils.validateEndpoint(system, interface1, interface2, den_interface[ite_step - 1], ite_step);
    return Math.abs(surdenstemp);
  }

  /**
   * getMolarDensity.
   *
   * @param compnum a int
   * @return an array of type double
   * @throws IllegalStateException if the latest calculation did not complete a density profile
   */
  public double[] getMolarDensity(int compnum) {
    requireCompleteProfile();
    double[] temp = new double[ite_step];
    for (int i = 0; i < ite_step; i++) {
      temp[i] = den_interface[i][compnum];
    }
    return temp;
  }

  /**
   * getMolarDensityTotal.
   *
   * @return an array of type double
   * @throws IllegalStateException if the latest calculation did not complete a density profile
   */
  public double[] getMolarDensityTotal() {
    requireCompleteProfile();
    double[] temp = new double[ite_step];
    for (int i = 0; i < ite_step; i++) {
      for (int j = 0; j < system.getPhase(0).getNumberOfComponents(); j++) {
        temp[i] += den_interface[i][j];
      }
    }
    return temp;
  }

  /**
   * getz.
   *
   * @return an array of type double
   * @throws IllegalStateException if the latest calculation did not complete a density profile
   */
  public double[] getz() {
    requireCompleteProfile();
    return z_step;
  }

  /**
   * getPressure.
   *
   * @return an array of type double
   * @throws IllegalStateException if the latest calculation did not complete a density profile
   */
  public double[] getPressure() {
    requireCompleteProfile();
    return pressure_interface;
  }

  /**
   * getInfluenceParameter.
   *
   * @param interfaceTension a double
   * @param componentNumber a int
   * @return a double
   * @throws IllegalStateException if the GT density profile cannot be completed during fitting
   */
  public double getInfluenceParameter(double interfaceTension, int componentNumber) {
    // double startGuess = calcSurfaceTension(0, 1);
    double oldInfluenceParameter;
    double calcVal = 0.0;
    double oldCalcVal = 0.0;
    double dSurfTensdinfluence = 0.0;
    int iter = 0;
    calcInfluenceParameter = true;
    try {
      calcVal = calcSurfaceTension(0, 1) - interfaceTension;
      requireCompleteProfile();
      oldInfluenceParameter = influenceParam[componentNumber];
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
        requireCompleteProfile();

        dSurfTensdinfluence = (calcVal - oldCalcVal) / (influenceParam[componentNumber] - oldInfluenceParameter);
        oldInfluenceParameter = influenceParam[componentNumber];
      } while (Math.abs(calcVal / interfaceTension) > 1e-8 && iter < 100);
      return influenceParam[componentNumber];
    } finally {
      calcInfluenceParameter = true;
    }
  }

  /**
   * Getter for the field <code>dmudn2</code>.
   *
   * @return the dmudn2
   * @throws IllegalStateException if the latest calculation did not complete a density profile
   */
  public double[][][] getDmudn2() {
    requireCompleteProfile();
    return dmudn2;
  }

  /**
   * Setter for the field <code>dmudn2</code>.
   *
   * @param dmudn2 the dmudn2 to set
   */
  public void setDmudn2(double[][][] dmudn2) {
    this.dmudn2 = dmudn2;
  }

  /** Discards all profile data before a new calculation and after any failed integration. */
  @Override
  protected void clearProfile() {
    profileComplete = false;
    localSystem = null;
    den_interface = null;
    z_step = null;
    pressure_interface = null;
    dmudn2 = null;
  }

  /**
   * Requires a complete profile rather than a fallback or a previous calculation.
   *
   * @throws IllegalStateException if no complete profile is available
   */
  private void requireCompleteProfile() {
    if (!profileComplete) {
      throw new IllegalStateException("No complete gradient-theory density profile is available");
    }
  }
}
