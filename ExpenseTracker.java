import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Expense Tracker (CLI) — single-file, no external libs. Java 11+
 *
 * Features
 *  - Add / list / edit / delete expenses
 *  - Fields: date, amount, category, description, payment method, note
 *  - Filter by month/date range/category/text
 *  - Monthly report (total + by category)
 *  - CSV export/import
 *  - Persistent storage in TSV (expenses.tsv)
 *
 * Compile: javac ExpenseTracker.java
 * Run:     java ExpenseTracker
 */
public class ExpenseTracker {
    // ===== Config =====
    private static final String STORAGE_FILE = "expenses.tsv";
    private static final String CSV_EXPORT_DEFAULT = "expenses_export.csv";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter YM_FMT   = DateTimeFormatter.ofPattern("yyyy-MM");

    // ===== Main =====
    public static void main(String[] args) {
        Store store = new Store(STORAGE_FILE);
        store.load();
        new ExpenseTracker().run(store);
    }

    private final Scanner in = new Scanner(System.in);

    private void run(Store store) {
        println("\n===== Expense Tracker (CLI) =====");
        println("Storage: " + STORAGE_FILE);
        help();
        while (true) {
            System.out.print("\n> ");
            if (!in.hasNextLine()) break;
            String line = in.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+", 2);
            String op = parts[0].toLowerCase(Locale.ROOT);
            String rest = parts.length > 1 ? parts[1] : "";

            try {
                switch (op) {
                    case "help": case "?": help(); break;
                    case "add": add(store, rest); break;
                    case "list": case "ls": list(store, rest); break;
                    case "edit": edit(store, rest); break;
                    case "del": case "rm": delete(store, rest); break;
                    case "search": search(store, rest); break;
                    case "report": report(store, rest); break;
                    case "export": exportCsv(store, rest); break;
                    case "import": importCsv(store, rest); break;
                    case "save": store.save(); println("Saved."); break;
                    case "quit": case "exit": store.save(); println("Saved. Bye!"); return;
                    default: println("Unknown command. Type 'help' for commands.");
                }
            } catch (Exception e) {
                println("Error: " + e.getMessage());
            }
        }
        store.save();
        println("\nSaved. Bye!");
    }

    // ===== Commands =====

    private void help() {
        println("\nCommands:");
        println("  add <amount> <description> [/date yyyy-mm-dd] [/cat category] [/pay method] [/note text]");
        println("  list [all | month yyyy-mm | today | range yyyy-mm-dd..yyyy-mm-dd] [/cat name] [/sort date|amt|cat] [/rev]");
        println("  edit <id> [/amt number] [/t new description] [/date yyyy-mm-dd] [/cat category] [/pay method] [/note text]");
        println("  del <id>");
        println("  search <text>");
        println("  report month <yyyy-mm>   — totals & by-category breakdown");
        println("  export [filename.csv]    — default: " + CSV_EXPORT_DEFAULT);
        println("  import <filename.csv>");
        println("  save | exit");
        println("\nExamples:");
        println("  add 249.99 Headphones /cat electronics /pay upi /date 2025-08-27");
        println("  list month 2025-08 /cat food");
        println("  report month 2025-08");
    }

    private void add(Store store, String args) {
        if (args.isBlank()) throw new IllegalArgumentException("Usage: add <amount> <description> [flags]");
        String[] head = args.trim().split("\\s+", 2);
        BigDecimal amount = parseAmount(head[0]);
        String rest = head.length > 1 ? head[1] : "";
        Map<String,String> flags = parseFlags(rest);
        String description = leadingText(rest);
        if (description.isBlank()) description = "(no description)";

        LocalDate date = parseDate(flags.get("/date"));
        if (date == null) date = LocalDate.now();
        String category = orNull(flags.get("/cat"));
        String pay = orNull(flags.get("/pay"));
        String note = orNull(flags.get("/note"));

        Expense e = store.add(date, amount, category, description.trim(), pay, note);
        store.save();
        println("Added #" + e.id + ": " + DATE_FMT.format(e.date) + "  " + money(e.amount) + "  " + e.description);
    }

    private void list(Store store, String args) {
        Map<String,String> flags = parseFlags(args);
        String scope = leadingText(args).toLowerCase(Locale.ROOT).trim();
        String sort = flags.getOrDefault("/sort", "date").toLowerCase(Locale.ROOT);
        boolean rev = flags.containsKey("/rev");
        String catFilter = flags.get("/cat");

        List<Expense> items = new ArrayList<>(store.all());

        if (!scope.isEmpty()) {
            String[] sc = scope.split("\\s+");
            switch (sc[0]) {
                case "all": break;
                case "today":
                    LocalDate today = LocalDate.now();
                    items.removeIf(e -> !e.date.equals(today)); break;
                case "month":
                    if (sc.length < 2) throw new IllegalArgumentException("Usage: list month yyyy-mm");
                    YearMonth ym = YearMonth.parse(sc[1], YM_FMT);
                    items.removeIf(e -> !(YearMonth.from(e.date).equals(ym))); break;
                case "range":
                    String[] rr = scope.replaceFirst("range\\s+","").split("\\.\\.");
                    if (rr.length != 2) throw new IllegalArgumentException("Usage: list range yyyy-mm-dd..yyyy-mm-dd");
                    LocalDate from = LocalDate.parse(rr[0], DATE_FMT);
                    LocalDate to   = LocalDate.parse(rr[1], DATE_FMT);
                    items.removeIf(e -> e.date.isBefore(from) || e.date.isAfter(to)); break;
                default:
                    // If user typed something else, ignore scope and show all
            }
        }

        if (catFilter != null && !catFilter.isBlank()) {
            String cf = catFilter.toLowerCase(Locale.ROOT).trim();
            items.removeIf(e -> e.category == null || !e.category.toLowerCase(Locale.ROOT).contains(cf));
        }

        Comparator<Expense> cmp;
        switch (sort) {
            case "amt": cmp = Comparator.comparing((Expense e) -> e.amount); break;
            case "cat": cmp = Comparator.comparing((Expense e) -> e.category == null ? "" : e.category, String.CASE_INSENSITIVE_ORDER); break;
            case "date":
            default: cmp = Comparator.comparing((Expense e) -> e.date).thenComparingInt(e -> e.id);
        }
        items.sort(cmp);
        if (rev) Collections.reverse(items);
        renderTable(items);
        BigDecimal total = items.stream().map(e -> e.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        println("Total: " + money(total));
    }

    private void edit(Store store, String args) {
        String[] p = args.trim().split("\\s+", 2);
        if (p.length == 0 || p[0].isBlank()) throw new IllegalArgumentException("Usage: edit <id> [flags]");
        int id = parseId(p[0]);
        Expense e = store.get(id);
        if (e == null) throw new IllegalArgumentException("No expense with id " + id);

        Map<String,String> flags = parseFlags(p.length > 1 ? p[1] : "");
        if (flags.containsKey("/amt")) e.amount = parseAmount(flags.get("/amt"));
        if (flags.containsKey("/t"))   e.description = flags.get("/t").trim();
        if (flags.containsKey("/date")) e.date = parseDate(flags.get("/date"));
        if (flags.containsKey("/cat"))  e.category = orNull(flags.get("/cat"));
        if (flags.containsKey("/pay"))  e.payment = orNull(flags.get("/pay"));
        if (flags.containsKey("/note")) e.note = orNull(flags.get("/note"));
        store.save();
        println("Edited #" + id + ".");
    }

    private void delete(Store store, String arg) {
        int id = parseId(arg);
        if (store.delete(id)) { store.save(); println("Deleted #" + id + "."); }
        else throw new IllegalArgumentException("No expense with id " + id);
    }

    private void search(Store store, String text) {
        String q = text.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) { println("Usage: search <text>"); return; }
        List<Expense> hits = new ArrayList<>();
        for (Expense e : store.all()) {
            if ((e.description != null && e.description.toLowerCase(Locale.ROOT).contains(q)) ||
                (e.category != null && e.category.toLowerCase(Locale.ROOT).contains(q)) ||
                (e.payment != null && e.payment.toLowerCase(Locale.ROOT).contains(q)) ||
                (e.note != null && e.note.toLowerCase(Locale.ROOT).contains(q))) {
                hits.add(e);
            }
        }
        renderTable(hits);
        BigDecimal total = hits.stream().map(x -> x.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        println("Total in results: " + money(total));
    }

    private void report(Store store, String args) {
        String[] p = args.trim().split("\\s+");
        if (p.length != 2 || !p[0].equalsIgnoreCase("month"))
            throw new IllegalArgumentException("Usage: report month yyyy-mm");
        YearMonth ym = YearMonth.parse(p[1], YM_FMT);

        List<Expense> items = new ArrayList<>();
        for (Expense e : store.all()) {
            if (YearMonth.from(e.date).equals(ym)) items.add(e);
        }
        if (items.isEmpty()) { println("(no expenses for " + ym + ")"); return; }

        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byCat = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Expense e : items) {
            total = total.add(e.amount);
            String cat = e.category == null || e.category.isBlank() ? "(uncategorized)" : e.category;
            byCat.put(cat, byCat.getOrDefault(cat, BigDecimal.ZERO).add(e.amount));
        }

        println("\nReport — " + ym);
        println("-".repeat(36));
        println("Total: " + money(total));
        println("By Category:");
        for (Map.Entry<String,BigDecimal> en : byCat.entrySet()) {
            BigDecimal v = en.getValue();
            BigDecimal pct = total.compareTo(BigDecimal.ZERO)==0 ? BigDecimal.ZERO :
                    v.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
            System.out.printf("  %-18s %12s  (%s%%)%n", en.getKey(), money(v), pct.toPlainString());
        }
    }

    private void exportCsv(Store store, String arg) {
        String file = arg == null || arg.isBlank() ? CSV_EXPORT_DEFAULT : arg.trim();
        List<String> lines = new ArrayList<>();
        lines.add("id,date,amount,category,description,payment,note");
        for (Expense e : store.all()) {
            lines.add(csv(
                e.id,
                DATE_FMT.format(e.date),
                e.amount.toPlainString(),
                nz(e.category),
                nz(e.description),
                nz(e.payment),
                nz(e.note)
            ));
        }
        try {
            Files.write(Paths.get(file), lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            println("Exported " + lines.size() + " lines to " + file);
        } catch (IOException ex) {
            throw new RuntimeException("Export failed: " + ex.getMessage(), ex);
        }
    }

    private void importCsv(Store store, String arg) {
        if (arg == null || arg.isBlank()) throw new IllegalArgumentException("Usage: import <filename.csv>");
        Path p = Paths.get(arg.trim());
        if (!Files.exists(p)) throw new IllegalArgumentException("File not found: " + p);

        int imported = 0;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line; boolean header = true;
            while ((line = br.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] f = parseCsvLine(line, 7);
                LocalDate date = LocalDate.parse(f[1], DATE_FMT);
                BigDecimal amt = new BigDecimal(f[2]);
                String cat = emptyToNull(f[3]);
                String desc = emptyToNull(f[4]);
                String pay  = emptyToNull(f[5]);
                String note = emptyToNull(f[6]);
                store.add(date, amt, cat, desc, pay, note);
                imported++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
        store.save();
        println("Imported " + imported + " expense(s) from " + p.getFileName());
    }

    // ===== Rendering & Utils =====

    private static void renderTable(List<Expense> items) {
        if (items.isEmpty()) { System.out.println("(no expenses)"); return; }
        String header = String.format("%-4s %-10s %12s %-16s %-32s %-10s %-24s",
                "ID", "Date", "Amount", "Category", "Description", "Payment", "Note");
        System.out.println(header);
        System.out.println("-".repeat(header.length()));
        for (Expense e : items) {
            System.out.printf("%-4d %-10s %12s %-16s %-32s %-10s %-24s%n",
                    e.id,
                    DATE_FMT.format(e.date),
                    money(e.amount),
                    truncate(nz(e.category), 16),
                    truncate(nz(e.description), 32),
                    truncate(nz(e.payment), 10),
                    truncate(nz(e.note), 24));
        }
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String orNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s.trim(); }
    private static String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }

    private static int parseId(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { throw new IllegalArgumentException("Provide a numeric id"); }
    }

    private static BigDecimal parseAmount(String s) {
        try {
            BigDecimal v = new BigDecimal(s.trim());
            if (v.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Amount must be >= 0");
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount. Example: 149.99");
        }
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim(), DATE_FMT); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Invalid date, use yyyy-mm-dd"); }
    }

    private static Map<String,String> parseFlags(String input) {
        Map<String,String> out = new LinkedHashMap<>();
        if (input == null) return out;
        String[] tokens = input.split("\\s+");
        String current = null;
        StringBuilder val = new StringBuilder();
        for (String tok : tokens) {
            if (tok.startsWith("/")) {
                if (current != null) out.put(current, val.toString().trim());
                current = tok.toLowerCase(Locale.ROOT);
                val.setLength(0);
            } else if (current != null) {
                if (val.length() > 0) val.append(' ');
                val.append(tok);
            }
        }
        if (current != null) out.put(current, val.toString().trim());
        return out;
    }

    private static String leadingText(String input) {
        if (input == null) return "";
        int idx = input.indexOf(" /");
        return (idx >= 0 ? input.substring(0, idx) : input).trim();
    }

    // CSV utilities: naive but handles basic quoted fields
    private static String csv(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(String.valueOf(fields[i])));
        }
        return sb.toString();
    }
    private static String escapeCsv(String s) {
        if (s == null) return "";
        boolean need = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!need) return s;
        return "\"" + s.replace("\"","\"\"") + "\"";
    }
    private static String[] parseCsvLine(String line, int expected) {
        List<String> out = new ArrayList<>(expected);
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQ) {
                if (c == '\"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '\"') { cur.append('\"'); i++; }
                    else inQ = false;
                } else cur.append(c);
            } else {
                if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
                else if (c == '\"') inQ = true;
                else cur.append(c);
            }
        }
        out.add(cur.toString());
        while (out.size() < expected) out.add("");
        return out.toArray(new String[0]);
    }

    private static void println(String s) { System.out.println(s); }

    // ===== Data Model & Storage =====
    static class Expense {
        int id;
        LocalDate date;
        BigDecimal amount;
        String category;     // nullable
        String description;  // nullable
        String payment;      // nullable
        String note;         // nullable

        Expense() {}
        Expense(int id) { this.id = id; }

        String toTsv() {
            // id \t date \t amount \t category \t description \t payment \t note
            return id + "\t" + DATE_FMT.format(date) + "\t" + amount.toPlainString() + "\t" +
                    esc(nz(category)) + "\t" + esc(nz(description)) + "\t" + esc(nz(payment)) + "\t" + esc(nz(note));
        }
        static Expense fromTsv(String line) {
            String[] f = splitTsv(line, 7);
            Expense e = new Expense(Integer.parseInt(f[0]));
            e.date = LocalDate.parse(f[1], DATE_FMT);
            e.amount = new BigDecimal(f[2]);
            e.category = unesc(f[3]); if (e.category.isEmpty()) e.category = null;
            e.description = unesc(f[4]); if (e.description.isEmpty()) e.description = null;
            e.payment = unesc(f[5]); if (e.payment.isEmpty()) e.payment = null;
            e.note = unesc(f[6]); if (e.note.isEmpty()) e.note = null;
            return e;
        }
        private static String esc(String s) {
            return s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r");
        }
        private static String unesc(String s) {
            StringBuilder out = new StringBuilder();
            boolean esc = false;
            for (char c : s.toCharArray()) {
                if (!esc && c == '\\') { esc = true; continue; }
                if (esc) {
                    switch (c) {
                        case 't': out.append('\t'); break;
                        case 'n': out.append('\n'); break;
                        case 'r': out.append('\r'); break;
                        default: out.append(c); break;
                    }
                    esc = false;
                } else out.append(c);
            }
            return out.toString();
        }
        private static String[] splitTsv(String line, int expected) {
            List<String> parts = new ArrayList<>(expected);
            StringBuilder cur = new StringBuilder();
            boolean esc = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (!esc && c == '\\') { esc = true; cur.append(c); continue; }
                if (c == '\t' && !esc) { parts.add(cur.toString()); cur.setLength(0); }
                else { cur.append(c); esc = false; }
            }
            parts.add(cur.toString());
            while (parts.size() < expected) parts.add("");
            return parts.toArray(new String[0]);
        }
    }

    static class Store {
        private final Path path;
        private final Map<Integer, Expense> map = new LinkedHashMap<>();
        private int nextId = 1;

        Store(String file) { this.path = Paths.get(file); }

        synchronized Expense add(LocalDate date, BigDecimal amount, String category, String description, String payment, String note) {
            Expense e = new Expense(nextId++);
            e.date = date == null ? LocalDate.now() : date;
            e.amount = amount == null ? BigDecimal.ZERO : amount;
            e.category = category;
            e.description = description;
            e.payment = payment;
            e.note = note;
            map.put(e.id, e);
            return e;
        }

        synchronized boolean delete(int id) { return map.remove(id) != null; }
        synchronized Expense get(int id) { return map.get(id); }
        synchronized Collection<Expense> all() { return map.values(); }

        synchronized void save() {
            List<String> lines = new ArrayList<>();
            lines.add("# Expense TSV v1");
            lines.add("# id\tdate\tamount\tcategory\tdescription\tpayment\tnote");
            for (Expense e : map.values()) lines.add(e.toTsv());
            try {
                Files.write(path, lines, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save: " + e.getMessage(), e);
            }
            try {
                Files.writeString(Paths.get(path.toString() + ".meta"), Integer.toString(nextId), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ignored) {}
        }

        synchronized void load() {
            map.clear(); nextId = 1;
            if (!Files.exists(path)) return;
            try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("#") || line.isBlank()) continue;
                    Expense e = Expense.fromTsv(line);
                    map.put(e.id, e);
                    nextId = Math.max(nextId, e.id + 1);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to load: " + e.getMessage(), e);
            }
            Path meta = Paths.get(path.toString() + ".meta");
            if (Files.exists(meta)) {
                try {
                    String s = Files.readString(meta, StandardCharsets.UTF_8).trim();
                    nextId = Math.max(nextId, Integer.parseInt(s));
                } catch (Exception ignored) {}
            }
        }
    }
}
