package com.customersupport.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One customer message. Leave {@code conversationId} out to start a new conversation, and send
 * back the id from the response to continue it.
 */
public record ChatRequest(
        @Size(max = 64) String conversationId,
        @Size(max = 64) String customerId,
        @NotBlank @Size(max = 4000) String message) {
}
