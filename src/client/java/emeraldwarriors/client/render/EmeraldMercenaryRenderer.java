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
        state.texture = Identifier.fromNamespaceAndPath(
                Emerald_Warriors.MOD_ID,
                "textures/entity/mercenary/" + skinId + "/" + skinId + "_" + rankSuffix + ".png"
        );

        // Seleccionar modelo ANTES de super para que la geometría sea correcta
        this.model = isSlim ? this.alexModel : this.steveModel;

        super.extractRenderState(entity, state, partialTick);

        Emerald_Warriors.LOGGER.info("[EmeraldMercenaryRenderer] Entity {} skinId={} slim={} texture={}",
                entity.getId(), skinId, isSlim, state.texture);
    }

    @Override
    public Identifier getTextureLocation(MercenaryRenderState state) {
        // Seleccionar el modelo correcto para ESTA entidad justo cuando se pide su textura
        // (getTextureLocation se llama dentro del render, antes de dibujar)
        this.model = state.slim ? this.alexModel : this.steveModel;
        return state.texture;
    }
}
