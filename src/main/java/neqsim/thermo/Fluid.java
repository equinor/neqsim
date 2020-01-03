/**
 * 
 */
package neqsim.thermo;

import org.apache.log4j.Logger;

/**
 * @author esol
 *
 */
public class Fluid {
	 static Logger logger = Logger.getLogger(Fluid.class);
	 static neqsim.thermo.system.SystemInterface fluid = null;
	 private static boolean hasWater = false;
	 private static boolean autoSelectModel = false;
	 private static String thermoModel = "srk";
	 private static String thermoMixingRule = "classic";
	
	 private static void setThermoModel() {
		 if(thermoModel.equals("srk")) {
			 fluid = new neqsim.thermo.system.SystemSrkEos();
		 }
		 else if(thermoModel.equals("pr")) {
			 fluid = new neqsim.thermo.system.SystemPrEos();
		 }
		 else if(thermoModel.equals("cpa")) {
			 fluid = new neqsim.thermo.system.SystemSrkCPAstatoil();
		 }
		 else{
			 fluid = new neqsim.thermo.system.SystemSrkEos();
		 }
	 }
	 
	 private static void setMixingRule() {
			 fluid.setMixingRule(getThermoMixingRule());
	 }
	 
	 public static neqsim.thermo.system.SystemInterface create2(String[] componentNames, double[] flowrate, String unit) {
		 setThermoModel();
		 createFluid(componentNames, flowrate, unit);
		 if(isHasWater()==true) fluid.addComponent("water", 0.1);
			fluid.createDatabase(true);
			setMixingRule();
			if(isHasWater()) {
				fluid.setMultiPhaseCheck(true);
			}
			if(isAutoSelectModel()) {
				fluid = fluid.autoSelectModel();
			}
			fluid.init(0);
			return fluid;
	 }
	 
	public static neqsim.thermo.system.SystemInterface create(String fluidType) {
		String[] compNames = null;
		double[] flowrate = null;
		setThermoModel();
		if(fluidType.equals("dry gas")) {
			compNames = new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane"};
			flowrate = new double[]{0.01, 0.02, 0.82, 0.11, 0.05, 0.01, 0.012};
			createFluid(compNames, flowrate, "mole/sec");	
		}
		else if(fluidType.equals("rich gas")) {
			compNames = new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10"};
			flowrate = new double[]{0.01, 0.02, 0.82, 0.11, 0.05, 0.01, 0.012, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01};
			createFluid(compNames, flowrate, "mole/sec");
		}
		else if(fluidType.equals("air")) {
			compNames = new String[] {"nitrogen", "oxygen"};
			flowrate = new double[]{0.78, 0.22};
			createFluid(compNames, flowrate, "mole/sec");
		}
		else if(fluidType.equals("gas condensate")) {
			compNames = new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10"};
			flowrate = new double[]{0.01, 0.02, 0.32, 0.05, 0.03, 0.01, 0.012, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01};
			createFluid(compNames, flowrate, "mole/sec");
			String[] charNames = new String[] {"C10-C15", "C16-C19", "C20-C30", "C31-C50"};
			double[] charFlowrate = new double[]{0.1, 0.08, 0.05, 0.01};
			double[] molarMass = new double[]{0.20, 0.3, 0.36, 0.4};
			double[] density = new double[]{700.0e-3, 810.0e-3, 880.0e-3, 920.0e-3};
			addCharacterized(charNames,charFlowrate,molarMass,  density);
		}
		else if(fluidType.equals("light oil")) {
			compNames = new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10"};
			flowrate = new double[]{0.01, 0.02, 0.52, 0.11, 0.05, 0.01, 0.012, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01};
			createFluid(compNames, flowrate, "mole/sec");
			String[] charNames = new String[] {"C10-C15", "C16-C19", "C20-C30", "C31-C50"};
			double[] charFlowrate = new double[]{0.2, 0.1, 0.05, 0.01};
			double[] molarMass = new double[]{0.20, 0.3, 0.36, 0.4};
			double[] density = new double[]{700.0e-3, 810.0e-3, 880.0e-3, 920.0e-3};
			addCharacterized(charNames,charFlowrate,molarMass,  density);
		}
		else if(fluidType.equals("black oil")) {
			compNames = new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane", "n-hexane"};
			flowrate = new double[]{0.01, 0.02, 0.22, 0.11, 0.05, 0.01, 0.012, 0.01, 0.01, 0.01};
			createFluid(compNames, flowrate, "mole/sec");
			String[] charNames = new String[] {"C10-C15", "C16-C19", "C20-C30", "C31-C50", "C51-C80"};
			double[] charFlowrate = new double[]{0.2, 0.1, 0.1, 0.05, 0.01};
			double[] molarMass = new double[]{0.20, 0.25, 0.3, 0.36, 0.4};
			double[] density = new double[]{700.0e-3, 750.0e-3, 810.0e-3, 880.0e-3, 920.0e-3};
			addCharacterized(charNames,charFlowrate,molarMass,  density);
		}
		else if(fluidType.equals("heavy oil")) {
			compNames = new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane", "n-hexane"};
			flowrate = new double[]{0.01, 0.01, 0.12, 0.11, 0.05, 0.01, 0.012, 0.01, 0.01, 0.01};
			createFluid(compNames, flowrate, "mole/sec");
			String[] charNames = new String[] {"C10-C15", "C16-C19", "C20-C30", "C31-C50", "C51-C80"};
			double[] charFlowrate = new double[]{0.2, 0.2, 0.2, 0.1, 0.1};
			double[] molarMass = new double[]{0.20, 0.25, 0.3, 0.36, 0.4};
			double[] density = new double[]{700.0e-3, 750.0e-3, 810.0e-3, 880.0e-3, 920.0e-3};
			addCharacterized(charNames,charFlowrate,molarMass,  density);
		}
		else if(neqsim.util.database.NeqSimDataBase.hasComponent(fluidType)) {
			compNames = new String[] {fluidType};
			flowrate = new double[]{1.0};
			createFluid(compNames, flowrate, "mole/sec");	
		}
		else {
			return null;
		}
		
		if(isHasWater()==true) fluid.addComponent("water", 0.1);
		fluid.createDatabase(true);
		setMixingRule();
		if(isHasWater()) {
			fluid.setMultiPhaseCheck(true);
		}
		if(isAutoSelectModel()) {
			fluid = fluid.autoSelectModel();
		}
		fluid.init(0);
		return fluid;
	}
	
	public static void addCharacterized(String[] charNames,double[] charFlowrate,double[] molarMass, double[] relativedensity) {
		if(charNames.length!=charFlowrate.length) {
			logger.error("component names and mole fractions need to be same length...");
		}
		
		for(int i=0;i<charNames.length;i++){
			fluid.addTBPfraction(charNames[i], charFlowrate[i], molarMass[i], relativedensity[i]);
		}
	}
	
	public static neqsim.thermo.system.SystemInterface addOilFractions(String[] charNames,double[] charFlowrate,double[] molarMass, double[] relativedensity) {
		if(charNames.length!=charFlowrate.length) {
			logger.error("component names and mole fractions need to be same length...");
		}
		
		for(int i=0;i<charNames.length;i++){
			fluid.addTBPfraction(charNames[i], charFlowrate[i], molarMass[i], relativedensity[i]);
		}
		fluid.createDatabase(true);
		setMixingRule();
		fluid.init(0);
		return fluid;
	}
	
	public static neqsim.thermo.system.SystemInterface createFluid(String[] componentNames, double[] flowrate, String unit) {
			
		if(componentNames.length!=flowrate.length) {
			logger.error("component names and mole fractions need to be same length...");
		}
		
		for(int i=0;i<componentNames.length;i++){
			fluid.addComponent(componentNames[i], flowrate[i], unit);
		}
		
		
		return fluid;
	}
	
	public static void addComponment(String name) {
		fluid.addComponent(name, 1.0);
		fluid.createDatabase(true);
		fluid.setMixingRule(2);
		
		if(isHasWater()) {
			fluid.setMultiPhaseCheck(true);
		}
	}
	
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		neqsim.thermo.Fluid.setHasWater(true);
		neqsim.thermo.system.SystemInterface fluid = neqsim.thermo.Fluid.create("light oil");
		fluid.display();
		
		neqsim.thermo.system.SystemInterface fluid2 = neqsim.thermo.Fluid.create("dry gas");
		fluid2.display();
		fluid2.getNumberOfComponents();
	}

	public static boolean isHasWater() {
		return hasWater;
	}

	public static void setHasWater(boolean hasWater) {
		Fluid.hasWater = hasWater;
	}

	public static boolean isAutoSelectModel() {
		return autoSelectModel;
	}

	public static void setAutoSelectModel(boolean autoSelectModel) {
		Fluid.autoSelectModel = autoSelectModel;
	}

	public static String getThermoModel() {
		return thermoModel;
	}

	public static void setThermoModel(String thermoModel) {
		Fluid.thermoModel = thermoModel;
	}

	public static String getThermoMixingRule() {
		return thermoMixingRule;
	}

	public static void setThermoMixingRule(String thermoMixingRule) {
		Fluid.thermoMixingRule = thermoMixingRule;
	}

}
