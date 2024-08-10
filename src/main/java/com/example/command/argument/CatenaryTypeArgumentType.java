package com.example.command.argument;

import com.example.command.FencingCommand.CatenaryType;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an ArgumentType that will return a CatenaryType.
 */
public class CatenaryTypeArgumentType implements ArgumentType<CatenaryType> {
	public static final DynamicCommandExceptionType INVALID_CATENARY_TYPE = new DynamicCommandExceptionType(
			o -> Text.literal("Invalid catenary type: " + o));

	public static CatenaryTypeArgumentType catenaryType() {
		return new CatenaryTypeArgumentType();
	}

	public static <S> CatenaryType getCatenaryType(CommandContext<S> context, String name) {
		return context.getArgument(name, CatenaryType.class);
	}

	private static final Collection<String> EXAMPLES = List.of("none", "pole_mounted_single", "pole_mounted_double", "gantry_mounted");

	@Override
	public CatenaryType parse(StringReader reader) throws CommandSyntaxException {
		int argBeginning = reader.getCursor(); // The starting position of the cursor is at the beginning of the argument.
		if (!reader.canRead()) {
			reader.skip();
		}

		// Parse the contents of the argument until we reach the end of the
		// command line (when 'canRead' becomes false) or until we reach a non-letter character
		while (reader.canRead() && (Character.isLetter(reader.peek()) || reader.peek() == '_')) { // peek provides the character at the current cursor position.
			reader.skip(); // Tells the StringReader to move it's cursor to the next position.
		}

		// Substring the part between the starting cursor position and the beginning of the next argument.
		String typeString = reader.getString().substring(argBeginning, reader.getCursor());
		CatenaryType type = CatenaryType.fromString(typeString); // Now our actual logic.
		return type;
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}

  @Override
  public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
    return CommandSource.suggestMatching(EXAMPLES, builder);
  }
}
