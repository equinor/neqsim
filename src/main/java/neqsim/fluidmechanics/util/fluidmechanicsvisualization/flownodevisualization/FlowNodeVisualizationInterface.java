/*
 * FlowNodeIVisualizationInterface.java
 *
 * Created on 5. august 2001, 16:28
 */

package neqsim.fluidmechanics.util.fluidmechanicsvisualization.flownodevisualization;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;

/**
 * FlowNodeVisualizationInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FlowNodeVisualizationInterface {
  /**
   * setData.
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public void setData(FlowNodeInterface node);

  /**
   * getPressure.
   *
   * @param i a int
   * @return a double
   */
  public double getPressure(int i);

  /**
   * getTemperature.
   *
   * @param i a int
   * @return a double
   */
  public double getTemperature(int i);

  /**
   * getDistanceToCenterOfNode.
   *
   * @return a double
   */
  public double getDistanceToCenterOfNode();

  /**
   * getVelocity.
   *
   * @param i a int
   * @return a double
   */
  public double getVelocity(int i);

  /**
   * getBulkComposition.
   *
   * @param i a int
   * @param phase a int
   * @return a double
   */
  public double getBulkComposition(int i, int phase);

  /**
   * getInterfaceComposition.
   *
   * @param i a int
   * @param phase a int
   * @return a double
   */
  public double getInterfaceComposition(int i, int phase);

  /**
   * Get number of components added.
   *
   * @return the number of components.
   */
  public int getNumberOfComponents();

  /**
   * getPhaseFraction.
   *
   * @param phase a int
   * @return a double
   */
  public double getPhaseFraction(int phase);

  /**
   * getMolarFlux.
   *
   * @param i a int
   * @param phase a int
   * @return a double
   */
  public double getMolarFlux(int i, int phase);

  /**
   * getInterfaceTemperature.
   *
   * @param i a int
   * @return a double
   */
  public double getInterfaceTemperature(int i);

  /**
   * getInterphaseContactLength.
   *
   * @return a double
   */
  public double getInterphaseContactLength();

  /**
   * getWallContactLength.
   *
   * @param phase a int
   * @return a double
   */
  public double getWallContactLength(int phase);

  /**
   * getReynoldsNumber.
   *
   * @param i a int
   * @return a double
   */
  public double getReynoldsNumber(int i);

  /**
   * getEffectiveMassTransferCoefficient.
   *
   * @param i a int
   * @param phase a int
   * @return a double
   */
  public double getEffectiveMassTransferCoefficient(int i, int phase);

  /**
   * getEffectiveSchmidtNumber.
   *
   * @param i a int
   * @param phase a int
   * @return a double
   */
  public double getEffectiveSchmidtNumber(int i, int phase);
}
