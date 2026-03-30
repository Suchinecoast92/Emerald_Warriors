package emeraldwarriors.client.gui;

import emeraldwarriors.entity.EmeraldMercenaryEntity;
import emeraldwarriors.inventory.MercenaryMenu;
import net.minecraft.client.Minecraft;
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

    private static final String[] RANK_NAMES = new String[] {
            "Recluta", "Soldado", "Centinela", "Veterano", "Guardián antiguo"
    };

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
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Panel principal
        drawPanel(guiGraphics, x, y, GUI_WIDTH, GUI_HEIGHT);

        // Separador
        guiGraphics.fill(x + 7, y + EQUIP_HEIGHT - 1, x + GUI_WIDTH - 7, y + EQUIP_HEIGHT, SEPARATOR);

        // Fondo de cada slot
        for (Slot slot : this.menu.slots) {
            drawSlotBg(guiGraphics, x + slot.x - 1, y + slot.y - 1);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Título: "Mercenario : Recluta"
        int rankOrdinal = this.menu.getMercRankOrdinal();
        String rankName = (rankOrdinal >= 0 && rankOrdinal < RANK_NAMES.length) ? RANK_NAMES[rankOrdinal] : "Recluta";
        String fullTitle = this.title.getString() + " : " + rankName;
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
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.fill(x, y, x + w - 1, y + 1, BORDER_LITE);
        g.fill(x, y, x + 1, y + h - 1, BORDER_LITE);
        g.fill(x + 1, y + h - 1, x + w, y + h, BORDER_DARK);
        g.fill(x + w - 1, y + 1, x + w, y + h, BORDER_DARK);
    }

    private void drawSlotBg(GuiGraphics g, int sx, int sy) {
        g.fill(sx, sy, sx + 18, sy + 18, SLOT_DARK);
        g.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT_LITE);
        g.fill(sx + 1, sy + 1, sx + 16, sy + 16, SLOT_BG);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
