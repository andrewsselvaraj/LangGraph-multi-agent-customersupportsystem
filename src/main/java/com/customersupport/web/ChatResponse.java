package com.customersupport.web;

import com.customersupport.graph.SupportState;

import java.util.List;

public record ChatResponse(
        String conversationId,
        String customerId,
        String category,
        String routingReason,
        String handledBy,
        String response,
        boolean escalated,
        String escalationReason,
        String ticketId,
        List<String> trace) {

    public static ChatResponse from(String conversationId, SupportState state) {
        return new ChatResponse(
                conversationId,
                state.customerId(),
                state.category().name(),
                state.routingReason().orElse(null),
                state.handledBy().orElse(null),
                state.response().orElse(null),
                state.ticketId().isPresent(),
                state.escalationReason().orElse(null),
                state.ticketId().orElse(null),
                state.trace());
    }
}
