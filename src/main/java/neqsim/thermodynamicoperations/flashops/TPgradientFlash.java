/*
 * TPgradientFlash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import Jama.Matrix;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * TPgradientFlash class handles thermodynamic calculations with temperature and pressure gradients.
 *
 * @author ASMF
 */
public class TPgradientFlash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private SystemInterface system;
  private double temperature;
  private double height;
  private Matrix Jac;
  private Matrix fvec;
  private Matrix dx;
  private SystemInterface localSystem;
  private SystemInterface tempSystem;
  private double deltaHeight;
  private double deltaT;

  /**
   * Default constructor for TPgradientFlash.
   */
  public TPgradientFlash() {}

  /**
   * Constructor for TPgradientFlash with system, height, and temperature.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param height a double representing the height
   * @param temperature a double representing the temperature
   */
  public TPgradientFlash(SystemInterface system, double height, double temperature) {
    this.system = system;
    this.temperature = temperature;
    this.height = height;
    int numComponents = system.getPhase(0).getNumberOfComponents();
    Jac = new Matrix(numComponents + 1, numComponents + 1);
    fvec = new Matrix(numComponents + 1, 1);
  }

  /**
   * Sets the fvec matrix values based on the thermodynamic calculations.
   */
  public void setfvec() {
    double sumx = 0.0;
    int numComponents = system.getPhase(0).getNumberOfComponents();
    double gravity = neqsim.thermo.ThermodynamicConstantsInterface.gravity;
    double gasConstant = neqsim.thermo.ThermodynamicConstantsInterface.R;

    for (int i = 0; i < numComponents; i++) {
      ComponentInterface component = (ComponentInterface) localSystem.getPhase(0).getComponent(i);
      double fugacityCoeff = component.getFugacityCoefficient();
      double componentX = component.getx();
      double pressure = localSystem.getPressure();

      // Breakdown of terms for readability
      double logTerm = Math.log(fugacityCoeff * componentX * pressure);
      double molarMassTerm = component.getMolarMass() * gravity * deltaHeight
          / (gasConstant * tempSystem.getPhase(0).getTemperature());

      fvec.set(i, 0,
          logTerm
              - Math.log(tempSystem.getPhases()[0].getComponent(i).getFugacityCoefficient()
                  * tempSystem.getPhases()[0].getComponent(i).getx() * tempSystem.getPressure())
              - molarMassTerm);
      sumx += componentX;
    }

    fvec.set(numComponents, 0, sumx - 1.0);
  }

  /**
   * Sets the Jacobian (Jac) matrix values based on the thermodynamic calculations.
   */
  public void setJac() {
    Jac.timesEquals(0.0);
    int numComponents = system.getPhase(0).getNumberOfComponents();

    for (int i = 0; i < numComponents; i++) {
      for (int j = 0; j < numComponents; j++) {
        double dij = (i == j) ? 1.0 : 0.0; // Kronecker delta
        double fugacityCoeff = localSystem.getPhases()[0].getComponent(i).getFugacityCoefficient();
        double componentX = localSystem.getPhases()[0].getComponent(i).getx();
        double pressure = localSystem.getPressure();

        double tempJ =
            1.0 / (fugacityCoeff * componentX * pressure) * (fugacityCoeff * dij * pressure
                + localSystem.getPhases()[0].getComponent(i).getdfugdx(j) * componentX * pressure);
        Jac.set(i, j, tempJ);
      }
    }

    // Set the last row of Jac
    for (int j = 0; j < numComponents; j++) {
      Jac.set(numComponents, j, 1.0);
    }

    for (int i = 0; i < numComponents; i++) {
      double fugacityCoeff = localSystem.getPhases()[0].getComponent(i).getFugacityCoefficient();
      double componentX = localSystem.getPhases()[0].getComponent(i).getx();
      double pressure = localSystem.getPressure();

      double tempJ = 1.0 / (fugacityCoeff * componentX * pressure)
          * (localSystem.getPhases()[0].getComponent(i).getdfugdp() * componentX * pressure
              + fugacityCoeff * componentX);
      Jac.set(i, numComponents, tempJ);
    }

    Jac.set(numComponents, numComponents, 0.0);
  }

  /**
   * Updates the composition and pressure in the local system based on calculated adjustments.
   */
  public void setNewX() {
    int numComponents = system.getPhase(0).getNumberOfComponents();
    double relaxationFactor = 0.8; // Relaxation factor for numerical stability

    for (int i = 0; i < numComponents; i++) {
      ComponentInterface component = (ComponentInterface) localSystem.getPhase(0).getComponent(i);
      double newX = component.getx() - relaxationFactor * dx.get(i, 0);
      component.setx(newX);
    }

    // Update system pressure
    double newPressure = localSystem.getPressure() - relaxationFactor * dx.get(numComponents, 0);
    localSystem.setPressure(newPressure);

    // Optional: Normalize phase composition
    // localSystem.getPhase(0).normalize();
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // Clone the system for temporary calculations
    tempSystem = system.clone();
    tempSystem.init(0);
    tempSystem.init(3);

    // Clone the system for local calculations
    localSystem = system.clone();

    // Initialize delta values for temperature and height
    deltaT = (temperature - system.getTemperature()) / 20.0;
    deltaHeight = height / 20.0;

    for (int step = 0; step < 20; step++) {
      // Incrementally adjust the temperature in the local system
      localSystem.setTemperature(localSystem.getTemperature() + deltaT);

      int iter = 0;
      double tolerance = 1e-10; // Convergence criterion
      int maxIterations = 50; // Maximum allowable iterations

      do {
        iter++;
        localSystem.init(3); // Initialize system properties
        setfvec(); // Calculate the function vector
        setJac(); // Calculate the Jacobian matrix
        dx = Jac.solve(fvec); // Solve for updates (dx)
        setNewX(); // Update composition and pressure
      } while (dx.norm2() > tolerance && iter < maxIterations);

      // Clone the updated local system into tempSystem
      tempSystem = localSystem.clone();
      tempSystem.init(3); // Re-initialize tempSystem for the next step
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return localSystem;
  }
}

