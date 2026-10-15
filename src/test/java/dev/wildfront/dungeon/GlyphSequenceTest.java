package dev.wildfront.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.wildfront.dungeon.GlyphSequence.Outcome;
import dev.wildfront.dungeon.GlyphSequence.Step;

class GlyphSequenceTest {
	private static final List<String> ORDER = List.of("B", "D", "A", "C");

	private static Step pressAll(String... glyphs) {
		Step step = new Step(Outcome.IGNORED, 0);
		boolean solved = false;

		for (String glyph : glyphs) {
			step = GlyphSequence.press(ORDER, step.progress(), solved, glyph);
			solved |= step.outcome() == Outcome.SOLVED;
		}

		return step;
	}

	@Test
	void theRightOrderAdvancesAndThenSolves() {
		assertEquals(new Step(Outcome.ADVANCED, 1), pressAll("B"));
		assertEquals(new Step(Outcome.ADVANCED, 2), pressAll("B", "D"));
		assertEquals(new Step(Outcome.ADVANCED, 3), pressAll("B", "D", "A"));
		assertEquals(new Step(Outcome.SOLVED, 4), pressAll("B", "D", "A", "C"));
	}

	@Test
	void aWrongGlyphStartsOverAndTheOrderCanStillBeCompleted() {
		assertEquals(new Step(Outcome.WRONG, 0), pressAll("A"));
		assertEquals(new Step(Outcome.WRONG, 0), pressAll("B", "D", "C"));
		// Pressing the same glyph twice is a mistake too.
		assertEquals(new Step(Outcome.WRONG, 0), pressAll("B", "B"));
		assertEquals(new Step(Outcome.SOLVED, 4), pressAll("B", "D", "C", "B", "D", "A", "C"));
		// A mistake right at the end costs nothing but starting over.
		assertEquals(new Step(Outcome.SOLVED, 4), pressAll("B", "D", "A", "B", "B", "D", "A", "C"));
	}

	@Test
	void glyphsOutsideTheOrderAndPressesAfterSolvingAreIgnored() {
		assertEquals(new Step(Outcome.IGNORED, 2), GlyphSequence.press(ORDER, 2, false, "E"));
		assertEquals(new Step(Outcome.IGNORED, 4), GlyphSequence.press(ORDER, 4, true, "B"));
		assertEquals(new Step(Outcome.IGNORED, 4), GlyphSequence.press(ORDER, 4, true, "A"));
	}

	@Test
	void twoPlayersTakingTurnsShareOneOrder() {
		// The temple keeps one progress; whoever presses, the presses count in the order they happen.
		Step first = GlyphSequence.press(ORDER, 0, false, "B");
		Step second = GlyphSequence.press(ORDER, first.progress(), false, "D");
		Step third = GlyphSequence.press(ORDER, second.progress(), false, "A");
		assertEquals(new Step(Outcome.SOLVED, 4), GlyphSequence.press(ORDER, third.progress(), false, "C"));
	}

	@Test
	void aProgressThatNoLongerFitsTheOrderIsTreatedAsAMistake() {
		assertEquals(new Step(Outcome.WRONG, 0), GlyphSequence.press(ORDER, 7, false, "B"));
		assertEquals(new Step(Outcome.WRONG, 0), GlyphSequence.press(ORDER, -1, false, "B"));
	}
}
