package com.customersupport.agent;

import com.customersupport.graph.Category;
import com.customersupport.graph.SupportState;
import com.customersupport.llm.LlmClient;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Supervisor agent: classifies the customer's message and decides which specialist
 * handles it. Safety rules (explicit human request, legal threats, strong anger) always
 * win; otherwise the LLM classifies, with keyword scoring as the offline fallback.
 */
@Component
public class RouterAgent implements NodeAction<SupportState> {

    static final List<String> ESCALATION_TRIGGERS = List.of(
            "a human", "real person", "representative", "manager", "supervisor",
            "lawyer", "legal action", "sue ", "lawsuit", "attorney",
            "unacceptable", "furious", "outraged", "worst service", "cancel everything");

    private static final List<String> BILLING_WORDS = List.of(
            "bill", "billing", "invoice", "charge", "charged", "refund", "payment", "pay ", "paid",
            "price", "pricing", "subscription", "plan", "upgrade", "downgrade", "credit card", "receipt");

    private static final List<String> TECHNICAL_WORDS = List.of(
            "error", "bug", "crash", "broken", "not working", "doesn't work", "won't", "cannot", "can't",
            "login", "log in", "password", "install", "slow", "timeout", "api", "app", "freeze", "sync");

    private static final String SYSTEM_PROMPT = """
            You are the routing supervisor of a customer support team. Classify the customer's message \
            into exactly one category:
            BILLING - invoices, charges, refunds, payments, plans and subscriptions
            TECHNICAL - product problems, errors, login or password issues, installation, performance, API
            GENERAL - anything else: company info, opening hours, accounts, feedback, how-to questions
            ESCALATE - the customer asks for a human, threatens legal action, or is very upset
            Use the earlier conversation to understand short follow-ups such as "yes, please do that".
            Answer in the form CATEGORY | short reason. Do not add anything else.""";

    private final LlmClient llm;

    public RouterAgent(LlmClient llm) {
        this.llm = llm;
    }

    @Override
    public Map<String, Object> apply(SupportState state) {
        String message = state.message();
        String lower = message.toLowerCase(Locale.ROOT);

        Optional<String> trigger = ESCALATION_TRIGGERS.stream().filter(lower::contains).findFirst();
        if (trigger.isPresent()) {
            return route(state, Category.ESCALATE, "Escalation trigger detected: \"" + trigger.get().trim() + "\"");
        }

        return llm.complete(SYSTEM_PROMPT, state.promptWithHistory())
                .flatMap(answer -> parse(state, answer))
                .orElseGet(() -> classifyByKeywords(state, lower));
    }

    private static Optional<Map<String, Object>> parse(SupportState state, String answer) {
        String[] parts = answer.split("\\|", 2);
        try {
            Category category = Category.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
            String reason = parts.length > 1 ? parts[1].trim() : "Classified by LLM";
            return Optional.of(route(state, category, "LLM: " + reason));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Map<String, Object> classifyByKeywords(SupportState state, String lower) {
        long billing = BILLING_WORDS.stream().filter(lower::contains).count();
        long technical = TECHNICAL_WORDS.stream().filter(lower::contains).count();
        if (billing == 0 && technical == 0) {
            // A follow-up like "yes please" stays with the agent that handled the previous turn.
            Optional<String> previous = state.value(SupportState.CATEGORY);
            if (state.history().size() > 1 && previous.isPresent() && !Category.ESCALATE.name().equals(previous.get())) {
                return route(state, Category.valueOf(previous.get()), "Follow-up to the previous " + previous.get() + " turn");
            }
            return route(state, Category.GENERAL, "No billing or technical keywords");
        }
        String counts = "Keyword match (billing=" + billing + ", technical=" + technical + ")";
        return route(state, billing >= technical ? Category.BILLING : Category.TECHNICAL, counts);
    }

    private static Map<String, Object> route(SupportState state, Category category, String reason) {
        return Map.of(
                SupportState.CATEGORY, category.name(),
                SupportState.ROUTING_REASON, reason,
                SupportState.TRACE, state.traceWith("router: " + category + " (" + reason + ")"));
    }
}
