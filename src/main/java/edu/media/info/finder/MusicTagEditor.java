package edu.media.info.finder;


import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.FieldDataInvalidException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MusicTagEditor extends JFrame {

    // ============================================================
    // Fonts
    // ============================================================

    private static Font FONT_LATIN;
    private static Font FONT_KHMER;
    private static Font FONT_KOREAN;
    private static Font FONT_CHINESE;
    private static Font FONT_JAPANESE;

    // ============================================================
    // UI
    // ============================================================

    private JTextField folderField;

    private JTable fileTable;
    private DefaultTableModel fileTableModel;

    private JPanel resultsPanel;
    private JScrollPane resultsScroll;

    private JTextPane selectedFileInfo;

    private JButton scanButton;
    private JButton refreshButton;
    private JButton searchButton;

    // ============================================================
    // Data
    // ============================================================

    private final List<File> mediaFiles = new ArrayList<>();

    private File selectedFile;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    // ============================================================
    // Constructor
    // ============================================================

    public MusicTagEditor() {

        setTitle("Music Tag Editor");
        setSize(1200, 800);
        setLocationRelativeTo(null);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        buildUI();
    }

    // ============================================================
    // Main
    // ============================================================

    public static void main(String[] args) {

        setupFonts();

        SwingUtilities.invokeLater(() -> {

            setGlobalSwingFont();

            MusicTagEditor editor = new MusicTagEditor();
            editor.setVisible(true);
        });
    }

    // ============================================================
    // Font setup
    // ============================================================

    private static void setupFonts() {

        FONT_LATIN = findFont(
                14,
                "Segoe UI",
                "Arial",
                "Dialog"
        );

        FONT_KHMER = findFont(
                14,
                "Noto Sans Khmer",
                "Khmer OS"
        );

        FONT_KOREAN = findFont(
                14,
                "Noto Sans KR"
        );

        FONT_CHINESE = findFont(
                14,
                "Noto Sans SC"
        );

        FONT_JAPANESE = findFont(
                14,
                "Noto Sans JP"
        );
    }

    private static Font findFont(int size, String... names) {

        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();

        String[] installed =
                ge.getAvailableFontFamilyNames();

        for (String wanted : names) {

            for (String installedName : installed) {

                if (installedName.equalsIgnoreCase(wanted)) {

                    return new Font(
                            installedName,
                            Font.PLAIN,
                            size
                    );
                }
            }
        }

        return new Font(
                "Dialog",
                Font.PLAIN,
                size
        );
    }

    private static void setGlobalSwingFont() {

        Font font = FONT_LATIN;

        UIManager.put("Label.font", font);
        UIManager.put("Button.font", font);
        UIManager.put("TextField.font", font);
        UIManager.put("TextArea.font", font);
        UIManager.put("TextPane.font", font);
        UIManager.put("Table.font", font);
        UIManager.put("TableHeader.font", font);
        UIManager.put("List.font", font);
        UIManager.put("ComboBox.font", font);
        UIManager.put("CheckBox.font", font);
        UIManager.put("RadioButton.font", font);
        UIManager.put("TabbedPane.font", font);
        UIManager.put("OptionPane.messageFont", font);
        UIManager.put("OptionPane.buttonFont", font);
    }

    // ============================================================
    // Detect font for Unicode character
    // ============================================================

    private static Font getFontForCodePoint(int codePoint) {

        // Khmer
        if (isKhmer(codePoint)) {
            return FONT_KHMER;
        }

        // Korean Hangul
        if (isKorean(codePoint)) {
            return FONT_KOREAN;
        }

        // Chinese
        if (isChinese(codePoint)) {
            return FONT_CHINESE;
        }

        // Japanese
        if (isJapanese(codePoint)) {
            return FONT_JAPANESE;
        }

        // Everything else
        return FONT_LATIN;
    }

    private static boolean isKhmer(int cp) {

        return
                (cp >= 0x1780 && cp <= 0x17FF) ||
                (cp >= 0x19E0 && cp <= 0x19FF);
    }

    private static boolean isKorean(int cp) {

        return
                (cp >= 0x1100 && cp <= 0x11FF) ||
                (cp >= 0x3130 && cp <= 0x318F) ||
                (cp >= 0xAC00 && cp <= 0xD7AF) ||
                (cp >= 0xA960 && cp <= 0xA97F) ||
                (cp >= 0xD7B0 && cp <= 0xD7FF);
    }

    private static boolean isChinese(int cp) {

        return
                (cp >= 0x3400 && cp <= 0x4DBF) ||
                (cp >= 0x4E00 && cp <= 0x9FFF) ||
                (cp >= 0xF900 && cp <= 0xFAFF) ||
                (cp >= 0x20000 && cp <= 0x2FA1F);
    }

    private static boolean isJapanese(int cp) {

        return
                (cp >= 0x3040 && cp <= 0x309F) || // Hiragana
                (cp >= 0x30A0 && cp <= 0x30FF) || // Katakana
                (cp >= 0x31F0 && cp <= 0x31FF) || // Katakana extensions
                (cp >= 0xFF66 && cp <= 0xFF9F);   // Half-width Katakana
    }

    // ============================================================
    // Create mixed-language JTextPane
    // ============================================================

    private static JTextPane createMultiLanguageTextPane(
            String text
    ) {

        JTextPane pane = new JTextPane();

        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);

        pane.setFont(FONT_LATIN);

        setMultiLanguageText(pane, text);

        return pane;
    }

    // ============================================================
    // Set mixed-language text
    // ============================================================

    private static void setMultiLanguageText(
            JTextPane pane,
            String text
    ) {

        if (text == null) {
            text = "";
        }

        StyledDocument document =
                pane.getStyledDocument();

        try {

            document.remove(
                    0,
                    document.getLength()
            );

            int index = 0;

            while (index < text.length()) {

                int codePoint =
                        text.codePointAt(index);

                int charCount =
                        Character.charCount(codePoint);

                String character =
                        new String(
                                Character.toChars(codePoint)
                        );

                Font font =
                        getFontForCodePoint(codePoint);

                SimpleAttributeSet attributes =
                        new SimpleAttributeSet();

                StyleConstants.setFontFamily(
                        attributes,
                        font.getFamily()
                );

                StyleConstants.setFontSize(
                        attributes,
                        font.getSize()
                );

                StyleConstants.setForeground(
                        attributes,
                        UIManager.getColor(
                                "Label.foreground"
                        )
                );

                document.insertString(
                        document.getLength(),
                        character,
                        attributes
                );

                index += charCount;
            }

        } catch (BadLocationException e) {

            e.printStackTrace();
        }
    }

    // ============================================================
    // UI
    // ============================================================

    private void buildUI() {

        JPanel root = new JPanel(new BorderLayout(10, 10));

        root.setBorder(
                new EmptyBorder(10, 10, 10, 10)
        );

        setContentPane(root);

        // --------------------------------------------------------
        // Top
        // --------------------------------------------------------

        JPanel topPanel =
                new JPanel(new BorderLayout(5, 5));

        JLabel folderLabel =
                new JLabel("Folder:");

        topPanel.add(
                folderLabel,
                BorderLayout.WEST
        );

        folderField =
                new JTextField();

        topPanel.add(
                folderField,
                BorderLayout.CENTER
        );

        JPanel buttonPanel =
                new JPanel(new FlowLayout(
                        FlowLayout.RIGHT,
                        5,
                        0
                ));

        JButton browseButton =
                new JButton("Browse");

        scanButton =
                new JButton("Scan");

        refreshButton =
                new JButton("Refresh Tags");

        searchButton =
                new JButton("Search iTunes");

        buttonPanel.add(browseButton);
        buttonPanel.add(scanButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(searchButton);

        topPanel.add(
                buttonPanel,
                BorderLayout.EAST
        );

        root.add(
                topPanel,
                BorderLayout.NORTH
        );

        // --------------------------------------------------------
        // File table
        // --------------------------------------------------------

        fileTableModel =
                new DefaultTableModel(
                        new Object[]{
                                "File",
                                "Track",
                                "Artist"
                        },
                        0
                ) {

                    @Override
                    public boolean isCellEditable(
                            int row,
                            int column
                    ) {
                        return false;
                    }

                    @Override
                    public Class<?> getColumnClass(
                            int column
                    ) {

                        if (column == 0) {
                            return File.class;
                        }

                        return String.class;
                    }
                };

        fileTable =
                new JTable(fileTableModel);

        fileTable.setRowHeight(32);
        fileTable.setFillsViewportHeight(true);

        fileTable.getColumnModel()
                .getColumn(0)
                .setPreferredWidth(550);

        fileTable.getColumnModel()
                .getColumn(1)
                .setPreferredWidth(250);

        fileTable.getColumnModel()
                .getColumn(2)
                .setPreferredWidth(250);

        fileTable.setDefaultRenderer(
                File.class,
                new MultiLanguageTableRenderer()
        );

        fileTable.setDefaultRenderer(
                String.class,
                new MultiLanguageTableRenderer()
        );

        JScrollPane fileScroll =
                new JScrollPane(fileTable);

        // --------------------------------------------------------
        // Selected file information
        // --------------------------------------------------------

        selectedFileInfo =
                new JTextPane();

        selectedFileInfo.setEditable(false);
        selectedFileInfo.setBackground(
                UIManager.getColor("Panel.background")
        );

        selectedFileInfo.setBorder(
                BorderFactory.createTitledBorder(
                        "Selected File"
                )
        );

        selectedFileInfo.setPreferredSize(
                new Dimension(300, 100)
        );

        // --------------------------------------------------------
        // Results
        // --------------------------------------------------------

        resultsPanel =
                new JPanel();

        resultsPanel.setLayout(
                new BoxLayout(
                        resultsPanel,
                        BoxLayout.Y_AXIS
                )
        );

        resultsScroll =
                new JScrollPane(resultsPanel);

        resultsScroll.setBorder(
                BorderFactory.createTitledBorder(
                        "iTunes Results"
                )
        );

        // --------------------------------------------------------
        // Center
        // --------------------------------------------------------

        JSplitPane horizontalSplit =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        fileScroll,
                        resultsScroll
                );

        horizontalSplit.setResizeWeight(0.50);

        JSplitPane verticalSplit =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        horizontalSplit,
                        selectedFileInfo
                );

        verticalSplit.setResizeWeight(0.85);

        root.add(
                verticalSplit,
                BorderLayout.CENTER
        );

        // --------------------------------------------------------
        // Events
        // --------------------------------------------------------

        browseButton.addActionListener(
                e -> browseFolder()
        );

        scanButton.addActionListener(
                e -> scanFolder()
        );

        refreshButton.addActionListener(
                e -> refreshTags()
        );

        searchButton.addActionListener(
                e -> {

                    if (selectedFile != null) {
                        searchITunesForFile(selectedFile);
                    }
                }
        );

        fileTable.getSelectionModel()
                .addListSelectionListener(e -> {

                    if (!e.getValueIsAdjusting()) {

                        int row =
                                fileTable.getSelectedRow();

                        if (row >= 0) {

                            Object value =
                                    fileTableModel.getValueAt(
                                            row,
                                            0
                                    );

                            if (value instanceof File) {

                                selectedFile =
                                        (File) value;

                                showSelectedFile(
                                        selectedFile
                                );

                                searchITunesForFile(
                                        selectedFile
                                );
                            }
                        }
                    }
                });
    }

    // ============================================================
    // Browse
    // ============================================================

    private void browseFolder() {

        JFileChooser chooser =
                new JFileChooser();

        chooser.setFileSelectionMode(
                JFileChooser.DIRECTORIES_ONLY
        );

        if (chooser.showOpenDialog(this)
                == JFileChooser.APPROVE_OPTION) {

            folderField.setText(
                    chooser.getSelectedFile()
                            .getAbsolutePath()
            );
        }
    }

    // ============================================================
    // Scan
    // ============================================================

    private void scanFolder() {

        String path =
                folderField.getText().trim();

        if (path.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Please select a folder."
            );

            return;
        }

        File folder =
                new File(path);

        if (!folder.isDirectory()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Folder does not exist."
            );

            return;
        }

        mediaFiles.clear();
        fileTableModel.setRowCount(0);

        scanDirectory(folder);

        for (File file : mediaFiles) {

            TagInfo tag =
                    readTags(file);

            fileTableModel.addRow(
                    new Object[]{
                            file,
                            tag.title,
                            tag.artist
                    }
            );
        }

        if (!mediaFiles.isEmpty()) {

            fileTable.setRowSelectionInterval(
                    0,
                    0
            );
        }
    }

    private void scanDirectory(File directory) {

        File[] files =
                directory.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {

            if (file.isDirectory()) {

                scanDirectory(file);

            } else if (isMediaFile(file)) {

                mediaFiles.add(file);
            }
        }
    }

    private boolean isMediaFile(File file) {

        String name =
                file.getName()
                        .toLowerCase(Locale.ROOT);

        return name.endsWith(".mp3")
                || name.endsWith(".flac")
                || name.endsWith(".m4a");
    }

    // ============================================================
    // Refresh tags
    // ============================================================

    private void refreshTags() {

        for (int row = 0;
             row < fileTableModel.getRowCount();
             row++) {

            Object value =
                    fileTableModel.getValueAt(
                            row,
                            0
                    );

            if (!(value instanceof File)) {
                continue;
            }

            File file =
                    (File) value;

            TagInfo tag =
                    readTags(file);

            fileTableModel.setValueAt(
                    tag.title,
                    row,
                    1
            );

            fileTableModel.setValueAt(
                    tag.artist,
                    row,
                    2
            );
        }

        if (selectedFile != null) {

            showSelectedFile(
                    selectedFile
            );
        }

        fileTable.repaint();
    }

    // ============================================================
    // Read tags
    // ============================================================

    private TagInfo readTags(File file) {

        TagInfo result =
                new TagInfo();

        try {

            AudioFile audioFile =
                    AudioFileIO.read(file);

            Tag tag =
                    audioFile.getTag();

            if (tag != null) {

                result.title =
                        safe(tag.getFirst(
                                FieldKey.TITLE
                        ));

                result.artist =
                        safe(tag.getFirst(
                                FieldKey.ARTIST
                        ));

                result.album =
                        safe(tag.getFirst(
                                FieldKey.ALBUM
                        ));

                result.albumArtist =
                        safe(tag.getFirst(
                                FieldKey.ALBUM_ARTIST
                        ));

                result.genre =
                        safe(tag.getFirst(
                                FieldKey.GENRE
                        ));

                result.year =
                        safe(tag.getFirst(
                                FieldKey.YEAR
                        ));

                result.track =
                        safe(tag.getFirst(
                                FieldKey.TRACK
                        ));

                result.disc =
                        safe(tag.getFirst(
                                FieldKey.DISC_NO
                        ));
            }

        } catch (Exception e) {

            System.err.println(
                    "Cannot read tags: "
                            + file.getAbsolutePath()
            );

            System.err.println(
                    e.getMessage()
            );
        }

        return result;
    }

    private String safe(String value) {

        if (value == null) {
            return "";
        }

        return value;
    }

    // ============================================================
    // Selected file
    // ============================================================

    private void showSelectedFile(File file) {

        TagInfo tag =
                readTags(file);

        String text =
                "File: " + file.getName() + "\n"
                + "Title: " + tag.title + "\n"
                + "Artist: " + tag.artist + "\n"
                + "Album: " + tag.album + "\n"
                + "Album Artist: " + tag.albumArtist + "\n"
                + "Genre: " + tag.genre + "\n"
                + "Year: " + tag.year + "\n"
                + "Track: " + tag.track + "\n"
                + "Disc: " + tag.disc;

        setMultiLanguageText(
                selectedFileInfo,
                text
        );
    }

    // ============================================================
    // iTunes search
    // ============================================================

    private void searchITunesForFile(File file) {

        String searchText =
                getSearchText(file);

        if (searchText.isEmpty()) {
            return;
        }

        resultsPanel.removeAll();

        JLabel searching =
                new JLabel(
                        "Searching iTunes..."
                );

        resultsPanel.add(searching);

        resultsPanel.revalidate();
        resultsPanel.repaint();

        Thread thread =
                new Thread(() -> {

                    try {

                        String encoded =
                                URLEncoder.encode(
                                        searchText,
                                        StandardCharsets.UTF_8
                                );

                        String url =
                                "https://itunes.apple.com/search"
                                + "?term="
                                + encoded
                                + "&media=music"
                                + "&entity=song"
                                + "&limit=20";

                        Request request =
                                new Request.Builder()
                                        .url(url)
                                        .get()
                                        .build();

                        try (Response response =
                                     httpClient.newCall(
                                             request
                                     ).execute()) {

                            if (!response.isSuccessful()) {

                                SwingUtilities.invokeLater(
                                        () -> showSearchError(
                                                "iTunes HTTP error: "
                                                        + response.code()
                                        )
                                );

                                return;
                            }

                            String json =
                                    response.body() != null
                                            ? response.body().string()
                                            : "";

                            List<ITunesResult> results =
                                    parseITunesResults(json);

                            SwingUtilities.invokeLater(
                                    () -> showITunesResults(
                                            results
                                    )
                            );
                        }

                    } catch (Exception e) {

                        SwingUtilities.invokeLater(
                                () -> showSearchError(
                                        "Search error: "
                                                + e.getMessage()
                                )
                        );
                    }

                });

        thread.setDaemon(true);
        thread.start();
    }

    private String getSearchText(File file) {

        TagInfo tag =
                readTags(file);

        String title =
                tag.title.trim();

        if (!title.isEmpty()) {
            return title;
        }

        String name =
                file.getName();

        int dot =
                name.lastIndexOf('.');

        if (dot > 0) {
            name =
                    name.substring(
                            0,
                            dot
                    );
        }

        // Remove common track numbering:
        //
        // 01 - Song
        // 01. Song
        // 01 Song

        name =
                name.replaceFirst(
                        "^\\s*\\d+\\s*[-.]?\\s*",
                        ""
                );

        return name.trim();
    }

    // ============================================================
    // Parse iTunes
    // ============================================================

    private List<ITunesResult> parseITunesResults(
            String json
    ) {

        List<ITunesResult> list =
                new ArrayList<>();

        JsonObject root =
                JsonParser.parseString(json)
                        .getAsJsonObject();

        if (!root.has("results")) {
            return list;
        }

        JsonArray results =
                root.getAsJsonArray("results");

        for (int i = 0;
             i < results.size();
             i++) {

            JsonObject object =
                    results.get(i)
                            .getAsJsonObject();

            ITunesResult result =
                    new ITunesResult();

            result.trackName =
                    getJsonString(
                            object,
                            "trackName"
                    );

            result.artistName =
                    getJsonString(
                            object,
                            "artistName"
                    );

            result.collectionName =
                    getJsonString(
                            object,
                            "collectionName"
                    );

            result.primaryGenreName =
                    getJsonString(
                            object,
                            "primaryGenreName"
                    );

            result.releaseDate =
                    getJsonString(
                            object,
                            "releaseDate"
                    );

            result.trackNumber =
                    getJsonInt(
                            object,
                            "trackNumber"
                    );

            result.discNumber =
                    getJsonInt(
                            object,
                            "discNumber"
                    );

            result.albumArtist =
                    result.artistName;

            if (!result.trackName.isEmpty()
                    || !result.artistName.isEmpty()) {

                list.add(result);
            }
        }

        return list;
    }

    private String getJsonString(
            JsonObject object,
            String key
    ) {

        if (!object.has(key)
                || object.get(key).isJsonNull()) {

            return "";
        }

        return object.get(key)
                .getAsString();
    }

    private int getJsonInt(
            JsonObject object,
            String key
    ) {

        if (!object.has(key)
                || object.get(key).isJsonNull()) {

            return 0;
        }

        try {

            return object.get(key)
                    .getAsInt();

        } catch (Exception e) {

            return 0;
        }
    }

    // ============================================================
    // Display iTunes results
    // ============================================================

    private void showITunesResults(
            List<ITunesResult> results
    ) {

        resultsPanel.removeAll();

        if (results.isEmpty()) {

            JTextPane empty =
                    createMultiLanguageTextPane(
                            "No iTunes results found."
                    );

            resultsPanel.add(empty);

        } else {

            for (ITunesResult result : results) {

                resultsPanel.add(
                        createResultPanel(result)
                );

                resultsPanel.add(
                        Box.createVerticalStrut(8)
                );
            }
        }

        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private JPanel createResultPanel(
            ITunesResult result
    ) {

        JPanel panel =
                new JPanel(
                        new BorderLayout(10, 5)
                );

        panel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                Color.LIGHT_GRAY
                        ),
                        new EmptyBorder(
                                8,
                                8,
                                8,
                                8
                        )
                )
        );

        JPanel textPanel =
                new JPanel();

        textPanel.setLayout(
                new BoxLayout(
                        textPanel,
                        BoxLayout.Y_AXIS
                )
        );

        String trackText =
                "Track: "
                        + result.trackName;

        String artistText =
                "Artist: "
                        + result.artistName;

        String albumText =
                "Album: "
                        + result.collectionName;

        String genreText =
                "Genre: "
                        + result.primaryGenreName;

        String dateText =
                "Release: "
                        + result.releaseDate;

        JTextPane track =
                createMultiLanguageTextPane(
                        trackText
                );

        JTextPane artist =
                createMultiLanguageTextPane(
                        artistText
                );

        JTextPane album =
                createMultiLanguageTextPane(
                        albumText
                );

        JTextPane genre =
                createMultiLanguageTextPane(
                        genreText
                );

        JTextPane date =
                createMultiLanguageTextPane(
                        dateText
                );

        textPanel.add(track);
        textPanel.add(artist);
        textPanel.add(album);
        textPanel.add(genre);
        textPanel.add(date);

        panel.add(
                textPanel,
                BorderLayout.CENTER
        );

        JButton apply =
                new JButton("Apply to File");

        apply.addActionListener(
                e -> applyResultToFile(result)
        );

        panel.add(
                apply,
                BorderLayout.EAST
        );

        return panel;
    }

    // ============================================================
    // Apply metadata
    // ============================================================

    private void applyResultToFile(
            ITunesResult result
    ) {

        if (selectedFile == null) {

            JOptionPane.showMessageDialog(
                    this,
                    "Please select a file first."
            );

            return;
        }

        try {

            AudioFile audioFile =
                    AudioFileIO.read(
                            selectedFile
                    );

            Tag tag =
                    audioFile.getTagOrCreateAndSetDefault();

            setTag(
                    tag,
                    FieldKey.TITLE,
                    result.trackName
            );

            setTag(
                    tag,
                    FieldKey.ARTIST,
                    result.artistName
            );

            setTag(
                    tag,
                    FieldKey.ALBUM_ARTIST,
                    result.albumArtist
            );

            setTag(
                    tag,
                    FieldKey.ALBUM,
                    result.collectionName
            );

            setTag(
                    tag,
                    FieldKey.GENRE,
                    result.primaryGenreName
            );

            if (result.trackNumber > 0) {

                setTag(
                        tag,
                        FieldKey.TRACK,
                        String.valueOf(
                                result.trackNumber
                        )
                );
            }

            if (result.discNumber > 0) {

                setTag(
                        tag,
                        FieldKey.DISC_NO,
                        String.valueOf(
                                result.discNumber
                        )
                );
            }

            if (result.releaseDate != null
                    && result.releaseDate.length() >= 4) {

                setTag(
                        tag,
                        FieldKey.YEAR,
                        result.releaseDate.substring(
                                0,
                                4
                        )
                );
            }

            audioFile.commit();

            updateSelectedTableRow();

            showSelectedFile(
                    selectedFile
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Tags applied successfully."
            );

        } catch (Exception e) {

            e.printStackTrace();

            JOptionPane.showMessageDialog(
                    this,
                    "Cannot write tags:\n"
                            + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void setTag(
            Tag tag,
            FieldKey key,
            String value
    )
            throws FieldDataInvalidException {

        if (value == null) {
            value = "";
        }

        tag.setField(
                key,
                value
        );
    }

    // ============================================================
    // Update table after Apply
    // ============================================================

    private void updateSelectedTableRow() {

        int row =
                fileTable.getSelectedRow();

        if (row < 0
                || selectedFile == null) {

            return;
        }

        TagInfo tag =
                readTags(selectedFile);

        fileTableModel.setValueAt(
                tag.title,
                row,
                1
        );

        fileTableModel.setValueAt(
                tag.artist,
                row,
                2
        );

        fileTable.repaint();
    }

    // ============================================================
    // Search error
    // ============================================================

    private void showSearchError(
            String message
    ) {

        resultsPanel.removeAll();

        JTextPane error =
                createMultiLanguageTextPane(
                        message
                );

        resultsPanel.add(error);

        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    // ============================================================
    // JTable mixed-language renderer
    // ============================================================

    private static class MultiLanguageTableRenderer
            extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {

            String text = "";

            if (value != null) {

                if (value instanceof File) {

                    text =
                            ((File) value)
                                    .getAbsolutePath();

                } else {

                    text =
                            value.toString();
                }
            }

            JTextPane pane =
                    createMultiLanguageTextPane(
                            text
                    );

            pane.setOpaque(true);

            if (isSelected) {

                pane.setBackground(
                        table.getSelectionBackground()
                );

                pane.setForeground(
                        table.getSelectionForeground()
                );

            } else {

                pane.setBackground(
                        table.getBackground()
                );

                pane.setForeground(
                        table.getForeground()
                );
            }

            pane.setBorder(
                    new EmptyBorder(
                            4,
                            6,
                            4,
                            6
                    )
            );

            return pane;
        }
    }

    // ============================================================
    // Data classes
    // ============================================================

    private static class TagInfo {

        String title = "";
        String artist = "";
        String album = "";
        String albumArtist = "";
        String genre = "";
        String year = "";
        String track = "";
        String disc = "";
    }

    private static class ITunesResult {

        String trackName = "";
        String artistName = "";
        String collectionName = "";
        String albumArtist = "";
        String primaryGenreName = "";
        String releaseDate = "";

        int trackNumber;
        int discNumber;
    }
}