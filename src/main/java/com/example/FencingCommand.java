package com.example;

import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.*;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.function.mask.Mask;
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

	public static int createFencing(ServerCommandSource source, String baseMaskString, String fencingMaterialString,
			int fencingHeight) {
		CorridorConstructionConstants constants = new CorridorConstructionConstants(source);

    Mask baseMask;
    Mask replaceableBlockMask;
    Pattern fencingMaterial;
    try {
      //The block types that fencing will be placed on, which is usually the material of the track or the retaining walls
      baseMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(baseMaskString, constants.getParserContext());
      //Set the mask for the blocks that may be replaced
      replaceableBlockMask = WorldEdit.getInstance().getMaskFactory().parseFromInput("##transit_corridor:railway_embankment_replaceable", constants.getParserContext());
      fencingMaterial = WorldEdit.getInstance().getPatternFactory().parseFromInput(fencingMaterialString, constants.getParserContext());
    } catch (InputParseException e) {
      constants.getActor()
					.printError(TextComponent.of("The mask and pattern arguments for the command /fence may have been invalid\n" + e));
			return 0;
    }

    try (EditSession editSession = WorldEdit.getInstance().newEditSession(constants.getActor())) {
      editSession.setMask(replaceableBlockMask);

      //Provide feedback to user
      int blocksEvaluated = 0;
      long regionSize = constants.getSelectedRegion().getVolume();
      constants.getActor().printInfo(TextComponent.of("Creating fencing..."));

      //Loop through all blocks in selection
      for (BlockVector3 point : constants.getSelectedRegion()) {
          if (baseMask.test(point)) {
              int neighbouringTrackBlocksCount = 0;
              boolean[] neighbouringTrackBlocks2D = new boolean[] {
                  baseMask.test(point.add(1,0,0)) || baseMask.test(point.add(1,1,0)) || baseMask.test(point.add(1,-1,0)),
                  baseMask.test(point.add(0,0,1)) || baseMask.test(point.add(0,1,1)) || baseMask.test(point.add(0,-1,1)),
                  baseMask.test(point.add(-1,0,0)) || baseMask.test(point.add(-1,1,0)) || baseMask.test(point.add(-1,-1,0)),
                  baseMask.test(point.add(0,0,-1)) || baseMask.test(point.add(0,1,-1)) || baseMask.test(point.add(0,-1,-1)),
                  baseMask.test(point.add(1,0,1)) || baseMask.test(point.add(1,1,1)) || baseMask.test(point.add(1,-1,1)),
                  baseMask.test(point.add(1,0,-1)) || baseMask.test(point.add(1,1,-1)) || baseMask.test(point.add(1,-1,-1)),
                  baseMask.test(point.add(-1,0,-1)) || baseMask.test(point.add(-1,1,-1)) || baseMask.test(point.add(-1,-1,-1)),
                  baseMask.test(point.add(-1,0,1)) || baseMask.test(point.add(-1,1,1)) || baseMask.test(point.add(-1,-1,1))
              };
              for (int i = 0; i < neighbouringTrackBlocks2D.length; i++) {
                  if (neighbouringTrackBlocks2D[i]) {
                      neighbouringTrackBlocksCount += 1;
                  }
              }
              boolean isOnEdge = neighbouringTrackBlocksCount < 8;

              if (replaceableBlockMask.test(point.add(0,1,0)) && ((baseMask.test(point.subtract(0,1,0)) && baseMask.test(point.subtract(0,2,0))) || isOnEdge)) {
                  for (int i = 1; i < fencingHeight + 1; i++) {
                    try {
                      editSession.setBlock(point.add(0,i,0), fencingMaterial);
                    } catch (MaxChangedBlocksException e) {
                      constants.getActor().printError(TextComponent.of(e.toString()));
                    }
                  }
              }
          }
          blocksEvaluated++;
          if (blocksEvaluated % 50000 == 0) {
              constants.getActor().printInfo(TextComponent.of(Math.round(100 * blocksEvaluated / regionSize) + "% complete"));
          }
      }

      constants.getActor().printInfo(TextComponent.of("Fencing successfully created."));
      constants.getActor().printInfo(TextComponent.of(editSession.getBlockChangeCount() + " blocks were changed."));
      constants.getLocalSession().remember(editSession);
    }
    return Command.SINGLE_SUCCESS;
  }
}
