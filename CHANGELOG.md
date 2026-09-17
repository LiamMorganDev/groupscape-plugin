# Changelog

All notable changes to the GroupScape plugin are logged here, newest first.

## [1.8.34] - 2026-09-17

### Added
- Group Chat's unread dot on the side panel's Chat tab now stays in sync with your other devices - read a message on the website or another RuneLite client, and the dot clears here too, live.

## [1.8.33] - 2026-09-17

### Fixed
- Group Chat messages in the side panel Chat tab no longer show with huge empty gaps between the sender's swatch, name, and message text.
- `!gs` now captures and relays your message no matter which chat tab you're typing in (All, Public, Clan, Private, Trade) - previously it only worked from Public/Clan and could leak into real chat from other tabs.

## [1.8.32] - 2026-09-17

### Added
- Group Chat: a new "Chat" tab next to the Roster tab in the side panel lets you see and send messages with your group. Messages sent to Public or Clan chat with a `!gs` prefix are captured and relayed to your group instead of going out as normal chat.

## [1.8.31] - 2026-09-17

### Fixed
- A Barrows run could undercount how many brothers you killed in the activity feed (e.g. showing 3 of 6) - kill detection now reads the game's own "brothers killed" flags instead of guessing from despawns, which was silently missing some brothers.

## [1.8.30] - 2026-09-17

### Fixed
- A boss Slayer task (Duke Sucellus, Barrows Brothers, and similar) could get permanently stuck showing as "Unknown task" in the group panel/side panel instead of at least "Boss" - a follow-up to the earlier fix for the same symptom, covering a case that fix missed.
- Cancelling a task after only a few kills could show up in the site's History tab as "Completed" at the full kill count instead of "Cancelled", hiding the point cost - the same timing gap between the game clearing your task and resetting its kill counter as the recent block-related fix, just untriggered for cancelling.

## [1.8.29] - 2026-09-16

### Fixed
- A task finished down to its kill limit and then blocked before being turned in was showing up as "Completed" in the site's History tab instead of "Blocked", with the block fee hidden.
- Blocking your current task could occasionally leave the site's History tab showing it as "Completed" with a fake max kill count, even after only a few kills - a timing gap between the game clearing your task and resetting its kill counter could let a stale reading slip through right before the block was recorded.

## [1.8.28] - 2026-09-14

### Fixed
- Hueycoatl kills were badly undercounted in the site's Loot Log - most kills were only being detected as the boss's tail breaking mid-fight (no loot), while the actual kill went unlogged. Kills are now detected correctly and their loot attached.

## [1.8.27] - 2026-09-12

### Fixed
- Blocking or cancelling a task right after finishing its kill count, but before turning it in, was showing up as a normal "Completed" task in the site's History tab, with the block/cancel fee looking like that task's own reward. It's now correctly labeled Blocked/Cancelled.
- Blocking a task type from the Slayer Reward Shop's list wasn't tracked at all - its point cost could silently attach to whatever task happened to close next in the site's History tab. It now shows up as its own entry.
- Notable drop chat notifications weren't reaching anyone - a mismatched field name meant the drop info never made it to the site, so the "player got X" chat message never fired for you or your group.
- A fully-killed task could still get mislabeled Blocked/Cancelled in the site's History tab if you blocked or cancelled a *different* task right afterward - the Reward Shop point spend was being misread as that finished task's own fee. Completed tasks are now always recorded as Completed.
- The Slayer Reward Shop's block-confirmation message ("...block X as a possible slayer task?") wasn't being recognized when it included the word "slayer", so some block purchases went untracked.

## [1.8.26] - 2026-09-12

### Added
- Mortimer's task modifiers (bonus points, task size up/down, clue rate, superior unique rate, Slayer XP) are now sent to the site's current task panel and History tab.

## [1.8.25] - 2026-09-11

### Fixed
- Skipping a task from a higher-level master (Vannaka, Nieve, Duradel, ...) via Turael/Aya/Spria was still showing up as "Superseded" in the site's History tab instead of "Reset" - the plugin was only checking who assigned the task being skipped, not who granted the free skip.
- A task's close (finished, skipped, cancelled, or blocked) could get lost entirely if the client or plugin restarted at just the wrong moment, permanently stranding that row as "Superseded". The plugin now remembers an in-progress task across a restart so it can still close it out correctly.

## [1.8.24] - 2026-09-11

### Fixed
- Fixed a brief stutter that happened every time a slayer task started or finished - the plugin was redundantly redoing its task lookup several times in the same instant instead of once.

## [1.8.23] - 2026-09-10

### Fixed
- A finished slayer task could get stuck showing 0 kills and "In progress" in the site's History tab, even after it was turned in for a new task - the plugin could lose track of a task's final kill count right as it closed out.

## [1.8.22] - 2026-09-10

### Changed
- Internal: fixed a test that was checking the loot grace window against an outdated timing, so it no longer fails builds.

### Fixed
- A completed slayer task's points reward wasn't showing up in the site's History tab - the plugin was comparing your points total from the wrong moment (after the reward had already landed), so it always looked like 0 gained.
- A free task skip from Turael, Aya, or Spria was showing up as "Unknown" in the site's History tab. Now correctly shows as "Reset".

## [1.8.21] - 2026-09-08

### Fixed
- Loot from bosses with long death animations (like Duke Sucellus) could fail to reach the site's loot log if the drop arrived a bit late - the plugin now waits a little longer before giving up on matching it to the kill.

## [1.8.20] - 2026-09-08

### Fixed
- Active prayer icons were still doubling up (Eagle Eye/Rigour/Hawk Eye/Sharp Eye alongside Deadeye, or Mystic Might/Mystic Lore/Mystic Will alongside Mystic Vigour) in the in-game overlay and the client's own side panel, which had no filtering at all - all three surfaces (also the website's side panel) now share the same suppression logic.
- Slayer task history and stats on the site were staying empty - the plugin was sending task events under the wrong upload key, so the server was silently dropping them.

### Changed
- Internal: commits now require a CHANGELOG.md entry - a pre-commit hook rejects a commit that doesn't stage one.

## [1.8.19] - 2026-09-08

### Added
- Slayer tasks now build a history on the site (each task's master, points earned/spent, kills, and how it ended - completed, cancelled, or blocked) plus an all-time stats summary, both viewable from new tabs on the Slayer panel.

### Fixed
- Active prayer icons no longer show both Eagle Eye and Deadeye (or both Mystic Might and Mystic Vigour) at once - only the upgraded prayer you actually have unlocked shows now.

## [1.8.18] - 2026-09-07

### Added
- Opening the Barrows chest or the Moons of Peril reward chest now logs a single kill entry naming exactly which brothers/moons you defeated that run, e.g. "Barrows (Dharok, Verac, Torag)" or "Moons of Peril (Eclipse, Blood)" - Moons of Peril chest openings weren't logged at all before this.

### Changed
- Individual Barrows brother and Moon kills no longer show up on their own in the activity feed, toast notifications, or Discord kill webhook - only the combined chest-opening entry does now.

### Fixed
- Moons of Peril reward chest openings were still being silently dropped in some runs - the moons don't reliably die the normal way (they finish through an "Enraged" transform partway through), so the combined entry now logs on chest opening regardless, even without a full per-moon breakdown.

## [1.8.17] - 2026-09-06

### Added
- Opening a Hunters' loot sack from Hunters' rumours (any tier) now shows up in the group's loot log.

## [1.8.16] - 2026-09-05

### Added
- Tombs of Amascut raid completions now report your actual invocation/raid level instead of always showing it as unknown.

### Fixed
- Opening your collection log to browse it no longer gets treated the same as actually unlocking a new item, so it won't show up as a fresh drop notification - only items you actually just received are reported that way.
- Boss kills with a slower death animation (e.g. Vardorvis) no longer occasionally get reported without their loot attached - the plugin now waits briefly for the loot to arrive before finalizing the kill.
- Your target no longer flickers off and back on in the party overlay/side panel during brief gaps in combat (e.g. a hit landing mid-fight); it now clears properly a few seconds after you actually stop interacting.
- Fixed the party overlay's health orb pips getting clipped at the top and bottom edges of the panel.

## [1.8.15] - 2026-09-05

### Fixed
- "Offline members last" was being ignored on both the party overlay and side panel when the sort order was set to Join order (the default), so offline members no longer sorted to the bottom in that mode.

### Added
- New sort order option, Join order (reversed), for both the party overlay and side panel.

## [1.8.14] - 2026-09-04

### Changed
- Raid markers are now on by default (this one-time change also applies to existing installs, unless you'd already switched it off yourself).
- The party overlay no longer hides or fades group members out of vicinity by default, and now sorts offline members to the bottom by default (also applied once to existing installs).
- Member online/offline, low HP, and boss kill notifications are now on by default (also applied once to existing installs).

## [1.8.13] - 2026-09-04

### Fixed
- Slayer master no longer disappears from the side panel for tasks assigned by Mortimer or Krystilia when the game client (or the plugin) restarts mid-task, since the master name previously had to wait for you to talk to them again before it would reappear.

## [1.8.12] - 2026-09-04

### Fixed
- Slayer task progress now accounts for Mortimer's bonus/reduced kill count modifiers (e.g. "+65 kills") in the total shown, instead of only tracking the original assigned amount.

## [1.8.11] - 2026-09-04

### Fixed
- Some collection log unlocks (notably boss uniques, e.g. Awakener's orb from Leviathan) could still fail to sync to the Activity Feed until you next opened your Collection Log, even after the previous inventory-diff fix - the plugin now also cross-checks your kill loot directly as a second, independent way to identify the unlocked item.

## [1.8.10] - 2026-09-03

### Added
- New party overlay Layout option: Orb Grid, which shows each group member as a circular HP orb (with Prayer/Run/Spec pips and a target indicator) tiled in a grid instead of a list of bars. The panel now sizes itself to however many members you have instead of always taking up a fixed width.
- New party overlay Layout option: Scoreboard, which lays members out as side-by-side vertical HP meters (like a raid frame turned sideways) instead of a list - handy for a wide, short strip along the top of the screen during boss fights.

### Changed
- The party overlay's "Scale" setting is now called "Layout", since most of its options change the whole arrangement, not just the size. Your existing choice is preserved.
- Orb Grid: bolder, better-spaced Prayer/Run/Spec pips around each orb.
- Scoreboard: sized up about a third, with a larger, bolder name label.

### Fixed
- A newly-assigned slayer boss task (Leviathan, Whisperer, Vardorvis, Duke Sucellus, etc.) could get permanently stuck showing as "Unknown task" in the group panel/side panel instead of the boss's name.
- Orb Grid's Prayer/Run/Spec pips no longer spill outside the panel's bottom edge - padding and row spacing now account for how far the bigger pips hang off each orb.
- Scoreboard's member initials now actually render bold - the font-weight flag alone wasn't visibly bolding the game's pixel font.
- New collection log items unlocked in the middle of a session (especially untradeable ones) could sometimes fail to sync to the Activity Feed until you next opened your Collection Log - the plugin now identifies the unlocked item directly from your inventory instead of relying on a Grand Exchange price lookup that doesn't cover every item.
- Party overlay text (member names, bar labels/values) no longer overlaps itself in Compact and Super Compact scale - font size and text position now scale properly with the tighter row heights instead of overflowing them.
- Talking to Mortimer, Aya, Achtryn, or Kuradal no longer fails to update your tracked slayer master - the plugin wasn't recognizing them as slayer masters yet.

## [1.8.9] - 2026-09-03

### Added
- Notable drop pings sent to Discord now show the item's icon.

### Fixed
- Drops received as a "noted" stack (e.g. a bulk pile of gems) no longer show up in the Activity Feed and Discord as an unrecognized "item #____ (untradeable)" - they now show the correct item name, link, and gp value just like the unnoted version.

## [1.8.8] - 2026-09-03

### Fixed
- Deaths to bosses (e.g. Leviathan) that hit you with a ranged/special attack you weren't actively fighting back against now correctly show "died to <boss>" instead of just "died" - killer attribution now uses whichever NPC last landed a hit on you, not just whoever you were targeting.
- Checking your slayer task (helm, enchanted gem, black mask, or with your slayer master) could sometimes show "null" for the task name on the site while the kill count stayed correct - the task name and location no longer get wiped out by a momentary lookup hiccup.

## [1.8.7] - 2026-09-03

### Fixed
- Combat achievement progress no longer waits up to a minute (or a relog) to sync to the site - it now updates the moment you complete a task or tier, and also refreshes whenever you open your Combat Achievements log.
- Your slayer task's assigning master no longer gets stuck showing as unknown after a client restart mid-task - talking to (or rechecking with) a slayer master now refreshes it right away.
- Boss slayer tasks (Leviathan, Whisperer, Vardorvis, Duke Sucellus) now show the actual boss name to your group instead of just "Boss".
- Collection log items whose name contains an apostrophe (e.g. Beekeeper's gloves) could fail to sync the moment you got them and only show up after you next opened your collection log - fixed a mismatched apostrophe character breaking the item name lookup.

## [1.8.6] - 2026-09-02

### Added
- Your current slayer task, master, points, and streak are now reported to your group, so the website can show them in each member's side panel.

### Fixed
- Fixed the Corrupted/Crystalline Hunllef never showing up as a kill in the Activity Feed, and the Gauntlet reward chest loot never showing in the Loot Log - the Hunllef's health bar never reliably signals its death, so the kill is now detected from the "Gauntlet completion count" chat message instead, and correctly paired with the reward chest's loot whenever you get around to opening it.
- Fixed your own row in the party overlay always showing a gold accent bar instead of your assigned helmet colour, even though every other group member's row already showed theirs correctly.
- Fixed Tombs of Amascut (and occasionally Chambers of Xeric) completions sometimes not appearing in the Activity Feed at all - the reward chest loot is now enough on its own to log the completion, instead of requiring an in-game chat message to line up with it first.
- Internal: confirmed raid completion chat detection wording against real completion messages and removed a stale internal note about it being unverified.
- Fixed collection log unlocks not syncing to your group until you next opened your collection log - new items now sync as soon as you get them.
- Fixed deaths (and any kill/loot alongside them) sometimes not appearing in the Activity Feed, toasts, or Discord at all - a too-strict check meant to filter out spurious death animations could also misfire on real deaths.

[1.8.5] — Raid Completions To Activity Feed, Kill Count Fixes

## [1.8.4] - 2026-08-31

### Added
- Raid markers now include four numbered (1-4) and four lettered (A-D) generic callouts, alongside Danger/Defend/Loot/Focus.
- You can hide raid marker types you don't use from your own "Raid Markers" menu, in three new settings sections - this only tidies your menu, it doesn't hide other members' markers.

### Changed
- Renamed the "Safe Spot" raid marker to "Defend".
- Chat messages from GroupScape (group notifications, pings, kill/loot lines) are now prefixed with "[gs]" and shown in purple, so they're easier to spot in a busy chat window.
- "Ping" and "Raid Markers" now only show up in the right-click menu when you shift-right-click, so a plain right-click stays uncluttered.
- Raid markers in the game world are now semi-transparent, so they don't fully block your view of what's underneath them.

### Removed
- The hold-to-ping hotkey (Z by default) is gone - use shift-right-click instead.

### Fixed
- Boss kills are detected more reliably - some deaths were missed when the health bar hid itself before the kill could be confirmed.
- Shift-right-click now actually shows the "Ping" and "Raid Markers" menu entries - they were silently never appearing due to a wrong key check.
- The "Raid Markers" submenu now lists top to bottom in the order Danger/Defend/Loot/Focus, then A-D, then 1-4 - it was showing bottom to top before.
- Fixed a false "died" report showing up on the group's activity feed when you hadn't actually died.

## [1.8.3] - 2026-08-31

### Changed
- Ping and raid marker beams (the optional vertical line under a marker) are no longer a separate setting - a tile ping/marker now always shows its beam, and an NPC ping/marker never does.

## [1.8.2] - 2026-08-31

### Fixed
- Fixed the hold-to-ping hotkey still not working - it now cancels the walk/attack the same way RuneLite's own Party plugin does, instead of a method that never actually suppressed it.

## [1.8.1] - 2026-08-31

### Fixed
- Loot log now tracks kills from every monster, not just a short list of bosses - previously it silently ignored loot from anything not on that list, which was most normal play.
- Fixed a timing issue where loot could sometimes go untracked even for a monster it was watching, if the loot dropped a moment before the kill was detected.

## [1.8.0] - 2026-08-31

### Added
- New "Raid Markers" feature (off by default - enable it in the settings). Right-click a tile or NPC to drop a Danger, Safe Spot, Loot, or Focus/Kill Target marker for your group to see - each shows its own icon and stays up until you clear/redrop it or the target dies, no timer. You can have one of each type active at a time, on a tile and on an NPC.
- Pings and raid markers can now optionally show a vertical beam under the marker (off by default), similar to the Ground Items plugin's loot beam - toggle "Show ping beams" and "Show raid marker beams" separately in the settings.

## [1.7.11] - 2026-08-31

### Fixed
- Fixed the hold-to-ping hotkey not working at all - clicking while holding the key could cause the game to briefly steal keyboard focus, making the plugin think the key had already been released.
- Fixed "Clear ping" (and pings ending on their own) silently failing every time.

### Changed
- You can now have one pinged monster and one pinged tile active at the same time - pinging a new tile no longer clears an in-progress monster ping, and vice versa. Right-click now shows separate "Clear NPC ping" and "Clear tile ping" options when applicable.

## [1.7.10] - 2026-08-31

### Fixed
- Fixed a pinged monster's marker not showing at all - a wrong parameter to a game math function meant the floating arrow and beam kept silently failing to draw the whole time a ping was active.

## [1.7.9] - 2026-08-31

### Fixed
- A pinged monster now shows an outline around its actual tile instead of its 3D model, and its floating arrow marker shows reliably above it.
- The hold-Z ping shortcut and the right-click "Ping"/"Clear ping" options are now more reliable, and won't silently fail without at least logging what went wrong.

## [1.7.8] - 2026-08-31

### Added
- You can now ping a spot or a monster for your group to see - right-click an NPC or a tile (in the game view or the world map) and choose "Ping", or hold Z and left-click in the game view for a quicker shortcut. Pings show as a colored arrow marker on the minimap, world map, and the group's website map, and a pinged monster's marker follows it around live. Pings clear themselves after about a minute, or sooner if the monster dies or you choose "Clear ping" from any right-click menu.

## [1.7.7] - 2026-08-31

### Fixed
- Fixed group members intermittently disappearing from the party overlay and side panel (showing as offline) even though they were online — the "offline" cutoff was set equal to the idle heartbeat interval, leaving no room for normal network delay.

## [1.7.6] - 2026-08-31

### Added
- Group members now show up on the world map and minimap while they're in your world and on your plane, marked with the same colored helm icon as the party overlay. On the world map, a member who's off-screen shows as an arrow at the map edge you can click to jump straight to them - just like clue and quest markers.
- Added a "Map markers" toggle to turn this off.

## [1.7.5] - 2026-08-31

### Changed
- Internal: fixed how your location data gets saved so it stays consistent between sessions.

### Removed
- Removed the "Pause sync" setting; use the plugin toggle if you want to stop sending data.

## [1.7.4] - 2026-08-30

### Changed
- Tightened up spacing in the side panel's inventory, equipment, and skills tabs so more fits without feeling cramped, and centered the equipment doll within the panel.
- Fixed the roster list sometimes getting clipped on the right edge instead of matching the padding on the left.

## [1.7.3] - 2026-08-29

### Fixed
- Fixed you sometimes staying stuck showing "offline" to your group after standing idle for a while, even though the plugin was still running - it now sends a periodic check-in even when nothing's changed.

## [1.7.2] - 2026-08-28

### Removed
- Removed farming patch and bird house timers tracking. It relied on reflecting into RuneLite's internal Time Tracking classes, which isn't something we can keep doing reliably.

## [1.7.1] - 2026-08-28

### Fixed
- The party overlay no longer gets stuck disconnected after the website has a brief outage or maintenance — it now notices and reconnects on its own instead of requiring you to restart RuneLite.
- Fixed another case of a member card's vitals bar briefly flashing over the inventory grid when a bag item changed.
