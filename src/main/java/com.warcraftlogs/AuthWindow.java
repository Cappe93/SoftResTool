package com.warcraftlogs;

import com.warcraftlogs.dto.AttendanceEntry;
import com.warcraftlogs.dto.AttendanceTableModel;
import com.warcraftlogs.dto.Player;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class AuthWindow {

    private static final Map<String, String> apiEndpoints = new LinkedHashMap<>() {{
        put("Fresh", "https://fresh.warcraftlogs.com/api/v2");
        put("Classic", "https://classic.warcraftlogs.com/api/v2");
        put("Season of Discovery (SoD)", "https://sod.warcraftlogs.com/api/v2");
        put("Retail", "https://www.warcraftlogs.com/api/v2");
    }};

    private static String accessToken = null;
    private static final Logger logger = Logger.getLogger(AuthWindow.class.getName());
    private static String selectedGuildName;
    private static String selectedServerName;
    private static Map<String, Integer> guildTagsMap;
    private static List<AttendanceEntry> attendance;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            if (isTokenValid()) {
                try {
                    JSONObject token = new JSONObject(new JSONTokener(new FileReader("token.json")));
                    String apiUrl = detectApiUrlFromToken(token); // 👈 Ricava l'endpoint corretto
                    accessToken = token.getString("access_token");
                    openMainWindow(apiUrl, accessToken);
                } catch (Exception e) {
                    e.printStackTrace();
                    createAndShowGUI();
                }
            } else {
                createAndShowGUI();
            }
        });
    }

    private static boolean isTokenValid() {
        File file = new File("token.json");
        if (!file.exists()) return false;

        try (FileReader reader = new FileReader(file)) {
            JSONObject obj = new JSONObject(new JSONTokener(reader));
            long createdAt = obj.getLong("created_at");
            long expiresIn = obj.getLong("expires_in");
            long now = System.currentTimeMillis();
            long expiresAt = createdAt + (expiresIn * 1000L);
            return now < expiresAt;
        } catch (Exception e) {
            return false;
        }
    }

    private static String detectApiUrlFromToken(JSONObject token) {
        return token.optString("api_url", apiEndpoints.get("Retail")); // default fallback
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Warcraft Logs API Auth");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 320);
        frame.setLayout(new BorderLayout());

        // Top-right button
        JButton softResToolButton = new JButton("Open SoftResTool");
        JPanel topRightPanel = new JPanel(new BorderLayout());
        topRightPanel.add(softResToolButton, BorderLayout.EAST);
        frame.add(topRightPanel, BorderLayout.NORTH);

        softResToolButton.addActionListener(evt -> SoftResToolWindow.show(frame));

        // Panel centrale con GridBagLayout
        JPanel contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Row 0: WoW Version
        gbc.gridx = 0;
        gbc.gridy = row;
        JLabel versionLabel = new JLabel("Select WoW Version:");
        contentPanel.add(versionLabel, gbc);

        gbc.gridx = 1;
        JComboBox<String> versionDropdown = new JComboBox<>(apiEndpoints.keySet().toArray(new String[0]));
        contentPanel.add(versionDropdown, gbc);

        // Row 1: Client ID
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        JLabel clientIdLabel = new JLabel("Client ID:");
        contentPanel.add(clientIdLabel, gbc);

        gbc.gridx = 1;
        JTextField clientIdField = new JTextField(25);
        contentPanel.add(clientIdField, gbc);

        // Row 2: Client Secret
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        JLabel clientSecretLabel = new JLabel("Client Secret:");
        contentPanel.add(clientSecretLabel, gbc);

        gbc.gridx = 1;
        JPasswordField clientSecretField = new JPasswordField(25);
        contentPanel.add(clientSecretField, gbc);

        // Row 3: Remember checkbox
        row++;
        gbc.gridx = 1;
        gbc.gridy = row;
        JCheckBox rememberCheckbox = new JCheckBox("Remember credentials");
        contentPanel.add(rememberCheckbox, gbc);

        // Row 4: Login button
        row++;
        gbc.gridx = 1;
        gbc.gridy = row;
        JButton loginButton = new JButton("Login");
        contentPanel.add(loginButton, gbc);

        // Row 5: Status label
        row++;
        gbc.gridx = 1;
        gbc.gridy = row;
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.DARK_GRAY);
        contentPanel.add(statusLabel, gbc);

        // Aggiunta del content panel al frame
        frame.add(contentPanel, BorderLayout.CENTER);

        // Load saved credentials if present
        File credsFile = new File("credentials.json");
        if (credsFile.exists()) {
            try (FileReader reader = new FileReader(credsFile)) {
                JSONObject obj = new JSONObject(new JSONTokener(reader));
                clientIdField.setText(obj.optString("client_id", ""));
                clientSecretField.setText(obj.optString("client_secret", ""));
                rememberCheckbox.setSelected(true);

                String savedVersion = obj.optString("wow_version", null);
                if (savedVersion != null) {
                    versionDropdown.setSelectedItem(savedVersion);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        // Action
        loginButton.addActionListener(e -> {
            String clientId = clientIdField.getText().trim();
            String clientSecret = new String(clientSecretField.getPassword()).trim();
            String selectedVersion = (String) versionDropdown.getSelectedItem();
            String apiUrl = apiEndpoints.get(selectedVersion);

            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please enter both Client ID and Client Secret.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            loginButton.setEnabled(false);
            statusLabel.setText("Requesting access token...");

            // Save or delete credentials
            if (rememberCheckbox.isSelected()) {
                try (FileWriter writer = new FileWriter("credentials.json")) {
                    JSONObject creds = new JSONObject();
                    creds.put("client_id", clientId);
                    creds.put("client_secret", clientSecret);
                    creds.put("wow_version", selectedVersion);  // <-- salva qui la versione selezionata
                    writer.write(creds.toString(2));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }


            new Thread(() -> {
                try {
                    JSONObject tokenObj = requestAccessToken(clientId, clientSecret);
                    accessToken = tokenObj.getString("access_token");

                    // Save token
                    saveTokenToFile(tokenObj,apiUrl);

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(frame, "Login successful! Opening the application now.", "Success", JOptionPane.INFORMATION_MESSAGE);
                        frame.dispose();
                        openMainWindow(apiUrl, accessToken);
                    });
                } catch (Exception ex) {
                    logger.info("Authentication failed: " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Authentication failed!"));
                } finally {
                    SwingUtilities.invokeLater(() -> loginButton.setEnabled(true));
                }
            }).start();
        });
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }


    private static JSONObject requestAccessToken(String clientId, String clientSecret) throws Exception {
        String credentials = clientId + ":" + clientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(credentials.getBytes());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.warcraftlogs.com/oauth/token"))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " - " + response.body());
        }

        return new JSONObject(response.body());
    }

    private static void saveTokenToFile(JSONObject tokenObj, String apiUrl) {
        try (FileWriter writer = new FileWriter("token.json")) {
            tokenObj.put("created_at", System.currentTimeMillis());
            tokenObj.put("api_url", apiUrl);  // <-- Aggiungi esplicitamente l'api_url scelto

            writer.write(tokenObj.toString(2));
        } catch (IOException e) {
            logger.warning("Failed to save token.json: " + e.getMessage());
        }
    }

    private static void openMainWindow(String apiUrl, String token) {
        JFrame mainFrame = new JFrame("WCL API - Main Window");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(600, 400);
        mainFrame.setLayout(new BorderLayout());

        JLabel countdownLabel = new JLabel();
        countdownLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        countdownLabel.setForeground(new Color(0, 128, 0));

        JLabel expiredLabel = new JLabel("Session expired!");
        expiredLabel.setForeground(Color.RED);
        expiredLabel.setVisible(false);

        JButton reauthButton = new JButton("Re-authenticate");
        reauthButton.addActionListener(e -> {
            new File("token.json").delete();
            mainFrame.dispose();
            createAndShowGUI();
        });

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(countdownLabel);
        statusPanel.add(expiredLabel);
        statusPanel.add(reauthButton);

        topPanel.add(statusPanel);

        JLabel label = new JLabel("Authenticated! Ready to use API at: " + apiUrl);

        JTextField guildInput = new JTextField();
        Dimension fixedSize = new Dimension(200, 25);
        guildInput.setMaximumSize(fixedSize);
        guildInput.setPreferredSize(fixedSize);
        guildInput.setMinimumSize(fixedSize);

        JComboBox<String> guildTagDropdown = new JComboBox<>();
        guildTagDropdown.setVisible(false);
        guildTagDropdown.setMaximumSize(fixedSize);
        guildTagDropdown.setPreferredSize(fixedSize);
        guildTagDropdown.setMinimumSize(fixedSize);

        JButton searchButton = new JButton("SEARCH GUILD");
        searchButton.setPreferredSize(new Dimension(120, 25));

        JPanel inputRow = new JPanel();
        inputRow.setLayout(new BoxLayout(inputRow, BoxLayout.X_AXIS));
        inputRow.add(guildInput);
        inputRow.add(Box.createRigidArea(new Dimension(10, 0)));
        inputRow.add(searchButton);

        JButton fetchAttendanceButton = new JButton("FETCH ATTENDANCE");
        fetchAttendanceButton.setPreferredSize(new Dimension(160, 25));
        fetchAttendanceButton.setVisible(false);

        JPanel dropdownRow = new JPanel();
        dropdownRow.setLayout(new BoxLayout(dropdownRow, BoxLayout.X_AXIS));
        dropdownRow.add(guildTagDropdown);
        dropdownRow.add(Box.createRigidArea(new Dimension(10, 0)));
        dropdownRow.add(fetchAttendanceButton);
        dropdownRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Tabella e CSV
        JTable table = new JTable();
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(580, 200));
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton exportCsvButton = new JButton("EXPORT CSV");
        exportCsvButton.setEnabled(false);

        JPanel tablePanel = new JPanel();
        tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.Y_AXIS));
        tablePanel.add(scrollPane);
        tablePanel.add(Box.createVerticalStrut(10));
        tablePanel.add(exportCsvButton);
        tablePanel.setVisible(false);

        // Pannello centrale
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        inputRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dropdownRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        exportCsvButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        centerPanel.add(label);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(inputRow);
        centerPanel.add(Box.createVerticalStrut(5));
        centerPanel.add(dropdownRow);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(tablePanel);

        mainFrame.add(topPanel, BorderLayout.NORTH);
        mainFrame.add(centerPanel, BorderLayout.CENTER);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);

        // SEARCH GUILD listener
        searchButton.addActionListener(e -> {
            String input = guildInput.getText().trim();
            String[] splitted = input.split("-");
            if (splitted.length != 2) {
                JOptionPane.showMessageDialog(mainFrame, "Please enter a valid guild-server name (e.g., GuildName-ServerName).", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }

            selectedGuildName = splitted[0];
            selectedServerName = splitted[1];
            guildTagsMap = new LinkedHashMap<>();

            searchGuild(apiUrl, token, input,
                    guildJson -> {
                        int guildId = guildJson.getJSONObject("data")
                                .getJSONObject("guildData")
                                .getJSONObject("guild")
                                .getInt("id");

                        getGuildReports(apiUrl, token, guildId,
                                reportsJson -> {
                                    guildTagsMap.put("All tags", 0);
                                    var reportsArray = reportsJson.getJSONObject("data").getJSONObject("reportData").getJSONObject("reports").getJSONArray("data");

                                    for (int i = 0; i < reportsArray.length(); i++) {
                                        JSONObject report = reportsArray.getJSONObject(i);
                                        if (report.has("guildTag") && !report.isNull("guildTag")) {
                                            JSONObject tag = report.getJSONObject("guildTag");
                                            int tagId = tag.getInt("id");
                                            String tagName = tag.getString("name");
                                            guildTagsMap.putIfAbsent(tagName, tagId);
                                        }
                                    }

                                    SwingUtilities.invokeLater(() -> {
                                        guildTagDropdown.removeAllItems();
                                        guildTagsMap.keySet().forEach(guildTagDropdown::addItem);
                                        guildTagDropdown.setVisible(true);
                                        fetchAttendanceButton.setVisible(true);
                                    });
                                },
                                err -> JOptionPane.showMessageDialog(mainFrame, err, "Errore Report", JOptionPane.ERROR_MESSAGE)
                        );
                    },
                    err -> JOptionPane.showMessageDialog(mainFrame, err, "Errore Guild", JOptionPane.ERROR_MESSAGE)
            );
        });

        // FETCH ATTENDANCE listener
        fetchAttendanceButton.addActionListener(e -> {
            Integer selectedTagId = guildTagsMap.get(guildTagDropdown.getSelectedItem());
            try {
                attendance = fetchAttendancePaginated(apiUrl, token, selectedGuildName, selectedServerName, "eu", String.valueOf(selectedTagId));
                table.setModel(new AttendanceTableModel(attendance));
                tablePanel.setVisible(true);
                exportCsvButton.setEnabled(true);
                centerPanel.revalidate();
                centerPanel.repaint();
            } catch (IOException | InterruptedException ex) {
                JOptionPane.showMessageDialog(mainFrame, ex, "Error attendance", JOptionPane.ERROR_MESSAGE);
            }
        });

        // EXPORT CSV listener
        exportCsvButton.addActionListener(ev -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File("attendance_export.csv"));
            int result = fileChooser.showSaveDialog(mainFrame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try (PrintWriter pw = new PrintWriter(file)) {

                    // 1. Crea lista colonne raid (nell'ordine dei report)
                    List<String> raidColumns = new ArrayList<>();
                    Map<String, String> codeToColumn = new LinkedHashMap<>();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/M");

                    for (AttendanceEntry entry : attendance) {
                        Instant instant = Instant.ofEpochMilli(entry.timestamp);
                        ZonedDateTime date = instant.atZone(ZoneId.systemDefault());
                        String text = entry.zoneName + " " + formatter.format(date);
                        raidColumns.add(text);
                        codeToColumn.put(entry.code, text);
                    }

                    // 2. Mappa per ogni player → presenza per raid
                    Map<String, Map<String, Integer>> playerRaidMap = new LinkedHashMap<>();
                    Map<String, Integer> attendanceCount = new HashMap<>();

                    for (AttendanceEntry entry : attendance) {
                        for (Player p : entry.players) {
                            playerRaidMap.putIfAbsent(p.name, new LinkedHashMap<>());
                            Map<String, Integer> raidPresences = playerRaidMap.get(p.name);
                            raidPresences.put(codeToColumn.get(entry.code), p.presence);

                            attendanceCount.put(p.name, attendanceCount.getOrDefault(p.name, 0) + (p.presence == 1 ? 1 : 0));
                        }
                    }

                    int totalRaids = raidColumns.size();

                    // 3. Scrivi intestazione
                    pw.print("Name,Att.%");
                    for (String raidCol : raidColumns) {
                        pw.print("," + raidCol);
                    }
                    pw.println();

                    // 4. Scrivi righe per player
                    for (String playerName : playerRaidMap.keySet()) {
                        int attended = attendanceCount.getOrDefault(playerName, 0);
                        double percentage = (totalRaids > 0) ? (attended * 100.0 / totalRaids) : 0;

                        pw.printf("\"%s\",\"%.1f\"", playerName, percentage);

                        Map<String, Integer> presenceMap = playerRaidMap.get(playerName);
                        for (String raid : raidColumns) {
                            Integer present = presenceMap.getOrDefault(raid, 0);
                            pw.print(",");
                            if (present == 1) pw.print("1");
                        }
                        pw.println();
                    }

                    JOptionPane.showMessageDialog(mainFrame, "CSV esportato in formato attendance!", "Success", JOptionPane.INFORMATION_MESSAGE);

                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(mainFrame, "Errore durante l'esportazione CSV: " + ex.getMessage(), "Errore", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Token monitor
        startSessionMonitor(countdownLabel, expiredLabel, reauthButton, mainFrame);
    }



    private static void startSessionMonitor(JLabel countdownLabel, JLabel expiredLabel, JButton reauthButton, JFrame mainFrame) {
        new Thread(() -> {
            try {
                File file = new File("token.json");
                if (!file.exists()) return;

                JSONObject tokenJson;
                try (FileReader reader = new FileReader(file)) {
                    tokenJson = new JSONObject(new JSONTokener(reader));
                }

                long createdAt = tokenJson.getLong("created_at");
                long expiresIn = tokenJson.getLong("expires_in");
                long expiresAt = createdAt + (expiresIn * 1000L);

                while (true) {
                    Thread.sleep(1000);
                    long now = System.currentTimeMillis();
                    long remaining = expiresAt - now;

                    SwingUtilities.invokeLater(() -> {
                        if (remaining <= 0) {
                            countdownLabel.setVisible(false);
                            expiredLabel.setVisible(true);

                            int choice = JOptionPane.showConfirmDialog(
                                    mainFrame,
                                    "Session has expired. Do you want to re-authenticate?",
                                    "Token Expired",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.WARNING_MESSAGE
                            );
                            if (choice == JOptionPane.YES_OPTION) {
                                new File("token.json").delete();
                                mainFrame.dispose();
                                createAndShowGUI();
                            }
                        } else {
                            long hours = remaining / (1000 * 60 * 60);
                            long minutes = (remaining / (1000 * 60)) % 60;
                            long seconds = (remaining / 1000) % 60;
                            countdownLabel.setText(String.format("Token expires in: %02d:%02d:%02d", hours, minutes, seconds));
                        }
                    });

                    if (remaining <= 0) break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void searchGuild(String apiUrl, String token, String guildServerInput, Consumer<JSONObject> onSuccess, Consumer<String> onError) {
        String[] parts = guildServerInput.split("-", 2);
        if (parts.length < 2) {
            onError.accept("Formato richiesto: GuildName-ServerSlug");
            return;
        }

        String guildName = parts[0];
        String serverSlug = parts[1].toLowerCase();
        String serverRegion = "eu"; // fisso per ora

        String graphqlQuery = String.format("""
        {
          guildData {
            guild(name: "%s", serverSlug: "%s", serverRegion: "%s") {
              id
              name
              server {
                name
                slug
                region {
                  name
                }
              }
            }
          }
        }
        """, guildName, serverSlug, serverRegion);

        JSONObject body = new JSONObject().put("query", graphqlQuery);

        new Thread(() -> {
            try {
                HttpResponse<String> resp = HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder()
                                        .uri(URI.create(apiUrl))
                                        .header("Authorization", "Bearer " + token)
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString()
                        );

                if (resp.statusCode() != 200) {
                    onError.accept("Errore HTTP " + resp.statusCode() + ": " + resp.body());
                    return;
                }

                JSONObject json = new JSONObject(resp.body());
                if (json.has("errors")) {
                    String msg = json.getJSONArray("errors").getJSONObject(0).optString("message", "Errore GraphQL");
                    onError.accept("Errore API: " + msg);
                    return;
                }

                onSuccess.accept(json); // ritorna il JSON completo alla funzione chiamante

            } catch (Exception e) {
                onError.accept("Request failed: " + e.getMessage());
            }
        }).start();
    }

    private static void getGuildReports(String apiUrl, String token, int guildId, Consumer<JSONObject> onSuccess, Consumer<String> onError) {
        String graphqlQuery = String.format("""
        {
          reportData {
            reports(guildID: %d) {
              data {
                code
                title
                startTime
                endTime
                zone {
                  name
                }
                guildTag {
                  id
                  name
                }
              }
            }
          }
        }
        """, guildId);

        JSONObject body = new JSONObject().put("query", graphqlQuery);

        new Thread(() -> {
            try {
                HttpResponse<String> resp = HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder()
                                        .uri(URI.create(apiUrl))
                                        .header("Authorization", "Bearer " + token)
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString()
                        );

                if (resp.statusCode() != 200) {
                    onError.accept("Errore HTTP " + resp.statusCode() + ": " + resp.body());
                    return;
                }

                JSONObject json = new JSONObject(resp.body());
                if (json.has("errors")) {
                    String msg = json.getJSONArray("errors").getJSONObject(0).optString("message", "Errore GraphQL");
                    onError.accept("Errore API: " + msg);
                    return;
                }

                onSuccess.accept(json); // ritorna il JSON con i dati dei report

            } catch (Exception e) {
                onError.accept("Request failed: " + e.getMessage());
            }
        }).start();
    }

    private static JComboBox<String> createGuildTagComboBox(JSONObject reportsJson) {
        Set<String> guildTags = new HashSet<>();

        try {
            var reportsArray = reportsJson
                    .getJSONObject("data")
                    .getJSONObject("reportData")
                    .getJSONObject("reports")
                    .getJSONArray("data");

            for (int i = 0; i < reportsArray.length(); i++) {
                var report = reportsArray.getJSONObject(i);
                if (!report.isNull("guildTag")) {
                    var guildTagObj = report.getJSONObject("guildTag");
                    String tagName = guildTagObj.optString("name", null);
                    if (tagName != null && !tagName.isBlank()) {
                        guildTags.add(tagName);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        guildTags.forEach(model::addElement);

        return new JComboBox<>(model);
    }

    public static List<AttendanceEntry> fetchAttendancePaginated(
            String apiUrl, String token,
            String guildName, String serverSlug, String serverRegion,
            String guildTagId
    ) throws IOException, InterruptedException {

        List<AttendanceEntry> attendanceEntries = new ArrayList<>();
        int page = 1;
        boolean hasMorePages;

        do {
            String query = """
            {
              guildData {
                guild(name: "%s", serverSlug: "%s", serverRegion: "%s") {
                  attendance(guildTagID: %s, page: %d) {
                    data {
                      code
                      zone { id name }
                      players {
                        name
                        type
                        presence
                      }
                      startTime
                    }
                    has_more_pages
                  }
                }
              }
            }
        """.formatted(guildName, serverSlug, serverRegion, guildTagId, page);

            String payload = new JSONObject().put("query", query).toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Errore HTTP " + response.statusCode() + ": " + response.body());
            }

            JSONObject json = new JSONObject(response.body());
            JSONObject attendance = json.getJSONObject("data")
                    .getJSONObject("guildData")
                    .getJSONObject("guild")
                    .getJSONObject("attendance");

            JSONArray data = attendance.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject report = data.getJSONObject(i);
                AttendanceEntry entry = new AttendanceEntry();
                entry.code = report.getString("code");
                entry.timestamp = report.optLong("startTime", System.currentTimeMillis()); // fallback se mancante

                JSONObject zone = report.getJSONObject("zone");
                entry.zoneId = zone.getInt("id");
                entry.zoneName = zone.getString("name");

                JSONArray players = report.getJSONArray("players");
                for (int j = 0; j < players.length(); j++) {
                    JSONObject p = players.getJSONObject(j);
                    Player player = new Player();
                    player.name = p.getString("name");
                    player.type = p.getString("type");
                    player.presence = p.getInt("presence");
                    entry.players.add(player);
                }

                attendanceEntries.add(entry);
            }

            hasMorePages = attendance.getBoolean("has_more_pages");
            page++;

        } while (hasMorePages);

        return attendanceEntries;
    }
}
