package com.codex.plunder.sepulchre;

import java.util.Arrays;

/**
 * One trip down the Hallowed Sepulchre: when it began, each floor's time, the coffins looted and
 * marks found on each floor, the Grand Hallowed Coffin, and the Agility experience it paid.
 *
 * <p>A floor's time is the game's own, read from its "Floor N time" message, when one was seen;
 * otherwise the plugin's count of game ticks from the floor before it.
 */
public final class SepulchreRun
{
	static final int FLOORS = 5;
	/** More coffins than any floor holds, kept as damage. */
	private static final int MAX_COFFINS = 20;
	/** A game tick is 0.6 seconds (wiki, Game tick). */
	public static final int TICK_MS = 600;

	private long at;
	private int ticks;
	private int deepest;
	private int[] floorTicks = blank();
	private int[] floorMs = blank();
	private int[] coffins = new int[FLOORS];
	private int[] marks = new int[FLOORS];
	private boolean grand;
	private int grandMarks;
	private int agilityXp;
	/** The game's overall time for the run, when it said one; -1 otherwise. */
	private int overallMs = -1;

	SepulchreRun()
	{
	}

	SepulchreRun(long at)
	{
		this.at = at;
	}

	static int[] blank()
	{
		int[] a = new int[FLOORS];
		Arrays.fill(a, -1);
		return a;
	}

	/** A copy, so a snapshot of the run under way cannot change after it is handed out. */
	SepulchreRun copy()
	{
		SepulchreRun c = new SepulchreRun(at);
		c.ticks = ticks;
		c.deepest = deepest;
		c.floorTicks = floorTicks.clone();
		c.floorMs = floorMs.clone();
		c.coffins = coffins.clone();
		c.marks = marks.clone();
		c.grand = grand;
		c.grandMarks = grandMarks;
		c.agilityXp = agilityXp;
		c.overallMs = overallMs;
		return c;
	}

	/** When the run began, in epoch milliseconds. */
	public long getAt()
	{
		return at;
	}

	/** Game ticks from the start of the run to its end. */
	public int getTicks()
	{
		return ticks;
	}

	/** The deepest floor finished, 0 for none. */
	public int getDeepest()
	{
		return deepest;
	}

	/** Coffins looted on a floor, 1 to 5. */
	public int coffinsOn(int floor)
	{
		return floor < 1 || floor > FLOORS ? 0 : coffins[floor - 1];
	}

	/** Hallowed marks from the ordinary coffins of a floor, 1 to 5. */
	public int marksOn(int floor)
	{
		return floor < 1 || floor > FLOORS ? 0 : marks[floor - 1];
	}

	public int getCoffins()
	{
		int n = 0;
		for (int c : coffins)
		{
			n += c;
		}
		return n;
	}

	/** Every Hallowed mark the run found, the grand coffin's among them. */
	public int getMarks()
	{
		int n = grandMarks;
		for (int m : marks)
		{
			n += m;
		}
		return n;
	}

	public boolean isGrand()
	{
		return grand;
	}

	public int getGrandMarks()
	{
		return grandMarks;
	}

	public int getAgilityXp()
	{
		return agilityXp;
	}

	public int getOverallMs()
	{
		return overallMs;
	}

	/** Milliseconds the floor took, the game's own time when it gave one; -1 when not known. */
	public int floorTime(int floor)
	{
		if (floor < 1 || floor > FLOORS)
		{
			return -1;
		}
		int i = floor - 1;
		if (floorMs[i] >= 0)
		{
			return floorMs[i];
		}
		return floorTicks[i] < 0 ? -1 : floorTicks[i] * TICK_MS;
	}

	/** Whether the game's own time was read for the floor. */
	public boolean isGameTimed(int floor)
	{
		return floor >= 1 && floor <= FLOORS && floorMs[floor - 1] >= 0;
	}

	/** Milliseconds from the start to the end of a floor, or -1 when any floor before it is not known. */
	public int timeTo(int floor)
	{
		if (floor < 1 || floor > deepest)
		{
			return -1;
		}
		int sum = 0;
		for (int f = 1; f <= floor; f++)
		{
			int t = floorTime(f);
			if (t < 0)
			{
				return -1;
			}
			sum += t;
		}
		return sum;
	}

	/** The whole run's floor time: the game's overall time when it said one, else the floors added up. */
	public int totalTime()
	{
		return overallMs >= 0 ? overallMs : timeTo(deepest);
	}

	/** Milliseconds the run took from first sight to last, for rates. */
	public long durationMs()
	{
		return Math.max((long) ticks * TICK_MS, Math.max(0, totalTime()));
	}

	// Changes, made by the tracker while the run is under way.

	void floorTicks(int floor, int t)
	{
		floorTicks[floor - 1] = Math.max(-1, t);
		deepest = Math.max(deepest, floor);
	}

	void floorMs(int floor, int ms)
	{
		floorMs[floor - 1] = ms;
	}

	void coffin(int floor, int gained)
	{
		coffins[floor - 1]++;
		marks[floor - 1] += gained;
	}

	void grand(int gained)
	{
		grand = true;
		grandMarks += gained;
	}

	void grandSeen()
	{
		grand = true;
	}

	void agility(int xp)
	{
		agilityXp += xp;
	}

	void overall(int ms)
	{
		overallMs = ms;
	}

	void ticks(int t)
	{
		ticks = Math.max(0, t);
	}

	/** Puts back what a damaged save left out. */
	SepulchreRun repair()
	{
		floorTicks = fix(floorTicks, -1);
		floorMs = fix(floorMs, -1);
		coffins = fix(coffins, 0);
		marks = fix(marks, 0);
		for (int i = 0; i < FLOORS; i++)
		{
			coffins[i] = Math.max(0, Math.min(MAX_COFFINS, coffins[i]));
			marks[i] = Math.max(0, marks[i]);
		}
		deepest = Math.max(0, Math.min(FLOORS, deepest));
		ticks = Math.max(0, ticks);
		grandMarks = Math.max(0, grandMarks);
		agilityXp = Math.max(0, agilityXp);
		overallMs = Math.max(-1, overallMs);
		return this;
	}

	private static int[] fix(int[] a, int fill)
	{
		int[] out = new int[FLOORS];
		Arrays.fill(out, fill);
		if (a != null)
		{
			System.arraycopy(a, 0, out, 0, Math.min(a.length, FLOORS));
		}
		for (int i = 0; i < FLOORS; i++)
		{
			out[i] = Math.max(fill, out[i]);
		}
		return out;
	}
}
