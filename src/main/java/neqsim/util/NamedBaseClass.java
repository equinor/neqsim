package neqsim.util;

public class NamedBaseClass implements NamedInterface {
    protected String name;

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
