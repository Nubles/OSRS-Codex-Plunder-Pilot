package com.codex.plunder.sepulchre;

/**
 * The five floors of the Hallowed Sepulchre, as the Old School wiki records them.
 *
 * <ul>
 * <li>Agility level and floor experience: wiki, Hallowed Sepulchre, the floor table ("Level Req.",
 * "Floor Exp"): 52/575, 62/925, 72/1,600, 77/2,875, 87/5,725.</li>
 * <li>Hallowed marks per coffin: wiki, Coffin (Hallowed Sepulchre), "Hallowed marks" by floor:
 * 1, 2-3, 3-5, 3-6, 4-6.</li>
 * <li>Coffins on each floor: the same page's Locations table, "Spawns": 1, 2, 3, 1+1, 2+1.</li>
 * <li>Strange old lockpick (full) per coffin: the same page, "1/200 (F1), 1/120 (F2), 1/90 (F3),
 * 1/60 (F4), 1/40 (F5)".</li>
 * </ul>
 */
public enum Floor
{
	ONE(1, 52, 575, 1, 1, 1, 200),
	TWO(2, 62, 925, 2, 3, 2, 120),
	THREE(3, 72, 1_600, 3, 5, 3, 90),
	FOUR(4, 77, 2_875, 3, 6, 2, 60),
	FIVE(5, 87, 5_725, 4, 6, 3, 40);

	/** Coffins need this Thieving level (wiki, Coffin (Hallowed Sepulchre): "require 66 Thieving"). */
	public static final int COFFIN_THIEVING = 66;
	/** Marks from the Grand Hallowed Coffin (wiki, Grand Hallowed Coffin: Hallowed mark "8–10", "Always"). */
	public static final int GRAND_MARKS_MIN = 8;
	public static final int GRAND_MARKS_MAX = 10;
	/** The most marks one ordinary coffin gives on any floor. */
	public static final int COFFIN_MARKS_MAX = 6;

	private final int number;
	private final int agility;
	private final int xp;
	private final int marksMin;
	private final int marksMax;
	private final int coffins;
	private final int lockpickOneIn;

	Floor(int number, int agility, int xp, int marksMin, int marksMax, int coffins, int lockpickOneIn)
	{
		this.number = number;
		this.agility = agility;
		this.xp = xp;
		this.marksMin = marksMin;
		this.marksMax = marksMax;
		this.coffins = coffins;
		this.lockpickOneIn = lockpickOneIn;
	}

	public int getNumber()
	{
		return number;
	}

	/** The Agility level the floor needs. */
	public int getAgility()
	{
		return agility;
	}

	/** Agility experience for finishing the floor. */
	public int getXp()
	{
		return xp;
	}

	public int getMarksMin()
	{
		return marksMin;
	}

	public int getMarksMax()
	{
		return marksMax;
	}

	/** Coffins the floor holds. */
	public int getCoffins()
	{
		return coffins;
	}

	/** One in so many coffins on this floor holds a Strange old lockpick (full). */
	public int getLockpickOneIn()
	{
		return lockpickOneIn;
	}

	/** The floor by number, 1 to 5, or null. */
	public static Floor of(int number)
	{
		return number >= 1 && number <= values().length ? values()[number - 1] : null;
	}

	/** How many floors an Agility level opens, 0 to 5. */
	public static int open(int agility)
	{
		int n = 0;
		for (Floor f : values())
		{
			n += agility >= f.agility ? 1 : 0;
		}
		return n;
	}

	/** Agility experience for a run that finishes floors 1 to {@code deepest}. */
	public static int xpTo(int deepest)
	{
		int xp = 0;
		for (Floor f : values())
		{
			xp += f.number <= deepest ? f.xp : 0;
		}
		return xp;
	}
}
