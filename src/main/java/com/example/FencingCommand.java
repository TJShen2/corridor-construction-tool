package com.example;

import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.factory.MaskFactory;
import com.sk89q.worldedit.extension.factory.PatternFactory;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskUnion;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;

public class FencingCommand {
  public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
    dispatcher.register(literal("fence")
        .requires(source -> source.hasPermissionLevel(2)) // Must be a game master to use the command. Command will not
                                                          // show up in tab completion or execute to non operators or
                                                          // any operator that is permission level 1.
        .then(argument("baseMask", StringArgumentType.string())
            .then(argument("fencingMaterial", StringArgumentType.string())
                .then(argument("fencingHeight", IntegerArgumentType.integer())
                    .executes(ctx -> createFencing(ctx.getSource(), getString(ctx, "baseMask"),
                        getString(ctx, "fencingMaterial"), getInteger(ctx, "fencingHeight"))))))); // You can deal with
                                                                                                   // the arguments out
                                                                                                   // here and pipe them
                                                                                                   // into the command.
  }

  private static int createFencing(ServerCommandSource source, String baseMasksString, String fencingMaterialString,
      int fencingHeight) {
    CorridorConstructionConstants constants = new CorridorConstructionConstants(source);

    // Define masks and patterns
    MaskFactory maskFactory = WorldEdit.getInstance().getMaskFactory();
    PatternFactory patternFactory = WorldEdit.getInstance().getPatternFactory();

    List<Mask> baseMasks = new ArrayList<>();
    Mask replaceableBlockMask;
    Pattern fencingMaterial;

    try {
      // The block types that fencing will be placed on, which is usually the material
      // of the track or the retaining walls
      String[] baseMaskStrings = baseMasksString.split(",");
      for (String baseMaskString : baseMaskStrings) {
        baseMasks.add(maskFactory.parseFromInput(baseMaskString, constants.getParserContext()));
      }
      fencingMaterial = patternFactory.parseFromInput(fencingMaterialString, constants.getParserContext());

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
      editSession.setMask(replaceableBlockMask);

      // Provide feedback to user
      int blocksEvaluated = 0;
      long regionSize = constants.getSelectedRegion().getVolume();
      constants.getActor().printInfo(TextComponent.of("Creating fencing..."));

      // Loop through all blocks in selection but filter out the ones that do not
      // satisfy baseMask, are not the highest baseMask-conforming block in their column, or have a ground block on top of them
      for (BlockVector3 point : constants.getSelectedRegion()) {
        int heightMap = editSession.getHighestTerrainBlock(point.getX(), point.getZ(), 0, 255, baseMask);

        if (baseMask.test(point) && heightMap == point.getY() && replaceableBlockMask.test(point.add(BlockVector3.UNIT_Y))) {
          // Place fences on the edges of bridges and embankments and on the retaining walls
          List<Integer> surroundingHeightMap = new ArrayList<>(getSurroundingHeightMap(point, editSession, baseMask, (byte) 1));

          long neighbouringTrackBlocksCount = surroundingHeightMap.stream().filter(e -> (e != 0)).count();
          boolean isOnEdge = neighbouringTrackBlocksCount < 8;

          if (isOnEdge) {
            for (int i = 2; i < 5; i++) {
              surroundingHeightMap.addAll(getSurroundingHeightMap(point, editSession, baseMask, (byte) i));
            }
            int fencingHeightBonus = Math.max(heightMap, Collections.max(surroundingHeightMap)) - point.getY();
            for (int i = 1; i < fencingHeight + fencingHeightBonus + 1; i++) {
              try {
                editSession.setBlock(point.add(0, i, 0), fencingMaterial);
              } catch (MaxChangedBlocksException e) {
                constants.getActor().printError(TextComponent.of(e.toString()));
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

      constants.getActor().printInfo(TextComponent.of("Fencing successfully created."));
      constants.getActor().printInfo(TextComponent.of(editSession.getBlockChangeCount() + " blocks were changed."));
      constants.getLocalSession().remember(editSession);
    }
    return Command.SINGLE_SUCCESS;
  }
  private static List<Integer> getSurroundingHeightMap(BlockVector3 point, EditSession editSession, Mask baseMask, byte distance) {
    return List.of(editSession.getHighestTerrainBlock(point.getX() + distance, point.getZ() + distance, 0, 320, baseMask),
        editSession.getHighestTerrainBlock(point.getX() + distance, point.getZ() - distance, 0, 320, baseMask),
        editSession.getHighestTerrainBlock(point.getX() - distance, point.getZ() - distance, 0, 320, baseMask),
        editSession.getHighestTerrainBlock(point.getX() - distance, point.getZ() + distance, 0, 320, baseMask),
        editSession.getHighestTerrainBlock(point.getX() + distance, point.getZ(), 0, 320, baseMask),
        editSession.getHighestTerrainBlock(point.getX(), point.getZ() + distance, 0, 320, baseMask),
        editSession.getHighestTerrainBlock(point.getX() - distance, point.getZ(), 0, 320, baseMask),
        editSession.getHighestTerrainBlock(point.getX(), point.getZ() - distance, 0, 320, baseMask)
    );
  }
}
