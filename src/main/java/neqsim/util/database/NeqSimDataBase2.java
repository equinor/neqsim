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
 * NeqSimDataBase.java
 *
 * Created on 17. mai 2001, 20:25
 */

package neqsim.util.database;

import java.sql.*;
/**
 *
 * @author  esol
 * @version
 */
public class NeqSimDataBase2 {

    private static final long serialVersionUID = 1000;
    
    public static boolean useOnlineBase = false;
    /** Creates new NeqSimDataBase */
    public NeqSimDataBase2() {
    }
    
    public Connection openConnection(String database) throws SQLException, ClassNotFoundException{
        if(useOnlineBase){
            Class.forName("org.gjt.mm.mysql.Driver");
            return DriverManager.getConnection("jdbc:mysql:" + database);
        }
        else{
            Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
            return DriverManager.getConnection("jdbc:odbc:DRIVER={Microsoft Access Driver (*.mdb)};DBQ=c:\\NeqSimDatabase2.mdb");
            //return DriverManager.getConnection("jdbc:odbc:" + database);
        }
    }
    
    public ResultSet getResultSet(String database, String sqlString){
        ResultSet result = null;
        try{
            Connection databaseConnection = this.openConnection(database);
            Statement statement = databaseConnection.createStatement();
            result =  statement.executeQuery(sqlString);
        }
        catch(Exception e){
            System.out.println("error in NeqSimDataBase " +e.toString());
            System.out.println("The database must be rgistered on the local DBMS to work.");
        }
        return result;
    }
    
}
