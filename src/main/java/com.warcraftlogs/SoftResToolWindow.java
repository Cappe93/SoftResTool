package com.warcraftlogs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.hc.core5.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.logging.*;

import static com.sun.activation.registries.LogSupport.isLoggable;

public class SoftResToolWindow {

    private static final String LOGIN_URL = "https://beta.softres.it/auth/redirect?provider=discord";
    private static final String COOKIE_FILE = "cookies.json";
    private static final Logger logger = Logger.getLogger(SoftResToolWindow.class.getName());

    public static void show(JFrame parentFrame) {
        JTextArea logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        Handler textAreaHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (isLoggable(record)) {
                    SwingUtilities.invokeLater(() -> {
                        logArea.append(getFormatter().format(record));
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    });
                }
            }

            @Override public void flush() {}
            @Override public void close() throws SecurityException {}

            {
                setFormatter(new SimpleFormatter());
            }
        };
        logger.addHandler(textAreaHandler);
        JFrame softResFrame = new JFrame("SoftRes Tool");
        softResFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        softResFrame.setSize(600, 350);
        softResFrame.setLayout(new BorderLayout());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        buttonPanel.add(new JLabel("Tool Options:"));
        buttonPanel.add(Box.createVerticalStrut(10));

        // --- Pulsante Authenticate ---
        JButton authButton = new JButton("Authenticate");
        authButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        authButton.addActionListener(e -> {
            new Thread(() -> {
                try {
                    runAuthenticationFlow();
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(softResFrame, "Authentication completed (cookies saved).", "Success", JOptionPane.INFORMATION_MESSAGE)
                    );
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(softResFrame, "Error: " + ex.getMessage(), "Authentication Failed", JOptionPane.ERROR_MESSAGE)
                    );
                    ex.printStackTrace();
                }
            }).start();
        });
        buttonPanel.add(authButton);
        buttonPanel.add(Box.createVerticalStrut(15));

        // --- RIGA: Select CSV + TextField ---
        JPanel fileRowPanel = new JPanel();
        fileRowPanel.setLayout(new BoxLayout(fileRowPanel, BoxLayout.X_AXIS));
        fileRowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField csvPathField = new JTextField();
        csvPathField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

        JButton selectCsvButton = new JButton("Select CSV File");
        selectCsvButton.setMaximumSize(new Dimension(150, 25));
        selectCsvButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            FileNameExtensionFilter csvFilter = new FileNameExtensionFilter("CSV Files (*.csv)", "csv");
            fileChooser.setFileFilter(csvFilter);
            fileChooser.setAcceptAllFileFilterUsed(false);

            int result = fileChooser.showOpenDialog(softResFrame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                if (!selectedFile.getName().toLowerCase().endsWith(".csv")) {
                    JOptionPane.showMessageDialog(softResFrame, "Please select a .csv file", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                csvPathField.setText(selectedFile.getAbsolutePath());
            }
        });

        fileRowPanel.add(selectCsvButton);
        fileRowPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        fileRowPanel.add(csvPathField);

        buttonPanel.add(fileRowPanel);
        buttonPanel.add(Box.createVerticalStrut(15));

        // --- RIGA: Upload API URL + Button ---
        JPanel uploadRow = new JPanel();
        uploadRow.setLayout(new BoxLayout(uploadRow, BoxLayout.X_AXIS));
        uploadRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField apiUrlField = new JTextField("https://beta.softres.it/reserve-bonus/upload"); // valore di default modificabile
        apiUrlField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

        JButton uploadButton = new JButton("Upload Bonus Reserves");
        uploadButton.setMaximumSize(new Dimension(200, 25));
        uploadButton.addActionListener(e -> {
            String csvPath = csvPathField.getText().trim();
            String apiUrl = apiUrlField.getText().trim();

            if (csvPath.isEmpty() || apiUrl.isEmpty()) {
                JOptionPane.showMessageDialog(softResFrame, "Please select a CSV file and enter a valid API URL.", "Missing Data", JOptionPane.WARNING_MESSAGE);
                return;
            }

            new Thread(() -> process(csvPath, apiUrl)).start();
        });

        uploadRow.add(apiUrlField);
        uploadRow.add(Box.createRigidArea(new Dimension(10, 0)));
        uploadRow.add(uploadButton);

        buttonPanel.add(uploadRow);
        buttonPanel.add(Box.createVerticalStrut(15));


        // --- Bottom Panel ---
        JButton closeButton = new JButton("Close SoftResTool");
        closeButton.addActionListener(e -> softResFrame.dispose());

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(closeButton);

        softResFrame.add(buttonPanel, BorderLayout.NORTH);
        softResFrame.add(logScrollPane, BorderLayout.CENTER);
        softResFrame.add(bottomPanel, BorderLayout.SOUTH);
        softResFrame.setVisible(true);
    }


    private static void runAuthenticationFlow() throws Exception {
        WebDriver driver = getAvailableWebDriver();

        if (driver == null) {
            throw new IllegalStateException("Nessun browser compatibile trovato.");
        }

        try {
            driver.get(LOGIN_URL);
            System.out.println("Waiting for login...");

            boolean found = false;
            long timeoutMs = 120_000;
            long startTime = System.currentTimeMillis();

            while (!found && (System.currentTimeMillis() - startTime < timeoutMs)) {
                Set<Cookie> cookies = driver.manage().getCookies();

                for (Cookie cookie : cookies) {
                    if ("softres_session".equals(cookie.getName())) {
                        saveCookiesToJson(cookies);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    Thread.sleep(1000);
                }
            }

            if (!found) {
                throw new RuntimeException("Timeout: 'softres_session' cookie non trovato.");
            }

        } finally {
            driver.quit();
            System.out.println("Browser closed.");
        }
    }

    private static void saveCookiesToJson(Set<Cookie> cookies) throws Exception {
        try (FileWriter writer = new FileWriter(COOKIE_FILE)) {
            writer.write("[\n");
            int i = 0;
            for (Cookie c : cookies) {
                writer.write(String.format(
                        "  {\"name\": \"%s\", \"value\": \"%s\", \"domain\": \"%s\", \"path\": \"%s\", \"secure\": %s}%s\n",
                        c.getName(), c.getValue(), c.getDomain(), c.getPath(), c.isSecure(),
                        (i++ < cookies.size() - 1) ? "," : ""));
            }
            writer.write("]");
        }
    }

    private static WebDriver getAvailableWebDriver() {
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--start-maximized");
            options.addArguments("--disable-infobars");
            options.addArguments("--disable-extensions");
            options.addArguments("--no-default-browser-check");
            options.addArguments("--disable-popup-blocking");
            System.out.println("Browser : Chrome");
            return new ChromeDriver(options);
        } catch (Exception ignored) {}

        try {
            WebDriverManager.firefoxdriver().setup();
            FirefoxOptions options = new FirefoxOptions();
            options.addPreference("dom.disable_open_during_load", false);
            options.addPreference("browser.startup.page", 1);
            options.addPreference("browser.startup.homepage", "about:blank");
            options.addPreference("browser.tabs.warnOnClose", false);
            options.addPreference("browser.shell.checkDefaultBrowser", false);
            System.out.println("Browser : Firefox");
            return new FirefoxDriver(options);
        } catch (Exception ignored) {}

        try {
            WebDriverManager.edgedriver().setup();
            EdgeOptions options = new EdgeOptions();
            options.addArguments("--start-maximized");
            options.addArguments("--disable-infobars");
            options.addArguments("--disable-extensions");
            options.addArguments("--no-default-browser-check");
            options.addArguments("--disable-popup-blocking");
            System.out.println("Browser : Edge");
            return new EdgeDriver(options);
        } catch (Exception ignored) {}

        try {
            System.out.println("Browser : Safari");
            return new SafariDriver();
        } catch (Exception ignored) {}

        return null;
    }

    private static void process(String csvPath, String apiUrl) {
        logger.info("Starting CSV processing: " + csvPath);

        JSONArray bonuses = new JSONArray();

        try (CSVReader reader = new CSVReader(new FileReader(csvPath))) {
            String[] headers = reader.readNext();
            if (headers == null) {
                logger.severe("CSV file is empty or missing headers.");
                return;
            }

            String[] row;
            int lineNumber = 1;
            while ((row = reader.readNext()) != null) {
                lineNumber++;
                if (row.length < 3) {
                    logger.warning("Line " + lineNumber + " skipped: less than 3 columns.");
                    continue;
                }

                String playerName = row[0].trim();
                if (playerName.isEmpty()) {
                    logger.warning("Line " + lineNumber + " skipped: missing player name.");
                    continue;
                }

                int presences = 0;
                int absences = 0;

                for (int i = 2; i < row.length; i++) {
                    if ("1".equals(row[i].trim())) {
                        presences++;
                    } else {
                        absences++;
                    }
                }

                int bonus = Math.max((presences - absences) / 2, 0);

                JSONObject obj = new JSONObject();
                obj.put("target", playerName);
                obj.put("bonus", bonus);
                bonuses.put(obj);
            }

            logger.info("Total players processed: " + bonuses.length());
            JSONObject finalPayload = new JSONObject();
            finalPayload.put("reserves_bonus", bonuses);

            sendRequest(finalPayload, apiUrl);

        } catch (IOException | CsvValidationException e) {
            logger.log(Level.SEVERE, "Error reading CSV file: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "HTTP request was interrupted: " + e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    private static void sendRequest(JSONObject json, String apiUrl) throws IOException, InterruptedException {

        JSONObject cookieData = readCookies();

        StringBuilder cookieHeaderBuilder = new StringBuilder();
        String xsrfToken = null;

        for (Object item : cookieData.getJSONArray("cookies")) {
            JSONObject cookie = (JSONObject) item;
            String name = cookie.getString("name");
            String value = cookie.getString("value");

            cookieHeaderBuilder.append(name).append("=").append(value).append("; ");

            if ("XSRF-TOKEN".equals(name)) {
                xsrfToken = URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }

        String cookieHeader = cookieHeaderBuilder.toString().trim();
        if (cookieHeader.endsWith(";")) {
            cookieHeader = cookieHeader.substring(0, cookieHeader.length() - 1);
        }

        if (xsrfToken == null) {
            logger.severe("XSRF-TOKEN cookie not found in the file.");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .PUT(HttpRequest.BodyPublishers.ofString(json.toString()))
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .header("cookie", cookieHeader)
                .header("x-xsrf-token", xsrfToken)
                .header("x-inertia", "true")
                .header("x-inertia-version", "688716a8bf15b355c68224c2ec66735c")
                .header("x-requested-with", "XMLHttpRequest")
                .header("referer", apiUrl)
                .header("origin", "https://beta.softres.it")
                .header("user-agent", "Mozilla/5.0")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        logger.info("HTTP response code: " + response.statusCode());
        if (response.statusCode() == HttpStatus.SC_SEE_OTHER) {
            logger.info("Soft reserves uploaded successfully!");
            logger.info("Printing softreserves: \n" + buildReservesTable(json.toString()));
            ;
        }
    }

    protected static JSONObject readCookies() throws IOException {
        try (FileReader reader = new FileReader(COOKIE_FILE)) {
            JSONArray array = new JSONArray(new JSONTokener(reader));
            JSONObject result = new JSONObject();
            result.put("cookies", array);
            return result;
        }
    }

    private static String buildReservesTable(String jsonString) {
        StringBuilder sb = new StringBuilder();

        // Parsing JSON
        JsonObject obj = JsonParser.parseString(jsonString).getAsJsonObject();
        JsonArray reserves = obj.getAsJsonArray("reserves_bonus");

        // Calcola larghezza massima del nome
        int maxNameLength = "Character".length();
        for (JsonElement el : reserves) {
            JsonObject o = el.getAsJsonObject();
            String name = o.get("target").getAsString();
            maxNameLength = Math.max(maxNameLength, name.length());
        }

        // Colonna bonus (in questo caso "BonusSR") è fissa a 8 caratteri
        String bonusHeader = "BonusSR";
        int bonusWidth = bonusHeader.length();

        // Costruisci intestazione e separatori
        String nameCol = padRight("Character", maxNameLength);
        String bonusCol = padRight(bonusHeader, bonusWidth);

        String separator = "+" + repeat("-", maxNameLength + 2) + "+" + repeat("-", bonusWidth + 2) + "+";

        sb.append(separator).append("\n");
        sb.append(String.format("| %-"+maxNameLength+"s | %-"+bonusWidth+"s |\n", "Character", bonusHeader));
        sb.append(separator).append("\n");

        // Riga per riga
        for (JsonElement el : reserves) {
            JsonObject o = el.getAsJsonObject();
            String name = o.get("target").getAsString();
            int bonus = o.get("bonus").getAsInt();

            sb.append(String.format("| %-"+maxNameLength+"s | %-"+bonusWidth+"d |\n", name, bonus));
        }

        sb.append(separator);
        return sb.toString();
    }

    // Utility methods
    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private static String repeat(String str, int times) {
        return str.repeat(times);
    }
}
