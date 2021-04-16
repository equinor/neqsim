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
 * BaseOperation.java
 *
 * Created on 11. august 2001, 20:32
 */

package neqsim.thermodynamicOperations;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author esol
 * @version
 */
public abstract class BaseOperation extends java.lang.Object implements OperationInterface, java.io.Serializable {

    private static final long serialVersionUID = 1000;

    SystemInterface systemThermo = null;;

    /** Creates new BaseOperation */
    public BaseOperation() {
    }

    public double[] get(String name) {
        return new double[3];
    }

    public String[][] getResultTable() {
        return new String[10][3];
    }

    public SystemInterface getThermoSystem() {
        return null;
    }

    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }

    public void printToFile(String name) {
    }

    public void createNetCdfFile(String name) {

    }

    public double[][] getPoints(int i) {
        return null;
    }

    public void addData(String name, double[][] data) {

    }
}
