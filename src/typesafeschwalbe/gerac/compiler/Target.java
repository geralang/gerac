
package typesafeschwalbe.gerac.compiler;

// import typesafeschwalbe.gerac.compiler.backend.CCodeGen;
// import typesafeschwalbe.gerac.compiler.backend.CodeGen;
// import typesafeschwalbe.gerac.compiler.backend.JsCodeGen;

public enum Target {
    C("c"/*, CCodeGen::new */),
    JAVASCRIPT("js"/*, JsCodeGen::new */);

    public final String targetName; // name used in target blocks
    // public final CodeGen.Constructor codeGen;

    private Target(String targetName/*, CodeGen.Constructor codeGen*/) {
        this.targetName = targetName;
        // this.codeGen = codeGen;
    }
}
