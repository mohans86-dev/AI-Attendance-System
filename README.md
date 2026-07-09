# AI Attendance System 📱🤖

A modern, offline-first Android application that leverages **Google ML Kit Face Detection** and a local **FaceNet TFLite Model** to mark attendance via face recognition.

## 📸 Screenshots

<div align="center">

<table>
  <tr>
    <th>🏠 Dashboard</th>
    <th>👤 Face Registration</th>
    <th>📷 Attendance Scanner</th>
  </tr>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/6f58d930-ce54-4bd7-8236-26de4caf828a" width="220"/>
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/9ed5738d-2ff9-4121-8f98-258a258320bf" width="220"/>
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/04230bff-301e-4232-8f70-aad3e1005d49" width="220"/>
    </td>
  </tr>

  <tr>
    <th>✅ Attendance Dialog</th>
    <th>📋 Attendance Records</th>
    <th>📄 PDF Report</th>
  </tr>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/a3212c14-8b3b-4ab9-90ba-c1624c0899e1" width="220"/>
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/37397407-6bd5-4db9-b563-809a3b6aa379" width="220"/>
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/682c1c5c-06f1-4913-bff3-36150524915c" width="220"/>
    </td>
  </tr>
</table>

</div>

## 🚀 Features

- 👤 **Face Registration**: Capture new faces, extract 128-dimension embeddings, and register profiles with names locally.
- 🔍 **Real-Time Recognition**: Detects faces in camera frames, processes embeddings, matches them against the local SQLite database, and marks attendance.
- 🔁 **Dual Camera Support**: Switch seamlessly between front and back cameras with animated button controls.
- 🔔 **Interactive Feedback**: Displays clean overlays around detected faces (indicating recognition status with green, red, or teal) and triggers bounce-animated status dialogs upon marking.
- 🖨️ **Report Printing & Export**:
  - Export attendance logs to A4 PDF documents.
  - Supports **Selected Date** or **Custom Date Ranges**.
  - Formatted tables with alternating row colors, summary stats, headers, and signature fields.
  - Integrates Android's system share sheet for sharing (WhatsApp, Email) and wireless printing.
- 🔒 **Privacy First**: All calculations, face detection, and database storage (SQLite + Room) happen entirely offline on the device.

---

## 🛠️ Technology Stack

- **Platform**: Android (Kotlin, Min SDK 24, Target SDK 35)
- **UI Architecture**: View Binding, Custom Views (Drawing face bounding boxes in real-time)
- **Machine Learning**: 
  - Google ML Kit (Face Detection API)
  - FaceNet (TensorFlow Lite model for generating 128D embeddings)
- **Database**: Room Database (SQLite)
- **Camera Core**: CameraX API (ImageAnalysis & Preview)
- **Asynchronous Flow**: Kotlin Coroutines & LiveData

---

## 📁 Project Structure

```
AIAttendanceSystem/
│
├── app/
│   ├── src/main/java/com/example/aiattendancesystem/
│   │   ├── data/            # Room Database entities, DAO, AppDatabase
│   │   ├── ml/              # FaceAnalyzer (ML Kit) & FaceNetModel (TFLite)
│   │   ├── ui/              # Activities, Custom Views (FaceOverlayView), Adapters
│   │   └── util/            # Report Generator (PDF builder)
│   │
│   ├── src/main/res/        # Layout XMLs, Drawables, Themes, FileProvider paths
│   └── build.gradle.kts     # App build configuration and dependencies
│
└── build.gradle.kts         # Root build configuration
```

---

## ⚙️ Setup & Installation

### Prerequisites
- Android Studio Jellyfish (or newer)
- Java JDK 17 or higher
- Android Device (recommended) or Emulator with camera access

### Getting Started
1. Clone the repository:
   ```bash
   git clone https://github.com/mohans86-dev/AI-Attendance-System.git
   cd AI-Attendance-System
   ```
2. Open the project in Android Studio.
3. Sync project with Gradle Files.
4. Run the app on your device/emulator.

---

## 📋 How to Use

### 1. Register a Face
- Tap **Register Face** on the dashboard.
- Enter the person's name.
- Align their face in the camera view and tap **Capture Face**.
- The face is cropped, the embedding is stored in the local database, and the profile is saved.

### 2. Mark Attendance
- Tap **Take Attendance** on the dashboard.
- Point the camera at a registered person.
- Once the bounding box turns Green (indicating a match) or Teal (ready), tap **Capture & Recognize**.
- A custom modal dialog with a bounce effect will confirm the attendance status (Marked Present or Already Recorded).

### 3. View & Export Records
- Go to **View Records** from the home screen.
- Filter by date or view the logs.
- Tap the **Printer icon** 🖨️ in the top bar.
- Choose **Selected Date** or **Custom Date Range** to compile the PDF.
- Share or print the generated document directly from the Android Share Sheet.

