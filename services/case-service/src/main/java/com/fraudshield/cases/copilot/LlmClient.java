package com.fraudshield.cases.copilot;

import com.fraudshield.cases.config.AppProperties;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Provider-agnostic chat model. The copilot never depends on a specific vendor: switch between
 * OpenAI-compatible APIs (OpenAI, Groq, OpenRouter...), Anthropic Claude or a local Ollama model
 * with one environment variable, or run with none and get the deterministic fallback.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ChatModel model;
    private final String description;

    public LlmClient(AppProperties props) {
        AppProperties.Ai ai = props.ai();
        String provider = ai.provider() == null ? "none" : ai.provider().trim().toLowerCase();
        ChatModel built = null;
        String desc = "none";
        try {
            switch (provider) {
                case "openai" -> {
                    String model = orDefault(ai.model(), "gpt-4o-mini");
                    built = OpenAiChatModel.builder()
                            .baseUrl(orDefault(ai.baseUrl(), "https://api.openai.com/v1"))
                            .apiKey(ai.apiKey())
                            .modelName(model)
                            .temperature(ai.temperature())
                            .timeout(ai.timeout())
                            .maxTokens(1200)
                            .maxRetries(1)
                            .build();
                    desc = "openai-compatible:" + model;
                }
                case "anthropic" -> {
                    String model = orDefault(ai.model(), "claude-opus-5-5");
                    // Current Claude models reject sampling parameters (temperature/top_p), and
                    // adaptive thinking shares the max_tokens budget, so leave generous headroom.
                    built = AnthropicChatModel.builder()
                            .apiKey(ai.apiKey())
                            .modelName(model)
                            .timeout(ai.timeout())
                            .maxTokens(16000)
                            .maxRetries(1)
                            .build();
                    desc = "anthropic:" + model;
                }
                case "ollama" -> {
                    String model = orDefault(ai.model(), "qwen2.5:1.5b");
                    built = OllamaChatModel.builder()
                            .baseUrl(orDefault(ai.baseUrl(), "http://localhost:11434"))
                            .modelName(model)
                            .temperature(ai.temperature())
                            .timeout(ai.timeout())
                            .maxRetries(1)
                            .build();
                    desc = "ollama:" + model;
                }
                default -> desc = "none";
            }
        } catch (RuntimeException e) {
            log.error("Could not configure AI provider '{}', falling back to rule-based copilot", provider, e);
            built = null;
            desc = "none";
        }
        this.model = built;
        this.description = desc;
        log.info("Copilot chat model: {}", description);
    }

    public boolean enabled() {
        return model != null;
    }

    public String description() {
        return description;
    }

    /** Empty when no model is configured or the call fails; callers then use the fallback. */
    public Optional<String> chat(String system, String user) {
        if (model == null) return Optional.empty();
        try {
            String text = model.chat(SystemMessage.from(system), UserMessage.from(user)).aiMessage().text();
            return Optional.ofNullable(text).filter(t -> !t.isBlank());
        } catch (RuntimeException e) {
            log.warn("LLM call failed ({}), using fallback", e.getMessage());
            return Optional.empty();
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
