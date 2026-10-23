package com.customersupport.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Thin wrapper around the chat model shared by all agents.
 *
 * <p>When no API key is configured, {@link #complete} returns empty and each agent uses
 * its rule-based fallback, so the graph can be run and tested without network access.
 * An LLM failure is treated the same way rather than failing the customer's request.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ChatModel model;

    @Autowired
    public LlmClient(LlmProperties properties) {
        if (properties.enabled()) {
            this.model = AnthropicChatModel.builder()
                    .apiKey(properties.apiKey())
                    .modelName(properties.model())
                    .maxTokens(properties.maxTokens() == null ? 1024 : properties.maxTokens())
                    .build();
            log.info("LLM enabled with model {}", properties.model());
        } else {
            this.model = null;
            log.info("No ANTHROPIC_API_KEY set: agents will use rule-based fallbacks");
        }
    }

    /** For tests: plug in any LangChain4j chat model, or null to force the fallbacks. */
    public LlmClient(ChatModel model) {
        this.model = model;
    }

    public boolean enabled() {
        return model != null;
    }

    public Optional<String> complete(String systemPrompt, String userPrompt) {
        if (model == null) {
            return Optional.empty();
        }
        try {
            String text = model.chat(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                    .aiMessage()
                    .text();
            return Optional.ofNullable(text).map(String::trim).filter(t -> !t.isEmpty());
        } catch (RuntimeException e) {
            log.warn("LLM call failed, falling back to rules: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
