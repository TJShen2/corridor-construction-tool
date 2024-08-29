package com.example.function;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
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
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Contains several functions and class methods for getting input from the Minecraft world through the WorldEdit API.
 * @author TJ Shen
 * @version 1.0.0
 */
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

	public static final Function<String, Clipboard> clipboardFromSchematic = (schematicName) -> {
		String schematicSaveDir = WorldEdit.getInstance().getConfiguration().saveDir;
		File schematicFile = WorldEdit.getInstance().getWorkingDirectoryPath(schematicSaveDir).resolve(schematicName + ".schem").toFile();
		ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
		try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
			return reader.read();
		} catch (IOException e) {
			return null;
		}
	};

	public static Clipboard unsafeClipboardFromSchematic(String schematicName) throws InputParseException {
		String schematicSaveDir = WorldEdit.getInstance().getConfiguration().saveDir;
		File schematicFile = WorldEdit.getInstance().getWorkingDirectoryPath(schematicSaveDir).resolve(schematicName).toFile();
		ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
		try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
			return reader.read();
		} catch (IOException e) {
			throw new InputParseException(TextComponent.of("No matching schematics found."));
		}
	};

	public static final Function<Clipboard, Pattern> patternFromClipboard = (clipboard) -> clipboard == null ? null : new ClipboardPattern(clipboard);
	public static final Function<String, Pattern> patternFromSchematic = clipboardFromSchematic.andThen(patternFromClipboard);

	public static final Supplier<Stream<String>> getSchematicNames = () -> {
		final String saveDir = WorldEdit.getInstance().getConfiguration().saveDir;
		Path rootDir = WorldEdit.getInstance().getWorkingDirectoryPath(saveDir);

		List<Path> fileList;
		try {
			Path resolvedRoot = rootDir.toRealPath();
			fileList = allFiles(resolvedRoot);
		} catch (IOException e) {
			return Stream.empty();
		}

		return fileList.stream().map(file -> file.getFileName().toString());
	};

	private static List<Path> allFiles(Path root) throws IOException {
		List<Path> pathList = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
			for (Path path : stream) {
				if (Files.isDirectory(path)) {
					pathList.addAll(allFiles(path));
				} else {
					pathList.add(path);
				}
			}
		}
		return pathList;
	}

	public static <V> V safeGetArgument(CommandContext<ServerCommandSource> context, String name, Class<V> clazz) {
    try {
      return context.getArgument(name, clazz);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

	public static BlockVector3 findEdge(EditSession editSession, Mask mask, BlockVector3 startPos, Direction dir, int horizontalSearchDistance, int verticalSearchDistance) {
		int highestYPos = editSession.getHighestTerrainBlock(startPos.getX(), startPos.getZ(), startPos.getY() - verticalSearchDistance, startPos.getY() + verticalSearchDistance, mask);
		if (horizontalSearchDistance == 0) {
			return null;
		} else if (highestYPos == startPos.getY() - verticalSearchDistance) {
      return startPos.subtract(dir.toBlockVector());
    } else {
      return findEdge(editSession, mask, startPos.withY(highestYPos).add(dir.toBlockVector()), dir, horizontalSearchDistance - 1, verticalSearchDistance);
    }
  }

	public static int distanceToEdge(EditSession editSession, Mask mask, BlockVector3 startPos, Direction dir, int horizontalSearchDistance, int verticalSearchDistance) {
		BlockVector3 otherPos = findEdge(editSession, mask, startPos.add(dir.toBlockVector()), dir, horizontalSearchDistance, verticalSearchDistance);
		return otherPos == null ? -1 : (int) startPos.distance(otherPos);
  }
}
