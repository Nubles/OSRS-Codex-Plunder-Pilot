package com.codex.plunder.sepulchre;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** The Sepulchre's run log: what the game says, how a run is timed and counted, the ghost, the pace and the odds. */
public class SepulchreTest
{
	private static final Gson GSON = new Gson();

	// What the game says, in the wording RuneLite's Chat Commands plugin and its tests read.

	@Test
	public void readsAFloorFinished()
	{
		SepulchreChat.Heard h = SepulchreChat.read("You have completed Floor 5 of the Hallowed Sepulchre! Total completions: <col=ff0000>1,114</col>.");
		assertNotNull(h);
		assertEquals(5, h.floorDone);
		assertFalse(h.grand);
	}

	@Test
	public void readsAFloorTimeWithAPersonalBestAndTheOverallTime()
	{
		SepulchreChat.Heard h = SepulchreChat.read("Floor 2 time: <col=ff0000>1:12.60</col>. Personal best: 1:05.40");
		assertEquals(2, h.timedFloor);
		assertEquals(72_600, h.floorMs);
		assertEquals(-1, h.overallMs);
		h = SepulchreChat.read("Floor 5 time: <col=ff0000>2:01.20</col> (new personal best)<br>Overall time: <col=ff0000>6:44.40</col>. Personal best: 6:30.00");
		assertEquals(5, h.timedFloor);
		assertEquals(121_200, h.floorMs);
		assertEquals(404_400, h.overallMs);
	}

	@Test
	public void readsTheGrandCoffinAndIgnoresTheRest()
	{
		assertTrue(SepulchreChat.read("You have opened the Grand Hallowed Coffin <col=ff0000>1,542</col> times!").grand);
		assertNull(SepulchreChat.read("You have completed Floor 9 of the Hallowed Sepulchre!"));
		assertNull(SepulchreChat.read("Your Pharaoh's sceptre has 3 charges left."));
		assertNull(SepulchreChat.read(null));
		assertEquals(3_723_600, SepulchreChat.ms("1:02:03.6"));
		assertEquals(-1, SepulchreChat.ms("x:12"));
	}

	// The tracker.

	/** A run: floors finished every so many ticks, with coffins giving so many marks on each. */
	static SepulchreRun run(long at, int[] ticks, int[][] coffins, boolean grand)
	{
		SepulchreTracker t = new SepulchreTracker();
		t.begin(at, 100);
		int tick = 100;
		for (int f = 1; f <= ticks.length; f++)
		{
			for (int marks : coffins.length >= f ? coffins[f - 1] : new int[0])
			{
				t.marks(marks);
			}
			tick += ticks[f - 1];
			assertTrue(t.floorDone(f, tick));
		}
		if (grand)
		{
			t.marks(9);
			t.grandOpened();
		}
		t.agility(Floor.xpTo(ticks.length));
		return t.finish(tick + 5);
	}

	@Test
	public void aRunIsTimedFloorByFloorAndItsCoffinsCountedBySize()
	{
		SepulchreRun r = run(0, new int[]{100, 150, 200}, new int[][]{{1}, {2, 3}, {5, 4, 3}}, false);
		assertEquals(3, r.getDeepest());
		assertEquals(60_000, r.floorTime(1));
		assertEquals(90_000, r.floorTime(2));
		assertEquals(270_000, r.timeTo(3));
		assertEquals(6, r.getCoffins());
		assertEquals(1, r.coffinsOn(1));
		assertEquals(3, r.coffinsOn(3));
		assertEquals(18, r.getMarks());
		assertFalse(r.isGrand());
		assertEquals(3_100, r.getAgilityXp());
	}

	@Test
	public void eightOrMoreMarksAtOnceIsTheGrandCoffin()
	{
		SepulchreRun r = run(0, new int[]{60, 60, 60, 60, 60}, new int[][]{{1}, {2}, {3}, {6}, {4, 6}}, true);
		assertTrue(r.isGrand());
		assertEquals(9, r.getGrandMarks());
		assertEquals(6, r.getCoffins());
		assertEquals(22 + 9, r.getMarks());
	}

	@Test
	public void theGamesOwnFloorTimeIsPreferredToTheTickCount()
	{
		SepulchreTracker t = new SepulchreTracker();
		t.begin(0, 0);
		t.floorTime(1, 58_200, -1);
		assertTrue(t.floorDone(1, 100));
		SepulchreRun r = t.finish(110);
		assertEquals(58_200, r.floorTime(1));
		assertTrue(r.isGameTimed(1));
	}

	@Test
	public void floorOneAgainIsANewRun()
	{
		SepulchreTracker t = new SepulchreTracker();
		t.begin(0, 0);
		assertTrue(t.floorDone(1, 90));
		assertTrue(t.floorDone(2, 200));
		assertFalse(t.floorDone(1, 500));
		assertEquals(3, t.getFloor());
	}

	@Test
	public void aRunFirstSeenPartWayHasNoTimeForTheFloorsItMissed()
	{
		SepulchreTracker t = new SepulchreTracker();
		t.begin(0, 50);
		assertTrue(t.floorDone(3, 50));
		assertTrue(t.floorDone(4, 150));
		SepulchreRun r = t.finish(160);
		assertEquals(-1, r.floorTime(1));
		assertEquals(-1, r.floorTime(3));
		assertEquals(60_000, r.floorTime(4));
		assertEquals(-1, r.timeTo(4));
		assertFalse(SepulchreLog.usable(r));
	}

	@Test
	public void aTripThatFinishesNothingAndFindsNothingIsNotKept()
	{
		SepulchreTracker t = new SepulchreTracker();
		t.begin(0, 0);
		assertNull(t.finish(40));
	}

	// The ghost.

	@Test
	public void theFastestRunToEachDepthIsItsGhost()
	{
		SepulchreLog log = new SepulchreLog();
		SepulchreRun slow = run(0, new int[]{100, 150, 200}, new int[][]{}, false);
		SepulchreRun fast = run(1, new int[]{90, 140, 190}, new int[][]{}, false);
		SepulchreRun shallow = run(2, new int[]{80, 120}, new int[][]{}, false);
		assertTrue(log.offerGhost(slow));
		assertTrue(log.offerGhost(fast));
		assertFalse(log.offerGhost(slow));
		assertTrue(log.offerGhost(shallow));
		assertSame(fast, log.ghost(3));
		assertSame(shallow, log.ghost(2));
		// With Agility for four floors and no ghost to floor 4, the run races the one to floor 3.
		assertSame(fast, log.liveGhost(4));
		assertSame(shallow, log.liveGhost(2));
		assertEquals(2, log.ghostCount());
	}

	@Test
	public void splitsShowTheGapPerFloorAndOnlyOverrunsOnTheFloorUnderWay()
	{
		SepulchreRun ghost = run(0, new int[]{100, 150, 200}, new int[][]{}, false);
		SepulchreTracker t = new SepulchreTracker();
		t.begin(0, 0);
		t.floorDone(1, 95);
		t.floorDone(2, 255);
		SepulchreRun now = t.snapshot(300);
		// 45 ticks into floor 3: inside the ghost's 200, so nothing said for it yet.
		FloorSplits s = FloorSplits.of(now, ghost, t.getFloor(), t.onFloor(300) * SepulchreRun.TICK_MS);
		assertEquals(3, s.getRows().size());
		assertEquals(Integer.valueOf(-3_000), s.getRows().get(0).getDelta());
		assertEquals(Integer.valueOf(6_000), s.getRows().get(1).getDelta());
		assertTrue(s.getRows().get(2).isLive());
		assertNull(s.getRows().get(2).getDelta());
		assertEquals(Integer.valueOf(3_000), s.total());
		// 210 ticks into floor 3: ten over the ghost, and the total counts it.
		s = FloorSplits.of(t.snapshot(465), ghost, 3, t.onFloor(465) * SepulchreRun.TICK_MS);
		assertEquals(Integer.valueOf(6_000), s.getRows().get(2).getDelta());
		assertEquals(Integer.valueOf(9_000), s.total());
		assertNull(FloorSplits.of(now, null, 3, 0).total());
	}

	// The log and the pace.

	@Test
	public void theLogCountsEveryRunAndSurvivesASaveAndDamage()
	{
		SepulchreLog log = new SepulchreLog();
		for (int i = 0; i < 5; i++)
		{
			SepulchreRun r = run(i * 300_000L, new int[]{100, 150, 200, 250}, new int[][]{{1}, {2, 3}, {3, 4, 5}, {6, 3}}, false);
			log.offerGhost(r);
			log.record(r);
		}
		log.markGot(Unique.PAGE_1);
		log.setBankMarks(420);
		SepulchreLog back = SepulchreLog.read(GSON, GSON.toJson(log));
		assertEquals(5, back.getTotalRuns());
		assertEquals(5 * 27, back.getTotalMarks());
		assertEquals(5 * 3, back.getCoffins()[2]);
		assertTrue(back.has(Unique.PAGE_1));
		assertFalse(back.has(Unique.RING_OF_ENDURANCE));
		assertEquals(420, back.getBankMarks());
		assertNotNull(back.ghost(4));

		// A field of the wrong type costs only that field.
		String damaged = GSON.toJson(log).replace("\"bankMarks\":420", "\"bankMarks\":\"lots\"");
		SepulchreLog mended = SepulchreLog.read(GSON, damaged);
		assertEquals(5, mended.getTotalRuns());
		assertEquals(-1, mended.getBankMarks());
		assertEquals(0, SepulchreLog.read(GSON, "not json").getTotalRuns());
		assertEquals(0, SepulchreLog.read(GSON, null).getRuns().size());
		SepulchreLog nulls = SepulchreLog.read(GSON, "{\"runs\":[null,{\"deepest\":9,\"coffins\":[-4]}],\"coffins\":null,\"ghosts\":{\"3\":null}}");
		assertEquals(1, nulls.getRuns().size());
		assertEquals(5, nulls.getRuns().get(0).getDeepest());
		assertEquals(0, nulls.getRuns().get(0).coffinsOn(1));
		assertEquals(0, nulls.ghostCount());
	}

	@Test
	public void thePaceCountsTheWalkBackBetweenRuns()
	{
		List<SepulchreRun> runs = new ArrayList<>();
		// Runs of 650 ticks (6.5 minutes), one every 7.5 minutes: a minute's walk back.
		for (int i = 0; i < 4; i++)
		{
			runs.add(run(i * 450_000L, new int[]{100, 150, 200, 200}, new int[][]{{1}, {2, 3}, {3, 4, 5}, {6, 3}}, false));
		}
		SepulchrePace pace = SepulchrePace.of(runs);
		assertEquals(27, pace.getMarksPerRun(), 1e-9);
		assertTrue(pace.isGapKnown());
		assertEquals(450_000, pace.cycleMs(), 3_000);
		assertEquals(27 * 8, pace.marksPerHour(), 1.5);
		assertEquals(3, pace.coffinsOn(3), 1e-9);
		assertEquals(67, pace.runsFor(1_800));
		assertEquals(0, pace.runsFor(-5));
		assertNull(SepulchrePace.of(new ArrayList<>()));
		// One run alone has no walk back to measure.
		assertFalse(SepulchrePace.of(runs.subList(0, 1)).isGapKnown());
	}

	// The odds.

	@Test
	public void theOddsOfEachUniqueFollowTheCoffinsLooted()
	{
		double[] all = {1, 2, 3, 2, 3};
		// The ring: 1/200 of the grand coffins opened.
		assertEquals(1 / 200.0, Unique.RING_OF_ENDURANCE.perRun(all, 1), 1e-12);
		assertEquals(0.5 / 200.0, Unique.RING_OF_ENDURANCE.perRun(all, 0.5), 1e-12);
		// The lockpick: 1/200, 2 at 1/120, 3 at 1/90, 2 at 1/60, 3 at 1/40.
		double none = (1 - 1 / 200.0) * Math.pow(1 - 1 / 120.0, 2) * Math.pow(1 - 1 / 90.0, 3) * Math.pow(1 - 1 / 60.0, 2) * Math.pow(1 - 1 / 40.0, 3);
		assertEquals(1 - none, Unique.STRANGE_OLD_LOCKPICK.perRun(all, 0), 1e-12);
		// Floor 3's page from its three coffins at 1/10.
		assertEquals(1 - Math.pow(0.9, 3), Unique.PAGE_3.perRun(all, 0), 1e-12);
		assertEquals(0, Unique.PAGE_5.perRun(new double[]{1, 2, 3, 0, 0}, 0), 1e-12);
		assertEquals(139, Unique.runsFor(1 / 200.0, 0.5));
		assertEquals(-1, Unique.runsFor(0, 0.5));
		assertEquals(1 - Math.pow(199 / 200.0, 300), Unique.RING_OF_ENDURANCE.hadByNow(new long[5], 300), 1e-12);
		assertSame(Unique.RING_OF_ENDURANCE, Unique.of(net.runelite.api.gameval.ItemID.RING_OF_ENDURANCE));
		assertSame(Unique.STRANGE_OLD_LOCKPICK, Unique.of(net.runelite.api.gameval.ItemID.STRANGE_OLD_LOCKPICK_FULL));
		assertNull(Unique.of(net.runelite.api.gameval.ItemID.HALLOWED_MARK));
	}

	@Test
	public void theFloorsAndRewardsAreTheWikis()
	{
		assertEquals(0, Floor.open(51));
		assertEquals(1, Floor.open(52));
		assertEquals(4, Floor.open(86));
		assertEquals(5, Floor.open(99));
		assertEquals(11_700, Floor.xpTo(5));
		assertEquals(5_975, Floor.xpTo(4));
		assertEquals(1_800, Reward.DARK_GRACEFUL.getCost());
		assertEquals(400, Reward.HALLOWED_TOOLS.getCost());
		assertEquals(3_000, Reward.DARK_ACORN.getCost());
	}
}
