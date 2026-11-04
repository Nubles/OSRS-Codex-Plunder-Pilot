package com.codex.plunder.ui;

import com.codex.plunder.core.Ghost;
import com.codex.plunder.core.Room;
import com.codex.plunder.core.Run;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * The ghost split table: each room in game ticks, this run against the ghost, with the gap green
 * where the run is quicker and ember where it is slower. The room under way is lit and opened up
 * into its parts (the trap, walk and door, the urns, the chest, the sarcophagus), so the tick it
 * falls behind is the tick it shows. The panel and the in-game overlay draw the same table.
 */
public final class GhostTable extends JComponent
{
	static final int ROW = 16;
	static final int HEAD = 14;

	/** One line of the table. */
	static final class Line
	{
		final String label;
		final int mine;
		final int ghost;
		final Integer delta;
		final boolean part;
		final boolean live;

		Line(String label, int mine, int ghost, Integer delta, boolean part, boolean live)
		{
			this.label = label;
			this.mine = mine;
			this.ghost = ghost;
			this.delta = delta;
			this.part = part;
			this.live = live;
		}
	}

	private final List<Line> lines;
	private final Integer total;
	private final Room at;

	public GhostTable(View.GhostSplits splits)
	{
		this(lines(splits.run, splits.ghost, splits.rows, true), total(splits), splits.run == null ? null : Room.of(splits.run.getDeepest()));
	}

	private GhostTable(List<Line> lines, Integer total, Room at)
	{
		this.lines = lines;
		this.total = total;
		this.at = at;
		setOpaque(false);
		setPreferredSize(new Dimension(10, height(lines.size(), true)));
	}

	/**
	 * The slim table the in-game overlay draws: a line a room and the gap so far, without the parts,
	 * worked out once when it is handed over so a frame only draws.
	 */
	public static final class Slim
	{
		private final List<Line> lines;
		private final Integer total;
		private final Room at;

		public Slim(View.GhostSplits splits)
		{
			lines = lines(splits.run, splits.ghost, splits.rows, false);
			total = total(splits);
			at = splits.run == null ? null : Room.of(splits.run.getDeepest());
		}

		public int height()
		{
			return GhostTable.height(lines.size(), true);
		}

		public void paint(Graphics2D g, int w)
		{
			GhostTable.paint(g, lines, total, at, w, true);
		}
	}

	/** The table's height for so many lines, with the footer or without. */
	static int height(int lines, boolean footer)
	{
		return HEAD + lines * ROW + (footer ? ROW + 2 : 0);
	}

	/** Ticks ahead (negative) or behind the ghost on entering the deepest room so far, or null. */
	static Integer total(View.GhostSplits splits)
	{
		return splits.run == null ? null : Ghost.entryDelta(splits.run, splits.ghost, Room.of(splits.run.getDeepest()));
	}

	/**
	 * The lines: a room each, and the parts of the room under way.
	 *
	 * @param parts whether to open the room under way into its parts
	 */
	static List<Line> lines(Run run, Run ghost, List<Ghost.Row> rows, boolean parts)
	{
		List<Line> out = new ArrayList<>();
		for (Ghost.Row row : rows)
		{
			out.add(new Line("Room " + row.getRoom().getNumber(), row.getMine(), row.getGhost(), row.getDelta(), false, row.isLive()));
			if (parts && row.isLive())
			{
				for (Ghost.Part p : Ghost.Part.values())
				{
					int mine = Ghost.part(run, row.getRoom(), p);
					int theirs = ghost == null ? -1 : Ghost.part(ghost, row.getRoom(), p);
					if (p != Ghost.Part.CROSS && mine <= 0 && theirs <= 0)
					{
						continue;
					}
					// Inside the ghost's time a part under way says nothing yet; over it, it shows.
					Integer d = mine < 0 || theirs < 0 || mine <= theirs ? null : mine - theirs;
					// A chest or sarcophagus not opened yet reads as not done, not as no time.
					if (p == Ghost.Part.CHEST || p == Ghost.Part.SARCOPHAGUS)
					{
						mine = mine <= 0 ? -1 : mine;
						d = mine < 0 ? null : d;
					}
					out.add(new Line(partName(p, run.urnsAt(row.getRoom())), mine, theirs, d, true, true));
				}
			}
		}
		return out;
	}

	static String partName(Ghost.Part p, int urns)
	{
		switch (p)
		{
			case URNS:
				return urns + (urns == 1 ? " urn" : " urns");
			case CHEST:
				return "chest";
			case SARCOPHAGUS:
				return "sarcophagus";
			default:
				return "trap, walk, door";
		}
	}

	/** Green quicker, ember slower, muted level. */
	static Color deltaColor(int d)
	{
		return d == 0 ? Theme.TEXT_MUTED : d < 0 ? Theme.GREEN : Theme.EMBER;
	}

	static String delta(int d)
	{
		return d == 0 ? "even" : (d < 0 ? "-" : "+") + Math.abs(d);
	}

	static String ticks(int t)
	{
		return t < 0 ? "-" : t + "t";
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		paint(g, lines, total, at, getWidth(), true);
		g.dispose();
	}

	/** Draws the table at the origin, so wide. */
	static void paint(Graphics2D g, List<Line> lines, Integer total, Room at, int w, boolean footer)
	{
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		int deltaRight = w - 2;
		int ghostRight = deltaRight - fm.stringWidth("+000") - 8;
		int mineRight = ghostRight - Math.max(fm.stringWidth("GHOST"), fm.stringWidth("000t")) - 8;

		int headBase = fm.getAscent();
		g.setColor(Theme.TEXT_FAINT);
		g.drawString("ROOM", 0, headBase);
		right(g, fm, "YOU", mineRight, headBase);
		right(g, fm, "GHOST", ghostRight, headBase);
		right(g, fm, "+/-", deltaRight, headBase);

		int y = HEAD;
		for (Line line : lines)
		{
			int base = y + (ROW + fm.getAscent()) / 2 - 1;
			if (line.live && !line.part)
			{
				g.setColor(Theme.alpha(Theme.GOLD, 28));
				g.fill(new RoundRectangle2D.Float(-4, y, w + 8, ROW, 6, 6));
			}
			int x = line.part ? 10 : 0;
			if (line.part)
			{
				g.setColor(Theme.alpha(Theme.GOLD_DIM, 160));
				g.fillRect(3, y + ROW / 2 - 1, 3, 1);
			}
			g.setColor(line.part ? Theme.TEXT_MUTED : line.live ? Theme.GOLD : Theme.TEXT);
			g.drawString(Ui.clip(line.label, fm, mineRight - fm.stringWidth("000t") - x - 4), x, base);
			g.setColor(line.live ? Theme.GOLD_BRIGHT : line.part ? Theme.TEXT_MUTED : Theme.TEXT);
			right(g, fm, ticks(line.mine), mineRight, base);
			g.setColor(Theme.TEXT_FAINT);
			right(g, fm, ticks(line.ghost), ghostRight, base);
			if (line.delta != null)
			{
				String d = delta(line.delta);
				Ui.shadowed(g, d, deltaRight - fm.stringWidth(d), base, deltaColor(line.delta));
			}
			y += ROW;
		}
		if (footer)
		{
			g.setColor(Theme.STROKE_SOFT);
			g.fillRect(0, y + 1, w, 1);
			int base = y + 2 + (ROW + fm.getAscent()) / 2 - 1;
			String left = at == null ? "" : "Into room " + at.getNumber();
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(left, 0, base);
			String right = total == null ? "no ghost yet" : total == 0 ? "level with ghost"
				: Math.abs(total) + (Math.abs(total) == 1 ? " tick " : " ticks ") + (total < 0 ? "ahead" : "behind");
			Color c = total == null ? Theme.TEXT_FAINT : deltaColor(total);
			Ui.shadowed(g, right, deltaRight - fm.stringWidth(right), base, c);
		}
	}

	private static void right(Graphics2D g, FontMetrics fm, String s, int right, int base)
	{
		g.drawString(s, right - fm.stringWidth(s), base);
	}
}
