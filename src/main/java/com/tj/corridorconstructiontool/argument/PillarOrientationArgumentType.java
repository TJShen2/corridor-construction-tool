package com.tj.corridorconstructiontool.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.tj.corridorconstructiontool.command.ElevatedAlignmentCommand.PillarOrientation;

import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an ArgumentType that will return a PillarOrientation.
 */
public class PillarOrientationArgumentType implements ArgumentType<PillarOrientation> {
	public static final DynamicCommandExceptionType INVALID_PILLAR_ORIENTATION = new DynamicCommandExceptionType(
			o -> Text.literal("Invalid pillar orientation: " + o));

	public static PillarOrientationArgumentType orientation() {
		return new PillarOrientationArgumentType();
	}

	public static <S> PillarOrientation getOrientation(CommandContext<S> context, String name) {
		return context.getArgument(name, PillarOrientation.class);
	}

	private static final Collection<String> EXAMPLES = List.of("longitudinal", "transverse", "unspecified");

	@Override
	public PillarOrientation parse(StringReader reader) throws CommandSyntaxException {
		int argBeginning = reader.getCursor(); // The starting position of the cursor is at the beginning of the argument.
		if (!reader.canRead()) {
			reader.skip();
		}

		// Parse the contents of the argument until we reach the end of the
		// command line (when 'canRead' becomes false) or until we reach a non-letter character
		while (reader.canRead() && (Character.isLetter(reader.peek()))) { // peek provides the character at the current cursor position.
			reader.skip(); // Tells the StringReader to move it's cursor to the next position.
		}

		// Substring the part between the starting cursor position and the beginning of the next argument.
		String orientationString = reader.getString().substring(argBeginning, reader.getCursor());
		PillarOrientation orientation = PillarOrientation.fromString(orientationString); // Now our actual logic.
		return orientation;
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
