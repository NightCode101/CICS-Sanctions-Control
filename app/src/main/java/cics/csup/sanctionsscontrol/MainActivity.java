package cics.csup.sanctionsscontrol;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements UserAdapter.OnUserInteractionListener {

    private FirebaseFirestore db;
    private UserAdapter adapter;
    private RecyclerView recyclerViewUsers;

    private TextView textViewTotalBottles;
    private TextView textViewTotalPassed;
    private AutoCompleteTextView autoCompleteTextViewSection;
    private TextView textViewEmptyList;
    private androidx.appcompat.widget.SearchView searchViewName;
    private String currentSearchText = "";
    private String currentSection = "1A"; // Default section
    private ListenerRegistration dataListener;
    private androidx.activity.result.ActivityResultLauncher<String[]> filePickerLauncher;
    private List<User> allUsersFromQuery = new ArrayList<>();
    private List<DocumentSnapshot> allSnapshotsFromQuery = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- 1. Initialize Database ---
        db = FirebaseFirestore.getInstance();

        // --- 2. Find ALL Views First ---
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        recyclerViewUsers = findViewById(R.id.recyclerViewUsers);
        textViewTotalBottles = findViewById(R.id.textViewTotalBottles);
        textViewTotalPassed = findViewById(R.id.textViewTotalPassed);
        autoCompleteTextViewSection = findViewById(R.id.autoCompleteTextViewSection);
        textViewEmptyList = findViewById(R.id.textViewEmptyList);
        searchViewName = findViewById(R.id.searchViewName);
        com.google.android.material.floatingactionbutton.FloatingActionButton fabAddUser;
        fabAddUser = findViewById(R.id.fabAddUser);

        // --- 3. Now You Can Use Them ---
        setSupportActionBar(toolbar);
        autoCompleteTextViewSection.setText(currentSection, false);
        initializeFilePicker();

        // --- 4. Set up Listeners and Adapters ---
        setupRecyclerView();
        setupSpinnerListener();
        setupSearchListener();
        fabAddUser.setOnClickListener(v -> showAddUserDialog());

        // Show the "Created by" Snackbar
        showSnackbar(getString(R.string.creator_credit));
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_upload_batch) {
            showImportPasswordDialog();
            return true;
        } else if (itemId == R.id.action_refresh) {
            loadDataFromFirestore();
            showSnackbar(getString(R.string.toast_refreshing));
            return true;
        } else if (itemId == R.id.delete_all_users) {
            showDeleteAllPasswordDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        adapter = new UserAdapter(this, textViewEmptyList);
        recyclerViewUsers.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewUsers.setAdapter(adapter);
    }

    private void initializeFilePicker() {
        filePickerLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        Log.d("BatchUpload", "File URI received: " + uri);
                        readCsvFileAndUpload(uri);
                    } else {
                        Log.d("BatchUpload", "No file selected.");
                    }
                });
    }

    private void setupSpinnerListener() {
        String[] sections = getResources().getStringArray(R.array.sections_array);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, sections
        );
        autoCompleteTextViewSection.setAdapter(adapter);

        autoCompleteTextViewSection.setOnItemClickListener((parent, view, position, id) -> {
            currentSection = parent.getItemAtPosition(position).toString();
            loadDataFromFirestore();
        });
    }

    private void setupSearchListener() {
        searchViewName.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentSearchText = query;
                filterAndDisplayList();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchText = newText;
                filterAndDisplayList();
                return false;
            }
        });
    }

    private void loadDataFromFirestore() {
        if (dataListener != null) {
            dataListener.remove();
        }

        Query query = db.collection("users");
        String allSectionsString = getString(R.string.default_all_sections);

        if (currentSection.equals(allSectionsString)) {
            Log.d("MainActivity", "Loading 'All Sections' with .get()");
            query = query.orderBy("name", Query.Direction.ASCENDING);

            query.get().addOnSuccessListener(queryDocumentSnapshots -> {
                long totalBottles = 0;
                long totalPassed = 0;
                allUsersFromQuery.clear();
                allSnapshotsFromQuery.clear();

                if (queryDocumentSnapshots != null) {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        User user = doc.toObject(User.class);
                        allUsersFromQuery.add(user);
                        allSnapshotsFromQuery.add(doc);
                        totalBottles += user.getTotalSanctions();
                        totalPassed += user.getPassedBottles();
                    }
                }

                textViewTotalBottles.setText(getString(R.string.total_bottles_format, totalBottles));
                textViewTotalPassed.setText(getString(R.string.total_passed_format, totalPassed));
                filterAndDisplayList();

            }).addOnFailureListener(e -> {
                Log.e("MainActivity", "Failed to get 'All Sections'", e);
            });

        } else {
            Log.d("MainActivity", "Loading '" + currentSection + "' with real-time listener");
            query = query.whereEqualTo("section", currentSection)
                    .orderBy("name", Query.Direction.ASCENDING);

            dataListener = query.addSnapshotListener((value, error) -> {
                if (error != null) {
                    Log.w("MainActivity", "Data listener error", error);
                    return;
                }

                long totalBottles = 0;
                long totalPassed = 0;
                allUsersFromQuery.clear();
                allSnapshotsFromQuery.clear();

                if (value != null) {
                    for (QueryDocumentSnapshot doc : value) {
                        User user = doc.toObject(User.class);
                        allUsersFromQuery.add(user);
                        allSnapshotsFromQuery.add(doc);
                        totalBottles += user.getTotalSanctions();
                        totalPassed += user.getPassedBottles();
                    }
                }

                textViewTotalBottles.setText(getString(R.string.total_bottles_format, totalBottles));
                textViewTotalPassed.setText(getString(R.string.total_passed_format, totalPassed));
                filterAndDisplayList();
            });
        }
    }

    private void filterAndDisplayList() {
        List<User> filteredUsers = new ArrayList<>();
        List<DocumentSnapshot> filteredSnapshots = new ArrayList<>();

        if (currentSearchText.isEmpty()) {
            filteredUsers.addAll(allUsersFromQuery);
            filteredSnapshots.addAll(allSnapshotsFromQuery);
        } else {
            String lowerCaseQuery = currentSearchText.toLowerCase();
            for (int i = 0; i < allUsersFromQuery.size(); i++) {
                User user = allUsersFromQuery.get(i);
                if (user.getName().toLowerCase().contains(lowerCaseQuery)) {
                    filteredUsers.add(user);
                    filteredSnapshots.add(allSnapshotsFromQuery.get(i));
                }
            }
        }
        adapter.setData(filteredUsers, filteredSnapshots);
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadDataFromFirestore();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (dataListener != null) {
            dataListener.remove();
        }
    }

    // --- All Dialog Methods ---

    @Override
    public void onEditClick(com.google.firebase.firestore.DocumentSnapshot documentSnapshot) {
        showEditUserDialog(documentSnapshot);
    }

    @Override
    public void onUserLongPress(com.google.firebase.firestore.DocumentSnapshot documentSnapshot) {
        showEditUserDialog(documentSnapshot);
    }

    private void showAddUserDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_user, null);

        final com.google.android.material.textfield.TextInputEditText editTextUserName = dialogView.findViewById(R.id.editTextUserName);
        final com.google.android.material.textfield.TextInputEditText editTextTotalSanctions = dialogView.findViewById(R.id.editTextTotalSanctions);
        final AutoCompleteTextView autoCompleteSection = dialogView.findViewById(R.id.autoCompleteTextViewNewUserSection);

        String[] sections = getResources().getStringArray(R.array.add_user_sections_array);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                sections
        );
        autoCompleteSection.setAdapter(adapter);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_add_title))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.button_add), (dialog, which) -> {
                    String name = editTextUserName.getText().toString().trim();
                    String totalSanctionsStr = editTextTotalSanctions.getText().toString().trim();
                    String section = autoCompleteSection.getText().toString().trim();

                    if (name.isEmpty()) {
                        showSnackbar(getString(R.string.toast_name_empty));
                        return;
                    }
                    if (totalSanctionsStr.isEmpty()) {
                        showSnackbar(getString(R.string.toast_sanctions_empty));
                        return;
                    }

                    String selectSectionString = getResources().getStringArray(R.array.add_user_sections_array)[0];
                    if (section.equals(selectSectionString) || section.isEmpty()) {
                        showSnackbar(getString(R.string.toast_section_empty));
                        return;
                    }

                    long totalSanctions = Long.parseLong(totalSanctionsStr);
                    User newUser = new User(name, section, totalSanctions, 0);

                    db.collection("users").add(newUser);
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    private void showEditUserDialog(com.google.firebase.firestore.DocumentSnapshot documentSnapshot) {
        final User user = documentSnapshot.toObject(User.class);
        final String userId = documentSnapshot.getId();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_user, null);

        TextView textViewEditTitle = dialogView.findViewById(R.id.textViewEditTitle);
        final com.google.android.material.textfield.TextInputEditText editTextAddPassed = dialogView.findViewById(R.id.editTextAddPassed);
        final com.google.android.material.textfield.TextInputEditText editTextNewTotal = dialogView.findViewById(R.id.editTextNewTotal);
        final com.google.android.material.textfield.TextInputEditText editTextAdminPassword = dialogView.findViewById(R.id.editTextAdminPassword);
        final com.google.android.material.textfield.TextInputEditText editTextRemovePassed = dialogView.findViewById(R.id.editTextRemovePassed);

        if (user != null) {
            textViewEditTitle.setText(getString(R.string.dialog_edit_title_format, user.getName()));
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(getString(R.string.button_save), (dialog, which) -> {
                    String addPassedStr = editTextAddPassed.getText().toString().trim();
                    String removePassedStr = editTextRemovePassed.getText().toString().trim();
                    String newTotalStr = editTextNewTotal.getText().toString().trim();
                    String adminPassStr = editTextAdminPassword.getText().toString().trim();

                    Map<String, Object> updates = new HashMap<>();
                    long bottlesToAdd = 0;
                    long bottlesToRemove = 0;

                    if (!addPassedStr.isEmpty()) {
                        try { bottlesToAdd = Long.parseLong(addPassedStr); } catch (NumberFormatException e) { /* ignore */ }
                    }
                    if (!removePassedStr.isEmpty()) {
                        try { bottlesToRemove = Long.parseLong(removePassedStr); } catch (NumberFormatException e) { /* ignore */ }
                    }

                    long netChange = bottlesToAdd - bottlesToRemove;
                    if (netChange != 0 && user != null) {
                        long newPassedBottles = user.getPassedBottles() + netChange;
                        if (newPassedBottles < 0) newPassedBottles = 0;
                        updates.put("passedBottles", newPassedBottles);
                    }

                    if (!newTotalStr.isEmpty()) {
                        if (adminPassStr.isEmpty()) {
                            showSnackbar(getString(R.string.toast_admin_pass_required_total));
                        } else {
                            db.collection("config").document("admin").get()
                                    .addOnSuccessListener(adminDoc -> {
                                        String correctPassword = adminDoc.getString("password");
                                        if (correctPassword != null && correctPassword.equals(adminPassStr)) {
                                            try {
                                                long newTotal = Long.parseLong(newTotalStr);
                                                updates.put("totalSanctions", newTotal);
                                                if (!updates.isEmpty()) updateUserDocument(userId, updates);
                                            } catch (NumberFormatException e) {
                                                showSnackbar(getString(R.string.toast_invalid_number_total));
                                            }
                                        } else {
                                            showSnackbar(getString(R.string.toast_wrong_admin_password));
                                        }
                                    });
                        }
                    } else if (!updates.isEmpty()) {
                        updateUserDocument(userId, updates);
                    }
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .setNeutralButton(getString(R.string.button_delete), (dialog, which) -> {
                    String adminPassStr = editTextAdminPassword.getText().toString().trim();
                    if (adminPassStr.isEmpty()) {
                        showSnackbar(getString(R.string.toast_admin_pass_required_delete));
                        return;
                    }
                    db.collection("config").document("admin").get()
                            .addOnSuccessListener(adminDoc -> {
                                String correctPassword = adminDoc.getString("password");
                                if (correctPassword != null && correctPassword.equals(adminPassStr) && user != null) {
                                    new androidx.appcompat.app.AlertDialog.Builder(this)
                                            .setTitle(getString(R.string.dialog_delete_user_title))
                                            .setMessage(String.format(getString(R.string.dialog_delete_user_message), user.getName()))
                                            .setPositiveButton(getString(R.string.button_delete), (deleteDialog, deleteWhich) -> {
                                                db.collection("users").document(userId).delete()
                                                        .addOnSuccessListener(aVoid -> showSnackbar(getString(R.string.toast_user_deleted)))
                                                        .addOnFailureListener(e -> showSnackbar(getString(R.string.toast_delete_user_error)));
                                            })
                                            .setNegativeButton(getString(R.string.button_cancel), null)
                                            .show();
                                } else if (user == null) {
                                    Log.e("EditDialog", "User object was null, cannot delete.");
                                }
                                else {
                                    showSnackbar(getString(R.string.toast_wrong_admin_password));
                                }
                            });
                })
                .show();
    }

    private void updateUserDocument(String userId, java.util.Map<String, Object> updates) {
        db.collection("users").document(userId).update(updates)
                .addOnSuccessListener(aVoid -> showSnackbar(getString(R.string.toast_user_updated)))
                .addOnFailureListener(e -> showSnackbar(getString(R.string.toast_update_failed)));
    }

    // --- Admin/Import/Delete Methods ---

    private void showImportPasswordDialog() {
        final android.widget.EditText passwordInput = new com.google.android.material.textfield.TextInputEditText(this);
        passwordInput.setHint(getString(R.string.hint_admin_password));
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        final android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new  android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(50, 20, 50, 20);
        passwordInput.setLayoutParams(params);
        container.addView(passwordInput);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_admin_title))
                .setMessage(getString(R.string.dialog_import_message))
                .setView(container)
                .setPositiveButton(getString(R.string.button_continue), (dialog, which) -> {
                    String pass = passwordInput.getText().toString().trim();
                    if (pass.isEmpty()) {
                        showSnackbar(getString(R.string.toast_password_empty));
                    } else {
                        checkPasswordAndLaunchPicker(pass);
                    }
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    private void checkPasswordAndLaunchPicker(String passwordToCheck) {
        db.collection("config").document("admin").get()
                .addOnSuccessListener(adminDoc -> {
                    String correctPassword = adminDoc.getString("password");
                    if (correctPassword != null && correctPassword.equals(passwordToCheck)) {
                        Log.d("BatchUpload", "Password correct, launching file picker...");
                        filePickerLauncher.launch(new String[]{"text/csv", "text/*"});
                    } else {
                        showSnackbar(getString(R.string.toast_wrong_admin_password));
                    }
                })
                .addOnFailureListener(e -> {
                    showSnackbar(getString(R.string.toast_password_check_error));
                });
    }

    private void showDeleteAllPasswordDialog() {
        final android.widget.EditText passwordInput = new com.google.android.material.textfield.TextInputEditText(this);
        passwordInput.setHint(getString(R.string.hint_admin_password));
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        final android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new  android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(50, 20, 50, 20);
        passwordInput.setLayoutParams(params);
        container.addView(passwordInput);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_delete_all_title_danger))
                .setMessage(getString(R.string.dialog_delete_all_message))
                .setView(container)
                .setPositiveButton(getString(R.string.button_continue), (dialog, which) -> {
                    String pass = passwordInput.getText().toString().trim();
                    if (pass.isEmpty()) {
                        showSnackbar(getString(R.string.toast_password_empty));
                    } else {
                        checkPasswordAndShowFinalConfirmation(pass);
                    }
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    private void checkPasswordAndShowFinalConfirmation(String passwordToCheck) {
        final String sectionToDelete = currentSection;
        final String allSectionsString = getString(R.string.default_all_sections);

        db.collection("config").document("deletion").get()
                .addOnSuccessListener(adminDoc -> {
                    String correctPassword = adminDoc.getString("password");
                    if (correctPassword != null && correctPassword.equals(passwordToCheck)) {

                        Query countQuery = db.collection("users");
                        if (!sectionToDelete.equals(allSectionsString)) {
                            countQuery = countQuery.whereEqualTo("section", sectionToDelete);
                        }

                        countQuery.get().addOnSuccessListener(queryDocumentSnapshots -> {
                            int count = queryDocumentSnapshots.size();

                            String title = getString(R.string.dialog_delete_all_confirm_title);
                            String message;
                            String buttonText;

                            if (sectionToDelete.equals(allSectionsString)) {
                                message = String.format(getString(R.string.dialog_delete_all_confirm_message_all), count);
                                buttonText = getString(R.string.dialog_delete_all_confirm_button_all);
                            } else {
                                message = String.format(getString(R.string.dialog_delete_all_confirm_message_section), count, sectionToDelete);
                                buttonText = String.format(getString(R.string.dialog_delete_all_confirm_button_section), sectionToDelete);
                            }

                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle(title)
                                    .setMessage(message)
                                    .setPositiveButton(buttonText, (dialog, which) -> {
                                        deleteAllUsers(sectionToDelete);
                                    })
                                    .setNegativeButton(getString(R.string.button_cancel), null)
                                    .show();
                        });

                    } else {
                        showSnackbar(getString(R.string.toast_wrong_deletion_password));
                    }
                })
                .addOnFailureListener(e -> {
                    showSnackbar(getString(R.string.toast_password_check_error));
                });
    }

    private void deleteAllUsers(String sectionToDelete) {
        showSnackbar(getString(R.string.toast_deleting_all));

        Query query = db.collection("users");
        String allSectionsString = getString(R.string.default_all_sections);

        if (!sectionToDelete.equals(allSectionsString)) {
            Log.d("DeleteAll", "Deleting users from section: " + sectionToDelete);
            query = query.whereEqualTo("section", sectionToDelete);
        } else {
            Log.d("DeleteAll", "Deleting ALL users from ALL sections.");
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        showSnackbar(getString(R.string.toast_no_data_to_delete));
                        return;
                    }

                    Log.d("DeleteAll", "Found " + queryDocumentSnapshots.size() + " documents to delete.");

                    List<DocumentSnapshot> documents = queryDocumentSnapshots.getDocuments();
                    int batchSize = 490;

                    for (int i = 0; i < documents.size(); i += batchSize) {
                        List<DocumentSnapshot> batchList = documents.subList(i, Math.min(i + batchSize, documents.size()));
                        com.google.firebase.firestore.WriteBatch batch = db.batch();

                        for (DocumentSnapshot doc : batchList) {
                            batch.delete(doc.getReference());
                        }

                        final int batchNumber = (i / batchSize) + 1;
                        final boolean isLastBatch = (i + batchSize >= documents.size());

                        batch.commit()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d("DeleteAll", "Batch " + batchNumber + " deleted successfully.");
                                    if (isLastBatch) {
                                        showSnackbar(getString(R.string.toast_all_data_deleted));
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("DeleteAll", "Batch " + batchNumber + " failed to delete", e);
                                    showSnackbar(String.format(getString(R.string.toast_delete_batch_error), batchNumber));
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    showSnackbar(getString(R.string.toast_delete_get_data_failed));
                    Log.e("DeleteAll", "Failed to get documents", e);
                });
    }

    private void readCsvFileAndUpload(Uri uri) {
        Log.d("BatchUpload", "Starting to read 4-column CSV file...");
        Map<String, User> userMap = new HashMap<>();

        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            boolean isFirstLine = true;
            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                int lastCommaIndex = line.lastIndexOf(',');
                if (lastCommaIndex == -1) continue;
                String totalSanctionsStr = line.substring(lastCommaIndex + 1).trim();
                String remaining1 = line.substring(0, lastCommaIndex).trim();

                int secondLastCommaIndex = remaining1.lastIndexOf(',');
                if (secondLastCommaIndex == -1) continue;
                String section = remaining1.substring(secondLastCommaIndex + 1).trim();
                String remaining2 = remaining1.substring(0, secondLastCommaIndex).trim();

                int firstCommaIndex = remaining2.indexOf(',');
                if (firstCommaIndex == -1) continue;
                String studentId = remaining2.substring(0, firstCommaIndex).trim();
                String name = remaining2.substring(firstCommaIndex + 1).trim();

                if (name.startsWith("\"") && name.endsWith("\"") && name.length() > 1) {
                    name = name.substring(1, name.length() - 1);
                }
                if (studentId.startsWith("\"") && studentId.endsWith("\"") && studentId.length() > 1) {
                    studentId = studentId.substring(1, studentId.length() - 1);
                }
                if (section.startsWith("\"") && section.endsWith("\"") && section.length() > 1) {
                    section = section.substring(1, section.length() - 1);
                }

                long totalSanctions = 0;
                try {
                    totalSanctions = Long.parseLong(totalSanctionsStr);
                } catch (NumberFormatException e) {
                    Log.w("BatchUpload", "Invalid number: " + totalSanctionsStr + ". Defaulting to 0.");
                }

                if (!studentId.isEmpty() && !name.isEmpty() && !section.isEmpty()) {
                    User user = new User(name, section, totalSanctions, 0);
                    userMap.put(studentId, user);
                }
            }

        } catch (IOException e) {
            Log.e("BatchUpload", "Error reading CSV file", e);
            showSnackbar(getString(R.string.toast_csv_read_error));
            return;
        }

        Log.d("BatchUpload", "CSV Parsed. " + userMap.size() + " users found. Starting upload...");
        if (userMap.isEmpty()) {
            showSnackbar(getString(R.string.toast_csv_no_users));
            return;
        }

        List<Map.Entry<String, User>> entryList = new ArrayList<>(userMap.entrySet());
        int batchSize = 490;

        for (int i = 0; i < entryList.size(); i += batchSize) {
            List<Map.Entry<String, User>> batchList = entryList.subList(i, Math.min(i + batchSize, entryList.size()));
            com.google.firebase.firestore.WriteBatch batch = db.batch();

            for (Map.Entry<String, User> entry : batchList) {
                String studentId = entry.getKey();
                User user = entry.getValue();
                com.google.firebase.firestore.DocumentReference docRef = db.collection("users").document(studentId);
                batch.set(docRef, user);
            }

            final int batchNumber = (i / batchSize) + 1;
            batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d("BatchUpload", "Batch " + batchNumber + " uploaded successfully!");
                        showSnackbar(String.format(getString(R.string.toast_batch_success), batchNumber));
                    })
                    .addOnFailureListener(e -> {
                        Log.e("BatchUpload", "Batch " + batchNumber + " FAILED: ", e);
                        showSnackbar(String.format(getString(R.string.toast_batch_failed), batchNumber));
                    });
        }
    }

    // Shows a styled Snackbar message.
    private void showSnackbar(String message) {
        // Find the main layout
        View coordinatorLayout = findViewById(R.id.mainCoordinatorLayout);
        if (coordinatorLayout == null) {
            // Fallback just in case
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }

        com.google.android.material.snackbar.Snackbar.make(coordinatorLayout, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setBackgroundTint(getColor(R.color.secondary_accent)) // Use your theme color!
                .setTextColor(getColor(R.color.white))
                .show();
    }
}