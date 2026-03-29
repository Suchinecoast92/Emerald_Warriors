package emeraldwarriors.client.render;

import emeraldwarriors.Emerald_Warriors;
import emeraldwarriors.client.model.MercenaryPlayerModel;
import emeraldwarriors.entity.EmeraldMercenaryEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;

import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public class EmeraldMercenaryRenderer extends HumanoidMobRenderer<EmeraldMercenaryEntity, EmeraldMercenaryRenderer.MercenaryRenderState, MercenaryPlayerModel<EmeraldMercenaryRenderer.MercenaryRenderState>> {
    private static final Identifier FALLBACK_TEXTURE = Identifier.fromNamespaceAndPath(
            Emerald_Warriors.MOD_ID, "textures/entity/mercenary/m1/m1_cobre.png");

    private final MercenaryPlayerModel<MercenaryRenderState> steveModel;
    private final MercenaryPlayerModel<MercenaryRenderState> alexModel;

    public EmeraldMercenaryRenderer(EntityRendererProvider.Context context) {
        super(context, new MercenaryPlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.steveModel = this.getModel();
        this.alexModel = new MercenaryPlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        // Armor layer — uses the player armor model set so vanilla armor renders on the mercenary
        ArmorModelSet<HumanoidModel<MercenaryRenderState>> armorModels = ArmorModelSet.bake(
                ModelLayers.PLAYER_ARMOR,
                context.getModelSet(),
                HumanoidModel::new
        );
        this.addLayer(new HumanoidArmorLayer<>(this, armorModels, context.getEquipmentRenderer()));
    }

    // Estado de render per-entidad: cada entidad tiene su propia instancia
    private static final Map<Integer, Identifier> LOGGED_TEXTURES = new HashMap<>();

    public static class MercenaryRenderState extends HumanoidRenderState {
        public Identifier texture = FALLBACK_TEXTURE;
        public boolean slim = false;
    }

    @Override
    public MercenaryRenderState createRenderState() {
        return new MercenaryRenderState();
    }

    @Override
    public void extractRenderState(EmeraldMercenaryEntity entity, MercenaryRenderState state, float partialTick) {
        String skinId = entity.getSkinId();
        if (skinId == null || skinId.isEmpty()) {
            skinId = "m1";
        }

        String rankSuffix = entity.getRankTextureSuffix();
        if (rankSuffix == null || rankSuffix.isEmpty()) {
            rankSuffix = "cobre";
        }

        boolean isSlim = skinId.endsWith("f");
        state.slim = isSlim;
        Identifier newTexture = Identifier.fromNamespaceAndPath(
                Emerald_Warriors.MOD_ID,
                "textures/entity/mercenary/" + skinId + "/" + skinId + "_" + rankSuffix + ".png"
        );
        state.texture = newTexture;

        // Seleccionar modelo ANTES de super para que la geometría sea correcta
        this.model = isSlim ? this.alexModel : this.steveModel;

        super.extractRenderState(entity, state, partialTick);

        // === Forzar pose de bloqueo con escudo ===
        // Cuando el mercenario está "usando" un escudo (ShieldAgainstCreeperGoal startUsingItem),
        // nos aseguramos de que el brazo correspondiente use la pose BLOCK para que el escudo
        // se vea al frente como en Alex.
        if (entity.isUsingItem()) {
            InteractionHand usedHand = entity.getUsedItemHand();

            // Escudo en mano principal
            if (usedHand == InteractionHand.MAIN_HAND && entity.getMainHandItem().is(Items.SHIELD)) {
                if (state.mainArm == HumanoidArm.RIGHT) {
                    state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
                } else {
                    state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
                }
            }

            // Escudo en offhand (caso típico: espada mano principal, escudo en la otra)
            if (usedHand == InteractionHand.OFF_HAND && entity.getOffhandItem().is(Items.SHIELD)) {
                if (state.mainArm == HumanoidArm.RIGHT) {
                    // Main RIGHT -> escudo está en brazo izquierdo
                    state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
                } else {
                    state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
                }
            }
        }

        Identifier prev = LOGGED_TEXTURES.get(entity.getId());
        if (prev == null || !prev.equals(newTexture)) {
            LOGGED_TEXTURES.put(entity.getId(), newTexture);
            Emerald_Warriors.LOGGER.info("[EmeraldMercenaryRenderer] Entity {} skinId={} slim={} texture={}",
                    entity.getId(), skinId, isSlim, newTexture);
        }
    }

    @Override
    public Identifier getTextureLocation(MercenaryRenderState state) {
        // Seleccionar el modelo correcto para ESTA entidad justo cuando se pide su textura
        // (getTextureLocation se llama dentro del render, antes de dibujar)
        this.model = state.slim ? this.alexModel : this.steveModel;
        return state.texture;
    }
}
