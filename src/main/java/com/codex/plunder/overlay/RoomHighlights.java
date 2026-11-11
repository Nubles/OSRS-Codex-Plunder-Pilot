package com.codex.plunder.overlay;

import com.codex.plunder.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Rectangle;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Collections;
import java.util.List;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Outlines in the room what the plan says to do next and nothing else: the urns worth searching
 * in gold, the chest and sarcophagus in teal when the sceptre odds make them worth it, the spear
 * trap and the untried tomb doors when it is time to move on. Urns the plan skips stay dark.
 */
public class RoomHighlights extends Overlay
{
	/** One object to outline, and in what colour. */
	public static final class Mark
	{
		final TileObject object;
		final Color color;
		final boolean clickbox;
		final String label;

		public Mark(TileObject object, Color color, boolean clickbox)
		{
			this(object, color, clickbox, null);
		}

		/** @param label a short tag drawn on the object, such as its place in the walking order */
		public Mark(TileObject object, Color color, boolean clickbox, String label)
		{
			this.object = object;
			this.color = color;
			this.clickbox = clickbox;
			this.label = label;
		}
	}

	private volatile List<Mark> marks = Collections.emptyList();

	public RoomHighlights()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	public void show(List<Mark> marks)
	{
		this.marks = marks == null ? Collections.emptyList() : marks;
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
		List<Mark> list = marks;
		if (list.isEmpty())
		{
			return null;
		}
		g.setStroke(new BasicStroke(1.6f));
		for (Mark m : list)
		{
			Shape shape = m.clickbox ? m.object.getClickbox() : hull(m.object);
			if (shape == null)
			{
				continue;
			}
			g.setColor(Theme.alpha(m.color, 44));
			g.fill(shape);
			g.setColor(Theme.alpha(m.color, 220));
			g.draw(shape);
			if (m.label != null)
			{
				Rectangle box = shape.getBounds();
				seal(g, m.label, (int) box.getCenterX(), (int) box.getCenterY(), m.color);
			}
		}
		return null;
	}

	/** A small gilt seal with the number in it, centred on a point. */
	static void seal(Graphics2D g, String text, int cx, int cy, Color color)
	{
		Theme.smooth(g);
		g.setFont(Theme.title());
		FontMetrics fm = g.getFontMetrics();
		int d = Math.max(16, fm.stringWidth(text) + 8);
		int x = cx - d / 2;
		int y = cy - d / 2;
		g.setPaint(new GradientPaint(0, y, Theme.mix(color, Color.WHITE, 0.25), 0, y + d, Theme.mix(color, Theme.INK_DEEP, 0.45)));
		g.fillOval(x, y, d, d);
		g.setColor(Theme.INK_DEEP);
		g.drawOval(x, y, d, d);
		g.drawString(text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2 - 2);
	}

	private static Shape hull(TileObject object)
	{
		if (object instanceof net.runelite.api.GameObject)
		{
			return ((net.runelite.api.GameObject) object).getConvexHull();
		}
		return object.getClickbox();
	}
}
