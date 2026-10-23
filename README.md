# Village Mechanics

Village Mechanics is a conservative Minecraft Forge 1.7.10 villager extension.

The initial prototype provides:

- profession-specific authoritative weighted trade pools;
- observed reference pools generated only through inspection;
- configurable villager health, unarmed damage, and knockback resistance;
- optional profession-specific natural armour, stored in normal entity equipment slots;
- compatibility-first behaviour with Village Names and SpecialAI.

The mod does not replace village generation, villager rendering, or general mob AI.

## Development

- Minecraft: 1.7.10
- Forge: 10.13.4.1614
- Java target: 8

Configuration is generated under `config/VillageMechanics/` on first launch.

## Trade configuration

Village Mechanics does not assume that vanilla profession IDs or vanilla trade tables are present.
It registers a lightweight observer for Forge-registered professions without generating profession
files during loading.

`/villagemechanics inspect` creates an empty active `trades/profession_<id>.json` only when missing.
An empty `pool` is passive and preserves trades supplied by other mods. Once the active pool contains
valid entries, Village Mechanics takes ownership of that profession's Forge candidate list: earlier
candidates are cleared and exactly one entry is chosen by relative positive `weight` each time the
villager generates or unlocks a trade. Item counts and maximum uses may be fixed or randomized with
`min` and `max` constraints.

Before applying an active pool, Village Mechanics observes the candidates supplied by earlier Forge
trade handlers in memory. Inspection merges those candidates and the villager's saved offers into
`trade_pools/profession_<id>.json`. Counts, metadata, item NBT, second payment items, and maximum uses
are retained. Repeatedly inspecting newly generated villagers may reveal further randomized entries.
Reference pool files never control gameplay and active files are never overwritten.

Prototype schema 1 `trades` entries are accepted as schema 2 authoritative `pool` entries for
compatibility. The old `generated_defaults` directory is no longer used or removed automatically.

The server command `/villagemechanics reload` reloads JSON configuration. A newly introduced
profession is also registered when reloading. Existing villagers retain recipes already saved in
their entity data; active constraints apply to new villagers and later trade unlocks.

## Combat configuration

`combat.json` controls maximum health, unarmed attack damage, knockback resistance, and weighted
natural armour sets. Combat attributes and armour are assigned without adding a polling task.
Village Mechanics deliberately leaves target selection and melee movement to SpecialAI when it is
installed. Natural armour is mechanically active and saved normally but is not rendered by this
prototype.

Use `/villagemechanics inspect` while looking at a villager to catalogue its observed candidate pool
and saved offers, then report its profession, health, attack damage, armour value, and hidden
equipment slots.
