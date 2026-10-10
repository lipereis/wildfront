package dev.wildfront.gametest;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import dev.wildfront.discovery.ActiveCache;
import dev.wildfront.discovery.DiscoveryManager;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

final class GameTestSupport {
	private static final AtomicInteger PLAYER_NUMBER = new AtomicInteger();

	private GameTestSupport() {
	}

	/**
	 * Fills the floor of the 8x8x8 test area with stone, so every column has solid ground at y = 0.
	 */
	static void buildFloor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/**
	 * Adds a player to the server, standing at the given position of the test area. Callers must
	 * pass it to {@link #remove} in a finally block, or it would stay online for other tests.
	 *
	 * <p>Built like {@code GameTestHelper.makeMockServerPlayerInLevel()}, except that this is a
	 * plain {@link ServerPlayer}: the vanilla mock always reports creative mode, which the event
	 * system rightly ignores.
	 */
	static ServerPlayer addPlayer(GameTestHelper helper, GameType gameMode, int x, int y, int z) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "wf-test-" + PLAYER_NUMBER.incrementAndGet()), false);
		ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);

		BlockPos position = helper.absolutePos(new BlockPos(x, y, z));
		player.setGameMode(gameMode);
		player.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
		return player;
	}

	/**
	 * Removes every forgotten cache in and around the test area and stops tracking it. The test
	 * world is kept between runs, so a cache left behind would still count as active next time.
	 */
	static void removeCaches(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(4, 4, 4));

		for (ActiveCache cache : DiscoveryManager.activeCaches(level)) {
			if (cache.pos().closerThan(center, 12)) {
				if (level.getBlockEntity(cache.pos()) instanceof BarrelBlockEntity barrel) {
					// Without its loot table the barrel is empty and drops nothing.
					barrel.setLootTable(null);
				}

				level.removeBlock(cache.pos(), false);
			}
		}

		DiscoveryManager.tick(level, level.getGameTime());
	}

	static void remove(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer player : players) {
			if (player != null) {
				helper.getLevel().getServer().getPlayerList().remove(player);
			}
		}
	}
}
