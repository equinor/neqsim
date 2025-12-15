package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.mathlib.nonlinearsolver.NewtonRhapson;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * sysNewtonRhapsonPHflash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SysNewtonRhapsonPHflash implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SysNewtonRhapsonPHflash.class);

  int neq = 0;
  int iter = 0;
  int ic02p = -100;
  int ic03p = -100;
  int testcrit = 0;
  int npCrit = 0;
  double beta = 0;
  double ds = 0;
  double dTmax = 1;
  double dPmax = 1;
  double avscp = 0.1;
  double TC1 = 0;
  double TC2 = 0;
  double PC1 = 0;
  double PC2 = 0;
  double specVar = 0;
  Matrix Jac;
  Matrix fvec;
  Matrix gTvec;
  Matrix gPvec;
  Matrix u;
  Matrix uold;
  Matrix Xgij;
  SystemInterface system;
  int numberOfComponents;
  int speceq = 0;
  Matrix a = new Matrix(4, 4);
  Matrix s = new Matrix(1, 4);
  Matrix xg;
  Matrix xcoef;
  NewtonRhapson solver;
  boolean etterCP = false;
  boolean etterCP2 = false;
  double dVdT = 0;
  int type = 0;
  double dPdT = 0;

  /**
   * <p>
   * Constructor for sysNewtonRhapsonPHflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   */
  public SysNewtonRhapsonPHflash(SystemInterface system, int numberOfPhases,
      int numberOfComponents) {
    this.system = system;
    this.numberOfComponents = numberOfComponents;
    neq = numberOfComponents;
    Jac = new Matrix(neq + 2, neq + 2);
    fvec = new Matrix(neq + 2, 1);
    gTvec = new Matrix(neq, 1);
    gPvec = new Matrix(neq, 1);
    u = new Matrix(neq + 2, 1);
    Xgij = new Matrix(neq + 2, 4);
    setu();
    uold = u.copy();
    // logger.info("Spec : " +speceq);
    solver = new NewtonRhapson();
    solver.setOrder(3);
  }

  /**
   * <p>
   * Constructor for sysNewtonRhapsonPHflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param numberOfPhases a int
   * @param numberOfComponents a int
   * @param type a int
   */
  public SysNewtonRhapsonPHflash(SystemInterface system, int numberOfPhases, int numberOfComponents,
      int type) {
    this(system, numberOfPhases, numberOfComponents);
    this.type = type;
  }

  /**
   * <p>
   * setSpec.
   * </p>
   *
   * @param spec a double
   */
  public void setSpec(double spec) {
    this.specVar = spec;
  }

  /**
   * <p>
   * Setter for the field <code>fvec</code>.
   * </p>
   */
  public void setfvec() {
    for (int i = 0; i < numberOfComponents; i++) {
      fvec.set(i, 0,
          Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient())
              + Math.log(system.getPhases()[0].getComponent(i).getx())
              - Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient())
              - Math.log(system.getPhases()[1].getComponent(i).getx()));
    }
    double rP = 0.0;
    double rT = 0.0;
    if (type == 0) {
      rT = 1.0 / (R * system.getTemperature()) * (specVar - system.getEnthalpy());
      rP = 0.0;
    }
    if (type == 1) {
      rT = 1.0 / R * (specVar - system.getEntropy());
      rP = 0.0;
    }

    fvec.set(numberOfComponents, 0, rT);
    fvec.set(numberOfComponents + 1, 0, rP);
  }

  /**
   * <p>
   * setJac.
   * </p>
   */
  public void setJac() {
    Jac.timesEquals(0.0);
    double dij = 0.0;

    double tempJ = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = 1.0 / system.getBeta()
            * (dij / system.getPhases()[0].getComponent(i).getx() - 1.0
                + system.getPhases()[0].getComponent(i).getdfugdx(j))
            + 1.0 / (1.0 - system.getBeta()) * (dij / system.getPhases()[1].getComponent(i).getx()
                - 1.0 + system.getPhases()[1].getComponent(i).getdfugdx(j));
        Jac.set(i, j, tempJ);
      }
    }

    double[] gT = new double[numberOfComponents];
    double[] gP = new double[numberOfComponents];

    for (int i = 0; i < numberOfComponents; i++) {
      gT[i] = system.getTemperature() * (system.getPhases()[0].getComponent(i).getdfugdt()
          - system.getPhases()[1].getComponent(i).getdfugdt());
      gP[i] = system.getPressure() * (system.getPhases()[0].getComponent(i).getdfugdp()
          - system.getPhases()[1].getComponent(i).getdfugdp());

      Jac.set(numberOfComponents, i, gT[i]);
      Jac.set(i, numberOfComponents, gT[i]);

      Jac.set(numberOfComponents + 1, i, gP[i]);
      Jac.set(i, numberOfComponents + 1, gP[i]);
    }

    double Ett = -system.getCp() / R;
    Jac.set(numberOfComponents, numberOfComponents, Ett);
    double Etp = system.getPressure() / R * system.getdVdTpn();
    Jac.set(numberOfComponents, numberOfComponents + 1, Etp);
    Jac.set(numberOfComponents + 1, numberOfComponents, Etp);
    double Epp =
        Math.pow(system.getPressure(), 2.0) / (R * system.getTemperature()) * system.getdVdPtn();
    Jac.set(numberOfComponents + 1, numberOfComponents + 1, Epp);

    // Force pressure to be constant by setting the last row to [0...0 1] and residual to 0
    for (int i = 0; i < numberOfComponents + 1; i++) {
      Jac.set(numberOfComponents + 1, i, 0.0);
    }
    Jac.set(numberOfComponents + 1, numberOfComponents + 1, 1.0);
  }

  /**
   * <p>
   * Setter for the field <code>u</code>.
   * </p>
   */
  public void setu() {
    for (int i = 0; i < numberOfComponents; i++) {
      u.set(i, 0, 0.0);
    }
    u.set(numberOfComponents, 0, 0.0);
    u.set(numberOfComponents + 1, 0, 0.0);
  }

  /**
   * <p>
   * init.
   * </p>
   */
  public void init() {
    double oldBeta = system.getBeta();
    double tempBeta = oldBeta;

    for (int i = 0; i < numberOfComponents; i++) {
      tempBeta += u.get(i, 0);
    }

    if (tempBeta < 1e-10) {
      tempBeta = 1e-10;
    }
    if (tempBeta > 1.0 - 1e-10) {
      tempBeta = 1.0 - 1e-10;
    }

    system.setBeta(tempBeta);

    for (int i = 0; i < numberOfComponents; i++) {
      double v = 0.0;
      double l = 0.0;
      v = system.getPhases()[0].getComponent(i).getx() * oldBeta + u.get(i, 0);
      double z = system.getPhases()[0].getComponent(i).getz();
      if (v < 0) {
        v = 1e-20;
      }
      if (v > z) {
        v = z - 1e-20;
      }
      l = z - v;

      system.getPhases()[0].getComponent(i).setx(v / system.getBeta());
      system.getPhases()[1].getComponent(i).setx(l / (1.0 - system.getBeta()));
      system.getPhases()[1].getComponent(i).setK(system.getPhases()[0].getComponent(i).getx()
          / system.getPhases()[1].getComponent(i).getx());
      system.getPhases()[0].getComponent(i).setK(system.getPhases()[1].getComponent(i).getK());
    }

    // dt = Math.exp(u.get(numberOfComponents+1,0)) - system.getTemperature();
    // logger.info("temperature: " + system.getTemperature());
    // logger.info("pressure: " + system.getPressure());
    // system.init(1);
    // v1 = system.getVolume();
    // system.setPressure(Math.exp(u.get(numberOfComponents+1,0)));
    // logger.info("temperature: " + system.getTemperature());

    double dLnT = u.get(numberOfComponents, 0);
    double dLnP = u.get(numberOfComponents + 1, 0);

    if (Math.abs(dLnT) > 1.0) {
      dLnT = Math.signum(dLnT) * 1.0;
    }
    if (Math.abs(dLnP) > 1.0) {
      dLnP = Math.signum(dLnP) * 1.0;
    }

    system.setTemperature(Math.exp(dLnT + Math.log(system.getTemperature())));
    system.setPressure(Math.exp(dLnP + Math.log(system.getPressure())));
    // logger.info("etter temperature: " + system.getTemperature());

    system.init(3);
  }

  /**
   * <p>
   * solve.
   * </p>
   *
   * @param np a int
   * @return a int
   */
  public int solve(int np) {
    if (Math.abs(system.getBeta()) < 1e-6 || Math.abs(system.getBeta() - 1.0) < 1e-6) {
      int iterSingle = 0;
      double err = 0.0;
      double oldTemp = system.getTemperature();

      do {
        iterSingle++;
        system.init(3);
        double val = 0.0;
        double dValdT = 0.0;

        if (type == 0) { // PH Flash
          val = system.getEnthalpy();
          dValdT = system.getCp();
        } else { // PS Flash
          val = system.getEntropy();
          dValdT = system.getCp() / system.getTemperature();
        }

        err = val - specVar;

        if (Math.abs(err) < 1e-6) {
          return 1;
        }

        double dT = err / dValdT;

        // Limit step size
        if (Math.abs(dT) > 0.2 * system.getTemperature()) {
          dT = Math.signum(dT) * 0.2 * system.getTemperature();
        }

        system.setTemperature(system.getTemperature() - dT);

        if (system.getTemperature() <= 0) {
          system.setTemperature(oldTemp);
          break; // Failed, go to 2-phase
        }

      } while (iterSingle < 100);

      // If converged
      if (Math.abs(err) < 1e-6) {
        return 1;
      }
    }

    iter = 1;
    do {
      iter++;
      init();
      setfvec();
      setJac();
      // fvec.print(10, 10);
      // Jac.print(10, 10);
      u = Jac.solve(fvec.times(-1.0));
      // u.equals(dx.timesEquals(1.0));
      // fvec.print(10, 10);
      // logger.info("iter: " + iter);
    } while (fvec.norm2() > 1.e-10 && iter < 1000); // && Double.isNaN(dx.norm2()));
    // logger.info("iter: " + iter);
    // logger.info("temperature: " + system.getTemperature());
    // logger.info("pressure: " + system.getPressure());
    init();
    return iter;
  }
}
