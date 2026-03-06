# HisaabLite – Billing & Inventory POS System

HisaabLite is a lightweight Point of Sale (POS) and inventory management system built using **Spring Boot**.  
It is designed for small retail stores to perform **fast billing, product search, staff management and invoice generation**.

The system provides a **modern billing dashboard**, real-time product search, and role-based access for store owners and staff.

---

## 🚀 Live Demo

**Application URL:**  
http://80.225.241.135:8080/

⚠️ **Note:**  
The application is hosted on a personal cloud VM.  
If the VM is stopped or under maintenance, the URL may not be accessible when you try it.

---

## ✨ Features

### 🧾 Billing System
- Fast POS billing interface
- Add products using **search + keyboard navigation**
- Automatic subtotal and cart updates
- Discount support
- Multiple payment modes (Cash / UPI / Card)

### 📦 Product Management
- Add, update and manage products
- Stock tracking
- Product search suggestions with available stock

### 👥 Staff Management
Owner can:
- Add staff members
- Assign roles (Manager / Cashier)
- Reset staff passwords
- Deactivate staff accounts
- Delete staff users

### 🔐 Authentication & Security
- Spring Security based login system
- Role-based access control
- Inactive users prevented from logging in

### 📊 Sales Management
- Cart based billing
- Invoice generation
- Sales history tracking

---

## 🧠 Key Highlights

- Real-time **AJAX product search**
- **Keyboard driven billing workflow**
- **Role-based login system**
- **Discount calculation**
- **Change return calculation**
- Clean **POS dashboard UI**

---

## 🏗 Tech Stack

**Backend**
- Java
- Spring Boot
- Spring Security
- Spring Data JPA

**Frontend**
- Thymeleaf
- HTML
- CSS
- JavaScript
- AJAX

**Database**
- MySQL

**Deployment**
- Oracle Cloud Infrastructure (OCI)
- Ubuntu VM
- Nginx / OpenJDK runtime

---

## ⚙️ Installation (Local Setup)

1. Clone repository

```bash
git clone https://github.com/yourusername/hisaablite.git
```

2. Navigate to project

```bash
cd hisaablite
```

3. Configure database in `application.properties`

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/hisaablite
spring.datasource.username=root
spring.datasource.password=yourpassword
```

4. Run application

```bash
mvn spring-boot:run
```

Application will start at:

```
http://localhost:8080
```

---

## 🧑‍💻 Author

**Waseem Khan**

Java Backend Developer

Tech Focus:
- Spring Boot
- Microservices
- REST APIs
- Cloud Deployment

---

## 📌 Future Improvements

- GST calculation support
- Advanced sales reports
- Barcode scanning
- Multi-store support
- Docker deployment

---

## ⭐ Project Purpose

This project was developed to demonstrate **real-world backend development skills**, including:

- Spring Boot application design
- Authentication & role management
- Cloud deployment
- POS system architecture