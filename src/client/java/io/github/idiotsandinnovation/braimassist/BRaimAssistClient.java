package io.github.idiotsandinnovation.braimassist;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class BRaimAssistClient implements ClientModInitializer {
	private static final String MOD_ID = "br_aimassist";
	private static final double MAX_TARGET_DISTANCE = 20.0;
	private static final double MAX_CROSSHAIR_ANGLE_DEGREES = 45.0;
	private static final float AIM_STRENGTH = 0.50F;

	private static final KeyMapping AIM_KEY = new KeyMapping(
			"key.br_aimassist.aim",
			InputConstants.KEY_R,
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))
	);

	private static Entity target;

	@Override
	public void onInitializeClient() {
		KeyMappingHelper.registerKeyMapping(AIM_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(BRaimAssistClient::onEndClientTick);
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "target_hud"),
				BRaimAssistClient::renderHud
		);
	}

	private static void onEndClientTick(Minecraft client) {
		Player player = client.player;
		if (player == null || client.level == null) {
			target = null;
			return;
		}

		if (!AIM_KEY.isDown()) {
			target = null;
			return;
		}

		if (target == null) {
			target = findTarget(player);
		}
		if (target != null && (!target.isAlive() || target.isRemoved())) {
			target = null;
			return;
		}
		if (target != null) {
			guideView(player, target);
		}
	}

	private static Entity findTarget(Player player) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return null;
		}

		Vec3 eyePosition = player.getEyePosition();
		Vec3 lookDirection = player.getLookAngle();
		double minimumLookDot = Math.cos(Math.toRadians(MAX_CROSSHAIR_ANGLE_DEGREES));
		double bestLookDot = minimumLookDot;
		double bestDistanceSquared = MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
		Entity bestTarget = null;
		AABB searchArea = player.getBoundingBox().inflate(MAX_TARGET_DISTANCE);
		List<LivingEntity> candidates = new ArrayList<>(level.players());
		candidates.addAll(level.getEntitiesOfClass(
				Mob.class,
				searchArea,
				entity -> entity.isAlive() && !entity.isSpectator()
		));

		for (LivingEntity candidate : candidates) {
			if (candidate == player || !candidate.isAlive() || candidate.isSpectator() || !player.hasLineOfSight(candidate)) {
				continue;
			}

			Vec3 toCandidate = closestPointOnHitbox(candidate, eyePosition).subtract(eyePosition);
			double distanceSquared = toCandidate.lengthSqr();
			if (distanceSquared > bestDistanceSquared) {
				continue;
			}

			double lookDot = distanceSquared == 0.0
					? 1.0
					: lookDirection.dot(toCandidate) / Math.sqrt(distanceSquared);
			if (lookDot > bestLookDot || (lookDot == bestLookDot && distanceSquared < bestDistanceSquared)) {
				bestTarget = candidate;
				bestLookDot = lookDot;
				bestDistanceSquared = distanceSquared;
			}
		}

		return bestTarget;
	}

	private static void guideView(Player player, Entity entity) {
		Vec3 eyePosition = player.getEyePosition();
		Vec3 targetPosition = closestPointOnHitbox(entity, eyePosition);
		if (targetPosition.equals(eyePosition)) {
			targetPosition = entity.getBoundingBox().getCenter();
		}
		double dx = targetPosition.x - eyePosition.x;
		double dy = targetPosition.y - eyePosition.y;
		double dz = targetPosition.z - eyePosition.z;
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float targetPitch = (float) -(Mth.atan2(dy, horizontalDistance) * Mth.RAD_TO_DEG);
		player.setYRot(player.getYRot() + Mth.wrapDegrees(targetYaw - player.getYRot()) * AIM_STRENGTH);
		player.setXRot(Mth.clamp(
				player.getXRot() + (targetPitch - player.getXRot()) * AIM_STRENGTH,
				-90.0F,
				90.0F
		));
	}

	private static Vec3 closestPointOnHitbox(Entity entity, Vec3 position) {
		AABB hitbox = entity.getBoundingBox();
		return new Vec3(
				Mth.clamp(position.x, hitbox.minX, hitbox.maxX),
				Mth.clamp(position.y, hitbox.minY, hitbox.maxY),
				Mth.clamp(position.z, hitbox.minZ, hitbox.maxZ)
		);
	}

	private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		if (target == null) {
			return;
		}

		String name = target instanceof Player
				? target.getName().getString()
				: target.getType().getDescription().getString();
		Component label = Component.translatable("hud.br_aimassist.target", name);
		Minecraft client = Minecraft.getInstance();
		int x = 8;
		int y = graphics.guiHeight() - client.font.lineHeight - 8;
		int width = client.font.width(label);

		graphics.fill(x - 4, y - 4, x + width + 4, y + client.font.lineHeight + 4, 0x99000000);
		graphics.text(client.font, label, x, y, 0xFFFFFFFF, true);
	}
}
