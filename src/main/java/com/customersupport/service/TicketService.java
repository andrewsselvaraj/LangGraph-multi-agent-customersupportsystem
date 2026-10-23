package com.customersupport.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Queue of conversations handed off to human agents. In-memory for the demo. */
@Service
public class TicketService {

    public enum Priority { LOW, NORMAL, HIGH, URGENT }

    public record Ticket(String id, String customerId, String message, String category,
                         String reason, Priority priority, Instant createdAt) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1000);

    public Ticket open(String customerId, String message, String category, String reason, Priority priority) {
        String id = "TCK-" + sequence.incrementAndGet();
        Ticket ticket = new Ticket(id, customerId, message, category, reason, priority, Instant.now());
        tickets.put(id, ticket);
        return ticket;
    }

    public Collection<Ticket> all() {
        List<Ticket> list = new ArrayList<>(tickets.values());
        list.sort((a, b) -> a.createdAt().compareTo(b.createdAt()));
        return list;
    }
}
