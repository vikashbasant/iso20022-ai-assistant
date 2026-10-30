package com.vikashbasant.paymentsai.mt103;

import com.vikashbasant.paymentsai.Samples;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mt103ParserTest {

    private final Mt103Parser parser = new Mt103Parser();

    @Test
    void parsesFullFinMessageIncludingMultiLineFields() {
        Mt103Message msg = parser.parse(Samples.VALID_MT103);

        assertThat(msg.get("20")).contains("INV2026-10-001");
        assertThat(msg.get("32A")).contains("261030USD1250,50");
        assertThat(msg.get("50K")).contains("/12345678901\nACME EXPORTS PVT LTD\nSECTOR 44 GURUGRAM\nHARYANA INDIA");
        assertThat(msg.get("71A")).contains("SHA");
        assertThat(msg.fields()).doesNotContainKey("-}");
    }

    @Test
    void parsesBareBlock4() {
        Mt103Message msg = parser.parse(":20:REF1\n:23B:CRED\n");
        assertThat(msg.fields()).containsKeys("20", "23B");
    }

    @Test
    void findsTagOptionByNumber() {
        Mt103Message msg = parser.parse(Samples.VALID_MT103);
        assertThat(msg.firstOption("50")).hasValueSatisfying(e -> assertThat(e.getKey()).isEqualTo("50K"));
        assertThat(msg.firstOption("59")).hasValueSatisfying(e -> assertThat(e.getKey()).isEqualTo("59"));
    }

    @Test
    void rejectsTextWithoutTags() {
        assertThatThrownBy(() -> parser.parse("hello world"))
                .isInstanceOf(Mt103ParseException.class);
        assertThatThrownBy(() -> parser.parse("  "))
                .isInstanceOf(Mt103ParseException.class);
    }
}
