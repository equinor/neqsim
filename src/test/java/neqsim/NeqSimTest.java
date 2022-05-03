package neqsim;

/**
 * @author ESOL
 *
 */
public class NeqSimTest {
  public NeqSimTest() {
    // Setting NeqSim to use test parameter database
    neqsim.util.database.NeqSimDataBase
        .setConnectionString("jdbc:derby:classpath:data/neqsimtestdatabase");
  }
}
