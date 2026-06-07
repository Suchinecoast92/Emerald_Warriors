# Emerald Warriors

Mod de Fabric para **Minecraft 1.21.11** que añade mercenarios contratables con esmeraldas: órdenes tácticas, IA de combate, equipamiento, rangos y PvP configurable.

**Versión actual: 1.0.0**

Inspirado en el sistema de mercenarios de TheAncientGuard, adaptado a la API moderna de Minecraft (Mojang mappings + Fabric).

## Instalación

1. Instala **Fabric Loader** para Minecraft 1.21.11.
2. Añade **Fabric API** a tu carpeta `mods/`.
3. Coloca `emerald_warriors-1.0.0.jar` en `mods/`.
4. Requiere **Java 21**.

## Inicio rápido

1. Encuentra un mercenario en una aldea o campamento.
2. **Clic derecho con esmeralda** → propuesta de contrato; segundo clic en 10 s → contratar.
3. **Clic derecho** (dueño) → abrir inventario y GUI de órdenes.
4. **Shift + clic derecho** (dueño) → ciclar orden: FOLLOW → GUARD → PATROL → NEUTRAL.
5. Usa un **cuerno de cabra** para dar órdenes a un grupo de mercenarios vinculados.

## Características

### Contratos
- Tarifa y días por compra según rango (valores deterministas por UUID del mercenario).
- Renovación: shift + clic derecho con esmeraldas (múltiplos exactos de la tarifa base; máx. 12 días acumulados).
- Pago con **bundle/saco** de esmeraldas: cambio automático, descuento en la siguiente compra y reducción de ban por disciplina.
- Al expirar: mensaje al ex-dueño, retirada y orden **NEUTRAL** (permanece en el mundo).

### Rangos
| Rango interno   | Nombre en juego | Textura   |
|-----------------|-----------------|-----------|
| RECRUIT         | Recluta         | cobre     |
| SOLDIER         | Novato          | hierro    |
| SENTINEL        | Aprendiz        | oro       |
| VETERAN         | Cualificado     | esmeralda |
| ANCIENT_GUARD   | Experto         | diamante  |

Stats por rango: HP, daño, knockback, radios de guardia/patrulla, distancia de persecución y umbral de retirada.

### Órdenes
| Orden   | Comportamiento |
|---------|----------------|
| FOLLOW  | Sigue al dueño; combate defensivo |
| GUARD   | Ancla en un punto; combate en radio |
| PATROL  | Patrulla un área; combate activo en zona |
| NEUTRAL | Deambula sin combate proactivo; solo se defiende si lo atacan |

Cambiar de orden suelta el target actual y reinicia la IA de combate.

### Toggle PvP — `Jugadores: ON/OFF`
Visible en la GUI del mercenario cuando la orden es **FOLLOW**, **GUARD** o **PATROL**.

| Situación | OFF | ON |
|-----------|-----|-----|
| Jugador golpea al **dueño** | Ignora | Defiende al instante (estilo lobo) |
| Intruso en zona (GUARD/PATROL) | Ignora | Ataca |
| Jugador golpea al **mercenario** | Se defiende | Se defiende |
| **Dueño** golpea a un jugador | Ayuda | Ayuda |

El toggle controla agresión **automática**. Si tú atacas primero, el mercenario te ayuda aunque el toggle esté OFF.

### Cuerno de cabra (grupos)
- **Shift + clic** en mercenario con cuerno → vincular / desvincular.
- **Shift + clic** al aire con cuerno → cambiar orden almacenada en el cuerno.
- **Clic normal** con cuerno → aplicar orden a todos los vinculados en 64 bloques.
- Los mercenarios muertos se eliminan automáticamente de los vínculos.

### IA de combate
- Goals separados para melee, arco y ballesta.
- Escudo reactivo, flanqueo variable, strafe en cooldown y retirada con poca vida.
- Ranged: evita friendly fire, busca terreno elevado en guardia/patrulla.
- Golpes críticos (Aprendiz 15 %, Experto 25 %).
- Leash por rango: abandona persecución si se aleja demasiado del ancla.
- Comportamiento ampliado durante raids.

### Defensa de aldeanos
- **Salvajes:** aggro inmediato contra jugadores que ataquen aldeanos o golems (línea de visión).
- **Contratados en GUARD/PATROL:** defienden contra mobs hostiles; ignoran al dueño atacando aldeanos.
- **FOLLOW / NEUTRAL:** sin defensa proactiva de aldeanos.

### Disciplina del dueño
Si golpeas a tu propio mercenario contratado (melee directo):
- Cada golpe muestra partículas de enfado.
- **3 golpes en 30 s** → rompe contrato, se retira y aplica **ban de recontrato** (días de Minecraft según rango):

| Rango     | Ban (días MC) |
|-----------|---------------|
| Recluta   | 5             |
| Novato    | 6             |
| Aprendiz  | 8             |
| Cualificado | 10          |
| Experto   | 12            |

El mercenario **nunca** targetea a su dueño actual.

### Huevo de spawn (creativo)
- **Huevo de mercenario** en la pestaña **Huevos de generación** del inventario creativo.
- Spawnea un mercenario salvaje (sin contrato) al usarlo como cualquier huevo vanilla.

### GUI e inventario
- Equipo (armadura, arma, offhand) + mochila 3×3.
- Barra de vida, XP y toggle de jugadores.
- Botón para finalizar contrato (con confirmación).

### Mundo
- Spawn en aldeas y **campamentos de mercenarios** (worldgen).
- Persistencia anti-despawn.
- Curación lenta cerca de fogatas en NEUTRAL.

## Comandos útiles (QA)

> `mod_id`: `emerald_warriors`

```mcfunction
/data get entity @e[type=emerald_warriors:emerald_mercenary,limit=1,sort=nearest] ContractTicks
/data merge entity @e[type=!player,distance=..6,limit=1,sort=nearest] {ContractTicks:40}
/data merge entity @e[type=!player,distance=..6,limit=1,sort=nearest] {ContractTicks:24000}
```

## Roadmap (post-1.0)

- Config editable (ban, leash, radios).
- Comando `standDown` dedicado sin cambiar orden.
- Monturas, formaciones y targeting con catalejo.
- Balance fino tras más feedback de jugadores.

## Arquitectura

```
emeraldwarriors/
├── entity/          # EmeraldMercenaryEntity + AI goals
├── client/          # Render, modelos, GUI
├── horn/            # Cuerno y grupos
├── inventory/       # Inventario del mercenario
├── mercenary/       # Enums (orden, rango, rol)
├── worldgen/        # Campamentos
└── config/          # Configuración
```

## Licencia

CC0-1.0 — ver archivo `LICENSE`.

## Enlaces

- [Repositorio](https://github.com/Suchinecoast92/Emerald_Warriors)
- [Changelog](CHANGELOG.md)

---

**Estado:** v1.0.0 — release estable.
