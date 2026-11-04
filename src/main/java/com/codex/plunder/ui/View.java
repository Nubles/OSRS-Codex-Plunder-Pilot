package com.codex.plunder.ui;

import com.codex.plunder.core.Check;
import com.codex.plunder.core.Ghost;
import com.codex.plunder.core.Goal;
import com.codex.plunder.core.Grade;
import com.codex.plunder.core.Leaks;
import com.codex.plunder.core.NavigatorGoals;
import com.codex.plunder.core.Plan;
import com.codex.plunder.core.Room;
import com.codex.plunder.core.Run;
import com.codex.plunder.core.Session;
import com.codex.plunder.core.Splits;
import com.codex.plunder.core.Step;
import java.util.Collections;
import java.util.List;

/** Everything the panel shows, worked out on the client thread and handed over whole. */
public final class View
{
	/** What the panel has to show. */
	public enum State
	{
		LOGGED_OUT,
		TOO_LOW,
		READY
	}

	/** Totals for the account, kept apart from the run in hand. */
	public static final class Record
	{
		final List<Run> runs;
		final long[] opened;
		final int sceptres;
		final long totalRuns;
		final long totalXp;
		final long totalArtefacts;
		final int recentRate;
		final int paceSamples;
		/** The best splits as they stood before the newest run that reached the deepest room, or null. */
		Splits splits;
		/** The newest run that reached the deepest room for this goal, or null. */
		Run splitRun;
		Session session;
		/** Where the clock goes on the way to the deepest room, or null. */
		Leaks leaks;
		/** Every session in the runs kept, newest first. */
		List<Session> history = Collections.emptyList();

		public Record(List<Run> runs, long[] opened, int sceptres, long totalRuns, long totalXp, long totalArtefacts, int recentRate, int paceSamples)
		{
			this.runs = runs == null ? Collections.emptyList() : runs;
			this.opened = opened == null ? new long[Room.values().length] : opened;
			this.sceptres = sceptres;
			this.totalRuns = totalRuns;
			this.totalXp = totalXp;
			this.totalArtefacts = totalArtefacts;
			this.recentRate = recentRate;
			this.paceSamples = paceSamples;
		}

		/**
		 * @param prior the splits from the runs before {@code last}
		 * @param last the newest run that reached the deepest room, or null
		 */
		public Record leaks(Leaks leaks)
		{
			this.leaks = leaks;
			return this;
		}

		public Record splits(Splits prior, Run last)
		{
			this.splits = prior;
			this.splitRun = last;
			return this;
		}

		public Record session(Session session)
		{
			this.session = session;
			return this;
		}

		public Record history(List<Session> history)
		{
			this.history = history == null ? Collections.emptyList() : history;
			return this;
		}

		static Record empty()
		{
			return new Record(null, null, 0, 0, 0, 0, 0, 0);
		}
	}

	/** The run in hand. */
	public static final class Live
	{
		final int ticksLeft;
		final int thievingXp;
		final long artefacts;
		final int opened;
		final double sceptreSoFar;
		/** Ticks ahead (negative) or behind the best run on entering this room, or null with nothing to compare. */
		final Integer split;

		public Live(int ticksLeft, int thievingXp, long artefacts, int opened, double sceptreSoFar)
		{
			this(ticksLeft, thievingXp, artefacts, opened, sceptreSoFar, null);
		}

		public Live(int ticksLeft, int thievingXp, long artefacts, int opened, double sceptreSoFar, Integer split)
		{
			this.split = split;
			this.ticksLeft = ticksLeft;
			this.thievingXp = thievingXp;
			this.artefacts = artefacts;
			this.opened = opened;
			this.sceptreSoFar = sceptreSoFar;
		}
	}

	/** The ready check and what the pack holds, while logged in. */
	public static final class Ready
	{
		final List<Check> checks;
		final int artefacts;
		final long artefactValue;

		public Ready(List<Check> checks, int artefacts, long artefactValue)
		{
			this.checks = checks == null ? Collections.emptyList() : checks;
			this.artefacts = artefacts;
			this.artefactValue = artefactValue;
		}
	}

	/** The artefacts waiting in the bank to be sold. */
	public static final class Stash
	{
		/** One kind of artefact. */
		public static final class Line
		{
			public final int itemId;
			public final String name;
			public final long count;
			public final long value;

			public Line(int itemId, String name, long count, long value)
			{
				this.itemId = itemId;
				this.name = name;
				this.count = count;
				this.value = value;
			}
		}

		final List<Line> lines;
		final long seenAt;
		/** The plugin whose saved bank this is, or null when this plugin saw the bank. */
		final String from;

		public Stash(List<Line> lines, long seenAt, String from)
		{
			this.lines = lines == null ? Collections.emptyList() : lines;
			this.seenAt = seenAt;
			this.from = from;
		}

		long count()
		{
			long n = 0;
			for (Line l : lines)
			{
				n += l.count;
			}
			return n;
		}

		long value()
		{
			long n = 0;
			for (Line l : lines)
			{
				n += l.value;
			}
			return n;
		}
	}

	final State state;
	final String player;
	final int level;
	final long xp;
	final int targetLevel;
	final Goal goal;
	/** The rest of the run in hand, or a fresh run's plan outside the pyramid. */
	final Plan plan;
	/** A whole fresh run at this level and goal, for per-run figures. */
	final Plan fresh;
	/** A fresh run at each room's entry level, for the ladder. */
	final List<Plan> ladder;
	final Step step;
	final Live live;
	final Record record;
	final long now;
	Ready ready;
	Stash stash;
	/** The Thieving levels Codex Navigator's pinned goals still need; empty without Navigator. */
	List<NavigatorGoals.Need> goals = Collections.emptyList();
	/** Whether the pyramid card names the player; off to share it without the name. */
	boolean nameOnCard = true;
	/** The run under way, or the last one, against its ghost; null with neither. */
	GhostSplits ghost;
	/** The last run's grade card, shown until the next run begins; null with none. */
	Grade grade;

	/** A run cut into its segments against its ghost, for the split table. */
	public static final class GhostSplits
	{
		final Run run;
		final Run ghost;
		final boolean live;
		final List<Ghost.Row> rows;

		/**
		 * @param run the run, or a snapshot of the one under way
		 * @param ghost the ghost it is measured against, or null when there is none yet
		 */
		public GhostSplits(Run run, Run ghost, boolean live)
		{
			this.run = run;
			this.ghost = ghost;
			this.live = live;
			this.rows = Ghost.rows(run, ghost, live);
		}
	}

	public View(State state, String player, int level, long xp, int targetLevel, Goal goal, Plan plan, Plan fresh,
		List<Plan> ladder, Step step, Live live, Record record, long now)
	{
		this.state = state;
		this.player = player;
		this.level = level;
		this.xp = xp;
		this.targetLevel = targetLevel;
		this.goal = goal == null ? Goal.EXPERIENCE : goal;
		this.plan = plan;
		this.fresh = fresh;
		this.ladder = ladder == null ? Collections.emptyList() : ladder;
		this.step = step;
		this.live = live;
		this.record = record == null ? Record.empty() : record;
		this.now = now;
	}

	public View ready(Ready ready)
	{
		this.ready = ready;
		return this;
	}

	public View stash(Stash stash)
	{
		this.stash = stash;
		return this;
	}

	public View goals(List<NavigatorGoals.Need> goals)
	{
		this.goals = goals == null ? Collections.emptyList() : goals;
		return this;
	}

	public View ghost(GhostSplits ghost)
	{
		this.ghost = ghost;
		return this;
	}

	public View grade(Grade grade)
	{
		this.grade = grade;
		return this;
	}

	public View nameOnCard(boolean value)
	{
		nameOnCard = value;
		return this;
	}

	public static View loggedOut()
	{
		return new View(State.LOGGED_OUT, null, 0, 0, 99, Goal.EXPERIENCE, null, null, null, null, null, null, System.currentTimeMillis());
	}

	boolean inRun()
	{
		return live != null;
	}
}
