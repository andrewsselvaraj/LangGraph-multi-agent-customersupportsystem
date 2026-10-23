package com.customersupport.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Tiny keyword-matched knowledge base used by the technical support agent. */
@Service
public class KnowledgeBase {

    public record Article(String id, String title, List<String> keywords, String solution) {
    }

    private final List<Article> articles = List.of(
            new Article("KB-101", "Resetting your password",
                    List.of("password", "reset", "forgot", "login", "log in", "sign in", "locked"),
                    "Go to the sign-in page and click \"Forgot password\". We will email a reset link that is valid "
                            + "for 30 minutes. If your account is locked after too many attempts, wait 15 minutes and try again."),
            new Article("KB-102", "App crashes or freezes",
                    List.of("crash", "crashes", "freeze", "frozen", "not responding", "hangs"),
                    "Update the app to the latest version, then clear its cache (Settings > Storage > Clear cache) "
                            + "and restart your device. If it still crashes, reinstall the app."),
            new Article("KB-103", "Slow performance or timeouts",
                    List.of("slow", "timeout", "time out", "lag", "loading", "latency"),
                    "Check status.example.com for ongoing incidents, try a different network, and disable browser "
                            + "extensions. Large exports can take a few minutes; you will get an email when they finish."),
            new Article("KB-104", "API errors",
                    List.of("api", "401", "403", "429", "500", "error code", "rate limit", "token"),
                    "A 401/403 means the API key is missing or lacks permission: regenerate it under Settings > API. "
                            + "A 429 means you hit the rate limit: back off and retry with exponential delay."),
            new Article("KB-105", "Installation and setup",
                    List.of("install", "setup", "set up", "download", "configure", "integration"),
                    "Download the installer from the Downloads page, run it, and sign in with your account. "
                            + "Integrations are configured under Settings > Integrations.")
    );

    public Optional<Article> search(String query) {
        String text = query.toLowerCase(Locale.ROOT);
        Article best = null;
        long bestScore = 0;
        for (Article article : articles) {
            long score = article.keywords().stream().filter(text::contains).count();
            if (score > bestScore) {
                best = article;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }
}
