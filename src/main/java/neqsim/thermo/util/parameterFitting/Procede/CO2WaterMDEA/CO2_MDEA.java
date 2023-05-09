package neqsim.thermo.util.parameterFitting.Procede.CO2WaterMDEA;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * CO2_MDEA class.
 * </p>
 *
 * @author agrawalnj
 * @version $Id: $Id
 */
public class CO2_MDEA {
  static Logger logger = LogManager.getLogger(CO2_MDEA.class);

  /**
   * <p>
   * Constructor for CO2_MDEA.
   * </p>
   */
  public CO2_MDEA() {}

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args the command line arguments
   */
  @SuppressWarnings("unused")
  public static void main(String[] args) {
    int i = 0;
    int j;
    int CO2Numb = 0;
    int WaterNumb = 0;
    int MDEANumb = 0;
    int HCO3Numb = 0;
    int MDEAHpNumb = 0;
    double ID;

    double pressure;
    double temperature;
    double x1;
    double x2;
    double x3;
    double bias;

    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =
            database.getResultSet("SELECT * FROM CO2WaterMDEA WHERE ID>196 AND ID<231")) {
      while (dataSet.next()) {
        i += 1;
        logger.info("Adding.... " + i);

        ID = Double.parseDouble(dataSet.getString("ID"));
        pressure = Double.parseDouble(dataSet.getString("PressureCO2"));
        temperature = Double.parseDouble(dataSet.getString("Temperature"));
        x1 = Double.parseDouble(dataSet.getString("x1"));
        x2 = Double.parseDouble(dataSet.getString("x2"));
        x3 = Double.parseDouble(dataSet.getString("x3"));

        /*
         * if((ID>56 && ID<64) || (ID>92 && ID<101) || (ID>123 && ID<131)) //75 wt% amine continue;
         */
        logger.info("................ID............ " + ID);
        SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);

        testSystem.addComponent("CO2", x1);
        testSystem.addComponent("MDEA", x3);
        testSystem.addComponent("water", x2);

        testSystem.chemicalReactionInit();
        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);

        j = 0;
        do {
          CO2Numb = j;
          j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
            .equals("CO2"));

        j = 0;
        do {
          MDEANumb = j;
          j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
            .equals("MDEA"));

        j = 0;
        do {
          WaterNumb = j;
          j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
            .equals("water"));

        j = 0;
        do {
          HCO3Numb = j;
          j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
            .equals("HCO3-"));

        j = 0;
        do {
          MDEAHpNumb = j;
          j++;
        } while (!testSystem.getPhases()[1].getComponents()[j - 1].getComponentName()
            .equals("MDEA+"));

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
          testOps.bubblePointPressureFlash(false);
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }

        bias = (pressure
            - testSystem.getPressure() * testSystem.getPhase(0).getComponent(CO2Numb).getx())
            / pressure * 100;
        // bias = (pressure-testSystem.getPressure())/pressure*100;

        // logger.info("Bias "+bias);
        // logger.info("Act "+testSystem.getPhase(1).getActivityCoefficient(MDEAHpNumb,
        // WaterNumb));
        // logger.info("Pressure CO2
        // "+testSystem.getPressure()*testSystem.getPhase(0).getComponent(CO2Numb).getx());
        try (PrintStream p =
            new PrintStream(new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true))) {
          p.println(ID + " " + pressure + " "
              + testSystem.getPressure() * testSystem.getPhase(0).getComponent(CO2Numb).getx());
        } catch (FileNotFoundException ex) {
          logger.error("Could not find file", ex);
        }
      }
    } catch (Exception ex) {
      logger.error("database error ", ex);
    }

    logger.info("Finished");
  }
}
