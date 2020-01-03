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

/*
 * VUflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author even solbraa
 * @version
 */
public class VSflash extends Flash implements java.io.Serializable {

	private static final long serialVersionUID = 1000;

	double Sspec = 0;
	double Vspec = 0;
	Flash tpFlash;

	/** Creates new PHflash */
	public VSflash() {
	}

	public VSflash(SystemInterface system, double Vspec, double Sspec) {
		this.system = system;
		this.tpFlash = new TPflash(system);
		this.Sspec = Sspec;
		this.Vspec = Vspec;
//        System.out.println("entalpy " + Hspec);
//        System.out.println("volume " + Vspec);
	}

	public double calcdQdPP() {
		double dQdVV = (system.getVolume() - Vspec)
				/ (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature())
				+ system.getPressure() * (system.getdVdPtn())
						/ (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());

		return dQdVV;
	}

	public double calcdQdTT() {
		if (system.getNumberOfPhases() == 1) {
			return -system.getPhase(0).getCp() / system.getTemperature() / neqsim.thermo.ThermodynamicConstantsInterface.R;
		}

		double dQdTT = 0.0;
		for (int i = 0; i < system.getNumberOfPhases(); i++) {
			dQdTT -= system.getPhase(i).getCp() / system.getPhase(i).getTemperature();
		}
		return dQdTT / neqsim.thermo.ThermodynamicConstantsInterface.R;
	}

	public double calcdQdT() {
		double dQdT = (Sspec - system.getEntropy()) / (neqsim.thermo.ThermodynamicConstantsInterface.R);
		return dQdT;
	}

	public double calcdQdP() {
		double dQdP = system.getPressure() * (system.getVolume() - Vspec)
				/ (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());
		return dQdP;
	}

	public double solveQ() {
		double oldPres = system.getPressure(), nyPres = system.getPressure(), nyTemp = system.getTemperature(),
				oldTemp = system.getTemperature();
		double iterations = 1;
	//	logger.info("Sspec: " + Sspec);
		do {
			iterations++;
			oldPres = nyPres;
			oldTemp = nyTemp;
			system.init(3);
		//	logger.info("Sentr: " + system.getEntropy());
		//	logger.info("calcdQdT(): " + calcdQdT());
		//	logger.info("dQdP: " + calcdQdP());
		//	logger.info("dQdT: " + calcdQdT());
			nyPres = oldPres - (iterations) / (iterations + 10.0) * calcdQdP() / calcdQdPP();
			nyTemp = oldTemp - (iterations) / (iterations + 10.0) * calcdQdT() / calcdQdTT();
		//	logger.info("volume: " + system.getVolume());
		//	logger.info("inernaleng: " + system.getInternalEnergy());
			system.setPressure(nyPres);
			system.setTemperature(nyTemp);
			tpFlash.run();
		//	logger.info("error1: " + (Math.abs((nyPres - oldPres) / (nyPres))));
		//	logger.info("error2: " + (Math.abs((nyTemp - oldTemp) / (nyTemp))));
		} while (Math.abs((nyPres - oldPres) / (nyPres)) + Math.abs((nyTemp - oldTemp) / (nyTemp)) > 1e-9
				&& iterations < 1000);
		return nyPres;
	}

	public void run() {
		tpFlash.run();
		solveQ();

	}

	public org.jfree.chart.JFreeChart getJFreeChart(String name) {
		return null;
	}
	
	 public static void main(String[] args) {
  	   SystemInterface testSystem = new SystemSrkEos(273.15+55,50.0);
         
         ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
         testSystem.addComponent("methane", 31.0);
         testSystem.addComponent("ethane", 4.0);
        // testSystem.addComponent("n-heptane", 0.2);
         testSystem.init(0);
         try{
             testOps.TPflash();
             testSystem.display();
             
             double entropy = testSystem.getEntropy()*1.2;
             double volume = testSystem.getVolume()*1.1;
             
             
             testOps.VSflash(volume, entropy);
              testSystem.display();
         }
         catch(Exception e){
             logger.error(e.toString());
         }
         
         
  }
}
