package com.codex.plunder.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import org.junit.Test;

public class GhostTest
{
	/**
	 * A scripted room: entered at a tick, then urns at the given gaps after a first search, an
	 * optional chest and sarcophagus some ticks after the last search.
	 */
	private static final class Plan
	{
		final int walkIn;
		final int urns;
		final int urnGap;
		final int chestGap;
		final int sarcGap;
		final int out;

		Plan(int walkIn, int urns, int urnGap, int chestGap, int sarcGap, int out)
		{
			this.walkIn = walkIn;
			this.urns = urns;
			this.urnGap = urnGap;
			this.chestGap = chestGap;
			this.sarcGap = sarcGap;
			this.out = out;
		}
	}

	private static Run play(Goal goal, Plan... rooms)
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, goal);
		int tick = 0;
		for (int i = 0; i < rooms.length; i++)
		{
			Room r = Room.values()[i];
			Plan p = rooms[i];
			t.room(r, tick, null);
			tick += p.walkIn;
			for (int u = 0; u < p.urns; u++)
			{
				if (u > 0)
				{
					tick += p.urnGap;
				}
				t.urnSearched(tick, null);
				t.thieving(r.getUrnXp());
			}
			if (p.chestGap > 0)
			{
				tick += p.chestGap;
				t.chestOpened(tick);
				t.thieving(r.getChestXp());
			}
			if (p.sarcGap > 0)
			{
				tick += p.sarcGap;
				t.sarcophagusOpened(tick);
			}
			tick += p.out;
		}
		return t.finish(tick);
	}

	private static Plan room(int walkIn, int urns, int urnGap, int out)
	{
		return new Plan(walkIn, urns, urnGap, 0, 0, out);
	}

	@Test
	public void aRoomsPartsAddUpToTheRoomAndTheFirstSearchBelongsToTheCrossing()
	{
		Run run = play(Goal.EXPERIENCE, room(10, 3, 4, 12), new Plan(8, 2, 5, 6, 9, 4));
		// Room 1: 10 in, two gaps of 4 between three urns, 12 out.
		assertEquals(30, Ghost.roomTicks(run, Room.ONE));
		assertEquals(8, Ghost.part(run, Room.ONE, Ghost.Part.URNS));
		assertEquals(22, Ghost.part(run, Room.ONE, Ghost.Part.CROSS));
		assertEquals(3, run.urnsAt(Room.ONE));
		// The crossing is the same time the plugin's room crossings have always kept.
		assertEquals(run.passAt(Room.ONE), Ghost.part(run, Room.ONE, Ghost.Part.CROSS));
		// Room 2, the deepest: runs to the end of the run.
		assertEquals(8 + 5 + 6 + 9 + 4, Ghost.roomTicks(run, Room.TWO));
		assertEquals(5, Ghost.part(run, Room.TWO, Ghost.Part.URNS));
		assertEquals(6, Ghost.part(run, Room.TWO, Ghost.Part.CHEST));
		assertEquals(9, Ghost.part(run, Room.TWO, Ghost.Part.SARCOPHAGUS));
		assertEquals(12, Ghost.part(run, Room.TWO, Ghost.Part.CROSS));
		assertEquals(-1, Ghost.roomTicks(run, Room.THREE));
	}

	@Test
	public void theSplitTableComparesEveryRoomAndHoldsTheLiveGapUntilItOpens()
	{
		Run ghost = play(Goal.EXPERIENCE, room(10, 3, 4, 12), room(8, 4, 4, 6));
		RunTracker t = new RunTracker();
		t.begin(0, 0, Goal.EXPERIENCE);
		t.room(Room.ONE, 0, null);
		t.urnSearched(9, null);
		t.room(Room.TWO, 26, null);
		// Room 1 took 26, four under the ghost's 30.
		List<Ghost.Row> rows = Ghost.rows(t.snapshot(40), ghost, true);
		assertEquals(2, rows.size());
		assertEquals(Integer.valueOf(-4), rows.get(0).getDelta());
		assertFalse(rows.get(0).isLive());
		// Room 2: 14 ticks in, the ghost took 26 there; under it, nothing is said yet.
		assertTrue(rows.get(1).isLive());
		assertEquals(14, rows.get(1).getMine());
		assertEquals(26, rows.get(1).getGhost());
		assertNull(rows.get(1).getDelta());
		// Past it, the gap shows.
		assertEquals(Integer.valueOf(4), Ghost.rows(t.snapshot(56), ghost, true).get(1).getDelta());
		assertEquals(Integer.valueOf(-4), Ghost.entryDelta(t.snapshot(56), ghost, Room.TWO));
	}

	@Test
	public void aSlowerRoomOfUrnsIsTheBiggestTimeSinkInPlainWords()
	{
		Run ghost = play(Goal.EXPERIENCE, room(10, 3, 4, 12), room(8, 4, 3, 6), room(9, 9, 2, 8));
		Run slow = play(Goal.EXPERIENCE, room(10, 3, 4, 12), room(8, 4, 3, 16), room(9, 9, 5, 8));
		Grade g = Grade.of(slow, ghost, Goal.EXPERIENCE);
		assertNotNull(g.getLetter());
		assertEquals(2, g.getSinks().size());
		assertEquals("Room 3: 40 ticks searching 9 urns, ghost took 16", g.getSinks().get(0).sentence());
		assertEquals(24, g.getSinks().get(0).getLost());
		assertEquals("Room 2: 24 ticks on the trap, walk and door, ghost took 14", g.getSinks().get(1).sentence());
		assertFalse(g.isNewGhost());
	}

	@Test
	public void moreUrnsThanTheGhostIsNotSlowOnlyASlowerPaceIs()
	{
		Run ghost = play(Goal.EXPERIENCE, room(10, 4, 4, 12));
		// Eight urns at the ghost's own pace: the first search is free, so 28 ticks to the 24 the
		// ghost's pace scales to, four over, under the five a sink needs.
		Run more = play(Goal.EXPERIENCE, room(10, 8, 4, 12));
		assertTrue(Grade.of(more, ghost, Goal.EXPERIENCE).getSinks().isEmpty());
		// Eight urns at six ticks each: 42 ticks to the 24 the ghost's pace gives eight.
		Run slower = play(Goal.EXPERIENCE, room(10, 8, 6, 12));
		Grade.Sink s = Grade.of(slower, ghost, Goal.EXPERIENCE).getSinks().get(0);
		assertEquals(42 - 24, s.getLost());
		assertEquals("Room 1: 42 ticks searching 8 urns, ghost took 12 for 4", s.sentence());
	}

	@Test
	public void chestsAndSarcophagiAreComparedOnlyWhenBothOpenedThem()
	{
		Run ghost = play(Goal.SCEPTRE, new Plan(10, 2, 4, 6, 0, 10));
		Run run = play(Goal.SCEPTRE, new Plan(10, 2, 4, 20, 30, 10));
		List<Grade.Sink> sinks = Grade.of(run, ghost, Goal.SCEPTRE).getSinks();
		assertEquals(1, sinks.size());
		assertEquals("Room 1: 20 ticks opening the chest, ghost took 6", sinks.get(0).sentence());
	}

	@Test
	public void atMostThreeSinksAndNoneUnderFiveTicks()
	{
		Run ghost = play(Goal.EXPERIENCE, room(5, 3, 2, 5), room(5, 3, 2, 5), room(5, 3, 2, 5), room(5, 3, 2, 5));
		Run run = play(Goal.EXPERIENCE, room(9, 3, 2, 5), room(15, 3, 2, 5), room(12, 3, 2, 5), room(25, 3, 2, 5));
		List<Grade.Sink> sinks = Grade.of(run, ghost, Goal.EXPERIENCE).getSinks();
		// Room 1 lost 4, under the five ticks a sink needs; the other three are named worst first.
		assertEquals(3, sinks.size());
		assertEquals(Room.FOUR, sinks.get(0).getRoom());
		assertEquals(Room.TWO, sinks.get(1).getRoom());
		assertEquals(Room.THREE, sinks.get(2).getRoom());
		// The deepest room's crossing runs to the end of the run, so it is not called a door.
		assertEquals("Room 4: 30 ticks not searching, ghost took 10", sinks.get(0).sentence());
	}

	@Test
	public void theLetterIsTheRateAgainstTheGhosts()
	{
		Run ghost = play(Goal.EXPERIENCE, room(10, 10, 10, 10));
		// Ghost: 10 urns at 60 xp in 110 ticks, 170 with the walk back.
		assertEquals(Grade.Letter.S, Grade.of(ghost, ghost, Goal.EXPERIENCE).getLetter());
		// 9 urns in 100 ticks: 540 xp in 160 against 600 in 170, 95.6%.
		assertEquals(Grade.Letter.A, Grade.of(play(Goal.EXPERIENCE, room(10, 9, 10, 10)), ghost, Goal.EXPERIENCE).getLetter());
		// 8 urns: 480 in 150 against 600 in 170, 90.7%.
		assertEquals(Grade.Letter.A, Grade.of(play(Goal.EXPERIENCE, room(10, 8, 10, 10)), ghost, Goal.EXPERIENCE).getLetter());
		// 7 urns: 420 in 140, 85.0%.
		assertEquals(Grade.Letter.B, Grade.of(play(Goal.EXPERIENCE, room(10, 7, 10, 10)), ghost, Goal.EXPERIENCE).getLetter());
		// 4 urns: 240 in 110, 61.8%.
		assertEquals(Grade.Letter.C, Grade.of(play(Goal.EXPERIENCE, room(10, 4, 10, 10)), ghost, Goal.EXPERIENCE).getLetter());
		// Quicker urns, same experience: past the ghost, an S and the new ghost.
		Grade better = Grade.of(play(Goal.EXPERIENCE, room(10, 10, 6, 10)), ghost, Goal.EXPERIENCE);
		assertEquals(Grade.Letter.S, better.getLetter());
		assertTrue(better.isNewGhost());
		assertTrue(better.share() > 1);
	}

	@Test
	public void theFirstRunOfItsKindHasNoLetterAndBecomesTheGhost()
	{
		Run first = play(Goal.EXPERIENCE, room(10, 3, 4, 12));
		Grade g = Grade.of(first, null, Goal.EXPERIENCE);
		assertNull(g.getLetter());
		assertTrue(g.isNewGhost());
		assertTrue(g.getSinks().isEmpty());
	}

	@Test
	public void ghostsAreKeptByGoalBracketAndDeepestRoomAndOnlyABetterRunReplacesOne()
	{
		Logbook book = new Logbook();
		Run ghost = play(Goal.EXPERIENCE, room(10, 10, 10, 10), room(10, 5, 10, 10));
		String key = Ghost.key(Goal.EXPERIENCE, Room.TWO, 2);
		assertTrue(book.offerGhost(key, ghost, Goal.EXPERIENCE));
		assertFalse(book.offerGhost(key, play(Goal.EXPERIENCE, room(10, 10, 12, 10), room(10, 5, 10, 10)), Goal.EXPERIENCE));
		assertSame(ghost, book.ghost(key));
		Run quicker = play(Goal.EXPERIENCE, room(10, 10, 8, 10), room(10, 5, 10, 10));
		assertTrue(book.offerGhost(key, quicker, Goal.EXPERIENCE));
		assertSame(quicker, book.ghost(key));
		// Other kinds have their own ghosts.
		assertNull(book.ghost(Ghost.key(Goal.SCEPTRE, Room.TWO, 2)));
		assertNull(book.ghost(Ghost.key(Goal.EXPERIENCE, Room.THREE, 2)));
		// A run under way races the ghost to the bracket's deepest room, or the deepest there is.
		assertSame(quicker, book.liveGhost(Goal.EXPERIENCE, Room.TWO));
		Run shallow = play(Goal.EXPERIENCE, room(10, 10, 10, 10));
		book.offerGhost(Ghost.key(Goal.EXPERIENCE, Room.THREE, 1), shallow, Goal.EXPERIENCE);
		assertSame(shallow, book.liveGhost(Goal.EXPERIENCE, Room.THREE));
		assertNull(book.liveGhost(Goal.SCEPTRE, Room.THREE));
	}

	@Test
	public void aRunWithoutItsSearchesTimedCannotBeAGhost()
	{
		// Saved before ghost splits: no search times.
		Run old = new Run(0, 300, 1, 600, 0, 10, 0, 0, 0, 0, 0, false, Goal.EXPERIENCE, new int[]{0, -1, -1, -1, -1, -1, -1, -1});
		assertFalse(Ghost.usable(old));
		assertFalse(new Logbook().offerGhost(Ghost.key(Goal.EXPERIENCE, Room.ONE, 1), old, Goal.EXPERIENCE));
		assertTrue(Grade.of(play(Goal.EXPERIENCE, room(10, 3, 4, 12)), old, Goal.EXPERIENCE).getSinks().isEmpty());
	}

	@Test
	public void ghostsSurviveASaveAndOutliveTheRunsKept()
	{
		Gson gson = new Gson();
		Logbook book = new Logbook();
		Run ghost = play(Goal.EXPERIENCE, room(10, 10, 10, 10), room(10, 5, 10, 10));
		String key = Ghost.key(Goal.EXPERIENCE, Room.TWO, 2);
		book.offerGhost(key, ghost, Goal.EXPERIENCE);
		book.record(ghost, new long[8]);
		for (int i = 0; i < Logbook.KEEP + 5; i++)
		{
			book.record(play(Goal.EXPERIENCE, room(10, 2, 10, 10)), new long[8]);
		}
		Logbook back = Logbook.read(gson, gson.toJson(book));
		Run kept = back.ghost(key);
		assertNotNull(kept);
		assertEquals(Logbook.KEEP, back.getRuns().size());
		assertEquals(Ghost.roomTicks(ghost, Room.TWO), Ghost.roomTicks(kept, Room.TWO));
		assertEquals(Ghost.part(ghost, Room.ONE, Ghost.Part.URNS), Ghost.part(kept, Room.ONE, Ghost.Part.URNS));
		assertEquals(ghost.urnsAt(Room.TWO), kept.urnsAt(Room.TWO));
	}

	@Test
	public void aDamagedGhostIsLostAloneAndTheRestOfTheSaveIsKept()
	{
		Gson gson = new Gson();
		Logbook book = new Logbook();
		book.setGoal(Goal.SCEPTRE);
		book.offerGhost(Ghost.key(Goal.EXPERIENCE, Room.ONE, 1), play(Goal.EXPERIENCE, room(10, 3, 4, 12)), Goal.EXPERIENCE);
		book.offerGhost(Ghost.key(Goal.EXPERIENCE, Room.TWO, 2), play(Goal.EXPERIENCE, room(10, 3, 4, 12), room(10, 3, 4, 12)), Goal.EXPERIENCE);
		String json = gson.toJson(book).replace("\"urnTicks\":[", "\"urnTicks\":\"broken\",\"x\":[");
		Logbook back = Logbook.read(gson, json);
		assertEquals(Goal.SCEPTRE, back.getGoal());
		// Both ghosts' urn times were broken, so neither can be raced: both are dropped, nothing else.
		assertEquals(0, back.ghostCount());
		Logbook whole = Logbook.read(gson, gson.toJson(book));
		assertEquals(2, whole.ghostCount());
		// A ghost that is not an object at all costs only itself.
		JsonObject odd = gson.fromJson(gson.toJson(book), JsonObject.class);
		odd.getAsJsonObject("ghosts").add("EXPERIENCE|1|1", new JsonPrimitive(7));
		Logbook oddBack = Logbook.read(gson, gson.toJson(odd));
		assertEquals(1, oddBack.ghostCount());
		assertEquals(Goal.SCEPTRE, oddBack.getGoal());
	}
}
