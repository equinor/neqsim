package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * cricondebarFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CricondebarFlash extends ConstantDutyPressureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ConstantDutyFlash.class);

  Matrix Jac;
  Matrix fvec;

  /**
   * <p>
   * Constructor for cricondebarFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public CricondebarFlash(SystemInterface system) {
    super(system);
    Jac = new Matrix(system.getPhase(0).getNumberOfComponents() + 1,
        system.getPhase(0).getNumberOfComponents() + 1);
    fvec = new Matrix(system.getPhase(0).getNumberOfComponents() + 1, 1);
  }

  /**
   * <p>
   * calcx.
   * </p>
   *
   * @return a double
   */
  public double calcx() {
    double xtotal = 0;
    for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
      system.getPhases()[0].getComponent(i)
          .setK(Math.exp(system.getPhases()[1].getComponent(i).getLogFugacityCoefficient()
              - system.getPhases()[0].getComponent(i).getLogFugacityCoefficient()));

      system.getPhases()[1].getComponent(i).setK(system.getPhases()[0].getComponent(i).getK());
      system.getPhases()[1].getComponent(i).setx(1.0 / system.getPhases()[0].getComponent(i).getK()
          * system.getPhases()[1].getComponent(i).getz());
      // ktot += Math.abs(system.getPhases()[1].getComponent(i).getK() - 1.0);
    }
    xtotal = 0.0;
    for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
      xtotal += system.getPhases()[1].getComponent(i).getx();
    }
    return xtotal;
  }

  /**
   * <p>
   * initMoleFraction.
   * </p>
   *
   * @return a double
   */
  public double initMoleFraction() {
    double[] XI = new double[system.getPhase(0).getNumberOfComponents()];
    // double sumX = 0.0;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      XI[i] = Math.exp(Math.log(system.getPhase(0).getComponent(i).getz())
          + system.getPhase(0).getComponent(i).getLogFugacityCoefficient()
          - system.getPhase(1).getComponent(i).getLogFugacityCoefficient());
      // sumX += XI[i];
    }
    double tempSumXI = 1.0; // sumX;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      system.getPhase(1).getComponent(i).setx(XI[i] / tempSumXI);
    }
    return tempSumXI;
  }

  /**
   * <p>
   * run2.
   * </p>
   */
  public void run2() {
    ThermodynamicOperations localOperation = new ThermodynamicOperations(system);

    system.init(0);
    system.setTemperature(system.getPhase(0).getPseudoCriticalTemperature() + 20);
    system.setPressure(system.getPhase(0).getPseudoCriticalPressure());
    system.init(3);
    int iterations = 0;

    int maxNumberOfIterations = 300;
    try {
      localOperation.dewPointTemperatureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    system.setNumberOfPhases(2);
    system.setBeta(0.5);
    system.setTemperature(system.getTemperature() - 20);
    system.init(3);
    system.display();
    double Q1 = 0;
    double Qold = 0.0;
    double dQ1dT = 0;
    double oldTemperature = 0;
    double oldIterTemp = 0;
    do {
      oldIterTemp = system.getTemperature();
      iterations = 0;
      do {
        iterations++;
        initMoleFraction();
        system.init(3);
        Qold = Q1;
        Q1 = 0.0;
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          Q1 -= system.getPhase(1).getComponent(i).getx()
              * (system.getPhase(0).getComponent(i).getdfugdt()
                  - system.getPhase(1).getComponent(i).getdfugdt());
        }
        if (iterations > 1) {
          dQ1dT = (Q1 - Qold) / (system.getTemperature() - oldTemperature);
        } else {
          dQ1dT = Q1 * 100.0;
        }

        oldTemperature = system.getTemperature();
        system.setTemperature(system.getTemperature() - 0.5 * Q1 / dQ1dT);

        system.init(3);
        logger.info(
            "temp " + system.getTemperature() + " Q1 " + Q1 + " pressure " + system.getPressure());
      } while (Math.abs(Q1) > 1e-10 && iterations < maxNumberOfIterations);

      iterations = 0;
      double presOld = system.getPressure();
      do {
        logger.info(
            "temp " + system.getTemperature() + " Q1 " + Q1 + " pressure " + system.getPressure());
        Matrix dx = null;

        iterations++;
        try {
          system.init(3);
          setfvec();
          setJac();
          dx = Jac.solve(fvec);
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }
        double damping = iterations * 1.0 / (10.0 + iterations);
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          double xlocal = system.getPhase(1).getComponent(i).getx() - damping * dx.get(i, 0);
          if (xlocal < 1e-30) {
            xlocal = 1e-30;
          }
          if (xlocal > 1.0 - 1e-30) {
            xlocal = 1.0 - 1e-30;
          }
          logger.info("x" + (xlocal) + " press " + system.getPressure() + " fvec " + fvec.norm2());
          system.getPhase(1).getComponent(i).setx(xlocal);
        }
        system.setPressure(
            system.getPressure() - damping * dx.get(system.getPhase(0).getNumberOfComponents(), 0));
      } while (Math.abs(fvec.norm2()) > 1.0e-12 && iterations < maxNumberOfIterations
          && Math.abs(presOld - system.getPressure()) < 10.0);
    } while (Math.abs(oldIterTemp - system.getTemperature()) > 1e-3);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    ThermodynamicOperations localOperation = new ThermodynamicOperations(system);

    system.init(0);
    // system.setTemperature(system.getPhase(0).getPseudoCriticalTemperature() + 7);
    // system.setPressure(system.getPhase(0).getPseudoCriticalPressure() / 2.0);
    system.init(3);

    int iterations = 0;
    localOperation.TPflash();
    double Q1 = 0;
    double Qold = 0.0;
    double dQ1dT = 1.0;
    double oldTemperature = 0;
    double dewTemp = 0;

    for (int ii = 0; ii < 45; ii++) {
      system.setPressure(system.getPressure() + 1.0);
      try {
        // if(ii<2) localOperation.dewPointTemperatureFlash();
        // else localOperation.dewPointPressureFlash();
        dewTemp = system.getTemperature();
        system.getPressure();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }

      iterations = 0;
      do {
        iterations++;
        system.init(3);
        // initMoleFraction();
        localOperation.TPflash();
        system.display();
        Qold = Q1;
        Q1 = 0.0;
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          Q1 -= system.getPhase(1).getComponent(i).getx()
              * (system.getPhase(0).getComponent(i).getdfugdt()
                  - system.getPhase(1).getComponent(i).getdfugdt());
        }
        if (iterations > 3) {
          dQ1dT = (Q1 - Qold) / (system.getTemperature() - oldTemperature);
        } else {
          dQ1dT = -Q1 * 100.0;
        }

        oldTemperature = system.getTemperature();
        system.setTemperature(
            system.getTemperature() - iterations * 1.0 / (iterations + 10.0) * Q1 / dQ1dT);

        logger.info("temp " + system.getTemperature() + "dewTemp " + dewTemp + " Q1 " + Q1
            + " pressure " + system.getPressure());
      } while (Math.abs(Q1) > 1e-10 && iterations < 15); // maxNumberOfIterations);
      logger.info("temp " + system.getTemperature() + " Q1 " + Q1);
      // if(ii<2) system.setTemperature(dewTemp-);
      logger.info("fvec " + fvec.norm2() + " pressure " + system.getPressure());
    }
  }

  /**
   * <p>
   * Setter for the field <code>fvec</code>.
   * </p>
   */
  public void setfvec() {
    double sumxx = 0;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      sumxx += system.getPhases()[1].getComponent(i).getx();
      fvec.set(i, 0,
          Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient()
              * system.getPhases()[0].getComponent(i).getz() * system.getPressure())
              - Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient()
                  * system.getPhases()[1].getComponent(i).getx() * system.getPressure()));
    }
    fvec.set(system.getPhase(0).getNumberOfComponents(), 0, 1.0 - sumxx);
    // logger.info("sumx" + sumxx);
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

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      for (int j = 0; j < system.getPhase(0).getNumberOfComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = -dij * 1.0 / system.getPhases()[1].getComponent(i).getx()
            - system.getPhases()[1].getComponent(i).getdfugdx(j);
        Jac.set(i, j, tempJ);
      }
    }

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      Jac.set(system.getPhase(0).getNumberOfComponents(), i, -1.0);
    }
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      Jac.set(i, system.getPhase(0).getNumberOfComponents(),
          system.getPhases()[0].getComponent(i).getdfugdp()
              - system.getPhases()[1].getComponent(i).getdfugdp());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
