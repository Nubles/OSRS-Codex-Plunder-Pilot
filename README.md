# Codex Plunder Pilot

**Every Pyramid Plunder run, planned for your Thieving level.**

Which rooms to walk straight through, which urns are worth the clock, when the grand gold chest and the sarcophagus pay, and your odds of the Pharaoh's sceptre. During a run it replans every tick against the five-minute clock, says the one thing to do next, and outlines it in the room.

Part of the **Codex** suite of RuneLite plugins. They share one look: warm ink, aged gold and the game's own fonts.

<p align="center">
  <img src="docs/screenshots/pilot.png" width="225" alt="Pilot: the route and the ready check">
  <img src="docs/screenshots/odds.png" width="225" alt="Odds: your sceptre luck">
  <img src="docs/screenshots/runs.png" width="225" alt="Runs: the pyramid, sessions and splits">
</p>

## What it does

**Pilot.** Pick what you are running for: **Experience** or the **Sceptre**. The route lists every room your level opens, what to do in each (walk through, so many urns, the chest, the sarcophagus) and what that pays, with the sceptre odds beside every chest and sarcophagus worth opening.

**Ready check.** Before you go in: enough free slots for the artefacts a run turns up, low hitpoints, whether a snake charm or lockpick helps at your level, Protect from Melee against the swarm and mummies, and your sceptre's charges.

**During a run.** The crest becomes the clock. The next-step card says exactly what to do: pass the trap, search 9 urns, open the chest, find the door. The plan is redone every tick from what is left, so a slow room shortens what follows instead of costing you the deepest room's urns. In the room, the urns worth searching are outlined and numbered in the shortest walk; the chest, sarcophagus, trap and untried doors light up when each is next. When a swarm or mummy is on you, or your pack is full, the next step says what to do first.

<p align="center">
  <img src="docs/screenshots/in-run.png" width="225" alt="During a run">
  <img src="docs/screenshots/plaque.png" width="246" alt="The run plaque">
</p>

**Odds.** Every grand gold chest and sarcophagus you open is counted by room. The page shows how many sceptres you should have had, how many you did, and how lucky or dry that is. For your plan it gives the chance each run, the runs to even odds and to nine in ten, and the date those land at your pace.

**Runs.** Your experience an hour over your last runs, drawn as the pyramid itself with your level and any goal levels up its side. Under it: your session with a bar for every run, your past sessions against your best, splits for each room against your quickest, and the artefacts waiting in your bank with what they fetch. **Copy pyramid card** copies a picture of it to paste into a chat.

<p align="center">
  <img src="docs/screenshots/pyramid-card.png" width="330" alt="The pyramid card">
</p>

**Time lost.** Each room on the way down, timed without its searching: the trap, the walk and finding the door. Your usual time over your last 20 runs is set against your quickest, and the gap is priced in what it costs you: the experience an hour those seconds would earn searching deeper. The room that gives the most away is lit, so you know exactly where to tighten up.

<p align="center">
  <img src="docs/screenshots/time-lost.png" width="242" alt="Time lost: where your clock goes">
</p>

**Chat.** When a run ends, a line with the room reached, the experience, the artefacts and the sceptre odds it carried, and the room that ran slowest against your quickest. When a new level opens a deeper room, a line says so and what a run pays there now.

## Settings that matter

- **Highlight the next step** (on): the outlines and urn numbers in the room.
- **Run plaque** and **Ready plaque** (both on): the plaque during a run, and the ready check's most urgent point before one.
- **Swarm and mummy warning** and **Full pack warning** (both on): turn either off and the next step keeps going instead of putting that warning first.
- **Run summary in chat** and **Room openings in chat** (both on).
- **Goal level** (99): the Thieving level the Runs page counts toward.
- **Name on cards** (on): turn off to share the pyramid card without your character's name.

## With other Codex plugins

Plunder Pilot works on its own. With other Codex plugins installed, it reads what they save and never changes it:

- **[Codex Navigator](https://github.com/Nubles/OSRS-Codex-Navigator):** every unfinished goal that needs more Thieving is listed on the Runs page, with the runs, play time and date it takes at your pace.
- **[Codex Bank Steward](https://github.com/Nubles/OSRS-Codex-Bank-Steward):** its saved bank, when newer than the Pilot's own look, counts your banked artefacts and finds a sceptre in your bank before you open it.
- **[Codex Gear Roadmap](https://github.com/Nubles/OSRS-Codex-Gear-Roadmap):** without Bank Steward, its saved gear finds a banked sceptre (but not a freshly found one, which it does not track).

## How it plans

The figures are the game's, as the Old School wiki records them: experience for each room's urns, chest and door, the Strength experience from each sarcophagus, and the sceptre rate per chest or sarcophagus (1 in 4,200 in room 1 down to 1 in 650 in rooms 7 and 8).

Getting through each room costs a fixed time. What is left of the five minutes goes to the urns, chests and sarcophagi your goal values most per tick:

- **Experience** opens the chests from room 4 on first, then searches urns from the best-paying room down while the clock allows.
- **Sceptre** opens every chest and the sarcophagi from room 4 on, where their odds are best, before any urn.

The plan starts from typical times and learns your own from your runs, including how long an urn takes you in each room. A room's urns can take you longer than another's, from failed searches or a longer walk between them; once the Pilot has timed enough of them it moves searches to where they pay more per second, and says so on the Runs page. Everything is saved per account in your RuneLite profile. A save damaged on disk keeps all it can: one unreadable run or setting is lost alone. Before a damaged save, or one a newer version wrote, is first saved over, a copy of it as found is kept aside in your profile.

## Rules posture

Codex Plunder Pilot only reads what the game already shows you: your skills, pack and bank, the room, the clock, which urns, chests and doors are open, and whether a swarm or mummy is after you. It outlines objects and draws its own panel and plaque. It does not click, change menus, or send anything over the network; you do every click.

## Development

```bash
./gradlew build   # compile and run tests
./gradlew run     # launch RuneLite with the plugin loaded
```

The planner, odds and run tracking (`core/`) are plain Java with unit tests. The panel and overlays are in `ui/` and `overlay/`.

## Licence

BSD 2-Clause, see [LICENSE](LICENSE).
