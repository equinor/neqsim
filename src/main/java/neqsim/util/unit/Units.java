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

  public static HashMap<String, UnitDescription> activeUnits = new HashMap<>();
  public static HashMap<String, UnitDescription> defaultUnits = new HashMap<>();
  private static HashMap<String, UnitDescription> siUnits = new HashMap<>();
  private static HashMap<String, UnitDescription> fieldUnits = new HashMap<>();

  private static String[] pressureUnits =
      new String[] {"Pa", "bara", "barg", "psi", "psig", "psia"};
  private static String[] temperatureUnits = new String[] {"K", "C", "F", "R"};
  private static String[] molarVolumeUnits = new String[] {"mol/m3", "litre/m3", "ft3/lbmole"};

  public Units() {
    if (activeUnits.size() == 0) {
      activeUnits.put("temperature", new UnitDescription("C", "Celsius"));
      activeUnits.put("pressure", new UnitDescription("bara", "bar absolute"));
      activeUnits.put("enthalpy", new UnitDescription("J/kg", "Joule per kg"));
      activeUnits.put("entropy", new UnitDescription("J/kgK", "Joule per kg and Kelvin"));
      activeUnits.put("density", new UnitDescription("kg/m3", "kg per cubic meter"));
      activeUnits.put("viscosity", new UnitDescription("kg/msec", "kg per meter and second"));
      activeUnits.put("surface tension", new UnitDescription("N/m", "newton per meter"));
      activeUnits.put("thermal concdutivity",
          new UnitDescription("W/mK", "watts per meter and Kelvin"));
      activeUnits.put("JT coefficient", new UnitDescription("C/bar", "Celsius per bar"));
      activeUnits.put("speed of sound", new UnitDescription("m/sec", "meter per second"));
      activeUnits.put("Heat Capacity (Cv)",
          new UnitDescription("kJ/kg*K", "kilo joule per kg and Kelvin"));
      activeUnits.put("Heat Capacity (Cp)",
          new UnitDescription("kJ/kg*K", "kilo joule per kg and Kelvin"));
      activeUnits.put("Molar Mass", new UnitDescription("kg/mol", "kilo gram per kilo mole"));
      activeUnits.put("molar volume", new UnitDescription("m3/mol", "cubic metre per mole"));
      activeUnits.put("mass flow", new UnitDescription("kg/hr", "kg per hour"));
      activeUnits.put("molar flow", new UnitDescription("mol/hr", "mole per hour"));
      activeUnits.put("volume flow", new UnitDescription("m3/hr", "cubic metre per hour"));

      siUnits.putAll(activeUnits); // Makes a copy of activeUnits
      siUnits.put("temperature", new UnitDescription("K", "Kelvin"));
      siUnits.put("pressure", new UnitDescription("Pa", "Pascal"));
      siUnits.put("enthalpy", new UnitDescription("J/mol", "Joule per mole"));
      siUnits.put("density", new UnitDescription("mol/m3", "mol per cubic meter"));
      siUnits.put("JT coefficient", new UnitDescription("K/Pa", "Kelvin per Pascal"));

      fieldUnits.putAll(activeUnits); // Makes a copy of activeUnits
      fieldUnits.put("temperature", new UnitDescription("F", "Fahrenheit"));
      fieldUnits.put("pressure", new UnitDescription("psia", "pounds per square inch absolute"));
      fieldUnits.put("enthalpy", new UnitDescription("Btu/lbmol", "Btu per lbmol"));
      fieldUnits.put("density", new UnitDescription("lb/ft3", "pound per cubic foot"));
      fieldUnits.put("Molar Mass", new UnitDescription("lbm/lbmol", "pound-mass per pound-mole"));
      fieldUnits.put("Heat Capacity (Cv)", new UnitDescription("btu/lbmole-F", "Btu/lbmole-F"));
      fieldUnits.put("Heat Capacity (Cp)", new UnitDescription("btu/lbmole-F", "Btu/lbmole-F"));
      fieldUnits.put("thermal concdutivity", new UnitDescription("Btu/hr-ft-F", "Btu/hr-ft-F"));
      fieldUnits.put("viscosity", new UnitDescription("cP", "centypoise"));
      fieldUnits.put("entropy", new UnitDescription("btu/lb-F", "btu/lb-F"));
      fieldUnits.put("JT coefficient",
          new UnitDescription("F/psi", "Farenheit per pounds per square inch"));
      fieldUnits.put("speed of sound", new UnitDescription("ft/sec", "ft/sec"));
      fieldUnits.put("molar volume",
          new UnitDescription("ft3/lbmole", "cubic foot per pound-mass-mole"));
      fieldUnits.put("mass flow", new UnitDescription("lb/hr", "lb/hr"));
      fieldUnits.put("molar flow", new UnitDescription("lbmole/hr", "lbmole/hr"));
      fieldUnits.put("actual volume flow", new UnitDescription("barrel/day", "barrel/day"));

      defaultUnits.putAll(activeUnits); // Makes a copy of activeUnits
    }
  }

  public static void activateSIUnits() {
    activeUnits = new HashMap<>(siUnits);
  }

  public static void activateFieldUnits() {
    activeUnits = new HashMap<>(fieldUnits);
  }

  public static void activateDefaultUnits() {
    activeUnits = new HashMap<>(defaultUnits); // Reassign with a copy
  }

  public static String getSymbol(String name) {
    return activeUnits.get(name).symbol;
  }

  public static String getSymbolName(String name) {
    return activeUnits.get(name).symbolName;
  }

  public void setUnit(String name, String symbol, String symbolName) {
    UnitDescription unit = activeUnits.get(name);
    if (unit != null) {
      unit.symbol = symbol;
      unit.symbolName = symbolName;
    }
  }

  public String[] getTemperatureUnits() {
    return temperatureUnits;
  }

  public String[] getPressureUnits() {
    return pressureUnits;
  }

  public String[] getMolarVolumeUnits() {
    return molarVolumeUnits;
  }
}
