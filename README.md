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
- `Apache PDFBox` (PDF rendering)
- `Apache POI` (DOCX manipulation)
- `JSoup` (HTML conversion for DOCX)

## Project Structure
The project is split into three Maven modules:

- **common** – shared DTOs, enums, and utility classes (e.g., VersionVector).
- **server** – Spring Boot application with REST APIs, JPA entities, storage backends, JWT authentication, WebSocket chat, and Redis active user tracking.
- **client** – JavaFX application containing both the sync client and the admin GUI, with all viewer/editor features, chat, and dialogs.

```
File_Synchronization_System/
├───── .env
├───── .gitignore

├───── client
│     ├───── dependency-reduced-pom.xml
│     ├───── pom.xml
│     ├───── src
│     │     ├───── main
│     │     │     ├───── java
│     │     │     │     └───── com
│     │     │     │           └───── filesync
│     │     │     │                 └───── client
│     │     │     │                       ├───── auth
│     │     │     │                       │     ├───── ConfirmResetController.java
│     │     │     │                       │     ├───── ForgotPasswordController.java
│     │     │     │                       │     ├───── RequestResetController.java
│     │     │     │                       │     └───── StartupController.java
│     │     │     │                       ├───── conflict
│     │     │     │                       │     ├───── ConflictController.java
│     │     │     │                       │     └───── ConflictResolver.java
│     │     │     │                       ├───── controller
│     │     │     │                       │     ├───── ChatController.java
│     │     │     │                       │     ├───── helper
│     │     │     │                       │     │     ├───── BreadcrumbManager.java
│     │     │     │                       │     │     ├───── BulkOperationHandler.java
│     │     │     │                       │     │     ├───── ButtonPermissionManager.java
│     │     │     │                       │     │     ├───── DragDropHandler.java
│     │     │     │                       │     │     └───── PermissionGuard.java
│     │     │     │                       │     ├───── ImageViewerController.java
│     │     │     │                       │     └───── ServerAdminApp.java
│     │     │     │                       ├───── db
│     │     │     │                       │     └───── LocalMetadataRepository.java
│     │     │     │                       ├───── dialog
│     │     │     │                       │     ├───── AddMemberDialog.java
│     │     │     │                       │     ├───── ConfirmationDialog.java
│     │     │     │                       │     ├───── ConfirmationDialogController.java
│     │     │     │                       │     ├───── CreateFileDialog.java
│     │     │     │                       │     ├───── CreateFileDialogController.java
│     │     │     │                       │     ├───── CreateFolderDialog.java
│     │     │     │                       │     ├───── CreateSharedFolderDialog.java
│     │     │     │                       │     ├───── ImageViewerDialog.java
│     │     │     │                       │     ├───── MediaPlayerDialog.java
│     │     │     │                       │     ├───── PendingRequestsDialog.java
│     │     │     │                       │     ├───── ProgressDialog.java
│     │     │     │                       │     ├───── ProgressDialogController.java
│     │     │     │                       │     ├───── RequestAccessDialog.java
│     │     │     │                       │     ├───── UploadChoiceController.java
│     │     │     │                       │     └───── UploadChoiceDialog.java
│     │     │     │                       ├───── document
│     │     │     │                       │     ├───── DocumentConverter.java
│     │     │     │                       │     ├───── DocumentViewerDialog.java
│     │     │     │                       │     ├───── DocxEditorController.java
│     │     │     │                       │     └───── PdfViewerController.java
│     │     │     │                       ├───── file
│     │     │     │                       │     ├───── FileHasher.java
│     │     │     │                       │     └───── FolderScanner.java
│     │     │     │                       ├───── files
│     │     │     │                       │     ├───── CreateFolderController.java
│     │     │     │                       │     ├───── delete
│     │     │     │                       │     ├───── download
│     │     │     │                       │     ├───── edit
│     │     │     │                       │     │     └───── EditDialogController.java
│     │     │     │                       │     ├───── FileExplorerController.java
│     │     │     │                       │     ├───── helper
│     │     │     │                       │     ├───── move
│     │     │     │                       │     ├───── ServerFileItem.java
│     │     │     │                       │     └───── upload
│     │     │     │                       ├───── http
│     │     │     │                       │     ├───── ChunkedUploader.java
│     │     │     │                       │     └───── SyncHttpClient.java
│     │     │     │                       ├───── icon
│     │     │     │                       │     └───── FileIconResolver.java
│     │     │     │                       ├───── media
│     │     │     │                       │     └───── MediaPlayerController.java
│     │     │     │                       ├───── model
│     │     │     │                       │     ├───── DragData.java
│     │     │     │                       │     └───── FileTransferData.java
│     │     │     │                       ├───── service
│     │     │     │                       │     ├───── FileOperationService.java
│     │     │     │                       │     ├───── FolderUploadService.java
│     │     │     │                       │     ├───── GlobalProgressController.java
│     │     │     │                       │     ├───── PasswordResetService.java
│     │     │     │                       │     └───── ProgressService.java
│     │     │     │                       ├───── SessionManager.java
│     │     │     │                       ├───── shared
│     │     │     │                       │     ├───── create
│     │     │     │                       │     │     └───── CreateSharedFolderController.java
│     │     │     │                       │     ├───── members
│     │     │     │                       │     │     └───── AddMemberController.java
│     │     │     │                       │     ├───── requests
│     │     │     │                       │     │     ├───── ApproveRequestsController.java
│     │     │     │                       │     │     └───── RequestAccessController.java
│     │     │     │                       │     └───── SharedFoldersController.java
│     │     │     │                       ├───── sync
│     │     │     │                       │     └───── SyncEngine.java
│     │     │     │                       ├───── task
│     │     │     │                       │     ├───── DeleteTask.java
│     │     │     │                       │     ├───── DownloadTask.java
│     │     │     │                       │     ├───── EditTask.java
│     │     │     │                       │     ├───── MoveTask.java
│     │     │     │                       │     ├───── RefreshTask.java
│     │     │     │                       │     └───── UploadTask.java
│     │     │     │                       ├───── util
│     │     │     │                       │     └───── ProjectStructurePrinter.java
│     │     │     │                       └───── websocket
│     │     │     │                             └───── ChatClient.java
│     │     │     └───── resources
│     │     │           └───── com
│     │     │                 └───── filesync
│     │     │                       └───── client
│     │     │                             ├───── auth
│     │     │                             │     ├───── confirm-reset.fxml
│     │     │                             │     ├───── send-reset-code.fxml
│     │     │                             │     ├───── startup-dialog.fxml
│     │     │                             │     └───── startup.css
│     │     │                             ├───── conflict
│     │     │                             │     └───── conflict-view.fxml
│     │     │                             ├───── css
│     │     │                             │     └───── styles.css
│     │     │                             ├───── dialog
│     │     │                             │     ├───── confirmation-dialog.fxml
│     │     │                             │     ├───── create-file-dialog.fxml
│     │     │                             │     ├───── create-folder-dialog.fxml
│     │     │                             │     ├───── General-design.css
│     │     │                             │     ├───── image-viewer.fxml
│     │     │                             │     ├───── new-folder-dialog.fxml
│     │     │                             │     ├───── progress-dialog.fxml
│     │     │                             │     └───── upload-choice-dialog.fxml
│     │     │                             ├───── document
│     │     │                             │     ├───── docx-editor.fxml
│     │     │                             │     └───── pdf-viewer.fxml
│     │     │                             ├───── files
│     │     │                             │     ├───── edit
│     │     │                             │     │     └───── edit-dialog.fxml
│     │     │                             │     └───── server-file-list.fxml
│     │     │                             ├───── icons
│     │     │                             │     ├───── audio.png
│     │     │                             │     ├───── doc.png
│     │     │                             │     ├───── file.png
│     │     │                             │     ├───── image.png
│     │     │                             │     ├───── pdf.png
│     │     │                             │     ├───── text.png
│     │     │                             │     └───── video.png
│     │     │                             ├───── media
│     │     │                             │     └───── media-player.fxml
│     │     │                             ├───── service
│     │     │                             │     └───── global-progress.fxml
│     │     │                             └───── shared
│     │     │                                   ├───── chat-view.fxml
│     │     │                                   ├───── create
│     │     │                                   │     └───── create-shared-folder.fxml
│     │     │                                   ├───── members
│     │     │                                   │     └───── add-member-dialog.fxml
│     │     │                                   ├───── requests
│     │     │                                   │     ├───── pending-requests-dialog.fxml
│     │     │                                   │     └───── request-access-dialog.fxml
│     │     │                                   └───── shared-folders-view.fxml
│     │     └───── test
│     │           └───── java
│
├───── common
│     ├───── pom.xml
│     ├───── src
│     │     ├───── main
│     │     │     ├───── java
│     │     │     │     └───── com
│     │     │     │           └───── filesync
│     │     │     │                 └───── common
│     │     │     │                       ├───── dto
│     │     │     │                       │     ├───── ChatMessage.java
│     │     │     │                       │     ├───── ChunkMetadataDto.java
│     │     │     │                       │     ├───── ConflictContextDto.java
│     │     │     │                       │     ├───── CreateFolderDto.java
│     │     │     │                       │     ├───── CreateFolderRequest.java
│     │     │     │                       │     ├───── EditSessionDto.java
│     │     │     │                       │     ├───── FileMetadataDto.java
│     │     │     │                       │     ├───── ForgotPasswordRequestDto.java
│     │     │     │                       │     ├───── MemberDto.java
│     │     │     │                       │     ├───── ResetPasswordRequestDto.java
│     │     │     │                       │     ├───── SharedFolderDto.java
│     │     │     │                       │     ├───── SyncActionDto.java
│     │     │     │                       │     ├───── SyncRequestDto.java
│     │     │     │                       │     ├───── SyncResponseDto.java
│     │     │     │                       │     ├───── UploadStatusDto.java
│     │     │     │                       │     └───── UserSearchResult.java
│     │     │     │                       ├───── enums
│     │     │     │                       │     ├───── Permission.java
│     │     │     │                       │     ├───── SyncActionType.java
│     │     │     │                       │     └───── SyncStatus.java
│     │     │     │                       └───── model
│     │     │     │                             └───── VersionVector.java
│     │     │     └───── resources
│     │     └───── test
│     │           └───── java
│
├───── context.md
├───── data
│     └───── filesyncdb.mv.db
├───── Dockerfile
├───── mvnw
├───── mvnw.cmd
├───── pom.xml
├───── README.md
├───── run-servers.bat
│
└───── server
      ├───── pom.xml
      ├───── src
      │     └───── main
      │           ├───── java
      │           │     └───── com
      │           │           └───── filesync
      │           │                 └───── server
      │           │                       ├───── config
      │           │                       │     ├───── ManualRabbitConfig.java
      │           │                       │     ├───── R2Config.java
      │           │                       │     ├───── RabbitMQConfig.java
      │           │                       │     └───── SecurityConfig.java
      │           │                       ├───── conflict
      │           │                       │     ├───── detector
      │           │                       │     │     └───── ConflictDetector.java
      │           │                       │     └───── strategy
      │           │                       │           ├───── ConflictStrategyFactory.java
      │           │                       │           ├───── ConflictStrategyInterface.java
      │           │                       │           ├───── ServerStrategyImplement.java
      │           │                       │           └───── UserStrategyImplement.java
      │           │                       ├───── consumer
      │           │                       │     └───── SyncConsumer.java
      │           │                       ├───── controller
      │           │                       │     ├───── AuthController.java
      │           │                       │     ├───── ChunkUploadController.java
      │           │                       │     ├───── FileController.java
      │           │                       │     ├───── FileTransferController.java
      │           │                       │     ├───── HealthController.java
      │           │                       │     ├───── SharedFolderController.java
      │           │                       │     ├───── SyncController.java
      │           │                       │     └───── UserController.java
      │           │                       ├───── debug
      │           │                       ├───── domain
      │           │                       │     ├───── ChatMessageEntity.java
      │           │                       │     ├───── FileMetadataEntity.java
      │           │                       │     ├───── SharedFolderEntity.java
      │           │                       │     ├───── SharedFolderMemberEntity.java
      │           │                       │     ├───── SharedFolderRequestEntity.java
      │           │                       │     ├───── SyncTask.java
      │           │                       │     └───── User.java
      │           │                       ├───── dto
      │           │                       │     └───── SyncMessage.java
      │           │                       ├───── filter
      │           │                       │     └───── RateLimitFilter.java
      │           │                       ├───── repository
      │           │                       │     ├───── ChatMessageRepository.java
      │           │                       │     ├───── FileMetadataRepository.java
      │           │                       │     ├───── SharedFolderMemberRepository.java
      │           │                       │     ├───── SharedFolderRepository.java
      │           │                       │     ├───── SharedFolderRequestRepository.java
      │           │                       │     ├───── SyncTaskRepository.java
      │           │                       │     └───── UserRepository.java
      │           │                       ├───── security
      │           │                       │     ├───── JwtAuthenticationFilter.java
      │           │                       │     └───── JwtService.java
      │           │                       ├───── ServerApplication.java
      │           │                       ├───── service
      │           │                       │     ├───── EditLogicInterface.java
      │           │                       │     ├───── EmailService.java
      │           │                       │     ├───── FileContentService.java
      │           │                       │     ├───── FileMetaDataService.java
      │           │                       │     ├───── HashCalculator.java
      │           │                       │     ├───── PermissionService.java
      │           │                       │     ├───── SharedFolderService.java
      │           │                       │     ├───── SyncTaskStatusService.java
      │           │                       │     └───── UserFindService.java
      │           │                       ├───── storage
      │           │                       │     ├───── ChunkStorageService.java
      │           │                       │     ├───── FileStorage.java
      │           │                       │     ├───── LocalDiskChunkStorage.java
      │           │                       │     ├───── LocalFileStorage.java
      │           │                       │     ├───── R2ChunkStorageService.java
      │           │                       │     ├───── R2StorageService.java
      │           │                       │     └───── RedisUploadStateService.java
      │           │                       ├───── web
      │           │                       │     ├───── FileBrowserController.java
      │           │                       │     └───── TestController.java
      │           │                       └───── websocket
      │           │                             ├───── AuthChannelInterceptor.java
      │           │                             ├───── config
      │           │                             │     └───── WebSocketConfig.java
      │           │                             ├───── controller
      │           │                             │     └───── ChatController.java
      │           │                             └───── service
      │           │                                   ├───── ActiveUserService.java
      │           │                                   └───── RedisActiveUserService.java
      │           └───── resources
      │                 ├───── application.properties
      │                 ├───── db
      │                 │     └───── migration
      │                 │           ├───── V1__initial_schema.sql
      │                 │           ├───── V2__add_shared_folders.sql
      │                 │           ├───── V3__add_unique_constraint_shared_folder_name.sql
      │                 │           ├───── V4__add_directory_support.sql
      │                 │           ├───── V5__compute_folder_sizes.sql
      │                 │           ├───── V6__add_requested_permission_to_requests.sql
      │                 │           └───── V7__create_chat_messages.sql
      │                 └───── templates
      │                       ├───── conflict.html
      │                       ├───── edit.html
      │                       ├───── files.html
      │                       ├───── forgot-password.html
      │                       ├───── login.html
      │                       ├───── register.html
      │                       ├───── reset-password.html
      │                       └───── upload.html
            
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
- **Permission field in file metadata** – The endpoint `GET /api/files/user/{ownerId}` now returns a `userPermission` field (`READ`, `WRITE`, or `NONE`) for each file, allowing the client to enforce fine‑grained access control without additional round trips.

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
- **Global progress indicator** – A top‑right status bar shows the current operation (upload, download, delete, move, edit, folder upload, refresh) with real‑time progress. The bar is non‑blocking, follows the observer pattern, and buttons are automatically disabled during operations to prevent concurrent actions.
- **Breadcrumb path display** – A label above the file table shows the current navigation path (e.g., `My Files / Documents / Work`). It updates when entering folders, going up, or exiting shared folders, providing clear context.
- **Direct text file editing via double‑click** – Users can double‑click any `.txt` file to open the editor immediately. The Edit button has been removed to declutter the interface. Write permission is enforced client‑side before opening the editor.
- **Permission‑sensitive buttons** – The Delete and Download buttons are dynamically enabled/disabled based on the selected file’s `userPermission` (WRITE for delete, READ/WRITE for download). This prevents users from attempting unauthorized actions.
- **Refactored FileExplorerController** – The controller has been split into helper classes (`DragDropHandler`, `BreadcrumbManager`, `ButtonPermissionManager`, `BulkOperationHandler`), improving maintainability and adhering to the Single Responsibility Principle.
- **Integrated media player** – Supports playback of audio (MP3, WAV) and video (MP4, AVI, MOV, MKV) files directly inside the admin GUI. The player window includes play/pause, seek slider with click‑to‑seek, volume control, rewind/forward (10 seconds), and a time display. If JavaFX cannot decode the file (e.g., due to missing codecs), the player automatically falls back to the system’s default media player, ensuring reliable playback on any Windows system.
- **Write‑permission enforcement for editing** – The Edit button has been removed; users can double‑click any `.txt` file to open the editor, but only if they have WRITE permission. Read‑only files cannot be edited, and an alert explains the restriction.
- **Permission‑sensitive buttons** – The Delete button is enabled only for files/folders with WRITE permission; the Download button is enabled for READ or WRITE permission. This prevents users from attempting unauthorised actions.
- **PDF viewer** – Double‑click a PDF file to open a dedicated viewer with page navigation, zoom in/out, and fit‑to‑width controls. Large PDFs are rendered page‑by‑page with caching for smooth navigation.
- **DOCX editor** – Double‑click a DOCX file to open a rich text editor (based on JavaFX HTMLEditor). Supports bold, italic, underline, and plain text editing. Changes are saved back to the server with full conflict detection.
- **Real‑time chat in shared folders** – When a shared folder is opened, a chat tab appears alongside the file explorer. Users can exchange messages in real time, see a list of active collaborators (updated automatically), and view message history when they re‑enter the folder. The chat uses WebSocket (STOMP) with RabbitMQ for horizontal scalability and Redis for tracking active users. Stale active users are cleaned up automatically after a configurable timeout.
- **Image viewer** – Double‑click image files (`.png`, `.jpg`, `.jpeg`, `.gif`, `.bmp`) to open a dedicated viewer with zoom, fit‑to‑window, and original size controls. The image is downloaded progressively and displayed with panning support.
- **Create new file** – A “New File” button opens a dialog where users can enter a file name and choose an extension (`.txt`, `.json`, `.xml`, `.html`, `.css`, `.js`, `.md`, `.csv`, `.yml`, `.properties`, `.docx`). The server receives a properly initialised file (empty for most formats; for `.docx`, a minimal valid document is generated).
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
- **Real‑time chat** – Each shared folder has its own chat room where members can coordinate and discuss changes. Messages are persisted and displayed as history when a user rejoins the folder. Active collaborators are shown in real time, with stale entries removed automatically.

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
- **Real‑time WebSocket messaging** – Chat messages are broadcast via RabbitMQ, enabling seamless scaling across multiple server instances.
- **Active user tracking with Redis** – Tracks which users are currently viewing a shared folder; stale entries are cleaned up by a scheduled job to prevent ghost users.

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

The project successfully implements a Dropbox‑like system with a modern architecture. It demonstrates asynchronous background processing, stateless authentication, cloud object storage, a message queue, parallel uploads, rate limiting, database migrations, and embedded monitoring – all essential for a production‑ready, horizontally scalable application. The system is fully self‑contained and ready to be deployed on any infrastructure.