# Notes

## Chat Memory: Vector Store vs JDBC

### When JDBC / in-memory is better than vector store for chat memory

- **Recency beats relevance** — the last N messages are almost always what the model needs, not the semantically closest
  ones. Vector search can surface a message from 50 turns ago while skipping what was said 2 turns ago.
- **Ordering is lost with vectors** — you get relevant chunks, not a coherent thread. Temporal references ("as I
  mentioned", "building on that") break down.
- **Extra latency and dependency** — every memory read requires an embedding call. JDBC is a fast indexed query with no
  model involved, and keeps working if Qdrant is slow or down.
- **Sliding window is the right primitive** — `MessageWindowChatMemory` over JDBC is simpler and more predictable for
  standard chat.

### When vector memory does make sense

- Very long multi-session memory (hundreds of past conversations) where you want to surface a relevant discussion from
  weeks ago.
- Agent memory where past _knowledge_ matters more than past _conversation flow_.
- You're already embedding everything and want unified storage.

---

## Chat Memory: Session / ConversationId

### Production (stateful) flow

1. `POST /sessions` → server creates a session record (DB / Redis), returns a `sessionId`
2. Client passes `sessionId` on every request (header or body)
3. Server validates it exists, is not expired, belongs to the caller — rejects otherwise
4. Messages stored under that ID in the memory backend
5. Session expires via TTL or `DELETE /sessions/{id}`

### Production (stateless) flow — JWT

1. First request carries no token → server generates a `conversationId` (UUID), signs it into a JWT, returns it in the
   response body
2. Client sends `Authorization: Bearer <token>` on every subsequent request
3. Server verifies signature, extracts `conversationId` — no DB lookup needed
4. `conversationId` is passed to `MessageChatMemoryAdvisor`; actual messages live in the memory backend keyed by it
5. Expiry via JWT `exp` claim — server rejects expired tokens with 401, client starts a new session

The JWT _is_ the session. The memory store (JDBC / Qdrant) still holds the messages — the JWT just carries the key.

---

## LLM Tool Calling: Long-Running Operations

### The core problem

Spring AI tool execution is synchronous within the chat request cycle. The flow is inherently sequential:

```
prompt → LLM (slow) → tool call → LLM again (slow) → response
```

The HTTP request blocks for the full duration. A slow tool (DB query, external API) means the user stares at a
spinner, and you risk timeouts at multiple layers (Ollama client, servlet, load balancer, browser).

### Strategy 1: Async + polling (client-driven) — recommended

The tool fires the job in the background and returns a `jobId` instantly. The HTTP thread is freed immediately after
the first LLM response. The client then polls a plain status endpoint (no LLM involved) until the result is ready,
and sends it back in a final message.

```
Turn 1:
  User: "What time is it in Tokyo?"
  LLM → calls startGetTime("Asia/Tokyo") → gets "abc-123" instantly
  LLM → "I've kicked off the lookup, I'll have an answer shortly."
  HTTP thread freed ✓

  [client polls GET /jobs/abc-123 in the background...]

Turn 2 (client sees result is ready, sends it back):
  User: "Result is ready: 2026-05-22T21:00:00" # HAMZA: WTF ?!
  LLM → "It's 9:00 PM in Tokyo."
```

This is the most practical pattern. The LLM is out of the loop during the wait — the client handles polling directly
and re-engages the LLM only when the result is in hand.

### Strategy 2: Two-tool pattern (LLM-driven polling)

Expose two tools to the LLM — one to start the job, one to check its result. The LLM orchestrates the polling itself
across conversation turns, but it has no timer so the client still has to nudge it each turn.

```kotlin
@Tool(description = "Start fetching the current time for a location. Returns a jobId.")
fun startGetTime(
    @ToolParam(description = "IANA timezone ID, e.g. 'Asia/Tokyo'") timeZone: String
): String {
    val jobId = UUID.randomUUID().toString()
    executor.submit { jobStore[jobId] = fetchTimeSlowly(timeZone) }
    return jobId
}

@Tool(description = "Check if a time lookup is done. Returns the time if ready, or 'PENDING' if still running.")
fun getTimeResult(
    @ToolParam(description = "The jobId returned by startGetTime") jobId: String
): String = jobStore[jobId] ?: "PENDING"
```

```
Turn 1:
  LLM → calls startGetTime("Asia/Tokyo") → gets "abc-123"
  LLM → "Still working on it, give me a moment..."

Turn 2 (user: "any update?"):
  LLM → calls getTimeResult("abc-123") → "PENDING"
  LLM → "Not ready yet..."

Turn 3 (user: "any update?"):
  LLM → calls getTimeResult("abc-123") → "2026-05-22T21:00:00"
  LLM → "It's 9:00 PM in Tokyo."
```

Feels more agentic but is awkward in practice — the client must keep nudging the conversation and the UX depends on
the LLM consistently choosing to re-check rather than giving up.

### Other strategies

- **Pre-fetch / cache** — if the slow data is predictable, warm it before the LLM needs it. Tool reads from cache and
  returns instantly. Best when inputs are known ahead of time.
- **Streaming** — `stream().content()` helps with LLM token generation latency but the tool call still blocks the
  stream until it resolves. Buys nothing for slow tools specifically.

### Why Webflux is not the answer

Making the tool non-blocking does not unblock the LLM cycle — the LLM step forces sequential execution regardless.
Webflux only helps inside a tool if it fans out to multiple I/O sources in parallel (e.g. `Flux.zip` over 3
databases). For everything else, Spring MVC + virtual threads (Java 21+) gives the same concurrency benefits without
the API complexity. Mixing blocking and reactive requires `block()` somewhere, which defeats the purpose.
