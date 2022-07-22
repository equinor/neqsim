/*
 * ChemicalReactionFactory.java
 *
 * Created on 20. september 2004, 22:40
 */

package neqsim.chemicalReactions.chemicalReaction;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();

    try {
      java.sql.ResultSet dataSet = null;
      dataSet = database.getResultSet("SELECT * FROM reactionkspdata where name='" + name + "'");

      dataSet.next();
      String reacname = dataSet.getString("name");
      K[0] = Double.parseDouble(dataSet.getString("K1"));
      K[1] = Double.parseDouble(dataSet.getString("K2"));
      K[2] = Double.parseDouble(dataSet.getString("K3"));
      K[3] = Double.parseDouble(dataSet.getString("K4"));
      refT = Double.parseDouble(dataSet.getString("Tref"));
      rateFactor = Double.parseDouble(dataSet.getString("r"));

      activationEnergy = Double.parseDouble(dataSet.getString("ACTENERGY"));
      neqsim.util.database.NeqSimDataBase database2 = new neqsim.util.database.NeqSimDataBase();
      java.sql.ResultSet dataSet2 =
          database2.getResultSet("SELECT * FROM stoccoefdata where reacname='" + reacname + "'");
      dataSet2.next();
      do {
        names.add(dataSet2.getString("compname").trim());
        stocCoef.add((dataSet2.getString("stoccoef")).trim());
      } while (dataSet2.next());

      // System.out.println("reaction added ok...");
      dataSet.close();
    } catch (Exception e) {
      logger.error(e.getMessage());
      String err = e.toString();
      System.out.println("could not add reacton: " + err);
    }
    try {
      database.getConnection().close();
    } catch (Exception e) {
      System.out.println("err closing database");
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
}
