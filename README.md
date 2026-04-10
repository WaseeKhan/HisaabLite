# Expygen – Retail Billing & Inventory Management System

A lightweight, modern Point of Sale (POS) solution for small retail businesses. Streamline billing, inventory tracking, staff management, and customer invoicing with ease.

---

## 🚀 Live Demo

🔗 [https://expygen.duckdns.org](https://expygen.duckdns.org)

*Hosted on a personal VM — may be temporarily unavailable if the server is stopped.*

---

## ✨ Core Features

### 🧾 Smart Billing
- Keyboard-driven POS interface
- Real-time product search with AJAX
- Instant cart updates with subtotal
- Discount & change calculation
- Multiple payment modes: Cash, UPI, Card

### 📦 Inventory Management
- Add, edit, delete products
- Real-time stock tracking
- Low stock alerts
- Product search with availability status

### 👥 Staff Management
- Role-based access (Owner, Manager, Cashier)
- Add, deactivate, or delete staff
- Reset passwords
- Secure authentication

### 📊 Sales & Invoices
- GST-compliant invoices
- PDF download
- WhatsApp sharing
- Sales history with search & filters

### 📈 Analytics Dashboard
- Today's revenue, invoices, items sold
- Lifetime business metrics
- Top products & customers
- Low stock alerts

### 💬 WhatsApp Integration
- Connect WhatsApp Business number
- QR code linking
- Send invoices via WhatsApp

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA |
| Frontend | Thymeleaf, HTML5, CSS3, JavaScript, AJAX |
| Database | MySQL 8.0 |
| PDF | iTextPDF |
| Charts | Chart.js |
| Deployment | Oracle Cloud, Ubuntu, Nginx |

---

## ⚙️ Local Setup

### Prerequisites
- Java 21+
- MySQL 8.0+
- Maven

### Steps
```bash
git clone https://github.com/yourusername/expygen.git
cd expygen
# Update database credentials in application.properties
mvn spring-boot:run