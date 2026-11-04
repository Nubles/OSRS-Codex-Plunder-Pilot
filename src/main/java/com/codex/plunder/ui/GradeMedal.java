package com.codex.plunder.ui;

import com.codex.plunder.core.Goal;
import com.codex.plunder.core.Grade;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * The head of the grade card: the letter struck on a gilt medallion, and beside it what the run
 * earned an hour against the ghost, and its loot an hour against the ghost's.
 */
class GradeMedal extends JComponent
{
	static final int HEIGHT = 62;
	private static final int MEDAL = 54;

	private final Grade grade;

	GradeMedal(Grade grade)
	{
		this.grade = grade;
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
	}

	/** The letter's colour: S bright gold, A teal, B dull gold, C ember; no letter is faint. */
	static Color tint(Grade.Letter letter)
	{
		if (letter == null)
		{
			return Theme.TEAL;
		}
		switch (letter)
		{
			case S:
				return Theme.GOLD_BRIGHT;
			case A:
				return Theme.TEAL;
			case B:
				return Theme.GOLD;
			default:
				return Theme.EMBER;
		}
	}

	/** A rate in its goal's words: "62k xp/hr", or "1 in 140/hr" for the sceptre. */
	static String rate(Goal goal, double rate)
	{
		if (goal == Goal.SCEPTRE)
		{
			return rate <= 0 ? "no odds" : Format.oneIn(Math.min(1, rate)) + "/hr";
		}
		return Format.amount(Math.round(rate)) + " xp/hr";
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		Color tint = tint(grade.getLetter());
		int cy = HEIGHT / 2;

		// The medallion: a dark disc in a gilt rim, lit from above.
		Ellipse2D disc = new Ellipse2D.Float(1.5f, cy - MEDAL / 2f, MEDAL, MEDAL);
		g.setPaint(new GradientPaint(0, cy - MEDAL / 2f, Theme.CARD_TOP, 0, cy + MEDAL / 2f, Theme.INK_DEEP));
		g.fill(disc);
		g.setStroke(new BasicStroke(2f));
		g.setPaint(new GradientPaint(0, cy - MEDAL / 2f, Theme.mix(tint, Color.WHITE, 0.25), 0, cy + MEDAL / 2f, Theme.alpha(tint, 120)));
		g.draw(disc);
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(tint, 60));
		g.draw(new Ellipse2D.Float(6.5f, cy - MEDAL / 2f + 5, MEDAL - 10, MEDAL - 10));
		if (grade.getLetter() == null)
		{
			// No letter for the first run of its kind: it is the first ghost.
			paintPixels(g, "1st", 1.5f + MEDAL / 2f, cy, 2, tint);
		}
		else
		{
			paintPixels(g, grade.getLetter().name(), 1.5f + MEDAL / 2f, cy, 3, tint);
		}

		int x = MEDAL + 12;
		int w = getWidth() - x;
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();

		int y = 6 + small.getAscent();
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_FAINT);
		String head = grade.getLetter() == null ? "FIRST RUN TO ROOM " + grade.getDeepest()
			: grade.isNewGhost() ? "ROOM " + grade.getDeepest() : "GRADE  -  ROOM " + grade.getDeepest();
		g.drawString(head, x, y);
		if (grade.isNewGhost() && grade.getLetter() != null)
		{
			String chip = "NEW GHOST";
			int cw = small.stringWidth(chip) + 8;
			int cx = getWidth() - cw - 1;
			if (cx > x + small.stringWidth(head) + 4)
			{
				g.setColor(Theme.alpha(Theme.TEAL, 40));
				g.fillRoundRect(cx, y - small.getAscent() - 2, cw, small.getHeight() + 2, 6, 6);
				g.setColor(Theme.alpha(Theme.TEAL, 150));
				g.drawRoundRect(cx, y - small.getAscent() - 2, cw, small.getHeight() + 2, 6, 6);
				Ui.shadowed(g, chip, cx + 4, y, Theme.TEAL);
			}
		}

		y += bold.getHeight() + 1;
		g.setFont(Theme.title());
		Ui.shadowed(g, Ui.clip(rate(grade.getGoal(), grade.getRate()), bold, w), x, y, tint);

		g.setFont(Theme.small());
		y += small.getHeight() + 3;
		String vs;
		if (grade.getLetter() == null)
		{
			vs = "now your ghost to beat";
		}
		else
		{
			vs = "ghost " + rate(grade.getGoal(), grade.getGhostRate()) + ", " + Format.percent(grade.share());
		}
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(Ui.clip(vs, small, w), x, y);
		y += small.getHeight() + 1;
		String loot = "loot " + Format.amount(Math.round(grade.getLootRate())) + "/hr";
		if (grade.getLetter() != null)
		{
			loot += ", ghost " + Format.amount(Math.round(grade.getGhostLootRate()));
		}
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(Ui.clip(loot, small, w), x, y);
		g.dispose();
	}

	/**
	 * Draws text centred on a point at a whole multiple of the game's bold font, every pixel
	 * enlarged as the game draws big numbers, so the letter stays crisp.
	 */
	static void paintPixels(Graphics2D g, String text, float cx, float cy, int scale, Color tint)
	{
		Font font = Theme.title();
		FontMetrics fm = g.getFontMetrics(font);
		int w = Math.max(1, fm.stringWidth(text) + 1);
		int h = fm.getAscent() + 2;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = img.createGraphics();
		sg.setFont(font);
		sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		int base = fm.getAscent();
		sg.setColor(Color.BLACK);
		sg.drawString(text, 1, base + 1);
		sg.setPaint(new GradientPaint(0, 2, Theme.mix(tint, Color.WHITE, 0.4), 0, base, tint));
		sg.drawString(text, 0, base);
		sg.dispose();
		// The cap height, not the font's box, is what is centred: the bold font's letters sit low in it.
		int top = topInk(img);
		int bottom = h;
		Graphics2D lg = (Graphics2D) g.create();
		lg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		int dw = w * scale;
		int dh = h * scale;
		int inkMid = (top + bottom) * scale / 2;
		lg.drawImage(img, Math.round(cx - dw / 2f), Math.round(cy - inkMid), dw, dh, null);
		lg.dispose();
	}

	private static int topInk(BufferedImage img)
	{
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 0; x < img.getWidth(); x++)
			{
				if ((img.getRGB(x, y) >>> 24) != 0)
				{
					return y;
				}
			}
		}
		return 0;
	}
}
