package digital.singularidade.databridge.output;

import java.util.List;

/**
 * A trigger of the table (contract 1.1; filled for Firebird).
 *
 * @param timing     {@code BEFORE} or {@code AFTER}
 * @param events     {@code INSERT}, {@code UPDATE}, {@code DELETE}, in declaration order
 * @param body       source as stored, starting at {@code AS}
 * @param generators generators the trigger depends on
 */
public record Trigger(String name, String timing, List<String> events, int position, boolean active,
                      String body, List<String> generators) {}
