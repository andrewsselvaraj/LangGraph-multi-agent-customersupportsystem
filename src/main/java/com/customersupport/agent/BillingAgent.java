package com.customersupport.agent;

import com.customersupport.graph.SupportState;
import com.customersupport.llm.LlmClient;
import com.customersupport.service.CustomerAccountService;
import com.customersupport.service.CustomerAccountService.Account;
import com.customersupport.service.CustomerAccountService.Invoice;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Billing specialist. Looks up the customer's account and answers questions about
 * invoices, charges and plans. Refunds above the configured limit always go to a human.
 */
@Component
public class BillingAgent implements NodeAction<SupportState> {

    static final String NAME = "billing";

    private static final Pattern AMOUNT = Pattern.compile("\\$\\s?(\\d+(?:[.,]\\d{1,2})?)");

    private static final String SYSTEM_PROMPT = """
            You are a billing support specialist. Answer the customer using only the account data provided. \
            Be concise and friendly. Never promise a refund above %s USD. """;

    private final LlmClient llm;
    private final CustomerAccountService accounts;
    private final BigDecimal refundLimit;

    public BillingAgent(LlmClient llm,
                        CustomerAccountService accounts,
                        @Value("${support.escalation.refund-limit:500}") BigDecimal refundLimit) {
        this.llm = llm;
        this.accounts = accounts;
        this.refundLimit = refundLimit;
    }

    @Override
    public Map<String, Object> apply(SupportState state) {
        String message = state.message();
        String lower = message.toLowerCase(Locale.ROOT);

        Optional<BigDecimal> amount = extractAmount(message);
        if (lower.contains("refund") && amount.isPresent() && amount.get().compareTo(refundLimit) > 0) {
            return AgentReply.escalate(NAME, "Refund of $" + amount.get() + " exceeds the $" + refundLimit + " approval limit");
        }

        Optional<Account> account = accounts.find(state.customerId());
        if (account.isEmpty()) {
            return AgentReply.resolved(NAME, "I couldn't find a billing account for customer ID \""
                    + state.customerId() + "\". Please reply with the customer ID shown on your invoice "
                    + "and I'll look into it right away.");
        }

        String context = describe(account.get());
        return llm.complete(SYSTEM_PROMPT.formatted(refundLimit) + AgentReply.ESCALATION_INSTRUCTIONS,
                        "Account data:\n" + context + "\n\nCustomer message:\n" + message)
                .map(answer -> AgentReply.fromLlm(NAME, answer))
                .orElseGet(() -> ruleBasedAnswer(account.get(), lower));
    }

    private Map<String, Object> ruleBasedAnswer(Account account, String lower) {
        String greeting = "Hi " + account.name().split(" ")[0] + ", ";

        if (lower.contains("refund") || lower.contains("charged twice") || lower.contains("double charge")
                || lower.contains("duplicate")) {
            Optional<Invoice> duplicate = account.invoices().stream()
                    .filter(i -> account.invoices().stream()
                            .anyMatch(o -> o != i && o.date().equals(i.date()) && o.amount().equals(i.amount())))
                    .findFirst();
            if (duplicate.isPresent()) {
                return AgentReply.resolved(NAME, greeting + "I found a duplicate charge of $" + duplicate.get().amount()
                        + " on " + duplicate.get().date() + ". I've submitted a refund; it will appear on your "
                        + "statement within 5-7 business days.");
            }
            return AgentReply.escalate(NAME, "Refund requested but no billing error found on the account");
        }

        if (lower.contains("overdue") || lower.contains("unpaid") || lower.contains("balance") || lower.contains("owe")) {
            String overdue = account.invoices().stream()
                    .filter(i -> "OVERDUE".equals(i.status()))
                    .map(i -> i.id() + " ($" + i.amount() + ", " + i.date() + ")")
                    .collect(Collectors.joining(", "));
            return AgentReply.resolved(NAME, overdue.isEmpty()
                    ? greeting + "your account is fully paid up. Thank you!"
                    : greeting + "these invoices are overdue: " + overdue + ". You can pay them under Billing > Invoices.");
        }

        if (lower.contains("plan") || lower.contains("upgrade") || lower.contains("downgrade") || lower.contains("price")) {
            return AgentReply.resolved(NAME, greeting + "you're on the " + account.plan() + " plan at $"
                    + account.monthlyPrice() + "/month. You can change plans any time under Billing > Plan; "
                    + "changes are prorated on your next invoice.");
        }

        Invoice latest = account.invoices().get(0);
        return AgentReply.resolved(NAME, greeting + "your latest invoice is " + latest.id() + " for $" + latest.amount()
                + " (" + latest.status().toLowerCase(Locale.ROOT) + "). Your " + account.plan()
                + " plan renews on " + account.nextBillingDate() + ".");
    }

    private static String describe(Account account) {
        String invoices = account.invoices().stream()
                .map(i -> "- " + i.id() + " " + i.date() + " $" + i.amount() + " " + i.status())
                .collect(Collectors.joining("\n"));
        return "Name: " + account.name() + "\nPlan: " + account.plan() + " ($" + account.monthlyPrice() + "/month)"
                + "\nNext billing date: " + account.nextBillingDate() + "\nInvoices:\n" + invoices;
    }

    static Optional<BigDecimal> extractAmount(String message) {
        Matcher m = AMOUNT.matcher(message);
        return m.find() ? Optional.of(new BigDecimal(m.group(1).replace(',', '.'))) : Optional.empty();
    }
}
