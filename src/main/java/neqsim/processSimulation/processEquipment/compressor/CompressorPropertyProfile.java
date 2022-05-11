package neqsim.processSimulation.processEquipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import neqsim.thermo.system.SystemInterface;

/**
 * @author ESOL
 *
 */
public class CompressorPropertyProfile implements Serializable {
  private static final long serialVersionUID = 1L;
  private ArrayList<SystemInterface> fluid = new ArrayList<SystemInterface>();
  private boolean isActive = false;

  public CompressorPropertyProfile() {}

  public void addFluid(SystemInterface inputFLuid) {
    inputFLuid.initPhysicalProperties();
    fluid.add(inputFLuid);
  }

  public boolean isActive() {
    return isActive;
  }

  public void setActive(boolean isActive) {
    this.isActive = isActive;
    fluid.clear();
  }

  public ArrayList<SystemInterface> getFluid() {
    return fluid;
  }

  public void setFluid(ArrayList<SystemInterface> fluid) {
    this.fluid = fluid;
  }
}
