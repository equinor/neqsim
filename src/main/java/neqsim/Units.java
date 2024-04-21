package neqsim;

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
  public class Unit {
    public String symbol;
    public String symbolName;

    public Unit(String symbol, String symbolName) {
      this.symbol = symbol;
      this.symbolName = symbolName;
    }
  }

  public static HashMap<String, Unit> activeUnits = new HashMap<>();
  public static HashMap<String, Unit> defaultUnits = new HashMap<>();
  private static HashMap<String, Unit> siUnits = new HashMap<>();
  private static HashMap<String, Unit> fieldUnits = new HashMap<>();

  public Units() {
    if (activeUnits.size() == 0) {
      activeUnits.put("temperature", new Unit("C", "Celsius"));
      activeUnits.put("pressure", new Unit("bara", "bar absolute"));
      activeUnits.put("enthalpy", new Unit("J/kg", "Joule per kg"));
      activeUnits.put("entropy", new Unit("J/kgK", "Joule per kg and Kelvin"));
      activeUnits.put("density", new Unit("kg/m3", "kg per cubic meter"));

      siUnits.putAll(activeUnits); // Makes a copy of activeUnits
      siUnits.put("temperature", new Unit("K", "Kelvin"));
      siUnits.put("pressure", new Unit("Pa", "Pascal"));
      siUnits.put("enthalpy", new Unit("J/mol", "Joule per mole"));
      siUnits.put("density", new Unit("mol/m3", "mol per cubic meter"));

      fieldUnits.putAll(activeUnits); // Makes a copy of activeUnits
      fieldUnits.put("temperature", new Unit("F", "Fahrenheit"));
      fieldUnits.put("pressure", new Unit("psia", "pounds per square inch absolute"));
      fieldUnits.put("enthalpy", new Unit("Btu/lbmol", "Btu per lbmol"));
      fieldUnits.put("density", new Unit("lb/ft3", "pound per cubic foot"));

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
    Unit unit = activeUnits.get(name);
    if (unit != null) {
      unit.symbol = symbol;
      unit.symbolName = symbolName;
    }
  }
}
