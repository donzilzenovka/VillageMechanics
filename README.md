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
added earlier by vanilla and other mods. In append mode, entries in `remove` selectively discard
matching existing recipes before entries in `trades` are added. `rolls` controls how many unique
additions are chosen, and each entry's positive `weight` is relative to the total weight of the
remaining entries.

A removal may match `buy`, `buySecondary`, and/or `sell`. Within each item match, `item`, `meta`,
and `count` are optional; omitted values are wildcards. An empty removal is rejected to prevent an
accidental deletion of every recipe. For example:

```json
{
  "profession": 191,
  "mode": "append",
  "rolls": 0,
  "remove": [
    {
      "buy": { "item": "minecraft:emerald" },
      "sell": { "item": "examplemod:unwanted_item", "meta": 0 }
    }
  ],
  "trades": []
}
```

Fresh reference copies are written to `config/VillageMechanics/generated_defaults/` on every load.
Files in the active `trades/` directory are only created when missing and are never overwritten.
At server startup the Forge villager registry is scanned and safe empty append templates are made
for modded professions as `profession_<id>.json`. A one-time entity-join observation also catches
villagers from mods that assign a profession without registering it correctly. This observation
does not poll villagers and does not replace or attempt to reconstruct another mod's native trades.

The server command `/villagemechanics reload` reloads JSON configuration. A newly introduced
profession is also registered when reloading. Existing villagers retain recipes already saved in
their entity data; the new configuration is used when a villager next generates a recipe list.

## Combat configuration

`combat.json` controls maximum health, unarmed attack damage, knockback resistance, and weighted
natural armour sets. Combat attributes and armour are assigned without adding a polling task.
Village Mechanics deliberately leaves target selection and melee movement to SpecialAI when it is
installed. Natural armour is mechanically active and saved normally but is not rendered by this
prototype.

Use `/villagemechanics inspect` while looking directly at a villager to report its raw profession
ID, health, attack damage, armour value, and hidden equipment slots.
