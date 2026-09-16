package com.groupscape;

import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Current slayer task, read passively off client varps/varbits - no slayer log or task widget
 * needs to be open. {@code VarPlayerID#SLAYER_TARGET} is the task id (0/negative both observed
 * as "no task assigned", e.g. between turning in a task and getting a new one); task name and
 * area name are looked up from the game's own DB tables rather than shipping a static id->name
 * table here, the same way RuneLite core's SlayerPlugin resolves them.
 *
 * <p>There is no varbit or DB row that names the assigning master directly - {@code
 * VarbitID#SLAYER_MASTER} is a small ordinal (used only to pick which "tasks completed" counter
 * applies, see the streak switch below), not an index into a master list. The master name is
 * instead captured passively from NPC dialogue text elsewhere (see
 * {@code GroupScapeTrackerPlugin#captureSlayerTaskMasterDialogue}) and snapshotted into {@code
 * masterName} by the caller whenever {@code SLAYER_TARGET}/{@code SLAYER_COUNT_ORIGINAL} change -
 * i.e. a new task was just assigned - or immediately whenever the player talks to a recognized
 * slayer master (including just rechecking an existing task), so a plugin/client restart mid-task
 * only leaves {@code masterName} null until the next such conversation, not until the next
 * reassignment.
 */
public class SlayerTaskState implements ConsumableState {
    private final transient String playerName;
    private final boolean hasTask;
    private final String masterName;
    private final String taskName;
    private final String taskLocation;
    private final int amountRemaining;
    private final int initialAmount;
    private final int points;
    private final int streak;

    /**
     * Mortimer's task modifier, resolved from the raw {@code SLAYER_MODIFIER_ID}/{@code
     * SLAYER_MODIFIER_VALUE}/{@code SLAYER_MODIFIER_NEGATIVE} varbits - {@code null} when no
     * modifier is active. Ids confirmed against the game's own decompiled interface script
     * (`slayer_modifiername_rewardshop`/script9747, which renders this same id/value pair into
     * Mortimer's assignment text) - see {@link #resolveModifierType}.
     */
    private final String modifierType;
    private final Integer modifierValue;
    private final Boolean modifierNegative;

    private final int taskId;

    public SlayerTaskState(String playerName, Client client, String masterName) {
        this(playerName, client, masterName, null);
    }

    /**
     * @param previous the last state successfully pushed for this player, or null. The DB-row
     * lookups in {@link #resolveTaskName} and {@link #resolveAreaName} can transiently come back
     * empty for a real, currently-assigned task (RuneLite core's own SlayerPlugin sees the same
     * thing and simply skips the update that tick rather than clobbering its last-known name -
     * ported here as falling back to {@code previous}'s name/location when the id being resolved
     * hasn't changed, since this class always rebuilds a full immutable snapshot rather than
     * patching one in place).
     */
    public SlayerTaskState(String playerName, Client client, String masterName, SlayerTaskState previous) {
        this.playerName = playerName;

        int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
        this.taskId = taskId;
        this.hasTask = taskId > 0;
        this.amountRemaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
        this.points = client.getVarbitValue(VarbitID.SLAYER_POINTS);

        // Mortimer (and possibly other masters) can modify the assigned count up or down (e.g.
        // "+65 kills") via SLAYER_MODIFIER_ID == 2; SLAYER_COUNT_ORIGINAL alone only reflects the
        // pre-modifier base amount, but SLAYER_COUNT (remaining) already accounts for the modifier,
        // so only the initial/total needs adjusting here. Ported from RuneLite core's SlayerPlugin.
        int initialAmount = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
        int rawModifierId = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID);
        boolean rawModifierNegative = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1;
        int rawModifierValue = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
        if (rawModifierId == 2) {
            initialAmount += rawModifierNegative ? -rawModifierValue : rawModifierValue;
        }
        this.initialAmount = initialAmount;

        this.modifierType = resolveModifierType(rawModifierId);
        this.modifierValue = modifierType != null ? rawModifierValue : null;
        this.modifierNegative = modifierType != null ? rawModifierNegative : null;

        // Krystilia (wilderness) and Mortimer (Managing Miscellania hard diary reward) keep their
        // own separate "tasks completed" counters instead of feeding the regular one - ported
        // 1:1 from RuneLite core's SlayerPlugin streak logic.
        int master = client.getVarbitValue(VarbitID.SLAYER_MASTER);
        switch (master) {
            case 7:
                this.streak = client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
                break;
            case 10:
                this.streak = client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
                break;
            default:
                this.streak = client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
                break;
        }

        if (hasTask) {
            boolean samePreviousTask = previous != null && previous.hasTask && previous.taskId == taskId;

            this.masterName = masterName != null
                    ? masterName
                    : (samePreviousTask ? previous.masterName : resolveMasterNameFromOrdinal(master));

            String resolvedTaskName = resolveTaskName(client, taskId);
            this.taskName = resolvedTaskName != null
                    ? resolvedTaskName
                    : (samePreviousTask ? previous.taskName : null);

            int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
            String resolvedAreaName = resolveAreaName(client, areaId);
            this.taskLocation = resolvedAreaName != null
                    ? resolvedAreaName
                    : (samePreviousTask ? previous.taskLocation : null);
        } else {
            this.masterName = null;
            this.taskName = null;
            this.taskLocation = null;
        }
    }

    /** See {@link #modifierType}'s javadoc. */
    private static String resolveModifierType(int modifierId) {
        switch (modifierId) {
            case 1:
                return "points";
            case 2:
                return "quantity";
            case 3:
                return "clue_rate";
            case 4:
                return "superior_rate";
            case 5:
                return "xp";
            default:
                return null;
        }
    }

    /**
     * {@code VarbitID#SLAYER_MASTER} isn't a full index into the master list (see this class's
     * javadoc), but two of its values are confirmed constants ported from RuneLite core's
     * SlayerPlugin (used there to pick the right streak counter, see the switch above) - reused
     * here as an immediate fallback for these two masters specifically when dialogue capture
     * hasn't named the master yet (e.g. right after a client/plugin restart mid-task), rather than
     * leaving masterName null until the player next talks to them.
     */
    private static String resolveMasterNameFromOrdinal(int master) {
        switch (master) {
            case 7:
                return "Krystilia";
            case 10:
                return "Mortimer";
            default:
                return null;
        }
    }

    /** Task id shared by every DT2-boss slayer assignment (Leviathan, Whisperer, Vardorvis, Duke
     * Sucellus, ...) - which specific boss is actually assigned lives in a separate varbit, see
     * {@link #resolveBossTaskId}. Ported from RuneLite core's SlayerPlugin. */
    private static final int BOSS_TASK_ID = 98;

    private static String resolveTaskName(Client client, int taskId) {
        boolean isBossTask = taskId == BOSS_TASK_ID;
        if (isBossTask) {
            Integer bossTaskId = resolveBossTaskId(client);
            if (bossTaskId == null) return "Boss";
            taskId = bossTaskId;
        }

        List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
        // A boss-task's SLAYER_TARGET_BOSSID can resolve to a SlayerTaskSublist row whose COL_TASK
        // id doesn't (yet, or ever, for this client/game state) have a matching SlayerTask row -
        // seen in practice for Duke Sucellus and Barrows Brothers assignments that never recovered.
        // Same "never leave a hasTask=true boss assignment permanently nameless" fallback as the
        // bossTaskId-not-found case above, rather than surfacing as "Unknown task" forever.
        if (rows.isEmpty()) return isBossTask ? "Boss" : null;

        Object[] fields = client.getDBTableField(rows.get(0), DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
        if (fields.length > 0) return (String) fields[0];
        return isBossTask ? "Boss" : null;
    }

    /**
     * A boss-task assignment ({@code SLAYER_TARGET == BOSS_TASK_ID}) names which boss via {@code
     * VarbitID#SLAYER_TARGET_BOSSID} indexing {@code DBTableID.SlayerTaskSublist} rather than
     * directly via {@code DBTableID.SlayerTask} like every other task - this resolves that
     * indirection to the real {@code SlayerTask} row id. Ported from RuneLite core's SlayerPlugin.
     */
    private static Integer resolveBossTaskId(Client client) {
        int bossVarbitId = client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID);
        List<Integer> rows = client.getDBRowsByValue(
                DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, bossVarbitId);
        if (rows.isEmpty()) return null;

        Object[] fields = client.getDBTableField(rows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0);
        return fields.length > 0 ? (Integer) fields[0] : null;
    }

    private static String resolveAreaName(Client client, int areaId) {
        if (areaId <= 0) return null;

        List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
        if (rows.isEmpty()) return null;

        Object[] fields = client.getDBTableField(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0);
        return fields.length > 0 ? (String) fields[0] : null;
    }

    // Package-visible accessors for GroupScapeTrackerPlugin's transition detection
    // (pushSlayerTaskState comparing `previous` against a freshly-built state) - `get()` only
    // exposes the wire-shaped Map, which doesn't let a caller cheaply inspect a *previous*
    // instance's fields without re-parsing its own output.
    boolean hasTask() {
        return hasTask;
    }

    int taskId() {
        return taskId;
    }

    String masterName() {
        return masterName;
    }

    String taskName() {
        return taskName;
    }

    int amountRemaining() {
        return amountRemaining;
    }

    int initialAmount() {
        return initialAmount;
    }

    int points() {
        return points;
    }

    String modifierType() {
        return modifierType;
    }

    Integer modifierValue() {
        return modifierValue;
    }

    Boolean modifierNegative() {
        return modifierNegative;
    }

    @Override
    public Object get() {
        Map<String, Object> out = new HashMap<>();
        // Always sent explicitly (even when false) so "no task" is unambiguous on the wire,
        // rather than the consumer having to infer it from missing fields.
        out.put("hasTask", hasTask);
        out.put("points", points);
        out.put("streak", streak);

        if (hasTask) {
            out.put("masterName", masterName);
            out.put("taskName", taskName);
            out.put("taskLocation", taskLocation);
            out.put("amountRemaining", amountRemaining);
            out.put("initialAmount", initialAmount);
            if (modifierType != null) {
                out.put("modifierType", modifierType);
                out.put("modifierValue", modifierValue);
                out.put("modifierNegative", modifierNegative);
            }
        }

        return out;
    }

    @Override
    public String whoOwnsThis() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof SlayerTaskState)) return false;

        SlayerTaskState other = (SlayerTaskState) o;
        return hasTask == other.hasTask
                && amountRemaining == other.amountRemaining
                && initialAmount == other.initialAmount
                && points == other.points
                && streak == other.streak
                && Objects.equals(masterName, other.masterName)
                && Objects.equals(taskName, other.taskName)
                && Objects.equals(taskLocation, other.taskLocation)
                && Objects.equals(modifierType, other.modifierType)
                && Objects.equals(modifierValue, other.modifierValue)
                && Objects.equals(modifierNegative, other.modifierNegative);
    }
}
