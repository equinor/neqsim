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
 * pTphaseEnvelope.java
 *
 * Created on 14. oktober 2000, 21:59
 */

package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import java.awt.*;
import javax.swing.*;
import neqsim.dataPresentation.visAD.visAd3D.visAd3DPlot;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.BaseOperation;
import neqsim.thermodynamicOperations.OperationInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;


/**
 *
 * @author  Even Solbraa
 * @version
 */
public class HPTphaseEnvelope extends BaseOperation implements OperationInterface, java.io.Serializable{

    private static final long serialVersionUID = 1000;
    
    double[][] points = new double[10][10];
    SystemInterface system;
    ThermodynamicOperations testOps;
    JProgressBar monitor;
    JFrame mainFrame;
    JPanel mainPanel;
    double startPressure=1, endPressure=0, startTemperature=160, endTemperature=0;
    
    /** Creates new bubblePointFlash */
    public HPTphaseEnvelope() {
    }
    
    public HPTphaseEnvelope(SystemInterface system) {
        testOps = new ThermodynamicOperations(system);
        this.system = system;
        mainFrame = new JFrame("Progress Bar");
        mainPanel = new JPanel();
        mainPanel.setSize(200,100);
        mainFrame.getContentPane().setLayout(new FlowLayout());
        mainPanel.setLayout(new FlowLayout());
        mainFrame.setSize(200,100);
        monitor = new JProgressBar(0,1000);
        monitor.setSize(200,100);
        monitor.setStringPainted(true);
        mainPanel.add(monitor);
        mainFrame.getContentPane().add(mainPanel);
        mainFrame.setVisible(true);
    }
    
    
    public void run(){
        
        int np =0;
        
        for(int i = 0;i<10;i++){
            system.setPressure(i*0.5+startPressure);
            for(int j=0;j<10;j++){
                np++;
                if(np%2==0){
                    monitor.setValue(np);
                    monitor.setString("Calculated points: " + np);
                }
                
                system.setTemperature(startTemperature+j);
                testOps.TPflash();
                system.init(3);
                points[i][j] = system.getEnthalpy();
            }
        }
    }
    
    public void displayResult(){
        try{
            mainFrame.setVisible(false);
            visAd3DPlot plot = new visAd3DPlot("pressure[bar]", "temperature[K]", "enthalpy[J/mol]");
            plot.setXYvals(150, 160, 10, 10, 20, 10);
            plot.setZvals(points);
            plot.init();
        } catch(Exception e){
            System.out.println("plotting failed");
        }
    }
    
    public void printToFile(String name) {
    }
    
    
    public double[][] getPoints(int i){
        return points;
    }
    
    public void createNetCdfFile(String name){
    }
    public org.jfree.chart.JFreeChart getJFreeChart(String name){
        return null;
    }
    
    public String[][] getResultTable(){
        return null;
    }
}
