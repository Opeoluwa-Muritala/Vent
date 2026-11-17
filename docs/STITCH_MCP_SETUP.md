# Stitch MCP Setup

Vent uses Google's official remote Stitch MCP endpoint:

```text
https://stitch.googleapis.com/mcp
```

Create an API key in Stitch settings. Keep it outside this repository.

In PowerShell, add the server to Codex using the executable shim (the `.ps1` shim is blocked by this machine's execution policy):

```powershell
codex.cmd mcp add stitch --url https://stitch.googleapis.com/mcp
```

Then add the API-key header mapping beneath `[mcp_servers.stitch]` in `%USERPROFILE%\.codex\config.toml`:

```toml
[mcp_servers.stitch]
url = "https://stitch.googleapis.com/mcp"
env_http_headers = { "X-Goog-Api-Key" = "STITCH_API_KEY" }
```

Set `STITCH_API_KEY` in the environment that launches Codex, restart Codex, and verify:

```powershell
codex.cmd mcp list
```

Do not paste the key into this repository, `AGENTS.md`, shell scripts, or committed configuration. Once connected, ask Stitch to create a project named **Vent** using `STITCH_PROMPT.md` as the generation prompt.
