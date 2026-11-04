package com.codex.plunder.sepulchre;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the game says in the Hallowed Sepulchre that a run is timed by. The wording is the one
 * RuneLite's own Chat Commands plugin reads for its personal bests and completion counts
 * (ChatCommandsPlugin: HS_PB_PATTERN, HS_KC_FLOOR_PATTERN and HS_KC_GHC_PATTERN, with test lines
 * such as "You have completed Floor 5 of the Hallowed Sepulchre! Total completions:
 * &lt;col=ff0000&gt;1,114&lt;/col&gt;.").
 */
public final class SepulchreChat
{
	private static final Pattern FLOOR_DONE = Pattern.compile("You have completed Floor (\\d) of the Hallowed Sepulchre!");
	private static final Pattern FLOOR_TIME = Pattern.compile("Floor (\\d) time: <col=ff0000>([0-9:]+(?:\\.[0-9]+)?)</col>");
	private static final Pattern OVERALL_TIME = Pattern.compile("Overall time: <col=ff0000>([0-9:]+(?:\\.[0-9]+)?)</col>");
	private static final Pattern GRAND = Pattern.compile("You have opened the Grand Hallowed Coffin");

	/** What a message said. */
	public static final class Heard
	{
		/** The floor finished, or 0. */
		public final int floorDone;
		/** The floor whose time was given, or 0. */
		public final int timedFloor;
		/** That floor's time in milliseconds, or -1. */
		public final int floorMs;
		/** The run's overall time in milliseconds, or -1. */
		public final int overallMs;
		/** The Grand Hallowed Coffin was opened. */
		public final boolean grand;

		Heard(int floorDone, int timedFloor, int floorMs, int overallMs, boolean grand)
		{
			this.floorDone = floorDone;
			this.timedFloor = timedFloor;
			this.floorMs = floorMs;
			this.overallMs = overallMs;
			this.grand = grand;
		}
	}

	private SepulchreChat()
	{
	}

	/** What a game message says about the run, or null when it is not one of the Sepulchre's. */
	public static Heard read(String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher done = FLOOR_DONE.matcher(message);
		if (done.find())
		{
			int floor = Integer.parseInt(done.group(1));
			return Floor.of(floor) == null ? null : new Heard(floor, 0, -1, -1, false);
		}
		Matcher time = FLOOR_TIME.matcher(message);
		if (time.find())
		{
			int floor = Integer.parseInt(time.group(1));
			int ms = ms(time.group(2));
			Matcher overall = OVERALL_TIME.matcher(message);
			int all = overall.find() ? ms(overall.group(1)) : -1;
			return Floor.of(floor) == null || ms < 0 ? null : new Heard(0, floor, ms, all, false);
		}
		if (GRAND.matcher(message).find())
		{
			return new Heard(0, 0, -1, -1, true);
		}
		return null;
	}

	/** A time as the game writes it, "1:23.40", "0:58" or "1:02:03.6", in milliseconds; -1 when it cannot be read. */
	static int ms(String time)
	{
		try
		{
			String[] parts = time.split(":");
			double seconds = 0;
			for (int i = 0; i < parts.length; i++)
			{
				double v = i == parts.length - 1 ? Double.parseDouble(parts[i]) : Integer.parseInt(parts[i]);
				seconds = seconds * 60 + v;
			}
			return seconds < 0 || seconds > 86_400 ? -1 : (int) Math.round(seconds * 1000);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
