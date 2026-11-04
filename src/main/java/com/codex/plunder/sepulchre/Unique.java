package com.codex.plunder.sepulchre;

import net.runelite.api.gameval.ItemID;

/**
 * The Sepulchre's rare finds, with the chance of each on a run like the player's own.
 *
 * <ul>
 * <li>Ring of endurance (uncharged): 1/200 from the Grand Hallowed Coffin (wiki, Grand Hallowed
 * Coffin, Uniques).</li>
 * <li>Strange old lockpick (full): from coffins, 1/200 on floor 1 down to 1/40 on floor 5 (wiki,
 * Coffin (Hallowed Sepulchre)); see {@link Floor#getLockpickOneIn()}.</li>
 * <li>Mysterious pages: 1/10 a coffin (wiki, Coffin (Hallowed Sepulchre)), and "One unique page is
 * found on each floor" (wiki, Hallowed Sepulchre), so floor N's coffins give page N.</li>
 * </ul>
 *
 * Item ids are RuneLite's (gameval ItemID).
 */
public enum Unique
{
	RING_OF_ENDURANCE("Ring of endurance", 0, ItemID.RING_OF_ENDURANCE_UNCHARGED, ItemID.RING_OF_ENDURANCE),
	STRANGE_OLD_LOCKPICK("Strange old lockpick", 0, ItemID.STRANGE_OLD_LOCKPICK_FULL, ItemID.STRANGE_OLD_LOCKPICK),
	PAGE_1("Mysterious page 1", 1, ItemID.HALLOWED_FLOOR1_PAGE),
	PAGE_2("Mysterious page 2", 2, ItemID.HALLOWED_FLOOR2_PAGE),
	PAGE_3("Mysterious page 3", 3, ItemID.HALLOWED_FLOOR3_PAGE),
	PAGE_4("Mysterious page 4", 4, ItemID.HALLOWED_FLOOR4_PAGE),
	PAGE_5("Mysterious page 5", 5, ItemID.HALLOWED_FLOOR5_PAGE);

	/** The Grand Hallowed Coffin's Ring of endurance, one in so many. */
	public static final int RING_ONE_IN = 200;
	/** A coffin's Mysterious page, one in so many. */
	public static final int PAGE_ONE_IN = 10;

	private final String label;
	private final int floor;
	private final int[] items;

	Unique(String label, int floor, int... items)
	{
		this.label = label;
		this.floor = floor;
		this.items = items;
	}

	public String getLabel()
	{
		return label;
	}

	/** The floor a page comes from, 0 for the rest. */
	public int getFloor()
	{
		return floor;
	}

	/** The unique an item is, or null. */
	public static Unique of(int itemId)
	{
		for (Unique u : values())
		{
			for (int id : u.items)
			{
				if (id == itemId)
				{
					return u;
				}
			}
		}
		return null;
	}

	/** The chance of at least one on a run that loots {@code coffins} a floor and opens the grand coffin {@code grand} of the time. */
	public double perRun(double[] coffins, double grand)
	{
		switch (this)
		{
			case RING_OF_ENDURANCE:
				return Math.max(0, Math.min(1, grand)) / RING_ONE_IN;
			case STRANGE_OLD_LOCKPICK:
			{
				double none = 1;
				for (Floor f : Floor.values())
				{
					none *= Math.pow(1 - 1.0 / f.getLockpickOneIn(), at(coffins, f.getNumber()));
				}
				return 1 - none;
			}
			default:
				return 1 - Math.pow(1 - 1.0 / PAGE_ONE_IN, at(coffins, floor));
		}
	}

	/** The chance on the player's own runs, at their pace. */
	public double perRun(SepulchrePace pace)
	{
		if (pace == null)
		{
			return 0;
		}
		double[] c = new double[SepulchreRun.FLOORS];
		for (int f = 1; f <= c.length; f++)
		{
			c[f - 1] = pace.coffinsOn(f);
		}
		return perRun(c, pace.getGrandShare());
	}

	/**
	 * The chance there would have been at least one by now, given every coffin and grand coffin the
	 * log has counted: how dry a player is who has not had it.
	 */
	public double hadByNow(long[] coffinsByFloor, long grands)
	{
		if (this == RING_OF_ENDURANCE)
		{
			return 1 - Math.pow(1 - 1.0 / RING_ONE_IN, Math.max(0, grands));
		}
		double[] c = new double[SepulchreRun.FLOORS];
		for (int i = 0; i < c.length && coffinsByFloor != null && i < coffinsByFloor.length; i++)
		{
			c[i] = Math.max(0, coffinsByFloor[i]);
		}
		return perRun(c, 0);
	}

	/** Runs for a given chance of at least one, at a per-run chance; -1 when it never comes. */
	public static long runsFor(double perRun, double chance)
	{
		if (perRun <= 0)
		{
			return -1;
		}
		if (perRun >= 1)
		{
			return 1;
		}
		return (long) Math.ceil(Math.log(1 - chance) / Math.log(1 - perRun));
	}

	private static double at(double[] coffins, int floor)
	{
		return coffins == null || floor < 1 || floor > coffins.length ? 0 : Math.max(0, coffins[floor - 1]);
	}
}
