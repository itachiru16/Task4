import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HotelBookingSystem.java
 *
 * Simple console-based hotel booking system with:
 * - Room categories (Standard, Deluxe, Suite)
 * - Search availability by date range and category
 * - Make and cancel reservations
 * - Payment simulation
 * - File I/O persistence (rooms.csv and reservations.csv)
 *
 * Compile: javac HotelBookingSystem.java
 * Run:     java HotelBookingSystem
 */
public class HotelBookingSystem {

    // ---------- Models ----------
    enum Category { STANDARD, DELUXE, SUITE }

    static class Room {
        String roomId;      // unique id like R101
        Category category;
        double ratePerNight;

        public Room(String roomId, Category category, double ratePerNight) {
            this.roomId = roomId;
            this.category = category;
            this.ratePerNight = ratePerNight;
        }

        public String toCsv() {
            return roomId + "," + category + "," + ratePerNight;
        }

        static Room fromCsv(String csv) {
            String[] p = csv.split(",", -1);
            return new Room(p[0], Category.valueOf(p[1]), Double.parseDouble(p[2]));
        }
    }

    static class Reservation {
        String bookingId;         // unique id: B20250001
        String customerName;
        String roomId;
        LocalDate checkIn;
        LocalDate checkOut;      // exclusive (i.e., checkout date)
        double totalAmount;
        String paymentStatus;    // "PAID" or "PENDING" or "FAILED"

        Reservation(String bookingId, String customerName, String roomId,
                    LocalDate checkIn, LocalDate checkOut, double totalAmount, String paymentStatus) {
            this.bookingId = bookingId;
            this.customerName = customerName;
            this.roomId = roomId;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
            this.totalAmount = totalAmount;
            this.paymentStatus = paymentStatus;
        }

        public String toCsv() {
            DateTimeFormatter f = DateTimeFormatter.ISO_LOCAL_DATE;
            return String.join(",", bookingId, customerName.replace(",", " "), roomId,
                    checkIn.format(f), checkOut.format(f), String.format("%.2f", totalAmount), paymentStatus);
        }

        static Reservation fromCsv(String csv) {
            String[] p = csv.split(",", -1);
            DateTimeFormatter f = DateTimeFormatter.ISO_LOCAL_DATE;
            return new Reservation(p[0], p[1], p[2], LocalDate.parse(p[3], f), LocalDate.parse(p[4], f),
                    Double.parseDouble(p[5]), p[6]);
        }
    }

    // ---------- Hotel core ----------
    static class Hotel {
        Map<String, Room> rooms = new LinkedHashMap<>();            // roomId -> Room
        List<Reservation> reservations = new ArrayList<>();
        int bookingCounter = 1;

        // load rooms from file or seed defaults
        void loadRooms(String roomsFile) {
            File f = new File(roomsFile);
            if (!f.exists()) {
                seedRooms();
                saveRooms(roomsFile);
                System.out.println("Rooms seeded and saved to " + roomsFile);
                return;
            }
            try (Scanner sc = new Scanner(f)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty()) continue;
                    Room r = Room.fromCsv(line);
                    rooms.put(r.roomId, r);
                }
            } catch (Exception e) {
                System.out.println("Failed to load rooms: " + e.getMessage());
            }
        }

        void seedRooms() {
            rooms.clear();
            // 10 sample rooms
            rooms.put("R101", new Room("R101", Category.STANDARD, 3000));
            rooms.put("R102", new Room("R102", Category.STANDARD, 3000));
            rooms.put("R103", new Room("R103", Category.STANDARD, 3200));
            rooms.put("R201", new Room("R201", Category.DELUXE, 5000));
            rooms.put("R202", new Room("R202", Category.DELUXE, 5200));
            rooms.put("R301", new Room("R301", Category.SUITE, 9000));
            rooms.put("R302", new Room("R302", Category.SUITE, 9500));
            rooms.put("R303", new Room("R303", Category.SUITE, 10000));
            rooms.put("R104", new Room("R104", Category.STANDARD, 3100));
            rooms.put("R203", new Room("R203", Category.DELUXE, 5300));
        }

        void saveRooms(String roomsFile) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(roomsFile))) {
                for (Room r : rooms.values()) pw.println(r.toCsv());
            } catch (IOException e) {
                System.out.println("Could not save rooms: " + e.getMessage());
            }
        }

        void loadReservations(String resFile) {
            File f = new File(resFile);
            if (!f.exists()) return;
            try (Scanner sc = new Scanner(f)) {
                while (sc.hasNextLine()) {
                    String l = sc.nextLine().trim();
                    if (l.isEmpty()) continue;
                    Reservation r = Reservation.fromCsv(l);
                    reservations.add(r);
                    // adjust booking counter to avoid duplicates
                    try {
                        String numPart = r.bookingId.replaceAll("[^0-9]", "");
                        if (!numPart.isEmpty()) {
                            int val = Integer.parseInt(numPart);
                            bookingCounter = Math.max(bookingCounter, val + 1);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                System.out.println("Failed to load reservations: " + e.getMessage());
            }
        }

        void saveReservations(String resFile) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(resFile))) {
                for (Reservation r : reservations) pw.println(r.toCsv());
            } catch (IOException e) {
                System.out.println("Could not save reservations: " + e.getMessage());
            }
        }

        // Search available rooms for date range and optional category filter
        List<Room> searchAvailable(LocalDate checkIn, LocalDate checkOut, Category categoryFilter) {
            List<Room> available = new ArrayList<>();
            for (Room room : rooms.values()) {
                if (categoryFilter != null && room.category != categoryFilter) continue;
                if (isRoomAvailable(room.roomId, checkIn, checkOut)) {
                    available.add(room);
                }
            }
            return available;
        }

        // Check single room availability
        boolean isRoomAvailable(String roomId, LocalDate checkIn, LocalDate checkOut) {
            for (Reservation r : reservations) {
                if (!r.roomId.equals(roomId)) continue;
                // overlap check: (startA < endB) && (startB < endA) means overlap
                if ( (r.checkIn.isBefore(checkOut)) && (checkIn.isBefore(r.checkOut)) ) {
                    return false;
                }
            }
            return true;
        }

        // Create booking id
        String nextBookingId() {
            String id = String.format("B%08d", bookingCounter++);
            return id;
        }

        // Book a room (returns Reservation) after payment
        Reservation bookRoom(String customerName, String roomId, LocalDate checkIn, LocalDate checkOut, PaymentSimulator payment) {
            if (!rooms.containsKey(roomId)) throw new IllegalArgumentException("Room not found");
            if (!isRoomAvailable(roomId, checkIn, checkOut)) throw new IllegalArgumentException("Room not available");

            Room room = rooms.get(roomId);
            long nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
            if (nights <= 0) throw new IllegalArgumentException("Invalid dates (check-out must be after check-in)");
            double total = nights * room.ratePerNight;

            // Simulate payment
            boolean paid = payment.processPayment(customerName, total);
            String paymentStatus = paid ? "PAID" : "FAILED";

            String bookingId = nextBookingId();
            Reservation res = new Reservation(bookingId, customerName, roomId, checkIn, checkOut, total, paymentStatus);
            if (paid) {
                reservations.add(res);
                System.out.println("Booking confirmed. Booking ID: " + bookingId);
            } else {
                System.out.println("Payment failed. Booking not completed.");
            }
            return res;
        }

        Reservation findReservationById(String bookingId) {
            for (Reservation r : reservations) if (r.bookingId.equalsIgnoreCase(bookingId)) return r;
            return null;
        }

        boolean cancelReservation(String bookingId) {
            Reservation r = findReservationById(bookingId);
            if (r == null) return false;
            reservations.remove(r);
            return true;
        }
    }

    // ---------- Payment simulator ----------
    static class PaymentSimulator {
        Random rnd = new Random();

        // Returns true if payment succeeded (simulate)
        boolean processPayment(String customerName, double amount) {
            System.out.printf("Processing payment for %s: Rs %.2f ...\n", customerName, amount);
            // Simulate user confirmation
            Scanner sc = new Scanner(System.in);
            System.out.print("Simulate payment? (y to succeed / n to fail): ");
            String in = sc.nextLine().trim().toLowerCase();
            if (in.equals("y")) {
                System.out.println("Payment succeeded.");
                return true;
            } else {
                System.out.println("Payment failed or cancelled.");
                return false;
            }
        }
    }

    // ---------- Console UI ----------
    static Hotel hotel = new Hotel();
    static PaymentSimulator payment = new PaymentSimulator();
    static Scanner scanner = new Scanner(System.in);
    static final String ROOMS_FILE = "rooms.csv";
    static final String RES_FILE = "reservations.csv";

    public static void main(String[] args) {
        System.out.println("=== Welcome to Simple Hotel Booking System ===");
        hotel.loadRooms(ROOMS_FILE);
        hotel.loadReservations(RES_FILE);

        boolean exit = false;
        while (!exit) {
            printMenu();
            String choice = scanner.nextLine().trim();
            try {
                switch (choice) {
                    case "1": doSearch(); break;
                    case "2": doBook(); break;
                    case "3": doCancel(); break;
                    case "4": viewReservation(); break;
                    case "5": listReservations(); break;
                    case "6": listRooms(); break;
                    case "7": saveAll(); break;
                    case "0": exit = true; break;
                    default:
                        System.out.println("Unknown option.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        // Save before exit
        saveAll();
        System.out.println("Goodbye!");
    }

    static void printMenu() {
        System.out.println("\nMenu:");
        System.out.println("1) Search available rooms");
        System.out.println("2) Make a booking");
        System.out.println("3) Cancel a booking");
        System.out.println("4) View booking details");
        System.out.println("5) List all reservations");
        System.out.println("6) List all rooms");
        System.out.println("7) Save data to files");
        System.out.println("0) Exit");
        System.out.print("Choose: ");
    }

    static void doSearch() {
        LocalDate[] dates = askDates();
        if (dates == null) return;
        Category cat = askCategoryOptional();
        List<Room> available = hotel.searchAvailable(dates[0], dates[1], cat);
        if (available.isEmpty()) {
            System.out.println("No rooms available for the selected dates/category.");
        } else {
            System.out.println("Available rooms:");
            for (Room r : available) {
                System.out.printf("%s | %s | Rs %.2f per night\n", r.roomId, r.category, r.ratePerNight);
            }
        }
    }

    static void doBook() {
        System.out.print("Your name: ");
        String name = scanner.nextLine().trim();
        LocalDate[] dates = askDates();
        if (dates == null) return;
        Category cat = askCategoryOptional();
        List<Room> available = hotel.searchAvailable(dates[0], dates[1], cat);

        if (available.isEmpty()) {
            System.out.println("No rooms available to book.");
            return;
        }

        System.out.println("Choose room by number or enter room id:");
        for (int i = 0; i < available.size(); i++) {
            Room r = available.get(i);
            System.out.printf("%d) %s | %s | Rs %.2f\n", i + 1, r.roomId, r.category, r.ratePerNight);
        }
        System.out.print("Choice: ");
        String choice = scanner.nextLine().trim();

        String roomId = null;
        try {
            int idx = Integer.parseInt(choice) - 1;
            if (idx >= 0 && idx < available.size()) roomId = available.get(idx).roomId;
        } catch (NumberFormatException ignored) {
            // treat as id
            roomId = choice.toUpperCase();
        }
        if (roomId == null || !hotel.rooms.containsKey(roomId)) {
            System.out.println("Invalid room choice.");
            return;
        }

        // Calculate total
        Room r = hotel.rooms.get(roomId);
        long nights = java.time.temporal.ChronoUnit.DAYS.between(dates[0], dates[1]);
        double total = nights * r.ratePerNight;
        System.out.printf("Booking %s (%s) from %s to %s for %d nights. Total: Rs %.2f\n",
                r.roomId, r.category, dates[0], dates[1], nights, total);

        // Payment & booking
        Reservation res = hotel.bookRoom(name, roomId, dates[0], dates[1], payment);
        if (res != null && res.paymentStatus.equals("PAID")) {
            hotel.saveReservations(RES_FILE); // save after successful booking
            System.out.println("Booking saved.");
        }
    }

    static void doCancel() {
        System.out.print("Enter booking id to cancel: ");
        String id = scanner.nextLine().trim();
        boolean ok = hotel.cancelReservation(id);
        if (ok) {
            System.out.println("Booking cancelled.");
            hotel.saveReservations(RES_FILE);
        } else {
            System.out.println("Booking id not found.");
        }
    }

    static void viewReservation() {
        System.out.print("Enter booking id: ");
        String id = scanner.nextLine().trim();
        Reservation r = hotel.findReservationById(id);
        if (r == null) {
            System.out.println("Not found.");
            return;
        }
        System.out.println("Booking details:");
        System.out.println("Booking ID: " + r.bookingId);
        System.out.println("Customer: " + r.customerName);
        System.out.println("Room: " + r.roomId);
        System.out.println("Check-in: " + r.checkIn);
        System.out.println("Check-out: " + r.checkOut);
        System.out.println("Total: Rs " + r.totalAmount);
        System.out.println("Payment status: " + r.paymentStatus);
    }

    static void listReservations() {
        if (hotel.reservations.isEmpty()) {
            System.out.println("(no reservations)");
            return;
        }
        System.out.println("All reservations:");
        for (Reservation r : hotel.reservations) {
            System.out.printf("%s | %s | %s to %s | Rs %.2f | %s\n",
                    r.bookingId, r.customerName, r.checkIn, r.checkOut, r.totalAmount, r.paymentStatus);
        }
    }

    static void listRooms() {
        System.out.println("Rooms:");
        for (Room r : hotel.rooms.values()) {
            System.out.printf("%s | %s | Rs %.2f per night\n", r.roomId, r.category, r.ratePerNight);
        }
    }

    static void saveAll() {
        hotel.saveRooms(ROOMS_FILE);
        hotel.saveReservations(RES_FILE);
        System.out.println("Data saved to files.");
    }

    // Helper: read check-in and check-out dates
    static LocalDate[] askDates() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            System.out.print("Enter check-in date (yyyy-mm-dd): ");
            String in1 = scanner.nextLine().trim();
            LocalDate checkIn = LocalDate.parse(in1);
            System.out.print("Enter check-out date (yyyy-mm-dd) (checkout date is exclusive): ");
            String in2 = scanner.nextLine().trim();
            LocalDate checkOut = LocalDate.parse(in2);
            if (!checkIn.isBefore(checkOut)) {
                System.out.println("Check-out must be after check-in.");
                return null;
            }
            return new LocalDate[]{checkIn, checkOut};
        } catch (Exception e) {
            System.out.println("Invalid date format. Use yyyy-mm-dd.");
            return null;
        }
    }

    // Ask optional category filter
    static Category askCategoryOptional() {
        System.out.print("Filter by category? (1 Standard, 2 Deluxe, 3 Suite, Enter for all): ");
        String c = scanner.nextLine().trim();
        if (c.isEmpty()) return null;
        switch (c) {
            case "1": return Category.STANDARD;
            case "2": return Category.DELUXE;
            case "3": return Category.SUITE;
            default:
                System.out.println("Invalid category input. Showing all.");
                return null;
        }
    }
}
