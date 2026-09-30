# Horrorcraft-LightsOut 🔦

Four LLM campers and one human in a dark oak forest. None of them are told
there is a killer. An experiment first, a movie second: how do agents behave
under horror?

| Piece | State |
| --- | --- |
| Spec | **done**: `docs/SPEC.md` |
| Lessons from Valley/Polis | `docs/LESSONS.md` |
| Schema | **done**, tested on Postgres 16: `supabase/001_schema.sql` |
| Skins (8) | `assets/skins/` (alex + josh = slim model) |
| Sound pack (20) | `assets/resourcepack/lightsout-sounds.zip` |
| Voices | **done**: `config/voices.json` (7 voices, killer silent) |
| Paper plugin | not started |
| Killer bot | not started |
| Bridge + director | not started |
| Agent runtime (mindcraft fork) | not started |
| Watcher | not started |

## World

- Paper **1.21.6**, own folder `C:\dev\lightsout-server` (never share with Polis)
- Seed `-1541124385142397106`
- Trailhead `83 88 283` · Village `32 ~ 368` · Mansion `168 ~ 408`

## Setup

1. Run `supabase/001_schema.sql` in the SQL editor, then confirm with the
   publishable key that `watch_campers` returns rows and `campers` is denied.
2. Host `lightsout-sounds.zip`, set `resource-pack`, `resource-pack-sha1`,
   `require-resource-pack=true` in `server.properties`.
3. See `CLAUDE.md` for build order.
