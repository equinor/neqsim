package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * FreezeOut class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FreezeOut extends ConstantDutyTemperatureFlash
    implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FreezeOut.class);

  public double[] FCompTemp = new double[10];
  public String[] FCompNames = new String[10];
  public boolean noFreezeFlash = true;

  /**
   * <p>
   * Constructor for FreezeOut.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FreezeOut(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    SystemInterface testSystem = system;
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // double[][] Fug = new double[12][35];
    // double[][] Fugrel = new double[2][40];
    int iterations = 0;
    double newTemp = 0;
    double OldTemp = 0;
    double FugRatio;
    double T2low = 0;
    double T2high = 0;
    boolean Left = true;
    boolean half = false;
    double SolidFug = 0.0;
    double FluidFug = 0.0;
    double temp = 0.0;
    double pres = 0.0;
    double Pvapsolid = 0.0;
    double solvol = 0.0;
    double soldens = 0.0;
    double trpTemp = 0.0;
    boolean CCequation = true;
    boolean noFreezeliq = true;
    boolean SolidForms = true;
    double maximum = 0;

    for (int k = 0; k < testSystem.getPhase(0).getNumberOfComponents(); k++) {
      FCompNames[k] = testSystem.getPhase(0).getComponent(k).getComponentName();
      if (testSystem.getPhase(0).getComponent(k).doSolidCheck()) {
        trpTemp = testSystem.getPhases()[0].getComponent(k).getTriplePointTemperature();
        if (noFreezeFlash) {
          testSystem.setTemperature(trpTemp);
          logger.info("Starting at Triple point temperature "
              + system.getPhase(0).getComponent(k).getComponentName());
        } else {
          testSystem.setTemperature(FCompTemp[k]);
          logger.info("starting at Temperature  " + system.getTemperature());
        }

        SystemInterface testSystem2 = new SystemSrkSchwartzentruberEos(216, 1);
        ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2);
        testSystem2.addComponent(testSystem.getPhase(0).getComponent(k).getComponentName(), 1);
        testSystem2.setPhaseType(0, PhaseType.GAS);
        noFreezeliq = true;
        SolidFug = 0.0;
        FluidFug = 0.0;
        SolidForms = true;
        temp = 0.0;
        Pvapsolid = 0.0;
        iterations = 0;
        half = false;
        T2high = trpTemp + 0.1;
        if (Math.abs(testSystem.getPhases()[0].getComponent(k).getHsub()) < 1) {
          CCequation = false;
        }
        do {
          iterations++;
          logger.info("-------------");
          temp = testSystem.getTemperature();
          logger.info("temperature " + temp);
          if (temp > trpTemp + 0.01) {
            temp = trpTemp;
          }
          if (CCequation) {
            Pvapsolid = testSystem.getPhase(0).getComponent(k).getCCsolidVaporPressure(temp);
          } else {
            Pvapsolid = testSystem.getPhase(0).getComponent(k).getSolidVaporPressure(temp);
          }
          soldens =
              testSystem.getPhase(0).getComponent(k).getPureComponentSolidDensity(temp) * 1000;
          if (soldens > 2000) {
            soldens = 1000;
          }
          solvol = 1.0 / soldens * testSystem.getPhase(0).getComponent(k).getMolarMass();

          logger.info("solid density " + soldens);
          testSystem.setTemperature(temp);
          testSystem2.setTemperature(temp);
          testSystem2.setPressure(Pvapsolid);
          testOps.TPflash();
          testOps2.TPflash();

          logger.info("Partial pressure "
              + testSystem.getPhase(1).getComponent(k).getx() * testSystem.getPressure());

          SolidFug = Pvapsolid * testSystem2.getPhase(0).getComponent(0).getFugacityCoefficient()
              * Math.exp(solvol / (R * temp) * (pres - Pvapsolid));
          FluidFug = testSystem.getPhase(0).getFugacity(k);

          FugRatio = SolidFug / FluidFug;

          OldTemp = testSystem.getTemperature();
          logger.info("Temperature " + OldTemp);
          logger.info("FugRatio solid/fluidphase " + FugRatio);

          if (1 < (FugRatio)) {
            if (OldTemp < trpTemp / 3) {
              SolidForms = false;
            }
            T2high = OldTemp;

            if (half) {
              newTemp = 0.5 * (T2low + T2high);
            } else if (1.5 > FugRatio) {
              newTemp = OldTemp - trpTemp * 0.1;
            } else if (1.5 < FugRatio) {
              newTemp = OldTemp - trpTemp * 0.15;
            } else {
              newTemp = OldTemp - trpTemp * 0.15;
            }
            Left = false;
          } else if (1 > (FugRatio)) {
            if (Left && ((OldTemp - trpTemp) > 0)) {
              noFreezeliq = false;
            }

            T2low = OldTemp;
            Left = true;
            half = true;
            newTemp = 0.5 * (T2low + T2high);
          }

          testSystem.setTemperature(newTemp);
        } while (((Math.abs(FugRatio - 1) >= 0.00001 && iterations < 100)) && noFreezeliq
            && SolidForms);
        logger.info("noFreezeliq: " + noFreezeliq + " SolidForms: " + SolidForms);

        if (noFreezeliq && SolidForms) {
          testSystem.setTemperature(OldTemp);
          FCompTemp[k] = OldTemp;
        } else if (!noFreezeliq) {
          testSystem.setTemperature(OldTemp);
          FCompTemp[k] = OldTemp;
          logger.error("Freezing Temperature not found");
        } else {
          testSystem.setTemperature(1000);
          FCompTemp[k] = OldTemp;
        }

        logger.info("Iterations :" + iterations);
      } // end Iflokke
    } // end for
    maximum = FCompTemp[0]; // start with the first value
    for (int i = 1; i < FCompTemp.length; i++) {
      if (FCompTemp[i] > maximum) {
        maximum = FCompTemp[i]; // new maximum
      }
    }

    testSystem.setTemperature(maximum);
    // this.printToFile("FrzOut");
  } // end Main

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {
    for (int n = 0; n < system.getPhases()[0].getNumberOfComponents(); n++) {
      name = name + "_" + system.getPhase(0).getComponent(n).getComponentName();
    }

    String myFile = "/java/" + name + ".frz";

    try (PrintWriter pr_writer = new PrintWriter(new FileWriter(myFile, true))) {
      pr_writer.println("name,freezeT,freezeP,z,iterations");
      pr_writer.flush();

      for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
        // print line to output file
        pr_writer.println(FCompNames[k] + "," + java.lang.Double.toString(FCompTemp[k]) + ","
            + system.getPressure() + ","
            + java.lang.Double.toString(system.getPhases()[0].getComponent(k).getz()));
        pr_writer.flush();
      }
    } catch (SecurityException ex) {
      logger.error("writeFile: caught security exception");
    } catch (IOException ioe) {
      logger.error("writeFile: caught i/o exception");
    }
  }
}
