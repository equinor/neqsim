package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * freezingPointTemperatureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class FreezingPointTemperatureFlash extends ConstantDutyTemperatureFlash
    implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FreezingPointTemperatureFlash.class);

  public boolean noFreezeFlash = true;
  public int Niterations = 0;
  public String name = "Frz";
  public String phaseName = "oil";

  /**
   * <p>
   * Constructor for freezingPointTemperatureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FreezingPointTemperatureFlash(SystemInterface system) {
    super(system);
  }

  /**
   * <p>
   * Constructor for freezingPointTemperatureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Freeze a boolean
   */
  public FreezingPointTemperatureFlash(SystemInterface system, boolean Freeze) {
    super(system);
    noFreezeFlash = Freeze;
  }

  /**
   * <p>
   * calcFunc.
   * </p>
   *
   * @return a double
   */
  public double calcFunc() {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    // double deriv = 0, funkOld = 0;
    double funk = 0;
    double SolidFugCoeff = 0.0;
    int numbComponents = system.getPhases()[0].getNumberOfComponents();

    for (int k = 0; k < numbComponents; k++) {
      // logger.info("Checking all the components " + k);
      if (system.getPhase(0).getComponent(k).doSolidCheck()) {
        ops.TPflash(false);
        SolidFugCoeff = system.getPhase(PhaseType.SOLID).getComponent(k)
            .fugcoef(system.getPhase(PhaseType.SOLID));
        funk = system.getPhase(0).getComponent(k).getz();
        for (int i = 0; i < system.getNumberOfPhases(); i++) {
          funk -= system.getPhase(i).getBeta() * SolidFugCoeff
              / system.getPhase(i).getComponent(k).getFugacityCoefficient();
        }
      }
    }
    return funk;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    int iterations = 0;
    int maxNumberOfIterations = 100;
    double deriv = 0;
    double funk = 0;
    double funkOld = 0;
    double maxTemperature = -500;
    double minTemperature = 1e6;
    double oldTemperature = 0.0;
    double newTemp = 0.0;
    double SolidFugCoeff = 0.0;
    int numbComponents = system.getPhases()[0].getNumberOfComponents();
    String[] FCompNames = new String[numbComponents];
    double[] FCompTemp = new double[numbComponents];
    boolean SolidForms = false;

    for (int k = 0; k < numbComponents; k++) {
      // logger.info("Cheking all the components " + k);
      if (system.getPhase(0).getComponent(k).doSolidCheck()) {
        SolidForms = true;
        FCompNames[k] = system.getPhase(0).getComponent(k).getComponentName();
        if (noFreezeFlash) {
          // system.setTemperature(trpTemp - 0.8);
          logger.info("Starting at Triple point temperature "
              + system.getPhase(0).getComponent(k).getComponentName());
        }

        funkOld = 0.0;
        newTemp = 0.0;
        iterations = 0;
        funk = 0.0;
        // int oldPhaseType = 0;
        maxNumberOfIterations = 100;
        do {
          iterations++;
          // oldPhaseType = system.getPhase(0).getType();
          ops.TPflash(false);
          SolidFugCoeff = system.getPhase(PhaseType.SOLID).getComponent(k)
              .fugcoef(system.getPhase(PhaseType.SOLID));
          funk = system.getPhase(0).getComponent(k).getz();
          for (int i = 0; i < system.getNumberOfPhases(); i++) {
            funk -= system.getPhase(i).getBeta() * SolidFugCoeff
                / system.getPhase(i).getComponent(k).getFugacityCoefficient();
          }
          logger.info("funk " + funk);
          if (iterations > 1) { // && oldPhaseType == system.getPhase(0).getType()) {
            deriv = (funk - funkOld) / (system.getTemperature() - oldTemperature);
          } else {
            deriv = funk * 100.0;
          }
          if (Math.abs(funk / deriv) > 10.0) {
            // deriv = Math.signum(deriv) * Math.abs(funk) * (10.0 / (1.0 *
            // iterations));
          }

          logger.info("phase type " + system.getPhase(0).getType());
          newTemp = system.getTemperature() - 0.9 * funk / deriv;
          logger.info("temperature " + system.getTemperature());
          oldTemperature = system.getTemperature();
          funkOld = funk;
          system.setTemperature(newTemp);
        } while (Math.abs(funk) >= 1e-12 && iterations < maxNumberOfIterations);
        FCompTemp[k] = newTemp;
        // logger.info("iterations " + iterations);

        if (system.getTemperature() < minTemperature) {
          minTemperature = oldTemperature;
        }
        if (system.getTemperature() > maxTemperature) {
          maxTemperature = oldTemperature;
        }
      } // end if
    } // end for lokke

    if (SolidForms) {
      system.setTemperature(maxTemperature);
    }
    system.init(1);
  }

  /**
   * <p>
   * printToFile.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param FCompNames an array of {@link java.lang.String} objects
   * @param FCompTemp an array of type double
   */
  public void printToFile(String name, String[] FCompNames, double[] FCompTemp) {
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
            + java.lang.Double.toString(system.getPhases()[0].getComponent(k).getz()) + ","
            + Niterations);
        pr_writer.flush();
      }
    } catch (SecurityException ex) {
      logger.error("writeFile: caught security exception");
    } catch (IOException ioe) {
      logger.error("writeFile: caught i/o exception");
    }
  }
}
