
<div align="center">

### **###################################**
### **YOU ARE ON THE FORK MADE BY ARONA74**

This fork was made to provide lastest features and bug fixes of Factions Mod to MC 1.20.1 version.
 
Please note that no support will be provided by original Factions Mod dev team, this fork is provided as is.

[You can post Issues here](https://github.com/Arona74/factions/issues).
</div>

**FEATURES NOT INCLUDED**
- Translations & GUI
- Prevent Explosions in claims

**CHANGES DONE BY ME**
- Additional relationship level (FRIENDLY)
- Compat API for my Simplyskills/Simplyswords forks (done for "Conquest of the Bleak Isles" server, new entries in config to set relationship level for friendly fire)
- Home marker on Bluemap use HTMLmarker instead of POImarker
- Block/mob filter lists (blacklist & whitelist) with pattern support:
  - `@modid` — matches all blocks/mobs from a mod (e.g. `@comforts`)
  - `namespace:prefix*` — wildcard suffix match (e.g. `comforts:sleeping_bag*`)
  - `namespace:exact` — exact id match
- `inventoryBlocks` list: designate any third-party block as an inventory for permission purposes (`USE_INVENTORIES` instead of `USE_BLOCKS`). Built-in support for Numismatic Overhaul (`numismatic-overhaul:piggy_bank`) and Tom's Simple Storage (`toms_storage:ts.storage_terminal`, `toms_storage:ts.crafting_terminal`). Supports the same `@modid`/`prefix*`/exact patterns as filter lists.
- CarryOn mod compat: block and entity placement (putting down a carried block/mob) is blocked by claim protection; entity placement can be toggled via `carryOnEntityPlacement` config option
- Faction safe (ender chest replacement) is toggleable per config
- Restricted wilderness mode with per-dimension control and configurable permissions
- Vassal system: factions can become vassals of others, contributing a % of their power to the overlord
- Wealth power: factions can sacrifice items to gain bonus power up to a configurable cap
- War & fame power pools with kill rewards and decay
- God blessings system: factions pray to gods for timed potion effects at a power cost
- Inactivity tiers: power multiplier decay based on days since last member login
- Claim decay: auto-unclaim chunks when faction power drops below threshold
- Unclaim cooldown: prevent immediate re-claiming of recently unclaimed chunks
- Territory entry/exit notifications via chat, action bar, or title
- Dynamic tab menu and chat formatting

**CHANGES COMING FROM 2.9.0 original Factions**
- Fix: ickerio#101
- Feat: Bluemap integration
- Fix: Auto run audit 4 times ickerio#102
- Fix: Add admin power when calculating max power ickerio#103
- Fix: claim remove all bug
- Fix: permissions api glitch
- Fix: Add colorless faction name placeholder
- Change: Increase log message verbosity
- Fix: Guest permissions
- Feat: Add additional power per ally
- Feat: Add squaremap integration
- Fix: null entity in onUseEntity
- Fix: ickerio#120 (even out map)
- Change: Reduce commander permissions
- Feat: Added the ability to set a wait time between each home warp
- Feat: Set cooldown after warping and add time remaining to fail message
- Fix: invite command not reporting failure
- Fix: Properly send invite remove fail
- Fix: Properly check admin power when auto claiming
- Fix: modify and create command not restricting name length correctly
- Fix: bug in kick command
- Feat: Group claim chunks in bluemap/dynmap/squaremap
- Feat/Fix: Allow kicking offline players

### **###################################**

</div>

&nbsp;

<div align="center">

<img alt="Factions Mod Icon" src="src/main/resources/assets/factions/icon.png">
 
# Factions Mod

Highly customizable, lightweight and elegant factions mod for the [Fabric loader][fabric] in Minecraft 1.20.1

[![Release](https://img.shields.io/github/v/release/ickerio/factions?style=for-the-badge&include_prereleases&sort=semver)][github:releases]
[![Available For](https://img.shields.io/badge/dynamic/json?label=Available%20For&style=for-the-badge&color=e64626&query=version&url=https%3A%2F%2Fapi.blueish.dev%2Fapi%2Fminecraft%2Fversion%3Fid%3Dfactions)][modrinth]
 [![Downloads](https://img.shields.io/badge/dynamic/json?label=Downloads&style=for-the-badge&color=e64626&query=downloads&url=https%3A%2F%2Fapi.blueish.dev%2Fapi%2Fminecraft%2Fdownloads%3Fcurseid%3D497362%26modrinthid%3Dfactions)][modrinth:releases]

</div>

### **ABOUT**

Factions Mod is an ultra lightweight, fast, and elegant solution to factions in modern minecraft. The **server-side** mod expands upon all the classic factions features whilst also focusing on customization and performance. Grow your faction, expand your claims, and storm your enemies for their chunks and loot.

A faction's power cap increases as new members join, expanding their ability to claim more land. For each claim they make, it requires that faction to sustain more power. Dying to other players will temporarily lose faction power and if it drops below the required threshold, all their claims will be vulnerable to being overtaken.

&nbsp;

### **FEATURES**

- 🎯 Fully featured factions mod with over 30 [commands][wiki:commands]
- ✨ Faction ranks, colors, MOTD and descriptions
- 🎉 In faction private chat, global chat and a stylized player list
- ⚡ Extreme performance and reliability
- ⚙️ Advanced [configuration][wiki:config] and customization options
- 🔥 Dynmap and Lucko Perms support out the box
- 🚀 Event driven API for further extensibility 
- 💬 Strong [community][discord] and active developer support

&nbsp;

### **GET STARTED**

Factions Mod is very intuitive and works immediately after installation, requiring no additional configuration. However, you can read further about the mod on the [Wiki][wiki]. Our wiki goes in depth about the factions mechanics, its configuration, commands and integrations.

A list of all **commands** is available on our [wiki][wiki:commands]

Have an issue or a suggestion? Join [our discord][discord]

### **License**
[MIT](LICENSE)

[fabric]: https://fabricmc.net/
[modrinth]: https://modrinth.com/mod/factions
[modrinth:releases]: https://modrinth.com/mod/factions/versions
[github:releases]: https://github.com/ickerio/factions/releases
[wiki]: https://github.com/ickerio/factions/wiki
[wiki:config]: https://github.com/ickerio/factions/wiki/Config
[wiki:commands]: https://github.com/ickerio/factions/wiki/Commands
[discord]: https://discord.gg/tHPFegeAY8
