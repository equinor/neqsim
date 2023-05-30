/*
 * ChemicalReactionFactory.java
 *
 * Created on 20. september 2004, 22:40
 */

package neqsim.chemicalReactions.chemicalReaction;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * ChemicalReactionFactory class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class ChemicalReactionFactory {
  static Logger logger = LogManager.getLogger(ChemicalReactionFactory.class);

  /**
   * <p>
   * Constructor for ChemicalReactionFactory.
   * </p>
   */
  public ChemicalReactionFactory() {}

  /**
   * <p>
   * getChemicalReaction.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link neqsim.chemicalReactions.chemicalReaction.ChemicalReaction} object
   */
  public static ChemicalReaction getChemicalReaction(String name) {
    ArrayList<String> names = new ArrayList<String>();
    ArrayList<String> stocCoef = new ArrayList<String>();
    double[] K = new double[4];
    double refT = 0;
    double rateFactor = 0;
    double activationEnergy = 0;

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet =
            database.getResultSet("SELECT * FROM reactionkspdata where name='" + name + "'")) {
      dataSet.next();
      String reacname = dataSet.getString("name");
      K[0] = Double.parseDouble(dataSet.getString("K1"));
      K[1] = Double.parseDouble(dataSet.getString("K2"));
      K[2] = Double.parseDouble(dataSet.getString("K3"));
      K[3] = Double.parseDouble(dataSet.getString("K4"));
      refT = Double.parseDouble(dataSet.getString("Tref"));
      rateFactor = Double.parseDouble(dataSet.getString("r"));

      activationEnergy = Double.parseDouble(dataSet.getString("ACTENERGY"));
      try (NeqSimDataBase database2 = new neqsim.util.database.NeqSimDataBase();
          java.sql.ResultSet dataSet2 = database2
              .getResultSet("SELECT * FROM stoccoefdata where reacname='" + reacname + "'")) {
        dataSet2.next();
        do {
          names.add(dataSet2.getString("compname").trim());
          stocCoef.add((dataSet2.getString("stoccoef")).trim());
        } while (dataSet2.next());
        // System.out.println("reaction added ok...");
      } catch (Exception ex) {
        logger.error("Could not add reaction", ex);
      }
    } catch (Exception ex) {
      logger.error("Could not add reaction", ex);
    }

    String[] nameArray = new String[names.size()];
    double[] stocCoefArray = new double[names.size()];
    for (int i = 0; i < names.size(); i++) {
      nameArray[i] = names.get(i);
      stocCoefArray[i] = Double.parseDouble(stocCoef.get(i));
    }
    return new ChemicalReaction(name, nameArray, stocCoefArray, K, rateFactor, activationEnergy,
        refT);
  }

  /**
   * Get names of all chemical rections in database.
   *
   * @return Names of all chemical reactions in database.
   */
  public static String[] getChemicalReactionNames() {
    ArrayList<String> nameList = new ArrayList<String>();
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = database.getResultSet("SELECT name FROM reactionkspdata")) {
      dataSet.next();
      do {
        nameList.add(dataSet.getString("name").trim());
      } while (dataSet.next());
    } catch (Exception ex) {
      logger.error("Failed reading from database.");
    }

    return nameList.toArray(new String[0]);
  }
}
