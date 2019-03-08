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

package neqsim.util.database;

/*
 * testPointbase.java
 *
 * Created on 1. november 2001, 08:56
 */

import java.sql.*;
/**
 *
 * @author  esol
 * @version
 */
public class NeqSimDataBaseMYSQL implements neqsim.util.util.FileSystemSettings, java.io.Serializable{

    private static final long serialVersionUID = 1000;
    
    static boolean started = false;
    protected Connection databaseConnection = null;
    public static boolean useOnlineBase = false;
    private static String dataBaseType = "mySQL";//"MSAccess";//"mySQL";//"oracle", "oracleST";
    static int numb=0;
    private Statement statement = null;
    
    
    /** Creates new testPointbase */
    public NeqSimDataBaseMYSQL() {
        
        if(!started){
            try{
                if(useOnlineBase){
                    Class.forName("com.mysql.jdbc.Driver").newInstance();
                } else{
                    numb++;
                    if(dataBaseType.equals("MSAccess")){
                        if(numb==1) {
                            Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").newInstance();
                        }
                    } else if(dataBaseType.equals("mySQL")){
                        if(numb==1) {
                            Class.forName("com.mysql.jdbc.Driver").newInstance();
                        }
                    } else if(dataBaseType.equals("oracle")){
                        if(numb==1) {
                            Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
                        }
                    } else if(dataBaseType.equals("oracleST")){
                        if(numb==1) {
                            Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
                        }
                    } else{
                        if(numb==1) {
                            Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
                        }
                    }
                }
            } catch(Exception ex){
                System.out.println("error in NeqSimDataBase " +ex.toString());
                System.out.println("The database must be rgistered on the local DBMS to work.");
            }
        }
        started= true;
        
        try{
            databaseConnection = this.openConnection("NeqSimDatabase");
            setStatement(databaseConnection.createStatement());
        } catch(Exception ex){
            System.out.println("SQLException "  + ex.getMessage());
            
        }
    }
    /** Creates new NeqSimDataBase */
    
    public Connection openConnection(String database) throws SQLException, ClassNotFoundException{
        javax.naming.InitialContext ctx = null;
        javax.sql.DataSource ds = null;
        
        try{
            
            if(useOnlineBase){
                Class.forName("com.mysql.jdbc.Driver");
                return DriverManager.getConnection("jdbc:mysql:" + database);
            } else{
                
                if(dataBaseType.equals("MSAccess")){
                    //return DriverManager.getConnection("jdbc:odbc:" + database);
                    String dir="";
                    if(System.getProperty("NeqSim.home")==null){
                        dir = neqsim.util.util.FileSystemSettings.root +"\\java\\neqsim";
                    } else{
                        dir =  System.getProperty("NeqSim.home");
                    }
                    //return DriverManager.getConnection("jdbc:odbc:" + database);
                    return DriverManager.getConnection("jdbc:odbc:DRIVER={Microsoft Access Driver (*.mdb)};DBQ="+dir+"\\data\\"+database);
                } else if (dataBaseType.equals("mySQL5")) {
                    return DriverManager.getConnection("jdbc:mysql://80.202.29.232/test","solbraa","ev2467en");
                } else if (dataBaseType.equals("mySQL")) {
                    return DriverManager.getConnection("jdbc:mysql://localhost/neqsimdatabase","root","ev2467en");//,"even","even");//"?/user=even&password=even");
                } else if (dataBaseType.equals("oracle")) {
                    return DriverManager.getConnection("jdbc:oracle:thin:@db13.statoil.no:10002:U461","neqsim", "ev2467en");
                } else if(dataBaseType.equals("mySQL2")){
                    ctx = new javax.naming.InitialContext();
                    ds = (javax.sql.DataSource) ctx.lookup("java:comp/env/jdbc/NeqsimDataSource");
                    if(ctx!=null) {
                        ctx.close();
                    }
                    return ds.getConnection();
                } else {
                    return DriverManager.getConnection("jdbc:odbc:" + database);
                }
            }
        } catch(Exception ex){
            System.out.println("SQLException "  + ex.getMessage());
            System.out.println("error in NeqSimDataBase " +ex.toString());
            System.out.println("The database must be rgistered on the local DBMS to work.");
        } finally{
            try{
                if(ctx!=null) {
                    ctx.close();
                }
            } catch(Exception e){
                e.printStackTrace();
            }
        }
        return null;
    }
    
    public Connection getConnection(){
        return databaseConnection;
    }
    
    public ResultSet getResultSet(String database, String sqlString){
        try{
            ResultSet result =  getStatement().executeQuery(sqlString);
            return result;
        } catch(Exception e){
            System.out.println("error in NeqSimbataBase " +e.toString());
            System.out.println("The database must be rgistered on the local DBMS to work.");
        }
        return null;
    }
    
    public ResultSet getResultSet(String sqlString){
        return this.getResultSet("NeqSimDatabase",sqlString);
    }
    
    public void execute(String sqlString){
        try{
            if(databaseConnection==null){
                databaseConnection = this.openConnection("NeqSimDatabase");
                setStatement(databaseConnection.createStatement());
            }
            getStatement().execute(sqlString);
        } catch(Exception e){
            System.out.println("error in NeqSimDataBase " +e.toString());
            System.out.println("The database must be rgistered on the local DBMS to work.");
        }
    }
    
    public static void main(String[] args){
        NeqSimDataBaseMYSQL database = new NeqSimDataBaseMYSQL();
        ResultSet dataSet =  database.getResultSet("NeqSimDatabase",  "SELECT * FROM COMP WHERE NAME='nitrogen'");
        try{
            dataSet.next();
            System.out.println("dataset " + dataSet.getString("molarmass"));
        } catch(Exception e){System.out.println("failed " + e.toString());}
        System.out.println("ok");
    }
    
    public static String getDataBaseType() {
        return dataBaseType;
    }
    
    public static void setDataBaseType(String aDataBaseType) {
        dataBaseType = aDataBaseType;
    }
    
    public Statement getStatement() {
        return statement;
    }
    
    public void setStatement(Statement statement) {
        this.statement = statement;
    }
    
}