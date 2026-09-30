# Instructions for Claude Code

Read `docs/SPEC.md` and `docs/LESSONS.md` before writing anything. The spec is
the source of truth; if code and spec disagree, stop and flag it.

## Rules

- Everything fails soft: a missing file or service means older behaviour, never a crash.
- One writer per shared file; write temp + rename.
- Pin mineflayer exactly (dependencies + overrides). Match Minecraft 1.21.6 exactly.
- Log every director action to `stimuli` with a timestamp. The experiment depends on it.
- All RNG goes through one seeded generator; seed stored on the run row.
- Windows exit codes are unsigned (4294967295). Never let one agent crash the server.
- No healing of any kind. Lives only go down.

## Build order (one step per session, test before moving on)

1. **Paper plugin** (`plugin/`): three-hit rule (cancel real damage, set health,
   slowness tiers, no natural regen), proximity chat (~24 blocks, forward Alex's
   lines to the bridge, `*` = text-only), wounded effects + blood particles,
   carry (addPassenger), stun on hitting the killer.
2. **Death hook + bodies**: no respawn for dead campers, armor stand body,
   `bodies` row.
3. **Proximity messages**: sign text, body discovery, sound-as-text for agents.
4. **Killer bot** (`killer/`): DORMANT/SIGN/STALK/REVEAL/STRIKE/VANISH plus every
   weakness in the spec (line of sight, burst sprint, doors, torchlight slow,
   torch breaking, groups, water, chase clock, hit cap, reaction delay).
5. **Director** (`bridge/director`): tension, rolls, kill budget, locks, chases,
   survival-action dodge checks, extra beats, sounds via `/playsound lightsout:*`.
6. **Voice**: ElevenLabs Flash queue, one speaker at a time, Alex jumps the queue,
   stale lines dropped live but archived, volume by distance.
7. **Radio, police, backup, endings.**
8. **Watcher**: reads `watch_*` views only.
