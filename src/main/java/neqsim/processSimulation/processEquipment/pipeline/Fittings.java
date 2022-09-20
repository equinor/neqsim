package neqsim.processSimulation.processEquipment.pipeline;

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
  private static final long serialVersionUID = 1000;
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

  public class Fitting implements Serializable {
    private static final long serialVersionUID = 1000;

    private String fittingName = "";
    private double LtoD = 1.0;

    public Fitting(String name, double LdivD) {
      this.fittingName = name;
      LtoD = LdivD;
    }

    public Fitting(String name) {
      this.fittingName = name;

      neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
      java.sql.ResultSet dataSet = null;
      try {
        dataSet = database.getResultSet(("SELECT * FROM fittings WHERE name='" + name + "'"));
        dataSet.next();
        LtoD = (Double.parseDouble(dataSet.getString("LtoD")));
        System.out.printf("LtoD " + LtoD);
      } catch (Exception ex) {
        System.out.println("error in comp");
        logger.error(ex.getMessage());
      } finally {
        try {
          dataSet.close();
        } catch (Exception ex) {
          System.out.println("error closing database.....");
          logger.error(ex.getMessage());
        }
      }
    }

    /**
     * @return the fittingName
     */
    public String getFittingName() {
      return fittingName;
    }

    /**
     * @param fittingName the fittingName to set
     */
    public void setFittingName(String fittingName) {
      this.fittingName = fittingName;
    }

    /**
     * @return the LtoD
     */
    public double getLtoD() {
      return LtoD;
    }

    /**
     * @param LtoD the LtoD to set
     */
    public void setLtoD(double LtoD) {
      this.LtoD = LtoD;
    }
  }
}
