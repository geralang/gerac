
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.ErrorException;
import typesafeschwalbe.gerac.compiler.Ref;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Target;

public class SourceParser extends Parser {

    private final Target target;
    
    public SourceParser(Lexer lexer, Target target) throws ErrorException {
        super(lexer);
        this.target = target;
    }

    public List<AstNode> parseGlobalStatements() throws ErrorException {
        return this.parseStatements(GLOBALLY_SCOPED);
    }

    private static final boolean GLOBALLY_SCOPED = true;
    private static final boolean LOCALLY_SCOPED = false;

    private List<AstNode> parseStatements(
        boolean inGlobalScope
    ) throws ErrorException {
        List<AstNode> nodes = new ArrayList<>();
        while(this.current.type != Token.Type.BRACE_CLOSE
                && this.current.type != Token.Type.FILE_END) {
            nodes.addAll(this.parseStatement(inGlobalScope));
        }
        return nodes;
    }

    private static record Usages(List<Namespace> paths, Source source) {}

    private Usages parseUsages() throws ErrorException {
        this.expect(
            Token.Type.PAREN_OPEN,
            Token.Type.IDENTIFIER,
            Token.Type.ASTERISK
        );
        Token start = this.current;
        Token end = this.current;
        List<String> segments = new ArrayList<>();
        while(this.current.type == Token.Type.IDENTIFIER) {
            segments.add(this.current.content);
            end = this.current;
            this.next();
            if(this.current.type != Token.Type.DOUBLE_COLON) {
                return new Usages(
                    List.of(new Namespace(segments)),
                    new Source(start.source, end.source)
                );
            }
            this.next();
        }
        this.expect(Token.Type.PAREN_OPEN, Token.Type.ASTERISK);
        if(this.current.type == Token.Type.ASTERISK) {
            segments.add(this.current.content);
            end = this.current;
            this.next();
            return new Usages(
                List.of(new Namespace(segments)),
                new Source(start.source, end.source)
            );
        }
        this.expect(Token.Type.PAREN_OPEN);
        this.next();
        List<Namespace> paths = new ArrayList<>();
        while(this.current.type != Token.Type.PAREN_CLOSE) {
            Usages usages = this.parseUsages();
            for(Namespace path: usages.paths) {
                List<String> finalSegments = new ArrayList<>(segments);
                finalSegments.addAll(path.elements());
                paths.add(new Namespace(finalSegments));
            }
            this.expect(Token.Type.COMMA, Token.Type.PAREN_CLOSE);
            if(this.current.type == Token.Type.COMMA) {
                this.next();
            }
        }
        end = this.current;
        this.next();
        return new Usages(paths, new Source(start.source, end.source));
    }

    private List<AstNode> parseStatement(
        boolean inGlobalScope
    ) throws ErrorException {
        boolean isPublic = this.current.type == Token.Type.KEYWORD_PUBLIC;
        if(isPublic) {
            if(!inGlobalScope) {
                throw new ErrorException(new Error(
                    "'pub' used in local context",
                    Error.Marking.error(
                        this.current.source,
                        "'pub' may only be used in the global scope"
                    )
                ));
            }
            this.next();
            this.expect(Token.Type.KEYWORD_PROCEDURE, Token.Type.KEYWORD_VALUE);
        }
        if(inGlobalScope) {
            this.expect(
                Token.Type.KEYWORD_PROCEDURE,
                Token.Type.KEYWORD_VALUE,
                Token.Type.KEYWORD_TARGET,
                Token.Type.KEYWORD_MODULE,
                Token.Type.KEYWORD_USE
            );
        }
        Token start = this.current;
        switch(this.current.type) {
            case KEYWORD_PROCEDURE: {
                if(!inGlobalScope) {
                    throw new ErrorException(new Error(
                        "Procedure in local context",
                        Error.Marking.error(
                            this.current.source,
                            "procedures may only be defined in the global scope"
                        )
                    ));
                }
                this.next();
                this.expect(Token.Type.IDENTIFIER);
                String name = this.current.content;
                this.next();
                this.expect(Token.Type.PAREN_OPEN);
                this.next();
                List<String> argumentNames = new ArrayList<>();
                while(this.current.type != Token.Type.PAREN_CLOSE) {
                    this.expect(Token.Type.IDENTIFIER);
                    argumentNames.add(this.current.content);
                    this.next();
                    this.expect(Token.Type.COMMA, Token.Type.PAREN_CLOSE);
                    if(this.current.type == Token.Type.COMMA) {
                        this.next();
                    }
                }
                this.next();
                this.expect(Token.Type.BRACE_OPEN, Token.Type.EQUALS);
                List<AstNode> body;
                Source endSource;
                if(this.current.type == Token.Type.EQUALS) {
                    Source returnSourceStart = this.current.source;
                    this.next();
                    AstNode value = this.parseExpression(true);
                    body = List.of(new AstNode(
                        AstNode.Type.RETURN,
                        new AstNode.MonoOp(value),
                        new Source(returnSourceStart, value.source)
                    ));
                    endSource = value.source;
                } else {
                    this.next();
                    body = this.parseStatements(LOCALLY_SCOPED);
                    this.expect(Token.Type.BRACE_CLOSE);
                    endSource = this.current.source;
                    this.next();
                }
                return List.of(new AstNode(
                    AstNode.Type.PROCEDURE,
                    new AstNode.Procedure(
                        isPublic, name, argumentNames, body
                    ),
                    new Source(start.source, endSource)
                ));
            }
            case KEYWORD_VALUE:
            case KEYWORD_MUTABLE: {
                boolean isMutable = this.current.type
                    == Token.Type.KEYWORD_MUTABLE;
                this.next();
                this.expect(Token.Type.IDENTIFIER);
                String name = this.current.content;
                Token nameToken = this.current;
                this.next();
                if(inGlobalScope) {
                    this.expect(Token.Type.EQUALS);
                }
                Optional<AstNode> value = Optional.empty();
                Source source = new Source(start.source, nameToken.source);
                if(this.current.type == Token.Type.EQUALS) {
                    this.next();
                    value = Optional.of(this.parseExpression(!inGlobalScope));
                    source = new Source(start.source, value.get().source);
                }
                return List.of(new AstNode(
                    AstNode.Type.VARIABLE,
                    new AstNode.Variable(
                        isPublic, isMutable, name,
                        new Ref<>(Optional.empty()),
                        value
                    ),
                    source
                ));
            }
            case KEYWORD_CASE: {
                this.next();
                AstNode value = this.parseExpression(true);
                this.expect(Token.Type.ARROW, Token.Type.BRACE_OPEN);
                if(this.current.type == Token.Type.ARROW) {
                    this.next();
                    List<AstNode> ifBody;
                    Source endSource;
                    List<AstNode> elseBody;
                    if(this.current.type == Token.Type.BRACE_OPEN) {
                        this.next();
                        ifBody = this.parseStatements(LOCALLY_SCOPED);
                        this.expect(Token.Type.BRACE_CLOSE);
                        endSource = this.current.source;
                        this.next();
                    } else {
                        ifBody = this.parseStatement(LOCALLY_SCOPED);
                        endSource = ifBody.get(ifBody.size() - 1).source;
                    }
                    if(this.current.type == Token.Type.KEYWORD_ELSE) {
                        this.next();
                        if(this.current.type == Token.Type.BRACE_OPEN) {
                            this.next();
                            elseBody = this.parseStatements(LOCALLY_SCOPED);
                            this.expect(Token.Type.BRACE_CLOSE);
                            endSource = this.current.source;
                            this.next();
                        } else {
                            elseBody = this.parseStatement(
                                LOCALLY_SCOPED
                            );
                            endSource = elseBody.get(elseBody.size() - 1)
                                .source;
                        }
                    } else {
                        elseBody = List.of();
                    }
                    return List.of(new AstNode(
                        AstNode.Type.CASE_CONDITIONAL,
                        new AstNode.CaseConditional(value, ifBody, elseBody),
                        new Source(start.source, endSource)
                    ));
                } else {
                    this.next();
                    if(this.current.type == Token.Type.HASHTAG) {
                        List<String> branchVariants = new ArrayList<>();
                        List<Optional<String>> branchVariableNames
                            = new ArrayList<>();
                        List<List<AstNode>> branchBodies = new ArrayList<>();
                        while(this.current.type != Token.Type.BRACE_CLOSE) {
                            this.expect(Token.Type.HASHTAG);
                            this.next();
                            this.expect(Token.Type.IDENTIFIER);
                            branchVariants.add(this.current.content);
                            this.next();
                            this.expect(
                                Token.Type.IDENTIFIER, Token.Type.ARROW
                            );
                            branchVariableNames.add(
                                this.current.type == Token.Type.IDENTIFIER
                                ? Optional.of(this.current.content)
                                : Optional.empty()
                            );
                            if(this.current.type == Token.Type.IDENTIFIER) {
                                this.next();
                            }
                            this.expect(Token.Type.ARROW);
                            this.next();
                            List<AstNode> branchBody;
                            if(this.current.type == Token.Type.BRACE_OPEN) {
                                this.next();
                                branchBody = this.parseStatements(
                                    LOCALLY_SCOPED
                                );
                                this.expect(Token.Type.BRACE_CLOSE);
                                this.next();
                            } else {
                                branchBody = this.parseStatement(
                                    LOCALLY_SCOPED
                                );
                            }
                            branchBodies.add(branchBody);
                        }
                        Source endSource = this.current.source;
                        this.next();
                        Optional<List<AstNode>> elseBody;
                        if(this.current.type == Token.Type.KEYWORD_ELSE) {
                            this.next();
                            if(this.current.type == Token.Type.BRACE_OPEN) {
                                this.next();
                                elseBody = Optional.of(
                                    this.parseStatements(LOCALLY_SCOPED)
                                );
                                this.expect(Token.Type.BRACE_CLOSE);
                                endSource = this.current.source;
                                this.next();
                            } else {
                                elseBody = Optional.of(
                                    this.parseStatement(LOCALLY_SCOPED)
                                );
                                endSource = elseBody.get().get(
                                    elseBody.get().size() - 1
                                ).source;
                            }
                        } else {
                            elseBody = Optional.empty();
                        }
                        return List.of(new AstNode(
                            AstNode.Type.CASE_VARIANT,
                            new AstNode.CaseVariant(
                                value, branchVariants, branchVariableNames,
                                branchBodies, elseBody
                            ),
                            new Source(start.source, endSource)
                        ));
                    } else {
                        List<AstNode> branchValues = new ArrayList<>();
                        List<List<AstNode>> branchBodies = new ArrayList<>();
                        while(this.current.type != Token.Type.BRACE_CLOSE) {
                            AstNode branchValue = this.parseExpression(false);
                            branchValues.add(branchValue);
                            this.expect(Token.Type.ARROW);
                            this.next();
                            List<AstNode> branchBody;
                            if(this.current.type == Token.Type.BRACE_OPEN) {
                                this.next();
                                branchBody = this.parseStatements(
                                    LOCALLY_SCOPED
                                );
                                this.expect(Token.Type.BRACE_CLOSE);
                                this.next();
                            } else {
                                branchBody = this.parseStatement(
                                    LOCALLY_SCOPED
                                );
                            }
                            branchBodies.add(branchBody);
                        }
                        Source endSource = this.current.source;
                        this.next();
                        List<AstNode> elseBody;
                        if(this.current.type == Token.Type.KEYWORD_ELSE) {
                            this.next();
                            if(this.current.type == Token.Type.BRACE_OPEN) {
                                this.next();
                                elseBody = this.parseStatements(LOCALLY_SCOPED);
                                this.expect(Token.Type.BRACE_CLOSE);
                                endSource = this.current.source;
                                this.next();
                            } else {
                                elseBody = this.parseStatement(
                                    LOCALLY_SCOPED
                                );
                                endSource = elseBody.get(elseBody.size() - 1)
                                    .source;
                            }
                        } else {
                            elseBody = List.of();
                        }
                        return List.of(new AstNode(
                            AstNode.Type.CASE_BRANCHING,
                            new AstNode.CaseBranching(
                                value, branchValues, branchBodies, elseBody
                            ),
                            new Source(start.source, endSource)
                        ));
                    }
                }
            }
            case KEYWORD_RETURN: {
                this.next();
                AstNode value = this.parseExpression(true);
                return List.of(new AstNode(
                    AstNode.Type.RETURN,
                    new AstNode.MonoOp(value),
                    new Source(start.source, value.source)
                ));
            }
            case KEYWORD_MODULE: {
                this.next();
                this.expect(Token.Type.IDENTIFIER);
                List<String> segments = new ArrayList<>();
                segments.add(this.current.content);
                Token end = this.current;
                this.next();
                while(this.current.type == Token.Type.DOUBLE_COLON) {
                    this.next();
                    this.expect(Token.Type.IDENTIFIER);
                    segments.add(this.current.content);
                    end = this.current;
                    this.next();
                }
                return List.of(new AstNode(
                    AstNode.Type.MODULE_DECLARATION,
                    new AstNode.NamespacePath(new Namespace(segments)),
                    new Source(start.source, end.source)
                ));
            }
            case KEYWORD_USE: {
                this.next();
                Usages usages = this.parseUsages();
                return List.of(new AstNode(
                    AstNode.Type.USE,
                    new AstNode.Usages(usages.paths),
                    new Source(start.source, usages.source)
                ));
            }
            case KEYWORD_TARGET: {
                this.next();
                this.expect(Token.Type.IDENTIFIER);
                String targetName = this.current.content;
                this.next();
                this.expect(Token.Type.BRACE_OPEN);
                this.next();
                List<AstNode> body = this.parseStatements(inGlobalScope);
                this.expect(Token.Type.BRACE_CLOSE);
                this.next();
                return targetName == this.target.targetName
                    ? body
                    : List.of();
            }
            default: {
                AstNode expr = this.parseExpression(true);
                if(this.current.type != Token.Type.EQUALS) {
                    return List.of(expr);
                }
                if(!expr.isAssignable()) {
                    throw new ErrorException(new Error(
                        "Expression cannot be assigned to",
                        Error.Marking.error(
                            expr.source,
                            "this expression is not mutable..."
                        ),
                        Error.Marking.error(
                            this.current.source,
                            "...so it may not be assigned to"
                        )
                    ));
                }
                this.next();
                AstNode value = this.parseExpression(true);
                return List.of(new AstNode(
                    AstNode.Type.ASSIGNMENT,
                    new AstNode.BiOp(expr, value),
                    new Source(expr.source, value.source)
                ));
            }
        }
    }

    private AstNode parseExpression(boolean inCalledScope) throws ErrorException {
        return this.parseExpression(999, inCalledScope);
    }

    private static AstNode.Type infixBiOpNodeType(Token operatorToken) {
        switch(operatorToken.type) {
            case PLUS: return AstNode.Type.ADD;
            case MINUS: return AstNode.Type.SUBTRACT;
            case ASTERISK: return AstNode.Type.MULTIPLY;
            case SLASH: return AstNode.Type.DIVIDE;
            case PERCENT: return AstNode.Type.MODULO;
            case LESS_THAN: return AstNode.Type.LESS_THAN;
            case GREATER_THAN: return AstNode.Type.GREATER_THAN;
            case LESS_THAN_EQUAL: return AstNode.Type.LESS_THAN_EQUAL;
            case GREATER_THAN_EQUAL: return AstNode.Type.GREATER_THAN_EQUAL;
            case DOUBLE_EQUALS: return AstNode.Type.EQUALS;
            case NOT_EQUALS: return AstNode.Type.NOT_EQUALS;
            case DOUBLE_PIPE: return AstNode.Type.OR;
            case DOUBLE_AMPERSAND: return AstNode.Type.AND;
            default: throw new RuntimeException("invalid token type!");
        }
    }

    private AstNode parseExpression(
        int precedence, boolean inCalledScope
    ) throws ErrorException {
        Optional<AstNode> previous = Optional.empty();
        while(true) {
            int currentPrecedence = this.current.type.infixPrecedence;
            if(previous.isPresent() && currentPrecedence >= precedence) {
                return previous.get();
            }
            Token start = this.current;
            if(previous.isPresent()) {
                switch(this.current.type) {
                    case PAREN_OPEN: {
                        AstNode called = previous.get();
                        this.next();
                        List<AstNode> arguments = new ArrayList<>();
                        while(this.current.type != Token.Type.PAREN_CLOSE) {
                            AstNode argument = this
                                .parseExpression(inCalledScope);
                            arguments.add(argument);
                            this.expect(
                                Token.Type.COMMA, Token.Type.PAREN_CLOSE
                            );
                            if(this.current.type == Token.Type.COMMA) {
                                this.next();
                            }
                        }
                        Token end = this.current;
                        this.next();
                        previous = Optional.of(new AstNode(
                            AstNode.Type.CALL,
                            new AstNode.Call(called, arguments),
                            new Source(called.source, end.source)
                        ));
                        continue;
                    }
                    case MEMBER_PIPE: {
                        AstNode accessed = previous.get();
                        this.next();
                        this.expect(Token.Type.IDENTIFIER);
                        String memberName = this.current.content;
                        this.next();
                        this.expect(Token.Type.PAREN_OPEN);
                        this.next();
                        List<AstNode> arguments = new ArrayList<>();
                        while(this.current.type != Token.Type.PAREN_CLOSE) {
                            AstNode argument = this
                                .parseExpression(inCalledScope);
                            arguments.add(argument);
                            this.expect(
                                Token.Type.COMMA, Token.Type.PAREN_CLOSE
                            );
                            if(this.current.type == Token.Type.COMMA) {
                                this.next();
                            }
                        }
                        Token end = this.current;
                        this.next();
                        previous = Optional.of(new AstNode(
                            AstNode.Type.METHOD_CALL,
                            new AstNode.MethodCall(
                                accessed, memberName, arguments
                            ),
                            new Source(accessed.source, end.source)
                        ));
                        continue;
                    }
                    case FUNCTION_PIPE: {
                        AstNode piped = previous.get();
                        this.next();
                        AstNode into = this.parseExpression(
                            Token.Type.FUNCTION_PIPE.infixPrecedence, 
                            inCalledScope
                        );
                        if(into.type != AstNode.Type.CALL) {
                            throw new ErrorException(new Error(
                                "Cannot be piped into",
                                Error.Marking.error(into.source, "not a call") 
                            ));
                        }
                        List<AstNode> arguments = new ArrayList<>();
                        arguments.add(piped);
                        arguments.addAll(
                            into.<AstNode.Call>getValue().arguments()
                        );
                        previous = Optional.of(new AstNode(
                            AstNode.Type.CALL,
                            new AstNode.Call(
                                into.<AstNode.Call>getValue().called(),
                                arguments
                            ),
                            new Source(piped.source, into.source)
                        ));
                        continue;
                    }
                    case DOT: {
                        AstNode accessed = previous.get();
                        this.next();
                        this.expect(Token.Type.IDENTIFIER);
                        String memberName = this.current.content;
                        Token end = this.current;
                        this.next();
                        previous = Optional.of(new AstNode(
                            AstNode.Type.OBJECT_ACCESS,
                            new AstNode.ObjectAccess(accessed, memberName),
                            new Source(accessed.source, end.source)
                        ));
                        continue;
                    }
                    case BRACKET_OPEN: {
                        AstNode accessed = previous.get();
                        this.next();
                        AstNode index = this.parseExpression(inCalledScope);
                        this.expect(Token.Type.BRACKET_CLOSE);
                        Token end = this.current;
                        this.next();
                        previous = Optional.of(new AstNode(
                            AstNode.Type.ARRAY_ACCESS,
                            new AstNode.BiOp(accessed, index),
                            new Source(accessed.source, end.source)
                        ));
                        continue;
                    }
                    case PLUS:
                    case MINUS:
                    case ASTERISK:
                    case SLASH:
                    case PERCENT:
                    case LESS_THAN:
                    case GREATER_THAN:
                    case LESS_THAN_EQUAL:
                    case GREATER_THAN_EQUAL:
                    case DOUBLE_EQUALS:
                    case NOT_EQUALS:
                    case DOUBLE_PIPE:
                    case DOUBLE_AMPERSAND: {
                        AstNode left = previous.get();
                        this.next();
                        AstNode right = this.parseExpression(
                            start.type.infixPrecedence, inCalledScope
                        );
                        previous = Optional.of(new AstNode(
                            SourceParser.infixBiOpNodeType(start),
                            new AstNode.BiOp(left, right),
                            new Source(left.source, right.source)
                        ));
                        continue;
                    }
                    case DOUBLE_DOT:
                    case DOUBLE_DOT_EQUALS: {
                        AstNode rangeStart = previous.get();
                        this.next();
                        AstNode rangeEnd = this.parseExpression(
                            start.type.infixPrecedence, inCalledScope
                        );
                        previous = Optional.of(new AstNode(
                            AstNode.Type.CALL,
                            new AstNode.Call(
                                new AstNode(
                                    AstNode.Type.MODULE_ACCESS,
                                    new AstNode.ModuleAccess(new Namespace(
                                        List.of(
                                            "core",
                                            start.type == Token.Type.DOUBLE_DOT
                                                ? "range"
                                                : "range_incl"
                                        )
                                    ), Optional.empty()),
                                    start.source
                                ),
                                List.of(rangeStart, rangeEnd)
                            ),
                            new Source(rangeStart.source, rangeEnd.source)
                        ));
                        continue;
                    }
                    case QUESTION_MARK: {
                        if(!inCalledScope) {
                            throw new ErrorException(new Error(
                                "Variant unwrap used in non-call context",
                                Error.Marking.error(
                                    this.current.source,
                                    "no value can be returned in this context,"
                                        + " but '?' might return a value"
                                )
                            ));
                        }
                        AstNode unwrapped = previous.get();
                        this.next();
                        this.expect(Token.Type.IDENTIFIER);
                        String variantName = this.current.content;
                        Token endToken = this.current;
                        this.next();
                        previous = Optional.of(new AstNode(
                            AstNode.Type.VARIANT_UNWRAP,
                            new AstNode.VariantUnwrap(unwrapped, variantName),
                            new Source(unwrapped.source, endToken.source)
                        ));
                        continue;
                    }
                    default: {
                        return previous.get();
                    }
                }
            }
            switch(this.current.type) {
                case DOUBLE_PIPE:
                case PIPE:
                case ARROW: {
                    List<String> argumentNames = new ArrayList<>();
                    List<Source> argumentSources = new ArrayList<>();
                    if(this.current.type == Token.Type.PIPE) {
                        this.next();
                        while(this.current.type != Token.Type.PIPE) {
                            this.expect(Token.Type.IDENTIFIER);
                            argumentNames.add(this.current.content);
                            argumentSources.add(this.current.source);
                            this.next();
                            this.expect(Token.Type.COMMA, Token.Type.PIPE);
                            if(this.current.type == Token.Type.COMMA) {
                                this.next();
                            }
                        }
                    } else if(this.current.type == Token.Type.ARROW) {
                        argumentNames.add("it");
                        argumentSources.add(start.source);
                    }
                    this.next();
                    List<AstNode> body;
                    Source endSource;
                    if(this.current.type == Token.Type.BRACE_OPEN) {
                        this.next();
                        body = this.parseStatements(LOCALLY_SCOPED);
                        this.expect(Token.Type.BRACE_CLOSE);
                        endSource = this.current.source;
                        this.next();
                    } else {
                        AstNode value = this.parseExpression(true);
                        endSource = value.source;
                        body = List.of(new AstNode(
                            AstNode.Type.RETURN,
                            new AstNode.MonoOp(value),
                            value.source
                        ));
                    }
                    previous = Optional.of(new AstNode(
                        AstNode.Type.CLOSURE,
                        new AstNode.Closure(
                            argumentNames,
                            new Ref<>(Optional.empty()),
                            new Ref<>(Optional.empty()),
                            new Ref<>(Optional.empty()),
                            body
                        ),
                        new Source(start.source, endSource)
                    ));
                    continue;
                }
                case BRACE_OPEN: {
                    this.next();
                    Map<String, AstNode> memberValues = new HashMap<>();
                    while(this.current.type != Token.Type.BRACE_CLOSE) {
                        this.expect(Token.Type.IDENTIFIER);
                        String memberName = this.current.content;
                        Source memberSource = this.current.source;
                        this.next();
                        this.expect(
                            Token.Type.EQUALS,
                            Token.Type.COMMA,
                            Token.Type.BRACE_CLOSE
                        );
                        AstNode memberValue;
                        if(this.current.type == Token.Type.EQUALS) {
                            this.next();
                            memberValue = this.parseExpression(inCalledScope);
                            this.expect(
                                Token.Type.COMMA, Token.Type.BRACE_CLOSE
                            );
                        } else {
                            memberValue = new AstNode(
                                AstNode.Type.MODULE_ACCESS,
                                new AstNode.ModuleAccess(
                                    new Namespace(List.of(memberName)),
                                    Optional.empty()
                                ),
                                memberSource
                            );
                        }
                        if(memberValues.containsKey(memberName)) {
                            throw new ErrorException(new Error(
                                "Duplicate object property",
                                Error.Marking.error(
                                    memberSource,
                                    "property '" + memberName + "'"
                                        + " appears more than once"
                                )
                            ));
                        }
                        memberValues.put(memberName, memberValue);
                        if(this.current.type == Token.Type.COMMA) {
                            this.next();
                        }
                    }
                    Token end = this.current;
                    this.next();
                    previous = Optional.of(new AstNode(
                        AstNode.Type.OBJECT_LITERAL,
                        new AstNode.ObjectLiteral(memberValues),
                        new Source(start.source, end.source)
                    ));
                    continue;
                }
                case BRACKET_OPEN: {
                    this.next();
                    if(this.current.type == Token.Type.BRACKET_CLOSE) {
                        Token end = this.current;
                        this.next();
                        previous = Optional.of(new AstNode(
                            AstNode.Type.ARRAY_LITERAL,
                            new AstNode.ArrayLiteral(List.of()),
                            new Source(start.source, end.source)
                        ));
                        continue;
                    }
                    AstNode value = this.parseExpression(inCalledScope);
                    this.expect(
                        Token.Type.COMMA, Token.Type.SEMICOLON, 
                        Token.Type.BRACKET_CLOSE
                    );
                    if(this.current.type == Token.Type.SEMICOLON) {
                        this.next();
                        AstNode size = this.parseExpression(inCalledScope);
                        this.expect(Token.Type.BRACKET_CLOSE);
                        Token end = this.current;
                        this.next();
                        previous = Optional.of(new AstNode(
                            AstNode.Type.REPEATING_ARRAY_LITERAL,
                            new AstNode.BiOp(value, size),
                            new Source(start.source, end.source)
                        ));
                        continue;
                    }
                    List<AstNode> values = new ArrayList<>();
                    values.add(value);
                    if(this.current.type == Token.Type.COMMA) {
                        this.next();                        
                        while(this.current.type != Token.Type.BRACKET_CLOSE) {
                            values.add(this.parseExpression(inCalledScope));
                            this.expect(
                                Token.Type.BRACKET_CLOSE, Token.Type.COMMA
                            );
                            if(this.current.type == Token.Type.COMMA) {
                                this.next();
                            }
                        }
                    }
                    Token end = this.current;
                    this.next();
                    previous = Optional.of(new AstNode(
                        AstNode.Type.ARRAY_LITERAL,
                        new AstNode.ArrayLiteral(values),
                        new Source(start.source, end.source)
                    ));
                    continue;
                }
                case KEYWORD_TRUE:
                case KEYWORD_FALSE: {
                    String value = this.current.content;
                    this.next();
                    previous = Optional.of(new AstNode(
                        AstNode.Type.BOOLEAN_LITERAL,
                        new AstNode.SimpleLiteral(value),
                        start.source
                    ));
                    continue;
                }
                case INTEGER: {
                    String value = this.current.content;
                    this.next();
                    previous = Optional.of(new AstNode(
                        AstNode.Type.INTEGER_LITERAL,
                        new AstNode.SimpleLiteral(value),
                        start.source
                    ));
                    continue;
                }
                case FRACTION: {
                    String value = this.current.content;
                    this.next();
                    previous = Optional.of(new AstNode(
                        AstNode.Type.FLOAT_LITERAL,
                        new AstNode.SimpleLiteral(value),
                        start.source
                    ));
                    continue;
                }
                case STRING: {
                    String value = this.current.content;
                    this.next();
                    previous = Optional.of(new AstNode(
                        AstNode.Type.STRING_LITERAL,
                        new AstNode.SimpleLiteral(value),
                        start.source
                    ));
                    continue;
                }
                case KEYWORD_UNIT: {
                    this.next();
                    previous = Optional.of(new AstNode(
                        AstNode.Type.UNIT_LITERAL, null, start.source
                    ));
                    continue;
                }
                case MINUS: {
                    this.next();
                    AstNode negated = this.parseExpression(
                        Token.Type.PREFIX_MINUS_PRECEDENCE, inCalledScope
                    );
                    previous = Optional.of(new AstNode(
                        AstNode.Type.NEGATE, 
                        new AstNode.MonoOp(negated),
                        new Source(start.source, negated.source)
                    ));
                    continue;
                }
                case EXCLAMATION_MARK: {
                    this.next();
                    AstNode negated = this.parseExpression(
                        Token.Type.PREFIX_EXCLAMATION_MARK_PRECEDENCE,
                        inCalledScope
                    );
                    previous = Optional.of(new AstNode(
                        AstNode.Type.NOT, 
                        new AstNode.MonoOp(negated),
                        new Source(start.source, negated.source)
                    ));
                    continue;
                }
                case IDENTIFIER: {
                    List<String> segments = new ArrayList<>();
                    segments.add(this.current.content);
                    Token end = this.current;
                    this.next();
                    while(this.current.type == Token.Type.DOUBLE_COLON) {
                        this.next();
                        this.expect(Token.Type.IDENTIFIER);
                        segments.add(this.current.content);
                        end = this.current;
                        this.next();
                    }
                    previous = Optional.of(new AstNode(
                        AstNode.Type.MODULE_ACCESS, 
                        new AstNode.ModuleAccess(
                            new Namespace(segments), Optional.empty()
                        ),
                        new Source(start.source, end.source)
                    ));
                    continue;
                }
                case HASHTAG: {
                    this.next();
                    this.expect(Token.Type.IDENTIFIER);
                    String variantName = this.current.content;
                    this.next();
                    AstNode variantValue = this.parseExpression(
                        Token.Type.PREFIX_HASHTAG_PRECEDENCE, inCalledScope
                    );
                    previous = Optional.of(new AstNode(
                        AstNode.Type.VARIANT_LITERAL, 
                        new AstNode.VariantLiteral(variantName, variantValue),
                        new Source(start.source, variantValue.source)
                    ));
                    continue;
                }
                case KEYWORD_STATIC: {
                    this.next();
                    AstNode expr = this.parseExpression(false);
                    previous = Optional.of(new AstNode(
                        AstNode.Type.STATIC, 
                        new AstNode.MonoOp(expr),
                        new Source(start.source, expr.source)
                    ));
                    continue;
                }
                case PAREN_OPEN: {
                    this.next();
                    AstNode expr = this.parseExpression(inCalledScope);
                    this.expect(Token.Type.PAREN_CLOSE);
                    Token end = this.current;
                    this.next();
                    previous = Optional.of(new AstNode(
                        expr.type, 
                        expr.getValue(),
                        new Source(start.source, end.source)
                    ));
                    continue;
                }
                default: {
                    this.throwUnexpected("the start of a full expression");
                }
            }
        }
    }

}
