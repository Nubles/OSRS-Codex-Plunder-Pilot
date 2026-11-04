package com.codex.plunder.ui;

import com.codex.plunder.core.Check;
import com.codex.plunder.core.Goal;
import com.codex.plunder.core.Grade;
import com.codex.plunder.core.Levels;
import com.codex.plunder.core.NavigatorGoals;
import com.codex.plunder.core.Odds;
import com.codex.plunder.core.Outlook;
import com.codex.plunder.core.Pace;
import com.codex.plunder.core.Plan;
import com.codex.plunder.core.Planner;
import com.codex.plunder.core.Room;
import com.codex.plunder.core.Run;
import com.codex.plunder.core.Session;
import com.codex.plunder.core.Leaks;
import com.codex.plunder.core.Splits;
import com.codex.plunder.core.Step;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Toolkit;
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
 * The Plunder Pilot side panel: the crest with the clock or the deepest room, then three pages.
 * Pilot is the next step and the route room by room, Odds is the sceptre and how dry the
 * player has been, and Runs is the rate, the way to a goal level and every run so far.
 */
public class PilotPanel extends PluginPanel
{
	static final int WIDTH = 200;
	private static final String[] TABS = {"pilot", "odds", "runs"};
	private static final int RECENT = 8;

	private final Actions actions;
	private final Crest crest = new Crest();
	private final Segmented tabs = new Segmented(true, "Pilot", "Odds", "Runs");
	private final Segmented goal = new Segmented(false, Goal.EXPERIENCE.getLabel(), Goal.SCEPTRE.getLabel());
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane pilot = scroller();
	private final JScrollPane odds = scroller();
	private final JScrollPane runs = scroller();

	private View view = View.loggedOut();

	public PilotPanel(Actions actions)
	{
		super(false);
		this.actions = actions;
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
		content.add(pilot, TABS[0]);
		content.add(odds, TABS[1]);
		content.add(runs, TABS[2]);
		add(content, BorderLayout.CENTER);

		tabs.onChange(this::showTab);
		goal.onChange(i -> actions.goal(Goal.values()[i]));
		refresh(true);
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
	public void update(View view)
	{
		this.view = view;
		goal.setSelected(view.goal.ordinal(), false);
		updateCrest();
		rebuild();
	}

	/** Opens one tab, building it if it is out of date. */
	public void showTab(int index)
	{
		tabs.setSelected(index, false);
		cards.show(content, TABS[index]);
		refresh(true);
	}

	private void updateCrest()
	{
		switch (view.state)
		{
			case LOGGED_OUT:
				crest.update("PYRAMID PLUNDER", null, "Runs planned to your level", "Log in to plan your runs",
					Theme.mix(Theme.TEXT_MUTED, Theme.GOLD, 0.45), Theme.GOLD_DIM, -1);
				return;
			case TOO_LOW:
				crest.update("THIEVING " + view.level, "21", "to enter", "The pyramid opens at Thieving 21", Theme.TEXT_MUTED, Theme.GOLD_DIM, -1);
				return;
			default:
				break;
		}
		Plan plan = view.plan;
		if (view.inRun())
		{
			Plan.Stop stop = plan.current();
			int ticks = view.live.ticksLeft;
			Color tint = Theme.clock(ticks);
			crest.update("ROOM " + stop.getRoom().getNumber() + " OF " + plan.getTop().getNumber(), Format.clock(ticks), "left",
				view.step.getTitle(), ticks <= 150 ? tint : Theme.mix(Theme.TEXT_MUTED, Theme.GOLD, 0.45), tint,
				ticks / (double) Planner.RUN_TICKS);
			return;
		}
		Plan fresh = view.fresh;
		crest.update("THIEVING " + view.level + "  -  " + view.goal.getLabel().toUpperCase(Locale.ENGLISH),
			String.valueOf(fresh.getTop().getNumber()), fresh.getTop().getNumber() == 1 ? "room" : "rooms",
			Format.amount(fresh.xpPerHour(Planner.RESTART_TICKS)) + " xp/hr, sceptre " + Format.oneInShort(fresh.getSceptreChance()),
			Theme.mix(Theme.TEXT_MUTED, Theme.GOLD, 0.35), Theme.GOLD, -1);
	}

	private String oddsShown;
	private String runsShown;
	/** The Pilot page shows an older view than the one in hand. */
	private boolean pilotStale = true;

	/**
	 * The Pilot page changes every tick of a run; the other two only when a run is added, the level
	 * or goal changes or a minute passes. While the panel is closed nothing is rebuilt, and while it
	 * is open only the page in view is, and that only when what it shows has changed.
	 */
	private void rebuild()
	{
		pilotStale = true;
		refresh(false);
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
		if (page == 0)
		{
			if (pilotStale)
			{
				pilotStale = false;
				rebuildPilot();
			}
			return;
		}
		View.Record r = view.record;
		String common = view.state + "|" + view.level + "|" + view.goal + "|" + r.totalRuns + "|" + r.runs.size() + "|" + r.sceptres
			+ "|" + r.paceSamples;
		if (page == 1)
		{
			long opened = 0;
			for (long o : r.opened)
			{
				opened += o;
			}
			String odds = common + "|" + opened + "|" + view.now / 86_400_000L;
			if (!odds.equals(oddsShown))
			{
				oddsShown = odds;
				rebuildOdds();
			}
			return;
		}
		StringBuilder goals = new StringBuilder();
		for (NavigatorGoals.Need n : view.goals)
		{
			goals.append(n.getGoal()).append(n.getLevel()).append(',');
		}
		// The pyramid lights the room of the run in hand.
		int room = view.inRun() ? view.plan.current().getRoom().getNumber() : 0;
		String runsKey = common + "|" + view.xp + "|" + view.targetLevel + "|" + r.recentRate + "|" + view.now / 60_000L + "|" + goals + "|" + room;
		if (!runsKey.equals(runsShown))
		{
			runsShown = runsKey;
			rebuildRuns();
		}
	}

	private boolean waiting(StackPanel stack)
	{
		if (view.state == View.State.LOGGED_OUT)
		{
			stack.add(note("The pyramid is sealed", "Log in and the Pilot plans every Pyramid Plunder run for your Thieving level: which rooms to walk through, which urns, chests and sarcophagi are worth the clock, and your odds of the Pharaoh's sceptre."));
			return true;
		}
		if (view.state == View.State.TOO_LOW)
		{
			int left = Levels.xpFor(Room.ENTRY_LEVEL) - (int) Math.min(Integer.MAX_VALUE, view.xp);
			stack.add(note("Not yet", "Pyramid Plunder needs Thieving 21, and the level cannot be boosted. You are " + Format.amount(Math.max(0, left)) + " experience away."));
			return true;
		}
		return false;
	}

	// The Pilot tab.

	private void rebuildPilot()
	{
		StackPanel stack = new StackPanel(8, 8);
		if (waiting(stack))
		{
			replace(pilot, stack);
			return;
		}
		if (!view.inRun() && view.grade != null)
		{
			addGrade(stack, view.grade, view.ghost);
		}
		stack.add(new SectionHeader("Run for", view.inRun() ? "replanned every tick" : null));
		stack.add(goal);

		Card next = new Card(new BorderLayout(0, 4));
		boolean danger = view.step.getKind() == Step.Kind.DANGER;
		next.accent(danger ? Theme.BLOOD : view.inRun() ? Theme.clock(view.live.ticksLeft) : Theme.TEAL).highlighted(view.inRun());
		next.add(Ui.label(danger ? "FIRST" : view.inRun() ? "NEXT" : "BEFORE YOU GO IN", Theme.small(), Theme.TEXT_FAINT), BorderLayout.NORTH);
		JPanel words = new JPanel(new BorderLayout(0, 2));
		words.setOpaque(false);
		words.add(Ui.label(view.step.getTitle(), Theme.title(), danger ? Theme.BLOOD : Theme.GOLD_BRIGHT), BorderLayout.NORTH);
		words.add(Ui.wrapped(view.step.getDetail(), Theme.small(), Theme.TEXT_MUTED, WIDTH - 30), BorderLayout.CENTER);
		next.add(words, BorderLayout.CENTER);
		stack.add(next);

		if (!view.inRun() && view.ready != null)
		{
			stack.add(new SectionHeader("Ready check"));
			Card ready = new Card(new BorderLayout());
			StackPanel checks = new StackPanel(4, 0);
			for (Check check : view.ready.checks)
			{
				checks.add(new CheckRow(check, WIDTH - 30));
			}
			ready.add(checks, BorderLayout.CENTER);
			stack.add(ready);
			if (view.ready.artefacts > 0)
			{
				stack.add(Ui.wrapped("Carrying " + view.ready.artefacts + (view.ready.artefacts == 1 ? " artefact" : " artefacts") + " worth "
					+ Format.coins(view.ready.artefactValue) + " gp. They sell for about the same at the Grand Exchange.", Theme.small(), Theme.GOLD_DIM, WIDTH - 16));
			}
		}

		Plan plan = view.plan;
		boolean split = view.inRun() && view.live.split != null;
		JPanel tiles = new JPanel(new GridLayout(1, split ? 3 : 2, 6, 0));
		tiles.setOpaque(false);
		if (view.inRun())
		{
			tiles.add(new StatTile("+" + Format.amount(view.live.thievingXp), "This run",
				"+" + Format.amount(plan.getThievingXp()) + (split ? "" : " to come"), Theme.GOLD));
			double chance = 1 - (1 - view.live.sceptreSoFar) * (1 - plan.getSceptreChance());
			tiles.add(new StatTile(Format.oneIn(chance), "Sceptre", view.live.opened + (split ? " opened" : " opened so far"), Theme.TEAL));
			if (split)
			{
				int d = view.live.split;
				Color c = Math.round(d * 0.6) == 0 ? Theme.TEXT_MUTED : d < 0 ? Theme.GREEN : Theme.EMBER;
				tiles.add(new StatTile(Format.split(d), "Split", "vs best", c));
			}
		}
		else
		{
			tiles.add(new StatTile(Format.amount(plan.getThievingXp()), "Per run",
				plan.getStrengthXp() > 0 ? "+" + Format.amount(plan.getStrengthXp()) + " strength" : "thieving xp", Theme.GOLD));
			tiles.add(new StatTile(Format.oneIn(plan.getSceptreChance()), "Sceptre", "a run", Theme.TEAL));
		}
		stack.add(tiles);

		if (view.inRun() && view.ghost != null)
		{
			stack.add(new SectionHeader("Ghost splits", view.ghost.ghost == null ? "no ghost yet" : "game ticks"));
			Card splits = new Card(new BorderLayout(0, 4));
			splits.accent(Theme.GOLD_DIM);
			splits.add(new GhostTable(view.ghost), BorderLayout.CENTER);
			if (view.ghost.ghost == null)
			{
				splits.add(Ui.wrapped("Your first timed run of this kind. Finish it and it becomes the ghost the next one races.",
					Theme.small(), Theme.TEXT_FAINT, WIDTH - 30), BorderLayout.SOUTH);
			}
			stack.add(splits);
		}

		stack.add(new SectionHeader("Route", "room " + plan.current().getRoom().getNumber() + " to " + plan.getTop().getNumber()));
		Card route = new Card(new BorderLayout());
		StackPanel rows = new StackPanel(0, 0);
		for (Plan.Stop stop : plan.getStops())
		{
			rows.add(new RoomRow(stop, view.inRun() && stop == plan.current()));
		}
		for (int i = plan.getTop().ordinal() + 1; i < Room.values().length; i++)
		{
			rows.add(new RoomRow(Room.values()[i]));
		}
		route.add(rows, BorderLayout.CENTER);
		stack.add(route);
		String foot = view.record.paceSamples >= Pace.TRUSTED
			? "Paced from your own searches. Chests and sarcophagi last in each room, so a swarm or mummy has the least time to chase."
			: "Paced at typical speeds until a few of your runs are timed. Chests and sarcophagi last in each room.";
		stack.add(Ui.wrapped(foot, Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		replace(pilot, stack);
	}

	/** The last run's grade card: the letter, its rate against the ghost, and where the time went. */
	private void addGrade(StackPanel stack, Grade grade, View.GhostSplits splits)
	{
		stack.add(new SectionHeader("Last run", grade.getLetter() == null ? "ghost set" : "against your ghost"));
		Card card = new Card(new BorderLayout(0, 6));
		card.accent(GradeMedal.tint(grade.getLetter())).highlighted(grade.getLetter() == Grade.Letter.S);
		card.add(new GradeMedal(grade), BorderLayout.NORTH);
		StackPanel body = new StackPanel(4, 0);
		if (grade.getLetter() != null)
		{
			if (grade.getSinks().isEmpty())
			{
				body.add(Ui.wrapped("No part of any room ran " + Grade.SINK_MIN + " ticks or more over your ghost.", Theme.small(), Theme.GREEN, WIDTH - 30));
			}
			else
			{
				body.add(Ui.label("WHERE THE TIME WENT", Theme.small(), Theme.TEXT_FAINT));
				for (Grade.Sink sink : grade.getSinks())
				{
					body.add(Ui.wrapped(sink.sentence(), Theme.small(), Theme.TEXT, WIDTH - 30));
				}
			}
		}
		if (splits != null && !splits.live && splits.ghost != null)
		{
			body.add(new GhostTable(splits));
		}
		String foot;
		if (grade.getLetter() == null)
		{
			foot = "Your first run to room " + grade.getDeepest() + " at this level is now your ghost. The next one is split against it, tick by tick.";
		}
		else if (grade.isNewGhost())
		{
			foot = "The best of its kind yet: the next run to room " + grade.getDeepest() + " races this one.";
		}
		else
		{
			foot = "S is within 2% of your ghost's rate, A within 10%, B within 25%.";
		}
		body.add(Ui.wrapped(foot, Theme.small(), Theme.TEXT_FAINT, WIDTH - 30));
		card.add(body, BorderLayout.CENTER);
		stack.add(card);
	}

	// The Odds tab.

	private void rebuildOdds()
	{
		StackPanel stack = new StackPanel(8, 8);
		if (waiting(stack))
		{
			replace(odds, stack);
			return;
		}
		View.Record r = view.record;
		long opened = 0;
		for (long o : r.opened)
		{
			opened += o;
		}
		double expected = Odds.expected(r.opened);
		JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(String.format(Locale.ENGLISH, "%,d", opened), "Opened", "chests & sarcs", Theme.GOLD));
		tiles.add(new StatTile(String.format(Locale.ENGLISH, "%.2f", expected), "Expected", r.sceptres + " found so far",
			r.sceptres > 0 ? Theme.TEAL : Theme.TEXT_MUTED));
		stack.add(tiles);

		Card luck = new Card(new BorderLayout(0, 6));
		luck.accent(Theme.TEAL);
		if (opened == 0)
		{
			luck.add(Ui.label("No openings yet", Theme.body(), Theme.GOLD), BorderLayout.NORTH);
			luck.add(Ui.wrapped("Every grand gold chest and sarcophagus you open is counted by room, and your sceptre luck is measured against it.",
				Theme.small(), Theme.TEXT_MUTED, WIDTH - 30), BorderLayout.CENTER);
		}
		else
		{
			double share = Odds.luck(r.opened, r.sceptres);
			String verdict;
			Color color;
			if (r.sceptres == 0)
			{
				double had = Odds.atLeastOne(r.opened);
				verdict = Format.percent(had) + " would have one by now";
				color = had > 0.5 ? Theme.EMBER : Theme.GOLD;
			}
			else if (share >= 0.5)
			{
				verdict = "Luckier than " + Format.percent(share);
				color = Theme.TEAL;
			}
			else
			{
				verdict = "Drier than " + Format.percent(1 - share);
				color = Theme.EMBER;
			}
			luck.add(Ui.label(verdict, Theme.body(), color), BorderLayout.NORTH);
			luck.add(new Meter("Your luck", Format.percent(share), share, color, 0.5), BorderLayout.CENTER);
		}
		stack.add(luck);

		Plan fresh = view.fresh;
		double perRun = fresh.getSceptreChance();
		stack.add(new SectionHeader("Your runs", view.goal.getLabel()));
		Card forecast = new Card(new BorderLayout(0, 6));
		StackPanel lines = new StackPanel(4, 0);
		lines.add(line("Each run", Format.oneIn(perRun), Theme.TEAL));
		lines.add(line("Even odds", Format.amount(Odds.runsFor(perRun, 0.5)) + " runs", Theme.GOLD));
		lines.add(line("Nine in ten", Format.amount(Odds.runsFor(perRun, 0.9)) + " runs", Theme.GOLD));
		lines.add(line("In an hour", Format.percent(fresh.sceptrePerHour(Planner.RESTART_TICKS)), Theme.GOLD));
		forecast.add(lines, BorderLayout.CENTER);
		Outlook outlook = Outlook.of(r.history, view.now);
		long even = outlook.afterRuns(Odds.runsFor(perRun, 0.5), view.now);
		if (even > 0)
		{
			// The run counts above, turned into days at the pace the player really comes back.
			long nine = outlook.afterRuns(Odds.runsFor(perRun, 0.9), view.now);
			StackPanel dates = new StackPanel(4, 0);
			dates.add(line("Even odds by", Format.shortDay(even), Theme.TEAL));
			dates.add(line("Nine in ten by", nine > 0 ? Format.shortDay(nine) : "years off", Theme.TEAL));
			dates.add(Ui.wrapped("At your pace: " + Format.duration(Math.round(outlook.hoursPerWeek() * 3_600_000L)) + " a week here, over the last four weeks.",
				Theme.small(), Theme.TEXT_FAINT, WIDTH - 30));
			forecast.add(dates, BorderLayout.SOUTH);
		}
		stack.add(forecast);
		if (view.goal == Goal.EXPERIENCE)
		{
			stack.add(Ui.wrapped("Hunting the sceptre opens every chest and the sarcophagi from room 4 on, at some cost in experience. Switch the goal on the Pilot page to compare.",
				Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		}

		stack.add(new SectionHeader("Per opening", "chest or sarc"));
		Card rooms = new Card(new BorderLayout());
		StackPanel rows = new StackPanel(3, 0);
		for (Room room : Room.values())
		{
			boolean open = view.level >= room.getLevel();
			long n = r.opened[room.ordinal()];
			rows.add(line("Room " + room.getNumber() + (n > 0 ? "  -  " + Format.amount(n) + " opened" : ""),
				"1 in " + String.format(Locale.ENGLISH, "%,d", room.getSceptreOneIn()), open ? Theme.TEAL : Theme.TEXT_FAINT));
		}
		rooms.add(rows, BorderLayout.CENTER);
		stack.add(rooms);
		stack.add(Ui.wrapped("Finding the sceptre ejects you from the pyramid at once. Rates are the game's, per the Old School wiki.",
			Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		replace(odds, stack);
	}

	// The Runs tab.

	private void rebuildRuns()
	{
		StackPanel stack = new StackPanel(8, 8);
		if (waiting(stack))
		{
			replace(runs, stack);
			return;
		}
		View.Record r = view.record;
		Plan fresh = view.fresh;
		int forecast = fresh.xpPerHour(Planner.RESTART_TICKS);
		int rate = r.recentRate > 0 ? r.recentRate : forecast;
		JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(Format.amount(rate), "Xp an hour", r.recentRate > 0 ? "your last runs" : "forecast", Theme.GOLD));
		tiles.add(new StatTile(Format.amount(r.totalRuns), "Runs", r.totalRuns == 0 ? "none logged yet" : Format.coins(r.totalArtefacts) + " gp loot", Theme.TEAL));
		stack.add(tiles);
		// The ladder, drawn as the pyramid it climbs, with the player's level and their goals up its side.
		stack.add(new PyramidCard(view.ladder, view.level, view.inRun() ? view.plan.current().getRoom() : null, r.sceptres, view.goals));
		stack.add(cardButton());

		addSession(stack, r.session);
		addHistory(stack, r.history);

		int target = Math.max(view.level + 1, Math.min(99, view.targetLevel));
		if (view.level < 99)
		{
			long goalXp = Levels.xpFor(target);
			long left = Math.max(0, goalXp - view.xp);
			int perRun = Math.max(1, fresh.getThievingXp());
			long runsLeft = (left + perRun - 1) / perRun;
			long start = Levels.xpFor(view.level);
			double share = goalXp <= start ? 1 : (view.xp - start) / (double) (goalXp - start);
			Card goalCard = new Card(new BorderLayout(0, 6));
			goalCard.accent(Theme.GOLD);
			goalCard.add(Ui.label("Thieving " + target, Theme.body(), Theme.GOLD), BorderLayout.NORTH);
			String hours = Format.duration(Math.round(left * 3_600_000.0 / Math.max(1, rate)));
			goalCard.add(new Meter(Format.amount(left) + " xp, " + Format.amount(runsLeft) + (runsLeft == 1 ? " run" : " runs"), hours, share, Theme.GOLD, -1),
				BorderLayout.CENTER);
			Outlook outlook = Outlook.of(r.history, view.now);
			StackPanel south = new StackPanel(4, 0);
			long by = outlook.afterHours(left / (double) Math.max(1, rate), view.now);
			if (by > 0)
			{
				south.add(Ui.label("About " + Format.shortDay(by) + " at your pace", Theme.small(), Theme.TEAL));
			}
			int unlock = Room.nextUnlock(view.level);
			if (unlock > 0 && unlock < target)
			{
				long toUnlock = Math.max(0, Levels.xpFor(unlock) - view.xp);
				south.add(Ui.wrapped("Room " + Room.highest(unlock).getNumber() + " opens at " + unlock + ", " + Format.amount(toUnlock)
					+ " xp away, and every run after pays more.", Theme.small(), Theme.TEXT_MUTED, WIDTH - 30));
			}
			if (south.getComponentCount() > 0)
			{
				goalCard.add(south, BorderLayout.SOUTH);
			}
			stack.add(goalCard);
			addGoals(stack, fresh, rate, outlook);
		}

		addStash(stack, view.stash);


		addSplits(stack, r);
		addLeaks(stack, r.leaks);

		if (!r.runs.isEmpty())
		{
			stack.add(new SectionHeader("Recent runs", Format.amount(r.totalXp) + " xp in all"));
			Card recent = new Card(new BorderLayout());
			StackPanel runRows = new StackPanel(4, 0);
			for (int i = r.runs.size() - 1; i >= 0 && i >= r.runs.size() - RECENT; i--)
			{
				Run run = r.runs.get(i);
				runRows.add(new RunRow(run, view.now));
			}
			recent.add(runRows, BorderLayout.CENTER);
			stack.add(recent);
		}
		replace(runs, stack);
	}

	/** The sitting in hand, or the last one: what it earned, its rate and the sceptre chance it carried. */
	/**
	 * The Thieving levels Codex Navigator's pinned goals need, each with the runs, play time and
	 * date it takes at the Pilot's plan and the player's pace.
	 */
	private void addGoals(StackPanel stack, Plan fresh, int rate, Outlook outlook)
	{
		if (view.goals.isEmpty())
		{
			return;
		}
		stack.add(new SectionHeader("For your goals", "Navigator"));
		Card card = new Card(new BorderLayout());
		card.accent(Theme.TEAL);
		StackPanel rows = new StackPanel(6, 0);
		int perRun = Math.max(1, fresh.getThievingXp());
		for (NavigatorGoals.Need n : view.goals)
		{
			long left = Math.max(0, Levels.xpFor(n.getLevel()) - view.xp);
			long runsLeft = (left + perRun - 1) / perRun;
			double hours = left / (double) Math.max(1, rate);
			long by = outlook.afterHours(hours, view.now);
			JPanel row = new JPanel(new BorderLayout(0, 1));
			row.setOpaque(false);
			JPanel top = new JPanel(new BorderLayout(6, 0));
			top.setOpaque(false);
			JLabel title = Ui.label(n.getGoal(), Theme.body(), n.isFocus() ? Theme.GOLD_BRIGHT : Theme.TEXT);
			title.setToolTipText(n.getGoal() + (n.isFocus() ? ", your Navigator focus" : "") + ", needs Thieving " + n.getLevel());
			top.add(title, BorderLayout.CENTER);
			top.add(Ui.label(String.valueOf(n.getLevel()), Theme.body(), Theme.GOLD), BorderLayout.EAST);
			row.add(top, BorderLayout.NORTH);
			String how = Format.amount(runsLeft) + (runsLeft == 1 ? " run, " : " runs, ")
				+ Format.duration(Math.round(hours * 3_600_000L)) + (by > 0 ? ", " + Format.shortDay(by) : "");
			row.add(Ui.label(how, Theme.small(), Theme.TEXT_MUTED), BorderLayout.SOUTH);
			rows.add(row);
		}
		card.add(rows, BorderLayout.CENTER);
		stack.add(card);
	}

	private void addSession(StackPanel stack, Session s)
	{
		if (s == null || s.isEmpty())
		{
			return;
		}
		int count = s.getRuns().size();
		String title = s.isCurrent() ? "This session" : "Last session";
		String when = s.isCurrent() ? Format.duration(s.spanMs()) : Format.shortDay(s.getStartedAt());
		stack.add(new SectionHeader(title, when));
		Card card = new Card(new BorderLayout(0, 6));
		card.accent(s.isCurrent() ? Theme.GOLD : Theme.GOLD_DIM);
		JPanel figures = new JPanel(new GridLayout(1, 3, 4, 0));
		figures.setOpaque(false);
		figures.add(figure(String.valueOf(count), count == 1 ? "run" : "runs", Theme.GOLD));
		figures.add(figure(Format.amount(s.xpPerHour()), "xp/hr", Theme.GOLD_BRIGHT));
		figures.add(figure(Format.coins(s.artefactsPerHour()), "gp/hr", Theme.GOLD));
		card.add(figures, BorderLayout.NORTH);
		card.add(new SessionBars(s.getRuns()), BorderLayout.CENTER);
		StackPanel lines = new StackPanel(3, 0);
		lines.add(line("Thieving", "+" + Format.amount(s.getXp()) + " xp", Theme.GOLD));
		if (s.getStrengthXp() > 0)
		{
			lines.add(line("Strength", "+" + Format.amount(s.getStrengthXp()) + " xp", Theme.GOLD_DIM));
		}
		if (s.getSceptres() > 0)
		{
			lines.add(line("Sceptre", s.getSceptres() == 1 ? "found!" : s.getSceptres() + " found!", Theme.TEAL));
		}
		else if (s.getSceptreChance() > 0)
		{
			lines.add(line("Sceptre chance", Format.oneIn(s.getSceptreChance()) + " (" + Format.percent(s.getSceptreChance()) + ")", Theme.TEAL));
		}
		Run best = s.best();
		if (best != null && count > 1)
		{
			lines.add(line("Best run", "+" + Format.amount(best.getThievingXp()) + ", room " + best.getDeepest(), Theme.GOLD));
		}
		card.add(lines, BorderLayout.SOUTH);
		stack.add(card);
	}

	/** The most past sessions listed. */
	private static final int PAST_SESSIONS = 8;

	/** Every session kept, newest first, each against the best one's pace. */
	/** Copies a picture of the climb, to paste into a chat. */
	private JComponent cardButton()
	{
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.setOpaque(false);
		CodexButton copy = new CodexButton("Copy pyramid card", false, null);
		copy.setToolTipText("Copies a picture of your pyramid and what your runs earn, to paste into a chat");
		copy.setAction(() ->
		{
			try
			{
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(PyramidSheet.transferable(PyramidSheet.render(view)), null);
				copy.flash("Copied: paste it anywhere");
			}
			catch (IllegalStateException e)
			{
				// Another program holds the clipboard.
				copy.flash("Clipboard busy, try again");
			}
		});
		buttons.add(copy);
		return buttons;
	}

	private void addHistory(StackPanel stack, List<Session> history)
	{
		if (history.size() < 2)
		{
			return;
		}
		Session best = Session.best(history);
		int bestRate = 0;
		for (Session s : history)
		{
			bestRate = Math.max(bestRate, s.xpPerHour());
		}
		// The best session is the gilt row below; the heading counts them rather than say its rate again.
		stack.add(new SectionHeader("Past sessions", history.size() + " sessions"));
		Card card = new Card(new BorderLayout(0, 4));
		StackPanel rows = new StackPanel(4, 0);
		for (int i = 0; i < history.size() && i < PAST_SESSIONS; i++)
		{
			Session s = history.get(i);
			rows.add(new SessionRow(s, bestRate, s == best));
		}
		card.add(rows, BorderLayout.CENTER);
		if (history.size() > PAST_SESSIONS)
		{
			card.add(Ui.label(olderNote(history.size() - PAST_SESSIONS), Theme.small(), Theme.TEXT_FAINT), BorderLayout.SOUTH);
		}
		stack.add(card);
		Session latest = history.get(0);
		if (best != null && latest != best && latest.getRuns().size() >= Session.BEST_MIN_RUNS)
		{
			int gap = best.xpPerHour() - latest.xpPerHour();
			stack.add(Ui.wrapped((latest.isCurrent() ? "This session runs " : "Your last session ran ") + Format.amount(Math.abs(gap))
				+ " xp/hr " + (gap > 0 ? "below" : "above") + " your best, on " + Format.shortDay(best.getStartedAt()) + ".",
				Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		}
	}

	/** "1 older session not shown", "3 older sessions not shown". */
	static String olderNote(int older)
	{
		return older + (older == 1 ? " older session" : " older sessions") + " not shown";
	}

	/** The artefacts waiting in the bank, with what they fetch at the Grand Exchange. */
	private void addStash(StackPanel stack, View.Stash stash)
	{
		if (stash == null)
		{
			return;
		}
		long count = stash.count();
		stack.add(new SectionHeader("Artefacts banked", count == 0 ? null : Format.coins(stash.value()) + " gp"));
		String seen = "Bank as last seen " + Format.ago(view.now - stash.seenAt) + (stash.from == null ? "" : ", by " + stash.from) + ".";
		if (count == 0)
		{
			stack.add(Ui.wrapped("None in the bank. " + seen, Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
			return;
		}
		Card card = new Card(new BorderLayout(0, 4));
		card.accent(Theme.GOLD_DIM);
		StackPanel rows = new StackPanel(3, 0);
		for (View.Stash.Line l : stash.lines)
		{
			rows.add(line(l.name + " x" + Format.amount(l.count), Format.coins(l.value), Theme.GOLD));
		}
		card.add(rows, BorderLayout.CENTER);
		stack.add(card);
		stack.add(Ui.wrapped(count + (count == 1 ? " artefact" : " artefacts") + " waiting to be sold at the Grand Exchange. " + seen,
			Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
	}

	/** A number over a small word, centred, for the session card. */
	private static JComponent figure(String value, String label, Color color)
	{
		JPanel p = new JPanel(new BorderLayout(0, 0));
		p.setOpaque(false);
		JLabel v = Ui.label(value, Theme.title(), color);
		v.setHorizontalAlignment(JLabel.CENTER);
		JLabel l = Ui.label(label, Theme.small(), Theme.TEXT_FAINT);
		l.setHorizontalAlignment(JLabel.CENTER);
		p.add(v, BorderLayout.CENTER);
		p.add(l, BorderLayout.SOUTH);
		return p;
	}

	/** The last run to the deepest room, room by room, against the best before it. */
	private void addSplits(StackPanel stack, View.Record r)
	{
		Splits best = r.splits;
		Run run = r.splitRun;
		if (best == null || best.getTop() == null)
		{
			return;
		}
		Room top = best.getTop();
		if (top.ordinal() == 0)
		{
			return;
		}
		if (run == null)
		{
			stack.add(new SectionHeader("Splits", "to room " + top.getNumber()));
			stack.add(Ui.wrapped("Reach room " + top.getNumber() + " on a " + view.goal.getLabel().toLowerCase(Locale.ENGLISH)
				+ " run and the Pilot keeps your splits: when you entered each room, against your best run.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
			return;
		}
		boolean compared = best.hasBest();
		Integer gap = compared ? best.delta(top, run.splitAt(top)) : null;
		String trailing = gap == null ? "first run" : gap < 0 ? "new best" : "best " + Format.clock(best.bestAt(top));
		stack.add(new SectionHeader("Splits", trailing));
		Card card = new Card(new BorderLayout(0, 6));
		card.accent(gap != null && gap < 0 ? Theme.GREEN : Theme.GOLD);
		card.add(new SplitTable(run, best), BorderLayout.CENTER);
		String foot;
		int sob = best.sumOfBest();
		if (!compared)
		{
			foot = "Your first timed run to room " + top.getNumber() + ". The next one is measured against it.";
		}
		else if (sob >= 0 && sob < best.bestAt(top))
		{
			foot = "Each bar is how long the room took; its tick is the room's quickest ever. Your quickest rooms together reach room "
				+ top.getNumber() + " at " + Format.clock(sob) + ", " + Format.seconds(best.bestAt(top) - sob) + " under your best.";
		}
		else
		{
			foot = "Each bar is how long the room took; its tick is the room's quickest ever.";
		}
		card.add(Ui.wrapped(foot, Theme.small(), Theme.TEXT_FAINT, WIDTH - 30), BorderLayout.SOUTH);
		stack.add(card);
	}

	/** Where the clock goes: each room's usual crossing against its quickest, priced in experience an hour. */
	private void addLeaks(StackPanel stack, Leaks leaks)
	{
		if (leaks == null || leaks.getTop() == null || leaks.getTop().ordinal() == 0)
		{
			return;
		}
		boolean urns = leaks.getFasterUrnsFrom() != null;
		if (leaks.getRooms().isEmpty() && !urns)
		{
			return;
		}
		Leaks.Leak worst = leaks.biggest();
		int total = leaks.total();
		stack.add(new SectionHeader("Time lost", total >= 100 ? Format.amount(total) + " xp/h" : "none"));
		Card card = new Card(new BorderLayout(0, 6));
		card.accent(worst != null ? Theme.EMBER : Theme.GREEN);
		StackPanel body = new StackPanel(6, 0);
		if (!leaks.getRooms().isEmpty())
		{
			body.add(new LeakTable(leaks));
			String foot;
			if (worst != null)
			{
				foot = "Room " + worst.getRoom().getNumber() + " gives the most away: its trap, walk and door usually take you " + Format.seconds(worst.getTypical())
					+ ", " + Format.seconds(worst.getGap()) + " over your quickest. That fast every run buys searches deeper down worth about "
					+ Format.amount(worst.getXpPerHour()) + " xp an hour.";
			}
			else
			{
				foot = "You get through every room about as fast as you ever have, so the clock is going on searches.";
			}
			body.add(Ui.wrapped(foot + " Searching is not counted. From your last " + leaks.getRuns() + " runs to room " + leaks.getTop().getNumber() + ".",
				Theme.small(), Theme.TEXT_FAINT, WIDTH - 30));
		}
		if (urns)
		{
			int n = leaks.getMovedUrns();
			body.add(Ui.wrapped("Your urns in room " + leaks.getFasterUrnsFrom().getNumber() + " take you longer than in room " + leaks.getFasterUrnsTo().getNumber()
				+ ", so the plan now searches " + n + (n == 1 ? " more urn" : " more urns") + " in room " + leaks.getFasterUrnsTo().getNumber()
				+ " instead: about " + Format.amount(leaks.getUrnGain()) + " xp a run more than timing every room alike.", Theme.small(), Theme.TEAL, WIDTH - 30));
		}
		card.add(body, BorderLayout.CENTER);
		stack.add(card);
	}

	private static JComponent line(String left, String right, Color color)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JLabel l = Ui.label(left, Theme.small(), Theme.TEXT_MUTED);
		row.add(l, BorderLayout.CENTER);
		row.add(Ui.label(right, Theme.small(), color), BorderLayout.EAST);
		return row;
	}

	static Card note(String title, String text)
	{
		Card card = new Card(new BorderLayout(0, 4));
		card.accent(Theme.GOLD_DIM);
		card.add(Ui.label(title, Theme.body(), Theme.GOLD), BorderLayout.NORTH);
		card.add(Ui.wrapped(text, Theme.small(), Theme.TEXT_MUTED, WIDTH - 30), BorderLayout.CENTER);
		return card;
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
