package de.omegazirkel.risingworld.stargate.dhd;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One result set for DHD display, page navigation, selection and dialing. */
final class DhdAddressFilter {
    private DhdAddressFilter() { }

    static List<String> visible(List<String> addresses, Map<String, String> aliases,
            Map<String, String> personalNames, Set<String> localAddresses,
            boolean showNetwork, String search) {
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return addresses.stream().filter(address ->
                (showNetwork || localAddresses.contains(address))
                && (query.isEmpty() || address.toLowerCase(Locale.ROOT).contains(query)
                    || aliases.getOrDefault(address, "").toLowerCase(Locale.ROOT).contains(query)
                    || personalNames.getOrDefault(address, "").toLowerCase(Locale.ROOT).contains(query)))
                .toList();
    }
}
