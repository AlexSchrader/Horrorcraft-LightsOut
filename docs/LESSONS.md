# What Valley cost to find out

Everything here was paid for once already. None of it is obvious, several of
them are silent when they break, and a few cost real money while looking fine.

Read this before writing the agent runtime, the bridge or the watcher.

---

## Money

**Every agent turn is one API call.** Measured on Sonnet 5 at a 4.5s cooldown:
**$1.53/hr per agent**, so eight is about **$12/hr**. Cost is linear in agent
count and in think rate, and those are the only two dials that behave simply.

```
(cached input × $0.20 + fresh input × $2 + output × $10) / 1,000,000
```

**Set a spend cap in the provider console before any long run.** A meter tells
you what is happening; only the console can stop it.

## Prompt caching, which is worth 10x

Caching is a **prefix match**: everything before the breakpoint must be
byte-identical between calls. Put anything that changes per turn — memory,
stats, inventory — ahead of the largest stable block and **nothing will ever
cache**, because what is left above it falls under the minimum cacheable size.

Order the system prompt:

```
<persona>
<command docs>          ← large, stable, cacheable
<world history>
[[[VOLATILE]]]          ← split here, cache_control on everything above
<examples>
<memory>
<stats>
<inventory>
```

Examples belong on the volatile side: they are selected by relevance and differ
per call, so they would break the prefix.

Log `cache read` per call. **If it stays at zero the bill is several times what
it should be** and nothing else will tell you.

## The prompt

**Few-shot examples beat instructions.** Valley shipped for weeks with upstream's
stock examples — *"Sure, I can do that"*, *"Apologies, I made an error"* — while
the persona said *not a helper, act and say nothing, never ask permission*. The
examples won. Write examples in the voice you actually want, or the persona is
decoration.

Two details that make examples land:

- If relevance selection falls back to **word overlap** (it will, if the provider
  has no embeddings endpoint), an example whose prompt text matches the real turn
  text nearly verbatim wins selection. Build the common turn types out of the
  literal strings the runtime sends.
- Same for anything the world says to an agent. Quote the exact string and the
  match is perfect.

**`$MEMORY` must actually be in the conversing prompt.** Otherwise the runtime
still writes memory summaries every turn and never reads them back — write-only
memory, paid for in full.

**Frame the summariser as data processing, not roleplay.** Phrased as in-character
narration, the model periodically refuses mid-run and writes *"I'm Claude, made by
Anthropic. I won't continue this roleplay…"* **into the agent's persistent
memory**, where it self-perpetuates through `Old memory: '$MEMORY'`.

**Split memory into FACTS and BELIEFS.** The summariser has no ground truth: it
summarises what the agent *said*, not what the world *did*. An agent miscounted
its inventory, concluded *"someone took them"*, and that fabrication was written
into memory as fact within four turns. Facts are events confirmed by action
output; beliefs are written as *"<name> believes …"*.

**API outages poison memory.** When calls fail, the failure string travels the
same path as speech and the summariser writes *"My brain disconnected, try
again."* into memory as an event. Clean memory files before rebooting after an
outage.

## The loop

**Never let thinking be fatal.** Upstream set a self-prompter to `STOPPED` after
three replies without a command, and *nothing restarts a STOPPED prompter*. An
agent that spent three turns talking went dormant for the rest of the run — alive,
still answering if spoken to, never acting again. Four of eight died this way in
one session within seven seconds of each other. Stand down and resume when idle
instead.

**Self-prompting has to be started.** If the loop only begins when the agent
issues a goal, then an agent that never issues one simply sits there forever. Make
the spawn message demand it, and check on boot that every agent did.

**Tell them how much daylight is left.** "Morning / Afternoon / Night" says
nothing about whether there is time to finish a wall. Real minutes until dark
changes what they choose.

## Talking

**Agents overhearing each other is the expensive failure.** One undirected line
that every agent reacts to is N calls. Directed speech should reach its addressee
and the watcher without going through world chat.

**But do not go deaf to the human.** Valley ignored open chat entirely once a
second agent existed — while separately already refusing to answer other agents,
so the gate saved nothing and only stopped *you* being heard. Gate on **who is
speaking**, not on how many agents exist.

## Shared state beats broadcast

Anything every agent must know — who holds office, what the settlement has named,
which kingdoms exist — belongs in a file read at prompt-build time, not in a
message. Everyone sees it within one turn, it survives a restart, it costs no
extra call, and there is no delivery to get wrong.

Keep **one writer per file.** Eight agents and a bridge doing read-modify-write on
the same JSON will lose updates. Write to a temp file and rename.

**Everything fails soft.** A missing file means the older behaviour, never a
crash.

## Database

**RLS filters rows, not columns.** A select policy on a table holding an agent's
private prompt exposes that prompt to anyone with the app URL. Point the browser
at a narrow view; keep the base table closed.

**A policy is only half.** RLS says which rows; the table `GRANT` says whether the
role may look at all. Hosted Supabase's default privileges grant `anon` on new
tables in `public`, so a schema that omits grants works there and nowhere else —
and the grant on a new table ends up decided by project settings rather than by
your migration. State both halves per table.

**A missing select policy is silent.** It returns an empty array, which is
indistinguishable from an empty table. Compare the same count under both keys.

**The SQL editor runs a script in one transaction.** One failing statement rolls
back the whole thing and still shows a green tick. A migration can be "run" and
absent.

**Create your own tables.** Valley's migrations only added columns to a schema
that lived nowhere in the repository, so the project could not be stood up from a
clean database at all.

**Partial indexes cannot serve `ON CONFLICT`.** A dedupe index with a `where`
clause plus `on_conflict=` returns `42P10` and drops every row. Insert plainly and
treat the conflict as success.

## Minecraft

**Pin mineflayer exactly.** A patch built for one version fails against the next,
and `npm install` still exits 0. Digging and item use then misbehave with nothing
saying why. Pin in both `dependencies` and `overrides`, and check for the error
after install.

**Match the Minecraft version exactly.** Mineflayer negotiates a fixed protocol;
a mismatch fails the handshake rather than degrading.

**`/difficulty` overrides `server.properties` permanently** by writing to
`level.dat`. A `Peaceful` world sat under `difficulty=normal` for an entire
session and combat silently did not work.

**All players must sleep to skip the night** at the default
`playersSleepingPercentage`. With one agent that is trivial; with eight, one
insomniac holds everybody in the dark. This is a great story generator and an
expensive one — the calls keep burning and nothing gets built.

**Windows exit codes are unsigned.** A crashed child reports `4294967295`, so
`if (code > 1) process.exit(code)` in a supervisor takes the whole server down
with one agent.

**Cap how far they may travel, and cap search separately.** An agent once
escalated a block search 64 → 512 and did nothing else for an entire run. Search
cost grows with the *cube* of the radius; travel does not. They are different
limits.
