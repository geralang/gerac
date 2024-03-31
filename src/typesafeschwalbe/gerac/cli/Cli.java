
package typesafeschwalbe.gerac.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.Result;

public class Cli {
    
    private interface Argument {
        char shortName();
        String longName();
        String description();
        String valueDescription();
        boolean hasValue();
        boolean isRequired();
    }

    public static record RequiredArgument(
        char shortName, String longName, String description,
        String valueDescription
    ) implements Argument {
        @Override public boolean hasValue() { return true; }
        @Override public boolean isRequired() { return true; }
    }

    public static record OptionalArgument(
        char shortName, String longName, String description,
        String valueDescription
    ) implements Argument {
        @Override public boolean hasValue() { return true; }
        @Override public boolean isRequired() { return false; }
    }

    public static record Flag(
        char shortName, String longName, String description
    ) implements Argument {
        @Override public boolean hasValue() { return false; }
        @Override public boolean isRequired() { return false; }
        @Override public String valueDescription() { return null; }
    }


    public static class Values {
        
        private final Map<RequiredArgument, String> required;
        private final Map<OptionalArgument, Optional<String>> optional;
        private final Map<Flag, Boolean> flags;
        private final List<String> free;

        private Values(
            Map<RequiredArgument, String> required,
            Map<OptionalArgument, Optional<String>> optional,
            Map<Flag, Boolean> flags,
            List<String> free
        ) {
            this.required = required;
            this.optional = optional;
            this.flags = flags;
            this.free = free;
        }

        public String get(RequiredArgument arg) {
            if(!this.required.containsKey(arg)) {
                throw new IllegalArgumentException(
                    "The given argument was not registered!"
                );
            }
            return this.required.get(arg);
        }

        public Optional<String> get(OptionalArgument arg) {
            if(!this.optional.containsKey(arg)) {
                throw new IllegalArgumentException(
                    "The given argument was not registered!"
                );
            }
            return this.optional.get(arg);
        }

        public boolean get(Flag flag) {
            if(!this.flags.containsKey(flag)) {
                throw new IllegalArgumentException(
                    "The given flag was not registered!"
                );
            }
            return this.flags.get(flag);
        }

        public List<String> free() {
            return this.free;
        }

    }


    private final List<RequiredArgument> required;
    private final List<OptionalArgument> optional;
    private final List<Flag> flags;
    private final Set<String> registered;

    public Cli() {
        this.required = new ArrayList<>();
        this.optional = new ArrayList<>();
        this.flags = new ArrayList<>();
        this.registered = new HashSet<>();
        this.add(new Cli.Flag(
            'h', "help", "displays a list of all available commands"
        ));
    }

    public Cli add(RequiredArgument arg) {
        if(registered.contains(arg.longName)) {
            throw new IllegalArgumentException(
                "The given argument was already registered!"
            );
        }
        this.required.add(arg);
        this.registered.add(arg.longName);
        return this;
    }

    public Cli add(OptionalArgument arg) {
        if(registered.contains(arg.longName)) {
            throw new IllegalArgumentException(
                "The given argument was already registered!"
            );
        }
        this.optional.add(arg);
        this.registered.add(arg.longName);
        return this;
    }

    public Cli add(Flag flag) {
        if(registered.contains(flag.longName)) {
            throw new IllegalArgumentException(
                "The given argument was already registered!"
            );
        }
        this.flags.add(flag);
        this.registered.add(flag.longName);
        return this;
    }

    private static Result<Values> invalidArgument(String arg) {
        return Result.ofError(new Error(
            "'" + arg + "' is not a valid argument"
        ));
    }

    private static Result<Values> missingValue(String arg) {
        return Result.ofError(new Error(
            "'" + arg + "' does not have a value specified"
        ));
    }

    private static Result<Values> missingArgument(RequiredArgument arg) {
        return Result.ofError(new Error(
            "The argument '"
                + "('--" + arg.longName + "' / '-" + arg.shortName + "')"
                + " [" + arg.valueDescription + "]"
                + "' is required but missing"
        ));
    }

    private Argument lookUpArgument(String longName) {
        for(RequiredArgument arg: this.required) {
            if(arg.longName.equals(longName)) { return arg; }
        }
        for(OptionalArgument arg: this.optional) {
            if(arg.longName.equals(longName)) { return arg; }
        }
        for(Flag arg: this.flags) {
            if(arg.longName.equals(longName)) { return arg; }
        }
        return null;
    }

    private Argument lookUpArgument(char shortName) {
        for(RequiredArgument arg: this.required) {
            if(arg.shortName == shortName) { return arg; }
        }
        for(OptionalArgument arg: this.optional) {
            if(arg.shortName == shortName) { return arg; }
        }
        for(Flag arg: this.flags) {
            if(arg.shortName == shortName) { return arg; }
        }
        return null;
    }

    private static void printArgumentHelp(Argument arg) {
        System.out.println(
            "    -" + arg.shortName()
                + (arg.hasValue()? " <" + arg.valueDescription() + ">" : "")
        );
        System.out.println(
            "    --" + arg.longName()
                + (arg.hasValue()? " <" + arg.valueDescription() + ">" : "")
        );
        System.out.println(
            "                " + arg.description()
        );
    }

    public Result<Values> parse(String[] args) {
        Map<RequiredArgument, String> required = new HashMap<>();
        Map<OptionalArgument, Optional<String>> optional = new HashMap<>();
        Map<Flag, Boolean> flags = new HashMap<>();
        List<String> free = new ArrayList<>();
        for(int argIdx = 0; argIdx < args.length; argIdx += 1) {
            String arg = args[argIdx];
            Argument argObj;
            if(arg.startsWith("--")) {
                argObj = this.lookUpArgument(arg.substring(2));
            } else if(arg.startsWith("-")) {
                if(arg.length() > 2) {
                    return Cli.invalidArgument(arg);
                }
                argObj = this.lookUpArgument(arg.charAt(1));
            } else {
                free.add(arg);
                continue;
            }
            if(argObj == null) {
                return Cli.invalidArgument(arg);
            }
            if(argObj.hasValue()) {
                if(argIdx + 1 >= args.length
                        || args[argIdx + 1].startsWith("-")) {
                    return Cli.missingValue(arg);
                }
                String value = args[argIdx + 1];
                argIdx += 1;
                if(argObj.isRequired()) {
                    required.put((RequiredArgument) argObj, value);
                } else {
                    optional.put((OptionalArgument) argObj, Optional.of(value));
                }
            } else {
                if(argObj.longName().equals("help")) {
                    System.out.println("List of available arguments:");
                    System.out.println("Required:");
                    for(RequiredArgument carg: this.required) {
                        Cli.printArgumentHelp(carg);
                    }
                    System.out.println("Optional:");
                    for(OptionalArgument carg: this.optional) {
                        Cli.printArgumentHelp(carg);
                    }
                    for(Flag carg: this.flags) {
                        Cli.printArgumentHelp(carg);
                    }
                    System.exit(1);
                }
                flags.put((Flag) argObj, true);
            }
        }
        for(RequiredArgument arg: this.required) {
            if(required.containsKey(arg)) {
                continue;
            }
            return Cli.missingArgument(arg);
        }
        for(OptionalArgument arg: this.optional) {
            if(optional.containsKey(arg)) {
                continue;
            }
            optional.put(arg, Optional.empty());
        }
        for(Flag arg: this.flags) {
            if(flags.containsKey(arg)) {
                continue;
            }
            flags.put(arg, false);
        }
        return Result.ofValue(new Values(required, optional, flags, free));
    }

}
