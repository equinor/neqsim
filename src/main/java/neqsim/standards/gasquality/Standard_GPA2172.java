package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Implementation of GPA 2172 - Calculation of Gross Heating Value, Relative Density,
 * Compressibility and Theoretical Hydrocarbon Liquid Content for Natural Gas Mixtures for Custody
 * Transfer.
 *
 * <p>
 * GPA 2172 is the North American standard for calculating key gas quality parameters from
 * composition analysis. It is widely used for custody transfer in the United States and Canada. The
 * standard uses GPA 2145 physical constants as reference data and specifies calculations at 60F
 * (15.556C) and 14.696 psia (1.01325 bara).
 * </p>
 *
 * <p>
 * Key calculations:
 * </p>
 * <ul>
 * <li>Gross heating value (ideal and real) in BTU/ft3</li>
 * <li>Relative density (specific gravity) to air</li>
 * <li>Compression factor (Z) at standard conditions via summation factor method</li>
 * <li>Theoretical gallons of liquid per thousand cubic feet (GPM)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_GPA2172 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_GPA2172.class);

  /** Internal GPA 2145 constants provider. */
  private Standard_GPA2145 gpa2145;

  /** Internal ISO 6976 calculator for Z-factor. */
  private Standard_ISO6976 iso6976;

  /** Ideal gas gross heating value in BTU/ft3 at 60F. */
  private double idealGrossHV = 0.0;

  /** Real gas gross heating value in BTU/ft3 at 60F. */
  private double realGrossHV = 0.0;

  /** Relative density (specific gravity) to air. */
  private double relativeDensity = 0.0;

  /** Compression factor at 60F, 14.696 psia. */
  private double compressionFactor = 1.0;

  /** Mixture molar mass in g/mol. */
  private double molarMass = 0.0;

  /** Theoretical C2+ liquid content in gallons per Mcf. */
  private double gpmC2Plus = 0.0;

  /** Theoretical C3+ liquid content in gallons per Mcf. */
  private double gpmC3Plus = 0.0;

  /** Molar mass of air at standard conditions in g/mol. */
  private static final double MOLAR_MASS_AIR = 28.9625;

  /**
   * GPA 2145 liquid density at 60F in lb/gal for GPM calculation. Standard values for common NGL
   * components.
   */
  private static final double ETHANE_LB_PER_GAL = 2.97;
  private static final double PROPANE_LB_PER_GAL = 4.233;
  private static final double IBUTANE_LB_PER_GAL = 4.695;
  private static final double NBUTANE_LB_PER_GAL = 4.872;
  private static final double IPENTANE_LB_PER_GAL = 5.209;
  private static final double NPENTANE_LB_PER_GAL = 5.262;
  private static final double HEXANE_LB_PER_GAL = 5.536;

  /**
   * Constructor for Standard_GPA2172.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_GPA2172(SystemInterface thermoSystem) {
    super("Standard_GPA2172",
        "Calculation of GHV, RD, Z, and Theoretical Hydrocarbon Liquid Content", thermoSystem);
    this.gpa2145 = new Standard_GPA2145(thermoSystem);
    this.iso6976 = new Standard_ISO6976(thermoSystem, 15.55, 15.55, "volume");
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      // Use GPA 2145 constants
      gpa2145.calculate();
      idealGrossHV = gpa2145.getValue("idealGrossHV");
      molarMass = gpa2145.getValue("molarMass");
      relativeDensity = molarMass / MOLAR_MASS_AIR;

      // Use ISO 6976 for Z-factor at 60F
      iso6976.setReferenceState("real");
      iso6976.calculate();
      compressionFactor = iso6976.getValue("CompressionFactor");

      // Real gas GHV = Ideal GHV / Z
      if (compressionFactor > 0.0) {
        realGrossHV = idealGrossHV / compressionFactor;
      }

      // Calculate GPM (gallons per Mcf = gallons per 1000 ft3)
      // GPM_i = x_i * MW_i / (rho_liq_i * 0.001) scaled to per Mcf
      // More precisely: GPM_i = x_i * (379.49 / rho_liq_i_gal_per_lb) * 1000/379.49
      // = x_i * 1000 * MW_i / (rho_liq_lb_per_gal * 379.49)
      // where 379.49 ft3/lb-mol (at 60F, 14.696 psia ideal gas molar vol)
      gpmC2Plus = 0.0;
      gpmC3Plus = 0.0;

      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        String name = thermoSystem.getPhase(0).getComponent(i).getName();
        double xi = thermoSystem.getPhase(0).getComponent(i).getz();
        double gpm = calculateComponentGPM(name, xi);

        if ("ethane".equals(name)) {
          gpmC2Plus += gpm;
        } else if ("propane".equals(name) || "i-butane".equals(name) || "n-butane".equals(name)
            || "i-pentane".equals(name) || "n-pentane".equals(name) || "n-hexane".equals(name)) {
          gpmC2Plus += gpm;
          gpmC3Plus += gpm;
        }
      }
    } catch (Exception ex) {
      logger.error("GPA 2172 calculation failed", ex);
    }
  }

  /**
   * Calculates gallons per Mcf for a given component.
   *
   * @param name the component name
   * @param moleFraction the mole fraction
   * @return GPM value
   */
  private double calculateComponentGPM(String name, double moleFraction) {
    double mw = 0.0;
    double lbPerGal = 0.0;

    if ("ethane".equals(name)) {
      mw = 30.070;
      lbPerGal = ETHANE_LB_PER_GAL;
    } else if ("propane".equals(name)) {
      mw = 44.096;
      lbPerGal = PROPANE_LB_PER_GAL;
    } else if ("i-butane".equals(name)) {
      mw = 58.122;
      lbPerGal = IBUTANE_LB_PER_GAL;
    } else if ("n-butane".equals(name)) {
      mw = 58.122;
      lbPerGal = NBUTANE_LB_PER_GAL;
    } else if ("i-pentane".equals(name)) {
      mw = 72.149;
      lbPerGal = IPENTANE_LB_PER_GAL;
    } else if ("n-pentane".equals(name)) {
      mw = 72.149;
      lbPerGal = NPENTANE_LB_PER_GAL;
    } else if ("n-hexane".equals(name)) {
      mw = 86.175;
      lbPerGal = HEXANE_LB_PER_GAL;
    }

    if (lbPerGal > 0.0) {
      // GPM = xi * MW / (lb_per_gal) * (1000/379.49)
      return moleFraction * mw / lbPerGal * (1000.0 / 379.49);
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    double value = getValue(returnParameter);
    if ("idealGrossHV".equals(returnParameter) || "realGrossHV".equals(returnParameter)) {
      if ("MJ/m3".equals(returnUnit)) {
        return value * 0.037316; // BTU/ft3 to MJ/m3
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
    if ("realGrossHV".equals(returnParameter)) {
      return realGrossHV;
    }
    if ("relativeDensity".equals(returnParameter) || "specificGravity".equals(returnParameter)) {
      return relativeDensity;
    }
    if ("compressionFactor".equals(returnParameter) || "Z".equals(returnParameter)) {
      return compressionFactor;
    }
    if ("molarMass".equals(returnParameter)) {
      return molarMass;
    }
    if ("GPMC2Plus".equals(returnParameter)) {
      return gpmC2Plus;
    }
    if ("GPMC3Plus".equals(returnParameter)) {
      return gpmC3Plus;
    }
    return idealGrossHV;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("idealGrossHV".equals(returnParameter) || "GHV".equals(returnParameter)
        || "realGrossHV".equals(returnParameter)) {
      return "BTU/ft3";
    }
    if ("relativeDensity".equals(returnParameter) || "specificGravity".equals(returnParameter)
        || "compressionFactor".equals(returnParameter) || "Z".equals(returnParameter)) {
      return "-";
    }
    if ("molarMass".equals(returnParameter)) {
      return "g/mol";
    }
    if ("GPMC2Plus".equals(returnParameter) || "GPMC3Plus".equals(returnParameter)) {
      return "gal/Mcf";
    }
    return "BTU/ft3";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return idealGrossHV > 0.0;
  }
}
