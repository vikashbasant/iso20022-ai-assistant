package com.vikashbasant.paymentsai.mt103;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the text block of an MT103 into tags. Accepts either a full FIN message
 * ({1:...}{2:...}{4:\n...\n-}) or just the block 4 lines starting with ":20:".
 */
@Component
public class Mt103Parser {

    private static final Pattern TAG_LINE = Pattern.compile("^:(\\d{2}[A-Z]?):(.*)$");

    public Mt103Message parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new Mt103ParseException("Message is empty");
        }
        String block4 = extractBlock4(raw);
        Map<String, String> fields = new LinkedHashMap<>();
        String currentTag = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : block4.split("\\r?\\n")) {
            if (line.isBlank() || line.trim().equals("-}") || line.trim().equals("-")) {
                continue;
            }
            Matcher m = TAG_LINE.matcher(line);
            if (m.matches()) {
                if (currentTag != null) {
                    fields.put(currentTag, currentValue.toString().strip());
                }
                currentTag = m.group(1);
                currentValue = new StringBuilder(m.group(2));
            } else if (currentTag != null) {
                currentValue.append('\n').append(line);
            } else {
                throw new Mt103ParseException("Text found before the first tag: '" + line.trim() + "'");
            }
        }
        if (currentTag != null) {
            fields.put(currentTag, currentValue.toString().strip());
        }
        if (fields.isEmpty()) {
            throw new Mt103ParseException("No MT tags found (expected lines like ':20:REF123')");
        }
        return new Mt103Message(fields);
    }

    private String extractBlock4(String raw) {
        int start = raw.indexOf("{4:");
        if (start < 0) {
            return raw.strip();
        }
        int end = raw.indexOf("-}", start);
        String body = end > start ? raw.substring(start + 3, end) : raw.substring(start + 3);
        return body.strip();
    }
}
