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
 * NetCdf.java
 *
 * Created on 5. august 2001, 21:52
 */

package neqsim.dataPresentation.fileHandeling.createTextFile;


import java.io.*;
//import ucar.nc2.*;
/**
 *
 * @author  esol
 * @version
 */
public class TextFile implements java.io.Serializable{

    private static final long serialVersionUID = 1000;
    
    String fileName = "c:/example.txt";
    String[][] values;
    //NetcdfFileWriteable ncfile;
    
    /** Creates new NetCdf */
    public TextFile() {
    }
    
    public void setOutputFileName(String name){
        this.fileName = name;
    }
    
    public void newFile(String name){
        try{
            File outputFile = new File(name);            
            FileWriter out = new FileWriter(outputFile);
            out.write("");
        }
        catch(Exception e){
            System.out.println(e.toString());
        }
    }
    
    public void setValues(String[][] values){
        System.out.println("writing " + values[0][0] + "  data");
        this.values = values;
    }
    
      public void setValues(double[][] valuesloca){
        values = new String[valuesloca[0].length][valuesloca.length];
        //System.out.println("writing " + values[0][0] + "  data");
        for(int i=0;i<values.length;i++){
            for(int j=0;j<values[0].length;j++){
                values[i][j] = Double.toString(valuesloca[j][i]) + " ";
            }
        }
    }
    
    public void createFile(){
        System.out.println("writing " + values[0][0] + "  data");
        System.out.println("length " + values.length);
        System.out.println("length2 " + values[0].length);
        
        try{
            File outputFile = new File(fileName);
            FileWriter out = new FileWriter(outputFile,true);
            
            for(int i=0;i<values.length;i++){
                for(int j=0;j<values[0].length;j++){
                    if(values[i][j]!=null) {
                        out.write(values[i][j]);
                    }
                    out.write("\t");
                }
                out.write("\n");
            }
            out.flush();
            out.close();
        }
        catch(Exception e){
            System.err.println("error writing file: " + e.toString());
        }
        System.out.println("writing data to file: " + fileName + " ... ok");
    }
    
}
