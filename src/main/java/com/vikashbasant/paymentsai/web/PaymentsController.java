package com.vikashbasant.paymentsai.web;

import com.vikashbasant.paymentsai.ai.PaymentsAssistant;
import com.vikashbasant.paymentsai.mapping.Mt103ToPacs008Mapper;
import com.vikashbasant.paymentsai.mt103.Mt103Message;
import com.vikashbasant.paymentsai.mt103.Mt103Parser;
import com.vikashbasant.paymentsai.validation.Mt103Validator;
import com.vikashbasant.paymentsai.validation.ValidationIssue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PaymentsController {

    private final Mt103Parser parser;
    private final Mt103Validator validator;
    private final Mt103ToPacs008Mapper mapper;
    private final PaymentsAssistant assistant;

    public PaymentsController(Mt103Parser parser, Mt103Validator validator,
                              Mt103ToPacs008Mapper mapper, PaymentsAssistant assistant) {
        this.parser = parser;
        this.validator = validator;
        this.mapper = mapper;
        this.assistant = assistant;
    }

    /** Rule-based checks only - no LLM call. */
    @PostMapping(value = "/mt103/validate", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ValidationResponse validate(@RequestBody String raw) {
        Mt103Message msg = parser.parse(raw);
        List<ValidationIssue> issues = validator.validate(msg);
        boolean valid = issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResponse(valid, msg.fields(), issues);
    }

    /** MT103 -> pacs.008.001.08 XML, plus notes on anything that could not be mapped. */
    @PostMapping(value = "/mt103/convert", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Mt103ToPacs008Mapper.MappingResult convert(@RequestBody String raw) {
        return mapper.map(parser.parse(raw));
    }

    /** Plain-English explanation of the message and of the validator's findings (LLM). */
    @PostMapping(value = "/mt103/explain", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, Object> explain(@RequestBody String raw) {
        Mt103Message msg = parser.parse(raw);
        List<ValidationIssue> issues = validator.validate(msg);
        return Map.of("issues", issues, "explanation", assistant.explain(raw, issues));
    }

    /** RAG question answering over the ISO 20022 / MT103 notes (LLM). */
    @PostMapping("/ask")
    public Map<String, String> ask(@Valid @RequestBody AskRequest request) {
        return Map.of("answer", assistant.ask(request.question()));
    }

    public record AskRequest(@NotBlank @Size(max = 500) String question) {
    }

    public record ValidationResponse(boolean valid, Map<String, String> fields, List<ValidationIssue> issues) {
    }
}
