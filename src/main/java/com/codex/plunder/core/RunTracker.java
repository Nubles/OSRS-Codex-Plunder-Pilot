package com.codex.plunder.core;

/**
 * Follows one run from the first spear trap to the way out: what was searched in which room,
 * what it paid, and how long each part took, which teaches the pace.
 */
public final class RunTracker
{
	private boolean active;
	private long startedAt;
	private int startTick;
	private Goal goal;
	private Room room;
	private int deepest;
	private int roomTick;
	private int lastActionTick;
	private int firstActionTick;
	private boolean actedHere;
	private int lastUrnTick;
	private int thievingXp;
	private int strengthXp;
	private int urns;
	private int chests;
	private int sarcophagi;
	private long artefacts;
	private int artefactCount;
	private boolean sceptre;
	private final long[] opened = new long[Room.values().length];
	private final int[] entered = new int[Room.values().length];
	private final int[] passes = new int[Room.values().length];
	private final int[] urnTicks = new int[Room.values().length];
	private final int[] chestTicks = new int[Room.values().length];
	private final int[] sarcophagusTicks = new int[Room.values().length];
	private final int[] urnsIn = new int[Room.values().length];

	public boolean isActive()
	{
		return active;
	}

	public void begin(long now, int tick, Goal goal)
	{
		active = true;
		startedAt = now;
		startTick = tick;
		this.goal = goal;
		room = null;
		deepest = 0;
		roomTick = tick;
		lastActionTick = tick;
		actedHere = false;
		lastUrnTick = -1;
		thievingXp = 0;
		strengthXp = 0;
		urns = 0;
		chests = 0;
		sarcophagi = 0;
		artefacts = 0;
		artefactCount = 0;
		sceptre = false;
		java.util.Arrays.fill(opened, 0);
		java.util.Arrays.fill(entered, -1);
		java.util.Arrays.fill(passes, -1);
		java.util.Arrays.fill(urnTicks, 0);
		java.util.Arrays.fill(chestTicks, 0);
		java.util.Arrays.fill(sarcophagusTicks, 0);
		java.util.Arrays.fill(urnsIn, 0);
	}

	/** The player is in this room now. Moving one room deeper times the room just left. */
	public void room(Room now, int tick, Pace pace)
	{
		if (!active || now == null || now == room)
		{
			return;
		}
		if (room != null && now.ordinal() == room.ordinal() + 1 && pace != null)
		{
			// The time from the last search to the next room is the door and the walk; the trap
			// came before the searches, so it is added back from the pace when there were any.
			int took = tick - (actedHere ? lastActionTick : roomTick);
			pace.passTook(actedHere ? took + (int) Math.round(pace.trap()) : took);
		}
		if (room != null && now.ordinal() == room.ordinal() + 1)
		{
			// The room's crossing without its searches: up to the first search, and from the last one out.
			passes[room.ordinal()] = actedHere ? (firstActionTick - roomTick) + (tick - lastActionTick) : tick - roomTick;
		}
		if (entered[now.ordinal()] < 0)
		{
			// Seen late (the plugin started mid-run) only ever makes a split slower, never a false best.
			entered[now.ordinal()] = Math.max(0, tick - startTick);
		}
		room = now;
		deepest = Math.max(deepest, now.getNumber());
		roomTick = tick;
		lastActionTick = tick;
		actedHere = false;
		lastUrnTick = -1;
	}

	public void thieving(int xp)
	{
		if (active && xp > 0)
		{
			thievingXp += xp;
		}
	}

	public void strength(int xp)
	{
		if (active && xp > 0)
		{
			strengthXp += xp;
		}
	}

	public void urnSearched(int tick, Pace pace)
	{
		if (!active)
		{
			return;
		}
		urns++;
		if (room != null)
		{
			urnTicks[room.ordinal()] += sinceLast(tick);
			urnsIn[room.ordinal()]++;
		}
		if (lastUrnTick >= 0 && pace != null)
		{
			pace.urnTook(room, tick - lastUrnTick);
		}
		lastUrnTick = tick;
		acted(tick);
	}

	public void chestOpened(int tick)
	{
		if (!active || room == null)
		{
			return;
		}
		chests++;
		opened[room.ordinal()]++;
		chestTicks[room.ordinal()] += sinceLast(tick);
		acted(tick);
	}

	public void sarcophagusOpened(int tick)
	{
		if (!active || room == null)
		{
			return;
		}
		sarcophagi++;
		opened[room.ordinal()]++;
		sarcophagusTicks[room.ordinal()] += sinceLast(tick);
		acted(tick);
	}

	/**
	 * Ticks since the last search in the room, which this search is charged with. The first search in
	 * a room is charged nothing: the trap and the walk to it belong to the room's crossing.
	 */
	private int sinceLast(int tick)
	{
		return actedHere ? Math.max(0, tick - lastActionTick) : 0;
	}

	private void acted(int tick)
	{
		if (!actedHere)
		{
			firstActionTick = tick;
		}
		lastActionTick = tick;
		actedHere = true;
	}

	public void artefacts(int count, long value)
	{
		if (active && count > 0)
		{
			artefactCount += count;
			artefacts += Math.max(0, value);
		}
	}

	public void sceptre()
	{
		if (active)
		{
			sceptre = true;
		}
	}

	/** Chests and sarcophagi opened this run, per room. */
	public long[] getOpened()
	{
		return opened.clone();
	}

	public Room getRoom()
	{
		return room;
	}

	/** The goal the run began with, which it is recorded and compared under even if the goal changes mid-run. */
	public Goal getGoal()
	{
		return goal;
	}

	/** Ticks from the start of the run to entering the room, or -1 when it has not been entered. */
	public int enteredAt(Room r)
	{
		return active && r != null ? entered[r.ordinal()] : -1;
	}

	public int getThievingXp()
	{
		return thievingXp;
	}

	public int getStrengthXp()
	{
		return strengthXp;
	}

	public long getArtefacts()
	{
		return artefacts;
	}

	public int getUrns()
	{
		return urns;
	}

	public int getChests()
	{
		return chests;
	}

	public int getSarcophagi()
	{
		return sarcophagi;
	}

	/** Ticks since the run began. */
	public int elapsed(int tick)
	{
		return active ? tick - startTick : 0;
	}

	/**
	 * The run so far as if it ended on this tick, for the ghost splits while it is under way; null
	 * when no run is.
	 */
	public Run snapshot(int tick)
	{
		if (!active)
		{
			return null;
		}
		return new Run(startedAt, Math.max(0, tick - startTick), deepest, thievingXp, strengthXp, urns, chests,
			sarcophagi, artefacts, artefactCount, Odds.atLeastOne(opened), sceptre, goal, entered, passes)
			.searches(urnTicks, chestTicks, sarcophagusTicks, urnsIn);
	}

	/**
	 * Ends the run.
	 *
	 * @return the run, or null when nothing at all was done in it
	 */
	public Run finish(int tick)
	{
		if (!active)
		{
			return null;
		}
		active = false;
		if (thievingXp == 0 && strengthXp == 0 && urns == 0 && chests == 0 && sarcophagi == 0 && !sceptre)
		{
			return null;
		}
		return new Run(startedAt, Math.max(0, tick - startTick), deepest, thievingXp, strengthXp, urns, chests,
			sarcophagi, artefacts, artefactCount, Odds.atLeastOne(opened), sceptre, goal, entered, passes)
			.searches(urnTicks, chestTicks, sarcophagusTicks, urnsIn);
	}
}
