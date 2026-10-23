# Village Mechanics

Village Mechanics is a conservative Minecraft Forge 1.7.10 villager extension.

The initial prototype provides:

- profession-specific weighted trade JSON;
- replaceable default trade files that are never overwritten after creation;
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
During mod post-initialization, Forge's registered profession IDs receive safe empty templates in
`config/VillageMechanics/trades/`. Each file may use `replace` mode to own the final recipe list or
`append` mode to preserve trades added earlier by other mods. `rolls` controls how many unique
entries are chosen, and each entry's positive `weight` is relative to the total remaining weight.

Files in the active `trades/` directory are only created when missing and are never overwritten.
Inspecting a profession that was not registered with Forge creates a safe empty append template
named `profession_<id>.json` in both `trades/` and `generated_defaults/`, then registers it for
subsequent trade generation. Existing legacy files remain explicit user configuration and are not
deleted automatically.

Inspection initializes the targeted villager's current offers and merges them into
`generated_defaults/profession_<id>.json` as an exact `replace` catalogue. Counts, metadata, item
NBT, second payment items, and maximum uses are retained. The active file in `trades/` is not
changed. Because other mods may randomize offers or unlock them progressively, repeated inspection
of different villagers with the same profession can reveal and append further variants; this is an
observed catalogue rather than a promise that every theoretical offer has been discovered.

The server command `/villagemechanics reload` reloads JSON configuration. A newly introduced
profession is also registered when reloading. Existing villagers retain recipes already saved in
their entity data; the new configuration is used when a villager next generates a recipe list.

## Combat configuration

`combat.json` controls maximum health, unarmed attack damage, knockback resistance, and weighted
natural armour sets. Combat attributes and armour are assigned without adding a polling task.
Village Mechanics deliberately leaves target selection and melee movement to SpecialAI when it is
installed. Natural armour is mechanically active and saved normally but is not rendered by this
prototype.

Use `/villagemechanics inspect` while looking at a villager to catalogue its visible trade offers
and report its profession, health, attack damage, armour value, and hidden equipment slots.
