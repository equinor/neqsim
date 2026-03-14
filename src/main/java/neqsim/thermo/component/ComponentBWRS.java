package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseBWRSEos;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentBWRS class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentBWRS extends ComponentSrk {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentBWRS.class);

  /**
   * Unit conversion factor for MBWR-32 parameters. The database coefficients are calibrated in
   * MPa-based units (density in mol/L), while the framework uses R_SI = 8.3144621 J/(mol·K). Since
   * R_SI / R_MPa = 1000, this factor compensates for the unit mismatch in Helmholtz functions.
   */
  private static final double MBWR_UNIT_FACTOR = 1e3;

  int OP = 9;
  int OE = 6;
  private double[] aBWRS = new double[32];
  private double[] BP = new double[OP];
  private double[] BE = new double[OE];

  private double[] BPdT = new double[OP];
  private double[] BEdT = new double[OE];

  private double[] BPdTdT = new double[OP];
  private double[] BEdTdT = new double[OE];

  double rhoc = 0.0;
  double gammaBWRS = 0.0;

  PhaseBWRSEos refPhaseBWRS = null;

  /**
   * <p>
   * Constructor for ComponentBWRS.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentBWRS(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);

    boolean paramsFound = false;
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet = null;
      try {
        dataSet = database.getResultSet(("SELECT * FROM mbwr32param WHERE name='" + name + "'"));
        if (dataSet.next()) {
          dataSet.getClob("name");
          for (int i = 0; i < 32; i++) {
            aBWRS[i] = Double.parseDouble(dataSet.getString("a" + i));
          }
          rhoc = Double.parseDouble(dataSet.getString("rhoc"));
          gammaBWRS = 1.0 / (rhoc * rhoc);
          paramsFound = true;
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    if (!paramsFound) {
      // Estimate rhoc from critical volume so the mixture gamma mixing rule works.
      // criticalVolume is in cm3/mol (set by parent Component class from database).
      // rhoc in mol/L = 1000 / Vc(cm3/mol).
      double Vc = getCriticalVolume();
      if (Vc > 0) {
        rhoc = 1000.0 / Vc;
      } else {
        // Last resort: use critical properties via Zc = Pc*Vc/(R*Tc)
        // Approximate rhoc using Pc/(Zc*R_MPa*Tc) with Zc~0.27
        double Tc = getTC();
        double Pc = getPC() / 10.0; // bara to MPa
        if (Tc > 0 && Pc > 0) {
          rhoc = Pc / (0.27 * 0.008314 * Tc);
        } else {
          rhoc = 10.0; // safe default ~methane-like
        }
      }
      gammaBWRS = 1.0 / (rhoc * rhoc);

      logger.warn(
          "MBWR-32 parameters not found for component '{}'. "
              + "Only methane and ethane have MBWR-32 parameters. "
              + "This component will behave as ideal gas in BWRS calculations "
              + "(estimated rhoc={} mol/L from critical volume). "
              + "Consider using GERG-2008 (SystemGERG2008Eos) for multi-component gas mixtures.",
          name, String.format("%.2f", rhoc));
    }
  }

  /**
   * <p>
   * Constructor for ComponentBWRS.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentBWRS(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentBWRS clone() {
    ComponentBWRS clonedComponent = null;
    try {
      clonedComponent = (ComponentBWRS) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double temperature, double pressure, double totalNumberOfMoles, double beta,
      int initType) {
    super.init(temperature, pressure, totalNumberOfMoles, beta, initType);

    BP[0] = R * temperature;
    BP[1] = aBWRS[0] * temperature + aBWRS[1] * Math.sqrt(temperature) + aBWRS[2]
        + aBWRS[3] / temperature + aBWRS[4] / Math.pow(temperature, 2.0);
    BP[2] = aBWRS[5] * temperature + aBWRS[6] + aBWRS[7] / temperature
        + aBWRS[8] / Math.pow(temperature, 2.0);
    BP[3] = aBWRS[9] * temperature + aBWRS[10] + aBWRS[11] / temperature;
    BP[4] = aBWRS[12];
    BP[5] = aBWRS[13] / temperature + aBWRS[14] / Math.pow(temperature, 2.0);
    BP[6] = aBWRS[15] / temperature;
    BP[7] = aBWRS[16] / temperature + aBWRS[17] / Math.pow(temperature, 2.0);
    BP[8] = aBWRS[18] / Math.pow(temperature, 2.0);

    BE[0] = (aBWRS[19] / Math.pow(temperature, 2.0) + aBWRS[20] / Math.pow(temperature, 3.0));
    // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));
    BE[1] = (aBWRS[21] / Math.pow(temperature, 2.0) + aBWRS[22] / Math.pow(temperature, 4.0));
    // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));
    BE[2] = (aBWRS[23] / Math.pow(temperature, 2.0) + aBWRS[24] / Math.pow(temperature, 3.0));
    // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));
    BE[3] = (aBWRS[25] / Math.pow(temperature, 2.0) + aBWRS[26] / Math.pow(temperature, 4.0));
    // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));
    BE[4] = (aBWRS[27] / Math.pow(temperature, 2.0) + aBWRS[28] / Math.pow(temperature, 3.0));
    // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));
    BE[5] = (aBWRS[29] / Math.pow(temperature, 2.0) + aBWRS[30] / Math.pow(temperature, 3.0)
        + aBWRS[31] / Math.pow(temperature, 4.0)); // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));

    BPdT[0] = R;
    BPdT[1] = aBWRS[0] + aBWRS[1] / (2.0 * Math.sqrt(temperature))
        - aBWRS[3] / Math.pow(temperature, 2.0) - 2.0 * aBWRS[4] / Math.pow(temperature, 3.0);
    BPdT[2] = aBWRS[5] - aBWRS[7] / Math.pow(temperature, 2.0)
        - 2.0 * aBWRS[8] / Math.pow(temperature, 3.0);
    BPdT[3] = aBWRS[9] - aBWRS[11] / Math.pow(temperature, 2.0);
    BPdT[4] = 0.0;
    BPdT[5] =
        -aBWRS[13] / Math.pow(temperature, 2.0) - 2.0 * aBWRS[14] / Math.pow(temperature, 3.0);
    BPdT[6] = -aBWRS[15] / Math.pow(temperature, 2.0);
    BPdT[7] =
        -aBWRS[16] / Math.pow(temperature, 2.0) - 2.0 * aBWRS[17] / Math.pow(temperature, 3.0);
    BPdT[8] = -2.0 * aBWRS[18] / Math.pow(temperature, 3.0);

    BEdT[0] = (-2.0 * aBWRS[19] / Math.pow(temperature, 3.0)
        - 3.0 * aBWRS[20] / Math.pow(temperature, 4.0)); // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));
    BEdT[1] = (-2.0 * aBWRS[21] / Math.pow(temperature, 3.0)
        - 4.0 * aBWRS[22] / Math.pow(temperature, 5.0)); // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));
    BEdT[2] = (-2.0 * aBWRS[23] / Math.pow(temperature, 3.0)
        - 3.0 * aBWRS[24] / Math.pow(temperature, 4.0)); // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));
    BEdT[3] = (-2.0 * aBWRS[25] / Math.pow(temperature, 3.0)
        - 4.0 * aBWRS[26] / Math.pow(temperature, 5.0)); // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));
    BEdT[4] = (-2.0 * aBWRS[27] / Math.pow(temperature, 3.0)
        - 3.0 * aBWRS[28] / Math.pow(temperature, 4.0)); // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));
    BEdT[5] = (-2.0 * aBWRS[29] / Math.pow(temperature, 3.0)
        - 3.0 * aBWRS[30] / Math.pow(temperature, 4.0)
        - 4.0 * aBWRS[31] / Math.pow(temperature, 5.0)); // *Math.exp(-gammaBWRS*Math.pow(getMolarDensity(),2.0));

    // Second temperature derivatives of BP and BE arrays
    BPdTdT[0] = 0;
    BPdTdT[1] = -aBWRS[1] / (4.0 * Math.pow(temperature, 1.5))
        + 2.0 * aBWRS[3] / Math.pow(temperature, 3.0) + 6.0 * aBWRS[4] / Math.pow(temperature, 4.0);
    BPdTdT[2] =
        2.0 * aBWRS[7] / Math.pow(temperature, 3.0) + 6.0 * aBWRS[8] / Math.pow(temperature, 4.0);
    BPdTdT[3] = 2.0 * aBWRS[11] / Math.pow(temperature, 3.0);
    BPdTdT[4] = 0;
    BPdTdT[5] =
        2.0 * aBWRS[13] / Math.pow(temperature, 3.0) + 6.0 * aBWRS[14] / Math.pow(temperature, 4.0);
    BPdTdT[6] = 2.0 * aBWRS[15] / Math.pow(temperature, 3.0);
    BPdTdT[7] =
        2.0 * aBWRS[16] / Math.pow(temperature, 3.0) + 6.0 * aBWRS[17] / Math.pow(temperature, 4.0);
    BPdTdT[8] = 6.0 * aBWRS[18] / Math.pow(temperature, 4.0);

    BEdTdT[0] = 6.0 * aBWRS[19] / Math.pow(temperature, 4.0)
        + 12.0 * aBWRS[20] / Math.pow(temperature, 5.0);
    BEdTdT[1] = 6.0 * aBWRS[21] / Math.pow(temperature, 4.0)
        + 20.0 * aBWRS[22] / Math.pow(temperature, 6.0);
    BEdTdT[2] = 6.0 * aBWRS[23] / Math.pow(temperature, 4.0)
        + 12.0 * aBWRS[24] / Math.pow(temperature, 5.0);
    BEdTdT[3] = 6.0 * aBWRS[25] / Math.pow(temperature, 4.0)
        + 20.0 * aBWRS[26] / Math.pow(temperature, 6.0);
    BEdTdT[4] = 6.0 * aBWRS[27] / Math.pow(temperature, 4.0)
        + 12.0 * aBWRS[28] / Math.pow(temperature, 5.0);
    BEdTdT[5] =
        6.0 * aBWRS[29] / Math.pow(temperature, 4.0) + 12.0 * aBWRS[30] / Math.pow(temperature, 5.0)
            + 20.0 * aBWRS[31] / Math.pow(temperature, 6.0);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponentphases, double temperature,
      double pressure) {
    return ((PhaseBWRSEos) phase).getdFdN(componentNumber);
  }

  /**
   * <p>
   * getFpoldn.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponentphases a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double getFpoldn(PhaseInterface phase, int numberOfComponentphases, double temperature,
      double pressure) {
    double temp = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += i * getBP(i) / (i - 0.0) * Math.pow(((PhaseBWRSEos) phase).getMolarDensity(), i - 1.0)
          * getdRhodn(phase, numberOfComponentphases, temperature, pressure);
    }
    return phase.getNumberOfMolesInPhase() / (R * temperature) * temp
        + ((PhaseBWRSEos) phase).getFpol() / phase.getNumberOfMolesInPhase();
  }

  /**
   * <p>
   * getdRhodn.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponentphases a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double getdRhodn(PhaseInterface phase, int numberOfComponentphases, double temperature,
      double pressure) {
    return ((PhaseBWRSEos) phase).getMolarDensity() / phase.getNumberOfMolesInPhase();
  }

  /**
   * <p>
   * getELdn.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponentphases a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double getELdn(PhaseInterface phase, int numberOfComponentphases, double temperature,
      double pressure) {
    return -2.0 * ((PhaseBWRSEos) phase).getMolarDensity() * getGammaBWRS()
        * Math.exp(-getGammaBWRS() * Math.pow(((PhaseBWRSEos) phase).getMolarDensity(), 2.0))
        * getdRhodn(phase, numberOfComponentphases, temperature, pressure);
  }

  /**
   * <p>
   * getFexpdn.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponentphases a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double getFexpdn(PhaseInterface phase, int numberOfComponentphases, double temperature,
      double pressure) {
    double oldTemp = 0.0;
    double temp = 0.0;
    oldTemp = -getBE(0) / (2.0 * getGammaBWRS())
        * getELdn(phase, numberOfComponentphases, temperature, pressure);

    temp += oldTemp;
    for (int i = 1; i < OE; i++) {
      oldTemp = -getBE(i) / (2.0 * getGammaBWRS())
          * Math.pow(((PhaseBWRSEos) phase).getMolarDensity(), 2 * i)
          * getELdn(phase, numberOfComponentphases, temperature, pressure)

          - (2.0 * i) * getBE(i) / (2.0 * getGammaBWRS()) * ((PhaseBWRSEos) phase).getEL()
              * Math.pow(((PhaseBWRSEos) phase).getMolarDensity(), 2.0 * i - 1.0)

          + getBE(i) / (2.0 * getGammaBWRS()) * (2.0 * i) / getBE(i - 1) * oldTemp;

      temp += oldTemp;
    }

    return phase.getNumberOfMolesInPhase() / (R * temperature) * temp
        + ((PhaseBWRSEos) phase).getFexp() / phase.getNumberOfMolesInPhase();
  }

  /**
   * Getter for property aBWRS.
   *
   * @return Value of property aBWRS.
   */
  public double[] getABWRS() {
    return this.aBWRS;
  }

  /**
   * <p>
   * Getter for the field <code>aBWRS</code>.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getABWRS(int i) {
    return this.aBWRS[i];
  }

  /**
   * Setter for property aBWRS.
   *
   * @param aBWRS New value of property aBWRS.
   */
  public void setABWRS(double[] aBWRS) {
    this.aBWRS = aBWRS;
  }

  /**
   * Getter for property BP.
   *
   * @param i a int
   * @return Value of property BP. public double[] getBPdT() { return this.BPdT; }
   */
  public double getBP(int i) {
    return this.BP[i];
  }

  /**
   * Setter for property BP.
   *
   * @param BP New value of property BP.
   */
  public void setBP(double[] BP) {
    this.BP = BP;
  }

  /**
   * Getter for property BE.
   *
   * @return Value of property BE.
   */
  public double[] getBE() {
    return this.BE;
  }

  /**
   * <p>
   * getBE.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getBE(int i) {
    return this.BE[i];
  }

  /**
   * Setter for property BE.
   *
   * @param BE New value of property BE.
   */
  public void setBE(double[] BE) {
    this.BE = BE;
  }

  /**
   * <p>
   * Setter for the field <code>refPhaseBWRS</code>.
   * </p>
   *
   * @param refPhaseBWRS a {@link neqsim.thermo.phase.PhaseBWRSEos} object
   */
  public void setRefPhaseBWRS(neqsim.thermo.phase.PhaseBWRSEos refPhaseBWRS) {
    this.refPhaseBWRS = refPhaseBWRS;
  }

  /**
   * Getter for property gammaBWRS.
   *
   * @return Value of property gammaBWRS.
   */
  public double getGammaBWRS() {
    return gammaBWRS;
  }

  /**
   * Setter for property gammaBWRS.
   *
   * @param gammaBWRS New value of property gammaBWRS.
   */
  public void setGammaBWRS(double gammaBWRS) {
    this.gammaBWRS = gammaBWRS;
  }

  /**
   * Getter for property BPdT.
   *
   * @return Value of property BPdT.
   */
  public double[] getBPdT() {
    return this.BPdT;
  }

  /**
   * <p>
   * getBPdT.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getBPdT(int i) {
    return this.BPdT[i];
  }

  /**
   * Setter for property BPdT.
   *
   * @param BPdT New value of property BPdT.
   */
  public void setBPdT(double[] BPdT) {
    this.BPdT = BPdT;
  }

  /**
   * Getter for property BEdT.
   *
   * @return Value of property BEdT.
   */
  public double[] getBEdT() {
    return this.BEdT;
  }

  /**
   * <p>
   * getBEdT.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getBEdT(int i) {
    return this.BEdT[i];
  }

  /**
   * Setter for property BEdT.
   *
   * @param BEdT New value of property BEdT.
   */
  public void setBEdT(double[] BEdT) {
    this.BEdT = BEdT;
  }

  /**
   * Getter for property BPdTdT.
   *
   * @param i array index
   * @return Value of BPdTdT[i].
   */
  public double getBPdTdT(int i) {
    return this.BPdTdT[i];
  }

  /**
   * Getter for property BEdTdT.
   *
   * @param i array index
   * @return Value of BEdTdT[i].
   */
  public double getBEdTdT(int i) {
    return this.BEdTdT[i];
  }

  /**
   * Getter for property rhoc.
   *
   * @return Value of property rhoc.
   */
  public double getRhoc() {
    return rhoc;
  }

  /**
   * Setter for property rhoc.
   *
   * @param rhoc New value of property rhoc.
   */
  public void setRhoc(double rhoc) {
    this.rhoc = rhoc;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    if (!super.equals(obj)) {
      return false;
    }
    ComponentBWRS other = (ComponentBWRS) obj;
    if (Double.compare(rhoc, other.rhoc) != 0) {
      return false;
    }
    if (Double.compare(gammaBWRS, other.gammaBWRS) != 0) {
      return false;
    }
    if (!java.util.Arrays.equals(aBWRS, other.aBWRS)) {
      return false;
    }
    if (!java.util.Arrays.equals(BP, other.BP)) {
      return false;
    }
    if (!java.util.Arrays.equals(BE, other.BE)) {
      return false;
    }
    if (!java.util.Arrays.equals(BPdT, other.BPdT)) {
      return false;
    }
    if (!java.util.Arrays.equals(BEdT, other.BEdT)) {
      return false;
    }
    if (!java.util.Arrays.equals(BPdTdT, other.BPdTdT)) {
      return false;
    }
    if (!java.util.Arrays.equals(BEdTdT, other.BEdTdT)) {
      return false;
    }
    return true;
  }
}
