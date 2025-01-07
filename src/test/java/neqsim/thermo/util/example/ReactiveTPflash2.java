package neqsim.thermo.util.example;
/*
 * ReactiveTPflash2.java
 *
 * Created on 27. september 2001, 09:43
 */

import java.io.File;
import java.io.FileWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ReactiveTPflash2 class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class ReactiveTPflash2 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ReactiveTPflash2.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemFurstElectrolyteEos(373.15, 10.0);

    try (FileWriter out = new FileWriter(new File("c:/temp/Wt50T25P10.txt"))) {
      out.write("wt% = 50, T = 40 C and P = 10 bar" + "\n");
      out.write("\n");
      out.write("wt% MDEA" + "\t\t" + "x_H2O(l)" + "\t\t\t" + "x_MDEA(l)" + "\t\t\t" + "x_CO2(l)"
          + "\t\t\t" + "x_CH4(l)" + "\t\t\t" + "x_MDEA+(l)" + "\t\t\t" + "x_HCO3-(l)" + "\t\t\t"
          + "x_CO3--(l)" + "\t\t\t" + "x_OH-(l)" + "\t\t\t" + "x_H3O+(l)" + "\t\t\t" + "x_H2O(g)"
          + "\t\t\t" + "x_MDEA(g)" + "\t\t\t" + "x_CO2(g)" + "\t\t\t" + "x_CH4(g)" + "\n");

      int imax = 3;
      double max = 15;
      double min = 0.01;

      for (int i = 0; i < imax; i++) {
        // Adding components
        double den = imax - 1.0;
        double methane = max - (max - min) / den * i;
        double co2 = min + (max - min) / den * i;
        // System.out.println(methane);
        // System.out.println(co2);
        testSystem.addComponent("methane", methane);
        testSystem.addComponent("CO2", co2);
        testSystem.addComponent("water", 50.0);
        // testSystem.addComponent("Piperazine", 1.78);
        testSystem.addComponent("MDEA", 7.5585298);

        // Initialization of etc
        testSystem.chemicalReactionInit();
        testSystem.useVolumeCorrection(true);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
        testSystem.init(0);
        // System.out.println("wt% Piperazine " +
        // testSystem.getPhase(1).getComponent("Piperazine").getx()*testSystem.getPhase(1).getComponent("Piperazine").getMolarMass()/testSystem.getPhase(1).getMolarMass());

        // Flash calculation
        try {
          // ops.bubblePointPressureFlash(false);
          ops.TPflash();
        } catch (Exception ex) {
        }

        // Write to screen
        // testSystem.display();
        // System.out.println("CO2 loading " +
        // (testSystem.getPhase(1).getComponent("CO2").getx()+testSystem.getPhase(1).getComponent("HCO3-").getx()+testSystem.getPhase(1).getComponent("CO3--").getx())/(testSystem.getPhase(1).getComponent("MDEA+").getx()+testSystem.getPhase(1).getComponent("MDEA").getx()));
        // System.out.println("Partial pressure CO2 " +
        // testSystem.getPressure()*testSystem.getPhase(0).getComponent("CO2").getx());
        // System.out.println("wt% MDEA " +
        // 100*(testSystem.getPhase(1).getComponent("MDEA+").getx()+testSystem.getPhase(1).getComponent("MDEA").getx())*testSystem.getPhase(1).getComponent("MDEA").getMolarMass()/((testSystem.getPhase(1).getComponent("water").getx()+testSystem.getPhase(1).getComponent("CO3--").getx()+testSystem.getPhase(1).getComponent("HCO3-").getx())*testSystem.getPhase(1).getComponent("water").getMolarMass()+(testSystem.getPhase(1).getComponent("MDEA+").getx()+testSystem.getPhase(1).getComponent("MDEA").getx())*testSystem.getPhase(1).getComponent("MDEA").getMolarMass()));

        // Writing to file
        out.write("" + 100
            * (testSystem.getPhase(1).getComponent("MDEA+").getx()
                + testSystem.getPhase(1).getComponent("MDEA").getx())
            * testSystem.getPhase(1).getComponent("MDEA").getMolarMass()
            / ((testSystem.getPhase(1).getComponent("water").getx()
                + testSystem.getPhase(1).getComponent("CO3--").getx()
                + testSystem.getPhase(1).getComponent("HCO3-").getx())
                * testSystem.getPhase(1).getComponent("water").getMolarMass()
                + (testSystem.getPhase(1).getComponent("MDEA+").getx()
                    + testSystem.getPhase(1).getComponent("MDEA").getx())
                    * testSystem.getPhase(1).getComponent("MDEA").getMolarMass()));
        out.write("\t" + testSystem.getPhase(1).getComponent("water").getx() + "\t"
            + testSystem.getPhase(1).getComponent("MDEA").getx() + "\t"
            + testSystem.getPhase(1).getComponent("CO2").getx() + "\t"
            + testSystem.getPhase(1).getComponent("methane").getx() + "\t"
            + testSystem.getPhase(1).getComponent("MDEA+").getx() + "\t"
            + testSystem.getPhase(1).getComponent("HCO3-").getx() + "\t"
            + testSystem.getPhase(1).getComponent("CO3--").getx() + "\t"
            + testSystem.getPhase(1).getComponent("OH-").getx() + "\t"
            + testSystem.getPhase(1).getComponent("H3O+").getx() + "\t"
            + testSystem.getPhase(0).getComponent("water").getx() + "\t"
            + testSystem.getPhase(0).getComponent("MDEA").getx() + "\t"
            + testSystem.getPhase(0).getComponent("CO2").getx() + "\t"
            + testSystem.getPhase(0).getComponent("methane").getx());
        out.write("\n");
      } // end for-loop
      out.flush();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    // for(int i=0;i<23;i++){
    // try{
    // ops.bubblePointPressureFlash(false);
    // // testSystem.display();
    // //ops.TPflash();
    // } catch(Exception ex){}

    // System.out.println("loading " + (0.0005+0.05*i)+ " PCO2 " +
    // testSystem.getPhase(0).getComponent("CO2").getx()*testSystem.getPressure());
    // testSystem.addComponent("CO2", 0.05*(6.45+1.78));
    // }
  }
}
