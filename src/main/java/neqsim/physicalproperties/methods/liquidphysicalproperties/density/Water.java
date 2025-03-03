/*
 * Water.java
 *
 * Created on 13. July 2022
 */

package neqsim.physicalproperties.methods.liquidphysicalproperties.density;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.component.ComponentInterface;

/**
 * <p>
 * Water Density Calculation class for aqueous salt solutions using Labiberte/Cooper partial
 * specific volumes.
 * </p>
 */
public class Water extends LiquidPhysicalPropertyMethod implements DensityInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Water.class);

  // Store c0...c4 parameters for each salt in a map [saltName -> {c0, c1, c2, c3, c4}].
  // Adjust or rename keys to match the component names in your model.
  private static final Map<String, double[]> saltParameters = new HashMap<>();

  static {
    // Example: these entries must match how your components are named
    // in the phase, and use the correct c0..c4 from your table:
    saltParameters.put("NaCl", new double[] {-0.00433, 0.06471, 1.0166, 0.014624, 3315.6});
    saltParameters.put("KCl", new double[] {-0.46782, 4.308, 2.378, 0.022044, 2714.0});
    saltParameters.put("NaBr", new double[] {109.77, 513.04, 1.5454, 0.011019, 1618.1});
    saltParameters.put("CaCl2", new double[] {-0.63254, 0.93995, 4.2785, 0.048319, 3180.9});
    saltParameters.put("HCOONa", new double[] {0.72701, 5.2872, 1.2768, 0.012640, 2554.3});
    saltParameters.put("HCOOK", new double[] {13.500, 5.6764, 0.12357, 0.0055267, 2181.9});
    saltParameters.put("KBr", new double[] {-0.0034507, 0.41086, 3.0836, 0.037482, 3202.1});
    saltParameters.put("HCOOCs", new double[] {30.138, 8.7212, 0.094231, 0.0063516, 2139.9});
  }

  /**
   * Constructor for Water.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Water(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  @Override
  public Water clone() {
    Water properties = null;
    try {
      properties = (Water) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return properties;
  }

  /**
   * Calculate the density of the liquid phase (kg/m^3) using partial specific volumes of water +
   * dissolved salts.
   */
  @Override
  public double calcDensity() {
    // 1) Get temperature (in Kelvin) and convert to °C
    double tempK = liquidPhase.getPhase().getTemperature();
    double tempC = tempK - 273.15;

    // 2) Identify water and compute its mass fraction & pure density
    double wH2O = 0.0;
    double rhoWater = 1000.0; // fallback if water not found

    double phaseMolarMass = liquidPhase.getPhase().getMolarMass();
    int numComps = liquidPhase.getPhase().getNumberOfComponents();

    // First pass: find water, store wH2O, get pure water density
    for (int i = 0; i < numComps; i++) {
      ComponentInterface comp = liquidPhase.getPhase().getComponent(i);

      // mass fraction of component
      double w = comp.getx() * comp.getMolarMass() / phaseMolarMass;

      if (comp.getName().equalsIgnoreCase("water")) {
        wH2O = w;
        // Try a more accurate density from NeqSim's pure-component function
        double pureWaterDensity = 1000.0;// comp.getPureComponentLiquidDensity(tempK);
        if (pureWaterDensity > 1e-8) {
          rhoWater = pureWaterDensity;
        }
        break;
      }
    }

    // Partial volume from water:
    double sumPartialVolumes = wH2O / rhoWater; // [m^3/kg of mixture]

    // 3) Now compute partial volumes of salts
    for (int i = 0; i < numComps; i++) {
      ComponentInterface comp = liquidPhase.getPhase().getComponent(i);
      String compName = comp.getName();
      if (compName.equalsIgnoreCase("water")) {
        // skip water
        continue;
      }

      // is it one of our salts?
      if (saltParameters.containsKey(compName)) {
        double wSalt = comp.getx() * comp.getMolarMass() / phaseMolarMass;
        double vSalt = calcPartialVolumeSalt(compName, tempC, wSalt);
        // partial volume contribution = mass fraction * vSalt
        sumPartialVolumes += wSalt * vSalt;
      }
    }

    // 4) Density is inverse of sum of partial volumes
    return 1.0 / sumPartialVolumes; // [kg/m^3]
  }

  /**
   * Calculate partial specific volume (in m^3/kg) for a given salt using Labiberte and Cooper's
   * correlation:
   *
   * v_salt = ( w_i + c2 + c3*T ) / [ (c0*w_i + c1) * exp( 0.000001*(T + c4)^2 ) ]
   *
   * Here T is in °C and w_i is the mass fraction of the salt.
   */
  private double calcPartialVolumeSalt(String saltName, double temperatureC, double wSalt) {
    double[] params = saltParameters.get(saltName);
    double c0 = params[0];
    double c1 = params[1];
    double c2 = params[2];
    double c3 = params[3];
    double c4 = params[4];

    // Numerator
    double numerator = wSalt + c2 + c3 * temperatureC;
    // Denominator
    double denominator = (c0 * wSalt + c1) * Math.exp(1.0e-6 * Math.pow(temperatureC + c4, 2.0));

    return numerator / denominator; // in m^3/kg if parameters are consistent
  }
}
