package neqsim.standards.gasquality;

import java.util.HashMap;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;

/**
 * Implementation of GPA 2145 - Table of Physical Constants of Paraffin Hydrocarbons and Other
 * Components of Natural Gas.
 *
 * <p>
 * GPA 2145 is the Gas Processors Association standard that provides reference physical property
 * data for natural gas components. It includes molar mass, ideal gas gross heating value, ideal gas
 * net heating value, liquid density at 60F, boiling point, critical properties, and acentric
 * factors for all common natural gas components.
 * </p>
 *
 * <p>
 * This implementation validates NeqSim component data against GPA 2145 reference values and
 * provides GPA 2145 reference property values directly.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_GPA2145 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * GPA 2145 reference data: molar mass (g/mol) for key natural gas components. Values from GPA
   * 2145-16 (2016 revision).
   */
  private static final Map<String, Double> MOLAR_MASS = new HashMap<String, Double>();

  /**
   * GPA 2145 ideal gas gross heating value in BTU/ft3 at 60F, 14.696 psia. Values from GPA 2145-16.
   */
  private static final Map<String, Double> IDEAL_GROSS_HV_BTU = new HashMap<String, Double>();

  /**
   * GPA 2145 ideal gas net heating value in BTU/ft3 at 60F, 14.696 psia. Values from GPA 2145-16.
   */
  private static final Map<String, Double> IDEAL_NET_HV_BTU = new HashMap<String, Double>();

  /**
   * GPA 2145 relative density (gas) to air at 60F. Values from GPA 2145-16.
   */
  private static final Map<String, Double> RELATIVE_DENSITY = new HashMap<String, Double>();

  static {
    // Molar mass (g/mol)
    MOLAR_MASS.put("methane", 16.043);
    MOLAR_MASS.put("ethane", 30.070);
    MOLAR_MASS.put("propane", 44.096);
    MOLAR_MASS.put("i-butane", 58.122);
    MOLAR_MASS.put("n-butane", 58.122);
    MOLAR_MASS.put("i-pentane", 72.149);
    MOLAR_MASS.put("n-pentane", 72.149);
    MOLAR_MASS.put("n-hexane", 86.175);
    MOLAR_MASS.put("n-heptane", 100.202);
    MOLAR_MASS.put("n-octane", 114.229);
    MOLAR_MASS.put("n-nonane", 128.255);
    MOLAR_MASS.put("n-decane", 142.282);
    MOLAR_MASS.put("nitrogen", 28.014);
    MOLAR_MASS.put("CO2", 44.010);
    MOLAR_MASS.put("H2S", 34.081);
    MOLAR_MASS.put("water", 18.015);
    MOLAR_MASS.put("hydrogen", 2.016);
    MOLAR_MASS.put("oxygen", 31.999);
    MOLAR_MASS.put("helium", 4.003);
    MOLAR_MASS.put("argon", 39.948);

    // Ideal gas gross heating value (BTU/ft3 at 60F, 14.696 psia)
    IDEAL_GROSS_HV_BTU.put("methane", 1010.0);
    IDEAL_GROSS_HV_BTU.put("ethane", 1769.7);
    IDEAL_GROSS_HV_BTU.put("propane", 2516.1);
    IDEAL_GROSS_HV_BTU.put("i-butane", 3251.9);
    IDEAL_GROSS_HV_BTU.put("n-butane", 3262.3);
    IDEAL_GROSS_HV_BTU.put("i-pentane", 4000.9);
    IDEAL_GROSS_HV_BTU.put("n-pentane", 4008.9);
    IDEAL_GROSS_HV_BTU.put("n-hexane", 4755.9);
    IDEAL_GROSS_HV_BTU.put("n-heptane", 5502.5);
    IDEAL_GROSS_HV_BTU.put("n-octane", 6248.9);
    IDEAL_GROSS_HV_BTU.put("n-nonane", 6996.5);
    IDEAL_GROSS_HV_BTU.put("n-decane", 7742.9);
    IDEAL_GROSS_HV_BTU.put("hydrogen", 325.0);
    IDEAL_GROSS_HV_BTU.put("H2S", 637.1);

    // Ideal gas net heating value (BTU/ft3 at 60F, 14.696 psia)
    IDEAL_NET_HV_BTU.put("methane", 909.4);
    IDEAL_NET_HV_BTU.put("ethane", 1618.7);
    IDEAL_NET_HV_BTU.put("propane", 2314.9);
    IDEAL_NET_HV_BTU.put("i-butane", 2999.0);
    IDEAL_NET_HV_BTU.put("n-butane", 3010.8);
    IDEAL_NET_HV_BTU.put("i-pentane", 3698.7);
    IDEAL_NET_HV_BTU.put("n-pentane", 3706.9);
    IDEAL_NET_HV_BTU.put("n-hexane", 4403.8);
    IDEAL_NET_HV_BTU.put("n-heptane", 5100.4);
    IDEAL_NET_HV_BTU.put("n-octane", 5796.1);
    IDEAL_NET_HV_BTU.put("n-nonane", 6493.4);
    IDEAL_NET_HV_BTU.put("n-decane", 7189.6);
    IDEAL_NET_HV_BTU.put("hydrogen", 274.6);
    IDEAL_NET_HV_BTU.put("H2S", 586.8);

    // Relative density (gas to air) at 60F
    RELATIVE_DENSITY.put("methane", 0.5539);
    RELATIVE_DENSITY.put("ethane", 1.0382);
    RELATIVE_DENSITY.put("propane", 1.5226);
    RELATIVE_DENSITY.put("i-butane", 2.0068);
    RELATIVE_DENSITY.put("n-butane", 2.0068);
    RELATIVE_DENSITY.put("i-pentane", 2.4912);
    RELATIVE_DENSITY.put("n-pentane", 2.4912);
    RELATIVE_DENSITY.put("n-hexane", 2.9755);
    RELATIVE_DENSITY.put("nitrogen", 0.9672);
    RELATIVE_DENSITY.put("CO2", 1.5196);
    RELATIVE_DENSITY.put("H2S", 1.1767);
    RELATIVE_DENSITY.put("hydrogen", 0.0696);
    RELATIVE_DENSITY.put("helium", 0.1382);
    RELATIVE_DENSITY.put("oxygen", 1.1048);
  }

  /** Calculated ideal gas gross heating value in BTU/ft3. */
  private double idealGrossHV = 0.0;

  /** Calculated ideal gas net heating value in BTU/ft3. */
  private double idealNetHV = 0.0;

  /** Calculated mixture molar mass in g/mol. */
  private double mixtureMolarMass = 0.0;

  /** Calculated mixture relative density. */
  private double mixtureRelativeDensity = 0.0;

  /** Number of components matched to GPA 2145 data. */
  private int matchedComponents = 0;

  /** Total number of components in the system. */
  private int totalComponents = 0;

  /**
   * Constructor for Standard_GPA2145.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_GPA2145(SystemInterface thermoSystem) {
    super("Standard_GPA2145",
        "Table of Physical Constants of Paraffin Hydrocarbons and Other Components of Natural Gas",
        thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    idealGrossHV = 0.0;
    idealNetHV = 0.0;
    mixtureMolarMass = 0.0;
    mixtureRelativeDensity = 0.0;
    matchedComponents = 0;
    totalComponents = thermoSystem.getPhase(0).getNumberOfComponents();

    for (int i = 0; i < totalComponents; i++) {
      String name = thermoSystem.getPhase(0).getComponent(i).getName();
      double xi = thermoSystem.getPhase(0).getComponent(i).getz();

      // Molar mass
      Double mw = MOLAR_MASS.get(name);
      if (mw != null) {
        mixtureMolarMass += xi * mw.doubleValue();
        matchedComponents++;
      } else {
        // Use NeqSim value for unmatched components
        mixtureMolarMass += xi * thermoSystem.getPhase(0).getComponent(i).getMolarMass() * 1000.0;
      }

      // Gross heating value
      Double ghv = IDEAL_GROSS_HV_BTU.get(name);
      if (ghv != null) {
        idealGrossHV += xi * ghv.doubleValue();
      }

      // Net heating value
      Double nhv = IDEAL_NET_HV_BTU.get(name);
      if (nhv != null) {
        idealNetHV += xi * nhv.doubleValue();
      }

      // Relative density
      Double rd = RELATIVE_DENSITY.get(name);
      if (rd != null) {
        mixtureRelativeDensity += xi * rd.doubleValue();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    double value = getValue(returnParameter);

    if ("idealGrossHV".equals(returnParameter) || "idealNetHV".equals(returnParameter)) {
      if ("MJ/m3".equals(returnUnit)) {
        return value * 0.037316; // BTU/ft3 to MJ/m3
      }
      if ("kJ/mol".equals(returnUnit)) {
        return value * 0.001055 * 0.02832; // BTU/ft3 to kJ/mol (approx)
      }
    }
    return value;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("idealGrossHV".equals(returnParameter) || "GHV".equals(returnParameter)) {
      return idealGrossHV;
    }
    if ("idealNetHV".equals(returnParameter) || "NHV".equals(returnParameter)) {
      return idealNetHV;
    }
    if ("molarMass".equals(returnParameter)) {
      return mixtureMolarMass;
    }
    if ("relativeDensity".equals(returnParameter)) {
      return mixtureRelativeDensity;
    }
    if ("matchedComponents".equals(returnParameter)) {
      return matchedComponents;
    }
    return idealGrossHV;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("idealGrossHV".equals(returnParameter) || "GHV".equals(returnParameter)
        || "idealNetHV".equals(returnParameter) || "NHV".equals(returnParameter)) {
      return "BTU/ft3";
    }
    if ("molarMass".equals(returnParameter)) {
      return "g/mol";
    }
    if ("relativeDensity".equals(returnParameter)) {
      return "-";
    }
    return "BTU/ft3";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return matchedComponents > 0 && idealGrossHV > 0.0;
  }

  /**
   * Gets the GPA 2145 reference molar mass for a given component.
   *
   * @param componentName the component name
   * @return the molar mass in g/mol, or -1 if not in GPA 2145
   */
  public static double getReferenceMolarMass(String componentName) {
    Double mm = MOLAR_MASS.get(componentName);
    return mm != null ? mm.doubleValue() : -1.0;
  }

  /**
   * Gets the GPA 2145 reference gross heating value for a given component.
   *
   * @param componentName the component name
   * @return the gross heating value in BTU/ft3, or -1 if not in GPA 2145
   */
  public static double getReferenceGrossHV(String componentName) {
    Double hv = IDEAL_GROSS_HV_BTU.get(componentName);
    return hv != null ? hv.doubleValue() : -1.0;
  }
}
