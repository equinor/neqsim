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
 * Water Density Calculation class for aqueous salt solutions using
 * Labiberte/Cooper partial
 * specific volumes.
 * </p>
 */
public class Water extends LiquidPhysicalPropertyMethod implements DensityInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Water.class);

  // Store c0...c4 parameters for each salt in a map [saltName -> {c0, c1, c2, c3,
  // c4}].
  // Adjust or rename keys to match the component names in your model.
  private static final Map<String, double[]> saltParameters = new HashMap<>();

  static {
    // Example: these entries must match how your components are named
    // in the phase, and use the correct c0..c4 from your table:
    saltParameters.put("NaCl", new double[] { -0.00433, 0.06471, 1.0166, 0.014624, 3315.6 });
    saltParameters.put("KCl", new double[] { -0.46782, 4.308, 2.378, 0.022044, 2714.0 });
    saltParameters.put("NaBr", new double[] { 109.77, 513.04, 1.5454, 0.011019, 1618.1 });
    saltParameters.put("CaCl2", new double[] { -0.63254, 0.93995, 4.2785, 0.048319, 3180.9 });
    saltParameters.put("HCOONa", new double[] { 0.72701, 5.2872, 1.2768, 0.012640, 2554.3 });
    saltParameters.put("HCOOK", new double[] { 13.500, 5.6764, 0.12357, 0.0055267, 2181.9 });
    saltParameters.put("KBr", new double[] { -0.0034507, 0.41086, 3.0836, 0.037482, 3202.1 });
    saltParameters.put("HCOOCs", new double[] { 30.138, 8.7212, 0.094231, 0.0063516, 2139.9 });
  }

  /**
   * Constructor for Water.
   *
   * @param liquidPhase a
   *                    {@link neqsim.physicalproperties.system.PhysicalProperties}
   *                    object
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
   * Calculate the density of the liquid phase (kg/m^3) using partial specific
   * volumes of water +
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
        double pureWaterDensity = calculatePureWaterDensity(liquidPhase.getPhase().getTemperature(),
            liquidPhase.getPhase().getPressure());
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
   * Calculate partial specific volume (in m^3/kg) for a given salt using
   * Labiberte and Cooper's correlation.
   *
   * v_salt = ( w_i + c2 + c3*T ) / [ (c0*w_i + c1) * exp( 0.000001*(T + c4)^2 ) ]
   *
   * Here T is in °C and w_i is the mass fraction of the salt.
   * 
   * @param saltName     the name of the salt
   * @param temperatureC temperature in Celsius
   * @param wSalt        mass fraction of the salt
   * @return partial specific volume in m^3/kg
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

  /**
   * Density of pure liquid water from IAPWS-IF97 Region 1.
   * Inputs: temperatureK — absolute temperature [K], pressureBar — absolute
   * pressure [bar]
   * Returns: density [kg/m^3]
   *
   * Valid (Region 1): 273.15 K ≤ T ≤ 623.15 K and p ≥ p_sat(T) up to 1000 bar.
   * This is the
   * compressed-/subcooled-liquid region. For steam or T greater than 623 K, use
   * other IF97 regions.
   * 
   * @param temperatureK Temperature in Kelvin
   * @param pressureBar  Pressure in bar
   * @return density in kg/m^3
   */
  public static double calculatePureWaterDensity(double temperatureK, double pressureBar) {
    if (temperatureK <= 0.0 || pressureBar <= 0.0) {
      throw new IllegalArgumentException("Temperature [K] and pressure [bar] must be positive.");
    }

    // Constants (IF97)
    final double RkJ = 0.461526; // specific gas constant for water [kJ/(kg·K)]
    final double pStarMPa = 16.53; // Region 1 pressure scaling [MPa]
    final double TStarK = 1386.0; // Region 1 temperature scaling [K]

    // Coefficients for Region 1 (Table 2 in IF97)
    final int[] I = { 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5,
        8, 8, 21, 23, 29, 30, 31, 32 };
    final int[] J = { -2, -1, 0, 1, 2, 3, 4, 5, -9, -7, -1, 0, 1, 3, -3, 0, 1, 3, 17, -4, 0, 6, -5,
        -2, 10, -8, -11, -6, -29, -31, -38, -39, -40, -41 };
    final double[] n = { 0.14632971213167, -0.84548187169114, -0.37563603672040e1,
        0.33855169168385e1, -0.95791963387872, 0.15772038513228e-1, -0.16616417199501e-1,
        0.81214629983568e-3, 0.28319080123804e-3, -0.60706301565874e-3, -0.18990068218419e-1,
        -0.32529748770505e-1, -0.21841717175414e-1, -0.52838357969930e-4, -0.47184321073267e-3,
        -0.30001780793026e-3, 0.47661393906987e-4, -0.44141845330846e-5, -0.72694996297594e-15,
        -0.31679644845054e-4, -0.28270797985312e-5, -0.85205128120103e-9, -0.22425281908000e-5,
        -0.65171222895601e-6, -0.14341729937924e-12, -0.40516996860117e-6, -0.12734301741641e-8,
        -0.17424871230634e-9, -0.68762131295531e-18, 0.14478307828521e-19, 0.26335781662795e-22,
        -0.11947622640071e-22, 0.18228094581404e-23, -0.93537087292458e-25 };

    // Reduced variables
    final double pMPa = pressureBar * 0.1; // bar -> MPa
    final double pi = pMPa / pStarMPa;
    final double tau = TStarK / temperatureK;

    // γ_π (partial derivative of dimensionless Gibbs energy wrt π) — Table 4
    double gamma_pi = 0.0;
    for (int k = 0; k < n.length; k++) {
      if (I[k] == 0)
        continue; // term vanishes because multiplied by I[k]
      gamma_pi += -n[k] * I[k] * Math.pow(7.1 - pi, I[k] - 1) * Math.pow(tau - 1.222, J[k]);
    }

    // Specific volume from Table 3: v * p / (R*T) = pi * gamma_pi
    // Use p in kPa with R in kJ so that 1 kPa·m^3 = 1 kJ
    final double p_kPa = pressureBar * 100.0; // bar -> kPa
    final double v = (RkJ * temperatureK / p_kPa) * (pi * gamma_pi); // [m^3/kg]

    // Density
    return 1.0 / v; // [kg/m^3]
  }
}
