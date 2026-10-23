package neqsim.process.fielddevelopment.reservoir;

import java.util.Locale;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Builds the flowing well stream of a VFP coordinate by recombining surface gas, stock-tank liquid and water.
 *
 * <p>
 * The template fluid fixes the component list, equation of state and mixing rule. The gas fluid is the surface gas
 * composition; the optional liquid fluid is the stock-tank oil or condensate; water is added as the pure component
 * {@code water} (the template must contain it) or as an explicit water fluid. Component moles follow from the surface
 * volumes: gas is converted with the ideal-gas molar volume at 15 C and 1.01325 bara, liquid and water with their
 * standard-condition density and molar mass.
 * </p>
 *
 * <p>
 * Supported rate bases and ratio definitions:
 * </p>
 * <ul>
 * <li>GAS: rate is gas Sm3/day; gas ratio OGR is stock-tank liquid Sm3 per Sm3 gas; water ratio WGR is water Sm3 per
 * Sm3 gas.</li>
 * <li>OIL: rate is stock-tank oil Sm3/day; gas ratio GOR/GLR is gas Sm3 per Sm3 oil; water ratio WCT (fraction of
 * liquid, below 1) or WOR (water Sm3 per Sm3 oil).</li>
 * <li>LIQ: rate is oil plus water Sm3/day; gas ratio GOR/GLR is gas Sm3 per Sm3 liquid; water ratio WCT or WOR.</li>
 * </ul>
 * <p>
 * Gas-lift quantity (ALQ, Sm3/day) is added as extra gas of the gas composition in the stream, i.e. it is assumed to
 * enter at the bottom of the modelled section.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class RecombinationFluidFactory implements VfpFluidFactory {
  private static final double STANDARD_MOLAR_DENSITY = ThermodynamicConstantsInterface.referencePressure * 1.0e5
      / (ThermodynamicConstantsInterface.R * ThermodynamicConstantsInterface.standardStateTemperature);

  private final SystemInterface template;
  private final String[] names;
  private final double[] gasZ;
  private final double[] liquidZ;
  private final double[] waterZ;
  private final double liquidMolPerSm3;
  private final double waterMolPerSm3;
  private String rateBasis = "GAS";
  private String waterRatioType = "WGR";
  private String gasRatioType = "OGR";
  private double traceFloor = 1.0e-12;

  /**
   * Creates a factory.
   *
   * @param template fluid with all components, EOS and mixing rule set; cloned for every stream
   * @param gas surface gas composition
   * @param liquid stock-tank oil or condensate composition, or null when no liquid is produced
   * @throws IllegalArgumentException if a source fluid has a component that the template lacks
   */
  public RecombinationFluidFactory(SystemInterface template, SystemInterface gas, SystemInterface liquid) {
    this(template, gas, liquid, null, 1000.0);
  }

  /**
   * Creates a factory with an explicit water composition.
   *
   * @param template fluid with all components, EOS and mixing rule set; cloned for every stream
   * @param gas surface gas composition
   * @param liquid stock-tank oil or condensate composition, or null when no liquid is produced
   * @param water water-phase composition, or null to use the pure component {@code water}
   * @param waterDensityKgPerSm3 standard-condition density of the water phase in kg/m3
   * @throws IllegalArgumentException if a source fluid has a component that the template lacks
   */
  public RecombinationFluidFactory(SystemInterface template, SystemInterface gas, SystemInterface liquid,
      SystemInterface water, double waterDensityKgPerSm3) {
    this.template = template;
    int n = template.getPhase(0).getNumberOfComponents();
    names = new String[n];
    for (int i = 0; i < n; i++) {
      names[i] = template.getPhase(0).getComponent(i).getComponentName();
    }
    gasZ = mapComposition(gas);
    liquidZ = liquid == null ? null : mapComposition(liquid);
    liquidMolPerSm3 = liquid == null ? 0.0 : standardMolarDensity(liquid);
    if (water == null) {
      waterZ = new double[n];
      int idx = indexOf("water");
      if (idx >= 0) {
        waterZ[idx] = 1.0;
      }
      waterMolPerSm3 = waterDensityKgPerSm3 / 0.018015;
    } else {
      waterZ = mapComposition(water);
      waterMolPerSm3 = waterDensityKgPerSm3 / water.getMolarMass();
    }
  }

  /**
   * Sets the rate basis of the table.
   *
   * @param basis GAS, OIL or LIQ
   * @return this factory
   * @throws IllegalArgumentException for an unsupported basis
   */
  public RecombinationFluidFactory setRateBasis(String basis) {
    String b = basis.toUpperCase(Locale.ROOT);
    if (!"GAS".equals(b) && !"OIL".equals(b) && !"LIQ".equals(b)) {
      throw new IllegalArgumentException("Rate basis must be GAS, OIL or LIQ: " + basis);
    }
    this.rateBasis = b;
    return this;
  }

  /**
   * Sets the water ratio definition.
   *
   * @param type WGR, WCT or WOR
   * @return this factory
   * @throws IllegalArgumentException for an unsupported type
   */
  public RecombinationFluidFactory setWaterRatioType(String type) {
    String t = type.toUpperCase(Locale.ROOT);
    if (!"WGR".equals(t) && !"WCT".equals(t) && !"WOR".equals(t)) {
      throw new IllegalArgumentException("Water ratio type must be WGR, WCT or WOR: " + type);
    }
    this.waterRatioType = t;
    return this;
  }

  /**
   * Sets the gas ratio definition.
   *
   * @param type OGR, GOR or GLR
   * @return this factory
   * @throws IllegalArgumentException for an unsupported type
   */
  public RecombinationFluidFactory setGasRatioType(String type) {
    String t = type.toUpperCase(Locale.ROOT);
    if (!"OGR".equals(t) && !"GOR".equals(t) && !"GLR".equals(t)) {
      throw new IllegalArgumentException("Gas ratio type must be OGR, GOR or GLR: " + type);
    }
    this.gasRatioType = t;
    return this;
  }

  /**
   * Sets the mole-fraction floor given to components that are absent from all sources. A tiny positive amount keeps the
   * flash well posed; use 0 to disable.
   *
   * @param floor mole fraction floor (default 1e-12)
   * @return this factory
   */
  public RecombinationFluidFactory setTraceFloor(double floor) {
    this.traceFloor = Math.max(0.0, floor);
    return this;
  }

  /**
   * Returns the molar density of surface gas used to convert Sm3 to mole.
   *
   * @return gas molar density at 15 C and 1.01325 bara in mol/Sm3
   */
  public static double getStandardGasMolarDensity() {
    return STANDARD_MOLAR_DENSITY;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface createWellStream(double rate, double waterRatio, double gasRatio, double alq) {
    double gasVol;
    double oilVol;
    double waterVol;
    if ("GAS".equals(rateBasis)) {
      gasVol = rate;
      oilVol = "OGR".equals(gasRatioType) ? gasRatio * rate : 0.0;
      waterVol = waterRatio * rate;
      if (!"WGR".equals(waterRatioType)) {
        throw new IllegalArgumentException("A GAS rate basis needs the WGR water ratio");
      }
    } else {
      if ("OGR".equals(gasRatioType)) {
        throw new IllegalArgumentException("OGR is only valid for a GAS rate basis");
      }
      if ("WGR".equals(waterRatioType)) {
        throw new IllegalArgumentException("WGR is only valid for a GAS rate basis");
      }
      if ("OIL".equals(rateBasis)) {
        oilVol = rate;
        waterVol = "WCT".equals(waterRatioType) ? waterCutToWaterVolume(waterRatio, rate) : waterRatio * rate;
        gasVol = gasRatio * rate;
      } else {
        if ("WCT".equals(waterRatioType)) {
          waterVol = waterRatio * rate;
        } else {
          waterVol = waterRatio / (1.0 + waterRatio) * rate;
        }
        oilVol = rate - waterVol;
        gasVol = gasRatio * rate;
      }
    }
    double nGas = (gasVol + alq) * STANDARD_MOLAR_DENSITY;
    double nLiquid = oilVol * liquidMolPerSm3;
    double nWater = waterVol * waterMolPerSm3;
    if (nLiquid > 0.0 && liquidZ == null) {
      throw new IllegalArgumentException("A liquid fluid is needed for a non-zero liquid rate");
    }
    if (nWater > 0.0 && indexOf("water") < 0 && isZero(waterZ)) {
      throw new IllegalArgumentException("The template has no water component for a non-zero water rate");
    }
    double[] moles = new double[names.length];
    double total = 0.0;
    for (int i = 0; i < names.length; i++) {
      moles[i] = nGas * gasZ[i] + (liquidZ == null ? 0.0 : nLiquid * liquidZ[i]) + nWater * waterZ[i];
      total += moles[i];
    }
    if (total <= 0.0) {
      throw new IllegalArgumentException("Table coordinate gives no flow: rate " + rate);
    }
    for (int i = 0; i < moles.length; i++) {
      moles[i] = Math.max(moles[i], traceFloor * total);
    }
    SystemInterface fluid = template.clone();
    fluid.setMolarComposition(moles);
    fluid.setTotalFlowRate(total / 86400.0, "mole/sec");
    fluid.init(0);
    return fluid;
  }

  /**
   * Converts a water cut to the water volume that accompanies an oil volume.
   *
   * @param waterCut water fraction of the liquid, below 1
   * @param oilVolume oil volume
   * @return water volume in the unit of the oil volume
   * @throws IllegalArgumentException if the water cut is 1 or above
   */
  private static double waterCutToWaterVolume(double waterCut, double oilVolume) {
    if (waterCut >= 1.0) {
      throw new IllegalArgumentException("Water cut must be below 1 for an OIL rate basis");
    }
    return waterCut / (1.0 - waterCut) * oilVolume;
  }

  /**
   * Checks whether all values are exactly zero.
   *
   * @param values values to test
   * @return true if every value is zero
   */
  private static boolean isZero(double[] values) {
    for (double v : values) {
      if (v != 0.0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Finds a template component by name.
   *
   * @param name component name, case-insensitive
   * @return component index, or -1 when absent
   */
  private int indexOf(String name) {
    for (int i = 0; i < names.length; i++) {
      if (names[i].equalsIgnoreCase(name)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Maps a source fluid composition onto the template component order as normalized mole fractions.
   *
   * @param source source fluid
   * @return mole fractions in template order
   * @throws IllegalArgumentException if the source has a component the template lacks or no moles
   */
  private double[] mapComposition(SystemInterface source) {
    double[] z = new double[names.length];
    int n = source.getPhase(0).getNumberOfComponents();
    double sum = 0.0;
    for (int i = 0; i < n; i++) {
      String name = source.getPhase(0).getComponent(i).getComponentName();
      int idx = indexOf(name);
      if (idx < 0) {
        throw new IllegalArgumentException("Template fluid lacks component " + name);
      }
      z[idx] = source.getPhase(0).getComponent(i).getNumberOfmoles();
      sum += z[idx];
    }
    if (sum <= 0.0) {
      throw new IllegalArgumentException("Source fluid has no moles");
    }
    for (int i = 0; i < z.length; i++) {
      z[i] /= sum;
    }
    return z;
  }

  /**
   * Calculates the molar density of a stock-tank liquid at 15 C and 1.01325 bara.
   *
   * @param liquid stock-tank liquid fluid
   * @return molar density in mol/Sm3
   */
  private static double standardMolarDensity(SystemInterface liquid) {
    SystemInterface std = liquid.clone();
    std.setTemperature(ThermodynamicConstantsInterface.standardStateTemperature, "K");
    std.setPressure(ThermodynamicConstantsInterface.referencePressure, "bara");
    new ThermodynamicOperations(std).TPflash();
    std.initProperties();
    return std.getDensity("kg/m3") / std.getMolarMass();
  }
}
