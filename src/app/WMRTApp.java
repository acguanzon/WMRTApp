package app;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.animation.TranslateTransition;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import javafx.scene.effect.Glow;
import javafx.scene.effect.DropShadow;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import javafx.concurrent.Worker;

import java.util.*;
import java.util.stream.Collectors;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Comparator;

public class WMRTApp extends Application {

    private Stage primaryStage;
    private TabPane mainTabPane;
    private boolean themeEnabled = true;
    private String themeUriCached;
    private String themeOverrideUriCached;

    // ------------------- User class -------------------
    static class User {
        String username;
        String password;
        String role; // "user" or "admin"

        User(String username, String password, String role) {
            this.username = username;
            this.password = password;
            this.role = role;
        }
    }

    // ------------------- Recycling Submission -------------------
    public static class Submission {
        private final String date;
        private final String material;
        private final double weight;
        private final String submittedBy;

        public Submission(String date, String material, double weight) {
            this(date, material, weight, "");
        }

        public Submission(String date, String material, double weight, String submittedBy) {
            this.date = date;
            this.material = material;
            this.weight = weight;
            this.submittedBy = submittedBy == null ? "" : submittedBy;
        }

        public String getDate() {
            return date;
        }

        public String getMaterial() {
            return material;
        }

        public double getWeight() {
            return weight;
        }

        public String getSubmittedBy() {
            return submittedBy;
        }
    }

    // ------------------- Pending Submission (for verification) -------------------
    public static class PendingSubmission {
        private final String id;
        private final String date;
        private final String material;
        private final double weight;
        private final String submittedBy;

        public PendingSubmission(String id, String date, String material, double weight, String submittedBy) {
            this.id = id;
            this.date = date;
            this.material = material;
            this.weight = weight;
            this.submittedBy = submittedBy;
        }

        public String getId() {
            return id;
        }

        public String getDate() {
            return date;
        }

        public String getMaterial() {
            return material;
        }

        public double getWeight() {
            return weight;
        }

        public String getSubmittedBy() {
            return submittedBy;
        }
    }

    // ------------------- Recycling Center -------------------
    // Center class moved to src/app/Center.java

    // ------------------- Optimized Route Service with Caching & Spatial Indexing
    // -------------------
    static class RouteService {
        static class Node {
            String id;
            double lat, lng;

            Node(String id, double lat, double lng) {
                this.id = id;
                this.lat = lat;
                this.lng = lng;
            }
        }

        static class Edge {
            String from, to;
            double weight;

            Edge(String from, String to, double weight) {
                this.from = from;
                this.to = to;
                this.weight = weight;
            }
        }

        // Spatial indexing for O(log n) center lookups
        static class SpatialIndex {
            private Map<String, Node> nodes = new HashMap<>();
            private List<Node> sortedByLat = new ArrayList<>();
            private List<Node> sortedByLng = new ArrayList<>();
            private boolean isDirty = true;

            void addNode(Node node) {
                nodes.put(node.id, node);
                isDirty = true;
            }

            void clear() {
                nodes.clear();
                sortedByLat.clear();
                sortedByLng.clear();
                isDirty = true;
            }

            // Find nearest center using divide-and-conquer spatial search
            Node findNearest(double lat, double lng, double maxDistance) {
                if (isDirty)
                    rebuildIndex();

                Node nearest = null;
                double minDist = maxDistance;

                // Use binary search for efficient range queries
                int latStart = binarySearchLat(lat - maxDistance);
                int latEnd = binarySearchLat(lat + maxDistance);

                for (int i = latStart; i <= latEnd && i < sortedByLat.size(); i++) {
                    Node node = sortedByLat.get(i);
                    double dist = Math.sqrt((node.lat - lat) * (node.lat - lat) + (node.lng - lng) * (node.lng - lng));
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = node;
                    }
                }

                return nearest;
            }

            private void rebuildIndex() {
                sortedByLat.clear();
                sortedByLng.clear();
                sortedByLat.addAll(nodes.values());
                sortedByLng.addAll(nodes.values());
                sortedByLat.sort((a, b) -> Double.compare(a.lat, b.lat));
                sortedByLng.sort((a, b) -> Double.compare(a.lng, b.lng));
                isDirty = false;
            }

            private int binarySearchLat(double targetLat) {
                int left = 0, right = sortedByLat.size() - 1;
                while (left <= right) {
                    int mid = left + (right - left) / 2;
                    if (sortedByLat.get(mid).lat < targetLat) {
                        left = mid + 1;
                    } else {
                        right = mid - 1;
                    }
                }
                return left;
            }
        }

        private Map<String, Node> nodes = new HashMap<>();
        private Map<String, List<Edge>> adjacencyList = new HashMap<>();
        private SpatialIndex spatialIndex = new SpatialIndex();

        // Caching for route calculations (memoization)
        private Map<String, Map<String, Double>> distanceCache = new HashMap<>();
        private Map<String, Map<String, List<String>>> pathCache = new HashMap<>();
        private long lastBuildTime = 0;
        private static final long CACHE_VALIDITY_MS = 300000; // 5 minutes

        void build(List<Center> centers) {
            // Check if rebuild is necessary (optimization)
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBuildTime < CACHE_VALIDITY_MS && !nodes.isEmpty()) {
                return; // Use cached data
            }

            nodes.clear();
            adjacencyList.clear();
            spatialIndex.clear();
            distanceCache.clear();
            pathCache.clear();

            // Add depot
            // Add depot (Bacolod City Hall area)
            Node depot = new Node("DEPOT", 10.6762, 122.9501);
            nodes.put("DEPOT", depot);
            spatialIndex.addNode(depot);
            adjacencyList.put("DEPOT", new ArrayList<>());

            // Add centers
            for (Center c : centers) {
                Node node = new Node(c.id, c.lat, c.lng);
                nodes.put(c.id, node);
                spatialIndex.addNode(node);
                adjacencyList.put(c.id, new ArrayList<>());
            }

            // Create optimized graph (only connect nearby nodes to reduce complexity)
            List<String> allNodes = new ArrayList<>(nodes.keySet());
            for (int i = 0; i < allNodes.size(); i++) {
                for (int j = 0; j < allNodes.size(); j++) {
                    if (i != j) {
                        String from = allNodes.get(i);
                        String to = allNodes.get(j);
                        double distance = calculateDistance(nodes.get(from), nodes.get(to));

                        // Only connect if distance is reasonable (optimization)
                        if (distance < 20.0) { // Max connection distance
                            adjacencyList.get(from).add(new Edge(from, to, distance));
                        }
                    }
                }
            }

            lastBuildTime = currentTime;
        }

        // Calculate Euclidean distance between two nodes
        private double calculateDistance(Node a, Node b) {
            double dx = a.lat - b.lat;
            double dy = a.lng - b.lng;
            return Math.sqrt(dx * dx + dy * dy);
        }

        // Optimized Dijkstra's algorithm with caching
        private Map<String, Double> dijkstra(String start) {
            // Check cache first
            if (distanceCache.containsKey(start)) {
                return distanceCache.get(start);
            }

            Map<String, Double> distances = new HashMap<>();
            Map<String, String> previous = new HashMap<>();
            Queue<String> queue = new LinkedList<>();

            // Initialize distances
            for (String node : nodes.keySet()) {
                distances.put(node, Double.MAX_VALUE);
            }
            distances.put(start, 0.0);
            queue.offer(start);

            while (!queue.isEmpty()) {
                String current = queue.poll();

                for (Edge edge : adjacencyList.get(current)) {
                    String neighbor = edge.to;
                    double newDist = distances.get(current) + edge.weight;

                    if (newDist < distances.get(neighbor)) {
                        distances.put(neighbor, newDist);
                        previous.put(neighbor, current);
                        queue.offer(neighbor);
                    }
                }
            }

            // Cache the result
            distanceCache.put(start, distances);
            return distances;
        }

        // Find shortest path between two nodes with caching
        private List<String> getShortestPath(String start, String end) {
            // Check cache first
            String cacheKey = start + "->" + end;
            if (pathCache.containsKey(start) && pathCache.get(start).containsKey(end)) {
                return pathCache.get(start).get(end);
            }

            Map<String, Double> distances = new HashMap<>();
            Map<String, String> previous = new HashMap<>();
            Queue<String> queue = new LinkedList<>();

            // Initialize distances
            for (String node : nodes.keySet()) {
                distances.put(node, Double.MAX_VALUE);
            }
            distances.put(start, 0.0);
            queue.offer(start);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (current.equals(end))
                    break;

                for (Edge edge : adjacencyList.get(current)) {
                    String neighbor = edge.to;
                    double newDist = distances.get(current) + edge.weight;

                    if (newDist < distances.get(neighbor)) {
                        distances.put(neighbor, newDist);
                        previous.put(neighbor, current);
                        queue.offer(neighbor);
                    }
                }
            }

            // Reconstruct path
            List<String> path = new ArrayList<>();
            String current = end;
            while (current != null) {
                path.add(0, current);
                current = previous.get(current);
            }

            // Cache the result
            if (!pathCache.containsKey(start)) {
                pathCache.put(start, new HashMap<>());
            }
            pathCache.get(start).put(end, path);

            return path;
        }

        // Optimized greedy algorithm for visit order with spatial indexing
        List<String> shortestVisitOrder(String start, List<String> targets) {
            if (targets.isEmpty()) {
                return Arrays.asList(start, start);
            }

            List<String> unvisited = new ArrayList<>(targets);
            List<String> route = new ArrayList<>();
            String current = start;
            route.add(start);

            // Use spatial indexing for faster nearest neighbor search
            while (!unvisited.isEmpty()) {
                String nearest = null;
                double minDistance = Double.MAX_VALUE;

                // Greedy selection: find nearest unvisited target
                for (String target : unvisited) {
                    Node currentNode = nodes.get(current);
                    Node targetNode = nodes.get(target);

                    // Use direct distance calculation for initial filtering
                    double directDistance = calculateDistance(currentNode, targetNode);

                    if (directDistance < minDistance) {
                        // Only calculate full path if it's potentially better
                        List<String> path = getShortestPath(current, target);
                        double pathDistance = calculatePathDistance(path);
                        if (pathDistance < minDistance) {
                            minDistance = pathDistance;
                            nearest = target;
                        }
                    }
                }

                if (nearest != null) {
                    // Add path to nearest target (excluding current node)
                    List<String> path = getShortestPath(current, nearest);
                    for (int i = 1; i < path.size(); i++) {
                        route.add(path.get(i));
                    }
                    current = nearest;
                    unvisited.remove(nearest);
                }
            }

            // Return to depot
            List<String> returnPath = getShortestPath(current, start);
            for (int i = 1; i < returnPath.size(); i++) {
                route.add(returnPath.get(i));
            }

            return route;
        }

        // Greedy algorithm for optimal center selection based on capacity and distance
        List<String> selectOptimalCenters(List<String> availableCenters, int maxCenters, double userLat,
                double userLng) {
            if (availableCenters.size() <= maxCenters) {
                return new ArrayList<>(availableCenters);
            }

            List<String> selected = new ArrayList<>();
            List<String> remaining = new ArrayList<>(availableCenters);

            // Greedy selection: prioritize centers by efficiency score
            while (selected.size() < maxCenters && !remaining.isEmpty()) {
                String bestCenter = null;
                double bestScore = -1;

                for (String center : remaining) {
                    Node centerNode = nodes.get(center);
                    double distance = calculateDistance(
                            new Node("user", userLat, userLng), centerNode);

                    // Efficiency score: inverse distance + center capacity factor
                    double efficiencyScore = 1.0 / (distance + 0.1) +
                            (centerNode.id.equals("C1") ? 2.0 : 1.0); // C1 has higher capacity

                    if (efficiencyScore > bestScore) {
                        bestScore = efficiencyScore;
                        bestCenter = center;
                    }
                }

                if (bestCenter != null) {
                    selected.add(bestCenter);
                    remaining.remove(bestCenter);
                }
            }

            return selected;
        }

        private double calculatePathDistance(List<String> path) {
            double totalDistance = 0.0;
            for (int i = 0; i < path.size() - 1; i++) {
                Node from = nodes.get(path.get(i));
                Node to = nodes.get(path.get(i + 1));
                totalDistance += calculateDistance(from, to);
            }
            return totalDistance;
        }

        Map<String, Node> getNodes() {
            return nodes;
        }
    }

    // ------------------- Tim Sort Implementation -------------------
    static class TimSort {
        private static final int MIN_MERGE = 32;
        private static final int MIN_GALLOP = 7;

        public static <T> void sort(List<T> list, Comparator<? super T> c) {
            if (list == null || list.size() < 2)
                return;

            Object[] a = list.toArray();
            int n = a.length;

            // If array is small, use insertion sort
            if (n < MIN_MERGE) {
                insertionSort(a, 0, n, c);
                // Copy back to list
                for (int i = 0; i < n; i++) {
                    list.set(i, (T) a[i]);
                }
                return;
            }

            // Calculate minimum run length
            int minRun = minRunLength(n);

            // Sort individual runs using insertion sort
            for (int i = 0; i < n; i += minRun) {
                int end = Math.min(i + minRun, n);
                insertionSort(a, i, end, c);
            }

            // Merge runs
            for (int size = minRun; size < n; size = 2 * size) {
                for (int left = 0; left < n; left += 2 * size) {
                    int mid = left + size;
                    int right = Math.min(left + 2 * size, n);

                    if (mid < right) {
                        merge(a, left, mid, right, c);
                    }
                }
            }

            // Copy back to list
            for (int i = 0; i < n; i++) {
                list.set(i, (T) a[i]);
            }
        }

        private static <T> void insertionSort(Object[] a, int left, int right, Comparator<? super T> c) {
            for (int i = left + 1; i < right; i++) {
                T key = (T) a[i];
                int j = i - 1;

                while (j >= left && c.compare((T) a[j], key) > 0) {
                    a[j + 1] = a[j];
                    j--;
                }
                a[j + 1] = key;
            }
        }

        private static <T> void merge(Object[] a, int left, int mid, int right, Comparator<? super T> c) {
            int len1 = mid - left;
            int len2 = right - mid;

            Object[] leftArray = new Object[len1];
            Object[] rightArray = new Object[len2];

            System.arraycopy(a, left, leftArray, 0, len1);
            System.arraycopy(a, mid, rightArray, 0, len2);

            int i = 0, j = 0, k = left;

            while (i < len1 && j < len2) {
                if (c.compare((T) leftArray[i], (T) rightArray[j]) <= 0) {
                    a[k++] = leftArray[i++];
                } else {
                    a[k++] = rightArray[j++];
                }
            }

            while (i < len1) {
                a[k++] = leftArray[i++];
            }

            while (j < len2) {
                a[k++] = rightArray[j++];
            }
        }

        private static int minRunLength(int n) {
            int r = 0;
            while (n >= MIN_MERGE) {
                r |= (n & 1);
                n >>= 1;
            }
            return n + r;
        }
    }

    // ------------------- Bacolod City Barangay Submission Points
    // -------------------
    static class Store {
        List<Center> centers = Arrays.asList(
                // Barangay 1 - Mandalagan (PhilAtlas coordinates)
                new Center("BRGY-001", 10.6923, 122.9662, "Open",
                        "Plastic bottles, Glass containers, Aluminum cans. Operating: 7am-7pm daily. Contact: 0917-123-4567",
                        "Mandalagan"),

                // Barangay 2 - Bata (PhilAtlas coordinates)
                new Center("BRGY-002", 10.6827, 122.9604, "Open",
                        "Paper, Cardboard, Newspaper. Operating: 8am-6pm Mon-Sat. Contact: 0918-234-5678", "Bata"),

                // Barangay 3 - Taculing (PhilAtlas coordinates)
                new Center("BRGY-003", 10.6496, 122.9475, "Busy",
                        "Mixed recyclables, Electronic waste. Operating: 9am-5pm Tue-Sun. Contact: 0919-345-6789",
                        "Taculing"),

                // Barangay 4 - Villamonte (PhilAtlas coordinates)
                new Center("BRGY-004", 10.6685, 122.9647, "Open",
                        "Plastic bags, Metal scraps, Glass bottles. Operating: 6am-8pm daily. Contact: 0920-456-7890",
                        "Villamonte"),

                // Barangay 5 - Alangilan (PhilAtlas coordinates)
                new Center("BRGY-005", 10.6610, 123.0790, "Closed",
                        "Maintenance until next Monday. All materials accepted when operational. Contact: 0921-567-8901",
                        "Alangilan"));
    }

    // ------------------- Sample user list -------------------
    private ObservableList<User> users = FXCollections.observableArrayList(
            new User("admin", "1234", "admin"),
            new User("john", "abcd", "user"),
            new User("mary", "pass", "user"));

    private User loggedInUser = null;
    private Store store = new Store();
    private RouteService routeService = new RouteService();
    private final Path usersFile = Paths.get("users.csv");

    // Pending submissions queue for admin verification
    private ObservableList<PendingSubmission> pendingSubmissions = FXCollections.observableArrayList();
    private int submissionCounter = 1; // For generating unique IDs

    // Approved submissions (shared between resident and admin views)
    private ObservableList<Submission> approvedSubmissions = FXCollections.observableArrayList();
    private final Path approvedFile = Paths.get("approved.csv");
    private final Path pendingFile = Paths.get("pending.csv");

    // ------------------- Optimized Data Structures for Table Operations
    // -------------------
    private Map<String, FilteredList<Submission>> userSubmissionCache = new HashMap<>();
    private Map<String, SortedList<Submission>> userSortedCache = new HashMap<>();
    private Map<String, FilteredList<PendingSubmission>> pendingFilterCache = new HashMap<>();
    private Map<String, SortedList<PendingSubmission>> pendingSortedCache = new HashMap<>();

    // Optimized table data retrieval with caching
    private FilteredList<Submission> getUserSubmissions(String username) {
        if (!userSubmissionCache.containsKey(username)) {
            FilteredList<Submission> filtered = approvedSubmissions.filtered(
                    submission -> submission.getSubmittedBy().equals(username));
            userSubmissionCache.put(username, filtered);
        }
        return userSubmissionCache.get(username);
    }

    private SortedList<Submission> getUserSortedSubmissions(String username) {
        if (!userSortedCache.containsKey(username)) {
            FilteredList<Submission> filtered = getUserSubmissions(username);
            SortedList<Submission> sorted = filtered.sorted();
            userSortedCache.put(username, sorted);
        }
        return userSortedCache.get(username);
    }

    // Clear caches when data changes
    private void clearSubmissionCaches() {
        userSubmissionCache.clear();
        userSortedCache.clear();
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        // Set borderless window style BEFORE first show
        primaryStage.initStyle(javafx.stage.StageStyle.UNDECORATED);

        loadUsersFromFile();
        loadPendingFromFile();
        loadApprovedFromFile();
        // Auto-save when lists change
        pendingSubmissions.addListener((javafx.collections.ListChangeListener<? super PendingSubmission>) change -> {
            savePendingToFile();
        });
        approvedSubmissions.addListener((javafx.collections.ListChangeListener<? super Submission>) change -> {
            saveApprovedToFile();
        });
        // Save on close
        primaryStage.setOnCloseRequest(e -> {
            savePendingToFile();
            saveApprovedToFile();
        });
        showLoginScreen();
    }

    private void applyTheme(Scene scene) {
        ensureThemeFile();
        ensureThemeOverrideFile();
        try {
            themeUriCached = java.nio.file.Paths.get("theme.css").toUri().toString();
            themeOverrideUriCached = java.nio.file.Paths.get("theme-override.css").toUri().toString();
            if (themeEnabled) {
                if (!scene.getStylesheets().contains(themeUriCached))
                    scene.getStylesheets().add(themeUriCached);
                if (!scene.getStylesheets().contains(themeOverrideUriCached))
                    scene.getStylesheets().add(themeOverrideUriCached);
            } else {
                scene.getStylesheets().remove(themeUriCached);
                scene.getStylesheets().remove(themeOverrideUriCached);
            }
        } catch (Exception ignore) {
        }
    }

    // ------------------- Login Screen -------------------
    private void showLoginScreen() {
        primaryStage.setTitle("WMRT Login");

        // Create main container
        VBox mainContainer = new VBox(30);
        mainContainer.setAlignment(javafx.geometry.Pos.CENTER);
        mainContainer.setPadding(new Insets(40));
        mainContainer.getStyleClass().add("login-pane");

        // Create logo section with floating animation
        VBox logoContainer = new VBox(15);
        logoContainer.setAlignment(javafx.geometry.Pos.CENTER);

        // Create a container for the logo with padding for glow effect
        StackPane logoPane = new StackPane();
        logoPane.setPadding(new Insets(20));
        logoPane.setMinSize(400, 400);

        // Try to load logo image from multiple possible locations
        ImageView logoImageView = null;
        Node logoNode = null;
        boolean imageLoaded = false;

        // Try different path formats and filenames
        // Place your logo file in the main project directory (same folder as theme.css,
        // users.csv, etc.)
        String[] imagePaths = {
                // Try logo.png first (recommended name)
                "logo.png",
                "file:logo.png",
                "file:./logo.png",
                new java.io.File("logo.png").toURI().toString(),
                // Try original filename as fallback
                "553948659_1344845000520826_308021965867850840_n.png",
                "file:553948659_1344845000520826_308021965867850840_n.png",
                "file:./553948659_1344845000520826_308021965867850840_n.png",
                new java.io.File("553948659_1344845000520826_308021965867850840_n.png").toURI().toString()
        };

        for (String imagePath : imagePaths) {
            try {
                Image logoImage = new Image(imagePath);
                // Check if image loaded successfully (not broken)
                if (logoImage.getWidth() > 0 && logoImage.getHeight() > 0) {
                    logoImageView = new ImageView(logoImage);
                    logoImageView.setFitWidth(350);
                    logoImageView.setFitHeight(350);
                    logoImageView.setPreserveRatio(true);
                    logoImageView.setSmooth(true);
                    logoNode = logoImageView;
                    imageLoaded = true;
                    System.out.println("Logo image loaded successfully from: " + imagePath);
                    break;
                }
            } catch (Exception e) {
                // Try next path
                continue;
            }
        }

        // If image not found, create a visible text logo
        if (!imageLoaded) {
            System.out.println("Logo image not found, using text logo");
            Label recycleSymbol = new Label("♻");
            recycleSymbol.setStyle(
                    "-fx-font-size: 200px; " +
                            "-fx-text-fill: #ffffff; " +
                            "-fx-font-weight: bold; " +
                            "-fx-opacity: 1.0;");
            logoNode = recycleSymbol;
        }

        // Always ensure logo node exists and is visible
        if (logoNode == null) {
            Label recycleSymbol = new Label("♻");
            recycleSymbol.setStyle(
                    "-fx-font-size: 200px; " +
                            "-fx-text-fill: #ffffff; " +
                            "-fx-font-weight: bold; " +
                            "-fx-opacity: 1.0;");
            logoNode = recycleSymbol;
        }

        // Simple subtle shadow for visibility (no neon effects)
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#000000"));
        shadow.setRadius(10);
        shadow.setSpread(0.3);
        shadow.setOffsetX(2);
        shadow.setOffsetY(2);

        // Apply subtle shadow only (no glow effect)
        logoNode.setEffect(shadow);
        if (logoImageView != null) {
            // For ImageView, remove all effects or use minimal shadow
            logoImageView.setStyle(
                    "-fx-opacity: 1.0;");
        }

        logoNode.setOpacity(1.0);

        // Make sure the logo pane is visible
        logoPane.setOpacity(1.0);
        logoPane.getChildren().add(logoNode);

        // Create floating animation (up and down movement)
        TranslateTransition floatAnimation = new TranslateTransition(Duration.millis(2000), logoPane);
        floatAnimation.setFromY(-15);
        floatAnimation.setToY(15);
        floatAnimation.setCycleCount(TranslateTransition.INDEFINITE);
        floatAnimation.setAutoReverse(true);
        floatAnimation.play();

        // Fade-in animation on load
        FadeTransition fadeIn = new FadeTransition(Duration.millis(1000), logoPane);
        fadeIn.setFromValue(0.3);
        fadeIn.setToValue(1.0);
        fadeIn.play();

        logoContainer.getChildren().add(logoPane);

        // Create login form
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(18);
        grid.setHgap(18);
        grid.setAlignment(javafx.geometry.Pos.CENTER);

        Label userLabel = new Label("Username:");
        userLabel.setStyle("-fx-text-fill: #00ffc6; -fx-font-size: 16px; -fx-font-weight: bold;");
        TextField userField = new TextField();
        userField.setPrefWidth(420);
        userField.setStyle("-fx-font-size: 18px;");
        grid.add(userLabel, 0, 0);
        grid.add(userField, 1, 0);

        Label passLabel = new Label("Password:");
        passLabel.setStyle("-fx-text-fill: #00ffc6; -fx-font-size: 16px; -fx-font-weight: bold;");
        PasswordField passField = new PasswordField();
        passField.setPrefWidth(420);
        passField.setStyle("-fx-font-size: 18px;");
        grid.add(passLabel, 0, 1);
        grid.add(passField, 1, 1);

        Button loginButton = new Button("Login");
        Button registerButton = new Button("Register New Account");

        HBox buttonBox = new HBox(20);
        buttonBox.getChildren().addAll(loginButton, registerButton);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        loginButton.setStyle(
                "-fx-font-size: 16px; -fx-padding: 12 20 12 20; -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        registerButton.setStyle(
                "-fx-font-size: 16px; -fx-padding: 12 20 12 20; -fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        grid.add(buttonBox, 0, 2, 2, 1);

        Label message = new Label();
        message.setStyle("-fx-text-fill: #f44336; -fx-font-size: 14px; -fx-font-weight: bold;");
        grid.add(message, 0, 3, 2, 1);

        loginButton.setOnAction(e -> {
            String username = userField.getText().trim();
            String password = passField.getText().trim();

            loggedInUser = users.stream()
                    .filter(u -> u.username.equals(username) && u.password.equals(password))
                    .findFirst()
                    .orElse(null);

            if (loggedInUser != null) {
                showDashboard();
            } else {
                message.setText("Invalid username or password.");
                message.setStyle("-fx-text-fill: #f44336; -fx-font-size: 14px; -fx-font-weight: bold;");
            }
        });

        registerButton.setOnAction(e -> {
            showRegistrationScreen();
        });

        // Add all components to main container
        mainContainer.getChildren().addAll(logoContainer, grid);

        Scene scene = new Scene(mainContainer);
        applyTheme(scene);
        primaryStage.setScene(scene);
        primaryStage.setFullScreen(true);
        primaryStage.setFullScreenExitHint("");
        if (!primaryStage.isShowing()) {
            primaryStage.show();
        }
    }

    // ------------------- Registration Screen -------------------
    private void showRegistrationScreen() {
        primaryStage.setTitle("WMRT Registration");

        // Create main container for centering
        VBox mainContainer = new VBox(30);
        mainContainer.setAlignment(javafx.geometry.Pos.CENTER);
        mainContainer.setPadding(new Insets(40));
        mainContainer.getStyleClass().add("login-pane");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(30));
        grid.setHgap(15);
        grid.setVgap(20);
        grid.setAlignment(javafx.geometry.Pos.CENTER);

        Label titleLabel = new Label("Create Resident Account");
        titleLabel.getStyleClass().add("login-title");
        titleLabel.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #00ff88;");
        grid.add(titleLabel, 0, 0, 2, 1);

        Label userLabel = new Label("Username:");
        userLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff;");
        TextField userField = new TextField();
        userField.setPromptText("Enter username");
        userField.setPrefSize(300, 40);
        userField.setStyle(
                "-fx-font-size: 16px; -fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; -fx-border-color: #00ff88; -fx-border-width: 2px;");
        grid.add(userLabel, 0, 1);
        grid.add(userField, 1, 1);

        Label passLabel = new Label("Password:");
        passLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff;");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Enter password");
        passField.setPrefSize(300, 40);
        passField.setStyle(
                "-fx-font-size: 16px; -fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; -fx-border-color: #00ff88; -fx-border-width: 2px;");
        grid.add(passLabel, 0, 2);
        grid.add(passField, 1, 2);

        Label confirmPassLabel = new Label("Confirm Password:");
        confirmPassLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff;");
        PasswordField confirmPassField = new PasswordField();
        confirmPassField.setPromptText("Confirm password");
        confirmPassField.setPrefSize(300, 40);
        confirmPassField.setStyle(
                "-fx-font-size: 16px; -fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; -fx-border-color: #00ff88; -fx-border-width: 2px;");
        grid.add(confirmPassLabel, 0, 3);
        grid.add(confirmPassField, 1, 3);

        // Role is automatically set to "user" for residents only
        Label roleLabel = new Label("Role:");
        roleLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff;");
        Label roleValue = new Label("Resident (User)");
        roleValue.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #00ff88;");
        grid.add(roleLabel, 0, 4);
        grid.add(roleValue, 1, 4);

        Button registerButton = new Button("Register");
        registerButton.setPrefSize(150, 50);
        registerButton.setStyle(
                "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");
        Button backButton = new Button("Back to Login");
        backButton.setPrefSize(150, 50);
        backButton.setStyle("-fx-background-color: #757575; -fx-text-fill: white; -fx-font-size: 18px;");

        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        buttonBox.getChildren().addAll(registerButton, backButton);
        grid.add(buttonBox, 0, 5, 2, 1);

        Label message = new Label();
        message.setStyle("-fx-text-fill: #ff4444; -fx-font-size: 16px;");
        grid.add(message, 0, 6, 2, 1);

        mainContainer.getChildren().add(grid);

        registerButton.setOnAction(e -> {
            String username = userField.getText().trim();
            String password = passField.getText().trim();
            String confirmPassword = confirmPassField.getText().trim();
            String role = "user"; // Always set to user for residents

            // Validation
            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                message.setText("Please fill in all fields!");
                return;
            }

            if (username.length() < 3) {
                message.setText("Username must be at least 3 characters!");
                return;
            }

            if (password.length() < 4) {
                message.setText("Password must be at least 4 characters!");
                return;
            }

            if (!password.equals(confirmPassword)) {
                message.setText("Passwords do not match!");
                return;
            }

            // Check if username already exists
            boolean usernameExists = users.stream()
                    .anyMatch(u -> u.username.equals(username));

            if (usernameExists) {
                message.setText("Username already exists!");
                return;
            }

            // Create new user (always as resident)
            User newUser = new User(username, password, role);
            users.add(newUser);
            saveUsersToFile();

            message.setText("Resident account created successfully! You can now login.");
            message.setStyle("-fx-text-fill: #4CAF50;");

            // Clear form
            userField.clear();
            passField.clear();
            confirmPassField.clear();
        });

        backButton.setOnAction(e -> {
            showLoginScreen();
        });

        Scene scene = new Scene(mainContainer);
        applyTheme(scene);
        primaryStage.setScene(scene);
        primaryStage.setFullScreen(true);
        primaryStage.setFullScreenExitHint("");
        if (!primaryStage.isShowing()) {
            primaryStage.show();
        }
    }

    // ------------------- Optimized Persistence with Lazy Loading
    // -------------------
    private boolean usersLoaded = false;
    private boolean pendingLoaded = false;
    private boolean approvedLoaded = false;
    private long lastSaveTime = 0;
    private static final long SAVE_THROTTLE_MS = 1000; // Save at most once per second

    private void loadUsersFromFile() {
        if (usersLoaded)
            return; // Lazy loading optimization

        try {
            if (Files.exists(usersFile)) {
                List<String> lines = Files.readAllLines(usersFile, StandardCharsets.UTF_8);
                users.clear();
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#"))
                        continue;
                    String[] parts = trimmed.split(",");
                    if (parts.length >= 3) {
                        String username = parts[0].trim();
                        String password = parts[1].trim();
                        String role = parts[2].trim();
                        users.add(new User(username, password, role));
                    }
                }
                if (users.isEmpty()) {
                    // Ensure at least one admin exists
                    users.add(new User("admin", "1234", "admin"));
                    saveUsersToFile();
                }
            } else {
                // First run: seed with default accounts and persist
                if (users.stream().noneMatch(u -> u.username.equals("admin"))) {
                    users.add(new User("admin", "1234", "admin"));
                }
                saveUsersToFile();
            }
            usersLoaded = true;
        } catch (IOException ex) {
            // Fallback: keep in-memory defaults
        }
    }

    private void saveUsersToFile() {
        // Throttle saves to prevent excessive I/O
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSaveTime < SAVE_THROTTLE_MS) {
            return; // Skip this save
        }

        try {
            List<String> lines = new ArrayList<>();
            lines.add("# username,password,role");
            for (User u : users) {
                lines.add(String.join(",", Arrays.asList(u.username, u.password, u.role)));
            }
            Files.write(usersFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            lastSaveTime = currentTime;
        } catch (IOException ex) {
            // Ignore write failure; app continues with in-memory users
        }
    }

    // ------------------- Persistence: Pending & Approved -------------------
    private void loadPendingFromFile() {
        if (pendingLoaded)
            return; // Lazy loading optimization

        try {
            pendingSubmissions.clear();
            if (!Files.exists(pendingFile))
                return;
            List<String> lines = Files.readAllLines(pendingFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#"))
                    continue;
                String[] p = t.split(",");
                if (p.length >= 5) {
                    String id = p[0];
                    String date = p[1];
                    String material = p[2];
                    double weight = Double.parseDouble(p[3]);
                    String submittedBy = p[4];
                    pendingSubmissions.add(new PendingSubmission(id, date, material, weight, submittedBy));
                    try {
                        int num = Integer.parseInt(id.replace("SUB-", ""));
                        submissionCounter = Math.max(submissionCounter, num + 1);
                    } catch (Exception ignore) {
                    }
                }
            }
            pendingLoaded = true;
        } catch (IOException ex) {
            // ignore
        }
    }

    private void savePendingToFile() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# id,date,material,weight,submittedBy");
            for (PendingSubmission ps : pendingSubmissions) {
                lines.add(String.join(",",
                        Arrays.asList(ps.getId(), ps.getDate(), ps.getMaterial(), String.valueOf(ps.getWeight()),
                                ps.getSubmittedBy())));
            }
            Files.write(pendingFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            // ignore
        }
    }

    private void loadApprovedFromFile() {
        try {
            approvedSubmissions.clear();
            if (!Files.exists(approvedFile))
                return;
            List<String> lines = Files.readAllLines(approvedFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#"))
                    continue;
                String[] p = t.split(",");
                if (p.length >= 4) {
                    String date = p[0];
                    String material = p[1];
                    double weight = Double.parseDouble(p[2]);
                    String submittedBy = p[3];
                    approvedSubmissions.add(new Submission(date, material, weight, submittedBy));
                }
            }
        } catch (IOException ex) {
            // ignore
        }
    }

    private void saveApprovedToFile() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# date,material,weight,submittedBy");
            for (Submission s : approvedSubmissions) {
                lines.add(String.join(",",
                        Arrays.asList(s.getDate(), s.getMaterial(), String.valueOf(s.getWeight()),
                                s.getSubmittedBy())));
            }
            Files.write(approvedFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            // ignore
        }
    }

    // ------------------- Theme (Futuristic) -------------------
    private void ensureThemeFile() {
        Path themePath = Paths.get("theme.css");
        String css = String.join("\n",
                ":root {",
                "    -fx-font-family: 'Segoe UI', 'Inter', 'Roboto', sans-serif;",
                "    -wmrt-accent: #00ffc6;",
                "    -wmrt-accent-2: #8a2be2;",
                "    -wmrt-bg: #0b0f14;",
                "    -wmrt-surface: #131a22;",
                "    -wmrt-text: #e6f1ff;",
                "}",
                ".root {",
                "    -fx-base: -wmrt-bg;",
                "    -fx-background-color: linear-gradient(to bottom, #0b0f14, #0c1118);",
                "    -fx-text-fill: -wmrt-text;",
                "}",
                "Label { -fx-text-fill: -wmrt-text; }",
                "TabPane { -fx-background-color: transparent; }",
                "TabPane .tab-header-area { -fx-background-color: transparent; }",
                "TabPane .tab-header-background { -fx-background-color: transparent; }",
                "TabPane .tab {",
                "    -fx-background-insets: 0;",
                "    -fx-background-radius: 10;",
                "    -fx-background-color: transparent;",
                "}",
                "TabPane .tab:selected {",
                "    -fx-background-color: rgba(138,43,226,0.18);",
                "    -fx-border-color: -wmrt-accent-2;",
                "    -fx-border-width: 0 0 2 0;",
                "}",
                "Button {",
                "    -fx-background-color: rgba(0,255,198,0.12);",
                "    -fx-border-color: -wmrt-accent;",
                "    -fx-border-radius: 10;",
                "    -fx-background-radius: 10;",
                "    -fx-text-fill: -wmrt-text;",
                "    -fx-cursor: hand;",
                "}",
                "Button:hover {",
                "    -fx-background-color: rgba(0,255,198,0.22);",
                "}",
                "TextField, PasswordField, ComboBox, TextArea {",
                "    -fx-background-color: -wmrt-surface;",
                "    -fx-text-fill: -wmrt-text;",
                "    -fx-background-radius: 8;",
                "    -fx-border-radius: 8;",
                "    -fx-border-color: rgba(255,255,255,0.08);",
                "}",
                "TableView {",
                "    -fx-background-color: -wmrt-surface;",
                "    -fx-background-radius: 10;",
                "    -fx-border-radius: 10;",
                "    -fx-border-color: rgba(255,255,255,0.06);",
                "}",
                "TableView .column-header-background { -fx-background-color: rgba(138,43,226,0.18); }",
                "TableView .column-header, TableView .filler {",
                "    -fx-size: 36px;",
                "    -fx-background-color: transparent;",
                "}",
                "TableRowCell:filled:selected, TableCell:filled:selected {",
                "    -fx-background-color: rgba(0,255,198,0.20);",
                "}",
                "Separator { -fx-background-color: rgba(255,255,255,0.08); }",
                "#routeCanvas, Canvas { -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 18, 0.2, 0, 4); }");
        try {
            if (Files.notExists(themePath)) {
                Files.write(themePath, css.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
            }
        } catch (IOException ignore) {
        }
    }

    private void ensureThemeOverrideFile() {
        Path themePath = Paths.get("theme-override.css");
        String css = String.join("\n",
                ".form-card {",
                "    -fx-background-color: rgba(19,26,34,0.8);",
                "    -fx-background-radius: 12;",
                "    -fx-padding: 14;",
                "    -fx-border-color: rgba(255,255,255,0.08);",
                "    -fx-border-radius: 12;",
                "}",
                ".form-card Label { -fx-text-fill: -wmrt-text; }",
                ".form-card TextField, .form-card ComboBox {",
                "    -fx-background-color: #0f151c;",
                "    -fx-text-fill: -wmrt-text;",
                "    -fx-border-color: rgba(0,255,198,0.25);",
                "}",
                "#submitRecordBtn {",
                "    -fx-background-color: linear-gradient(to right, rgba(0,255,198,0.25), rgba(138,43,226,0.25));",
                "}",
                "#submitRecordBtn:hover {",
                "    -fx-background-color: linear-gradient(to right, rgba(0,255,198,0.45), rgba(138,43,226,0.45));",
                "}",
                ".stats-card {",
                "    -fx-background-color: rgba(19,26,34,0.8);",
                "    -fx-background-radius: 12;",
                "    -fx-padding: 12;",
                "    -fx-border-color: rgba(255,255,255,0.08);",
                "    -fx-border-radius: 12;",
                "}",
                ".stats-card Label { -fx-text-fill: -wmrt-text; }",
                ".table-contrast .column-header-background { -fx-background-color: rgba(0,255,198,0.18); }",
                ".table-contrast .table-row-cell { -fx-background-color: rgba(19,26,34,0.6); }",
                ".table-contrast .table-cell { -fx-text-fill: -wmrt-text; }",
                ".date-input {",
                "    -fx-background-color: #0f151c;",
                "    -fx-text-fill: -wmrt-text;",
                "    -fx-border-color: rgba(138,43,226,0.35);",
                "}",
                ".login-pane {",
                "    -fx-background-color: linear-gradient(to bottom, rgba(11,15,20,0.95), rgba(12,17,24,0.95));",
                "    -fx-padding: 30;",
                "    -fx-background-radius: 16;",
                "    -fx-border-radius: 16;",
                "    -fx-border-color: rgba(0,255,198,0.25);",
                "}",
                ".login-pane Label { -fx-text-fill: -wmrt-text; }",
                ".login-title { -fx-text-fill: -wmrt-text; -fx-font-size: 22px; -fx-font-weight: bold; }");
        try {
            if (Files.notExists(themePath)) {
                Files.write(themePath, css.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
            }
        } catch (IOException ignore) {
        }
    }

    // ------------------- Change Password -------------------
    private void showChangePasswordDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Update your account password");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        PasswordField currentField = new PasswordField();
        currentField.setPromptText("Current password");
        PasswordField newField = new PasswordField();
        newField.setPromptText("New password (min 4 chars)");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm new password");
        Label message = new Label();
        message.setStyle("-fx-text-fill: #d32f2f;");

        grid.add(new Label("Current:"), 0, 0);
        grid.add(currentField, 1, 0);
        grid.add(new Label("New:"), 0, 1);
        grid.add(newField, 1, 1);
        grid.add(new Label("Confirm:"), 0, 2);
        grid.add(confirmField, 1, 2);
        grid.add(message, 1, 3);

        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            String current = currentField.getText() == null ? "" : currentField.getText();
            String next = newField.getText() == null ? "" : newField.getText();
            String confirm = confirmField.getText() == null ? "" : confirmField.getText();

            if (current.isEmpty() || next.isEmpty() || confirm.isEmpty()) {
                message.setText("Please fill in all fields.");
                evt.consume();
                return;
            }
            if (!Objects.equals(current, loggedInUser.password)) {
                message.setText("Current password is incorrect.");
                evt.consume();
                return;
            }
            if (next.length() < 4) {
                message.setText("New password must be at least 4 characters.");
                evt.consume();
                return;
            }
            if (!Objects.equals(next, confirm)) {
                message.setText("New passwords do not match.");
                evt.consume();
                return;
            }

            // Update in-memory and persist
            loggedInUser.password = next;
            // Also update the list entry (same reference) and save
            saveUsersToFile();

            // Inform user
            Alert ok = new Alert(Alert.AlertType.INFORMATION, "Password updated successfully.", ButtonType.OK);
            ok.setHeaderText(null);
            ok.showAndWait();
        });

        dialog.showAndWait();
    }

    // ------------------- Edit Status Dialog -------------------
    private void showEditStatusDialog(Center center, Label statusMessage, Runnable refreshCallback) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Edit Center Details");
        dialog.setHeaderText("Edit Status & Guidelines for " + center.getBarangayName() + " (" + center.getId() + ")");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #00ff88; -fx-border-width: 2px;");

        // Current Status Section
        Label currentStatusLabel = new Label("Current Status:");
        currentStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-font-size: 16px;");
        Label currentStatusValue = new Label(center.getStatus());
        currentStatusValue.setStyle("-fx-font-weight: bold; -fx-text-fill: #00ffc6; -fx-font-size: 16px;");

        // New Status Section
        Label newStatusLabel = new Label("New Status:");
        newStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-font-size: 16px;");
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Open", "Busy", "Closed");
        statusCombo.setValue(center.getStatus());
        statusCombo.setPrefWidth(200);

        // Simple and direct styling
        statusCombo.setStyle(
                "-fx-background-color: #ffffff; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-border-color: #00ff88; " +
                        "-fx-border-width: 2px; " +
                        "-fx-font-size: 14px;");

        // Guidelines Section
        Label guidelinesLabel = new Label("Guidelines:");
        guidelinesLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-font-size: 16px;");
        TextArea guidelinesArea = new TextArea(center.getGuidelines());
        guidelinesArea.setPromptText("Enter guidelines for this center...");
        guidelinesArea.setPrefRowCount(4);
        guidelinesArea.setPrefWidth(400);

        // Apply comprehensive TextArea styling
        guidelinesArea.setStyle(
                "-fx-background-color: #2a2a2a; " +
                        "-fx-text-fill: #ffffff; " +
                        "-fx-border-color: #00ff88; " +
                        "-fx-border-width: 2px; " +
                        "-fx-font-size: 14px; " +
                        "-fx-control-inner-background: #2a2a2a; " +
                        "-fx-prompt-text-fill: #cccccc; " +
                        "-fx-highlight-fill: #00ff88; " +
                        "-fx-highlight-text-fill: #ffffff;");

        // Reason Section
        Label reasonLabel = new Label("Status Change Reason (Optional):");
        reasonLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-font-size: 16px;");
        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Enter reason for status change...");
        reasonArea.setPrefRowCount(2);
        reasonArea.setPrefWidth(400);

        // Apply comprehensive TextArea styling
        reasonArea.setStyle(
                "-fx-background-color: #2a2a2a; " +
                        "-fx-text-fill: #ffffff; " +
                        "-fx-border-color: #00ff88; " +
                        "-fx-border-width: 2px; " +
                        "-fx-font-size: 14px; " +
                        "-fx-control-inner-background: #2a2a2a; " +
                        "-fx-prompt-text-fill: #cccccc; " +
                        "-fx-highlight-fill: #00ff88; " +
                        "-fx-highlight-text-fill: #ffffff;");

        Label message = new Label();
        message.setStyle("-fx-text-fill: #ff4444; -fx-font-weight: bold; -fx-font-size: 14px;");

        // Add components to grid
        grid.add(currentStatusLabel, 0, 0);
        grid.add(currentStatusValue, 1, 0);
        grid.add(newStatusLabel, 0, 1);
        grid.add(statusCombo, 1, 1);
        grid.add(guidelinesLabel, 0, 2);
        grid.add(guidelinesArea, 1, 2);
        grid.add(reasonLabel, 0, 3);
        grid.add(reasonArea, 1, 3);
        grid.add(message, 0, 4, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Apply theme to dialog pane
        dialog.getDialogPane().setStyle(
                "-fx-background-color: #1a1a1a; -fx-border-color: #00ff88; -fx-border-width: 2px; -fx-text-fill: #ffffff;");

        // Apply theme to buttons
        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        Node cancelButton = dialog.getDialogPane().lookupButton(cancelType);
        saveButton.setStyle(
                "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        cancelButton.setStyle("-fx-background-color: #757575; -fx-text-fill: white; -fx-font-size: 14px;");
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            String newStatus = statusCombo.getValue();
            String newGuidelines = guidelinesArea.getText() == null ? "" : guidelinesArea.getText().trim();
            String reason = reasonArea.getText() == null ? "" : reasonArea.getText().trim();

            if (newStatus == null || newStatus.isEmpty()) {
                message.setText("Please select a status!");
                evt.consume();
                return;
            }

            if (newGuidelines.isEmpty()) {
                message.setText("Please enter guidelines!");
                evt.consume();
                return;
            }

            // Check if anything actually changed
            boolean statusChanged = !newStatus.equals(center.getStatus());
            boolean guidelinesChanged = !newGuidelines.equals(center.getGuidelines());

            if (!statusChanged && !guidelinesChanged) {
                message.setText("No changes detected!");
                evt.consume();
                return;
            }

            // Update the center
            String oldStatus = center.getStatus();
            String oldGuidelines = center.getGuidelines();

            if (statusChanged) {
                center.setStatus(newStatus);
            }

            if (guidelinesChanged) {
                center.setGuidelines(newGuidelines);
            }

            // Add status change reason to guidelines if provided
            if (statusChanged && !reason.isEmpty()) {
                String statusNote = " | Status changed to " + newStatus + ": " + reason;
                center.setGuidelines(center.getGuidelines() + statusNote);
            }

            // Create success message
            StringBuilder successMsg = new StringBuilder();
            if (statusChanged) {
                successMsg.append("Status updated from ").append(oldStatus).append(" to ").append(newStatus);
            }
            if (guidelinesChanged) {
                if (statusChanged)
                    successMsg.append(" and ");
                successMsg.append("Guidelines updated");
            }
            successMsg.append(" for ").append(center.getBarangayName());

            statusMessage.setText(successMsg.toString());
            statusMessage.setStyle("-fx-text-fill: #4CAF50;");

            // Refresh the map and table if callback is provided
            if (refreshCallback != null) {
                refreshCallback.run();
            }

            // Note: Table will automatically refresh due to ObservableList
        });

        dialog.showAndWait();
    }

    // ------------------- Main Dashboard -------------------
    private void showDashboard() {
        BorderPane root = new BorderPane();
        TabPane tabPane = new TabPane();
        mainTabPane = tabPane;

        // Role-based tab assignment
        if (loggedInUser.role.equals("admin")) {
            // Admin sees admin, collection route, and docs tabs
            tabPane.getTabs().addAll(buildAdminTab(), buildCollectionRouteTab(), buildSubmissionPointsTab(),
                    buildDocsTab());
        } else {
            // Regular users see resident and collection route tabs
            tabPane.getTabs().addAll(buildResidentTab(), buildCollectionRouteTab(), buildSubmissionPointsTab());
        }

        root.setCenter(tabPane);

        // ------------------- Account Buttons -------------------
        Button changePwdButton = new Button("Change Password");
        changePwdButton.setOnAction(e -> showChangePasswordDialog());
        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(e -> {
            loggedInUser = null;
            showLoginScreen(); // Return to login screen
        });

        HBox topBar = new HBox();
        topBar.setPadding(new Insets(10));
        topBar.setSpacing(10);
        // Theme toggle
        CheckBox themeToggle = new CheckBox("Neon Theme");
        themeToggle.setSelected(themeEnabled);
        themeToggle.setOnAction(ev -> {
            themeEnabled = themeToggle.isSelected();
            if (primaryStage != null && primaryStage.getScene() != null) {
                try {
                    String themeUri = java.nio.file.Paths.get("theme.css").toUri().toString();
                    if (themeEnabled) {
                        if (!primaryStage.getScene().getStylesheets().contains(themeUri)) {
                            primaryStage.getScene().getStylesheets().add(themeUri);
                        }
                    } else {
                        primaryStage.getScene().getStylesheets().remove(themeUri);
                    }
                } catch (Exception ignore) {
                }
            }
        });
        topBar.getChildren().addAll(new Label("Logged in as: " + loggedInUser.username), themeToggle, changePwdButton,
                logoutButton);

        root.setTop(topBar);

        Scene scene = new Scene(root);
        // Apply futuristic theme (dark neon)
        applyTheme(scene);

        // Set fullscreen for immersive experience (initStyle already set in start())
        primaryStage.setScene(scene);
        primaryStage.setTitle("WMRT Dashboard - " + loggedInUser.username);
        primaryStage.setMaximized(true); // Maximize to fill screen
        primaryStage.setFullScreen(true); // True fullscreen
        primaryStage.setFullScreenExitHint(""); // No exit hint

        if (!primaryStage.isShowing()) {
            primaryStage.show();
        }
    }

    // ------------------- Resident Tab -------------------
    private Tab buildResidentTab() {
        Tab tab = new Tab("Resident");
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        Label lbl = new Label("Resident Dashboard - Submit Recycling Records");

        // Use shared approved submissions list

        // Submission Form
        Label formLabel = new Label("Submit New Recycling Record:");
        formLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane form = new GridPane();
        form.setPadding(new Insets(10));
        form.setHgap(10);
        form.setVgap(10);
        form.getStyleClass().add("form-card");

        // Date input (auto-populated with current date)
        Label dateLabel = new Label("Date:");
        TextField dateField = new TextField();
        dateField.setPromptText("YYYY-MM-DD");
        dateField.setEditable(false); // Make read-only since it's auto-populated
        dateField.setStyle("");
        dateField.getStyleClass().add("date-input");

        // Set current date automatically
        java.time.LocalDate today = java.time.LocalDate.now();
        dateField.setText(today.toString()); // Format: YYYY-MM-DD

        form.add(dateLabel, 0, 0);
        form.add(dateField, 1, 0);

        // Material input
        Label materialLabel = new Label("Material:");
        ComboBox<String> materialCombo = new ComboBox<>();
        materialCombo.getItems().addAll("Plastic", "Glass", "Paper", "Metal", "Cardboard", "Aluminum");
        materialCombo.setPromptText("Select material type");
        form.add(materialLabel, 0, 1);
        form.add(materialCombo, 1, 1);

        // Weight input
        Label weightLabel = new Label("Weight (kg):");
        TextField weightField = new TextField();
        weightField.setPromptText("Enter weight (e.g., 2.5)");
        form.add(weightLabel, 0, 2);
        form.add(weightField, 1, 2);

        // Submit button
        Button submitButton = new Button("Submit Recycling Record");
        submitButton.setId("submitRecordBtn");
        submitButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        form.add(submitButton, 1, 3);

        // Status label
        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #d32f2f;");
        form.add(statusLabel, 1, 4);

        // Submit button action
        submitButton.setOnAction(e -> {
            try {
                String date = dateField.getText().trim(); // This will always have today's date
                String material = materialCombo.getValue();
                String weightText = weightField.getText().trim();

                // Validation (date is always filled, so only check material and weight)
                if (material == null || weightText.isEmpty()) {
                    statusLabel.setText("Please select a material and enter weight!");
                    return;
                }

                double weight = Double.parseDouble(weightText);
                if (weight <= 0) {
                    statusLabel.setText("Weight must be greater than 0!");
                    return;
                }

                // Add new submission to pending queue for admin verification
                String submissionId = "SUB-" + String.format("%04d", submissionCounter++);
                PendingSubmission pendingSubmission = new PendingSubmission(
                        submissionId, date, material, weight, loggedInUser.username);
                pendingSubmissions.add(pendingSubmission);
                savePendingToFile();

                // Clear caches since data has changed
                clearSubmissionCaches();

                // Clear form (date stays the same, only clear material and weight)
                materialCombo.setValue(null);
                weightField.clear();
                statusLabel.setText("Submission sent for verification! ID: " + submissionId);
                statusLabel.setStyle("-fx-text-fill: #4CAF50;");

            } catch (NumberFormatException ex) {
                statusLabel.setText("Please enter a valid weight number (e.g., 2.5)!");
                statusLabel.setStyle("-fx-text-fill: #d32f2f;");
            }
        });

        // Table with optimized data structures
        TableView<Submission> table = new TableView<>();
        table.getStyleClass().add("table-contrast");

        TableColumn<Submission, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        TableColumn<Submission, String> matCol = new TableColumn<>("Material");
        matCol.setCellValueFactory(new PropertyValueFactory<>("material"));
        TableColumn<Submission, Double> wtCol = new TableColumn<>("Weight (kg)");
        wtCol.setCellValueFactory(new PropertyValueFactory<>("weight"));
        TableColumn<Submission, String> byCol = new TableColumn<>("Submitted By");
        byCol.setCellValueFactory(new PropertyValueFactory<>("submittedBy"));
        table.getColumns().add(dateCol);
        table.getColumns().add(matCol);
        table.getColumns().add(wtCol);
        table.getColumns().add(byCol);

        // Enable sorting on all columns
        dateCol.setSortable(true);
        matCol.setSortable(true);
        wtCol.setSortable(true);

        // Create a modifiable list for the table
        ObservableList<Submission> tableItems = FXCollections.observableArrayList();

        // Initialize with user's submissions
        tableItems.addAll(getUserSubmissions(loggedInUser.username));
        table.setItems(tableItems);

        // Make date column sort by actual date value (YYYY-MM-DD)
        dateCol.setComparator((a, b) -> {
            if (a == null && b == null)
                return 0;
            if (a == null)
                return -1;
            if (b == null)
                return 1;
            try {
                java.time.LocalDate da = java.time.LocalDate.parse(a);
                java.time.LocalDate db = java.time.LocalDate.parse(b);
                return da.compareTo(db);
            } catch (Exception ex) {
                return a.compareTo(b);
            }
        });

        // Search and sorting controls
        HBox controlsBox = new HBox(10);
        controlsBox.setPadding(new Insets(5));

        TextField searchField = new TextField();
        searchField.setPromptText("Search material...");
        // Search functionality with real-time filtering
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String keyword = newValue == null ? "" : newValue.toLowerCase();
            if (keyword.isEmpty()) {
                // Show all user submissions
                tableItems.setAll(getUserSubmissions(loggedInUser.username));
            } else {
                // Filter by material
                List<Submission> filtered = getUserSubmissions(loggedInUser.username).stream()
                        .filter(s -> s.getMaterial().toLowerCase().contains(keyword))
                        .collect(Collectors.toList());
                tableItems.setAll(filtered);
            }
        });

        // Sorting controls with Tim Sort
        Label sortLabel = new Label("Sort by (Tim Sort):");
        ComboBox<String> sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll("Date (Newest First)", "Date (Oldest First)", "Material (A-Z)", "Material (Z-A)",
                "Weight (High to Low)", "Weight (Low to High)");
        sortCombo.setValue("Date (Newest First)"); // Default sorting

        sortCombo.setOnAction(e -> {
            String sortOption = sortCombo.getValue();
            List<Submission> userSubmissions = new ArrayList<>(getUserSubmissions(loggedInUser.username));

            // Apply Tim Sort based on selected option
            switch (sortOption) {
                case "Date (Newest First)":
                    TimSort.sort(userSubmissions, (a, b) -> {
                        try {
                            java.time.LocalDate da = java.time.LocalDate.parse(a.getDate());
                            java.time.LocalDate db = java.time.LocalDate.parse(b.getDate());
                            return db.compareTo(da); // Descending (newest first)
                        } catch (Exception ex) {
                            return b.getDate().compareTo(a.getDate());
                        }
                    });
                    break;
                case "Date (Oldest First)":
                    TimSort.sort(userSubmissions, (a, b) -> {
                        try {
                            java.time.LocalDate da = java.time.LocalDate.parse(a.getDate());
                            java.time.LocalDate db = java.time.LocalDate.parse(b.getDate());
                            return da.compareTo(db); // Ascending (oldest first)
                        } catch (Exception ex) {
                            return a.getDate().compareTo(b.getDate());
                        }
                    });
                    break;
                case "Material (A-Z)":
                    TimSort.sort(userSubmissions, (a, b) -> a.getMaterial().compareTo(b.getMaterial()));
                    break;
                case "Material (Z-A)":
                    TimSort.sort(userSubmissions, (a, b) -> b.getMaterial().compareTo(a.getMaterial()));
                    break;
                case "Weight (High to Low)":
                    TimSort.sort(userSubmissions, (a, b) -> Double.compare(b.getWeight(), a.getWeight()));
                    break;
                case "Weight (Low to High)":
                    TimSort.sort(userSubmissions, (a, b) -> Double.compare(a.getWeight(), b.getWeight()));
                    break;
            }

            // Update the table with sorted data using setAll for proper refresh
            tableItems.setAll(userSubmissions);
        });

        controlsBox.getChildren().addAll(searchField, sortLabel, sortCombo);

        // Add separator
        Separator separator = new Separator();

        layout.getChildren().addAll(lbl, formLabel, form, separator, controlsBox, table);

        // Export to PDF (via system PDF printer)
        Button exportPdfButton = new Button("Download PDF of My Records");
        exportPdfButton.setOnAction(e -> {
            // Use the cached sorted list for PDF export
            SortedList<Submission> userSorted = getUserSortedSubmissions(loggedInUser.username);
            List<Submission> toPrint = new ArrayList<>(userSorted);
            VBox printable = buildUserRecordPrintNode(loggedInUser.username, toPrint);
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job != null && job.showPrintDialog(primaryStage)) {
                boolean success = job.printPage(printable);
                if (success) {
                    job.endJob();
                }
            }
        });

        layout.getChildren().add(exportPdfButton);
        tab.setContent(layout);
        return tab;
    }

    // ------------------- Admin Tab -------------------
    private Tab buildAdminTab() {
        Tab tab = new Tab("Admin");
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        Label lbl = new Label("Admin Dashboard - Verification & Analytics");
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        layout.getChildren().add(lbl);

        // Create tabbed interface for admin functions
        TabPane adminTabs = new TabPane();

        // Verification Tab
        Tab verificationTab = new Tab("Verify Submissions");
        VBox verificationLayout = new VBox(10);
        verificationLayout.setPadding(new Insets(10));

        Label verificationLabel = new Label("Pending Submissions for Verification:");
        verificationLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Pending submissions table
        TableView<PendingSubmission> pendingTable = new TableView<>();
        TableColumn<PendingSubmission, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<PendingSubmission, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        TableColumn<PendingSubmission, String> materialCol = new TableColumn<>("Material");
        materialCol.setCellValueFactory(new PropertyValueFactory<>("material"));
        TableColumn<PendingSubmission, Double> weightCol = new TableColumn<>("Weight (kg)");
        weightCol.setCellValueFactory(new PropertyValueFactory<>("weight"));
        TableColumn<PendingSubmission, String> submittedByCol = new TableColumn<>("Submitted By");
        submittedByCol.setCellValueFactory(new PropertyValueFactory<>("submittedBy"));

        // Enable sorting on all columns
        idCol.setSortable(true);
        dateCol.setSortable(true);
        materialCol.setSortable(true);
        weightCol.setSortable(true);
        submittedByCol.setSortable(true);

        pendingTable.getColumns().add(idCol);
        pendingTable.getColumns().add(dateCol);
        pendingTable.getColumns().add(materialCol);
        pendingTable.getColumns().add(weightCol);
        pendingTable.getColumns().add(submittedByCol);
        // Sorting for pending table
        dateCol.setComparator((a, b) -> {
            if (a == null && b == null)
                return 0;
            if (a == null)
                return -1;
            if (b == null)
                return 1;
            try {
                java.time.LocalDate da = java.time.LocalDate.parse(a);
                java.time.LocalDate db = java.time.LocalDate.parse(b);
                return da.compareTo(db);
            } catch (Exception ex) {
                return a.compareTo(b);
            }
        });

        // Create a modifiable list for the pending table
        ObservableList<PendingSubmission> pendingTableItems = FXCollections.observableArrayList();
        pendingTableItems.addAll(pendingSubmissions);
        pendingTable.setItems(pendingTableItems);

        // Add sorting controls for pending submissions with Tim Sort
        HBox pendingSortBox = new HBox(10);
        pendingSortBox.setPadding(new Insets(5));

        Label pendingSortLabel = new Label("Sort pending by (Tim Sort):");
        ComboBox<String> pendingSortCombo = new ComboBox<>();
        pendingSortCombo.getItems().addAll("Date (Newest First)", "Date (Oldest First)", "Material (A-Z)",
                "Weight (High to Low)", "Submitted By (A-Z)");
        pendingSortCombo.setValue("Date (Newest First)"); // Default sorting

        pendingSortCombo.setOnAction(e -> {
            String sortOption = pendingSortCombo.getValue();
            List<PendingSubmission> pendingList = new ArrayList<>(pendingSubmissions);

            // Apply Tim Sort based on selected option
            switch (sortOption) {
                case "Date (Newest First)":
                    TimSort.sort(pendingList, (a, b) -> {
                        try {
                            java.time.LocalDate da = java.time.LocalDate.parse(a.getDate());
                            java.time.LocalDate db = java.time.LocalDate.parse(b.getDate());
                            return db.compareTo(da); // Descending (newest first)
                        } catch (Exception ex) {
                            return b.getDate().compareTo(a.getDate());
                        }
                    });
                    break;
                case "Date (Oldest First)":
                    TimSort.sort(pendingList, (a, b) -> {
                        try {
                            java.time.LocalDate da = java.time.LocalDate.parse(a.getDate());
                            java.time.LocalDate db = java.time.LocalDate.parse(b.getDate());
                            return da.compareTo(db); // Ascending (oldest first)
                        } catch (Exception ex) {
                            return a.getDate().compareTo(b.getDate());
                        }
                    });
                    break;
                case "Material (A-Z)":
                    TimSort.sort(pendingList, (a, b) -> a.getMaterial().compareTo(b.getMaterial()));
                    break;
                case "Weight (High to Low)":
                    TimSort.sort(pendingList, (a, b) -> Double.compare(b.getWeight(), a.getWeight()));
                    break;
                case "Submitted By (A-Z)":
                    TimSort.sort(pendingList, (a, b) -> a.getSubmittedBy().compareTo(b.getSubmittedBy()));
                    break;
            }

            // Update the table with sorted data using setAll for proper refresh
            pendingTableItems.setAll(pendingList);
        });

        pendingSortBox.getChildren().addAll(pendingSortLabel, pendingSortCombo);

        // Action buttons
        HBox buttonBox = new HBox(10);
        Button approveButton = new Button("Approve Selected");
        approveButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        Button rejectButton = new Button("Reject Selected");
        rejectButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #d32f2f;");

        // Approve button action
        approveButton.setOnAction(e -> {
            PendingSubmission selected = pendingTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                // Add to approved submissions
                approvedSubmissions.add(new Submission(selected.getDate(), selected.getMaterial(), selected.getWeight(),
                        selected.getSubmittedBy()));
                // Remove from pending
                pendingSubmissions.remove(selected);
                saveApprovedToFile();
                savePendingToFile();

                // Clear caches since data has changed
                clearSubmissionCaches();

                statusLabel.setText("Submission " + selected.getId() + " approved!");
                statusLabel.setStyle("-fx-text-fill: #4CAF50;");
            } else {
                statusLabel.setText("Please select a submission to approve!");
                statusLabel.setStyle("-fx-text-fill: #d32f2f;");
            }
        });

        // Reject button action
        rejectButton.setOnAction(e -> {
            PendingSubmission selected = pendingTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                // Remove from pending (rejected)
                pendingSubmissions.remove(selected);
                savePendingToFile();
                statusLabel.setText("Submission " + selected.getId() + " rejected!");
                statusLabel.setStyle("-fx-text-fill: #f44336;");
            } else {
                statusLabel.setText("Please select a submission to reject!");
                statusLabel.setStyle("-fx-text-fill: #d32f2f;");
            }
        });

        buttonBox.getChildren().addAll(approveButton, rejectButton, statusLabel);
        verificationLayout.getChildren().addAll(verificationLabel, pendingSortBox, pendingTable, buttonBox);
        verificationTab.setContent(verificationLayout);

        // Analytics Tab
        Tab analyticsTab = new Tab("Analytics");
        VBox analyticsLayout = new VBox(10);
        analyticsLayout.setPadding(new Insets(10));

        Label analyticsLabel = new Label("Recycling Analytics:");
        analyticsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Chart
        PieChart chart = new PieChart();
        chart.getData().addAll(
                new PieChart.Data("Plastic", 40),
                new PieChart.Data("Glass", 30),
                new PieChart.Data("Paper", 20),
                new PieChart.Data("Metal", 10));

        // Approved submissions table for analytics
        Label approvedLabel = new Label("Approved Submissions:");
        approvedLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        TableView<Submission> approvedTable = new TableView<>();
        TableColumn<Submission, String> approvedDateCol = new TableColumn<>("Date");
        approvedDateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        TableColumn<Submission, String> approvedMatCol = new TableColumn<>("Material");
        approvedMatCol.setCellValueFactory(new PropertyValueFactory<>("material"));
        TableColumn<Submission, Double> approvedWtCol = new TableColumn<>("Weight (kg)");
        approvedWtCol.setCellValueFactory(new PropertyValueFactory<>("weight"));

        // Enable sorting on all columns
        approvedDateCol.setSortable(true);
        approvedMatCol.setSortable(true);
        approvedWtCol.setSortable(true);

        approvedTable.getColumns().add(approvedDateCol);
        approvedTable.getColumns().add(approvedMatCol);
        approvedTable.getColumns().add(approvedWtCol);

        // Create a modifiable list for the approved table
        ObservableList<Submission> approvedTableItems = FXCollections.observableArrayList();
        approvedTableItems.addAll(approvedSubmissions);
        approvedTable.setItems(approvedTableItems);
        // Proper date comparator for approved date column
        approvedDateCol.setComparator((a, b) -> {
            if (a == null && b == null)
                return 0;
            if (a == null)
                return -1;
            if (b == null)
                return 1;
            try {
                java.time.LocalDate da = java.time.LocalDate.parse(a);
                java.time.LocalDate db = java.time.LocalDate.parse(b);
                return da.compareTo(db);
            } catch (Exception ex) {
                return a.compareTo(b);
            }
        });

        // Sorting controls for approved submissions with Tim Sort
        HBox approvedSortBox = new HBox(10);
        approvedSortBox.setPadding(new Insets(5));

        Label approvedSortLabel = new Label("Sort approved by (Tim Sort):");
        ComboBox<String> approvedSortCombo = new ComboBox<>();
        approvedSortCombo.getItems().addAll("Date (Newest First)", "Date (Oldest First)", "Material (A-Z)",
                "Weight (High to Low)");
        approvedSortCombo.setValue("Date (Newest First)"); // Default sorting

        approvedSortCombo.setOnAction(e -> {
            String sortOption = approvedSortCombo.getValue();
            List<Submission> approvedList = new ArrayList<>(approvedSubmissions);

            // Apply Tim Sort based on selected option
            switch (sortOption) {
                case "Date (Newest First)":
                    TimSort.sort(approvedList, (a, b) -> {
                        try {
                            java.time.LocalDate da = java.time.LocalDate.parse(a.getDate());
                            java.time.LocalDate db = java.time.LocalDate.parse(b.getDate());
                            return db.compareTo(da); // Descending (newest first)
                        } catch (Exception ex) {
                            return b.getDate().compareTo(a.getDate());
                        }
                    });
                    break;
                case "Date (Oldest First)":
                    TimSort.sort(approvedList, (a, b) -> {
                        try {
                            java.time.LocalDate da = java.time.LocalDate.parse(a.getDate());
                            java.time.LocalDate db = java.time.LocalDate.parse(b.getDate());
                            return da.compareTo(db); // Ascending (oldest first)
                        } catch (Exception ex) {
                            return a.getDate().compareTo(b.getDate());
                        }
                    });
                    break;
                case "Material (A-Z)":
                    TimSort.sort(approvedList, (a, b) -> a.getMaterial().compareTo(b.getMaterial()));
                    break;
                case "Weight (High to Low)":
                    TimSort.sort(approvedList, (a, b) -> Double.compare(b.getWeight(), a.getWeight()));
                    break;
            }

            // Update the table with sorted data using setAll for proper refresh
            approvedTableItems.setAll(approvedList);
        });

        approvedSortBox.getChildren().addAll(approvedSortLabel, approvedSortCombo);

        analyticsLayout.getChildren().addAll(analyticsLabel, chart, approvedLabel, approvedSortBox, approvedTable);
        analyticsTab.setContent(analyticsLayout);

        adminTabs.getTabs().addAll(verificationTab, analyticsTab);
        layout.getChildren().add(adminTabs);

        tab.setContent(layout);
        return tab;
    }

    // ------------------- Collection Route Tab -------------------
    private Tab buildCollectionRouteTab() {
        Tab tab = new Tab("Collection Route");
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        Label routeLabel = new Label("Interactive Collection Route Map");
        routeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        // Create WebView for map display
        WebView mapView = new WebView();
        WebEngine webEngine = mapView.getEngine();

        // Set WebView size
        mapView.setPrefHeight(550);
        mapView.setMinHeight(400);

        // Load the map HTML file
        try {
            java.io.File mapFile = new java.io.File("map-viewer.html");
            String mapUrl = mapFile.toURI().toString();
            webEngine.load(mapUrl);

            // Wait for page to load, then populate map with data
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    System.out.println("Collection route map loaded, waiting for tiles...");

                    // Add delay in background thread to ensure map tiles are fully loaded
                    new Thread(() -> {
                        try {
                            Thread.sleep(3000); // Wait 3 seconds for map tiles to load
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        System.out.println("Starting to add markers to collection route map...");
                        System.out.println("Total centers to add: " + store.centers.size());

                        javafx.application.Platform.runLater(() -> {
                            // Clear existing markers
                            executeMapScript(webEngine, "clearAllMarkers()");

                            // Add depot marker (Bacolod City Hall area)
                            executeMapScript(webEngine, String.format("addDepot(%.6f, %.6f)", 10.6762, 122.9501));

                            // Add collection center markers
                            for (Center center : store.centers) {
                                String script = String.format(
                                        "addCenter('%s', %.6f, %.6f, '%s', '%s', '%s')",
                                        center.id,
                                        center.lat,
                                        center.lng,
                                        center.barangayName,
                                        center.status,
                                        center.guidelines.replace("'", "\\\\'") // Escape single quotes
                                );
                                executeMapScript(webEngine, script);
                            }

                            // Calculate and draw route
                            routeService.build(store.centers);
                            List<String> targets = store.centers.stream().map(c -> c.id).collect(Collectors.toList());
                            List<String> route = routeService.shortestVisitOrder("DEPOT", targets);

                            // Build route points array for map
                            StringBuilder routePoints = new StringBuilder("[");
                            for (String nodeId : route) {
                                RouteService.Node node = routeService.getNodes().get(nodeId);
                                if (node != null) {
                                    routePoints.append(String.format("[%.6f, %.6f],", node.lat, node.lng));
                                }
                            }
                            if (routePoints.length() > 1) {
                                routePoints.setLength(routePoints.length() - 1); // Remove trailing comma
                            }
                            routePoints.append("]");

                            // Draw route on map with blue color
                            String routeScript = String.format("drawRoute(%s, '#2196F3')", routePoints.toString());
                            executeMapScript(webEngine, routeScript);

                            // Calculate total distance
                            double totalDistance = 0.0;
                            for (int i = 0; i < route.size() - 1; i++) {
                                RouteService.Node from = routeService.getNodes().get(route.get(i));
                                RouteService.Node to = routeService.getNodes().get(route.get(i + 1));
                                double dx = from.lat - to.lat;
                                double dy = from.lng - to.lng;
                                totalDistance += Math.sqrt(dx * dx + dy * dy);
                            }

                            // Update map info panel
                            String infoScript = String.format(
                                    "updateInfo('%.2f km', '%d centers', '~%d min')",
                                    totalDistance * 111.0, // Approximate km conversion (1 degree ≈ 111 km)
                                    store.centers.size(),
                                    (int) (totalDistance * 111.0 * 3) // Rough estimate: 3 min per km
                            );
                            executeMapScript(webEngine, infoScript);

                            System.out.println("Collection route map fully loaded!");
                        });
                    }).start();
                }
            });
        } catch (Exception e) {
            System.err.println("Error loading map: " + e.getMessage());
            e.printStackTrace();
            Label errorLabel = new Label(
                    "Error loading map. Please ensure map-viewer.html exists in the project directory.");
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            layout.getChildren().addAll(routeLabel, errorLabel);
            tab.setContent(layout);
            return tab;
        }

        // Route statistics summary
        Label routeStatsLabel = new Label("Route Optimization:");
        routeStatsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Calculate and display route statistics
        routeService.build(store.centers);
        List<String> targets = store.centers.stream().map(c -> c.id).collect(Collectors.toList());
        List<String> route = routeService.shortestVisitOrder("DEPOT", targets);

        double totalDistance = 0.0;
        for (int i = 0; i < route.size() - 1; i++) {
            RouteService.Node from = routeService.getNodes().get(route.get(i));
            RouteService.Node to = routeService.getNodes().get(route.get(i + 1));
            double dx = from.lat - to.lat;
            double dy = from.lng - to.lng;
            totalDistance += Math.sqrt(dx * dx + dy * dy);
        }

        Label statsLabel = new Label();
        statsLabel.setText(String.format(
                "🚛 Total Centers: %d | 📍 Total Distance: ~%.2f km | ⚡ Algorithm: Dijkstra's Shortest Path with Greedy Selection",
                store.centers.size(),
                totalDistance * 111.0 // Convert to approximate km
        ));
        statsLabel.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 12px;");
        statsLabel.setWrapText(true);

        // Route details in text form
        Label routeDetailsLabel = new Label("Route Order: " + String.join(" → ", route));
        routeDetailsLabel.setWrapText(true);
        routeDetailsLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-padding: 10;");

        // Fix for map rendering issues when tab is initially hidden
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                System.out.println("Collection Route tab selected - refreshing map view");
                // Small delay to allow layout to settle
                new Thread(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                    javafx.application.Platform.runLater(() -> {
                        executeMapScript(webEngine, "map.invalidateSize();");
                    });
                }).start();
            }
        });

        // Refresh button
        Button refreshBtn = new Button("Refresh Map");
        refreshBtn.setOnAction(e -> {
            webEngine.reload();
        });

        HBox controls = new HBox(10, routeLabel, refreshBtn);
        controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        layout.getChildren().addAll(controls, mapView, routeStatsLabel, statsLabel, routeDetailsLabel);
        tab.setContent(layout);
        return tab;
    }

    // Helper method to execute JavaScript on the map
    private void executeMapScript(WebEngine webEngine, String script) {
        try {
            System.out.println("Executing map script: " + script);
            Object result = webEngine.executeScript(script);
            System.out.println("Script executed successfully. Result: " + result);
        } catch (Exception e) {
            System.err.println("Error executing map script: " + script);
            e.printStackTrace();
        }
    }

    // Helper method to update map with filtered centers
    private void updateMapWithFilteredCenters(WebEngine webEngine, List<Center> centers) {
        javafx.application.Platform.runLater(() -> {
            // Clear existing markers
            executeMapScript(webEngine, "clearAllMarkers()");

            // Add depot
            executeMapScript(webEngine, String.format("addDepot(%.6f, %.6f)", 10.6762, 122.9501));

            // Add only the filtered centers (using submission marker style)
            for (Center center : centers) {
                String script = String.format(
                        "addSubmissionMarker('%s', %.6f, %.6f, '%s', '%s', '%s')",
                        center.id,
                        center.lat,
                        center.lng,
                        center.barangayName,
                        center.status,
                        center.guidelines.replace("'", "\\\\'") // Escape single quotes
                );
                executeMapScript(webEngine, script);
            }
        });
    }

    // ------------------- Docs Tab -------------------
    private Tab buildDocsTab() {
        Tab tab = new Tab("Docs");

        String docs = """
                WMRTApp
                - Promotes sustainable cities via digital recycling participation and efficient collection routing.

                ### Keyboard Tips
                - In Resident tab, type in the Search box and press Enter to quickly filter.
                """;

        TextArea ta = new TextArea(docs);
        ta.setWrapText(true);
        ta.setEditable(false);
        ta.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12;");

        VBox layout = new VBox(ta);
        layout.setPadding(new Insets(10));

        tab.setContent(layout);
        return tab;
    }

    // ------------------- Submission Points Tab -------------------
    private Tab buildSubmissionPointsTab() {
        Tab tab = new Tab("Submission Points");
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        Label header = new Label("Available Submission Points");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        // Controls
        HBox controls = new HBox(10);
        TextField search = new TextField();
        search.setPromptText("Search by ID, barangay, status, or guideline text...");
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All", "Open", "Busy", "Closed");
        statusFilter.setValue("All");
        CheckBox onlyOpen = new CheckBox("Only Open");
        controls.getChildren().addAll(search, new Label("Status:"), statusFilter, onlyOpen);

        // Table
        TableView<Center> table = new TableView<>();
        // table.getStyleClass().add("table-contrast"); // Temporarily disabled
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Center, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cellData -> {
            Center center = cellData.getValue();
            return new javafx.beans.property.SimpleStringProperty(center != null ? center.getId() : "");
        });
        TableColumn<Center, String> barangayCol = new TableColumn<>("Barangay");
        barangayCol.setCellValueFactory(cellData -> {
            Center center = cellData.getValue();
            return new javafx.beans.property.SimpleStringProperty(center != null ? center.getBarangayName() : "");
        });
        TableColumn<Center, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> {
            Center center = cellData.getValue();
            return new javafx.beans.property.SimpleStringProperty(center != null ? center.getStatus() : "");
        });
        TableColumn<Center, String> guideCol = new TableColumn<>("Guidelines");
        guideCol.setCellValueFactory(cellData -> {
            Center center = cellData.getValue();
            return new javafx.beans.property.SimpleStringProperty(center != null ? center.getGuidelines() : "");
        });
        TableColumn<Center, Double> latCol = new TableColumn<>("Lat");
        latCol.setCellValueFactory(new PropertyValueFactory<>("lat"));
        TableColumn<Center, Double> lngCol = new TableColumn<>("Lng");
        lngCol.setCellValueFactory(new PropertyValueFactory<>("lng"));
        idCol.setSortable(true);
        barangayCol.setSortable(true);
        statusCol.setSortable(true);
        guideCol.setSortable(true);
        latCol.setSortable(true);
        lngCol.setSortable(true);

        // Set column widths for better visibility
        idCol.setPrefWidth(80);
        barangayCol.setPrefWidth(120);
        statusCol.setPrefWidth(80);
        guideCol.setPrefWidth(300);
        latCol.setPrefWidth(80);
        lngCol.setPrefWidth(80);

        // Add columns to table first
        table.getColumns().addAll(idCol, barangayCol, statusCol, guideCol, latCol, lngCol);

        FilteredList<Center> filtered = new FilteredList<>(FXCollections.observableArrayList(store.centers), c -> true);
        SortedList<Center> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());

        table.setItems(sorted);

        // Interactive WebView map for submission points
        WebView mapView = new WebView();
        WebEngine webEngine = mapView.getEngine();

        // Smaller height for minimap feel
        mapView.setPrefHeight(350);
        mapView.setMinHeight(300);

        Button refreshMapBtn = new Button("Refresh Map");
        refreshMapBtn.setOnAction(e -> {
            webEngine.reload();
        });

        HBox mapHeader = new HBox(10, new Label("Interactive Map"), refreshMapBtn);
        mapHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label mapLabel = new Label();
        mapLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Load the map HTML file
        try {
            java.io.File mapFile = new java.io.File("map-viewer.html");
            String mapUrl = mapFile.toURI().toString();

            System.out.println("Loading submission points map from: " + mapUrl);
            System.out.println("File exists: " + mapFile.exists());
            System.out.println("File absolute path: " + mapFile.getAbsolutePath());

            webEngine.load(mapUrl);

            // Wait for page to load, then populate map with delay
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                System.out.println("Submission points map state changed: " + oldState + " -> " + newState);

                if (newState == Worker.State.SUCCEEDED) {
                    System.out.println("Submission points map loaded, waiting for tiles...");

                    // Add delay to ensure map tiles are fully loaded
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000); // Wait 2 seconds
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        System.out.println("Loading centers on submission points map...");
                        // Initial load - show all centers
                        updateMapWithFilteredCenters(webEngine, sorted);
                    }).start();
                } else if (newState == Worker.State.FAILED) {
                    System.err.println("Failed to load submission points map!");
                    System.err.println("Error: " + webEngine.getLoadWorker().getException());
                }
            });
        } catch (Exception e) {
            System.err.println("Error loading submission points map: " + e.getMessage());
            e.printStackTrace();
        }

        // Apply filter logic with map update
        Runnable applyFilter = () -> {
            String text = search.getText();
            String q = text == null ? "" : text.toLowerCase();
            String statusSel = statusFilter.getValue();
            filtered.setPredicate(c -> {
                boolean matchesQuery = q.isEmpty() || c.id.toLowerCase().contains(q) ||
                        c.barangayName.toLowerCase().contains(q) || c.status.toLowerCase().contains(q) ||
                        (c.guidelines != null && c.guidelines.toLowerCase().contains(q));
                boolean matchesStatus = "All".equals(statusSel) || c.status.equalsIgnoreCase(statusSel);
                boolean matchesOnlyOpen = !onlyOpen.isSelected() || "Open".equalsIgnoreCase(c.status);
                return matchesQuery && matchesStatus && matchesOnlyOpen;
            });

            // Update map to show only filtered centers
            updateMapWithFilteredCenters(webEngine, sorted);
        };

        // Table selection listener - highlight selected center on map
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                executeMapScript(webEngine, "highlightCenter('" + newVal.getId() + "')");
            }
        });

        // Store applyFilter in a final variable for use in lambda
        final Runnable refreshMapAndFilter = applyFilter;

        // Admin controls for editing submission points
        if (loggedInUser != null && "admin".equals(loggedInUser.role)) {
            HBox adminControls = new HBox(10);
            adminControls.setPadding(new Insets(10));

            Label adminLabel = new Label("Admin Controls:");
            adminLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #00ffc6;");

            Button editStatusButton = new Button("Edit Selected Center Status");
            editStatusButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");

            Label statusMessage = new Label();
            statusMessage.setStyle("-fx-text-fill: #00ffc6;");

            editStatusButton.setOnAction(e -> {
                Center selectedCenter = table.getSelectionModel().getSelectedItem();
                if (selectedCenter != null) {
                    showEditStatusDialog(selectedCenter, statusMessage, refreshMapAndFilter);
                } else {
                    statusMessage.setText("Please select a center to edit!");
                    statusMessage.setStyle("-fx-text-fill: #f44336;");
                }
            });

            adminControls.getChildren().addAll(adminLabel, editStatusButton, statusMessage);
            layout.getChildren().add(adminControls);
        }
        search.textProperty().addListener((obs, o, n) -> applyFilter.run());
        statusFilter.setOnAction(e -> applyFilter.run());
        onlyOpen.setOnAction(e -> applyFilter.run());

        HBox legend = new HBox(15);
        legend.getChildren().addAll(new Label("Legend:"), colorKey("Open", Color.DARKGREEN),
                colorKey("Busy", Color.DARKORANGE), colorKey("Closed", Color.DARKRED));

        layout.getChildren().addAll(header, controls, table, new Separator(), legend, mapHeader, mapView);
        tab.setContent(layout);
        return tab;
    }

    private Label colorKey(String label, Color color) {
        Canvas swatch = new Canvas(12, 12);
        GraphicsContext g = swatch.getGraphicsContext2D();
        g.setFill(color);
        g.fillRect(0, 0, 12, 12);
        g.setStroke(Color.GRAY);
        g.strokeRect(0.5, 0.5, 11, 11);
        HBox box = new HBox(6);
        Label text = new Label(label);
        box.getChildren().addAll(swatch, text);
        return text;
    }

    private void drawCentersOnCanvas(Canvas canvas, List<Center> centers) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setStroke(Color.GRAY);
        g.strokeRect(0.5, 0.5, canvas.getWidth() - 1, canvas.getHeight() - 1);

        double minLat = centers.stream().mapToDouble(c -> c.lat).min().orElse(0);
        double maxLat = centers.stream().mapToDouble(c -> c.lat).max().orElse(1);
        double minLng = centers.stream().mapToDouble(c -> c.lng).min().orElse(0);
        double maxLng = centers.stream().mapToDouble(c -> c.lng).max().orElse(1);
        double pad = 30;
        double W = canvas.getWidth() - 2 * pad, H = canvas.getHeight() - 2 * pad;
        java.util.function.BiFunction<Double, Double, Point2D> map = (lat, lng) -> new Point2D(
                pad + (lng - minLng) / Math.max(1e-6, (maxLng - minLng)) * W,
                pad + (lat - minLat) / Math.max(1e-6, (maxLat - minLat)) * H);

        for (Center c : centers) {
            Point2D p = map.apply(c.lat, c.lng);
            if ("Open".equalsIgnoreCase(c.status))
                g.setFill(Color.DARKGREEN);
            else if ("Busy".equalsIgnoreCase(c.status))
                g.setFill(Color.DARKORANGE);
            else
                g.setFill(Color.DARKRED);
            g.fillOval(p.x - 4, p.y - 4, 8, 8);
            g.setFill(Color.BLACK);
            g.fillText(c.id + " (" + c.status + ")", p.x + 6, p.y);
        }
    }

    // ------------------- Printable Node for User Records -------------------
    private VBox buildUserRecordPrintNode(String username, List<Submission> submissions) {
        VBox root = new VBox(8);
        root.setPadding(new Insets(20));
        Label title = new Label("Recycling Records for: " + username);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        root.getChildren().add(title);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        grid.add(new Label("Date"), 0, 0);
        grid.add(new Label("Material"), 1, 0);
        grid.add(new Label("Weight (kg)"), 2, 0);

        int row = 1;
        for (Submission s : submissions) {
            grid.add(new Label(s.getDate()), 0, row);
            grid.add(new Label(s.getMaterial()), 1, row);
            grid.add(new Label(String.format(Locale.US, "%.2f", s.getWeight())), 2, row);
            row++;
        }
        root.getChildren().add(grid);
        return root;
    }

    // ------------------- Draw Route -------------------
    private void drawRoute(Canvas canvas, List<Center> centers) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setStroke(Color.GRAY);
        g.strokeRect(0.5, 0.5, canvas.getWidth() - 1, canvas.getHeight() - 1);

        routeService.build(store.centers);
        List<String> targets = centers.stream().map(c -> c.id).collect(Collectors.toList());
        List<String> order = routeService.shortestVisitOrder("DEPOT", targets);

        double minLat = store.centers.stream().mapToDouble(c -> c.lat).min().orElse(0);
        double maxLat = store.centers.stream().mapToDouble(c -> c.lat).max().orElse(1);
        double minLng = store.centers.stream().mapToDouble(c -> c.lng).min().orElse(0);
        double maxLng = store.centers.stream().mapToDouble(c -> c.lng).max().orElse(1);
        double pad = 30;
        double W = canvas.getWidth() - 2 * pad, H = canvas.getHeight() - 2 * pad;
        java.util.function.BiFunction<Double, Double, Point2D> map = (lat, lng) -> new Point2D(
                pad + (lng - minLng) / Math.max(1e-6, (maxLng - minLng)) * W,
                pad + (lat - minLat) / Math.max(1e-6, (maxLat - minLat)) * H);

        // Depot
        g.setFill(Color.DARKBLUE);
        Point2D depotPt = map.apply(5.0, 5.0);
        g.fillOval(depotPt.x - 5, depotPt.y - 5, 10, 10);
        g.fillText("DEPOT", depotPt.x + 6, depotPt.y - 6);

        // Centers
        g.setFill(Color.DARKGREEN);
        for (Center c : store.centers) {
            Point2D p = map.apply(c.lat, c.lng);
            g.fillOval(p.x - 4, p.y - 4, 8, 8);
            g.fillText(c.id, p.x + 6, p.y);
        }

        // Route
        g.setStroke(Color.DARKORANGE);
        g.setLineWidth(2.0);
        for (int i = 0; i < order.size() - 1; i++) {
            RouteService.Node a = routeService.getNodes().get(order.get(i));
            RouteService.Node b = routeService.getNodes().get(order.get(i + 1));
            Point2D pa = map.apply(a.lat, a.lng);
            Point2D pb = map.apply(b.lat, b.lng);
            g.strokeLine(pa.x, pa.y, pb.x, pb.y);
        }

        // Order list
        g.setFill(Color.BLACK);
        g.fillText("Visit Order:", pad, canvas.getHeight() - pad + 5);
        for (int i = 0; i < order.size(); i++) {
            g.fillText((i + 1) + ". " + order.get(i), pad + i * 80, canvas.getHeight() - pad + 20);
        }
    }

    // ------------------- Route Information Helper -------------------
    private void updateRouteInfo(Label infoLabel, List<Center> centers) {
        routeService.build(centers);
        List<String> targets = centers.stream().map(c -> c.id).collect(Collectors.toList());
        List<String> route = routeService.shortestVisitOrder("DEPOT", targets);

        // Calculate total distance
        double totalDistance = 0.0;
        for (int i = 0; i < route.size() - 1; i++) {
            RouteService.Node from = routeService.getNodes().get(route.get(i));
            RouteService.Node to = routeService.getNodes().get(route.get(i + 1));
            double dx = from.lat - to.lat;
            double dy = from.lng - to.lng;
            totalDistance += Math.sqrt(dx * dx + dy * dy);
        }

        String routeText = String.format(
                "Route: %s | Total Distance: %.2f meters | Stops: %d",
                String.join(" → ", route),
                totalDistance,
                route.size() - 1);

        infoLabel.setText(routeText);
        infoLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
    }

    // ------------------- Detailed Route Information for Admin -------------------
    private void updateRouteDetails(Label detailsLabel, List<Center> centers) {
        routeService.build(centers);
        List<String> targets = centers.stream().map(c -> c.id).collect(Collectors.toList());
        List<String> route = routeService.shortestVisitOrder("DEPOT", targets);

        StringBuilder details = new StringBuilder();
        details.append("Route Details:\n");

        double totalDistance = 0.0;
        for (int i = 0; i < route.size() - 1; i++) {
            String from = route.get(i);
            String to = route.get(i + 1);
            RouteService.Node fromNode = routeService.getNodes().get(from);
            RouteService.Node toNode = routeService.getNodes().get(to);

            double dx = fromNode.lat - toNode.lat;
            double dy = fromNode.lng - toNode.lng;
            double distance = Math.sqrt(dx * dx + dy * dy);
            totalDistance += distance;

            details.append(String.format("  %d. %s → %s (%.2f meters)\n",
                    i + 1, from, to, distance));
        }

        details.append(String.format("\nTotal Route Distance: %.2f meters\n", totalDistance));
        details.append(String.format("Number of Centers: %d\n", centers.size()));
        details.append(String.format("Algorithm: Dijkstra's Shortest Path"));

        detailsLabel.setText(details.toString());
        // Use theme styling; remove hardcoded light background
        detailsLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-padding: 10;");
    }

    // ------------------- Helper Struct -------------------
    static class Point2D {
        final double x, y;

        Point2D(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
