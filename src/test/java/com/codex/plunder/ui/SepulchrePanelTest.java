package com.codex.plunder.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.codex.plunder.overlay.SepulchreOverlay;
import com.codex.plunder.sepulchre.FloorSplits;
import com.codex.plunder.sepulchre.Reward;
import com.codex.plunder.sepulchre.SepulchreLog;
import com.codex.plunder.sepulchre.SepulchreRun;
import com.codex.plunder.sepulchre.SepulchreTracker;
import com.codex.plunder.sepulchre.Unique;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.Test;

/**
 * The Sepulchre panel builds every page for every state without throwing, says the right things,
 * and, when CODEX_SHOTS names a folder, paints each page there as a picture to look over.
 */
public class SepulchrePanelTest
{
	private static final long NOW = 1_767_225_600_000L;
	private static final int SCALE = 2;

	/** A run that finishes floors in the given game times, looting coffins giving these marks. */
	static SepulchreRun run(long at, int[] floorMs, int[][] coffins, boolean grand)
	{
		SepulchreTracker t = new SepulchreTracker();
		t.begin(at, 0);
		int tick = 0;
		for (int f = 1; f <= floorMs.length; f++)
		{
			for (int m : f <= coffins.length ? coffins[f - 1] : new int[0])
			{
				t.marks(m);
			}
			tick += floorMs[f - 1] / 600 + 3;
			t.floorDone(f, tick);
			t.floorTime(f, floorMs[f - 1], -1);
			t.agility(new int[]{575, 925, 1_600, 2_875, 5_725}[f - 1]);
		}
		if (grand)
		{
			t.marks(9);
			t.grandOpened();
		}
		return t.finish(tick + 20);
	}

	private static SepulchreLog log(int runs)
	{
		SepulchreLog log = new SepulchreLog();
		for (int i = 0; i < runs; i++)
		{
			SepulchreRun r = run(NOW - (runs - i) * 520_000L, new int[]{52_000 + i * 100, 71_000, 96_000, 118_000, 151_000},
				new int[][]{{1}, {2, 3}, {4, 3, 5}, {5, 4}, {6, 5, 4}}, i % 3 != 1);
			log.offerGhost(r);
			log.record(r);
		}
		log.markGot(Unique.PAGE_1);
		log.markGot(Unique.PAGE_2);
		log.setBankMarks(1_120);
		return log;
	}

	private static SepulchreView view(SepulchreLog log)
	{
		SepulchreView v = new SepulchreView(SepulchreView.State.READY, "Zezima", 91, 78, NOW);
		v.runs = log.getRuns();
		v.totalRuns = log.getTotalRuns();
		v.totalMarks = log.getTotalMarks();
		v.grands = log.getGrands();
		v.coffins = log.getCoffins();
		v.pace = log.pace();
		v.packMarks = 37;
		v.bankMarks = log.getBankMarks();
		v.target = Reward.DARK_GRACEFUL;
		EnumSet<Unique> got = EnumSet.noneOf(Unique.class);
		for (Unique u : Unique.values())
		{
			if (log.has(u))
			{
				got.add(u);
			}
		}
		v.got = got;
		return v;
	}

	/** Floor 3 under way, ahead on floor 1 and behind on floor 2. */
	private static SepulchreView live(SepulchreLog log)
	{
		SepulchreView v = view(log);
		SepulchreTracker t = new SepulchreTracker();
		t.begin(NOW, 0);
		t.marks(1);
		t.floorDone(1, 85);
		t.floorTime(1, 50_400, -1);
		t.agility(575);
		t.marks(3);
		t.marks(2);
		t.floorDone(2, 210);
		t.floorTime(2, 74_200, -1);
		t.agility(925);
		t.marks(4);
		v.splits = FloorSplits.of(t.snapshot(380), log.liveGhost(5), t.getFloor(), t.onFloor(380) * 600);
		return v;
	}

	private static SepulchreView last(SepulchreLog log)
	{
		SepulchreView v = view(log);
		SepulchreRun ghost = log.ghost(5);
		SepulchreRun run = run(NOW - 90_000, new int[]{49_800, 70_400, 93_000, 120_200, 148_600},
			new int[][]{{1}, {3, 2}, {5, 4, 3}, {6, 3}, {6, 6, 4}}, true);
		v.splits = FloorSplits.of(run, ghost, 0, 0);
		v.newGhost = log.offerGhost(run);
		return v;
	}

	@Test
	public void everyPageBuildsInEveryState()
	{
		SepulchrePanel panel = new SepulchrePanel();
		SepulchreLog log = log(14);
		List<SepulchreView> views = new ArrayList<>();
		views.add(SepulchreView.loggedOut());
		views.add(view(new SepulchreLog()));
		views.add(view(log));
		views.add(live(log));
		views.add(last(log));
		for (SepulchreView v : views)
		{
			panel.update(v);
			for (int i = 0; i < 3; i++)
			{
				assertTrue(panel.page(i).getPreferredSize().height > 50);
			}
		}
	}

	@Test
	public void theLastRunSaysHowMuchFasterTheNewGhostIs()
	{
		SepulchrePanel panel = new SepulchrePanel();
		SepulchreView v = last(log(14));
		assertTrue(v.newGhost);
		panel.update(v);
		String text = text(panel.page(0));
		assertTrue(text, text.contains("New ghost, 6.0s faster"));
		assertTrue(text, text.contains("Floor 5 in 8:02.0"));
	}

	@Test
	public void theMarksPageCountsDownToTheRewardAndLeavesFoundUniquesFound()
	{
		SepulchrePanel panel = new SepulchrePanel();
		SepulchreView v = view(log(14));
		panel.update(v);
		String text = text(panel.page(1));
		// 1,800 for six dark dyes, 1,157 held: 643 to go.
		assertTrue(text, text.contains("643 marks"));
		assertTrue(text, text.contains("Mysterious page 1"));
		assertTrue(text, text.contains("found"));
		assertTrue(text, text.contains("Ring of endurance"));
		assertEquals(1_157, v.held());
	}

	@Test
	public void timesAndGapsReadAsTheGameWritesThem()
	{
		assertEquals("1:23.4", FloorTable.time(83_400));
		assertEquals("0:05.0", FloorTable.time(5_000));
		assertEquals("-", FloorTable.time(-1));
		assertEquals("+3.2s", FloorTable.delta(3_200));
		assertEquals("-1:04", FloorTable.delta(-64_000));
		assertEquals("even", FloorTable.delta(40));
	}

	@Test
	public void picturesForReview() throws Exception
	{
		String dir = System.getenv("CODEX_SHOTS");
		if (dir == null || dir.isEmpty())
		{
			return;
		}
		SepulchreLog log = log(14);
		SepulchrePanel panel = new SepulchrePanel();
		panel.update(live(log));
		shot(panel, 0, dir + "/sepulchre-run-live.png");
		panel.update(last(log(14)));
		shot(panel, 0, dir + "/sepulchre-last-run.png");
		panel.update(view(log));
		shot(panel, 1, dir + "/sepulchre-marks.png");
		shot(panel, 2, dir + "/sepulchre-log.png");
		panel.update(view(new SepulchreLog()));
		shot(panel, 0, dir + "/sepulchre-first-look.png");

		SepulchreOverlay overlay = new SepulchreOverlay();
		overlay.show(live(log).splits);
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D pg = probe.createGraphics();
		Dimension d = overlay.render(pg);
		pg.dispose();
		BufferedImage img = new BufferedImage((d.width + 24) * SCALE, (d.height + 24) * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.scale(SCALE, SCALE);
		g.setColor(new Color(62, 74, 58));
		g.fillRect(0, 0, d.width + 24, d.height + 24);
		g.translate(12, 12);
		overlay.render(g);
		g.dispose();
		ImageIO.write(img, "png", new java.io.File(dir + "/sepulchre-overlay.png"));
	}

	private static void shot(SepulchrePanel panel, int tab, String file) throws Exception
	{
		JComponent page = panel.page(tab);
		int w = 225;
		panel.setSize(w, 2000);
		layout(panel);
		int north = panel.getComponent(0).getHeight();
		int h = north + page.getPreferredSize().height + 4;
		panel.setSize(w, h);
		layout(panel);
		BufferedImage img = new BufferedImage(w * SCALE, h * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.scale(SCALE, SCALE);
		panel.printAll(g);
		g.dispose();
		ImageIO.write(img, "png", new java.io.File(file));
	}

	private static void layout(Component c)
	{
		c.doLayout();
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				layout(child);
			}
		}
	}

	private static String text(Component c)
	{
		StringBuilder out = new StringBuilder();
		if (c instanceof JLabel)
		{
			out.append(((JLabel) c).getText()).append('\n');
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				out.append(text(child));
			}
		}
		return out.toString();
	}
}
