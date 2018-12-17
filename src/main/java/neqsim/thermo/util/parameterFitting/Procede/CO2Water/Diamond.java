package neqsim.thermo.util.parameterFitting.Procede.CO2Water;

import neqsim.util.database.NeqSimDataBase;
import java.io.*;
import java.sql.*;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

/*
 * Sleipneracetate.java
 *
 * Created on August 6, 2004, 11:41 AM
 */
/**
 *
 * @author agrawalnj
 */
public class Diamond {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(Diamond.class);

    /**
     * Creates a new instance of Sleipneracetate
     */
    public Diamond() {
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        FileOutputStream outfile;
        PrintStream p;
        try {
            outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt");
            p = new PrintStream(outfile);
            p.close();
        } catch (IOException e) {
            logger.error("Could not find file");
        }
        double temperature, x, pressure, ID;

        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet( "SELECT * FROM Diamond");

        try {
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
                } catch (Exception e) {
                    logger.error(e.toString());
                }

                //System.out.println(testSystem.getPressure()*testSystem.getPhase(0).getComponent(0).getx());
                try {
                    outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true);
                    p = new PrintStream(outfile);
                    p.println(ID + " " + x + " " + pressure + " " + testSystem.getPressure());
                    p.close();
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
