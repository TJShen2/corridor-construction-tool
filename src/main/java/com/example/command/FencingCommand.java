package com.example.command;

import net.minecraft.block.BlockState;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import static net.minecraft.server.command.CommandManager.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import com.example.CorridorConstructionConstants;
import com.example.Functions;
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
import com.sk89q.worldedit.extension.factory.MaskFactory;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskUnion;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.Direction.Flag;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static net.minecraft.command.argument.BlockStateArgumentType.getBlockState;

public class FencingCommand {
  public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
    dispatcher.register(literal("fence")
        .requires(source -> source.hasPermissionLevel(2)) // Must be a game master to use the command. Command will not
                                                          // show up in tab completion or execute to non operators or
                                                          // any operator that is permission level 1.
        .then(argument("trackMask", BlockStateArgumentType.blockState(registryAccess))
        .then(argument("endMarkerMask", BlockStateArgumentType.blockState(registryAccess))
        .then(argument("baseMask", StringArgumentType.string()).suggests(new SuggestionProvider<ServerCommandSource>() {
          @Override
          public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
            String input = safeGetArgument(context, "baseMask", String.class);

            if (input == null || input.length() < 3) {
              return Suggestions.empty();
            } else {
              input = input.charAt(0) == '\"' ? input.substring(1) : input;
              input = input.charAt(input.length() - 1) == '\"' ? input.substring(0, input.length() - 2) : input;

              String currentInput = Arrays.asList(input.split(",")).getLast();
              int start = Math.max(context.getInput().lastIndexOf(","), context.getInput().lastIndexOf(" ")) + 1;
              List<String> suggestions = WorldEdit.getInstance().getMaskFactory().getSuggestions(currentInput);

              SuggestionsBuilder builder2 = new SuggestionsBuilder(context.getInput(), start);
              suggestions.stream().forEach(e -> builder2.suggest(e));
              return builder2.buildFuture();
            }
          }
        })
        .then(argument("fencingMaterial", BlockStateArgumentType.blockState(registryAccess))
        .then(argument("fencingHeight", IntegerArgumentType.integer())
        .then(argument("catenaryType", StringArgumentType.word()).suggests(new SuggestionProvider<ServerCommandSource>() {
          @Override
          public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
            builder.suggest("none");
            builder.suggest("pole_mounted_single");
            builder.suggest("pole_mounted_double");
            builder.suggest("gantry_mounted");
            return builder.buildFuture();
          }
        })
        .then(argument("poleBaseMaterial", BlockStateArgumentType.blockState(registryAccess))
        .then(argument("catenaryHeight", IntegerArgumentType.integer())
        .then(argument("poleSpacing", IntegerArgumentType.integer())
        .then(argument("trackPositions", StringArgumentType.string())
            .executes(ctx -> createFencing(ctx.getSource(), getBlockState(ctx, "trackMask").getBlockState(), getBlockState(ctx, "endMarkerMask").getBlockState(), getString(ctx, "baseMask"),
            getBlockState(ctx, "fencingMaterial").getBlockState(), getInteger(ctx, "fencingHeight"), getString(ctx, "catenaryType"), getBlockState(ctx, "poleBaseMaterial").getBlockState(), getInteger(ctx, "catenaryHeight"), getInteger(ctx, "poleSpacing"), getString(ctx, "trackPositions"))))))))))))));
            // You can deal with the arguments out here and pipe them into the command.
  }

  private static <V> V safeGetArgument(CommandContext<ServerCommandSource> context, String name, Class<V> clazz) {
    try {
      return context.getArgument(name, clazz);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static int createFencing(ServerCommandSource source, BlockState trackMaterial, BlockState endMarkerMaterial, String baseMasksString, BlockState fencingMaterial,
      int fencingHeight, String catenaryType, BlockState poleBaseMaterial, int catenaryHeight, int poleSpacing, String trackPositionsString) {
    CorridorConstructionConstants constants = new CorridorConstructionConstants(source);

    // Verify input
    // catenaryHeight >= fencingHeight is required
    if (fencingHeight > catenaryHeight) {
      constants.getActor().printError(TextComponent.of("Fencing height cannot be greater than catenary height."));
      return 0;
    } else if (!(catenaryType.equals("constants") || catenaryType.equals("pole_mounted_single") || catenaryType.equals("pole_mounted_double") || catenaryType.equals("gantry_mounted"))) {
      constants.getActor().printError(TextComponent.of("\"" + catenaryType + "\"" + " is not a valid catenary type. Please enter \"none\", \"pole_mounted_single\", \"pole_mounted_double\", or \"gantry_mounted\"."));
      return 0;
    }

    Collection<String> trackPositionStrings = Arrays.asList(trackPositionsString.split(","));
    Collection<Integer> trackPositions = new ArrayList<>();
    trackPositionStrings.stream().forEach(e -> trackPositions.add(Integer.valueOf(e)));

    boolean includeCatenary = catenaryType != "none";
    // Define masks
    MaskFactory maskFactory = WorldEdit.getInstance().getMaskFactory();

    List<Mask> baseMasks = new ArrayList<>();
    Mask replaceableBlockMask;

    Map<Direction, List<BlockVector3>> poleLocationsByDirection = Map.of(Direction.EAST, new ArrayList<>(), Direction.WEST, new ArrayList<>(), Direction.NORTH, new ArrayList<>(), Direction.SOUTH, new ArrayList<>());
    List<BlockVector3> poleLocations = new ArrayList<>();

    try {
      // The block types that fencing will be placed on, which is usually the material
      // of the track or the retaining walls
      String[] baseMaskStrings = baseMasksString.split(",");
      for (String baseMaskString : baseMaskStrings) {
        baseMasks.add(maskFactory.parseFromInput(baseMaskString, constants.getParserContext()));
      }

      // Set the mask for the blocks that may be replaced
      replaceableBlockMask = maskFactory.parseFromInput("##corridor_construction_tool:fencing_replaceable", constants.getParserContext());
    } catch (InputParseException e) {
      constants.getActor()
          .printError(
              TextComponent.of("The mask and pattern arguments for the command /fence may have been invalid. Individual masks in a mask union should be separated with a comma.\n" + e));
      return 0;
    }
    Mask baseMask = new MaskUnion(baseMasks);

    try (EditSession editSession = WorldEdit.getInstance().newEditSession(constants.getActor())) {
      // Define masks and patterns
      Mask trackMask = new BlockMask(editSession, FabricAdapter.adapt(trackMaterial).toBaseBlock());
      Mask endMarkerMask = new BlockMask(editSession, FabricAdapter.adapt(endMarkerMaterial).toBaseBlock());
      BaseBlock poleBase = FabricAdapter.adapt(poleBaseMaterial).toBaseBlock();
      BaseBlock fencing = FabricAdapter.adapt(fencingMaterial).toBaseBlock();
      editSession.setMask(replaceableBlockMask);

      // Provide feedback to user
      int blocksEvaluated = 0;
      long regionSize = constants.getSelectedRegion().getVolume();
      constants.getActor().printInfo(TextComponent.of("Creating fencing..."));

      // Catenaries will only be constructed from one side, so we need to store the data of which side the catenaries are constructed from
      Direction allowedXDirection = null;
      Direction allowedZDirection = null;

      // Place fences on the edges of bridges and embankments and on the retaining walls
      // Loop through all blocks in selection but only operate on track blocks that have a base block on top of them or are on edge
      for (BlockVector3 point : constants.getSelectedRegion()) {
        // Provide feedback to user
        blocksEvaluated++;
        if (blocksEvaluated % 50000 == 0) {
          constants.getActor()
              .printInfo(TextComponent.of(Math.round(100 * blocksEvaluated / regionSize) + "% complete"));
        }

        boolean isOnEdge = !Functions.occludedByMask2D.test(point, new MaskUnion(trackMask, endMarkerMask));
        boolean isOnOuterEdge = !Functions.occludedByMaskCardinal.test(point, new MaskUnion(trackMask, endMarkerMask));

        if (trackMask.test(point) && (editSession.getHighestTerrainBlock(point.getX(), point.getZ(), 0, 320, baseMask) != 0 || isOnEdge)) {
          Map<Direction, Integer> numTrackBlocksByDirection = new HashMap<>();
          Direction.valuesOf(Flag.CARDINAL).stream().forEach(dir -> numTrackBlocksByDirection.put(dir, getTrackBlocksCount(point, editSession, trackMask, IntStream.range(1, 5).toArray(), dir)));
          Direction catenaryDirection = numTrackBlocksByDirection.entrySet().stream().max((a, b) -> a.getValue().compareTo(b.getValue())).get().getKey();
          Direction oppositeToCatenaryDirection = Direction.findClosest(catenaryDirection.toVector().multiply(-1), Flag.CARDINAL);

          boolean atAppropriateLocation = poleLocationsByDirection.get(catenaryDirection).isEmpty() ? true : point.distance(poleLocationsByDirection.get(catenaryDirection).getLast()) >= poleSpacing;

          // Update allowedXDirection and allowedZDirection and perform check
          if ((catenaryDirection.equals(Direction.WEST) || catenaryDirection.equals(Direction.EAST)) && allowedXDirection == null) {
            allowedXDirection = catenaryDirection;
          } else if ((catenaryDirection.equals(Direction.NORTH) || catenaryDirection.equals(Direction.SOUTH)) && allowedZDirection == null) {
            allowedZDirection = catenaryDirection;
          }
          boolean directionIsAllowed = allowedXDirection == catenaryDirection || allowedZDirection == catenaryDirection;

          // If includeCatenary is true, then build the catenary
          try {
            if (includeCatenary && atAppropriateLocation && isOnOuterEdge && directionIsAllowed) {
              poleLocationsByDirection.get(catenaryDirection).add(point);
              buildCatenaryWithFence(poleLocations, catenaryType, editSession, point, poleBase, fencing, catenaryDirection, oppositeToCatenaryDirection, catenaryHeight, trackMask, trackPositions, fencingHeight, baseMask, replaceableBlockMask);
            } else if (!poleLocations.contains(point)) {
              buildFence(editSession, point, fencingHeight, baseMask, replaceableBlockMask, fencing, true);
            }
          } catch (MaxChangedBlocksException e) {
            constants.getActor().printError(e.getRichMessage());
          }
        }
      }

      constants.getActor().printInfo(TextComponent.of("Fencing successfully created."));
      constants.getActor().printInfo(TextComponent.of(editSession.getBlockChangeCount() + " blocks were changed."));
      constants.getLocalSession().remember(editSession);
    }
    return Command.SINGLE_SUCCESS;
  }
  private static List<Integer> getSurroundingHeightMap(BlockVector3 point, EditSession editSession, Mask baseMask, int distance, int minY, int maxY) {
    return List.of(editSession.getHighestTerrainBlock(point.getX() + distance, point.getZ() + distance, 0, 320, baseMask),
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

  private static void buildCatenaryWithFence(List<BlockVector3> poleLocations, String catenaryType, EditSession editSession, BlockVector3 point, BaseBlock poleBase, BaseBlock fencing, Direction catenaryDirection, Direction oppositeToCatenaryDirection, int catenaryHeight, Mask trackMask, Collection<Integer> trackPositions, int fencingHeight, Mask baseMask, Mask replaceableBlockMask) throws MaxChangedBlocksException {
    // Build pole
    BlockVector3 point2 = findOtherEdge(editSession, trackMask, point.add(catenaryDirection.toBlockVector()), catenaryDirection);
    poleLocations.add(point);
    poleLocations.add(point2);
    int trackWidth = (int) point.distance(point2);

    int heightReduction1 = buildPole(editSession, point, catenaryHeight, fencingHeight, baseMask, poleBase, catenaryDirection);
    int heightReduction2 = buildPole(editSession, point2, catenaryHeight, fencingHeight, baseMask, poleBase, oppositeToCatenaryDirection);

    BlockVector3 origin1 = point.add(0, catenaryHeight, 0);
    BlockVector3 origin2 = point2.add(0, catenaryHeight, 0);

    buildFence(editSession, origin1.add(BlockVector3.UNIT_Y), Math.max(0, fencingHeight - heightReduction1), baseMask, replaceableBlockMask, fencing, false);
    buildFence(editSession, origin2.add(BlockVector3.UNIT_Y), Math.max(0, fencingHeight - heightReduction2), baseMask, replaceableBlockMask, fencing, false);

    // Build racks and nodes
    // If catenary is gantry_mounted, also build a gantry
    switch (catenaryType) {
      case "pole_mounted_single":
        buildNode(editSession, origin1, catenaryDirection, oppositeToCatenaryDirection);
        break;
      case "pole_mounted_double":
        buildNode(editSession, origin1, catenaryDirection, oppositeToCatenaryDirection);
        buildNode(editSession, origin2, oppositeToCatenaryDirection, catenaryDirection);
        break;
      case "gantry_mounted":
        buildGantry(editSession, origin1.add(BlockVector3.UNIT_Y), catenaryDirection, oppositeToCatenaryDirection, trackWidth, trackPositions);
        buildGantry(editSession, origin2.add(BlockVector3.UNIT_Y), oppositeToCatenaryDirection, catenaryDirection, trackWidth, trackPositions);
        break;
    }
  }

  private static BlockVector3 findOtherEdge(EditSession editSession, Mask mask, BlockVector3 edge, Direction dir) {
    if (editSession.getHighestTerrainBlock(edge.getX(), edge.getZ(), edge.getY() - 2, edge.getY() + 1, mask) == edge.getY() - 2) {
      return edge.subtract(dir.toBlockVector());
    } else {
      return findOtherEdge(editSession, mask, edge.add(dir.toBlockVector()), dir);
    }
  }

  private static int buildPole(EditSession editSession, BlockVector3 point, int catenaryHeight, int fencingHeight, Mask baseMask, Pattern poleBase, Direction catenaryDirection) throws MaxChangedBlocksException {
    List<Integer> surroundingHeightMap = new ArrayList<>();
    IntStream.range(1, 5).forEach(i -> surroundingHeightMap.addAll(getSurroundingHeightMap(point, editSession, baseMask, i, 0, 320)));

    int baseBlockHeight = Math.max(point.getY(), editSession.getHighestTerrainBlock(point.getX(), point.getZ(), 0, 255, baseMask));
    BlockVector3 baseBlock = BlockVector3.at(point.getX(), baseBlockHeight, point.getZ());
    int fencingHeightBonus = Math.max(baseBlockHeight, Collections.max(surroundingHeightMap)) - baseBlock.getY();
    int actualFencingHeight = fencingHeight + fencingHeightBonus;

    BlockType pole = BlockTypes.get("msd:catenary_pole");

    // Build pole base and pole
    // If baseBlock is above point, then build pole base all the way past catenaryHeight and increase baseBlock Y coordinate so that fencing is built higher
    // Otherwise, only build pole base up to fencingHeight, include a rack pole, and continue in order to prevent fencing from overwriting pole base.
    for (int i = 0; i < catenaryHeight + 1; i++) {
      BlockVector3 block = point.add(0, i + 1, 0);

      if (baseBlockHeight > point.getY() || i < actualFencingHeight) {
        editSession.setBlock(block, poleBase);
      } else {
        editSession.setBlock(block, pole.getState(Map.of(pole.getProperty("facing"), catenaryDirection)));
      }
    }

    // Return the difference between the catenaryHeight and baseBlock height (is greater than 0)
    return catenaryHeight - (baseBlockHeight - point.getY());
  }

  private static void buildFence(EditSession editSession, BlockVector3 point, int fencingHeight, Mask baseMask, Mask replaceableBlockMask, BaseBlock fencing, boolean applyHeightBonus) throws MaxChangedBlocksException {
    List<Integer> surroundingHeightMap = new ArrayList<>();
    IntStream.range(1, 5).forEach(i -> surroundingHeightMap.addAll(getSurroundingHeightMap(point, editSession, baseMask, i, 0, 320)));

    int baseBlockHeight = Math.max(point.getY(), editSession.getHighestTerrainBlock(point.getX(), point.getZ(), 0, 255, baseMask));
    BlockVector3 baseBlock = BlockVector3.at(point.getX(), baseBlockHeight, point.getZ());
    int actualFencingHeight = applyHeightBonus ? fencingHeight + (Math.max(baseBlockHeight, Collections.max(surroundingHeightMap)) - baseBlock.getY()) : fencingHeight;

    // If there is no ground above the base block, then build the fence
    if (replaceableBlockMask.test(baseBlock.add(BlockVector3.UNIT_Y))) {
      for (int i = 0; i < actualFencingHeight; i++) {
        editSession.setBlock(baseBlock.add(0, i + 1, 0), fencing);
      }
    }
  }

  private static void buildNode(EditSession editSession, BlockVector3 origin, Direction catenaryDirection, Direction oppositeToCatenaryDirection) throws MaxChangedBlocksException {
    BlockType catenaryRack1 = BlockTypes.get("msd:catenary_rack_1");
    BlockType catenaryRack2 = BlockTypes.get("msd:catenary_rack_2");
    BlockType catenaryNode = BlockTypes.get("msd:catenary_node");
    BlockType rackPole = BlockTypes.get("msd:catenary_rack_pole");

    editSession.setBlock(origin, rackPole.getState(Map.of(rackPole.getProperty("facing"), oppositeToCatenaryDirection)));
    editSession.setBlock(origin.add(catenaryDirection.toBlockVector()), catenaryRack2.getState(Map.of(catenaryRack2.getProperty("facing"), oppositeToCatenaryDirection)));
    editSession.setBlock(origin.add(catenaryDirection.toBlockVector().multiply(2)), catenaryRack1.getState(Map.of(catenaryRack1.getProperty("facing"), oppositeToCatenaryDirection)));
    editSession.setBlock(origin.add(catenaryDirection.toBlockVector().multiply(3)), catenaryNode.getState(Map.of(catenaryNode.getProperty("facing"), catenaryDirection, catenaryNode.getProperty("is_connected"), false)));
  }

  private static void buildGantry(EditSession editSession, BlockVector3 origin, Direction catenaryDirection, Direction oppositeToCatenaryDirection, int trackWidth, Collection<Integer> trackPositions) throws MaxChangedBlocksException {
    BlockType gantrySide = BlockTypes.get("msd:catenary_pole_top_side");
    BlockType gantryMiddle = BlockTypes.get("msd:catenary_pole_top_middle");

    editSession.setBlock(origin.add(catenaryDirection.toBlockVector()), gantrySide.getState(Map.of(gantrySide.getProperty("facing"), oppositeToCatenaryDirection)));
    for (int i = 2; i < trackWidth / 2 + 1; i++) {
      editSession.setBlock(origin.add(catenaryDirection.toBlockVector().multiply(i)), gantryMiddle.getState(Map.of(gantrySide.getProperty("facing"), catenaryDirection)));
      if (trackPositions.contains(i)) {
        buildNodeUnderGantry(editSession, origin.add(catenaryDirection.toBlockVector().multiply(i)), catenaryDirection, oppositeToCatenaryDirection);
      }
    }
  }

  private static void buildNodeUnderGantry(EditSession editSession, BlockVector3 origin, Direction catenaryDirection, Direction oppositeToCatenaryDirection) throws MaxChangedBlocksException {
    BlockType shortCatenaryRackBothSide = BlockTypes.get("msd:short_catenary_rack_both_side");
    BlockType shortCatenaryRack = BlockTypes.get("msd:short_catenary_rack");
    BlockType shortCatenaryNode = BlockTypes.get("msd:short_catenary_node");

    BlockVector3 rackLocation = origin.subtract(0, 2, 0);
    editSession.setBlock(rackLocation, shortCatenaryRackBothSide.getState(Map.of(shortCatenaryRackBothSide.getProperty("facing"), catenaryDirection)));
    editSession.setBlock(rackLocation.add(catenaryDirection.toBlockVector()), shortCatenaryRack.getState(Map.of(shortCatenaryRack.getProperty("facing"), oppositeToCatenaryDirection)));
    editSession.setBlock(rackLocation.add(catenaryDirection.toBlockVector().multiply(-1)), shortCatenaryRack.getState(Map.of(shortCatenaryRack.getProperty("facing"), catenaryDirection)));
    editSession.setBlock(rackLocation.add(catenaryDirection.toBlockVector().multiply(2)), shortCatenaryNode.getState(Map.of(shortCatenaryNode.getProperty("facing"), catenaryDirection, shortCatenaryNode.getProperty("is_connected"), false)));
    editSession.setBlock(rackLocation.add(catenaryDirection.toBlockVector().multiply(-2)), shortCatenaryNode.getState(Map.of(shortCatenaryNode.getProperty("facing"), oppositeToCatenaryDirection, shortCatenaryNode.getProperty("is_connected"), false)));
  }
}
