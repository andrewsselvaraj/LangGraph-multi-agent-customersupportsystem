package com.customersupport.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared state that flows through the support graph. Every agent reads from it and
 * returns a partial update; LangGraph4j merges the updates using the channels below.
 */
public class SupportState extends AgentState {

    public static final String CUSTOMER_ID = "customerId";
    public static final String MESSAGE = "message";
    public static final String CATEGORY = "category";
    public static final String ROUTING_REASON = "routingReason";
    public static final String RESPONSE = "response";
    public static final String HANDLED_BY = "handledBy";
    public static final String NEEDS_ESCALATION = "needsEscalation";
    public static final String ESCALATION_REASON = "escalationReason";
    public static final String TICKET_ID = "ticketId";
    public static final String TRACE = "trace";

    /** Only the trace needs a reducer: each node appends the step it performed. */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            TRACE, Channels.appenderWithDuplicate(ArrayList::new)
    );

    public SupportState(Map<String, Object> initData) {
        super(initData);
    }

    public String customerId() {
        return this.<String>value(CUSTOMER_ID).orElse("anonymous");
    }

    public String message() {
        return this.<String>value(MESSAGE).orElse("");
    }

    public Category category() {
        return this.<String>value(CATEGORY).map(Category::valueOf).orElse(Category.GENERAL);
    }

    public Optional<String> routingReason() {
        return value(ROUTING_REASON);
    }

    public Optional<String> response() {
        return value(RESPONSE);
    }

    public Optional<String> handledBy() {
        return value(HANDLED_BY);
    }

    public boolean needsEscalation() {
        return this.<Boolean>value(NEEDS_ESCALATION).orElse(false);
    }

    public Optional<String> escalationReason() {
        return value(ESCALATION_REASON);
    }

    public Optional<String> ticketId() {
        return value(TICKET_ID);
    }

    public List<String> trace() {
        return this.<List<String>>value(TRACE).orElseGet(List::of);
    }
}
