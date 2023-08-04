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
  static Logger logger = LogManager.getLogger(Element.class);

  private String name;
  private String[] nameArray;
  private double[] coefArray;

  /**
   * <p>
   * Constructor for Element.
   * </p>
   *
   * @param name Name of component.
   */
  public Element(String name) {
    this.name = name;

    ArrayList<String> names = new ArrayList<String>();
    ArrayList<String> stocCoef = new ArrayList<String>();

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet =
            database.getResultSet(("SELECT * FROM element WHERE componentname='" + name + "'"))) {

      if (!dataSet.next()) {
        return;
      }

      do {
        names.add(dataSet.getString("atomelement").trim());
        stocCoef.add(dataSet.getString("number"));
      } while (dataSet.next());

      nameArray = new String[names.size()];
      coefArray = new double[nameArray.length];
      for (int i = 0; i < nameArray.length; i++) {
        coefArray[i] = Double.parseDouble(stocCoef.get(i));
        nameArray[i] = names.get(i);
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * Getter for property name.
   *
   * @return Component name.
   */
  public String getName() {
    return this.name;
  }

  /**
   * Getter for property nameArray.
   *
   * @return an array of {@link java.lang.String} objects. Names of Elements of component.
   */
  public String[] getElementNames() {
    return nameArray;
  }

  /**
   * Getter for property coefArray.
   *
   * @return an array of {@link double} objects. Coefficient corresponding to nameArray.
   */
  public double[] getElementCoefs() {
    return coefArray;
  }

  /**
   * Get all defined components.
   *
   * @return All element names in database.
   */
  public static ArrayList<String> getAllElementComponentNames() {
    ArrayList<String> names = new ArrayList<String>();
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM element"))) {
      dataSet.next();
      do {
        names.add(dataSet.getString("componentname").trim());
      } while (dataSet.next());
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    return names;
  }
}
