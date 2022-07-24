package neqsim.thermo.util.parameterFitting.Procede.Density;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * density class.
 * </p>
 *
 * @author agrawalnj
 * @version $Id: $Id
 */
public class density {
  static Logger logger = LogManager.getLogger(density.class);

  /**
   * <p>
   * Constructor for density.
   * </p>
   */
  public density() {}

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  public static void main(String[] args) {
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

      try (PrintStream p =
          new PrintStream(new FileOutputStream("C:/java/NeqSimSource/Patrick.txt", true))) {
        p.println(
            loading + " " + testSystem.getPhase(1).getPhysicalProperties().getDensity() / 1000);
      } catch (FileNotFoundException ex) {
        logger.error("Could not find file");
        logger.error("Could not write to Patrick.txt" + ex.getMessage());
      }
    }
  }
}
