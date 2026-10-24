package com.codex.plunder;

import com.codex.plunder.core.Check;
import com.codex.plunder.core.Experience;
import com.codex.plunder.core.Goal;
import com.codex.plunder.core.Here;
import com.codex.plunder.core.Leaks;
import com.codex.plunder.core.Logbook;
import com.codex.plunder.core.NavigatorGoals;
import com.codex.plunder.core.Odds;
import com.codex.plunder.core.Pace;
import com.codex.plunder.core.Plan;
import com.codex.plunder.core.Planner;
import com.codex.plunder.core.Room;
import com.codex.plunder.core.Route;
import com.codex.plunder.core.Run;
import com.codex.plunder.core.RunTracker;
import com.codex.plunder.core.Salvage;
import com.codex.plunder.core.Sceptre;
import com.codex.plunder.core.SceptreWatch;
import com.codex.plunder.core.Session;
import com.codex.plunder.core.SharedBank;
import com.codex.plunder.core.Splits;
import com.codex.plunder.core.Stash;
import com.codex.plunder.core.Step;
import com.codex.plunder.core.Unlock;
import com.codex.plunder.overlay.PilotPlaque;
import com.codex.plunder.overlay.RoomHighlights;
import com.codex.plunder.ui.Actions;
import com.codex.plunder.ui.Format;
import com.codex.plunder.ui.PilotPanel;
import com.codex.plunder.ui.Sigil;
import com.codex.plunder.ui.Theme;
import com.codex.plunder.ui.View;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Codex Plunder Pilot",
	description = "Plans every Pyramid Plunder run for your Thieving level: which rooms to walk through, which urns, chests and sarcophagi are worth the clock, and your sceptre odds",
	tags = {"pyramid plunder", "thieving", "sophanem", "sceptre", "pharaoh", "urn", "minigame", "codex"}
)
public class CodexPlunderPilotPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(CodexPlunderPilotPlugin.class);
	private static final String LOGBOOK_KEY = "logbook";
	/** Where a save this version could not keep whole is put aside, once, before it is written over. */
	private static final String BACKUP_KEY = "logbookBackup";
	/** Past this a save is far bigger than any the pilot writes, and is not copied aside. */
	private static final int BACKUP_MAX = 1_000_000;
	private static final int PYRAMID_REGION = 7749;
	/** About eighteen tiles: the room the player is in, not the ones beside it. */
	private static final int NEAR = 2350;

	private static final Set<Integer> URNS = ImmutableSet.of(ObjectID.NTK_URN_TYPE1_MULTI_1, ObjectID.NTK_URN_TYPE1_MULTI_2,
		ObjectID.NTK_URN_TYPE1_MULTI_3, ObjectID.NTK_URN_TYPE1_MULTI_4, ObjectID.NTK_URN_TYPE1_MULTI_5, ObjectID.NTK_URN_TYPE2_MULTI_6,
		ObjectID.NTK_URN_TYPE2_MULTI_7, ObjectID.NTK_URN_TYPE2_MULTI_8, ObjectID.NTK_URN_TYPE2_MULTI_9, ObjectID.NTK_URN_TYPE2_MULTI_10,
		ObjectID.NTK_URN_TYPE3_MULTI_11, ObjectID.NTK_URN_TYPE3_MULTI_12, ObjectID.NTK_URN_TYPE3_MULTI_13, ObjectID.NTK_URN_TYPE3_MULTI_14,
		ObjectID.NTK_URN_TYPE3_MULTI_15);
	private static final Set<Integer> URNS_CLOSED = ImmutableSet.of(ObjectID.NTK_URN1_CLOSED, ObjectID.NTK_URN2_CLOSED, ObjectID.NTK_URN3_CLOSED);
	private static final Set<Integer> DOORS = ImmutableSet.of(ObjectID.NTK_TOMB_DOOR1, ObjectID.NTK_TOMB_DOOR2, ObjectID.NTK_TOMB_DOOR3, ObjectID.NTK_TOMB_DOOR4);
	private static final Set<Integer> ARTEFACTS = ImmutableSet.of(ItemID.NTK_IVORY_COMB, ItemID.NTK_SCARAB_POTTERY, ItemID.NTK_STATUETTE_POTTERY,
		ItemID.NTK_SEAL_STONE, ItemID.NTK_SCARAB_STONE, ItemID.NTK_STATUETTE_STONE, ItemID.NTK_SEAL_GOLD, ItemID.NTK_SCARAB_GOLD, ItemID.NTK_STATUETTE_GOLD);
	private static final Set<Integer> CHASERS = ImmutableSet.of(NpcID.NTK_SCARAB_SWARM, NpcID.NTK_MUMMY_1, NpcID.NTK_MUMMY_2,
		NpcID.NTK_MUMMY_3, NpcID.NTK_MUMMY_4, NpcID.NTK_MUMMY_5);
	private static final Set<Integer> SCEPTRES = ImmutableSet.of(ItemID.PHARAOHS_SCEPTRE, ItemID.PHARAOHS_SCEPTRE_CHARGED, ItemID.PHARAOHS_SCEPTRE_CHARGED_INITIAL);
	/**
	 * Unverified guess: the player variable named for the sceptre holds its charges. See
	 * {@link Sceptre}: it is only believed once a game message naming the charges agrees with it.
	 */
	private static final int SCEPTRE_CHARGES_VARP = VarPlayerID.PHARAOHS_SCEPTRE;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private CodexPlunderPilotConfig config;

	private final PilotPlaque plaque = new PilotPlaque();
	private final RoomHighlights highlights = new RoomHighlights();
	private final RunTracker tracker = new RunTracker();
	private final List<GameObject> urns = new ArrayList<>();
	private final List<GameObject> chests = new ArrayList<>();
	private final List<GameObject> sarcophagi = new ArrayList<>();
	private final List<GameObject> traps = new ArrayList<>();
	private final List<WallObject> doors = new ArrayList<>();
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);

	private PilotPanel panel;
	private NavigationButton navButton;

	private String profile;
	private Logbook logbook;
	private boolean unsaved;
	/** Counts logbook loads, so a slow read that an account switch or shutdown overtook is dropped. */
	private int loadGeneration;
	private boolean dirty;

	/** The room as it stood last tick, to see what changed. */
	private Room lastRoom;
	private int lastClosedUrns = -1;
	private boolean lastChestOpen;
	private boolean lastSarcophagusOpen;

	private Map<Integer, Integer> lastPack;
	/** Tells a sceptre found from one moved, emptied or only just sent with the pack. */
	private final SceptreWatch sceptreWatch = new SceptreWatch();
	private java.util.Set<GameObject> routeOf = java.util.Collections.emptySet();
	private int routeCount = -1;
	private List<GameObject> routeOrder = java.util.Collections.emptyList();
	/** A message named the sceptre's charges; next tick it is held up against the player variable. */
	private boolean compareSceptre;
	/** The sceptre in the bank Codex Bank Steward or Gear Roadmap saved, read per profile and when they save; null when none is saved. */
	private Check.SceptreKit sharedBanked;
	/** The Thieving level as read on the last tick since login, or -1 before the first read. */
	private int seenLevel = -1;
	/** The whole bank another Codex plugin saved, read with {@link #sharedBanked}; null when none is saved. */
	private SharedBank sharedBank;
	/** Codex Navigator's goal board as saved for this profile, or null; and the Thieving levels it needs, read from it. */
	private String navBoard;
	private List<NavigatorGoals.Need> navNeeds = Collections.emptyList();
	private int navLevel = -1;

	@Provides
	CodexPlunderPilotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexPlunderPilotConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new PilotPanel(new PanelActions());
		navButton = NavigationButton.builder()
			.tooltip("Codex Plunder Pilot")
			.icon(Sigil.icon(16))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(plaque);
		overlayManager.add(highlights);
		clientThread.invokeLater(() ->
		{
			loadProfile();
			lastXp.clear();
			seenLevel = -1;
			lastPack = null;
			sceptreWatch.reset();
			publish(null);
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(plaque);
		overlayManager.remove(highlights);
		clientToolbar.removeNavigation(navButton);
		clientThread.invokeLater(() ->
		{
			save();
			// A logbook still being read off the client thread must not come back after this.
			loadGeneration++;
			logbook = null;
			profile = null;
			forgetScene();
		});
		panel = null;
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		// ConfigManager writes its changes to disk in this same event, before a save queued on the
		// client thread runs, so a save made here has to be sent on by hand or it is never written.
		CompletableFuture<Void> done = new CompletableFuture<>();
		clientThread.invokeLater(() ->
		{
			try
			{
				if (unsaved && profile != null && logbook != null)
				{
					save();
					configManager.sendConfig();
				}
			}
			finally
			{
				done.complete(null);
			}
		});
		event.waitFor(done);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case HOPPING:
				// The pack and worn items are sent again on the new world; look afresh rather than count them as found.
				sceptreWatch.reset();
				break;
			case LOADING:
				forgetScene();
				break;
			case LOGIN_SCREEN:
				endRun();
				save();
				lastXp.clear();
				seenLevel = -1;
				lastPack = null;
				// A run ended by logging out is not one the sceptre threw you out of: the pack read
				// on the next login must not be taken for a sceptre found as the run ended.
				sceptreWatch.reset();
				publish(null);
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		save();
		if (configManager.getRSProfileKey() == null)
		{
			// Logging out can leave no profile until the next login. The account's logbook stays in
			// hand, saved, so the panel still shows it and the logout still records the run under way.
			return;
		}
		loadProfile();
		lastXp.clear();
		seenLevel = -1;
		dirty = true;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CodexPlunderPilotConfig.GROUP.equals(event.getGroup()))
		{
			dirty = true;
		}
		else if (NavigatorGoals.GROUP.equals(event.getGroup()) && NavigatorGoals.KEY.equals(event.getKey()))
		{
			clientThread.invokeLater(() ->
			{
				if (profile != null && profile.equals(configManager.getRSProfileKey()))
				{
					readNavigator();
					dirty = true;
				}
			});
		}
		else if (SharedBank.STEWARD_GROUP.equals(event.getGroup()) && SharedBank.STEWARD_KEY.equals(event.getKey())
			|| SharedBank.ROADMAP_GROUP.equals(event.getGroup()) && SharedBank.ROADMAP_KEY.equals(event.getKey()))
		{
			// Another Codex plugin saved the bank; it only counts for the profile in hand.
			clientThread.invokeLater(() ->
			{
				if (profile != null && profile.equals(configManager.getRSProfileKey()) && logbook != null)
				{
					sharedBanked = readSharedBank();
					dirty = true;
				}
			});
		}
	}

	// What is in the room, kept as the scene loads it.

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject o = event.getGameObject();
		int id = o.getId();
		if (URNS.contains(id))
		{
			urns.add(o);
		}
		else if (id == ObjectID.NTK_GOLDEN_CHEST_MULTI)
		{
			chests.add(o);
		}
		else if (id == ObjectID.NTK_SARCOPHAGUS_MULTI)
		{
			sarcophagi.add(o);
		}
		else if (id == ObjectID.NTK_SPEARTRAP_INMOTION)
		{
			traps.add(o);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject o = event.getGameObject();
		urns.remove(o);
		chests.remove(o);
		sarcophagi.remove(o);
		traps.remove(o);
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		if (DOORS.contains(event.getWallObject().getId()))
		{
			doors.add(event.getWallObject());
		}
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		doors.remove(event.getWallObject());
	}

	private void forgetScene()
	{
		urns.clear();
		chests.clear();
		sarcophagi.clear();
		traps.clear();
		doors.clear();
		lastClosedUrns = -1;
	}

	// The sceptre's charges, from what the game says about them.

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (logbook == null || type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM)
		{
			return;
		}
		Integer charges = Sceptre.parse(event.getMessage());
		if (charges == null || carriedSceptre() == Check.SceptreKit.NONE && !event.getMessage().toLowerCase(java.util.Locale.ENGLISH).contains("pharaoh"))
		{
			return;
		}
		logbook.getSceptre().heard(charges);
		compareSceptre = true;
		unsaved = true;
		dirty = true;
	}

	/** The Pharaoh's sceptre in the pack or in hand, charged or not. */
	private Check.SceptreKit carriedSceptre()
	{
		Check.SceptreKit best = Check.SceptreKit.NONE;
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer items = client.getItemContainer(container);
			if (items != null)
			{
				for (Item item : items.getItems())
				{
					best = better(best, sceptreKit(item.getId()));
				}
			}
		}
		return best;
	}

	/** Pharaoh's sceptres in the pack and in hand, of any charge. */
	private int sceptreCount()
	{
		int n = 0;
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer items = client.getItemContainer(container);
			if (items != null)
			{
				for (Item item : items.getItems())
				{
					n += sceptreKit(item.getId()) == Check.SceptreKit.NONE ? 0 : item.getQuantity();
				}
			}
		}
		return n;
	}

	/** What a single item is as a sceptre: not one, an emptied one, or a charged one. */
	private static Check.SceptreKit sceptreKit(int itemId)
	{
		if (itemId == ItemID.PHARAOHS_SCEPTRE_CHARGED || itemId == ItemID.PHARAOHS_SCEPTRE_CHARGED_INITIAL)
		{
			return Check.SceptreKit.CHARGED;
		}
		return itemId == ItemID.PHARAOHS_SCEPTRE ? Check.SceptreKit.UNCHARGED : Check.SceptreKit.NONE;
	}

	/** A charged sceptre beats an emptied one, which beats none. */
	private static Check.SceptreKit better(Check.SceptreKit a, Check.SceptreKit b)
	{
		return b.ordinal() > a.ordinal() ? b : a;
	}

	/** The sceptre waiting in the bank: as this plugin last saw it, else as Codex Bank Steward or Gear Roadmap saved it. */
	private Check.SceptreKit sceptreBanked()
	{
		Check.SceptreKit seen = logbook.getSceptre().getBanked();
		if (seen != null)
		{
			return seen;
		}
		return sharedBanked == null ? Check.SceptreKit.NONE : sharedBanked;
	}

	/**
	 * The sceptre in the bank another Codex plugin last saved: Codex Bank Steward's full bank,
	 * else Codex Gear Roadmap's. Roadmap saves only the gear it knows, which takes in the emptied
	 * and the charged sceptre but not the freshly found one; the Steward's holds everything.
	 * Null when neither is installed or has seen the bank.
	 */
	private Check.SceptreKit readSharedBank()
	{
		SharedBank bank = SharedBank.read(gson,
			configManager.getRSProfileConfiguration(SharedBank.STEWARD_GROUP, SharedBank.STEWARD_KEY),
			configManager.getRSProfileConfiguration(SharedBank.ROADMAP_GROUP, SharedBank.ROADMAP_KEY));
		sharedBank = bank;
		if (bank == null)
		{
			return null;
		}
		Check.SceptreKit kit = Check.SceptreKit.NONE;
		for (int id : bank.ids())
		{
			kit = better(kit, sceptreKit(id));
		}
		return kit;
	}

	// Experience and the pack.

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill == Skill.HITPOINTS || skill == Skill.PRAYER)
		{
			// The ready check reads both.
			dirty = true;
			return;
		}
		if (skill != Skill.THIEVING && skill != Skill.STRENGTH)
		{
			return;
		}
		Integer before = lastXp.put(skill, event.getXp());
		int gained = Experience.gained(before, event.getXp());
		if (gained <= 0)
		{
			return;
		}
		if (skill == Skill.THIEVING)
		{
			tracker.thieving(gained);
		}
		else
		{
			tracker.strength(gained);
		}
		dirty = true;
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		// Write the bank look as the bank closes, so its time is saved before Bank Steward's
		// snapshot of the same visit can outlive it (the Stash weighs the two by time).
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			save();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK && logbook != null)
		{
			Check.SceptreKit banked = Check.SceptreKit.NONE;
			for (Item item : event.getItemContainer().getItems())
			{
				banked = better(banked, sceptreKit(item.getId()));
			}
			if (logbook.getSceptre().setBanked(banked))
			{
				unsaved = true;
				dirty = true;
			}
			Map<Integer, Long> artefacts = new HashMap<>();
			for (Item item : event.getItemContainer().getItems())
			{
				if (ARTEFACTS.contains(item.getId()) && item.getQuantity() > 0)
				{
					artefacts.merge(item.getId(), (long) item.getQuantity(), Long::sum);
				}
			}
			if (logbook.setBanked(artefacts, System.currentTimeMillis()))
			{
				unsaved = true;
				dirty = true;
			}
			return;
		}
		if (event.getContainerId() == InventoryID.WORN)
		{
			// The ready check reads a sceptre held in hand.
			dirty = true;
			return;
		}
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		Map<Integer, Integer> pack = pack(event.getItemContainer());
		Map<Integer, Integer> before = lastPack;
		lastPack = pack;
		if (before == null)
		{
			return;
		}
		long value = 0;
		int count = 0;
		for (Map.Entry<Integer, Integer> e : pack.entrySet())
		{
			int gained = e.getValue() - before.getOrDefault(e.getKey(), 0);
			if (gained <= 0)
			{
				continue;
			}
			if (ARTEFACTS.contains(e.getKey()))
			{
				value += (long) gained * itemManager.getItemPrice(e.getKey());
				count += gained;
			}
		}
		if (tracker.isActive())
		{
			tracker.artefacts(count, value);
		}
		dirty = true;
	}

	/** What the pack holds that the ready check and the alarms care about, read each tick. */
	private static final class Carried
	{
		int free = 28;
		boolean charm;
		boolean lockpick;
		int artefacts;
		long artefactValue;
	}

	private Carried carried()
	{
		Carried c = new Carried();
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv == null)
		{
			return c;
		}
		int used = 0;
		for (Item item : inv.getItems())
		{
			int id = item.getId();
			if (id <= 0)
			{
				continue;
			}
			used++;
			if (id == ItemID.SNAKE_FLUTE)
			{
				c.charm = true;
			}
			else if (id == ItemID.LOCKPICK)
			{
				c.lockpick = true;
			}
			else if (ARTEFACTS.contains(id))
			{
				c.artefacts += item.getQuantity();
				c.artefactValue += (long) item.getQuantity() * itemManager.getItemPrice(id);
			}
		}
		c.free = Math.max(0, 28 - used);
		return c;
	}

	/** Whether a scarab swarm or a mummy has gone for the player. */
	private boolean chased(Player player)
	{
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (CHASERS.contains(npc.getId()) && npc.getInteracting() == player)
			{
				return true;
			}
		}
		return false;
	}

	private static Map<Integer, Integer> pack(ItemContainer container)
	{
		Map<Integer, Integer> pack = new HashMap<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (ARTEFACTS.contains(item.getId()) || SCEPTRES.contains(item.getId()))
				{
					pack.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		return pack;
	}

	// Every tick: where the run stands, the plan for the rest, and the next step.

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (logbook == null)
		{
			return;
		}
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		int level = client.getRealSkillLevel(Skill.THIEVING);
		long xp = client.getSkillExperience(Skill.THIEVING);
		if (xp > 0)
		{
			// Only a level gained while watching opens a room: a level read at login, or one gained
			// on another device, is not news.
			if (seenLevel > 0 && level > seenLevel && config.roomOpens())
			{
				announceOpened(Unlock.opened(seenLevel, level, logbook.getGoal(), logbook.getPace()));
			}
			seenLevel = level;
		}
		if (level != logbook.getLevel() || xp != logbook.getXp())
		{
			logbook.seen(level, xp);
			unsaved = true;
			dirty = true;
		}
		if (compareSceptre)
		{
			compareSceptre = false;
			logbook.getSceptre().compare(client.getVarpValue(SCEPTRE_CHARGES_VARP));
		}
		// Counted once a tick, when the pack and the worn items have both caught up. Until the pack
		// has arrived after a login or hop there is nothing to count against.
		int sceptres = client.getItemContainer(InventoryID.INV) == null ? -1 : sceptreCount();
		SceptreWatch.Seen seen = sceptreWatch.tick(sceptres, tracker.isActive(), client.getTickCount());
		if (seen == SceptreWatch.Seen.FOUND)
		{
			tracker.sceptre();
		}
		else if (seen == SceptreWatch.Seen.FOUND_AFTER)
		{
			logbook.sceptreAfter();
			unsaved = true;
			save();
		}
		dirty |= seen != SceptreWatch.Seen.NOTHING;
		// Experience not sent yet reads 0; taken as the start, the real total would count as one gain.
		baseline(Skill.THIEVING);
		baseline(Skill.STRENGTH);
		if (lastPack == null && client.getItemContainer(InventoryID.INV) != null)
		{
			lastPack = pack(client.getItemContainer(InventoryID.INV));
		}

		int now = client.getTickCount();
		int timer = client.getVarbitValue(VarbitID.NTK_PLAYER_TIMER_COUNT);
		boolean inPyramid = player.getWorldLocation().getRegionID() == PYRAMID_REGION && timer > 0;
		Room room = Room.of(client.getVarbitValue(VarbitID.NTK_ROOM_NUMBER));
		if (!inPyramid || room == null)
		{
			if (tracker.isActive())
			{
				endRun();
			}
			lobbyPlaque(player.getWorldLocation().getRegionID() == PYRAMID_REGION && timer == 0, level);
			if (dirty)
			{
				publish(null);
			}
			return;
		}
		Pace pace = logbook.getPace();
		if (!tracker.isActive())
		{
			tracker.begin(System.currentTimeMillis(), now - timer, logbook.getGoal());
			lastRoom = null;
		}
		tracker.room(room, now, pace);

		LocalPoint me = player.getLocalLocation();
		int plane = client.getTopLevelWorldView().getPlane();
		Map<Integer, Boolean> slots = new HashMap<>();
		List<GameObject> closedUrns = new ArrayList<>();
		for (GameObject urn : urns)
		{
			if (!near(urn, me, plane))
			{
				continue;
			}
			ObjectComposition def = client.getObjectDefinition(urn.getId());
			ObjectComposition shown = def.getImpostor();
			boolean closed = shown != null && URNS_CLOSED.contains(shown.getId());
			slots.merge(def.getVarbitId(), closed, Boolean::logicalOr);
			if (closed)
			{
				closedUrns.add(urn);
			}
		}
		int urnsLeft;
		if (slots.isEmpty())
		{
			urnsLeft = room.getUrns();
		}
		else
		{
			int closed = 0;
			for (boolean c : slots.values())
			{
				closed += c ? 1 : 0;
			}
			// A room with fewer urns than slots leaves a slot that never opens.
			urnsLeft = Math.max(0, closed - Math.max(0, slots.size() - room.getUrns()));
		}
		boolean chestOpen = opened(chests, ObjectID.NTK_GOLDEN_CHEST_CLOSED, me, plane);
		boolean sarcophagusOpen = opened(sarcophagi, ObjectID.NTK_SARCOPHAGUS, me, plane);
		if (room == lastRoom)
		{
			if (lastClosedUrns >= 0)
			{
				for (int i = urnsLeft; i < lastClosedUrns; i++)
				{
					tracker.urnSearched(now, pace);
				}
			}
			if (chestOpen && !lastChestOpen)
			{
				tracker.chestOpened(now);
			}
			if (sarcophagusOpen && !lastSarcophagusOpen)
			{
				tracker.sarcophagusOpened(now);
			}
		}
		lastRoom = room;
		lastClosedUrns = slots.isEmpty() ? -1 : urnsLeft;
		lastChestOpen = chestOpen;
		lastSarcophagusOpen = sarcophagusOpen;

		List<WallObject> untried = new ArrayList<>();
		Set<Integer> doorSlots = new java.util.HashSet<>();
		Set<Integer> triedSlots = new java.util.HashSet<>();
		for (WallObject door : doors)
		{
			if (!near(door, me, plane))
			{
				continue;
			}
			ObjectComposition def = client.getObjectDefinition(door.getId());
			ObjectComposition shown = def.getImpostor();
			doorSlots.add(def.getVarbitId());
			if (shown != null && shown.getId() == ObjectID.NTK_TOMB_DOOR_NOANIM)
			{
				untried.add(door);
			}
			else
			{
				triedSlots.add(def.getVarbitId());
			}
		}
		boolean trapActive = client.getVarbitValue(VarbitID.NTK_TRAP_ACTIVE) == 1;
		int ticksLeft = Math.max(0, Planner.RUN_TICKS - timer);
		Here here = new Here(room, urnsLeft, chestOpen, sarcophagusOpen, !trapActive);
		Plan plan = Planner.plan(level, logbook.getGoal(), pace, here, ticksLeft);
		if (plan == null)
		{
			publish(null);
			return;
		}
		Carried pack = carried();
		Step.Alarm alarm = new Step.Alarm(config.packWarning() && pack.free == 0, client.getBoostedSkillLevel(Skill.HITPOINTS) <= Check.LOW_HP,
			config.chaseWarning() && chased(player), client.getRealSkillLevel(Skill.PRAYER) >= 43);
		Step step = Step.next(plan, true, trapActive, triedSlots.size(), alarm);

		List<RoomHighlights.Mark> marks = new ArrayList<>();
		if (config.highlights())
		{
			switch (step.getKind())
			{
				case TRAP:
					traps.stream().filter(t -> near(t, me, plane)).forEach(t -> marks.add(new RoomHighlights.Mark(t, Theme.EMBER, true)));
					break;
				case URNS:
					List<GameObject> route = route(closedUrns, plan.current().getUrns(), player);
					for (int i = 0; i < route.size(); i++)
					{
						marks.add(new RoomHighlights.Mark(route.get(i), i == 0 ? Theme.GOLD_BRIGHT : Theme.GOLD, false, String.valueOf(i + 1)));
					}
					break;
				case CHEST:
					chests.stream().filter(c -> near(c, me, plane)).forEach(c -> marks.add(new RoomHighlights.Mark(c, Theme.TEAL, false)));
					break;
				case SARCOPHAGUS:
					sarcophagi.stream().filter(c -> near(c, me, plane)).forEach(c -> marks.add(new RoomHighlights.Mark(c, Theme.VIOLET, false)));
					break;
				case DOOR:
					untried.forEach(d -> marks.add(new RoomHighlights.Mark(d, Theme.GOLD, true)));
					break;
				default:
					break;
			}
		}
		highlights.show(marks);

		if (config.plaque())
		{
			Color color = step.getKind() == Step.Kind.DANGER ? Theme.BLOOD : Theme.clock(ticksLeft);
			// For the first moments in a room its split against the best run shows on the plaque.
			Integer split = liveSplit(room);
			boolean flash = split != null && tracker.elapsed(now) - tracker.enteredAt(room) < SPLIT_FLASH_TICKS;
			plaque.show("R" + room.getNumber() + "  " + Format.clock(ticksLeft), step.getTitle(), step.getDetail(), color,
				ticksLeft / (double) Planner.RUN_TICKS, flash ? Format.split(split) : null, flash ? splitColor(split) : null);
		}
		else
		{
			plaque.show(null, null, null, null, 0);
		}
		publish(new Live(plan, step, ticksLeft));
	}

	/** Notes the experience a skill starts from, once it has been sent. */
	private void baseline(Skill skill)
	{
		int xp = client.getSkillExperience(skill);
		if (Experience.sent(xp, client.getSkillExperience(Skill.HITPOINTS)))
		{
			lastXp.putIfAbsent(skill, xp);
		}
	}

	/**
	 * In the pyramid before a run, the ready check's most urgent point on the plaque, so a full
	 * pack or low hitpoints is seen without the panel open. Hidden anywhere else, or when all is well.
	 */
	private void lobbyPlaque(boolean inLobby, int level)
	{
		Check urgent = null;
		if (inLobby && config.readyPlaque())
		{
			List<Check> checks = readyChecks(level);
			urgent = checks == null ? null : Check.mostUrgent(checks);
		}
		if (urgent == null)
		{
			plaque.show(null, null, null, null, 0);
			return;
		}
		boolean warn = urgent.getState() == Check.State.WARN;
		plaque.show(warn ? "Before you go" : "Tip", urgent.getTitle(), urgent.getDetail(), warn ? Theme.EMBER : Theme.GOLD, 1);
	}

	/** The ready check for a fresh run at this level, or null below the first room. Client thread only. */
	private List<Check> readyChecks(int level)
	{
		Plan fresh = Planner.fresh(level, logbook.getGoal(), logbook.getPace());
		if (fresh == null)
		{
			return null;
		}
		Carried pack = carried();
		Check.SceptreKit sceptre = carriedSceptre();
		int charges = sceptre == Check.SceptreKit.CHARGED ? logbook.getSceptre().charges(client.getVarpValue(SCEPTRE_CHARGES_VARP)) : 0;
		Check.Kit kit = new Check.Kit(level, client.getRealSkillLevel(Skill.PRAYER), client.getBoostedSkillLevel(Skill.HITPOINTS),
			pack.free, pack.charm, pack.lockpick).sceptre(sceptre, charges, logbook.getSceptre().isLive(), sceptreBanked());
		return Check.ready(kit, fresh, logbook.slotsNeeded(fresh, 10));
	}

	/**
	 * The urns worth searching in walking order. The walk is worked out again only when an urn
	 * is searched, so the numbers hold still while the player crosses the room.
	 */
	private List<GameObject> route(List<GameObject> closed, int count, Player player)
	{
		java.util.Set<GameObject> now = new java.util.HashSet<>(closed);
		if (now.equals(routeOf) && routeCount == count)
		{
			return routeOrder;
		}
		int[] xs = new int[closed.size()];
		int[] ys = new int[closed.size()];
		for (int i = 0; i < closed.size(); i++)
		{
			xs[i] = closed.get(i).getWorldLocation().getX();
			ys[i] = closed.get(i).getWorldLocation().getY();
		}
		List<GameObject> order = new ArrayList<>();
		for (int i : Route.order(player.getWorldLocation().getX(), player.getWorldLocation().getY(), xs, ys, count))
		{
			order.add(closed.get(i));
		}
		routeOf = now;
		routeCount = count;
		routeOrder = order;
		return order;
	}

	private static boolean near(TileObject o, LocalPoint me, int plane)
	{
		return o.getPlane() == plane && o.getLocalLocation().distanceTo(me) < NEAR;
	}

	/** Whether the room's chest or sarcophagus has been opened; they all share one state. */
	private boolean opened(List<GameObject> objects, int closedId, LocalPoint me, int plane)
	{
		for (GameObject o : objects)
		{
			if (near(o, me, plane))
			{
				ObjectComposition shown = client.getObjectDefinition(o.getId()).getImpostor();
				return shown != null && shown.getId() != closedId;
			}
		}
		return false;
	}

	private void endRun()
	{
		highlights.show(null);
		plaque.show(null, null, null, null, 0);
		lastRoom = null;
		lastClosedUrns = -1;
		if (!tracker.isActive())
		{
			return;
		}
		long[] opened = tracker.getOpened();
		Run run = tracker.finish(client.getTickCount());
		sceptreWatch.runEnded(client.getTickCount());
		if (run == null || logbook == null)
		{
			return;
		}
		Room top = Room.highest(logbook.getLevel());
		Splits before = splits(run.getGoal(), top).all;
		boolean best = before.hasBest() && before.isNewBest(run, run.getGoal());
		int gained = best ? before.bestAt(top) - run.splitAt(top) : 0;
		logbook.record(run, opened);
		unsaved = true;
		save();
		dirty = true;
		if (config.summary())
		{
			ChatMessageBuilder message = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("Plunder Pilot: ")
				.append(ChatColorType.NORMAL)
				.append("room " + run.getDeepest() + ", +" + String.format("%,d", run.getThievingXp()) + " Thieving xp");
			if (run.getArtefacts() > 0)
			{
				message.append(", " + Format.coins(run.getArtefacts()) + " gp of artefacts");
			}
			if (run.getChests() + run.getSarcophagi() > 0)
			{
				message.append(". Sceptre odds this run " + Format.oneIn(run.getSceptreChance()));
			}
			if (best)
			{
				message.append(". New best to room " + top.getNumber() + ": " + Format.clock(run.splitAt(top)) + ", "
					+ Format.seconds(gained) + " faster");
			}
			else
			{
				Room slow = before.slowest(run);
				if (slow != null)
				{
					message.append(". Room " + slow.getNumber() + " ran " + Format.seconds(before.overrun(run, slow)) + " over your quickest");
				}
			}
			Session session = Session.latest(logbook.getRuns(), System.currentTimeMillis());
			if (session.getRuns().size() >= 2)
			{
				message.append(". Session: " + session.getRuns().size() + " runs, " + Format.amount(session.xpPerHour()) + " xp/hr");
			}
			long toNext = Unlock.runsToNext(logbook.getLevel(), logbook.getXp(), run.getThievingXp());
			if (toNext > 0 && toNext <= Unlock.SOON_RUNS)
			{
				Room next = Room.highest(Room.nextUnlock(logbook.getLevel()));
				message.append(". Room " + next.getNumber() + " opens " + (toNext == 1 ? "after one more run like this" : "in about " + toNext + " runs"));
			}
			message.append(".");
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(message.build())
				.build());
		}
	}

	/** A line in the chat box when a level opens a deeper room, with what a run pays there now. */
	private void announceOpened(Unlock.Opened opened)
	{
		if (opened == null)
		{
			return;
		}
		Room room = opened.getRoom();
		StringBuilder text = new StringBuilder("Thieving " + room.getLevel() + " opens room " + room.getNumber() + ". ");
		if (opened.getRunXpBefore() > 0)
		{
			text.append("A run now pays about " + Format.amount(opened.getRunXpNow()) + " xp, up from " + Format.amount(opened.getRunXpBefore())
				+ ": " + Format.amount(opened.getRateNow()) + " xp/hr.");
		}
		else
		{
			text.append("A run pays about " + Format.amount(opened.getRunXpNow()) + " xp: " + Format.amount(opened.getRateNow()) + " xp/hr.");
		}
		chat(text.toString());
	}

	private void chat(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("Plunder Pilot: ")
				.append(ChatColorType.NORMAL)
				.append(text)
				.build())
			.build());
	}

	/** Ticks the room's split stays on the plaque after entering it: about ten seconds. */
	private static final int SPLIT_FLASH_TICKS = 17;

	/**
	 * Ticks ahead (negative) or behind the best run on entering the room the run is in, or null with
	 * nothing to compare. Measured against the best for the goal the run began with, which is the goal
	 * it will be saved under.
	 */
	private Integer liveSplit(Room in)
	{
		if (in == null || in.ordinal() == 0 || tracker.getGoal() == null || logbook == null)
		{
			return null;
		}
		return splits(tracker.getGoal(), Room.highest(logbook.getLevel())).all.delta(in, tracker.enteredAt(in));
	}

	/** Green ahead of the best, ember behind, muted when level with it. */
	private static Color splitColor(int ticks)
	{
		return Math.round(Math.abs(ticks) * 0.6) == 0 ? Theme.TEXT_MUTED : ticks < 0 ? Theme.GREEN : Theme.EMBER;
	}

	/** The splits for a goal at the deepest room, over every run and over the runs before the newest comparable one. */
	private static final class SplitBook
	{
		final Splits all;
		final Splits prior;
		final Run last;

		SplitBook(Splits all, Splits prior, Run last)
		{
			this.all = all;
			this.prior = prior;
			this.last = last;
		}
	}

	/**
	 * Books by goal and deepest room, worked out again only when a run is added. More than one is
	 * kept because a run whose goal was changed part way is measured under the goal it began with
	 * while the Runs page shows the new one, and both are asked for every tick of the run.
	 */
	private final Map<String, SplitBook> splitBooks = new HashMap<>();
	private long splitBooksRuns = -1;

	private SplitBook splits(Goal goal, Room top)
	{
		long runsNow = logbook.getTotalRuns() * 1000L + logbook.getRuns().size();
		if (runsNow != splitBooksRuns)
		{
			splitBooks.clear();
			splitBooksRuns = runsNow;
		}
		String key = goal + "|" + top;
		SplitBook splitBook = splitBooks.get(key);
		if (splitBook == null)
		{
			List<Run> runs = logbook.getRuns();
			int last = -1;
			for (int i = runs.size() - 1; i >= 0; i--)
			{
				if (Splits.comparable(runs.get(i), goal, top))
				{
					last = i;
					break;
				}
			}
			Splits all = Splits.of(runs, goal, top);
			Splits prior = last < 0 ? all : Splits.of(runs.subList(0, last), goal, top);
			splitBook = new SplitBook(all, prior, last < 0 ? null : runs.get(last));
			splitBooks.put(key, splitBook);
		}
		return splitBook;
	}

	// Saving.

	private void loadProfile()
	{
		profile = configManager.getRSProfileKey();
		logbook = null;
		sharedBanked = null;
		sharedBank = null;
		splitBooks.clear();
		splitBooksRuns = -1;
		int generation = ++loadGeneration;
		if (profile == null)
		{
			return;
		}
		String saved = configManager.getRSProfileConfiguration(CodexPlunderPilotConfig.GROUP, LOGBOOK_KEY);
		if (saved == null || saved.length() < Logbook.LARGE)
		{
			Logbook read = Logbook.read(gson, saved);
			loaded(profile, saved, read, Salvage.wouldLose(gson, saved, read));
		}
		else
		{
			// A long logbook (many runs, or a hand-edited save) takes milliseconds to tens of them to
			// read, so it is read off the client thread and taken up on it. Until then the pilot shows
			// and records nothing, as when logged out.
			String forProfile = profile;
			executor.execute(() ->
			{
				Logbook got;
				boolean loses;
				try
				{
					got = Logbook.read(gson, saved);
					loses = Salvage.wouldLose(gson, saved, got);
				}
				catch (RuntimeException e)
				{
					log.warn("Could not read the saved logbook", e);
					got = new Logbook();
					loses = true;
				}
				Logbook read = got;
				boolean lose = loses;
				clientThread.invokeLater(() ->
				{
					// Not if the account changed, or the plugin was turned off, while it was read.
					if (generation == loadGeneration && forProfile.equals(profile))
					{
						loaded(forProfile, saved, read, lose);
						dirty = true;
					}
				});
			});
		}
		sharedBanked = readSharedBank();
		readNavigator();
		unsaved = false;
		dirty = true;
	}

	/**
	 * Takes up the logbook read from {@code saved}, first putting {@code saved} aside when saving
	 * over it would lose something ({@code lose}): a newer version's fields, or damage mended.
	 */
	private void loaded(String forProfile, String saved, Logbook read, boolean lose)
	{
		if (lose && saved.length() <= BACKUP_MAX)
		{
			log.warn("The saved logbook holds what this version cannot keep; a copy is kept under {}", BACKUP_KEY);
			configManager.setConfiguration(CodexPlunderPilotConfig.GROUP, forProfile, BACKUP_KEY, saved);
		}
		logbook = read;
		unsaved = false;
	}

	/** Reads Codex Navigator's goal board for this profile; never writes it. */
	private void readNavigator()
	{
		navBoard = profile == null ? null : configManager.getRSProfileConfiguration(NavigatorGoals.GROUP, NavigatorGoals.KEY);
		navLevel = -1;
	}

	/** The Thieving levels the goals need above the level now, read again only when the board or level changes. */
	private List<NavigatorGoals.Need> navigatorNeeds(int level)
	{
		if (level != navLevel)
		{
			navLevel = level;
			navNeeds = NavigatorGoals.needs(gson, navBoard, "Thieving", level);
		}
		return navNeeds;
	}

	private void save()
	{
		if (profile == null || logbook == null || !unsaved)
		{
			return;
		}
		unsaved = false;
		configManager.setConfiguration(CodexPlunderPilotConfig.GROUP, profile, LOGBOOK_KEY, gson.toJson(logbook));
	}

	// Handing the panel what it shows.

	private static final class Live
	{
		final Plan plan;
		final Step step;
		final int ticksLeft;

		Live(Plan plan, Step step, int ticksLeft)
		{
			this.plan = plan;
			this.step = step;
			this.ticksLeft = ticksLeft;
		}
	}

	private void publish(Live live)
	{
		dirty = false;
		PilotPanel target = panel;
		if (target == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
		if (logbook == null || !loggedIn && logbook.getLevel() == 0)
		{
			SwingUtilities.invokeLater(() -> target.update(View.loggedOut()));
			return;
		}
		int level = logbook.getLevel();
		Goal goal = logbook.getGoal();
		Pace pace = logbook.getPace();
		SplitBook book = splits(goal, Room.highest(Math.max(Room.ENTRY_LEVEL, level)));
		View.Record record = new View.Record(new ArrayList<>(logbook.getRuns()), logbook.getOpened(), logbook.getSceptres(),
			logbook.getTotalRuns(), logbook.getTotalXp(), logbook.getTotalArtefacts(), logbook.recentRate(10), pace.getUrnSamples())
			.splits(book.prior, book.last)
			.leaks(level >= Room.ENTRY_LEVEL ? Leaks.of(logbook.getRuns(), goal, level, pace) : null)
			.session(Session.latest(logbook.getRuns(), now))
			.history(Session.history(logbook.getRuns(), now));
		String name = loggedIn ? client.getLocalPlayer().getName() : null;
		Plan fresh = Planner.fresh(level, goal, pace);
		View view;
		if (fresh == null)
		{
			view = new View(View.State.TOO_LOW, name, level, logbook.getXp(), config.goalLevel(), goal, null, null, null, null, null, record, now);
		}
		else
		{
			List<Plan> ladder = new ArrayList<>();
			for (Room r : Room.values())
			{
				ladder.add(Planner.fresh(r.getLevel(), goal, pace));
			}
			Plan plan = live == null ? fresh : live.plan;
			Step step = live == null ? Step.next(fresh, false, false, 0) : live.step;
			Integer split = live == null ? null : liveSplit(tracker.getRoom());
			View.Live run = live == null ? null : new View.Live(live.ticksLeft, tracker.getThievingXp(), tracker.getArtefacts(),
				tracker.getChests() + tracker.getSarcophagi(), Odds.atLeastOne(tracker.getOpened()), split);
			view = new View(View.State.READY, name, level, logbook.getXp(), config.goalLevel(), goal, plan, fresh, ladder, step, run, record, now);
			if (loggedIn)
			{
				Carried pack = carried();
				view.ready(new View.Ready(readyChecks(level), pack.artefacts, pack.artefactValue));
			}
		}
		view.stash(stash()).goals(navigatorNeeds(level)).nameOnCard(config.nameOnCard());
		SwingUtilities.invokeLater(() -> target.update(view));
	}

	/** The artefacts in the bank, valued at the Grand Exchange, from whichever look at the bank is newer. */
	private View.Stash stash()
	{
		Stash stash = Stash.pick(logbook.getBanked(), logbook.getBankedAt(), sharedBank, ARTEFACTS);
		if (stash == null)
		{
			return null;
		}
		List<View.Stash.Line> lines = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : stash.getItems().entrySet())
		{
			int id = e.getKey();
			lines.add(new View.Stash.Line(id, itemManager.getItemComposition(id).getName(), e.getValue(), e.getValue() * itemManager.getItemPrice(id)));
		}
		// The most valuable first.
		lines.sort((a, b) -> a.value != b.value ? Long.compare(b.value, a.value) : Integer.compare(a.itemId, b.itemId));
		return new View.Stash(lines, stash.getSeenAt(), stash.getFrom() == SharedBank.Source.BANK_STEWARD ? "Codex Bank Steward" : null);
	}

	private final class PanelActions implements Actions
	{
		@Override
		public void goal(Goal goal)
		{
			clientThread.invokeLater(() ->
			{
				if (logbook != null && logbook.getGoal() != goal)
				{
					logbook.setGoal(goal);
					unsaved = true;
					save();
					// During a run the next tick replans with the new goal.
					if (tracker.isActive())
					{
						dirty = true;
					}
					else
					{
						publish(null);
					}
				}
			});
		}
	}
}
