package com.codex.plunder.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.codex.plunder.core.Goal;
import com.codex.plunder.core.Logbook;
import com.codex.plunder.core.Pace;
import com.codex.plunder.core.Room;
import com.codex.plunder.core.RunTracker;
import com.codex.plunder.sepulchre.FloorSplits;
import com.codex.plunder.sepulchre.SepulchreTracker;
import com.codex.plunder.ui.Card;
import com.codex.plunder.ui.GhostTable;
import com.codex.plunder.ui.Theme;
import com.codex.plunder.ui.View;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.function.Function;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import org.junit.Test;

/**
 * Every overlay leaves the graphics as it found them: the clip, transform, font, colour, stroke and
 * rendering hints. RuneLite draws every overlay with one graphics, so a clip cleared or a transform
 * left behind here would break the overlays drawn after, every frame.
 */
public class GraphicsStateTest
{
	private static final Rectangle CLIP = new Rectangle(7, 9, 300, 200);

	/** Renders the overlay into a clipped graphics and checks nothing set on it is left changed. */
	private static void leavesStateAlone(Overlay overlay)
	{
		BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setClip(CLIP);
		g.translate(3, 4);
		Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
		g.setFont(font);
		g.setColor(Color.MAGENTA);
		Stroke stroke = new BasicStroke(3f);
		g.setStroke(stroke);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		Shape clip = g.getClip();
		AffineTransform transform = g.getTransform();
		RenderingHints hints = (RenderingHints) g.getRenderingHints().clone();

		overlay.render(g);

		assertNotNull("the clip was cleared", g.getClip());
		assertEquals(clip.getBounds(), g.getClip().getBounds());
		assertEquals(transform, g.getTransform());
		assertEquals(font, g.getFont());
		assertEquals(Color.MAGENTA, g.getColor());
		assertEquals(stroke, g.getStroke());
		assertEquals(hints, g.getRenderingHints());
		g.dispose();
	}

	@Test
	public void theRunPlaqueLeavesTheGraphicsAlone()
	{
		PilotPlaque plaque = new PilotPlaque();
		plaque.show("R7  2:41", "Search 9 urns", "675 xp each, then the chest", Theme.GOLD, 0.5);
		leavesStateAlone(plaque);
	}

	@Test
	public void theRoomOutlinesLeaveTheGraphicsAlone()
	{
		TileObject urn = mock(TileObject.class);
		when(urn.getClickbox()).thenReturn(new Rectangle(40, 40, 30, 24));
		RoomHighlights highlights = new RoomHighlights();
		highlights.show(Collections.singletonList(new RoomHighlights.Mark(urn, Theme.GOLD, true, "1")));
		leavesStateAlone(highlights);
	}

	@Test
	public void theGhostSplitsLeaveTheGraphicsAlone()
	{
		Pace pace = new Logbook().getPace();
		RunTracker t = new RunTracker();
		t.begin(0, 0, Goal.EXPERIENCE);
		t.room(Room.ONE, 20, pace);
		t.room(Room.TWO, 60, pace);
		GhostOverlay overlay = new GhostOverlay();
		overlay.show(new GhostTable.Slim(new View.GhostSplits(t.snapshot(80), null, true)));
		leavesStateAlone(overlay);
	}

	@Test
	public void theFloorSplitsLeaveTheGraphicsAlone()
	{
		SepulchreTracker t = new SepulchreTracker();
		t.begin(0, 0);
		t.floorDone(1, 90);
		t.floorTime(1, 54_000, -1);
		SepulchreOverlay overlay = new SepulchreOverlay();
		overlay.show(FloorSplits.of(t.snapshot(120), null, t.getFloor(), t.onFloor(120) * 600));
		leavesStateAlone(overlay);
	}

	/** A card's accent stripe is clipped to the card, and never paints past the clip it was given. */
	@Test
	public void aCardKeepsToTheClipItIsGiven()
	{
		Card card = new Card(new java.awt.BorderLayout());
		card.accent(Theme.GOLD);
		card.setSize(120, 60);
		BufferedImage img = new BufferedImage(120, 60, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		Rectangle clip = new Rectangle(20, 0, 100, 60);
		g.setClip(clip);
		card.paint(g);
		assertEquals(clip, g.getClip().getBounds());
		g.dispose();
		Function<Integer, Integer> alphaAt = x -> img.getRGB(x, 30) >>> 24;
		for (int x = 0; x < 20; x++)
		{
			assertEquals("painted outside the clip at x=" + x, 0, (int) alphaAt.apply(x));
		}
		Dimension size = card.getSize();
		assertEquals(120, size.width);
	}
}
