package com.tj.corridorconstructiontool.factory.parser.pattern;

import java.util.stream.Stream;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.pattern.ClipboardPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.tj.corridorconstructiontool.function.Functions;

public class SchematicPatternParser extends InputParser<Pattern> {

	public SchematicPatternParser(WorldEdit worldEdit) {
		super(worldEdit);
	}

	@Override
	public Stream<String> getSuggestions(String input) {
		if (input.isEmpty()) {
			return Stream.of("$");
    }
		if (input.startsWith("$")) {
			if (input.equals("$")) {
				return Functions.getSchematicNames.get().map(s -> "$" + s);
			} else {
				String[] offsetParts = input.split("@", 2);

				if (offsetParts.length == 2) {
					String coords = offsetParts[1];
					if (coords.isEmpty()) {
						return Stream.of(input + "[x,y,z]");
					}
				} else {
					return Functions.getSchematicNames.get().flatMap(name -> Stream.of("$" + name, "$" + name + "@[x,y,z]"));
				}
			}
		}
		return Stream.empty();
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
		Clipboard clipboard = Functions.unsafeClipboardFromSchematic(withoutSymbol);
		return new ClipboardPattern(clipboard, offset);
	}
}
