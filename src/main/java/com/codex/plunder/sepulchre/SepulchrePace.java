package com.codex.plunder.sepulchre;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The player's own pace in the Sepulchre, learned from their last runs: marks a run, how long a run
 * takes with the walk back to the next, the coffins they loot on each floor and how often they open
 * the Grand Hallowed Coffin. Every forecast is made from this, not from a guide's figures.
 */
public final class SepulchrePace
{
	/** Runs the pace is learned from. */
	public static final int LAST = 20;
	/** A gap between runs longer than this is a break, not the walk back. */
	static final long BREAK_MS = 10 * 60_000L;
	static final long HOUR_MS = 3_600_000L;

	private final int runs;
	private final double marksPerRun;
	private final long runMs;
	private final long gapMs;
	private final boolean gapKnown;
	private final double[] coffins;
	private final double grandShare;
	private final double xpPerRun;

	private SepulchrePace(int runs, double marksPerRun, long runMs, long gapMs, boolean gapKnown, double[] coffins, double grandShare, double xpPerRun)
	{
		this.runs = runs;
		this.marksPerRun = marksPerRun;
		this.runMs = runMs;
		this.gapMs = gapMs;
		this.gapKnown = gapKnown;
		this.coffins = coffins;
		this.grandShare = grandShare;
		this.xpPerRun = xpPerRun;
	}

	/** The pace over the last runs, oldest first; null with no run to go on. */
	public static SepulchrePace of(List<SepulchreRun> all)
	{
		if (all == null || all.isEmpty())
		{
			return null;
		}
		List<SepulchreRun> last = all.subList(Math.max(0, all.size() - LAST), all.size());
		int n = last.size();
		double marks = 0;
		double ms = 0;
		double grand = 0;
		double xp = 0;
		double[] coffins = new double[SepulchreRun.FLOORS];
		for (SepulchreRun r : last)
		{
			marks += r.getMarks();
			ms += r.durationMs();
			grand += r.isGrand() ? 1 : 0;
			xp += r.getAgilityXp();
			for (int f = 1; f <= SepulchreRun.FLOORS; f++)
			{
				coffins[f - 1] += r.coffinsOn(f);
			}
		}
		for (int i = 0; i < coffins.length; i++)
		{
			coffins[i] /= n;
		}
		// The walk back: the usual gap from the end of one run to the start of the next.
		List<Long> gaps = new ArrayList<>();
		for (int i = 1; i < last.size(); i++)
		{
			SepulchreRun a = last.get(i - 1);
			long gap = last.get(i).getAt() - (a.getAt() + a.durationMs());
			if (gap >= 0 && gap <= BREAK_MS)
			{
				gaps.add(gap);
			}
		}
		long gap = 0;
		if (!gaps.isEmpty())
		{
			Collections.sort(gaps);
			gap = gaps.get(gaps.size() / 2);
		}
		return new SepulchrePace(n, marks / n, Math.round(ms / n), gap, !gaps.isEmpty(), coffins, grand / n, xp / n);
	}

	public int getRuns()
	{
		return runs;
	}

	public double getMarksPerRun()
	{
		return marksPerRun;
	}

	/** The usual run, from first sight of the floor to the end. */
	public long getRunMs()
	{
		return runMs;
	}

	/** The usual walk back between runs; 0 until two runs have been seen close together. */
	public long getGapMs()
	{
		return gapMs;
	}

	/** Whether the walk back has been measured, so the hourly figures count it. */
	public boolean isGapKnown()
	{
		return gapKnown;
	}

	/** A run and the walk back to the next. */
	public long cycleMs()
	{
		return runMs + gapMs;
	}

	public double runsPerHour()
	{
		return cycleMs() <= 0 ? 0 : HOUR_MS / (double) cycleMs();
	}

	public double marksPerHour()
	{
		return marksPerRun * runsPerHour();
	}

	public double xpPerHour()
	{
		return xpPerRun * runsPerHour();
	}

	public double getXpPerRun()
	{
		return xpPerRun;
	}

	/** Coffins looted a run on a floor, 1 to 5. */
	public double coffinsOn(int floor)
	{
		return floor < 1 || floor > coffins.length ? 0 : coffins[floor - 1];
	}

	/** The share of runs that open the Grand Hallowed Coffin. */
	public double getGrandShare()
	{
		return grandShare;
	}

	/** Runs to gather so many marks at this pace; -1 when the runs find none. */
	public long runsFor(long marks)
	{
		if (marks <= 0)
		{
			return 0;
		}
		return marksPerRun <= 0 ? -1 : (long) Math.ceil(marks / marksPerRun);
	}

	/** Play time to gather so many marks, in milliseconds; -1 when the runs find none. */
	public long msFor(long marks)
	{
		long runsNeeded = runsFor(marks);
		return runsNeeded < 0 ? -1 : runsNeeded * cycleMs();
	}
}
