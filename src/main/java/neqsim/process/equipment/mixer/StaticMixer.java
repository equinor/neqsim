/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */

package neqsim.process.equipment.mixer;

import java.util.UUID;
import neqsim.thermo.system.SystemSoreideWhitson;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * StaticMixer class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class StaticMixer extends Mixer {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for StaticMixer.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public StaticMixer(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void mixStream() {
    int index = 0;
    String compName = new String();
    for (int k = 1; k < streams.size(); k++) {
      for (int i = 0; i < streams.get(k).getThermoSystem().getPhases()[0]
          .getNumberOfComponents(); i++) {
        boolean gotComponent = false;
        String componentName =
            streams.get(k).getThermoSystem().getPhases()[0].getComponent(i).getName();
        // System.out.println("adding: " + componentName);
        double moles =
            streams.get(k).getThermoSystem().getPhases()[0].getComponent(i).getNumberOfmoles();
        // System.out.println("moles: " + moles + " " +
        // mixedStream.getThermoSystem().getPhases()[0].getNumberOfComponents());
        for (int p = 0; p < mixedStream.getThermoSystem().getPhases()[0]
            .getNumberOfComponents(); p++) {
          if (mixedStream.getThermoSystem().getPhases()[0].getComponent(p).getName()
              .equals(componentName)) {
            gotComponent = true;
            index = streams.get(0).getThermoSystem().getPhases()[0].getComponent(p)
                .getComponentNumber();
            compName =
                streams.get(0).getThermoSystem().getPhases()[0].getComponent(p).getComponentName();
          }
        }

        if (gotComponent) {
          // System.out.println("adding moles starting....");
          mixedStream.getThermoSystem().addComponent(index, moles, 0);
          // mixedStream.getThermoSystem().init_x_y();
          // System.out.println("adding moles finished");
        } else {
          // System.out.println("ikke gaa hit");
          mixedStream.getThermoSystem().addComponent(compName, moles, 0);
        }
      }
    }

  }

  /** {@inheritDoc} */
  @Override
  public double guessTemperature() {
    double gtemp = 0;
    for (int k = 0; k < streams.size(); k++) {
      gtemp += streams.get(k).getThermoSystem().getTemperature()
          * streams.get(k).getThermoSystem().getNumberOfMoles()
          / mixedStream.getThermoSystem().getNumberOfMoles();
    }
    return gtemp;
  }

  /** {@inheritDoc} */
  @Override
  public double calcMixStreamEnthalpy() {
    double enthalpy = 0;
    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().init(3);
      enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
      System.out.println("total enthalpy k : " + streams.get(k).getThermoSystem().getEnthalpy());
    }
    System.out.println("total enthalpy of streams: " + enthalpy);
    return enthalpy;
  }


  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double enthalpy = 0.0;
    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().init(3);
      enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
    }
    mixedStream.setThermoSystem((streams.get(0).getThermoSystem().clone()));
    mixedStream.getThermoSystem().setNumberOfPhases(2);
    mixedStream.getThermoSystem().reInitPhaseType();
    mixStream();
    // System.out.println("filan temp " + mixedStream.getTemperature());
   
    ThermodynamicOperations testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
    try {
      if (Double.isNaN(enthalpy)) {
        logger.error("error in StaticMixer calc0 - enthalpy NaN");
        testOps.TPflash();
      } else {
        testOps.PHflash(enthalpy, 0);
      }
      // System.out.println("enthalp ok " + enthalpy);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // System.out.println("temp " + mixedStream.getThermoSystem().getTemperature());
     mixedStream.getThermoSystem().initProperties();
     if (mixedStream.getFluid().getClass().getName().equals("neqsim.thermo.system.SystemSoreideWhitson")) {
          ((SystemSoreideWhitson) mixedStream.getFluid()).setSalinity(getMixedSalinity(), "mole/sec");
          mixedStream.run();
    } 

    setCalculationIdentifier(id);
    
  }
}
