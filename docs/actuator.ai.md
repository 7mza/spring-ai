# FIXME: use this to build dashboards

## gen_ai.client.token.usage

```json
{
  "availableTags": [
    {
      "tag": "gen_ai.operation.name",
      "values": ["chat", "embedding"]
    },
    {
      "tag": "gen_ai.response.model",
      "values": [
        "nomic-embed-text-v2-moe",
        "gemma4:e4b",
        "gemma4:e4bgemma4:e4bgemma4:e4bgemma4:e4b",
        "gemma4:e4bgemma4:e4b"
      ]
    },
    {
      "tag": "gen_ai.request.model",
      "values": ["gemma4:e4b", "none"]
    },
    {
      "tag": "gen_ai.token.type",
      "values": ["output", "input", "total"]
    },
    {
      "tag": "gen_ai.system",
      "values": ["ollama"]
    }
  ],
  "description": "Measures number of input and output tokens used",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 38454.0
    }
  ],
  "name": "gen_ai.client.token.usage"
}
```

##

```json

```
