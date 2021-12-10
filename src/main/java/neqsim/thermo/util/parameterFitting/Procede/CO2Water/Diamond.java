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
 *
 * @author agrawalnj
 */
public class Diamond {
    static Logger logger = LogManager.getLogger(Diamond.class);

    public Diamond() {
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        double temperature, x, pressure, ID;

        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("SELECT * FROM Diamond");

        try {
            while (dataSet.next()) {
                ID = Double.parseDouble(dataSet.getString("ID"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                pressure = Double.parseDouble(dataSet.getString("Pressure"));
                x = Double.parseDouble(dataSet.getString("x"));

                SystemInterface testSystem =
                        new SystemSrkSchwartzentruberEos(temperature, 0.9 * pressure);
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
                } catch (Exception e) {
                    logger.error(e.toString());
                }

                // System.out.println(testSystem.getPressure()*testSystem.getPhase(0).getComponent(0).getx());
                try (PrintStream p = new PrintStream(new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true))) {
                    p.println(ID + " " + x + " " + pressure + " " + testSystem.getPressure());
                } catch (FileNotFoundException e) {
                    logger.error("Could not find file");
                    logger.error("Could not read from Patrick.txt" + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }
        logger.info("Finished");
    }
}
