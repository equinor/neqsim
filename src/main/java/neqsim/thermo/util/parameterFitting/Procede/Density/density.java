package neqsim.thermo.util.parameterFitting.Procede.Density;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;

/*
 *
 * @author agrawalnj
 */
public class density {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(density.class);

    /** Creates a new instance of Sleipneracetate */
    public density() {}

    @SuppressWarnings("unused")
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

        double n1, n2, n3, x1, x2, x3, total;
        double MDEAwt = 0.44;
        double loading = 0.0;
        double temperature = 273.16 + 20;
        double pressure = 0.01;

        for (loading = 0.0; loading <= 1.0; loading += 0.1) {
            n3 = MDEAwt / 119.16;
            n2 = (1 - MDEAwt) / 18.015;
            n1 = n3 * loading;
            total = n1 + n2 + n3;
            x1 = n1 / total;
            x2 = n2 / total;
            x3 = n3 / total;

            SystemInterface testSystem = new SystemFurstElectrolyteEos(298, 1);

            testSystem.addComponent("CO2", x1);
            testSystem.addComponent("MDEA", x3);
            testSystem.addComponent("water", x2);

            testSystem.chemicalReactionInit();
            testSystem.createDatabase(true);
            testSystem.useVolumeCorrection(true);
            testSystem.setMixingRule(4);
            testSystem.init(0);
            testSystem.init(1);
            testSystem.initPhysicalProperties();

            try {
                outfile = new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true);
                p = new PrintStream(outfile);
                p.println(loading + " "
                        + testSystem.getPhase(1).getPhysicalProperties().getDensity() / 1000);
                p.close();
            } catch (FileNotFoundException e) {
                logger.error("Could not find file");

                logger.error("Could not write to Patrick.txt" + e.getMessage());
            }
        }
    }
}
