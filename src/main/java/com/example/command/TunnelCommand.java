package com.example.command;

import com.example.CorridorConstructionConstants;
import com.example.function.Functions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.command.CommandArgParser;
import com.sk89q.worldedit.internal.util.Substring;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BlockTypes;

import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;

/**
 * This class represents a brigadier command that creates a tunnel above a track using the WorldEdit API.
 * Tunnel construction is dynamic: the height of the walls changes based on the height of the surrounding terrain, and a tunnel ceiling is only built on sections of track bed that are underground.
 * @author TJ Shen
 * @version 1.0.0
*/
public class TunnelCommand {
	/**
   * Registers the command with brigadier.
   * @param dispatcher the dispatcher used to register the command
  */
	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("tunnel")
				.requires(source -> source.hasPermissionLevel(2)) // Must be a game master to use the command. Command will not
																													// show up in tab completion or execute to non operators or
																													// any operator that is permission level 1.
				.then(argument("tunnelHeight", IntegerArgumentType.integer())
				.then(argument("withSchematic", BoolArgumentType.bool())
				.then(argument("maskPatternInput", StringArgumentType.greedyString()).suggests(new SuggestionProvider<ServerCommandSource>() {
          @Override
          public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
            String input = Functions.safeGetArgument(context, "maskPatternInput", String.class);

            if (input == null) {
              return Suggestions.empty();
            } else {
              String currentInput = Arrays.asList(Arrays.asList(input.split(" ")).getLast().split(",")).getLast();
              int start = Math.max(context.getInput().lastIndexOf(","), context.getInput().lastIndexOf(" ")) + 1;
              List<String> suggestions = WorldEdit.getInstance().getMaskFactory().getSuggestions(currentInput);

              SuggestionsBuilder builder2 = new SuggestionsBuilder(context.getInput(), start);
              suggestions.stream().forEach(e -> builder2.suggest(e));
              return builder2.buildFuture();
            }
          }
        })
				.executes(ctx -> createTunnel(ctx.getSource(), getInteger(ctx, "tunnelHeight"), getBool(ctx, "withSchematic"), getString(ctx, "maskPatternInput")))))));
	}

	/**
	 * Creates a tunnel above a track within the player's region selection.
	 * @param source the command source that this command is being run from
	 * @param tunnelHeight the required clearance above the track bed, in metres
	 * @param withSchematic whether to use a clipboard pattern loaded from a schematic for the tunnelPattern
	 * @param maskPatternInputString contains the following arguments, in order, separated by a space:
	 * trackMask: a mask containing the block(s) the track bed is made of
	 * tunnelPattern: a pattern representing the block(s) the tunnel walls and ceiling will be constructed with, or, if withSchematic = true, the name of the schematic containing the desired clipboard pattern
	 * @return 0 if the construction failed, 1 if the construction succeeded
	 */
	public static int createTunnel(ServerCommandSource source, int tunnelHeight, boolean withSchematic, String maskPatternInputString) {
		CorridorConstructionConstants constants = CorridorConstructionConstants.of(source);

		// Define masks and patterns
		CommandArgParser argParser = CommandArgParser.forArgString(maskPatternInputString);
    List<Substring> maskPatternArgs = argParser.parseArgs().toList();

    // Verify input
    if (maskPatternArgs.size() != 2) {
      constants.actor().printError(TextComponent.of("The arguments provided for maskPatternInput did not match the expected arguments [trackMask][tunnelPattern]."));
    }

		//Define masks and patterns
    Mask trackMask = Functions.safeParseMaskUnion.apply(maskPatternArgs.get(0).getSubstring(), constants.parserContext());
		Mask replaceableBlockMask = Functions.safeParseMaskUnion.apply("##corridor_construction_tool:tunnel_replaceable", constants.parserContext());
		Mask airMask = Functions.airMask.apply(constants.parserContext());
		Mask groundMask = Functions.groundMask.apply(trackMask, constants.parserContext());

		String tunnelPatternString = maskPatternArgs.get(1).getSubstring();
    Pattern tunnelPattern = withSchematic ? Functions.patternFromSchematic.apply(constants.selectionWorld(), tunnelPatternString) : Functions.safeParsePattern.apply(tunnelPatternString, constants.parserContext());

		try (EditSession editSession = WorldEdit.getInstance().newEditSession(constants.actor())) {
			editSession.setMask(replaceableBlockMask);

			// Provide feedback to user
			int blocksEvaluated = 0;
			int isOnEdgeCount = 0;
			int isTunnelCount = 0;
			int isTransitionCount = 0;
			long regionSize = constants.selectedRegion().getVolume();
			constants.actor().printInfo(TextComponent.of("Creating tunnel..."));

			// Loop through all blocks in selection
			for (BlockVector3 point : constants.selectedRegion()) {
				if (trackMask.test(point)) {
					boolean isOnEdge = !Functions.occludedByMask2D.test(point, trackMask);

					BlockVector3 ceilingLocation = point.add(0, tunnelHeight + 1, 0);
					boolean isBelowTrack = trackMask.test(point.add(0, 1, 0));
					boolean isTunnel = groundMask.test(ceilingLocation.add(BlockVector3.UNIT_Y));
					boolean isTransition = !isTunnel && pointIsAdjacentTo(ceilingLocation.add(BlockVector3.UNIT_Y), groundMask);

					if (isOnEdge) {
						isOnEdgeCount++;
					}
					if (isTunnel) {
						isTunnelCount++;
					}
					if (isTransition) {
						isTransitionCount++;
					}

					if (isTunnel) {
						if (isOnEdge) {
							// Tunnel wall
							for (int i = 0; i < tunnelHeight; i++) {
								BlockVector3 wallBlock = point.add(0, i + 1, 0);
								try {
									editSession.setBlock(wallBlock, tunnelPattern);
								} catch (MaxChangedBlocksException e) {
									constants.actor().printError(TextComponent.of(e.toString()));
								}
							}
						} else {
							// Set air blocks between the tunnel ceiling and tunnel floor
							clearAbove(point, tunnelHeight + 1, airMask, editSession, constants.actor());
						}
						// Tunnel ceiling
						try {
							editSession.setBlock(ceilingLocation, tunnelPattern);
						} catch (MaxChangedBlocksException e) {
							constants.actor().printError(TextComponent.of(e.toString()));
						}
						if (isBelowTrack) {
							try {
								editSession.setBlock(ceilingLocation.subtract(0, 1, 0), tunnelPattern);
							} catch (MaxChangedBlocksException e) {
								constants.actor().printError(TextComponent.of(e.toString()));
							}
						}
					} else {
						if (isOnEdge) {
							// Retaining wall of cutting
							int i = 0;
							while (true) {
								i++;
								BlockVector3 wallBlock = point.add(0, i, 0);
								boolean wallBlockTouchingGround = pointIsAdjacentTo(wallBlock, groundMask);

								if (wallBlockTouchingGround) {
									try {
										editSession.setBlock(wallBlock, tunnelPattern);
									} catch (MaxChangedBlocksException e) {
										constants.actor().printError(TextComponent.of(e.toString()));
									}
								} else {
									break;
								}
							}
						} else {
							// Clear space above cutting
							clearAbove(point, 255 - point.getY(), airMask, editSession, constants.actor());
						}
						if (isTransition) {
							// Tunnel-cutting transition
							try {
								editSession.setBlock(ceilingLocation, tunnelPattern);
							} catch (MaxChangedBlocksException e) {
								constants.actor().printError(TextComponent.of(e.toString()));
							}

							int i = 0;
							while (true) {
								i++;
								BlockVector3 wallBlock = ceilingLocation.add(0, i, 0);
								boolean wallBlockTouchingGround = pointIsAdjacentTo(wallBlock, groundMask);

								if (replaceableBlockMask.test(wallBlock) && wallBlockTouchingGround) {
									try {
										editSession.setBlock(wallBlock, tunnelPattern);
									} catch (MaxChangedBlocksException e) {
										constants.actor().printError(TextComponent.of(e.toString()));
									}
								} else {
									// Clear space above wall
									clearAbove(wallBlock, 255 - wallBlock.getY(), airMask, editSession, constants.actor());
									break;
								}
							}
						}
					}
				}
				blocksEvaluated++;
				if (blocksEvaluated % 50000 == 0) {
					constants.actor()
							.printInfo(TextComponent.of(Math.round(100 * blocksEvaluated / regionSize) + "% complete"));
				}
			}

			constants.actor().printInfo(TextComponent.of("Corridor successfully created."));
			constants.actor().printInfo(TextComponent.of(editSession.getBlockChangeCount() + " blocks were changed."));
			constants.actor().printInfo(TextComponent.of(isOnEdgeCount + " blocks were on edge"));
			constants.actor().printInfo(TextComponent.of(isTunnelCount + " blocks were tunnel floor"));
			constants.actor().printInfo(TextComponent.of(isTransitionCount + " blocks were transition floor"));
			constants.localSession().remember(editSession);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static void clearAbove(BlockVector3 point, int height, Mask airMask, EditSession editSession, Actor actor) {
		for (int i = 1; i < height; i++) {
			if (!airMask.test(point.add(0, i, 0))) {
				try {
					editSession.setBlock(point.add(0, i, 0), BlockTypes.get("minecraft:air").getDefaultState());
				} catch (MaxChangedBlocksException e) {
					actor.printError(TextComponent.of(e.toString()));
				}
			}
		}
	}
	private static boolean pointIsAdjacentTo(BlockVector3 point, Mask adjacentMask) {
		return adjacentMask.test(point.add(1, 0, 0)) ||
				adjacentMask.test(point.add(-1, 0, 0)) ||
				adjacentMask.test(point.add(0, 0, 1)) ||
				adjacentMask.test(point.add(0, 0, -1)) ||
				adjacentMask.test(point.add(-1, 0, -1)) ||
				adjacentMask.test(point.add(1, 0, -1)) ||
				adjacentMask.test(point.add(1, 0, 1)) ||
				adjacentMask.test(point.add(-1, 0, 1));
	}
}
