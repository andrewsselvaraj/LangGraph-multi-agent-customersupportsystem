package com.customersupport.llm;

import java.util.List;

/** Result of a tool-calling run: the model's final answer and the tools it called on the way. */
public record AgentRun(String answer, List<String> toolCalls) {
}
