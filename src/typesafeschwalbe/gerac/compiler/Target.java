
package typesafeschwalbe.gerac.compiler;

public enum Target {
    C("c"),
    JAVASCRIPT("js");

    public final String targetName; // name used in target blocks

    private Target(String targetName) {
        this.targetName = targetName;
    }
}
