package neqsim.thermo.util.parameterFitting.Procede.WaterMDEA;

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
 * <p>Water_MDEA class.</p>
 *
 * @author agrawalnj
 * @version $Id: $Id
 */
public class Water_MDEA {
    static Logger logger = LogManager.getLogger(Water_MDEA.class);

    /**
     * <p>Constructor for Water_MDEA.</p>
     */
    public Water_MDEA() {}

    /**
     * <p>main.</p>
     *
     * @param args the command line arguments
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        double pressure = 1;
        double temperature = 25 + 273.16;
        double x1, x2;

        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("SELECT * FROM WaterMDEA");

        try {
            while (dataSet.next()) {
                double ID = Double.parseDouble(dataSet.getString("ID"));
                pressure = Double.parseDouble(dataSet.getString("Pressure"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                x1 = Double.parseDouble(dataSet.getString("x1"));
                x2 = Double.parseDouble(dataSet.getString("x2"));

                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(298, pressure);

                testSystem.addComponent("water", x1);
                testSystem.addComponent("MDEA", x2);

                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);

                ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

                try {
                    testOps.bubblePointPressureFlash(false);
                } catch (Exception e) {
                    logger.error(e.toString());
                }

                double hm = testSystem.getPhase(1).getEnthalpy();
                logger.info(hm);

                try (PrintStream p = new PrintStream(
                        new FileOutputStream("C:/java/NeqSimSource/water_MDEA.txt", true))) {
                    p.println(ID + " " + pressure + " " + testSystem.getPressure());
                } catch (FileNotFoundException e) {
                    logger.error("Could not find file", e);
                }
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }
        logger.info("Finished");
    }
}
