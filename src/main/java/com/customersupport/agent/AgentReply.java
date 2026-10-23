package com.customersupport.agent;

import com.customersupport.agent.tools.SupportTools;
import com.customersupport.graph.SupportState;
import com.customersupport.llm.AgentRun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the state updates the specialist agents return. */
final class AgentReply {

    private AgentReply() {
    }

    static Map<String, Object> resolved(SupportState state, String agent, String response, List<String> steps) {
        List<String> trace = new ArrayList<>(steps);
        trace.add(agent + ": resolved");
        Map<String, Object> update = new HashMap<>();
        update.put(SupportState.HANDLED_BY, agent);
        update.put(SupportState.RESPONSE, response);
        update.put(SupportState.NEEDS_ESCALATION, false);
        update.put(SupportState.TRACE, state.traceWith(trace.toArray(String[]::new)));
        update.put(SupportState.HISTORY, List.of("Agent (" + agent + "): " + response));
        return update;
    }

    static Map<String, Object> resolved(SupportState state, String agent, String response) {
        return resolved(state, agent, response, List.of());
    }

    static Map<String, Object> escalate(SupportState state, String agent, String reason, List<String> steps) {
        List<String> trace = new ArrayList<>(steps);
        trace.add(agent + ": escalating (" + reason + ")");
        Map<String, Object> update = new HashMap<>();
        update.put(SupportState.HANDLED_BY, agent);
        update.put(SupportState.NEEDS_ESCALATION, true);
        update.put(SupportState.ESCALATION_REASON, reason);
        update.put(SupportState.TRACE, state.traceWith(trace.toArray(String[]::new)));
        return update;
    }

    static Map<String, Object> escalate(SupportState state, String agent, String reason) {
        return escalate(state, agent, reason, List.of());
    }

    /** Turns a finished tool-calling run into a resolution or an escalation. */
    static Map<String, Object> fromRun(SupportState state, String agent, AgentRun run, SupportTools tools) {
        List<String> steps = run.toolCalls().stream().map(call -> agent + " -> " + call).toList();
        if (tools.escalationReason().isPresent()) {
            return escalate(state, agent, tools.escalationReason().get(), steps);
        }
        if (run.answer().isBlank()) {
            return escalate(state, agent, "Agent could not finish the request", steps);
        }
        return resolved(state, agent, run.answer(), steps);
    }
}
