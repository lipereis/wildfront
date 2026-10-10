package dev.wildfront;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs under fabric-loader-junit, so Fabric Loader has parsed and validated fabric.mod.json
 * before these tests execute.
 */
class ModMetadataTest {
	private static ModMetadata metadata;

	@BeforeAll
	static void loadMetadata() {
		ModContainer container = FabricLoader.getInstance().getModContainer(Wildfront.MOD_ID)
				.orElseThrow(() -> new AssertionError("Fabric Loader did not discover mod '" + Wildfront.MOD_ID + "'"));
		metadata = container.getMetadata();
	}

	@Test
	void nameIsSet() {
		assertEquals("Wildfront", metadata.getName());
	}

	@Test
	void versionPlaceholderIsExpanded() {
		String version = metadata.getVersion().getFriendlyString();
		assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), "unexpected version: " + version);
	}

	@Test
	void requiredDependenciesAreDeclared() {
		var dependencyIds = metadata.getDependencies().stream().map(dependency -> dependency.getModId()).toList();
		assertTrue(dependencyIds.containsAll(java.util.List.of("fabricloader", "minecraft", "java", "fabric-api")),
				"declared dependencies: " + dependencyIds);
	}

	@Test
	void identifiersUseModNamespace() {
		assertDoesNotThrow(() -> assertEquals("wildfront:example", Wildfront.id("example").toString()));
	}
}
