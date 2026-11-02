package com.mg.llm.result;

public sealed interface LLMResult permits Ok, RateLimited, TimedOut, Broken, ClientException {
}
