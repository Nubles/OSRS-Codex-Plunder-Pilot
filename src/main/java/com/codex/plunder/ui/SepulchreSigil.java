package com.codex.plunder.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** The Sepulchre panel's mark: a gilt coffin lid with a teal hallowed mark set in it. */
public final class SepulchreSigil
{
	private SepulchreSigil()
	{
	}

	public static BufferedImage icon(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Theme.smooth(g);
		paint(g, 0, 0, size);
		g.dispose();
		return image;
	}

	/** Draws the mark in a size by size box. */
	public static void paint(Graphics2D g, float x, float y, float size)
	{
		float s = size / 16f;
		Path2D lid = new Path2D.Float();
		lid.moveTo(x + 5.6f * s, y + 0.6f * s);
		lid.lineTo(x + 10.4f * s, y + 0.6f * s);
		lid.lineTo(x + 13.4f * s, y + 4.6f * s);
		lid.lineTo(x + 10.8f * s, y + 15.4f * s);
		lid.lineTo(x + 5.2f * s, y + 15.4f * s);
		lid.lineTo(x + 2.6f * s, y + 4.6f * s);
		lid.closePath();
		g.setPaint(new GradientPaint(x, y, Theme.GOLD_BRIGHT, x + 12 * s, y + 16 * s, Theme.GOLD_DIM));
		g.fill(lid);
		g.setStroke(new BasicStroke(Math.max(0.7f, 0.6f * s)));
		g.setColor(Theme.INK_DEEP);
		g.draw(lid);

		// The bands across the lid.
		g.setColor(Theme.alpha(Theme.INK_DEEP, 130));
		g.setStroke(new BasicStroke(Math.max(0.5f, 0.45f * s)));
		g.drawLine(Math.round(x + 3.4f * s), Math.round(y + 4.6f * s), Math.round(x + 12.6f * s), Math.round(y + 4.6f * s));
		g.drawLine(Math.round(x + 4.4f * s), Math.round(y + 12.2f * s), Math.round(x + 11.6f * s), Math.round(y + 12.2f * s));

		// The mark: a teal jewel in the heart of the lid.
		float cx = x + 8f * s;
		float cy = y + 8.2f * s;
		float r = 2.3f * s;
		Ellipse2D jewel = new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2);
		g.setColor(Theme.INK_DEEP);
		g.setStroke(new BasicStroke(Math.max(1.1f, 1.1f * s)));
		g.draw(jewel);
		g.setPaint(new GradientPaint(cx, cy - r, Theme.mix(Theme.TEAL, Theme.TEXT, 0.4f), cx, cy + r, Theme.mix(Theme.TEAL, Theme.INK_DEEP, 0.3f)));
		g.fill(jewel);
		g.setColor(new Color(255, 255, 255, 150));
		g.fill(new Ellipse2D.Float(cx - r * 0.55f, cy - r * 0.6f, r * 0.6f, r * 0.6f));
	}
}
