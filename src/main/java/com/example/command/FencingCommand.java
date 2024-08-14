package com.example.command;

import net.minecraft.server.command.ServerCommandSource;
import static net.minecraft.server.command.CommandManager.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.example.CorridorConstructionConstants;
import com.example.SetBlockOperation;
import com.example.command.argument.CatenaryTypeArgumentType;
import com.example.function.Functions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
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
import com.sk89q.worldedit.function.mask.MaskUnion;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.command.CommandArgParser;
import com.sk89q.worldedit.internal.util.Substring;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.Direction.Flag;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.example.command.argument.CatenaryTypeArgumentType.getCatenaryType;
import static com.example.command.FencingCommand.CatenaryType.*;

/**
 * This class represents a brigadier command that creates a fence beside the track and (optionally) an electrical system above the track using the WorldEdit API.
 * @author TJ Shen
 * @version 1.0.0
*/
public class FencingCommand {
  /**
   * Represents the different catenary designs that this command permits.
   */
  public enum CatenaryType {
    /**
     * No catenary will be built.
     */
    NONE,
    /**
     * The catenary racks and nodes will be mounted on poles on one side of the track.
     */
    POLE_MOUNTED_SINGLE,
    /**
     * The catenary racks and nodes will be mounted on poles on both sides of the track.
     */
    POLE_MOUNTED_DOUBLE,
    /**
     * The catenary racks and nodes will be mounted on a gantry overhanging the track.
     */
    GANTRY_MOUNTED;

    /**
     * Produces a CatenaryType instance from a string
     * @param input the string to parse into a CatenaryType
     * @return the field of CatenaryType with the same name as the input, or NONE if the input string does not match the name of any field of CatenaryType
     */
    public static CatenaryType fromString(String input) {
      try {
        return (CatenaryType) CatenaryType.class.getField(input.toUpperCase()).get(null);
      } catch (NoSuchFieldException|IllegalAccessException e) {
        return NONE;
      }
    }
  }

  /**
   * Registers the command with brigadier.
   * @param dispatcher the dispatcher used to register the command
   */
  public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
    dispatcher.register(literal("fence")
        .requires(source -> source.hasPermissionLevel(2)) // Must be a game master to use the command. Command will not
                                                          // show up in tab completion or execute to non operators or
                                                          // any operator that is permission level 1.
        .then(argument("fencingHeight", IntegerArgumentType.integer())
        .then(argument("catenaryType", CatenaryTypeArgumentType.catenaryType())
        .then(argument("catenaryHeight", IntegerArgumentType.integer())
        .then(argument("nodeSpacing", IntegerArgumentType.integer())
        .then(argument("normalTrackWidth", IntegerArgumentType.integer())
        .then(argument("trackPositions", StringArgumentType.string())

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
            .executes(ctx -> createFencing(ctx.getSource(), getInteger(ctx, "fencingHeight"), getCatenaryType(ctx, "catenaryType"), getInteger(ctx, "catenaryHeight"), getInteger(ctx, "normalTrackWidth"), getInteger(ctx, "nodeSpacing"), getString(ctx, "trackPositions"), getString(ctx, "maskPatternInput")))))))))));
  }

  /**
   * Creates a fence beside the track and (optionally) an electrical system above the track.
   * @param source the command source that this command is being run from
   * @param fencingHeight the height of the fencing on flat ground, in metres
   * @param catenaryType the catenary design to use
   * @param catenaryHeight the clearance available above the track bed to construct the catenaries, in metres
   * @param normalTrackWidth the width of the track bed on tangent (straight) sections, in metres
   * @param nodeSpacing the minimum distance between the catenary nodes, in metres
   * @param trackPositionsString a comma-separated list of integers representing the location of the tracks relative to the edges of the track bed, in metres
   * @param maskPatternInputString contains the following arguments, in order, separated by a space:
   * trackMask: a mask containing the block(s) the track bed is made of
   * endMarkerMask: a mask containing the block(s) used to mark the ends of the track
   * baseMask: a mask containing the block(s) that fencing will be placed on, in addition to the track blocks
   * poleBasePattern: the pattern representing the block(s) used to build the base for the catenary poles
   * fencingPattern: the pattern representing the block(s) used to build the fence
   * @return 0 if the construction failed, 1 if the construction succeeded
   */
  public static int createFencing(ServerCommandSource source, int fencingHeight, CatenaryType catenaryType, int catenaryHeight, int normalTrackWidth, int nodeSpacing, String trackPositionsString, String maskPatternInputString) {
    long startTime = System.currentTimeMillis();

    CorridorConstructionConstants constants = CorridorConstructionConstants.of(source);

    CommandArgParser argParser = CommandArgParser.forArgString(maskPatternInputString);
    List<Substring> maskPatternArgs = argParser.parseArgs().toList();

    // Verify input
    if (maskPatternArgs.size() != 5) {
      constants.actor().printError(TextComponent.of("The arguments provided for maskPatternInput did not match the expected arguments [trackMask][endMarkerMask][baseMask][poleBasePattern][fencingPattern]."));
      return 0;
    } else if (fencingHeight > catenaryHeight) {
      constants.actor().printError(TextComponent.of("Fencing height cannot be greater than catenary height."));
      return 0;
    } else if (!(catenaryType.equals(NONE) || catenaryType.equals(POLE_MOUNTED_SINGLE) || catenaryType.equals(POLE_MOUNTED_DOUBLE) || catenaryType.equals(GANTRY_MOUNTED))) {
      constants.actor().printError(TextComponent.of("\"" + catenaryType + "\"" + " is not a valid catenary type. Please enter \"none\", \"pole_mounted_single\", \"pole_mounted_double\", or \"gantry_mounted\"."));
      return 0;
    }

    Mask trackMask = Functions.safeParseMaskUnion.apply(maskPatternArgs.get(0).getSubstring(), constants.parserContext());
    Mask endMarkerMask = Functions.safeParseMaskUnion.apply(maskPatternArgs.get(1).getSubstring(), constants.parserContext());
    Mask baseMask = Functions.safeParseMaskUnion.apply(maskPatternArgs.get(2).getSubstring(), constants.parserContext());

    Pattern poleBasePattern = Functions.safeParsePattern.apply(maskPatternArgs.get(3).getSubstring(), constants.parserContext());
    Pattern fencingPattern = Functions.safeParsePattern.apply(maskPatternArgs.get(4).getSubstring(), constants.parserContext());

    Mask replaceableBlockMask = Functions.safeParseMaskUnion.apply("##corridor_construction_tool:fencing_replaceable", constants.parserContext());

    List<String> trackPositionStrings = Arrays.asList(trackPositionsString.split(","));
    List<Integer> normalTrackPositions = trackPositionStrings.stream().map(e -> Integer.valueOf(e)).toList();

    boolean includeCatenary = !catenaryType.equals(NONE);
    List<BlockVector3> poleLocations = new ArrayList<>();

    // Catenaries will only be constructed from one side, so we need to store the data of which side the catenaries are constructed from
    Direction allowedXDirection = Direction.WEST;
    Direction allowedZDirection = Direction.NORTH;

    try (EditSession editSession = WorldEdit.getInstance().newEditSession(constants.actor())) {
      // Place fences on the edges of bridges and embankments and on the retaining walls
      // Create a stream that generates the blocks that need to be modified

      Stream<SetBlockOperation> operations = StreamSupport.stream(constants.selectedRegion().spliterator(), false).filter(point -> trackMask.test(point) && (editSession.getHighestTerrainBlock(point.getX(), point.getZ(), point.getY(), 320, baseMask) != point.getY() || !Functions.occludedByMask2D.test(point, new MaskUnion(trackMask, endMarkerMask)))).map(point -> {
        boolean isOnOuterEdge = !Functions.occludedByMaskCardinal.test(point, new MaskUnion(trackMask, endMarkerMask));

        Map<Direction, Integer> numTrackBlocksByDirection = Direction.valuesOf(Flag.CARDINAL).stream().collect(Collectors.toUnmodifiableMap(Function.identity(), dir -> getTrackBlocksCount(point, editSession, trackMask, IntStream.range(1, 5).toArray(), dir)));
        Direction catenaryDirection = numTrackBlocksByDirection.entrySet().stream().max((a, b) -> a.getValue().compareTo(b.getValue())).get().getKey();
        Direction oppositeToCatenaryDirection = Direction.findClosest(catenaryDirection.toVector().multiply(-1), Flag.CARDINAL);

        boolean atAppropriateLocation = poleLocations.isEmpty() ? true : poleLocations.stream().allMatch(poleLocation -> point.distance(poleLocation) >= nodeSpacing);
        boolean directionIsAllowed = allowedXDirection == catenaryDirection || allowedZDirection == catenaryDirection;

        // If includeCatenary is true, then build the catenary
        if (includeCatenary && atAppropriateLocation && isOnOuterEdge && directionIsAllowed) {
          BlockVector3 point2 = Functions.findEdge(editSession, trackMask, point.add(catenaryDirection.toBlockVector()), catenaryDirection, 100);
          point2 = point2 == null ? point : point2;

          poleLocations.add(point);
          poleLocations.add(point2);

          int trackWidth = (int) point.distance(point2);
          float trackWidthMultiplier = trackWidth / normalTrackWidth;
          List<Integer> effectiveTrackPositions = normalTrackPositions.stream().map(pos -> Math.round(pos * trackWidthMultiplier)).toList();

          return buildCatenaryWithFence(poleLocations, catenaryType, editSession, point, point2, trackWidth, poleBasePattern, fencingPattern, catenaryDirection, oppositeToCatenaryDirection, catenaryHeight, trackMask, effectiveTrackPositions, fencingHeight, baseMask, replaceableBlockMask);
        } else if (!poleLocations.contains(point)) {
          return buildFence(editSession, point, fencingHeight, baseMask, replaceableBlockMask, fencingPattern, true);
        } else {
          return Stream.<SetBlockOperation>empty();
        }
      }).flatMap(Function.identity());

      // Set mask
      editSession.setMask(replaceableBlockMask);

      // Set up feedback system
      int blocksEvaluated = 0;
      final long regionSize = constants.selectedRegion().getVolume();
      constants.actor().printInfo(TextComponent.of("Creating fencing..."));

      // Do the actual modification
      operations.forEach(op -> {
        try {
          editSession.setBlock(op.point(), op.pattern());
          showFeedback(blocksEvaluated, regionSize, constants.actor());
        } catch (MaxChangedBlocksException e) {
          constants.actor().printError(e.getRichMessage());
        }
      });

      long finishTime = System.currentTimeMillis();
      constants.actor().printInfo(TextComponent.of("Fencing successfully created."));
      constants.actor().printInfo(TextComponent.of(editSession.getBlockChangeCount() + " blocks were changed in " + (finishTime - startTime) + " ms."));
      constants.localSession().remember(editSession);
      return Command.SINGLE_SUCCESS;
    }
  }

  private static void showFeedback(int blocksEvaluated, long regionSize, Actor actor) {
    // Provide feedback to user
    blocksEvaluated++;
    if (blocksEvaluated % 50000 == 0) {
      actor.printInfo(TextComponent.of(Math.round(100 * blocksEvaluated / regionSize) + "% complete"));
    }
  }

  private static IntStream getSurroundingHeightMap(BlockVector3 point, EditSession editSession, Mask baseMask, int distance, int minY, int maxY) {
    return IntStream.of(editSession.getHighestTerrainBlock(point.getX() + distance, point.getZ() + distance, 0, 320, baseMask),
        editSession.getHighestTerrainBlock(point.getX() + distance, point.getZ() - distance, minY, maxY, baseMask),
        editSession.getHighestTerrainBlock(point.getX() - distance, point.getZ() - distance, minY, maxY, baseMask),
        editSession.getHighestTerrainBlock(point.getX() - distance, point.getZ() + distance, minY, maxY, baseMask),
        editSession.getHighestTerrainBlock(point.getX() + distance, point.getZ(), minY, maxY, baseMask),
        editSession.getHighestTerrainBlock(point.getX(), point.getZ() + distance, minY, maxY, baseMask),
        editSession.getHighestTerrainBlock(point.getX() - distance, point.getZ(), minY, maxY, baseMask),
        editSession.getHighestTerrainBlock(point.getX(), point.getZ() - distance, minY, maxY, baseMask)
    );
  }

  private static Integer getTrackBlocksCount(BlockVector3 point, EditSession editSession, Mask trackMask, int[] distances, Direction direction) {
    int trackBlocksCount = 0;
    for (int distance : distances) {
      BlockVector3 origin = point.add(direction.toBlockVector().multiply(distance));

      for (int i = -distance; i < distance; i++) {
        trackBlocksCount += (editSession.getHighestTerrainBlock(origin.getX() + i * direction.toBlockVector().getZ(), origin.getZ() + i * direction.toBlockVector().getX(), 0, 320, trackMask) >= point.getY() - 1) ? 1 : 0;
      }
    }
    return trackBlocksCount;
  }

  private static Stream<SetBlockOperation> buildCatenaryWithFence(List<BlockVector3> poleLocations, CatenaryType catenaryType, EditSession editSession, BlockVector3 point1, BlockVector3 point2, int trackWidth, Pattern poleBasePattern, Pattern fencingPattern, Direction catenaryDirection, Direction oppositeToCatenaryDirection, int catenaryHeight, Mask trackMask, Collection<Integer> trackPositions, int fencingHeight, Mask baseMask, Mask replaceableBlockMask) {
    // Build pole
    int baseBlockHeight1 = Math.max(point1.getY(), editSession.getHighestTerrainBlock(point1.getX(), point1.getZ(), 0, 255, baseMask));
    int baseBlockHeight2 = Math.max(point2.getY(), editSession.getHighestTerrainBlock(point2.getX(), point2.getZ(), 0, 255, baseMask));

    int heightReduction1 = catenaryHeight - (baseBlockHeight1 - point1.getY());
    int heightReduction2 = catenaryHeight - (baseBlockHeight2 - point1.getY());

    BlockVector3 origin1 = point1.add(0, catenaryHeight, 0);
    BlockVector3 origin2 = point2.add(0, catenaryHeight, 0);

    return Stream.of(buildPole(editSession, point1, catenaryHeight, fencingHeight, baseBlockHeight1, baseMask, poleBasePattern, catenaryDirection),
        buildPole(editSession, point2, catenaryHeight, fencingHeight, baseBlockHeight2, baseMask, poleBasePattern, oppositeToCatenaryDirection),
        buildFence(editSession, origin1.add(BlockVector3.UNIT_Y), Math.max(0, fencingHeight - heightReduction1), baseMask, replaceableBlockMask, fencingPattern, false),
        buildFence(editSession, origin2.add(BlockVector3.UNIT_Y), Math.max(0, fencingHeight - heightReduction2), baseMask, replaceableBlockMask, fencingPattern, false),

        // Build racks and nodes
        // If catenary is gantry_mounted, also build a gantry
        switch (catenaryType) {
          case NONE -> Stream.<SetBlockOperation>empty();
          case POLE_MOUNTED_SINGLE -> buildNode(editSession, origin1, catenaryDirection, oppositeToCatenaryDirection);
          case POLE_MOUNTED_DOUBLE -> Stream.concat(buildNode(editSession, origin1, catenaryDirection, oppositeToCatenaryDirection), buildNode(editSession, origin2, oppositeToCatenaryDirection, catenaryDirection));
          case GANTRY_MOUNTED -> Stream.concat(buildGantry(editSession, origin1.add(BlockVector3.UNIT_Y), catenaryDirection, oppositeToCatenaryDirection, trackWidth, trackPositions, true), buildGantry(editSession, origin2.add(BlockVector3.UNIT_Y), oppositeToCatenaryDirection, catenaryDirection, trackWidth, trackPositions, false));
        }).flatMap(Function.identity());
  }

  private static Stream<SetBlockOperation> buildPole(EditSession editSession, BlockVector3 point, int catenaryHeight, int fencingHeight, int baseBlockHeight, Mask baseMask, Pattern poleBase, Direction catenaryDirection) {
    IntStream surroundingHeightMap = IntStream.range(1, 5).flatMap(i -> getSurroundingHeightMap(point, editSession, baseMask, i, 0, 320));

    BlockVector3 baseBlock = BlockVector3.at(point.getX(), baseBlockHeight, point.getZ());
    int fencingHeightBonus = Math.max(baseBlockHeight, surroundingHeightMap.max().getAsInt()) - baseBlock.getY();
    int actualFencingHeight = fencingHeight + fencingHeightBonus;

    BlockType pole = BlockTypes.get("msd:catenary_pole");

    // Build pole base and pole
    // If baseBlock is above point, then build pole base all the way past catenaryHeight and increase baseBlock Y coordinate so that fencing is built higher
    // Otherwise, only build pole base up to fencingHeight, include a rack pole, and continue in order to prevent fencing from overwriting pole base.
    return IntStream.range(0, catenaryHeight + 1).mapToObj(i -> baseBlockHeight > point.getY() || i < actualFencingHeight ? new SetBlockOperation(point.add(0, i + 1, 0), poleBase) : new SetBlockOperation(point.add(0, i + 1, 0), pole.getState(Map.of(pole.getProperty("facing"), catenaryDirection))));
  }

  private static Stream<SetBlockOperation> buildFence(EditSession editSession, BlockVector3 point, int fencingHeight, Mask baseMask, Mask replaceableBlockMask, Pattern fencingPattern, boolean applyHeightBonus) {
    IntStream surroundingHeightMap = IntStream.range(1, 5).flatMap(i -> getSurroundingHeightMap(point, editSession, baseMask, i, 0, 320));

    int baseBlockHeight = Math.max(point.getY(), editSession.getHighestTerrainBlock(point.getX(), point.getZ(), 0, 255, baseMask));
    BlockVector3 baseBlock = BlockVector3.at(point.getX(), baseBlockHeight, point.getZ());
    int actualFencingHeight = applyHeightBonus ? fencingHeight + (Math.max(baseBlockHeight, surroundingHeightMap.max().getAsInt()) - baseBlock.getY()) : fencingHeight;

    // If there is no ground above the base block, then build the fence
    return replaceableBlockMask.test(baseBlock.add(BlockVector3.UNIT_Y)) ? IntStream.range(0, actualFencingHeight).mapToObj(i -> new SetBlockOperation(baseBlock.add(0, i + 1, 0), fencingPattern)) : Stream.empty();
  }

  private static Stream<SetBlockOperation> buildNode(EditSession editSession, BlockVector3 origin, Direction catenaryDirection, Direction oppositeToCatenaryDirection) {
    BlockType catenaryRack1 = BlockTypes.get("msd:catenary_rack_1");
    BlockType catenaryRack2 = BlockTypes.get("msd:catenary_rack_2");
    BlockType catenaryNode = BlockTypes.get("msd:catenary_node");
    BlockType rackPole = BlockTypes.get("msd:catenary_rack_pole");

    return Stream.of(new SetBlockOperation(origin, rackPole.getState(Map.of(rackPole.getProperty("facing"), oppositeToCatenaryDirection))),
        new SetBlockOperation(origin.add(catenaryDirection.toBlockVector()), catenaryRack2.getState(Map.of(catenaryRack2.getProperty("facing"), oppositeToCatenaryDirection))),
        new SetBlockOperation(origin.add(catenaryDirection.toBlockVector().multiply(2)), catenaryRack1.getState(Map.of(catenaryRack1.getProperty("facing"), oppositeToCatenaryDirection))),
        new SetBlockOperation(origin.add(catenaryDirection.toBlockVector().multiply(3)), catenaryNode.getState(Map.of(catenaryNode.getProperty("facing"), catenaryDirection, catenaryNode.getProperty("is_connected"), false))));
  }

  private static Stream<SetBlockOperation> buildGantry(EditSession editSession, BlockVector3 origin, Direction catenaryDirection, Direction oppositeToCatenaryDirection, int trackWidth, Collection<Integer> trackPositions, boolean includeNode) {
    BlockType gantrySide = BlockTypes.get("msd:catenary_pole_top_side");
    BlockType gantryMiddle = BlockTypes.get("msd:catenary_pole_top_middle");

    return Stream.of(Stream.of(new SetBlockOperation(origin.add(catenaryDirection.toBlockVector()), gantrySide.getState(Map.of(gantrySide.getProperty("facing"), oppositeToCatenaryDirection)))),
        IntStream.range(2, trackWidth / 2 + 1).mapToObj(i -> new SetBlockOperation(origin.add(catenaryDirection.toBlockVector().multiply(i)), gantryMiddle.getState(Map.of(gantrySide.getProperty("facing"), catenaryDirection)))),
        IntStream.range(2, trackWidth).filter(i -> includeNode && trackPositions.contains(i)).mapToObj(i -> buildNodeUnderGantry(editSession, origin.add(catenaryDirection.toBlockVector().multiply(i)), catenaryDirection, oppositeToCatenaryDirection)).flatMap(Function.identity())).flatMap(Function.identity());
  }

  private static Stream<SetBlockOperation> buildNodeUnderGantry(EditSession editSession, BlockVector3 origin, Direction catenaryDirection, Direction oppositeToCatenaryDirection) {
    BlockType shortCatenaryRackBothSide = BlockTypes.get("msd:short_catenary_rack_both_side");
    BlockType shortCatenaryRack = BlockTypes.get("msd:short_catenary_rack");
    BlockType shortCatenaryNode = BlockTypes.get("msd:short_catenary_node");

    BlockVector3 rackLocation = origin.subtract(0, 2, 0);
    return Stream.of(new SetBlockOperation(rackLocation, shortCatenaryRackBothSide.getState(Map.of(shortCatenaryRackBothSide.getProperty("facing"), catenaryDirection))),
        new SetBlockOperation(rackLocation.add(catenaryDirection.toBlockVector()), shortCatenaryRack.getState(Map.of(shortCatenaryRack.getProperty("facing"), oppositeToCatenaryDirection))),
        new SetBlockOperation(rackLocation.add(catenaryDirection.toBlockVector().multiply(-1)), shortCatenaryRack.getState(Map.of(shortCatenaryRack.getProperty("facing"), catenaryDirection))),
        new SetBlockOperation(rackLocation.add(catenaryDirection.toBlockVector().multiply(2)), shortCatenaryNode.getState(Map.of(shortCatenaryNode.getProperty("facing"), catenaryDirection, shortCatenaryNode.getProperty("is_connected"), false))),
        new SetBlockOperation(rackLocation.add(catenaryDirection.toBlockVector().multiply(-2)), shortCatenaryNode.getState(Map.of(shortCatenaryNode.getProperty("facing"), oppositeToCatenaryDirection, shortCatenaryNode.getProperty("is_connected"), false))));
  }
}
