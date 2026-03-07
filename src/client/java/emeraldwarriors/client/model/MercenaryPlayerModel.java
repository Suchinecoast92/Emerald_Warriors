package emeraldwarriors.client.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Modelo de mercenario basado en HumanoidModel.
 *
 * Usamos la geometría bakeada desde ModelLayers.PLAYER / PLAYER_SLIM
 * para que las texturas de mercenarios encajen al 100 % con el layout
 * de skin de jugador (Steve/Alex), pero sin depender de PlayerModel
 * ni AvatarRenderState, que internamente bypasean getTextureLocation().
 *
 * El parámetro de tipo S permite que el renderer use una subclase
 * de HumanoidRenderState (como MercenaryRenderState) sin conflictos
 * de genéricos con HumanoidMobRenderer.
 */
@Environment(EnvType.CLIENT)
public class MercenaryPlayerModel<S extends HumanoidRenderState> extends HumanoidModel<S> {

    private final boolean slim;

    public MercenaryPlayerModel(ModelPart root, boolean slim) {
        super(root);
        this.slim = slim;
    }

    public boolean isSlim() {
        return this.slim;
    }
}
