package com.vikashbasant.paymentsai.validation;

import com.vikashbasant.paymentsai.mt103.Mt103Message;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic field-format and cross-field checks for an MT103.
 * Covers the common rules that cause rejects; it is not a full SWIFT network validator.
 */
@Component
public class Mt103Validator {

    static final Pattern BIC = Pattern.compile("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,14},\\d{0,14}$");
    private static final Set<String> BANK_OPERATION_CODES = Set.of("CRED", "CRTS", "SPAY", "SPRI", "SSTD");
    private static final Set<String> CHARGE_CODES = Set.of("BEN", "OUR", "SHA");
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    public List<ValidationIssue> validate(Mt103Message msg) {
        List<ValidationIssue> issues = new ArrayList<>();

        requireTag(msg, "20", issues);
        requireTag(msg, "23B", issues);
        requireTag(msg, "32A", issues);
        requireTag(msg, "71A", issues);
        if (msg.firstOption("50").isEmpty()) {
            issues.add(ValidationIssue.error("50a", "Ordering customer (50A, 50F or 50K) is mandatory"));
        }
        if (msg.firstOption("59").isEmpty()) {
            issues.add(ValidationIssue.error("59a", "Beneficiary customer (59, 59A or 59F) is mandatory"));
        }

        msg.get("20").ifPresent(v -> checkReference(v, issues));
        msg.get("23B").ifPresent(v -> {
            if (!BANK_OPERATION_CODES.contains(v.trim())) {
                issues.add(ValidationIssue.error("23B", "Bank operation code '" + v.trim()
                        + "' is not one of " + new java.util.TreeSet<>(BANK_OPERATION_CODES)));
            }
        });
        msg.get("32A").ifPresent(v -> checkValueDateCurrencyAmount(v, issues));
        msg.get("71A").ifPresent(v -> checkCharges(msg, v.trim(), issues));

        for (String tag : List.of("52A", "53A", "54A", "56A", "57A")) {
            msg.get(tag).ifPresent(v -> checkBic(tag, v, issues));
        }
        msg.get("50K").ifPresent(v -> checkNameAndAddress("50K", v, issues));
        msg.get("59").ifPresent(v -> checkNameAndAddress("59", v, issues));
        msg.get("70").ifPresent(v -> checkLines("70", v, 4, 35, issues));
        return issues;
    }

    private void requireTag(Mt103Message msg, String tag, List<ValidationIssue> issues) {
        if (!msg.has(tag)) {
            issues.add(ValidationIssue.error(tag, "Field :" + tag + ": is mandatory"));
        }
    }

    private void checkReference(String ref, List<ValidationIssue> issues) {
        String v = ref.trim();
        if (v.isEmpty() || v.length() > 16) {
            issues.add(ValidationIssue.error("20", "Sender's reference must be 1-16 characters"));
        }
        if (v.startsWith("/") || v.endsWith("/") || v.contains("//")) {
            issues.add(ValidationIssue.error("20", "Sender's reference must not start or end with '/' or contain '//'"));
        }
    }

    private void checkValueDateCurrencyAmount(String raw, List<ValidationIssue> issues) {
        String v = raw.trim();
        if (v.length() < 11) {
            issues.add(ValidationIssue.error("32A", "Expected YYMMDD + currency + amount, e.g. 261030USD1250,50"));
            return;
        }
        String date = v.substring(0, 6);
        String ccy = v.substring(6, 9);
        String amount = v.substring(9);
        try {
            LocalDate.parse(date, YYMMDD);
        } catch (DateTimeParseException e) {
            issues.add(ValidationIssue.error("32A", "Value date '" + date + "' is not a valid YYMMDD date"));
        }
        if (!CURRENCY.matcher(ccy).matches()) {
            issues.add(ValidationIssue.error("32A", "Currency '" + ccy + "' must be a 3-letter ISO 4217 code"));
        }
        if (!AMOUNT.matcher(amount).matches() || amount.length() > 15) {
            issues.add(ValidationIssue.error("32A", "Amount '" + amount
                    + "' must use a comma as decimal separator (e.g. 1250,50) and be at most 15 characters"));
        } else if (amount.replace(",", "").chars().allMatch(c -> c == '0')) {
            issues.add(ValidationIssue.error("32A", "Amount must be greater than zero"));
        }
    }

    private void checkCharges(Mt103Message msg, String code, List<ValidationIssue> issues) {
        if (!CHARGE_CODES.contains(code)) {
            issues.add(ValidationIssue.error("71A", "Details of charges '" + code + "' must be BEN, OUR or SHA"));
            return;
        }
        boolean has71F = msg.fields().keySet().stream().anyMatch(t -> t.equals("71F"));
        boolean has71G = msg.has("71G");
        if (code.equals("BEN") && !has71F) {
            issues.add(ValidationIssue.error("71F", "When 71A is BEN, at least one 71F (sender's charges) is required"));
        }
        if ((code.equals("BEN") || code.equals("SHA")) && has71G) {
            issues.add(ValidationIssue.error("71G", "71G (receiver's charges) is only allowed when 71A is OUR"));
        }
    }

    private void checkBic(String tag, String value, List<ValidationIssue> issues) {
        String[] lines = value.split("\\n");
        String bic = lines[lines.length - 1].trim();
        if (!BIC.matcher(bic).matches()) {
            issues.add(ValidationIssue.error(tag, "'" + bic + "' is not a valid BIC (8 or 11 characters)"));
        }
    }

    private void checkNameAndAddress(String tag, String value, List<ValidationIssue> issues) {
        String[] lines = value.split("\\n");
        int start = lines[0].startsWith("/") ? 1 : 0;
        if (start == 1 && lines[0].length() > 35) {
            issues.add(ValidationIssue.error(tag, "Account line must be at most 34 characters after '/'"));
        }
        int nameLines = lines.length - start;
        if (nameLines == 0) {
            issues.add(ValidationIssue.error(tag, "Name and address are missing"));
        } else if (nameLines > 4) {
            issues.add(ValidationIssue.error(tag, "Name and address allow at most 4 lines"));
        }
        for (int i = start; i < lines.length; i++) {
            if (lines[i].length() > 35) {
                issues.add(ValidationIssue.error(tag, "Line " + (i + 1) + " exceeds 35 characters"));
            }
        }
        if (start == 0) {
            issues.add(ValidationIssue.warning(tag, "No account number line ('/ACCOUNT') supplied"));
        }
    }

    private void checkLines(String tag, String value, int maxLines, int maxLen, List<ValidationIssue> issues) {
        String[] lines = value.split("\\n");
        if (lines.length > maxLines) {
            issues.add(ValidationIssue.error(tag, "At most " + maxLines + " lines allowed"));
        }
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() > maxLen) {
                issues.add(ValidationIssue.error(tag, "Line " + (i + 1) + " exceeds " + maxLen + " characters"));
            }
        }
    }
}
