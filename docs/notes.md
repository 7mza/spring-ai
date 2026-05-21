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
