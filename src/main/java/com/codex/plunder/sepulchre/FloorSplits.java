package com.codex.plunder.sepulchre;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Sepulchre run floor by floor against its ghost, in milliseconds: each floor's time, the
 * ghost's, and the gap. During a run the floor under way is timed as it goes, and says nothing
 * until it has run past the ghost's time.
 */
public final class FloorSplits
{
	/** One floor of the table. */
	public static final class Row
	{
		private final int floor;
		private final int mine;
		private final int ghost;
		private final boolean live;

		Row(int floor, int mine, int ghost, boolean live)
		{
			this.floor = floor;
			this.mine = mine;
			this.ghost = ghost;
			this.live = live;
		}

		public int getFloor()
		{
			return floor;
		}

		/** Milliseconds this run took on the floor, so far for the floor under way; -1 when not run. */
		public int getMine()
		{
			return mine;
		}

		/** The ghost's time on the floor, -1 when it never ran it. */
		public int getGhost()
		{
			return ghost;
		}

		public boolean isLive()
		{
			return live;
		}

		/** Milliseconds over (positive) or under the ghost, or null with nothing to say yet. */
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

	private final SepulchreRun run;
	private final SepulchreRun ghost;
	private final List<Row> rows;
	private final boolean live;
	private final int liveFloor;

	private FloorSplits(SepulchreRun run, SepulchreRun ghost, List<Row> rows, boolean live, int liveFloor)
	{
		this.run = run;
		this.ghost = ghost;
		this.rows = rows;
		this.live = live;
		this.liveFloor = liveFloor;
	}

	/**
	 * The table for a run.
	 *
	 * @param liveFloor the floor under way, or 0 for a finished run
	 * @param liveMs time spent so far on the floor under way
	 */
	public static FloorSplits of(SepulchreRun run, SepulchreRun ghost, int liveFloor, int liveMs)
	{
		if (run == null)
		{
			return new FloorSplits(null, ghost, Collections.emptyList(), false, 0);
		}
		boolean live = liveFloor > 0;
		int last = Math.max(Math.max(run.getDeepest(), live ? liveFloor : 0), ghost == null ? 0 : ghost.getDeepest());
		List<Row> rows = new ArrayList<>();
		for (int f = 1; f <= last; f++)
		{
			boolean now = live && f == liveFloor && f > run.getDeepest();
			int mine = now ? liveMs : f <= run.getDeepest() ? run.floorTime(f) : -1;
			int theirs = ghost == null || f > ghost.getDeepest() ? -1 : ghost.floorTime(f);
			rows.add(new Row(f, mine, theirs, now));
		}
		return new FloorSplits(run, ghost, rows, live, live ? liveFloor : 0);
	}

	public SepulchreRun getRun()
	{
		return run;
	}

	public SepulchreRun getGhost()
	{
		return ghost;
	}

	public List<Row> getRows()
	{
		return rows;
	}

	public boolean isLive()
	{
		return live;
	}

	public int getLiveFloor()
	{
		return liveFloor;
	}

	/**
	 * Milliseconds ahead (negative) or behind the ghost: over every floor both have finished, and
	 * during a run any time the floor under way has run past the ghost's. Null with no ghost or
	 * nothing to compare yet.
	 */
	public Integer total()
	{
		if (ghost == null || run == null)
		{
			return null;
		}
		Integer sum = null;
		for (Row row : rows)
		{
			Integer d = row.getDelta();
			if (d == null)
			{
				continue;
			}
			sum = (sum == null ? 0 : sum) + d;
		}
		return sum;
	}
}
