# Factions Update - New Features

## 💪 Player Power & Activity

Each player now contributes **individual power** to their faction based on their activity!

- Every member contributes up to **20 power** (by default) to the faction
- **Inactive players** contribute less power over time:
  - 7+ days inactive: 75% power
  - 14+ days inactive: 50% power
  - 30+ days inactive: 0% power
- Power contribution resets when you **log in**
- Check individual power in `/f info` next to each member's name

---

## 💰 Wealth Power

Factions can gain **Wealth Power** by sacrificing valuable items!

- Use `/f sacrifice` while holding valuable items to sacrifice them
- Sacrifice items like **diamonds**, **diamond blocks**, **netherite ingots**, or **netherite blocks**
- Wealth power **decays over time** if you stop sacrificing
- Maximum wealth power: **100** (by default)
- Check your wealth power by hovering over "Total Power" in `/f info`

---

## ⚔️ War Power

Your faction can earn **War Power** by defeating players from other factions in combat!

- **Earn power** when you kill players from other factions
- **Bonus rewards** for killing members of enemy factions (2x by default)
- War power **decays over time** if your faction stops fighting
- Maximum war power: **100** (by default)
- Check your war power by hovering over "Total Power" in `/f info`

---

## 🏆 Fame Power

Your faction can earn **Fame Power** by completing heroic deeds like finishing dungeons!

- Fame is earned through **special activities** (dungeons, events, etc.)
- Fame power **decays over time** if your faction stops earning it
- Maximum fame power: **100** (by default)
- Check your fame power by hovering over "Total Power" in `/f info`

---

## 🏴 Claim Decay

Claims now require **power to maintain**! If your faction's power drops too low, claims will be lost.

- Each claim requires **5 power** (by default) to maintain
- If your power drops below the required amount, **claims will be removed automatically**
- Claims farthest from your faction home are removed first
- Claims in different dimensions are removed before same-dimension claims
- Check your "Required Power" in `/f info` to see how much power your claims need

### Tips:
- Keep your members active to maintain power
- Sacrifice items regularly to boost wealth power
- Fight enemies to gain war power
- Complete dungeons to earn fame power
- Don't over-expand - only claim what you can defend!

---

## 👑 Vassals & Overlords

Factions can now form **vassal relationships** - a hierarchy where smaller factions serve larger ones.

### How it works:
- A faction can **offer** to become another faction's vassal
- The target faction's leader must **accept** the offer
- Factions must be **mutual allies** before becoming vassals
- Overlords gain a **power bonus** (25% of vassal's power by default)

### Commands:
| Command | Who can use | What it does |
|---------|-------------|--------------|
| `/f vassal info` | Members | View your vassal status |
| `/f vassal offer <faction>` | Leaders | Offer to become their vassal |
| `/f vassal accept <faction>` | Leaders | Accept a vassal offer |
| `/f vassal release <faction>` | Leaders | Release one of your vassals |
| `/f vassal leave` | Leaders | Leave your overlord |

### Rules:
- Only **one level** of hierarchy (no vassal chains)
- Overlords **cannot** become vassals of another faction
- If an overlord faction disbands, all vassals are **automatically released**

---

## 🙏 God's Blessings

Faction leaders can **pray to the gods**, spending wealth power to grant powerful effects to all faction members!

### How it works:
- Leaders use `/f gods pray <godname>` to invoke a god's blessing
- The faction spends **wealth power** as an offering
- **All online members** immediately receive the potion effect
- Members who **login or respawn** while a blessing is active get the remaining duration
- Multiple blessings can be active at once (up to 2 by default)

### Commands:
| Command | Who can use | What it does |
|---------|-------------|--------------|
| `/f gods` | Anyone | List all available gods and their costs |
| `/f gods pray <god>` | Leaders | Pray to a god to grant its blessing |

### Default Gods:
| God | Effect | Duration | Level | Cost |
|-----|--------|----------|-------|------|
| **Ares** | Strength | 5 min | I | 25 |
| **Athena** | Resistance | 5 min | I | 30 |
| **Hermes** | Speed | 5 min | II | 20 |
| **Apollo** | Regeneration | 3 min | I | 35 |
| **Hephaestus** | Haste | 5 min | II | 15 |

### Tips:
- Hover over god names in `/f gods` to see details, click to auto-fill the command
- Check active blessings in `/f info`
- **5 minute cooldown** between prayers - coordinate with your faction!
- Blessings persist through death - you'll get them back when you respawn
- If your faction is a **vassal**, your overlord's blessings may also apply to you

---

## 📊 Power Breakdown

Your faction's total power comes from multiple sources. Hover over "Total Power" in `/f info` to see:

| Source | Description |
|--------|-------------|
| **Base** | Starting power for all factions |
| **Player** | Combined power from all active members |
| **Wealth** | Power from sacrificing items |
| **War** | Power from killing enemy players |
| **Fame** | Power from completing dungeons and events |
| **Vassal** | Bonus power from your vassals (if overlord) |
| **Admin** | Power added/removed by server admins |
