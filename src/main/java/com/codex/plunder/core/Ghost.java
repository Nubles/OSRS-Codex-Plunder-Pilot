package com.codex.plunder.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ghost splits: a run cut into its natural segments, timed in game ticks, against the player's own
 * best run of the same kind. Every room is split into its crossing (the trap, the walk and the door,
 * everything that is not a search) and its searches: the urns, the grand gold chest and the
 * sarcophagus. Each search is timed from the search before it in the room, so a room's parts add up
 * to the whole room.
 *
 * <p>The ghost is the best run for the goal, the deepest room the player's Thieving level opens (their
 * bracket) and the deepest room the run reached: the most experience an hour on an experience run,
 * the best sceptre odds an hour on a sceptre run. A run that gets to a room it never has before is
 * not measured against one that stopped short of it.
 */
public final class Ghost
{
	/** The parts of a room. */
	public enum Part
	{
		/** The trap, the walk and the door: everything that is not a search. */
		CROSS,
		URNS,
		CHEST,
		SARCOPHAGUS
	}

	private Ghost()
	{
	}

	/** The key a ghost is kept under: the goal, the bracket's deepest room and the deepest room reached. */
	public static String key(Goal goal, Room bracket, int deepest)
	{
		return (goal == null ? Goal.EXPERIENCE : goal).name() + "|" + (bracket == null ? 0 : bracket.getNumber()) + "|" + deepest;
	}

	/** Whether a run can be a ghost: timed room by room from the first room, its searches kept. */
	public static boolean usable(Run run)
	{
		return run != null && run.hasSearches() && run.getDeepest() >= 1 && run.splitAt(Room.ONE) >= 0
			&& run.splitAt(Room.of(run.getDeepest())) >= 0;
	}

	/**
	 * What the run earned an hour toward its goal, counting the usual walk back between runs:
	 * Thieving experience on an experience run, the chance of a sceptre on a sceptre run.
	 */
	public static double rate(Run run, Goal goal)
	{
		if (run == null)
		{
			return 0;
		}
		double hours = (run.getTicks() + Planner.RESTART_TICKS) / Planner.TICKS_PER_HOUR;
		double value = goal == Goal.SCEPTRE ? run.getSceptreChance() : run.getThievingXp();
		return value / hours;
	}

	/** Artefacts' worth an hour, counting the walk back between runs. */
	public static double lootRate(Run run)
	{
		return run == null ? 0 : run.getArtefacts() / ((run.getTicks() + Planner.RESTART_TICKS) / Planner.TICKS_PER_HOUR);
	}

	/** Whether the run beats the ghost and should take its place. */
	public static boolean beats(Run run, Run ghost, Goal goal)
	{
		if (!usable(run))
		{
			return false;
		}
		if (ghost == null)
		{
			return true;
		}
		double a = rate(run, goal);
		double b = rate(ghost, goal);
		// Level on the goal, the richer run is the better ghost.
		return a > b || a == b && lootRate(run) > lootRate(ghost);
	}

	/**
	 * Ticks the run spent in the room: from entering it to entering the next, or to the end of the
	 * run in its deepest room. -1 when the run did not reach it or its arrival was not seen.
	 */
	public static int roomTicks(Run run, Room room)
	{
		if (run == null || room == null || room.getNumber() > run.getDeepest())
		{
			return -1;
		}
		int from = run.splitAt(room);
		if (from < 0)
		{
			return -1;
		}
		int to = room.getNumber() == run.getDeepest() ? run.getTicks() : run.splitAt(Room.values()[room.ordinal() + 1]);
		return to < from ? -1 : to - from;
	}

	/** Ticks one part of the room took, or -1 when not known. */
	public static int part(Run run, Room room, Part part)
	{
		int total = roomTicks(run, room);
		if (total < 0 || !run.hasSearches())
		{
			return -1;
		}
		int urns = run.urnTicksAt(room);
		int chest = run.chestTicksAt(room);
		int sarc = run.sarcophagusTicksAt(room);
		switch (part)
		{
			case URNS:
				return urns;
			case CHEST:
				return chest;
			case SARCOPHAGUS:
				return sarc;
			default:
				return Math.max(0, total - urns - chest - sarc);
		}
	}

	/** One room of the split table: this run against the ghost. */
	public static final class Row
	{
		private final Room room;
		private final int mine;
		private final int ghost;
		private final boolean live;

		Row(Room room, int mine, int ghost, boolean live)
		{
			this.room = room;
			this.mine = mine;
			this.ghost = ghost;
			this.live = live;
		}

		public Room getRoom()
		{
			return room;
		}

		/** Ticks this run has spent in the room, so far for the room under way; -1 when not reached. */
		public int getMine()
		{
			return mine;
		}

		/** Ticks the ghost spent in the room; -1 when it never reached it. */
		public int getGhost()
		{
			return ghost;
		}

		/** The room the run is in now, still being timed. */
		public boolean isLive()
		{
			return live;
		}

		/**
		 * Ticks over (positive) or under the ghost, or null with nothing to say yet: either side
		 * unknown, or a room under way that is still inside the ghost's time.
		 */
		public Integer getDelta()
		{
			if (mine < 0 || ghost < 0)
			{
				return null;
			}
			int d = mine - ghost;
			return live && d <= 0 ? null : d;
		}
	}

	/**
	 * The split table: every room either run reached, this run against the ghost.
	 *
	 * @param run the run, or a snapshot of the one under way
	 * @param live whether the run is still under way, so its deepest room is still being timed
	 */
	public static List<Row> rows(Run run, Run ghost, boolean live)
	{
		if (run == null)
		{
			return Collections.emptyList();
		}
		int deepest = Math.max(run.getDeepest(), ghost == null ? 0 : ghost.getDeepest());
		List<Row> rows = new ArrayList<>();
		for (int n = 1; n <= deepest; n++)
		{
			Room r = Room.of(n);
			rows.add(new Row(r, roomTicks(run, r), roomTicks(ghost, r), live && n == run.getDeepest()));
		}
		return rows;
	}

	/**
	 * Ticks ahead (negative) or behind the ghost on entering the room, or null when either entry
	 * was not seen.
	 */
	public static Integer entryDelta(Run run, Run ghost, Room room)
	{
		if (run == null || ghost == null || room == null)
		{
			return null;
		}
		int a = run.splitAt(room);
		int b = ghost.splitAt(room);
		return a < 0 || b < 0 || room.getNumber() > run.getDeepest() || room.getNumber() > ghost.getDeepest() ? null : a - b;
	}
}
