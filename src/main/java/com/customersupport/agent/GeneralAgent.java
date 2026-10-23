package com.customersupport.agent;

import com.customersupport.graph.SupportState;
import com.customersupport.llm.LlmClient;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Handles everything that is neither billing nor technical: FAQs, account and company questions. */
@Component
public class GeneralAgent implements NodeAction<SupportState> {

    static final String NAME = "general";

    private static final Map<List<String>, String> FAQ = new LinkedHashMap<>();

    static {
        FAQ.put(List.of("hours", "open", "available", "when can"),
                "Our support team is available Monday to Friday, 8am-8pm, and Saturday 9am-5pm (UTC). "
                        + "This chat is available 24/7.");
        FAQ.put(List.of("contact", "phone", "email", "call"),
                "You can reach us at support@example.com or +1 800 555 0100, or keep chatting here.");
        FAQ.put(List.of("cancel", "close my account", "delete my account"),
                "You can cancel any time under Settings > Account > Cancel subscription. Your data stays "
                        + "available for 30 days in case you change your mind.");
        FAQ.put(List.of("feature", "suggest", "feedback", "idea"),
                "Thanks for the feedback! I've passed it to our product team. You can also vote on ideas at "
                        + "feedback.example.com.");
        FAQ.put(List.of("privacy", "gdpr", "data"),
                "We never sell your data. You can download or delete it under Settings > Privacy. Our full policy "
                        + "is at example.com/privacy.");
    }

    private static final String SYSTEM_PROMPT = """
            You are a friendly general customer support agent for a SaaS company. Answer briefly and helpfully. \
            Support hours: Mon-Fri 8am-8pm, Sat 9am-5pm UTC. Contact: support@example.com. """;

    private final LlmClient llm;

    public GeneralAgent(LlmClient llm) {
        this.llm = llm;
    }

    @Override
    public Map<String, Object> apply(SupportState state) {
        return llm.complete(SYSTEM_PROMPT + AgentReply.ESCALATION_INSTRUCTIONS, state.message())
                .map(answer -> AgentReply.fromLlm(NAME, answer))
                .orElseGet(() -> AgentReply.resolved(NAME, faqAnswer(state.message())));
    }

    private static String faqAnswer(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return FAQ.entrySet().stream()
                .filter(e -> e.getKey().stream().anyMatch(lower::contains))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("Thanks for reaching out! I can help with billing, technical problems, or general questions "
                        + "about your account. Could you tell me a bit more about what you need?");
    }
}
