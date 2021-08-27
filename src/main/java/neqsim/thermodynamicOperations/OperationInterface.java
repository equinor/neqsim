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
 * OperationInterface.java
 *
 * Created on 2. oktober 2000, 22:14
 */

package neqsim.thermodynamicOperations;

import neqsim.thermo.system.SystemInterface;

/**
 * @author  Even Solbraa
 * @version
 */
public interface OperationInterface extends Runnable, java.io.Serializable {
    public void displayResult();

    public double[][] getPoints(int i);

    public void addData(String name, double[][] data);

    public String[][] getResultTable();

    public void createNetCdfFile(String name);

    public void printToFile(String name);

    public double[] get(String name);

    public org.jfree.chart.JFreeChart getJFreeChart(String name);

    public SystemInterface getThermoSystem();
}
