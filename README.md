# Village Mechanics

Village Mechanics is a conservative Minecraft Forge 1.7.10 villager extension.

The initial prototype provides:

- an independent, profession-specific weighted trade engine;
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

Village Mechanics does not depend on vanilla or Forge candidate trade tables. It takes control when
the player interacts with an ordinary `EntityVillager` whose profession has a populated active pool.
Profession 5 and Mystcraft profession 1210950779 are explicitly excluded.

`/villagemechanics inspect` creates an empty active `trades/profession_<id>.json` only when missing.
An empty `pool` is passive and preserves the original trading system. Once the active pool contains
valid entries, Village Mechanics deterministically selects `offerCount` weighted entries for each
villager. Item counts and maximum uses may be fixed or randomized with `min` and `max` constraints.
The same villager receives the same selection whenever its interface opens. Matching recipes retain
their current use counts; recipes outside the configured selection are removed.

Inspection merges the villager's saved offers into `trade_pools/profession_<id>.json` as reference
material. Counts, metadata, item NBT, second payment items, and maximum uses are retained. Reference
pool files never control gameplay and active files are never overwritten.

Prototype schema 1 `trades` entries are accepted as schema 3 authoritative `pool` entries for
compatibility. The old `generated_defaults` directory is no longer used or removed automatically.

The server command `/villagemechanics reload` reloads JSON configuration. A newly introduced
profession becomes active on its next interaction after reloading. Existing villagers are supported:
their offers are reconciled with the configured stable selection when the interface next opens.

## Combat configuration

`combat.json` controls maximum health, unarmed attack damage, knockback resistance, and weighted
natural armour sets. Combat attributes and armour are assigned without adding a polling task.
Village Mechanics deliberately leaves target selection and melee movement to SpecialAI when it is
installed. Natural armour is mechanically active and saved normally but is not rendered by this
prototype.

Use `/villagemechanics inspect` while looking at a villager to catalogue its saved offers, then report
its profession, health, attack damage, armour value, and hidden equipment slots.
