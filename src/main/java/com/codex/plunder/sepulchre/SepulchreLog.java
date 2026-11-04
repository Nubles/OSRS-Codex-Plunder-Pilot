package com.codex.plunder.sepulchre;

import com.codex.plunder.core.Salvage;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Everything kept per account about the Hallowed Sepulchre: every run, the coffins ever looted on
 * each floor (what the unique odds are counted from), the ghost for each depth, the uniques seen,
 * and the marks in the bank as last seen. Saved beside the Plunder logbook under its own key.
 */
public final class SepulchreLog
{
	/** Runs kept in full; older runs still count in the totals. */
	static final int KEEP = 300;

	private List<SepulchreRun> runs = new ArrayList<>();
	private long totalRuns;
	private long totalMarks;
	private long totalXp;
	private long grands;
	private long[] coffins = new long[SepulchreRun.FLOORS];
	/** The fastest run to each depth, by deepest floor ("1" to "5"), kept however old. */
	private Map<String, SepulchreRun> ghosts = new TreeMap<>();
	/** The uniques seen in the pack, worn or in the bank, by name. */
	private TreeSet<String> got = new TreeSet<>();
	/** Hallowed marks in the bank as last seen; -1 before the bank has been seen. */
	private long bankMarks = -1;
	private int agility;
	private int thieving;

	public List<SepulchreRun> getRuns()
	{
		return Collections.unmodifiableList(runs);
	}

	public long getTotalRuns()
	{
		return totalRuns;
	}

	public long getTotalMarks()
	{
		return totalMarks;
	}

	public long getTotalXp()
	{
		return totalXp;
	}

	public long getGrands()
	{
		return grands;
	}

	/** Coffins ever looted on each floor, 1 to 5 at 0 to 4. */
	public long[] getCoffins()
	{
		return coffins.clone();
	}

	public long getBankMarks()
	{
		return bankMarks;
	}

	/** @return whether it changed */
	public boolean setBankMarks(long marks)
	{
		long m = Math.max(0, marks);
		boolean changed = m != bankMarks;
		bankMarks = m;
		return changed;
	}

	public int getAgility()
	{
		return agility;
	}

	public int getThieving()
	{
		return thieving;
	}

	/** @return whether either level changed */
	public boolean seen(int agility, int thieving)
	{
		boolean changed = agility != this.agility || thieving != this.thieving;
		this.agility = agility;
		this.thieving = thieving;
		return changed;
	}

	public boolean has(Unique u)
	{
		return u != null && got.contains(u.name());
	}

	/** @return whether it was new */
	public boolean markGot(Unique u)
	{
		return u != null && got.add(u.name());
	}

	public SepulchrePace pace()
	{
		return SepulchrePace.of(runs);
	}

	/** Adds a finished run. */
	public void record(SepulchreRun run)
	{
		runs.add(run);
		while (runs.size() > KEEP)
		{
			runs.remove(0);
		}
		totalRuns++;
		totalMarks += run.getMarks();
		totalXp += run.getAgilityXp();
		grands += run.isGrand() ? 1 : 0;
		for (int f = 1; f <= SepulchreRun.FLOORS; f++)
		{
			coffins[f - 1] += run.coffinsOn(f);
		}
	}

	/** Whether a run can be a ghost: every floor down to its deepest timed. */
	public static boolean usable(SepulchreRun run)
	{
		return run != null && run.getDeepest() >= 1 && run.timeTo(run.getDeepest()) > 0;
	}

	/** The ghost for runs that finish this floor, or null. */
	public SepulchreRun ghost(int deepest)
	{
		return ghosts.get(String.valueOf(deepest));
	}

	/**
	 * The ghost a run under way races: the one to the deepest floor the Agility level opens, or
	 * else the deepest there is below it.
	 */
	public SepulchreRun liveGhost(int floorsOpen)
	{
		for (int f = Math.min(SepulchreRun.FLOORS, Math.max(floorsOpen, 1)); f >= 1; f--)
		{
			SepulchreRun g = ghost(f);
			if (g != null)
			{
				return g;
			}
		}
		// Above any level seen: the deepest ghost there is.
		for (int f = SepulchreRun.FLOORS; f >= 1; f--)
		{
			if (ghost(f) != null)
			{
				return ghost(f);
			}
		}
		return null;
	}

	/**
	 * Offers a finished run as the ghost for its depth: the fastest to that floor wins.
	 *
	 * @return whether it took the place of the ghost there was, or there was none
	 */
	public boolean offerGhost(SepulchreRun run)
	{
		if (!usable(run))
		{
			return false;
		}
		SepulchreRun old = ghost(run.getDeepest());
		if (old != null && old.timeTo(old.getDeepest()) <= run.timeTo(run.getDeepest()))
		{
			return false;
		}
		ghosts.put(String.valueOf(run.getDeepest()), run);
		return true;
	}

	public int ghostCount()
	{
		return ghosts.size();
	}

	/** Reads the log saved for an account, or a fresh one; a damaged save keeps all it can. */
	public static SepulchreLog read(Gson gson, String saved)
	{
		SepulchreLog read = Salvage.read(gson, saved, SepulchreLog.class);
		return read == null ? new SepulchreLog() : read.repair();
	}

	/** Puts back what an older or damaged save left out. */
	public SepulchreLog repair()
	{
		runs = runs == null ? new ArrayList<>() : new ArrayList<>(runs);
		runs.removeIf(r -> r == null);
		runs.forEach(SepulchreRun::repair);
		while (runs.size() > KEEP)
		{
			runs.remove(0);
		}
		totalRuns = Math.max(Math.max(0, totalRuns), runs.size());
		totalMarks = Math.max(0, totalMarks);
		totalXp = Math.max(0, totalXp);
		grands = Math.max(0, grands);
		long[] fixed = new long[SepulchreRun.FLOORS];
		if (coffins != null)
		{
			System.arraycopy(coffins, 0, fixed, 0, Math.min(coffins.length, fixed.length));
		}
		for (int i = 0; i < fixed.length; i++)
		{
			fixed[i] = Math.max(0, fixed[i]);
		}
		coffins = fixed;
		ghosts = ghosts == null ? new TreeMap<>() : new TreeMap<>(ghosts);
		ghosts.values().removeIf(r -> r == null);
		ghosts.values().forEach(SepulchreRun::repair);
		ghosts.entrySet().removeIf(e -> !usable(e.getValue()) || !String.valueOf(e.getValue().getDeepest()).equals(e.getKey()));
		got = got == null ? new TreeSet<>() : new TreeSet<>(got);
		got.removeIf(n -> n == null);
		bankMarks = Math.max(-1, bankMarks);
		agility = Math.max(0, Math.min(99, agility));
		thieving = Math.max(0, Math.min(99, thieving));
		return this;
	}
}
