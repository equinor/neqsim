/*
 * ThermodynamicModelTest.java
 *
 * Created on 7. mai 2001, 19:20
 */

package neqsim.thermo;



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
    
    
    
    
    
    
    // 
    // 
    // 
    // 
    // 
    // 
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
        temp1 += system.getPhase(j).getComponents()[i].getNumberOfMolesInPhase()
            * Math.log(system.getPhase(j).getComponents()[i].getFugacityCoefficient());
        // temp2 += system.getPhase(j).getComponents()[i].getNumberOfMolesInPhase()
        // * Math.log(system.getPhase(j).getComponents()[i].getFugacityCoefficient());
      }
      temp1 -= system.getPhase(j).getGresTP() / (R * system.getTemperature());
    }
    
    // 
    // 
    // 
    // 
    // 
    // 
    // 
    // 
    // 
    // temp1 -= system.getPhase(j).getGresTP() / (R * system.getTemperature());
    // temp2 -= system.getPhases()[1].getGresTP() / (R * system.getTemperature());
    double sum = Math.abs(temp1);
    // 
    // 
    // 
    // 
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
        // Math.log(system.getPhases()[0].getComponents()[j].getFugacityCoefficient());
        // temp2 +=
        // Math.log(system.getPhases()[1].getComponents()[j].getFugacityCoefficient());
        for (int i = 0; i < system.getPhase(k).getNumberOfComponents(); i++) {
          temp1 += system.getPhase(k).getComponents()[i].getNumberOfMolesInPhase()
              * system.getPhase(k).getComponents()[i].getdfugdn(j);
          // temp2 += system.getPhases()[1].getComponents()[i].getNumberOfMolesInPhase()
          // * system.getPhases()[1].getComponents()[i].getdfugdn(j);
          // 
        }

        // 
        // 
      }
    }
    sum += Math.abs(temp1);
    
    
    // 
    // 
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
        // Math.log(system.getPhases()[0].getComponents()[j].getFugacityCoefficient());
        // temp2 +=
        // Math.log(system.getPhases()[1].getComponents()[j].getFugacityCoefficient());
        for (int i = 0; i < system.getPhase(k).getNumberOfComponents(); i++) {
          temp1 += system.getPhase(k).getComponents()[i].getdfugdn(j)
              - system.getPhase(k).getComponents()[j].getdfugdn(i);
          // temp2 += system.getPhases()[1].getComponents()[i].getdfugdn(j)
          // - system.getPhases()[1].getComponents()[j].getdfugdn(i);
        }
        // sum += Math.abs(temp1) + Math.abs(temp2);
        // 
        // 
      }

      // 
      // 
      // 
      // 
    }
    sum += Math.abs(temp1);
    
    
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
        temp1 += system.getPhase(j).getComponents()[i].getNumberOfMolesInPhase()
            * system.getPhase(j).getComponents()[i].getdfugdp();
      }
      temp1 -= (system.getPhase(j).getZ() - 1.0) * system.getPhase(j).getNumberOfMolesInPhase()
          / system.getPhase(j).getPressure();
    }
    sum = Math.abs(temp1) + Math.abs(temp2);
    // 
    // 
    
    
    // 
    // 

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
    // 
    // 
    
    
    // 
    // 
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
      
      
      // 
      // 
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
      
      
      // 
      // 
    }

    system.setPressure(system.getPressure() + dp);
    system.init(3);

    for (int phase = 0; phase < 2; phase++) {
      for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
        double dn = system.getPhases()[phase].getComponents()[k].getNumberOfMolesInPhase() / 1.0e5;
        
        
        // 
        // 
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
            
            // 
          }
          if (phase == 1) {
            
            // 
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

  /**
   * Setter for property <code>maxError</code>.
   *
   * @param maxErr before test will report failed Set maximum allowed error in model check
   */
  public void setMaxError(double maxErr) {
    this.maxError = maxErr;
  }
}
