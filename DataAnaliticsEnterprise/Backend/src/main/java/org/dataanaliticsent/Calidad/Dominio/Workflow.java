package org.dataanaliticsent.Calidad.Dominio;

import java.util.Map;
import java.util.Set;

public final class Workflow {
    private Workflow() {}
    private static final Map<String, Set<String>> INCIDENT = Map.of(
        "OPEN", Set.of("IN_PROGRESS", "DISMISSED"),
        "IN_PROGRESS", Set.of("RESOLVED", "DISMISSED"),
        "RESOLVED", Set.of("OPEN"), "DISMISSED", Set.of("OPEN"));
    public static boolean incidentTransition(String from, String to) {
        return INCIDENT.getOrDefault(from, Set.of()).contains(to);
    }
}
