package com.vikashbasant.paymentsai.validation;

import com.vikashbasant.paymentsai.Samples;
import com.vikashbasant.paymentsai.mt103.Mt103Parser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Mt103ValidatorTest {

    private final Mt103Parser parser = new Mt103Parser();
    private final Mt103Validator validator = new Mt103Validator();

    @Test
    void validMessageHasNoErrors() {
        List<ValidationIssue> issues = validator.validate(parser.parse(Samples.VALID_MT103));
        assertThat(issues).noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
    }

    @Test
    void invalidMessageReportsEachProblem() {
        List<ValidationIssue> issues = validator.validate(parser.parse(Samples.INVALID_MT103));

        assertThat(errorTags(issues)).contains("20", "23B", "32A", "71F", "71G", "57A");
        assertThat(messages(issues)).anyMatch(m -> m.contains("not a valid YYMMDD date"));
        assertThat(messages(issues)).anyMatch(m -> m.contains("comma as decimal separator"));
        assertThat(messages(issues)).anyMatch(m -> m.contains("only allowed when 71A is OUR"));
    }

    @Test
    void missingMandatoryFieldsAreReported() {
        List<ValidationIssue> issues = validator.validate(parser.parse(":20:ONLYREF\n"));
        assertThat(errorTags(issues)).contains("23B", "32A", "71A", "50a", "59a");
    }

    @Test
    void bicPatternAcceptsEightAndElevenCharacters() {
        assertThat(Mt103Validator.BIC.matcher("HDFCINBB").matches()).isTrue();
        assertThat(Mt103Validator.BIC.matcher("HDFCINBBXXX").matches()).isTrue();
        assertThat(Mt103Validator.BIC.matcher("HDFC1NBB").matches()).isFalse();
        assertThat(Mt103Validator.BIC.matcher("CITI33").matches()).isFalse();
    }

    private static List<String> errorTags(List<ValidationIssue> issues) {
        return issues.stream().filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .map(ValidationIssue::tag).toList();
    }

    private static List<String> messages(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::message).toList();
    }
}
