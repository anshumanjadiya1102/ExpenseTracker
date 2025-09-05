<p align="center">
  <img src="https://img.shields.io/badge/Java-Expense%20Tracker-orange?style=for-the-badge&logo=java&logoColor=white" />
  <img src="https://img.shields.io/badge/Type-CLI-blue?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Status-Active-brightgreen?style=for-the-badge" />
</p>

<h1 align="center">💰 Expense Tracker (Java CLI)</h1>

A fast, no-nonsense **expense tracker** you can run anywhere.  
Single-file Java, no frameworks, **persists to disk**, does **filters, reports, and CSV**.

---

## ✨ Features
- ➕ Add expenses with **date, amount, category, description, payment method, note**
- 🔍 List & filter by **month**, **date range**, **category**, or **search text**
- 📊 **Monthly report**: total + category breakdown with percentages
- 💾 Persistent storage in a simple **TSV file**
- 📤 **Export** to CSV, 📥 **Import** from CSV
- 🧰 Single-file Java 11+, **no external libraries**

## Downloads
<a href="https://yourwebsite.com/downloads/my-repo.zip" download>
  Download My GitHub Project 
</a>
---

## 🚀 Quick Start

```bash
# 1) Compile
javac ExpenseTracker.java
```
You’ll see a prompt like:
```csharp
===== Expense Tracker (CLI) =====
Storage: expenses.tsv
>
```
## 🧭 Commands
```bash
add <amount> <description> [/date yyyy-mm-dd] [/cat category] [/pay method] [/note text]
list [all | month yyyy-mm | today | range yyyy-mm-dd..yyyy-mm-dd] [/cat name] [/sort date|amt|cat] [/rev]
edit <id> [/amt number] [/t new description] [/date yyyy-mm-dd] [/cat category] [/pay method] [/note text]
del <id>
search <text>
report month <yyyy-mm>
export [filename.csv]
import <filename.csv>
save | exit
```
## Examples
```
> add 249.99 Headphones /cat electronics /pay upi /date 2025-08-27
> add 120.50 Groceries at LocalMart /cat food /pay cash
> list month 2025-08 /cat food
> search headphones
> report month 2025-08
> export my_expenses.csv
```
## 📊 Sample Output
```yaml
ID   Date         Amount      Category         Description                      Payment    Note
---- ------------ ----------- ---------------- -------------------------------- ---------- ------------------------
1    2025-08-27   249.99      electronics      Headphones                       upi
2    2025-08-27   120.50      food             Groceries at LocalMart           cash
Total: 370.49
```
## 🧱 Data Files

- expenses.tsv – human-readable storage

- expenses.tsv.meta – tracks next ID

- expenses_export.csv – default export name (override in command)

## 🛠 Requirements

Java 11+

## 📜 License

MIT – free to use and modify.
PRs welcome! 🚀

<div align=center>
💰 A simple Java CLI app to track, view, and manage your daily expenses with local file storage.
</div>
