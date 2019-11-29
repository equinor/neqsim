/*
 * InterfaceProperties.java
 *
 * Created on 13. august 2001, 13:12
 */
package neqsim.physicalProperties.interfaceProperties;

import org.apache.log4j.Logger;

import neqsim.physicalProperties.interfaceProperties.solidAdsorption.AdsorptionInterface;
import neqsim.physicalProperties.interfaceProperties.solidAdsorption.PotentialTheoryAdsorption;
import neqsim.physicalProperties.interfaceProperties.surfaceTension.FirozabadiRamleyInterfaceTension;
import neqsim.physicalProperties.interfaceProperties.surfaceTension.GTSurfaceTension;
import neqsim.physicalProperties.interfaceProperties.surfaceTension.GTSurfaceTensionSimple;
import neqsim.physicalProperties.interfaceProperties.surfaceTension.LGTSurfaceTension;
import neqsim.physicalProperties.interfaceProperties.surfaceTension.ParachorSurfaceTension;
import neqsim.physicalProperties.interfaceProperties.surfaceTension.SurfaceTensionInterface;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author esol
 * @version
 */
public class InterfaceProperties implements InterphasePropertiesInterface, java.io.Serializable, Cloneable {

	private static final long serialVersionUID = 1000;

	SystemInterface system;
	SurfaceTensionInterface gasLiquidSurfaceTensionCalc = null;
	SurfaceTensionInterface gasAqueousSurfaceTensionCalc = null;
	SurfaceTensionInterface liquidLiquidSurfaceTensionCalc = null;
	AdsorptionInterface[] adsorptionCalc;
	double[] surfaceTension;
	int numberOfInterfaces = 1;
	private int interfacialTensionModel = 0;
	static Logger logger = Logger.getLogger(InterfaceProperties.class);
	/**
	 * Creates new InterfaceProperties
	 */
	public InterfaceProperties() {
	}

	public InterfaceProperties(SystemInterface system) {
		numberOfInterfaces = system.getNumberOfPhases() - 1;
		this.system = system;
		// gasLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
		// gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
		// liquidLiquidSurfaceTensionCalc = new
		// FirozabadiRamleyInterfaceTension(system);
	}

	public Object clone() {
		InterfaceProperties clonedSystem = null;
		try {
			//clonedSystem = (InterfaceProperties) suclone();
			// clonedSystem.chemicalReactionOperations = (ChemicalReactionOperations)
			// chemicalReactionOperations.clone();
		} catch (Exception e) {
			logger.error("Cloning failed.", e);
		}
		//clonedSystem.system = system;
		return clonedSystem;
	}
	
	public void init(SystemInterface system) {
		this.system = system;
		init();
	}

	public void init() {
		numberOfInterfaces = system.getNumberOfPhases() - 1;
		surfaceTension = new double[numberOfInterfaces + 1];
		if (gasLiquidSurfaceTensionCalc == null || gasAqueousSurfaceTensionCalc == null
				|| liquidLiquidSurfaceTensionCalc == null) {
			setInterfacialTensionModel(interfacialTensionModel);
		}
		// surfaceTensionCalc[i] = new LGTSurfaceTension(system);
		// surfaceTension[i] = surfaceTensionCalc[i].calcSurfaceTension(i, i
		// + 1);
	}

	public void initAdsorption() {
		adsorptionCalc = new AdsorptionInterface[system.getNumberOfPhases()];

		for (int i = 0; i < system.getNumberOfPhases(); i++) {
			adsorptionCalc[i] = new PotentialTheoryAdsorption(system);
		}
	}

	public void setSolidAdsorbentMaterial(String material) {
		for (int i = 0; i < system.getNumberOfPhases(); i++) {
			adsorptionCalc[i].setSolidMaterial(material);
		}
	}

	public void calcAdsorption() {
		for (int i = 0; i < system.getNumberOfPhases(); i++) {
			adsorptionCalc[i].calcAdorption(i);
		}
	}

	/*
	 * public double getSurfaceTension(int numb) { if (numb >= numberOfInterfaces) {
	 * return 0.0; } else { return surfaceTension[numb]; } }
	 */
	public double getSurfaceTension(int numb1, int numb2) {

		if (system.getPhase(numb1).getPhaseTypeName().equals("gas")
				&& system.getPhase(numb2).getPhaseTypeName().equals("oil")) {
			return gasLiquidSurfaceTensionCalc.calcSurfaceTension(numb1, numb2);
		} else if (system.getPhase(numb1).getPhaseTypeName().equals("gas")
				&& system.getPhase(numb2).getPhaseTypeName().equals("aqueous")) {
			return gasAqueousSurfaceTensionCalc.calcSurfaceTension(numb1, numb2);
		} else {
			return liquidLiquidSurfaceTensionCalc.calcSurfaceTension(numb1, numb2);
		}
	}

	//TODO add unit conversion implementation to interfacial tension
	public double getSurfaceTension(int numb1, int numb2, String unit) {
		double val = getSurfaceTension(numb1, numb2);
		///...conversion methods
		return val;
	}

	public SurfaceTensionInterface getSurfaceTensionModel(int i) {
		if (system.getPhase(i).getPhaseTypeName().equals("gas")) {
			return gasLiquidSurfaceTensionCalc;
		} else {
			return gasLiquidSurfaceTensionCalc;
		}
	}

	/**
	 * @return the interfacialTensionModel
	 */
	public int getInterfacialTensionModel() {
		return interfacialTensionModel;
	}

	/**
	 * @param interfacialTensionModel the interfacialTensionModel to set
	 */
	public void setInterfacialTensionModel(String phase1, String phase2, String model) {
		SurfaceTensionInterface surfTensModel = null;
		if ("Linear Gradient Theory".equals(model)) {
			surfTensModel = new LGTSurfaceTension(system);
		} else if ("Simple Gradient Theory".equals(model)) {
			surfTensModel = new GTSurfaceTensionSimple(system);
		} else if ("Full Gradient Theory".equals(model)) {
			surfTensModel = new GTSurfaceTension(system);
		} else if ("Firozabadi Ramley".equals(model)) {
			surfTensModel = new FirozabadiRamleyInterfaceTension(system);
		} else if ("Parachor".equals(model)) {
			surfTensModel = new ParachorSurfaceTension(system);
		} else {
			surfTensModel = new ParachorSurfaceTension(system);
		}

		if (phase1.equals("gas") && phase2.equals("oil")) {
			gasLiquidSurfaceTensionCalc = surfTensModel;
		} else if (phase1.equals("gas") && phase2.equals("aqueous")) {
			gasAqueousSurfaceTensionCalc = surfTensModel;
		} else if (phase1.equals("oil") && phase2.equals("aqueous")) {
			liquidLiquidSurfaceTensionCalc = surfTensModel;
		}

	}

	public void setInterfacialTensionModel(int interfacialTensionModel) {
		this.interfacialTensionModel = interfacialTensionModel;
		if (interfacialTensionModel == 0) {
			gasLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
			gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
			liquidLiquidSurfaceTensionCalc = new FirozabadiRamleyInterfaceTension(system);
		} else if (interfacialTensionModel == 1) {
			gasLiquidSurfaceTensionCalc = new GTSurfaceTension(system); // Sintef method
			// liquidLiquidSurfaceTensionCalc = new GTSurfaceTension(system); //Sintef
			// method
			gasAqueousSurfaceTensionCalc = new GTSurfaceTensionSimple(system);
			// gasLiquidSurfaceTensionCalc = new GTSurfaceTensionSimple(system);
			liquidLiquidSurfaceTensionCalc = new GTSurfaceTensionSimple(system);
		} else if (interfacialTensionModel == 2) {
			gasLiquidSurfaceTensionCalc = new LGTSurfaceTension(system);
			liquidLiquidSurfaceTensionCalc = new LGTSurfaceTension(system);
			gasAqueousSurfaceTensionCalc = new LGTSurfaceTension(system);
		} else if (interfacialTensionModel == 3) {
			gasLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
			gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
			liquidLiquidSurfaceTensionCalc = new FirozabadiRamleyInterfaceTension(system);
		} else if (interfacialTensionModel == 4) {
			gasLiquidSurfaceTensionCalc = new GTSurfaceTensionSimple(system);
			liquidLiquidSurfaceTensionCalc = new LGTSurfaceTension(system);
			gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
		} else if (interfacialTensionModel == 5) {
			gasLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
			gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
			liquidLiquidSurfaceTensionCalc = new FirozabadiRamleyInterfaceTension(system);
		} else {
			gasLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
			gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
			liquidLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
		}

	}
}
