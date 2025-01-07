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
 * <p>
 * RateUnit class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class RateUnit extends neqsim.util.unit.BaseUnit {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(RateUnit.class);

  double molarmass = 0.0;
  double stddens = 0.0;
  double boilp = 0.0;

  /**
   * <p>
   * Constructor for RateUnit.
   * </p>
   *
   * @param value a double
   * @param name a {@link java.lang.String} object
   * @param molarmass a double
   * @param stddens a double
   * @param boilp a double
   */
  public RateUnit(double value, String name, double molarmass, double stddens, double boilp) {
    super(value, name);
    this.molarmass = molarmass;
    this.stddens = stddens;
    this.boilp = boilp;
  }

  /**
   * <p>
   * getConversionFactor.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a double
   */
  public double getConversionFactor(String name) {
    double mol_m3 = 0.0;
    double mol_Sm3 = ThermodynamicConstantsInterface.atm
        / (ThermodynamicConstantsInterface.R * standardStateTemperature);
    if (boilp < 25) {
      mol_m3 = ThermodynamicConstantsInterface.atm
          / (ThermodynamicConstantsInterface.R * standardStateTemperature);
    } else {
      mol_m3 = 1.0 / (molarmass) * stddens * 1000;
    }

    if (name.equals("mole/sec") || name.equals("mol/sec") || name.equals("SI")
        || name.equals("mol")) {
      factor = 1.0;
    } else if (name.equals("Nlitre/min")) {
      factor = 1.0 / 60.0 * mol_m3 / 1000.0;
    } else if (name.equals("Nlitre/sec")) {
      factor = mol_m3 / 1000.0;
    } else if (name.equals("Am3/hr")) {
      factor = 1.0 / molarmass / 3600.0 * stddens;
    } else if (name.equals("Am3/day")) {
      factor = 1.0 / molarmass / (3600.0 * 24.0) * stddens;
    } else if (name.equals("Am3/min")) {
      factor = 1.0 / molarmass / 60.0 * stddens;
    } else if (name.equals("Am3/sec")) {
      factor = 1.0 / molarmass * stddens;
    } else if (name.equals("kg/sec")) {
      factor = 1.0 / (molarmass);
    } else if (name.equals("kg/min")) {
      factor = 1.0 / 60.0 * 1.0 / (molarmass);
    } else if (name.equals("kg/hr")) {
      factor = 1.0 / molarmass / 3600.0;
    } else if (name.equals("kg/day")) {
      factor = 1.0 / molarmass / (3600.0 * 24.0);
    } else if (name.equals("Sm^3/sec") | name.equals("Sm3/sec")) {
      factor = mol_Sm3;
    } else if (name.equals("Sm^3/min") | name.equals("Sm3/min")) {
      factor = 1.0 / 60.0 * mol_Sm3;
    } else if (name.equals("Sm^3/hr") | name.equals("Sm3/hr")) {
      factor = 1.0 / 60.0 / 60.0 * mol_Sm3;
    } else if (name.equals("Sm^3/day") || name.equals("Sm3/day")) {
      factor = 1.0 / 60.0 / 60.0 / 24.0 * mol_Sm3;
    } else if (name.equals("MSm^3/day") || name.equals("MSm3/day")) {
      factor = 1.0e6 * mol_Sm3 / (3600.0 * 24.0);
    } else if (name.equals("MSm^3/hr") || name.equals("MSm3/hr")) {
      factor = 1.0e6 * mol_Sm3 / (3600.0);
    } else if (name.equals("idSm3/hr")) {
      factor = 1.0 / molarmass / 3600.0 * stddens;
    } else if (name.equals("idSm3/day")) {
      factor = 1.0 / molarmass / (3600.0 * 24.0) * stddens;
    } else if (name.equals("gallons/min")) {
      factor = 1.0 / molarmass / 60.0 * stddens / 10.0 * 3.78541178;
    } else {
      throw new RuntimeException(
          new InvalidInputException(this, "getConversionFactor", "unit", "not supported"));
    }

    return factor;
  }

  /** {@inheritDoc} */
  @Override
  public double getSIvalue() {
    return getConversionFactor(inunit) / getConversionFactor("SI") * invalue;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String tounit) {
    return getConversionFactor(inunit) / getConversionFactor(tounit) * invalue;
  }
}
