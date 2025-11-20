import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * HospitalPatientsApp
 *
 * Single-file Java 11+ Swing application that demonstrates:
 * - Encapsulation, Inheritance, Abstraction, Polymorphism
 * - File handling (serialization .ser + human-readable .txt CSV)
 * - GUI: Login -> Dashboard -> Patient Management (Add + View)
 *
 * DOB format expected in the Add Patient form: dd-MM-yyyy  (e.g., 12-05-1995)
 *
 * Compile:
 * javac HospitalPatientsApp.java
 * Run:
 * java HospitalPatientsApp
 *
 * Sample login:
 * admin / admin123
 */
class HospitalPatientsApp {

    // ------------------ FileManager (Abstraction for file IO) ------------------
    public static class FileManager {
        private static final String DATA_DIR = "data";
        private static final String PATIENT_SER = "patients.ser";
        private static final String PATIENT_TXT = "patients.txt";

        static {
            File d = new File(DATA_DIR);
            if (!d.exists()) d.mkdirs();
        }

        // Save patients list as Java serialized object
        public static synchronized void savePatients(List<Patient> list) {
            File f = new File(DATA_DIR, PATIENT_SER);
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
                oos.writeObject(list);
            } catch (IOException ex) {
                showError("Error while saving patients (ser): " + ex.getMessage());
            }
            // Also write a human-readable CSV-style file
            writePatientsTxt(list);
        }

        // Load patients; return empty list on error or not exists
        @SuppressWarnings("unchecked")
        public static synchronized List<Patient> loadPatients() {
            File f = new File(DATA_DIR, PATIENT_SER);
            if (!f.exists()) return new ArrayList<>();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                Object obj = ois.readObject();
                return (List<Patient>) obj;
            } catch (Exception ex) {
                showError("Error while loading patients (ser): " + ex.getMessage());
                return new ArrayList<>();
            }
        }

        // Write a CSV-ish file for quick viewing
        private static synchronized void writePatientsTxt(List<Patient> list) {
            File f = new File(DATA_DIR, PATIENT_TXT);
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("id,name,dob,age,gender,contact,address,medicalHistory");
                for (Patient p : list) {
                    String line = String.format("%s,%s,%s,%d,%s,%s,%s,%s",
                            escapeCsv(p.getId()),
                            escapeCsv(p.getName()),
                            p.getDob() == null ? "" : p.getDob().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                            p.getAge(),
                            escapeCsv(p.getGender()),
                            escapeCsv(p.getContact()),
                            escapeCsv(p.getAddress()),
                            escapeCsv(p.getMedicalHistory())
                    );
                    pw.println(line);
                }
            } catch (IOException ex) {
                showError("Error while saving patients.txt: " + ex.getMessage());
            }
        }

        // simple CSV escaping for commas/newlines
        private static String escapeCsv(String s) {
            if (s == null) return "";
            String out = s.replace("\"", "\"\"");
            if (out.contains(",") || out.contains("\n") || out.contains("\"")) {
                out = "\"" + out + "\"";
            }
            return out;
        }

        private static void showError(String msg) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, msg, "File Error", JOptionPane.ERROR_MESSAGE));
            System.err.println(msg);
        }
    }

    // ------------------ Models ------------------

    // Abstract Person: shows Inheritance and Encapsulation
    public static abstract class Person implements Serializable {
        private String id;
        private String name;
        private LocalDate dob;
        private String contact;

        public Person() {}

        public Person(String id, String name, LocalDate dob, String contact) {
            setId(id);
            setName(name);
            setDob(dob);
            setContact(contact);
        }

        public String getId() { return id; }
        public void setId(String id) {
            if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("ID required");
            this.id = id.trim();
        }

        public String getName() { return name; }
        public void setName(String name) {
            if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Name required");
            this.name = name.trim();
        }

        public LocalDate getDob() { return dob; }
        public void setDob(LocalDate dob) {
            this.dob = dob;
        }

        public String getContact() { return contact; }
        public void setContact(String contact) {
            if (contact == null || contact.trim().isEmpty()) throw new IllegalArgumentException("Contact required");
            this.contact = contact.trim();
        }

        public abstract String displayDetails();
    }

    // Patient class demonstrates Polymorphism (overrides displayDetails) and Encapsulation
    public static class Patient extends Person implements Serializable {
        private int age;
        private String gender;
        private String address;
        private String medicalHistory;

        public Patient() { super(); }

        public Patient(String id, String name, LocalDate dob, String contact, int age, String gender, String address, String medicalHistory) {
            super(id, name, dob, contact);
            setAge(age);
            setGender(gender);
            setAddress(address);
            setMedicalHistory(medicalHistory);
        }

        public int getAge() { return age; }
        public void setAge(int age) {
            if (age < 0 || age > 150) throw new IllegalArgumentException("Invalid age");
            this.age = age;
        }

        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = (gender == null) ? "" : gender.trim(); }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = (address == null) ? "" : address.trim(); }

        public String getMedicalHistory() { return medicalHistory; }
        public void setMedicalHistory(String medicalHistory) { this.medicalHistory = (medicalHistory == null) ? "" : medicalHistory.trim(); }

        @Override
        public String displayDetails() {
            return String.format("Patient[id=%s,name=%s,age=%d]", getId(), getName(), getAge());
        }

        // Overloaded search methods (example of polymorphism)
        public boolean matchesById(String id) {
            return getId().equalsIgnoreCase(id);
        }
        public boolean matchesByName(String name, boolean exact) {
            if (exact) return getName().equalsIgnoreCase(name);
            return getName().toLowerCase().contains(name.toLowerCase());
        }
    }

    // Doctor and Staff classes exist for completeness (inheritance)
    public static class Doctor extends Person implements Serializable {
        private String specialization;
        public Doctor(String id, String name, LocalDate dob, String contact, String specialization) {
            super(id, name, dob, contact);
            this.specialization = specialization;
        }
        @Override
        public String displayDetails() {
            return "Dr. " + getName() + " (" + specialization + ")";
        }
    }

    public static class Staff extends Person implements Serializable {
        private String role;
        public Staff(String id, String name, LocalDate dob, String contact, String role) {
            super(id, name, dob, contact);
            this.role = role;
        }
        @Override
        public String displayDetails() {
            return getName() + " - " + role;
        }
    }

    // ------------------ DAO for Patients ------------------
    public static class PatientDAO {
        private List<Patient> list;
        public PatientDAO() { list = FileManager.loadPatients(); }

        public synchronized void add(Patient p) {
            list.add(p);
            FileManager.savePatients(list);
        }

        public synchronized void update(Patient p) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equalsIgnoreCase(p.getId())) {
                    list.set(i, p);
                    FileManager.savePatients(list);
                    return;
                }
            }
        }

        public synchronized void delete(String id) {
            list.removeIf(x -> x.getId().equalsIgnoreCase(id));
            FileManager.savePatients(list);
        }

        public synchronized List<Patient> all() { return new ArrayList<>(list); }

        public synchronized Optional<Patient> findById(String id) {
            return list.stream().filter(x -> x.getId().equalsIgnoreCase(id)).findFirst();
        }
    }

    // ------------------ GUI: Login -> Dashboard -> Patient Management ------------------

    // Login Frame
    public static class LoginFrame extends JFrame {
        public LoginFrame() {
            setTitle("HMS - Login");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(360, 210);
            setLocationRelativeTo(null);
            initUI();
        }

        private void initUI() {
            JPanel p = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6,6,6,6);
            gbc.anchor = GridBagConstraints.WEST;

            JLabel lblUser = new JLabel("Username:");
            JTextField txtUser = new JTextField(16);
            JLabel lblPass = new JLabel("Password:");
            JPasswordField txtPass = new JPasswordField(16);
            JButton btnLogin = new JButton("Login");

            gbc.gridx=0; gbc.gridy=0; p.add(lblUser, gbc);
            gbc.gridx=1; p.add(txtUser, gbc);
            gbc.gridx=0; gbc.gridy=1; p.add(lblPass, gbc);
            gbc.gridx=1; p.add(txtPass, gbc);
            gbc.gridx=1; gbc.gridy=2; p.add(btnLogin, gbc);

            btnLogin.addActionListener(e -> {
                String u = txtUser.getText().trim();
                String pass = new String(txtPass.getPassword());
                String role = authenticate(u, pass);
                if (role != null) {
                    SwingUtilities.invokeLater(() -> {
                        DashboardFrame df = new DashboardFrame(role);
                        df.setVisible(true);
                    });
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid credentials", "Login failed", JOptionPane.ERROR_MESSAGE);
                }
            });

            add(p);
        }

        private String authenticate(String u, String p) {
            if ("admin".equals(u) && "admin123".equals(p)) return "Admin";
            if ("staff".equals(u) && "staff123".equals(p)) return "Staff";
            if ("doc".equals(u) && "doc123".equals(p)) return "Doctor";
            return null;
        }
    }

    // Dashboard
    public static class DashboardFrame extends JFrame {
        private final PatientDAO patientDAO = new PatientDAO();
        private final String role;
        public DashboardFrame(String role) {
            this.role = role;
            setTitle("HMS Dashboard - " + role);
            setSize(900, 600);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            initUI();
        }

        private void initUI() {
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnPatients = new JButton("Manage Patients");
            JButton btnExit = new JButton("Logout");
            top.add(btnPatients);
            top.add(btnExit);
            add(top, BorderLayout.NORTH);

            JLabel welcome = new JLabel("Welcome, " + role);
            welcome.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            add(welcome, BorderLayout.CENTER);

            btnPatients.addActionListener(e -> {
                PatientManagementFrame pf = new PatientManagementFrame(patientDAO);
                pf.setVisible(true);
            });

            btnExit.addActionListener(e -> {
                int c = JOptionPane.showConfirmDialog(this, "Logout?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (c == JOptionPane.YES_OPTION) {
                    dispose();
                    SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
                }
            });
        }
    }

    // Patient Management Frame: Add + View (JTable)
    public static class PatientManagementFrame extends JFrame {
        private final PatientDAO dao;
        private final DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Name","DOB","Age","Gender","Contact","Address","History"}, 0);
        private final JTable table = new JTable(model);

        public PatientManagementFrame(PatientDAO dao) {
            this.dao = dao;
            setTitle("Manage Patients");
            setSize(1000, 500);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(6,6));
            initUI();
            loadTable();
        }

        private void initUI() {
            add(new JScrollPane(table), BorderLayout.CENTER);

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnAdd = new JButton("Add Patient");
            JButton btnEdit = new JButton("Edit Selected");
            JButton btnDelete = new JButton("Delete Selected");
            JButton btnRefresh = new JButton("Refresh");
            JButton btnGenerateSample = new JButton("Generate Sample");

            top.add(btnAdd); top.add(btnEdit); top.add(btnDelete); top.add(btnRefresh); top.add(btnGenerateSample);
            add(top, BorderLayout.NORTH);

            btnAdd.addActionListener(e -> {
                Patient p = showPatientDialog(null);
                if (p != null) {
                    dao.add(p);
                    loadTable();
                    JOptionPane.showMessageDialog(this, "Patient saved successfully.");
                }
            });

            btnEdit.addActionListener(e -> {
                int r = table.getSelectedRow();
                if (r == -1) { JOptionPane.showMessageDialog(this, "Select a row to edit."); return; }
                String id = (String) model.getValueAt(r, 0);
                Optional<Patient> opt = dao.findById(id);
                if (!opt.isPresent()) { JOptionPane.showMessageDialog(this, "Record not found."); return; }
                Patient updated = showPatientDialog(opt.get());
                if (updated != null) {
                    dao.update(updated);
                    loadTable();
                    JOptionPane.showMessageDialog(this, "Patient updated.");
                }
            });

            btnDelete.addActionListener(e -> {
                int r = table.getSelectedRow();
                if (r == -1) { JOptionPane.showMessageDialog(this, "Select a row to delete."); return; }
                String id = (String) model.getValueAt(r, 0);
                int c = JOptionPane.showConfirmDialog(this, "Delete " + id + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (c == JOptionPane.YES_OPTION) {
                    dao.delete(id);
                    loadTable();
                }
            });

            btnRefresh.addActionListener(e -> loadTable());

            btnGenerateSample.addActionListener(e -> {
                try {
                    Patient a = new Patient("P001","Ali Khan", LocalDate.parse("12-05-1995", DateTimeFormatter.ofPattern("dd-MM-yyyy")), "03001234567", 30, "Male", "Karachi", "Cough");
                    Patient b = new Patient("P002","Sara Ali", LocalDate.parse("02-03-1990", DateTimeFormatter.ofPattern("dd-MM-yyyy")), "03007654321", 35, "Female", "Lahore", "None");
                    dao.add(a); dao.add(b);
                    loadTable();
                    JOptionPane.showMessageDialog(this, "Sample patients added.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error creating sample: " + ex.getMessage());
                }
            });
        }

        private void loadTable() {
            model.setRowCount(0);
            for (Patient p : dao.all()) {
                String dobStr = p.getDob() == null ? "" : p.getDob().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                model.addRow(new Object[]{p.getId(), p.getName(), dobStr, p.getAge(), p.getGender(), p.getContact(), p.getAddress(), p.getMedicalHistory()});
            }
        }

        // Add / Edit dialog; if existing != null -> edit mode
        private Patient showPatientDialog(Patient existing) {
            JTextField idF = new JTextField(12);
            JTextField nameF = new JTextField(20);
            JTextField dobF = new JTextField(10); // dd-MM-yyyy
            JTextField ageF = new JTextField(4);
            JTextField genderF = new JTextField(8);
            JTextField contactF = new JTextField(12);
            JTextField addressF = new JTextField(20);
            JTextArea historyA = new JTextArea(4, 20);

            if (existing != null) {
                idF.setText(existing.getId()); idF.setEditable(false);
                nameF.setText(existing.getName());
                if (existing.getDob() != null) dobF.setText(existing.getDob().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                ageF.setText(String.valueOf(existing.getAge()));
                genderF.setText(existing.getGender());
                contactF.setText(existing.getContact());
                addressF.setText(existing.getAddress());
                historyA.setText(existing.getMedicalHistory());
            }

            JPanel p = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4,4,4,4);
            gbc.anchor = GridBagConstraints.WEST;
            int y = 0;

            gbc.gridx=0; gbc.gridy=y; p.add(new JLabel("Patient ID:"), gbc); gbc.gridx=1; p.add(idF, gbc); y++;
            gbc.gridx=0; gbc.gridy=y; p.add(new JLabel("Name:"), gbc); gbc.gridx=1; p.add(nameF, gbc); y++;
            gbc.gridx=0; gbc.gridy=y; p.add(new JLabel("DOB (dd-MM-yyyy):"), gbc); gbc.gridx=1; p.add(dobF, gbc); y++;
            gbc.gridx=0; gbc.gridy=y; p.add(new JLabel("Age:"), gbc); gbc.gridx=1; p.add(ageF, gbc); y++;
            gbc.gridx=0; gbc.gridy=y; p.add(new JLabel("Gender:"), gbc); gbc.gridx=1; p.add(genderF, gbc); y++;
            gbc.gridx=0; gbc.gridy=y; p.add(new JLabel("Contact:"), gbc); gbc.gridx=1; p.add(contactF, gbc); y++;
            gbc.gridx=0; gbc.gridy=y; p.add(new JLabel("Address:"), gbc); gbc.gridx=1; p.add(addressF, gbc); y++;
            gbc.gridx=0; gbc.gridy=y; p.add(new JLabel("Medical History:"), gbc); gbc.gridx=1; p.add(new JScrollPane(historyA), gbc);

            int ok = JOptionPane.showConfirmDialog(this, p, existing == null ? "Add Patient" : "Edit Patient", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok == JOptionPane.OK_OPTION) {
                try {
                    String id = idF.getText().trim();
                    String name = nameF.getText().trim();
                    String dobStr = dobF.getText().trim();
                    LocalDate dob = null;
                    if (!dobStr.isEmpty()) {
                        try {
                            dob = LocalDate.parse(dobStr, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                        } catch (DateTimeParseException dt) {
                            throw new IllegalArgumentException("DOB must be in dd-MM-yyyy format (e.g., 12-05-1995).");
                        }
                    } else {
                        throw new IllegalArgumentException("DOB required");
                    }
                    int age = Integer.parseInt(ageF.getText().trim());
                    String gender = genderF.getText().trim();
                    String contact = contactF.getText().trim();
                    String address = addressF.getText().trim();
                    String history = historyA.getText().trim();

                    Patient pNew = new Patient(id, name, dob, contact, age, gender, address, history);
                    return pNew;
                } catch (NumberFormatException nfe) {
                    JOptionPane.showMessageDialog(this, "Age must be a number.");
                } catch (IllegalArgumentException iae) {
                    JOptionPane.showMessageDialog(this, "Validation error: " + iae.getMessage());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
            }
            return null;
        }
    }

    // ------------------ Main ------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new LoginFrame().setVisible(true);
        });
    }
}