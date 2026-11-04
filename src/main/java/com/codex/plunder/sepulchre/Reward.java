package com.codex.plunder.sepulchre;

/**
 * What Hallowed marks buy at the Mysterious Hallowed Goods shop (wiki, Mysterious Hallowed Goods:
 * Hallowed grapple, focus, symbol and hammer 100 each, Hallowed ring 250, Dark dye 300, Dark acorn
 * 3,000). Dark graceful is six dyes (wiki, Dark dye: "Six are needed to recolour the full graceful
 * set"). The Ring of endurance is not sold; it is a loot chance, counted with the uniques.
 */
public enum Reward
{
	DARK_GRACEFUL("Dark graceful", "six dark dyes", 6 * 300),
	DARK_DYE("Dark dye", "one graceful piece", 300),
	HALLOWED_TOOLS("Hallowed tools", "grapple, focus, symbol, hammer", 4 * 100),
	HALLOWED_RING("Hallowed ring", "one ring", 250),
	DARK_ACORN("Dark acorn", "one acorn", 3_000);

	private final String label;
	private final String what;
	private final int cost;

	Reward(String label, String what, int cost)
	{
		this.label = label;
		this.what = what;
		this.cost = cost;
	}

	public String getLabel()
	{
		return label;
	}

	public String getWhat()
	{
		return what;
	}

	/** Hallowed marks it costs. */
	public int getCost()
	{
		return cost;
	}

	@Override
	public String toString()
	{
		return label + " (" + String.format(java.util.Locale.ENGLISH, "%,d", cost) + ")";
	}
}
