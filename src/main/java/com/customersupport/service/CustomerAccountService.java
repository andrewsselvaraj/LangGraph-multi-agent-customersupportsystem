package com.customersupport.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory stand-in for a billing system. Replace with a real client in production. */
@Service
public class CustomerAccountService {

    public record Invoice(String id, LocalDate date, BigDecimal amount, String status) {
    }

    public record Account(String customerId, String name, String plan, BigDecimal monthlyPrice,
                          LocalDate nextBillingDate, List<Invoice> invoices) {
    }

    public record Refund(String id, String customerId, String invoiceId, BigDecimal amount, String reason,
                         Instant createdAt) {
    }

    private final Map<String, Refund> refundsByInvoice = new ConcurrentHashMap<>();
    private final AtomicInteger refundSequence = new AtomicInteger(5000);

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

    /** Records a refund; returns empty if the invoice was already refunded. */
    public Optional<Refund> issueRefund(String customerId, String invoiceId, BigDecimal amount, String reason) {
        Refund refund = new Refund("RF-" + refundSequence.incrementAndGet(), customerId, invoiceId, amount, reason,
                Instant.now());
        return refundsByInvoice.putIfAbsent(invoiceId, refund) == null ? Optional.of(refund) : Optional.empty();
    }

    public Optional<Refund> refundFor(String invoiceId) {
        return Optional.ofNullable(refundsByInvoice.get(invoiceId));
    }
}
