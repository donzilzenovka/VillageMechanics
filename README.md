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

The five vanilla professions receive individual files in `config/VillageMechanics/trades/`.
Each file may use `replace` mode to own the final recipe list or `append` mode to preserve trades
added earlier by vanilla and other mods. `rolls` controls how many unique entries are chosen, and
each entry's positive `weight` is relative to the total weight of the remaining entries.

Fresh reference copies are written to `config/VillageMechanics/generated_defaults/` on every load.
Files in the active `trades/` directory are only created when missing and are never overwritten.
Inspecting a previously unknown modded profession creates a safe empty append template named
`profession_<id>.json` in both directories and registers it for subsequent trade generation.

The server command `/villagemechanics reload` reloads JSON configuration. A newly introduced
profession is also registered when reloading. Existing villagers retain recipes already saved in
their entity data; the new configuration is used when a villager next generates a recipe list.

## Combat configuration

`combat.json` controls maximum health, unarmed attack damage, knockback resistance, and weighted
natural armour sets. Combat attributes and armour are assigned without adding a polling task.
Village Mechanics deliberately leaves target selection and melee movement to SpecialAI when it is
installed. Natural armour is mechanically active and saved normally but is not rendered by this
prototype.

Use `/villagemechanics inspect` near a villager to report its profession, health, attack damage,
armour value, and hidden equipment slots.
