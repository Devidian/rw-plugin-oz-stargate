package de.omegazirkel.risingworld.stargate.dhd;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class DhdAddressFilterTest {
    private static final List<String> ADDRESSES = List.of("LOCAL123", "REMOTE456");
    private static final Map<String, String> ALIASES = Map.of("REMOTE456", "Atlantis");
    private static final Map<String, String> NAMES = Map.of("LOCAL123", "Zuhause");
    private static final Set<String> LOCAL = Set.of("LOCAL123");

    @Test public void networkIsVisibleByDefaultAndCanBeHidden() {
        assertEquals(ADDRESSES, DhdAddressFilter.visible(ADDRESSES, ALIASES, NAMES, LOCAL, true, ""));
        assertEquals(List.of("LOCAL123"), DhdAddressFilter.visible(ADDRESSES, ALIASES, NAMES, LOCAL, false, ""));
    }

    @Test public void matchesAddressAliasAndPersonalNameWithNetworkSwitch() {
        assertEquals(List.of("REMOTE456"), DhdAddressFilter.visible(ADDRESSES, ALIASES, NAMES, LOCAL, true, "remote"));
        assertEquals(List.of("REMOTE456"), DhdAddressFilter.visible(ADDRESSES, ALIASES, NAMES, LOCAL, true, "atlantis"));
        assertEquals(List.of("LOCAL123"), DhdAddressFilter.visible(ADDRESSES, ALIASES, NAMES, LOCAL, true, "zuhause"));
        assertEquals(List.of(), DhdAddressFilter.visible(ADDRESSES, ALIASES, NAMES, LOCAL, false, "atlantis"));
        assertEquals(ADDRESSES, DhdAddressFilter.visible(ADDRESSES, ALIASES, NAMES, LOCAL, true, "  "));
    }
}
