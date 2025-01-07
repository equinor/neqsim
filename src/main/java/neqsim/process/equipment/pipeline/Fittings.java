package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Fittings class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Fittings implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Fittings.class);

  ArrayList<Fitting> fittingList = new ArrayList<Fitting>();

  /**
   * <p>
   * Constructor for Fittings.
   * </p>
   */
  public Fittings() {}

  /**
   * <p>
   * add.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param LdivD a double
   */
  public void add(String name, double LdivD) {
    fittingList.add(new Fitting(name, LdivD));
  }

  /**
   * <p>
   * add.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void add(String name) {
    fittingList.add(new Fitting(name));
  }

  /**
   * <p>
   * getFittingsList.
   * </p>
   *
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<Fitting> getFittingsList() {
    return fittingList;
  }

  /**
   * Constructor for Fitting.
   */
  public class Fitting implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    private String fittingName = "";
    private double LtoD = 1.0;

    public Fitting(String name, double LdivD) {
      this.fittingName = name;
      LtoD = LdivD;
    }

    /**
     * Constructor for Fitting.
     *
     * @param name Name of fitting
     */
    public Fitting(String name) {
      this.fittingName = name;

      try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
          java.sql.ResultSet dataSet =
              database.getResultSet(("SELECT * FROM fittings WHERE name='" + name + "'"))) {
        dataSet.next();
        LtoD = (Double.parseDouble(dataSet.getString("LtoD")));
        System.out.printf("LtoD " + LtoD);
      } catch (Exception ex) {
        logger.error("error in fittings");
      }
    }

    /**
     * Getter for parameter fittingName.
     *
     * @return the fittingName
     */
    public String getFittingName() {
      return fittingName;
    }

    /**
     * Setter for parameter fittingName.
     *
     * @param fittingName the fittingName to set
     */
    public void setFittingName(String fittingName) {
      this.fittingName = fittingName;
    }

    /**
     * Getter for parameter LtoD.
     *
     * @return the LtoD
     */
    public double getLtoD() {
      return LtoD;
    }

    /**
     * Setter for parameter LtoD.
     *
     * @param LtoD the LtoD to set
     */
    public void setLtoD(double LtoD) {
      this.LtoD = LtoD;
    }
  }
}
