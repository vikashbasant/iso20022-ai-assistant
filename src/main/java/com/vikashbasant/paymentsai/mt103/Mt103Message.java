package com.vikashbasant.paymentsai.mt103;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Block 4 (text block) of a SWIFT MT103, kept as tag -> raw value in message order.
 * Tags carry their option letter, e.g. "50K", "59", "57A".
 */
public record Mt103Message(Map<String, String> fields) {

    public Mt103Message {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Optional<String> get(String tag) {
        return Optional.ofNullable(fields.get(tag));
    }

    /** First field whose tag starts with the given number, e.g. "50" matches 50A, 50F or 50K. */
    public Optional<Map.Entry<String, String>> firstOption(String tagNumber) {
        return fields.entrySet().stream()
                .filter(e -> e.getKey().startsWith(tagNumber) && e.getKey().length() <= tagNumber.length() + 1)
                .findFirst();
    }

    public boolean has(String tag) {
        return fields.containsKey(tag);
    }
}
