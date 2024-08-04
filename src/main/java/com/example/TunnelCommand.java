package com.example;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.world.SideEffectExtent;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;

import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.*;

import java.util.ArrayList;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;

public class TunnelCommand {
	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("tunnel")
				.requires(source -> source.hasPermissionLevel(2)) // Must be a game master to use the command. Command will not
																													// show up in tab completion or execute to non operators or
																													// any operator that is permission level 1.
				.then(argument("trackMask", StringArgumentType.string())
				.then(argument("tunnelMaterial", StringArgumentType.string())
				.then(argument("tunnelHeight", IntegerArgumentType.integer())
				.executes(ctx -> createTunnel(ctx.getSource(), getString(ctx, "trackMask"),
						getString(ctx, "tunnelMaterial"), getInteger(ctx, "tunnelHeight"))))))); // You can deal with
																																																	// the arguments out
																																																	// here and pipe them
																																																	// into the command.
	}

	public static int createTunnel(ServerCommandSource source, String trackMaskString, String tunnelMaterialString,
			int tunnelHeight) {
		CorridorConstructionConstants constants = new CorridorConstructionConstants(source);

		// Define masks and patterns
		Mask trackMask;
		Mask replaceableBlockMask;
		Mask airMask;
		Mask railMask;
		Pattern tunnelMaterial;
		try {
			trackMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(trackMaskString,
					constants.getParserContext());
			replaceableBlockMask = WorldEdit.getInstance().getMaskFactory()
					.parseFromInput("##transit_corridor:railway_embankment_replaceable", constants.getParserContext());
			airMask = WorldEdit.getInstance().getMaskFactory().parseFromInput("minecraft:air", constants.getParserContext());
			tunnelMaterial = WorldEdit.getInstance().getPatternFactory().parseFromInput(tunnelMaterialString,
					constants.getParserContext());
		} catch (InputParseException e) {
			constants.getActor()
					.printError(TextComponent.of("The mask and pattern arguments for the command /tunnel may have been invalid\n" + e));
			return 0;
		}
		try {
			railMask = WorldEdit.getInstance().getMaskFactory().parseFromInput("mtr:rail", constants.getParserContext());
		} catch (InputParseException e) {
			railMask = new BlockMask(new SideEffectExtent(constants.getSelectionWorld()), new ArrayList<BaseBlock>());
		}
		Mask groundMask = new MaskIntersection(Masks.negate(replaceableBlockMask), Masks.negate(trackMask), Masks.negate(railMask));

		try (EditSession editSession = WorldEdit.getInstance().newEditSession(constants.getActor())) {
			editSession.setMask(Masks.negate(railMask));

			// Provide feedback to user
			int blocksEvaluated = 0;
			int isOnEdgeCount = 0;
			int isTunnelCount = 0;
			int isTransitionCount = 0;
			long regionSize = constants.getSelectedRegion().getVolume();
			constants.getActor().printInfo(TextComponent.of("Creating tunnel..."));

			// Loop through all blocks in selection
			for (BlockVector3 point : constants.getSelectedRegion()) {
				if (trackMask.test(point)) {
					int neighbouringTrackBlocksCount = ((trackMask.test(point.add(1,0,0)) || trackMask.test(point.add(1,1,0)) || trackMask.test(point.add(1,-1,0))) ? 1 : 0) +
						((trackMask.test(point.add(0,0,1)) || trackMask.test(point.add(0,1,1)) || trackMask.test(point.add(0,-1,1))) ? 1 : 0) +
						((trackMask.test(point.add(-1,0,0)) || trackMask.test(point.add(-1,1,0)) || trackMask.test(point.add(-1,-1,0))) ? 1 : 0) +
						((trackMask.test(point.add(0,0,-1)) || trackMask.test(point.add(0,1,-1)) || trackMask.test(point.add(0,-1,-1))) ? 1 : 0) +
						((trackMask.test(point.add(1,0,1)) || trackMask.test(point.add(1,1,1)) || trackMask.test(point.add(1,-1,1))) ? 1 : 0) +
						((trackMask.test(point.add(1,0,-1)) || trackMask.test(point.add(1,1,-1)) || trackMask.test(point.add(1,-1,-1))) ? 1 : 0) +
						((trackMask.test(point.add(-1,0,-1)) || trackMask.test(point.add(-1,1,-1)) || trackMask.test(point.add(-1,-1,-1))) ? 1 : 0) +
						((trackMask.test(point.add(-1,0,1)) || trackMask.test(point.add(-1,1,1)) || trackMask.test(point.add(-1,-1,1))) ? 1 : 0);

					BlockVector3 ceilingLocation = point.add(0, tunnelHeight + 1, 0);

					boolean isOnEdge = neighbouringTrackBlocksCount < 8;
					boolean isBelowTrack = trackMask.test(point.add(0, 1, 0));
					boolean isTunnel = groundMask.test(ceilingLocation.add(BlockVector3.UNIT_Y));
					boolean isTransition = !isTunnel &&
							(groundMask.test(ceilingLocation.add(1, 1, 0)) ||
									groundMask.test(ceilingLocation.add(-1, 1, 0)) ||
									groundMask.test(ceilingLocation.add(0, 1, 1)) ||
									groundMask.test(ceilingLocation.add(0, 1, -1)) ||
									groundMask.test(ceilingLocation.add(-1, 1, -1)) ||
									groundMask.test(ceilingLocation.add(1, 1, -1)) ||
									groundMask.test(ceilingLocation.add(1, 1, 1)) ||
									groundMask.test(ceilingLocation.add(-1, 1, 1)));

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
									editSession.setBlock(wallBlock, tunnelMaterial);
								} catch (MaxChangedBlocksException e) {
									constants.getActor().printError(TextComponent.of(e.toString()));
								}
							}
						} else {
							// Set air blocks between the tunnel ceiling and tunnel floor
							clearAbove(point, tunnelHeight + 1, airMask, editSession, constants.getActor());
						}
						// Tunnel ceiling
						try {
							editSession.setBlock(ceilingLocation, tunnelMaterial);
						} catch (MaxChangedBlocksException e) {
							constants.getActor().printError(TextComponent.of(e.toString()));
						}
						if (isBelowTrack) {
							try {
								editSession.setBlock(ceilingLocation.subtract(0, 1, 0), tunnelMaterial);
							} catch (MaxChangedBlocksException e) {
								constants.getActor().printError(TextComponent.of(e.toString()));
							}
						}
					} else {
						if (isOnEdge) {
							// Retaining wall of cutting
							int i = 0;
							while (true) {
								i++;
								BlockVector3 wallBlock = point.add(0, i, 0);

								boolean wallBlockTouchingGround = groundMask.test(wallBlock.add(1, 0, 0)) ||
										groundMask.test(wallBlock.add(-1, 0, 0)) ||
										groundMask.test(wallBlock.add(0, 0, 1)) ||
										groundMask.test(wallBlock.add(0, 0, -1)) ||
										groundMask.test(wallBlock.add(-1, 0, -1)) ||
										groundMask.test(wallBlock.add(1, 0, -1)) ||
										groundMask.test(wallBlock.add(1, 0, 1)) ||
										groundMask.test(wallBlock.add(-1, 0, 1));

								if (wallBlockTouchingGround) {
									try {
										editSession.setBlock(wallBlock, tunnelMaterial);
									} catch (MaxChangedBlocksException e) {
										constants.getActor().printError(TextComponent.of(e.toString()));
									}
								} else {
									break;
								}
							}
						} else {
							// Clear space above cutting
							clearAbove(point, 255 - point.getY(), airMask, editSession, constants.getActor());
						}
						if (isTransition) {
							// Tunnel-cutting transition
							try {
								editSession.setBlock(ceilingLocation, tunnelMaterial);
							} catch (MaxChangedBlocksException e) {
								constants.getActor().printError(TextComponent.of(e.toString()));
							}

							int i = 0;
							while (true) {
								i++;
								BlockVector3 wallBlock = ceilingLocation.add(0, i, 0);

								boolean wallBlockTouchingGround = groundMask.test(wallBlock.add(1, 0, 0)) ||
										groundMask.test(wallBlock.add(-1, 0, 0)) ||
										groundMask.test(wallBlock.add(0, 0, 1)) ||
										groundMask.test(wallBlock.add(0, 0, -1)) ||
										groundMask.test(wallBlock.add(-1, 0, -1)) ||
										groundMask.test(wallBlock.add(1, 0, -1)) ||
										groundMask.test(wallBlock.add(1, 0, 1)) ||
										groundMask.test(wallBlock.add(-1, 0, 1));

								if (replaceableBlockMask.test(wallBlock) && wallBlockTouchingGround) {
									try {
										editSession.setBlock(wallBlock, tunnelMaterial);
									} catch (MaxChangedBlocksException e) {
										constants.getActor().printError(TextComponent.of(e.toString()));
									}
								} else {
									// Clear space above wall
									clearAbove(wallBlock, 255 - wallBlock.getY(), airMask, editSession, constants.getActor());
									break;
								}
							}
						}
					}
				}
				blocksEvaluated++;
				if (blocksEvaluated % 50000 == 0) {
					constants.getActor()
							.printInfo(TextComponent.of(Math.round(100 * blocksEvaluated / regionSize) + "% complete"));
				}
			}

			constants.getActor().printInfo(TextComponent.of("Corridor successfully created."));
			constants.getActor().printInfo(TextComponent.of(editSession.getBlockChangeCount() + " blocks were changed."));
			constants.getActor().printInfo(TextComponent.of(isOnEdgeCount + " blocks were on edge"));
			constants.getActor().printInfo(TextComponent.of(isTunnelCount + " blocks were tunnel floor"));
			constants.getActor().printInfo(TextComponent.of(isTransitionCount + " block were transition floor"));
			constants.getLocalSession().remember(editSession);
		}
		return Command.SINGLE_SUCCESS;
	}

	public static void clearAbove(BlockVector3 point, int height, Mask airMask, EditSession editSession, Actor actor) {
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
}
