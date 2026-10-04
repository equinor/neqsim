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
   * @param unit Supported units are kg/sec, kg/min, kg/hr, kg/day, m3/sec, Am3/sec, m3/min, Am3/min, m3/hr, Am3/hr,
   * m3/day, Am3/day, idSm3/sec, idSm3/min, idSm3/hr, idSm3/day, Sm3/sec, Sm3/min, Sm3/hr, Sm3/day, MSm3/day, MSm3/hr,
   * mole/sec, mol/sec, mole/min, mol/min, mole/hr, mol/hr, kmole/sec, kmol/sec, kmole/min, kmol/min, kmole/hr, kmol/hr,
   * kmole/day, kmol/day, lbmole/hr, lbmol/hr, lb/hr, barrel/day, bbl/day, gallons/min
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
   * Calculates EOS bubble-point TVP for the supplied composition at the specified temperature.
   *
   * <p>
   * No water removal or external water-saturation correction is performed. A declared water-contact approximation on an
   * audited dry model adds the full independent water saturation pressure at this same temperature. TVP at 30 degrees
   * Celsius is not interchangeable with VPCR4 or correlated RVPE at 37.8 degrees Celsius. See the
   * <a href="https://equinor.github.io/neqsim/standards/astm_d6377_rvp.html">water-basis guide</a>.
   *
   * @param referenceTemperature reference temperature within the fluid model's validity range
   * @param unit temperature unit, for example "C" or "K"
   * @param returnUnit pressure unit; use an absolute unit such as "bara" or "kPa" for reporting
   * @return bubble-point pressure in the requested unit; the Stream implementation returns zero on flash exception
   */
  public double getTVP(double referenceTemperature, String unit, String returnUnit);

  /**
   * Calculates raw VPCR4 (vapor/liquid volume ratio 4:1), not correlated RVPE.
   *
   * <p>
   * Despite the method name, this overload defaults to "VPCR4", not "RVP_ASTM_D6377". It uses the supplied composition
   * without independent water-saturation addition. A positive value is not a convergence certificate or laboratory
   * compliance result; verify the corrected volume ratio.
   *
   * @param referenceTemperature reference temperature, normally 37.8 degrees Celsius
   * @param unit temperature unit, for example "C" or "K"
   * @param returnUnit pressure unit; use an absolute unit such as "bara" or "kPa" for reporting
   * @return raw VPCR4 in the requested unit, or zero if standard evaluation throws
   * @see #getRVP(double, String, String, String)
   */
  public double getRVP(double referenceTemperature, String unit, String returnUnit);

  /**
   * Calculates the explicitly selected vapor-pressure result.
   *
   * <p>
   * "RVP_ASTM_D6377" returns 0.834 times VPCR4 on the supplied composition. "VPCR4_no_water" returns raw dry VPCR4, not
   * dry RVPE; audit all-phase characterization and retained BIPs before using that removal path. For a declared
   * water-contact approximation, multiply the sum of audited dry VPCR4 and independent water saturation pressure by
   * 0.834. Do not add the full water pressure to an already correlated RVPE. See the
   * <a href="https://equinor.github.io/neqsim/standards/astm_d6377_rvp.html">water-basis guide</a>.
   *
   * @param referenceTemperature reference temperature, normally 37.8 degrees Celsius
   * @param unit temperature unit, for example "C" or "K"
   * @param returnUnit pressure unit; use an absolute unit such as "bara" or "kPa" for reporting
   * @param rvpMethod legacy method label from Standard_ASTM_D6377.RvpMethod; use its getLabel() value
   * @return selected result in the requested unit; check validity and convergence before interpretation
   * @see neqsim.standards.oilquality.Standard_ASTM_D6377
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
   * Return the ISO 6976:1995 inferior calorific value on the legacy volume basis. The real-gas volume reference is 0 C
   * at reference pressure; combustion is at 15.55 C. This is not a 15 C standard-volume value. For energy rates use a
   * molar calorific value from {@link #getISO6976(String, double, double)} multiplied by molar flow.
   *
   * @return inferior calorific value in J/m3 at 0 C
   */
  public double LCV();

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
