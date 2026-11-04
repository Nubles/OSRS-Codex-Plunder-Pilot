package com.codex.plunder.ui;

import com.codex.plunder.sepulchre.FloorSplits;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * The Sepulchre's floor splits: each floor's time against the ghost's, the gap green where the
 * run is quicker and ember where it is slower, the floor under way lit and timed as it goes. The
 * panel and the in-game overlay draw the same table.
 */
public final class FloorTable extends JComponent
{
	static final int ROW = 16;
	static final int HEAD = 14;

	private final FloorSplits splits;

	public FloorTable(FloorSplits splits)
	{
		this.splits = splits;
		setOpaque(false);
		setPreferredSize(new Dimension(10, height(splits)));
	}

	/** The table's height with its footer. */
	public static int height(FloorSplits splits)
	{
		return HEAD + splits.getRows().size() * ROW + ROW + 2;
	}

	/** A time as "1:23.4", or "-" when not known. */
	public static String time(int ms)
	{
		if (ms < 0)
		{
			return "-";
		}
		int tenths = (int) Math.round(ms / 100.0);
		return tenths / 600 + ":" + String.format(Locale.ENGLISH, "%02d.%d", tenths / 10 % 60, tenths % 10);
	}

	/** A gap with its sign: "+3.2s", "-1:04", or "even" under a tenth. */
	public static String delta(int ms)
	{
		int tenths = (int) Math.round(Math.abs(ms) / 100.0);
		if (tenths == 0)
		{
			return "even";
		}
		String sign = ms < 0 ? "-" : "+";
		if (tenths < 600)
		{
			return sign + tenths / 10 + "." + tenths % 10 + "s";
		}
		int s = tenths / 10;
		return sign + s / 60 + ":" + String.format(Locale.ENGLISH, "%02d", s % 60);
	}

	/** Green quicker, ember slower, muted level. */
	public static Color deltaColor(int ms)
	{
		return Math.abs(ms) < 50 ? Theme.TEXT_MUTED : ms < 0 ? Theme.GREEN : Theme.EMBER;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		paint(g, splits, getWidth());
		g.dispose();
	}

	/** Draws the table at the origin, so wide. */
	public static void paint(Graphics2D g, FloorSplits splits, int w)
	{
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		int deltaRight = w - 2;
		int ghostRight = deltaRight - fm.stringWidth("+00.0s") - 6;
		int mineRight = ghostRight - fm.stringWidth("00:00.0") - 6;

		int headBase = fm.getAscent();
		g.setColor(Theme.TEXT_FAINT);
		g.drawString("FLOOR", 0, headBase);
		right(g, fm, "YOU", mineRight, headBase);
		right(g, fm, "GHOST", ghostRight, headBase);
		right(g, fm, "+/-", deltaRight, headBase);

		int y = HEAD;
		for (FloorSplits.Row row : splits.getRows())
		{
			int base = y + (ROW + fm.getAscent()) / 2 - 1;
			if (row.isLive())
			{
				g.setColor(Theme.alpha(Theme.GOLD, 28));
				g.fill(new RoundRectangle2D.Float(-4, y, w + 8, ROW, 6, 6));
			}
			boolean ran = row.getMine() >= 0;
			g.setColor(row.isLive() ? Theme.GOLD : ran ? Theme.TEXT : Theme.TEXT_FAINT);
			g.drawString("Floor " + row.getFloor(), 0, base);
			g.setColor(row.isLive() ? Theme.GOLD_BRIGHT : Theme.TEXT);
			right(g, fm, time(row.getMine()), mineRight, base);
			g.setColor(Theme.TEXT_FAINT);
			right(g, fm, time(row.getGhost()), ghostRight, base);
			Integer d = row.getDelta();
			if (d != null)
			{
				String s = delta(d);
				Ui.shadowed(g, s, deltaRight - fm.stringWidth(s), base, deltaColor(d));
			}
			y += ROW;
		}
		g.setColor(Theme.STROKE_SOFT);
		g.fillRect(0, y + 1, w, 1);
		int base = y + 2 + (ROW + fm.getAscent()) / 2 - 1;
		String left = splits.isLive() ? "On floor " + splits.getLiveFloor()
			: splits.getRun() == null ? "" : "To floor " + splits.getRun().getDeepest();
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(left, 0, base);
		Integer total = splits.total();
		String right = splits.getGhost() == null ? "no ghost yet" : total == null ? "on the ghost's pace"
			: Math.abs(total) < 50 ? "level with ghost" : delta(total).replace("+", "").replace("-", "") + (total < 0 ? " ahead" : " behind");
		Color c = splits.getGhost() == null || total == null ? Theme.TEXT_FAINT : deltaColor(total);
		Ui.shadowed(g, right, deltaRight - fm.stringWidth(right), base, c);
	}

	private static void right(Graphics2D g, FontMetrics fm, String s, int right, int base)
	{
		g.drawString(s, right - fm.stringWidth(s), base);
	}
}
