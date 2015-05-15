package com.shapesecurity.csp;

import com.shapesecurity.csp.Parser.ParseException;
import com.shapesecurity.csp.Tokeniser.TokeniserException;
import com.shapesecurity.csp.directives.*;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import static org.junit.Assert.*;

public class ParserTest {
    private static int countIterable(Iterable<Directive> a) {
        int count = 0;
        for (Object b : a) {
            ++count;
        }
        return count;
    }

    private String createAndShow(@Nonnull String value) throws ParseException, TokeniserException {
        return Parser.parse(value).getDirectiveByType(BaseUriDirective.class).show();
    }

    @Test
    public void testEmptyParser() throws ParseException, TokeniserException {
        Policy emptyPolicy = Parser.parse("");
        assertNotNull("empty policy should not be null", emptyPolicy);
    }

    @Test
    public void testDuplicates() throws ParseException, TokeniserException {
        Policy p;
        p = Parser.parse("img-src a ;;; img-src b");
        assertNotNull("policy should not be null", p);
        assertEquals("", 1, countIterable(p.getDirectives()));
        Directive firstDirective = p.getDirectives().iterator().next();
        ImgSrcDirective imgSrcDirective = p.getDirectiveByType(ImgSrcDirective.class);
        assertEquals("", imgSrcDirective, firstDirective);
        assertEquals("", "img-src", imgSrcDirective.name);
        assertEquals("", "img-src a", imgSrcDirective.show());
    }

    @Test
    public void testParser() throws ParseException, TokeniserException {
        Policy p;
        p = Parser.parse("font-src *");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("form-action *");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("frame-ancestors 'none'");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("frame-src *");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("img-src *");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("media-src *");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("object-src *");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("plugin-types */*");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("report-uri https://example.com/report");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("sandbox allow-scripts");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("script-src *");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

        p = Parser.parse("style-src *");
        assertNotNull("policy should not be null", p);
        assertEquals("directive count", 1, countIterable(p.getDirectives()));

    }

    private void failsToParse(String policy) {
        try {
            Parser.parse(policy);
        } catch (ParseException | TokeniserException ignored) {
            return;
        }
        fail();
    }

    @Test
    public void testSourceExpressionParsing() throws ParseException, TokeniserException {
        assertEquals("directive-name, no directive-value", "base-uri", createAndShow("base-uri"));
        assertEquals("directive-name, <tab>", "base-uri", createAndShow("base-uri\t"));
        assertEquals("directive-name, <space>", "base-uri", createAndShow("base-uri "));
        assertEquals("directive-name, 3*<space>", "base-uri", createAndShow("base-uri   "));
        assertEquals("directive-name, scheme-part", "base-uri https:", createAndShow("base-uri https:"));
        assertEquals("directive-name, 2*scheme-part", "base-uri file: javascript:", createAndShow("base-uri file: javascript: "));
        assertEquals("directive-name, host-part *", "base-uri *", createAndShow("base-uri *"));
        assertEquals("directive-name, host-part *.", "base-uri *.a", createAndShow("base-uri *.a"));

        // XXX: these two tests are actually valid according to the CSP spec, but we choose not to support paths other than path-abempty
        failsToParse("base-uri abc_");
        failsToParse("base-uri abc..");

        assertEquals("directive-name, port-part", "base-uri *:12", createAndShow("base-uri *:12"));
        failsToParse("base-uri *:ee");
        assertEquals("directive-name, path-part", "base-uri */abc", createAndShow("base-uri */abc"));
        failsToParse("base-uri *\n");
        assertEquals("directive-name, full host source", "base-uri https://a.com:888/ert", createAndShow("base-uri https://a.com:888/ert"));
    }

    @Test
    public void testAncestorSourceParsing() throws ParseException, TokeniserException {
        assertEquals("directive-name, no directive-value", "frame-ancestors", Parser.parse("frame-ancestors").getDirectiveByType(FrameAncestorsDirective.class).show());
        assertEquals("directive-name, directive-value", "frame-ancestors 'none'", Parser.parse("frame-ancestors 'none'").getDirectiveByType(FrameAncestorsDirective.class).show());
    }

    @Test
    public void testPluginTypesParsing() throws ParseException, TokeniserException {
        failsToParse("plugin-types");
        // XXX: technically allowed via ietf-token if an RFC introduces a type/subtype that is empty
        failsToParse("plugin-types /");
        assertEquals("directive-name, directive-value", "plugin-types a/b", Parser.parse("plugin-types a/b").getDirectiveByType(PluginTypesDirective.class).show());
        assertEquals("directive-name, directive-value", "plugin-types a/b c/d", Parser.parse("plugin-types a/b c/d").getDirectiveByType(PluginTypesDirective.class).show());
        assertEquals("directive-name, directive-value", "plugin-types x-a/x-b", Parser.parse("plugin-types x-a/x-b").getDirectiveByType(PluginTypesDirective.class).show());
        assertEquals("directive-name, directive-value", "plugin-types X-A/X-B", Parser.parse("plugin-types X-A/X-B").getDirectiveByType(PluginTypesDirective.class).show());
    }

    @Test
    public void testReportUriMerge() throws ParseException, TokeniserException {
        Policy p;
        p = Parser.parse("report-uri a");
        Policy q;
        q = Parser.parse("report-uri b");
        Directive d1 = p.getDirectiveByType(ReportUriDirective.class);
        Directive d2 = q.getDirectiveByType(ReportUriDirective.class);
        d1.merge(d2);
        d1.show();
        assertEquals("directive-name, directive-value", "report-uri a b", d1.show());
    }

    @Test
    public void testSandboxParsing() throws ParseException, TokeniserException {
        failsToParse("sandbox a!*\n");
        //failsToParse("sandbox a!*\^");
        assertEquals("directive-name, directive-value", "sandbox abc", Parser.parse("sandbox abc").getDirectiveByType(SandboxDirective.class).show());

    }

    @Test
    public void testHash() throws ParseException, TokeniserException {
        failsToParse("sandbox a!*\n");
        //failsToParse("sandbox a!*\^");
        assertEquals("directive-name, directive-value", "sandbox abc", Parser.parse("sandbox abc").getDirectiveByType(SandboxDirective.class).show());

    }

//    @Test
//    public void testRealData() throws ParseException, TokeniserException, FileNotFoundException {
//        Scanner sc = new Scanner(new File("data/csp.txt"));
//        while (sc.hasNextLine()) {
//            Policy p;
//            String[] line = sc.nextLine().split(":", 2);
//            p = Parser.parse(line[1]);
//            assertNotNull(String.format("policy should not be null: %s", line[0]), p);
//        }
//    }
}
