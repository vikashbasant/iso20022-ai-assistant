package com.vikashbasant.paymentsai.validation;

public record ValidationIssue(String tag, Severity severity, String message) {

    public enum Severity { ERROR, WARNING }

    public static ValidationIssue error(String tag, String message) {
        return new ValidationIssue(tag, Severity.ERROR, message);
    }

    public static ValidationIssue warning(String tag, String message) {
        return new ValidationIssue(tag, Severity.WARNING, message);
    }
}
