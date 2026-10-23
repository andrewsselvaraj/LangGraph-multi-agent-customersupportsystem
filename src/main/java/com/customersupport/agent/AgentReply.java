package com.customersupport.agent;

import com.customersupport.graph.SupportState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers shared by the specialist agents for building their state updates and for
 * reading the escalation marker an LLM can put at the start of its answer.
 */
final class AgentReply {

    /** An LLM-backed specialist starts its answer with this when a human must take over. */
    static final String ESCALATE_MARKER = "[ESCALATE]";

    static final String ESCALATION_INSTRUCTIONS = """
            If you cannot resolve the request safely within policy, or the customer needs an action \
            only a human can take, reply with exactly %s followed by a one-sentence reason, and nothing else.""".formatted(ESCALATE_MARKER);

    private AgentReply() {
    }

    static Map<String, Object> resolved(String agent, String response) {
        Map<String, Object> update = new HashMap<>();
        update.put(SupportState.HANDLED_BY, agent);
        update.put(SupportState.RESPONSE, response);
        update.put(SupportState.NEEDS_ESCALATION, false);
        update.put(SupportState.TRACE, List.of(agent + ": resolved"));
        return update;
    }

    static Map<String, Object> escalate(String agent, String reason) {
        Map<String, Object> update = new HashMap<>();
        update.put(SupportState.HANDLED_BY, agent);
        update.put(SupportState.NEEDS_ESCALATION, true);
        update.put(SupportState.ESCALATION_REASON, reason);
        update.put(SupportState.TRACE, List.of(agent + ": escalating (" + reason + ")"));
        return update;
    }

    /** Turns an LLM answer into either a resolution or an escalation. */
    static Map<String, Object> fromLlm(String agent, String answer) {
        if (answer.startsWith(ESCALATE_MARKER)) {
            String reason = answer.substring(ESCALATE_MARKER.length()).trim();
            return escalate(agent, reason.isEmpty() ? "Agent could not resolve the request" : reason);
        }
        return resolved(agent, answer);
    }
}
