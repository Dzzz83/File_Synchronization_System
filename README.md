# File Synchronization System – Project Documentation

## Introduction

This project is a distributed file synchronization system similar to Dropbox. It consists of a Spring Boot server and a JavaFX client. The client supports two modes: an automatic sync client (folder watcher) and a manual admin GUI for file management.

The system is designed with scalability in mind: it supports chunked upload with resume, pluggable object storage (local disk or Cloudflare R2), and streaming I/O to avoid memory bottlenecks.

All code follows SOLID principles. The server is stateless, making it suitable for horizontal scaling.

## Technology Stack

- Java 17
- Maven (multi‑module)
- Spring Boot 3.2.5 (Web, Data JPA, Security, WebFlux, Thymeleaf)
- H2 database (metadata, local client DB)
- JavaFX 17 (client GUI)
- diff‑match‑patch for character‑level diff
- AWS S3 SDK (for Cloudflare R2 integration)
- Cloudflare R2 (optional object storage backend)

## Project Structure

The project is split into three Maven modules:

- **common** – shared DTOs, enums, and utility classes (e.g., VersionVector).
- **server** – Spring Boot application with REST APIs, JPA entities, and storage backends.
- **client** – JavaFX application containing both the sync client and the admin GUI.

```
File_Synchronization_System/
├── pom.xml (parent)
├── common/
│   └── src/main/java/com/filesync/common/
│       ├── dto/
│       ├── enums/
│       └── model/
├── server/
│   └── src/main/java/com/filesync/server/
│       ├── config/               (SecurityConfig, R2Config)
│       ├── controller/           (FileController, SyncController, ChunkUploadController)
│       ├── domain/               (FileMetadataEntity, User, SyncTask)
│       ├── repository/           (JPA repositories)
│       ├── service/              (FileMetaDataService, FileContentService, AsyncSyncService)
│       ├── storage/              (FileStorage interface, LocalFileStorage, R2StorageService,
│       │                          ChunkStorageService interface, LocalDiskChunkStorage, R2ChunkStorageService)
│       └── web/                  (Thymeleaf controllers – optional)
└── client/
    └── src/main/java/com/filesync/client/
        ├── admin/                (ServerAdminApp, StartupController, ServerFileListController,
        │                          EditDialogController, ConflictResolver)
        ├── sync/                 (SyncEngine, FolderScanner)
        ├── http/                 (SyncHttpClient, ChunkedUploader)
        └── db/                   (LocalMetadataRepository)
```

## Current Status – What Is Already Implemented

### Server

- All REST endpoints for file metadata, simple upload/download, chunked upload, sync, and conflict detection.
- User registration, forgot password, and reset password as JSON endpoints.
- Chunked upload with resume: chunks are stored temporarily and assembled on the server.
- Two storage backends for full files: local disk (`./uploads`) and Cloudflare R2 (S3‑compatible). The backend is switchable via `storage.type={local|r2}`.
- Two chunk storage implementations:
    - `LocalDiskChunkStorage` – stores chunks on local disk, assembles them, then saves the final file using the chosen full‑file storage.
    - `R2ChunkStorageService` – uses S3 multipart upload to send parts directly to R2. The final file is assembled on the cloud side, with no local disk usage for chunks or assembly.
- Streaming upload and download (no entire file loaded into memory).
- Conflict detection when editing a file: client sends the original hash, server returns 409 if the file changed since last download.

### Client – Admin GUI (JavaFX)

- Startup dialog with tabs for login and registration, plus separate windows for forgot password and reset password.
- Main file list: table showing path, size, last modified, and buttons for upload, download, edit, delete, refresh.
- Upload uses chunked upload for files larger than 5 MB; otherwise simple upload.
- Download works via the streaming endpoint.
- Edit: downloads a text file, allows editing in a simple TextArea. When saving, the server is asked to compare the original hash; if a conflict occurs, the side‑by‑side diff viewer (reused from the sync client) opens, and the user can merge the changes.
- Delete: removes metadata and the actual file from storage.
- All file operations are performed via HTTP calls to the server.

### Client – Sync Client (Automatic)

- Watches a local folder using Java’s WatchService.
- Computes SHA‑256 hashes and calls the server’s sync endpoint.
- Based on the server response, it uploads, downloads, deletes, or resolves conflicts (using the same diff viewer as the admin GUI).
- Stores local metadata in an H2 database to avoid recomputing hashes unnecessarily.

### OOP & SOLID Highlights

- Single Responsibility: each class has one purpose (FolderScanner, ChunkedUploader, ConflictResolver, etc.).
- Open‑Closed: new storage backends can be added without modifying sync or controller logic.
- Liskov Substitution: any implementation of FileStorage or ChunkStorageService can be swapped.
- Interface Segregation: focused interfaces (FileStorage, ChunkStorageService) keep the code decoupled.
- Dependency Inversion: high‑level modules depend on abstractions; constructors inject the dependencies.
- Strategy Pattern: conflict resolution strategies are prepared.
- Factory Pattern: used for creating the appropriate storage backend based on configuration.

## Remaining Work (for a High Grade)

The system is fully functional for a single‑node deployment. The following tasks address **scalability** and are planned for completion:

1. **Asynchronous Sync** – The current sync endpoint works synchronously. For large syncs, this may cause HTTP timeouts. We plan to implement a task‑based asynchronous API: the client sends a start request, gets a task ID, and polls for status. This allows the sync to run in the background and supports many concurrent syncs.

2. **Replace H2 with PostgreSQL** – H2 is not suitable for clustered deployments. Moving to PostgreSQL (or another production database) allows multiple server instances to share the same metadata.

3. **Horizontal Scaling** – Make the server completely stateless (use JWT for authentication, store sessions in Redis). Then a load balancer can distribute traffic across several instances.

4. **Message Queue for Heavy Tasks** – Push long‑running operations (like chunk assembly or sync) into a message queue (RabbitMQ, SQS) to decouple request handling from background processing.

5. **Client‑Side Parallel Chunk Upload** – Currently, the client uploads chunks sequentially. Uploading several chunks concurrently would improve throughput.

6. **Monitoring & Auto‑scaling** – Integrate Micrometer + Prometheus + Grafana, and optionally use Kubernetes Horizontal Pod Autoscaling.

The **asynchronous sync** will be the first of these to be implemented, as it offers the biggest immediate improvement for large folders.

## How to Run

### Prerequisites
- Java 17 (or 25 – the code works with both)
- Maven (or use the Maven wrapper)

### Running the Server

1. Navigate to the `server` directory.
2. Configure `application.properties`:
    - Set `storage.type=r2` if you want to use Cloudflare R2, or `storage.type=local` for local disk.
    - For R2, provide the endpoint, access key, secret key, and bucket name (either via environment variables or a `.env` file).
3. Run with Maven:
   ```
   mvn spring-boot:run
   ```
   The server will start on port 8080.

### Running the Admin GUI

1. Navigate to the `client` directory.
2. Run the `ServerAdminApp` class. From the command line (adjust classpath as needed):
   ```
   mvn clean compile
   mvn exec:java -Dexec.mainClass="com.filesync.client.admin.ServerAdminApp"
   ```
   Or run it directly from your IDE.
3. In the startup dialog, enter the server URL (e.g., `http://localhost:8080`), your owner ID (username), and optionally register if you don’t have an account yet.

### Running the Sync Client

1. Similarly, run the `ClientApplication` class.
2. Provide the local folder to watch and the server URL (or hardcode them). The sync client will then keep the folder synchronised with the server.

## Conclusion

The project successfully implements a Dropbox‑like system with a modern architecture. It demonstrates many important software design principles and scalability techniques. By completing the remaining tasks (especially asynchronous sync and database migration), the system would be ready for a production environment with hundreds of concurrent users.