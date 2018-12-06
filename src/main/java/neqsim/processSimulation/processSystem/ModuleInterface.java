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
 * ModuleInterface.java
 *
 * Created on 1. november 2006, 21:48
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package neqsim.processSimulation.processSystem;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author ESOL
 */
public interface ModuleInterface extends ProcessEquipmentInterface {

    public neqsim.processSimulation.processSystem.ProcessSystem getOperations();

    public void addInputStream(String streamName, StreamInterface stream);

    public StreamInterface getOutputStream(String streamName);

    public String getPreferedThermodynamicModel();

    public void setPreferedThermodynamicModel(String preferedThermodynamicModel);

    public void run();

    public void initializeStreams();

    public void initializeModule();

    public void setIsCalcDesign(boolean isCalcDesign);

    public boolean isCalcDesign();

}
