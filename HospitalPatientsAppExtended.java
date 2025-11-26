// HospitalPatientsAppExtended.java
// Java 11+ single file Swing application
// Save, compile: javac HospitalPatientsAppExtended.java
// Run: java HospitalPatientsAppExtended

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extended Hospital Management System (single-file)
 *
 * Features:
 * - Patient registration / admit / discharge
 * - Appointment booking (CSV -> opens in Excel)
 * - Doctor & Staff management (specialization, duty timings, shifts)
 * - User authentication and role-based authorization (Admin/Doctor/Nurse/Reception)
 * - Reports: patient reports, lab reports (CSV)
 * - Data persistence: .ser + CSV/TXT files under data/ directory
 *
 * Build on the user's original code and expands functionality.
 */
public class HospitalPatientsAppExtended {

    // ------------------ FileManager (Abstraction for file IO) ------------------
    public static class FileManager {
        private static final String DATA_DIR = "data";
        private static final String PATIENT_SER = "patients.ser";
        private static final String PATIENT_TXT = "patients.txt";
        private static final String USERS_SER = "users.ser";
        private static final String USERS_TXT = "users.txt";
        private static final String DOCTORS_SER = "doctors.ser";
        private static final String DOCTORS_TXT = "doctors.txt";
        private static final String STAFF_SER = "staff.ser";
        private static final String STAFF_TXT = "staff.txt";
        private static final String APPTS_CSV = "appointments.csv";
        private static final String LAB_CSV = "lab_reports.csv";

        static {
            File d = new File(DATA_DIR);
            if (!d.exists()) d.mkdirs();
        }

        public static synchronized void saveObject(String filename, Object obj) {
            File f = new File(DATA_DIR, filename);
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
                oos.writeObject(obj);
            } catch (IOException ex) {
                showError("Error while saving " + filename + ": " + ex.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        public static synchronized <T> T loadObject(String filename, Class<T> clazz) {
            File f = new File(DATA_DIR, filename);
            if (!f.exists()) {
                try {
                    return clazz.getDeclaredConstructor().newInstance(); // for lists we expect ArrayList
                } catch (Exception e) {
                    return null;
                }
            }
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                Object obj = ois.readObject();
                return (T) obj;
            } catch (Exception ex) {
                showError("Error while loading " + filename + ": " + ex.getMessage());
                return null;
            }
        }

        public static synchronized void writePatientsTxt(List<Patient> list) {
            File f = new File(DATA_DIR, PATIENT_TXT);
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("id,name,dob,age,status,gender,contact,address,medicalHistory,admitDate");
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                for (Patient p : list) {
                    pw.printf("%s,%s,%s,%d,%s,%s,%s,%s,%s,%s%n",
                            escapeCsv(p.getId()),
                            escapeCsv(p.getName()),
                            p.getDob() == null ? "" : p.getDob().format(fmt),
                            p.getAge(),
                            p.getStatus(),
                            escapeCsv(p.getGender()),
                            escapeCsv(p.getContact()),
                            escapeCsv(p.getAddress()),
                            escapeCsv(p.getMedicalHistory()),
                            p.getAdmitDate() == null ? "" : p.getAdmitDate().format(fmt)
                    );
                }
            } catch (IOException ex) {
                showError("Error while saving patients.txt: " + ex.getMessage());
            }
        }

        public static synchronized void writeUsersTxt(List<User> users) {
            File f = new File(DATA_DIR, USERS_TXT);
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("username,role,passwordHash");
                for (User u : users) {
                    pw.printf("%s,%s,%s%n", escapeCsv(u.getUsername()), escapeCsv(u.getRole()), u.getPasswordHash());
                }
            } catch (IOException ex) {
                showError("Error while saving users.txt: " + ex.getMessage());
            }
        }

        public static synchronized void writeDoctorsTxt(List<Doctor> doctors) {
            File f = new File(DATA_DIR, DOCTORS_TXT);
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("id,name,specialization,dutyTimings,contact");
                for (Doctor d : doctors) {
                    pw.printf("%s,%s,%s,%s,%s%n", escapeCsv(d.getId()), escapeCsv(d.getName()), escapeCsv(d.getSpecialization()), escapeCsv(d.getDutyTimings()), escapeCsv(d.getContact()));
                }
            } catch (IOException ex) { showError("Error while saving doctors.txt: " + ex.getMessage()); }
        }

        public static synchronized void writeStaffTxt(List<Staff> staff) {
            File f = new File(DATA_DIR, STAFF_TXT);
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("id,name,role,shiftSchedule,contact");
                for (Staff s : staff) {
                    pw.printf("%s,%s,%s,%s,%s%n", escapeCsv(s.getId()), escapeCsv(s.getName()), escapeCsv(s.getRole()), escapeCsv(s.getShiftSchedule()), escapeCsv(s.getContact()));
                }
            } catch (IOException ex) { showError("Error while saving staff.txt: " + ex.getMessage()); }
        }

        public static synchronized void appendAppointmentCsv(Appointment appt) {
            File f = new File(DATA_DIR, APPTS_CSV);
            boolean writeHeader = !f.exists();
            try (PrintWriter pw = new PrintWriter(new FileWriter(f, true))) {
                if (writeHeader) pw.println("appointmentId,patientId,doctorId,datetime,reason,status");
                pw.printf("%s,%s,%s,%s,%s,%s%n", escapeCsv(appt.getId()), escapeCsv(appt.getPatientId()), escapeCsv(appt.getDoctorId()), appt.getDateTime().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")), escapeCsv(appt.getReason()), escapeCsv(appt.getStatus()));
            } catch (IOException ex) { showError("Error while writing appointments.csv: " + ex.getMessage()); }
        }

        public static synchronized void appendLabReport(LabReport lr) {
            File f = new File(DATA_DIR, LAB_CSV);
            boolean writeHeader = !f.exists();
            try (PrintWriter pw = new PrintWriter(new FileWriter(f, true))) {
                if (writeHeader) pw.println("reportId,patientId,doctorId,date,testName,result,notes");
                pw.printf("%s,%s,%s,%s,%s,%s,%s%n", escapeCsv(lr.getId()), escapeCsv(lr.getPatientId()), escapeCsv(lr.getDoctorId()), lr.getDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")), escapeCsv(lr.getTestName()), escapeCsv(lr.getResult()), escapeCsv(lr.getNotes()));
            } catch (IOException ex) { showError("Error while writing lab_reports.csv: " + ex.getMessage()); }
        }

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
        public void setDob(LocalDate dob) { this.dob = dob; }

        public String getContact() { return contact; }
        public void setContact(String contact) {
            if (contact == null || contact.trim().isEmpty()) throw new IllegalArgumentException("Contact required");
            this.contact = contact.trim();
        }

        public abstract String displayDetails();
    }

    public static class Patient extends Person implements Serializable {
        private int age;
        private String gender;
        private String address;
        private String medicalHistory;
        private Status status = Status.REGISTERED;
        private LocalDate admitDate;

        public enum Status { REGISTERED, ADMITTED, DISCHARGED }

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

        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }

        public LocalDate getAdmitDate() { return admitDate; }
        public void setAdmitDate(LocalDate admitDate) { this.admitDate = admitDate; }

        @Override
        public String displayDetails() {
            return String.format("Patient[id=%s,name=%s,age=%d,status=%s]", getId(), getName(), getAge(), status);
        }

        public boolean matchesById(String id) { return getId().equalsIgnoreCase(id); }
        public boolean matchesByName(String name, boolean exact) {
            if (exact) return getName().equalsIgnoreCase(name);
            return getName().toLowerCase().contains(name.toLowerCase());
        }
    }

    public static class Doctor extends Person implements Serializable {
        private String specialization;
        private String dutyTimings; // e.g., "Mon-Fri 09:00-15:00"

        public Doctor() { super(); }
        public Doctor(String id, String name, LocalDate dob, String contact, String specialization, String dutyTimings) {
            super(id, name, dob, contact);
            this.specialization = specialization;
            this.dutyTimings = dutyTimings;
        }
        public String getSpecialization() { return specialization; }
        public void setSpecialization(String s) { this.specialization = s; }
        public String getDutyTimings() { return dutyTimings; }
        public void setDutyTimings(String d) { this.dutyTimings = d; }

        @Override
        public String displayDetails() {
            return "Dr. " + getName() + " (" + specialization + ")";
        }
    }

    public static class Staff extends Person implements Serializable {
        private String role; // Nurse, WardBoy, etc.
        private String shiftSchedule; // e.g., "Morning: 7-3"

        public Staff() { super(); }
        public Staff(String id, String name, LocalDate dob, String contact, String role, String shift) {
            super(id, name, dob, contact);
            this.role = role;
            this.shiftSchedule = shift;
        }
        public String getRole() { return role; }
        public void setRole(String r) { this.role = r; }
        public String getShiftSchedule() { return shiftSchedule; }
        public void setShiftSchedule(String s) { this.shiftSchedule = s; }

        @Override
        public String displayDetails() {
            return getName() + " - " + role;
        }
    }

    // ------------------ Users & Auth ------------------

    public static class User implements Serializable {
        private String username;
        private String passwordHash; // SHA-256 hex
        private String role;

        public User() {}

        public User(String username, String passwordPlain, String role) {
            setUsername(username);
            setPasswordHash(hash(passwordPlain));
            setRole(role);
        }

        public String getUsername() { return username; }
        public void setUsername(String u) {
            if (u == null || u.trim().isEmpty()) throw new IllegalArgumentException("Username required");
            this.username = u.trim();
        }
        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String hash) { this.passwordHash = hash; }
        public void setPasswordPlain(String plain) { this.passwordHash = hash(plain); }

        public String getRole() { return role; }
        public void setRole(String r) { this.role = r; }

        public boolean verifyPassword(String plain) {
            return hash(plain).equals(this.passwordHash);
        }

        private static String hash(String plain) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] b = md.digest(plain.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte x : b) sb.append(String.format("%02x", x));
                return sb.toString();
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    // ------------------ Appointments and Reports ------------------

    public static class Appointment implements Serializable {
        private String id;
        private String patientId;
        private String doctorId;
        private LocalDateTime dateTime;
        private String reason;
        private String status = "SCHEDULED";

        public Appointment() {}
        public Appointment(String id, String patientId, String doctorId, LocalDateTime dt, String reason) {
            this.id = id; this.patientId = patientId; this.doctorId = doctorId; this.dateTime = dt; this.reason = reason;
        }
        public String getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getDoctorId() { return doctorId; }
        public LocalDateTime getDateTime() { return dateTime; }
        public String getReason() { return reason; }
        public String getStatus() { return status; }
        public void setStatus(String s) { status = s; }
    }

    public static class LabReport implements Serializable {
        private String id;
        private String patientId;
        private String doctorId;
        private LocalDate date;
        private String testName;
        private String result;
        private String notes;

        public LabReport() {}
        public LabReport(String id, String patientId, String doctorId, LocalDate date, String testName, String result, String notes) {
            this.id=id; this.patientId=patientId; this.doctorId=doctorId; this.date=date; this.testName=testName; this.result=result; this.notes=notes;
        }
        public String getId(){return id;}
        public String getPatientId(){return patientId;}
        public String getDoctorId(){return doctorId;}
        public LocalDate getDate(){return date;}
        public String getTestName(){return testName;}
        public String getResult(){return result;}
        public String getNotes(){return notes;}
    }

    // ------------------ DAOs ------------------

    public static class PatientDAO {
        private List<Patient> list;
        public PatientDAO() {
            List<Patient> loaded = FileManager.loadObject("patients.ser", ArrayList.class);
            list = loaded == null ? new ArrayList<>() : loaded;
        }
        public synchronized void add(Patient p){
            list.add(p);
            FileManager.saveObject("patients.ser", list);
            FileManager.writePatientsTxt(list);
        }
        public synchronized void update(Patient p){
            for (int i=0;i<list.size();i++){
                if (list.get(i).getId().equalsIgnoreCase(p.getId())){
                    list.set(i,p);
                    FileManager.saveObject("patients.ser", list);
                    FileManager.writePatientsTxt(list);
                    return;
                }
            }
        }
        public synchronized void delete(String id){
            list.removeIf(x->x.getId().equalsIgnoreCase(id));
            FileManager.saveObject("patients.ser", list);
            FileManager.writePatientsTxt(list);
        }
        public synchronized List<Patient> all(){ return new ArrayList<>(list); }
        public synchronized Optional<Patient> findById(String id){ return list.stream().filter(x->x.getId().equalsIgnoreCase(id)).findFirst();}
    }

    public static class UserDAO {
        private List<User> users;
        public UserDAO(){
            List<User> loaded = FileManager.loadObject("users.ser", ArrayList.class);
            users = loaded == null ? new ArrayList<>() : loaded;
            if (users.isEmpty()) createDefaultUsers();
        }
        private void createDefaultUsers(){
            users.add(new User("admin","admin123","Admin"));
            users.add(new User("doc","doc123","Doctor"));
            users.add(new User("nurse","nurse123","Nurse"));
            users.add(new User("recep","recep123","Receptionist"));
            persist();
        }
        public synchronized void add(User u){ users.add(u); persist(); }
        public synchronized List<User> all(){ return new ArrayList<>(users); }
        public synchronized Optional<User> findByUsername(String uname){ return users.stream().filter(u->u.getUsername().equalsIgnoreCase(uname)).findFirst(); }
        public synchronized void persist(){
            FileManager.saveObject("users.ser", users);
            FileManager.writeUsersTxt(users);
        }
    }

    public static class DoctorDAO {
        private List<Doctor> list;
        public DoctorDAO(){
            List<Doctor> loaded = FileManager.loadObject("doctors.ser", ArrayList.class);
            list = loaded==null? new ArrayList<>() : loaded;
        }
        public synchronized void add(Doctor d){ list.add(d); FileManager.saveObject("doctors.ser", list); FileManager.writeDoctorsTxt(list); }
        public synchronized List<Doctor> all(){ return new ArrayList<>(list); }
        public synchronized Optional<Doctor> findById(String id){ return list.stream().filter(x->x.getId().equalsIgnoreCase(id)).findFirst(); }
    }

    public static class StaffDAO {
        private List<Staff> list;
        public StaffDAO(){
            List<Staff> loaded = FileManager.loadObject("staff.ser", ArrayList.class);
            list = loaded==null? new ArrayList<>() : loaded;
        }
        public synchronized void add(Staff s){ list.add(s); FileManager.saveObject("staff.ser", list); FileManager.writeStaffTxt(list); }
        public synchronized List<Staff> all(){ return new ArrayList<>(list); }
    }

    public static class AppointmentDAO {
        private List<Appointment> list;
        public AppointmentDAO(){
            List<Appointment> loaded = FileManager.loadObject("appointments.ser", ArrayList.class);
            list = loaded==null? new ArrayList<>() : loaded;
        }
        public synchronized void add(Appointment a){
            list.add(a);
            FileManager.saveObject("appointments.ser", list);
            FileManager.appendAppointmentCsv(a);
        }
        public synchronized List<Appointment> all(){ return new ArrayList<>(list); }
    }

    public static class LabDAO {
        private List<LabReport> list;
        public LabDAO(){
            List<LabReport> loaded = FileManager.loadObject("lab_reports.ser", ArrayList.class);
            list = loaded==null? new ArrayList<>() : loaded;
        }
        public synchronized void add(LabReport lr){
            list.add(lr);
            FileManager.saveObject("lab_reports.ser", list);
            FileManager.appendLabReport(lr);
        }
        public synchronized List<LabReport> all(){ return new ArrayList<>(list); }
    }

    // ------------------ GUI ------------------

    public static class LoginFrame extends JFrame {
        public LoginFrame() {
            setTitle("HMS - Login");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(380, 220);
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

            UserDAO userDAO = new UserDAO();

            btnLogin.addActionListener(e -> {
                String u = txtUser.getText().trim();
                String pass = new String(txtPass.getPassword());
                Optional<User> opt = userDAO.findByUsername(u);
                if (opt.isPresent() && opt.get().verifyPassword(pass)) {
                    SwingUtilities.invokeLater(() -> {
                        DashboardFrame df = new DashboardFrame(opt.get(), userDAO);
                        df.setVisible(true);
                    });
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid credentials", "Login failed", JOptionPane.ERROR_MESSAGE);
                }
            });

            add(p);
        }
    }

    public static class DashboardFrame extends JFrame {
        private final User user;
        private final UserDAO userDAO;
        private final PatientDAO patientDAO = new PatientDAO();
        private final DoctorDAO doctorDAO = new DoctorDAO();
        private final StaffDAO staffDAO = new StaffDAO();
        private final AppointmentDAO appointmentDAO = new AppointmentDAO();
        private final LabDAO labDAO = new LabDAO();

        public DashboardFrame(User user, UserDAO userDAO) {
            this.user = user;
            this.userDAO = userDAO;
            setTitle("HMS Dashboard - " + user.getRole() + " (" + user.getUsername() + ")");
            setSize(1000, 650);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            initUI();
        }

        private void initUI() {
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnPatients = new JButton("Manage Patients");
            JButton btnDoctors = new JButton("Doctors");
            JButton btnStaff = new JButton("Staff");
            JButton btnAppts = new JButton("Appointments");
            JButton btnReports = new JButton("Reports");
            JButton btnUsers = new JButton("Users (Admin)");
            JButton btnExit = new JButton("Logout");

            top.add(btnPatients);
            top.add(btnDoctors);
            top.add(btnStaff);
            top.add(btnAppts);
            top.add(btnReports);
            if ("Admin".equalsIgnoreCase(user.getRole())) top.add(btnUsers);
            top.add(btnExit);
            add(top, BorderLayout.NORTH);

            JLabel welcome = new JLabel("Welcome, " + user.getUsername() + " (" + user.getRole() + ")");
            welcome.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            add(welcome, BorderLayout.CENTER);

            btnPatients.addActionListener(e -> {
                PatientManagementFrame pf = new PatientManagementFrame(patientDAO, user, doctorDAO, appointmentDAO, labDAO);
                pf.setVisible(true);
            });

            btnDoctors.addActionListener(e -> {
                DoctorManagementFrame df = new DoctorManagementFrame(doctorDAO, user);
                df.setVisible(true);
            });

            btnStaff.addActionListener(e -> {
                StaffManagementFrame sf = new StaffManagementFrame(staffDAO, user);
                sf.setVisible(true);
            });

            btnAppts.addActionListener(e -> {
                AppointmentFrame af = new AppointmentFrame(appointmentDAO, patientDAO, doctorDAO, user);
                af.setVisible(true);
            });

            btnReports.addActionListener(e -> {
                ReportsFrame rf = new ReportsFrame(patientDAO, labDAO, user);
                rf.setVisible(true);
            });

            btnUsers.addActionListener(e -> {
                if (!"Admin".equalsIgnoreCase(user.getRole())) { JOptionPane.showMessageDialog(this, "Only Admin can manage users"); return; }
                UsersAdminFrame uf = new UsersAdminFrame(userDAO);
                uf.setVisible(true);
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

    // ------------------ PatientManagementFrame ------------------

    public static class PatientManagementFrame extends JFrame {
        private final PatientDAO dao;
        private final User user;
        private final DoctorDAO doctorDAO;
        private final AppointmentDAO appointmentDAO;
        private final LabDAO labDAO;
        private final DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Name","DOB","Age","Status","Gender","Contact","Address","History","AdmitDate"}, 0);
        private final JTable table = new JTable(model);

        public PatientManagementFrame(PatientDAO dao, User user, DoctorDAO doctorDAO, AppointmentDAO apptDAO, LabDAO labDAO) {
            this.dao = dao;
            this.user = user;
            this.doctorDAO = doctorDAO;
            this.appointmentDAO = apptDAO;
            this.labDAO = labDAO;
            setTitle("Manage Patients - " + user.getRole());
            setSize(1100, 520);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(6,6));
            initUI();
            loadTable();
        }

        private void initUI() {
            add(new JScrollPane(table), BorderLayout.CENTER);

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnAdd = new JButton("Register Patient");
            JButton btnEdit = new JButton("Edit Selected");
            JButton btnAdmit = new JButton("Admit Selected");
            JButton btnDischarge = new JButton("Discharge Selected");
            JButton btnDelete = new JButton("Delete Selected");
            JButton btnRefresh = new JButton("Refresh");
            JButton btnLab = new JButton("Add Lab Report");
            JButton btnExport = new JButton("Export Patients (CSV)");

            top.add(btnAdd); top.add(btnEdit); top.add(btnAdmit); top.add(btnDischarge); top.add(btnLab); top.add(btnExport); top.add(btnDelete); top.add(btnRefresh);
            add(top, BorderLayout.NORTH);

            // Role-based enable/disable
            String r = user.getRole();
            btnDelete.setEnabled("Admin".equalsIgnoreCase(r));
            btnAdmit.setEnabled(!"Receptionist".equalsIgnoreCase(r)); // receptionists register/appointment only
            btnDischarge.setEnabled(!"Receptionist".equalsIgnoreCase(r));
            btnLab.setEnabled("Doctor".equalsIgnoreCase(r) || "Admin".equalsIgnoreCase(r));

            btnAdd.addActionListener(e -> {
                Patient p = showPatientDialog(null);
                if (p != null) { dao.add(p); loadTable(); JOptionPane.showMessageDialog(this, "Patient registered successfully."); }
            });

            btnEdit.addActionListener(e -> {
                int rsel = table.getSelectedRow();
                if (rsel == -1) { JOptionPane.showMessageDialog(this, "Select a row to edit."); return; }
                String id = (String) model.getValueAt(rsel, 0);
                Optional<Patient> opt = dao.findById(id);
                if (!opt.isPresent()) { JOptionPane.showMessageDialog(this, "Record not found."); return; }
                Patient updated = showPatientDialog(opt.get());
                if (updated != null) { dao.update(updated); loadTable(); JOptionPane.showMessageDialog(this, "Patient updated."); }
            });

            btnAdmit.addActionListener(e -> {
                int rsel = table.getSelectedRow();
                if (rsel == -1) { JOptionPane.showMessageDialog(this, "Select a row to admit."); return; }
                String id = (String) model.getValueAt(rsel, 0);
                Optional<Patient> opt = dao.findById(id);
                if (opt.isPresent()) {
                    Patient p = opt.get();
                    p.setStatus(Patient.Status.ADMITTED);
                    p.setAdmitDate(LocalDate.now());
                    dao.update(p);
                    loadTable();
                    JOptionPane.showMessageDialog(this, "Patient admitted.");
                }
            });

            btnDischarge.addActionListener(e -> {
                int rsel = table.getSelectedRow();
                if (rsel == -1) { JOptionPane.showMessageDialog(this, "Select a row to discharge."); return; }
                String id = (String) model.getValueAt(rsel, 0);
                Optional<Patient> opt = dao.findById(id);
                if (opt.isPresent()) {
                    Patient p = opt.get();
                    p.setStatus(Patient.Status.DISCHARGED);
                    dao.update(p);
                    loadTable();
                    JOptionPane.showMessageDialog(this, "Patient discharged.");
                }
            });

            btnDelete.addActionListener(e -> {
                int rsel = table.getSelectedRow();
                if (rsel == -1) { JOptionPane.showMessageDialog(this, "Select a row to delete."); return; }
                String id = (String) model.getValueAt(rsel, 0);
                int c = JOptionPane.showConfirmDialog(this, "Delete " + id + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (c == JOptionPane.YES_OPTION) {
                    dao.delete(id); loadTable();
                }
            });

            btnRefresh.addActionListener(e -> loadTable());

            btnLab.addActionListener(e -> {
                int rsel = table.getSelectedRow();
                if (rsel == -1) { JOptionPane.showMessageDialog(this, "Select a patient to add lab report."); return; }
                String id = (String) model.getValueAt(rsel, 0);
                Optional<Patient> opt = dao.findById(id);
                if (!opt.isPresent()) { JOptionPane.showMessageDialog(this, "Patient not found."); return; }
                LabReport lr = showLabDialog(opt.get());
                if (lr != null) {
                    labDAO.add(lr);
                    JOptionPane.showMessageDialog(this, "Lab report saved.");
                }
            });

            btnExport.addActionListener(e -> {
                List<Patient> all = dao.all();
                File out = new File("data/patients_export.csv");
                try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
                    pw.println("id,name,dob,age,status,gender,contact,address,medicalHistory,admitDate");
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                    for (Patient p : all) {
                        pw.printf("%s,%s,%s,%d,%s,%s,%s,%s,%s%n",
                                p.getId(), p.getName(), p.getDob()==null?"":p.getDob().format(fmt), p.getAge(), p.getStatus(), p.getGender(), p.getContact(), p.getAddress(), p.getMedicalHistory());
                    }
                    JOptionPane.showMessageDialog(this, "Exported to data/patients_export.csv");
                } catch (IOException ex) { JOptionPane.showMessageDialog(this, "Export error: " + ex.getMessage()); }
            });
        }

        private LabReport showLabDialog(Patient p) {
            JTextField testF = new JTextField(20);
            JTextField resultF = new JTextField(20);
            JTextArea notes = new JTextArea(4,20);
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4,4,4,4);
            gbc.anchor = GridBagConstraints.WEST;
            int y=0;
            gbc.gridx=0; gbc.gridy=y; panel.add(new JLabel("Patient: " + p.getName() + " ("+p.getId()+")"), gbc); y++;
            gbc.gridx=0; gbc.gridy=y; panel.add(new JLabel("Test Name:"), gbc); gbc.gridx=1; panel.add(testF, gbc); y++;
            gbc.gridx=0; gbc.gridy=y; panel.add(new JLabel("Result:"), gbc); gbc.gridx=1; panel.add(resultF, gbc); y++;
            gbc.gridx=0; gbc.gridy=y; panel.add(new JLabel("Notes:"), gbc); gbc.gridx=1; panel.add(new JScrollPane(notes), gbc);

            int ok = JOptionPane.showConfirmDialog(this, panel, "Add Lab Report", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                String id = "LR" + UUID.randomUUID().toString().substring(0,8);
                LabReport lr = new LabReport(id, p.getId(), "N/A", LocalDate.now(), testF.getText().trim(), resultF.getText().trim(), notes.getText().trim());
                return lr;
            }
            return null;
        }

        private void loadTable() {
            model.setRowCount(0);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            for (Patient p : dao.all()) {
                String dobStr = p.getDob() == null ? "" : p.getDob().format(fmt);
                String admit = p.getAdmitDate()==null ? "" : p.getAdmitDate().format(fmt);
                String contact = maskedContactForRole(p.getContact(), user.getRole());
                model.addRow(new Object[]{p.getId(), p.getName(), dobStr, p.getAge(), p.getStatus(), p.getGender(), contact, p.getAddress(), p.getMedicalHistory(), admit});
            }
        }

        private String maskedContactForRole(String contact, String role) {
            if ("Admin".equalsIgnoreCase(role) || "Receptionist".equalsIgnoreCase(role)) return contact;
            // mask middle digits for privacy
            if (contact == null || contact.length() < 6) return "*****";
            int len = contact.length();
            return contact.substring(0, 2) + "*****" + contact.substring(len-2);
        }

        // Add / Edit dialog
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
            } else {
                idF.setText("P" + UUID.randomUUID().toString().substring(0,5));
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

            int ok = JOptionPane.showConfirmDialog(this, p, existing == null ? "Register Patient" : "Edit Patient", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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

    // ------------------ DoctorManagementFrame ------------------

    public static class DoctorManagementFrame extends JFrame {
        private final DoctorDAO dao;
        private final User user;
        private final DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Name","Specialization","DutyTimings","Contact"}, 0);
        private final JTable table = new JTable(model);

        public DoctorManagementFrame(DoctorDAO dao, User user) {
            this.dao = dao; this.user = user;
            setTitle("Doctors - " + user.getRole());
            setSize(800,400); setLocationRelativeTo(null);
            initUI(); loadTable();
        }

        private void initUI() {
            add(new JScrollPane(table), BorderLayout.CENTER);
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnAdd = new JButton("Add Doctor");
            JButton btnRefresh = new JButton("Refresh");
            top.add(btnAdd); top.add(btnRefresh);
            add(top, BorderLayout.NORTH);

            btnAdd.setEnabled("Admin".equalsIgnoreCase(user.getRole()));
            btnAdd.addActionListener(e -> {
                JTextField idF = new JTextField("D" + UUID.randomUUID().toString().substring(0,5), 12);
                JTextField nameF = new JTextField(20);
                JTextField specF = new JTextField(20);
                JTextField dutyF = new JTextField(20);
                JTextField contactF = new JTextField(12);
                JPanel p = new JPanel(new GridLayout(0,2,4,4));
                p.add(new JLabel("ID:")); p.add(idF);
                p.add(new JLabel("Name:")); p.add(nameF);
                p.add(new JLabel("Specialization:")); p.add(specF);
                p.add(new JLabel("Duty Timings:")); p.add(dutyF);
                p.add(new JLabel("Contact:")); p.add(contactF);
                int ok = JOptionPane.showConfirmDialog(this, p, "Add Doctor", JOptionPane.OK_CANCEL_OPTION);
                if (ok == JOptionPane.OK_OPTION) {
                    try {
                        Doctor d = new Doctor(idF.getText().trim(), nameF.getText().trim(), LocalDate.now(), contactF.getText().trim(), specF.getText().trim(), dutyF.getText().trim());
                        dao.add(d);
                        loadTable();
                    } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
                }
            });

            btnRefresh.addActionListener(e -> loadTable());
        }

        private void loadTable() {
            model.setRowCount(0);
            for (Doctor d : dao.all()) {
                model.addRow(new Object[]{d.getId(), d.getName(), d.getSpecialization(), d.getDutyTimings(), d.getContact()});
            }
        }
    }

    // ------------------ StaffManagementFrame ------------------

    public static class StaffManagementFrame extends JFrame {
        private final StaffDAO dao;
        private final User user;
        private final DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Name","Role","Shift","Contact"}, 0);
        private final JTable table = new JTable(model);

        public StaffManagementFrame(StaffDAO dao, User user) {
            this.dao = dao; this.user = user;
            setTitle("Staff - " + user.getRole());
            setSize(800,400); setLocationRelativeTo(null);
            initUI(); loadTable();
        }

        private void initUI() {
            add(new JScrollPane(table), BorderLayout.CENTER);
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnAdd = new JButton("Add Staff");
            JButton btnRefresh = new JButton("Refresh");
            top.add(btnAdd); top.add(btnRefresh);
            add(top, BorderLayout.NORTH);

            btnAdd.setEnabled("Admin".equalsIgnoreCase(user.getRole()));
            btnAdd.addActionListener(e -> {
                JTextField idF = new JTextField("S" + UUID.randomUUID().toString().substring(0,5), 12);
                JTextField nameF = new JTextField(20);
                JTextField roleF = new JTextField(12);
                JTextField shiftF = new JTextField(12);
                JTextField contactF = new JTextField(12);
                JPanel p = new JPanel(new GridLayout(0,2,4,4));
                p.add(new JLabel("ID:")); p.add(idF);
                p.add(new JLabel("Name:")); p.add(nameF);
                p.add(new JLabel("Role:")); p.add(roleF);
                p.add(new JLabel("Shift Schedule:")); p.add(shiftF);
                p.add(new JLabel("Contact:")); p.add(contactF);
                int ok = JOptionPane.showConfirmDialog(this, p, "Add Staff", JOptionPane.OK_CANCEL_OPTION);
                if (ok == JOptionPane.OK_OPTION) {
                    try {
                        Staff s = new Staff(idF.getText().trim(), nameF.getText().trim(), LocalDate.now(), contactF.getText().trim(), roleF.getText().trim(), shiftF.getText().trim());
                        dao.add(s);
                        loadTable();
                    } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
                }
            });

            btnRefresh.addActionListener(e -> loadTable());
        }

        private void loadTable() {
            model.setRowCount(0);
            for (Staff s : dao.all()) {
                model.addRow(new Object[]{s.getId(), s.getName(), s.getRole(), s.getShiftSchedule(), s.getContact()});
            }
        }
    }

    // ------------------ Appointments UI ------------------

    public static class AppointmentFrame extends JFrame {
        private final AppointmentDAO dao;
        private final PatientDAO pdao;
        private final DoctorDAO ddao;
        private final User user;
        private final DefaultTableModel model = new DefaultTableModel(new String[]{"ID","PatientID","DoctorID","DateTime","Reason","Status"}, 0);
        private final JTable table = new JTable(model);

        public AppointmentFrame(AppointmentDAO dao, PatientDAO pdao, DoctorDAO ddao, User user) {
            this.dao=dao; this.pdao=pdao; this.ddao=ddao; this.user=user;
            setTitle("Appointments - " + user.getRole());
            setSize(900,420); setLocationRelativeTo(null);
            initUI(); loadTable();
        }

        private void initUI() {
            add(new JScrollPane(table), BorderLayout.CENTER);
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnAdd = new JButton("Book Appointment");
            JButton btnRefresh = new JButton("Refresh");
            top.add(btnAdd); top.add(btnRefresh);
            add(top, BorderLayout.NORTH);

            btnAdd.setEnabled(!"Doctor".equalsIgnoreCase(user.getRole())); // doctors don't book via this UI
            btnAdd.addActionListener(e -> {
                // choose patient and doctor
                List<Patient> patients = pdao.all();
                List<Doctor> doctors = ddao.all();
                if (patients.isEmpty()) { JOptionPane.showMessageDialog(this, "No patients available. Register patient first."); return; }
                if (doctors.isEmpty()) { JOptionPane.showMessageDialog(this, "No doctors available. Add doctors first."); return; }

                String[] pitems = patients.stream().map(x->x.getId()+" - "+x.getName()).toArray(String[]::new);
                String[] ditem = doctors.stream().map(x->x.getId()+" - "+x.getName() + " ("+x.getSpecialization()+")").toArray(String[]::new);
                JComboBox<String> pc = new JComboBox<>(pitems);
                JComboBox<String> dc = new JComboBox<>(ditem);
                JTextField dateF = new JTextField("dd-MM-yyyy HH:mm", 16);
                JTextField reasonF = new JTextField(30);
                JPanel panel = new JPanel(new GridLayout(0,2,4,4));
                panel.add(new JLabel("Patient:")); panel.add(pc);
                panel.add(new JLabel("Doctor:")); panel.add(dc);
                panel.add(new JLabel("Date & Time (dd-MM-yyyy HH:mm):")); panel.add(dateF);
                panel.add(new JLabel("Reason:")); panel.add(reasonF);

                int ok = JOptionPane.showConfirmDialog(this, panel, "Book Appointment", JOptionPane.OK_CANCEL_OPTION);
                if (ok == JOptionPane.OK_OPTION) {
                    try {
                        String pid = ((String)pc.getSelectedItem()).split(" - ")[0];
                        String did = ((String)dc.getSelectedItem()).split(" - ")[0];
                        LocalDateTime dt = LocalDateTime.parse(dateF.getText().trim(), DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
                        String reason = reasonF.getText().trim();
                        Appointment ap = new Appointment("A"+UUID.randomUUID().toString().substring(0,6), pid, did, dt, reason);
                        dao.add(ap);
                        loadTable();
                        JOptionPane.showMessageDialog(this, "Appointment scheduled.");
                    } catch (DateTimeParseException dt) { JOptionPane.showMessageDialog(this, "Invalid date/time format."); }
                }
            });

            btnRefresh.addActionListener(e -> loadTable());
        }

        private void loadTable() {
            model.setRowCount(0);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            for (Appointment a : dao.all()) {
                model.addRow(new Object[]{a.getId(), a.getPatientId(), a.getDoctorId(), a.getDateTime().format(fmt), a.getReason(), a.getStatus()});
            }
        }
    }

    // ------------------ Reports UI ------------------

    public static class ReportsFrame extends JFrame {
        private final PatientDAO pdao;
        private final LabDAO ldao;
        private final User user;

        public ReportsFrame(PatientDAO pdao, LabDAO ldao, User user) {
            this.pdao = pdao; this.ldao = ldao; this.user = user;
            setTitle("Reports - " + user.getRole());
            setSize(700,400); setLocationRelativeTo(null);
            initUI();
        }

        private void initUI() {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnPatientReport = new JButton("Export Patient Report (CSV)");
            JButton btnLabReport = new JButton("View Lab Reports (CSV)");
            p.add(btnPatientReport); p.add(btnLabReport);
            add(p, BorderLayout.NORTH);

            btnPatientReport.addActionListener(e -> {
                try {
                    List<Patient> all = pdao.all();
                    File out = new File("data/patient_report_" + System.currentTimeMillis() + ".csv");
                    try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
                        pw.println("id,name,dob,age,status,gender,contact,address,medicalHistory,admitDate");
                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                        for (Patient pt : all) {
                            pw.printf("%s,%s,%s,%d,%s,%s,%s,%s,%s%n",
                                    pt.getId(), pt.getName(), pt.getDob()==null?"":pt.getDob().format(fmt), pt.getAge(), pt.getStatus(), pt.getGender(), pt.getContact(), pt.getAddress(), pt.getMedicalHistory());
                        }
                    }
                    JOptionPane.showMessageDialog(this, "Patient report exported to " + out.getPath());
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Export error: " + ex.getMessage()); }
            });

            btnLabReport.addActionListener(e -> {
                File f = new File("data/lab_reports.csv");
                if (!f.exists()) { JOptionPane.showMessageDialog(this, "No lab reports found."); return; }
                JOptionPane.showMessageDialog(this, "Lab reports are saved at: " + f.getPath() + "\nOpen with Excel.");
            });
        }
    }

    // ------------------ Users Admin UI ------------------

    public static class UsersAdminFrame extends JFrame {
        private final UserDAO dao;
        private final DefaultTableModel model = new DefaultTableModel(new String[]{"Username","Role"}, 0);
        private final JTable table = new JTable(model);

        public UsersAdminFrame(UserDAO dao) {
            this.dao = dao;
            setTitle("User Administration");
            setSize(600,400); setLocationRelativeTo(null);
            initUI(); loadTable();
        }

        private void initUI() {
            add(new JScrollPane(table), BorderLayout.CENTER);
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnAdd = new JButton("Add User");
            JButton btnRefresh = new JButton("Refresh");
            top.add(btnAdd); top.add(btnRefresh);
            add(top, BorderLayout.NORTH);

            btnAdd.addActionListener(e -> {
                JTextField userF = new JTextField(12);
                JPasswordField passF = new JPasswordField(12);
                JComboBox<String> roleC = new JComboBox<>(new String[]{"Admin","Doctor","Nurse","Receptionist"});
                JPanel p = new JPanel(new GridLayout(0,2,4,4));
                p.add(new JLabel("Username:")); p.add(userF);
                p.add(new JLabel("Password:")); p.add(passF);
                p.add(new JLabel("Role:")); p.add(roleC);
                int ok = JOptionPane.showConfirmDialog(this, p, "Add User", JOptionPane.OK_CANCEL_OPTION);
                if (ok == JOptionPane.OK_OPTION) {
                    try {
                        User newU = new User(userF.getText().trim(), new String(passF.getPassword()), roleC.getSelectedItem().toString());
                        dao.add(newU);
                        loadTable();
                    } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
                }
            });

            btnRefresh.addActionListener(e -> loadTable());
        }

        private void loadTable() {
            model.setRowCount(0);
            for (User u : dao.all()) model.addRow(new Object[]{u.getUsername(), u.getRole()});
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
