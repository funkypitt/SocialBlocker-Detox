package com.parentcontrol.socialblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UpstreamOrderTest {

    private InetAddress net1, net2, q1, q2, q3, q4;
    private List<InetAddress> network, fallback;
    private UpstreamOrder upstream;

    private static InetAddress ip(String s) throws Exception {
        return InetAddress.getByName(s); // literal address: no lookup
    }

    @Before
    public void setUp() throws Exception {
        net1 = ip("192.168.1.1");
        net2 = ip("fd00::1");
        network = Arrays.asList(net1, net2);
        fallback = new ArrayList<>();
        for (String s : UpstreamOrder.FALLBACK_DNS) fallback.add(ip(s));
        q1 = fallback.get(0); q2 = fallback.get(1); q3 = fallback.get(2); q4 = fallback.get(3);
        upstream = new UpstreamOrder();
        upstream.setServers(network, fallback);
    }

    /** One lookup against servers that answer or not; returns the server that answered. */
    private InetAddress lookup(long now, InetAddress... alive) {
        List<InetAddress> up = Arrays.asList(alive);
        List<InetAddress> failed = new ArrayList<>();
        for (InetAddress server : upstream.order(now)) {
            if (up.contains(server)) {
                upstream.answered(server, failed, now);
                return server;
            }
            failed.add(server);
        }
        return null;
    }

    @Test
    public void fallbackIsQuad9Only() {
        assertEquals(Arrays.asList("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9"),
                Arrays.asList(UpstreamOrder.FALLBACK_DNS));
    }

    @Test
    public void networkFirstThenFallback() {
        assertEquals(Arrays.asList(net1, net2, q1, q2, q3, q4), upstream.order(0));
    }

    @Test
    public void networkStaysFirstWhileItAnswers() {
        for (long t = 0; t < 10; t++) assertEquals(net1, lookup(t * 1000, net1, net2, q1));
        assertEquals(net1, upstream.order(10_000).get(0));
    }

    @Test
    public void secondNetworkServerIsPreferredOnceItAnswered_fallbackStillLast() {
        assertEquals(net2, lookup(0, net2, q1));
        assertEquals(Arrays.asList(net2, net1, q1, q2, q3, q4), upstream.order(1000));
    }

    @Test
    public void fallbackOnlyAfterTheNetworkFailed_thenFirstForAMinuteOnly() {
        // the network's servers are silent: Quad9 answers
        assertEquals(q1, lookup(1000, q1));
        // for a minute Quad9 is asked first, the network's servers stay in the list behind it
        List<InetAddress> during = upstream.order(1000 + UpstreamOrder.NETWORK_RETRY_MS - 1);
        assertEquals(q1, during.get(0));
        assertTrue(during.containsAll(network));
        assertTrue(during.indexOf(net1) > during.indexOf(q4));
        // then the network is tried first again
        List<InetAddress> after = upstream.order(1000 + UpstreamOrder.NETWORK_RETRY_MS);
        assertEquals(Arrays.asList(net1, net2), after.subList(0, 2));
    }

    @Test
    public void backToTheNetworkAfterTheMinute_andItStaysThere() {
        assertEquals(q1, lookup(0, q1));
        long later = UpstreamOrder.NETWORK_RETRY_MS;
        // the network has come back: it answers the retry, and every lookup after it
        assertEquals(net1, lookup(later, net1, net2, q1));
        assertEquals(net1, lookup(later + 1, net1, net2, q1));
        assertEquals(Arrays.asList(net1, net2, q1, q2, q3, q4), upstream.order(later + 2));
    }

    @Test
    public void fallbackNeverBecomesPreferredAcrossManyMinutes() {
        // network dead for five minutes, then alive
        long t = 0;
        for (; t < 5 * 60_000; t += 500) lookup(t, q1, q2);
        // once alive, at most one retry period passes before the network is first again
        InetAddress last = null;
        for (long end = t + UpstreamOrder.NETWORK_RETRY_MS + 1000; t < end; t += 500) last = lookup(t, net1, net2, q1, q2);
        assertEquals(net1, last);
        assertEquals(net1, upstream.order(t).get(0));
    }

    @Test
    public void onlyOneLookupRetriesADeadNetwork() {
        assertEquals(q1, lookup(0, q1));
        long later = UpstreamOrder.NETWORK_RETRY_MS;
        List<InetAddress> probe = upstream.order(later);      // this one tries the network
        List<InetAddress> other = upstream.order(later + 10); // the others do not wait for it
        assertEquals(net1, probe.get(0));
        assertEquals(q1, other.get(0));
        // the retry fails and Quad9 answers: another minute with Quad9 first
        upstream.answered(q1, Arrays.asList(net1, net2), later + 5000);
        assertEquals(q1, upstream.order(later + 5000 + UpstreamOrder.NETWORK_RETRY_MS - 1).get(0));
        assertEquals(net1, upstream.order(later + 5000 + UpstreamOrder.NETWORK_RETRY_MS).get(0));
    }

    @Test
    public void aNetworkAnswerEndsThePeriodAtOnce() {
        assertEquals(q1, lookup(0, q1));
        // Quad9 silent too during the period, a server of the network answers as last resort
        assertEquals(net2, lookup(5000, net2));
        assertEquals(Arrays.asList(net2, net1), upstream.order(5001).subList(0, 2));
    }

    @Test
    public void changeOfNetworkForgetsTheFailure() throws Exception {
        assertEquals(q1, lookup(0, q1));
        InetAddress other = ip("10.20.30.40");
        upstream.setServers(Collections.singletonList(other), fallback);
        assertEquals(other, upstream.order(10).get(0));
    }

    @Test
    public void sameNetworkRefreshedKeepsTheMemory() {
        assertEquals(q1, lookup(0, q1));
        upstream.setServers(new ArrayList<>(network), fallback);
        assertEquals(q1, upstream.order(10).get(0));
    }

    @Test
    public void totalFailureDoesNotPromoteTheFallback() {
        assertEquals(null, lookup(0));
        assertEquals(net1, upstream.order(1).get(0));
    }

    @Test
    public void noNetworkServersKnown_fallbackAlone() {
        upstream.setServers(Collections.<InetAddress>emptyList(), fallback);
        assertEquals(fallback, upstream.order(0));
        assertEquals(q1, lookup(0, q1));
        upstream.setServers(network, fallback);
        assertEquals(net1, upstream.order(1).get(0));
    }

    @Test
    public void networkUsingQuad9ItselfIsNotListedTwice() {
        upstream.setServers(Collections.singletonList(q1), fallback);
        assertEquals(Arrays.asList(q1, q2, q3, q4), upstream.order(0));
        assertTrue(upstream.isNetworkServer(q1));
        assertFalse(upstream.isNetworkServer(q2));
    }
}
