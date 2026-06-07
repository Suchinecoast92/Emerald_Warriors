# Changelog

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
