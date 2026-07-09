# AI Attendance System App Workflow

The **AI Attendance System** is an offline-first Android application that combines on-device machine learning with a local SQLite database (via Room) to track attendance. Below is a detailed breakdown of the application workflows, system architecture, data models, and the machine learning pipeline.

---

## 🗺️ System Architecture Overview

```mermaid
graph TD
    UI[User Interface / Activities] <--> Room[Room DB / SQLite]
    UI <--> CameraX[CameraX API]
    CameraX --> MLKit[Google ML Kit Face Detector]
    MLKit --> Cropper[Bitmap Crop & Resize]
    Cropper --> FaceNet[FaceNet TFLite Model]
    FaceNet --> Math[L2 Distance Comparison]
    Math <--> Room
```

The application is structured into three core layers:
1. **Presentation (UI) Layer**: Handled by activities (`MainActivity`, `RegisterActivity`, `AttendanceActivity`, `RecordsActivity`, `RegisteredUsersActivity`) using **View Binding** and customized overlay rendering.
2. **Machine Learning Pipeline**: Uses **CameraX** for live image analysis, feeding frames to **Google ML Kit** for face boundary detection, then extracting the face, passing it to **FaceNet TFLite**, and L2 normalizing the outputs.
3. **Data Access Layer**: Uses a local **Room Database** containing two tables: `Person` (representing users and their 128D embeddings) and `AttendanceRecord` (representing logs).

---

## 🔄 Core Workflows

### 1. Face Registration Flow 👤

This flow registers a new user by capturing their face, generating a unique biometric embedding, and saving it to the SQLite database.

```mermaid
sequenceDiagram
    actor User
    participant RA as RegisterActivity
    participant DB as Room Database (Person Table)
    participant FA as FaceAnalyzer
    participant FN as FaceNetModel
    
    User->>RA: Enters Name & Launches Registration
    RA->>RA: Opens CameraX Preview & Analysis
    RA->>FA: Stream Frame
    FA->>FA: Detects Face Bounding Box
    FA->>RA: Updates UI overlay (Teal Border)
    User->>RA: Clicks "Capture Face"
    RA->>FN: Extract and normalize cropped 160x160 face Bitmap
    FN->>FN: Run TFLite Interpreter (128D FloatArray)
    FN-->>RA: Returns 128D Vector
    RA->>DB: Insert Person(name, faceEmbedding)
    DB-->>RA: Confirm Success
    RA->>User: Show confirmation and return to Dashboard
```

---

### 2. Take Attendance & Recognition Flow 🔍

This flow runs real-time camera analysis to detect and identify a face. If recognized, it saves a new attendance record.

```mermaid
sequenceDiagram
    actor Target
    participant AA as AttendanceActivity
    participant FA as FaceAnalyzer
    participant FN as FaceNetModel
    participant DB as Room Database
    
    Target->>AA: Approaches camera
    AA->>FA: Analyze Frame
    FA->>FA: Google ML Kit detects Face Bounding Box
    FA->>AA: Returns FaceResult (Rect, embedding)
    AA->>AA: Draw Teal Bounding Box (Face Detected)
    AA->>AA: User taps "Capture & Recognize"
    AA->>DB: Fetch all registered users' embeddings
    AA->>FN: Compare current embedding with registered embeddings (L2 Distance)
    Note over AA,FN: L2 Distance = sqrt(sum((emb1[i] - emb2[i])^2))
    alt Best Match Distance < 1.0 (Threshold)
        AA->>AA: Draw Green Bounding Box (Recognized)
        AA->>DB: Check if already marked today
        alt Already marked today
            AA->>Target: Show warning dialog (Already Recorded)
        else Not marked today
            AA->>DB: Insert AttendanceRecord(personId, name, timestamp)
            AA->>Target: Show success dialog with bounce animation
        end
    else Best Match Distance >= 1.0
        AA->>AA: Draw Red Bounding Box (Unknown Face)
        AA->>Target: Display "Face not recognized" error toast
    end
```

---

### 3. Attendance Logs & PDF Report Generation Flow 🖨️

This workflow compiles selected logs into a professional PDF report on the local device, ready for wireless printing or sharing.

```mermaid
graph TD
    A[Launch RecordsActivity] --> B[Retrieve logs from database]
    B --> C[Display logs in RecyclerView]
    C --> D{Filter Logs?}
    D -- By Date --> E[Update RecyclerView list]
    D -- Export Report --> F[Tap Printer Icon]
    F --> G{Select Range}
    G -- Selected Date --> H[Filter database logs for selected date]
    G -- Custom Range --> I[Filter database logs for date range]
    H & I --> J[Compile PDF document using Android Canvas APIs]
    J --> K[Generate layout with headers, alternating row colors, and signature lines]
    K --> L[Save PDF to local app cache folder]
    L --> M[Initiate Android Intent.ACTION_SEND with FileProvider URI]
    M --> N[Show Android System Share Sheet]
    N -- Option A --> O[Share to WhatsApp / Email]
    N -- Option B --> P[Send to Wireless Printer]
```

---

## 🧠 Machine Learning & Inference Pipeline

The core ML calculations happen locally using standard floating-point operations:

1. **Detection**: **Google ML Kit Face Detector** is configured with `PERFORMANCE_MODE_FAST` to run efficiently on mobile. It isolates the coordinates of the largest face in the current frame.
2. **Crop & Scale**: The system crops the face bounding box out of the full image bitmap, then resizes it to exactly **160 × 160 pixels** (FaceNet input size).
3. **Preprocessing**: The 160×160 RGB pixels are scaled and normalized to values between `[-1.0, 1.0]` using:
   $$\text{Normalized Value} = \frac{\text{Pixel Color Value} - 127.5}{127.5}$$
4. **Embedding Generation**: The normalized bitmap buffer is run through the `mobile_face_net.tflite` model, generating a `FloatArray` of size 128 (128-dimensional space representation of facial features).
5. **L2 Normalization**: The 128D embedding vector is normalized to unit length so that distance calculations are standardized.
6. **Matching**: The system compares the live face's embedding $A$ and a stored face's embedding $B$ using **L2 (Euclidean) distance**:
   $$d(A, B) = \sqrt{\sum_{i=1}^{128} (A_i - B_i)^2}$$
   * **Distance < 1.0**: Confirmed match.
   * **Distance >= 1.0**: Unrecognized/Unknown.

---

## 💾 Database Schema

The Room Database operates with two tables:

### 1. `Person` Table (Registered Users)
* `id` (Long, Primary Key, Auto-generate)
* `name` (String)
* `faceEmbedding` (FloatArray, converted to string/blob via TypeConverters for storage)
* `createdAt` (Long, timestamp)

### 2. `AttendanceRecord` Table (Logs)
* `id` (Long, Primary Key, Auto-generate)
* `personId` (Long, Foreign Key pointing to `Person.id` on delete CASCADE)
* `personName` (String, cached name)
* `timestamp` (Long, timestamp of attendance in milliseconds)
