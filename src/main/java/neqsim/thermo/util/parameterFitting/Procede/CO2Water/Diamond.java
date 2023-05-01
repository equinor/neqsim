package neqsim.thermo.util.parameterFitting.Procede.CO2Water;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * Diamond class.
 * </p>
 *
 * @author agrawalnj
 * @version $Id: $Id
 */
public class Diamond {
  static Logger logger = LogManager.getLogger(Diamond.class);

  /**
   * <p>
   * Constructor for Diamond.
   * </p>
   */
  public Diamond() {}

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    double temperature;

    double x;
    double pressure;
    double ID;

    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("SELECT * FROM Diamond")) {
      while (dataSet.next()) {
        ID = Double.parseDouble(dataSet.getString("ID"));
        temperature = Double.parseDouble(dataSet.getString("Temperature"));
        pressure = Double.parseDouble(dataSet.getString("Pressure"));
        x = Double.parseDouble(dataSet.getString("x"));

        SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, 0.9 * pressure);
        testSystem.addComponent("CO2", x);
        testSystem.addComponent("water", 1 - x);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);
        testSystem.init(1);

        logger.info(ID);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
          testOps.bubblePointPressureFlash(false);
        } catch (Exception ex) {
          logger.error(ex.toString());
        }

        // System.out.println(testSystem.getPressure()*testSystem.getPhase(0).getComponent(0).getx());
        try (PrintStream p =
            new PrintStream(new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true))) {
          p.println(ID + " " + x + " " + pressure + " " + testSystem.getPressure());
        } catch (FileNotFoundException ex) {
          logger.error("Could not find file");
          logger.error("Could not read from Patrick.txt" + ex.getMessage());
        }
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }
    logger.info("Finished");
  }
}
