package com.example.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.example.CorridorConstructionConstants;
import com.example.command.argument.PillarOrientationArgumentType;
import com.example.function.Functions;
import com.example.mask.UndergroundMask;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.internal.command.CommandArgParser;
import com.sk89q.worldedit.internal.util.Substring;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.formatting.text.TextComponent;

import net.minecraft.server.command.ServerCommandSource;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static com.example.command.argument.PillarOrientationArgumentType.getOrientation;

public class PillarCommand {
  public enum PillarOrientation {
    LONGITUDINAL, TRANSVERSE, UNSPECIFIED;

    public static PillarOrientation fromString(String input) {
      try {
        return (PillarOrientation) PillarOrientation.class.getField(input.toUpperCase()).get(null);
      } catch (NoSuchFieldException|IllegalAccessException e) {
        return PillarOrientation.UNSPECIFIED;
      }
    }
  }

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("pillar")
				.requires(source -> source.hasPermissionLevel(2)) // Must be a game master to use the command. Command will not
																													// show up in tab completion or execute to non operators or
																													// any operator that is permission level 1.
        .then(argument("trackWidth", IntegerArgumentType.integer())
        .then(argument("pillarSpacing", IntegerArgumentType.integer())
        .then(argument("pillarDepth", IntegerArgumentType.integer())
				.then(argument("pillarSchematicName", StringArgumentType.string())
        .then(argument("pillarOrientation", PillarOrientationArgumentType.orientation())
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
				.executes(ctx -> createPillars(ctx.getSource(), getInteger(ctx, "trackWidth"), getInteger(ctx, "pillarSpacing"), getInteger(ctx, "pillarDepth"), getString(ctx, "pillarSchematicName"), getOrientation(ctx, "pillarOrientation"), getString(ctx, "maskPatternInput"))))))))));
	}

  public static int createPillars(ServerCommandSource source, int trackWidth, int pillarSpacing, int pillarDepth, String pillarSchematicName, PillarOrientation pillarOrientation, String maskPatternInputString) {
    CorridorConstructionConstants constants = new CorridorConstructionConstants(source);

		// Define masks and patterns
		CommandArgParser argParser = CommandArgParser.forArgString(maskPatternInputString);
    List<Substring> maskPatternArgs = argParser.parseArgs().toList();

    // Get schematic as clipboard
    ClipboardHolder holder = new ClipboardHolder(Functions.clipboardFromSchematic.apply(constants.getSelectionWorld(), pillarSchematicName));

    // Verify input
    if (maskPatternArgs.size() != 1) {
      constants.getActor().printError(TextComponent.of("The arguments provided for maskPatternInput did not match the expected arguments [trackMask]."));
    }

		// Define masks
    Mask trackMask = Functions.safeParseMaskUnion.apply(maskPatternArgs.get(0).getSubstring(), constants.getParserContext());
		Mask replaceableBlockMask = Functions.safeParseMaskUnion.apply("##corridor_construction_tool:pillar_replaceable", constants.getParserContext());
		Mask groundMask = Functions.groundMask.apply(trackMask, constants.getParserContext());

    // Keep track of where pillars have already been placed
    List<BlockVector3> pillarLocations = new ArrayList<>();

    try (EditSession editSession = WorldEdit.getInstance().newEditSession(constants.getActor())) {
      // Set mask
      Mask undergroundMask = new UndergroundMask(editSession, groundMask, pillarDepth);
      editSession.setMask(new MaskIntersection(replaceableBlockMask, Masks.negate(undergroundMask)));

      // Provide feedback to user
      int blocksEvaluated = 0;
      long regionSize = constants.getSelectedRegion().getVolume();
      constants.getActor().printInfo(TextComponent.of("Creating pillars..."));

      for (BlockVector3 point : constants.getSelectedRegion()) {
        // Provide feedback to user
        blocksEvaluated++;
        if (blocksEvaluated % 50000 == 0) {
          constants.getActor()
              .printInfo(TextComponent.of(Math.round(100 * blocksEvaluated / regionSize) + "% complete"));
        }

        if (trackMask.test(point) && replaceableBlockMask.test(point.subtract(BlockVector3.UNIT_Y))) {
          Integer distanceToWestEdge = Functions.distanceToEdge(editSession, trackMask, point, Direction.WEST, trackWidth);
          Integer distanceToEastEdge = Functions.distanceToEdge(editSession, trackMask, point, Direction.EAST, trackWidth);
          Integer distanceToNorthEdge = Functions.distanceToEdge(editSession, trackMask, point, Direction.NORTH, trackWidth);
          Integer distanceToSouthEdge = Functions.distanceToEdge(editSession, trackMask, point, Direction.SOUTH, trackWidth);

          boolean isInCenter = (distanceToEastEdge == null || distanceToWestEdge == null || Math.abs(distanceToEastEdge - distanceToWestEdge) < 2) &&
              (distanceToSouthEdge == null || distanceToNorthEdge == null || Math.abs(distanceToSouthEdge - distanceToNorthEdge) < 2);

          if (isInCenter && (pillarLocations.isEmpty() || point.distance(pillarLocations.getLast()) >= pillarSpacing)) {
            BlockVector3 pillarSize = holder.getClipboard().getDimensions();
            boolean hasCorrectOrientation = switch (pillarOrientation) {
              case LONGITUDINAL -> pillarSize.getX() > pillarSize.getZ() == (distanceToEastEdge == null || distanceToEastEdge > trackWidth / 2 - 1) && (distanceToWestEdge == null || distanceToWestEdge > trackWidth / 2 - 1);
              case TRANSVERSE -> pillarSize.getX() < pillarSize.getZ() == (distanceToEastEdge == null || distanceToEastEdge > trackWidth / 2 - 1) && (distanceToWestEdge == null || distanceToWestEdge > trackWidth / 2 - 1);
              case UNSPECIFIED -> true;
            };

            if (!hasCorrectOrientation) {
              AffineTransform transform = new AffineTransform();
              transform = transform.rotateY(-90);
              holder.setTransform(holder.getTransform().combine(transform));
            }

            pillarLocations.add(point);
            // Create paste operation
            Operation operation = holder.createPaste(editSession).to(point).ignoreAirBlocks(true).copyBiomes(false).copyEntities(false).maskSource(null).build();
            // Paste schematic
            try {
              Operations.complete(operation);
            } catch (WorldEditException e) {
              constants.getActor().printError(e.getRichMessage());
            }
            constants.getActor().printInfo(TextComponent.of("Created a pillar at: " + point.toString()));
          }
        }
      }
      constants.getActor().printInfo(TextComponent.of("Pillars successfully created."));
      constants.getActor().printInfo(TextComponent.of(editSession.getBlockChangeCount() + " blocks were changed."));
      constants.getLocalSession().remember(editSession);
    }
    return Command.SINGLE_SUCCESS;
  }
}
