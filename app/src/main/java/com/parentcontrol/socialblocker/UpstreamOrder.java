package com.parentcontrol.socialblocker;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Decides in which order the upstream resolvers are asked. No Android code
 * here, so the rule can be unit-tested.
 *
 * The rule: the network's own DNS servers come first for every lookup; the
 * public fallback (Quad9) is only asked when none of them answered, and it
 * never becomes the preferred server.
 *
 * A network whose DNS is dead must not cost a full round of timeouts on every
 * lookup, so a failure is remembered for a short while:
 *  - when a lookup had to be answered by the fallback after every server of
 *    the network stayed silent, the fallback is asked first for the next
 *    NETWORK_RETRY_MS (one minute);
 *  - after that the network's servers are tried first again. Only one lookup
 *    makes that attempt: for PROBE_MS the others keep going to the fallback,
 *    so a network that is still dead delays one lookup a minute, not all the
 *    lookups in flight;
 *  - an answer from a server of the network, or a change of network, ends the
 *    period at once.
 * Within each group the server that answered last is asked first.
 */
final class UpstreamOrder {

    /** Quad9 (Swiss non-profit), both families. */
    static final String[] FALLBACK_DNS = {
            "9.9.9.9", "149.112.112.112",
            "2620:fe::fe", "2620:fe::9"
    };
    static final long NETWORK_RETRY_MS = 60_000;
    static final long PROBE_MS = 30_000;

    private List<InetAddress> network = new ArrayList<>();
    private List<InetAddress> fallback = new ArrayList<>();
    private InetAddress lastGoodNetwork;
    private InetAddress lastGoodFallback;
    /** The fallback is asked first until this time; 0 = the network is not known to be silent. */
    private long networkSilentUntil = 0;

    /** The servers of the current network, and the fallback (minus any address the network uses itself). */
    synchronized void setServers(List<InetAddress> networkServers, List<InetAddress> fallbackServers) {
        if (!network.equals(networkServers)) {
            // Another network: what the previous one did says nothing about it.
            networkSilentUntil = 0;
            lastGoodNetwork = null;
        }
        network = new ArrayList<>(networkServers);
        fallback = new ArrayList<>();
        for (InetAddress a : fallbackServers) {
            if (!network.contains(a)) fallback.add(a);
        }
    }

    /** The servers to ask, in order, for a lookup starting at {@code now} (milliseconds, any steady clock). */
    synchronized List<InetAddress> order(long now) {
        boolean fallbackFirst = false;
        if (networkSilentUntil != 0 && !network.isEmpty()) {
            if (now < networkSilentUntil) {
                fallbackFirst = true;
            } else {
                // This lookup tries the network again; the others wait for its verdict.
                networkSilentUntil = now + PROBE_MS;
            }
        }
        List<InetAddress> order = new ArrayList<>();
        if (fallbackFirst) {
            addGroup(order, fallback, lastGoodFallback);
            addGroup(order, network, lastGoodNetwork);
        } else {
            addGroup(order, network, lastGoodNetwork);
            addGroup(order, fallback, lastGoodFallback);
        }
        return order;
    }

    /**
     * {@code server} answered at {@code now}, after the servers in
     * {@code failedBefore} stayed silent for the same lookup.
     */
    synchronized void answered(InetAddress server, Collection<InetAddress> failedBefore, long now) {
        if (network.contains(server)) {
            lastGoodNetwork = server;
            networkSilentUntil = 0;
        } else {
            lastGoodFallback = server;
            if (!network.isEmpty() && failedBefore.containsAll(network)) {
                networkSilentUntil = now + NETWORK_RETRY_MS;
            }
        }
    }

    synchronized boolean isNetworkServer(InetAddress server) {
        return network.contains(server);
    }

    private static void addGroup(List<InetAddress> order, List<InetAddress> group, InetAddress first) {
        if (first != null && group.contains(first)) order.add(first);
        for (InetAddress a : group) {
            if (!order.contains(a)) order.add(a);
        }
    }
}
