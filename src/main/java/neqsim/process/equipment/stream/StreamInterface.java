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
 * StreamInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface StreamInterface extends ProcessEquipmentInterface {
  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem();

  /**
   * setThermoSystem.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setThermoSystem(SystemInterface thermoSystem);

  /**
   * setFlowRate.
   *
   * @param flowrate a double
   * @param unit a {@link java.lang.String} object
   */
  public void setFlowRate(double flowrate, String unit);

  /** {@inheritDoc} */
  @Override
  public double getPressure();

  /** {@inheritDoc} */
  @Override
  public double getPressure(String unit);

  /**
   * runTPflash.
   */
  public void runTPflash();

  /** {@inheritDoc} */
  @Override
  public double getTemperature(String unit);

  /** {@inheritDoc} */
  @Override
  public double getTemperature();

  /** {@inheritDoc} */
  @Override
  public void setName(String name);

  /**
   * Calculate and return cricondentherm.
   *
   * @param unit a {@link java.lang.String} object
   * @return Calculated cricondentherm in specified unit
   */
  public double CCT(String unit);

  /**
   * Calculate and return cricondenbar.
   *
   * @param unit a {@link java.lang.String} object
   * @return Calculated cricondenbar in specified unit
   */
  public double CCB(String unit);

  /**
   * getFlowRate. Wrapper for SystemInterface.getFlowRate().
   *
   * @param unit Supported units are kg/sec, kg/min, kg/hr, kg/day, m3/sec, m3/min, m3/hr, Sm3/sec, Sm3/hr, Sm3/day,
   * MSm3/day, MSm3/hr, mole/sec, mol/sec, mole/min, mol/min, mole/hr, mol/hr, kmole/sec, kmol/sec, kmole/min, kmol/min,
   * kmole/hr, kmol/hr, kmole/day, kmol/day, lbmole/hr, lb/hr, barrel/day, gallons/min
   * @return flow rate in specified unit
   */
  public default double getFlowRate(String unit) {
    return this.getFluid().getFlowRate(unit);
  }

  /**
   * Calculates the True Vapor Pressure (TVP) of the stream.
   *
   * @param referenceTemperature a double
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double TVP(double referenceTemperature, String unit);

  /**
   * Calculates the True Vapor Pressure (TVP) of the stream.
   *
   * @param referenceTemperature a double
   * @param unit a {@link java.lang.String} object
   * @param returnUnit a {@link java.lang.String} object
   * @return a double
   */
  public double getTVP(double referenceTemperature, String unit, String returnUnit);

  /**
   * Calculates the Reid Vapor Pressure (RVP) of the stream.
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
  public double getRVP(double referenceTemperature, String unit, String returnUnit, String rvpMethod);

  /**
   * setFluid.
   *
   * @param fluid a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setFluid(SystemInterface fluid);

  /**
   * getMolarRate.
   *
   * @return a double
   */
  public double getMolarRate();

  /**
   * Clone object.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface clone();

  /**
   * Clone object and set a new name.
   *
   * @param name Name of cloned object
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface clone(String name);

  /**
   * flashStream.
   */
  public void flashStream();

  /**
   * getHydrateEquilibriumTemperature.
   *
   * @return a double
   */
  public double getHydrateEquilibriumTemperature();

  /**
   * setThermoSystemFromPhase.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseTypeName a {@link java.lang.String} object
   */
  public void setThermoSystemFromPhase(SystemInterface thermoSystem, String phaseTypeName);

  /**
   * setEmptyThermoSystem.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setEmptyThermoSystem(SystemInterface thermoSystem);

  /**
   * setPressure.
   *
   * @param pressure a double
   * @param unit a {@link java.lang.String} object
   */
  public void setPressure(double pressure, String unit);

  /**
   * setTemperature.
   *
   * @param temperature a double
   * @param unit a {@link java.lang.String} object
   */
  public void setTemperature(double temperature, String unit);

  /**
   * GCV.
   *
   * @return a double
   */
  public double GCV();

  /**
   * getGCV.
   *
   * @param unit a String
   * @param refTVolume a double in Celcius
   * @param refTCombustion a double in Celcius
   * @return a double
   */
  public double getGCV(String unit, double refTVolume, double refTCombustion);

  /**
   * getWI.
   *
   * @param unit a String
   * @param refTVolume a double in Celcius
   * @param refTCombustion a double in Celcius
   * @return a double
   */
  public double getWI(String unit, double refTVolume, double refTCombustion);

  /**
   * getWI.
   *
   * @param unit a String
   * @param refTVolume a double in Celcius
   * @param refTCombustion a double in Celcius
   * @return a Standard_ISO6976
   */
  public Standard_ISO6976 getISO6976(String unit, double refTVolume, double refTCombustion);

  /**
   * LCV.
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
   * @param temperatureUnit the unit of the temperature to be used (e.g., "C" for Celsius, "K" for Kelvin)
   * @param refpressure the reference pressure at which the dew point is to be calculated
   * @param refPressureUnit the unit of the reference pressure (e.g., "bar", "Pa")
   * @return the hydrocarbon dew point temperature in the specified temperature unit
   */
  public double getHydrocarbonDewPoint(String temperatureUnit, double refpressure, String refPressureUnit);
}
