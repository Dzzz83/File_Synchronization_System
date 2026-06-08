# File Synchronization System – Project Documentation

## Introduction

This project is a distributed file synchronization system similar to Dropbox. It consists of a Spring Boot server and a JavaFX client. The client supports two modes: an automatic sync client (folder watcher) and a manual admin GUI for file management.

The system is designed with scalability in mind: it supports chunked upload with resume, pluggable object storage (local disk or Cloudflare R2), streaming I/O to avoid memory bottlenecks, and a stateless JWT‑based authentication that allows horizontal scaling.

All code follows SOLID principles. The server is stateless, making it suitable for running multiple instances behind a load balancer.

## Technology Stack

- Java 17 (works with Java 25 as well)
- Maven (multi‑module)
- Spring Boot 3.2.5 (Web, Data JPA, Security, WebFlux, Thymeleaf)
- PostgreSQL (metadata) and H2 (client local cache)
- JavaFX 17 (client GUI)
- diff‑match‑patch for character‑level diff
- AWS S3 SDK (for Cloudflare R2 integration)
- Cloudflare R2 (optional object storage backend)
- JJWT for JSON Web Tokens
- RabbitMQ (CloudAMQP) for asynchronous task processing
- Flyway for database migrations
- JavaMelody for embedded monitoring
- Bucket4j for rate limiting

## Project Structure

The project is split into three Maven modules:

- **common** – shared DTOs, enums, and utility classes (e.g., VersionVector).
- **server** – Spring Boot application with REST APIs, JPA entities, storage backends, and JWT authentication.
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
│       ├── config/               (SecurityConfig, R2Config, RabbitMQConfig)
│       ├── filter/               (RateLimitFilter)
│       ├── controller/           (FileController, SyncController, AuthController, ChunkUploadController)
│       ├── domain/               (FileMetadataEntity, User, SyncTask)
│       ├── repository/           (JPA repositories)
│       ├── service/              (FileMetaDataService, FileContentService, JwtService, SyncTaskStatusService)
│       ├── storage/              (FileStorage, LocalFileStorage, R2StorageService,
│       │                          ChunkStorageService, LocalDiskChunkStorage, R2ChunkStorageService)
│       └── consumer/             (RabbitMQ consumer for sync tasks)
└── client/
    └── src/main/java/com/filesync/client/
    ├── controller/           (JavaFX controllers: FileExplorerController, SharedFoldersController, etc.)
    ├── dialog/               (Dialog loaders: CreateFolderDialog, AddMemberDialog, RequestAccessDialog, PendingRequestsDialog)
    ├── service/              (Business services: FileOperationService, PasswordResetService)
    ├── http/                 (SyncHttpClient, ChunkedUploader)
    ├── sync/                 (SyncEngine, FolderScanner)
    ├── db/                   (LocalMetadataRepository)
    ├── conflict/             (ConflictResolver, ConflictController)
    └── file/     
            
```

## What Is Already Implemented

### Server

- All REST endpoints for file metadata, simple upload/download, chunked upload, sync, and conflict detection.
- User registration, forgot password, and reset password as JSON endpoints.
- Chunked upload with resume: chunks are stored temporarily and assembled on the server.
- Two storage backends for full files: local disk (`./uploads`) and Cloudflare R2 (S3‑compatible). Switchable via `storage.type={local|r2}`.
- Two chunk storage implementations:
    - `LocalDiskChunkStorage` – stores chunks on local disk, assembles them, then saves the final file using the chosen full‑file storage.
    - `R2ChunkStorageService` – uses S3 multipart upload to send parts directly to R2. The final file is assembled on the cloud side, with no local disk usage for chunks or assembly.
- **Folder size caching** – The size of a folder is automatically computed as the total size of all files inside it (including subfolders) and stored in the database. This value is updated incrementally on every file upload, delete, move, or edit, eliminating recursive size calculations on each listing. The client displays folder sizes in a human‑readable format (KB, MB, GB).
- Streaming upload and download – no entire file loaded into memory.
- Conflict detection when editing a file: client sends the original hash, server returns 409 if the file changed since last download.
- **Asynchronous sync** – endpoint `POST /api/sync/start` returns a task ID. The server performs the file comparison in a background thread and stores the resulting actions (upload, download, conflict, etc.) as JSON. Client polls `GET /api/sync/status/{taskId}` until completion. This prevents HTTP timeouts and supports many concurrent syncs.
- **PostgreSQL** is now used for metadata storage (instead of H2). Multiple server instances can share the same database.
- **JWT authentication** – the server is stateless. Endpoint `POST /api/auth/login` returns a token. All protected endpoints (files, sync, chunks) require a valid `Authorization: Bearer <token>` header.
- **RabbitMQ (message queue)** – sync requests are sent to a queue and processed by a separate consumer. This decouples HTTP handling from background work and makes the system more resilient under load.
- **Rate limiting** – a Bucket4j filter limits each IP to 100 requests per minute, protecting the server from abusive clients.
- **Embedded monitoring** – JavaMelody provides a web dashboard at `/monitoring` showing CPU, memory, HTTP requests, SQL queries, and more (no extra setup).
- **Shared folders** – users can create shared folders, add members with READ/WRITE permissions, request access by folder name (search), and approve requests. Folder owners can delete a shared folder (removes all files and members). All file operations respect folder‑level permissions.

### Client – Admin GUI (JavaFX)

- Startup dialog with tabs for login (username + password) and registration, plus separate windows for forgot password and reset password.
- Login validates credentials with the server and stores the JWT. Logout clears the token.
- Main file list: table showing path, human‑readable size (e.g., 1.5 MB, 256 KB), last modified date, and buttons for upload, download, edit, delete, refresh.
- Upload uses chunked upload for files larger than 5 MB, with **parallel chunk upload** (up to 5 chunks concurrently) for faster large file transfers. The global progress bar shows real‑time byte‑level progress for both uploads and downloads. Every chunk request includes the JWT.
- Download works via the streaming endpoint with the token, and now reports progress through the global progress bar (including estimated bytes transferred).
- Edit: downloads a text file, allows editing. When saving, the server compares the original hash; if a conflict occurs, the side‑by‑side diff viewer (reused from the sync client) opens, and the user can merge the changes.
- Delete: removes metadata and the actual file from storage.
- All file operations are performed via HTTP calls that include the JWT.
- **Shared folders management** – a separate tab lists all shared folders accessible to the user. Owners see a red badge on the “Manage Requests” button when pending requests exist. Owners can add/update members (READ/WRITE), approve access requests, and delete the folder.
- **Modular UI** – dialogs (create folder, add member, request access, pending requests) are separated into their own FXML files and controller classes, following a clear separation of UI and logic.
- **File explorer navigation** – users can double‑click folders to navigate inside, and use the “..” entry to go up one level. The same explorer is used for personal files and for browsing inside shared folders, providing a consistent experience.
- **Drag & drop file moves** – Files and folders can be moved to a different location by dragging them onto a folder or the “..” (parent folder) entry. Moving to the root of personal files or out of shared folders is fully supported, with automatic permission checks.
- **Upload file or folder** – a single “Upload” button offers a choice between uploading a single file or an entire folder (with subfolders preserved). Large files use chunked upload; folder upload shows a progress dialog.

### Client – Sync Client (Automatic)

- Watches a local folder using Java’s WatchService.
- Computes SHA‑256 hashes and calls the server’s asynchronous sync endpoint (start + poll).
- Based on the server response (actions), it uploads, downloads, deletes, or resolves conflicts (using the same diff viewer as the admin GUI).
- Stores local metadata in an H2 database to avoid recomputing hashes unnecessarily.

### Shared Folder Features

- **Create** – name, optional initial members (searched by username/email), owner automatically gets WRITE permission.
- **Request access** – search by folder name, select from results, the owner receives a pending request.
- **Manage requests** – owners see a red badge with the count of pending requests; they can approve, adding the requester as READ‑only.
- **Manage members** – owners can add or update members (READ/WRITE) and revoke access (by removing the member).
- **Delete folder** – owners can delete the entire folder, which removes all files (both metadata and actual storage) and members.
- **Permission enforcement** – READ allows download/list, WRITE allows upload/edit/delete. Permissions are checked on every file operation.
- **Integrated file explorer** – double‑click a shared folder to open its contents inside the same tab (instead of a new window). The explorer has the same buttons and “..” navigation as the personal files tab. A “Back to folders” button appears when inside a shared folder (or the “..” entry at the root exits back to the shared folder list).
### OOP & SOLID Highlights

- **Single Responsibility**: each class has one purpose (FolderScanner, ChunkedUploader, ConflictResolver, JwtService, etc.).
- **Open‑Closed**: new storage backends can be added without modifying sync or controller logic.
- **Liskov Substitution**: any implementation of FileStorage or ChunkStorageService can be swapped.
- **Interface Segregation**: focused interfaces (FileStorage, ChunkStorageService) keep the code decoupled.
- **Dependency Inversion**: high‑level modules depend on abstractions; constructors inject the dependencies.
- **Strategy Pattern**: conflict resolution strategies are prepared.
- **Factory Pattern**: used for creating the appropriate storage backend based on configuration.

## Scalability & Production Features

- **Stateless JWT** – no sessions; any server instance can handle any request.
- **Shared PostgreSQL** – multiple server instances share the same metadata.
- **RabbitMQ** – async sync tasks are decoupled from HTTP requests.
- **Parallel chunk upload** – client uploads up to 5 chunks simultaneously, improving throughput.
- **Rate limiting** – per‑IP token bucket (100 requests/minute) prevents abuse.
- **Database migrations** – Flyway manages schema versions safely.
- **Embedded monitoring** – JavaMelody dashboard at `/monitoring` for real‑time metrics.
- **Horizontal scaling** – demonstrated by running two server instances behind Nginx with load balancing.
- **Parallel folder upload** – files inside a folder are uploaded concurrently (up to 5 at a time) while preserving the directory structure.
## What’s Already Deployed and Running

The server is live on **Render** (free tier) at:  
`https://file-synchronization-system.onrender.com`

It uses:
- Neon.tech PostgreSQL (free)
- Cloudflare R2 for file storage
- CloudAMQP RabbitMQ (free “Little Lemur” plan)
- A `/health` endpoint that an external cron job pings every 10 minutes to keep the instance awake (avoids cold starts during demos).

The JavaFX client works with this public URL – just enter the URL in the login screen.

## Future Enhancements (Optional)

- **Distributed tracing** (Jaeger) for request flows across services.
- **Redis caching** for frequently accessed file metadata.
- **Kubernetes deployment** with Horizontal Pod Autoscaling.

## How to Run

### Prerequisites

- Java 17 (or 25 – the code works with both)
- Maven (or use the Maven wrapper)
- PostgreSQL instance (e.g., Neon.tech free tier) for metadata
- Cloudflare R2 account (optional, for object storage)

### Running the Server

1. Navigate to the server directory.
2. Create a `.env` file (or set environment variables) with the following:
    - `DB_URL`, `DB_USER`, `DB_PASSWORD` for PostgreSQL
    - `R2_ENDPOINT`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET_NAME` (if using R2)
    - `JWT_SECRET` (a long random string, at least 32 characters)
3. Configure `application.properties`:
    - Set `storage.type=r2` for Cloudflare R2, or `storage.type=local` for local disk.
4. Run with Maven:
   ```
   mvn spring-boot:run
   ```
   The server starts on port 8080.

### Running the Admin GUI

1. Navigate to the client directory.
2. Run the `ServerAdminApp` class (from IDE or via Maven exec plugin).
3. In the startup dialog, enter the server URL (e.g., `http://localhost:8080`). If you don’t have an account, use the **Register** tab. Then log in with your username and password.

### Running the Sync Client

1. Run the `ClientApplication` class (or a custom launcher).
2. Provide the local folder path and the server URL. The client will authenticate (you need to pass username and password) and then keep the folder synchronised.

## Conclusion

The project successfully implements a Dropbox‑like system with a modern architecture. It demonstrates asynchronous background processing, stateless authentication, cloud object storage, a message queue, parallel uploads, rate limiting, database migrations, and embedded monitoring – all essential for a production‑ready, horizontally scalable application. The code is live on Render, and the JavaFX client works against the cloud server.