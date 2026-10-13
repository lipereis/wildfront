package dev.wildfront.client;

import dev.wildfront.Wildfront;
import dev.wildfront.entity.GuardEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Draws a guard as a player-shaped figure wearing the guard skin. The model is the game's own
 * player model with four-pixel arms, which is the layout the skin was painted for, and all
 * animation is the game's.
 */
public class GuardRenderer extends HumanoidMobRenderer<GuardEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = Wildfront.id("textures/entity/guard.png");

	public GuardRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(GuardEntity guard, HumanoidArm arm) {
		// An archer that has a target holds its bow drawn, like a skeleton.
		if (guard.getMainArm() == arm && guard.isAggressive() && guard.isArcher()) {
			return HumanoidModel.ArmPose.BOW_AND_ARROW;
		}

		return super.getArmPose(guard, arm);
	}
}
