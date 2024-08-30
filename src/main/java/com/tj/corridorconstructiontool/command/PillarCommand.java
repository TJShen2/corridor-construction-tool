package com.tj.corridorconstructiontool.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.command.CommandArgParser;
import com.sk89q.worldedit.internal.util.Substring;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.tj.CorridorConstructionConstants;
import com.tj.corridorconstructiontool.argument.PillarOrientationArgumentType;
import com.tj.function.DistanceToEdges;
import com.tj.function.Functions;
import com.tj.function.mask.UndergroundMask;
import com.tj.regions.HorizontallyBoundedCuboidRegion;

import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.tj.corridorconstructiontool.argument.PillarOrientationArgumentType.getOrientation;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

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
     * Produces a {@code PillarOrientation} instance from a string.
     * @param input the string to parse into a {@code PillarOrientation}
     * @return the field of {@code PillarOrientation} with the same name as the input, or UNSPECIFIED if the input string does not match the name of any field of {@code PillarOrientation}
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
				.then(argument("pillarSchematicName", StringArgumentType.string()).suggests((ctx, builder) -> CommandSource.suggestMatching(Functions.getSchematicNames.get(), builder))
        .then(argument("pillarOrientation", PillarOrientationArgumentType.orientation())
				.then(argument("maskPatternInput", StringArgumentType.greedyString()).suggests((context, builder) -> {
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
    ClipboardHolder holder;
    try {
      holder = new ClipboardHolder(Functions.unsafeClipboardFromSchematic(pillarSchematicName));
    } catch (InputParseException e) {
      constants.actor().printError(e.getRichMessage());
      return 0;
    }

    // Verify input
    if (maskPatternArgs.size() != 2) {
      constants.actor().printError(TextComponent.of("The arguments provided for maskPatternInput did not match the expected arguments [trackMask][fillPattern]."));
    }

		// Define masks
    Mask trackMask = Functions.safeParseMaskUnion.apply(maskPatternArgs.get(0).getSubstring(), constants.parserContext());
		Mask replaceableBlockMask = Functions.safeParseMaskUnion.apply("##corridor_construction_tool:pillar_replaceable", constants.parserContext());
    // TODO: overly broad groundMask leads to overly broad undergroundMask leading to failure of pillar construction underneath catenary and fencing structures
		Mask groundMask = Functions.groundMask.apply(trackMask, constants.parserContext());

    // Define patterns
    Pattern fillPattern = Functions.safeParsePattern.apply(maskPatternArgs.get(1).getSubstring(), constants.parserContext());

    // Keep track of where pillars have already been placed
    List<BlockVector3> pillarLocations = new ArrayList<>();

    try (EditSession editSession = WorldEdit.getInstance().newEditSession(constants.actor())) {
      // Set mask
      Mask undergroundMask = new UndergroundMask(editSession, groundMask, pillarDepth);
      Mask underTrackMask = new UndergroundMask(editSession, trackMask, 1);
      editSession.setMask(new MaskIntersection(Masks.negate(undergroundMask), underTrackMask));

      // Provide feedback to user
      int blocksEvaluated = 0;
      long regionSize = constants.selectedRegion().getVolume();
      constants.actor().printInfo(TextComponent.of("Creating pillars..."));

      LOOPTHROUGHPOINTS: for (BlockVector3 point : constants.selectedRegion()) {
        // Provide feedback to user
        blocksEvaluated++;
        if (blocksEvaluated % 50000 == 0) {
          constants.actor()
              .printInfo(TextComponent.of(Math.round(100 * blocksEvaluated / regionSize) + "% complete"));
        }

        if (trackMask.test(point) && replaceableBlockMask.test(point.subtract(BlockVector3.UNIT_Y))) {
          // Move clipboard origin to the centre of the pillar
          DistanceToEdges pointDistanceToEdges = DistanceToEdges.findEdges(editSession, trackMask, point, trackWidth * 2, 2);
          boolean isInCenter = Math.abs(pointDistanceToEdges.east() - pointDistanceToEdges.west()) <= 1 && Math.abs(pointDistanceToEdges.south() - pointDistanceToEdges.north()) <= 1;
          holder.getClipboard().setOrigin(holder.getClipboard().getRegion().getCenter().withY(((CuboidRegion) holder.getClipboard().getRegion()).getMaximumY() + 1).toBlockPoint());

          // Analyse the area that will be occupied by the pillar to determine whether placement of pillar blocks will be fully underneath track, and therefore whether the pillar is aligned correctly
          BlockVector3 centreDisplacement = point.subtract(holder.getClipboard().getRegion().getCenter().toBlockPoint());
          HorizontallyBoundedCuboidRegion pillarRegion = new HorizontallyBoundedCuboidRegion(((CuboidRegion) holder.getClipboard().getRegion()).getPos1().add(centreDisplacement), ((CuboidRegion) holder.getClipboard().getRegion()).getPos2().add(centreDisplacement));
          BlockVector2[] pillarRegionCorners = {
              BlockVector2.at(pillarRegion.getPos1().getX(), pillarRegion.getPos1().getZ()),
              BlockVector2.at(pillarRegion.getPos1().getX(), pillarRegion.getPos2().getZ()),
              BlockVector2.at(pillarRegion.getPos2().getX(), pillarRegion.getPos1().getZ()),
              BlockVector2.at(pillarRegion.getPos2().getX(), pillarRegion.getPos2().getZ())
          };
          boolean isAligned = true;
          for (BlockVector2 col : pillarRegionCorners) {
            if (editSession.getHighestTerrainBlock(col.getX(), col.getZ(), point.getY() - trackWidth / 2, point.getY() + trackWidth / 2, trackMask) == point.getY() - trackWidth / 2) {
              isAligned = false;
              break;
            }
          }

          // Determine whether the pillar location is far enough away from the last pillar constructed
          boolean atAppropriateLocation = (pillarLocations.isEmpty() || pillarLocations.stream().allMatch(point2 -> point.distance(point2) >= pillarSpacing));

          if (isInCenter && isAligned && atAppropriateLocation) {
            BlockVector3 pillarSize = holder.getClipboard().getDimensions();
            boolean hasCorrectOrientation = switch (pillarOrientation) {
              case LONGITUDINAL -> pillarSize.getX() > pillarSize.getZ() == pointDistanceToEdges.east() > pointDistanceToEdges.south();
              case TRANSVERSE -> pillarSize.getX() < pillarSize.getZ() == pointDistanceToEdges.south() > pointDistanceToEdges.east();
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

            // Fill in area above pillar but below track (on segments of track with a nonzero grade)
            for (BlockVector2 col : pillarRegion.asFlatRegion()) {
              BlockVector3 highestFillBlock = col.toBlockVector3(editSession.getHighestTerrainBlock(col.getX(), col.getZ(), point.getY(), point.getY() + trackWidth / 2, trackMask));
              BlockVector3 lowestFillBlock = col.toBlockVector3(point.getY());
              if (!highestFillBlock.equals(lowestFillBlock)) {
                try {
                  editSession.setBlocks(new CuboidRegion(highestFillBlock, lowestFillBlock), fillPattern);
                } catch (MaxChangedBlocksException e) {
                  constants.actor().printError(e.getRichMessage());
                  break LOOPTHROUGHPOINTS;
                }
              }
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
