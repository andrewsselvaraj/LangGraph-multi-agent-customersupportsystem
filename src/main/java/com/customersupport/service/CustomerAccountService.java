package com.customersupport.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory stand-in for a billing system. Replace with a real client in production. */
@Service
public class CustomerAccountService {

    public record Invoice(String id, LocalDate date, BigDecimal amount, String status) {
    }

    public record Account(String customerId, String name, String plan, BigDecimal monthlyPrice,
                          LocalDate nextBillingDate, List<Invoice> invoices) {
    }

    private final Map<String, Account> accounts = Map.of(
            "C-1001", new Account("C-1001", "Alice Johnson", "Pro", new BigDecimal("49.00"),
                    LocalDate.of(2026, 11, 1),
                    List.of(new Invoice("INV-9001", LocalDate.of(2026, 10, 1), new BigDecimal("49.00"), "PAID"),
                            new Invoice("INV-8801", LocalDate.of(2026, 9, 1), new BigDecimal("49.00"), "PAID"))),
            "C-1002", new Account("C-1002", "Bob Smith", "Enterprise", new BigDecimal("899.00"),
                    LocalDate.of(2026, 11, 15),
                    List.of(new Invoice("INV-9002", LocalDate.of(2026, 10, 15), new BigDecimal("899.00"), "OVERDUE"))),
            "C-1003", new Account("C-1003", "Carol Lee", "Basic", new BigDecimal("9.00"),
                    LocalDate.of(2026, 11, 3),
                    List.of(new Invoice("INV-9003", LocalDate.of(2026, 10, 3), new BigDecimal("9.00"), "PAID"),
                            new Invoice("INV-9004", LocalDate.of(2026, 10, 3), new BigDecimal("9.00"), "PAID")))
    );

    public Optional<Account> find(String customerId) {
        return Optional.ofNullable(accounts.get(customerId));
    }
}
