package com.codex.plunder.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A finished run's grade against its ghost: S, A, B or C from what it earned an hour toward its
 * goal against the ghost's best, and the one to three parts of rooms where it lost the most time,
 * said in plain words ("Room 5: 41 ticks searching 9 urns, ghost took 18").
 */
public final class Grade
{
	public enum Letter
	{
		S, A, B, C
	}

	/** Shares of the ghost's rate each letter needs: S within 2% of it (or past it), A 90%, B 75%. */
	static final double S_SHARE = 0.98;
	static final double A_SHARE = 0.90;
	static final double B_SHARE = 0.75;
	/** Time sinks named at most. */
	public static final int SINKS = 3;
	/** Ticks over the ghost a part must lose before it is named: three seconds, as the splits do. */
	public static final int SINK_MIN = Splits.SLOW;

	/** One part of one room where the run lost time to the ghost. */
	public static final class Sink
	{
		private final Room room;
		private final Ghost.Part part;
		private final int mine;
		private final int ghost;
		private final int mineUrns;
		private final int ghostUrns;
		private final int lost;
		private final boolean deepest;

		Sink(Room room, Ghost.Part part, int mine, int ghost, int mineUrns, int ghostUrns, int lost, boolean deepest)
		{
			this.room = room;
			this.part = part;
			this.mine = mine;
			this.ghost = ghost;
			this.mineUrns = mineUrns;
			this.ghostUrns = ghostUrns;
			this.lost = lost;
			this.deepest = deepest;
		}

		public Room getRoom()
		{
			return room;
		}

		public Ghost.Part getPart()
		{
			return part;
		}

		public int getMine()
		{
			return mine;
		}

		public int getGhost()
		{
			return ghost;
		}

		/** Ticks lost to the ghost; for urns, against the ghost's pace for as many urns. */
		public int getLost()
		{
			return lost;
		}

		/** The sink in plain words: "Room 5: 41 ticks searching 9 urns, ghost took 18". */
		public String sentence()
		{
			String what;
			String ghostTook = "ghost took " + ghost;
			switch (part)
			{
				case URNS:
					what = "searching " + mineUrns + (mineUrns == 1 ? " urn" : " urns");
					if (ghostUrns != mineUrns)
					{
						ghostTook += " for " + ghostUrns;
					}
					break;
				case CHEST:
					what = "opening the chest";
					break;
				case SARCOPHAGUS:
					what = "opening the sarcophagus";
					break;
				default:
					what = deepest ? "not searching" : "on the trap, walk and door";
					break;
			}
			return "Room " + room.getNumber() + ": " + mine + " ticks " + what + ", " + ghostTook;
		}
	}

	private final Letter letter;
	private final Goal goal;
	private final double rate;
	private final double ghostRate;
	private final double lootRate;
	private final double ghostLootRate;
	private final List<Sink> sinks;
	private final boolean newGhost;
	private final int deepest;
	private final Integer finish;

	private Grade(Letter letter, Goal goal, double rate, double ghostRate, double lootRate, double ghostLootRate, List<Sink> sinks,
		boolean newGhost, int deepest, Integer finish)
	{
		this.letter = letter;
		this.goal = goal;
		this.rate = rate;
		this.ghostRate = ghostRate;
		this.lootRate = lootRate;
		this.ghostLootRate = ghostLootRate;
		this.sinks = sinks;
		this.newGhost = newGhost;
		this.deepest = deepest;
		this.finish = finish;
	}

	/**
	 * @param run the finished run
	 * @param ghost the ghost it is measured against, as it was before this run; null when there was none
	 * @param goal the goal the run began with
	 */
	public static Grade of(Run run, Run ghost, Goal goal)
	{
		if (run == null)
		{
			return null;
		}
		double rate = Ghost.rate(run, goal);
		double loot = Ghost.lootRate(run);
		boolean replaces = Ghost.beats(run, ghost, goal);
		if (ghost == null)
		{
			return new Grade(null, goal, rate, 0, loot, 0, Collections.emptyList(), replaces, run.getDeepest(), null);
		}
		double ghostRate = Ghost.rate(ghost, goal);
		double share = ghostRate <= 0 ? (rate > 0 ? 1 : 0) : rate / ghostRate;
		Letter letter = share >= S_SHARE ? Letter.S : share >= A_SHARE ? Letter.A : share >= B_SHARE ? Letter.B : Letter.C;
		Room last = Room.of(run.getDeepest());
		return new Grade(letter, goal, rate, ghostRate, loot, Ghost.lootRate(ghost), sinks(run, ghost), replaces, run.getDeepest(),
			Ghost.entryDelta(run, ghost, last));
	}

	/** The parts of rooms where the run lost the most time to the ghost, worst first. */
	static List<Sink> sinks(Run run, Run ghost)
	{
		List<Sink> all = new ArrayList<>();
		if (!run.hasSearches() || ghost == null || !ghost.hasSearches())
		{
			return all;
		}
		int both = Math.min(run.getDeepest(), ghost.getDeepest());
		for (int n = 1; n <= both; n++)
		{
			Room r = Room.of(n);
			if (Ghost.roomTicks(run, r) < 0 || Ghost.roomTicks(ghost, r) < 0)
			{
				continue;
			}
			boolean deepest = n == run.getDeepest() || n == ghost.getDeepest();
			for (Ghost.Part part : Ghost.Part.values())
			{
				int mine = Ghost.part(run, r, part);
				int theirs = Ghost.part(ghost, r, part);
				int lost;
				if (part == Ghost.Part.URNS)
				{
					int a = run.urnsAt(r);
					int b = ghost.urnsAt(r);
					if (a <= 0 || b <= 0)
					{
						continue;
					}
					// Searching more urns than the ghost is not slow: only the pace for as many counts.
					lost = (int) Math.round(mine - theirs * (a / (double) b));
				}
				else if (part == Ghost.Part.CROSS)
				{
					lost = mine - theirs;
				}
				else
				{
					// A chest or sarcophagus the ghost left shut is not time lost to it.
					if (mine <= 0 || theirs <= 0)
					{
						continue;
					}
					lost = mine - theirs;
				}
				if (lost >= SINK_MIN)
				{
					all.add(new Sink(r, part, mine, theirs, run.urnsAt(r), ghost.urnsAt(r), lost, deepest));
				}
			}
		}
		// Worst first; level, the earlier room first, as it was lost first.
		all.sort((x, y) -> x.lost != y.lost ? Integer.compare(y.lost, x.lost) : Integer.compare(x.room.ordinal(), y.room.ordinal()));
		return all.size() > SINKS ? new ArrayList<>(all.subList(0, SINKS)) : all;
	}

	/** The letter, or null for the first run of its kind, which only sets the ghost. */
	public Letter getLetter()
	{
		return letter;
	}

	public Goal getGoal()
	{
		return goal;
	}

	/** Thieving experience an hour on an experience run, the sceptre chance an hour on a sceptre run. */
	public double getRate()
	{
		return rate;
	}

	public double getGhostRate()
	{
		return ghostRate;
	}

	/** Artefacts' worth an hour. */
	public double getLootRate()
	{
		return lootRate;
	}

	public double getGhostLootRate()
	{
		return ghostLootRate;
	}

	/** The parts of rooms that lost the most time, worst first; none when there was no ghost. */
	public List<Sink> getSinks()
	{
		return Collections.unmodifiableList(sinks);
	}

	/** Whether the run is the new ghost. */
	public boolean isNewGhost()
	{
		return newGhost;
	}

	public int getDeepest()
	{
		return deepest;
	}

	/** Ticks ahead (negative) or behind the ghost on entering the deepest room, or null. */
	public Integer getFinish()
	{
		return finish;
	}

	/** The run's rate as a share of the ghost's, or 0 with no ghost. */
	public double share()
	{
		return ghostRate <= 0 ? 0 : rate / ghostRate;
	}
}
