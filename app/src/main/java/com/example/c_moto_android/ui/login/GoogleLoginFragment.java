package com.example.c_moto_android.ui.login;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.c_moto_android.R;
import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.BeginSignInResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

public class GoogleLoginFragment extends LoginFragment {
    SignInClient oneTapClient;
    BeginSignInRequest signInRequest;
    Button button;
    private static final int REQ_ONE_TAP = 2;  // Can be any integer unique to the Activity.

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        oneTapClient = Identity.getSignInClient(requireActivity());
        signInRequest = BeginSignInRequest.builder()
                .setPasswordRequestOptions(BeginSignInRequest.PasswordRequestOptions.builder()
                        .setSupported(true)
                        .build())
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(getString(R.string.web_client_id))
                        .setFilterByAuthorizedAccounts(true)
                        .build())
                .setAutoSelectEnabled(true)
                .build();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        button = view.findViewById(R.id.googleSignInButton);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                oneTapClient.beginSignIn(signInRequest)
                        .addOnSuccessListener(requireActivity(), new OnSuccessListener<BeginSignInResult>() {
                            @Override
                            public void onSuccess(BeginSignInResult result) {
                                try {
                                    IntentSender intentSender = result.getPendingIntent().getIntentSender();
                                    Intent intent = new Intent();
                                    startIntentSenderForResult(intentSender, REQ_ONE_TAP, intent, 0, 0, 0, null);
                                } catch (IntentSender.SendIntentException e) {
                                    Log.e("GoogleLoginFragment", "Error starting One Tap UI", e);
                                }
                            }

                        })
                        .addOnFailureListener(requireActivity(), new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e("GoogleLoginFragment", "Error beginning One Tap sign-in flow", e);
                            }
                        });
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ONE_TAP) {
            try {
                SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(data);
                String idToken = credential.getGoogleIdToken();
                String username = credential.getId();
                String password = credential.getPassword();
                if (idToken != null) {
                    String email = credential.getId();
                    Toast.makeText(requireContext(), "Email: " + email, Toast.LENGTH_SHORT).show();
                } else if (password != null) {
                    // Handle password sign-in if needed
                }
            } catch (ApiException e) {
                Log.e("GoogleLoginFragment", "Error getting sign-in credential", e);
            }
        }
    }
}
