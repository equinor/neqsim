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
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.util.database;

import java.sql.*;

/**
 *
 * @author esol
 */
public class AspenIP21Database implements neqsim.util.util.FileSystemSettings, java.io.Serializable {

    private static final long serialVersionUID = 1000;

    protected Connection databaseConnection = null;//ss
    private static String dataBaseType = "Karsto";
    private Statement statement = null;

    public AspenIP21Database() {

        try {
            if (dataBaseType.equals("Karsto")) {
                Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").newInstance();
            }
        } catch (Exception ex) {
            System.out.println("error in Online Karsto " + ex.toString());
            System.out.println("The database must be registered on the local DBMS to work.");
        }

        try {
            databaseConnection = this.openConnection("Karsto");
            setStatement(databaseConnection.createStatement());
        } catch (Exception ex) {
            System.out.println("SQLException " + ex.getMessage());

        }
    }

    public Connection openConnection(String database) throws SQLException, ClassNotFoundException {
        javax.naming.InitialContext ctx = null;
        javax.sql.DataSource ds = null;

        try {
        } catch (Exception ex) {
            System.out.println("SQLException " + ex.getMessage());
            System.out.println("error in Kårstø DB " + ex.toString());
            System.out.println("The Kårstø database must be registered on the local DBMS to work.");
        } finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void setStatement(Statement statement) {
        this.statement = statement;
    }

    public ResultSet getResultSet(String sqlString) {
        return this.getResultSet("Karsto", sqlString);
    }

    public ResultSet getResultSet(String database, String sqlString) {
        try {
            ResultSet result = getStatement().executeQuery(sqlString);
            return result;
        } catch (Exception e) {
            System.out.println("error in DB " + e.toString());
            System.out.println("The database must be rgistered on the local DBMS to work.");
        }
        return null;
    }

    public Statement getStatement() {
        return statement;
    }

    public static void main(String[] args) {
        AspenIP21Database database = new AspenIP21Database();
        ResultSet dataSet = database.getResultSet("Karsto", "SELECT * FROM IP_AnalogDef WHERE NAME='21TI1117'");
        try {
                     while (dataSet.next()) {

                         System.out.println("dataset " + dataSet.getString(4));
                         System.out.println("dataset value " + dataSet.getDouble("IP_VALUE"));
            }
        } catch (Exception e) {
            System.out.println("failed " + e.toString());
        }
        System.out.println("ok");
    }
}
