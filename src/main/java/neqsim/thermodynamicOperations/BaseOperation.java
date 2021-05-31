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

    @Override
	public double[] get(String name) {
        return new double[3];
    }

    @Override
	public String[][] getResultTable() {
        return new String[10][3];
    }

    @Override
	public SystemInterface getThermoSystem() {
        return null;
    }

    @Override
	public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }

    @Override
	public void printToFile(String name) {
    }

    @Override
	public void createNetCdfFile(String name) {

    }

    @Override
	public double[][] getPoints(int i) {
        return null;
    }

    @Override
	public void addData(String name, double[][] data) {

    }
}
