package com.vikashbasant.paymentsai.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikashbasant.paymentsai.mt103.Mt103ParseException;
import com.vikashbasant.paymentsai.mt103.Mt103Parser;
import com.vikashbasant.paymentsai.validation.Mt103Validator;
import com.vikashbasant.paymentsai.validation.ValidationIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Optional streaming mode: consumes raw MT103 messages from one topic and publishes
 * validation results (JSON) to another. Enabled with app.kafka.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class Mt103ValidationListener {

    private static final Logger log = LoggerFactory.getLogger(Mt103ValidationListener.class);

    private final Mt103Parser parser;
    private final Mt103Validator validator;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String outputTopic;

    public Mt103ValidationListener(Mt103Parser parser, Mt103Validator validator,
                                   KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                   @org.springframework.beans.factory.annotation.Value("${app.kafka.output-topic}") String outputTopic) {
        this.parser = parser;
        this.validator = validator;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.outputTopic = outputTopic;
    }

    @KafkaListener(topics = "${app.kafka.input-topic}", groupId = "mt103-validator")
    public void onMessage(String raw) throws JsonProcessingException {
        Map<String, Object> result;
        String key;
        try {
            var msg = parser.parse(raw);
            List<ValidationIssue> issues = validator.validate(msg);
            key = msg.get("20").orElse("UNKNOWN").trim();
            boolean valid = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
            result = Map.of("reference", key, "valid", valid, "issues", issues);
        } catch (Mt103ParseException e) {
            key = "UNPARSEABLE";
            result = Map.of("reference", key, "valid", false, "error", e.getMessage());
        }
        kafkaTemplate.send(outputTopic, key, objectMapper.writeValueAsString(result));
        log.info("Validated MT103 {} -> {}", key, result.get("valid"));
    }
}
