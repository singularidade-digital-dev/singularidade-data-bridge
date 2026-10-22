package digital.singularidade.databridge.source.jdbc.firebird;

import java.util.ArrayList;
import java.util.List;

/**
 * Decoded {@code RDB$TRIGGER_TYPE} of a table trigger: timing ({@code BEFORE}/{@code AFTER}) and the
 * events it fires on, in declaration order. Multi-event types pack up to three actions in two-bit slots
 * of {@code type + 1}: 1 = INSERT, 2 = UPDATE, 3 = DELETE; the lowest bit is the phase.
 */
public record FirebirdTriggerType(String timing, List<String> events) {

    private static final String[] ACTIONS = {null, "INSERT", "UPDATE", "DELETE"};

    public static FirebirdTriggerType decode(int type) {
        if (type < 1 || type >= 8192) {
            throw new IllegalArgumentException("not a table trigger type: " + type);
        }
        int packed = type + 1;
        String timing = (packed & 1) == 0 ? "BEFORE" : "AFTER";
        List<String> events = new ArrayList<>();
        for (int slot = 1; slot <= 3; slot++) {
            int action = (packed >> (slot * 2 - 1)) & 3;
            if (action == 0) break;
            events.add(ACTIONS[action]);
        }
        if (events.isEmpty()) {
            throw new IllegalArgumentException("not a table trigger type: " + type);
        }
        return new FirebirdTriggerType(timing, List.copyOf(events));
    }

    /** DDL form, e.g. {@code BEFORE INSERT OR UPDATE}. */
    public String clause() {
        return timing + " " + String.join(" OR ", events);
    }
}
