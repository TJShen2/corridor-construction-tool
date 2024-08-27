package com.example;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.example.function.Functions;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.pattern.ClipboardPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;

public class SchematicPatternParser extends InputParser<Pattern> {

	public SchematicPatternParser(WorldEdit worldEdit) {
		super(worldEdit);
	}

	@Override
	public Stream<String> getSuggestions(String input) {
		if (input.isEmpty()) {
			return Stream.of("$");
    } else if (input.equals("$")) {
			return Stream.concat(Stream.of("$"), getSchematicNameSuggestions());
		} else {
			String[] offsetParts = input.split("@", 2);

			if (offsetParts.length == 2) {
				String coords = offsetParts[1];
				if (coords.isEmpty()) {
					return Stream.of(input + "[x,y,z]");
				}
			} else {
				return getSchematicNameSuggestions().flatMap(name -> Stream.of("$" + name, "$" + name + "@[x,y,z]"));
			}
			return Stream.empty();
		}
	}

	private Stream<String> getSchematicNameSuggestions() {
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
	}

	@Override
	public Pattern parseFromInput(String input, ParserContext context) throws InputParseException {
		if (!input.startsWith("$")) {
			return null;
		}
		String withoutSymbol = input.substring(1);

		String[] offsetParts = withoutSymbol.split("@", 2);
		BlockVector3 offset = BlockVector3.ZERO;
		if (offsetParts.length == 2) {
			String coords = offsetParts[1];
			if (coords.length() < 7 // min length of `[x,y,z]`
					|| coords.charAt(0) != '[' || coords.charAt(coords.length() - 1) != ']') {
				throw new InputParseException(TranslatableComponent.of("worldedit.error.parser.clipboard.missing-offset"));
			}
			String[] offsetSplit = coords.substring(1, coords.length() - 1).split(",");
			if (offsetSplit.length != 3) {
				throw new InputParseException(TranslatableComponent.of("worldedit.error.parser.clipboard.missing-coordinates"));
			}
			offset = BlockVector3.at(
					Integer.parseInt(offsetSplit[0]),
					Integer.parseInt(offsetSplit[1]),
					Integer.parseInt(offsetSplit[2]));
		}
		Clipboard clipboard = Functions.clipboardFromSchematic.apply(withoutSymbol);
		return new ClipboardPattern(clipboard, offset);
	}

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
}
