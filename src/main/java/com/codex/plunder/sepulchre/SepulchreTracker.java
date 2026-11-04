package com.codex.plunder.sepulchre;

/**
 * The Sepulchre run under way: when each floor ends, the coffins looted on it and the marks they
 * gave. A floor is timed from the end of the one before it (or the start of the run) to the game's
 * "completed Floor N" message, and the game's own floor time replaces that count when it says one.
 *
 * <p>A coffin is counted when Hallowed marks arrive in the pack: an ordinary coffin always gives
 * 1 to 6 marks and the Grand Hallowed Coffin 8 to 10 (wiki, Coffin (Hallowed Sepulchre) and Grand
 * Hallowed Coffin), so the size of the gain tells which it was.
 */
public final class SepulchreTracker
{
	private SepulchreRun run;
	private int startTick;
	private int floorStart;

	public boolean isActive()
	{
		return run != null;
	}

	public void begin(long wallMs, int tick)
	{
		run = new SepulchreRun(wallMs);
		startTick = tick;
		floorStart = tick;
	}

	/** The floor being run now, 1 to 5: the one after the deepest finished. */
	public int getFloor()
	{
		return run == null ? 0 : Math.min(SepulchreRun.FLOORS, run.getDeepest() + 1);
	}

	/** Whether the deepest floor finished is the last there is. */
	public boolean isAtBottom()
	{
		return run != null && run.getDeepest() >= SepulchreRun.FLOORS;
	}

	/** Ticks spent on the floor under way. */
	public int onFloor(int tick)
	{
		return run == null ? 0 : Math.max(0, tick - floorStart);
	}

	/**
	 * The game said a floor is finished.
	 *
	 * @return false when the message belongs to a new run (floor 1 again, or a floor already done),
	 *     which the caller ends this one for and starts afresh
	 */
	public boolean floorDone(int floor, int tick)
	{
		if (run == null || Floor.of(floor) == null)
		{
			return false;
		}
		if (floor <= run.getDeepest())
		{
			// A floor this run already finished: a new run has begun.
			return false;
		}
		// A floor finished in a run first seen part way through has no start to time from.
		// A floor finished the tick its run was first seen has no start either.
		int ticks = floor == run.getDeepest() + 1 ? tick - floorStart : -1;
		run.floorTicks(floor, ticks > 0 ? ticks : -1);
		floorStart = tick;
		return true;
	}

	/** The game gave a floor's time, and perhaps the run's overall time. */
	public void floorTime(int floor, int ms, int overallMs)
	{
		if (run == null || Floor.of(floor) == null)
		{
			return;
		}
		run.floorMs(floor, ms);
		if (overallMs >= 0)
		{
			run.overall(overallMs);
		}
	}

	/** Hallowed marks arrived in the pack. */
	public void marks(int gained)
	{
		if (run == null || gained <= 0)
		{
			return;
		}
		if (gained >= Floor.GRAND_MARKS_MIN)
		{
			run.grand(gained);
		}
		else
		{
			run.coffin(getFloor(), gained);
		}
	}

	/** The game said the Grand Hallowed Coffin was opened. */
	public void grandOpened()
	{
		if (run != null)
		{
			run.grandSeen();
		}
	}

	public void agility(int xp)
	{
		if (run != null && xp > 0)
		{
			run.agility(xp);
		}
	}

	/** The run so far, a copy. */
	public SepulchreRun snapshot(int tick)
	{
		if (run == null)
		{
			return null;
		}
		SepulchreRun s = run.copy();
		s.ticks(tick - startTick);
		return s;
	}

	/** Ends the run: what it came to, or null when it finished no floor and looted nothing. */
	public SepulchreRun finish(int tick)
	{
		SepulchreRun done = snapshot(tick);
		run = null;
		if (done == null || done.getDeepest() == 0 && done.getMarks() == 0 && !done.isGrand())
		{
			return null;
		}
		return done;
	}
}
