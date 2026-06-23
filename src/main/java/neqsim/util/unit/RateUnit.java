/*
 * RateUnit.java
 *
 * Created on 25. januar 2002, 20:22
 */

package neqsim.util.unit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.util.exception.InvalidInputException;

/**
 * RateUnit class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class RateUnit extends neqsim.util.unit.BaseUnit {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private static final String[] ALLOWED_UNITS = {"mole/sec", "mol/sec", "SI", "mol", "mole/min", "mol/min", "mole/hr",
      "mol/hr", "kmole/sec", "kmol/sec", "kmole/min", "kmol/min", "kmole/hr", "kmol/hr", "kmole/day", "kmol/day",
      "Nlitre/min", "Nlitre/sec", "Am3/hr", "m3/hr", "Am3/day", "m3/day", "Am3/min", "m3/min", "Am3/sec", "m3/sec",
      "kg/sec", "kg/min", "kg/hr", "kg/day", "Sm^3/sec", "Sm3/sec", "Sm^3/min", "Sm3/min", "Sm^3/hr", "Sm3/hr",
      "Sm^3/day", "Sm3/day", "MSm^3/day", "MSm3/day", "MSm^3/hr", "MSm3/hr", "idSm3/sec", "idSm3/min", "idSm3/hr",
      "idSm3/day", "gallons/min", "lb/hr", "lbmole/hr", "lbmol/hr", "barrel/day", "bbl/day"};
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(RateUnit.class);

  double molarmass = 0.0;
  double stddens = 0.0;
  double boilp = 0.0;

  /**
   * Constructor for RateUnit.
   *
   * @param value a double
   * @param unit a {@link java.lang.String} object
   * @param molarmass a double
   * @param stddens a double
   * @param boilp a double
   */
  public RateUnit(double value, String unit, double molarmass, double stddens, double boilp) {
    super(value, unit);
    this.molarmass = molarmass;
    this.stddens = stddens;
    this.boilp = boilp;
  }

  /**
   * getConversionFactor.
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getConversionFactor(String unit) {
    double mol_m3 = 0.0;
    double mol_Sm3 = ThermodynamicConstantsInterface.atm
        / (ThermodynamicConstantsInterface.R * standardStateTemperature);
    if (boilp < 25) {
      mol_m3 = ThermodynamicConstantsInterface.atm / (ThermodynamicConstantsInterface.R * standardStateTemperature);
    } else {
      mol_m3 = 1.0 / (molarmass) * stddens * 1000;
    }
    double factor = 1.0;

    if (unit.equals("mole/sec") || unit.equals("mol/sec") || unit.equals("SI") || unit.equals("mol")) {
      factor = 1.0;
    } else if (unit.equals("mole/min") || unit.equals("mol/min")) {
      factor = 1.0 / 60.0;
    } else if (unit.equals("mole/hr") || unit.equals("mol/hr")) {
      factor = 1.0 / 3600.0;
    } else if (unit.equals("kmole/sec") || unit.equals("kmol/sec")) {
      factor = 1000.0;
    } else if (unit.equals("kmole/min") || unit.equals("kmol/min")) {
      factor = 1000.0 / 60.0;
    } else if (unit.equals("kmole/hr") || unit.equals("kmol/hr")) {
      factor = 1000.0 / 3600.0;
    } else if (unit.equals("kmole/day") || unit.equals("kmol/day")) {
      factor = 1000.0 / (3600.0 * 24.0);
    } else if (unit.equals("Nlitre/min")) {
      factor = 1.0 / 60.0 * mol_m3 / 1000.0;
    } else if (unit.equals("Nlitre/sec")) {
      factor = mol_m3 / 1000.0;
    } else if (unit.equals("Am3/hr") || unit.equals("m3/hr")) {
      factor = 1.0 / molarmass / 3600.0 * stddens;
    } else if (unit.equals("Am3/day") || unit.equals("m3/day")) {
      factor = 1.0 / molarmass / (3600.0 * 24.0) * stddens;
    } else if (unit.equals("Am3/min") || unit.equals("m3/min")) {
      factor = 1.0 / molarmass / 60.0 * stddens;
    } else if (unit.equals("Am3/sec") || unit.equals("m3/sec")) {
      factor = 1.0 / molarmass * stddens;
    } else if (unit.equals("kg/sec")) {
      factor = 1.0 / (molarmass);
    } else if (unit.equals("kg/min")) {
      factor = 1.0 / 60.0 * 1.0 / (molarmass);
    } else if (unit.equals("kg/hr")) {
      factor = 1.0 / molarmass / 3600.0;
    } else if (unit.equals("kg/day")) {
      factor = 1.0 / molarmass / (3600.0 * 24.0);
    } else if (unit.equals("Sm^3/sec") || unit.equals("Sm3/sec")) {
      factor = mol_Sm3;
    } else if (unit.equals("Sm^3/min") || unit.equals("Sm3/min")) {
      factor = 1.0 / 60.0 * mol_Sm3;
    } else if (unit.equals("Sm^3/hr") || unit.equals("Sm3/hr")) {
      factor = 1.0 / 60.0 / 60.0 * mol_Sm3;
    } else if (unit.equals("Sm^3/day") || unit.equals("Sm3/day")) {
      factor = 1.0 / 60.0 / 60.0 / 24.0 * mol_Sm3;
    } else if (unit.equals("MSm^3/day") || unit.equals("MSm3/day")) {
      factor = 1.0e6 * mol_Sm3 / (3600.0 * 24.0);
    } else if (unit.equals("MSm^3/hr") || unit.equals("MSm3/hr")) {
      factor = 1.0e6 * mol_Sm3 / (3600.0);
    } else if (unit.equals("idSm3/sec")) {
      factor = 1.0 / molarmass * stddens;
    } else if (unit.equals("idSm3/min")) {
      factor = 1.0 / molarmass / 60.0 * stddens;
    } else if (unit.equals("idSm3/hr")) {
      factor = 1.0 / molarmass / 3600.0 * stddens;
    } else if (unit.equals("idSm3/day")) {
      factor = 1.0 / molarmass / (3600.0 * 24.0) * stddens;
    } else if (unit.equals("gallons/min")) {
      factor = 1.0 / molarmass / 60.0 * stddens / 10.0 * 3.78541178;
    } else if (unit.equals("lb/hr")) {
      factor = 1.0 / molarmass / 3600.0 / 2.20462262;
    } else if (unit.equals("lbmole/hr") || unit.equals("lbmol/hr")) {
      factor = 1000.0 / 2.20462262 / 3600.0;
    } else if (unit.equals("barrel/day") || unit.equals("bbl/day")) {
      factor = 1.0 / molarmass / (3600.0 * 24.0) / 2.20462262 / 0.068;
    } else {
      throw new RuntimeException(new InvalidInputException(this, "getConversionFactor", "unit",
          "'" + unit + "' is not supported. Supported units: mole/sec, mol/sec, mole/min, "
              + "mol/min, mole/hr, mol/hr, kmole/sec, kmol/sec, kmole/min, kmol/min, "
              + "kmole/hr, kmol/hr, kmole/day, kmol/day, kg/sec, kg/min, kg/hr, kg/day, "
              + "lb/hr, lbmole/hr, lbmol/hr, m3/sec, Am3/sec, m3/min, Am3/min, m3/hr, "
              + "Am3/hr, m3/day, Am3/day, Sm3/sec, Sm3/min, Sm3/hr, Sm3/day, MSm3/day, "
              + "MSm3/hr, idSm3/sec, idSm3/min, idSm3/hr, idSm3/day, Nlitre/min, "
              + "Nlitre/sec, gallons/min, barrel/day, bbl/day"));
    }

    return factor;
  }

  @Override
  public String getSIUnit() {
    return "mol/sec";
  }

  /** {@inheritDoc} */
  @Override
  public double getSIvalue() {
    return getConversionFactor(inunit) / getConversionFactor("SI") * invalue;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String toUnit) {
    return getSIvalue() / getConversionFactor(toUnit);
  }

  /**
   * Convert a rate value using the generic static signature.
   *
   * <p>
   * Rate conversion depends on fluid properties, so this overload is intentionally unsupported.
   *
   * @param value value to convert
   * @param unit source unit
   * @param toUnit target unit
   * @return never returns normally
   */
  public static double convert(double value, String unit, String toUnit) {
    throw new UnsupportedOperationException(
        "Rate conversion requires fluid properties. " + "Use convert(value, unit, toUnit, molarmass, stddens, boilp).");
  }

  /**
   * Convert a rate value between units using fluid properties.
   *
   * @param value value to convert
   * @param unit source unit
   * @param toUnit target unit
   * @param molarmass molar mass
   * @param stddens standard density
   * @param boilp boiling point proxy
   * @return converted rate value
   */
  public static double convert(double value, String unit, String toUnit, double molarmass, double stddens,
      double boilp) {
    return new RateUnit(value, unit, molarmass, stddens, boilp).getValue(toUnit);
  }
}
