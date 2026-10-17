
![BBC-title-banner](img/BBC-title-banner.jpg)

**Boss Bar Messages** shows announcements in the boss bar at the top of the screen. You write your messages in *config.yml*, and the plugin cycles through them on its own. The bar fills up while the message is on screen and disappears when it's done. You can also show a message right away with a command, for example before a restart.


![BBC-section-banner-features](img/BBC-section-banner-features.jpg)

- Different messages per biome, world or dimension, e.g. one list for the Nether and another for spawn.
- Messages rotate on their own. You set the order, the gap between them and whether the list loops.
- Hex colors (&#RRGGBB) and classic & codes, also mixed in one message.
- Optional sound for each message, plus all 7 bar colors and 5 styles.
- Show a message right away with /bossbar play, including one-off messages that aren't in the config.
- Every plugin message can be translated. English and Polish are included, and missing keys fall back to English.
- /bossbar reload applies config changes without a restart.


![BBC-section-banner-commands](img/BBC-section-banner-commands.jpg)

/bossbar play <id></id> - Show a predefined boss bar from config.yml to all online players
/bossbar play custom  <color></color> <time></time> "<message></message>" [sound] - Show a one-time custom boss bar
/bossbar list - List predefined boss bars and auto-broadcast status
/bossbar reload - Reload config.yml and the language file
/bossbar clear - Remove all currently visible boss bars (rotations keep running)
/bossbar help - Show command help


![BBC-section-banner-config](img/BBC-section-banner-config.jpg)

**Example plugin configuration file**

```YAML
# Boss Bar Messages configuration

# Prefix for plugin messages (supports & color codes).
prefix: "&6[BossBar] &r"

# Language file from langs/<code>.yml (bundled: en, pl).
lang: en

# Automatic rotation of bossbars.
# Players get the rotation of their region, matched by: biome > world name > environment
# (overworld, nether, the_end). Players with no matching enabled region get 'default'.
#   enabled  - false: region is ignored and players fall through to the next match
#   gap      - pause between bossbars (s/m/h, 0s = none)
#   loop     - start over after the last bossbar
#   bossbars - ids from the 'bossbars' section; empty or missing = no bossbars in this region
auto-broadcast:
  enabled: true

  default:
    enabled: true
    gap: 30s
    loop: true
    bossbars:
     - welcome
      - rules
      - discord

  regions:
    overworld:
      enabled: false
      gap: 1m
      loop: true
      bossbars:
       - welcome
        - rules

    nether:
      enabled: false
      gap: 30s
      loop: true
      bossbars:
       - nether_warning

    plains:
      enabled: false
      gap: 45s
      loop: true
      bossbars:
       - plains_event

# Bossbar definitions, used by rotations and /bossbar play <id>.
# Colors: blue, green, pink, purple, red, white, yellow
# Styles: solid, segmented_6, segmented_10, segmented_12, segmented_20
# Message supports & codes and &#RRGGBB hex colors. Sound is optional.
bossbars:
  - id: welcome
    message: "&aWelcome to the server!"
    color: green
    style: solid
    duration: 10s
    sound: minecraft:block.note_block.pling

  - id: rules
    message: "&eRead the rules: /rules"
    color: yellow
    style: segmented_10
    duration: 8s

  - id: discord
    message: "&9Join our Discord: /discord"
    color: blue
    style: solid
    duration: 8s

  - id: nether_warning
    message: "&cWatch out for ghasts and lava!"
    color: red
    style: solid
    duration: 10s

  - id: plains_event
    message: "&aAn event is happening on the plains!"
    color: green
    style: segmented_10
    duration: 12s
```

**Example language configuration file**

```YAML
# Boss Bar Messages - English. Color codes use &, placeholders use {braces}.

# ---------- Errors ----------
no-permission: "&cYou don't have permission to use this command."
unknown-style: "&cUnknown style: '{value}'. &7Available: {options}"
unknown-color: "&cUnknown color: '{value}'. &7Available: {options}"
invalid-time: "&c{reason}"
message-needs-quotes: "&cMessage must be wrapped in double quotes."
message-needs-quotes-example: "&7Example: /bossbar play custom solid red 5s \"Hello!\" minecraft:ambient.cave"
bossbar-not-found: "&cBossbar with id '{id}' not found."
reload-error: "&cConfig reload failed: {reason}"

# ---------- Success / info ----------
ad-hoc-displayed: "&aDisplayed bossbar for {duration}{soundInfo}."
sound-info: " &7(sound: {sound})"
play-displayed: "&aPlaying '{id}' ({duration})."
reload-success: "&aConfig reloaded. Loaded {count} bossbar(s)."
clear-success: "&aCleared all currently visible bossbars."

# ---------- /bossbar list ----------
list-empty: "&eNo bossbars defined in config.yml."
list-header: "&eDefined bossbars ({count}):"
list-entry: "&7 \u2022 &f{id} &7- {color} / {style} / {duration}{soundInfo}"
list-footer-auto-on: "&7Auto-broadcast: &aON"
list-footer-auto-off: "&7Auto-broadcast: &cOFF"
list-footer-regions: "&7Active rotations: &f{count}"

# ---------- /bossbar (usage) ----------
usage-header: "&eCommands:"
usage-play-custom: "&f/bossbar play custom <style> <color> <time> \"<message>\" [sound] &7- one-shot bar"
usage-play-id: "&f/bossbar play <id> &7- run a predefined bossbar from config"
usage-list: "&f/bossbar list &7- list defined bossbars"
usage-reload: "&f/bossbar reload &7- reload config"
usage-clear: "&f/bossbar clear &7- clear all currently visible bossbars"
usage-styles: "&7Styles: {options}"
usage-colors: "&7Colors: {options}"
```
