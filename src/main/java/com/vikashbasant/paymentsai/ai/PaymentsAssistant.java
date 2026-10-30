package com.vikashbasant.paymentsai.ai;

import com.vikashbasant.paymentsai.validation.ValidationIssue;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM features. Answers are grounded in the local knowledge notes (RAG) and,
 * for message explanations, in the deterministic validator's findings, so the model
 * explains rule results instead of inventing its own.
 */
@Service
public class PaymentsAssistant {

    private static final String SYSTEM_PROMPT = """
            You are a payments operations assistant for SWIFT MT and ISO 20022 (MX) messages.
            Answer from the provided context. If the context does not cover the question, say so
            plainly instead of guessing. Keep answers short and practical for a payments engineer.
            Never invent field rules, codes or deadlines.
            """;

    private final ChatClient chatClient;
    private final KnowledgeBase knowledgeBase;

    public PaymentsAssistant(ChatClient.Builder builder, VectorStore vectorStore, KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(4).similarityThreshold(0.3).build())
                        .build())
                .build();
    }

    @Cacheable(cacheNames = "ai-answers", key = "#question.trim().toLowerCase()")
    public String ask(String question) {
        knowledgeBase.ensureLoaded();
        return chatClient.prompt().user(question).call().content();
    }

    public String explain(String rawMessage, List<ValidationIssue> issues) {
        knowledgeBase.ensureLoaded();
        String findings = issues.isEmpty()
                ? "The rule-based validator found no issues."
                : issues.stream()
                    .map(i -> "- [" + i.severity() + "] :" + i.tag() + ": " + i.message())
                    .collect(Collectors.joining("\n"));
        String prompt = """
                Explain this MT103 to an operations analyst: who pays whom, amount, value date and charges.
                Then explain each validator finding below and how to fix it. Do not add findings of your own.

                MT103:
                %s

                Validator findings:
                %s
                """.formatted(rawMessage, findings);
        return chatClient.prompt().user(prompt).call().content();
    }
}
