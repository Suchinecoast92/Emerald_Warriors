package emeraldwarriors.client.gui;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.inventory.MercenaryMenu;
import emeraldwarriors.mercenary.MercenaryOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Inventario del mercenario — estilo vanilla.
 *
 * Panel superior (92px): equipamiento del mercenario
 *   Columna izq: armadura (casco, peto, pantalones, botas)
 *   Segunda col: arma principal + mano izquierda
 *   Zona derecha: 9 slots mochila (3x3)
 * Panel inferior: inventario del jugador + hotbar
 */
public class MercenaryScreen extends AbstractContainerScreen<MercenaryMenu> {
    private static final int TERMINATE_BUTTON_WIDTH = 90;
    private static final int TERMINATE_BUTTON_HEIGHT = 20;
    private static final int PLAYER_TOGGLE_SIZE = 12;


    // Colores vanilla (generic_54.png)
    private static final int PANEL_BG     = 0xFFC6C6C6;
    private static final int BORDER_LITE  = 0xFFFFFFFF;
    private static final int BORDER_DARK  = 0xFF555555; 
    private static final int SLOT_BG      = 0xFF8B8B8B;
    private static final int SLOT_DARK    = 0xFF373737;
    private static final int SLOT_LITE    = 0xFFFFFFFF;
    private static final int SEPARATOR    = 0xFF888888;

    private static final int GUI_WIDTH     = 176;
    private static final int EQUIP_HEIGHT  = 99;
    private static final int PLAYER_HEIGHT = 101;
    private static final int GUI_HEIGHT    = EQUIP_HEIGHT + PLAYER_HEIGHT;

    // Sprites HUD vanilla
    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_FULL      = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_HALF      = Identifier.withDefaultNamespace("hud/heart/half");
    private static final Identifier XP_BG           = Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier XP_PROGRESS     = Identifier.withDefaultNamespace("hud/experience_bar_progress");

    private Button terminateButton;
    private Button playerTargetsButton;
    private int pendingCloseTicks = -1;

    public MercenaryScreen(MercenaryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = EQUIP_HEIGHT + 2;

        int buttonX = this.leftPos + (this.imageWidth - TERMINATE_BUTTON_WIDTH) / 2;
        int buttonY = this.topPos + this.imageHeight + 8;
        this.terminateButton = this.addRenderableWidget(Button.builder(Component.literal("Finalizar"), button -> {
                    boolean wasConfirmPending = this.menu.isTerminateConfirmPending();
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MercenaryMenu.BUTTON_TERMINATE_CONTRACT);
                        if (wasConfirmPending) {
                            this.pendingCloseTicks = 6;
                        }
                    }
                })
                .bounds(buttonX, buttonY, TERMINATE_BUTTON_WIDTH, TERMINATE_BUTTON_HEIGHT)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Finalizar contrato")))
                .build());

        int playerToggleX = this.leftPos + 62;
        int playerToggleY = this.topPos + 56;
        this.playerTargetsButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MercenaryMenu.BUTTON_TOGGLE_PLAYER_TARGETS);
                    }
                })
                .bounds(playerToggleX, playerToggleY, PLAYER_TOGGLE_SIZE, PLAYER_TOGGLE_SIZE)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Permitir combate contra jugadores en Guardia/Patrulla")))
                .build());
        this.updateTerminateButton();
        this.updatePlayerTargetsButton();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.pendingCloseTicks >= 0) {
            this.pendingCloseTicks--;
            if (this.pendingCloseTicks <= 0 && this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.closeContainer();
                this.pendingCloseTicks = -1;
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Panel principal
        drawPanel(guiGraphics, x, y, GUI_WIDTH, GUI_HEIGHT);

        // Separador
        guiGraphics.fill(RenderPipelines.GUI, x + 7, y + EQUIP_HEIGHT - 1, x + GUI_WIDTH - 7, y + EQUIP_HEIGHT, SEPARATOR);

        // Fondo de cada slot
        for (Slot slot : this.menu.slots) {
            drawSlotBg(guiGraphics, x + slot.x - 1, y + slot.y - 1);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBlurredBackground(guiGraphics);
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.updateTerminateButton();
        this.updatePlayerTargetsButton();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Título: ya viene como "Nombre : Rango" desde la entidad
        String fullTitle = this.title.getString();
        int titleWidth = this.font.width(fullTitle);
        int titleX = (this.imageWidth - titleWidth) / 2;
        g.drawString(this.font, fullTitle, titleX, this.titleLabelY, 0xFF404040, false);

        // Etiqueta "Inventario"
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF404040, false);

        // --- Zona de información del mercenario (coordenadas relativas al panel) ---
        int infoX = 62;

        // Fila de corazones
        int heartsY = 18;

        // Valores base desde ContainerData sincronizado por el servidor
        int health = this.menu.getMercHealth();
        int maxHealth = this.menu.getMercMaxHealth();

        // Intentar usar la entidad cliente para reflejar la vida en tiempo real
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            int entityId = this.menu.getMercEntityId();
            if (entityId != 0) {
                Entity e = minecraft.level.getEntity(entityId);
                if (e instanceof EmeraldMercenaryEntity merc) {
                    health = (int) Math.ceil(merc.getHealth());
                    maxHealth = (int) Math.ceil(merc.getMaxHealth());
                }
            }
        }
        if (maxHealth <= 0) {
            maxHealth = 1;
        }
        int totalHearts = (maxHealth + 1) / 2;
        int heartsToDraw = Math.min(totalHearts, 10);
        for (int i = 0; i < heartsToDraw; i++) {
            int hx = infoX + i * 9;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, hx, heartsY, 9, 9);
            int heartHealth = (i + 1) * 2;
            if (health >= heartHealth) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, hx, heartsY, 9, 9);
            } else if (health == heartHealth - 1) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, hx, heartsY, 9, 9);
            }
        }

        // Etiqueta "EXP"
        g.drawString(this.font, "EXP", infoX, 29, 0xFF404040, false);

        // Barra de EXP
        int xpY = 39;
        int barWidth = 90;
        int barHeight = 5;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BG, infoX, xpY, barWidth, barHeight);
        int exp = this.menu.getMercExp();
        int maxExp = this.menu.getMercMaxExp();
        if (maxExp <= 0) {
            maxExp = 1;
        }
        if (exp > 0) {
            float pct = Math.min(1.0f, Math.max(0.0f, (float) exp / (float) maxExp));
            int filled = (int) (barWidth * pct);
            if (filled > 0) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, infoX, xpY, filled, barHeight);
            }
        }

        if (shouldShowPlayerTargetsToggle()) {
            int pvpLabelX = infoX + 20;
            int pvpLabelY = 57;
            g.drawString(this.font, "Jugadores:", pvpLabelX, pvpLabelY, 0xFF404040, false);
            boolean enabled = this.menu.allowPlayerTargets();
            String state = enabled ? "ON" : "OFF";
            int stateColor = enabled ? 0xFF2E7D32 : 0xFFAA2222;
            g.drawString(this.font, state, pvpLabelX + 63, pvpLabelY, stateColor, false);
        }

    }

    private boolean shouldShowPlayerTargetsToggle() {
        int ordinal = this.menu.getOrderOrdinal();
        if (ordinal < 0 || ordinal >= MercenaryOrder.values().length) {
            return false;
        }
        MercenaryOrder order = MercenaryOrder.values()[ordinal];
        return order == MercenaryOrder.GUARD || order == MercenaryOrder.PATROL;
    }

    private void updateTerminateButton() {
        if (this.terminateButton == null) {
            return;
        }
        boolean confirmPending = this.menu.isTerminateConfirmPending();
        if (confirmPending) {
            int secondsLeft = Math.max(1, (this.menu.getTerminateConfirmTicksRemaining() + 19) / 20);
            this.terminateButton.setMessage(Component.literal("Confirmar (" + secondsLeft + "s)"));
        } else {
            this.terminateButton.setMessage(Component.literal("Finalizar"));
        }
        this.terminateButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                confirmPending ? "Confirmar finalizacion del contrato" : "Finalizar contrato"
        )));
        this.terminateButton.active = this.pendingCloseTicks < 0;
    }

    private void updatePlayerTargetsButton() {
        if (this.playerTargetsButton == null) {
            return;
        }
        boolean show = shouldShowPlayerTargetsToggle();
        this.playerTargetsButton.visible = show;
        this.playerTargetsButton.active = show && this.pendingCloseTicks < 0;
        if (!show) {
            return;
        }
        boolean enabled = this.menu.allowPlayerTargets();
        this.playerTargetsButton.setMessage(Component.literal(enabled ? "x" : ""));
        this.playerTargetsButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                enabled ? "Jugadores: ON" : "Jugadores: OFF"
        )));
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(RenderPipelines.GUI, x, y, x + w, y + h, PANEL_BG);
        g.fill(RenderPipelines.GUI, x, y, x + w - 1, y + 1, BORDER_LITE);
        g.fill(RenderPipelines.GUI, x, y, x + 1, y + h - 1, BORDER_LITE);
        g.fill(RenderPipelines.GUI, x + 1, y + h - 1, x + w, y + h, BORDER_DARK);
        g.fill(RenderPipelines.GUI, x + w - 1, y + 1, x + w, y + h, BORDER_DARK);
    }

    private void drawSlotBg(GuiGraphics g, int sx, int sy) {
        g.fill(RenderPipelines.GUI, sx, sy, sx + 18, sy + 18, SLOT_DARK);
        g.fill(RenderPipelines.GUI, sx + 1, sy + 1, sx + 17, sy + 17, SLOT_LITE);
        g.fill(RenderPipelines.GUI, sx + 1, sy + 1, sx + 16, sy + 16, SLOT_BG);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
