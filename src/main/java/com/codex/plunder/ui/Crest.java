package com.codex.plunder.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * The crest at the top of the panel: the Pilot's medallion, one figure in large gilt numerals
 * (the clock during a run, the deepest room before one), and a line saying what it means.
 * During a run a thin bar under it drains with the clock.
 */
public class Crest extends JComponent
{
	private static final int HEIGHT = 104;

	private final Animator bar = new Animator(this);
	private String heading = "PYRAMID PLUNDER";
	private String figure;
	private String unit;
	private String status = "Log in to plan your runs";
	private Color statusColor = Theme.TEXT_MUTED;
	private Color figureTint = Theme.GOLD_DIM;
	private double progress = -1;
	/** Draws the Sepulchre's coffin in the medallion instead of the pyramid. */
	private boolean coffin;

	/** A crest with the Sepulchre's coffin in its medallion. */
	public static Crest sepulchre()
	{
		Crest c = new Crest();
		c.coffin = true;
		return c;
	}

	public Crest()
	{
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setMinimumSize(new Dimension(10, HEIGHT));
	}

	/**
	 * @param figure the large figure, such as "3:12" or "8"
	 * @param unit a small word after the figure, or null
	 * @param progress how much of the bar is lit, 0 to 1, or below 0 for no bar
	 */
	public void update(String heading, String figure, String unit, String status, Color statusColor, Color tint, double progress)
	{
		this.heading = heading;
		this.figure = figure;
		this.unit = unit;
		this.status = status;
		this.statusColor = statusColor;
		this.figureTint = tint;
		boolean had = this.progress >= 0;
		this.progress = progress;
		if (progress >= 0)
		{
			bar.set(progress, had);
		}
		repaint();
	}

	/**
	 * Draws text at twice the size of the game's bold font by doubling every pixel, the way the
	 * game itself draws large numbers, so the figures stay crisp instead of being re-rasterised.
	 *
	 * @return the width drawn
	 */
	static int paintLarge(Graphics2D g, String text, int x, int y, Color tint)
	{
		Font font = Theme.title();
		FontMetrics fm = g.getFontMetrics(font);
		int w = Math.max(1, fm.stringWidth(text) + 1);
		int h = fm.getHeight() + 1;
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = small.createGraphics();
		sg.setFont(font);
		sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		int base = fm.getAscent();
		sg.setColor(Color.BLACK);
		sg.drawString(text, 1, base + 1);
		sg.setPaint(new GradientPaint(0, base - fm.getAscent() + 3, Theme.mix(tint, Color.WHITE, 0.35), 0, base, tint));
		sg.drawString(text, 0, base);
		sg.dispose();
		Graphics2D lg = (Graphics2D) g.create();
		lg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		lg.drawImage(small, x, y - 2 * fm.getAscent() + 22, w * 2, h * 2, null);
		lg.dispose();
		return w * 2;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, Theme.GOLD_DIM, 0.22), 0, h, Theme.INK));
		g.fillRect(0, 0, w, h);

		// The medallion: a gilt ring with the mark inside, lit from above.
		int mx = 10;
		int my = 14;
		int md = 54;
		g.setPaint(new RadialGradientPaint(mx + md / 2f, my + md / 3f, md * 0.7f, new float[]{0, 1},
			new Color[]{Theme.mix(Theme.CARD_TOP, Theme.GOLD_DIM, 0.35), Theme.INK_DEEP}));
		g.fill(new Ellipse2D.Float(mx, my, md, md));
		g.setStroke(new BasicStroke(2f));
		g.setPaint(new GradientPaint(mx, my, Theme.GOLD_BRIGHT, mx, my + md, Theme.GOLD_DIM));
		g.draw(new Ellipse2D.Float(mx + 1, my + 1, md - 2, md - 2));
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(Theme.GOLD, 70));
		g.draw(new Ellipse2D.Float(mx + 5, my + 5, md - 10, md - 10));
		if (coffin)
		{
			SepulchreSigil.paint(g, mx + 11, my + 11, md - 22);
		}
		else
		{
			Sigil.paint(g, mx + 11, my + 11, md - 22);
		}

		int tx = mx + md + 12;
		int room = w - tx - 8;
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(Ui.clip(heading, small, room), tx, 22);

		int top = 27;
		int cy = top + 50;
		if (figure == null)
		{
			// Nothing to count yet: the status takes the figure's place rather than a lone dash.
			g.setFont(Theme.body());
			FontMetrics body = g.getFontMetrics();
			Ui.shadowed(g, Ui.clip(status == null ? "" : status, body, room), tx, top + 18, statusColor);
			g.setFont(Theme.small());
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(Ui.clip(unit == null ? "" : unit, small, room), tx, top + 34);
		}
		else
		{
			int figureW = paintLarge(g, figure, tx, top, figureTint);
			if (unit != null)
			{
				g.setFont(Theme.small());
				g.setColor(Theme.GOLD_DIM);
				g.drawString(unit, tx + figureW + 3, top + 18);
			}
		}
		g.setFont(Theme.small());
		if (status != null && figure != null)
		{
			Ui.shadowed(g, Ui.clip(status, small, room), tx, cy, statusColor);
		}
		if (progress >= 0)
		{
			int by = cy + 8;
			int bw = room;
			g.setColor(Theme.INK_DEEP);
			g.fillRoundRect(tx, by, bw, 4, 4, 4);
			int lit = (int) Math.round(bw * Math.max(0, Math.min(1, bar.get())));
			if (lit > 0)
			{
				g.setPaint(new GradientPaint(tx, 0, Theme.mix(figureTint, Theme.INK, 0.35), tx + bw, 0, figureTint));
				g.fillRoundRect(tx, by, lit, 4, 4, 4);
			}
		}

		// The gilt rule under the crest, with a diamond at its middle.
		int y = h - 2;
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.GOLD, 0), w / 2f, 0, Theme.alpha(Theme.GOLD, 150), true));
		g.drawLine(0, y, w, y);
		Path2D diamond = new Path2D.Float();
		diamond.moveTo(w / 2f, y - 3);
		diamond.lineTo(w / 2f + 3, y);
		diamond.lineTo(w / 2f, y + 3);
		diamond.lineTo(w / 2f - 3, y);
		diamond.closePath();
		g.setColor(Theme.GOLD);
		g.fill(diamond);
		g.dispose();
	}
}
