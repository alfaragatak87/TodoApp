import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

// ========================================================================
// MODEL — merepresentasikan satu tugas (Encapsulation: semua field private,
// hanya bisa diakses/diubah lewat method publik yang disediakan).
// ========================================================================
class Task implements Serializable {
    private static final long serialVersionUID = 3L;

    // [OOP CONCEPT: ENCAPSULATION]
    // Semua atribut dibungkus menjadi private agar tidak bisa dimanipulasi
    // secara langsung dari luar kelas. Harus menggunakan public method (getter/setter).
    private final int id;
    private String description;
    private String notes;           // catatan/deskripsi tambahan
    private LocalDateTime deadline;
    private boolean completed;

    public Task(int id, String description, String notes, LocalDateTime deadline) {
        this.id = id;
        this.description = description;
        this.notes = (notes == null) ? "" : notes.trim();
        this.deadline = deadline;
        this.completed = false;
    }

    public int getId()              { return id; }
    public String getDescription()  { return description; }
    public String getNotes()        { return notes; }
    public LocalDateTime getDeadline() { return deadline; }
    public boolean isCompleted()    { return completed; }
    public void toggleCompleted()   { completed = !completed; }

    public void update(String newDescription, String newNotes, LocalDateTime newDeadline) {
        this.description = newDescription;
        this.notes = (newNotes == null) ? "" : newNotes.trim();
        this.deadline = newDeadline;
    }

    public boolean hasDeadline() { return deadline != null; }

    public boolean isOverdue() {
        return deadline != null && !completed && LocalDateTime.now().isAfter(deadline);
    }

    /** Teks sisa waktu / keterlambatan, dalam Bahasa Indonesia, untuk ditampilkan di UI. */
    public String getTimeRemaining() {
        if (deadline == null) return "Tidak ada deadline";

        LocalDateTime now = LocalDateTime.now();
        boolean late = now.isAfter(deadline);
        LocalDateTime from = late ? deadline : now;
        LocalDateTime to = late ? now : deadline;

        long minutes = ChronoUnit.MINUTES.between(from, to);
        long days = minutes / (60 * 24);
        long hours = (minutes / 60) % 24;
        long mins = minutes % 60;

        String besaran;
        if (days > 0) besaran = String.format("%d hari %d jam %d menit", days, hours, mins);
        else if (hours > 0) besaran = String.format("%d jam %d menit", hours, mins);
        else besaran = String.format("%d menit", mins);

        return late ? "Terlambat " + besaran : besaran;
    }
}

// ========================================================================
// LOGIC / REPOSITORY — mengelola koleksi Task dan persistensinya ke disk.
// Kelas ini menyembunyikan detail penyimpanan dari kelas GUI (Abstraction).
// ========================================================================
class TodoList {
    // [OOP CONCEPT: ABSTRACTION]
    // Kelas ini menyembunyikan detail kerumitan struktur data List dan proses File I/O.
    // GUI (kelas lain) hanya memanggil fungsi sederhana seperti addTask() tanpa tahu kerumitan di baliknya.
    private List<Task> tasks;
    private int nextId;
    private static final String FILE_NAME = "tasks.dat";

    public TodoList() {
        tasks = new ArrayList<>();
        nextId = 1;
        loadFromFile();
    }

    public void addTask(String description, String notes, LocalDateTime deadline) {
        if (description == null || description.trim().isEmpty()) return;
        tasks.add(new Task(nextId++, description.trim(), notes, deadline));
        saveToFile();
    }

    public void removeTask(int id) {
        tasks.removeIf(t -> t.getId() == id);
        saveToFile();
    }

    public void editTask(int id, String newDesc, String newNotes, LocalDateTime newDeadline) {
        if (newDesc == null || newDesc.trim().isEmpty()) return;
        for (Task t : tasks) {
            if (t.getId() == id) {
                t.update(newDesc.trim(), newNotes, newDeadline);
                break;
            }
        }
        saveToFile();
    }

    public void toggleTask(int id) {
        for (Task t : tasks) {
            if (t.getId() == id) {
                t.toggleCompleted();
                break;
            }
        }
        saveToFile();
    }

    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks);
    }

    // ===== PENYIMPANAN =====
    private void saveToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
            oos.writeObject(tasks);
            oos.writeInt(nextId);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Gagal menyimpan data: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromFile() {
        File file = new File(FILE_NAME);
        if (!file.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            tasks = (List<Task>) ois.readObject();
            nextId = ois.readInt();
        } catch (IOException | ClassNotFoundException e) {
            JOptionPane.showMessageDialog(null,
                    "Gagal memuat data: " + e.getMessage() + "\nMemulai dengan daftar kosong.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            tasks = new ArrayList<>();
            nextId = 1;
        }
    }
}

// ========================================================================
// VIEW / CONTROLLER — UI Swing. Mengatur tampilan, input pengguna, dan
// menghubungkan aksi pengguna ke TodoList (Inheritance dipakai lewat
// extends JPanel pada TaskCellRenderer; Polymorphism lewat override
// getListCellRendererComponent()).
// ========================================================================
class TodoGUI {
    private final TodoList todoList = new TodoList();
    private final JFrame frame = new JFrame("Todo List with Countdown");
    private final DefaultListModel<Task> listModel = new DefaultListModel<>();
    private final JList<Task> taskList = new JList<>(listModel);
    private final JTextField inputField = new JTextField();
    private final JTextArea  notesField = new JTextArea(2, 20);
    private final JTextField searchField = new JTextField();
    private final JLabel clockLabel = new JLabel("", SwingConstants.RIGHT);
    private final JComboBox<String> filterBox =
            new JComboBox<>(new String[]{"Semua", "Belum selesai", "Selesai", "Terlambat"});
    private final JComboBox<String> sortBox =
            new JComboBox<>(new String[]{"Terbaru ditambahkan", "Deadline terdekat", "Abjad (A-Z)"});
    private Timer timer;

    public TodoGUI() {
        initUI();
        refreshList();
        startTimer();
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
                // fallback ke look and feel default
            }
        }

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(850, 750);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(0, 0));
        frame.getContentPane().setBackground(new Color(245, 247, 250));

        // ---------- Header ----------
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
                new EmptyBorder(20, 25, 20, 25)
        ));
        JLabel titleLabel = new JLabel("My Todo List", SwingConstants.LEFT);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titleLabel.setForeground(new Color(44, 62, 80));
        clockLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        clockLabel.setForeground(new Color(100, 100, 100));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(clockLabel, BorderLayout.EAST);
        frame.add(headerPanel, BorderLayout.NORTH);

        // ---------- Panel tengah: toolbar (search + filter + sort) di atas list ----------
        JPanel centerPanel = new JPanel(new BorderLayout(0, 15));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(20, 25, 10, 25));

        JPanel toolbarPanel = new JPanel(new BorderLayout(10, 0));
        toolbarPanel.setOpaque(false);

        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(new JLabel("Cari: "), BorderLayout.WEST);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        searchField.putClientProperty("JTextField.placeholderText", "Cari tugas...");
        searchPanel.add(searchField, BorderLayout.CENTER);

        JPanel filterSortPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterSortPanel.setOpaque(false);
        filterBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sortBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        filterSortPanel.add(new JLabel("Filter:"));
        filterSortPanel.add(filterBox);
        filterSortPanel.add(new JLabel("Urutkan:"));
        filterSortPanel.add(sortBox);

        toolbarPanel.add(searchPanel, BorderLayout.CENTER);
        toolbarPanel.add(filterSortPanel, BorderLayout.EAST);
        centerPanel.add(toolbarPanel, BorderLayout.NORTH);

        // ---------- Daftar tugas ----------
        taskList.setCellRenderer(new TaskCellRenderer());
        taskList.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskList.setBackground(new Color(245, 247, 250));
        taskList.setFixedCellHeight(72);

        JScrollPane scrollPane = new JScrollPane(taskList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230)));
        scrollPane.getViewport().setBackground(new Color(245, 247, 250));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        frame.add(centerPanel, BorderLayout.CENTER);

        // ---------- Panel bawah: input tugas baru ----------
        JPanel bottomPanel = new JPanel(new BorderLayout(15, 15));
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)),
                new EmptyBorder(20, 25, 20, 25)
        ));

        JPanel descPanel = new JPanel(new BorderLayout(5, 0));
        descPanel.setOpaque(false);

        JPanel labelCol = new JPanel(new GridLayout(2, 1, 0, 8));
        labelCol.setOpaque(false);
        labelCol.add(new JLabel("Tugas:"));
        labelCol.add(new JLabel("Catatan:"));

        JPanel fieldCol = new JPanel(new GridLayout(2, 1, 0, 8));
        fieldCol.setOpaque(false);
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        notesField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        fieldCol.add(inputField);
        fieldCol.add(new JScrollPane(notesField));

        descPanel.add(labelCol, BorderLayout.WEST);
        descPanel.add(fieldCol, BorderLayout.CENTER);
        bottomPanel.add(descPanel, BorderLayout.CENTER);

        // Tombol aksi
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttonPanel.setOpaque(false);

        JButton addBtn = createStyledButton("Tambah", new Color(52, 152, 219));
        JButton addDeadlineBtn = createStyledButton("Tambah Deadline", new Color(155, 89, 182));
        JButton editBtn = createStyledButton("Edit", new Color(243, 156, 18));
        JButton doneBtn = createStyledButton("Selesai", new Color(46, 204, 113));
        JButton deleteBtn = createStyledButton("Hapus", new Color(231, 76, 60));

        buttonPanel.add(addBtn);
        buttonPanel.add(addDeadlineBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(doneBtn);
        buttonPanel.add(deleteBtn);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        frame.add(bottomPanel, BorderLayout.SOUTH);

        // ---------- Event handler ----------
        addBtn.addActionListener(e -> addTask(null));
        addDeadlineBtn.addActionListener(e -> showDeadlinePickerDialog());
        editBtn.addActionListener(e -> showEditDialog());
        doneBtn.addActionListener(e -> toggleTask());
        deleteBtn.addActionListener(e -> deleteTask());
        inputField.addActionListener(e -> addTask(null));

        taskList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    deleteTask();
                }
            }
        });

        // Klik langsung pada baris tugas untuk toggle atau edit
        // [OOP CONCEPT: INHERITANCE & POLYMORPHISM]
        // Membuat Anonymous Inner Class yang mewarisi (extends) MouseAdapter,
        // dan menimpa (override) secara polimorfik fungsi mouseClicked.
        taskList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int idx = taskList.locationToIndex(e.getPoint());
                if (idx != -1) {
                    taskList.setSelectedIndex(idx);
                    int cellWidth = taskList.getWidth();
                    // Area tombol Edit: 55px dari sisi kanan kartu (dikurangi padding 10)
                    boolean clickedEditBtn = e.getX() > cellWidth - 65;
                    if (clickedEditBtn) {
                        showEditDialog();
                    } else if (e.getX() < 50 || e.getClickCount() == 2) {
                        // Klik di area checkbox atau double-click = toggle selesai
                        Task task = listModel.get(idx);
                        todoList.toggleTask(task.getId());
                        refreshList();
                    }
                }
            }

            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int cellWidth = taskList.getWidth();
                if (e.getX() > cellWidth - 65) {
                    taskList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    taskList.setCursor(Cursor.getDefaultCursor());
                }
            }
        });
        taskList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int cellWidth = taskList.getWidth();
                if (e.getX() > cellWidth - 65) {
                    taskList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    taskList.setCursor(Cursor.getDefaultCursor());
                }
            }
        });

        // Cari secara langsung saat mengetik
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshList(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshList(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshList(); }
        });
        filterBox.addActionListener(e -> refreshList());
        sortBox.addActionListener(e -> refreshList());

        frame.setVisible(true);
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(bg.darker());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(bg);
            }
        });
        
        return btn;
    }

    // ===== DIALOG PEMILIH DEADLINE =====
    private void showDeadlinePickerDialog() {
        String desc = inputField.getText();
        if (desc.trim().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Deskripsi tugas tidak boleh kosong.");
            return;
        }

        JDialog dialog = new JDialog(frame, "Pilih Deadline", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(350, 250);
        dialog.setLocationRelativeTo(frame);

        JPanel pickerPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        pickerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        SpinnerDateModel dateModel = new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH);
        JSpinner dateSpinner = new JSpinner(dateModel);
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "dd/MM/yyyy"));
        dateSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        SpinnerDateModel timeModel = new SpinnerDateModel(new Date(), null, null, Calendar.HOUR_OF_DAY);
        JSpinner timeSpinner = new JSpinner(timeModel);
        timeSpinner.setEditor(new JSpinner.DateEditor(timeSpinner, "HH:mm"));
        timeSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        pickerPanel.add(new JLabel("Tanggal:"));
        pickerPanel.add(dateSpinner);
        pickerPanel.add(new JLabel("Jam (24h):"));
        pickerPanel.add(timeSpinner);

        JLabel infoLabel = new JLabel("Pilih tanggal dan jam deadline", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        infoLabel.setForeground(Color.GRAY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton okBtn = createStyledButton("OK", new Color(46, 204, 113));
        JButton cancelBtn = createStyledButton("Batal", new Color(231, 76, 60));
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        dialog.add(pickerPanel, BorderLayout.CENTER);
        dialog.add(infoLabel, BorderLayout.NORTH);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        okBtn.addActionListener(e -> {
            Date selectedDate = (Date) dateSpinner.getValue();
            Date selectedTime = (Date) timeSpinner.getValue();

            Calendar calDate = Calendar.getInstance();
            calDate.setTime(selectedDate);
            Calendar calTime = Calendar.getInstance();
            calTime.setTime(selectedTime);

            LocalDateTime deadline = LocalDateTime.of(
                    calDate.get(Calendar.YEAR),
                    calDate.get(Calendar.MONTH) + 1,
                    calDate.get(Calendar.DAY_OF_MONTH),
                    calTime.get(Calendar.HOUR_OF_DAY),
                    calTime.get(Calendar.MINUTE)
            );

            addTask(deadline);
            dialog.dispose();
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        dialog.setVisible(true);
    }

    private void showEditDialog() {
        int idx = taskList.getSelectedIndex();
        if (idx == -1) {
            JOptionPane.showMessageDialog(frame, "Pilih tugas yang akan diedit.");
            return;
        }
        Task task = listModel.get(idx);

        JDialog dialog = new JDialog(frame, "Edit Tugas", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(420, 380);
        dialog.setLocationRelativeTo(frame);

        JPanel editPanel = new JPanel(new GridLayout(5, 2, 5, 8));
        editPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextField descField = new JTextField(task.getDescription());
        descField.setFont(new Font("Segoe UI", Font.PLAIN, 15));

        JTextArea editNotesField = new JTextArea(task.getNotes(), 2, 20);
        editNotesField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        editNotesField.setLineWrap(true);
        editNotesField.setWrapStyleWord(true);

        JCheckBox deadlineCheck = new JCheckBox("Punya Deadline?");
        deadlineCheck.setSelected(task.hasDeadline());

        SpinnerDateModel dateModel = new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH);
        JSpinner dateSpinner = new JSpinner(dateModel);
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "dd/MM/yyyy"));
        dateSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        SpinnerDateModel timeModel = new SpinnerDateModel(new Date(), null, null, Calendar.HOUR_OF_DAY);
        JSpinner timeSpinner = new JSpinner(timeModel);
        timeSpinner.setEditor(new JSpinner.DateEditor(timeSpinner, "HH:mm"));
        timeSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        if (task.hasDeadline()) {
            LocalDateTime dl = task.getDeadline();
            Calendar cal = Calendar.getInstance();
            cal.set(dl.getYear(), dl.getMonthValue() - 1, dl.getDayOfMonth(), dl.getHour(), dl.getMinute(), 0);
            dateModel.setValue(cal.getTime());
            timeModel.setValue(cal.getTime());
        }

        dateSpinner.setEnabled(deadlineCheck.isSelected());
        timeSpinner.setEnabled(deadlineCheck.isSelected());

        deadlineCheck.addActionListener(e -> {
            dateSpinner.setEnabled(deadlineCheck.isSelected());
            timeSpinner.setEnabled(deadlineCheck.isSelected());
        });

        editPanel.add(new JLabel("Nama Tugas:"));
        editPanel.add(descField);
        editPanel.add(new JLabel("Catatan:"));
        editPanel.add(new JScrollPane(editNotesField));
        editPanel.add(new JLabel(""));
        editPanel.add(deadlineCheck);
        editPanel.add(new JLabel("Tanggal:"));
        editPanel.add(dateSpinner);
        editPanel.add(new JLabel("Jam (24h):"));
        editPanel.add(timeSpinner);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton saveBtn = createStyledButton("Simpan", new Color(46, 204, 113));
        JButton cancelBtn = createStyledButton("Batal", new Color(231, 76, 60));
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);

        dialog.add(editPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        saveBtn.addActionListener(e -> {
            LocalDateTime newDeadline = null;
            if (deadlineCheck.isSelected()) {
                Date selectedDate = (Date) dateSpinner.getValue();
                Date selectedTime = (Date) timeSpinner.getValue();

                Calendar calDate = Calendar.getInstance();
                calDate.setTime(selectedDate);
                Calendar calTime = Calendar.getInstance();
                calTime.setTime(selectedTime);

                newDeadline = LocalDateTime.of(
                        calDate.get(Calendar.YEAR),
                        calDate.get(Calendar.MONTH) + 1,
                        calDate.get(Calendar.DAY_OF_MONTH),
                        calTime.get(Calendar.HOUR_OF_DAY),
                        calTime.get(Calendar.MINUTE)
                );
            }
            todoList.editTask(task.getId(), descField.getText(), editNotesField.getText(), newDeadline);
            refreshList();
            dialog.dispose();
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    /** Menambahkan tugas baru. deadline == null berarti tugas biasa tanpa deadline. */
    private void addTask(LocalDateTime deadline) {
        String desc = inputField.getText();
        if (desc.trim().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Nama tugas tidak boleh kosong.");
            return;
        }
        todoList.addTask(desc, notesField.getText(), deadline);
        inputField.setText("");
        notesField.setText("");
        refreshList();
    }

    private void toggleTask() {
        int idx = taskList.getSelectedIndex();
        if (idx == -1) {
            JOptionPane.showMessageDialog(frame, "Pilih tugas terlebih dahulu.");
            return;
        }
        Task task = listModel.get(idx);
        todoList.toggleTask(task.getId());
        refreshList();
    }

    private void deleteTask() {
        int idx = taskList.getSelectedIndex();
        if (idx == -1) {
            JOptionPane.showMessageDialog(frame, "Pilih tugas yang akan dihapus.");
            return;
        }
        Task task = listModel.get(idx);
        int confirm = JOptionPane.showConfirmDialog(frame,
                "Hapus tugas: \"" + task.getDescription() + "\"?",
                "Konfirmasi Hapus",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            todoList.removeTask(task.getId());
            refreshList();
        }
    }

    /** Mengambil daftar tugas dari TodoList, lalu menerapkan pencarian, filter, dan urutan. */
    private void refreshList() {
        List<Task> all = todoList.getAllTasks();

        // 1) Pencarian berdasarkan teks
        String query = searchField.getText().trim().toLowerCase();
        if (!query.isEmpty()) {
            all.removeIf(t -> !t.getDescription().toLowerCase().contains(query));
        }

        // 2) Filter berdasarkan status
        String filter = (String) filterBox.getSelectedItem();
        if (filter != null) {
            switch (filter) {
                case "Belum selesai":
                    all.removeIf(Task::isCompleted);
                    break;
                case "Selesai":
                    all.removeIf(t -> !t.isCompleted());
                    break;
                case "Terlambat":
                    all.removeIf(t -> !t.isOverdue());
                    break;
                default: // "Semua" -> tidak difilter
            }
        }

        // 3) Pengurutan
        String sort = (String) sortBox.getSelectedItem();
        if (sort != null) {
            switch (sort) {
                case "Deadline terdekat":
                    all.sort(Comparator.comparing(
                            (Task t) -> t.getDeadline() == null,
                            Boolean::compareTo
                    ).thenComparing(t -> t.getDeadline() == null ? LocalDateTime.MAX : t.getDeadline()));
                    break;
                case "Abjad (A-Z)":
                    all.sort(Comparator.comparing(t -> t.getDescription().toLowerCase()));
                    break;
                default: // "Terbaru ditambahkan" -> urutan id asli sudah sesuai
            }
        }

        listModel.clear();
        for (Task t : all) {
            listModel.addElement(t);
        }
    }

    private void startTimer() {
        timer = new Timer(1000, e -> {
            updateClock();
            boolean hasDeadline = todoList.getAllTasks().stream().anyMatch(Task::hasDeadline);
            if (hasDeadline) {
                taskList.repaint();
            }
        });
        timer.start();
    }

    private void updateClock() {
        LocalDateTime now = LocalDateTime.now();
        clockLabel.setText(now.format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy HH:mm:ss")));
    }

    // ===== CELL RENDERER =====
    // [OOP CONCEPT: INHERITANCE]
    // TaskCellRenderer mewarisi (extends) JPanel sehingga memiliki semua sifat/komponen GUI dari JPanel,
    // lalu ditambahkan komponen kustom kita sendiri (seperti checkbox dan label).
    private static class TaskCellRenderer extends JPanel implements ListCellRenderer<Task> {
        private final JCheckBox checkBox  = new JCheckBox();
        private final JLabel    descLabel = new JLabel();
        private final JLabel    timeLabel = new JLabel();
        private final JPanel    editBtn   = new JPanel();
        private final JLabel    editLbl   = new JLabel("Edit");

        public TaskCellRenderer() {
            setLayout(new BorderLayout(12, 0));
            setOpaque(true);

            checkBox.setOpaque(false);
            checkBox.setEnabled(false);

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new java.awt.GridLayout(2, 1, 0, 2));
            textPanel.setOpaque(false);
            descLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            textPanel.add(descLabel);
            textPanel.add(timeLabel);

            // Tombol Edit kecil di sisi kanan kartu
            editBtn.setPreferredSize(new Dimension(55, 32));
            editBtn.setBackground(new Color(243, 156, 18));
            editBtn.setLayout(new java.awt.GridBagLayout());
            editBtn.setOpaque(true);
            editLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            editLbl.setForeground(Color.WHITE);
            editBtn.add(editLbl);

            JPanel eastPanel = new JPanel(new java.awt.GridBagLayout());
            eastPanel.setOpaque(false);
            eastPanel.setPreferredSize(new Dimension(65, 72));
            eastPanel.add(editBtn);

            add(checkBox, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
            add(eastPanel, BorderLayout.EAST);
        }

        // [OOP CONCEPT: POLYMORPHISM]
        // Overriding (menimpa) metode getListCellRendererComponent dari interface ListCellRenderer.
        // Fungsi ini akan dipanggil secara polimorfik oleh Swing ketika merender JList.
        @Override
        public Component getListCellRendererComponent(JList<? extends Task> list, Task task,
                int index, boolean isSelected, boolean cellHasFocus) {

            checkBox.setSelected(task.isCompleted());

            if (task.isCompleted()) {
                descLabel.setText("<html><strike>" + task.getDescription() + "</strike></html>");
                descLabel.setForeground(new Color(160, 160, 160));
            } else {
                descLabel.setText(task.getDescription());
                descLabel.setForeground(new Color(30, 30, 30));
            }

            // Baris kedua: catatan (jika ada) atau status waktu
            String notes = task.getNotes();
            if (notes != null && !notes.isEmpty()) {
                String timePart;
                if (task.hasDeadline()) {
                    String rem = task.getTimeRemaining();
                    if (task.isOverdue()) {
                        timePart = " | Terlambat: " + rem;
                        timeLabel.setForeground(new Color(231, 76, 60));
                    } else if (task.isCompleted()) {
                        timePart = " | Selesai";
                        timeLabel.setForeground(new Color(46, 204, 113));
                    } else {
                        timePart = " | Sisa: " + rem;
                        timeLabel.setForeground(new Color(52, 152, 219));
                    }
                } else {
                    timePart = "";
                    timeLabel.setForeground(new Color(100, 100, 100));
                }
                timeLabel.setText(notes + timePart);
            } else {
                if (task.hasDeadline()) {
                    String rem = task.getTimeRemaining();
                    if (task.isOverdue()) {
                        timeLabel.setText("Terlambat: " + rem);
                        timeLabel.setForeground(new Color(231, 76, 60));
                    } else if (task.isCompleted()) {
                        timeLabel.setText("Selesai");
                        timeLabel.setForeground(new Color(46, 204, 113));
                    } else {
                        timeLabel.setText("Sisa waktu: " + rem);
                        timeLabel.setForeground(new Color(52, 152, 219));
                    }
                } else {
                    timeLabel.setText("Tugas biasa (tanpa deadline)");
                    timeLabel.setForeground(new Color(150, 150, 150));
                }
            }

            if (isSelected) {
                setBackground(new Color(219, 237, 255));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(5, 10, 5, 10),
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(52, 152, 219), 2, true),
                                BorderFactory.createEmptyBorder(10, 14, 10, 14)
                        )
                ));
                editBtn.setBackground(new Color(230, 140, 10));
            } else {
                setBackground(Color.WHITE);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(5, 10, 5, 10),
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(225, 225, 225), 1, true),
                                BorderFactory.createEmptyBorder(10, 14, 10, 14)
                        )
                ));
                editBtn.setBackground(new Color(243, 156, 18));
            }
            return this;
        }
    }
}

// ========================================================================
// MAIN — titik masuk aplikasi.
// ========================================================================
public class TodoApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(TodoGUI::new);
    }
}
