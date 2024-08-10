package com.example.command;

import com.example.CorridorConstructionConstants;
import com.example.function.Functions;
import com.example.mask.UndergroundMask;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.command.CommandArgParser;
import com.sk89q.worldedit.internal.util.Substring;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.FlatRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.*;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;

public class EmbankmentCommand {
  public static void register(CommandDispatcher<ServerCommandSource> dispatcher){
      dispatcher.register(literal("embankment")
          .requires(source -> source.hasPermissionLevel(2)) // Must be a game master to use the command. Command will not show up in tab completion or execute to non operators or any operator that is permission level 1.
					.then(argument("maxHeight", IntegerArgumentType.integer())
					.then(argument("grade", DoubleArgumentType.doubleArg())
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
          .executes(ctx -> createEmbankment(ctx.getSource(), getInteger(ctx, "maxHeight"), getDouble(ctx, "grade"), getString(ctx, "maskPatternInput")))))));
  }

  private static int createEmbankment(ServerCommandSource source, int maxHeight, double grade, String maskPatternInputString) {
		CorridorConstructionConstants constants = new CorridorConstructionConstants(source);

		CommandArgParser argParser = CommandArgParser.forArgString(maskPatternInputString);
    List<Substring> maskPatternArgs = argParser.parseArgs().toList();

    // Verify input
    if (maskPatternArgs.size() != 2) {
      constants.getActor().printError(TextComponent.of("The arguments provided for maskPatternInput did not match the expected arguments [trackMask][embankmentPattern]."));
    }

		//Define masks and patterns
    Mask trackMask = Functions.safeParseMaskUnion.apply(maskPatternArgs.get(0).getSubstring(), constants.getParserContext());
    Pattern embankmentPattern = Functions.safeParsePattern.apply(maskPatternArgs.get(1).getSubstring(), constants.getParserContext());

    Mask replaceableBlockMask = Functions.safeParseMaskUnion.apply("##corridor_construction_tool:embankment_replaceable", constants.getParserContext());
		Mask groundMask = Functions.groundMask.apply(trackMask, constants.getParserContext());

		try (EditSession editSession = WorldEdit.getInstance().newEditSession(constants.getActor())) {
			//Set the mask for the blocks that may be replaced
      Mask undergroundMask = new UndergroundMask(editSession, groundMask, 5);
      editSession.setMask(new MaskIntersection(replaceableBlockMask, Masks.negate(undergroundMask)));

			//Provide feedback to user
			int blocksEvaluated = 0;
			long regionSize = constants.getSelectedRegion().getVolume();
			constants.getActor().printInfo(TextComponent.of("Creating embankment..."));

			for (BlockVector3 point : constants.getSelectedRegion()) {
				if (trackMask.test(point)) {
					int width = (int) Math.round(maxHeight / grade);
					Region heightTestRegion = new CuboidRegion(editSession.getWorld(), point.subtract(width, 0, width), point.add(width, 0, width));

					if (checkHeightMap(point, width, maxHeight, heightTestRegion, groundMask)) {
						boolean isSurrounded = trackMask.test(point.add(BlockVector3.UNIT_X)) && trackMask.test(point.add(BlockVector3.UNIT_Z)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_X)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_Z));

						if (isSurrounded) {
							createColumn(point.subtract(BlockVector3.UNIT_Y), maxHeight, replaceableBlockMask, embankmentPattern, editSession, constants.getActor());
						} else {
							int[] edgeDirection = {
									!trackMask.test(point.add(BlockVector3.UNIT_X)) ? 1 : 0,
									!trackMask.test(point.add(BlockVector3.UNIT_Z)) ? 1 : 0,
									!trackMask.test(point.add(BlockVector3.UNIT_MINUS_X)) ? 1 : 0,
									!trackMask.test(point.add(BlockVector3.UNIT_MINUS_Z)) ? 1 : 0
							};
							Region slopeRegion = new CuboidRegion(editSession.getWorld(), point.subtract(width * edgeDirection[2], 0, width * edgeDirection[3]), point.add(width * edgeDirection[0], 0, width * edgeDirection[1]));
							createSlope(point, maxHeight, replaceableBlockMask, embankmentPattern, editSession, constants.getActor(), (FlatRegion) slopeRegion, grade);
						}
					}
				}
				blocksEvaluated++;
				if (blocksEvaluated % 50000 == 0) {
						constants.getActor().printInfo(TextComponent.of(String.valueOf(Math.round(100 * blocksEvaluated / regionSize)).concat("% complete")));
				}
			}

			constants.getActor().printInfo(TextComponent.of("Embankment successfully created."));
			constants.getActor().printInfo(TextComponent.of(String.valueOf(editSession.getBlockChangeCount()).concat(" blocks were changed.")));
			constants.getLocalSession().remember(editSession);
		}
		return Command.SINGLE_SUCCESS; // Success
	}

	private static boolean checkHeightMap(BlockVector3 origin, int radius, int maxHeight, Region cuboidRegion, Mask groundMask) {
		//Hash table containing each column near the track
		Map<BlockVector2, Boolean> columns = new HashMap<>();

		BlockVector2 origin2D = origin.toBlockVector2();

		int validHeightCount = 0;
		int invalidHeightCount = 0;

		for (BlockVector3 block : cuboidRegion) {
				BlockVector2 column = block.toBlockVector2();

				if (column.distance(origin2D) <= radius) {
						Boolean isValidHeight = columns.get(column);

						if (isValidHeight == null) {
								isValidHeight = groundMask.test(column.toBlockVector3(origin.getY() - maxHeight));
								columns.put(column, isValidHeight);
						}

						if (isValidHeight) {
								validHeightCount++;
						} else {
								invalidHeightCount++;
						}
				}
		}
		boolean isValidHeightMap = invalidHeightCount == 0 || validHeightCount / invalidHeightCount >= 19;
		return isValidHeightMap;
	}

	private static void createColumn(BlockVector3 origin, int maxHeight, Mask replaceableBlockMask, Pattern embankmentMaterial, EditSession editSession, Actor actor) {
		for (int i = 0; i < maxHeight; i++) {
			BlockVector3 point = origin.subtract(0,i,0);
			if (replaceableBlockMask.test(point)) {
				try {
					editSession.setBlock(point, embankmentMaterial);
				} catch (MaxChangedBlocksException e) {
					actor.printError(TextComponent.of(e.toString()));
				}
			} else {
				break;
			}
		}
	}

	private static void createSlope(BlockVector3 origin, int maxHeight, Mask replaceableBlockMask, Pattern embankmentMaterial, EditSession editSession, Actor actor, FlatRegion slopeRegion, double grade) {
		BlockVector2 origin2D = origin.toBlockVector2();

		for (BlockVector2 column : slopeRegion.asFlatRegion()) {
				double distanceFromOrigin = origin2D.distance(column);
				BlockVector3 columnOrigin = column.toBlockVector3((int) (origin.getY() - Math.max(1, Math.round(grade * distanceFromOrigin))));
				createColumn(columnOrigin, maxHeight, replaceableBlockMask, embankmentMaterial, editSession, actor);
		}
	}
}
