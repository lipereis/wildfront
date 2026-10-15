package dev.wildfront.dungeon;

import java.util.List;

/**
 * The rule of the Forgotten Temple's puzzle: glyphs must be woken in a set order. Contains no
 * Minecraft types so the rule can be unit tested.
 *
 * <p>A wrong glyph starts the order over; nothing is lost and nothing breaks, so the puzzle can
 * always be tried again. Glyphs that are not part of the order are ignored.
 */
public final class GlyphSequence {
	public enum Outcome {
		/** The glyph was the next one; more are to come. */
		ADVANCED,
		/** The glyph was the last one: the puzzle is solved. */
		SOLVED,
		/** The glyph was not the next one: the order starts over. */
		WRONG,
		/** The glyph is not part of the order, or the puzzle was solved already. */
		IGNORED
	}

	/**
	 * @param outcome  what the press did
	 * @param progress how many glyphs of the order have been woken after the press
	 */
	public record Step(Outcome outcome, int progress) {
	}

	private GlyphSequence() {
	}

	/**
	 * @param solution the glyphs in the order they must be woken; none twice
	 * @param progress how many of them have been woken so far
	 * @param solved   whether the puzzle was solved already
	 * @param glyph    the glyph whose button was pressed
	 */
	public static <T> Step press(List<T> solution, int progress, boolean solved, T glyph) {
		if (solved || !solution.contains(glyph)) {
			return new Step(Outcome.IGNORED, progress);
		}

		if (progress >= 0 && progress < solution.size() && solution.get(progress).equals(glyph)) {
			int next = progress + 1;
			return new Step(next == solution.size() ? Outcome.SOLVED : Outcome.ADVANCED, next);
		}

		return new Step(Outcome.WRONG, 0);
	}
}
