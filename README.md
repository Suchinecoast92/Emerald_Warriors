# Emerald Warriors

Mod de Fabric para **Minecraft 1.21.11** que añade mercenarios contratables con esmeraldas: órdenes (cuerno + catalejo), monturas autónomas, IA de combate, rangos, defensa de aldeanos en raid y PvP configurable.

**Versión actual: 1.0.0**

Inspirado en el sistema de mercenarios de TheAncientGuard, adaptado a la API moderna de Minecraft (Mojang mappings + Fabric).

> **Para jugar ya:** lee [Qué debes saber](#qué-debes-saber-jugador). Ahí están los detalles que suelen confundir (catalejo vs órdenes base, monturas, camello, PvP y raids).

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
  - Al **suelo** → orden de movimiento al punto marcado (**hold**).
  - A una **entidad** → orden de ataque sobre ese objetivo.
- Resalta brevemente el objetivo marcado (brillo cliente).
- Los comandos tácticos no cambian la orden persistente del mercenario (FOLLOW/GUARD/PATROL/NEUTRAL).
- Alcance de mando y de apuntado: 128 bloques.
- **Persistencia al reentrar / relog:** el punto de hold, offsets de dispersión y el ataque táctico se guardan en NBT del mercenario. Tras salir y volver, siguen en su posición (o persiguiendo el objetivo) sin que tengas que marcar de nuevo. El cliente también reinicia el cooldown del catalejo al detectar una sesión nueva.
- **Dispersión en posición:** varios mercenarios enviados al mismo punto se reparten en un radio corto (0,6–2,6 bloques) en lugar de amontonarse en el mismo bloque.
- **Ataque táctico:** arqueros y ballesteros se acercan si no tienen línea de visión; si ya ven al objetivo desde una posición elevada, permanecen y disparan (estilo esqueleto/pillager). La ballesta no carga fuera de alcance ni sin línea de visión.
- En combate grupal, cada mercenario flanquea desde un ángulo estable para rodear al enemigo.
- Un ataque táctico activo tiene prioridad: el mercenario no cambia de objetivo por defensa de aldeanos u otras amenazas hasta que termine esa orden.

### Monturas (v3.1)
Sistema vanilla: **correa** (solo para vincular) + **silla**, sin GUI nueva.

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
- A pie y lejos: la montura **sigue al mercenario por pathfinding** (no se ata con correa vanilla de forma continua; eso evitaba bucles al ir a un hold con catalejo).
- Si el hold táctico está lejos, el mercenario **prefiere montar** e ir a caballo hasta el punto.
- Anti-teleport: camina hasta la montura (~2,5 bloques) antes de subir.
- **Control del jinete:** el mercenario montado dirige la montura (patrulla, persecución y combate). Vanilla solo reconoce a jugadores como conductores; el mod declara al mercenario como jinete controlador para desactivar la IA de deambular del caballo/camello.

**Camello (dos asientos)**
- Si el mercenario ya va montado y tú subes, ocupas el **asiento trasero**; el mercenario sigue delante y sigue dirigiendo.
- Si tú montas primero y el mercenario sube después, se coloca en el **asiento delantero** (conductor).
- Altura visual ajustada para que no flote ni se hunda en la silla.

**Monturas aliadas (no las atacan)**
- Los mercenarios **no targetean ni dañan** su propia montura ni las monturas vinculadas de otros mercenarios del mismo dueño.
- Los salvajes tratan como protegidas todas las monturas vinculadas a cualquier mercenario.
- Evita que en combate se peleen contra caballos/camellos del grupo por daño colateral.

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
- Los mercenarios del campamento se colocan **después** de terminar la generación del chunk (evita congelar el mundo al crear mundos nuevos).
- ~25 % de los mercenarios del campamento reciben montura; ~40 % de esos empiezan montados, el resto a pie (la montura les sigue por pathfinding).
- Los salvajes montados **controlan** su montura: patrullan y persiguen a caballo (persecución montada incluso si el path inicial falla, hasta ~32 bloques).
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
- Escudo reactivo, strafe en cooldown y retirada con poca vida.
- Melee estilo vanilla (como lobos/zombis): todos persiguen y golpean al objetivo; un apiñamiento leve es normal al estar adyacentes.
- Si hay aliados cerca atacando lo mismo, cada mercenario puede buscar un punto lateral cercano (offset suave) para no pelear por el mismo bloque — sin flanqueo táctico ni formaciones.
- **Ranged (arco/ballesta):** evita friendly fire; con línea de visión y ventaja de altura permanece en su posición y dispara (como esqueletos/pillagers). Cabeza, brazos y arma apuntan al objetivo antes de disparar.
- **Endermen:** no se consideran amenaza automática; solo se combaten si los provocas, das orden de ataque o el mercenario se defiende. Arqueros/ballesteros cambian a melee contra endermen.
- **Puertas de valla:** solo se abren cuando el mercenario está pathfinding y va a pasar por ellas (no al estar quieto en guardia).
- Golpes críticos (Aprendiz 15 %, Experto 25 %).
- Leash por rango: abandona persecución si se aleja demasiado del ancla (excepto en autodefensa o comandos tácticos).
- Comportamiento ampliado durante raids.

### Apariencias (skins)
- **42 modelos** de mercenario (`m1`–`m42f`), cada uno con 5 variantes por rango (cobre, hierro, oro, esmeralda, diamante).
- Los modelos con sufijo **`f`** usan brazos delgados (Alex); el resto usa Steve.
- Última tanda añadida: **m36**, **m37f**, **m38f**, **m39f**, **m40**, **m41f**, **m42f**.
- Retexturas recientes en modelos existentes: m1, m2, m8, m9, m12, m13, m17, m18, m20, m22, m24, m29, m30, m31f.

### Defensa de aldeanos
- **Salvajes:** aggro inmediato contra quien ataque aldeanos, comerciantes errantes o golems de hierro (estilo golem).
- **Contratados en GUARD/PATROL:** defienden contra mobs hostiles; no atacan jugadores por esta vía (el PvP lo controla el toggle). Ignoran al dueño si él golpea aldeanos.
- **FOLLOW en raid:** pelean a quien esté atacando o **teniendo como objetivo** a aldeanos/golems cercanos (p. ej. evocador antes de que aterrice el golpe). Pueden abandonar un combate lejano para salvar la aldea; radio de detección ampliado durante la raid.
- **FOLLOW / NEUTRAL (fuera de raid):** sin defensa proactiva de aldeanos.
- Un **ataque táctico del catalejo** tiene prioridad sobre esta defensa hasta que se complete.

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

## Qué debes saber (jugador)

1. **Minecraft 1.21.11 + Fabric + Java 21.** Sin Fabric API no carga.
2. **Contrato en dos clics** con esmeralda (propuesta → confirmar en 10 s). Renovar: shift + clic con esmeraldas (múltiplos exactos de la tarifa).
3. **Órdenes base** (FOLLOW/GUARD/PATROL/NEUTRAL) y **órdenes tácticas del catalejo** son cosas distintas: el catalejo mueve o ataca sin cambiar la orden base. El hold **persiste al relog**.
4. **Cuerno y catalejo** mandan a 128 bloques. Vincula mercenarios con shift + clic derecho; el clic normal (cuerno) o el clic izquierdo apuntando (catalejo) aplica la orden.
5. **Monturas:** la correa solo sirve para **vincular/desvincular**. Después la montura sigue sola; no esperes una correa vanilla permanente.
6. **Camello compartido:** el mercenario es el conductor (asiento delantero); tú vas atrás si subes cuando él ya está montado.
7. **No dispares a caballos del grupo a propósito:** igual no deberían targetearlos, pero el daño de área/proyectiles vanilla sigue existiendo.
8. **PvP:** el toggle **Jugadores ON/OFF** solo controla agresión automática. Si tú atacas primero, te ayudan aunque esté OFF. Si te golpean al mercenario, se defiende siempre.
9. **Disciplina:** 3 golpes tuyos al mercenario en 30 s rompen el contrato y aplican ban de recontrato (días según rango).
10. **Raids:** con FOLLOW, tus mercenarios pueden dejar lo que estén haciendo para proteger aldeanos/golems. Colócalos cerca de la aldea si quieres que intervengan.
11. **Salvajes:** no atacan por iniciativa (salvo defensa de aldeanos / autodefensa). Pueden venir montados en patrullas o campamentos.
12. **Servidor / LAN:** compatible multijugador; los vínculos de cuerno/catalejo y el hold táctico viven en el servidor (NBT).

## Roadmap (post-1.0)

- Config editable (ban, radios, monturas).
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
├── mixin/           # Jinete controlador, asientos de camello
├── inventory/       # Inventario del mercenario
├── mercenary/       # Enums (orden, rango, rol)
├── worldgen/        # Campamentos
└── config/          # Configuración
```

## Licencia

**Todos los derechos reservados** (Copyright © 2026 Suchinecoast92). Ver archivo [`LICENSE`](LICENSE).  
No se concede permiso para copiar, modificar, distribuir ni crear obras derivadas sin autorización previa por escrito.

## Enlaces

- [Repositorio](https://github.com/Suchinecoast92/Emerald_Warriors)
- [Changelog](CHANGELOG.md)

---

**Estado:** v1.0.0 — hold táctico persistente, monturas aliadas protegidas, camello con asientos correctos y defensa de aldeanos en raid en `main`.
