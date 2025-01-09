/*
 * StreamInterface.java
 *
 * Created on 21. august 2001, 22:49
 */

package neqsim.process.equipment.stream;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.standards.gasquality.Standard_ISO6976;
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
  @Override
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
   * getFlowRate. Wrapper for SystemInterface.getFlowRate().
   * </p>
   *
   * @param unit Supported units are kg/sec, kg/min, kg/hr, kg/day, m3/sec, m3/min, m3/hr, Sm3/sec,
   *        Sm3/hr, Sm3/day, MSm3/day, mole/sec, mole/min, mole/hr
   * @return flow rate in specified unit
   */
  public default double getFlowRate(String unit) {
    return this.getFluid().getFlowRate(unit);
  }

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
   * TVP.
   * </p>
   *
   * @param referenceTemperature a double
   * @param unit a {@link java.lang.String} object
   * @param returnUnit a {@link java.lang.String} object
   * @return a double
   */
  public double getTVP(double referenceTemperature, String unit, String returnUnit);

  /**
   * <p>
   * TVP.
   * </p>
   *
   * @param referenceTemperature a double
   * @param unit a {@link java.lang.String} object
   * @param returnUnit a {@link java.lang.String} object
   * @return a double
   */
  public double getRVP(double referenceTemperature, String unit, String returnUnit);


  /**
   * Calculates the Reid Vapor Pressure (RVP) of the stream.
   *
   * @param referenceTemperature the reference temperature at which RVP is calculated
   * @param unit the unit of the reference temperature
   * @param returnUnit the unit in which the RVP should be returned
   * @param rvpMethod the method used to calculate RVP
   * @return the calculated RVP in the specified return unit
   */
  public double getRVP(double referenceTemperature, String unit, String returnUnit,
      String rvpMethod);

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
   * Clone object.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface clone();

  /**
   * <p>
   * Clone object and set a new name.
   * </p>
   *
   * @param name Name of cloned object
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface clone(String name);

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
   * getGCV.
   * </p>
   *
   * @param unit a String
   * @param refTVolume a double in Celcius
   * @param refTCombustion a double in Celcius
   * @return a double
   */
  public double getGCV(String unit, double refTVolume, double refTCombustion);

  /**
   * <p>
   * getWI.
   * </p>
   *
   * @param unit a String
   * @param refTVolume a double in Celcius
   * @param refTCombustion a double in Celcius
   * @return a double
   */
  public double getWI(String unit, double refTVolume, double refTCombustion);

  /**
   * <p>
   * getWI.
   * </p>
   *
   * @param unit a String
   * @param refTVolume a double in Celcius
   * @param refTCombustion a double in Celcius
   * @return a Standard_ISO6976
   */
  public Standard_ISO6976 getISO6976(String unit, double refTVolume, double refTCombustion);

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


  /**
   * Calculates the hydrocarbon dew point of the stream.
   *
   * @param temperatureUnit the unit of the temperature to be used (e.g., "C" for Celsius, "K" for
   *        Kelvin)
   * @param refpressure the reference pressure at which the dew point is to be calculated
   * @param refPressureUnit the unit of the reference pressure (e.g., "bar", "Pa")
   * @return the hydrocarbon dew point temperature in the specified temperature unit
   */
  public double getHydrocarbonDewPoint(String temperatureUnit, double refpressure,
      String refPressureUnit);
}
