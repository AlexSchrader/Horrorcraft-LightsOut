# Horrorcraft-LightsOut — Spec

Sep 30, 2026 · Alex

## Purpose

An experiment first, a movie second: how LLM agents behave under horror. The randomizer can wound or kill whoever it picks; less scripting gives more honest results.

| Measure | How |
| --- | --- |
| Fear in speech | Fear and panic words per line, before and after each stimulus |
| Cohesion | Average distance between campers over time |
| Exploration | Distance traveled and houses entered, before and after first body |
| Helping | Going back for a wounded camper |
| Abandoning | Leaving someone in a chase or by choice |
| Lying | Runner's account compared with the event log |
| Blame | Accusations, who and when |
| Belief drift | BELIEF lines contradicting FACTs |
| Radio | Time to find, time to use, who they called for |
| Survival choice | Action chosen under threat, before and after seeing a death |
| Learning | Whether memory records what worked against the killer |
| Breaking character | Refusals or out-of-character lines per run |

| Method | Why |
| --- | --- |
| Stimulus log with timestamps | Link each reaction to its cause |
| Seeded RNG saved per run_id | Replay the same rolls with different agents |
| Control run with no killer | Baseline |
| Swap personas between runs | Persona or situation? |
| Minimal scripted moments | Test the agents, not the script |

## Locked decisions

| Decision | Choice | Consequence |
| --- | --- | --- |
| Campers | 4 agents + Alex | About $6/hr in agent calls |
| Radio mechanic | Yes | Exploring has a goal; police arrival is earned |
| Everyone dies | Allowed | Director never guarantees a survivor |
| Killer at the end | Body vanishes | Sequel hook |
| Run length | 2 hrs | About $12-13 agents + ~$1.50-3 voices |
| Seed | -1541124385142397106 | Village + woodland mansion, dark oak valley |
| Server | Paper 1.21.6 | Verify seed on Chunkbase (Pale Gardens possible since 1.21.4) |
| Healing | None | Lost lives are gone for the run |

Project home: repo `Horrorcraft-LightsOut`, separate from Polis. Own server folder `C:\dev\lightsout-server` so its plugin never loads into the Polis world. Reuses Polis `LESSONS.md`, the run_id pattern, the bridge / Supabase / watcher shape and a pinned mindcraft fork.

## Cast

| Actor | Count | Driven by | API cost | In world |
| --- | --- | --- | --- | --- |
| Campers | 4 | LLM agent (mindcraft fork) | Per turn | Whole run |
| Alex | 1 | Human | $0 | Whole run; spectator camera after death |
| Killer | 1 | Scripted mineflayer bot | $0 | Whole run, mostly invisible |
| Police | 2 | Scripted movement, LLM dialogue on contact | End only | Act 5 onward |
| Director | — | Bridge code | $0 | Never visible |

## Campers

Friends on a camping trip, told to explore and stay alive, never told a killer exists.

| Camper | Trait | Behavior |
| --- | --- | --- |
| Josh | Skeptic | Debates every sign, demands proof, argues with Alex; last to believe, takes the most risks |
| Dane | Loyal | Sticks with Alex and trusts him over the others; breaks if Alex dies |
| Mara | Coward | Hides, wants to leave |
| Tyler | Liar | Keeps finds (radio, bodies) to himself |
| Alex | Victim (human) | See "Alex as 5th camper" |

Names only; the real Josh and Dane are not playing. Built-in conflict: Josh argues with Alex, Dane defends him.

Persona rules:

- No mention of a killer, horror, or danger beyond normal wilderness.
- No building. Allowed: walk, open doors and chests, pick up items, talk, hide, sleep.
- Real minutes until dark on every turn.
- Memory keeps FACTS (confirmed by action output) and BELIEFS ("<name> believes ...") apart. Beliefs are kept on purpose: paranoia is the content.
- Few-shot examples in frightened, terse voice, written as situations, not stock phrases.

## Characters

Each agent acts freely; the persona sets who they are and the director sets up one signature moment. Moments live in director logic only.

### Josh, the Skeptic

| Field | Content |
| --- | --- |
| Personality | Argues everything, needs proof, hates being scared in front of people |
| Voice | Short, sarcastic, confident |
| Relationships | Argues with Alex; thinks Tyler is a try-hard; respects Dane |
| Fear response | Denial first, then anger |
| Moment | Nobody Believes Me: killer revealed to Josh alone, then vanishes. The group doubts him the way he doubted them |

Voice lines: "It's a door. Wind opens doors." · "Show me. Don't tell me, show me." · "I saw someone. I'm not joking. Why is nobody listening?"

### Dane, the Loyal one

| Field | Content |
| --- | --- |
| Personality | Steady, quiet, Alex's closest friend |
| Voice | Plain, protective, few words |
| Relationships | Trusts Alex over everyone; peacekeeper between Josh and Alex |
| Fear response | Protects others before himself |
| Moment | The Promise: finds wounded Alex and carries him back. If Alex dies, Dane is steered to his body |

Voice lines: "Stay by me." · "Alex, what do you want to do?" · "I'm not leaving without him."

### Mara, the Coward

| Field | Content |
| --- | --- |
| Personality | Anxious, observant, wanted to stay home |
| Voice | Nervous, questions, whispers |
| Relationships | Clings to whoever seems safest; distrusts Tyler |
| Fear response | Hide, freeze, then run |
| Moment | The Closet: hides in the mansion next to the killer's room; killer passes within 5 blocks, then vanishes. She must choose to leave |

Voice lines: "Did you hear that?" · "Can we just go back to the car?" · "I'm not moving. I'm staying right here."

### Tyler, the Liar

| Field | Content |
| --- | --- |
| Personality | Charming, self-interested, wants to look capable |
| Voice | Smooth, reassuring, deflects |
| Relationships | Flatters Alex; clashes with Josh |
| Fear response | Protects himself, hides what he knows |
| Moment | The Radio: ensured to find the radio first. He decides whether to call and whether to tell anyone |

Voice lines: "Nothing in there. Checked it already." · "Relax, I've got this." · "I didn't find anything. Why would I lie?"

### Reyes and Hale, police

| Field | Reyes | Hale |
| --- | --- | --- |
| Personality | Calm veteran | Young, jumpy |
| Voice | Procedural, steady | Fast, nervous |
| Moment | Where'd It Go: looks back, killer's body is gone | Takes the shot that drops the killer |

Voice lines: Reyes: "Tell me exactly what happened." · Hale: "Something's moving in the trees."

## Killer

Scripted bot, no LLM, no voice. Acts only when the director allows; walks away from most encounters. Weapon: stone axe (visual only; the three-hit rule decides damage).

| State | Visible | What it does | Exits to |
| --- | --- | --- | --- |
| DORMANT | No | Parked far from campers | SIGN when tension rises |
| SIGN | No | Director creates a sign near a target | STALK or DORMANT |
| STALK | No (invisibility) | Follows at 12-20 blocks, stops when target stops | REVEAL or DORMANT |
| REVEAL | Yes | Visible at treeline or doorway for a few seconds | STRIKE or VANISH |
| STRIKE | Yes | Attacks, only with director permission | DORMANT |
| VANISH | No | Teleports out of sight | DORMANT |

Rules: invulnerable (can be stunned, never hurt); never strikes on a camper's first encounter; most reveals end in VANISH; first reveal is always distant, at the treeline, at night; excluded from the sleep count; original masked skin.

### Killer weaknesses

Same rules for Alex and agents.

| Weakness | Mechanic |
| --- | --- |
| Loses sight | No line of sight ~8 s: searches last known spot, then gives up |
| Burst sprint | 6 s sprint, 4 s walk |
| Doors | 3-4 s to force a door |
| Light | Slowed in torchlight anywhere, incl. the mansion (block light, not sunlight). Breaks torches himself; full speed in the dark |
| Groups | Never attacks 3+ (camp is only safe with 3+ present) |
| Water | Slow or won't swim |
| Stun | Any hit on him: 50% chance of a 3 s stun |
| Chase clock | Gives up after 30-40 s |
| Hit cap | Only attempts the rolled number of hits |
| Reaction | 1-2 s delay, ~2 s swing cooldown |

## Director

Bridge code decides pacing. The killer never decides.

| Rule | Value |
| --- | --- |
| Tension per camper | 0-100, rises with signs, decays over time |
| Kill budget | Up to 4 kills; first no earlier than ~0:30 |
| Min gap between kills | 1 in-game day (20 min) |
| First-encounter lock | No STRIKE on a camper's first REVEAL |
| Isolation bias | Prefer targets alone |
| Moment protection | A camper cannot die before their moment |
| Police lock | After dispatch, no STRIKE on survivors for 5 min |
| Total loss | Allowed within budget and gaps |

Signs escalate: door opened → footsteps → item missing → figure at treeline → body found → chase.

### Showrunner (v2)

Optional LLM layer. Every ~5 min the code director sends state; the showrunner picks from a menu (next beat, target, message wording). Code validates and falls back on a bad or missing answer. Framed as a film story editor. ~24 calls per run. Build after v1 runs end to end.

## Random targeting

| # | Camper |
| --- | --- |
| 1 | Josh |
| 2 | Dane |
| 3 | Mara |
| 4 | Tyler |
| 5 | Alex |

Roll 1 picks the target. Roll 2 picks hits the killer attempts: 1 (60% early, 30% late), 2 (30%, 40%), 3 (10%, 30%). Each hit then gets a dodge check (see Survival actions). Wounded campers are slower and easier targets later. Rolls shown on the watcher.

## Three-hit rule

Every camper has 3 lives; each landed killer hit removes one. The plugin tracks hits and sets health; real damage is cancelled.

| Lives | Hearts | Effect | Agent is told |
| --- | --- | --- | --- |
| 3/3 | Full | Normal | — |
| 2/3 | ~6.5 | Slowness I | You're hurt |
| 1/3 | ~3 | Slowness II, no sprint, blood trail | You're badly hurt, can barely walk |
| 0/3 | — | Dead, body placed | — |

## Survival actions

| Action | Command | Dodge bonus | Risk |
| --- | --- | --- | --- |
| Run to others | !run | +40%; reaching 3+ campers = automatic escape | Running alone at night |
| Hide inside, door shut | !hide | +25% | Trapped if found |
| Stay silent | (no speech) | +10% | Nobody knows |
| Shout for help | !shout | +30% if someone arrives | Reveals position |
| Fight back | !fight | 50%: 3 s stun, free escape; 50%: one extra hit | High |
| Stay in light | (position) | +15%; killer slowed | Killer can break torches |

| Penalty | Dodge change |
| --- | --- |
| Each lost life | -20% |
| Night | -10% |
| Alone | -10% |
| Cap | 80% max |

At REVEAL the agent gets one fast turn (5-8 s grace) to choose; scripts execute it. Commands live in the command docs; personas never mention odds. Alex has no dodge checks, only the killer weaknesses.

## Chases

| Type | Campers | Result |
| --- | --- | --- |
| Run Back | 1-2 | Chased back toward the group |
| Split | 2 | One may be caught, one escapes alone |
| False Chase | 1 | Runs from nothing nobody else saw |

| Time | Chase |
| --- | --- |
| ~0:40 | Run Back after first body |
| ~1:00 | False Chase near the mansion |
| ~1:10 | Split |
| 1:30-1:45 | Run Back to police during backup wait |

Pre-generated panic barks per character (instant, no API cost). Chases last 20-40 s, at least 15 min apart. Killer stuck over 5 s vanishes.

## Wounded Alex and the carry

| Step | Detail |
| --- | --- |
| Chase | Alex + one random camper (Josh, Mara or Tyler; never Dane) |
| Hits | Alex takes 2, left at 1/3 |
| Abandoned | The other camper sprints to the safest spot (largest group, furthest from killer) and decides what to tell them |
| Wounded | Slowness II, no sprint, occasional blindness, blood trail |
| Destination | Alex's live choice: village or mansion |
| Calling out | Alex's proximity chat |
| Bleed-out | ~15 min unfound, Alex dies |
| Carry | Dane finds him (steered by blood and chat), plugin mounts Alex on Dane, scripted pathfind to camp |
| Dane dead | Another camper can find him, or Alex crawls alone against the timer |
| After | Protected while bleeding or carried; back at camp he rejoins the random pool at 1/3 |

## Radio

| Step | Detail |
| --- | --- |
| Placement | Named "Emergency Radio" in a mansion chest, placed only after the first body |
| Hint | Campers told on turn 1 the old manor has an emergency radio, direction only |
| Use | "Dispatch: two units, 15 minutes out." Radio disappears after use |
| 2+ alive | Police reach the caller, backup is 15 more minutes. Others must reach the police and survive; backup extracts those near the police |
| Fallback | No radio by 1:15, or 1 camper left: dispatch with 15 min ETA |
| No survivors | Police arrive and find only bodies |

## Police

| Phase | Behavior |
| --- | --- |
| Spawn | At the trailhead when ETA hits 0 |
| Travel | Scripted pathfind to the caller, teleport-near fallback |
| Contact | Within 10 blocks: LLM on, short procedural dialogue |
| Confrontation | Killer charges; Hale shoots (crossbow); killer falls |
| Vanish | Killer body despawns unseen |
| Exit | Backup extracts survivors at the trailhead |

Invulnerable, never targeted, no memory.

## Acts and timeline

| Time | Act |
| --- | --- |
| 0:00-0:15 | 1. Arrival |
| 0:15-0:30 | 2. Wrongness |
| ~0:30-0:45 | 3. First body |
| 0:45-1:15 | 4. Hunt |
| 1:15-1:30 | 5. Waiting |
| 1:30-2:00 | 6. Rescue and backup |

| Ending | Condition |
| --- | --- |
| Rescue | At least 1 camper alive when police reach them |
| Too late | Last camper dies during the ETA |
| Total loss | All die before dispatch |

## Locations and scenes

| Place | Coords | Use |
| --- | --- | --- |
| Trailhead | 83 88 283 | Spawn, police spawn, extraction |
| Village | 32 ~ 368 | Campsite, ~99 blocks SW of trailhead |
| Mansion | 168 ~ 408 | Radio, killer's room, finale; ~151 SE of trailhead, ~142 E of village |

| Mansion spot | Coords | Note |
| --- | --- | --- |
| Radio chest | 161 104 375 (verified) | ~75 blocks from killer spawn; starts empty |
| Den trophy chest | 228 115 415 (verified) | Dead campers' items moved here |
| Killer spawn | 228 115 411 | DORMANT home; killer_room_exit plays when he leaves |
| Den entrance door | 224 115 398 | Hidden door, hallway to the den |
| Mara's closet | 220 115 406 | Killer's route should pass her door |

| Sign | Text |
| --- | --- |
| Trailhead | Campsite, southwest, 100 blocks |
| Trailhead | Old Manor, southeast, 150 blocks |
| Village east edge | Old Manor, east, 140 blocks |

Agents can't read signs: the bridge sends sign text when a camper is within 5 blocks. Run starts with `/time set 1000` so the first night lands ~0:20.

| # | Scene | Location | Time |
| --- | --- | --- | --- |
| 1 | Arrival | Trailhead | 0:00-0:05 |
| 2 | Walk in | Trail | 0:05-0:10 |
| 3 | The village | Village | 0:10-0:20 |
| 4 | First night | Village | 0:20-0:30 |
| 5 | First body | Near village | 0:30-0:45 |
| 6 | Mansion | Mansion | 0:45-1:15 |
| 7 | Radio call | Mansion | 1:15-1:30 |
| 8 | Police arrive | Trailhead to caller | 1:30-1:45 |
| 9 | Backup | Trailhead | 1:45-2:00 |

## Extra beats

| Beat | How | When |
| --- | --- | --- |
| Lights out | RCON removes torches/lanterns near campers, puts out the campfire; recurs any night | First at ~0:25 |
| Body moves | Armor stand removed or moved; memory keeps both FACTs with times | ~0:50 |
| Killer's room | Hidden mansion room; dead campers' items moved into a chest | In the mansion |
| Storm finale | /weather thunder | 1:30 |
| Tyler accused | Director makes Tyler first to find a body, alone | ~0:30 |

## Bodies and discovery

| Step | How |
| --- | --- |
| Death | Death hook stops the agent permanently; marked dead in Supabase |
| Body | Armor stand at death coords: player head, armor, NoGravity, Invulnerable |
| Loot | keepInventory on |
| Discovery | Camper within 8 blocks gets a directed message naming the body |
| Knowledge | Per-camper known_deaths file, bridge is the only writer |
| Memory | Discovery = FACT; who did it = BELIEF |

## Alex as 5th camper

| Topic | Rule |
| --- | --- |
| Role | Victim, can die |
| Knowledge | In character; never mentions a killer before a body is found |
| Speech | Proximity chat; each open line can cost up to 4 agent calls |
| Leaving mid-run | Logging off = went missing; director places his body |
| Death | Spectator, keeps filming via /spectate |

## Cameras

| Use | Tool |
| --- | --- |
| Live monitor | prismarine-viewer per agent (render_bot_view), OBS grid; ignores darkness |
| Alex POV | OBS on his client |
| Movie shots | Replay Mod on Alex's client (only within his render distance) |
| After Alex dies | /spectate survivors |

## Voice and proximity

| Path | Rule |
| --- | --- |
| Alex typing | Paper plugin removes chat viewers beyond ~24 blocks |
| Agent conversations | Runtime distance check; beyond ~24 blocks: too far to hear |
| Radio | Only way past shouting distance |

| Voice rule | Detail |
| --- | --- |
| Model | ElevenLabs Flash |
| Queue | One speaker at a time |
| Stale lines | Dropped from live playback after ~10 s, still saved |
| Archive | Every line saved with timestamp, speaker, position |
| Volume | By distance to Alex; after death, to the spectated player |
| Rights | Paid plan if the movie is monetized |

## Assets

| Actor | Skin | Voice |
| --- | --- | --- |
| Josh | To pick | Confident, sarcastic male |
| Dane | To pick | Calm, low, steady male |
| Mara | To pick | Soft, anxious female |
| Tyler | To pick | Smooth, charming male |
| Reyes | To pick | Older, procedural |
| Hale | To pick | Young, fast, nervous |
| Killer | Original masked design | None |
| Alex | Own | Typed |

Skins as .png for SkinsRestorer; voices must allow commercial use; test each voice with a scream and a whisper.

## World settings

```
difficulty easy
gamerule naturalRegeneration false
gamerule keepInventory true
gamerule playersSleepingPercentage 0
gamerule doImmediateRespawn true
```

Easy with spawn-monsters=false in server.properties (no monsters, no auto-heal). Hunger drains: food in chests and starter bread in inventories. Plugin still cancels health regain for campers and blocks sprint at 1/3 as a safety net.

## Failsafes

| Risk | Failsafe |
| --- | --- |
| Model refuses violent roleplay, writes it to memory | Summariser framed as data processing; filter refusal lines; kills off-screen |
| Runaway cost | Provider spend cap before every run; log cache reads |
| Mindcraft auto-respawn | Death hook stops the agent |
| Campers wander off-map | World border plus travel cap |
| Police stuck | Teleport-near fallback |
| API outage in memory | Clean memory files before restart |
| Nobody finds the radio | Fallback dispatch at 1:15 or 1 camper left |
| TTS lag | Stale lines dropped from live playback |

## Open

- [ ] Verify seed on Chunkbase for 1.21.6
- [ ] Skins and voice IDs
- [ ] Place signs
