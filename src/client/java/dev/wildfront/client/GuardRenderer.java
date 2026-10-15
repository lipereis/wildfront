package dev.wildfront.client;

import dev.wildfront.Wildfront;
import dev.wildfront.entity.GuardEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Draws a guard as a player-shaped figure wearing the guard skin. The model is the game's own
 * player model with four-pixel arms, which is the layout the skin was painted for, and all
 * animation is the game's. A camp chief wears a skin of its own. Armour is drawn as on a player.
 */
public class GuardRenderer extends HumanoidMobRenderer<GuardEntity, GuardRenderer.State, HumanoidModel<GuardRenderer.State>> {
	private static final Identifier TEXTURE = Wildfront.id("textures/entity/guard.png");
	private static final Identifier CHIEF_TEXTURE = Wildfront.id("textures/entity/guard_chief.png");

	/** What is drawn of a guard; a chief has its own skin. */
	public static class State extends HumanoidRenderState {
		public boolean chief;
	}

	public GuardRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
				context.getEquipmentRenderer()));
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.chief ? CHIEF_TEXTURE : TEXTURE;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GuardEntity guard, State state, float partialTick) {
		super.extractRenderState(guard, state, partialTick);
		state.chief = guard.isChief();
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
