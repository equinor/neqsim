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

package neqsim.processSimulation.processSystem;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.logging.log4j.*;
/*
 * thermoOps.java
 *
 * Created on 2. oktober 2000, 20:27
 */
import java.util.*;
import neqsim.processSimulation.costEstimation.CostEstimateBaseClass;
import neqsim.processSimulation.measurementDevice.MeasurementDeviceBaseClass;
import neqsim.processSimulation.measurementDevice.MeasurementDeviceInterface;
import neqsim.processSimulation.mechanicalDesign.SystemMechanicalDesign;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.RecycleController;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ProcessSystem extends java.lang.Object implements java.io.Serializable, Runnable {

    private static final long serialVersionUID = 1000;

    Thread thisThread;
    // ProcessEquipmentInterface[]
    String[][] signalDB = new String[100][100];
    private double time = 0;
    private int timeStepNumber = 0;
    private ArrayList<ProcessEquipmentBaseClass> unitOperations = new ArrayList(0);
    ArrayList<MeasurementDeviceInterface> measurementDevices = new ArrayList(0);
    RecycleController recycleController = new RecycleController();
    private double timeStep = 1.0;
    private String name = "process name";
    private SystemMechanicalDesign systemMechanicalDesign = null;
    private CostEstimateBaseClass costEstimator = null;
    static Logger logger = LogManager.getLogger(ProcessSystem.class);

    /**
     * Creates new thermoOps
     */
    public ProcessSystem() {
        systemMechanicalDesign = new SystemMechanicalDesign(this);
        costEstimator = new CostEstimateBaseClass(this);
    }

    public void add(ProcessEquipmentInterface operation) {
        getUnitOperations().add(operation);
        if (operation instanceof ModuleInterface) {
            ((ModuleInterface) operation).initializeModule();
        }
    }

    public void add(MeasurementDeviceInterface measurementDevice) {
        measurementDevices.add(measurementDevice);
    }

    public void add(ProcessEquipmentInterface[] operations) {
        getUnitOperations().addAll(Arrays.asList(operations));
    }

    public Object getUnit(String name) {
        for (int i = 0; i < getUnitOperations().size(); i++) {
            if (getUnitOperations().get(i) instanceof ModuleInterface) {
                for (int j = 0; j < ((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations().size(); j++) {

                    if (((ProcessEquipmentInterface) ((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations().get(j)).getName().equals(name)) {
                        return ((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations().get(j);
                    }
                }
            } else if (((ProcessEquipmentInterface) getUnitOperations().get(i)).getName().equals(name)) {
                return getUnitOperations().get(i);
            }

        }
        return null;
    }
    
    public int getUnitNumber(String name) {
        for (int i = 0; i < getUnitOperations().size(); i++) {
            if (getUnitOperations().get(i) instanceof ModuleInterface) {
                for (int j = 0; j < ((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations().size(); j++) {

                    if (((ProcessEquipmentInterface) ((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations().get(j)).getName().equals(name)) {
                        return j;
                    }
                }
            } else if (((ProcessEquipmentInterface) getUnitOperations().get(i)).getName().equals(name)) {
                return i;
            }

        }
        return 0;
    }
    
    public void replaceObject(String unitName, ProcessEquipmentBaseClass operation) {
    	unitOperations.set(getUnitNumber(name), operation);
    }

    public ArrayList getAllUnitNames() {
        ArrayList unitNames = new ArrayList(0);
        for (int i = 0; i < getUnitOperations().size(); i++) {
            if (getUnitOperations().get(i) instanceof ModuleInterface) {
                for (int j = 0; j < ((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations().size(); j++) {
                    unitNames.add(((ProcessEquipmentInterface) ((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations().get(j)).getName());
                }
            }
            unitNames.add(((ProcessEquipmentInterface) unitOperations.get(i)).getName());
        }
        return unitNames;
    }

    /**
     * @return the unitOperations
     */
    public ArrayList getUnitOperations() {
        return unitOperations;
    }

    public void removeUnit(String name) {
        for (int i = 0; i < unitOperations.size(); i++) {
            if (((ProcessEquipmentInterface) unitOperations.get(i)).getName().equals(name)) {
                unitOperations.remove(i);
            }
        }
    }

    public void clearAll() {
        unitOperations = new ArrayList(0);
    }

    public void clear() {
        unitOperations = new ArrayList(0);
    }
    
    public void setFluid(SystemInterface fluid1, SystemInterface fluid2) {
    	fluid1.removeMoles();
    	for(int i=0;i<fluid2.getNumberOfComponents();i++) {
    		if(fluid1.getPhase(0).hasComponent(fluid2.getComponent(i).getName())){
    			fluid1.addComponent(fluid2.getComponent(i).getName(), fluid2.getComponent(i).getNumberOfmoles());
    		}
    	}
    	fluid1.init(0);
    	fluid1.setTemperature(fluid2.getTemperature());
    	fluid1.setPressure(fluid2.getPressure());
    }

 
    public Thread runAsThread() {
    	 Thread processThread = new Thread(this);
    	 processThread.start();
    	 return processThread;
    }

    public void run() {
        boolean isConverged = true;
        boolean hasResycle = false;
        int iter = 0;
        
        //Initializing recycle controller
        recycleController.clear();
        for (int i = 0; i < unitOperations.size(); i++) {
            if (unitOperations.get(i).getClass().getSimpleName().equals("Recycle")) {
            	hasResycle = true;
            	recycleController.addRecycle((Recycle)unitOperations.get(i));
            }
        }
        recycleController.init();
        
        do {
            iter++;
            isConverged = true;
            for (int i = 0; i < unitOperations.size(); i++) {
            	if (!unitOperations.get(i).getClass().getSimpleName().equals("Recycle")) ((Runnable) unitOperations.get(i)).run();
                if (unitOperations.get(i).getClass().getSimpleName().equals("Recycle") && recycleController.doSolveRecycle((Recycle) unitOperations.get(i))) {
                	((Runnable) unitOperations.get(i)).run();
                }
            }
            if(!recycleController.solvedAll() || recycleController.hasHigherPriorityLevel()) {
        		isConverged=false;
        	}
        	if(recycleController.solvedCurrentPriorityLevel()) {
        		recycleController.nextPriorityLevel();
        	}
        	else if(recycleController.hasLoverPriorityLevel() && !recycleController.solvedAll()) {
        		recycleController.resetPriorityLevel();
        		//isConverged=true;
        	}
        	

            signalDB = new String[1000][1 + 3 * measurementDevices.size()];

            signalDB[timeStepNumber] = new String[1 + 3 * measurementDevices.size()];
            for (int i = 0; i < measurementDevices.size(); i++) {
                signalDB[timeStepNumber][0] = Double.toString(time);
                signalDB[timeStepNumber][3 * i + 1] = ((MeasurementDeviceInterface) measurementDevices.get(i)).getName();
                signalDB[timeStepNumber][3 * i + 2] = Double.toString(((MeasurementDeviceInterface) measurementDevices.get(i)).getMeasuredValue());
                signalDB[timeStepNumber][3 * i + 3] = ((MeasurementDeviceInterface) measurementDevices.get(i)).getUnit();

            }
        } while ((!isConverged || (iter < 2 && hasResycle)) && iter < 100);
        
    }

    public void runTransient() {
        time += getTimeStep();

        for (int i = 0; i < unitOperations.size(); i++) {
            ((ProcessEquipmentInterface) unitOperations.get(i)).runTransient(getTimeStep());
        }
        timeStepNumber++;
        signalDB[timeStepNumber] = new String[1 + 3 * measurementDevices.size()];
        for (int i = 0; i < measurementDevices.size(); i++) {
            signalDB[timeStepNumber][0] = Double.toString(time);
            signalDB[timeStepNumber][3 * i + 1] = ((MeasurementDeviceInterface) measurementDevices.get(i)).getName();
            signalDB[timeStepNumber][3 * i + 2] = Double.toString(((MeasurementDeviceInterface) measurementDevices.get(i)).getMeasuredValue());
            signalDB[timeStepNumber][3 * i + 3] = ((MeasurementDeviceInterface) measurementDevices.get(i)).getUnit();

        }

    }

    public int size() {
        return unitOperations.size();
    }

    public void view() {
        this.displayResult();
    }

    public void displayResult() {

        try {
            thisThread.join();
        } catch (Exception e) {
            System.out.println("Thread did not finish");
        }
        for (int i = 0; i < unitOperations.size(); i++) {
            ((ProcessEquipmentInterface) unitOperations.get(i)).displayResult();
        }

        /*
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridLayout(1, 0, 5, 5));
        JTextArea area1 = new JTextArea(10, 10);
        JTable Jtab = new JTable(reportResults(), reportResults()[0]);
        frame.add(area1);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        * */
    }

    public void reportMeasuredValues() {
        try {
            thisThread.join();
        } catch (Exception e) {
            System.out.println("Thread did not finish");
        }
        for (int i = 0; i < measurementDevices.size(); i++) {
            System.out.println("Measurements Device Name: " + ((MeasurementDeviceInterface) measurementDevices.get(i)).getName());
            System.out.println("Value: " + ((MeasurementDeviceInterface) measurementDevices.get(i)).getMeasuredValue() + " " + ((MeasurementDeviceInterface) measurementDevices.get(i)).getUnit());
            if(((MeasurementDeviceInterface) measurementDevices.get(i)).isOnlineSignal()) System.out.println("Online value: " + ((MeasurementDeviceInterface) measurementDevices.get(i)).getOnlineSignal().getValue()+ " " + ((MeasurementDeviceInterface) measurementDevices.get(i)).getOnlineSignal().getUnit());

        }
    }
    
    public void save(String filePath) {

		ObjectOutputStream out = null;
		InputStream in = null;
		try {
			FileOutputStream fout = new FileOutputStream(filePath, false);
			out = new ObjectOutputStream(fout);
			out.writeObject(this);
			out.close();
			logger.info("process file saved to:  " + filePath);
		} catch (Exception e) {
			logger.error(e.toString());
			e.printStackTrace();
		}
	}
    
    public static ProcessSystem open(String filePath) {

		FileInputStream streamIn = null;
		InputStream in = null;
		ProcessSystem tempSystem = null;
		try {
			streamIn = new FileInputStream(filePath);
			ObjectInputStream objectinputstream = new ObjectInputStream(streamIn);
			tempSystem = (ProcessSystem) objectinputstream.readObject();
			logger.info("process file open ok:  " + filePath);
		} catch (Exception e) {
			logger.error(e.toString());
			e.printStackTrace();
		}
		return tempSystem;
	}

    public String[][] reportResults() {
        String[][] text = new String[200][];

        int numb = 0;
        for (int i = 0; i < unitOperations.size(); i++) {
            for (int k = 0; k < ((ProcessEquipmentInterface) unitOperations.get(i)).reportResults().length; k++) {
                text[numb++] = ((ProcessEquipmentInterface) unitOperations.get(i)).reportResults()[k];
            }
        }
        return text;
    }

    public void printLogFile(String filename) {
        neqsim.dataPresentation.fileHandeling.createTextFile.TextFile tempFile = new neqsim.dataPresentation.fileHandeling.createTextFile.TextFile();
        tempFile.setOutputFileName(filename);
        tempFile.setValues(signalDB);
        tempFile.createFile();
    }

    public double getTimeStep() {
        return timeStep;
    }

    public void setTimeStep(double timeStep) {
        this.timeStep = timeStep;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the systemMechanicalDesign
     */
    public SystemMechanicalDesign getSystemMechanicalDesign() {
        return systemMechanicalDesign;
    }

    /**
     * @param systemMechanicalDesign the systemMechanicalDesign to set
     */
    public void setSystemMechanicalDesign(SystemMechanicalDesign systemMechanicalDesign) {
        this.systemMechanicalDesign = systemMechanicalDesign;
    }

    /**
     * @return the costEstimator
     */
    public CostEstimateBaseClass getCostEstimator() {
        return costEstimator;
    }
}
