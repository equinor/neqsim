package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * freezingPointTemperatureFlashTR class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class FreezingPointTemperatureFlashTR extends ConstantDutyTemperatureFlash
    implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FreezingPointTemperatureFlashTR.class);

  public boolean noFreezeFlash = true;
  public int Niterations = 0;
  public String[] FCompNames = new String[10];
  public double[] FCompTemp = new double[10];
  public int compnr;
  public String name = "Frz";
  public boolean CCequation = true;

  /**
   * <p>
   * Constructor for freezingPointTemperatureFlashTR.
   * </p>
   *
   * @param Freeze a boolean
   */
  public FreezingPointTemperatureFlashTR(boolean Freeze) {
    noFreezeFlash = Freeze;
  }

  /**
   * <p>
   * Constructor for freezingPointTemperatureFlashTR.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FreezingPointTemperatureFlashTR(SystemInterface system) {
    super(system);
  }

  /**
   * <p>
   * Constructor for freezingPointTemperatureFlashTR.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Freeze a boolean
   */
  public FreezingPointTemperatureFlashTR(SystemInterface system, boolean Freeze) {
    super(system);
    noFreezeFlash = Freeze;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    int iterations = 0;
    // int maxNumberOfIterations = 15000;
    // double yold = 0, ytotal = 1;
    double deriv = 0;
    double funk = 0;
    double funkOld = 0;
    // double Testfunk = 0.00000000;
    double maxTemperature = 0;
    double minTemperature = 1e6;
    double oldTemperature = 0.0;
    double newTemp = 0.0;
    double SolidFug = 0.0;
    double temp = 0.0;
    double pres = 0.0;
    double Pvapsolid = 0.0;
    double SolVapFugCoeff = 0.0;
    double dfugdt = 0.0;
    double solvol = 0.0;

    double soldens = 0.0;
    double trpTemp = 0.0;
    // for(int k=0;k<system.getPhases()[0].getNumberOfComponents();k++){
    for (int k = 0; k < 1; k++) {
      // if(system.getPhase(0).getComponent(k).fugcoef(system.getPhase(0))<9e4){//&&
      // system.getPhase(PhaseType.SOLID).getComponent(k).doSolidCheck()){ // solidCheck variablen
      // er satt naar man kaller setSolidCheck Funksjonen som maa kjores faer du
      // kjorer scriptet.
      FCompNames[k] = system.getPhase(0).getComponent(k).getComponentName();
      if (system.getPhase(0).getComponent(k).getHsub() < 1000) {
        CCequation = false;
      }

      if (noFreezeFlash) {
        system.setTemperature(system.getPhases()[0].getComponent(k).getTriplePointTemperature());
        logger.info("Starting at Triple point temperature "
            + system.getPhase(0).getComponent(k).getComponentName());
      } else {
        system.setTemperature(system.getTemperature());
        logger.info("starting at Temperature  " + system.getTemperature());
      }

      // init reference system for vapor fugacity
      SystemInterface testSystem2 =
          new SystemSrkSchwartzentruberEos(system.getTemperature(), system.getPressure());
      ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2);
      testSystem2.addComponent(system.getPhase(0).getComponent(k).getComponentName(), 1);

      oldTemperature = 0.0;
      funkOld = 0.0;
      newTemp = 0.0;
      system.init(0);
      system.init(1);
      iterations = 0;
      trpTemp = system.getPhase(0).getComponent(k).getTriplePointTemperature();

      do {
        funk = 0.0;
        deriv = 0.0;
        iterations++;
        newTemp = 0.0;
        temp = system.getTemperature();
        pres = system.getPressure();

        if (temp > trpTemp + 1) {
          temp = trpTemp;
        }
        if (CCequation) {
          Pvapsolid = system.getPhase(0).getComponent(k).getCCsolidVaporPressure(temp);
          dfugdt = Math.log((system.getPhase(0).getComponent(k).getCCsolidVaporPressuredT(temp)
              * Math.exp(solvol / (R * temp) * (pres - Pvapsolid))) / pres);
        } else {
          Pvapsolid = system.getPhase(0).getComponent(k).getSolidVaporPressure(temp);
          dfugdt = Math.log((system.getPhase(0).getComponent(k).getSolidVaporPressuredT(temp)
              * Math.exp(solvol / (R * temp) * (pres - Pvapsolid))) / pres);
        }
        // Pvapsolid = system.getPhase(0).getComponent(k).getCCsolidVaporPressure(temp);
        // legge in sjekk paa om soldens eksisterer i databasen.

        soldens = system.getPhase(0).getComponent(k).getPureComponentSolidDensity(temp) * 1000;

        logger.info("Solid density" + soldens);

        if (soldens > 2000) {
          soldens = 1000;
        }
        solvol = 1.0 / soldens * system.getPhase(0).getComponent(k).getMolarMass();

        testSystem2.setTemperature(temp);
        testSystem2.setPressure(Pvapsolid);
        ops.TPflash();
        testOps2.TPflash();
        SolidFug = Pvapsolid / pres * Math.exp(solvol / (R * temp) * (pres - Pvapsolid));
        SolVapFugCoeff = testSystem2.getPhase(0).getComponent(0).getFugacityCoefficient();

        funk = system.getPhases()[0].getComponent(k).getz();

        for (int i = 0; i < system.getNumberOfPhases(); i++) {
          funk -= system.getPhases()[i].getBeta() * SolidFug * SolVapFugCoeff
              / system.getPhases()[i].getComponent(k).getFugacityCoefficient();
          deriv -= 0.01 * system.getPhases()[i].getBeta() * (SolidFug * SolVapFugCoeff
              * Math.exp(system.getPhases()[i].getComponent(k).getdfugdt()) * -1.0
              / Math.pow(system.getPhases()[i].getComponent(k).getFugacityCoefficient(), 2.0)
              + Math.exp(dfugdt) / system.getPhases()[i].getComponent(k).getFugacityCoefficient());
        }
        if (iterations >= 2) {
          deriv = -(funk - funkOld) / (system.getTemperature() - oldTemperature);
        } else {
          deriv = -funk;
        }
        oldTemperature = system.getTemperature();
        funkOld = funk;

        if (oldTemperature < trpTemp + 1) {
          newTemp =
              system.getTemperature() + 0.5 * (iterations / (10.0 + iterations)) * funk / deriv;
        } else {
          newTemp = system.getTemperature() + 0.5 * (iterations / (10.0 + iterations)) * funk;
        }
        logger.info("newTEmp  " + newTemp);
        if (newTemp > (trpTemp + 5)) {
          system.setTemperature(
              system.getPhases()[0].getComponent(k).getTriplePointTemperature() + 0.4);
        } else if (newTemp < 1) {
          system.setTemperature(oldTemperature + 2);
          // } else if(newTemp=="NaN") { system.setTemperature(oldTemperature*0.9374);
        } else {
          system.setTemperature(newTemp);
        }
        logger.info("funk " + funk);
        logger.info("temperature " + system.getTemperature());
      } while ((Math.abs(funk) >= 0.001 && iterations < 100));
      FCompTemp[k] = system.getTemperature();
      logger.info("iterations " + iterations);
      Niterations = iterations;
      // logger.info("funk " + funk + k + " " + system.getTemperature());
      if (system.getTemperature() < minTemperature) {
        minTemperature = system.getTemperature();
      }
      if (system.getTemperature() > maxTemperature) {
        maxTemperature = system.getTemperature();
        // Testfunk = funk;
      }
    }
    // }
    // this.printToFile(name);
    system.setTemperature(maxTemperature);
    // system.setSolidPhaseCheck(true);
    // ops.TPflash();
    // logger.info("final funk:"+ Testfunk);
    // system.display();
    // logger.info("min freezing temp " + minTemperature);
    // logger.info("max freezing temp " + maxTemperature);
  }

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
            + java.lang.Double.toString(system.getPhases()[0].getComponent(k).getz()) + ","
            + Niterations);
        pr_writer.flush();
      }
    } catch (SecurityException ex) {
      logger.info("writeFile: caught security exception");
    } catch (IOException ioe) {
      logger.info("writeFile: caught i/o exception");
    }
  }

  /**
   * <p>
   * getNiterations.
   * </p>
   *
   * @return a int
   */
  public int getNiterations() {
    return Niterations;
  }
}
