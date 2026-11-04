package com.codex.plunder.ui;

import com.codex.plunder.sepulchre.Floor;
import com.codex.plunder.sepulchre.FloorSplits;
import com.codex.plunder.sepulchre.Reward;
import com.codex.plunder.sepulchre.SepulchrePace;
import com.codex.plunder.sepulchre.SepulchreRun;
import com.codex.plunder.sepulchre.Unique;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/**
 * The Hallowed Sepulchre side panel: the crest with the gap to the ghost during a run or the marks
 * in hand between runs, then three pages. Run is the floor splits against the ghost and the floors
 * the Agility level opens, Marks is the pace, the reward being saved for and the odds of each
 * unique still to find, and Log is every run so far. Information only: it never says what to click.
 */
public class SepulchrePanel extends PluginPanel
{
	static final int WIDTH = PilotPanel.WIDTH;
	private static final String[] TABS = {"run", "marks", "log"};
	private static final int RECENT = 10;

	private final Crest crest = Crest.sepulchre();
	private final Segmented tabs = new Segmented(true, "Run", "Marks", "Log");
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane[] pages = {scroller(), scroller(), scroller()};
	private final boolean[] stale = {true, true, true};

	private SepulchreView view = SepulchreView.loggedOut();

	public SepulchrePanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(Theme.INK);
		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(crest, BorderLayout.NORTH);
		JPanel tabHolder = new JPanel(new BorderLayout());
		tabHolder.setOpaque(false);
		tabHolder.setBorder(new EmptyBorder(8, 8, 2, 8));
		tabHolder.add(tabs, BorderLayout.CENTER);
		north.add(tabHolder, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);
		content.setOpaque(false);
		for (int i = 0; i < TABS.length; i++)
		{
			content.add(pages[i], TABS[i]);
		}
		add(content, BorderLayout.CENTER);
		tabs.onChange(this::showTab);
		update(view);
	}

	private static JScrollPane scroller()
	{
		JScrollPane scroll = new JScrollPane();
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		return scroll;
	}

	/** Called on the Swing thread whenever anything shown changes. */
	public void update(SepulchreView view)
	{
		this.view = view;
		updateCrest();
		java.util.Arrays.fill(stale, true);
		refresh(false);
	}

	public void showTab(int index)
	{
		tabs.setSelected(index, false);
		cards.show(content, TABS[index]);
		refresh(true);
	}

	@Override
	public void onActivate()
	{
		refresh(true);
	}

	private void refresh(boolean force)
	{
		if (!force && !isShowing())
		{
			return;
		}
		int page = tabs.getSelected();
		if (!stale[page])
		{
			return;
		}
		stale[page] = false;
		StackPanel stack = new StackPanel(8, 8);
		if (view.state == SepulchreView.State.LOGGED_OUT)
		{
			stack.add(PilotPanel.note("The crypt is quiet", "Log in and every Hallowed Sepulchre run is timed floor by floor against your best, with the marks it found, your pace, and the odds of the uniques you have yet to find."));
		}
		else if (page == 0)
		{
			buildRun(stack);
		}
		else if (page == 1)
		{
			buildMarks(stack);
		}
		else
		{
			buildLog(stack);
		}
		replace(pages[page], stack);
	}

	/** The page in view, built now, and its full height: for a picture of the panel. */
	JComponent page(int index)
	{
		showTab(index);
		return (JComponent) pages[index].getViewport().getView();
	}

	private void updateCrest()
	{
		if (view.state == SepulchreView.State.LOGGED_OUT)
		{
			crest.update("HALLOWED SEPULCHRE", null, null, "Log in to time your runs", Theme.mix(Theme.TEXT_MUTED, Theme.GOLD, 0.45), Theme.GOLD_DIM, -1);
			return;
		}
		int open = Floor.open(view.agility);
		if (view.inRun())
		{
			FloorSplits s = view.splits;
			SepulchreRun run = s.getRun();
			Integer total = s.total();
			String figure;
			String unit;
			Color tint;
			if (s.getGhost() == null || total == null)
			{
				FloorSplits.Row row = liveRow(s);
				figure = FloorTable.time(row == null ? 0 : row.getMine());
				unit = "floor " + s.getLiveFloor();
				tint = Theme.GOLD;
			}
			else
			{
				figure = FloorTable.delta(total);
				unit = total < 0 ? "ahead" : "behind";
				tint = FloorTable.deltaColor(total);
			}
			String status = "+" + run.getMarks() + " marks from " + run.getCoffins() + (run.getCoffins() == 1 ? " coffin" : " coffins")
				+ (run.isGrand() ? " and the grand" : "");
			crest.update("FLOOR " + s.getLiveFloor() + " OF " + Math.max(open, s.getLiveFloor()), figure, unit, status,
				Theme.mix(Theme.TEXT_MUTED, Theme.GOLD, 0.45), tint, Math.min(1, run.getDeepest() / (double) Math.max(1, open)));
			return;
		}
		String heading = "AGILITY " + view.agility + "  -  " + (open == 0 ? "NO FLOORS" : open == 1 ? "1 FLOOR" : open + " FLOORS");
		long need = view.target.getCost() - view.held();
		String status;
		if (need <= 0)
		{
			status = "Enough for " + view.target.getLabel().toLowerCase(Locale.ENGLISH);
		}
		else if (view.pace != null && view.pace.runsFor(need) > 0)
		{
			status = view.target.getLabel() + ": " + plural(view.pace.runsFor(need), "run");
		}
		else
		{
			status = amount(need) + " to " + view.target.getLabel().toLowerCase(Locale.ENGLISH);
		}
		crest.update(heading, amount(view.held()), "marks", status, Theme.mix(Theme.TEXT_MUTED, Theme.GOLD, 0.35), Theme.GOLD, -1);
	}

	private static FloorSplits.Row liveRow(FloorSplits s)
	{
		for (FloorSplits.Row r : s.getRows())
		{
			if (r.isLive())
			{
				return r;
			}
		}
		return null;
	}

	// The Run page.

	private void buildRun(StackPanel stack)
	{
		FloorSplits s = view.splits;
		if (view.inRun())
		{
			SepulchreRun run = s.getRun();
			JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
			tiles.setOpaque(false);
			tiles.add(new StatTile("+" + run.getMarks(), "Marks", plural(run.getCoffins(), "coffin") + (run.isGrand() ? " + grand" : ""), Theme.GOLD));
			tiles.add(new StatTile("+" + Format.amount(run.getAgilityXp()), "Agility", "xp this run", Theme.TEAL));
			stack.add(tiles);
			stack.add(new SectionHeader("Floor splits", s.getGhost() == null ? "no ghost yet" : "vs your ghost"));
			Card card = new Card(new BorderLayout(0, 4));
			card.accent(Theme.GOLD_DIM).highlighted(true);
			card.add(new FloorTable(s), BorderLayout.CENTER);
			if (s.getGhost() == null)
			{
				card.add(Ui.wrapped("Your first timed run this deep. Finish it and it becomes the ghost the next one races.",
					Theme.small(), Theme.TEXT_FAINT, WIDTH - 30), BorderLayout.SOUTH);
			}
			stack.add(card);
		}
		else if (s != null && s.getRun() != null)
		{
			addLastRun(stack, s);
		}
		else
		{
			stack.add(PilotPanel.note("No runs yet", "Go down the stairs in the Hallowed Sepulchre and each floor is timed, the game's own time where it gives one, against your fastest run to the same floor."));
		}

		stack.add(new SectionHeader("Floors", "Agility " + view.agility));
		Card floors = new Card(new BorderLayout());
		StackPanel rows = new StackPanel(5, 0);
		for (Floor f : Floor.values())
		{
			boolean open = view.agility >= f.getAgility();
			boolean here = view.inRun() && f.getNumber() == view.splits.getLiveFloor();
			Color c = here ? Theme.GOLD_BRIGHT : open ? Theme.TEXT : Theme.TEXT_FAINT;
			JPanel row = new JPanel(new BorderLayout(0, 1));
			row.setOpaque(false);
			row.add(line("Floor " + f.getNumber(), open ? String.format(Locale.ENGLISH, "%,d xp", f.getXp()) : "Agility " + f.getAgility(),
				c, open ? Theme.GOLD : Theme.TEXT_FAINT), BorderLayout.NORTH);
			String marks = f.getMarksMin() == f.getMarksMax() ? String.valueOf(f.getMarksMin()) : f.getMarksMin() + "-" + f.getMarksMax();
			row.add(Ui.label(plural(f.getCoffins(), "coffin") + ", " + marks + (f.getMarksMax() == 1 ? " mark" : " marks") + " each"
				+ (f == Floor.FIVE ? ", + grand" : ""), Theme.small(), open ? Theme.TEXT_MUTED : Theme.TEXT_FAINT), BorderLayout.CENTER);
			rows.add(row);
		}
		floors.add(rows, BorderLayout.CENTER);
		stack.add(floors);
		if (view.thieving > 0 && view.thieving < Floor.COFFIN_THIEVING)
		{
			stack.add(Ui.wrapped("Coffins need Thieving " + Floor.COFFIN_THIEVING + "; you have " + view.thieving + ". Until then a run pays Agility experience only.",
				Theme.small(), Theme.EMBER, WIDTH - 16));
		}
		stack.add(Ui.wrapped("Floor experience and marks are the game's, per the Old School wiki. Times are the game's own floor times where it says them.",
			Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
	}

	private void addLastRun(StackPanel stack, FloorSplits s)
	{
		SepulchreRun run = s.getRun();
		Integer total = s.total();
		stack.add(new SectionHeader("Last run", Format.ago(view.now - run.getAt())));
		Card card = new Card(new BorderLayout(0, 6));
		Color accent = view.newGhost ? Theme.GOLD_BRIGHT : total == null ? Theme.GOLD_DIM : FloorTable.deltaColor(total);
		card.accent(accent).highlighted(view.newGhost);
		JPanel head = new JPanel(new BorderLayout(0, 2));
		head.setOpaque(false);
		int time = run.totalTime();
		head.add(Ui.label("Floor " + run.getDeepest() + (time > 0 ? " in " + FloorTable.time(time) : ""), Theme.title(), Theme.GOLD_BRIGHT), BorderLayout.NORTH);
		String verdict;
		Color vc;
		if (view.newGhost)
		{
			verdict = s.getGhost() == null || s.getGhost() == run || total == null ? "Your ghost to floor " + run.getDeepest() + " now"
				: "New ghost, " + FloorTable.delta(total).substring(1) + " faster";
			vc = Theme.GOLD;
		}
		else if (total == null)
		{
			verdict = "No ghost to compare yet";
			vc = Theme.TEXT_MUTED;
		}
		else
		{
			verdict = Math.abs(total) < 50 ? "Level with your ghost" : FloorTable.delta(total).substring(1) + (total < 0 ? " ahead of" : " behind") + " your ghost";
			vc = FloorTable.deltaColor(total);
		}
		head.add(Ui.label(verdict, Theme.small(), vc), BorderLayout.CENTER);
		card.add(head, BorderLayout.NORTH);
		StackPanel body = new StackPanel(4, 0);
		if (s.getGhost() != null && s.getGhost() != run)
		{
			body.add(new FloorTable(s));
		}
		body.add(line("Hallowed marks", "+" + run.getMarks(), Theme.TEXT_MUTED, Theme.GOLD));
		StringBuilder per = new StringBuilder();
		for (int f = 1; f <= run.getDeepest(); f++)
		{
			per.append(per.length() == 0 ? "" : " / ").append(run.coffinsOn(f));
		}
		body.add(line("Coffins by floor", per.length() == 0 ? "none" : per.toString(), Theme.TEXT_MUTED, Theme.TEXT));
		body.add(line("Grand coffin", run.isGrand() ? (run.getGrandMarks() > 0 ? "+" + run.getGrandMarks() + " marks" : "opened") : "not opened",
			Theme.TEXT_MUTED, run.isGrand() ? Theme.TEAL : Theme.TEXT_FAINT));
		body.add(line("Agility", "+" + String.format(Locale.ENGLISH, "%,d", run.getAgilityXp()) + " xp", Theme.TEXT_MUTED, Theme.TEAL));
		card.add(body, BorderLayout.CENTER);
		stack.add(card);
	}

	// The Marks page.

	private void buildMarks(StackPanel stack)
	{
		SepulchrePace pace = view.pace;
		JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
		tiles.setOpaque(false);
		if (pace == null)
		{
			tiles.add(new StatTile("-", "Per run", "no runs yet", Theme.TEXT_MUTED));
			tiles.add(new StatTile("-", "Per hour", "at your pace", Theme.TEXT_MUTED));
		}
		else
		{
			tiles.add(new StatTile(String.format(Locale.ENGLISH, "%.1f", pace.getMarksPerRun()), "Per run", "last " + plural(pace.getRuns(), "run"), Theme.GOLD));
			tiles.add(new StatTile(amount(Math.round(pace.marksPerHour())), "Per hour", pace.isGapKnown() ? "at your pace" : "run time only", Theme.GOLD));
		}
		stack.add(tiles);

		Reward target = view.target;
		long held = view.held();
		long need = Math.max(0, target.getCost() - held);
		stack.add(new SectionHeader("Saving for", target.getWhat()));
		Card goal = new Card(new BorderLayout(0, 6));
		goal.accent(need == 0 ? Theme.GREEN : Theme.GOLD).highlighted(true);
		goal.add(Ui.label(target.getLabel(), Theme.title(), Theme.GOLD_BRIGHT), BorderLayout.NORTH);
		StackPanel lines = new StackPanel(4, 0);
		double share = held / (double) target.getCost();
		lines.add(new Meter(amount(held) + " of " + amount(target.getCost()) + " marks", Format.percent(Math.min(1, share)), share, Theme.progressColor(share), -1));
		if (need == 0)
		{
			lines.add(Ui.wrapped("You hold enough. The Mysterious Hallowed Goods shop sells it.", Theme.small(), Theme.GREEN, WIDTH - 30));
		}
		else
		{
			lines.add(line("To go", amount(need) + " marks", Theme.TEXT_MUTED, Theme.GOLD));
			if (pace != null && pace.runsFor(need) > 0)
			{
				lines.add(line("Runs", "about " + amount(pace.runsFor(need)), Theme.TEXT_MUTED, Theme.GOLD));
				lines.add(line("Play time", Format.duration(pace.msFor(need)), Theme.TEXT_MUTED, Theme.GOLD));
			}
			else
			{
				lines.add(Ui.wrapped(pace == null ? "Finish a run with coffins and the time to it is worked out at your pace."
					: "Your recent runs found no marks, so there is no pace to count from.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 30));
			}
		}
		lines.add(Ui.wrapped(view.bankMarks < 0 ? "Counting " + amount(view.packMarks) + " in your pack. Open your bank once to count the marks there too."
			: amount(view.packMarks) + " in your pack, " + amount(view.bankMarks) + " in your bank as last seen.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 30));
		goal.add(lines, BorderLayout.CENTER);
		stack.add(goal);

		stack.add(new SectionHeader("Every reward", "marks"));
		Card all = new Card(new BorderLayout());
		StackPanel rows = new StackPanel(4, 0);
		for (Reward r : Reward.values())
		{
			long left = r.getCost() - held;
			String when = left <= 0 ? "enough" : pace != null && pace.runsFor(left) > 0 ? plural(pace.runsFor(left), "run") : amount(left) + " to go";
			rows.add(line(r.getLabel() + "  " + amount(r.getCost()), when, r == target ? Theme.GOLD : Theme.TEXT_MUTED, left <= 0 ? Theme.GREEN : Theme.TEXT));
		}
		all.add(rows, BorderLayout.CENTER);
		stack.add(all);
		stack.add(Ui.wrapped("Choose what you save for in the plugin's settings. Costs are the shop's, per the Old School wiki.",
			Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));

		stack.add(new SectionHeader("Uniques", "on your runs"));
		Card odds = new Card(new BorderLayout());
		odds.accent(Theme.TEAL);
		StackPanel uniques = new StackPanel(6, 0);
		for (Unique u : Unique.values())
		{
			uniques.add(uniqueRow(u, pace));
		}
		odds.add(uniques, BorderLayout.CENTER);
		stack.add(odds);
		stack.add(Ui.wrapped("Each run's chance is worked out from the coffins you loot on each floor and how often you open the grand coffin. A unique counts as found once seen in your pack, worn or in your bank. Drop rates per the Old School wiki.",
			Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
	}

	private JComponent uniqueRow(Unique u, SepulchrePace pace)
	{
		JPanel row = new JPanel(new BorderLayout(0, 1));
		row.setOpaque(false);
		if (view.got.contains(u))
		{
			row.add(line(u.getLabel(), "found", Theme.TEXT_MUTED, Theme.GREEN), BorderLayout.NORTH);
			return row;
		}
		double p = u.perRun(pace);
		row.add(line(u.getLabel(), p > 0 ? Format.oneIn(p) + " a run" : "not on your runs", Theme.TEXT, p > 0 ? Theme.TEAL : Theme.TEXT_FAINT), BorderLayout.NORTH);
		StringBuilder sub = new StringBuilder();
		if (p > 0)
		{
			sub.append("Even odds in ").append(plural(Unique.runsFor(p, 0.5), "run"));
		}
		else if (u.getFloor() > 0)
		{
			sub.append("From floor ").append(u.getFloor()).append("'s coffins");
		}
		else if (u == Unique.RING_OF_ENDURANCE)
		{
			sub.append("From the grand coffin on floor 5");
		}
		else
		{
			sub.append("From any coffin, likelier deeper");
		}
		double had = u.hadByNow(view.coffins, view.grands);
		if (had >= 0.01)
		{
			sub.append(". ").append(Format.percent(had)).append(" would have one by now");
		}
		row.add(Ui.wrapped(sub.toString(), Theme.small(), had >= 0.5 ? Theme.EMBER : Theme.TEXT_FAINT, WIDTH - 30), BorderLayout.CENTER);
		return row;
	}

	// The Log page.

	private void buildLog(StackPanel stack)
	{
		JPanel tiles = new JPanel(new GridLayout(1, 3, 6, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(amount(view.totalRuns), "Runs", "logged", Theme.GOLD));
		tiles.add(new StatTile(amount(view.totalMarks), "Marks", "found", Theme.GOLD));
		tiles.add(new StatTile(amount(view.grands), "Grand", "coffins", Theme.TEAL));
		stack.add(tiles);

		List<SepulchreRun> runs = view.runs;
		stack.add(new SectionHeader("Recent runs", runs.isEmpty() ? null : "newest first"));
		Card recent = new Card(new BorderLayout());
		StackPanel rows = new StackPanel(6, 0);
		if (runs.isEmpty())
		{
			rows.add(Ui.wrapped("Every run is logged here for this account: how deep, how fast, and what it found.", Theme.small(), Theme.TEXT_MUTED, WIDTH - 30));
		}
		for (int i = runs.size() - 1, n = 0; i >= 0 && n < RECENT; i--, n++)
		{
			SepulchreRun r = runs.get(i);
			JPanel row = new JPanel(new BorderLayout(0, 1));
			row.setOpaque(false);
			int t = r.totalTime();
			row.add(line("Floor " + r.getDeepest() + (t > 0 ? "  " + FloorTable.time(t) : ""), "+" + r.getMarks() + " marks", Theme.TEXT, Theme.GOLD), BorderLayout.NORTH);
			row.add(Ui.label(Format.ago(view.now - r.getAt()) + ", " + plural(r.getCoffins(), "coffin") + (r.isGrand() ? ", grand coffin" : ""),
				Theme.small(), Theme.TEXT_FAINT), BorderLayout.CENTER);
			rows.add(row);
		}
		recent.add(rows, BorderLayout.CENTER);
		stack.add(recent);

		stack.add(new SectionHeader("Coffins looted", "by floor"));
		Card coffins = new Card(new BorderLayout());
		StackPanel lines = new StackPanel(3, 0);
		for (Floor f : Floor.values())
		{
			long n = view.coffins[f.getNumber() - 1];
			lines.add(line("Floor " + f.getNumber(), amount(n), Theme.TEXT_MUTED, n > 0 ? Theme.TEXT : Theme.TEXT_FAINT));
		}
		lines.add(line("Grand coffin", amount(view.grands), Theme.TEXT_MUTED, view.grands > 0 ? Theme.TEAL : Theme.TEXT_FAINT));
		coffins.add(lines, BorderLayout.CENTER);
		stack.add(coffins);
		if (view.pace != null)
		{
			stack.add(Ui.wrapped("At your pace: " + amount(Math.round(view.pace.xpPerHour())) + " Agility xp an hour, a run every "
				+ Format.duration(view.pace.cycleMs()) + (view.pace.isGapKnown() ? " with the walk back." : "."), Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		}
	}

	// Helpers.

	private static String amount(long n)
	{
		return Format.amount(n);
	}

	static String plural(long n, String word)
	{
		return String.format(Locale.ENGLISH, "%,d", n) + " " + word + (n == 1 ? "" : "s");
	}

	private static JComponent line(String left, String right, Color leftColor, Color rightColor)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JLabel l = Ui.label(left, Theme.small(), leftColor);
		row.add(l, BorderLayout.CENTER);
		row.add(Ui.label(right, Theme.small(), rightColor), BorderLayout.EAST);
		return row;
	}

	private static void replace(JScrollPane scroll, JComponent view)
	{
		int position = scroll.getVerticalScrollBar().getValue();
		scroll.setViewportView(view);
		scroll.revalidate();
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(position));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		g.setColor(Theme.INK);
		g.fillRect(0, 0, getWidth(), getHeight());
	}
}
