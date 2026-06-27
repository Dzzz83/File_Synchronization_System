# 📁 File Synchronization System

## Project Documentation

---

## Introduction

This project is a distributed file synchronization system similar to Dropbox. It consists of a Spring Boot server and a JavaFX client application.

The client provides a full-featured administrative GUI for file management with automatic background synchronization. An asynchronous synchronization engine continuously compares local and server states and performs the required actions (upload, download, and conflict resolution) without user intervention.

---

# Technology Stack

### Core Technologies

* Java 17 (compatible with Java 25)
* Maven (Multi-Module Project)
* Spring Boot 3.2.5

    * Spring Web
    * Spring Data JPA
    * Spring Security
    * Spring WebFlux
    * Thymeleaf
* PostgreSQL (Metadata Storage)
* JavaFX 17 (Desktop Client)

### Networking & Communication

* Spring WebClient
* WebSocket (STOMP)
* RabbitMQ (CloudAMQP)

### Authentication & Security

* JWT (JJWT)
* Bucket4j (Rate Limiting)

### Storage

* Local Disk Storage
* Cloudflare R2
* AWS S3 SDK

### Synchronization & Processing

* Redis
* Flyway
* diff-match-patch

### Document & Media Processing

* Apache PDFBox
* Apache POI
* JSoup

### Monitoring

* JavaMelody

---

# Project Structure

The project is divided into three Maven modules:

| Module     | Description                                                                                         |
| ---------- | --------------------------------------------------------------------------------------------------- |
| **common** | Shared DTOs, enums, and utility classes                                                             |
| **server** | Spring Boot backend with REST APIs, authentication, storage, synchronization, and WebSocket support |
| **client** | JavaFX desktop application with integrated automatic synchronization                                |

```text
File_Synchronization_System/
├── common/
├── server/
├── client/
└── ...
```

*(Full project structure omitted here for brevity in documentation, but retained in repository source.)*

---

# Implemented Features

## Server Features

### File Management APIs

* File metadata endpoints
* Upload and download APIs
* Chunked upload APIs
* Synchronization APIs
* Conflict detection APIs

### Authentication

* User registration
* Forgot password
* Password reset
* JWT-based authentication

### Storage

#### Full File Storage Backends

1. Local Storage (`./uploads`)
2. Cloudflare R2 (S3 Compatible)

Switch using:

```properties
storage.type=local
```

or

```properties
storage.type=r2
```

#### Chunk Storage Implementations

**LocalDiskChunkStorage**

* Stores chunks locally
* Reassembles chunks on the server
* Saves final file using the configured storage backend

**R2ChunkStorageService**

* Uses S3 Multipart Upload
* Sends chunks directly to R2
* No local disk assembly required

### Synchronization

* Asynchronous background synchronization
* RabbitMQ-backed processing
* Task-based sync execution

```http
POST /api/sync/start
```

Returns a task ID.

Clients poll:

```http
GET /api/sync/status/{taskId}
```

until completion.

### Folder Size Caching

Folder sizes are automatically maintained and updated during:

* Upload
* Delete
* Move
* Edit

This eliminates recursive size calculations during file listings.

### Streaming Transfers

* Streaming uploads
* Streaming downloads
* No full-file memory loading

### Conflict Detection

The client sends the original file hash.

If the server detects modifications:

```http
409 Conflict
```

is returned.

### Database

* PostgreSQL metadata storage
* Multi-instance support

### Messaging & Background Processing

* RabbitMQ queue processing
* Decoupled synchronization execution

### Rate Limiting

Bucket4j limits each IP to:

```text
100 requests/minute
```

### Monitoring

JavaMelody dashboard:

```text
/monitoring
```

Provides:

* CPU usage
* Memory usage
* HTTP metrics
* SQL metrics
* JVM statistics

### Shared Folders

Supports:

* Shared folder creation
* READ/WRITE permissions
* Access requests
* Request approval
* Folder deletion

### Permission System

Each file includes:

```text
READ
WRITE
NONE
```

permission values.

### Real-Time Features

#### WebSocket Updates

Provides:

* File creation events
* File updates
* File deletions
* Folder updates
* Chat messaging

#### Redis Active User Tracking

Tracks:

* Active folder viewers
* Automatic stale-user cleanup

---

## Client Features (JavaFX)

### Startup

Provides:

* Login
* Registration
* Password reset

### File Explorer

Displays:

* File path
* Human-readable size
* Last modified date

Supports:

* Upload
* Download
* Delete
* Move
* Create file
* Create folder
* Refresh

### Navigation

* Breadcrumb navigation
* Folder double-click navigation
* Parent folder (`..`) navigation

### Upload System

Supports:

* Single file uploads
* Folder uploads
* Structure preservation

Files larger than **5 MB** use chunked uploads with up to **5 parallel chunks**.

### Download System

Provides:

* Streaming downloads
* Real-time progress tracking

### Text Editing

Supports:

* `.txt` editing
* Conflict detection
* Side-by-side merge view

### PDF Viewer

Supports:

* Zoom
* Page navigation
* Fit-to-width

### DOCX Editor

Supports:

* Rich text editing
* Bold
* Italic
* Underline

Uses:

* Apache POI
* JSoup

### Image Viewer

Supported formats:

* PNG
* JPG
* JPEG
* GIF
* BMP

Features:

* Zoom
* Fit-to-window
* Original size

### Media Player

Supported formats:

* MP3
* WAV
* MP4
* AVI
* MOV
* MKV

Features:

* Play/Pause
* Seek
* Volume control
* Time display

### Shared Folder Management

Supports:

* Folder creation
* Member management
* Permission updates
* Access approval
* Folder deletion

### Real-Time Chat

Provides:

* Live messaging
* Active collaborator list
* Message history

### Real-Time File Updates

Automatically refreshes UI when:

* Files are created
* Files are modified
* Files are deleted

### Automatic Background Synchronization

Every **5 minutes**, the client:

1. Scans local files
2. Sends state to server
3. Executes synchronization actions
4. Resolves conflicts when necessary

### Global Progress Tracking

Displays:

* Current operation
* Real-time progress

Prevents conflicting operations by disabling controls during execution.

### Permission-Aware UI

* Delete → WRITE only
* Download → READ or WRITE

### Drag & Drop

Supports moving files and folders directly within the file explorer.

---

# OOP & SOLID Principles

### Single Responsibility Principle

Each class has a single responsibility:

* FolderScanner
* ChunkedUploader
* ConflictResolver
* JwtService

### Open/Closed Principle

Storage backends can be extended without modifying existing logic.

### Liskov Substitution Principle

Implementations of:

* FileStorage
* ChunkStorageService

can be substituted transparently.

### Interface Segregation Principle

Focused interfaces prevent unnecessary dependencies.

### Dependency Inversion Principle

Dependencies are injected through abstractions.

### Design Patterns Used

#### Strategy Pattern

Used for conflict resolution strategies.

#### Factory Pattern

Used for storage backend selection.

---

# Scalability & Production Features

* Stateless JWT authentication
* Shared PostgreSQL database
* RabbitMQ asynchronous processing
* Parallel chunk uploads
* Flyway database migrations
* Bucket4j rate limiting
* JavaMelody monitoring
* Horizontal server scaling
* Parallel folder uploads
* Real-time WebSocket communication
* Redis-based active user tracking

---

# How to Run

## Prerequisites

* Java 17 (or Java 25)
* Maven
* PostgreSQL
* Cloudflare R2 (optional)
* RabbitMQ (optional)
* Redis (required for chat and active user tracking)

---

## Running the Server

### 1. Navigate to Server Module

```bash
cd server
```

### 2. Configure Environment Variables

Required variables:

```text
DB_URL=
DB_USER=
DB_PASSWORD=
JWT_SECRET=
REDIS_HOST=
REDIS_PORT=

# Optional – R2 storage (if storage.type=r2)
R2_ACCESS_KEY_ID=
R2_SECRET_ACCESS_KEY=
R2_ENDPOINT=
R2_BUCKET_NAME=
R2_REGION=auto

# Optional – RabbitMQ (if not using local defaults)
CLOUDAMQP_URL=

# Optional – Redis authentication
REDIS_PASSWORD=
REDIS_USER=

# Optional – Password reset emails
MAIL_USERNAME=
MAIL_PASSWORD=

# Optional – JavaMelody monitoring dashboard
METRICS_PASSWORD=
```

### 3. Configure Storage Backend

```properties
storage.type=r2
```

### 4. Build and Run

```bash
mvn clean package
java -jar target/server-*.jar
```

Server starts on:

```text
http://localhost:8080
```

---

## Running the Client

### 1. Navigate to Client Module

```bash
cd client
```

### 2. Build

```bash
mvn clean package
```

### 3. Run

```bash
java -jar target/client-*.jar
```

Or run:

```text
GUIApplication
```

directly from your IDE.

### 4. Login

Enter:

```text
http://localhost:8080
```

as the server URL and either:

* Register a new account
* Log in with existing credentials

The background synchronization service starts automatically after login.

---

# Running Multiple Server Nodes (Horizontal Scaling)

To demonstrate horizontal scaling, the `demo/run-servers.bat` script starts **four server instances** on ports **8080–8083** (all binding to `0.0.0.0`).

Each instance:

* Shares the same PostgreSQL database
* Uses the same JWT secret
* Operates as a stateless server
* Can handle requests interchangeably

This setup demonstrates how the system can scale horizontally by distributing client requests across multiple backend nodes.

## Using a Load Balancer (Nginx)

For production deployments, **Nginx** can be configured as a reverse proxy with **round-robin load balancing**.

### Example Nginx Configuration

```nginx
upstream backend {
    server 127.0.0.1:8080;
    server 127.0.0.1:8081;
    server 127.0.0.1:8082;
    server 127.0.0.1:8083;
}

server {
    listen 80;

    location / {
        proxy_pass http://backend;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### How It Works

1. The client sends requests to Nginx.
2. Nginx distributes requests across the available server nodes.
3. Each node accesses the same PostgreSQL database.
4. JWT authentication remains valid across all nodes because the same secret key is used.
5. If one node becomes unavailable, traffic can continue to be served by the remaining nodes.

### Benefits

* Improved throughput through request distribution
* Better resource utilization
* Fault tolerance
* Horizontal scalability
* Stateless architecture support

After configuring Nginx, point the client to the load balancer URL instead of an individual server instance:

```text
http://localhost
```

All requests will then be routed through Nginx and distributed across the available backend servers.

# Conclusion

This project successfully implements a Dropbox-like distributed file synchronization platform using a modern, production-oriented architecture.

It combines:

* Stateless authentication
* Cloud object storage
* Asynchronous processing
* RabbitMQ messaging
* Redis state management
* Parallel uploads
* Rate limiting
* Database migrations
* Embedded monitoring

The result is a fully self-contained, horizontally scalable system ready for deployment in real-world environments.
