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

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet = null;
      try {
        dataSet = database.getResultSet(("SELECT * FROM mbwr32param WHERE name='" + name + "'"));
        dataSet.next();
        dataSet.getClob("name");
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }

      for (int i = 0; i < 32; i++) {
        aBWRS[i] = Double.parseDouble(dataSet.getString("a" + i));
        // System.out.println(aBWRS[i]);
      }
      rhoc = Double.parseDouble(dataSet.getString("rhoc"));
      gammaBWRS = 1.0 / (rhoc * rhoc);
      // logger.info("gamma " + gammaBWRS);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * Constructor for ComponentBWRS.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature
   * @param PC Critical pressure
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

    // disse deriverte er ennaa ikke satt inn (finnes i Odvar's avhandling)
    BPdTdT[0] = 0;
    BPdTdT[1] = 0;
    BPdTdT[2] = 0;
    BPdTdT[3] = 0;
    BPdTdT[4] = 0;
    BPdTdT[5] = 0;
    BPdTdT[6] = 0;
    BPdTdT[7] = 0;
    BPdTdT[8] = 0;

    BEdTdT[0] = 0.0;
    BEdTdT[1] = 0.0;
    BEdTdT[2] = 0.0;
    BEdTdT[3] = 0.0;
    BEdTdT[4] = 0.0;
    BEdTdT[5] = 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponentphases, double temperature,
      double pressure) {
    // System.out.println("Fref " +
    // refPhaseBWRS.getF()/phase.getNumberOfMolesInPhase());
    // System.out.println("Fref2 " + 1e3*(getFpoldn(phase, numberOfComponentphases,
    // temperature, pressure) + getFexpdn(phase, numberOfComponentphases,
    // temperature, pressure)));
    // System.out.println("Fref3 " + refPhaseBWRS.getdFdN());
    return refPhaseBWRS.getF() / phase.getNumberOfMolesInPhase();
    // return refPhaseBWRS.getdFdN();
    // System.out.println("dFdN super " +
    // super.dFdN(phase,numberOfComponentphases,temperature,pressure));
    // System.out.println("this dFdN " + 1e3*(getFpoldn(phase, numberOfComponentphases,
    // temperature, pressure) + getFexpdn(phase, numberOfComponentphases, temperature,
    // pressure)));
    // return 1e3*(getFpoldn(phase, numberOfComponentphases, temperature, pressure)
    // + getFexpdn(phase, numberOfComponentphases, temperature, pressure));
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
}
