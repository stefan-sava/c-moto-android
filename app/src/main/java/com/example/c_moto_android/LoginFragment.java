package com.example.c_moto_android;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

public class LoginFragment extends Fragment {

    private static final int RC_SIGN_IN = 9001;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private Button mLoginButton;
    private TextView mUserInfoTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        // Configurare Google Sign In
        mGoogleSignInClient = configureGoogleSignIn();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);
        mLoginButton = view.findViewById(R.id.login_button);
        mUserInfoTextView = view.findViewById(R.id.user_info_textview);

        FirebaseUser user = mAuth.getCurrentUser();
        updateUI(user);

        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signInWithGoogle();
            }
        });
        return view;
    }

    private GoogleSignInClient configureGoogleSignIn() {
        // Configurare Google Sign In Options
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        // Creare și returnare GoogleSignInClient
        return GoogleSignIn.getClient(requireContext(), gso);
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Rezultatul întoarcerii de la activitatea de autentificare Google
        if (requestCode == RC_SIGN_IN) {
            GoogleSignIn.getSignedInAccountFromIntent(data)
                    .addOnCompleteListener(task -> {
                        try {
                            // Autentificare cu succes, actualizare UI cu datele utilizatorului
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            firebaseAuthWithGoogle(account.getIdToken());
                        } catch (ApiException e) {
                            // Autentificare eșuată, afișare mesaj de eroare
                            Toast.makeText(getContext(), "Autentificare eșuată", Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }
                    });
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(getActivity(), task -> {
                    if (task.isSuccessful()) {
                        // Semnare cu succes, actualizare UI cu datele utilizatorului semnat
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Verificare dacă utilizatorul are deja un nume de afișat
                            if (user.getDisplayName() == null || user.getDisplayName().isEmpty()) {
                                // Creare cont nou cu nume
                                createNewUserProfile(user, "Numele Utilizatorului");
                            } else {
                                updateUI(user);
                                navigateToHome();
                            }
                        }
                    } else {
                        // Dacă semnarea eșuează, afișează un mesaj utilizatorului.
                        Toast.makeText(getContext(), "Autentificare eșuată", Toast.LENGTH_SHORT).show();
                        updateUI(null);
                    }
                });
    }

    private void createNewUserProfile(FirebaseUser user, String displayName) {
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build();

        user.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "Profil creat cu succes", Toast.LENGTH_SHORT).show();
                        updateUI(user);
                        navigateToHome();
                    } else {
                        Toast.makeText(getContext(), "Eroare la crearea profilului", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToHome() {
        NavHostFragment.findNavController(this).navigate(R.id.nav_home);
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            mUserInfoTextView.setText("Bine ai venit, " + user.getDisplayName());
            mLoginButton.setVisibility(View.GONE);
            mUserInfoTextView.setVisibility(View.VISIBLE);
        } else {
            mUserInfoTextView.setText("");
            mLoginButton.setVisibility(View.VISIBLE);
            mUserInfoTextView.setVisibility(View.GONE);
        }
    }
}
