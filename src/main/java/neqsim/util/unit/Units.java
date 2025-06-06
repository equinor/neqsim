package neqsim.util.unit;

import java.util.HashMap;

/**
 * <p>
 * Units class.
 * </p>
 *
 * @author even
 * @version $Id: $Id
 */
public class Units {
  /**
   * Unit class nested within Units.
   */
  public class UnitDescription {
    public String symbol;
    public String symbolName;

    public UnitDescription(String symbol, String symbolName) {
      this.symbol = symbol;
      this.symbolName = symbolName;
    }
  }

  /** Constant <code>activeUnits</code> */
  public static HashMap<String, UnitDescription> activeUnits = new HashMap<>();
  /** Constant <code>defaultUnits</code> */
  public static HashMap<String, UnitDescription> defaultUnits = new HashMap<>();
  /** Constant <code>metricUnits</code> */
  public static HashMap<String, UnitDescription> metricUnits = new HashMap<>();
  private static HashMap<String, UnitDescription> siUnits = new HashMap<>();
  private static HashMap<String, UnitDescription> fieldUnits = new HashMap<>();

  private static String[] pressureUnits =
      new String[] {"Pa", "bara", "barg", "psi", "psig", "psia"};
  private static String[] temperatureUnits = new String[] {"K", "C", "F", "R"};
  private static String[] molarVolumeUnits = new String[] {"mol/m3", "litre/m3", "ft3/lbmole"};

  /**
   * <p>
   * Constructor for Units.
   * </p>
   */
  public Units() {
    if (activeUnits.size() == 0) {
      activeUnits.put("temperature", new UnitDescription("C", "Celsius"));
      activeUnits.put("pressure", new UnitDescription("bara", "bar absolute"));
      activeUnits.put("enthalpy", new UnitDescription("J/kg", "Joule per kg"));
      activeUnits.put("entropy", new UnitDescription("J/kgK", "Joule per kg and Kelvin"));
      activeUnits.put("density", new UnitDescription("kg/m3", "kg per cubic meter"));
      activeUnits.put("viscosity", new UnitDescription("kg/msec", "kg per meter and second"));
      activeUnits.put("surface tension", new UnitDescription("N/m", "newton per meter"));
      activeUnits.put("thermal conductivity",
          new UnitDescription("W/mK", "watts per meter and Kelvin"));
      activeUnits.put("JT coefficient", new UnitDescription("C/bar", "Celsius per bar"));
      activeUnits.put("speed of sound", new UnitDescription("m/sec", "meter per second"));
      activeUnits.put("Heat Capacity (Cv)",
          new UnitDescription("kJ/kgK", "kilo joule per kg and Kelvin"));
      activeUnits.put("Heat Capacity (Cp)",
          new UnitDescription("kJ/kgK", "kilo joule per kg and Kelvin"));
      activeUnits.put("Molar Mass", new UnitDescription("kg/mol", "kilo gram per kilo mole"));
      activeUnits.put("molar volume", new UnitDescription("m3/mol", "cubic metre per mole"));
      activeUnits.put("mass flow", new UnitDescription("kg/hr", "kg per hour"));
      activeUnits.put("molar flow", new UnitDescription("mole/hr", "mole per hour"));
      activeUnits.put("volume flow", new UnitDescription("m3/hr", "cubic metre per hour"));
      activeUnits.put("standard volume flow",
          new UnitDescription("Sm3/hr", "standard cubic metre per hour"));
      activeUnits.put("duty", new UnitDescription("W", "watt"));
      activeUnits.put("power", new UnitDescription("W", "watt"));

      metricUnits.putAll(activeUnits);
      metricUnits.put("Molar Mass", new UnitDescription("gr/mol", "gr/mol"));
      metricUnits.put("molar volume", new UnitDescription("cm3/mol", "cm3/mol"));
      metricUnits.put("viscosity", new UnitDescription("Pas", "Pascal per second"));

      siUnits.putAll(activeUnits); // Makes a copy of activeUnits
      siUnits.put("temperature", new UnitDescription("K", "Kelvin"));
      siUnits.put("pressure", new UnitDescription("Pa", "Pascal"));
      siUnits.put("enthalpy", new UnitDescription("J/mol", "Joule per mole"));
      siUnits.put("density", new UnitDescription("kg/m3", "kg per cubic meter"));
      siUnits.put("JT coefficient", new UnitDescription("K/Pa", "Kelvin per Pascal"));
      siUnits.put("thermal conductivity",
          new UnitDescription("J/sec-m-K", "Joule per second meter and Kelvin"));

      fieldUnits.putAll(activeUnits); // Makes a copy of activeUnits
      fieldUnits.put("temperature", new UnitDescription("F", "Fahrenheit"));
      fieldUnits.put("pressure", new UnitDescription("psia", "pounds per square inch absolute"));
      fieldUnits.put("enthalpy", new UnitDescription("Btu/lbmol", "Btu per lbmol"));
      fieldUnits.put("density", new UnitDescription("lb/ft3", "pound per cubic foot"));
      fieldUnits.put("Molar Mass", new UnitDescription("lbm/lbmol", "pound-mass per pound-mole"));
      fieldUnits.put("Heat Capacity (Cv)", new UnitDescription("btu/lbmole-F", "Btu/lbmole-F"));
      fieldUnits.put("Heat Capacity (Cp)", new UnitDescription("btu/lbmole-F", "Btu/lbmole-F"));
      fieldUnits.put("thermal conductivity", new UnitDescription("Btu/hr-ft-F", "Btu/hr-ft-F"));
      fieldUnits.put("viscosity", new UnitDescription("cP", "centypoise"));
      fieldUnits.put("entropy", new UnitDescription("btu/lb-F", "btu/lb-F"));
      fieldUnits.put("JT coefficient",
          new UnitDescription("F/psi", "Farenheit per pounds per square inch"));
      fieldUnits.put("speed of sound", new UnitDescription("ft/sec", "ft/sec"));
      fieldUnits.put("molar volume",
          new UnitDescription("ft3/lbmole", "cubic foot per pound-mass-mole"));
      fieldUnits.put("mass flow", new UnitDescription("lb/hr", "lb/hr"));
      fieldUnits.put("molar flow", new UnitDescription("lbmole/hr", "lbmole/hr"));
      fieldUnits.put("volume flow", new UnitDescription("barrel/day", "barrel/day"));
      fieldUnits.put("power", new UnitDescription("hp", "horsepower"));
      fieldUnits.put("duty", new UnitDescription("BTU/hr", "BTU per hour"));

      defaultUnits.putAll(activeUnits); // Makes a copy of activeUnits
    }
  }

  /**
   * <p>
   * activateSIUnits.
   * </p>
   */
  public static void activateSIUnits() {
    if (activeUnits.size() == 0) {
      new Units();
    }
    activeUnits = new HashMap<>(siUnits);
  }

  /**
   * <p>
   * activateFieldUnits.
   * </p>
   */
  public static void activateFieldUnits() {
    if (activeUnits.size() == 0) {
      new Units();
    }
    activeUnits = new HashMap<>(fieldUnits);
  }

  /**
   * <p>
   * activateMetricUnits.
   * </p>
   */
  public static void activateMetricUnits() {
    if (activeUnits.size() == 0) {
      new Units();
    }
    activeUnits = new HashMap<>(metricUnits);
  }

  /**
   * <p>
   * activateDefaultUnits.
   * </p>
   */
  public static void activateDefaultUnits() {
    if (activeUnits.size() == 0) {
      new Units();
    }
    activeUnits = new HashMap<>(defaultUnits); // Reassign with a copy
  }

  /**
   * <p>
   * getSymbol.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link java.lang.String} object
   */
  public static String getSymbol(String name) {
    if (activeUnits.size() == 0) {
      new Units();
    }
    return activeUnits.get(name).symbol;
  }

  /**
   * <p>
   * getSymbolName.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link java.lang.String} object
   */
  public static String getSymbolName(String name) {
    if (activeUnits.size() == 0) {
      new Units();
    }
    return activeUnits.get(name).symbolName;
  }

  /**
   * <p>
   * setUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param symbol a {@link java.lang.String} object
   * @param symbolName a {@link java.lang.String} object
   */
  public static void setUnit(String name, String symbol, String symbolName) {
    if (activeUnits.size() == 0) {
      new Units();
    }
    UnitDescription unit = activeUnits.get(name);
    if (unit != null) {
      unit.symbol = symbol;
      unit.symbolName = symbolName;
    }
  }

  /**
   * <p>
   * Getter for the field <code>temperatureUnits</code>.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getTemperatureUnits() {
    return temperatureUnits;
  }

  /**
   * <p>
   * Getter for the field <code>pressureUnits</code>.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getPressureUnits() {
    return pressureUnits;
  }

  /**
   * <p>
   * Getter for the field <code>molarVolumeUnits</code>.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getMolarVolumeUnits() {
    return molarVolumeUnits;
  }
}
