# Changelog

## [Unreleased]

### Correcciones
- **Catalejo:** las órdenes tácticas vuelven a funcionar tras salir y reentrar al mundo (cooldown del cliente reiniciado al detectar sesión nueva).
- **Worldgen:** spawn de mercenarios en campamentos diferido al hilo del servidor; evita congelar el mundo al generar chunks nuevos.

### Combate e IA
- Arqueros/ballesteros mantienen ventaja de altura con línea de visión (estilo esqueleto/pillager).
- Animación de apuntado ranged alineada con el objetivo (cabeza, brazos, arco/ballesta).
- Endermen neutrales salvo provocación u orden; arqueros pasan a melee contra endermen.
- Puertas de valla solo se abren cuando el pathfinding las cruza.

### Carga con lanza montado
- Los jinetes con lanza usan el ataque de carga cinético vanilla (1.21.11).
- Puerto de la IA vanilla `SpearUseGoal` adaptado al mercenario (PathfinderMob) y a la
  navegación de la montura; el daño lo aplica el componente `kinetic_weapon` por velocidad.

### Mando a distancia
- Alcance de órdenes de cuerno y catalejo ampliado de 64 a 128 bloques.

### Monturas v3.1
- Caballo, burro, mula y camello en campamentos y contratados.
- Velocidad de viaje y combate montado; escala especial para camello.
- Montaje diferido en spawn salvaje para evitar fallos en worldgen.

### Spawn salvaje
- Grupos de 1–4 mercenarios (`solitarySpawn.maxGroup`).
- ~22 % de grupos con patrulla montada (líder Veterano/Experto + acompañantes).
- Campamentos: ~25 % de mercenarios con montura; ~40 % de esos empiezan montados.

## [1.0.0] — 2026-06-07

Primera versión estable jugable.

### Contratos y economía
- Contratación en 2 pasos con esmeraldas (tarifa y días por rango).
- Renovación con shift + clic derecho; pago con sacos/bundles y cambio.
- Expiración de contrato con mensaje y transición a orden NEUTRAL.
- Ban temporal al romper contrato por disciplina (según rango).
- Descuento por pago con bundle.

### Órdenes y cuerno
- 4 órdenes: FOLLOW, GUARD, PATROL, NEUTRAL.
- Ciclo de órdenes con shift + clic derecho en el mercenario.
- Sistema de cuerno de cabra: vincular mercenarios y dar órdenes en grupo (64 bloques).

### Combate e IA
- IA separada para melee, arco y ballesta.
- Escudo reactivo, flanqueo, retirada con poca vida y tácticas de terreno elevado.
- 5 rangos con stats, texturas y progresión por XP.
- Golpes críticos en rangos Aprendiz y Experto.
- Comportamiento ampliado durante raids.
- Defensa de aldeanos y golems (salvajes y contratados en guardia/patrulla).

### PvP configurable
- Toggle **Jugadores: ON/OFF** en FOLLOW, GUARD y PATROL (GUI del mercenario).
- **OFF:** ignora golpes al dueño; sin PvP territorial en guardia/patrulla.
- **ON:** defiende al dueño al primer golpe (estilo lobo) + PvP territorial.
- Siempre se defiende si lo golpean; siempre ayuda si el dueño ataca primero.

### Huevo de spawn
- Huevo de mercenario en la pestaña creativa **Huevos de generación**.
- Textura propia verde (estilo esmeralda).

### Mundo y persistencia
- Spawn en aldeas y campamentos de mercenarios en el mundo.
- Mercenarios persistentes (no despawn por distancia).
- Curación pasiva cerca de fogatas en orden NEUTRAL.

### Multijugador
- Compatible en LAN/servidor; probado con múltiples jugadores.

### Requisitos
- Minecraft **1.21.11**
- Fabric Loader **≥ 0.18.4**
- Fabric API
- Java **21**
