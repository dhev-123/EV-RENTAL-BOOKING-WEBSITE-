# DHEV EV Rental - Java Full Stack

This is a Java backend plus frontend version of the EV Rental and Booking Management Platform.

## Login

- User ID: `dhevrentalev`
- Password: `1234`

## Run

Install JDK 17 or newer, then run:

```powershell
cd java-fullstack
javac -d out src\DhevEvRentalServer.java
java -cp out DhevEvRentalServer
```

Open:

```text
http://localhost:8080
```

## API

- `POST /api/login`
- `GET /api/vehicles`
- `POST /api/vehicles`
- `GET /api/bookings`
- `POST /api/bookings`
- `POST /api/bookings/cancel`

The backend uses Java's built-in HTTP server and keeps data in memory for a simple VS Code project.
