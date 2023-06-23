/*
 * StreamInterface.java
 *
 * Created on 21. august 2001, 22:49
 */

package neqsim.processSimulation.processEquipment.stream;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * StreamInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface StreamInterface extends ProcessEquipmentInterface {
  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem();

  /**
   * <p>
   * setThermoSystem.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setThermoSystem(SystemInterface thermoSystem);

  /**
   * <p>
   * setFlowRate.
   * </p>
   *
   * @param flowrate a double
   * @param unit a {@link java.lang.String} object
   */
  public void setFlowRate(double flowrate, String unit);

  /**
   * {@inheritDoc}
   *
   * <p>
   * getPressure.
   * </p>
   */
  public double getPressure(String unit);

  /** {@inheritDoc} */
  @Override
  public double getPressure();

  /**
   * <p>
   * runTPflash.
   * </p>
   */
  public void runTPflash();

  /**
   * <p>
   * getTemperature.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getTemperature(String unit);

  /**
   * <p>
   * getTemperature.
   * </p>
   *
   * @return a double
   */
  public double getTemperature();

  /** {@inheritDoc} */
  @Override
  public void setName(String name);

  /**
   * <p>
   * CCT.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double CCT(String unit);

  /**
   * <p>
   * CCB.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double CCB(String unit);

  /**
   * <p>
   * getFlowRate.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getFlowRate(String unit);

  /**
   * <p>
   * TVP.
   * </p>
   *
   * @param temperature a double
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double TVP(double temperature, String unit);

  /**
   * <p>
   * setFluid.
   * </p>
   *
   * @param fluid a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setFluid(SystemInterface fluid);

  /**
   * <p>
   * getMolarRate.
   * </p>
   *
   * @return a double
   */
  public double getMolarRate();

  /**
   * <p>
   * clone.
   * </p>
   *
   * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public StreamInterface clone();

  /**
   * <p>
   * flashStream.
   * </p>
   */
  public void flashStream();

  /**
   * <p>
   * getHydrateEquilibriumTemperature.
   * </p>
   *
   * @return a double
   */
  public double getHydrateEquilibriumTemperature();

  /**
   * <p>
   * setThermoSystemFromPhase.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseTypeName a {@link java.lang.String} object
   */
  public void setThermoSystemFromPhase(SystemInterface thermoSystem, String phaseTypeName);

  /**
   * <p>
   * setEmptyThermoSystem.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setEmptyThermoSystem(SystemInterface thermoSystem);

  /**
   * <p>
   * setPressure.
   * </p>
   *
   * @param pressure a double
   * @param unit a {@link java.lang.String} object
   */
  public void setPressure(double pressure, String unit);

  /**
   * <p>
   * setTemperature.
   * </p>
   *
   * @param temperature a double
   * @param unit a {@link java.lang.String} object
   */
  public void setTemperature(double temperature, String unit);

  /**
   * <p>
   * GCV.
   * </p>
   *
   * @return a double
   */
  public double GCV();

  /**
   * <p>
   * LCV.
   * </p>
   *
   * @return a double
   */
  public double LCV();

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o);

  /** {@inheritDoc} */
  @Override
  public int hashCode();

}
