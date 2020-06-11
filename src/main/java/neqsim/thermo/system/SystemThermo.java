/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package neqsim.thermo.system;

import java.awt.*;
import java.io.*;
import java.sql.ResultSet;
import java.text.*;
import java.util.*;
import javax.swing.*;
import neqsim.chemicalReactions.ChemicalReactionOperations;
import neqsim.physicalProperties.interfaceProperties.InterfaceProperties;
import neqsim.physicalProperties.interfaceProperties.InterphasePropertiesInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.characterization.Characterise;
import neqsim.thermo.characterization.WaxCharacterise;
import neqsim.thermo.characterization.WaxModelInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSolid;
import neqsim.thermo.phase.PhaseSolidComplex;
import neqsim.thermo.phase.PhaseWax;
import org.apache.logging.log4j.*;

/*
 * This is the base class of the System classes. The purpose of this class is to
 * give common variables and methods to sub classes. The methods and variables
 * in this class are: Date	Method	Purpose 7/3-00	System_Thermo(double, double)
 * Constructor 7/3-00	addcomponent(String, double)	addding components from
 * text-file: "Component_Data.txt" 7/3-00	init()	initializing
 */
abstract class SystemThermo extends java.lang.Object implements SystemInterface, Cloneable, java.io.Serializable {

	private static final long serialVersionUID = 1000;// implements System_Interface{
	// Class variables

	private boolean implementedTemperatureDeriativesofFugacity = true;
	private boolean implementedPressureDeriativesofFugacity = true;
	private boolean implementedCompositionDeriativesofFugacity = true;
	protected double criticalTemperature = 0;
	protected String[][] resultTable = null;
	protected String fluidInfo = "No Information Available";
	protected String fluidName = "DefaultName", modelName = "Default";
	protected boolean numericDerivatives = false, allowPhaseShift = true;
	private boolean useTVasIndependentVariables = false;
	protected double criticalPressure = 0;
	private double totalNumberOfMoles = 0;
	protected neqsim.thermo.characterization.WaxCharacterise waxCharacterisation = null;// new WaxCharacterise(this);
	protected double[] beta = new double[6];
	protected int a, initType = 3;
	private ArrayList componentNames = new ArrayList();
	protected ArrayList resultArray1 = new ArrayList();
	protected String[] CapeOpenProperties11 = { "molecularWeight", "speedOfSound", "jouleThomsonCoefficient",
			"internalEnergy", "internalEnergy.Dtemperature", "gibbsEnergy", "helmholtzEnergy", "fugacityCoefficient",
			"logFugacityCoefficient", "logFugacityCoefficient.Dtemperature", "logFugacityCoefficient.Dpressure",
			"logFugacityCoefficient.Dmoles", "enthalpy", "enthalpy.Dmoles", "enthalpy.Dtemperature",
			"enthalpy.Dpressure", "entropy", "entropy.Dtemperature", "entropy.Dpressure", "entropy.Dmoles",
			"heatCapacityCp", "heatCapacityCv", "density", "density.Dtemperature", "density.Dpressure",
			"density.Dmoles", "volume", "volume.Dpressure", "volume.Dtemperature", "molecularWeight.Dtemperature",
			"molecularWeight.Dpressure", "molecularWeight.Dmoles", "compressibilityFactor" };
	protected String[] CapeOpenProperties10 = { "molecularWeight", "speedOfSound", "jouleThomsonCoefficient", "energy",
			"energy.Dtemperature", "gibbsFreeEnergy", "helmholtzFreeEnergy", "fugacityCoefficient",
			"logFugacityCoefficient", "logFugacityCoefficient.Dtemperature", "logFugacityCoefficient.Dpressure",
			"logFugacityCoefficient.Dmoles", "enthalpy", "enthalpy.Dmoles", "enthalpy.Dtemperature",
			"enthalpy.Dpressure", "entropy", "entropy.Dtemperature", "entropy.Dpressure", "entropy.Dmoles",
			"heatCapacity", "heatCapacityCv", "density", "density.Dtemperature", "density.Dpressure", "density.Dmoles",
			"volume", "volume.Dpressure", "volume.Dtemperature", "molecularWeight.Dtemperature",
			"molecularWeight.Dpressure", "molecularWeight.Dmoles", "compressibilityFactor" };
	protected int numberOfComponents = 0;
	protected int numberOfPhases = 2;
	public int maxNumberOfPhases = 2;
	protected int attractiveTermNumber = 0;
	protected int phase = 2;
	protected int onePhaseType = 1; // 0 - liquid 1 - gas
	protected int[] phaseType = { 1, 0, 0, 0, 0, 0 };
	protected int[] phaseIndex = { 0, 1, 2, 3, 4, 5 };
	protected ChemicalReactionOperations chemicalReactionOperations;
	private int mixingRule = 1;
	protected boolean chemicalSystem = false, solidPhaseCheck = false, multiPhaseCheck = false, hydrateCheck = false;
	protected boolean checkStability = true;
	protected PhaseInterface[] phaseArray;
	public neqsim.thermo.characterization.Characterise characterization = null;
	protected neqsim.standards.StandardInterface standard = null;
	protected InterphasePropertiesInterface interfaceProp = null;
	private boolean multiphaseWaxCheck = false;
	Object pdfDocument = null;
	private boolean forcePhaseTypes = false;
	static Logger logger = LogManager.getLogger(SystemThermo.class);

	public SystemThermo() {
		phaseArray = new PhaseInterface[6];
		characterization = new Characterise(this);
		interfaceProp = new InterfaceProperties(this);
	}

	public SystemThermo(double T, double P) {
		this();
		if (T < 0.0 || P < 0.0) {
			logger.error("Negative input temperature or pressure");
			neqsim.util.exception.InvalidInputException e = new neqsim.util.exception.InvalidInputException();
			throw new RuntimeException(e);
		}
		beta[0] = 1.0;
		beta[1] = 1.0;
		beta[2] = 1.0;
		beta[3] = 1.0;
		beta[4] = 1.0;
		beta[5] = 1.0;
	}

	public int getNumberOfComponents() {
		return getComponentNames().length;
	}

	public void clearAll() {
		setTotalNumberOfMoles(0);
		phaseType[0] = 1;
		phaseType[1] = 0;
		numberOfComponents = 0;
		numberOfPhases = 2;
		phase = 2;
		onePhaseType = 1;
		beta[0] = 1.0;
		beta[1] = 1.0;
		beta[2] = 1.0;
		beta[3] = 1.0;
		beta[4] = 1.0;
		beta[5] = 1.0;
		chemicalSystem = false;

		double oldTemp = phaseArray[0].getTemperature();
		double oldPres = phaseArray[0].getPressure();

		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			try {
				phaseArray[i] = phaseArray[i].getClass().getDeclaredConstructor().newInstance();
			} catch (Exception e) {
				logger.error("err " + e.toString());
			}
			phaseArray[i].setTemperature(oldTemp);
			phaseArray[i].setPressure(oldPres);
		}
	}

	public void resetCharacterisation() {
		int numerOfLumpedComps = characterization.getLumpingModel().getNumberOfLumpedComponents();
		characterization = new Characterise(this);
		characterization.getLumpingModel().setNumberOfLumpedComponents(numerOfLumpedComps);
	}

	public Object clone() {
		SystemThermo clonedSystem = null;
		try {
			clonedSystem = (SystemThermo) super.clone();
			// clonedSystem.chemicalReactionOperations = (ChemicalReactionOperations)
			// chemicalReactionOperations.clone();
		} catch (Exception e) {
			logger.error("Cloning failed.", e);
		}

		clonedSystem.beta = beta.clone();
		clonedSystem.attractiveTermNumber = attractiveTermNumber;
		clonedSystem.phaseType = phaseType.clone();
		clonedSystem.phaseIndex = phaseIndex.clone();
		if (interfaceProp != null) {
			// clonedSystem.interfaceProp = (InterphasePropertiesInterface)
			// interfaceProp.clone();
		}
		clonedSystem.characterization = (neqsim.thermo.characterization.Characterise) characterization.clone();
		if (clonedSystem.waxCharacterisation != null) {
			clonedSystem.waxCharacterisation = (neqsim.thermo.characterization.WaxCharacterise) waxCharacterisation
					.clone();
		}
		System.arraycopy(this.beta, 0, clonedSystem.beta, 0, beta.length);
		System.arraycopy(this.phaseType, 0, clonedSystem.phaseType, 0, phaseType.length);
		System.arraycopy(this.phaseIndex, 0, clonedSystem.phaseIndex, 0, phaseIndex.length);

		clonedSystem.phaseArray = phaseArray.clone();
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
		}
		return clonedSystem;
	}

	/**
	 * add fluid to an existing fluid
	 *
	 * @param addSystem1 first fluid to add
	 * @param addSystem2 second fluid o add
	 *
	 * @return new fluid
	 */
	public void addFluid(SystemInterface addSystem) {
		boolean addedNewComponent = false;
		int index = -1;
		for (int i = 0; i < addSystem.getPhase(0).getNumberOfComponents(); i++) {
			if (!getPhase(0).hasComponent(addSystem.getPhase(0).getComponent(i).getComponentName())) {
				addedNewComponent = true;
			} else {
				index = getPhase(0).getComponent(addSystem.getPhase(0).getComponent(i).getComponentName())
						.getComponentNumber();
			}

			if (index != -1) {
				addComponent(index, addSystem.getPhase(0).getComponent(i).getNumberOfmoles());
			} else {
				addComponent(addSystem.getPhase(0).getComponent(i).getComponentName(),
						addSystem.getPhase(0).getComponent(i).getNumberOfmoles());
			}
		}
		if (addedNewComponent) {
			createDatabase(true);
			setMixingRule(getMixingRule());
			init(0);
		}
	}

	public void addPhase() {
		/*
		 * if (maxNumberOfPhases < 6 && !hydrateCheck) { ArrayList phaseList = new
		 * ArrayList(0); for (int i = 0; i < numberOfPhases; i++) {
		 * phaseList.add(phaseArray[i]); } // add the new phase
		 * phaseList.add(phaseArray[0].clone()); beta[phaseList.size() - 1] = 1.0e-8; //
		 * beta[1] -= beta[1]/1.0e5;
		 *
		 * PhaseInterface[] phaseArray2 = new PhaseInterface[numberOfPhases + 1];
		 *
		 * for (int i = 0; i < numberOfPhases + 1; i++) { phaseArray2[i] =
		 * (PhaseInterface) phaseList.get(i); }
		 *
		 * phaseArray = phaseArray2;
		 *
		 * System.out.println("number of phases " + numberOfPhases); if
		 * (maxNumberOfPhases < numberOfPhases) { maxNumberOfPhases = numberOfPhases; }
		 * }
		 */
		numberOfPhases++;
	}

	public void addSolidPhase() {
		if (!multiPhaseCheck) {
			setMultiPhaseCheck(true);
		}
		phaseArray[3] = new PhasePureComponentSolid();
		phaseArray[3].setTemperature(phaseArray[0].getTemperature());
		phaseArray[3].setPressure(phaseArray[0].getPressure());
		for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
			if (getPhase(0).getComponent(i).isIsTBPfraction()) {
				phaseArray[3].addcomponent("default", getPhase(0).getComponent(i).getNumberOfmoles(),
						getPhase(0).getComponent(i).getNumberOfmoles(), i);
				phaseArray[3].getComponent(i).setComponentName(getPhase(0).getComponent(i).getName());
				phaseArray[3].getComponent(i).setIsPlusFraction(true);
			} else {
				phaseArray[3].addcomponent(getPhase(0).getComponent(i).getName(),
						getPhase(0).getComponent(i).getNumberOfmoles(), getPhase(0).getComponent(i).getNumberOfmoles(),
						i);
			}
		}
		((PhaseSolid) phaseArray[3]).setSolidRefFluidPhase(phaseArray[0]);
		// numberOfPhases = 4;
		if (getMaxNumberOfPhases() < 4) {
			setMaxNumberOfPhases(4);
		}
	}

	public void addHydratePhase2() {
		if (!multiPhaseCheck) {
			setMultiPhaseCheck(true);
		}
		phaseArray[3] = new PhaseHydrate();
		phaseArray[3].setTemperature(phaseArray[0].getTemperature());
		phaseArray[3].setPressure(phaseArray[0].getPressure());
		for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
			if (getPhase(0).getComponent(i).isIsTBPfraction()) {
				phaseArray[3].addcomponent("default", getPhase(0).getComponent(i).getNumberOfmoles(),
						getPhase(0).getComponent(i).getNumberOfmoles(), i);
				phaseArray[3].getComponent("default").setComponentName(getPhase(0).getComponent(i).getName());
			} else {
				phaseArray[3].addcomponent(getPhase(0).getComponent(i).getName(),
						getPhase(0).getComponent(i).getNumberOfmoles(), getPhase(0).getComponent(i).getNumberOfmoles(),
						i);
			}
		}
		numberOfPhases = 4;
		setMaxNumberOfPhases(4);
	}

	public void addSolidComplexPhase(String type) {
		if (!multiPhaseCheck) {
			setMultiPhaseCheck(true);
		}
		addHydratePhase();
		if (type.equals("wax")) {
			phaseArray[5] = new PhaseWax();
		} else {
			phaseArray[5] = new PhaseSolidComplex();
		}

		phaseArray[5].setTemperature(phaseArray[0].getTemperature());
		phaseArray[5].setPressure(phaseArray[0].getPressure());
		phaseArray[5].setPhaseTypeName("wax");
		for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
			if (getPhase(0).getComponent(i).isIsTBPfraction()) {
				phaseArray[5].addcomponent(getPhase(0).getComponent(i).getName(),
						getPhase(0).getComponent(i).getNumberOfmoles(), getPhase(0).getComponent(i).getNumberOfmoles(),
						i);
				phaseArray[5].getComponent(i).setIsPlusFraction(true);
			} else {
				phaseArray[5].addcomponent(getPhase(0).getComponent(i).getName(),
						getPhase(0).getComponent(i).getNumberOfmoles(), getPhase(0).getComponent(i).getNumberOfmoles(),
						i);
			}
		}
		((PhaseSolid) phaseArray[5]).setSolidRefFluidPhase(phaseArray[0]);
		numberOfPhases = 6;
		setMaxNumberOfPhases(6);
	}

	public void addHydratePhase() {
		if (!multiPhaseCheck) {
			setMultiPhaseCheck(true);
		}

		if (!hasSolidPhase()) {
			phaseArray[3] = new PhasePureComponentSolid();
			phaseArray[3].setTemperature(phaseArray[0].getTemperature());
			phaseArray[3].setPressure(phaseArray[0].getPressure());
			phaseArray[3].setPhaseTypeName("solid");
			for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
				if (getPhase(0).getComponent(i).isIsTBPfraction()) {
					phaseArray[3].addcomponent("default", getPhase(0).getComponent(i).getNumberOfmoles(),
							getPhase(0).getComponent(i).getNumberOfmoles(), i);
					phaseArray[3].getComponent(i).setComponentName(getPhase(0).getComponent(i).getName());
					phaseArray[3].getComponent(i).setIsTBPfraction(true);
				} else {
					phaseArray[3].addcomponent(getPhase(0).getComponent(i).getName(),
							getPhase(0).getComponent(i).getNumberOfmoles(),
							getPhase(0).getComponent(i).getNumberOfmoles(), i);
				}
			}
			((PhaseSolid) phaseArray[3]).setSolidRefFluidPhase(phaseArray[0]);
		}

		phaseArray[4] = new PhaseHydrate(getModelName());
		phaseArray[4].setTemperature(phaseArray[0].getTemperature());
		phaseArray[4].setPressure(phaseArray[0].getPressure());
		phaseArray[4].setPhaseTypeName("hydrate");
		for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
			if (getPhase(0).getComponent(i).isIsTBPfraction()) {
				phaseArray[4].addcomponent("default", getPhase(0).getComponent(i).getNumberOfmoles(),
						getPhase(0).getComponent(i).getNumberOfmoles(), i);
				phaseArray[4].getComponent(i).setComponentName(getPhase(0).getComponent(i).getName());
				phaseArray[4].getComponent(i).setIsTBPfraction(true);
			} else {
				phaseArray[4].addcomponent(getPhase(0).getComponent(i).getName(),
						getPhase(0).getComponent(i).getNumberOfmoles(), getPhase(0).getComponent(i).getNumberOfmoles(),
						i);
			}
		}
		((PhaseHydrate) phaseArray[4]).setSolidRefFluidPhase(phaseArray[0]);

		numberOfPhases = 5;
		if (getMaxNumberOfPhases() < 5) {
			setMaxNumberOfPhases(5);
		}
	}

	public void setAllComponentsInPhase(int phase) {
		// init(0);
		double molesInPhase = 0;
		for (int k = 0; k < numberOfPhases; k++) {
			for (int i = 0; i < numberOfComponents; i++) {
				if (phase != k) {
					// System.out.println("moles of comp: " + i + " " +
					// phaseArray[k].getComponents()[i].getNumberOfMolesInPhase());
					phaseArray[phase].addMoles(i,
							(phaseArray[k].getComponents()[i].getNumberOfMolesInPhase() * (1.0 - 0.01)));
					phaseArray[k].addMoles(i,
							-(phaseArray[k].getComponents()[i].getNumberOfMolesInPhase() * (1.0 - 0.01)));
					phaseArray[k].getComponents()[i].setx(phaseArray[k].getComponents()[i].getNumberOfMolesInPhase()
							/ phaseArray[k].getNumberOfMolesInPhase());
					// System.out.println("moles of comp after: " + i + " " +
					// phaseArray[k].getComponents()[i].getNumberOfMolesInPhase());
				}
			}
		}
		initBeta();
		init(1);
	}

	public void removePhase(int specPhase) {

		setTotalNumberOfMoles(getTotalNumberOfMoles() - getPhase(specPhase).getNumberOfMolesInPhase());

		for (int j = 0; j < numberOfPhases; j++) {
			for (int i = 0; i < numberOfComponents; i++) {
				getPhase(j).getComponents()[i].setNumberOfmoles(getPhase(j).getComponents()[i].getNumberOfmoles()
						- getPhase(specPhase).getComponents()[i].getNumberOfMolesInPhase());
			}
		}

		ArrayList phaseList = new ArrayList(0);
		for (int i = 0; i < numberOfPhases; i++) {
			if (specPhase != i) {
				phaseList.add(phaseArray[phaseIndex[i]]);
			}
		}

		// phaseArray = new PhaseInterface[numberOfPhases - 1];
		for (int i = 0; i < numberOfPhases - 1; i++) {
			// phaseArray[i] = (PhaseInterface) phaseList.get(i);
			if (i >= specPhase) {
				phaseIndex[i] = phaseIndex[i + 1];
				phaseType[i] = phaseType[i + 1];
			}
		}
		numberOfPhases--;
	}

	public void removePhaseKeepTotalComposition(int specPhase) {

		ArrayList phaseList = new ArrayList(0);
		for (int i = 0; i < numberOfPhases; i++) {
			if (specPhase != i) {
				phaseList.add(phaseArray[phaseIndex[i]]);
			}
		}

		// phaseArray = new PhaseInterface[numberOfPhases - 1];
		for (int i = 0; i < numberOfPhases - 1; i++) {
			// phaseArray[i] = (PhaseInterface) phaseList.get(i);
			if (i >= specPhase) {
				phaseIndex[i] = phaseIndex[i + 1];
				phaseType[i] = phaseType[i + 1];
			}
		}
		numberOfPhases--;
	}

	public void replacePhase(int repPhase, PhaseInterface newPhase) {
		for (int i = 0; i < 2; i++) {
			phaseArray[i] = (PhaseInterface) newPhase.clone();
		}
		setTotalNumberOfMoles(newPhase.getNumberOfMolesInPhase());
	}

	public SystemInterface phaseToSystem(PhaseInterface newPhase) {

		for (int i = 0; i < newPhase.getNumberOfComponents(); i++) {
			newPhase.getComponents()[i].setNumberOfmoles(newPhase.getComponents()[i].getNumberOfMolesInPhase());
		}

		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			phaseArray[i] = (PhaseInterface) newPhase.clone();
		}

		setTotalNumberOfMoles(newPhase.getNumberOfMolesInPhase());
		this.init(0);
		setNumberOfPhases(1);
		setPhaseType(0, newPhase.getPhaseType());
		initBeta();
		init_x_y();
		this.init(1);
		return this;
	}

	public SystemInterface getEmptySystemClone() {
		int phaseNumber = 0;

		SystemInterface newSystem = (SystemInterface) this.clone();

		for (int j = 0; j < getMaxNumberOfPhases(); j++) {
			phaseNumber = j;
			for (int i = 0; i < getPhase(j).getNumberOfComponents(); i++) {
				newSystem.getPhase(j).getComponents()[i]
						.setNumberOfmoles(getPhase(phaseNumber).getComponents()[i].getNumberOfMolesInPhase() / 1.0e30);
				newSystem.getPhase(j).getComponents()[i].setNumberOfMolesInPhase(
						getPhase(phaseNumber).getComponents()[i].getNumberOfMolesInPhase() / 1.0e30);
			}
		}

		newSystem.setTotalNumberOfMoles(getPhase(phaseNumber).getNumberOfMolesInPhase() / 1.0e30);

		newSystem.init(0);
		newSystem.init(1);
		return newSystem;
	}

	public SystemInterface phaseToSystem(String phaseName) {
		try {
			for (int j = 0; j < getMaxNumberOfPhases(); j++) {
				if (this.getPhase(j).getPhaseTypeName().equals(phaseName)) {
					return phaseToSystem(j);
				}
			}
		} catch (Exception e) {
			logger.error(
					"error....." + fluidName + " has no phase .... " + phaseName + " ..... returning phase number 0");
		}
		return phaseToSystem(0);

	}

	public SystemInterface phaseToSystem(int phaseNumber) {
		SystemInterface newSystem = (SystemInterface) this.clone();

		for (int j = 0; j < getMaxNumberOfPhases(); j++) {
			for (int i = 0; i < getPhase(j).getNumberOfComponents(); i++) {
				newSystem.getPhase(j).getComponent(i)
						.setNumberOfmoles(getPhase(phaseNumber).getComponent(i).getNumberOfMolesInPhase());
				newSystem.getPhase(j).getComponent(i)
						.setNumberOfMolesInPhase(getPhase(phaseNumber).getComponent(i).getNumberOfMolesInPhase());
			}
		}

		newSystem.setTotalNumberOfMoles(getPhase(phaseNumber).getNumberOfMolesInPhase());

		newSystem.init(0);
		newSystem.setNumberOfPhases(1);
		newSystem.setPhaseType(0, getPhase(phaseNumber).getPhaseType());// phaseType[phaseNumber]);
		newSystem.init(1);
		return newSystem;
	}

	public SystemInterface phaseToSystem(int phaseNumber1, int phaseNumber2) {
		SystemInterface newSystem = (SystemInterface) this.clone();

		for (int j = 0; j < getMaxNumberOfPhases(); j++) {
			for (int i = 0; i < getPhase(j).getNumberOfComponents(); i++) {
				newSystem.getPhases()[j].getComponents()[i]
						.setNumberOfmoles(getPhase(phaseNumber1).getComponents()[i].getNumberOfMolesInPhase()
								+ getPhase(phaseNumber2).getComponents()[i].getNumberOfMolesInPhase());
				newSystem.getPhases()[j].getComponents()[i]
						.setNumberOfMolesInPhase(getPhase(phaseNumber1).getComponents()[i].getNumberOfMolesInPhase()
								+ getPhase(phaseNumber2).getComponents()[i].getNumberOfMolesInPhase());
			}
		}

		newSystem.setTotalNumberOfMoles(
				getPhase(phaseNumber1).getNumberOfMolesInPhase() + getPhase(phaseNumber2).getNumberOfMolesInPhase());

		newSystem.init(0);

		newSystem.setNumberOfPhases(1);
		// newSystem.setPhaseType(0,
		// getPhase(phaseNumber1).getPhaseType());//phaseType[phaseNumber]);
		newSystem.init(1);
		return newSystem;
	}

	public void setTotalFlowRate(double flowRate, String flowunit) {

		if (getMolarMass() < 1e-20) {
			init(0);
		}
		if (flowunit.equals("Am3/hr") || flowunit.equals("Am3/min") || flowunit.equals("Am3/sec")) {
			initPhysicalProperties("density");
		}
		neqsim.util.unit.Unit unit = new neqsim.util.unit.RateUnit(flowRate, flowunit, getMolarMass(),
				getDensity("kg/m3"), 0);
		double SIval = unit.getSIvalue();
		double totalNumberOfMolesLocal = totalNumberOfMoles;
		for (int i = 0; i < numberOfComponents; i++) {
			if (flowRate < 1e-100) {
				setEmptyFluid();
			} else if (totalNumberOfMolesLocal > 1e-100) {
				addComponent(i, SIval / totalNumberOfMolesLocal * getPhase(0).getComponent(i).getNumberOfmoles()
						- getPhase(0).getComponent(i).getNumberOfmoles());
			} else {
				addComponent(i, SIval);
			}
		}
	}

	/**
	 * method to return flow rate of fluid
	 *
	 * @param flowunit The unit as a string. Supported units are kg/sec, kg/min,
	 *                 kg/hr m3/sec, m3/min, m3/hr, mole/sec, mole/min, mole/hr,
	 *                 Sm3/hr, Sm3/day
	 *
	 * @return flow rate in specified unit
	 */
	public double getFlowRate(String flowunit) {
		if (flowunit.equals("kg/sec")) {
			return totalNumberOfMoles * getMolarMass();
		} else if (flowunit.equals("kg/min")) {
			return totalNumberOfMoles * getMolarMass() * 60.0;
		} else if (flowunit.equals("Sm3/hr")) {
			return totalNumberOfMoles * 3600.0 * ThermodynamicConstantsInterface.R
					* ThermodynamicConstantsInterface.standardStateTemperature / 101325.0;
		} else if (flowunit.equals("Sm3/day")) {
			return totalNumberOfMoles * 3600.0 * 24.0 * ThermodynamicConstantsInterface.R
					* ThermodynamicConstantsInterface.standardStateTemperature / 101325.0;
		} else if (flowunit.equals("kg/hr")) {
			return totalNumberOfMoles * getMolarMass() * 3600.0;
		} else if (flowunit.equals("m3/hr")) {
			// return getVolume() / 1.0e5 * 3600.0;
			return totalNumberOfMoles * getMolarMass() * 3600.0 / getDensity("kg/m3");
		} else if (flowunit.equals("m3/min")) {
			return totalNumberOfMoles * getMolarMass() * 60.0 / getDensity("kg/m3");
			// return getVolume() / 1.0e5 * 60.0;
		} else if (flowunit.equals("m3/sec")) {
			return totalNumberOfMoles * getMolarMass() / getDensity("kg/m3");
			// return getVolume() / 1.0e5;
		} else if (flowunit.equals("mole/sec")) {
			return totalNumberOfMoles;
		} else if (flowunit.equals("mole/min")) {
			return totalNumberOfMoles * 60.0;
		} else if (flowunit.equals("mole/hr")) {
			return totalNumberOfMoles * 3600.0;
		} else {
			throw new RuntimeException("failed.. unit: " + flowunit + " not suported");
		}
	}

	public void changeComponentName(String name, String newName) {

		for (int i = 0; i < numberOfComponents; i++) {
			if (componentNames.get(i).equals(name)) {
				componentNames.set(i, newName);
			}
		}

		for (int i = 0; i < maxNumberOfPhases; i++) {
			getPhase(i).getComponent(name).setComponentName(newName);
		}
	}

	public void addComponent(String componentName, double value, String name, int phase) {
		if (!neqsim.util.database.NeqSimDataBase.hasComponent(componentName)) {
			logger.error("No component with name: " + componentName + " in database");
			return;
		}
		neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
		java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + componentName + "'"));
		double molarmass = 0.0, stddens = 0.0, boilp = 0.0;
		try {
			dataSet.next();
			molarmass = Double.parseDouble(dataSet.getString("molarmass")) / 1000.0;
			stddens = Double.parseDouble(dataSet.getString("stddens"));
			boilp = Double.parseDouble(dataSet.getString("normboil"));
		} catch (Exception e) {
			logger.error("failed " + e.toString());
			throw new RuntimeException(e);
		} finally {
			try {
				dataSet.close();
				if (database.getStatement() != null) {
					database.getStatement().close();
				}
				if (database.getConnection() != null) {
					database.getConnection().close();
				}
			} catch (Exception e) {
				logger.error("error", e);

			}
		}
		neqsim.util.unit.Unit unit = new neqsim.util.unit.RateUnit(value, name, molarmass, stddens, boilp);
		double SIval = unit.getSIvalue();
		// System.out.println("number of moles " + SIval);
		this.addComponent(componentName, SIval, phase);
	}

	public void addSalt(String componentName, double value) {
		neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
		java.sql.ResultSet dataSet = database
				.getResultSet("SELECT * FROM compsalt WHERE SaltName='" + componentName + "'");
		double val1 = 1e-20, val2 = 1e-20;
		try {
			dataSet.next();
			String name1 = dataSet.getString("ion1").trim();
			String name2 = dataSet.getString("ion2").trim();
			val1 = Double.parseDouble(dataSet.getString("stoc1")) * value;
			val2 = Double.parseDouble(dataSet.getString("stoc2")) * value;
			this.addComponent(name1, val1);
			this.addComponent(name2, val2);
			logger.info("ok adding salts. Ions: " + name1 + ", " + name2);
		} catch (Exception e) {
			logger.error("failed " + e.toString());
		}
	}

	/**
	 * method to add true boiling point fraction
	 *
	 * @param componentName selected name of the component to be added
	 * @param numberOfMoles number of moles to be added
	 * @param molarMass     molar mass of the component in kg/mol
	 * @param density       density of the component in g/cm3
	 */
	public void addTBPfraction(String componentName, double numberOfMoles, double molarMass, double density) {
		if (density < 0.0 || molarMass < 0.0) {
			logger.error("Negative input molar mass or density.");
			neqsim.util.exception.InvalidInputException e = new neqsim.util.exception.InvalidInputException();
			throw new RuntimeException(e);

		}

		SystemInterface refSystem = null;
		double TC = 0.0, PC = 0.0, m = 0.0, TB = 0.0, acs = 0.0;
		double penelouxC = 0.0;
		double racketZ = 0.0;
		componentName = (componentName.split("_PC")[0]) + "_PC";// + getFluidName());

		try {
			refSystem = this.getClass().getDeclaredConstructor().newInstance();
			refSystem.setTemperature(273.15 + 15.0);
			refSystem.setPressure(1.01325);
			refSystem.addComponent("default", 1.0, 273.15, 50.0, 0.1);
			refSystem.init(0);
			refSystem.setNumberOfPhases(1);
			refSystem.setPhaseType(0, "liquid");
			molarMass = 1000 * molarMass;
			TC = characterization.getTBPModel().calcTC(molarMass, density);
			PC = characterization.getTBPModel().calcPC(molarMass, density);
			m = characterization.getTBPModel().calcm(molarMass, density);
			acs = characterization.getTBPModel().calcAcentricFactor(molarMass, density);
			// TBPfractionCoefs[2][0]+TBPfractionCoefs[2][1]*molarMass+TBPfractionCoefs[2][2]*density+TBPfractionCoefs[2][3]*Math.pow(molarMass,2.0);
			TB = characterization.getTBPModel().calcTB(molarMass, density);
			// Math.pow((molarMass/5.805e-5*Math.pow(density,0.9371)), 1.0/2.3776);
			// acs = TBPfractionModel.calcAcentricFactor(molarMass, density);
			// System.out.println("acentric " + acs);
			// 3.0/7.0*MathLib.generalMath.GeneralMath.log10(PC/1.01325)/(TC/TB-1.0)-1.0;
			molarMass /= 1000.0;

			for (int i = 0; i < refSystem.getNumberOfPhases(); i++) {
				refSystem.getPhase(i).getComponent(0).setComponentName(componentName);
				refSystem.getPhase(i).getComponent(0).setMolarMass(molarMass);
				refSystem.getPhase(i).getComponent(0).setAcentricFactor(acs);
				refSystem.getPhase(i).getComponent(0).setTC(TC);
				refSystem.getPhase(i).getComponent(0).setPC(PC);
				refSystem.getPhase(i).getComponent(0).setComponentType("TBPfraction");
				refSystem.getPhase(i).getComponent(0).setIsTBPfraction(true);
				if (characterization.getTBPModel().isCalcm()) {
					refSystem.getPhase(i).getComponent(0).getAtractiveTerm().setm(m);
					acs = refSystem.getPhase(i).getComponent(0).getAcentricFactor();
				}
			}

			refSystem.setTemperature(273.15 + 15.0);
			refSystem.setPressure(1.01325);
			refSystem.init(1);
			// refSystem.display();
			racketZ = characterization.getTBPModel().calcRacketZ(refSystem, molarMass * 1000.0, density);

			// System.out.println("vol ok");
			// System.out.println("racketZ " + racketZ);
			// penelouxC = (refSystem.getPhase(1).getMolarVolume() - molarMass/density*1e2);
			// System.out.println("vol err " +
			// penelouxC/refSystem.getPhase(1).getMolarVolume()*100);
			// racketZ = TPBracketcoefs[0] -
			// penelouxC/(TPBracketcoefs[1]*thermo.ThermodynamicConstantsInterface.R*refSystem.getPhase(1).getComponent(0).getTC()/(refSystem.getPhase(1).getComponent(0).getPC()));
			refSystem.getPhase(0).getComponent(0).setRacketZ(racketZ);
			// refSystem.init(1);
			// refSystem.display();
			// refSystem.getPhase(1).getComponent(0).setRacketZ(racketZ);
			//
			// // refSystem.setTemperature(273.15+80.0);
			// // refSystem.setPressure(1.01325);
			// // refSystem.init(1);
			// //refSystem.initPhysicalProperties();
			// // APIdens - refSystem.getPhase(1).getPhysicalProperties().getDensity();;
			// //må sammenligne med API-standard for tetthet - og sette Penloux dt
			//
			//
		} catch (Exception e) {
			logger.error("error", e);
		}

		double critVol = characterization.getTBPModel().calcCriticalVolume(molarMass * 1000, density);// 0.2918-0.0928*
																										// acs)*8.314*TC/PC*10.0;
		addComponent(componentName, numberOfMoles, TC, PC, acs);
		double Kwatson = Math.pow(TB * 1.8, 1.0 / 3.0) / density;
		// System.out.println("watson " + Kwatson);
		double CF = Math.pow((12.8 - Kwatson) * (10.0 - Kwatson) / (10.0 * acs), 2.0);
		double acsKeslerLee = acs;// characterization.getTBPModel().calcAcentricFactorKeslerLee(molarMass*1000.0,
									// density);
		double cpa = (-0.33886 + 0.02827 * Kwatson - 0.26105 * CF + 0.59332 * acsKeslerLee * CF) * 4.18682 * molarMass
				* 1e3;
		double cpb = (-(0.9291 - 1.1543 * Kwatson + 0.0368 * Kwatson * Kwatson) * 1e-4
				+ CF * (4.56 - 9.48 * acsKeslerLee) * 1e-4) * 4.18682 * molarMass * 1.8 * 1e3;
		double cpc = (-1.6658e-7 + CF * (0.536 - 0.6828 * acsKeslerLee) * 1.0e-7) * 4.18682 * molarMass * 1.8 * 1.8
				* 1.0e3;
		double cpd = 0.0;

		for (int i = 0; i < numberOfPhases; i++) {
			getPhase(i).setAtractiveTerm(attractiveTermNumber);
			getPhase(i).getComponent(componentName).setMolarMass(molarMass);
			getPhase(i).getComponent(componentName).setComponentType("TBPfraction");
			getPhase(i).getComponent(componentName).setNormalLiquidDensity(density);
			getPhase(i).getComponent(componentName).setNormalBoilingPoint(TB - 273.15);
			getPhase(i).getComponent(componentName)
					.setAcentricFactor(refSystem.getPhase(0).getComponent(0).getAcentricFactor());
			getPhase(i).getComponent(componentName).setCriticalVolume(critVol);
			getPhase(i).getComponent(componentName).setRacketZ(racketZ);
			getPhase(i).getComponent(componentName).setRacketZCPA(racketZ);
			getPhase(i).getComponent(componentName).setIsTBPfraction(true);
			getPhase(i).getComponent(componentName)
					.setParachorParameter(characterization.getTBPModel().calcParachorParameter(molarMass, density));// 59.3+2.34*molarMass*1000.0);//0.5003*thermo.ThermodynamicConstantsInterface.R*TC/PC*(0.25969-racketZ));
			getPhase(i).getComponent(componentName).setCriticalViscosity(
					characterization.getTBPModel().calcCriticalViscosity(molarMass * 1000.0, density));// 7.94830*Math.sqrt(1e3*molarMass)*Math.pow(PC,2.0/3.0)/Math.pow(TC,
																										// 1.0/6.0)*1e-7);
			getPhase(i).getComponent(componentName).setTriplePointTemperature(
					374.5 + 0.02617 * getPhase(i).getComponent(componentName).getMolarMass() * 1000.0
							- 20172.0 / (getPhase(i).getComponent(componentName).getMolarMass() * 1000.0));
			getPhase(i).getComponent(componentName)
					.setHeatOfFusion(0.1426 / 0.238845 * getPhase(i).getComponent(componentName).getMolarMass() * 1000.0
							* getPhase(i).getComponent(componentName).getTriplePointTemperature());
			getPhase(i).getComponent(componentName).setIdealGasEnthalpyOfFormation(-1462600 * molarMass - 47566.0);
			// getPhase(i).getComponent(componentName).set

			// System.out.println(" plusTC " + TC + " plusPC " + PC + " plusm " + m + "
			// acslusm " + acs + " tb " + TB + " critvol " + critVol + " racketZ " + racketZ
			// + " parachor " +
			// getPhase(i).getComponent(componentName).getParachorParameter());
			getPhase(i).getComponent(componentName).setCpA(cpa);
			getPhase(i).getComponent(componentName).setCpB(cpb);
			getPhase(i).getComponent(componentName).setCpC(cpc);
			getPhase(i).getComponent(componentName).setCpD(cpd);
		}
	}

	public void addPlusFraction(String componentName, double numberOfMoles, double molarMass, double density) {
		addTBPfraction(componentName, numberOfMoles, molarMass, density);
		componentName = (componentName + "_" + "PC");// getFluidName());
		for (int i = 0; i < numberOfPhases; i++) {
			// System.out.println("comp " + componentName);
			getPhase(i).getComponent(componentName).setIsPlusFraction(true);
			getPhase(i).getComponent(componentName).setCriticalViscosity(
					7.94830 * Math.sqrt(1e3 * getPhase(i).getComponent(componentName).getMolarMass())
							* Math.pow(getPhase(i).getComponent(componentName).getPC(), 2.0 / 3.0)
							/ Math.pow(getPhase(i).getComponent(componentName).getTC(), 1.0 / 6.0) * 1e-7);
		}
	}

	public void addComponent(String componentName, double value, String name) {
		if (!neqsim.util.database.NeqSimDataBase.hasComponent(componentName)) {
			logger.error("No component with name: " + componentName + " in database");
			return;
		}
		neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
		java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + componentName + "'"));
		double molarmass = 0.0, stddens = 0.0, boilp = 0.0;
		try {
			dataSet.next();
			molarmass = Double.parseDouble(dataSet.getString("molarmass")) / 1000.0;
			stddens = Double.parseDouble(dataSet.getString("stddens"));
			boilp = Double.parseDouble(dataSet.getString("normboil"));
		} catch (Exception e) {
			logger.error("failed " + e.toString());
		} finally {
			try {
				dataSet.close();
			} catch (Exception e) {
				logger.error("error", e);

			}
		}
		neqsim.util.unit.Unit unit = new neqsim.util.unit.RateUnit(value, name, molarmass, stddens, boilp);
		double SIval = unit.getSIvalue();
		// System.out.println("number of moles " + SIval);
		this.addComponent(componentName, SIval);
	}

	public void addComponent(String componentName, double moles, double TC, double PC, double acs) {
		String comNam = componentName;
		if (getPhase(0).hasComponent(componentName)) {
			addComponent(componentName, moles);
		} else {
			addComponent("default", moles);
			comNam = "default";
//            componentNames.set(componentNames.indexOf("default"), componentName);
		}
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			getPhase(i).getComponent(comNam).setComponentName(componentName);
			getPhase(i).getComponent(componentName).setTC(TC);
			getPhase(i).getComponent(componentName).setPC(PC);
			getPhase(i).getComponent(componentName).setAcentricFactor(acs);
		}
		if (comNam.equals("default")) {
			componentNames.remove("default");
			componentNames.add(componentName);
		}
	}

	public void addComponent(int componentIndex, double moles) {
		if (componentIndex >= getPhase(0).getNumberOfComponents()) {
			logger.error("componentIndex higher than number of components in database");
			return;
		}
		setTotalNumberOfMoles(getTotalNumberOfMoles() + moles);
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			getPhase(i).addMolesChemReac(componentIndex, moles, moles);
		}
	}

	/**
	 * add a component to a fluid. If component already exists, it will be added to
	 * the component
	 *
	 * @param componentName Name of the component to be added. See NeqSim database
	 *                      for available components in the database.
	 * @param moles         number of moles (per second) of the component to be
	 *                      added to the fluid
	 */
	public void addComponent(String componentName, double moles) {
		int index = 0;

		boolean addForFirstTime = true;
		for (int p = 0; p < componentNames.size(); p++) {
			if (componentNames.get(p).equals(componentName)) {
				addForFirstTime = false;
				index = p;
			}
		}

		if (!neqsim.util.database.NeqSimDataBase.hasComponent(componentName) && addForFirstTime) {
			// logger.error("No component with name: " + componentName + " in database");
			return;
		}

		if (addForFirstTime) {
			if (moles < 0.0) {
				logger.error("Negative input number of moles of component: " + componentName);
				neqsim.util.exception.InvalidInputException e = new neqsim.util.exception.InvalidInputException();
				throw new RuntimeException(e);
			}
			setTotalNumberOfMoles(getTotalNumberOfMoles() + moles);
			// System.out.println("adding " + componentName);
			componentNames.add(componentName);
			for (int i = 0; i < getMaxNumberOfPhases(); i++) {
				getPhase(i).addcomponent(componentName, moles, moles, numberOfComponents);
				getPhase(i).setAtractiveTerm(attractiveTermNumber);
			}
			numberOfComponents++;
		} else {
			for (int i = 0; i < getMaxNumberOfPhases(); i++) {
				if ((getPhase(i).getComponent(componentName).getNumberOfMolesInPhase() + moles) < 0.0) {
					init(0);
					break;
				}
			}

			setTotalNumberOfMoles(getTotalNumberOfMoles() + moles);
			// System.out.println("adding chem reac " + componentName);
			for (int i = 0; i < getMaxNumberOfPhases(); i++) {
				getPhase(i).addMolesChemReac(index, moles, moles);
			}
		}
	}

	public void addComponent(String componentName, double moles, int phaseNumber) {
		if (!neqsim.util.database.NeqSimDataBase.hasComponent(componentName)) {
			logger.error("No component with name: " + componentName + " in database");
			return;
		}
		int index = 0;

		boolean addForFirstTime = true;
		for (int p = 0; p < componentNames.size(); p++) {
			if (componentNames.get(p).equals(componentName)) {
				addForFirstTime = false;
				index = p;
			}
		}

		if (addForFirstTime) {
			if (moles < 0.0) {
				logger.error("Negative input number of moles.");
				neqsim.util.exception.InvalidInputException e = new neqsim.util.exception.InvalidInputException();
				throw new RuntimeException(e);
			}

			componentNames.add(componentName);
			double k = 1.0;
			setTotalNumberOfMoles(getTotalNumberOfMoles() + moles);

			for (int i = 0; i < getMaxNumberOfPhases(); i++) {
				if (phaseNumber == i) {
					k = 1.0;
				} else {
					k = 1.0e-30;
				}
				getPhase(i).addcomponent(componentName, moles, moles * k, numberOfComponents);
				getPhase(i).setAtractiveTerm(attractiveTermNumber);
			}
			numberOfComponents++;
		} else {
			addComponent(index, moles, phaseNumber);
		}
	}

	public void addComponent(int index, double moles, int phaseNumber) {
		if (index >= getPhase(0).getNumberOfComponents()) {
			logger.error("componentIndex higher than number of components in database");
			return;
		}
		double k = 1.0;

		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			if (phaseNumber == i) {
				k = 1.0;
			} else {
				k = 1e-30;
			}
			phaseArray[phaseIndex[i]].addMolesChemReac(index, moles * k, moles);
		}
		setTotalNumberOfMoles(getTotalNumberOfMoles() + moles);
	}

	public void removeComponent(String name) {
		setTotalNumberOfMoles(getTotalNumberOfMoles() - phaseArray[0].getComponent(name).getNumberOfmoles());
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			getPhase(i).removeComponent(name, getTotalNumberOfMoles(),
					phaseArray[phaseIndex[i]].getComponent(name).getNumberOfMolesInPhase(),
					phaseArray[phaseIndex[i]].getComponent(name).getComponentNumber());
		}
		//
		componentNames.remove(name);
		// System.out.println("removing " + componentNames.toString());
		numberOfComponents--;
	}

	/**
	 * This method set the flow rate of all components to zero.
	 */
	public void setEmptyFluid() {
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			getPhase(i).setEmptyFluid();
		}
		totalNumberOfMoles = 0.0;
	}

	/**
	 * This method set the flow rate of all components to zero. This method is
	 * depreciated - and the setEmptyFluid method should be used.
	 */
	@Deprecated
	public void removeMoles() {
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			getPhase(i).setEmptyFluid();
		}
		totalNumberOfMoles = 0.0;
	}

	public final double calcBeta()
			throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
		ComponentInterface[] compArray = getPhase(0).getComponents();

		int i, iterations = 0;
		double tolerance = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
		double deriv = 0.0, gbeta = 0.0, gtest = 0.0, betal = 0;
		double nybeta = 0, midler = 0, minBeta = tolerance, maxBeta = 1.0 - tolerance;

		double g0 = -1.0, g1 = 1.0;
		nybeta = beta[0];
		betal = 1.0 - nybeta;

		for (i = 0; i < numberOfComponents; i++) {
			midler = (compArray[i].getK() * compArray[i].getz() - 1.0) / (compArray[i].getK() - 1.0);
			if ((midler > minBeta) && (compArray[i].getK() > 1.0)) {
				minBeta = midler;
			}
			midler = (1.0 - compArray[i].getz()) / (1.0 - compArray[i].getK());
			if ((midler < maxBeta) && (compArray[i].getK() < 1.0)) {
				maxBeta = midler;
			}
			g0 += compArray[i].getz() * compArray[i].getK();
			g1 += -compArray[i].getz() / compArray[i].getK();
		}

		if (g0 < 0) {
			beta[1] = 1.0 - tolerance;
			beta[0] = tolerance;
			return beta[0];
		}
		if (g1 > 0) {
			beta[1] = tolerance;
			beta[0] = 1.0 - tolerance;
			return beta[0];
		}

		nybeta = (minBeta + maxBeta) / 2.0;
		// System.out.println("guessed beta: " + nybeta + " maxbeta: " +maxBeta + "
		// minbeta: " +minBeta );
		betal = 1.0 - nybeta;

		// ' *l = 1.0-nybeta;
		gtest = 0.0;
		for (i = 0; i < numberOfComponents; i++) {
			gtest += compArray[i].getz() * (compArray[i].getK() - 1.0) / (1.0 - nybeta + nybeta * compArray[i].getK()); // beta
																														// =
																														// nybeta
		}

		if (gtest >= 0) {
			minBeta = nybeta;
		} else {
			maxBeta = nybeta;
		}

		if (gtest < 0) {
			double minold = minBeta;
			minBeta = 1.0 - maxBeta;
			maxBeta = 1.0 - minold;
		}

		iterations = 0;
		// System.out.println("gtest: " + gtest);
		double step = 1.0;
		do {
			iterations++;
			if (gtest >= 0) {
				// oldbeta = nybeta;
				deriv = 0.0;
				gbeta = 0.0;

				for (i = 0; i < numberOfComponents; i++) {
					double temp1 = (compArray[i].getK() - 1.0);
					double temp2 = 1.0 + temp1 * nybeta;
					deriv += -(compArray[i].getz() * temp1 * temp1) / (temp2 * temp2);
					gbeta += compArray[i].getz() * (compArray[i].getK() - 1.0)
							/ (1.0 + (compArray[i].getK() - 1.0) * nybeta);
				}

				if (gbeta >= 0) {
					minBeta = nybeta;
				} else {
					maxBeta = nybeta;
				}
				nybeta -= (gbeta / deriv);

				// System.out.println("beta: " + maxBeta);
				if (nybeta > maxBeta) {
					nybeta = maxBeta;
				}
				if (nybeta < minBeta) {
					nybeta = minBeta;
				}

				/*
				 * if ((nybeta > maxBeta) || (nybeta < minBeta)) { // nybeta = 0.5 * (maxBeta +
				 * minBeta); gbeta = 1.0; }
				 */
			} else {
				// oldbeta = betal;
				deriv = 0.0;
				gbeta = 0.0;

				for (i = 0; i < numberOfComponents; i++) {
					deriv -= (compArray[i].getz() * (compArray[i].getK() - 1.0) * (1.0 - compArray[i].getK()))
							/ Math.pow((betal + (1 - betal) * compArray[i].getK()), 2);
					gbeta += compArray[i].getz() * (compArray[i].getK() - 1.0)
							/ (betal + (-betal + 1.0) * compArray[i].getK());
				}

				if (gbeta < 0) {
					minBeta = betal;
				} else {
					maxBeta = betal;
				}

				betal -= (gbeta / deriv);

				if (betal > maxBeta) {
					betal = maxBeta;
				}
				if (betal < minBeta) {
					betal = minBeta;
				}

				/*
				 * if ((betal > maxBeta) || (betal < minBeta)) { gbeta = 1.0; { betal = 0.5 *
				 * (maxBeta + minBeta); } }
				 */
				nybeta = 1.0 - betal;
			}
			step = gbeta / deriv;
			// System.out.println("step : " + step);

		} while (((Math.abs(step)) >= 1.0e-10 && iterations < 300));// && (Math.abs(nybeta)-Math.abs(maxBeta))>0.1);

		// System.out.println("beta: " + nybeta + " iterations: " + iterations);
		if (nybeta <= tolerance) {
			phase = 1;
			nybeta = tolerance;
		} else if (nybeta >= 1.0 - tolerance) {
			phase = 0;
			nybeta = 1.0 - tolerance;
			// superheated vapour
		} else {
			phase = 2;
		} // two-phase liquid-gas

		beta[0] = nybeta;
		beta[1] = 1.0 - nybeta;

		if (iterations >= 300) {
			throw new neqsim.util.exception.TooManyIterationsException();
		}
		if (Double.isNaN(beta[1])) {
			for (i = 0; i < numberOfComponents; i++) {
				// System.out.println("K " + compArray[i].getK());
				// System.out.println("z " + compArray[i].getz());
			}
			throw new neqsim.util.exception.IsNaNException();
		}
		return beta[0];
	}

	public final double initBeta() {
		for (int i = 0; i < numberOfPhases; i++) {
			beta[phaseIndex[i]] = getPhase(i).getNumberOfMolesInPhase() / getTotalNumberOfMoles();
			// System.out.println("beta " + beta[i]);
		}
		return beta[phaseIndex[0]];
	}

	/**
	 * method to get the Joule Thomson Coefficient of a system. Based on a phase
	 * mole fraction basis average
	 * 
	 * @param unit The unit as a string. Supported units are K/bar, C/bar
	 *
	 * @return Joule Thomson coefficient in given unit
	 */
	public double getJouleThomsonCoefficient(String unit) {
		double JTcoef = getJouleThomsonCoefficient();
		double conversionFactor = 1.0;
		switch (unit) {
		case "K/bar":
			conversionFactor = 1.0;
			break;
		case "C/bar":
			conversionFactor = 1.0;
			break;
		}
		return JTcoef * conversionFactor;
	}

	/**
	 * method to get the Joule Thomson Coefficient of a system. Based on a phase
	 * mole fraction basis average
	 *
	 * @return Joule Thomson coefficient in K/bar
	 */
	public double getJouleThomsonCoefficient() {
		double JTcoef = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			JTcoef += getBeta(i) * getPhase(i).getJouleThomsonCoefficient();
		}
		return JTcoef;
	}

	/**
	 * method to get the speed of sound of a system. THe sound speed is implemented
	 * based on a molar average over the phases
	 * 
	 * @param unit The unit as a string. Supported units are m/s, km/h
	 * @return speed of sound in m/s
	 */
	public double getSoundSpeed(String unit) {
		double refVel = getSoundSpeed();
		double conversionFactor = 1.0;
		switch (unit) {
		case "m/s":
			conversionFactor = 1.0;
			break;
		case "km/hr":
			conversionFactor = 3.6;
			break;
		}
		return refVel * conversionFactor;
	}

	/**
	 * method to get the speed of sound of a system. THe sound speed is implemented
	 * based on a molar average over the phases
	 *
	 * @return speed of sound in m/s
	 */
	public double getSoundSpeed() {
		double soundspeed = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			soundspeed += getBeta(i) * getPhase(i).getSoundSpeed();
		}
		return soundspeed;
	}

	public final void initTotalNumberOfMoles(double change) {
		setTotalNumberOfMoles(getTotalNumberOfMoles() + change);
		// System.out.println("total moles: " + totalNumberOfMoles);
		for (int j = 0; j < numberOfPhases; j++) {
			for (int i = 0; i < numberOfComponents; i++) {
				getPhase(j).getComponents()[i]
						.setNumberOfmoles(phaseArray[phaseIndex[0]].getComponents()[i].getNumberOfmoles());
			}
		}
	}

	public final void init_x_y() {
		double x = 0.0, z = 0;
		for (int j = 0; j < numberOfPhases; j++) {
			x = 0;
			z = 0;
			for (int i = 0; i < numberOfComponents; i++) {
				getPhase(j).getComponents()[i]
						.setz(getPhase(j).getComponents()[i].getNumberOfmoles() / getTotalNumberOfMoles());
				getPhase(j).getComponents()[i].setx(getPhase(j).getComponents()[i].getNumberOfMolesInPhase()
						/ getPhase(j).getNumberOfMolesInPhase());
				x += getPhase(j).getComponents()[i].getx();
				z += getPhase(j).getComponents()[i].getz();
			}
		}
	}

	public final void calc_x_y() {

		for (int j = 0; j < numberOfPhases; j++) {
			for (int i = 0; i < numberOfComponents; i++) {
				if (j == 0) {
					getPhase(j).getComponent(i).setx(getPhase(0).getComponent(i).getK()
							* getPhase(j).getComponents()[i].getz()
							/ (1 - beta[phaseIndex[0]] + beta[phaseIndex[0]] * getPhase(0).getComponent(i).getK()));
				} else if (j == 1) {
					getPhase(j).getComponent(i).setx(getPhase(0).getComponent(i).getz()
							/ (1.0 - beta[phaseIndex[0]] + beta[phaseIndex[0]] * getPhase(0).getComponent(i).getK()));
				} //
					// phaseArray[j].getComponents()[i].setx(phaseArray[0].getComponents()[i].getx()
					// / phaseArray[0].getComponents()[i].getK());
					// System.out.println("comp: " + j + i + " " + c[j][i].getx());
			}
			getPhase(j).normalize();
		}
	}

	public final void calc_x_y_nonorm() {

		for (int j = 0; j < numberOfPhases; j++) {
			for (int i = 0; i < numberOfComponents; i++) {
				if (j == 0) {
					getPhase(j).getComponents()[i].setx(getPhase(j).getComponents()[i].getK()
							* getPhase(j).getComponents()[i].getz()
							/ (1 - beta[phaseIndex[0]] + beta[phaseIndex[0]] * getPhase(0).getComponents()[i].getK()));
				}
				if (j == 1) {
					getPhase(j).getComponents()[i].setx(getPhase(0).getComponents()[i].getz() / (1.0
							- beta[phaseIndex[0]] + beta[phaseIndex[0]] * getPhase(0).getComponents()[i].getK()));
				} //
					// phaseArray[j].getComponents()[i].setx(phaseArray[0].getComponents()[i].getx()
					// / phaseArray[0].getComponents()[i].getK());
					// System.out.println("comp: " + j + i + " " + c[j][i].getx());
			}
			// getPhase(j).normalize();
		}
	}

	public void reset_x_y() {

		for (int j = 0; j < numberOfPhases; j++) {
			for (int i = 0; i < numberOfComponents; i++) {
				getPhase(j).getComponents()[i].setx(phaseArray[phaseIndex[0]].getComponents()[i].getz());
			}
		}
	}

	public void reset() {
		for (int i = 0; i < numberOfComponents; i++) {
			addComponent(getPhase(0).getComponent(i).getComponentName(),
					-getPhase(0).getComponent(i).getNumberOfmoles());
		}
	}

	public boolean hasSolidPhase() {
		for (int i = 0; i < numberOfPhases; i++) {
			if (getPhase(i).getPhaseTypeName().equals("solid")) {
				return true;
			}
		}
		return false;
	}

	public void init(int type) { // type = 0 start init type =1 gi nye betingelser
		if (numericDerivatives) {
			initNumeric(type);
		} else {
			initAnalytic(type);
		}
	}

	/**
	 * Calculates thermodynamic properties of a fluid using the init(2) method
	 *
	 */
	public void initThermoProperties() {
		init(2);
	}

	/**
	 * Calculates thermodynamic and physical properties of a fluid using
	 * initThermoProperties() and initPhysicalProperties();
	 *
	 */
	public void initProperties() {
		initThermoProperties();
		initPhysicalProperties();
	}

	public void init(int type, int phase) { // type = 0 start init type =1 gi nye betingelser
		if (numericDerivatives) {
			initNumeric(type, phase);
		} else {
			initAnalytic(type, phase);
		}
	}

	public void init() {
		this.init(initType);
	}

	public void initAnalytic(int type) { // type = 0 start init type =1O gi nye betingelser
		if (type == 0) {
			numberOfPhases = getMaxNumberOfPhases();
			for (int i = 0; i < getMaxNumberOfPhases(); i++) {
				phaseType[i] = 0;
				beta[i] = 1.0;
				phaseIndex[i] = i;
			}
			phaseType[0] = 1;
			for (int i = 0; i < numberOfPhases; i++) {
				if (getPhase(i) == null) {
				} else {
					getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, type, phaseType[phaseIndex[i]],
							beta[phaseIndex[i]]);
				}
			}
			numberOfPhases = 2;
		}

		if (type == 1) {
			for (int i = 0; i < numberOfPhases; i++) {
				getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, 1, phaseType[phaseIndex[i]],
						beta[phaseIndex[i]]);
			}

			for (int i = 0; i < numberOfPhases; i++) {
				for (int j = 0; j < numberOfComponents; j++) {
					getPhase(i).getComponents()[j].fugcoef(getPhase(i));
				}
			}
		}

		if (type == 2) // calculate T and P derivatives
		{
			for (int i = 0; i < numberOfPhases; i++) {
				getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, 2, phaseType[phaseIndex[i]],
						beta[phaseIndex[i]]);
			}

			for (int i = 0; i < numberOfPhases; i++) {
				for (int j = 0; j < numberOfComponents; j++) {
					getPhase(i).getComponents()[j].fugcoef(getPhase(i));
					getPhase(i).getComponents()[j].logfugcoefdT(getPhase(i));
					getPhase(i).getComponents()[j].logfugcoefdP(getPhase(i));
				}
			}
		}

		if (type == 3) // calculate all derivatives
		{
			for (int i = 0; i < numberOfPhases; i++) {
				getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, 3, phaseType[phaseIndex[i]],
						beta[phaseIndex[i]]);
			}

			for (int i = 0; i < numberOfPhases; i++) {
				for (int j = 0; j < numberOfComponents; j++) {
					getPhase(i).getComponents()[j].fugcoef(getPhase(i));
					getPhase(i).getComponents()[j].logfugcoefdT(getPhase(i));
					getPhase(i).getComponents()[j].logfugcoefdP(getPhase(i));
					getPhase(i).getComponents()[j].logfugcoefdN(getPhase(i));
				}
			}
		}

		if (type == 4) // calculate all derivatives numerically
		{
			for (int i = 0; i < numberOfPhases; i++) {
				getPhase(i).init(getTotalNumberOfMoles(), numberOfComponents, 3, phaseType[phaseIndex[i]],
						beta[phaseIndex[i]]);
			}
			for (int i = 0; i < numberOfPhases; i++) {
				for (int j = 0; j < numberOfComponents; j++) {
					getPhase(i).getComponents()[j].fugcoef(getPhase(i));
					getPhase(i).getComponents()[j].fugcoefDiffTempNumeric(getPhase(i), numberOfComponents,
							getPhase(i).getTemperature(), getPhase(i).getPressure());
					getPhase(i).getComponents()[j].fugcoefDiffPresNumeric(getPhase(i), numberOfComponents,
							getPhase(i).getTemperature(), getPhase(i).getPressure());
				}
			}
		}

		for (int i = 1; i < numberOfPhases; i++) {
			if (getPhase(i).getPhaseTypeName().equals("gas")) {
				getPhase(i).setPhaseTypeName("oil");
			}
		}
	}

	public void initAnalytic(int type, int phase) {
		if (type == 0) {
			beta[0] = 1.0;
			phaseIndex[phase] = phase;
			getPhase(phase).init(getTotalNumberOfMoles(), numberOfComponents, 0, phaseType[phaseIndex[phase]],
					beta[phaseIndex[phase]]);
		} else if (type == 1) {
			getPhase(phase).init(getTotalNumberOfMoles(), numberOfComponents, 1, phaseType[phaseIndex[phase]],
					beta[phaseIndex[phase]]);

			for (int j = 0; j < numberOfComponents; j++) {
				getPhase(phase).getComponents()[j].fugcoef(getPhase(phase));
			}
		} else if (type == 2) {
			getPhase(phase).init(getTotalNumberOfMoles(), numberOfComponents, 2, phaseType[phaseIndex[phase]],
					beta[phaseIndex[phase]]);

			for (int j = 0; j < numberOfComponents; j++) {
				getPhase(phase).getComponents()[j].fugcoef(getPhase(phase));
				getPhase(phase).getComponents()[j].logfugcoefdT(getPhase(phase));
				getPhase(phase).getComponents()[j].logfugcoefdP(getPhase(phase));
			}
		} else if (type == 3) {
			getPhase(phase).init(getTotalNumberOfMoles(), numberOfComponents, 3, phaseType[phaseIndex[phase]],
					beta[phaseIndex[phase]]);

			for (int j = 0; j < numberOfComponents; j++) {
				getPhase(phase).getComponents()[j].fugcoef(getPhase(phase));
				getPhase(phase).getComponents()[j].logfugcoefdT(getPhase(phase));
				getPhase(phase).getComponents()[j].logfugcoefdP(getPhase(phase));
				getPhase(phase).getComponents()[j].logfugcoefdN(getPhase(phase));
			}
		}

		for (int i = 1; i < numberOfPhases; i++) {
			if (getPhase(i).getPhaseTypeName().equals("gas")) {
				getPhase(i).setPhaseTypeName("oil");
			}
		}

	}

	public void initNumeric(int type) {
		initNumeric(type, 1);
	}

	public void initNumeric(int type, int phasen) {
		if (type < 2) {
			initAnalytic(type);
		} else if (type >= 2) {
			double[][] gasfug = new double[2][getPhases()[0].getNumberOfComponents()];
			double[][] liqfug = new double[2][getPhases()[0].getNumberOfComponents()];

			double dt = getTemperature() / 1.0e6;
			setTemperature(getTemperature() + dt);
			init(1);

			for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
				gasfug[0][i] = Math.log(getPhases()[0].getComponents()[i].getFugasityCoeffisient());
				liqfug[0][i] = Math.log(getPhases()[1].getComponents()[i].getFugasityCoeffisient());
			}

			setTemperature(getTemperature() - 2 * dt);
			init(1);

			for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
				gasfug[1][i] = Math.log(getPhases()[0].getComponents()[i].getFugasityCoeffisient());
				liqfug[1][i] = Math.log(getPhases()[1].getComponents()[i].getFugasityCoeffisient());
			}

			for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
				getPhase(0).getComponent(i).setdfugdt((gasfug[0][i] - gasfug[1][i]) / (2 * dt));
				getPhase(1).getComponent(i).setdfugdt((liqfug[0][i] - liqfug[1][i]) / (2 * dt));
			}

			setTemperature(getTemperature() + dt);

			double dp = getPressure() / 1.0e6;
			setPressure(getPressure() + dp);
			init(1);

			for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
				gasfug[0][i] = Math.log(getPhases()[0].getComponents()[i].getFugasityCoeffisient());
				liqfug[0][i] = Math.log(getPhases()[1].getComponents()[i].getFugasityCoeffisient());
			}

			setPressure(getPressure() - 2 * dp);
			init(1);

			for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
				gasfug[1][i] = Math.log(getPhases()[0].getComponents()[i].getFugasityCoeffisient());
				liqfug[1][i] = Math.log(getPhases()[1].getComponents()[i].getFugasityCoeffisient());
			}

			for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
				getPhase(0).getComponent(i).setdfugdp((gasfug[0][i] - gasfug[1][i]) / (2 * dp));
				getPhase(1).getComponent(i).setdfugdp((liqfug[0][i] - liqfug[1][i]) / (2 * dp));
			}

			setPressure(getPressure() + dp);
			init(1);

			if (type == 3) {
				for (int phase = 0; phase < 2; phase++) {
					for (int k = 0; k < getPhases()[0].getNumberOfComponents(); k++) {
						double dn = getPhases()[phase].getComponents()[k].getNumberOfMolesInPhase() / 1.0e6;

						addComponent(k, dn, phase);
						// initBeta();
						init_x_y();
						init(1);

						for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
							liqfug[0][i] = Math.log(getPhases()[phase].getComponents()[i].getFugasityCoeffisient());
						}

						addComponent(k, -2.0 * dn, phase);
						// initBeta();
						init_x_y();
						init(1);

						for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
							// gasfug[1][i] =
							// Math.log(getPhases()[0].getComponents()[i].getFugasityCoeffisient());
							liqfug[1][i] = Math.log(getPhases()[phase].getComponents()[i].getFugasityCoeffisient());
						}
						addComponent(k, dn, phase);
						init_x_y();
						init(1);

						for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
							getPhase(phase).getComponent(k).setdfugdn(i, (liqfug[0][i] - liqfug[1][i]) / (2 * dn));
							getPhase(phase).getComponent(k).setdfugdx(i, (liqfug[0][i] - liqfug[1][i]) / (2 * dn)
									* getPhase(phase).getNumberOfMolesInPhase());
						}
						// initBeta();

					}
				}
			}
		}
	}

	public void initNumeric() {
		double[][] gasfug = new double[2][getPhases()[0].getNumberOfComponents()];
		double[][] liqfug = new double[2][getPhases()[0].getNumberOfComponents()];
		double[][] gasnumericDfugdt = new double[2][getPhases()[0].getNumberOfComponents()];
		double[][] liqnumericDfugdt = new double[2][getPhases()[0].getNumberOfComponents()];
		double[][] gasnumericDfugdp = new double[2][getPhases()[0].getNumberOfComponents()];
		double[][] liqnumericDfugdp = new double[2][getPhases()[0].getNumberOfComponents()];
		double[][][] gasnumericDfugdn = new double[2][getPhases()[0].getNumberOfComponents()][getPhases()[0]
				.getNumberOfComponents()];
		double[][][] liqnumericDfugdn = new double[2][getPhases()[0].getNumberOfComponents()][getPhases()[0]
				.getNumberOfComponents()];

		double dt = getTemperature() / 1e5;
		setTemperature(getTemperature() + dt);
		init(1);

		for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
			gasfug[0][i] = Math.log(getPhases()[0].getComponents()[i].getFugasityCoeffisient());
			liqfug[0][i] = Math.log(getPhases()[1].getComponents()[i].getFugasityCoeffisient());
		}

		setTemperature(getTemperature() - 2 * dt);
		init(1);

		for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
			gasfug[1][i] = Math.log(getPhases()[0].getComponents()[i].getFugasityCoeffisient());
			liqfug[1][i] = Math.log(getPhases()[1].getComponents()[i].getFugasityCoeffisient());
			gasnumericDfugdt[0][i] = (gasfug[0][i] - gasfug[1][i]) / (2 * dt);
			liqnumericDfugdt[0][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dt);
			phaseArray[0].getComponents()[i].setdfugdt(gasnumericDfugdt[0][i]);
			phaseArray[1].getComponents()[i].setdfugdt(liqnumericDfugdt[0][i]);
		}

		setTemperature(getTemperature() + dt);

		double dp = getPressure() / 1e5;
		setPressure(getPressure() + dp);
		init(1);

		for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
			gasfug[0][i] = Math.log(getPhases()[0].getComponents()[i].getFugasityCoeffisient());
			liqfug[0][i] = Math.log(getPhases()[1].getComponents()[i].getFugasityCoeffisient());
		}

		setPressure(getPressure() - 2 * dp);
		init(1);

		for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
			gasfug[1][i] = Math.log(getPhases()[0].getComponents()[i].getFugasityCoeffisient());
			liqfug[1][i] = Math.log(getPhases()[1].getComponents()[i].getFugasityCoeffisient());
			gasnumericDfugdp[0][i] = (gasfug[0][i] - gasfug[1][i]) / (2 * dp);
			liqnumericDfugdp[0][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dp);
			phaseArray[0].getComponents()[i].setdfugdp(gasnumericDfugdp[0][i]);
			phaseArray[1].getComponents()[i].setdfugdp(liqnumericDfugdp[0][i]);
		}

		setPressure(getPressure() + dp);
		init(1);

		for (int phase = 0; phase < 2; phase++) {
			for (int k = 0; k < getPhases()[0].getNumberOfComponents(); k++) {
				double dn = getPhases()[phase].getComponents()[k].getNumberOfMolesInPhase() / 1.0e6;
				if (dn < 1e-12) {
					dn = 1e-12;
				}

				addComponent(k, dn, phase);
				// initBeta();
				init_x_y();
				init(1);

				for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
					liqfug[0][i] = Math.log(getPhases()[phase].getComponents()[i].getFugasityCoeffisient());
				}

				addComponent(k, -2.0 * dn, phase);
				// initBeta();
				init_x_y();
				init(1);

				for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
					// gasfug[1][i] =
					// Math.log(getPhases()[0].getComponents()[i].getFugasityCoeffisient());
					liqfug[1][i] = Math.log(getPhases()[phase].getComponents()[i].getFugasityCoeffisient());
				}

				for (int i = 0; i < getPhases()[0].getNumberOfComponents(); i++) {
					if (phase == 0) {
						gasnumericDfugdn[0][k][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dn);
						phaseArray[0].getComponents()[i].setdfugdn(k, gasnumericDfugdn[0][k][i]);
						phaseArray[0].getComponents()[i].setdfugdx(k,
								gasnumericDfugdn[0][k][i] * phaseArray[0].getNumberOfMolesInPhase());
					}

					if (phase == 1) {
						liqnumericDfugdn[0][k][i] = (liqfug[0][i] - liqfug[1][i]) / (2 * dn);
						phaseArray[1].getComponents()[i].setdfugdn(k, liqnumericDfugdn[0][k][i]);
						phaseArray[1].getComponents()[i].setdfugdx(k,
								liqnumericDfugdn[0][k][i] * phaseArray[1].getNumberOfMolesInPhase());
					}
				}

				addComponent(k, dn, phase);
				// initBeta();
				init_x_y();
				init(1);
			}
		}
	}

	public void initPhysicalProperties() {
		for (int i = 0; i < numberOfPhases; i++) {
			getPhase(i).initPhysicalProperties();
		}
		calcInterfaceProperties();
	}

	public void initPhysicalProperties(String propertyName) {
		for (int i = 0; i < numberOfPhases; i++) {
			getPhase(i).initPhysicalProperties(propertyName);
		}
	}

	public void resetPhysicalProperties() {
		for (int i = 0; i < maxNumberOfPhases; i++) {
			getPhase(i).resetPhysicalProperties();
		}
	}

	public void initRefPhases() {
		for (int i = 0; i < numberOfPhases; i++) {
			getPhase(i).initRefPhases(false);
		}
	}

	/**
	 * /** specify the type model for the physical properties you want to use. *
	 * Type: Model * 0 Orginal/default * 1 Water * 2 Glycol * 3 Amine
	 */
	public void setPhysicalPropertyModel(int type) {
		for (int i = 0; i < numberOfPhases; i++) {
			getPhase(i).setPhysicalProperties(type);
		}
	}

	public void chemicalReactionInit() { // type = 0 start init type =1 gi nye betingelser
		chemicalReactionOperations = new ChemicalReactionOperations(this);
		chemicalSystem = chemicalReactionOperations.hasRections();
	}

	public ChemicalReactionOperations getChemicalReactionOperations() { // type = 0 start init type =1 gi nye
																		// betingelser
		return chemicalReactionOperations;
	}

	public final PhaseInterface getGasPhase() {
		for (int phase = 0; phase < numberOfPhases; phase++) {
			if (phaseArray[phaseIndex[phase]].getPhaseType() == 1) {
				return phaseArray[phase];
			}
		}
		logger.info("No gas phase at current state.");
		return null;
	}

	public final PhaseInterface getLiquidPhase() {
		for (int phase = 0; phase < numberOfPhases; phase++) {
			if (phaseArray[phaseIndex[phase]].getPhaseType() == 0) {
				return phaseArray[phase];
			}
		}
		logger.info("No liquid phase at current state.");
		return null;
	}

	public final PhaseInterface getPhase(int i) {
		if (i >= getNumberOfPhases()) {
			// throw new RuntimeException();
		}
		return phaseArray[phaseIndex[i]];
	}

	public final boolean isChemicalSystem() {
		return chemicalSystem;
	}

	public final void isChemicalSystem(boolean temp) {
		chemicalSystem = temp;
	}

	public double getAntoineVaporPressure(double temp) {
		return phaseArray[0].getAntoineVaporPressure(temp);
	}

	public final double getTC() {
		return criticalTemperature;
	}

	public final double getPC() {
		return criticalPressure;
	}

	public final void setTC(double TC) {
		criticalTemperature = TC;
	}

	public final void setPC(double PC) {
		criticalPressure = PC;
	}

	public final void setMixingRule(int type) {
		mixingRule = type;
		if (numberOfPhases < 4) {
			resetPhysicalProperties();// initPhysicalProperties();
		}
		for (int i = 0; i < maxNumberOfPhases; i++) {
			getPhase(i).setMixingRule(type);
			getPhase(i).initPhysicalProperties();
			// getPhase(i).getPhysicalProperties().getMixingRule().initMixingRules(getPhase(i));
		}
	}

	public void setMixingRuleGEmodel(String name) {
		for (int i = 0; i < numberOfPhases; i++) {
			getPhase(i).setMixingRuleGEModel(name);
		}
	}

	public void setMixingRule(String typename, String GEmodel) {
		setMixingRuleGEmodel(GEmodel);
		setMixingRule(typename);
	}

	/**
	 * method to set the mixing rule for the fluid
	 *
	 * @param mixingRuleName the name of the mixing rule. The name can be
	 *                       'no','classic', 'Huron-Vidal'/'HV', 'Huron-Vidal-T',
	 *                       'WS'/'Wong-Sandler' , 'classic-CPA', 'classic-T',
	 *                       'classic-CPA-T', 'classic-Tx'
	 */
	public void setMixingRule(String typename) {
		int var = 0;
		if (typename.equals("no")) {
			var = 1;
		} else if (typename.equals("classic")) {
			var = 2;
		} else if (typename.equals("HV")) {
			var = 4;
		} else if (typename.equals("WS")) {
			var = 5;
		} else if (typename.equals("CPA-Mix")) {
			var = 7;
		} else if (typename.equals("classic-T")) {
			var = 8;
		} else if (typename.equals("classic-T-cpa")) {
			var = 9;
		} else if (typename.equals("classic-Tx-cpa")) {
			var = 10;
		} else {
			var = 1;
		}
		this.setMixingRule(var);
	}

	public String[] getComponentNames() {
		ArrayList components = new ArrayList();

		for (int j = 0; j < numberOfComponents; j++) {
			components.add(phaseArray[0].getComponents()[j].getName());
		}
		String[] componentList = new String[components.size()];
		for (int j = 0; j < numberOfComponents; j++) {
			componentList[j] = (String) components.get(j);
		}
		return componentList;
	}

	public void setNumberOfPhases(int number) {
		this.numberOfPhases = number;
	}

	public void useVolumeCorrection(boolean volcor) {
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			getPhase(i).useVolumeCorrection(volcor);
		}
	}

	public final PhaseInterface[] getPhases() {
		return phaseArray;
	}

	public double getGibbsEnergy() {
		double gibbsEnergy = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			gibbsEnergy += getPhase(i).getGibbsEnergy();
		}
		return gibbsEnergy;
	}

	/**
	 * method to return exergy in a given unit
	 * 
	 * @param temperatureOfSurroundings in Kelvin
	 * @param unit                      The unit as a string. Supported units are J,
	 *                                  J/mol, J/kg and kJ/kg
	 * @return exergy in specified unit
	 */
	public double getExergy(double temperatureOfSurroundings, String exergyUnit) {
		double refExergy = getExergy(temperatureOfSurroundings); // exergy in J
		double conversionFactor = 1.0;
		switch (exergyUnit) {
		case "J":
			conversionFactor = 1.0;
			break;
		case "J/mol":
			conversionFactor = 1.0 / getTotalNumberOfMoles();
			break;
		case "J/kg":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
			break;
		case "kJ/kg":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
			break;
		}
		return refExergy * conversionFactor;
	}

	/**
	 * method to return exergy defined as (h1-T0*s1) in a unit Joule
	 * 
	 * @param temperatureOfSurroundings in Kelvin
	 */
	public double getExergy(double temperatureOfSurroundings) {
		double getExergy = getEnthalpy() - temperatureOfSurroundings * getEntropy();
		return getExergy;
	}

	/**
	 * method to return enthalpy in a unit Joule
	 */
	public double getEnthalpy() {
		double enthalpy = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			enthalpy += getPhase(i).getEnthalpy();
		}
		return enthalpy;
	}

	/**
	 * method to return enthalpy in a given unit
	 *
	 * @param unit The unit as a string. Supported units are J, J/mol, J/kg and
	 *             kJ/kg
	 * @return enthalpy in specified unit
	 */
	public double getEnthalpy(String unit) {
		double refEnthalpy = getEnthalpy(); // enthalpy in J
		double conversionFactor = 1.0;
		switch (unit) {
		case "J":
			conversionFactor = 1.0;
			break;
		case "J/mol":
			conversionFactor = 1.0 / getTotalNumberOfMoles();
			break;
		case "J/kg":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
			break;
		case "kJ/kg":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
			break;
		}
		return refEnthalpy * conversionFactor;
	}

	/**
	 * method to return viscosity
	 *
	 * @return viscosity in unit kg/msec
	 */
	public double getViscosity() {
		double visc = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			visc += beta[phaseIndex[i]] * getPhase(i).getPhysicalProperties().getViscosity();
		}
		return visc;
	}

	/**
	 * method to return viscosity in a given unit
	 *
	 * @param unit The unit as a string. Supported units are kg/msec, cP
	 *             (centipoise)
	 * @return viscosity in specified unit
	 */
	public double getViscosity(String unit) {
		double refViscosity = getViscosity(); // viscosity in kg/msec
		double conversionFactor = 1.0;
		switch (unit) {
		case "kg/msec":
			conversionFactor = 1.0;
			break;
		case "cP":
			conversionFactor = 1.0e3;
			break;
		default:
			throw new RuntimeException();
		}
		return refViscosity * conversionFactor;
	}

	public double getKinematicViscosity() {
		return getViscosity() / getDensity();
	}

	/**
	 * method to return conductivity of a fluid
	 *
	 * @return conductivity in unit W/m*K
	 * 
	 * @deprecated use {@link #getThermalConductivity()} instead.
	 */
	public double getConductivity() {
		double cond = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			cond += beta[phaseIndex[i]] * getPhase(i).getPhysicalProperties().getConductivity();
		}
		return cond;
	}

	/**
	 * method to return conductivity in a given unit
	 *
	 * @param unit The unit as a string. Supported units are W/mK, W/cmK
	 *
	 * @return conductivity in specified unit
	 * 
	 * @deprecated use {@link #getThermalConductivity(String unit)} instead.
	 */
	public double getConductivity(String unit) {
		double refConductivity = getConductivity(); // conductivity in W/m*K
		double conversionFactor = 1.0;
		switch (unit) {
		case "W/mK":
			conversionFactor = 1.0;
			break;
		case "W/cmK":
			conversionFactor = 0.01;
			break;
		default:
			throw new RuntimeException();
		}
		return refConductivity * conversionFactor;
	}

	/**
	 * method to return conductivity of a fluid
	 *
	 * @return conductivity in unit W/m*K
	 */
	public double getThermalConductivity() {
		double cond = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			cond += beta[phaseIndex[i]] * getPhase(i).getPhysicalProperties().getConductivity();
		}
		return cond;
	}

	/**
	 * method to return conductivity in a given unit
	 *
	 * @param unit The unit as a string. Supported units are W/mK, W/cmK
	 *
	 * @return conductivity in specified unit
	 */
	public double getThermalConductivity(String unit) {
		double refConductivity = getConductivity(); // conductivity in W/m*K
		double conversionFactor = 1.0;
		switch (unit) {
		case "W/mK":
			conversionFactor = 1.0;
			break;
		case "W/cmK":
			conversionFactor = 0.01;
			break;
		default:
			throw new RuntimeException();
		}
		return refConductivity * conversionFactor;
	}

	/**
	 * method to return internal energy (U) in unit J
	 */
	public double getInternalEnergy() {
		double internalEnergy = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			internalEnergy += getPhase(i).getInternalEnergy();
		}
		return internalEnergy;
	}

	/**
	 * method to return internal energy (U) in a given unit
	 *
	 * @param unit The unit as a string. unit supported units are J, J/mole, J/kg
	 *             and kJ/kg
	 * @return internal energy in given unit
	 */
	public double getInternalEnergy(String unit) {
		double refEnthalpy = getInternalEnergy(); // enthalpy in J
		double conversionFactor = 1.0;
		switch (unit) {
		case "J":
			conversionFactor = 1.0;
		case "J/mole":
			conversionFactor = 1.0 / getTotalNumberOfMoles();
		case "J/kg":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
		case "kJ/kg":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
		}
		return refEnthalpy * conversionFactor;
	}

	public double getHelmholtzEnergy() {
		double helmholtzEnergy = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			helmholtzEnergy += getPhase(i).getHelmholtzEnergy();
		}
		return helmholtzEnergy;
	}

	public double getEntropy() {
		double entropy = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			entropy += getPhase(i).getEntropy();
		}
		return entropy;
	}

	/**
	 * method to return total entropy of the fluid
	 *
	 * @param unit The unit as a string. Supported units are J/K, J/moleK, J/kgK and
	 *             kJ/kgK
	 * @return entropy in specified unit
	 */
	public double getEntropy(String unit) {
		double refEntropy = getEntropy(); // entropy in J/K
		double conversionFactor = 1.0;
		switch (unit) {
		case "J/K":
			conversionFactor = 1.0;
			break;
		case "J/molK":
			conversionFactor = 1.0 / getTotalNumberOfMoles();
			break;
		case "J/kgK":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
			break;
		case "kJ/kgK":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
			break;
		}
		return refEntropy * conversionFactor;
	}

	/**
	 * method to return molar volume of the fluid note: without Peneloux volume
	 * correction
	 *
	 * @return molar volume volume in unit m3/mol*1e5
	 */
	public double getMolarVolume() {
		double volume = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			volume += beta[phaseIndex[i]] * getPhase(i).getMolarVolume();
		}
		return volume;
	}

	/**
	 * method to get density of a fluid note: without Peneloux volume correction
	 *
	 * @return density with unit kg/m3
	 */
	public double getDensity() {
		double density = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			density += 1.0e5 * (getPhase(i).getMolarMass() * beta[phaseIndex[i]] / getPhase(i).getMolarVolume());
		}
		return density;
	}

	/**
	 * method to get density of a fluid note: with Peneloux volume correction
	 *
	 * @param unit The unit as a string. Supported units are kg/m3, mol/m3
	 * @return density in specified unit
	 */
	public double getDensity(String unit) {
		double density = 0;
		for (int i = 0; i < getNumberOfPhases(); i++) {
			density += getPhase(i).getVolume() / getVolume() * getPhase(i).getPhysicalProperties().getDensity();
		}
		double refDensity = density; // density in kg/m3
		double conversionFactor = 1.0;
		switch (unit) {
		case "kg/m3":
			conversionFactor = 1.0;
			break;
		case "mol/m3":
			conversionFactor = 1.0 / getMolarMass();
			break;
		default:
			throw new RuntimeException();
		}
		return refDensity * conversionFactor;
	}

	public double getZ() {
		double Z = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			Z += beta[phaseIndex[i]] * getPhase(i).getZ();
		}
		return Z;
	}

	public double getMoleFractionsSum() {
		double sumz = 0.0;
		for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
			sumz += phaseArray[0].getComponent(i).getz();
		}
		return sumz;
	}

	/**
	 * method to get molar mass of a fluid phase
	 *
	 * @return molar mass in unit kg/mol
	 */
	public double getMolarMass() {
		double tempVar = 0;
		for (int i = 0; i < phaseArray[0].getNumberOfComponents(); i++) {
			tempVar += phaseArray[0].getComponents()[i].getz() * phaseArray[0].getComponents()[i].getMolarMass();
		}
		return tempVar;
	}

	/**
	 * method to get molar mass of a fluid phase
	 * 
	 * @param unit The unit as a string. Supported units are kg/mol, gr/mol
	 * @return molar mass in given unit
	 */
	public double getMolarMass(String unit) {
		double refMolarMass = getMolarMass();
		double conversionFactor = 1.0;
		switch (unit) {
		case "kg/mol":
			conversionFactor = 1.0;
			break;
		case "gr/mol":
			conversionFactor = 1000.0;
			break;
		default:
			throw new RuntimeException();
		}
		return refMolarMass * conversionFactor;
	}

	/**
	 * method to set the temperature of a fluid (same temperature for all phases)
	 *
	 * @param newTemperature in unit Kelvin
	 */
	public void setTemperature(double newTemperature) {
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			getPhases()[i].setTemperature(newTemperature);
		}
	}

	/**
	 * method to set the temperature of a fluid (same temperature for all phases)
	 *
	 * @param newTemperature in specified unit
	 * @param unit           unit can be C or K (Celcius of Kelvin)
	 */
	public void setTemperature(double newTemperature, String unit) {
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			if (unit.equals("K")) {
				getPhases()[i].setTemperature(newTemperature);
			} else if (unit.equals("C")) {
				getPhases()[i].setTemperature(newTemperature + 273.15);
			} else {
				throw new RuntimeException();
			}
		}
	}

	public double getNumberOfMoles() {
		return getTotalNumberOfMoles();
	}

	/**
	 * method to set the phase type of a given phase
	 *
	 * @param phaseToChange the phase number of the phase to set phase type
	 * @param newPhaseType  the phase type number (valid phase types are 1 (gas) and
	 *                      0 (liquid)
	 */
	public void setPhaseType(int phaseToChange, int newPhaseType) {
		// System.out.println("new phase type: cha " + newPhaseType);
		if (allowPhaseShift) {
			phaseType[phaseIndex[phaseToChange]] = newPhaseType;
		}
	}

	/**
	 * method to set the phase type of a given phase
	 *
	 * @param phaseToChange the phase number of the phase to set phase type
	 * @param phaseTypeName the phase type name (valid names are gas or liquid)
	 */
	public void setPhaseType(int phaseToChange, String phaseTypeName) {
		// System.out.println("new phase type: cha " + newPhaseType);
		int newPhaseType = 1;
		if (allowPhaseShift) {
			if (phaseTypeName.equals("gas") || phaseTypeName.equals("vapour")) {
				newPhaseType = 1;
			} else if (phaseTypeName.equals("liquid") || phaseTypeName.equals("oil")
					|| phaseTypeName.equals("aqueous")) {
				newPhaseType = 0;
			} else {
				newPhaseType = 0;
			}
			phaseType[phaseIndex[phaseToChange]] = newPhaseType;
		}
	}

	public void setPhaseType(String phases, int newPhaseType) {
		// System.out.println("new phase type: cha " + newPhaseType);
		if (allowPhaseShift) {
			if (phases.equals("all")) {
				for (int i = 0; i < getMaxNumberOfPhases(); i++) {
					phaseType[i] = newPhaseType;
				}
			}
		}
	}

	public void invertPhaseTypes() {
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			if (phaseType[i] == 0) {
				phaseType[i] = 1;
			} else {
				phaseType[i] = 0;
			}
		}
	}

	public void setPhase(PhaseInterface phase, int numb) {
		double temp = phaseArray[numb].getTemperature();
		double pres = phaseArray[numb].getPressure();
		this.phaseArray[numb] = phase;
		this.phaseArray[numb].setTemperature(temp);
		this.phaseArray[numb].setPressure(pres);
	}

	public void reInitPhaseType() {
		phaseType[0] = 1;
		phaseType[1] = 0;
		phaseType[2] = 0;
		phaseType[3] = 0;
	}

	public final boolean doSolidPhaseCheck() {
		return solidPhaseCheck;
	}

	/**
	 * method to set the pressure of a fluid (same temperature for all phases)
	 *
	 * @param newPressure in specified unit
	 * @param unit        unit can be C or K (Celcius of Kelvin)
	 */
	public final void setPressure(double newPressure) {
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			phaseArray[i].setPressure(newPressure);
		}
	}

	/**
	 * method to set the pressure of a fluid (same temperature for all phases)
	 *
	 * @param newPressure in specified unit
	 * @param unit        unit can be bar/bara/barg or atm
	 */
	public final void setPressure(double newPressure, String unit) {
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			if (unit.equals("bar") || unit.equals("bara")) {
				phaseArray[i].setPressure(newPressure);
			} else if (unit.equals("atm")) {
				phaseArray[i].setPressure(newPressure + 0.01325);
			} else if (unit.equals("barg")) {
				phaseArray[i].setPressure(newPressure + 1.01325);
			} else {
				throw new RuntimeException(
						"setting new pressure could not be done. Specified unit might not be supported");
			}
		}
	}

	public final void setTemperature(double newPressure, int phase) {
		getPhase(phaseIndex[phase]).setTemperature(newPressure);
	}

	/**
	 * method to return temperature
	 *
	 * @return temperature in unit Kelvin
	 */
	public final double getTemperature() {
		return phaseArray[0].getTemperature();
	}

	/**
	 * method to return temperature in a given unit
	 *
	 * @param unit The unit as a string. Supported units are K, C, R
	 * @return temperature in specified unit
	 */
	public final double getTemperature(String unit) {
		neqsim.util.unit.TemperatureUnit presConversion = new neqsim.util.unit.TemperatureUnit(getTemperature(), "K");
		return presConversion.getValue(unit);
	}

	public double getTemperature(int phaseNumber) {
		return getPhase(phaseIndex[phaseNumber]).getTemperature();
	}

	/**
	 * method to return pressure of a fluid
	 *
	 * @return pressure in unit bara
	 */
	public final double getPressure() {
		return phaseArray[0].getPressure();//
	}

	/**
	 * method to return pressure in a given unit
	 *
	 * @param unit The unit as a string. Supported units are bara, barg, Pa and MPa
	 * @return pressure in specified unit
	 */
	public final double getPressure(String unit) {
		neqsim.util.unit.PressureUnit presConversion = new neqsim.util.unit.PressureUnit(getPressure(), "bara");
		return presConversion.getValue(unit);
	}

	/**
	 * method to return pressure of phase
	 *
	 * @return pressure of phase in unit bara
	 */
	public final double getPressure(int phaseNumber) {
		return getPhase(phaseIndex[phaseNumber]).getPressure();
	}

	public final double getBeta() {
		return beta[0];
	}

	public final double getBeta(int phase) {
		return beta[phaseIndex[phase]];
	}

	public void setAtractiveTerm(int i) {
		for (int k = 0; k < getMaxNumberOfPhases(); k++) {
			phaseArray[k].setAtractiveTerm(i);
		}
	}

	public final int getNumberOfPhases() {
		return numberOfPhases;
	}

	public final void setBeta(double b) {
		if (b < 0)
			b = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
		if (b > 1)
			b = 1.0 - neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
		beta[0] = b;
		beta[1] = 1.0 - b;
	}

	public final void setBeta(int phase, double b) {
		if (b < 0)
			b = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
		if (b > 1)
			b = 1.0 - neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
		beta[phaseIndex[phase]] = b;
	}

	/**
	 * method to return fluid volume
	 *
	 * @return volume in unit m3*1e5
	 */
	public final double getVolume() {
		double volume = 0.0;
		for (int i = 0; i < numberOfPhases; i++) {
			volume += getPhase(i).getMolarVolume() * getPhase(i).getNumberOfMolesInPhase();
		}
		return volume;
	}

	/**
	 * method to return fluid volume
	 *
	 * @param unit The unit as a string. Supported units are m3, litre, m3/kg,
	 *             m3/mol
	 *
	 * @return volume in specified unit
	 */
	public double getVolume(String unit) {
		double conversionFactor = 1.0;
		switch (unit) {
		case "m3":
			conversionFactor = 1.0;
			break;
		case "m3/kg":
			conversionFactor = 1.0 / getMass("kg");
			break;
		case "litre":
			conversionFactor = 1000.0;
			break;
		case "m3/mol":
			conversionFactor = 1.0 / getTotalNumberOfMoles();
			break;
		}
		return conversionFactor * getVolume() / 1.0e5;
	}

	/**
	 * method to return mass of fluid
	 *
	 * @param unit The unit as a string. Supported units are kg, gr, tons
	 *
	 * @return volume in specified unit
	 */
	public double getMass(String unit) {
		double conversionFactor = 1.0;
		switch (unit) {
		case "kg":
			conversionFactor = 1.0;
			break;

		case "gr":
			conversionFactor = 1000.0;
			break;
		case "tons":
			conversionFactor = 1.0e-3;
			break;
		}
		return conversionFactor * getTotalNumberOfMoles() * getMolarMass();
	}

	/**
	 * method to return fluid volume with Peneloux volume correction need to call
	 * initPhysicalProperties() before this method is called
	 *
	 * @return volume in specified unit
	 */
	public double getCorrectedVolume() {
		double volume = 0;
		for (int i = 0; i < numberOfPhases; i++) {
			volume += getPhase(i).getMolarMass() / getPhase(i).getPhysicalProperties().getDensity()
					* getPhase(i).getNumberOfMolesInPhase();
		}
		return volume;
	}

	public double getdVdPtn() {
		double dVdP = 0.0;
		for (int i = 0; i < numberOfPhases; i++) {
			dVdP += 1.0 / getPhase(i).getdPdVTn();
		}
		return dVdP;
	}

	public double getdVdTpn() {
		double dVdT = 0.0;
		for (int i = 0; i < numberOfPhases; i++) {
			dVdT += -getPhase(i).getdPdTVn() / getPhase(i).getdPdVTn();
		}
		return dVdT;
	}

	/**
	 * method to return specific heat capacity (Cp)
	 *
	 * @return Cp in unit J/K
	 */
	public double getCp() {
		double cP = 0.0;
		for (int i = 0; i < numberOfPhases; i++) {
			cP += getPhase(i).getCp();
		}
		return cP;
	}

	/**
	 * method to return specific heat capacity (Cp) in a given unit
	 *
	 * @param unit The unit as a string. Supported units are J/K, J/molK, J/kgK and
	 *             kJ/kgK
	 * @return Cp in specified unit
	 */
	public double getCp(String unit) {
		double refCp = getCp(); // Cp in J/K
		double conversionFactor = 1.0;
		switch (unit) {
		case "J/K":
			conversionFactor = 1.0;
			break;
		case "J/molK":
			conversionFactor = 1.0 / getTotalNumberOfMoles();
			break;
		case "J/kgK":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
			break;
		case "kJ/kgK":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
			break;
		}
		return refCp * conversionFactor;
	}

	/**
	 * method to return specific heat capacity (Cv)
	 *
	 * @return Cv in unit J/K
	 */
	public double getCv() {
		double cv = 0.0;
		for (int i = 0; i < numberOfPhases; i++) {
			cv += getPhase(i).getCv();
		}
		return cv;
	}

	/**
	 * method to return specific heat capacity (Cv) in a given unit
	 *
	 * @param unit The unit as a string. Supported units are J/K, J/molK, J/kgK and
	 *             kJ/kgK
	 * @return Cv in specified unit
	 */
	public double getCv(String unit) {
		double refCv = getCv(); // enthalpy in J
		double conversionFactor = 1.0;
		switch (unit) {
		case "J/K":
			conversionFactor = 1.0;
			break;
		case "J/molK":
			conversionFactor = 1.0 / getTotalNumberOfMoles();
			break;
		case "J/kgK":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass();
			break;
		case "kJ/kgK":
			conversionFactor = 1.0 / getTotalNumberOfMoles() / getMolarMass() / 1000.0;
			break;
		}
		return refCv * conversionFactor;
	}

	/**
	 * method to return heat capacity ratio/adiabatic index/Poisson constant
	 *
	 * @return kappa
	 */
	public double getKappa() {
		return getCp() / getCv();
	}

	/**
	 * method to return heat capacity ratio/adiabatic index/Poisson constant
	 *
	 * @return kappa
	 */
	public double getGamma() {
		return getCp() / getCv();
	}

	/**
	 * method to return heat capacity ratio calculated as Cp/(Cp-R)
	 *
	 * @return kappa
	 */
	public double getGamma2() {
		double cp0 = getCp();
		return cp0 / (cp0 - ThermodynamicConstantsInterface.R * totalNumberOfMoles);
	}

	public void calcInterfaceProperties() {
		interfaceProp.init();
	}

	public InterphasePropertiesInterface getInterphaseProperties() {
		return interfaceProp;
	}

	/**
	 * method to return interfacial tension between two phases
	 *
	 * @param phase1 phase number of phase1
	 * @param phase2 phase number of phase2
	 * @return interfacial tension with unit N/m
	 */
	public double getInterfacialTension(int phase1, int phase2) {
		return interfaceProp.getSurfaceTension(phase1, phase2);
	}

	public double getInterfacialTension(int phase1, int phase2, String unit) {
		return interfaceProp.getSurfaceTension(phase1, phase2, unit);
	}

	/**
	 * method to return interfacial tension between two phases
	 *
	 * @param phase1 phase type of phase1 as string (valid phases are gas, oil,
	 *               aqueous)
	 * @param phase2 phase type of phase2 as string (valid phases are gas, oil,
	 *               aqueous)
	 * @return interfacial tension with unit N/m. If one or both phases does not
	 *         exist - the method will return NaN
	 */
	public double getInterfacialTension(String phase1, String phase2) {
		if (hasPhaseType(phase1) && hasPhaseType(phase2)) {
			return interfaceProp.getSurfaceTension(getPhaseNumberOfPhase(phase1), getPhaseNumberOfPhase(phase2));
		} else {
			return Double.NaN;
		}
	}

	public String write() {
		// create a String description of the system
		return "";
	}

	public void normalizeBeta() {
		double tot = 0.0;
		for (int i = 0; i < numberOfPhases; i++) {
			tot += beta[phaseIndex[i]];
		}
		for (int i = 0; i < numberOfPhases; i++) {
			beta[phaseIndex[i]] /= tot;
		}
	}

	public void display() {
		display(this.getFluidName());
	}

	public String[][] createTable(String name) {
		// System.out.println("number of comps : " + numberOfComponents + " number of
		// phases " + numberOfPhases);
		String[][] table = new String[getPhases()[0].getNumberOfComponents() + 30][7];

		initProperties();

		java.text.DecimalFormat nf = new java.text.DecimalFormat();

		java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		nf.setDecimalFormatSymbols(symbols);

		nf.setMaximumFractionDigits(5);
		nf.applyPattern("#.#####E0");

		/// String[][] table = new String[getPhases()[0].getNumberOfComponents() +
		/// 30][7];
		String[] names = { "", "Feed", "Phase 1", "Phase 2", "Phase 3", "Phase 4", "Unit" };
		table[0][0] = "";// getPhases()[0].getPhaseTypeName();//"";

		for (int i = 0; i < getPhases()[0].getNumberOfComponents() + 30; i++) {
			for (int j = 0; j < 7; j++) {
				table[i][j] = "";
			}
		}
		table[0][1] = "total";
		for (int i = 0; i < numberOfPhases; i++) {
			table[0][i + 2] = getPhase(i).getPhaseTypeName();
		}

		StringBuffer buf = new StringBuffer();
		FieldPosition test = new FieldPosition(0);
		for (int j = 0; j < getPhases()[0].getNumberOfComponents(); j++) {
			buf = new StringBuffer();
			table[j + 1][1] = nf.format(getPhase(0).getComponents()[j].getz(), buf, test).toString();
		}
		buf = new StringBuffer();
		table[getPhases()[0].getNumberOfComponents() + 4][1] = nf.format(getMolarMass() * 1000, buf, test).toString();
		buf = new StringBuffer();
		table[getPhases()[0].getNumberOfComponents() + 9][1] = nf
				.format(getEnthalpy() / (getTotalNumberOfMoles() * getMolarMass() * 1000), buf, test).toString();
		buf = new StringBuffer();
		table[getPhases()[0].getNumberOfComponents() + 10][1] = nf
				.format(getEntropy() / (getTotalNumberOfMoles() * getMolarMass() * 1000), buf, test).toString();

		for (int i = 0; i < numberOfPhases; i++) {
			for (int j = 0; j < getPhases()[0].getNumberOfComponents(); j++) {
				table[j + 1][0] = getPhases()[0].getComponents()[j].getName();
				buf = new StringBuffer();
				table[j + 1][i + 2] = nf.format(getPhase(i).getComponents()[j].getx(), buf, test).toString();
				table[j + 1][6] = "[mole fraction]";
			}

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 2][0] = "Density";
			table[getPhases()[0].getNumberOfComponents() + 2][i + 2] = nf
					.format(getPhase(i).getPhysicalProperties().getDensity(), buf, test).toString();
			table[getPhases()[0].getNumberOfComponents() + 2][6] = "[kg/m^3]";

			// Double.longValue(system.getPhase(i).getBeta());
			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 3][0] = "PhaseFraction";
			table[getPhases()[0].getNumberOfComponents() + 3][i + 2] = nf.format(getPhase(i).getBeta(), buf, test)
					.toString();
			table[getPhases()[0].getNumberOfComponents() + 3][6] = "[mole fraction]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 4][0] = "MolarMass";
			table[getPhases()[0].getNumberOfComponents() + 4][i + 2] = nf
					.format(getPhase(i).getMolarMass() * 1000, buf, test).toString();
			table[getPhases()[0].getNumberOfComponents() + 4][6] = "[kg/kmol]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 5][0] = "Z factor";
			table[getPhases()[0].getNumberOfComponents() + 5][i + 2] = nf.format(getPhase(i).getZ(), buf, test)
					.toString();
			table[getPhases()[0].getNumberOfComponents() + 5][6] = "[-]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 6][0] = "Heat Capacity (Cp)";
			table[getPhases()[0].getNumberOfComponents() + 6][i + 2] = nf
					.format((getPhase(i).getCp()
							/ (getPhase(i).getNumberOfMolesInPhase() * getPhase(i).getMolarMass() * 1000)), buf, test)
					.toString();
			table[getPhases()[0].getNumberOfComponents() + 6][6] = "[kJ/kg*K]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 7][0] = "Heat Capacity (Cv)";
			table[getPhases()[0].getNumberOfComponents() + 7][i + 2] = nf
					.format((getPhase(i).getCv()
							/ (getPhase(i).getNumberOfMolesInPhase() * getPhase(i).getMolarMass() * 1000)), buf, test)
					.toString();
			table[getPhases()[0].getNumberOfComponents() + 7][6] = "[kJ/kg*K]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 8][0] = "Speed of Sound";
			table[getPhases()[0].getNumberOfComponents() + 8][i + 2] = nf
					.format((getPhase(i).getSoundSpeed()), buf, test).toString();
			table[getPhases()[0].getNumberOfComponents() + 8][6] = "[m/sec]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 9][0] = "Enthalpy";
			table[getPhases()[0].getNumberOfComponents() + 9][i + 2] = nf
					.format((getPhase(i).getEnthalpy()
							/ (getPhase(i).getNumberOfMolesInPhase() * getPhase(i).getMolarMass() * 1000)), buf, test)
					.toString();
			table[getPhases()[0].getNumberOfComponents() + 9][6] = "[kJ/kg]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 10][0] = "Entropy";
			table[getPhases()[0].getNumberOfComponents() + 10][i + 2] = nf
					.format((getPhase(i).getEntropy()
							/ (getPhase(i).getNumberOfMolesInPhase() * getPhase(i).getMolarMass() * 1000)), buf, test)
					.toString();
			table[getPhases()[0].getNumberOfComponents() + 10][6] = "[kJ/kg*K]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 11][0] = "JT coefficient";
			table[getPhases()[0].getNumberOfComponents() + 11][i + 2] = nf
					.format((getPhase(i).getJouleThomsonCoefficient()), buf, test).toString();
			table[getPhases()[0].getNumberOfComponents() + 11][6] = "[K/bar]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 13][0] = "Viscosity";
			table[getPhases()[0].getNumberOfComponents() + 13][i + 2] = nf
					.format((getPhase(i).getPhysicalProperties().getViscosity()), buf, test).toString();
			table[getPhases()[0].getNumberOfComponents() + 13][6] = "[kg/m*sec]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 14][0] = "Conductivity";
			table[getPhases()[0].getNumberOfComponents() + 14][i + 2] = nf
					.format(getPhase(i).getPhysicalProperties().getConductivity(), buf, test).toString();
			table[getPhases()[0].getNumberOfComponents() + 14][6] = "[W/m*K]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 15][0] = "SurfaceTension";
			try {
				if (i < numberOfPhases - 1) {
					table[getPhases()[0].getNumberOfComponents() + 15][2] = nf
							.format(getInterphaseProperties().getSurfaceTension(0, 1), buf, test).toString();
					buf = new StringBuffer();
					table[getPhases()[0].getNumberOfComponents() + 15][3] = nf
							.format(getInterphaseProperties().getSurfaceTension(0, 1), buf, test).toString();
					buf = new StringBuffer();
					if (i == 1) {
						table[getPhases()[0].getNumberOfComponents() + 17][2] = nf
								.format(getInterphaseProperties().getSurfaceTension(0, 2), buf, test).toString();
						buf = new StringBuffer();
						table[getPhases()[0].getNumberOfComponents() + 17][4] = nf
								.format(getInterphaseProperties().getSurfaceTension(0, 2), buf, test).toString();
						table[getPhases()[0].getNumberOfComponents() + 17][6] = "[N/m]";
					}
					if (i == 1) {
						buf = new StringBuffer();
						table[getPhases()[0].getNumberOfComponents() + 16][3] = nf
								.format(getInterphaseProperties().getSurfaceTension(1, 2), buf, test).toString();
						buf = new StringBuffer();
						table[getPhases()[0].getNumberOfComponents() + 16][4] = nf
								.format(getInterphaseProperties().getSurfaceTension(1, 2), buf, test).toString();
						table[getPhases()[0].getNumberOfComponents() + 16][6] = "[N/m]";
					}
				}
			} catch (Exception e) {
				logger.error("error", e);
			}
			table[getPhases()[0].getNumberOfComponents() + 15][6] = "[N/m]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 19][0] = "Pressure";
			table[getPhases()[0].getNumberOfComponents() + 19][i + 2] = Double.toString(getPhase(i).getPressure());
			table[getPhases()[0].getNumberOfComponents() + 19][6] = "[bar]";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 20][0] = "Temperature";
			table[getPhases()[0].getNumberOfComponents() + 20][i + 2] = Double.toString(getPhase(i).getTemperature());
			table[getPhases()[0].getNumberOfComponents() + 20][6] = "[K]";
			Double.toString(getPhase(i).getTemperature());

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 22][0] = "Model";
			table[getPhases()[0].getNumberOfComponents() + 22][i + 2] = getModelName();
			table[getPhases()[0].getNumberOfComponents() + 22][6] = "-";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 23][0] = "Mixing Rule";
			try {
				table[getPhases()[0].getNumberOfComponents() + 23][i + 2] = ((PhaseEosInterface) getPhase(i))
						.getMixingRuleName();
			} catch (Exception e) {
				table[getPhases()[0].getNumberOfComponents() + 23][i + 2] = "?";
				// logger.error("error",e);
			}
			table[getPhases()[0].getNumberOfComponents() + 23][6] = "-";

			buf = new StringBuffer();
			table[getPhases()[0].getNumberOfComponents() + 25][0] = "Stream";
			table[getPhases()[0].getNumberOfComponents() + 25][i + 2] = name;
			table[getPhases()[0].getNumberOfComponents() + 25][6] = "-";
		}
		resultTable = table;

		return table;
	}

	public void display(String name) {
		JFrame dialog = new JFrame("System-Report");
		Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
		Container dialogContentPane = dialog.getContentPane();
		dialogContentPane.setLayout(new BorderLayout());

		String[] names = { "", "Feed", "Phase 1", "Phase 2", "Phase 3", "Phase 4", "Unit" };
		String[][] table = createTable(name);
		JTable Jtab = new JTable(table, names);
		JScrollPane scrollpane = new JScrollPane(Jtab);
		dialogContentPane.add(scrollpane);

		// setting the size of the frame and text size
		dialog.setSize(screenDimension.width / 2, screenDimension.height / 2); // pack();
		Jtab.setRowHeight(dialog.getHeight() / table.length);
		Jtab.setFont(new Font("Serif", Font.PLAIN,
				dialog.getHeight() / table.length - dialog.getHeight() / table.length / 10));

		// dialog.pack();
		dialog.setVisible(true);
	}

	public void write(String name, String filename, boolean newfile) {
		String[][] table = createTable(name);
		neqsim.dataPresentation.fileHandeling.createTextFile.TextFile file = new neqsim.dataPresentation.fileHandeling.createTextFile.TextFile();
		if (newfile) {
			file.newFile(filename);
		}
		file.setOutputFileName(filename);
		file.setValues(table);
		file.createFile();
	}

	public void resetDatabase() {
		neqsim.util.database.NeqSimDataBase database = null;
		try {
			database = new neqsim.util.database.NeqSimDataBase();
			if (database.createTemporaryTables()) {
				database.execute("delete FROM comptemp");
				database.execute("delete FROM intertemp");
			}
		} catch (Exception e) {
			logger.error("error in SystemThermo Class...resetDatabase() method");
			logger.error("error in comp");
			logger.error("error", e);
		} finally {
			try {
				if (database.getStatement() != null) {
					database.getStatement().close();
				}
				if (database.getConnection() != null) {
					database.getConnection().close();
				}
			} catch (Exception e) {
				logger.error("error closing database.....", e);
			}
		}
	}

	public void createDatabase(boolean reset) {
		neqsim.util.database.NeqSimDataBase database = null;
		try {
			if (reset) {
				resetDatabase();
			}
			database = new neqsim.util.database.NeqSimDataBase();
			String names = new String();

			for (int k = 0; k < getPhase(0).getNumberOfComponents() - 1; k++) {
				names += "'" + this.getComponentNames()[k] + "', ";

			}
			names += "'" + this.getComponentNames()[getPhase(0).getNumberOfComponents() - 1] + "'";

			if (database.createTemporaryTables()) {
				database.execute("insert into comptemp SELECT * FROM comp WHERE name IN (" + names + ")");
				database.execute("insert into intertemp SELECT DISTINCT * FROM inter WHERE comp1 IN (" + names
						+ ") AND comp2 IN (" + names + ")");
				database.execute("delete FROM intertemp WHERE comp1=comp2");
			}
			// System.out.println("ok " + names);

			for (int phase = 0; phase < maxNumberOfPhases; phase++) {
				getPhase(phase).setMixingRuleDefined(false);
			}

			for (int i = 0; i < numberOfComponents; i++) {
				if (getPhase(0).getComponent(i).isIsTBPfraction() || getPhase(0).getComponent(i).isIsPlusFraction()) {
					getPhase(0).getComponent(i).insertComponentIntoDatabase("");
				}
			}
		} catch (Exception e) {
			logger.error("error in SystemThermo Class...createDatabase() method", e);
		} finally {
			try {
				if (database.getStatement() != null) {
					database.getStatement().close();
				}
				if (database.getConnection() != null) {
					database.getConnection().close();
				}
			} catch (Exception e) {
				logger.error("error closing database.....", e);
			}

		}
	}

	/**
	 * Indexed getter for property phaseIndex.
	 *
	 * @param index Index of the property.
	 * @return Value of the property at <CODE>index</CODE>.
	 */
	public final int getPhaseIndex(int index) {
		return phaseIndex[index];
	}

	/**
	 * Indexed setter for property phaseIndex.
	 *
	 * @param index      Index of the property.
	 * @param phaseIndex New value of the property at <CODE>index</CODE>.
	 */
	public final void setPhaseIndex(int index, int phaseIndex) {
		this.phaseIndex[index] = phaseIndex;
	}

	/**
	 * Setter for property solidPhaseCheck.
	 *
	 * @param solidPhaseCheck New value of property solidPhaseCheck.
	 */
	public void setSolidPhaseCheck(boolean solidPhaseCheck) {
		// init(0);
		int oldphase = numberOfPhases;
		if (!this.solidPhaseCheck) {
			addSolidPhase();
		}
		this.solidPhaseCheck = solidPhaseCheck;
		// init(0);

		for (int phase = 0; phase < numberOfPhases; phase++) {
			for (int k = 0; k < getPhases()[0].getNumberOfComponents(); k++) {
				getPhase(phase).getComponent(k).setSolidCheck(solidPhaseCheck);
				getPhase(3).getComponent(k).setSolidCheck(solidPhaseCheck);
			}
		}
		numberOfPhases = oldphase;
	}

	public void setSolidPhaseCheck(String solidComponent) {
		init(0);
		int oldphase = numberOfPhases;
		if (!solidPhaseCheck) {
			addSolidPhase();
		}
		this.solidPhaseCheck = true;
		init(0);

		for (int phase = 0; phase < getMaxNumberOfPhases(); phase++) {
			try {
				getPhase(phase).getComponent(solidComponent).setSolidCheck(true);
				getPhase(3).getComponent(solidComponent).setSolidCheck(true);
			} catch (Exception e) {
				logger.error("error", e);
			}
		}
		numberOfPhases = oldphase;
	}

	public void setHydrateCheck(boolean hydrateCheck) {
		init(0);
		if (hydrateCheck) {
			addHydratePhase();
		}
		this.hydrateCheck = hydrateCheck;
		init(0);
	}

	/**
	 * Getter for property multiPhaseCheck.
	 *
	 * @return Value of property multiPhaseCheck.
	 */
	public boolean doMultiPhaseCheck() {
		return multiPhaseCheck;
	}

	/**
	 * Setter for property multiPhaseCheck.
	 *
	 * @param multiPhaseCheck New value of property multiPhaseCheck.
	 */
	public void setMultiPhaseCheck(boolean multiPhaseCheck) {

		if (getMaxNumberOfPhases() < 3) {
			setMaxNumberOfPhases(3);

			if (multiPhaseCheck) {
				phaseArray[2] = (PhaseInterface) phaseArray[1].clone();
				phaseArray[2].resetMixingRule(phaseArray[0].getMixingRuleNumber());
				phaseArray[2].resetPhysicalProperties();
				phaseArray[2].initPhysicalProperties();
			}
		}
		this.multiPhaseCheck = multiPhaseCheck;
	}

	/**
	 * Getter for property initType.
	 *
	 * @return Value of property initType.
	 */
	public int getInitType() {
		return initType;
	}

	/**
	 * Setter for property initType.
	 *
	 * @param initType New value of property initType.
	 */
	public void setInitType(int initType) {
		this.initType = initType;
	}

	/**
	 * Getter for property numericDerivatives.
	 *
	 * @return Value of property numericDerivatives.
	 */
	public boolean isNumericDerivatives() {
		return numericDerivatives;
	}

	/**
	 * Setter for property numericDerivatives.
	 *
	 * @param numericDerivatives New value of property numericDerivatives.
	 */
	public void setNumericDerivatives(boolean numericDerivatives) {
		this.numericDerivatives = numericDerivatives;
	}

	public void checkStability(boolean val) {
		checkStability = val;
	}

	public boolean checkStability() {
		return checkStability;
	}

	/**
	 * Getter for property hydrateCheck.
	 *
	 * @return Value of property hydrateCheck.
	 */
	public boolean doHydrateCheck() {
		return hydrateCheck;
	}

	public void save(String name) {
		FileOutputStream fout = null;
		ObjectOutputStream out = null;
		try {
			fout = new FileOutputStream(name);
			out = new ObjectOutputStream(fout);
			out.writeObject(this);
			out.close();

		} catch (Exception e) {
			logger.error(e.toString());
		}
	}

	public SystemInterface readObject(int ID) {
		ResultSet rs = null;
		SystemThermo tempSystem = null;
		neqsim.util.database.NeqSimBlobDatabase database = new neqsim.util.database.NeqSimBlobDatabase();
		try {
			java.sql.Connection con = database.openConnection();
			String sqlStr = "SELECT FLUID FROM fluid_blobdb WHERE ID=" + Integer.toString(ID);
			java.sql.PreparedStatement ps = con.prepareStatement(sqlStr);
			rs = ps.executeQuery();

			if (rs.next()) {
				ByteArrayInputStream bais;
				ObjectInputStream ins;
				bais = new ByteArrayInputStream(rs.getBytes("FLUID"));
				ins = new ObjectInputStream(bais);

				tempSystem = (SystemThermo) ins.readObject();
				ins.close();
			}
		} catch (Exception e) {
			logger.error("error", e);
		} finally {
			try {
				if (database.getStatement() != null) {
					database.getStatement().close();
				}
				if (database.getConnection() != null) {
					database.getConnection().close();
				}
			} catch (Exception e) {
				logger.error("err closing database IN MIX..., e");
				logger.error("error", e);
			}
		}

		return tempSystem;
	}

	public void saveFluid(int ID) {
		saveObject(ID, "");
	}

	public void saveFluid(int ID, String text) {
		saveObject(ID, text);
	}

	public void saveObject(int ID, String text) {
		ByteArrayOutputStream fout = new ByteArrayOutputStream();
		ObjectOutputStream out = null;
		InputStream in = null;
		try {
			out = new ObjectOutputStream(fout);
			out.writeObject(this);
			out.close();

		} catch (Exception e) {
			logger.error(e.toString());
		}
		byte[] byteObject = fout.toByteArray();
		ByteArrayInputStream inpStream = new ByteArrayInputStream(byteObject);

		neqsim.util.database.NeqSimBlobDatabase database = new neqsim.util.database.NeqSimBlobDatabase();

		try {
			java.sql.Connection con = database.openConnection();

			java.sql.PreparedStatement ps = con.prepareStatement("REPLACE INTO fluid_blobdb (ID, FLUID) VALUES (?,?)");
			ps.setInt(1, ID);
			ps.setBlob(2, inpStream);

			ps.executeUpdate();
			/*
			 * if (!text.isEmpty()) { ps = con.prepareStatement(
			 * "REPLACE INTO fluidinfo (ID, TEXT) VALUES (?,?)"); ps.setInt(1, ID);
			 * ps.setString(2, text); }
			 * 
			 * ps.executeUpdate();
			 * 
			 */
		} catch (Exception e) {
			logger.error("error", e);
		} finally {
			try {
				if (database.getStatement() != null) {
					database.getStatement().close();
				}
				if (database.getConnection() != null) {
					database.getConnection().close();
				}
			} catch (Exception e) {
				logger.error("err closing database IN MIX...", e);
			}
		}
		// database.execute("INSERT INTO fluid_blobdb VALUES ('1'," + sqlString + ")");
	}

	public void saveObjectToFile(String filePath, String fluidName) {

		ObjectOutputStream out = null;
		InputStream in = null;
		try {
			FileOutputStream fout = new FileOutputStream(filePath, false);
			out = new ObjectOutputStream(fout);
			out.writeObject(this);
			out.close();

		} catch (Exception e) {
			logger.error(e.toString());
		}
		// database.execute("INSERT INTO fluid_blobdb VALUES ('1'," + sqlString + ")");
	}

	public SystemInterface readObjectFromFile(String filePath, String fluidName) {

		FileInputStream streamIn = null;
		InputStream in = null;
		SystemThermo tempSystem = null;
		try {
			streamIn = new FileInputStream(filePath);
			ObjectInputStream objectinputstream = new ObjectInputStream(streamIn);
			tempSystem = (SystemThermo) objectinputstream.readObject();

		} catch (Exception e) {
			logger.error(e.toString());
		}
		return tempSystem;
	}

	public java.lang.String getMixingRuleName() {
		return ((PhaseEosInterface) getPhase(0)).getMixingRule().getMixingRuleName();
	}

	/**
	 * Getter for property info.
	 *
	 * @return Value of property info.
	 */
	public java.lang.String getFluidInfo() {
		return fluidInfo;
	}

	/**
	 * Setter for property info.
	 *
	 * @param info New value of property info.
	 */
	public void setFluidInfo(java.lang.String info) {
		this.fluidInfo = fluidInfo;
	}

	/**
	 * Getter for property fluidName.
	 *
	 * @return Value of property fluidName.
	 */
	public java.lang.String getFluidName() {
		return fluidName;
	}

	/**
	 * Setter for property fluidName.
	 *
	 * @param fluidName New value of property fluidName.
	 */
	public void setFluidName(java.lang.String fluidName) {
		this.fluidName = fluidName;
	}

	public boolean setLastTBPasPlus() {
		neqsim.thermo.characterization.PlusCharacterize temp = new neqsim.thermo.characterization.PlusCharacterize(
				this);
		if (temp.hasPlusFraction()) {
			return false;
		} else {
			temp.setHeavyTBPtoPlus();
		}
		return true;
	}

	/**
	 * Getter for property characterization.
	 *
	 * @return Value of property characterization.
	 */
	public neqsim.thermo.characterization.Characterise getCharacterization() {
		return characterization;
	}

	public void calcKIJ(boolean ok) {
		neqsim.thermo.mixingRule.EosMixingRules.calcEOSInteractionParameters = ok;
		for (int i = 0; i < numberOfPhases; i++) {
			((PhaseEosInterface) getPhase(i)).getMixingRule().setCalcEOSInteractionParameters(ok);
		}
	}

	/**
	 * Getter for property modelName.
	 *
	 * @return Value of property modelName.
	 */
	public java.lang.String getModelName() {
		return modelName;
	}

	/**
	 * Setter for property modelName.
	 *
	 * @param modelName New value of property modelName.
	 */
	public void setModelName(java.lang.String modelName) {
		this.modelName = modelName;
	}

	/**
	 * Getter for property allowPhaseShift.
	 *
	 * @return Value of property allowPhaseShift.
	 *
	 */
	public boolean allowPhaseShift() {
		return allowPhaseShift;
	}

	/**
	 * Setter for property allowPhaseShift.
	 *
	 * @param allowPhaseShift New value of property allowPhaseShift.
	 *
	 */
	public void allowPhaseShift(boolean allowPhaseShift) {
		this.allowPhaseShift = allowPhaseShift;
	}

	public double getProperty(String prop, String compName, int phase) {
		if (prop.equals("molefraction")) {
			return getPhase(phase).getComponent(compName).getx();
		} else if (prop.equals("fugacitycoefficient")) {
			return getPhase(phase).getComponent(compName).getFugasityCoefficient();
		} else if (prop.equals("logfugdT")) {
			return getPhase(phase).getComponent(compName).getdfugdt();
		} else if (prop.equals("logfugdP")) {
			return getPhase(phase).getComponent(compName).getdfugdp();
		} else {
			return 1.0;
		}
	}

	public double getProperty(String prop, int phase) {
		initPhysicalProperties();
		if (prop.equals("temperature")) {
			return getPhase(phase).getTemperature();
		} else if (prop.equals("pressure")) {
			return getPhase(phase).getPressure();
		} else if (prop.equals("compressibility")) {
			return getPhase(phase).getZ();
		} else if (prop.equals("density")) {
			return getPhase(phase).getPhysicalProperties().getDensity();
		} else if (prop.equals("beta")) {
			return getPhase(phase).getBeta();
		} else if (prop.equals("enthalpy")) {
			return getPhase(phase).getEnthalpy();
		} else if (prop.equals("entropy")) {
			return getPhase(phase).getEntropy();
		} else if (prop.equals("viscosity")) {
			return getPhase(phase).getPhysicalProperties().getViscosity();
		} else if (prop.equals("conductivity")) {
			return getPhase(phase).getPhysicalProperties().getConductivity();
		} else {
			return 1.0;
		}
	}

	public double getProperty(String prop) {
		if (prop.equals("numberOfPhases")) {
			return numberOfPhases;
		} else if (prop.equals("numberOfComponents")) {
			return numberOfComponents;
		} else if (prop.equals("enthalpy")) {
			return getEnthalpy();
		} else if (prop.equals("entropy")) {
			return getEntropy();
		} else if (prop.equals("temperature")) {
			return getTemperature();
		} else if (prop.equals("pressure")) {
			return getPressure();
		} else {
			return 1.0;
		}
	}

	public void saveToDataBase() {
		neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
		// java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM
		// SYSTEMREPORT"));
		double molarmass = 0.0, stddens = 0.0, boilp = 0.0;
		try {

			database.execute("delete FROM systemreport");
			int i = 0;
			for (; i < numberOfComponents; i++) {
				String sqlString = "'" + Integer.toString(i + 1) + "', '" + getPhase(0).getComponent(i).getName()
						+ "', " + "'molfrac[-] ', '" + Double.toString(getPhase(0).getComponent(i).getz()) + "'";

				int j = 0;
				for (; j < numberOfPhases; j++) {
					sqlString += ", '" + Double.toString(getPhase(j).getComponent(i).getx()) + "'";
				}

				while (j < 3) {
					j++;
					sqlString += ", '0'";
				}

				logger.error(sqlString);

				database.execute("INSERT INTO systemreport VALUES (" + sqlString + ")");
			}

			// beta
			i++;

			String sqlString = "'" + Integer.toString(i + 1) + "', 'PhaseFraction', " + "'[-]', '1'";

			int j = 0;
			for (; j < numberOfPhases; j++) {
				sqlString += ", '" + Double.toString(getPhase(j).getBeta()) + "'";
			}

			while (j < 3) {
				j++;
				sqlString += ", '0'";
			}

			logger.error(sqlString);

			database.execute("INSERT INTO systemreport VALUES (" + sqlString + ")");

			// molarmass
			i++;

			sqlString = "'" + Integer.toString(i + 1) + "', 'MolarMass', " + "'kg/mol ', '"
					+ Double.toString(getMolarMass()) + "'";

			j = 0;
			for (; j < numberOfPhases; j++) {
				sqlString += ", '" + Double.toString(getPhase(j).getMolarMass()) + "'";
			}
			while (j < 3) {
				j++;
				sqlString += ", '0'";
			}

			// System.out.println(sqlString);
			database.execute("INSERT INTO systemreport VALUES (" + sqlString + ")");

			// dataSet.next();
			// dataSet.updateString("SPECIFICATION", "dette");
			// double test = dataSet.getDouble("Phase1");
			// System.out.println(test);
			// dataSet.next();
			// dataSet.updateString(1,"tesst");
			database.getConnection().close();
		} catch (Exception e) {
			logger.error("failed " + e.toString());
		} finally {
			try {
				if (database.getStatement() != null) {
					database.getStatement().close();
				}
				if (database.getConnection() != null) {
					database.getConnection().close();
				}
			} catch (Exception e) {
				logger.error("err closing database IN MIX...", e);
			}
		}

	}

	/**
	 * Getter for property standard.
	 *
	 * @return Value of property standard.
	 *
	 */
	public neqsim.standards.StandardInterface getStandard() {
		return standard;
	}

	public neqsim.standards.StandardInterface getStandard(String standardName) {
		this.setStandard(standardName);
		return standard;
	}

	public void generatePDF() {
		neqsim.dataPresentation.iTextPDF.PdfCreator pdfDocument = null;
		pdfDocument = new neqsim.dataPresentation.iTextPDF.PdfCreator();
		pdfDocument.getDocument().addTitle("NeqSim Thermo Simulation Report");
		pdfDocument.getDocument().addKeywords("Temperature ");

		pdfDocument.getDocument().open();
		try {
			pdfDocument.getDocument().add(new com.lowagie.text.Paragraph("Properties of fluid: " + getFluidName(),
					com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.TIMES_ROMAN, 12)));

			com.lowagie.text.List list = new com.lowagie.text.List(true, 20);
			list.add(new com.lowagie.text.ListItem("Thermodynamic model: " + getModelName()));
			list.add(new com.lowagie.text.ListItem("Mixing rule: " + getMixingRuleName()));
			list.add(new com.lowagie.text.ListItem("Number of phases: " + getNumberOfPhases()));
			list.add(new com.lowagie.text.ListItem("Status of calculation: ok"));
			pdfDocument.getDocument().add(list);

			com.lowagie.text.Table resTable = new com.lowagie.text.Table(6,
					getPhases()[0].getNumberOfComponents() + 30);
			String[][] tempTable = createTable(getFluidName());
			for (int i = 0; i < getPhases()[0].getNumberOfComponents() + 30; i++) {
				for (int j = 0; j < 6; j++) {
					resTable.addCell(tempTable[i][j]);
				}
			}
			pdfDocument.getDocument().add(resTable);

			com.lowagie.text.Anchor anchor = new com.lowagie.text.Anchor("NeqSim Website",
					com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, 12,
							com.lowagie.text.Font.UNDERLINE, new Color(0, 0, 255)));
			anchor.setReference("http://www.stud.ntnu.no/~solbraa/neqsim");
			anchor.setName("NeqSim Website");

			pdfDocument.getDocument().add(anchor);
		} catch (Exception e) {
			logger.error("error", e);
		}
		pdfDocument.getDocument().close();
		this.pdfDocument = pdfDocument;
	}

	public void displayPDF() {
		generatePDF();
		((neqsim.dataPresentation.iTextPDF.PdfCreator) pdfDocument).openPDF();
	}

	/**
	 * Setter for property standard.
	 *
	 * @param standard New value of property standard.
	 *
	 */
	public void setStandard(String standardName) {
		if (standardName.equals("ISO1992")) {
			this.standard = new neqsim.standards.gasQuality.Standard_ISO6976();
		} else if (standardName.equals("Draft_ISO18453")) {
			this.standard = new neqsim.standards.gasQuality.Draft_ISO18453(this);
		} else {
			this.standard = new neqsim.standards.gasQuality.Standard_ISO6976();
		}
	}

	/**
	 * Getter for property hasPlusFraction.
	 *
	 * @return Value of property hasPlusFraction.
	 *
	 */
	public boolean hasPlusFraction() {
		for (int i = 0; i < numberOfComponents; i++) {
			if (getPhase(0).getComponent(i).isIsPlusFraction()) {
				return true;
			}
		}
		return false;
	}

	public boolean hasTBPFraction() {
		for (int i = 0; i < numberOfComponents; i++) {
			if (getPhase(0).getComponent(i).isIsTBPfraction()) {
				return true;
			}
		}
		return false;
	}

	public void tuneModel(String model, double val, int phase) {
		if (model.equals("viscosity")) {
			getPhase(phase).getPhysicalProperties().getViscosityModel().tuneModel(val, getPhase(phase).getTemperature(),
					getPhase(phase).getPressure());
			for (int i = 0; i < getMaxNumberOfPhases(); i++) {
				for (int j = 0; j < numberOfPhases; j++) {
					getPhase(i).getComponent(j)
							.setCriticalViscosity(getPhase(phase).getComponent(j).getCriticalViscosity());
				}
			}
		}

		initPhysicalProperties();
	}

	public double getHeatOfVaporization() {
		if (numberOfPhases < 2) {
			return 0;
		} else {
			return getPhase(0).getEnthalpy() / getPhase(0).getNumberOfMolesInPhase()
					- getPhase(1).getEnthalpy() / getPhase(1).getNumberOfMolesInPhase();
		}
	}

	public void readFluid(String fluidName) {
		this.fluidName = fluidName;
		try {
			neqsim.util.database.NeqSimFluidDataBase database = new neqsim.util.database.NeqSimFluidDataBase();
			java.sql.ResultSet dataSet = null;
			dataSet = database.getResultSet("SELECT * FROM " + fluidName);

			while (dataSet.next()) {
				String componentType = dataSet.getString("ComponentType");

				if (componentType.equals("normal")) {
					addComponent(dataSet.getString("ComponentName"), Double.parseDouble(dataSet.getString("Rate")));
				} else if (componentType.equals("TBP")) {
					addTBPfraction(dataSet.getString("ComponentName"), Double.parseDouble(dataSet.getString("Rate")),
							Double.parseDouble(dataSet.getString("MolarMass")) / 1000.0,
							Double.parseDouble(dataSet.getString("Density")));
				} else if (componentType.equals("Plus")) {
					addPlusFraction(dataSet.getString("ComponentName"), Double.parseDouble(dataSet.getString("Rate")),
							Double.parseDouble(dataSet.getString("MolarMass")) / 1000.0,
							Double.parseDouble(dataSet.getString("Density")));
				} else {
					logger.error("component type need to be specified for ... " + dataSet.getString("ComponentName"));
				}
			}
		} catch (Exception e) {
			String err = e.toString();
			logger.error(err);
		}
	}

	public String[][] getResultTable() {
		return resultTable;
	}
	//
	// public String[] getResultArray1(){
	// java.util.ArrayList list = new java.util.ArrayList();
	// for(int i=0;i<resultTable[0].length;i++){
	// list.add(getResultTable()[0][i].toString());
	// }
	// String[] componentList = new String[list.size()];
	// for (int j=0; j<resultTable[0].length; j++){
	// componentList[j] = (String) list.get(j);
	// }
	// return componentList;
	// }

	@SuppressWarnings("empty-statement")
	public SystemInterface setModel(String model) {
		SystemInterface tempModel = null;
		try {
			if (model.equals("SRK-EOS")) {
				tempModel = new SystemSrkEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("GERG2004-EOS")) {
				tempModel = new SystemGERG2004Eos(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("PrEos") || model.equals("PR-EOS")) {
				tempModel = new SystemPrEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("ScRK-EOS") || model.equals("ScRK-EOS-HV")) {
				tempModel = new SystemSrkSchwartzentruberEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("Electrolyte-ScRK-EOS")) {
				tempModel = new SystemFurstElectrolyteEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("GERG-water-EOS")) {
				tempModel = new SystemGERGwaterEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("CPAs-SRK-EOS")) {
				tempModel = new SystemSrkCPAs(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("CPAs-SRK-EOS-statoil")) {
				tempModel = new SystemSrkCPAstatoil(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("Electrolyte-CPA-EOS-statoil")) {
				tempModel = new SystemElectrolyteCPAstatoil(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("UMR-PRU-EoS")) {
				tempModel = new SystemUMRPRUMCEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("PC-SAFT")) {
				tempModel = new SystemPCSAFT(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("GERG-2008-EoS")) {
				tempModel = new SystemGERG2004Eos(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("SRK-TwuCoon-Statoil-EOS") || model.equals("SRK-TwuCoon-EOS")) {
				tempModel = new SystemSrkTwuCoonStatoilEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
			} else if (model.equals("SRK-TwuCoon-Param-EOS")) {
				tempModel = new SystemSrkTwuCoonParamEos(getPhase(0).getTemperature(), getPhase(0).getPressure());
			}
			else if (model.equals("Duan-Sun")) {
				tempModel = new SystemDuanSun(getPhase(0).getTemperature(), getPhase(0).getPressure());
			}
			else {
				logger.error("model : " + model + " not defined.....");
			}
			// tempModel.getCharacterization().setTBPModel("RiaziDaubert");
			tempModel.useVolumeCorrection(true);

			logger.info("created class " + tempModel);
			for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
				logger.info("adding " + getPhase(0).getComponent(i).getName() + " moles "
						+ getPhase(0).getComponent(i).getNumberOfmoles() + " isPlus "
						+ getPhase(0).getComponent(i).isIsPlusFraction() + " isTBP "
						+ getPhase(0).getComponent(i).isIsTBPfraction());
				if (getPhase(0).getComponent(i).isIsTBPfraction()) {
					tempModel.addTBPfraction(getPhase(0).getComponent(i).getName(),
							getPhase(0).getComponent(i).getNumberOfmoles(), getPhase(0).getComponent(i).getMolarMass(),
							getPhase(0).getComponent(i).getNormalLiquidDensity());
				} else if (getPhase(0).getComponent(i).isIsPlusFraction()) {
					tempModel.addPlusFraction(getPhase(0).getComponent(i).getName(),
							getPhase(0).getComponent(i).getNumberOfmoles(), getPhase(0).getComponent(i).getMolarMass(),
							getPhase(0).getComponent(i).getNormalLiquidDensity());
				} else {
					tempModel.addComponent(getPhase(0).getComponent(i).getName(),
							getPhase(0).getComponent(i).getNumberOfmoles());
				}
			}

			// if (tempModel.getCharacterization().characterize()) {
			// tempModel.addPlusFraction(6, 100);
			// }
			logger.info("creatore database ......");
			logger.info("done ... creatore database ......");
			tempModel.createDatabase(true);
			logger.info("done ... set mixing rule ......");
			tempModel.autoSelectMixingRule();
			if (model.equals("Electrolyte-ScRK-EOS")) {// || model.equals("Electrolyte-CPA-EOS-statoil")) {
				logger.info("chemical reaction init......");
				tempModel.setMultiPhaseCheck(false);
				tempModel.chemicalReactionInit();
			} else {
				tempModel.setMultiPhaseCheck(true);
			}

		} catch (Exception e) {
			logger.error("error", e);
		}
		return tempModel;
	}

	public SystemInterface autoSelectModel() {
		if (this.getPhase(0).hasComponent("MDEA") && this.getPhase(0).hasComponent("water")
				&& this.getPhase(0).hasComponent("CO2")) {
			return setModel("Electrolyte-ScRK-EOS");
		} else if (this.getPhase(0).hasComponent("water") || this.getPhase(0).hasComponent("methanol")
				|| this.getPhase(0).hasComponent("MEG") || this.getPhase(0).hasComponent("TEG")
				|| this.getPhase(0).hasComponent("ethanol") || this.getPhase(0).hasComponent("DEG")) {
			if (this.getPhase(0).hasComponent("Na+") || this.getPhase(0).hasComponent("K+")
					|| this.getPhase(0).hasComponent("Br-") || this.getPhase(0).hasComponent("Mg++")
					|| this.getPhase(0).hasComponent("Cl-") || this.getPhase(0).hasComponent("Ca++")
					|| this.getPhase(0).hasComponent("Fe++") || this.getPhase(0).hasComponent("SO4--")) {
				logger.info("model elect");
				return setModel("Electrolyte-CPA-EOS-statoil");
			} else {
				return setModel("CPAs-SRK-EOS-statoil");
			}
		} else if (this.getPhase(0).hasComponent("water")) {
			return setModel("ScRK-EOS");
		} else if (this.getPhase(0).hasComponent("mercury")) {
			return setModel("SRK-TwuCoon-Statoil-EOS");
		} else {
			logger.info("no model");
			return setModel("SRK-EOS");

		}
	}

	public void autoSelectMixingRule() {
		logger.info("setting mixing rule");
		if (modelName.equals("CPAs-SRK-EOS") || modelName.equals("CPA-SRK-EOS")
				|| modelName.equals("Electrolyte-CPA-EOS-statoil") || modelName.equals("CPAs-SRK-EOS-statoil")) {
			this.setMixingRule(10);
			// System.out.println("mix rule 10");
		} else if ((modelName.equals("ScRK-EOS-HV") || modelName.equals("SRK-EOS") || modelName.equals("ScRK-EOS"))
				&& this.getPhase(0).hasComponent("water")) {
			this.setMixingRule(4);
		} else if (modelName.equals("PR-EOS")) {
			this.setMixingRule(2);
		} else if (modelName.equals("Electrolyte-ScRK-EOS")) {
			this.setMixingRule(4);
		} else if (modelName.equals("UMR-PRU-EoS") || modelName.equals("UMR-PRU-MC-EoS")) {
			this.setMixingRule("HV", "UNIFAC_UMRPRU");
		} else if (modelName.equals("GERG-water-EOS")) {
			this.setMixingRule(8);
		} else if (modelName.equals("GERG-2008-EOS")) {
			this.setMixingRule(2);
		} else if (modelName.equals("PC-SAFT")) {
			this.setMixingRule(8);
		} else {
			this.setMixingRule(2);
		}
	}

	public int getMixingRule() {
		return mixingRule;
	}

	public ComponentInterface getComponent(String name) {
		return getPhase(0).getComponent(name);
	}

	public ComponentInterface getComponent(int number) {
		return getPhase(0).getComponent(number);
	}

	public void orderByDensity() {
		boolean change = false;
		int count = 0;

		for (int i = 0; i < getNumberOfPhases(); i++) {
			if (getPhase(i).getPhysicalProperties() == null) {
				getPhase(i).initPhysicalProperties("density");
			}
			getPhase(i).getPhysicalProperties().setPhase(getPhase(i));
		}

		do {
			change = false;
			count++;
			for (int i = 1; i < getNumberOfPhases(); i++) {
				if (i == 4) {
					break;
				}

				try {
					if (change || getPhase(i).getPhysicalProperties() == null) {
						getPhase(i).initPhysicalProperties("density");
					}
				} catch (Exception e) {
					logger.error("error", e);
				}
				if (getPhase(i).getPhysicalProperties().calcDensity() < getPhase(i - 1).getPhysicalProperties()
						.calcDensity()) {
					int tempIndex1 = getPhaseIndex(i - 1);
					int tempIndex2 = getPhaseIndex(i);
					setPhaseIndex(i, tempIndex1);
					setPhaseIndex(i - 1, tempIndex2);
					change = true;
				}
			}
		} while (change);

	}

	public void addLiquidToGas(double fraction) {
		for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
			double change = getPhase(1).getComponent(i).getNumberOfMolesInPhase() * fraction;
			addComponent(i, change, 0);
			addComponent(i, -change, 1);
		}
	}

	public void addPhaseFractionToPhase(double fraction, String specification, String fromPhaseName,
			String toPhaseName) {
		if (!(hasPhaseType(fromPhaseName) && hasPhaseType(toPhaseName) || fraction < 1e-25)) {
			return;
		}
		int phaseNumbFrom = getPhaseNumberOfPhase(fromPhaseName);
		int phaseNumbTo = getPhaseNumberOfPhase(toPhaseName);
		for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
			double change = getPhase(phaseNumbFrom).getComponent(i).getNumberOfMolesInPhase() * fraction;
			addComponent(i, change, phaseNumbTo);
			addComponent(i, -change, phaseNumbFrom);
		}
	}

	public void addGasToLiquid(double fraction) {
		for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
			double change = getPhase(0).getComponent(i).getNumberOfMolesInPhase() * fraction;
			addComponent(i, -change, 0);
			addComponent(i, change, 1);
		}

	}

	public double getTotalNumberOfMoles() {
		return this.totalNumberOfMoles;
	}

	public void setTotalNumberOfMoles(double totalNumberOfMoles) {
		this.totalNumberOfMoles = totalNumberOfMoles;
	}

	public boolean hasPhaseType(String phaseTypeName) {
		for (int i = 0; i < numberOfPhases; i++) {
			if (getPhase(i).getPhaseTypeName().equals(phaseTypeName)) {
				return true;
			}
		}
		return false;
	}

	public PhaseInterface getPhase(String phaseTypeName) {
		for (int i = 0; i < numberOfPhases; i++) {
			if (getPhase(i).getPhaseTypeName().equals(phaseTypeName)) {
				return getPhase(i);
			}
		}
		throw new RuntimeException();
	}

	public int getPhaseNumberOfPhase(String phaseTypeName) {
//        if(phaseTypeName.equals("gas")) return 0;
//        else if(phaseTypeName.equals("oil")) return 1;
//        else if(phaseTypeName.equals("water")) return 2;
//        else if(phaseTypeName.equals("liquid")) return 1;
//        else return 0;
//
		for (int i = 0; i < numberOfPhases; i++) {
			if (getPhase(i).getPhaseTypeName().equals(phaseTypeName)) {
				return i;
			}
		}
		return 0;
	}

	public int getPhaseIndexOfPhase(String phaseTypeName) {
//        if(phaseTypeName.equals("gas")) return 0;
//        else if(phaseTypeName.equals("oil")) return 1;
//        else if(phaseTypeName.equals("water")) return 2;
//        else if(phaseTypeName.equals("liquid")) return 1;
//        else return 0;
//
		for (int i = 0; i < numberOfPhases; i++) {
			if (getPhase(i).getPhaseTypeName().equals(phaseTypeName)) {
				return phaseIndex[i];
			}
		}
		return phaseIndex[0];
	}

	public PhaseInterface getPhaseOfType(String phaseName) {
		for (int i = 0; i < numberOfPhases; i++) {
			if (getPhase(i).getPhaseTypeName().equals(phaseName)) {
				return getPhase(i);
			}
		}
		return null;
	}

	public double calcHenrysConstant(String component) {
		if (numberOfPhases != 2) {
			logger.error("cant calculated Henrys constant - two phases must be present.");
			return 0;
		} else {
			int compNumb = getPhase(getPhaseIndex(0)).getComponent(component).getComponentNumber();
			double hc = getPhase(getPhaseIndex(0)).getFugacity(compNumb)
					/ getPhase(getPhaseIndex(1)).getComponent(component).getx();
			return hc;
		}
	}

	public boolean useTVasIndependentVariables() {
		return useTVasIndependentVariables;
	}

	public void setUseTVasIndependentVariables(boolean useTVasIndependentVariables) {
		for (int i = 0; i < numberOfPhases; i++) {
			getPhase(i).setTotalVolume(getPhase(i).getVolume());
			getPhase(i).setConstantPhaseVolume(useTVasIndependentVariables);
			getPhase(i).calcMolarVolume(!useTVasIndependentVariables);
		}
		this.useTVasIndependentVariables = useTVasIndependentVariables;
	}

	public void setBmixType(int bmixType) {
		for (int i = 0; i < getMaxNumberOfPhases(); i++) {
			((PhaseEosInterface) getPhase(i)).getMixingRule().setBmixType(bmixType);
		}
	}

	/**
	 * @return the implementedTemperatureDeriativesofFugacity
	 */
	public boolean isImplementedTemperatureDeriativesofFugacity() {
		return implementedTemperatureDeriativesofFugacity;
	}

	/**
	 * @param implementedTemperatureDeriativesofFugacity the
	 *                                                   implementedTemperatureDeriativesofFugacity
	 *                                                   to set
	 */
	public void setImplementedTemperatureDeriativesofFugacity(boolean implementedTemperatureDeriativesofFugacity) {
		this.implementedTemperatureDeriativesofFugacity = implementedTemperatureDeriativesofFugacity;
	}

	/**
	 * @return the implementedPressureDeriativesofFugacity
	 */
	public boolean isImplementedPressureDeriativesofFugacity() {
		return implementedPressureDeriativesofFugacity;
	}

	/**
	 * @param implementedPressureDeriativesofFugacity the
	 *                                                implementedPressureDeriativesofFugacity
	 *                                                to set
	 */
	public void setImplementedPressureDeriativesofFugacity(boolean implementedPressureDeriativesofFugacity) {
		this.implementedPressureDeriativesofFugacity = implementedPressureDeriativesofFugacity;
	}

	/**
	 * @return the implementedCompositionDeriativesofFugacity
	 */
	public boolean isImplementedCompositionDeriativesofFugacity() {
		return implementedCompositionDeriativesofFugacity;
	}

	/**
	 * @param implementedCompositionDeriativesofFugacity the
	 *                                                   implementedCompositionDeriativesofFugacity
	 *                                                   to set
	 */
	public void setImplementedCompositionDeriativesofFugacity(boolean implementedCompositionDeriativesofFugacity) {
		this.implementedCompositionDeriativesofFugacity = implementedCompositionDeriativesofFugacity;
	}

	public void deleteFluidPhase(int phase) {
		for (int i = phase; i < numberOfPhases; i++) {
			phaseIndex[i] = phaseIndex[i + 1];
		}
		numberOfPhases--;
	}

	/**
	 * @return the maxNumberOfPhases
	 */
	public int getMaxNumberOfPhases() {
		return maxNumberOfPhases;
	}

	/**
	 * @param maxNumberOfPhases the maxNumberOfPhases to set
	 */
	public void setMaxNumberOfPhases(int maxNumberOfPhases) {
		this.maxNumberOfPhases = maxNumberOfPhases;
	}

	/**
	 * This method is used to set the total molar composition of a fluid. The total
	 * flow rate will be kept constant. The input mole fractions will be normalized.
	 *
	 * @param molefractions is a double array taking the molar fraction of the
	 *                      components in the fluid
	 * @return Nothing.
	 */
	public void setMolarComposition(double[] molefractions) {
		double totalFlow = getTotalNumberOfMoles();
		if (totalFlow < 1e-100) {
			logger.error("Total flow can not be 0 when setting molar composition ");
			neqsim.util.exception.InvalidInputException e = new neqsim.util.exception.InvalidInputException();
			throw new RuntimeException(e);
		}
		double sum = 0;
		for (double value : molefractions) {
			sum += value;
		}
		setEmptyFluid();
		for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
			addComponent(compNumb, totalFlow * molefractions[compNumb] / sum);
		}
		for (int i = 0; i < getNumberOfPhases(); i++) {
			init(0, i);
		}
	}

	/**
	 * This method is used to set the total molar composition of a plus fluid. The
	 * total flow rate will be kept constant. The input mole fractions will be
	 * normalized.
	 *
	 * @param molefractions is a double array taking the molar fraction of the
	 *                      components in the fluid. THe last molfraction is the
	 *                      mole fraction of the plus component
	 * @return Nothing.
	 */
	public void setMolarCompositionPlus(double[] molefractions) {
		double totalFlow = getTotalNumberOfMoles();
		if (totalFlow < 1e-100) {
			logger.error("Total flow can not be 0 when setting molar composition ");
			neqsim.util.exception.InvalidInputException e = new neqsim.util.exception.InvalidInputException();
			throw new RuntimeException(e);
		}
		double sum = 0;
		for (double value : molefractions) {
			sum += value;
		}
		setEmptyFluid();
		for (int compNumb = 0; compNumb < numberOfComponents
				- getCharacterization().getLumpingModel().getNumberOfLumpedComponents(); compNumb++) {
			addComponent(compNumb, totalFlow * molefractions[compNumb] / sum);
		}
		int ii = 0;
		for (int compNumb = numberOfComponents - getCharacterization().getLumpingModel()
				.getNumberOfLumpedComponents(); compNumb < numberOfComponents; compNumb++) {
			addComponent(compNumb,
					totalFlow * getCharacterization().getLumpingModel().getFractionOfHeavyEnd(ii++)
							* molefractions[numberOfComponents
									- getCharacterization().getLumpingModel().getNumberOfLumpedComponents()]
							/ sum);
		}
		for (int i = 0; i < getNumberOfPhases(); i++) {
			init(0, i);
		}
	}

	/**
	 * This method is used to set the total molar composition of a characterized
	 * fluid. The total flow rate will be kept constant. The input mole fractions
	 * will be normalized.
	 *
	 * @param molefractions is a double array taking the molar fraction of the
	 *                      components in the fluid. THe last fraction in the array
	 *                      is the total molefraction of the characterized
	 *                      components.
	 * @return Nothing.
	 */
	public void setMolarCompositionOfPlusFluid(double[] molefractions) {
		double totalFlow = getTotalNumberOfMoles();
		if (totalFlow < 1e-100) {
			logger.error("Total flow can not be 0 when setting molar composition ");
			neqsim.util.exception.InvalidInputException e = new neqsim.util.exception.InvalidInputException();
			throw new RuntimeException(e);
		}
		double sum = 0;
		for (double value : molefractions) {
			sum += value;
		}
		setEmptyFluid();
		int compNumb = 0;
		for (compNumb = 0; compNumb < molefractions.length - 1; compNumb++) {
			addComponent(compNumb, totalFlow * molefractions[compNumb] / sum);
		}
		for (int j = 0; j < getCharacterization().getLumpingModel().getNumberOfLumpedComponents() - 1; j++) {
			// addComponent(compNumb, totalFlow * molefractions[molefractions.length - 1]
			// * getCharacterization().getLumpingModel().getFractionOfHeavyEnd(j) / sum);
			compNumb++;
		}
		for (int i = 0; i < getNumberOfPhases(); i++) {
			init(0, i);
		}
	}

	/**
	 * This method is used to set the total molar composition of a fluid. The total
	 * flow rate will be kept constant. The input mole fractions will be normalized.
	 *
	 * @param moles is a double array taking the molar flow rate (mole/sec) of the
	 *              components in the fluid
	 * @return Nothing.
	 */
	public void setMolarFlowRates(double[] moles) {
		setEmptyFluid();
		for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
			addComponent(compNumb, moles[compNumb]);
		}
		for (int i = 0; i < getNumberOfPhases(); i++) {
			init(0, i);
		}
	}

	/**
	 * Returns the molar rate vector in unit mole/sec
	 */
	public double[] getMolarRate() {
		double[] comp = new double[getPhase(0).getNumberOfComponents()];

		for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
			comp[compNumb] = getPhase(0).getComponent(compNumb).getNumberOfmoles();
		}
		return comp;
	}

	/**
	 * Returns the overall mole composition vector in unit mole fraction
	 */
	public double[] getMolarComposition() {
		double[] comp = new double[getPhase(0).getNumberOfComponents()];

		for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
			comp[compNumb] = getPhase(0).getComponent(compNumb).getz();
		}
		return comp;
	}

	/**
	 * @return the multiphaseWaxCheck
	 */
	public boolean isMultiphaseWaxCheck() {
		return multiphaseWaxCheck;
	}

	/**
	 * @param multiphaseWaxCheck the multiphaseWaxCheck to set
	 */
	public void setMultiphaseWaxCheck(boolean multiphaseWaxCheck) {
		this.multiphaseWaxCheck = multiphaseWaxCheck;
	}

	public String[] getCompIDs() {
		String[] ids = new String[numberOfComponents];

		for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
			ids[compNumb] = Integer.toString(getPhase(0).getComponent(compNumb).getIndex());
		}
		return ids;
	}

	public String[] getCompFormulaes() {
		String[] formula = new String[numberOfComponents];

		for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
			formula[compNumb] = getPhase(0).getComponent(compNumb).getFormulae();
		}
		return formula;
	}

	public String[] getCompNames() {
		String[] names = new String[numberOfComponents];

		for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
			names[compNumb] = getPhase(0).getComponent(compNumb).getComponentName();
		}
		return names;
	}

	public double[] getNormalBoilingPointTemperatures() {
		double[] bt = new double[numberOfComponents];

		for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
			bt[compNumb] = getPhase(0).getComponent(compNumb).getNormalBoilingPoint() + 273.15;
		}
		return bt;
	}

	public String[] getCapeOpenProperties11() {
		return CapeOpenProperties11;
	}

	public String[] getCapeOpenProperties10() {
		return CapeOpenProperties10;
	}

	public double[] getMolecularWeights() {
		double[] mm = new double[numberOfComponents];

		for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
			mm[compNumb] = getPhase(0).getComponent(compNumb).getMolarMass() * 1e3;
		}
		return mm;
	}

	public String[] getCASNumbers() {
		String[] names = new String[numberOfComponents];

		for (int compNumb = 0; compNumb < numberOfComponents; compNumb++) {
			names[compNumb] = getPhase(0).getComponent(compNumb).getCASnumber();
		}
		return names;
	}

	public int getNumberOfOilFractionComponents() {
		int number = 0;
		for (int i = 0; i < getPhase(0).getNumberOfComponents(); i++) {
			if (getPhase(0).getComponent(i).isIsTBPfraction() || getPhase(0).getComponent(i).isIsPlusFraction()) {
				number++;
			}
		}
		return number;
	}

	public int[] getOilFractionIDs() {
		int numb = getNumberOfOilFractionComponents();
		int[] IDs = new int[numb];
		int number = 0;
		for (int i = 0; i < numb; i++) {
			if (getPhase(0).getComponent(i).isIsTBPfraction() || getPhase(0).getComponent(i).isIsPlusFraction()) {
				IDs[i] = getPhase(0).getComponent(i).getIndex();
				number++;
			}
		}
		return IDs;
	}

	public boolean setHeavyTBPfractionAsPlusFraction() {
		int compNumber = 0;
		double molarMass = 0;
		boolean foundTBP = false;

		for (int i = 0; i < numberOfComponents; i++) {
			if (getPhase(0).getComponent(i).isIsTBPfraction() || getPhase(0).getComponent(i).isIsPlusFraction()) {
				if (getPhase(0).getComponent(i).getMolarMass() > molarMass) {
					molarMass = getPhase(0).getComponent(i).getMolarMass();
					compNumber = i;
					foundTBP = true;
				}
			}
		}
		if (foundTBP) {
			for (int i = 0; i < maxNumberOfPhases; i++) {
				getPhase(0).getComponent(compNumber).setIsPlusFraction(true);
			}
		}
		return foundTBP;
	}

	public double[] getOilFractionNormalBoilingPoints() {
		int numb = getNumberOfOilFractionComponents();
		int[] indexes = getOilFractionIDs();
		double[] temp = new double[numb];
		for (int i = 0; i < numb; i++) {
			temp[i] = getPhase(0).getComponentWithIndex(indexes[i]).getNormalBoilingPoint();
		}
		return temp;
	}

	public double[] getOilFractionLiquidDensityAt25C() {
		int numb = getNumberOfOilFractionComponents();
		int[] indexes = getOilFractionIDs();
		double[] temp = new double[numb];
		for (int i = 0; i < numb; i++) {
			temp[i] = getPhase(0).getComponentWithIndex(indexes[i]).getNormalLiquidDensity();
		}
		return temp;
	}

	public double[] getOilFractionMolecularMass() {
		int numb = getNumberOfOilFractionComponents();
		int[] indexes = getOilFractionIDs();
		double[] temp = new double[numb];
		for (int i = 0; i < numb; i++) {
			temp[i] = getPhase(0).getComponentWithIndex(indexes[i]).getMolarMass();
		}
		return temp;
	}

	public PhaseInterface getLowestGibbsEnergyPhase() {
		if (getPhase(0).getGibbsEnergy() < getPhase(1).getGibbsEnergy()) {
			return getPhase(0);
		} else {
			return getPhase(1);
		}
	}

	public double getWtFraction(int phaseNumber) {
		return getPhase(phaseNumber).getWtFraction(this);
	}

	/**
	 * method to return the volume fraction of a phase note: without Peneloux volume
	 * correction
	 *
	 * @param phaseNumber number of the phase to get volume fraction for
	 * @return volume fraction
	 */
	public double getVolumeFraction(int phaseNumber) {
		return getPhase(phaseNumber).getVolume() / getVolume();
	}

	public final double getPhaseFraction(String phaseTypeName, String unit) {
		int phaseNumber = getPhaseNumberOfPhase(phaseTypeName);
		switch (unit) {
		case "mole":
			return getBeta(phaseNumber);
		case "volume":
			return getVolumeFraction(phaseNumber);
		case "mass":
			initPhysicalProperties("density");
			return getPhase(phaseNumber).getVolume() * getPhase(phaseNumber).getDensity() / getDensity();
		default:
			return getBeta(phaseNumber);
		}
	}

	/**
	 * method to return the volume fraction of a phase note: with Peneloux volume
	 * correction
	 *
	 * @param phaseNumber number of the phase to get volume fraction for
	 * @return volume fraction
	 */
	public double getCorrectedVolumeFraction(int phaseNumber) {
		return getPhase(phaseNumber).getCorrectedVolume() / getCorrectedVolume();
	}

	public double getMoleFraction(int phaseNumber) {
		return getPhase(phaseNumber).getBeta();
	}

	public void isImplementedCompositionDeriativesofFugacity(boolean isImpl) {
		implementedCompositionDeriativesofFugacity = isImpl;
	}

	public void addCapeOpenProperty(String propertyName) {
		String[] tempString = new String[CapeOpenProperties11.length + 1];
		System.arraycopy(CapeOpenProperties11, 0, tempString, 0, CapeOpenProperties11.length);
		tempString[CapeOpenProperties11.length] = propertyName;
		CapeOpenProperties11 = tempString;

		tempString = new String[CapeOpenProperties10.length + 1];
		System.arraycopy(CapeOpenProperties10, 0, tempString, 0, CapeOpenProperties10.length);
		tempString[CapeOpenProperties10.length] = propertyName;
		CapeOpenProperties10 = tempString;
	}

	/**
	 * @return the waxCharacterisation
	 */
	public neqsim.thermo.characterization.WaxCharacterise getWaxCharacterisation() {
		return waxCharacterisation;
	}

	public WaxModelInterface getWaxModel() {
		if (waxCharacterisation == null) {
			waxCharacterisation = new WaxCharacterise(this);
		}
		return waxCharacterisation.getModel();
	}

	/**
	 * @param componentNames the componentNames to set
	 */
	public void setComponentNames(String[] componentNames) {
		for (int i = 0; i < componentNames.length; i++) {
			this.componentNames.set(i, componentNames[i]);
		}
	}

	public double getLiquidVolume() {
		double totFlow = 0;

		for (int kj = 0; kj < numberOfPhases; kj++) {
			if (!getPhase(kj).getPhaseTypeName().equals("gas")) {
				totFlow += getPhase(kj).getVolume();
			}
		}
		return totFlow;
	}

	/**
	 * @return the forcePhaseTypes
	 */
	public boolean isForcePhaseTypes() {
		return forcePhaseTypes;
	}

	/**
	 * @param forcePhaseTypes the forcePhaseTypes to set
	 */
	public void setForcePhaseTypes(boolean forcePhaseTypes) {
		this.forcePhaseTypes = forcePhaseTypes;
	}
}
