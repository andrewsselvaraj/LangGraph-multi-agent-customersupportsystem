package com.customersupport.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Company policies and frequently asked questions. */
@Service
public class Faq {

    private final Map<List<String>, String> entries = new LinkedHashMap<>();

    public Faq() {
        entries.put(List.of("hours", "open", "available", "when can"),
                "Our support team is available Monday to Friday, 8am-8pm, and Saturday 9am-5pm (UTC). "
                        + "This chat is available 24/7.");
        entries.put(List.of("contact", "phone", "email", "call"),
                "You can reach us at support@example.com or +1 800 555 0100, or keep chatting here.");
        entries.put(List.of("cancel", "close my account", "delete my account"),
                "You can cancel any time under Settings > Account > Cancel subscription. Your data stays "
                        + "available for 30 days in case you change your mind.");
        entries.put(List.of("feature", "suggest", "feedback", "idea"),
                "Thanks for the feedback! I've passed it to our product team. You can also vote on ideas at "
                        + "feedback.example.com.");
        entries.put(List.of("privacy", "gdpr", "data"),
                "We never sell your data. You can download or delete it under Settings > Privacy. Our full policy "
                        + "is at example.com/privacy.");
    }

    public Optional<String> answer(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        return entries.entrySet().stream()
                .filter(e -> e.getKey().stream().anyMatch(lower::contains))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
