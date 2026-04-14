# God's Blessings System

Faction leaders can pray to the gods, spending **wealth power** to grant temporary potion effects to all faction members.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/f gods` | Any player | List all available gods with their effects and costs |
| `/f gods pray <god>` | Leader only | Pray to a god to grant its blessing |

## How It Works

1. **Sacrifice items** to build up wealth power (`/f sacrifice`)
2. **Leaders pray** to a god using `/f gods pray <godname>`
3. **All online members** receive the potion effect immediately
4. **Members who login** while blessing is active get the effect with remaining duration
5. **Death doesn't remove blessings** - effects are reapplied when you respawn
6. Check active blessings with `/f info`

## Default Gods

| God | Effect | Duration | Level | Cost |
|-----|--------|----------|-------|------|
| **Ares** | Strength | 5 min | I | 25 |
| **Athena** | Resistance | 5 min | I | 30 |
| **Hermes** | Speed | 5 min | II | 20 |
| **Apollo** | Regeneration | 3 min | I | 35 |
| **Hephaestus** | Haste | 5 min | II | 15 |

## Limits

- **Cooldown**: 5 minutes between prayers (per faction)
- **Max Active**: 2 blessings can be active at once
- Praying to the same god refreshes its duration

## Tips

- Hover over god names in `/f gods` to see details
- Click a god name to auto-fill the pray command
- Use `/f info` to see active blessings and remaining time
- Coordinate with your faction before important battles!
