package emeraldwarriors.client.model;

import emeraldwarriors.client.render.EmeraldMercenaryRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;

@Environment(EnvType.CLIENT)
public class MercenaryArmorModel<S extends HumanoidRenderState> extends HumanoidModel<S> {

    public MercenaryArmorModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(S state) {
        super.setupAnim(state);

        if (state instanceof EmeraldMercenaryRenderer.MercenaryRenderState ms && ms.contractAdmiring) {
            this.head.xRot = 0.5F;
            this.head.yRot = 0.0F;

            ModelPart arm = (state.mainArm == HumanoidArm.RIGHT) ? this.rightArm : this.leftArm;
            arm.yRot = (state.mainArm == HumanoidArm.RIGHT) ? -0.5F : 0.5F;
            arm.xRot = -0.9F;
        }
    }
}
