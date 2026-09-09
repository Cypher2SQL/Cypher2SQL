package com.iisaka.cypher2sql;

import com.iisaka.cypher2sql.query.cypher.Syntax;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntaxTest {
    @AfterEach
    void clearOverrides() {
        System.clearProperty("cypher.antlr.lexer");
        System.clearProperty("cypher.antlr.parser");
        System.clearProperty("cypher.antlr.entryRules");
    }

    @Test
    void malformedCypherRaisesSyntaxError() {
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Syntax.cypher25().parseTree("MATCH (p:Person RETURN p"));
        assertTrue(ex.getCause().getCause().getMessage().startsWith("Cypher syntax error"));
    }

    @Test
    void unknownLexerClassRaisesIllegalState() {
        System.setProperty("cypher.antlr.lexer", "com.iisaka.cypher2sql.NoSuchLexer");

        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Syntax.cypher25().parseTree("MATCH (p:Person) RETURN p"));
        assertTrue(ex.getMessage().startsWith("Unable to load Cypher lexer class:"));
    }

    @Test
    void unknownParserClassRaisesIllegalState() {
        System.setProperty("cypher.antlr.parser", "com.iisaka.cypher2sql.NoSuchParser");

        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Syntax.cypher25().parseTree("MATCH (p:Person) RETURN p"));
        assertTrue(ex.getMessage().startsWith("Unable to load Cypher parser class:"));
    }

    @Test
    void noSupportedEntryRuleRaisesIllegalState() {
        System.setProperty("cypher.antlr.entryRules", "notARealEntryRule");

        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Syntax.cypher25().parseTree("MATCH (p:Person) RETURN p"));
        assertTrue(ex.getMessage().startsWith("No supported Cypher entry rule found on parser:"));
    }
}
