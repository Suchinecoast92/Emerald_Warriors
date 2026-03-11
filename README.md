# Emerald Warriors

Un mod de Minecraft 1.21.11 que añade mercenarios contratables con IA de combate, equipamiento y sistema de rangos.

## Contexto del Proyecto

Este mod está inspirado en el sistema de mercenarios de TheAncientGuard, adaptado a la arquitectura moderna de Minecraft 1.21.11 con Mojang mappings y el nuevo sistema de EntityRenderStates.

## Características Implementadas ✅

### Sistema de Contratación
- **Contratación de Mercenarios**: Los jugadores pueden contratar mercenarios mediante un sistema de oferta contractual
- **Timeout de Contrato**: Las ofertas expiran después de 200 ticks (10 segundos)
- **Persistencia del Dueño**: Los mercenarios recuerdan a su dueño a través de UUIDs

### Sistema de Rangos
- **5 Rangos Disponibles**: RECRUIT, SOLDIER, SERGEANT, CAPTAIN, GENERAL
- **Sistema de Experiencia**: Los mercenarios ganan EXP por combate
- **Texturas por Rango**: Cada rango tiene texturas únicas (cobre, hierro, oro, esmeralda, diamante)

### IA de Combate
- **Melee Attack Goal**: Ataque cuerpo a cuerpo con animación de swing
- **Protect Owner Goal**: Defiende al dueño cuando es atacado
- **Follow Owner Goal**: Sigue al dueño cuando se aleja
- **Target Acquisition**: Ataca mobs hostiles automáticamente
- **Combat Roles**: Sistema de roles (GUARDIAN, ARCHER) para diferentes estilos de combate

### Sistema de Inventario
- **Inventario Personal**: 12 slots (arma, armadura, bolsa)
- **Equipamiento Sincronizado**: Items equipados se renderizan en el modelo
- **GUI Personalizada**: Interfaz para gestionar inventario y ver estadísticas

### Sistema de Renderizado
- **Modelos Humanoides**: Soporte para modelos Steve y Alex (slim)
- **Render de Armas**: Las armas equipadas se muestran en las manos
- **Render de Armadura**: La armadura equipada se muestra sobre el modelo
- **Texturas Dinámicas**: Cambio de textura según skin ID y rango

### Sistema de Datos
- **Sync de Datos**: Sincronización cliente-servidor de estadísticas
- **Persistencia NBT**: Guardado/carga de datos del mercenario
- **Entity Data Accessors**: Acceso eficiente a datos sincronizados

## En Progreso 🚧

### Sistema de Experiencia y Niveles
- **Ganancia de EXP**: Por derrotar mobs hostiles
- **Sistema de Niveles**: Progresión entre rangos
- **Bonificaciones por Rango**: Mejoras de estadísticas según nivel

### Persistencia Avanzada
- **Persistencia de Inventario**: Guardar/cargar items del mercenario
- **Persistencia de Contrato**: Estado del contrato al reiniciar servidor
- **Backup de Datos**: Recuperación de datos corruptos

## Por Implementar 📋

### Sistema de Economía
- **Costos de Contratación**: Diferentes precios por rango
- **Pagos por Servicios**: Salarios periódicos
- **Sistema de Recontratación**: Renovación de contratos

### IA Avanzada
- **Archer AI**: Sistema de combate a distancia con arcos/ballestas
- **Tactical AI**: Estrategias de combate avanzadas
- **Formation AI**: Movimiento coordinado en grupos

### Sistema de Equipamiento
- **Durabilidad de Items**: Desgaste natural del equipo
- **Reparación de Equipamiento**: Sistema de mantenimiento
- **Mejoras de Equipo**: Encantamientos y mejoras

### Interfaz de Usuario
- **Panel de Comandos**: GUI para dar órdenes específicas
- **Sistema de Gestión**: Administración de múltiples mercenarios
- **Estadísticas Detalladas**: Informes de combate y progreso

### Sistema de Misiones
- **Quest System**: Misiones asignables a mercenarios
- **Recompensas**: Premios por completar objetivos
- **Reputación**: Sistema de fama/infamia

### Multiplayer
- **Clan System**: Agrupación de jugadores con mercenarios compartidos
- **PvP Balance**: Balance para combate entre jugadores con mercenarios
- **Leaderboards**: Tablas de clasificación competitivas

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
├── inventory/             # Sistema de inventario
├── mercenary/             # Enums y datos
└── menu/                  # Menus y containers
```

### Dependencias
- **Minecraft 1.21.11**: Versión objetivo
- **Fabric Loader**: Carga de mods
- **Mojang Mappings**: Nomenclatura oficial
- **EntityRenderStates**: Nuevo sistema de renderizado

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

**Estado Actual**: Alpha funcional con sistema básico de mercenarios implementado.
