package neqsim.processSimulation.processEquipment;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

public interface TwoPortInterface {
  /**
   * Get inlet pressure of twoport.
   * 
   * @return inlet pressure of TwoPortEquipment in unit bara
   */
  public double getInletPressure();

  /**
   * Get inlet Stream of twoport.
   *
   * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  @Deprecated
  default public StreamInterface getInStream() {
    return getInletStream();
  }

  /**
   * Get inlet Stream of twoport.
   * 
   * @return inlet Stream of TwoPortEquipment
   */
  public StreamInterface getInletStream();

  /**
   * Get inlet temperature of twoport.
   * 
   * @return inlet temperature of TwoPortEquipment in unit kelvin
   */
  public double getInletTemperature();

  /**
   * Get outlet pressure of twoport.
   * 
   * @return outlet pressure of TwoPortEquipment in unit bara
   */
  public double getOutletPressure();

  /**
   * Get outlet Stream of twoport.
   *
   * @return outlet Stream of TwoPortEquipment
   * @deprecated use {@link #getOutletStream()} instead
   */
  @Deprecated
  default public StreamInterface getOutStream() {
    return getOutletStream();
  }

  /**
   * Get outlet Stream of twoport.
   * 
   * @return outlet Stream of TwoPortEquipment
   */
  public StreamInterface getOutletStream();

  /**
   * Get outlet temperature of twoport.
   * 
   * @return outlet temperature of TwoPortEquipment in unit kelvin
   */
  public double getOutletTemperature();

  /**
   * Set inlet pressure of twoport.
   * 
   * @param pressure value to set in unit bara
   */
  public void setInletPressure(double pressure);

  /**
   * Set inlet Stream of twoport.
   *
   * @param inletStream value to set
   */
  public void setInletStream(StreamInterface inletStream);

  /**
   * Set inlet temperature of twoport.
   * 
   * @param temperature value to set in unit kelvin
   */
  public void setInletTemperature(double temperature);

  /**
   * Set outlet pressure of twoport.
   * 
   * @param pressure value to set in unit bara
   */
  public void setOutletPressure(double pressure);

  /**
   * Set outlet Stream of twoport.
   * 
   * @param stream value to set
   */
  public void setOutletStream(StreamInterface stream);

  /**
   * Set outlet temperature of twoport.
   * 
   * @param temperature value to set in kelvin
   */
  public void setOutletTemperature(double temperature);
}
