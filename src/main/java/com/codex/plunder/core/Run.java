package com.codex.plunder.core;

/** One finished trip into the pyramid. */
public final class Run
{
	private long at;
	private int ticks;
	private int deepest;
	private int thievingXp;
	private int strengthXp;
	private int urns;
	private int chests;
	private int sarcophagi;
	private long artefacts;
	private int artefactCount;
	private double sceptreChance;
	private boolean sceptre;
	private Goal goal;
	/** Ticks from the start of the run to entering each room, by room; -1 where not seen. Older saves have none. */
	private int[] splits;
	/** Ticks each room took to cross without its searches: the trap, the walk and the door; -1 where not seen. Older saves have none. */
	private int[] passes;
	/**
	 * Ticks each room's searches took, by room, the way the ghost splits time them: each search is
	 * timed from the one before it in the room, so the first search's walk and the trap count in the
	 * crossing. Null in saves made before ghost splits.
	 */
	private int[] urnTicks;
	private int[] chestTicks;
	private int[] sarcophagusTicks;
	/** Urns searched in each room. */
	private int[] urnsIn;

	Run()
	{
	}

	Run(long at, int ticks, int deepest, int thievingXp, int strengthXp, int urns, int chests, int sarcophagi,
		long artefacts, int artefactCount, double sceptreChance, boolean sceptre, Goal goal)
	{
		this(at, ticks, deepest, thievingXp, strengthXp, urns, chests, sarcophagi, artefacts, artefactCount, sceptreChance, sceptre, goal, null);
	}

	Run(long at, int ticks, int deepest, int thievingXp, int strengthXp, int urns, int chests, int sarcophagi,
		long artefacts, int artefactCount, double sceptreChance, boolean sceptre, Goal goal, int[] splits)
	{
		this(at, ticks, deepest, thievingXp, strengthXp, urns, chests, sarcophagi, artefacts, artefactCount, sceptreChance, sceptre, goal, splits, null);
	}

	Run(long at, int ticks, int deepest, int thievingXp, int strengthXp, int urns, int chests, int sarcophagi,
		long artefacts, int artefactCount, double sceptreChance, boolean sceptre, Goal goal, int[] splits, int[] passes)
	{
		this.splits = splits == null ? null : splits.clone();
		this.passes = passes == null ? null : passes.clone();
		this.artefactCount = artefactCount;
		this.at = at;
		this.ticks = ticks;
		this.deepest = deepest;
		this.thievingXp = thievingXp;
		this.strengthXp = strengthXp;
		this.urns = urns;
		this.chests = chests;
		this.sarcophagi = sarcophagi;
		this.artefacts = artefacts;
		this.sceptreChance = sceptreChance;
		this.sceptre = sceptre;
		this.goal = goal;
	}

	/** When the run began, in epoch milliseconds. */
	public long getAt()
	{
		return at;
	}

	public int getTicks()
	{
		return ticks;
	}

	/** The deepest room reached. */
	public int getDeepest()
	{
		return deepest;
	}

	public int getThievingXp()
	{
		return thievingXp;
	}

	public int getStrengthXp()
	{
		return strengthXp;
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

	/** What the artefacts picked up were worth at Grand Exchange prices. */
	public long getArtefacts()
	{
		return artefacts;
	}

	/** How many artefacts were picked up, each taking a slot. */
	public int getArtefactCount()
	{
		return artefactCount;
	}

	/** The chance this run's openings had of a sceptre. */
	public double getSceptreChance()
	{
		return sceptreChance;
	}

	public boolean isSceptre()
	{
		return sceptre;
	}

	void markSceptre()
	{
		sceptre = true;
	}

	public Goal getGoal()
	{
		return goal == null ? Goal.EXPERIENCE : goal;
	}

	/**
	 * Ticks from the start of the run to entering the room, or -1 when the run never got there,
	 * entered it before the plugin was watching, or was saved before splits were kept.
	 */
	public int splitAt(Room room)
	{
		return splits == null || room == null || room.ordinal() >= splits.length ? -1 : splits[room.ordinal()];
	}

	/**
	 * Ticks crossing the room took without its searches, or -1 when the run did not cross it,
	 * or was saved before crossings were kept.
	 */
	public int passAt(Room room)
	{
		return passes == null || room == null || room.ordinal() >= passes.length ? -1 : passes[room.ordinal()];
	}

	/** Adds the searches' times room by room, which the ghost splits are drawn from. */
	Run searches(int[] urnTicks, int[] chestTicks, int[] sarcophagusTicks, int[] urnsIn)
	{
		this.urnTicks = urnTicks == null ? null : urnTicks.clone();
		this.chestTicks = chestTicks == null ? null : chestTicks.clone();
		this.sarcophagusTicks = sarcophagusTicks == null ? null : sarcophagusTicks.clone();
		this.urnsIn = urnsIn == null ? null : urnsIn.clone();
		return this;
	}

	/** Whether the run kept its searches' times room by room, which a ghost needs. */
	public boolean hasSearches()
	{
		int n = Room.values().length;
		return splits != null && splits.length == n && urnTicks != null && urnTicks.length == n && chestTicks != null
			&& chestTicks.length == n && sarcophagusTicks != null && sarcophagusTicks.length == n && urnsIn != null && urnsIn.length == n;
	}

	/** Ticks spent searching urns in the room, or -1 when not kept. */
	public int urnTicksAt(Room room)
	{
		return at(urnTicks, room);
	}

	public int chestTicksAt(Room room)
	{
		return at(chestTicks, room);
	}

	public int sarcophagusTicksAt(Room room)
	{
		return at(sarcophagusTicks, room);
	}

	/** Urns searched in the room, or -1 when not kept. */
	public int urnsAt(Room room)
	{
		return at(urnsIn, room);
	}

	private static int at(int[] a, Room room)
	{
		return a == null || room == null || room.ordinal() >= a.length ? -1 : a[room.ordinal()];
	}

	/** Brings back into range what a damaged save left out of it. */
	void repair()
	{
		ticks = Math.max(0, ticks);
		deepest = Math.max(0, Math.min(Room.values().length, deepest));
		thievingXp = Math.max(0, thievingXp);
		strengthXp = Math.max(0, strengthXp);
		urns = Math.max(0, urns);
		chests = Math.max(0, chests);
		sarcophagi = Math.max(0, sarcophagi);
		artefacts = Math.max(0, artefacts);
		artefactCount = Math.max(0, artefactCount);
		sceptreChance = sceptreChance >= 0 && sceptreChance <= 1 ? sceptreChance : 0;
		if (splits != null)
		{
			for (int k = 0; k < splits.length; k++)
			{
				splits[k] = Math.max(-1, splits[k]);
			}
		}
		if (passes != null)
		{
			for (int k = 0; k < passes.length; k++)
			{
				passes[k] = Math.max(-1, passes[k]);
			}
		}
		for (int[] a : new int[][]{urnTicks, chestTicks, sarcophagusTicks, urnsIn})
		{
			if (a != null)
			{
				for (int k = 0; k < a.length; k++)
				{
					a[k] = Math.max(0, a[k]);
				}
			}
		}
	}
}
