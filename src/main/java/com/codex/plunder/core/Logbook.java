package com.codex.plunder.core;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything kept per account: the learned pace, the chosen goal, every run, and every chest
 * and sarcophagus ever opened per room, which is what the sceptre odds are counted from.
 */
public final class Logbook
{
	/** Runs kept in full; older runs still count in the totals. */
	static final int KEEP = 300;
	/**
	 * A save at least this long is read off the client thread. 300 runs come to about 80,000
	 * characters and take about 5 ms to read and check; a hand-edited save can be far longer.
	 */
	public static final int LARGE = 40_000;

	private Pace pace = new Pace();
	private Goal goal = Goal.EXPERIENCE;
	private int level;
	private long xp;
	private List<Run> runs = new ArrayList<>();
	private long[] opened = new long[Room.values().length];
	private int sceptres;
	private long totalRuns;
	private long totalXp;
	private long totalArtefacts;
	private long totalTicks;
	private Sceptre sceptre = new Sceptre();
	/** Artefacts in the bank as last seen here, by item id. */
	private Map<Integer, Long> banked = new TreeMap<>();
	private long bankedAt;
	/** The ghost for each kind of run, by {@link Ghost#key}: the best run of that kind, kept however old. */
	private Map<String, Run> ghosts = new TreeMap<>();

	public Pace getPace()
	{
		return pace;
	}

	public Goal getGoal()
	{
		return goal;
	}

	public void setGoal(Goal goal)
	{
		this.goal = goal == null ? Goal.EXPERIENCE : goal;
	}

	/** The Thieving level as last seen, so the panel plans while logged out. */
	public int getLevel()
	{
		return level;
	}

	public long getXp()
	{
		return xp;
	}

	public void seen(int level, long xp)
	{
		this.level = level;
		this.xp = xp;
	}

	/** The sceptre's charges and where it was last seen. */
	public Sceptre getSceptre()
	{
		if (sceptre == null)
		{
			sceptre = new Sceptre();
		}
		return sceptre;
	}

	/** Artefacts in the bank as last seen here. */
	public Map<Integer, Long> getBanked()
	{
		return Collections.unmodifiableMap(banked);
	}

	/** When the bank was last seen here, 0 for never. */
	public long getBankedAt()
	{
		return bankedAt;
	}

	/**
	 * The artefacts the bank holds, seen now. The time of the look matters as much as what it
	 * found: an unchanged bank seen again is still this plugin's newer look, and a save holding
	 * only the older time would let Bank Steward's snapshot of the same visit look newer.
	 *
	 * @return whether the save no longer matches, which is whenever the bank is seen at a new time
	 */
	public boolean setBanked(Map<Integer, Long> artefacts, long at)
	{
		Map<Integer, Long> next = new TreeMap<>(artefacts);
		boolean changed = !next.equals(banked) || at != bankedAt;
		banked = next;
		bankedAt = at;
		return changed;
	}

	public List<Run> getRuns()
	{
		return Collections.unmodifiableList(runs);
	}

	public long[] getOpened()
	{
		return opened.clone();
	}

	public long openedTotal()
	{
		long n = 0;
		for (long o : opened)
		{
			n += o;
		}
		return n;
	}

	public int getSceptres()
	{
		return sceptres;
	}

	public long getTotalRuns()
	{
		return totalRuns;
	}

	public long getTotalXp()
	{
		return totalXp;
	}

	public long getTotalArtefacts()
	{
		return totalArtefacts;
	}

	public long getTotalTicks()
	{
		return totalTicks;
	}

	public void record(Run run, long[] openedThisRun)
	{
		runs.add(run);
		while (runs.size() > KEEP)
		{
			runs.remove(0);
		}
		for (int i = 0; i < opened.length && i < openedThisRun.length; i++)
		{
			opened[i] += openedThisRun[i];
		}
		if (run.isSceptre())
		{
			sceptres++;
		}
		totalRuns++;
		totalXp += run.getThievingXp();
		totalArtefacts += run.getArtefacts();
		totalTicks += run.getTicks();
	}

	/** The ghost kept under a key, or null. */
	public Run ghost(String key)
	{
		return key == null ? null : ghosts.get(key);
	}

	/**
	 * The ghost a run under way is measured against: the one that reached the deepest room the
	 * bracket opens, or else the deepest any ghost of the bracket reached. Null when there is none.
	 */
	public Run liveGhost(Goal goal, Room bracket)
	{
		if (bracket == null)
		{
			return null;
		}
		for (int deepest = bracket.getNumber(); deepest >= 1; deepest--)
		{
			Run g = ghosts.get(Ghost.key(goal, bracket, deepest));
			if (g != null)
			{
				return g;
			}
		}
		return null;
	}

	/**
	 * Offers a finished run as the ghost for its kind.
	 *
	 * @return whether it took the place of the ghost there was (or there was none)
	 */
	public boolean offerGhost(String key, Run run, Goal goal)
	{
		if (key == null || !Ghost.beats(run, ghosts.get(key), goal))
		{
			return false;
		}
		ghosts.put(key, run);
		return true;
	}

	/** How many ghosts are kept. */
	public int ghostCount()
	{
		return ghosts.size();
	}

	/**
	 * The sceptre turned up just after the last run was closed, since finding it throws the player
	 * out of the pyramid at once.
	 */
	public void sceptreAfter()
	{
		if (runs.isEmpty())
		{
			return;
		}
		Run last = runs.get(runs.size() - 1);
		if (!last.isSceptre())
		{
			last.markSceptre();
			sceptres++;
		}
	}

	/**
	 * Free slots a run like this one needs for its artefacts: the most picked up in any of the
	 * last few runs, or a guess from the plan before there are three runs to go on.
	 */
	public int slotsNeeded(Plan plan, int last)
	{
		int n = 0;
		int most = 0;
		for (int i = runs.size() - 1; i >= 0 && n < last; i--, n++)
		{
			most = Math.max(most, runs.get(i).getArtefactCount());
		}
		if (n >= 3)
		{
			return most;
		}
		int guess = 0;
		for (Plan.Stop s : plan.getStops())
		{
			guess += s.getUrns() + (s.isChest() ? 3 : 0) + (s.isSarcophagus() ? 3 : 0);
		}
		// Roughly one urn in three holds an artefact; chests and sarcophagi nearly always do.
		return (int) Math.ceil(guess / 3.0);
	}

	/**
	 * Thieving experience an hour over the last few runs, each counted with the usual walk back in
	 * between runs, or 0 before there are two runs to go on.
	 */
	public int recentRate(int last)
	{
		int n = 0;
		long xp = 0;
		long ticks = 0;
		for (int i = runs.size() - 1; i >= 0 && n < last; i--)
		{
			Run r = runs.get(i);
			xp += r.getThievingXp();
			ticks += r.getTicks() + Planner.RESTART_TICKS;
			n++;
		}
		return n < 2 || ticks <= 0 ? 0 : (int) Math.round(xp * Planner.TICKS_PER_HOUR / ticks);
	}

	/**
	 * Reads the logbook saved for an account, or a fresh one when there is none. A damaged save
	 * keeps all it can: a field or run that cannot be read is lost alone.
	 */
	public static Logbook read(Gson gson, String saved)
	{
		Logbook read = Salvage.read(gson, saved, Logbook.class);
		return read == null ? new Logbook() : read.repair();
	}

	/** Puts back what an older or damaged save left out. */
	public Logbook repair()
	{
		if (pace == null)
		{
			pace = new Pace();
		}
		pace.repair();
		if (goal == null)
		{
			goal = Goal.EXPERIENCE;
		}
		if (runs == null)
		{
			runs = new ArrayList<>();
		}
		runs.removeIf(r -> r == null);
		runs.forEach(Run::repair);
		while (runs.size() > KEEP)
		{
			runs.remove(0);
		}
		// A level or count out of what the game allows is damage, brought back into range.
		level = Math.max(0, Math.min(Levels.MAX, level));
		xp = Math.max(0, Math.min(Levels.MAX_XP, xp));
		sceptres = Math.max(0, sceptres);
		totalRuns = Math.max(Math.max(0, totalRuns), runs.size());
		totalXp = Math.max(0, totalXp);
		totalArtefacts = Math.max(0, totalArtefacts);
		totalTicks = Math.max(0, totalTicks);
		bankedAt = Math.max(0, bankedAt);
		if (sceptre == null)
		{
			sceptre = new Sceptre();
		}
		ghosts = ghosts == null ? new TreeMap<>() : new TreeMap<>(ghosts);
		ghosts.values().removeIf(r -> r == null);
		ghosts.values().forEach(Run::repair);
		ghosts.values().removeIf(r -> !Ghost.usable(r));
		banked = banked == null ? new TreeMap<>() : new TreeMap<>(banked);
		banked.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() <= 0);
		if (opened == null || opened.length != Room.values().length)
		{
			long[] fixed = new long[Room.values().length];
			if (opened != null)
			{
				System.arraycopy(opened, 0, fixed, 0, Math.min(opened.length, fixed.length));
			}
			opened = fixed;
		}
		for (int i = 0; i < opened.length; i++)
		{
			opened[i] = Math.max(0, opened[i]);
		}
		return this;
	}
}
