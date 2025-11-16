# CICS Sanctions Control

An Android application designed for the efficient management and tracking of student sanctions (e.g., "bottles" or "passed" requirements) within the College of Information and Computing Sciences (CICS).

## ✨ Features

* **User Management:** Easily add, edit, and delete student records.
* **Real-time Tracking:** Monitor individual student progress and overall totals for sanctions and passed requirements.
* **Dynamic Filtering:** Filter student lists by specific sections (e.g., 1A, 1B, 2A) for focused viewing.
* **Search Functionality:** Quickly find students by name (case-insensitive).
* **Admin Tools (Password Protected):**
    * **Batch CSV Import:** Bulk upload student data (including Student ID, Name, Section, and Total Sanctions) from a CSV file.
    * **Mass Deletion:** Securely delete all student records or only records within a specific section.
    * **Total Sanction Adjustment:** Admins can modify a student's total sanction count.
* **Intuitive UI:** Built with Material Design principles for a clean, user-friendly, and accessible interface.
* **Firebase Firestore Backend:** Utilizes a robust, real-time NoSQL database for seamless data synchronization.

## 🚀 Technologies Used

* **Android SDK** (Java)
* **Firebase Firestore** (Backend)
* **Material Design Components** (UI)
* **AndroidX Libraries**

## 🛠️ Setup and Installation

### Prerequisites

* Android Studio (latest version recommended)
* A Firebase Project configured for your Android app.
    * Ensure Firestore is set up and configured for your project.
    * Download `google-services.json` and place it in your `app/` directory.

### Running the Application

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/NightCode101/CICS-Sanctions-Control.git
    cd CICS-Sanctions-Control
    ```
2.  **Open in Android Studio:**
    * Open Android Studio.
    * Select `File > Open...` and navigate to the cloned project directory.
3.  **Sync Gradle:**
    * Android Studio should automatically prompt you to sync Gradle. If not, click `File > Sync Project with Gradle Files`.
4.  **Firebase Configuration:**
    * Ensure your `google-services.json` file is correctly placed.
    * **Admin Passwords:** The app requires two documents in your `config` collection in Firestore:
        * `db.collection("config").document("admin").set(Map.of("password", "your_admin_password"))`
        * `db.collection("config").document("deletion").set(Map.of("password", "your_deletion_password"))`
5.  **Run on Device/Emulator:**
    * Connect an Android device or start an emulator.
    * Click the `Run 'app'` button (green triangle) in Android Studio.

## 📈 CSV Import Format

The batch CSV import expects a 4-column format. The first row is skipped as a header.

**Format:**
`Student_ID,Student_Name,Section,Total_Sanctions`

**Example:**
```csv
StudentID,Name,Section,TotalSanctions
2020-0001,John Doe,1A,5
2020-0002,"Jane Smith, Jr.",1B,10
2020-0003,Bob Johnson,2A,2
```

**Important Notes for CSV:**
* `Student_ID` is used as the unique Document ID in Firestore.
* Names containing commas (e.g., `"Smith, Jr."`) **must** be enclosed in double quotes to be parsed correctly.

## 📝 License

This project is licensed under the MIT License. You can find the full license text in the `LICENSE` file in this repository.

-----
