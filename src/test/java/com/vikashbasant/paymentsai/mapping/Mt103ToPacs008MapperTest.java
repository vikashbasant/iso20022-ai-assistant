package com.vikashbasant.paymentsai.mapping;

import com.vikashbasant.paymentsai.Samples;
import com.vikashbasant.paymentsai.mt103.Mt103Parser;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class Mt103ToPacs008MapperTest {

    private final Mt103Parser parser = new Mt103Parser();
    private final Mt103ToPacs008Mapper mapper =
            new Mt103ToPacs008Mapper(Clock.fixed(Instant.parse("2026-10-30T10:15:30Z"), ZoneOffset.UTC));

    @Test
    void mapsCoreFields() throws Exception {
        var result = mapper.map(parser.parse(Samples.VALID_MT103));
        Document doc = parse(result.xml());

        assertThat(xpath(doc, "//*[local-name()='GrpHdr']/*[local-name()='MsgId']")).isEqualTo("INV2026-10-001");
        assertThat(xpath(doc, "//*[local-name()='CreDtTm']")).isEqualTo("2026-10-30T10:15:30Z");
        assertThat(xpath(doc, "//*[local-name()='IntrBkSttlmAmt']")).isEqualTo("1250.50");
        assertThat(xpath(doc, "//*[local-name()='IntrBkSttlmAmt']/@Ccy")).isEqualTo("USD");
        assertThat(xpath(doc, "//*[local-name()='IntrBkSttlmDt']")).isEqualTo("2026-10-30");
        assertThat(xpath(doc, "//*[local-name()='ChrgBr']")).isEqualTo("SHAR");
        assertThat(xpath(doc, "//*[local-name()='Dbtr']/*[local-name()='Nm']")).isEqualTo("ACME EXPORTS PVT LTD");
        assertThat(xpath(doc, "//*[local-name()='DbtrAcct']//*[local-name()='Id']/*[local-name()='Othr']/*[local-name()='Id']"))
                .isEqualTo("12345678901");
        assertThat(xpath(doc, "//*[local-name()='DbtrAgt']//*[local-name()='BICFI']")).isEqualTo("HDFCINBBXXX");
        assertThat(xpath(doc, "//*[local-name()='CdtrAgt']//*[local-name()='BICFI']")).isEqualTo("CITIUS33XXX");
        assertThat(xpath(doc, "//*[local-name()='Cdtr']/*[local-name()='Nm']")).isEqualTo("GLOBEX CORPORATION");
        assertThat(xpath(doc, "//*[local-name()='Ustrd']")).isEqualTo("INVOICE 4471 OCT 2026");
        assertThat(xpath(doc, "//*[local-name()='UETR']")).matches("[0-9a-f-]{36}");
    }

    @Test
    void mapsChargeCodesAndWholeAmounts() throws Exception {
        String mt = ":20:R1\n:23B:CRED\n:32A:261030EUR500,\n:50K:/1\nA\n:59:/2\nB\n:71A:OUR\n";
        var result = mapper.map(parser.parse(mt));
        Document doc = parse(result.xml());

        assertThat(xpath(doc, "//*[local-name()='ChrgBr']")).isEqualTo("DEBT");
        assertThat(xpath(doc, "//*[local-name()='IntrBkSttlmAmt']")).isEqualTo("500.00");
        assertThat(result.notes()).anyMatch(n -> n.contains("No 52A"));
    }

    @Test
    void escapesXmlSpecialCharacters() throws Exception {
        String mt = ":20:R2\n:23B:CRED\n:32A:261030USD1,\n:50K:A & B <TRADING>\n:59:/2\nC\n:71A:SHA\n";
        Document doc = parse(mapper.map(parser.parse(mt)).xml());
        assertThat(xpath(doc, "//*[local-name()='Dbtr']/*[local-name()='Nm']")).isEqualTo("A & B <TRADING>");
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String xpath(Document doc, String expr) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(expr, doc);
    }
}
