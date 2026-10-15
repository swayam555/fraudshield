package com.fraudshield.risk.graph;

import com.fraudshield.risk.feature.FeatureStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Breadth-first walk of the payment graph around one account, for the investigation view. */
@Service
public class GraphService {

    private static final int MAX_NODES = 60;
    private static final int MAX_NEIGHBOURS = 15;

    private final StringRedisTemplate redis;

    public GraphService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Graph around(String root, int depth) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        Deque<String[]> queue = new ArrayDeque<>();
        queue.add(new String[]{root, "0"});
        nodes.put(root, node(root, 0));

        while (!queue.isEmpty() && nodes.size() < MAX_NODES) {
            String[] cur = queue.poll();
            String vpa = cur[0];
            int level = Integer.parseInt(cur[1]);
            if (level >= depth) continue;

            Set<String> out = redis.opsForZSet().reverseRange("g:out:" + vpa, 0, MAX_NEIGHBOURS - 1);
            if (out != null) {
                for (String payee : out) {
                    edges.add(edge(vpa, payee));
                    if (!nodes.containsKey(payee) && nodes.size() < MAX_NODES) {
                        nodes.put(payee, node(payee, level + 1));
                        queue.add(new String[]{payee, String.valueOf(level + 1)});
                    }
                }
            }
            Set<String> in = redis.opsForZSet().reverseRange("g:in:" + vpa, 0, MAX_NEIGHBOURS - 1);
            if (in != null) {
                for (String payer : in) {
                    if (edges.stream().noneMatch(e -> e.from().equals(payer) && e.to().equals(vpa))) {
                        edges.add(edge(payer, vpa));
                    }
                    if (!nodes.containsKey(payer) && nodes.size() < MAX_NODES) {
                        nodes.put(payer, node(payer, level + 1));
                        queue.add(new String[]{payer, String.valueOf(level + 1)});
                    }
                }
            }
        }
        List<Edge> visible = edges.stream()
                .filter(e -> nodes.containsKey(e.from()) && nodes.containsKey(e.to()))
                .distinct()
                .toList();
        return new Graph(root, new ArrayList<>(nodes.values()), visible);
    }

    private Node node(String vpa, int level) {
        Double mule = redis.opsForZSet().score(FeatureStore.MULE_WATCH, vpa);
        Boolean denied = redis.opsForSet().isMember(FeatureStore.DENY_LIST, vpa);
        return new Node(vpa, level, mule == null ? 0 : mule, Boolean.TRUE.equals(denied));
    }

    private Edge edge(String from, String to) {
        Object amt = redis.opsForHash().get("g:amt:" + from, to);
        Object cnt = redis.opsForHash().get("g:cnt:" + from, to);
        return new Edge(from, to,
                amt == null ? 0 : Math.round(Double.parseDouble(amt.toString())),
                cnt == null ? 0 : Long.parseLong(cnt.toString()));
    }

    public record Node(String id, int level, double muleScore, boolean denied) {
    }

    public record Edge(String from, String to, long amount, long count) {
    }

    public record Graph(String root, List<Node> nodes, List<Edge> edges) {
    }
}
