/*
 * Element.java
 *
 * Created on 4. februar 2001, 22:11
 */

package neqsim.thermo.atomElement;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * Element class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Element implements ThermodynamicConstantsInterface {
  private static final long serialVersionUID = 1000;
  String[] nameArray;
  double[] coefArray;
  static Logger logger = LogManager.getLogger(Element.class);

  /**
   * <p>
   * Constructor for Element.
   * </p>
   */
  public Element() {}

  /**
   * <p>
   * Constructor for Element.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Element(String name) {
    ArrayList<String> names = new ArrayList<String>();
    ArrayList<String> stocCoef = new ArrayList<String>();

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet =
            database.getResultSet(("SELECT * FROM element WHERE componentname='" + name + "'"))) {
      dataSet.next();
      // System.out.println("comp name " + dataSet.getString("componentname"));
      do {
        names.add(dataSet.getString("atomelement").trim());
        // System.out.println("name " + dataSet.getString("atomelement"));
        stocCoef.add(dataSet.getString("number"));
      } while (dataSet.next());

      nameArray = new String[names.size()];
      coefArray = new double[nameArray.length];
      for (int i = 0; i < nameArray.length; i++) {
        coefArray[i] = Double.parseDouble(stocCoef.get(i));
        nameArray[i] = names.get(i);
      }
    } catch (Exception ex) {
      logger.error(ex.toString());
    }
  }

  /**
   * <p>
   * getElementNames.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getElementNames() {
    return nameArray;
  }

  /**
   * <p>
   * getElementCoefs.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] getElementCoefs() {
    return coefArray;
  }
}
