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
  private static final long serialVersionUID = 1000;
  SystemInterface system;
  static Logger logger = LogManager.getLogger(ThermodynamicModelTest.class);

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
    double temp1 = 0 ;
    for (int j = 0; j < system.getNumberOfPhases(); j++) {
      for (int i = 0; i < system.getPhase(j).getNumberOfComponents(); i++) {
        temp1 += system.getPhase(j).getComponents()[i].getNumberOfMolesInPhase()
            * Math.log(system.getPhase(j).getComponents()[i].getFugacityCoefficient());
        // temp2 += system.getPhase(j).getComponents()[i].getNumberOfMolesInPhase()
        // * Math.log(system.getPhase(j).getComponents()[i].getFugacityCoefficient());
      }
      temp1 -= system.getPhase(j).getGresTP() / (R * system.getTemperature());
    }
    logger.info("Testing fugacity coefficients...................");
    //logger.info("Total fug gas : " + temp1);
    //logger.info("Total fug liq : " + temp2);
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
    //logger.info("Diffference fug gas : " + temp1);
    //logger.info("Difference fug liq : " + temp2);
    // logger.info("Diffference fug gas : " + temp1);
    // logger.info("Difference fug liq : " + temp2);
    return Math.abs(sum) < 1e-10;
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
      //temp2 = 0;
      // temp1 +=
      // Math.log(system.getPhases()[0].getComponents()[j].getFugacityCoefficient());
      // temp2 +=
      // Math.log(system.getPhases()[1].getComponents()[j].getFugacityCoefficient());
      for (int i = 0; i < system.getPhase(k).getNumberOfComponents(); i++) {
        temp1 += system.getPhase(k).getComponents()[i].getNumberOfMolesInPhase()
            * system.getPhase(k).getComponents()[i].getdfugdn(j);
        //temp2 += system.getPhases()[1].getComponents()[i].getNumberOfMolesInPhase()
            //* system.getPhases()[1].getComponents()[i].getdfugdn(j);
        // logger.info("fug " +
        // system.getPhases()[1].getComponents()[i].getNumberOfMolesInPhase()*system.getPhases()[1].getComponents()[i].getdfugdn(j));
      }
      
      
      // logger.info("test fugdn gas : " + j + " " + temp1 + " name " +
      // system.getPhases()[0].getComponents()[j].getComponentName());
      // logger.info("test fugdn liq : " + j + " " + temp2);
    }
  }
  sum += Math.abs(temp1) ;
  logger.info("Testing composition derivatives of fugacity coefficients...................");
    logger.info("Diffference : " + sum);
    // logger.info("Testing composition derivatives of fugacity
    // coefficients...................");
    // logger.info("Difference : " + sum);
    return Math.abs(sum) < 1e-10;
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
      //temp2 = 0;
      // temp1 +=
      // Math.log(system.getPhases()[0].getComponents()[j].getFugacityCoefficient());
      // temp2 +=
      // Math.log(system.getPhases()[1].getComponents()[j].getFugacityCoefficient());
      for (int i = 0; i < system.getPhase(k).getNumberOfComponents(); i++) {
        temp1 += system.getPhase(k).getComponents()[i].getdfugdn(j)
            - system.getPhase(k).getComponents()[j].getdfugdn(i);
        //temp2 += system.getPhases()[1].getComponents()[i].getdfugdn(j)
           // - system.getPhases()[1].getComponents()[j].getdfugdn(i);
      }
      //sum += Math.abs(temp1) + Math.abs(temp2);
      // logger.info("test fugdn gas : " + j + " " + temp1);
      // logger.info("test fugdn liq : " + j + " " + temp2);
    }
    
    //logger.info("Testing composition derivatives2 of fugacity coefficients...................");
    //logger.info("Diffference : " + sum);
    // logger.info("Testing composition derivatives2 of fugacity
    // coefficients...................");
    // logger.info("Difference : " + sum);
  }
  sum += Math.abs(temp1) ;
  logger.info("Testing composition derivatives2 of fugacity coefficients...................");
  logger.info("Diffference : " + sum);  
    return Math.abs(sum) < 1e-10;
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
        temp1 += system.getPhase(j).getComponents()[i].getNumberOfMolesInPhase()
            * system.getPhase(j).getComponents()[i].getdfugdp();

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

    return Math.abs(sum) < 1e-10;
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
        temp1 += system.getPhase(j).getComponents()[i].getNumberOfMolesInPhase()
            * system.getPhase(j).getComponents()[i].getdfugdt();
        // temp2 += system.getPhases()[1].getComponents()[i].getNumberOfMolesInPhase()
        // * system.getPhases()[1].getComponents()[i].getdfugdt();
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
    return Math.abs(sum) < 1e-10;
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
      gasnumericDfugdt[0][i] = system.getPhases()[0].getComponents()[i].getdfugdt();
      gasnumericDfugdp[0][i] = system.getPhases()[0].getComponents()[i].getdfugdp();
      liqnumericDfugdt[0][i] = system.getPhases()[1].getComponents()[i].getdfugdt();
      liqnumericDfugdp[0][i] = system.getPhases()[1].getComponents()[i].getdfugdp();
      for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
        gasnumericDfugdn[0][i][k] = system.getPhases()[0].getComponents()[i].getdfugdn(k);
        liqnumericDfugdn[0][i][k] = system.getPhases()[1].getComponents()[i].getdfugdn(k);
      }
    }

    double dt = system.getTemperature() / 1e5;
    system.setTemperature(system.getTemperature() + dt);
    system.init(3);

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[0][i] = Math.log(system.getPhases()[0].getComponents()[i].getFugacityCoefficient());
      liqfug[0][i] = Math.log(system.getPhases()[1].getComponents()[i].getFugacityCoefficient());
    }

    system.setTemperature(system.getTemperature() - 2 * dt);
    system.init(3);

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[1][i] = Math.log(system.getPhases()[0].getComponents()[i].getFugacityCoefficient());
      liqfug[1][i] = Math.log(system.getPhases()[1].getComponents()[i].getFugacityCoefficient());
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
      gasfug[0][i] = Math.log(system.getPhases()[0].getComponents()[i].getFugacityCoefficient());
      liqfug[0][i] = Math.log(system.getPhases()[1].getComponents()[i].getFugacityCoefficient());
    }

    system.setPressure(system.getPressure() - 2 * dp);
    system.init(3);

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      gasfug[1][i] = Math.log(system.getPhases()[0].getComponents()[i].getFugacityCoefficient());
      liqfug[1][i] = Math.log(system.getPhases()[1].getComponents()[i].getFugacityCoefficient());
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

    for (int phase = 0; phase < 2; phase++) {
      for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
        double dn = system.getPhases()[phase].getComponents()[k].getNumberOfMolesInPhase() / 1.0e5;
        logger.info(
            "component name " + system.getPhases()[phase].getComponents()[k].getComponentName());
        logger.info("dn " + dn);
        // logger.info("component name " +
        // system.getPhases()[phase].getComponents()[k].getComponentName());
        // logger.info("dn " + dn);
        if (dn < 1e-12) {
          dn = 1e-12;
        }
        system.addComponent(k, dn, phase);
        // system.initBeta();
        system.init_x_y();
        system.init(3);

        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          // gasfug[0][i] =
          // Math.log(system.getPhases()[0].getComponents()[i].getFugacityCoefficient());
          liqfug[0][i] =
              Math.log(system.getPhases()[phase].getComponents()[i].getFugacityCoefficient());
        }

        system.addComponent(k, -2.0 * dn, phase);
        // system.initBeta();
        system.init_x_y();
        system.init(3);

        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          // gasfug[1][i] =
          // Math.log(system.getPhases()[0].getComponents()[i].getFugacityCoefficient());
          liqfug[1][i] =
              Math.log(system.getPhases()[phase].getComponents()[i].getFugacityCoefficient());
        }

        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          if (phase == 0) {
            logger.info("dn: gas phase comp " + i + "  % error "
                + ((liqfug[0][i] - liqfug[1][i]) / (2 * dn) - gasnumericDfugdn[0][i][k])
                    / gasnumericDfugdn[0][i][k] * 100.0);
            // logger.info("dn: gas phase comp " + i + " % error " +
            // ((liqfug[0][i] -
            // liqfug[1][i])/(2*dn) -
            // gasnumericDfugdn[0][i][k])/gasnumericDfugdn[0][i][k]*100.0);
          }
          if (phase == 1) {
            logger.info("dn: liq phase comp " + i + "  % error "
                + ((liqfug[0][i] - liqfug[1][i]) / (2 * dn) - liqnumericDfugdn[0][i][k])
                    / liqnumericDfugdn[0][i][k] * 100.0);
            // logger.info("dn: liq phase comp " + i + " % error " +
            // ((liqfug[0][i] -
            // liqfug[1][i])/(2*dn) -
            // liqnumericDfugdn[0][i][k])/liqnumericDfugdn[0][i][k]*100.0);
          }
        }

        system.addComponent(k, dn, phase);
        // system.initBeta();
        system.init_x_y();
        system.init(3);
      }
    }
    return true;
  }
}
