package com.codex.plunder.ui;

import com.codex.plunder.sepulchre.FloorSplits;
import com.codex.plunder.sepulchre.Reward;
import com.codex.plunder.sepulchre.SepulchrePace;
import com.codex.plunder.sepulchre.SepulchreRun;
import com.codex.plunder.sepulchre.Unique;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What the Sepulchre panel shows, worked out on the client thread and handed over whole: the
 * run under way or the last one against its ghost, the player's pace, the marks in hand and the
 * reward they are saving for, and the uniques still to find.
 */
public final class SepulchreView
{
	public enum State
	{
		LOGGED_OUT,
		READY
	}

	public final State state;
	public final String name;
	public final int agility;
	public final int thieving;
	public final long now;

	/** The run under way against its ghost, or the last run's, or null. */
	public FloorSplits splits;
	/** The last finished run became the ghost for its depth. */
	public boolean newGhost;
	/** Recent runs, oldest first. */
	public List<SepulchreRun> runs = Collections.emptyList();
	public long totalRuns;
	public long totalMarks;
	public long totalXp;
	public long grands;
	public long[] coffins = new long[5];
	public SepulchrePace pace;
	/** Marks in the pack now. */
	public long packMarks;
	/** Marks in the bank as last seen, -1 before the bank has been seen. */
	public long bankMarks = -1;
	public Reward target = Reward.DARK_GRACEFUL;
	public Set<Unique> got = EnumSet.noneOf(Unique.class);

	public SepulchreView(State state, String name, int agility, int thieving, long now)
	{
		this.state = state;
		this.name = name;
		this.agility = agility;
		this.thieving = thieving;
		this.now = now;
	}

	public static SepulchreView loggedOut()
	{
		return new SepulchreView(State.LOGGED_OUT, null, 0, 0, System.currentTimeMillis());
	}

	public boolean inRun()
	{
		return splits != null && splits.isLive();
	}

	/** Marks held: the pack and the bank as last seen. */
	public long held()
	{
		return packMarks + Math.max(0, bankMarks);
	}
}
