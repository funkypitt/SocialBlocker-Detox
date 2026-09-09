package com.parentcontrol.socialblocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DNS-intercepting VPN service with per-platform blocking.
 *
 * Only DNS traffic (UDP 53) is routed through the TUN via a virtual DNS IP
 * (10.0.0.1). All other traffic flows normally — zero interference.
 *
 * Network robustness notes (learned the hard way on US carriers):
 *  - Android blocks every address family the VPN does not provide an address
 *    for. With an IPv4-only TUN that silently cut all IPv6 traffic, which on
 *    IPv6-only mobile networks (T-Mobile US and friends, 464XLAT) meant the
 *    whole phone lost connectivity. We now explicitly let IPv6 bypass the VPN.
 *  - Upstream queries go to the underlying network's own resolvers first
 *    (IPv4 or IPv6), then to public resolvers of both families. A hard-coded
 *    8.8.8.8 is useless on networks that block or lack IPv4 transit.
 *  - Queries are answered from a thread pool so one slow resolver cannot
 *    stall every DNS lookup on the device.
 */
public class SocialBlockerVpnService extends VpnService {

    private static final String TAG = "SocialBlockerVPN";
    private static final String CHANNEL_ID = "social_blocker_vpn";
    private static final int NOTIFICATION_ID = 1;

    private static final String VPN_ADDRESS = "10.0.0.2";
    private static final String VPN_DNS = "10.0.0.1";
    private static final int DNS_PORT = 53;

    /** Public resolvers tried after the underlying network's own servers. */
    private static final String[] FALLBACK_DNS = {
            "8.8.8.8", "1.1.1.1",
            "2001:4860:4860::8888", "2606:4700:4700::1111"
    };
    private static final int UPSTREAM_TIMEOUT_MS = 2500;
    private static final long UPSTREAM_CACHE_MS = 15_000;
    private static final int RESOLVER_THREADS = 8;

    private ParcelFileDescriptor vpnInterface;
    private volatile boolean running = false;
    private ExecutorService resolverPool;
    private final Object tunWriteLock = new Object();

    // Cached view of the underlying (non-VPN) network and its resolvers.
    private volatile Network underlyingNetwork;
    private volatile List<InetAddress> upstreamServers = new ArrayList<>();
    private volatile long upstreamRefreshedAt = 0;
    private volatile InetAddress lastGoodUpstream;

    // ---- Per-platform domain lists ----

    private static final Set<String> YOUTUBE_DOMAINS = new HashSet<>(Arrays.asList(
            "youtube.com", "www.youtube.com", "m.youtube.com",
            "music.youtube.com", "tv.youtube.com", "studio.youtube.com",
            "youtu.be", "www.youtu.be", "yt.be",
            "youtube-nocookie.com", "www.youtube-nocookie.com",
            "youtubei.googleapis.com", "youtube.googleapis.com",
            "youtubeembedded-pa.googleapis.com",
            "youtube-ui.l.google.com", "youtube.l.google.com",
            "ytimg.com", "i.ytimg.com", "s.ytimg.com", "ytimg.l.google.com",
            "yt3.ggpht.com", "yt3.googleusercontent.com",
            "googlevideo.com", "www.googlevideo.com",
            "youtubeeducation.com", "youtubekids.com",
            "yti.google.com", "wide-youtube.l.google.com",
            "youtubeads.l.google.com", "yt-video-upload.l.google.com"
    ));

    private static final Set<String> INSTAGRAM_DOMAINS = new HashSet<>(Arrays.asList(
            "instagram.com", "www.instagram.com",
            "i.instagram.com", "graph.instagram.com",
            "cdninstagram.com", "scontent.cdninstagram.com",
            "ig.me", "ig.com",
            "instagram.c10r.facebook.com",
            "edge-chat.instagram.com",
            "scontent-cdg4-3.cdninstagram.com"
    ));

    private static final Set<String> TIKTOK_DOMAINS = new HashSet<>(Arrays.asList(
            "tiktok.com", "www.tiktok.com", "m.tiktok.com",
            "tiktokv.com", "www.tiktokv.com",
            "tiktokcdn.com", "tiktokcdn-us.com",
            "muscdn.com",
            "musical.ly", "www.musical.ly",
            "p16-sign-sg.tiktokcdn.com",
            "v16-webapp-prime.tiktok.com",
            "mon.tiktokv.com",
            "log.tiktokv.com",
            "pull-l3-hs.pstatp.com",
            "sf16-muse-va.ibytedtos.com",
            "lf16-tiktok-common.tiktokcdn-us.com"
    ));

    private static final Set<String> REDDIT_DOMAINS = new HashSet<>(Arrays.asList(
            "reddit.com", "www.reddit.com", "m.reddit.com",
            "old.reddit.com", "new.reddit.com", "np.reddit.com",
            "out.reddit.com", "oauth.reddit.com", "gateway.reddit.com",
            "redd.it", "i.redd.it", "v.redd.it",
            "redditstatic.com", "www.redditstatic.com",
            "redditmedia.com", "i.redditmedia.com",
            "reddit.map.fastly.net", "redditinc.com"
    ));

    private static final Set<String> X_DOMAINS = new HashSet<>(Arrays.asList(
            "x.com", "www.x.com", "mobile.x.com", "api.x.com",
            "twitter.com", "www.twitter.com", "m.twitter.com", "mobile.twitter.com",
            "api.twitter.com", "twttr.com",
            "t.co",
            "twimg.com", "abs.twimg.com", "abs-0.twimg.com",
            "pbs.twimg.com", "video.twimg.com",
            "twitter.map.fastly.net"
    ));

    private static final Set<String> SUBSTACK_DOMAINS = new HashSet<>(Arrays.asList(
            "substack.com", "www.substack.com",
            "substackcdn.com", "substackapi.com"
    ));

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        BlockPreferences prefs = new BlockPreferences(this);
        if (!prefs.isBlockingEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startVpn();
        return START_STICKY;
    }

    // ========================= VPN Lifecycle =========================

    private void startVpn() {
        if (running) return;

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        try {
            Builder builder = new Builder();
            builder.setSession("Social Blocker");
            builder.addAddress(VPN_ADDRESS, 32);
            builder.setMtu(1500);
            builder.addDnsServer(VPN_DNS);
            builder.addRoute(VPN_DNS, 32);

            // The TUN only carries IPv4 (our virtual DNS). Without this call
            // Android drops all IPv6 traffic on the device, which breaks
            // IPv6-only carriers entirely. IPv6 DNS still lands on 10.0.0.1
            // because the resolver only uses the VPN's declared DNS servers.
            builder.allowFamily(OsConstants.AF_INET6);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false);
            }

            builder.setBlocking(true);
            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface");
                stopSelf();
                return;
            }

            running = true;
            resolverPool = Executors.newFixedThreadPool(RESOLVER_THREADS);
            refreshUpstreamServers(true);
            new Thread(this::packetLoop, "VPN-PacketLoop").start();
            Log.i(TAG, "VPN started, upstream=" + upstreamServers);

        } catch (Exception e) {
            Log.e(TAG, "Error starting VPN", e);
            stopVpn();
        }
    }

    private void stopVpn() {
        running = false;
        if (resolverPool != null) {
            resolverPool.shutdownNow();
            resolverPool = null;
        }
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (IOException e) { /* ignore */ }
            vpnInterface = null;
        }
        stopForeground(true);
        stopSelf();
    }

    // ========================= Packet Loop =========================

    private void packetLoop() {
        FileInputStream tunIn = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream tunOut = new FileOutputStream(vpnInterface.getFileDescriptor());
        byte[] packet = new byte[32767];
        BlockPreferences prefs = new BlockPreferences(this);

        while (running) {
            try {
                int length = tunIn.read(packet);
                if (length <= 0) { Thread.sleep(10); continue; }
                // Copy: the buffer is reused by the next read while the
                // resolver thread is still working on this query.
                final byte[] copy = Arrays.copyOf(packet, length);
                final ExecutorService pool = resolverPool;
                if (pool == null) break;
                try {
                    pool.execute(() -> handlePacket(copy, copy.length, tunOut, prefs));
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    break;
                }
            } catch (IOException e) {
                if (running) Log.e(TAG, "Packet loop I/O error", e);
                break;
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void handlePacket(byte[] packet, int length, FileOutputStream tunOut, BlockPreferences prefs) {
        try {
            if (length < 20) return;
            if (((packet[0] >> 4) & 0xF) != 4) return;

            int ipHeaderLen = (packet[0] & 0xF) * 4;
            if ((packet[9] & 0xFF) != 17) return; // UDP only
            if (length < ipHeaderLen + 8) return;

            int srcPort = readU16(packet, ipHeaderLen);
            int dstPort = readU16(packet, ipHeaderLen + 2);

            if (dstPort == DNS_PORT) {
                handleDnsQuery(packet, length, ipHeaderLen, srcPort, tunOut, prefs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling packet", e);
        }
    }

    // ========================= DNS Handling =========================

    private void handleDnsQuery(byte[] packet, int length, int ipHeaderLen,
                                int srcPort, FileOutputStream tunOut, BlockPreferences prefs) throws IOException {
        int udpDataOffset = ipHeaderLen + 8;
        int udpDataLength = length - udpDataOffset;
        if (udpDataLength < 12) return;

        byte[] dnsQuery = new byte[udpDataLength];
        System.arraycopy(packet, udpDataOffset, dnsQuery, 0, udpDataLength);

        String domain = extractDomainName(dnsQuery);
        if (domain == null) return;

        boolean shouldBlock = isDomainBlocked(domain, prefs)
                && ScheduleChecker.shouldBlock(prefs.getSchedule());

        byte[] dnsResponse;
        if (shouldBlock) {
            Log.d(TAG, "DNS BLOCKED: " + domain);
            dnsResponse = createBlockedDnsResponse(dnsQuery);
        } else {
            dnsResponse = forwardDnsQuery(dnsQuery);
        }

        if (dnsResponse == null) return;

        byte[] responsePacket = buildUdpResponsePacket(packet, ipHeaderLen, srcPort, dnsResponse);
        if (responsePacket != null) {
            synchronized (tunWriteLock) {
                if (!running) return;
                tunOut.write(responsePacket);
                tunOut.flush();
            }
        }
    }

    // ========================= Domain Matching =========================

    private boolean isDomainBlocked(String domain, BlockPreferences prefs) {
        if (domain == null || domain.isEmpty()) return false;

        if (prefs.isYoutubeBlocked() && matchesDomainSet(domain, YOUTUBE_DOMAINS)) return true;
        if (prefs.isInstagramBlocked() && matchesDomainSet(domain, INSTAGRAM_DOMAINS)) return true;
        if (prefs.isTiktokBlocked() && matchesDomainSet(domain, TIKTOK_DOMAINS)) return true;
        if (prefs.isRedditBlocked() && matchesDomainSet(domain, REDDIT_DOMAINS)) return true;
        if (prefs.isXBlocked() && matchesDomainSet(domain, X_DOMAINS)) return true;
        if (prefs.isSubstackBlocked() && matchesDomainSet(domain, SUBSTACK_DOMAINS)) return true;

        return false;
    }

    /** Check exact match and all parent domains (e.g. x.y.tiktok.com matches tiktok.com) */
    private boolean matchesDomainSet(String domain, Set<String> domainSet) {
        String check = domain;
        while (check.contains(".")) {
            if (domainSet.contains(check)) return true;
            check = check.substring(check.indexOf('.') + 1);
        }
        return domainSet.contains(check);
    }

    // ========================= DNS Parsing =========================

    private String extractDomainName(byte[] dns) {
        try {
            int offset = 12;
            StringBuilder domain = new StringBuilder();
            while (offset < dns.length) {
                int labelLength = dns[offset] & 0xFF;
                if (labelLength == 0) break;
                if ((labelLength & 0xC0) == 0xC0) break;
                if (domain.length() > 0) domain.append('.');
                offset++;
                for (int i = 0; i < labelLength && offset < dns.length; i++) {
                    domain.append((char) (dns[offset] & 0xFF));
                    offset++;
                }
            }
            return domain.toString().toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] createBlockedDnsResponse(byte[] query) {
        try {
            int offset = 12;
            while (offset < query.length && (query[offset] & 0xFF) != 0) {
                int labelLen = query[offset] & 0xFF;
                if ((labelLen & 0xC0) == 0xC0) { offset += 2; break; }
                offset += 1 + labelLen;
            }
            if (offset < query.length && (query[offset] & 0xFF) == 0) offset++;
            offset += 4;

            byte[] response = new byte[offset + 16];
            System.arraycopy(query, 0, response, 0, offset);
            response[2] = (byte) 0x81;
            response[3] = (byte) 0x80;
            response[6] = 0;
            response[7] = 1;

            int a = offset;
            response[a] = (byte) 0xC0; response[a + 1] = 0x0C;
            response[a + 2] = 0; response[a + 3] = 1;
            response[a + 4] = 0; response[a + 5] = 1;
            response[a + 6] = 0; response[a + 7] = 0;
            response[a + 8] = 0; response[a + 9] = 60;
            response[a + 10] = 0; response[a + 11] = 4;
            response[a + 12] = 0; response[a + 13] = 0;
            response[a + 14] = 0; response[a + 15] = 0;
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Error creating blocked DNS response", e);
            return null;
        }
    }

    // ========================= Upstream Resolvers =========================

    /**
     * Refresh the list of upstream resolvers: the underlying (non-VPN)
     * network's own DNS servers first, then public fallbacks of both
     * families. Cheap enough to call before every forward; throttled.
     */
    private void refreshUpstreamServers(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - upstreamRefreshedAt < UPSTREAM_CACHE_MS) return;
        upstreamRefreshedAt = now;

        List<InetAddress> servers = new ArrayList<>();
        Network chosen = null;
        boolean chosenValidated = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (Network net : cm.getAllNetworks()) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                    if (caps == null) continue;
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue;
                    LinkProperties lp = cm.getLinkProperties(net);
                    if (lp == null) continue;
                    List<InetAddress> dns = lp.getDnsServers();
                    if (dns.isEmpty()) continue;
                    boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    // Prefer a validated network; otherwise keep the first usable one.
                    if (chosen == null || (validated && !chosenValidated)) {
                        chosen = net;
                        chosenValidated = validated;
                        servers = new ArrayList<>(dns);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read underlying network DNS servers", e);
        }

        for (String s : FALLBACK_DNS) {
            try {
                InetAddress a = InetAddress.getByName(s);
                if (!servers.contains(a)) servers.add(a);
            } catch (Exception ignored) { }
        }

        underlyingNetwork = chosen;
        upstreamServers = servers;
    }

    private byte[] forwardDnsQuery(byte[] query) {
        refreshUpstreamServers(false);

        List<InetAddress> order = new ArrayList<>();
        InetAddress good = lastGoodUpstream;
        if (good != null) order.add(good);
        for (InetAddress a : upstreamServers) {
            if (!order.contains(a)) order.add(a);
        }

        for (InetAddress server : order) {
            byte[] response = querySingleUpstream(query, server);
            if (response != null) {
                if (server != lastGoodUpstream) {
                    lastGoodUpstream = server;
                    Log.i(TAG, "Upstream DNS now " + server.getHostAddress());
                }
                return response;
            }
        }
        Log.e(TAG, "All upstream DNS servers failed");
        // Force a fresh look at the network on the next query.
        upstreamRefreshedAt = 0;
        lastGoodUpstream = null;
        return null;
    }

    private byte[] querySingleUpstream(byte[] query, InetAddress server) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            // protect() keeps the socket out of the VPN; binding it to the
            // underlying network as well makes IPv6 resolvers reachable on
            // IPv6-only carriers even while the VPN is the default network.
            protect(socket);
            Network net = underlyingNetwork;
            if (net != null) {
                try { net.bindSocket(socket); } catch (IOException e) { /* protect() is enough */ }
            }
            DatagramPacket request = new DatagramPacket(query, query.length, server, DNS_PORT);
            socket.setSoTimeout(UPSTREAM_TIMEOUT_MS);
            socket.send(request);
            byte[] buf = new byte[4096];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);
            return Arrays.copyOf(buf, response.getLength());
        } catch (Exception e) {
            Log.w(TAG, "Upstream " + server.getHostAddress() + " failed: " + e.getMessage());
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }

    // ========================= Packet Building =========================

    private byte[] buildUdpResponsePacket(byte[] origPacket, int ipHeaderLen,
                                          int origSrcPort, byte[] dnsResponse) {
        int totalLen = 20 + 8 + dnsResponse.length;
        byte[] pkt = new byte[totalLen];
        pkt[0] = 0x45;
        pkt[2] = (byte) ((totalLen >> 8) & 0xFF);
        pkt[3] = (byte) (totalLen & 0xFF);
        pkt[6] = 0x40; pkt[8] = 64; pkt[9] = 17;
        System.arraycopy(origPacket, 16, pkt, 12, 4);
        System.arraycopy(origPacket, 12, pkt, 16, 4);
        int ipCsum = calculateChecksum(pkt, 0, 20);
        pkt[10] = (byte) ((ipCsum >> 8) & 0xFF);
        pkt[11] = (byte) (ipCsum & 0xFF);
        int udpLen = 8 + dnsResponse.length;
        pkt[20] = 0; pkt[21] = 53;
        pkt[22] = (byte) ((origSrcPort >> 8) & 0xFF);
        pkt[23] = (byte) (origSrcPort & 0xFF);
        pkt[24] = (byte) ((udpLen >> 8) & 0xFF);
        pkt[25] = (byte) (udpLen & 0xFF);
        System.arraycopy(dnsResponse, 0, pkt, 28, dnsResponse.length);
        return pkt;
    }

    private int calculateChecksum(byte[] data, int offset, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            int high = (data[offset + i] & 0xFF) << 8;
            int low = (i + 1 < length) ? (data[offset + i + 1] & 0xFF) : 0;
            sum += high | low;
        }
        while ((sum >> 16) > 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    private static int readU16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    // ========================= Notification =========================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.vpn_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle(getString(R.string.vpn_notification_title))
                .setContentText(getString(R.string.vpn_notification_text))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
