package com.codex.plunder.overlay;

import com.codex.plunder.ui.Sigil;
import com.codex.plunder.ui.Theme;
import com.codex.plunder.ui.Ui;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A slim gilt plaque in the game view during a run: the room, the clock, and the one thing to
 * do next, with a hairline under it that drains with the clock. It draws what the plugin hands it.
 */
public class PilotPlaque extends Overlay
{
	private static final int PAD = 7;

	private volatile Shown shown;

	private static final class Shown
	{
		final String figure;
		final String step;
		final String detail;
		final Color color;
		final double progress;
		final String split;
		final Color splitColor;
		/** The layout, worked out on the first frame this is shown and kept for the rest. */
		String[] lines;
		String stepShown;
		int width;
		int height;
		int chip;
		RoundRectangle2D shape;
		GradientPaint fill;
		Color rim;
		Color chipFill;
		Color chipRim;

		Shown(String figure, String step, String detail, Color color, double progress, String split, Color splitColor)
		{
			this.split = split;
			this.splitColor = splitColor;
			this.figure = figure;
			this.step = step;
			this.detail = detail;
			this.color = color;
			this.progress = progress;
		}
	}

	public PilotPlaque()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/**
	 * @param figure the big text, such as "R6  3:12", or null to hide the plaque
	 * @param progress the share of the clock left, 0 to 1
	 */
	public void show(String figure, String step, String detail, Color color, double progress)
	{
		show(figure, step, detail, color, progress, null, null);
	}

	/**
	 * @param split the room's split against the best run, such as "-4s", drawn after the figure; or null
	 * @param splitColor green ahead, ember behind
	 */
	public void show(String figure, String step, String detail, Color color, double progress, String split, Color splitColor)
	{
		shown = figure == null ? null : new Shown(figure, step, detail, color, progress, split, splitColor);
	}

	/** The widest a detail line runs before it breaks onto a second. */
	private static final int DETAIL_WIDTH = 260;

	/** The detail in one line, or two broken at a space; the second is cut short if it is still too long. */
	static String[] lines(String text, FontMetrics fm, int width)
	{
		if (fm.stringWidth(text) <= width)
		{
			return new String[]{text};
		}
		int cut = -1;
		for (int i = text.indexOf(' '); i > 0; i = text.indexOf(' ', i + 1))
		{
			if (fm.stringWidth(text.substring(0, i)) > width)
			{
				break;
			}
			cut = i;
		}
		if (cut < 0)
		{
			return new String[]{text};
		}
		return new String[]{text.substring(0, cut), text.substring(cut + 1)};
	}

	/** The widest the step runs: a long one is cut short rather than stretch the plaque across the game. */
	private static final int STEP_WIDTH = 300;
	private static final Color TRACK = Theme.alpha(Theme.INK_DEEP, 220);
	/** The mark, drawn once. */
	private BufferedImage mark;

	/** Works out what is shown once, so a frame draws without measuring or allocating. */
	private static void layout(Shown s, FontMetrics bold, FontMetrics small, int textX)
	{
		// A split sits after the figure as a small framed chip, the way a speedrun timer flashes one.
		s.chip = s.split == null ? 0 : small.stringWidth(s.split) + 8;
		s.stepShown = Ui.clip(s.step == null ? "" : s.step, bold, STEP_WIDTH);
		int line1 = bold.stringWidth(s.figure) + 10 + (s.chip > 0 ? s.chip + 8 : 0) + bold.stringWidth(s.stepShown);
		// A long detail takes a second line rather than being cut off mid-word.
		String[] lines = s.detail == null ? new String[0] : lines(s.detail, small, DETAIL_WIDTH);
		int line2 = 0;
		for (int i = 0; i < lines.length; i++)
		{
			lines[i] = Ui.clip(lines[i], small, Math.max(DETAIL_WIDTH, line1));
			line2 = Math.max(line2, small.stringWidth(lines[i]));
		}
		s.width = textX + Math.max(line1, line2) + PAD;
		s.height = PAD + bold.getHeight() + lines.length * (small.getHeight() + 1) + PAD;
		s.shape = new RoundRectangle2D.Float(0.5f, 0.5f, s.width - 1, s.height - 1, 10, 10);
		s.fill = new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 232), 0, s.height, Theme.alpha(Theme.INK_DEEP, 232));
		s.rim = Theme.alpha(s.color, 170);
		if (s.splitColor != null)
		{
			s.chipFill = Theme.alpha(s.splitColor, 46);
			s.chipRim = Theme.alpha(s.splitColor, 150);
		}
		s.lines = lines;
	}

	/**
	 * Draws on a copy of the graphics, so no font, colour, stroke, hint, transform or clip set here
	 * reaches the overlays drawn after this one.
	 */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			return draw(g);
		}
		finally
		{
			g.dispose();
		}
	}

	private Dimension draw(Graphics2D g)
	{
		Shown s = shown;
		if (s == null)
		{
			return null;
		}
		Theme.smooth(g);
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int icon = 16;
		int textX = PAD + icon + 6;
		if (s.lines == null)
		{
			layout(s, bold, small, textX);
		}
		String[] lines = s.lines;
		int width = s.width;
		int height = s.height;
		int chip = s.chip;
		g.setPaint(s.fill);
		g.fill(s.shape);
		g.setColor(s.rim);
		g.draw(s.shape);

		if (mark == null)
		{
			mark = Sigil.icon(icon);
		}
		g.drawImage(mark, PAD, (height - icon) / 2 - 1, null);
		int base = PAD + bold.getAscent();
		g.setFont(Theme.title());
		Ui.shadowed(g, s.figure, textX, base, s.color);
		int stepX = textX + bold.stringWidth(s.figure) + 10;
		if (chip > 0)
		{
			int ch = small.getHeight() + 1;
			int cy = base - bold.getAscent() / 2 - ch / 2 - 1;
			g.setColor(s.chipFill);
			g.fillRoundRect(stepX, cy, chip, ch, 6, 6);
			g.setColor(s.chipRim);
			g.drawRoundRect(stepX, cy, chip, ch, 6, 6);
			g.setFont(Theme.small());
			Ui.shadowed(g, s.split, stepX + 4, cy + (ch + small.getAscent()) / 2 - 1, s.splitColor);
			g.setFont(Theme.title());
			stepX += chip + 8;
		}
		Ui.shadowed(g, s.stepShown, stepX, base, Theme.TEXT);
		g.setFont(Theme.small());
		for (int i = 0; i < lines.length; i++)
		{
			Ui.shadowed(g, lines[i], textX, base + (i + 1) * (small.getHeight() + 1) + 1, Theme.TEXT_MUTED);
		}
		int bar = (int) Math.round((width - 20) * Math.max(0, Math.min(1, s.progress)));
		g.setColor(TRACK);
		g.fillRect(10, height - 3, width - 20, 2);
		g.setColor(s.color);
		g.fillRect(10, height - 3, bar, 2);
		return new Dimension(width, height);
	}
}
