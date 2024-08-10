package com.example.function;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import com.mojang.brigadier.context.CommandContext;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.mask.MaskUnion;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.ClipboardPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockTypes;

import net.minecraft.server.command.ServerCommandSource;

public class Functions {
  public static final BiFunction<BlockVector3, Mask, Integer> countAdjacentBlocks = (point, mask) -> ((mask.test(point.add(1,0,0)) || mask.test(point.add(1,1,0)) || mask.test(point.add(1,-1,0))) ? 1 : 0) +
			((mask.test(point.add(0,0,1)) || mask.test(point.add(0,1,1)) || mask.test(point.add(0,-1,1))) ? 1 : 0) +
			((mask.test(point.add(-1,0,0)) || mask.test(point.add(-1,1,0)) || mask.test(point.add(-1,-1,0))) ? 1 : 0) +
			((mask.test(point.add(0,0,-1)) || mask.test(point.add(0,1,-1)) || mask.test(point.add(0,-1,-1))) ? 1 : 0) +
			((mask.test(point.add(1,0,1)) || mask.test(point.add(1,1,1)) || mask.test(point.add(1,-1,1))) ? 1 : 0) +
			((mask.test(point.add(1,0,-1)) || mask.test(point.add(1,1,-1)) || mask.test(point.add(1,-1,-1))) ? 1 : 0) +
			((mask.test(point.add(-1,0,-1)) || mask.test(point.add(-1,1,-1)) || mask.test(point.add(-1,-1,-1))) ? 1 : 0) +
			((mask.test(point.add(-1,0,1)) || mask.test(point.add(-1,1,1)) || mask.test(point.add(-1,-1,1))) ? 1 : 0);
  public static final BiPredicate<BlockVector3, Mask> occludedByMask2D = (point, mask) -> Functions.countAdjacentBlocks.apply(point, mask) == 8;

	public static final BiFunction<BlockVector3, Mask, Integer> countAdjacentBlocksCardinal = (point, mask) -> ((mask.test(point.add(1,0,0)) || mask.test(point.add(1,1,0)) || mask.test(point.add(1,-1,0))) ? 1 : 0) +
			((mask.test(point.add(0,0,1)) || mask.test(point.add(0,1,1)) || mask.test(point.add(0,-1,1))) ? 1 : 0) +
			((mask.test(point.add(-1,0,0)) || mask.test(point.add(-1,1,0)) || mask.test(point.add(-1,-1,0))) ? 1 : 0) +
			((mask.test(point.add(0,0,-1)) || mask.test(point.add(0,1,-1)) || mask.test(point.add(0,-1,-1))) ? 1 : 0);
	public static final BiPredicate<BlockVector3, Mask> occludedByMaskCardinal = (point, mask) -> Functions.countAdjacentBlocksCardinal.apply(point, mask) == 4;

	public static final BiFunction<String, ParserContext, Mask> safeParseMaskUnion = (input, context) -> {
    Stream<String> maskStrings = Stream.of(input.split(","));
    Stream<Mask> masks = maskStrings.map(string -> {
      try {
        return WorldEdit.getInstance().getMaskFactory().parseFromInput(string, context);
      } catch (InputParseException e) {
        return Masks.alwaysTrue();
      }
    });
    return new MaskUnion(masks.toList());
  };

  @SuppressWarnings("deprecation")
  public static final BiFunction<String, ParserContext, Pattern> safeParsePattern = (input, context) -> {
    try {
      return WorldEdit.getInstance().getPatternFactory().parseFromInput(input, context);
    } catch (InputParseException e) {
      return new BlockPattern(BlockTypes.SMOOTH_QUARTZ.getDefaultState());
    }
  };

	public static final Function<ParserContext, Mask> airMask = (context) -> safeParseMaskUnion.apply("minecraft:air", context);
	public static final BiFunction<Mask, ParserContext, Mask> groundMask = (trackMask, context) -> new MaskIntersection(safeParseMaskUnion.apply("##corridor_construction_tool:natural", context), Masks.negate(safeParseMaskUnion.apply("##corridor_construction_tool:natural_non_terrain", context)), Masks.negate(trackMask));

	public static final BiFunction<World, String, Clipboard> clipboardFromSchematic = (selectionWorld, schematicName) -> {
		File schematicFile = selectionWorld.getStoragePath().getParent().getParent().resolve("config/worldedit/schematics/" + schematicName + ".schem").toFile();
		ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
		try {
			try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
				return reader.read();
			}
		} catch (IOException e) {
			return null;
		}
	};

	@SuppressWarnings("deprecation")
	public static final Function<Clipboard, Pattern> patternFromClipboard = (clipboard) -> clipboard == null ? new BlockPattern(BlockTypes.SMOOTH_QUARTZ.getDefaultState()) : new ClipboardPattern(clipboard);
	public static final BiFunction<World, String, Pattern> patternFromSchematic = clipboardFromSchematic.andThen(patternFromClipboard);

	public static <V> V safeGetArgument(CommandContext<ServerCommandSource> context, String name, Class<V> clazz) {
    try {
      return context.getArgument(name, clazz);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

	public static BlockVector3 findEdge(EditSession editSession, Mask mask, BlockVector3 startPos, Direction dir, int searchDistance) {
		if (searchDistance == 0) {
			return null;
		} else if (editSession.getHighestTerrainBlock(startPos.getX(), startPos.getZ(), startPos.getY() - 2, startPos.getY() + 1, mask) == startPos.getY() - 2) {
      return startPos.subtract(dir.toBlockVector());
    } else {
      return findEdge(editSession, mask, startPos.add(dir.toBlockVector()), dir, searchDistance - 1);
    }
  }

	public static Integer distanceToEdge(EditSession editSession, Mask mask, BlockVector3 startPos, Direction dir, int searchDistance) {
		BlockVector3 otherPos = findEdge(editSession, mask, startPos.add(dir.toBlockVector()), dir, searchDistance);
		return otherPos == null ? null : (int) startPos.distance(otherPos);
  }
}
