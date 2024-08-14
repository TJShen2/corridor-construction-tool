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

/**
 * This class represents a brigadier command that creates pillars at regular intervals underneath a track bed using the WorldEdit API.
 *
 * @author TJ Shen
 * @version 1.0.0
*/

public class PillarCommand {
  /**
   * Represents the possible ways a pillar can be oriented relative to the orientation of the track bed.
   */
  public enum PillarOrientation {
    /**
     * The pillar is oriented along the track bed.
     */
    LONGITUDINAL,
    /**
     * The pillar is oriented across the track bed.
     */
    TRANSVERSE,
    /**
     * The user has decided not the specify what the orientation of the pillar should be.
     */
    UNSPECIFIED;

    /**
     * Produces a PillarOrientation instance from a string.
     * @param input the string to parse into a PillarOrientation
     * @return the field of PillarOrientation with the same name as the input, or UNSPECIFIED if the input string does not match the name of any field of PillarOrientation
     */
    public static PillarOrientation fromString(String input) {
      try {
        return (PillarOrientation) PillarOrientation.class.getField(input.toUpperCase()).get(null);
      } catch (NoSuchFieldException|IllegalAccessException e) {
        return PillarOrientation.UNSPECIFIED;
      }
    }
  }

  /**
   * Registers the command with brigadier.
   * @param dispatcher the dispatcher used to register the command
   */
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

  /**
   * Creates pillars at regular intervals underneath a track bed within the player's region selection.
   * @param source the command source that this command is being run from
   * @param trackWidth the width of the track bed, in metres
   * @param pillarSpacing the desired distance between the pillars, in metres
   * @param pillarDepth the distance that each pillar extends through the ground, in metres
   * @param pillarSchematicName the name of the schematic that represents the pillar
   * @param pillarOrientation the orientation of the pillar relative to the orientation of the track bed
   * @param maskPatternInputString contains the trackMask (a mask containing the block(s) the track bed is made of)
   * @return 0 if the construction failed, 1 if the construction succeeded
   */
  public static int createPillars(ServerCommandSource source, int trackWidth, int pillarSpacing, int pillarDepth, String pillarSchematicName, PillarOrientation pillarOrientation, String maskPatternInputString) {
    CorridorConstructionConstants constants = CorridorConstructionConstants.of(source);

		// Define masks and patterns
		CommandArgParser argParser = CommandArgParser.forArgString(maskPatternInputString);
    List<Substring> maskPatternArgs = argParser.parseArgs().toList();

    // Get schematic as clipboard
    ClipboardHolder holder = new ClipboardHolder(Functions.clipboardFromSchematic.apply(constants.selectionWorld(), pillarSchematicName));

    // Verify input
    if (maskPatternArgs.size() != 1) {
      constants.actor().printError(TextComponent.of("The arguments provided for maskPatternInput did not match the expected arguments [trackMask]."));
    }

		// Define masks
    Mask trackMask = Functions.safeParseMaskUnion.apply(maskPatternArgs.get(0).getSubstring(), constants.parserContext());
		Mask replaceableBlockMask = Functions.safeParseMaskUnion.apply("##corridor_construction_tool:pillar_replaceable", constants.parserContext());
		Mask groundMask = Functions.groundMask.apply(trackMask, constants.parserContext());

    // Keep track of where pillars have already been placed
    List<BlockVector3> pillarLocations = new ArrayList<>();

    try (EditSession editSession = WorldEdit.getInstance().newEditSession(constants.actor())) {
      // Set mask
      Mask undergroundMask = new UndergroundMask(editSession, groundMask, pillarDepth);
      editSession.setMask(Masks.negate(undergroundMask));

      // Provide feedback to user
      int blocksEvaluated = 0;
      long regionSize = constants.selectedRegion().getVolume();
      constants.actor().printInfo(TextComponent.of("Creating pillars..."));

      for (BlockVector3 point : constants.selectedRegion()) {
        // Provide feedback to user
        blocksEvaluated++;
        if (blocksEvaluated % 50000 == 0) {
          constants.actor()
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
              constants.actor().printError(e.getRichMessage());
            }
            constants.actor().printInfo(TextComponent.of("Created a pillar at: " + point.toString()));
          }
        }
      }
      constants.actor().printInfo(TextComponent.of("Pillars successfully created."));
      constants.actor().printInfo(TextComponent.of(editSession.getBlockChangeCount() + " blocks were changed."));
      constants.localSession().remember(editSession);
    }
    return Command.SINGLE_SUCCESS;
  }
}
