/*
 * TPgradientFlash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import java.util.Arrays;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * TPgradientFlash class handles thermodynamic calculations with temperature and pressure gradients.
 *
 * @author ESOL
 * @version 1.0
 */
public class TPgradientFlash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private static final LinearAlgebraOperations ALGEBRA = new EjmlLinearAlgebra();
  private SystemInterface system;
  private double temperature;
  private double height;
  private double[][] Jac;
  private double[] fvec;
  private double[] dx;
  private SystemInterface localSystem;
  private SystemInterface tempSystem;
  private double deltaHeight;
  private double deltaT;

  /**
   * Default constructor for TPgradientFlash.
   */
  public TPgradientFlash() {
  }

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
    Jac = new double[numComponents + 1][numComponents + 1];
    fvec = new double[numComponents + 1];
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
      ComponentInterface component = localSystem.getPhase(0).getComponent(i);
      double fugacityCoeff = component.getFugacityCoefficient();
      double componentX = component.getx();
      double pressure = localSystem.getPressure();

      // Breakdown of terms for readability
      double logTerm = Math.log(fugacityCoeff) + Math.log(componentX) + Math.log(pressure);
      double molarMassTerm = component.getMolarMass() * gravity * deltaHeight
          / (gasConstant * tempSystem.getPhase(0).getTemperature());

      ComponentInterface referenceComponent = tempSystem.getPhase(0).getComponent(i);
      double referenceLogFugacity = Math.log(referenceComponent.getFugacityCoefficient())
          + Math.log(referenceComponent.getx()) + Math.log(tempSystem.getPressure());
      fvec[i] = logTerm - referenceLogFugacity - molarMassTerm;
      sumx += componentX;
    }

    fvec[numComponents] = sumx - 1.0;
  }

  /**
   * Sets the Jacobian of log fugacity and composition normalization.
   *
   * <p>
   * The component derivatives already represent derivatives of the logarithmic fugacity coefficient, so they must not
   * be divided by the fugacity coefficient again.
   * </p>
   */
  public void setJac() {
    for (double[] row : Jac) {
      Arrays.fill(row, 0.0);
    }
    int numComponents = system.getPhase(0).getNumberOfComponents();

    for (int i = 0; i < numComponents; i++) {
      for (int j = 0; j < numComponents; j++) {
        double dij = (i == j) ? 1.0 : 0.0; // Kronecker delta
        double componentX = localSystem.getPhases()[0].getComponent(i).getx();

        double tempJ = dij / componentX + localSystem.getPhases()[0].getComponent(i).getdfugdx(j);
        Jac[i][j] = tempJ;
      }
    }

    // Set the last row of Jac
    for (int j = 0; j < numComponents; j++) {
      Jac[numComponents][j] = 1.0;
    }

    for (int i = 0; i < numComponents; i++) {
      double pressure = localSystem.getPressure();

      double tempJ = 1.0 / pressure + localSystem.getPhases()[0].getComponent(i).getdfugdp();
      Jac[i][numComponents] = tempJ;
    }

    Jac[numComponents][numComponents] = 0.0;
  }

  /**
   * Updates the composition and pressure in the local system based on calculated adjustments.
   */
  public void setNewX() {
    int numComponents = system.getPhase(0).getNumberOfComponents();
    double relaxationFactor = 1.0;
    for (int i = 0; i < numComponents; i++) {
      double composition = localSystem.getPhase(0).getComponent(i).getx();
      if (dx[i] > 0.0 && dx[i] >= composition) {
        relaxationFactor = Math.min(relaxationFactor, 0.5 * composition / dx[i]);
      }
    }
    if (dx[numComponents] >= localSystem.getPressure()) {
      relaxationFactor = Math.min(relaxationFactor, 0.5 * localSystem.getPressure() / dx[numComponents]);
    }

    for (int i = 0; i < numComponents; i++) {
      ComponentInterface component = localSystem.getPhase(0).getComponent(i);
      double newX = component.getx() - relaxationFactor * dx[i];
      component.setx(newX);
    }

    // Update system pressure
    double newPressure = localSystem.getPressure() - relaxationFactor * dx[numComponents];
    localSystem.setPressure(newPressure);

  }

  /**
   * Returns the largest dimensionless Newton correction.
   *
   * @return maximum relative-composition or relative-pressure correction
   */
  private double correctionNorm() {
    double norm = Math.abs(dx[dx.length - 1]) / localSystem.getPressure();
    for (int i = 0; i < dx.length - 1; i++) {
      double composition = localSystem.getPhase(0).getComponent(i).getx();
      norm = Math.max(norm, Math.abs(dx[i]) / Math.max(Math.abs(composition), 1.0e-30));
    }
    return norm;
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
      double tolerance = 1e-13; // Dimensionless relative-composition and relative-pressure correction
      int maxIterations = 50; // Maximum allowable iterations

      do {
        iter++;
        localSystem.init(3); // Initialize system properties
        setfvec(); // Calculate the function vector
        setJac(); // Calculate the Jacobian matrix
        dx = ALGEBRA.solve(Jac, fvec); // Solve for updates (dx)
        if (!Double.isFinite(correctionNorm())) {
          throw new IllegalStateException("Gradient flash produced a nonfinite Newton correction at step " + step);
        }
        setNewX(); // Update composition and pressure
      } while (correctionNorm() > tolerance && iter < maxIterations);

      if (correctionNorm() > tolerance) {
        throw new IllegalStateException(
            "Gradient flash did not converge after " + maxIterations + " iterations at step " + step);
      }

      // Verify the accepted update and retain initialized properties for the next depth step.
      localSystem.init(3);
      setfvec();
      double residualNorm = 0.0;
      for (double residual : fvec) {
        residualNorm = Math.max(residualNorm, Math.abs(residual));
      }
      if (!Double.isFinite(residualNorm) || residualNorm > 1.0e-10) {
        throw new IllegalStateException("Gradient flash residual did not converge at step " + step);
      }
      tempSystem = localSystem.clone();
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return localSystem;
  }
}
