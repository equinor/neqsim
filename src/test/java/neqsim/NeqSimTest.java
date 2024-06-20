package neqsim;
//test
/**
 * Abstract class for Tests requiring a NeqSimDataBase.
 */
public abstract class NeqSimTest {
  /**
   * Constructor for NeqSimTest object.
   */
  public NeqSimTest() {
    // Setting NeqSim to use test parameter database
    // neqsim.util.database.NeqSimDataBase
    // .setConnectionString("jdbc:derby:classpath:data/neqsimtestdatabase");
    // neqsim.util.database.NeqSimDataBase.setDataBaseType("Derby");
  }
}
