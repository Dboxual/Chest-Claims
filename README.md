# ChestClaims

A Paper 1.21 plugin for claiming and protecting chests and containers. Integrates with BetterTeams for team-based access control.

## Features
- Claim individual containers to restrict access
- Team-based claiming via BetterTeams integration
- Upkeep system for claim maintenance
- GUI-based claim management

## Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/claims cancel` | Cancel pending claim action | — |
| `/claims reload` | Reload configuration | `chestclaims.admin` |
| `/claims cycle` | Cycle claim access mode | — |

## Soft Dependencies
- `Bops` — economy integration
- `BetterTeams` — team-based access

## Build
```bash
./gradlew build
```
