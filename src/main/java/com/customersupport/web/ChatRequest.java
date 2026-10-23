package com.customersupport.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @Size(max = 64) String customerId,
        @NotBlank @Size(max = 4000) String message) {
}
