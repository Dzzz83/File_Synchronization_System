package com.filesync.client.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class NodeHealthChecker {
    private NodeHealthChecker() {}

    public static void main(String[] args) {
        List<NodeEndpoint> nodes = new ArrayList<>();

        // Laptop 1: big-black-cat
        String laptop1Ip = "100.127.165.125";
        for (int port = 8080; port <= 8083; port++) {
            nodes.add(new NodeEndpoint(laptop1Ip, port));
        }

        // Laptop 2: laptop-e0hoiasa
        String laptop2Ip = "100.127.93.108";
        for (int port = 8080; port <= 8083; port++) {
            nodes.add(new NodeEndpoint(laptop2Ip, port));
        }

        // Laptop 3: laptop-u3g39hrt
        String laptop3Ip = "100.84.134.81";
        for (int port = 8080; port <= 8083; port++) {
            nodes.add(new NodeEndpoint(laptop3Ip, port));
        }

        System.out.println("Checking health of " + nodes.size() + " server nodes...\n");
        int healthyCount = 0;
        for (NodeEndpoint node : nodes) {
            boolean healthy = checkHealth(node.ip, node.port);
            if (healthy) {
                healthyCount++;
                System.out.printf("Node %s:%d is UP%n", node.ip, node.port);
            } else {
                System.out.printf("Node %s:%d is DOWN%n", node.ip, node.port);
            }
        }
        System.out.printf("\nTotal healthy nodes: %d / %d%n", healthyCount, nodes.size());
    }

    private static boolean checkHealth(String ip, int port) {
        String urlString = String.format("http://%s:%d/health", ip, port);
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static class NodeEndpoint {
        final String ip;
        final int port;
        NodeEndpoint(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }
}