package com.vikashbasant.paymentsai.mapping;

import com.vikashbasant.paymentsai.mt103.Mt103Message;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rule-based translation of the core MT103 fields into an ISO 20022 pacs.008.001.08 message.
 * This is a learning/demo mapping of the main fields, not a certified CBPR+ translator.
 */
@Component
public class Mt103ToPacs008Mapper {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Map<String, String> CHARGE_BEARER = Map.of("OUR", "DEBT", "BEN", "CRED", "SHA", "SHAR");

    private final Clock clock;

    public Mt103ToPacs008Mapper() {
        this(Clock.systemUTC());
    }

    Mt103ToPacs008Mapper(Clock clock) {
        this.clock = clock;
    }

    public MappingResult map(Mt103Message mt) {
        List<String> notes = new ArrayList<>();
        String ref = mt.get("20").orElse("NOTPROVIDED").trim();
        String f32a = mt.get("32A").orElseThrow(() -> new IllegalArgumentException("32A is required for mapping")).trim();
        LocalDate valueDate = LocalDate.parse(f32a.substring(0, 6), YYMMDD);
        String currency = f32a.substring(6, 9);
        String amount = f32a.substring(9).replace(',', '.');
        if (amount.endsWith(".")) {
            amount = amount + "00";
        }
        String chargeCode = mt.get("71A").map(String::trim).orElse("SHA");
        String chargeBearer = CHARGE_BEARER.getOrDefault(chargeCode, "SHAR");

        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">\n");
        x.append("  <FIToFICstmrCdtTrf>\n");
        x.append("    <GrpHdr>\n");
        tag(x, 6, "MsgId", ref);
        tag(x, 6, "CreDtTm", OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS).toString());
        tag(x, 6, "NbOfTxs", "1");
        x.append("      <SttlmInf>\n");
        tag(x, 8, "SttlmMtd", "INDA");
        x.append("      </SttlmInf>\n");
        x.append("    </GrpHdr>\n");
        x.append("    <CdtTrfTxInf>\n");
        x.append("      <PmtId>\n");
        tag(x, 8, "InstrId", ref);
        tag(x, 8, "EndToEndId", "NOTPROVIDED");
        tag(x, 8, "UETR", UUID.randomUUID().toString());
        x.append("      </PmtId>\n");
        x.append("      <IntrBkSttlmAmt Ccy=\"").append(escape(currency)).append("\">")
                .append(escape(amount)).append("</IntrBkSttlmAmt>\n");
        tag(x, 6, "IntrBkSttlmDt", valueDate.toString());
        tag(x, 6, "ChrgBr", chargeBearer);
        notes.add("EndToEndId set to NOTPROVIDED and a new UETR generated: MT103 block 3 field 121 is not parsed by this demo.");

        mt.firstOption("50").ifPresentOrElse(
                e -> party(x, "Dbtr", "DbtrAcct", e.getKey(), e.getValue(), notes),
                () -> notes.add("No ordering customer (50a) - Dbtr omitted."));
        agent(x, "DbtrAgt", mt.get("52A").orElse(null), notes,
                "No 52A - in a live flow the debtor agent defaults to the MT sender BIC from block 1.");
        agent(x, "CdtrAgt", mt.get("57A").orElse(null), notes,
                "No 57A - in a live flow the creditor agent defaults to the MT receiver BIC from block 2.");
        mt.firstOption("59").ifPresentOrElse(
                e -> party(x, "Cdtr", "CdtrAcct", e.getKey(), e.getValue(), notes),
                () -> notes.add("No beneficiary (59a) - Cdtr omitted."));

        mt.get("70").ifPresent(rmt -> {
            x.append("      <RmtInf>\n");
            tag(x, 8, "Ustrd", rmt.replace('\n', ' ').trim());
            x.append("      </RmtInf>\n");
        });
        x.append("    </CdtTrfTxInf>\n");
        x.append("  </FIToFICstmrCdtTrf>\n");
        x.append("</Document>\n");
        return new MappingResult(x.toString(), notes);
    }

    private void party(StringBuilder x, String partyTag, String acctTag, String mtTag, String value, List<String> notes) {
        List<String> lines = new ArrayList<>(Arrays.asList(value.split("\\n")));
        String account = null;
        if (!lines.isEmpty() && lines.get(0).startsWith("/")) {
            account = lines.remove(0).substring(1).trim();
        }
        if (mtTag.endsWith("A")) {
            // Option A identifies the party by BIC
            x.append("      <").append(partyTag).append(">\n");
            x.append("        <Id><OrgId>\n");
            tag(x, 10, "AnyBIC", lines.isEmpty() ? "" : lines.get(lines.size() - 1).trim());
            x.append("        </OrgId></Id>\n");
            x.append("      </").append(partyTag).append(">\n");
        } else {
            if (mtTag.endsWith("F")) {
                notes.add(mtTag + " (structured party) mapped as free text; numbered line prefixes kept as-is.");
            }
            x.append("      <").append(partyTag).append(">\n");
            tag(x, 8, "Nm", lines.isEmpty() ? "" : lines.get(0).trim());
            if (lines.size() > 1) {
                x.append("        <PstlAdr>\n");
                for (String addr : lines.subList(1, lines.size())) {
                    tag(x, 10, "AdrLine", addr.trim());
                }
                x.append("        </PstlAdr>\n");
            }
            x.append("      </").append(partyTag).append(">\n");
        }
        if (account != null && !account.isEmpty()) {
            x.append("      <").append(acctTag).append(">\n");
            x.append("        <Id><Othr>\n");
            tag(x, 10, "Id", account);
            x.append("        </Othr></Id>\n");
            x.append("      </").append(acctTag).append(">\n");
        }
    }

    private void agent(StringBuilder x, String agentTag, String value, List<String> notes, String missingNote) {
        if (value == null) {
            notes.add(missingNote);
            return;
        }
        String[] lines = value.split("\\n");
        x.append("      <").append(agentTag).append(">\n");
        x.append("        <FinInstnId>\n");
        tag(x, 10, "BICFI", lines[lines.length - 1].trim());
        x.append("        </FinInstnId>\n");
        x.append("      </").append(agentTag).append(">\n");
    }

    private static void tag(StringBuilder x, int indent, String name, String value) {
        x.append(" ".repeat(indent)).append('<').append(name).append('>')
                .append(escape(value)).append("</").append(name).append(">\n");
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    public record MappingResult(String xml, List<String> notes) {
    }
}
