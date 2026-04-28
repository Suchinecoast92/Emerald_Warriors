# Emerald Warriors

Un mod de Minecraft 1.21.11 que añade mercenarios contratables con IA de combate, equipamiento y sistema de rangos.

## Contexto del Proyecto

Este mod está inspirado en el sistema de mercenarios de TheAncientGuard, adaptado a Minecraft 1.21.11 (Mojang mappings) con Fabric.

## Características Implementadas ✅

### Sistema de Contratos (vanilla-friendly)
- **Oferta de contrato en 2 pasos**: Clic derecho con esmeralda → propuesta; segundo clic dentro de 10s → contratar.
- **Tarifa y días por compra por rango**: Cada mercenario tiene valores deterministas por UUID dentro de un rango (evita RNG abusivo).
- **Duración del contrato en ticks**: Se consume con el tiempo y expira.
- **Mensajes cortos por rango**: Propuestas/aceptaciones estilo vanilla.
- **Renovación de contrato**: Shift + clic derecho con esmeraldas para extender tiempo si ya tiene contrato.
  - Solo acepta **múltiplos exactos** de la compra base (por ejemplo, si la tarifa es 6 esmeraldas por 3 días, acepta 6/12/18...).
  - Límite de acumulación: máximo **12 días** almacenados.
  - Feedback vanilla: el mercenario **"admira"** las esmeraldas antes de finalizar (durante ese tiempo la IA se pausa).
- **Pago con saco/bundle (incluye sacos de colores)**:
  - Acepta `bundle` si contiene **solo esmeraldas**.
  - Consume el pago y suelta **cambio** en esmeraldas individuales (estilo piglin).
  - La animación de admirar muestra el **saco/bundle** cuando se paga con saco.
- **Beneficios por pagar con saco/bundle**:
  - Reduce el ban al romper contrato por disciplina.
  - Otorga un **descuento** para la siguiente compra del mismo jugador (por 1 uso), según rango.
  - Baja probabilidad de frases "easter egg" al aceptar.
- **Expiración de contrato**:
  - Cuando llega a 0, el mercenario se acerca al ex-dueño, envía un mensaje sutil en el chat y luego se retira.
  - Cambia a orden **NEUTRAL** y re-ancla su zona (patrol center) para quedarse en el mundo sin despawnear.

### Rangos (`MercenaryRank`)
- **5 rangos**:
  - `RECRUIT`
  - `SOLDIER`
  - `SENTINEL`
  - `VETERAN`
  - `ANCIENT_GUARD`
- **Stats por rango**: HP, daño, resistencia al knockback, radios (detección/guardia/patrulla) y distancia máxima de persecución.
- **Texturas por rango**: Variantes visuales por sufijo (`cobre`, `hierro`, `oro`, `esmeralda`, `diamante`).

### Órdenes (`MercenaryOrder`)
- **FOLLOW**: Sigue al owner; combate defensivo.
- **GUARD**: Se ancla en un punto y combate dentro de su radio.
- **PATROL**: Patrulla un área y combate activamente en esa zona.
- **Shift + clic derecho (dueño)**: Cicla orden FOLLOW → GUARD → PATROL.

### Cuerno de cabra: órdenes por grupo
- **Shift + clic derecho en mercenario con cuerno (dueño)**: Vincular / desvincular al grupo del cuerno.
- **Shift + clic derecho al aire con cuerno**: Cambiar la orden almacenada en el cuerno.
- **Clic derecho normal con cuerno**: Ejecuta la orden del cuerno sobre los mercenarios vinculados (dentro del alcance definido por el sistema).
- **Limpieza de vínculos**: si un mercenario vinculado muere, se elimina automáticamente de los cuernos para evitar referencias a mercenarios muertos.

### Inventario y GUI
- **Inventario del mercenario**: Slots de equipo + bolsa.
- **Abrir inventario**:
  - Si el mercenario está contratado: clic derecho con esmeralda (dueño) o clic derecho normal (dueño).

### IA de combate (melee / arco / ballesta)
- **Melee / Ranged goals**: IA separada para melee, arco y ballesta.
- **Evita friendly fire (disparo/posicionamiento)**:
  - Mercenarios intentan no disparar si un aliado/owner está en línea de tiro.
  - Reposicionamiento táctico para ranged contra mobs.
- **Anti-clumping**:
  - Melee y ranged tienden a separarse alrededor del objetivo para no apilarse.
- **Fix de combate melee**: al acercarse al objetivo (especialmente tras recibir proyectiles) no debe quedarse "quieto" fuera de rango; el melee cierra distancia de forma robusta.
- **Cooldown del mazo (vanilla Mace)**: aplica un cooldown tipo vanilla para evitar spam del arma.

### PvP: persecución más "vanilla" al defender al owner
- **Velocidad mínima vs jugadores**: persecución más consistente.
- **Recalculo de path más frecuente vs jugadores**: menos exploit por zigzag.
- **Leash por rango**: si se aleja demasiado de su ancla, rompe persecución y regresa.
  - Contra jugadores: el leash se amplía (multiplicador) para que la defensa sea útil.

### Raids: comportamiento contextual
- **Detección robusta de raid** (con caché) para evitar coste por tick.
- **Multiplicadores durante raid**:
  - Aceptación de targets y distancias ampliadas.
  - Persecución ampliada durante combate de raid.

### Disciplina del owner (anti-abuso, vanilla-friendly)
- **Ventana de strikes**: 600 ticks (30s).
- **Strike 1**: mira al owner + `angry_villager`.
- **Strike 2**: mira + `angry_villager` + levanta escudo si tiene + contraataque:
  - Si el owner golpeó con "mano vacía" (no-arma): slap fijo (1.0F).
  - Si el owner golpeó con arma real: devuelve golpe usando el arma del mercenario.
- **Strike 3**: rompe contrato + se retira + aplica ban de recontrato al ex-owner (por días de Minecraft) según rango.
- **Ban por rango (días de Minecraft)**:
  - `RECRUIT`: 5
  - `SOLDIER`: 6
  - `SENTINEL`: 8
  - `VETERAN`: 10
  - `ANCIENT_GUARD`: 12
- **Definición de "arma real" (para el counter del strike 2)**:
  - Se considera arma si el ítem en `mainHand` aporta daño de ataque (`ATTACK_DAMAGE`) > 1 (o es arco/ballesta).
  - Si no aporta daño real (bloques/comida/etc.), se trata como "mano vacía".
- **Regla dura**: el mercenario nunca debe seleccionar como target a su owner actual.

### UX (chat vs action bar)
- **Chat**: solo diálogos inmersivos del mercenario.
- **Action Bar**: información mecánica (contrato iniciado/extendido, ban restante, etc.).

### Curación pasiva cerca de fogata
- Si está en **NEUTRAL**, fuera de combate y herido, se cura lentamente cerca de una fogata encendida.
- Alcance: **radio 4** (horizontal) y **±1 bloque** (vertical).

### Persistencia (anti-despawn)
- El mercenario está marcado como persistente y además se deshabilita explícitamente cualquier despawn por distancia.
- Solo desaparece si muere (o si un comando/mod externo lo elimina).

## Comandos útiles (pruebas / QA)

> Nota: el `mod_id` es `emerald_warriors`. Si el selector por tipo no funciona en tu entorno, usa `type=!player` + `distance`.

- **Ver ticks restantes del contrato**
  - `/data get entity @e[type=emerald_warriors:emerald_mercenary,limit=1,sort=nearest] ContractTicks`

- **Forzar expiración rápida (2 segundos aprox.)**
  - `/data merge entity @e[type=!player,distance=..6,limit=1,sort=nearest] {ContractTicks:40}`

- **Simular "queda 1 día"**
  - `/data merge entity @e[type=!player,distance=..6,limit=1,sort=nearest] {ContractTicks:24000}`

## Roadmap / Pendiente 📋

- **Balance/config**:
  - Ajustar valores de ban por rango a los definitivos.
  - Exponer valores (ban, leash, etc.) vía config si se desea.
- **Disciplina vs daño indirecto**: decidir si aplica disciplina también a proyectiles/AOE del owner (hoy es melee directo).
- **Retirada tras ruptura**: hacer el "retreat" más robusto (buscar posiciones seguras si hay obstáculos).
- **QA / pruebas**: checklist de regresión para PvP, raid y disciplina (multi-cliente).

## Arquitectura Técnica

### Paquetes Principales
```
emeraldwarriors/
├── entity/                 # Entidades y AI
│   ├── EmeraldMercenaryEntity.java
│   └── ai/                 # Goals de IA
├── client/                 # Renderizado y GUI
│   ├── render/            # Renderers
│   ├── model/             # Modelos 3D
│   └── gui/               # Interfaces
├── horn/                   # Sistema de cuerno (grupos / órdenes)
├── inventory/             # Sistema de inventario
├── mercenary/             # Enums y datos
└── menu/                  # Menus y containers
```

### Dependencias
- **Minecraft 1.21.11**: Versión objetivo
- **Fabric Loader**: Carga de mods
- **Mojang Mappings**: Nomenclatura oficial
- **Fabric API**: Eventos y utilidades
- **EntityRenderStates**: Sistema moderno de renderizado

## Instalación

1. Requiere **Fabric Loader** para Minecraft 1.21.11
2. Colocar el archivo `.jar` en la carpeta `mods/`
3. Iniciar el juego y disfrutar

## Contribución

El proyecto está en desarrollo activo. Las áreas principales para contribución son:
- Sistema de economía y balance
- IA avanzada y tactical behaviors
- Interfaz de usuario mejorada
- Sistema de misiones y quests

## Licencia

Este mod está desarrollado como de aprendizaje de la API de Minecraft 1.21.11.

---

**Estado Actual**: Alpha funcional con mercenarios contratables, órdenes, IA de combate y sistema de disciplina del owner.
