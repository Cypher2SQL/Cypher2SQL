package com.iisaka.cypher2sql.query.cypher;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.tree.ParseTree;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CypherSyntax {
    private static final List<String> DEFAULT_ENTRY_RULES = List.of(
            "statement",
            "query",
            "cypher",
            "oC_Cypher");

    private final String lexerClassName;
    private final String parserClassName;
    private final List<String> entryRules;

    private CypherSyntax(final String lexerClassName, final String parserClassName, final List<String> entryRules) {
        this.lexerClassName = lexerClassName;
        this.parserClassName = parserClassName;
        this.entryRules = entryRules;
    }

    public static CypherSyntax cypher25() {
        final String lexer = System.getProperty("cypher.antlr.lexer", "org.neo4j.cypher.internal.parser.v25.Cypher25Lexer");
        final String parser = System.getProperty("cypher.antlr.parser", "org.neo4j.cypher.internal.parser.v25.Cypher25Parser");
        final String entryRulesRaw = System.getProperty("cypher.antlr.entryRules", "");
        final List<String> entryRules = entryRulesRaw.isBlank()
                ? DEFAULT_ENTRY_RULES
                : new ArrayList<>(Arrays.asList(entryRulesRaw.split(",")));
        return new CypherSyntax(lexer, parser, entryRules);
    }

    public CypherParseResult parse(final String cypher) {
        final CharStream input = CharStreams.fromString(cypher);
        final Lexer lexer = instantiateLexer(input);
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final Parser parser = instantiateParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new CypherSyntaxErrorListener());
        final ParseTree parseTree = invokeEntryRule(parser);
        return new CypherParseResult(parseTree, parser, tokens);
    }

    public ParseTree parseTree(final String cypher) {
        return parse(cypher).parseTree();
    }

    private Lexer instantiateLexer(final CharStream input) {
        try {
            final Class<?> clazz = Class.forName(lexerClassName);
            return (Lexer) clazz.getConstructor(CharStream.class).newInstance(input);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to load Cypher lexer class: " + lexerClassName, ex);
        }
    }

    private Parser instantiateParser(final TokenStream tokens) {
        try {
            final Class<?> clazz = Class.forName(parserClassName);
            return (Parser) clazz.getConstructor(TokenStream.class).newInstance(tokens);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to load Cypher parser class: " + parserClassName, ex);
        }
    }

    private ParseTree invokeEntryRule(final Parser parser) {
        for (final String rule : entryRules) {
            try {
                final Method method = parser.getClass().getMethod(rule);
                final Object result = method.invoke(parser);
                if (result instanceof ParseTree tree) {
                    return tree;
                }
            } catch (NoSuchMethodException ignored) {
                continue;
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalStateException("Unable to invoke Cypher entry rule: " + rule, ex);
            }
        }
        throw new IllegalStateException("No supported Cypher entry rule found on parser: " + parserClassName);
    }

    private static final class CypherSyntaxErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(
                final Recognizer<?, ?> recognizer,
                final Object offendingSymbol,
                final int line,
                final int charPositionInLine,
                final String msg,
                final RecognitionException e) {
            throw new IllegalArgumentException(
                    "Cypher syntax error at line " + line + ", column " + charPositionInLine + ": " + msg,
                    e);
        }
    }
}
