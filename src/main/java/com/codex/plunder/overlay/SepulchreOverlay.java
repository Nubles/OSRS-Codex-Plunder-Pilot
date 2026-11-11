package com.codex.plunder.overlay;

import com.codex.plunder.sepulchre.FloorSplits;
import com.codex.plunder.ui.FloorTable;
import com.codex.plunder.ui.Theme;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The Hallowed Sepulchre floor splits in the game view, for players who keep the panel shut: each
 * floor against the ghost and how far ahead or behind the run is. Off unless turned on in the
 * settings. It draws what the plugin hands it, and nothing between runs.
 */
public class SepulchreOverlay extends Overlay
{
	private static final int PAD = 7;
	private static final int WIDTH = 176;

	private volatile FloorSplits shown;

	public SepulchreOverlay()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/** @param table the table to draw, or null to hide it */
	public void show(FloorSplits table)
	{
		shown = table;
	}

	public FloorSplits getShown()
	{
		return shown;
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
		FloorSplits s = shown;
		if (s == null)
		{
			return null;
		}
		Theme.smooth(g);
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		int title = fm.getHeight() + 2;
		int w = WIDTH + PAD * 2;
		int h = PAD + title + FloorTable.height(s) + PAD - 2;
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 232), 0, h, Theme.alpha(Theme.INK_DEEP, 232)));
		g.fill(shape);
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 170));
		g.draw(shape);
		g.setColor(Theme.GOLD);
		g.drawString("FLOOR SPLITS", PAD, PAD + fm.getAscent());
		Graphics2D t = (Graphics2D) g.create();
		t.translate(PAD, PAD + title);
		FloorTable.paint(t, s, WIDTH);
		t.dispose();
		return new Dimension(w, h);
	}
}
