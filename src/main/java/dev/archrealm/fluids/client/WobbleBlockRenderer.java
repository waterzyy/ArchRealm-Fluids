package dev.archrealm.fluids.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.archrealm.fluids.registry.ModBlocks;
import dev.archrealm.fluids.world.WobbleBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the slime and honey blocks as squashing, stretching jelly cubes.
 *
 * <p>The whole geometry is six quads, so the cost per block is trivial, and the deformation is
 * derived from the wobble value the block entity already keeps. Because the block itself still has a
 * normal model, the texture stays sharp and lighting is the ordinary block lighting.
 */
public final class WobbleBlockRenderer
		implements BlockEntityRenderer<WobbleBlockEntity, WobbleBlockRenderer.WobbleRenderState> {
	private static final Identifier SLIME_TEXTURE = Identifier.withDefaultNamespace("textures/block/slime_block.png");
	private static final Identifier HONEY_TEXTURE = Identifier.withDefaultNamespace("textures/block/honey_block.png");

	public static final class WobbleRenderState extends BlockEntityRenderState {
		public float wobble;
		public float phase;
		public Identifier texture = SLIME_TEXTURE;
	}

	@Override
	public WobbleRenderState createRenderState() {
		return new WobbleRenderState();
	}

	@Override
	public void extractRenderState(WobbleBlockEntity blockEntity, WobbleRenderState state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
		state.wobble = blockEntity.wobble();
		state.phase = blockEntity.phase() + partialTicks * 0.7F;
		state.texture = blockEntity.getBlockState().is(ModBlocks.WOBBLE_HONEY) ? HONEY_TEXTURE : SLIME_TEXTURE;
	}

	@Override
	public void submit(WobbleRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		float wobble = state.wobble;
		float squash = wobble <= 0.001F ? 1.0F : 1.0F - 0.3F * wobble * Mth.abs(Mth.sin(state.phase));
		float bulge = 1.0F / Mth.sqrt(squash);
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.scale(bulge, squash, bulge);
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(state.texture),
				(pose, buffer) -> cube(pose, buffer, light));
	}

	private static void cube(PoseStack.Pose pose, VertexConsumer buffer, int light) {
		// +Z
		quad(pose, buffer, light, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1);
		// -Z
		quad(pose, buffer, light, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, -1);
		// +X
		quad(pose, buffer, light, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0);
		// -X
		quad(pose, buffer, light, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, -1, 0, 0);
		// +Y
		quad(pose, buffer, light, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 1, 0);
		// -Y
		quad(pose, buffer, light, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3,
			float nx, float ny, float nz) {
		vertex(pose, buffer, light, x0, y0, z0, 0.0F, 0.0F, nx, ny, nz);
		vertex(pose, buffer, light, x1, y1, z1, 1.0F, 0.0F, nx, ny, nz);
		vertex(pose, buffer, light, x2, y2, z2, 1.0F, 1.0F, nx, ny, nz);
		vertex(pose, buffer, light, x3, y3, z3, 0.0F, 1.0F, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light,
			float x, float y, float z, float u, float v, float nx, float ny, float nz) {
		buffer.addVertex(pose, x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
