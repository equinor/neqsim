package neqsim.process.equipment.mixer;

import java.util.UUID;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * StaticPhaseMixer class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class StaticPhaseMixer extends StaticMixer {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for StaticPhaseMixer.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public StaticPhaseMixer(String name) {
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
        System.out.println("adding: " + componentName);
        int numberOfPhases = streams.get(k).getThermoSystem().getNumberOfPhases();
        double[] moles = new double[numberOfPhases];
        PhaseType[] phaseType = new PhaseType[numberOfPhases];

        // her maa man egentlig sjekke at phase typen er den samme !!! antar at begge er
        // to fase elle gass - tofase
        for (int p = 0; p < numberOfPhases; p++) {
          moles[p] = streams.get(k).getThermoSystem().getPhase(p).getComponent(i)
              .getNumberOfMolesInPhase();
          phaseType[p] = streams.get(k).getThermoSystem().getPhase(p).getType();
        }
        if (k == 1) {
          phaseType[0] = PhaseType.LIQUID;
          mixedStream.getThermoSystem().getPhase(1)
              .setTemperature(streams.get(k).getThermoSystem().getTemperature());
        }

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
          System.out.println("adding moles starting....");
          for (int p = 0; p < numberOfPhases; p++) {
            if (phaseType[p] == PhaseType.LIQUID) {
              System.out.println("adding liq");
              mixedStream.getThermoSystem().addComponent(index, moles[p], 1);
            } else if (phaseType[p] == PhaseType.GAS) {
              System.out.println("adding gas");
              mixedStream.getThermoSystem().addComponent(index, moles[p], 0);
            } else {
              System.out.println("not here....");
            }
          }
          System.out.println("adding moles finished");
        } else {
          System.out.println("ikke gaa hit");
          for (int p = 0; p < numberOfPhases; p++) {
            mixedStream.getThermoSystem().addComponent(compName, moles[p], p);
          }
        }
      }
    }
    mixedStream.getThermoSystem().init_x_y();
    mixedStream.getThermoSystem().initBeta();
    mixedStream.getThermoSystem().init(2);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().init(3);
    }

    mixedStream.setThermoSystem((streams.get(0).getThermoSystem().clone()));
    mixedStream.getThermoSystem().init(0);
    mixedStream.getThermoSystem().setBeta(1, 1e-10);
    mixedStream.getThermoSystem().init(2);
    mixedStream.getThermoSystem().reInitPhaseType();

    mixStream();

    mixedStream.getThermoSystem().initProperties();
    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }
}
