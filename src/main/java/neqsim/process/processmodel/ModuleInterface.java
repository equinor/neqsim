/*
 * ModuleInterface.java
 *
 * Created on 1. november 2006, 21:48
 */

package neqsim.process.processmodel;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * ModuleInterface interface.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface ModuleInterface extends ProcessEquipmentInterface {
  /**
   * getOperations.
   *
   * @return a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public neqsim.process.processmodel.ProcessSystem getOperations();

  /**
   * addInputStream.
   *
   * @param streamName a {@link java.lang.String} object
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addInputStream(String streamName, StreamInterface stream);

  /**
   * getOutputStream.
   *
   * @param streamName a {@link java.lang.String} object
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOutputStream(String streamName);

  /**
   * getPreferedThermodynamicModel.
   *
   * @return a {@link java.lang.String} object
   */
  public String getPreferedThermodynamicModel();

  /**
   * setPreferedThermodynamicModel.
   *
   * @param preferedThermodynamicModel a {@link java.lang.String} object
   */
  public void setPreferedThermodynamicModel(String preferedThermodynamicModel);

  /**
   * initializeStreams.
   */
  public void initializeStreams();

  /**
   * initializeModule.
   */
  public void initializeModule();

  /**
   * setIsCalcDesign.
   *
   * @param isCalcDesign a boolean
   */
  public void setIsCalcDesign(boolean isCalcDesign);

  /**
   * isCalcDesign.
   *
   * @return a boolean
   */
  public boolean isCalcDesign();

  /**
   * getUnit.
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link java.lang.Object} object
   */
  public Object getUnit(String name);

  /**
   * setProperty.
   *
   * @param propertyName a {@link java.lang.String} object
   * @param value a double
   */
  public void setProperty(String propertyName, double value);
}
