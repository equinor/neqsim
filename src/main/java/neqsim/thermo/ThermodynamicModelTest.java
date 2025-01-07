/*
 * ThermodynamicModelTest.java
 *
 * Created on 7. mai 2001, 19:20
 */

package neqsim.thermo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ThermodynamicModelTest class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ThermodynamicModelTest implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ThermodynamicModelTest.class);

  SystemInterface system;
  private double maxError = 1e-10;

  /**
   * <p>
   * Constructor for ThermodynamicModelTest.
   * </p>
   */
  public ThermodynamicModelTest() {}

  /**
   * <p>
   * Constructor for ThermodynamicModelTest.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ThermodynamicModelTest(SystemInterface system) {
    this.system = system;
  }

  /**
   * <p>
   * runTest.
   * </p>
   */
  public void runTest() {
    // system.init(0);
    system.init(3);
    logger.info("testing fugacitycoefs..." + checkFugacityCoefficients());
    logger.info("testing fugacity der composition..." + checkFugacityCoefficientsDn());
    logger.info("testing fugacity der composition2..." + checkFugacityCoefficientsDn2());
    logger.info("testing fugacity der pressure..." + checkFugacityCoefficientsDP());
    logger.info("testing fugacity der temperature..." + checkFugacityCoefficientsDT());
    logger.info("comparing to numerical derivatives..." + checkNumerically());
    // logger.info("testing fugacitycoefs..." + checkFugacityCoefficients());
    // logger.info("testing fugacity der composition..." +
    // checkFugacityCoefficientsDn());
    // logger.info("testing fugacity der composition2..." +
    // checkFugacityCoefficientsDn2());
    // logger.info("testing fugacity der pressure..." +
    // checkFugacityCoefficientsDP());
    // logger.info("testing fugacity der temperature..." +
    // checkFugacityCoefficientsDT());
    // logger.info("comparing to numerical derivatives..." +
    // checkNumerically());
  }

  /**
   * <p>
   * checkFugacityCoefficients.
   * </p>
   *
   * @return a boolean
   */
  public boolean checkFugacityCoefficients() {
    double temp1 = 0;
    for (int j = 0; j < system.getNumberOfPhases(); j++) {
      for (int i = 0; i < system.getPhase(j).getNumberOfComponents(); i++) {
        temp1 += system.getPhase(j).getComponent(i).getNumberOfMolesInPhase()
            * Math.log(system.getPhase(j).getComponent(i).getFugacityCoefficient());
        // temp2 += system.getPhase(j).getComponent(i).getNumberOfMolesInPhase()
        // * Math.log(system.getPhase(j).getComponent(i).getFugacityCoefficient());
      }
      temp1 -= system.getPhase(j).getGresTP() / (R * system.getTemperature());
    }
    logger.info("Testing fugacity coefficients...................");
    // logger.info("Total fug gas : " + temp1);
    // logger.info("Total fug liq : " + temp2);
    // logger.info("Testing fugacity coefficients...................");
    // logger.info("Total fug gas : " + temp1);
    // logger.info("Total fug liq : " + temp2);
    // logger.info("MOLAR VOLUME 1 " + system.getPhase("gas").getMolarVolume());
    // logger.info("MOLAR VOLUME 2 " + system.getPhase("aqueous").getMolarVolume());
    // logger.info("number of phases " + system.getNumberOfPhases());
    // logger.info("number of components " + system.getNumberOfComponents());
    // temp1 -= system.getPhase(j).getGresTP() / (R * system.getTemperature());
    // temp2 -= system.getPhases()[1].getGresTP() / (R * system.getTemperature());
    double sum = Math.abs(temp1);
    // logger.info("Diffference fug gas : " + temp1);
    // logger.info("Difference fug liq : " + temp2);
    // logger.info("Diffference fug gas : " + temp1);
    // logger.info("Difference fug liq : " + temp2);
    return Math.abs(sum) < maxError;
  }

  /**
   * <p>
   * checkFugacityCoefficientsDn.
   * </p>
   *
   * @return a boolean
   */
  // public boolean checkFugacityCoefficientsDn() {
  public boolean checkFugacityCoefficientsDn() {
    double temp1 = 0;
    double sum = 0;
    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      for (int j = 0; j < system.getPhase(k).getNumberOfComponents(); j++) {
        temp1 = 0;
        // temp2 = 0;
        // temp1 +=
        // Math.log(system.getPhases()[0].getComponent(j).getFugacityCoefficient());
        // temp2 +=
        // Math.log(system.getPhases()[1].getComponent(j).getFugacityCoefficient());
        for (int i = 0; i < system.getPhase(k).getNumberOfComponents(); i++) {
          temp1 += system.getPhase(k).getComponent(i).getNumberOfMolesInPhase()
              * system.getPhase(k).getComponent(i).getdfugdn(j);
          // temp2 += system.getPhases()[1].getComponent(i).getNumberOfMolesInPhase()
          // * system.getPhases()[1].getComponent(i).getdfugdn(j);
          // logger.info("fug " +
          // system.getPhases()[1].getComponent(i).getNumberOfMolesInPhase()*system.getPhases()[1].getComponent(i).getdfugdn(j));
        }

        // logger.info("test fugdn gas : " + j + " " + temp1 + " name " +
        // system.getPhases()[0].getComponent(j).getComponentName());
        // logger.info("test fugdn liq : " + j + " " + temp2);
      }
    }
    sum += Math.abs(temp1);
    logger.info("Testing composition derivatives of fugacity coefficients...................");
    logger.info("Diffference : " + sum);
    // logger.info("Testing composition derivatives of fugacity
    // coefficients...................");
    // logger.info("Difference : " + sum);
    return Math.abs(sum) < maxError;
  }

  /**
   * <p>
   * checkFugacityCoefficientsDn2.
   * </p>
   *
   * @return a boolean
   */
  public boolean checkFugacityCoefficientsDn2() {
    // boolean test1 = false, test2 = false;
    double temp1 = 0;
    double sum = 0;

    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      for (int j = 0; j < system.getPhase(k).getNumberOfComponents(); j++) {
        temp1 = 0;
        // temp2 = 0;
        // temp1 +=
        // Math.log(system.getPhases()[0].getComponent(j).getFugacityCoefficient());
        // temp2 +=
        // Math.log(system.getPhases()[1].getComponent(j).getFugacityCoefficient());
        for (int i = 0; i < system.getPhase(k).getNumberOfComponents(); i++) {
          temp1 += system.getPhase(k).getComponent(i).getdfugdn(j)
              - system.getPhase(k).getComponent(j).getdfugdn(i);
          // temp2 += system.getPhases()[1].getComponent(i).getdfugdn(j)
          // - system.getPhases()[1].getComponent(j).getdfugdn(i);
        }
        // sum += Math.abs(temp1) + Math.abs(temp2);
        // logger.info("test fugdn gas : " + j + " " + temp1);
        // logger.info("test fugdn liq : " + j + " " + temp2);
      }

      // logger.info("Testing composition derivatives2 of fugacity
      // coefficients...................");
      // logger.info("Diffference : " + sum);
      // logger.info("Testing composition derivatives2 of fugacity
      // coefficients...................");
      // logger.info("Difference : " + sum);
    }
    sum += Math.abs(temp1);
    logger.info("Testing composition derivatives2 of fugacity coefficients...................");
    logger.info("Diffference : " + sum);
    return Math.abs(sum) < maxError;
  }

  /**
   * <p>
   * checkFugacityCoefficientsDP.
   * </p>
   *
   * @return a boolean
   */
  public boolean checkFugacityCoefficientsDP() {
    // boolean test1 = false, test2 = false;
    double temp1 = 0, temp2 = 0;
    double sum = 0;
    for (int j = 0; j < system.getNumberOfPhases(); j++) {
      for (int i = 0; i < system.getPhase(j).getNumberOfComponents(); i++) {
        temp1 += system.getPhase(j).getComponent(i).getNumberOfMolesInPhase()
            * system.getPhase(j).getComponent(i).getdfugdp();
      }
      temp1 -= (system.getPhase(j).getZ() - 1.0) * system.getPhase(j).getNumberOfMolesInPhase()
          / system.getPhase(j).getPressure();
    }
    sum = Math.abs(temp1) + Math.abs(temp2);
    // logger.info("test fugdp gas : " + temp1);
    // logger.info("test fugdp liq : " + temp2);
    logger.info("Testing pressure derivatives of fugacity coefficients...................");
    logger.info("Error : " + sum);
    // logger.info("Testing pressure derivatives of fugacity
    // coefficients...................");
    // logger.info("Error : " + sum);

    return Math.abs(sum) < maxError;
  }

  /**
   * <p>
   * checkFugacityCoefficientsDT.
   * </p>
   *
   * @return a boolean
   */
  public boolean checkFugacityCoefficientsDT() {
    // boolean test1 = false, test2 = false;
    double temp1 = 0;
    double sum = 0;
    for (int j = 0; j < system.getNumberOfPhases(); j++) {
      for (int i = 0; i < system.getPhase(j).getNumberOfComponents(); i++) {
        temp1 += system.getPhase(j).getComponent(i).getNumberOfMolesInPhase()
            * system.getPhase(j).getComponent(i).getdfugdt();
        // temp2 += system.getPhases()[1].getComponent(i).getNumberOfMolesInPhase()
        // * system.getPhases()[1].getComponent(i).getdfugdt();
      }
      temp1 += system.getPhase(j).getHresTP() / (R * Math.pow(system.getTemperature(), 2.0));
      // temp2 += system.getPhases()[1].getHresTP() / (R * Math.pow(system.getTemperature(), 2.0));
    }
    // sum = Math.abs(temp1) + Math.abs(temp2);
    sum = Math.abs(temp1);
    // logger.info("test fugdp gas : " + system.getPhases()[0].getHresTP());
    // logger.info("test fugdp liq : " + temp2);
    logger.info("Testing temperature derivatives of fugacity coefficients...................");
    logger.info("Error : " + sum);
    // logger.info("Testing temperature derivatives of fugacity
    // coefficients...................");
    // logger.info("Error : " + sum);
    return Math.abs(sum) < maxError;
  }

  /**
   * <p>
   * checkNumerically.
   * </p>
   *
   * @return a boolean
   */
  public boolean checkNumerically() {
    double[][] gasfug = new double[2][system.getPhases()[0].getNumberOfComponents()];
    double[][] liqfug = new double[2][system.getPhases()[0].getNumberOfComponents()];
    double[][] gasnumericDfugdt = new double[2][system.getPhases()[0].getNumberOfComponents()];
    double[][] liqnumericDfugdt = new double[2][system.getPhases()[0].getNumberOfComponents()];
    double[][] gasnumericDfugdp = new double[2][system.getPhases()[0].getNumberOfComponents()];
    double[][] liqnumericDfugdp = new double[2][system.getPhases()[0].getNumberOfComponents()];
    double[][][] gasnumericDfugdn = new double[2][system.getPhases()[0]
        .getNumberOfComponents()][system.getPhases()[0].getNumberOfComponents()];
    double[][][] liqnumericDfugdn = new double[2][system.getPhases()[0]
        .getNumberOfComponents()][system.getPhases()[0].getNumberOfComponents()];
    system.init(3);

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      gasnumericDfugdt[0][i] = system.getPhases()[0].getComponent(i).getdfugdt();
      gasnumericDfugdp[0][i] = system.getPhases()[0].getComponent(i).getdfugdp();
      liqnumericDfugdt[0][i] = system.getPhases()[1].getComponent(i).getdfugdt();
      liqnumericDfugdp[0][i] = system.getPhases()[1].getComponent(i).getdfugdp();
      for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
        gasnumericDfugdn[0][i][k] = system.getPhases()[0].getComponent(i).getdfugdn(k);
        liqnumericDfugdn[0][i][k] = system.getPhases()[1].getComponent(i).getdfugdn(k);
      }
    }

    double dt = system.getTemperature() / 1e5;
    system.setTemperature(system.getTemperature() + dt);
    system.init(3);

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[0][i] = Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient());
      liqfug[0][i] = Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient());
    }

    system.setTemperature(system.getTemperature() - 2 * dt);
    system.init(3);

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[1][i] = Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient());
      liqfug[1][i] = Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient());
    }

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      logger.info("dt: gas phase comp " + i + "  % error "
          + ((gasfug[0][i] - gasfug[1][i]) / (2 * dt) - gasnumericDfugdt[0][i])
              / gasnumericDfugdt[0][i] * 100.0);
      logger.info("dt: liq phase comp " + i + "  % error "
          + ((liqfug[0][i] - liqfug[1][i]) / (2 * dt) - liqnumericDfugdt[0][i])
              / liqnumericDfugdt[0][i] * 100.0);
      // logger.info("dt: gas phase comp " + i + " % error " + ((gasfug[0][i] -
      // gasfug[1][i])/(2*dt) - gasnumericDfugdt[0][i])/gasnumericDfugdt[0][i]*100.0);
      // logger.info("dt: liq phase comp " + i + " % error " + ((liqfug[0][i] -
      // liqfug[1][i])/(2*dt) - liqnumericDfugdt[0][i])/liqnumericDfugdt[0][i]*100.0);
    }

    system.setTemperature(system.getTemperature() + dt);
    system.init(3);

    double dp = system.getPressure() / 1e5;
    system.setPressure(system.getPressure() + dp);
    system.init(3);

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[0][i] = Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient());
      liqfug[0][i] = Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient());
    }

    system.setPressure(system.getPressure() - 2 * dp);
    system.init(3);

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[1][i] = Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient());
      liqfug[1][i] = Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient());
    }

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      logger.info("dp: gas phase comp " + i + "  % error "
          + ((gasfug[0][i] - gasfug[1][i]) / (2 * dp) - gasnumericDfugdp[0][i])
              / gasnumericDfugdp[0][i] * 100.0);
      logger.info("dp: liq phase comp " + i + "  % error "
          + ((liqfug[0][i] - liqfug[1][i]) / (2 * dp) - liqnumericDfugdp[0][i])
              / liqnumericDfugdp[0][i] * 100.0);
      // logger.info("dp: gas phase comp " + i + " % error " + ((gasfug[0][i] -
      // gasfug[1][i])/(2*dp) - gasnumericDfugdp[0][i])/gasnumericDfugdp[0][i]*100.0);
      // logger.info("dp: liq phase comp " + i + " % error " + ((liqfug[0][i] -
      // liqfug[1][i])/(2*dp) - liqnumericDfugdp[0][i])/liqnumericDfugdp[0][i]*100.0);
    }

    system.setPressure(system.getPressure() + dp);
    system.init(3);

    for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
      for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
        double dn = system.getPhase(phaseNum).getComponent(k).getNumberOfMolesInPhase() / 1.0e5;
        logger
            .info("component name " + system.getPhase(phaseNum).getComponent(k).getComponentName());
        logger.info("dn " + dn);
        // logger.info("component name " +
        // system.getPhase(phaseNum).getComponent(k).getComponentName());
        // logger.info("dn " + dn);
        if (dn < 1e-12) {
          dn = 1e-12;
        }
        system.addComponent(k, dn, phaseNum);
        // system.initBeta();
        system.init_x_y();
        system.init(3);

        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          // gasfug[0][i] =
          // Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient());
          liqfug[0][i] =
              Math.log(system.getPhase(phaseNum).getComponent(i).getFugacityCoefficient());
        }

        system.addComponent(k, -2.0 * dn, phaseNum);
        // system.initBeta();
        system.init_x_y();
        system.init(3);

        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          // gasfug[1][i] =
          // Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient());
          liqfug[1][i] =
              Math.log(system.getPhase(phaseNum).getComponent(i).getFugacityCoefficient());
        }

        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          if (phaseNum == 0) {
            logger.info("dn: gas phase comp " + i + "  % error "
                + ((liqfug[0][i] - liqfug[1][i]) / (2 * dn) - gasnumericDfugdn[0][i][k])
                    / gasnumericDfugdn[0][i][k] * 100.0);
            // logger.info("dn: gas phase comp " + i + " % error " +
            // ((liqfug[0][i] -
            // liqfug[1][i])/(2*dn) -
            // gasnumericDfugdn[0][i][k])/gasnumericDfugdn[0][i][k]*100.0);
          }
          if (phaseNum == 1) {
            logger.info("dn: liq phase comp " + i + "  % error "
                + ((liqfug[0][i] - liqfug[1][i]) / (2 * dn) - liqnumericDfugdn[0][i][k])
                    / liqnumericDfugdn[0][i][k] * 100.0);
            // logger.info("dn: liq phase comp " + i + " % error " +
            // ((liqfug[0][i] -
            // liqfug[1][i])/(2*dn) -
            // liqnumericDfugdn[0][i][k])/liqnumericDfugdn[0][i][k]*100.0);
          }
        }

        system.addComponent(k, dn, phaseNum);
        // system.initBeta();
        system.init_x_y();
        system.init(3);
      }
    }
    return true;
  }

  /**
   * Setter for property <code>maxError</code>.
   *
   * @param maxErr before test will report failed Set maximum allowed error in model check
   */
  public void setMaxError(double maxErr) {
    this.maxError = maxErr;
  }
}
