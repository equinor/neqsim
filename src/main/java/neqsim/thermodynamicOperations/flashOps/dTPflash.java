/*
 * dTPflash.java
 *
 * Created on 2. oktober 2000, 22:26
 */

package neqsim.thermodynamicOperations.flashOps;



import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * dTPflash class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class dTPflash extends TPflash {
  private static final long serialVersionUID = 1000;
  

  String[] flashComp = null;

  /**
   * <p>
   * Constructor for dTPflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param comps an array of {@link java.lang.String} objects
   */
  public dTPflash(SystemInterface system, String[] comps) {
    this.system = system;
    this.flashComp = comps;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    iterations = 0;
    double diff = 0.0;
    // double fracdiff = 0.0;

    // system.setBeta(0.5);
    do {
      diff = 0.0;
      // fracdiff = 0.0;
      iterations++;
      system.init(1);
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        // boolean hasgot = false;
        for (int j = 0; j < flashComp.length; j++) {
          if (flashComp[j].equals(system.getPhase(0).getComponent(i).getName())) {
            diff += Math.abs((system.getPhase(1).getComponent(i).getx()
                * system.getPhase(1).getComponent(i).getFugacityCoefficient()
                * system.getPhase(1).getPressure())
                - (system.getPhase(0).getComponent(i).getx()
                    * system.getPhase(0).getComponent(i).getFugacityCoefficient()
                    * system.getPhase(0).getPressure()));
            system.getPhase(1).getComponent(i).setx(system.getPhase(1).getComponent(i).getx()
                * (system.getPhase(0).getComponent(i).getx()
                        * system.getPhase(0).getComponent(i).getFugacityCoefficient()
                        * system.getPhase(0).getPressure())
                    / (system.getPhase(1).getComponent(i).getx()
                        * system.getPhase(1).getComponent(i).getFugacityCoefficient()
                        * system.getPhase(1).getPressure()));

            // fracdiff += system.getPhase(1).getComponent(i).getz() -
            // system.getPhase(1).getComponent(i).getx();

            // hasgot = true;
            // 
          }
        }
        // if(!hasgot) system.getPhase(1).getComponent(i).setx(1e-16);
      }

      // system.setBeta(0.5+fracdiff);

      system.getPhase(1).normalize();
      
    } while (diff > 1e-10 && iterations < 1000);

    if (diff > 1e-10) {
      
    }
  }
}
