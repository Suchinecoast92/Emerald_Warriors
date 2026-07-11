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
| NEUTRAL | Deambula sin combate proactivo; se defiende si lo atacan (jugador, mob u otro mercenario) |

Cambiar de orden suelta el target actual y reinicia la IA de combate.

**Autodefensa (salvajes y NEUTRAL):** los mercenarios sin contrato no atacan por iniciativa propia, pero responden a quien los lastime — incluidos tus mercenarios contratados si los provocan. Pueden perseguir al agresor aunque salga del radio de patrulla.

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
- **Clic normal** con cuerno → aplicar orden a todos los vinculados en 128 bloques.
- Los mercenarios muertos se eliminan automáticamente de los vínculos.

### Catalejo (órdenes tácticas)
- **Shift + clic derecho** en mercenario con catalejo → vincular / desvincular al grupo del catalejo.
- **Shift + clic derecho** al aire con catalejo → ciclar la orden almacenada (como el cuerno).
- **Clic izquierdo** mientras apuntas con el catalejo:
  - Al **suelo** → orden de movimiento al punto marcado.
  - A una **entidad** → orden de ataque sobre ese objetivo.
- Resalta brevemente el objetivo marcado (brillo cliente).
- Los comandos tácticos no cambian la orden persistente del mercenario.
- Alcance de mando y de apuntado: 128 bloques.
- **Dispersión en posición:** varios mercenarios enviados al mismo punto se reparten en un radio corto (0,6–2,6 bloques) en lugar de amontonarse en el mismo bloque.
- **Ataque táctico:** arqueros y ballesteros se acercan al objetivo antes de disparar; la ballesta no carga fuera de alcance ni sin línea de visión.
- En combate grupal, cada mercenario flanquea desde un ángulo estable para rodear al enemigo.

### Monturas (v3.1)
Sistema vanilla: **correa** + **silla**, sin GUI nueva.

**Vincular (contratados)**
1. **Shift + clic derecho** con correa en tu mercenario → seleccionar.
2. **Shift + clic derecho** en la montura con silla → vincular (consume 1 correa).
3. **Shift + clic derecho** con correa en el mercenario si ya tiene montura → desvincular.

El mensaje muestra el **nombre de la montura** (`[🐴] Relámpago vinculado.`).

**Monturas soportadas**
- Caballo, burro, mula y camello (todos con silla).
- Campamentos: 50 % caballo, 20 % burro, 15 % mula, 15 % camello (25 % de mercenarios del campamento reciben montura).
- En **desierto** y **badlands**: 70 % camello, 15 % caballo, 10 % burro, 5 % mula.
- Camello sentado: se levanta automáticamente antes de montar o moverse con él.
- Ajustes de altura por tipo (anclaje + render) para que el jinete quede bien en la silla.

**Comportamiento autónomo**
- Decide cuándo montar, bajar o ir a pie según orden, distancia, arma y combate.
- **Follow:** monta si está lejos; baja cerca del dueño (salvo lanza).
- **Guard / Patrol:** monta para desplazarse; baja al llegar o en melee con espada.
- **Neutral:** mayormente a pie.
- A pie y cerca del dueño: puede llevar la montura con correa (estilo errante + llamas).
- Anti-teleport: camina hasta la montura (~2,5 bloques) antes de subir.

**Carga con lanza (jinete)**
- Los mercenarios montados con **lanza** ejecutan el ataque de carga cinético vanilla.
- Reutiliza la IA de carga de los mobs con lanza (1.21.11): galopan contra el objetivo,
  mantienen la lanza en guardia y reposicionan entre pasadas para volver a cargar.
- El daño escala con la velocidad relativa (galope de la montura); a mayor velocidad, más daño,
  con knockback y capacidad de desmontar en la fase inicial (comportamiento vanilla del componente).
- Convergencia carga/jab: con distancia embisten (carga); pegados y sin carrerilla ceden al
  golpe melee (jab) y vuelven a cargar en cuanto recuperan distancia.
- El lancero se mantiene montado en combate para poder cargar.

**Campamentos salvajes**
- Cada mercenario de campamento spawnea con una montura domada y silla (caballo, burro, mula o camello).
- ~25 % de los mercenarios del campamento reciben montura; ~40 % de esos empiezan montados, el resto a pie con correa.
- Al contratar, el vínculo persiste.
- Al **morir**, **expirar el contrato** o **romper contrato por disciplina**, el mercenario se desmonta y suelta la montura vinculada.

**Patrullas salvajes (spawn natural)**
- Grupos de 1–4 mercenarios en el mundo.
- ~22 % de grupos: líder Veterano/Guardián ancestral montado + 1–3 acompañantes de rango menor a pie.
- Los acompañantes siguen al líder mientras patrullan.

**Ritmo montado**
| Situación | Velocidad (pathfinding) |
|-----------|-------------------------|
| Viaje (fuera de combate) | `goalSpeed × 1,2` (× escala camello si aplica) |
| Combate | viaje `× 1,175` (galope moderado) |

Equinos usan escala 1,0. Camello: al menos ×1,55 respecto a su velocidad base, o más si su atributo es menor que un caballo medio.

Ejemplos: follow 1,0 → 1,20 viaje / 1,41 combate; patrol 0,9 → 1,08 / 1,27.

**Postura al montar**
- Cuerpo alineado con la dirección de la montura.
- Cabeza libre con giro limitado (±55° horizontal, ±40° vertical).

### IA de combate
- Goals separados para melee, arco y ballesta.
- Escudo reactivo, flanqueo variable, strafe en cooldown y retirada con poca vida.
- Ranged: evita friendly fire, busca terreno elevado en guardia/patrulla; en órdenes tácticas prioriza acercarse antes de disparar.
- Golpes críticos (Aprendiz 15 %, Experto 25 %).
- Leash por rango: abandona persecución si se aleja demasiado del ancla (excepto en autodefensa o comandos tácticos).
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
- Grupos salvajes de 1–4 mercenarios (configurable en `emerald_warriors.json`).
- Persistencia anti-despawn.
- Curación lenta cerca de fogatas en NEUTRAL.

### Configuración
Archivo `config/emerald_warriors.json` (se crea al primer arranque):

| Opción | Descripción |
|--------|-------------|
| `toggles.camps` | Activa campamentos en worldgen |
| `toggles.solitarySpawns` | Spawn natural de grupos salvajes |
| `camp.rarityChance` | 1 de cada N chunks intenta generar campamento |
| `solitarySpawn.weight` | Peso del spawn natural |
| `solitarySpawn.maxGroup` | Tamaño máximo de grupo (por defecto 4) |

## Comandos (operador)

| Comando | Descripción |
|---------|-------------|
| `/mercenary addexp <cantidad>` | Añade XP a tus mercenarios contratados en 10 bloques |

## Comandos útiles (QA / NBT)

> `mod_id`: `emerald_warriors`

```mcfunction
/data get entity @e[type=emerald_warriors:emerald_mercenary,limit=1,sort=nearest] ContractTicks
/data merge entity @e[type=!player,distance=..6,limit=1,sort=nearest] {ContractTicks:40}
/data merge entity @e[type=!player,distance=..6,limit=1,sort=nearest] {ContractTicks:24000}
```

## Roadmap (post-1.0)

- Config editable (ban, leash, radios).
- Comando `standDown` dedicado sin cambiar orden.
- Formaciones y banderín de tropa.
- Balance fino tras más feedback de jugadores.

## Arquitectura

```
emeraldwarriors/
├── entity/          # EmeraldMercenaryEntity + AI goals
│   └── spawn/       # Grupos salvajes y patrullas montadas
├── client/          # Render, modelos, GUI
├── horn/            # Cuerno y grupos
├── spyglass/        # Catalejo y comandos tácticos
├── mount/           # Monturas (vínculo, IA autónoma, ritmos)
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

**Estado:** v1.0.0 — dispersión táctica, autodefensa de salvajes, desvincular montura al terminar contrato y mejoras de combate a distancia integradas en `main`.
