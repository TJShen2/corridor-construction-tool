package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.command.EmbankmentCommand;
import com.example.command.FencingCommand;
import com.example.command.PillarCommand;
import com.example.command.TunnelCommand;
import com.example.command.argument.CatenaryTypeArgumentType;
import com.example.command.argument.PillarOrientationArgumentType;

/**
 * This class is the main class of CorridorConstructionTool that initializes the mod.
 * @author TJ Shen
 * @version 1.0.0
 */
public class CorridorConstructionTool implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
  public static final Logger LOGGER = LoggerFactory.getLogger("CorridorConstructionTool");

	// TODO: Help/documentation in-game or on the web

	/**
	 * Perform initialization tasks.
	 * This code runs as soon as Minecraft is in a mod-load-ready state. However, some things (like resources) may still be uninitialized. Proceed with mild caution.
	 */
	@Override
	public void onInitialize() {
		ArgumentTypeRegistry.registerArgumentType(new Identifier("corridor_construction_tool", "catenary_type"), CatenaryTypeArgumentType.class, ConstantArgumentSerializer.of(CatenaryTypeArgumentType::catenaryType));
		ArgumentTypeRegistry.registerArgumentType(new Identifier("corridor_construction_tool", "pillar_orientation"), PillarOrientationArgumentType.class, ConstantArgumentSerializer.of(PillarOrientationArgumentType::orientation));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> EmbankmentCommand.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TunnelCommand.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FencingCommand.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> PillarCommand.register(dispatcher));

		LOGGER.info("Corridor construction tool has been initialized!");
	}
}
