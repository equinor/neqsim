package neqsim.util;


public abstract class NamedBaseClass implements NamedInterface, java.io.Serializable {
    public String name;

    public NamedBaseClass(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
