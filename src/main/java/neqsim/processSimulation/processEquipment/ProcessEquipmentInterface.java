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
 * OperationInterafce.java
 *
 * Created on 2. oktober 2000, 22:14
 */
package neqsim.processSimulation.processEquipment;

import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface ProcessEquipmentInterface extends Runnable, java.io.Serializable {

    public void run();
    
    public String[][] reportResults();

    public void runTransient(double dt);

    public MechanicalDesign getMechanicalDesign();

    public void displayResult();

    public String getName();

    public void setName(String name);

    public void setRegulatorOutSignal(double signal);

    public void setController(ControllerDeviceInterface controller);

    public ControllerDeviceInterface getController();
}
