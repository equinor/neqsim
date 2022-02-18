/**
 * 
 */
package neqsim.processSimulation.processEquipment.compressor;
import java.util.ArrayList;

import neqsim.thermo.system.SystemInterface;
/**
 * @author ESOL
 *
 */
public class CompresorPropertyProfile{

	private ArrayList<SystemInterface> fluid = new ArrayList<SystemInterface>();
	private boolean isActive = false;
	
	public CompresorPropertyProfile() {
		
	}

	public void addFluid(SystemInterface inputFLuid) {
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
