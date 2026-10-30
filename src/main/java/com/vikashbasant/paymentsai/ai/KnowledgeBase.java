package com.vikashbasant.paymentsai.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the markdown notes under classpath:knowledge/ into the vector store.
 * Loading is lazy so the app starts (and the non-AI endpoints work) without an API key.
 */
@Component
public class KnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);

    private final VectorStore vectorStore;
    private volatile boolean loaded;

    public KnowledgeBase(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (loaded) {
                return;
            }
            List<Document> docs = readDocuments();
            List<Document> chunks = new TokenTextSplitter(400, 100, 20, 1000, true).apply(docs);
            vectorStore.add(chunks);
            log.info("Indexed {} knowledge documents as {} chunks", docs.size(), chunks.size());
            loaded = true;
        }
    }

    private List<Document> readDocuments() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:knowledge/*.md");
            List<Document> docs = new ArrayList<>();
            for (Resource r : resources) {
                String text = r.getContentAsString(StandardCharsets.UTF_8);
                docs.add(new Document(text, Map.of("source", String.valueOf(r.getFilename()))));
            }
            return docs;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read knowledge documents", e);
        }
    }
}
