/*
 * FluidBoundaryInterface.java
 *
 * Created on 11. desember 2000, 17:17
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc;

import Jama.Matrix;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor.EnhancementFactor;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * FluidBoundaryInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface FluidBoundaryInterface extends Cloneable {
  /**
   * <p>
   * getInterphaseSystem.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getInterphaseSystem();

  /**
   * <p>
   * setInterphaseSystem.
   * </p>
   *
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setInterphaseSystem(SystemInterface interphaseSystem);

  /**
   * <p>
   * solve.
   * </p>
   */
  public void solve();

  /**
   * <p>
   * massTransSolve.
   * </p>
   */
  public void massTransSolve();

  /**
   * <p>
   * write.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param filename a {@link java.lang.String} object
   * @param newfile a boolean
   */
  public void write(String name, String filename, boolean newfile);

  /**
   * <p>
   * display.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void display(String name);

  /**
   * <p>
   * heatTransSolve.
   * </p>
   */
  public void heatTransSolve();

  /**
   * <p>
   * setEnhancementType.
   * </p>
   *
   * @param type a int
   */
  public void setEnhancementType(int type);

  /**
   * <p>
   * getEnhancementFactor.
   * </p>
   *
   * @return a
   *         {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor.EnhancementFactor}
   *         object
   */
  public EnhancementFactor getEnhancementFactor();

  /**
   * <p>
   * getInterphaseHeatFlux.
   * </p>
   *
   * @param phase a int
   * @return a double
   */
  public double getInterphaseHeatFlux(int phase);

  /**
   * <p>
   * getBulkSystemOpertions.
   * </p>
   *
   * @return a {@link neqsim.thermodynamicOperations.ThermodynamicOperations} object
   */
  public ThermodynamicOperations getBulkSystemOpertions();

  /**
   * <p>
   * calcFluxes.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] calcFluxes();

  /**
   * <p>
   * getMassTransferCoefficientMatrix.
   * </p>
   *
   * @return an array of {@link Jama.Matrix} objects
   */
  public Matrix[] getMassTransferCoefficientMatrix();

  /**
   * <p>
   * getBinaryMassTransferCoefficient.
   * </p>
   *
   * @param phase a int
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getBinaryMassTransferCoefficient(int phase, int i, int j);

  /**
   * <p>
   * getBulkSystem.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getBulkSystem();

  /**
   * <p>
   * isHeatTransferCalc.
   * </p>
   *
   * @return a boolean
   */
  public boolean isHeatTransferCalc();

  /**
   * <p>
   * setHeatTransferCalc.
   * </p>
   *
   * @param heatTransferCalc a boolean
   */
  public void setHeatTransferCalc(boolean heatTransferCalc);

  /**
   * <p>
   * setMassTransferCalc.
   * </p>
   *
   * @param heatTransferCalc a boolean
   */
  public void setMassTransferCalc(boolean heatTransferCalc);

  /**
   * <p>
   * getInterphaseMolarFlux.
   * </p>
   *
   * @param component a int
   * @return a double
   */
  public double getInterphaseMolarFlux(int component);

  /**
   * <p>
   * getEffectiveMassTransferCoefficient.
   * </p>
   *
   * @param phase a int
   * @param i a int
   * @return a double
   */
  public double getEffectiveMassTransferCoefficient(int phase, int i);

  /**
   * <p>
   * clone.
   * </p>
   *
   * @return a
   *         {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface}
   *         object
   */
  public FluidBoundaryInterface clone();

  /**
   * <p>
   * useThermodynamicCorrections.
   * </p>
   *
   * @param phase a int
   * @return a boolean
   */
  public boolean useThermodynamicCorrections(int phase);

  /**
   * <p>
   * useThermodynamicCorrections.
   * </p>
   *
   * @param thermodynamicCorrections a boolean
   */
  public void useThermodynamicCorrections(boolean thermodynamicCorrections);

  /**
   * <p>
   * useThermodynamicCorrections.
   * </p>
   *
   * @param thermodynamicCorrections a boolean
   * @param phase a int
   */
  public void useThermodynamicCorrections(boolean thermodynamicCorrections, int phase);

  /**
   * <p>
   * useFiniteFluxCorrection.
   * </p>
   *
   * @param phase a int
   * @return a boolean
   */
  public boolean useFiniteFluxCorrection(int phase);

  /**
   * <p>
   * useFiniteFluxCorrection.
   * </p>
   *
   * @param finiteFluxCorrection a boolean
   */
  public void useFiniteFluxCorrection(boolean finiteFluxCorrection);

  /**
   * <p>
   * useFiniteFluxCorrection.
   * </p>
   *
   * @param finiteFluxCorrection a boolean
   * @param phase a int
   */
  public void useFiniteFluxCorrection(boolean finiteFluxCorrection, int phase);
}
